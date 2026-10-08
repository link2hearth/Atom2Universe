package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

class ClassicGolfTest {
    @Test fun mowingBandsFollowContoursAndRespectHazards() {
        val hole = ClassicHole(1, 4, 300f, greenShape = GolfGreenShape(shape = 0f))
        assertEquals(GolfLie.FAIRWAY, hole.lieAt(16.9f, 100f))
        assertEquals(GolfLie.SEMI_ROUGH, hole.lieAt(18.5f, 100f))
        assertEquals(GolfLie.ROUGH, hole.lieAt(20.1f, 100f))
        assertEquals(GolfLie.GREEN, hole.lieAt(15.9f, 300f))
        assertEquals(GolfLie.FRINGE, hole.lieAt(17f, 300f))
        assertEquals(GolfLie.SEMI_ROUGH, hole.lieAt(18.1f, 300f))
        for (surface in listOf(GolfLie.BUNKER, GolfLie.WATER)) {
            assertEquals(surface, hole.copy(hazards = listOf(GolfHazard(17f, 300f, 1f, 1f, surface)))
                .lieAt(17f, 300f))
        }
    }

    @Test fun mowingBandsHaveIntermediateRollingDistances() {
        fun run(lie: GolfLie): Float {
            val ball = BallState().apply { vz = 4f }
            repeat(1200) { GolfBallPhysics.rollingStep(ball, 0f, 0f, lie) }
            assertEquals(0f, ball.vz, .0001f)
            return ball.pz
        }
        val distances = listOf(GolfLie.GREEN, GolfLie.FRINGE, GolfLie.FAIRWAY,
            GolfLie.SEMI_ROUGH, GolfLie.ROUGH).map(::run)
        distances.zipWithNext().forEach { (faster, slower) -> assertTrue(faster > slower * 1.2f) }
    }

    private fun settle(game: ClassicGame, dt: Float = 1f / 60f) {
        repeat((32f / dt).toInt()) {
            if (game.state == GolfState.READY || game.state == GolfState.HOLED) return
            game.update(dt)
        }
        fail("Shot did not settle on hole ${game.hole.number}")
    }

    /** A wide, almost level test hole: everything is fairway. */
    private val range = ClassicHole(1, 5, 1000f, width = 2000f, fairwayBaseWidth = 1900f)

    private class Flight(val carry: Float, val apex: Float, val landingAngle: Float, val run: Float)

    /**
     * Full flight, hops and roll on perfectly flat turf of [lie], straight from the physics.
     * [contact] is the game's vertical contact point: −1 low (more spin), +1 high.
     */
    private fun flatShot(club: GolfClub, lie: GolfLie, power: Float = 1f, contact: Float = 0f): Flight {
        val swing = GolfCalibration.swing(club, power)
        val b = BallState()
        GolfBallPhysics.launch(b, 0f, GolfBallPhysics.RADIUS, 0f, swing * GolfCalibration.fullSpeed(club),
            GolfCalibration.launch(club, swing) - 1.5f * contact, club.spinRpm * swing * (1f - .5f * contact), 0f, 0f)
        var apex = 0f
        while (!(b.py <= GolfBallPhysics.RADIUS && b.vy < 0f)) { GolfBallPhysics.flightStep(b, 0f, 0f); apex = max(apex, b.py) }
        val carry = b.pz
        val angle = Math.toDegrees(atan2(-b.vy, hypot(b.vx, b.vz)).toDouble()).toFloat()
        repeat(20) {
            b.py = GolfBallPhysics.RADIUS
            GolfBallPhysics.bounce(b, 0f, 1f, 0f, lie, landing = it == 0)
            if (b.vy < GolfBallPhysics.ROLL_THRESHOLD) {
                GolfBallPhysics.startRolling(b, 0f, 1f, 0f, lie)
                val speed = hypot(b.vx, b.vz)
                val distance = speed * speed / (2f * GolfBallPhysics.rolling(lie))
                return Flight(carry, apex, angle, b.pz + sign(b.vz) * distance - carry)
            }
            b.py += 1e-4f
            while (!(b.py <= GolfBallPhysics.RADIUS && b.vy < 0f)) GolfBallPhysics.flightStep(b, 0f, 0f)
        }
        fail("Ball never rolled"); error("")
    }

