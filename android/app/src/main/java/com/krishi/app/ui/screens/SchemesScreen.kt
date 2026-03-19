/*
 * SchemesScreen.kt — Government schemes screen (Feature 4).
 *
 * Matches approved mockup:
 * 1. Profile bar (district, crops, acres)
 * 2. Scheme cards with: eligible tag, name, benefit amount, description, apply button
 */

package com.krishi.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishi.app.data.remote.ApiClient
import com.krishi.app.data.remote.SchemeResponse
import com.krishi.app.ui.theme.KrishiColors
import kotlinx.coroutines.launch

private fun mapDistrictToApi(district: String): String = when {
    "ದಕ್ಷಿಣ ಕನ್ನಡ" in district -> "Dakshina Kannada"
    "ಉಡುಪಿ" in district -> "Udupi"
    "ಉತ್ತರ ಕನ್ನಡ" in district -> "Uttara Kannada"
    else -> district
}

private fun mapCropToApi(crop: String): String {
    val clean = crop.replace(Regex("[🌴🥥🌾🥜🍫🌳🌶️🍌] ?"), "").trim()
    return when {
        "ಅಡಿಕೆ" in clean -> "Arecanut"
        "ತೆಂಗು" in clean -> "Coconut"
        "ಭತ್ತ" in clean -> "Rice"
        "ಕಾಳುಮೆಣಸು" in clean || "ಮೆಣಸು" in clean -> "Black Pepper"
        "ಗೋಡಂಬಿ" in clean -> "Cashew"
        "ಕೊಬ್ಬರಿ" in clean -> "Copra"
        "ಬಾಳೆ" in clean -> "Banana"
        "ಕೋಕೋ" in clean -> "Cocoa"
        "ರಬ್ಬರ್" in clean -> "Rubber"
        else -> clean
    }
}

@Composable
fun SchemesScreen(
    language: String,
    district: String = "",
    crops: List<String> = emptyList(),
    landAcres: String = "",
) {
    var schemes by remember { mutableStateOf<List<SchemeResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val apiDistrict = mapDistrictToApi(district).ifEmpty { null }
                val apiCrops = crops.map { mapCropToApi(it) }.joinToString(",").ifEmpty { null }
                val acres = landAcres.toDoubleOrNull()
                schemes = ApiClient.service.getEligibleSchemes(
                    district = apiDistrict,
                    crops = apiCrops,
                    landAcres = acres,
                ).schemes
            } catch (_: Exception) {
                // Fallback to all schemes if eligible endpoint fails
                try {
                    schemes = ApiClient.service.getSchemes().schemes
                } catch (_: Exception) { }
            }
            isLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(KrishiColors.Background),
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KrishiColors.Surface)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text("KRISHI", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = KrishiColors.Primary)
                Text(
                    if (language == "kn") "ನಿಮಗೆ ಅರ್ಹ ಯೋಜನೆಗಳು" else "Eligible Schemes for You",
                    fontSize = 12.sp, color = KrishiColors.TextSecondary,
                )
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(KrishiColors.Border))
        }

        // Profile bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .background(KrishiColors.Surface, RoundedCornerShape(12.dp))
                    .border(1.5.dp, KrishiColors.Border, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("👤", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val profileText = if (district.isNotEmpty() || crops.isNotEmpty())
                        "${district.ifEmpty { "—" }} • ${crops.joinToString(", ").ifEmpty { "—" }} • ${landAcres.ifEmpty { "—" }} ${if (language == "kn") "ಎಕರೆ" else "acres"}"
                    else
                        if (language == "kn") "ಪ್ರೊಫೈಲ್ ಭರ್ತಿ ಮಾಡಿ" else "Complete your profile"
                    Text(
                        profileText,
                        fontSize = 14.sp, color = KrishiColors.TextPrimary, fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (language == "kn") "${schemes.size} ಯೋಜನೆ ಕಂಡುಬಂದಿವೆ"
                        else "${schemes.size} schemes found",
                        fontSize = 12.sp, color = KrishiColors.Primary, fontWeight = FontWeight.Bold,
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

        // Scheme cards
        items(schemes) { scheme ->
            SchemeCard(scheme = scheme, language = language)
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun SchemeCard(scheme: SchemeResponse, language: String) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .background(KrishiColors.Surface, RoundedCornerShape(12.dp))
            .border(1.5.dp, KrishiColors.Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        // Eligible tag
        Text(
            if (language == "kn") "✓ ಅರ್ಹ" else "✓ ELIGIBLE",
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KrishiColors.EligibleText,
            modifier = Modifier
                .background(KrishiColors.EligibleBg, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )

        Spacer(Modifier.height(6.dp))

        // Scheme name
        Text(
            if (language == "kn") scheme.name_kn else scheme.name_en,
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = KrishiColors.TextPrimary,
        )

        // Benefit amount
        val benefit = if (language == "kn") scheme.benefit_kn else scheme.benefit_en
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                benefit.split("/").firstOrNull() ?: benefit,
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = KrishiColors.Primary,
            )
            if ("/" in benefit) {
                Text(
                    "/${benefit.split("/").last()}",
                    fontSize = 11.sp, color = KrishiColors.TextMuted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Description
        Text(
            if (language == "kn") scheme.description_kn else scheme.description_en,
            fontSize = 14.sp, color = KrishiColors.TextSecondary, lineHeight = 20.sp,
        )

        // Step-by-step instructions
        val steps = if (language == "kn") scheme.steps_kn else scheme.steps_en
        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (language == "kn") "📝 ಹೇಗೆ ಅರ್ಜಿ ಸಲ್ಲಿಸುವುದು:" else "📝 How to apply:",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = KrishiColors.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            steps.forEachIndexed { index, step ->
                Row(modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
                    Text(
                        "${index + 1}.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = KrishiColors.Primary,
                        modifier = Modifier.width(18.dp),
                    )
                    Text(
                        step,
                        fontSize = 13.sp,
                        color = KrishiColors.TextSecondary,
                        lineHeight = 16.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Documents
        val docs = if (language == "kn") scheme.documents_kn else scheme.documents_en
        if (docs.isNotBlank()) {
            Text("📄 $docs", fontSize = 13.sp, color = KrishiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
        }

        // Deadline
        if (scheme.deadline.isNotBlank()) {
            Text(
                "⏰ ${if (language == "kn") "ಗಡುವು" else "Deadline"}: ${scheme.deadline}",
                fontSize = 13.sp, color = KrishiColors.TextSecondary, modifier = Modifier.padding(top = 2.dp),
            )
        }

        // Helpline
        if (scheme.helpline.isNotBlank()) {
            Text(
                "📞 ${scheme.helpline}",
                fontSize = 13.sp, color = KrishiColors.TextSecondary, modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        // Apply button
        Text(
            if (language == "kn") "ಅರ್ಜಿ ಸಲ್ಲಿಸಿ →" else "Apply Now →",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(KrishiColors.Primary)
                .clickable {
                    if (scheme.apply_url.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(scheme.apply_url)))
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
