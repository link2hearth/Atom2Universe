package com.Atom2Universe.app.games.trebuchet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.graphics.toColorInt
import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.Shape
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

/**
 * Affichage et pilotage du trébuchet.
 *
 * La simulation tourne sur son propre thread à pas fixe ; les événements tactiles
 * arrivent depuis le thread UI, d'où les blocs `synchronized(game)`.
 *
 * La caméra suit le boulet pendant le vol puis revient cadrer le point de chute :
 * sans elle, une machine qui envoie à vingt mètres tirerait hors de l'écran.
 */
class TrebuchetView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : SurfaceView(ctx, attrs), SurfaceHolder.Callback, Runnable {

    interface Listener {
        /** Le tir est terminé : l'activité affiche la portée. */
        fun onShotFinished()

        /** Un réglage a bougé au doigt : l'activité rafraîchit ses textes. */
        fun onMachineChanged()
    }

    val game = TrebuchetGame()
    var listener: Listener? = null

    private var thread: Thread? = null
    @Volatile private var running = false
    private var lastNanos = 0L
    private var accumulator = 0f
    private var lastPhase = TrebuchetGame.Phase.BUILD

    private val dp = resources.displayMetrics.density

    // ── Caméra ────────────────────────────────────────────────────────────────

    private var camX = 0f
    private var camY = 2f
    private var camScale = 60f
    private var camReady = false

    private companion object {
        const val FIXED_DT = 1f / 120f

        /** Marges autour de la machine, en mètres : la vue suit sa taille. */
        const val BUILD_VIEW_MARGIN = 7f
        const val FLIGHT_VIEW_MARGIN = 14f
    }

    // ── Palette ───────────────────────────────────────────────────────────────

    private val bgTop = "#0A1024".toColorInt()
    private val bgBottom = "#16233F".toColorInt()

