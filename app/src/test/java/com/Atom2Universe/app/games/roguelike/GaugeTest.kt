package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * La jauge, façon CTB de FFX (voir [Combat] et DONJON.md, « La jauge »). À vitesse normale et
 * actions pleines, elle doit rejouer **exactement** l'ancien compte à rebours ; seules les
 * durées changent : elles se comptent aux tours de la victime.
 */
class GaugeTest {

    /**
     * Un groupe de trois (cadences 3, 4 et 5), increvable, et un héros increvable. Les monstres
     * sont ceux de l'étage 1 : à vitesse normale, comme le héros de départ.
     */
    private fun sturdyGroup(vararg types: MonsterType = arrayOf(MonsterType.RAT, MonsterType.SKELETON, MonsterType.DEMON), ambush: Boolean = false): Combat {
        val enemies = Encounters.build(types.toList(), 1).onEach { it.hp = 1_000_000 }
        val hero = heroWithAllSlots().apply { hp = 1_000_000 }
        return Combat(hero, 5, enemies, ambush, rng = Random(3), d20 = { 10 }, attackDie = { 1 })
    }

    /** Qui a agi entre chaque tour du héros, sur [rounds] tours. */
    private fun schedule(c: Combat, rounds: Int): List<List<Int>> = List(rounds) {
        c.attack(c.aliveIndices().first(), Timing.MISS)
        c.passEnemyTurns().attackers
    }

    @Test
    fun laJaugeRejoueLAncienCompteARebours() {
        val c = sturdyGroup()
        // L'ancien moteur : l'ennemi i frappe au tour r si r = compte à rebours de départ + k × cadence
        val start = c.enemies.indices.map { i -> c.enemies[i].cadence - (i % c.enemies[i].cadence) }
        val expected = List(40) { r0 ->
            val r = r0 + 1
            c.enemies.indices.filter { i -> r >= start[i] && (r - start[i]) % c.enemies[i].cadence == 0 }
        }
        assertEquals(expected, schedule(c, 40))
    }

    @Test
    fun laBarreDOrdrePreditLaSuiteDesTours() {
        val c = sturdyGroup()
        val predicted = c.forecast(30)
        // Tour par tour, qui a vraiment la main
        val c2 = sturdyGroup()
        val actual = mutableListOf<Int>()
        c2.attack(0, Timing.MISS)
        while (actual.size < 30) {
            if (c2.phase == CombatPhase.PLAYER_TURN) { actual += Combat.HERO; c2.attack(0, Timing.MISS) }
            else {
                actual += c2.actingEnemy
                c2.startEnemyTurn().attackers.forEach { c2.resolveStrike(it, Timing.MISS) }
                c2.endEnemyTurn()
            }
        }
        assertEquals(predicted.map { it.actor }, actual)
    }

    @Test
    fun enEmbuscadeLesPlusRapidesFrappentAvantLeHeros() {
        val c = sturdyGroup(MonsterType.RAT, ambush = true)
        assertEquals(CombatPhase.ENEMY_TURN, c.phase)
        assertEquals(listOf(0), c.passEnemyTurns().attackers)
        assertEquals(CombatPhase.PLAYER_TURN, c.phase)
    }

    // ── Les durées aux tours de la victime ──────────────────────────────────────

    /** Un gobelin lent (cadence 2) : il agit un tour du héros sur deux. */
    private fun slowFoe(vararg relics: Relic, d20: Int = 1): Combat {
        val hero = heroWithAllSlots().apply { relics.forEach { addRelic(it) }; hp = 1_000_000 }
        val foe = Enemy(MonsterType.GOBLIN, maxHp = 1_000_000, damage = 3, cadence = 2, countdown = 1)
        return Combat(hero, 1, listOf(foe), ambush = false, rng = Random(1), d20 = { d20 }, attackDie = { 1 })
    }

