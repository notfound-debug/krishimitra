package com.krishi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishi.app.ui.theme.KrishiColors
import com.krishi.app.ui.viewmodel.ChatMessage
import com.krishi.app.ui.viewmodel.ChatViewModel

@Composable
fun ChatScreen(viewModel: ChatViewModel, language: String) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KrishiColors.Background),
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(KrishiColors.Surface)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text("KRISHI", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = KrishiColors.Primary)
            Text(
                if (language == "kn") "ಕೃಷಿಮಿತ್ರನನ್ನು ಕೇಳಿ • AI ಕೃಷಿ ಸಹಾಯಕ"
                else "Ask KrishiMitra • AI Farm Advisor",
                fontSize = 12.sp, color = KrishiColors.TextSecondary,
            )
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(KrishiColors.Border))

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            // Welcome card if empty
            if (uiState.messages.isEmpty() && !uiState.isLoading) {
                item {
                    WelcomeCard(language) { suggestion ->
                        viewModel.updateInputText(suggestion)
                        viewModel.sendMessage()
                    }
                }
            }

            items(uiState.messages, key = { it.id }) { message ->
                ChatBubble(message = message)
            }

            // Typing indicator
            if (uiState.isLoading) {
                item { TypingIndicator(language) }
            }
        }

        // Input bar
        ChatInputBar(
            text = uiState.inputText,
            onTextChange = { viewModel.updateInputText(it) },
            onSend = { viewModel.sendMessage() },
            isLoading = uiState.isLoading,
            language = language,
        )
    }
}

@Composable
fun WelcomeCard(language: String, onSuggestionClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KrishiColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, KrishiColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            if (language == "kn")
                "🙏 ನಮಸ್ಕಾರ! ನಾನು ಕೃಷಿಮಿತ್ರ. ಕೃಷಿ ಬಗ್ಗೆ ಏನಾದರೂ ಕೇಳಿ — ಅಡಿಕೆ, ತೆಂಗು, ಯೋಜನೆ, ಬೆಲೆ, ಹವಾಮಾನ."
            else
                "🙏 Hello! I am KrishiMitra. Ask me anything about farming — arecanut, coconut, schemes, prices, weather.",
            fontSize = 16.sp, color = KrishiColors.TextPrimary, lineHeight = 24.sp,
        )

        Spacer(Modifier.height(12.dp))

        // Suggested questions
        val suggestions = if (language == "kn") listOf(
            "ಕೊಳೆ ರೋಗ ತಡೆಗಟ್ಟುವುದು ಹೇಗೆ?",
            "PM KISAN ಅರ್ಜಿ ಹೇಗೆ?",
            "ಅಡಿಕೆ ಜೊತೆ ಏನು ಬೆಳೆಯಬಹುದು?",
        ) else listOf(
            "How to prevent Kole Roga?",
            "How to apply for PM KISAN?",
            "What to intercrop with arecanut?",
        )

        suggestions.forEach { suggestion ->
            Text(
                text = "💬 $suggestion",
                fontSize = 15.sp,
                color = KrishiColors.Primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(KrishiColors.PrimaryLight)
                    .clickable { onSuggestionClick(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 16.dp,
                    )
                )
                .background(
                    if (message.isUser) KrishiColors.Primary else KrishiColors.Surface
                )
                .then(
                    if (!message.isUser) Modifier.border(
                        1.dp, KrishiColors.Border,
                        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                    ) else Modifier
                )
                .padding(12.dp),
        ) {
            Text(
                text = message.text,
                fontSize = 16.sp,
                color = if (message.isUser) Color.White else KrishiColors.TextPrimary,
                lineHeight = 24.sp,
            )
        }

        // Source citations for bot messages
        if (!message.isUser && message.sources.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "📄 ${message.sources.joinToString(", ")}",
                fontSize = 11.sp,
                color = KrishiColors.TextMuted,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun TypingIndicator(language: String) {
    Box(
        modifier = Modifier
            .widthIn(max = 200.dp)
            .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
            .background(KrishiColors.Surface)
            .border(1.dp, KrishiColors.Border, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
            .padding(12.dp),
    ) {
        Text(
            if (language == "kn") "ಯೋಚಿಸುತ್ತಿದ್ದೇನೆ..." else "Thinking...",
            fontSize = 15.sp,
            color = KrishiColors.TextMuted,
        )
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    language: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KrishiColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Text input
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = TextStyle(fontSize = 16.sp, color = KrishiColors.TextPrimary),
            cursorBrush = SolidColor(KrishiColors.Primary),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(KrishiColors.Background)
                .border(1.dp, KrishiColors.Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        if (language == "kn") "ನಿಮ್ಮ ಪ್ರಶ್ನೆ ಬರೆಯಿರಿ..."
                        else "Type your question...",
                        fontSize = 15.sp,
                        color = KrishiColors.TextMuted,
                    )
                }
                innerTextField()
            },
        )

        Spacer(Modifier.width(8.dp))

        // Send button
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (text.isNotBlank() && !isLoading) KrishiColors.Primary
                    else KrishiColors.Border
                )
                .clickable(enabled = text.isNotBlank() && !isLoading) { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Text("➤", fontSize = 18.sp, color = Color.White)
        }
    }
}
