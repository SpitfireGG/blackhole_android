package com.blackhole.downloader.core

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Publishes finished downloads into `Movies/Blackhole` so they show up in the
 * system gallery, and reads that folder back for the videos list.
 *
 * yt-dlp needs a real filesystem path to write to, and on Android 11+ an app can
 * only write directly to Download/ and Documents/. So downloads land in the app's
 * own external files dir first and get copied into MediaStore on completion.
 */
object MediaLibrary {

    const val ALBUM = "Blackhole"
    private val RELATIVE_PATH = "${Environment.DIRECTORY_MOVIES}/$ALBUM"

    /** Scratch space for in-flight downloads. Never needs a storage permission. */
    fun scratchDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "incoming").apply { mkdirs() }

    fun publish(context: Context, source: File, displayName: String): Uri? {
        val name = sanitise(displayName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishModern(context, source, name)
        } else {
            publishLegacy(context, source, name)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishModern(context: Context, source: File, name: String): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values) ?: return null
        runCatching {
            resolver.openOutputStream(uri, "w")?.use { out ->
                source.inputStream().use { input -> input.copyTo(out, DEFAULT_BUFFER_SIZE * 8) }
            } ?: error("could not open output stream")
        }.onFailure {
            resolver.delete(uri, null, null)
            return null
        }

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(context: Context, source: File, name: String): Uri? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            ALBUM
        )
        if (!dir.exists() && !dir.mkdirs()) return null

        var target = File(dir, name)
        var counter = 1
        while (target.exists()) {
            target = File(dir, dedupe(name, counter++))
        }

        runCatching {
            source.inputStream().use { input ->
                target.outputStream().use { out -> input.copyTo(out, DEFAULT_BUFFER_SIZE * 8) }
            }
        }.onFailure { return null }

        var scanned: Uri? = null
        MediaScannerConnection.scanFile(
            context, arrayOf(target.absolutePath), arrayOf(mimeOf(name))
        ) { _, uri -> scanned = uri }
        return scanned ?: Uri.fromFile(target)
    }

    @SuppressLint("InlinedApi")
    fun list(context: Context): List<VideoFile> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED
        )

        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Anchored to the exact album path; a bare "%Blackhole%" would also
            // match unrelated folders like Movies/MyBlackholeClips.
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf("$RELATIVE_PATH%")
        } else {
            "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/$ALBUM/%")
        }

        val out = mutableListOf<VideoFile>()
        runCatching {
            context.contentResolver.query(
                collection, projection, selection, args,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    out += VideoFile(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = cursor.getString(nameCol) ?: "video",
                        sizeBytes = cursor.getLong(sizeCol),
                        durationMs = cursor.getLong(durCol),
                        addedAtSeconds = cursor.getLong(dateCol)
                    )
                }
            }
        }
        return out
    }

    fun delete(context: Context, video: VideoFile): Boolean =
        runCatching { context.contentResolver.delete(video.uri, null, null) > 0 }
            .getOrDefault(false)

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        else -> "video/mp4"
    }

    private fun sanitise(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { "blackhole_${System.currentTimeMillis()}.mp4" }
            .take(120)

    private fun dedupe(name: String, counter: Int): String {
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "mp4")
        return "$stem ($counter).$ext"
    }
}
