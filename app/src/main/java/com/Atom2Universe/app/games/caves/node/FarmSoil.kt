package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

internal object FarmSoil {
    const val FARMLAND: Short = 9300
    const val HOE: Short = 9301

    fun registerTextures() {
        for (part in listOf("top", "side", "hoe")) {
            BlockRegistry.registerGeneratedTexture("farm_soil:$part") { size -> texture(part, size) }
        }
    }

    private fun texture(part: String, size: Int): Bitmap {
        val native = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        if (part == "hoe") {
            val canvas = Canvas(native)
            val paint = Paint().apply { isAntiAlias = false }
            fun rect(x: Float, y: Float, w: Float, h: Float, color: Int) {
                paint.color = color; canvas.drawRect(x, y, x+w, y+h, paint)
            }
            canvas.rotate(28f, 16f, 16f)
            rect(15f, 6f, 3f, 23f, 0xFF735035.toInt())
            rect(15f, 7f, 1f, 21f, 0xFFBD9664.toInt())
            rect(7f, 4f, 13f, 4f, 0xFF485961.toInt())
            rect(7f, 7f, 5f, 5f, 0xFF71888B.toInt())
            rect(7f, 11f, 5f, 1f, 0xFFB6C6BD.toInt())
        } else {
            val pixels = IntArray(1024) { i ->
                val x = i % 32; val y = i / 32
                val groove = if (part == "top") y % 8 else (x + 2) % 8
                val ridge = if (part == "top") intArrayOf(-19,-25,-14,4,13,8,1,-8)[groove]
                    else if (y < 5) intArrayOf(-13,-17,-8,3,6,2,0,-6)[groove] else 0
                val grain = ((x * 13 + y * 23 + x * y * 3) % 11) - 5
                val clod = if ((x / 3 + y / 2 * 7) % 13 == 0) 7 else 0
                val shade = ridge + grain + clod
                val r = (83 + shade).coerceIn(0,255)
                val g = (57 + shade * 3 / 4).coerceIn(0,255)
                val b = (37 + shade / 2).coerceIn(0,255)
                0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
            }
            native.setPixels(pixels,0,32,0,0,32,32)
        }
        if (size == 32) return native
        return Bitmap.createScaledBitmap(native,size,size,false).also { native.recycle() }
    }
}
