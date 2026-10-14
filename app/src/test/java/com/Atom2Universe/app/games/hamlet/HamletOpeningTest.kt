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

    @Test fun `un heros seul passe les premieres etapes puis chaque ticket de campagne ajoute un heros`() {
        val g = game(); val a = g.adventure; a.add(AdventureResource.TICKETS, 1); a.summon()
        // étape 0, puis la cueillette de l'étape 1, puis les étapes 2 et 3 : trois vrais combats pour lui seul
        assertEquals(BattleOutcome.VICTORY, fight(a, 0).outcome)
        assertTrue(a.gather(1))
        assertEquals(BattleOutcome.VICTORY, fight(a, 2).outcome)
        val before = a[AdventureResource.TICKETS]
        assertEquals(BattleOutcome.VICTORY, fight(a, 3).outcome)
        assertEquals(before + 1, a[AdventureResource.TICKETS]); assertEquals(4, a.frontier)
        assertEquals(HamletHeroes.OPENING[1], a.summon().single().hero)
        assertEquals(2, a.fielded())
    }

    @Test fun `la quatrieme place s'ouvre apres le premier boss et la cinquieme bien plus tard`() {
        val a = game().adventure
        a.frontier = 9; assertEquals(3, a.slotCount()); a.frontier = 10; assertEquals(4, a.slotCount())
        a.frontier = 39; assertEquals(4, a.slotCount()); a.frontier = 40; assertEquals(5, a.slotCount())
        assertEquals(1L, a.ticketReward(9)); assertEquals(2L, a.ticketReward(39)); assertEquals(0L, a.ticketReward(10))
    }
}
