package com.Atom2Universe.app.games.roguelike

import android.content.Context
import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import kotlin.math.roundToInt
import kotlin.random.Random

/** Implémenté par les enums dont le nom affiché passe par les ressources strings. */
interface Labeled {
    @get:StringRes val labelRes: Int
}

// ─── Stats ──────────────────────────────────────────────────────────────────────

/** Les six caractéristiques D&D, plus les bonus que donnent les objets. */
enum class StatType(@StringRes override val labelRes: Int, val isPercent: Boolean) : Labeled {
    STR          (R.string.roguelike_attr_str,             false),
    DEX          (R.string.roguelike_attr_dex,             false),
    CON          (R.string.roguelike_attr_con,             false),
    INT          (R.string.roguelike_attr_int,             false),
    WIS          (R.string.roguelike_attr_wis,             false),
    CHA          (R.string.roguelike_attr_cha,             false),
    ARMOR        (R.string.roguelike_stattype_armor,       false),
    MAX_HP       (R.string.roguelike_stattype_maxhp,       false),
    WEAPON_DMG   (R.string.roguelike_stattype_weapon_dmg,  false),
    SPELL_DMG    (R.string.roguelike_stattype_spell_dmg,   true),
    CRIT_CHANCE  (R.string.roguelike_stattype_crit_chance, true),
    CRIT_DAMAGE  (R.string.roguelike_stattype_crit_damage, true),
    LIFE_STEAL   (R.string.roguelike_stattype_life_steal,  true),
    /** Vitesse : la jauge du héros se remplit plus vite (+10 % = un tour de plus tous les dix). */
    SPEED        (R.string.roguelike_stattype_speed,       true);

    companion object {
        val ATTRIBUTES = listOf(STR, DEX, CON, INT, WIS, CHA)
    }
}

/**
 * Une ligne de stat sur un objet. [tier] vaut 0 pour les stats de base (implicites) et
 * 1 à 8 pour un affixe : c'est son palier de puissance, voir [AffixBudget].
 */
data class StatRoll(val type: StatType, val value: Float, val tier: Int = 0) {
    fun display(context: Context): String {
        val label = context.getString(type.labelRes)
        val line = if (type.isPercent)
            context.getString(R.string.roguelike_stat_roll_percent, (value * 100).roundToInt(), label)
        else
            context.getString(R.string.roguelike_stat_roll_flat, value.roundToInt(), label)
        return if (tier > 0) context.getString(R.string.roguelike_stat_roll_tier, line, tier) else line
    }
}

// ─── Rareté ────────────────────────────────────────────────────────────────────

/** Légendaires et sets viendront plus tard (voir DONJON.md). */
enum class Rarity(
    @StringRes override val labelRes: Int, val colorArgb: Int,
    val minAffixes: Int, val maxAffixes: Int, val sellMult: Float,
) : Labeled {
    NORMAL(R.string.roguelike_rarity_normal, 0xFFBDBDBD.toInt(), 0, 0, 1f),
    MAGIC (R.string.roguelike_rarity_magic,  0xFF6E9BFF.toInt(), 1, 2, 2f),
    RARE  (R.string.roguelike_rarity_rare,   0xFFFFD54F.toInt(), 3, 4, 4f),
}

// ─── Emplacements ──────────────────────────────────────────────────────────────

enum class EquipSlot(@StringRes override val labelRes: Int) : Labeled {
    WEAPON (R.string.roguelike_slot_weapon), OFFHAND(R.string.roguelike_slot_offhand),
    HELMET(R.string.roguelike_slot_helmet), CHEST(R.string.roguelike_slot_chest), BOOTS(R.string.roguelike_slot_boots),
    AMULET(R.string.roguelike_slot_amulet), RING(R.string.roguelike_slot_ring)
}

// ─── Matières ──────────────────────────────────────────────────────────────────

/**
 * Une seule échelle de matières, 5 tiers chacune. Le tier 5 d'une matière a la même
 * puissance que le tier 1 de la suivante : on trouve du Cuir 5 aux mêmes étages que du
 * Cuivre 1. Noms provisoires. [weaponRes] : le nom pour les armes et bijoux (une épée
 * « de cuir » n'existe pas, le premier palier est en bois).
 */
enum class Material(@StringRes val armorRes: Int, @StringRes val weaponRes: Int) {
    LEATHER   (R.string.roguelike_mat_leather,    R.string.roguelike_mat_wood),
    COPPER    (R.string.roguelike_mat_copper,     R.string.roguelike_mat_copper),
    BRONZE    (R.string.roguelike_mat_bronze,     R.string.roguelike_mat_bronze),
    IRON      (R.string.roguelike_mat_iron,       R.string.roguelike_mat_iron),
    STEEL     (R.string.roguelike_mat_steel,      R.string.roguelike_mat_steel),
    MITHRIL   (R.string.roguelike_mat_mithril,    R.string.roguelike_mat_mithril),
    OBSIDIAN  (R.string.roguelike_mat_obsidian,   R.string.roguelike_mat_obsidian),
    ADAMANTIUM(R.string.roguelike_mat_adamantium, R.string.roguelike_mat_adamantium),
    ORICHALCUM(R.string.roguelike_mat_orichalcum, R.string.roguelike_mat_orichalcum),
    ASTRALITE (R.string.roguelike_mat_astralite,  R.string.roguelike_mat_astralite);

