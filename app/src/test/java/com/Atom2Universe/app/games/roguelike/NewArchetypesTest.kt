package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Le vagabond (poids intermédiaire) et le nécromancien (poids super léger) : armure, Spéciaux, sets. */
class NewArchetypesTest {

    private fun piece(base: ItemBase, weight: ArmorWeight?) =
        LootSystem.create(base, 1, Rarity.NORMAL, 0, Random(0), forcedWeight = weight)

    private fun heroOf(a: Archetype) = Hero.starter().apply {
        equipped[EquipSlot.HELMET] = piece(ItemBase.HELMET, a.weight)
        equipped[EquipSlot.CHEST] = piece(ItemBase.ARMOR, a.weight)
        equipped[EquipSlot.BOOTS] = piece(ItemBase.BOOTS, a.weight)
    }

    private fun setHero(z: Int) = Hero.starter().apply {
        val set = IsotopeSets.of(z)!!
        for ((i, base) in IsotopeSets.BASES.withIndex()) {
            val p = LootSystem.createSetPiece(set, base, 0, Random(i)); equipped[p.slot] = p
        }
    }

    /** Un gobelin solide dont le d20 d'attaque sort 20 (il touche toujours). [ambush] : il frappe avant le héros. */
    private fun fight(hero: Hero, hp: Int = 100000, damage: Int = 10, ambush: Boolean = false) =
        Combat(hero, 1, listOf(Enemy(MonsterType.GOBLIN, hp, damage, 1, 1)), ambush = ambush, rng = Random(1), attackDie = { 20 })

    // ── L'armure ────────────────────────────────────────────────────────────────

    @Test
    fun lesCinqPoidsGardentLaMoyenneDArmureEtDeVitesse() {
        val n = ArmorWeight.entries.size
        assertEquals(5, n)
        assertEquals(1f, ArmorWeight.entries.sumOf { it.armorMult.toDouble() }.toFloat() / n, 0.0001f)
        assertEquals(0f, ArmorWeight.entries.sumOf { it.speedPerPiece.toDouble() }.toFloat() / n, 0.0001f)
    }

    @Test
    fun chaqueArchetypeVientDeSonPoids() {
        for (a in Archetype.entries) assertEquals(a, heroOf(a).archetype)
    }

    @Test
    fun laDexterieNeDepassePasSonPlafondEnIntermediaire() {
        fun ac(weight: ArmorWeight, dex: Float): Int {
            val hero = heroOf(Archetype.entries.first { it.weight == weight })
            hero.equipped[EquipSlot.HELMET] = hero.equipped.getValue(EquipSlot.HELMET).let { it.copy(implicits = it.implicits + StatRoll(StatType.DEX, dex)) }
            return hero.armorClass
        }
        assertTrue("intermédiaire : au plus +${ArmorWeight.MEDIUM.dexCap}", ac(ArmorWeight.MEDIUM, 60f) - ac(ArmorWeight.MEDIUM, 0f) <= ArmorWeight.MEDIUM.dexCap)
        assertTrue("léger : plus", ac(ArmorWeight.LIGHT, 60f) - ac(ArmorWeight.LIGHT, 0f) > ArmorWeight.MEDIUM.dexCap)
        assertEquals("lourd : rien", ac(ArmorWeight.HEAVY, 0f), ac(ArmorWeight.HEAVY, 60f))
    }

    // ── Le vagabond ─────────────────────────────────────────────────────────────

    @Test
    fun lEnchainementFrappeDeuxFois() {
        val c = fight(heroOf(Archetype.VAGABOND))
        val hits = c.chain(0, Timing.MISS, Timing.MISS)
        assertEquals(Combat.CHAIN_HITS, hits.size)
        assertTrue(c.hero.specialCooldown > 0)
    }

    @Test
    fun leSecondCoupChercheUneAutreCibleSiLePremierTue() {
        val hero = heroOf(Archetype.VAGABOND)
        val c = Combat(hero, 1, listOf(Enemy(MonsterType.GOBLIN, 1, 10, 1, 1), Enemy(MonsterType.GOBLIN, 100000, 10, 1, 1)),
            ambush = false, rng = Random(1), attackDie = { 20 })
        val hits = c.chain(0, Timing.PERFECT, Timing.PERFECT)
        assertTrue(hits[0].killed)
        assertEquals("le second coup va au survivant", 1, hits[1].target)
    }

