package com.Atom2Universe.app.music.sync.peer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.edit

/**
 * Gère la liste des réseaux Wi-Fi de confiance.
 * La sync LAN ne s'active que sur les réseaux explicitement marqués.
 *
 * Identifiant de réseau :
 *   - API 31+ : SSID via NetworkCapabilities.transportInfo (sans permission de localisation)
 *   - API 26-30 : SSID via WifiManager (peut être masqué sur API 29-30 sans localisation,
 *                 fallback sur networkId)
 */
object TrustedNetworkManager {

    private const val PREFS_NAME  = "trusted_wifi_networks"
    private const val KEY_TRUSTED = "trusted_ids"

    /**
     * Identifiant stable du réseau actuel, ou null si pas connecté en Wi-Fi.
     * Peut être le SSID (ex: "Maison") ou "net#42" si le SSID n'est pas accessible.
     */
    fun getCurrentNetworkId(context: Context): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getNetworkIdApi31(context)
        } else {
            getNetworkIdLegacy(context)
        }
    }

    /**
     * Nom lisible du réseau actuel pour l'affichage dans les settings.
     */
    fun getCurrentNetworkName(context: Context): String? = getCurrentNetworkId(context)

    fun isCurrentNetworkTrusted(context: Context): Boolean {
        val id = getCurrentNetworkId(context) ?: return false
        return getTrustedIds(context).contains(id)
    }

    fun trustCurrentNetwork(context: Context) {
        val id = getCurrentNetworkId(context) ?: return
        val ids = getTrustedIds(context).toMutableSet()
        ids.add(id)
        saveTrustedIds(context, ids)
    }

    fun untrustCurrentNetwork(context: Context) {
        val id = getCurrentNetworkId(context) ?: return
        val ids = getTrustedIds(context).toMutableSet()
        ids.remove(id)
        saveTrustedIds(context, ids)
    }

    fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    // ── Privé ─────────────────────────────────────────────────────────────────

    private fun getTrustedIds(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_TRUSTED, emptySet()) ?: emptySet()

    private fun saveTrustedIds(context: Context, ids: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putStringSet(KEY_TRUSTED, ids) }
    }

    private fun getNetworkIdApi31(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return null) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val wifiInfo = caps.transportInfo as? WifiInfo ?: return null
        val ssid = wifiInfo.ssid?.removePrefix("\"")?.removeSuffix("\"")
        return if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") null else ssid
    }

    @Suppress("DEPRECATION")
    private fun getNetworkIdLegacy(context: Context): String? {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        if (!wm.isWifiEnabled) return null
        val info = wm.connectionInfo ?: return null
        val ssid = info.ssid?.removePrefix("\"")?.removeSuffix("\"")
        if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") return ssid
        // Fallback : identifiant numérique du réseau sauvegardé
        val netId = info.networkId
        return if (netId != -1) "net#$netId" else null
    }
}
