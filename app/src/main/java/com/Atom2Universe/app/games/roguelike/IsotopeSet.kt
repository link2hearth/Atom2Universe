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
 * Porter les trois pièces ([Hero.activeSet]) améliore le Spécial de l'archétype et, pour ce set,
 * ajoute [hpShare] de PV max. Il n'y a rien à deux pièces.
 */
class IsotopeSet(
    /** Le numéro atomique de l'élément. */
    val z: Int,
    /** Le nombre de masse de l'isotope : 2 pour le deutérium. */
    val mass: Int,
    /** Un nom propre, quand l'isotope en a un (le deutérium) ; sinon on écrit « Li-6 ». */
    @StringRes val nameRes: Int? = null,
    /** Part de PV max en plus quand les trois pièces sont portées. */
    val hpShare: Float = 0f,
) {
    /** L'archétype tourne avec l'élément : lourd, léger, tissu, en boucle. */
    val archetype: Archetype get() = Archetype.entries[(z - 1) % Archetype.entries.size]

    val firstFloor get() = (z - 1) * IsotopeSets.BAND_FLOORS + 1
    val lastFloor get() = z * IsotopeSets.BAND_FLOORS

    /** Le lien du lexique vers la fiche du set. */
    val lexiconId get() = IsotopeSets.lexiconId(z)

    /** « Deutérium », ou « Li-6 » quand l'isotope n'a pas de nom propre. */
    fun label(context: Context): String =
        nameRes?.let(context::getString) ?: "${symbol()}-$mass"

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

    /**
     * La puissance d'une pièce : de 10 à 12 pour le premier élément, puis 10 de plus par élément.
     * C'est la puissance du dernier étage de la tranche, un peu au-dessus (voir [LootSystem.powerCenter]) :
     * meilleure que tout le butin ordinaire de la tranche, mais pas de la suivante. La fourchette
     * fait que certaines pièces valent un peu mieux que d'autres, donc qu'on les farme.
     */
    const val POWER_SPREAD = 3
    fun basePower(z: Int) = z * Grade.TIERS * Grade.POWER_PER_TIER

    /** Les trois emplacements d'un set. */
    val SLOTS = listOf(EquipSlot.HELMET, EquipSlot.CHEST, EquipSlot.BOOTS)
    val BASES = listOf(ItemBase.HELMET, ItemBase.ARMOR, ItemBase.BOOTS)

    /** Le Garde du guerrier renvoie plus quand il porte le set, et revient plus vite. */
    const val GUARD_THORNS_SHARE = 0.75f
    const val SPECIAL_COOLDOWN = 4

    /** Les sets qui existent. Un par élément au plus, et aucun pour un élément sans autre isotope. */
    val ALL = listOf(
        IsotopeSet(1, 2, R.string.roguelike_isotope_deuterium, hpShare = 0.25f),
    )

    fun of(z: Int) = ALL.firstOrNull { it.z == z }

    /** Le set qui tombe à cet étage, ou null. */
    fun forFloor(floor: Int) = ALL.firstOrNull { floor in it.firstFloor..it.lastFloor }

    fun lexiconId(z: Int) = "set_$z"
}
