package com.dmx.khutwa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmx.khutwa.domain.DayStats
import com.dmx.khutwa.ui.theme.Neon

@Composable
fun HistoryScreen(state: KhutwaState) {
    val ar = state.arabicDigits
    var range by remember { mutableStateOf(7) }
    val days = state.history.take(range)
    val closed = days.filter { !Format.isToday(it.date) }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, n) in listOf("أسبوع" to 7, "شهر" to 30, "٣ أشهر" to 90)) {
                    FilterChip(
                        selected = range == n,
                        onClick = { range = n },
                        label = { Text(label) },
                    )
                }
            }
        }

        item {
            Card {
                SectionTitle("الخطوات")
                Spacer(Modifier.height(14.dp))
                DayBarChart(
                    values = days.reversed().map { it.date to it.steps },
                    goal = state.profile.goalSteps,
                    arabicDigits = ar,
                )
            }
        }

        item {
            // Averages exclude today: a half-finished day would drag every
            // average down and make the whole screen read as a decline.
            val avg = if (closed.isEmpty()) 0L else closed.sumOf { it.steps } / closed.size
            val best = closed.maxByOrNull { it.steps }
            val metCount = closed.count { it.goalMet }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Format.number(avg, ar), "متوسط يومي", Neon.Steps, Modifier.weight(1f))
                StatTile(
                    best?.let { Format.number(it.steps, ar) } ?: "—",
                    "أفضل يوم", Neon.Active, Modifier.weight(1f),
                )
                StatTile(
                    "${Format.number(metCount, ar)}/${Format.number(closed.size, ar)}",
                    "حققت الهدف", Neon.Distance, Modifier.weight(1f),
                )
            }
        }

        item { SectionTitle("الأيام") }
        items(days) { day -> DayRow(day, ar) }
    }
}

@Composable
private fun DayRow(day: DayStats, arabicDigits: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Format.shortDate(day.date, arabicDigits) +
                        if (Format.isToday(day.date)) " · اليوم" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    Format.number(day.steps, arabicDigits),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (day.goalMet) Neon.Active else MaterialTheme.colorScheme.onSurface,
                )
            }
            // Days recorded before v2 have a total but no breakdown. Showing
            // "0.00 km" there would read as a lazy day rather than missing data.
            if (day.hasBreakdown) {
                Spacer(Modifier.height(8.dp))
                SegmentedBar(
                    incidental = day.incidentalSteps,
                    walk = day.walkSteps,
                    brisk = day.briskSteps,
                    run = day.runSteps,
                    unclassified = day.unclassifiedSteps,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${Format.km(day.distanceM, arabicDigits)} كم",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${Format.number(day.kcalActive.toLong(), arabicDigits)} سعرة",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${Format.number(day.activeMinutes, arabicDigits)} د نشاط",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    "سُجّل قبل تحديث التفاصيل — الإجمالي فقط",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
