package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

/** Le stuff fait la classe : l'archétype, sa parade parfaite et son bouton « Spécial ». */
class ArchetypeTest {

    private fun piece(base: ItemBase, weight: ArmorWeight?) =
        LootSystem.create(base, 1, Rarity.NORMAL, 0, Random(0), forcedWeight = weight)

    private fun heroWearing(helmet: ArmorWeight, chest: ArmorWeight, boots: ArmorWeight, shield: Boolean = false) =
        Hero.starter().apply {
            equipped[EquipSlot.HELMET] = piece(ItemBase.HELMET, helmet)
            equipped[EquipSlot.CHEST]  = piece(ItemBase.ARMOR, chest)
            equipped[EquipSlot.BOOTS]  = piece(ItemBase.BOOTS, boots)
            if (shield) equipped[EquipSlot.OFFHAND] = piece(ItemBase.SHIELD, null)
        }

    private fun heroOf(a: Archetype, shield: Boolean = false) = heroWearing(a.weight, a.weight, a.weight, shield)

    /** Un combat contre un gobelin solide ; le d20 d'attaque sort 20 : il touche toujours. */
    private fun fight(hero: Hero, hp: Int = 1000) = Combat(
        hero, 1, listOf(Enemy(MonsterType.GOBLIN, hp, 10, 1, 1)), ambush = true,
        rng = Random(1), attackDie = { 20 },
    )

    @Test
    fun deuxPiecesDuMemePoidsDonnentLArchetype() {
        assertEquals(Archetype.WARRIOR, heroWearing(ArmorWeight.HEAVY, ArmorWeight.HEAVY, ArmorWeight.CLOTH).archetype)
        assertEquals(Archetype.ROGUE, heroWearing(ArmorWeight.LIGHT, ArmorWeight.HEAVY, ArmorWeight.LIGHT).archetype)
        assertEquals(Archetype.MAGE, heroOf(Archetype.MAGE).archetype)
        assertNull("une pièce de chaque : pas d'archétype", heroWearing(ArmorWeight.HEAVY, ArmorWeight.LIGHT, ArmorWeight.CLOTH).archetype)
        assertNull(Hero.starter().archetype)
    }

    @Test
    fun sansArchetypeLeSpecialEstVerrouille() {
        val c = Combat(Hero.starter(), 1, listOf(Enemy(MonsterType.GOBLIN, 100, 5, 1, 1)), ambush = false, rng = Random(1))
        assertFalse(c.canUseSpecial())
    }

    @Test
    fun leGuerrierFrappeMoinsFortAvecSonArme() {
        val hero = heroOf(Archetype.WARRIOR)
        hero.equipped[EquipSlot.WEAPON] = piece(ItemBase.AXE, null)
        assertEquals(Hero.WARRIOR_WEAPON_DAMAGE_MULT, hero.weaponTypeMult, 0.001f)
    }

    @Test
    fun lesDoublesRegenerentLeMage() {
        val hero = heroOf(Archetype.MAGE)
        hero.hp = hero.maxHp / 2
        val before = hero.hp
        val c = Combat(hero, 1, listOf(Enemy(MonsterType.GOBLIN, 1000, 5, 1, 1)), ambush = false, rng = Random(1))
        c.mirrorImage()
        assertEquals((hero.maxHp * Combat.MIRROR_REGEN_SHARE).roundToInt().coerceAtLeast(1), hero.hp - before)
    }

    // ── Parade parfaite ─────────────────────────────────────────────────────────

    @Test
    fun leGuerrierBloqueAuBouclier() {
        val c = fight(heroOf(Archetype.WARRIOR, shield = true))
        c.startEnemyTurn()
        val s = c.resolveStrike(0, Timing.PERFECT)
        assertTrue(s.blocked)
        assertEquals(0, s.damage)
    }

    @Test
    fun leBlocageRenvoieUnCoupDeBouclier() {
        val c = fight(heroOf(Archetype.WARRIOR, shield = true))
        c.startEnemyTurn()
        val s = c.resolveStrike(0, Timing.PERFECT)
        assertTrue(s.thorns > 0)
        assertEquals(1000 - s.thorns, c.enemies[0].hp)
    }

    @Test
    fun enGardeChaqueCoupRecuEstRenvoye() {
        // Même coup reçu (mêmes dés), avec et sans Garde : seule la Garde renvoie
        fun strike(guard: Boolean): EnemyStrike {
            val c = Combat(heroOf(Archetype.WARRIOR), 1, listOf(Enemy(MonsterType.GOBLIN, 1000, 10, 1, 1)), ambush = false,
                rng = Random(1), attackDie = { 20 })
            if (guard) c.guard() else c.waitWithoutDice()
            c.startEnemyTurn()
            return c.resolveStrike(0, Timing.MISS)
        }
        assertEquals(0, strike(guard = false).thorns)
        val guarded = strike(guard = true)
        assertTrue(guarded.damage > 0)
        assertTrue("la moitié du coup brut (${guarded.thorns})", guarded.thorns >= guarded.damage / 2)
    }

    /**
     * Passer son tour sans lancer de dé, au même coût que la Garde (une demi-jauge) : un sort
     * de soutien qui ne fait que soigner, la Régénération.
     */
    private fun Combat.waitWithoutDice() {
        hero.addRelic(Relic.HEAL)
        castRelic(Relic.HEAL, 0, Timing.MISS)
    }

