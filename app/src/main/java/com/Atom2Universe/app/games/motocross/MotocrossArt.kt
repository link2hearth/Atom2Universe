package com.Atom2Universe.app.games.motocross

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.*

/** Rendu de la moto partagé entre le jeu et les illustrations fixes, sans simulation. */
internal class MotocrossArt {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private fun fill(color: Int) { ink.style = Paint.Style.FILL; ink.color = color; ink.shader = null }
    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, thickness: Float) {
        ink.style = Paint.Style.STROKE; ink.color = color; ink.strokeWidth = thickness; ink.shader = null
        canvas.drawLine(x1, y1, x2, y2, ink)
    }
    private fun circle(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        fill(color); canvas.drawCircle(x, y, radius, ink)
    }

    fun draw(canvas: Canvas, bike: MotocrossBike) {
        drawWheel(canvas, bike.rear)
        drawWheel(canvas, bike.front)
        canvas.save(); canvas.translate(bike.x, bike.y)
        canvas.rotate(Math.toDegrees(bike.angle.toDouble()).toFloat())
        val rearY = -MotocrossBike.REST + bike.rear.compression
        val frontY = -MotocrossBike.REST + bike.front.compression
        // Bras oscillant, amortisseur arrière et fourche télescopique.
        line(canvas, -.76f, rearY, -.10f, -.08f, STEEL, .10f)
        line(canvas, -.62f, rearY + .03f, -.22f, .23f, DARK, .13f)
        line(canvas, -.62f, rearY + .03f, -.22f, .23f, GOLD, .055f)
        for (i in 1..5) {
            val t = i / 7f
            val x = -.62f + .4f * t
            val y = rearY + .03f + (.20f - rearY) * t
            line(canvas, x - .055f, y + .035f, x + .055f, y - .035f, WHITE, .025f)
        }
        line(canvas, .76f, frontY, .50f, .28f, STEEL, .105f)
        line(canvas, .62f, frontY + (.28f - frontY) * .53f, .50f, .28f, GOLD, .13f)
        line(canvas, -.45f, .18f, -.08f, -.17f, MINT, .085f)
        line(canvas, -.08f, -.17f, .50f, .23f, MINT, .085f)
        line(canvas, .50f, .23f, -.45f, .18f, MINT, .09f)
        circle(canvas, -.03f, -.04f, .18f, DARK)
        circle(canvas, -.03f, -.04f, .11f, STEEL)
        line(canvas, -.51f, .28f, -.12f, .28f, DARK, .13f)
        line(canvas, -.78f, .20f, -.44f, .24f, MINT, .095f)
        line(canvas, .50f, .24f, .88f, .15f, MINT, .09f)
        line(canvas, .50f, .25f, .44f, .43f, STEEL, .055f)
        line(canvas, .44f, .43f, .62f, .43f, DARK, .07f)
        line(canvas, -.18f, -.15f, .05f, -.15f, STEEL, .055f)
        drawRider(canvas, bike)
        canvas.restore()
    }

    private fun drawWheel(canvas: Canvas, wheel: MotocrossBike.Wheel) {
        circle(canvas, wheel.x, wheel.y, MotocrossBike.RADIUS, DARK)
        ink.style = Paint.Style.STROKE; ink.color = STEEL; ink.strokeWidth = .035f
        canvas.drawCircle(wheel.x, wheel.y, .235f, ink)
        for (i in 0 until 10) {
            val a = wheel.spin + i * PI.toFloat() / 5f
            val ca = cos(a); val sa = sin(a)
            line(canvas, wheel.x + ca * .06f, wheel.y + sa * .06f,
                wheel.x + ca * .22f, wheel.y + sa * .22f, STEEL, .016f)
            line(canvas, wheel.x + ca * .287f, wheel.y + sa * .287f,
                wheel.x + ca * .319f, wheel.y + sa * .319f, Color.rgb(65, 72, 78), .04f)
        }
        circle(canvas, wheel.x, wheel.y, .065f, GOLD)
    }

    private fun drawRider(canvas: Canvas, bike: MotocrossBike) {
        val lean = bike.lean
        val crouch = bike.impact * .08f
        val hipX = -.25f + lean * .18f
        val hipY = .40f - crouch
        val shoulderX = .02f + lean * .29f
        val shoulderY = .83f - crouch
        // Les genoux et coudes se plient par cinématique inverse ; mains et pieds
        // restent sur leurs appuis pendant le déplacement du bassin.
        limb(canvas, hipX, hipY, -.02f, -.11f, .40f, .40f, 1f, Color.rgb(30, 46, 66), .13f)
        limb(canvas, shoulderX, shoulderY, .54f, .43f, .34f, .35f, -1f, Color.rgb(200, 115, 63), .10f)
        line(canvas, hipX, hipY, shoulderX, shoulderY, ORANGE, .25f)
        line(canvas, hipX - .05f, hipY + .05f, shoulderX - .06f, shoulderY - .03f, DARK, .075f)
        limb(canvas, hipX + .06f, hipY, .02f, -.13f, .40f, .40f, 1f, Color.rgb(57, 78, 99), .13f)
        limb(canvas, shoulderX + .04f, shoulderY - .02f, .57f, .43f, .34f, .35f, -1f, ORANGE, .095f)
        line(canvas, -.03f, -.12f, .17f, -.12f, DARK, .10f)
        circle(canvas, .57f, .43f, .06f, DARK)
        val headX = bike.headLocalX
        val headY = bike.headLocalY
        line(canvas, shoulderX, shoulderY, headX, headY - .08f, DARK, .11f)
        circle(canvas, headX, headY, .205f, WHITE)
        line(canvas, headX + .01f, headY + .06f, headX + .21f, headY + .04f, DARK, .11f)
        line(canvas, headX + .06f, headY + .16f, headX + .27f, headY + .10f, ORANGE, .055f)
        line(canvas, headX + .05f, headY - .12f, headX + .20f, headY - .06f, WHITE, .07f)
    }

    private fun limb(canvas: Canvas, ax: Float, ay: Float, bx: Float, by: Float,
                     upper: Float, lower: Float, bend: Float, color: Int, thickness: Float) {
        val dx = bx - ax; val dy = by - ay
        val distance = hypot(dx, dy).coerceAtLeast(.001f)
        val d = distance.coerceIn(abs(upper - lower) + .001f, upper + lower - .001f)
        val along = (upper * upper - lower * lower + d * d) / (2f * d)
        val side = sqrt(max(0f, upper * upper - along * along)) * bend
        val jointX = ax + dx / distance * along - dy / distance * side
        val jointY = ay + dy / distance * along + dx / distance * side
        line(canvas, ax, ay, jointX, jointY, color, thickness)
        line(canvas, jointX, jointY, bx, by, color, thickness * .84f)
        circle(canvas, jointX, jointY, thickness * .55f, color)
    }

    private companion object {
        val DARK = Color.rgb(17, 27, 39)
        val WHITE = Color.rgb(235, 242, 242)
        val STEEL = Color.rgb(151, 174, 187)
        val MINT = Color.rgb(101, 225, 194)
        val GOLD = Color.rgb(242, 195, 108)
        val ORANGE = Color.rgb(246, 147, 88)
    }
}
