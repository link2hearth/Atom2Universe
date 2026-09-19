package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Le grimoire : les états partagés, les réactions et les résonances (voir DONJON.md,
 * « Le grimoire »). Comme dans [RelicTest], le d20 des jets est truqué : 1, le monstre rate
 * toujours son jet ; 20, il le réussit toujours.
 *
 * Les monstres sont des gobelins très solides qui frappent à chaque tour : le gobelin est
 * normal au feu, à la foudre et au physique, résistant à la glace, vulnérable au poison.
 */
class GrimoireTest {

    private fun fight(
        vararg relics: Relic, enemies: Int = 1, type: MonsterType = MonsterType.GOBLIN, d20: Int = 1,
        attackDie: () -> Int = { 15 },
    ): Combat {
        val hero = heroWithAllSlots().apply { relics.forEach { addRelic(it) } }
        val foes = List(enemies) { Enemy(type, maxHp = 1000, damage = 3, cadence = 1, countdown = 1) }
        return Combat(hero, 1, foes, ambush = false, rng = Random(1), d20 = { d20 }, attackDie = attackDie)
    }

    /** Un tour ennemi complet (parade ratée), puis la main revient au joueur. */
    private fun enemyTurn(c: Combat): EnemyTurnStart = c.passEnemyTurns(Timing.MISS)

    /** Lance [relic] puis laisse passer le tour ennemi, recharges remises à zéro. */
    private fun castAndPass(c: Combat, relic: Relic, target: Int = 0): CastResult {
        c.relicCooldowns.clear()
        val cast = c.castRelic(relic, target, Timing.MISS)
        enemyTurn(c)
        return cast
    }

    // ── Le gel laisse un tour pour le briser ────────────────────────────────────

    @Test
    fun unGelDUnTourLaisseUneCibleFigeePourLeHeros() {
        val c = fight(Relic.ICE_SHARD)
        castAndPass(c, Relic.ICE_SHARD)
        val e = c.enemies[0]
        assertEquals("un tour et demi de gel, un tour du héros est passé", 0.5, e.frozenTime, 1e-9)
        assertTrue("mais la glace est encore là pendant le tour du héros", e.frozen)
        assertTrue("exposée au Coup mortel", c.isExposed(e))
        c.attack(0, Timing.MISS)
        assertEquals("elle frappe à son tour", listOf(0), c.startEnemyTurn().attackers)
        assertTrue("encore engourdie pendant ce coup", e.frozen)
        c.endEnemyTurn()
        assertFalse("la glace fond à la fin de son tour", e.frozen)
    }

    @Test
    fun lAttaqueDeBaseNeBrisePasLeGel() {
        val c = fight(Relic.ICE_SHARD)
        castAndPass(c, Relic.ICE_SHARD)
        c.attack(0, Timing.MISS)
        assertTrue(c.enemies[0].frozen)
    }

    // ── Réactions ───────────────────────────────────────────────────────────────

    @Test
    fun unSortPhysiqueBriseLeGel() {
        val c = fight(Relic.ICE_SHARD, Relic.EARTHQUAKE, d20 = 20)   // 20 : l'Avalanche ne refige pas
        c.enemies[0].frozenTime = 1.0
        val hit = c.castRelic(Relic.EARTHQUAKE, 0, Timing.MISS).main!!
        assertEquals(listOf(Reaction.SHATTER), hit.reactions)
        val (lo, _) = c.hero.relicDamage(Relic.EARTHQUAKE)
        assertTrue("×2 (${hit.damage} contre au moins $lo)", hit.damage >= (lo * Reaction.SHATTER_MULT).toInt())
        assertFalse(c.enemies[0].frozen)
    }

    @Test
    fun leFeuFaitFondreLeGelEtBruleQuandMeme() {
        val c = fight(Relic.FIREBALL)
        c.enemies[0].frozenTime = 1.0
        val hit = c.castRelic(Relic.FIREBALL, 0, Timing.MISS).main!!
        assertEquals(listOf(Reaction.MELT), hit.reactions)
        assertFalse(c.enemies[0].frozen)
        assertTrue(c.enemies[0].burnTurns > 0)
    }

