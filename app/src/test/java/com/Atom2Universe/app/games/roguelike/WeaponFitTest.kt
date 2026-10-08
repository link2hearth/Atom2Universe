package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Les armes qui vont à un archétype, et le malus des autres (voir DONJON.md, « Armes compatibles »). */
class WeaponFitTest {

    private fun weapon(base: ItemBase, power: Int = 10) = LootSystem.create(base, power, Rarity.COMMON, 0, Random(0))

    private fun heroOf(a: Archetype, weapon: ItemBase?) = Hero.starter().apply {
        for ((slot, base) in listOf(EquipSlot.HELMET to ItemBase.HELMET, EquipSlot.CHEST to ItemBase.ARMOR, EquipSlot.BOOTS to ItemBase.BOOTS))
            equipped[slot] = LootSystem.create(base, 1, Rarity.COMMON, 0, Random(0), forcedWeight = a.weight)
        if (weapon == null) equipped.remove(EquipSlot.WEAPON)
        else equipped[EquipSlot.WEAPON] = weapon(weapon)
    }

    @Test
    fun laTableDesArmesCompatibles() {
        val table = mapOf(
            Archetype.BARBARIAN to setOf(ItemBase.SWORD, ItemBase.AXE),
            Archetype.WARRIOR to setOf(ItemBase.SWORD, ItemBase.MACE),
            Archetype.VAGABOND to setOf(ItemBase.SWORD, ItemBase.SPEAR),
            Archetype.ROGUE to setOf(ItemBase.SWORD, ItemBase.DAGGER),
            Archetype.MAGE to setOf(ItemBase.SWORD, ItemBase.STAFF),
            Archetype.NECROMANCER to setOf(ItemBase.SWORD, ItemBase.SCEPTER),
        )
        val weapons = ItemBase.entries.filter { it.slot == EquipSlot.WEAPON }
        for (a in Archetype.entries) assertEquals(a.name, table.getValue(a), weapons.filter { a.accepts(it) }.toSet())
    }

    @Test
    fun lEpeeVaATousLesArchetypes() {
        for (a in Archetype.entries) assertTrue(a.accepts(ItemBase.SWORD))
    }

    @Test
    fun uneArmeQuiNeVaPasFrappeMoinsFort() {
        val fit = heroOf(Archetype.MAGE, ItemBase.STAFF)
        val wrong = heroOf(Archetype.MAGE, ItemBase.AXE)
        assertEquals(1f, fit.weaponTypeMult, 0.0001f)
        assertEquals(1f - Hero.WRONG_WEAPON_MALUS, wrong.weaponTypeMult, 0.0001f)
        // Même arme, avec et sans archétype : seul le malus change
        val axe = heroOf(Archetype.MAGE, ItemBase.AXE)
        val plainMax = axe.weaponMax
        axe.equipped.remove(EquipSlot.HELMET); axe.equipped.remove(EquipSlot.CHEST); axe.equipped.remove(EquipSlot.BOOTS)
        assertEquals(1f, axe.weaponTypeMult, 0.0001f)
        assertTrue("le malus fait perdre des dégâts", plainMax < axe.weaponMax)
    }

    @Test
    fun sansArchetypeOuSansArmeAucunMalus() {
        val hero = Hero.starter()
        assertEquals(1f, hero.weaponTypeMult, 0.0001f)
        hero.equipped.remove(EquipSlot.WEAPON)
        assertEquals(1f, heroOf(Archetype.WARRIOR, null).weaponTypeMult, 0.0001f)
    }

    @Test
    fun laNoteTientCompteDuMalus() {
        val staff = weapon(ItemBase.STAFF)
        val axe = weapon(ItemBase.AXE)
        assertEquals("sans archétype, la note est celle d'avant", LootSystem.rating(axe), LootSystem.rating(axe, null))
        assertEquals("arme qui va : rien ne change", LootSystem.rating(staff), LootSystem.rating(staff, Archetype.MAGE))
        assertTrue("arme qui ne va pas : moins bien notée", LootSystem.rating(axe, Archetype.MAGE) < LootSystem.rating(axe))
    }

    @Test
    fun lesArmesRestentComparablesEntreElles() {
        // À puissance égale, le multiplicateur de dégâts ne fait pas d'un type d'arme un choix perdant
        val mults = ItemBase.entries.filter { it.slot == EquipSlot.WEAPON }.map { it.damageMult }
        assertTrue("écart de dégâts : ${mults.min()} à ${mults.max()}", mults.max() / mults.min() < 1.25f)
    }
    @Test fun betterOffClassWeaponStillDealsDamageWithItsOwnAttribute() {
        val h = heroOf(Archetype.WARRIOR, ItemBase.MACE)
        h.equipped[EquipSlot.WEAPON] = weapon(ItemBase.MACE, 1)
        val oldDamage = h.weaponMax
        h.equipped[EquipSlot.WEAPON] = weapon(ItemBase.STAFF, 20)
        assertEquals(StatType.INT, h.weaponAttribute)
        assertEquals(.85f, h.weaponTypeMult, .0001f)
        assertTrue(h.weaponMin > 0)
        assertTrue(h.weaponMax > oldDamage)
        val c = Combat(h, 1, listOf(Enemy(MonsterType.GOBLIN, 10000, 1, 100, 100)), false, Random(1))
        assertTrue(c.attack(0, Timing.GOOD).damage > 0)
    }

    @Test fun armorPrimaryFollowsClassAndLegacyMigrationPreservesOtherStats() {
        val mapping = mapOf(ArmorWeight.HEAVY to StatType.CON, ArmorWeight.LIGHT to StatType.DEX,
            ArmorWeight.CLOTH to StatType.INT, ArmorWeight.MEDIUM to StatType.END,
            ArmorWeight.ULTRALIGHT to StatType.WIS, ArmorWeight.FUR to StatType.STR)
        for ((weight, stat) in mapping) for (base in ArmorWeight.WEIGHTED) {
            val item = LootSystem.create(base, 20, Rarity.EPIC, 42, Random(9), forcedWeight = weight)
            assertEquals(stat, item.implicits.first().type)
            val old = item.copy(implicits = item.implicits.mapIndexed { i, roll ->
                if (i == 0) roll.copy(type = StatType.CHA) else roll
            })
            val upgraded = old.withClassPrimaryAttribute()
            assertEquals(item, upgraded)
            assertEquals(upgraded, upgraded.withClassPrimaryAttribute())
        }
    }
}