package com.Atom2Universe.app.games.hamlet

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletStewardshipTest {
    private var time = 1_800_000_000_000L
    private fun game() = HamletGame({ time }, Random(5)).apply { startNew(false) }.withStarters()

    @Test fun `les gestes du village et les aventures alimentent le meme carnet sans doubles gains`() {
        val g = game(); val s = g.stewardship
        g.stat("harvested", 2); g.stat("products", 2); g.stat("taxes"); g.stat("delivered")
        g.stat("battles_won"); g.stat("adventure_gathered"); g.stat("expeditions")
        assertTrue(g.adventure.levelUp(0)); assertTrue(g.adventure.levelUp(1))
        assertTrue(s.canClaim(0)); assertTrue(s.canClaim(1)); assertTrue(s.canClaim(2)); assertFalse(s.canClaimChest())
        val before = g.adventure[AdventureResource.TICKETS]
        for (i in 0..2) { assertTrue(s.claim(i)); assertFalse(s.claim(i)) }
        assertEquals(before + 3, g.adventure[AdventureResource.TICKETS])
        val work = g.workSeconds
        assertTrue(s.claimChest()); assertFalse(s.claimChest())
        assertEquals(before + 4, g.adventure[AdventureResource.TICKETS]); assertEquals(180, g.workSeconds - work)
        val bag = g.adventure.bag.clone()
        val restored = game(); assertTrue(HamletSave.fromJson(restored, HamletSave.toJson(g)))
        assertFalse(restored.stewardship.hasRewards()); assertArrayEquals(bag, restored.adventure.bag)
        assertFalse(restored.stewardship.claimChest())
    }
    @Test fun `le premier geste du lendemain compte et un recul de lhorloge ne renouvelle pas les gains`() {
        val g = game(); g.stat("harvested", 6); assertTrue(g.stewardship.claim(0))
        time = (time / Hamlet.DAY_MS + 1) * Hamlet.DAY_MS + 1000
        g.stat("harvested")
        assertEquals(1L, g.stewardship.progress(0)); assertFalse(g.stewardship.canClaim(0))
        g.stat("harvested", 5); assertTrue(g.stewardship.claim(0))
        time -= Hamlet.DAY_MS
        assertFalse(g.stewardship.claim(0))
        g.stewardship.reset(); assertFalse(g.stewardship.claim(-1)); assertFalse(g.stewardship.claim(3))
    }
    @Test fun `les affectations sont uniques et ne retirent jamais lequipe de combat`() {
        val g = game(); val s = g.stewardship; val team = g.adventure.teams[0].clone()
        assertTrue(s.assign(0, 0)); assertEquals(10, s.bonus(0)); assertEquals(10, s.villageBonus(Cat.FIELD))
        assertTrue(s.assign(1, 0)); assertEquals(-1, s.posts[0]); assertEquals(5, s.bonus(1))
        assertFalse(s.assign(2, 10)); assertFalse(s.assign(3, 0)); assertFalse(s.assign(2, -2))
        assertArrayEquals(team, g.adventure.teams[0]); assertNotNull(g.adventure.beginBattle(BattleMode.CAMPAIGN, 0))
        g.adventure.abandonBattle()
        g.adventure.heroes[0].level = 1000; g.adventure.heroes[0].stars = 1000
        assertTrue(s.assign(0, 0)); assertEquals(25, s.bonus(0)); assertTrue(s.assign(0, -1)); assertEquals(0, s.bonus(0))
    }
    @Test fun `une nouvelle affectation ne reecrit pas la production durant labsence`() {
        val g = game(); val food = g.adventure[AdventureResource.FOOD]
        time += 10 * 60_000
        assertTrue(g.stewardship.assign(0, 0))
        assertEquals(food + 40, g.adventure[AdventureResource.FOOD])
        time += 10 * 60_000; g.adventure.advanceProduction()
        assertEquals(food + 84, g.adventure[AdventureResource.FOOD])
        val field = g.objs.values.first { it.def.cat == Cat.FIELD }
        assertEquals(110, g.yieldCoins(field, 100))
    }
    @Test fun `une ancienne sauvegarde ne donne pas de recompenses retroactives et les postes corrompus sont nettoyes`() {
        val g = game(); g.stat("harvested", 100)
        val legacy = JSONObject(HamletSave.toJson(g)).apply { remove("stewardship") }
        val restored = game(); assertTrue(HamletSave.fromJson(restored, legacy.toString()))
        assertEquals(0L, restored.stewardship.progress(0)); assertTrue(restored.stewardship.posts.all { it == -1 })
        assertTrue(g.stewardship.assign(0, 0))
        val bad = JSONObject(HamletSave.toJson(g))
        bad.getJSONObject("stewardship").getJSONArray("posts").put(1, 0).put(2, 999)
        assertTrue(HamletSave.fromJson(restored, bad.toString()))
        assertArrayEquals(intArrayOf(0, -1, -1), restored.stewardship.posts)
    }
    @Test fun `les heros renforcent les voyages et le retour recompense les deux systemes une seule fois`() {
        val g = game(); val j = g.journey
        val startingDuration = j.expeditionSeconds(0)
        assertTrue(g.adventure.assign(0, 2))
        g.adventure.heroes[0].level = 20; g.adventure.heroes[0].stars = 3
        assertTrue(j.scoutingPower() > 0); assertTrue(j.expeditionSeconds(0) < startingDuration)
        assertTrue(j.startExpedition(0)); val arrival = j.arrival
        g.adventure.heroes[0].stars = 0; assertEquals(arrival, j.arrival)
        time = arrival; val before = g.adventure.bag.clone()
        assertTrue(j.collectExpedition()); assertFalse(j.collectExpedition())
        assertEquals(before[0] + j.expeditionFood(0), g.adventure[AdventureResource.FOOD])
        assertEquals(before[7] + 1, g.adventure[AdventureResource.TICKETS]); assertEquals(4, j.supplies)
    }
}
