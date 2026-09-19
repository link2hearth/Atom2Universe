package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Les sets d'isotope (voir DONJON.md, « Les sets d'isotopes ») : chute, force, trois pièces, bonus par archétype. */
class IsotopeSetTest {

    private fun set(z: Int) = IsotopeSets.of(z)!!
    private val deuterium get() = set(1)
    private val helium3 get() = set(2)
    private val lithium6 get() = set(3)

    private fun piece(s: IsotopeSet, base: ItemBase, seed: Int = 0) = LootSystem.createSetPiece(s, base, 0, Random(seed))

    /** Un héros qui porte une pièce de chaque set donné, dans l'ordre casque, armure, bottes. */
    private fun heroWearing(vararg sets: IsotopeSet) = Hero.starter().apply {
        for ((i, s) in sets.withIndex()) { val p = piece(s, IsotopeSets.BASES[i]); equipped[p.slot] = p }
    }

    private fun full(s: IsotopeSet) = heroWearing(s, s, s)

    /** La même armure sans le set : mêmes stats, mais aucune pièce d'isotope. */
    private fun withoutSet(hero: Hero) = hero.also { h ->
        for (slot in IsotopeSets.SLOTS) h.equipped[slot] = h.equipped.getValue(slot).copy(isotopeZ = null)
    }

    @Test
    fun lArchetypeTourneAvecLeNumeroAtomique() {
        val wanted = listOf(Archetype.WARRIOR, Archetype.ROGUE, Archetype.MAGE, Archetype.VAGABOND, Archetype.NECROMANCER, Archetype.WARRIOR)
        for ((i, a) in wanted.withIndex()) assertEquals(a, IsotopeSet(i + 1, 1).archetype)
        assertEquals(Archetype.WARRIOR, deuterium.archetype)
        assertEquals(Archetype.ROGUE, helium3.archetype)
        assertEquals(Archetype.MAGE, lithium6.archetype)
    }

    @Test
    fun chaqueSetNeTombeQueDansSaTranche() {
        val rng = Random(1)
        for (s in IsotopeSets.ALL) {
            for (floor in s.firstFloor..s.lastFloor) assertEquals(s, IsotopeSets.forFloor(floor))
            assertTrue(IsotopeSets.forFloor(s.firstFloor - 1) != s)
        }
        assertNull(IsotopeSets.forFloor(126))
        repeat(3000) {
            val outside = LootSystem.generate(rng.nextInt(126, 400), 0, rng)
            assertNull("aucune pièce de set hors des tranches", outside.isotopeZ)
        }
    }

    @Test
    fun huitPourCentDesObjetsDeLaTrancheSontDuSet() {
        val rng = Random(2)
        val n = 20000
        for (s in IsotopeSets.ALL) {
            val share = List(n) { LootSystem.generate(rng.nextInt(s.firstFloor, s.lastFloor + 1), 0, rng) }
                .count { it.isotopeZ == s.z } / n.toFloat()
            assertEquals("set ${s.z}", IsotopeSets.DROP_SHARE, share, 0.01f)
        }
    }

    @Test
    fun lesPiecesSontDuBonPoidsRaresEtFortes() {
        val rng = Random(3)
        for (s in IsotopeSets.ALL) {
            val powers = mutableSetOf<Int>()
            repeat(500) {
                val e = LootSystem.createSetPiece(s, IsotopeSets.BASES.random(rng), 0, rng)
                assertEquals(s.archetype.weight, e.weight)
                assertEquals(Rarity.RARE, e.rarity)
                assertTrue(e.slot in IsotopeSets.SLOTS)
                powers += e.power
            }
            val base = IsotopeSets.basePower(s.z)
            assertEquals("trois puissances par set", setOf(base, base + 1, base + 2), powers)
        }
        assertEquals(10, IsotopeSets.basePower(1))
        // Mieux que tout le butin ordinaire du premier étage
        assertTrue(LootSystem.scale(10) > 5 * LootSystem.scale(1))
    }

