package com.blackhole.downloader.core

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DownloadState {
    data object Idle : DownloadState

    data class Working(
        val url: String,
        val platform: Platform,
        val progress: Float,
        val status: String,
        val queued: Int
    ) : DownloadState
}

sealed interface DownloadEvent {
    data class Finished(val name: String, val uri: Uri) : DownloadEvent
    data class Failed(val reason: String) : DownloadEvent
    data class Info(val message: String) : DownloadEvent
}

/**
 * Single source of truth shared between the foreground service and the UI.
 * Deliberately process-global: the service outlives the Activity.
 */
object DownloadBus {

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    /** Bumped every time the library changes so the videos list can refresh. */
    private val _libraryVersion = MutableStateFlow(0)
    val libraryVersion = _libraryVersion.asStateFlow()

    fun publish(state: DownloadState) {
        _state.value = state
    }

    fun emit(event: DownloadEvent) {
        _events.tryEmit(event)
    }

    fun invalidateLibrary() {
        _libraryVersion.value = _libraryVersion.value + 1
    }
}
