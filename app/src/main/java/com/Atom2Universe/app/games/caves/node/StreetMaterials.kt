package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

/** Reusable original street textures, generated at atlas resolution. */
internal object StreetMaterials {
    const val ASPHALT: Short = 2700
    const val CONCRETE: Short = 2701
    const val SIDEWALK: Short = 2702
    const val CURB: Short = 2703
    const val MANHOLE: Short = 2704
    const val DRAIN: Short = 2705
    const val LANE_NS: Short = 2706
    const val LANE_EW: Short = 2707
    const val CROSS_NS: Short = 2708
    const val CROSS_EW: Short = 2709
    const val STOP_NS: Short = 2710
    const val STOP_EW: Short = 2711
    const val SHINGLES: Short = 2712
    const val TILE: Short = 2713
    const val LIGHT: Short = 2714

    private val names = listOf("asphalt", "concrete", "sidewalk", "curb", "manhole", "drain",
        "lane_ns", "lane_ew", "cross_ns", "cross_ew", "stop_ns", "stop_ew", "shingles", "tile", "light")

    fun register() {
        for (name in names) BlockRegistry.registerGeneratedTexture("street:$name") { size -> texture(name, size) }
    }

    private fun texture(name: String, size: Int): Bitmap {
        val pixels = IntArray(size * size)
        for (py in 0 until size) for (px in 0 until size) {
            val x = px * 32 / size; val y = py * 32 / size
            val grain = ((x * 7349 xor y * 19391) ushr 2) and 31
            var color = when (name) {
                "concrete", "sidewalk", "curb" -> 0xBEBBB1
                "tile" -> 0xDEDAD0
                "shingles" -> 0x535761
                "light" -> 0xFFF0C0
                else -> 0x43474A
            }
            var shade = grain / 3 - 5
            when (name) {
                "concrete" -> if (grain == 0) shade -= 12
                "sidewalk" -> if (x == 0 || y == 0) shade = -32 else if (x == 1 || y == 1) shade = 8
                "curb" -> if (x in 1..3) shade = 18 else if (x == 0 || x == 31) shade = -30
                "manhole" -> {
                    val dx = x - 15.5f; val dy = y - 15.5f; val r = sqrt(dx*dx+dy*dy)
                    if (r <= 14) {
                        color = 0x62686B
                        shade = when {
                            r > 13 -> -42
                            r > 11.8f -> 27
                            r > 10.7f -> -30
                            (x+y)%6 == 0 || Math.floorMod(x-y,6) == 0 -> -18
                            else -> grain/3
                        }
                        if ((abs(dx)<2 && abs(dy) in 8f..10f) || (abs(dy)<2 && abs(dx) in 8f..10f)) shade = -42
                    }
                }
                "drain" -> {
                    if (x in 2..29 && y in 2..29) {
                        color=0x74797A
                        shade=if (x in 5..26 && y in 5..26 && x%5 in 1..3) -70 else 0
                    }
                }
                "lane_ns", "lane_ew" -> {
                    val across=if(name.endsWith("ns")) x else y
                    if(across in 7..11 || across in 20..24) { color=0xD9B958; shade=grain/5-3 }
                }
                "cross_ns", "cross_ew" -> {
                    val along=if(name.endsWith("ns")) y else x
                    if(along in 5..26) { color=0xE2DED0; shade=grain/4-4 }
                }
                "stop_ns", "stop_ew" -> {
                    val along=if(name.endsWith("ns")) y else x
                    if(along in 11..21) { color=0xE2DED0; shade=grain/4-4 }
                }
                "shingles" -> {
                    val row=y/8
                    if(y%8==0 || (x+row%2*8)%16==0) shade=-30
                    else if(y%8==1) shade=12
                }
                "tile" -> if(x%16==0 || y%16==0) shade=-28
                "light" -> {
                    if(x<3 || x>28 || y<3 || y>28) { color=0x777F80; shade=0 }
                    else shade=if(y%6==0) -12 else 0
                }
            }
            pixels[py*size+px]=Color.rgb(((color shr 16 and 255)+shade).coerceIn(0,255),
                ((color shr 8 and 255)+shade).coerceIn(0,255),((color and 255)+shade).coerceIn(0,255))
        }
        return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888)
    }
}
