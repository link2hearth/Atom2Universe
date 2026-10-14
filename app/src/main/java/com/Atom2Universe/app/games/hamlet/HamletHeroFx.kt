package com.Atom2Universe.app.games.hamlet

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Rarity colours and the live effects around heroes: the usual collection-game ladder (grey, blue,
 * purple, gold, rose). Each tier keeps the previous effects and adds its own, so a legendary hero
 * always shines more than a rare one. Drawn on the character grid so effects keep the pixel style.
 */
internal object HamletHeroFx {
    private val MAIN = intArrayOf(0x9AA39B, 0x3F95EA, 0xAE63EC, 0xF2B033, 0xFF4C78)
    private val LIGHT = intArrayOf(0xDDE2D8, 0xAFDBFF, 0xE5C6FF, 0xFFE9A0, 0xFFC1D2)
    private val DEEP = intArrayOf(0x5C6660, 0x1D4F8E, 0x55288A, 0x92590C, 0x8A163E)
    private const val OPAQUE = 0xFF000000.toInt()
    fun main(r: HeroRarity) = MAIN[r.ordinal] or OPAQUE
    fun light(r: HeroRarity) = LIGHT[r.ordinal] or OPAQUE
    fun deep(r: HeroRarity) = DEEP[r.ordinal] or OPAQUE

    /** Halo thickness on the sprite grid; common heroes have none. */
    fun haloRadius(r: HeroRarity) = intArrayOf(0, 1, 2, 3, 3)[r.ordinal]

    private val hsv = FloatArray(3)
    /** Mythic heroes shimmer around rose, from violet to coral; the others keep their colour. */
    fun aura(r: HeroRarity, t: Float): Int {
        if (r != HeroRarity.MYTHIC) return main(r)
        hsv[0] = (345f + 50f * sin(t * 1.3f) + 360f) % 360f; hsv[1] = .6f; hsv[2] = 1f
        return Color.HSVToColor(hsv)
    }

    /** Halo strength: steady for rare, then a breathing pulse that grows with rarity. */
    fun haloAlpha(r: HeroRarity, t: Float): Float {
        val pulse = .5f + .5f * sin(t * (1.8f + r.ordinal * .4f))
        return when (r) {
            HeroRarity.COMMON -> 0f
            HeroRarity.RARE -> .5f
            HeroRarity.EPIC -> .5f + .3f * pulse
            HeroRarity.LEGENDARY -> .55f + .4f * pulse
            HeroRarity.MYTHIC -> .6f + .4f * pulse
        }
    }

    private val paint = Paint().apply { isAntiAlias = false }
    private val spot = RectF()
    private var ox = 0f
    private var oy = 0f
    private var s = 1f
    private var dir = 1f
    private var fade = 1f
    private val MOTE_X = floatArrayOf(9f, 39f, 15f, 34f, 5f, 43f, 21f, 28f)
    private val SPARKS = intArrayOf(5, 14, 43, 8, 45, 35, 3, 37)

    private fun place(x: Float, foot: Float, size: Float, mirrored: Boolean, alpha: Int) {
        s = size / 56f; ox = x; oy = foot - size; dir = if (mirrored) -1f else 1f; fade = alpha / 255f
    }
    private fun tone(color: Int, a: Float) { paint.color = (((a * fade).coerceIn(0f, 1f) * 255).toInt() shl 24) or (color and 0xFFFFFF) }
    private fun dot(c: Canvas, gx: Float, gy: Float, w: Int = 1, h: Int = 1) {
        val px = gx.roundToInt(); val py = gy.roundToInt()
        val a = ox + (px - 24f) * s * dir; val b = ox + (px + w - 24f) * s * dir
        spot.set(min(a, b), oy + py * s, max(a, b), oy + (py + h) * s)
        c.drawRect(spot, paint)
    }
    private fun frac(v: Float) = v - floor(v)

