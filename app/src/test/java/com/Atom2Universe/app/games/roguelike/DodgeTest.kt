package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** L'esquive (0 % de base, seule la DEX en donne) et les poids d'armure. */
class DodgeTest {

    private fun piece(base: ItemBase, weight: ArmorWeight?, tier: Int = 1) =
        LootSystem.create(base, tier, Rarity.NORMAL, 0, Random(0), forcedWeight = weight)

    /** Un anneau qui ne donne que [dex] points de DEX. */
    private fun dexRing(dex: Float) = piece(ItemBase.RING, null).copy(
        implicits = listOf(StatRoll(StatType.DEX, dex)), affixes = emptyList())

    /** Un héros nu (DEX 10) de l'étage [floor], avec [dex] points de DEX en plus. */
    private fun heroWithDex(dex: Float, floor: Int) = Hero().apply {
        this.floor = floor
        if (dex > 0f) equipped[EquipSlot.RING] = dexRing(dex)
    }

    /** Trois pièces du même poids, sans arme (l'épée de départ peut porter de la DEX). */
    private fun heroIn(weight: ArmorWeight) = Hero().apply {
        equipped[EquipSlot.HELMET] = piece(ItemBase.HELMET, weight)
        equipped[EquipSlot.CHEST]  = piece(ItemBase.ARMOR, weight)
        equipped[EquipSlot.BOOTS]  = piece(ItemBase.BOOTS, weight)
    }

    @Test
    fun sansDexOnNEsquiveRien() {
        for (floor in listOf(1, 50, 100, 1000)) assertEquals(0f, heroWithDex(0f, floor).dodgeChance(floor), 0f)
    }

    @Test
    fun laDexDeReferenceDonneQuaranteParCentATousLesEtages() {
        for (floor in listOf(1, 10, 50, 100, 500, 5000)) {
            // La DEX se compte en points entiers : à l'étage 1 (7 points de référence), un point vaut près de 6 %
            val ref = Dodge.referenceDex(floor)
            assertEquals("étage $floor", Dodge.AT_REFERENCE, heroWithDex(ref, floor).dodgeChance(floor), 0.03f)
            assertEquals("moitié, étage $floor", Dodge.AT_REFERENCE / 2, heroWithDex(ref / 2, floor).dodgeChance(floor), 0.03f)
        }
    }

    @Test
    fun lEsquiveNeDepassePasSonPlafond() {
        assertEquals(Dodge.MAX, heroWithDex(10 * Dodge.referenceDex(100), 100).dodgeChance(100), 0f)
    }

    @Test
    fun laMemeDexVautMoinsPlusBas() {
        // Les monstres profonds attendent plus de DEX : 30 points esquivent moins à l'étage 100 qu'à l'étage 20
        assertTrue(heroWithDex(30f, 100).dodgeChance(100) < heroWithDex(30f, 20).dodgeChance(20))
    }

    @Test
    fun leLegerEsquiveParSaDexLeLourdNon() {
        val heavy = heroIn(ArmorWeight.HEAVY)
        val light = heroIn(ArmorWeight.LIGHT)
        assertTrue(heavy.armor > light.armor)
        assertEquals(0f, heavy.dodgeChance(1), 0f)
        assertTrue(light.dodgeChance(1) > 0f)
    }

    @Test
    fun laDexCompteEnArmureLourdeCommeAilleurs() {
        val heavy = heroIn(ArmorWeight.HEAVY).apply { equipped[EquipSlot.RING] = dexRing(6f) }
        val cloth = heroIn(ArmorWeight.CLOTH).apply { equipped[EquipSlot.RING] = dexRing(6f) }
        assertEquals(cloth.dodgeChance(1), heavy.dodgeChance(1), 0f)
        assertTrue(heavy.dodgeChance(1) > 0f)
    }

    @Test
    fun leBouclierNeDonnePasDEsquive() {
        val hero = Hero.starter()
        val before = hero.dodgeChance(1)
        hero.equipped[EquipSlot.OFFHAND] = piece(ItemBase.SHIELD, null)
        assertEquals(before, hero.dodgeChance(1), 0f)
    }

    @Test
    fun leLourdDonneDesPvParSaCon() {
        // Depuis le 22/09/2026, le lourd porte la CON du guerrier : plus de PV que le tissu (INT)
        assertTrue(heroIn(ArmorWeight.HEAVY).maxHp > heroIn(ArmorWeight.CLOTH).maxHp)
    }

    @Test
    fun lePoidsNeChangePasLaNote() {
        // Même base, même matière : léger ou lourd, c'est le choix d'un archétype, pas d'une flèche du sac.
        // Avant, le léger (+1 CA, +5 % de vitesse) notait 20 à 30 % plus haut et les bots finissaient tous voleurs.
        val ratings = ArmorWeight.entries.associateWith { LootSystem.rating(piece(ItemBase.ARMOR, it, tier = 5)) }
        val low = ratings.values.min(); val high = ratings.values.max()
        assertTrue("les cinq poids notent pareil : $ratings", high <= low * 1.03f)
    }

    // ── Le tirage de l'esquive ──────────────────────────────────────────────────

    /** Un coup de gobelin sur un héros qui esquive 40 % : [roll] (puis [second] s'il a le désavantage). */
    private fun strike(roll: Float, enraged: Boolean = false, second: Float = roll): EnemyStrike {
        val rolls = ArrayDeque(listOf(roll, second))
        val enemy = Enemy(MonsterType.GOBLIN, 100, 5, 1, 1).apply { if (enraged) rageTurns = 3 }
        val hero = heroWithDex(Dodge.referenceDex(1), 1)
        val c = Combat(hero, 1, listOf(enemy), ambush = true, rng = Random(1), dodgeRoll = { rolls.removeFirst() })
        c.startEnemyTurn()
        return c.resolveStrike(0, Timing.MISS)
    }

    @Test
    fun sousLaChanceDEsquiveLeCoupEstEvite() {
        assertTrue(strike(0.1f).missed)
        assertFalse(strike(0.9f).missed)
    }

    @Test
    fun unCoupEviteNeFaitPasDeDegats() {
        assertEquals(0, strike(0.1f).damage)
    }

    @Test
    fun laRageDonneLeDesavantage() {
        // 0,9 puis 0,1 : enragé, le héros garde le meilleur tirage et esquive ; calme, seul le premier compte
        assertTrue(strike(0.9f, enraged = true, second = 0.1f).missed)
        assertFalse(strike(0.9f, enraged = false, second = 0.1f).missed)
    }
}
