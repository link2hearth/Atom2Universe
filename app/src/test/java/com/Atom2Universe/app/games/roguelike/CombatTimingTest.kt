package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CombatTimingTest {
    private fun hero(stat: StatType, points: Float) = Hero().apply {
        equipped[EquipSlot.RING] = LootSystem.create(ItemBase.RING, 1, Rarity.NORMAL, 1, Random(0))
            .copy(implicits = listOf(StatRoll(stat, points)), affixes = emptyList())
    }

    @Test fun dexterityDoesNotChangeGestureTiming() {
        val hero = hero(StatType.DEX, 1000f)
        assertEquals(CombatTiming.STRIKE_MS, CombatTiming.strikeMs(hero))
        assertEquals(CombatTiming.PARRY_GOOD_MS, CombatTiming.parryGood(hero), .001f)
        assertEquals(0, hero.parryBonusMs)
    }

    @Test fun enduranceIsMonotonicAndCapped() {
        val baseline = hero(StatType.END, 0f)
        val middle = hero(StatType.END, 30f)
        val maximum = hero(StatType.END, 60f)
        val excessive = hero(StatType.END, 10000f)
        assertTrue(CombatTiming.strikeMs(baseline) < CombatTiming.strikeMs(middle))
        assertTrue(CombatTiming.strikeMs(middle) < CombatTiming.strikeMs(maximum))
        assertEquals(CombatTiming.strikeMs(maximum), CombatTiming.strikeMs(excessive))
        assertEquals(CombatTiming.parryGood(maximum), CombatTiming.parryGood(excessive), .001f)
        assertTrue(CombatTiming.strikeMs(maximum) * CombatTiming.strikePerfect(maximum) > 1350f * .045f)
        assertTrue(CombatTiming.windupMs(maximum) >= 950L)
        assertEquals(baseline.speed, maximum.speed, .001f)
    }

    @Test fun swordUsesItsRolledAttributeAndSpearUsesEndurance() {
        for (attribute in StatType.ATTRIBUTES) {
            val sword = LootSystem.create(ItemBase.SWORD, 1, Rarity.NORMAL, 1, Random(0))
                .copy(implicits = listOf(StatRoll(attribute, 25f)))
            val hero = Hero().apply { equipped[EquipSlot.WEAPON] = sword }
            assertEquals(attribute, hero.weaponAttribute)
            assertEquals(sword.damageMax * 2, hero.weaponMax)
        }
        val spear = LootSystem.create(ItemBase.SPEAR, 1, Rarity.NORMAL, 1, Random(0))
        assertEquals(StatType.END, spear.damageAttribute)
        assertTrue(Archetype.VAGABOND.accepts(spear.base))
    }
}
