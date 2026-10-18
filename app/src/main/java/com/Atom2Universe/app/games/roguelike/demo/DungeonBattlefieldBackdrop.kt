package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.roundToInt
import kotlin.math.sin

/** Décor fixe en cache ; étoffes et foyers animés uniquement derrière les combattants. */
internal object DungeonBattlefieldBackdrop {
    private val paint = Paint().apply { isAntiAlias = false }
    private val path = Path()
    private fun tone(night: Boolean, day: Long, dark: Long) = (if (night) dark else day).toInt()
    private fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color
        c.drawRect(x, y, x + w, y + h, paint)
    }
    private fun polygon(c: Canvas, color: Int, vararg points: Float) {
        path.reset()
        path.moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
        path.close()
        paint.color = color
        c.drawPath(path, paint)
    }

    fun draw(c: Canvas, floor: Float, height: Float, night: Boolean) {
        val sky = tone(night, 0xFFA4B7BC, 0xFF1D293F)
        c.drawColor(sky)
        rect(c, 0f, floor * .45f, 240f, floor * .55f, tone(night, 0xFFD0C7AA, 0xFF394252))
        if (night) {
            for (i in 0..17) rect(c, (i * 53 % 235 + 2).toFloat(),
                3f + i * 7 % (floor * .4f).toInt().coerceAtLeast(1), 1f, 1f, 0xFF9FB5C9.toInt())
            rect(c, 178f, 13f, 12f, 12f, 0xFFD4DCC8.toInt())
            rect(c, 183f, 11f, 10f, 11f, sky)
        } else {
            rect(c, 178f, 16f, 13f, 12f, 0xFFE7D4A3.toInt())
        }
        for (i in 0..4) {
            val x = i * 53f - 10
            val y = 10f + i % 3 * 8
            rect(c, x, y, 37f, 5f, tone(night, 0xFF8F9FA8, 0xFF2F3A50))
            rect(c, x + 12, y - 3, 21f, 4f, tone(night, 0xFFB9C3BF, 0xFF38455A))
        }
        polygon(c, tone(night, 0xFF7D8E83, 0xFF334751), 0f, floor - 16,
            41f, floor - 27, 91f, floor - 14, 142f, floor - 22, 190f, floor - 12,
            240f, floor - 24, 240f, floor + 4, 0f, floor + 4)
        // Ruine lointaine, basse et peu contrastée.
        val ruin = tone(night, 0xFF6D7C77, 0xFF293E49)
        rect(c, 100f, floor - 25, 37f, 20f, ruin)
        rect(c, 98f, floor - 34, 10f, 29f, ruin)
        rect(c, 129f, floor - 30, 10f, 25f, ruin)
        for (i in 0..3) rect(c, 109f + i * 6, floor - 28 + i % 2 * 3, 3f, 5f, ruin)
        rect(c, 114f, floor - 16, 8f, 11f, tone(night, 0xFF566862, 0xFF22353F))
        rect(c, 0f, floor - 5, 240f, height, tone(night, 0xFF8E8A6C, 0xFF414F50))
        rect(c, 0f, floor - 5, 240f, 8f, tone(night, 0xFF788065, 0xFF354948))
        // Terre piétinée : texture discrète, sans objets sur les positions de combat.
        for (i in 0..170) {
            val x = (i * 61 % 237).toFloat()
            val y = floor + 8 + i * 37 % (height - floor - 10).toInt().coerceAtLeast(1)
            rect(c, x, y, 2f + i % 4, 1f, tone(night, 0xFF9B9579, 0xFF4D5A58))
            if (x < 17 || x > 223) {
                rect(c, x, y - 3, 1f, 4f, tone(night, 0xFF596F58, 0xFF2B4244))
            }
        }
        // Chariot éventré, au fond à gauche.
        val timber = tone(night, 0xFF725541, 0xFF384047)
        polygon(c, timber, 4f, floor - 16, 34f, floor - 20, 42f, floor - 2, 8f, floor + 1)
        for (i in 0..3) rect(c, 8f, floor - 15 + i * 4, 26f - i % 2 * 7, 2f,
            tone(night, 0xFFA28156, 0xFF647064))
        polygon(c, timber, 30f, floor - 19, 33f, floor - 20, 47f, floor - 5, 44f, floor - 3)
        wheel(c, 14f, floor + 1, night)
        wheel(c, 35f, floor + 3, night)
        rect(c, 40f, floor + 2, 16f, 2f, timber)
        // Deux étendards hauts encadrent la ruine ; le tissu sera dessiné séparément.
        for (x in intArrayOf(67, 159)) {
            rect(c, x.toFloat(), floor - 53, 2f, 57f, tone(night, 0xFF685746, 0xFF4A5B62))
            rect(c, x.toFloat(), floor - 53, 1f, 54f, tone(night, 0xFFB4A084, 0xFF8D9994))
            polygon(c, tone(night, 0xFFD5C8A3, 0xFFADB7AA), x - 2f, floor - 51,
                x + 1f, floor - 58, x + 4f, floor - 51)
        }
        // Boucliers cabossés, lances et planches confinés aux marges.
        for (side in 0..1) {
            c.save()
            if (side == 1) { c.translate(240f, 0f); c.scale(-1f, 1f) }
            shield(c, 5f, floor + 25, night, side == 0)
            shield(c, 3f, height - 29, night, side != 0)
            polygon(c, timber, 2f, floor + 63, 4f, floor + 61, 18f, floor + 78, 16f, floor + 80)
            rect(c, 8f, floor + 46, 2f, 28f, tone(night, 0xFF8D7958, 0xFF66716A))
            polygon(c, tone(night, 0xFFBCC3B1, 0xFF839A9A), 6f, floor + 47,
                9f, floor + 39, 12f, floor + 47)
            c.restore()
        }
        for (x in intArrayOf(49, 204)) {
            rect(c, x - 6f, floor + 1, 15f, 3f, tone(night, 0xFF544F42, 0xFF2D353C))
            rect(c, x - 3f, floor, 9f, 2f, 0xFF9B6245.toInt())
        }
    }

    private fun wheel(c: Canvas, x: Float, y: Float, night: Boolean) {
        val rim = tone(night, 0xFF4D4940, 0xFF26373D)
        polygon(c, rim, x - 7, y - 3, x - 3, y - 7, x + 4, y - 7, x + 8, y - 3,
            x + 8, y + 3, x + 4, y + 7, x - 3, y + 7, x - 7, y + 3)
        rect(c, x - 4, y - 3, 9f, 7f, tone(night, 0xFF9F926C, 0xFF556159))
        rect(c, x, y - 5, 2f, 11f, rim)
        rect(c, x - 5, y, 12f, 2f, rim)
        rect(c, x, y, 2f, 2f, tone(night, 0xFFC1AE86, 0xFF8B9990))
    }

    private fun shield(c: Canvas, x: Float, y: Float, night: Boolean, red: Boolean) {
        polygon(c, tone(night, 0xFFB9B49B, 0xFF819292), x, y, x + 11, y - 2,
            x + 13, y + 9, x + 8, y + 15, x + 2, y + 11)
        polygon(c, tone(night, if (red) 0xFF965957 else 0xFF4F7982,
            if (red) 0xFF654753 else 0xFF39596D), x + 2, y + 2, x + 9, y,
            x + 11, y + 8, x + 7, y + 12, x + 4, y + 9)
        rect(c, x + 6, y + 2, 2f, 8f, tone(night, 0xFFD7BC7C, 0xFFA59F7E))
        rect(c, x + 3, y + 5, 7f, 2f, tone(night, 0xFFD7BC7C, 0xFFA59F7E))
        rect(c, x + 9, y + 1, 3f, 2f, tone(night, 0xFF4B4942, 0xFF293A43))
    }

    fun atmosphere(c: Canvas, floor: Float, clock: Float, night: Boolean) {
        val tick = (clock / 130f).toInt()
        for (i in 0..1) {
            val x = if (i == 0) 69f else 161f
            val y = floor - 50
            // Colonnes de tissu : l'attache reste immobile, les plis gagnent en amplitude au bout.
            for (column in 0..23) {
                val wave = (sin(tick * .45f - column * .25f + i) * column / 10f).roundToInt()
                val edge = when { column > 20 -> 10f; column > 17 -> 15f; else -> 19f }
                val shade = (column + tick) % 12 < 4
                val cloth = if (i == 0) tone(night, if (shade) 0xFF9B5360 else 0xFFB66B70,
                    if (shade) 0xFF643E55 else 0xFF86586D)
                else tone(night, if (shade) 0xFF3D6C80 else 0xFF578B99,
                    if (shade) 0xFF30465F else 0xFF526B85)
                rect(c, x + column, y + wave, 1f, edge, cloth)
                rect(c, x + column, y + wave, 1f, 1f, tone(night, 0xFFD3B88A, 0xFF9C9D91))
                if (column in 7..14) rect(c, x + column, y + wave + 7, 1f,
                    if (column in 10..11) 7f else 2f, tone(night, 0xFFE1C58C, 0xFFB0AE90))
            }
        }
        for (x in intArrayOf(49, 204)) {
            if (night) rect(c, x - 8f, floor - 4, 19f, 9f, 0x24E9A05A)
            for (j in 0..2) {
                val h = 3f + (tick + j * 2 + x) % 4
                rect(c, x - 2f + j * 3, floor - h, 3f, h, 0xFFB86641.toInt())
                rect(c, x - 1f + j * 3, floor - h + 2, 1f, h - 1, 0xFFE6AE5D.toInt())
            }
            for (j in 0..2) {
                val rise = (tick + j * 7) % 22
                rect(c, x + rise / 5f, floor - 9 - rise, 3f + j, 2f,
                    tone(night, 0x55707875, 0x55576573))
            }
        }
    }
}
