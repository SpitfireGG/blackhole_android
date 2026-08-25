package com.blackhole.downloader.service

import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.blackhole.downloader.BlackholeApp
import com.blackhole.downloader.ClipboardBridgeActivity
import com.blackhole.downloader.MainActivity
import com.blackhole.downloader.R
import com.blackhole.downloader.core.DownloadBus
import com.blackhole.downloader.core.DownloadState
import com.blackhole.downloader.core.Prefs
import com.blackhole.downloader.core.UrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class FloatingOverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var overlayView: FrameLayout
    private var params: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // Whisper mode: hold the bubble and say "download".
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var holdToTalk = false
    private var listenTimer: Runnable? = null

    // Ghost mode: shake to reveal the bubble, drag it to a corner to download.
    private var lastShakeAt = 0L

    private val sensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
                    applyGhostAlpha()
                    refreshSensor()
                }
                return START_STICKY
            }
        }

        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            return START_STICKY
        }

        startForegroundSafely(buildNotification())
        showOverlay()
        observeQueue()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        cancelListenTimer()
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { sensorManager.unregisterListener(shakeListener) }
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_bubble, null) as FrameLayout

        val bubble = overlayView.findViewById<ImageView>(R.id.overlay_icon)
        bubble.setOnTouchListener(OverlayTouchListener())

        applyGhostAlpha()

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            flags,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(200)
        }

        wm.addView(overlayView, params)
        refreshSensor()
    }

    private fun hideOverlay() {
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            wm.removeView(overlayView)
        }
    }

    private fun observeQueue() {
        scope.launch {
            DownloadBus.state.collect { state ->
                val badge = overlayView.findViewById<TextView>(R.id.overlay_badge) ?: return@collect
                val count = when (state) {
                    is DownloadState.Idle -> 0
                    is DownloadState.Working -> state.queued + 1
                }
                if (count > 0) {
                    badge.text = count.toString()
                    badge.visibility = View.VISIBLE
                } else {
                    badge.text = ""
                    badge.visibility = View.GONE
                }
            }
        }
    }

    private fun currentClipboardUrl(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return null
        for (i in 0 until clip.itemCount) {
            val text = runCatching { clip.getItemAt(i).coerceToText(this) }.getOrNull() ?: continue
            UrlUtils.extractUrl(text)?.let {
                Prefs.lastClipboardUrl = it
                return it
            }
        }
        return null
    }

    private fun performTap() = downloadFromClipboard()

    /** Reads the clipboard (via the bridge when background reads are blocked) and queues it. */
    private fun downloadFromClipboard() {
        val url = currentClipboardUrl()
        if (url == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openClipboardBridge()
            return
        }
        if (url == null) {
            pulseAnimation()
            showFlash("No link on clipboard")
            return
        }
        if (Prefs.isAlreadyDownloaded(url)) {
            pulseAnimation()
            showFlash("Already saved")
            return
        }
        Prefs.lastHandledUrl = url
        if (DownloadService.enqueue(this, url)) {
            showFlash("Download queued")
        } else {
            showFlash("Open Blackhole to download")
        }
        pulseAnimation()
    }

    private fun openClipboardBridge() {
        val intent = Intent(this, ClipboardBridgeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }
    }

    // ------------------------------------------------------------- whisper

    private fun startListenTimer() {
        if (!Prefs.whisperMode) return
        cancelListenTimer()
        val timer = Runnable { startListening() }
        listenTimer = timer
        overlayView.postDelayed(timer, HOLD_TO_TALK_MS)
    }

    private fun cancelListenTimer() {
        listenTimer?.let { overlayView.removeCallbacks(it) }
        listenTimer = null
    }

    private fun startListening() {
        if (listening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            showFlash("Mic permission needed")
            return
        }
        listening = true
        holdToTalk = true
        showFlash("Listening…")
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this)
                recognizer?.setRecognitionListener(recognitionListener)
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            recognizer?.startListening(intent)
        } catch (t: Throwable) {
            listening = false
            showFlash("Mic busy")
        }
    }

    private fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            listening = false
        }
        override fun onError(error: Int) {
            listening = false
            if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_NO_MATCH
            ) {
                showFlash("Say \u201cdownload\u201d")
            }
        }
        override fun onPartialResults(partialResults: android.os.Bundle?) {}
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        override fun onResults(results: android.os.Bundle?) {
            listening = false
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
            val heard = texts.joinToString(" ").lowercase()
            if (TRIGGER_WORDS.any { heard.contains(it) }) {
                downloadFromClipboard()
            } else {
                showFlash("Say \u201cdownload\u201d")
            }
        }
    }

    // ---------------------------------------------------------------- ghost

    private fun applyGhostAlpha() {
        if (!::overlayView.isInitialized) return
        overlayView.animate().cancel()
        overlayView.alpha = if (Prefs.ghostMode) GHOST_ALPHA else 1f
    }

    private fun refreshSensor() {
        if (Prefs.ghostMode) {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sensor != null) {
                sensorManager.registerListener(
                    shakeListener, sensor, SensorManager.SENSOR_DELAY_UI
                )
            }
        } else {
            sensorManager.unregisterListener(shakeListener)
        }
    }

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!Prefs.ghostMode) return
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            if (sqrt(x * x + y * y + z * z) > SHAKE_THRESHOLD) {
                val now = System.currentTimeMillis()
                if (now - lastShakeAt > SHAKE_COOLDOWN_MS) {
                    lastShakeAt = now
                    revealBubble()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun revealBubble() {
        overlayView.animate().cancel()
        overlayView.alpha = 1f
        overlayView.removeCallbacks(hideRunnable)
        overlayView.postDelayed(hideRunnable, GHOST_VISIBLE_MS)
    }

    private val hideRunnable = Runnable {
        if (Prefs.ghostMode) {
            overlayView.animate().alpha(GHOST_ALPHA).setDuration(400).start()
        }
    }

    private fun isInCorner(x: Int, y: Int): Boolean {
        val dm = resources.displayMetrics
        val margin = dp(28)
        val vw = overlayView.width
        val vh = overlayView.height
        val nearLeft = x <= margin
        val nearRight = x + vw >= dm.widthPixels - margin
        val nearTop = y <= margin
        val nearBottom = y + vh >= dm.heightPixels - margin
        return (nearLeft || nearRight) && (nearTop || nearBottom)
    }

    private fun cornerDownload() {
        pulseAnimation()
        downloadFromClipboard()
    }

    private fun pulseAnimation() {
        val bubble = overlayView.findViewById<ImageView>(R.id.overlay_icon)
        val pulse = ValueAnimator.ofFloat(1f, 1.35f, 1f).apply {
            duration = 350
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                bubble.scaleX = v
                bubble.scaleY = v
            }
        }
        pulse.start()
    }

    private fun showFlash(text: String) {
        val flash = overlayView.findViewById<TextView>(R.id.overlay_flash) ?: return
        flash.post {
            flash.text = text
            flash.alpha = 1f
            flash.animate().alpha(0f).setDuration(1800).start()
        }
    }

    private fun startForegroundSafely(notification: Notification) {
        runCatching {
            startForeground(NOTIFICATION_ID, notification)
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
        private var downX = 0f
        private var downY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val p = params ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downX = event.x
                    downY = event.y
                    dragging = false
                    holdToTalk = false
                    startListenTimer()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (dx * dx + dy * dy > 100) {
                        dragging = true
                        cancelListenTimer()
                        if (listening) {
                            listening = false
                            stopListening()
                        }
                        p.x = (event.rawX - downX).toInt()
                        p.y = (event.rawY - downY).toInt()
                        wm.updateViewLayout(overlayView, p)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val wasHoldToTalk = listening || holdToTalk
                    if (listening) {
                        listening = false
                        stopListening()
                    }
                    holdToTalk = false
                    cancelListenTimer()
                    if (dragging) {
                        if (Prefs.ghostMode && isInCorner(p.x, p.y)) {
                            cornerDownload()
                        }
                    } else if (!wasHoldToTalk) {
                        performTap()
                    }
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_OUTSIDE -> {
                    dragging = false
                    cancelListenTimer()
                    return false
                }
            }
            return false
        }
    }

    companion object {
        const val ACTION_HIDE = "com.blackhole.downloader.HIDE_OVERLAY"
        const val ACTION_REFRESH = "com.blackhole.downloader.REFRESH_OVERLAY"
        private const val NOTIFICATION_ID = 4202

        private const val HOLD_TO_TALK_MS = 500L
        private const val GHOST_ALPHA = 0.06f
        private const val SHAKE_THRESHOLD = 18f
        private const val SHAKE_COOLDOWN_MS = 1500L
        private const val GHOST_VISIBLE_MS = 3500L
        private val TRIGGER_WORDS = listOf("download", "grab", "save", "fetch", "capture", "pull")

        fun start(context: Context) {
            context.startService(Intent(context, FloatingOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FloatingOverlayService::class.java).setAction(ACTION_HIDE)
            )
        }

        fun refresh(context: Context) {
            context.startService(
                Intent(context, FloatingOverlayService::class.java).setAction(ACTION_REFRESH)
            )
        }
    }
}
