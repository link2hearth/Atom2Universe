package com.Atom2Universe.app.games.roguelike

import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import kotlin.math.roundToInt
import kotlin.random.Random

// ─── Monstres ───────────────────────────────────────────────────────────────────

/**
 * Ce qu'un monstre pense d'un élément, comme dans D&D (et les types de Pokémon) : les
 * dégâts de l'élément sont multipliés par [damageMult], et [saveBonus] s'ajoute à son jet
 * de sauvegarde contre l'effet (gel, paralysie). Immunisé : ni dégâts, ni effet.
 */
enum class Affinity(val damageMult: Float, val saveBonus: Int) {
    VULNERABLE(2f, -5), NORMAL(1f, 0), RESISTANT(0.5f, 5), IMMUNE(0f, 0)
}

/**
 * Stats de base à l'étage 1. Elles grimpent avec l'étage, jamais avec le joueur :
 * c'est ce qui permet à l'équipement de compter.
 *
 * [cadence] : le monstre frappe tous les N tours. Un rat frappe à chaque tour,
 * une grosse brute prend son élan.
 *
 * [affinities] : ses faiblesses et résistances par élément — provisoires, comme ces monstres,
 * mais réparties pour qu'aucun élément ne l'emporte partout. C'est ce qui oblige à choisir
 * ses deux reliques selon ce qu'on va affronter. Rien ne les affiche : on les découvre en
 * frappant (« Efficace ! », « Peu efficace… », « Immunisé ! »).
 */
enum class MonsterType(
    @StringRes override val labelRes: Int,
    val baseHp: Int, val baseDamage: Int, val cadence: Int,
    val minFloor: Int, val goldMin: Int, val goldMax: Int,
    val affinities: Map<Element, Affinity>,
) : Labeled {
    RAT     (R.string.roguelike_monster_rat,      16,  3, 1, 1, 1,  3,
        mapOf(Element.FIRE to Affinity.VULNERABLE, Element.POISON to Affinity.RESISTANT)),
    GOBLIN  (R.string.roguelike_monster_goblin,   24,  4, 1, 1, 2,  5,
        mapOf(Element.POISON to Affinity.VULNERABLE, Element.ICE to Affinity.RESISTANT)),
    SKELETON(R.string.roguelike_monster_skeleton, 34,  6, 2, 2, 3,  7,
        mapOf(Element.POISON to Affinity.IMMUNE, Element.LIGHTNING to Affinity.VULNERABLE, Element.FIRE to Affinity.RESISTANT)),
    ORC     (R.string.roguelike_monster_orc,      50, 10, 2, 3, 5, 10,
        mapOf(Element.FIRE to Affinity.VULNERABLE, Element.LIGHTNING to Affinity.RESISTANT)),
    DEMON   (R.string.roguelike_monster_demon,    70, 14, 3, 5, 8, 15,
        mapOf(Element.FIRE to Affinity.IMMUNE, Element.ICE to Affinity.VULNERABLE, Element.POISON to Affinity.RESISTANT));

    fun affinity(e: Element) = affinities[e] ?: Affinity.NORMAL
}

/**
 * Les jets de sauvegarde, façon D&D. Contre un sort de contrôle, le monstre lance
 * **d20 + sa maîtrise + son affinité** ; s'il n'atteint pas le **DD** du héros
 * de la relique (voir [Hero.spellDc]), l'effet prend.
 *
 * La maîtrise grandit avec la puissance : celle du héros suit son arme, celle du monstre
 * l'étage (la puissance d'arme qu'on y trouve). Avec l'équipement de l'étage et 10 d'INT,
 * elles s'annulent et le gel prend **une fois sur deux** ; INT et l'arme font pencher la
 * balance, les affinités la déplacent de 25 points (±5 sur un d20).
 */
object SpellSave {
    const val DC_BASE = 11
    /**
     * Le geste compte aussi pour le contrôle : un swipe « bien » sur le sort ajoute
     * [GOOD_STRIKE_DC] au DD, un swipe parfait impose au monstre le **désavantage** de
     * D&D (deux d20, il garde le pire) — le gel prend alors ~3 fois sur 4 au lieu d'une
     * sur 2. Les gestes décident *combien* (critique, parade) ; ici ils pèsent aussi sur *si*.
     */
    const val GOOD_STRIKE_DC = 2
    /** +2 au départ, +1 tous les 8 crans de puissance (+7 à la puissance 41), comme les niveaux de D&D. */
    fun proficiency(power: Int) = 2 + (power - 1).coerceAtLeast(0) / 8
    fun monsterProficiency(floor: Int) = proficiency(LootSystem.powerCenter(floor).roundToInt())
    /** Chance que l'effet prenne pour un d20 : total < DD. */
    fun landChance(dc: Int, saveBonus: Int) = ((dc - 1 - saveBonus).coerceIn(0, 20)) / 20f
}

