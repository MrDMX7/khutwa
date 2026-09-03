package com.dmx.khutwa.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmx.khutwa.domain.ActivityClass
import com.dmx.khutwa.ui.theme.Neon
import androidx.compose.foundation.Canvas

/**
 * The hero progress ring.
 *
 * Two strokes: a wide, very translucent one underneath the real arc, which
 * reads as a glow without needing a blur (Compose has no cheap blur on older
 * APIs, and a real shadow layer would cost a saveLayer every frame).
 */
@Composable
fun StepRing(
    progress: Float,
    centerLabel: String,
    caption: String,
    subCaption: String? = null,
    color: Color = Neon.Steps,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1.5f),
        animationSpec = tween(900),
        label = "ring",
    )
    Box(modifier = modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2 + 8.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = color.copy(alpha = 0.12f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                // Glow pass.
                drawArc(
                    color = color.copy(alpha = 0.18f),
                    startAngle = -90f, sweepAngle = 360f * animated.coerceAtMost(1f),
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke * 2.1f, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(color, color.copy(alpha = 0.65f), color)),
                    startAngle = -90f, sweepAngle = 360f * animated.coerceAtMost(1f),
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            // Past the goal, a second lap in a lighter tone rather than a bar
            // that just sits full — overachieving should be visible.
            if (animated > 1f) {
                drawArc(
                    color = Color.White.copy(alpha = 0.85f),
                    startAngle = -90f, sweepAngle = 360f * (animated - 1f),
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke * 0.4f, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerLabel, fontSize = 52.sp, style = MaterialTheme.typography.displayLarge)
            Text(caption, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (subCaption != null) {
                Spacer(Modifier.height(4.dp))
                Text(subCaption, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(width = 26.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Walk / brisk / run split as one bar. Segments below 2% are dropped so they don't vanish into a sliver. */
@Composable
fun SegmentedBar(
    incidental: Long,
    walk: Long,
    brisk: Long,
    run: Long,
    unclassified: Long,
    modifier: Modifier = Modifier,
) {
    val total = (incidental + walk + brisk + run + unclassified).coerceAtLeast(1)
    val parts = listOf(
        run to Neon.Run,
        brisk to Neon.Brisk,
        walk to Neon.Walk,
        incidental to Neon.Incidental,
        unclassified to Color(0xFF2C3644),
    ).filter { it.first > 0 }

    Row(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        for ((value, color) in parts) {
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
fun LegendDot(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Steps per hour across the day — the "when was I active" chart.
 *
 * Bars are tinted by the intensity that hour reached rather than all one
 * colour, so a quiet hour of pottering is visibly different from half an hour
 * of running even when the totals are similar.
 */
@Composable
fun HourlyChart(
    hourly: IntArray,
    peakClassPerHour: Array<ActivityClass>,
    arabicDigits: Boolean,
    modifier: Modifier = Modifier,
) {
    val max = (hourly.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (h in 0 until 24) {
                val v = hourly[h]
                val frac = v.toFloat() / max
                val color = when (peakClassPerHour.getOrElse(h) { ActivityClass.INCIDENTAL }) {
                    ActivityClass.RUN -> Neon.Run
                    ActivityClass.BRISK -> Neon.Brisk
                    ActivityClass.WALK -> Neon.Walk
                    ActivityClass.INCIDENTAL -> Neon.Incidental
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(if (v == 0) 0.012f else (0.06f + frac * 0.94f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (v == 0) MaterialTheme.colorScheme.surfaceVariant else color)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (h in listOf(0, 6, 12, 18, 23)) {
                Text(
                    Format.digits(h.toString().padStart(2, '0'), arabicDigits),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Simple vertical bar chart for daily history. */
@Composable
fun DayBarChart(
    values: List<Pair<String, Long>>,   // date -> steps, oldest first
    goal: Int,
    arabicDigits: Boolean,
    modifier: Modifier = Modifier,
) {
    val max = (values.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(goal.toLong()).coerceAtLeast(1L)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for ((_, steps) in values) {
                val met = goal > 0 && steps >= goal
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((steps.toFloat() / max).coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (met) Neon.Steps else Neon.Steps.copy(alpha = 0.35f))
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((date, _) in values) {
                Text(
                    Format.digits(date.substring(8), arabicDigits),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Day-of-week × hour heatmap of when you are typically active.
 *
 * This is the pattern a daily total can never show: two people with identical
 * step counts can have completely different days.
 */
@Composable
fun ActivityHeatmap(
    grid: Array<IntArray>,      // [7][24]
    arabicDigits: Boolean,
    modifier: Modifier = Modifier,
) {
    val max = grid.flatMap { it.asIterable() }.maxOrNull()?.coerceAtLeast(1) ?: 1
    val dayNames = listOf("أحد", "إثن", "ثلا", "أرب", "خمي", "جمع", "سبت")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (d in 0 until 7) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    dayNames[d],
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (h in 0 until 24) {
                    val v = grid[d][h]
                    val alpha = if (v == 0) 0.05f else 0.15f + 0.85f * (v.toFloat() / max)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Neon.Steps.copy(alpha = alpha))
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth().padding(start = 30.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            for (h in listOf(0, 6, 12, 18, 23)) {
                Text(
                    Format.digits(h.toString(), arabicDigits),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
