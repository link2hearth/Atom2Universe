package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Les sets d'isotope (voir DONJON.md, « Les sets d'isotopes ») : chute, force, trois pièces, Spécial amélioré. */
class IsotopeSetTest {

    private val deuterium get() = IsotopeSets.of(1)!!

    private fun piece(base: ItemBase, seed: Int = 0) = LootSystem.createSetPiece(deuterium, base, 0, Random(seed))

    private fun heroWearing(vararg bases: ItemBase) = Hero.starter().apply {
        for (b in bases) { val p = piece(b); equipped[p.slot] = p }
    }

    private val fullSet = arrayOf(ItemBase.HELMET, ItemBase.ARMOR, ItemBase.BOOTS)

    @Test
    fun lArchetypeTourneAvecLeNumeroAtomique() {
        val wanted = listOf(Archetype.WARRIOR, Archetype.ROGUE, Archetype.MAGE, Archetype.WARRIOR)
        for ((i, a) in wanted.withIndex()) assertEquals(a, IsotopeSet(i + 1, 1).archetype)
        assertEquals("le deutérium est lourd", Archetype.WARRIOR, deuterium.archetype)
    }

    @Test
    fun leSetNeTombeQueDansSaTranche() {
        val rng = Random(1)
        for (floor in 1..25) assertEquals(deuterium, IsotopeSets.forFloor(floor))
        for (floor in listOf(26, 50, 500)) assertNull(IsotopeSets.forFloor(floor))
        repeat(3000) {
            val outside = LootSystem.generate(rng.nextInt(26, 200), 0, rng)
            assertNull("aucune pièce de set hors de sa tranche", outside.isotopeZ)
        }
    }

    @Test
    fun huitPourCentDesObjetsDeLaTrancheSontDuSet() {
        val rng = Random(2)
        val n = 20000
        val share = List(n) { LootSystem.generate(rng.nextInt(1, 26), 0, rng) }.count { it.isotopeZ == 1 } / n.toFloat()
        assertEquals(IsotopeSets.DROP_SHARE, share, 0.01f)
    }

    @Test
    fun lesPiecesSontLourdesRaresEtFortes() {
        val rng = Random(3)
        val powers = mutableSetOf<Int>()
        repeat(500) {
            val e = LootSystem.createSetPiece(deuterium, IsotopeSets.BASES.random(rng), 0, rng)
            assertEquals(ArmorWeight.HEAVY, e.weight)
            assertEquals(Rarity.RARE, e.rarity)
            assertTrue(e.slot in IsotopeSets.SLOTS)
            assertTrue("puissance ${e.power}", e.power in 10..12)
            powers += e.power
        }
        assertEquals("les trois puissances sortent", setOf(10, 11, 12), powers)
        // Mieux que tout le butin ordinaire du premier étage
        assertTrue(LootSystem.scale(10) > 5 * LootSystem.scale(1))
    }

    @Test
    fun sansLesTroisPiecesRienNeChange() {
        assertNull(Hero.starter().activeSet)
        val two = heroWearing(ItemBase.HELMET, ItemBase.ARMOR)
        assertNull(two.activeSet)
        assertEquals(false, two.specialBoosted(Archetype.WARRIOR))
    }

    @Test
    fun lesTroisPiecesDonnentDesPVEnPlus() {
        val hero = heroWearing(*fullSet)
        assertEquals(deuterium, hero.activeSet)
        assertEquals(Archetype.WARRIOR, hero.archetype)
        assertTrue(hero.specialBoosted(Archetype.WARRIOR))
        val withSet = hero.maxHp
        for (slot in IsotopeSets.SLOTS) hero.equipped[slot] = hero.equipped.getValue(slot).copy(isotopeZ = null)
        val without = hero.maxHp
        assertEquals(without * (1f + deuterium.hpShare), withSet.toFloat(), 0.5f)
        assertTrue(withSet > without)
        for (slot in IsotopeSets.SLOTS) hero.equipped[slot] = hero.equipped.getValue(slot).copy(isotopeZ = deuterium.z)
        // Ôter une pièce ôte le bonus
        hero.equipped.remove(EquipSlot.BOOTS)
        assertNull(hero.activeSet)
    }

    @Test
    fun laGardeRenvoiePlusEtRevientPlusVite() {
        fun guarded(hero: Hero): Pair<Int, Int> {
            val c = Combat(hero, 1, listOf(Enemy(MonsterType.GOBLIN, 100000, 10, 1, 1)), ambush = false,
                rng = Random(1), attackDie = { 20 })
            c.guard()
            val cooldown = hero.specialCooldown
            c.startEnemyTurn()
            return c.resolveStrike(0, Timing.MISS).thorns to cooldown
        }
        val (plainThorns, plainCd) = guarded(Hero.starter().apply {
            for (b in fullSet) { val p = LootSystem.create(b, 10, Rarity.RARE, 0, Random(0), ArmorWeight.HEAVY); equipped[p.slot] = p }
        })
        val (setThorns, setCd) = guarded(heroWearing(*fullSet))
        assertTrue("$setThorns doit dépasser $plainThorns", setThorns > plainThorns)
        assertTrue("$setCd doit être plus court que $plainCd", setCd < plainCd)
    }

    @Test
    fun laFicheDuSetResteCacheJusquALaPremierePiece() {
        val entry = Lexicon.find(deuterium.lexiconId)!!
        val hero = Hero.starter()
        assertTrue(entry.secret)
        assertTrue(!entry.visible(hero))
        hero.knownSets += deuterium.z
        assertTrue(entry.visible(hero))
    }
}
