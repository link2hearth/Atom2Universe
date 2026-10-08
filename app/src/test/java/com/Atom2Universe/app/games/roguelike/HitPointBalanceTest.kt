package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class HitPointBalanceTest {
    @Test fun starterAndEquipmentHpAreMultiplied() {
        val hero = Hero.starter()
        assertEquals(90, hero.maxHp)
        assertEquals(hero.maxHp, hero.hp)
        val sword = hero.equipped.getValue(EquipSlot.WEAPON)
        hero.equipped[EquipSlot.WEAPON] = sword.copy(affixes = listOf(StatRoll(StatType.MAX_HP, 10f)))
        assertEquals(115, hero.maxHp)
    }

    @Test fun ordinaryEnemiesAndBossesFollowTheCurveWithoutChangingDamageOrCadence() {
        for (floor in listOf(1, 25, 100, 250, 500)) for (count in 1..3) {
            val type = MonsterType.GOBLIN
            val enemies = Encounters.build(List(count) { type }, floor)
            for (enemy in enemies) {
                val ordinaryHp = (type.baseHp * Encounters.hpMult(floor) * if (enemy.isBoss) Encounters.BOSS_HP_MULT else 1f).roundToInt()
                val damage = (type.baseDamage * Encounters.damageMult(floor) * if (enemy.isBoss) Encounters.BOSS_DAMAGE_MULT else 1f).roundToInt()
                assertEquals((ordinaryHp * HitPointBalance.enemyMultiplier(floor)).roundToInt(), enemy.maxHp)
                assertEquals(enemy.maxHp, enemy.hp)
                assertEquals(damage, enemy.damage)
                assertEquals(type.cadence + count - 1, enemy.cadence)
            }
        }
    }

    @Test fun oldSavesScaleOnceAndCurrentSavesStayUnchanged() {
        assertEquals(45, HitPointBalance.restore(18, 1.0, 1))
        assertEquals(45, HitPointBalance.restore(90, 5.0, 1))
        assertEquals(90, HitPointBalance.restore(36, 1.0, 1))
        assertEquals(80, HitPointBalance.restore(100, 5.0, 250))
        for (floor in listOf(1, 100, 250, 500)) assertEquals(73, HitPointBalance.restore(73, HitPointBalance.playerMultiplier(floor), floor))
    }
    @Test fun curvesAreLinearAndStopAtFloor250() {
        assertEquals(2.5, HitPointBalance.playerMultiplier(1), 0.0)
        assertEquals(2.5, HitPointBalance.enemyMultiplier(1), 0.0)
        assertEquals(3.0, HitPointBalance.playerMultiplier(84), 1e-9)
        assertEquals(2.5 + 2.5 / 3, HitPointBalance.enemyMultiplier(84), 1e-9)
        for (floor in listOf(250, 500, 1000)) {
            assertEquals(4.0, HitPointBalance.playerMultiplier(floor), 0.0)
            assertEquals(5.0, HitPointBalance.enemyMultiplier(floor), 0.0)
        }
        val hero = Hero.starter()
        hero.floor = 250
        assertEquals(144, hero.maxHp)
    }
}