package io.nativeplanet.grove.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val fileMark: String,
    val size: Long,
    val tags: String,
    val created: Long,
    val modified: Long,
    val description: String,
    val starred: Boolean,
    val allowed: String,
    val inCatalogs: String,
    val localPath: String?,
    val syncState: String,
    val lastSynced: Long?
)

@Entity(tableName = "views")
data class ViewEntity(
    @PrimaryKey val name: String,
    val tags: String,
    val color: String
)

@Entity(tableName = "shares")
data class ShareEntity(
    @PrimaryKey val token: String,
    val fileId: String,
    val name: String
)

@Entity(tableName = "pending_uploads")
data class PendingUploadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localPath: String,
    val name: String,
    val fileMark: String,
    val tags: String,
    val sourceApp: String?,
    val createdAt: Long,
    val status: String,
    val error: String?
)

@Dao
interface FileDao {
    @Query("SELECT * FROM files ORDER BY modified DESC")
    fun getAllFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE starred = 1 ORDER BY modified DESC")
    fun getStarredFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getFileById(id: String): FileEntity?

    @Query("SELECT * FROM files WHERE name LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    fun searchFiles(query: String): Flow<List<FileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileEntity>)

    @Delete
    suspend fun deleteFile(file: FileEntity)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteFileById(id: String)

    @Query("DELETE FROM files")
    suspend fun deleteAllFiles()

    @Query("UPDATE files SET localPath = :path WHERE id = :id")
    suspend fun updateLocalPath(id: String, path: String)

    @Query("UPDATE files SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(id: String, state: String)
}

@Dao
interface ViewDao {
    @Query("SELECT * FROM views")
    fun getAllViews(): Flow<List<ViewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertView(view: ViewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertViews(views: List<ViewEntity>)

    @Query("DELETE FROM views WHERE name = :name")
    suspend fun deleteView(name: String)

    @Query("DELETE FROM views")
    suspend fun deleteAllViews()
}

@Dao
interface ShareDao {
    @Query("SELECT * FROM shares")
    fun getAllShares(): Flow<List<ShareEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShare(share: ShareEntity)

    @Query("DELETE FROM shares WHERE token = :token")
    suspend fun deleteShare(token: String)

    @Query("DELETE FROM shares")
    suspend fun deleteAllShares()
}

@Dao
interface PendingUploadDao {
    @Query("SELECT * FROM pending_uploads WHERE status = 'pending' ORDER BY createdAt ASC")
    fun getPendingUploads(): Flow<List<PendingUploadEntity>>

    @Query("SELECT COUNT(*) FROM pending_uploads WHERE status = 'pending'")
    fun getPendingCount(): Flow<Int>

    @Insert
    suspend fun insertUpload(upload: PendingUploadEntity): Long

    @Query("UPDATE pending_uploads SET status = :status, error = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String?)

    @Query("DELETE FROM pending_uploads WHERE id = :id")
    suspend fun deleteUpload(id: Long)

    @Query("DELETE FROM pending_uploads WHERE status = 'completed'")
    suspend fun deleteCompleted()
}

@Database(
    entities = [FileEntity::class, ViewEntity::class, ShareEntity::class, PendingUploadEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GroveDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun viewDao(): ViewDao
    abstract fun shareDao(): ShareDao
    abstract fun pendingUploadDao(): PendingUploadDao

    companion object {
        @Volatile
        private var INSTANCE: GroveDatabase? = null

        fun getInstance(context: Context): GroveDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GroveDatabase::class.java,
                    "grove_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