    companion object {
        const val TIERS = 5
        /** Puissance gagnée d'une matière à la suivante (4 : le tier 5 chevauche le tier 1 suivant). */
        const val STEP = 4
        val MAX_POWER = (entries.size - 1) * STEP + TIERS
    }
}

// ─── Bases d'objets ────────────────────────────────────────────────────────────

/**
 * Le type d'objet fixe sa base. Une arme donne toujours la caractéristique de son type
 * ([attribute]) ; une pièce d'armure ou un bijou tire une caractéristique au hasard
 * ([attribute] = null).
 */
enum class ItemBase(
    @StringRes val nounRes: Int,
    val slot: EquipSlot,
    val attribute: StatType?,
    val damageMult: Float,
    val armorBase: Float,
    val spellBonus: Float,
    val usesWeaponMaterial: Boolean,
) {
    SWORD  (R.string.roguelike_base_sword,   EquipSlot.WEAPON,  StatType.STR, 1.00f, 0f, 0f,     true),
    AXE    (R.string.roguelike_base_axe,     EquipSlot.WEAPON,  StatType.STR, 1.25f, 0f, 0f,     true),
    DAGGER (R.string.roguelike_base_dagger,  EquipSlot.WEAPON,  StatType.DEX, 0.80f, 0f, 0f,     true),
    MACE   (R.string.roguelike_base_mace,    EquipSlot.WEAPON,  StatType.CON, 1.00f, 0f, 0f,     true),
    STAFF  (R.string.roguelike_base_staff,   EquipSlot.WEAPON,  StatType.INT, 0.60f, 0f, 0.10f,  true),
    SCEPTER(R.string.roguelike_base_scepter, EquipSlot.WEAPON,  StatType.WIS, 0.70f, 0f, 0.05f,  true),
    SHIELD (R.string.roguelike_base_shield,  EquipSlot.OFFHAND, StatType.CON, 0f,    4f, 0f,     true),
    ORB    (R.string.roguelike_base_orb,     EquipSlot.OFFHAND, StatType.INT, 0f,    0f, 0.08f,  true),
    HELMET (R.string.roguelike_base_helmet,  EquipSlot.HELMET,  null,         0f,    3f, 0f,     false),
    ARMOR  (R.string.roguelike_base_armor,   EquipSlot.CHEST,   null,         0f,    6f, 0f,     false),
    BOOTS  (R.string.roguelike_base_boots,   EquipSlot.BOOTS,   null,         0f,    3f, 0f,     false),
    AMULET (R.string.roguelike_base_amulet,  EquipSlot.AMULET,  null,         0f,    0f, 0f,     true),
    RING   (R.string.roguelike_base_ring,    EquipSlot.RING,    null,         0f,    0f, 0f,     true),
}

// ─── Poids d'armure ────────────────────────────────────────────────────────────

/**
 * Le poids d'une pièce d'armure (casque, armure, bottes). **C'est le stuff qui fait la
 * classe**, comme dans Diablo : on ne choisit pas d'archétype, on le porte.
 *  - Tissu : peu d'armure — le mage, qui compte sur ses sorts.
 *  - Léger : un peu moins d'armure, +1 CA par pièce, et la DEX compte dans la CA — le
 *    voleur, qui **évite**.
 *  - Lourd : beaucoup d'armure, mais la DEX ne compte plus dans la CA — le guerrier, qui
 *    **encaisse**.
 * L'armure réduit les dégâts d'un coup ; la CA ([ArmorClass]) décide s'il touche. La
 * moyenne des trois multiplicateurs vaut 1 : le héros de référence ne change pas.
 * [speedPerPiece] : l'armure lourde ralentit, la légère accélère, le tissu ne change rien —
 * la moyenne vaut 0, là aussi (voir DONJON.md, « La jauge »).
 * Chaque poids a ses noms de pièces (pas d'adjectif à accorder).
 */
enum class ArmorWeight(
    @StringRes override val labelRes: Int,
    val armorMult: Float, val acPerPiece: Int, val dexCounts: Boolean, val speedPerPiece: Float,
    @StringRes val helmetRes: Int, @StringRes val chestRes: Int, @StringRes val bootsRes: Int,
) : Labeled {
    CLOTH(R.string.roguelike_weight_cloth, 0.6f, 0, true, 0f,
        R.string.roguelike_base_hood, R.string.roguelike_base_robe, R.string.roguelike_base_sandals),
    LIGHT(R.string.roguelike_weight_light, 0.9f, 1, true, 0.05f,
        R.string.roguelike_base_coif, R.string.roguelike_base_jerkin, R.string.roguelike_base_boots),
    HEAVY(R.string.roguelike_weight_heavy, 1.5f, 0, false, -0.05f,
        R.string.roguelike_base_helm, R.string.roguelike_base_plate, R.string.roguelike_base_sabatons);

    fun nounRes(base: ItemBase) = when (base) {
        ItemBase.HELMET -> helmetRes
        ItemBase.ARMOR  -> chestRes
        ItemBase.BOOTS  -> bootsRes
        else            -> base.nounRes
    }

    companion object {
        /** Les bases qui ont un poids. */
        val WEIGHTED = setOf(ItemBase.HELMET, ItemBase.ARMOR, ItemBase.BOOTS)
    }
}

