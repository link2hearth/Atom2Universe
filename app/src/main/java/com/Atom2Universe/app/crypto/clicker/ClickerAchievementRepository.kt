package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import androidx.core.content.edit

class ClickerAchievementRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadUnlocked(): Set<String> =
        prefs.getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet()

    fun saveUnlocked(ids: Set<String>) {
        prefs.edit { putStringSet(KEY_UNLOCKED, ids) }
    }

    companion object {
        private const val PREFS_NAME = "clicker_achievements"
        private const val KEY_UNLOCKED = "unlocked_ids"
    }
}
