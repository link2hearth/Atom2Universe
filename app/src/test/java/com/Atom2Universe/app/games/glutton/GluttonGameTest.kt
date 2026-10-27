package com.Atom2Universe.app.games.glutton

import com.Atom2Universe.app.games.glutton.GluttonCatalog.Motion
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Theme
import com.Atom2Universe.app.games.glutton.GluttonGame.Mode
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

class GluttonGameTest {
    private fun advance(g: GluttonGame, seconds: Float, fps: Int = 60) {
        repeat((seconds * fps).roundToInt()) { g.update(1f / fps) }
    }
    private fun prop(id: String) = GluttonCatalog.byId.getValue(id)

    @Test fun catalogueIsConsistentAndEveryPageIsDrawn() {
        val ids = HashSet<String>()
        for (prop in GluttonCatalog.props) {
            assertTrue(prop.id, prop.metres > 0.0 && prop.spread >= 1.0)
            if (prop.moves && prop.motion != Motion.ORBIT) assertTrue(prop.id, prop.speed > 0.0)
            assertTrue("duplicate ${prop.id}", ids.add(prop.id))
        }
        val assets = File("src/main/assets/glutton")
        for (theme in Theme.entries) {
            val props = GluttonCatalog.forTheme(theme)
            assertEquals(theme.name, props.indices.toList(), props.map { it.slot })
            assertEquals("$theme is not sorted by size", props.map { it.metres }, props.map { it.metres }.sorted())
            assertTrue(theme.name, props.first().metres < props.last().metres / 20)
            for (page in 0 until GluttonCatalog.pageCount(theme)) assertTrue("${theme}_$page", File(assets, "${theme.name.lowercase()}_$page.webp").exists())
        }
        for (ground in GluttonWorld.Ground.entries) assertTrue(ground.name, File(assets, "ground_${ground.name.lowercase()}.webp").exists())
        assertEquals(-1, GluttonCatalog.hourglass.slot)
        assertEquals(Theme.SUBURB, GluttonCatalog.house.theme)
    }

    @Test fun everyRingIsReproducibleLaidOutAndFed() {
        val a = GluttonWorld(3); val b = GluttonWorld(3)
        a.ensure(20); b.ensure(20)
        assertEquals(a.placements.map { Triple(it.prop.id, it.x, it.y) }, b.placements.map { Triple(it.prop.id, it.x, it.y) })
        for (ring in a.rings) {
            assertTrue("ring ${ring.index}: ${ring.placements.size}", ring.placements.size >= 100)
            assertTrue("ring ${ring.index} hourglasses", ring.hourglasses.size >= 3)
            for (p in ring.placements) {
                assertTrue(ring.contains(p.x, p.y))
                if (ring.index > 0) assertTrue("${p.prop.id} inside the inner ring", max(abs(p.x), abs(p.y)) >= ring.inner)
            }
            // Food for the gobbler the ring is meant for, at every stage of it.
            val sizes = ring.placements.map { it.radius }
            for (f in listOf(.1, .5, .9)) {
                val tier = ring.from * (ring.to / ring.from).pow(f)
                assertTrue("ring ${ring.index} at $f: nothing near ${"%.3g".format(tier)}", sizes.count { it in tier * .2..tier * .8 } >= 15)
            }
        }
        // Nothing overlaps much: things stand side by side, not on top of each other.
        for (ring in a.rings.take(4)) {
            val list = ring.placements
            for (i in list.indices step 7) for (j in i + 1 until min(list.size, i + 60)) {
                val p = list[i]; val q = list[j]
                assertTrue("${p.prop.id} on ${q.prop.id}", hypot(p.x - q.x, p.y - q.y) > (p.radius + q.radius) * .9)
            }
        }
        // Your start is clear, and the town has its streets with traffic.
        assertTrue(a.rings[0].placements.none { it.radius > .02 && hypot(it.x, it.y) < it.radius + .2 })
        assertTrue(a.rings[2].lanes.size > 20 && a.rings[2].movers.count { it.lane != null } >= 6)
        assertTrue(a.rings[6].movers.count { it.orbit != null } >= 8)
    }

