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
        val c = fight(Relic.ICE_SHARD, Relic.WHIRLWIND, d20 = 20)
        c.enemies[0].frozenTime = 1.0
        val hit = c.castRelic(Relic.WHIRLWIND, 0, Timing.MISS).main!!
        assertEquals(listOf(Reaction.SHATTER), hit.reactions)
        val (lo, _) = c.hero.relicDamage(Relic.WHIRLWIND)
        assertTrue("×2 (${hit.damage} contre au moins $lo)", hit.damage >= (lo * Reaction.SHATTER_MULT).toInt())
        assertFalse(c.enemies[0].frozen)
    }

    @Test
    fun leFeuSurUnFigeFaitUnChocThermiqueEtBruleQuandMeme() {
        val c = fight(Relic.FIREBALL)
        c.enemies[0].frozenTime = 1.0
        val hit = c.castRelic(Relic.FIREBALL, 0, Timing.MISS).main!!
        assertEquals(listOf(Reaction.THERMAL_SHOCK), hit.reactions)
        assertFalse(c.enemies[0].frozen)
        assertTrue(c.enemies[0].burnTurns > 0)
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
    fun laFioleFractureEtLeTempsLUsePeuAPeu() {
        val c = fight(Relic.ACID_FLASK)
        c.castRelic(Relic.ACID_FLASK, 0, Timing.MISS)
        assertEquals(Relic.ACID_FLASK.effectTurns, c.enemies[0].fracturedTurns)
        enemyTurn(c)
        assertEquals("un tour de moins à chaque fin de tour ennemi", Relic.ACID_FLASK.effectTurns - 1, c.enemies[0].fracturedTurns)
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
    fun lesEtincellesVisentTroisFoisSansParalysie() {
        val c = fight(Relic.CHAIN_LIGHTNING, enemies = 3)
        val cast = c.castRelic(Relic.CHAIN_LIGHTNING, 1, Timing.MISS)
        assertEquals(3, cast.hits.size)
        assertTrue(cast.hits.all { it.target in 0..2 })
        assertTrue(c.enemies.all { it.paralyzedTurns == 0 })
    }

    // ── Résonances ──────────────────────────────────────────────────────────────

    @Test
    fun uneResonanceDonneSaCaracEtSeDecouvreUneFois() {
        val hero = heroWithAllSlots()
        val int = hero.attribute(StatType.WIS)
        hero.addRelic(Relic.FIREBALL)
        assertTrue(hero.resonances.isEmpty())
        hero.addRelic(Relic.VENOM)
        assertEquals(listOf(Resonance.ALCHEMY), hero.resonances)
        assertEquals(int + Resonance.BONUS, hero.attribute(StatType.WIS))
        assertEquals(listOf(Resonance.ALCHEMY), hero.discoverResonances())
        assertTrue("déjà dans le carnet", hero.discoverResonances().isEmpty())
        hero.toggleRelic(Relic.VENOM)
        assertTrue(hero.resonances.isEmpty())
        assertEquals(int, hero.attribute(StatType.WIS))
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

    // ── Lot 2 : saignement ──────────────────────────────────────────────────────

    /** L'ennemi 0 attaque (le héros vient d'agir) : on renvoie son coup. */
    private fun strikeOf(c: Combat, parry: Timing = Timing.MISS): EnemyStrike {
        val t = c.startEnemyTurn()
        return c.resolveStrike(t.attackers.first(), parry)
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

    // ── Lot 2 : les ruses ───────────────────────────────────────────────────────

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

    // ── Lot 2 : sacré, soin, pierre ─────────────────────────────────────────────

    @Test
    fun laLumiereAveugleEtBruleLesMortsVivants() {
        val c = fight(Relic.HOLY_LIGHT, type = MonsterType.SKELETON)
        val hit = c.castRelic(Relic.HOLY_LIGHT, 0, Timing.MISS).main!!
        assertEquals(Affinity.VULNERABLE, hit.affinity)
        assertEquals(Relic.HOLY_LIGHT.effectTurns, c.enemies[0].blindedTurns)
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
        val bare = strike(Relic.HEAL)
        val stone = strike(Relic.STONESKIN)
        val rampart = strike(Relic.STONESKIN, Relic.WAR_CRY)
        assertEquals(0, bare.thorns)
        assertTrue("l'armure doublée : ${stone.damage} contre ${bare.damage}", stone.damage < bare.damage)
        assertTrue(stone.thorns > 0)
        assertTrue("Rempart : le double", rampart.thorns >= 2 * stone.thorns - 1)
    }

    // ── Refonte du 20/09/2026 : la Ponction vitale et le Verglas ─────────────────

    @Test
    fun laPonctionRendLaMoitieDesDegatsInfliges() {
        val c = fight(Relic.PONCTION)
        c.hero.hp = c.hero.maxHp / 2
        val before = c.hero.hp
        val hit = c.castRelic(Relic.PONCTION, 0, Timing.MISS).main!!
        assertTrue(hit.damage > 0)
        assertEquals((hit.damage * Relic.DRAIN_SHARE).let { Math.round(it) }, c.hero.hp - before)
    }

    @Test
    fun leVerglasFrappeEtRalentitLaCible() {
        val c = fight(Relic.SLOW, d20 = 1)
        val hit = c.castRelic(Relic.SLOW, 0, Timing.MISS).main!!
        assertTrue("il frappe", hit.damage > 0)
        assertTrue("et ralentit", c.enemies[0].slowTurns > 0)
    }

    // ── Refonte du 20/09/2026 : une réaction par paire d'éléments ────────────────

    /** Un ennemi solide dans l'état voulu, puis [relic] lancée dessus (d20 à 20 : ses jets de sauvegarde réussissent, seules les réactions comptent). */
    private fun reaction(relic: Relic, enemies: Int = 1, type: MonsterType = MonsterType.GOBLIN, setup: (Enemy) -> Unit): Pair<Combat, HitResult> {
        val c = fight(relic, enemies = enemies, type = type, d20 = 20)
        setup(c.enemies[0])
        val hit = c.castRelic(relic, 0, Timing.MISS).main!!
        return c to hit
    }
    private fun Enemy.burning() { burnTurns = 2; burnDamage = 3 }
    private fun Enemy.frozenNow() { frozenTime = 2.0 }
    private fun Enemy.paralyzedNow() { paralyzedTurns = 2 }
    private fun Enemy.poisonedNow() { poisonDoses = 1; poisonTurns = 3; poisonDoseDamage = 5 }
    private fun Enemy.exposedNow() { fracturedTurns = 2 }

    @Test
    fun laGlaceSurUnBruleFaitUnChocThermique() {
        val (c, hit) = reaction(Relic.ICE_SHARD) { it.burning() }
        assertTrue(Reaction.THERMAL_SHOCK in hit.reactions)
        assertEquals("la brûlure s'éteint", 0, c.enemies[0].burnTurns)
    }

    @Test
    fun laFoudreSurUnBruleFaitDuPlasma() {
        val (c, hit) = reaction(Relic.LIGHTNING) { it.burning() }
        assertTrue(Reaction.PLASMA in hit.reactions)
        assertEquals(2 + Reaction.PLASMA_BURN_TURNS, c.enemies[0].burnTurns)
    }

    @Test
    fun leFeuSurUnExposeCalcine() {
        val (_, hit) = reaction(Relic.FIREBALL) { it.exposedNow() }
        assertTrue(Reaction.CALCINATION in hit.reactions)
    }

    @Test
    fun leFeuSurUnParalyseFaitUnCourtCircuit() {
        val (c, hit) = reaction(Relic.FIREBALL) { it.paralyzedNow() }
        assertTrue(Reaction.SHORT_CIRCUIT in hit.reactions)
        assertEquals(0, c.enemies[0].paralyzedTurns)
    }

    @Test
    fun laGlaceSurUnParalyseLeFigeSansJet() {
        val (c, hit) = reaction(Relic.ICE_SHARD) { it.paralyzedNow() }
        assertTrue(Reaction.RIGIDITY in hit.reactions)
        assertTrue("gelé malgré son jet réussi", c.enemies[0].frozen)
        assertEquals(0, c.enemies[0].paralyzedTurns)
    }

    @Test
    fun laGlaceSurUnEmpoisonneEnFaitUnRalentissement() {
        val (c, hit) = reaction(Relic.ICE_SHARD) { it.poisonedNow() }
        assertTrue(Reaction.POISON_ICE in hit.reactions)
        assertEquals(0, c.enemies[0].poisonDoses)
        assertEquals(3, c.enemies[0].slowTurns)
    }

    @Test
    fun laGlaceSurUnExposeLeFigeEtAllongeSonExposition() {
        val (c, hit) = reaction(Relic.ICE_SHARD) { it.exposedNow() }
        assertTrue(Reaction.FROZEN_ARMOR in hit.reactions)
        assertTrue(c.enemies[0].frozen)
        assertEquals(2 + Reaction.EXPOSED_EXTRA, c.enemies[0].fracturedTurns)
    }

    @Test
    fun laFoudreSurUnEmpoisonneFaitDesConvulsions() {
        val (c, hit) = reaction(Relic.LIGHTNING) { it.poisonedNow() }
        assertTrue(Reaction.CONVULSIONS in hit.reactions)
        assertTrue(c.enemies[0].paralyzedTurns >= Reaction.CONVULSION_TURNS)
        assertEquals("les doses tombent une fois", 5, hit.explosion)
    }

    @Test
    fun laFoudreSurUnFigeLeParalyse() {
        val (c, hit) = reaction(Relic.LIGHTNING) { it.frozenNow() }
        assertTrue(Reaction.ICE_SHOCK in hit.reactions)
        assertFalse(c.enemies[0].frozen)
        assertEquals(Reaction.ICE_SHOCK_TURNS, c.enemies[0].paralyzedTurns)
    }

    @Test
    fun laFoudreSurUnExposeSauteSurLesAutres() {
        val (c, hit) = reaction(Relic.LIGHTNING, enemies = 2) { it.exposedNow() }
        assertTrue(Reaction.LIGHTNING_ROD in hit.reactions)
        assertTrue(hit.explosion > 0)
        assertTrue(c.enemies[1].hp < c.enemies[1].maxHp)
    }

    @Test
    fun lePoisonSurUnBruleFaitUneEtincelle() {
        val (c, hit) = reaction(Relic.VENOM) { it.burning() }
        assertTrue(Reaction.SPARK in hit.reactions)
        assertEquals("la brûlure est devenue du poison", 0, c.enemies[0].burnTurns)
        assertEquals("la dose posée et une dose de plus", 2, c.enemies[0].poisonDoses)
    }

    @Test
    fun lePoisonSurUnFigeFaitUneEngelure() {
        val (c, hit) = reaction(Relic.VENOM) { it.frozenNow() }
        assertTrue(Reaction.FROSTBITE in hit.reactions)
        assertEquals(2, c.enemies[0].poisonDoses)
        assertTrue("le gel reste", c.enemies[0].frozen)
    }

    @Test
    fun lePoisonSurUnParalyseAllongeLaParalysie() {
        val (c, hit) = reaction(Relic.VENOM) { it.paralyzedNow() }
        assertTrue(Reaction.NEUROTOXIN in hit.reactions)
        assertEquals(2 + Reaction.NEUROTOXIN_EXTRA, c.enemies[0].paralyzedTurns)
    }

    @Test
    fun lePoisonSurUnExposeInfecte() {
        val (c, hit) = reaction(Relic.VENOM) { it.exposedNow() }
        assertTrue(Reaction.INFECTION in hit.reactions)
        assertEquals(2, c.enemies[0].poisonDoses)
        assertEquals(2 + Reaction.EXPOSED_EXTRA, c.enemies[0].fracturedTurns)
    }

    @Test
    fun unSortPhysiqueSurUnParalyseEstUnCoupDeGrace() {
        val (_, hit) = reaction(Relic.WHIRLWIND) { it.paralyzedNow() }
        assertTrue(Reaction.DEATHBLOW in hit.reactions)
        assertTrue(hit.crit)
    }

    @Test
    fun leSacreSurUnEmpoisonnePurifieLeHeros() {
        val (c, hit) = reaction(Relic.HOLY_LIGHT) { it.poisonedNow() }
        assertTrue(Reaction.PURIFY in hit.reactions)
        assertEquals("le poison est nettoyé", 0, c.enemies[0].poisonDoses)
        assertTrue("purifié", c.purifiedTurns > 0)
        assertTrue("il récupère des PV", c.regenTurns > 0)

    }

    @Test
    fun leSacreSurUnBruleLeSoigne() {
        val c = fight(Relic.HOLY_LIGHT, d20 = 20)
        c.enemies[0].burning()
        c.hero.hp = 1
        val hit = c.castRelic(Relic.HOLY_LIGHT, 0, Timing.MISS).main!!
        assertTrue(Reaction.HOLY_FIRE in hit.reactions)
        assertEquals(0, c.enemies[0].burnTurns)
        assertTrue("soigné", c.hero.hp > 1)
    }

    @Test
    fun uneAttaqueDeBaseDuBonArchetypeReagitMaisALaMoitie() {
        // Le mage (feu) avec une arme de mage et l'orbe : son attaque de base fait fondre un figé
        fun hitOn(frozen: Boolean, offhand: Boolean): HitResult {
            val hero = Hero.starter().apply {
                equipped[EquipSlot.HELMET] = LootSystem.create(ItemBase.HELMET, 1, Rarity.NORMAL, 0, Random(0), forcedWeight = ArmorWeight.CLOTH)
                equipped[EquipSlot.CHEST] = LootSystem.create(ItemBase.ARMOR, 1, Rarity.NORMAL, 0, Random(0), forcedWeight = ArmorWeight.CLOTH)
                equipped[EquipSlot.BOOTS] = LootSystem.create(ItemBase.BOOTS, 1, Rarity.NORMAL, 0, Random(0), forcedWeight = ArmorWeight.CLOTH)
                equipped[EquipSlot.WEAPON] = LootSystem.create(ItemBase.STAFF, 1, Rarity.NORMAL, 0, Random(0))
                if (offhand) equipped[EquipSlot.OFFHAND] = LootSystem.create(ItemBase.ORB, 1, Rarity.NORMAL, 0, Random(0))
            }
            val c = Combat(hero, 1, listOf(Enemy(MonsterType.ORC, 100000, 3, 1, 1)), ambush = false, rng = Random(1), attackDie = { 15 })
            if (frozen) c.enemies[0].frozenTime = 2.0
            return c.attack(0, Timing.MISS)
        }
        assertEquals(Element.FIRE, Hero.starter().apply {
            equipped[EquipSlot.HELMET] = LootSystem.create(ItemBase.HELMET, 1, Rarity.NORMAL, 0, Random(0), forcedWeight = ArmorWeight.CLOTH)
            equipped[EquipSlot.CHEST] = LootSystem.create(ItemBase.ARMOR, 1, Rarity.NORMAL, 0, Random(0), forcedWeight = ArmorWeight.CLOTH)
            equipped[EquipSlot.WEAPON] = LootSystem.create(ItemBase.STAFF, 1, Rarity.NORMAL, 0, Random(0))
            equipped[EquipSlot.OFFHAND] = LootSystem.create(ItemBase.ORB, 1, Rarity.NORMAL, 0, Random(0))
        }.attackElement)
        assertTrue(Reaction.THERMAL_SHOCK in hitOn(frozen = true, offhand = true).reactions)
        assertTrue("sans la main gauche de classe : rien", hitOn(frozen = true, offhand = false).reactions.isEmpty())
        assertTrue("pas figé : rien", hitOn(frozen = false, offhand = true).reactions.isEmpty())
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