    @Test
    fun laBrulureRongeAuTourDeLaVictime() {
        val c = slowFoe(Relic.FIREBALL)
        c.castRelic(Relic.FIREBALL, 0, Timing.MISS)
        val e = c.enemies[0]
        assertEquals(1, c.passEnemyTurns().ticks.size)            // son tour : la brûlure ronge
        c.attack(0, Timing.MISS)
        assertTrue("pas son tour : rien ne ronge", c.passEnemyTurns().ticks.isEmpty())
        assertEquals("il brûle encore, deux tours du héros plus tard", 1, e.burnTurns)
        c.attack(0, Timing.MISS)
        assertEquals(1, c.passEnemyTurns().ticks.size)
        assertEquals(0, e.burnTurns)
    }

    @Test
    fun leGelRalentitTresFort() {
        val c = slowFoe(Relic.ICE_SHARD)                        // il devait jouer à 0,5, puis tous les 2 tours
        c.castRelic(Relic.ICE_SHARD, 0, Timing.MISS)
        assertTrue("sa jauge est au ralenti : il ne joue pas encore", c.passEnemyTurns().attackers.isEmpty())
        assertTrue(c.enemies[0].frozen)
        // Un tour et demi à ×0,25 : il joue à 1,625 au lieu de 0,5
        assertEquals(0.625, c.timeUntilTurn(0), 1e-9)
        c.attack(0, Timing.MISS)
        assertEquals(listOf(0), c.passEnemyTurns().attackers)
        assertFalse("la glace a fondu à la fin de son tour", c.enemies[0].frozen)
    }

    @Test
    fun gelIlFrappeEngourdi() {
        fun blow(frozen: Boolean): Int {
            val brute = Enemy(MonsterType.GOBLIN, maxHp = 1_000_000, damage = 100, cadence = 1, countdown = 1)
            val hero = heroWithAllSlots().apply { hp = 1_000_000 }
            val c = Combat(hero, 1, listOf(brute), ambush = false, rng = Random(1), attackDie = { 15 })
            c.attack(0, Timing.MISS)
            if (frozen) brute.frozenTime = 1.0
            c.startEnemyTurn()
            return c.resolveStrike(0, Timing.MISS).damage
        }
        val normal = blow(false)
        assertTrue("engourdi : ${blow(true)} contre $normal", blow(true) <= normal * Combat.NUMB_MULT + 1)
    }

    @Test
    fun lAffaiblissementCouvreSesProchainesAttaques() {
        val c = slowFoe(Relic.WAR_CRY)
        c.castRelic(Relic.WAR_CRY, 0, Timing.MISS)
        val e = c.enemies[0]
        repeat(2) {
            assertEquals(listOf(0), c.passEnemyTurns().attackers)
            c.attack(0, Timing.MISS)
            c.passEnemyTurns()                                    // pas son tour
            c.attack(0, Timing.MISS)
        }
        assertEquals("deux de ses tours : c'est fini", 0, e.weakenedTurns)
    }

    @Test
    fun lesRechargesSeComptentAuxToursDuHeros() {
        val c = slowFoe(Relic.FIREBALL)
        c.castRelic(Relic.FIREBALL, 0, Timing.MISS)
        val ready = List(Relic.FIREBALL.cooldown) {
            c.passEnemyTurns()
            c.canCast(Relic.FIREBALL).also { c.attack(0, Timing.MISS) }
        }
        // L'ennemi ne joue qu'un tour sur deux : la recharge avance quand même à chaque tour du héros
        assertEquals(List(Relic.FIREBALL.cooldown - 1) { false } + true, ready)
    }

    // ── Les coûts ───────────────────────────────────────────────────────────────

