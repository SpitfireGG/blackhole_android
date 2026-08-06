package com.blackhole.downloader.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackhole.downloader.core.DownloadBus
import com.blackhole.downloader.core.DownloadEvent
import com.blackhole.downloader.core.MediaLibrary
import com.blackhole.downloader.core.Platform
import com.blackhole.downloader.core.Prefs
import com.blackhole.downloader.core.UrlUtils
import com.blackhole.downloader.core.VideoFile
import com.blackhole.downloader.core.YtdlEngine
import com.blackhole.downloader.service.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, VIDEOS, SETTINGS }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen = _screen.asStateFlow()

    private val _videos = MutableStateFlow<List<VideoFile>>(emptyList())
    val videos = _videos.asStateFlow()

    private val _loadingLibrary = MutableStateFlow(false)
    val loadingLibrary = _loadingLibrary.asStateFlow()

    /** Detected from the clipboard, shown under the void as a hint before you tap. */
    private val _clipboardHint = MutableStateFlow<Pair<String, Platform>?>(null)
    val clipboardHint = _clipboardHint.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts = _toasts.asSharedFlow()

    private val _ytdlpVersion = MutableStateFlow("checking…")
    val ytdlpVersion = _ytdlpVersion.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating = _updating.asStateFlow()

    init {
        viewModelScope.launch {
            DownloadBus.libraryVersion.drop(1).collect { refreshLibrary() }
        }
        viewModelScope.launch {
            DownloadBus.events.collect { event ->
                when (event) {
                    is DownloadEvent.Finished -> _toasts.tryEmit("Saved ${event.name}")
                    is DownloadEvent.Failed -> _toasts.tryEmit(event.reason)
                    is DownloadEvent.Info -> _toasts.tryEmit(event.message)
                }
            }
        }
        viewModelScope.launch {
            val version = withContext(Dispatchers.IO) { YtdlEngine.version(getApplication()) }
            _ytdlpVersion.value = version
        }
        refreshLibrary()
    }

    fun go(screen: Screen) {
        _screen.value = screen
        if (screen == Screen.VIDEOS) refreshLibrary()
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _loadingLibrary.value = true
            val list = withContext(Dispatchers.IO) { MediaLibrary.list(getApplication()) }
            _videos.value = list
            _loadingLibrary.value = false
        }
    }

    fun delete(video: VideoFile) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { MediaLibrary.delete(getApplication(), video) }
            if (ok) {
                _videos.value = _videos.value.filterNot { it.id == video.id }
                _toasts.tryEmit("Deleted ${video.name}")
            } else {
                _toasts.tryEmit("Couldn't delete that file")
            }
        }
    }

    /** Reads the clipboard once, without starting anything. */
    fun peekClipboard(): String? {
        val url = currentClipboardUrl()
        _clipboardHint.value = url?.let { it to UrlUtils.platformOf(it) }
        return url
    }

    /** The whole point of the app: one tap, clipboard in, video out. */
    fun swallowClipboard(): Boolean {
        val url = currentClipboardUrl()
        if (url == null) {
            _toasts.tryEmit("No link on the clipboard. Copy one and tap again")
            return false
        }
        start(url)
        return true
    }

    fun start(url: String) {
        if (Prefs.isAlreadyDownloaded(url)) {
            _toasts.tryEmit("Already saved")
            return
        }
        Prefs.lastHandledUrl = url
        _clipboardHint.value = url to UrlUtils.platformOf(url)
        DownloadService.enqueue(getApplication(), url)
    }

    fun cancel() {
        DownloadService.cancel(getApplication())
    }

    fun updateYtdlp() {
        if (_updating.value) return
        viewModelScope.launch {
            _updating.value = true
            val result = withContext(Dispatchers.IO) { YtdlEngine.update(getApplication()) }
            _ytdlpVersion.value = withContext(Dispatchers.IO) { YtdlEngine.version(getApplication()) }
            _updating.value = false
            _toasts.tryEmit(
                when (result) {
                    "ALREADY_UP_TO_DATE" -> "yt-dlp is already current"
                    "DONE" -> "yt-dlp updated"
                    else -> "yt-dlp: $result"
                }
            )
        }
    }

    private fun currentClipboardUrl(): String? {
        val context: Context = getApplication()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null
        val clip = clipboard.primaryClip ?: return null
        for (i in 0 until clip.itemCount) {
            val text = clip.getItemAt(i).coerceToText(context)
            UrlUtils.extractUrl(text)?.let {
                Prefs.lastClipboardUrl = it
                return it
            }
        }
        return null
    }
}