    @Test fun courseHasEighteenOriginalPlayableHolesAndFixedBag() {
        assertEquals(18, ClassicCourse.holes.size)
        assertEquals(72, ClassicCourse.holes.sumOf { it.par })
        assertEquals((1..18).toList(), ClassicCourse.holes.map { it.number })
        assertEquals(14, GolfClub.entries.size)
        ClassicCourse.holes.forEach { hole ->
            assertEquals(GolfLie.TEE, hole.lieAt(hole.tee.x, hole.tee.z))
            assertEquals(GolfLie.GREEN, hole.lieAt(hole.cup.x, hole.cup.z))
            assertTrue(hole.greenRadius >= 14f)
            assertTrue(hole.length in 90f..510f)
            assertTrue(abs(hole.fairwayCenter(hole.length) - hole.cup.x) < .001f)
        }
    }

    @Test fun everyFullSwingCarriesItsClubDistanceAndPowerIsAFractionOfIt() {
        for (club in GolfClub.entries.filter { it != GolfClub.PUTTER }) {
            val full = flatShot(club, GolfLie.FAIRWAY)
            assertEquals(club.name, club.carry, full.carry, club.carry * .01f)
            assertEquals(club.name, club.carry * .5f, flatShot(club, GolfLie.FAIRWAY, .5f).carry, club.carry * .03f)
            assertEquals(club.name, club.carry * .2f, flatShot(club, GolfLie.FAIRWAY, .2f).carry, club.carry * .03f)
        }
    }

    @Test fun flightsLookLikeGolfNotLikeACannon() {
        val driver = flatShot(GolfClub.DRIVER, GolfLie.FAIRWAY)
        assertTrue("Driver apex ${driver.apex}", driver.apex in 20f..35f)
        assertTrue("Driver descent ${driver.landingAngle}", driver.landingAngle in 28f..45f)
        assertTrue("A drive runs on: ${driver.run}", driver.run in 15f..50f)
        val iron = flatShot(GolfClub.IRON7, GolfLie.FAIRWAY)
        assertTrue("7 iron apex ${iron.apex}", iron.apex in 20f..34f)
        assertTrue("7 iron descent ${iron.landingAngle}", iron.landingAngle > 40f)
        assertTrue("7 iron run ${iron.run}", iron.run in 5f..16f)
        for (club in listOf(GolfClub.PW, GolfClub.GW, GolfClub.SW, GolfClub.LW)) {
            val wedge = flatShot(club, GolfLie.GREEN)
            assertTrue("A centred full $club rolls a little and never comes back: ${wedge.run}", wedge.run in 1f..8f)
        }
        assertTrue("Rough grabs the ball", flatShot(GolfClub.DRIVER, GolfLie.ROUGH).run < driver.run * .6f)
        assertTrue("A low contact brings a wedge back", flatShot(GolfClub.SW, GolfLie.GREEN, 1f, -1f).run < 0f)
        val punch = flatShot(GolfClub.IRON7, GolfLie.GREEN, .5f, 1f)
        val pitch = flatShot(GolfClub.IRON7, GolfLie.GREEN, .5f)
        assertTrue("A high contact lets an iron run: ${punch.run} vs ${pitch.run}", punch.run > pitch.run * 1.5f)
    }

    @Test fun aSlopeOnlyNudgesTheLanding() {
        // The crater impact turned a 55° wedge on a 1.4 % upslope into a backward bounce (hole 7).
        for (club in listOf(GolfClub.IRON7, GolfClub.PW, GolfClub.LW)) {
            val swing = GolfCalibration.swing(club, 1f)
            val falling = BallState()
            GolfBallPhysics.launch(falling, 0f, GolfBallPhysics.RADIUS, 0f, swing * GolfCalibration.fullSpeed(club),
                GolfCalibration.launch(club, swing), club.spinRpm * swing, 0f, 0f)
            while (!(falling.py <= GolfBallPhysics.RADIUS && falling.vy < 0f)) GolfBallPhysics.flightStep(falling, 0f, 0f)
            /** Speed along a plane rising by [slope] towards the target, just after the landing. */
            fun kept(slope: Float): Float {
                val b = BallState().apply {
                    vx = falling.vx; vy = falling.vy; vz = falling.vz; wx = falling.wx; wy = falling.wy; wz = falling.wz
                }
                val n = sqrt(1f + slope * slope)
                GolfBallPhysics.bounce(b, 0f, 1f / n, -slope / n, GolfLie.GREEN, landing = true)
                return (b.vy * slope + b.vz) / n
            }
            val flat = kept(0f)
            assertTrue("$club keeps running after its landing: $flat", flat > 1f)
            for (slope in listOf(-.03f, .03f))
                assertEquals("$club on a ${slope * 100} % slope", flat, kept(slope), flat * .15f)
        }
    }

