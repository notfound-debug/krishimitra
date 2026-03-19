/*
 * HomeScreen.kt — The daily digest screen (Feature 1).
 *
 * Layout (top to bottom):
 * 1. Header: "KRISHI" logo + date pill
 * 2. Hero price card (dark green): primary crop price + trend arrow
 * 3. Stats grid: 3 cards (coconut, weather, schemes)
 * 4. Section header: "// ಇಂದಿನ ಸುದ್ದಿ"
 * 5. Digest items: categorized news with Kannada action lines
 *
 * Pull-to-refresh triggers API call to fetch latest digest.
 */

package com.krishi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishi.app.ui.theme.KrishiColors
import com.krishi.app.ui.viewmodel.DigestItem
import com.krishi.app.ui.viewmodel.HomeCardData
import com.krishi.app.ui.viewmodel.HomeViewModel
import com.krishi.app.ui.viewmodel.SourceLink

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

@Composable
fun HomeHeader(language: String, taluk: String, onToggleLanguage: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KrishiColors.Surface)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "KRISHI",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = KrishiColors.Primary,
            )
            Text(
                text = if (language == "kn") "AI ಕೃಷಿ ಸಹಾಯಕ • ${taluk.ifEmpty { "ದಕ / ಉಡುಪಿ" }}"
                       else "AI Farm Assistant • ${taluk.ifEmpty { "DK / Udupi" }}",
                fontSize = 12.sp,
                color = KrishiColors.TextSecondary,
                letterSpacing = 1.sp,
            )
        }
        // Language toggle pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, KrishiColors.Primary, RoundedCornerShape(20.dp))
                .clickable { onToggleLanguage() },
        ) {
            Text(
                text = "ಕ",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (language == "kn") Color.White else KrishiColors.Primary,
                modifier = Modifier
                    .background(
                        if (language == "kn") KrishiColors.Primary else Color.Transparent,
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Text(
                text = "En",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (language == "en") Color.White else KrishiColors.Primary,
                modifier = Modifier
                    .background(
                        if (language == "en") KrishiColors.Primary else Color.Transparent,
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
fun HeroPriceCard(cards: HomeCardData, language: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(KrishiColors.Primary)
            .padding(18.dp),
    ) {
        Column {
            Text(
                text = "${cards.primaryCropName} • ${cards.primaryCropApmc.ifEmpty { "APMC" }}",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = cards.primaryCropPrice,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (language == "kn") "/ಕ್ವಿಂ" else "/qtl",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            if (cards.primaryCropTrend.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                val arrow = when (cards.primaryCropTrendDir) { "up" -> "▲"; "down" -> "▼"; else -> "—" }
                Text(
                    text = "$arrow ${cards.primaryCropTrend} ${if (language == "kn") "ನಿನ್ನೆಗಿಂತ" else "from yesterday"}",
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            // Date stamp
            if (cards.priceDate.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "📅 ${formatDate(cards.priceDate, language)} ${if (!cards.isRealPrice) if (language == "kn") "(ಅಂದಾಜು)" else "(Est.)" else ""}",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
fun StatsGrid(cards: HomeCardData, language: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Card 1: Secondary crop price
        StatCard(
            emoji = "🥥",
            value = cards.secondaryCropPrice,
            label = cards.secondaryCropName.ifEmpty { if (language == "kn") "ತೆಂಗು" else "Coconut" },
            modifier = Modifier.weight(1f),
        )
        // Card 2: Weather
        StatCard(
            emoji = if (cards.weatherDesc.contains("rain", true) || cards.weatherDesc.contains("ಮಳೆ")) "🌧️" else "☀️",
            value = cards.weatherTemp,
            label = cards.weatherDesc.take(12).ifEmpty { if (language == "kn") "ಹವಾಮಾನ" else "Weather" },
            modifier = Modifier.weight(1f),
        )
        // Card 3: Smart insight
        StatCard(
            emoji = "💡",
            value = cards.insightCrop.ifEmpty { if (language == "kn") "ಸಲಹೆ" else "Tip" },
            label = cards.insightText.take(25).ifEmpty { if (language == "kn") "ಲೋಡ್ ಆಗುತ್ತಿದೆ" else "Loading" },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun StatCard(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(1.5.dp, KrishiColors.Border, RoundedCornerShape(12.dp))
            .background(KrishiColors.Surface, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = KrishiColors.Primary, textAlign = TextAlign.Center)
        Text(label, fontSize = 11.sp, color = KrishiColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionHeader(title: String, count: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "// $title",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = KrishiColors.Primary,
            letterSpacing = 2.sp,
        )
        Text(
            text = count,
            fontSize = 11.sp,
            color = KrishiColors.TextSecondary,
            modifier = Modifier
                .background(KrishiColors.BorderLight, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun DigestItemCard(item: DigestItem, onSourceClick: (String) -> Unit = {}) {
    val borderColor = when (item.category) {
        "price" -> KrishiColors.PriceBorder
        "crop" -> KrishiColors.CropBorder
        "scheme" -> KrishiColors.SchemeBorder
        "weather" -> KrishiColors.WeatherBorder
        else -> KrishiColors.GeneralBorder
    }
    val badgeBg = when (item.category) {
        "price" -> KrishiColors.PriceBadgeBg
        "crop" -> KrishiColors.CropBadgeBg
        "scheme" -> KrishiColors.SchemeBadgeBg
        "weather" -> KrishiColors.WeatherBadgeBg
        else -> KrishiColors.GeneralBadgeBg
    }
    val badgeText = when (item.category) {
        "price" -> KrishiColors.PriceBadgeText
        "crop" -> KrishiColors.CropBadgeText
        "scheme" -> KrishiColors.SchemeBadgeText
        "weather" -> KrishiColors.WeatherBadgeText
        else -> KrishiColors.GeneralBadgeText
    }

    // Left border drawn behind the card content
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, KrishiColors.BorderLight, RoundedCornerShape(12.dp))
            .drawBehind {
                // Draw 4dp left accent border
                drawRect(
                    color = borderColor,
                    topLeft = Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                )
            }
            .background(KrishiColors.Surface)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Text(
            text = item.badge,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = badgeText,
            modifier = Modifier
                .background(badgeBg, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.text,
            fontSize = 16.sp,
            color = KrishiColors.TextPrimary,
            lineHeight = 24.sp,
        )
        if (item.action.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.action,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = KrishiColors.Primary,
            )
        }
        // Source link
        if (item.sourceUrl.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "📄 ${item.sourceName.ifEmpty { "ಮೂಲ" }} →",
                fontSize = 12.sp,
                color = KrishiColors.TextMuted,
                modifier = Modifier
                    .clickable { onSourceClick(item.sourceUrl) }
                    .padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
fun SourceLinkItem(link: SourceLink, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(KrishiColors.Surface, RoundedCornerShape(8.dp))
            .border(1.dp, KrishiColors.BorderLight, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .then(
                Modifier.clickable { onClick(link.link) }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.title,
                fontSize = 14.sp,
                color = KrishiColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = link.source,
                fontSize = 12.sp,
                color = KrishiColors.TextMuted,
            )
        }
        Text(
            text = "→",
            fontSize = 16.sp,
            color = KrishiColors.Primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val language by viewModel.language.collectAsState()
    val context = LocalContext.current

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { viewModel.refresh() },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(KrishiColors.Background),
        ) {
            item { HomeHeader(language = language, taluk = uiState.farmerTaluk, onToggleLanguage = { viewModel.toggleLanguage() }) }

            item {
                Spacer(
                    Modifier.fillMaxWidth().height(1.dp).background(KrishiColors.Border)
                )
            }

            item { HeroPriceCard(cards = uiState.cards, language = language) }

            item { StatsGrid(cards = uiState.cards, language = language) }

            item {
                SectionHeader(
                    title = "ಇಂದಿನ ಸುದ್ದಿ",
                    count = "${uiState.digestItems.size} ಸುದ್ದಿ",
                )
            }

            if (uiState.digestItems.isEmpty() && !uiState.isLoading) {
                item {
                    Text(
                        text = "ಇನ್ನೂ ಸುದ್ದಿ ಲಭ್ಯವಿಲ್ಲ. ರಿಫ್ರೆಶ್ ಮಾಡಲು ಕೆಳಕ್ಕೆ ಎಳೆಯಿರಿ.",
                        fontSize = 16.sp,
                        color = KrishiColors.TextMuted,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }

            items(uiState.digestItems) { digestItem ->
                DigestItemCard(digestItem) { url ->
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url),
                    )
                    context.startActivity(intent)
                }
            }

            // Source links section — tap to open original article
            if (uiState.sourceLinks.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "ಮೂಲ ಲೇಖನಗಳು",
                        count = "${uiState.sourceLinks.size} ಲೇಖನ",
                    )
                }

                items(uiState.sourceLinks.take(10)) { link ->
                    SourceLinkItem(link) { url ->
                        // Open the article in the phone's browser
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url),
                        )
                        context.startActivity(intent)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
