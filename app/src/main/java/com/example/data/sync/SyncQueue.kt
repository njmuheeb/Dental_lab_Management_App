package com.example.data.sync

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Outbox queue for the OPTIONAL Google Sheets sync layer.
 *
 * The app is offline-first: Room/SQLite is the single source of truth and this table is
 * completely inert while sync is disabled. When a row is DELETED locally we cannot mark a
 * flag on the row (it no longer exists), so a tombstone entry is enqueued here. UPSERTs do
 * not need queue entries - rows carry a [com.example.data.model] `pendingSync` flag instead,
 * which makes the first sync after enabling automatically push the full dataset.
 *
 * Unique index on (entityType, entitySyncId) with REPLACE conflict strategy means repeated
 * modifications of the same entity collapse into one queue entry - no duplicates.
 */
@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["entityType", "entitySyncId"], unique = true)]
)
data class SyncQueueEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,      // "clinic", "patient", "work_type", "clinic_rate", "work_order", "payment"
    val entitySyncId: String,
    val operation: String,       // "DELETE"
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncQueueEntry>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun countFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: SyncQueueEntry)

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()
}