// ─── Objet ─────────────────────────────────────────────────────────────────────

data class Equipment(
    val base: ItemBase,
    val material: Material,
    val tier: Int,
    val rarity: Rarity,
    /** Dégâts de l'arme (0 pour ce qui n'en est pas une). */
    val damageMin: Int,
    val damageMax: Int,
    val armor: Int,
    /** Stats de base, fixées par le type d'objet. */
    val implicits: List<StatRoll>,
    /** Stats aléatoires, selon la rareté. */
    val affixes: List<StatRoll>,
    val spriteRow: Int,
    val spriteCol: Int,
    /** Ordre de ramassage, pour trier « dernier looté en premier ». */
    val lootId: Long,
    /** Tissu, léger ou lourd (casque, armure, bottes). Null ailleurs, et sur les pièces d'avant les poids. */
    val weight: ArmorWeight? = null,
) {
    val slot get() = base.slot
    /** Ce que la pièce ajoute à la CA : son poids, ou le bouclier. */
    val acBonus get() = (weight?.acPerPiece ?: 0) + if (base == ItemBase.SHIELD) ArmorClass.SHIELD else 0
    /** Ce que la pièce change à la vitesse par son poids (les affixes de vitesse sont à part). */
    val weightSpeed get() = weight?.speedPerPiece ?: 0f
    val power get() = material.ordinal * Material.STEP + tier
    val allStats get() = implicits + affixes
    fun sum(type: StatType) = allStats.filter { it.type == type }.sumOf { it.value.toDouble() }.toFloat()
}

// ─── Le budget des affixes ─────────────────────────────────────────────────────

/**
 * Combien vaut un affixe, et **pourquoi** cette valeur-là. Tout part d'une seule règle :
 *
 * > Un affixe tiré au maximum de son palier vaut **12 % de son axe** (dégâts à l'arme,
 * > PV, PV effectifs, dégâts des sorts, or) chez le *héros de référence*.
 *
 * Le héros de référence, c'est celui qui porte sept objets **Normaux** (donc sans aucun
 * affixe) de la même puissance : épée, bouclier, casque, armure, bottes, amulette, anneau.
 * Il sert d'étalon, rien de plus. On le modélise ici pour que les fourchettes soient
 * **calculées** et pas inventées : si un jour on change les dégâts de base d'une épée ou
 * les PV par point de CON, les affixes suivent tout seuls. `AffixBudgetTest` le vérifie.
 *
 * Pourquoi c'est nécessaire : les stats ne se comparent pas naïvement. À la puissance 1,
 * +1 point de CON vaut 7,8 % des PV alors que +1 % de dégâts critiques vaut 0,06 % des
 * dégâts — 130 fois moins. Sans étalon commun, une table d'affixes écrite à la main donne
 * forcément des lignes mortes et des lignes obligatoires.
 *
 * ### Les huit paliers
 * Un affixe n'existe qu'à partir d'une certaine puissance d'objet, comme l'*item level*
 * de Diablo ou Path of Exile. [TIER_POWER] donne la puissance minimale de chaque palier,
 * choisie pour tomber sur les zones du donjon : étages 1, 6, 11, 21, 31, 41, 61, 81.
 *
 * ### Deux familles d'affixes
 * - **Les affixes à budget** (caractéristiques, armure, PV, dégâts d'arme) : leur valeur
 *   est *déduite* de la règle des 12 %. Ils grandissent parce que leur axe grandit —
 *   l'armure ×19 sur la partie, la FOR seulement ×2,3 (elle se dilue dans un
 *   multiplicateur qui monte).
 * - **Les affixes à plafond** ([CAPPED]) : chance de critique, dégâts critiques, dégâts
 *   des sorts, vol de vie. Ce sont des taux, leur effet ne dépend pas de la puissance. Les
 *   régler à 12 % les rendrait démesurés dès l'étage 1 (il faudrait +13 % de critique). On
 *   fixe donc à la main **ce qu'un équipement complet peut en porter**, et on répartit sur
 *   les huit paliers. La chance de critique est plafonnée à 60 % chez le héros : les
 *   affixes ne doivent pas y suffire à eux seuls.
 */
object AffixBudget {

    const val TIERS = 8

