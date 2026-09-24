package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Les sets d'isotope (voir DONJON.md, « Les sets d'isotopes ») : chute, force, trois pièces, bonus par archétype. */
class IsotopeSetTest {

    private fun set(z: Int) = IsotopeSets.ofElement(z)!!
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
    fun apresLOganessonOnRepartDuDeuterium() {
        val lap = 118 * IsotopeSets.BAND_FLOORS
        val again = IsotopeSets.forFloor(lap + 1)!!
        assertEquals(1, again.z)
        assertEquals(1, again.cycle)
        assertEquals(119, again.index)
        assertEquals(deuterium.archetype, again.archetype)
        assertEquals(lap + 1, again.firstFloor)
        assertEquals(deuterium, IsotopeSets.of(1))
        assertEquals(again, IsotopeSets.of(again.index))
        // Sa puissance suit le même nom d'objet que le butin : le deuxième tour de l'hydrogène
        val power = IsotopeSets.basePower(again.index)
        assertEquals(0, Grade.element(power))
        assertEquals(1, Grade.cycle(power))
        // La pièce se souvient du tour, et deux tours d'un même archétype se combinent
        val e = LootSystem.createSetPiece(again, ItemBase.HELMET, 0, Random(1))
        assertEquals(119, e.isotopeZ)
        assertEquals(again, e.isotopeSet)
        assertEquals(Archetype.WARRIOR, heroWearing(again, deuterium, again).setArchetype)
        assertEquals(Archetype.WARRIOR, IsotopeSets.forFloor(lap * 3 + 1)!!.archetype)
    }

    @Test
    fun tousLesSetsTombentPartoutAuTierSuperieur() {
        val rng = Random(2)
        for (floor in listOf(1, 5, 6, 25, 26, 501, 525, 2950, 2951, 10000)) {
            val pieces = List(20000) { LootSystem.generate(floor, 0, rng) }.filter { it.isotopeZ != null }
            assertEquals(IsotopeSets.DROP_SHARE, pieces.size / 20000f, 0.01f)
            assertEquals(Archetype.entries.toSet(), pieces.map { it.isotopeSet!!.archetype }.toSet())
            val center = kotlin.math.floor(LootSystem.powerCenter(floor).toDouble()).toInt()
            val next = ((center - 1) / Grade.POWER_PER_TIER + 1) * Grade.POWER_PER_TIER + 1
            for (piece in pieces) {
                assertTrue(piece.power in next until next + Grade.POWER_PER_TIER)
                assertEquals(Rarity.RARE, piece.rarity)
                assertEquals(piece.isotopeSet!!.archetype.weight, piece.weight)
            }
        }
    }

    @Test
    fun anciensEtNouveauxSetsSeCombinentSansChangerLesStats() {
        for (set in IsotopeSets.PERMANENT) {
            assertEquals(set, IsotopeSets.of(set.index))
            val old = if (set.archetype == Archetype.BARBARIAN) deuterium.copy(barbarian = true)
                else IsotopeSets.ALL.first { it.archetype == set.archetype }
            val hero = heroWearing(old, set, set)
            assertEquals(set.archetype, hero.setArchetype)
            assertTrue(IsotopeSets.discovered(hero, set))
        }
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
        // Le multiplicateur de PV de l'étage (×2,5 au départ) s'applique après, avec son arrondi
        assertEquals(without * (1f + IsotopeSets.HP_SHARE), withSet.toFloat(), 3f)
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
        // Le bonus s'ajoute aux « dégâts des sorts » de l'équipement (aucun ici) : il multiplie le reste
        assertEquals(1f + IsotopeSets.SPELL_SHARE, withSet / without, 0.001f)
    }

    /** Un gobelin qui frappe à 100 : assez fort pour que les parts de renvoi (70 % contre 75 %) ne tombent pas sur le même arrondi. */
    private fun fight(hero: Hero) = Combat(hero, 1, listOf(Enemy(MonsterType.GOBLIN, 100000, 100, 1, 1)), ambush = false,
        rng = Random(1), attackDie = { 20 }, dodgeRoll = { 1f })

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
