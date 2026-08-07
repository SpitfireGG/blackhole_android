package com.blackhole.downloader.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Tiny in-memory thumbnail cache so the videos list doesn't re-decode on every scroll. */
object Thumbs {

    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun of(context: Context, video: VideoFile): Bitmap? {
        val key = video.uri.toString()
        cache.get(key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            frame(context, video)
        }

        return bitmap?.also { cache.put(key, it) }
    }

    /** Tries progressively harder ways to get a frame; null means "no thumbnail". */
    private fun frame(context: Context, video: VideoFile): Bitmap? {
        // Fast path: let MediaStore produce the thumbnail. Returns null when the
        // file hasn't been indexed yet or the codec can't be decoded for a frame.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(video.uri, Size(192, 192), null)
            } else {
                null
            }
        }.getOrNull()?.let { return it }

        // Retry through MediaMetadataRetriever; it tolerates freshly published
        // files that MediaStore hasn't fully scanned yet.
        frameFromRetriever(context, video)?.let { return it }

        // Last resort: decode a frame with the bundled ffmpeg binary. Codecs that
        // Android's thumbnailer can't handle (e.g. AV1 on phones without a hardware
        // decoder) are often still decodable by ffmpeg.
        return frameFromFfmpeg(context, video)
    }

    private fun frameFromRetriever(context: Context, video: VideoFile): Bitmap? {
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

    private fun frameFromFfmpeg(context: Context, video: VideoFile): Bitmap? {
        // The engine init (idempotent) guarantees libffmpeg.so and its shared libs
        // are extracted; it already ran at app startup via BlackholeApp.
        YtdlEngine.ensureInit(context).getOrNull() ?: return null

        val binary = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!binary.isFile || !binary.canExecute()) return null

        // libffmpeg.so is dynamically linked against the libs FFmpeg.init unzipped.
        val libDirs = listOf("python", "ffmpeg", "aria2c")
            .mapNotNull { name ->
                val lib = File(context.noBackupFilesDir, "youtubedl-android/packages/$name/usr/lib")
                lib.takeIf { it.isDirectory }
            }
            .joinToString(":") { it.absolutePath }

        val cacheDir = File(context.cacheDir, "frames").apply { mkdirs() }
        val inputFile = File(cacheDir, "in_${video.id}.mp4")
        val out = File(cacheDir, "thumb_${video.id}.jpg")

        return try {
            context.contentResolver.openInputStream(video.uri)?.use { input ->
                inputFile.outputStream().use { input.copyTo(it) }
            } ?: return null

            val process = ProcessBuilder(
                binary.absolutePath,
                "-ss", "1",
                "-i", inputFile.absolutePath,
                "-frames:v", "1",
                "-vf", "scale=192:-2",
                "-y", out.absolutePath
            ).apply {
                if (libDirs.isNotEmpty()) environment()["LD_LIBRARY_PATH"] = libDirs
            }.start()

            // ffmpeg logs to stderr; drain it or the pipe fills and ffmpeg stalls.
            val drain = Thread { runCatching { process.errorStream.use { it.readBytes() } } }
            drain.isDaemon = true
            drain.start()

            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroy()
                return null
            }
            if (process.exitValue() != 0) return null
            if (!out.isFile || out.length() == 0L) return null
            BitmapFactory.decodeFile(out.absolutePath)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { inputFile.delete() }
            runCatching { out.delete() }
        }
    }
}
