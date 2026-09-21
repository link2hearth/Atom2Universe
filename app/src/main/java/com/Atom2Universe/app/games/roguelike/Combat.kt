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
        mapOf(Element.POISON to Affinity.IMMUNE, Element.LIGHTNING to Affinity.VULNERABLE, Element.FIRE to Affinity.RESISTANT,
            Element.HOLY to Affinity.VULNERABLE)),
    ORC     (R.string.roguelike_monster_orc,      50, 10, 2, 3, 5, 10,
        mapOf(Element.FIRE to Affinity.VULNERABLE, Element.LIGHTNING to Affinity.RESISTANT)),
    DEMON   (R.string.roguelike_monster_demon,    70, 14, 3, 5, 8, 15,
        mapOf(Element.FIRE to Affinity.IMMUNE, Element.ICE to Affinity.VULNERABLE, Element.POISON to Affinity.RESISTANT,
            Element.HOLY to Affinity.VULNERABLE));

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
    /**
     * Le plancher de la chance d'être touché : quelle que soit la CA, un monstre touche au moins 30 % du temps (les faces
     * [ALWAYS_HIT_FROM] à 20 du d20). Sans lui, un voleur qui empile la DEX (dague, arc, armure légère) n'était touché que
     * par les 20 naturels et n'encaissait presque rien (voir DONJON.md, « Voleur : la chance d'être touché plancher »).
     * Comme le plafond du critique, il empêche une caractéristique de ne plus rien coûter à l'adversaire.
     */
    const val MIN_HIT = 0.30f
    val ALWAYS_HIT_FROM = 21 - Math.round(MIN_HIT * 20)

    /** Chance de toucher : le 1 rate toujours, et les faces [ALWAYS_HIT_FROM] à 20 touchent toujours ([MIN_HIT]). */
    fun hitChance(ac: Int, attack: Int) = ((21 - (ac - attack)) / 20f).coerceIn(MIN_HIT, 0.95f)
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

/**
 * Un ennemi en combat. Il a sa **jauge** (voir [Combat]) : elle se remplit de [rate] par tour
 * du héros ; pleine, c'est son tour.
 *
 * Ses états se comptent **à ses tours à lui**, comme dans FFX : la brûlure et le poison
 * rongent au début de son tour, le gel lui fait perdre ses prochains tours, et les autres
 * états (trempé, fracturé, affaibli…) perdent un tour à la fin de chacun de ses tours.
 */
class Enemy(
    val type: MonsterType,
    val maxHp: Int,
    val damage: Int,
    /** Il agit tous les N tours du héros (à vitesse égale) : sa jauge se remplit en N tours. */
    val cadence: Int,
    /** Tours du héros avant sa première action : c'est ce qui place sa jauge au début du combat. */
    countdown: Int,
    /** Sa vitesse : 1 à l'étage 1, elle monte avec l'étage (voir [Encounters.speedMult]). */
    val speed: Double = 1.0,
) {
    var hp = maxHp
    val alive get() = hp > 0

    /**
     * Sa jauge : 1, c'est son tour. Au départ, il agit juste après le [countdown]ᵉ tour du
     * héros (le « − 0,5 » : entre deux tours du héros, jamais en même temps que lui).
     */
    var gauge = 1.0 - (countdown - 0.5) / cadence
    /**
     * Ce que sa jauge gagne par tour du héros : sa vitesse sur sa cadence. Enragé, deux fois
     * plus vite (un rat enragé peut frapper deux fois entre deux tours du héros) ; ralenti par
     * la Lenteur, deux fois moins. Le gel, lui, la ralentit très fort (voir [frozenTime]).
     */
    val rate: Double get() = rateWith(rageTurns > 0, slowTurns > 0)
    fun rateWith(enraged: Boolean, slowed: Boolean) = speed / cadence *
        (if (enraged) Relic.RAGE_SPEED else 1.0) * (if (slowed) Relic.SLOW_SPEED else 1.0)

    // ── Effets des reliques (voir [Relic]) ──
    var burnTurns  = 0
    var burnDamage = 0
    var poisonTurns = 0
    var poisonDoses = 0
    var poisonDoseDamage = 0
    /**
     * Gelé : pendant ce temps (en tours du héros, [Relic.FREEZE_TURN_LENGTH] par tour de gel), sa
     * jauge se remplit à ×[Relic.CHILL_SPEED], et il est **engourdi** : ses coups font
     * ×[Combat.NUMB_MULT]. La glace retarde son attaque et adoucit celle qui finit par tomber.
     */
    var frozenTime = 0.0
    /** Ralenti par la Lenteur : sa jauge se remplit deux fois moins vite, pendant ses prochains tours. */
    var slowTurns = 0
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
    /**
     * Le gel est fini (sa jauge repart à pleine vitesse), mais la glace est encore sur lui
     * jusqu'à la fin de son tour suivant : il compte comme figé (Bris, Fonte, Coup mortel, et
     * son coup reste engourdi).
     */
    var thawing = false
    val frozen get() = frozenTime > 0 || thawing

    // ── États partagés du grimoire (voir [RelicEffect]) : ils perdent un tour à la fin de chacun de ses tours ──
    var fracturedTurns = 0
    var weakenedTurns = 0
    /** Aveuglé : il attaque avec désavantage, comme la rage, mais sans la vitesse. */
    var blindedTurns = 0
    /** Marqué par le chasseur, jusqu'à sa mort. */
    var marked = false
    /** Saignement : ce qu'il perd à chacune de ses attaques, pendant [bleedTurns] tours. */
    var bleedTurns = 0
    var bleedDamage = 0
    /** Charmé : sa prochaine attaque frappe un de ses alliés. */
    var charmed = false
}

object Encounters {

    /**
     * Tous les monstres, à tous les étages : relevés quand les bots ont été recalibrés sur un
     * vrai joueur (18/09/2026) — le donjon s'était révélé bien trop facile.
     */
    const val HP_SCALE     = 1.3f
    const val DAMAGE_SCALE = 1.5f

    /**
     * L'étage où les courbes changent de régime. Jusque-là, PV et dégâts montent en ligne
     * droite (réglés au banc sur les 100 premiers étages). Au-delà, les caractéristiques du
     * héros ne grandissent plus (voir [LootSystem.DEEP_POWER]) : les monstres grandissent
     * alors **exactement comme l'équipement**, au rythme de la puissance, et le rapport de
     * force reste celui de l'étage 100 — plus la rampe [DEPTH_RAMP].
     */
    const val DEEP_FLOOR = 100

    /**
     * Ce qui rend la profondeur plus dure que l'étage 100 à équipement moyen : PV et dégâts
     * ×1,25 à l'étage 1 000, ×1,5 à l'étage 10 000. C'est ce que le farm des affixes doit
     * rattraper : un équipement moyen ne suffit plus, il faut les bons tirages.
     */
    const val DEPTH_RAMP = 0.1086f

    /** Au-delà de l'étage 100 : la croissance de l'équipement, et la rampe. 1 avant. */
    fun depthMult(floor: Int): Float {
        if (floor <= DEEP_FLOOR) return 1f
        val power = LootSystem.powerCenter(floor)
        val powerAt100 = LootSystem.powerCenter(DEEP_FLOOR)
        val gear = (1f + 0.45f * (power - 1)) / (1f + 0.45f * (powerAt100 - 1))
        return gear * (1f + DEPTH_RAMP * kotlin.math.ln(floor.toFloat() / DEEP_FLOOR))
    }

    fun hpMult(floor: Int)     = HP_SCALE * (1f + 0.22f * (floor.coerceAtMost(DEEP_FLOOR) - 1)) * depthMult(floor)
    fun damageMult(floor: Int) = DAMAGE_SCALE * (1f + 0.15f * (floor.coerceAtMost(DEEP_FLOOR) - 1)) * depthMult(floor)
    /**
     * La vitesse des monstres monte avec l'étage : +0,4 % par étage, ×1,4 à l'étage 100, puis
     * plus rien — sinon ils seraient 41 fois plus rapides à l'étage 10 000. Leur vitesse par
     * type, c'est leur cadence (le rat joue à chaque tour, le démon un sur trois).
     */
    const val SPEED_PER_FLOOR = 0.004
    fun speedMult(floor: Int)  = 1.0 + SPEED_PER_FLOOR * (floor.coerceAtMost(DEEP_FLOOR) - 1)

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
            speed     = speedMult(floor),
        )
    }
}

// ─── Reliques ───────────────────────────────────────────────────────────────────

/**
 * Le type de dégâts d'un sort. Il décide des **affinités** (ce que le monstre en pense) et
 * des **réactions** (voir [Reaction]) — pas de l'effet : une Pluie glacée est de la glace,
 * mais elle trempe au lieu de figer (voir [RelicEffect]).
 *
 * [PHYSICAL] est « sans élément » : aucun monstre n'y est vulnérable ni
 * résistant, la valeur sûre contre un monstre qu'on ne connaît pas encore. Le physique brise
 * la glace (Bris), l'arcane ne réagit à rien. [HOLY], le sacré, est fait pour les
 * morts-vivants des Cryptes.
 */
enum class Element { FIRE, ICE, LIGHTNING, POISON, HOLY, PHYSICAL }

/**
 * Qui un sort touche. [CHAIN] : la cible, puis les autres, un peu moins fort à chaque rebond.
 * [MISSILES] : trois traits, chacun sur un ennemi au hasard (le même peut en prendre plusieurs).
 */
enum class RelicTarget { ONE, ALL, CHAIN, MISSILES, SELF }

/**
 * Ce qu'un sort fait **en plus** de ses dégâts. Un seul effet par relique : c'est ce qui la
 * définit. Les durées sont dans [Relic.effectTurns]. [hits] : faux pour un sort qui ne frappe
 * pas (il ne fait que poser son effet).
 *
 * Les états que ces effets posent sont **partagés** par tout le grimoire : c'est ce qui permet
 * les combos (voir [Reaction]). Une nouvelle relique réutilise d'abord les états qui
 * existent ; un nouvel état se pense avec ses réactions.
 */
enum class RelicEffect(val hits: Boolean = true) {
    NONE,
    /** Brûlure : [Relic.BURN_SHARE] du coup à chaque tour. */
    BURN,
    /** Jet de sauvegarde ; raté, la cible est **figée** : son compteur s'arrête (un délai, jamais une annulation). */
    FREEZE,
    /** Paralysie, façon Pokémon : chaque attaque qui tombe demande un jet ; ratée, elle est perdue. */
    PARALYZE,
    /** Une dose de poison de plus (jusqu'à [Relic.POISON_MAX_DOSES]) ; relancer renouvelle la durée. */
    POISON,
    /** Fracturé : la cible prend [Combat.FRACTURE_MULT] fois **tous** les dégâts (la Vulnérabilité de *Slay the Spire*). */
    FRACTURE,
    /** Marqué jusqu'à sa mort : exposé au Coup mortel, critiques plus lourds. Mort, la marque saute sur un autre. Une seule à la fois. */
    MARK,
    /** Cri de guerre : tous les ennemis **affaiblis**, et les [Relic.WARCRY_ATTACKS] prochains coups d'arme renforcés. */
    WARCRY(hits = false),
    /** Lames empoisonnées : chacun des [Relic.effectTurns] prochains coups d'arme ajoute une dose. */
    ENCHANT_POISON(hits = false),
    /** Saignement : la cible perd des PV **chaque fois qu'elle attaque**. La geler la fait moins saigner. */
    BLEED,
    /** Dagues en éventail : chaque critique fait saigner. */
    BLEED_ON_CRIT,
    /** Bombe fumigène : tous aveuglés, et le prochain coup d'arme est une attaque sournoise (critique garanti). */
    SMOKE(hits = false),
    /** Fiole d'acide : une dose de poison **et** fracturé. */
    ACID,
    /** Cristallisation : dégâts modestes, mais ×[Relic.CRYSTAL_MULT] contre un figé (et le gel se brise). */
    CRYSTALLIZE,
    /** Météore : il tombe [Relic.effectTurns] tours plus tard, sur tous les ennemis. */
    DELAYED,
    /** Bouclier arcanique : une barrière absorbe les prochains dégâts ; si elle tient jusqu'au tour suivant, les reliques gagnent un tour de recharge. */
    BARRIER(hits = false),
    /** Aveuglé : il attaque avec désavantage. */
    BLIND,
    /** Régénération : un peu de PV à chacun des [Relic.effectTurns] prochains tours. */
    REGEN(hits = false),
    /** Soin : [Relic.HEAL_SHARE] des PV max, tout de suite. Le soin classique, qui remplace la potion. */
    HEAL(hits = false),
    /** Peau de pierre : l'armure double, et on renvoie une part des coups reçus (épines). */
    STONESKIN(hits = false),
    /** Charme : jet ; raté, sa prochaine attaque frappe un autre ennemi (seul, il la perd). */
    CHARM(hits = false),
    /** Hâte : la jauge du héros se remplit plus vite pendant ses prochains tours. */
    HASTE(hits = false),
    /** Lenteur : jet ; raté, la jauge de la cible se remplit deux fois moins vite pendant ses prochains tours. Un contrôle. */
    SLOW,
    /** Ponction vitale : dégâts, et le héros récupère [Relic.DRAIN_SHARE] de ce qu'il a infligé. */
    DRAIN,
    /** Sablier : les prochaines attaques ennemies arrivent plus lentement, la fenêtre de parade s'élargit. */
    HOURGLASS(hits = false),
}