    /** Puissance minimale des paliers T1 à T8 — soit les étages 1, 6, 11, 21, 31, 41, 61, 81. */
    val TIER_POWER = intArrayOf(1, 3, 5, 9, 13, 17, 25, 33)

    /** Un tirage va de 55 % à 100 % de la valeur du palier : il reste une marge à chasser. */
    const val ROLL_MIN = 0.55f

    /** La règle : un affixe plein vaut 12 % de son axe. */
    const val SHARE = 0.12f

    /**
     * La DEX ne fait pas qu'ajouter du critique : elle élargit aussi la fenêtre de parade
     * (+4 ms par point sur 160). On compte donc ses points 1,8 fois quand on mesure ce
     * qu'elle vaut, sinon son axe paraîtrait deux fois trop étroit.
     */
    private const val DEX_PARRY_FACTOR = 1.8f

    /** Coups d'épée donnés dans un combat type — sert à mesurer ce que vaut le vol de vie. */
    private const val HITS_PER_FIGHT = 5f

    /**
     * Les affixes à plafond. Ce qu'un équipement complet peut en porter, réparti sur les
     * huit paliers. Deux raisons d'y être :
     *
     * - **Ce sont des taux** (critique, dégâts critiques, dégâts des sorts, vol de vie) :
     *   leur effet ne dépend pas de la puissance de l'objet. Les régler à 12 % donnerait
     *   +13 % de critique dès l'étage 1, et plus rien à chasser ensuite.
     * - **Leur axe ne grandit pas** : la CHA donne 3 % d'or par point, à l'étage 1 comme à
     *   l'étage 100, et la DEX un point de critique. La règle des 12 % leur donnerait la
     *   même valeur aux huit paliers — un palier qui n'apporte rien n'est pas un palier.
     *
     * La chance de critique du héros est bornée à 60 % : les affixes et la DEX réunis ne
     * doivent y arriver que pour une panoplie qui ne cherche que ça.
     */
    private val CAPPED: Map<StatType, FloatArray> = mapOf(
        StatType.CRIT_CHANCE to floatArrayOf(.020f, .026f, .032f, .040f, .048f, .056f, .068f, .080f),
        StatType.CRIT_DAMAGE to floatArrayOf(.12f, .16f, .20f, .25f, .30f, .36f, .43f, .50f),
        StatType.SPELL_DMG   to floatArrayOf(.08f, .11f, .14f, .18f, .22f, .26f, .30f, .35f),
        StatType.LIFE_STEAL  to floatArrayOf(.004f, .006f, .008f, .010f, .013f, .016f, .019f, .023f),
        StatType.DEX         to floatArrayOf(3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f),
        // La vitesse : +2 % au premier palier, +8 % au dernier. Un taux, qui vaut double (voir perPoint)
        StatType.SPEED       to floatArrayOf(.020f, .025f, .030f, .040f, .050f, .060f, .070f, .080f),
        StatType.CHA         to floatArrayOf(2f, 3f, 4f, 5f, 6f, 8f, 10f, 12f),
    )

    /** Vrai si [type] se règle par un plafond posé à la main plutôt que par la règle des 12 %. */
    fun isCapped(type: StatType) = type in CAPPED

    /**
     * Certains affixes n'apparaissent pas avant un certain palier, comme les gros
     * modificateurs de Diablo ou Path of Exile réservés aux objets de haut niveau. Ce n'est
     * pas de la difficulté artificielle : au palier 1, +1 % de vol de vie rend 0,1 % du sac
     * de PV par coup. Une ligne qui ne fait rien occupe une place et déçoit — mieux vaut
     * qu'elle n'existe pas encore.
     */
    private val MIN_TIER: Map<StatType, Int> = mapOf(
        StatType.LIFE_STEAL  to 4,
        StatType.CRIT_DAMAGE to 3,
    )

    fun minTier(type: StatType) = MIN_TIER[type] ?: 1

    /** Vrai si un objet de cette puissance peut porter [type]. */
    fun allows(type: StatType, power: Int) = maxTier(power) >= minTier(type)

    // ── Le héros de référence ───────────────────────────────────────────────────
    // Sept objets Normaux à la puissance p, tels que LootSystem.create les fabrique.

    /** Une caractéristique donnée par l'arme ou la main gauche (leur type la garantit). */
    private fun mainImplicit(p: Int) = 2f + 0.6f * (p - 1)
    /** Une caractéristique donnée par une des cinq autres pièces, au hasard. */
    private fun sideImplicit(p: Int) = 1f + 0.4f * (p - 1)
    /** Ces cinq tirages au hasard se répartissent sur les six caractéristiques. */
    private fun spread(p: Int) = 5f * sideImplicit(p) / 6f

    /** L'épée donne la FOR, le bouclier la CON : ces deux-là sont mieux servies. */
    fun refStr(p: Int) = Hero.BASE_ATTRIBUTE + mainImplicit(p) + spread(p)
    fun refCon(p: Int) = refStr(p)
    /** DEX, INT, SAG, CHA : seulement ce qui tombe au hasard. */
    fun refOther(p: Int) = Hero.BASE_ATTRIBUTE + spread(p)

