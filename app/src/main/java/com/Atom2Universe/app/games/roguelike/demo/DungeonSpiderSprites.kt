package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** Huit pattes à deux segments, abdomen séparé du céphalothorax et crochets mobiles. */
internal object DungeonSpiderSprites {
    fun create(style: DungeonDemoSprites.MonsterStyle, pose: Int = 0): Bitmap {
        val w = 44
        val h = 32
        val data = IntArray(w * h)
        val ink = 0xFF19212D.toInt()
        val dark = style.shadow.toInt()
        val body = style.fur.toInt()
        val light = style.light.toInt()
        val eye = style.eye.toInt()
        val boss = style == DungeonDemoSprites.MonsterStyle.SPIDER_BOSS
        val bob = if (pose in 2..4) 1 else 0
        fun pixel(x: Int, y: Int, color: Int) {
            if (x in 0 until w && y in 0 until h) data[y * w + x] = color
        }
        fun rect(x: Int, y: Int, width: Int, height: Int, color: Int) {
            for (py in y until y + height) for (px in x until x + width) pixel(px, py, color)
        }
        fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, thick: Int) {
            val steps = maxOf(abs(x1 - x0), abs(y1 - y0), 1)
            for (i in 0..steps) {
                val x = (x0 + (x1 - x0) * i.toFloat() / steps).roundToInt()
                val y = (y0 + (y1 - y0) * i.toFloat() / steps).roundToInt()
                rect(x, y, thick, thick, color)
            }
        }
        fun oval(cx: Int, cy: Int, rx: Int, ry: Int, color: Int) {
            for (y in -ry..ry) for (x in -rx..rx) {
                if (x * x * ry * ry + y * y * rx * rx <= rx * rx * ry * ry)
                    pixel(cx + x, cy + y, color)
            }
        }

        // Quatre paires distinctes ; les genoux s'écartent et les extrémités suivent le pas.
        for (side in listOf(-1, 1)) for (leg in 0..3) {
            val phase = pose * Math.PI / 4 + leg * Math.PI / 2 + if (side < 0) Math.PI else 0.0
            val stride = (sin(phase) * 2).roundToInt()
            val rootX = 21 + side * 5
            val rootY = 15 + leg * 2 + bob
            val kneeX = 21 + side * (13 + if (leg == 1 || leg == 2) 2 else 0)
            val kneeY = 4 + leg * 6 + stride
            val footX = (21 + side * (18 + if (leg % 2 == 0) 1 else 0) + stride).coerceIn(1, 40)
            val footY = 10 + leg * 6
            line(rootX, rootY, kneeX, kneeY, ink, 3)
            line(kneeX, kneeY, footX, footY, ink, 2)
            line(rootX, rootY, kneeX, kneeY, body, 1)
            line(kneeX, kneeY, footX, footY - 1, dark, 1)
            rect(kneeX, kneeY, 2, 2, light)
        }
        // Abdomen volumineux et marques dorsales.
        val rx = if (boss) 12 else 9
        val ry = if (boss) 10 else 8
        oval(21, 11 + bob, rx, ry, ink)
        oval(21, 11 + bob, rx - 1, ry - 1, dark)
        oval(20, 9 + bob, rx - 3, ry - 3, body)
        oval(18, 7 + bob, 3, 2, light)
        for (i in 0..2) {
            rect(20 - i, 8 + i * 3 + bob, 3 + i * 2, 1, style.skin.toInt())
        }
        if (boss) {
            // Motif rouge en sablier : la matriarche se distingue aussi par sa taille.
            line(18, 8 + bob, 24, 14 + bob, eye, 1)
            line(24, 8 + bob, 18, 14 + bob, eye, 1)
            rect(18, 8 + bob, 7, 1, eye)
            rect(18, 14 + bob, 7, 1, eye)
        }
        oval(21, 21 + bob, if (boss) 9 else 7, 6, ink)
        oval(21, 20 + bob, if (boss) 8 else 6, 4, body)
        rect(17, 18 + bob, 8, 1, light)
        // Deux grands yeux et quatre petits, au-dessus des deux chélicères.
        for (x in listOf(17, 22)) {
            rect(x, 20 + bob, 3, 3, ink)
            rect(x, 20 + bob, 2, 2, eye)
        }
        for (x in listOf(15, 18, 23, 26)) pixel(x, 18 + bob, eye)
        val spread = if (pose in 3..5) 1 else 0
        line(17, 24 + bob, 16 - spread, 28, ink, 2)
        line(25, 24 + bob, 26 + spread, 28, ink, 2)
        line(16 - spread, 28, 19 - spread, 29, light, 1)
        line(26 + spread, 28, 23 + spread, 29, light, 1)
        return Bitmap.createBitmap(data, w, h, Bitmap.Config.ARGB_8888)
    }
}
