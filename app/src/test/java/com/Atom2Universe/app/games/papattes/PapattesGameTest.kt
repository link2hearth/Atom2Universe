package com.Atom2Universe.app.games.papattes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Garde-fous de Grandes papattes : parcours, démarches, pouvoirs, chutes, Labo et sauvegarde. */
class PapattesGameTest {
    private val dt = 1f / 60f
    private val gold = PapattesGame.STAR_FINISH or PapattesGame.STAR_GOLD

    /** Achète un œuf, finit l'étape nouvelle [stage] et le fait éclore : une bestiole de plus. */
    private fun PapattesProgress.hatchOne(stage: Int): Critter {
        while (!canBuyEgg) clearStage(1, PapattesGame.STAR_FINISH)
        assertTrue(buyEgg(Random(stage)))
        clearStage(stage, PapattesGame.STAR_FINISH)
        return hatch()!!
    }

    private fun PapattesGame.run(seconds: Float, each: (PapattesGame) -> Unit = {}) {
        var t = 0f
        while (t < seconds) { each(this); update(dt); t += dt }
    }

    @Test
    fun `le meme numero d'etape donne le meme parcours`() {
        for (stage in listOf(1, 7, 13, 25)) {
            val a = PapattesCourse(stage)
            val b = PapattesCourse(stage)
            assertEquals(a.segments.size, b.segments.size)
            assertEquals(a.finishX, b.finishX, 0f)
            for (i in a.segments.indices) assertEquals(a.segments[i].y1, b.segments[i].y1, 0f)
        }
    }

    @Test
    fun `les biomes tournent par six etapes`() {
        assertEquals(Biome.MEADOW, PapattesCourse(1).biome)
        assertEquals(Biome.MARSH, PapattesCourse(7).biome)
        assertEquals(Biome.ICE, PapattesCourse(13).biome)
        assertEquals(Biome.MEADOW, PapattesCourse(19).biome)
    }

    @Test
    fun `aucun trou ne depasse la portee des jambes`() {
        for (stage in 1..36) for (legs in Legs.entries) {
            val course = PapattesCourse(stage, legs)
            // Ce qui porte : les segments (rondins et planches compris) et les bascules.
            val spans = course.segments.map { it.x0 to it.x1 } +
                course.seesaws.map { it.pivotX - it.halfLength to it.pivotX + it.halfLength }
            var reach = spans.minOf { it.first }
            for ((x0, x1) in spans.sortedBy { it.first }) {
                if (x0 > reach) assertTrue("étape $stage, $legs : trou de ${x0 - reach} m",
                    x0 - reach <= maxOf(PapattesCourse.MIN_GAP, legs.reach) + .01f)
                reach = maxOf(reach, x1)
            }
            assertTrue(course.checkpointX in course.startX..course.finishX)
            assertTrue(course.surfaceAt(course.checkpointX).isFinite())
            assertTrue(course.surfaceAt(course.finishX).isFinite())
            assertEquals(PapattesCourse.GOLD_SEEDS, course.goldSeeds.size)
        }
    }

    @Test
    fun `chaque paire de jambes finit la premiere prairie`() {
        for (legs in Legs.entries) {
            val game = PapattesGame(1, Critter(Head.CAT, Body.POMPOM, legs))
            assertTrue("$legs n'arrive pas au drapeau", PapattesRobot(game).run(120f))
        }
    }

    @Test
    fun `tenir le doigt trop longtemps fait tomber`() {
        val game = PapattesGame(1, Critter.STARTER)
        game.press()
        var t = 0f
        while (t < 4f && game.falls == 0) { game.update(dt); t += dt }
        assertTrue("aucune chute en tenant 4 s", game.falls > 0)
    }

    @Test
    fun `tapoter tres vite n'avance presque pas`() {
        val game = PapattesGame(1, Critter.STARTER)
        var phase = 0f
        var down = false
        game.run(20f) {
            phase += dt
            if (down && phase > .15f) { it.release(); down = false; phase = 0f }
            else if (!down && phase > .1f) { it.press(); down = true; phase = 0f }
        }
        val robot = PapattesGame(1, Critter.STARTER)
        PapattesRobot(robot).run(20f)
        assertTrue(game.progressX < robot.progressX / 2)
    }

