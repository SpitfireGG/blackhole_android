package com.blackhole.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackhole.downloader.core.DownloadBus
import com.blackhole.downloader.core.Prefs
import com.blackhole.downloader.core.UrlUtils
import com.blackhole.downloader.ui.AboutDialog
import com.blackhole.downloader.ui.HomeScreen
import com.blackhole.downloader.service.ClipboardVampireService
import com.blackhole.downloader.service.FloatingOverlayService
import com.blackhole.downloader.ui.MainViewModel
import com.blackhole.downloader.ui.Screen
import com.blackhole.downloader.ui.SettingsScreen
import com.blackhole.downloader.ui.VideosScreen
import com.blackhole.downloader.ui.theme.BlackholeTheme
import com.blackhole.downloader.ui.theme.Ink

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Recomputed on every resume so the settings hint rows react to the user
    // granting mic permission or enabling Blackhole under Accessibility.
    // Set for real in onCreate once the activity is attached.
    private var vampireActive by mutableStateOf(false)
    private var micGranted by mutableStateOf(false)

    /**
     * Notifications aren't required to download, but without them a background
     * download is invisible, so we ask once and carry on either way.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationsIfNeeded()
        vampireActive = ClipboardVampireService.isEnabled(this)
        micGranted = hasMicPermission()

        setContent {
            BlackholeTheme {
                val screen by viewModel.screen.collectAsStateWithLifecycle()
                val downloadState by DownloadBus.state.collectAsStateWithLifecycle()
                val videos by viewModel.videos.collectAsStateWithLifecycle()
                val loading by viewModel.loadingLibrary.collectAsStateWithLifecycle()
                val hint by viewModel.clipboardHint.collectAsStateWithLifecycle()
                val version by viewModel.ytdlpVersion.collectAsStateWithLifecycle()
                val updating by viewModel.updating.collectAsStateWithLifecycle()

                val snackbars = remember { SnackbarHostState() }
                var aboutOpen by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    viewModel.toasts.collect { snackbars.showSnackbar(it) }
                }

                Scaffold(
                    containerColor = Color.Transparent,
                    contentColor = Ink.TextPrimary,
                    snackbarHost = { SnackbarHost(snackbars) }
                ) { _ ->
                    Box(Modifier.fillMaxSize()) {
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                state = downloadState,
                                clipboardHint = hint,
                                onTapVoid = { viewModel.swallowClipboard() },
                                onCancel = { viewModel.cancel() },
                                onOpenVideos = { viewModel.go(Screen.VIDEOS) },
                                onOpenAbout = { aboutOpen = true }
                            )

                            Screen.VIDEOS -> VideosScreen(
                                videos = videos,
                                loading = loading,
                                onBack = { viewModel.go(Screen.HOME) },
                                onSettings = { viewModel.go(Screen.SETTINGS) },
                                onDelete = { viewModel.delete(it) }
                            )

                            Screen.SETTINGS -> SettingsScreen(
                                ytdlpVersion = version,
                                updating = updating,
                                onBack = { viewModel.go(Screen.VIDEOS) },
                                onUpdateYtdlp = { viewModel.updateYtdlp() },
                                overlayEnabled = Prefs.overlayEnabled,
                                onOverlayToggle = { enabled ->
                                    Prefs.overlayEnabled = enabled
                                    if (enabled) {
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                                            Settings.canDrawOverlays(this@MainActivity)
                                        ) {
                                            FloatingOverlayService.start(this@MainActivity)
                                        }
                                    } else {
                                        FloatingOverlayService.stop(this@MainActivity)
                                    }
                                },
                                ghostEnabled = Prefs.ghostMode,
                                onGhostToggle = { enabled ->
                                    Prefs.ghostMode = enabled
                                    FloatingOverlayService.refresh(this@MainActivity)
                                },
                                whisperEnabled = Prefs.whisperMode,
                                onWhisperToggle = { enabled ->
                                    Prefs.whisperMode = enabled
                                    if (enabled &&
                                        ContextCompat.checkSelfPermission(
                                            this@MainActivity, Manifest.permission.RECORD_AUDIO
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                vampireEnabled = Prefs.vampireMode,
                                onVampireToggle = { enabled ->
                                    Prefs.vampireMode = enabled
                                    if (enabled && !ClipboardVampireService.isEnabled(this@MainActivity)) {
                                        openAccessibilitySettings()
                                    }
                                },
                                onOpenAccessibilitySettings = { openAccessibilitySettings() },
                                onRequestMicPermission = {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                micPermissionGranted = micGranted,
                                vampireServiceEnabled = vampireActive
                            )
                        }

                        if (aboutOpen) {
                            AboutDialog(version) { aboutOpen = false }
                        }
                    }
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vampireActive = ClipboardVampireService.isEnabled(this)
        micGranted = hasMicPermission()
        // Clipboard reads only work while an app holds focus, which is exactly the
        // moment we want one. Android 12+ shows a system toast when this happens.
        val url = viewModel.peekClipboard()
        if (url != null && (Prefs.autoStartOnOpen || Prefs.vampireMode) && url != Prefs.lastHandledUrl) {
            viewModel.start(url)
        }

        // Start overlay if enabled and permission granted (e.g. returning from settings)
        if (Prefs.overlayEnabled) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                FloatingOverlayService.start(this)
            }
        }
    }

    /** Handles "share to Blackhole" and "open link with Blackhole". */
    private fun handleIntent(intent: Intent?) {
        val i = intent ?: return
        val candidate = when (i.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> i.dataString
            else -> null
        } ?: return

        val url = UrlUtils.extractUrl(candidate) ?: return
        if (url == Prefs.lastHandledUrl && i.action == Intent.ACTION_VIEW) return

        viewModel.go(Screen.HOME)
        viewModel.start(url)

        // Stop the same link firing again on rotation or when returning to the app.
        i.action = Intent.ACTION_MAIN
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAccessibilitySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
