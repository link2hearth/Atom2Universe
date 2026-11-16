package com.Atom2Universe.app.games.mercerie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Garde-fous des étages sans fin : toujours jouables, toujours les mêmes, variés, et réglés par le robot. */
class MercerieGeneratorTest {
    private val first = MercerieLevels.HANDMADE + 1

    private fun kind(l: MercerieLevel): String {
        val g = l.goal
        val collect = g.collect.any { it > 0 }
        return when {
            g.onlyScore -> "score"
            g.patches && (collect || g.thimbles > 0) -> "mixte"
            g.patches -> "écussons"
            g.thimbles > 0 -> "dés"
            else -> "récolte"
        }
    }

    @Test
    fun floorsAreSoundAndRepeatable() {
        for (n in first until first + 150) for (variant in 0..3) {
            val tag = "étage $n variante $variant"
            val l = MercerieGenerator.build(n, variant)
            assertEquals(tag, n, l.number)
            assertTrue(tag, l.cols in 5..9 && l.rows in 5..9)
            assertTrue(tag, l.map.all { it.length == l.cols })
            assertTrue(tag, l.colors in 5..6)
            for (c in l.colors until MercerieBoard.COLORS) assertEquals(tag, 0, l.goal.collect[c])
            val again = MercerieGenerator.build(n, variant)
            assertEquals(tag, l.map, again.map)
            assertEquals(tag, l.goal.collect.toList(), again.goal.collect.toList())
            assertEquals(tag, l.goal.thimbles, again.goal.thimbles)
            val b = MercerieBoard(l, 1L)
            assertEquals(tag, l.goal.patches, b.patchesLeft > 0)
            assertFalse(tag, b.hasMatches())
            assertTrue(tag, b.hasMove())
            assertFalse(tag, b.goalMet)
            // Assez de pièces libres pour jouer : les obstacles ne mangent pas le plateau.
            val free = b.piece.count { it != null && !it.pinned }
            assertTrue("$tag : $free pièces libres", free >= 30)
        }
        assertTrue(MercerieGenerator.build(first + 3, 3).goal.onlyScore)
    }

    @Test
    fun everyFiveFloorsOfferTheFiveGoals() {
        for (block in 0 until 20) {
            val kinds = (0 until 5).map { kind(MercerieGenerator.build(first + block * 5 + it)) }.toSet()
            assertEquals("étages ${first + block * 5} et suivants : $kinds", 5, kinds.size)
        }
        // Et jamais deux fois la même forme de plateau de suite.
        for (n in first until first + 100) {
            assertFalse("étages $n et ${n + 1}", MercerieGenerator.build(n).map.map { it.replace(Regex("[^ ]"), ".") } ==
                MercerieGenerator.build(n + 1).map.map { it.replace(Regex("[^ ]"), ".") })
        }
    }

    /** Le robot règle quelques étages : coups raisonnables, étoiles croissantes, et il les regagne ensuite. */
    @Test
    fun robotTunesFloors() {
        val report = StringBuilder()
        val kinds = HashMap<String, Int>()
        var total = 0
        for (n in first until first + 30) {
            val (variant, l) = MercerieGenerator.make(n)
            kinds[kind(l)] = (kinds[kind(l)] ?: 0) + 1
            assertTrue("étage $n : ${l.moves} coups", l.moves in 12..48)
            assertTrue("étage $n", l.stars[0] < l.stars[1] && l.stars[1] < l.stars[2])
            val games = (0 until 4).map { MercerieRobot.play(l, 40_000L * n + it) }
            val wins = games.count { it.won }
            total += wins
            report.append("étage $n : ${kind(l)}, ${l.cols}×${l.rows}, ${l.colors} couleurs, variante $variant, ${l.moves} coups, ")
                .append("étoiles ${l.stars.toList()}, robot $wins/4, étoiles obtenues ${games.map { it.stars }}\n")
            for (row in l.map) report.append("    ").append(row.replace(' ', '·')).append('\n')
            assertTrue("étage $n : le robot ne le gagne jamais\n$report", wins >= 1)
        }
        report.append("sortes : $kinds, robot $total/120").append('\n')
        File("build/outputs/mercerie/floors.txt").apply { parentFile?.mkdirs(); writeText(report.toString()) }
        // Le réglage ne doit pas tout ramener à des étages « au score » : chaque sorte reste présente.
        for (k in listOf("score", "récolte", "écussons", "dés", "mixte")) assertTrue("$k : $kinds", (kinds[k] ?: 0) >= 3)
        // Ni trop dur ni donné : le robot gagne entre six et neuf parties sur dix.
        assertTrue("robot $total/120", total in 72..112)
    }
}
