package com.Atom2Universe.app.music.sync

import android.content.Context
import java.util.UUID

/**
 * Identité stable de l'appareil courant.
 * UUID généré une seule fois, stocké dans SharedPreferences.
 * Survit aux sync et aux migrations — ne dépend d'aucune BDD.
 */
object DeviceIdentity {

    private const val PREFS_NAME = "a2u_device_identity"
    private const val KEY_DEVICE_ID = "device_uuid"

    @Volatile
    private var cachedId: String? = null

    fun getDeviceId(context: Context): String {
        cachedId?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_DEVICE_ID, null)
        if (stored != null) {
            cachedId = stored
            return stored
        }
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        cachedId = newId
        return newId
    }
}
