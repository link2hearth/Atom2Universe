package com.Atom2Universe.app.games.hamlet

import kotlin.math.ln
import kotlin.math.max

/**
 * La base de l'économie du village. Rien n'est fixé culture par culture : une seule base (pièces par
 * heure de champ) et une courbe de durée en tirent les gains. Une culture courte rapporte 2 à 3 fois la
 * base par heure, une culture de plusieurs heures 0,5 à 1 fois. Le niveau de déblocage ne compte pas :
 * une fraise débloquée tard ne rapporte pas plus qu'un blé du premier jour, elle sert d'ingrédient.
 */
object HamletEconomy {
    /** Pièces par heure de champ pour la culture de référence (4 h, facteur 1). */
    const val BASE_PER_HOUR = 120.0
    /** Part du gain dépensée en graines, et part de l'expérience, par rapport au gain. */
    const val SEED_SHARE = 0.18
    const val XP_SHARE = 0.15
    /** Un champ enchanté coûte cher : ses cultures rapportent presque deux fois plus. */
    const val MAGIC_FACTOR = 1.8
    /** Unités de culture rangées dans la réserve à chaque récolte. */
    const val UNITS_PER_HARVEST = 4
    const val MAX_TIER = 5

    /** (durée en secondes, facteur) : interpolation logarithmique entre les points. */
    private val CURVE = arrayOf(
        60 to 3.0, 300 to 2.6, 900 to 2.2, 1_800 to 1.8, 3_600 to 1.5,
        7_200 to 1.2, 14_400 to 1.0, 28_800 to 0.8, 57_600 to 0.65, 86_400 to 0.5,
    )

    fun factor(seconds: Int): Double {
        val s = seconds.coerceAtLeast(1)
        if (s <= CURVE.first().first) return CURVE.first().second
        if (s >= CURVE.last().first) return CURVE.last().second
        for (i in 1 until CURVE.size) {
            val (t1, f1) = CURVE[i]
            if (s <= t1) {
                val (t0, f0) = CURVE[i - 1]
                val k = (ln(s.toDouble()) - ln(t0.toDouble())) / (ln(t1.toDouble()) - ln(t0.toDouble()))
                return f0 + (f1 - f0) * k
            }
        }
        return CURVE.last().second
    }

    /** Pièces rapportées par une récolte de [seconds] secondes. */
    fun earn(seconds: Int, magic: Boolean = false): Int =
        max(1, Math.round(BASE_PER_HOUR * factor(seconds) * seconds / 3600.0 * (if (magic) MAGIC_FACTOR else 1.0)).toInt())

    fun seed(earn: Int): Int = max(1, Math.ceil(earn * SEED_SHARE).toInt())
    fun xp(earn: Int): Int = max(1, Math.round(earn * XP_SHARE).toInt())

    /** Pièces gagnées par heure de champ, tout compris (pour les bancs et les fiches). */
    fun perHour(c: Crop): Double = (c.earn - c.cost) * 3600.0 / c.time

    // ------------------------------------------------------------------ réserve et améliorations

    /** Place dans la réserve pour chaque culture ; elle grandit avec la halle. */
    fun pantryCap(hall: Int) = 40 + 20 * hall

    /** Unités de culture exigées pour passer au rang [toTier] (2 à 5). */
    fun upgradeUnits(toTier: Int) = when (toTier) { 2 -> 6; 3 -> 12; 4 -> 20; else -> 32 }

    data class Need(val crop: String, val units: Int)

    /** Cultures ordinaires, par ordre de déblocage : la liste d'où viennent les ingrédients. */
    private val ordinary: List<Crop> by lazy { Hamlet.crops.filter { !it.magic } }

    /**
     * Les ingrédients du rang [toTier] pour un bâtiment : la culture qui s'ouvre en même temps que lui pour
     * le rang 2, puis des cultures de plus en plus tardives. Du rang 4, deux cultures se partagent la quantité.
     * Le même bâtiment demande toujours la même chose.
     */
    fun upgradeNeeds(def: Def, toTier: Int): List<Need> {
        if (toTier !in 2..MAX_TIER) return emptyList()
        val list = ordinary
        val opened = list.count { it.lvl <= def.lvl }.coerceAtLeast(1)
        val wobble = Math.floorMod(def.id.hashCode(), 3) - 1
        val index = (opened - 1 + (toTier - 2) * 3 + wobble).coerceIn(0, list.size - 1)
        val units = upgradeUnits(toTier)
        if (toTier < 4) return listOf(Need(list[index].id, units))
        val second = (index - 2).coerceAtLeast(0).let { if (it == index) (index + 1).coerceAtMost(list.size - 1) else it }
        return listOf(Need(list[index].id, units - units / 3), Need(list[second].id, units / 3))
    }

    /** Pièces demandées pour passer au rang suivant d'un objet de rang [tier] et de prix [cost]. */
    fun upgradeCoins(cost: Int, tier: Int) = max(30, cost * (tier + 1) / 2)
}
