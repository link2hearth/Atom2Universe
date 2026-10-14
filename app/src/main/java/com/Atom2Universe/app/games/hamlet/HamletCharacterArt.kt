package com.Atom2Universe.app.games.hamlet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache

/** Original, articulated pixel art. Frames are rendered once on a 48 × 56 grid, with a margin for
 * heroes' halos and companions; scaling keeps their crisp pixels. A shared bounded cache serves
 * portraits, residents and battle units. */
object HamletCharacterArt {
    enum class Pose(val frames: Int, val fps: Float) {
        IDLE(4, 3f), WALK(6, 9f), ATTACK(4, 16f), CAST(4, 7f), HURT(2, 12f), DOWN(1, 1f)
    }
    private const val W = 48
    private const val H = 56
    private const val PAD_X = 11
    private const val PAD_TOP = 12
    private const val PAD_BOTTOM = 4
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
        for (pose in Pose.entries) for (frame in 0 until pose.frames) {
            sprite(hero, id, pose, frame)
            if (hero && pose != Pose.DOWN && HamletHeroes.defs[Math.floorMod(id, HamletHeroes.defs.size)].rarity != HeroRarity.COMMON) halo(id, pose, frame)
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

    /** Size is the character's box; foot is the ground anchor, independent of pose. Heroes also get
     * their rarity effects, animated by [fx] (pass 0 to keep them still). */
    fun draw(c: Canvas, hero: Boolean, id: Int, x: Float, foot: Float, size: Float,
             time: Float = 0f, pose: Pose = Pose.IDLE, mirrored: Boolean = false, alpha: Int = 255, fx: Float = time) {
        val frame = frame(pose, time)
        val sprite = sprite(hero, id, pose, frame)
        val rarity = if (hero && pose != Pose.DOWN) HamletHeroes.defs[Math.floorMod(id, HamletHeroes.defs.size)].rarity else null
        if (rarity == null || rarity == HeroRarity.COMMON) { drawBitmap(c, sprite, x, foot, size, mirrored, paint, alpha); return }
        HamletHeroFx.under(c, rarity, x, foot, size, fx, mirrored, alpha)
        haloPaint.color = HamletHeroFx.aura(rarity, fx)
        drawBitmap(c, halo(id, pose, frame), x, foot, size, mirrored, haloPaint, (HamletHeroFx.haloAlpha(rarity, fx) * alpha).toInt())
        drawBitmap(c, sprite, x, foot, size, mirrored, paint, alpha)
        HamletHeroFx.over(c, rarity, x, foot, size, fx, mirrored, alpha)
    }

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
        val id = Math.floorMod(rawId, if (resident) 12 else if (hero) HamletHeroes.defs.size else 8)
        val key = (if (resident) 20000 else if (hero) 10000 else 0) + id * 256 + pose.ordinal * 16 + frame
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
            else -> Pixels(canvas).enemy(id, pose, frame)
        }
        cache.put(key, bmp)
        return bmp
    }

    /** Glow around the hero's silhouette, as an alpha mask tinted when drawn. */
    private fun halo(rawId: Int, pose: Pose, frame: Int): Bitmap {
        val id = Math.floorMod(rawId, HamletHeroes.defs.size)
        val key = 30000 + id * 256 + pose.ordinal * 16 + frame
        cache.get(key)?.let { return it }
        val source = sprite(true, id, pose, frame)
        val w = source.width; val h = source.height
        val radius = HamletHeroFx.haloRadius(HamletHeroes.defs[id].rarity)
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

        fun enemy(id: Int, pose: Pose, f: Int) {
            val motion = when (pose) { Pose.WALK -> intArrayOf(0, 2, 3, 0, -2, -3)[f]; Pose.ATTACK -> intArrayOf(-2, 0, 4, 1)[f]; else -> 0 }
            val bob = when (pose) {
                Pose.IDLE, Pose.CAST -> f % 2
                Pose.WALK -> kotlin.math.abs(motion) / 2
                else -> 0
            }
            val blink = pose == Pose.HURT || pose == Pose.DOWN || (pose == Pose.IDLE && f == 3)
            val leaf = color(0x71AF75); val leafLight = color(0xBBDD85)
            c.save(); c.translate((if (pose == Pose.ATTACK) motion else 0).toFloat(), -bob.toFloat())
            when (id) {
                0 -> { // Moss sprite: squash/stretch, feet and springing sapling.
                    val squish = if (pose == Pose.ATTACK || pose == Pose.WALK) f % 2 * 3 else bob
                    box(11 - motion / 2, 46, 9, 6, color(0x3F7263)); box(28 + motion / 2, 46, 10, 6, color(0x3F7263))
                    box(10 - squish, 30 + squish, 27 + squish * 2, 20 - squish, color(0x448875))
                    box(13, 27 + squish, 23, 19 - squish, leaf); rect(16, 30 + squish, 8, 3, leafLight)
                    eyes(23, 38 + squish, blink, 8); rect(28, 45, 3, 1, ink)
                    line(23, 29 + squish, 24 + bob, 20, color(0x79593A), 2)
                    poly(leaf, 24, 23, 15, 17 - bob, 16, 13 - bob, 23, 15, 25, 21, 29, 14, 35, 13, 33, 20)
                    rect(12, 42, 3, 3, leafLight)
                }
                1 -> { // Bat wings genuinely open and fold.
                    val flap = intArrayOf(-6, 0, 6, 0)[f % 4]
                    poly(ink, 20, 29, 11, 20 + flap, 3, 22 + flap, 4, 38 + flap / 2, 11, 34, 16, 39, 22, 35)
                    poly(color(0x796697), 19, 30, 10, 23 + flap, 5, 24 + flap, 6, 34 + flap / 2, 12, 31, 18, 36)
                    poly(ink, 28, 29, 37, 20 + flap, 45, 22 + flap, 44, 38 + flap / 2, 37, 34, 32, 39, 26, 35)
                    poly(color(0xAA7DA8), 29, 30, 38, 23 + flap, 43, 24 + flap, 42, 34 + flap / 2, 36, 31, 30, 36)
                    box(18, 24, 15, 19, color(0x59476D)); poly(color(0xB190BC), 19, 27, 17, 15, 23, 21, 29, 21, 34, 16, 32, 27)
                    eyes(23, 29, blink, 6); rect(25, 35, 2, 3, cream); rect(30, 35, 1, 2, cream)
                }
                2 -> { // Small masked brigand, ragged cape and twin knives.
                    box(16 - motion / 2, 39, 6, 13, color(0x62494A)); box(27 + motion / 2, 39, 6, 13, color(0x62494A))
                    poly(ink, 16, 24, 30, 26, 36, 45, 24, 42, 13, 46); poly(color(0xAA604B), 17, 26, 29, 28, 33, 42, 23, 39, 16, 42)
                    box(17, 16, 17, 16, color(0x955F44)); poly(color(0xD89759), 16, 19, 18, 12, 27, 10, 34, 17)
                    rect(19, 21, 16, 6, ink); eyes(24, 22, blink, 7)
                    line(16, 30, 12, 40 - motion, ink, 5); line(30, 31, 37, 34 + motion, ink, 5)
                    line(37, 34 + motion, 42, 24 + motion, color(0xC6D2C4), 3); line(11, 39 - motion, 7, 47 - motion, color(0xC6D2C4), 3)
                    rect(19, 36, 13, 3, color(0xE0B875))
                }
                3, 4 -> { // Heavy rock sentinel / frost ogre.
                    val stone = if (id == 3) color(0xA58C6B) else color(0x6797AE)
                    val light = if (id == 3) color(0xD4BB8A) else color(0xB1D6DE)
                    val dark = if (id == 3) color(0x736953) else color(0x3D6B89)
                    box(11 - motion / 2, 41, 11, 12, dark); box(27 + motion / 2, 41, 11, 12, stone)
                    box(10, 26, 29, 21, stone); rect(15, 28, 20, 4, light); rect(28, 35, 9, 10, dark)
                    box(6, 28 - motion, 10, 17, dark); box(34, 27 + motion, 10, 17, stone); rect(35, 29 + motion, 7, 5, light)
                    box(15, 12, 23, 20, stone); rect(17, 14, 17, 5, light); rect(18, 22, 19, 4, dark); eyes(24, 22, blink, 9)
                    if (id == 3) { gem(25, 36, leaf, leafLight, 4); line(20, 15, 21, 20, dark); line(20, 34, 17, 39, dark, 2); rect(10, 27, 8, 3, leaf) }
                    else { poly(light, 16, 16, 10, 7, 13, 5, 21, 13); poly(light, 32, 13, 39, 6, 42, 8, 36, 18); rect(25, 29, 2, 4, cream); rect(33, 29, 2, 4, cream); box(7, 43, 8, 8, dark) }
                }
                5 -> { // Bramble boar, separate trot legs and twitching ears.
                    for (j in 0..3) box(11 + j * 7, 43, 5, 9 - (j % 2 * 2 - 1) * motion / 2, color(0x5F4E3E))
                    box(7, 29, 31, 19, color(0x96734F)); rect(10, 31, 22, 4, color(0xC59A65))
                    poly(leaf, 7, 30, 13, 18, 18, 25, 23, 18, 27, 25, 33, 20, 35, 33)
                    box(29, 31, 13, 15, color(0xBB9569)); box(37, 37, 9, 8, color(0xD4AF88)); rect(41, 39, 2, 3, ink)
                    eyes(33, 34, blink, 6); poly(color(0xD1A975), 30, 32, 30 - bob, 22, 36, 27); poly(cream, 38, 45, 35, 46, 34, 41, 36, 42)
                    line(8, 37, 3, 33 - bob, color(0x6E583F), 2)
                }
                6 -> { // Crystal snail: walking belly wave and revolving jewel pulse.
                    box(6, 43 + bob, 37, 9 - bob, color(0x789DA2)); rect(11, 45 + bob, 31, 3, color(0xB9D0BE))
                    box(10, 20, 25, 27, color(0x6B789E)); box(14, 22, 21, 21, color(0xA0ADD1)); box(17, 26, 15, 14, color(0x6975A0)); box(20, 29, 9, 8, color(0xBBBDDF))
                    gem(22, 20, color(0xA0D8DF), cream, 8); gem(11, 26, color(0x90B9CD), cream, 4)
                    line(36, 44, 37, 32 - bob, color(0xB9D0BE), 3); line(42, 44, 44, 30 + bob, color(0xB9D0BE), 3)
                    box(35, 30 - bob, 5, 5, cream); box(41, 28 + bob, 5, 5, cream); rect(38, 31 - bob, 1, 2, ink); rect(44, 29 + bob, 1, 2, ink)
                    if (pose == Pose.CAST) { glint(9 + f * 5, 15 - f, cream, 2); glint(30, 13 + f * 2, color(0xA0D8DF), 2) }
                }
                7 -> { // Fern drake: articulated wing, jaw, tail and two feet.
                    val green = color(0x509887); val light = color(0xA5CDA1); val dark = color(0x35656A)
                    poly(ink, 18, 41, 6, 43, 2, 35, 3, 30, 7, 37, 17, 34)
                    poly(green, 17, 40, 7, 41, 4, 35, 8, 38, 17, 36)
                    box(15 - motion / 2, 42, 8, 11, dark); box(28 + motion / 2, 42, 9, 11, dark)
                    box(14, 25, 24, 22, green); rect(25, 32, 9, 13, light)
                    val flap = intArrayOf(-3, 1, 5, 1)[f % 4]
                    poly(ink, 18, 32, 7, 18 + flap, 5, 8 + flap, 17, 13 + flap, 25, 26)
                    poly(light, 18, 30, 9, 18 + flap, 8, 11 + flap, 16, 16 + flap, 22, 26)
                    line(9, 13 + flap, 16, 27, dark, 2)
                    box(24, 16, 17, 17, green); box(34, 25, 12, if (pose == Pose.ATTACK || pose == Pose.CAST) 9 else 6, green)
                    rect(35, 28, 9, 2, dark); eyes(29, 21, blink, 7); rect(29, 23, 3, 1, color(0xE8CB7B))
                    poly(light, 24, 20, 22, 9, 28, 14); poly(light, 35, 18, 38, 9, 39, 21)
                    if (pose == Pose.CAST) { gem(42, 35, leafLight, cream, 3); glint(39, 42, cream, 2) }
                }
            }
            if (pose == Pose.HURT) { line(38, 12, 42, 8, cream, 2); line(39, 17, 45, 16, cream, 2) }
            c.restore()
        }
        private fun eyes(x: Int, y: Int, closed: Boolean, distance: Int) {
            rect(x, y, 3, if (closed) 1 else 4, ink); rect(x + distance, y, 2, if (closed) 1 else 3, ink)
            if (!closed) { rect(x + 1, y, 1, 1, cream); rect(x + distance, y, 1, 1, cream) }
        }
    }
}
