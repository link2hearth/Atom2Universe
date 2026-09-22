package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class BarbarianTest {
    private fun hero(club: Boolean = true) = Hero().apply {
        for (base in IsotopeSets.BASES + ItemBase.AXE + if (club) listOf(ItemBase.CLUB) else emptyList()) {
            val item = LootSystem.create(base, 10, Rarity.NORMAL, nextLootId++, Random(1), ArmorWeight.FUR)
                .copy(affixes = emptyList())
            equipped[item.slot] = item
        }
        healFull()
    }
    private fun fight(hero: Hero) = Combat(hero, 20,
        listOf(Enemy(MonsterType.GOBLIN, 100000, 1, 1, 1)), ambush = false, rng = Random(7))

    @Test fun classAndFiveDistinctElements() {
        val hero = hero()
        assertEquals(Archetype.BARBARIAN, hero.archetype)
        assertEquals(StatType.STR, hero.weaponAttribute)
        val relics = Relic.entries.filter { it.attribute == StatType.STR }
        assertEquals(5, relics.size)
        assertEquals(setOf(Element.FIRE, Element.ICE, Element.LIGHTNING, Element.POISON, Element.PHYSICAL),
            relics.map { it.element }.toSet())
        assertTrue(relics.all { it.minCooldown >= 3 })
    }

    @Test fun smashOpensBreachOnlyWithClubAndNeverParalyzes() {
        val withClub = fight(hero())
        val withoutClub = fight(hero(false))
        assertTrue(withClub.smash(0, Timing.PERFECT).damage > 0)
        withoutClub.smash(0, Timing.PERFECT)
        assertEquals(Combat.BREACH_TURNS, withClub.enemies[0].breachedTurns)
        assertEquals(0, withoutClub.enemies[0].breachedTurns)
        assertEquals(0, withClub.enemies[0].paralyzedTurns)
    }

    @Test fun legacySetsKeepTheirClassesAndFurVariantActivatesBarbarian() {
        val original = listOf(Archetype.WARRIOR, Archetype.ROGUE, Archetype.MAGE,
            Archetype.VAGABOND, Archetype.NECROMANCER, Archetype.WARRIOR)
        for (z in 1..6) assertEquals(original[z - 1], IsotopeSets.of(z)!!.archetype)
        val hero = hero()
        for (base in IsotopeSets.BASES) {
            val item = LootSystem.createSetPiece(IsotopeSets.of(1)!!.copy(barbarian = true), base, 1, Random(0))
            hero.equipped[item.slot] = item
            assertEquals(Archetype.BARBARIAN, item.isotopeSet!!.archetype)
        }
        assertEquals(Archetype.BARBARIAN, hero.setArchetype)
        val fight = fight(hero)
        fight.smash(0, Timing.PERFECT)
        assertEquals(IsotopeSets.BARBARIAN_BREACH_TURNS, fight.enemies[0].breachedTurns)
    }

    @Test fun wisdomCannotRemoveSpecialCooldown() {
        val hero = hero()
        hero.equipped[EquipSlot.WEAPON] = hero.equipped.getValue(EquipSlot.WEAPON).copy(
            affixes = listOf(StatRoll(StatType.WIS, 10000f)))
        val fight = fight(hero)
        fight.smash(0, Timing.PERFECT)
        assertEquals(Hero.MIN_SPECIAL_COOLDOWN, hero.specialCooldown)
    }

    @Test fun wandererRelicsKeepTheirSaveIdentifiersAndDistinctRole() {
        assertEquals(RelicTarget.MISSILES, Relic.valueOf("CHAIN_LIGHTNING").target)
        assertEquals(RelicTarget.ALL, Relic.valueOf("WHIRLWIND").target)
        assertEquals(StatType.END, Relic.WHIRLWIND.attribute)
        assertTrue(RelicBudget.poisonDotShare(Relic.CHAMPIGNON) > RelicBudget.poisonDotShare(Relic.VENOMOUS_WOUND))
        assertEquals(1.5f, Combat.CHAIN_HIT_MULT * (2f + Combat.LANTERN_CHAIN_BONUS), .001f)
    }
}