/**
 * La classe d'armure et le jet d'attaque des monstres, façon D&D : le monstre lance
 * **d20 + son bonus d'attaque** ; s'il atteint la CA du héros ([Hero.armorClass]), il
 * touche, et **alors seulement** l'armure réduit ses dégâts et la parade joue. Un 20
 * touche toujours, un 1 rate toujours.
 *
 * Le bonus d'attaque suit l'étage comme la maîtrise du héros suit ses pièces : ils
 * s'annulent, et le héros de référence (bouclier, pièces sans bonus, DEX 10) est touché
 * [REF_HIT] = 3 fois sur 4. Pour que ce héros prenne en moyenne autant qu'avant la CA,
 * les coups qui touchent sont relevés de [DAMAGE_COMPENSATION] : l'équilibre PV / dégâts
 * réglé avec les affixes tient, et c'est l'écart à la référence qui paie — le voleur léger
 * esquive plus, le mage en tissu prend plus souvent.
 */
object ArmorClass {
    const val BASE = 10
    const val SHIELD = 2
    const val MONSTER_ATTACK_BASE = 6
    /** Un point de CA, c'est une face du d20 : 5 points de chance d'être touché. */
    const val AC_STEP = 0.05f
    const val REF_HIT = 0.75f
    const val DAMAGE_COMPENSATION = 1f / REF_HIT

    fun monsterAttack(floor: Int) = SpellSave.monsterProficiency(floor) + MONSTER_ATTACK_BASE
    /** Chance de toucher, avec le 1 qui rate et le 20 qui touche toujours. */
    fun hitChance(ac: Int, attack: Int) = ((21 - (ac - attack)) / 20f).coerceIn(0.05f, 0.95f)
}

/**
 * Un jet de sauvegarde lancé : [roll] le d20 gardé, [total] avec les bonus, contre [dc].
 * [disadvantage] : le monstre a lancé deux dés et gardé le pire (swipe parfait).
 */
data class SaveRoll(
    val roll: Int, val total: Int, val dc: Int, val saved: Boolean,
    val reason: SaveReason = SaveReason.ROLLED, val disadvantage: Boolean = false,
)
/** Pourquoi le jet a été réussi d'office : immunité, ou rage. */
enum class SaveReason { ROLLED, IMMUNE, RAGE }

class Enemy(
    val type: MonsterType,
    val maxHp: Int,
    val damage: Int,
    val cadence: Int,
    /** Tours restants avant sa prochaine attaque (affiché au-dessus de lui). */
    var countdown: Int,
) {
    var hp = maxHp
    val alive get() = hp > 0

    // ── Effets des reliques (voir [Relic]) ──
    var burnTurns  = 0
    var burnDamage = 0
    var poisonTurns = 0
    var poisonDoses = 0
    var poisonDoseDamage = 0
    /** Gelé : son compteur ne bouge plus, il ne fait rien du tout. */
    var frozenTurns = 0
    /** Paralysé : chaque attaque qui tombe demande un jet ; raté, elle est perdue. */
    var paralyzedTurns = 0
    /** Le geste du lancer de la paralysie : il pèse sur tous ses jets suivants. */
    var paralysisTiming = Timing.MISS
    /** Le DD de la relique qui l'a paralysé, figé au lancer. */
    var paralysisDc = 0
    /** Contrôles réussis depuis sa dernière attaque : à [Relic.RAGE_AFTER], il enrage. */
    var controlStreak = 0
    /** Enragé : incontrôlable, frappe deux fois plus vite, mais attaque avec désavantage. */
    var rageTurns = 0
    val enraged get() = rageTurns > 0
}

object Encounters {

    fun hpMult(floor: Int)     = 1f + 0.22f * (floor - 1)
    fun damageMult(floor: Int) = 1f + 0.15f * (floor - 1)

    /** Taille du groupe : seul au début, jusqu'à 3 à partir de l'étage 5. */
    fun groupSize(floor: Int, rng: Random): Int {
        val r = rng.nextFloat()
        return when {
            floor <= 2 -> 1
            floor <= 4 -> if (r < 0.70f) 1 else 2
            else       -> when { r < 0.50f -> 1; r < 0.85f -> 2; else -> 3 }
        }
    }