    private val pBg = Paint()
    private val pStar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 255, 255, 255) }
    private val pGround = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#1B2A1E".toColorInt() }
    private val pGrass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
        color = "#3E6B43".toColorInt()
    }
    private val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        color = Color.argb(90, 180, 220, 190)
    }
    private val pTickLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(150, 200, 230, 205)
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val pFiringLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.argb(160, 255, 180, 90)
        pathEffect = DashPathEffect(floatArrayOf(9f * dp, 7f * dp), 0f)
    }
    private val pFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#6D4C41".toColorInt() }
    private val pBeam = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#A1887F".toColorInt() }
    private val pCup = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#8D6E63".toColorInt() }
    private val pEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * dp
        color = "#4E342E".toColorInt()
    }
    private val pWeight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#546E7A".toColorInt() }
    private val pWeightEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = "#263238".toColorInt()
    }
    private val pBall = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#B0BEC5".toColorInt() }
    private val pBallEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = "#607D8B".toColorInt()
    }
    private val pStopper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#C62828".toColorInt() }
    private val pPivot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#37474F".toColorInt() }
    private val pTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        color = Color.argb(210, 255, 214, 120)
    }
    private val pGhost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.argb(90, 150, 190, 255)
        pathEffect = DashPathEffect(floatArrayOf(7f * dp, 6f * dp), 0f)
    }
    private val pHandle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.argb(120, 255, 214, 120)
        pathEffect = DashPathEffect(floatArrayOf(5f * dp, 5f * dp), 0f)
    }
    private val pHint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(150, 220, 235, 255)
    }

    private val tmpPath = Path()
    private val corners = FloatArray(8)

    private val starsX = FloatArray(40)
    private val starsY = FloatArray(40)
    private val starsR = FloatArray(40)

    // ── Saisie ────────────────────────────────────────────────────────────────

    private enum class Drag { NONE, BALL, WEIGHT }
    private var drag = Drag.NONE

    init {
        holder.addCallback(this)
        isFocusable = true
        val r = Random(11)
        for (i in starsX.indices) {
            starsX[i] = r.nextFloat()
            starsY[i] = r.nextFloat()
            starsR[i] = 0.6f + r.nextFloat() * 1.2f
        }
    }

    // ── Cycle de vie ──────────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) = resume()

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        camReady = false
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = pause()

    fun pause() {
        running = false
        thread?.join(1500)
        thread = null
    }

    fun resume() {
        if (running) return
        running = true
        lastNanos = System.nanoTime()
        accumulator = 0f
        thread = Thread(this, "TrebuchetPhysics").also { it.start() }
    }

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            val frameDt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.1f)
            lastNanos = now

            var finished = false
            synchronized(game) {
                accumulator += frameDt
                while (accumulator >= FIXED_DT) {
                    game.step(FIXED_DT)
                    accumulator -= FIXED_DT
                }
                if (game.phase != lastPhase) {
                    if (game.phase == TrebuchetGame.Phase.RESULT) finished = true
                    lastPhase = game.phase
                }
                updateCamera(frameDt)
            }
            if (finished) post { listener?.onShotFinished() }

            val canvas = holder.lockCanvas()
            if (canvas == null) {
                Thread.sleep(16)
                continue
            }
            try {
                synchronized(game) { drawFrame(canvas) }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            Thread.sleep(4)
        }
    }

    /** Remet le suivi de phase à zéro après un changement piloté par l'activité. */
    fun syncPhase() {
        synchronized(game) { lastPhase = game.phase }
    }

    // ── Caméra ────────────────────────────────────────────────────────────────

    private fun updateCamera(dt: Float) {
        if (width == 0) return
        // Le cadrage suit la taille de la machine : un bras de 8 m ne tient pas
        // dans la fenêtre qui suffisait à un bras de 4 m.
        val machineWidth = game.config.beamLength + BUILD_VIEW_MARGIN
        val targetWidth = when (game.phase) {
            TrebuchetGame.Phase.BUILD -> machineWidth
            TrebuchetGame.Phase.RESULT ->
                max(machineWidth, abs(game.ball.x - TrebuchetRules.FIRING_LINE) + 14f)
            else -> game.config.beamLength + FLIGHT_VIEW_MARGIN
        }
        val targetScale = width / targetWidth
        var tx: Float
        var ty: Float
        when (game.phase) {
            TrebuchetGame.Phase.BUILD -> {
                tx = game.pivotX - game.config.longArm * 0.35f
                ty = game.pivotY + 0.4f
            }
            TrebuchetGame.Phase.RESULT -> {
                tx = (TrebuchetRules.FIRING_LINE + game.ball.x) / 2f
                ty = game.pivotY + 0.4f
            }
            else -> {
                tx = game.ball.x + 2.5f
                ty = game.ball.y + 1f
            }
        }

        if (!camReady) {
            camX = tx; camY = ty; camScale = targetScale; camReady = true
            return
        }
        // Suivi souple : la caméra rattrape sa cible sans à-coups.
        val k = (dt * 4.5f).coerceIn(0f, 1f)
        camX += (tx - camX) * k
        camY += (ty - camY) * k
        camScale += (targetScale - camScale) * (dt * 2.5f).coerceIn(0f, 1f)

        // Le sol reste toujours visible : on ne descend pas sous l'horizon.
        val halfH = height / 2f / camScale
        val minY = -0.8f + halfH
        if (camY < minY) camY = minY
    }

    private fun sx(x: Float) = (x - camX) * camScale + width / 2f
    private fun sy(y: Float) = height / 2f - (y - camY) * camScale

    // ── Saisie ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wx = (event.x - width / 2f) / camScale + camX
        val wy = camY - (event.y - height / 2f) / camScale

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> synchronized(game) {
                if (game.phase != TrebuchetGame.Phase.BUILD) return true
                val dBall = hypot(wx - game.ball.x, wy - game.ball.y)
                val dWeight = hypot(wx - game.counterweight.x, wy - game.counterweight.y)
                // Tolérance exprimée en pixels : sur une grande machine, tout est
                // plus petit à l'écran et une marge en mètres deviendrait ridicule.
                val reach = 44f * dp / camScale
                drag = when {
                    dBall < reach && dBall <= dWeight -> Drag.BALL
                    dWeight < reach + 0.3f -> Drag.WEIGHT
                    else -> Drag.NONE
                }
            }
            MotionEvent.ACTION_MOVE -> synchronized(game) {
                when (drag) {
                    // Le boulet ne suit que la longueur du bras : le doigt donne
                    // l'abscisse, le jeu se charge de rester dans le domaine permis.
                    Drag.BALL -> game.setBallDistance(game.pivotX - wx)
                    Drag.WEIGHT -> {
                        val half = TrebuchetRules.counterweightHalfSize(game.config.counterweightMass)
                        val base = game.pivotY + TrebuchetRules.BEAM_HALF_THICKNESS + half
                        game.setDropHeight(wy - base)
                    }
                    Drag.NONE -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (drag != Drag.NONE) listener?.onMachineChanged()
                drag = Drag.NONE
            }
        }
        return true
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    private fun drawFrame(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        pBg.shader = LinearGradient(0f, 0f, 0f, h, bgTop, bgBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, pBg)
        for (i in starsX.indices) {
            canvas.drawCircle(starsX[i] * w, starsY[i] * h * 0.55f, starsR[i] * dp, pStar)
        }

        drawGround(canvas, w, h)
        drawGhost(canvas)
        drawFrameAndPivot(canvas)
        drawBody(canvas, game.beam, pBeam, pEdge)
        drawBody(canvas, game.cupBack, pCup, pEdge)
        drawBody(canvas, game.cupFrontBase, pCup, pEdge)
        drawBody(canvas, game.cupFrontTip, pCup, pEdge)
        drawStopper(canvas)
        if (game.counterweight.inWorld || game.phase == TrebuchetGame.Phase.BUILD) {
            drawBody(canvas, game.counterweight, pWeight, pWeightEdge)
        }
        drawBody(canvas, game.ball, pBall, pBallEdge)
        drawTrail(canvas)
        if (game.phase == TrebuchetGame.Phase.BUILD) drawBuildHints(canvas)
    }

    private fun drawGround(canvas: Canvas, w: Float, h: Float) {
        val groundY = sy(0f)
        if (groundY < h) {
            canvas.drawRect(0f, groundY, w, h, pGround)
            canvas.drawLine(0f, groundY, w, groundY, pGrass)
        }

        // Graduations tous les 5 m à partir de la ligne de tir.
        pTickLabel.textSize = 11f * dp
        val step = 5f
        val leftWorld = camX - (w / 2f) / camScale
        val rightWorld = camX + (w / 2f) / camScale
        var d = 0f
        while (TrebuchetRules.FIRING_LINE + d < rightWorld + step) {
            val x = TrebuchetRules.FIRING_LINE + d
            if (x > leftWorld - step) {
                val px = sx(x)
                canvas.drawLine(px, groundY, px, groundY + 10f * dp, pTick)
                if (d > 0f) {
                    canvas.drawText("${d.toInt()} m", px, groundY + 24f * dp, pTickLabel)
                }
            }
            d += step
        }

        // La ligne de tir : la machine doit rester derrière.
        val fx = sx(TrebuchetRules.FIRING_LINE)
        canvas.drawLine(fx, groundY, fx, groundY - 2.2f * camScale, pFiringLine)
    }

    /** Le bâti : un A sous le pivot, purement décoratif mais il donne l'échelle. */
    private fun drawFrameAndPivot(canvas: Canvas) {
        val px = sx(game.pivotX)
        val py = sy(game.pivotY)
        val groundY = sy(0f)
        val spread = 0.42f * camScale
        val legW = max(2f * dp, 0.05f * camScale)

        tmpPath.reset()
        tmpPath.moveTo(px - legW, py)
        tmpPath.lineTo(px - spread, groundY)
        tmpPath.lineTo(px - spread + legW * 2f, groundY)
        tmpPath.lineTo(px + legW, py)
        tmpPath.close()
        canvas.drawPath(tmpPath, pFrame)

        tmpPath.reset()
        tmpPath.moveTo(px - legW, py)
        tmpPath.lineTo(px + spread - legW * 2f, groundY)
        tmpPath.lineTo(px + spread, groundY)
        tmpPath.lineTo(px + legW, py)
        tmpPath.close()
        canvas.drawPath(tmpPath, pFrame)

        canvas.drawCircle(px, py, max(3f * dp, 0.09f * camScale), pPivot)
    }

    private fun drawStopper(canvas: Canvas) {
        canvas.drawCircle(
            sx(game.stopper.x), sy(game.stopper.y),
            TrebuchetRules.STOP_RADIUS * camScale, pStopper
        )
    }

    private fun drawBody(canvas: Canvas, b: PhysBody, fill: Paint, edge: Paint) {
        if (b.shape == Shape.CIRCLE) {
            val cx = sx(b.x)
            val cy = sy(b.y)
            val r = b.radius * camScale
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, edge)
            // Un rayon tracé, pour qu'on voie le boulet rouler.
            canvas.drawLine(
                cx, cy,
                cx + r * kotlin.math.cos(b.angle), cy - r * kotlin.math.sin(b.angle),
                edge
            )
            return
        }
        b.corners(corners)
        tmpPath.reset()
        tmpPath.moveTo(sx(corners[0]), sy(corners[1]))
        for (i in 1 until 4) tmpPath.lineTo(sx(corners[i * 2]), sy(corners[i * 2 + 1]))
        tmpPath.close()
        canvas.drawPath(tmpPath, fill)
        canvas.drawPath(tmpPath, edge)
    }

    private fun drawTrail(canvas: Canvas) {
        val t = game.trail
        if (t.size < 4) return
        tmpPath.reset()
        tmpPath.moveTo(sx(t[0]), sy(t[1]))
        var i = 2
        while (i < t.size) {
            tmpPath.lineTo(sx(t[i]), sy(t[i + 1]))
            i += 2
        }
        canvas.drawPath(tmpPath, pTrail)
    }

    /**
     * Le fantôme du tir précédent. Sans lui, corriger sa machine relève de la
     * superstition : avec, on voit de combien on a manqué.
     */
    private fun drawGhost(canvas: Canvas) {
        val g = game.ghost ?: return
        if (g.size < 4) return
        tmpPath.reset()
        tmpPath.moveTo(sx(g[0]), sy(g[1]))
        var i = 2
        while (i < g.size) {
            tmpPath.lineTo(sx(g[i]), sy(g[i + 1]))
            i += 2
        }
        canvas.drawPath(tmpPath, pGhost)
    }

    /** Repères de la phase de pose : ce qu'on peut attraper au doigt. */
    private fun drawBuildHints(canvas: Canvas) {
        pHint.textSize = 11f * dp

        // Trait entre le contrepoids suspendu et le bras : la hauteur de lâcher.
        val cw = game.counterweight
        val half = TrebuchetRules.counterweightHalfSize(game.config.counterweightMass)
        val topOfBeam = game.pivotY + TrebuchetRules.BEAM_HALF_THICKNESS
        canvas.drawLine(sx(cw.x), sy(cw.y - half), sx(cw.x), sy(topOfBeam), pHandle)
        canvas.drawText(
            "%.1f m".format(game.config.dropHeight),
            sx(cw.x) + 26f * dp, sy((cw.y - half + topOfBeam) / 2f), pHint
        )

        // Cercle discret autour du boulet : on peut le faire glisser sur le bras.
        canvas.drawCircle(
            sx(game.ball.x), sy(game.ball.y),
            TrebuchetRules.BALL_RADIUS * camScale + 7f * dp, pHandle
        )
    }
}
