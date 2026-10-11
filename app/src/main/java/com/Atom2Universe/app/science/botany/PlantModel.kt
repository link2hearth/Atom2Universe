package com.Atom2Universe.app.science.botany

import androidx.annotation.StringRes
import com.Atom2Universe.app.R

/** Stable IDs, not positions: a new section, family or comparison is just another catalogue entry. */
data class PlantSection(val id: String, @param:StringRes val title: Int, @param:StringRes val description: Int)
/** Editorial collections, not exclusive native ranges. A taxon may occur in several collections. */
data class PlantCollection(val id: String, @param:StringRes val title: Int, @param:StringRes val description: Int)
data class PlantFamily(val id: String, @param:StringRes val name: Int, @param:StringRes val description: Int,
                       @param:StringRes val aliases: Int = 0)
data class PlantGroup(val id: String, @param:StringRes val title: Int, val members: List<String>, @param:StringRes val differences: Int)

enum class LeafShape(val label: Int) {
    OVAL(R.string.bot_leaf_oval), LANCE(R.string.bot_leaf_lance), HEART(R.string.bot_leaf_heart),
    TRIANGLE(R.string.bot_leaf_triangle), ROUND(R.string.bot_leaf_round), OAK(R.string.bot_leaf_oak),
    MAPLE(R.string.bot_leaf_maple), PINNATE(R.string.bot_leaf_pinnate), PALMATE(R.string.bot_leaf_palmate),
    TRIFOLIATE(R.string.bot_leaf_trifoliate), DIVIDED(R.string.bot_leaf_divided),
    NEEDLE(R.string.bot_leaf_needle), SCALE(R.string.bot_leaf_scale), LINEAR(R.string.bot_leaf_linear),
    PALMATILOBED(R.string.bot_leaf_palmatilobed), BIPINNATE(R.string.bot_leaf_bipinnate), BIFOLIATE(R.string.bot_leaf_bifoliate)
}
enum class LeafMargin(val label: Int) {
    ENTIRE(R.string.bot_margin_entire), SERRATE(R.string.bot_margin_serrate), LOBED(R.string.bot_margin_lobed), SPINY(R.string.bot_margin_spiny)
}
enum class LeafArrangement(val label: Int) {
    ALTERNATE(R.string.bot_arr_alternate), OPPOSITE(R.string.bot_arr_opposite), WHORLED(R.string.bot_arr_whorled),
    ROSETTE(R.string.bot_arr_rosette), FASCICLE(R.string.bot_arr_fascicle)
}
enum class FlowerShape(val label: Int) {
    CATKIN(R.string.bot_fl_catkin), CONIFER(R.string.bot_fl_conifer), STAR(R.string.bot_fl_star),
    CUP(R.string.bot_fl_cup), DAISY(R.string.bot_fl_daisy), THISTLE(R.string.bot_fl_thistle),
    UMBEL(R.string.bot_fl_umbel), CORYMB(R.string.bot_fl_corymb), PEA(R.string.bot_fl_pea),
    BELL(R.string.bot_fl_bell), LIPPED(R.string.bot_fl_lipped), GRASS(R.string.bot_fl_grass),
    SPIKE(R.string.bot_fl_spike), PANICLE(R.string.bot_fl_panicle), APETALOUS(R.string.bot_fl_apetalous),
    LIGULATE(R.string.bot_fl_ligulate), RACEME(R.string.bot_fl_raceme), GLOBOSE_HEAD(R.string.bot_fl_globose_head)
}
enum class FruitShape(val label: Int) {
    ACORN(R.string.bot_fr_acorn), NUT(R.string.bot_fr_nut), BEECH(R.string.bot_fr_beech),
    CHESTNUT(R.string.bot_fr_chestnut), HORSECHESTNUT(R.string.bot_fr_horsechestnut),
    DOUBLE_SAMARA(R.string.bot_fr_double_samara), SAMARA(R.string.bot_fr_samara),
    CONE(R.string.bot_fr_cone), DRUPE(R.string.bot_fr_drupe), POME(R.string.bot_fr_pome),
    HIP(R.string.bot_fr_hip), POD(R.string.bot_fr_pod), CAPSULE(R.string.bot_fr_capsule),
    ACHENE(R.string.bot_fr_achene), GRAIN(R.string.bot_fr_grain), SCHIZOCARP(R.string.bot_fr_schizocarp),
    BERRY(R.string.bot_fr_berry), SILIQUE(R.string.bot_fr_silique), SILICLE(R.string.bot_fr_silicle),
    FOLLICLE(R.string.bot_fr_follicle), INDEHISCENT(R.string.bot_fr_indehiscent)
}
enum class Habitat(val label: Int) {
    WOOD(R.string.bot_hab_wood), HEDGE(R.string.bot_hab_hedge), MEADOW(R.string.bot_hab_meadow),
    WET(R.string.bot_hab_wet), HEATH(R.string.bot_hab_heath), MOUNTAIN(R.string.bot_hab_mountain),
    DISTURBED(R.string.bot_hab_disturbed), PARK(R.string.bot_hab_park),
    SAVANNA(R.string.bot_hab_savanna), DRY_SCRUB(R.string.bot_hab_dry_scrub)
}
enum class FlowerColor(val label: Int, val ink: Int) {
    WHITE(R.string.bot_color_white, 0xfff6f1df.toInt()), YELLOW(R.string.bot_color_yellow, 0xffe6be35.toInt()),
    PINK(R.string.bot_color_pink, 0xffd77eab.toInt()), PURPLE(R.string.bot_color_purple, 0xff9962ae.toInt()),
    BLUE(R.string.bot_color_blue, 0xff667fbc.toInt()), RED(R.string.bot_color_red, 0xffdc5141.toInt()),
    GREEN(R.string.bot_color_green, 0xffa5aa5a.toInt())
}
enum class PlantView(val label: Int) {
    HABIT(R.string.bot_view_habit), LEAF(R.string.bot_view_leaf), FLOWER(R.string.bot_view_flower), FRUIT(R.string.bot_view_fruit)
}
enum class Crown { ROUND, COLUMN, SPREAD, CONICAL, PINE, SHRUB, HERB, GRASS }

