package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import androidx.core.content.edit

class CritRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCritChanceLevel(): Int = prefs.getInt(KEY_CHANCE_LEVEL, 0)
    fun getCritDamageLevel(): Int = prefs.getInt(KEY_DAMAGE_LEVEL, 0)

    fun incrementCritChance() = prefs.edit { putInt(KEY_CHANCE_LEVEL, getCritChanceLevel() + 1) }
    fun incrementCritDamage() = prefs.edit { putInt(KEY_DAMAGE_LEVEL, getCritDamageLevel() + 1) }

    fun reset() = prefs.edit { clear() }

    companion object {
        private const val PREFS_NAME      = "clicker_crit_prefs"
        private const val KEY_CHANCE_LEVEL = "crit_chance_level"
        private const val KEY_DAMAGE_LEVEL = "crit_damage_level"
    }
}