/**
 * Une relique donne un sort. On les **trouve** en explorant (voir
 * [RoguelikeGame.RELIC_CHANCE]), on en porte jusqu'à [Hero.RELIC_SLOTS] à la fois (voir [Hero.unlockedRelicSlots]).
 *
 * Une relique ne décrit que sa **forme** : son élément, sa caractéristique, qui elle touche,
 * son effet, sa recharge. Ses dégâts ne sont écrits nulle part : [RelicBudget] les calcule.
 * Ils se comptent en coups d'épée de référence, puis suivent l'arme portée, la
 * caractéristique de la relique et les bonus « dégâts des sorts » (voir [Hero.relicDamage]).
 *
 * **N'importe quel archétype peut porter n'importe quelle relique.** La caractéristique
 * l'oriente (FOR : guerrier, DEX : voleur, INT : mage), mais les meilleurs builds se trouvent
 * en croisant — voir DONJON.md, « Le grimoire ».
 */
enum class Relic(
    @StringRes override val labelRes: Int,
    @StringRes val descRes: Int,
    val element: Element,
    /** La caractéristique qui fait ses dégâts et son DD : c'est elle qui l'oriente vers un archétype. */
    val attribute: StatType,
    val target: RelicTarget,
    val effect: RelicEffect,
    val cooldown: Int,
    /** La durée de l'effet en tours — ou, pour un effet sur l'arme, le nombre de coups. */
    val effectTurns: Int,
    val color: Int,
    val iconRow: Int, val iconCol: Int,
) : Labeled {
    // Une relique par élément et par archétype (refonte du 20/09/2026, voir DONJON.md, « Refonte du grimoire »).
    // Guerrier (CON)
    FER_ROUGE       (R.string.roguelike_relic_fer_rouge,       R.string.roguelike_relic_fer_rouge_desc,       Element.FIRE,      StatType.CON, RelicTarget.ONE,      RelicEffect.BURN,           3, 2, 0xFFB5451B.toInt(), 113, 6),
    STONESKIN       (R.string.roguelike_relic_stoneskin,       R.string.roguelike_relic_stoneskin_desc,       Element.ICE,       StatType.CON, RelicTarget.SELF,     RelicEffect.STONESKIN,      3, 2, 0xFF5F8FA8.toInt(), 132, 2),
    MARTEAU_FOUDRE  (R.string.roguelike_relic_marteau_foudre,  R.string.roguelike_relic_marteau_foudre_desc,  Element.LIGHTNING, StatType.CON, RelicTarget.ONE,      RelicEffect.PARALYZE,       3, 2, 0xFF9C7A12.toInt(), 132, 5),
    POISONED_BLADES (R.string.roguelike_relic_poisoned_blades, R.string.roguelike_relic_poisoned_blades_desc, Element.POISON,    StatType.CON, RelicTarget.SELF,     RelicEffect.ENCHANT_POISON, 3, 3, 0xFF2E7D32.toInt(), 133, 5),
    WAR_CRY         (R.string.roguelike_relic_war_cry,         R.string.roguelike_relic_war_cry_desc,         Element.PHYSICAL,  StatType.CON, RelicTarget.ALL,      RelicEffect.WARCRY,         3, 2, 0xFF8E2424.toInt(), 132, 0),
    // Voleur (DEX)
    COCKTAIL        (R.string.roguelike_relic_cocktail,        R.string.roguelike_relic_cocktail_desc,        Element.FIRE,      StatType.DEX, RelicTarget.ALL,      RelicEffect.BURN,           3, 2, 0xFFC0501E.toInt(), 113, 0),
    CRYSTALLIZE     (R.string.roguelike_relic_crystallize,     R.string.roguelike_relic_crystallize_desc,     Element.ICE,       StatType.DEX, RelicTarget.ONE,      RelicEffect.CRYSTALLIZE,    3, 0, 0xFF4A8FC0.toInt(), 134, 1),
    HASTE           (R.string.roguelike_relic_haste,           R.string.roguelike_relic_haste_desc,           Element.LIGHTNING, StatType.DEX, RelicTarget.SELF,     RelicEffect.HASTE,          3, 3, 0xFF26A69A.toInt(), 132, 7),
    VENOM           (R.string.roguelike_relic_venom,           R.string.roguelike_relic_venom_desc,           Element.POISON,    StatType.DEX, RelicTarget.ONE,      RelicEffect.POISON,         3, 4, 0xFF3E8E3A.toInt(), 133, 3),
    HUNTERS_MARK    (R.string.roguelike_relic_hunters_mark,    R.string.roguelike_relic_hunters_mark_desc,    Element.PHYSICAL,  StatType.DEX, RelicTarget.ONE,      RelicEffect.MARK,           3, 0, 0xFF9E3B3B.toInt(), 132, 11),
    // Vagabond (FOR)
    LANTERNE        (R.string.roguelike_relic_lanterne,        R.string.roguelike_relic_lanterne_desc,        Element.FIRE,      StatType.STR, RelicTarget.ONE,      RelicEffect.BURN,           3, 4, 0xFFD9822B.toInt(), 113, 6),
    SLOW            (R.string.roguelike_relic_slow,            R.string.roguelike_relic_slow_desc,            Element.ICE,       StatType.STR, RelicTarget.ONE,      RelicEffect.SLOW,           3, 3, 0xFF5C6BC0.toInt(), 132, 13),
    CHAIN_LIGHTNING (R.string.roguelike_relic_chain_lightning, R.string.roguelike_relic_chain_lightning_desc, Element.LIGHTNING, StatType.STR, RelicTarget.CHAIN,     RelicEffect.NONE,           3, 0, 0xFF7B6A12.toInt(), 132, 12),
    CHAMPIGNON      (R.string.roguelike_relic_champignon,      R.string.roguelike_relic_champignon_desc,      Element.POISON,    StatType.STR, RelicTarget.ONE,      RelicEffect.POISON,         3, 6, 0xFF6B8E23.toInt(), 133, 14),
    WHIRLWIND       (R.string.roguelike_relic_whirlwind,       R.string.roguelike_relic_whirlwind_desc,       Element.PHYSICAL,  StatType.STR, RelicTarget.ALL,      RelicEffect.NONE,           3, 0, 0xFF9A6A2E.toInt(), 133, 9),
    // Mage (SAG)
    FIREBALL        (R.string.roguelike_relic_fireball,        R.string.roguelike_relic_fireball_desc,        Element.FIRE,      StatType.WIS, RelicTarget.ONE,      RelicEffect.BURN,           3, 2, 0xFFB5451B.toInt(), 113, 6),
    FREEZING_RAIN   (R.string.roguelike_relic_freezing_rain,   R.string.roguelike_relic_freezing_rain_desc,   Element.ICE,       StatType.WIS, RelicTarget.ALL,      RelicEffect.FREEZE,         3, 1, 0xFF1E6F8C.toInt(), 132, 6),
    LIGHTNING       (R.string.roguelike_relic_lightning,       R.string.roguelike_relic_lightning_desc,       Element.LIGHTNING, StatType.WIS, RelicTarget.ONE,      RelicEffect.PARALYZE,       3, 2, 0xFF9C7A12.toInt(), 132, 5),
    ACID_FLASK      (R.string.roguelike_relic_acid_flask,      R.string.roguelike_relic_acid_flask_desc,      Element.POISON,    StatType.WIS, RelicTarget.ONE,      RelicEffect.ACID,           3, 2, 0xFF5E8C1E.toInt(), 133, 14),
    HOLY_LIGHT      (R.string.roguelike_relic_holy_light,      R.string.roguelike_relic_holy_light_desc,      Element.HOLY,      StatType.WIS, RelicTarget.ONE,      RelicEffect.BLIND,          3, 1, 0xFFB09A3A.toInt(), 113, 2),
    // Nécromancien (INT)
    METEOR          (R.string.roguelike_relic_meteor,          R.string.roguelike_relic_meteor_desc,          Element.FIRE,      StatType.INT, RelicTarget.ALL,      RelicEffect.DELAYED,        3, 2, 0xFFC0501E.toInt(), 113, 0),
    ICE_SHARD       (R.string.roguelike_relic_ice_shard,       R.string.roguelike_relic_ice_shard_desc,       Element.ICE,       StatType.INT, RelicTarget.ONE,      RelicEffect.FREEZE,         3, 1, 0xFF2F7FB5.toInt(), 113, 8),
    MAGIC_MISSILE   (R.string.roguelike_relic_magic_missile,   R.string.roguelike_relic_magic_missile_desc,   Element.LIGHTNING, StatType.INT, RelicTarget.MISSILES, RelicEffect.NONE,           3, 0, 0xFFB39A1E.toInt(), 132, 8),
    PESTE           (R.string.roguelike_relic_peste,           R.string.roguelike_relic_peste_desc,           Element.POISON,    StatType.INT, RelicTarget.ALL,      RelicEffect.POISON,         3, 3, 0xFF3E8E3A.toInt(), 133, 3),
    PONCTION        (R.string.roguelike_relic_ponction,        R.string.roguelike_relic_ponction_desc,        Element.PHYSICAL,  StatType.INT, RelicTarget.ONE,      RelicEffect.DRAIN,          3, 0, 0xFF7E2F4F.toInt(), 132, 4),
    // Neutres (hors de la grille : le Soin remplace la potion, le Sablier aide au timing)
    HEAL            (R.string.roguelike_relic_heal,            R.string.roguelike_relic_heal_desc,            Element.HOLY,      StatType.WIS, RelicTarget.SELF,     RelicEffect.HEAL,           3, 0, 0xFF43A047.toInt(), 17, 0),
    HOURGLASS       (R.string.roguelike_relic_hourglass,       R.string.roguelike_relic_hourglass_desc,       Element.PHYSICAL,  StatType.WIS, RelicTarget.SELF,     RelicEffect.HOURGLASS,      3, 3, 0xFFC9A227.toInt(), 132, 14);

    /** Un sort qui ne frappe pas : il ne fait que poser son effet. */
    val hits get() = effect.hits

    /**
     * Un sort qui est un **coup d'arme** (Brise-armure, Tourbillon) : il porte le poison des
     * Lames empoisonnées, sur chaque cible, pour une seule charge.
     */
    val weaponStrike get() = this == WHIRLWIND

    /** Dégâts directs par cible, en coups d'épée de référence (voir [RelicBudget]). */
    val minCoef get() = RelicBudget.hitCoef(this) * RelicBudget.SPREAD_MIN
    val maxCoef get() = RelicBudget.hitCoef(this) * RelicBudget.SPREAD_MAX
    /** Poison : ce qu'une dose ronge par tour, en coups d'épée. 0 pour les autres. */
    val doseCoef get() = RelicBudget.doseCoef(this)

    /** La recharge la plus courte possible, quelle que soit la SAG (voir [Hero.castCooldown]). */
    val minCooldown get() = if (effect == RelicEffect.HEAL) HEAL_MIN_COOLDOWN else 1

    companion object {
        const val BURN_SHARE       = 0.25f
        const val POISON_MAX_DOSES = 3
        /** Les doses des Lames empoisonnées et de la Fiole d'acide durent autant que celles du Venin. */
        const val ENCHANT_DOSE_TURNS = 4
        /** Coups d'arme renforcés par le Cri de guerre. */
        const val WARCRY_ATTACKS = 2
        /** Chaîne d'éclairs : chaque rebond perd 30 %. */
        const val CHAIN_FALLOFF = 0.7f
        const val MISSILE_COUNT = 3
        /** Ponction vitale : la part des dégâts infligés que le héros récupère. */
        const val DRAIN_SHARE = 0.5f
        /** Cristallisation contre un figé. */
        const val CRYSTAL_MULT = 3f
        /** Bouclier arcanique : la barrière, en part des PV max (fixe, la caractéristique ne la grossit pas). */
        const val BARRIER_SHARE = 0.20f
        /** Régénération : les PV rendus à chaque tour, en part des PV max (fixe, la caractéristique ne la grossit pas). */
        const val REGEN_SHARE = 0.07f
        /**
         * Soin : la part des PV max rendue d'un coup, 35 % (décidé par le propriétaire, 19/09/2026,
         * quand la potion a disparu). Un chiffre fixe : la SAG ne le grossit pas, elle raccourcit
         * sa recharge.
         */
        const val HEAL_SHARE = 0.35f
        /** Le Soin ne se recharge jamais en moins de 3 tours, quelle que soit la SAG. */
        const val HEAL_MIN_COOLDOWN = 3
        /** Peau de pierre : l'armure est multipliée par ça, et les épines renvoient cette part des coups. */
        const val STONESKIN_ARMOR = 4f
        const val THORNS_SHARE = 0.80f
        /** Dagues en éventail : durée du saignement posé par un critique. */
        const val FAN_BLEED_TURNS = 3
        /**
         * La rage : après ce nombre de contrôles réussis d'affilée (sans qu'il ait pu frapper
         * entre-temps), l'ennemi s'énerve pendant [RAGE_TURNS] tours. Il est alors
         * incontrôlable, son compteur descend de 2 par tour, mais il attaque avec
         * **désavantage** (deux d20, il garde le pire). Sans ça, deux reliques de contrôle
         * bloquaient un ennemi pour toujours — et c'est encore elle qui empêche les combos
         * de contrôle (Givre, Orage, Avalanche, Charme) de tourner en boucle.
         */
        const val RAGE_AFTER = 2
        const val RAGE_TURNS = 3
        /** Enragé : sa jauge se remplit deux fois plus vite. */
        const val RAGE_SPEED = 2.0
        /** Hâte : la vitesse du héros est multipliée par ça. */
        const val HASTE_SPEED = 2.0
        /** Lenteur : la vitesse de la cible est multipliée par ça. */
        const val SLOW_SPEED = 0.2
        /** Sablier : l'élan des attaques ennemies dure ça fois plus longtemps, et les fenêtres de parade s'élargissent d'autant. */
        const val HOURGLASS_SLOW = 2.0f
        /**
         * Le gel (retravaillé le 18/09/2026, le propriétaire : « la glace ne sert à rien, elle
         * décale juste le tour ») : chaque tour de gel ralentit la jauge à ×[CHILL_SPEED] pendant
         * [FREEZE_TURN_LENGTH] tours du héros (≈ 1,1 tour de retard), et la cible est engourdie
         * tout ce temps (voir [Combat.NUMB_MULT]).
         */
        const val FREEZE_TURN_LENGTH = 1.5
        const val CHILL_SPEED = 0.25
        /** Séisme : chaque ennemi recule de ça dans sa jauge (son prochain tour est repoussé). */
        const val EARTHQUAKE_PUSH = 0.25
    }
}