/** Measurements are representative drawing sizes, independent from the species' variable height range. */
data class PlantLook(
    val crown: Crown, val leaf: LeafShape, val margin: LeafMargin, val arrangement: LeafArrangement,
    val flower: FlowerShape, val fruit: FruitShape, val color: FlowerColor,
    val leafCm: Float, val flowerCm: Float, val fruitCm: Float,
    val leaflets: Int = 7, val petals: Int = 5, val petiole: Float = .22f,
    val fruitStalk: Float = .25f, val samaraAngle: Float = 65f, val needleCount: Int = 1,
    val coneUpright: Boolean = false, val bark: Int = 0xff746454.toInt(),
    val fruitColor: Int = 0xff97613d.toInt(), val leafColor: Int = 0xff58794c.toInt(),
    val thorny: Boolean = false, val seed: Int = 1,
    val leafDetail: LeafDetail = LeafDetail(), val architecture: TreeArchitecture? = null,
    val flowerDetail: FlowerDetail = FlowerDetail(), val fruitDetail: FruitDetail = FruitDetail(),
    val herbDetail: HerbArchitecture? = null, val fineShrub: Boolean = false
)

data class PlantSpecies(
    val id: String, @param:StringRes val name: Int, @param:StringRes val latin: Int,
    @param:StringRes val aliases: Int, val familyId: String, val sectionIds: Set<String>,
    val heightMinCm: Int, val heightMaxCm: Int, val flowering: Set<Int>, val fruiting: Set<Int>,
    val habitats: Set<Habitat>, val evergreen: Boolean, val look: PlantLook,
    @param:StringRes val description: Int, @param:StringRes val distribution: Int,
    /** Resources describing the actual illustrated characters in each view; also used by TalkBack. */
    val captions: Map<PlantView, Int>, val sourceIds: Set<String>,
    /** Real variation beyond the representative drawing (basal vs stem leaves, colour forms, etc.). */
    val additionalLeaves: Set<LeafShape> = emptySet(),
    val additionalArrangements: Set<LeafArrangement> = emptySet(),
    val additionalMargins: Set<LeafMargin> = emptySet(),
    val additionalColors: Set<FlowerColor> = emptySet(),
    val variablePersistence: Boolean = false,
    val collectionIds: Set<String> = setOf("europe"),
    /** Empty month sets mean no transferable monthly calendar, not absence of flowering/fruiting. */
    @param:StringRes val phenologyNote: Int = 0
) {
    val leafShapes get() = additionalLeaves + look.leaf
    val leafArrangements get() = additionalArrangements + look.arrangement
    val leafMargins get() = additionalMargins + look.margin
    val flowerColors get() = additionalColors + look.color
}

data class BotanySource(val id: String, @param:StringRes val title: Int, val url: String)
