package com.Atom2Universe.app.games.caves.ai

import kotlin.random.Random

/**
 * Découpe des cases de déploiement en **escouades posées ensemble**.
 *
 * Une escouade n'a de sens que si ses hommes commencent au même endroit : sinon le premier ordre
 * de regroupement les fait traverser la carte chacun de leur côté, et l'assaut arrive en file
 * indienne — exactement ce que la coordination cherche à éviter. On prend donc une case au hasard
 * comme chef de file, puis ses voisines dans un petit rayon, espacées d'un bloc et demi pour que
 * les corps ne se chevauchent pas au départ.
 */
internal object SquadSpawn {

    /** Rayon de la grappe autour du chef de file, en blocs. */
    const val CLUSTER_RADIUS = 7

    /** Deux escouades d'un même réservoir ne démarrent jamais plus près que ça. */
    const val SQUAD_SEPARATION = 26

    private const val MIN_SPACING_SQ = 2 * 2

    /**
     * Une escouade par réservoir (une pièce, une parcelle…). Les réservoirs sont tirés au hasard,
     * et ceux qui ne peuvent pas loger au moins deux hommes sont ignorés.
     */
    fun fromZones(grid: NavGrid, zones: List<List<Int>>, squads: Int, size: Int,
                  rng: Random): List<IntArray> {
        val result = ArrayList<IntArray>(squads)
        for (zone in zones.filter { it.size >= 2 }.shuffled(rng)) {
            if (result.size >= squads) break
            val group = grab(grid, zone, size, rng)
            if (group.size >= 2) result.add(group)
        }
        return result
    }

    /** Un seul grand réservoir : on y découpe des grappes éloignées les unes des autres. */
    fun cluster(grid: NavGrid, pool: List<Int>, squads: Int, size: Int, rng: Random): List<IntArray> {
        val result = ArrayList<IntArray>(squads)
        val seeds = ArrayList<Int>(squads)
        var attempts = 0
        while (result.size < squads && attempts < squads * SEED_ATTEMPTS) {
            attempts++
            val seed = pool[rng.nextInt(pool.size)]
            if (seeds.any { near(grid, it, seed, SQUAD_SEPARATION) }) continue
            val group = grab(grid, pool, size, rng, seed)
            if (group.size < 2) continue
            seeds.add(seed)
            result.add(group)
        }
        return result
    }

    /** Le chef de file puis ses voisins immédiats, dans l'ordre où ils tiennent. */
    fun grab(grid: NavGrid, pool: List<Int>, size: Int, rng: Random, seed: Int = -1): IntArray {
        if (pool.isEmpty()) return IntArray(0)
        val leader = if (seed >= 0) seed else pool[rng.nextInt(pool.size)]
        val chosen = ArrayList<Int>(size)
        chosen.add(leader)
        val start = rng.nextInt(pool.size)
        for (i in pool.indices) {
            if (chosen.size >= size) break
            val n = pool[(start + i) % pool.size]
            if (n == leader) continue
            if (!near(grid, leader, n, CLUSTER_RADIUS)) continue
            if (chosen.any { distSq(grid, it, n) < MIN_SPACING_SQ }) continue
            chosen.add(n)
        }
        return chosen.toIntArray()
    }

    private fun near(grid: NavGrid, a: Int, b: Int, radius: Int): Boolean =
        distSq(grid, a, b) <= radius * radius

    private fun distSq(grid: NavGrid, a: Int, b: Int): Int {
        val dx = grid.nodeX[a] - grid.nodeX[b]
        val dy = (grid.nodeY[a] - grid.nodeY[b]) * VERTICAL_WEIGHT
        val dz = grid.nodeZ[a] - grid.nodeZ[b]
        return dx * dx + dy * dy + dz * dz
    }

    /** Un étage d'écart sépare deux hommes bien plus qu'un bloc de couloir. */
    private const val VERTICAL_WEIGHT = 3
    private const val SEED_ATTEMPTS = 40
}
