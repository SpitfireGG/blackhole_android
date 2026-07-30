package com.blackhole.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.blackhole.downloader.core.Prefs
import com.blackhole.downloader.core.YtdlEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BlackholeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        createChannels()

        // Unpacking python + yt-dlp takes a second or two on first run, so get it
        // out of the way before the user has had time to tap anything.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            YtdlEngine.ensureInit(this@BlackholeApp)
            if (Prefs.autoUpdate) {
                YtdlEngine.update(this@BlackholeApp)
            }
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                "Downloads in progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "The ongoing notification shown while a video is downloading"
                setShowBadge(false)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                "Finished downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Tells you when a video is saved, or why one failed"
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OVERLAY,
                "Floating overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for the floating download bubble"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL_PROGRESS = "downloads_progress"
        const val CHANNEL_DONE = "downloads_done"
        const val CHANNEL_OVERLAY = "floating_overlay"
    }
}
