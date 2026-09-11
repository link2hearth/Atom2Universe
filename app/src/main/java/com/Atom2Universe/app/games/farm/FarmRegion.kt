package com.Atom2Universe.app.games.farm

import android.graphics.*
import com.Atom2Universe.app.R

enum class FarmRegion(val label: Int, val description: Int) {
    HOME(R.string.farm_region_home, R.string.farm_region_home_info),
    LIVESTOCK(R.string.farm_region_livestock, R.string.farm_region_livestock_info),
    FIELDS(R.string.farm_region_fields, R.string.farm_region_fields_info),
    GREENHOUSE(R.string.farm_region_greenhouse, R.string.farm_region_greenhouse_info)
}

/** Separate landscape previews. Their future economies never mutate the main farm save. */
class FarmRegionScenery(private val sprites: FarmSprites) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val greenhouseDecor = FarmGreenhouseDecor()
    private val fields = listOf(RectF(110f, 210f, 785f, 710f), RectF(815f, 210f, 1490f, 710f),
        RectF(110f, 750f, 785f, 1250f), RectF(815f, 750f, 1490f, 1250f))
    fun draw(canvas: Canvas, region: FarmRegion, visible: RectF) {
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
            drawGreenhouse(canvas, visible)
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
    private fun drawGreenhouse(canvas: Canvas, visible: RectF) {
        greenhouseDecor.draw(canvas)

        val topBand = RectF(95f, 120f, 805f, 310f)
        val bottomBand = RectF(95f, 1320f, 805f, 1510f)
        drawFlowerBand(canvas, topBand, 0)
        drawFlowerBand(canvas, bottomBand, 4)

        val beds = listOf(
            RectF(95f, 390f, 365f, 620f), RectF(535f, 390f, 805f, 620f),
            RectF(95f, 690f, 365f, 920f), RectF(535f, 690f, 805f, 920f),
            RectF(95f, 990f, 365f, 1220f), RectF(535f, 990f, 805f, 1220f)
        )
        beds.forEachIndexed { i, bed ->
            if (!RectF.intersects(bed, visible)) return@forEachIndexed
            drawFlowerBed(canvas, bed, i)
        }

        greenhouseDecor.drawEntrance(canvas)
    }
    private fun drawFlowerBand(canvas: Canvas, band: RectF, offset: Int) {
        greenhouseDecor.drawPlanter(canvas, band)
        for (i in 0..7) {
            val x = band.left + 55f + i * (band.width() - 110f) / 7f
            val flower = (i + offset) % 8
            val sheet = if (flower < 4) FLOWERS_A else FLOWERS_B
            val row = (flower % 4) * 2 + (i + offset) % 2
            sprites.flower(canvas, sheet, row, 4, RectF(x - 48f, band.top + 38f, x + 48f, band.bottom - 18f))
        }
    }
    private fun drawFlowerBed(canvas: Canvas, bed: RectF, index: Int) {
        greenhouseDecor.drawPlanter(canvas, bed)
        val sheet = if (index < 3) FLOWERS_A else FLOWERS_B
        val baseRow = (index % 4) * 2
        for (row in 0..1) for (col in 0..2) {
            val stage = 2 + (row + col + index) % 3
            val variant = (row + col + index) % 2
            val x = bed.left + 58f + col * (bed.width() - 116f) / 2f
            val y = bed.top + 88f + row * 82f
            sprites.flower(canvas, sheet, baseRow + variant, stage, RectF(x - 48f, y - 72f, x + 48f, y + 52f))
        }
        paint.color = Color.rgb(80, 55, 36)
        paint.alpha = 90
        canvas.drawRoundRect(bed.left + 28f, bed.bottom - 28f, bed.right - 28f, bed.bottom - 17f, 6f, 6f, paint)
        paint.alpha = 255
    }
    private companion object {
        const val FLOWERS_A = "garden_flowers_sunflower_tulip_lavender_daisy_v1.png"
        const val FLOWERS_B = "garden_flowers_rose_hydrangea_poppy_orchid_v1.png"
    }
}
