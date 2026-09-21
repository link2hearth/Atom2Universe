package com.Atom2Universe.app.games.roguelike

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Small, deterministic pixel compositions. Coordinates are a 32-pixel tile. */
internal class DungeonMapArt {
    private val p = Paint().apply { isAntiAlias = false }
    private fun box(c: Canvas, x: Int, y: Int, w: Int, h: Int, color: Int) {
        p.color = color
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
    }
    private fun shade(color: Int, amount: Int): Int = android.graphics.Color.rgb(
        (android.graphics.Color.red(color) + amount).coerceIn(0, 255),
        (android.graphics.Color.green(color) + amount).coerceIn(0, 255),
        (android.graphics.Color.blue(color) + amount).coerceIn(0, 255))

    fun tile(c: Canvas, bounds: RectF, theme: DungeonTheme, tile: TileType, x: Int, y: Int) {
        c.save()
        c.translate(bounds.left, bounds.top)
        c.scale(bounds.width() / 32f, bounds.height() / 32f)
        val seed = (x * 73856093 xor y * 19349663) and Int.MAX_VALUE
        val ground = theme.ground
        val stone = theme.stone
        box(c, 0, 0, 32, 32, ground)
        if (theme.outdoor) {
            repeat(9) { i ->
                val px = (seed / (i + 1) + i * 11) % 30
                val py = (seed / (i + 3) + i * 7) % 30
                box(c, px, py, 2, 1, shade(ground, if (i % 2 == 0) 12 else -9))
            }
        } else {
            for (row in 0..3) {
                box(c, 0, row * 8, 32, 1, shade(ground, -15))
                box(c, (if (row % 2 == 0) 8 else 20), row * 8, 1, 8, shade(ground, -15))
            }
            box(c, 2, 2, 10, 1, shade(ground, 12))
        }
        if (tile == TileType.WALL) {
            // Dark footing makes every blocking cell distinct from a walkable path.
            box(c, 1, 5, 30, 26, shade(ground, -22))
            when (theme) {
                DungeonTheme.CEMETERY -> when (seed % 5) {
                    0 -> tree(c, false)
                    1 -> {
                        box(c, 3, 10, 26, 18, stone)
                        box(c, 1, 8, 30, 4, shade(stone, 18))
                        box(c, 6, 5, 20, 3, shade(stone, 8))
                        box(c, 10, 15, 12, 13, 0xFF202B30.toInt())
                        box(c, 5, 14, 3, 14, shade(stone, 22))
                        box(c, 24, 14, 3, 14, shade(stone, -15))
                    }
                    else -> {
                        box(c, 7, 25, 20, 4, shade(stone, -25))
                        box(c, 10, 10, 13, 17, stone)
                        box(c, 12, 7, 9, 3, shade(stone, 12))
                        box(c, 11, 11, 2, 14, shade(stone, 18))
                        box(c, 16, 12, 2, 9, shade(stone, -28))
                        box(c, 13, 15, 8, 2, shade(stone, -28))
                        box(c, 6, 28, 7, 2, 0xFF496354.toInt())
                    }
                }
                DungeonTheme.FOREST -> tree(c, true)
                DungeonTheme.FIELDS -> {
                    for (i in 0..4) {
                        val xx = 3 + i * 6
                        val yy = 6 + (seed / (i + 1)) % 6
                        box(c, xx, yy, 2, 24 - yy, stone)
                        box(c, xx - 2, yy + 2, 6, 5, shade(stone, 27))
                        box(c, xx + 2, yy + 10, 2, 6, shade(stone, -10))
                    }
                }
                DungeonTheme.LIBRARY -> {
                    box(c, 2, 4, 28, 25, shade(stone, -25))
                    for (row in 0..1) {
                        for (i in 0..5) box(c, 5 + i * 4, 7 + row * 11, 3, 8,
                            intArrayOf(0xFF8D5353.toInt(), 0xFF668173.toInt(), 0xFFB09658.toInt())[(i + seed % 3) % 3])
                        box(c, 3, 15 + row * 11, 26, 2, stone)
                    }
                }
                DungeonTheme.PIRATE, DungeonTheme.INN -> {
                    box(c, 5, 7, 23, 21, stone)
                    box(c, 7, 9, 19, 17, shade(stone, -22))
                    box(c, 5, 12, 23, 3, 0xFF434E55.toInt())
                    box(c, 5, 23, 23, 3, 0xFF434E55.toInt())
                    box(c, 11, 8, 2, 19, shade(stone, 12))
                    box(c, 22, 8, 2, 19, shade(stone, 12))
                }
                DungeonTheme.MINE -> {
                    box(c, 4, 10, 25, 18, stone)
                    box(c, 9, 5, 15, 22, shade(stone, 15))
                    box(c, 6, 19, 8, 9, shade(stone, -20))
                    box(c, 18, 11, 3, 6, 0xFF70BBBD.toInt())
                    box(c, 22, 15, 3, 4, 0xFF98D5D4.toInt())
                }
                DungeonTheme.SPACESHIP -> {
                    box(c, 1, 3, 30, 26, stone)
                    box(c, 4, 6, 24, 19, shade(stone, -28))
                    box(c, 6, 8, 2, 14, 0xFF6ADDDC.toInt())
                    box(c, 12, 10, 12, 2, shade(stone, 25))
                    box(c, 12, 15, 12, 2, shade(stone, 25))
                    box(c, 12, 20, 12, 2, shade(stone, 25))
                }
                else -> {
                    for (row in 0..2) {
                        box(c, 1, 3 + row * 8, 30, 7, shade(stone, -row * 9))
                        box(c, if (row % 2 == 0) 11 else 22, 3 + row * 8, 1, 7, shade(stone, -35))
                    }
                    box(c, 1, 2, 30, 2, shade(stone, 20))
                    if (theme == DungeonTheme.BATTLEFIELD) {
                        box(c, 17, 4, 2, 22, 0xFFC0B9A7.toInt())
                        box(c, 12, 20, 12, 3, 0xFF564333.toInt())
                    }
                }
            }
        }
        if (tile == TileType.STAIRS_DOWN) {
            box(c, 4, 4, 24, 25, 0xFF18212C.toInt())
            for (i in 0..4) box(c, 6 + i * 2, 7 + i * 4, 20 - i * 4, 3, shade(stone, 28 - i * 13))
            box(c, 2, 3, 3, 26, 0xFFC5AB69.toInt())
            box(c, 27, 3, 3, 26, 0xFFC5AB69.toInt())
        }
        c.restore()
    }

