package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class NeutrinoRepository {
    private val prefs: SharedPreferences
    constructor(context: Context) { prefs = context.getSharedPreferences("neutrino_prefs", Context.MODE_PRIVATE) }
    internal constructor(preferences: SharedPreferences) { prefs = preferences }

    fun getBalance(): Int = prefs.getInt("balance", 0)

    fun isBalanceInitialized(): Boolean = prefs.contains("balance")

    fun addBalance(count: Int) {
        val newBalance = getBalance() + count
        val lifetime = prefs.getInt("lifetime_neutrinos", 0)
        prefs.edit()
            .putInt("balance", newBalance.coerceAtLeast(0))
            .putInt("lifetime_neutrinos", lifetime + count)
            .apply()
    }

    /** A durable receipt and its reward share one transaction, including process restarts. */
    fun addBalanceOnce(receipt: String, count: Int): Boolean = synchronized(prefs) {
        require(count>=0 && (receipt.startsWith("billiards:") || receipt.startsWith("jigsaw:")))
        val key="reward_receipt_$receipt"
        if(prefs.getBoolean(key,false)) return@synchronized false
        prefs.edit().putBoolean(key,true)
            .putInt("balance",(getBalance().toLong()+count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .putInt("lifetime_neutrinos",(getLifetimeNeutrinos().toLong()+count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .commit()
    }

    fun hasRewardReceipt(receipt: String): Boolean = prefs.getBoolean("reward_receipt_$receipt", false)

    enum class GuidePurchase { UNLOCKED, NOT_ENOUGH, WRITE_FAILED }

    fun hasJigsawGuide(sessionId: String): Boolean = prefs.getBoolean("jigsaw_guide_$sessionId", false)

    /** The debit and session entitlement are written together, so reloading a puzzle never charges twice. */
    fun purchaseJigsawGuide(sessionId: String): GuidePurchase = synchronized(prefs) {
        require(sessionId.matches(Regex("[a-fA-F0-9-]{36}")))
        val key = "jigsaw_guide_$sessionId"
        val balance = getBalance()
        if (prefs.getBoolean(key, false)) {
            // Commit again also repairs a previous write that only reached the in-memory preferences.
            return@synchronized if (prefs.edit().putBoolean(key, true).putInt("balance", balance).commit())
                GuidePurchase.UNLOCKED else GuidePurchase.WRITE_FAILED
        }
        if (balance < NeutrinoRewards.JIGSAW_GUIDE_COST) return@synchronized GuidePurchase.NOT_ENOUGH
        if (prefs.edit().putInt("balance", balance - NeutrinoRewards.JIGSAW_GUIDE_COST).putBoolean(key, true).commit())
            GuidePurchase.UNLOCKED else GuidePurchase.WRITE_FAILED
    }

    fun subtractBalance(count: Int): Boolean {
        val current = getBalance()
        if (current < count) return false
        setBalance(current - count)
        return true
    }

    fun setBalance(n: Int) {
        prefs.edit { putInt("balance", n.coerceAtLeast(0)) }
    }

    fun getLifetimeNeutrinos(): Int = prefs.getInt("lifetime_neutrinos", 0)

    /** Écriture directe — utilisée par la restauration d'une sauvegarde Drive. */
    fun setLifetimeNeutrinos(n: Int) {
        prefs.edit { putInt("lifetime_neutrinos", n.coerceAtLeast(0)) }
    }

    fun reset() {
        prefs.edit { clear() }
    }

    fun recordColorStackHardWin(): Boolean {
        val wins = prefs.getInt("colorstack_hard_wins", 0) + 1
        prefs.edit { putInt("colorstack_hard_wins", wins) }
        return wins % 2 == 0
    }

    fun recordPipeTapHardWin(): Boolean {
        val wins = prefs.getInt("pipetap_hard_wins", 0) + 1
        prefs.edit { putInt("pipetap_hard_wins", wins) }
        return wins % 2 == 0
    }
}
