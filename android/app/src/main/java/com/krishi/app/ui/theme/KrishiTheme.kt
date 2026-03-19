/*
 * KrishiTheme.kt — App-wide colors and typography.
 *
 * Matches the approved HTML mockup:
 * - Light background (#FAFAFA) for sunlight readability
 * - Deep green (#1B5E20) primary
 * - Dark bottom nav (#071207) from Option D
 * - High contrast text (#222222)
 * - Category colors: orange (price), green (crop), blue (scheme), teal (weather)
 */

package com.krishi.app.ui.theme

import androidx.compose.ui.graphics.Color

object KrishiColors {
    // Primary
    val Primary = Color(0xFF1B5E20)          // Deep green
    val PrimaryDark = Color(0xFF1B3A1E)      // Bottom nav background (softer dark green)
    val PrimaryLight = Color(0xFFE8F5E9)     // Light green background
    val Accent = Color(0xFFC6FF00)           // Lime green (active nav item)

    // Backgrounds
    val Background = Color(0xFFFAFAFA)       // Main screen background
    val Surface = Color(0xFFFFFFFF)          // Cards
    val StatusBar = Color(0xFF1B5E20)        // Status bar

    // Text
    val TextPrimary = Color(0xFF222222)      // Main text — high contrast
    val TextSecondary = Color(0xFF666666)    // Subtitle text
    val TextMuted = Color(0xFF999999)        // Hints, timestamps
    val TextOnPrimary = Color(0xFFFFFFFF)    // Text on green backgrounds
    val TextOnDark = Color(0xFFA5D6A7)       // Inactive nav text (brighter, visible for elders)

    // Category badge colors (background + text pairs)
    val PriceBadgeBg = Color(0xFFFFF3E0)
    val PriceBadgeText = Color(0xFFE65100)
    val PriceBorder = Color(0xFFE65100)

    val CropBadgeBg = Color(0xFFE8F5E9)
    val CropBadgeText = Color(0xFF2E7D32)
    val CropBorder = Color(0xFF2E7D32)

    val SchemeBadgeBg = Color(0xFFE3F2FD)
    val SchemeBadgeText = Color(0xFF1565C0)
    val SchemeBorder = Color(0xFF1565C0)

    val WeatherBadgeBg = Color(0xFFE0F7FA)
    val WeatherBadgeText = Color(0xFF00838F)
    val WeatherBorder = Color(0xFF00838F)

    val GeneralBadgeBg = Color(0xFFF3E5F5)
    val GeneralBadgeText = Color(0xFF6A1B9A)
    val GeneralBorder = Color(0xFF6A1B9A)

    // Trend colors
    val TrendUp = Color(0xFF2E7D32)
    val TrendDown = Color(0xFFC62828)
    val TrendNeutral = Color(0xFF888888)

    // Borders & dividers
    val Border = Color(0xFFE0E0E0)
    val BorderLight = Color(0xFFF0F0F0)

    // Recommendation box
    val RecoBg = Color(0xFFFFF8E1)
    val RecoBorder = Color(0xFFFFE082)

    // Scheme button
    val ButtonGreen = Color(0xFF1B5E20)
    val EligibleBg = Color(0xFFE8F5E9)
    val EligibleText = Color(0xFF1B5E20)

    // Date pill
    val DatePillBg = Color(0xFFE8F5E9)
    val DatePillBorder = Color(0xFFC8E6C9)
}
