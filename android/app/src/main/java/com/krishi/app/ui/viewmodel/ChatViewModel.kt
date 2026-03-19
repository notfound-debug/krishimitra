package com.krishi.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishi.app.data.local.PrefsStore
import com.krishi.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val sources: List<String>,
    val timestamp: Long,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val inputText: String = "",
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val prefsStore: PrefsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val language: StateFlow<String> = prefsStore.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, "kn")

    private var deviceId: String = ""

    init {
        // Observe chat messages from Room DB — auto-updates UI
        viewModelScope.launch {
            chatRepository.allMessages.collect { entities ->
                val messages = entities.map { entity ->
                    val sources = try {
                        com.google.gson.Gson().fromJson(
                            entity.sourcesJson, Array<String>::class.java
                        ).toList()
                    } catch (_: Exception) { emptyList() }

                    ChatMessage(
                        id = entity.id,
                        isUser = entity.isUser,
                        text = entity.text,
                        sources = sources,
                        timestamp = entity.timestamp,
                    )
                }
                _uiState.update { it.copy(messages = messages) }
            }
        }

        viewModelScope.launch {
            deviceId = prefsStore.ensureDeviceId()
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isLoading) return

        _uiState.update { it.copy(inputText = "", isLoading = true) }

        viewModelScope.launch {
            chatRepository.sendQuestion(
                question = text,
                language = language.value,
                deviceId = deviceId,
                district = "",
                crops = emptyList(),
            )
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
