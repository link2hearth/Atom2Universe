package com.Atom2Universe.app.games.farm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Base64
import kotlin.math.*

/** Articulated native pixel art for all four livestock families and their young. */
object FarmAnimalArt {
    data class Pose(val time: Double = 0.0, val walk: Double = 0.0, val eat: Double = 0.0,
                    val phase: Double = 0.0, val direction: Int = 1)
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val cache = mutableMapOf<Int, List<Bitmap>>()
    private val tau = PI * 2
    private val supported = setOf("hen", "rooster", "cow", "bull", "chick", "calf",
        "ewe", "ram", "lamb", "sow", "boar", "piglet")
    private val cowEdges = intArrayOf(0xFF8B7776.toInt(), 0xFF967966.toInt(), 0xFF897875.toInt(), 0xFF80808C.toInt())
    private val cowBases = intArrayOf(0xFFF2E7D4.toInt(), 0xFFEAC39C.toInt(), 0xFFEBD9B9.toInt(), 0xFFEEE8E3.toInt())
    private fun ease(v: Double): Double = v.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
    private fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun supports(id: String) = id.substringBeforeLast('_') in supported

    fun draw(canvas: Canvas, id: String, target: RectF, pose: Pose = Pose()) {
        if (target.width() <= 0 || target.height() <= 0) return
        val name = id.substringBeforeLast('_')
        val bird = name == "hen" || name == "rooster" || name == "chick"
        val sheep = name == "ewe" || name == "ram" || name == "lamb"
        val pig = name == "sow" || name == "boar" || name == "piglet"
        val young = name in setOf("chick", "calf", "lamb", "piglet")
        val male = name in setOf("rooster", "bull", "ram", "boar")
        val variant = ((id.substringAfterLast('_').toIntOrNull() ?: 1) - 1).coerceIn(0, 3)
        val model = (when {
            sheep -> 24 + if (young) 8 else if (male) 4 else 0
            pig -> 36 + if (young) 8 else if (male) 4 else 0
            young -> if (bird) 16 else 20
            else -> (if (bird) 0 else 8) + (if (male) 4 else 0)
        }) + variant
        val parts = cache.getOrPut(model) {
            FarmAnimalPixels.models[model].map { recipe ->
                val bitmap = Bitmap.createBitmap(recipe.width * 3, recipe.height * 3, Bitmap.Config.ARGB_8888)
                val c = Canvas(bitmap); c.scale(3f, 3f)
                val bytes = Base64.decode(recipe.runs, Base64.NO_WRAP)
                for (i in bytes.indices step 4) {
                    val x = bytes[i].toInt() and 255; val y = bytes[i + 1].toInt() and 255
                    val width = bytes[i + 2].toInt() and 255
                    paint.color = FarmAnimalPixels.colors[bytes[i + 3].toInt() and 255]
                    c.drawRect(x.toFloat(), y.toFloat(), (x + width).toFloat(), y + 1f, paint)
                }
                bitmap
            }
        }
        val scale = min(target.width() / (if (bird) 92f else 120f), target.height() / (if (bird) 78f else 91f)) *
            (if (young) { if (bird) .66f else .74f } else 1f)
        canvas.save()
        canvas.translate(target.centerX(), target.bottom - 4 * scale)
        canvas.scale(scale, scale)
        paint.color = Color.argb(45, 55, 95, 61)
        canvas.drawOval(if (bird) RectF(-25f, -1f, 25f, 5f) else RectF(-48f, -1f, 48f, 7f), paint)
        canvas.scale(pose.direction.toFloat(), 1f)
        val draw = Painter(canvas, parts)
        if (bird) draw.bird(pose, male, young) else draw.quadruped(pose, male, variant, young, sheep, pig)
        canvas.restore()
    }