    @Test
    fun deuxSortsDeSoutienValentUnTour() {
        // Un rat qui joue à chaque tour : deux sorts à demi-jauge, et il n'a frappé qu'une fois
        val hero = heroWithAllSlots().apply { addRelic(Relic.WAR_CRY); addRelic(Relic.HOURGLASS); hp = 1_000_000 }
        val rat = Enemy(MonsterType.RAT, maxHp = 1_000_000, damage = 3, cadence = 1, countdown = 1)
        val c = Combat(hero, 1, listOf(rat), ambush = false, rng = Random(1), attackDie = { 1 })
        assertEquals(Combat.SUPPORT_ACTION, c.relicCost(Relic.WAR_CRY), 0.0)
        assertEquals(Combat.FULL_ACTION, c.relicCost(Relic.FIREBALL), 0.0)
        c.castRelic(Relic.WAR_CRY, 0, Timing.MISS)
        // À égalité (le rat et le héros à demi-tour), le rat passe d'abord : il attendait
        assertEquals(listOf(0), c.passEnemyTurns().attackers)
        c.castRelic(Relic.HOURGLASS, 0, Timing.MISS)
        assertEquals("le héros rejoue avant le 2e coup du rat", CombatPhase.PLAYER_TURN, c.phase)
    }

    @Test
    fun laBarreMontreOuTomberaitLeProchainTour() {
        val c = sturdyGroup(MonsterType.RAT)                      // un rat qui joue à mi-tour
        assertEquals(listOf(0, Combat.HERO, 0, Combat.HERO), c.forecast(4, Combat.FULL_ACTION).map { it.actor })
        // Une action à un quart de jauge : le héros rejouerait avant le rat
        assertEquals(listOf(Combat.HERO, 0, Combat.HERO, 0), c.forecast(4, 0.25).map { it.actor })
    }

    // ── La vitesse ──────────────────────────────────────────────────────────────

    @Test
    fun lesMonstresAccelerentAvecLEtage() {
        assertEquals(1.0, Encounters.speedMult(1), 1e-9)
        assertEquals(1.396, Encounters.speedMult(100), 1e-9)
        val deep = Encounters.build(listOf(MonsterType.RAT), 100).single()
        assertEquals(Encounters.speedMult(100), deep.rate, 1e-9)
    }

    @Test
    fun lArmureLegereAccelereLaLourdeRalentit() {
        fun hero(weight: ArmorWeight) = heroWithAllSlots().apply {
            for (b in listOf(ItemBase.HELMET, ItemBase.ARMOR, ItemBase.BOOTS))
                equipped[b.slot] = LootSystem.create(b, 1, Rarity.NORMAL, 0, Random(1), forcedWeight = weight)
        }
        assertEquals(1.15f, hero(ArmorWeight.LIGHT).speed, 1e-5f)
        assertEquals(1.00f, hero(ArmorWeight.CLOTH).speed, 1e-5f)
        assertEquals(0.85f, hero(ArmorWeight.HEAVY).speed, 1e-5f)
    }

    @Test
    fun unHerosRapideRejoueAvantUnMonstreLent() {
        // Deux fois plus rapide qu'un gobelin de cadence 2 : quatre tours du héros pour un du gobelin
        val hero = heroWithAllSlots().apply {
            equipped[EquipSlot.RING] = LootSystem.create(ItemBase.RING, 1, Rarity.NORMAL, 0, Random(1))
                .let { it.copy(implicits = listOf(StatRoll(StatType.SPEED, 1f)), affixes = emptyList()) }
            hp = 1_000_000
        }
        assertEquals(2f, hero.speed, 1e-5f)
        val foe = Enemy(MonsterType.GOBLIN, maxHp = 1_000_000, damage = 3, cadence = 2, countdown = 2)
        val c = Combat(hero, 1, listOf(foe), ambush = false, rng = Random(1), attackDie = { 1 })
        assertEquals(listOf(Combat.HERO, Combat.HERO, 0, Combat.HERO), c.forecast(4).map { it.actor })
        assertEquals("le gobelin joue dans 3 tours du héros", 3, c.roundsUntilTurn(0))
    }