    @Test
    fun laRouladeParfaiteEviteLeCoupEtRelevePlusTardLeProchainCoup() {
        val hero = heroOf(Archetype.VAGABOND)
        val c = fight(hero, ambush = true)
        c.startEnemyTurn()
        val s = c.resolveStrike(0, Timing.PERFECT)
        assertTrue(s.dodged)
        assertEquals(0, s.damage)
        assertTrue("pas de riposte gratuite", s.counter == null)
        c.endEnemyTurn()
        // Le coup suivant est relevé de ROLL_BONUS : au moins la moitié de plus que le plus petit coup d'arme
        val hit = c.attack(0, Timing.MISS)
        assertTrue("${hit.damage} < ${hero.weaponMin}", hit.damage >= (hero.weaponMin * (1f + Combat.ROLL_BONUS)).toInt() - 1)
    }

    @Test
    fun leSetDuVagabondRenforceLEnchainementEtLesCritiques() {
        val hero = setHero(4)
        assertEquals(Archetype.VAGABOND, hero.setArchetype)
        val gear = hero.equipped.values.sumOf { it.sum(StatType.CRIT_DAMAGE).toDouble() }.toFloat()
        assertEquals(Hero.BASE_CRIT_MULT + gear + IsotopeSets.CRIT_DAMAGE_BONUS, hero.critMult, 0.001f)
        val c = fight(hero)
        c.chain(0, Timing.MISS, Timing.MISS)
        assertEquals(hero.spellCooldown(IsotopeSets.SPECIAL_COOLDOWN), hero.specialCooldown)
    }

    // ── Le nécromancien ─────────────────────────────────────────────────────────

    @Test
    fun ilCommenceAvecDeuxPantinsEtTroisAvecLeSet() {
        val plain = fight(heroOf(Archetype.NECROMANCER))
        assertEquals(Combat.PUPPETS, plain.puppetHp.size)
        assertTrue(plain.puppetHp.all { it == plain.puppetMaxHp })
        assertEquals(IsotopeSets.PUPPETS, fight(setHero(5)).puppetHp.size)
        assertTrue("les autres n'en ont pas", fight(heroOf(Archetype.MAGE)).puppetHp.isEmpty())
    }

    @Test
    fun lesPantinsEncaissentLaPlusGrosseParteDuCoup() {
        val c = fight(heroOf(Archetype.NECROMANCER), damage = 8, ambush = true)
        val before = c.puppetHp.sum()
        c.startEnemyTurn()
        val s = c.resolveStrike(0, Timing.MISS)
        val lost = before - c.puppetHp.sum()
        assertTrue(s.damage > 0 && lost > 0)
        val heroShare = s.damage.toFloat() / (s.damage + lost)
        assertEquals(Combat.PUPPET_SELF_SHARE, heroShare, 0.12f)
    }

    @Test
    fun lEchoDependDuGeste() {
        fun loss(timing: Timing): Pair<Int, Int> {
            val c = fight(heroOf(Archetype.NECROMANCER))
            val before = c.enemies[0].hp
            val hit = c.attack(0, timing)
            return (before - c.enemies[0].hp) to hit.damage
        }
        val (perfectLoss, perfectHit) = loss(Timing.PERFECT)
        val (goodLoss, goodHit) = loss(Timing.GOOD)
        val (missLoss, missHit) = loss(Timing.MISS)
        assertEquals("un raté : aucun écho", missHit, missLoss)
        val perfectEcho = perfectLoss - perfectHit
        val goodEcho = goodLoss - goodHit
        assertTrue("parfait : un écho", perfectEcho > 0)
        assertTrue("bon : un écho, pas plus que le parfait", goodEcho in 1..perfectEcho)
    }

    @Test
    fun leRappelRelevePantinsTombesEtFrappe() {
        val c = fight(heroOf(Archetype.NECROMANCER), damage = 20, ambush = true)
        c.startEnemyTurn()
        c.resolveStrike(0, Timing.MISS)
        c.endEnemyTurn()
        assertTrue("des pantins sont tombés", c.puppetHp.any { it < c.puppetMaxHp })
        assertEquals(CombatPhase.PLAYER_TURN, c.phase)
        val before = c.enemies[0].hp
        c.recallPuppets()
        assertTrue(c.puppetHp.all { it == c.puppetMaxHp })
        assertTrue("la salve blesse", c.enemies[0].hp < before)
    }

    @Test
    fun leSetDuNecromancienDonneUnPantinEtDesRechargesPlusCourtes() {
        val hero = setHero(5)
        assertEquals(Archetype.NECROMANCER, hero.setArchetype)
        val withSet = hero.spellCooldown(6)
        for (slot in IsotopeSets.SLOTS) hero.equipped[slot] = hero.equipped.getValue(slot).copy(isotopeZ = null)
        assertEquals(hero.spellCooldown(6) - IsotopeSets.RECHARGE_CUT, withSet)
    }
}
