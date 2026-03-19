/*
 * HomeViewModel.kt — Holds the Home screen's state.
 *
 * What it does:
 * 1. On creation: starts observing the digest from Room DB (instant cache)
 * 2. On creation: triggers a background API refresh
 * 3. On pull-to-refresh: triggers another API refresh
 * 4. Parses the raw Kannada digest text into categorized items
 *
 * The ViewModel survives screen rotation — the farmer doesn't lose
 * their scroll position or see a re-fetch.
 */

package com.krishi.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishi.app.data.local.DigestEntity
import com.krishi.app.data.local.PrefsStore
import com.krishi.app.data.repository.DigestRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DigestItem(
    val category: String,   // "price", "crop", "scheme", "weather", "general"
    val badge: String,       // "💰 ಬೆಲೆ", "🌿 ಬೆಳೆ", etc.
    val text: String,        // Main news text in Kannada
    val action: String,      // "→ ..." action line
    val sourceUrl: String = "",  // Link to original article
    val sourceName: String = "", // e.g. "Krishi Jagran"
)

data class SourceLink(
    val title: String,
    val link: String,
    val source: String,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val digestItems: List<DigestItem> = emptyList(),
    val sourceLinks: List<SourceLink> = emptyList(),
    val lastUpdated: String = "",
    val error: String? = null,
)

class HomeViewModel(
    private val repository: DigestRepository,
    private val prefsStore: PrefsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Language state — observed by the entire app
    val language: StateFlow<String> = prefsStore.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, "kn")

    // Keep the latest digest entity so we can re-parse on language toggle
    private var latestEntity: DigestEntity? = null
    private var cachedLinks: List<SourceLink> = emptyList()

    init {
        // Observe cached digest from Room DB
        viewModelScope.launch {
            repository.latestDigest.collect { entity ->
                if (entity != null) {
                    latestEntity = entity
                    updateDigestForLanguage(entity, language.value)
                }
            }
        }

        // Re-parse when language changes
        viewModelScope.launch {
            language.collect { lang ->
                latestEntity?.let { updateDigestForLanguage(it, lang) }
            }
        }

        refresh()
    }

    private fun updateDigestForLanguage(entity: DigestEntity, lang: String) {
        val text = if (lang == "en" && entity.english.isNotBlank()) entity.english else entity.kannada
        val links = try {
            com.google.gson.Gson().fromJson(
                entity.articleLinksJson,
                Array<SourceLink>::class.java
            ).toList()
        } catch (_: Exception) { emptyList() }

        cachedLinks = links
        val items = parseDigestText(text).map { item ->
            // Attach a source link to each digest item (round-robin from available links)
            val linkIndex = item.hashCode().mod(links.size.coerceAtLeast(1))
            if (links.isNotEmpty()) {
                val link = links[linkIndex.coerceIn(0, links.size - 1)]
                item.copy(sourceUrl = link.link, sourceName = link.source)
            } else item
        }

        _uiState.update {
            it.copy(
                digestItems = items,
                sourceLinks = links,
                lastUpdated = entity.generatedAt,
            )
        }
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val newLang = if (language.value == "kn") "en" else "kn"
            prefsStore.setLanguage(newLang)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = repository.refreshDigest()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = if (!success) "ಇಂಟರ್ನೆಟ್ ಸಂಪರ್ಕವಿಲ್ಲ" else null,
                )
            }
        }
    }

    /**
     * Parse the raw Kannada digest text into categorized items.
     *
     * The LLM generates a two-line format:
     *   * 💰 ಬೆಲೆ: ಮಂಗಳೂರು ಮಂಡಿಯಲ್ಲಿ ಅಡಿಕೆ ₹42,000
     *   → ಮಂಗಳೂರು ಮಂಡಿಯಲ್ಲಿ ಮಾರಾಟ ಲಾಭಕರ
     *
     * Line 1 = news text (starts with emoji category)
     * Line 2 = action line (starts with →)
     *
     * We pair them up into DigestItem objects.
     */
    private fun parseDigestText(raw: String): List<DigestItem> {
        if (raw.isBlank()) return emptyList()

        val lines = raw.split("\n")
            .map { it.trim().removePrefix("*").removePrefix("-").removePrefix("•").trim() }
            .filter { it.isNotBlank() }

        val items = mutableListOf<DigestItem>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            // Skip lines that are just action lines without a parent news line
            if (line.startsWith("→")) {
                i++
                continue
            }

            val category = detectCategory(line)
            val badge = when (category) {
                "price" -> "💰 ಬೆಲೆ"
                "crop" -> "🌿 ಬೆಳೆ"
                "scheme" -> "🏛️ ಯೋಜನೆ"
                "weather" -> "🌧️ ಹವಾಮಾನ"
                else -> "📰 ಸಾಮಾನ್ಯ"
            }

            // Clean the line — remove emoji/category prefix
            var cleanText = line
                .replace(Regex("^[💰🌿🏛️🌧️📰]+\\s*"), "")
                .replace(Regex("^(ಬೆಲೆ|ಬೆಳೆ|ಯೋಜನೆ|ಹವಾಮಾನ|ಸಾಮಾನ್ಯ)[:\\s]*"), "")
                .trim()

            // Check if action is in the same line (→ inline)
            var action = ""
            if ("→" in cleanText) {
                val parts = cleanText.split("→", limit = 2)
                cleanText = parts[0].trim()
                action = "→ ${parts[1].trim()}"
            }

            // Check if the NEXT line is an action line (→ on separate line)
            if (action.isEmpty() && i + 1 < lines.size && lines[i + 1].startsWith("→")) {
                action = lines[i + 1].trim()
                i++ // Skip the action line
            }

            if (cleanText.length >= 5) {
                items.add(DigestItem(
                    category = category,
                    badge = badge,
                    text = cleanText,
                    action = action,
                ))
            }

            i++
        }

        return items
    }

    private fun detectCategory(line: String): String {
        val lower = line.lowercase()
        return when {
            "💰" in line || "ಬೆಲೆ" in line || "price" in lower || "₹" in line -> "price"
            "🌿" in line || "ಬೆಳೆ" in line || "crop" in lower || "ರೋಗ" in line -> "crop"
            "🏛️" in line || "ಯೋಜನೆ" in line || "scheme" in lower || "ಕಿಸಾನ್" in line -> "scheme"
            "🌧️" in line || "ಹವಾಮಾನ" in line || "weather" in lower || "ಮಳೆ" in line -> "weather"
            else -> "general"
        }
    }
}
