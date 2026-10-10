package com.Atom2Universe.app.games.farm

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Native kitchen worktop: motion is feedback, never a timer or a condition for the reward. */
class FarmCookingView(context: Context, val session: FarmCookingSession) : View(context) {
    var onProgress: (() -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var downX = 0f; private var downY = 0f
    private var fingerX = 0f; private var fingerY = 0f
    private var dragging = false
    private var downStep = -1
    private var spoonAngle = 0f
    private var pulseAt = -1L
    init {
        isClickable = true; isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        updateDescription()
    }
    private val ink = Color.rgb(105, 76, 49)
    private val cream = Color.rgb(255, 241, 208)
    private val green = Color.rgb(112, 153, 85)
    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        paint.color = color; paint.style = Paint.Style.FILL; c.drawOval(l, t, r, b, paint)
    }
    private fun round(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 5f) {
        paint.color = color; paint.style = Paint.Style.FILL; c.drawRoundRect(l, t, r, b, radius, radius, paint)
    }
    private fun line(c: Canvas, x: Float, y: Float, x2: Float, y2: Float, color: Int, stroke: Float = 2f) {
        paint.color = color; paint.strokeWidth = stroke; paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(x, y, x2, y2, paint)
    }
    private fun label(c: Canvas, value: String, x: Float, y: Float, size: Float = 5f) {
        paint.color = ink; paint.textSize = size; paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); c.drawText(value, x, y, paint)
    }
    private fun ingredient(c: Canvas, index: Int, x: Float, y: Float, chopped: Boolean = false) {
        val colors = listOf(green, Color.rgb(228, 170, 88), Color.rgb(208, 124, 99), Color.rgb(169, 127, 76))
        val crops = session.recipe.crops.flatMap { ingredient -> List(ingredient.count) { ingredient.crop } }
        val crop = if (crops.isEmpty()) null else crops[index % crops.size]
        val color = when (crop) {
            FarmCrop.RADISH, FarmCrop.STRAWBERRY, FarmCrop.TOMATO, FarmCrop.APPLE -> Color.rgb(208, 124, 99)
            FarmCrop.CHERRY -> Color.rgb(163, 86, 91)
            FarmCrop.PUMPKIN, FarmCrop.CORN -> Color.rgb(228, 170, 88)
            FarmCrop.CAULIFLOWER -> cream
            FarmCrop.PEAS, FarmCrop.LETTUCE, FarmCrop.ZUCCHINI, FarmCrop.PEAR -> green
            else -> colors[index % colors.size]
        }
        if (chopped) {
            for (j in 0..2) round(c, x - 7 + j * 5, y - 3, x - 3 + j * 5, y + 4, color, 2f)
        } else {
            oval(c, x - 7, y - 8, x + 7, y + 8, color)
            line(c, x, y - 5, x + 1, y + 4, cream, 1f)
            line(c, x, y - 8, x + 4, y - 12, green, 2f)
        }
    }
    private fun assemblyIngredient(c: Canvas, index: Int, x: Float, y: Float) {
        if (!session.sandwich) { ingredient(c, index, x, y); return }
        when (index) {
            0, 3 -> round(c, x - 8, y - 5, x + 8, y + 5, Color.rgb(220, 179, 101), 4f)
            1 -> {
                for (i in 0..2) oval(c, x - 8 + i * 5, y - 5, x - 1 + i * 5, y + 5, green)
            }
            else -> if (session.recipe == FarmRecipe.EGG_SANDWICH) {
                oval(c, x - 7, y - 6, x + 7, y + 6, cream)
                oval(c, x - 3, y - 3, x + 3, y + 3, Color.rgb(235, 190, 65))
            } else round(c, x - 7, y - 5, x + 7, y + 5, Color.rgb(246, 211, 105), 1f)
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.scale(width / 100f, height / 100f)
        round(canvas, 1f, 1f, 99f, 99f, Color.rgb(238, 221, 184), 7f)
        for (y in listOf(18f, 40f, 62f, 84f)) line(canvas, 3f, y, 97f, y, Color.rgb(220, 199, 162), .6f)
        when (session.step) {
            FarmCookingStep.CHOP -> {
                round(canvas, 5f, 25f, 95f, 81f, ink, 7f)
                round(canvas, 7f, 27f, 93f, 79f, Color.rgb(201, 161, 104), 6f)
                oval(canvas, 10f, 44f, 13f, 62f, ink)
                for (i in 0..3) {
                    val x = 20f + i * 20
                    ingredient(canvas, i, x, 54f, session.chopped(i))
                    if (!session.chopped(i)) {
                        for (y in listOf(32f, 37f, 65f, 70f)) line(canvas, x, y, x, y + 3, cream, 1f)
                        line(canvas, x - 3, 67f, x, 72f, cream, 1f); line(canvas, x + 3, 67f, x, 72f, cream, 1f)
                    }
                }
                if (dragging) {
                    round(canvas, fingerX * 100 - 2, fingerY * 100 - 11, fingerX * 100 + 2, fingerY * 100 + 2,
                        Color.rgb(198, 209, 202), 1f)
                    round(canvas, fingerX * 100 - 2, fingerY * 100 - 17, fingerX * 100 + 2, fingerY * 100 - 10, ink, 1f)
                }
            }
            FarmCookingStep.STIR -> {
                round(canvas, 8f, 46f, 92f, 56f, ink, 4f)
                oval(canvas, 15f, 20f, 85f, 89f, ink)
                oval(canvas, 18f, 23f, 82f, 85f, green)
                oval(canvas, 22f, 27f, 78f, 79f, Color.rgb(219, 172, 95))
                for (i in 0..6) {
                    val a = spoonAngle + i * PI * 2 / 7
                    val x = 50 + cos(a) * 19; val y = 53 + sin(a) * 18
                    oval(canvas, x.toFloat() - 2, y.toFloat() - 2, x.toFloat() + 2, y.toFloat() + 2,
                        if (i % 2 == 0) green else cream)
                }
                val x = 50f + cos(spoonAngle) * 22; val y = 53f + sin(spoonAngle) * 22
                line(canvas, x, y, x + 15, y - 20, ink, 3f)
                oval(canvas, x - 4, y - 3, x + 4, y + 3, Color.rgb(169, 119, 71))
                paint.color = cream; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.4f
                canvas.drawArc(27f, 31f, 73f, 77f, -70f, 280f, false, paint); paint.style = Paint.Style.FILL
                line(canvas, 70f, 48f, 73f, 51f, cream, 1.5f); line(canvas, 76f, 46f, 73f, 51f, cream, 1.5f)
            }
            FarmCookingStep.ASSEMBLE -> {
                val sandwich = session.sandwich
                oval(canvas, 20f, 57f, 80f, 95f, ink); oval(canvas, 22f, 59f, 78f, 93f, cream)
                if (sandwich && session.layers > 0) round(canvas, 32f, 69f, 68f, 82f, Color.rgb(202, 159, 87), 7f)
                for (i in 0..3) {
                    val x = 14f + i * 24
                    round(canvas, x - 10, 12f, x + 10, 35f, cream, 4f)
                    if (i >= session.layers) {
                        assemblyIngredient(canvas, i, x, 24f)
                        label(canvas, context.getString(R.string.farm_kitchen_number, i + 1), x, 9f)
                    }
                    if (i < session.layers) {
                        if (sandwich) {
                            val y = 74f - i * 5
                            round(canvas, 33f, y - 4, 67f, y, when (i) {
                                0 -> Color.rgb(219, 176, 94); 1 -> green; 2 -> Color.rgb(244, 214, 124); else -> Color.rgb(216, 179, 112)
                            }, 3f)
                        } else ingredient(canvas, i, 36f + i * 9, 73f, true)
                    }
                }
                if (dragging) assemblyIngredient(canvas, session.layers, fingerX * 100, fingerY * 100)
                label(canvas, context.getString(R.string.farm_kitchen_plate), 50f, 96f)
            }
            null -> {
                oval(canvas, 16f, 25f, 84f, 88f, green); oval(canvas, 21f, 30f, 79f, 83f, cream)
                line(canvas, 32f, 56f, 45f, 68f, green, 5f); line(canvas, 45f, 68f, 70f, 43f, green, 5f)
            }
        }
        round(canvas, 8f, 3f, 92f, 6f, cream, 2f)
        if (session.progress > 0) round(canvas, 8f, 3f, 8f + 84 * session.progress, 6f, green, 2f)
        val age = SystemClock.uptimeMillis() - pulseAt
        if (pulseAt >= 0 && age in 0..650 && ValueAnimator.areAnimatorsEnabled()) {
            val t = age / 650f
            for (i in 0..7) {
                val a = i * PI / 4; val r = 8 + t * 22
                oval(canvas, (50 + cos(a) * r).toFloat() - 1, (53 + sin(a) * r).toFloat() - 1,
                    (50 + cos(a) * r).toFloat() + 1, (53 + sin(a) * r).toFloat() + 1, Color.rgb(206, 165, 69))
            }
            postInvalidateOnAnimation()
        }
        canvas.restore()
    }
    private fun updateDescription() {
        contentDescription = if (session.complete) context.getString(R.string.farm_kitchen_done) else context.getString(R.string.farm_kitchen_accessibility,
            session.step?.let { context.getString(it.label) } ?: context.getString(R.string.farm_kitchen_done),
            (session.progress * 100).toInt())
    }
    fun assistGesture() { if (session.assist()) changed() }
    private fun changed() {
        pulseAt = SystemClock.uptimeMillis()
        if (session.progress >= .99999f) { session.next(); dragging = false }
        updateDescription(); invalidate(); onProgress?.invoke()
    }
    override fun performClick(): Boolean { super.performClick(); assistGesture(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width <= 0 || height <= 0) return false
        if (session.complete) {
            dragging = false; session.endStroke(); parent?.requestDisallowInterceptTouchEvent(false); return true
        }
        val x = (event.x / width).coerceIn(0f, 1f); val y = (event.y / height).coerceIn(0f, 1f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = x; downY = y; fingerX = x; fingerY = y; dragging = true; downStep = session.index
                session.endStroke(); session.stir(x, y); invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return true
                fingerX = x; fingerY = y
                if (session.step == FarmCookingStep.STIR) {
                    spoonAngle = kotlin.math.atan2(y - .53f, x - .5f)
                    if (session.stir(x, y)) changed()
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging && downStep == session.index && session.drag(downX, downY, x, y)) changed()
                dragging = false
                session.endStroke(); parent?.requestDisallowInterceptTouchEvent(false); invalidate(); return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false; session.endStroke(); parent?.requestDisallowInterceptTouchEvent(false); invalidate(); return true
            }
        }
        return true
    }
    override fun onDetachedFromWindow() { dragging = false; session.endStroke(); super.onDetachedFromWindow() }
}
