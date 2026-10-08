package com.Atom2Universe.app.games.sokoban

import android.graphics.Canvas
import android.graphics.Paint

/** Dessin commun au jeu et à son illustration de hub, sans état Android View. */
internal class SokobanRenderer {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Long, radius: Float = .08f) {
        ink.color = color.toInt()
        ink.style = Paint.Style.FILL
        c.drawRoundRect(l, t, r, b, radius, radius, ink)
    }

    private fun line(c: Canvas, x: Float, y: Float, xx: Float, yy: Float, color: Long, width: Float = .035f) {
        ink.color = color.toInt()
        ink.strokeWidth = width
        ink.strokeCap = Paint.Cap.ROUND
        c.drawLine(x, y, xx, yy, ink)
    }

    private fun dot(c: Canvas, x: Float, y: Float, radius: Float, color: Long) {
        ink.color = color.toInt()
        c.drawCircle(x, y, radius, ink)
    }

    fun draw(
        canvas: Canvas,
        g: SokobanGame,
        cellSize: Float,
        offsetX: Float,
        offsetY: Float,
        progress: Float = 1f,
        previousPlayer: Int = g.player,
        pushedFrom: Int = -1,
        pushedTo: Int = -1,
        facing: SokobanDir = SokobanDir.DOWN
    ) {
        val p = g.puzzle ?: return
        if (cellSize <= 0f) return
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(cellSize, cellSize)
        rect(canvas, -.08f, -.08f, p.width + .08f, p.height + .08f, 0xFF0B171E, .2f)
        for (y in 0 until p.height) for (x in 0 until p.width) {
            val idx = y * p.width + x
            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            if (p.isWall(idx)) {
                rect(canvas, .02f, .07f, .98f, .99f, 0xFF10252D)
                rect(canvas, .02f, .02f, .98f, .83f, 0xFF35545B)
                rect(canvas, .08f, .08f, .92f, .71f, 0xFF3F6268)
                line(canvas, .14f, .13f, .85f, .13f, 0xFF71908A, .025f)
                line(canvas, .2f, .76f, .8f, .76f, 0xFF203D46)
                dot(canvas, .15f, .61f, .025f, 0xFF91A59B)
                dot(canvas, .85f, .61f, .025f, 0xFF91A59B)
            } else {
                rect(canvas, .025f, .025f, .975f, .975f,
                    if ((x + y) % 2 == 0) 0xFF20343D else 0xFF243B43, .04f)
                line(canvas, .12f, .92f, .88f, .92f, 0xFF152932, .02f)
                dot(canvas, .12f, .12f, .02f, 0xFF49616A)
                if (idx in p.goals) {
                    rect(canvas, .17f, .17f, .83f, .83f, 0xFF2C5D59, .14f)
                    ink.style = Paint.Style.STROKE
                    ink.color = 0xFF8EE4BF.toInt()
                    ink.strokeWidth = .035f
                    canvas.drawCircle(.5f, .5f, .23f, ink)
                    ink.style = Paint.Style.FILL
                    dot(canvas, .5f, .5f, .075f, 0xFFB8F6D4)
                    for (i in 0..3) {
                        canvas.save()
                        canvas.rotate(i * 90f, .5f, .5f)
                        line(canvas, .4f, .11f, .6f, .11f, 0xFF8EE4BF)
                        canvas.restore()
                    }
                }
            }
            canvas.restore()
        }
        for (box in g.boxes) {
            val from = if (box == pushedTo && progress < 1f) pushedFrom else box
            val x = from % p.width + (box % p.width - from % p.width) * progress
            val y = from / p.width + (box / p.width - from / p.width) * progress
            canvas.save()
            canvas.translate(x, y)
            drawCrate(canvas, box in p.goals)
            canvas.restore()
        }
        val from = if (progress < 1f) previousPlayer else g.player
        val px = from % p.width + (g.player % p.width - from % p.width) * progress
        val py = from / p.width + (g.player / p.width - from / p.width) * progress
        canvas.save()
        canvas.translate(px, py)
        // Petit robot sur chenilles, visière et antenne.
        rect(canvas, .18f, .73f, .84f, .91f, 0xFF102129, .12f)
        rect(canvas, .15f, .39f, .29f, .8f, 0xFF111F29)
        rect(canvas, .71f, .39f, .85f, .8f, 0xFF111F29)
        rect(canvas, .23f, .3f, .77f, .8f, 0xFFB67D48, .15f)
        rect(canvas, .23f, .23f, .77f, .69f, 0xFFF4DFAC, .15f)
        line(canvas, .5f, .24f, .5f, .12f, 0xFFF4DFAC)
        dot(canvas, .5f, .11f, .05f, 0xFF8EE4BF)
        rect(canvas, .3f, .34f, .7f, .54f, 0xFF16313C, .07f)
        val lookX = facing.dx * .025f
        val lookY = facing.dy * .018f
        dot(canvas, .41f + lookX, .435f + lookY, .035f, 0xFF9CEFD2)
        dot(canvas, .59f + lookX, .435f + lookY, .035f, 0xFF9CEFD2)
        line(canvas, .44f, .61f, .56f, .61f, 0xFFB67D48)
        canvas.restore()
        canvas.restore()
    }

    private fun drawCrate(c: Canvas, done: Boolean) {
        rect(c, .12f, .73f, .91f, .94f, 0xFF102129)
        rect(c, .13f, .18f, .87f, .86f, if (done) 0xFF377867 else 0xFF955A37)
        rect(c, .13f, .1f, .87f, .73f, if (done) 0xFF83CBA5 else 0xFFE2AE6C)
        rect(c, .23f, .2f, .77f, .64f, if (done) 0xFF438B74 else 0xFFB67B47, .025f)
        if (done) {
            line(c, .35f, .42f, .46f, .53f, 0xFFDAFFE6, .06f)
            line(c, .46f, .53f, .66f, .31f, 0xFFDAFFE6, .06f)
        } else {
            line(c, .28f, .25f, .72f, .59f, 0xFFF2C88C, .065f)
            line(c, .28f, .59f, .72f, .25f, 0xFFF2C88C, .065f)
        }
        for (x in listOf(.19f, .81f)) for (y in listOf(.16f, .67f))
            dot(c, x, y, .024f, 0xFF423D32)
    }

}
