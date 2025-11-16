package com.example.atom2univers

import android.app.Activity
import android.view.WindowManager
import android.webkit.JavascriptInterface
import java.lang.ref.WeakReference

class AndroidSystemBridge(activity: Activity, webView: GameWebView) {

    private val activityRef = WeakReference(activity)
    private val webViewRef = WeakReference(webView)

    @JavascriptInterface
    fun setScreenAwake(enabled: Boolean): Boolean {
        val activity = activityRef.get() ?: return false
        val keepAwake = enabled
        activity.runOnUiThread {
            val window = activity.window
            if (window != null) {
                if (keepAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            webViewRef.get()?.keepScreenOn = keepAwake
        }
        return true
    }
}
