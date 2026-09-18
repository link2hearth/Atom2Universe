package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** La classe d'armure, les poids d'armure et le jet d'attaque des monstres. */
class ArmorClassTest {

    private fun piece(base: ItemBase, weight: ArmorWeight?, tier: Int = 1) =
        LootSystem.create(base, Material.LEATHER, tier, Rarity.NORMAL, 0, Random(0), forcedWeight = weight)

    /** Un héros avec trois pièces du même poids et une DEX donnée (par un anneau). */
    private fun heroIn(weight: ArmorWeight, dexBonus: Int = 0) = Hero.starter().apply {
        equipped[EquipSlot.HELMET] = piece(ItemBase.HELMET, weight)
        equipped[EquipSlot.CHEST]  = piece(ItemBase.ARMOR, weight)
        equipped[EquipSlot.BOOTS]  = piece(ItemBase.BOOTS, weight)
        if (dexBonus > 0) equipped[EquipSlot.RING] = piece(ItemBase.RING, null).copy(
            implicits = listOf(StatRoll(StatType.DEX, dexBonus.toFloat())), affixes = emptyList())
    }

    @Test
    fun leHerosDeReferenceEstToucheTroisFoisSurQuatre() {
        // Bouclier, pièces sans bonus, DEX 10, face à l'étage de sa puissance
        for (p in listOf(1, 9, 17, 33)) {
            val ac = ArmorClass.BASE + SpellSave.proficiency(p) + ArmorClass.SHIELD
            val floor = ((p - 1) / 0.4f + 1).toInt()
            assertEquals("puissance $p", ArmorClass.REF_HIT, ArmorClass.hitChance(ac, ArmorClass.monsterAttack(floor)), 0.051f)
        }
    }

    @Test
    fun leLegerAjouteSaCaEtLaDex() {
        val light = heroIn(ArmorWeight.LIGHT, dexBonus = 6)
        val cloth = heroIn(ArmorWeight.CLOTH, dexBonus = 6)
        // +1 par pièce légère ; la DEX (+3) compte dans les deux
        assertEquals(cloth.armorClass + 3, light.armorClass)
    }

    @Test
    fun leLourdNeCompteplusLaDex() {
        val heavyDex = heroIn(ArmorWeight.HEAVY, dexBonus = 6)
        val heavy = heroIn(ArmorWeight.HEAVY)
        assertEquals(heavy.armorClass, heavyDex.armorClass)
    }

    @Test
    fun leLourdEncaisseLeLegerEvite() {
        val heavy = heroIn(ArmorWeight.HEAVY)
        val light = heroIn(ArmorWeight.LIGHT)
        assertTrue(heavy.armor > light.armor)
        assertTrue(light.armorClass > heavy.armorClass)
    }

    @Test
    fun lePoidsNeChangePasLesPv() {
        assertEquals(heroIn(ArmorWeight.HEAVY).maxHp, heroIn(ArmorWeight.CLOTH).maxHp)
    }

    @Test
    fun leBouclierDonneDeuxCa() {
        val hero = Hero.starter()
        val before = hero.armorClass
        hero.equipped[EquipSlot.OFFHAND] = piece(ItemBase.SHIELD, null)
        assertEquals(before + ArmorClass.SHIELD, hero.armorClass)
    }

    @Test
    fun laCaCompteDansLaNote() {
        // Même base, même matière : la note ne doit pas enterrer le léger sous le lourd
        val light = LootSystem.rating(piece(ItemBase.ARMOR, ArmorWeight.LIGHT, tier = 5))
        val heavy = LootSystem.rating(piece(ItemBase.ARMOR, ArmorWeight.HEAVY, tier = 5))
        val cloth = LootSystem.rating(piece(ItemBase.ARMOR, ArmorWeight.CLOTH, tier = 5))
        assertTrue(cloth < light)
        assertTrue("écart léger / lourd raisonnable : $light contre $heavy", light * 1.5f > heavy)
    }

    // ── Jet d'attaque ───────────────────────────────────────────────────────────

    private fun strike(die: Int, enraged: Boolean = false, second: Int = die): EnemyStrike {
        val dice = ArrayDeque(listOf(die, second))
        val enemy = Enemy(MonsterType.GOBLIN, 100, 5, 1, 1).apply { if (enraged) rageTurns = 3 }
        val c = Combat(Hero.starter(), 1, listOf(enemy), ambush = true, rng = Random(1), attackDie = { dice.removeFirst() })
        c.startEnemyTurn()
        return c.resolveStrike(0, Timing.MISS)
    }

    @Test
    fun unUnRateToujoursUnVingtToucheToujours() {
        assertTrue(strike(1).missed)
        assertFalse(strike(20).missed)
    }

    @Test
    fun unRateNeFaitPasDeDegats() {
        assertEquals(0, strike(1).damage)
    }

    @Test
    fun laRageDonneLeDesavantage() {
        // 20 puis 1 : enragé, il garde le 1 et rate ; calme, il garde le 20 et touche
        assertTrue(strike(20, enraged = true, second = 1).missed)
        assertFalse(strike(20, enraged = false, second = 1).missed)
    }
}
