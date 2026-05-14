package com.Atom2Universe.app.util

import android.annotation.SuppressLint
import android.util.Log

object LogcatNoiseReducer {
    private const val TAG = "LogcatNoiseReducer"

    @SuppressLint("PrivateApi")
    fun reducePopupMenuLogs() {
        setLogTagLevel("ViewRootImpl", "W")
        setLogTagLevel("BLASTBufferQueue", "W")
    }

    @SuppressLint("PrivateApi")
    private fun setLogTagLevel(tag: String, level: String) {
        try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val setMethod = systemProperties.getDeclaredMethod(
                "set",
                String::class.java,
                String::class.java
            )
            setMethod.invoke(null, "log.tag.$tag", level)
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to update log tag $tag", exception)
        }
    }
}
