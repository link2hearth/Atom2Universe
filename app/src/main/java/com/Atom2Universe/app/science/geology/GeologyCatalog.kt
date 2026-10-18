package com.Atom2Universe.app.science.geology

import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.timeline.EarthPeriods

enum class EarthChapter(@param:StringRes val title: Int, @param:StringRes val intro: Int) {
    INTERIOR(R.string.geo_interior, R.string.geo_interior_intro),
    PLATES(R.string.geo_plates, R.string.geo_plates_intro),
    MOUNTAINS(R.string.geo_mountains, R.string.geo_mountains_intro),
    ROCKS(R.string.geo_rocks, R.string.geo_rocks_intro),
    TIME(R.string.geo_time, R.string.geo_time_intro)
}

enum class EarthScene(@param:StringRes val title: Int, val chapter: EarthChapter, val ids: List<String>) {
    GLOBE(R.string.geo_globe, EarthChapter.INTERIOR, listOf("crust", "upper_mantle", "lower_mantle", "outer_core", "inner_core")),
    SHELL(R.string.geo_shell, EarthChapter.INTERIOR, listOf("ocean_crust", "continent_crust", "moho", "lithosphere", "asthenosphere")),
    RIDGE(R.string.geo_ridge, EarthChapter.PLATES, listOf("ridge", "basalt", "plate_motion", "ocean_age")),
    SUBDUCTION(R.string.geo_subduction, EarthChapter.PLATES, listOf("subduction", "trench", "arc", "earthquake", "andes")),
    COLLISION(R.string.geo_collision, EarthChapter.PLATES, listOf("collision", "fold", "root", "himalaya")),
    TRANSFORM(R.string.geo_transform, EarthChapter.PLATES, listOf("transform", "earthquake", "plates_world")),
    HOTSPOT(R.string.geo_hotspot, EarthChapter.PLATES, listOf("hotspot", "hawaii", "plates_world")),
    FOLDS(R.string.geo_folds, EarthChapter.MOUNTAINS, listOf("fold", "thrust", "root", "alps", "himalaya")),
    FAULTS(R.string.geo_faults, EarthChapter.MOUNTAINS, listOf("normal_fault", "rift", "plate_motion")),
    LANDSCAPE(R.string.geo_landscape, EarthChapter.MOUNTAINS, listOf("erosion", "glacier", "isostasy", "old_mountains")),
    STRATA(R.string.geo_strata, EarthChapter.ROCKS, listOf("sediment", "sandstone", "limestone", "shale", "fossil")),
    ROCK_CYCLE(R.string.geo_rock_cycle, EarthChapter.ROCKS, listOf("igneous", "granite", "basalt", "metamorphic", "rock_cycle")),
    ARCHIVE(R.string.geo_archive, EarthChapter.ROCKS, listOf("superposition", "unconformity", "dating", "crosscut")),
    AGES(R.string.geo_ages, EarthChapter.TIME, EarthPeriods.all.map { it.id })
}

