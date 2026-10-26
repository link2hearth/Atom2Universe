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
            val flight = MochiFlight.step(state, MochiGame.STEP, g.walls)
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

    private fun segmentDistance(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val t = (((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy).coerceAtLeast(.00001f)).coerceIn(0f, 1f)
        return hypot(x - ax - t * dx, y - ay - t * dy)
    }

    private fun advanceToNext(g: MochiGame, choose: Boolean = true) {
        advanceTo(g, g.flowers.first { it.id == g.attachedId + 1 }, choose)
    }

    private fun advanceTo(g: MochiGame, next: MochiGame.Flower, choose: Boolean = true) {
        val from = g.flowers.first { it.id == g.attachedId }
        var v = safeShot(g, next)
        var waits = 0
        val maxWaits = if (from.kind == MochiGame.FlowerKind.FRAGILE) 6 else 40
        while (v == null && waits++ < maxWaits) {
            g.update(.1f)
            v = safeShot(g, next)
        }
        assertNotNull("Fenêtre praticable manquante : seed=${g.seed}, ${from.id} -> ${next.id}, t=${g.time}", v)
        val shot = requireNotNull(v)
        val power = MochiGame.DRAG_POWER * g.launchPower
        assertTrue(g.launch(-shot.x / power, shot.y / power))
        var steps = 0
        while (g.phase == MochiGame.Phase.FLYING && steps++ < 180) g.update(1f / 60f)
        if (g.phase == MochiGame.Phase.CHOOSING && choose) {
            assertTrue(g.chooseUpgrade(g.offers.firstOrNull { it == MochiGame.Upgrade.NECTAR } ?: g.offers.first()))
        }
        if (!choose && g.phase == MochiGame.Phase.CHOOSING) return
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
        // Une vraie section générée et une bulle permettent de tester les deux départs
        // dans le même monde, sans ajouter artificiellement un danger sur la trajectoire.
        var beesSeen = false
        // Cette graine comporte une abeille entre les fleurs fixes 34 et 35.
        for (seed in listOf(10L)) {
            val g = MochiGame(seed)
            // Plusieurs sections sont nécessaires pour rencontrer une paire fixe
            // avec un passage d'abeille après l'obtention du premier secours.
            repeat(40) {
                val from = requireNotNull(g.attachedFlower)
                val next = g.flowers.first { it.id == from.id + 1 }
                beesSeen = beesSeen || g.hazards.any { it.kind == MochiGame.Danger.BEE }
                if (g.shields == 1 && from.kind != MochiGame.FlowerKind.FRAGILE &&
                    from.kind != MochiGame.FlowerKind.DRIFT && next.kind != MochiGame.FlowerKind.DRIFT) {
                    val shot = requireNotNull(MochiGarden.solveLaunch(from, next, g.walls, g.time, g.flowers, catchRadius = g.catchRadius))
                    val velocity = shot.velocity
                    fun contacts(startTime: Float, safetyMargin: Float) =
                        contacts(g, velocity, shot.duration, startTime, safetyMargin)
                    val badDelay = (0..36).firstOrNull { delay ->
                        val hits = contacts(g.time + delay * .1f, -2f)
                        hits.isNotEmpty() && hits.all { it.kind == MochiGame.Danger.BEE }
                    }
                    val hasWindow = (0..36).any { contacts(g.time + it * .1f, 2f).isEmpty() }
                    if (badDelay != null && hasWindow) {
                        repeat(badDelay) { g.update(.1f) }
                        val power = MochiGame.DRAG_POWER * g.launchPower
                        assertTrue(g.launch(-velocity.x / power, velocity.y / power))
                        var ticks = 0
                        while (g.phase == MochiGame.Phase.FLYING && ticks++ < 240) g.update(MochiGame.STEP)
                        assertEquals("L'abeille doit réellement consommer le secours", 0, g.shields)
                        assertEquals(from.id, g.attachedId)
                        assertEquals(MochiGame.Phase.ATTACHED, g.phase)
                        val safeDelay = (0..36).firstOrNull { contacts(g.time + it * .1f, 2f).isEmpty() }
                        assertNotNull("La même trajectoire doit avoir une fenêtre sans collision", safeDelay)
                        repeat(requireNotNull(safeDelay)) { g.update(.1f) }
                        assertTrue(g.launch(-velocity.x / power, velocity.y / power))
                        ticks = 0
                        while (g.phase == MochiGame.Phase.FLYING && ticks++ < 240) g.update(MochiGame.STEP)
                        assertEquals("Attendre doit permettre de réussir exactement le même saut", next.id, g.attachedId)
                        assertTrue(g.phase == MochiGame.Phase.ATTACHED || g.phase == MochiGame.Phase.CHOOSING)
                        return
                    }
                }
                advanceToNext(g, choose = false)
                if (g.phase == MochiGame.Phase.CHOOSING) {
                    assertTrue(g.chooseUpgrade(if (MochiGame.Upgrade.BUBBLE in g.offers)
                        MochiGame.Upgrade.BUBBLE else g.offers.first()))
                }
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
            assertTrue(kinds.containsAll(listOf(MochiGame.FlowerKind.DRIFT, MochiGame.FlowerKind.FRAGILE,
                MochiGame.FlowerKind.SPRING, MochiGame.FlowerKind.SANCTUARY)))
            assertTrue("Les hauteurs de saut doivent poser différents problèmes", gaps.size >= 3)
            assertEquals(listOf(10, 22, 36), rests)
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
        assertTrue(g.phase == MochiGame.Phase.ATTACHED || g.phase == MochiGame.Phase.CHOOSING)
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

    @Test fun twoPassiveFamiliesAreAChoiceAndCannotAllBeMaxed() {
        val g = MochiGame(120)
        repeat(14) {
            val target = isolatedTarget(g, dx = if (it % 2 == 0) 50f else -50f, kind = MochiGame.FlowerKind.SANCTUARY)
            landOn(g, target)
            assertEquals(MochiGame.Phase.CHOOSING, g.phase)
            if (g.magnet > 0 && g.grip > 0) assertFalse(MochiGame.Upgrade.NECTAR in g.offers)
            if (g.shields == 1) assertFalse(MochiGame.Upgrade.BUBBLE in g.offers)
            val choice = listOf(MochiGame.Upgrade.MAGNET, MochiGame.Upgrade.GRIP,
                MochiGame.Upgrade.BUBBLE, MochiGame.Upgrade.HARVEST).firstOrNull { option -> option in g.offers }
            assertNotNull("Une halte doit toujours proposer un choix utile", choice)
            assertTrue(g.chooseUpgrade(requireNotNull(choice)))
            assertTrue(g.passiveSlotsUsed <= 2)
            assertTrue(g.magnet <= 3 && g.grip <= 3 && g.nectar <= 3 && g.shields <= 1)
        }
        assertEquals(3, g.magnet)
        assertEquals(3, g.grip)
        assertEquals(0, g.nectar)
        val target = isolatedTarget(g, dx = 0f, kind = MochiGame.FlowerKind.SANCTUARY)
        landOn(g, target)
        assertEquals(listOf(MochiGame.Upgrade.HARVEST), g.offers)
        val before = g.score
        assertTrue(g.chooseUpgrade(MochiGame.Upgrade.HARVEST))
        assertEquals(before + MochiGame.HARVEST_POINTS, g.score)
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
        repeat(5) { g.update(MochiGame.STEP) }
        assertEquals(preview.first().x, g.x, .001f)
        assertEquals(preview.first().y, g.y, .001f)
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

    @Test fun normalGuideBecomesShortAfterThreeActualLaunches() {
        val g = MochiGame(1)
        val fullSize = g.preview(0f, 110f).size
        repeat(3) {
            assertTrue(g.showTrajectory)
            assertTrue(g.preview(0f, 110f).isNotEmpty())
            assertFalse(g.launch(0f, 0f))
            assertEquals(it, g.shots)
            assertTrue(g.launch(0f, 18f))
            repeat(120) { g.update(MochiGame.STEP) }
        }
        assertEquals(MochiGame.Phase.ATTACHED, g.phase)
        assertFalse(g.showTrajectory)
        val shortGuide = g.preview(0f, 110f)
        assertTrue(shortGuide.isNotEmpty())
        assertTrue("Le guide normal montre seulement l'impulsion", shortGuide.size < fullSize / 2)
    }

    @Test fun easyModeKeepsTheGuideAfterTheTutorial() {
        val g = MochiGame(1, easyMode = true)
        repeat(6) {
            assertTrue(g.launch(0f, 18f))
            repeat(120) { g.update(MochiGame.STEP) }
        }
        assertEquals(6, g.shots)
        assertTrue(g.showTrajectory)
        assertTrue(g.preview(0f, 110f).isNotEmpty())
    }

    private fun rest(seed: Long): MochiGame = MochiGame(seed).also { g ->
        repeat(MochiGame.REST_EVERY) { advanceToNext(g, choose = false) }
    }

    @Test fun sanctuaryFreezesTheWorldAndAcceptsOnlyOneGift() {
        val g = rest(5)
        assertEquals(MochiGame.Phase.CHOOSING, g.phase)
        assertEquals(3, g.offers.size)
        assertEquals(3, g.offers.distinct().size)
        val time = g.time
        repeat(30) { g.update(.1f) }
        assertEquals(time, g.time, 0f)
        assertFalse(g.launch(0f, 100f))
        val unavailable = MochiGame.Upgrade.entries.first { it !in g.offers }
        assertFalse(g.chooseUpgrade(unavailable))
        val gift = g.offers.first()
        assertTrue(g.chooseUpgrade(gift))
        assertEquals(MochiGame.Phase.ATTACHED, g.phase)
        assertFalse(g.chooseUpgrade(gift))
        assertEquals(1, g.shields + g.magnet + g.grip + g.nectar)
    }

    @Test fun movingAndFragilePerchesHaveDifferentConsequences() {
        val drift = MochiGame(4)
        drift.flowers[0] = drift.flowers[0].copy(kind = MochiGame.FlowerKind.DRIFT)
        val oldX = drift.x
        drift.update(.1f)
        assertNotEquals(oldX, drift.x)
        assertEquals(drift.flowers[0].at(drift.time), drift.x, .001f)
        assertEquals(MochiGame.START_Y, drift.y, .001f)

        val fragile = MochiGame(4)
        fragile.flowers[0] = fragile.flowers[0].copy(kind = MochiGame.FlowerKind.FRAGILE, life = .05f)
        fragile.update(.1f)
        assertFalse(fragile.flowers[0].alive)
        assertEquals(MochiGame.Phase.FLYING, fragile.phase)
        assertEquals(0, fragile.shots)
        assertTrue(fragile.y < MochiGame.START_Y)
    }

    @Test fun springBoostIsIncludedInThePreviewAndTheLaunch() {
        val g = MochiGame(2)
        val ordinary = g.velocityForDrag(0f, 100f)
        g.flowers[0] = g.flowers[0].copy(kind = MochiGame.FlowerKind.SPRING)
        val boosted = g.velocityForDrag(0f, 100f)
        assertEquals(ordinary.y * 1.2f, boosted.y, .001f)
        val preview = g.preview(0f, 100f)
        g.launch(0f, 100f)
        repeat(5) { g.update(MochiGame.STEP) }
        assertEquals(preview.first().y, g.y, .001f)
    }

    @Test fun bubbleRescuesOnceWithoutPayingAgainForThePerch() {
        val g = (0L..10L).asSequence().map { seed ->
            MochiGame(seed).also { landOn(it, isolatedTarget(it, kind = MochiGame.FlowerKind.SANCTUARY)) }
        }.first { MochiGame.Upgrade.BUBBLE in it.offers }
        assertTrue(g.chooseUpgrade(MochiGame.Upgrade.BUBBLE))
        val perch = g.attachedId
        val catches = g.caught
        g.hazards.clear()
        g.hazards += MochiGame.Hazard(g.x, g.y + 80f, MochiGame.Danger.THORN, 0f)
        g.launch(0f, 100f)
        repeat(30) { g.update(MochiGame.STEP) }
        assertEquals(MochiGame.Phase.ATTACHED, g.phase)
        assertEquals(perch, g.attachedId)
        assertEquals(catches, g.caught)
        assertEquals(0, g.shields)
        assertTrue(g.offers.isEmpty())
        g.launch(0f, 100f)
        repeat(30) { g.update(MochiGame.STEP) }
        assertEquals(MochiGame.Phase.OVER, g.phase)
    }

    @Test fun bubbleCannotTeleportToAnUnvisitedFlowerAfterAFragileDeparture() {
        val g = MochiGame(1)
        landOn(g, isolatedTarget(g, kind = MochiGame.FlowerKind.SANCTUARY))
        assertTrue(g.chooseUpgrade(MochiGame.Upgrade.BUBBLE))
        val current = g.flowers.indexOfFirst { it.id == g.attachedId }
        g.flowers[current] = g.flowers[current].copy(kind = MochiGame.FlowerKind.FRAGILE)
        // Proche du départ, mais hors du saut : une fleur touchée en montant serait désormais visitée.
        val unexplored = MochiGame.Flower(99, g.x + 40f, g.y + 20f)
        g.flowers += unexplored
        g.hazards += MochiGame.Hazard(g.x, g.y + 75f, MochiGame.Danger.THORN, 0f)
        g.launch(0f, 100f)
        repeat(45) { g.update(MochiGame.STEP) }
        assertEquals(MochiGame.Phase.ATTACHED, g.phase)
        assertEquals(0, g.attachedId)
        assertFalse(unexplored.visited)
        assertEquals(0, g.shields)
        assertEquals(0, g.combo)
    }

    @Test fun detoursAreOptionalAndGoldenFruitPaysOnlyOnce() {
        val g = MochiGame(99)
        g.sweets.clear()
        val gold = MochiGame.Sweet(g.x, g.y + 65f, golden = true)
        g.sweets += gold
        g.launch(0f, 100f)
        repeat(24) { g.update(MochiGame.STEP) }
        assertTrue(gold.taken)
        assertEquals(25, g.score)
        assertEquals(1, g.treats)
        repeat(120) { g.update(MochiGame.STEP) }
        assertEquals(1, g.treats)
    }
}
