package com.Atom2Universe.app.games.caves.node

import org.json.JSONObject

internal data class BlockDef(
    val id: Short,
    val name: String,
    val textureTop: String,
    val textureSide: String,
    val textureBottom: String,
    val textureSideGrass: String?,  // face latérale quand dessus est AIR (transition herbe)
    val textureSideSand: String?,   // face latérale quand dessus est SAND/REDSAND
    val textureSideSnow: String?,   // face latérale quand dessus est SNOW/ICE
    val color: Int,
    val hardness: Float,
    val decoration: Boolean,
    val transparent: Boolean,
    val water: Boolean,
    val falling: Boolean,
    val lightEmission: Int,
    val drop: String,
    val creativeTab: String,
    val spriteMargin: Float,
    val spriteHeight: Float,
    // indices assignés par BlockRegistry.buildTextureAtlas()
    var layerTop: Int = -1,
    var layerSide: Int = -1,
    var layerBottom: Int = -1,
    var layerSideGrass: Int = -1,
    var layerSideSand: Int = -1,
    var layerSideSnow: Int = -1,
) {
    companion object {
        fun fromJson(j: JSONObject): BlockDef {
            val colorStr = j.optString("color", "#444444").trimStart('#')
            val colorInt = (0xFF000000.toInt()) or colorStr.toLong(16).toInt()
            return BlockDef(
                id              = j.getInt("id").toShort(),
                name            = j.getString("name"),
                textureTop      = j.getString("texture_top"),
                textureSide     = j.getString("texture_side"),
                textureBottom   = j.getString("texture_bottom"),
                textureSideGrass = j.optString("texture_side_grass", "").ifEmpty { null },
                textureSideSand  = j.optString("texture_side_sand", "").ifEmpty { null },
                textureSideSnow  = j.optString("texture_side_snow", "").ifEmpty { null },
                color           = colorInt,
                hardness        = j.optDouble("hardness", 1.0).toFloat(),
                decoration      = j.optBoolean("decoration", false),
                transparent     = j.optBoolean("transparent", false),
                water           = j.optBoolean("water", false),
                falling         = j.optBoolean("falling", false),
                lightEmission   = j.optInt("light_emission", 0),
                drop            = j.optString("drop", ""),
                creativeTab     = j.optString("creative_tab", "terrain"),
                spriteMargin    = j.optDouble("sprite_margin", 0.10).toFloat(),
                spriteHeight    = j.optDouble("sprite_height", 0.90).toFloat(),
            )
        }
    }
}