enum class EarthSource(@param:StringRes val title: Int, val url: String) {
    INTERIOR(R.string.geo_source_interior, "https://pubs.usgs.gov/gip/interior/"),
    MODEL(R.string.geo_source_model, "https://www.nps.gov/subjects/geology/plate-tectonics-inner-earth-model.htm"),
    MOTION(R.string.geo_source_motion, "https://www.nps.gov/subjects/geology/plate-tectonics-evidence-of-plate-motions.htm"),
    RIDGE(R.string.geo_source_ridge, "https://pubs.usgs.gov/gip/dynamic/developing.html"),
    RIFT(R.string.geo_source_rift, "https://www.nps.gov/subjects/geology/plate-tectonics-continental-rift.htm"),
    SUBDUCTION(R.string.geo_source_subduction, "https://www.nps.gov/subjects/geology/plate-tectonics-subduction-zones.htm"),
    COLLISION(R.string.geo_source_collision, "https://www.nps.gov/subjects/geology/plate-tectonics-collisional-mountain-ranges.htm"),
    TRANSFORM(R.string.geo_source_transform, "https://www.nps.gov/subjects/geology/plate-tectonics-transform-plate-boundaries.htm"),
    HOTSPOT(R.string.geo_source_hotspot, "https://www.nps.gov/subjects/geology/plate-tectonics-oceanic-hotspots.htm"),
    ALPS(R.string.geo_source_alps, "https://www.brgm.fr/fr/actualite/eclairage/roches-plus-vieilles-pensait-au-coeur-alpes"),
    ALPINE_COLLISION(R.string.geo_source_alpine_collision, "https://rgf.brgm.fr/sites/default/files/upload/resume_these_bh.pdf"),
    HIMALAYA(R.string.geo_source_himalaya, "https://pubs.usgs.gov/of/2010/1099/of2010-1099.pdf"),
    IGNEOUS(R.string.geo_source_igneous, "https://www.nps.gov/subjects/geology/igneous.htm"),
    SEDIMENT(R.string.geo_source_sediment, "https://www.nps.gov/subjects/geology/sedimentary.htm"),
    METAMORPHIC(R.string.geo_source_metamorphic, "https://www.nps.gov/subjects/geology/metamorphic.htm"),
    STRATA(R.string.geo_source_strata, "https://www.nps.gov/articles/geologic-principles-superposition-and-original-horizontality.htm"),
    DATING(R.string.geo_source_dating, "https://home.nps.gov/articles/000/grcatime-timescale.htm"),
    CROSSCUT(R.string.geo_source_crosscut, "https://www.nps.gov/articles/geologic-principles-cross-cutting-relationships.htm"),
    LANDSCAPE(R.string.geo_source_landscape, "https://www.nps.gov/subjects/erosion/erosion.htm"),
    GLACIER(R.string.geo_source_glacier, "https://www.nps.gov/subjects/geology/glacial-landforms.htm"),
    SCALE(R.string.geo_source_scale, "https://stratigraphy.org/chart/")
}

data class EarthTopic(
    val id: String, @param:StringRes val title: Int,
    @param:StringRes val facts: Int, @param:StringRes val origin: Int,
    @param:StringRes val detail: Int, val source: EarthSource,
    val periodId: String? = null, val lifeId: String? = null
)

