package com.dmx.khutwa.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dmx.khutwa.Scheduler
import com.dmx.khutwa.data.StepRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var onPermissionResult: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                StepRepository.checkpoint(applicationContext) {}
                Scheduler.scheduleAll(applicationContext)
            }
            onPermissionResult?.invoke()
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HomeScreen(
                        onRequestPermission = { after ->
                            onPermissionResult = after
                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission()) {
            StepRepository.checkpoint(applicationContext) {}
            // Idempotent (same request codes just replace the pending alarm),
            // so it's safe to call on every resume. This is the fallback path
            // for whenever permission was already granted some other way
            // (e.g. granted directly, not through the in-app button) — without
            // it, the periodic/midnight alarms could end up never scheduled.
            Scheduler.scheduleAll(applicationContext)
        }
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
}

@Composable
private fun HomeScreen(onRequestPermission: (onDone: () -> Unit) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasPermissionNow(context)) }
    var today by remember { mutableLongStateOf(StepRepository.todaySteps(context)) }
    var history by remember { mutableStateOf(StepRepository.recentDays(context, 7)) }

    fun refresh() {
        today = StepRepository.todaySteps(context)
        history = StepRepository.recentDays(context, 7)
    }

    LaunchedEffect(granted) {
        if (granted) refresh()
    }

    Scaffold(
        topBar = {
            Surface {
                Text("خطوة", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(20.dp, 14.dp))
            }
        }
    ) { pad ->
        if (!granted) {
            PermissionScreen(Modifier.padding(pad)) {
                onRequestPermission {
                    granted = hasPermissionNow(context)
                    refresh()
                }
            }
        } else {
            Column(Modifier.padding(pad).fillMaxSize().padding(20.dp)) {
                Text("اليوم", fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                Text(
                    "$today",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("خطوة", fontSize = 15.sp, color = Color.Gray)

                Spacer(Modifier.height(28.dp))
                Text("آخر ٧ أيام", fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.height(10.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history) { (date, steps) ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatDate(date), fontSize = 14.sp)
                                Text("$steps", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionScreen(modifier: Modifier = Modifier, onGrant: () -> Unit) {
    Box(modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("خطوة يحتاج إذن عدّاد الخطوات", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "يقرأ عدّاد الخطوات بالهاردوير مباشرة، يحسب خطواتك حتى لو ما فتحت " +
                    "التطبيق طول اليوم — بدون الاعتماد على خدمة تشتغل بالخلفية باستمرار.",
                fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center, lineHeight = 21.sp
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onGrant) { Text("منح الإذن") }
        }
    }
}

private fun hasPermissionNow(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED

private fun formatDate(iso: String): String = try {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)
    SimpleDateFormat("EEE d MMM", Locale("ar")).format(d ?: Date())
} catch (e: Exception) {
    iso
}
