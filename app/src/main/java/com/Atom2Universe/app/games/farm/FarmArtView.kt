package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.*
import android.view.View

/** Small native illustrations remain crisp at any density; crop previews reuse the game art. */
class FarmArtView(context: Context, private val kind: Kind, private val sprites: FarmSprites? = null) : View(context) {
    enum class Kind { SHOP, SEEDS, MAP, BACK, CLOSE, COIN, CROP, WATER, MANURE, CRATE, VILLAGE, VILLAGER, ANIMAL_PRODUCT, PREPARATION }
    var recipe: FarmRecipe = FarmRecipe.PICKLES
        set(value) { field = value; invalidate() }
    var product: FarmAnimalProduct = FarmAnimalProduct.EGG
        set(value) { field = value; invalidate() }
    var villager: FarmVillager? = null
        set(value) { field = value; invalidate() }
    var crop: FarmCrop? = null
        set(value) { field = value; invalidate() }
    var stock: Int? = null
        set(value) { field = value; invalidate() }
    /** A small "something needs you" dot, independent of [stock] - top corner, never the same spot. */
    var alert: Boolean = false
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 3f) {
        paint.color = color; paint.style = Paint.Style.FILL
        c.drawRoundRect(l, t, r, b, radius, radius, paint)
    }
    private fun line(c: Canvas, x: Float, y: Float, x2: Float, y2: Float, color: Int, stroke: Float = 3f) {
        paint.color = color; paint.strokeWidth = stroke; paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(x, y, x2, y2, paint)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width, height) / 48f
        canvas.save(); canvas.translate((width - 48 * scale) / 2, (height - 48 * scale) / 2); canvas.scale(scale, scale)
        val cream = Color.rgb(255, 242, 205)
        val brown = Color.rgb(108, 76, 51)
        val green = Color.rgb(93, 134, 81)
        val rose = Color.rgb(206, 114, 91)
        when (kind) {
            Kind.PREPARATION -> when (recipe) {
                FarmRecipe.PICKLES -> {
                    rect(canvas, 12f, 9f, 36f, 43f, brown, 7f)
                    rect(canvas, 14f, 12f, 34f, 40f, Color.rgb(182, 211, 174), 5f)
                    rect(canvas, 11f, 6f, 37f, 13f, green, 2f)
                    for (x in listOf(19f, 28f)) {
                        paint.color = rose; canvas.drawOval(x - 4, 22f, x + 4, 33f, paint)
                        line(canvas, x, 22f, x - 2, 17f, green, 2f)
                    }
                }
                FarmRecipe.SOUP, FarmRecipe.EGG_SALAD -> {
                    paint.color = brown; canvas.drawOval(5f, 24f, 43f, 43f, paint)
                    rect(canvas, 6f, 24f, 42f, 34f, green, 4f)
                    paint.color = if (recipe == FarmRecipe.SOUP) Color.rgb(207, 164, 83) else Color.rgb(151, 190, 105)
                    canvas.drawOval(7f, 20f, 41f, 32f, paint)
                    for (x in listOf(15f, 23f, 32f)) {
                        paint.color = if (recipe == FarmRecipe.SOUP) green else cream
                        canvas.drawCircle(x, 26f, 3f, paint)
                    }
                    if (recipe == FarmRecipe.SOUP) {
                        line(canvas, 17f, 15f, 20f, 9f, brown, 2f)
                        line(canvas, 28f, 15f, 31f, 7f, brown, 2f)
                    }
                }
                FarmRecipe.TRUFFLE_PAN -> {
                    line(canvas, 31f, 24f, 44f, 11f, brown, 6f)
                    paint.color = brown; canvas.drawOval(3f, 17f, 37f, 39f, paint)
                    paint.color = Color.rgb(210, 174, 98); canvas.drawOval(6f, 20f, 34f, 35f, paint)
                    for (x in listOf(12f, 22f, 29f)) {
                        paint.color = brown; canvas.drawCircle(x, 27f, 3f, paint)
                    }
                }
                FarmRecipe.CHEESE -> {
                    rect(canvas, 5f, 37f, 43f, 42f, brown, 3f)
                    rect(canvas, 10f, 23f, 38f, 37f, Color.rgb(229, 198, 108), 4f)
                    paint.color = cream; canvas.drawOval(10f, 16f, 38f, 29f, paint)
                    for (x in listOf(18f, 29f)) {
                        paint.color = Color.rgb(194, 164, 87); canvas.drawCircle(x, 30f, 2f, paint)
                    }
                    line(canvas, 16f, 13f, 31f, 8f, green, 2f)
                }
                FarmRecipe.SCARF -> {
                    rect(canvas, 10f, 7f, 23f, 41f, rose, 4f)
                    rect(canvas, 20f, 7f, 37f, 18f, rose, 4f)
                    rect(canvas, 29f, 14f, 39f, 34f, Color.rgb(166, 93, 85), 3f)
                    for (y in listOf(14f, 23f, 32f)) line(canvas, 11f, y, 22f, y, cream, 2f)
                    for (x in listOf(13f, 17f, 21f)) line(canvas, x, 39f, x, 44f, rose, 2f)
                }
            }
            Kind.ANIMAL_PRODUCT -> when (product) {
                FarmAnimalProduct.EGG -> {
                    paint.color = brown; canvas.drawOval(10f, 6f, 38f, 44f, paint)
                    paint.color = cream; canvas.drawOval(12f, 7f, 36f, 42f, paint)
                    paint.color = Color.rgb(231, 203, 155); canvas.drawOval(23f, 26f, 34f, 39f, paint)
                }
                FarmAnimalProduct.WOOL -> {
                    paint.color = brown; canvas.drawCircle(24f, 25f, 18f, paint)
                    paint.color = cream; canvas.drawCircle(24f, 25f, 16f, paint)
                    for (i in 0..3) line(canvas, 13f + i * 5, 15f, 18f + i * 5, 36f, Color.rgb(204, 190, 152), 2f)
                    line(canvas, 13f, 23f, 34f, 15f, brown, 1.5f)
                    line(canvas, 16f, 32f, 36f, 24f, brown, 1.5f)
                }
                FarmAnimalProduct.MILK -> {
                    rect(canvas, 14f, 11f, 34f, 42f, brown, 7f)
                    rect(canvas, 16f, 13f, 32f, 40f, cream, 6f)
                    rect(canvas, 18f, 6f, 30f, 15f, Color.rgb(159, 191, 194), 2f)
                    rect(canvas, 16f, 24f, 32f, 34f, Color.rgb(159, 191, 194), 1f)
                }
                FarmAnimalProduct.TRUFFLE -> {
                    paint.color = brown; canvas.drawOval(7f, 15f, 40f, 40f, paint)
                    for (i in 0..7) {
                        paint.color = if (i % 2 == 0) Color.rgb(137, 99, 67) else Color.rgb(74, 65, 47)
                        canvas.drawCircle(12f + i % 4 * 7, 22f + i / 4 * 10, 3f, paint)
                    }
                    line(canvas, 30f, 14f, 37f, 8f, green, 3f)
                }
            }
            Kind.VILLAGE -> {
                line(canvas, 13f, 26f, 13f, 44f, brown, 4f)
                line(canvas, 35f, 26f, 35f, 44f, brown, 4f)
                rect(canvas, 5f, 7f, 43f, 35f, brown, 5f)
                rect(canvas, 8f, 10f, 40f, 32f, cream, 3f)
                rect(canvas, 12f, 14f, 28f, 26f, Color.rgb(228, 210, 167), 2f)
                line(canvas, 12f, 14f, 20f, 21f, rose, 1.8f)
                line(canvas, 28f, 14f, 20f, 21f, rose, 1.8f)
                paint.color = green; canvas.drawCircle(34f, 17f, 3f, paint)
                line(canvas, 32f, 24f, 36f, 24f, green, 2f)
            }
            Kind.VILLAGER -> {
                val person = villager ?: FarmVillager.LUCIE
                val shirt = when (person) {
                    FarmVillager.LUCIE -> rose
                    FarmVillager.MALO -> green
                    FarmVillager.IRIS -> Color.rgb(143, 120, 174)
                }
                val skin = if (person == FarmVillager.IRIS) Color.rgb(182, 127, 85) else Color.rgb(238, 189, 137)
                val hair = if (person == FarmVillager.MALO) Color.rgb(165, 121, 61) else brown
                paint.color = sagePortraitColor; canvas.drawCircle(24f, 24f, 23f, paint)
                rect(canvas, 9f, 30f, 39f, 47f, shirt, 12f)
                rect(canvas, 21f, 26f, 27f, 34f, skin, 3f)
                paint.color = hair; canvas.drawOval(12f, 5f, 36f, 32f, paint)
                paint.color = skin; canvas.drawOval(15f, 10f, 33f, 30f, paint)
                rect(canvas, 14f, 7f, 33f, 15f, hair, 5f)
                paint.color = brown
                canvas.drawCircle(20f, 20f, 1.2f, paint); canvas.drawCircle(28f, 20f, 1.2f, paint)
                line(canvas, 22f, 25f, 26f, 25f, rose, 1.5f)
                if (person == FarmVillager.LUCIE) {
                    rect(canvas, 17f, 34f, 31f, 47f, cream, 3f)
                    rect(canvas, 11f, 7f, 37f, 12f, cream, 3f)
                    rect(canvas, 15f, 2f, 33f, 10f, cream, 4f)
                } else if (person == FarmVillager.MALO) {
                    rect(canvas, 10f, 9f, 38f, 13f, Color.rgb(212, 174, 94), 3f)
                    rect(canvas, 16f, 3f, 32f, 11f, Color.rgb(212, 174, 94), 4f)
                } else {
                    paint.color = rose; canvas.drawCircle(33f, 12f, 4f, paint)
                    paint.color = cream; canvas.drawCircle(33f, 12f, 1.5f, paint)
                }
            }
            Kind.SHOP -> {
                rect(canvas, 6f, 39f, 44f, 43f, 0x22745233)
                rect(canvas, 10f, 17f, 40f, 40f, brown)
                rect(canvas, 12f, 18f, 38f, 38f, cream)
                rect(canvas, 27f, 25f, 35f, 39f, green)
                rect(canvas, 15f, 25f, 24f, 32f, Color.rgb(162, 210, 201))
                rect(canvas, 8f, 9f, 42f, 16f, rose)
                for (i in 0..4) rect(canvas, 7f + i * 7, 14f, 14f + i * 7, 23f, if (i % 2 == 0) rose else cream)
                rect(canvas, 13f, 33f, 25f, 39f, Color.rgb(185, 139, 81))
                paint.color = green; canvas.drawOval(14f, 28f, 20f, 35f, paint)
                paint.color = Color.rgb(231, 151, 64); canvas.drawCircle(22f, 33f, 3f, paint)
                paint.color = cream; canvas.drawCircle(33f, 32f, 1f, paint)
            }
            Kind.SEEDS -> {
                canvas.rotate(-7f, 24f, 24f)
                rect(canvas, 10f, 8f, 38f, 42f, brown)
                rect(canvas, 12f, 9f, 36f, 40f, cream)
                rect(canvas, 12f, 9f, 36f, 14f, rose)
                crop?.let { sprites?.crop(canvas, it, 0, 4, RectF(13f, 14f, 35f, 35f)) }
                line(canvas, 18f, 37f, 30f, 37f, green, 2f)
                canvas.rotate(7f, 24f, 24f)
            }
            Kind.MAP -> {
                rect(canvas, 6f, 10f, 42f, 39f, brown)
                rect(canvas, 8f, 11f, 40f, 37f, cream)
                rect(canvas, 11f, 14f, 21f, 23f, green)
                rect(canvas, 25f, 14f, 37f, 27f, Color.rgb(149, 174, 107))
                rect(canvas, 11f, 27f, 21f, 34f, Color.rgb(190, 148, 92))
                line(canvas, 24f, 12f, 24f, 35f, Color.rgb(225, 201, 145), 2f)
                paint.color = rose; canvas.drawCircle(32f, 30f, 4f, paint)
                paint.color = cream; canvas.drawCircle(32f, 30f, 1.5f, paint)
            }
            Kind.BACK -> {
                rect(canvas, 9f, 9f, 39f, 39f, cream, 12f)
                line(canvas, 30f, 24f, 17f, 24f, brown)
                line(canvas, 17f, 24f, 24f, 17f, brown)
                line(canvas, 17f, 24f, 24f, 31f, brown)
            }
            Kind.CLOSE -> {
                line(canvas, 18f, 18f, 30f, 30f, brown)
                line(canvas, 30f, 18f, 18f, 30f, brown)
            }
            Kind.COIN -> {
                paint.color = Color.rgb(175, 115, 40); canvas.drawCircle(24f, 25f, 15f, paint)
                paint.color = Color.rgb(247, 194, 78); canvas.drawCircle(24f, 23f, 14f, paint)
                paint.color = Color.rgb(255, 222, 126); canvas.drawCircle(24f, 23f, 10f, paint)
                line(canvas, 24f, 17f, 24f, 29f, Color.rgb(201, 143, 46), 3f)
            }
            Kind.CROP -> crop?.let { sprites?.crop(canvas, it, 0, 4, RectF(0f, 0f, 48f, 46f)) }
            Kind.CRATE -> {
                rect(canvas, 8f, 18f, 40f, 42f, Color.rgb(116, 78, 48), 5f)
                rect(canvas, 10f, 15f, 38f, 25f, Color.rgb(188, 132, 72), 4f)
                rect(canvas, 10f, 25f, 38f, 40f, Color.rgb(156, 101, 56), 4f)
                line(canvas, 13f, 29f, 35f, 37f, Color.rgb(104, 68, 42), 2f)
                line(canvas, 35f, 29f, 13f, 37f, Color.rgb(104, 68, 42), 2f)
                crop?.let { sprites?.crop(canvas, it, 0, 4, RectF(13f, 2f, 35f, 24f)) }
                paint.color = Color.rgb(255, 224, 91)
                val star = Path()
                for (i in 0 until 10) {
                    val a = -Math.PI / 2 + i * Math.PI / 5
                    val r = if (i % 2 == 0) 5.2f else 2.4f
                    val x = 36f + kotlin.math.cos(a).toFloat() * r
                    val y = 13f + kotlin.math.sin(a).toFloat() * r
                    if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close(); canvas.drawPath(star, paint)
            }
            Kind.MANURE -> {
                val burlap = Color.rgb(184, 151, 99)
                val muck = Color.rgb(88, 59, 34)
                val stink = Color.rgb(150, 176, 86)
                // The smell rises behind the sack, so the squiggles never cut across the burlap.
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.2f; paint.strokeCap = Paint.Cap.ROUND
                paint.color = stink
                for ((x, base) in listOf(14f to 16f, 24f to 14f, 34f to 16f)) {
                    val wisp = Path()
                    wisp.moveTo(x, base)
                    wisp.quadTo(x - 4f, base - 3f, x, base - 6f)
                    wisp.quadTo(x + 4f, base - 9f, x, base - 12f)
                    canvas.drawPath(wisp, paint)
                }
                paint.style = Paint.Style.FILL
                rect(canvas, 10f, 42f, 38f, 46f, 0x22745233, 2f)
                // The heap first: the sack body then hides its bottom half and leaves a mound showing.
                paint.color = muck; canvas.drawOval(15f, 15f, 33f, 27f, paint)
                paint.color = Color.rgb(66, 44, 26)
                canvas.drawCircle(21f, 19f, 2.2f, paint)
                canvas.drawCircle(27f, 20f, 1.6f, paint)
                rect(canvas, 12f, 23f, 36f, 43f, burlap, 8f)
                rect(canvas, 12f, 36f, 36f, 43f, Color.rgb(160, 129, 83), 8f)
                rect(canvas, 11f, 22f, 37f, 27f, Color.rgb(206, 177, 124), 4f)
                line(canvas, 19f, 30f, 19f, 40f, Color.rgb(156, 125, 80), 1.6f)
                line(canvas, 29f, 30f, 29f, 40f, Color.rgb(156, 125, 80), 1.6f)
                // Two flies, because it really does stink.
                paint.color = Color.rgb(58, 52, 40)
                canvas.drawCircle(8f, 12f, 1.2f, paint)
                canvas.drawCircle(40f, 9f, 1f, paint)
            }
            Kind.WATER -> {
                val blue = Color.rgb(112, 197, 232)
                val darkBlue = Color.rgb(55, 122, 170)
                val rim = Color.rgb(236, 250, 255)
                rect(canvas, 9f, 40f, 38f, 44f, 0x22745233, 2f)
                canvas.rotate(-10f, 24f, 26f)
                rect(canvas, 13f, 18f, 35f, 39f, darkBlue, 8f)
                rect(canvas, 15f, 20f, 33f, 37f, blue, 6f)
                rect(canvas, 16f, 18f, 32f, 23f, rim, 4f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = darkBlue
                canvas.drawArc(RectF(6f, 20f, 20f, 36f), 95f, 170f, false, paint)
                canvas.drawArc(RectF(28f, 22f, 46f, 39f), -85f, 145f, false, paint)
                line(canvas, 29f, 15f, 42f, 11f, darkBlue, 4f)
                line(canvas, 37f, 12f, 44f, 16f, darkBlue, 3f)
                paint.style = Paint.Style.FILL
                canvas.rotate(10f, 24f, 26f)
                paint.color = blue
                for ((x, y) in listOf(37f to 30f, 41f to 34f, 34f to 36f))
                    canvas.drawCircle(x, y, 2.1f, paint)
            }
        }
        stock?.let {
            rect(canvas, 27f, 32f, 47f, 47f, green, 6f)
            paint.color = Color.WHITE; paint.textSize = if (it > 99) 8f else 10f
            paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
            canvas.drawText(it.toString(), 37f, 43f, paint)
        }
        if (alert) {
            paint.style = Paint.Style.FILL; paint.color = rose
            canvas.drawCircle(38f, 10f, 9f, paint)
            paint.color = cream; paint.textSize = 12f
            paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
            canvas.drawText("i", 38f, 14.5f, paint)
        }
        canvas.restore()
    }

    private val sagePortraitColor = Color.rgb(220, 232, 195)
}