    fun roll(floor: Int, rng: Random): List<MonsterType> {
        val eligible = MonsterType.entries.filter { it.minFloor <= floor }
        return List(groupSize(floor, rng)) { eligible.random(rng) }
    }

    /**
     * En groupe, chacun frappe moins souvent (cadence + taille − 1) et les attaques sont
     * décalées : trois rats frappent à tour de rôle, pas tous ensemble.
     */
    fun build(types: List<MonsterType>, floor: Int): List<Enemy> = types.mapIndexed { i, t ->
        val cadence = t.cadence + types.size - 1
        Enemy(
            type      = t,
            maxHp     = (t.baseHp * hpMult(floor)).roundToInt(),
            damage    = (t.baseDamage * damageMult(floor)).roundToInt(),
            cadence   = cadence,
            countdown = cadence - (i % cadence),
        )
    }
}

// ─── Reliques ───────────────────────────────────────────────────────────────────

/** L'élément d'un sort : il décide de l'effet qui s'ajoute aux dégâts. */
enum class Element { FIRE, ICE, LIGHTNING, POISON }

/**
 * Une relique donne un sort. On les **trouve** dans le donjon (voir
 * [RoguelikeGame.RELIC_FLOORS]), on n'en porte que [Hero.RELIC_SLOTS] à la fois.
 *
 * Une relique ne décrit que sa **forme** : son élément, sa recharge, la durée de son effet.
 * Ses dégâts, eux, sont **calculés** par [RelicBudget] : aucun nombre de dégâts n'est écrit
 * ici. Ils se comptent en coups d'épée de référence, puis suivent l'arme portée, INT et
 * les bonus « dégâts des sorts » (voir [Hero.relicDamage]).
 *
 * [effectTurns] : la durée de l'effet de l'élément —
 *  - Feu : brûlure, [BURN_SHARE] du coup à chaque tour ;
 *  - Glace : un jet de sauvegarde au lancer ; raté, la cible est **figée** (son compteur
 *    s'arrête : son attaque est **repoussée**, jamais annulée) ;
 *  - Foudre : la cible est **paralysée**, façon Pokémon : chaque attaque qui tombe pendant
 *    la paralysie demande un jet ; raté, elle est **perdue** (la magie, plus tard, passera) ;
 *  - Poison : une **dose** de plus (jusqu'à [POISON_MAX_DOSES]) ; relancer renouvelle la
 *    durée de toutes les doses.
 */
enum class Relic(
    @StringRes override val labelRes: Int,
    @StringRes val descRes: Int,
    val element: Element,
    /** La caractéristique qui fait ses dégâts et son DD : c'est elle qui dit à quel archétype elle va. */
    val attribute: StatType,
    val cooldown: Int,
    val effectTurns: Int,
    val color: Int,
    val iconRow: Int, val iconCol: Int,
) : Labeled {
    FIREBALL (R.string.roguelike_relic_fireball,  R.string.roguelike_relic_fireball_desc,  Element.FIRE,      StatType.INT, 3, 2, 0xFFB5451B.toInt(), 113, 6),
    ICE_SHARD(R.string.roguelike_relic_ice_shard, R.string.roguelike_relic_ice_shard_desc, Element.ICE,       StatType.INT, 3, 1, 0xFF2F7FB5.toInt(), 113, 8),
    LIGHTNING(R.string.roguelike_relic_lightning, R.string.roguelike_relic_lightning_desc, Element.LIGHTNING, StatType.INT, 5, 3, 0xFF9C7A12.toInt(), 132, 5),
    VENOM    (R.string.roguelike_relic_venom,     R.string.roguelike_relic_venom_desc,     Element.POISON,    StatType.DEX, 3, 4, 0xFF3E8E3A.toInt(), 133, 3);

    /** Dégâts directs, en coups d'épée de référence (voir [RelicBudget]). */
    val minCoef get() = RelicBudget.hitCoef(this) * RelicBudget.SPREAD_MIN
    val maxCoef get() = RelicBudget.hitCoef(this) * RelicBudget.SPREAD_MAX
    /** Poison : ce qu'une dose ronge par tour, en coups d'épée. 0 pour les autres. */
    val doseCoef get() = RelicBudget.doseCoef(this)

    companion object {
        const val BURN_SHARE       = 0.25f
        const val POISON_MAX_DOSES = 3
        /**
         * La rage : après ce nombre de contrôles réussis d'affilée (sans qu'il ait pu frapper
         * entre-temps), l'ennemi s'énerve pendant [RAGE_TURNS] tours. Il est alors
         * incontrôlable, son compteur descend de 2 par tour, mais il attaque avec
         * **désavantage** (deux d20, il garde le pire). Sans ça, deux reliques de contrôle
         * bloquaient un ennemi pour toujours.
         */
        const val RAGE_AFTER = 2
        const val RAGE_TURNS = 3
    }
}

