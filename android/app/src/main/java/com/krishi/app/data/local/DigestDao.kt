/*
 * DigestDao.kt — Data Access Object for digest cache.
 *
 * DAO = the "queries" layer. You write function signatures + annotations,
 * Room generates the actual SQL implementation.
 *
 * Flow is how Room queries return data:
 * - Flow = reactive stream. When data changes in the DB, the UI auto-updates.
 *   (Like LiveData but Kotlin-native)
 */

package com.krishi.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DigestDao {

    // Get the most recent digest — returns a Flow that auto-updates the UI
    @Query("SELECT * FROM digests ORDER BY date DESC LIMIT 1")
    fun getLatestDigest(): Flow<DigestEntity?>

    // Get digest for a specific date
    @Query("SELECT * FROM digests WHERE date = :date")
    suspend fun getDigestByDate(date: String): DigestEntity?

    // Save a digest (replace if same date already exists)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDigest(digest: DigestEntity)

    // Delete old digests (keep only last 7 days to save storage)
    @Query("DELETE FROM digests WHERE date < :cutoffDate")
    suspend fun deleteOldDigests(cutoffDate: String)
}