    @Test fun everyRoomOfTheHouseAndTheGardenAreReachableThroughTheDoorways() {
        repeat(6) { seed ->
            val world = GluttonWorld(seed.toLong())
            val house = world.rings[0]
            assertEquals(2, house.exits.size)
            val step = .1; val n = 200; val origin = -10.0; val r = .05
            fun free(x: Double, y: Double) = house.walls.none { w ->
                val along = (if (w.horizontal) x else y).coerceIn(w.from, w.until)
                hypot(x - (if (w.horizontal) along else w.line), y - (if (w.horizontal) w.line else along)) < r * .85 + w.thickness
            }
            val seen = BooleanArray(n * n); val queue = ArrayDeque<Int>()
            val start = ((0 - origin) / step).toInt().let { it + it * n }
            seen[start] = true; queue += start
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst(); val c = i % n; val w = i / n
                for ((dc, dw) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val nc = c + dc; val nw = w + dw
                    if (nc !in 0 until n || nw !in 0 until n) continue
                    val j = nc + nw * n
                    if (seen[j] || !free(origin + (nc + .5) * step, origin + (nw + .5) * step)) continue
                    seen[j] = true; queue += j
                }
            }
            fun reached(x: Double, y: Double) = seen[((x - origin) / step).toInt() + ((y - origin) / step).toInt() * n]
            for (patch in house.patches) assertTrue("seed $seed room at ${patch.x0},${patch.y0}", reached((patch.x0 + patch.x1) / 2 + .03, (patch.y0 + patch.y1) / 2 + .03))
            assertTrue("seed $seed: no way out", reached(-9.5, -9.5))
        }
    }

    @Test fun firstBitesAreImmediateAndDigestionPacesGrowth() {
        val g = GluttonGame(6)
        val start = g.radius
        for (frame in 0 until 8 * 60) {
            val food = g.nearestFood() ?: break
            g.steer((food.x - g.x) * 1000, (food.y - g.y) * 1000); g.update(1f / 60)
            if (g.eaten >= 5) break
        }
        assertTrue("eaten=${g.eaten}", g.eaten >= 5)
        assertTrue(g.radius > start && g.radius < start * 1.5)
        assertEquals(g.eaten, g.recent.size)
        val cookie = g.add(prop("cookie"), g.x + 1, g.y)
        assertEquals(GluttonGame.CAP * g.area, g.growth(cookie), 1e-12)
        val crumb = g.add(prop("sesame_seed"), g.x + 1, g.y)
        assertTrue(g.growth(crumb) > 0.0 && g.growth(crumb) < g.growth(cookie) / 5)
        // A whole street gulped at once still takes its time to digest.
        val giant = GluttonGame(7); giant.clearItems(); giant.forceRadius(1.0)
        repeat(60) { giant.add(prop("garden_gnome"), giant.x, giant.y) }
        val area = giant.area
        advance(giant, 1f)
        assertEquals(60, giant.eaten)
        assertTrue(giant.area <= area * exp(GluttonGame.DIGEST) * 1.01)
        assertTrue(giant.stomach > 0.0)
    }

    @Test fun onlySmallerThingsAreEdibleAndBiggerOnesBlock() {
        val g = GluttonGame(7); g.clearItems()
        val chair = g.add(prop("dining_chair"), g.x + .45, g.y)
        assertFalse(g.canEat(chair))
        g.steer(1.0, 0.0); advance(g, 3f)
        assertTrue(chair.active); assertEquals(0, g.eaten)
        assertTrue(hypot(g.x - chair.x, g.y - chair.y) >= g.radius * .8 + chair.radius * .78 - .002)
        g.forceRadius(chair.radius / GluttonGame.EDIBLE + .01)
        g.teleport(chair.x, chair.y); assertFalse(chair.active)
    }

    @Test fun aBumpMakesYouSpitOutYourLastBitesAndTheyCanBeEatenAgain() {
        val g = GluttonGame(8); g.clearItems()
        g.forceRadius(.05)
        repeat(6) { g.add(prop("cookie"), g.x, g.y, scale = .5) }
        g.update(1f / 60)
        assertEquals(6, g.recent.size); val eaten = g.eaten
        advance(g, 2f); val grown = g.radius
        val vacuum = g.add(prop("robot_vacuum"), g.x + g.radius + .1, g.y)
        var knocked = false
        for (frame in 0 until 180) {
            g.update(1f / 60)
            if (g.events.any { it.effect == GluttonGame.Effect.KNOCK }) { knocked = true; break }
        }
        assertTrue(knocked)
        assertEquals(3, g.recent.size); assertTrue(g.radius < grown)
        assertEquals(3, g.loose.size)
        assertTrue(g.loose.all { it.cooldown > 0f && !g.canEat(it) })
        assertTrue(vacuum.rest > 0f)
        vacuum.active = false; advance(g, 2f)
        assertEquals(0, g.loose.size)
        val back = g.items.filter { it.prop.id == "cookie" }
        assertEquals(3, back.size)
        for (item in back) { g.teleport(item.x, item.y) }
        assertEquals(eaten + 3, g.eaten)
    }

    @Test fun chasersHuntWhileYouAreSmallAndBecomeLunchOnceYouOutgrowThem() {
        val g = GluttonGame(9); g.clearItems()
        g.forceRadius(.05)
        val vacuum = g.add(prop("robot_vacuum"), g.x + .5, g.y)
        val before = hypot(vacuum.x - g.x, vacuum.y - g.y)
        advance(g, 1f)
        assertTrue(vacuum.alert); assertTrue(hypot(vacuum.x - g.x, vacuum.y - g.y) < before)
        g.forceRadius(vacuum.radius / GluttonGame.EDIBLE + .02)
        vacuum.rest = 0f; vacuum.x = g.x + g.radius * 4; vacuum.y = g.y; vacuum.vx = 0.0; vacuum.vy = 0.0
        g.steer(0.0, 0.0); g.update(1f / 60); g.update(1f / 60)
        assertFalse(vacuum.alert); assertTrue(g.canEat(vacuum))
        vacuum.x = g.x; vacuum.y = g.y; g.update(1f / 60); assertFalse(vacuum.active)
    }

    @Test fun yourHouseIsHiddenInsideThenCoversItsRoomsAndIsSwallowedOnceYouAreBigEnough() {
        val g = GluttonGame(15)
        assertFalse(g.house.active); assertFalse(g.houseEaten)
        assertNotNull(g.layers[0])
        g.forceRadius(1.0); g.teleport(0.0, 40.0)
        assertTrue(g.house.active); assertTrue(g.covered[0]); assertNull(g.layers[0])
        assertFalse(g.canEat(g.house)); assertFalse(g.houseEaten)
        assertTrue(g.world.rings[2].built)
        g.forceRadius(g.house.radius / GluttonGame.EDIBLE + .1)
        g.teleport(0.0, g.house.radius * .2)
        advance(g, .5f)
        assertFalse(g.house.active); assertTrue(g.houseEaten)
    }

    @Test fun leavingTheEarthLeavesTheEarthBehindAndTheJourneyEndsWithTheUniverse() {
        val g = GluttonGame(16)
        val space = g.world.rings[6]
        g.forceRadius(2e6); g.teleport(space.inner * 1.5, 0.0)
        val earth = g.covers.single { it.prop.id == "earth" }
        assertTrue(earth.active); assertFalse(g.house.active)
        for (i in 0..5) { assertTrue(g.covered[i]); assertNull(g.layers[i]) }
        assertTrue(g.items.any { it.orbit != null })
        assertFalse(g.finished)
        val last = g.world.rings.last()
        g.forceRadius(last.to * 1.01); g.teleport(last.inner * 2, 0.0)
        // The journey only ends once the last ring is eaten clean; the last tenth then falls in by itself.
        assertTrue(g.finale); assertFalse(g.finished); assertTrue(g.finalTotal > 100)
        val things = g.layers.last()!!.let { it.statics + it.movers }.filter { it.active }
        things.drop(things.size / 4).forEach { it.active = false }
        advance(g, 2f)
        assertFalse(g.finished); assertFalse(g.collapsing); assertTrue(g.finaleProgress in .7f..<.9f)
        things.drop(things.size / 20).forEach { it.active = false }
        var collapses = 0
        repeat(5 * 60) { g.update(1f / 60); collapses += g.events.count { it.effect == GluttonGame.Effect.COLLAPSE } }
        assertEquals(1, collapses)
        assertTrue(g.finished); assertTrue(g.cleared); assertEquals(0, g.finalLeft)
    }

    @Test fun vehiclesKeepToTheirRoadsAndPlanetsToTheirOrbits() {
        val g = GluttonGame(10)
        g.forceRadius(.5); g.teleport(0.0, 30.0)
        val car = g.layers[1]!!.movers.first { it.lane != null }
        val out = DoubleArray(4)
        advance(g, 10f)
        car.lane!!.at(car.laneAt, out)
        assertTrue(hypot(car.x - out[0], car.y - out[1]) < car.radius * 1.5)
        val space = GluttonGame(11); space.forceRadius(2e6); space.teleport(space.world.rings[6].inner * 1.5, 0.0)
        val mercury = space.layers[6]!!.movers.first { it.prop.id == "mercury" }
        advance(space, .1f)
        val (x0, y0) = mercury.x to mercury.y
        advance(space, 5f)
        val orbit = mercury.orbit!!
        assertTrue(hypot(mercury.x - x0, mercury.y - y0) > orbit.radius * .2)
        assertEquals(orbit.radius, hypot(mercury.x - orbit.cx, mercury.y - orbit.cy), orbit.radius * 1e-6)
    }

    @Test fun burstIsASingleShockwaveThatStunsNearbyMovers() {
        val g = GluttonGame(11); g.clearItems()
        g.forceRadius(.05)
        val vacuum = g.add(prop("robot_vacuum"), g.x + g.suctionRadius, g.y)
        assertTrue(g.activateBurst()); assertFalse(g.activateBurst())
        g.update(1f / 60)
        assertEquals(1, g.events.count { it.effect == GluttonGame.Effect.BURST })
        assertTrue(vacuum.stunned > 0f)
        advance(g, 2.2f); assertEquals(0f, vacuum.stunned, .001f)
    }

    @Test fun theJourneyHasNoClockWhileTheHungryClockFeedsOnBites() {
        val voyage = GluttonGame(12, Mode.VOYAGE)
        assertEquals(0, voyage.items.count { it.prop.bonus })
        advance(voyage, 5f)
        assertFalse(voyage.finished); assertEquals(5f, voyage.elapsed, .02f)
        val hungry = GluttonGame(12, Mode.FRINGALE)
        assertEquals(120f, hungry.remaining, .001f)
        assertTrue(hungry.items.count { it.prop.bonus } >= 5)
        hungry.clearItems()
        hungry.add(GluttonCatalog.hourglass, hungry.x, hungry.y, scale = .01)
        hungry.add(prop("cookie"), hungry.x, hungry.y, scale = .1)
        hungry.update(1f / 60)
        assertEquals(1, hungry.events.count { it.effect == GluttonGame.Effect.BONUS })
        // The hourglass at once, the cookie only as it is digested, and never as fast as time passes.
        assertEquals(GluttonGame.FRINGALE_MAX - 1f / 60, hungry.remaining, .02f)
        val before = hungry.remaining
        advance(hungry, 2f)
        assertTrue(hungry.bonusTime > GluttonGame.HOURGLASS)
        assertTrue(hungry.remaining < before)
        var finishes = 0
        repeat(200 * 60) { hungry.update(1f / 60); finishes += hungry.events.count { it.effect == GluttonGame.Effect.FINISH } }
        assertTrue(hungry.finished); assertEquals(1, finishes); assertFalse(hungry.cleared)
    }

    @Test fun movementAndCollectionsAreIndependentOfRefreshRate() {
        val results = intArrayOf(60, 120).map { fps ->
            val g = GluttonGame(13); for (layer in g.layers) layer?.movers?.clear()
            g.steer(1.0, .3); advance(g, 3f, fps); Triple(g.x, g.y, g.eaten)
        }
        assertEquals(results[0].first, results[1].first, .002); assertEquals(results[0].second, results[1].second, .002)
        assertEquals(results[0].third, results[1].third)
    }

    @Test fun invalidInputIsSafe() {
        val g = GluttonGame(14)
        g.steer(Double.NaN, 1.0); g.update(1f / 60); assertTrue(g.x.isFinite())
        g.update(Float.NaN); g.update(-1f); g.update(Float.POSITIVE_INFINITY)
        assertTrue(g.radius >= GluttonGame.START_RADIUS)
    }

    @Test fun aRouteFindingPlayerTravelsTheWholeJourney() {
        val report = StringBuilder()
        val failures = ArrayList<String>()
        for (seed in 0 until 2) {
            val g = GluttonGame(200L + seed, Mode.VOYAGE)
            val rings = FloatArray(g.world.rings.size)
            val t = GluttonRobot.play(g, 2400f, report, rings)
            // Measured 27/10/2026: out of the house at 58 to 101 s, the universe gone at 784 to 829 s.
            if (rings[1] < 0f || rings[1] > 150f) failures += "seed=$seed left the house at ${rings[1]}s"
            if (!g.cleared || t > 1300f) failures += "seed=$seed: universe not eaten in time (${t}s, ring ${g.ringReached})"
            if (t < 450f) failures += "seed=$seed ate the universe in ${t}s: far too fast"
        }
        // The hungry clock always runs out: a short game measured by how far you got.
        for (seed in 300L..301L) {
            val hungry = GluttonGame(seed, Mode.FRINGALE)
            val t = GluttonRobot.play(hungry, 2400f, report)
            if (hungry.cleared || t > 900f || t < 120f) failures += "FRINGALE seed=$seed lasted ${t}s, cleared=${hungry.cleared}"
        }
        println(report)
        File("build/outputs/glutton").apply { mkdirs() }.resolve("robot.txt").writeText(report.toString())
        assertTrue(report.toString() + failures.joinToString("\n"), failures.isEmpty())
    }
}
