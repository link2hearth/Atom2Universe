package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Economy and atomicity of user-driven builder tools; no renderer or wall-clock waits. */
class HamletBuilderTest {
    private var time = 1_000_000_000L
    private fun game(sandbox: Boolean = false) = HamletGame({ time }, Random(34)).apply {
        startNew(sandbox); established(); xp = 100_000; coins = 100_000; gems = 100; buildHistory.clear()
    }
    private fun spot(g: HamletGame, id: String): Pair<Int, Int> {
        val origin = g.realmOrigin()
        for (y in origin until origin + g.realmSize()) for (x in origin until origin + g.realmSize())
            if (g.canPlace(id, x, y, false)) return x to y
        error("No space for $id")
    }
    private fun buy(g: HamletGame, id: String): Obj { val (x, y) = spot(g, id); return g.buy(id, x, y, false)!! }
    private fun empty(g: HamletGame): Pair<Int, Int> {
        val o = g.realmOrigin()
        for (y in o until o + g.realmSize()) for (x in o until o + g.realmSize())
            if (g.objAt(x, y) == null && g.pathAt(x, y) == 0) return x to y
        error("No grass")
    }

    @Test fun `undo placement retains unrelated harvest and its work bonus`() {
        val g = game(); val coins = g.coins; val xp = g.xp
        val purchased = buy(g, "gnome")
        val field = g.objs.values.first { it.t == "field" }
        assertTrue(g.plant(field, "corn")); time += 46_000; assertTrue(g.harvest(field))
        val earned = g.coins + Hamlet.byId.getValue("gnome").cost
        val work = g.workSeconds; val harvestedXp = g.xp - Hamlet.byId.getValue("gnome").xp
        assertTrue(g.buildHistory.undo())
        assertNull(g.objs[purchased.id]); assertEquals(earned, g.coins); assertEquals(harvestedXp, g.xp)
        assertTrue(g.coins > coins); assertTrue(g.xp > xp); assertEquals(work, g.workSeconds)
        assertEquals(1L, g.stats["harvested"]); assertEquals(0L, g.stats["built_gnome"])
    }

    @Test fun `repeated buy and undo cannot farm coins xp or level gems`() {
        val g = game(); g.xp = 43; g.gems = 2
        val coins = g.coins
        repeat(12) { buy(g, "field"); assertEquals(2, g.level()); assertTrue(g.buildHistory.undo()) }
        assertEquals(coins, g.coins); assertEquals(43L, g.xp); assertEquals(2, g.gems)
        assertEquals(0L, g.stats["built"])
    }

    @Test fun `gem purchases refund actual gems while sandbox refunds nothing`() {
        val g = game(); g.xp = 6_000_000
        val gemDef = Hamlet.defs.first { it.gems > 0 && !it.onWater && !it.shore && it.cat != Cat.HALL }
        val coins = g.coins; val gems = g.gems
        HamletIndustry.building(gemDef).resources.forEach { (r, n) -> g.adventure.add(r, n) }
        val materials = g.adventure.bag.clone()
        buy(g, gemDef.id); assertTrue(g.buildHistory.undo())
        assertEquals(coins, g.coins); assertEquals(gems, g.gems); assertArrayEquals(materials, g.adventure.bag)
        val free = game(true); val before = free.coins
        buy(free, "field"); assertTrue(free.buildHistory.undo()); assertEquals(before, free.coins)
    }

    @Test fun `undo cannot erase an exploited purchase or a claimed milestone`() {
        val g = game(); val field = buy(g, "field")
        assertTrue(g.plant(field, "corn")); val coins = g.coins
        assertFalse(g.buildHistory.undo()); assertSame(field, g.objs[field.id]); assertEquals(coins, g.coins)
        g.buildHistory.clear(); buy(g, "gnome"); g.quest++
        assertFalse(g.buildHistory.undo())
    }

    @Test fun `undo refuses removing a later independently earned level`() {
        val g = game(); g.xp = 124
        buy(g, "field") // 129 XP, still level 2.
        g.addXp(2) // Level 3 belongs to another activity.
        val xp = g.xp; val gems = g.gems; val coins = g.coins
        assertFalse(g.buildHistory.undo())
        assertEquals(xp, g.xp); assertEquals(gems, g.gems); assertEquals(coins, g.coins)
    }

    @Test fun `sale undo requires unspent proceeds and preserves object state`() {
        val g = game(); val obj = g.objs.values.first { it.t == "cottage" }
        obj.tier = 2; obj.t0 = time - 5000
        assertTrue(g.sell(obj)); val proceeds = g.sellValue(obj)
        g.coins = 0
        assertFalse(g.buildHistory.undo()); assertNull(g.objs[obj.id]); assertEquals(0L, g.coins)
        g.coins = proceeds.toLong()
        assertTrue(g.buildHistory.undo()); assertSame(obj, g.objs[obj.id]); assertEquals(0L, g.coins)
        assertEquals(2, obj.tier); assertEquals(time - 5000, obj.t0)
    }

    @Test fun `move undo keeps harvests performed after the move`() {
        val g = game(); val obj = g.objs.values.first { it.t == "field" }
        val x = obj.x; val y = obj.y; val (toX, toY) = spot(g, obj.t)
        assertTrue(g.move(obj, toX, toY, true)); assertTrue(g.plant(obj, "corn"))
        time += 46_000; assertTrue(g.harvest(obj)); val coins = g.coins; val xp = g.xp
        assertTrue(g.buildHistory.undo())
        assertEquals(x, obj.x); assertEquals(y, obj.y); assertFalse(obj.flip); assertNull(obj.crop)
        assertEquals(coins, g.coins); assertEquals(xp, g.xp)
    }