    /** Behind the hero: rune circle at the feet and the far half of the mythic orbit. */
    fun under(c: Canvas, r: HeroRarity, x: Float, foot: Float, size: Float, t: Float, mirrored: Boolean, alpha: Int) {
        if (r.ordinal < 3) return
        place(x, foot, size, mirrored, alpha)
        val n = if (r == HeroRarity.MYTHIC) 22 else 16
        for (k in 0 until n) {
            val a = k * 2 * PI.toFloat() / n + t * .6f
            val front = sin(a)
            tone(aura(r, t + k * .2f), .45f + .35f * front)
            dot(c, 24f + cos(a) * 17f, 54f + front * 3f, if (k % 4 == 0) 2 else 1)
        }
        if (r == HeroRarity.MYTHIC) orbit(c, r, t, back = true)
    }

    /** In front of the hero: rising motes, twinkles and the near half of the mythic orbit. */
    fun over(c: Canvas, r: HeroRarity, x: Float, foot: Float, size: Float, t: Float, mirrored: Boolean, alpha: Int) {
        val tier = r.ordinal
        if (tier < 2) return
        place(x, foot, size, mirrored, alpha)
        val motes = intArrayOf(0, 0, 4, 6, 8)[tier]
        for (k in 0 until motes) {
            val phase = frac(t / (2.6f + (k % 3) * .45f) + k * .618f)
            tone(if (r == HeroRarity.MYTHIC) aura(r, t + k) else light(r), sin(phase * PI.toFloat()))
            dot(c, MOTE_X[k] + sin(t * 1.7f + k * 2f) * 1.3f, 51f - phase * 44f)
        }
        if (tier >= 3) for (k in 0 until if (r == HeroRarity.MYTHIC) 4 else 3) {
            val twinkle = frac(t * .7f + k * .29f)
            if (twinkle > .45f) continue
            val arm = if (twinkle < .1f || twinkle > .35f) 1 else 2
            val gx = SPARKS[k * 2].toFloat(); val gy = SPARKS[k * 2 + 1].toFloat()
            tone(light(r), 1f)
            dot(c, gx - arm, gy, arm * 2 + 1); dot(c, gx, gy - arm, 1, arm * 2 + 1)
            tone(0xFFFFFF, 1f); dot(c, gx, gy)
        }
        if (r == HeroRarity.MYTHIC) orbit(c, r, t, back = false)
    }

    /** Bosses: a crimson halo, a jagged ring on the ground and rising embers. */
    const val MENACE = 0xFFE0402E.toInt()
    private const val EMBER = 0xFF8A4A
    fun menaceAlpha(t: Float) = .55f + .35f * (.5f + .5f * sin(t * 2.6f))

    fun menaceUnder(c: Canvas, x: Float, foot: Float, size: Float, t: Float, mirrored: Boolean, alpha: Int) {
        place(x, foot, size, mirrored, alpha)
        for (k in 0 until 20) {
            val a = k * 2 * PI.toFloat() / 20 - t * .4f
            val front = sin(a)
            tone(MENACE, .4f + .35f * front)
            dot(c, 24f + cos(a) * 21f, 54f + front * 3.5f, if (k % 2 == 0) 2 else 1, if (k % 5 == 0) 2 else 1)
        }
    }

    fun menaceOver(c: Canvas, x: Float, foot: Float, size: Float, t: Float, mirrored: Boolean, alpha: Int) {
        place(x, foot, size, mirrored, alpha)
        for (k in 0 until 6) {
            val phase = frac(t / (2.2f + (k % 3) * .5f) + k * .41f)
            tone(if (k % 2 == 0) EMBER else MENACE, sin(phase * PI.toFloat()))
            dot(c, MOTE_X[k] + sin(t * 2.1f + k) * 1.5f, 50f - phase * 50f)
        }
    }

    private fun orbit(c: Canvas, r: HeroRarity, t: Float, back: Boolean) {
        for (k in 0 until 4) {
            val a = t * 1.4f + k * PI.toFloat() / 2
            val depth = sin(a)
            if ((depth < 0) != back) continue
            val gx = 24f + cos(a) * 21f; val gy = 31f + depth * 5f
            tone(aura(r, t + k * .7f), if (back) .7f else 1f)
            dot(c, gx - 1, gy, 3); dot(c, gx, gy - 1, 1, 3)
            tone(0xFFFFFF, if (back) .7f else 1f); dot(c, gx, gy)
        }
    }
}

