package com.Atom2Universe.app.games.roguelike.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.roundToInt
import kotlin.math.sin

/** Équipage articulé en pixel art : bandana et sabre, brute à hache, capitaine au tricorne. */
internal object DungeonPirateSprites {
    fun create(style: DungeonDemoSprites.MonsterStyle, pose: Int = 0): Bitmap {
        val captain = style == DungeonDemoSprites.MonsterStyle.PIRATE_CAPTAIN
        val brute = style == DungeonDemoSprites.MonsterStyle.PIRATE_BRUTE
        val bitmap = Bitmap.createBitmap(42, 40, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = false }
        val ink = 0xFF192332.toInt()
        val shade = style.shadow.toInt()
        val cloth = style.fur.toInt()
        val trim = style.light.toInt()
        val skin = style.skin.toInt()
        val gold = style.eye.toInt()
        val skinLight = 0xFFE5B78B.toInt()
        val steel = 0xFFB7D6DB.toInt()
        val red = 0xFFA54450.toInt()
        val sway = sin(pose * Math.PI / 4).roundToInt()
        val step = if (pose in 1..3) 1 else if (pose in 5..7) -1 else 0
        fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
            paint.color = color
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
        }
        fun plate(x: Int, y: Int, w: Int, h: Int, color: Int) {
            rect(x + 1, y, w - 2, h, ink)
            rect(x, y + 1, w, h - 2, ink)
            rect(x + 1, y + 1, w - 2, h - 2, color)
        }

        // Pieds fixes en hauteur, petit déplacement horizontal alterné.
        for (side in 0..1) {
            val x = 13 + side * 9 + if (side == 0) step else -step
            plate(x, 28, 7, 9, shade)
            rect(x + 1, 29, 2, 5, cloth)
            plate(x - 2, 35, 9, 4, shade)
            rect(x - 1, 36, 4, 1, if (captain) gold else cloth)
        }
        if (captain) {
            // Long manteau rouge, pans séparés et liserés dorés.
            plate(8, 16 + sway, 25, 17, shade)
            rect(10, 18 + sway, 7, 14, cloth)
            rect(24, 18 + sway, 7, 14, cloth)
            rect(11, 20 + sway, 2, 11, gold)
            rect(28, 20 + sway, 2, 11, gold)
        }
        val left = if (brute) 7 else 11
        val width = if (brute) 25 else 19
        plate(left, 16 + sway, width, 14, cloth)
        if (brute) {
            // Torse massif, gilet ouvert et bandoulière.
            rect(13, 17 + sway, 12, 9, skin)
            rect(14, 18 + sway, 4, 5, skinLight)
            for (i in 0..5) rect(12 + i * 2, 17 + i + sway, 4, 2, shade)
        } else if (captain) {
            rect(17, 17 + sway, 7, 9, trim)
            rect(18, 20 + sway, 5, 7, shade)
            for (y in 21..25 step 2) rect(19, y + sway, 2, 1, gold)
            rect(9, 17 + sway, 7, 3, gold)
            rect(26, 17 + sway, 7, 3, gold)
        } else {
            // Marinière claire à rayures bleues, foulard rouge.
            rect(13, 18 + sway, 15, 9, trim)
            for (y in 20..26 step 3) rect(13, y + sway, 15, 1, cloth)
            rect(17, 16 + sway, 6, 3, red)
            rect(19, 19 + sway, 3, 4, red)
        }
        rect(left + 1, 27 + sway, width - 2, 3, shade)
        rect(18, 27 + sway, 5, 3, gold)
        rect(19, 28 + sway, 3, 1, ink)

        // Bras et mains, puis arme ; toute l'amplitude reste dans la grille.
        plate(left - 3, 18 + sway, 5, 9, if (brute) skin else cloth)
        plate(left - 3, 25 + sway, 5, 5, skin)
        plate(left + width - 1, 18 - sway, 5, 9, if (brute) skin else cloth)
        if (captain) {
            rect(left - 2, 28 + sway, 2, 4, steel)
            rect(left - 1, 31 + sway, 4, 2, steel)
            rect(left + 2, 29 + sway, 2, 3, steel)
        }
        plate(left + width - 1, 25 - sway, 5, 4, skin)
        val weaponX = if (brute) 36 else 34
        rect(weaponX, 16 - sway, 2, 16, ink)
        rect(weaponX, 26 - sway, 1, 5, gold)
        if (brute) {
            plate(32, 10 - sway, 9, 10, shade)
            rect(35, 11 - sway, 5, 7, steel)
            rect(39, 12 - sway, 2, 5, trim)
            rect(36, 8 - sway, 2, 16, 0xFF8A674B.toInt())
        } else {
            rect(weaponX - 1, 11 - sway, 3, 14, steel)
            rect(weaponX - 3, 8 - sway, 3, 5, steel)
            rect(weaponX - 4, 7 - sway, 2, 3, trim)
            rect(weaponX - 3, 24 - sway, 6, 2, gold)
        }

        // Visages : nez, oreille, barbe et cache-œil lisibles même sur la carte.
        plate(13, 6 + sway, 15, 11, skin)
        rect(14, 8 + sway, 6, 5, skinLight)
        rect(11, 10 + sway, 3, 3, skin)
        rect(26, 9 + sway, 3, 4, skin)
        rect(14, 10 + sway, 2, 2, ink)
        rect(22, 10 + sway, 3, 2, ink)
        rect(17, 12 + sway, 3, 2, skinLight)
        if (brute || captain) {
            rect(14, 14 + sway, 12, 3, shade)
            rect(16, 17 + sway, 8, if (brute) 3 else 2, shade)
            rect(17, 14 + sway, 4, 1, trim)
        } else rect(17, 15 + sway, 5, 1, shade)
        if (captain) {
            // Tricorne noir, tête de mort et plume ; silhouette propre au boss.
            plate(8, 4 + sway, 25, 4, shade)
            plate(12, 1 + sway, 17, 6, shade)
            rect(9, 6 + sway, 22, 1, gold)
            rect(18, 2 + sway, 5, 3, trim)
            rect(19, 3 + sway, 1, 1, ink)
            rect(21, 3 + sway, 1, 1, ink)
            rect(19, 5 + sway, 3, 1, trim)
            rect(29, 1 + sway, 3, 4, red)
            rect(31, 0 + sway, 2, 3, trim)
            rect(21, 9 + sway, 5, 4, ink)
            rect(14, 8 + sway, 10, 1, shade)
            rect(27, 13 + sway, 2, 3, gold)
        } else if (brute) {
            rect(14, 6 + sway, 11, 2, skinLight)
            rect(26, 12 + sway, 2, 3, gold)
        } else {
            plate(12, 4 + sway, 17, 5, red)
            rect(14, 5 + sway, 12, 1, trim)
            rect(28, 7 + sway, 4, 2, red)
            rect(30, 9 + sway, 3, 4, red)
        }
        return bitmap
    }
}
