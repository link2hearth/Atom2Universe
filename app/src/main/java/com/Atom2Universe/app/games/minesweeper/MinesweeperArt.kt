package com.Atom2Universe.app.games.minesweeper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Motifs communs au plateau et à son illustration dans les hubs. */
internal object MinesweeperArt {
    val navy = Color.rgb(18, 48, 65)
    val teal = Color.rgb(101, 227, 205)
    val amber = Color.rgb(245, 191, 105)
    val red = Color.rgb(255, 117, 131)

    fun drawBeacon(canvas: Canvas, rect: RectF, cs: Float, ink: Paint, flagPath: Path) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        ink.color = amber
        ink.alpha = 24
        canvas.drawCircle(cx, cy, cs * .34f, ink)
        ink.alpha = 255
        ink.strokeWidth = cs * .045f
        canvas.drawLine(cx - cs * .1f, cy + cs * .23f, cx - cs * .1f, cy - cs * .24f, ink)
        canvas.drawLine(cx - cs * .22f, cy + cs * .23f, cx + cs * .14f, cy + cs * .23f, ink)
        flagPath.reset()
        flagPath.moveTo(cx - cs * .06f, cy - cs * .24f)
        flagPath.lineTo(cx + cs * .25f, cy - cs * .1f)
        flagPath.lineTo(cx - cs * .06f, cy + cs * .02f)
        flagPath.close()
        canvas.drawPath(flagPath, ink)
    }

    fun drawMine(canvas: Canvas, rect: RectF, cs: Float, hit: Boolean, ink: Paint) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        if (hit) {
            ink.color = red
            ink.alpha = 60
            canvas.drawRoundRect(rect, cs * .12f, cs * .12f, ink)
            ink.alpha = 255
        }
        ink.color = red
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = cs * .04f
        canvas.drawCircle(cx, cy, cs * .23f, ink)
        canvas.drawLine(cx - cs * .33f, cy, cx + cs * .33f, cy, ink)
        canvas.drawLine(cx, cy - cs * .33f, cx, cy + cs * .33f, ink)
        ink.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, cs * .12f, ink)
    }

}
