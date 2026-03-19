/*
 * DigestEntity.kt — Room database entity for cached digests.
 *
 * HOW ROOM WORKS:
 * 1. You define a data class with @Entity (this becomes a SQLite table)
 * 2. You define a DAO interface with @Dao (queries)
 * 3. Room auto-generates all the SQL code
 *
 * This entity mirrors the backend's DigestResponse but is stored locally.
 * When the farmer opens the app offline, we show this cached data.
 */

package com.krishi.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "digests")
data class DigestEntity(
    @PrimaryKey
    val date: String,           // "2026-03-18" — one row per day
    val kannada: String,        // Kannada bullet points
    val english: String,        // English translation
    val articleCount: Int,      // How many articles were summarized
    val articleLinksJson: String = "[]", // JSON string of article links for "view source"
    val generatedAt: String,    // When this was generated
    val cachedAt: Long = System.currentTimeMillis(),
)
