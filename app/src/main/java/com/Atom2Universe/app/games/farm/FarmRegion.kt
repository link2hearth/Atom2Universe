package com.Atom2Universe.app.games.farm

import android.graphics.*
import com.Atom2Universe.app.R

enum class FarmRegion(val label: Int, val description: Int) {
    HOME(R.string.farm_region_home, R.string.farm_region_home_info),
    LIVESTOCK(R.string.farm_region_livestock, R.string.farm_region_livestock_info),
    FIELDS(R.string.farm_region_fields, R.string.farm_region_fields_info),
    GREENHOUSE(R.string.farm_region_greenhouse, R.string.farm_region_greenhouse_info)
}

/** Native landscapes. Greenhouse art and hit testing share the same planter rectangles. */
class FarmRegionScenery(private val sprites: FarmSprites) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val greenhouseDecor = FarmGreenhouseDecor()
    private val fields = listOf(RectF(110f, 210f, 785f, 710f), RectF(815f, 210f, 1490f, 710f),
        RectF(110f, 750f, 785f, 1250f), RectF(815f, 750f, 1490f, 1250f))
    private val flowers = FarmFlowerArt()
    private val visitors = FarmVisitorArt()
    fun draw(canvas: Canvas, region: FarmRegion, visible: RectF, greenhouse: FarmGreenhouseState? = null, now: Long = System.currentTimeMillis()) {
        if (region == FarmRegion.FIELDS) {
            paint.color = Color.rgb(191, 169, 113)
            canvas.drawRoundRect(75f, 175f, 1525f, 1285f, 30f, 30f, paint)
            fields.forEachIndexed { i, field ->
                if (!RectF.intersects(field, visible)) return@forEachIndexed
                paint.color = Color.rgb(146, 116, 60)
                canvas.drawRect(field, paint)
                // Continuous crop canopy: no individual soil squares or internal fences.
                canvas.save(); canvas.clipRect(field)
                for (row in 0..20) for (col in 0..25) {
                    val x = field.left + col * 28f + if (row % 2 == 0) 0f else 14f
                    val y = field.top + row * 26f
                    val target = RectF(x - 5, y - 18, x + 35, y + 32)
                    if (RectF.intersects(target, visible)) sprites.crop(canvas, FarmCrop.WHEAT, i % 2, 4, target)
                }
                canvas.restore()
            }
        } else if (region == FarmRegion.GREENHOUSE) {
            drawGreenhouse(canvas, visible, greenhouse, now)
        } else {
            paint.color = Color.rgb(190, 168, 112)
            canvas.drawPath(Path().apply {
                moveTo(800f, 1450f); cubicTo(690f, 1090f, 990f, 820f, 800f, 530f)
            }, Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = 65f; strokeCap = Paint.Cap.ROUND })
            for (pen in listOf(RectF(100f, 600f, 690f, 1250f), RectF(950f, 660f, 1500f, 1190f))) {
                paint.color = Color.rgb(137, 167, 92)
                canvas.drawRoundRect(pen, 18f, 18f, paint)
                val columns = 7
                for (i in 0 until columns) {
                    val x = pen.left + i * pen.width() / columns
                    sprites.environment(canvas, 0, 2, RectF(x, pen.top - 20, x + pen.width() / columns, pen.top + 30))
                    sprites.environment(canvas, 0, if (i == 3) 3 else 2, RectF(x, pen.bottom - 25, x + pen.width() / columns, pen.bottom + 25))
                }
                for (i in 0..7) for (x in listOf(pen.left, pen.right))
                    sprites.environment(canvas, 1, 2, RectF(x - 10, pen.top + i * pen.height() / 8, x + 12, pen.top + (i + 1) * pen.height() / 8))
                paint.color = Color.rgb(104, 90, 70)
                canvas.drawRoundRect(pen.left + 50f, pen.top + 80f, pen.left + 180, pen.top + 130, 10f, 10f, paint)
                paint.color = Color.rgb(129, 188, 195)
                canvas.drawRoundRect(pen.left + 56, pen.top + 86, pen.left + 174, pen.top + 124, 6f, 6f, paint)
            }
            paint.color = Color.rgb(193, 151, 103)
            canvas.drawRect(540f, 260f, 1060f, 510f, paint)
            paint.color = Color.rgb(98, 113, 78)
            canvas.drawRect(740f, 340f, 860f, 510f, paint)
            paint.color = Color.rgb(178, 103, 84)
            canvas.drawPath(Path().apply { moveTo(490f, 280f); lineTo(800f, 120f); lineTo(1110f, 280f); close() }, paint)
            paint.color = Color.rgb(235, 208, 150)
            canvas.drawRoundRect(570f, 450f, 660f, 520f, 12f, 12f, paint)
            canvas.drawRoundRect(950f, 450f, 1040f, 520f, 12f, 12f, paint)
        }
        if (region != FarmRegion.GREENHOUSE) for (i in 0..12) {
            val x = 20f + i * 125
            sprites.environment(canvas, 2, 3, RectF(x, 1350f + i % 3 * 20, x + 70, 1410f + i % 3 * 20))
        }
    }
    private val bands = listOf(RectF(95f, 120f, 805f, 310f), RectF(95f, 1320f, 805f, 1510f))
    fun greenhouseSlot(x: Float, y: Float): Int? = FarmGreenhouseLayout.beds.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }
    private fun drawGreenhouse(canvas: Canvas, visible: RectF, state: FarmGreenhouseState?, now: Long) {
        val time = if (android.animation.ValueAnimator.areAnimatorsEnabled()) (now % 120_000) / 1000f else 0f
        greenhouseDecor.draw(canvas)
        bands.forEach { greenhouseDecor.drawPlanter(canvas, it) }
        FarmGreenhouseLayout.beds.forEachIndexed { slot, bed ->
            if (!RectF.intersects(bed, visible)) return@forEachIndexed
            greenhouseDecor.drawPlanter(canvas, bed)
            paint.color = Color.rgb(80, 55, 36)
            paint.alpha = 90
            canvas.drawRoundRect(bed.left + 28f, bed.bottom - 28f, bed.right - 28f, bed.bottom - 17f, 6f, 6f, paint)
            paint.alpha = 255
            state?.at(slot)?.let { culture ->
                for (i in 0..2) {
                    val x = bed.left + 35f + i * 68f
                    flowers.draw(canvas, culture.flower, RectF(x, bed.top + 25f, x + 66f, bed.bottom - 26f), culture.progress(now),
                        sway = kotlin.math.sin(time * 1.5f + i + slot) * 2.5f)
                }
                if (culture.readyAt <= now) {
                    paint.color = Color.rgb(247, 210, 106)
                    canvas.drawCircle(bed.right - 22f, bed.top + 22f, 9f + kotlin.math.sin(time * 2 + slot), paint)
                }
            }
        }
        state?.let { greenhouse -> bands.forEach { bed ->
            for (i in 0..6) {
                val x = bed.left + 34f + i * 94f
                flowers.draw(canvas, greenhouse.displayFlower, RectF(x, bed.top + 24f, x + 60f, bed.bottom - 22f),
                    sway = kotlin.math.sin(time * 1.5f + i) * 2.5f)
            }
        } }
        greenhouseDecor.drawEntrance(canvas)
        for (i in 0..1) {
            val x = 430f + kotlin.math.sin(time * .35f + i * 2) * 85f
            val y = 400f + i * 470f + kotlin.math.cos(time * .4f + i) * 95f
            visitors.draw(canvas, FarmVisitor.BUTTERFLY, RectF(x - 22f, y - 22f, x + 22f, y + 22f), time + i)
        }
    }
}

object FarmGreenhouseLayout {
    val beds = listOf(
        RectF(95f, 390f, 365f, 620f), RectF(535f, 390f, 805f, 620f),
        RectF(95f, 690f, 365f, 920f), RectF(535f, 690f, 805f, 920f),
        RectF(95f, 990f, 365f, 1220f), RectF(535f, 990f, 805f, 1220f)
    )
}
