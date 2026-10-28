package com.Atom2Universe.app.games.papattes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * L'éclosion de l'œuf mystère, en deux gestes :
 * 1. frotter l'œuf pour le réchauffer : il rougeoie, tremble et se fendille ; laissé tranquille,
 *    il refroidit doucement ;
 * 2. une fois chaud, la coquille se casse en morceaux : on les attrape au doigt et on les jette.
 *    La bestiole apparaît derrière.
 */
class PapattesHatchView(context: Context) : View(context) {
    enum class Phase { RUB, PEEL, DONE }

    /** La bestiole cachée dans l'œuf. */
    var critter: Critter = Critter.STARTER
        set(value) { field = value; reset() }
    var onDone: (() -> Unit)? = null
    var phase = Phase.RUB; private set
    /** Chaleur de l'œuf, de 0 à 1. */
    var warmth = 0f; private set

    private val sprites = PapattesSprites.get(context)
    private val art = PapattesArt(sprites)
    private val pose = PapattesPose()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = 0xFF5A3A20.toInt()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eggRect = RectF()
    private val shifted = RectF()
    private val path = Path()
    private var lastFrame = 0L
    private var time = 0f
    private var shake = 0f
    private var rubSpeed = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var rubbing = false
    private var tick = 0f
    private var doneTime = 0f
    private var velocity: VelocityTracker? = null

    /** Les fissures, en coordonnées 0..1 de l'œuf : des lignes brisées qui partent du milieu. */
    private val cracks = ArrayList<FloatArray>()

    /** Un morceau de coquille : un polygone en coordonnées 0..1 de l'œuf. */
    private class Piece(val points: FloatArray, val cx: Float, val cy: Float) {
        var dx = 0f; var dy = 0f; var angle = 0f
        var vx = 0f; var vy = 0f; var spin = 0f
        var flying = false; var life = 1f
        val gone get() = flying && life <= 0f
    }
    private val pieces = ArrayList<Piece>()
    private var grabbed: Piece? = null

    private class Spark { var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var life = 0f; var color = 0 }
    private val sparks = Array(48) { Spark() }
    private val rnd = Random(SystemClock.uptimeMillis())

    init {
        isClickable = true
        contentDescription = context.getString(R.string.papattes_hatch_description)
        reset()
    }

    private fun reset() {
        phase = Phase.RUB
        warmth = 0f
        shake = 0f
        grabbed = null
        doneTime = 0f
        val r = Random(critter.code + 11)
        cracks.clear()
        for (i in 0 until CRACKS) {
            // Chaque fissure part près du milieu de l'œuf et file en zigzag vers le bord.
            val a = (i / CRACKS.toFloat()) * 6.283f + r.nextFloat() * .6f
            val n = 6
            val pts = FloatArray(n * 2)
            var x = .5f + (r.nextFloat() - .5f) * .1f
            var y = .55f + (r.nextFloat() - .5f) * .1f
            for (k in 0 until n) {
                pts[k * 2] = x; pts[k * 2 + 1] = y
                val zig = if (k % 2 == 0) .5f else -.5f
                x += cos(a + zig) * .07f
                y += sin(a + zig) * .08f
            }
            cracks += pts
        }
        buildPieces(r)
        invalidate()
    }

    /** Une grille 3 × 4 aux sommets décalés : chaque case est un morceau aux bords irréguliers. */
    private fun buildPieces(r: Random) {
        pieces.clear()
        val cols = 3
        val rows = 4
        val gx = Array(cols + 1) { FloatArray(rows + 1) }
        val gy = Array(cols + 1) { FloatArray(rows + 1) }
        for (i in 0..cols) for (j in 0..rows) {
            val inner = i in 1 until cols && j in 1 until rows
            gx[i][j] = i / cols.toFloat() + if (inner) (r.nextFloat() - .5f) * .14f else 0f
            gy[i][j] = j / rows.toFloat() + if (inner) (r.nextFloat() - .5f) * .12f else 0f
        }
        for (i in 0 until cols) for (j in 0 until rows) {
            val corners = listOf(gx[i][j] to gy[i][j], gx[i + 1][j] to gy[i + 1][j], gx[i + 1][j + 1] to gy[i + 1][j + 1], gx[i][j + 1] to gy[i][j + 1])
            // Un point brisé au milieu de chaque bord, pour des bords en dents de scie.
            val pts = ArrayList<Float>()
            for (k in 0 until 4) {
                val (ax, ay) = corners[k]
                val (bx, by) = corners[(k + 1) % 4]
                pts += ax; pts += ay
                val edgeSeed = Random(((minOf(ax, bx) * 1000).toInt() * 31 + (minOf(ay, by) * 1000).toInt()) + critter.code)
                pts += (ax + bx) / 2 + (edgeSeed.nextFloat() - .5f) * .05f
                pts += (ay + by) / 2 + (edgeSeed.nextFloat() - .5f) * .05f
            }
            val cx = (gx[i][j] + gx[i + 1][j + 1]) / 2
            val cy = (gy[i][j] + gy[i + 1][j + 1]) / 2
            pieces += Piece(pts.toFloatArray(), cx, cy)
        }
    }