    /** Dégâts moyens d'une épée de puissance p, avant caractéristiques (base 4–7). */
    fun refWeaponDamage(p: Int) = 5.5f * LootSystem.scale(p)
    /** Casque 3 + armure 6 + bottes 3 + bouclier 4. */
    fun refArmor(p: Int) = 16f * LootSystem.scale(p)
    /** La constante de l'armure à cette puissance : dégâts reçus × k / (k + armure). */
    fun refK(p: Int) = 50f * LootSystem.scale(p)
    /** PV de base, PV de la CON, et les PV implicites des quatre pièces défensives. */
    fun refHp(p: Int) = Hero.BASE_HP + Hero.HP_PER_CON * (refCon(p) - Hero.BASE_ATTRIBUTE) +
        16f * LootSystem.HP_PER_ARMOR_BASE * LootSystem.scale(p)
    fun refCritChance(p: Int) = 0.05f + 0.01f * (refOther(p) - Hero.BASE_ATTRIBUTE)
    /** Ce que le critique ajoute déjà aux dégâts : un point de plus en vaut d'autant moins. */
    private fun critFactor(p: Int) = 1f + refCritChance(p) * (Hero.BASE_CRIT_MULT - 1f)

    /**
     * Ce que vaut **+1 unité** de [type] sur son axe, chez le héros de référence à la
     * puissance [p]. Pour les stats en pourcentage, l'unité est 1,0 (soit +100 %).
     */
    fun perPoint(type: StatType, p: Int): Float = when (type) {
        // Axe : dégâts à l'arme
        StatType.STR         -> 0.04f / (1f + 0.04f * (refStr(p) - Hero.BASE_ATTRIBUTE))
        StatType.DEX         -> DEX_PARRY_FACTOR * 0.01f * (Hero.BASE_CRIT_MULT - 1f) / critFactor(p)
        StatType.WEAPON_DMG  -> 1f / refWeaponDamage(p)
        StatType.CRIT_CHANCE -> (Hero.BASE_CRIT_MULT - 1f) / critFactor(p)
        StatType.CRIT_DAMAGE -> refCritChance(p) / critFactor(p)
        // Axe : survie
        StatType.CON         -> Hero.HP_PER_CON / refHp(p)
        StatType.MAX_HP      -> 1f / refHp(p)
        StatType.ARMOR       -> (1f / refK(p)) / (1f + refArmor(p) / refK(p))
        // Axe : sorts
        StatType.INT         -> 0.05f / (1f + 0.05f * (refOther(p) - Hero.BASE_ATTRIBUTE))
        StatType.SPELL_DMG   -> 1f
        // La SAG est une stat à paliers (6 points = un tour de recharge en moins) : on la
        // cale sur la FOR pour qu'elle roule les mêmes nombres qu'une caractéristique.
        StatType.WIS         -> 0.04f / (1f + 0.04f * (refStr(p) - Hero.BASE_ATTRIBUTE))
        // Axe : l'or
        StatType.CHA         -> 0.03f
        // Axe : la part du sac de PV rendue sur un combat entier. Compter un seul coup
        // sous-estime le vol de vie — il se cumule, c'est tout son intérêt.
        StatType.LIFE_STEAL  -> HITS_PER_FIGHT * refWeaponDamage(p) *
                                (1f + 0.04f * (refStr(p) - Hero.BASE_ATTRIBUTE)) / refHp(p)
        // Deux axes à la fois : +10 % de vitesse, c'est 10 % de coups en plus par coup reçu,
        // donc autant de dégâts infligés que de dégâts évités sur un combat
        StatType.SPEED       -> SPEED_AXES
    }

    /** La vitesse compte sur les dégâts **et** sur la survie. */
    private const val SPEED_AXES = 2f

    /**
     * Ce que vaut +1 CA sur l'axe de la survie : il retire 5 points de chance d'être touché
     * au héros de référence, qui l'est 3 fois sur 4 — soit 1/15 des dégâts reçus.
     */
    fun perAcPoint() = ArmorClass.AC_STEP / ArmorClass.REF_HIT

    /** Le palier le plus haut qu'un objet de cette puissance peut porter. */
    fun maxTier(power: Int) = TIER_POWER.count { it <= power }.coerceIn(1, TIERS)

    /**
     * Valeur d'un affixe [type] au palier [tier], tirage plein (100 %). Pour les stats à
     * plafond c'est une valeur posée ; pour les autres elle tombe de la règle des 12 %.
     */
    fun nominal(type: StatType, tier: Int): Float {
        CAPPED[type]?.let { return it[tier - 1] }
        return SHARE / perPoint(type, TIER_POWER[tier - 1])
    }

