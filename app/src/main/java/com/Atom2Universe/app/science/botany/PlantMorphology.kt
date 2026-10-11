package com.Atom2Universe.app.science.botany

/**
 * Drawing traits, independent of species IDs: new taxa can reuse or refine these profiles.
 * Diagnostic reference plates: woodlandtrust.org.uk/trees-woods-and-wildlife/british-trees/
 * and conifers.org/pi/ (Abies alba, Picea abies, Larix decidua).
 * Crown outlines represent one adult habit; age, exposure and competition alter them greatly.
 */
enum class LeafBase { TAPERED, ROUNDED, CORDATE, AURICLED }
enum class LeafTip { POINTED, ACUMINATE, ROUNDED, NOTCHED }
enum class LeafTeeth { AUTO, DOUBLE, CRENATE, WAVY }
enum class Venation { PINNATE, PALMATE, ARCUATE, PARALLEL, THREE_BASAL }
enum class BarkSurface { FURROWED, SMOOTH, FLUTED, PAPERY, PLATED, LENTICELS }
enum class ConiferSpray { FLAT, PENDULOUS, TUFTED }

data class HerbArchitecture(
    val basalLeaf: LeafShape? = null,
    val basalDetail: LeafDetail? = null,
    val stemLeaf: LeafShape? = null,
    val creeping: Boolean = false,
    val branches: Boolean = true,
    val flowerScale: Float = .22f,
    val basalRosette: Boolean = true,
    val lowerLeavesOpposite: Boolean = false
)

/** Organ profiles are reusable by future taxa, without adding species-ID branches to the painters. */
enum class CorollaForm { RADIAL, POPPY, VIOLET, COWSLIP, FOXGLOVE, HAREBELL, HEATHER, URN, BIFID, MALLOW, SPEEDWELL,
    BAOBAB, TAMARIND, SENNA, CORONA, HIBISCUS, SESAME }
enum class FlowerCluster {
    DEFAULT, CLOVER, UNILATERAL, SMALL_UMBEL, FEW_FLOWERS, STERILE_RIM, LINDEN,
    GLOBOSE_CATKIN, HAZEL, UPRIGHT_CATKIN, PENDANT_RACEME, RYEGRASS, COCKSFOOT, QUAKING_GRASS,
    WILLOW, AXILLARY, SIMPLE_UMBEL, RACEMOSE, BRUSH_SPIKE, BRISTLY_SPIKE, PAIRED_RACEMES
}
data class FlowerDetail(
    val corolla: CorollaForm = CorollaForm.RADIAL,
    val cluster: FlowerCluster = FlowerCluster.DEFAULT,
    val petalWidth: Float = 26f,
    val stamens: Int = 20,
    val rays: Int = 24,
    val spikeLength: Float = 65f,
    val darkCentre: Boolean = false,
    val throatMark: Boolean = false,
    val perianth: Boolean = true,
    val discColor: Int = 0xffd9b33c.toInt(),
    val notchDepth: Float = .45f,
    val hollowReceptacle: Boolean = false,
    val fusedCalyx: Boolean = false,
    val female: Boolean = false,
    val styles: Int = 0,
    val shortStamens: Int = 0
)
enum class CapsuleForm { OVOID, PORICIDAL_DISC, PYXIS, THREE_VALVES, FOUR_LOBES, THREE_HORNS, COTTON, BASAL_PORES, APICAL_TEETH, HEART, BEAKED, INFLATED_CALYX }
enum class ConeForm { SPRUCE, FIR, PINE, LARCH, ALDER }
enum class SamaraForm { SINGLE, THREE_LOBED_BRACT, TWO_WINGS, ORBICULAR, FOUR_WINGS }
enum class SchizocarpForm { PAIRED, CRANESBILL, WHEEL }
enum class AcheneForm { SIMPLE, AGGREGATE, FOUR_NUTLETS, LINDEN, PAPPUS, SILKY_CLUSTER }
enum class PodForm { STRAIGHT, SPIRAL, CURLED, PULPY, FLAT_CURVED }
data class FruitDetail(
    val capsule: CapsuleForm = CapsuleForm.OVOID,
    val cone: ConeForm = ConeForm.SPRUCE,
    val samara: SamaraForm = SamaraForm.SINGLE,
    val achene: AcheneForm = AcheneForm.SIMPLE,
    val count: Int = 3,
    val elongation: Float = 1f,
    val bloom: Boolean = false,
    val hookedRibs: Boolean = false,
    val beak: Boolean = false,
    val podSeeds: Int = 6,
    val podWidth: Float = 18f,
    val shortPod: Boolean = false,
    val acheneWidth: Float = 12f,
    val acheneRibs: Int = 7,
    val schizocarp: SchizocarpForm = SchizocarpForm.PAIRED,
    val capsuleTeeth: Int = 5,
    val reflexedTeeth: Boolean = false,
    val persistentCalyx: Boolean = false,
    val raceme: Boolean = false,
    val pappusScales: Boolean = false,
    val prominentRibs: Boolean = false,
    val globoseCapsule: Boolean = false,
    val podForm: PodForm = PodForm.STRAIGHT
)

data class LeafDetail(
    /** Half-width / blade length; null uses the basic leaf-shape proportions. */
    val width: Float? = null,
    val base: LeafBase = LeafBase.TAPERED,
    val tip: LeafTip = LeafTip.POINTED,
    val teeth: LeafTeeth = LeafTeeth.AUTO,
    val veinPairs: Int = 8,
    val venation: Venation = Venation.PINNATE,
    val asymmetry: Float = .035f,
    val lobePairs: Int = 5,
    val lobeDepth: Float = .38f,
    val roundedLobes: Boolean = true,
    val ciliate: Boolean = false,
    val glossy: Boolean = false,
    val leafletWidth: Float = .22f,
    val leafletPetiole: Float = .06f,
    val leafletWidestAboveMiddle: Boolean = false,
    val basalOffset: Float = 0f,
    val leafletTip: LeafTip = LeafTip.ACUMINATE,
    val leafletShape: LeafShape = LeafShape.LANCE,
    val leafletAngle: Float? = null,
    val palmateSegments: Int = 5,
    val petioleGlands: Boolean = false,
    val subdividedLobes: Boolean = false,
    val terminalTendril: Boolean = false,
    val pinnaPairs: Int = 5,
    val pinnulePairs: Int = 10
)

/** Representative free-growing adult habit, not an identification key or a fixed growth form. */
data class TreeArchitecture(
    val radius: Float = 90f,
    val top: Float = 38f,
    val bottom: Float = 202f,
    val fork: Float = 194f,
    val trunkWidth: Float = 15f,
    val crooked: Float = 5f,
    val density: Float = .85f,
    val droop: Float = 0f,
    val barkSurface: BarkSurface = BarkSurface.FURROWED,
    val spray: ConiferSpray = ConiferSpray.FLAT,
    val bottleTrunk: Boolean = false,
    val umbrella: Boolean = false
)
