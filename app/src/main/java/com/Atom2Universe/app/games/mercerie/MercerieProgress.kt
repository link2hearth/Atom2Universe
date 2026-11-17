package com.Atom2Universe.app.games.mercerie

import android.content.Context

/**
 * Ce que le joueur a déjà cousu : étoiles et meilleur score de chaque niveau.
 *
 * Une clé par niveau (`stars_7`, `best_7`) dans `mercerie_save` : la synchronisation des records
 * garde ainsi le meilleur de chaque appareil. Le mode facile a ses propres clés (`easy_stars_7`,
 * `easy_best_7`) : un niveau fini en facile ouvre le suivant, mais ses étoiles restent à part, ne
 * comptent pas dans les totaux et se voient comme telles sur la carte.
 */
class MercerieProgress(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun stars(level: Int) = prefs.getInt("stars_$level", 0)
    fun best(level: Int) = prefs.getInt("best_$level", 0)
    fun easyStars(level: Int) = prefs.getInt("easy_stars_$level", 0)
    fun easyBest(level: Int) = prefs.getInt("easy_best_$level", 0)

    /** Fini seulement en mode facile : la carte le montre. */
    fun onlyEasy(level: Int) = stars(level) == 0 && easyStars(level) > 0

    /** Gagné, dans un mode ou dans l'autre. */
    fun done(level: Int) = stars(level) > 0 || easyStars(level) > 0

    /** Le premier niveau pas encore gagné : c'est celui que « Jouer » lance. Il y en a toujours un. */
    val next: Int
        get() {
            var n = known
            while (done(n)) n++
            known = n
            return n
        }
    private var known = 1

    fun unlocked(level: Int) = level <= next

    /**
     * Le numéro où commencent les dix niveaux de la mise à jour : juste après le dernier niveau
     * gagné quand le joueur la découvre, au plus tôt le 31. Fixé une fois pour toutes, pour que ses
     * étoiles restent sur les mêmes niveaux.
     */
    val extraAt: Int
        get() {
            val stored = prefs.getInt("extra_at", 0)
            if (stored > 0) return stored
            val at = maxOf(MercerieLevels.HANDMADE + 1, next)
            prefs.edit().putInt("extra_at", at).apply()
            return at
        }

    /** Réglage du « ? » : montrer un coup possible quand le joueur ne joue pas. */
    var hints: Boolean
        get() = prefs.getBoolean("hints", true)
        set(value) { prefs.edit().putBoolean("hints", value).apply() }

    /** Haut-parleur de la barre du haut : sons coupés. */
    var muted: Boolean
        get() = prefs.getBoolean("muted", false)
        set(value) { prefs.edit().putBoolean("muted", value).apply() }

    /** Bascule de l'accueil : la feutrine de nuit. */
    var dark: Boolean
        get() = prefs.getBoolean("dark", false)
        set(value) { prefs.edit().putBoolean("dark", value).apply() }

    /** Réglage du « ? » : le mode facile, le jeu d'avant la mise à jour ([MercerieLevel.easy]). */
    var easy: Boolean
        get() = prefs.getBoolean("easy", false)
        set(value) { prefs.edit().putBoolean("easy", value).apply() }

    /** Totaux du mode normal seulement. */
    val totalScore: Int get() = (1..next).sumOf { best(it) }
    val totalStars: Int get() = (1..next).sumOf { stars(it) }

    /** Meilleur score du niveau dans le mode [easy]. */
    fun best(level: Int, easy: Boolean) = if (easy) easyBest(level) else best(level)

    class Record(val firstTime: Boolean, val newBest: Boolean)

    fun record(level: Int, score: Int, stars: Int, easy: Boolean): Record {
        val prefix = if (easy) "easy_" else ""
        val first = prefs.getInt("${prefix}stars_$level", 0) == 0
        val before = best(level, easy)
        prefs.edit()
            .putInt("${prefix}stars_$level", maxOf(stars, prefs.getInt("${prefix}stars_$level", 0)))
            .putInt("${prefix}best_$level", maxOf(score, before))
            .apply()
        return Record(first, score > before)
    }

    companion object {
        const val PREFS = "mercerie_save"
    }
}