/**
 * Ce que vaut un sort, et **pourquoi** : même démarche que [AffixBudget].
 *
 * > L'unité, c'est le **tour** : un coup d'épée de référence vaut 1. Un sort remplace un
 * > coup d'épée, il doit donc valoir ce coup **plus une prime** de [SHARE_PER_TURN] par
 * > tour de recharge — soit, en moyenne sur le combat, +12 % de ce que fait le héros pour
 * > chaque relique portée : **une relique vaut un affixe plein**.
 *
 * L'effet se paie sur les dégâts directs :
 *  - **Feu** : la brûlure ajoute [Relic.BURN_SHARE] du coup par tour ; le coup est réduit
 *    d'autant pour que coup + brûlure fassent la valeur.
 *  - **Glace, foudre** : [FREEZE_TURN_VALUE] et [PARALYSIS_TURN_VALUE] par tour d'effet,
 *    multipliés par [REF_LAND_CHANCE], la chance qu'a l'effet de prendre à équipement de
 *    l'étage. Ces valeurs sont **mesurées** (`relicsAtFixedGear`), pas déduites : le
 *    contrôle ne se laisse pas mettre en formule (voir DONJON.md, « Les reliques »).
 *  - **Poison** : [POISON_DOT_SHARE] de la valeur part dans la première dose (sur toute sa
 *    durée), le reste dans le coup. **Voulu** : les doses qui s'empilent dépassent le budget
 *    dans un long combat — c'est le sort des gros sacs de PV, donc des boss.
 */
object RelicBudget {

    /** La prime d'un sort, par tour de recharge, en coups d'épée : l'équivalent d'un affixe plein. */
    const val SHARE_PER_TURN = 0.12f
    const val FREEZE_TURN_VALUE = 1.0f
    const val PARALYSIS_TURN_VALUE = 0.8f
    /** Un gel prend une fois sur deux contre un monstre normal, à équipement de l'étage. */
    const val REF_LAND_CHANCE = 0.5f
    const val POISON_DOT_SHARE = 2f / 3f
    /** L'écart des dégâts autour de la moyenne, comme l'épée (4–7 autour de 5,5). */
    const val SPREAD_MIN = 0.75f
    const val SPREAD_MAX = 1.25f

    /** Ce que vaut un lancer, en coups d'épée. */
    fun value(r: Relic) = 1f + SHARE_PER_TURN * r.cooldown

    /** Coup direct moyen, en coups d'épée. */
    fun hitCoef(r: Relic): Float = when (r.element) {
        Element.FIRE      -> value(r) / (1f + Relic.BURN_SHARE * r.effectTurns)
        Element.ICE       -> value(r) - FREEZE_TURN_VALUE * r.effectTurns * REF_LAND_CHANCE
        Element.LIGHTNING -> value(r) - PARALYSIS_TURN_VALUE * r.effectTurns * REF_LAND_CHANCE
        Element.POISON    -> value(r) * (1f - POISON_DOT_SHARE)
    }

    fun doseCoef(r: Relic): Float =
        if (r.element == Element.POISON) value(r) * POISON_DOT_SHARE / r.effectTurns else 0f
}

// ─── Combat ─────────────────────────────────────────────────────────────────────

/** Qualité d'un geste en rythme : parade ou frappe. */
enum class Timing { MISS, GOOD, PERFECT }

enum class CombatPhase { PLAYER_TURN, ENEMY_TURN, VICTORY, DEFEAT }

/**
 * [affinity] : ce que la cible pense de l'élément du sort (NORMAL pour l'épée).
 * [save] : le jet de sauvegarde contre l'effet, s'il y en a eu un. [enraged] : ce sort l'a
 * fait enrager.
 */