    private fun tree(c: Canvas, leafy: Boolean) {
        box(c, 14, 10, 5, 19, 0xFF705749.toInt())
        box(c, 8, 13, 8, 3, 0xFF705749.toInt())
        box(c, 7, 7, 3, 9, 0xFF705749.toInt())
        box(c, 18, 9, 8, 3, 0xFF705749.toInt())
        box(c, 24, 4, 3, 8, 0xFF705749.toInt())
        box(c, 11, 28, 12, 2, 0xFF493D39.toInt())
        if (leafy) {
            box(c, 3, 6, 25, 13, 0xFF294735.toInt())
            box(c, 7, 2, 17, 14, 0xFF3C6648.toInt())
            box(c, 8, 3, 10, 4, 0xFF547D53.toInt())
        }
    }

    fun pickup(c: Canvas, bounds: RectF, gold: Boolean, variant: Int = 0) {
        c.save()
        c.translate(bounds.left, bounds.top)
        c.scale(bounds.width() / 32f, bounds.height() / 32f)
        if (gold) {
            for (i in 0..2) {
                box(c, 6 + i * 6, 20 - i * 4, 9, 5, 0xFFAC732A.toInt())
                box(c, 6 + i * 6, 18 - i * 4, 9, 3, 0xFFFFD276.toInt())
            }
        } else {
            val hue = android.graphics.Color.HSVToColor(floatArrayOf((variant * 47f) % 360f, .45f, .95f))
            box(c, 6, 26, 20, 3, 0xFF788796.toInt())
            box(c, 11, 5, 10, 20, hue)
            box(c, 7, 10, 18, 10, hue)
            box(c, 12, 8, 3, 9, 0xFFECFFFF.toInt())
            box(c, 23, 3, 2, 4, 0xFFD9F8FA.toInt())
        }
        c.restore()
    }
}