    @Test
    fun leFeuSurUnTrempeFaitDeLaVapeurQuiAveugleTout() {
        val c = fight(Relic.FREEZING_RAIN, Relic.FIREBALL, enemies = 2)
        castAndPass(c, Relic.FREEZING_RAIN)
        val hit = c.castRelic(Relic.FIREBALL, 0, Timing.MISS).main!!
        assertTrue(Reaction.STEAM in hit.reactions)
        assertEquals("pas de brûlure", 0, c.enemies[0].burnTurns)
        assertEquals("l'eau s'est évaporée", 0, c.enemies[0].soakedTurns)
        assertTrue("tout le monde est aveuglé", c.enemies.all { it.blindedTurns > 0 })
    }

    @Test
    fun aveugleIlAttaqueAvecDesavantage() {
        // Les dés sortent 20 puis 1 : aveuglé, il garde le 1 et rate
        val dice = ArrayDeque(listOf(20, 1))
        val c = fight(attackDie = { dice.removeFirst() })
        c.enemies[0].blindedTurns = 1
        c.attack(0, Timing.MISS)
        val t = c.startEnemyTurn()
        assertTrue(c.resolveStrike(t.attackers.single(), Timing.MISS).missed)
        c.endEnemyTurn()
        assertEquals("l'aveuglement ne dure qu'un tour", 0, c.enemies[0].blindedTurns)
    }

    @Test
    fun laFoudreSurUnTrempeElectrocute() {
        val c = fight(Relic.FREEZING_RAIN, Relic.LIGHTNING)
        castAndPass(c, Relic.FREEZING_RAIN)
        val hit = c.castRelic(Relic.LIGHTNING, 0, Timing.MISS).main!!
        assertEquals(listOf(Reaction.ELECTROCUTION), hit.reactions)
        assertEquals("ses jets de paralysie se feront au désavantage", Timing.PERFECT, c.enemies[0].paralysisTiming)
        assertTrue("l'eau reste", c.enemies[0].soakedTurns > 0)
    }

    @Test
    fun leGelSurUnTrempeDureUnTourDePlus() {
        val c = fight(Relic.FREEZING_RAIN, Relic.ICE_SHARD)
        castAndPass(c, Relic.FREEZING_RAIN)
        val hit = c.castRelic(Relic.ICE_SHARD, 0, Timing.MISS).main!!
        assertEquals(listOf(Reaction.FROST), hit.reactions)
        // Deux tours de gel ; le premier tour du héros est déjà passé depuis le lancer
        assertEquals((Relic.ICE_SHARD.effectTurns + Reaction.FROST_EXTRA_TURNS) * Relic.FREEZE_TURN_LENGTH - 1, c.enemies[0].frozenTime, 1e-9)
        assertEquals("l'eau a gelé", 0, c.enemies[0].soakedTurns)
    }

    @Test
    fun trempeIlResisteMoins() {
        val c = fight(Relic.FREEZING_RAIN, Relic.ICE_SHARD, d20 = 10)
        castAndPass(c, Relic.FREEZING_RAIN)
        val save = c.castRelic(Relic.ICE_SHARD, 0, Timing.MISS).main!!.save!!
        assertEquals(10 + SpellSave.monsterProficiency(1) + MonsterType.GOBLIN.affinity(Element.ICE).saveBonus -
            Combat.SOAKED_SAVE_MALUS, save.total)
    }

    @Test
    fun lEauEteintLeFeu() {
        val c = fight(Relic.FIREBALL, Relic.FREEZING_RAIN)
        castAndPass(c, Relic.FIREBALL)
        assertTrue(c.enemies[0].burnTurns > 0)
        c.relicCooldowns.clear()
        c.castRelic(Relic.FREEZING_RAIN, 0, Timing.MISS)
        assertEquals(0, c.enemies[0].burnTurns)
    }

    @Test
    fun leFeuFaitExploserLePoison() {
        val c = fight(Relic.VENOM, Relic.FIREBALL)
        castAndPass(c, Relic.VENOM)
        val e = c.enemies[0]
        val expected = e.poisonDoses * e.poisonDoseDamage * e.poisonTurns
        assertTrue(expected > 0)
        val hit = c.castRelic(Relic.FIREBALL, 0, Timing.MISS).main!!
        assertTrue(Reaction.EXPLOSION in hit.reactions)
        assertEquals(expected, hit.explosion)
        assertEquals("le poison est consommé", 0, e.poisonDoses)
    }

