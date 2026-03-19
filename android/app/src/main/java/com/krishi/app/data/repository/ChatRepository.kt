package com.krishi.app.data.repository

import android.util.Log
import com.krishi.app.data.local.ChatDao
import com.krishi.app.data.local.ChatEntity
import com.krishi.app.data.remote.ApiClient
import com.krishi.app.data.remote.AskRequest
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {

    val allMessages: Flow<List<ChatEntity>> = chatDao.getAllMessages()

    suspend fun sendQuestion(
        question: String,
        language: String,
        deviceId: String,
        district: String,
        crops: List<String>,
    ): Boolean {
        // Save user message immediately (shows in UI instantly)
        chatDao.insertMessage(
            ChatEntity(isUser = true, text = question, language = language)
        )

        return try {
            // Call backend API
            val response = ApiClient.service.askQuestion(
                AskRequest(
                    question = question,
                    language = language,
                    device_id = deviceId,
                    district = district,
                    crops = crops,
                )
            )

            // Save bot answer
            chatDao.insertMessage(
                ChatEntity(
                    isUser = false,
                    text = response.answer,
                    language = language,
                    sourcesJson = Gson().toJson(response.sources),
                )
            )

            chatDao.pruneOldMessages()
            Log.d("ChatRepo", "Q&A success: ${response.sources}")
            true

        } catch (e: Exception) {
            Log.w("ChatRepo", "Q&A failed: ${e.message}")
            val errorMsg = if (language == "kn")
                "ಕ್ಷಮಿಸಿ, ಉತ್ತರಿಸಲು ಸಾಧ್ಯವಾಗಲಿಲ್ಲ. ಇಂಟರ್ನೆಟ್ ಪರಿಶೀಲಿಸಿ."
            else
                "Sorry, unable to answer. Please check your internet."

            chatDao.insertMessage(
                ChatEntity(isUser = false, text = errorMsg, language = language)
            )
            false
        }
    }

    suspend fun clearHistory() {
        chatDao.clearAll()
    }
}
