package com.blackhole.downloader.core

import android.content.Context
import android.util.Log
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

/**
 * Owns the one-time initialisation of the bundled python + yt-dlp + ffmpeg binaries.
 * All calls are blocking; always invoke from a background dispatcher.
 */
object YtdlEngine {

    private const val TAG = "YtdlEngine"

    @Volatile
    private var initialised = false
    private val lock = Any()

    fun ensureInit(context: Context): Result<Unit> {
        if (initialised) return Result.success(Unit)
        synchronized(lock) {
            if (initialised) return Result.success(Unit)
            return runCatching {
                val app = context.applicationContext
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                // aria2c is optional; a failure here must not block downloads.
                runCatching { Aria2c.getInstance().init(app) }
                    .onFailure { Log.w(TAG, "aria2c unavailable", it) }
                initialised = true
            }.onFailure { Log.e(TAG, "engine init failed", it) }
        }
    }

    /** Pulls a fresh yt-dlp. Safe to call on every cold start; it no-ops when current. */
    fun update(context: Context): String {
        ensureInit(context).getOrElse { return "init failed" }
        return runCatching {
            val channel = if (Prefs.nightlyChannel) {
                YoutubeDL.UpdateChannel.NIGHTLY
            } else {
                YoutubeDL.UpdateChannel.STABLE
            }
            YoutubeDL.getInstance()
                .updateYoutubeDL(context.applicationContext, channel)
                ?.name ?: "unknown"
        }.getOrElse { e ->
            Log.w(TAG, "yt-dlp update failed", e)
            "update failed"
        }
    }

    fun version(context: Context): String =
        runCatching { YoutubeDL.getInstance().version(context) }.getOrNull() ?: "unknown"
}