    @Test fun centredApproachesRunForwardOnEveryGreen() {
        // On the real relief of both courses, a centred contact never comes back after landing.
        var greens = 0
        for (holes in listOf(ClassicCourse.holes, HeatherCourse.holes)) for (h in holes) for (short in listOf(6f, 0f)) {
            val g = ClassicGame(h)
            if (h.par > 3) {
                val z = h.length - 90f; val x = h.fairwayCenter(z)
                if (h.lieAt(x, z) != GolfLie.FAIRWAY) continue
                g.restore(GolfPoint(x, h.heightAt(x, z) + ClassicHole.BALL_RADIUS, z), 0)
            }
            val target = g.distanceToCup - short
            var lo = .05f; var hi = 1f
            repeat(18) { val m = (lo + hi) / 2; if (g.preview(m).carry < target) lo = m else hi = m }
            val ux = sin(g.aimAngle); val uz = cos(g.aimAngle)
            assertTrue(g.hit((lo + hi) / 2))
            var landing: GolfPoint? = null
            var before = g.velocity
            repeat(40 * 120) {
                if (g.state != GolfState.FLYING && g.state != GolfState.ROLLING) return@repeat
                val wasFlying = g.state == GolfState.FLYING
                g.update(1f / 120f)
                val after = g.velocity
                if (landing == null && wasFlying && (g.state != GolfState.FLYING || (before.y < 0f && after.y > 0f))) landing = g.ball
                before = after
            }
            val first = landing ?: continue
            if (h.lieAt(first.x, first.z) != GolfLie.GREEN || g.state == GolfState.HOLED) continue
            greens++
            val run = (g.ball.x - first.x) * ux + (g.ball.z - first.z) * uz
            assertTrue("Hole ${h.number} (${g.club}, ${short} m short): run $run", run >= .5f)
        }
        assertTrue("Enough approaches landed on a green: $greens", greens >= 40)
    }

    @Test fun aShorterSwingKeepsTheClubsDescentAndRun() {
        // Partial swings used to come in flat and roll 40 to 90 m on a green, while a full one stopped.
        for (club in GolfClub.entries.filter { it != GolfClub.PUTTER }) {
            val full = flatShot(club, GolfLie.FAIRWAY)
            for (power in listOf(.3f, .5f, .7f, .9f)) {
                val partial = flatShot(club, GolfLie.FAIRWAY, power)
                assertEquals("$club at $power comes down like a full swing", full.landingAngle, partial.landingAngle, 1.5f)
                if (club.ordinal >= GolfClub.IRON7.ordinal) {
                    val green = flatShot(club, GolfLie.GREEN, power)
                    assertTrue("$club at $power holds the green: ${green.run}", green.run < 15f)
                }
            }
        }
    }

    @Test fun anImpactNeverCreatesEnergy() {
        val random = Random(7)
        val r2 = GolfBallPhysics.RADIUS * GolfBallPhysics.RADIUS
        fun energy(b: BallState) = .5f * (b.vx * b.vx + b.vy * b.vy + b.vz * b.vz) + .2f * r2 * (b.wx * b.wx + b.wy * b.wy + b.wz * b.wz)
        repeat(4000) {
            val b = BallState()
            b.vx = random.nextFloat() * 60f - 30f; b.vy = -random.nextFloat() * 40f; b.vz = random.nextFloat() * 60f - 30f
            b.wx = random.nextFloat() * 1600f - 800f; b.wy = random.nextFloat() * 400f - 200f; b.wz = random.nextFloat() * 1600f - 800f
            val gx = random.nextFloat() * .8f - .4f; val gz = random.nextFloat() * .8f - .4f
            val length = sqrt(1f + gx * gx + gz * gz)
            val before = energy(b)
            GolfBallPhysics.bounce(b, -gx / length, 1f / length, -gz / length, GolfLie.entries[random.nextInt(5)], it % 2 == 0)
            assertTrue("Impact $it gained energy", energy(b) <= before * 1.0001f + 1e-4f)
        }
    }

