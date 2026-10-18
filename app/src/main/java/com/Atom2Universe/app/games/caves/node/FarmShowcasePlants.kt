package com.Atom2Universe.app.games.caves.node

import com.Atom2Universe.app.games.farm.FarmCrop
import com.Atom2Universe.app.games.farm.FarmPlantArt

/** Shared staged plant artwork for player crops and the fixed showcase samples. IDs stay stable. */
internal object FarmShowcasePlants {
    private const val FIRST_ID = 9400
    val crops = listOf(FarmCrop.WHEAT, FarmCrop.CORN, FarmCrop.TOMATO, FarmCrop.LETTUCE,
        FarmCrop.CARROT, FarmCrop.POTATO, FarmCrop.ONION, FarmCrop.STRAWBERRY,
        FarmCrop.RADISH, FarmCrop.PEPPER, FarmCrop.EGGPLANT,
        FarmCrop.BROCCOLI, FarmCrop.CAULIFLOWER, FarmCrop.PEAS,
        FarmCrop.CHILI, FarmCrop.LEEK,
        FarmCrop.RASPBERRY, FarmCrop.BLUEBERRY, FarmCrop.PINEAPPLE)
    val growth = listOf(.12f, .32f, .55f, .78f, 1f)
    private val tiered = setOf(FarmCrop.TOMATO, FarmCrop.CHILI, FarmCrop.PEPPER, FarmCrop.EGGPLANT)
    fun hasLeafTiers(crop: Int) = crops[crop] in tiered
    fun stemGrowth(stage: Int): Float {
        val t = ((growth[stage] - .1f) / .51f).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    fun crownHeight(crop: Int) = if (crops[crop] == FarmCrop.TOMATO) .53f else .42f
    fun id(crop: Int, stage: Int) = (FIRST_ID + crop * growth.size + stage).toShort()
    fun sample(id: Short): Pair<Int, Int>? {
        val index = id.toInt() - FIRST_ID
        return if (index in 0 until crops.size * growth.size) index / growth.size to index % growth.size else null
    }

    fun definitions(template: BlockDef): List<BlockDef> = crops.flatMapIndexed { index, crop ->
        growth.mapIndexed { stage, fraction ->
            val texture = "farm_showcase:${crop.name}:$stage"
            BlockRegistry.registerGeneratedTexture(texture) { size -> FarmPlantArt.worldSprite(crop, fraction, size) }
            val foliage = if (crop in tiered) "farm_showcase:foliage:${crop.name}" else texture
            if (crop in tiered) BlockRegistry.registerGeneratedTexture(foliage) { size ->
                FarmPlantArt.worldFoliage(crop, size)
            }
            template.copy(id = id(index, stage), name = "farm_showcase_${crop.name.lowercase()}_$stage",
                textureTop = texture, textureSide = foliage, textureBottom = texture,
                placeable = false, spriteMargin = 0f, spriteWidth = 2f,
                spriteHeight = if (crop in tiered || crop == FarmCrop.CORN) 3f else 2f,
                harvestCategory = "technical", placementRule = "farmland", drop = "")
        }
    }
}
