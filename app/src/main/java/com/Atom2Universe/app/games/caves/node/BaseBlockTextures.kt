package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap

/** Selected base-block art. Terrain variants preserve the approved palettes at 32 x 32 pixels. */
internal object BaseBlockTextures {
    private const val PREFIX = "base_art:"
    private val terrains = setOf("stone", "dirt", "sand", "grass_top", "grass_side", "cobblestone", "planks")
    private val singleTerrains = setOf("mud_wet", "mud_clay", "mud_swamp")
    private val props = setOf("chest", "cache").flatMap { kind ->
        listOf("top", "front", "side", "back", "bottom").map { "${kind}_$it" }
    }.toSet()

    /** Also validates the variant suffix; unknown names never become asset paths. */
    private fun key(name: String): String? {
        if (!name.startsWith(PREFIX)) return null
        val parts = name.removePrefix(PREFIX).split(':')
        if (parts.size == 1 && (parts[0] in props || parts[0] in singleTerrains)) return parts[0]
        if (parts.size != 2 || parts[0] !in terrains || parts[1].length != 1 || parts[1][0] !in '0'..'3') return null
        return "${parts[0]}_${parts[1]}"
    }

    fun supports(name: String): Boolean = key(name) != null

    /** Non-selected textures retain the usual single-variant behavior. */
    fun variantCount(name: String): Int =
        if (supports(name) && name.removePrefix(PREFIX).substringBefore(':') in terrains) 4 else 1

    fun texture(name: String, outputSize: Int, vivid: Boolean = false): Bitmap {
        requireNotNull(key(name)) { "Unknown base texture: $name" }
        require(outputSize > 0)
        val source = SelectedTextureDefaults.texture(name, 32)
        val colored = if (vivid) {
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            for (i in pixels.indices) if (pixels[i] ushr 24 != 0) pixels[i] = CavePalette.vivid(pixels[i])
            Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888).also { source.recycle() }
        } else source
        if (colored.width == outputSize && colored.height == outputSize) return colored
        return Bitmap.createScaledBitmap(colored, outputSize, outputSize, false).also { colored.recycle() }
    }
}
