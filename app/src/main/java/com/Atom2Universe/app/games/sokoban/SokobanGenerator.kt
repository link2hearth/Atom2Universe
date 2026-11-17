package com.Atom2Universe.app.games.sokoban

import kotlin.random.Random

/**
 * Le niveau aléatoire, fabriqué sur la tablette : une pièce tirée au hasard puis retouchée
 * pendant [SokobanDifficulty.budgetMs] (recuit de [SokobanForge]), et une finition qui mure le
 * sol inutile. Chaque plateau essayé est exploré en entier : le niveau est toujours soluble et
 * le défi annoncé est la vraie solution optimale en poussées.
 */
object SokobanGenerator {

    private const val ITERATIONS = 4000

    fun generate(
        difficulty: SokobanDifficulty,
        random: Random = Random.Default,
        checkCancellation: () -> Unit = {}
    ): SokobanPuzzle? {
        val t0 = System.nanoTime()
        val deadline = t0 + difficulty.budgetMs * 1_000_000
        val spec = difficulty.spec(random)
        val seed = SokobanForge.seed(spec, random, deadline, checkCancellation) ?: return null
        val climbEnd = t0 + (deadline - t0) * 85 / 100
        val climbed = SokobanForge.climb(seed, spec, random, ITERATIONS, climbEnd, checkCancellation)
        val done = SokobanForge.polish(climbed, spec, random, deadline, checkCancellation)
        return SokobanForge.toPuzzle(done, random)
    }
}
