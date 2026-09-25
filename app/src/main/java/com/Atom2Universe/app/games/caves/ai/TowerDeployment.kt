package com.Atom2Universe.app.games.caves.ai

import com.Atom2Universe.app.games.caves.world.MapPoint
import com.Atom2Universe.app.games.caves.world.OfficeTowerMap
import kotlin.random.Random

/**
 * Garnison de la tour, créée une fois par manche. Aucun renfort, et **aucun tirage de pièce** :
 * chaque escouade occupe un poste choisi à la main dans [OfficeTowerMap.POSTS], le long du
 * parcours. Le chef de file se tient sur le poste, ses hommes à quelques pas autour ; seul ce
 * placement autour du poste change d'une manche à l'autre.
 */
internal class TowerDeployment(private val grid: NavGrid, spawn: MapPoint) {
    private val pools = ArrayList<IntArray>(OfficeTowerMap.POSTS.size)

    init {
        // Seules comptent les cases que le joueur peut atteindre depuis le sas.
        val reachable = BooleanArray(grid.nodeCount)
        val queue = IntArray(grid.nodeCount)
        var read = 0; var write = 0
        val start = grid.nodeAt(spawn.x, spawn.y, spawn.z)
        if (start >= 0) { reachable[start] = true; queue[write++] = start }
        while (read < write) {
            val n = queue[read++]
            for (e in grid.edgeStart[n] until grid.edgeStart[n + 1]) {
                val next = grid.edgeTarget[e]
                if (!reachable[next]) { reachable[next] = true; queue[write++] = next }
            }
        }
        for (post in OfficeTowerMap.POSTS) {
            // Le chef d'abord (la case la plus proche du poste), puis les voisines au même niveau.
            val around = ArrayList<Int>()
            for (n in 0 until grid.nodeCount) {
                if (!reachable[n] || grid.nodeY[n] != post.y) continue
                val dx = grid.nodeX[n] - post.x; val dz = grid.nodeZ[n] - post.z
                if (dx * dx + dz * dz <= POST_RADIUS * POST_RADIUS) around.add(n)
            }
            around.sortBy {
                val dx = grid.nodeX[it] - post.x; val dz = grid.nodeZ[it] - post.z
                dx * dx + dz * dz
            }
            pools.add(around.toIntArray())
        }
    }

    /** Une escouade par poste, dans l'ordre de [OfficeTowerMap.POSTS]. Un poste introuvable est sauté. */
    fun chooseSquads(rng: Random): List<IntArray> {
        val result = ArrayList<IntArray>(pools.size)
        for ((i, pool) in pools.withIndex()) {
            if (pool.isEmpty()) continue
            val group = SquadSpawn.grab(grid, pool.asList(), OfficeTowerMap.POSTS[i].size, rng, seed = pool[0])
            if (group.isNotEmpty()) result.add(group)
        }
        return result
    }

    /** Cases réellement disponibles autour de chaque poste (pour les tests). */
    fun poolSizes(): List<Int> = pools.map { it.size }

    private companion object {
        /** Rayon autour du poste où se placent les hommes de l'escouade. */
        const val POST_RADIUS = 4
    }
}