    /**
     * Le palier tiré : le plus haut disponible six fois sur dix, sinon un ou deux crans en
     * dessous. C'est ça qui fait qu'on continue de ramasser au même étage — un objet de la
     * bonne puissance peut encore cacher un meilleur palier.
     */
    /**
     * Le plus petit tirage d'un affixe, déjà arrondi. Sans ce plancher, une stat « épaisse »
     * — un point de CON vaut 8 % des PV — verrait ses 55 % s'écraser sur +1 et tomber bien
     * en dessous de sa part. On arrondit donc le minimum vers le haut, pas au plus proche.
     */
    fun minRoll(type: StatType, tier: Int): Float {
        val low = nominal(type, tier) * ROLL_MIN
        return if (type.isPercent) low else kotlin.math.ceil(low).coerceAtLeast(1f)
    }

    fun pickTier(type: StatType, power: Int, rng: Random): Int {
        val top = maxTier(power)
        val floorTier = minTier(type)
        val r = rng.nextFloat()
        val picked = when {
            r < 0.60f -> top
            r < 0.90f -> top - 1
            else      -> top - 2
        }
        return picked.coerceIn(floorTier, top)
    }
}

// ─── Génération ────────────────────────────────────────────────────────────────

object LootSystem {

    /** Chance qu'un ennemi vaincu lâche un équipement. */
    const val DROP_CHANCE = 0.20f

    /**
     * PV donnés par une pièce défensive, par point de son armure de base. Casque, armure,
     * bottes et bouclier totalisent 16 d'armure de base, soit 12 × la puissance en PV.
     * C'est ce qui fait tenir le sac de PV au rythme des dégâts des monstres.
     */
    const val HP_PER_ARMOR_BASE = 0.75f

    /** Multiplicateur de base d'une puissance donnée : +45 % par cran. */
    fun scale(power: Int) = 1f + 0.45f * (power - 1)

    /** Puissance typique d'un étage : 1 à l'étage 1, ~41 à l'étage 100. */
    fun powerCenter(floor: Int) = 1f + (floor - 1) * 0.4f

    /**
     * Fréquence d'apparition d'un affixe, à la façon des colonnes « frequency » de Diablo 2 :
     * tous les affixes d'un emplacement ne sortent pas autant. Les stats de confort sont
     * communes, celles qui font les gros écarts (dégâts d'arme, critique, vol de vie) sont
     * rares — c'est ce qui rend un objet mémorable, pas la taille du tirage.
     */
    private val affixWeights: Map<StatType, Float> = mapOf(
        StatType.STR to 10f, StatType.DEX to 10f, StatType.CON to 10f, StatType.INT to 10f,
        StatType.WIS to 8f,  StatType.CHA to 6f,
        StatType.ARMOR to 10f, StatType.MAX_HP to 10f,
        StatType.WEAPON_DMG to 6f, StatType.SPELL_DMG to 6f,
        StatType.CRIT_CHANCE to 5f, StatType.CRIT_DAMAGE to 5f,
        StatType.LIFE_STEAL to 3f, StatType.SPEED to 5f,
    )

    /**
     * Ce que chaque emplacement peut porter. Un affixe ne sort jamais deux fois sur le même
     * objet : on ne veut pas d'un anneau qui empile trois fois la même ligne.
     */
    private val affixPools: Map<EquipSlot, List<StatType>> = run {
        val attrs = StatType.ATTRIBUTES
        val armorPiece = attrs + listOf(StatType.ARMOR, StatType.MAX_HP)
        val jewel = attrs + listOf(StatType.MAX_HP, StatType.SPELL_DMG, StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.LIFE_STEAL, StatType.SPEED)
        // La vitesse : sur l'arme, les bottes et les bijoux (comme la vitesse d'attaque et de
        // course de Diablo), pas sur le casque, l'armure ni la main gauche
        mapOf(
            EquipSlot.WEAPON  to attrs + listOf(StatType.WEAPON_DMG, StatType.SPELL_DMG, StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.LIFE_STEAL, StatType.SPEED),
            EquipSlot.OFFHAND to attrs + listOf(StatType.ARMOR, StatType.MAX_HP, StatType.SPELL_DMG, StatType.CRIT_CHANCE),
            EquipSlot.HELMET  to armorPiece,
            EquipSlot.CHEST   to armorPiece,
            EquipSlot.BOOTS   to armorPiece + StatType.SPEED,
            EquipSlot.AMULET  to jewel,
            EquipSlot.RING    to jewel,
        )
    }

    /** Tire [count] affixes différents dans [pool], chacun selon sa fréquence. */
    private fun pickAffixes(pool: List<StatType>, count: Int, rng: Random): List<StatType> {
        val left = pool.toMutableList()
        return List(count.coerceAtMost(left.size)) {
            val picked = weighted(left.map { t -> t to affixWeights.getValue(t) }, rng)
            left -= picked
            picked
        }
    }

    fun tryDrop(floor: Int, lootId: Long, rng: Random = Random): Equipment? {
        if (rng.nextFloat() > DROP_CHANCE) return null
        return generate(floor, lootId, rng)
    }

