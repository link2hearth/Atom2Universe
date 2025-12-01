package com.example.atom2univers

import android.webkit.JavascriptInterface
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.lang.ref.WeakReference

class WebAppBridge(activity: MainActivity) {

    private val activityRef = WeakReference(activity)
    private val defaultNewsFeedUrl = "https://news.google.com/rss?hl=fr&gl=FR&ceid=FR:fr"

    @JavascriptInterface
    fun saveBackup() {
        // Get the activity from the WeakReference
        val activity = activityRef.get()
        // Run the code on the UI thread, using the 'activity' variable
        activity?.runOnUiThread {
            activity.startCreateBackup()
        }
    }

    @JavascriptInterface
    fun loadBackup() {
        // Get the activity from the WeakReference
        val activity = activityRef.get()
        // Run the code on the UI thread, using the 'activity' variable
        activity?.runOnUiThread {
            activity.startOpenBackup()
        }
    }

    @JavascriptInterface
    fun sendBackupData(base64Data: String?) {
        activityRef.get()?.writeBackupFromJs(base64Data)
    }

    @JavascriptInterface
    fun loadNews(feedUrl: String?) {
        val activity = activityRef.get() ?: return
        val targetUrl = feedUrl?.takeIf { it.isNotBlank() } ?: defaultNewsFeedUrl
        Thread {
            val xml = fetchRss(targetUrl)
            val payload = JSONObject.quote(xml)
            val script = "window.onNewsLoaded && window.onNewsLoaded($payload);"
            activity.postJavascript(script)
        }.start()
    }

    private fun fetchRss(url: String): String {
        var connection: HttpURLConnection? = null
        var reader: BufferedReader? = null
        return try {
            val endpoint = URL(url)
            connection = endpoint.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            val stream = connection.inputStream
            reader = BufferedReader(InputStreamReader(stream))
            val builder = StringBuilder()
            var line: String?
            while (reader?.readLine().also { line = it } != null) {
                builder.append(line).append('\n')
            }
            builder.toString()
        } catch (error: Exception) {
            ""
        } finally {
            try {
                reader?.close()
            } catch (_: Exception) {
            }
            connection?.disconnect()
        }
    }
}
