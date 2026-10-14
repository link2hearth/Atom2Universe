package com.Atom2Universe.app.games.hamlet

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.random.Random

class HamletShardShopTest {
    private var time = 1_800_000_000_000L
    private fun game(sandbox: Boolean = false) = HamletGame({ time }, Random(8)).apply { startNew(sandbox) }.withStarters()
    private fun facility(g: HamletGame, type: String, tier: Int) {
        g.objs.values.firstOrNull { it.t == type }?.let { it.tier = tier; return }
        val id = 9000 + g.objs.size
        g.objs[id] = Obj(id, type, 0, 0).apply { this.tier = tier }
    }
    private fun open(g: HamletGame, rarity: HeroRarity): Int {
        val r = g.shardShop.requirements(rarity)
        g.journey.chapter = r.chapter; g.adventure.frontier = r.frontier
        facility(g, "adventureInn", maxOf(1, r.inn))
        if (r.forge > 0) facility(g, "adventureForge", r.forge)
        if (r.academy > 0) facility(g, "adventureAcademy", r.academy)
        g.adventure.add(AdventureResource.RUNES, 100_000)
        g.adventure.add(AdventureResource.CRYSTAL, 100_000)
        return g.adventure.heroes.first { it.def.rarity == rarity }.also { it.owned = true }.id
    }

    @Test fun `boutique et cinq paliers suivent chapitres routes et batiments termines`() {
        val g = game(); val s = g.shardShop
        assertFalse(s.unlocked()); assertEquals(0, s.rank())
        for (rarity in HeroRarity.entries) {
            val hero = open(g, rarity); val req = s.requirements(rarity)
            assertTrue(s.unlocked(rarity)); assertTrue(s.canBuy(hero))
            g.journey.chapter = req.chapter - 1
            assertFalse(s.unlocked(rarity)); assertFalse(s.canBuy(hero))
            g.journey.chapter = req.chapter
            if (req.frontier > 0) {
                g.adventure.frontier = req.frontier - 1; assertFalse(s.unlocked(rarity))
                g.adventure.frontier = req.frontier
            }
        }
        val inn = g.objs.values.first { it.t == "adventureInn" }
        inn.b0 = time
        assertFalse(s.unlocked()); assertFalse(s.unlocked(HeroRarity.MYTHIC))
        time += inn.def.build * 1000L + 1
        assertEquals(5, s.rank())
    }

    @Test fun `aucun fragment ne recrute un heros absent ni ne devient universel`() {
        val g = game(); val s = g.shardShop; open(g, HeroRarity.COMMON)
        val absent = g.adventure.heroes.first { !it.owned }
        val before = g.adventure.bag.clone()
        assertNull(s.cost(absent.id)); assertEquals(0, s.remaining(absent.id))
        assertFalse(s.canBuy(absent.id)); assertFalse(s.buy(absent.id)); assertArrayEquals(before, g.adventure.bag)
        assertTrue(s.offers().all { g.adventure.heroes[it.hero].owned })
        assertTrue(s.buy(0, 2)); assertEquals(2L, g.adventure.heroes[0].shards)
        assertEquals(0L, g.adventure.heroes[1].shards); assertFalse(absent.owned)
        assertFalse(g.adventure.promote(0)); assertFalse(g.adventure.promote(1))
    }

    @Test fun `stock quotidien partage entre personnages de meme rarete`() {
        val g = game(); val s = g.shardShop; open(g, HeroRarity.COMMON)
        val initial = g.adventure[AdventureResource.RUNES]
        val first = s.cost(0, 4)!!; assertTrue(s.buy(0, 4, first))
        assertEquals(2, s.remaining(1)); assertEquals(2, s.remaining(HeroRarity.COMMON))
        val second = s.cost(1, 2)!!; assertTrue(s.buy(1, 2, second))
        assertEquals(0, s.remaining(0)); assertFalse(s.buy(0)); assertFalse(s.buy(1))
        assertEquals(initial - first.runes - second.runes, g.adventure[AdventureResource.RUNES])
        assertEquals(4L, g.adventure.heroes[0].shards); assertEquals(2L, g.adventure.heroes[1].shards)
    }