    fun generate(floor: Int, lootId: Long = 0, rng: Random = Random): Equipment {
        // Puissance : autour de celle de l'étage, un peu en dessous le plus souvent
        val power = (powerCenter(floor) + rng.nextFloat() * 3f - 2f).roundToInt().coerceIn(1, Material.MAX_POWER)
        val (material, tier) = pickGrade(power, rng)
        return create(pickBase(rng), material, tier, pickRarity(floor, rng), lootId, rng)
    }

    /** Toutes les façons d'écrire une puissance : Cuir 5 ou Cuivre 1, au hasard. */
    private fun pickGrade(power: Int, rng: Random): Pair<Material, Int> {
        val options = Material.entries.mapNotNull { m ->
            val tier = power - m.ordinal * Material.STEP
            if (tier in 1..Material.TIERS) m to tier else null
        }
        return options.random(rng)
    }

    fun create(
        base: ItemBase, material: Material, tier: Int, rarity: Rarity, lootId: Long, rng: Random,
        forcedWeight: ArmorWeight? = null,
    ): Equipment {
        val power = material.ordinal * Material.STEP + tier
        val s = scale(power)

        val weight = if (base in ArmorWeight.WEIGHTED) forcedWeight ?: ArmorWeight.entries.random(rng) else null
        val isWeapon = base.damageMult > 0f
        val dmgMin = if (isWeapon) (4f * s * base.damageMult).roundToInt().coerceAtLeast(1) else 0
        val dmgMax = if (isWeapon) (7f * s * base.damageMult).roundToInt().coerceAtLeast(dmgMin + 1) else 0
        // Le poids change l'armure, pas les PV : ceux-là suivent la base de la pièce
        val armor  = (base.armorBase * (weight?.armorMult ?: 1f) * s).roundToInt()

        val implicits = mutableListOf<StatRoll>()
        val attr = base.attribute ?: StatType.ATTRIBUTES.random(rng)
        val attrValue = if (base.attribute != null) 2f + 0.6f * (power - 1) else 1f + 0.4f * (power - 1)
        implicits += StatRoll(attr, attrValue.roundToInt().toFloat())
        if (base.armorBase > 0f)
            implicits += StatRoll(StatType.MAX_HP, (base.armorBase * HP_PER_ARMOR_BASE * s).roundToInt().toFloat())
        if (base.spellBonus > 0f) implicits += StatRoll(StatType.SPELL_DMG, base.spellBonus * (1f + 0.1f * (power - 1)))

        val count = rng.nextInt(rarity.minAffixes, rarity.maxAffixes + 1)
        val pool = affixPools.getValue(base.slot).filter { AffixBudget.allows(it, power) }
        val affixes = pickAffixes(pool, count, rng).map { rollAffix(it, power, rng) }

        val (row, col) = pickSprite(base, material, rng)
        return Equipment(base, material, tier, rarity, dmgMin, dmgMax, armor, implicits, affixes, row, col, lootId, weight)
    }

    /**
     * Un affixe : d'abord son palier (ce que la puissance de l'objet autorise), puis un
     * tirage entre 55 % et 100 % de la valeur de ce palier. Les fourchettes ne sont écrites
     * nulle part — elles se déduisent du budget, voir [AffixBudget].
     */
    private fun rollAffix(type: StatType, power: Int, rng: Random): StatRoll {
        val tier = AffixBudget.pickTier(type, power, rng)
        val full = AffixBudget.nominal(type, tier)
        val value = full * (AffixBudget.ROLL_MIN + rng.nextFloat() * (1f - AffixBudget.ROLL_MIN))
        val rolled = if (type.isPercent) value
                     else value.roundToInt().toFloat().coerceAtLeast(AffixBudget.minRoll(type, tier))
        return StatRoll(type, rolled, tier)
    }

    private fun pickBase(rng: Random): ItemBase = weighted(listOf(
        ItemBase.SWORD to 6f, ItemBase.AXE to 5f, ItemBase.DAGGER to 5f, ItemBase.MACE to 4f,
        ItemBase.STAFF to 5f, ItemBase.SCEPTER to 4f,
        ItemBase.SHIELD to 6f, ItemBase.ORB to 5f,
        ItemBase.HELMET to 10f, ItemBase.ARMOR to 12f, ItemBase.BOOTS to 10f,
        ItemBase.AMULET to 7f, ItemBase.RING to 8f,
    ), rng)

    private fun pickRarity(floor: Int, rng: Random): Rarity = weighted(listOf(
        Rarity.NORMAL to maxOf(25f, 55f - floor * 0.3f),
        Rarity.MAGIC  to 35f,
        Rarity.RARE   to minOf(35f, 10f + floor * 0.25f),
    ), rng)

