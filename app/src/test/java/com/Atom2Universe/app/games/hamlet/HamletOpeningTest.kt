package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Le début de partie : zéro héros, trois appels écrits d'avance, des combats que l'on peut gagner à chaque étape. */
class HamletOpeningTest {
    private var time = 1_800_000_000_000L
    private fun game(seed: Int = 3) = HamletGame({ time }, Random(seed)).apply { startNew(false) }
    private fun fight(a: HamletAdventure, stage: Int, seed: Int = 1): HamletBattle {
        val b = a.beginBattle(BattleMode.CAMPAIGN, stage, seed)!!
        b.advance(100f)
        a.settle(b)
        return b
    }

    @Test fun `on commence sans aucun heros ni appel`() {
        val g = game(); val a = g.adventure
        assertEquals(0, a.ownedCount()); assertEquals(0L, a[AdventureResource.TICKETS])
        assertFalse(a.opened()); assertEquals(3, a.slotCount())
        assertNull(a.beginBattle(BattleMode.CAMPAIGN, 0)); assertTrue(a.summon().isEmpty())
    }

    @Test fun `la deuxieme quete offre le premier appel et il est ecrit d'avance`() {
        val g = game()
        assertEquals(1, Hamlet.quests[1].tickets)
        g.stats["planted"] = 3; g.stats["harvested"] = 3
        assertTrue(g.claimQuest()); assertTrue(g.claimQuest())
        val a = g.adventure
        assertEquals(1L, a[AdventureResource.TICKETS]); assertTrue(a.opened())
        val pull = a.summon().single()
        assertEquals(HamletHeroes.OPENING[0], pull.hero); assertTrue(pull.newHero); assertTrue(pull.scripted)
        assertEquals(HeroRarity.COMMON, a.heroes[pull.hero].def.rarity)
        assertEquals(pull.hero, a.teams[0][0])
    }

    @Test fun `les trois premiers heros arrivent dans l'ordre par des appels scriptes`() {
        val a = game().adventure; a.add(AdventureResource.TICKETS, 3)
        val got = a.summon(3).map { it.hero }
        assertEquals(HamletHeroes.OPENING.toList(), got)
        assertEquals(got, a.teams[0].take(3)); assertEquals(0, a.scriptedLeft())
        assertTrue(got.all { a.heroes[it].def.rarity == HeroRarity.COMMON })
    }

    /** Une chaumière dans le village : la route s'ouvre jusqu'à l'étape 3. */
    private fun withHouse(g: HamletGame): HamletGame {
        val hall = g.objs.values.first { it.t == "hall" }
        assertNotNull(g.place("cottage", hall.x + 5, hall.y + 9))
        return g
    }

    @Test fun `un heros seul passe les premieres etapes puis chaque ticket de campagne ajoute un heros`() {
        val g = withHouse(game()); val a = g.adventure; a.add(AdventureResource.TICKETS, 1); a.summon()
        // étape 0, puis la cueillette de l'étape 1, puis les étapes 2 et 3 : trois vrais combats pour lui seul
        assertEquals(BattleOutcome.VICTORY, fight(a, 0).outcome)
        assertTrue(a.gather(1))
        assertEquals(BattleOutcome.VICTORY, fight(a, 2).outcome)
        val before = a[AdventureResource.TICKETS]
        assertEquals(BattleOutcome.VICTORY, fight(a, 3).outcome)
        assertEquals(before + 1, a[AdventureResource.TICKETS]); assertEquals(3, a.frontier)
        assertEquals(HamletHeroes.OPENING[1], a.summon().single().hero)
        assertEquals(2, a.fielded())
    }

    @Test fun `sans maison une seule etape se farme, et sa premiere victoire ne paie qu'une fois`() {
        val g = game(); val a = g.adventure; a.add(AdventureResource.TICKETS, 1); a.summon()
        assertEquals(0, a.frontierCap())
        assertEquals(0L, a[AdventureResource.TIMBER])
        fight(a, 0)
        val wood = a[AdventureResource.TIMBER]; val coins = g.coins
        assertTrue("la première victoire donne de quoi bâtir la première maison (8 bois, 6 pierres)", wood >= 8 && a[AdventureResource.STONE] >= 6)
        assertTrue(coins >= 100)
        assertEquals("la route ne bouge pas", 0, a.frontier); assertTrue(a.roadClosed()); assertFalse(a.isFirst(0))
        assertNull("l'étape 1 n'est pas encore ouverte", a.beginBattle(BattleMode.CAMPAIGN, 1)); assertFalse(a.gather(1))
        // rejouer l'étape 0 ne rapporte que la routine, jamais une nouvelle première victoire
        repeat(5) { fight(a, 0, it + 2); assertFalse("seulement de la routine", a.lastFirst) }
        assertTrue(g.coins > coins); assertTrue(a[AdventureResource.TIMBER] > wood)
        assertEquals(0, a.frontier)
    }

    @Test fun `la route s'ouvre avec la maison, le premier chapitre puis le deuxieme`() {
        val g = game(); val a = g.adventure
        assertEquals(0, a.frontierCap())
        withHouse(g); assertEquals(3, a.frontierCap())
        g.journey.chapter = 1; assertEquals(HamletAdventure.FOURTH_SLOT, a.frontierCap())
        g.journey.chapter = 2; assertEquals(Int.MAX_VALUE, a.frontierCap())
        val creative = game().apply { sandbox = true }
        assertEquals(Int.MAX_VALUE, creative.adventure.frontierCap())
    }

    @Test fun `quand la route s'ouvre, la derniere etape gagnee fait avancer d'un pas sans nouvelle premiere victoire`() {
        val g = withHouse(game()); val a = g.adventure; a.add(AdventureResource.TICKETS, 1); a.summon()
        fight(a, 0); assertTrue(a.gather(1)); fight(a, 2); fight(a, 3)
        assertEquals(3, a.frontier); assertTrue(a.roadClosed())
        fight(a, 3, 9)
        assertFalse("pas de nouvelle première victoire", a.lastFirst); assertEquals(3, a.frontier)
        g.journey.chapter = 1
        assertFalse(a.roadClosed()); fight(a, 3, 10)
        assertEquals("un pas de plus une fois la route ouverte", 4, a.frontier)
    }

    @Test fun `le butin des premieres etapes est plus petit que celui des suivantes`() {
        val a = game().adventure
        assertTrue(a.lootRamp(0) < a.lootRamp(10)); assertTrue(a.lootRamp(10) < 1.0); assertEquals(1.0, a.lootRamp(HamletAdventure.FULL_LOOT_STAGE), 0.0)
        assertTrue(a.xpRamp(0) < a.lootRamp(0))
    }

    @Test fun `la quatrieme place s'ouvre apres le premier boss et la cinquieme bien plus tard`() {
        val a = game().adventure
        a.frontier = 9; assertEquals(3, a.slotCount()); a.frontier = 10; assertEquals(4, a.slotCount())
        a.frontier = 39; assertEquals(4, a.slotCount()); a.frontier = 40; assertEquals(5, a.slotCount())
        assertEquals(1L, a.ticketReward(9)); assertEquals(2L, a.ticketReward(39)); assertEquals(0L, a.ticketReward(10))
    }
}
