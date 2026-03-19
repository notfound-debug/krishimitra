/*
 * KrishiApp.kt — Application class (runs ONCE when app launches).
 *
 * Think of this as the "constructor" for the entire app.
 * We initialize singletons here (database, repository)
 * so they're available to all screens.
 *
 * In AndroidManifest.xml, we set android:name=".KrishiApp"
 * which tells Android to use this class as the app entry.
 */

package com.krishi.app

import android.app.Application
import com.krishi.app.data.local.AppDatabase
import com.krishi.app.data.local.PrefsStore
import com.krishi.app.data.repository.ChatRepository
import com.krishi.app.data.repository.DigestRepository

class KrishiApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var digestRepository: DigestRepository
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var prefsStore: PrefsStore
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        digestRepository = DigestRepository(database.digestDao())
        chatRepository = ChatRepository(database.chatDao())
        prefsStore = PrefsStore(this)
    }
}
