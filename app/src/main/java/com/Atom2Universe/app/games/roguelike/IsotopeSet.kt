package com.Atom2Universe.app.games.roguelike

import android.content.Context
import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import com.Atom2Universe.app.periodic.getPeriodicElements
import java.text.Normalizer

/**
 * Un set d'isotope : trois pièces (casque, armure, bottes) d'un même archétype, qui ne tombent
 * que dans **la tranche de 25 étages de leur élément** (l'hydrogène aux étages 1 à 25, l'hélium
 * aux étages 26 à 50…). Voir DONJON.md, « Les sets d'isotopes ».
 *
 * Un seul set par élément, au plus : [z] est donc son identifiant. Les trois poids d'armure
 * tournent avec le numéro atomique — lourd, léger, tissu, en boucle — et l'archétype suit
 * ([archetype]).
 *
 * Le bonus ne dépend **que de l'archétype**, pas de l'isotope : trois pièces de sets du même
 * archétype (deux de deutérium et une de béryllium, par exemple) l'activent, voir
 * [Hero.setArchetype]. Il améliore le Spécial de l'archétype et donne une stat de base qui le sert
 * (PV du lourd, vitesse du léger, dégâts des sorts du mage). Rien à deux pièces.
 */
data class IsotopeSet(
    /** Le numéro atomique de l'élément. */
    val z: Int,
    /** Le nombre de masse de l'isotope : 2 pour le deutérium. */
    val mass: Int,
    /** Un nom propre, quand l'isotope en a un (le deutérium) ; sinon on écrit « Li-6 ». */
    @StringRes val nameRes: Int? = null,
    /**
     * Le tour de la table : 0 la première fois, 1 après l'oganesson, où l'on repart du deutérium
     * (« Deutérium stellaire »), et ainsi de suite avec les mots des cycles de [Grade].
     */
    val cycle: Int = 0,
) {
    /**
     * Le numéro du set dans la suite sans fin des tranches : [z] au premier tour, puis 118 de plus
     * par tour. C'est ce qu'une pièce enregistre ([Equipment.isotopeZ]) ; il vaut [z] au premier
     * tour, donc les anciennes sauvegardes restent lisibles.
     */
    val index get() = z + cycle * Grade.ELEMENTS

    /** L'archétype tourne avec l'élément : lourd, léger, tissu, en boucle. */
    val archetype: Archetype get() = Archetype.entries[(z - 1) % Archetype.entries.size]

    val firstFloor get() = (index - 1) * IsotopeSets.BAND_FLOORS + 1
    val lastFloor get() = index * IsotopeSets.BAND_FLOORS

    /** Le lien du lexique vers la fiche du set. */
    val lexiconId get() = IsotopeSets.lexiconId(z)

    /** « Deutérium », ou « Li-6 » quand l'isotope n'a pas de nom propre, suivi du mot du cycle (« Li-6 stellaire »). */
    fun label(context: Context): String {
        val name = nameRes?.let(context::getString) ?: "${symbol()}-$mass"
        val word = LootSystem.cycleWord(context, cycle) ?: return name
        return context.getString(R.string.roguelike_isotope_cycle, name, word)
    }

    /** Le symbole de l'élément (« Li »). */
    fun symbol() = periodic[z - 1].symbol

    /** Le nom prêt à suivre une pièce : « de Deutérium » ou « d'Li-6 » (l'anglais garde le nom nu). */
    fun materialLabel(context: Context): String {
        val label = label(context)
        val elided = Normalizer.normalize(label.take(1), Normalizer.Form.NFD).lowercase().firstOrNull() in ELIDING
        return context.getString(if (elided) R.string.roguelike_item_material_elided else R.string.roguelike_item_material, label)
    }

    private companion object {
        val periodic by lazy { getPeriodicElements() }
        val ELIDING = setOf('a', 'e', 'i', 'o', 'u', 'y', 'h')
    }
}

object IsotopeSets {
    /** Étages par élément : la même tranche que les noms d'objets ([Grade]). */
    const val BAND_FLOORS = 25

    /** Part des objets tombés dans la tranche qui sont une pièce du set. */
    const val DROP_SHARE = 0.08f

    /** La part réellement utilisée : les bancs de mesure la mettent à 0 pour mesurer « sans sets ». */
    @Volatile var dropShare = DROP_SHARE

    /**
     * La puissance d'une pièce : de 10 à 12 pour le premier élément, puis 10 de plus par élément.
     * C'est la puissance du dernier étage de la tranche, un peu au-dessus (voir [LootSystem.powerCenter]) :
     * meilleure que tout le butin ordinaire de la tranche, mais pas de la suivante. La fourchette
     * fait que certaines pièces valent un peu mieux que d'autres, donc qu'on les farme.
     */
    const val POWER_SPREAD = 3
    fun basePower(index: Int) = index * Grade.TIERS * Grade.POWER_PER_TIER

