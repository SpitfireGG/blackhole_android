package com.blackhole.downloader.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackhole.downloader.core.MediaLibrary
import com.blackhole.downloader.core.Thumbs
import com.blackhole.downloader.core.VideoFile
import com.blackhole.downloader.ui.theme.Ink
import com.blackhole.downloader.ui.theme.WordmarkStyle

@Composable
fun VideosScreen(
    videos: List<VideoFile>,
    loading: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onDelete: (VideoFile) -> Unit
) {
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

            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onSettings)
                    .padding(4.dp)
            ) { GearsGlyph() }
        }

        Text(
            "BLACKHOLE",
            style = WordmarkStyle.copy(color = Ink.TextSecondary),
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        when {
            videos.isEmpty() && loading -> Placeholder("Reading your library")
            videos.isEmpty() -> Placeholder("Nothing saved yet.\nCopy a link and tap the void.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(videos, key = { it.id }) { video ->
                    VideoRow(
                        video = video,
                        onOpen = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(video.uri, "video/*")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                )
                            }
                        },
                        onShare = {
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND)
                                            .setType("video/*")
                                            .putExtra(Intent.EXTRA_STREAM, video.uri)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                        "Share video"
                                    )
                                )
                            }
                        },
                        onDelete = { onDelete(video) }
                    )
                    HorizontalDivider(color = Ink.Divider, thickness = 0.7.dp)
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
private fun VideoRow(
    video: VideoFile,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(video.id) { mutableStateOf<Bitmap?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(video.id) { thumbnail = Thumbs.of(context, video) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(46.dp)
                .height(62.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Ink.Surface),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = thumbnail
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PlayGlyph(size = 18.dp, tint = Ink.TextTertiary)
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = video.name,
                color = Ink.TextPrimary,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(video.readableSize)
                    val duration = video.readableDuration
                    if (duration.isNotEmpty()) append("  ·  $duration")
                },
                color = Ink.TextSecondary,
                fontSize = 13.sp
            )
        }

        Box {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { menuOpen = true }
                    .padding(6.dp)
            ) { FourDotsGlyph() }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(Ink.Surface)
            ) {
                DropdownMenuItem(
                    text = { Text("Play", color = Ink.TextPrimary) },
                    onClick = { menuOpen = false; onOpen() }
                )
                DropdownMenuItem(
                    text = { Text("Share", color = Ink.TextPrimary) },
                    onClick = { menuOpen = false; onShare() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Ink.Red) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun Placeholder(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                color = Ink.TextTertiary,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Movies/${MediaLibrary.ALBUM}",
                color = Ink.TextTertiary.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}
