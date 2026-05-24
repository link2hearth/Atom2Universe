package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import androidx.core.content.edit

class FactoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("clicker_factories", Context.MODE_PRIVATE)

    fun getCount(type: FactoryType): Int = prefs.getInt(type.id, 0)

    fun getAllCounts(): Map<FactoryType, Int> =
        FactoryType.values().associateWith { getCount(it) }

    fun increment(type: FactoryType) =
        prefs.edit { putInt(type.id, getCount(type) + 1) }

    fun reset() = prefs.edit { clear() }
}
