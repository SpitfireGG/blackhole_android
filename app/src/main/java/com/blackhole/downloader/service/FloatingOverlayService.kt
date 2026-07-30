package com.blackhole.downloader.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.blackhole.downloader.BlackholeApp
import com.blackhole.downloader.MainActivity
import com.blackhole.downloader.R
import com.blackhole.downloader.core.Prefs
import com.blackhole.downloader.core.UrlUtils

class FloatingOverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var overlayView: FrameLayout
    private var params: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            return START_STICKY
        }

        startForegroundSafely(buildNotification())
        showOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_bubble, null) as FrameLayout
        overlayView.setOnTouchListener(OverlayTouchListener())

        val bubble = overlayView.findViewById<ImageView>(R.id.overlay_icon)
        bubble.setOnClickListener {
            val url = currentClipboardUrl()
            if (url != null) {
                Prefs.lastHandledUrl = url
                DownloadService.enqueue(this, url)
                showFlash("Downloading")
            } else {
                showFlash("No link on clipboard")
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            dp(64),
            dp(64),
            flags,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(200)
        }

        wm.addView(overlayView, params)
    }

    private fun hideOverlay() {
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            wm.removeView(overlayView)
        }
    }

    private fun currentClipboardUrl(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        for (i in 0 until clip.itemCount) {
            val text = clip.getItemAt(i).coerceToText(this)
            UrlUtils.extractUrl(text)?.let { return it }
        }
        return null
    }

    private fun showFlash(text: String) {
        val flash = overlayView.findViewById<View>(R.id.overlay_flash) ?: return
        flash.post {
            flash.alpha = 1f
            flash.animate().alpha(0f).duration = 1500
        }
    }

    private fun startForegroundSafely(notification: Notification) {
        runCatching {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, FloatingOverlayService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, BlackholeApp.CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_stat_blackhole)
            .setContentTitle("Blackhole overlay")
            .setContentText("Tap the bubble to download from clipboard")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Hide", stop)
            .build()
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }

    private inner class OverlayTouchListener : View.OnTouchListener {
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val p = params ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (dx * dx + dy * dy > 20) {
                        dragging = true
                        p.x = initialX + dx
                        p.y = initialY + dy
                        wm.updateViewLayout(overlayView, p)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        overlayView.findViewById<ImageView>(R.id.overlay_icon)?.performClick()
                    }
                    dragging = false
                    return true
                }
            }
            return false
        }
    }

    companion object {
        const val ACTION_HIDE = "com.blackhole.downloader.HIDE_OVERLAY"
        private const val NOTIFICATION_ID = 4202

        fun start(context: Context) {
            context.startService(Intent(context, FloatingOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FloatingOverlayService::class.java).setAction(ACTION_HIDE)
            )
        }
    }
}
