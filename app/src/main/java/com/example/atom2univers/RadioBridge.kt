package com.example.atom2univers

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.media3.common.MediaItem
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
    private var currentStreamName: String? = null

    @JavascriptInterface
    fun playStream(url: String, name: String) {
        currentStreamUrl = url
        currentStreamName = name
        activity.runOnUiThread {
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            player.play()
            notifyPlaybackState("playing")
        }
    }

    @JavascriptInterface
    fun pauseStream() {
        activity.runOnUiThread {
            player.pause()
            notifyPlaybackState("paused")
        }
    }

    @JavascriptInterface
    fun stopStream() {
        activity.runOnUiThread {
            player.stop()
            player.clearMediaItems()
            notifyPlaybackState("stopped")
        }
    }

    @JavascriptInterface
    fun startRecording() {
        val streamUrl = currentStreamUrl ?: return
        val baseName = currentStreamName?.takeIf { it.isNotBlank() } ?: "radio_record"
        val started = recorder.startRecording(streamUrl, baseName) {
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

    private fun postToWebView(script: String) {
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

}
