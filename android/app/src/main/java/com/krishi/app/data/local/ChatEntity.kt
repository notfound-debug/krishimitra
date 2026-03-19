package com.krishi.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val isUser: Boolean,
    val text: String,
    val language: String = "kn",
    val sourcesJson: String = "[]",
    val timestamp: Long = System.currentTimeMillis(),
)
