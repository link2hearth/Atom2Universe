package com.Atom2Universe.app.science.mycology

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.Atom2Universe.app.R

/** Statut de comestibilité, du plus rassurant au plus grave (l'ordre sert aux filtres et aux tris). */
enum class FungusStatus(@param:StringRes val label: Int, val color: Int) {
    EDIBLE(R.string.myco_status_edible, 0xFF2E7D32.toInt()),
    COOKED(R.string.myco_status_cooked, 0xFFB26A00.toInt()),
    INEDIBLE(R.string.myco_status_inedible, 0xFF6B6B6B.toInt()),
    TOXIC(R.string.myco_status_toxic, 0xFFD84315.toInt()),
    DEADLY(R.string.myco_status_deadly, 0xFF8E0F24.toInt())
}

/** Les trois vues d'une planche. */
enum class PlateMode(@param:StringRes val label: Int) {
    SIDE(R.string.myco_view_side),
    SECTION(R.string.myco_view_section),
    UNDER(R.string.myco_view_under)
}

enum class CapShape { CONVEX, BELL, DEPRESSED, FUNNEL, MOREL, BRAIN }
enum class Hymenium { GILLS, PORES, RIDGES, NONE }
enum class GillAttach { FREE, ADNATE, DECURRENT }
enum class Interior { SOLID, STUFFED, HOLLOW, CHAMBERED }
enum class StipeForm { CYLINDER, CLUB, BULB, MARGINATE, TAPER_DOWN, FLARED, BARREL }
enum class RingKind { NONE, SKIRT, STRIATE, SLIDING, FLOCCOSE, FUGACIOUS, ZONE }
enum class VolvaKind { NONE, SAC, RIMS }

/** Décors du chapeau, dessinés par-dessus la couleur de fond. */
sealed class CapDeco {
    /** Verrues ou plaques du voile (tue-mouches, panthère). */
    class Warts(val color: Int, val count: Int, val size: Float) : CapDeco()
    /** Écailles en couronnes concentriques (coulemelle, petites lépiotes). */
    class Scales(val color: Int, val rows: Int, val size: Float, val from: Float = 0.2f) : CapDeco()
    /** Fibrilles rayonnantes depuis le centre. */
    class Fibrils(val color: Int, val count: Int, val alpha: Int = 90) : CapDeco()
    /** Flocons irréguliers (amanite rougissante). */
    class Flakes(val color: Int, val count: Int, val size: Float) : CapDeco()
    /** Mèches retroussées étagées sur un chapeau haut (coprin chevelu) : [rows] étages, [size] en cm. */
    class Shaggy(val color: Int, val rows: Int, val size: Float) : CapDeco()
    /** Fines stries sur la marge (amanite panthère). */
    class Striate(val alpha: Int = 120) : CapDeco()
    /** Surface visqueuse et brillante. */
    object Sticky : CapDeco()
    /** Surface feutrée : bord plus clair et duveteux. */
    object Felt : CapDeco()
}

/** Décors du pied. */
sealed class StipeDeco {
    /** Réseau en mailles (cèpes) ; [from] = part de la hauteur couverte en partant du haut. */
    class Net(val color: Int, val from: Float, val cell: Float, val alpha: Int = 200) : StipeDeco()
    /** Bandes en zigzag, « peau de serpent » (coulemelle). */
    class Bands(val color: Int, val gap: Float, val alpha: Int = 200) : StipeDeco()
    /** Fines fibrilles ondulées (phalloïde). */
    class Fibrils(val color: Int, val alpha: Int = 70) : StipeDeco()
    /** Écailles sombres sur la partie basse ([from] = part de la hauteur couverte en partant du bas). */
    class Scales(val color: Int, val from: Float) : StipeDeco()
    /** Sillons verticaux (gyromitre). */
    class Furrows(val color: Int) : StipeDeco()
}

enum class StainZone { CAP_SKIN, ABOVE_TUBES, STIPE_BASE, STIPE_ALL, STIPE_TOP, CAP_EDGE, HYMENIUM }

/** Changement de couleur de la chair ou des lames à la blessure. */
class Stain(val zone: StainZone, val color: Int, val strength: Float)

/** Points d'accroche des repères : la vue les calcule d'après la géométrie. */
enum class Anchor {
    CAP, EDGE, UMBO, FACE, RING, VOLVA, BULB, STIPE_U, STIPE_M, BASE, SKIN,
    S_FLESH, S_HYM, S_STIPE, S_BASE, S_CAVITY, S_STAIN, S_RING, S_VOLVA,
    U_RIM, U_HYM, U_CENTER
}

