package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import androidx.core.content.edit

class ClickerOfflineRepository(context: Context) {
    private val prefs = context.getSharedPreferences("clicker_offline", Context.MODE_PRIVATE)

    fun load(): Long = prefs.getLong("last_online_ms", 0L)
    fun save(timestampMs: Long) = prefs.edit { putLong("last_online_ms", timestampMs) }
}
