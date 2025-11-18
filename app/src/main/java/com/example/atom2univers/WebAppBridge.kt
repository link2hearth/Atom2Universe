package com.example.atom2univers

import android.webkit.JavascriptInterface
import java.lang.ref.WeakReference

class WebAppBridge(activity: MainActivity) {

    private val activityRef = WeakReference(activity)

    @JavascriptInterface
    fun saveBackup() {
        activityRef.get()?.runOnUiThread {
            it.startCreateBackup()
        }
    }

    @JavascriptInterface
    fun loadBackup() {
        activityRef.get()?.runOnUiThread {
            it.startOpenBackup()
        }
    }

    @JavascriptInterface
    fun sendBackupData(base64Data: String?) {
        activityRef.get()?.writeBackupFromJs(base64Data)
    }
}
