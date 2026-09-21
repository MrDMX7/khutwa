package com.dmx.khutwa.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmx.khutwa.ui.theme.Khutwa
import com.dmx.khutwa.ui.theme.KhutwaMark
import com.dmx.khutwa.ui.theme.KhutwaShape
import com.dmx.khutwa.ui.theme.Numerals

/**
 * The layout primitive every screen is built from.
 *
 * A tinted field fills the top of the screen and is rounded off at the bottom;
 * the content scrolls above it, and the first card is positioned so it *crosses*
 * that rounded edge. The crossing is what gives the screen a foreground and a
 * background — a card sitting fully inside a coloured box reads flat, which is
 * exactly how the previous version looked.
 */
@Composable
fun FieldScreen(
    fieldHeight: Dp = 300.dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = Khutwa
    Box(
        modifier
            .fillMaxSize()
            .background(p.ground)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(fieldHeight)
                .clip(RoundedCornerShape(bottomStart = KhutwaShape.field, bottomEnd = KhutwaShape.field))
                .background(p.field)
        ) {
            ContourTexture(Modifier.fillMaxSize())
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            content = content,
        )
    }
}

/**
 * Elevation lines, as on a walking map.
 *
 * The field needed depth without an image: flat tint read as an empty
 * rectangle, and a gradient would have been the obvious tell of a generated
 * layout. Seven nested loops at 7% opacity give the band a texture you notice
 * only when you look for it.
 */
@Composable
fun ContourTexture(modifier: Modifier = Modifier) {
    val color = Khutwa.wine
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (i in 1..7) {
            val k = i * 26f * (w / 390f)
            val path = Path().apply {
                moveTo(-40f + k * 0.2f, 60f + k * 0.9f)
                cubicTo(
                    60f + k * 0.4f, -20f - k * 0.5f,
                    230f + k * 0.8f, -10f - k * 0.4f,
                    300f + k, 70f + k * 0.6f,
                )
                cubicTo(
                    360f + k * 1.1f, 150f + k * 0.9f,
                    160f + k * 0.9f, 210f + k * 1.1f,
                    -40f + k * 0.2f, 60f + k * 0.9f,
                )
            }
            drawPath(path, color.copy(alpha = 0.07f), style = Stroke(width = 1f))
        }
    }
}

/** A floating white card. The one shadow in the system; nothing else lifts. */
@Composable
fun KCard(
    modifier: Modifier = Modifier,
    radius: Dp = KhutwaShape.card,
    padding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = Khutwa
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .shadow(
                elevation = if (p.dark) 0.dp else 18.dp,
                shape = shape,
                ambientColor = p.plum.copy(alpha = 0.30f),
                spotColor = p.plum.copy(alpha = 0.30f),
            )
            .clip(shape)
            .background(p.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Section label — the small muted line above a group. */
@Composable
fun KLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = Khutwa.muted,
    )
}

/** A figure and its unit on one baseline, always left-to-right. */
@Composable
fun KNumber(
    value: String,
    unit: String? = null,
    size: Int = 28,
    color: Color = Color.Unspecified,
    weight: Int = 300,
    modifier: Modifier = Modifier,
) {
    val p = Khutwa
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            fontFamily = Numerals,
            fontWeight = androidx.compose.ui.text.font.FontWeight(weight),
            fontSize = size.sp,
            lineHeight = (size * 1.05f).sp,
            letterSpacing = (-size / 60f).sp,
            color = if (color == Color.Unspecified) p.ink else color,
        )
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                style = MaterialTheme.typography.labelMedium,
                color = p.muted,
                modifier = Modifier.padding(bottom = (size * 0.08f).dp),
            )
        }
    }
}

/** Two-up metric card: a figure, its unit, and the line that gives it meaning. */
@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    KCard(modifier, radius = KhutwaShape.cardSmall, padding = 16.dp) {
        KLabel(title)
        Spacer(Modifier.height(6.dp))
        KNumber(value, unit, size = 26)
        Spacer(Modifier.height(4.dp))
        Text(
            sub,
            style = MaterialTheme.typography.labelSmall,
            color = Khutwa.ink2,
            maxLines = 2,
        )
    }
}

/**
 * The day as a line: 96 quarter-hour bars, the future greyed to a thin rule.
 *
 * This is the chart the old version was missing. A daily total answers "how
 * much"; only this answers "when" — and "when" is what changes behaviour, since
 * it shows the evening walk that did not happen.
 */
