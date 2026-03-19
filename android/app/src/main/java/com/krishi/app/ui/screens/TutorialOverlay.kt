package com.krishi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

private data class TutorialStep(
    val icon: String,
    val titleKn: String,
    val titleEn: String,
    val descKn: String,
    val descEn: String,
)

private val steps = listOf(
    TutorialStep("⌂", "ಮುಖಪುಟ", "Home",
        "ಪ್ರತಿದಿನ ಕೃಷಿ ಸುದ್ದಿ ಇಲ್ಲಿ ಬರುತ್ತದೆ. ಬೆಲೆ, ಹವಾಮಾನ, ಯೋಜನೆ — ಎಲ್ಲವೂ ಒಂದೇ ಕಡೆ.",
        "Daily farm news appears here. Prices, weather, schemes — all in one place."),
    TutorialStep("✦", "ಕೇಳಿ", "Ask",
        "ಕೃಷಿ ಬಗ್ಗೆ ಪ್ರಶ್ನೆ ಕೇಳಿ. AI ಕನ್ನಡದಲ್ಲಿ ಉತ್ತರ ಕೊಡುತ್ತದೆ.",
        "Ask any farming question. AI answers in Kannada."),
    TutorialStep("◎", "ಬೆಲೆ", "Prices",
        "ಮಂಡಿ ಬೆಲೆ ನೋಡಿ. ಎಲ್ಲಿ ಮಾರಾಟ ಲಾಭಕರ ಎಂದು ತಿಳಿಯಿರಿ.",
        "Check mandi prices. Know where to sell for best price."),
    TutorialStep("▤", "ಯೋಜನೆ", "Schemes",
        "ಸರ್ಕಾರಿ ಯೋಜನೆ ಹುಡುಕಿ. ನೇರವಾಗಿ ಅರ್ಜಿ ಸಲ್ಲಿಸಿ.",
        "Find government schemes. Apply directly."),
)

@Composable
fun TutorialOverlay(language: String, onComplete: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }
    val step = steps[currentStep]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) { },
    ) {
        // Tutorial card in the center-bottom area (above where nav bar would be)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Icon
            Text(
                text = step.icon,
                fontSize = 48.sp,
            )

            Spacer(Modifier.height(12.dp))

            // Tab name
            Text(
                text = if (language == "kn") step.titleKn else step.titleEn,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = KrishiColors.Primary,
            )

            Spacer(Modifier.height(8.dp))

            // Description
            Text(
                text = if (language == "kn") step.descKn else step.descEn,
                fontSize = 16.sp,
                color = KrishiColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )

            Spacer(Modifier.height(20.dp))

            // Step indicator
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(steps.size) { i ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i == currentStep) KrishiColors.Primary
                                else KrishiColors.Border,
                            ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Button
            val isLast = currentStep == steps.size - 1
            Text(
                text = if (isLast) {
                    if (language == "kn") "ಆರಂಭಿಸೋಣ! ✓" else "Let's start! ✓"
                } else {
                    if (language == "kn") "ಮುಂದೆ →" else "Next →"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KrishiColors.Primary)
                    .clickable {
                        if (isLast) onComplete()
                        else currentStep++
                    }
                    .padding(vertical = 14.dp),
            )
        }
    }
}
