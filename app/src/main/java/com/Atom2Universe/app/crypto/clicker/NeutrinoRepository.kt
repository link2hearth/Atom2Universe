package com.Atom2Universe.app.crypto.clicker

import android.content.Context

class NeutrinoRepository(context: Context) {

    private val prefs = context.getSharedPreferences("neutrino_prefs", Context.MODE_PRIVATE)

    fun addPending(count: Int) {
        val current = prefs.getInt("pending", 0)
        val lifetime = prefs.getInt("lifetime_neutrinos", 0)
        val widgetBalance = prefs.getInt("widget_balance", 0)
        prefs.edit()
            .putInt("pending", current + count)
            .putInt("lifetime_neutrinos", lifetime + count)
            .putInt("widget_balance", widgetBalance + count)
            .apply()
    }

    fun getLifetimeNeutrinos(): Int = prefs.getInt("lifetime_neutrinos", 0)

    fun getPending(): Int = prefs.getInt("pending", 0)

    fun claimPending(): Int {
        val pending = prefs.getInt("pending", 0)
        if (pending > 0) prefs.edit().putInt("pending", 0).apply()
        return pending
    }

    /** Solde affiché dans le widget blackjack (miroir du clicker, mis à jour par syncNeutrinos). */
    fun getWidgetBalance(): Int = prefs.getInt("widget_balance", 0)
    fun setWidgetBalance(n: Int) { prefs.edit().putInt("widget_balance", n.coerceAtLeast(0)).apply() }

    /** Le widget a perdu des neutrinos : les débiter du clicker au prochain sync. */
    fun addDebit(count: Int) {
        val current = prefs.getInt("pending_debit", 0)
        val widgetBalance = prefs.getInt("widget_balance", 0)
        prefs.edit()
            .putInt("pending_debit", current + count)
            .putInt("widget_balance", (widgetBalance - count).coerceAtLeast(0))
            .apply()
    }

    fun getDebit(): Int = prefs.getInt("pending_debit", 0)

    fun claimDebit(): Int {
        val debit = prefs.getInt("pending_debit", 0)
        if (debit > 0) prefs.edit().putInt("pending_debit", 0).apply()
        return debit
    }

    /** Returns true if a neutrino should be awarded (every 2 hard wins). */
    fun recordColorStackHardWin(): Boolean {
        val wins = prefs.getInt("colorstack_hard_wins", 0) + 1
        prefs.edit().putInt("colorstack_hard_wins", wins).apply()
        return wins % 2 == 0
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    /** Returns true if a neutrino should be awarded (every 2 hard wins). */
    fun recordPipeTapHardWin(): Boolean {
        val wins = prefs.getInt("pipetap_hard_wins", 0) + 1
        prefs.edit().putInt("pipetap_hard_wins", wins).apply()
        return wins % 2 == 0
    }
}
