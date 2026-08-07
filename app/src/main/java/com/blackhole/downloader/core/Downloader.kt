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
                // under a format id containing "download", so excluding it leaves the
                // clean stream. The trailing /b is a safety net if the ids ever change.
                request.addOption("-f", "bv*+ba/b[format_id!*=download]/b")
                request.addOption("--merge-output-format", "mp4")
            }

            // Non-YouTube platforms always pull the best quality available.
            Platform.PINTEREST, Platform.INSTAGRAM, Platform.TWITTER, Platform.FACEBOOK,
            Platform.REDDIT, Platform.OTHER -> {
                request.addOption("-f", "bv*+ba/b")
                request.addOption("--merge-output-format", "mp4")
            }

            Platform.YOUTUBE -> {
                // Honour the quality cap first, then fall back to whatever is best.
                // Prioritising height over container means a selected resolution is
                // never silently downgraded just because it isn't available as mp4.
                // AV1 is excluded: many phones (e.g. Snapdragon 695) have no AV1
                // hardware decoder, so Android's thumbnailer and editors can't draw
                // a frame from AV1 files. VP9/H.264 stay fully decodable and VP9
                // still reaches 1440p/4K. Audio prefers AAC over Opus for the same
                // reason (Opus-in-MP4 is a rough edge for some editors).
                val format = if (cap > 0) {
                    "bv*[height<=?$cap][vcodec!*=av01]+ba[ext=m4a]/bv*[height<=?$cap][vcodec!*=av01]+ba/b[height<=?$cap]/b"
                } else {
                    "bv*[vcodec!*=av01]+ba[ext=m4a]/bv*[vcodec!*=av01]+ba/b"
                }
                request.addOption("-f", format)
                request.addOption("--merge-output-format", "mp4")
                // The android client is throttled to ~720p by YouTube; the tv and web
                // clients expose the full resolution ladder (1440p/4K etc).
                request.addOption("--extractor-args", "youtube:player_client=default,tv,web_safari")
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
