package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

class ParryBalanceTest {
    private fun combat(hero: Hero, die: Int, seed: Int = 0): Combat = Combat(
        hero, 1, listOf(Enemy(MonsterType.GOBLIN, 100000, 100, 1, 1)),
        ambush = true, rng = Random(seed), attackDie = { die },
    ).also { it.startEnemyTurn() }

    @Test fun timingSelectsDamageBeforeArmorWithoutAnotherRandomSpread() {
        for ((timing, multiplier) in listOf(Timing.PERFECT to .6f, Timing.GOOD to .85f, Timing.MISS to 1.1f)) {
            repeat(10) { seed ->
                val hero = Hero.starter()
                val expected = hero.mitigate(100f * ArmorClass.DAMAGE_COMPENSATION * multiplier, 1).roundToInt()
                val strike = combat(hero, 20, seed).resolveStrike(0, timing)
                assertEquals(expected, strike.damage)
                assertFalse(strike.missed)
            }
        }
    }

    @Test fun timingAddsOneOrTwoDefensiveFacesOnTheSameDie() {
        fun avoided(timing: Timing) = (1..20).count { die ->
            combat(Hero.starter(), die).resolveStrike(0, timing).missed
        }
        val baseline = avoided(Timing.MISS)
        assertEquals(baseline + 1, avoided(Timing.GOOD))
        assertEquals(baseline + 2, avoided(Timing.PERFECT))
        for (timing in Timing.entries) {
            val hits = (1..20).count { ArmorClass.hits(it, 10000, 0, timing) }
            assertEquals(6, hits) // Le plafond de défense reste à 70 %.
        }
    }

    @Test fun perfectTimingCannotGuaranteeAvoidanceOrClassPerks() {
        for (archetype in Archetype.entries) {
            val hero = Hero.starter().apply {
                for (base in listOf(ItemBase.HELMET, ItemBase.ARMOR, ItemBase.BOOTS)) {
                    val item = LootSystem.create(base, 1, Rarity.NORMAL, 0, Random(0), forcedWeight = archetype.weight)
                    equipped[item.slot] = item
                }
                addRelic(Relic.FIREBALL)
                relicCooldowns[Relic.FIREBALL] = 3
            }
            val c = combat(hero, 20)
            val cooldown = hero.relicCooldown(Relic.FIREBALL)
            val strike = c.resolveStrike(0, Timing.PERFECT)
            assertTrue("$archetype subit le coup", strike.damage + strike.puppetAbsorbed > 0)
            assertFalse(strike.blocked)
            assertFalse(strike.dodged)
            assertFalse(strike.recovered)
            assertNull(strike.counter)
            assertFalse(c.rollReady)
            assertEquals(cooldown, hero.relicCooldown(Relic.FIREBALL))
        }
    }

    @Test fun equipmentStillDeterminesTheDefensiveThreshold() {
        val hero = Hero.starter()
        val before = hero.armorClass
        val shield = LootSystem.create(ItemBase.SHIELD, 1, Rarity.NORMAL, 0, Random(0))
        hero.equipped[EquipSlot.OFFHAND] = shield
        val attack = ArmorClass.monsterAttack(1)
        for (timing in Timing.entries) {
            val without = (1..20).count { !ArmorClass.hits(it, before, attack, timing) }
            val with = (1..20).count { !ArmorClass.hits(it, hero.armorClass, attack, timing) }
            assertEquals(without + ArmorClass.SHIELD, with)
        }
    }

    @Test fun earlyDamageReductionFadesWithoutACliffOrChangingDeepFloors() {
        assertEquals(.8f, Encounters.earlyDamageMult(1), .0001f)
        assertEquals(.8f, Encounters.earlyDamageMult(5), .0001f)
        assertEquals(1f, Encounters.earlyDamageMult(20), .0001f)
        assertEquals(1f, Encounters.earlyDamageMult(1000), .0001f)
        for (floor in 2..100) assertTrue(Encounters.damageMult(floor) >= Encounters.damageMult(floor - 1))
        val oldDeep = Encounters.DAMAGE_SCALE * (1f + .15f * (Encounters.DEEP_FLOOR - 1)) * Encounters.depthMult(1000)
        assertEquals(oldDeep, Encounters.damageMult(1000), .0001f)
    }
}
