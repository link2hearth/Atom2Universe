package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Canvas
import android.graphics.Paint

/** Mobilier au fond, parquet libre pour les combattants. Décor fixe mis en cache. */
internal object DungeonInnBackdrop {
    private val paint = Paint().apply { isAntiAlias = false }
    private fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Long) {
        paint.color = color.toInt()
        c.drawRect(x, y, x + w, y + h, paint)
    }

    fun draw(c: Canvas, floor: Float, height: Float) {
        c.drawColor(0xFF483D3D.toInt())
        for (row in 0..(floor / 9).toInt()) for (col in 0..15) {
            rect(c, col * 17f, row * 9f, 16f, 8f,
                if ((row + col) % 3 == 0) 0xFF756252 else 0xFF6A574D)
        }
        for (x in intArrayOf(0, 76, 158, 234)) {
            rect(c, x.toFloat(), 0f, 6f, floor, 0xFF352E32)
            rect(c, x + 1f, 0f, 2f, floor, 0xFF966E4C)
        }
        for (y in floatArrayOf(3f, 27f, floor - 5)) {
            rect(c, 0f, y, 240f, 5f, 0xFF352B2C)
            rect(c, 0f, y, 240f, 1f, 0xFFAF8659)
        }
        // Fenêtre bleue et rideaux, en contraste avec le foyer.
        val wy = floor - 47
        rect(c, 17f, wy, 42f, 37f, 0xFF302C35)
        rect(c, 21f, wy + 3, 34f, 29f, 0xFF344D64)
        rect(c, 24f, wy + 5, 10f, 9f, 0xFF638996)
        rect(c, 43f, wy + 6, 5f, 5f, 0xFFE3D8AE)
        rect(c, 37f, wy + 2, 3f, 31f, 0xFFB39165)
        rect(c, 20f, wy + 17, 36f, 2f, 0xFFB39165)
        rect(c, 14f, wy - 2, 48f, 3f, 0xFFBC9060)
        for (x in intArrayOf(17, 52)) {
            rect(c, x.toFloat(), wy + 1, 7f, 29f, 0xFF824956)
            rect(c, x + 2f, wy + 1, 2f, 28f, 0xFFB77572)
            rect(c, x.toFloat(), wy + 20, 7f, 2f, 0xFFD3AE70)
        }
        rect(c, 15f, floor - 12, 47f, 4f, 0xFFBD9163)
        // Cheminée de pierre avec hotte, tablette et bûches.
        val fy = floor - 43
        rect(c, 99f, 0f, 42f, floor, 0xFF4E4847)
        for (y in 0..(floor / 8).toInt()) {
            rect(c, 101f, y * 8f, 38f, 7f, if (y % 2 == 0) 0xFF7D7364 else 0xFF71675E)
        }
        rect(c, 92f, fy, 56f, 5f, 0xFF332E32)
        rect(c, 91f, fy - 2, 58f, 3f, 0xFFB39B78)
        rect(c, 103f, fy + 8, 34f, 33f, 0xFF211E2A)
        rect(c, 100f, fy + 7, 4f, 34f, 0xFF9B8870)
        rect(c, 136f, fy + 7, 4f, 34f, 0xFF9B8870)
        rect(c, 96f, floor - 3, 48f, 5f, 0xFFAA9575)
        rect(c, 107f, floor - 9, 25f, 4f, 0xFF66402F)
        rect(c, 110f, floor - 10, 21f, 1f, 0xFFC47846)
        // Étagères, bouteilles et vaisselle derrière le comptoir.
        for (y in floatArrayOf(floor - 37, floor - 21)) {
            rect(c, 173f, y, 50f, 3f, 0xFF362C2C)
            rect(c, 172f, y - 1, 52f, 2f, 0xFFB0875D)
            for (i in 0..4) {
                val x = 177f + i * 9
                rect(c, x + 1, y - 10, 2f, 4f, 0xFFC4AD78)
                rect(c, x, y - 7, 4f, 6f, if (i % 2 == 0) 0xFF638570 else 0xFFA16A68)
                rect(c, x + 1, y - 6, 1f, 4f, 0xFFB6C09C)
            }
        }
        // Parquet à joints décalés ; variations fixes, sans bruit recalculé par image.
        rect(c, 0f, floor + 2, 240f, height, 0xFF493735)
        for (row in 0..((height - floor) / 12).toInt()) for (col in -1..5) {
            val x = col * 49f + if (row % 2 == 0) 0f else 24f
            val y = floor + 4 + row * 12
            rect(c, x, y, 47f, 10f, if ((row + col) % 3 == 0) 0xFF7B5C49 else 0xFF705341)
            rect(c, x + 1, y, 45f, 1f, 0xFF927158)
            rect(c, x + 8, y + 6, 24f, 1f, 0xFF644B40)
            rect(c, x + 3, y + 2, 1f, 1f, 0xFF3F3232)
            rect(c, x + 43, y + 7, 1f, 1f, 0xFF3F3232)
        }
        // Comptoir et petite table restent dans la bande du fond.
        rect(c, 168f, floor - 8, 60f, 19f, 0xFF493334)
        for (x in 170..224 step 9) rect(c, x.toFloat(), floor - 5, 7f, 14f, 0xFF8C624A)
        rect(c, 165f, floor - 12, 66f, 5f, 0xFFC19462)
        rect(c, 170f, floor - 12, 57f, 1f, 0xFFE1B783)
        rect(c, 181f, floor - 18, 5f, 6f, 0xFFD3BB94)
        rect(c, 186f, floor - 17, 2f, 4f, 0xFFD3BB94)
        rect(c, 18f, floor + 4, 35f, 4f, 0xFF312A2F)
        rect(c, 21f, floor + 8, 4f, 8f, 0xFF453136)
        rect(c, 46f, floor + 8, 4f, 8f, 0xFF453136)
        rect(c, 16f, floor, 39f, 5f, 0xFFA77952)
        rect(c, 18f, floor, 35f, 1f, 0xFFD5AC78)
        rect(c, 32f, floor - 4, 10f, 3f, 0xFFD5CAA9)
    }

    fun atmosphere(c: Canvas, floor: Float, clock: Float) {
        val tick = (clock / 160).toInt()
        for (i in 0..3) {
            val x = 108f + i * 6
            val h = 7f + (tick + i * 3) % 7
            rect(c, x, floor - 9 - h, 5f, h, 0xFFC66B43)
            rect(c, x + 1, floor - 7 - h, 3f, h - 2, 0xFFF2B85D)
            rect(c, x + 2, floor - 11, 1f, 3f, 0xFFFFE2A0)
        }
    }
}
