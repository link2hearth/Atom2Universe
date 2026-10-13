package com.Atom2Universe.app.games.hamlet

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletJourneyTest {
    private var time = 1_800_000_000_000L
    private fun game() = HamletGame({ time }, Random(7)).apply { startNew(false) }
    private fun buy(g: HamletGame, id: String): Obj? {
        val start = g.realmOrigin()
        for (y in start until start + g.realmSize()) for (x in start until start + g.realmSize())
            if (g.canPlace(id, x, y, false)) return g.buy(id, x, y, false)
        error("aucune place pour $id")
    }

    @Test fun `le niveau seul ne debloque pas les decouvertes et le mode libre les ouvre`() {
        val g = game(); g.xp = 10_000_000; g.coins = 10_000_000
        assertNull(buy(g, "waypost"))
        g.journey.chapter = 1
        assertNotNull(buy(g, "waypost"))
        g.hall = 5
        assertFalse(g.upgradeHall())
        g.sandbox = true
        assertTrue(g.upgradeHall())
        assertNotNull(buy(g, "festivalhall"))
    }

    @Test fun `chaque objectif de chapitre est accessible avant son deblocage`() {
        HamletJourney.chapters.forEachIndexed { i, c ->
            for (goal in c.goals) {
                if (goal.key.startsWith("built_")) {
                    val id = goal.key.removePrefix("built_")
                    assertTrue(id in Hamlet.byId)
                    assertTrue((HamletJourney.buildings[id] ?: 0) <= i)
                    assertTrue(Hamlet.byId.getValue(id).lvl <= c.level)
                }
                if (goal.key.startsWith("region_")) {
                    val r = goal.key.removePrefix("region_").toInt()
                    assertTrue(HamletJourney.regions[r].chapter <= i)
                }
            }
            assertTrue(game().journey.hallChapter(c.hall) <= i)
        }
        assertTrue(Hamlet.defs.all { it.build <= 2 * Hamlet.HOUR })
    }

    @Test fun `reclamer un chapitre ne paie pas deux fois et recrute un compagnon`() {
        val g = game(); g.xp = 80; g.stats["harvested"] = 8; g.stats["built_bench"] = 1
        assertTrue(g.journey.claim())
        assertEquals(1, g.journey.chapter)
        assertEquals(1, g.journey.companions[0]); assertEquals(0, g.journey.party[0])
        val coins = g.coins; val supplies = g.journey.supplies
        assertFalse(g.journey.claim()); assertEquals(coins, g.coins); assertEquals(supplies, g.journey.supplies)
    }

    @Test fun `une expedition exige une equipe puis donne une seule recompense meme hors ligne`() {
        val g = game(); val j = g.journey
        assertFalse(j.startExpedition(1))
        assertTrue(j.startExpedition(0)); assertFalse(j.startExpedition(0)); assertFalse(j.collectExpedition())
        time += 20 * 60_000
        assertTrue(HamletSave.fromJson(g, HamletSave.toJson(g)))
        assertTrue(j.collectExpedition()); val coins = g.coins
        assertEquals(4, j.supplies); assertEquals(1, j.explored[0])
        assertFalse(j.collectExpedition()); assertEquals(coins, g.coins)
        j.chapter = 2
        assertFalse(j.startExpedition(1))
        j.companions[0] = 1; assertTrue(j.assign(0, 0)); assertTrue(j.startExpedition(1))
    }

    @Test fun `recherche et entrainement respectent ressources chapitres et plafonds`() {
        val g = game(); val j = g.journey; g.coins = 1_000_000; j.supplies = 10_000
        assertFalse(j.study(0)); j.chapter = 2; assertTrue(j.study(0)); assertFalse(j.study(0))
        assertEquals(1, j.research[0]); assertEquals(999_600L, g.coins)
        j.companions[0] = 1; assertFalse(j.train(0)); j.chapter = 12
        assertTrue(j.train(0))
        while (j.canTrain(0)) assertTrue(j.train(0))
        assertEquals(5, j.companions[0]); assertFalse(j.train(0))
        while (j.canResearch(0)) assertTrue(j.study(0))
        assertEquals(5, j.research[0]); assertFalse(j.study(0))
        assertFalse(j.study(-1)); assertFalse(j.train(9)); assertFalse(j.assign(9, 0))
    }

    @Test fun `les bonus ne multiplient pas un compagnon assigne deux fois et les dates sont figees`() {
        val g = game(); val j = g.journey; j.chapter = 12
        j.companions.fill(5); j.research.fill(5)
        assertTrue(j.assign(2, 0)); assertTrue(j.assign(2, 1)); assertEquals(1, j.party.count { it == 2 })
        j.assign(5, 2)
        assertEquals(HamletJourney.regions[0].seconds * 70 / 100, j.expeditionSeconds(0))
        assertTrue(j.startExpedition(0)); val arrival = j.arrival
        j.assign(0, 1); assertEquals(arrival, j.arrival)
        assertEquals(30, j.bonus(Cat.FIELD))
    }

    @Test fun `le petit atelier est gratuit repetable et sans penalite pour une erreur`() {
        val g = game(); g.coins = 0; g.gems = 0; g.beginWork()
        val wanted = g.workTool
        assertFalse(g.answerWork((wanted + 1) % 3)); assertEquals(0, g.workStep)
        repeat(12) { assertTrue(g.answerWork(g.workTool)) }
        assertEquals(300, g.workSeconds); assertEquals(0L, g.coins); assertEquals(0, g.gems)
        repeat(23 * 12) { assertTrue(g.answerWork(g.workTool)) }
        assertEquals(7200, g.workSeconds); assertFalse(g.answerWork(g.workTool))
    }

    @Test fun `le farm gagne du travail et aide vraiment un chantier sans depense`() {
        val g = game(); g.coins = 100000; g.xp = 100000
        val b = buy(g, "lumbermill")!!; val before = g.buildLeft(b)
        g.stat("harvested", 2); g.stat("products", 2); g.stat("delivered")
        assertEquals(80, g.workSeconds)
        val coins = g.coins; val gems = g.gems
        assertTrue(g.applyWork(b)); assertEquals(before - 80_000, g.buildLeft(b))
        assertEquals(coins, g.coins); assertEquals(gems, g.gems); assertFalse(g.applyWork(b))
        g.workSeconds = 7200
        while (g.buildLeft(b) > 0) assertTrue(g.applyWork(b))
        assertEquals(0L, b.b0); assertFalse(g.applyWork(Obj(999, "lumbermill", 0, 0)))
    }

    @Test fun `un voyage long est accelerable gratuitement et la reserve est consommee`() {
        val g = game(); val j = g.journey; j.chapter = 12; j.companions.fill(5)
        j.assign(0, 0); j.assign(1, 1); j.assign(3, 2)
        assertTrue(j.startExpedition(5)); val left = j.expeditionLeft()
        g.workSeconds = 600
        assertTrue(g.workOnExpedition()); assertEquals(left - 300_000, j.expeditionLeft())
        assertTrue(g.workOnExpedition()); assertFalse(g.workOnExpedition())
        assertEquals(0, g.workSeconds)
    }

    @Test fun `les cultures les commandes et les animaux acceptent aussi le travail gratuit`() {
        val g = game(); val f = g.objs.values.first { it.t == "field" }
        assertTrue(g.plant(f, "corn")); g.workSeconds = 100
        assertTrue(g.applyWork(f)); assertEquals(Status.RIPE, g.status(f)); assertEquals(55, g.workSeconds)
        g.sandbox = true
        val b = buy(g, "bakery")!!; b.order = Order("Odile", time)
        g.workSeconds = 300; val left = g.jobLeft(b)
        assertTrue(g.applyWork(b)); assertTrue(g.jobLeft(b) < left)
        val hen = buy(g, "henyard")!!; g.workSeconds = 200
        assertFalse(g.applyWork(hen)); assertTrue(g.feed(hen)); assertTrue(g.applyWork(hen))
        assertEquals(Status.PRODUCT, g.status(hen))
    }

    @Test fun `la sauvegarde conserve progres equipe voyage et gestes de travail`() {
        val g = game(); val j = g.journey
        j.chapter = 7; j.supplies = 113; j.research[0] = 2; j.companions[1] = 3; j.assign(1, 0)
        j.explored[2] = 8; j.startExpedition(1)
        g.beginWork(); repeat(4) { g.answerWork(g.workTool) }; g.workSeconds = 52
        val json = HamletSave.toJson(g); val other = game()
        assertTrue(HamletSave.fromJson(other, json)); assertEquals(json, HamletSave.toJson(other))
        // Migration : la v2 ne retire aucune construction ni aucun acquis du village.
        val old = JSONObject(json).put("version", 2).apply { remove("journey"); remove("workSeconds"); remove("workStep"); remove("workTool") }
        assertTrue(HamletSave.fromJson(other, old.toString()))
        assertEquals(0, other.journey.chapter); assertEquals(g.objs.size, other.objs.size); assertEquals(g.coins, other.coins)
    }
}
