package com.Atom2Universe.app.crypto.clicker

import android.content.Context

class NeutrinoRepository(context: Context) {

    private val prefs = context.getSharedPreferences("neutrino_prefs", Context.MODE_PRIVATE)

    fun getBalance(): Int = prefs.getInt("balance", 0)

    fun addBalance(count: Int) {
        val newBalance = getBalance() + count
        val lifetime = prefs.getInt("lifetime_neutrinos", 0)
        prefs.edit()
            .putInt("balance", newBalance.coerceAtLeast(0))
            .putInt("lifetime_neutrinos", lifetime + count)
            .apply()
    }

    fun subtractBalance(count: Int): Boolean {
        val current = getBalance()
        if (current < count) return false
        setBalance(current - count)
        return true
    }

    fun setBalance(n: Int) {
        prefs.edit().putInt("balance", n.coerceAtLeast(0)).apply()
    }

    fun getLifetimeNeutrinos(): Int = prefs.getInt("lifetime_neutrinos", 0)

    fun reset() {
        prefs.edit().clear().apply()
    }

    fun recordColorStackHardWin(): Boolean {
        val wins = prefs.getInt("colorstack_hard_wins", 0) + 1
        prefs.edit().putInt("colorstack_hard_wins", wins).apply()
        return wins % 2 == 0
    }

    fun recordPipeTapHardWin(): Boolean {
        val wins = prefs.getInt("pipetap_hard_wins", 0) + 1
        prefs.edit().putInt("pipetap_hard_wins", wins).apply()
        return wins % 2 == 0
    }
}
