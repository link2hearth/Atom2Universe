package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random

/** Les règles du village, sans téléphone : une horloge truquée remplace le temps qui passe. */
class HamletGameTest {

    private var t = 1_000_000_000L
    private fun game(sandbox: Boolean = false): HamletGame {
        val g = HamletGame({ t }, Random(5))
        g.startNew(sandbox)
        return g
    }

    private fun freeSpot(g: HamletGame, id: String): Pair<Int, Int> {
        val o = g.realmOrigin()
        val s = g.realmSize()
        for (y in o until o + s) for (x in o until o + s) if (g.canPlace(id, x, y, false)) return x to y
        error("pas de place pour $id")
    }

    @Test
    fun `une nouvelle partie a une halle, des champs et une chaumiere`() {
        val g = game()
        assertEquals(1, g.objs.values.count { it.t == "hall" })
        assertEquals(2, g.objs.values.count { it.t == "field" })
        assertEquals(1, g.objs.values.count { it.t == "cottage" })
        assertEquals(250L, g.coins)
    }

    @Test
    fun `planter puis recolter donne pieces et experience`() {
        val g = game()
        val f = g.objs.values.first { it.t == "field" }
        val before = g.coins
        assertTrue(g.plant(f, "corn"))
        assertEquals(before - Hamlet.cropById.getValue("corn").cost, g.coins)
        assertFalse("pas encore mur", g.harvest(f))
        t += 46_000
        assertEquals(Status.RIPE, g.status(f))
        assertTrue(g.harvest(f))
        assertEquals(before - 5 + 12, g.coins)
        assertNull(f.crop)
        assertEquals(1L, g.stats["harvested"])
    }

    @Test
    fun `une culture oubliee fletrit`() {
        val g = game()
        val f = g.objs.values.first { it.t == "field" }
        g.plant(f, "corn")
        t += (45L * 2 + 3600 + 5) * 1000
        assertEquals(Status.WITHERED, g.status(f))
        val c = g.coins
        assertTrue(g.harvest(f))
        assertEquals("rien n'est gagné", c, g.coins)
    }

    @Test
    fun `un champ magique refuse une culture ordinaire et inversement`() {
        val g = game(true)
        val (x, y) = freeSpot(g, "magicfield")
        val magic = g.buy("magicfield", x, y, false)!!
        assertFalse(g.plant(magic, "corn"))
        assertTrue(g.plant(magic, "glowcabbage"))
        val (x2, y2) = freeSpot(g, "field")
        val plain = g.buy("field", x2, y2, false)!!
        assertFalse(g.plant(plain, "glowcabbage"))
    }

    @Test
    fun `les impots se percoivent apres la periode et le charme les augmente`() {
        val g = game()
        val h = g.objs.values.first { it.t == "cottage" }
        assertFalse(g.collectTax(h))
        t += 5 * 60_000L + 1
        assertEquals(Status.TAX, g.status(h))
        // des fleurs et des arbres autour augmentent l'impôt
        g.sandbox = true
        var placed = 0
        for (dx in -3..4) for (dy in -3..4) {
            if (placed >= 12) break
            if (g.buy("flowersPink", h.x + dx, h.y + dy, false) != null) placed++
        }
        g.sandbox = false
        assertTrue(g.charmBonus(h) > 0)
        val c = g.coins
        assertTrue(g.collectTax(h))
        assertTrue("l'impôt dépasse le tarif de base", g.coins - c > h.def.tax)
    }

    @Test
    fun `un atelier recoit une commande puis la livre`() {
        val g = game(true)
        val (x, y) = freeSpot(g, "bakery")
        val b = g.buy("bakery", x, y, false)!!
        t += 60_000
        g.tick()
        assertNotNull(b.order)
        assertEquals(Status.ORDER_NEW, g.status(b))
        assertTrue(g.acceptOrder(b))
        assertFalse(g.deliver(b))
        t += Hamlet.byId.getValue("bakery").job * 1000L + 1
        assertEquals(Status.ORDER_DONE, g.status(b))
        val c = g.coins
        assertTrue(g.deliver(b))
        assertTrue(g.coins > c)
        assertNull(b.order)
    }

