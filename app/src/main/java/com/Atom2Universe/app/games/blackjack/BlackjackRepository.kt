package com.Atom2Universe.app.games.blackjack

import android.content.Context
import androidx.core.content.edit

class BlackjackRepository(context: Context) {

    private val prefs = context.getSharedPreferences("blackjack_prefs", Context.MODE_PRIVATE)

    var balance: Int
        get() = prefs.getInt(KEY_BALANCE, DEFAULT_BALANCE)
        set(v) { prefs.edit { putInt(KEY_BALANCE, v.coerceAtLeast(0)) } }

    /** Le nombre d'adversaires IA, retenu d'une partie à l'autre : la table se rouvre telle qu'on l'a laissée. */
    var aiCount: Int
        get() = prefs.getInt(KEY_AI_COUNT, 0).coerceIn(0, 4)
        set(v) { prefs.edit { putInt(KEY_AI_COUNT, v.coerceIn(0, 4)) } }

    companion object {
        private const val KEY_AI_COUNT = "ai_count"
        private const val KEY_BALANCE = "balance"
        const val DEFAULT_BALANCE = 20
    }
}
