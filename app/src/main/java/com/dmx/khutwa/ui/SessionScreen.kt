package com.dmx.khutwa.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmx.khutwa.data.SessionRecorder
import com.dmx.khutwa.data.Settings
import com.dmx.khutwa.domain.Cadence
import com.dmx.khutwa.ui.theme.Neon

/**
 * The live run screen.
 *
 * Deliberately sparse while a session is running: this is read at a glance,
 * mid-stride, often in bright sun. Big numbers, few of them, no scrolling
 * needed for the ones that matter.
 */
@Composable
fun SessionScreen(state: KhutwaState, vm: KhutwaViewModel) {
    val context = LocalContext.current
    val live by SessionRecorder.state.collectAsStateWithLifecycle()
    val ar = state.arabicDigits

    if (!live.active) {
        SessionSetup(state, vm)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()) {
                Text(
                    live.phase.name + if (live.phaseRemainingMs > 0)
                        " · ${Format.duration(live.phaseRemainingMs, ar)}" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = when (live.phase.id) {
                        0 -> Neon.Calories
                        2 -> Neon.Distance
                        else -> Neon.Active
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    Format.km(live.distanceM, ar),
                    style = MaterialTheme.typography.displayLarge,
                )
                Text("كيلومتر", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (live.distanceFromGps) "من GPS" else "تقدير من الخطوات — لا إشارة GPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (live.distanceFromGps) Neon.Active
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Format.duration(live.elapsedMs, ar), "الزمن", Neon.Steps,
                    Modifier.weight(1f))
                StatTile(Format.number(live.steps, ar), "خطوة", Neon.Distance, Modifier.weight(1f))
                StatTile(Format.number(live.kcal.toInt(), ar), "سعرة", Neon.Calories,
                    Modifier.weight(1f))
            }
        }

        // Cadence gets its own card because it is the thing the coach is
        // actively steering, and the target is the user's own baseline + 5-10%,
        // never the mythical 180.
        item {
            Card {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        SectionTitle("الإيقاع الحالي")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            Format.number(live.currentCadence, ar),
                            style = MaterialTheme.typography.displayLarge,
                            color = cadenceColor(live.currentCadence),
                        )
                        Text("خطوة/دقيقة", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("المتوسط ${Format.number(live.avgCadence, ar)}",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("الأقصى ${Format.number(live.maxCadence, ar)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (live.points.size > 2) {
            item {
                Card {
                    SectionTitle("المسار")
                    Spacer(Modifier.height(12.dp))
                    RouteCard(live.points, ar)
                    if (live.points.count { it.altM != 0.0 } > 3) {
                        Spacer(Modifier.height(14.dp))
                        ElevationProfile(live.points, ar)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { if (live.paused) SessionRecorder.resume() else SessionRecorder.pause() },
                    modifier = Modifier.weight(1f),
                ) { Text(if (live.paused) "استئناف" else "إيقاف مؤقت") }
                OutlinedButton(
                    onClick = { SessionRecorder.beginCooldown(5) },
                    modifier = Modifier.weight(1f),
                ) { Text("ابدأ التهدئة") }
            }
        }

        item {
            Button(
                onClick = { SessionRecorder.stop { vm.refresh() } },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Run),
            ) { Text("إنهاء الجلسة") }
        }
    }
}

@Composable
private fun cadenceColor(cadence: Int) = when {
    cadence >= Cadence.RUN_MIN -> Neon.Run
    cadence >= Cadence.MODERATE_MIN -> Neon.Active
    cadence > Cadence.INCIDENTAL_MAX -> Neon.Steps
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun SessionSetup(state: KhutwaState, vm: KhutwaViewModel) {
    val context = LocalContext.current
    val ar = state.arabicDigits
    var voice by remember { mutableStateOf(Settings.voiceCoach(context)) }
    var warmup by remember { mutableStateOf(5) }
    var ttsNote by remember { mutableStateOf<String?>(null) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text("جلسة جديدة", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "تسجيل مسار مع إرشاد صوتي مباشر. عدّ خطواتك اليومي يشتغل دائماً بدون " +
                    "هذي الشاشة — الجلسة للمسار والتدريب فقط.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Card {
                SectionTitle("الإحماء")
                Spacer(Modifier.height(6.dp))
                Text(
                    "٥-١٠ دقائق مشي سريع أو هرولة خفيفة. الأدلة تقول: تمارين ديناميكية، " +
                        "لا إطالة ساكنة — الإطالة الطويلة قبل الجهد تضعف القوة ٤-٧٪.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in listOf(0, 5, 10)) {
                        FilterChip(
                            selected = warmup == m,
                            onClick = { warmup = m },
                            label = { Text(if (m == 0) "بدون" else "${Format.number(m, ar)} د") },
                        )
                    }
                }
            }
        }

        item {
            Card {
                SectionTitle("المدرّب الصوتي")
                Spacer(Modifier.height(6.dp))
                Text(
                    "تصحيح الإيقاع، تقارير كل ٥ دقائق، تنبيه المنطقة الرمادية، " +
                        "ومعلومات مبنية على أبحاث. يستهدف إيقاعك أنت + ٥-١٠٪ لا رقم ١٨٠.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ToggleRow("تفعيل الصوت", voice) {
                    voice = it
                    Settings.setVoiceCoach(context, it)
                }
                ttsNote?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Neon.Calories)
                }
            }
        }

        item {
            Button(
                onClick = {
                    locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    SessionRecorder.start(
                        context = context,
                        profile = state.profile,
                        voice = voice,
                        warmupMinutes = warmup,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ابدأ") }
        }

        if (state.sessions.isNotEmpty()) {
            item { SectionTitle("جلسات سابقة") }
            items(state.sessions) { s ->
                Card {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(Format.shortDate(s.date, ar),
                                style = MaterialTheme.typography.titleMedium)
                            Text(Format.clock(s.startMs, ar),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${Format.km(s.distanceM, ar)} كم",
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${s.paceText()} د/كم · ${Format.duration(s.durationMs, ar)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
