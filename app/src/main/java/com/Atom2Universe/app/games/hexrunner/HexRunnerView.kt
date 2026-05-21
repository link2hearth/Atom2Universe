package com.Atom2Universe.app.games.hexrunner

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*

class HexRunnerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    val game = HexRunnerGame()

    @Volatile private var running = false
    @Volatile private var pendingStart = false
    private var thread: Thread? = null

    private var lastSwipeX = 0f

    var onGameOver: (() -> Unit)? = null
    private var gameOverFired = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path  = Path()
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val colBg         = Color.parseColor("#070710")
    private val colSolid      = intArrayOf(0, 229, 192)
    private val colGap        = intArrayOf(30, 160, 50)
    private val colDanger     = intArrayOf(220, 50, 50)
    private val colCenter     = Color.parseColor("#0D1A28")
    private val colPlayer     = Color.WHITE
    private val colPlayerGlow = Color.parseColor("#5500E5C0")
    private val colScore      = Color.parseColor("#AAEEDD")

    init { holder.addCallback(this) }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this, "hex-runner").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        thread?.join(2000)
    }

    override fun run() {
        var lastNanos = System.nanoTime()
        while (running) {
            val now   = System.nanoTime()
            val dtMs  = ((now - lastNanos) / 1_000_000L).coerceAtMost(50L)
            lastNanos = now

            if (pendingStart) {
                pendingStart = false
                gameOverFired = false
                game.start()
            }
            if (game.isRunning) {
                game.update(dtMs)
                if (game.isGameOver && !gameOverFired) {
                    gameOverFired = true
                    onGameOver?.invoke()
                }
            }

            while (game.justPassedRings.isNotEmpty()) game.justPassedRings.removeFirst()

            val canvas = holder.lockCanvas()
            if (canvas != null) try { drawGame(canvas) } finally { holder.unlockCanvasAndPost(canvas) }

            val frameMs = (System.nanoTime() - now) / 1_000_000L
            if (frameMs < 16L) Thread.sleep(16L - frameMs)
        }
    }

    fun startGame() {
        pendingStart = true
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!game.isRunning) return true
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { lastSwipeX = e.x }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastSwipeX
                if (abs(dx) > 50f) {
                    if (dx < 0) game.rotateLeft() else game.rotateRight()
                    lastSwipeX = e.x
                }
            }
        }
        return true
    }

    private fun drawGame(canvas: Canvas) {
        canvas.drawColor(colBg)
        val N = game.ringCount
        if (N == 0) return

        val cx     = width  / 2f
        val cy     = height / 2f
        val pR     = minOf(cx, cy) * 0.88f
        val minR   = pR * 0.05f
        val angle  = computeHexAngle()

        paint.style = Paint.Style.FILL
        paint.color = colCenter
        canvas.drawCircle(cx, cy, minR * 2.5f, paint)

        for (i in (N - 1) downTo 0) {
            val fInner = ringFrac(i, N)
            val fOuter = if (i == 0) 1f else ringFrac(i - 1, N)
            val rInner = perspR(fInner, pR, minR)
            val rOuter = perspR(fOuter, pR, minR)
            if (rInner >= rOuter) continue

            val ring      = game.getRing(i)
            val proximity = fInner.coerceIn(0f, 1f)

            for (face in 0 until HexRunnerGame.NUM_FACES) {
                buildTrap(face, rInner, rOuter, cx, cy, angle)
                paint.style = Paint.Style.FILL
                val isGapFace = !ring.solid[face]
                paint.color = Color.argb(lerp(40f, 230f, proximity).toInt(), colSolid[0], colSolid[1], colSolid[2])
                canvas.drawPath(path, paint)
                paint.style       = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = Color.argb(lerp(5f, 90f, proximity).toInt(), 0, 160, 130)
                canvas.drawPath(path, paint)
                if (isGapFace) {
                    val rBandOuter = rInner + (rOuter - rInner) * 0.22f
                    buildTrap(face, rInner, rBandOuter, cx, cy, angle)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb(lerp(80f, 240f, proximity).toInt(), 210, 30, 30)
                    canvas.drawPath(path, paint)
                }
            }
        }

        paint.style       = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(100, 0, 200, 170)
        drawHexOutline(canvas, cx, cy, pR, angle)

        drawPlayer(canvas, cx, cy, pR, angle)
        drawHUD(canvas, cx)
    }

    private fun drawPlayer(canvas: Canvas, cx: Float, cy: Float, pR: Float, hexAngleDeg: Double) {
        val faceMidRad = rad(60.0 + game.playerFaceIndex * 60.0 + 30.0 + hexAngleDeg)
        val r  = pR * 0.91f
        val px = cx + r * cos(faceMidRad)
        val py = cy + r * sin(faceMidRad)

        val dx  = cx - px;  val dy  = cy - py
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val nx  =  dx / len;  val ny  =  dy / len
        val tx  = -ny;        val ty  =  nx

        val s = pR * 0.055f

        paint.style = Paint.Style.FILL
        paint.color = colPlayerGlow
        path.reset()
        path.moveTo(px + nx*s*1.7f,              py + ny*s*1.7f)
        path.lineTo(px - nx*s*0.9f + tx*s*1.4f, py - ny*s*0.9f + ty*s*1.4f)
        path.lineTo(px - nx*s*0.9f - tx*s*1.4f, py - ny*s*0.9f - ty*s*1.4f)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = colPlayer
        path.reset()
        path.moveTo(px + nx*s,                   py + ny*s)
        path.lineTo(px - nx*s*0.6f + tx*s*0.9f, py - ny*s*0.6f + ty*s*0.9f)
        path.lineTo(px - nx*s*0.6f - tx*s*0.9f, py - ny*s*0.6f - ty*s*0.9f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun buildTrap(
        faceIdx: Int, rInner: Float, rOuter: Float,
        cx: Float, cy: Float, hexAngleDeg: Double
    ) {
        val a1 = rad(60.0 + faceIdx * 60.0 + hexAngleDeg)
        val a2 = rad(60.0 + (faceIdx + 1) * 60.0 + hexAngleDeg)
        path.reset()
        path.moveTo(cx + rInner * cos(a1), cy + rInner * sin(a1))
        path.lineTo(cx + rInner * cos(a2), cy + rInner * sin(a2))
        path.lineTo(cx + rOuter * cos(a2), cy + rOuter * sin(a2))
        path.lineTo(cx + rOuter * cos(a1), cy + rOuter * sin(a1))
        path.close()
    }

    private fun drawHexOutline(canvas: Canvas, cx: Float, cy: Float, r: Float, hexAngleDeg: Double) {
        path.reset()
        for (v in 0 until 6) {
            val a = rad(60.0 + v * 60.0 + hexAngleDeg)
            if (v == 0) path.moveTo(cx + r * cos(a), cy + r * sin(a))
            else        path.lineTo(cx + r * cos(a), cy + r * sin(a))
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawHUD(canvas: Canvas, cx: Float) {
        if (!game.isRunning && !game.isGameOver) return
        scorePaint.textSize = width * 0.06f
        scorePaint.color    = colScore
        canvas.drawText(formatTime(game.score / 1000), cx, width * 0.10f, scorePaint)
    }

    private fun formatTime(secs: Long): String {
        val m = secs / 60; val s = secs % 60
        return if (m > 0) "%d:%02d".format(m, s) else "$s s"
    }

    private fun ringFrac(i: Int, N: Int): Float =
        (game.scrollProgress + N - 1 - i).toFloat() / N

    private fun perspR(f: Float, pR: Float, minR: Float): Float {
        val t = f.coerceIn(0f, 1f)
        return minR + (pR - minR) * t * t * t
    }

    private fun computeHexAngle(): Double {
        val target = -game.playerFaceIndex * 60.0
        val from   = -game.rotationFrom   * 60.0
        var delta  = target - from
        while (delta >  180.0) delta -= 360.0
        while (delta < -180.0) delta += 360.0
        return from + delta * easeInOut(game.rotationProgress)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun rad(deg: Double) = Math.toRadians(deg).toFloat()

    private fun easeInOut(t: Float): Double {
        val c = t.toDouble()
        return if (c < 0.5) 2.0 * c * c else -1.0 + (4.0 - 2.0 * c) * c
    }
}