    @Test
    fun unImmuniseNeReagitPas() {
        // Le démon est immunisé au feu : pas de Fonte, le gel tient
        val c = fight(Relic.FIREBALL, type = MonsterType.DEMON)
        c.enemies[0].frozenTime = 1.0
        assertTrue(c.castRelic(Relic.FIREBALL, 0, Timing.MISS).main!!.reactions.isEmpty())
        assertTrue(c.enemies[0].frozen)
    }

    // ── Les nouveaux états ──────────────────────────────────────────────────────

    @Test
    fun unFractureEncaissePlus() {
        fun damageOn(fractured: Boolean): Int {
            val c = fight()
            if (fractured) c.enemies[0].fracturedTurns = 3
            return c.attack(0, Timing.MISS).damage
        }
        val normal = damageOn(false)
        assertEquals(Math.round(normal * Combat.FRACTURE_MULT), damageOn(true))
    }

    @Test
    fun leBriseArmureFracture() {
        val c = fight(Relic.SUNDER)
        c.castRelic(Relic.SUNDER, 0, Timing.MISS)
        assertEquals(Relic.SUNDER.effectTurns, c.enemies[0].fracturedTurns)
        enemyTurn(c)
        assertEquals("un tour de moins à chaque fin de tour ennemi", Relic.SUNDER.effectTurns - 1, c.enemies[0].fracturedTurns)
    }

    @Test
    fun leCriAffaiblitToutLeMondeEtRenforceLArme() {
        val c = fight(Relic.WAR_CRY, enemies = 3)
        val cast = c.castRelic(Relic.WAR_CRY, 0, Timing.MISS)
        assertEquals(3, cast.hits.size)
        assertTrue(cast.hits.all { it.noDamage })
        assertTrue(c.enemies.all { it.weakenedTurns == Relic.WAR_CRY.effectTurns })
        assertEquals(Relic.WARCRY_ATTACKS, c.empoweredAttacks)
        enemyTurn(c)
        c.attack(0, Timing.MISS)
        assertEquals(Relic.WARCRY_ATTACKS - 1, c.empoweredAttacks)
    }

    @Test
    fun unAffaibliFrappeMoinsFort() {
        fun strikeFrom(weakened: Boolean): Int {
            // Un gros cogneur : avec 3 de dégâts, l'arrondi mangerait la différence
            val brute = Enemy(MonsterType.GOBLIN, maxHp = 1000, damage = 100, cadence = 1, countdown = 1)
            val c = Combat(heroWithAllSlots(), 1, listOf(brute), ambush = false, rng = Random(1), attackDie = { 15 })
            if (weakened) brute.weakenedTurns = 2
            c.attack(0, Timing.MISS)
            val t = c.startEnemyTurn()
            return c.resolveStrike(t.attackers.single(), Timing.MISS).damage
        }
        assertTrue(strikeFrom(true) < strikeFrom(false))
    }

    @Test
    fun laMarqueExposeEtSauteSurLeSuivant() {
        val c = fight(Relic.HUNTERS_MARK, enemies = 2)
        c.castRelic(Relic.HUNTERS_MARK, 0, Timing.MISS)
        assertTrue(c.enemies[0].marked)
        assertTrue(c.isExposed(c.enemies[0]))
        enemyTurn(c)
        c.enemies[0].hp = 1
        assertTrue(c.attack(0, Timing.MISS).killed)
        assertTrue("la marque a sauté", c.enemies[1].marked)
    }

    @Test
    fun uneSeuleMarqueALaFois() {
        val c = fight(Relic.HUNTERS_MARK, enemies = 2)
        castAndPass(c, Relic.HUNTERS_MARK, target = 0)
        c.relicCooldowns.clear()
        c.castRelic(Relic.HUNTERS_MARK, 1, Timing.MISS)
        assertFalse(c.enemies[0].marked)
        assertTrue(c.enemies[1].marked)
    }

