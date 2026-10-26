package com.Atom2Universe.app.games.mochi

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class MochiGameTest {
    /** Le solveur propose un geste ; le vrai jeu doit ensuite réussir toutes ses collisions. */
    private fun safeShot(g: MochiGame, target: MochiGame.Flower): MochiGame.Point? {
        val from = requireNotNull(g.attachedFlower)
        val shot = MochiGarden.solveLaunch(from, target, g.walls, g.time, g.flowers, catchRadius = g.catchRadius) ?: return null
        return shot.velocity.takeIf { contacts(g, it, shot.duration, g.time, 1f).isEmpty() }
    }

    /** Vérifie séparément les dangers mobiles, sur tous les sous-segments de chaque rebond. */
    private fun contacts(g: MochiGame, velocity: MochiGame.Point, duration: Float,
        startTime: Float, margin: Float): Set<MochiGame.Hazard> {
        var state = MochiFlight.State(g.x, g.y, velocity.x, velocity.y)
        val hits = mutableSetOf<MochiGame.Hazard>()
        for (step in 0..ceil(duration / MochiGame.STEP).toInt()) {
            val flight = MochiFlight.step(state, MochiGame.STEP, g.walls, minBounce = MochiGame.MIN_BOUNCE)
            for (segment in flight.segments) {
                val start = startTime + (step + segment.startFraction) * MochiGame.STEP
                val end = startTime + (step + segment.endFraction) * MochiGame.STEP
                g.hazards.filterTo(hits) { hazard ->
                    segmentDistance(0f, 0f, segment.ax - hazard.at(start), segment.ay - hazard.y,
                        segment.bx - hazard.at(end), segment.by - hazard.y) < hazard.radius + MochiGame.RADIUS + margin
                }
            }
            state = flight.state
        }
        return hits
    }

    /** update() plafonne chaque appel à 0,1 s : simuler une vraie attente demande plusieurs appels. */
    private fun wait(g: MochiGame, seconds: Float) {
        var left = seconds
        while (left > 0f) { g.update(min(.1f, left)); left -= .1f }
    }

    private fun segmentDistance(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val t = (((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy).coerceAtLeast(.00001f)).coerceIn(0f, 1f)
        return hypot(x - ax - t * dx, y - ay - t * dy)
    }

    private fun advanceToNext(g: MochiGame) {
        advanceTo(g, g.flowers.first { it.id == g.attachedId + 1 })
    }

    private fun advanceTo(g: MochiGame, next: MochiGame.Flower) {
        val from = g.flowers.first { it.id == g.attachedId }
        var v = safeShot(g, next)
        var waits = 0
        // Les fleurs fanent : une fenêtre doit s'ouvrir bien avant que la prise ne cède.
        while (v == null && waits++ < 25) {
            g.update(.1f)
            v = safeShot(g, next)
        }
        assertNotNull("Fenêtre praticable manquante : seed=${g.seed}, ${from.id} -> ${next.id}, t=${g.time}", v)
        val shot = requireNotNull(v)
        val power = MochiGame.DRAG_POWER * g.launchPower
        assertTrue(g.launch(-shot.x / power, shot.y / power))
        var steps = 0
        while (g.phase == MochiGame.Phase.FLYING && steps++ < 180) g.update(1f / 60f)
        assertEquals("seed=${g.seed}, fleur=${next.id}, fin=${g.end}", MochiGame.Phase.ATTACHED, g.phase)
        assertEquals(next.id, g.attachedId)
    }

    @Test fun generatedDetoursHaveAPlayableEntranceAndExit() {
        var branchesTaken = 0
        for (seed in listOf(0L, 13L, 42L)) {
            val g = MochiGame(seed)
            repeat(18) {
                val next = g.flowers.first { it.id == g.attachedId + 1 }
                g.flowers.firstOrNull { it.id == -next.id }?.let { branch ->
                    advanceTo(g, branch)
                    assertTrue(g.detours > 0)
                    branchesTaken++
                }
                advanceTo(g, next)
            }
        }
        assertTrue("Le garde-fou doit réellement parcourir les branches générées", branchesTaken >= 3)
    }

    @Test fun aGeneratedBeeMakesTheSameJumpUnsafeThenSafeAfterWaiting() {
        var beesSeen = false
        for (seed in listOf(10L, 3L, 27L)) {
            val g = MochiGame(seed)
            repeat(40) {
                val from = requireNotNull(g.attachedFlower)
                val next = g.flowers.first { it.id == from.id + 1 }
                beesSeen = beesSeen || g.hazards.any { it.kind == MochiGame.Danger.BEE }
                if (from.kind != MochiGame.FlowerKind.DRIFT && next.kind != MochiGame.FlowerKind.DRIFT) {
                    val shot = requireNotNull(MochiGarden.solveLaunch(from, next, g.walls, g.time, g.flowers, catchRadius = g.catchRadius))
                    val velocity = shot.velocity
                    fun contacts(startTime: Float, safetyMargin: Float) =
                        contacts(g, velocity, shot.duration, startTime, safetyMargin)
                    val badDelay = (0..20).firstOrNull { delay ->
                        val hits = contacts(g.time + delay * .1f, -2f)
                        hits.isNotEmpty() && hits.all { it.kind == MochiGame.Danger.BEE }
                    }
                    val hasWindow = (0..20).any { contacts(g.time + it * .1f, 2f).isEmpty() }
                    if (badDelay != null && hasWindow) {
                        g.gainBubble()
                        val peachesBefore = g.peaches
                        repeat(badDelay) { g.update(.1f) }
                        val power = MochiGame.DRAG_POWER * g.launchPower
                        assertTrue(g.launch(-velocity.x / power, velocity.y / power))
                        var ticks = 0
                        while (g.phase == MochiGame.Phase.FLYING && ticks++ < 240) g.update(MochiGame.STEP)
                        // La bulle est consommée ; des pêches déjà mûres peuvent aussitôt en faire une autre.
                        assertTrue("L'abeille doit réellement consommer la bulle",
                            g.bubbles == 0 || (g.peaches == 0 && peachesBefore >= MochiGame.PEACHES_PER_BUBBLE))
                        assertEquals("Mochi flotte dans sa bulle ou tient une fleur voisine", MochiGame.Phase.ATTACHED, g.phase)
                        assertTrue(g.inBubble || g.attachedFlower != null)
                        return
                    }
                }
                advanceToNext(g)
            }
        }
        assertTrue("Les sections échantillonnées doivent générer des abeilles", beesSeen)
        fail("Aucune abeille générée ne change le risque du saut avec son cycle")
    }

    @Test fun generatedRoutesRemainPlayableAndMemoryBounded() {
        // Garde-fou court : collisions réelles du moteur sur plusieurs jardins et phases d'abeilles.
        for (seed in listOf(0L, 13L, 42L, 891L)) {
            val g = MochiGame(seed)
            val kinds = mutableSetOf<MochiGame.FlowerKind>()
            val gaps = mutableSetOf<Int>()
            val rests = mutableListOf<Int>()
            repeat(36) {
                val previousY = g.y
                advanceToNext(g)
                kinds += requireNotNull(g.attachedFlower).kind
                if (g.attachedFlower?.kind == MochiGame.FlowerKind.SANCTUARY) rests += g.attachedId
                gaps += ((g.y - previousY) / 20).toInt()
                assertTrue(g.flowers.size < 22)
                assertTrue(g.hazards.size < 16)
                assertTrue(g.sweets.size < 22)
                assertTrue("Les murs anciens doivent être libérés", g.walls.size < 40)
            }
            assertEquals(36, g.caught)
            assertTrue(kinds.containsAll(listOf(MochiGame.FlowerKind.DRIFT, MochiGame.FlowerKind.SPRING,
                MochiGame.FlowerKind.SANCTUARY)))
            assertTrue("Les hauteurs de saut doivent poser différents problèmes", gaps.size >= 3)
            assertEquals(listOf(10, 22, 36), rests)
            assertEquals("Chaque fleur d'or offre une bulle", 1, g.bubbles)
        }
    }

    private fun isolatedTarget(g: MochiGame, dx: Float = 60f, kind: MochiGame.FlowerKind = MochiGame.FlowerKind.NORMAL): MochiGame.Flower {
        val source = requireNotNull(g.attachedFlower)
        g.flowers.clear()
        g.flowers += source
        g.hazards.clear()
        g.sweets.clear()
        g.walls.clear()
        return MochiGame.Flower(source.id + 1, g.x + dx, g.y + 120f, kind = kind).also { g.flowers += it }
    }

    private fun landOn(g: MochiGame, target: MochiGame.Flower, offset: Float = 0f) {
        val sourceId = g.attachedId
        val duration = .85f
        val power = MochiGame.DRAG_POWER * g.launchPower
        val vx = (target.at(g.time + duration) + offset - g.x) / duration
        val vy = (target.y - g.y) / duration + .5f * MochiGame.GRAVITY * duration
        assertTrue(g.launch(-vx / power, vy / power))
        var ticks = 0
        while (g.phase == MochiGame.Phase.FLYING && ticks++ < 240) {
            g.update(MochiGame.STEP)
            // Ces fixtures testent la règle de réception, sans les futures sections du générateur.
            g.flowers.removeAll { it.id != sourceId && it.id != target.id }
            g.hazards.clear()
            g.sweets.clear()
            g.walls.clear()
        }
        assertEquals(target.id, g.attachedId)
        assertEquals(MochiGame.Phase.ATTACHED, g.phase)
    }

    @Test fun flowersCatchOnTheWayUpAndCanActuallyBeMissed() {
        val clean = MochiGame(3)
        val flower = isolatedTarget(clean, dx = 0f)
        assertTrue(clean.launch(0f, 110f))
        var ticks = 0
        while (clean.phase == MochiGame.Phase.FLYING && ticks++ < 120) clean.update(MochiGame.STEP)
        assertTrue("Accroche pendant la montée, avant le sommet du saut", clean.time < .35f)
        assertEquals(MochiGame.Phase.ATTACHED, clean.phase)
        assertEquals(flower.id, clean.attachedId)

        val miss = MochiGame(3)
        val missed = isolatedTarget(miss, dx = miss.catchRadius + 5f)
        miss.launch(0f, 110f)
        repeat(180) { miss.update(MochiGame.STEP) }
        assertFalse(missed.visited)
        assertEquals("Une visée trop à côté revient sur la prise inférieure", 0, miss.attachedId)
        assertEquals(0, miss.score)
    }

    @Test fun flowersAlsoCatchSidewaysWithoutCrossingTheirTop() {
        val g = MochiGame(3)
        val target = isolatedTarget(g, dx = 80f)
        g.flowers[g.flowers.indexOf(target)] = target.copy(y = g.y)
        assertTrue(g.launch(-100f, 0f))
        var steps = 0
        while (g.phase == MochiGame.Phase.FLYING && steps++ < 60) g.update(MochiGame.STEP)
        assertEquals(MochiGame.Phase.ATTACHED, g.phase)
        assertEquals(target.id, g.attachedId)
        assertTrue(g.time < .25f)
    }

    @Test fun precisionEarnsTheComboAndAnEdgeLandingBreaksIt() {
        val g = MochiGame(10)
        landOn(g, isolatedTarget(g))
        assertEquals(1, g.perfects)
        assertEquals(1, g.combo)
        val firstPerfectScore = g.score
        landOn(g, isolatedTarget(g, dx = -60f))
        assertEquals(2, g.perfects)
        assertEquals(2, g.combo)
        assertTrue("Une série doit récompenser la maîtrise", g.score - firstPerfectScore > firstPerfectScore)
        val beforeEdge = g.score
        val edge = isolatedTarget(g)
        landOn(g, edge, g.catchRadiusFor(edge) - 2f)
        assertEquals(2, g.perfects)
        assertEquals(0, g.combo)
        assertEquals(2, g.bestCombo)
        assertTrue(g.score - beforeEdge < firstPerfectScore)
        val score = g.score
        g.launch(0f, 35f)
        repeat(120) { g.update(MochiGame.STEP) }
        assertEquals("Rebondir sur une prise déjà obtenue ne rapporte rien", score, g.score)
        assertEquals(2, g.perfects)
    }

    @Test fun sameSeedAndInputsReproduceTheGarden() {
        val a = MochiGame(891)
        val b = MochiGame(891)
        repeat(16) { advanceToNext(a); advanceToNext(b) }
        assertEquals(a.flowers, b.flowers)
        assertEquals(a.hazards, b.hazards)
        assertEquals(a.sweets, b.sweets)
        assertEquals(a.walls, b.walls)
        assertEquals(a.bankShots, b.bankShots)
        assertEquals(a.score, b.score)
        assertNotEquals(MochiGame(1).flowers, MochiGame(2).flowers)
    }

    @Test fun draggingHasADeadZoneAndBoundedPower() {
        val g = MochiGame(3)
        assertFalse(g.launch(1f, 1f))
        assertFalse(g.launch(Float.NaN, 100f))
        val v = g.velocityForDrag(-10000f, 10000f)
        assertEquals(MochiGame.MAX_SPEED, hypot(v.x, v.y), .01f)
        assertTrue(g.launch(-30f, 110f))
        assertTrue(g.vx > 0 && g.vy > 0)
        assertFalse("On ne se relance pas en l'air", g.launch(0f, 100f))
    }

    @Test fun catchingTheSameFlowerNeverCreatesPoints() {
        val g = MochiGame(6)
        assertTrue(g.launch(0f, 18f))
        repeat(120) { g.update(1f / 120f) }
        assertEquals(MochiGame.Phase.ATTACHED, g.phase)
        assertEquals(0, g.attachedId)
        assertEquals(0, g.score)
        assertEquals(0, g.caught)
    }

    @Test fun aBadLaunchFallsAndGameOverIsTerminal() {
        val g = MochiGame(7)
        assertTrue(g.launch(0f, -100f))
        repeat(120) { g.update(1f / 60f) }
        assertEquals(MochiGame.Phase.OVER, g.phase)
        assertEquals(MochiGame.End.FALL, g.end)
        val y = g.y
        g.update(1f)
        assertFalse(g.launch(0f, 100f))
        assertEquals(y, g.y, 0f)
    }

    @Test fun fixedStepsGiveTheSameFlightAtDifferentRefreshRates() {
        val a = MochiGame(42)
        val b = MochiGame(42)
        a.launch(0f, 140f)
        b.launch(0f, 140f)
        repeat(24) { a.update(1f / 60f) }
        repeat(48) { b.update(1f / 120f) }
        assertEquals(a.phase, b.phase)
        assertEquals(a.x, b.x, .001f)
        assertEquals(a.y, b.y, .001f)
        assertEquals(a.bottom, b.bottom, .001f)
    }

    @Test fun previewDoesNotAdvanceTheGameAndMatchesFlight() {
        val g = MochiGame(4)
        val preview = g.preview(0f, 140f)
        assertEquals(0f, g.time, 0f)
        assertEquals(MochiGame.START_Y, g.y, 0f)
        assertEquals(MochiGame.Phase.ATTACHED, g.phase)
        assertTrue(g.launch(0f, 140f))
        repeat(MochiGame.PREVIEW_EVERY) { g.update(MochiGame.STEP) }
        assertEquals(preview.points.first().x, g.x, .001f)
        assertEquals(preview.points.first().y, g.y, .001f)
        assertEquals(MochiGame.Preview.EMPTY, g.preview(0f, 140f))
    }

    @Test fun thePreviewNamesTheFlowerThatWillCatchMochi() {
        val g = MochiGame(5)
        val target = g.flowers.first { it.id == 1 }
        val shot = requireNotNull(MochiGarden.solveLaunch(g.flowers[0], target, g.walls, 0f, g.flowers))
        val preview = g.preview(-shot.velocity.x / MochiGame.DRAG_POWER, shot.velocity.y / MochiGame.DRAG_POWER)
        assertEquals(MochiGame.Outcome.LAND, preview.outcome)
        assertEquals(1, preview.flowerId)
        val last = preview.points.last()
        assertTrue(hypot(last.x - target.x, last.y - target.y) <= g.catchRadiusFor(target) + .01f)
        assertEquals(MochiGame.Outcome.FALL, g.preview(0f, -100f).outcome)
        g.hazards.clear()
        g.hazards += MochiGame.Hazard(g.x, g.y + 85f, MochiGame.Danger.THORN, 0f)
        assertEquals(MochiGame.Outcome.DANGER, g.preview(0f, 110f).outcome)
    }

    @Test fun hazardContactsEndTheFlight() {
        for (kind in MochiGame.Danger.entries) {
            val g = MochiGame(2)
            g.hazards.clear()
            g.hazards += MochiGame.Hazard(g.x, g.y + 85f, kind, 0f)
            g.launch(0f, 110f)
            repeat(60) { g.update(MochiGame.STEP) }
            assertEquals(MochiGame.Phase.OVER, g.phase)
            assertEquals(if (kind == MochiGame.Danger.BEE) MochiGame.End.BEE else MochiGame.End.THORN, g.end)
        }
    }

    @Test fun flowersWiltUnderMochiButTheStartAndGoldenOnesWait() {
        val g = MochiGame(4)
        wait(g, MochiGame.wiltTime(0) + 2f)
        assertEquals("La fleur de départ attend le premier tir", MochiGame.Phase.ATTACHED, g.phase)
        assertTrue(g.wiltFraction < 0f)

        val target = isolatedTarget(g)
        landOn(g, target)
        assertEquals(1f, g.wiltFraction, .02f)
        wait(g, MochiGame.wiltTime(target.row) / 2f)
        assertEquals(.5f, g.wiltFraction, .03f)
        wait(g, MochiGame.wiltTime(target.row))
        assertFalse(target.alive)
        assertEquals(MochiGame.Phase.OVER, g.phase)
        assertEquals(MochiGame.End.FALL, g.end)
        assertEquals(0, g.combo)

        val rest = MochiGame(4)
        landOn(rest, isolatedTarget(rest, kind = MochiGame.FlowerKind.SANCTUARY))
        assertEquals(1, rest.bubbles)
        wait(rest, 30f)
        assertEquals(MochiGame.Phase.ATTACHED, rest.phase)
        assertTrue(rest.wiltFraction < 0f)
        assertTrue("La pression grandit avec l'altitude", MochiGame.wiltTime(40) < MochiGame.wiltTime(0))
        assertEquals(MochiGame.wiltTime(500), MochiGame.wiltTime(1000), 0f)
    }

    @Test fun aBubbleCatchesTheFallAndMochiRelaunchesFromIt() {
        val g = MochiGame(7)
        g.gainBubble()
        g.hazards.clear()
        assertTrue(g.launch(0f, -100f))
        repeat(120) { g.update(1f / 60f) }
        assertEquals(MochiGame.Phase.ATTACHED, g.phase)
        assertTrue(g.inBubble)
        assertEquals(0, g.bubbles)
        assertTrue("La bulle se forme au bord bas de l'écran", g.y < MochiGame.START_Y)
        assertTrue(g.y >= g.bottom)
        val preview = g.preview(0f, 140f)
        assertTrue(preview.points.isNotEmpty())
        assertTrue(g.launch(0f, 140f))
        assertEquals(MochiGame.Phase.FLYING, g.phase)
        assertFalse(g.inBubble)
    }

    @Test fun aBubblePopsAfterAWhileAndTheFallThenEndsTheRun() {
        val g = MochiGame(7)
        g.gainBubble()
        g.hazards.clear()
        assertTrue(g.launch(0f, -100f))
        var ticks = 0
        while (!g.inBubble && ticks++ < 120) g.update(1f / 60f)
        assertTrue(g.inBubble)
        assertEquals(1f, g.wiltFraction, .02f)
        // La bulle dérive lentement vers le haut : sans fleur à portée, sa fin est une chute.
        g.flowers.clear()
        wait(g, MochiGame.BUBBLE_TIME + .5f)
        repeat(120) { g.update(1f / 60f) }
        assertEquals(MochiGame.Phase.OVER, g.phase)
        assertEquals(MochiGame.End.FALL, g.end)
    }

    @Test fun aThornSpendsTheBubbleAndTheBubbleFormsAwayFromIt() {
        val g = MochiGame(2)
        g.gainBubble()
        assertEquals(1, g.bubbles)
        g.hazards.clear()
        // Assez haut pour que la bulle se forme hors de portée de la fleur de départ.
        val thorn = MochiGame.Hazard(g.x, g.y + 120f, MochiGame.Danger.THORN, 0f)
        g.hazards += thorn
        g.launch(0f, 110f)
        repeat(40) { g.update(MochiGame.STEP) }
        assertEquals(MochiGame.Phase.ATTACHED, g.phase)
        assertTrue(g.inBubble)
        assertEquals(0, g.bubbles)
        // La bulle flotte lentement vers le haut après sa formation : quelques unités de marge.
        assertTrue(hypot(g.x - thorn.x, g.y - thorn.y) >= thorn.radius + MochiGame.RADIUS + MochiGame.BUBBLE_CLEARANCE - 4f)
        assertEquals(MochiGame.Outcome.LAND, g.preview(0f, -40f).outcome)
        g.launch(0f, 110f)
        repeat(40) { g.update(MochiGame.STEP) }
        assertEquals(MochiGame.Phase.OVER, g.phase)
        assertEquals(MochiGame.End.THORN, g.end)
    }

    @Test fun peachesRipenABubbleAndAStarFruitGivesOneAtOnce() {
        val g = MochiGame(99)
        g.sweets.clear()
        g.hazards.clear()
        repeat(MochiGame.PEACHES_PER_BUBBLE) { g.sweets += MochiGame.Sweet(g.x, g.y + 30f + it * 8f) }
        g.launch(0f, 100f)
        repeat(24) { g.update(MochiGame.STEP) }
        assertEquals(MochiGame.PEACHES_PER_BUBBLE, g.treats)
        assertEquals(MochiGame.PEACHES_PER_BUBBLE * MochiGame.PEACH_POINTS, g.score)
        assertEquals(1, g.bubbles)
        assertEquals(0, g.peaches)

        val gold = MochiGame(99)
        gold.sweets.clear()
        gold.hazards.clear()
        val fruit = MochiGame.Sweet(gold.x, gold.y + 65f, golden = true)
        gold.sweets += fruit
        gold.launch(0f, 100f)
        repeat(24) { gold.update(MochiGame.STEP) }
        assertTrue(fruit.taken)
        assertEquals(MochiGame.GOLD_POINTS, gold.score)
        assertEquals(1, gold.bubbles)
        repeat(120) { gold.update(MochiGame.STEP) }
        assertEquals(1, gold.treats)
    }

    @Test fun movingPerchesCarryMochiAndTheSpringBoostIsPreviewed() {
        val drift = MochiGame(4)
        drift.flowers[0] = drift.flowers[0].copy(kind = MochiGame.FlowerKind.DRIFT)
        val oldX = drift.x
        drift.update(.1f)
        assertNotEquals(oldX, drift.x)
        assertEquals(drift.flowers[0].at(drift.time), drift.x, .001f)
        assertEquals(MochiGame.START_Y, drift.y, .001f)

        val g = MochiGame(2)
        val ordinary = g.velocityForDrag(0f, 100f)
        g.flowers[0] = g.flowers[0].copy(kind = MochiGame.FlowerKind.SPRING)
        val boosted = g.velocityForDrag(0f, 100f)
        assertEquals(ordinary.y * 1.2f, boosted.y, .001f)
        val preview = g.preview(0f, 100f)
        g.launch(0f, 100f)
        repeat(MochiGame.PREVIEW_EVERY) { g.update(MochiGame.STEP) }
        assertEquals(preview.points.first().y, g.y, .001f)
    }
}