    @Test fun `a whole brush gesture including repeated tiles is a single exact refund`() {
        val g = game(); val (x, y) = empty(g); val coins = g.coins; val xp = g.xp
        g.buildHistory.beginGesture()
        assertTrue(g.paint("road", x, y)); assertTrue(g.paint("eraser", x, y)); assertTrue(g.paint("road", x, y))
        g.buildHistory.endGesture()
        assertEquals(1, g.buildHistory.size); assertTrue(g.buildHistory.undo())
        assertEquals(0, g.pathAt(x, y)); assertEquals(coins, g.coins); assertEquals(xp, g.xp)
    }

    @Test fun `bulldozer gesture restores objects and terrain atomically`() {
        val g = game(); val obj = g.objs.values.first { it.t == "cottage" }; val path = g.paths.indexOfFirst { it != 0 }
        val before = g.coins; val code = g.paths[path]
        g.buildHistory.beginGesture(); assertTrue(g.sell(obj)); assertTrue(g.paint("eraser", path % Hamlet.GRID, path / Hamlet.GRID)); g.buildHistory.endGesture()
        g.coins = 0
        assertFalse(g.buildHistory.undo()); assertNull(g.objs[obj.id]); assertEquals(0, g.paths[path])
        g.coins = before + g.sellValue(obj)
        assertTrue(g.buildHistory.undo()); assertSame(obj, g.objs[obj.id]); assertEquals(code, g.paths[path]); assertEquals(before, g.coins)
    }

    @Test fun `flood and drain preserve roads buildings and water beneath boats`() {
        val g = game(true); val prior = g.paths.copyOf(); val hall = g.objs.values.first { it.t == "hall" }
        assertTrue(g.floodRealm(true) > 100); assertEquals(0, g.pathAt(hall.x, hall.y))
        assertTrue(g.buildHistory.undo()); assertArrayEquals(prior, g.paths)
        g.floodRealm(true)
        val boat = buy(g, Hamlet.defs.first { it.onWater && !it.shore }.id)
        g.buildHistory.clear()
        val flooded = g.paths.copyOf(); assertTrue(g.floodRealm(false) > 100)
        assertEquals(Hamlet.PATH_WATER, g.pathAt(boat.x, boat.y))
        assertTrue(g.buildHistory.undo()); assertArrayEquals(flooded, g.paths)
    }

    @Test fun `fill stops at budget and is fully reversible`() {
        val g = game(); val (x, y) = empty(g); val unitCost = Hamlet.byId.getValue("road").cost
        g.coins = unitCost * 3L; val coins = g.coins; val xp = g.xp
        val painted = g.fillConnected("road", x, y)
        assertTrue(painted in 1..3); assertEquals(coins - painted * unitCost, g.coins)
        assertTrue(g.buildHistory.undo()); assertEquals(coins, g.coins); assertEquals(xp, g.xp)
    }

    @Test fun `hall is protected and recent choices remain bounded distinct and valid`() {
        val g = game(); val hall = g.objs.values.first { it.t == "hall" }
        assertFalse(g.sell(hall)); assertFalse(g.move(hall, hall.x, hall.y + 1, false)); assertFalse(g.buildHistory.canUndo())
        val choices = Hamlet.defs.filter { it.cat != Cat.HALL }.take(12).map { it.id }
        choices.forEach(g.buildHistory::noteRecent); g.buildHistory.noteRecent(choices.last())
        assertEquals(9, g.buildHistory.recent.size); assertEquals(9, g.buildHistory.recent.toSet().size)
        g.buildHistory.restoreRecent(listOf("hall", "nonexistent", "road", "road", "field"))
        assertEquals(listOf("road", "field"), g.buildHistory.recent)
        g.startNew(false); assertFalse(g.buildHistory.canUndo()); assertTrue(g.buildHistory.recent.isEmpty())
    }

    @Test fun `large water brush charges once per stamp and undoes every painted tile`() {
        val g = game(); val lake = Hamlet.byId.getValue("lake"); val (x, y) = spot(g, "lake")
        val coins = g.coins; val xp = g.xp; val prior = g.paths.copyOf()
        assertTrue(g.paint("lake", x, y))
        assertEquals(coins - lake.cost, g.coins); assertEquals(xp + lake.xp, g.xp)
        assertEquals((lake.w * lake.d).toLong(), g.stats["water"])
        assertTrue(g.buildHistory.undo()); assertArrayEquals(prior, g.paths)
        assertEquals(coins, g.coins); assertEquals(xp, g.xp)
    }

    @Test fun `free workshop and first expedition recover even a completely empty treasury`() {
        val g = game(); g.coins = 0; g.gems = 0; g.xp = 0
        g.objs.clear(); g.occ.fill(0); g.paths.fill(0)
        repeat(2) {
            assertTrue(g.journey.startExpedition(0))
            repeat(3) {
                g.beginWork()
                repeat(12) { assertTrue(g.answerWork(g.workTool)) }
                assertTrue(g.workOnExpedition())
            }
            assertEquals(0L, g.journey.expeditionLeft()); assertTrue(g.journey.collectExpedition())
        }
        assertEquals(80L, g.coins); assertEquals(0, g.gems)
        val field = buy(g, "field"); assertTrue(g.plant(field, "wheat"))
        assertEquals(80L - Hamlet.byId.getValue("field").cost - Hamlet.cropById.getValue("wheat").cost, g.coins)
    }
}