    @Test
    fun lesLamesEmpoisonnentLesProchainsCoups() {
        val c = fight(Relic.POISONED_BLADES)
        val cast = c.castRelic(Relic.POISONED_BLADES, 0, Timing.MISS)
        assertTrue("un sort sur soi ne touche personne", cast.hits.isEmpty())
        assertEquals(Relic.POISONED_BLADES.effectTurns, c.poisonedBlades)
        enemyTurn(c)
        c.attack(0, Timing.MISS)
        assertEquals(1, c.enemies[0].poisonDoses)
        assertEquals(Relic.POISONED_BLADES.effectTurns - 1, c.poisonedBlades)
    }

    @Test
    fun lesLamesNeMordentPasUnImmunise() {
        val c = fight(Relic.POISONED_BLADES, type = MonsterType.SKELETON)
        castAndPass(c, Relic.POISONED_BLADES)
        c.attack(0, Timing.MISS)
        assertEquals(0, c.enemies[0].poisonDoses)
    }

    @Test
    fun laChaineToucheToutLeMondeCibleEnPremier() {
        val c = fight(Relic.CHAIN_LIGHTNING, enemies = 3)
        val cast = c.castRelic(Relic.CHAIN_LIGHTNING, 1, Timing.MISS)
        assertEquals(listOf(1, 0, 2), cast.hits.map { it.target })
    }

    @Test
    fun laPluieTrempeToutLeMonde() {
        val c = fight(Relic.FREEZING_RAIN, enemies = 3)
        c.castRelic(Relic.FREEZING_RAIN, 0, Timing.MISS)
        assertTrue(c.enemies.all { it.soakedTurns == Relic.FREEZING_RAIN.effectTurns })
    }

    // ── Résonances ──────────────────────────────────────────────────────────────

    @Test
    fun uneResonanceDonneSaCaracEtSeDecouvreUneFois() {
        val hero = heroWithAllSlots()
        val int = hero.attribute(StatType.INT)
        hero.addRelic(Relic.FIREBALL)
        assertTrue(hero.resonances.isEmpty())
        hero.addRelic(Relic.VENOM)
        assertEquals(listOf(Resonance.ALCHEMY), hero.resonances)
        assertEquals(int + Resonance.BONUS, hero.attribute(StatType.INT))
        assertEquals(listOf(Resonance.ALCHEMY), hero.discoverResonances())
        assertTrue("déjà dans le carnet", hero.discoverResonances().isEmpty())
        hero.toggleRelic(Relic.VENOM)
        assertTrue(hero.resonances.isEmpty())
        assertEquals(int, hero.attribute(StatType.INT))
        assertTrue("le carnet s'en souvient", Resonance.ALCHEMY in hero.knownResonances)
    }

    @Test
    fun alchimieLExplosionEmpoisonneLesAutres() {
        val c = fight(Relic.VENOM, Relic.FIREBALL, enemies = 2)
        castAndPass(c, Relic.VENOM, target = 0)
        assertEquals(0, c.enemies[1].poisonDoses)
        c.castRelic(Relic.FIREBALL, 0, Timing.MISS)
        assertEquals(1, c.enemies[1].poisonDoses)
    }

    @Test
    fun tempeteDeFeuLaVapeurAveugleUnTourDePlus() {
        val c = fight(Relic.FREEZING_RAIN, Relic.FIREBALL)
        castAndPass(c, Relic.FREEZING_RAIN)
        c.castRelic(Relic.FIREBALL, 0, Timing.MISS)
        assertEquals(Reaction.STEAM_BLIND_TURNS + 1, c.enemies[0].blindedTurns)
    }

    @Test
    fun orageLaChaineParalyseLesTrempes() {
        val c = fight(Relic.FREEZING_RAIN, Relic.CHAIN_LIGHTNING, enemies = 2)
        castAndPass(c, Relic.FREEZING_RAIN)
        val cast = c.castRelic(Relic.CHAIN_LIGHTNING, 0, Timing.MISS)
        assertTrue(cast.hits.all { Reaction.ELECTROCUTION in it.reactions })
        assertTrue(c.enemies.all { it.paralyzedTurns == 1 })
    }

