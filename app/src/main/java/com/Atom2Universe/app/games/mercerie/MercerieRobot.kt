package com.Atom2Universe.app.games.mercerie

import kotlin.random.Random

/**
 * Un joueur automatique : il regarde un coup à l'avance et prend celui qui avance le plus
 * l'objectif, comme un joueur attentif mais sans plan. Il sert aux tests (chaque niveau doit être
 * faisable) et au réglage des étages sans fin ([MercerieGenerator.tune]).
 */
object MercerieRobot {
    class Result(val used: Int, val won: Boolean, val score: Int, val stars: Int)

    fun settle(b: MercerieBoard) { var n = 0; while (b.advance() != null && n++ < 500) { /* suite */ } }

    private fun value(before: MercerieBoard, after: MercerieBoard): Double {
        val g = before.level.goal
        var v = (after.score - before.score) / 100.0
        if (g.patches) v += 40.0 * (before.patchesLeft - after.patchesLeft) + 8.0 * (before.knotsLeft - after.knotsLeft)
        for (c in 0 until MercerieBoard.COLORS) if (g.collect[c] > 0)
            v += 15.0 * (minOf(after.collected[c], g.collect[c]) - minOf(before.collected[c], g.collect[c]))
        if (g.thimbles > 0) v += 300.0 * (after.thimblesDone - before.thimblesDone) +
            25.0 * (after.thimbleDepth - before.thimbleDepth).coerceAtLeast(0)
        return v
    }

    /** Joue une partie entière, bonus de fin compris. */
    fun play(level: MercerieLevel, seed: Long): Result {
        val rnd = Random(seed)
        val b = MercerieBoard(level, seed)
        while (!b.finished) {
            val moves = b.allMoves()
            if (moves.isEmpty()) { settle(b); if (b.allMoves().isEmpty()) break else continue }
            var best = moves[0]
            var bestValue = -1e9
            for (m in moves) {
                val f = b.fork(rnd.nextLong())
                f.swap(m[0], m[1]); settle(f)
                val v = value(b, f) + rnd.nextDouble() * 0.5
                if (v > bestValue) { bestValue = v; best = m }
            }
            b.swap(best[0], best[1]); settle(b)
        }
        val used = level.moves - b.movesLeft
        val won = b.goalMet
        if (won) { b.startBonus(); settle(b) }
        return Result(used, won, b.score, b.stars)
    }
}
