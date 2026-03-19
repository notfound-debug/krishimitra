package com.krishi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishi.app.ui.theme.KrishiColors
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onComplete: (OnboardingProfile) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    // Profile state
    var selectedDistrict by remember { mutableStateOf("") }
    var selectedTaluk by remember { mutableStateOf("") }
    var selectedCrops by remember { mutableStateOf(setOf<String>()) }
    var landAcres by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("kn") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        // Skip status bar area
        Spacer(Modifier.height(32.dp))

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = false, // Only move via buttons (not skippable)
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> FeaturesPage()
                2 -> WhyPage()
                3 -> ProfilePage(
                    selectedDistrict = selectedDistrict,
                    onDistrictChange = { selectedDistrict = it; selectedTaluk = "" },
                    selectedTaluk = selectedTaluk,
                    onTalukChange = { selectedTaluk = it },
                    selectedCrops = selectedCrops,
                    onCropToggle = { crop ->
                        selectedCrops = if (crop in selectedCrops)
                            selectedCrops - crop else selectedCrops + crop
                    },
                    landAcres = landAcres,
                    onLandChange = { landAcres = it },
                    selectedLanguage = selectedLanguage,
                    onLanguageChange = { selectedLanguage = it },
                )
            }
        }

        // Dots + Button
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) KrishiColors.Primary
                                else KrishiColors.Border,
                            ),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Button
            val isLastPage = pagerState.currentPage == 3
            val buttonText = if (isLastPage) "ಸಿದ್ಧ — ಆರಂಭಿಸೋಣ! ✓" else "ಮುಂದೆ →"

            Text(
                text = buttonText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KrishiColors.Primary)
                    .clickable {
                        if (isLastPage) {
                            onComplete(
                                OnboardingProfile(
                                    district = selectedDistrict,
                                    taluk = selectedTaluk,
                                    crops = selectedCrops.toList(),
                                    landAcres = landAcres.toDoubleOrNull() ?: 0.0,
                                    language = selectedLanguage,
                                )
                            )
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
            )
        }
    }
}

data class OnboardingProfile(
    val district: String,
    val taluk: String,
    val crops: List<String>,
    val landAcres: Double,
    val language: String,
)

// --- Page 1: Welcome ---
@Composable
fun WelcomePage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🌾", fontSize = 64.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            "ಕೃಷಿಮಿತ್ರಗೆ ಸ್ವಾಗತ!",
            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = KrishiColors.Primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "ನಿಮ್ಮ AI ಕೃಷಿ ಸಹಾಯಕ. ಪ್ರತಿದಿನ ಕೃಷಿ ಸುದ್ದಿ, ಮಂಡಿ ಬೆಲೆ, ಸರ್ಕಾರಿ ಯೋಜನೆ, ಮತ್ತು ನಿಮ್ಮ ಪ್ರಶ್ನೆಗಳಿಗೆ ಉತ್ತರ — ಕನ್ನಡದಲ್ಲಿ.",
            fontSize = 16.sp, color = KrishiColors.TextSecondary,
            textAlign = TextAlign.Center, lineHeight = 24.sp,
        )
    }
}

// --- Page 2: Features ---
@Composable
fun FeaturesPage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val features = listOf(
            "📰" to "ಪ್ರತಿದಿನ ಕೃಷಿ ಸುದ್ದಿ — ಏನು ಮಾಡಬೇಕು ಎಂದು ಹೇಳುತ್ತೇವೆ",
            "💬" to "ಕೃಷಿ ಬಗ್ಗೆ ಪ್ರಶ್ನೆ ಕೇಳಿ — AI ಉತ್ತರ ಕೊಡುತ್ತದೆ",
            "📊" to "ಮಂಡಿ ಬೆಲೆ — ಎಲ್ಲಿ ಮಾರಾಟ ಲಾಭಕರ ಎಂದು ತಿಳಿಯಿರಿ",
            "🏛️" to "ಸರ್ಕಾರಿ ಯೋಜನೆ — ನಿಮಗೆ ಅರ್ಹ ಯೋಜನೆ ಹುಡುಕಿ",
        )

        Text(
            "ಏನೆಲ್ಲಾ ಇದೆ?",
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KrishiColors.Primary,
        )
        Spacer(Modifier.height(24.dp))

        features.forEach { (emoji, text) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(emoji, fontSize = 28.sp, modifier = Modifier.width(44.dp))
                Text(
                    text, fontSize = 16.sp, color = KrishiColors.TextPrimary,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

// --- Page 3: Why KrishiMitra ---
@Composable
fun WhyPage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🤝", fontSize = 64.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            "ಉಚಿತ. ಕನ್ನಡದಲ್ಲಿ. ನಿಮಗಾಗಿ.",
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KrishiColors.Primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "✓ ಯಾವುದೇ ಶುಲ್ಕವಿಲ್ಲ — ಸಂಪೂರ್ಣ ಉಚಿತ\n" +
            "✓ ಕನ್ನಡ ಮತ್ತು ಇಂಗ್ಲಿಷ್ ಎರಡೂ\n" +
            "✓ ದಕ್ಷಿಣ ಕನ್ನಡ, ಉಡುಪಿ, ಉತ್ತರ ಕನ್ನಡ ಬೆಳೆಗಳಿಗೆ\n" +
            "✓ ಅಡಿಕೆ, ತೆಂಗು, ಭತ್ತ, ಕಾಳುಮೆಣಸು, ಗೋಡಂಬಿ",
            fontSize = 16.sp, color = KrishiColors.TextSecondary,
            lineHeight = 28.sp,
        )
    }
}

// --- Page 4: Profile Setup ---