    @Test
    fun sansOrageLaChaineNeParalysePas() {
        val c = fight(Relic.FREEZING_RAIN, Relic.CHAIN_LIGHTNING, enemies = 2)
        c.hero.toggleRelic(Relic.FREEZING_RAIN)                 // plus de paire
        c.enemies.forEach { it.soakedTurns = 2 }
        c.castRelic(Relic.CHAIN_LIGHTNING, 0, Timing.MISS)
        assertTrue(c.enemies.all { it.paralyzedTurns == 0 })
    }

    @Test
    fun avalancheLeSeismeFige() {
        val c = fight(Relic.ICE_SHARD, Relic.EARTHQUAKE, enemies = 2)
        c.castRelic(Relic.EARTHQUAKE, 0, Timing.MISS)
        assertTrue(c.enemies.all { it.frozen })
    }

    @Test
    fun chargeDuBelierLeBriseArmureFrappeLesAffaiblis() {
        val c = fight(Relic.WAR_CRY, Relic.SUNDER, enemies = 3)
        castAndPass(c, Relic.WAR_CRY)
        val cast = c.castRelic(Relic.SUNDER, 1, Timing.MISS)
        assertEquals(listOf(1, 0, 2), cast.hits.map { it.target })
        assertTrue(c.enemies.all { it.fracturedTurns > 0 })
    }

    // ── Lot 2 : saignement ──────────────────────────────────────────────────────

    /** L'ennemi 0 attaque (le héros vient d'agir) : on renvoie son coup. */
    private fun strikeOf(c: Combat, parry: Timing = Timing.MISS): EnemyStrike {
        val t = c.startEnemyTurn()
        return c.resolveStrike(t.attackers.first(), parry)
    }

    @Test
    fun laSaigneeRongeAChaqueAttaque() {
        val c = fight(Relic.REND)
        c.castRelic(Relic.REND, 0, Timing.MISS)
        val e = c.enemies[0]
        assertEquals(Relic.REND.effectTurns, e.bleedTurns)
        val hp = e.hp
        val strike = strikeOf(c)
        assertEquals(c.hero.bleedDamage(Relic.REND), strike.bleed)
        assertEquals(hp - strike.bleed, e.hp)
    }

    @Test
    fun ilPeutSaignerAMortEtLeCoupNePartPas() {
        val c = fight(Relic.REND)
        c.castRelic(Relic.REND, 0, Timing.MISS)
        c.enemies[0].hp = 1
        val hp = c.hero.hp
        val strike = strikeOf(c)
        assertTrue(strike.bledOut)
        assertEquals(hp, c.hero.hp)
        assertEquals(CombatPhase.VICTORY, c.phase)
    }

    @Test
    fun leFeuCauteriseLaPlaie() {
        val c = fight(Relic.REND, Relic.FIREBALL)
        castAndPass(c, Relic.REND)
        val hit = c.castRelic(Relic.FIREBALL, 0, Timing.MISS).main!!
        assertTrue(Reaction.CAUTERIZE in hit.reactions)
        assertEquals(0, c.enemies[0].bleedTurns)
    }

    @Test
    fun leTourbillonPorteLesLamesSurToutLeMondePourUneCharge() {
        val c = fight(Relic.POISONED_BLADES, Relic.WHIRLWIND, enemies = 3)
        castAndPass(c, Relic.POISONED_BLADES)
        c.relicCooldowns.clear()
        assertEquals(3, c.castRelic(Relic.WHIRLWIND, 0, Timing.MISS).hits.size)
        assertTrue(c.enemies.all { it.poisonDoses == 1 })
        assertEquals(Relic.POISONED_BLADES.effectTurns - 1, c.poisonedBlades)
    }

    @Test
    fun hemorragieLeTourbillonFaitSaignerToutLeMonde() {
        val c = fight(Relic.REND, Relic.WHIRLWIND, enemies = 3)
        c.castRelic(Relic.WHIRLWIND, 0, Timing.MISS)
        assertTrue(c.enemies.all { it.bleedTurns > 0 })
    }

