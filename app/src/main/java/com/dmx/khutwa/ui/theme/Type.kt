package com.dmx.khutwa.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.dmx.khutwa.R

/**
 * Two faces, one job each.
 *
 * `Readex Pro` carries the Arabic and the Latin prose: it is one of the few
 * families whose Arabic is drawn as a first-class script rather than an
 * afterthought, and its open apertures survive the small label sizes this
 * layout leans on.
 *
 * `Jost` sets numerals only — geometric, single-storey, and at weight 300 the
 * five-digit step count reads as one shape rather than a wall of ink. Both are
 * variable fonts, so every weight below is an instance of one file (413 KB for
 * the pair) instead of six static cuts.
 */
private fun readex(weight: Int) = Font(
    R.font.readex_pro,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun jost(weight: Int) = Font(
    R.font.jost,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Readex = FontFamily(readex(300), readex(400), readex(500), readex(600))

/** Numerals and Latin identifiers. Never used for Arabic prose. */
val Numerals = FontFamily(jost(200), jost(300), jost(400), jost(500))

// Arabic needs more leading than Latin at the same size; these values are set
// for Arabic and left alone rather than split per-language.
private val trimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun body(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Readex,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    lineHeightStyle = trimBoth,
)

val KhutwaTypography = Typography(
    // The hero number. Jost 200 at this size is the single loudest thing in the
    // app and everything else is quieter than it on purpose.
    displayLarge = TextStyle(
        fontFamily = Numerals, fontWeight = FontWeight(300),
        fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-1.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Numerals, fontWeight = FontWeight(300),
        fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Numerals, fontWeight = FontWeight(300),
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp,
    ),
    headlineMedium = body(20, 30, FontWeight.Medium),
    headlineSmall = body(17, 26, FontWeight.Medium),
    titleLarge = body(16, 24, FontWeight.Medium),
    titleMedium = body(14, 22, FontWeight.Medium),
    bodyLarge = body(14, 24),
    bodyMedium = body(13, 22),
    bodySmall = body(12, 20),
    labelLarge = body(13, 18, FontWeight.Medium),
    labelMedium = body(12, 16),
    labelSmall = body(11, 15),
)

/** Numerals in a size that matches a body line — for inline figures. */
val NumeralInline = TextStyle(
    fontFamily = Numerals, fontWeight = FontWeight(400), fontSize = 13.sp,
)
