package com.Atom2Universe.app.games.hamlet

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pixel-art effects of attacks, skills and ultimates, at the fighters' own pixel size. Each effect is
 * a pure function of its age, so the battle view simply replays recent events without storing or
 * allocating anything. Every role has its own skill: a mage's orb, a ranger's arrow, an engineer's
 * bomb, a rogue's cross cut, a duelist's crescent, a guardian's shockwave, a bard's notes.
 */
internal class HamletBattleFx {
    private val p = Paint().apply { isAntiAlias = false }
    /** One art pixel on screen. */
    var px = 4f

    private fun fade(color: Int, alpha: Float) = ((alpha.coerceIn(0f, 1f) * (color ushr 24)).toInt() shl 24) or (color and 0xFFFFFF)
    private fun block(c: Canvas, x: Float, y: Float, n: Int, color: Int) {
        if (color ushr 24 == 0) return
        val gx = (floor(x / px) - n / 2) * px; val gy = (floor(y / px) - n / 2) * px
        p.color = color; c.drawRect(gx, gy, gx + n * px, gy + n * px, p)
    }
    private fun streak(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, n: Int, color: Int) {
        val steps = max(1, (max(abs(x1 - x0), abs(y1 - y0)) / px).toInt())
        for (i in 0..steps) { val k = i.toFloat() / steps; block(c, x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, n, color) }
    }
    private fun ring(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, n: Int, color: Int, dashed: Boolean = false, turn: Float = 0f) {
        val steps = max(12, ((rx + ry) * 1.6f / px).toInt())
        for (i in 0 until steps) {
            if (dashed && i % 3 == 2) continue
            val a = i * TAU / steps + turn
            block(c, x + cos(a) * rx, y + sin(a) * ry, n, color)
        }
    }
    private fun plus(c: Canvas, x: Float, y: Float, color: Int) {
        block(c, x, y, 1, color); block(c, x - px, y, 1, color); block(c, x + px, y, 1, color)
        block(c, x, y - px, 1, color); block(c, x, y + px, 1, color)
    }

    /** Sparks spiral up around a caster; an ultimate adds a pillar of light and a widening ground ring. */
    fun cast(c: Canvas, role: HeroRole, x: Float, foot: Float, size: Float, age: Float, ultimate: Boolean) {
        val life = if (ultimate) 1f else .6f
        if (age < 0 || age > life) return
        val k = age / life; val left = 1 - k
        val core = CORE[role.ordinal]; val glow = GLOW[role.ordinal]
        if (ultimate) {
            p.color = fade(glow, .3f * left); c.drawRect(x - size * .24f, 0f, x + size * .24f, foot, p)
            p.color = fade(core, .45f * left); c.drawRect(x - size * .07f, 0f, x + size * .07f, foot, p)
            ring(c, x, foot, size * (.3f + k * .9f), size * (.09f + k * .27f), 2, fade(glow, left))
        }
        val n = if (ultimate) 14 else 9
        for (i in 0 until n) {
            val a = i * TAU / n + age * 3
            val r = size * (.28f + .08f * sin(i * 1.7f))
            val rise = k * size * (.5f + .3f * ((i * 37) % 10) / 10f)
            block(c, x + cos(a) * r, foot - size * .08f - rise - sin(a) * r * .25f, if (i % 3 == 0) 2 else 1, fade(if (i % 2 == 0) core else glow, left))
        }
    }

    /** A skill leaving the caster's hand ([sx], [sy]) and landing on the target's chest ([tx], [ty]). */
    fun skill(c: Canvas, role: HeroRole, sx: Float, sy: Float, tx: Float, ty: Float, size: Float, age: Float, ultimate: Boolean) {
        val travel = travel(role)
        if (age < 0) return
        if (age < travel) { projectile(c, role, sx, sy, tx, ty, size, age / travel, ultimate); return }
        val t = age - travel
        val big = if (ultimate) 1.45f else 1f
        val core = CORE[role.ordinal]; val glow = GLOW[role.ordinal]
        when (role) {
            HeroRole.MAGE -> { burst(c, tx, ty, size * .5f * big, t, .45f, core, glow); if (ultimate) bolt(c, tx, ty, size, t) }
            HeroRole.RANGER -> burst(c, tx, ty, size * .26f * big, t, .25f, core, glow)
            HeroRole.ENGINEER -> explosion(c, tx, ty, size * .5f * big, t)
            HeroRole.ROGUE -> { cross(c, tx, ty, size * .3f * big, t, core, glow); drips(c, tx, ty, size, t, glow) }
            HeroRole.DUELIST -> {
                crescent(c, tx, ty, size * .38f * big, t, core, glow, sx > tx)
                if (ultimate) crescent(c, tx, ty - size * .12f, size * .3f * big, t - .1f, core, glow, sx <= tx)
            }
            HeroRole.GUARDIAN -> { shockwave(c, tx, ty + size * .42f, size * .55f * big, t, glow); burst(c, tx, ty, size * .24f, t, .3f, core, glow) }
            else -> burst(c, tx, ty, size * .32f * big, t, .4f, core, glow)
        }
    }