    @Test
    fun sansBouclierLeGuerrierBloqueMaisRenvoieMoins() {
        fun perfect(shield: Boolean): EnemyStrike {
            val c = fight(heroOf(Archetype.WARRIOR, shield = shield))
            c.startEnemyTurn()
            return c.resolveStrike(0, Timing.PERFECT)
        }
        val bare = perfect(shield = false)
        assertTrue("il bloque aussi sans bouclier", bare.blocked)
        assertEquals(0, bare.damage)
        assertTrue(bare.thorns > 0)
        assertTrue("mais il renvoie moins", bare.thorns < perfect(shield = true).thorns)
    }

    @Test
    fun leVoleurEsquiveEtRiposte() {
        val c = fight(heroOf(Archetype.ROGUE))
        c.startEnemyTurn()
        val s = c.resolveStrike(0, Timing.PERFECT)
        assertTrue(s.dodged)
        assertEquals(0, s.damage)
        assertNotNull(s.counter)
        assertTrue(c.enemies[0].hp < 1000)
    }

    @Test
    fun laRiposteQuiAcheveGagneLeCombat() {
        val c = fight(heroOf(Archetype.ROGUE), hp = 1)
        c.startEnemyTurn()
        assertTrue(c.resolveStrike(0, Timing.PERFECT).counter!!.killed)
        assertEquals(CombatPhase.VICTORY, c.phase)
    }

    @Test
    fun leMageRegagneUneRecharge() {
        val hero = heroOf(Archetype.MAGE).apply { addRelic(Relic.FIREBALL); relicCooldowns[Relic.FIREBALL] = 3; specialCooldown = 3 }
        val c = fight(hero)
        c.startEnemyTurn()
        assertTrue(c.resolveStrike(0, Timing.PERFECT).recovered)
        assertEquals(2, hero.relicCooldown(Relic.FIREBALL))
        assertEquals("le contresort ne recharge que les reliques", 3, hero.specialCooldown)
    }

    // ── Le bouton « Spécial » ───────────────────────────────────────────────────

    @Test
    fun laGardeDureJusquAuProchainTour() {
        val c = Combat(heroOf(Archetype.WARRIOR), 1, listOf(Enemy(MonsterType.GOBLIN, 100, 5, 1, 1)), ambush = false, rng = Random(1))
        c.guard()
        assertTrue(c.guarding)
        c.startEnemyTurn(); c.endEnemyTurn()
        assertFalse(c.guarding)
        assertFalse("en recharge", c.canUseSpecial())
    }

    @Test
    fun lImageMiroirPrendLesCoups() {
        val hero = heroOf(Archetype.MAGE)
        val c = Combat(hero, 1, listOf(Enemy(MonsterType.GOBLIN, 1000, 5, 1, 1)), ambush = false,
            rng = Random(1), attackDie = { 20 })
        val hp0 = hero.hp
        c.mirrorImage()
        assertEquals(Combat.MIRROR_IMAGES, c.mirrorImages)
        assertTrue("les doubles régénèrent le mage", hero.hp >= hp0)
        val afterMirror = hero.hp
        c.startEnemyTurn()
        val s = c.resolveStrike(0, Timing.MISS)
        assertTrue(s.imageHit)
        assertEquals(afterMirror, hero.hp)
        assertEquals(Combat.MIRROR_IMAGES - 1, c.mirrorImages)
    }

    @Test
    fun leCoupMortelSurUneCibleExposee() {
        val hero = heroOf(Archetype.ROGUE)
        val enemy = Enemy(MonsterType.GOBLIN, 1000, 5, 1, 1).apply { poisonTurns = 2; poisonDoses = 1; poisonDoseDamage = 1 }
        val c = Combat(hero, 1, listOf(enemy), ambush = false, rng = Random(1))
        val hit = c.deadlyStrike(0, Timing.MISS)
        assertTrue("critique garanti", hit.crit)
        assertTrue(hit.damage >= (hero.weaponMin * (hero.critMult + Combat.DEADLY_CRIT_BONUS)).toInt())
    }

    @Test
    fun uneCibleEntameeEstExposee() {
        val c = Combat(heroOf(Archetype.ROGUE), 1, listOf(Enemy(MonsterType.GOBLIN, 100, 5, 1, 1)), ambush = false, rng = Random(1))
        val e = c.enemies[0]
        assertFalse(c.isExposed(e))
        e.hp = 20
        assertTrue(c.isExposed(e))
    }

    @Test
    fun laRechargeDuSpecialSeGarde() {
        val hero = heroOf(Archetype.WARRIOR)
        Combat(hero, 1, listOf(Enemy(MonsterType.GOBLIN, 100, 5, 1, 1)), ambush = false, rng = Random(1)).guard()
        val next = Combat(hero, 1, listOf(Enemy(MonsterType.GOBLIN, 100, 5, 1, 1)), ambush = false, rng = Random(2))
        assertFalse(next.canUseSpecial())
        hero.tickRelics(Hero.SPECIAL_COOLDOWN)
        assertTrue(next.canUseSpecial())
    }
}