private val DISTRICTS = mapOf(
    "ದಕ್ಷಿಣ ಕನ್ನಡ" to listOf("ಮಂಗಳೂರು", "ಪುತ್ತೂರು", "ಸುಳ್ಯ", "ಬಂಟ್ವಾಳ", "ಬೆಳ್ತಂಗಡಿ"),
    "ಉಡುಪಿ" to listOf("ಉಡುಪಿ", "ಕುಂದಾಪುರ", "ಕಾರ್ಕಳ", "ಬ್ರಹ್ಮಾವರ"),
    "ಉತ್ತರ ಕನ್ನಡ" to listOf("ಸಿರ್ಸಿ", "ಸಿದ್ದಾಪುರ", "ಕುಮಟಾ", "ಹೊನ್ನಾವರ", "ಅಂಕೋಲಾ"),
)

private val CROPS = listOf(
    "🌴 ಅಡಿಕೆ", "🥥 ತೆಂಗು", "🌾 ಭತ್ತ", "🫚 ಕಾಳುಮೆಣಸು",
    "🥜 ಗೋಡಂಬಿ", "🍫 ಕೋಕೋ", "🌳 ರಬ್ಬರ್", "🌶️ ಮೆಣಸಿನಕಾಯಿ",
)

@Composable
fun ProfilePage(
    selectedDistrict: String,
    onDistrictChange: (String) -> Unit,
    selectedTaluk: String,
    onTalukChange: (String) -> Unit,
    selectedCrops: Set<String>,
    onCropToggle: (String) -> Unit,
    landAcres: String,
    onLandChange: (String) -> Unit,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            "ನಿಮ್ಮ ವಿವರ ತುಂಬಿ",
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KrishiColors.Primary,
        )
        Text(
            "ನಿಮಗೆ ಸರಿಯಾದ ಮಾಹಿತಿ ಕೊಡಲು ಇದು ಸಹಾಯ ಮಾಡುತ್ತದೆ",
            fontSize = 14.sp, color = KrishiColors.TextMuted,
        )

        Spacer(Modifier.height(20.dp))

        // District
        FormLabel("📍 ನಿಮ್ಮ ಜಿಲ್ಲೆ")
        ChipSelector(
            options = DISTRICTS.keys.toList(),
            selected = selectedDistrict,
            onSelect = onDistrictChange,
        )

        // Taluk
        if (selectedDistrict.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            FormLabel("📍 ನಿಮ್ಮ ತಾಲೂಕು")
            ChipSelector(
                options = DISTRICTS[selectedDistrict] ?: emptyList(),
                selected = selectedTaluk,
                onSelect = onTalukChange,
            )
        }

        // Crops
        Spacer(Modifier.height(16.dp))
        FormLabel("🌿 ನೀವು ಯಾವ ಬೆಳೆ ಬೆಳೆಯುತ್ತೀರಿ?")
        Spacer(Modifier.height(6.dp))
        FlowRow(items = CROPS) { crop ->
            val isSelected = crop in selectedCrops
            Text(
                text = crop,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else KrishiColors.TextSecondary,
                modifier = Modifier
                    .padding(2.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) KrishiColors.Primary else Color.White)
                    .border(
                        1.5.dp,
                        if (isSelected) KrishiColors.Primary else KrishiColors.Border,
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onCropToggle(crop) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        // Land
        Spacer(Modifier.height(16.dp))
        FormLabel("📐 ನಿಮ್ಮ ಭೂಮಿ (ಎಕರೆ)")
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = landAcres,
            onValueChange = { if (it.length <= 5) onLandChange(it) },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 16.sp, color = KrishiColors.TextPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(KrishiColors.Background)
                .border(1.5.dp, KrishiColors.Border, RoundedCornerShape(10.dp))
                .padding(14.dp),
            decorationBox = { inner ->
                if (landAcres.isEmpty()) Text("ಉದಾ: 2.5", fontSize = 16.sp, color = KrishiColors.TextMuted)
                inner()
            },
        )

        // Language
        Spacer(Modifier.height(16.dp))
        FormLabel("🗣️ ಭಾಷೆ")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            listOf("kn" to "ಕನ್ನಡ", "en" to "English").forEach { (code, label) ->
                val isSel = selectedLanguage == code
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) Color.White else KrishiColors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSel) KrishiColors.Primary else Color.White)
                        .border(1.5.dp, if (isSel) KrishiColors.Primary else KrishiColors.Border, RoundedCornerShape(20.dp))
                        .clickable { onLanguageChange(code) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun FormLabel(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = KrishiColors.TextPrimary,
    )
}

@Composable
fun ChipSelector(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(items = options) { option ->
        val isSel = option == selected
        Text(
            text = option,
            fontSize = 14.sp,
            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
            color = if (isSel) Color.White else KrishiColors.TextSecondary,
            modifier = Modifier
                .padding(2.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isSel) KrishiColors.Primary else Color.White)
                .border(
                    1.5.dp,
                    if (isSel) KrishiColors.Primary else KrishiColors.Border,
                    RoundedCornerShape(20.dp),
                )
                .clickable { onSelect(option) }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

// Simple flow row (wraps items to next line)
@Composable
fun FlowRow(items: List<String>, content: @Composable (String) -> Unit) {
    // Using a simple Column + Row approach for wrapping
    var currentRow = mutableListOf<String>()
    val rows = mutableListOf<List<String>>()

    items.forEach { item ->
        currentRow.add(item)
        if (currentRow.size >= 3) {
            rows.add(currentRow.toList())
            currentRow = mutableListOf()
        }
    }
    if (currentRow.isNotEmpty()) rows.add(currentRow)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { item -> content(item) }
            }
        }
    }
}
