package com.blackhole.downloader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackhole.downloader.ui.theme.Ink

@Composable
fun AboutDialog(ytdlpVersion: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        titleContentColor = Ink.TextPrimary,
        textContentColor = Ink.TextSecondary,
        title = { Text("BLACKHOLE", letterSpacing = 5.sp, fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    text = "Copy a link from YouTube, TikTok, X, or Pinterest, open the app, " +
                        "and tap the circle. The video lands in Movies/Blackhole.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "You can also share a link straight to Blackhole from any app's " +
                        "share sheet, which skips the clipboard entirely.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "TikTok downloads take the clean stream rather than the watermarked one.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Runs yt-dlp ($ytdlpVersion) and ffmpeg on the phone itself. " +
                        "No account, no server, no ad SDK in this build.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Ink.TextTertiary
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Downloading is governed by each platform's terms and by copyright " +
                        "law where you live. Keep it to your own uploads, licensed material, " +
                        "and personal use.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Ink.TextTertiary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Ink.Amber) }
        }
    )
}