/** Internal shells, rock ages and geological periods are deliberately separate concepts. */
object GeologyCatalog {
    val topics = listOf(
        EarthTopic("crust", R.string.geo_crust, R.string.geo_crust_facts, R.string.geo_crust_origin, R.string.geo_crust_detail, EarthSource.INTERIOR, "hadean"),
        EarthTopic("upper_mantle", R.string.geo_upper_mantle, R.string.geo_upper_mantle_facts, R.string.geo_upper_mantle_origin, R.string.geo_upper_mantle_detail, EarthSource.MODEL, "hadean"),
        EarthTopic("lower_mantle", R.string.geo_lower_mantle, R.string.geo_lower_mantle_facts, R.string.geo_lower_mantle_origin, R.string.geo_lower_mantle_detail, EarthSource.INTERIOR),
        EarthTopic("outer_core", R.string.geo_outer_core, R.string.geo_outer_core_facts, R.string.geo_outer_core_origin, R.string.geo_outer_core_detail, EarthSource.INTERIOR, "hadean"),
        EarthTopic("inner_core", R.string.geo_inner_core, R.string.geo_inner_core_facts, R.string.geo_inner_core_origin, R.string.geo_inner_core_detail, EarthSource.INTERIOR),
        EarthTopic("ocean_crust", R.string.geo_ocean_crust, R.string.geo_ocean_crust_facts, R.string.geo_ocean_crust_origin, R.string.geo_ocean_crust_detail, EarthSource.RIDGE),
        EarthTopic("continent_crust", R.string.geo_continent_crust, R.string.geo_continent_crust_facts, R.string.geo_continent_crust_origin, R.string.geo_continent_crust_detail, EarthSource.INTERIOR, "archean"),
        EarthTopic("moho", R.string.geo_moho, R.string.geo_moho_facts, R.string.geo_moho_origin, R.string.geo_moho_detail, EarthSource.INTERIOR),
        EarthTopic("lithosphere", R.string.geo_lithosphere, R.string.geo_lithosphere_facts, R.string.geo_lithosphere_origin, R.string.geo_lithosphere_detail, EarthSource.MODEL),
        EarthTopic("asthenosphere", R.string.geo_asthenosphere, R.string.geo_asthenosphere_facts, R.string.geo_asthenosphere_origin, R.string.geo_asthenosphere_detail, EarthSource.MODEL),
        EarthTopic("ridge", R.string.geo_ridge, R.string.geo_ridge_facts, R.string.geo_ridge_origin, R.string.geo_ridge_detail, EarthSource.RIDGE),
        EarthTopic("rift", R.string.geo_rift, R.string.geo_rift_facts, R.string.geo_rift_origin, R.string.geo_rift_detail, EarthSource.RIFT),
        EarthTopic("plate_motion", R.string.geo_plate_motion, R.string.geo_plate_motion_facts, R.string.geo_plate_motion_origin, R.string.geo_plate_motion_detail, EarthSource.MOTION),
        EarthTopic("ocean_age", R.string.geo_ocean_age, R.string.geo_ocean_age_facts, R.string.geo_ocean_age_origin, R.string.geo_ocean_age_detail, EarthSource.RIDGE),
        EarthTopic("subduction", R.string.geo_subduction, R.string.geo_subduction_facts, R.string.geo_subduction_origin, R.string.geo_subduction_detail, EarthSource.SUBDUCTION),
        EarthTopic("trench", R.string.geo_trench, R.string.geo_trench_facts, R.string.geo_trench_origin, R.string.geo_trench_detail, EarthSource.SUBDUCTION),
        EarthTopic("arc", R.string.geo_arc, R.string.geo_arc_facts, R.string.geo_arc_origin, R.string.geo_arc_detail, EarthSource.SUBDUCTION),
        EarthTopic("earthquake", R.string.geo_earthquake, R.string.geo_earthquake_facts, R.string.geo_earthquake_origin, R.string.geo_earthquake_detail, EarthSource.MOTION),
        EarthTopic("collision", R.string.geo_collision, R.string.geo_collision_facts, R.string.geo_collision_origin, R.string.geo_collision_detail, EarthSource.COLLISION),
        EarthTopic("fold", R.string.geo_fold, R.string.geo_fold_facts, R.string.geo_fold_origin, R.string.geo_fold_detail, EarthSource.COLLISION),
        EarthTopic("root", R.string.geo_root, R.string.geo_root_facts, R.string.geo_root_origin, R.string.geo_root_detail, EarthSource.COLLISION),
        EarthTopic("transform", R.string.geo_transform, R.string.geo_transform_facts, R.string.geo_transform_origin, R.string.geo_transform_detail, EarthSource.TRANSFORM),
        EarthTopic("hotspot", R.string.geo_hotspot, R.string.geo_hotspot_facts, R.string.geo_hotspot_origin, R.string.geo_hotspot_detail, EarthSource.HOTSPOT),
        EarthTopic("hawaii", R.string.geo_hawaii, R.string.geo_hawaii_facts, R.string.geo_hawaii_origin, R.string.geo_hawaii_detail, EarthSource.HOTSPOT),
        EarthTopic("plates_world", R.string.geo_plates_world, R.string.geo_plates_world_facts, R.string.geo_plates_world_origin, R.string.geo_plates_world_detail, EarthSource.MOTION),
        EarthTopic("thrust", R.string.geo_thrust, R.string.geo_thrust_facts, R.string.geo_thrust_origin, R.string.geo_thrust_detail, EarthSource.COLLISION),
        EarthTopic("alps", R.string.geo_alps, R.string.geo_alps_facts, R.string.geo_alps_origin, R.string.geo_alps_detail, EarthSource.ALPS, "paleogene"),
        EarthTopic("himalaya", R.string.geo_himalaya, R.string.geo_himalaya_facts, R.string.geo_himalaya_origin, R.string.geo_himalaya_detail, EarthSource.HIMALAYA, "paleogene"),
        EarthTopic("andes", R.string.geo_andes, R.string.geo_andes_facts, R.string.geo_andes_origin, R.string.geo_andes_detail, EarthSource.SUBDUCTION, "cenozoic"),
        EarthTopic("normal_fault", R.string.geo_normal_fault, R.string.geo_normal_fault_facts, R.string.geo_normal_fault_origin, R.string.geo_normal_fault_detail, EarthSource.RIFT),
        EarthTopic("erosion", R.string.geo_erosion, R.string.geo_erosion_facts, R.string.geo_erosion_origin, R.string.geo_erosion_detail, EarthSource.LANDSCAPE),
        EarthTopic("glacier", R.string.geo_glacier, R.string.geo_glacier_facts, R.string.geo_glacier_origin, R.string.geo_glacier_detail, EarthSource.GLACIER, "quaternary"),
        EarthTopic("isostasy", R.string.geo_isostasy, R.string.geo_isostasy_facts, R.string.geo_isostasy_origin, R.string.geo_isostasy_detail, EarthSource.COLLISION),
        EarthTopic("old_mountains", R.string.geo_old_mountains, R.string.geo_old_mountains_facts, R.string.geo_old_mountains_origin, R.string.geo_old_mountains_detail, EarthSource.COLLISION, "carboniferous"),
        EarthTopic("sediment", R.string.geo_sediment, R.string.geo_sediment_facts, R.string.geo_sediment_origin, R.string.geo_sediment_detail, EarthSource.SEDIMENT),
        EarthTopic("sandstone", R.string.geo_sandstone, R.string.geo_sandstone_facts, R.string.geo_sandstone_origin, R.string.geo_sandstone_detail, EarthSource.SEDIMENT),
        EarthTopic("limestone", R.string.geo_limestone, R.string.geo_limestone_facts, R.string.geo_limestone_origin, R.string.geo_limestone_detail, EarthSource.SEDIMENT, lifeId = "molluscs"),
        EarthTopic("shale", R.string.geo_shale, R.string.geo_shale_facts, R.string.geo_shale_origin, R.string.geo_shale_detail, EarthSource.SEDIMENT),
        EarthTopic("fossil", R.string.geo_fossil, R.string.geo_fossil_facts, R.string.geo_fossil_origin, R.string.geo_fossil_detail, EarthSource.DATING, "phanerozoic", "animals"),
        EarthTopic("igneous", R.string.geo_igneous, R.string.geo_igneous_facts, R.string.geo_igneous_origin, R.string.geo_igneous_detail, EarthSource.IGNEOUS),
        EarthTopic("granite", R.string.geo_granite, R.string.geo_granite_facts, R.string.geo_granite_origin, R.string.geo_granite_detail, EarthSource.IGNEOUS),
        EarthTopic("basalt", R.string.geo_basalt, R.string.geo_basalt_facts, R.string.geo_basalt_origin, R.string.geo_basalt_detail, EarthSource.IGNEOUS),
        EarthTopic("metamorphic", R.string.geo_metamorphic, R.string.geo_metamorphic_facts, R.string.geo_metamorphic_origin, R.string.geo_metamorphic_detail, EarthSource.METAMORPHIC),
        EarthTopic("rock_cycle", R.string.geo_rock_cycle, R.string.geo_rock_cycle_facts, R.string.geo_rock_cycle_origin, R.string.geo_rock_cycle_detail, EarthSource.METAMORPHIC),
        EarthTopic("superposition", R.string.geo_superposition, R.string.geo_superposition_facts, R.string.geo_superposition_origin, R.string.geo_superposition_detail, EarthSource.STRATA),
        EarthTopic("unconformity", R.string.geo_unconformity, R.string.geo_unconformity_facts, R.string.geo_unconformity_origin, R.string.geo_unconformity_detail, EarthSource.DATING),
        EarthTopic("dating", R.string.geo_dating, R.string.geo_dating_facts, R.string.geo_dating_origin, R.string.geo_dating_detail, EarthSource.DATING),
        EarthTopic("crosscut", R.string.geo_crosscut, R.string.geo_crosscut_facts, R.string.geo_crosscut_origin, R.string.geo_crosscut_detail, EarthSource.CROSSCUT)
    )
    private val byId = topics.associateBy { it.id }
    fun get(id: String) = byId[id]
    fun scene(id: String) = EarthScene.entries.firstOrNull { id in it.ids }
    val lifeByPeriod = mapOf("archean" to "luca", "proterozoic" to "eukaryotes", "cambrian" to "arthropods",
        "ordovician" to "land_plants", "devonian" to "tetrapods", "carboniferous" to "amniotes", "jurassic" to "mammals")
}
