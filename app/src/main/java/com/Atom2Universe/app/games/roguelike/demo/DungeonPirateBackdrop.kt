package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sin

/** Deux lieux du navire : mobilier fixe en cache, voilure et lanterne animées au fond. */
internal object DungeonPirateBackdrop {
    private val paint = Paint().apply { isAntiAlias = false }
    private val path = Path()
    private fun tone(night: Boolean, day: Long, dark: Long) = if (night) dark else day
    private fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Long) {
        paint.color = color.toInt(); c.drawRect(x, y, x + w, y + h, paint)
    }
    private fun poly(c: Canvas, color: Long, vararg p: Float) {
        path.reset(); path.moveTo(p[0], p[1])
        for (i in 2 until p.size step 2) path.lineTo(p[i], p[i + 1])
        path.close(); paint.color = color.toInt(); c.drawPath(path, paint)
    }
    private fun upper(c: Canvas, floor: Float) {
        c.translate(0f, floor); c.scale(1f, (floor / 84f).coerceAtMost(1f)); c.translate(0f, -84f)
    }
    fun draw(c: Canvas, floor: Float, height: Float, cabin: Boolean, night: Boolean) {
        c.drawColor(tone(night, 0xFF95BCC5, 0xFF1E2E4B).toInt())
        c.save(); upper(c, floor)
        if (cabin) cabinWall(c) else deckWall(c, night)
        c.restore()
        rect(c, 0f, floor + 2, 240f, height, tone(night, 0xFF574638, 0xFF303E49))
        for (row in 0..((height - floor) / 12).toInt()) for (col in -1..5) {
            val x = col * 51f + row % 2 * 25
            val y = floor + 4 + row * 12
            rect(c, x, y, 49f, 10f, tone(night,
                if (cabin) 0xFF735746 else 0xFF967950, 0xFF46535B))
            rect(c, x + 3, y + 2, 33f, 1f, tone(night, if (cabin) 0xFF85634C else 0xFFA88A5D, 0xFF53616A))
            rect(c, x + 2, y + 7, 1f, 1f, tone(night, 0xFF473D35, 0xFF2F3B48))
        }
        // Caisses, coffres et tonneaux restent contre les bords.
        for (side in 0..1) {
            c.save()
            if (side == 1) { c.translate(240f, 0f); c.scale(-1f, 1f) }
            if (cabin) {
                rect(c, 0f, floor + 21, 17f, 18f, 0xFF4D3836)
                poly(c, 0xFF966647, 0f, floor + 21, 3f, floor + 16, 13f, floor + 16,
                    17f, floor + 21, 17f, floor + 29, 0f, floor + 29)
                rect(c, 2f, floor + 19, 2f, 17f, 0xFFCAAA65)
                rect(c, 12f, floor + 19, 2f, 17f, 0xFFCAAA65)
                rect(c, 7f, floor + 27, 3f, 4f, 0xFFE1C57F)
            } else {
                rect(c, 0f, floor + 4, 5f, height - floor, tone(night, 0xFF705441, 0xFF344653))
                rect(c, 0f, floor + 4, 2f, height - floor, tone(night, 0xFFB19667, 0xFF71818A))
            }
            barrel(c, 2f, height - 33, night)
            c.restore()
        }
    }
    private fun barrel(c: Canvas, x: Float, y: Float, night: Boolean) {
        poly(c, tone(night, 0xFF8C6547, 0xFF4B5254), x + 2, y, x + 12, y,
            x + 15, y + 8, x + 14, y + 21, x + 2, y + 21, x, y + 8)
        for (i in 0..2) rect(c, x + 3 + i * 4, y + 2, 1f, 17f, tone(night, 0xFFC09A60, 0xFF788078))
        for (dy in intArrayOf(4, 16)) rect(c, x + 1, y + dy, 13f, 3f, tone(night, 0xFF4A5B5E, 0xFF2F424F))
    }
    private fun deckWall(c: Canvas, night: Boolean) {
        if (night) {
            for (i in 0..22) rect(c, (i * 53 % 238).toFloat(), 3f + i * 13 % 40, 1f, 1f, 0xFFBCCBD4)
            rect(c, 184f, 11f, 12f, 12f, 0xFFE1E0B9)
            rect(c, 189f, 9f, 10f, 11f, 0xFF1E2E4B)
        } else {
            rect(c, 184f, 12f, 13f, 13f, 0xFFF2DF9F)
            for (i in 0..2) {
                rect(c, 8f + i * 78, 19f + i % 2 * 6, 30f, 4f, 0xFFDCE4D3)
                rect(c, 15f + i * 78, 16f + i % 2 * 6, 17f, 4f, 0xFFDCE4D3)
            }
        }
        rect(c, 0f, 48f, 240f, 36f, tone(night, 0xFF438996, 0xFF294B68))
        rect(c, 0f, 48f, 240f, 1f, tone(night, 0xFFB2D6CB, 0xFF6E8EA1))
        for (i in 0..24) rect(c, (i * 43 % 231).toFloat(), 51f + i * 7 % 24,
            7f + i % 4, 1f, tone(night, 0xFF6BAAB0, 0xFF3D6380))
        // Cordages en dehors de la zone de combat.
        for (side in 0..1) {
            c.save()
            if (side == 1) { c.translate(240f, 0f); c.scale(-1f, 1f) }
            for (i in 0..2) poly(c, tone(night, 0xFF8C7A55, 0xFF6C7780),
                45f + i * 7, 0f, 46f + i * 7, 0f, 15f + i * 6, 76f, 14f + i * 6, 76f)
            for (i in 0..6) rect(c, 22f + (6 - i) * 3, 18f + i * 8, 17f, 1f, tone(night, 0xFFB8A474, 0xFF78868D))
            c.restore()
        }
        rect(c, 0f, 74f, 240f, 10f, tone(night, 0xFF765440, 0xFF354553))
        for (i in 0..15) rect(c, i * 16f, 66f, 4f, 18f, tone(night, 0xFF93724F, 0xFF546573))
        rect(c, 0f, 65f, 240f, 4f, tone(night, 0xFFC29B65, 0xFF819099))
        // Mât au fond, et vergue haute réservée à la voile animée.
        rect(c, 116f, 0f, 7f, 84f, tone(night, 0xFF795941, 0xFF495560))
        rect(c, 117f, 0f, 2f, 84f, tone(night, 0xFFC7A26B, 0xFF859093))
        rect(c, 82f, 23f, 76f, 3f, tone(night, 0xFF8E6B4A, 0xFF61717B))
        for (i in 0..2) rect(c, 115f, 59f + i * 3, 9f, 1f, tone(night, 0xFFBDA575, 0xFF919D9C))
        // Canons courts adossés au bastingage, au-dessus des acteurs.
        for (x in intArrayOf(35, 180)) {
            rect(c, x.toFloat(), 75f, 24f, 5f, tone(night, 0xFF8C5B42, 0xFF4C4952))
            for (dx in intArrayOf(2, 18)) {
                rect(c, x + dx.toFloat(), 78f, 6f, 6f, 0xFF293643)
                rect(c, x + dx + 2f, 80f, 2f, 2f, 0xFF9CA592)
            }
            poly(c, 0xFF3B515C, x + 4f, 74f, x + 7f, 63f, x + 17f, 63f, x + 20f, 74f)
            rect(c, x + 6f, 61f, 13f, 5f, 0xFF71868B)
            rect(c, x + 9f, 62f, 7f, 2f, 0xFF1D303D)
        }
    }
    private fun cabinWall(c: Canvas) {
        rect(c, 0f, 0f, 240f, 84f, 0xFF4D393B)
        for (i in 0..19) {
            rect(c, i * 12f, 0f, 10f, 84f, if (i % 3 == 0) 0xFF725044 else 0xFF62463F)
            rect(c, i * 12f + 1, 4f, 1f, 71f, 0xFF89624A)
        }
        // Grande fenêtre de poupe ; rideaux bordeaux et traverses dorées.
        poly(c, 0xFFB59A6A, 72f, 61f, 72f, 18f, 84f, 9f, 159f, 9f,
            171f, 18f, 171f, 61f)
        rect(c, 77f, 18f, 89f, 39f, 0xFF80A9B6)
        rect(c, 77f, 38f, 89f, 19f, 0xFF397489)
        for (x in intArrayOf(98, 121, 144)) rect(c, x.toFloat(), 17f, 3f, 40f, 0xFFBEA06D)
        rect(c, 76f, 35f, 92f, 3f, 0xFFCEB581)
        for (x in intArrayOf(67, 167)) {
            rect(c, x.toFloat(), 14f, 9f, 48f, 0xFF723F51)
            rect(c, x + 2f, 15f, 2f, 46f, 0xFFAC6773)
            rect(c, x.toFloat(), 43f, 9f, 2f, 0xFFD5B477)
        }
        rect(c, 65f, 11f, 112f, 3f, 0xFFD0AE76)
        // Bureau de navigation au fond, carte et instruments sur le plateau.
        rect(c, 77f, 71f, 90f, 5f, 0xFFAA7E52)
        rect(c, 81f, 76f, 6f, 8f, 0xFF684939)
        rect(c, 156f, 76f, 6f, 8f, 0xFF684939)
        poly(c, 0xFFE0C99B, 91f, 63f, 133f, 64f, 140f, 71f, 86f, 71f)
        poly(c, 0xFF899975, 101f, 65f, 112f, 65f, 117f, 68f, 109f, 69f, 104f, 67f)
        rect(c, 120f, 66f, 5f, 1f, 0xFF9F7156)
        rect(c, 122f, 65f, 1f, 4f, 0xFF9F7156)
        rect(c, 145f, 67f, 12f, 3f, 0xFFCFB575)
        rect(c, 148f, 65f, 6f, 2f, 0xFF679993)
        // Bibliothèque, bouteilles et coffre mural sur les côtés.
        rect(c, 8f, 20f, 42f, 59f, 0xFF342F35)
        for (row in 0..2) {
            val y = 36f + row * 18
            for (i in 0..5) {
                rect(c, 12f + i * 6, y - 11 + i % 3, 4f, 11f - i % 3,
                    when (i % 3) { 0 -> 0xFF618780; 1 -> 0xFFB88E61; else -> 0xFF9A6066 })
                rect(c, 12f + i * 6, y - 7, 4f, 1f, 0xFFD0BA87)
            }
            rect(c, 8f, y, 42f, 3f, 0xFFB58A5D)
        }
        rect(c, 189f, 56f, 40f, 25f, 0xFF79513E)
        rect(c, 188f, 54f, 42f, 4f, 0xFFB78D5B)
        for (x in intArrayOf(192, 221)) rect(c, x.toFloat(), 58f, 3f, 23f, 0xFFC4A469)
        for (i in 0..2) {
            rect(c, 195f + i * 11, 43f, 6f, 11f, 0xFF4C8575)
            rect(c, 197f + i * 11, 39f, 2f, 5f, 0xFFAFBF90)
            rect(c, 196f + i * 11, 48f, 4f, 3f, 0xFFCFBE93)
        }
        // Poutres courbes stylisées et points d'attache du plafond.
        for (x in intArrayOf(55, 179)) {
            rect(c, x.toFloat(), 0f, 5f, 84f, 0xFF3C3034)
            rect(c, x + 1f, 0f, 1f, 82f, 0xFFAB8055)
        }
        rect(c, 0f, 2f, 240f, 5f, 0xFFB18A5A)
    }
    fun atmosphere(c: Canvas, floor: Float, clock: Float, cabin: Boolean, night: Boolean) {
        c.save(); upper(c, floor)
        val tick = (clock / 150f).toInt()
        if (cabin) {
            // Reflets confinés aux carreaux, sans recouvrir les montants.
            for (pane in 0..3) {
                val x = 80f + pane * 23 + tick % 8
                rect(c, x, 43f + pane % 2 * 5, 8f, 1f, 0xFF84B7BC)
            }
            c.save(); c.rotate(sin(clock / 1800f) * 6f, 121f, 5f)
            for (i in 0..4) rect(c, 120f + i % 2, 7f + i * 3, 2f, 2f, 0xFFD0B783)
            rect(c, 113f, 25f, 18f, 20f, 0x25F1BD6B)
            rect(c, 116f, 25f, 11f, 16f, 0xFF574C3E)
            rect(c, 118f, 28f, 7f, 10f, 0xFFBD995D)
            rect(c, 120f, 30f + tick % 2, 3f, 7f, 0xFFF3C777)
            rect(c, 121f, 33f, 1f, 4f, 0xFFFFE6AB)
            rect(c, 115f, 24f, 13f, 3f, 0xFFC0A370)
            rect(c, 115f, 40f, 13f, 3f, 0xFFAC8C5E)
            c.restore()
        } else {
            for (i in 0..9) rect(c, ((i * 29 + tick) % 225).toFloat(),
                52f + i % 3 * 4, 8f, 1f, tone(night, 0xFFABD1CC, 0xFF6A8FA5))
            // Voile attachée à la vergue : bord supérieur fixe, ourlet gonflé par le vent.
            for (col in 0..61) {
                val belly = sin(col / 61f * Math.PI).toFloat()
                val h = 13f + belly * (9f + sin(clock / 900f) * 2)
                rect(c, 89f + col, 26f, 1f, h, tone(night,
                    if (col % 15 == 0) 0xFFB9AB86 else 0xFFE1D4AF,
                    if (col % 15 == 0) 0xFF647A8A else 0xFF9BAAB6))
            }
            // Pavillon noir : tête de mort pixelisée solidaire des plis du tissu.
            for (col in 0..25) {
                val wave = sin(clock / 420f - col * .22f) * col / 12f
                rect(c, 124f + col, 5f + wave, 1f, 12f - if (col > 21) 2 else 0,
                    tone(night, 0xFF273340, 0xFF17273A))
                if (col in 8..15) rect(c, 124f + col, 8f + wave, 1f, 4f, 0xFFD1D3BE)
                if (col in 10..13) rect(c, 124f + col, 12f + wave, 1f, 2f, 0xFFD1D3BE)
                if (col == 10 || col == 13) rect(c, 124f + col, 9f + wave, 1f, 1f, 0xFF263747)
            }
        }
        c.restore()
    }
}
