package com.Atom2Universe.app.games.glutton

import com.Atom2Universe.app.games.glutton.GluttonCatalog.Motion
import com.Atom2Universe.app.games.glutton.GluttonGame.Mode
import com.Atom2Universe.app.games.glutton.GluttonWorld.Cell
import com.Atom2Universe.app.games.glutton.GluttonWorld.Kind
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GluttonGameTest {
    private fun advance(g: GluttonGame, seconds: Float, fps: Int = 60) {
        repeat((seconds * fps).roundToInt()) { g.update(1f / fps) }
    }
    private fun prop(id: String) = GluttonCatalog.props.first { it.id == id }
    private fun item(g: GluttonGame, id: String, x: Float, y: Float, scale: Float = 1f): GluttonGame.Item {
        val prop = prop(id)
        return GluttonGame.Item(9000 + g.items.size, prop, x, y, 0f, prop.radius * scale, g.world.cellAt(x, y)).also { g.items += it }
    }
    /** Empties the world so a test can stage exactly what it needs. */
    private fun clear(g: GluttonGame) { g.items.clear() }
    private fun findEdge(g: GluttonGame, kind: Kind, sameLevel: Boolean? = null, ring: Int = 0): Triple<Cell, Int, Cell> {
        val r = g.world.rings[ring]
        for (cell in r.cells) for (side in 1..2) {
            val edge = g.world.edge(cell, side) ?: continue
            val to = g.world.neighbour(cell, side) ?: continue
            if (edge.kind != kind) continue
            if (sameLevel != null && (cell.level == to.level) != sameLevel) continue
            if (cell.inner || to.inner) continue
            return Triple(cell, side, to)
        }
        fail("no $kind edge in ring $ring"); throw IllegalStateException()
    }
    /** Walks from the middle of [from] straight across [side] for a while; true when the gobbler ended up in [to]. */
    private fun crosses(g: GluttonGame, from: Cell, side: Int, to: Cell, along: Float = -1f): Boolean {
        val cx = (from.x0 + from.x1) / 2; val cy = (from.y0 + from.y1) / 2
        val seconds = from.ring.cell / g.moveSpeed * 1.5f + 2f
        val edge = g.world.edge(from, side)
        val offset = if (along >= 0f) along else edge?.offset ?: .5f
        val sx = if (side == 1 || side == 3) cx else from.x0 + offset * from.ring.cell
        val sy = if (side == 0 || side == 2) cy else from.y0 + offset * from.ring.cell
        g.teleport(sx, sy)
        var t = 0f
        while (t < seconds) {
            g.steer(if (side == 1) 1f else if (side == 3) -1f else 0f, if (side == 2) 1f else if (side == 0) -1f else 0f)
            g.update(1f / 60); t += 1f / 60
            if (g.cell === to) return true
        }
        return false
    }
    private fun GluttonGame.teleport(x: Float, y: Float) {
        val field = javaClass.getDeclaredField("x").apply { isAccessible = true }; field.setFloat(this, x)
        val fieldY = javaClass.getDeclaredField("y").apply { isAccessible = true }; fieldY.setFloat(this, y)
        update(1f / 120)
    }

    @Test fun catalogueIsConsistent() {
        val ids = HashSet<String>()
        for (prop in GluttonCatalog.props) {
            assertTrue(prop.id, prop.metres > 0f)
            if (prop.moves) assertTrue(prop.id, prop.speed > 0f)
            assertTrue("duplicate ${prop.id}", ids.add(prop.id))
        }
        for (theme in GluttonCatalog.Theme.entries) {
            val props = GluttonCatalog.forTheme(theme)
            assertEquals(theme.name, props.indices.toList(), props.map { it.slot })
            assertTrue(theme.name, props.size <= GluttonCatalog.pageCount(theme) * GluttonCatalog.PAGE)
            assertEquals("$theme is not sorted by size", props.map { it.metres }, props.map { it.metres }.sorted())
            assertTrue(theme.name, props.first().metres < props.last().metres / 20f)
        }
        assertEquals(-1, GluttonCatalog.hourglass.slot)
        assertEquals(GluttonCatalog.Theme.SUBURB, GluttonCatalog.house.theme)
    }
    @Test fun worldIsReproducibleConnectedAndFurnished() {
        repeat(8) { seed ->
            val a = GluttonWorld(seed.toLong()); val b = GluttonWorld(seed.toLong())
            assertEquals(a.placements.map { Triple(it.prop.id, it.x, it.y) }, b.placements.map { Triple(it.prop.id, it.x, it.y) })
            assertTrue("seed=$seed items=${a.placements.size}", a.placements.size >= 1200)
            assertTrue("movers=${a.movers.size}", a.movers.size >= 24); assertTrue(a.hourglasses.size >= 30)
            for (p in a.placements + a.movers + a.hourglasses) {
                assertTrue(p.x > p.cell.x0 && p.x < p.cell.x1 && p.y > p.cell.y0 && p.y < p.cell.y1)
                assertSame(p.cell, a.cellAt(p.x, p.y))
            }
            for (ring in a.rings) {
                // Every cell can be reached by a tiny gobbler over open ground and stairs alone.
                val seen = HashSet<Cell>(); val queue = ArrayDeque<Cell>()
                val start = ring.cell(ring.count / 2, ring.count / 2); seen += start; queue += start
                while (queue.isNotEmpty()) {
                    val cell = queue.removeFirst()
                    for (side in 0..3) {
                        val to = a.neighbour(cell, side) ?: continue
                        val edge = a.edge(cell, side)
                        if (edge != null && edge.kind == Kind.CLIFF) continue
                        if (edge != null) assertNotEquals(cell.level, to.level)
                        if (seen.add(to)) queue += to
                    }
                }
                assertEquals("ring ${ring.index} seed $seed", ring.cells.size, seen.size)
                assertEquals(2, ring.exits.size); assertNotEquals(ring.exits[0].first, ring.exits[1].first)
                val kinds = ring.cells.flatMap { c -> (1..2).mapNotNull { a.edge(c, it)?.kind } }.toSet()
                assertTrue("ring ${ring.index} lacks variety: $kinds", kinds.containsAll(listOf(Kind.STAIRS, Kind.CLIFF)))
                // Broad terraces, not a checkerboard: most neighbours share a level.
                val pairs = ring.cells.flatMap { c -> (1..2).mapNotNull { a.neighbour(c, it)?.let { n -> c.level == n.level } } }
                assertTrue("ring ${ring.index} seed $seed: ${pairs.count { it }}/${pairs.size} flat", pairs.count { it } * 5 >= pairs.size * 2)
                assertTrue(ring.cells.any { it.level == ring.levels - 1 } && ring.cells.any { it.level == 0 })
            }
            assertEquals(a.rings[0].cell(2, 2), a.cellAt(0f, 0f))
            assertTrue(a.rings[1].cell(3, 3).inner)
        }
    }
    @Test fun foodIsSizedForTheTerraceAndThereIsAlwaysSomethingToEat() {
        val g = GluttonGame(1)
        assertTrue(g.items.count { g.canEat(it) && !it.moves } >= 30)
        for (ring in g.world.rings) for (level in 0 until ring.levels) {
            val cells = ring.cells.filter { it.level == level }
            val radii = g.world.placements.filter { it.cell in cells }.map { it.radius }
            val tier = cells.first().tier
            // The top terrace of the city holds things so big that few fit in a cell: a quarter is enough there.
            assertTrue("ring ${ring.index} level $level", radii.count { it in tier * .3f..tier * 1.6f } >= radii.size / 4)
        }
    }
    @Test fun cliffsAreOneWayUntilYouCanClimbThem() {
        val g = GluttonGame(4); clear(g)
        val (a, side, b) = findEdge(g, Kind.CLIFF)
        val (low, high, downSide, upSide) = if (a.level < b.level) listOf(a, b, (side + 2) % 4, side) else listOf(b, a, side, (side + 2) % 4)
        val rise = (high as Cell).height - (low as Cell).height
        g.forceRadius(.02f)
        assertTrue(crosses(g, high, downSide as Int, low, along = .5f))
        assertFalse(crosses(g, low, upSide as Int, high, along = .5f))
        g.forceRadius(rise + .005f)
        assertTrue(crosses(g, low, upSide, high, along = .5f))
    }
    @Test fun theHouseOpensOntoTheSuburbThroughItsDoorsOrOnceYouAreTall() {
        val g = GluttonGame(5); clear(g)
        val room = g.world.rings[0]
        val (exitSide, offset) = room.exits[0]
        val along = -room.half + offset * room.side
        val col = if (exitSide == 1) room.count - 1 else if (exitSide == 3) 0 else ((along + room.half) / room.cell).toInt().coerceIn(0, room.count - 1)
        val row = if (exitSide == 2) room.count - 1 else if (exitSide == 0) 0 else ((along + room.half) / room.cell).toInt().coerceIn(0, room.count - 1)
        val cell = room.cell(col, row)
        val garden = g.world.rings[1].cell(3, 3)
        g.forceRadius(.02f)
        val localOffset = ((along - (if (exitSide == 0 || exitSide == 2) cell.x0 else cell.y0)) / room.cell)
        assertTrue(crosses(g, cell, exitSide, garden, along = localOffset))
        val (wallSide, wallOffset) = if (exitSide == 0 || exitSide == 2) exitSide to (localOffset + .4f) % 1f else exitSide to (localOffset + .4f) % 1f
        g.forceRadius(.02f)
        assertFalse(crosses(g, cell, wallSide, garden, along = wallOffset))
        g.forceRadius(g.world.rings[1].base + .005f)
        assertTrue(crosses(g, cell, wallSide, garden, along = wallOffset))
    }
    @Test fun firstBitesAreImmediateAndGrowthIsCapped() {
        val g = GluttonGame(6)
        val start = g.radius
        for (frame in 0 until 8 * 60) {
            val food = g.nearestFood() ?: break
            g.steer((food.x - g.x) * 1000f, (food.y - g.y) * 1000f); g.update(1f / 60)
            if (g.eaten >= 5) break
        }
        assertTrue("eaten=${g.eaten}", g.eaten >= 5)
        assertTrue(g.radius > start && g.radius < start * 1.5f)
        assertEquals(g.eaten, g.stuck.size)
        val cookie = item(g, "cookie", g.x, g.y)
        assertEquals(GluttonGame.CAP * g.area, g.growth(cookie), 1e-6f)
        val crumb = item(g, "sesame_seed", g.x, g.y)
        assertTrue(g.growth(crumb) > 0f && g.growth(crumb) < g.growth(cookie) / 5f)
    }
    @Test fun onlySmallerThingsAreEdibleAndBiggerOnesBlock() {
        val g = GluttonGame(7); clear(g)
        val chair = item(g, "dining_chair", g.x + .45f, g.y)
        assertFalse(g.canEat(chair))
        g.steer(1f, 0f); advance(g, 3f)
        assertTrue(chair.active); assertEquals(0, g.eaten)
        assertTrue(hypot(g.x - chair.x, g.y - chair.y) >= g.radius * .8f + chair.radius * .78f - .002f)
        g.forceRadius(chair.radius / GluttonGame.EDIBLE + .01f)
        chair.x = g.x; chair.y = g.y; g.update(1f / 60); assertFalse(chair.active)
    }
    @Test fun aBumpKnocksStuckThingsLooseAndTheyCanBeEatenAgain() {
        val g = GluttonGame(8); clear(g)
        repeat(6) { item(g, "cookie", g.x, g.y, scale = .5f) }
        g.forceRadius(.05f); g.update(1f / 60)
        assertEquals(6, g.stuck.size); val grown = g.radius; val eaten = g.eaten
        advance(g, 1.2f)
        val cat = item(g, "robot_vacuum", g.x + g.radius + .1f, g.y)
        var knocked = false
        for (frame in 0 until 180) {
            g.update(1f / 60)
            if (g.events.any { it.effect == GluttonGame.Effect.KNOCK }) { knocked = true; break }
        }
        assertTrue(knocked)
        assertEquals(3, g.stuck.size); assertTrue(g.radius < grown)
        val loose = g.items.filter { it.active && it.prop.id == "cookie" }
        assertEquals(3, loose.size)
        assertTrue(loose.all { it.cooldown > 0f && !g.canEat(it) })
        assertTrue(cat.rest > 0f)
        g.items.remove(cat); advance(g, 2f)
        for (item in loose) if (item.active) { item.x = g.x; item.y = g.y }
        g.update(1f / 60)
        assertEquals(eaten + 3, g.eaten); assertEquals(6, g.stuck.size)
    }
    @Test fun chasersHuntWhileYouAreSmallAndBecomeLunchOnceYouOutgrowThem() {
        val g = GluttonGame(9); clear(g)
        g.forceRadius(.05f)
        val vacuum = item(g, "robot_vacuum", g.x + .5f, g.y)
        val before = hypot(vacuum.x - g.x, vacuum.y - g.y)
        advance(g, 1f)
        assertTrue(vacuum.alert); assertTrue(hypot(vacuum.x - g.x, vacuum.y - g.y) < before)
        g.forceRadius(vacuum.radius / GluttonGame.EDIBLE + .02f)
        vacuum.rest = 0f; vacuum.x = g.x + g.radius * 4f; vacuum.y = g.y; vacuum.vx = 0f; vacuum.vy = 0f
        g.steer(0f, 0f); g.update(1f / 60); g.update(1f / 60)
        assertFalse(vacuum.alert); assertTrue(g.canEat(vacuum))
        vacuum.x = g.x; vacuum.y = g.y; g.update(1f / 60); assertFalse(vacuum.active)
    }
    @Test fun swallowingTheLastThingEndsTheRunAsClearedAndGiantsGrowSlower() {
        val g = GluttonGame(16); clear(g); g.forceFoodLeft(1)
        val cookie = item(g, "cookie", g.x, g.y, scale = .2f)
        g.forceRadius(.05f)
        assertEquals(1f, g.damping, 1e-6f)
        g.update(1f / 60)
        assertFalse(cookie.active); assertTrue(g.finished); assertTrue(g.cleared)
        assertEquals(1, g.events.count { it.effect == GluttonGame.Effect.FINISH })
        val giant = GluttonGame(17); giant.forceRadius(6f)
        assertEquals((.6f / 6f).pow(GluttonGame.DAMP), giant.damping, 1e-6f)
        val bite = item(giant, "cottage", giant.x, giant.y)
        assertEquals(GluttonGame.CAP * giant.area * giant.damping, giant.growth(bite), 1e-3f)
        assertFalse(giant.cleared)
    }
    @Test fun yourOwnHouseIsHiddenInsideThenSwallowedOnceYouAreBigEnough() {
        val g = GluttonGame(15); clear(g); g.items += g.house
        assertFalse(g.house.active); assertFalse(g.houseEaten)
        val suburb = g.world.rings[1]
        g.forceRadius(1f); g.teleport(0f, suburb.half - 10f)
        assertTrue(g.house.active); assertFalse(g.canEat(g.house)); assertFalse(g.houseEaten)
        g.forceRadius(g.house.radius / GluttonGame.EDIBLE + .1f)
        g.teleport(0f, g.house.radius)
        advance(g, 1f)
        assertFalse(g.house.active); assertTrue(g.houseEaten)
    }
    @Test fun creaturesStayInTheirCellAndPatrolsKeepMoving() {
        val g = GluttonGame(10); clear(g)
        val mover = g.world.movers.first { it.prop.motion == Motion.PATROL }
        val car = item(g, mover.prop.id, mover.x, mover.y)
        car.heading = 0f
        advance(g, 20f)
        assertTrue(car.x > car.cell!!.x0 && car.x < car.cell.x1 && car.y > car.cell.y0 && car.y < car.cell.y1)
        assertTrue(abs(car.vx) > 0f)
    }
    @Test fun burstIsASingleShockwaveThatStunsNearbyCreatures() {
        val g = GluttonGame(11); clear(g)
        g.forceRadius(.05f)
        val cat = item(g, "robot_vacuum", g.x + g.suctionRadius, g.y)
        assertTrue(g.activateBurst()); assertFalse(g.activateBurst())
        g.update(1f / 60)
        assertEquals(1, g.events.count { it.effect == GluttonGame.Effect.BURST })
        assertTrue(cat.stunned > 0f)
        advance(g, 2.2f); assertEquals(0f, cat.stunned, .001f)
    }
    @Test fun threeMinutesIsFixedWhileTheHungryClockFeedsOnBites() {
        val chrono = GluttonGame(12, Mode.CHRONO)
        assertEquals(180f, chrono.remaining, .001f)
        assertEquals(0, chrono.items.count { it.prop.bonus })
        val hourglass = item(chrono, "hourglass", chrono.x, chrono.y, scale = .01f)
        val cookie = item(chrono, "cookie", chrono.x, chrono.y, scale = .1f)
        chrono.update(1f / 60)
        assertFalse(hourglass.active); assertFalse(cookie.active)
        assertEquals(180f - 1f / 60, chrono.remaining, .01f)
        val frenzy = GluttonGame(12, Mode.FRINGALE)
        assertEquals(45f, frenzy.remaining, .001f)
        assertTrue(frenzy.items.count { it.prop.bonus } >= 30)
        clear(frenzy)
        item(frenzy, "hourglass", frenzy.x, frenzy.y, scale = .01f)
        item(frenzy, "cookie", frenzy.x, frenzy.y, scale = .1f)
        frenzy.update(1f / 60)
        assertEquals(45f + GluttonGame.HOURGLASS + GluttonGame.BITE_TIME - 1f / 60, frenzy.remaining, .01f)
        assertEquals(GluttonGame.HOURGLASS + GluttonGame.BITE_TIME, frenzy.bonusTime, .001f)
        assertEquals(1, frenzy.events.count { it.effect == GluttonGame.Effect.BONUS })
    }
    @Test fun movementAndCollectionsAreIndependentOfRefreshRate() {
        val results = intArrayOf(60, 120).map { fps ->
            val g = GluttonGame(13); g.items.removeAll { it.moves }
            g.steer(1f, .3f); advance(g, 3f, fps); Triple(g.x, g.y, g.eaten)
        }
        assertEquals(results[0].first, results[1].first, .002f); assertEquals(results[0].second, results[1].second, .002f)
        assertEquals(results[0].third, results[1].third)
    }
    @Test fun releaseInvalidInputTimeoutAndMemoryAreSafe() {
        val g = GluttonGame(14)
        g.steer(Float.NaN, 1f); g.update(1f / 60); assertTrue(g.x.isFinite())
        g.update(Float.NaN); g.update(-1f); g.update(Float.POSITIVE_INFINITY)
        var finishes = 0
        repeat(((Mode.CHRONO.budget + 2f) * 60).roundToInt()) { g.update(1f / 60); finishes += g.events.count { it.effect == GluttonGame.Effect.FINISH } }
        assertTrue(g.finished); assertEquals(1, finishes)
        assertTrue(g.radius >= GluttonGame.START_RADIUS)
    }
    @Test fun aRouteFindingPlayerGrowsThroughTheRingsInBothModes() {
        val report = StringBuilder()
        val failures = ArrayList<String>()
        for (mode in Mode.entries) for (seed in 0 until 3) {
            val g = GluttonGame(200L + seed, mode)
            val t = GluttonRobot.play(g, 600f, report)
            if (g.diameterMetres < 2f) failures += "$mode seed=$seed stayed at ${g.diameterMetres} m"
            if (g.diameterMetres > 2000f) failures += "$mode seed=$seed exploded to ${g.diameterMetres} m"
            if (mode == Mode.FRINGALE && (t < 60f || t > 900f)) failures += "$mode seed=$seed lasted ${t}s"
            if (mode == Mode.CHRONO && !g.finished) failures += "$mode seed=$seed did not finish"
        }
        println(report)
        assertTrue(report.toString() + failures.joinToString("\n"), failures.isEmpty())
    }
}
