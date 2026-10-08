package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import androidx.core.content.edit

class ElementTokenRepository(context: Context) {

    private val prefs = context.getSharedPreferences("element_token_prefs", Context.MODE_PRIVATE)

    fun getBalance(): Int = prefs.getInt("balance", 0)

    fun addTokens(count: Int) {
        prefs.edit { putInt("balance", (getBalance() + count).coerceAtLeast(0)) }
    }

    fun consumeTokens(count: Int): Boolean {
        val current = getBalance()
        if (current < count) return false
        prefs.edit { putInt("balance", current - count) }
        return true
    }

    fun setBalance(n: Int) {
        prefs.edit { putInt("balance", n.coerceAtLeast(0)) }
    }

    fun reset() {
        prefs.edit { clear() }
    }
}
