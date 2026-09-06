package com.dmx.khutwa.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dmx.khutwa.data.Exporter
import com.dmx.khutwa.data.MapTiles
import com.dmx.khutwa.data.RootFeatures
import com.dmx.khutwa.data.SessionRecorder
import com.dmx.khutwa.data.Settings
import com.dmx.khutwa.data.StepRepository
import com.dmx.khutwa.data.StrideCalibrator
import com.dmx.khutwa.domain.Profile
import com.dmx.khutwa.domain.Sex

@Composable
fun SettingsScreen(state: KhutwaState, vm: KhutwaViewModel) {
    val context = LocalContext.current
    val ar = state.arabicDigits
    var status by remember { mutableStateOf<String?>(null) }

    var height by remember(state.profile) { mutableStateOf(state.profile.heightCm.toString()) }
    var weight by remember(state.profile) { mutableStateOf(state.profile.weightKg.toInt().toString()) }
    var age by remember(state.profile) { mutableStateOf(state.profile.age.toString()) }
    var goal by remember(state.profile) { mutableStateOf(state.profile.goalSteps.toString()) }
    var female by remember(state.profile) { mutableStateOf(state.profile.sex == Sex.FEMALE) }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Card {
                SectionTitle("بياناتك")
                Spacer(Modifier.height(6.dp))
                Text(
                    "تُحفظ على جهازك فقط ولا تُرسل لأي مكان. الطول يحدد طول خطوتك (المسافة)، " +
                        "والوزن والعمر والجنس تدخل في معادلة السعرات.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                NumberField("الطول (سم)", height) { height = it }
                Spacer(Modifier.height(10.dp))
                NumberField("الوزن (كجم)", weight) { weight = it }
                Spacer(Modifier.height(10.dp))
                NumberField("العمر", age) { age = it }
                Spacer(Modifier.height(10.dp))
                NumberField("الهدف اليومي (خطوة)", goal) { goal = it }
                Spacer(Modifier.height(12.dp))
                ToggleRow("أنثى", female) { female = it }
                Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    vm.saveProfile(
                        Profile(
                            heightCm = height.toIntOrNull()?.coerceIn(100, 250) ?: 175,
                            weightKg = weight.toDoubleOrNull()?.coerceIn(30.0, 250.0) ?: 75.0,
                            age = age.toIntOrNull()?.coerceIn(10, 100) ?: 30,
                            sex = if (female) Sex.FEMALE else Sex.MALE,
                            goalSteps = goal.toIntOrNull()?.coerceIn(1000, 50000) ?: 7000,
                            strideA = state.profile.strideA,
                            strideB = state.profile.strideB,
                        )
                    )
                    status = "حُفظت البيانات"
                }) { Text("حفظ") }
            }
        }

        item {
            Card {
                SectionTitle("معايرة طول الخطوة")
                Spacer(Modifier.height(6.dp))
                val source = if (state.profile.strideA != null) "معايرة مقاسة" else "تقدير من الطول"
                Text(
                    "المصدر الحالي: $source",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "طول خطوتك يطول كلما زاد إيقاعك، فالتطبيق يبني منحنى بدل رقم ثابت. " +
                        "معايرة GPS تجمع عيّنات من مشيك الطبيعي وهي أدق من قياس يدوي واحد، " +
                        "لأنها تغطي سرعات مختلفة.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                val locationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    Settings.setGpsCalibration(context, granted)
                    status = if (granted) {
                        "معايرة GPS مفعّلة — امشِ بشكل طبيعي وسيتعلم التطبيق طول خطوتك"
                    } else {
                        "تحتاج إذن الموقع لمعايرة GPS"
                    }
                }
                var gpsOn by remember { mutableStateOf(Settings.gpsCalibration(context)) }
                ToggleRow("معايرة تلقائية عبر GPS", gpsOn) { want ->
                    if (want) {
                        if (StrideCalibrator.hasPermission(context)) {
                            Settings.setGpsCalibration(context, true); gpsOn = true
                        } else {
                            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            gpsOn = true
                        }
                    } else {
                        Settings.setGpsCalibration(context, false)
                        StrideCalibrator.stop(context)
                        gpsOn = false
                    }
                }
                Spacer(Modifier.height(12.dp))
                var manualSteps by remember { mutableStateOf("") }
                var manualMetres by remember { mutableStateOf("") }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        NumberField("عدد الخطوات", manualSteps) { manualSteps = it }
                    }
                    Column(Modifier.weight(1f)) {
                        NumberField("المسافة (متر)", manualMetres) { manualMetres = it }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        val s = manualSteps.toIntOrNull()
                        val m = manualMetres.toDoubleOrNull()
                        if (s != null && m != null) {
                            vm.manualCalibrate(s, m)
                            status = "تمت المعايرة اليدوية"
                        } else {
                            status = "أدخل عدد الخطوات والمسافة"
                        }
                    }) { Text("معايرة يدوية") }
                    OutlinedButton(onClick = {
                        vm.recalibrateStride { fit ->
                            status = if (fit == null) {
                                "العيّنات غير كافية بعد — امشِ مسافات أطول بسرعات مختلفة"
                            } else {
                                "أُعيدت المعايرة من عيّنات GPS"
                            }
                        }
                    }) { Text("من عيّنات GPS") }
                }
            }
        }

        item {
            Card {
                SectionTitle("العرض")
                Spacer(Modifier.height(12.dp))
                ToggleRow("أرقام عربية (٠١٢٣)", state.arabicDigits) { vm.setArabicDigits(it) }
                Spacer(Modifier.height(10.dp))
                ToggleRow("عرض السعرات الكلية (نشاط + أيض أساسي)", state.totalCalories) {
                    vm.setTotalCalories(it)
                }
            }
        }

        item {
            Card {
                SectionTitle("الصوت والخريطة")
                Spacer(Modifier.height(12.dp))
                var voice by remember { mutableStateOf(Settings.voiceCoach(context)) }
                ToggleRow("المدرّب الصوتي في الجلسات", voice) {
                    voice = it
                    Settings.setVoiceCoach(context, it)
                    SessionRecorder.setVoice(it)
                }
                Spacer(Modifier.height(12.dp))
                var cacheMb by remember { mutableStateOf(MapTiles.cacheSizeBytes(context) / 1024f / 1024f) }
                Text(
                    "ذاكرة بلاطات الخريطة: ${"%.1f".format(cacheMb)} ميجابايت. " +
                        "البلاطات تُحفظ محلياً فتُفتح الجلسة القديمة بدون إنترنت.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    MapTiles.clearCache(context)
                    cacheMb = 0f
                    status = "مُسحت ذاكرة الخريطة"
                }) { Text("مسح ذاكرة الخريطة") }
            }
        }

        item {
            Card {
                SectionTitle("النسخ والتصدير")
                Spacer(Modifier.height(6.dp))
                Text(
                    "سجلك موجود على هذا الجهاز فقط — أي إلغاء تثبيت يمحيه.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        Exporter.exportCsv(context) { f ->
                            if (f != null) Exporter.share(context, f) else status = "فشل التصدير"
                        }
                    }) { Text("تصدير الأيام") }
                    OutlinedButton(onClick = {
                        Exporter.exportMinutesCsv(context) { f ->
                            if (f != null) Exporter.share(context, f) else status = "فشل التصدير"
                        }
                    }) { Text("تصدير التفاصيل") }
                }
            }
        }

        // Root is strictly optional. Nothing in the counting path depends on it,
        // and the su prompt only ever appears because of a tap here. The Play
        // edition compiles this block out entirely (RootFeatures.AVAILABLE is a
        // constant false there and no su code exists in that flavor).
        if (RootFeatures.AVAILABLE) item {
            Card {
                SectionTitle("مزايا الروت (اختيارية)")
                Spacer(Modifier.height(6.dp))
                Text(
                    "التطبيق يعمل بالكامل بدون روت. هذي إضافات تحسّن الموثوقية فقط.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        status = "جاري الطلب…"
                        RootFeatures.requestBackgroundExemption(context) { r ->
                            status = if (r.ok) "تم إعفاء التطبيق من قيود البطارية"
                            else "تعذّر — الروت غير متاح أو مرفوض"
                        }
                    }) { Text("إعفاء من قيود البطارية") }
                    OutlinedButton(onClick = {
                        status = "جاري النسخ…"
                        RootFeatures.backupDatabase(context) { r ->
                            status = if (r.ok) "نُسخت القاعدة إلى ${r.output}"
                            else "تعذّر النسخ — الروت غير متاح"
                        }
                    }) { Text("نسخة خارج التطبيق") }
                }
            }
        }

        // The regression test for the whole engine, exposed rather than hidden:
        // stored days since boot must equal the live hardware counter.
        item {
            Card {
                SectionTitle("التشخيص")
                Spacer(Modifier.height(12.dp))
                var diag by remember { mutableStateOf<String?>(null) }
                OutlinedButton(onClick = {
                    StepRepository.diagnostics(context) { raw, stored ->
                        diag = if (raw == null) {
                            "لم يستجب الحساس"
                        } else {
                            val delta = stored - raw
                            "عدّاد الهاردوير: ${Format.number(raw, ar)}\n" +
                                "المخزَّن منذ الإقلاع: ${Format.number(stored, ar)}\n" +
                                "الفارق: ${Format.number(delta, ar)}" +
                                if (delta == 0L) "  ✓ مطابق" else "  ⚠"
                        }
                    }
                }) { Text("فحص الدقة") }
                diag?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "المفروض يكون الفارق صفر: مجموع الأيام المخزَّنة منذ آخر إقلاع = قيمة " +
                        "حساس الهاردوير بالضبط.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        status?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium) } }
    }
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