    /** Le morceau tient-il surtout de la coquille ? Les coins de la grille sont presque vides. */
    private fun Piece.mostlyShell(): Boolean {
        val ex = (cx - .5f) / .5f
        val ey = (cy - .55f) / .5f
        return ex * ex + ey * ey < .9f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val bmp = sprites.egg.bitmap
        val eh = min(h * .62f, w * .7f * bmp.height / bmp.width)
        val ew = eh * bmp.width / bmp.height
        eggRect.set((w - ew) / 2, h * .46f - eh / 2, (w + ew) / 2, h * .46f + eh / 2)
    }

    private fun ex(u: Float) = eggRect.left + u * eggRect.width()
    private fun ey(v: Float) = eggRect.top + v * eggRect.height()

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceIn(0f, .05f)
        lastFrame = now
        update(dt)

        // Halo chaud derrière l'œuf, qui grandit avec la chaleur.
        val glow = if (phase == Phase.RUB) warmth else 1f
        if (glow > 0f) {
            fill.shader = RadialGradient(eggRect.centerX(), eggRect.centerY(), eggRect.height() * (.55f + .35f * glow),
                (((180 * glow).toInt() shl 24) or 0xFFB347), 0x00FFB347, Shader.TileMode.CLAMP)
            canvas.drawCircle(eggRect.centerX(), eggRect.centerY(), eggRect.height() * (.55f + .35f * glow), fill)
            fill.shader = null
        }
        if (phase != Phase.RUB) drawCritter(canvas)

