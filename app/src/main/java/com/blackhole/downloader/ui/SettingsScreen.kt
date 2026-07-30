package com.blackhole.downloader.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackhole.downloader.core.Prefs
import com.blackhole.downloader.ui.theme.Ink
import com.blackhole.downloader.ui.theme.WordmarkStyle

private val QUALITY_STEPS = listOf(0 to "Best", 2160 to "4K", 1440 to "1440p", 1080 to "1080p", 720 to "720p", 480 to "480p")

@Composable
fun SettingsScreen(
    ytdlpVersion: String,
    updating: Boolean,
    onBack: () -> Unit,
    onUpdateYtdlp: () -> Unit,
    overlayEnabled: Boolean,
    onOverlayToggle: (Boolean) -> Unit
) {
    var autoUpdate by remember { mutableStateOf(Prefs.autoUpdate) }
    var nightly by remember { mutableStateOf(Prefs.nightlyChannel) }
    var shortNames by remember { mutableStateOf(Prefs.shortFilenames) }
    var autoStart by remember { mutableStateOf(Prefs.autoStartOnOpen) }
    var aria2c by remember { mutableStateOf(Prefs.useAria2c) }
    var maxHeight by remember { mutableIntStateOf(Prefs.maxHeight) }
    var overlayChecked by remember { mutableStateOf(overlayEnabled) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Ink.BackgroundTop,
                    0.5f to Ink.BackgroundMid,
                    1f to Ink.BackgroundBottom
                )
            )
            .systemBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onBack)
                    .padding(4.dp)
            ) { BackGlyph() }
        }

        Text(
            "SETTINGS",
            style = WordmarkStyle.copy(color = Ink.TextSecondary, fontSize = 16.sp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            textAlign = TextAlign.Center
        )

        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 30.dp)) {

            SectionHeader("Downloads")

            QualityRow(current = maxHeight) {
                maxHeight = it
                Prefs.maxHeight = it
            }

            ToggleRow(
                title = "Short filenames",
                subtitle = "Save as A7KD91MZQ2.mp4 instead of the video title",
                checked = shortNames
            ) { shortNames = it; Prefs.shortFilenames = it }

            ToggleRow(
                title = "Start on open",
                subtitle = "Begin downloading the moment you open the app with a fresh link copied",
                checked = autoStart
            ) { autoStart = it; Prefs.autoStartOnOpen = it }

            ToggleRow(
                title = "Use aria2c",
                subtitle = "Faster on unstable connections, occasionally struggles with livestreams",
                checked = aria2c
            ) { aria2c = it; Prefs.useAria2c = it }

            ToggleRow(
                title = "Floating bubble",
                subtitle = "Show a draggable download button over other apps",
                checked = overlayChecked
            ) { enabled ->
                overlayChecked = enabled
                onOverlayToggle(enabled)
            }

            if (overlayChecked) {
                val canDraw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
                if (!canDraw) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Grant overlay permission",
                            color = Ink.Amber,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            SectionHeader("Engine")

            ToggleRow(
                title = "Auto-update yt-dlp",
                subtitle = "Checks on launch. Platforms change their players often; a stale engine is the usual cause of failed downloads",
                checked = autoUpdate
            ) { autoUpdate = it; Prefs.autoUpdate = it }

            ToggleRow(
                title = "Nightly channel",
                subtitle = "Picks up YouTube fixes days earlier than the stable channel",
                checked = nightly
            ) { nightly = it; Prefs.nightlyChannel = it }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !updating, onClick = onUpdateYtdlp)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (updating) "Updating…" else "Update yt-dlp now",
                        color = if (updating) Ink.TextSecondary else Ink.Amber,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text("Installed: $ytdlpVersion", color = Ink.TextTertiary, fontSize = 12.sp)
                }
            }

            HorizontalDivider(color = Ink.Divider, thickness = 0.7.dp)

            Text(
                text = "Saved to Movies/Blackhole. Downloads keep running when you leave the app.",
                color = Ink.TextTertiary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = Ink.AmberDim,
        fontSize = 11.sp,
        letterSpacing = 3.sp,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = Ink.TextPrimary, fontSize = 16.sp)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Ink.TextTertiary, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink.BackgroundBottom,
                checkedTrackColor = Ink.Amber,
                uncheckedThumbColor = Ink.TextTertiary,
                uncheckedTrackColor = Ink.Surface,
                uncheckedBorderColor = Ink.Divider
            )
        )
    }
}

@Composable
private fun QualityRow(current: Int, onPick: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text("Maximum quality", color = Ink.TextPrimary, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUALITY_STEPS.forEach { (height, label) ->
                val selected = height == current
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) Ink.Amber else Ink.Surface)
                        .clickable { onPick(height) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        label,
                        color = if (selected) Ink.BackgroundBottom else Ink.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
