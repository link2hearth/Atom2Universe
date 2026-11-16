package com.Atom2Universe.app.games.mercerie

import android.content.Context

/**
 * Ce que le joueur a déjà cousu : étoiles et meilleur score de chaque niveau.
 *
 * Une clé par niveau (`stars_7`, `best_7`) dans `mercerie_save` : la synchronisation des records
 * garde ainsi le meilleur de chaque appareil.
 */
class MercerieProgress(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun stars(level: Int) = prefs.getInt("stars_$level", 0)
    fun best(level: Int) = prefs.getInt("best_$level", 0)

    /** Le premier niveau pas encore gagné : c'est celui que « Jouer » lance. Il y en a toujours un. */
    val next: Int
        get() {
            var n = known
            while (stars(n) > 0) n++
            known = n
            return n
        }
    private var known = 1

    fun unlocked(level: Int) = level <= next

    /** Réglage du « ? » : montrer un coup possible quand le joueur ne joue pas. */
    var hints: Boolean
        get() = prefs.getBoolean("hints", true)
        set(value) { prefs.edit().putBoolean("hints", value).apply() }

    val totalScore: Int get() = (1..next).sumOf { best(it) }
    val totalStars: Int get() = (1..next).sumOf { stars(it) }

    class Record(val firstTime: Boolean, val newBest: Boolean)

    fun record(level: Int, score: Int, stars: Int): Record {
        val first = stars(level) == 0
        val better = score > best(level)
        prefs.edit()
            .putInt("stars_$level", maxOf(stars, stars(level)))
            .putInt("best_$level", maxOf(score, best(level)))
            .apply()
        return Record(first, better)
    }

    companion object {
        const val PREFS = "mercerie_save"
    }
}
