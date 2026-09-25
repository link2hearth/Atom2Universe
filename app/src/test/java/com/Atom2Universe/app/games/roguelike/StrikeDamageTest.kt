package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class StrikeDamageTest {
    private fun hero(low: Int = 10, high: Int = 20, critical: Float = 0f) = Hero().apply {
        equipped[EquipSlot.WEAPON] = Equipment(ItemBase.SWORD, 1, Rarity.COMMON,
            low, high, 0, listOf(StatRoll(StatType.CRIT_CHANCE, critical - Hero.BASE_CRIT)),
            emptyList(), 0, 0, 1L)
    }

    private fun combat(hero: Hero, floor: Int = 1, seed: Int = 0) = Combat(hero, floor,
        listOf(Enemy(MonsterType.GOBLIN, 100000, 1, 1, 1)), false, Random(seed))

    @Test fun perfectUsesMaximumAndGoodUsesMidpointWithoutCriticals() {
        val perfect = combat(hero()).attack(0, Timing.PERFECT)
        val good = combat(hero()).attack(0, Timing.GOOD)
        val miss = combat(hero()).attack(0, Timing.MISS)
        assertEquals(20, perfect.damage)
        assertEquals(15, good.damage)
        assertTrue(miss.damage in 1..9)
        assertFalse(perfect.crit)
        assertFalse(good.crit)
        assertFalse(miss.crit)
    }

    @Test fun timingDoesNotChangeTheCriticalRoll() {
        repeat(100) { seed ->
            val criticals = Timing.entries.map { timing ->
                combat(hero(critical = .35f), seed = seed).attack(0, timing).crit
            }
            assertEquals(1, criticals.distinct().size)
        }
    }

    @Test fun betterEquipmentCanOvercomeDefenseOnAMissedGesture() {
        val weak = combat(hero(), floor = 100).attack(0, Timing.MISS)
        val strong = combat(hero(100, 150), floor = 100).attack(0, Timing.MISS)
        assertEquals(0, weak.damage)
        assertFalse(weak.crit)
        assertTrue(strong.damage in 1..99)
    }

    @Test fun blockedSpellDoesNotInflictItsBurn() {
        val h = hero().apply { addRelic(Relic.FIREBALL) }
        val c = combat(h, floor = 1000)
        val hit = c.castRelic(Relic.FIREBALL, 0, Timing.MISS).main!!
        assertEquals(0, hit.damage)
        assertEquals(0, c.enemies[0].burnTurns)
        assertEquals(c.enemies[0].maxHp, c.enemies[0].hp)
    }

    @Test fun blockedWeaponDoesNotHealThroughLifeSteal() {
        val h = hero().apply {
            val weapon = equipped.getValue(EquipSlot.WEAPON)
            equipped[EquipSlot.WEAPON] = weapon.copy(affixes = listOf(StatRoll(StatType.LIFE_STEAL, 1f)))
            hp = 1
        }
        assertEquals(0, combat(h, floor = 1000).attack(0, Timing.MISS).damage)
        assertEquals(1, h.hp)
    }

    @Test fun tougherMonstersBossesAndDepthIncreaseOnlyMissDefense() {
        val rat = StrikeDamage.defense(MonsterType.RAT, 1, false)
        val demon = StrikeDamage.defense(MonsterType.DEMON, 1, false)
        assertTrue(demon > rat)
        assertTrue(StrikeDamage.defense(MonsterType.DEMON, 1, true) > demon)
        assertTrue(StrikeDamage.defense(MonsterType.DEMON, 100, false) > demon)
        assertEquals(20f, StrikeDamage.afterDefense(20f, Timing.PERFECT, 1000f), 0f)
        assertEquals(15f, StrikeDamage.afterDefense(15f, Timing.GOOD, 1000f), 0f)
        assertEquals(0f, StrikeDamage.afterDefense(10f, Timing.MISS, 1000f), 0f)
    }
}