    @Test
    fun `un chantier se termine avec le temps ou avec des gemmes`() {
        val g = game()
        g.coins = 100000
        g.xp = 10_000_000
        val (x, y) = freeSpot(g, "lumbermill")
        val s = g.buy("lumbermill", x, y, false)!!
        assertEquals(Status.BUILDING, g.status(s))
        assertTrue(g.hurryCost(s) >= 1)
        g.gems = 0
        assertFalse(g.hurry(s))
        g.gems = 50
        assertTrue(g.hurry(s))
        assertEquals(0L, g.buildLeft(s))
        t += 11 * 60_000
        val (x2, y2) = freeSpot(g, "bakery")
        val b = g.buy("bakery", x2, y2, false)!!
        t += 2 * 60_000
        g.tick()
        assertEquals(0L, b.b0)
    }

    @Test
    fun `on ne peut pas construire sur un autre objet, hors du royaume ou au dessus du niveau`() {
        val g = game()
        val h = g.objs.values.first { it.t == "hall" }
        assertFalse(g.canPlace("cottage", h.x, h.y, false))
        assertFalse(g.canPlace("cottage", 0, 0, false))
        assertNull(g.buy("jousting", 30, 40, false))
    }

    @Test
    fun `l'eau se pose en peignant et les barques n'y flottent que la`() {
        val g = game(true)
        val o = g.realmOrigin() + 2
        assertFalse(g.canPlace("rowboat", o, o, false))
        assertTrue(g.paint("river", o, o))
        assertTrue(g.canPlace("rowboat", o, o, false))
        assertFalse(g.canPlace("cottage", o, o, false))
        assertTrue(g.paint("land", o, o))
        assertEquals(0, g.pathAt(o, o))
    }

    @Test
    fun `une pecherie doit toucher l'eau`() {
        val g = game(true)
        val o = g.realmOrigin() + 2
        assertFalse(g.canPlace("fishing", o + 5, o + 5, false))
        for (i in 0..3) g.paint("river", o + 4, o + 5 + i)
        assertTrue(g.canPlace("fishing", o + 5, o + 5, false))
    }

    @Test
    fun `vendre rembourse un quart et libere la place`() {
        val g = game(true)
        g.sandbox = false
        g.coins = 1000
        val (x, y) = freeSpot(g, "cottage")
        val c = g.buy("cottage", x, y, false)!!
        val after = g.coins
        assertTrue(g.sell(c))
        assertEquals(after + 25, g.coins)
        assertTrue(g.canPlace("cottage", x, y, false))
    }

    @Test
    fun `deplacer et tourner respectent la place`() {
        val g = game(true)
        val (x, y) = freeSpot(g, "stables")
        val s = g.buy("stables", x, y, false)!!
        assertEquals(3, s.w); assertEquals(2, s.d)
        assertTrue(g.move(s, x, y, true))
        assertEquals(2, s.w); assertEquals(3, s.d)
        assertEquals(s.id, g.occ[g.idx(x + 1, y + 2)])
        assertEquals(0, g.occ[g.idx(x + 2, y)])
    }

    @Test
    fun `monter de niveau donne des gemmes`() {
        val g = game()
        g.gems = 0
        g.addXp(30)
        assertEquals(2, g.level())
        assertEquals(1, g.gems)
        g.addXp(100_000)
        assertTrue(g.level() > 10)
        assertTrue(g.gems > 5)
    }

    @Test
    fun `la halle s'agrandit et le royaume avec`() {
        val g = game()
        g.coins = 1_000_000
        g.xp = 1_000_000
        val before = g.realmSize()
        assertTrue(g.upgradeHall())
        assertTrue(g.realmSize() > before)
        val e = g.realmSize()
        assertTrue(g.expandLand())
        assertEquals(e + 2, g.realmSize())
    }

    @Test
    fun `les quetes avancent et se reclament`() {
        val g = game()
        val f = g.objs.values.first { it.t == "field" }
        g.plant(f, "corn"); g.plant(g.objs.values.last { it.t == "field" }, "corn")
        val q = g.currentQuest()!!
        assertEquals("planted", q.key)
        assertFalse(g.questDone(q))
        val third = g.objs.values.first { it.t == "field" }
        t += 46_000
        g.harvest(third); g.plant(third, "corn")
        assertTrue(g.questDone(q))
        val coins = g.coins
        assertTrue(g.claimQuest())
        assertEquals(1, g.quest)
        assertTrue(g.coins > coins)
    }

