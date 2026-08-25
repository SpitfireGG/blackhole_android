package com.blackhole.downloader.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class DownloadResult(val uri: Uri, val displayName: String, val sizeBytes: Long)

object Downloader {

    private const val TAG = "Downloader"

    /** Breathing room before the automatic second attempt. */
    private const val RETRY_DELAY_MS = 1_500L

    /** Characters used for the short random filenames. Ambiguous glyphs left out. */
    private const val ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789"

    /**
     * Runs one download start to finish: yt-dlp into a scratch folder, then publish
     * the finished file into Movies/Blackhole.
     *
     * A single failed attempt is not the end: extractors rot weekly and streams
     * hiccup, so one automatic retry runs after refreshing yt-dlp. Permanent
     * failures (private, deleted, age-locked) skip the retry.
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

        val first = runAttempt(context, url, processId, onProgress)
        if (first.isSuccess || !isRetryable(first)) return@withContext first

        coroutineContext.ensureActive()
        onProgress(0f, "First try failed — refreshing the engine and retrying")
        delay(RETRY_DELAY_MS)
        // Refreshing costs a second or two and doubles as a cooldown for
        // rate-limit / bot-check style rejections. Skipped when the user opted out.
        if (Prefs.autoUpdate) YtdlEngine.update(context)
        coroutineContext.ensureActive()
        runAttempt(context, url, processId, onProgress)
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    // ---------------------------------------------------------------- attempt

    private suspend fun runAttempt(
        context: Context,
        url: String,
        processId: String,
        onProgress: (Float, String) -> Unit
    ): Result<DownloadResult> {
        val scratch = File(MediaLibrary.scratchDir(context), UUID.randomUUID().toString())
        if (!scratch.mkdirs()) {
            return Result.failure(IllegalStateException("Can't create working folder"))
        }

        return try {
            val request = buildRequest(url, UrlUtils.platformOf(url), scratch)

            coroutineContext.ensureActive()
            // Holds the engine gate so a concurrent yt-dlp update can't swap
            // binaries out from under this extraction.
            YtdlEngine.gate.withLock {
                YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
                    onProgress(progress.coerceIn(0f, 100f), line)
                }
            }

            val produced = pickOutputFile(scratch)
                ?: return Result.failure(
                    IllegalStateException("yt-dlp finished but produced no file")
                )

            val finalName = if (Prefs.shortFilenames) {
                randomId() + "." + produced.extension.ifBlank { "mp4" }
            } else {
                produced.name
            }

            val size = produced.length()
            val uri = MediaLibrary.publish(context, produced, finalName)
                ?: return Result.failure(
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

    /** Errors that a refresh-and-retry can't possibly fix aren't worth the wait. */
    private fun isRetryable(result: Result<DownloadResult>): Boolean {
        val message = result.exceptionOrNull()?.message.orEmpty()
        val permanent = listOf(
            "private video", "video unavailable", "unsupported url",
            "requested format is not available", "contains age"
        )
        return permanent.none { message.contains(it, ignoreCase = true) }
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
                // The bare `/b` at the very end is deliberate: when the clean stream
                // is missing entirely, a watermarked video beats a failed download.
                request.addOption(
                    "-f",
                    "bv*[format_id!*=download]+ba[format_id!*=download]" +
                        "/b[format_id!*=download]" +
                        "/b"
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
                // Resolution beats codec: pick the highest rung at or under the cap,
                // preferring H.264+AAC in MP4 for Android gallery/editor compatibility
                // only when it actually exists at that resolution. YouTube's H.264
                // ladder frequently stops below the top resolution while VP9 covers
                // it, so an avc1-only selector silently degrades 1080p requests to
                // ~720p. Each `/` branch is only consulted when the previous one
                // matches nothing; the final branches trade codec purity (and, last
                // of all, the cap itself) for delivering *something* watchable
                // instead of failing outright.
                val format = if (cap > 0) {
                    "bv*[ext=mp4][vcodec^=avc1][height<=?$cap]+ba[ext=m4a]" +
                        "/bv*[vcodec^=avc1][height<=?$cap]+ba" +
                        "/bv*[height<=?$cap]+ba" +
                        "/b[height<=?$cap]" +
                        "/bv*+ba/b"
                } else {
                    "bv*[ext=mp4][vcodec^=avc1]+ba[ext=m4a]" +
                        "/bv*[vcodec^=avc1]+ba" +
                        "/bv*+ba/b"
                }
                request.addOption("-f", format)
                request.addOption("--merge-output-format", "mp4")
                // Player clients are left at yt-dlp's defaults: they are retuned
                // with every release (this app tracks nightly), whereas hardcoded
                // clients rot fast. The previously pinned android_vr,tv combo was
                // serving logged-out sessions DRM'd/SABR-limited ladders that
                // capped well below 1080p.
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