    @Test
    fun sansLesTroisPiecesDuMemeArchetypeRienNeChange() {
        assertNull(Hero.starter().setArchetype)
        assertNull(heroWearing(deuterium, deuterium).setArchetype)
        assertEquals(false, heroWearing(deuterium, deuterium).specialBoosted(Archetype.WARRIOR))
        // Des archétypes différents ne se mélangent pas
        assertNull(heroWearing(deuterium, helium3, deuterium).setArchetype)
        assertNull(heroWearing(deuterium, deuterium, lithium6).setArchetype)
    }

    @Test
    fun leLourdADesPVEnPlus() {
        val hero = full(deuterium)
        assertEquals(Archetype.WARRIOR, hero.setArchetype)
        assertEquals(Archetype.WARRIOR, hero.archetype)
        assertTrue(hero.specialBoosted(Archetype.WARRIOR))
        val withSet = hero.maxHp
        val without = withoutSet(hero).maxHp
        assertEquals(without * (1f + IsotopeSets.HP_SHARE), withSet.toFloat(), 0.5f)
        assertTrue(withSet > without)
    }

    @Test
    fun leLegerVaPlusVite() {
        val hero = full(helium3)
        assertEquals(Archetype.ROGUE, hero.setArchetype)
        assertEquals(Archetype.ROGUE, hero.archetype)
        val withSet = hero.speed
        val without = withoutSet(hero).speed
        assertEquals(without + IsotopeSets.SPEED_BONUS, withSet, 0.001f)
    }

    @Test
    fun leMageFaitPlusDeDegatsDeSorts() {
        val hero = full(lithium6)
        assertEquals(Archetype.MAGE, hero.setArchetype)
        assertEquals(Archetype.MAGE, hero.archetype)
        val relic = Relic.entries.first { it.attribute == StatType.INT }
        val withSet = hero.relicMult(relic)
        val without = withoutSet(hero).relicMult(relic)
        assertEquals(IsotopeSets.SPELL_SHARE, withSet - without, 0.001f)
    }

    private fun fight(hero: Hero) = Combat(hero, 1, listOf(Enemy(MonsterType.GOBLIN, 100000, 10, 1, 1)), ambush = false,
        rng = Random(1), attackDie = { 20 })

    @Test
    fun laGardeRenvoiePlusEtRevientPlusVite() {
        fun guarded(hero: Hero): Pair<Int, Int> {
            val c = fight(hero)
            c.guard()
            val cooldown = hero.specialCooldown
            c.startEnemyTurn()
            return c.resolveStrike(0, Timing.MISS).thorns to cooldown
        }
        val (plainThorns, plainCd) = guarded(withoutSet(full(deuterium)))
        val (setThorns, setCd) = guarded(full(deuterium))
        assertTrue("$setThorns doit dépasser $plainThorns", setThorns > plainThorns)
        assertTrue("$setCd doit être plus court que $plainCd", setCd < plainCd)
    }

    @Test
    fun leCoupMortelFrappePlusFort() {
        fun deadly(hero: Hero): Pair<Int, Int> {
            val c = fight(hero)
            c.enemies[0].hp = 20000   // sous 30 % de ses PV : la cible est exposée
            val hit = c.deadlyStrike(0, Timing.PERFECT)
            return hit.damage to hero.specialCooldown
        }
        val (plain, plainCd) = deadly(withoutSet(full(helium3)))
        val (boosted, boostedCd) = deadly(full(helium3))
        assertTrue("$boosted doit dépasser $plain", boosted > plain)
        assertTrue(boostedCd < plainCd)
    }

    @Test
    fun lImageMiroirAQuatreDoubles() {
        val plain = fight(withoutSet(full(lithium6))).also { it.mirrorImage() }
        val boosted = fight(full(lithium6)).also { it.mirrorImage() }
        assertEquals(Combat.MIRROR_IMAGES, plain.mirrorImages)
        assertEquals(IsotopeSets.MIRROR_IMAGES, boosted.mirrorImages)
    }

    @Test
    fun laFicheDuSetResteCacheJusquALaPremierePiece() {
        for (s in IsotopeSets.ALL) {
            val entry = Lexicon.find(s.lexiconId)!!
            val hero = Hero.starter()
            assertTrue(entry.secret)
            assertTrue(!entry.visible(hero))
            hero.knownSets += s.z
            assertTrue(entry.visible(hero))
        }
    }
}
