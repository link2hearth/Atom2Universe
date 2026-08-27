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
 * La caméra suit le boulet pendant le vol puis recule pour montrer tout l'arc :
 * une machine bien réglée envoie à plus de deux cents mètres, et c'est justement
 * cette courbe-là qu'on veut voir.
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
        const val BUILD_VIEW_MARGIN = 8f
        const val FLIGHT_VIEW_MARGIN = 18f

        /** Fenêtre minimale en vol : un boulet rapide doit rester dedans. */
        const val FLIGHT_MIN_WIDTH = 90f

        /** Au-delà, on ne recule plus : la machine deviendrait un point. */
        const val MAX_VIEW_WIDTH = 300f
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
    private val pFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#6D4C41".toColorInt() }
    private val pBeam = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#A1887F".toColorInt() }
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
    /** La chape du contrepoids : une élingue d'acier, épaisse. */
    private val pStrap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
        color = "#78909C".toColorInt()
        strokeCap = Paint.Cap.ROUND
    }
    /** La fronde : une corde tressée, claire pour qu'on la suive dans le fouet. */
    private val pSling = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * dp
        color = "#E9C46A".toColorInt()
        strokeCap = Paint.Cap.ROUND
    }
    /** La fronde molle : elle pend, elle ne tire rien. */
    private val pSlingSlack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.argb(110, 233, 196, 106)
        strokeCap = Paint.Cap.ROUND
    }
    /** Le crochet de largage : rouge, c'est la pièce qui décide du tir. */
    private val pPin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * dp
        color = "#EF5350".toColorInt()
        strokeCap = Paint.Cap.ROUND
    }
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
        color = Color.argb(160, 220, 235, 255)
    }

    private val tmpPath = Path()
    private val corners = FloatArray(8)
    private val partPose = FloatArray(3)
    private val tip = FloatArray(2)
    private val butt = FloatArray(2)
    private val pin = FloatArray(2)

    private val starsX = FloatArray(40)
    private val starsY = FloatArray(40)
    private val starsR = FloatArray(40)

    // ── Saisie ────────────────────────────────────────────────────────────────

    private var draggingSling = false

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
        // Le cadrage suit la taille de la machine : un bras de 12 m ne tient pas dans
        // la fenêtre qui suffisait à un bras de 6 m.
        val machineWidth = game.config.beamLength + game.config.slingLength + BUILD_VIEW_MARGIN
        val targetWidth = when (game.phase) {
            TrebuchetGame.Phase.BUILD -> machineWidth
            // Une fois retombé, on recule pour montrer tout l'arc : c'est le moment
            // où le joueur juge sa machine.
            TrebuchetGame.Phase.RESULT ->
                max(machineWidth, abs(game.ball.x) + 20f).coerceAtMost(MAX_VIEW_WIDTH)
            // En vol, la fenêtre doit être assez large pour qu'un boulet à soixante
            // mètres par seconde ne la traverse pas en une demi-seconde.
            else -> max(machineWidth + FLIGHT_VIEW_MARGIN, FLIGHT_MIN_WIDTH)
        }
        val targetScale = width / targetWidth
        val tx: Float
        val ty: Float
        val follow: Float
        when (game.phase) {
            TrebuchetGame.Phase.BUILD -> {
                tx = game.pivotX + game.config.longArm * 0.15f
                ty = game.pivotY * 0.55f
                follow = 4.5f
            }
            TrebuchetGame.Phase.RESULT -> {
                tx = game.ball.x / 2f
                ty = game.pivotY * 0.6f
                follow = 3f
            }
            else -> {
                // On vise devant le boulet, d'autant plus loin qu'il va vite : le
                // cadrage anticipe au lieu de courir après.
                tx = game.ball.x + game.ball.vx * 0.4f
                ty = game.ball.y + game.ball.vy * 0.2f
                follow = 8f
            }
        }

        if (!camReady) {
            camX = tx; camY = ty; camScale = targetScale; camReady = true
            return
        }
        // Suivi souple : la caméra rattrape sa cible sans à-coups.
        val k = (dt * follow).coerceIn(0f, 1f)
        camX += (tx - camX) * k
        camY += (ty - camY) * k
        camScale += (targetScale - camScale) * (dt * 2.5f).coerceIn(0f, 1f)

        // Le sol reste toujours visible : on ne descend pas sous l'horizon.
        val halfH = height / 2f / camScale
        val minY = -1f + halfH
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
                // Tolérance exprimée en pixels : sur une grande machine, tout est plus
                // petit à l'écran et une marge en mètres deviendrait ridicule.
                val reach = 46f * dp / camScale
                draggingSling = hypot(wx - game.ball.x, wy - game.ball.y) < reach
            }
            MotionEvent.ACTION_MOVE -> synchronized(game) {
                // Faire glisser le boulet au sol allonge ou raccourcit la fronde : le
                // doigt donne l'abscisse, le jeu se charge de rester dans le domaine
                // permis.
                if (draggingSling) {
                    game.tipWorld(tip)
                    game.setSlingLength(hypot(tip[0] - wx, tip[1] - TrebuchetRules.BALL_RADIUS))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingSling) listener?.onMachineChanged()
                draggingSling = false
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
        drawStrap(canvas)
        drawBody(canvas, game.counterweight, pWeight, pWeightEdge)
        drawBody(canvas, game.beam, pBeam, pEdge)
        drawPin(canvas)
        drawSling(canvas)
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

        // Graduations à partir du pied de la machine. Le pas s'élargit quand on
        // recule : à trois cents mètres, une borne tous les dix mètres serait une
        // bouillie de traits.
        pTickLabel.textSize = 11f * dp
        val viewWidth = w / camScale
        val step = when {
            viewWidth > 180f -> 50f
            viewWidth > 70f -> 25f
            else -> 10f
        }
        val leftWorld = camX - viewWidth / 2f
        val rightWorld = camX + viewWidth / 2f
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
    }

    /** Le bâti : un A sous le pivot, purement décoratif mais il donne l'échelle. */
    private fun drawFrameAndPivot(canvas: Canvas) {
        val px = sx(game.pivotX)
        val py = sy(game.pivotY)
        val groundY = sy(0f)
        val spread = 0.30f * game.pivotY * camScale
        val legW = max(2f * dp, 0.06f * camScale)

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

        canvas.drawCircle(px, py, max(3f * dp, 0.10f * camScale), pPivot)
    }

    /** L'élingue qui suspend le contrepoids sous le bras court. */
    private fun drawStrap(canvas: Canvas) {
        game.buttWorld(butt)
        canvas.drawLine(
            sx(butt[0]), sy(butt[1]),
            sx(game.counterweight.x), sy(game.counterweight.y), pStrap
        )
    }

    /** Le crochet de largage, planté dans le prolongement de la pointe du bras. */
    private fun drawPin(canvas: Canvas) {
        game.tipWorld(tip)
        game.pinWorld(pin)
        canvas.drawLine(sx(tip[0]), sy(tip[1]), sx(pin[0]), sy(pin[1]), pPin)
    }

    /**
     * La fronde. Tant que la boucle est sur le crochet, elle relie la pointe au
     * boulet ; molle, elle s'efface, parce qu'une corde molle ne transmet rien et
     * qu'il faut que ça se voie.
     */
    private fun drawSling(canvas: Canvas) {
        if (game.ballFree) return
        game.tipWorld(tip)
        canvas.drawLine(
            sx(tip[0]), sy(tip[1]), sx(game.ball.x), sy(game.ball.y),
            if (game.sling.isSlack) pSlingSlack else pSling
        )
    }

    private fun drawBody(canvas: Canvas, b: PhysBody, fill: Paint, edge: Paint) {
        for (i in b.parts.indices) drawPart(canvas, b, i, fill, edge)
    }

    private fun drawPart(canvas: Canvas, b: PhysBody, part: Int, fill: Paint, edge: Paint) {
        val p = b.parts[part]
        if (p.shape == Shape.CIRCLE) {
            b.partWorld(part, partPose)
            val cx = sx(partPose[0])
            val cy = sy(partPose[1])
            val r = p.radius * camScale
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, edge)
            // Un rayon tracé, pour qu'on voie le boulet rouler.
            canvas.drawLine(
                cx, cy,
                cx + r * kotlin.math.cos(partPose[2]), cy - r * kotlin.math.sin(partPose[2]),
                edge
            )
            return
        }
        b.partCorners(part, corners)
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

        // Cercle discret autour du boulet : on le fait glisser au sol pour régler la
        // longueur de la fronde.
        canvas.drawCircle(
            sx(game.ball.x), sy(game.ball.y),
            TrebuchetRules.BALL_RADIUS * camScale + 8f * dp, pHandle
        )
        game.tipWorld(tip)
        canvas.drawText(
            "%.1f m".format(game.config.slingLength),
            sx((tip[0] + game.ball.x) / 2f), sy(game.ball.y) - 18f * dp, pHint
        )
    }
}