data class HitResult(
    val target: Int, val damage: Int, val crit: Boolean, val killed: Boolean,
    val affinity: Affinity = Affinity.NORMAL, val save: SaveRoll? = null, val enraged: Boolean = false,
)
/** Dégâts d'un effet qui dure (brûlure, poison) au début du tour ennemi. */
data class DotTick(val enemy: Int, val damage: Int, val killed: Boolean, val element: Element)
/** Un ennemi arrêté par un effet ce tour-ci : figé par la glace, ou attaque perdue par la foudre. */
data class StatusStop(val enemy: Int, val element: Element)
/** Un jet de sauvegarde lancé pendant le tour ennemi (paralysie). */
data class EnemySave(val enemy: Int, val save: SaveRoll)
/**
 * Le début du tour ennemi : les dégâts des effets, qui est arrêté, les jets de paralysie,
 * ceux qui enragent, puis qui frappe.
 */
data class EnemyTurnStart(
    val ticks: List<DotTick>, val attackers: List<Int>, val stopped: List<StatusStop>,
    val saves: List<EnemySave> = emptyList(), val enraged: List<Int> = emptyList(),
)
/**
 * [missed] : le jet d'attaque n'a pas atteint la CA du héros. [imageHit] : il a frappé un
 * double de l'Image miroir. [blocked] (guerrier, bouclier), [dodged] + [counter] (voleur),
 * [recovered] (mage) : ce que la parade parfaite a donné selon l'archétype.
 */
data class EnemyStrike(
    val enemy: Int, val damage: Int, val parry: Timing,
    val missed: Boolean = false, val imageHit: Boolean = false,
    val blocked: Boolean = false, val dodged: Boolean = false, val counter: HitResult? = null,
    val recovered: Boolean = false,
)
data class CombatRewards(val gold: Int, val potions: Int, val equipment: List<Equipment>)

/**
 * Un combat au tour par tour. Le moteur ne connaît pas le temps : l'écran mesure le
 * geste du joueur (parade, swipe) et le lui transmet sous forme de [Timing].
 *
 * Déroulé d'un tour :
 *   tour du joueur : [attack], [castRelic] ou [drinkPotion]
 *   tour ennemi    : [startEnemyTurn] (brûlure, poison, gel, paralysie, attaquants),
 *                    puis [resolveStrike] pour chacun, puis [endEnemyTurn]
 */