    private fun projectile(c: Canvas, role: HeroRole, sx: Float, sy: Float, tx: Float, ty: Float, size: Float, k: Float, ultimate: Boolean) {
        val core = CORE[role.ordinal]; val glow = GLOW[role.ordinal]
        val x = sx + (tx - sx) * k; var y = sy + (ty - sy) * k
        val n = if (ultimate) 2 else 1
        when (role) {
            HeroRole.RANGER -> {
                val d = max(1f, sqrt((tx - sx) * (tx - sx) + (ty - sy) * (ty - sy)))
                val ux = (tx - sx) / d; val uy = (ty - sy) / d
                streak(c, x - ux * 6 * px, y - uy * 6 * px, x, y, 1, 0xFFB08A5A.toInt())
                block(c, x + ux * px, y + uy * px, n + 1, core)
                block(c, x - ux * 6 * px, y - uy * 6 * px - px, 1, glow); block(c, x - ux * 6 * px, y - uy * 6 * px + px, 1, glow)
            }
            HeroRole.ENGINEER -> {
                y -= sin(k * Math.PI.toFloat()) * size * .6f
                block(c, x, y, 2 + n - 1, 0xFF34343E.toInt()); block(c, x - px, y - px, 1, 0xFF6A6A78.toInt())
                if (((k * 20).toInt() and 1) == 0) block(c, x + px, y - 2 * px, 1, 0xFFFFE07A.toInt())
            }
            HeroRole.BARD -> for (j in 0..1) {
                val nx = x - j * 4 * px; val ny = y + sin(k * 9 + j * 2) * 2 * px
                block(c, nx, ny, 2, glow); streak(c, nx + px, ny, nx + px, ny - 4 * px, 1, glow); block(c, nx + 2 * px, ny - 4 * px, 1, glow)
            }
            HeroRole.SUMMONER -> for (j in 0..2) {
                val a = k * 14 + j * 2.1f
                block(c, x + cos(a) * 3 * px, y + sin(a) * 3 * px, 1, if (j == 0) core else glow)
            }
            else -> {
                for (j in 1..3) block(c, x - (tx - sx) * .05f * j, y - (ty - sy) * .05f * j, 1, fade(glow, 1 - j * .28f))
                block(c, x, y, 2 + n, fade(glow, .6f)); block(c, x, y, 1 + n, core)
            }
        }
    }

