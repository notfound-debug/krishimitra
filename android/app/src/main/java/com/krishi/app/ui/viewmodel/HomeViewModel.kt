package com.krishi.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishi.app.data.local.DigestEntity
import com.krishi.app.data.local.PrefsStore
import com.krishi.app.data.remote.ApiClient
import com.krishi.app.data.repository.DigestRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DigestItem(
    val category: String,
    val badge: String,
    val text: String,
    val action: String,
    val sourceUrl: String = "",
    val sourceName: String = "",
)

data class SourceLink(
    val title: String,
    val link: String,
    val source: String,
)

// Data for the 3 stat cards + hero price
data class HomeCardData(
    // Hero card (primary crop)
    val primaryCropName: String = "ಅಡಿಕೆ",
    val primaryCropPrice: String = "—",
    val primaryCropApmc: String = "",
    val primaryCropTrend: String = "",
    val primaryCropTrendDir: String = "",
    val priceDate: String = "",
    val isRealPrice: Boolean = false,
    // Stat card 1: secondary crop
    val secondaryCropName: String = "",
    val secondaryCropPrice: String = "—",
    // Stat card 2: weather
    val weatherTemp: String = "—",
    val weatherDesc: String = "",
    // Stat card 3: smart insight
    val insightText: String = "",
    val insightCrop: String = "",
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val digestItems: List<DigestItem> = emptyList(),
    val sourceLinks: List<SourceLink> = emptyList(),
    val lastUpdated: String = "",
    val error: String? = null,
    val cards: HomeCardData = HomeCardData(),
    val farmerTaluk: String = "",
    val farmerDistrict: String = "",
)