/** Un repère : un mot posé sur un point du dessin. */
class Mark(val at: Anchor, @param:StringRes val label: Int)

/**
 * Tout ce qu'il faut pour dessiner un champignon : mesures en centimètres (typiques, pas exactes),
 * couleurs et caractères d'identification. L'axe du pied est x = 0, le sol y = 0.
 */
class FungusLook(
    // Chapeau
    val capDiam: Float,
    val capRise: Float,
    val capShape: CapShape = CapShape.CONVEX,
    val capN: Float = 2.2f,
    val edgeDrop: Float = 0f,
    val umbo: Float = 0f,
    val dip: Float = 0f,
    val inrolled: Boolean = false,
    val capCenter: Int,
    val capMid: Int,
    val capEdge: Int,
    val capDecos: List<CapDeco> = emptyList(),
    /** Épaisseur de la chair du chapeau au centre (cm) ; 0 = automatique. */
    val fleshThick: Float = 0f,
    // Pied
    val stipeH: Float,
    val stipeW: Float,
    val stipeBaseW: Float = stipeW,
    val stipeForm: StipeForm = StipeForm.CYLINDER,
    val bulbW: Float = 0f,
    val bulbH: Float = 0f,
    val lean: Float = 0f,
    val stipeTop: Int,
    val stipeBottom: Int = stipeTop,
    val stipeDecos: List<StipeDeco> = emptyList(),
    val interior: Interior = Interior.SOLID,
    // Dessous du chapeau
    val hymenium: Hymenium = Hymenium.GILLS,
    val hymColor: Int,
    val hymInner: Int = hymColor,
    val attach: GillAttach = GillAttach.FREE,
    val hymDepth: Float = 0.6f,
    val forked: Boolean = false,
    /** Lames très serrées (1) ou espacées (0). */
    val crowd: Float = 0.7f,
    // Accessoires
    val ring: RingKind = RingKind.NONE,
    val ringAt: Float = 0.78f,
    val ringColor: Int = 0xFFF4F0E4.toInt(),
    val volva: VolvaKind = VolvaKind.NONE,
    val volvaH: Float = 0f,
    val volvaColor: Int = 0xFFF6F4EA.toInt(),
    // Chair
    val flesh: Int,
    val stipeFlesh: Int = flesh,
    val stains: List<Stain> = emptyList(),
    val spore: Int,
    val cluster: Int = 1,
    val seed: Int = 1,
    // Repères
    val marksSide: List<Mark> = emptyList(),
    val marksSection: List<Mark> = emptyList(),
    val marksUnder: List<Mark> = emptyList()
) {
    /** Inclinaison apparente du plan du chapeau : rapport du demi-axe vertical au rayon de l'ellipse du dessous. */
    val tilt: Float get() = when (hymenium) {
        Hymenium.PORES -> 0.24f
        else -> 0.2f
    }

    /** Hauteur totale (pied + chapeau). */
    val totalHeight: Float get() = stipeH + capRise + 0.4f
}

/**
 * Une espèce du catalogue. Les textes viennent tous des ressources : le nom dans la langue courante,
 * l'autre langue en « aussi appelé », et le nom latin (non traduit).
 */
class FungusSpecies(
    val id: String,
    @param:StringRes val name: Int,
    @param:StringRes val altNames: Int,
    @param:StringRes val latin: Int,
    val status: FungusStatus,
    /** Vente interdite en France : une pastille en plus du statut. */
    val saleBanned: Boolean = false,
    val look: FungusLook,
    @param:ArrayRes val traits: Int,
    @param:StringRes val note: Int,
    /** Histoire du classement (0 = rien à raconter). */
    @param:StringRes val history: Int = 0
)

/** Un groupe de sosies. */
class FungusGroup(
    val id: String,
    @param:StringRes val title: Int,
    val members: List<String>,
    @param:ArrayRes val points: Int,
    /** Étiquette courte de l'origine de la confusion (rapport ANSES, décret…). */
    @param:StringRes val evidence: Int
)

/** Un champignon dont le classement a changé. */
class FungusChange(
    val speciesId: String,
    val before: FungusStatus,
    val now: FungusStatus,
    /** Années repères, dans l'ordre. */
    val years: List<Int>,
    @param:StringRes val story: Int
)
