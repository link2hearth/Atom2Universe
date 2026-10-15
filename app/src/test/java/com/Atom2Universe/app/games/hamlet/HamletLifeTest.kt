package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.nio.file.Files
import kotlin.random.Random

class HamletLifeTest {
    private var time = 1_000_000_000L
    private fun game(): HamletGame = HamletGame({ time }, Random(17)).apply { startNew(false) }.established()
    private fun buy(g: HamletGame, id: String): Obj {
        val origin = g.realmOrigin()
        for (y in origin until origin + g.realmSize()) for (x in origin until origin + g.realmSize()) {
            if (g.canPlace(id, x, y, false)) return g.buy(id, x, y, false)!!
        }
        error("No room: $id")
    }
    @Test fun `nourrir ne redemarre pas un cycle et les produits ne se collectent qu'une fois`() {
        val g = game().apply { xp = 10_000; coins = 10_000 }
        val pen = buy(g, "henyard")
        assertFalse(g.feed(pen))
        time += 31_000; g.tick()
        assertEquals(Status.FEED, g.status(pen))
        val before = g.coins
        g.pantry["corn"] = 1
        assertTrue(g.feed(pen)); val start = pen.t0
        assertFalse(g.feed(pen)); assertEquals(start, pen.t0)
        assertFalse(g.collectProduct(pen))
        time += 8 * Hamlet.DAY_MS
        assertEquals(1, g.productCount(pen))
        assertTrue(g.collectProduct(pen)); assertFalse(g.collectProduct(pen))
        assertEquals(before + 30, g.coins); assertEquals(0, g.stock("corn"))
        assertEquals(Status.FEED, g.status(pen))
    }
    @Test fun `les vergers gardent trois recoltes et la fraction du prochain cycle`() {
        val g = game().apply { xp = 10_000; coins = 10_000 }
        val tree = buy(g, "treeApple")
        val period = HamletLife.produce.getValue(tree.t).seconds * 1000L
        time += period * 10 + period / 2
        assertEquals(3, g.productCount(tree))
        val before = g.coins
        assertTrue(g.collectProduct(tree)); assertEquals(before + 72, g.coins)
        assertEquals(period / 2, g.productLeft(tree))
        assertFalse(g.collectProduct(tree))
    }
    @Test fun `soigner protege les plantes oubliees et ne se paie qu'une fois`() {
        val g = game()
        val f = g.objs.values.first { it.def.cat == Cat.FIELD }
        assertTrue(g.plant(f, "corn")); assertTrue(g.tend(f))
        val before = g.coins
        assertFalse(g.tend(f)); assertEquals(before, g.coins)
        time += 5 * 3_600_000
        assertEquals(Status.RIPE, g.status(f)); assertTrue(g.harvest(f))
        assertEquals(before + Hamlet.cropById.getValue("corn").earn * 120 / 100, g.coins); assertFalse(f.tended)
        assertTrue(g.plant(f, "corn")); assertFalse(f.tended)
    }
    @Test fun `ameliorer a un prix et un plafond sans doubler les recettes`() {
        val g = game().apply { coins = 10_000 }
        val home = g.objs.values.first { it.def.cat == Cat.HOME }
        // sans les récoltes demandées, rien ne se passe
        assertFalse(g.canAffordUpgrade(home)); assertFalse(g.upgrade(home)); assertEquals(1, home.tier); assertEquals(10_000L, g.coins)
        for (tier in 2..5) {
            HamletIndustry.upgrade(home).resources.forEach { (r, n) -> g.adventure.add(r, n) }
            for (need in g.upgradeNeeds(home)) g.pantry[need.crop] = need.units
            assertTrue(g.upgrade(home)); assertEquals(tier, home.tier)
        }
        for (need in HamletEconomy.upgradeNeeds(home.def, 5)) g.pantry[need.crop] = 100
        assertFalse(g.upgrade(home)); assertEquals(20, g.yieldCoins(home, 10))
        g.coins = 0
        assertFalse(g.upgrade(g.objs.values.first { it.def.cat == Cat.FIELD }))
    }
    @Test fun `les objectifs journaliers changent au jour suivant sans double paiement`() {
        val g = game()
        g.stat("harvested", 8)
        assertTrue(g.claimDaily(0)); assertFalse(g.claimDaily(0)); assertFalse(g.claimDaily(-1))
        val copy = HamletGame({ time }, Random(17))
        assertTrue(HamletSave.fromJson(copy, HamletSave.toJson(g)))
        assertFalse(copy.claimDaily(0))
        time += Hamlet.DAY_MS
        // La première action du nouveau jour est comptée, même sans tick préalable.
        g.stat("harvested", 1)
        assertEquals(1, g.dailyProgress(0).toInt()); assertEquals(0, g.dailyClaimed)
        g.stat("harvested", 7); assertTrue(g.claimDaily(0))
    }
    @Test fun `un objet vendu ne permet plus de recettes ni de deplacement`() {
        val g = game().apply { coins = 10_000 }
        val home = g.objs.values.first { it.def.cat == Cat.HOME }
        assertTrue(g.sell(home)); time += Hamlet.DAY_MS
        assertFalse(g.collectTax(home)); assertFalse(g.move(home, home.x, home.y, false)); assertFalse(g.upgrade(home))
        val field = g.objs.values.first { it.def.cat == Cat.FIELD }
        assertTrue(g.plant(field, "corn")); assertTrue(g.sell(field)); time += 60_000
        assertFalse(g.harvest(field)); assertFalse(g.plant(home, "corn"))
    }
    @Test fun `la gomme ne retire pas l'eau sous une barque`() {
        val g = game().apply { sandbox = true }
        val spot = g.realmOrigin()
        assertTrue(g.paint("river", spot, spot))
        assertNotNull(g.buy("rowboat", spot, spot, false))
        assertFalse(g.paint("eraser", spot, spot))
        assertFalse(g.paint("land", spot, spot))
        assertEquals(Hamlet.PATH_WATER, g.pathAt(spot, spot))
    }
    @Test fun `les nouveaux etats persistent et une lecture invalide est transactionnelle`() {
        val g = game().apply { xp = 10_000; coins = 10_000 }
        val pen = buy(g, "henyard"); time += 31_000; g.tick(); g.feed(pen)
        for (need in g.upgradeNeeds(pen)) g.pantry[need.crop] = 100
        g.upgrade(pen)
        val field = g.objs.values.first { it.def.cat == Cat.FIELD }; g.plant(field, "oats"); g.tend(field)
        val saved = HamletSave.toJson(g)
        val h = game(); assertTrue(HamletSave.fromJson(h, saved))
        assertEquals(2, h.objs[pen.id]!!.tier); assertEquals(pen.t0, h.objs[pen.id]!!.t0); assertTrue(h.objs[field.id]!!.tended)
        assertFalse(HamletSave.fromJson(h, "{\"objs\":[{\"id\":1,\"t\":\"hall\"}]}"))
        assertEquals(saved, HamletSave.toJson(h))
        val duplicated = JSONObject(saved)
        duplicated.getJSONArray("objs").put(duplicated.getJSONArray("objs").getJSONObject(0))
        assertFalse(HamletSave.fromJson(h, duplicated.toString())); assertEquals(saved, HamletSave.toJson(h))
    }
    @Test fun `la sauvegarde secondaire permet de recuperer un fichier tronque`() {
        val dir = Files.createTempDirectory("hamlet-save").toFile()
        try {
            val g = game(); assertTrue(HamletSave.write(dir, g)); val initial = g.coins
            g.coins += 10; assertTrue(HamletSave.write(dir, g))
            HamletSave.file(dir).writeText("{broken")
            val loaded = game(); assertTrue(HamletSave.read(dir, loaded)); assertEquals(initial, loaded.coins)
        } finally { dir.deleteRecursively() }
    }
    @Test fun `un batiment construit avant sa quete est reconnu`() {
        val g = game()
        g.stat("built_bakery")
        g.quest = 6; g.questBase = 1
        assertTrue(g.questDone(g.currentQuest()!!))
    }
    @Test fun `les deux villages ne partagent ni argent ni batiments`() {
        val dir = Files.createTempDirectory("hamlet-worlds").toFile()
        try {
            val normal = game(); normal.coins = 77
            val creative = game().apply { startNew(true); coins = 900_000 }
            buy(creative, "dairyfarm")
            assertTrue(HamletSave.write(dir, normal))
            assertTrue(HamletSave.write(dir, creative, true))
            val loaded = game(); assertTrue(HamletSave.read(dir, loaded))
            assertEquals(77L, loaded.coins); assertFalse(loaded.sandbox)
            assertFalse(loaded.objs.values.any { it.t == "dairyfarm" })
            assertTrue(HamletSave.read(dir, loaded, true))
            assertEquals(900_000L, loaded.coins); assertTrue(loaded.sandbox)
            assertTrue(loaded.objs.values.any { it.t == "dairyfarm" })
        } finally { dir.deleteRecursively() }
    }
    @Test fun `accelerer un elevage ne dispense pas de le nourrir`() {
        val g = game().apply { xp = 10_000; coins = 10_000 }
        val pen = buy(g, "henyard")
        time += 31_000; g.tick()
        assertEquals(0, g.hurryCost(pen)); assertFalse(g.hurry(pen))
        g.pantry["corn"] = 1
        assertTrue(g.feed(pen)); assertEquals(1, g.hurryCost(pen))
        val gems = g.gems
        assertTrue(g.hurry(pen)); assertEquals(gems - 1, g.gems)
        assertTrue(g.collectProduct(pen)); assertEquals(Status.FEED, g.status(pen))
        assertFalse(g.hurry(pen)); assertFalse(g.collectProduct(pen))
    }
}