    @Test
    fun meuteLesDaguesCritiquentLaCibleMarqueeEtLaFontSaigner() {
        val c = fight(Relic.HUNTERS_MARK, Relic.FAN_OF_KNIVES, enemies = 2)
        castAndPass(c, Relic.HUNTERS_MARK, target = 0)
        c.relicCooldowns.clear()
        val hit = c.castRelic(Relic.FAN_OF_KNIVES, 1, Timing.MISS).hits.single { it.target == 0 }
        assertTrue(hit.crit)
        assertTrue(c.enemies[0].bleedTurns > 0)
    }

    // ── Lot 2 : les ruses ───────────────────────────────────────────────────────

    @Test
    fun laBombeAveugleEtPrepareUneAttaqueSournoise() {
        val c = fight(Relic.SMOKE_BOMB, enemies = 2)
        c.castRelic(Relic.SMOKE_BOMB, 0, Timing.MISS)
        assertTrue(c.enemies.all { it.blindedTurns == Relic.SMOKE_BOMB.effectTurns })
        assertTrue(c.ambushReady)
        enemyTurn(c)
        assertTrue("attaque sournoise : critique garanti", c.attack(0, Timing.MISS).crit)
        assertFalse(c.ambushReady)
    }

    @Test
    fun laFioleEmpoisonneEtFracture() {
        val c = fight(Relic.ACID_FLASK)
        c.castRelic(Relic.ACID_FLASK, 0, Timing.MISS)
        assertEquals(1, c.enemies[0].poisonDoses)
        assertEquals(Relic.ACID_FLASK.effectTurns, c.enemies[0].fracturedTurns)
    }

    @Test
    fun corrosionCinqDosesAuLieuDeTrois() {
        fun dosesAfter(vararg relics: Relic): Int {
            val c = fight(*relics)
            c.enemies[0].apply { poisonDoses = 4; poisonTurns = 2; poisonDoseDamage = 1 }
            c.castRelic(Relic.ACID_FLASK, 0, Timing.MISS)
            return c.enemies[0].poisonDoses
        }
        assertEquals(Relic.POISON_MAX_DOSES, dosesAfter(Relic.ACID_FLASK))
        assertEquals(Combat.CORROSION_MAX_DOSES, dosesAfter(Relic.ACID_FLASK, Relic.POISONED_BLADES))
    }

    @Test
    fun leCharmeRetourneSonAttaqueContreUnAllie() {
        val c = fight(Relic.CHARM, enemies = 2)
        c.castRelic(Relic.CHARM, 0, Timing.MISS)
        assertTrue(c.enemies[0].charmed)
        val hp = c.hero.hp
        val t = c.startEnemyTurn()
        val strike = c.resolveStrike(0, Timing.MISS)
        assertTrue(0 in t.attackers)
        assertTrue(strike.charmed)
        assertEquals(1, strike.charmHit!!.target)
        assertTrue(c.enemies[1].hp < c.enemies[1].maxHp)
        assertEquals(hp, c.hero.hp)
        assertFalse("le charme se dissipe", c.enemies[0].charmed)
    }

    @Test
    fun seulLeCharmeLuiFaitPerdreSonAttaque() {
        val c = fight(Relic.CHARM)
        c.castRelic(Relic.CHARM, 0, Timing.MISS)
        val hp = c.hero.hp
        val strike = strikeOf(c)
        assertTrue(strike.charmed)
        assertEquals(null, strike.charmHit)
        assertEquals(hp, c.hero.hp)
    }

    @Test
    fun deuxCharmesDAffileeFontEnrager() {
        // L'attaque détournée ne remet pas la série à zéro : sinon le Charme bloquait sans fin
        val c = fight(Relic.CHARM)
        c.castRelic(Relic.CHARM, 0, Timing.MISS)
        assertTrue(enemyTurn(c).attackers.isNotEmpty())
        c.relicCooldowns.clear()
        assertTrue("2e charme d'affilée : il enrage", c.castRelic(Relic.CHARM, 0, Timing.MISS).main!!.enraged)
        assertFalse(c.enemies[0].charmed)
    }

