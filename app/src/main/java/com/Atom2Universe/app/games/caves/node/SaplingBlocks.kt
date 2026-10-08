package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import com.Atom2Universe.app.games.caves.world.TreeSpecies
import kotlin.math.abs

/** Original 32-pixel plant sprites, built locally just like the meadow textures. */
internal object SaplingBlocks {
    fun definitions(template: BlockDef): List<BlockDef> = TreeSpecies.types.mapIndexed { species, type ->
        val texture = "sapling:$type"
        BlockRegistry.registerGeneratedTexture(texture) { size ->
            val source = Bitmap.createBitmap(pixels(species), 32, 32, Bitmap.Config.ARGB_8888)
            if (size == 32) source else Bitmap.createScaledBitmap(source, size, size, false).also { source.recycle() }
        }
        template.copy(id = TreeSpecies.sapling(species), name = "sapling_$type",
            textureTop = texture, textureSide = texture, textureBottom = texture,
            decoration = true, transparent = true, placeable = true, replaceable = false,
            spriteHeight = .85f, spriteWidth = 1f, spriteMargin = .08f,
            harvestCategory = "recoverable", drop = "sapling_$type", placementRule = "soil",
            tags = setOf("sapling"), creativeTab = "nature")
    }

    fun pixels(species: Int): IntArray {
        val pixels = IntArray(32 * 32)
        val palette = intArrayOf(0x648B43, 0x8FA955, 0x416D51, 0x405D44, 0x458958,
            0x4E7751, 0x719847, 0x829C4D, 0xD991B0, 0x9872B1, 0x629CAE, 0xCCB759,
            0x39664A, 0x537C65, 0x749347, 0x97A05B, 0xA2A666, 0x759970, 0x66A365, 0xC78C4B)
        fun ink(x: Int, y: Int, rgb: Int) { if (x in 0..31 && y in 0..31) pixels[y * 32 + x] = rgb or 0xFF000000.toInt() }
        val stem = when (species) { 1 -> 0xC9C4AA; 5, 12 -> 0x965B40; else -> 0x795C42 }
        for (y in 10..29) { ink(15, y, stem); ink(16, y, 0xA08359) }
        for (x in 12..19) ink(x, 29 - abs(x - 16) / 3, stem)
        val conifer = species in listOf(2, 5, 12, 13)
        val color = palette[species]
        fun leaf(cx: Int, cy: Int, rx: Int, ry: Int) {
            for (dy in -ry..ry) for (dx in -rx..rx) {
                if (dx * dx * ry * ry + dy * dy * rx * rx > rx * rx * ry * ry) continue
                val shade = if ((dx + dy + species) % 4 == 0) 0x101008 else 0
                ink(cx + dx, cy + dy, color + shade)
            }
        }
        if (conifer) {
            val lift = if (species >= 12) 2 else 0
            for (row in 0..3) {
                val y = 6 + row * 5 - lift
                for (dy in 0..3) for (dx in -(row + dy)..(row + dy)) ink(15 + dx, y + dy, color)
                for (dx in -row..row) ink(15 + dx, y + 1, color + 0x182016)
            }
        } else {
            val spread = if (species in listOf(14, 15, 16)) 2 else 0
            val lean = species % 3 - 1
            for (i in 0..5) {
                ink(15 - i, 22 - i, stem); ink(16 + i, 18 - i, stem)
            }
            leaf(10 - spread, 16 + lean, 4 + spread, 3)
            leaf(22 + spread, 11 - lean, 4, 3 + species % 2)
            leaf(15 + lean, 7, 3, 4)
            if (species == 17) for (y in 17..24) { ink(7, y, color); ink(24, y - 5, color) }
            if (species in 6..8) { ink(10, 16, if (species == 7) 0xD2B954 else 0xD87382); ink(22, 12, 0xE6C3BC) }
        }
        return pixels
    }
}
