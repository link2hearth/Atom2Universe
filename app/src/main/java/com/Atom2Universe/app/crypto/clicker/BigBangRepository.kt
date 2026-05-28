package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import androidx.core.content.edit

class BigBangRepository(context: Context) {

    companion object {
        const val UNLOCK_THRESHOLD = 178
    }

    private val prefs = context.getSharedPreferences("big_bang_prefs", Context.MODE_PRIVATE)

    fun isUnlocked(): Boolean = prefs.getBoolean("unlocked", false)
    fun markUnlocked() = prefs.edit { putBoolean("unlocked", true) }

    fun getLevel(bonus: BigBangBonus): Int = prefs.getInt(bonus.id, 0)

    fun addLevels(bonus: BigBangBonus, count: Int) =
        prefs.edit { putInt(bonus.id, getLevel(bonus) + count) }

    fun resetUnlock() = prefs.edit { putBoolean("unlocked", false) }

    fun getBigBangCount(): Int = prefs.getInt("big_bang_count", 0)

    fun incrementBigBangCount() =
        prefs.edit { putInt("big_bang_count", getBigBangCount() + 1) }
}