    private class Painter(val c: Canvas, val parts: List<Bitmap>) {
        private var opacity = 255
        fun part(index: Int, x: Double, y: Double, ox: Double, oy: Double, angle: Double = 0.0) {
            val b = parts[index]
            c.save(); c.translate(x.toFloat(), y.toFloat()); c.rotate(Math.toDegrees(angle).toFloat())
            paint.color = Color.WHITE
            paint.alpha = opacity
            c.drawBitmap(b, null, RectF(-ox.toFloat(), -oy.toFloat(), b.width / 3f - ox.toFloat(), b.height / 3f - oy.toFloat()), paint)
            c.restore()
        }
        fun rect(x: Double, y: Double, w: Double, h: Double, color: Int) {
            paint.color = color
            val xx = x.roundToInt().toFloat(); val yy = y.roundToInt().toFloat()
            c.drawRect(xx, yy, xx + max(1, w.roundToInt()), yy + max(1, h.roundToInt()), paint)
        }
        fun line(x: Double, y: Double, xx: Double, yy: Double, w: Double, color: Int) {
            val n = ceil(max(abs(xx - x), abs(yy - y))).toInt()
            for (i in 0..n) rect(mix(x, xx, i.toDouble() / max(1, n)), mix(y, yy, i.toDouble() / max(1, n)), w, w, color)
        }
        fun blink(t: Double, seed: Double) = (t + seed) % 5.7 in 5.45..5.6
        fun bird(p: Pose, male: Boolean, young: Boolean) {
            val t = p.time; val w = p.walk; val phase = p.phase
            val bob = sin(t * 2.4) * .22 + abs(sin(phase)) * .7 * w
            val peck = p.eat * (.65 + .35 * max(0.0, sin(t * 4.2)).pow(3))
            part(4, -1 + sin(phase + PI) * 3 * w, -13 - max(0.0, cos(phase + PI)) * 3 * w, 6.0, 0.0, sin(phase + PI) * .19 * w)
            part(2, -15.0, -28 + bob, if (male) 33.0 else 22.0, if (male) 36.0 else 25.0, -.09 + sin(t * 1.8) * .055 + peck * .08)
            part(0, 0.0, -27 + bob, 25.0, 21.0, peck * .09)
            part(4, 6 + sin(phase) * 3 * w, -12 - max(0.0, cos(phase)) * 3 * w, 6.0, 0.0, sin(phase) * .19 * w)
            val flutter = if (young) max(0.0, sin(t * 1.2)).pow(12) * sin(t * 12) * .09 else 0.0
            part(1, -3.0, -27 + bob, 14.0, 12.0, sin(t * 1.5) * .018 + peck * .06 + flutter)
            c.save(); c.translate((14 + peck * 4).toFloat(), (-31 + bob + peck * 14).toFloat())
            c.rotate(Math.toDegrees(peck * .92 + sin(phase) * .04 * w).toFloat())
            part(3, 0.0, 0.0, 14.0, if (male) 44.0 else 32.0)
            if (blink(t, .3)) line(8.0, -13.0, 11.0, -13.0, 1.0, 0xFF66585C.toInt())
            else { rect(8.0, -15.0, 3.0, 4.0, 0xFF64555C.toInt()); rect(8.0, -15.0, 1.0, 1.0, 0xFFFFF9E8.toInt()) }
            c.restore()
        }
        fun quadruped(p: Pose, male: Boolean, variant: Int, young: Boolean, sheep: Boolean, pig: Boolean) {
            val t = p.time; val w = p.walk; val e = p.eat; val phase = p.phase
            val bob = cos(phase * 2) * .55 * w + sin(t * 1.7) * .3
            fun leg(x: Double, phase: Double, far: Boolean) {
                val u = ((phase / tau) % 1 + 1) % 1
                val foot: Double; val lift: Double
                if (u < .62) { foot = mix(4.0, -4.0, u / .62); lift = 0.0 }
                else { val q = (u - .62) / .38; foot = mix(-4.0, 4.0, ease(q)); lift = sin(q * PI) * 4 }
                val ay = -25 + bob; val fx = x + foot * w; val fy = -lift * w
                val dx = fx - x; val dy = fy - ay; val dist = min(27.8, hypot(dx, dy))
                val angle = atan2(dy, dx); val offset = acos((dist / 28).coerceIn(-1.0, 1.0))
                val bend = ease(lift / 4) * ease(w)
                val kx = mix((x + fx) / 2, x + cos(angle - offset) * 14, bend)
                val ky = mix((ay + fy) / 2, ay + sin(angle - offset) * 14, bend)
                opacity = if (far) 224 else 255
                part(3, x, ay, 7.0, 2.0, atan2(ky - ay, kx - x) - PI / 2)
                part(4, kx, ky, 7.0, 2.0, atan2(fy - ky, fx - kx) - PI / 2)
                opacity = 255
            }
            leg(-19.0, phase + PI * .5, true); leg(18.0, phase + PI * 1.5, true)
            val tail = sin(t * 1.7) * 3 + max(0.0, sin(t * .8)).pow(9) * 5
            val tx = -42 - tail; val ty = -22 + sin(t * 1.7) * 1.4
            if (sheep || pig) {
                // Short woolly tail for sheep; a complete curled tail for pigs.
                part(5, -36.0, -44 + bob, 10.0, 3.0, sin(t * (if (pig) 2.5 else 1.6)) * .14)
            } else {
                line(-32.0, -43 + bob, -39.0, -36.0, 2.0, cowEdges[variant])
                line(-39.0, -36.0, tx, ty, 2.0, cowEdges[variant])
                line(-33.0, -42 + bob, -38.0, -36.0, 1.0, cowBases[variant])
                part(5, tx + 1, ty + 1, 7.0, 3.0, -tail * .04)
            }
            if (!male && !young && !sheep && !pig) part(6, -3.0, -22 + bob, 12.0, 0.0)
            part(0, -5.0, -43 + bob, 38.0, 28.0)
            leg(-23.0, phase, false); leg(15.0, phase + PI, false)
            c.save(); c.translate((29 + e * 3).toFloat(), (-44 + bob + e * 15).toFloat())
            c.rotate(Math.toDegrees(e * .5 + sin(t * 1.2) * .025 + (if (pig) e * sin(t * 3) * .04 else 0.0)).toFloat())
            val flick = max(0.0, sin(t * 1.13)).pow(16) * .19
            part(2, -9.0, -13.0, 18.0, 9.0, -.12 - flick)
            c.save(); c.translate(19f, -14f); c.scale(-1f, 1f)
            part(2, 0.0, 0.0, 18.0, 9.0, .12 + flick * .75); c.restore()
            part(1, 0.0, 0.0, 20.0, if (male && !sheep && !pig) 40.0 else 28.0)
            for (ex in listOf(-6.0, 10.0)) {
                if (blink(t, 2.1)) line(ex, -3.0, ex + 3, -3.0, 1.0, 0xFF61585F.toInt())
                else { rect(ex, -6.0, 3.0, 4.0, 0xFF61585F.toInt()); rect(ex, -6.0, 1.0, 1.0, 0xFFFFF9E8.toInt()) }
            }
            if (e > .4) {
                val chew = sin(t * 4) * .6
                line(2.0, 17 + chew, 8.0, 17 + chew, 1.0, 0xFFBE8D97.toInt())
                if (!pig) {
                    line(8.0, 17.0, 13.0, 20 + chew, 1.0, 0xFF71A271.toInt())
                    line(8.0, 17.0, 14.0, 16 + chew, 1.0, 0xFFA2BD79.toInt())
                }
            }
            c.restore()
        }
    }
}
