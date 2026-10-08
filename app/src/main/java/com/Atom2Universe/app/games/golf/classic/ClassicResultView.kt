package com.Atom2Universe.app.games.golf.classic

import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.Atom2Universe.app.games.golf.GolfUi
import kotlin.math.*
import kotlin.random.Random

/**
 * The end of a hole: a short animation matched to the score instead of a dialog.
 * Par stays calm, a birdie sparkles, an eagle throws confetti, a hole in one is a full fireworks show.
 */
internal class ClassicResultView(context: Context, private val ui: GolfUi, private val kind: Kind, private val title: String, buttons: List<View>) : FrameLayout(context) {
    enum class Kind { ACE, EAGLE, BIRDIE, PAR, BOGEY, WORSE;
        companion object {
            fun of(strokes: Int, par: Int): Kind {
                val diff = strokes - par
                return when { strokes == 1 -> ACE; diff <= -2 -> EAGLE; diff == -1 -> BIRDIE; diff == 0 -> PAR; diff == 1 -> BOGEY; else -> WORSE }
            }
        }
    }

    private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val color: Int, val size: Float, val confetti: Boolean, var rot: Float, val spin: Float)

    init {
        isClickable = true; isFocusable = true
        addView(Scene(context), LayoutParams(-1, -1))
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = INVISIBLE; alpha = 0f }
        buttons.forEachIndexed { i, b -> column.addView(b, LinearLayout.LayoutParams(ui.dp(240), -2).apply { if (i > 0) topMargin = ui.dp(10) }) }
        addView(column, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = ui.dp(48) })
        column.animate().alpha(1f).setStartDelay(if (kind == Kind.ACE) 1800L else 900L).setDuration(400).withStartAction { column.visibility = VISIBLE }.start()
    }

    private inner class Scene(context: Context) : View(context) {
        private val start = System.nanoTime()
        private var last = start
        private val random = Random(start)
        private val sparks = ArrayList<Spark>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ray = Path()
        private var rays: RadialGradient? = null
        private var nextBurst = 0.35f
        private var rain = 0f
        private var opened = false
        private val palette = intArrayOf(0xFFFFD778.toInt(), 0xFFFF7E62.toInt(), 0xFFFFFFFF.toInt(), 0xFF8BE8FF.toInt(), 0xFFB59BFF.toInt(), 0xFF8BE8A0.toInt())

        private val fill = when (kind) {
            Kind.ACE -> 0xFFFFD778; Kind.EAGLE -> 0xFFFFC94D; Kind.BIRDIE -> 0xFF8BE8A0
            Kind.PAR -> 0xFFFFFFFF; Kind.BOGEY -> 0xFFFFB067; Kind.WORSE -> 0xFFFF7E7E
        }.toInt()
        private val titleSize = ui.dpf(when (kind) { Kind.ACE -> 54f; Kind.EAGLE -> 46f; Kind.BIRDIE -> 44f; else -> 38f })
        private val dimAlpha = when (kind) { Kind.ACE -> 90; Kind.EAGLE -> 80; Kind.BIRDIE -> 70; Kind.PAR -> 60; Kind.BOGEY -> 90; Kind.WORSE -> 120 }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val r = max(w, h) * .6f
            rays = RadialGradient(0f, 0f, r, if (kind == Kind.BIRDIE) 0x668BE8A0 else 0x99FFD778.toInt(), 0x00FFD778, Shader.TileMode.CLAMP)
        }

        private fun burst(x: Float, y: Float, count: Int, confetti: Boolean, speed: ClosedFloatingPointRange<Float>, upward: Boolean) {
            repeat(count) {
                val angle = if (upward) -PI.toFloat() * random.nextFloat() else (random.nextFloat() * 2 * PI).toFloat()
                val v = ui.dpf(random.nextFloat() * (speed.endInclusive - speed.start) + speed.start)
                val life = if (confetti) 1.8f + random.nextFloat() else .9f + random.nextFloat() * .5f
                sparks += Spark(x, y, cos(angle) * v, sin(angle) * v, life, life, palette[random.nextInt(palette.size)],
                    ui.dpf(if (confetti) 9f else 3f) * (.7f + random.nextFloat() * .6f), confetti, random.nextFloat() * 6f, (random.nextFloat() - .5f) * 14f)
            }
        }

        private fun spawn(t: Float, dt: Float) {
            val w = width.toFloat(); val h = height.toFloat()
            when (kind) {
                Kind.ACE -> {
                    if (!opened && t >= .15f) { opened = true; burst(w / 2, h * .34f, 90, true, 140f..420f, false) }
                    if (t >= nextBurst) {
                        nextBurst = t + .35f + random.nextFloat() * .3f
                        burst(w * (.15f + random.nextFloat() * .7f), h * (.1f + random.nextFloat() * .35f), 36, false, 90f..260f, false)
                    }
                    rain += dt * 40f
                }
                Kind.EAGLE -> {
                    if (!opened && t >= .2f) { opened = true; burst(w / 2, h * .34f, 70, true, 160f..420f, true) }
                    if (t < 2.5f) rain += dt * 18f
                }
                Kind.BIRDIE -> if (!opened && t >= .2f) { opened = true; burst(w / 2, h * .34f, 26, false, 80f..210f, false) }
                else -> {}
            }
            while (rain >= 1f) {
                rain -= 1f
                sparks += Spark(random.nextFloat() * w, -ui.dpf(10f), 0f, ui.dpf(110f + random.nextFloat() * 110f), 4f, 4f,
                    palette[random.nextInt(palette.size)], ui.dpf(9f) * (.7f + random.nextFloat() * .6f), true, random.nextFloat() * 6f, (random.nextFloat() - .5f) * 14f)
            }
        }

        private fun step(dt: Float) {
            val it = sparks.iterator()
            while (it.hasNext()) {
                val s = it.next()
                s.life -= dt
                if (s.life <= 0f || s.y > height + ui.dpf(30f)) { it.remove(); continue }
                if (s.confetti) { s.vy += ui.dpf(260f) * dt; s.vx *= 1f - 1.3f * dt; s.vy *= 1f - .5f * dt } else s.vy += ui.dpf(220f) * dt
                s.x += s.vx * dt; s.y += s.vy * dt; s.rot += s.spin * dt
            }
        }

        private fun easeBack(p: Float): Float { val q = p - 1f; return 1f + 2.70158f * q * q * q + 1.70158f * q * q }
        private fun bounce(p: Float): Float = when {
            p < 1 / 2.75f -> 7.5625f * p * p
            p < 2 / 2.75f -> { val q = p - 1.5f / 2.75f; 7.5625f * q * q + .75f }
            p < 2.5f / 2.75f -> { val q = p - 2.25f / 2.75f; 7.5625f * q * q + .9375f }
            else -> { val q = p - 2.625f / 2.75f; 7.5625f * q * q + .984375f }
        }

        override fun onDraw(c: Canvas) {
            val now = System.nanoTime()
            val t = (now - start) / 1e9f
            val dt = ((now - last) / 1e9f).coerceIn(0f, .05f); last = now
            spawn(t, dt); step(dt)
            val w = width.toFloat(); val h = height.toFloat(); val cx = w / 2; val cy = h * .34f
            val appear = (t / .5f).coerceIn(0f, 1f)
            c.drawColor(Color.argb((dimAlpha * min(t / .3f, 1f)).toInt(), 0, 0, 0))

            if (kind == Kind.ACE || kind == Kind.EAGLE || kind == Kind.BIRDIE) {
                c.save(); c.translate(cx, cy)
                paint.style = Paint.Style.FILL; paint.shader = rays; paint.alpha = (255 * appear * (if (kind == Kind.EAGLE) .7f else 1f)).toInt()
                if (kind == Kind.BIRDIE) {
                    c.drawCircle(0f, 0f, min(w, h) * (.45f + .03f * sin(t * 4f)), paint)
                } else {
                    val spin = t * .45f; val half = (PI / 24).toFloat(); val r = max(w, h)
                    ray.reset()
                    for (i in 0 until 12) {
                        val a = spin + i * (2 * PI / 12).toFloat()
                        ray.moveTo(0f, 0f); ray.lineTo(cos(a - half) * r, sin(a - half) * r); ray.lineTo(cos(a + half) * r, sin(a + half) * r); ray.close()
                    }
                    c.drawPath(ray, paint)
                }
                paint.shader = null; c.restore()
            }

            for (s in sparks) {
                val fade = (s.life / s.max).coerceIn(0f, 1f).pow(.6f)
                paint.color = s.color; paint.alpha = (255 * fade).toInt()
                if (s.confetti) {
                    c.save(); c.translate(s.x, s.y); c.rotate(Math.toDegrees(s.rot.toDouble()).toFloat())
                    val flutter = abs(cos(s.rot * 1.7f)) * .85f + .15f
                    paint.style = Paint.Style.FILL; c.drawRect(-s.size / 2, -s.size * .3f * flutter, s.size / 2, s.size * .3f * flutter, paint); c.restore()
                } else {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = s.size * .8f; paint.strokeCap = Paint.Cap.ROUND
                    c.drawLine(s.x - s.vx * .035f, s.y - s.vy * .035f, s.x, s.y, paint)
                }
            }

            paint.style = Paint.Style.FILL_AND_STROKE; paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
            paint.textSize = titleSize
            val fit = min(1f, w * .9f / max(1f, paint.measureText(title)))
            var scale = fit; var shiftY = 0f; var turn = 0f; var alpha = appear
            when (kind) {
                Kind.ACE, Kind.EAGLE, Kind.BIRDIE -> { scale *= easeBack(appear).coerceAtLeast(0f); alpha = min(1f, appear * 2.5f); if (kind == Kind.ACE && t > .5f) scale *= 1f + .04f * sin(t * 6f) }
                Kind.PAR -> shiftY = (1f - appear) * ui.dpf(16f)
                Kind.BOGEY -> shiftY = -(1f - bounce(appear)) * ui.dpf(70f)
                Kind.WORSE -> { scale *= 1f + .25f * (1f - appear); turn = sin(t * 30f) * 4f * exp(-t * 4f) }
            }
            c.save(); c.translate(cx, cy + shiftY); c.rotate(turn); c.scale(scale, scale)
            val baseline = -(paint.ascent() + paint.descent()) / 2f
            paint.strokeJoin = Paint.Join.ROUND; paint.strokeWidth = ui.dpf(7f); paint.color = 0xFF14201A.toInt(); paint.alpha = (255 * alpha).toInt()
            c.drawText(title, 0f, baseline, paint)
            paint.strokeWidth = 0f; paint.color = fill; paint.alpha = (255 * alpha).toInt()
            c.drawText(title, 0f, baseline, paint)
            c.restore()
            postInvalidateOnAnimation()
        }
    }
}
