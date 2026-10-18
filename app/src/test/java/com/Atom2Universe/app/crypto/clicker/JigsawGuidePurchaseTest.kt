package com.Atom2Universe.app.crypto.clicker

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Executors

class JigsawGuidePurchaseTest {
    private val session = UUID.randomUUID().toString()

    @Test fun `guide is charged once across repository instances and process restarts`() {
        val prefs = MemoryPreferences(mapOf("balance" to 3, "lifetime_neutrinos" to 50))
        assertEquals(NeutrinoRepository.GuidePurchase.UNLOCKED, NeutrinoRepository(prefs).purchaseJigsawGuide(session))
        assertEquals(NeutrinoRepository.GuidePurchase.UNLOCKED, NeutrinoRepository(prefs).purchaseJigsawGuide(session))
        val restored = NeutrinoRepository(prefs.restart())
        assertTrue(restored.hasJigsawGuide(session)); assertEquals(2, restored.getBalance())
        assertEquals(NeutrinoRepository.GuidePurchase.UNLOCKED, restored.purchaseJigsawGuide(session))
        assertEquals(2, restored.getBalance()); assertEquals(50, restored.getLifetimeNeutrinos())
    }

    @Test fun `insufficient balance neither unlocks nor spends and an existing purchase remains usable at zero`() {
        val prefs = MemoryPreferences(mapOf("balance" to 0))
        val repository = NeutrinoRepository(prefs)
        assertEquals(NeutrinoRepository.GuidePurchase.NOT_ENOUGH, repository.purchaseJigsawGuide(session))
        assertFalse(repository.hasJigsawGuide(session)); assertEquals(0, repository.getBalance())
        repository.setBalance(1)
        assertEquals(NeutrinoRepository.GuidePurchase.UNLOCKED, repository.purchaseJigsawGuide(session))
        assertEquals(0, repository.getBalance())
        assertEquals(NeutrinoRepository.GuidePurchase.UNLOCKED, repository.purchaseJigsawGuide(session))
    }

    @Test fun `retry after a failed preference commit repairs the purchase without charging twice`() {
        val prefs = MemoryPreferences(mapOf("balance" to 4))
        val repository = NeutrinoRepository(prefs)
        prefs.failNextCommit = true
        assertEquals(NeutrinoRepository.GuidePurchase.WRITE_FAILED, repository.purchaseJigsawGuide(session))
        assertFalse(NeutrinoRepository(prefs.restart()).hasJigsawGuide(session))
        assertEquals(NeutrinoRepository.GuidePurchase.UNLOCKED, repository.purchaseJigsawGuide(session))
        val restored = NeutrinoRepository(prefs.restart())
        assertTrue(restored.hasJigsawGuide(session)); assertEquals(3, restored.getBalance())
    }

    @Test fun `concurrent requests only spend once and the full completion reward is paid afterwards`() {
        val prefs = MemoryPreferences(mapOf("balance" to 2, "lifetime_neutrinos" to 9))
        val executor = Executors.newFixedThreadPool(2)
        try {
            val purchases = (0..1).map { executor.submit<NeutrinoRepository.GuidePurchase> { NeutrinoRepository(prefs).purchaseJigsawGuide(session) } }
            purchases.forEach { assertEquals(NeutrinoRepository.GuidePurchase.UNLOCKED, it.get()) }
        } finally { executor.shutdownNow() }
        val repository = NeutrinoRepository(prefs)
        assertEquals(1, repository.getBalance())
        val reward = NeutrinoRewards.jigsaw(1)
        assertTrue(repository.addBalanceOnce("jigsaw:$session", reward))
        assertEquals(1 + reward, repository.getBalance()); assertEquals(9 + reward, repository.getLifetimeNeutrinos())
        assertFalse(repository.addBalanceOnce("jigsaw:$session", reward))
    }

    /** Models Android commit: memory changes immediately; a failed disk write does not survive restart. */
    private class MemoryPreferences(initial: Map<String, Any?>) : SharedPreferences {
        private var memory = initial.toMutableMap()
        private var disk = initial.toMutableMap()
        var failNextCommit = false
        fun restart() = MemoryPreferences(disk)
        override fun getAll(): MutableMap<String, *> = memory.toMutableMap()
        override fun contains(key: String?) = key in memory
        override fun getBoolean(key: String?, defValue: Boolean) = memory[key] as? Boolean ?: defValue
        override fun getInt(key: String?, defValue: Int) = memory[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long) = memory[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float) = memory[key] as? Float ?: defValue
        override fun getString(key: String?, defValue: String?) = memory[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST") override fun getStringSet(key: String?, defValues: MutableSet<String>?) = memory[key] as? MutableSet<String> ?: defValues
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = Editor()
        private inner class Editor : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private var cleared = false
            private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply { changes[key!!] = value }
            override fun putBoolean(key: String?, value: Boolean) = put(key, value)
            override fun putInt(key: String?, value: Int) = put(key, value)
            override fun putLong(key: String?, value: Long) = put(key, value)
            override fun putFloat(key: String?, value: Float) = put(key, value)
            override fun putString(key: String?, value: String?) = put(key, value)
            override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values)
            override fun remove(key: String?) = put(key, null)
            override fun clear(): SharedPreferences.Editor = apply { cleared = true }
            override fun apply() { commit() }
            override fun commit(): Boolean {
                if (cleared) memory.clear()
                changes.forEach { (key, value) -> if (value == null) memory.remove(key) else memory[key] = value }
                if (failNextCommit) { failNextCommit = false; return false }
                disk = memory.toMutableMap()
                return true
            }
        }
    }
}