    /** Icônes provisoires dans 64x64.png : la couleur suit la matière (brun, or, bleu). */
    private fun pickSprite(base: ItemBase, material: Material, rng: Random): Pair<Int, Int> {
        val shade = (material.ordinal * 3) / Material.entries.size   // 0, 1, 2
        val wo = shade * 5                                            // lignes des armes : +5 or, +10 glace
        fun pick(row: Int, cols: IntRange) = row to cols.random(rng)
        return when (base) {
            ItemBase.SWORD   -> pick(90 + wo, 4..9)
            ItemBase.DAGGER  -> pick(90 + wo, 0..3)
            ItemBase.AXE     -> pick(91 + wo, 5..9)
            ItemBase.MACE    -> pick(92 + wo, 0..3)
            ItemBase.STAFF   -> pick(93 + wo, 0..3)
            ItemBase.SCEPTER -> pick(93 + wo, 4..7)
            ItemBase.SHIELD  -> pick(134, 2..4)
            ItemBase.ORB     -> pick(133, 0..5)
            ItemBase.HELMET  -> pick(listOf(122, 119, 125)[shade], 0..15)
            ItemBase.ARMOR   -> pick(listOf(123, 120, 126)[shade], 0..15)
            ItemBase.BOOTS   -> pick(listOf(124, 121, 127)[shade], 0..15)
            ItemBase.AMULET  -> pick(115, 9..11)
            ItemBase.RING    -> pick(115, 2..8)
        }
    }

    // ── Valeur ──────────────────────────────────────────────────────────────────

    fun sellPrice(e: Equipment): Int = ((3 + 2 * e.power) * e.rarity.sellMult).roundToInt()

    /**
     * Note d'un objet, pour trier le sac et comparer avec ce qu'on porte.
     *
     * Elle se compte dans la **monnaie du budget des affixes** ([AffixBudget]) : chaque
     * ligne vaut ce qu'elle apporte en % de son axe, donc un affixe plein vaut 12 points.
     * Plus de pondérations écrites à la main qui vieillissent mal, et plus de ligne qui
     * paraît énorme juste parce qu'elle s'affiche en pourcentage.
     *
     * Le tout est multiplié par l'échelle de la puissance, sinon la note ne dirait que
     * « bon *pour sa puissance* » : une épée de Cuir 1 parfaite noterait autant qu'une épée
     * d'Astralite 5, et on ne verrait jamais l'objet plus profond comme une amélioration.
     */
    fun rating(e: Equipment): Int {
        val p = e.power
        var r = 0f
        if (e.damageMax > 0) r += (e.damageMin + e.damageMax) / 2f / AffixBudget.refWeaponDamage(p) * 100f
        if (e.armor > 0)     r += e.armor * AffixBudget.perPoint(StatType.ARMOR, p) * 100f
        r += e.acBonus * AffixBudget.perAcPoint() * 100f
        r += e.weightSpeed * AffixBudget.perPoint(StatType.SPEED, p) * 100f
        for (s in e.allStats) r += s.value * AffixBudget.perPoint(s.type, p) * 100f
        return (r * scale(p)).roundToInt()
    }

    // ── Affichage ───────────────────────────────────────────────────────────────

    /** « Épée de fer 3 » / « Iron Sword 3 ». */
    fun displayName(context: Context, e: Equipment): String {
        val mat = context.getString(if (e.base.usesWeaponMaterial) e.material.weaponRes else e.material.armorRes)
        val noun = e.weight?.nounRes(e.base) ?: e.base.nounRes
        return context.getString(R.string.roguelike_item_name, context.getString(noun), mat, e.tier)
    }

    /** Des points de CA en points d'esquive, pour le joueur qui ne connaît pas D&D. */
    private fun dodgePercent(ac: Int) = Math.round(ac * ArmorClass.AC_STEP * 100)

    /** Lignes de description : dégâts, armure, puis toutes les stats. */
    fun describe(context: Context, e: Equipment): List<String> = buildList {
        if (e.damageMax > 0) add(context.getString(R.string.roguelike_item_damage, e.damageMin, e.damageMax))
        if (e.armor > 0) add(context.getString(R.string.roguelike_item_armor, e.armor))
        when (e.weight) {
            ArmorWeight.CLOTH -> add(context.getString(R.string.roguelike_item_weight_cloth))
            ArmorWeight.LIGHT -> add(context.getString(R.string.roguelike_item_weight_light, dodgePercent(ArmorWeight.LIGHT.acPerPiece),
                Math.round(ArmorWeight.LIGHT.speedPerPiece * 100)))
            ArmorWeight.HEAVY -> add(context.getString(R.string.roguelike_item_weight_heavy, Math.round(-ArmorWeight.HEAVY.speedPerPiece * 100)))
            null -> {}
        }
        if (e.base == ItemBase.SHIELD) add(context.getString(R.string.roguelike_item_shield_ac, dodgePercent(ArmorClass.SHIELD)))
        e.implicits.forEach { add(it.display(context)) }
        e.affixes.forEach { add(it.display(context)) }
    }

    private fun <T> weighted(list: List<Pair<T, Float>>, rng: Random): T {
        val total = list.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.001f)
        var r = rng.nextFloat() * total
        for ((item, w) in list) { r -= w; if (r <= 0f) return item }
        return list.last().first
    }
}
