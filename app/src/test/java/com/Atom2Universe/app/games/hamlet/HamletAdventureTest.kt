package com.Atom2Universe.app.games.hamlet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletAdventureTest {
    private var time = 1_800_000_000_000L
    private fun game() = HamletGame({ time }, Random(3)).apply { startNew(false) }
    private fun finish(b: HamletBattle) { b.advance(100f); assertNotEquals(BattleOutcome.RUNNING, b.outcome) }
    private fun facility(g: HamletGame, id: String) {
        g.coins = 1_000_000; g.xp = 1_000_000
        val origin = g.realmOrigin()
        for (y in origin until origin + g.realmSize()) for (x in origin until origin + g.realmSize())
            if (g.canPlace(id, x, y, false)) { val o = g.buy(id, x, y, false)!!; time += o.def.build * 1000L + 1; return }
        error(id)
    }

    @Test fun `un combat valide gagne une fois et un abandon ne consomme rien`() {
        val g = game(); val a = g.adventure; val b = a.beginBattle(BattleMode.CAMPAIGN, 0, 8)!!
        assertNull(a.beginBattle(BattleMode.CAMPAIGN, 0))
        assertFalse(a.settle(b)); finish(b); assertEquals(BattleOutcome.VICTORY, b.outcome)
        assertTrue(a.settle(b)); val loot = a.bag.clone(); assertFalse(a.settle(b)); assertArrayEquals(loot, a.bag)
        assertEquals(1, a.frontier); assertTrue(g.workSeconds >= 45)
        val before = a.bag.clone(); val retry = a.beginBattle(BattleMode.CAMPAIGN, 0, 8)!!
        a.abandonBattle(); finish(retry); assertFalse(a.settle(retry)); assertArrayEquals(before, a.bag)
        assertNull(a.beginBattle(BattleMode.RAID, 0)); assertNull(a.beginBattle(BattleMode.CAMPAIGN, 99))
    }
    @Test fun `defaite gratuite et farm sans limite quotidienne`() {
        val g = game(); val a = g.adventure; a.frontier = 200
        val before = a.bag.clone(); val b = a.beginBattle(BattleMode.CAMPAIGN, 200, 2)!!
        finish(b); assertEquals(BattleOutcome.DEFEAT, b.outcome); assertTrue(a.settle(b))
        assertArrayEquals(before, a.bag); assertNotNull(a.beginBattle(BattleMode.CAMPAIGN, 200, 2)); a.abandonBattle()
        repeat(100) { assertTrue(a.gather(1)) }
        assertTrue(a[AdventureResource.FOOD] > before[0] + 3000)
        assertFalse(a.gather(201)); assertFalse(a.gather(0)); assertEquals(200, a.frontier)
        assertTrue(g.workSeconds in 4500..7200)
    }
    @Test fun `formations uniques bonus de faction et liens dependants de la vraie equipe`() {
        val g = game(); val a = g.adventure
        assertFalse(a.assign(2, 0)); a.heroes[2].owned = true
        assertTrue(a.assign(2, 1)); assertTrue(a.assign(2, 2))
        assertEquals(1, a.teams[0].count { it == 2 }); assertEquals(0, a.teams[0][1])
        assertEquals(listOf(0), a.activeBonds()); assertEquals(0.1, a.factionBonus(HeroFaction.GROVE), 0.0001)
        assertFalse(a.assign(2, 5)); a.frontier = 24; assertTrue(a.assign(2, 5))
        assertTrue(a.selectTeam(1)); assertArrayEquals(a.teams[0], a.teams[1])
        assertTrue(a.remove(5)); assertNotEquals(a.teams[0][5], a.teams[1][5])
        assertTrue(HamletHeroes.counter(HeroFaction.GROVE, HeroFaction.FORGE) > 1)
        assertTrue(HamletHeroes.counter(HeroFaction.FORGE, HeroFaction.GROVE) < 1)
    }
    @Test fun `forge recherche recrutement et traits respectent les ressources et les batiments`() {
        val g = game(); val a = g.adventure
        assertFalse(a.forge(0, 0)); assertFalse(a.research(0)); assertFalse(a.focusRecruit(2))
        a.frontier = 6; assertTrue(a.focusRecruit(2)); assertTrue(a.heroes[2].owned)
        assertFalse(a.focusRecruit(2)); a.add(AdventureResource.TICKETS, 3); assertTrue(a.focusRecruit(2)); assertEquals(8, a.heroes[2].shards)
        assertTrue(a.promote(2)); assertEquals(3, a.heroes[2].shards); assertFalse(a.promoteTrait(2, 0))
        a.heroes[2].stars = 5; a.heroes[2].shards = 12; assertTrue(a.promoteTrait(2, 0)); assertEquals(0, a.heroes[2].shards)
        facility(g, "adventureForge"); assertTrue(a.forge(0, 0)); assertEquals(1, a.heroes[0].gear[0]); assertFalse(a.refine(0, 0))
        a.heroes[0].gear[0] = 5; a.add(AdventureResource.CRYSTAL, 10); assertTrue(a.refine(0, 0))
        facility(g, "adventureAcademy"); assertTrue(a.research(0)); assertFalse(a.research(0))
        val power = a.numbers(0).attack; a.heroes[0].level = 100; assertTrue(a.numbers(0).attack > power * 3)
    }
    @Test fun `production hors ligne et changement de metier ne reecrivent pas le passe`() {
        val g = game(); val a = g.adventure; val before = a.bag.clone()
        time += 10 * 60_000; assertTrue(a.assignWorker(0, -1)); assertTrue(a.assignWorker(3, 1))
        assertEquals(before[0] + 40, a[AdventureResource.FOOD]); assertEquals(before[4], a[AdventureResource.BOOKS])
        time += 2 * 60_000; a.advanceProduction(); assertEquals(before[4] + 2, a[AdventureResource.BOOKS])
        val snapshot = a.bag.clone(); a.advanceProduction(); assertArrayEquals(snapshot, a.bag)
        assertFalse(a.assignWorker(4, 1)); time -= 300_000; a.advanceProduction(); assertArrayEquals(snapshot, a.bag)
        time += 30L * 24 * 60 * 60_000; a.advanceProduction(); assertTrue(a[AdventureResource.FOOD] > 40_000)
    }
    @Test fun `ultime manuel soins boucliers et invocations sont vraiment executes`() {
        val healer = BattleUnit(0, 3, false, HeroFaction.GROVE, HeroRole.HEALER, 2, 2000.0, 100.0, 100.0, 2f, 8f, 20f)
        val guard = BattleUnit(1, 1, false, HeroFaction.FORGE, HeroRole.GUARDIAN, 0, 3000.0, 80.0, 100.0, 2f, 8f, 20f)
        val summon = BattleUnit(2, 9, false, HeroFaction.GROVE, HeroRole.SUMMONER, 3, 2000.0, 80.0, 100.0, 2f, 8f, 20f)
        val foe = BattleUnit(100, 4, true, HeroFaction.DAWN, HeroRole.DUELIST, 0, 20_000.0, 70.0, 100.0, 2f, 60f, 100f)
        val b = HamletBattle(BattleMode.CAMPAIGN, 0, 0, arrayListOf(healer, guard, summon, foe), Random(1), autoUltimates = false)
        guard.hp = 1000.0; assertFalse(b.ultimate(0)); b.advance(16f)
        assertTrue(b.healing > 0); assertTrue(b.units.any { it.summoned }); assertTrue(b.readyUltimates().isNotEmpty())
        healer.ultimateLeft = 0f; assertTrue(b.ultimate(0)); assertFalse(b.ultimate(0))
        guard.ultimateLeft = 0f; assertTrue(b.ultimate(1)); assertTrue(healer.shield > 0)
        assertFalse(b.ultimate(100)); assertTrue(b.damage > 0)
    }
    @Test fun `combat deterministe independant de la cadence du rendu`() {
        val a = game().adventure.beginBattle(BattleMode.CAMPAIGN, 0, 42)!!
        val b = game().adventure.beginBattle(BattleMode.CAMPAIGN, 0, 42)!!
        a.advance(90f); repeat(1800) { b.advance(0.05f) }
        assertEquals(a.outcome, b.outcome); assertEquals(a.time, b.time, 0.001f); assertEquals(a.damage, b.damage, 0.001)
        assertEquals(a.units.map { it.hp }, b.units.map { it.hp })
    }
    @Test fun `arene solo vrais heros et supports se debloquent dans la campagne`() {
        val a = game().adventure; a.frontier = 19; assertTrue(a.gather(20).not())
        a.frontier = 20; assertTrue(a.gather(20)); assertEquals(1, a.pets[0]); assertEquals(1, a.relics[0])
        assertTrue(a.selectSupport(true, 0)); assertFalse(a.selectSupport(true, 2)); assertTrue(a.selectSupport(false, 0))
        val b = a.beginBattle(BattleMode.ARENA, 0, 2)!!
        assertTrue(b.units.filter { it.enemy }.all { it.heroVisual }); assertEquals(0, b.pet); assertEquals(0, b.relic)
    }
    @Test fun `sauvegarde complete migration et equipes corrompues reste jouable`() {
        val g = game(); val a = g.adventure; a.frontier = 80; a.heroes[9].owned = true; a.heroes[9].level = 72
        a.heroes[9].gear[0] = 12; a.heroes[9].skills[2] = 9; a.pets[2] = 3; a.teamPets[0] = 2
        assertTrue(a.assign(9, 5)); a.stars[79] = 2; val saved = HamletSave.toJson(g)
        val restored = game(); assertTrue(HamletSave.fromJson(restored, saved)); val r = restored.adventure
        assertEquals(80, r.frontier); assertEquals(72, r.heroes[9].level); assertEquals(9, r.heroes[9].skills[2]); assertArrayEquals(a.bag, r.bag)
        assertEquals(9, r.teams[0][5]); assertEquals(2, r.teamPets[0]); assertNull(r.activeBattle)
        val bad = JSONObject(saved); val adv = bad.getJSONObject("adventure")
        adv.put("teams", JSONArray().put(JSONArray(listOf(900, 9, 9, -20, 2, 2)))); adv.put("team", 999)
        assertTrue(HamletSave.fromJson(restored, bad.toString())); assertTrue(restored.adventure.teams[2].any { it >= 0 })
        assertEquals(1, restored.adventure.teams[0].count { it == 9 })
        bad.remove("adventure"); bad.put("version", 3); assertTrue(HamletSave.fromJson(restored, bad.toString())); assertEquals(0, restored.adventure.frontier)
    }
    @Test fun `mode libre ouvre les heros et supports et les essais gratuits ne consomment pas le hasard`() {
        val g = game(); g.startNew(true); val a = g.adventure
        assertTrue(a.heroes.all { it.owned }); assertTrue(a.pets.all { it > 0 }); assertTrue(a.relics.all { it > 0 })
        assertEquals(6, a.slotCount()); a.bag.fill(0); assertTrue(a.levelUp(11)); assertTrue(a.forge(11, 0))
        assertTrue(a.assign(11, 5)); assertArrayEquals(LongArray(8), a.bag)
        val legacy = JSONObject(HamletSave.toJson(g)).apply { remove("adventure"); put("version", 3) }
        val migrated = game(); assertTrue(HamletSave.fromJson(migrated, legacy.toString()))
        assertTrue(migrated.adventure.heroes.all { it.owned }); assertTrue(migrated.adventure.pets.all { it > 0 })
        val first = game(); val second = game()
        assertNull(first.adventure.beginBattle(BattleMode.CAMPAIGN, 999))
        val b1 = first.adventure.beginBattle(BattleMode.CAMPAIGN, 0)!!; val b2 = second.adventure.beginBattle(BattleMode.CAMPAIGN, 0)!!
        finish(b1); finish(b2); assertEquals(b1.damage, b2.damage, 0.001)
        g.startNew(false); assertEquals(4, g.adventure.heroes.count { it.owned }); assertTrue(g.adventure.pets.all { it == 0 })
    }
    @Test fun `bois pierre ameliorent la milice et accelerent le chantier sans depenser a reserve pleine`() {
        val g = game(); val a = g.adventure; assertFalse(a.improveTroops(0))
        facility(g, "adventureYard"); val power = a.numbers(0).attack
        assertTrue(a.improveTroops(0)); assertTrue(a.numbers(0).attack > power)
        val before = a.bag.clone(); assertTrue(a.supplyBuilders()); assertEquals(60, g.workSeconds)
        assertEquals(before[1] - 10, a[AdventureResource.TIMBER]); assertEquals(before[2] - 10, a[AdventureResource.STONE])
        g.workSeconds = 7200; val full = a.bag.clone(); assertFalse(a.supplyBuilders()); assertArrayEquals(full, a.bag)
    }
}