    /** Rays and a ring bursting from the impact. */
    private fun burst(c: Canvas, x: Float, y: Float, r: Float, t: Float, life: Float, core: Int, glow: Int) {
        if (t < 0 || t > life) return
        val k = t / life; val left = 1 - k
        if (k < .25f) block(c, x, y, 4, fade(core, 1 - k * 4))
        for (i in 0 until 8) {
            val a = i * TAU / 8 + .3f
            val d = r * (.25f + k)
            block(c, x + cos(a) * d, y + sin(a) * d, 2, fade(glow, left))
            block(c, x + cos(a) * d * .6f, y + sin(a) * d * .6f, 1, fade(core, left))
        }
        if (k > .2f) ring(c, x, y, r * k * 1.1f, r * k * .8f, 1, fade(glow, left * .8f))
    }
    /** The sky splits on the target: a zigzag bolt from the top of the field. */
    private fun bolt(c: Canvas, x: Float, y: Float, size: Float, t: Float) {
        if (t > .3f) return
        val left = 1 - t / .3f
        var lx = x; var ly = 0f
        val steps = 7
        for (i in 1..steps) {
            val nx = x + if (i == steps) 0f else ((i * 73) % 7 - 3) * size * .05f
            val ny = y * i / steps
            streak(c, lx, ly, nx, ny, 2, fade(0xFFB89CFF.toInt(), left))
            streak(c, lx, ly, nx, ny, 1, fade(0xFFFFFFFF.toInt(), left))
            lx = nx; ly = ny
        }
    }
    private fun explosion(c: Canvas, x: Float, y: Float, r: Float, t: Float) {
        if (t < 0 || t > .7f) return
        val k = (t / .35f).coerceAtMost(1f)
        if (t < .35f) {
            val left = 1 - k
            for (layer in 0..2) {
                val rr = r * k * (.4f + layer * .3f)
                val col = when (layer) { 0 -> 0xFFFFF4B0.toInt(); 1 -> 0xFFFFB03A.toInt(); else -> 0xFFE0502A.toInt() }
                ring(c, x, y, rr, rr * .8f, 2, fade(col, left + .2f))
            }
        }
        val smoke = ((t - .1f) / .6f).coerceIn(0f, 1f)
        if (smoke > 0) for (i in 0 until 6) {
            val a = i * TAU / 6
            block(c, x + cos(a) * r * .5f, y + sin(a) * r * .3f - smoke * r, 2, fade(0xFF7A7480.toInt(), (1 - smoke) * .8f))
        }
        if (t < .5f) for (i in 0..2) {
            val a = i * 2.2f + t * 6
            block(c, x + cos(a) * r * (.4f + t), y + sin(a) * r * (.3f + t) - t * r * .4f, 1, 0xFFC8B48A.toInt())
        }
    }
    private fun cross(c: Canvas, x: Float, y: Float, r: Float, t: Float, core: Int, glow: Int) {
        if (t < 0 || t > .35f) return
        val draw = (t / .1f).coerceAtMost(1f); val left = 1 - (t - .1f).coerceAtLeast(0f) / .25f
        streak(c, x - r, y - r, x - r + 2 * r * draw, y - r + 2 * r * draw, 2, fade(glow, left))
        streak(c, x - r, y - r, x - r + 2 * r * draw, y - r + 2 * r * draw, 1, fade(core, left))
        if (t > .05f) {
            val d2 = ((t - .05f) / .1f).coerceAtMost(1f)
            streak(c, x + r, y - r, x + r - 2 * r * d2, y - r + 2 * r * d2, 2, fade(glow, left))
            streak(c, x + r, y - r, x + r - 2 * r * d2, y - r + 2 * r * d2, 1, fade(core, left))
        }
    }
    private fun drips(c: Canvas, x: Float, y: Float, size: Float, t: Float, glow: Int) {
        if (t < .1f || t > .8f) return
        val k = (t - .1f) / .7f
        for (i in 0..2) block(c, x + (i - 1) * size * .14f, y + k * size * .45f + i % 2 * size * .08f, 1, fade(glow, 1 - k))
    }
    private fun crescent(c: Canvas, x: Float, y: Float, r: Float, t: Float, core: Int, glow: Int, flip: Boolean) {
        if (t < 0 || t > .32f) return
        val sweep = (t / .1f).coerceAtMost(1f); val left = 1 - (t - .1f).coerceAtLeast(0f) / .22f
        val steps = 14
        for (i in 0..(steps * sweep).toInt()) {
            val a = -1.1f + 2.2f * i / steps
            val cx = x + (if (flip) -1 else 1) * cos(a) * r * .5f; val cy = y + sin(a) * r
            block(c, cx, cy, 2, fade(glow, left)); block(c, cx - (if (flip) -1 else 1) * px, cy, 1, fade(core, left))
        }
    }
    private fun shockwave(c: Canvas, x: Float, foot: Float, r: Float, t: Float, glow: Int) {
        if (t < 0 || t > .45f) return
        val k = t / .45f
        ring(c, x, foot, r * (.3f + k), r * (.1f + k * .3f), 2, fade(glow, 1 - k))
        for (i in 0 until 6) block(c, x + (i - 2.5f) * r * .35f, foot - k * r * .5f - (i % 2) * px * 2, 1, fade(0xFFC8B48A.toInt(), 1 - k))
    }