    @Test fun integrationIsIndependentOfRenderFrameGroupingAndPreviewIsOnlyANominalGuide() {
        val a = ClassicGame(ClassicCourse.holes[0])
        val b = ClassicGame(ClassicCourse.holes[0])
        val guide = a.preview(.76f)
        assertSame(guide, a.preview(.76f))
        a.hit(.76f); b.hit(.76f)
        settle(a, 1f / 30f); settle(b, 1f / 120f)
        assertEquals(a.ball.x, b.ball.x, .0001f)
        assertEquals(a.ball.z, b.ball.z, .0001f)
        val landing = guide.landing!!
        assertTrue("The guide marks the landing, not the end of the run", hypot(landing.x - a.ball.x, landing.z - a.ball.z) > .2f)
        assertEquals(a.strokes, b.strokes)
    }

    @Test fun longerClubsGoFurtherAndLowerPowerShortensTheShot() {
        var previous = Float.POSITIVE_INFINITY
        for (club in GolfClub.entries.filter { it != GolfClub.PUTTER }) {
            val game = ClassicGame(range)
            game.windX = 0f; game.windZ = 0f; game.club = club; game.aimAngle = 0f
            val guide = game.preview(1f)
            assertEquals(club.name, club.carry, guide.carry, club.carry * .06f)
            game.hit(1f); settle(game)
            assertTrue("${club.name}: ${game.ball.z}", game.ball.z in club.carry * .9f..club.carry * 1.3f)
            assertTrue("${club.name} must be shorter than the previous club", game.ball.z < previous)
            previous = game.ball.z
            val full = game.ball.z
            game.reset(); game.club = club; game.aimAngle = 0f
            assertEquals(club.name, club.carry * .5f, game.preview(.5f).carry, club.carry * .06f)
            game.hit(.5f); settle(game)
            assertTrue(club.name, game.ball.z < full * .85f)
        }
    }

    @Test fun shortPuttCanHoleAndFastPuttCannotTeleportIntoCup() {
        val hole = ClassicCourse.holes[0]
        val game = ClassicGame(hole)
        game.restore(GolfPoint(hole.cup.x, 0f, hole.length - 1.5f), 2)
        assertEquals(GolfClub.PUTTER, game.club)
        game.aimAngle = 0f
        game.hit(2.2f / GolfClub.PUTTER.carry); settle(game)
        assertEquals(GolfState.HOLED, game.state)
        assertEquals(3, game.strokes)
        game.restore(GolfPoint(hole.cup.x, 0f, hole.length - 1.5f), 2)
        game.aimAngle = 0f
        game.hit(1f); settle(game)
        assertNotEquals(GolfState.HOLED, game.state)
    }

    @Test fun waterAndOutOfBoundsCountStrokeAndDistancePenaltyOnce() {
        for (water in listOf(true, false)) {
            val hole = ClassicHole(1, 4, 300f, width = if (water) 200f else 12f,
                hazards = if (water) listOf(GolfHazard(0f, 30f, 40f, 25f, GolfLie.WATER)) else emptyList())
            val game = ClassicGame(hole)
            game.windX = 0f; game.windZ = 0f
            game.club = GolfClub.PUTTER
            game.aimAngle = if (water) 0f else (PI / 2).toFloat()
            val guide = game.preview(1f)
            assertTrue("The guide flags the penalty", guide.hazard)
            assertFalse("Penalty preview must never draw a teleport back to tee", guide.flight.drop(1).any { it == hole.tee })
            game.hit(1f); settle(game)
            assertEquals(2, game.strokes)
            assertEquals(1, game.lastPenalty)
            assertEquals(hole.tee, game.ball)
            repeat(20) { game.update(.1f) }
            assertEquals(2, game.strokes)
        }
    }

