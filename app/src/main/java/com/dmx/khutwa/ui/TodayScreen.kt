package com.dmx.khutwa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmx.khutwa.domain.ActivityClass
import com.dmx.khutwa.domain.Bout
import com.dmx.khutwa.domain.Insights
import com.dmx.khutwa.ui.theme.Neon

@Composable
fun TodayScreen(state: KhutwaState) {
    val today = state.today ?: return
    val ar = state.arabicDigits
    val goal = if (today.goal > 0) today.goal else state.profile.goalSteps
    val progress = if (goal > 0) today.steps.toFloat() / goal else 0f

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StepRing(
                    progress = progress,
                    centerLabel = Format.number(today.steps, ar),
                    caption = "من ${Format.number(goal, ar)} خطوة",
                    subCaption = state.ghost?.let { g ->
                        val sign = if (g.ahead) "+" else "−"
                        "$sign${Format.number(kotlin.math.abs(g.delta), ar)} مقارنة بـ" +
                            " ${Format.shortDate(g.referenceDate, ar)}"
                    },
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = if (today.hasBreakdown) Format.km(today.distanceM, ar) else "—",
                    label = "كيلومتر", accent = Neon.Distance, modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = if (today.hasBreakdown)
                        Format.number(state.displayedKcal().toLong(), ar) else "—",
                    label = if (state.totalCalories) "سعرة (كلي)" else "سعرة نشاط",
                    accent = Neon.Calories, modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = Format.number(today.activeMinutes, ar),
                    label = "دقيقة نشاط", accent = Neon.Active, modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = if (today.floors > 0) Format.decimal(today.floors, 1, ar) else "—",
                    label = "طابق", accent = Neon.Floors, modifier = Modifier.weight(1f),
                )
            }
        }

        // The split the user specifically asked for: walking steps vs running steps.
        if (today.hasBreakdown) {
            item {
                Card {
                    SectionTitle("توزيع الخطوات")
                    Spacer(Modifier.height(12.dp))
                    SegmentedBar(
                        incidental = today.incidentalSteps,
                        walk = today.walkSteps,
                        brisk = today.briskSteps,
                        run = today.runSteps,
                        unclassified = today.unclassifiedSteps,
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (today.runSteps > 0) {
                            LegendDot(Neon.Run, "ركض", Format.number(today.runSteps, ar))
                        }
                        LegendDot(Neon.Brisk, "مشي سريع", Format.number(today.briskSteps, ar))
                        LegendDot(Neon.Walk, "مشي", Format.number(today.walkSteps, ar))
                        LegendDot(Neon.Incidental, "حركة متفرقة",
                            Format.number(today.incidentalSteps, ar))
                        if (today.unclassifiedSteps > 0) {
                            // Honest about the gap rather than silently folding
                            // these into "walking": the hardware counter saw them,
                            // the detector's FIFO didn't keep their timestamps.
                            LegendDot(Color(0xFF2C3644), "غير مصنّف",
                                Format.number(today.unclassifiedSteps, ar))
                        }
                    }
                }
            }
        }

        item {
            Card {
                SectionTitle("نشاطك خلال اليوم")
                Spacer(Modifier.height(14.dp))
                HourlyChart(state.hourly, state.hourlyClass, ar)
            }
        }

        if (state.insights.isNotEmpty()) {
            item {
                Card {
                    SectionTitle("ملاحظات")
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in state.insights.take(3)) InsightRow(i)
                    }
                }
            }
        }

        if (state.bouts.isNotEmpty()) {
            item { SectionTitle("جولات اليوم") }
            items(state.bouts.reversed()) { bout -> BoutCard(bout, ar) }
        }
    }
}

@Composable
fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun InsightRow(insight: Insights.Insight) {
    val accent = when (insight.tone) {
        Insights.Tone.GOOD -> Neon.Active
        Insights.Tone.WARN -> Neon.Calories
        Insights.Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top) {
        Text("•", color = accent, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Text(
            insight.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BoutCard(bout: Bout, arabicDigits: Boolean) {
    val (label, color) = when (bout.activityClass) {
        ActivityClass.RUN -> "ركض" to Neon.Run
        ActivityClass.BRISK -> "مشي سريع" to Neon.Brisk
        else -> "مشي" to Neon.Walk
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = color)
                Text(
                    "${Format.clock(bout.startMs, arabicDigits)} – " +
                        Format.clock(bout.endMs, arabicDigits),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${Format.number(bout.steps, arabicDigits)} خطوة",
                    style = MaterialTheme.typography.bodyMedium)
                Text("${Format.km(bout.distanceM, arabicDigits)} كم",
                    style = MaterialTheme.typography.bodyMedium)
                Text(Format.duration(bout.durationMs, arabicDigits),
                    style = MaterialTheme.typography.bodyMedium)
                Text("${Format.number(bout.avgCadence, arabicDigits)} خ/د",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
