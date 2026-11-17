package com.Atom2Universe.app.games.mercerie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Garde-fous du tutoriel : chaque tableau commence sans alignement, chaque coup prévu est permis
 * au moment où il est demandé, et il montre bien ce que dit sa consigne (le bonus qui naît, la
 * combinaison, les effets). Le déroulé de chaque tableau est écrit dans
 * `build/outputs/mercerie/tutorial.txt`.
 */
class MercerieTutorialTest {

    private fun settle(b: MercerieBoard): List<Step> {
        val steps = ArrayList<Step>()
        while (true) {
            steps += b.advance() ?: break
            assertTrue("résolution sans fin", steps.size < 400)
        }
        return steps
    }

    /** Le plateau en texte : couleur, ou `*` arc-en-ciel, `h` `v` ciseaux, `b` pompon, `w` papillon, `k` nœud. */
    private fun picture(b: MercerieBoard): String = buildString {
        for (r in 0 until b.rows) {
            append("    ")
            for (c in 0 until b.cols) {
                val i = b.idx(r, c)
                val p = b.piece[i]
                append(
                    when {
                        b.knot[i] > 0 -> 'k'
                        p == null -> '·'
                        p.kind == Kind.RAINBOW -> '*'
                        p.kind == Kind.LINE_H -> 'h'
                        p.kind == Kind.LINE_V -> 'v'
                        p.kind == Kind.BOMB -> 'b'
                        p.kind == Kind.BUTTERFLY -> 'w'
                        else -> '0' + p.color
                    }
                )
            }
            append('\n')
        }
    }

    @Test
    fun everyLessonShowsWhatItSays() {
        val report = StringBuilder()
        assertEquals(12, MercerieLessons.ALL.size)
        assertEquals(10, MercerieLessons.forMode(true).size)
        for (easy in listOf(false, true)) for (lesson in MercerieLessons.forMode(easy)) {
            val tag = "tableau ${MercerieLessons.ALL.indexOf(lesson) + 1}${if (easy) " facile" else ""}"
            assertTrue(tag, lesson.grid.all { it.length == lesson.grid[0].length })
            val b = lesson.board(easy)
            assertFalse("$tag\n${picture(b)}", b.hasMatches())
            report.append(tag).append('\n')
            for ((k, m) in lesson.moves.withIndex()) {
                val cells = lesson.cells(k)
                val move = "$tag, coup ${k + 1}"
                report.append("  coup ${k + 1} : (${m.r1},${m.c1}) ↔ (${m.r2},${m.c2})\n").append(picture(b))
                assertTrue("$move non permis\n${picture(b)}", b.canSwap(cells[0], cells[1]))
                assertTrue(move, b.swap(cells[0], cells[1]))
                val clears = settle(b).filterIsInstance<ClearStep>()
                assertTrue(move, clears.isNotEmpty())
                val first = clears.first()
                m.makes?.let { kind -> assertTrue("$move : ${first.born.map { it.piece.kind }}", first.born.any { it.piece.kind == kind }) }
                m.combo?.let { combo -> assertEquals(move, combo, first.combo) }
                val fx = clears.flatMap { it.fx }.map { it.type }.toSet()
                for (t in m.shows) assertTrue("$move : $t absent de $fx", t in fx)
                report.append("  après : ${clears.size} temps, effets $fx\n")
            }
            // À toi : la fin de la démonstration laisse de quoi refaire la même chose, sans autre bonus qui traîne.
            if (lesson.practice != null) assertTrue("$tag : un bonus traîne\n${picture(b)}", b.piece.all { it == null || it.kind == Kind.NORMAL })
            val placed = lesson.placeEncore(b)
            assertEquals(tag, lesson.encore.size, placed.size)
            report.append("  à toi :\n").append(picture(b))
            if (lesson.hasPractice) {
                val p = lesson.practiceMove(b)
                assertNotNull("$tag : rien à refaire\n${picture(b)}", p)
                assertTrue(tag, lesson.practiced(b, p!![0], p[1]))
                val combo = lesson.moves.last().combo
                assertTrue(tag, b.swap(p[0], p[1]))
                val first = settle(b).filterIsInstance<ClearStep>().first()
                lesson.practice?.let { kind ->
                    val born = first.born.map { if (it.piece.kind == Kind.LINE_V) Kind.LINE_H else it.piece.kind }
                    assertTrue("$tag : $born", kind in born)
                }
                if (lesson.encore.isNotEmpty()) assertEquals(tag, combo, first.combo)
            }
            report.append('\n')
        }
        File("build/outputs/mercerie/tutorial.txt").apply { parentFile?.mkdirs(); writeText(report.toString()) }
    }

    /** Le coup conseillé montre d'abord une combinaison, puis un coup qui fabrique un bonus. */
    @Test
    fun hintPrefersBonuses() {
        // Ciseaux + ciseaux côte à côte : c'est le coup conseillé.
        val cross = MercerieLessons.ALL[4].board(false)
        val m = cross.bestMove()!!
        assertEquals(setOf(cross.idx(5, 3), cross.idx(5, 4)), m.toSet())
        // Le tableau des 5 cœurs : le conseil fabrique l'arc-en-ciel, pas un simple alignement.
        val rainbow = MercerieLessons.ALL[2].board(false)
        assertEquals(setOf(rainbow.idx(4, 3), rainbow.idx(5, 3)), rainbow.bestMove()!!.toSet())
    }
}
