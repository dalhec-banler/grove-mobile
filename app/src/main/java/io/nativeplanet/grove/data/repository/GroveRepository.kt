package io.nativeplanet.grove.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import io.nativeplanet.grove.data.local.*
import io.nativeplanet.grove.data.remote.UrbitClient
import io.nativeplanet.grove.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.Instant

class GroveRepository(
    private val context: Context,
    private val urbitClient: UrbitClient,
    private val database: GroveDatabase
) {
    companion object {
        private const val TAG = "GroveRepository"
    }

    private val gson = Gson()
    private val fileDao = database.fileDao()
    private val viewDao = database.viewDao()
    private val shareDao = database.shareDao()
    private val pendingUploadDao = database.pendingUploadDao()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val cacheDir: File
        get() = File(context.cacheDir, "grove_files").also { it.mkdirs() }

    val files: Flow<List<GroveFile>> = fileDao.getAllFiles().map { entities ->
        entities.map { it.toGroveFile() }
    }

    val starredFiles: Flow<List<GroveFile>> = fileDao.getStarredFiles().map { entities ->
        entities.map { it.toGroveFile() }
    }

    val views: Flow<List<GroveView>> = viewDao.getAllViews().map { entities ->
        entities.map { GroveView(it.name, it.tags.split(",").filter { t -> t.isNotEmpty() }, it.color) }
    }

    val shares: Flow<List<GroveShare>> = shareDao.getAllShares().map { entities ->
        entities.map { GroveShare(it.token, it.fileId, it.name) }
    }

    val pendingUploads: Flow<Int> = pendingUploadDao.getPendingCount()

    val isConnected: StateFlow<Boolean> = urbitClient.isConnected
    val shipName: StateFlow<String?> = urbitClient.shipName

    suspend fun connect(code: String): Boolean {
        val success = urbitClient.authenticate(code)
        if (success) {
            syncAll()
            startUpdateSubscription()
        }
        return success
    }

    fun disconnect() {
        urbitClient.disconnect()
    }

    suspend fun syncAll() {
        syncFiles()
        syncViews()
        syncShares()
    }

    private suspend fun syncFiles() {
        val remoteFiles = urbitClient.scry("/files") { json ->
            parseFileList(json)
        } ?: return

        fileDao.insertFiles(remoteFiles.map { it.toEntity() })
    }

    private suspend fun syncViews() {
        val remoteViews = urbitClient.scry("/views") { json ->
            parseViewList(json)
        } ?: return

        viewDao.deleteAllViews()
        viewDao.insertViews(remoteViews.map {
            ViewEntity(it.name, it.tags.joinToString(","), it.color)
        })
    }

    private suspend fun syncShares() {
        val remoteShares = urbitClient.scry("/shares") { json ->
            parseShareList(json)
        } ?: return

        shareDao.deleteAllShares()
        remoteShares.forEach {
            shareDao.insertShare(ShareEntity(it.token, it.fileId, it.name))
        }
    }

    private fun startUpdateSubscription() {
        scope.launch {
            urbitClient.subscribeUpdates().collect { update ->
                handleUpdate(update)
            }
        }
    }

    private suspend fun handleUpdate(json: JsonObject) {
        val type = json.get("type")?.asString ?: return

        when (type) {
            "fileAdded", "fileUpdated" -> {
                val file = parseFileMeta(json)
                fileDao.insertFile(file.toEntity())
            }
            "fileRemoved" -> {
                val fileId = json.get("fileId")?.asString ?: return
                fileDao.deleteFileById(fileId)
                File(cacheDir, fileId).delete()
            }
            "viewAdded" -> {
                val name = json.get("name")?.asString ?: return
                val tags = json.getAsJsonArray("tags")?.map { it.asString } ?: emptyList()
                val color = json.get("color")?.asString ?: "#000000"
                viewDao.insertView(ViewEntity(name, tags.joinToString(","), color))
            }
            "viewRemoved" -> {
                val name = json.get("name")?.asString ?: return
                viewDao.deleteView(name)
            }
            "shareAdded" -> {
                val token = json.get("token")?.asString ?: return
                val fileId = json.get("fileId")?.asString ?: return
                shareDao.insertShare(ShareEntity(token, fileId, ""))
            }
            "shareRemoved" -> {
                val token = json.get("token")?.asString ?: return
                shareDao.deleteShare(token)
            }
        }
    }

    suspend fun downloadFile(fileId: String): File? {
        val destFile = File(cacheDir, fileId)
        if (destFile.exists()) return destFile

        fileDao.updateSyncState(fileId, SyncState.DOWNLOADING.name)
        val success = urbitClient.downloadFile(fileId, destFile)

        if (success) {
            fileDao.updateLocalPath(fileId, destFile.absolutePath)
            fileDao.updateSyncState(fileId, SyncState.SYNCED.name)
            return destFile
        } else {
            fileDao.updateSyncState(fileId, SyncState.ERROR.name)
            return null
        }
    }

    suspend fun uploadFile(
        localPath: String,
        name: String,
        tags: List<String>,
        sourceApp: String? = null
    ): Boolean {
        val file = File(localPath)
        if (!file.exists()) return false

        val fileMark = guessFileMark(name)
        val allTags = if (sourceApp != null) tags + sourceApp else tags

        val uploadId = pendingUploadDao.insertUpload(
            PendingUploadEntity(
                localPath = localPath,
                name = name,
                fileMark = fileMark,
                tags = allTags.joinToString(","),
                sourceApp = sourceApp,
                createdAt = System.currentTimeMillis(),
                status = "pending",
                error = null
            )
        )

        return try {
            pendingUploadDao.updateStatus(uploadId, "uploading", null)
            val data = file.readBytes()
            val success = urbitClient.uploadFile(name, fileMark, data, allTags)

            if (success) {
                pendingUploadDao.updateStatus(uploadId, "completed", null)
                true
            } else {
                pendingUploadDao.updateStatus(uploadId, "error", "Upload failed")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload error", e)
            pendingUploadDao.updateStatus(uploadId, "error", e.message)
            false
        }
    }

    suspend fun retryPendingUploads() {
        pendingUploadDao.getPendingUploads().first().forEach { upload ->
            uploadFile(upload.localPath, upload.name, upload.tags.split(","), upload.sourceApp)
        }
    }

    suspend fun toggleStar(fileId: String): Boolean {
        val file = fileDao.getFileById(fileId) ?: return false
        val success = urbitClient.poke(mapOf("toggle-star" to mapOf("id" to fileId)))
        if (success) {
            fileDao.insertFile(file.copy(starred = !file.starred))
        }
        return success
    }

    suspend fun deleteFile(fileId: String): Boolean {
        val success = urbitClient.poke(mapOf("delete" to mapOf("id" to fileId)))
        if (success) {
            fileDao.deleteFileById(fileId)
            File(cacheDir, fileId).delete()
        }
        return success
    }

    suspend fun addTags(fileId: String, tags: List<String>): Boolean {
        return urbitClient.poke(mapOf(
            "add-tags" to mapOf("id" to fileId, "tags" to tags)
        ))
    }

    suspend fun removeTags(fileId: String, tags: List<String>): Boolean {
        return urbitClient.poke(mapOf(
            "remove-tags" to mapOf("id" to fileId, "tags" to tags)
        ))
    }

    suspend fun shareFile(fileId: String): Boolean {
        return urbitClient.poke(mapOf("share" to mapOf("id" to fileId)))
    }

    suspend fun unshareFile(token: String): Boolean {
        return urbitClient.poke(mapOf("unshare" to mapOf("token" to token)))
    }

    fun searchFiles(query: String): Flow<List<GroveFile>> {
        return fileDao.searchFiles(query).map { entities ->
            entities.map { it.toGroveFile() }
        }
    }

    fun getFilesByTag(tag: String): Flow<List<GroveFile>> {
        return files.map { allFiles ->
            allFiles.filter { tag in it.tags }
        }
    }

    private fun guessFileMark(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "jpg"
            "png" -> "png"
            "gif" -> "gif"
            "webp" -> "webp"
            "pdf" -> "pdf"
            "txt" -> "txt"
            "md" -> "md"
            "json" -> "json"
            "mp3" -> "mp3"
            "mp4" -> "mp4"
            "mov" -> "mov"
            "zip" -> "zip"
            else -> "blob"
        }
    }

    private fun parseFileList(json: String): List<GroveFile> {
        val array = gson.fromJson(json, JsonArray::class.java)
        return array.map { parseFileMeta(it.asJsonObject) }
    }

    private fun parseFileMeta(obj: JsonObject): GroveFile {
        return GroveFile(
            id = obj.get("id")?.asString ?: "",
            name = obj.get("name")?.asString ?: "",
            fileMark = obj.get("file-mark")?.asString ?: obj.get("fileMark")?.asString ?: "",
            size = obj.get("size")?.asLong ?: 0,
            tags = obj.getAsJsonArray("tags")?.map { it.asString } ?: emptyList(),
            created = parseUrbitDate(obj.get("created")?.asString),
            modified = parseUrbitDate(obj.get("modified")?.asString),
            description = obj.get("description")?.asString ?: "",
            starred = obj.get("starred")?.asBoolean ?: false,
            allowed = obj.getAsJsonArray("allowed")?.map { it.asString } ?: emptyList(),
            inCatalogs = obj.getAsJsonArray("inCatalogs")?.map { it.asString } ?: emptyList()
        )
    }

    private fun parseViewList(json: String): List<GroveView> {
        val array = gson.fromJson(json, JsonArray::class.java)
        return array.map {
            val obj = it.asJsonObject
            GroveView(
                name = obj.get("name")?.asString ?: "",
                tags = obj.getAsJsonArray("tags")?.map { t -> t.asString } ?: emptyList(),
                color = obj.get("color")?.asString ?: "#000000"
            )
        }
    }

    private fun parseShareList(json: String): List<GroveShare> {
        val array = gson.fromJson(json, JsonArray::class.java)
        return array.map {
            val obj = it.asJsonObject
            GroveShare(
                token = obj.get("token")?.asString ?: "",
                fileId = obj.get("file-id")?.asString ?: "",
                name = obj.get("name")?.asString ?: ""
            )
        }
    }

    private fun parseUrbitDate(date: String?): Instant {
        if (date == null) return Instant.now()
        return try {
            Instant.now()
        } catch (e: Exception) {
            Instant.now()
        }
    }

    private fun GroveFile.toEntity() = FileEntity(
        id = id,
        name = name,
        fileMark = fileMark,
        size = size,
        tags = tags.joinToString(","),
        created = created.toEpochMilli(),
        modified = modified.toEpochMilli(),
        description = description,
        starred = starred,
        allowed = allowed.joinToString(","),
        inCatalogs = inCatalogs.joinToString(","),
        localPath = localPath,
        syncState = syncState.name,
        lastSynced = System.currentTimeMillis()
    )

    private fun FileEntity.toGroveFile() = GroveFile(
        id = id,
        name = name,
        fileMark = fileMark,
        size = size,
        tags = tags.split(",").filter { it.isNotEmpty() },
        created = Instant.ofEpochMilli(created),
        modified = Instant.ofEpochMilli(modified),
        description = description,
        starred = starred,
        allowed = allowed.split(",").filter { it.isNotEmpty() },
        inCatalogs = inCatalogs.split(",").filter { it.isNotEmpty() },
        localPath = localPath,
        syncState = try { SyncState.valueOf(syncState) } catch (e: Exception) { SyncState.SYNCED }
    )
}
