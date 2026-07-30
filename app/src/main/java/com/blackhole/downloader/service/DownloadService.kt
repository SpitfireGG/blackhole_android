package com.blackhole.downloader.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.blackhole.downloader.BlackholeApp
import com.blackhole.downloader.MainActivity
import com.blackhole.downloader.R
import com.blackhole.downloader.core.DownloadBus
import com.blackhole.downloader.core.DownloadEvent
import com.blackhole.downloader.core.DownloadState
import com.blackhole.downloader.core.Downloader
import com.blackhole.downloader.core.UrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Downloads run here rather than in the Activity so that locking the phone or
 * switching apps mid-download doesn't kill them.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)

    private var worker: Job? = null
    private var currentProcessId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        worker = scope.launch { consume() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                currentProcessId?.let { Downloader.cancel(it) }
                drain()
                stopSelfSafely()
                return START_NOT_STICKY
            }

            else -> {
                val url = intent?.getStringExtra(EXTRA_URL)
                if (url.isNullOrBlank()) {
                    stopSelfSafely()
                    return START_NOT_STICKY
                }
                pending.incrementAndGet()
                startForegroundSafely(buildProgressNotification(url, 0f, indeterminate = true))
                queue.trySend(url)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        queue.close()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- worker

    private suspend fun consume() {
        for (url in queue) {
            val platform = UrlUtils.platformOf(url)
            val processId = UUID.randomUUID().toString()
            currentProcessId = processId

            DownloadBus.publish(
                DownloadState.Working(url, platform, 0f, "Resolving ${platform.label} link", pending.get() - 1)
            )
            notify(buildProgressNotification(url, 0f, indeterminate = true))

            var lastPushed = -1
            val result = Downloader.download(this, url, processId) { progress, line ->
                val rounded = progress.toInt()
                DownloadBus.publish(
                    DownloadState.Working(
                        url = url,
                        platform = platform,
                        progress = progress,
                        status = summarise(line, progress),
                        queued = (pending.get() - 1).coerceAtLeast(0)
                    )
                )
                if (rounded != lastPushed) {
                    lastPushed = rounded
                    notify(buildProgressNotification(url, progress, indeterminate = progress <= 0f))
                }
            }

            currentProcessId = null
            pending.decrementAndGet()

            result.fold(
                onSuccess = {
                    DownloadBus.emit(DownloadEvent.Finished(it.displayName, it.uri))
                    DownloadBus.invalidateLibrary()
                    notifyDone(it.displayName, UrlUtils.formatBytes(it.sizeBytes))
                },
                onFailure = {
                    val reason = friendlyError(it)
                    DownloadBus.emit(DownloadEvent.Failed(reason))
                    notifyFailed(reason)
                }
            )

            if (pending.get() <= 0) {
                DownloadBus.publish(DownloadState.Idle)
                stopSelfSafely()
            }
        }
    }

    private fun drain() {
        while (queue.tryReceive().isSuccess) { /* discard */ }
        pending.set(0)
        DownloadBus.publish(DownloadState.Idle)
    }

    // ------------------------------------------------------- notifications

    private fun startForegroundSafely(notification: Notification) {
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        }
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notify(notification: Notification) {
        runCatching { manager().notify(NOTIFICATION_ID, notification) }
    }

    private fun buildProgressNotification(
        url: String,
        progress: Float,
        indeterminate: Boolean
    ): Notification {
        val platform = UrlUtils.platformOf(url)
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return baseNotification(BlackholeApp.CHANNEL_PROGRESS)
            .setContentTitle("Pulling in ${platform.label} video")
            .setContentText(if (indeterminate) "Starting" else "${progress.toInt()}%")
            .setProgress(100, progress.toInt(), indeterminate)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun notifyDone(name: String, size: String) {
        val notification = baseNotification(BlackholeApp.CHANNEL_DONE)
            .setContentTitle("Saved to Movies/Blackhole")
            .setContentText("$name  ·  $size")
            .setStyle(NotificationCompat.BigTextStyle().bigText(name))
            .setAutoCancel(true)
            .build()
        runCatching { manager().notify(nextDoneId(), notification) }
    }

    private fun notifyFailed(reason: String) {
        val notification = baseNotification(BlackholeApp.CHANNEL_DONE)
            .setContentTitle("Download failed")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setAutoCancel(true)
            .build()
        runCatching { manager().notify(nextDoneId(), notification) }
    }

    private fun baseNotification(channel: String): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_stat_blackhole)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    }

    private fun manager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_DOWNLOAD = "com.blackhole.downloader.DOWNLOAD"
        const val ACTION_CANCEL = "com.blackhole.downloader.CANCEL"
        const val EXTRA_URL = "url"

        private const val NOTIFICATION_ID = 4201
        private val doneCounter = AtomicInteger(5000)
        private fun nextDoneId() = doneCounter.incrementAndGet()

        fun enqueue(context: Context, url: String) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_DOWNLOAD)
                .putExtra(EXTRA_URL, url)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL)
            )
        }

        /** Turns a raw yt-dlp output line into something worth showing a person. */
        fun summarise(line: String, progress: Float): String {
            val trimmed = line.trim()
            return when {
                trimmed.contains("[Merger]", true) -> "Merging video and audio"
                trimmed.contains("Extracting", true) -> "Reading the page"
                trimmed.contains("[info]", true) -> "Picking best quality"
                trimmed.contains("frag", true) -> "Downloading ${progress.toInt()}%"
                progress > 0f -> "Downloading ${progress.toInt()}%"
                else -> "Working"
            }
        }

        fun friendlyError(t: Throwable): String {
            val message = t.message.orEmpty()
            return when {
                message.contains("Private video", true) ||
                    message.contains("login", true) ||
                    message.contains("cookies", true) -> "That post is private or needs a login"

                message.contains("Unsupported URL", true) ||
                    message.contains("no video", true) ||
                    message.contains("Unable to extract", true) -> "No video found at that link"

                message.contains("Video unavailable", true) ||
                    message.contains("removed", true) -> "The video is unavailable or deleted"

                message.contains("timed out", true) ||
                    message.contains("Temporary failure", true) ||
                    message.contains("Network", true) -> "Network dropped out. Try again"

                message.contains("age", true) &&
                    message.contains("confirm", true) -> "Age-restricted. A login would be needed"

                message.contains("Movies/Blackhole", true) -> message

                else -> "Couldn't download that one. Try updating yt-dlp in settings"
            }
        }
    }
}