class HomeViewModel(
    private val repository: DigestRepository,
    private val prefsStore: PrefsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val language: StateFlow<String> = prefsStore.language
        .stateIn(viewModelScope, SharingStarted.Eagerly, "kn")

    private var latestEntity: DigestEntity? = null
    private var cachedLinks: List<SourceLink> = emptyList()

    init {
        // Observe digest from Room DB
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

        // Observe profile data
        viewModelScope.launch {
            combine(
                prefsStore.district,
                prefsStore.taluk,
                prefsStore.crops,
            ) { district, taluk, crops ->
                Triple(district, taluk, crops)
            }.collect { (district, taluk, crops) ->
                _uiState.update {
                    it.copy(farmerDistrict = district, farmerTaluk = taluk)
                }
                // Fetch personalized data when profile is available
                if (crops.isNotEmpty()) {
                    fetchCardData(crops)
                }
            }
        }

        refresh()
    }

    private fun fetchCardData(crops: List<String>) {
        viewModelScope.launch {
            val lang = language.value
            val cards = _uiState.value.cards.copy()
            var updatedCards = cards

            try {
                // Primary crop price
                val primaryCropRaw = (crops.firstOrNull() ?: "ಅಡಿಕೆ")
                    .replace(Regex("[🌴🥥🌾🥜🍫🌳🌶️🍌] ?"), "").trim()

                // Map Kannada crop name to API crop name
                val cropApiName = mapCropToApi(primaryCropRaw)
                val priceResponse = ApiClient.service.getPrices(cropApiName)
                val bestPrice = priceResponse.prices.firstOrNull()

                updatedCards = updatedCards.copy(
                    primaryCropName = if (lang == "kn") priceResponse.crop_kn else priceResponse.crop,
                    primaryCropPrice = "₹${formatPrice(bestPrice?.max_price ?: 0)}",
                    primaryCropApmc = priceResponse.best_apmc,
                    primaryCropTrend = bestPrice?.trend ?: "",
                    primaryCropTrendDir = bestPrice?.trend_direction ?: "",
                    priceDate = priceResponse.date,
                    isRealPrice = true,
                )

                // Secondary crop price
                if (crops.size >= 2) {
                    val secondaryCropRaw = crops[1]
                        .replace(Regex("[🌴🥥🌾🥜🍫🌳🌶️🍌] ?"), "").trim()
                    val secApiName = mapCropToApi(secondaryCropRaw)
                    val secResponse = ApiClient.service.getPrices(secApiName)
                    val secBest = secResponse.prices.firstOrNull()
                    updatedCards = updatedCards.copy(
                        secondaryCropName = if (lang == "kn") secondaryCropRaw else secResponse.crop,
                        secondaryCropPrice = "₹${formatPriceShort(secBest?.max_price ?: 0)}",
                    )
                }
            } catch (e: Exception) {
                Log.w("HomeVM", "Price fetch failed: ${e.message}")
            }

            // Weather
            try {
                val district = mapDistrictToApi(_uiState.value.farmerDistrict)
                val weatherResp = ApiClient.service.getWeather(district)
                updatedCards = updatedCards.copy(
                    weatherTemp = "${weatherResp.temp}°C",
                    weatherDesc = if (lang == "kn") weatherResp.description_kn else weatherResp.description_en,
                )
            } catch (e: Exception) {
                Log.w("HomeVM", "Weather fetch failed: ${e.message}")
                updatedCards = updatedCards.copy(weatherTemp = "—", weatherDesc = "")
            }

            // Smart insight
            try {
                val primaryCropRaw = (crops.firstOrNull() ?: "ಅಡಿಕೆ")
                    .replace(Regex("[🌴🥥🌾🥜🍫🌳🌶️🍌] ?"), "").trim()
                val cropApiName = mapCropToApi(primaryCropRaw)
                val insightResp = ApiClient.service.getInsight(cropApiName, lang)
                updatedCards = updatedCards.copy(
                    insightText = if (lang == "kn") insightResp.insight_kn else insightResp.insight_en,
                    insightCrop = if (lang == "kn") insightResp.crop_kn else insightResp.crop,
                )
            } catch (e: Exception) {
                Log.w("HomeVM", "Insight fetch failed: ${e.message}")
            }

            _uiState.update { it.copy(cards = updatedCards) }
        }
    }

    private fun mapDistrictToApi(kannadaDistrict: String): String = when {
        "ದಕ್ಷಿಣ ಕನ್ನಡ" in kannadaDistrict -> "Dakshina Kannada"
        "ಉಡುಪಿ" in kannadaDistrict -> "Udupi"
        "ಉತ್ತರ ಕನ್ನಡ" in kannadaDistrict -> "Uttara Kannada"
        else -> kannadaDistrict.ifEmpty { "Dakshina Kannada" }
    }

    private fun mapCropToApi(kannadaName: String): String {
        return when {
            "ಅಡಿಕೆ" in kannadaName || "arecanut" in kannadaName.lowercase() -> "Arecanut"
            "ತೆಂಗು" in kannadaName || "coconut" in kannadaName.lowercase() -> "Coconut"
            "ಭತ್ತ" in kannadaName || "rice" in kannadaName.lowercase() -> "Rice"
            "ಕಾಳುಮೆಣಸು" in kannadaName || "pepper" in kannadaName.lowercase() -> "Black Pepper"
            "ಗೋಡಂಬಿ" in kannadaName || "cashew" in kannadaName.lowercase() -> "Cashew"
            "ಕೊಬ್ಬರಿ" in kannadaName || "copra" in kannadaName.lowercase() -> "Copra"
            "ಬಾಳೆ" in kannadaName || "banana" in kannadaName.lowercase() -> "Banana"
            "ಕೋಕೋ" in kannadaName || "cocoa" in kannadaName.lowercase() -> "Cocoa"
            "ರಬ್ಬರ್" in kannadaName || "rubber" in kannadaName.lowercase() -> "Rubber"
            "ಮೆಣಸಿನಕಾಯಿ" in kannadaName || "chilli" in kannadaName.lowercase() -> "Chilli"
            else -> kannadaName
        }
    }

    private fun formatPrice(price: Int): String = String.format("%,d", price)
    private fun formatPriceShort(price: Int): String =
        if (price >= 1000) "${String.format("%.1f", price / 1000.0)}K" else price.toString()

    private fun updateDigestForLanguage(entity: DigestEntity, lang: String) {
        val text = if (lang == "en" && entity.english.isNotBlank()) entity.english else entity.kannada
        val links = try {
            com.google.gson.Gson().fromJson(
                entity.articleLinksJson, Array<SourceLink>::class.java
            ).toList()
        } catch (_: Exception) { emptyList() }

        cachedLinks = links
        val items = parseDigestText(text).map { item ->
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
            // Also refresh card data
            val crops = prefsStore.crops.first()
            if (crops.isNotEmpty()) fetchCardData(crops)
        }
    }

    private fun parseDigestText(raw: String): List<DigestItem> {
        if (raw.isBlank()) return emptyList()
        val lines = raw.split("\n")
            .map { it.trim().removePrefix("*").removePrefix("-").removePrefix("•").trim() }
            .filter { it.isNotBlank() }
        val items = mutableListOf<DigestItem>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("→")) { i++; continue }
            val category = detectCategory(line)
            val badge = when (category) {
                "price" -> "💰 ಬೆಲೆ"; "crop" -> "🌿 ಬೆಳೆ"; "scheme" -> "🏛️ ಯೋಜನೆ"
                "weather" -> "🌧️ ಹವಾಮಾನ"; "impact" -> "⚠️ ಪ್ರಭಾವ"; else -> "📰 ಸಾಮಾನ್ಯ"
            }
            var cleanText = line
                .replace(Regex("^[💰🌿🏛️🌧️📰⚠️]+\\s*"), "")
                .replace(Regex("^(ಬೆಲೆ|ಬೆಳೆ|ಯೋಜನೆ|ಹವಾಮಾನ|ಪ್ರಭಾವ|ಸಾಮಾನ್ಯ|Price|Crop|Scheme|Weather|Impact|General)[:\\s]*", RegexOption.IGNORE_CASE), "")
                .trim()
            var action = ""
            if ("→" in cleanText) {
                val parts = cleanText.split("→", limit = 2)
                cleanText = parts[0].trim()
                action = "→ ${parts[1].trim()}"
            }
            if (action.isEmpty() && i + 1 < lines.size && lines[i + 1].startsWith("→")) {
                action = lines[i + 1].trim()
                i++
            }
            if (cleanText.length >= 5) {
                items.add(DigestItem(category = category, badge = badge, text = cleanText, action = action))
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
            "⚠️" in line || "ಪ್ರಭಾವ" in line || "impact" in lower -> "impact"
            else -> "general"
        }
    }
}
