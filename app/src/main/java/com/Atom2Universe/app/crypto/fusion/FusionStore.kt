package com.Atom2Universe.app.crypto.fusion

import android.content.Context
import androidx.core.content.edit

class FusionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTries(recipe: FusionRecipe): Int = prefs.getInt("${recipe.id}_tries", 0)
    fun getWins(recipe: FusionRecipe): Int = prefs.getInt("${recipe.id}_wins", 0)
    fun getTotalWins(): Int = prefs.getInt(KEY_TOTAL_WINS, 0)

    fun recordAttempt(recipe: FusionRecipe, success: Boolean) {
        val oldTotalWins = getTotalWins()
        val toAps = oldTotalWins % 2 == 0
        prefs.edit {
            putInt("${recipe.id}_tries", getTries(recipe) + 1)
            if (success) {
                putInt("${recipe.id}_wins", getWins(recipe) + 1)
                putInt(KEY_TOTAL_WINS, oldTotalWins + 1)
                if (toAps) putFloat(KEY_BONUS_APS, (getBonusMultAps() + 0.01).toFloat())
                else putFloat(KEY_BONUS_APC, (getBonusMultApc() + 0.01).toFloat())
            }
        }
    }

    fun getBonusMultApc(): Double = prefs.getFloat(KEY_BONUS_APC, 0f).toDouble()
    fun getBonusMultAps(): Double = prefs.getFloat(KEY_BONUS_APS, 0f).toDouble()

    fun nextBonusIsAps(): Boolean = getTotalWins() % 2 == 0

    fun reset() = prefs.edit { clear() }

    companion object {
        private const val PREFS_NAME = "fusion_prefs"
        private const val KEY_TOTAL_WINS = "total_wins"
        private const val KEY_BONUS_APC = "bonus_mult_apc"
        private const val KEY_BONUS_APS = "bonus_mult_aps"
    }
}
