/*
 * PricesScreen.kt — Mandi prices screen (Feature 3).
 *
 * Matches approved mockup:
 * 1. Crop selector chips (horizontal scroll)
 * 2. Hero price card (best APMC, big price, trend)
 * 3. Price table (all APMCs with min/max/trend)
 * 4. Recommendation box (yellow, tells farmer where to sell)
 */

package com.krishi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishi.app.data.remote.ApiClient
import com.krishi.app.data.remote.PriceEntry
import com.krishi.app.data.remote.PricesResponse
import com.krishi.app.ui.theme.KrishiColors
import kotlinx.coroutines.launch

private val apmcNameKn = mapOf(
    "Mangalore" to "ಮಂಗಳೂರು",
    "Puttur" to "ಪುತ್ತೂರು",
    "Sullia" to "ಸುಳ್ಯ",
    "Bantwal" to "ಬಂಟ್ವಾಳ",
    "Belthangady" to "ಬೆಳ್ತಂಗಡಿ",
    "Udupi" to "ಉಡುಪಿ",
    "Kundapura" to "ಕುಂದಾಪುರ",
    "Karkala" to "ಕಾರ್ಕಳ",
)

private fun formatDate(dateStr: String, language: String): String {
    val parts = dateStr.split("-")
    if (parts.size != 3) return dateStr
    val year = parts[0]
    val month = parts[1].toIntOrNull() ?: return dateStr
    val day = parts[2].toIntOrNull() ?: return dateStr
    if (month < 1 || month > 12) return dateStr
    val knMonths = listOf("ಜನವರಿ", "ಫೆಬ್ರವರಿ", "ಮಾರ್ಚ್", "ಏಪ್ರಿಲ್", "ಮೇ", "ಜೂನ್", "ಜುಲೈ", "ಆಗಸ್ಟ್", "ಸೆಪ್ಟೆಂಬರ್", "ಅಕ್ಟೋಬರ್", "ನವೆಂಬರ್", "ಡಿಸೆಂಬರ್")
    val enMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val monthName = if (language == "kn") knMonths[month - 1] else enMonths[month - 1]
    return "$day $monthName $year"
}

private data class CropChip(val name: String, val nameKn: String, val emoji: String)

private val crops = listOf(
    CropChip("Arecanut", "ಅಡಿಕೆ", "🌴"),
    CropChip("Coconut", "ತೆಂಗು", "🥥"),
    CropChip("Copra", "ಕೊಬ್ಬರಿ", "🥥"),
    CropChip("Rice", "ಭತ್ತ", "🌾"),
    CropChip("Black Pepper", "ಕಾಳುಮೆಣಸು", ""),
    CropChip("Cashew", "ಗೋಡಂಬಿ", "🥜"),
    CropChip("Banana", "ಬಾಳೆ", "🍌"),
)

