package com.Atom2Universe.app.games.hamlet

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletShopIntegrationTest {
    private var time = 1_800_000_000_000L
    private fun game() = HamletGame({ time }, Random(5)).apply { startNew(false) }
    private fun openShop(g: HamletGame) {
        g.sandbox = true
        val origin = g.realmOrigin()
        var placed = false
        loop@ for (y in origin until origin + g.realmSize()) for (x in origin until origin + g.realmSize()) {
            if (g.canPlace("adventureInn", x, y, false)) {
                assertNotNull(g.buy("adventureInn", x, y, false)); placed = true; break@loop
            }
        }
        assertTrue(placed); g.sandbox = false; g.journey.chapter = 2
        g.adventure.add(AdventureResource.RUNES, 1000)
        assertTrue(g.shardShop.unlocked())
    }

    @Test fun `sauvegarde complete garde achat nominatif paiement et quota partage`() {
        val g = game(); openShop(g)
        val initial = g.adventure[AdventureResource.RUNES]
        val price = g.shardShop.cost(0, 3)!!
        assertTrue(g.shardShop.buy(0, 3, price))
        val restored = game(); assertTrue(HamletSave.fromJson(restored, HamletSave.toJson(g)))
        assertTrue(restored.shardShop.unlocked())
        assertEquals(initial - price.runes, restored.adventure[AdventureResource.RUNES])
        assertEquals(3L, restored.adventure.heroes[0].shards); assertEquals(0L, restored.adventure.heroes[1].shards)
        assertEquals(3, restored.shardShop.remaining(1))
        assertTrue(restored.shardShop.buy(1, 3)); assertFalse(restored.shardShop.buy(0))
        assertEquals(3L, restored.adventure.heroes[1].shards)
        assertFalse(HamletSave.fromJson(restored, "{}")); assertEquals(0, restored.shardShop.remaining(0))
        time = (time / Hamlet.DAY_MS + 1) * Hamlet.DAY_MS
        assertEquals(6, restored.shardShop.remaining(0))
    }

    @Test fun `ancienne sauvegarde et nouvelle partie initialisent un stock coherent`() {
        val g = game(); openShop(g)
        assertTrue(g.shardShop.buy(0, 6))
        val legacy = JSONObject(HamletSave.toJson(g)).apply { remove("shardShop") }
        val restored = game(); assertTrue(HamletSave.fromJson(restored, legacy.toString()))
        assertEquals(6L, restored.adventure.heroes[0].shards); assertEquals(6, restored.shardShop.remaining(0))
        assertTrue(restored.shardShop.buy(0, 6))
        restored.startNew(false)
        assertFalse(restored.shardShop.unlocked()); assertEquals(0L, restored.adventure.heroes[0].shards)
        assertEquals(6, restored.shardShop.remaining(HeroRarity.COMMON))
    }
}
