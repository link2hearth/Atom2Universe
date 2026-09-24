package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

class ParryBalanceTest {
    /** Un gobelin qui frappe ; [dodge] : le tirage d'esquive, [perk] : celui de l'atout (1 : jamais d'atout). */
    private fun combat(hero: Hero, dodge: Float = 1f, perk: Float = 1f, seed: Int = 0): Combat = Combat(
        hero, 1, listOf(Enemy(MonsterType.GOBLIN, 100000, 100, 1, 1)),
        ambush = true, rng = Random(seed), dodgeRoll = { dodge }, perkRoll = { perk },
    ).also { it.startEnemyTurn() }

    private fun heroOf(archetype: Archetype) = Hero.starter().apply {
        for (base in listOf(ItemBase.HELMET, ItemBase.ARMOR, ItemBase.BOOTS)) {
            val item = LootSystem.create(base, 1, Rarity.NORMAL, 0, Random(0), forcedWeight = archetype.weight)
            equipped[item.slot] = item
        }
        addRelic(Relic.FIREBALL)
        relicCooldowns[Relic.FIREBALL] = 3
    }

    @Test fun timingSelectsDamageBeforeArmorWithoutAnotherRandomSpread() {
        for ((timing, multiplier) in listOf(Timing.PERFECT to .6f, Timing.GOOD to .85f, Timing.MISS to 1.1f)) {
            repeat(10) { seed ->
                val hero = Hero.starter()
                val expected = hero.mitigate(100f * Dodge.damageMult * multiplier, 1).roundToInt()
                val strike = combat(hero, seed = seed).resolveStrike(0, timing)
                assertEquals(expected, strike.damage)
                assertFalse(strike.missed)
            }
        }
    }

    @Test fun timingDoesNotChangeDodge() {
        // Le geste choisit les dégâts et l'atout ; l'esquive ne vient que de la DEX
        val hero = Hero.starter().apply {
            equipped[EquipSlot.RING] = LootSystem.create(ItemBase.RING, 1, Rarity.NORMAL, 0, Random(0))
                .copy(implicits = listOf(StatRoll(StatType.DEX, 5f)), affixes = emptyList())
        }
        fun avoided(timing: Timing) = (0 until 20).count { i -> combat(hero, dodge = i / 20f).resolveStrike(0, timing).missed }
        val baseline = avoided(Timing.MISS)
        assertTrue(baseline > 0)
        assertEquals(baseline, avoided(Timing.GOOD))
        assertEquals(baseline, avoided(Timing.PERFECT))
    }

    @Test fun perfectTimingWithoutThePerkRollGivesNoPerk() {
        for (archetype in Archetype.entries) {
            val hero = heroOf(archetype)
            val c = combat(hero)
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

    @Test fun thePerkTriggersEvenOnAHit() {
        // Le monstre ne rate pas (tirage d'esquive à 1), mais l'atout tombe : le blocage, la riposte et la roulade évitent le coup
        for (archetype in listOf(Archetype.WARRIOR, Archetype.ROGUE, Archetype.VAGABOND)) {
            val c = combat(heroOf(archetype), perk = 0f)
            val strike = c.resolveStrike(0, Timing.PERFECT)
            assertEquals("$archetype évite le coup", 0, strike.damage)
            assertTrue(strike.blocked || strike.dodged)
        }
        assertNotNull(combat(heroOf(Archetype.ROGUE), perk = 0f).resolveStrike(0, Timing.PERFECT).counter)
        assertTrue(combat(heroOf(Archetype.VAGABOND), perk = 0f).also { it.resolveStrike(0, Timing.PERFECT) }.rollReady)
        // Le mage récupère une recharge, mais prend le coup
        val mage = heroOf(Archetype.MAGE)
        val strike = combat(mage, perk = 0f).resolveStrike(0, Timing.PERFECT)
        assertTrue(strike.recovered)
        assertTrue(strike.damage > 0)
    }

    @Test fun thePerkChanceStartsAtTwentyAndIsCapped() {
        val hero = Hero.starter()
        assertEquals(Hero.BASE_CLASS_PERK, hero.classPerkChance, 0f)
        hero.equipped[EquipSlot.RING] = LootSystem.create(ItemBase.RING, 1, Rarity.NORMAL, 0, Random(0))
            .copy(implicits = listOf(StatRoll(StatType.CLASS_PERK, 0.9f)), affixes = emptyList())
        assertEquals(Hero.MAX_CLASS_PERK, hero.classPerkChance, 0f)
    }

    @Test fun onlyPerfectTimingTriggersThePerk() {
        for (timing in listOf(Timing.GOOD, Timing.MISS)) {
            val strike = combat(heroOf(Archetype.WARRIOR), perk = 0f).resolveStrike(0, timing)
            assertFalse(strike.blocked)
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