    @Test fun interruptedShotPersistsSafePositionWithoutErasingStroke() {
        val game = ClassicGame(ClassicCourse.holes[0])
        val origin = game.ball
        game.hit(1f); game.update(.2f)
        assertEquals(origin, game.saveBall)
        assertEquals(1, game.saveStrokes)
        val restored = ClassicGame(game.hole)
        restored.restore(game.saveBall, game.saveStrokes)
        assertEquals(origin, restored.ball)
        assertEquals(1, restored.strokes)
        assertEquals(GolfState.READY, restored.state)
        assertFalse(game.hit(1f))
        assertFalse("No power, no stroke", ClassicGame(game.hole).hit(0f))
    }

    @Test fun roughAndSandReduceCarryAndWedgeCanEscapeBunker() {
        val fairway = ClassicHole(1, 4, 400f, fairwayBaseWidth = 180f)
        val rough = fairway.copy(fairwayBaseWidth = 1f)
        val sand = fairway.copy(hazards = listOf(GolfHazard(20f, 100f, 12f, 12f, GolfLie.BUNKER)))
        fun shot(hole: ClassicHole, club: GolfClub): ClassicGame = ClassicGame(hole).apply {
            restore(GolfPoint(20f, 0f, 100f), 0)
            windX = 0f; windZ = 0f
            this.club = club; aimAngle = 0f
            hit(.6f); settle(this)
        }
        val clean = shot(fairway, GolfClub.IRON7)
        val thick = shot(rough, GolfClub.IRON7)
        val trapped = shot(sand, GolfClub.IRON7)
        assertTrue(thick.ball.z < clean.ball.z)
        assertTrue(trapped.ball.z < thick.ball.z)
        assertNotEquals(GolfLie.BUNKER, shot(sand, GolfClub.SW).lie)
    }

    @Test fun finalRollingStepOutOfBoundsStillAppliesPenalty() {
        val cleanHole = ClassicHole(1, 4, 400f, fairwayBaseWidth = 150f)
        // Moving an OB boundary leaves the relief identical. A water fixture would excavate
        // a shore and change the trajectory before the boundary this test needs to isolate.
        val start = GolfPoint(5f, 0f, 0f)
        assertEquals(GolfLie.FAIRWAY, cleanHole.lieAt(start.x, start.z))
        val clean = ClassicGame(cleanHole).apply {
            restore(start, 0); club = GolfClub.PUTTER; aimAngle = PI.toFloat() / 2f
            hit(.1f)
        }
        var beforeLastStep = clean.ball
        var steps = 0
        while (clean.state == GolfState.ROLLING && steps++ < 1000) {
            beforeLastStep = clean.ball
            clean.update(1f / 120f)
        }
        assertEquals(GolfState.READY, clean.state)
        val boundary = clean.ball.x - .00005f
        assertTrue("Only the step that stops the ball must cross OB", beforeLastStep.x < boundary)
        val narrowHole = cleanHole.copy(width = boundary * 2f)
        assertEquals(GolfLie.FAIRWAY, narrowHole.lieAt(beforeLastStep.x, beforeLastStep.z))
        assertEquals(GolfLie.OUT, narrowHole.lieAt(clean.ball.x, clean.ball.z))
        assertEquals(cleanHole.heightAt(clean.ball.x, clean.ball.z), narrowHole.heightAt(clean.ball.x, clean.ball.z), 0f)
        val out = ClassicGame(narrowHole).apply {
            restore(start, 0); club = GolfClub.PUTTER; aimAngle = PI.toFloat() / 2f
            hit(.1f); settle(this)
        }
        assertEquals(GolfState.READY, out.state)
        assertEquals(start.x, out.ball.x, 0f)
        assertEquals(start.z, out.ball.z, 0f)
        assertEquals(1, out.lastPenalty)
        assertEquals(2, out.strokes)
        repeat(10) { out.update(.1f) }
        assertEquals(1, out.lastPenalty)
        assertEquals(2, out.strokes)
    }

