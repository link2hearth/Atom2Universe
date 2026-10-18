package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import com.Atom2Universe.app.games.golf.classic.RandomCourseStore
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.max

class RandomGolfGeneratorTest {
    @Test fun sameSpecGivesSameHolesAndAnotherSeedDoesNot() {
        val spec = RandomGolfSpec(4242, 9, null)
        val a = RandomGolfGenerator.holes(spec); val b = RandomGolfGenerator.holes(spec)
        assertEquals(9, a.size)
        assertEquals(a.map { it.par }, b.map { it.par })
        assertEquals(a.map { it.route }, b.map { it.route })
        assertEquals(a.map { h -> h.hazards.map { "${it.x},${it.z},${it.rx},${it.rz},${it.lie}" } },
            b.map { h -> h.hazards.map { "${it.x},${it.z},${it.rx},${it.rz},${it.lie}" } })
        assertNotEquals(a.map { it.route }, RandomGolfGenerator.holes(spec.copy(seed = 4243)).map { it.route })
    }

    @Test fun specSurvivesItsId() {
        for (theme in listOf<RandomGolfTheme?>(null) + RandomGolfTheme.entries) {
            val spec = RandomGolfSpec(123456, 5, theme)
            assertEquals(spec, RandomGolfSpec.parse(spec.id))
        }
        assertNull(RandomGolfSpec.parse("gardens"))
        assertNull(RandomGolfSpec.parse("rnd-12-4-mix"))
    }

    @Test fun everyHoleOfEveryThemeIsPlayable() {
        val failures = ArrayList<String>()
        val themes = listOf<RandomGolfTheme?>(null) + RandomGolfTheme.entries
        val pars = IntArray(6)
        for (seed in 1L..40L) for (theme in themes) {
            val spec = RandomGolfSpec(seed * 7919, 18, theme)
            for (hole in RandomGolfGenerator.holes(spec)) {
                pars[hole.par]++
                RandomGolfGenerator.problem(hole)?.let { failures += "${spec.id} #${hole.number} par ${hole.par}: $it" }
                if (hole.hazards.isEmpty() && hole.islands.isEmpty()) failures += "${spec.id} #${hole.number}: fell back to a bare hole"
            }
        }
        assertTrue(failures.take(25).joinToString("\n") + "\n(${failures.size})", failures.isEmpty())
        assertTrue("pars ${pars.toList()}", pars[3] > 0 && pars[4] > pars[3] && pars[5] > 0)
    }

    @Test fun mixedThemeUsesSeveralThemes() {
        val themes = RandomGolfGenerator.holes(RandomGolfSpec(99, 18, null)).indices
            .map { RandomGolfGenerator.themeOf(RandomGolfSpec(99, 18, null), it + 1) }.toSet()
        assertTrue(themes.size >= 3)
    }

    /** The caddie aims and picks the club; the robot only finds the power that reaches the target. */
    private fun play(hole: ClassicHole): Int? {
        val game = ClassicGame(hole)
        game.windX = 0f; game.windZ = 0f
        repeat(16) {
            if (game.state == GolfState.HOLED) return game.strokes
            val target = hole.recommendedLanding(game.ball)
            val distance = hypot(target.x - game.ball.x, target.z - game.ball.z)
            val club = game.club
            val reach = if (club == GolfClub.PUTTER) hole.puttRange else club.carry
            var lo = .02f; var hi = 1f
            if (distance >= reach) lo = 1f else repeat(16) {
                val m = (lo + hi) / 2f
                if (game.preview(m).carry < distance) lo = m else hi = m
            }
            if (!game.hit(max(lo, .02f))) return null
            repeat(60 * 40) {
                if (game.state == GolfState.READY || game.state == GolfState.HOLED) return@repeat
                game.update(1f / 60f)
            }
        }
        return if (game.state == GolfState.HOLED) game.strokes else null
    }

    @Test fun theGamesOwnCaddieCanFinishGeneratedHoles() {
        var land = 0; var landHoled = 0; var islands = 0; var islandHoled = 0
        val stuck = ArrayList<String>()
        for (seed in 1L..6L) for (theme in listOf<RandomGolfTheme?>(null) + RandomGolfTheme.entries) {
            val spec = RandomGolfSpec(seed * 31, 18, theme)
            for (hole in RandomGolfGenerator.holes(spec)) {
                hole.trees.size // the scenery builds on the same description
                for (x in -150..150 step 30) for (z in -20..(hole.length.toInt() + 50) step 30) {
                    hole.heightAt(x.toFloat(), z.toFloat()); hole.lieAt(x.toFloat(), z.toFloat())
                }
                val holed = play(hole) != null
                if (hole.islands.isEmpty()) { land++; if (holed) landHoled++ else stuck += "${spec.id} #${hole.number} par ${hole.par}" }
                else { islands++; if (holed) islandHoled++ }
            }
        }
        assertTrue("land $landHoled/$land, stuck: ${stuck.take(12)}", landHoled == land)
        // The blunt robot drowns on islands: the hand-drawn archipelago course is finished 8 times in 18.
        assertTrue("islands $islandHoled/$islands", islandHoled * 18 >= islands * 8)
    }

    @Test fun namesComeFromTheSeedAndSpreadOverTheLists() {
        assertEquals(RandomGolfNames.pick(5621786414, 40, 40), RandomGolfNames.pick(5621786414, 40, 40))
        val names = (1L..400L).map { RandomGolfNames.pick(it * 7919, 40, 40) }
        assertTrue(names.all { it.first in 0..39 && it.second in 0..39 })
        assertTrue(names.map { it.first }.toSet().size >= 30 && names.map { it.second }.toSet().size >= 30)
        assertTrue(names.toSet().size >= 300)
    }

    @Test fun favouritesComeFirstAndEachGroupKeepsTheNewestFirst() {
        val specs = (1L..5L).map { RandomGolfSpec(it, 9, null) } // newest first
        val order = RandomCourseStore.order(specs, setOf(specs[3].id, specs[1].id)).map { it.seed }
        assertEquals(listOf(2L, 4L, 1L, 3L, 5L), order)
    }
}
