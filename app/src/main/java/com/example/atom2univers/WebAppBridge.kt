package com.example.atom2univers

import android.webkit.JavascriptInterface
import java.lang.ref.WeakReference

class WebAppBridge(activity: MainActivity) {

    private val activityRef = WeakReference(activity)

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
}