@Composable
fun PricesScreen(language: String) {
    var selectedCrop by remember { mutableStateOf(crops[0]) }
    var pricesData by remember { mutableStateOf<PricesResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun loadPrices(crop: String) {
        scope.launch {
            isLoading = true
            try {
                pricesData = ApiClient.service.getPrices(crop)
            } catch (_: Exception) { }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadPrices(selectedCrop.name) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(KrishiColors.Background),
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KrishiColors.Surface)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("KRISHI", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = KrishiColors.Primary)
                    Text(
                        if (language == "kn") "APMC ಮಂಡಿ ಬೆಲೆಗಳು • ದಕ / ಉಡುಪಿ"
                        else "APMC Mandi Prices • DK / Udupi",
                        fontSize = 12.sp, color = KrishiColors.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(KrishiColors.Border))
        }

        // Crop chips
        item {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                crops.forEach { crop ->
                    val selected = crop == selectedCrop
                    Text(
                        text = "${crop.emoji} ${if (language == "kn") crop.nameKn else crop.name}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.White else KrishiColors.TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) KrishiColors.Primary else KrishiColors.Surface)
                            .border(
                                1.5.dp,
                                if (selected) KrishiColors.Primary else KrishiColors.Border,
                                RoundedCornerShape(20.dp),
                            )
                            .clickable {
                                selectedCrop = crop
                                loadPrices(crop.name)
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KrishiColors.Primary)
                }
            }
            return@LazyColumn
        }

        val data = pricesData ?: return@LazyColumn

        // Hero price card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(KrishiColors.Primary)
                    .padding(18.dp),
            ) {
                Column {
                    Text(
                        if (language == "kn") "${data.crop_kn} — ಇಂದಿನ ಅತ್ಯುತ್ತಮ ಬೆಲೆ"
                        else "${data.crop} — Today's Best Price",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), letterSpacing = 1.sp,
                    )
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                        val bestPrice = data.prices.firstOrNull()
                        Text(
                            "₹${formatPrice(bestPrice?.max_price ?: 0)}",
                            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (language == "kn") "/ಕ್ವಿಂ @ ${data.best_apmc}"
                            else "/qtl @ ${data.best_apmc}",
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    val bestTrend = data.prices.firstOrNull()?.trend ?: ""
                    val bestDir = data.prices.firstOrNull()?.trend_direction ?: ""
                    if (bestTrend.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        val arrow = when (bestDir) { "up" -> "▲"; "down" -> "▼"; else -> "—" }
                        Text(
                            "$arrow $bestTrend ${if (language == "kn") "ನಿನ್ನೆಗಿಂತ" else "from yesterday"}",
                            fontSize = 13.sp, color = Color.White,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    // Date stamp
                    if (data.date.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "📅 ${formatDate(data.date, language)}",
                            fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }

        // Section header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (language == "kn") "// ಎಲ್ಲಾ APMC" else "// All APMCs",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = KrishiColors.Primary, letterSpacing = 1.sp,
                )
                Text(
                    "${data.prices.size} ${if (language == "kn") "ಮಂಡಿ" else "mandis"}",
                    fontSize = 11.sp, color = KrishiColors.TextSecondary,
                    modifier = Modifier.background(KrishiColors.BorderLight, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        // Price table
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, KrishiColors.Border, RoundedCornerShape(12.dp))
                    .background(KrishiColors.Surface),
            ) {
                // Table header
                PriceTableRow(
                    apmc = "APMC",
                    min = if (language == "kn") "ಕನಿಷ್ಠ" else "Min",
                    max = if (language == "kn") "ಗರಿಷ್ಠ" else "Max",
                    trend = if (language == "kn") "ಟ್ರೆಂಡ್" else "Trend",
                    isHeader = true,
                    isBest = false,
                    bestApmc = "",
                    language = language,
                )
                data.prices.forEach { price ->
                    PriceTableRow(
                        apmc = price.apmc,
                        min = "₹${formatPriceShort(price.min_price)}",
                        max = "₹${formatPriceShort(price.max_price)}",
                        trend = price.trend,
                        isHeader = false,
                        isBest = price.apmc == data.best_apmc,
                        bestApmc = data.best_apmc,
                        trendDirection = price.trend_direction,
                        language = language,
                    )
                }
            }
        }

        // Recommendation box
        item {
            val reco = if (language == "kn") data.recommendation_kn else data.recommendation_en
            if (reco.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .background(KrishiColors.RecoBg, RoundedCornerShape(10.dp))
                        .border(1.5.dp, KrishiColors.RecoBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        if (language == "kn") "💡 ಶಿಫಾರಸು" else "💡 Recommendation",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KrishiColors.PriceBadgeText,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(reco, fontSize = 15.sp, color = KrishiColors.TextPrimary, lineHeight = 22.sp)
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PriceTableRow(
    apmc: String, min: String, max: String, trend: String,
    isHeader: Boolean, isBest: Boolean, bestApmc: String,
    trendDirection: String = "",
    language: String = "kn",
) {
    val displayApmc = if (!isHeader && language == "kn") apmcNameKn[apmc] ?: apmc else apmc
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isHeader) Modifier.background(Color(0xFFF5F5F5)) else Modifier)
            .padding(horizontal = 14.dp, vertical = if (isHeader) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(2f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                displayApmc,
                fontSize = if (isHeader) 9.sp else 12.sp,
                fontWeight = if (isHeader || isBest) FontWeight.Bold else FontWeight.Normal,
                color = if (isHeader) KrishiColors.TextMuted else KrishiColors.TextPrimary,
            )
            if (isBest) {
                Spacer(Modifier.width(6.dp))
                Text(
                    if (apmc == bestApmc) "ಉತ್ತಮ" else "",
                    fontSize = 9.sp, fontWeight = FontWeight.Bold, color = KrishiColors.Primary,
                    modifier = Modifier
                        .background(KrishiColors.PrimaryLight, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Text(min, fontSize = if (isHeader) 9.sp else 11.sp, color = if (isHeader) KrishiColors.TextMuted else KrishiColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(
            max, fontSize = if (isHeader) 9.sp else 11.sp,
            fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
            color = if (isHeader) KrishiColors.TextMuted else if (isBest) KrishiColors.Primary else KrishiColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            trend, fontSize = if (isHeader) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                isHeader -> KrishiColors.TextMuted
                trendDirection == "up" -> KrishiColors.TrendUp
                trendDirection == "down" -> KrishiColors.TrendDown
                else -> KrishiColors.TrendNeutral
            },
            modifier = Modifier.weight(1f),
        )
    }
    if (!isHeader) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 14.dp).background(KrishiColors.BorderLight))
    }
}

private fun formatPrice(price: Int): String {
    return String.format("%,d", price)
}

private fun formatPriceShort(price: Int): String {
    return if (price >= 1000) "${String.format("%.1f", price / 1000.0)}K" else price.toString()
}
