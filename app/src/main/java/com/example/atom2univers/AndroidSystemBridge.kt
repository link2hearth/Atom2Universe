package com.example.atom2univers

import android.app.Activity
import android.view.WindowManager
import android.webkit.JavascriptInterface
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
        return keepAwake
    }

    @JavascriptInterface
    fun setStatusBarVisible(visible: Boolean): Boolean {
        val activity = activityRef.get() ?: return false
        val shouldShow = visible
        activity.runOnUiThread {
            val window = activity.window ?: return@runOnUiThread
            val controller = WindowCompat.getInsetsController(window, window.decorView)
                ?: return@runOnUiThread

            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            if (shouldShow) {
                controller.show(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }

            controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
        return shouldShow
    }
}
