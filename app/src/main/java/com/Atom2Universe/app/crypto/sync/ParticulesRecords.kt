package com.Atom2Universe.app.crypto.sync

import android.content.Context
import com.Atom2Universe.app.games.particules.data.ParticulesDatabase
import com.Atom2Universe.app.games.particules.data.ParticulesMetaEntity

/**
 * Les records de Particules, vus comme s'ils étaient des préférences.
 *
 * Tous les autres records vivent dans les SharedPreferences de leur jeu ; Particules
 * range les siens dans sa base Room, à côté de la progression de la boutique. Plutôt
 * que d'apprendre Room à toute la mécanique de sync, cet objet se présente sous un
 * nom de « préférences » réservé ([SOURCE]) et traduit lecture, écriture et
 * effacement vers la base. Seuls les records passent : la boutique reste propre à
 * chaque appareil.
 */
object ParticulesRecords {

    /** Faux nom de préférences sous lequel les records apparaissent dans la sync. */
    const val SOURCE = "particules_meta"

    const val KEY_HIGH_SCORE = "highScore"
    const val KEY_HIGHEST_LEVEL = "highestLevel"
    const val KEY_BEST_COMBO = "bestCombo"

    /** Clé → valeur, sans les records jamais établis (0). */
    suspend fun read(context: Context): Map<String, Double> {
        val meta = dao(context).getMeta() ?: return emptyMap()
        return mapOf(
            KEY_HIGH_SCORE to meta.highScore.toDouble(),
            KEY_HIGHEST_LEVEL to meta.highestLevel.toDouble(),
            KEY_BEST_COMBO to meta.bestCombo.toDouble()
        ).filterValues { it > 0.0 }
    }

    suspend fun write(context: Context, key: String, value: Double) {
        update(context) { meta ->
            when (key) {
                KEY_HIGH_SCORE -> meta.copy(highScore = value.toLong())
                KEY_HIGHEST_LEVEL -> meta.copy(highestLevel = value.toInt())
                KEY_BEST_COMBO -> meta.copy(bestCombo = value.toInt())
                else -> meta
            }
        }
    }

    /** Remet à zéro les records dont la clé est désignée, sans toucher à la boutique. */
    suspend fun erase(context: Context, matches: (String) -> Boolean) {
        update(context) { meta ->
            meta.copy(
                highScore = if (matches(KEY_HIGH_SCORE)) 0L else meta.highScore,
                highestLevel = if (matches(KEY_HIGHEST_LEVEL)) 0 else meta.highestLevel,
                bestCombo = if (matches(KEY_BEST_COMBO)) 0 else meta.bestCombo
            )
        }
    }

    private suspend fun update(context: Context, change: (ParticulesMetaEntity) -> ParticulesMetaEntity) {
        val dao = dao(context)
        val current = dao.getMeta() ?: ParticulesMetaEntity()
        val next = change(current)
        if (next != current) dao.upsert(next)
    }

    private fun dao(context: Context) =
        ParticulesDatabase.getInstance(context.applicationContext).metaDao()
}
