package com.dmx.khutwa.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.dmx.khutwa.R
import com.dmx.khutwa.Scheduler
import com.dmx.khutwa.data.SessionRecorder
import com.dmx.khutwa.data.Settings
import com.dmx.khutwa.data.StepDetectorService
import com.dmx.khutwa.data.StepRepository
import com.dmx.khutwa.ui.theme.KhutwaTheme

class MainActivity : ComponentActivity() {

    private var onPermissionResult: (() -> Unit)? = null

    private val requestActivityRecognition =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                StepRepository.checkpoint(applicationContext) {}
                Scheduler.scheduleAll(applicationContext)
                startTracking()
            }
            onPermissionResult?.invoke()
        }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KhutwaTheme(dark = true) {
                // Arabic-first: force RTL regardless of device locale.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Root(
                        hasPermission = ::hasPermission,
                        requestPermission = { done ->
                            onPermissionResult = done
                            requestActivityRecognition.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        },
                        onOnboarded = {
                            Settings.setOnboarded(applicationContext, true)
                            askNotificationPermission()
                            startTracking()
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission()) {
            StepRepository.checkpoint(applicationContext) {}
            // Idempotent: identical request codes replace the pending alarms, so
            // this is the recovery path if the self-rescheduling chain ever broke.
            Scheduler.scheduleAll(applicationContext)
            if (Settings.isOnboarded(applicationContext)) startTracking()
            // Heal any session left with impossible numbers by the background
            // throttling that the foreground service now prevents.
            SessionRecorder.repairImplausibleSessions(applicationContext)
        }
    }

    private fun startTracking() {
        runCatching { StepDetectorService.start(applicationContext) }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACTIVITY_RECOGNITION
    ) == PackageManager.PERMISSION_GRANTED
}

private enum class Tab(val label: String, val icon: Int) {
    TODAY("اليوم", R.drawable.ic_steps),
    SESSION("جلسة", R.drawable.ic_run),
    HISTORY("السجل", R.drawable.ic_history),
    ANALYSIS("التحليل", R.drawable.ic_analysis),
    KNOWLEDGE("المعرفة", R.drawable.ic_book),
    // Six tabs leave each label narrow; "الإعدادات" wrapped to two lines.
    SETTINGS("إعدادات", R.drawable.ic_settings),
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun Root(
    hasPermission: () -> Boolean,
    requestPermission: (() -> Unit) -> Unit,
    onOnboarded: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasPermission()) }
    var onboarded by remember { mutableStateOf(Settings.isOnboarded(context)) }

    if (!granted) {
        PermissionScreen(onRequest = { requestPermission { granted = hasPermission() } })
        return
    }
    if (!onboarded) {
        OnboardingScreen(onDone = {
            onOnboarded()
            onboarded = true
        })
        return
    }

    val vm: KhutwaViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.TODAY) }

    LaunchedEffect(Unit) { vm.checkpointAndRefresh() }

    // On the unfolded inner screen there is room for a rail plus a wider
    // content column; folded, that same rail would eat a third of the width.
    val activity = LocalContext.current as ComponentActivity
    val widthClass = calculateWindowSizeClass(activity).widthSizeClass
    val expanded = widthClass != WindowWidthSizeClass.Compact

    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                for (t in Tab.entries) {
                    NavigationRailItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(painterResource(t.icon), t.label) },
                        label = { Text(t.label, maxLines = 1, softWrap = false) },
                    )
                }
            }
            Box(Modifier.weight(1f)) { TabContent(tab, vm, state) }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    for (t in Tab.entries) {
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(painterResource(t.icon), t.label) },
                            label = {
                                Text(
                                    t.label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) { TabContent(tab, vm, state) }
        }
    }
}

@Composable
private fun TabContent(tab: Tab, vm: KhutwaViewModel, state: KhutwaState) {
    when (tab) {
        Tab.TODAY -> TodayScreen(state)
        Tab.SESSION -> SessionScreen(state, vm)
        Tab.HISTORY -> HistoryScreen(state)
        Tab.ANALYSIS -> AnalysisScreen(state, vm)
        Tab.KNOWLEDGE -> KnowledgeScreen(state)
        Tab.SETTINGS -> SettingsScreen(state, vm)
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "خطوة يحتاج إذن عدّاد الخطوات",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "يقرأ عدّاد الخطوات بالهاردوير مباشرة، ويحسب خطواتك حتى لو ما فتحت " +
                        "التطبيق طول اليوم — بدون الاعتماد على خدمة تشتغل بالخلفية باستمرار.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRequest) { Text("منح الإذن") }
            }
        }
    }
}
