package com.Atom2Universe.app.games.caves

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View

/** A static voxel landscape; no game renderer or textures are loaded by the menu. */
internal class CaveMenuArt(context: Context, private val variant: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val checkpoint = canvas.save()
        canvas.scale(width / 480f, height / 120f)
        val sky = when (variant) { 1 -> "#233C60"; 2 -> "#49332F"; else -> "#1C4143" }
        paint.shader = LinearGradient(0f, 0f, 480f, 120f,
            Color.parseColor(sky), Color.parseColor("#172834"), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 480f, 120f, paint)
        paint.shader = null
        paint.color = Color.parseColor(if (variant == 2) "#FFC083" else "#C1EDD7")
        canvas.drawCircle(391f, 29f, 14f, paint)
        for (i in 0..12) {
            val x = i * 42f - 14f
            val top = 54f + ((i * 17) % 29)
            rect(canvas, x, top, x + 44f, 120f, "#25444C")
        }
        for (i in 0..14) {
            val x = i * 36f - 18f
            val top = 91f + ((i * 11) % 17)
            rect(canvas, x, top, x + 37f, 120f, "#32575A")
        }
        when (variant) {
            1 -> {
                cube(canvas, 204f, 73f, 30f, "#97DCCB", "#4A9D9D", "#317482")
                cube(canvas, 264f, 88f, 30f, "#B4BBFF", "#8185C5", "#555D9B")
                cube(canvas, 234f, 43f, 30f, "#FFDC9B", "#D4A45F", "#A37649")
                cube(canvas, 324f, 78f, 20f, "#C8ADDE", "#997EB5", "#725C91")
            }
            2 -> {
                cube(canvas, 220f, 90f, 43f, "#C1A58D", "#8B766D", "#615B5D")
                cube(canvas, 306f, 90f, 43f, "#C1A58D", "#8B766D", "#615B5D")
                rect(canvas, 219f, 29f, 223f, 69f, "#FFE0B1")
                rect(canvas, 223f, 29f, 246f, 43f, "#F4A778")
                rect(canvas, 305f, 29f, 309f, 69f, "#D2F4FF")
                rect(canvas, 309f, 29f, 332f, 43f, "#8ABFE4")
            }
            else -> {
                cube(canvas, 244f, 91f, 48f, "#99C99A", "#668F75", "#466B61")
                rect(canvas, 238f, 41f, 248f, 77f, "#AC8866")
                cube(canvas, 243f, 38f, 27f, "#B2DDA5", "#72AE85", "#4B876D")
                cube(canvas, 321f, 98f, 25f, "#A9C6B7", "#738F8B", "#516A70")
            }
        }
        canvas.restoreToCount(checkpoint)
    }

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: String) {
        paint.color = Color.parseColor(color)
        c.drawRect(l, t, r, b, paint)
    }

    private fun cube(c: Canvas, x: Float, y: Float, s: Float, top: String, left: String, right: String) {
        fun face(color: String, vararg points: Float) {
            path.reset()
            path.moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
            path.close()
            paint.color = Color.parseColor(color)
            c.drawPath(path, paint)
        }
        face(top, x, y - s, x + s, y - s / 2, x, y, x - s, y - s / 2)
        face(left, x - s, y - s / 2, x, y, x, y + s, x - s, y + s / 2)
        face(right, x, y, x + s, y - s / 2, x + s, y + s / 2, x, y + s)
    }
}