/**
 * Les **réactions** : un coup qui touche une cible **déjà** dans l'état d'un **autre** élément fait
 * quelque chose en plus. Chaque élément laisse un état : le feu **brûle**, la glace **fige**, la
 * foudre **paralyse**, le poison **empoisonne**, le physique/sacré **expose** (fracturé, marqué
 * ou aveuglé). Il y a une réaction pour chaque paire d'éléments, dans un sens ou dans l'autre
 * (voir DONJON.md, « Refonte du grimoire »).
 *
 * Elles marchent pour tout le monde : c'est le savoir du joueur qui paie. Rien ne les annonce, on les
 * découvre en jouant. Une réaction se lit sur l'état **d'avant** le coup, et un monstre immunisé à
 * l'élément ne réagit pas. L'attaque de base porte l'élément de l'archétype (voir
 * [Hero.attackElement]) : elle déclenche aussi les réactions, mais avec la moitié de leur bonus.
 *
 * Inspirations : les réactions de *Genshin Impact*, les surfaces de *Divinity: Original Sin 2*.
 */
enum class Reaction(@StringRes override val labelRes: Int, val color: Int) : Labeled {
    /** Feu sur un figé, ou glace sur un brûlé : ×[THERMAL_MULT], et les deux états disparaissent. */
    THERMAL_SHOCK(R.string.roguelike_reaction_thermal_shock, 0xFFFF8A65.toInt()),
    /** Feu sur un empoisonné : toutes les doses restantes tombent d'un coup, le poison est consommé. */
    EXPLOSION    (R.string.roguelike_reaction_explosion,     0xFFFFB74D.toInt()),
    /** Foudre sur un brûlé : ×[PLASMA_MULT], et la brûlure dure [PLASMA_BURN_TURNS] tours de plus. */
    PLASMA       (R.string.roguelike_reaction_plasma,        0xFFFFD54F.toInt()),
    /** Feu sur un exposé : ×[CALCINATION_MULT]. */
    CALCINATION  (R.string.roguelike_reaction_calcination,   0xFFFF7043.toInt()),
    /** Feu sur un paralysé : ×[SHORT_CIRCUIT_MULT], et la paralysie disparaît. */
    SHORT_CIRCUIT(R.string.roguelike_reaction_short_circuit, 0xFFFFCA28.toInt()),
    /** Glace sur un paralysé : il gèle aussitôt [RIGIDITY_TURNS] tour, sans jet, et la paralysie disparaît. */
    RIGIDITY     (R.string.roguelike_reaction_rigidity,      0xFFB3E5FC.toInt()),
    /** Glace sur un empoisonné : le poison restant devient un ralentissement de même durée. */
    POISON_ICE   (R.string.roguelike_reaction_poison_ice,    0xFF81D4FA.toInt()),
    /** Glace sur un exposé : il gèle aussitôt (sans jet) et son exposition dure [EXPOSED_EXTRA] tour de plus. */
    FROZEN_ARMOR (R.string.roguelike_reaction_frozen_armor,  0xFF90CAF9.toInt()),
    /** Foudre sur un empoisonné : paralysie garantie [CONVULSION_TURNS] tour, et les doses tombent une fois. */
    CONVULSIONS  (R.string.roguelike_reaction_convulsions,   0xFFDCE775.toInt()),
    /** Foudre sur un figé : le gel devient une paralysie de [ICE_SHOCK_TURNS] tours. */
    ICE_SHOCK    (R.string.roguelike_reaction_ice_shock,     0xFFE1F5FE.toInt()),
    /** Foudre sur un exposé : l'éclair saute sur les autres ennemis pour [ROD_SHARE] du coup. */
    LIGHTNING_ROD(R.string.roguelike_reaction_lightning_rod, 0xFFFFF176.toInt()),
    /** Poison sur un brûlé : la brûlure devient du poison, une dose de plus égale à celle posée. */
    SPARK        (R.string.roguelike_reaction_spark,         0xFFAED581.toInt()),
    /** Poison sur un figé : une dose de plus tout de suite, le gel reste. */
    FROSTBITE    (R.string.roguelike_reaction_frostbite,     0xFF80DEEA.toInt()),
    /** Poison sur un paralysé : la paralysie dure [NEUROTOXIN_EXTRA] tour de plus. */
    NEUROTOXIN   (R.string.roguelike_reaction_neurotoxin,    0xFF9CCC65.toInt()),
    /** Poison sur un exposé : une dose de plus, et l'exposition dure [EXPOSED_EXTRA] tour de plus. */
    INFECTION    (R.string.roguelike_reaction_infection,     0xFF7CB342.toInt()),
    /** Physique/sacré sur un figé : ×[SHATTER_MULT], le gel se brise. */
    SHATTER      (R.string.roguelike_reaction_shatter,       0xFFE1F5FE.toInt()),
    /** Physique/sacré sur un paralysé : critique garanti. */
    DEATHBLOW    (R.string.roguelike_reaction_deathblow,     0xFFEF9A9A.toInt()),
    /** Physique/sacré sur un empoisonné : le poison est nettoyé, le héros est **purifié** (armure et régénération). */
    PURIFY       (R.string.roguelike_reaction_purify,        0xFFFFF59D.toInt()),
    /** Physique/sacré sur un brûlé : la brûlure s'éteint et soigne le héros de [HOLY_FIRE_SHARE] de ce qu'elle aurait fait. */
    HOLY_FIRE    (R.string.roguelike_reaction_holy_fire,     0xFFFFE082.toInt());

    companion object {
        const val THERMAL_MULT = 1.5f
        const val PLASMA_MULT = 1.5f
        const val CALCINATION_MULT = 1.5f
        const val SHORT_CIRCUIT_MULT = 1.25f
        const val SHATTER_MULT = 2f
        const val PLASMA_BURN_TURNS = 2
        const val RIGIDITY_TURNS = 1
        const val ICE_SHOCK_TURNS = 2
        const val CONVULSION_TURNS = 1
        const val NEUROTOXIN_EXTRA = 1
        const val EXPOSED_EXTRA = 1
        const val ROD_SHARE = 0.5f
        const val HOLY_FIRE_SHARE = 0.5f
        /** Purifié : durée, armure, et régénération (part des PV max par tour et par dose nettoyée). */
        const val PURIFIED_TURNS = 3
        const val PURIFIED_ARMOR = 1.5f
        const val PURIFIED_REGEN_SHARE = 0.03f
        /** Ce qu'il reste d'un bonus quand la réaction vient de l'attaque de base (multiplicateurs, doses, dégâts). */
        const val BASE_ATTACK_PART = 0.5f
    }
}

/**
 * Les **résonances** : deux reliques précises portées ensemble. Chacune donne [BONUS] points
 * dans une caractéristique **et** un effet en plus, écrit dans [Combat] là où il joue. C'est le
 * « set de reliques » (les duos de dieux de *Hades*). On les découvre en portant la paire une
 * première fois ([Hero.discoverResonances]), ensuite l'inventaire les liste.
 *
 * Plusieurs croisent les archétypes exprès (Avalanche : une relique de mage, une de guerrier).
 * Une relique peut entrer dans plusieurs paires : avec deux emplacements, une seule est active.
 */
