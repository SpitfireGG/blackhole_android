package com.blackhole.downloader.core

import android.net.Uri

data class VideoFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val addedAtSeconds: Long
) {
    val readableSize: String get() = UrlUtils.formatBytes(sizeBytes)
    val readableDuration: String get() = UrlUtils.formatDuration(durationMs)
}