    @Test fun treesHaveSafeDeterministicPositionsAndCannotIncreaseEnergy() {
        val hole = ClassicCourse.holes[0]
        assertEquals(hole.trees, hole.copy().trees)
        assertTrue(hole.trees.isNotEmpty())
        hole.trees.forEach { tree ->
            assertTrue(hypot(tree.x, tree.z) > 15f + tree.radius)
            assertTrue(hypot(tree.x - hole.cup.x, tree.z - hole.cup.z) > 25f + tree.radius)
            assertTrue(abs(tree.x - hole.pathX(tree.z)) > 5f + tree.radius)
        }
        fun speed2(v: GolfPoint) = v.x * v.x + v.y * v.y + v.z * v.z
        for (kind in 0..2) {
            val tree = GolfTree(0f, 0f, 4f, kind)
            val incoming = GolfPoint(0f, 0f, 80f)
            val trunk = GolfTreeCollision.collide(tree, 0f, GolfPoint(0f, .2f, -2f), GolfPoint(0f, .2f, 2f), incoming)
            assertNotNull(trunk)
            assertTrue(trunk!!.velocity.z < 0f)
            assertTrue(speed2(trunk.velocity) < speed2(incoming) * .2f)
            val canopy = GolfTreeCollision.collide(tree, 0f, GolfPoint(1f, 6.6f, -8f), GolfPoint(1f, 6.6f, 8f), incoming)
            assertNotNull(canopy)
            assertTrue(speed2(canopy!!.velocity) < speed2(incoming) * .1f)
            assertTrue(canopy.point.x.isFinite() && canopy.point.y.isFinite() && canopy.point.z.isFinite())
            assertNull(GolfTreeCollision.collide(tree, 0f, GolfPoint(0f, 40f, -8f), GolfPoint(0f, 40f, 8f), incoming))
            // A low ball to the side of the trunk passes freely underneath the foliage.
            assertNull(GolfTreeCollision.collide(tree, 0f, GolfPoint(1f, .2f, -8f), GolfPoint(1f, .2f, 8f), incoming))
        }
    }

    @Test fun realShotHitsTreesEvenThoughTheNominalGuideDoesNotSolveObstacles() {
        val hole = ClassicCourse.holes[0]
        val tree = hole.trees.first { abs(it.x) < hole.width * .5f - 5f && it.z in 10f..hole.length - 30f }
        val game = ClassicGame(hole)
        game.restore(GolfPoint(tree.x, 0f, tree.z - 1.1f), 0)
        game.club = GolfClub.PUTTER; game.aimAngle = 0f
        val predicted = game.preview(1f).landing!!
        game.hit(1f); settle(game)
        assertTrue("Tree trunk must rebound the approaching ball", game.ball.z < tree.z - .4f)
        assertTrue(predicted.z > tree.z)
    }

    @Test fun everyCupAcceptsAWellReadOneMetrePutt() {
        // With a real 108 mm hole the break matters: a straight line can miss on a sloping green,
        // but from every side some line within a few degrees must drop.
        ClassicCourse.holes.forEach { hole ->
            for (direction in 0..3) {
                val angle = direction * PI.toFloat() * .5f
                val holed = (-40..40).any { tenth ->
                    val game = ClassicGame(hole)
                    game.restore(GolfPoint(hole.cup.x - sin(angle), 0f, hole.cup.z - cos(angle)), 2)
                    assertEquals(GolfClub.PUTTER, game.club)
                    game.aimAngle = angle + Math.toRadians(tenth * .2).toFloat()
                    game.hit(1.5f / GolfClub.PUTTER.carry); settle(game)
                    game.state == GolfState.HOLED && game.strokes == 3
                }
                assertTrue("Hole ${hole.number}, direction $direction: no line drops", holed)
            }
            val dx = (hole.heightAt(hole.cup.x + .3f, hole.cup.z) - hole.heightAt(hole.cup.x - .3f, hole.cup.z)) / .6f
            val dz = (hole.heightAt(hole.cup.x, hole.cup.z + .3f) - hole.heightAt(hole.cup.x, hole.cup.z - .3f)) / .6f
            assertTrue("Cup ${hole.number} needs a stable putting surface", hypot(dx, dz) < .04f)
        }
    }

