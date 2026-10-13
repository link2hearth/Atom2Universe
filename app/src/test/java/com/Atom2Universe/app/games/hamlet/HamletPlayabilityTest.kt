package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Short, deterministic escape-route checks: no money, daily quota or real waiting required. */
class HamletPlayabilityTest {
    @Test fun brokePlayerCanRecoverThroughNeighborsWithoutWaiting() {
        val game = HamletGame({ 1_800_000_000_000L }, Random(1)).apply { startNew(false); coins = 0; gems = 0 }
        val neighbors = game.neighbors
        assertTrue(neighbors.visit(0))
        repeat(3) {
            assertTrue(neighbors.placeOrder(0, HamletNeighbors.FIRST_SHOP))
            var gestures = 0
            while (neighbors.left(0, HamletNeighbors.FIRST_SHOP) > 0 && gestures < 100) {
                assertTrue(neighbors.help(0, HamletNeighbors.FIRST_SHOP, neighbors.helpTool(0, HamletNeighbors.FIRST_SHOP)))
                gestures++
            }
            assertEquals(24, gestures)
            assertNotNull(neighbors.collect(0, HamletNeighbors.FIRST_SHOP))
        }
        val field = game.objs.values.first { it.def.cat == Cat.FIELD }
        assertTrue(game.coins >= 60)
        assertTrue(game.plant(field, "corn"))
        assertEquals(0, game.gems)
    }

    @Test fun longestConstructionHasBoundedActiveAlternativeAndNoDailyCap() {
        assertTrue(Hamlet.defs.all { it.build <= 7200 && it.lvl <= Hamlet.MAX_LEVEL })
        val game = HamletGame({ 1_800_000_000_000L }, Random(2)).apply { startNew(false) }
        val def = Hamlet.defs.filter { !it.isPath && it.cat != Cat.HALL }.maxBy { it.build }
        // Isolate the waiting mechanic; purchasing eligibility is covered by the economy simulations.
        val building = Obj(game.nextId++, def.id, 0, 0).also { it.b0 = game.now(); game.objs[it.id] = it }
        game.workSeconds = 0
        var gestures = 0
        while (game.buildLeft(building) > 0 && gestures < 400) {
            game.beginWork()
            repeat(12) { assertTrue(game.answerWork(game.workTool)); gestures++ }
            assertTrue(game.applyWork(building))
        }
        assertEquals(0L, game.buildLeft(building))
        // At a deliberately relaxed two seconds per gesture, even a two-hour build is under ten active minutes.
        assertTrue(gestures * 2 <= 600)
        val before = game.workSeconds
        repeat(12) { assertTrue(game.answerWork(game.workTool)) }
        assertEquals(before + 300, game.workSeconds)
    }

    @Test fun allNeighborShopsRemainPresentAndHomeIsUntouched() {
        val game = HamletGame({ 1_800_000_000_000L }).apply { startNew(false) }
        val before = HamletSave.toJson(game)
        for (neighbor in HamletNeighbors.villages) {
            val scene = game.neighbors.generate(neighbor.id)!!
            neighbor.shops.forEachIndexed { index, type ->
                assertEquals("neighbor ${neighbor.id} shop $index", type, scene.objs[HamletNeighbors.FIRST_SHOP + index]?.t)
            }
            assertTrue(scene.objs.values.any { it.t == "hall" })
        }
        assertEquals(before, HamletSave.toJson(game))
    }
}
