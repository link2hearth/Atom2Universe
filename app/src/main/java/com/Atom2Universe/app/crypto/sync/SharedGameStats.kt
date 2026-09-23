package com.Atom2Universe.app.crypto.sync

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Un compteur qui s'additionne entre appareils (parties jouées, victoires…).
 *
 * Pour qu'un nouveau compteur voyage, ajouter sa ligne dans [COUNTER_KEYS].
 */
data class CounterKey(
    val prefsName: String,
    /** Nom exact de la clé, ou son début si [isPrefix]. */
    val key: String,
    val isPrefix: Boolean = false
) {
    fun matches(prefs: String, fullKey: String): Boolean =
        prefs == prefsName && if (isPrefix) fullKey.startsWith(key) else fullKey == key
}

val COUNTER_KEYS = listOf(
    CounterKey("game_stats", "solitaire_played"),
    CounterKey("game_stats", "solitaire_won"),
    CounterKey("game_stats", "colorstack_hard_played"),
    CounterKey("game_stats", "colorstack_hard_won"),
    CounterKey("game_stats", "sudoku_played"),
    CounterKey("game_stats", "sudoku_won"),
    CounterKey("game_stats", "chess_played"),
    CounterKey("game_stats", "chess_won"),
    CounterKey("game_stats", "draughts_played"),
    CounterKey("game_stats", "draughts_won"),
    CounterKey("game_stats", "game2048_played"),
    CounterKey("game_stats", "game2048_won"),
    CounterKey("game_stats", "blackjack_played"),
    CounterKey("game_stats", "blackjack_won"),
    CounterKey("game_stats", "pipetap_hard_won"),
    CounterKey("game_stats", "othello_played"),
    CounterKey("game_stats", "othello_won"),
    CounterKey("circles_save", "solved_", isPrefix = true),
    CounterKey("escape_labyrinth_prefs", "solved"),
    CounterKey("escape_labyrinth_prefs", "solved_perfect"),
    CounterKey("starbridges_save", "solved"),
    CounterKey("trebuchet_game", "sites_destroyed")
)

/**
 * Une statistique qu'on peut supprimer depuis la page stats : un record ou un
 * compteur, désigné par ses préférences et sa clé (ou le début de ses clés).
 */
data class SyncedStat(val prefsName: String, val key: String, val isPrefix: Boolean = false) {
    /** Le motif stocké dans les suppressions : `prefs.clé`, ou `prefs.début*`. */
    val pattern: String get() = highScoreId(prefsName, key) + if (isPrefix) "*" else ""

    val isCounter: Boolean
        get() = COUNTER_KEYS.any { it.prefsName == prefsName && (it.matches(prefsName, key) || it.key == key) }
}

/**
 * Tout ce que cet appareil sait des statistiques partagées : les compteurs publiés
 * par les AUTRES appareils (ceux d'ici restent dans les préférences de chaque jeu)
 * et les suppressions datées.
 *
 * Pourquoi dater les suppressions ? Parce que la sync garde le meilleur record de
 * tous les appareils : un record effacé ici reviendrait du cloud à la sync suivante.
 * La date dit « tout ce qui existait avant cet instant est effacé », et chaque
 * appareil l'applique en la découvrant.
 */
object SharedGameStats {

    private const val PREFS = "games_sync_state"
    private const val KEY_OTHER_DEVICES = "other_devices"
    private const val KEY_RESETS = "resets"

    /** Le nom affiché pour cet appareil : celui choisi dans les réglages Android, sinon le modèle. */
    fun deviceName(context: Context): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL
            ?: "Android"

    // ─── Compteurs ────────────────────────────────────────────────────────────

