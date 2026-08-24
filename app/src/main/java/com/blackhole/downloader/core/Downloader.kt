package com.blackhole.downloader.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class DownloadResult(val uri: Uri, val displayName: String, val sizeBytes: Long)

object Downloader {

    private const val TAG = "Downloader"

    /** Characters used for the short random filenames. Ambiguous glyphs left out. */
    private const val ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789"

    /**
     * Runs one download start to finish: yt-dlp into a scratch folder, then publish
     * the finished file into Movies/Blackhole.
     *
     * @param onProgress called with (0..100, latest yt-dlp output line)
     */
    suspend fun download(
        context: Context,
        url: String,
        processId: String,
        onProgress: (Float, String) -> Unit
    ): Result<DownloadResult> = withContext(Dispatchers.IO) {
        YtdlEngine.ensureInit(context).getOrElse { return@withContext Result.failure(it) }

        val scratch = File(MediaLibrary.scratchDir(context), UUID.randomUUID().toString())
        if (!scratch.mkdirs()) {
            return@withContext Result.failure(IllegalStateException("Can't create working folder"))
        }

        try {
            val platform = UrlUtils.platformOf(url)
            val request = buildRequest(url, platform, scratch)

            coroutineContext.ensureActive()
            YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
                onProgress(progress.coerceIn(0f, 100f), line)
            }

            val produced = pickOutputFile(scratch)
                ?: return@withContext Result.failure(
                    IllegalStateException("yt-dlp finished but produced no file")
                )

            val finalName = if (Prefs.shortFilenames) {
                randomId() + "." + produced.extension.ifBlank { "mp4" }
            } else {
                produced.name
            }

            val size = produced.length()
            val uri = MediaLibrary.publish(context, produced, finalName)
                ?: return@withContext Result.failure(
                    IllegalStateException("Couldn't save into Movies/Blackhole")
                )

            Result.success(DownloadResult(uri, finalName, size))
        } catch (t: Throwable) {
            Log.e(TAG, "download failed for $url", t)
            Result.failure(t)
        } finally {
            runCatching { scratch.deleteRecursively() }
        }
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    // ---------------------------------------------------------------- request

    private fun buildRequest(url: String, platform: Platform, scratch: File): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)

        val template = if (Prefs.shortFilenames) {
            "${scratch.absolutePath}/%(id)s.%(ext)s"
        } else {
            "${scratch.absolutePath}/%(title).80B [%(id)s].%(ext)s"
        }
        request.addOption("-o", template)

        // Behaviour that applies everywhere.
        request.addOption("--no-playlist")
        request.addOption("--no-mtime")
        request.addOption("--no-part")
        request.addOption("--no-warnings")
        request.addOption("--restrict-filenames")
        request.addOption("--retries", "6")
        request.addOption("--fragment-retries", "6")
        request.addOption("--socket-timeout", "20")
        request.addOption("--concurrent-fragments", "4")
        // Attach the platform's cover art so players that read embedded art show it.
        request.addOption("--embed-thumbnail")

        if (Prefs.useAria2c) {
            request.addOption("--downloader", "libaria2c.so")
            request.addOption("--external-downloader-args", "aria2c:--summary-interval=1")
        }

        val cap = Prefs.maxHeight

        when (platform) {
            Platform.TIKTOK -> {
                // TikTok serves two things: `play_addr` (clean) and `download_addr`
                // (stamped with the username watermark). yt-dlp exposes the stamped one
                // under a format id containing "download". Apply the exclusion to
                // every branch: an unrestricted fallback could otherwise silently
                // select the stamped copy when the clean stream is unavailable.
                request.addOption(
                    "-f",
                    "bv*[format_id!*=download]+ba[format_id!*=download]/b[format_id!*=download]"
                )
                request.addOption("--merge-output-format", "mp4")
            }

            // Non-YouTube platforms always pull the best quality available.
            Platform.PINTEREST, Platform.INSTAGRAM, Platform.TWITTER, Platform.FACEBOOK,
            Platform.REDDIT, Platform.OTHER -> {
                request.addOption("-f", "bv*+ba/b")
                request.addOption("--merge-output-format", "mp4")
            }

            Platform.YOUTUBE -> {
                // Keep YouTube downloads suitable for Android galleries and editors.
                // Merely merging into MP4 does not convert VP9/AV1 to H.264, and many
                // Android editor thumbnailers cannot decode those codecs. Require an
                // MP4 H.264 stream and AAC audio; if separate streams are unavailable,
                // fall back to a progressive MP4 that already contains both. yt-dlp
                // will choose the best H.264 resolution within the requested cap.
                val format = if (cap > 0) {
                    "bv*[ext=mp4][vcodec^=avc1][height<=?$cap]+ba[ext=m4a]/b[ext=mp4][vcodec^=avc1][height<=?$cap]"
                } else {
                    "bv*[ext=mp4][vcodec^=avc1]+ba[ext=m4a]/b[ext=mp4][vcodec^=avc1]"
                }
                request.addOption("-f", format)
                request.addOption("--merge-output-format", "mp4")
                // The default web client is bot-checked and can collapse to a 360p
                // ladder; android_vr exposes the full resolution ladder (1080p H.264,
                // 4K VP9) without those checks.
                request.addOption("--extractor-args", "youtube:player_client=android_vr,tv")
            }
        }

        return request
    }

    /** yt-dlp may leave behind fragments; the finished video is the largest real file. */
    private fun pickOutputFile(dir: File): File? = dir.walkTopDown()
        .filter { it.isFile && it.length() > 0 }
        .filterNot { it.name.endsWith(".part") }
        .filterNot { it.name.endsWith(".ytdl") }
        .filterNot { it.name.endsWith(".temp") }
        .maxByOrNull { it.length() }

    private fun randomId(length: Int = 10): String =
        (1..length).map { ID_ALPHABET.random() }.joinToString("")
}
