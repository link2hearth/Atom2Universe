package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Small cached, native pixel sprites, matching the farm's nearest-neighbour scenery. */
class FarmProjectArt {
    private val sprites = mutableMapOf<String, Bitmap>()
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private fun rect(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color; c.drawRect(x, y, x + w, y + h, paint)
    }
    private val stone = Color.rgb(177, 179, 147)
    private val shadow = Color.rgb(95, 109, 77)
    private val wood = Color.rgb(160, 111, 66)
    private val lightWood = Color.rgb(204, 156, 97)
    private val leaf = Color.rgb(75, 125, 69)
    private fun sprite(key: String, target: RectF, canvas: Canvas, draw: (Canvas) -> Unit) {
        val bitmap = sprites.getOrPut(key) {
            Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
        }
        canvas.drawBitmap(bitmap, null, target, paint)
    }
    fun project(canvas: Canvas, project: FarmProject, stage: Int, target: RectF) {
        sprite("${project.name}$stage", target, canvas) { c ->
            paint.color = Color.argb(70, 51, 79, 44)
            c.drawOval(5f, 35f, 59f, 45f, paint)
            if (stage == 0) {
                // A cleared site, timber and stakes: visible before the first contribution.
                rect(c, 12f, 29f, 41f, 8f, Color.rgb(163, 143, 91))
                for (x in listOf(10f, 51f)) {
                    rect(c, x, 23f, 2f, 13f, wood)
                    rect(c, x - 2f, 22f, 6f, 2f, lightWood)
                }
                rect(c, 16f, 36f, 20f, 3f, wood)
                rect(c, 18f, 33f, 20f, 3f, lightWood)
            } else when (project) {
                FarmProject.WELL -> {
                    rect(c, 19f, 27f, 28f, 13f, shadow)
                    rect(c, 18f, 26f, 28f, 11f, stone)
                    rect(c, 23f, 28f, 18f, 5f, Color.rgb(57, 99, 106))
                    for (y in listOf(30f, 35f)) rect(c, 19f, y, 27f, 1f, shadow)
                    for (x in listOf(26f, 36f)) rect(c, x, 31f, 1f, 4f, shadow)
                    if (stage >= 2) {
                        for (x in listOf(18f, 44f)) rect(c, x, 12f, 3f, 18f, wood)
                        rect(c, 18f, 17f, 29f, 3f, lightWood)
                        rect(c, 32f, 18f, 1f, 11f, shadow)
                        rect(c, 29f, 24f, 7f, 6f, wood)
                    }
                    if (stage == 3) {
                        paint.color = Color.rgb(146, 74, 55)
                        c.drawPath(Path().apply { moveTo(12f, 15f); lineTo(32f, 3f); lineTo(52f, 15f); close() }, paint)
                        rect(c, 12f, 15f, 40f, 3f, Color.rgb(111, 61, 45))
                        rect(c, 26f, 6f, 3f, 2f, Color.rgb(203, 125, 79))
                        rect(c, 7f, 35f, 7f, 4f, leaf)
                        rect(c, 10f, 32f, 3f, 3f, Color.rgb(247, 214, 104))
                    }
                }
                FarmProject.POND -> {
                    paint.color = Color.rgb(139, 131, 83)
                    c.drawOval(8f, 21f, 58f, 40f, paint)
                    paint.color = if (stage == 1) Color.rgb(114, 101, 65) else Color.rgb(72, 144, 159)
                    c.drawOval(12f, 23f, 53f, 36f, paint)
                    if (stage >= 2) {
                        rect(c, 20f, 26f, 19f, 1f, Color.rgb(146, 206, 206))
                        for (x in listOf(10f, 48f, 53f)) rect(c, x, 30f, 5f, 4f, stone)
                    }
                    if (stage == 3) {
                        for (x in listOf(11f, 15f, 50f)) {
                            rect(c, x, 14f, 1f, 13f, leaf)
                            rect(c, x, 12f, 2f, 6f, wood)
                        }
                        paint.color = leaf; c.drawOval(35f, 28f, 42f, 32f, paint)
                        rect(c, 37f, 27f, 3f, 2f, Color.rgb(249, 190, 165))
                        rect(c, 22f, 30f, 6f, 3f, Color.rgb(243, 218, 119))
                        rect(c, 26f, 28f, 3f, 3f, Color.rgb(243, 218, 119))
                        rect(c, 29f, 29f, 2f, 1f, Color.rgb(218, 140, 67))
                    }
                }
                FarmProject.PICNIC -> {
                    for (x in listOf(17f, 40f)) rect(c, x, 24f, 4f, 14f, wood)
                    rect(c, 12f, 22f, 37f, 6f, lightWood)
                    rect(c, 12f, 28f, 37f, 2f, wood)
                    if (stage >= 2) {
                        rect(c, 8f, 33f, 43f, 4f, lightWood)
                        for (x in listOf(11f, 45f)) rect(c, x, 37f, 3f, 5f, wood)
                    }
                    if (stage == 3) {
                        rect(c, 22f, 22f, 17f, 10f, Color.rgb(247, 225, 183))
                        for (x in listOf(24f, 30f, 36f)) rect(c, x, 22f, 2f, 10f, Color.rgb(204, 111, 86))
                        rect(c, 30f, 17f, 9f, 5f, wood)
                        rect(c, 32f, 14f, 5f, 1f, wood)
                        rect(c, 17f, 19f, 4f, 3f, Color.rgb(213, 174, 107))
                        for (x in listOf(7f, 52f)) {
                            rect(c, x, 34f, 3f, 5f, leaf)
                            rect(c, x - 1f, 32f, 5f, 2f, Color.rgb(236, 180, 165))
                        }
                    }
                }
            }
        }
    }
    fun gift(canvas: Canvas, gift: FarmGift, target: RectF, flower: FarmFlower? = null) {
        sprite(gift.name + if (gift == FarmGift.FLOWERS) flower?.name.orEmpty() else "", target, canvas) { c ->
            paint.color = Color.argb(70, 51, 79, 44); c.drawOval(9f, 36f, 56f, 43f, paint)
            when (gift) {
                FarmGift.FLOWERS -> {
                    rect(c, 13f, 31f, 39f, 9f, wood)
                    rect(c, 11f, 29f, 43f, 3f, lightWood)
                    if (flower != null) {
                        val art = FarmFlowerArt()
                        for (x in listOf(13f, 25f, 37f)) art.draw(c, flower, RectF(x, 3f, x + 15f, 34f))
                    } else for ((i, x) in listOf(18f, 28f, 39f, 47f).withIndex()) {
                        rect(c, x, 21f, 2f, 9f, leaf)
                        rect(c, x - 4f, 24f, 5f, 2f, leaf)
                        val color = if (i % 2 == 0) Color.rgb(240, 169, 154) else Color.rgb(247, 215, 115)
                        rect(c, x - 3f, 18f, 7f, 4f, color)
                        rect(c, x - 1f, 16f, 3f, 8f, color)
                        rect(c, x, 19f, 1f, 2f, wood)
                    }
                }
                FarmGift.BIRDHOUSE -> {
                    rect(c, 30f, 17f, 3f, 24f, wood)
                    rect(c, 23f, 13f, 18f, 15f, lightWood)
                    paint.color = Color.rgb(99, 142, 109)
                    c.drawPath(Path().apply { moveTo(20f, 14f); lineTo(32f, 4f); lineTo(44f, 14f); close() }, paint)
                    paint.color = shadow; c.drawCircle(32f, 20f, 3f, paint)
                    rect(c, 29f, 26f, 7f, 2f, wood)
                    rect(c, 38f, 24f, 4f, 3f, Color.rgb(225, 184, 92))
                    rect(c, 41f, 22f, 2f, 3f, Color.rgb(225, 184, 92))
                }
                FarmGift.BUNTING -> {
                    for (x in listOf(10f, 53f)) rect(c, x, 7f, 2f, 33f, wood)
                    rect(c, 10f, 11f, 45f, 1f, shadow)
                    val colors = listOf(Color.rgb(209, 121, 94), Color.rgb(247, 214, 115), Color.rgb(104, 167, 172))
                    for (i in 0..4) {
                        val x = 14f + i * 8
                        paint.color = colors[i % colors.size]
                        c.drawPath(Path().apply { moveTo(x, 12f); lineTo(x + 6, 12f); lineTo(x + 3, 20f); close() }, paint)
                    }
                }
            }
        }
    }
}

class FarmProjectPreview(context: Context, private val state: FarmProjectsState,
                         private val project: FarmProject? = null, private val gift: FarmGift? = null,
                         private val flower: FarmFlower? = null) : View(context) {
    private val art = FarmProjectArt()
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = minOf(width.toFloat(), height * 4f / 3)
        val h = w * 3 / 4
        val target = RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        project?.let { art.project(canvas, it, it.stage(state.progress(it)), target) }
        gift?.let { art.gift(canvas, it, target, flower) }
    }
}