    /** Les trois emplacements d'un set. */
    val SLOTS = listOf(EquipSlot.HELMET, EquipSlot.CHEST, EquipSlot.BOOTS)
    val BASES = listOf(ItemBase.HELMET, ItemBase.ARMOR, ItemBase.BOOTS)

    // ── Le bonus des trois pièces, par archétype ────────────────────────────────

    /** Le Spécial revient plus vite, quel que soit l'archétype. */
    const val SPECIAL_COOLDOWN = 4
    /** Lourd : la Garde renvoie plus, et les PV max montent. */
    const val GUARD_THORNS_SHARE = 0.75f
    const val HP_SHARE = 0.25f
    /** Léger : le Coup mortel frappe plus fort, et la vitesse monte. */
    const val DEADLY_CRIT_BONUS = 2f
    const val SPEED_BONUS = 0.15f
    /** Mage : plus de doubles à l'Image miroir, et les dégâts des sorts montent. */
    const val MIRROR_IMAGES = 4
    const val SPELL_SHARE = 0.25f
    /** Vagabond : chaque coup de l'Enchaînement frappe plus fort, et les dégâts critiques montent. */
    const val CHAIN_DAMAGE_BONUS = 0.25f
    const val CRIT_DAMAGE_BONUS = 0.5f
    /** Nécromancien : un pantin de plus, et les recharges de ses sorts raccourcissent d'un tour (la SAG). */
    const val PUPPETS = 3
    const val RECHARGE_CUT = 1