        canvas.save()
        val wobble = (sin(time * 30f) * rubSpeed * 4f + sin(time * 55f) * shake * 6f).coerceIn(-12f, 12f)
        canvas.rotate(wobble, eggRect.centerX(), eggRect.bottom)
        if (phase == Phase.RUB) {
            canvas.drawBitmap(sprites.egg.bitmap, null, eggRect, paint)
            drawCracks(canvas, warmth)
        } else {
            for (p in pieces) if (!p.gone) drawPiece(canvas, p)
        }
        canvas.restore()
        for (s in sparks) if (s.life > 0f) {
            fill.color = s.color
            fill.alpha = (255 * s.life.coerceIn(0f, 1f)).toInt()
            canvas.drawCircle(s.x, s.y, eggRect.width() * .02f, fill)
        }
        fill.alpha = 255
        if (isShown && (phase != Phase.DONE || doneTime < 3f || sparks.any { it.life > 0f })) postInvalidateOnAnimation()
        else if (isShown) postInvalidateDelayed(33)
    }

    private fun drawCracks(canvas: Canvas, amount: Float) {
        crackPaint.strokeWidth = eggRect.width() * .018f
        val total = cracks.size * 5
        var shown = (amount * 1.15f * total).toInt()
        for (c in cracks) {
            path.reset()
            path.moveTo(ex(c[0]), ey(c[1]))
            var any = false
            for (k in 1 until c.size / 2) {
                if (shown <= 0) break
                path.lineTo(ex(c[k * 2]), ey(c[k * 2 + 1]))
                shown--
                any = true
            }
            if (any) canvas.drawPath(path, crackPaint)
        }
    }

    private fun piecePath(p: Piece) {
        path.reset()
        for (k in 0 until p.points.size / 2) {
            val x = ex(p.points[k * 2]) + p.dx
            val y = ey(p.points[k * 2 + 1]) + p.dy
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun drawPiece(canvas: Canvas, p: Piece) {
        canvas.save()
        val px = ex(p.cx) + p.dx
        val py = ey(p.cy) + p.dy
        canvas.rotate(p.angle, px, py)
        piecePath(p)
        canvas.save()
        canvas.clipPath(path)
        paint.alpha = (255 * p.life.coerceIn(0f, 1f)).toInt()
        shifted.set(eggRect.left + p.dx, eggRect.top + p.dy, eggRect.right + p.dx, eggRect.bottom + p.dy)
        canvas.drawBitmap(sprites.egg.bitmap, null, shifted, paint)
        paint.alpha = 255
        canvas.restore()
        // Le bord du morceau : la fêlure, seulement sur la coquille.
        if (p.mostlyShell()) {
            crackPaint.strokeWidth = eggRect.width() * .012f
            crackPaint.alpha = (255 * p.life.coerceIn(0f, 1f)).toInt()
            canvas.drawPath(path, crackPaint)
            crackPaint.alpha = 255
        }
        canvas.restore()
    }

    private fun drawCritter(canvas: Canvas) {
        val c = critter
        val standing = PapattesArt.standingHeight(c)
        val scale = eggRect.height() * .95f / standing
        val bob = if (phase == Phase.DONE) sin(time * 5f) * .03f else 0f
        val hipY = eggRect.bottom - (c.legs.length * cos(PapattesWalker.START_SPREAD) + c.legs.footRadius) * scale
        pose.standing(c, eggRect.centerX(), hipY - bob * scale, scale, PapattesWalker.START_SPREAD + bob * 2f)
        pose.bodyAngle = bob
        art.critter(canvas, c, pose)
    }

    private fun update(dt: Float) {
        time += dt
        shake = (shake - dt * 2.5f).coerceAtLeast(0f)
        rubSpeed = (rubSpeed - dt * 3f).coerceAtLeast(0f)
        for (s in sparks) if (s.life > 0f) {
            s.life -= dt * 1.4f
            s.vy += eggRect.height() * .9f * dt
            s.x += s.vx * dt; s.y += s.vy * dt
        }
        when (phase) {
            Phase.RUB -> if (!rubbing) warmth = (warmth - COOL_RATE * dt).coerceAtLeast(0f)
            Phase.PEEL -> {
                for (p in pieces) if (p.flying && p.life > 0f) {
                    p.vy += eggRect.height() * 2.2f * dt
                    p.dx += p.vx * dt; p.dy += p.vy * dt
                    p.angle += p.spin * dt
                    p.life -= dt * .9f
                }
                if (pieces.all { it.flying } && pieces.all { it.gone || it.life < .3f }) {
                    phase = Phase.DONE
                    burst(eggRect.centerX(), eggRect.centerY(), 30, 0xFFFFD34D.toInt())
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onDone?.invoke()
                }
            }
            Phase.DONE -> doneTime += dt
        }
    }

    private fun burst(x: Float, y: Float, count: Int, color: Int) {
        var made = 0
        for (s in sparks) {
            if (made >= count) break
            if (s.life > 0f) continue
            s.x = x; s.y = y
            val a = rnd.nextFloat() * 6.283f
            val v = eggRect.height() * (.4f + rnd.nextFloat() * .8f)
            s.vx = cos(a) * v; s.vy = sin(a) * v - eggRect.height() * .5f
            s.life = .7f + rnd.nextFloat() * .5f
            s.color = color
            made++
        }
    }

    private fun crack() {
        phase = Phase.PEEL
        shake = 1f
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        burst(eggRect.centerX(), eggRect.centerY(), 16, 0xFFFFF1C2.toInt())
        // Les éclats presque vides des coins sautent tout seuls.
        for (p in pieces) if (!p.mostlyShell()) fling(p, 0f, 0f)
    }

    private fun fling(p: Piece, vx: Float, vy: Float) {
        val ox = p.cx - .5f
        val oy = p.cy - .55f
        val len = hypot(ox, oy).coerceAtLeast(.05f)
        val push = eggRect.height() * 1.2f
        p.vx = vx + ox / len * push
        p.vy = vy + oy / len * push - eggRect.height() * .8f
        p.spin = (rnd.nextFloat() - .5f) * 720f
        p.flying = true
    }

    private fun insidePiece(p: Piece, x: Float, y: Float): Boolean {
        var inside = false
        val n = p.points.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = ex(p.points[i * 2]) + p.dx; val yi = ey(p.points[i * 2 + 1]) + p.dy
            val xj = ex(p.points[j * 2]) + p.dx; val yj = ey(p.points[j * 2 + 1]) + p.dy
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x; lastY = y
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                when (phase) {
                    Phase.RUB -> rubbing = near(x, y)
                    Phase.PEEL -> grabbed = pieces.lastOrNull { !it.flying && insidePiece(it, x, y) }
                    Phase.DONE -> Unit
                }
                postInvalidateOnAnimation()
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                val dx = x - lastX
                val dy = y - lastY
                lastX = x; lastY = y
                when (phase) {
                    Phase.RUB -> if (near(x, y)) {
                        rubbing = true
                        val d = hypot(dx, dy)
                        warmth += d / (eggRect.height() * RUB_LENGTH)
                        rubSpeed = (rubSpeed + d / eggRect.height()).coerceAtMost(1f)
                        tick += d
                        if (tick > eggRect.height() * .6f) {
                            tick = 0f
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            burst(x, y, 2, 0xFFFFB347.toInt())
                        }
                        if (warmth >= 1f) { warmth = 1f; rubbing = false; crack() }
                    }
                    Phase.PEEL -> grabbed?.let { it.dx += dx; it.dy += dy }
                    Phase.DONE -> Unit
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.addMovement(event)
                velocity?.computeCurrentVelocity(1000)
                val p = grabbed
                if (p != null) {
                    fling(p, velocity?.xVelocity ?: 0f, velocity?.yVelocity ?: 0f)
                    burst(ex(p.cx) + p.dx, ey(p.cy) + p.dy, 4, 0xFFFFF4DA.toInt())
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                grabbed = null
                rubbing = false
                velocity?.recycle()
                velocity = null
            }
        }
        return true
    }

    private fun near(x: Float, y: Float): Boolean {
        val m = eggRect.width() * .25f
        return x > eggRect.left - m && x < eggRect.right + m && y > eggRect.top - m && y < eggRect.bottom + m
    }

    private companion object {
        const val CRACKS = 7
        /** Longueur de frottement pour réchauffer l'œuf, en hauteurs d'œuf. */
        const val RUB_LENGTH = 7f
        /** Refroidissement par seconde quand on ne frotte pas. */
        const val COOL_RATE = .06f
    }
}
