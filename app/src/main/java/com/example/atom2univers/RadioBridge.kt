package com.example.atom2univers

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject

class RadioBridge(
    private val activity: MainActivity,
    private val webView: WebView,
    private val player: ExoPlayer,
    private val scope: CoroutineScope
) {

    private val recorder = Recorder(activity.applicationContext, scope)
    private var currentStreamUrl: String? = null
    private var latestMetadata: TrackMetadata? = null
    private var currentStationName: String? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                notifyPlaybackError(error.errorCodeName ?: "playback_error")
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                notifyMetadata(mediaMetadata)
            }
        })
    }

    @JavascriptInterface
    fun playStream(url: String?, name: String?) {
        val streamUrl = url?.takeIf { it.isNotBlank() }
        if (streamUrl == null) {
            notifyPlaybackError("invalid_url")
            return
        }

        currentStreamUrl = streamUrl
        currentStationName = name?.takeIf { it.isNotBlank() }
        latestMetadata = TrackMetadata(null, null, currentStationName)
        activity.runOnUiThread {
            try {
                val mediaItem = MediaItem.fromUri(streamUrl)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
                player.play()
                notifyPlaybackState("playing")
            } catch (error: Exception) {
                notifyPlaybackError(error.message ?: "playback_failed")
            }
        }
    }

    @JavascriptInterface
    fun pauseStream() {
        activity.runOnUiThread {
            try {
                player.pause()
                notifyPlaybackState("paused")
            } catch (error: Exception) {
                notifyPlaybackError(error.message ?: "pause_failed")
            }
        }
    }

    @JavascriptInterface
    fun stopStream() {
        activity.runOnUiThread {
            try {
                player.stop()
                player.clearMediaItems()
                notifyPlaybackState("stopped")
            } catch (error: Exception) {
                notifyPlaybackError(error.message ?: "stop_failed")
            }
        }
    }

    @JavascriptInterface
    fun startRecording() {
        val streamUrl = currentStreamUrl ?: return
        val started = recorder.startRecording(streamUrl, latestMetadata) {
            notifyRecordingState(false)
        }
        if (started) {
            notifyRecordingState(true)
        }
    }

    @JavascriptInterface
    fun stopRecording() {
        recorder.stopRecording()
        notifyRecordingState(false)
    }

    fun release() {
        recorder.release()
    }

    private fun notifyRecordingState(isRecording: Boolean) {
        val script = "window.onRadioRecordingChanged && window.onRadioRecordingChanged(${if (isRecording) "true" else "false"});"
        postToWebView(script)
    }

    private fun notifyPlaybackState(state: String) {
        val quotedState = JSONObject.quote(state)
        val script = "window.onAndroidRadioStateChanged && window.onAndroidRadioStateChanged($quotedState);"
        postToWebView(script)
    }

    private fun notifyPlaybackError(reason: String) {
        Log.w(TAG, "Radio playback error: $reason")
        val script = "window.onAndroidRadioStateChanged && window.onAndroidRadioStateChanged('error');"
        postToWebView(script)
    }

    private fun notifyMetadata(mediaMetadata: MediaMetadata?) {
        val artist = mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: mediaMetadata?.albumArtist?.toString()?.takeIf { it.isNotBlank() }
        val title = mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
        val station = currentStationName

        latestMetadata = TrackMetadata(artist, title, station)

        val json = JSONObject()
        artist?.let { json.put("artist", it) }
        title?.let { json.put("title", it) }

        val script = if (json.length() > 0) {
            "window.onAndroidRadioMetadataChanged && window.onAndroidRadioMetadataChanged($json);"
        } else {
            "window.onAndroidRadioMetadataChanged && window.onAndroidRadioMetadataChanged(null);"
        }

        recorder.updateMetadata(latestMetadata)
        postToWebView(script)
    }

    private fun postToWebView(script: String) {
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    companion object {
        private const val TAG = "RadioBridge"
    }
}