    @Test
    fun `le cadeau du jour ne s'ouvre qu'une fois par jour`() {
        val g = game()
        assertFalse(g.giftReady())
        t += 4 * 60_000
        assertTrue(g.giftReady())
        assertTrue(g.openGift())
        assertFalse(g.giftReady())
        t += Hamlet.DAY_MS
        assertTrue(g.giftReady())
    }

    @Test
    fun `la sauvegarde relit le village a l'identique`() {
        val g = game(true)
        val (x, y) = freeSpot(g, "bakery")
        g.buy("bakery", x, y, true)
        val water = freeSpot(g, "field")
        assertTrue(g.paint("river", water.first, water.second))
        g.objs.values.first { it.t == "field" }.let { g.plant(it, "wheat") }
        val json = HamletSave.toJson(g)
        val h = HamletGame({ t }, Random(1))
        assertTrue(HamletSave.fromJson(h, json))
        assertEquals(g.objs.size, h.objs.size)
        assertEquals(g.coins, h.coins)
        assertEquals(g.sandbox, h.sandbox)
        assertEquals(Hamlet.PATH_WATER, h.pathAt(water.first, water.second))
        assertTrue(h.objs.values.any { it.t == "bakery" && it.flip })
        assertEquals("wheat", h.objs.values.first { it.crop != null }.crop)
        assertEquals(json, HamletSave.toJson(h))
    }

    @Test
    fun `une sauvegarde cassee est refusee`() {
        val h = HamletGame({ t }, Random(1))
        assertFalse(HamletSave.fromJson(h, "pas du json"))
        assertFalse(HamletSave.fromJson(h, "{\"objs\":[]}"))
    }

    @Test
    fun `le catalogue est coherent`() {
        val ids = Hamlet.defs.map { it.id }
        assertEquals("pas de doublon", ids.size, ids.toSet().size)
        for (d in Hamlet.defs) {
            assertTrue("${d.id} a une taille", d.w >= 1 && d.d >= 1)
            if (d.cat == Cat.HOME) assertTrue("${d.id} rapporte", d.tax > 0 && d.period > 0)
            if (d.cat == Cat.SHOP) assertTrue("${d.id} paie", d.pay > 0 && d.job > 0 && d.glyph.isNotEmpty())
            if (!d.isPath && d.cat != Cat.HALL) assertTrue("${d.id} a un prix", d.cost > 0 || d.gems > 0)
        }
        assertEquals(Hamlet.crops.size, Hamlet.crops.map { it.id }.toSet().size)
        assertTrue("cultures ordonnées par déblocage", Hamlet.crops.zipWithNext().all { it.first.lvl <= it.second.lvl })
        assertEquals(Hamlet.pathCode.size, Hamlet.pathCode.values.toSet().size)
    }

    // ------------------------------------------------------------------ textes

    private fun strings(dir: String): Set<String> {
        val out = HashSet<String>()
        File("src/main/res/$dir").listFiles { f -> f.name.startsWith("strings_hamlet") && f.name.endsWith(".xml") }!!.forEach { f ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) out.add(nodes.item(i).attributes.getNamedItem("name").nodeValue)
        }
        return out
    }

    @Test
    fun `chaque objet, culture, quete et produit a ses textes en anglais et en francais`() {
        val en = strings("values")
        val fr = strings("values-fr")
        val needed = ArrayList<String>()
        for (d in Hamlet.defs) {
            if (d.cat == Cat.HALL) { for (i in 1..Hamlet.halls.size) needed.add("hamlet_n_hall$i") } else needed.add("hamlet_n_${d.id}")
            if (d.cat == Cat.SHOP) needed.add("hamlet_n_p_${d.id}")
        }
        for (c in Hamlet.crops) needed.add("hamlet_n_c_${c.id}")
        for (i in Hamlet.quests.indices) { needed.add("hamlet_q${i}_t"); needed.add("hamlet_q${i}_d") }
        for ((type, count) in listOf("chapter" to 12, "region" to 6, "companion" to 6, "research" to 3))
            for (i in 0 until count) { needed.add("hamlet_${type}_$i"); needed.add("hamlet_${type}_story_$i") }
        for (i in 0..14) needed.add("hamlet_tech_$i")
        val missingEn = needed.filter { it !in en }
        val missingFr = needed.filter { it !in fr }
        assertTrue("manque en anglais : $missingEn", missingEn.isEmpty())
        assertTrue("manque en français : $missingFr", missingFr.isEmpty())
        assertEquals("mêmes clés dans les deux langues", en, fr)
    }
}
