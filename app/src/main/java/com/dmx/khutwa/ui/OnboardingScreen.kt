package com.dmx.khutwa.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dmx.khutwa.data.Settings
import com.dmx.khutwa.domain.Profile
import com.dmx.khutwa.domain.Sex
import com.dmx.khutwa.ui.theme.Neon

/**
 * First-run setup.
 *
 * Asks only for what the formulas genuinely need, and says why — height drives
 * stride length, the rest drive the calorie equations. The goal is a real
 * choice rather than a silent default, because the number people aim at is the
 * one thing in a step app that actually changes behaviour.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var height by remember { mutableStateOf("175") }
    var weight by remember { mutableStateOf("75") }
    var age by remember { mutableStateOf("30") }
    var female by remember { mutableStateOf(false) }
    var goal by remember { mutableStateOf(7000) }
    var customGoal by remember { mutableStateOf("") }

    Surface(color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text("خطوة", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "إعداد سريع مرة وحدة، عشان المسافة والسعرات تطلع دقيقة.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Card {
                    SectionTitle("بياناتك")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "تبقى على جهازك ولا تُرسل لأي خادم.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    NumberField("الطول (سم)", height) { height = it }
                    Spacer(Modifier.height(10.dp))
                    NumberField("الوزن (كجم)", weight) { weight = it }
                    Spacer(Modifier.height(10.dp))
                    NumberField("العمر", age) { age = it }
                    Spacer(Modifier.height(12.dp))
                    ToggleRow("أنثى", female) { female = it }
                }
            }

            item {
                Card {
                    SectionTitle("هدفك اليومي")
                    Spacer(Modifier.height(12.dp))
                    GoalOption(
                        selected = goal == 7000,
                        title = "٧٬٠٠٠ خطوة",
                        subtitle = "الرقم المدعوم بالأدلة. ميتا-تحليل ٢٠٢٥ في Lancet Public " +
                            "Health على ١٥ دراسة وجد انخفاضاً ٤٧٪ في الوفيات عند ٧٬٠٠٠ " +
                            "مقارنة بـ ٢٬٠٠٠ خطوة.",
                        onClick = { goal = 7000 },
                    )
                    Spacer(Modifier.height(10.dp))
                    GoalOption(
                        selected = goal == 10000,
                        title = "١٠٬٠٠٠ خطوة",
                        subtitle = "الرقم المشهور. أصله حملة تسويقية يابانية سنة ١٩٦٥ لعدّاد " +
                            "اسمه «مانبو-كي»، مو دراسة — لكنه هدف طموح صالح.",
                        onClick = { goal = 10000 },
                    )
                    Spacer(Modifier.height(10.dp))
                    GoalOption(
                        selected = goal !in listOf(7000, 10000),
                        title = "رقم من عندك",
                        subtitle = "اختر ما يناسب مستواك الحالي.",
                        onClick = { goal = customGoal.toIntOrNull() ?: 5000 },
                    )
                    if (goal !in listOf(7000, 10000)) {
                        Spacer(Modifier.height(10.dp))
                        NumberField("الهدف", customGoal) {
                            customGoal = it
                            goal = it.toIntOrNull() ?: 5000
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        Settings.saveProfile(
                            context,
                            Profile(
                                heightCm = height.toIntOrNull()?.coerceIn(100, 250) ?: 175,
                                weightKg = weight.toDoubleOrNull()?.coerceIn(30.0, 250.0) ?: 75.0,
                                age = age.toIntOrNull()?.coerceIn(10, 100) ?: 30,
                                sex = if (female) Sex.FEMALE else Sex.MALE,
                                goalSteps = goal.coerceIn(1000, 50000),
                            )
                        )
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("ابدأ") }
            }
        }
    }
}

@Composable
private fun GoalOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) Neon.Steps else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