    @Test
    fun `une chute ramene au dernier drapeau`() {
        val game = PapattesGame(1, Critter.STARTER)
        game.press()
        var t = 0f
        while (t < 8f && PapattesGame.Event.RESPAWN !in game.events) { game.update(dt); t += dt }
        assertTrue(PapattesGame.Event.RESPAWN in game.events)
        assertEquals(PapattesGame.Phase.WALKING, game.phase)
        val c = FloatArray(2)
        game.walker.torsoCenter(c)
        assertEquals(game.course.startX, c[0], .5f)
    }

    @Test
    fun `un bond charge plus longtemps porte plus loin`() {
        fun leap(hold: Float): Float {
            val game = PapattesGame(1, Critter(Head.FROG, Body.POMPOM, Legs.FROG))
            game.run(.5f)
            val start = game.walker.body.x
            game.press()
            game.run(hold)
            game.release()
            var t = 0f
            var airborne = false
            while (t < 3f) {
                game.update(dt); t += dt
                if (game.feetOnGround == 0) airborne = true
                if (airborne && game.feetOnGround > 0) break
            }
            assertTrue("pas de bond après $hold s", airborne)
            return game.walker.body.x - start
        }
        val short = leap(.15f)
        val long = leap(.6f)
        assertTrue("bond court $short m, long $long m", long > short + .5f)
    }

    @Test
    fun `les patins filent sur la glace et rament dans l'herbe`() {
        assertTrue(Legs.SKATES.glideSpeed(Surface.ICE) > 2 * Legs.SKATES.glideSpeed(Surface.GRASS))
        assertTrue(Legs.ROLLERS.glideSpeed(Surface.GRASS) > 2 * Legs.SKATES.glideSpeed(Surface.GRASS))
        // Pousser fait avancer, un appui bref fait sauter.
        val game = PapattesGame(1, Critter(Head.CAT, Body.POMPOM, Legs.ROLLERS))
        game.run(.3f)
        game.press()
        game.run(1.2f)
        game.release()
        assertTrue("vitesse ${game.walker.body.vx}", game.walker.body.vx > 1.2f)
        game.run(.3f)
        game.press(); game.update(dt); game.update(dt); game.release()
        assertTrue(PapattesGame.Event.JUMP in game.events || game.walker.body.vy > 1f)
    }

    @Test
    fun `l'eau ne porte que les corps qui flottent`() {
        val stage = (1..30).first { PapattesCourse(it).waters.isNotEmpty() }
        val course = PapattesCourse(stage)
        val water = course.waters.first()
        val mid = (water.x0 + water.x1) / 2
        val buoy = PapattesGame(stage, Critter(Head.CAT, Body.BUOY, Legs.STILTS))
        val plain = PapattesGame(stage, Critter.STARTER)
        assertEquals(water.level + PapattesCourse.FLOAT_LIFT, buoy.groundAt(mid), 1e-4f)
        if (course.seesaws.none { mid > it.pivotX - it.halfLength && mid < it.pivotX + it.halfLength } &&
            course.segments.none { it.floating && mid >= it.x0 && mid <= it.x1 }) {
            assertFalse(plain.groundAt(mid).isFinite())
        }
    }

    @Test
    fun `une planche cede peu apres qu'on a pose le pied dessus`() {
        val stage = (1..60).first { s -> PapattesCourse(s).segments.any { it.floater == Floater.PLANK } }
        val game = PapattesGame(stage, Critter(Head.CAT, Body.POMPOM, Legs.OSTRICH))
        val plank = game.course.segments.indexOfFirst { it.floater == Floater.PLANK }
        val s = game.course.segments[plank]
        val mid = (s.x0 + s.x1) / 2
        assertTrue(game.groundAt(mid).isFinite())
        // Le robot traverse la passerelle : les planches touchées finissent par céder, et ne portent plus.
        val robot = PapattesRobot(game)
        var t = 0f
        while (t < 120f && game.plankFall.none { it >= 0f }) { robot.tick(dt); game.update(dt); t += dt }
        val fallen = game.plankFall.indexOfFirst { it >= 0f }
        assertTrue("aucune planche n'a cédé", fallen >= 0)
        val f = game.course.segments[fallen]
        assertTrue(game.plankTimer[fallen] >= PapattesGame.CRUMBLE_DELAY)
        assertFalse(game.groundAt((f.x0 + f.x1) / 2) == f.y0)
    }

