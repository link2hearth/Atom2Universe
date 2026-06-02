package com.Atom2Universe.app.games.caves.world

import org.json.JSONArray
import org.json.JSONObject

internal fun blockIdByName(name: String): Short = when (name) {
    "air"         -> AIR
    "dirt"        -> DIRT
    "grass"       -> GRASS
    "gravel"      -> GRAVEL
    "gravel_dirt" -> GRAVEL_DIRT
    "wood"        -> WOOD
    "wood_white"  -> WOOD_WHITE
    "plank"       -> PLANK
    "leaves"      -> LEAVES
    "leaves_orange" -> LEAVES_ORANGE
    "leaves_fall" -> LEAVES_FALL
    "stone"       -> STONE
    "granite"     -> GRANITE
    "quartz"      -> QUARTZ
    "brick_red"   -> BRICK_RED
    "brick_grey"  -> BRICK_GREY
    "rock"        -> ROCK
    "rock_moss"   -> ROCK_MOSS
    "coal"        -> COAL
    "iron"        -> IRON
    "silver"      -> SILVER
    "gold"        -> GOLD
    "copper"      -> COPPER
    "ruby"        -> RUBY
    "emerald"     -> EMERALD
    "crystal"     -> CRYSTAL
    "redstone"    -> REDSTONE
    "sand"        -> SAND
    "redsand"     -> REDSAND
    "cactus"      -> CACTUS
    "ice"         -> ICE
    "snow"        -> SNOW
    "lava"        -> LAVA
    "water"       -> WATER
    "water_flow"  -> WATER_FLOW
    "grass_wild1" -> GRASS_WILD1
    "grass_wild2" -> GRASS_WILD2
    "grass_wild3" -> GRASS_WILD3
    "grass_wild4" -> GRASS_WILD4
    "grass_brown" -> GRASS_BROWN
    "grass_tan"   -> GRASS_TAN
    "mushroom_red"   -> MUSHROOM_RED
    "mushroom_brown" -> MUSHROOM_BROWN
    "mushroom_tan"   -> MUSHROOM_TAN
    "wheat1" -> WHEAT1; "wheat2" -> WHEAT2; "wheat3" -> WHEAT3; "wheat4" -> WHEAT4
    else -> AIR.also { android.util.Log.w("BiomeDef", "Unknown block name: $name") }
}

data class OreEntry(
    val block: Short,
    val repeatMin: Int,
    val repeatMax: Int,
    val minCount: Int,
    val maxCount: Int,
    val chance: Float = 1.0f,
)

data class OreDistanceBracket(
    val distMax: Float,
    val ores: List<OreEntry>,
)

data class BlockNoiseEntry(val block: Short, val noiseMin: Float)

data class VegetationEntry(val block: Short, val weight: Int)

/**
 * Bloc de surface posé au-dessus d'une altitude donnée (ex. neige sur les sommets).
 * [thickness] = nombre de blocs depuis la surface remplacés par ce bloc (1 = surface seule).
 */
data class HeightBlockEntry(val block: Short, val minHeight: Int, val thickness: Int = 1)

/** Structure pouvant apparaître dans un biome, référencée par son nom dans StructureRegistry. */
data class StructureEntry(val name: String, val weight: Int)

data class CaveBiomeDef(
    val id: String,
    val noiseOffsets: DoubleArray,
    val baseBlock: Short,
    val secondaryBlock: Short,
    val secondaryNoiseThreshold: Float,
    val roomChance: Float,
    val wormZeroChance: Float,
    val pocketsEnabled: Boolean,
    val pocketBlocks: List<BlockNoiseEntry>,
    val floorBlocks: List<BlockNoiseEntry>,
    val floorSkipBeyondDist: Float,
    val ores: List<OreEntry>,
    val oreByDistance: List<OreDistanceBracket>?,
    val decorationDensity: Float,
    val decorationBlocks: List<Short>,
    val treesEnabled: Boolean,
) {
    companion object {
        fun fromJson(j: JSONObject): CaveBiomeDef {
            fun parseBlock(name: String) = blockIdByName(name)
            fun parseOre(o: JSONObject) = OreEntry(
                block      = parseBlock(o.getString("block")),
                repeatMin  = o.optInt("repeat_min", 1),
                repeatMax  = o.optInt("repeat_max", 2),
                minCount   = o.optInt("min_count", 3),
                maxCount   = o.optInt("max_count", 6),
                chance     = o.optDouble("chance", 1.0).toFloat(),
            )
            fun parseOreArr(arr: JSONArray) = (0 until arr.length()).map { parseOre(arr.getJSONObject(it)) }
            fun parseBlockNoise(o: JSONObject) = BlockNoiseEntry(
                block    = parseBlock(o.getString("block")),
                noiseMin = o.optDouble("noise_min", -99.0).toFloat(),
            )

            val off = j.getJSONArray("noise_offsets")
            val distArr = j.optJSONArray("ore_by_distance")
            val decoArr = j.optJSONArray("decoration_blocks")

            return CaveBiomeDef(
                id                       = j.getString("id"),
                noiseOffsets             = doubleArrayOf(off.getDouble(0), off.getDouble(1), off.getDouble(2)),
                baseBlock                = parseBlock(j.getString("base_block")),
                secondaryBlock           = parseBlock(j.optString("secondary_block", j.getString("base_block"))),
                secondaryNoiseThreshold  = j.optDouble("secondary_noise_threshold", 99.0).toFloat(),
                roomChance               = j.optDouble("room_chance", 0.006).toFloat(),
                wormZeroChance           = j.optDouble("worm_zero_chance", 0.45).toFloat(),
                pocketsEnabled           = j.optBoolean("pockets_enabled", true),
                pocketBlocks             = j.optJSONArray("pocket_blocks")?.let { a -> (0 until a.length()).map { parseBlockNoise(a.getJSONObject(it)) } } ?: emptyList(),
                floorBlocks              = j.optJSONArray("floor_blocks")?.let { a -> (0 until a.length()).map { parseBlockNoise(a.getJSONObject(it)) } } ?: emptyList(),
                floorSkipBeyondDist      = j.optDouble("floor_skip_beyond_dist", 9999.0).toFloat(),
                ores                     = j.optJSONArray("ores")?.let { parseOreArr(it) } ?: emptyList(),
                oreByDistance            = distArr?.let { a -> (0 until a.length()).map { i ->
                    val b = a.getJSONObject(i)
                    OreDistanceBracket(b.getDouble("dist_max").toFloat(), parseOreArr(b.getJSONArray("ores")))
                } },
                decorationDensity        = j.optDouble("decoration_density", 0.0).toFloat(),
                decorationBlocks         = decoArr?.let { a -> (0 until a.length()).map { parseBlock(a.getString(it)) } } ?: emptyList(),
                treesEnabled             = j.optBoolean("trees_enabled", false),
            )
        }
    }
}

