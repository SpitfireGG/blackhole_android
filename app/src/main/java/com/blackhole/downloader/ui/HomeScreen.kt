package com.blackhole.downloader.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackhole.downloader.core.DownloadState
import com.blackhole.downloader.core.Platform
import com.blackhole.downloader.ui.theme.Ink
import com.blackhole.downloader.ui.theme.LabelStyle
import com.blackhole.downloader.ui.theme.WordmarkStyle

@Composable
fun HomeScreen(
    state: DownloadState,
    clipboardHint: Pair<String, Platform>?,
    onTapVoid: () -> Unit,
    onCancel: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val working = state is DownloadState.Working
    val progress = (state as? DownloadState.Working)?.progress

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Ink.BackgroundTop,
                    0.45f to Ink.BackgroundMid,
                    1f to Ink.BackgroundBottom
                )
            )
            .systemBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onOpenAbout).padding(4.dp)) {
                InfoGlyph()
            }
            Box(Modifier.padding(4.dp)) { NoAdsGlyph() }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 62.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("BLACKHOLE", style = WordmarkStyle)

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val diameter = (maxWidth * 0.60f).coerceAtMost(320.dp)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VoidButton(
                        diameter = diameter,
                        progress = progress,
                        working = working,
                        contentDescription = "Download the link on your clipboard",
                        onClick = { if (working) onCancel() else onTapVoid() }
                    )

                    Spacer(Modifier.height(28.dp))

                    Box(
                        Modifier.height(52.dp).fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        StatusLine(state = state, hint = clipboardHint)
                    }
                }
            }

            Column(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenVideos)
                    .padding(horizontal = 26.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DotClusterGlyph(size = 92.dp)
                Spacer(Modifier.height(10.dp))
                Text("VIDEOS", style = LabelStyle.copy(color = Ink.Amber))
            }

            Spacer(Modifier.height(34.dp))
        }
    }
}

@Composable
private fun StatusLine(state: DownloadState, hint: Pair<String, Platform>?) {
    when (state) {
        is DownloadState.Working -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = state.status,
                color = Ink.TextPrimary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(state.platform.label)
                    if (state.queued > 0) append("  ·  ${state.queued} waiting")
                    append("  ·  tap to cancel")
                },
                color = Ink.TextTertiary,
                fontSize = 11.sp,
                letterSpacing = 0.6.sp
            )
        }

        DownloadState.Idle -> Crossfade(
            targetState = hint,
            label = "clipboard-hint"
        ) { current ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (current != null) {
                    Text(
                        text = "${current.second.label} link ready",
                        color = Ink.Amber,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = current.first,
                        color = Ink.TextTertiary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(240.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Copy a link, then tap",
                        color = Ink.TextTertiary,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
