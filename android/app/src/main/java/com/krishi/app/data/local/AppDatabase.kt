/*
 * AppDatabase.kt — Room database definition.
 *
 * This ties together all entities (tables) and DAOs (queries).
 * Room generates the actual SQLite database from this.
 *
 * Singleton pattern: only ONE database instance for the whole app.
 * (Creating multiple would waste memory and cause locking issues)
 */

package com.krishi.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DigestEntity::class, ChatEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun digestDao(): DigestDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile  // Ensures all threads see the latest instance
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // Double-checked locking — thread-safe singleton
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "krishimitra_db"
                ).fallbackToDestructiveMigration()  // OK — it's just a cache
                .build().also { INSTANCE = it }
            }
        }
    }
}
