package com.blackhole.downloader.core

import java.net.URI

/** Platforms Blackhole recognises by name. Anything else still goes through yt-dlp. */
enum class Platform(val label: String) {
    YOUTUBE("YouTube"),
    TWITTER("X"),
    PINTEREST("Pinterest"),
    TIKTOK("TikTok"),
    INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"),
    REDDIT("Reddit"),
    OTHER("Link")
}

object UrlUtils {

    private val URL_PATTERN = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /**
     * Pulls the first http(s) link out of arbitrary text. Share sheets on TikTok and
     * Instagram wrap the link in a caption, so we can't assume the clipboard is a bare URL.
     */
    fun extractUrl(text: CharSequence?): String? {
        if (text.isNullOrBlank()) return null
        val raw = URL_PATTERN.find(text)?.value ?: return null
        return raw.trimEnd('.', ',', ';', ')', ']', '}', '"', '\'')
            .takeIf { it.length > 10 }
    }

    fun platformOf(url: String): Platform {
        val host = hostOf(url)
        return when {
            host.contains("youtube") || host.contains("youtu.be") -> Platform.YOUTUBE
            host.contains("tiktok") -> Platform.TIKTOK
            host.contains("pinterest") || host.contains("pin.it") -> Platform.PINTEREST
            host == "x.com" || host.endsWith(".x.com") || host.contains("twitter") -> Platform.TWITTER
            host.contains("instagram") -> Platform.INSTAGRAM
            host.contains("facebook") || host.contains("fb.watch") -> Platform.FACEBOOK
            host.contains("reddit") || host.contains("redd.it") -> Platform.REDDIT
            else -> Platform.OTHER
        }
    }

    private fun hostOf(url: String): String = runCatching {
        URI(url).host?.lowercase()?.removePrefix("www.").orEmpty()
    }.getOrDefault("")

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0 || value >= 100) {
            "${value.toInt()} ${units[unit]}"
        } else {
            String.format("%.1f %s", value, units[unit])
        }
    }

    fun formatDuration(millis: Long): String {
        if (millis <= 0) return ""
        val totalSeconds = millis / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }
}