    @Test
    fun `l'oeuf mystere s'achete, couve jusqu'a une etape nouvelle puis eclot`() {
        val p = PapattesProgress()
        assertFalse("pas assez de graines", p.buyEgg(Random(1)))
        p.clearStage(1, PapattesGame.STAR_FINISH)
        p.clearStage(2, PapattesGame.STAR_FINISH)
        p.clearStage(3, PapattesGame.STAR_FINISH)
        val seeds = p.seeds
        assertTrue(p.buyEgg(Random(1)))
        assertEquals(seeds - PapattesProgress.EGG_COST, p.seeds)
        assertFalse("un seul œuf à la fois", p.buyEgg(Random(2)))
        // Rejouer une étape déjà finie ne le réchauffe pas.
        assertFalse(p.clearStage(2, PapattesGame.STAR_FINISH).eggReady)
        assertNull(p.hatch())
        // Une étape nouvelle, si.
        assertTrue(p.clearStage(4, PapattesGame.STAR_FINISH).eggReady)
        val inside = p.egg!!
        val back = PapattesProgress.decode(p.encode())
        assertEquals(inside, back.egg)
        assertTrue(back.eggReady)
        assertEquals(inside, p.hatch())
        assertEquals(inside, p.pen.last())
        assertNull(p.egg)
        assertTrue(p.isDiscovered(Parts.index(inside.head)))
    }

    @Test
    fun `les graines d'or rapportent des graines`() {
        val p = PapattesProgress()
        val reward = p.clearStage(1, PapattesGame.STAR_FINISH, gold = 2)
        assertEquals(PapattesProgress.FIRST_SEEDS + PapattesProgress.SEEDS_PER_STAGE + 2 * PapattesProgress.GOLD_VALUE, reward.seeds)
    }

    @Test
    fun `croiser coute des graines et melange les pieces des parents`() {
        val p = PapattesProgress()
        p.hatchOne(2)
        p.hatchOne(3)
        while (p.seeds < PapattesProgress.BREED_COST) p.clearStage(1, PapattesGame.STAR_FINISH)
        assertTrue(p.pen.size >= 3)
        val seeds = p.seeds
        val a = p.pen[0]
        val b = p.pen[1]
        val child = p.breed(0, 1, Random(3))
        assertNotNull(child)
        child!!
        assertEquals(seeds - PapattesProgress.BREED_COST, p.seeds)
        assertTrue(child.legs == a.legs || child.legs == b.legs || !Legs.entries.all { p.isDiscovered(Parts.index(it)) })
        assertNull("on ne croise pas une bestiole avec elle-même", p.breed(1, 1, Random(1)))
    }

    @Test
    fun `la sauvegarde rend exactement la meme progression`() {
        val p = PapattesProgress()
        for (s in 1..6) p.clearStage(s, if (s % 2 == 0) gold or PapattesGame.STAR_CLEAN else PapattesGame.STAR_FINISH)
        p.hatchOne(7)
        p.hatchOne(8)
        p.breed(0, 2, Random(9))
        p.select(1)
        val back = PapattesProgress.decode(p.encode())
        assertEquals(p.encode(), back.encode())
        assertEquals(p.activeCritter, back.activeCritter)
        assertEquals(p.discoveredCount, back.discoveredCount)
        assertEquals(p.starsOf(4), back.starsOf(4))
        // Une sauvegarde illisible repart de zéro au lieu de planter.
        assertEquals(Critter.STARTER, PapattesProgress.decode("n'importe quoi").activeCritter)
    }

    @Test
    fun `les codes de bestioles couvrent toute la collection`() {
        for (code in 0 until Critter.COUNT) assertEquals(code, Critter.fromCode(code)!!.code)
        assertNull(Critter.fromCode(Critter.COUNT))
    }

    @Test
    fun `on ne relache pas sa derniere bestiole`() {
        val p = PapattesProgress()
        assertFalse(p.release(0))
        p.hatchOne(2)
        p.select(1)
        val walking = p.activeCritter
        assertTrue(p.release(0))
        assertEquals(walking, p.activeCritter)
    }
}