    @Test fun caddieAimsAndHandsAClubButTheDoseStaysWithThePlayer() {
        val hole = ClassicCourse.holes[0]
        val game = ClassicGame(hole)
        val target = hole.openingTarget
        assertEquals(atan2(target.x, target.z), game.aimAngle, 1e-5f)
        assertTrue("The tee club reaches the landing area", game.club.carry >= hypot(target.x, target.z))
        game.club = GolfClub.IRON8; game.setSpin(.4f, -.3f)
        game.hit(.45f); settle(game)
        assertEquals(GolfState.READY, game.state)
        val next = hole.recommendedLanding(game.ball)
        assertEquals(atan2(next.x - game.ball.x, next.z - game.ball.z), game.aimAngle, 1e-4f)
        assertNotEquals(GolfClub.DRIVER, game.club)
        assertEquals("The impact point is reset for each shot", 0f, game.spinX, 0f)
        assertTrue(game.distanceToCup > 150f)
        val par3 = ClassicCourse.holes.first { it.par == 3 }
        val tee = ClassicGame(par3).club
        val reach = hypot(par3.cup.x, par3.cup.z)
        assertTrue("The par 3 club reaches the flag", tee.carry >= reach)
        assertTrue("…and is the shortest that does", GolfClub.entries[tee.ordinal + 1].carry < reach)
    }

    @Test fun impactErrorsCurveAndLoseDistance() {
        fun shot(error: Float) = ClassicGame(range).apply {
            windX = 0f; windZ = 0f; club = GolfClub.IRON7; aimAngle = 0f
            hit(.75f, error); settle(this)
        }
        val perfect = shot(0f)
        val right = shot(.65f)
        val left = shot(-.65f)
        val missed = shot(1f)
        assertEquals("A clean strike stays on line", 0f, perfect.ball.x, 1.5f)
        // Facing +z, the player's right is -x.
        assertTrue(right.ball.x < perfect.ball.x - 5f)
        assertTrue(left.ball.x > perfect.ball.x + 5f)
        assertTrue(right.ball.z < perfect.ball.z)
        assertTrue(left.ball.z < perfect.ball.z)
        assertTrue(missed.ball.z < perfect.ball.z * .92f)
        assertTrue(abs(missed.ball.x) > abs(right.ball.x))
    }

    @Test fun contactPointChangesFlightCurveAndRunAndTheGuideShowsIt() {
        fun shot(x: Float, y: Float): Triple<ClassicGame, Float, ShotPreview> {
            val game = ClassicGame(range)
            game.club = GolfClub.IRON7; game.aimAngle = 0f; game.windX = 3f; game.windZ = 0f
            game.setSpin(x, y)
            val guide = game.preview(1f)
            game.windX = 8f
            assertSame("The guide does not solve the wind", guide, game.preview(1f))
            game.windX = 0f
            game.hit(1f)
            var peak = game.ball.y
            repeat(32 * 60) { game.update(1f / 60f); peak = max(peak, game.ball.y) }
            assertEquals(GolfState.READY, game.state)
            return Triple(game, peak, guide)
        }
        val low = shot(0f, -1f)
        val high = shot(0f, 1f)
        val fade = shot(1f, 0f)
        val draw = shot(-1f, 0f)
        assertTrue("Striking low launches higher", low.second > high.second)
        val lowRun = low.first.ball.z - low.third.landing!!.z
        val highRun = high.first.ball.z - high.third.landing!!.z
        assertTrue("Striking high runs further: $highRun vs $lowRun", highRun > lowRun + 3f)
        assertTrue("The guide shows the longer run", high.third.roll.last().z - high.third.landing!!.z > low.third.roll.last().z - low.third.landing!!.z)
        assertTrue("Right side curves right", fade.first.ball.x < -5f && fade.third.landing!!.x < -5f)
        assertTrue("Left side curves left", draw.first.ball.x > 5f)
    }

    /** A perfectly level green around the cup at (0, 300). */
    private val flatGreen = ClassicHole(1, 4, 300f, greenShape = GolfGreenShape(slopeX = 0f, slopeZ = 0f))

