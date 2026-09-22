package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class LifeStealTest {
    private fun hero(steal: Float = .1f) = Hero.starter().apply {
        val weapon = equipped.getValue(EquipSlot.WEAPON)
        equipped[EquipSlot.WEAPON] = weapon.copy(affixes = listOf(StatRoll(StatType.LIFE_STEAL, steal)))
        hp = 1
    }

    private fun fight(h: Hero, hp: Int = 10000) = Combat(h, 1,
        listOf(Enemy(MonsterType.GOBLIN, hp, 1, 100, 100)), false, Random(8))

    @Test fun overkillDoesNotHealAndSmallHitsAccumulate() {
        val h = hero()
        val c = Combat(h, 1, List(10) {
            Enemy(MonsterType.GOBLIN, 1, 1, 100, 100)
        }, false, Random(8))
        repeat(10) { c.attack(it, Timing.PERFECT) }
        assertEquals(2, h.hp)
        assertEquals(1, c.lifeStolen)
    }

    @Test fun relicDirectDamageUsesEquipmentLifeSteal() {
        val h = hero().apply { addRelic(Relic.PONCTION) }
        val c = fight(h)
        val hit = c.castRelic(Relic.PONCTION, 0, Timing.PERFECT).main!!
        assertEquals(minOf(h.maxHp - 1, (hit.damage * h.lifeSteal).toInt()), c.lifeStolen)
        assertEquals(1 + c.lifeStolen, h.hp)
    }

    @Test fun totalLifeStealIsCappedEvenForLegacyEquipment() {
        assertEquals(.25f, hero(1f).lifeSteal, 0f)
    }

    @Test fun generatedLifeStealStaysBetweenOneAndFivePercent() {
        var found = 0
        for (power in listOf(1, 10, 100, 1000)) repeat(100) { seed ->
            val e = LootSystem.create(ItemBase.SWORD, power, Rarity.RARE, seed.toLong(), Random(seed))
            for (roll in e.affixes.filter { it.type == StatType.LIFE_STEAL }) {
                found++
                assertTrue(roll.value in .01f.. .05f)
            }
        }
        assertTrue(found > 0)
    }

    @Test fun legacyHealIsDiscardedAndSoulRuptureKeepsItsSaveIdentity() {
        assertNull(Relic.fromSavedName("HEAL"))
        assertNull(Relic.fromSavedName("UNKNOWN"))
        assertEquals(Relic.PONCTION, Relic.fromSavedName("PONCTION"))
        for (relic in Relic.entries) assertEquals(relic, Relic.fromSavedName(relic.name))
    }

    @Test fun periodicDamageDoesNotStealLife() {
        val h = hero().apply { addRelic(Relic.POISONED_BLADES) }
        val c = Combat(h, 1, listOf(Enemy(MonsterType.GOBLIN, 10000, 1, 1, 1)), false, Random(8))
        c.enemies[0].burnDamage = 100
        c.enemies[0].burnTurns = 3
        c.castRelic(Relic.POISONED_BLADES, 0, Timing.PERFECT)
        val before = c.enemies[0].hp
        c.startEnemyTurn()
        assertTrue(c.enemies[0].hp < before)
        assertEquals(0, c.lifeStolen)
        assertEquals(1, h.hp)
    }

    @Test fun rogueRiposteStealsLife() {
        val h = hero()
        for (base in listOf(ItemBase.HELMET, ItemBase.ARMOR, ItemBase.BOOTS)) {
            val e = LootSystem.create(base, 1, Rarity.NORMAL, 0, Random(0), forcedWeight = ArmorWeight.LIGHT)
            h.equipped[e.slot] = e
        }
        h.equipped[EquipSlot.WEAPON] = LootSystem.create(ItemBase.SWORD, 100, Rarity.NORMAL, 0, Random(1)).copy(affixes = listOf(StatRoll(StatType.LIFE_STEAL, .1f)))
        val c = Combat(h, 1, listOf(Enemy(MonsterType.GOBLIN, 10000, 1, 1, 1)), true, Random(8), attackDie = { 1 })
        c.startEnemyTurn()
        val hit = c.resolveStrike(0, Timing.PERFECT).counter!!
        assertTrue(c.lifeStolen > 0)
        assertEquals(minOf(h.maxHp - 1, (hit.damage * h.lifeSteal).toInt()), c.lifeStolen)
    }
    @Test fun areaDamageAddsActualDamageAcrossTargets() {
        val h = hero().apply { addRelic(Relic.WHIRLWIND) }
        val enemies = List(3) { Enemy(MonsterType.GOBLIN, 10000, 1, 100, 100) }
        val c = Combat(h, 1, enemies, false, Random(8))
        c.castRelic(Relic.WHIRLWIND, 0, Timing.PERFECT)
        val removed = enemies.sumOf { it.maxHp - it.hp }
        assertEquals((removed * h.lifeSteal).toInt(), c.lifeStolen)
    }

    @Test fun puppetEchoDoesNotAddLifeSteal() {
        val h = hero()
        for (base in listOf(ItemBase.HELMET, ItemBase.ARMOR, ItemBase.BOOTS)) {
            val e = LootSystem.create(base, 1, Rarity.NORMAL, 0, Random(0), forcedWeight = Archetype.NECROMANCER.weight)
            h.equipped[e.slot] = e
        }
        h.equipped[EquipSlot.WEAPON] = LootSystem.create(ItemBase.SWORD, 100, Rarity.NORMAL, 0, Random(1)).copy(affixes = listOf(StatRoll(StatType.LIFE_STEAL, .1f)))
        val c = fight(h)
        val hit = c.attack(0, Timing.PERFECT)
        assertTrue(hit.echo > 0)
        assertEquals(minOf(h.maxHp - 1, (hit.damage * h.lifeSteal).toInt()), c.lifeStolen)
    }
    @Test fun lifeStealCanDropInEverySlotAndFiveMaxRollsReachTheCap() {
        val h = hero(0f)
        for (base in listOf(ItemBase.SWORD, ItemBase.SHIELD, ItemBase.HELMET, ItemBase.ARMOR,
            ItemBase.BOOTS, ItemBase.AMULET, ItemBase.RING)) {
            val found = (0..500).asSequence().map { seed ->
                LootSystem.create(base, 100, Rarity.RARE, seed.toLong(), Random(seed))
            }.firstOrNull { item -> item.affixes.any { it.type == StatType.LIFE_STEAL } }
            assertNotNull("Life steal must be available on $base", found)
            h.equipped[base.slot] = found!!.copy(affixes = listOf(StatRoll(StatType.LIFE_STEAL, .05f)))
        }
        assertEquals(.25f, h.lifeSteal, 1e-6f)
        h.equipped.remove(EquipSlot.RING)
        h.equipped.remove(EquipSlot.AMULET)
        assertEquals(.25f, h.lifeSteal, 1e-6f)
    }
}