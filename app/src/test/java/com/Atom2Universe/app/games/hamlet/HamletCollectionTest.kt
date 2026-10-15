package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletCollectionTest {
    private var time = 1_800_000_000_000L
    private fun game() = HamletGame({ time }, Random(5)).apply { startNew(false) }.established()
    private fun buy(g: HamletGame, type: String): Obj {
        val creative = g.sandbox
        g.sandbox = true
        try {
            for (y in g.realmOrigin() until g.realmOrigin() + g.realmSize())
                for (x in g.realmOrigin() until g.realmOrigin() + g.realmSize())
                    if (g.canPlace(type, x, y, false)) return g.buy(type, x, y, false)!!
            error("No room for $type")
        } finally { g.sandbox = creative }
    }

    @Test fun `un geste recolte le champ ou la maison sans double paiement`() {
        val g = game()
        val field = g.objs.values.first { it.t == "field" }
        val home = g.objs.values.first { it.t == "cottage" }
        assertTrue(g.plant(field, "corn"))
        time += 301_000
        assertTrue(g.collectReady(field)); assertFalse(g.collectReady(field))
        assertTrue(g.collectReady(home)); assertFalse(g.collectReady(home))
        assertEquals(1L, g.stats["harvested"]); assertEquals(1L, g.stats["taxes"])
    }

    @Test fun `un geste ne paie ni nourriture ni accelerateur et ne lance pas de commande`() {
        val g = game()
        val field = g.objs.values.first { it.t == "field" }
        val pen = buy(g, "henyard")
        val shop = buy(g, "bakery")
        assertTrue(g.plant(field, "corn"))
        val coins = g.coins; val gems = g.gems
        assertFalse(g.collectReady(field))
        assertFalse(g.collectReady(pen))
        time += 31_000; g.tick()
        assertEquals(Status.ORDER_NEW, g.status(shop))
        assertFalse(g.collectReady(shop)); assertFalse(g.collectReady(pen))
        assertEquals(1, g.readyCount()); assertEquals(0, g.collectableCount())
        assertEquals(coins, g.coins); assertEquals(gems, g.gems)
        assertEquals(0L, shop.order!!.t0)
    }

    @Test fun `un geste livre une commande terminee et ramasse un produit pret`() {
        val g = game()
        val shop = buy(g, "bakery"); val tree = buy(g, "treeApple")
        time += 60_000; g.tick()
        g.coins = 10000
        g.pantry["wheat"] = 2
        assertTrue(g.acceptOrder(shop))
        time += maxOf(shop.def.job, HamletLife.produce.getValue(tree.t).seconds) * 1000L + 1
        assertTrue(g.collectReady(shop)); assertFalse(g.collectReady(shop))
        assertTrue(g.collectReady(tree)); assertFalse(g.collectReady(tree))
        assertEquals(1L, g.stats["delivered"])
    }

    @Test fun `tout recolter arrive apres le chapitre dix et survit a la sauvegarde`() {
        val g = game(); val home = g.objs.values.first { it.t == "cottage" }
        time += 601_000
        val coins = g.coins
        assertFalse(g.canCollectAll()); assertEquals(0, g.collectAll()); assertEquals(coins, g.coins)
        assertEquals(Status.TAX, g.status(home))
        g.journey.chapter = HamletGame.COLLECT_ALL_CHAPTER - 1
        assertFalse(g.canCollectAll())
        g.journey.chapter++
        val restored = game()
        assertTrue(HamletSave.fromJson(restored, HamletSave.toJson(g)))
        assertTrue(restored.canCollectAll()); assertEquals(2, restored.collectAll()); assertEquals(0, restored.collectAll())
        assertEquals(1L, restored.stats["gifts"]); assertEquals(1L, restored.stats["taxes"])
        val creative = game().apply { sandbox = true }
        assertTrue(creative.canCollectAll())
    }

    @Test fun `un ancien objet vendu ne rapporte rien au toucher`() {
        val g = game(); val home = g.objs.values.first { it.t == "cottage" }
        assertTrue(g.sell(home)); time += Hamlet.DAY_MS
        val coins = g.coins
        assertFalse(g.collectReady(home)); assertEquals(coins, g.coins)
    }

    @Test fun `un geste ouvre le cadeau quotidien et nettoie un champ fane`() {
        val g = game(); val hall = g.objs.values.first { it.def.cat == Cat.HALL }
        val field = g.objs.values.first { it.t == "field" }
        assertTrue(g.plant(field, "corn")); time += Hamlet.DAY_MS
        assertEquals(Status.GIFT, g.status(hall)); assertEquals(Status.WITHERED, g.status(field))
        assertTrue(g.collectReady(hall)); assertFalse(g.collectReady(hall))
        val coins = g.coins
        assertTrue(g.collectReady(field)); assertNull(field.crop); assertEquals(coins, g.coins)
        assertFalse(g.collectReady(field))
    }

    @Test fun `les petites expeditions ne peuvent pas produire des invitations sans limite`() {
        val g = game(); val j = g.journey
        fun trip() { assertTrue(j.startExpedition(0)); time = j.arrival; assertTrue(j.collectExpedition()) }
        val before = g.adventure[AdventureResource.TICKETS]
        assertEquals(1L, j.expeditionTickets(0)); trip()
        assertEquals(0L, j.expeditionTickets(0))
        repeat(19) { trip() }
        assertEquals(before + 2, g.adventure[AdventureResource.TICKETS])
        assertEquals(20, j.explored[0]); assertEquals(80, j.supplies)
        val restored = game(); assertTrue(HamletSave.fromJson(restored, HamletSave.toJson(g)))
        restored.journey.explored[0] = 24
        assertEquals(0L, restored.journey.expeditionTickets(0))
        time -= Hamlet.DAY_MS
        assertEquals(0L, restored.journey.expeditionTickets(0))
        time += 2 * Hamlet.DAY_MS
        assertEquals(1L, restored.journey.expeditionTickets(0))
    }
}