    @Test fun `paiement est atomique sur toutes ressources et quantites`() {
        val g = game(); val s = g.shardShop; val hero = open(g, HeroRarity.EPIC)
        g.adventure.bag[AdventureResource.CRYSTAL.ordinal] = 0
        val before = g.adventure.bag.clone()
        assertFalse(s.buy(hero)); assertFalse(s.buy(hero, 0)); assertFalse(s.buy(hero, -1)); assertFalse(s.buy(hero, 3))
        assertArrayEquals(before, g.adventure.bag); assertEquals(0L, g.adventure.heroes[hero].shards)
        assertEquals(2, s.remaining(hero))
        val price = s.cost(hero, 2)!!
        g.adventure.add(AdventureResource.CRYSTAL, price.crystal)
        assertTrue(s.buy(hero, 2, price))
        assertEquals(before[AdventureResource.RUNES.ordinal] - price.runes, g.adventure[AdventureResource.RUNES])
        assertEquals(0L, g.adventure[AdventureResource.CRYSTAL]); assertEquals(2L, g.adventure.heroes[hero].shards)
    }

    @Test fun `quota avance aux minuits utc sans reouvrir lors du recul horloge`() {
        val g = game(); val s = g.shardShop; open(g, HeroRarity.COMMON)
        val startDay = time / Hamlet.DAY_MS
        assertTrue(s.buy(0, 6)); assertEquals(0, s.remaining(0))
        time = (startDay + 1) * Hamlet.DAY_MS - 1; assertEquals(0, s.remaining(0))
        time++; assertEquals(6, s.remaining(0)); assertTrue(s.buy(0, 6))
        time = startDay * Hamlet.DAY_MS; assertEquals(0, s.remaining(0)); assertFalse(s.buy(0))
        time = (startDay + 1) * Hamlet.DAY_MS + 10; assertEquals(0, s.remaining(0))
        time = (startDay + 2) * Hamlet.DAY_MS; assertEquals(6, s.remaining(0))
    }

    @Test fun `quota mythique revient le lundi utc et se conserve au prochain jour`() {
        val g = game(); val s = g.shardShop; val hero = open(g, HeroRarity.MYTHIC)
        assertTrue(s.buy(hero)); assertFalse(s.buy(hero))
        val monday = s.nextRestock(HeroRarity.MYTHIC)
        assertEquals(4L, monday / Hamlet.DAY_MS % 7) // Day zero was Thursday.
        time = monday - 1; assertEquals(0, s.remaining(hero))
        time++; assertEquals(1, s.remaining(hero)); assertTrue(s.buy(hero))
        time += Hamlet.DAY_MS; assertEquals(0, s.remaining(hero))
        time = monday - Hamlet.DAY_MS; assertEquals(0, s.remaining(hero)); assertFalse(s.buy(hero))
    }

    @Test fun `devis change refuse achat et prix ne baisse pas si route ou horloge recule`() {
        val g = game(); val s = g.shardShop; open(g, HeroRarity.COMMON)
        val displayed = s.cost(0)!!
        g.adventure.frontier = 100
        val before = g.adventure.bag.clone()
        assertFalse(s.buy(0, expectedCost = displayed)); assertArrayEquals(before, g.adventure.bag)
        assertEquals(6, s.remaining(0)); assertEquals(0L, g.adventure.heroes[0].shards)
        val advanced = s.cost(0)!!; assertTrue(advanced.runes > displayed.runes)
        g.adventure.frontier = 0; time -= Hamlet.DAY_MS
        assertEquals(advanced, s.cost(0)); assertTrue(s.buy(0, expectedCost = advanced))
    }

    @Test fun `sauvegarde copie et restauration conservent quotas et plancher prix`() {
        val g = game(); val s = g.shardShop; open(g, HeroRarity.COMMON)
        g.adventure.frontier = 200; assertTrue(s.buy(0, 4))
        val j = s.encode(); val expected = s.cost(0)
        val restored = game(); open(restored, HeroRarity.COMMON); restored.shardShop.decode(j)
        assertEquals(2, restored.shardShop.remaining(1)); assertEquals(expected, restored.shardShop.cost(0))
        val copied = game(); open(copied, HeroRarity.COMMON); copied.shardShop.copyFrom(s)
        assertEquals(2, copied.shardShop.remaining(0)); assertEquals(expected, copied.shardShop.cost(0))
        s.copyFrom(s); assertEquals(2, s.remaining(0))
        s.reset(); assertEquals(6, s.remaining(0))
    }