    @Test
    fun leBouclierNeSeRechargePasLuiMeme() {
        val c = fight(Relic.ARCANE_SHIELD)
        c.castRelic(Relic.ARCANE_SHIELD, 0, Timing.MISS)
        val cd = c.hero.relicCooldown(Relic.ARCANE_SHIELD)
        enemyTurn(c)
        assertEquals("un seul tour de moins, le normal", cd - 1, c.hero.relicCooldown(Relic.ARCANE_SHIELD))
    }

    @Test
    fun discordeUnAveugleNeResistePasAuCharme() {
        val c = fight(Relic.CHARM, Relic.SMOKE_BOMB, d20 = 20)   // 20 : il résisterait toujours
        c.enemies[0].blindedTurns = 1
        c.castRelic(Relic.CHARM, 0, Timing.MISS)
        assertTrue(c.enemies[0].charmed)
    }

    // ── Lot 2 : les sorts du mage ───────────────────────────────────────────────

    @Test
    fun laCristallisationFrappeTriplePuisBrise() {
        val c = fight(Relic.CRYSTALLIZE)
        c.enemies[0].frozenTime = 1.0
        val (lo, _) = c.hero.relicDamage(Relic.CRYSTALLIZE)
        // Le gobelin résiste à la glace : ×0,5, puis ×3
        val hit = c.castRelic(Relic.CRYSTALLIZE, 0, Timing.MISS).main!!
        assertTrue(hit.damage >= (lo * 0.5f * Relic.CRYSTAL_MULT).toInt())
        assertFalse(c.enemies[0].frozen)
    }

    @Test
    fun zeroAbsoluLaCristallisationGardeLeGel() {
        val c = fight(Relic.ICE_SHARD, Relic.CRYSTALLIZE)
        c.enemies[0].frozenTime = 1.0
        c.castRelic(Relic.CRYSTALLIZE, 0, Timing.MISS)
        assertTrue(c.enemies[0].frozen)
    }

    @Test
    fun leMeteoreTombeDeuxToursPlusTard() {
        val c = fight(Relic.METEOR, enemies = 2)
        assertTrue(c.castRelic(Relic.METEOR, 0, Timing.MISS).hits.isEmpty())
        assertTrue(c.lastHeroTurnEnd.meteor.isEmpty())
        enemyTurn(c)
        c.attack(0, Timing.MISS)
        assertEquals("il tombe à la fin du 2e tour du héros", 2, c.lastHeroTurnEnd.meteor.size)
        assertEquals(0, c.meteorTurns)
    }

    @Test
    fun onNeRelancePasUnMeteoreEnLAir() {
        val c = fight(Relic.METEOR)
        c.castRelic(Relic.METEOR, 0, Timing.MISS)
        enemyTurn(c)
        c.relicCooldowns.clear()                               // comme si la SAG avait tout rechargé
        assertFalse("sinon il ne tomberait jamais", c.canCast(Relic.METEOR))
        c.attack(0, Timing.MISS)
        assertEquals(1, c.lastHeroTurnEnd.meteor.size)
    }

    @Test
    fun lesProjectilesTirentTroisTraits() {
        val c = fight(Relic.MAGIC_MISSILE, enemies = 2)
        val hits = c.castRelic(Relic.MAGIC_MISSILE, 0, Timing.MISS).hits
        assertEquals(Relic.MISSILE_COUNT, hits.size)
        assertTrue(hits.all { it.affinity == Affinity.NORMAL && it.reactions.isEmpty() })
    }

    @Test
    fun leBouclierAbsorbeEtRechargeSilTient() {
        val c = fight(Relic.ARCANE_SHIELD, Relic.FIREBALL)
        c.relicCooldowns[Relic.FIREBALL] = 5
        c.castRelic(Relic.ARCANE_SHIELD, 0, Timing.MISS)
        val barrier = c.barrier
        assertEquals(c.hero.relicAmount(Relic.ARCANE_SHIELD), barrier)
        val hp = c.hero.hp
        val strike = strikeOf(c)
        c.endEnemyTurn()
        assertTrue(strike.absorbed > 0)
        assertEquals("un petit coup : la barrière a tout pris", hp, c.hero.hp)
        assertEquals("un tour normal + un tour du bouclier", 3, c.hero.relicCooldown(Relic.FIREBALL))
    }

    // ── Lot 2 : sacré, soin, pierre ─────────────────────────────────────────────

