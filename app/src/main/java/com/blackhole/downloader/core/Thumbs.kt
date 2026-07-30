package com.blackhole.downloader.core

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tiny in-memory thumbnail cache so the videos list doesn't re-decode on every scroll. */
object Thumbs {

    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun of(context: Context, video: VideoFile): Bitmap? {
        val key = video.uri.toString()
        cache.get(key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(video.uri, Size(192, 192), null)
                } else {
                    legacyFrame(context, video)
                }
            }.getOrNull()
        }

        return bitmap?.also { cache.put(key, it) }
    }

    private fun legacyFrame(context: Context, video: VideoFile): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, video.uri)
            retriever.getFrameAtTime(0)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
