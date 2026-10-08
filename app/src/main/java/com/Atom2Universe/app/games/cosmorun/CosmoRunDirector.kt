package com.Atom2Universe.app.games.cosmorun

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random

/**
 * Le metteur en scène : choisit le prochain motif et l'intervalle qui le sépare du précédent.
 * Il alterne respirations et dangers, monte en palier avec la distance, et ne relie deux motifs que
 * si une voie de sortie du premier peut rejoindre une voie d'entrée du second dans l'intervalle.
 */
class CosmoRunDirector(private val random: Random) {
    class Choice(val pattern: CosmoRunPatterns.Pattern, val gapRows: Int)

    var roofsEnabled = true
    private var previous: CosmoRunPatterns.Pattern? = null
    private val recent = ArrayDeque<String>()
    private var dangersSinceBreather = 0
    private var dangersBeforeBreather = 2
    private var started = false

    fun reset() {
        previous = null; recent.clear(); dangersSinceBreather = 0; dangersBeforeBreather = 2; started = false
    }

    /** Palier maximal à cette distance : un de plus tous les 600 m, jusqu'au 5 (à 2 400 m). */
    fun tierAt(distance: Float) = min(5, 1 + (distance / TIER_LENGTH).toInt())

    fun next(distance: Float): Choice {
        val all = CosmoRunPatterns.all
        val prev = previous
        val wantBreather = !started || dangersSinceBreather >= dangersBeforeBreather ||
            (prev != null && prev.tier >= 4 && !prev.breather)
        started = true
        val tier = tierAt(distance)
        val pool = all.filter {
            it.breather == wantBreather && (roofsEnabled || !it.usesRoof) && it.name !in recent &&
                it.themes and (1 shl (distance.toInt() / CosmoRunGame.SECTOR_LENGTH % 3)) != 0 &&
                (wantBreather || it.tier <= tier)
        }
        // Les motifs du palier courant pèsent le triple des paliers inférieurs.
        val weighted = pool.map { p -> p to (p.weight * if (!p.breather && p.tier >= tier) 3 else 1) }
        var pick: CosmoRunPatterns.Pattern? = null
        var gap = 2
        var candidates = weighted
        while (candidates.isNotEmpty() && pick == null) {
            val total = candidates.sumOf { it.second }
            var roll = random.nextInt(total)
            val chosen = candidates.first { roll -= it.second; roll < 0 }
            val mirrored = if (random.nextBoolean()) CosmoRunPatterns.mirror(chosen.first) else chosen.first
            val g = gapBetween(prev, mirrored)
            if (g != null) { pick = mirrored; gap = g } else candidates = candidates.filter { it !== chosen }
        }
        val fallback = pick ?: all.first { it.breather && it.name !in recent && (roofsEnabled || !it.usesRoof) }
        if (pick == null) gap = gapBetween(prev, fallback) ?: MAX_GAP
        // Passé le dernier palier, la piste ne monte plus : ce sont les respirations qui raréfient.
        val rarer = ((distance - TIER_LENGTH * 4f) / 1500f).toInt().coerceIn(0, 2)
        if (fallback.breather) { dangersSinceBreather = 0; dangersBeforeBreather = 2 + random.nextInt(2) + rarer }
        else dangersSinceBreather++
        previous = fallback
        recent.addLast(fallback.name); while (recent.size > 2) recent.removeFirst()
        return Choice(fallback, if (prev == null) 0 else gap)
    }

    /** Rangées vides à laisser entre [a] et [b], ou null si aucune voie de sortie ne rejoint une voie d'entrée. */
    fun gapBetween(a: CosmoRunPatterns.Pattern?, b: CosmoRunPatterns.Pattern): Int? {
        if (a == null) return 0
        val base = when {
            b.breather -> 1
            a.exitRoof != 0 -> 3       // on redescend d'un toit : quelques rangées de sol libre
            else -> 2
        }
        val exits = a.exit or a.exitRoof
        var shift = CosmoRunGame.NUM_LANES
        for (x in 0 until CosmoRunGame.NUM_LANES) if (exits and (1 shl x) != 0)
            for (y in 0 until CosmoRunGame.NUM_LANES) if (b.entry and (1 shl y) != 0) shift = min(shift, abs(x - y))
        if (shift == CosmoRunGame.NUM_LANES) return null
        val needed = maxOf(base, ceil(shift / LANES_PER_ROW).toInt())
        return if (needed > MAX_GAP) null else needed
    }

    companion object {
        const val TIER_LENGTH = 600f
        const val MAX_GAP = 5
        /** Deux voies en 0,22 s : un peu plus d'une voie par rangée de 0,16 s. */
        const val LANES_PER_ROW = 1.4f
    }
}
