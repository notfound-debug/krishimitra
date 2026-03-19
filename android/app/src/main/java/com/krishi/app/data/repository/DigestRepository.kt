/*
 * DigestRepository.kt — Decides where to get data from.
 *
 * THE MOST IMPORTANT PATTERN IN ANDROID:
 *
 * The repository is the "smart middleman" between the UI and data sources.
 * It follows the "offline-first" pattern:
 *
 *   1. Always show cached data IMMEDIATELY (fast, works offline)
 *   2. Fetch fresh data from API in background
 *   3. Save fresh data to cache
 *   4. UI auto-updates because it's observing a Flow
 *
 * This is why the app feels fast even on slow internet:
 * - Farmer opens app → sees yesterday's digest instantly (from cache)
 * - Background fetch completes → today's digest replaces it
 * - If no internet → yesterday's digest stays, with "offline" badge
 */

package com.krishi.app.data.repository

import android.util.Log
import com.krishi.app.data.local.DigestDao
import com.krishi.app.data.local.DigestEntity
import com.krishi.app.data.remote.ApiClient
import kotlinx.coroutines.flow.Flow

class DigestRepository(private val digestDao: DigestDao) {

    // This Flow is observed by the UI — auto-updates when DB changes
    val latestDigest: Flow<DigestEntity?> = digestDao.getLatestDigest()

    /**
     * Fetch fresh digest from backend and cache it locally.
     *
     * Returns true if successful, false if failed (no internet, server down, etc.)
     * Even if this fails, the UI still shows cached data.
     */
    suspend fun refreshDigest(): Boolean {
        return try {
            // Call our backend: GET /api/digest
            val response = ApiClient.service.getDigest()

            // Convert API response → Room entity and save to local DB
            // Serialize article links to JSON string for Room storage
            val linksJson = com.google.gson.Gson().toJson(response.article_links)
            val entity = DigestEntity(
                date = response.date,
                kannada = response.kannada,
                english = response.english,
                articleCount = response.article_count,
                articleLinksJson = linksJson,
                generatedAt = response.generated_at,
            )
            digestDao.insertDigest(entity)

            // Clean up old cached digests (keep last 7 days)
            // This prevents the local DB from growing forever
            val sevenDaysAgo = java.time.LocalDate.now().minusDays(7).toString()
            digestDao.deleteOldDigests(sevenDaysAgo)

            Log.d("DigestRepo", "Digest refreshed: ${response.date}")
            true

        } catch (e: Exception) {
            // Network error, server down, etc.
            // This is FINE — the UI will show cached data
            Log.w("DigestRepo", "Failed to refresh digest: ${e.message}")
            false
        }
    }
}
