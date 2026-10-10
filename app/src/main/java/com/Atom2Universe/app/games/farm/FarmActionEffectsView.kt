package com.Atom2Universe.app.games.farm

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** A bounded, touch-transparent layer: brief visual feedback never controls game state. */
class FarmActionEffectsView(context: Context) : View(context) {
    enum class Kind { SEEDS, WATER, BASKET, HEART, COINS, GIFT, COOK, FLOWERS, BUILD, PANTRY }
    private data class Effect(val kind: Kind, val x: Float, val y: Float, val caption: String,
                              val at: Long, val animated: Boolean, val flower: FarmFlower?)
    private val active = mutableListOf<Effect>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flowerArt = FarmFlowerArt()
    private val density = resources.displayMetrics.density
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; isClickable = false; isFocusable = false }
    fun emit(kind: Kind, x: Float, y: Float, caption: String = "", flower: FarmFlower? = null) {
        if (active.size >= 12) active.removeAt(0)
        val now = SystemClock.uptimeMillis()
        val nearby = active.count { now - it.at < 100 && kotlin.math.abs(it.y - y) < 10 * density }
        val offset = if (nearby == 0) 0f else if (nearby % 2 == 1) 64 * density else -64 * density
        active += Effect(kind, x + offset, y, caption, now, ValueAnimator.areAnimatorsEnabled(), flower)
        invalidate()
    }
    private fun round(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 3f) {
        paint.color = color; c.drawRoundRect(l, t, r, b, radius, radius, paint)
    }
    private fun icon(c: Canvas, kind: Kind, flower: FarmFlower?, phase: Float) {
        val wood = Color.rgb(135, 92, 56); val cream = Color.rgb(255, 242, 205); val green = Color.rgb(94, 137, 77)
        when (kind) {
            Kind.PANTRY -> {
                round(c, -21f, -13f, 21f, 16f, wood, 7f)
                round(c, -18f, -10f, 18f, 13f, cream, 5f)
                for (i in 0..2) round(c, -14f + i * 10, -6f, -6f + i * 10, 6f, Color.rgb(200, 165, 125), 3f)
                round(c, -8f, 8f, 8f, 14f, green, 2f)
            }
            Kind.COINS -> {
                paint.color = Color.rgb(203, 152, 48); c.drawCircle(0f, 0f, 18f, paint)
                paint.color = Color.rgb(249, 215, 107); c.drawCircle(0f, -1f, 14f, paint)
                round(c, -2f, -9f, 2f, 8f, wood)
            }
            Kind.SEEDS -> {
                round(c, -14f, -16f, 14f, 17f, cream, 7f); round(c, -15f, -17f, 15f, -10f, green)
                for (i in 0..2) {
                    paint.color = wood; c.drawOval(-7f + i * 6, -3f, -3f + i * 6, 4f, paint)
                }
                round(c, -8f, 8f, 8f, 11f, green)
            }
            Kind.WATER -> {
                round(c, -15f, -8f, 7f, 14f, Color.rgb(118, 178, 193), 6f)
                round(c, 5f, -3f, 22f, 2f, Color.rgb(118, 178, 193))
                paint.color = Color.rgb(89, 151, 171); paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
                c.drawOval(-23f, -6f, -8f, 9f, paint); paint.style = Paint.Style.FILL
            }
            Kind.BASKET -> {
                paint.color = wood; paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                c.drawArc(-16f, -22f, 16f, 12f, 180f, 180f, false, paint); paint.style = Paint.Style.FILL
                for ((i, color) in listOf(0xCD765D, 0xF1D06C, 0x8EAB68).withIndex()) {
                    paint.color = color or -0x1000000; c.drawCircle(-10f + i * 10, -6f, 7f, paint)
                }
                round(c, -20f, -3f, 20f, 17f, wood, 5f)
                for (i in 0..2) round(c, -18f, 2f + i * 5, 18f, 4f + i * 5, Color.rgb(201, 158, 99))
            }
            Kind.HEART -> {
                paint.color = Color.rgb(216, 125, 115)
                c.drawPath(Path().apply { moveTo(0f, 18f); cubicTo(-38f, -4f, -16f, -30f, 0f, -10f)
                    cubicTo(16f, -30f, 38f, -4f, 0f, 18f); close() }, paint)
            }
            Kind.GIFT -> {
                round(c, -17f, -11f, 17f, 17f, Color.rgb(224, 168, 105))
                round(c, -20f, -15f, 20f, -7f, Color.rgb(245, 210, 143))
                round(c, -3f, -15f, 3f, 17f, green)
                paint.color = green; c.drawOval(-14f, -23f, 0f, -13f, paint); c.drawOval(0f, -23f, 14f, -13f, paint)
            }
            Kind.COOK -> {
                round(c, -15f, -7f, 15f, 18f, Color.rgb(158, 185, 133), 8f)
                round(c, -18f, -10f, 18f, -5f, cream)
                paint.color = wood; paint.strokeWidth = 3f
                c.drawLine(4f, 0f, 22f + sin(phase * 8) * 3, -21f, paint)
                paint.color = Color.rgb(201, 157, 81); c.drawCircle(3f, 2f, 5f, paint)
            }
            Kind.FLOWERS -> flowerArt.draw(c, flower ?: FarmFlower.DAISY_WHITE, RectF(-22f, -31f, 22f, 25f))
            Kind.BUILD -> {
                c.save(); c.rotate(-24f + sin(phase * 9) * 12f)
                round(c, -3f, -12f, 3f, 22f, wood); round(c, -14f, -20f, 14f, -10f, Color.rgb(163, 177, 158))
                c.restore()
            }
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        active.removeAll { now - it.at >= 1200 }
        for (effect in active) {
            val t = ((now - effect.at) / 1200f).coerceIn(0f, 1f)
            val fade = if (!effect.animated || t < .65f) 1f else (1f - t) / .35f
            val rise = if (effect.animated) (1f - (1f - t) * (1f - t)) * 48f else 0f
            val x = effect.x.coerceIn(40f * density, maxOf(40f * density, width - 40f * density))
            val y = effect.y.coerceIn(100f * density, maxOf(100f * density, height - 80f * density)) - rise * density
            canvas.saveLayerAlpha(RectF(x - 140 * density, y - 120 * density, x + 140 * density, y + 100 * density), (fade * 255).toInt())
            canvas.translate(x, y); canvas.scale(density, density)
            if (effect.animated) {
                for (i in 0..7) {
                    val a = i * Math.PI / 4
                    val radius = 18f + t * 40f
                    paint.color = when (effect.kind) {
                        Kind.WATER -> Color.rgb(115, 192, 208)
                        Kind.HEART -> Color.rgb(229, 155, 146)
                        Kind.FLOWERS -> (effect.flower?.color ?: 0xE8C965) or -0x1000000
                        else -> Color.rgb(224, 186, 98)
                    }
                    canvas.drawOval((cos(a) * radius - 2).toFloat(), (sin(a) * radius - 2 + t * t * 15).toFloat(),
                        (cos(a) * radius + 2).toFloat(), (sin(a) * radius + 2 + t * t * 15).toFloat(), paint)
                }
            }
            canvas.save()
            val pop = if (effect.animated) .7f + minOf(t * 5, .3f) + sin(t * Math.PI).toFloat() * .12f else 1f
            canvas.scale(pop, pop); canvas.rotate(if (effect.animated) sin(t * 6) * 6 else 0f)
            icon(canvas, effect.kind, effect.flower, if (effect.animated) t else 0f); canvas.restore()
            if (effect.caption.isNotEmpty()) {
                paint.textSize = 15f; paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
                val labelWidth = (paint.measureText(effect.caption) + 16f).coerceAtMost(260f)
                round(canvas, -labelWidth / 2, 29f, labelWidth / 2, 52f, Color.rgb(255, 248, 225), 9f)
                paint.color = Color.rgb(85, 100, 62); canvas.drawText(effect.caption, 0f, 46f, paint)
            }
            canvas.restore()
        }
        if (active.any { it.animated }) postInvalidateOnAnimation()
        else if (active.isNotEmpty()) postInvalidateDelayed((1200 - (now - active.first().at)).coerceAtLeast(1))
    }
    override fun onDetachedFromWindow() { active.clear(); super.onDetachedFromWindow() }
}
