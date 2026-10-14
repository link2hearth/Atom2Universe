package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Les promesses de l'économie : une base, une courbe de durée, le niveau de déblocage ne change pas le prix. */
class HamletEconomyTest {
    private var time = 1_800_000_000_000L
    private fun game() = HamletGame({ time }, Random(9)).apply { startNew(false) }

    @Test fun `les cultures courtes paient deux a trois fois la base et les longues une demie a une fois`() {
        for (c in Hamlet.crops.filter { !it.magic }) {
            val perHour = c.earn * 3600.0 / c.time / HamletEconomy.BASE_PER_HOUR
            if (c.time <= 900) assertTrue("${c.id} : ×%.2f".format(perHour), perHour in 2.0..3.4)
            if (c.time >= 14_400) assertTrue("${c.id} : ×%.2f".format(perHour), perHour in 0.45..1.05)
        }
    }

    @Test fun `le niveau de deblocage ne compte pas dans le prix`() {
        // le gain ne dépend que de la durée : deux cultures de même durée rapportent pareil, quel que soit leur niveau
        for ((_, group) in Hamlet.crops.filter { !it.magic }.groupBy { it.time }) assertEquals(1, group.map { it.earn }.distinct().size)
        for (c in Hamlet.crops) assertEquals(HamletEconomy.earn(c.time, c.magic), c.earn)
        // le gain par heure ne dépend que de la durée : il ne remonte jamais quand la durée s'allonge, quel que soit le niveau de la culture
        val sorted = Hamlet.crops.filter { !it.magic }.sortedBy { it.time }
        for (i in 1 until sorted.size) {
            val a = sorted[i - 1]; val b = sorted[i]
            assertTrue("${a.id} (${a.time} s) puis ${b.id} (${b.time} s)", b.earn * 3600.0 / b.time <= a.earn * 3600.0 / a.time * 1.03)
        }
    }

    @Test fun `la courbe de duree descend sans a-coup`() {
        var last = Double.MAX_VALUE
        for (seconds in listOf(30, 60, 300, 900, 1_800, 3_600, 7_200, 14_400, 28_800, 57_600, 86_400, 172_800)) {
            val f = HamletEconomy.factor(seconds); assertTrue("$seconds s : $f", f <= last + 1e-9); last = f
        }
        assertEquals(3.0, HamletEconomy.factor(60), 0.001); assertEquals(1.0, HamletEconomy.factor(14_400), 0.001); assertEquals(0.5, HamletEconomy.factor(86_400), 0.001)
    }

    @Test fun `une graine coute une fraction du gain et l'experience reste proportionnelle`() {
        for (c in Hamlet.crops) { assertTrue(c.cost < c.earn); assertTrue(c.xp in 1..c.earn) }
    }

    @Test fun `la recolte remplit la reserve, qui a un plafond`() {
        val g = game(); g.coins = 100_000; g.xp = 1_000_000
        val field = g.objs.values.first { it.def.cat == Cat.FIELD }
        assertEquals(0, g.stock("wheat"))
        assertTrue(g.plant(field, "wheat")); time += 400_000L; g.tick()
        assertTrue(g.harvest(field)); assertEquals(HamletEconomy.UNITS_PER_HARVEST, g.stock("wheat"))
        g.addStock("wheat", 10_000); assertEquals(g.pantryCap(), g.stock("wheat"))
        assertTrue(HamletEconomy.pantryCap(8) > HamletEconomy.pantryCap(1))
    }

    @Test fun `une amelioration demande des pieces et des recoltes precises, toujours les memes`() {
        val g = game(); g.coins = 1_000_000; g.xp = 5_000_000
        val field = g.objs.values.first { it.def.cat == Cat.FIELD }
        val needs = g.upgradeNeeds(field)
        assertTrue(needs.isNotEmpty()); assertEquals(needs, g.upgradeNeeds(field))
        assertFalse("sans les recoltes", g.canAffordUpgrade(field))
        for (n in needs) g.pantry[n.crop] = n.units
        val coins = g.coins; val cost = g.upgradeCost(field)
        assertTrue(g.upgrade(field)); assertEquals(2, field.tier); assertEquals(coins - cost, g.coins)
        for (n in needs) assertTrue(g.stock(n.crop) <= HamletEconomy.pantryCap(1) - n.units)
        // plus on monte, plus c'est lourd
        assertTrue(HamletEconomy.upgradeUnits(5) > HamletEconomy.upgradeUnits(2))
        assertEquals(HamletEconomy.MAX_TIER, 5)
    }

    @Test fun `un rang de plus rapporte plus de pieces et plus d'experience`() {
        val g = game(); g.coins = 100_000; g.xp = 1_000_000
        val field = g.objs.values.first { it.def.cat == Cat.FIELD }
        fun harvestOnce(): Pair<Long, Long> {
            val coins = g.coins.toLong(); val xp = g.xp
            assertTrue(g.plant(field, "wheat")); time += 400_000L; g.tick(); assertTrue(g.harvest(field))
            return (g.coins - coins) to (g.xp - xp)
        }
        val (c1, x1) = harvestOnce()
        field.tier = 3
        val (c3, x3) = harvestOnce()
        assertTrue("pièces $c1 -> $c3", c3 > c1); assertTrue("xp $x1 -> $x3", x3 > x1)
    }
}
