package com.Atom2Universe.app.games.caves.node

import com.Atom2Universe.app.games.farm.FarmCrop

/** Stable species order is shared with the existing world plant IDs (9400..9494). */
internal object FarmItems {
    val crops get() = FarmShowcasePlants.crops
    fun seed(crop: Int): Short = (9600 + crop).toShort()
    fun produce(crop: Int): Short = (9700 + crop).toShort()
    fun seedCrop(id: Short) = (id.toInt() - 9600).takeIf { it in crops.indices }
    fun produceCrop(id: Short) = (id.toInt() - 9700).takeIf { it in crops.indices }
    fun isItem(id: Short) = id == FarmSoil.HOE || FrontierItems.isGardenItem(id) || ExpeditionItems.isGardenItem(id) || seedCrop(id) != null || produceCrop(id) != null
    fun durationMs(crop: Int): Long = when (crops[crop]) {
        FarmCrop.RADISH, FarmCrop.LETTUCE -> 240_000L
        FarmCrop.CORN, FarmCrop.PINEAPPLE -> 720_000L
        else -> 480_000L
    }
    fun regrows(crop: Int) = crops[crop] in setOf(FarmCrop.TOMATO, FarmCrop.STRAWBERRY,
        FarmCrop.CHILI, FarmCrop.PEPPER, FarmCrop.EGGPLANT, FarmCrop.RASPBERRY, FarmCrop.BLUEBERRY)
    fun definitions(template: BlockDef): List<BlockDef> = crops.flatMapIndexed { index, crop ->
        listOf(true, false).map { seeds ->
            val texture = "farm_item:${crop.name}:$seeds"
            BlockRegistry.registerGeneratedTexture(texture) { size -> FarmItemArt.texture(crop, seeds, size) }
            template.copy(id = if (seeds) seed(index) else produce(index),
                name = "farm_${if (seeds) "seed" else "produce"}_${crop.name.lowercase()}",
                textureTop = texture, textureSide = texture, textureBottom = texture,
                decoration = false, placeable = false, drop = "", harvestCategory = "technical",
                creativeTab = "nature", placementRule = "any", tags = setOf("gardening"))
        }
    }
}