enum class Resonance(
    @StringRes override val labelRes: Int,
    @StringRes val descRes: Int,
    val a: Relic, val b: Relic,
    val attribute: StatType,
) : Labeled {
    /** L'Explosion met une dose de Venin à tous les autres ennemis. */
    ALCHEMY      (R.string.roguelike_resonance_alchemy,       R.string.roguelike_resonance_alchemy_desc,       Relic.FIREBALL,        Relic.VENOM,           StatType.WIS),
    /** La Cristallisation ne brise plus le gel. */
    ABSOLUTE_ZERO(R.string.roguelike_resonance_absolute_zero, R.string.roguelike_resonance_absolute_zero_desc, Relic.ICE_SHARD,       Relic.CRYSTALLIZE,     StatType.INT),
    /** 5 doses de poison au plus au lieu de 3. */
    CORROSION    (R.string.roguelike_resonance_corrosion,     R.string.roguelike_resonance_corrosion_desc,     Relic.POISONED_BLADES, Relic.ACID_FLASK,      StatType.CON),
    /** Les épines de la Peau de givre renvoient le double. */
    RAMPART      (R.string.roguelike_resonance_rampart,       R.string.roguelike_resonance_rampart_desc,       Relic.STONESKIN,       Relic.WAR_CRY,         StatType.CON);

    companion object {
        const val BONUS = 2
        fun active(worn: Collection<Relic>) = entries.filter { it.a in worn && it.b in worn }
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
 * Un sort de zone partage sa valeur entre les cibles d'un groupe moyen ([REF_GROUP],
 * [REF_CHAIN]). Sur chaque cible, l'effet se paie sur les dégâts directs :
 *  - **Feu** : la brûlure ajoute [Relic.BURN_SHARE] du coup par tour ; le coup est réduit
 *    d'autant pour que coup + brûlure fassent la part.
 *  - **Gel, paralysie** : [FREEZE_TURN_VALUE] et [PARALYSIS_TURN_VALUE] par tour d'effet,
 *    multipliés par [REF_LAND_CHANCE], la chance qu'a l'effet de prendre à équipement de
 *    l'étage. Ces valeurs sont **mesurées** (`relicsAtFixedGear`), pas déduites : le
 *    contrôle ne se laisse pas mettre en formule (voir DONJON.md, « Les reliques »).
 *  - **Poison** : [POISON_DOT_SHARE] de la part part dans la première dose (sur toute sa
 *    durée), le reste dans le coup. **Voulu** : les doses qui s'empilent dépassent le budget
 *    dans un long combat — c'est le sort des gros sacs de PV, donc des boss.
 *  - **Les autres états** : un prix par tour, ou fixe. **Provisoires**, posés à l'estime le
 *    18/09/2026 : à mesurer comme le contrôle.
 *  - **Sorts qui ne frappent pas** : toute la valeur part dans l'effet. Pour ceux qui se
 *    comptent en PV (barrière, soin, peau de pierre) ou en contrôle (charme, bombe), la
 *    quantité est une constante de [Relic] : on n'a pas encore d'échange PV ↔ coup d'épée.
 *
 * Les réactions et les résonances ne sont **pas** au budget : elles récompensent le savoir,
 * et c'est ce qu'on veut payer.
 */
object RelicBudget {

    /** La prime d'un sort, par tour de recharge, en coups d'épée : l'équivalent d'un affixe plein. */
    const val SHARE_PER_TURN = 0.12f
    const val FREEZE_TURN_VALUE = 1.0f
    const val PARALYSIS_TURN_VALUE = 0.8f
    /** Un gel prend une fois sur deux contre un monstre normal, à équipement de l'étage. */
    const val REF_LAND_CHANCE = 0.5f
    const val POISON_DOT_SHARE = 0.2f
    const val FRACTURE_TURN_VALUE = 0.06f
    const val MARK_VALUE = 0.5f
    const val WEAKEN_TURN_VALUE = 0.15f
    const val BLIND_TURN_VALUE = 0.3f
    /** Saignée : la part de la valeur qui part dans le saignement. */
    const val BLEED_SHARE = 0.12f
    /** Attaques ennemies par tour, en moyenne : c'est à chacune que le saignement ronge. */
    const val REF_ATTACKS_PER_TURN = 0.6f
    /** Ce que vaut un saignement posé par un critique des Dagues, et la chance de critique de référence. */
    const val CRIT_BLEED_VALUE = 0.5f
    const val REF_CRIT_CHANCE = 0.15f
    /** Fiole d'acide : la part de la valeur dans la dose. */
    const val ACID_DOSE_SHARE = 0.1f
    /** Cristallisation : la part du coup normal ; le reste paie le ×[Relic.CRYSTAL_MULT] contre un figé. */
    const val CRYSTAL_HIT_SHARE = 0.7f
    /** Verglas : la part de la valeur qui paie le ralentissement ; le reste est le coup. */
    const val SLOW_EFFECT_SHARE = 0.5f
    /** Ponction vitale : la part de la valeur qui paie le soin ; le reste est le coup. */
    const val DRAIN_EFFECT_SHARE = 0.25f
    /** Météore : ce qu'on gagne à attendre (il peut tomber sur un combat déjà fini). */
    const val DELAY_PREMIUM = 0.3f
    /** Taille moyenne d'un groupe à partir de l'étage 5 (1 : 50 %, 2 : 35 %, 3 : 15 %, voir [Encounters]). */
    const val REF_GROUP = 1.65f
    /** Ce que touche en tout une chaîne dans ce groupe moyen, rebonds affaiblis compris. */
    val REF_CHAIN = 0.50f * 1f +
        0.35f * (1f + Relic.CHAIN_FALLOFF) +
        0.15f * (1f + Relic.CHAIN_FALLOFF + Relic.CHAIN_FALLOFF * Relic.CHAIN_FALLOFF)
    /** L'écart des dégâts autour de la moyenne, comme l'épée (4–7 autour de 5,5). */
    const val SPREAD_MIN = 0.75f
    const val SPREAD_MAX = 1.25f

    /** Ce que vaut un lancer, en coups d'épée. */
    fun value(r: Relic) = 1f + SHARE_PER_TURN * r.cooldown

    /** Le nombre de cibles d'un lancer moyen, en « cibles pleines ». Les traits se partagent un lancer. */
    fun targets(r: Relic) = when (r.target) {
        RelicTarget.ONE, RelicTarget.SELF, RelicTarget.MISSILES -> 1f
        RelicTarget.ALL   -> REF_GROUP
        RelicTarget.CHAIN -> REF_CHAIN
    }

    /** La part du lancer qui revient à chaque cible. */
    fun share(r: Relic) = value(r) / targets(r)

    /** Ce que vaut l'effet sur une cible, en coups d'épée (négatif : ce que le sort gagne à payer un prix). */
    fun effectValue(r: Relic): Float = when (r.effect) {
        RelicEffect.NONE      -> 0f
        RelicEffect.BURN      -> hitCoef(r) * Relic.BURN_SHARE * r.effectTurns
        RelicEffect.FREEZE    -> FREEZE_TURN_VALUE * r.effectTurns * REF_LAND_CHANCE
        RelicEffect.PARALYZE  -> PARALYSIS_TURN_VALUE * r.effectTurns * REF_LAND_CHANCE
        RelicEffect.POISON    -> share(r) * POISON_DOT_SHARE
        RelicEffect.FRACTURE  -> FRACTURE_TURN_VALUE * r.effectTurns
        RelicEffect.MARK      -> MARK_VALUE
        RelicEffect.WARCRY    -> WEAKEN_TURN_VALUE * r.effectTurns
        RelicEffect.BLEED     -> share(r) * BLEED_SHARE
        RelicEffect.BLEED_ON_CRIT -> CRIT_BLEED_VALUE * REF_CRIT_CHANCE
        RelicEffect.ACID      -> FRACTURE_TURN_VALUE * r.effectTurns + share(r) * ACID_DOSE_SHARE
        RelicEffect.CRYSTALLIZE -> share(r) * (1f - CRYSTAL_HIT_SHARE)
        RelicEffect.DELAYED   -> -share(r) * DELAY_PREMIUM
        RelicEffect.BLIND     -> BLIND_TURN_VALUE * r.effectTurns
        RelicEffect.SLOW      -> share(r) * SLOW_EFFECT_SHARE
        RelicEffect.DRAIN     -> share(r) * DRAIN_EFFECT_SHARE
        RelicEffect.ENCHANT_POISON, RelicEffect.SMOKE, RelicEffect.BARRIER,
        RelicEffect.REGEN, RelicEffect.HEAL, RelicEffect.STONESKIN, RelicEffect.CHARM,
        RelicEffect.HASTE, RelicEffect.HOURGLASS -> share(r)
    }

    /** Coup direct moyen sur chaque cible, en coups d'épée. */
    fun hitCoef(r: Relic): Float = when {
        !r.hits -> 0f
        r.effect == RelicEffect.BURN   -> share(r) / (1f + Relic.BURN_SHARE * r.effectTurns)
        r.effect == RelicEffect.POISON -> share(r) * (1f - POISON_DOT_SHARE)
        else -> share(r) - effectValue(r)
    }

    /** Ce qu'une dose ronge par tour, en coups d'épée. */
    fun doseCoef(r: Relic): Float = when (r.effect) {
        RelicEffect.POISON -> share(r) * POISON_DOT_SHARE / r.effectTurns
        RelicEffect.ENCHANT_POISON -> share(r) / enchantDoseTicks(r)
        RelicEffect.ACID -> share(r) * ACID_DOSE_SHARE / Relic.ENCHANT_DOSE_TURNS
        else -> 0f
    }

    /** Ce que le saignement ronge à chaque attaque de la cible, en coups d'épée. */
    fun bleedCoef(r: Relic): Float = when (r.effect) {
        RelicEffect.BLEED -> share(r) * BLEED_SHARE / (r.effectTurns * REF_ATTACKS_PER_TURN)
        RelicEffect.BLEED_ON_CRIT -> CRIT_BLEED_VALUE / (Relic.FAN_BLEED_TURNS * REF_ATTACKS_PER_TURN)
        else -> 0f
    }

    /**
     * Les « doses-tours » qu'on paie à un enchantement de poison : ceux qui tombent **pendant**
     * l'enchantement, un coup d'arme par tour. Trois coups : 1 + 2 + 3 = 6. Les doses qui
     * rongent encore après ne sont pas comptées : un combat ordinaire est fini avant (mesuré :
     * en les comptant, les Lames faisaient moins bien qu'aucun sort). Comme le Venin, elles
     * dépassent donc le budget dans un long combat — contre un boss.
     */
    fun enchantDoseTicks(r: Relic): Int =
        (1..r.effectTurns).sumOf { it.coerceAtMost(Relic.POISON_MAX_DOSES) }

    /**
     * Cri de guerre : ce qui reste après l'affaiblissement du groupe moyen, réparti sur les
     * coups renforcés. En part d'un coup d'arme : 0,55 = +55 %.
     */
    fun empowerBonus(r: Relic) = ((value(r) - effectValue(r) * REF_GROUP) / Relic.WARCRY_ATTACKS).coerceAtLeast(0f)
}

// ─── Combat ─────────────────────────────────────────────────────────────────────

/** Qualité d'un geste en rythme : parade ou frappe. */
enum class Timing { MISS, GOOD, PERFECT }

enum class CombatPhase { PLAYER_TURN, ENEMY_TURN, VICTORY, DEFEAT }

/**
 * [affinity] : ce que la cible pense de l'élément du sort (NORMAL pour l'épée).
 * [save] : le jet de sauvegarde contre l'effet, s'il y en a eu un. [enraged] : ce sort l'a
 * fait enrager. [reactions] : les réactions déclenchées sur elle ; [explosion] : les dégâts de
 * l'Explosion (ou de la Purification), comptés à part du coup. [noDamage] : un sort qui ne
 * frappe pas (Cri de guerre).
 */
data class HitResult(
    val target: Int, val damage: Int, val crit: Boolean, val killed: Boolean,
    val affinity: Affinity = Affinity.NORMAL, val save: SaveRoll? = null, val enraged: Boolean = false,
    val reactions: List<Reaction> = emptyList(), val explosion: Int = 0, val noDamage: Boolean = false,
    /** Nécromancien : ce que les pantins ont ajouté à ce coup, en écho. */
    val echo: Int = 0,
)
/** Un sort lancé : une touche par ennemi atteint, la cible visée en premier. Vide pour un sort sur soi. */
data class CastResult(val relic: Relic, val hits: List<HitResult>) {
    val main get() = hits.firstOrNull()
}
/** Dégâts d'un effet qui dure (brûlure, poison au tour de la victime ; l'Aube à la fin du tour du héros). */
data class DotTick(val enemy: Int, val damage: Int, val killed: Boolean, val element: Element)
/** Un ennemi arrêté par un effet ce tour-ci : figé par la glace, ou attaque perdue par la foudre. */
data class StatusStop(val enemy: Int, val element: Element)
/** Un jet de sauvegarde lancé pendant le tour ennemi (paralysie). */
data class EnemySave(val enemy: Int, val save: SaveRoll)
/**
 * Le début du tour d'**un** ennemi : ce que ses effets lui rongent, s'il est arrêté (gel,
 * paralysie), son jet de paralysie, s'il enrage, puis s'il frappe ([attackers] : lui, ou
 * personne).
 */
data class EnemyTurnStart(
    val ticks: List<DotTick>, val attackers: List<Int>, val stopped: List<StatusStop>,
    val saves: List<EnemySave> = emptyList(), val enraged: List<Int> = emptyList(),
)
/**
 * La fin du tour du héros, juste après son action : le Météore qui tombe, les PV rendus par
 * la Régénération, et ce que l'Aube brûle. Ce sont des durées **du héros** : elles se
 * comptent à ses tours.
 */
data class HeroTurnEnd(val meteor: List<HitResult> = emptyList(), val healed: Int = 0, val ticks: List<DotTick> = emptyList()) {
    val isEmpty get() = meteor.isEmpty() && healed == 0 && ticks.isEmpty()
}
/**
 * Un tour à venir, dans la barre d'ordre : [actor] vaut [Combat.HERO], [Combat.METEOR] ou
 * l'indice d'un ennemi. [frozen] : un ennemi qui perdra ce tour-là (gelé).
 */
data class TurnSlot(val actor: Int, val frozen: Boolean = false)
/**
 * [missed] : le jet d'attaque n'a pas atteint la CA du héros. [imageHit] : il a frappé un
 * double de l'Image miroir. [blocked] (guerrier, bouclier), [dodged] + [counter] (voleur),
 * [recovered] (mage) : ce que la parade parfaite a donné selon l'archétype.
 *
 * Le grimoire : [bleed] ce que l'attaquant a saigné en frappant ([bledOut] : il en est mort,
 * le coup ne part pas) ; [charmHit] le coup qu'il a porté à un allié, charmé ([charmed] seul :
 * il a perdu son attaque) ; [absorbed] ce que la barrière a pris ; [thorns] ce que les épines
 * lui ont renvoyé.
 */
data class EnemyStrike(
    val enemy: Int, val damage: Int, val parry: Timing,
    val missed: Boolean = false, val imageHit: Boolean = false,
    val blocked: Boolean = false, val dodged: Boolean = false, val counter: HitResult? = null,
    val recovered: Boolean = false,
    val bleed: Int = 0, val bledOut: Boolean = false,
    val charmed: Boolean = false, val charmHit: HitResult? = null,
    val absorbed: Int = 0, val thorns: Int = 0, val thornsKilled: Boolean = false,
    /** Nécromancien : ce que ses pantins ont pris à sa place. */
    val puppetAbsorbed: Int = 0,
)
data class CombatRewards(val gold: Int, val equipment: List<Equipment>)

/**
 * Un combat au tour par tour, façon **CTB de FFX** (voir DONJON.md, « La jauge ») : le héros
 * et chaque ennemi ont une **jauge** qui se remplit à leur vitesse ; pleine, c'est leur tour.
 * Le temps ne s'écoule **pas** en continu : il est figé pendant les actions et pendant que le
 * joueur choisit. Entre deux actions, [advance] fait avancer toutes les jauges jusqu'à la
 * prochaine pleine, et donne la main à son propriétaire. Une action vide la jauge de celui
 * qui la fait (une action pleine : de 1).
 *
 * L'unité de temps est le **tour du héros à vitesse normale** : sa jauge gagne 1 par unité.
 * Un ennemi de cadence N gagne 1/N : il agit tous les N tours du héros.
 *
 * Déroulé :
 *   tour du héros   : [attack], [castRelic] ou le Spécial ; à la fin, le
 *                     Météore et la Régénération ([lastHeroTurnEnd])
 *   tour d'un ennemi ([actingEnemy]) : [startEnemyTurn] (brûlure, poison, gel, paralysie),
 *                     [resolveStrike] s'il frappe, puis [endEnemyTurn] (ses états perdent
 *                     un tour)
 *
 * Le moteur ne connaît pas le temps réel : l'écran mesure le geste du joueur (parade, swipe)
 * et le lui transmet sous forme de [Timing].
 *
 * Tous les dégâts infligés à un ennemi passent par [wound] : la fracture et la marque qui
 * saute y sont réglées une fois pour toutes.
 */
class Combat(
    val hero: Hero,
    val floor: Int,
    val enemies: List<Enemy>,
    /** Pris en embuscade : la jauge du héros part vide, les monstres frappent avant qu'il agisse. */
    val ambush: Boolean,
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

        /** Coup mortel : une cible sous ce seuil de PV est exposée. */
        const val DEADLY_HP_THRESHOLD = 0.30f
        /** Coup mortel sur une cible exposée : critique garanti, et ce bonus au multiplicateur. */
        const val DEADLY_CRIT_BONUS = 1f
        const val MIRROR_IMAGES = 3
        /** Soin en part des PV max à chaque tour du mage tant qu'un double est debout. */
        const val MIRROR_REGEN_SHARE = 0.04f
        /** Vagabond : l'Enchaînement frappe deux fois ; une roulade parfaite relève le prochain coup de cette part. */
        const val CHAIN_HITS = 2
        const val ROLL_BONUS = 0.5f
        /** Nécromancien : nombre de pantins invoqués, PV d'un pantin, part des coups gardée par le héros, écho. */
        const val PUPPETS = 2
        const val PUPPET_HP_SHARE = 0.15f
        const val PUPPET_SELF_SHARE = 0.60f
        const val ECHO_SHARE = 0.25f
        const val PUPPET_SUMMON_HIT_SHARE = 0.40f
        const val ECHO_GOOD_FACTOR = 0.5f
        /** L'arc du voleur : une cible sous cette part de ses PV est exposée (au lieu de [DEADLY_HP_THRESHOLD]). */
        const val BOW_EXPOSE_THRESHOLD = 0.60f
        /** Le grimoire du nécromancien : un pantin de plus. */
        const val GRIMOIRE_PUPPETS = 1
        /** Le grimoire relève aussi l'écho de chaque pantin de cette part. */
        const val GRIMOIRE_ECHO_BONUS = 1.0f
        /** La lanterne du vagabond : le second coup de l'Enchaînement frappe de cette part en plus. */
        const val LANTERN_CHAIN_BONUS = 1.0f
        /** Sa parade parfaite soigne les pantins de cette part de leurs PV. */
        const val PUPPET_PARRY_HEAL = 0.5f

        // Les états partagés (voir [RelicEffect])
        /** Fracturé : il prend ce multiple de tous les dégâts. */
        const val FRACTURE_MULT = 1.25f
        /** Coup de grâce porté par l'attaque de base : un critique plus probable, pas garanti. */
        const val DEATHBLOW_BASE_CRIT = 0.3f
        /** Affaibli : ses coups font ce multiple. */
        const val WEAKEN_MULT = 0.7f
        /** Gelé : engourdi, ses coups font ce multiple (cumulé avec l'affaiblissement). */
        const val NUMB_MULT = 0.7f
        /** Marqué : les critiques contre lui gagnent ce bonus au multiplicateur. */
        const val MARK_CRIT_BONUS = 0.5f
        /** Corrosion : doses de poison au plus. */
        const val CORROSION_MAX_DOSES = 5
        /** Guerrier en Garde : chaque coup reçu (bloqué ou encaissé) renvoie cette part du coup brut. */
        const val GUARD_THORNS_SHARE = 0.5f
        /** Guerrier, blocage parfait au bouclier : le coup de bouclier renvoie cette part. */
        const val BLOCK_THORNS_SHARE = 0.3f
        /** Le même blocage sans bouclier : il marche, mais renvoie moins. */
        const val BARE_BLOCK_THORNS_SHARE = 0.2f

        // La jauge
        /** Dans la barre d'ordre et [actingEnemy] : le héros, et le Météore qui tombe. */
        const val HERO = -1
        const val METEOR = -2
        /** Ce que coûte une action pleine : toute la jauge. */
        const val FULL_ACTION = 1.0
        /** Ce que coûte une action qui ne frappe pas (sort de soutien, Garde, Image miroir). */
        const val SUPPORT_ACTION = 0.5
        /** Deux jauges pleines à moins de ça l'une de l'autre sont pleines en même temps. */
        private const val TIME_EPSILON = 1e-9
    }

    /** Guerrier : en garde jusqu'à son prochain tour (l'écran double la fenêtre de parade). */
    var guarding = false
        private set
    /** Mage : doubles de l'Image miroir encore debout. */
    var mirrorImages = 0
        private set
    /** Vagabond : sa roulade parfaite relève son prochain coup d'arme. */
    var rollReady = false
        private set
    /** Nécromancien : les PV de chaque pantin (0 : tombé). Vide pour les autres archétypes. */
    private val puppetHpList = mutableListOf<Int>()
    val puppetHp: List<Int> get() = puppetHpList
    /** Les PV pleins d'un pantin. */
    val puppetMaxHp get() = (hero.maxHp * PUPPET_HP_SHARE).roundToInt().coerceAtLeast(1)

    // ── Ce que les reliques posent sur le héros, pour ce combat ──
    /** Cri de guerre : coups d'arme renforcés qui restent, et de combien (en part d'un coup). */
    var empoweredAttacks = 0
        private set
    private var empowerBonus = 0f
    /** Lames empoisonnées : coups d'arme qui empoisonnent encore, et la dose qu'ils posent. */
    var poisonedBlades = 0
        private set
    private var bladeDose = 0
    /** Bombe fumigène : le prochain coup d'arme est une attaque sournoise. */
    var ambushReady = false
        private set
    /** Bouclier arcanique : PV de barrière. [barrierFresh] : posée ce tour, elle peut rendre une recharge. */
    var barrier = 0
        private set
    private var barrierFresh = false
    /** Régénération : tours et PV par tour. */
    var regenTurns = 0
        private set
    private var regenAmount = 0
    /** Peau de pierre : armure doublée et épines. */
    var stoneskinTurns = 0
    /** Purifié (réaction Purification) : tours restants, l'armure monte. */
    var purifiedTurns = 0
        private set
    /** Hâte : tours du héros qui restent à vitesse ×[Relic.HASTE_SPEED]. */
    var hasteTurns = 0
        private set
    /** Sablier : attaques ennemies qui arriveront encore au ralenti. */
    var hourglassStrikes = 0
        private set
    /** Météore : tours avant l'impact (0 : rien en l'air), et le geste du lancer. */
    var meteorTurns = 0
        private set
    private var meteorTiming = Timing.MISS

    /** À qui la main : fixé par [advance], dès la construction. */
    var phase = CombatPhase.PLAYER_TURN
        private set

    /** Les recharges vivent sur le héros : elles continuent d'un combat à l'autre. */
    val relicCooldowns get() = hero.relicCooldowns
    var rewards: CombatRewards? = null
        private set

    // ── La jauge ──
    /** La jauge du héros : pleine au départ, vide s'il est pris en embuscade. */
    var heroGauge = if (ambush) 0.0 else 1.0
        private set
    /** Ce que la jauge du héros gagne par unité de temps : 1, c'est la vitesse normale (voir [Hero.speed]), et la Hâte. */
    val heroRate get() = heroRateWith(hasteTurns > 0)
    private fun heroRateWith(haste: Boolean) = hero.speed.toDouble() * if (haste) Relic.HASTE_SPEED else 1.0
    /** L'ennemi dont c'est le tour ([HERO] pendant le tour du héros). */
    var actingEnemy = HERO
        private set
    /** Ce que la dernière action du héros a déclenché en fin de tour (Météore, Régénération). */
    var lastHeroTurnEnd = HeroTurnEnd()
        private set
    /**
     * Le premier tour du héros, s'il n'est pas pris en embuscade, ne fait pas passer de temps :
     * les recharges n'avancent pas. En embuscade, son tour « manqué » compte, comme avant.
     */
    private var upkeepDue = ambush

    init {
        hero.floor = floor
        advance()
    }

    fun aliveIndices() = enemies.indices.filter { enemies[it].alive }
    /**
     * Le Météore ne se relance pas tant qu'il est en l'air : chaque lancer remettait son compte
     * à zéro, et avec une recharge d'un tour (beaucoup de SAG) il ne tombait jamais.
     */
    fun canCast(relic: Relic) = phase == CombatPhase.PLAYER_TURN && relic in hero.relicSlots && hero.relicCooldown(relic) == 0 &&
        !(relic.effect == RelicEffect.DELAYED && meteorTurns > 0)
    fun canUseSpecial() = phase == CombatPhase.PLAYER_TURN && hero.archetype != null && hero.specialCooldown == 0

    // ── Ce que coûte chaque action, en jauge (1 : toute la jauge) ──
    // Une action qui ne frappe pas coûte une demi-jauge : on rejoue plus vite (voir DONJON.md,
    // « Étape 3 »). Les effets n'ont pas été réduits pour autant : c'est un bonus, mesuré.
    fun attackCost() = FULL_ACTION
    fun relicCost(relic: Relic) = if (relic.hits) FULL_ACTION else SUPPORT_ACTION
    /** La Garde et l'Image miroir ne frappent pas ; le Coup mortel, si. */
    fun specialCost() = if (hero.archetype == Archetype.ROGUE || hero.archetype == Archetype.VAGABOND) FULL_ACTION else SUPPORT_ACTION

    /**
     * Empoisonnée, figée, paralysée, aveuglée, charmée, marquée ou bien entamée : le voleur y
     * plante son coup mortel.
     */
    fun isExposed(e: Enemy) = e.alive && (e.poisonTurns > 0 || e.frozen || e.paralyzedTurns > 0 || e.marked ||
        e.blindedTurns > 0 || e.charmed ||
        e.hp < e.maxHp * (if (hero.classOffhand(Archetype.ROGUE)) BOW_EXPOSE_THRESHOLD else DEADLY_HP_THRESHOLD))

    // ── Tour du joueur ──────────────────────────────────────────────────────────

    private fun weaponRoll() = rng.nextInt(hero.weaponMin, hero.weaponMax.coerceAtLeast(hero.weaponMin) + 1).toFloat()

    fun attack(target: Int, timing: Timing): HitResult {
        check(phase == CombatPhase.PLAYER_TURN)
        val result = weaponHit(target, timing)
        val echo = echo(target, timing)
        afterPlayerAction(attackCost())
        return result.copy(echo = echo)
    }

    /**
     * Un coup d'arme : l'attaque, le Coup mortel, la riposte du voleur. C'est ici que jouent
     * le Cri de guerre (coup renforcé), les Lames empoisonnées (une dose par coup) et la Bombe
     * fumigène (attaque sournoise : critique garanti). [lifeSteal] : le vol de vie ne joue pas
     * sur la riposte.
     */
    private fun weaponHit(target: Int, timing: Timing, forceCrit: Boolean = false, critBonus: Float = 0f, lifeSteal: Boolean = true,
                          damageMult: Float = 1f): HitResult {
        var raw = weaponRoll() * damageMult
        if (rollReady) { rollReady = false; raw *= 1f + ROLL_BONUS }
        if (empoweredAttacks > 0) { empoweredAttacks--; raw *= 1f + empowerBonus }
        val sneak = ambushReady
        ambushReady = false
        // L'élément de l'archétype (bonne arme, main gauche de classe) : l'attaque déclenche les réactions, à moitié
        val el = hero.attackElement
        val foe = enemies[target]
        val rx = if (el == null || !foe.alive || foe.type.affinity(el) == Affinity.IMMUNE) null else react(el, target, Reaction.BASE_ATTACK_PART)
        if (rx != null) raw *= rx.mult
        var result = hit(target, raw, timing, forceCrit = forceCrit || sneak || rx?.forceCrit == true, critBonus = critBonus + (rx?.critBonus ?: 0f))
        if (rx != null) {
            if (foe.alive) rx.after.forEach { it(result.damage) }
            hero.discover(foe.type, el!!, rx.reactions)
            result = result.copy(killed = !foe.alive, reactions = rx.reactions, explosion = rx.extra)
        }
        if (lifeSteal && hero.lifeSteal > 0f) hero.heal((result.damage * hero.lifeSteal).roundToInt())
        if (poisonedBlades > 0) {
            poisonedBlades--
            bladePoison(enemies[target])
        }
        return result
    }

    /** Une dose des Lames empoisonnées sur [e], si le poison le touche. */
    private fun bladePoison(e: Enemy) {
        val affinity = e.type.affinity(Element.POISON)
        if (e.alive && affinity != Affinity.IMMUNE)
            addDose(e, (bladeDose * affinity.damageMult).roundToInt().coerceAtLeast(1), Relic.ENCHANT_DOSE_TURNS)
    }

    /**
     * Lance [relic]. [target] est la cible visée : la seule pour un sort à cible unique, la
     * première pour une chaîne ou une zone, ignorée pour un sort sur soi.
     */
    fun castRelic(relic: Relic, target: Int, timing: Timing): CastResult {
        check(canCast(relic))
        val touched = when {
            relic.effect == RelicEffect.DELAYED -> emptyList()
            relic.target == RelicTarget.SELF -> emptyList()
            relic.target == RelicTarget.ONE -> listOf(target)
            relic.target == RelicTarget.MISSILES -> List(Relic.MISSILE_COUNT) { -1 }   // tirés au fur et à mesure
            else -> listOf(target) + aliveIndices().filter { it != target }
        }
        val hits = mutableListOf<HitResult>()
        var mult = if (relic.target == RelicTarget.MISSILES) 1f / Relic.MISSILE_COUNT else 1f
        for (planned in touched) {
            // Un trait vise un ennemi encore debout, au hasard
            val i = if (planned >= 0) planned else aliveIndices().randomOrNull(rng) ?: break
            if (!enemies[i].alive) continue
            hits += relicHit(relic, i, timing, mult)
            if (relic.target == RelicTarget.CHAIN) mult *= Relic.CHAIN_FALLOFF
        }
        // Un sort qui est un coup d'arme ne consomme qu'une charge des Lames, quel que soit le nombre de cibles
        if (relic.weaponStrike && poisonedBlades > 0) poisonedBlades--
        applySelfEffect(relic, timing)
        relicCooldowns[relic] = hero.castCooldown(relic)
        afterPlayerAction(relicCost(relic))
        return CastResult(relic, hits)
    }

    /** Les effets qui se posent sur le héros (ou sur le combat) plutôt que sur une cible. */
    private fun applySelfEffect(relic: Relic, timing: Timing) {
        when (relic.effect) {
            RelicEffect.WARCRY -> {
                empoweredAttacks = Relic.WARCRY_ATTACKS
                empowerBonus = RelicBudget.empowerBonus(relic) * hero.relicMult(relic)
            }
            RelicEffect.ENCHANT_POISON -> {
                poisonedBlades = relic.effectTurns
                bladeDose = hero.poisonDose(relic)
            }
            RelicEffect.HEAL -> hero.heal(hero.relicAmount(relic))
            RelicEffect.STONESKIN -> stoneskinTurns = relic.effectTurns
            RelicEffect.HASTE -> hasteTurns = relic.effectTurns
            RelicEffect.HOURGLASS -> hourglassStrikes = relic.effectTurns
            RelicEffect.DELAYED -> {
                meteorTurns = relic.effectTurns
                meteorTiming = timing
            }
            else -> {}
        }
    }

    /** Un sort sur une cible : les réactions (lues avant), le coup, puis l'effet. */
    private fun relicHit(relic: Relic, i: Int, timing: Timing, mult0: Float): HitResult {
        val e = enemies[i]
        val affinity = e.type.affinity(relic.element)
        val immune = affinity == Affinity.IMMUNE
        val rx = if (immune) Reacting() else react(relic.element, i, 1f)
        val reactions = rx.reactions
        var mult = affinity.damageMult * mult0 * rx.mult
        // La Cristallisation : énorme contre un figé, et le gel se brise (sauf avec Zéro absolu)
        if (relic.effect == RelicEffect.CRYSTALLIZE && e.frozen) {
            mult *= Relic.CRYSTAL_MULT
            if (Resonance.ABSOLUTE_ZERO !in hero.resonances) thaw(e)
        }
        val result = if (relic.hits) {
            val (lo, hi) = hero.relicDamage(relic)
            hit(i, rng.nextInt(lo, hi + 1) * mult, timing, allowZero = immune, forceCrit = rx.forceCrit)
        } else HitResult(i, 0, crit = false, killed = false, noDamage = true)
        if (relic.weaponStrike && poisonedBlades > 0) bladePoison(e)
        val (save, enraged) = if (e.alive && !immune) applyEffect(relic, e, result, timing, reactions) else null to false
        // Après l'effet : les réactions qui convertissent un état (elles ont besoin de ce que le sort vient de poser)
        if (e.alive) rx.after.forEach { it(result.damage) }
        hero.discover(e.type, relic.element, reactions)
        return result.copy(killed = !e.alive, affinity = affinity, save = save, enraged = enraged,
            reactions = reactions, explosion = rx.extra)
    }

    /** Ce que les états d'une cible font à un coup : les réactions déclenchées, le multiplicateur, et ce qui se joue après le coup. */
    private class Reacting {
        val reactions = mutableListOf<Reaction>()
        var mult = 1f
        var forceCrit = false
        var critBonus = 0f
        /** Les dégâts en plus des réactions qui frappent (Explosion, Convulsions, Paratonnerre). */
        var extra = 0
        /** Ce qui se joue après le coup et son effet, avec les dégâts du coup. */
        val after = mutableListOf<(Int) -> Unit>()
    }

    /** Les états d'une cible, lus **avant** le coup. */
    private class TargetStates(e: Enemy) {
        val burning = e.burnTurns > 0
        val frozen = e.frozen
        val paralyzed = e.paralyzedTurns > 0
        val poisoned = e.poisonTurns > 0
        val exposed = e.fracturedTurns > 0 || e.blindedTurns > 0 || e.marked
    }

    /** Une dose de poison de plus : de la taille de celle qui vient d'être posée, à défaut celle du Venin. */
    private fun extraDose(e: Enemy) {
        val size = if (e.poisonDoseDamage > 0) e.poisonDoseDamage else hero.poisonDose(Relic.VENOM)
        addDose(e, size, maxOf(e.poisonTurns, Relic.ENCHANT_DOSE_TURNS))
    }

    /** Son exposition dure un peu plus longtemps. */
    private fun extendExposure(e: Enemy) {
        if (e.fracturedTurns > 0) e.fracturedTurns += Reaction.EXPOSED_EXTRA
        if (e.blindedTurns > 0) e.blindedTurns += Reaction.EXPOSED_EXTRA
    }

    /**
     * Les réactions d'un coup d'élément [el] sur [i]. [part] : 1 pour un sort, [Reaction.BASE_ATTACK_PART]
     * pour l'attaque de base (la moitié du bonus). Le physique et le sacré ne font qu'un.
     */
    private fun react(el: Element, i: Int, part: Float): Reacting {
        val e = enemies[i]
        val st = TargetStates(e)
        val r = Reacting()
        fun bonus(m: Float) = 1f + (m - 1f) * part
        when (el) {
            Element.FIRE -> {
                if (st.frozen) { thaw(e); r.mult *= bonus(Reaction.THERMAL_MULT); r.reactions += Reaction.THERMAL_SHOCK }
                if (st.poisoned) { r.reactions += Reaction.EXPLOSION; r.after += { r.extra += explode(i, part) } }
                if (st.exposed) { r.mult *= bonus(Reaction.CALCINATION_MULT); r.reactions += Reaction.CALCINATION }
                if (st.paralyzed) { e.paralyzedTurns = 0; r.mult *= bonus(Reaction.SHORT_CIRCUIT_MULT); r.reactions += Reaction.SHORT_CIRCUIT }
            }
            Element.ICE -> {
                if (st.burning) {
                    e.burnTurns = 0; e.burnDamage = 0
                    r.mult *= bonus(Reaction.THERMAL_MULT); r.reactions += Reaction.THERMAL_SHOCK
                }
                if (st.paralyzed) {
                    r.reactions += Reaction.RIGIDITY
                    r.after += { e.paralyzedTurns = 0; freezeFor(e, Reaction.RIGIDITY_TURNS) }
                }
                if (st.poisoned) {
                    r.reactions += Reaction.POISON_ICE
                    e.slowTurns = maxOf(e.slowTurns, e.poisonTurns)
                    consumePoison(e)
                }
                if (st.exposed) {
                    r.reactions += Reaction.FROZEN_ARMOR
                    r.after += { freezeFor(e, Reaction.RIGIDITY_TURNS); extendExposure(e) }
                }
            }
            Element.LIGHTNING -> {
                if (st.burning) {
                    r.mult *= bonus(Reaction.PLASMA_MULT); r.reactions += Reaction.PLASMA
                    e.burnTurns += Reaction.PLASMA_BURN_TURNS
                }
                if (st.poisoned) {
                    r.reactions += Reaction.CONVULSIONS
                    r.after += {
                        paralyzeFor(e, Reaction.CONVULSION_TURNS)
                        r.extra += wound(i, e.poisonDoses * e.poisonDoseDamage * part)
                    }
                }
                if (st.frozen) {
                    r.reactions += Reaction.ICE_SHOCK
                    r.after += { thaw(e); paralyzeFor(e, Reaction.ICE_SHOCK_TURNS) }
                }
                if (st.exposed) {
                    r.reactions += Reaction.LIGHTNING_ROD
                    r.after += { dmg ->
                        for (j in aliveIndices()) if (j != i) r.extra += wound(j, dmg * Reaction.ROD_SHARE * part)
                    }
                }
            }
            Element.POISON -> {
                if (st.burning) {
                    r.reactions += Reaction.SPARK
                    r.after += { e.burnTurns = 0; e.burnDamage = 0; extraDose(e) }
                }
                if (st.frozen) { r.reactions += Reaction.FROSTBITE; r.after += { extraDose(e) } }
                if (st.paralyzed) { r.reactions += Reaction.NEUROTOXIN; r.after += { e.paralyzedTurns += Reaction.NEUROTOXIN_EXTRA } }
                if (st.exposed) { r.reactions += Reaction.INFECTION; r.after += { extraDose(e); extendExposure(e) } }
            }
            Element.PHYSICAL, Element.HOLY -> {
                if (st.frozen) { thaw(e); r.mult *= bonus(Reaction.SHATTER_MULT); r.reactions += Reaction.SHATTER }
                if (st.paralyzed) {
                    r.reactions += Reaction.DEATHBLOW
                    if (part >= 1f) r.forceCrit = true else r.critBonus = DEATHBLOW_BASE_CRIT
                }
                if (st.poisoned) {
                    r.reactions += Reaction.PURIFY
                    val doses = e.poisonDoses
                    consumePoison(e)
                    purified(doses, part)
                }
                if (st.burning) {
                    r.reactions += Reaction.HOLY_FIRE
                    val left = e.burnDamage * e.burnTurns
                    e.burnTurns = 0; e.burnDamage = 0
                    hero.heal((left * Reaction.HOLY_FIRE_SHARE * part).roundToInt())
                }
            }
        }
        return r
    }

    /** Purification : le héros est purifié, son armure monte et il récupère des PV chaque tour (par dose nettoyée). */
    private fun purified(doses: Int, part: Float) {
        purifiedTurns = Reaction.PURIFIED_TURNS
        regenTurns = maxOf(regenTurns, Reaction.PURIFIED_TURNS)
        regenAmount = maxOf(regenAmount, (hero.maxHp * Reaction.PURIFIED_REGEN_SHARE * doses * part).roundToInt().coerceAtLeast(1))
    }

    /**
     * Le jet de sauvegarde de [e] contre un contrôle. Immunisé ou enragé, il le réussit
     * d'office. Trempé, il a un malus. [timing] : le geste du joueur au lancer (voir
     * [SpellSave.GOOD_STRIKE_DC]).
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
        thaw(e)
        e.paralyzedTurns = 0
        e.charmed = false
        e.slowTurns = 0
        return true
    }

    private fun thaw(e: Enemy) { e.frozenTime = 0.0; e.thawing = false }

    /** Pose l'effet du sort ([result] : le coup, dont la brûlure prend sa part). Renvoie le jet de sauvegarde (s'il y en a un) et la rage. */
    private fun applyEffect(relic: Relic, e: Enemy, result: HitResult, timing: Timing, reactions: MutableList<Reaction>): Pair<SaveRoll?, Boolean> {
        val dc = hero.spellDc(relic)
        val affinityMult = e.type.affinity(relic.element).damageMult
        when (relic.effect) {
            RelicEffect.BURN -> {
                e.burnTurns  = relic.effectTurns
                e.burnDamage = (result.damage * Relic.BURN_SHARE).roundToInt().coerceAtLeast(1)
            }
            RelicEffect.FREEZE -> return freeze(e, dc, timing, relic.effectTurns)
            // Pas de jet au lancer : chaque attaque qui tombe pendant la paralysie en demandera un
            RelicEffect.PARALYZE -> paralyze(e, dc, timing, relic.effectTurns)
            RelicEffect.POISON -> addDose(e, (hero.poisonDose(relic) * affinityMult).roundToInt().coerceAtLeast(1), relic.effectTurns)
            RelicEffect.FRACTURE -> e.fracturedTurns = maxOf(e.fracturedTurns, relic.effectTurns)
            RelicEffect.MARK -> { enemies.forEach { it.marked = false }; e.marked = true }
            RelicEffect.WARCRY -> e.weakenedTurns = maxOf(e.weakenedTurns, relic.effectTurns)
            RelicEffect.BLEED -> bleed(e, hero.bleedDamage(relic), relic.effectTurns)
            RelicEffect.BLEED_ON_CRIT -> if (result.crit) bleed(e, hero.bleedDamage(relic), Relic.FAN_BLEED_TURNS)
            RelicEffect.ACID -> {
                addDose(e, (hero.poisonDose(relic) * affinityMult).roundToInt().coerceAtLeast(1), Relic.ENCHANT_DOSE_TURNS)
                e.fracturedTurns = maxOf(e.fracturedTurns, relic.effectTurns)
            }
            RelicEffect.BLIND -> e.blindedTurns = maxOf(e.blindedTurns, relic.effectTurns)
            RelicEffect.SLOW -> {
                val save = rollSave(e, relic.element, dc, timing)
                if (save.saved) return save to false
                e.slowTurns = maxOf(e.slowTurns, relic.effectTurns)
                return save to controlled(e)
            }
            RelicEffect.DRAIN -> hero.heal((result.damage * Relic.DRAIN_SHARE).roundToInt())
            else -> {}
        }
        return null to false
    }

    /** Un gel : jet de sauvegarde. */
    private fun freeze(e: Enemy, dc: Int, timing: Timing, turns: Int): Pair<SaveRoll?, Boolean> {
        val save = rollSave(e, Element.ICE, dc, timing)
        if (save.saved) return save to false
        freezeFor(e, turns)
        return save to controlled(e)
    }

    /** Un gel posé d'office (les réactions n'ont pas de jet). Un enragé n'y est pas sensible. */
    private fun freezeFor(e: Enemy, turns: Int) {
        if (e.enraged) return
        e.frozenTime = maxOf(e.frozenTime, turns * Relic.FREEZE_TURN_LENGTH)
        e.thawing = false
    }

    /** Une paralysie : chaque attaque qui tombe demandera un jet. */
    private fun paralyze(e: Enemy, dc: Int, timing: Timing, turns: Int) {
        if (e.enraged) return
        e.paralyzedTurns = maxOf(e.paralyzedTurns, turns)
        e.paralysisTiming = timing
        e.paralysisDc = dc
    }

    /** Une paralysie posée d'office par une réaction. */
    private fun paralyzeFor(e: Enemy, turns: Int) {
        if (e.enraged) return
        e.paralyzedTurns = maxOf(e.paralyzedTurns, turns)
        e.paralysisTiming = Timing.MISS
    }

    private fun bleed(e: Enemy, damage: Int, turns: Int) {
        e.bleedTurns = maxOf(e.bleedTurns, turns)
        e.bleedDamage = maxOf(e.bleedDamage, damage)
    }

    private fun addDose(e: Enemy, doseDamage: Int, turns: Int) {
        val max = if (Resonance.CORROSION in hero.resonances) CORROSION_MAX_DOSES else Relic.POISON_MAX_DOSES
        e.poisonDoses = (e.poisonDoses + 1).coerceAtMost(max)
        e.poisonTurns = maxOf(e.poisonTurns, turns)
        e.poisonDoseDamage = maxOf(e.poisonDoseDamage, doseDamage)
    }

    /** Ce que rongeraient encore les doses de [e], et le poison est consommé. */
    private fun consumePoison(e: Enemy): Int {
        val left = e.poisonDoses * e.poisonDoseDamage * e.poisonTurns
        e.poisonDoses = 0; e.poisonTurns = 0; e.poisonDoseDamage = 0
        return left
    }

    /** Explosion : les doses restantes tombent d'un coup ([part] : 1 pour un sort, moins pour l'attaque de base). Avec l'Alchimie, elles éclaboussent les autres. */
    private fun explode(i: Int, part: Float = 1f): Int {
        val dmg = wound(i, consumePoison(enemies[i]) * part)
        if (Resonance.ALCHEMY in hero.resonances) for (j in aliveIndices()) {
            if (j == i) continue
            val affinity = enemies[j].type.affinity(Element.POISON)
            if (affinity == Affinity.IMMUNE) continue
            addDose(enemies[j], (hero.poisonDose(Relic.VENOM) * affinity.damageMult).roundToInt().coerceAtLeast(1), Relic.VENOM.effectTurns)
        }
        return dmg
    }

    // ── Le « Spécial » de l'archétype ───────────────────────────────────────────

    /** Le bonus du Coup mortel au multiplicateur de critique : relevé par le set d'isotope léger. */
    private fun deadlyCritBonus() = if (hero.specialBoosted(Archetype.ROGUE)) IsotopeSets.DEADLY_CRIT_BONUS else DEADLY_CRIT_BONUS

    private fun spendSpecial() {
        val base = if (hero.setArchetype != null) IsotopeSets.SPECIAL_COOLDOWN else Hero.SPECIAL_COOLDOWN
        hero.specialCooldown = hero.spellCooldown(base)
    }

    /** Guerrier : on passe son tour en garde (parade plus large, et chaque coup reçu est renvoyé en partie, voir [retaliate]). */
    fun guard() {
        check(canUseSpecial() && hero.archetype == Archetype.WARRIOR)
        guarding = true
        spendSpecial()
        afterPlayerAction(specialCost())
    }

    /** Mage : trois doubles qui prennent les coups à sa place, façon D&D. */
    fun mirrorImage() {
        check(canUseSpecial() && hero.archetype == Archetype.MAGE)
        mirrorImages = if (hero.specialBoosted(Archetype.MAGE)) IsotopeSets.MIRROR_IMAGES else MIRROR_IMAGES
        spendSpecial()
        afterPlayerAction(specialCost())
    }

    /**
     * Vagabond : [CHAIN_HITS] coups d'arme d'affilée, un geste par coup, pour une seule action. Le set les
     * renforce de [IsotopeSets.CHAIN_DAMAGE_BONUS]. Si le premier coup tue, le second cherche une autre cible.
     */
    fun chain(target: Int, first: Timing, second: Timing): List<HitResult> {
        check(canUseSpecial() && hero.archetype == Archetype.VAGABOND)
        val bonus = if (hero.specialBoosted(Archetype.VAGABOND)) 1f + IsotopeSets.CHAIN_DAMAGE_BONUS else 1f
        val hits = mutableListOf<HitResult>()
        val lantern = hero.classOffhand(Archetype.VAGABOND)
        for ((i, timing) in listOf(first, second).take(CHAIN_HITS).withIndex()) {
            val at = if (enemies[target].alive) target else aliveIndices().firstOrNull() ?: break
            hits += weaponHit(at, timing, damageMult = bonus * if (lantern && i > 0) 1f + LANTERN_CHAIN_BONUS else 1f)
        }
        spendSpecial()
        afterPlayerAction(specialCost())
        return hits
    }

    /** Nécromancien : invoque ou relève ses pantins ; chacun frappe aussitôt à 40 % des dégâts moyens d'arme. */
    fun recallPuppets() {
        check(canUseSpecial() && hero.archetype == Archetype.NECROMANCER)
        val count = (if (hero.specialBoosted(Archetype.NECROMANCER)) IsotopeSets.PUPPETS else PUPPETS) +
            if (hero.classOffhand(Archetype.NECROMANCER)) GRIMOIRE_PUPPETS else 0
        puppetHpList.clear()
        repeat(count) { puppetHpList += puppetMaxHp }
        val targets = aliveIndices()
        if (targets.isNotEmpty()) {
            val avg = (hero.weaponMin + hero.weaponMax) / 2f
            for (i in puppetHpList.indices) {
                val at = targets[i % targets.size].takeIf { enemies[it].alive } ?: aliveIndices().firstOrNull() ?: break
                wound(at, avg * PUPPET_SUMMON_HIT_SHARE)
            }
        }
        spendSpecial()
        afterPlayerAction(specialCost())
    }

    /**
     * L'écho des pantins : à chaque coup d'arme du nécromancien, chaque pantin debout frappe la même cible d'une part
     * de ses dégâts d'arme. La qualité du geste décide : parfait = tout, bon = la moitié, raté = rien.
     */
    private fun echo(target: Int, timing: Timing): Int {
        val factor = when (timing) { Timing.PERFECT -> 1f; Timing.GOOD -> ECHO_GOOD_FACTOR; Timing.MISS -> 0f }
        if (factor <= 0f || puppetHpList.none { it > 0 }) return 0
        val avg = (hero.weaponMin + hero.weaponMax) / 2f
        val echoMult = if (hero.classOffhand(Archetype.NECROMANCER)) 1f + GRIMOIRE_ECHO_BONUS else 1f
        var total = 0
        for (hp in puppetHpList) {
            if (hp <= 0) continue
            val at = if (enemies[target].alive) target else aliveIndices().firstOrNull() ?: return total
            total += wound(at, avg * ECHO_SHARE * factor * echoMult)
        }
        return total
    }

    /**
     * Les pantins encaissent : ils prennent la part [PUPPET_SELF_SHARE] en moins de chaque coup, le nécromancien
     * garde le reste ; ce qu'un pantin ne peut pas absorber retombe sur lui. Renvoie ce qu'il subit.
     */
    private fun throughPuppets(dmg: Int): Int {
        if (puppetHpList.none { it > 0 }) return dmg
        val mine = (dmg * PUPPET_SELF_SHARE).roundToInt().coerceAtLeast(1)
        var left = dmg - mine
        for (i in puppetHpList.indices) {
            if (left <= 0) break
            val take = minOf(puppetHpList[i], left)
            puppetHpList[i] -= take
            left -= take
        }
        return mine + left
    }

    /**
     * Voleur : un coup d'arme. Sur une cible exposée ([isExposed]) ou après une Bombe
     * fumigène, critique garanti et multiplicateur relevé de [DEADLY_CRIT_BONUS] — l'attaque
     * sournoise de D&D, et de quoi achever un petit monstre déjà entamé. Sinon, un coup normal.
     */
    fun deadlyStrike(target: Int, timing: Timing): HitResult {
        check(canUseSpecial() && hero.archetype == Archetype.ROGUE)
        val exposed = isExposed(enemies[target]) || ambushReady
        val result = weaponHit(target, timing, forceCrit = exposed, critBonus = if (exposed) deadlyCritBonus() else 0f)
        spendSpecial()
        afterPlayerAction(specialCost())
        return result
    }

    private fun hit(
        target: Int, raw: Float, timing: Timing, allowZero: Boolean = false,
        forceCrit: Boolean = false, critBonus: Float = 0f,
    ): HitResult {
        val e = enemies[target]
        require(e.alive)
        val bonus = when (timing) { Timing.MISS -> 0f; Timing.GOOD -> STRIKE_GOOD; Timing.PERFECT -> STRIKE_PERFECT }
        val crit  = forceCrit || rng.nextFloat() < (hero.critChance(floor) + bonus).coerceAtMost(0.95f)
        val critMult = hero.critMult + critBonus + if (e.marked) MARK_CRIT_BONUS else 0f
        val dmg = wound(target, if (crit) raw * critMult else raw, allowZero)
        return HitResult(target, dmg, crit, !e.alive)
    }

    /**
     * **Tous** les dégâts infligés à un ennemi passent par ici : coups, brûlure, poison,
     * explosion, saignement, épines, coup d'un allié charmé. La fracture s'y applique, et la
     * marque d'un mort saute sur un survivant.
     */
    private fun wound(i: Int, amount: Float, allowZero: Boolean = false): Int {
        val e = enemies[i]
        val mult = if (e.fracturedTurns > 0) FRACTURE_MULT else 1f
        val dmg = (amount * mult).roundToInt().coerceAtLeast(if (allowZero) 0 else 1)
        e.hp = (e.hp - dmg).coerceAtLeast(0)
        if (!e.alive && e.marked) {
            e.marked = false
            aliveIndices().firstOrNull()?.let { enemies[it].marked = true }
        }
        return dmg
    }

    /**
     * L'action du héros est faite : la fin de son tour (Météore, Régénération), puis sa jauge
     * se vide de [cost] et le temps avance jusqu'au prochain tour.
     */
    private fun afterPlayerAction(cost: Double) {
        lastHeroTurnEnd = HeroTurnEnd()
        if (aliveIndices().isEmpty()) { phase = win(); return }
        lastHeroTurnEnd = endHeroTurn()
        if (aliveIndices().isEmpty()) { phase = win(); return }
        heroGauge -= cost
        advance()
    }

    /**
     * La fin du tour du héros : le Météore tombe s'il est l'heure, la Régénération soigne (et
     * l'Aube brûle les morts-vivants). Ce sont des durées du héros, comptées à ses tours.
     */
    private fun endHeroTurn(): HeroTurnEnd {
        val meteor = mutableListOf<HitResult>()
        if (meteorTurns > 0 && --meteorTurns == 0)
            for (i in aliveIndices()) meteor += relicHit(Relic.METEOR, i, meteorTiming, 1f)
        var healed = 0
        val ticks = mutableListOf<DotTick>()
        if (regenTurns > 0) {
            regenTurns--
            val before = hero.hp
            hero.heal(regenAmount)
            healed = hero.hp - before
        }
        if (hero.archetype == Archetype.MAGE && mirrorImages > 0) {
            val before = hero.hp
            hero.heal((hero.maxHp * MIRROR_REGEN_SHARE).roundToInt().coerceAtLeast(1))
            healed += hero.hp - before
        }
        return HeroTurnEnd(meteor, healed, ticks)
    }

    /**
     * Le début du tour du héros : ce qui dure jusqu'à son prochain tour s'arrête (Garde, Peau
     * de pierre), le Bouclier arcanique qui a tenu rend une recharge, et les recharges
     * avancent d'un tour — elles se comptent **aux tours du héros**.
     */
    private fun beginHeroTurn() {
        if (!upkeepDue) { upkeepDue = true; return }
        if (stoneskinTurns > 0) stoneskinTurns--
        if (purifiedTurns > 0) purifiedTurns--
        if (hasteTurns > 0) hasteTurns--
        hero.tickRelics()
        guarding = false
    }

    // ── La jauge ────────────────────────────────────────────────────────────────

    /** Le temps qu'il faut à la jauge d'un ennemi pour être pleine, en tours du héros (le gel la ralentit d'abord). */
    fun timeUntilTurn(i: Int): Double = fillTime(enemies[i].gauge, enemies[i].rate, enemies[i].frozenTime)

    /**
     * Le temps pour remplir une jauge à [gauge] qui gagne [rate] par unité de temps, ralentie à
     * ×[Relic.CHILL_SPEED] pendant les [chill] premières unités (le gel).
     */
    private fun fillTime(gauge: Double, rate: Double, chill: Double): Double {
        val need = 1.0 - gauge
        if (need <= 0.0) return 0.0
        val chilledGain = rate * Relic.CHILL_SPEED * chill
        return if (need <= chilledGain) need / (rate * Relic.CHILL_SPEED) else chill + (need - chilledGain) / rate
    }

    /** Ce que gagne en [dt] une jauge de vitesse [rate], dont les [chill] premières unités sont gelées. */
    private fun gainOver(rate: Double, chill: Double, dt: Double): Double {
        val chilled = minOf(chill, dt)
        return rate * (Relic.CHILL_SPEED * chilled + (dt - chilled))
    }

    /**
     * Le nombre de tours du héros (à sa vitesse) avant que l'ennemi [i] agisse (1 : juste après
     * le prochain tour du héros). C'est l'ancien compte à rebours, que le bot de simulation lit encore.
     */
    fun roundsUntilTurn(i: Int): Int = kotlin.math.ceil(timeUntilTurn(i) * heroRate - TIME_EPSILON).toInt().coerceAtLeast(0)

    /**
     * Le temps avance jusqu'à la prochaine jauge pleine, et la main passe à son propriétaire.
     * À égalité, les ennemis passent avant le héros (ils attendaient, lui vient d'agir ; sans
     * ça, une action à demi-jauge le faisait rejouer avant même qu'un rat ait frappé), et entre
     * eux dans l'ordre.
     */
    private fun advance() {
        var next = HERO
        var dt = ((1.0 - heroGauge) / heroRate).coerceAtLeast(0.0)
        for (i in aliveIndices()) {
            val t = timeUntilTurn(i)
            if (t < dt - TIME_EPSILON || (next == HERO && t <= dt + TIME_EPSILON)) { next = i; dt = t }
        }
        heroGauge += heroRate * dt
        for (i in aliveIndices()) {
            val e = enemies[i]
            // Gelé, sa jauge avance au ralenti ; quand le gel finit, la glace reste jusqu'à son tour
            e.gauge += gainOver(e.rate, e.frozenTime, dt)
            if (e.frozenTime > 0) {
                e.frozenTime -= minOf(e.frozenTime, dt)
                if (e.frozenTime <= TIME_EPSILON) { e.frozenTime = 0.0; e.thawing = true }
            }
        }
        if (next == HERO) {
            heroGauge = 1.0
            actingEnemy = HERO
            phase = CombatPhase.PLAYER_TURN
            beginHeroTurn()
        } else {
            enemies[next].gauge = 1.0
            actingEnemy = next
            phase = CombatPhase.ENEMY_TURN
        }
    }

    /**
     * Les [count] prochains tours, dans l'ordre : ce qu'affiche la barre d'ordre. Le tour en
     * cours n'y est pas. [heroCost] : ce que coûtera l'action que le héros choisit (s'il a la
     * main) — c'est ce qui montre où tomberait son prochain tour. Ensuite, on suppose des
     * actions pleines. Le gel, la fin de la rage, de la Lenteur et de la Hâte sont suivis ; le
     * prochain tour d'un ennemi encore pris dans la glace est marqué [TurnSlot.frozen] ; le
     * Météore apparaît à la fin du tour du héros où il tombe.
     */
    fun forecast(count: Int, heroCost: Double = FULL_ACTION): List<TurnSlot> {
        val alive = aliveIndices()
        val gauge = DoubleArray(enemies.size) { enemies[it].gauge }
        val rage = IntArray(enemies.size) { enemies[it].rageTurns }
        val slow = IntArray(enemies.size) { enemies[it].slowTurns }
        val frozenTime = DoubleArray(enemies.size) { enemies[it].frozenTime }
        val icy = BooleanArray(enemies.size) { enemies[it].frozen && it != actingEnemy }
        fun rate(i: Int) = enemies[i].rateWith(rage[i] > 0, slow[i] > 0)
        var haste = hasteTurns
        fun heroRate() = heroRateWith(haste > 0)
        var hg = heroGauge
        var meteor = meteorTurns
        val slots = mutableListOf<TurnSlot>()
        // Le tour en cours se termine
        if (phase == CombatPhase.PLAYER_TURN) {
            hg -= heroCost
            if (meteor > 0 && --meteor == 0) slots += TurnSlot(METEOR)
        } else if (actingEnemy >= 0) {
            gauge[actingEnemy] -= FULL_ACTION
            if (slow[actingEnemy] > 0) slow[actingEnemy]--
        }
        while (slots.size < count && alive.isNotEmpty()) {
            var next = HERO
            var dt = ((1.0 - hg) / heroRate()).coerceAtLeast(0.0)
            for (i in alive) {
                val t = fillTime(gauge[i], rate(i), frozenTime[i])
                if (t < dt - TIME_EPSILON || (next == HERO && t <= dt + TIME_EPSILON)) { next = i; dt = t }
            }
            hg += heroRate() * dt
            for (i in alive) {
                gauge[i] += gainOver(rate(i), frozenTime[i], dt)
                frozenTime[i] -= minOf(frozenTime[i], dt)
            }
            if (next == HERO) {
                slots += TurnSlot(HERO)
                hg = 1.0 - FULL_ACTION
                if (haste > 0) haste--
                if (meteor > 0 && --meteor == 0) slots += TurnSlot(METEOR)
            } else {
                slots += TurnSlot(next, frozen = icy[next])
                icy[next] = false
                if (rage[next] > 0) rage[next]--
                if (slow[next] > 0) slow[next]--
                gauge[next] = 1.0 - FULL_ACTION
            }
        }
        return slots.take(count)
    }

    // ── Tour des ennemis ────────────────────────────────────────────────────────

    /**
     * Début du tour de [actingEnemy], et tout se compte **à son tour à lui** : la brûlure et le
     * poison rongent, la rage perd un tour. Le gel a ralenti sa jauge (voir [advance]) ; s'il
     * est encore pris dans la glace, son coup est engourdi, et la glace qui restait sur lui
     * ([Enemy.thawing]) fond à la fin de ce tour.
     * **Paralysé**, son attaque demande un jet de sauvegarde : raté, elle est perdue.
     */
    fun startEnemyTurn(): EnemyTurnStart {
        check(phase == CombatPhase.ENEMY_TURN)
        val i = actingEnemy
        val e = enemies[i]
        val ticks = mutableListOf<DotTick>()
        if (e.burnTurns > 0) {
            val dmg = wound(i, e.burnDamage.toFloat())
            e.burnTurns--
            ticks += DotTick(i, dmg, !e.alive, Element.FIRE)
        }
        if (e.alive && e.poisonTurns > 0) {
            val dmg = wound(i, (e.poisonDoses * e.poisonDoseDamage).toFloat())
            if (--e.poisonTurns == 0) { e.poisonDoses = 0; e.poisonDoseDamage = 0 }
            ticks += DotTick(i, dmg, !e.alive, Element.POISON)
        }
        if (!e.alive) {
            if (aliveIndices().isEmpty()) phase = win()
            return EnemyTurnStart(ticks, emptyList(), emptyList())
        }

        if (e.enraged) e.rageTurns--
        if (e.paralyzedTurns > 0) {
            e.paralyzedTurns--
            val save = rollSave(e, Element.LIGHTNING, e.paralysisDc, e.paralysisTiming)
            if (!save.saved) {
                val enraged = if (controlled(e)) listOf(i) else emptyList()
                return EnemyTurnStart(ticks, emptyList(), listOf(StatusStop(i, Element.LIGHTNING)), listOf(EnemySave(i, save)), enraged)
            }
            return EnemyTurnStart(ticks, listOf(i), emptyList(), listOf(EnemySave(i, save)))
        }
        return EnemyTurnStart(ticks, listOf(i), emptyList())
    }

    fun resolveStrike(enemyIndex: Int, parry: Timing): EnemyStrike {
        check(phase == CombatPhase.ENEMY_TURN)
        val e = enemies[enemyIndex]
        if (!e.alive) return EnemyStrike(enemyIndex, 0, parry, missed = true)
        // Le Sablier : cette attaque est arrivée au ralenti (l'écran a élargi la parade)
        if (hourglassStrikes > 0) hourglassStrikes--
        // Saignement : chaque attaque rouvre la plaie. S'il en meurt, le coup ne part pas
        val bled = if (e.bleedTurns > 0) wound(enemyIndex, e.bleedDamage.toFloat()) else 0
        if (!e.alive) {
            if (aliveIndices().isEmpty()) phase = win()
            return EnemyStrike(enemyIndex, 0, parry, missed = true, bleed = bled, bledOut = true)
        }
        // Charmé : il frappe un allié (seul, il perd son attaque), puis le charme se dissipe
        if (e.charmed) {
            e.charmed = false
            val ally = aliveIndices().filter { it != enemyIndex }.randomOrNull(rng)
            val charmHit = ally?.let {
                val dmg = wound(it, e.damage * (0.85f + rng.nextFloat() * 0.30f))
                HitResult(it, dmg, crit = false, killed = !enemies[it].alive)
            }
            if (aliveIndices().isEmpty()) phase = win()
            return EnemyStrike(enemyIndex, 0, parry, bleed = bled, charmed = true, charmHit = charmHit)
        }
        // Il a pu frapper : la série de contrôles qui mène à la rage repart de zéro. Pas avant
        // le charme : une attaque détournée est un contrôle, sinon le Charme bloquerait sans fin
        e.controlStreak = 0
        // Image miroir, comme dans D&D : avant son jet d'attaque, un d20 dit s'il vise un
        // double — 6+ avec trois doubles, 8+ avec deux, 11+ avec le dernier
        if (mirrorImages > 0) {
            val need = when (mirrorImages) { 4 -> 5; 3 -> 6; 2 -> 8; else -> 11 }
            if (attackDie() >= need) { mirrorImages--; return EnemyStrike(enemyIndex, 0, parry, imageHit = true, bleed = bled) }
        }
        // Enragé ou aveuglé : il attaque avec désavantage (deux d20, le pire gardé)
        val roll = if (e.enraged || e.blindedTurns > 0) minOf(attackDie(), attackDie()) else attackDie()
        val hits = roll >= ArmorClass.ALWAYS_HIT_FROM || (roll != 1 &&roll + ArmorClass.monsterAttack(floor) >= hero.armorClass)
        if (!hits) return EnemyStrike(enemyIndex, 0, parry, missed = true, bleed = bled)

        // Le coup brut, avant parade et armure : c'est sur lui que se calcule ce qu'on renvoie
        // Affaibli, et engourdi par la glace : ses coups font moins mal
        val weakened = (if (e.weakenedTurns > 0) WEAKEN_MULT else 1f) * (if (e.frozen) NUMB_MULT else 1f)
        val spread = 0.85f + rng.nextFloat() * 0.30f
        val blow = e.damage * spread * weakened * ArmorClass.DAMAGE_COMPENSATION

        // La parade parfaite, selon l'archétype
        var recovered = false
        if (parry == Timing.PERFECT) when (hero.archetype) {
            // Le guerrier bloque : rien ne passe, et le coup renvoie. Sans bouclier, ça marche, mais il renvoie moins
            Archetype.WARRIOR -> {
                val thorns = retaliate(enemyIndex, blow, blocked = true)
                return EnemyStrike(enemyIndex, 0, parry, blocked = true, bleed = bled,
                    thorns = thorns, thornsKilled = thorns > 0 && !e.alive)
            }
            Archetype.ROGUE -> {
                val counter = weaponHit(enemyIndex, Timing.MISS, lifeSteal = false)
                if (aliveIndices().isEmpty()) phase = win()
                return EnemyStrike(enemyIndex, 0, parry, dodged = true, counter = counter, bleed = bled)
            }
            Archetype.MAGE -> { hero.tickRelics(1, includeSpecial = false); recovered = true }
            // La roulade : il évite le coup, sans riposte gratuite, et son prochain coup frappe plus fort
            Archetype.VAGABOND -> {
                rollReady = true
                return EnemyStrike(enemyIndex, 0, parry, dodged = true, bleed = bled)
            }
            // Ses pantins reprennent des forces
            Archetype.NECROMANCER -> for (i in puppetHpList.indices)
                puppetHpList[i] = (puppetHpList[i] + puppetMaxHp * PUPPET_PARRY_HEAL).roundToInt().coerceAtMost(puppetMaxHp)
            null -> {}
        }

        val parryMult = when (parry) { Timing.MISS -> 1f; Timing.GOOD -> PARRY_GOOD_MULT; Timing.PERFECT -> PARRY_PERFECT_MULT }
        val armorMult = maxOf(if (stoneskinTurns > 0) Relic.STONESKIN_ARMOR else 1f, if (purifiedTurns > 0) Reaction.PURIFIED_ARMOR else 1f)
        val puppetsBefore = puppetHpList.sum()
        var dmg = throughPuppets(hero.mitigate(blow * parryMult, floor, armorMult).roundToInt().coerceAtLeast(1))
        val puppetTaken = puppetsBefore - puppetHpList.sum()
        // Le Bouclier arcanique prend d'abord
        val absorbed = minOf(barrier, dmg)
        barrier -= absorbed
        dmg -= absorbed
        hero.hp = (hero.hp - dmg).coerceAtLeast(0)
        val thorns = retaliate(enemyIndex, blow, blocked = false)   // s'il en meurt, retaliate a déjà donné la victoire
        if (hero.hp == 0) phase = CombatPhase.DEFEAT
        return EnemyStrike(enemyIndex, dmg, parry, recovered = recovered, bleed = bled,
            absorbed = absorbed, thorns = thorns, thornsKilled = thorns > 0 && !e.alive, puppetAbsorbed = puppetTaken)
    }

    /**
     * Ce que le héros renvoie à celui qui l'a frappé, en part du coup **brut** [blow] (avant
     * parade et armure : plus le monstre frappe fort, plus il se fait mal, et la grosse armure
     * du guerrier n'affaiblit pas ce qu'il renvoie). Les sources s'additionnent :
     *  - la Peau de pierre ([Relic.THORNS_SHARE], doublée par Rempart) ;
     *  - la **Garde** du guerrier, la Représaille ([GUARD_THORNS_SHARE]) ;
     *  - le **blocage parfait** au bouclier, le coup de bouclier ([BLOCK_THORNS_SHARE]).
     * Idée du propriétaire (18/09/2026) : le guerrier qui se protège beaucoup doit faire mal en
     * encaissant, sinon il ne tue rien. Renvoie les dégâts infligés.
     */
    private fun retaliate(enemyIndex: Int, blow: Float, blocked: Boolean): Int {
        var share = 0f
        if (stoneskinTurns > 0) share += Relic.THORNS_SHARE * if (Resonance.RAMPART in hero.resonances) 2f else 1f
        if (guarding) share += if (hero.specialBoosted(Archetype.WARRIOR)) IsotopeSets.GUARD_THORNS_SHARE else GUARD_THORNS_SHARE
        if (blocked) share += if (hero.hasShield) BLOCK_THORNS_SHARE else BARE_BLOCK_THORNS_SHARE
        if (share <= 0f) return 0
        val dmg = wound(enemyIndex, blow * share)
        if (hero.hp > 0 && aliveIndices().isEmpty()) phase = win()
        return dmg
    }

    /**
     * Fin du tour de [actingEnemy] : ses états qui durent perdent un tour (les siens seulement),
     * sa jauge se vide, et le temps avance jusqu'au prochain tour.
     */
    fun endEnemyTurn() {
        if (phase != CombatPhase.ENEMY_TURN) return
        val e = enemies[actingEnemy]
        if (e.fracturedTurns > 0) e.fracturedTurns--
        if (e.weakenedTurns > 0) e.weakenedTurns--
        if (e.blindedTurns > 0) e.blindedTurns--
        if (e.bleedTurns > 0 && --e.bleedTurns == 0) e.bleedDamage = 0
        if (e.slowTurns > 0) e.slowTurns--
        e.thawing = false
        e.gauge -= FULL_ACTION
        advance()
    }

    // ── Victoire ────────────────────────────────────────────────────────────────

    private fun win(): CombatPhase {
        var gold = 0
        val loot = mutableListOf<Equipment>()
        val floorGold = 1f + 0.10f * (floor - 1)
        for (e in enemies) {
            gold += (rng.nextInt(e.type.goldMin, e.type.goldMax + 1) * floorGold * hero.goldMult).roundToInt()
            LootSystem.tryDrop(floor, hero.nextLootId, rng)?.let { loot += it; hero.nextLootId++ }
        }
        rewards = CombatRewards(gold, loot)
        return CombatPhase.VICTORY
    }
}
