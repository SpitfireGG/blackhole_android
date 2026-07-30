package com.blackhole.downloader.core

import android.content.Context
import android.content.SharedPreferences

/** Small SharedPreferences wrapper. No analytics, no network, no ad SDK. */
object Prefs {

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("blackhole", Context.MODE_PRIVATE)
    }

    /** Keep yt-dlp itself up to date. YouTube breaks extractors roughly monthly. */
    var autoUpdate: Boolean
        get() = sp.getBoolean(KEY_AUTO_UPDATE, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_UPDATE, value).apply()

    /** Nightly yt-dlp fixes YouTube faster than stable does. */
    var nightlyChannel: Boolean
        get() = sp.getBoolean(KEY_NIGHTLY, true)
        set(value) = sp.edit().putBoolean(KEY_NIGHTLY, value).apply()

    /** Filenames like `A7KD91MZQ2.mp4` instead of the video title. */
    var shortFilenames: Boolean
        get() = sp.getBoolean(KEY_SHORT_NAMES, false)
        set(value) = sp.edit().putBoolean(KEY_SHORT_NAMES, value).apply()

    /** Ceiling on vertical resolution. 0 means "best available". */
    var maxHeight: Int
        get() = sp.getInt(KEY_MAX_HEIGHT, 1080)
        set(value) = sp.edit().putInt(KEY_MAX_HEIGHT, value).apply()

    /** Fire the download the moment the app opens with a link already on the clipboard. */
    var autoStartOnOpen: Boolean
        get() = sp.getBoolean(KEY_AUTO_START, false)
        set(value) = sp.edit().putBoolean(KEY_AUTO_START, value).apply()

    /** aria2c is faster on flaky connections but occasionally chokes on HLS. */
    var useAria2c: Boolean
        get() = sp.getBoolean(KEY_ARIA2C, false)
        set(value) = sp.edit().putBoolean(KEY_ARIA2C, value).apply()

    /** Remembers the last link we downloaded so re-opening the app doesn't loop. */
    var lastHandledUrl: String?
        get() = sp.getString(KEY_LAST_URL, null)
        set(value) = sp.edit().putString(KEY_LAST_URL, value).apply()

    /** Floating overlay bubble for one-tap downloads from any app. */
    var overlayEnabled: Boolean
        get() = sp.getBoolean(KEY_OVERLAY, false)
        set(value) = sp.edit().putBoolean(KEY_OVERLAY, value).apply()

    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val KEY_NIGHTLY = "nightly_channel"
    private const val KEY_SHORT_NAMES = "short_filenames"
    private const val KEY_MAX_HEIGHT = "max_height"
    private const val KEY_AUTO_START = "auto_start_on_open"
    private const val KEY_ARIA2C = "use_aria2c"
    private const val KEY_LAST_URL = "last_handled_url"
    private const val KEY_OVERLAY = "floating_overlay"
}