    @Test
    fun laLumiereAveugleEtBruleLesMortsVivants() {
        val c = fight(Relic.HOLY_LIGHT, type = MonsterType.SKELETON)
        val hit = c.castRelic(Relic.HOLY_LIGHT, 0, Timing.MISS).main!!
        assertEquals(Affinity.VULNERABLE, hit.affinity)
        assertEquals(Relic.HOLY_LIGHT.effectTurns, c.enemies[0].blindedTurns)
    }

    @Test
    fun leSacrePurifieLePoisonDuDemon() {
        val c = fight(Relic.VENOM, Relic.HOLY_LIGHT, type = MonsterType.DEMON)
        castAndPass(c, Relic.VENOM)
        val e = c.enemies[0]
        // Le démon résiste au poison (×0,5) mais craint le sacré (×2) : ce qui restait fait quatre fois plus
        val left = e.poisonDoses * e.poisonDoseDamage * e.poisonTurns
        val hit = c.castRelic(Relic.HOLY_LIGHT, 0, Timing.MISS).main!!
        assertTrue(Reaction.PURIFY in hit.reactions)
        assertEquals(left * 4, hit.explosion)
        assertEquals(0, e.poisonDoses)
    }

    @Test
    fun laRegenerationSoigneAChaqueTour() {
        val c = fight(Relic.REGENERATION)
        c.hero.hp = 5
        c.castRelic(Relic.REGENERATION, 0, Timing.MISS)
        assertEquals(c.hero.relicAmount(Relic.REGENERATION), c.lastHeroTurnEnd.healed)
        assertEquals(Relic.REGENERATION.effectTurns - 1, c.regenTurns)
    }

    @Test
    fun aubeLeSoinBruleLesMortsVivants() {
        val c = fight(Relic.HOLY_LIGHT, Relic.REGENERATION, type = MonsterType.SKELETON)
        c.castRelic(Relic.REGENERATION, 0, Timing.MISS)
        val tick = c.lastHeroTurnEnd.ticks.single()
        assertEquals(Element.HOLY, tick.element)
    }

    @Test
    fun laPeauDePierreEncaisseEtRenvoie() {
        // Un héros avec de l'armure (sinon la doubler ne change rien) contre un gros cogneur.
        // Chacun lance un sort sur soi en premier : les dés du monstre sortent pareil.
        fun strike(vararg relics: Relic): EnemyStrike {
            val brute = Enemy(MonsterType.GOBLIN, maxHp = 1000, damage = 100, cadence = 1, countdown = 1)
            val hero = heroWithAllSlots().apply {
                relics.forEach { addRelic(it) }
                equipped[EquipSlot.RING] = LootSystem.create(ItemBase.RING, 1, Rarity.NORMAL, 0, Random(0))
                    .copy(implicits = listOf(StatRoll(StatType.ARMOR, 40f)), affixes = emptyList())
            }
            val c = Combat(hero, 1, listOf(brute), ambush = false, rng = Random(1), attackDie = { 15 })
            c.castRelic(relics.first(), 0, Timing.MISS)
            return strikeOf(c)
        }
        val bare = strike(Relic.REGENERATION)
        val stone = strike(Relic.STONESKIN)
        val rampart = strike(Relic.STONESKIN, Relic.WAR_CRY)
        assertEquals(0, bare.thorns)
        assertTrue("l'armure doublée : ${stone.damage} contre ${bare.damage}", stone.damage < bare.damage)
        assertTrue(stone.thorns > 0)
        assertTrue("Rempart : le double", rampart.thorns >= 2 * stone.thorns - 1)
    }

    // ── Où on les trouve ────────────────────────────────────────────────────────

    @Test
    fun laPremiereReliqueEstGarantieALEtageDeux() {
        repeat(10) { seed ->
            val game = RoguelikeGame(startFloor = RoguelikeGame.FIRST_RELIC_FLOOR, rng = Random(seed))
            assertTrue(game.level.items.any { it.type == ItemType.RELIC })
        }
        assertTrue("jamais à l'étage 1", RoguelikeGame(rng = Random(3)).level.items.none { it.type == ItemType.RELIC })
    }
}