    fun readLocalCounters(context: Context): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        COUNTER_KEYS.groupBy { it.prefsName }.forEach { (prefsName, keys) ->
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.all.forEach { (key, raw) ->
                if (keys.none { it.matches(prefsName, key) }) return@forEach
                val v = (raw as? Number)?.toInt() ?: return@forEach
                if (v > 0) out[highScoreId(prefsName, key)] = v
            }
        }
        return out
    }

    fun otherDevices(context: Context): Map<String, DeviceCountersData> {
        val raw = prefs(context).getString(KEY_OTHER_DEVICES, null) ?: return emptyMap()
        return try {
            DeviceCountersData.mapFromJson(JSONObject(raw))
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveOtherDevices(context: Context, devices: Map<String, DeviceCountersData>) {
        prefs(context).edit {
            putString(KEY_OTHER_DEVICES, DeviceCountersData.mapToJson(devices).toString())
        }
    }

    /** Ce compteur, tous appareils confondus. */
    fun total(context: Context, prefsName: String, key: String): Int {
        val local = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).getInt(key, 0)
        val id = highScoreId(prefsName, key)
        return local + otherDevices(context).values.sumOf { it.counters[id] ?: 0 }
    }

    /**
     * Le détail par appareil pour ces compteurs, cet appareil en premier.
     * Les appareils qui n'ont joué à aucun d'eux sont omis.
     */
    fun breakdown(context: Context, stats: List<SyncedStat>): List<Pair<String?, List<Int>>> {
        val ids = stats.map { highScoreId(it.prefsName, it.key) }
        val rows = mutableListOf<Pair<String?, List<Int>>>()
        val local = stats.map {
            context.getSharedPreferences(it.prefsName, Context.MODE_PRIVATE).getInt(it.key, 0)
        }
        // null = cet appareil : l'écran le libelle lui-même, dans la bonne langue.
        if (local.any { it > 0 }) rows += null to local
        otherDevices(context).values.sortedBy { it.name }.forEach { device ->
            val values = ids.map { device.counters[it] ?: 0 }
            if (values.any { it > 0 }) rows += device.name to values
        }
        return rows
    }

    // ─── Suppressions ─────────────────────────────────────────────────────────

    fun resets(context: Context): Map<String, Long> {
        val raw = prefs(context).getString(KEY_RESETS, null) ?: return emptyMap()
        return try {
            resetsFromJson(JSONObject(raw))
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveResets(context: Context, resets: Map<String, Long>) {
        prefs(context).edit { putString(KEY_RESETS, resetsToJson(resets).toString()) }
    }

    /** Le motif désigne-t-il cet identifiant `prefs.clé` ? */
    fun patternMatches(pattern: String, id: String): Boolean =
        if (pattern.endsWith("*")) id.startsWith(pattern.dropLast(1)) else id == pattern

    /** La suppression la plus récente qui touche cet identifiant, ou 0. */
    fun resetTimeFor(resets: Map<String, Long>, id: String): Long =
        resets.filterKeys { patternMatches(it, id) }.values.maxOrNull() ?: 0L

    /**
     * Supprime ces statistiques ici, tout de suite, et note la date pour que la
     * prochaine sync les efface aussi du cloud et des autres appareils.
     */
    suspend fun delete(context: Context, stats: List<SyncedStat>, now: Long = System.currentTimeMillis()) {
        stats.forEach { stat -> eraseLocal(context, stat.pattern) }

        // Les compteurs des autres appareils disparaissent aussi de l'affichage d'ici,
        // sans attendre qu'ils se synchronisent.
        val others = otherDevices(context).mapValues { (_, device) ->
            device.copy(counters = device.counters.filterKeys { id ->
                stats.none { patternMatches(it.pattern, id) }
            })
        }
        saveOtherDevices(context, others)

        val resets = resets(context).toMutableMap()
        stats.forEach { resets[it.pattern] = now }
        saveResets(context, resets)
    }

    /** Efface de cet appareil les valeurs désignées par le motif. */
    suspend fun eraseLocal(context: Context, pattern: String) {
        val dot = pattern.indexOf('.')
        if (dot <= 0) return
        val prefsName = pattern.substring(0, dot)
        if (prefsName == ParticulesRecords.SOURCE) {
            ParticulesRecords.erase(context) { key -> patternMatches(pattern, highScoreId(prefsName, key)) }
            return
        }
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val doomed = prefs.all.keys.filter { patternMatches(pattern, highScoreId(prefsName, it)) }
        if (doomed.isEmpty()) return
        prefs.edit { doomed.forEach { remove(it) } }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