@Composable
fun DayTimeline(
    bins: IntArray,          // 96 quarter-hour totals
    nowBin: Int,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    arabicDigits: Boolean = true,
) {
    val p = Khutwa
    val peak = (bins.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val gap = 1.5f * density
            val barW = (size.width - gap * 95) / 96
            val base = size.height
            for (i in 0 until 96) {
                val x = i * (barW + gap)
                if (i >= nowBin) {
                    drawRoundRect(
                        color = p.rule,
                        topLeft = Offset(x, base - 2f),
                        size = Size(barW, 2f),
                        cornerRadius = CornerRadius(1f),
                    )
                    continue
                }
                val v = bins[i]
                val h = if (v == 0) 2f else (base * (v.toFloat() / peak)).coerceAtLeast(2f)
                drawRoundRect(
                    color = if (v == 0) p.rule else p.wine,
                    topLeft = Offset(x, base - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2),
                )
            }
            val markerX = nowBin.coerceIn(0, 95) * (barW + gap)
            drawLine(
                color = p.rose,
                start = Offset(markerX, 0f),
                end = Offset(markerX, base),
                strokeWidth = 1f * density,
                cap = StrokeCap.Round,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            // Hour ticks sit under their bar, so the row is laid out by weight
            // rather than spaced evenly — 06 belongs a quarter of the way in.
            for ((index, hour) in listOf(6, 12, 18).withIndex()) {
                val before = if (index == 0) hour else hour - listOf(6, 12, 18)[index - 1]
                Spacer(Modifier.weight(before / 24f))
                Text(
                    Format.digits(hour.toString().padStart(2, '0'), arabicDigits),
                    style = MaterialTheme.typography.labelSmall,
                    color = p.muted,
                    fontFamily = Numerals,
                )
            }
            Spacer(Modifier.weight(6f / 24f))
        }
    }
}

/** Progress as a ring — used small, beside the number, never as the hero. */
@Composable
fun ProgressRing(
    progress: Float,
    size: Dp = 72.dp,
    stroke: Dp = 6.dp,
    label: String? = null,
) {
    val p = Khutwa
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 2f),
        animationSpec = tween(800),
        label = "ring",
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arc = Size(this.size.width - sw, this.size.height - sw)
            drawArc(
                color = p.field,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arc,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    color = p.wine,
                    startAngle = -90f, sweepAngle = 360f * animated.coerceAtMost(1f),
                    useCenter = false, topLeft = Offset(inset, inset), size = arc,
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
            }
            // Past the goal a second, thinner lap — overachieving stays visible
            // instead of resting at a full ring.
            if (animated > 1f) {
                drawArc(
                    color = p.plum,
                    startAngle = -90f, sweepAngle = 360f * (animated - 1f),
                    useCenter = false, topLeft = Offset(inset, inset), size = arc,
                    style = Stroke(width = sw * 0.45f, cap = StrokeCap.Round),
                )
            }
        }
        if (label != null) {
            Text(
                label,
                fontFamily = Numerals,
                fontSize = 12.sp,
                color = p.wine,
            )
        }
    }
}

/** The walk / brisk / incidental / unclassified split, as one thin bar. */
@Composable
fun SplitBar(
    parts: List<Pair<Long, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val total = parts.sumOf { it.first }.coerceAtLeast(1L)
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((value, color) in parts.filter { it.first > 0 }) {
            Box(
                Modifier
                    .weight(value.toFloat() / total)
                    .fillMaxSize()
                    .background(color)
            )
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Khutwa.ink2)
        Spacer(Modifier.width(5.dp))
        Text(value, fontFamily = Numerals, fontSize = 12.sp, color = Khutwa.muted)
    }
}

/** The screen header: title, optional subtitle, and the mark on the right. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Khutwa.ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Khutwa.ink2)
            }
        }
        if (trailing != null) trailing() else KhutwaMark(30.dp)
    }
}

/** Pill selector — the segmented control used for week / month / year. */
@Composable
fun PillGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = Khutwa
    Row(
        modifier
            .clip(RoundedCornerShape(KhutwaShape.pill))
            .background(p.card)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, text ->
            val on = i == selectedIndex
            Box(
                Modifier
                    .clip(RoundedCornerShape(KhutwaShape.pill))
                    .background(if (on) p.plum else Color.Transparent)
                    .clickable { onSelect(i) }
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) p.card else p.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A row inside a grouped card: title, subtitle, and a trailing control. */
@Composable
fun KRow(
    title: String,
    subtitle: String? = null,
    last: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val p = Khutwa
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 48.dp)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = p.ink)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = p.muted)
                }
            }
            trailing?.invoke(this)
        }
        if (!last) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.rule))
        }
    }
}