    /** Les sets qui existent. Un par élément au plus, et aucun pour un élément sans autre isotope. */
    val ALL = listOf(
        IsotopeSet(1, 2, R.string.roguelike_isotope_deuterium),   // lourd
        IsotopeSet(2, 3),                                          // léger
        IsotopeSet(3, 6),                                          // mage
        IsotopeSet(4, 9),                                          // vagabond
        IsotopeSet(5, 10),                                         // nécromancien
        // Ensuite, la boucle : les archétypes tournent avec Z. L'isotope est le premier stable de
        // nuclides.json (le plus longtemps vivant quand l'élément n'en a aucun). Rien pour Sc, Pr, Tb, Ho, Og.
        IsotopeSet(6, 12),        // C, lourd
        IsotopeSet(7, 14),        // N, léger
        IsotopeSet(8, 16),        // O, mage
        IsotopeSet(9, 19),        // F, vagabond
        IsotopeSet(10, 20),       // Ne, nécromancien
        IsotopeSet(11, 23),       // Na, lourd
        IsotopeSet(12, 24),       // Mg, léger
        IsotopeSet(13, 27),       // Al, mage
        IsotopeSet(14, 28),       // Si, vagabond
        IsotopeSet(15, 31),       // P, nécromancien
        IsotopeSet(16, 32),       // S, lourd
        IsotopeSet(17, 35),       // Cl, léger
        IsotopeSet(18, 36),       // Ar, mage
        IsotopeSet(19, 39),       // K, vagabond
        IsotopeSet(20, 40),       // Ca, nécromancien
        IsotopeSet(22, 46),       // Ti, léger
        IsotopeSet(23, 50),       // V, mage
        IsotopeSet(24, 50),       // Cr, vagabond
        IsotopeSet(25, 55),       // Mn, nécromancien
        IsotopeSet(26, 54),       // Fe, lourd
        IsotopeSet(27, 59),       // Co, léger
        IsotopeSet(28, 58),       // Ni, mage
        IsotopeSet(29, 63),       // Cu, vagabond
        IsotopeSet(30, 64),       // Zn, nécromancien
        IsotopeSet(31, 69),       // Ga, lourd
        IsotopeSet(32, 70),       // Ge, léger
        IsotopeSet(33, 75),       // As, mage
        IsotopeSet(34, 74),       // Se, vagabond
        IsotopeSet(35, 79),       // Br, nécromancien
        IsotopeSet(36, 80),       // Kr, lourd
        IsotopeSet(37, 85),       // Rb, léger
        IsotopeSet(38, 84),       // Sr, mage
        IsotopeSet(39, 89),       // Y, vagabond
        IsotopeSet(40, 90),       // Zr, nécromancien
        IsotopeSet(41, 93),       // Nb, lourd
        IsotopeSet(42, 94),       // Mo, léger
        IsotopeSet(43, 100),      // Tc, mage
        IsotopeSet(44, 96),       // Ru, vagabond
        IsotopeSet(45, 103),      // Rh, nécromancien
        IsotopeSet(46, 102),      // Pd, lourd
        IsotopeSet(47, 107),      // Ag, léger
        IsotopeSet(48, 108),      // Cd, mage
        IsotopeSet(49, 113),      // In, vagabond
        IsotopeSet(50, 112),      // Sn, nécromancien
        IsotopeSet(51, 121),      // Sb, lourd
        IsotopeSet(52, 120),      // Te, léger
        IsotopeSet(53, 127),      // I, mage
        IsotopeSet(54, 124),      // Xe, vagabond
        IsotopeSet(55, 133),      // Cs, nécromancien
        IsotopeSet(56, 134),      // Ba, lourd
        IsotopeSet(57, 138),      // La, léger
        IsotopeSet(58, 136),      // Ce, mage
        IsotopeSet(60, 142),      // Nd, nécromancien
        IsotopeSet(61, 147),      // Pm, lourd
        IsotopeSet(62, 144),      // Sm, léger
        IsotopeSet(63, 151),      // Eu, mage
        IsotopeSet(64, 152),      // Gd, vagabond
        IsotopeSet(66, 156),      // Dy, lourd
        IsotopeSet(68, 162),      // Er, mage
        IsotopeSet(69, 169),      // Tm, vagabond
        IsotopeSet(70, 168),      // Yb, nécromancien
        IsotopeSet(71, 175),      // Lu, lourd
        IsotopeSet(72, 176),      // Hf, léger
        IsotopeSet(73, 180),      // Ta, mage
        IsotopeSet(74, 180),      // W, vagabond
        IsotopeSet(75, 185),      // Re, nécromancien
        IsotopeSet(76, 184),      // Os, lourd
        IsotopeSet(77, 191),      // Ir, léger
        IsotopeSet(78, 190),      // Pt, mage
        IsotopeSet(79, 197),      // Au, vagabond
        IsotopeSet(80, 196),      // Hg, nécromancien
        IsotopeSet(81, 203),      // Tl, lourd
        IsotopeSet(82, 204),      // Pb, léger
        IsotopeSet(83, 209),      // Bi, mage
        IsotopeSet(84, 210),      // Po, vagabond
        IsotopeSet(85, 211),      // At, nécromancien
        IsotopeSet(86, 220),      // Rn, lourd
        IsotopeSet(87, 223),      // Fr, léger
        IsotopeSet(88, 226),      // Ra, mage
        IsotopeSet(89, 227),      // Ac, vagabond
        IsotopeSet(90, 232),      // Th, nécromancien
        IsotopeSet(91, 231),      // Pa, lourd
        IsotopeSet(92, 236),      // U, léger
        IsotopeSet(93, 237),      // Np, mage
        IsotopeSet(94, 240),      // Pu, vagabond
        IsotopeSet(95, 243),      // Am, nécromancien
        IsotopeSet(96, 247),      // Cm, lourd
        IsotopeSet(97, 247),      // Bk, léger
        IsotopeSet(98, 251),      // Cf, mage
        IsotopeSet(99, 252),      // Es, vagabond
        IsotopeSet(100, 257),     // Fm, nécromancien
        IsotopeSet(101, 259),     // Md, lourd
        IsotopeSet(102, 259),     // No, léger
        IsotopeSet(103, 262),     // Lr, mage
        IsotopeSet(104, 257),     // Rf, vagabond
        IsotopeSet(105, 262),     // Db, nécromancien
        IsotopeSet(106, 266),     // Sg, lourd
        IsotopeSet(107, 268),     // Bh, léger
        IsotopeSet(108, 269),     // Hs, mage
        IsotopeSet(109, 274),     // Mt, vagabond
        IsotopeSet(110, 281),     // Ds, nécromancien
        IsotopeSet(111, 281),     // Rg, lourd
        IsotopeSet(112, 285),     // Cn, léger
        IsotopeSet(113, 285),     // Nh, mage
        IsotopeSet(114, 289),     // Fl, vagabond
        IsotopeSet(115, 289),     // Mc, nécromancien
        IsotopeSet(116, 293),     // Lv, lourd
        IsotopeSet(117, 293),     // Ts, léger
    )

    private val BY_Z by lazy { ALL.associateBy { it.z } }

    /** Le set de l'élément [z] au premier tour, ou null. */
    fun ofElement(z: Int) = BY_Z[z]

    /** Le set de ce numéro ([IsotopeSet.index]) : les tours suivants reprennent les mêmes sets, un mot de cycle en plus. */
    fun of(index: Int): IsotopeSet? {
        if (index < 1) return null
        val base = BY_Z[(index - 1) % Grade.ELEMENTS + 1] ?: return null
        val cycle = (index - 1) / Grade.ELEMENTS
        return if (cycle == 0) base else base.copy(cycle = cycle)
    }

    /** Le set qui tombe à cet étage, ou null. Les tranches se suivent sans fin : après l'oganesson, on repart du deutérium. */
    fun forFloor(floor: Int) = if (floor < 1) null else of((floor - 1) / BAND_FLOORS + 1)

    fun lexiconId(z: Int) = "set_$z"
}