/** Card background behind a hero portrait: rarity gradient, light rays from legendary, rank pips. */
internal class HeroBackdrop(private val rarity: HeroRarity, private val density: Float) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rays = Path()
    private val clip = Path()
    private val area = RectF()
    private var shader: Shader? = null
    private var width = 0f
    private var height = 0f

    fun draw(c: Canvas, w: Float, h: Float, time: Float) {
        val tier = rarity.ordinal
        val radius = 12f * density
        if (w != width || h != height) {
            width = w; height = h
            shader = LinearGradient(0f, 0f, 0f, h, TOP[tier] or OPAQUE, BOTTOM[tier] or OPAQUE, Shader.TileMode.CLAMP)
            area.set(0f, 0f, w, h); clip.reset(); clip.addRoundRect(area, radius, radius, Path.Direction.CW)
        }
        fill.shader = shader; c.drawRoundRect(area, radius, radius, fill); fill.shader = null
        if (tier >= 3) {
            c.save(); c.clipPath(clip)
            val cx = w / 2; val cy = h * .58f; val reach = w + h
            rays.reset()
            for (k in 0 until 12) {
                val a = time * .25f + k * PI.toFloat() / 6
                rays.moveTo(cx, cy)
                rays.lineTo(cx + cos(a) * reach, cy + sin(a) * reach)
                rays.lineTo(cx + cos(a + .2f) * reach, cy + sin(a + .2f) * reach)
                rays.close()
            }
            fill.color = 0x40FFFFFF; c.drawPath(rays, fill)
            c.restore()
        }
        if (tier >= 2) for (k in 0 until 4) {
            val twinkle = .5f + .5f * sin(time * 2.2f + k * 1.7f)
            fill.color = (((90 + 140 * twinkle).toInt()) shl 24) or 0xFFFFFF
            val sx = w * STARS[k * 2]; val sy = h * STARS[k * 2 + 1]; val arm = (2f + 2f * twinkle) * density
            c.drawRect(sx - arm, sy - density * .5f, sx + arm, sy + density * .5f, fill)
            c.drawRect(sx - density * .5f, sy - arm, sx + density * .5f, sy + arm, fill)
        }
        val stroke = (1.5f + tier * .6f) * density
        edge.strokeWidth = stroke; edge.color = HamletHeroFx.deep(rarity)
        c.drawRoundRect(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2, radius, radius, edge)
        if (tier >= 3) {
            edge.strokeWidth = density; edge.color = HamletHeroFx.light(rarity)
            c.drawRoundRect(stroke + density, stroke + density, w - stroke - density, h - stroke - density, radius * .8f, radius * .8f, edge)
        }
        // Rank pips: one per rarity step, readable without any text.
        val pip = 3.5f * density
        for (k in 0..tier) {
            val px = 10f * density + k * 9f * density; val py = 10f * density
            rays.reset(); rays.moveTo(px, py - pip); rays.lineTo(px + pip, py); rays.lineTo(px, py + pip); rays.lineTo(px - pip, py); rays.close()
            fill.color = HamletHeroFx.main(rarity); c.drawPath(rays, fill)
            edge.strokeWidth = density; edge.color = HamletHeroFx.deep(rarity); c.drawPath(rays, edge)
        }
    }

    private companion object {
        const val OPAQUE = 0xFF000000.toInt()
        val TOP = intArrayOf(0xEEEFE8, 0xDDEEFF, 0xF0E2FF, 0xFFF0C0, 0xFFE2EA)
        val BOTTOM = intArrayOf(0xC5CABF, 0x86B8EA, 0xB287DE, 0xD48420, 0xD94A78)
        val STARS = floatArrayOf(.16f, .3f, .86f, .22f, .82f, .62f, .14f, .7f)
    }
}
