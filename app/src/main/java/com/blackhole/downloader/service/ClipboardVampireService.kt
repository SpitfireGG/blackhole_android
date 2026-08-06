package com.blackhole.downloader.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.blackhole.downloader.core.Prefs
import com.blackhole.downloader.core.UrlUtils

/**
 * The only way to read the clipboard from the background on Android 10+ is to be
 * an enabled accessibility service, so that's what full vampire mode rides on.
 *
 * When the user copies something anywhere on the phone, the window changes and we
 * get an event; we then peek at the clipboard and, if it holds a platform link we
 * haven't seen yet, enqueue a download. Debounced and de-duplicated so one copy
 * can never double-fire.
 *
 * The user must switch this service on under Settings → Accessibility; an app
 * cannot enable an accessibility service for itself.
 */
class ClipboardVampireService : AccessibilityService() {

    private var lastEventAt = 0L
    private var lastSeenUrl: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!Prefs.vampireMode) return

        val now = System.currentTimeMillis()
        if (now - lastEventAt < DEBOUNCE_MS) return
        lastEventAt = now

        val url = readClipboardUrl() ?: return
        if (url == lastSeenUrl) return
        lastSeenUrl = url

        if (url == Prefs.lastHandledUrl) return
        if (Prefs.isAlreadyDownloaded(url)) return

        Prefs.lastHandledUrl = url
        DownloadService.enqueue(this, url)
    }

    override fun onInterrupt() {}

    private fun readClipboardUrl(): String? {
        val clip = runCatching { clipboard }.getOrNull() ?: return null
        for (i in 0 until clip.itemCount) {
            val text = runCatching { clip.getItemAt(i).coerceToText(this) }.getOrNull() ?: continue
            UrlUtils.extractUrl(text)?.let { return it }
        }
        return null
    }

    companion object {
        private const val DEBOUNCE_MS = 1500L

        /** True when the user has switched Blackhole on under Accessibility. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val self = ComponentName(context, ClipboardVampireService::class.java).flattenToString()
            return enabled.split(':').any { it.equals(self, ignoreCase = true) }
        }
    }
}
