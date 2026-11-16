package com.Atom2Universe.app.games.mercerie

import org.junit.Test
import java.io.File

/**
 * Banc de mesure (exclu par défaut, voir `bancsDeMesure`) : le robot joue chaque niveau des dizaines
 * de fois et propose les coups et les scores des étoiles. Sa sortie se recolle dans
 * `MercerieLevels.TUNING` : build/outputs/mercerie/balance.txt.
 *
 * On laisse le robot réussir environ trois parties sur quatre.
 */
class MercerieBalanceTest {
    private fun pct(sorted: List<Int>, p: Double) = if (sorted.isEmpty()) 0 else sorted[((sorted.size - 1) * p).toInt()]
    private fun round(v: Int) = (v + 50) / 100 * 100

    @Test
    fun tune() {
        val games = 40
        val table = StringBuilder()
        val notes = StringBuilder()
        for (l in MercerieLevels.all) {
            var moves = l.moves
            if (!l.goal.onlyScore) {
                // Coups qu'il faut au robot pour finir, avec une réserve large.
                val wide = l.with(moves = 60)
                val used = (0 until games).map { MercerieRobot.play(wide, 1000L * l.number + it).let { r -> if (r.won) r.used else 60 } }.sorted()
                moves = (pct(used, 0.75) + 1).coerceIn(10, 50)
                notes.append("niveau ${l.number} : coups médians ${pct(used, 0.5)}, 75 % ${pct(used, 0.75)}, max ${used.last()}\n")
            }
            val real = l.with(moves = moves, stars = intArrayOf(0, 1, 2))
            val results = (0 until games).map { MercerieRobot.play(real, 5000L * l.number + it) }
            val scores = (if (l.goal.onlyScore) results else results.filter { it.won }).map { it.score }.sorted()
            val wins = results.count { it.won }
            val s1 = round(if (l.goal.onlyScore) pct(scores, 0.2) else pct(scores, 0.0) / 2).coerceAtLeast(500)
            val s2 = maxOf(round(pct(scores, if (l.goal.onlyScore) 0.55 else 0.35)), s1 + 500)
            val s3 = maxOf(round(pct(scores, if (l.goal.onlyScore) 0.85 else 0.7)), s2 + 500)
            table.append("        intArrayOf($moves, $s1, $s2, $s3),\n")
            notes.append("niveau ${l.number} : $moves coups, gagné $wins/$games, scores ${scores.firstOrNull()} / ${pct(scores, 0.5)} / ${scores.lastOrNull()}\n")
        }
        val out = File("build/outputs/mercerie/balance.txt")
        out.parentFile.mkdirs()
        out.writeText(table.toString() + "\n" + notes)
        println(table)
        println(notes)
    }
}
