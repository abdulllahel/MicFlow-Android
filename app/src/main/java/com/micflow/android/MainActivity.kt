package com.micflow.android

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.micflow.android.ui.HomeScreen
import com.micflow.android.ui.HomeTheme
import com.micflow.android.ui.LocalTheme
import com.micflow.android.ui.LoginScreen
import com.micflow.android.ui.Palette
import com.micflow.android.ui.PermissionState
import com.micflow.android.ui.SettingsScreen
import com.micflow.android.ui.VoiceScreen
import com.micflow.android.ui.plainClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MicFlow's screens: signed out, only the login; signed in, three tabs (Home, Voice Typing,
 * Settings), in the Mac app's forest-green (or dark) frame.
 */
class MainActivity : ComponentActivity() {
    private val app get() = MicFlowApp.shared
    private var permissions by mutableStateOf<PermissionState?>(null)
    private var callbackArrived = false

    private val micRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { readPermissions() }
    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    /** Where a notification asked to go: "whatsnew", "settings" or "update". */
    private var opened by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val frame = HomeTheme.green.frame.toArgb()
        enableEdgeToEdge(SystemBarStyle.dark(frame), SystemBarStyle.dark(frame))
        handle(intent)
        setContent { Root() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Google's sign-in comes back as micflow://auth-callback?code=…; notifications say where to go. */
    private fun handle(intent: Intent?) {
        intent?.getStringExtra(MicFlowNotifications.EXTRA_OPEN)?.let { target ->
            opened = target
            if (target == "update") app.updater.checkThen { app.updater.install(it) }
        }
        val uri = intent?.data ?: return
        if (uri.scheme == "micflow" && uri.host == "auth-callback") {
            callbackArrived = true
            lifecycleScope.launch { app.account.handleCallback(uri) }
        }
    }

    override fun onResume() {
        super.onResume()
        app.activity = this
        readPermissions()
        app.keySetup.appResumed()
        app.refreshKeyState()
        MicFlowNotifications.schedule(this)
        askForNotifications()
        // Back from the browser without signing in: say so, rather than waiting forever.
        lifecycleScope.launch {
            delay(800)
            if (!callbackArrived) app.account.cancelledIfWaiting()
            callbackArrived = false
        }
    }

    override fun onPause() {
        super.onPause()
        if (app.activity === this) app.activity = null
    }

    /** Once signed in, asks once to show notifications (new posts and updates). */
    private fun askForNotifications() {
        val prefs = Settings.prefs(this)
        if (app.account.isSignedIn && android.os.Build.VERSION.SDK_INT >= 33 && !MicFlowNotifications.allowed(this)
            && !prefs.getBoolean("askedNotifications", false)) {
            prefs.edit().putBoolean("askedNotifications", true).apply()
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun readPermissions() {
        permissions = PermissionState.read(this) { micRequest.launch(Manifest.permission.RECORD_AUDIO) }
    }

    @Composable
    private fun Root() {
        @Suppress("UNUSED_VARIABLE") val revision = app.revision
        val dark = Settings.prefs(this).getBoolean(Settings.DARK, false)
        val theme = if (dark) HomeTheme.dark else HomeTheme.green
        // Right after signing in (the sign-in finishes while MicFlow is already open).
        androidx.compose.runtime.LaunchedEffect(app.account.isSignedIn) { if (app.account.isSignedIn) askForNotifications() }
        var tab by rememberSaveable { mutableStateOf("home") }
        var settingsTab by rememberSaveable { mutableStateOf("General") }
        opened?.let { target ->
            tab = if (target == "whatsnew" || target == "settings" || target == "update") "settings" else tab
            settingsTab = if (target == "whatsnew") "What's New" else "General"
            opened = null
        }
        CompositionLocalProvider(LocalTheme provides theme) {
            Box(Modifier.fillMaxSize().background(theme.frame)) {
                if (!app.account.isSignedIn) {
                    LoginScreen(app)
                } else {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        // imePadding: a text box stays above the keyboard.
                        Column(Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
                            val state = permissions ?: PermissionState.read(this@MainActivity) {}
                            when (tab) {
                                "voice" -> VoiceScreen(app, state)
                                "settings" -> SettingsScreen(app, settingsTab) { settingsTab = it }
                                else -> if (app.needsSetup) LoginScreen(app, standalone = false) else HomeScreen(app) { tab = "voice" }
                            }
                        }
                        TabBar(tab) { tab = it }
                    }
                }
            }
        }
    }

    /** Material's bottom navigation bar: icon and name always shown, in MicFlow's colours. */
    @Composable
    private fun TabBar(tab: String, select: (String) -> Unit) {
        val theme = LocalTheme.current
        NavigationBar(containerColor = theme.card, tonalElevation = 0.dp) {
            listOf(Triple("home", "Home", Icons.Filled.Home), Triple("voice", "Voice Typing", Icons.Filled.GraphicEq),
                   Triple("settings", "Settings", Icons.Filled.Settings)).forEach { (id, title, icon) ->
                NavigationBarItem(
                    selected = tab == id,
                    onClick = { select(id) },
                    icon = { Icon(icon, contentDescription = null) },
                    label = { Text(title, fontWeight = if (tab == id) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Palette.pink, selectedTextColor = Palette.cream, indicatorColor = Palette.cream,
                        unselectedIconColor = Palette.white.copy(alpha = 0.75f), unselectedTextColor = Palette.white.copy(alpha = 0.75f)),
                )
            }
        }
    }
}
