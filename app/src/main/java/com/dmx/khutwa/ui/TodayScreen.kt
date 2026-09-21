package com.dmx.khutwa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmx.khutwa.R
import com.dmx.khutwa.domain.Metrics
import com.dmx.khutwa.domain.MinuteBucket
import com.dmx.khutwa.domain.Streaks
import com.dmx.khutwa.ui.theme.Khutwa
import com.dmx.khutwa.ui.theme.KhutwaMark
import java.util.Calendar

/**
 * Today.
 *
 * One number, one accent, and — the part the old screen was missing — *when*
 * the steps happened. The four metric cards are deliberately the smaller half
 * of the screen: they are context for the timeline, not the point of it.
 */
@Composable
fun TodayScreen(state: KhutwaState) {
    val today = state.today ?: return
    val p = Khutwa
    val ar = state.arabicDigits
    val goal = if (today.goal > 0) today.goal else state.profile.goalSteps
    val progress = if (goal > 0) today.steps.toFloat() / goal else 0f
    val remaining = (goal - today.steps).coerceAtLeast(0L)
    val streak = remember(state.history, today) {
        Streaks.compute(state.history, today.date)
    }
    val bins = remember(state.todayMinutes) { quarterBins(state.todayMinutes) }
    val nowBin = Format.minuteOfDayNow() / 15

    FieldScreen(fieldHeight = 400.dp) {
        Spacer(Modifier.height(56.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(greetingRes()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = p.ink,
                )
                Text(
                    buildString {
                        append(Format.longDate(today.date, ar))
                        if (streak.current > 0) {
                            append(" · ")
                            append(
                                pluralDays(streak.current, ar,
                                    stringResource(R.string.streak_days_one),
                                    stringResource(R.string.streak_days_few),
                                    stringResource(R.string.streak_days_many))
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = p.ink2,
                )
            }
            KhutwaMark(34.dp)
        }

        Spacer(Modifier.height(40.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                KNumber(
                    Format.number(today.steps, ar),
                    size = 66,
                    weight = 300,
                    color = p.plum,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (remaining > 0)
                        stringResource(R.string.today_remaining, Format.number(remaining, ar))
                    else
                        stringResource(R.string.today_goal_met),
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.ink2,
                )
            }
            ProgressRing(
                progress = progress,
                label = Format.digits("${(progress * 100).toInt()}%", ar),
            )
        }

        Spacer(Modifier.height(38.dp))

        // Crosses the field's rounded edge — the card that gives the screen depth.
        KCard(padding = 18.dp) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    stringResource(R.string.today_timeline_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = p.ink,
                )
                Text(
                    stringResource(
                        R.string.today_peak_cadence,
                        Format.number(today.peak1Cadence, ar),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = p.muted,
                )
            }
            Spacer(Modifier.height(12.dp))
            DayTimeline(bins = bins, nowBin = nowBin, arabicDigits = ar)
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = stringResource(R.string.metric_distance),
                value = Format.km(today.distanceM, ar),
                unit = stringResource(R.string.unit_km),
                sub = stringResource(
                    R.string.metric_distance_sub,
                    // Stride at a typical walking cadence — the number the
                    // distance actually rests on, shown rather than hidden.
                    Format.number(
                        (Metrics.strideMetres(state.profile, 110) * 100).toInt(), ar
                    ),
                ),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = stringResource(R.string.metric_calories),
                value = Format.number(state.displayedKcal().toLong(), ar),
                unit = stringResource(R.string.unit_kcal),
                sub = stringResource(R.string.metric_calories_sub),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = stringResource(R.string.metric_active),
                value = Format.number(today.activeMinutes, ar),
                unit = stringResource(R.string.unit_minute),
                sub = stringResource(
                    R.string.metric_active_sub,
                    Format.number(state.weeklyActiveMinutes, ar),
                    Format.number(150, ar),
                ),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = stringResource(R.string.metric_brisk),
                value = Format.number(today.briskSteps + today.runSteps, ar),
                unit = stringResource(R.string.unit_step),
                sub = stringResource(
                    R.string.metric_brisk_sub,
                    Format.digits(
                        "${percent(today.briskSteps + today.runSteps, today.steps)}%",
                        ar,
                    ),
                ),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))

        KCard {
            Text(
                stringResource(R.string.today_split_title),
                style = MaterialTheme.typography.titleLarge,
                color = p.ink,
            )
            Spacer(Modifier.height(12.dp))
            SplitBar(
                parts = listOf(
                    today.walkSteps to p.walk,
                    today.briskSteps + today.runSteps to p.brisk,
                    today.incidentalSteps to p.incidental,
                    today.unclassifiedSteps to p.unclassified,
                )
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                LegendItem(p.walk, stringResource(R.string.class_walk),
                    Format.number(today.walkSteps, ar))
                LegendItem(p.brisk, stringResource(R.string.class_brisk),
                    Format.number(today.briskSteps + today.runSteps, ar))
                LegendItem(p.incidental, stringResource(R.string.class_incidental),
                    Format.number(today.incidentalSteps, ar))
                if (today.unclassifiedSteps > 0) {
                    LegendItem(p.unclassified, stringResource(R.string.class_unclassified),
                        Format.number(today.unclassifiedSteps, ar))
                }
            }
            if (today.unclassifiedSteps > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.today_unclassified_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = p.muted,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        KCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(34.dp))
                Text(
                    stringResource(R.string.today_goal_note, Format.number(goal, ar)),
                    style = MaterialTheme.typography.bodySmall,
                    color = p.ink2,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

private fun percent(part: Long, whole: Long): Int =
    if (whole <= 0) 0 else ((part * 100.0) / whole).toInt()

/** Arabic has three plural shapes for a day count; English needs two. */
private fun pluralDays(n: Int, arabic: Boolean, one: String, few: String, many: String): String {
    val digits = Format.number(n, arabic)
    return when {
        n == 1 -> one
        n == 2 -> few
        n in 3..10 -> String.format(many, digits)
        else -> String.format(one, digits)
    }
}

/** 96 quarter-hour totals for the day, in local time. */
fun quarterBins(minutes: List<MinuteBucket>): IntArray {
    val bins = IntArray(96)
    val cal = Calendar.getInstance()
    for (m in minutes) {
        cal.timeInMillis = m.tsMin * 60_000L
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        bins[(minuteOfDay / 15).coerceIn(0, 95)] += m.steps
    }
    return bins
}

private fun greetingRes(): Int {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> R.string.greeting_night
        hour < 12 -> R.string.greeting_morning
        hour < 17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }
}
