package com.blackhole.downloader

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.blackhole.downloader.core.Prefs
import com.blackhole.downloader.core.UrlUtils
import com.blackhole.downloader.service.DownloadService

/**
 * Android 10+ blocks clipboard reads from background services, so the floating
 * overlay bubble can't see the clipboard on its own. This fully transparent
 * activity briefly takes focus (which is what grants clipboard access), reads
 * the link, starts the download, and finishes. Rendered invisible and kept out
 * of Recents so the user never sees it.
 */
class ClipboardBridgeActivity : Activity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Pre-Q the overlay service can read the clipboard directly; nothing to bridge.
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled) {
            handled = true
            // Give the window manager a beat to fully grant focus before reading.
            window.decorView.postDelayed({ readClipboardAndFinish() }, 80)
        }
    }

    private fun readClipboardAndFinish() {
        val url = readClipboardUrl()
        when {
            url == null -> toast("empty clipboard detected, please copy something and try again")
            Prefs.isAlreadyDownloaded(url) -> {
                Prefs.lastHandledUrl = url
                toast("Already saved")
            }
            else -> {
                Prefs.lastHandledUrl = url
                if (DownloadService.enqueue(this, url)) {
                    toast("Download queued")
                } else {
                    toast("Couldn't start the download — open Blackhole and try again")
                }
            }
        }
        finish()
    }

    private fun readClipboardUrl(): String? {
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

    private fun toast(message: String) {
        runCatching { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }
}
