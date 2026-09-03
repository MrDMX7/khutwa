package com.dmx.khutwa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dmx.khutwa.ui.theme.Neon

@Composable
fun AnalysisScreen(state: KhutwaState, vm: KhutwaViewModel) {
    val ar = state.arabicDigits
    val today = state.today

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Peak-30 cadence: the metric that separates "walked a lot slowly" from
        // "actually raised your heart rate", and one almost no app surfaces.
        item {
            Card {
                SectionTitle("ذروة الإيقاع")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = today?.peak30Cadence?.takeIf { it > 0 }
                            ?.let { Format.number(it, ar) } ?: "—",
                        label = "أعلى ٣٠ دقيقة (خطوة/دقيقة)",
                        accent = Neon.Run,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = today?.peak1Cadence?.takeIf { it > 0 }
                            ?.let { Format.number(it, ar) } ?: "—",
                        label = "أعلى دقيقة",
                        accent = Neon.Calories,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "ذروة الـ٣٠ دقيقة هي متوسط أعلى ٣٠ دقيقة إيقاعاً في يومك (مو بالضرورة " +
                        "متتالية). مقياس مُثبت لشدة النشاط، ومرتبط بانخفاض مخاطر القلب " +
                        "والأيض بشكل مستقل عن عدد الخطوات نفسه.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                today?.peak30Cadence?.takeIf { it > 0 }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "تصنيف اليوم: ${vm.peakCadenceLabel(it)}  ·  " +
                            "١٠٠ خطوة/دقيقة = شدة معتدلة، ١٣٠ = شدة عالية",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // WHO: 150 minutes of moderate activity a week.
        item {
            Card {
                SectionTitle("دقائق النشاط الأسبوعية")
                Spacer(Modifier.height(12.dp))
                val target = vm.weeklyActiveTarget()
                val done = state.weeklyActiveMinutes
                Text(
                    "${Format.number(done, ar)} / ${Format.number(target, ar)} دقيقة",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (done.toFloat() / target).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Neon.Active,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "توصية منظمة الصحة العالمية: ١٥٠ دقيقة نشاط معتدل أسبوعياً. " +
                        "نحسب الدقيقة نشطة إذا كان إيقاعك فيها ١٠٠ خطوة/دقيقة أو أكثر.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card {
                SectionTitle("متى تكون نشيطاً عادة")
                Spacer(Modifier.height(14.dp))
                ActivityHeatmap(state.heatmap, ar)
                Spacer(Modifier.height(10.dp))
                Text(
                    "آخر ٦٠ يوماً، مجمّعة حسب اليوم والساعة.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Ghost Runner: competing against your own history.
        item {
            Card {
                SectionTitle("أرقامك القياسية")
                Spacer(Modifier.height(12.dp))
                val r = state.records
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RecordRow("أكثر يوم",
                        r.bestDay?.let { "${Format.number(it.steps, ar)} خطوة" } ?: "—",
                        r.bestDay?.let { Format.shortDate(it.date, ar) })
                    RecordRow("أعلى ذروة إيقاع",
                        r.bestPeak30?.peak30Cadence?.let { "${Format.number(it, ar)} خ/د" } ?: "—",
                        r.bestPeak30?.let { Format.shortDate(it.date, ar) })
                    RecordRow("أطول جولة",
                        r.longestBout?.let { Format.duration(it.durationMs, ar) } ?: "—",
                        r.longestBout?.let { Format.shortDate(it.date, ar) })
                    RecordRow("أسرع كيلومتر",
                        r.fastestKmSeconds?.let {
                            "${Format.number(it / 60, ar)}:${
                                Format.digits((it % 60).toString().padStart(2, '0'), ar)
                            }"
                        } ?: "—",
                        r.fastestKmDate?.let { Format.shortDate(it, ar) })
                }
            }
        }

        if (state.insights.isNotEmpty()) {
            item {
                Card {
                    SectionTitle("كل الملاحظات")
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in state.insights) InsightRow(i)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordRow(label: String, value: String, sub: String?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
