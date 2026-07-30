package com.blackhole.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.blackhole.downloader.ui.MainViewModel
import com.blackhole.downloader.ui.Screen
import com.blackhole.downloader.ui.SettingsScreen
import com.blackhole.downloader.ui.VideosScreen
import com.blackhole.downloader.ui.theme.BlackholeTheme
import com.blackhole.downloader.ui.theme.Ink

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /**
     * Notifications aren't required to download, but without them a background
     * download is invisible, so we ask once and carry on either way.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationsIfNeeded()

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
                                onUpdateYtdlp = { viewModel.updateYtdlp() }
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
        // Clipboard reads only work while an app holds focus, which is exactly the
        // moment we want one. Android 12+ shows a system toast when this happens.
        val url = viewModel.peekClipboard()
        if (url != null && Prefs.autoStartOnOpen && url != Prefs.lastHandledUrl) {
            viewModel.start(url)
        }
    }

    /** Handles "share to Blackhole" and "open link with Blackhole". */
    private fun handleIntent(intent: Intent?) {
        val candidate = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return

        val url = UrlUtils.extractUrl(candidate) ?: return
        if (url == Prefs.lastHandledUrl && intent.action == Intent.ACTION_VIEW) return

        viewModel.go(Screen.HOME)
        viewModel.start(url)

        // Stop the same link firing again on rotation or when returning to the app.
        intent.action = Intent.ACTION_MAIN
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
}
