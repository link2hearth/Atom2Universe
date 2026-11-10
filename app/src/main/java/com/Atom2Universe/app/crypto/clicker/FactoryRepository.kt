package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import androidx.core.content.edit

class FactoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("clicker_factories", Context.MODE_PRIVATE)

    fun getCount(type: FactoryType): Int = prefs.getInt(type.id, 0)

    fun getAllCounts(): Map<FactoryType, Int> =
        FactoryType.values().associateWith { getCount(it) }

    fun setCount(type: FactoryType, count: Int) =
        prefs.edit { putInt(type.id, count.coerceAtLeast(0)) }

    fun setCounts(counts: Map<FactoryType, Int>) = prefs.edit {
        for ((type, count) in counts) putInt(type.id, count)
    }

    fun reset() = prefs.edit { clear() }
}