data class SurfaceBiomeDef(
    val id: String,
    val noiseOffsets: DoubleArray,
    val surfaceBlocks: List<BlockNoiseEntry>,
    val surfaceVarietyScale: Float,
    val surfaceVarietyOffset: Double,
    val dirtBlock: Short,
    val stoneBlock: Short,
    val heightBase: Double,
    val heightAmplitude: Double,
    val heightRoughness: Double,
    val heightBlocks: List<HeightBlockEntry>,
    val topsoilDepth: Int,
    val treeDensityBase: Float,
    val treeDensityNoiseScale: Float,
    val treeType: String,
    val treeMinHeight: Int,
    val treeMaxHeight: Int,
    val canopyRadius: Int,
    val bushDensity: Float,
    val bushBlock: Short?,
    val decorationDensity: Float,
    val decorationBlocks: List<Short>,
    val vegetationDensity: Float,
    val vegetationBlocks: List<VegetationEntry>,
    val wheatEnabled: Boolean,
    val structures: List<StructureEntry>,
) {
    companion object {
        fun fromJson(j: JSONObject): SurfaceBiomeDef {
            fun parseBlock(name: String) = blockIdByName(name)
            val off = j.getJSONArray("noise_offsets")
            val surfArr = j.getJSONArray("surface_blocks")
            val decoArr = j.optJSONArray("decoration_blocks")
            val vegArr  = j.optJSONArray("vegetation_blocks")
            val bushStr = j.optString("bush_block", "")
            return SurfaceBiomeDef(
                id                   = j.getString("id"),
                noiseOffsets         = doubleArrayOf(off.getDouble(0), off.getDouble(1), off.getDouble(2)),
                surfaceBlocks        = (0 until surfArr.length()).map { i ->
                    val o = surfArr.getJSONObject(i)
                    BlockNoiseEntry(parseBlock(o.getString("block")), o.optDouble("noise_min", -99.0).toFloat())
                },
                surfaceVarietyScale  = j.optDouble("surface_variety_scale", 0.04).toFloat(),
                surfaceVarietyOffset = j.optDouble("surface_variety_offset", 0.0),
                dirtBlock            = parseBlock(j.getString("dirt_block")),
                stoneBlock           = parseBlock(j.getString("stone_block")),
                heightBase           = j.optDouble("height_base", 0.0),
                heightAmplitude      = j.optDouble("height_amplitude", 16.0),
                heightRoughness      = j.optDouble("height_roughness", 4.0),
                heightBlocks         = j.optJSONArray("height_blocks")?.let { a ->
                    (0 until a.length()).map {
                        val o = a.getJSONObject(it)
                        HeightBlockEntry(parseBlock(o.getString("block")), o.optInt("min_height", 0), o.optInt("thickness", 1))
                    }.sortedByDescending { it.minHeight }
                } ?: emptyList(),
                topsoilDepth         = j.optInt("topsoil_depth", 3),
                treeDensityBase      = j.optDouble("tree_density_base", 0.0).toFloat(),
                treeDensityNoiseScale= j.optDouble("tree_density_noise_scale", 0.0).toFloat(),
                treeType             = j.optString("tree_type", "none"),
                treeMinHeight        = j.optInt("tree_min_height", 3),
                treeMaxHeight        = j.optInt("tree_max_height", 5),
                canopyRadius         = j.optInt("canopy_radius", 2),
                bushDensity          = j.optDouble("bush_density", 0.0).toFloat(),
                bushBlock            = if (bushStr.isNotEmpty()) parseBlock(bushStr) else null,
                decorationDensity    = j.optDouble("decoration_density", 0.01).toFloat(),
                decorationBlocks     = decoArr?.let { a -> (0 until a.length()).map { parseBlock(a.getString(it)) } } ?: emptyList(),
                vegetationDensity    = j.optDouble("vegetation_density", 0.0).toFloat(),
                vegetationBlocks     = vegArr?.let { a -> (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    VegetationEntry(parseBlock(o.getString("block")), o.optInt("weight", 1))
                } } ?: emptyList(),
                wheatEnabled         = j.optBoolean("wheat_enabled", false),
                structures           = j.optJSONArray("structures")?.let { a ->
                    (0 until a.length()).map {
                        val o = a.getJSONObject(it)
                        StructureEntry(o.getString("name"), o.optInt("weight", 1))
                    }
                } ?: emptyList(),
            )
        }
    }
}
