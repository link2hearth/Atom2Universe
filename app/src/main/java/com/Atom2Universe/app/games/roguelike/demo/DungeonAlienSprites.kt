package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.roundToInt
import kotlin.math.sin

/** Trois anatomies sur une grille commune ; les huit poses sont mises en cache par la scène. */
internal object DungeonAlienSprites {
    fun create(style: DungeonDemoSprites.MonsterStyle, pose: Int = 0): Bitmap {
        val bitmap = Bitmap.createBitmap(40, 36, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = false }
        val outline = 0xFF182333.toInt()
        val dark = style.shadow.toInt()
        val body = style.fur.toInt()
        val light = style.light.toInt()
        val accent = style.skin.toInt()
        val eye = style.eye.toInt()
        val sway = sin(pose * Math.PI / 4).roundToInt()
        fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
            paint.color = color
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
        }
        fun plate(x: Int, y: Int, w: Int, h: Int, color: Int) {
            rect(x + 2, y, w - 4, h, outline)
            rect(x, y + 2, w, h - 4, outline)
            rect(x + 2, y + 2, w - 4, h - 4, color)
        }
        when (style) {
            DungeonDemoSprites.MonsterStyle.ALIEN_SCOUT -> {
                // Petit bipède : crâne en poire, yeux noirs obliques et combinaison spatiale.
                for (side in 0..1) {
                    val x = 13 + side * 10
                    val step = if (side == 0) sway else -sway
                    rect(x, 25, 5, 7 + step, outline)
                    rect(x + 1, 25, 3, 5 + step, body)
                    rect(x - 2, 31 + step, 7, 2, dark)
                    rect(8 + side * 20, 20 + step, 4, 8, outline)
                    rect(9 + side * 20, 20 + step, 2, 6, body)
                    rect(7 + side * 20, 27 + step, 5, 2, light)
                }
                plate(11, 17, 17, 11, accent)
                rect(17, 19, 5, 6, dark)
                rect(18, 20, 3, 2, eye)
                plate(8, 2 + sway, 23, 13, body)
                plate(12, 11 + sway, 15, 8, body)
                rect(12, 4 + sway, 13, 2, light)
                rect(10, 7 + sway, 7, 4, outline)
                rect(12, 10 + sway, 5, 2, outline)
                rect(22, 7 + sway, 7, 4, outline)
                rect(22, 10 + sway, 5, 2, outline)
                rect(12, 8 + sway, 2, 1, eye)
                rect(24, 8 + sway, 2, 1, eye)
                rect(17, 15 + sway, 5, 1, dark)
            }
            DungeonDemoSprites.MonsterStyle.ALIEN_CRAWLER -> {
                // Six pattes articulées, carapace segmentée et mandibules luminescentes.
                for (leg in 0..2) {
                    val x = 9 + leg * 9
                    val step = if ((pose + leg) % 8 < 4) 1 else -1
                    rect(x - 4, 15 + step, 3, 12, outline)
                    rect(x - 3, 16 + step, 1, 9, accent)
                    rect(x - 6, 26 + step, 5, 2, light)
                }
                plate(7, 11 + sway, 29, 15, dark)
                for (segment in 0..2) {
                    plate(9 + segment * 8, 9 + sway, 10, 14, body)
                    rect(11 + segment * 8, 11 + sway, 5, 2, light)
                    rect(14 + segment * 8, 15 + sway, 2, 5, dark)
                }
                for (leg in 0..2) {
                    val x = 11 + leg * 9
                    val step = if ((pose + leg) % 8 < 4) -1 else 1
                    rect(x, 23, 3, 6 + step, outline)
                    rect(x + 2, 28 + step, 3, 5, dark)
                    rect(x + 1, 32 + step, 6, 2, light)
                }
                plate(2, 16 + sway, 14, 12, body)
                rect(4, 18 + sway, 4, 3, eye)
                rect(10, 18 + sway, 3, 3, eye)
                rect(3, 24 + sway, 3, 5, outline)
                rect(10, 24 + sway, 3, 5, outline)
                rect(5, 28 + sway, 2, 2, light)
                rect(9, 28 + sway, 2, 2, light)
            }
            DungeonDemoSprites.MonsterStyle.ALIEN_FLOATER -> {
                // Cloche flottante et cinq tentacules dont les ondulations sont décalées.
                for (tentacle in 0..4) {
                    val x = 9 + tentacle * 5
                    for (part in 0..4) {
                        val wave = (sin((pose + tentacle + part) * Math.PI / 4) * 2).roundToInt()
                        val y = 18 + part * 3 + sway
                        rect(x + wave, y, 3, 4, dark)
                        rect(x + wave, y, 1, 3, if (part == 4) eye else accent)
                    }
                }
                plate(7, 4 + sway, 27, 17, body)
                rect(11, 3 + sway, 19, 4, outline)
                rect(12, 5 + sway, 17, 3, light)
                rect(9, 9 + sway, 3, 6, light)
                rect(10, 18 + sway, 21, 3, dark)
                plate(15, 10 + sway, 12, 9, accent)
                rect(18, 12 + sway, 6, 4, eye)
                rect(20, 12 + sway, 2, 4, outline)
                rect(29, 12 + sway, 2, 3, light)
            }
            else -> error("Not an alien style: $style")
        }
        return bitmap
    }
}
