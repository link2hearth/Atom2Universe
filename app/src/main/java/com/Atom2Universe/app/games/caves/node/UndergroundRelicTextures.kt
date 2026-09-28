package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/** Site relics share the existing atlas and furniture collision/meshing paths. */
internal object UndergroundRelicTextures {
    private val materials = linkedMapOf(
        "ossuary" to 0x696D66, "cocoon" to 0xC6C0A2,
        "furnace_duct" to 0x534A47, "grave_stele" to 0x667C6B,
        "instruments" to 0x796744, "golem_seal" to 0x435C80,
        "dwarf_tools" to 0x806043, "ogre_trophy" to 0x755640,
        "canopic_urn" to 0xBF9460, "root_stump" to 0x685640,
        "spectral_tablet" to 0x5F527D, "cistern_filter" to 0x456E66)

    fun register() {
        for (name in materials.keys) for (top in listOf(false, true)) {
            val face = if (top) "top" else "side"
            BlockRegistry.registerGeneratedTexture("cave_relics:${name}_$face") { size ->
                texture(name, top, size)
            }
        }
    }

    private fun shade(rgb: Int, value: Int): Int = Color.rgb(
        ((rgb shr 16 and 255) + value).coerceIn(0, 255),
        ((rgb shr 8 and 255) + value).coerceIn(0, 255),
        ((rgb and 255) + value).coerceIn(0, 255))

    private fun texture(name: String, top: Boolean, size: Int): Bitmap {
        val pixels = IntArray(1024)
        for (y in 0..31) for (x in 0..31) {
            val grain = (x * 37 xor y * 71) % 13 - 6
            val edge = minOf(x, y, 31 - x, 31 - y)
            val dx = abs(2 * x - 31)
            val dy = abs(2 * y - 31)
            val base = shade(materials.getValue(name), grain + if (edge == 1) 16 else 0)
            pixels[y * 32 + x] = when (name) {
                "ossuary" -> {
                    val sx = x % 16
                    val sy = y % 16
                    when {
                        sy in 4..11 && sx in 4..11 -> shade(
                            if (sy in 7..8 && (sx in 5..6 || sx in 9..10) || sy == 11 && sx % 2 == 0)
                                0x37392F else 0xDED3AE, grain)
                        sy == 0 || sx == 0 -> shade(0x393D36, grain)
                        else -> base
                    }
                }
                "cocoon" -> when {
                    (x + y / 2) % 7 <= 1 || (x - y / 3 + 32) % 9 == 0 -> shade(0xEBE8CF, grain)
                    !top && dx < 7 && y in 12..18 -> shade(0x696B4B, grain)
                    else -> shade(0xAAA889, grain)
                }
                "furnace_duct" -> when {
                    edge < 3 -> shade(0x888A82, grain - if (edge == 2) 36 else 0)
                    x % 6 <= 1 -> shade(0x303436, grain)
                    y in 7..26 -> shade(if ((x + y * 3) % 11 < 4) 0xFFC45C else 0xB54A28, grain)
                    else -> base
                }
                "grave_stele" -> when {
                    edge < 3 -> shade(0x394D43, grain)
                    dx < 4 && y in 6..23 || y in 11..14 && x in 8..23 -> shade(0xABB8A0, grain)
                    (x * 7 + y * 3) % 29 < 3 -> shade(0x486345, grain)
                    else -> base
                }
                "instruments" -> when {
                    edge < 2 || y == 23 -> shade(0xAC9360, grain)
                    x in 5..11 && y in 7..19 || x in 20..26 && y in 12..22 ->
                        shade(if (y < 12 || y in 12..15 && x > 19) 0xC1D5B0 else 0x63B6A6, grain)
                    x in 7..9 && y in 3..7 || x in 22..24 && y in 8..12 -> shade(0xD3BA88, grain)
                    x in 13..17 && y in 19..21 -> shade(0xC48EC7, grain)
                    else -> base
                }
                "golem_seal" -> when {
                    dx + dy in 20..24 || dx < 3 && dy < 11 -> shade(0x9AE3EA, grain)
                    edge == 3 || edge == 5 -> shade(0x7A94AC, grain)
                    else -> base
                }
                "dwarf_tools" -> when {
                    x in 5..8 && y in 7..27 || x in 23..25 && y in 12..27 -> shade(0xC39D63, grain)
                    x in 3..14 && y in 5..10 || y in 10..14 && x in 18..29 -> shade(0xB5BEC0, grain)
                    y % 8 == 0 -> shade(0x443A31, grain)
                    else -> base
                }
                "ogre_trophy" -> when {
                    dx in 15..21 && y in 4..19 -> shade(0xDCC9A1, grain)
                    dx < 13 && y in 13..26 -> shade(if (y in 17..19 && dx in 5..9) 0x35332C else 0xBFA881, grain)
                    edge < 3 -> shade(0x40372C, grain)
                    else -> base
                }
                "canopic_urn" -> when {
                    top && dx * dx + dy * dy < 250 -> shade(0x594C3F, grain)
                    y in 3..5 || y in 26..28 -> shade(0xD5B46D, grain)
                    y in 8..23 && (x % 8 == 2 || y % 7 == 2 && x % 8 in 2..5) -> shade(0x536F73, grain)
                    else -> base
                }
                "root_stump" -> when {
                    top && maxOf(dx, dy) % 8 < 2 -> shade(0xB49A68, grain)
                    (x + y / 5) % 8 <= 1 -> shade(0x343F2C, grain)
                    (x * 5 + y * 3) % 23 < 6 -> shade(0x688049, grain)
                    else -> base
                }
                "spectral_tablet" -> when {
                    edge < 3 -> shade(0x30314D, grain)
                    y in 6..25 && (y % 6 == 0 && x in 7..24 || x % 6 == 1 && y % 6 in 1..3) ->
                        shade(0xC5B5E7, grain)
                    else -> base
                }
                "cistern_filter" -> when {
                    edge < 3 -> shade(0x8AADA2, grain)
                    x % 6 <= 1 || y % 6 <= 1 -> shade(0x293E3A, grain)
                    (x + y * 3) % 17 < 5 -> shade(0xADC969, grain)
                    else -> shade(0x608E69, grain)
                }
                else -> base
            }
        }
        val source = Bitmap.createBitmap(pixels, 32, 32, Bitmap.Config.ARGB_8888)
        if (size == 32) return source
        return Bitmap.createScaledBitmap(source, size, size, false).also { source.recycle() }
    }
}
