package com.Atom2Universe.app.science.botany

import android.graphics.*
import androidx.core.graphics.ColorUtils

internal fun botanicalMix(a: Int, b: Int, amount: Float) = ColorUtils.blendARGB(a, b, amount.coerceIn(0f, 1f))

/** Small shared brush set. Every operation resets its shader so state cannot leak between organs. */
internal class BotanyInk(val canvas: Canvas) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun stroke(path: Path, color: Int, width: Float) {
        paint.shader = null; paint.color = color; paint.style = Paint.Style.STROKE
        paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
    }
    fun line(x: Float, y: Float, xx: Float, yy: Float, color: Int, width: Float) {
        paint.shader = null; paint.color = color; paint.style = Paint.Style.STROKE
        paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x,y,xx,yy,paint)
    }
    fun fill(path: Path, color: Int) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color; canvas.drawPath(path,paint)
    }
    fun wash(path: Path, x: Float, y: Float, xx: Float, yy: Float, colors: IntArray) {
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE
        paint.shader = LinearGradient(x,y,xx,yy,colors,null,Shader.TileMode.CLAMP)
        canvas.drawPath(path,paint); paint.shader = null
    }
    fun cloud(path: Path, x: Float, y: Float, radius: Float, colors: IntArray) {
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE
        paint.shader = RadialGradient(x,y,radius,colors,null,Shader.TileMode.CLAMP)
        canvas.drawPath(path,paint); paint.shader = null
    }
    fun oval(x: Float, y: Float, rx: Float, ry: Float, color: Int) = fill(Path().apply {
        addOval(x-rx,y-ry,x+rx,y+ry,Path.Direction.CW)
    },color)
    inline fun at(x: Float, y: Float, angle: Float = 0f, scale: Float = 1f, block: () -> Unit) {
        canvas.save(); canvas.translate(x,y); canvas.rotate(angle); canvas.scale(scale,scale)
        try { block() } finally { canvas.restore() }
    }
}
