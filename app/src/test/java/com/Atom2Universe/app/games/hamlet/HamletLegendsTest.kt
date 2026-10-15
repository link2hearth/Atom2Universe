package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Les légendes ne sortent jamais d'un appel : une rumeur, un cercle de pierres et trois veillées. */
class HamletLegendsTest {
    private var time = 1_800_000_000_000L
    private fun game() = HamletGame({ time }, Random(5)).apply { startNew(false) }.established()
    private fun circle(g: HamletGame) {
        g.coins = 10_000_000; g.xp = 5_000_000
        val origin = g.realmOrigin()
        for (y in origin until origin + g.realmSize()) for (x in origin until origin + g.realmSize())
            if (g.canPlace(HamletLegends.CIRCLE, x, y, false)) {
                val o = g.buy(HamletLegends.CIRCLE, x, y, false)!!; time += o.def.build * 1000L + 1; g.tick(); return
            }
        error("pas de place pour le cercle")
    }
    private fun feed(g: HamletGame, legend: Int) {
        val phase = g.legends.phase(legend)!!
        g.coins = maxOf(g.coins, phase.coins + 1); g.adventure.add(AdventureResource.CRYSTAL, phase.crystal)
        for (n in phase.needs) g.pantry[n.crop] = n.units
    }

    @Test fun `aucune legende ne sort d'un appel`() {
        val g = game(); val a = g.adventure
        a.add(AdventureResource.TICKETS, 3); a.summon(3)
        repeat(1_500) { a.add(AdventureResource.TICKETS, 1); a.summon() }
        assertTrue(a.heroes.filter { it.owned }.none { it.def.rarity.ordinal >= HeroRarity.LEGENDARY.ordinal })
    }

    @Test fun `la rumeur precede l'ouverture d'un chapitre et le mythique attend les trois autres`() {
        val g = game(); val l = g.legends
        assertFalse(l.whispered(0)); g.journey.chapter = HamletLegends.defs[0].chapter - 1
        assertTrue(l.whispered(0)); assertFalse(l.opened(0))
        g.journey.chapter = HamletLegends.defs[0].chapter; assertTrue(l.opened(0))
        g.journey.chapter = 12
        assertTrue(l.whispered(3)); assertFalse(l.opened(3))
        for (id in HamletLegends.defs[3].requires) g.adventure.heroes[id].owned = true
        assertTrue(l.opened(3))
    }

    @Test fun `trois nuits de veillee avec attente reelle puis le heros arrive`() {
        val g = game(); val l = g.legends; g.journey.chapter = 6
        assertFalse(l.canBegin(0)); feed(g, 0); assertFalse("sans cercle", l.canBegin(0))
        circle(g); feed(g, 0); assertTrue(l.canBegin(0))
        val hero = HamletLegends.defs[0].hero
        for (night in 1..3) {
            feed(g, 0)
            assertTrue(l.begin(0)); assertFalse("une veillee a la fois", l.begin(0))
            assertFalse(l.canFinish(0)); assertNull(l.finish(0))
            time += (l.phase(0)?.hours ?: HamletLegends.defs[0].phases[night - 1].hours) * 3_600_000L - 1_000L
            assertFalse("sans travail investi, la durée de base reste entière", l.canFinish(0))
            time += 2_000L
            val step = l.finish(0)!!
            assertEquals(night, step.night)
            if (night < 3) { assertNull(step.recruit); assertFalse(g.adventure.heroes[hero].owned) }
            else { assertTrue(step.recruit!!.newHero); assertTrue(step.recruit!!.legend); assertTrue(g.adventure.heroes[hero].owned) }
        }
        assertTrue(l.owned(0)); assertNull(l.phase(0)); assertFalse(l.canBegin(0))
        assertEquals(1L, g.stats["legends"])
    }

    @Test fun `une veillee prend les pieces, les cristaux et les recoltes de la reserve`() {
        val g = game(); g.journey.chapter = 6; circle(g); feed(g, 0)
        val phase = g.legends.phase(0)!!
        val coins = g.coins; val crystal = g.adventure[AdventureResource.CRYSTAL]
        assertTrue(g.legends.begin(0))
        assertEquals(coins - phase.coins, g.coins); assertEquals(crystal - phase.crystal, g.adventure[AdventureResource.CRYSTAL])
        for (n in phase.needs) assertEquals(0, g.stock(n.crop))
    }

    @Test fun `la veillee survit a la sauvegarde`() {
        val g = game(); g.journey.chapter = 6; circle(g); feed(g, 0); assertTrue(g.legends.begin(0))
        val copy = game()
        assertTrue(HamletSave.fromJson(copy, HamletSave.toJson(g)))
        assertTrue(copy.legends.running(0)); assertEquals(g.legends.until[0], copy.legends.until[0])
        assertEquals(g.legends.nights.toList(), copy.legends.nights.toList())
    }

    @Test fun `chaque phase demande des cultures qui existent et le cercle est un decor du catalogue`() {
        assertNotNull(Hamlet.byId[HamletLegends.CIRCLE])
        for (def in HamletLegends.defs) for (phase in def.phases) {
            assertTrue(phase.hours >= 12); assertTrue(phase.needs.isNotEmpty())
            for (n in phase.needs) { assertTrue(n.crop in Hamlet.cropById); assertTrue(n.units <= HamletEconomy.pantryCap(1)) }
        }
        assertEquals(setOf(HeroRarity.LEGENDARY, HeroRarity.MYTHIC), HamletLegends.defs.map { HamletHeroes.defs[it.hero].rarity }.toSet())
    }
}
