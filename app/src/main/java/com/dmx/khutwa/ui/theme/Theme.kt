package com.dmx.khutwa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The palette, direction "الحقل".
 *
 * Decisions here are the owner's, taken against three drawn alternatives, and
 * they are what the whole layout depends on — change one and the screens stop
 * agreeing with each other:
 *
 *  · **One accent.** Wine carries progress, actions and anything the eye should
 *    find first. The previous version gave every metric its own neon colour;
 *    six accents means no accent, and it read as a toy.
 *  · **The field.** A tinted band behind the top of each screen, rounded at the
 *    bottom, with cards crossing its edge. That crossing is the depth cue — the
 *    reason the layout has a foreground and a background at all.
 *  · **Ink is warm, not black.** #2A1E23 against #FBF8F7; pure black on pure
 *    white is a browser default, not a design.
 */
@Immutable
data class KhutwaPalette(
    val ground: Color,
    val field: Color,
    val card: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val wine: Color,
    val plum: Color,
    val rose: Color,
    val rule: Color,
    /** Walk / brisk / run / incidental, as tints of the one accent. */
    val walk: Color,
    val brisk: Color,
    val run: Color,
    val incidental: Color,
    val unclassified: Color,
    val dark: Boolean,
)

val LightPalette = KhutwaPalette(
    ground = Color(0xFFFBF8F7),
    field = Color(0xFFF3E4EA),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF2A1E23),
    ink2 = Color(0xFF66565D),
    muted = Color(0xFF9A868E),
    wine = Color(0xFF8E2547),
    plum = Color(0xFF5E2138),
    rose = Color(0xFFD9A7B8),
    rule = Color(0xFFF0E4E8),
    walk = Color(0xFF8E2547),
    brisk = Color(0xFF5E2138),
    run = Color(0xFF3A0E20),
    incidental = Color(0xFFC9A6B2),
    unclassified = Color(0xFFE6DADF),
    dark = false,
)

/**
 * The same system after dark. Not an inversion: the field stays a plum band and
 * the accent lifts to a lighter wine, because #8E2547 on near-black fails
 * contrast for text and reads as brown for fills.
 */
val DarkPalette = KhutwaPalette(
    ground = Color(0xFF14100F),
    field = Color(0xFF2A1A21),
    card = Color(0xFF1F1719),
    ink = Color(0xFFF2E9EC),
    ink2 = Color(0xFFC0AEB5),
    muted = Color(0xFF94818A),
    wine = Color(0xFFD4658A),
    plum = Color(0xFFE8A0B8),
    rose = Color(0xFF7A4257),
    rule = Color(0xFF2E2429),
    walk = Color(0xFFD4658A),
    brisk = Color(0xFFE8A0B8),
    run = Color(0xFFF3C3D2),
    incidental = Color(0xFF6E4B58),
    unclassified = Color(0xFF3A2C32),
    dark = true,
)

val LocalKhutwaPalette: ProvidableCompositionLocal<KhutwaPalette> =
    staticCompositionLocalOf { LightPalette }

/** Shorthand: `Khutwa.wine` anywhere inside the theme. */
val Khutwa: KhutwaPalette
    @Composable get() = LocalKhutwaPalette.current

object KhutwaShape {
    val field = 40.dp
    val card = 24.dp
    val cardSmall = 20.dp
    val pill = 100.dp
}

private fun schemeOf(p: KhutwaPalette) = if (p.dark) {
    darkColorScheme(
        primary = p.wine, onPrimary = Color(0xFF2A0D18),
        secondary = p.plum, onSecondary = Color(0xFF2A0D18),
        background = p.ground, onBackground = p.ink,
        surface = p.card, onSurface = p.ink,
        surfaceVariant = p.field, onSurfaceVariant = p.ink2,
        outline = p.rule, outlineVariant = p.rule,
        error = Color(0xFFE58C7A), onError = Color(0xFF3A140C),
    )
} else {
    lightColorScheme(
        primary = p.wine, onPrimary = p.card,
        secondary = p.plum, onSecondary = p.card,
        background = p.ground, onBackground = p.ink,
        surface = p.card, onSurface = p.ink,
        surfaceVariant = p.field, onSurfaceVariant = p.ink2,
        outline = p.rule, outlineVariant = p.rule,
        error = Color(0xFF9A3B2E), onError = p.card,
    )
}

@Composable
fun KhutwaTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalKhutwaPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeOf(palette),
            typography = KhutwaTypography,
            content = content,
        )
    }
}

/**
 * Kept only so the screens still awaiting the redesign keep compiling; each one
 * loses its reference as it is rewritten, and this object goes with the last of
 * them. Values are pulled onto the new palette so a half-migrated build looks
 * consistent rather than half neon.
 */
@Deprecated("Use Khutwa.<colour> from the palette instead.")
object Neon {
    val Steps = LightPalette.wine
    val Distance = LightPalette.plum
    val Calories = LightPalette.wine
    val Active = LightPalette.plum
    val Run = LightPalette.run
    val Floors = LightPalette.incidental
    val Incidental = LightPalette.incidental
    val Walk = LightPalette.walk
    val Brisk = LightPalette.brisk
}
