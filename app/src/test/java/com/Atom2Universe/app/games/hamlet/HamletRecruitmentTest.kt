package com.Atom2Universe.app.games.hamlet

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletRecruitmentTest {
    private var time = 1_800_000_000_000L
    private fun game() = HamletGame({ time }, Random(31)).apply { startNew(false) }

    @Test fun `catalogue stable avec tous les roles dans chaque faction`() {
        val defs = HamletHeroes.defs
        assertEquals(36, defs.size)
        assertEquals(defs.indices.toList(), defs.map { it.id })
        HeroFaction.entries.forEach { faction ->
            assertEquals(12, defs.count { it.faction == faction })
            assertEquals(HeroRole.entries.toSet(), defs.filter { it.faction == faction }.map { it.role }.toSet())
        }
        assertEquals(4, game().adventure.heroes.count { it.owned })
        assertTrue(defs.all { it.portraitBase in 0..11 })
        assertEquals(listOf(12, 12, 8, 3, 1), HeroRarity.entries.map { rarity -> defs.count { it.rarity == rarity } })
        assertTrue(game().adventure.heroes.filter { it.owned }.all { it.def.rarity == HeroRarity.COMMON })
    }

    @Test fun `tirages depensent les invitations une fois et echec garde le hasard`() {
        val a = game().adventure; val b = game().adventure
        val tickets = a[AdventureResource.TICKETS]
        assertTrue(a.summon(10).isEmpty()); assertTrue(a.summon(0).isEmpty())
        assertEquals(tickets, a[AdventureResource.TICKETS])
        assertEquals(b.summon(1), a.summon(1))
        assertEquals(tickets - 1, a[AdventureResource.TICKETS])
        assertEquals(1, a.recruitmentPulls)
    }

    @Test fun `garanties survivent a la sauvegarde et remise a zero selon rarete`() {
        val g = game(); val a = g.adventure
        a.pityEpic = HamletAdventure.PITY_EPIC - 1; a.pityLegendary = HamletAdventure.PITY_LEGENDARY - 1; a.pityMythic = HamletAdventure.PITY_MYTHIC - 1
        a.recruitmentPulls = 150; a.recruitmentWork = 7; a.innTicketProgress = 120
        val restored = game()
        assertTrue(HamletSave.fromJson(restored, HamletSave.toJson(g)))
        val r = restored.adventure
        assertEquals(150, r.recruitmentPulls); assertEquals(7L, r.recruitmentWork); assertEquals(120L, r.innTicketProgress)
        val pull = r.summon().single()
        assertEquals(HeroRarity.MYTHIC, r.heroes[pull.hero].def.rarity)
        assertTrue(pull.guaranteed)
        assertEquals(0, r.pityEpic); assertEquals(0, r.pityLegendary); assertEquals(0, r.pityMythic)
        assertEquals(151, r.recruitmentPulls)
    }

    @Test fun `tirages en lot respectent tous les compteurs et doublons`() {
        val a = game().adventure; a.add(AdventureResource.TICKETS, 200)
        a.heroes.forEach { it.owned = true }
        repeat(20) {
            val pulls = a.summon(10)
            assertEquals(10, pulls.size)
            assertTrue(pulls.all { !it.newHero && it.shards == 1L })
            assertTrue(a.pityEpic in 0 until HamletAdventure.PITY_EPIC); assertTrue(a.pityLegendary in 0 until HamletAdventure.PITY_LEGENDARY); assertTrue(a.pityMythic in 0 until HamletAdventure.PITY_MYTHIC)
        }
        assertEquals(200L, a.heroes.sumOf { it.shards })
        assertEquals(200, a.recruitmentPulls)
        assertEquals(3L, a[AdventureResource.TICKETS])
    }

    @Test fun `fragments ouvrent un heros sans contourner leur cout`() {
        val a = game().adventure; val h = a.heroes[10]
        val cost = a.shardUnlockCost(h.id)
        h.shards = cost - 1
        assertFalse(a.recruitShards(h.id)); assertFalse(h.owned)
        h.shards++
        assertTrue(a.recruitShards(h.id)); assertTrue(h.owned); assertEquals(0L, h.shards)
        assertFalse(a.recruitShards(h.id)); assertFalse(a.recruitShards(-1))
    }

    @Test fun `anciens heros sauves et nouveaux voyageurs ne changent pas les routes`() {
        val g = game(); val a = g.adventure
        assertEquals(AdventureNode.RESCUE, a.node(5))
        assertEquals(AdventureNode.BATTLE, a.node(3)) // Sylva opens targeted recruitment at 4, without changing this route.
        assertTrue(a.summonPool().any { it.id == 35 })
        assertFalse(a.focusRecruit(35))
        a.frontier = 110
        assertFalse(a.focusRecruit(35))
        a.frontier = a.recruitFrontier(35); a.add(AdventureResource.TICKETS, 300)
        assertFalse(a.focusRecruit(35))
        g.journey.chapter = a.recruitChapter(35)
        assertTrue(a.focusRecruit(35)); assertTrue(a.heroes[35].owned)
    }

    @Test fun `migration ajoute les voyageurs sans perdre equipe ou progression`() {
        val g = game(); val a = g.adventure
        a.frontier = 80; a.heroes[9].owned = true; a.heroes[9].level = 45
        val legacy = HamletAdventureSave.encode(a)
        val oldHeroes = legacy.getJSONArray("heroes")
        legacy.put("heroes", JSONArray((0 until 12).map { oldHeroes.getJSONObject(it) }))
        listOf("recruitmentPulls", "pityEpic", "pityLegendary", "pityMythic", "recruitmentWork", "innTicketProgress").forEach { legacy.remove(it) }
        val restored = game().adventure
        HamletAdventureSave.decode(restored, legacy)
        assertEquals(45, restored.heroes[9].level); assertTrue(restored.heroes[9].owned)
        assertTrue(restored.heroes.drop(12).none { it.owned })
        assertArrayEquals(a.teams[0], restored.teams[0]); assertEquals(0, restored.recruitmentPulls)
    }

    @Test fun `expedition rapide reservee aux routes maitrisees et consomme vraiment les vivres`() {
        val a = game().adventure
        a.frontier = 1; a.stars[0] = 2
        assertFalse(a.sweep(0)); assertFalse(a.sweep(-1)); assertFalse(a.sweep(1))
        a.stars[0] = 3
        val food = a[AdventureResource.FOOD]; val ore = a[AdventureResource.ORE]
        assertTrue(a.sweep(0)); assertTrue(a[AdventureResource.FOOD] < food)
        assertTrue(a[AdventureResource.ORE] > ore); assertEquals(1, a.frontier)
        a.frontier = 105; a.stars[104] = 3; a.add(AdventureResource.FOOD, 200)
        val laterFood = a[AdventureResource.FOOD]
        assertTrue(a.sweep(104)); assertTrue(a[AdventureResource.FOOD] < laterFood)
        a.bag[AdventureResource.FOOD.ordinal] = 0
        val before = a.bag.clone(); assertFalse(a.sweep(0)); assertArrayEquals(before, a.bag)
    }

    @Test fun `le travail au village nourrit le recrutement sans valider des actions nulles`() {
        val a = game().adventure; val tickets = a[AdventureResource.TICKETS]
        a.villageAction("harvested", 100); a.villageAction("harvested", 19); assertEquals(tickets, a[AdventureResource.TICKETS])
        assertEquals(119L, a.recruitmentWork)
        a.villageAction("products", 0); assertEquals(119L, a.recruitmentWork)
        a.villageAction("delivered", 1); assertEquals(tickets + 1, a[AdventureResource.TICKETS])
        assertEquals(2L, a.recruitmentWork)
    }

    @Test fun `auberge produit pendant absence a partir de la fin du chantier`() {
        val g = game(); val a = g.adventure; val start = time
        g.coins = 1_000_000; g.xp = 1_000_000
        val origin = g.realmOrigin()
        val spot = (origin until origin + g.realmSize()).flatMap { y -> (origin until origin + g.realmSize()).map { x -> x to y } }
            .first { (x, y) -> g.canPlace("adventureInn", x, y, false) }
        val inn = g.buy("adventureInn", spot.first, spot.second, false)!!
        val tickets = a[AdventureResource.TICKETS]
        time = start + inn.def.build * 1000L + 720 * 60_000
        a.advanceProduction()
        assertEquals(tickets + 1, a[AdventureResource.TICKETS]); assertEquals(0L, a.innTicketProgress)
        val saved = a.bag.clone(); a.advanceProduction(); assertArrayEquals(saved, a.bag)
    }

    @Test fun `formation automatique place defenseurs et soin sans doublons`() {
        val a = game().adventure
        assertTrue(a.autoFormation())
        val selected = a.teams[a.selectedTeam].take(a.slotCount())
        assertEquals(4, selected.toSet().size)
        assertEquals(HeroRole.GUARDIAN, a.heroes[selected[0]].def.role)
        assertEquals(HeroRole.DUELIST, a.heroes[selected[1]].def.role)
        assertEquals(HeroRole.HEALER, a.heroes[selected[2]].def.role)
        assertTrue(selected.all { a.heroes[it].owned })
    }

    @Test fun `purification retire les effets et rempart protege les deux premieres places`() {
        val cleanser = BattleUnit(0, 14, false, HeroFaction.FORGE, HeroRole.HEALER, 2,
            1000.0, 80.0, 10.0, 2f, 7f, 20f, ability = HeroAbility.PURIFY)
        val ally = BattleUnit(1, 1, false, HeroFaction.FORGE, HeroRole.GUARDIAN, 0, 2000.0, 50.0, 40.0, 2f, 8f, 20f)
        val ward = BattleUnit(2, 16, false, HeroFaction.DAWN, HeroRole.GUARDIAN, 1,
            2000.0, 50.0, 40.0, 2f, 8f, 20f, ability = HeroAbility.WARD)
        val foe = HamletBattle.enemy(4, 100, 0, 10.0, false)
        val b = HamletBattle(BattleMode.CAMPAIGN, 0, 0, arrayListOf(cleanser, ally, ward, foe), Random(3), autoUltimates = false)
        ally.hp = 500.0; ally.poison = 10f; ally.poisonAttack = 20.0; ally.weakened = 8f; ally.stun = 5f
        cleanser.ultimateLeft = 0f
        assertTrue(b.ultimate(cleanser.index))
        assertEquals(0f, ally.poison, 0f); assertEquals(0f, ally.stun, 0f); assertEquals(0f, ally.weakened, 0f)
        ward.ultimateLeft = 0f; assertTrue(b.ultimate(ward.index))
        assertTrue(ally.shield > 0); assertTrue(ward.shield > 0)
    }

    @Test fun `rendement identique chaque minute et hors ligne avec sauvegarde des fractions`() {
        var active = game(); val offline = game()
        for (g in listOf(active, offline)) {
            assertTrue(g.stewardship.assign(0, 0))
            assertTrue(g.stewardship.assign(1, 1))
            assertTrue(g.stewardship.assign(2, 3))
            g.adventure.tech[2] = 3
        }
        repeat(60) { minute ->
            time += 60_000; active.adventure.advanceProduction()
            if (minute == 0) {
                assertTrue(active.adventure.productionRemainder[0] > 0)
                val snapshot = HamletSave.toJson(active)
                val restored = game()
                assertTrue(HamletSave.fromJson(restored, snapshot))
                assertArrayEquals(active.adventure.productionRemainder, restored.adventure.productionRemainder, 0.00000001)
                active = restored
            }
        }
        offline.adventure.advanceProduction()
        assertArrayEquals(offline.adventure.bag, active.adventure.bag)
        assertArrayEquals(offline.adventure.productionRemainder, active.adventure.productionRemainder, 0.00000001)
    }

    @Test fun `retour de lhorloge ne compte pas deux fois les minutes deja produites`() {
        val a = game().adventure
        time += 10 * 60_000; a.advanceProduction()
        val settledAt = a.productionAt; val bag = a.bag.clone()
        time -= 5 * 60_000; a.advanceProduction()
        assertEquals(settledAt, a.productionAt); assertArrayEquals(bag, a.bag)
        time += 5 * 60_000; a.advanceProduction(); assertArrayEquals(bag, a.bag)
        time += 60_000; a.advanceProduction()
        assertEquals(bag[0] + 4, a[AdventureResource.FOOD])
    }

    @Test fun `une copie vaut un fragment du personnage et dix fragments ameliorent vraiment le starter`() {
        val a = game().adventure; val h = a.heroes[0]
        h.level = 400; h.gear.fill(15)
        val before = HamletHeroes.numbers(h)
        assertTrue(a.focusRecruit(0)); assertEquals(1L, h.shards)
        assertEquals(0L, a.heroes[1].shards); assertFalse(a.promote(1))
        assertFalse(a.promote(0)); a.add(AdventureResource.TICKETS, 27)
        repeat(9) { assertTrue(a.focusRecruit(0)) }
        assertEquals(10L, h.shards)
        assertEquals(10L, a.starCost(0)); assertTrue(a.promote(0))
        assertEquals(0L, h.shards); assertEquals(1, h.stars)
        val promoted = HamletHeroes.numbers(h)
        assertEquals(1.05, promoted.attack / before.attack, 0.000001)
        assertEquals(1.05, promoted.hp / before.hp, 0.000001)
        assertEquals(25L, a.starCost(0))
        a.add(AdventureResource.TICKETS, 75)
        repeat(24) { assertTrue(a.focusRecruit(0)) }
        assertFalse(a.promote(0)); assertTrue(a.focusRecruit(0)); assertTrue(a.promote(0))
        assertEquals(0L, h.shards); assertEquals(50L, a.starCost(0))
        assertTrue(a.starGainPercent(0) > 4.0)
        h.stars = HamletHeroes.STAR_CAP; val remaining = h.shards
        assertFalse(a.promote(0)); assertEquals(remaining, h.shards)
    }

    @Test fun `ciblage elite ne contourne ni prix ni routes ni chapitres`() {
        val g = game(); val a = g.adventure
        a.add(AdventureResource.TICKETS, 1000)
        for ((id, cost) in listOf(5 to 12L, 8 to 240L, 10 to 600L)) {
            val wallet = a[AdventureResource.TICKETS]
            a.frontier = 110; g.journey.chapter = 0
            assertFalse(a.focusRecruit(id)); assertEquals(wallet, a[AdventureResource.TICKETS])
            a.frontier = a.recruitFrontier(id); g.journey.chapter = a.recruitChapter(id)
            assertEquals(cost, a.recruitCost(id)); assertTrue(a.focusRecruit(id))
            assertEquals(wallet - cost, a[AdventureResource.TICKETS])
        }
        assertEquals(600L, a.shardUnlockCost(10)); assertEquals(240L, a.shardUnlockCost(8)); assertEquals(60L, a.shardUnlockCost(5))
    }

    @Test fun `secours de campagne elite rapporte un fragment sans debloquer le heros`() {
        val a = game().adventure; a.frontier = 43
        a.heroes.filter { it.owned }.forEach { it.level = 200 }
        val fight = a.beginBattle(BattleMode.CAMPAIGN, 43, 9)!!; fight.advance(100f)
        assertEquals(BattleOutcome.VICTORY, fight.outcome); assertTrue(a.settle(fight))
        assertFalse(a.heroes[8].owned); assertEquals(1L, a.heroes[8].shards)
        val retry = a.beginBattle(BattleMode.CAMPAIGN, 43, 9)!!; retry.advance(100f); assertTrue(a.settle(retry))
        assertFalse(a.heroes[8].owned); assertEquals(1L, a.heroes[8].shards)
    }

    @Test fun `butin vise le heros possede choisi et replays ne generent pas invitations ou fragments elites`() {
        val g = game(); val a = g.adventure
        a.frontier = 10; a.stars[9] = 3
        assertTrue(a.selectShardFocus(0)); assertFalse(a.selectShardFocus(10))
        val tickets = a[AdventureResource.TICKETS]
        assertTrue(a.sweep(9)); assertTrue(a.sweep(9))
        assertEquals(tickets, a[AdventureResource.TICKETS]); assertEquals(6L, a.heroes[0].shards)
        assertTrue(a.heroes.filter { !it.owned }.all { it.shards == 0L })
        val restored = game(); assertTrue(HamletSave.fromJson(restored, HamletSave.toJson(g)))
        assertEquals(0, restored.adventure.shardFocusHero)
    }

    @Test fun `aucun empilement auberges ne multiplie la vitesse invitations`() {
        val g = game(); val a = g.adventure
        g.objs[9990] = Obj(9990, "adventureInn", 0, 0).apply { tier = 3 }
        g.objs[9991] = Obj(9991, "adventureInn", 0, 0).apply { tier = 3 }
        val tickets = a[AdventureResource.TICKETS]
        time += 8 * 60 * 60_000; a.advanceProduction()
        assertEquals(tickets + 1, a[AdventureResource.TICKETS])
    }
}
