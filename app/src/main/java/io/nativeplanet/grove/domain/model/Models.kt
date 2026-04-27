package io.nativeplanet.grove.domain.model

import java.time.Instant

data class GroveFile(
    val id: String,
    val name: String,
    val fileMark: String,
    val size: Long,
    val tags: List<String>,
    val created: Instant,
    val modified: Instant,
    val description: String,
    val starred: Boolean,
    val allowed: List<String> = emptyList(),
    val inCatalogs: List<String> = emptyList(),
    val localPath: String? = null,
    val syncState: SyncState = SyncState.SYNCED
)

enum class SyncState {
    SYNCED,
    PENDING_UPLOAD,
    UPLOADING,
    PENDING_DOWNLOAD,
    DOWNLOADING,
    ERROR
}

data class GroveView(
    val name: String,
    val tags: List<String>,
    val color: String
)

data class GroveShare(
    val token: String,
    val fileId: String,
    val name: String
)

data class InboxEntry(
    val owner: String,
    val fileId: String,
    val name: String,
    val fileMark: String,
    val size: Long,
    val offered: String,
    val accepted: Boolean,
    val cached: Boolean
)

data class Trust(
    val trusted: List<String>,
    val blocked: List<String>
)

data class CatalogConfig(
    val name: String,
    val description: String,
    val mode: CatalogMode,
    val friends: List<String>,
    val groupFlag: GroupFlag?,
    val files: List<String>,
    val created: String,
    val modified: String
)

enum class CatalogMode {
    PUBLIC, PALS, GROUP
}

data class GroupFlag(
    val host: String,
    val name: String
)

data class Catalog(
    val catalogId: String,
    val config: CatalogConfig
)

data class CanopyEntry(
    val id: String,
    val displayName: String,
    val fileMark: String,
    val size: Long,
    val tags: List<String>,
    val published: String,
    val description: String
)

data class CatalogListing(
    val host: String,
    val catalogId: String,
    val name: String,
    val description: String,
    val mode: CatalogMode,
    val entries: List<CanopyEntry>
)

data class ConnectionState(
    val isConnected: Boolean,
    val shipName: String? = null,
    val lastSync: Instant? = null,
    val pendingUploads: Int = 0,
    val error: String? = null
)

sealed class GroveUpdate {
    data class FileAdded(val file: GroveFile) : GroveUpdate()
    data class FileUpdated(val file: GroveFile) : GroveUpdate()
    data class FileRemoved(val fileId: String) : GroveUpdate()
    data class AllowedUpdated(val fileId: String, val ships: List<String>) : GroveUpdate()
    data class ViewAdded(val view: GroveView) : GroveUpdate()
    data class ViewRemoved(val name: String) : GroveUpdate()
    data class ShareAdded(val share: GroveShare) : GroveUpdate()
    data class ShareRemoved(val token: String) : GroveUpdate()
    data class InboxAdded(val entry: InboxEntry) : GroveUpdate()
    data class InboxUpdated(val entry: InboxEntry) : GroveUpdate()
    data class InboxRemoved(val owner: String, val fileId: String) : GroveUpdate()
    data class TrustedUpdated(val trust: Trust) : GroveUpdate()
    data class CacheUpdated(val owner: String, val meta: GroveFile) : GroveUpdate()
    data class CacheRemoved(val owner: String, val fileId: String) : GroveUpdate()
}