class Combat(
    val hero: Hero,
    val floor: Int,
    val enemies: List<Enemy>,
    ambush: Boolean,
    private val rng: Random = Random,
    /** Le d20 des jets de sauvegarde (les tests le truquent). */
    private val d20: () -> Int = { rng.nextInt(1, 21) },
    /** Le d20 des jets d'attaque des monstres. */
    private val attackDie: () -> Int = { rng.nextInt(1, 21) },
) {
    companion object {
        const val STRIKE_GOOD      = 0.25f
        const val STRIKE_PERFECT   = 0.60f
        const val PARRY_GOOD_MULT  = 0.5f
        const val PARRY_PERFECT_MULT = 0.2f
        const val POTION_DROP      = 0.08f

        /** Coup mortel : une cible sous ce seuil de PV est exposée. */
        const val DEADLY_HP_THRESHOLD = 0.30f
        /** Coup mortel sur une cible exposée : critique garanti, et ce bonus au multiplicateur. */
        const val DEADLY_CRIT_BONUS = 1f
        const val MIRROR_IMAGES = 3
    }

    /** Guerrier : en garde jusqu'à son prochain tour (l'écran double la fenêtre de parade). */
    var guarding = false
        private set
    /** Mage : doubles de l'Image miroir encore debout. */
    var mirrorImages = 0
        private set

    /** Pris en embuscade : les monstres frappent avant qu'on puisse agir. */
    var phase = if (ambush) CombatPhase.ENEMY_TURN else CombatPhase.PLAYER_TURN
        private set

    /** Les recharges vivent sur le héros : elles continuent d'un combat à l'autre. */
    val relicCooldowns get() = hero.relicCooldowns
    var rewards: CombatRewards? = null
        private set

    fun aliveIndices() = enemies.indices.filter { enemies[it].alive }
    fun canCast(relic: Relic) = phase == CombatPhase.PLAYER_TURN && relic in hero.relicSlots && hero.relicCooldown(relic) == 0
    fun canDrinkPotion() = phase == CombatPhase.PLAYER_TURN && hero.potions > 0 && hero.hp < hero.maxHp
    fun canUseSpecial() = phase == CombatPhase.PLAYER_TURN && hero.archetype != null && hero.specialCooldown == 0

    /** Empoisonnée, figée, paralysée ou bien entamée : le voleur y plante son coup mortel. */
    fun isExposed(e: Enemy) = e.alive && (e.poisonTurns > 0 || e.frozenTurns > 0 || e.paralyzedTurns > 0 ||
        e.hp < e.maxHp * DEADLY_HP_THRESHOLD)

    // ── Tour du joueur ──────────────────────────────────────────────────────────

    private fun weaponRoll() = rng.nextInt(hero.weaponMin, hero.weaponMax.coerceAtLeast(hero.weaponMin) + 1).toFloat()

    fun attack(target: Int, timing: Timing): HitResult {
        check(phase == CombatPhase.PLAYER_TURN)
        val result = hit(target, weaponRoll(), timing)
        // Vol de vie : seulement à l'arme
        if (hero.lifeSteal > 0f) hero.heal((result.damage * hero.lifeSteal).roundToInt())
        afterPlayerAction()
        return result
    }

    fun castRelic(relic: Relic, target: Int, timing: Timing): HitResult {
        check(canCast(relic))
        val e = enemies[target]
        val affinity = e.type.affinity(relic.element)
        val (lo, hi) = hero.relicDamage(relic)
        val raw = rng.nextInt(lo, hi + 1) * affinity.damageMult
        val result = hit(target, raw, timing, allowZero = affinity == Affinity.IMMUNE).copy(affinity = affinity)
        val (save, enraged) = if (e.alive) applyEffect(relic, e, result.damage, affinity, timing) else null to false
        relicCooldowns[relic] = hero.spellCooldown(relic.cooldown)
        afterPlayerAction()
        return result.copy(save = save, enraged = enraged)
    }

    /**
     * Le jet de sauvegarde de [e] contre un contrôle. Immunisé ou enragé, il le réussit
     * d'office. [timing] : le geste du joueur au lancer (voir [SpellSave.GOOD_STRIKE_DC]).
     */
    private fun rollSave(e: Enemy, element: Element, baseDc: Int, timing: Timing): SaveRoll {
        val dc = baseDc + if (timing == Timing.GOOD) SpellSave.GOOD_STRIKE_DC else 0
        val affinity = e.type.affinity(element)
        if (affinity == Affinity.IMMUNE) return SaveRoll(0, 0, dc, saved = true, reason = SaveReason.IMMUNE)
        if (e.enraged) return SaveRoll(0, 0, dc, saved = true, reason = SaveReason.RAGE)
        val disadvantage = timing == Timing.PERFECT
        val roll = if (disadvantage) minOf(d20(), d20()) else d20()
        val total = roll + SpellSave.monsterProficiency(floor) + affinity.saveBonus
        return SaveRoll(roll, total, dc, saved = total >= dc, disadvantage = disadvantage)
    }

    /** Un contrôle a pris : au [Relic.RAGE_AFTER]ᵉ d'affilée, l'ennemi enrage. Vrai s'il enrage. */
    private fun controlled(e: Enemy): Boolean {
        if (++e.controlStreak < Relic.RAGE_AFTER) return false
        e.controlStreak = 0
        e.rageTurns = Relic.RAGE_TURNS
        e.frozenTurns = 0
        e.paralyzedTurns = 0
        return true
    }

    /** Pose l'effet de l'élément. Renvoie le jet de sauvegarde (s'il y en a un) et la rage. */
    private fun applyEffect(relic: Relic, e: Enemy, damage: Int, affinity: Affinity, timing: Timing): Pair<SaveRoll?, Boolean> {
        if (affinity == Affinity.IMMUNE) return null to false
        when (relic.element) {
            Element.FIRE -> {
                e.burnTurns  = relic.effectTurns
                e.burnDamage = (damage * Relic.BURN_SHARE).roundToInt().coerceAtLeast(1)
            }
            Element.ICE -> {
                val save = rollSave(e, Element.ICE, hero.spellDc(relic), timing)
                if (save.saved) return save to false
                e.frozenTurns = maxOf(e.frozenTurns, relic.effectTurns)
                return save to controlled(e)
            }
            // Pas de jet au lancer : chaque attaque qui tombe pendant la paralysie en demandera un
            Element.LIGHTNING -> if (!e.enraged) {
                e.paralyzedTurns = maxOf(e.paralyzedTurns, relic.effectTurns)
                e.paralysisTiming = timing
                e.paralysisDc = hero.spellDc(relic)
            }
            Element.POISON -> {
                e.poisonDoses = (e.poisonDoses + 1).coerceAtMost(Relic.POISON_MAX_DOSES)
                e.poisonTurns = relic.effectTurns
                val dose = (hero.poisonDose(relic) * affinity.damageMult).roundToInt().coerceAtLeast(1)
                e.poisonDoseDamage = maxOf(e.poisonDoseDamage, dose)
            }
        }
        return null to false
    }

    // ── Le « Spécial » de l'archétype ───────────────────────────────────────────

    private fun spendSpecial() { hero.specialCooldown = hero.spellCooldown(Hero.SPECIAL_COOLDOWN) }

    /** Guerrier : on passe son tour en garde. */
    fun guard() {
        check(canUseSpecial() && hero.archetype == Archetype.WARRIOR)
        guarding = true
        spendSpecial()
        afterPlayerAction()
    }

    /** Mage : trois doubles qui prennent les coups à sa place, façon D&D. */
    fun mirrorImage() {
        check(canUseSpecial() && hero.archetype == Archetype.MAGE)
        mirrorImages = MIRROR_IMAGES
        spendSpecial()
        afterPlayerAction()
    }

    /**
     * Voleur : un coup d'arme. Sur une cible exposée ([isExposed]), critique garanti et
     * multiplicateur relevé de [DEADLY_CRIT_BONUS] — l'attaque sournoise de D&D, et de quoi
     * achever un petit monstre déjà entamé. Sinon, un coup normal.
     */
    fun deadlyStrike(target: Int, timing: Timing): HitResult {
        check(canUseSpecial() && hero.archetype == Archetype.ROGUE)
        val exposed = isExposed(enemies[target])
        val result = hit(target, weaponRoll(), timing, forceCrit = exposed, critBonus = if (exposed) DEADLY_CRIT_BONUS else 0f)
        if (hero.lifeSteal > 0f) hero.heal((result.damage * hero.lifeSteal).roundToInt())
        spendSpecial()
        afterPlayerAction()
        return result
    }

    fun drinkPotion(): Int {
        check(canDrinkPotion())
        val before = hero.hp
        hero.potions--
        hero.heal((hero.maxHp * Hero.POTION_HEAL).roundToInt())
        afterPlayerAction()
        return hero.hp - before
    }

    private fun hit(
        target: Int, raw: Float, timing: Timing, allowZero: Boolean = false,
        forceCrit: Boolean = false, critBonus: Float = 0f,
    ): HitResult {
        val e = enemies[target]
        require(e.alive)
        val bonus = when (timing) { Timing.MISS -> 0f; Timing.GOOD -> STRIKE_GOOD; Timing.PERFECT -> STRIKE_PERFECT }
        val crit  = forceCrit || rng.nextFloat() < (hero.critChance + bonus).coerceAtMost(0.95f)
        val dmg   = (if (crit) raw * (hero.critMult + critBonus) else raw).roundToInt().coerceAtLeast(if (allowZero) 0 else 1)
        e.hp = (e.hp - dmg).coerceAtLeast(0)
        return HitResult(target, dmg, crit, !e.alive)
    }

    private fun afterPlayerAction() {
        phase = if (aliveIndices().isEmpty()) win() else CombatPhase.ENEMY_TURN
    }

    // ── Tour des ennemis ────────────────────────────────────────────────────────

    /**
     * Début du tour ennemi : les effets qui durent rongent (brûlure, poison), puis chaque
     * ennemi avance son compteur. Un ennemi **figé** ne fait rien, pas même avancer son
     * compteur : son attaque est repoussée. Un ennemi **paralysé** avance normalement, mais
     * l'attaque qui tombe demande un jet de sauvegarde : raté, elle est perdue. Un ennemi
     * **enragé** avance de 2.
     */
    fun startEnemyTurn(): EnemyTurnStart {
        check(phase == CombatPhase.ENEMY_TURN)
        val ticks = mutableListOf<DotTick>()
        for (i in aliveIndices()) {
            val e = enemies[i]
            if (e.burnTurns > 0) {
                e.hp = (e.hp - e.burnDamage).coerceAtLeast(0)
                e.burnTurns--
                ticks += DotTick(i, e.burnDamage, !e.alive, Element.FIRE)
            }
            if (e.alive && e.poisonTurns > 0) {
                val dmg = e.poisonDoses * e.poisonDoseDamage
                e.hp = (e.hp - dmg).coerceAtLeast(0)
                if (--e.poisonTurns == 0) { e.poisonDoses = 0; e.poisonDoseDamage = 0 }
                ticks += DotTick(i, dmg, !e.alive, Element.POISON)
            }
        }
        if (aliveIndices().isEmpty()) { phase = win(); return EnemyTurnStart(ticks, emptyList(), emptyList()) }

        val attackers = mutableListOf<Int>()
        val stopped = mutableListOf<StatusStop>()
        val saves = mutableListOf<EnemySave>()
        val enragedNow = mutableListOf<Int>()
        for (i in aliveIndices()) {
            val e = enemies[i]
            val wasEnraged = e.enraged
            if (e.enraged) e.rageTurns--
            if (e.frozenTurns > 0) {
                e.frozenTurns--
                stopped += StatusStop(i, Element.ICE)
                continue
            }
            e.countdown -= if (wasEnraged) 2 else 1
            val due = e.countdown <= 0
            if (due) e.countdown = e.cadence
            if (e.paralyzedTurns > 0) {
                e.paralyzedTurns--
                if (due) {
                    val save = rollSave(e, Element.LIGHTNING, e.paralysisDc, e.paralysisTiming)
                    saves += EnemySave(i, save)
                    if (!save.saved) {
                        stopped += StatusStop(i, Element.LIGHTNING)
                        if (controlled(e)) enragedNow += i
                        continue
                    }
                }
            }
            if (due) attackers += i
        }
        return EnemyTurnStart(ticks, attackers, stopped, saves, enragedNow)
    }

    fun resolveStrike(enemyIndex: Int, parry: Timing): EnemyStrike {
        check(phase == CombatPhase.ENEMY_TURN)
        val e = enemies[enemyIndex]
        if (!e.alive) return EnemyStrike(enemyIndex, 0, parry, missed = true)
        // Il a pu frapper : la série de contrôles qui mène à la rage repart de zéro
        e.controlStreak = 0
        // Image miroir, comme dans D&D : avant son jet d'attaque, un d20 dit s'il vise un
        // double — 6+ avec trois doubles, 8+ avec deux, 11+ avec le dernier
        if (mirrorImages > 0) {
            val need = when (mirrorImages) { 3 -> 6; 2 -> 8; else -> 11 }
            if (attackDie() >= need) { mirrorImages--; return EnemyStrike(enemyIndex, 0, parry, imageHit = true) }
        }
        val roll = if (e.enraged) minOf(attackDie(), attackDie()) else attackDie()
        val hits = roll == 20 || (roll != 1 && roll + ArmorClass.monsterAttack(floor) >= hero.armorClass)
        if (!hits) return EnemyStrike(enemyIndex, 0, parry, missed = true)

        // La parade parfaite, selon l'archétype
        var recovered = false
        if (parry == Timing.PERFECT) when (hero.archetype) {
            Archetype.WARRIOR -> if (hero.hasShield) return EnemyStrike(enemyIndex, 0, parry, blocked = true)
            Archetype.ROGUE -> {
                val counter = hit(enemyIndex, weaponRoll(), Timing.MISS)
                if (aliveIndices().isEmpty()) phase = win()
                return EnemyStrike(enemyIndex, 0, parry, dodged = true, counter = counter)
            }
            Archetype.MAGE -> { hero.tickRelics(1, includeSpecial = false); recovered = true }
            null -> {}
        }

        val parryMult = when (parry) { Timing.MISS -> 1f; Timing.GOOD -> PARRY_GOOD_MULT; Timing.PERFECT -> PARRY_PERFECT_MULT }
        val spread = 0.85f + rng.nextFloat() * 0.30f
        val dmg = hero.mitigate(e.damage * spread * parryMult * ArmorClass.DAMAGE_COMPENSATION, floor).roundToInt().coerceAtLeast(1)
        hero.hp = (hero.hp - dmg).coerceAtLeast(0)
        if (hero.hp == 0) phase = CombatPhase.DEFEAT
        return EnemyStrike(enemyIndex, dmg, parry, recovered = recovered)
    }

    fun endEnemyTurn() {
        if (phase != CombatPhase.ENEMY_TURN) return
        hero.tickRelics()
        guarding = false
        phase = CombatPhase.PLAYER_TURN
    }

    // ── Victoire ────────────────────────────────────────────────────────────────

    private fun win(): CombatPhase {
        var gold = 0; var potions = 0
        val loot = mutableListOf<Equipment>()
        val floorGold = 1f + 0.10f * (floor - 1)
        for (e in enemies) {
            gold += (rng.nextInt(e.type.goldMin, e.type.goldMax + 1) * floorGold * hero.goldMult).roundToInt()
            if (rng.nextFloat() < POTION_DROP) potions++
            LootSystem.tryDrop(floor, hero.nextLootId, rng)?.let { loot += it; hero.nextLootId++ }
        }
        rewards = CombatRewards(gold, potions, loot)
        return CombatPhase.VICTORY
    }
}