    @Test fun `donnees malformees bornent les stocks et ne creent pas de remboursement`() {
        val g = game(); open(g, HeroRarity.COMMON); val s = g.shardShop
        val day = time / Hamlet.DAY_MS
        s.decode(JSONObject().put("day", day).put("week", (day + 3) / 7)
            .put("frontierPriceFloor", 100).put("daily", JSONObject().put("COMMON", 9999).put("RARE", -50))
            .put("weekly", JSONObject().put("MYTHIC", 9999)))
        assertEquals(0, s.remaining(HeroRarity.COMMON)); assertEquals(4, s.remaining(HeroRarity.RARE))
        assertEquals(0, s.remaining(HeroRarity.MYTHIC)); assertEquals(26L, s.cost(0)!!.runes)
        g.adventure.heroes[0].shards = Long.MAX_VALUE
        s.reset(); val wallet = g.adventure.bag.clone()
        assertFalse(s.buy(0)); assertArrayEquals(wallet, g.adventure.bag)
        s.decode(null); assertEquals(6, s.remaining(HeroRarity.COMMON))
    }

    @Test fun `achats concurrents ne doublent ni stock ni gains`() {
        val g = game(); val s = g.shardShop; open(g, HeroRarity.COMMON)
        val price = s.cost(0)!!; val initial = g.adventure[AdventureResource.RUNES]
        val workers = Executors.newFixedThreadPool(4)
        try {
            val results = (0 until 20).map { workers.submit<Boolean> { s.buy(0) } }.map { it.get() }
            assertEquals(6, results.count { it }); assertEquals(6L, g.adventure.heroes[0].shards)
            assertEquals(initial - price.runes * 6, g.adventure[AdventureResource.RUNES])
            assertEquals(0, s.remaining(0))
        } finally { workers.shutdownNow() }
    }

    @Test fun `notification apres achat voit transaction complete et ne peut reutiliser stock`() {
        val g = game(); val s = g.shardShop; open(g, HeroRarity.COMMON)
        var attempted = false; var duplicate = false
        g.listener = object : HamletListener {
            override fun onReward(o: Obj?, coins: Int, xp: Int, gems: Int) = Unit
            override fun onLevelUp(level: Int, gems: Int) = Unit
            override fun onMessage(code: String, arg: String) = Unit
            override fun onChanged() {
                if (!attempted) { attempted = true; duplicate = s.buy(0) }
            }
        }
        assertTrue(s.buy(0, 6)); assertTrue(attempted); assertFalse(duplicate)
        assertEquals(6L, g.adventure.heroes[0].shards); assertEquals(0, s.remaining(0))
    }

    @Test fun `prix correspondent aux runes et cristaux vraiment gagnes autour des paliers`() {
        fun loot(stage: Int): LongArray {
            // Le butin d'une étape maîtrisée : on la refait en expédition rapide, sans combat à gagner.
            val g = game(); val a = g.adventure
            a.frontier = stage + 1; a.stars[stage] = 3; a.bag.fill(0); a.bag[AdventureResource.FOOD.ordinal] = 1_000_000
            assertTrue("reference encounter stage $stage", a.sweep(stage)); return a.bag.clone()
        }
        for (rarity in HeroRarity.entries) {
            val g = game(); val hero = open(g, rarity)
            val price = g.shardShop.cost(hero)!!
            val milestone = g.shardShop.requirements(rarity).frontier
            val ordinary = (milestone..milestone + 8).first { g.adventure.node(it) == AdventureNode.BATTLE }
            val earned = loot(ordinary)[AdventureResource.RUNES.ordinal]
            val encounters = price.runes.toDouble() / earned
            assertTrue("rune price should remain near 2..8 ordinary encounters for $rarity", encounters in 1.9..8.5)
            if (price.crystal > 0) {
                val previousBoss = g.shardShop.requirements(rarity).frontier / 10 * 10 - 1
                val crystals = loot(previousBoss)[AdventureResource.CRYSTAL.ordinal]
                assertTrue("a normal boss should fund at least one personal fragment for $rarity", crystals >= price.crystal)
            }
            val equipment = g.armory.craftCost(0, GearSet.HUNT, EquipmentRarity.RARE)
            assertEquals(0L, equipment.runes) // Basic equipment remains accessible while saving runes for stars.
        }
    }

    @Test fun `mode libre ouvre les fragments sans facturer ni limiter le stock`() {
        val g = game(true); val s = g.shardShop; g.adventure.bag.fill(0)
        assertEquals(5, s.rank()); assertTrue(s.buy(10, 100)); assertTrue(s.buy(10, 100))
        assertEquals(200L, g.adventure.heroes[10].shards); assertArrayEquals(LongArray(8), g.adventure.bag)
    }
}
