package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletRecruitmentTest {
    private var time = 1_800_000_000_000L
    private fun game(seed: Int = 31) = HamletGame({ time }, Random(seed)).apply { startNew(false) }
    /** Une équipe de départ pour les tests qui n'ont pas besoin du scénario d'ouverture. */
    private fun starters(a: HamletAdventure) { for (id in intArrayOf(0, 1, 3, 7)) a.welcome(a.heroes[id]) }

    @Test fun `catalogue stable avec tous les roles dans chaque faction`() {
        val defs = HamletHeroes.defs
        assertEquals(36, defs.size)
        assertEquals(defs.indices.toList(), defs.map { it.id })
        HeroFaction.entries.forEach { faction ->
            assertEquals(12, defs.count { it.faction == faction })
            assertEquals(HeroRole.entries.toSet(), defs.filter { it.faction == faction }.map { it.role }.toSet())
        }
        assertEquals(defs.size, HamletHeroLooks.size)
        assertEquals(listOf(12, 12, 8, 3, 1), HeroRarity.entries.map { rarity -> defs.count { it.rarity == rarity } })
        assertTrue(HamletHeroes.OPENING.all { defs[it].rarity == HeroRarity.COMMON })
        assertEquals(defs.filter { it.rarity == HeroRarity.LEGENDARY }.map { it.id }.toSet(), HamletHeroes.LEGENDS.toSet())
        assertEquals(HeroRarity.MYTHIC, defs[HamletHeroes.MYTHIC].rarity)
    }

    @Test fun `aucun heros au depart et les tirages depensent les invitations une fois`() {
        val a = game().adventure; val b = game().adventure
        assertEquals(0, a.ownedCount()); assertTrue(a.heroes.none { it.owned })
        assertTrue(a.summon(10).isEmpty()); assertTrue(a.summon(0).isEmpty()); assertTrue(a.summon(11).isEmpty())
        a.add(AdventureResource.TICKETS, 5); b.add(AdventureResource.TICKETS, 5)
        assertTrue(a.summon(10).isEmpty()); assertEquals(5L, a[AdventureResource.TICKETS])
        assertEquals(b.summon(1), a.summon(1))
        assertEquals(4L, a[AdventureResource.TICKETS]); assertEquals(1, a.recruitmentPulls)
    }

    @Test fun `les trois premiers appels sont ecrits d'avance puis le hasard commence`() {
        val a = game().adventure; a.add(AdventureResource.TICKETS, 40)
        val first = a.summon(3)
        assertEquals(HamletHeroes.OPENING.toList(), first.map { it.hero })
        assertTrue(first.all { it.scripted && it.newHero })
        assertEquals(0, a.scriptedLeft())
        val rest = (0 until 37).flatMap { a.summon(1) }
        assertTrue(rest.none { it.scripted })
        assertTrue("jamais de légende dans un tirage", rest.none { a.heroes[it.hero].def.rarity.ordinal >= HeroRarity.LEGENDARY.ordinal })
    }

    @Test fun `un rare est garanti au huitieme appel libre et un epique tous les quatre-vingts`() {
        // Beaucoup de graines : le huitième appel libre donne toujours au moins un rare.
        for (seed in 0 until 60) {
            val a = game(seed).adventure; a.add(AdventureResource.TICKETS, 11)
            a.summon(3)
            val free = (0 until 8).map { a.summon(1).single() }
            assertTrue("graine $seed", free.any { a.heroes[it.hero].def.rarity.ordinal >= HeroRarity.RARE.ordinal })
        }
        val a = game(5).adventure; a.add(AdventureResource.TICKETS, 3 + 400)
        a.summon(3)
        var sinceEpic = 0
        repeat(400) {
            val r = a.summon(1).single(); sinceEpic++
            if (a.heroes[r.hero].def.rarity == HeroRarity.EPIC) sinceEpic = 0
            assertTrue(sinceEpic < HamletAdventure.PITY_EPIC)
        }
    }

    @Test fun `un doublon donne des fragments de son propre heros selon sa rarete`() {
        val a = game().adventure; a.add(AdventureResource.TICKETS, 200)
        a.heroes.filter { it.def.rarity.ordinal <= HeroRarity.EPIC.ordinal }.forEach { it.owned = true }
        val before = a.heroes.sumOf { it.shards }
        val pulls = a.summon(10)
        assertTrue(pulls.all { !it.newHero })
        for (p in pulls) assertEquals(HamletHeroes.duplicateShards(a.heroes[p.hero].def.rarity), p.shards)
        assertEquals(before + pulls.sumOf { it.shards }, a.heroes.sumOf { it.shards })
        assertEquals(1L, HamletHeroes.duplicateShards(HeroRarity.COMMON)); assertEquals(2L, HamletHeroes.duplicateShards(HeroRarity.RARE))
        assertEquals(4L, HamletHeroes.duplicateShards(HeroRarity.EPIC))
    }

    @Test fun `garanties et compteur survivent a la sauvegarde`() {
        val g = game(); val a = g.adventure
        a.add(AdventureResource.TICKETS, 30); a.summon(10); a.summon(2)
        a.pityEpic = HamletAdventure.PITY_EPIC - 1; a.recruitmentWork = 7; a.innTicketProgress = 120; a.shardDrip = 5
        val restored = game()
        assertTrue(HamletSave.fromJson(restored, HamletSave.toJson(g)))
        val r = restored.adventure
        assertEquals(12, r.recruitmentPulls); assertEquals(7L, r.recruitmentWork); assertEquals(120L, r.innTicketProgress); assertEquals(5, r.shardDrip)
        assertEquals(a.pityRare, r.pityRare)
        val pull = r.summon().single()
        assertEquals(HeroRarity.EPIC, r.heroes[pull.hero].def.rarity); assertTrue(pull.guaranteed)
        assertEquals(0, r.pityEpic)
    }

    @Test fun `le travail au village nourrit les appels mais seulement quelques uns par jour`() {
        val a = game().adventure; val tickets = a[AdventureResource.TICKETS]
        a.villageAction("harvested", 100); a.villageAction("harvested", 99); assertEquals(tickets, a[AdventureResource.TICKETS])
        assertEquals(199L, a.recruitmentWork)
        a.villageAction("products", 0); assertEquals(199L, a.recruitmentWork)
        a.villageAction("delivered", 1); a.villageAction("delivered", 100)
        assertEquals(tickets + 1, a[AdventureResource.TICKETS])
        repeat(30) { a.villageAction("delivered", 100) }
        assertEquals(tickets + HamletAdventure.WORK_TICKETS_PER_DAY, a[AdventureResource.TICKETS])
        time += Hamlet.DAY_MS; a.villageAction("delivered", 100)
        assertTrue(a[AdventureResource.TICKETS] > tickets + HamletAdventure.WORK_TICKETS_PER_DAY)
    }

    @Test fun `auberge produit pendant absence a partir de la fin du chantier`() {
        val g = game(); val a = g.adventure; val start = time
        g.coins = 1_000_000; g.xp = 1_000_000
        val origin = g.realmOrigin()
        val spot = (origin until origin + g.realmSize()).flatMap { y -> (origin until origin + g.realmSize()).map { x -> x to y } }
            .first { (x, y) -> g.canPlace("adventureInn", x, y, false) }
        val inn = g.buy("adventureInn", spot.first, spot.second, false)!!
        val tickets = a[AdventureResource.TICKETS]
        time = start + inn.def.build * 1000L + 24 * 60 * 60_000L
        a.advanceProduction()
        assertEquals(tickets + 1, a[AdventureResource.TICKETS]); assertEquals(0L, a.innTicketProgress)
        val saved = a.bag.clone(); a.advanceProduction(); assertArrayEquals(saved, a.bag)
    }

    @Test fun `la prochaine invitation de l'auberge se compte a rebours`() {
        val g = game(); val a = g.adventure
        assertNull(a.innNextIn())
        g.coins = 1_000_000; g.xp = 1_000_000
        val origin = g.realmOrigin()
        val spot = (origin until origin + g.realmSize()).flatMap { y -> (origin until origin + g.realmSize()).map { x -> x to y } }
            .first { (x, y) -> g.canPlace("adventureInn", x, y, false) }
        val inn = g.buy("adventureInn", spot.first, spot.second, false)!!
        time += inn.def.build * 1000L + 1; g.tick()
        val first = a.innNextIn()!!
        assertTrue("environ 24 h au rang 1 : $first", first in 23 * 3_600_000L..24 * 3_600_000L)
        time += 6 * 3_600_000L; a.advanceProduction()
        assertTrue(a.innNextIn()!! < first - 5 * 3_600_000L)
    }

    @Test fun `formation automatique place defenseurs et soin sans doublons`() {
        val a = game().adventure; starters(a)
        assertTrue(a.autoFormation())
        val selected = a.teams[a.selectedTeam].take(a.slotCount()).filter { it >= 0 }
        assertEquals(3, selected.size); assertEquals(3, selected.toSet().size)
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
            starters(g.adventure)
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

    @Test fun `le camp se remplit pendant douze heures seulement et lentrepot rallonge ce temps`() {
        val a = game().adventure
        assertEquals(12 * 60L, a.campCapMinutes())
        val start = a[AdventureResource.FOOD]
        time += 10 * 3_600_000L; a.advanceProduction()
        val tenHours = a[AdventureResource.FOOD] - start
        val b = game().adventure
        time += 3 * 24 * 3_600_000L; b.advanceProduction()
        val threeDays = b[AdventureResource.FOOD] - 180
        assertTrue(threeDays <= tenHours * 12 / 10 + 5)
        assertTrue(threeDays > tenHours)
        assertEquals(720L, b.campFullIn())
        time += 13 * 3_600_000L; assertEquals(0L, b.campFullIn())
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

    @Test fun `le niveau est plafonne par les etoiles et les etoiles par la halle`() {
        val g = game(); val a = g.adventure; starters(a)
        a.bag[AdventureResource.FOOD.ordinal] = 1_000_000
        assertEquals(10, a.levelCap(0))
        repeat(100) { a.levelUp(0) }
        assertEquals(10, a.heroes[0].level); assertFalse(a.levelUp(0))
        a.heroes[0].shards = 10_000
        assertEquals(HamletHeroes.starLimit(1), a.starLimit())
        repeat(5) { a.promote(0) }
        assertEquals(2, a.heroes[0].stars); assertFalse(a.canPromote(0))
        assertEquals(26, a.levelCap(0))
        repeat(100) { a.levelUp(0) }
        assertEquals(26, a.heroes[0].level)
        g.hall = 3
        assertTrue(a.promote(0)); assertEquals(3, a.heroes[0].stars)
        g.hall = 8; repeat(10) { a.promote(0) }; assertEquals(HamletHeroes.STAR_CAP, a.heroes[0].stars)
    }

    @Test fun `competences traits et recherches ont un plafond qui monte avec les etoiles et les batiments`() {
        val g = game(); val a = g.adventure; starters(a)
        a.bag[AdventureResource.BOOKS.ordinal] = 10_000_000
        assertFalse(a.studySkill(0, 0))
        a.frontier = HamletAdventure.FOURTH_SLOT
        repeat(10) { a.studySkill(0, 0) }
        assertEquals(a.skillCap(0), a.heroes[0].skills[0])
        a.heroes[0].stars = 5; a.heroes[0].shards = 1_000
        assertEquals(1, a.traitCap(0)); assertTrue(a.promoteTrait(0, 0)); assertFalse(a.promoteTrait(0, 0))
        assertFalse(a.research(0))
        g.coins = 1_000_000; g.xp = 1_000_000
        val origin = g.realmOrigin()
        outer@ for (y in origin until origin + g.realmSize()) for (x in origin until origin + g.realmSize())
            if (g.canPlace("adventureAcademy", x, y, false)) { val o = g.buy("adventureAcademy", x, y, false)!!; time += o.def.build * 1000L + 1; break@outer }
        repeat(30) { a.research(0) }
        assertEquals(a.techCap(), a.tech[0])
    }
}