    /** Putts along +z from [back] metres short of the cup, offset sideways, arriving at [arrival] m/s. */
    private fun putt(arrival: Float, offset: Float = 0f, back: Float = 1.2f): ClassicGame {
        val hole = flatGreen
        val game = ClassicGame(hole)
        game.restore(GolfPoint(hole.cup.x + offset, 0f, hole.cup.z - back), 0)
        game.aimAngle = 0f
        val rolling = GolfBallPhysics.rolling(GolfLie.GREEN)
        // Flat-green distance whose speed after [back] metres is [arrival].
        val distance = back + arrival * arrival / (2f * rolling)
        game.hit(distance / GolfClub.PUTTER.carry)
        settle(game, 1f / 120f)
        return game
    }

    @Test fun aSlowBallDropsIntoTheRealCupAndRestsOnItsBottom() {
        for (arrival in listOf(.25f, .5f, .8f, 1.1f)) for (offset in listOf(0f, .015f, .03f)) {
            val game = putt(arrival, offset)
            assertEquals("arrival $arrival offset $offset", GolfState.HOLED, game.state)
            val cup = flatGreen.cup
            assertTrue("The ball rests below the lip", game.ball.y < cup.y - ClassicHole.BALL_RADIUS)
            assertTrue(game.ball.y >= cup.y - ClassicHole.CUP_DEPTH)
            assertTrue("…inside the cylinder", hypot(game.ball.x - cup.x, game.ball.z - cup.z) < ClassicHole.CUP_RADIUS)
        }
    }

    @Test fun aFastBallJumpsTheHoleAndOneWhoseCentreMissesTheOpeningRollsPast() {
        val fast = putt(3.5f)
        assertNotEquals(GolfState.HOLED, fast.state)
        assertTrue("It carried on past the cup", fast.ball.z > flatGreen.cup.z + .3f)
        val outside = putt(.5f, ClassicHole.CUP_RADIUS + .004f)
        assertNotEquals("The turf still carries a ball whose centre stays off the opening", GolfState.HOLED, outside.state)
        assertTrue(outside.ball.z > flatGreen.cup.z)
    }

    @Test fun theFlagstickStopsAChipAndADunkGoesIn() {
        val cup = flatGreen.cup
        val chip = ClassicGame(flatGreen).apply {
            windX = 0f; windZ = 0f
            throwBall(GolfPoint(cup.x, cup.y + .45f, cup.z - 1f), GolfPoint(0f, 0f, 3.5f))
            settle(this, 1f / 120f)
        }
        assertEquals("The pin kills the chip, which drops", GolfState.HOLED, chip.state)
        val dunk = ClassicGame(flatGreen).apply {
            windX = 0f; windZ = 0f
            throwBall(GolfPoint(cup.x + .02f, cup.y + .4f, cup.z - .05f), GolfPoint(0f, -1f, .3f))
            settle(this, 1f / 120f)
        }
        assertEquals(GolfState.HOLED, dunk.state)
    }

    @Test fun putterGuideFollowsTheRealRoll() {
        val hole = ClassicCourse.holes[0]
        for (offset in listOf(1f, 3f, 6f)) {
            val game = ClassicGame(hole)
            game.restore(GolfPoint(hole.cup.x, 0f, hole.cup.z - offset), 2)
            // Away from the cup: the guide must stop where the real putt stops, slopes included.
            game.aimAngle = Math.PI.toFloat()
            val guide = game.preview(.2f)
            assertEquals(5f, guide.carry, 1e-4f)
            assertTrue(guide.roll.isEmpty())
            val end = guide.landing!!
            game.hit(.2f); settle(game)
            assertEquals("offset $offset x", game.ball.x, end.x, .15f)
            assertEquals("offset $offset z", game.ball.z, end.z, .15f)
        }
        val game = ClassicGame(hole = ClassicCourse.holes[0])
        game.restore(GolfPoint(game.hole.cup.x, 0f, game.hole.cup.z - 1f), 2)
        game.aimAngle = 0f
        assertNotSame(game.preview(.2f), game.preview(.4f))
    }
}
