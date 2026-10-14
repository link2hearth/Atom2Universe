package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletSystemsTest {
    private var time = 1_800_000_000_000L
    private fun game() = HamletGame({ time }, Random(4)).apply { startNew(false) }.withStarters()
    private fun building(g: HamletGame, id: String, tier: Int = 1) {
        // Scène préparée : les tests d'achat et placement existent dans HamletGameTest.
        g.objs[g.nextId] = Obj(g.nextId++, id, 0, 0).apply { this.tier = tier }
    }
    @Test fun `academie permet des recrues utiles sans entrainement a prix derisoire`() {
        val g = game(); val a = g.adventure
        a.heroes.filter { it.owned }.forEach { it.stars = 5; it.level = 20 }
        a.heroes[2].owned = true
        assertEquals(1, a.effectiveLevel(2)); building(g, "adventureAcademy")
        // une recrue rattrape l'équipe, mais jamais au-delà du plafond de ses propres étoiles
        assertEquals(20, a.trainingLevel()); assertEquals(10, a.effectiveLevel(2))
        a.heroes[2].stars = 2; assertEquals(20, a.effectiveLevel(2))
        assertEquals(a.levelCost(0), a.levelCost(2))
        assertTrue(a.assign(2, 2)); assertTrue(a.numbers(2).attack > HamletHeroes.defs[2].attack * 2)
        a.add(AdventureResource.FOOD, a.levelCost(2)); assertTrue(a.levelUp(2)); assertEquals(21, a.heroes[2].level)
        assertEquals(20, a.trainingLevel())
    }
    @Test fun `equipement et tactique traversent la sauvegarde et arrivent au vrai combat`() {
        val g = game(); val a = g.adventure; building(g, "adventureForge")
        a.add(AdventureResource.ORE, 200); a.add(AdventureResource.STONE, 100)
        val before = a.numbers(1)
        val piece = g.armory.craft(1, GearSet.HEARTHWARD, EquipmentRarity.RARE)!!
        assertTrue(g.armory.equip(1, piece.id)); assertTrue(a.numbers(1).hp > before.hp)
        assertTrue(a.selectTactic(BattleTactic.DEFENSIVE))
        val restored = game(); assertTrue(HamletSave.fromJson(restored, HamletSave.toJson(g)))
        assertEquals(piece, restored.armory.equipped(1, 1)); assertEquals(BattleTactic.DEFENSIVE, restored.adventure.tactics[0])
        val battle = restored.adventure.beginBattle(BattleMode.CAMPAIGN, 0, 3)!!
        assertEquals(BattleTactic.DEFENSIVE, battle.tactic); assertTrue(battle.units.first { it.index == 1 }.shield > 0)
        assertFalse(restored.armory.unequip(1, 1)); restored.adventure.abandonBattle(); assertTrue(restored.armory.unequip(1, 1))
    }
    @Test fun `ateliers convertissent les ressources sans pertes partielles ni boucle gratuite`() {
        val g = game(); val a = g.adventure; val before = a.bag.clone()
        assertFalse(a.workshopSupplies(false)); assertFalse(a.workshopSupplies(true)); assertArrayEquals(before, a.bag)
        building(g, "adventureStore"); building(g, "adventureAcademy")
        assertTrue(a.workshopSupplies(false)); assertEquals(before[1] - 12, a.bag[1]); assertEquals(before[2] - 12, a.bag[2]); assertEquals(before[3] + 8, a.bag[3])
        val second = a.bag.clone(); assertTrue(a.workshopSupplies(true)); assertEquals(second[0] - 16, a.bag[0]); assertEquals(second[1] - 16, a.bag[1]); assertEquals(second[4] + 8, a.bag[4])
        a.bag[1] = 0; val empty = a.bag.clone(); assertFalse(a.workshopSupplies(false)); assertArrayEquals(empty, a.bag)
    }
    @Test fun `portail reduit les futurs voyages mais ne modifie pas le depart enregistre`() {
        val g = game(); val j = g.journey; val before = j.expeditionSeconds(0)
        assertTrue(j.startExpedition(0)); val arrival = j.arrival
        building(g, "adventureGate", 3)
        assertTrue(j.expeditionSeconds(0) < before); assertEquals(arrival, j.arrival)
    }
    @Test fun `la milice soutient les heros sans entrainement infini ni depense au dela de lescorte`() {
        val g = game(); val a = g.adventure; building(g, "adventureYard", 3); a.frontier = 200
        AdventureResource.entries.forEach { a.add(it, 100_000) }
        assertEquals(120, a.marchCapacity()); assertEquals(10, a.maxTroopTier())
        a.troops[0] = 117; val food = a[AdventureResource.FOOD]
        assertTrue(a.trainTroops(0)); assertEquals(120, a.troops[0]); assertEquals(food - 6, a[AdventureResource.FOOD])
        val full = a.bag.clone(); assertFalse(a.trainTroops(0)); assertArrayEquals(full, a.bag)
        repeat(9) { assertTrue(a.improveTroops(0)) }; val capped = a.bag.clone()
        assertFalse(a.improveTroops(0)); assertArrayEquals(capped, a.bag)
    }
}
