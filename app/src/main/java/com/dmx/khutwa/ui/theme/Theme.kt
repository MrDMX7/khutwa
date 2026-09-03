package com.dmx.khutwa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Dark-first palette with one neon accent per metric.
 *
 * The point of a colour per metric is that the app becomes readable at a
 * glance — you learn that violet means distance and can find it without
 * reading a label. Accents are used for fills, rings and glows only; body text
 * stays near-white, because neon text on near-black is genuinely hard to read
 * regardless of how good it looks in a screenshot.
 */
object Neon {
    val Steps = Color(0xFF22D3EE)      // cyan
    val Distance = Color(0xFFA78BFA)   // violet
    val Calories = Color(0xFFFBBF24)   // amber
    val Active = Color(0xFF34D399)     // emerald
    val Run = Color(0xFFFB7185)        // rose
    val Floors = Color(0xFF60A5FA)     // blue

    /** Class colours for the walk/brisk/run breakdown bar. */
    val Incidental = Color(0xFF475569)
    val Walk = Color(0xFF22D3EE)
    val Brisk = Color(0xFF34D399)
}

// True black-adjacent ground so OLED pixels actually switch off.
private val Ground = Color(0xFF0B0E13)
private val Surface1 = Color(0xFF141922)
private val Surface2 = Color(0xFF1C2330)
private val OnDark = Color(0xFFE8EDF5)
private val OnDarkMuted = Color(0xFF8A97AB)

private val DarkColors = darkColorScheme(
    primary = Neon.Steps,
    onPrimary = Color(0xFF00232B),
    secondary = Neon.Distance,
    tertiary = Neon.Calories,
    background = Ground,
    onBackground = OnDark,
    surface = Surface1,
    onSurface = OnDark,
    surfaceVariant = Surface2,
    onSurfaceVariant = OnDarkMuted,
    outline = Color(0xFF2C3644),
    error = Neon.Run,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7490),
    secondary = Color(0xFF6D28D9),
    tertiary = Color(0xFFB45309),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF2F7),
    onSurfaceVariant = Color(0xFF4B5563),
)

private val KhutwaTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, color = OnDarkMuted),
)

@Composable
fun KhutwaTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = KhutwaTypography,
        content = content,
    )
}
