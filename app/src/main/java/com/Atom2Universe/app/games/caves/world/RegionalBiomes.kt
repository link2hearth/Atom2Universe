package com.Atom2Universe.app.games.caves.world

/** Climate variants reuse existing blocks, trees and animal habitats. */
internal object RegionalBiomes {
    data class Variant(val id: String, val parent: String, val temperature: Double, val humidity: Double,
        val tree: String, val density: Float, val ground: Short = GRASS)

    val variants = listOf(
        Variant("flower_meadow", "plains", .54, .52, "none", 0f),
        Variant("orchard", "forest", .59, .59, "apple", .035f),
        Variant("autumn_woods", "birch_forest", .44, .57, "autumn", .055f, FOREST_FLOOR),
        Variant("cherry_grove", "magic_forest_pink", .62, .70, "pink", .045f),
        Variant("heathland", "plains", .40, .34, "none", 0f, GRASS),
        Variant("willow_marsh", "wetlands", .56, .90, "willow", .03f, MUD),
        Variant("mossy_woods", "dark_forest", .42, .81, "broad_oak", .05f, MOSS),
        Variant("steppe", "savanna", .60, .24, "none", 0f, GRASS),
        Variant("pine_barrens", "taiga", .32, .35, "sapin", .025f, FOREST_FLOOR),
        Variant("snowy_taiga", "taiga", .12, .70, "sapin", .055f, DIRT_SNOW),
        Variant("frost_plains", "tundra", .08, .44, "none", 0f, SNOW),
        Variant("alpine_meadow", "taiga", .25, .48, "sapin", .012f, GRASS),
        Variant("dry_scrub", "savanna", .76, .22, "acacia", .012f, GRASS),
        Variant("baobab_savanna", "savanna", .85, .38, "baobab", .024f),
        Variant("sandstone_badlands", "red_desert", .91, .15, "none", 0f, REDSAND),
        Variant("tropical_woodland", "jungle_edge", .79, .67, "jungle_small", .045f),
        Variant("rainforest", "jungle", .93, .94, "jungle", .075f, FOREST_FLOOR),
        Variant("tropical_marsh", "wetlands", .84, .86, "willow", .028f, MUD)
    )
    private val byId = variants.associateBy { it.id }
    fun parent(id: String): String = byId[id]?.parent ?: id
    fun variant(id: String): Variant? = byId[id]

    fun definitions(legacy: List<SurfaceBiomeDef>): List<SurfaceBiomeDef> = variants.mapIndexed { i, v ->
        legacy.first { it.id == v.parent }.copy(id = v.id,
            noiseOffsets = doubleArrayOf(71000.0 + i * 137, 0.0, 83000.0 + i * 251),
            temperature = v.temperature.toFloat(), humidity = v.humidity.toFloat(),
            treeType = v.tree, treeDensityBase = v.density,
            surfaceBlocks = listOf(BlockNoiseEntry(v.ground, -99f)))
    }

    fun profiles(base: List<NaturalBiomeProfile>): List<NaturalBiomeProfile> = base +
        variants.map { NaturalBiomeProfile(it.id, it.temperature, it.humidity, 0.0) }
}