    // ── Étape 5 : le contrôle agit sur les jauges ───────────────────────────────

    @Test
    fun laHateFaitJouerPlusSouvent() {
        // Un gobelin lent (cadence 2) qui jouera à 1,5
        fun fight(relic: Relic): Combat {
            val hero = heroWithAllSlots().apply { addRelic(relic); hp = 1_000_000 }
            val foe = Enemy(MonsterType.GOBLIN, maxHp = 1_000_000, damage = 3, cadence = 2, countdown = 2)
            return Combat(hero, 1, listOf(foe), ambush = false, rng = Random(1), attackDie = { 1 })
                .also { it.castRelic(relic, 0, Timing.MISS) }
        }
        // Deux sorts à demi-jauge : sans Hâte, le gobelin passe avant le prochain tour du héros ; avec
        // (vitesse ×1,4), le héros le double
        assertEquals(listOf(0, Combat.HERO), fight(Relic.WAR_CRY).forecast(2).map { it.actor })
        val c = fight(Relic.HASTE)
        assertEquals(Relic.HASTE_SPEED, c.heroRate, 1e-9)
        assertEquals(listOf(Combat.HERO, 0), c.forecast(2).map { it.actor })
    }

    @Test
    fun laLenteurRalentitEtCompteCommeUnControle() {
        val hero = heroWithAllSlots().apply { addRelic(Relic.SLOW) }
        val rat = Enemy(MonsterType.RAT, maxHp = 1_000_000, damage = 3, cadence = 1, countdown = 1)
        val c = Combat(hero, 1, listOf(rat), ambush = false, rng = Random(1), d20 = { 1 }, attackDie = { 1 })
        val hit = c.castRelic(Relic.SLOW, 0, Timing.MISS).main!!
        assertFalse(hit.save!!.saved)
        assertTrue(rat.slowed)
        assertEquals(1, rat.controlStreak)
    }

    @Test
    fun leRalentissementDureAutantSurUnEnnemiLent() {
        // Compté en tours du héros : un chef lent n'est pas ralenti plus longtemps qu'un rat
        fun delay(cadence: Int): Double {
            val hero = heroWithAllSlots().apply { addRelic(Relic.SLOW); hp = 1_000_000 }
            val foe = Enemy(MonsterType.GOBLIN, maxHp = 1_000_000, damage = 3, cadence = cadence, countdown = cadence)
            val c = Combat(hero, 1, listOf(foe), ambush = false, rng = Random(1), d20 = { 1 }, attackDie = { 1 })
            val before = c.timeUntilTurn(0)
            foe.slowTime = Relic.SLOW.effectTurns * Relic.SLOW_TURN_LENGTH
            return c.timeUntilTurn(0) - before
        }
        val slowLength = Relic.SLOW.effectTurns * Relic.SLOW_TURN_LENGTH
        // Ralenti de moitié pendant tout ce temps : il perd la moitié de ce temps, quelle que soit sa cadence
        assertEquals(slowLength * (1 - Relic.SLOW_SPEED), delay(4), 1e-6)
        assertEquals(delay(4), delay(8), 1e-6)
    }

    @Test
    fun leSablierRalentitLesProchainesAttaques() {
        val hero = heroWithAllSlots().apply { addRelic(Relic.HOURGLASS); hp = 1_000_000 }
        val rat = Enemy(MonsterType.RAT, maxHp = 1_000_000, damage = 3, cadence = 1, countdown = 1)
        val c = Combat(hero, 1, listOf(rat), ambush = false, rng = Random(1), attackDie = { 1 })
        c.castRelic(Relic.HOURGLASS, 0, Timing.MISS)
        assertEquals(Relic.HOURGLASS.effectTurns, c.hourglassStrikes)
        c.passEnemyTurns()
        assertEquals("une attaque est passée au ralenti", Relic.HOURGLASS.effectTurns - 1, c.hourglassStrikes)
    }
}
