package com.Atom2Universe.app.games.roguelike

import android.content.Context
import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import com.Atom2Universe.app.periodic.getPeriodicElements
import com.Atom2Universe.app.periodic.localizedName
import java.text.Normalizer
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
 * 1 ou plus pour un affixe : c'est son palier de puissance, voir [AffixBudget].
 */
data class StatRoll(val type: StatType, val value: Float, val tier: Int = 0) {
    /**
     * La ligne de stat. [linked] : le nom de la stat et le palier deviennent des liens du lexique
     * (voir [LexiconText]) ; sinon le texte est nu, pour les vues qui dessinent au canvas.
     */
    fun display(context: Context, linked: Boolean = false): String {
        val name = context.getString(type.labelRes)
        val label = if (linked) LexiconText.link(Lexicon.idOf(type), name) else name
        val line = if (type.isPercent)
            context.getString(R.string.roguelike_stat_roll_percent, (value * 100).roundToInt(), label)
        else
            context.getString(R.string.roguelike_stat_roll_flat, DungeonNumbers.format(context, value.roundToInt()), label)
        val out = if (tier > 0) context.getString(R.string.roguelike_stat_roll_tier, line, tier, type.name) else line
        return if (linked) out else LexiconText.strip(out)
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

// ─── Matières : le tableau périodique ──────────────────────────────────────────

/**
 * Le nom d'une puissance d'objet. Les matières sont **les 118 éléments**, dans l'ordre du
 * numéro atomique : Épée d'Hydrogène au premier étage, Cuirasse de Fer vers l'étage 650,
 * Oganesson tout au fond. Chaque élément a cinq tiers (I à V), un tier tous les 5 étages,
 * soit 25 étages par élément. Après l'Oganesson on repart à l'Hydrogène avec un mot de
 * cycle, de l'atome à l'univers : stellaire, galactique, cosmique… (voir DONJON.md, « Le
 * donjon sans fin »).
 *
 * Seul le **nom** suit cette échelle : les stats d'un objet sont une formule continue de sa
 * puissance ([LootSystem.scale]), et la puissance n'a pas de plafond.
 */
object Grade {
    const val TIERS = 5
    /** Deux crans de puissance par tier : 5 étages à 0,4 cran par étage. */
    const val POWER_PER_TIER = 2
    const val ELEMENTS = 118
    /** Tiers dans un cycle complet de l'Hydrogène à l'Oganesson. */
    const val TIERS_PER_CYCLE = TIERS * ELEMENTS

    private fun index(power: Int) = (power.coerceAtLeast(1) - 1) / POWER_PER_TIER
    /** Le tier dans l'élément, de 1 à 5. */
    fun tier(power: Int) = index(power) % TIERS + 1
    /** L'élément, de 0 (Hydrogène) à 117 (Oganesson). */
    fun element(power: Int) = index(power) / TIERS % ELEMENTS
    /** Le cycle : 0 pour le premier passage, 1 pour le stellaire… */
    fun cycle(power: Int) = index(power) / TIERS_PER_CYCLE
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
) {
    SWORD  (R.string.roguelike_base_sword,   EquipSlot.WEAPON,  StatType.STR, 1.00f, 0f, 0f),
    AXE    (R.string.roguelike_base_axe,     EquipSlot.WEAPON,  StatType.STR, 1.25f, 0f, 0f),
    DAGGER (R.string.roguelike_base_dagger,  EquipSlot.WEAPON,  StatType.DEX, 0.80f, 0f, 0f),
    MACE   (R.string.roguelike_base_mace,    EquipSlot.WEAPON,  StatType.CON, 1.00f, 0f, 0f),
    STAFF  (R.string.roguelike_base_staff,   EquipSlot.WEAPON,  StatType.INT, 0.60f, 0f, 0.10f),
    SCEPTER(R.string.roguelike_base_scepter, EquipSlot.WEAPON,  StatType.WIS, 0.70f, 0f, 0.05f),
    SHIELD (R.string.roguelike_base_shield,  EquipSlot.OFFHAND, StatType.CON, 0f,    4f, 0f),
    ORB    (R.string.roguelike_base_orb,     EquipSlot.OFFHAND, StatType.INT, 0f,    0f, 0.08f),
    // Une main gauche par archétype (voir DONJON.md) : le bouclier du guerrier, l'orbe du mage, l'arc
    // du voleur, le grimoire du nécromancien, la lanterne du vagabond. Leur effet sur le Spécial viendra
    // avec les archétypes ; leur part de défense, avec l'équilibrage des mains gauches.
    BOW     (R.string.roguelike_base_bow,      EquipSlot.OFFHAND, StatType.DEX, 0f,    0f, 0f),
    GRIMOIRE(R.string.roguelike_base_grimoire, EquipSlot.OFFHAND, StatType.WIS, 0f,    0f, 0f),
    LANTERN (R.string.roguelike_base_lantern,  EquipSlot.OFFHAND, StatType.STR, 0f,    0f, 0f),
    HELMET (R.string.roguelike_base_helmet,  EquipSlot.HELMET,  null,         0f,    3f, 0f),
    ARMOR  (R.string.roguelike_base_armor,   EquipSlot.CHEST,   null,         0f,    6f, 0f),
    BOOTS  (R.string.roguelike_base_boots,   EquipSlot.BOOTS,   null,         0f,    3f, 0f),
    AMULET (R.string.roguelike_base_amulet,  EquipSlot.AMULET,  null,         0f,    0f, 0f),
    RING   (R.string.roguelike_base_ring,    EquipSlot.RING,    null,         0f,    0f, 0f),
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
 * moyenne des cinq multiplicateurs vaut 1 (0,5 / 0,7 / 0,95 / 1,25 / 1,6) : le héros de référence ne change pas.
 * [speedPerPiece] : l'armure lourde ralentit, la légère accélère, le tissu ne change rien —
 * la moyenne vaut 0, là aussi (voir DONJON.md, « La jauge »).
 * Chaque poids a ses noms de pièces (pas d'adjectif à accorder).
 */
enum class ArmorWeight(
    @StringRes override val labelRes: Int,
    val armorMult: Float, val acPerPiece: Int, val dexCounts: Boolean, val speedPerPiece: Float,
    @StringRes val helmetRes: Int, @StringRes val chestRes: Int, @StringRes val bootsRes: Int,
    /** Ce que la DEX peut ajouter à la CA au plus (l'armure intermédiaire de D&D : +2). */
    val dexCap: Int = Int.MAX_VALUE,
) : Labeled {
    CLOTH(R.string.roguelike_weight_cloth, 0.7f, 0, true, 0f,
        R.string.roguelike_base_hood, R.string.roguelike_base_robe, R.string.roguelike_base_sandals),
    LIGHT(R.string.roguelike_weight_light, 0.95f, 1, true, 0.05f,
        R.string.roguelike_base_coif, R.string.roguelike_base_jerkin, R.string.roguelike_base_boots),
    HEAVY(R.string.roguelike_weight_heavy, 1.6f, 0, false, -0.05f,
        R.string.roguelike_base_helm, R.string.roguelike_base_plate, R.string.roguelike_base_sabatons),
    /** Entre le léger et le lourd : le vagabond. La DEX compte dans la CA, jusqu'à +2. */
    MEDIUM(R.string.roguelike_weight_medium, 1.25f, 0, true, -0.025f,
        R.string.roguelike_base_cap, R.string.roguelike_base_hauberk, R.string.roguelike_base_greaves, dexCap = 2),
    /** Sous le tissu : le nécromancien, qui compte sur ses pantins. */
    ULTRALIGHT(R.string.roguelike_weight_ultralight, 0.5f, 0, true, 0.025f,
        R.string.roguelike_base_veil, R.string.roguelike_base_shroud, R.string.roguelike_base_wraps);

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
    /** Sa puissance : elle fait ses stats et son nom (voir [Grade]). Sans plafond. */
    val power: Int,
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
    /** Le numéro atomique du set d'isotope dont la pièce fait partie (voir [IsotopeSet]), ou null. */
    val isotopeZ: Int? = null,
) {
    val isotopeSet get() = isotopeZ?.let(IsotopeSets::of)
    val slot get() = base.slot
    /** Ce que la pièce ajoute à la CA : son poids, ou le bouclier. */
    val acBonus get() = (weight?.acPerPiece ?: 0) + if (base == ItemBase.SHIELD) ArmorClass.SHIELD else 0
    /** Ce que la pièce change à la vitesse par son poids (les affixes de vitesse sont à part). */
    val weightSpeed get() = weight?.speedPerPiece ?: 0f
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
 * ### Les paliers
 * Un affixe n'existe qu'à partir d'une certaine puissance d'objet, comme l'*item level*
 * de Diablo ou Path of Exile. [TIER_POWER] donne la puissance minimale des huit premiers
 * paliers, choisie pour tomber sur les zones du donjon : étages 1, 6, 11, 21, 31, 41, 61, 81.
 * Le donjon est sans fin, les paliers aussi : au-delà du 8ᵉ, chacun s'ouvre à une puissance
 * 1,3 fois plus haute que le précédent ([tierPower]) — le palier 26 vers l'étage 10 000.
 * Les taux (dégâts des sorts, dégâts critiques, vol de vie, vitesse) s'arrêtent au palier 8 ;
 * tout le reste continue, caractéristiques comprises ([maxTierOf]).
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

    /** Les paliers posés à la main. Les suivants se calculent ([tierPower]). */
    const val TIERS = 8

    /** Puissance minimale des paliers T1 à T8 — soit les étages 1, 6, 11, 21, 31, 41, 61, 81. */
    val TIER_POWER = intArrayOf(1, 3, 5, 9, 13, 17, 25, 33)

    /** Au-delà du 8ᵉ, chaque palier s'ouvre à une puissance 1,3 fois plus haute. */
    const val DEEP_TIER_GROWTH = 1.3

    /**
     * Ce que chaque palier de critique ajoute au-delà du 8ᵉ : +0,5 point. Les monstres
     * profonds résistent au critique d'autant ([critResistance]) : le critique ne s'envole
     * pas, il faut le chasser pour le garder.
     */
    const val CRIT_DEEP_STEP = 0.005f

    /**
     * Combien d'affixes de critique la résistance des monstres suppose : deux. Un héros qui en
     * porte moins critique de moins en moins en profondeur, un héros qui en empile plus garde
     * de la marge — même 100 % de critique affiché ne suffit plus tout au fond.
     */
    private const val CRIT_RESIST_AFFIXES = 2

    /** Puissance minimale du palier [tier], sans limite. */
    fun tierPower(tier: Int): Int =
        if (tier <= TIERS) TIER_POWER[tier - 1]
        else Math.round(TIER_POWER[TIERS - 1] * Math.pow(DEEP_TIER_GROWTH, (tier - TIERS).toDouble())).toInt()

    /**
     * Le dernier palier d'une stat. Les **taux** (dégâts des sorts, dégâts critiques, vol de
     * vie, vitesse) s'arrêtent au 8ᵉ : leur effet ne dépend pas de la profondeur, un palier de
     * plus n'y apporterait rien. Tout le reste continue : dégâts d'arme, armure, PV, critique,
     * et les caractéristiques (voir [LootSystem.attributeWeight]).
     */
    fun maxTierOf(type: StatType): Int = when (type) {
        StatType.SPELL_DMG, StatType.CRIT_DAMAGE, StatType.LIFE_STEAL, StatType.SPEED -> TIERS
        else -> Int.MAX_VALUE
    }

    /**
     * La résistance des monstres au critique, à la puissance de leur étage : ce que
     * [CRIT_RESIST_AFFIXES] affixes de critique ont gagné au-delà du palier 8. Nulle jusqu'à
     * l'étage 100 environ.
     */
    fun critResistance(power: Int) = CRIT_RESIST_AFFIXES * CRIT_DEEP_STEP * (maxTier(power) - TIERS).coerceAtLeast(0)

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
    private fun mainImplicit(p: Int) = LootSystem.mainAttribute(p)
    /** Une caractéristique donnée par une des cinq autres pièces, au hasard. */
    private fun sideImplicit(p: Int) = LootSystem.sideAttribute(p)
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
    fun refCritChance(p: Int) = 0.05f + 0.01f * (refOther(p) - Hero.BASE_ATTRIBUTE) * w(p)

    /**
     * Le poids d'un point de caractéristique à cette puissance (voir [LootSystem.attributeWeight]) :
     * au-delà de l'étage 100, un point vaut moins, et un affixe en porte d'autant plus.
     */
    private fun w(p: Int) = LootSystem.attributeWeight(p.toFloat())
    /** Ce que le critique ajoute déjà aux dégâts : un point de plus en vaut d'autant moins. */
    private fun critFactor(p: Int) = 1f + refCritChance(p) * (Hero.BASE_CRIT_MULT - 1f)

    /**
     * Ce que vaut **+1 unité** de [type] sur son axe, chez le héros de référence à la
     * puissance [p]. Pour les stats en pourcentage, l'unité est 1,0 (soit +100 %).
     */
    fun perPoint(type: StatType, p: Int): Float = when (type) {
        // Axe : dégâts à l'arme
        StatType.STR         -> 0.04f * w(p) / (1f + 0.04f * (refStr(p) - Hero.BASE_ATTRIBUTE) * w(p))
        StatType.DEX         -> DEX_PARRY_FACTOR * 0.01f * w(p) * (Hero.BASE_CRIT_MULT - 1f) / critFactor(p)
        StatType.WEAPON_DMG  -> 1f / refWeaponDamage(p)
        StatType.CRIT_CHANCE -> (Hero.BASE_CRIT_MULT - 1f) / critFactor(p)
        StatType.CRIT_DAMAGE -> refCritChance(p) / critFactor(p)
        // Axe : survie
        StatType.CON         -> Hero.HP_PER_CON / refHp(p)
        StatType.MAX_HP      -> 1f / refHp(p)
        StatType.ARMOR       -> (1f / refK(p)) / (1f + refArmor(p) / refK(p))
        // Axe : sorts
        StatType.INT         -> 0.05f * w(p) / (1f + 0.05f * (refOther(p) - Hero.BASE_ATTRIBUTE) * w(p))
        StatType.SPELL_DMG   -> 1f
        // La SAG est une stat à paliers (6 points = un tour de recharge en moins) : on la
        // cale sur la FOR pour qu'elle roule les mêmes nombres qu'une caractéristique.
        StatType.WIS         -> 0.04f * w(p) / (1f + 0.04f * (refStr(p) - Hero.BASE_ATTRIBUTE) * w(p))
        // Axe : l'or
        StatType.CHA         -> 0.03f * w(p)
        // Axe : la part du sac de PV rendue sur un combat entier. Compter un seul coup
        // sous-estime le vol de vie — il se cumule, c'est tout son intérêt.
        StatType.LIFE_STEAL  -> HITS_PER_FIGHT * refWeaponDamage(p) *
                                (1f + 0.04f * (refStr(p) - Hero.BASE_ATTRIBUTE) * w(p)) / refHp(p)
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

    /** Le palier le plus haut qu'un objet de cette puissance peut porter, toutes stats confondues. */
    fun maxTier(power: Int): Int {
        var t = 1
        while (tierPower(t + 1) <= power) t++
        return t
    }

    /**
     * Valeur d'un affixe [type] au palier [tier], tirage plein (100 %). Pour les stats à
     * plafond c'est une valeur posée ; pour les autres elle tombe de la règle des 12 %.
     * Au-delà du 8ᵉ palier, le critique gagne [CRIT_DEEP_STEP] par palier.
     */
    fun nominal(type: StatType, tier: Int): Float {
        CAPPED[type]?.let {
            if (tier <= TIERS) return it[tier - 1]
            return when (type) {
                StatType.CRIT_CHANCE -> it[TIERS - 1] + CRIT_DEEP_STEP * (tier - TIERS)
                // DEX et CHA grandissent exactement comme leur poids baisse (voir LootSystem.attributeWeight) :
                // un affixe garde ce qu'il valait au palier 8
                StatType.DEX, StatType.CHA -> it[TIERS - 1] / LootSystem.attributeWeight(tierPower(tier).toFloat())
                else -> it[TIERS - 1]
            }
        }
        return SHARE / perPoint(type, tierPower(tier))
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
        val top = minOf(maxTier(power), maxTierOf(type))
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

    /** Multiplicateur de base d'une puissance donnée : +45 % par cran. Sans plafond. */
    fun scale(power: Int) = 1f + 0.45f * (power - 1)

    /** Puissance typique d'un étage : 1 à l'étage 1, ~41 à l'étage 100, ~4 000 à l'étage 10 000. */
    fun powerCenter(floor: Int) = 1f + (floor - 1) * 0.4f

    /**
     * La puissance de l'étage 100. Les **taux** que les objets donnent d'office (le bonus
     * « dégâts des sorts » du bâton et de l'orbe) s'y arrêtent : un taux ne se dilue pas.
     */
    const val DEEP_POWER = 41

    /** La caractéristique qu'une arme ou une main gauche donne par son type. Sans plafond. */
    fun mainAttribute(power: Int) = 2f + 0.6f * (power - 1)
    /** La caractéristique au hasard d'une autre pièce. Sans plafond. */
    fun sideAttribute(power: Int) = 1f + 0.4f * (power - 1)

    /**
     * **Ce que vaut un point de caractéristique face aux monstres d'une puissance d'étage** : 1
     * jusqu'à l'étage 100, puis de moins en moins, exactement au rythme où les caractéristiques
     * des objets grandissent. Les caractéristiques montent sans fin (FOR 50 à l'étage 100, 500 à
     * l'étage 1 000…), mais les monstres profonds y résistent d'autant, comme l'armure protège
     * moins face à eux : c'est **l'avance sur l'équipement moyen de l'étage** qui compte.
     *
     * Pourquoi : les caractéristiques sont des modificateurs façon D&D (jets de d20, critique,
     * fenêtre de parade, recharges). Comptées telles quelles, un +600 à l'étage 10 000 rendrait
     * chaque jet automatique, et la FOR multipliée par l'arme ferait grandir les dégâts comme un
     * carré. Avec ce poids, le héros moyen de l'étage 10 000 a les mêmes modificateurs que celui
     * de l'étage 100 ; celui qui a farmé plus de FOR que prévu garde son avance.
     */
    fun attributeWeight(power: Float): Float {
        val at100 = powerCenter(Encounters.DEEP_FLOOR)
        if (power <= at100) return 1f
        return (1f + 0.4f * (at100 - 1)) / (1f + 0.4f * (power - 1))
    }

    /** Le poids d'un point de caractéristique à l'étage [floor]. */
    fun attributeWeightAt(floor: Int) = attributeWeight(powerCenter(floor))

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
        // Dans la tranche d'un set d'isotope, une part des objets en est une pièce. Le tirage n'a lieu
        // que là : partout ailleurs, les dés tombent comme avant.
        IsotopeSets.forFloor(floor)?.let { set ->
            if (rng.nextFloat() < IsotopeSets.dropShare) return createSetPiece(set, IsotopeSets.BASES.random(rng), lootId, rng)
        }
        // Puissance : autour de celle de l'étage, un peu en dessous le plus souvent
        val power = (powerCenter(floor) + rng.nextFloat() * 3f - 2f).roundToInt().coerceAtLeast(1)
        return create(pickBase(rng), power, pickRarity(floor, rng), lootId, rng)
    }

    /**
     * Une pièce d'un set d'isotope : rare, du poids de l'archétype du set, et d'une puissance qui
     * varie un peu d'une pièce à l'autre ([IsotopeSets.basePower]).
     */
    fun createSetPiece(set: IsotopeSet, base: ItemBase, lootId: Long, rng: Random): Equipment {
        val power = IsotopeSets.basePower(set.z) + rng.nextInt(IsotopeSets.POWER_SPREAD)
        return create(base, power, Rarity.RARE, lootId, rng, forcedWeight = set.archetype.weight).copy(isotopeZ = set.z)
    }

    fun create(
        base: ItemBase, power: Int, rarity: Rarity, lootId: Long, rng: Random,
        forcedWeight: ArmorWeight? = null,
    ): Equipment {
        val s = scale(power)

        val weight = if (base in ArmorWeight.WEIGHTED) forcedWeight ?: ArmorWeight.entries.random(rng) else null
        val isWeapon = base.damageMult > 0f
        val dmgMin = if (isWeapon) (4f * s * base.damageMult).roundToInt().coerceAtLeast(1) else 0
        val dmgMax = if (isWeapon) (7f * s * base.damageMult).roundToInt().coerceAtLeast(dmgMin + 1) else 0
        // Le poids change l'armure, pas les PV : ceux-là suivent la base de la pièce
        val armor  = (base.armorBase * (weight?.armorMult ?: 1f) * s).roundToInt()

        val implicits = mutableListOf<StatRoll>()
        val attr = base.attribute ?: StatType.ATTRIBUTES.random(rng)
        val attrValue = if (base.attribute != null) mainAttribute(power) else sideAttribute(power)
        implicits += StatRoll(attr, attrValue.roundToInt().toFloat())
        if (base.armorBase > 0f)
            implicits += StatRoll(StatType.MAX_HP, (base.armorBase * HP_PER_ARMOR_BASE * s).roundToInt().toFloat())
        // Un taux : il s'arrête à l'étage 100 (voir DEEP_POWER)
        if (base.spellBonus > 0f)
            implicits += StatRoll(StatType.SPELL_DMG, base.spellBonus * (1f + 0.1f * (power.coerceAtMost(DEEP_POWER) - 1)))

        val count = rng.nextInt(rarity.minAffixes, rarity.maxAffixes + 1)
        val pool = affixPools.getValue(base.slot).filter { AffixBudget.allows(it, power) }
        val affixes = pickAffixes(pool, count, rng).map { rollAffix(it, power, rng) }

        val (row, col) = pickSprite(base, power, rng)
        return Equipment(base, power, rarity, dmgMin, dmgMax, armor, implicits, affixes, row, col, lootId, weight)
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
        ItemBase.BOW to 4f, ItemBase.GRIMOIRE to 4f, ItemBase.LANTERN to 4f,
        ItemBase.HELMET to 10f, ItemBase.ARMOR to 12f, ItemBase.BOOTS to 10f,
        ItemBase.AMULET to 7f, ItemBase.RING to 8f,
    ), rng)

    private fun pickRarity(floor: Int, rng: Random): Rarity = weighted(listOf(
        Rarity.NORMAL to maxOf(25f, 55f - floor * 0.3f),
        Rarity.MAGIC  to 35f,
        Rarity.RARE   to minOf(35f, 10f + floor * 0.25f),
    ), rng)

    /**
     * Icônes provisoires dans 64x64.png : la couleur suit l'élément dans son cycle (brun, or,
     * bleu), le premier tiers du tableau périodique en brun.
     */
    private fun pickSprite(base: ItemBase, power: Int, rng: Random): Pair<Int, Int> {
        val shade = (Grade.element(power) * 3) / Grade.ELEMENTS      // 0, 1, 2
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
            // Icônes provisoires : celles de l'orbe, en attendant les vraies
            ItemBase.BOW, ItemBase.GRIMOIRE, ItemBase.LANTERN -> pick(133, 0..5)
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
     * « bon *pour sa puissance* » : une épée d'Hydrogène I parfaite noterait autant qu'une épée
     * de Fer V, et on ne verrait jamais l'objet plus profond comme une amélioration.
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

    /**
     * « Épée d'Hydrogène II » / « Hydrogen Sword II ». [linked] : la base et la matière deviennent
     * des liens du lexique (l'inventaire) ; sinon le nom est nu.
     */
    fun displayName(context: Context, e: Equipment, linked: Boolean = false): String {
        val noun = context.getString(e.weight?.nounRes(e.base) ?: e.base.nounRes)
        e.isotopeSet?.let { set ->
            return context.getString(R.string.roguelike_item_set_name,
                if (linked) LexiconText.link(Lexicon.idOf(e.base), noun) else noun,
                if (linked) LexiconText.link(set.lexiconId, set.materialLabel(context)) else set.materialLabel(context))
        }
        val tier = context.resources.getStringArray(R.array.roguelike_grade_tiers)[Grade.tier(e.power) - 1]
        val material = materialName(context, e.power)
        return context.getString(R.string.roguelike_item_grade_name,
            if (linked) LexiconText.link(Lexicon.idOf(e.base), noun) else noun,
            if (linked) LexiconText.link(Lexicon.materialId(Grade.element(e.power)), material) else material,
            tier)
    }

    /**
     * Le nom de la matière d'une puissance : l'élément, et au-delà du premier cycle son mot
     * (stellaire, galactique…). En français, « de » s'élide devant une voyelle ou un h
     * (« d'Hydrogène ») ; en anglais les deux formats sont les mêmes.
     */
    fun materialName(context: Context, power: Int): String {
        val element = periodic[Grade.element(power)].localizedName(context)
        val cycle = Grade.cycle(power)
        val words = context.resources.getStringArray(R.array.roguelike_grade_cycles)
        val cycleWord = when {
            cycle == 0 -> null
            cycle < words.size -> words[cycle]
            // Au-delà du dernier mot, on le numérote : « primordial 2 », « primordial 3 »…
            else -> context.getString(R.string.roguelike_grade_cycle_numbered, words.last(), cycle - words.size + 2)
        }
        val elided = Normalizer.normalize(element.take(1), Normalizer.Form.NFD).lowercase().firstOrNull() in ELIDING
        val res = when {
            cycleWord == null -> if (elided) R.string.roguelike_item_material_elided else R.string.roguelike_item_material
            else -> if (elided) R.string.roguelike_item_material_cycle_elided else R.string.roguelike_item_material_cycle
        }
        return if (cycleWord == null) context.getString(res, element) else context.getString(res, element, cycleWord)
    }

    private val periodic by lazy { getPeriodicElements() }

    /** Les lettres devant lesquelles le français élide « de » (Hydrogène, Or, Argon…). */
    private val ELIDING = setOf('a', 'e', 'i', 'o', 'u', 'y', 'h')

    /** Des points de CA en points d'esquive, pour le joueur qui ne connaît pas D&D. */
    private fun dodgePercent(ac: Int) = Math.round(ac * ArmorClass.AC_STEP * 100)

    /**
     * Lignes de description : dégâts, armure, poids, puis toutes les stats. Rien que des noms et des
     * chiffres : ce que veut dire « Léger » ou « P4 » est dans le lexique. [linked] : les mots
     * deviennent des liens (voir [LexiconText]) ; sinon les lignes sont nues (canvas).
     */
    fun describe(context: Context, e: Equipment, linked: Boolean = false): List<String> = buildList {
        if (e.damageMax > 0) add(context.getString(R.string.roguelike_item_damage,
            DungeonNumbers.format(context, e.damageMin), DungeonNumbers.format(context, e.damageMax)))
        if (e.armor > 0) add(context.getString(R.string.roguelike_item_armor, DungeonNumbers.format(context, e.armor)))
        e.weight?.let { add(LexiconText.link(Lexicon.idOf(it), context.getString(it.labelRes))) }
        e.isotopeSet?.let { add(context.getString(R.string.roguelike_item_set_line, LexiconText.link(it.lexiconId, it.label(context)))) }
        if (e.base == ItemBase.SHIELD) add(context.getString(R.string.roguelike_item_shield_ac, dodgePercent(ArmorClass.SHIELD)))
        e.implicits.forEach { add(it.display(context, linked = true)) }
        e.affixes.forEach { add(it.display(context, linked = true)) }
    }.map { if (linked) it else LexiconText.strip(it) }

    private fun <T> weighted(list: List<Pair<T, Float>>, rng: Random): T {
        val total = list.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.001f)
        var r = rng.nextFloat() * total
        for ((item, w) in list) { r -= w; if (r <= 0f) return item }
        return list.last().first
    }
}
