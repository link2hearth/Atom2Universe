package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Bitmap
import com.Atom2Universe.app.games.roguelike.MonsterType
import kotlin.math.abs
import kotlin.math.roundToInt

/** Une grille commune, des anatomies propres ; agrandissement du chef sans filtrage. */
internal object DungeonCreatureSprites {
    fun create(style: DungeonDemoSprites.MonsterStyle, pose: Int = 0,
        clothes: DungeonZombieSprites.Clothes? = null): Bitmap {
        val pixels = CreaturePixels(style, pose, clothes)
        when (style.creature) {
            MonsterType.FELINE, MonsterType.WOLF, MonsterType.BEAR -> DungeonBeastSprites.draw(pixels)
            MonsterType.GOBLIN, MonsterType.TROLL, MonsterType.DEMON -> DungeonHumanoidCreatureSprites.draw(pixels)
            MonsterType.SCORPION, MonsterType.CARNIVOROUS_PLANT, MonsterType.SNAKE -> DungeonWildCreatureSprites.draw(pixels)
            else -> error("Unsupported creature: ${style.creature}")
        }
        return pixels.bitmap()
    }
}

/** Primitives sans anticrénelage. Les palettes de vêtements restent identiques dans les huit poses. */
internal class CreaturePixels(val style: DungeonDemoSprites.MonsterStyle, val pose: Int,
    clothes: DungeonZombieSprites.Clothes?) {
    private val width = 40
    private val height = 36
    private val pixels = IntArray(width * height)
    val boss = style.creatureBoss
    val type = requireNotNull(style.creature)
    val ink = 0xFF182130.toInt()
    val dark = style.shadow.toInt()
    val body = style.fur.toInt()
    val light = style.light.toInt()
    val accent = style.skin.toInt()
    val eye = style.eye.toInt()
    val ivory = 0xFFF0DFC0.toInt()
    val metal = 0xFFACC5D0.toInt()
    val wood = 0xFF795641.toInt()
    val shirt = clothes?.shirt ?: 0xFF795F8A.toInt()
    val trousers = clothes?.trousers ?: 0xFF465266.toInt()
    val step = intArrayOf(0, 1, 2, 1, 0, -1, -2, -1)[pose]
    // Les serpents conservent leur ondulation ; les autres corps restent ancrés.
    val bob = if (type == MonsterType.SNAKE && pose in 2..4) 1 else 0

    fun dot(x: Int, y: Int, color: Int) {
        if (x in 0 until width && y in 0 until height) pixels[y * width + x] = color
    }
    fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        for (py in y until y + h) for (px in x until x + w) dot(px, py, color)
    }
    fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, thick: Int = 1) {
        val count = maxOf(abs(x1 - x0), abs(y1 - y0), 1)
        for (i in 0..count) rect((x0 + (x1 - x0) * i.toFloat() / count).roundToInt(),
            (y0 + (y1 - y0) * i.toFloat() / count).roundToInt(), thick, thick, color)
    }
    fun limb(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, thick: Int = 3) {
        line(x0, y0, x1, y1, ink, thick)
        line(x0 + 1, y0, x1 + 1, y1, color, (thick - 2).coerceAtLeast(1))
    }
    fun oval(cx: Int, cy: Int, rx: Int, ry: Int, color: Int) {
        for (y in -ry..ry) for (x in -rx..rx)
            if (x * x * ry * ry + y * y * rx * rx <= rx * rx * ry * ry) dot(cx + x, cy + y, color)
    }
    fun shell(cx: Int, cy: Int, rx: Int, ry: Int, color: Int = body) {
        oval(cx, cy, rx, ry, ink)
        oval(cx, cy, rx - 1, ry - 1, color)
    }
    fun plate(x: Int, y: Int, w: Int, h: Int, color: Int) {
        rect(x + 1, y, w - 2, h, ink)
        rect(x, y + 1, w, h - 2, ink)
        rect(x + 1, y + 1, w - 2, h - 2, color)
    }
    fun poly(color: Int, vararg xy: Int) {
        val count = xy.size / 2
        for (y in 0 until height) for (x in 0 until width) {
            var inside = false
            var previous = count - 1
            for (i in 0 until count) {
                val xi = xy[i * 2]; val yi = xy[i * 2 + 1]
                val xj = xy[previous * 2]; val yj = xy[previous * 2 + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi).toFloat() * (y - yi) / (yj - yi) + xi)
                    inside = !inside
                previous = i
            }
            if (inside) dot(x, y, color)
        }
    }
    fun bitmap(): Bitmap {
        val outWidth = 48
        val outHeight = 44
        val output = IntArray(outWidth * outHeight)
        val scale = if (boss) 1.2f else 1f
        val left = (outWidth - width * scale) / 2f
        val top = outHeight - height * scale
        for (y in 0 until outHeight) for (x in 0 until outWidth) {
            val fx = (x - left) / scale
            val fy = (y - top) / scale
            if (fx >= 0 && fy >= 0 && fx < width && fy < height)
                output[y * outWidth + x] = pixels[fy.toInt() * width + fx.toInt()]
        }
        return Bitmap.createBitmap(output, outWidth, outHeight, Bitmap.Config.ARGB_8888)
    }
}
