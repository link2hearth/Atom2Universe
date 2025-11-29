package com.example.atom2univers

import android.app.Activity
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.lang.ref.WeakReference

class AntiCheatBridge(activity: Activity) {

    private val activityRef = WeakReference(activity)

    @JavascriptInterface
    fun onCheatDetected() {
        val activity = activityRef.get() ?: return
        activity.runOnUiThread {
            Toast.makeText(activity, R.string.anti_cheat_detected_toast, Toast.LENGTH_LONG).show()
            Log.w(TAG, "Suspicious clicking pattern detected in WebView")
            // Uncomment the line below to close the activity immediately after detection.
            // activity.finish()
        }
    }

    private companion object {
        private const val TAG = "AntiCheatBridge"
    }
}
