package com.Atom2Universe.app.games.hamlet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache

/** Original, articulated pixel art. Frames are rendered once on a 48 × 56 grid, with a margin for
 * halos, companions and the largest bosses; scaling keeps their crisp pixels. A shared bounded cache
 * serves portraits, residents and battle units. Enemy ids come from [HamletEnemies]. */
object HamletCharacterArt {
    enum class Pose(val frames: Int, val fps: Float) {
        IDLE(4, 3f), WALK(6, 9f), ATTACK(4, 16f), CAST(4, 7f), HURT(2, 12f), DOWN(1, 1f)
    }
    private const val W = 48
    private const val H = 56
    private const val PAD_X = 11
    private const val PAD_TOP = 12
    private const val PAD_BOTTOM = 4
    /** Cache key ranges; enemies start at zero. */
    private const val HEROES = 100_000
    private const val RESIDENTS = 200_000
    private const val HERO_HALOS = 300_000
    private const val BOSS_HALOS = 400_000
    private val cache = object : LruCache<Int, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount
    }
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val haloPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val destination = RectF()
    private data class Colors(val cloth: Int, val light: Int, val dark: Int, val skin: Int, val hair: Int, val trim: Int)
    private fun color(hex: Int) = hex or 0xFF000000.toInt()
    private fun palette(cloth: Int, light: Int, dark: Int, skin: Int, hair: Int, trim: Int) =
        Colors(color(cloth), color(light), color(dark), color(skin), color(hair), color(trim))
    private val residentPalettes = arrayOf(
        palette(0x4D9264, 0xA6CB79, 0x306344, 0xF5BA8C, 0x814831, 0xEED18A),
        palette(0x508AA6, 0xA3CDD0, 0x315D78, 0xEABA8E, 0x784D36, 0xD8AB64),
        palette(0x357C7C, 0x84BBB1, 0x234C62, 0xE2A77C, 0x283E43, 0xC0DBC2),
        palette(0x937CB0, 0xC7ADE0, 0x5D527F, 0xF4CBB5, 0xE6E5E1, 0xC9E5B5),
        palette(0xC66F8C, 0xF0B1B0, 0x874E78, 0xB97954, 0x493440, 0xF4D48D),
        palette(0x445F9D, 0x8499CA, 0x323F76, 0xEEC4A3, 0xD7DFE6, 0xE8C96C),
        palette(0xB98648, 0xE7C27E, 0x79533A, 0xCB9166, 0x593F2E, 0x8AC7B5),
        palette(0xB95348, 0xEE9D68, 0x733E45, 0xF0B18A, 0xCB613D, 0xEDC26C),
        palette(0xE1D4A8, 0xFFF3D1, 0xAB976D, 0xAC6C4A, 0x433332, 0xE5B755),
        palette(0x788958, 0xC2BF76, 0x4C6149, 0xDEAE80, 0x70472F, 0xEAD7A0),
        palette(0x7EAEBE, 0xCEEAF0, 0x556C9F, 0xF1CDBF, 0xDCE9ED, 0xACDFDD),
        palette(0xBB9254, 0xE5BD79, 0x76613F, 0xD49A72, 0x61493B, 0x9AD0CF)
    )

    fun prewarm(hero: Boolean, id: Int) {
        val glows = haloRadius(hero, id) > 0
        for (pose in Pose.entries) for (frame in 0 until pose.frames) {
            sprite(hero, id, pose, frame)
            if (glows && pose != Pose.DOWN) halo(hero, id, pose, frame)
        }
    }

    fun prewarmResident(id: Int) {
        for (pose in arrayOf(Pose.IDLE, Pose.WALK, Pose.CAST)) for (frame in 0 until pose.frames)
            sprite(true, id, pose, frame, resident = true)
    }

    fun drawResident(c: Canvas, id: Int, x: Float, foot: Float, size: Float, time: Float = 0f,
                     walking: Boolean = false, working: Boolean = false, mirrored: Boolean = false) {
        val pose = if (walking) Pose.WALK else if (working) Pose.CAST else Pose.IDLE
        drawBitmap(c, sprite(true, id, pose, frame(pose, time), resident = true), x, foot, size, mirrored, paint, 255)
    }

    fun bitmap(hero: Boolean, id: Int, pose: Pose = Pose.IDLE, time: Float = 0f): Bitmap = sprite(hero, id, pose, frame(pose, time))

    /** Where [bitmap] lands for a character whose box is [size] tall with its feet on [foot]. */
    fun bounds(x: Float, foot: Float, size: Float, out: RectF) {
        val k = size / H
        out.set(x - (W / 2f + PAD_X) * k, foot - (H + PAD_TOP) * k, x + (W / 2f + PAD_X) * k, foot + PAD_BOTTOM * k)
    }

    /** Size is the character's box; foot is the ground anchor, independent of pose. Heroes get their
     * rarity effects and bosses their menace, animated by [fx] (pass 0 to keep them still). */
    fun draw(c: Canvas, hero: Boolean, id: Int, x: Float, foot: Float, size: Float,
             time: Float = 0f, pose: Pose = Pose.IDLE, mirrored: Boolean = false, alpha: Int = 255, fx: Float = time) {
        val frame = frame(pose, time)
        val sprite = sprite(hero, id, pose, frame)
        if (pose == Pose.DOWN || haloRadius(hero, id) == 0) { drawBitmap(c, sprite, x, foot, size, mirrored, paint, alpha); return }
        if (hero) {
            val rarity = HamletHeroes.defs[Math.floorMod(id, HamletHeroes.defs.size)].rarity
            HamletHeroFx.under(c, rarity, x, foot, size, fx, mirrored, alpha)
            haloPaint.color = HamletHeroFx.aura(rarity, fx)
            drawBitmap(c, halo(true, id, pose, frame), x, foot, size, mirrored, haloPaint, (HamletHeroFx.haloAlpha(rarity, fx) * alpha).toInt())
            drawBitmap(c, sprite, x, foot, size, mirrored, paint, alpha)
            HamletHeroFx.over(c, rarity, x, foot, size, fx, mirrored, alpha)
        } else {
            HamletHeroFx.menaceUnder(c, x, foot, size, fx, mirrored, alpha)
            haloPaint.color = HamletHeroFx.MENACE
            drawBitmap(c, halo(false, id, pose, frame), x, foot, size, mirrored, haloPaint, (HamletHeroFx.menaceAlpha(fx) * alpha).toInt())
            drawBitmap(c, sprite, x, foot, size, mirrored, paint, alpha)
            HamletHeroFx.menaceOver(c, x, foot, size, fx, mirrored, alpha)
        }
    }

    private fun haloRadius(hero: Boolean, rawId: Int) = if (hero) HamletHeroFx.haloRadius(HamletHeroes.defs[Math.floorMod(rawId, HamletHeroes.defs.size)].rarity)
        else if (HamletEnemies.isBoss(Math.floorMod(rawId, HamletEnemies.COUNT))) 2 else 0

    private fun frame(pose: Pose, time: Float) = if (time.isFinite()) (time.coerceAtLeast(0f) * pose.fps).toInt() % pose.frames else 0

    private fun drawBitmap(c: Canvas, sprite: Bitmap, x: Float, foot: Float, size: Float, mirrored: Boolean, p: Paint, alpha: Int) {
        bounds(x, foot, size, destination)
        p.alpha = alpha.coerceIn(0, 255)
        c.save()
        if (mirrored) { c.translate(x * 2, 0f); c.scale(-1f, 1f) }
        c.drawBitmap(sprite, null, destination, p)
        c.restore()
        p.alpha = 255
    }

    private fun sprite(hero: Boolean, rawId: Int, pose: Pose, frame: Int, resident: Boolean = false): Bitmap {
        val id = Math.floorMod(rawId, if (resident) 12 else if (hero) HamletHeroes.defs.size else HamletEnemies.COUNT)
        val key = (if (resident) RESIDENTS else if (hero) HEROES else 0) + id * 256 + pose.ordinal * 16 + frame
        cache.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(W + PAD_X * 2, H + PAD_TOP + PAD_BOTTOM, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate(PAD_X.toFloat(), PAD_TOP.toFloat())
        if (pose == Pose.DOWN) {
            canvas.translate(5f, 43f); canvas.scale(0.8f, 0.8f); canvas.rotate(76f); canvas.translate(-24f, -50f)
        }
        when {
            resident -> Pixels(canvas).resident(id, pose, frame, residentPalettes[id])
            hero -> HeroPainter(canvas).paint(HamletHeroLooks[id], HamletHeroes.defs[id], pose, frame)
            else -> EnemyPainter(canvas).paint(id, pose, frame)
        }
        cache.put(key, bmp)
        return bmp
    }

    /** Glow around a silhouette, as an alpha mask tinted when drawn. */
    private fun halo(hero: Boolean, rawId: Int, pose: Pose, frame: Int): Bitmap {
        val id = Math.floorMod(rawId, if (hero) HamletHeroes.defs.size else HamletEnemies.COUNT)
        val key = (if (hero) HERO_HALOS else BOSS_HALOS) + id * 256 + pose.ordinal * 16 + frame
        cache.get(key)?.let { return it }
        val source = sprite(hero, id, pose, frame)
        val w = source.width; val h = source.height
        val radius = haloRadius(hero, id)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val glow = IntArray(w * h)
        val strength = intArrayOf(0, 255, 140, 60)
        for (y in 0 until h) for (x in 0 until w) {
            if (pixels[y * w + x] ushr 24 != 0) continue
            var nearest = Int.MAX_VALUE
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val d = dx * dx + dy * dy
                if (d == 0 || d >= nearest || d > radius * radius) continue
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h && pixels[yy * w + xx] ushr 24 != 0) nearest = d
            }
            if (nearest == Int.MAX_VALUE) continue
            val ring = if (nearest <= 1) 1 else if (nearest <= 4) 2 else 3
            glow[y * w + x] = (strength[ring] shl 24) or 0xFFFFFF
        }
        val shape = Bitmap.createBitmap(glow, w, h, Bitmap.Config.ARGB_8888)
        val mask = shape.extractAlpha()
        shape.recycle()
        cache.put(key, mask)
        return mask
    }

    /** Residents and enemies; only used while constructing cached frames, never in a drawing loop. */
    private class Pixels(c: Canvas) : PixelPen(c) {
        fun resident(id: Int, pose: Pose, f: Int, q: Colors) {
            val walk = if (pose == Pose.WALK) intArrayOf(0, 2, 3, 0, -2, -3)[f] else 0
            val bob = if (pose == Pose.WALK) if (f % 3 == 1) -1 else 0 else if (pose == Pose.IDLE && f == 2) -1 else 0
            val cast = pose == Pose.CAST
            c.save(); c.translate(0f, bob.toFloat())
            box(18 - walk / 2, 39, 6, 11 - kotlin.math.abs(walk) / 2, q.dark)
            box(25 + walk / 2, 39, 6, 11 + kotlin.math.abs(walk) / 2, q.cloth)
            box(17 - walk / 2, 48 - kotlin.math.abs(walk) / 2, 8, 4, color(0x594B44))
            box(24 + walk / 2, 49, 9, 4, color(0x594B44))
            rect(26 + walk / 2, 49, 4, 1, q.trim)
            box(17, 27, 14, 16, q.cloth)
            rect(19, 30, 3, 9, q.light); rect(28, 30, 2, 10, q.dark)
            rect(17, 39, 14, 3, q.dark); box(23, 39, 4, 3, q.trim)
            val handY = if (cast) intArrayOf(29, 23, 21, 25)[f] else 37 - walk / 2
            val handX = if (cast) 34 else 32
            line(18, 29, 15 - walk / 2, 36 + walk / 2, ink, 6)
            line(18, 29, 15 - walk / 2, 36 + walk / 2, q.cloth, 4)
            box(12 - walk / 2, 34 + walk / 2, 5, 5, q.skin)
            line(29, 30, handX, handY, ink, 6); line(29, 30, handX, handY, q.light, 4)
            box(17, 14, 16, 16, q.hair)
            box(20, 17, 13, 12, q.skin)
            rect(19, 17, 4, 4, q.hair); rect(20, 15, 11, 3, q.hair)
            rect(21, 19, 3, 2, blend(q.hair, cream)); rect(32, 23, 2, 3, q.skin)
            val blink = pose == Pose.IDLE && f == 3
            rect(26, 22, 2, if (blink) 1 else 3, ink); rect(31, 22, 1, if (blink) 1 else 2, ink)
            if (!blink) rect(26, 22, 1, 1, cream)
            rect(27, 27, 3, 1, color(0xAA6D60))
            when (id) {
                0 -> {
                    for (j in 0..4) box(16 + (j + f) % 2, 25 + j * 3, 4, 4, q.hair)
                    poly(q.light, 17, 15, 14, 10, 19, 12, 23, 9, 21, 15); rect(16, 38, 4, 2, q.trim)
                }
                3 -> { rect(16, 18, 4, 20, q.hair); rect(18, 31, 3, 10, color(0xBDBDD4)); box(24, 13, 5, 4, q.trim); rect(32, 17, 3, 7, q.hair) }
                4 -> { for (j in 0..3) box(15 + j * 4, 12 + j % 2, 6, 6, q.hair); box(14, 18, 5, 15, q.hair); box(16, 15, 5, 4, q.light); rect(18, 16, 1, 1, cream) }
                7 -> { poly(q.hair, 17, 22, 15, 14, 20, 15, 19, 9, 25, 13, 28, 10, 34, 18, 24, 18, 20, 21); rect(17, 28, 16, 3, q.trim); line(18, 30, 9, 34 - f % 2, q.cloth, 3) }
                10 -> { poly(q.hair, 16, 32, 17, 16, 23, 12, 31, 14, 35, 22, 31, 20, 28, 18, 22, 18, 20, 35); gem(25, 14, q.trim, cream, 2); rect(18, 28, 2, 11, color(0xA8C7DB)) }
                11 -> { box(16, 14, 18, 7, q.dark); box(18, 15, 6, 5, q.trim); box(26, 15, 6, 5, q.trim); rect(19, 16, 3, 2, q.light); rect(27, 16, 3, 2, q.light); box(12, 29, 7, 12, q.dark); rect(13, 29, 3, 8, q.trim) }
            }
            // Aprons and everyday tools, rather than armed miniature heroes.
            rect(20, 32, 9, 10, q.light); rect(21, 34, 2, 7, q.trim)
            if (id % 4 == 0) {
                box(handX - 3, handY + 1, 10, 8, color(0xB58D53)); rect(handX - 2, handY + 3, 8, 1, q.trim)
                poly(color(0x69A873), handX - 3, handY + 1, handX - 4, handY - 4, handX, handY - 2, handX + 3, handY - 6, handX + 5, handY + 1)
            } else if (id % 4 == 1) {
                line(handX, handY + 5, handX + 2, handY - 7, color(0x946E45), 2); box(handX - 2, handY - 9, 9, 4, color(0x9DAFB5))
            } else if (id % 4 == 2) {
                box(handX - 2, handY - 2, 8, 10, q.dark); rect(handX + 1, handY, 4, 6, cream)
            } else {
                box(handX - 1, handY, 9, 8, color(0x75ADB6)); line(handX + 7, handY + 4, handX + 11, handY, color(0x75ADB6), 3)
                if (cast) { rect(handX + 11, handY + 4, 1, 2, color(0xB7E2E0)); rect(handX + 13, handY + 7 + f % 2, 1, 2, color(0xB7E2E0)) }
            }
            box(handX - 2, handY - 2, 5, 5, q.skin)
            c.restore()
        }
    }
}