    /** An ordinary attack: a quick slash for close fighters, a small bolt for the others. */
    fun attack(c: Canvas, role: HeroRole, sx: Float, sy: Float, tx: Float, ty: Float, size: Float, age: Float) {
        val travel = travel(role) * .7f
        val glow = GLOW[role.ordinal]
        if (travel > 0 && age < travel) {
            val k = age / travel
            block(c, sx + (tx - sx) * k, sy + (ty - sy) * k, 2, fade(glow, .7f))
            block(c, sx + (tx - sx) * k, sy + (ty - sy) * k, 1, 0xFFFFFFFF.toInt())
            return
        }
        val t = age - travel
        if (t > .16f) return
        val left = 1 - t / .16f
        if (travel == 0f) {
            val r = size * .22f; val dir = if (sx > tx) -1 else 1
            streak(c, tx - r * dir, ty - r, tx + r * dir, ty + r * .6f, 1, fade(0xFFFFFFFF.toInt(), left))
            streak(c, tx - r * dir, ty - r + px * 2, tx + r * dir, ty + r * .6f + px * 2, 1, fade(glow, left * .8f))
        } else for (i in 0 until 4) {
            val a = i * TAU / 4 + .8f; val d = size * .1f * (.5f + t / .16f)
            block(c, tx + cos(a) * d, ty + sin(a) * d, 1, fade(0xFFFFFFFF.toInt(), left))
        }
    }

    /** Green crosses rising from a healed fighter. */
    fun heal(c: Canvas, x: Float, y: Float, size: Float, age: Float) {
        if (age < 0 || age > .9f) return
        val k = age / .9f
        for (i in 0..3) {
            val hx = x + (i - 1.5f) * size * .17f + sin(age * 6 + i) * px
            val hy = y + size * .15f - k * size * .55f - (i % 2) * size * .1f
            plus(c, hx, hy, fade(if (i % 2 == 0) 0xFF8CF29E.toInt() else 0xFFD8FFD0.toInt(), 1 - k))
        }
    }
    /** A bubble closing around a protected fighter. */
    fun shield(c: Canvas, x: Float, y: Float, size: Float, age: Float) {
        if (age < 0 || age > .6f) return
        val k = age / .6f
        ring(c, x, y, size * .34f * (1.25f - k * .25f), size * .46f * (1.25f - k * .25f), 1, fade(0xFF9AE8FF.toInt(), 1 - k))
        for (i in 0..3) block(c, x + cos(i * 1.57f + k * 3) * size * .3f, y + sin(i * 1.57f + k * 3) * size * .42f, 1, fade(0xFFFFFFFF.toInt(), 1 - k))
    }
    /** The bubble that stays while a shield holds. */
    fun guard(c: Canvas, x: Float, y: Float, size: Float, time: Float) {
        ring(c, x, y, size * .34f, size * .46f, 1, 0x557FDCF5, dashed = true, turn = time * .6f)
    }
    /** A rune circle opening where a summon appears. */
    fun summon(c: Canvas, x: Float, foot: Float, size: Float, age: Float) {
        if (age < 0 || age > 1f) return
        val left = 1 - age
        ring(c, x, foot, size * .42f, size * .13f, 1, fade(0xFFC88CFF.toInt(), left), dashed = true, turn = age * 4)
        ring(c, x, foot, size * .3f, size * .09f, 1, fade(0xFFF2D8FF.toInt(), left), turn = -age * 4)
        p.color = fade(0xFFB061F0.toInt(), .35f * left); c.drawRect(x - size * .2f, foot - size * (.4f + age), x + size * .2f, foot, p)
    }

    companion object {
        private const val TAU = 6.2831855f
        // GUARDIAN, RANGER, ROGUE, HEALER, BARD, MAGE, DUELIST, SUMMONER, ENGINEER
        private val TRAVEL = floatArrayOf(0f, .16f, 0f, .2f, .3f, .24f, 0f, .22f, .32f)
        private val CORE = intArrayOf(0xFFF1B8, 0xFFFFFF, 0xD8FF9C, 0xDDFFD8, 0xFFE1F2, 0xE6DCFF, 0xFFFFFF, 0xF2D8FF, 0xFFF0B0).opaque()
        private val GLOW = intArrayOf(0xF2C24E, 0xE2C98A, 0x6FD24A, 0x62D98A, 0xFF7FC8, 0x8C6CFF, 0xFF5A4A, 0xB061F0, 0xFF8A2E).opaque()
        private fun IntArray.opaque() = also { for (i in indices) it[i] = it[i] or 0xFF000000.toInt() }
        /** Seconds a skill flies before it lands; close fighters strike at once. */
        fun travel(role: HeroRole) = TRAVEL[role.ordinal]
        /** The role's light, for labels and banners. */
        fun glow(role: HeroRole) = GLOW[role.ordinal]
    }
}
