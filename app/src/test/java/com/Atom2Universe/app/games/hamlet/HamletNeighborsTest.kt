package com.Atom2Universe.app.games.hamlet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletNeighborsTest {
    private var time = 1_800_000_000_000L
    private fun game() = HamletGame({ time }, Random(31)).apply { startNew(false) }

    @Test fun `douze villages deterministes visites sans remplacer ni modifier le domicile`() {
        val home = game()
        val before = HamletSave.toJson(home)
        for (profile in HamletNeighbors.villages) {
            val first = home.neighbors.generate(profile.id)!!
            val second = home.neighbors.generate(profile.id)!!
            assertNotSame(home, first)
            assertArrayEquals(first.paths, second.paths)
            assertEquals(first.objs.values.map { listOf(it.id, it.t, it.x, it.y) }, second.objs.values.map { listOf(it.id, it.t, it.x, it.y) })
            profile.shops.forEachIndexed { i, type ->
                val shop = first.objs[HamletNeighbors.FIRST_SHOP + i]!!
                assertEquals(type, shop.t)
                assertEquals(Cat.SHOP, shop.def.cat)
                assertTrue(first.inRealm(shop.x, shop.y, shop.w, shop.d))
            }
            for (obj in first.objs.values) for (y in obj.y until obj.y + obj.d) for (x in obj.x until obj.x + obj.w)
                assertEquals(obj.id, first.occ[first.idx(x, y)])
            first.objs.clear(); first.paths.fill(0)
        }
        assertEquals(before, HamletSave.toJson(home))
        assertNull(home.neighbors.generate(-1)); assertNull(home.neighbors.generate(12))
    }

    @Test fun `commande gratuite refuse doubles fausses boutiques et collecte anticipee`() {
        val g = game(); val n = g.neighbors; val coins = g.coins
        assertFalse(n.placeOrder(-1, 100)); assertFalse(n.placeOrder(0, 99)); assertFalse(n.placeOrder(0, 103))
        assertTrue(n.placeOrder(0, 100)); assertFalse(n.placeOrder(0, 100))
        assertEquals(coins, g.coins); assertNull(n.collect(0, 100)); assertEquals(1, n.orders.size)
        time += n.duration(0, 100) * 1000L
        val result = n.collect(0, 100)!!
        assertEquals(coins + result.coins, g.coins)
        assertEquals(result.work, g.workSeconds)
        assertNull(n.collect(0, 100)); assertTrue(n.orders.isEmpty())
    }

    @Test fun `collecte retire le colis avant tout callback et ne paie qu une fois`() {
        val g = game(); val n = g.neighbors
        n.placeOrder(1, 101); time += 600_000
        var callbacks = 0
        g.listener = object : HamletListener {
            override fun onReward(o: Obj?, coins: Int, xp: Int, gems: Int) { callbacks++; assertNull(n.collect(1, 101)) }
            override fun onLevelUp(level: Int, gems: Int) = Unit
            override fun onMessage(code: String, arg: String) = Unit
            override fun onChanged() = Unit
        }
        assertNotNull(n.collect(1, 101)); assertEquals(1, callbacks)
    }

    @Test fun `travail et gestes accelerent seulement la commande choisie sans energie`() {
        val g = game(); val n = g.neighbors
        n.placeOrder(0, 100); n.placeOrder(0, 101)
        val other = n.left(0, 101); val initial = n.left(0, 100)
        assertEquals(0, n.useWork(0, 100))
        assertFalse(n.help(0, 100, (n.helpTool(0, 100) + 1) % 3))
        repeat(6) { assertTrue(n.help(0, 100, n.helpTool(0, 100))) }
        assertEquals(initial - 30_000, n.left(0, 100)); assertEquals(other, n.left(0, 101))
        g.workSeconds = 7200
        val used = n.useWork(0, 100)
        assertEquals(90, used); assertEquals(7110, g.workSeconds)
        assertEquals(0L, n.left(0, 100)); assertEquals(other, n.left(0, 101))
        assertFalse(n.help(0, 100, 0)); assertEquals(0, n.useWork(0, 100))
    }

    @Test fun `commandes hors ligne et gestes intermediaires sont sauvegardes avec le village`() {
        val g = game(); val n = g.neighbors
        n.visit(5); n.placeOrder(5, 102)
        repeat(3) { n.help(5, 102, n.helpTool(5, 102)) }
        val saved = HamletSave.toJson(g)
        time += 86_400_000
        val restored = game()
        assertTrue(HamletSave.fromJson(restored, saved))
        assertEquals(setOf(5), restored.neighbors.visited)
        assertEquals(3, restored.neighbors.order(5, 102)!!.aid)
        assertEquals(1, restored.neighbors.readyCount())
        assertNotNull(restored.neighbors.collect(5, 102))
        assertTrue(HamletSave.fromJson(restored, HamletSave.toJson(restored)))
        assertNull(restored.neighbors.collect(5, 102))
    }

    @Test fun `sauvegarde refuse ids types durees et doublons incoherents`() {
        val g = game(); val n = g.neighbors
        n.placeOrder(0, 100)
        val valid = n.encode().getJSONArray("orders").getJSONObject(0)
        val rows = JSONArray().put(valid).put(JSONObject(valid.toString()))
            .put(JSONObject(valid.toString()).put("shop", 101).put("type", "forged"))
            .put(JSONObject(valid.toString()).put("neighbor", 42))
            .put(JSONObject(valid.toString()).put("shop", 102).put("type", "herbalist").put("due", time + 86_400_000))
        n.decode(JSONObject().put("orders", rows).put("visited", JSONArray(listOf(-1, 0, 50))))
        assertEquals(1, n.orders.size); assertEquals(setOf(0), n.visited)
        val detached = n.order(0, 100)!!; detached.due = 0
        assertTrue(n.left(0, 100) > 0)
        n.decode(null); assertTrue(n.orders.isEmpty()); assertTrue(n.visited.isEmpty())
    }

    @Test fun `aide ne depend ni des gemmes ni d une limite quotidienne et reset separe les modes`() {
        val g = game(); val n = g.neighbors; g.gems = 0
        repeat(25) {
            assertTrue(n.placeOrder(0, 100))
            repeat(24) { assertTrue(n.help(0, 100, n.helpTool(0, 100))) }
            assertEquals(0L, n.left(0, 100)); assertNotNull(n.collect(0, 100))
        }
        n.visit(2); n.placeOrder(2, 100)
        g.startNew(true)
        assertTrue(n.orders.isEmpty()); assertTrue(n.visited.isEmpty())
        assertTrue(n.placeOrder(0, 100)); assertNull(n.collect(0, 100))
    }

    @Test fun `objectifs voisins reclamables une fois persistent sans decaler la quete principale`() {
        val g = game(); val n = g.neighbors; val quest = g.quest
        assertFalse(n.claimMilestone(0)); assertFalse(n.claimMilestone(3))
        repeat(5) { n.visit(it) }
        assertTrue(n.claimMilestone(0)); assertFalse(n.claimMilestone(0))
        assertEquals(12, n.milestoneTarget(0)); assertEquals(quest, g.quest)
        assertTrue(HamletSave.fromJson(g, HamletSave.toJson(g)))
        assertEquals(12, n.milestoneTarget(0)); assertFalse(n.claimMilestone(0))
        for (i in 5..11) n.visit(i)
        assertTrue(n.claimMilestone(0)); assertEquals(0, n.milestoneTarget(0)); assertFalse(n.claimMilestone(0))
    }
}
