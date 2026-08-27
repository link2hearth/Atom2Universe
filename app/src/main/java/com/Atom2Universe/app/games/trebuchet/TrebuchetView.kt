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
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.graphics.toColorInt
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.Shape
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Affichage et pilotage du trébuchet.
 *
 * La simulation tourne sur son propre thread à pas fixe ; les événements tactiles
 * arrivent depuis le thread UI, d'où les blocs `synchronized(game)`.
 *
 * La caméra suit le boulet pendant le vol puis recule pour montrer tout l'arc :
 * une machine bien réglée envoie à plus de quatre cents mètres, et c'est justement
 * cette courbe-là qu'on veut voir. Hors du vol, le joueur reprend la main : il
 * fait défiler la distance au doigt et pince pour zoomer, entre deux butées —
 * juste derrière la machine et cinq cents mètres devant. Le sol, lui, reste calé
 * en bas de l'image : on ne monte pas la vue, on dézoome. Un double-appui rend le
 * cadrage à la caméra.
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

    // ── Caméra ───────────────────────────────────────────────────────────────

    private var camX = 0f
    private var camY = 2f
    private var camScale = 60f
    private var camReady = false

    /**
     * Le joueur tient le cadrage. Tant qu'il le tient, la caméra ne bouge plus
     * toute seule ; un changement de phase le lui reprend, parce qu'au départ d'un
     * tir c'est le boulet qui commande, et qu'à l'arrivée on veut voir tout l'arc.
     */
    private var manualCam = false
    private var camPhase = TrebuchetGame.Phase.BUILD

    /** Le cadrage ne s'attrape pas en vol : pendant le tir, la caméra suit. */
    private val cameraFree: Boolean
        get() = game.phase != TrebuchetGame.Phase.FLIGHT

    private companion object {
        const val FIXED_DT = 1f / 120f

        /** Marges autour de la machine, en mètres : la vue suit sa taille. */
        const val BUILD_VIEW_MARGIN = 8f
        const val FLIGHT_VIEW_MARGIN = 18f

        /** Fenêtre minimale en vol : un boulet rapide doit rester dedans. */
        const val FLIGHT_MIN_WIDTH = 90f

        /** Au-delà, le cadrage automatique ne recule plus. */
        const val MAX_VIEW_WIDTH = 560f

        /** Butée arrière : juste derrière la pointe du bras bandé, en mètres. */
        const val PAN_BACK_MARGIN = 6f

        /** Butée avant : le terrain de jeu s'arrête là, en mètres. */
        const val PAN_FRONT = 500f

        /** Zoom maximal : au plus près, la fenêtre fait cette largeur en mètres. */
        const val MIN_VIEW_WIDTH = 8f

        /**
         * Bande de terre gardée sous le sol, en dp — la place des bornes de distance.
         * En dp et non en mètres : c'est une marge d'affichage, elle n'a aucune raison
         * de grandir quand on zoome.
         */
        const val GROUND_INSET_DP = 34f

        /** Deux appuis rapprochés rendent le cadrage à la caméra. */
        const val DOUBLE_TAP_MS = 300L

        /** Sous ce déplacement, un doigt posé est un appui, pas un glissement. */
        const val DRAG_SLOP_DP = 9f
    }

    // ── Palette ──────────────────────────────────────────────────────────────

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

    // ── Saisie ───────────────────────────────────────────────────────────────

    private var draggingSling = false

    /** État du geste à un ou deux doigts : centre, écartement, chemin parcouru. */
    private var gestureCount = 0
    private var focusX = 0f
    private var focusY = 0f
    private var focusSpread = 0f
    private var dragTravel = 0f
    private var gestureLocked = false
    private var lastTapAt = 0L

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

    // ── Cycle de vie ─────────────────────────────────────────────────────────

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

    // ── Caméra ───────────────────────────────────────────────────────────────

    private fun updateCamera(dt: Float) {
        if (width == 0) return

        // Chaque changement de phase reprend le cadrage : on veut voir partir le
        // tir, puis voir l'arc entier, sans avoir à toucher l'écran.
        if (game.phase != camPhase) {
            camPhase = game.phase
            manualCam = false
        }
        if (manualCam && cameraFree) {
            clampCamera()
            return
        }

        val cfg = game.config
        // Le cadrage suit la taille de la machine : un bras de 18 m ne tient pas
        // dans la fenêtre qui suffisait à un bras de 8 m.
        val machineWidth = cfg.beamLength + cfg.slingLength + BUILD_VIEW_MARGIN
        // Et sa hauteur compte autant : couché, le téléphone n'a que deux cents
        // pixels de haut, et un cadrage réglé sur la seule largeur décapiterait la
        // machine. C'est là, et seulement là, que portrait et paysage diffèrent.
        val machineHeight = game.pivotY + cfg.shortArm + cfg.hangLength + 4f

        val targetScale: Float
        val tx: Float
        val ty: Float
        val follow: Float

        if (game.phase == TrebuchetGame.Phase.FLIGHT) {
            // En vol, la fenêtre doit être assez large pour qu'un boulet à cent
            // mètres par seconde ne la traverse pas en une demi-seconde.
            targetScale = width / max(machineWidth + FLIGHT_VIEW_MARGIN, FLIGHT_MIN_WIDTH)
            // On vise devant le boulet, d'autant plus loin qu'il va vite : le
            // cadrage anticipe au lieu de courir après.
            tx = game.ball.x + game.ball.vx * 0.4f
            ty = game.ball.y + game.ball.vy * 0.2f
            follow = 8f
        } else {
            val targetWidth: Float
            val targetHeight: Float
            if (game.phase == TrebuchetGame.Phase.RESULT) {
                // Une fois retombé, on recule pour montrer tout l'arc : c'est le
                // moment où le joueur juge sa machine.
                targetWidth = max(machineWidth, abs(game.ball.x) + 30f)
                    .coerceAtMost(MAX_VIEW_WIDTH)
                targetHeight = max(machineHeight, game.peakHeight + 12f)
                tx = game.ball.x / 2f
                follow = 3f
            } else {
                // Bandée, la machine s'étale côté arrière : pointe plongée derrière,
                // fronde couchée dessous. On décale le cadre du même côté.
                targetWidth = machineWidth
                targetHeight = machineHeight
                tx = game.pivotX - cfg.longArm * 0.15f
                follow = 4.5f
            }
            targetScale = min(
                width / targetWidth,
                (height - GROUND_INSET_DP * dp) / targetHeight
            ).coerceIn(minScale(), maxScale())
            ty = groundCamY(targetScale)
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

        if (cameraFree) {
            clampCamera()
        } else {
            // En vol, le boulet peut monter très haut et la caméra le suit : on
            // interdit seulement de passer sous l'horizon.
            val minY = -1f + height / 2f / camScale
            if (camY < minY) camY = minY
        }
    }

    /**
     * Les butées du cadrage libre : juste derrière la machine d'un côté, la ligne
     * des cinq cents mètres de l'autre. La hauteur, elle, ne se règle pas : hors du
     * vol, le sol est calé en bas de l'image et n'en bouge plus, zoom compris. Une
     * vue de tir se lit comme une gravure — l'horizon toujours à la même place, et
     * seule la distance qui défile.
     */
    private fun clampCamera() {
        camScale = camScale.coerceIn(minScale(), maxScale())
        val halfW = width / 2f / camScale

        val left = panLeft()
        val right = panRight()
        camX = if (right - left <= halfW * 2f) {
            // Dézoom complet : le terrain est plus étroit que la vue, on le centre.
            (left + right) / 2f
        } else {
            camX.coerceIn(left + halfW, right - halfW)
        }
        camY = groundCamY(camScale)
    }

    /**
     * L'ordonnée de caméra qui pose le sol au bas de l'image, la bande de terre des
     * bornes de distance gardée dessous. Elle ne dépend que de l'échelle : c'est ce
     * qui fait qu'un pincement zoome sur le sol au lieu de le faire glisser.
     */
    private fun groundCamY(scale: Float) = (height / 2f - GROUND_INSET_DP * dp) / scale

    /** Butée arrière : la pointe du bras bandé plonge de tout le bras long. */
    private fun panLeft(): Float = game.pivotX - game.config.longArm - PAN_BACK_MARGIN

    /** Butée avant : cinq cents mètres, ou le tir en cours s'il est allé plus loin. */
    private fun panRight(): Float =
        max(PAN_FRONT, game.shotDistance + 60f).coerceAtMost(TrebuchetRules.GROUND_RIGHT)

    /** Dézoom maximal : tout le terrain tient dans la largeur de l'écran. */
    private fun minScale(): Float = width / max(panRight() - panLeft(), MIN_VIEW_WIDTH)

    /** Zoom maximal : de quoi examiner le crochet de largage à la loupe. */
    private fun maxScale(): Float = width / MIN_VIEW_WIDTH

    private fun sx(x: Float) = (x - camX) * camScale + width / 2f
    private fun sy(y: Float) = height / 2f - (y - camY) * camScale

    private fun worldX(px: Float) = (px - width / 2f) / camScale + camX
    private fun worldY(py: Float) = camY - (py - height / 2f) / camScale

    // ── Saisie ───────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> synchronized(game) {
                val now = SystemClock.uptimeMillis()
                val doubleTap = now - lastTapAt < DOUBLE_TAP_MS
                lastTapAt = now
                draggingSling = false
                dragTravel = 0f
                gestureLocked = false
                if (doubleTap) {
                    // Deux appuis rapprochés : on rend le cadrage à la caméra, et le
                    // doigt qui traîne ensuite ne le lui reprend pas aussitôt.
                    manualCam = false
                    gestureLocked = true
                } else if (game.phase == TrebuchetGame.Phase.BUILD) {
                    // Tolérance exprimée en pixels : sur une grande machine, tout est
                    // plus petit à l'écran et une marge en mètres deviendrait ridicule.
                    val reach = 46f * dp / camScale
                    draggingSling = hypot(
                        worldX(event.x) - game.ball.x, worldY(event.y) - game.ball.y
                    ) < reach
                }
                readPointers(event, -1)
            }
            // Un deuxième doigt, c'est toujours la caméra : on lâche la fronde.
            MotionEvent.ACTION_POINTER_DOWN -> synchronized(game) {
                if (draggingSling) {
                    draggingSling = false
                    post { listener?.onMachineChanged() }
                }
                readPointers(event, -1)
            }
            MotionEvent.ACTION_MOVE -> synchronized(game) {
                if (draggingSling) {
                    // Faire glisser le boulet au sol allonge ou raccourcit la fronde : le
                    // doigt donne l'abscisse, le jeu se charge de rester dans le domaine
                    // permis.
                    game.tipWorld(tip)
                    game.setSlingLength(
                        hypot(tip[0] - worldX(event.x), tip[1] - TrebuchetRules.BALL_RADIUS)
                    )
                } else {
                    dragCamera(event)
                }
            }
            // Un doigt s'en va : on repart de ceux qui restent, sinon la vue saute.
            MotionEvent.ACTION_POINTER_UP -> synchronized(game) {
                readPointers(event, event.actionIndex)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingSling) listener?.onMachineChanged()
                draggingSling = false
                gestureCount = 0
            }
        }
        return true
    }

    /**
     * Glissement et pincement, d'un seul tenant : l'endroit du terrain posé sous
     * les doigts y reste. C'est ce qui fait qu'un zoom à deux doigts colle à l'image
     * au lieu de partir en vrille autour du centre de l'écran.
     */
    private fun dragCamera(event: MotionEvent) {
        val hadPointers = gestureCount > 0
        val prevX = focusX
        val prevSpread = focusSpread
        readPointers(event, -1)
        if (!hadPointers || gestureCount == 0) return

        // Seul l'écart horizontal compte : un doigt qui monte ne fait rien, ce n'est
        // pas un début de glissement.
        dragTravel += abs(focusX - prevX)
        if (gestureLocked || !cameraFree) return
        val pinching = gestureCount >= 2 && prevSpread > 1f && focusSpread > 1f
        if (!pinching && dragTravel < DRAG_SLOP_DP * dp) return

        // L'abscisse du monde visée, mesurée avant de changer d'échelle : c'est elle
        // qui restera sous les doigts. La hauteur, elle, appartient au sol.
        val wx = worldX(prevX)
        if (pinching) {
            camScale = (camScale * (focusSpread / prevSpread)).coerceIn(minScale(), maxScale())
        }
        camX = wx - (focusX - width / 2f) / camScale
        manualCam = true
        camReady = true
        clampCamera()
    }

    /**
     * Relève la position moyenne des doigts et leur écartement, en ignorant
     * éventuellement celui qui est en train de se lever. Au-delà de deux, les doigts
     * supplémentaires n'apprennent plus rien.
     */
    private fun readPointers(event: MotionEvent, skip: Int) {
        var n = 0
        var x0 = 0f; var y0 = 0f
        var x1 = 0f; var y1 = 0f
        for (i in 0 until event.pointerCount) {
            if (i == skip) continue
            if (n == 0) {
                x0 = event.getX(i); y0 = event.getY(i)
            } else if (n == 1) {
                x1 = event.getX(i); y1 = event.getY(i)
            }
            n++
        }
        gestureCount = min(n, 2)
        when (gestureCount) {
            0 -> focusSpread = 0f
            1 -> {
                focusX = x0; focusY = y0; focusSpread = 0f
            }
            else -> {
                focusX = (x0 + x1) / 2f
                focusY = (y0 + y1) / 2f
                focusSpread = hypot(x0 - x1, y0 - y1)
            }
        }
    }

    // ── Rendu ────────────────────────────────────────────────────────────────

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
        drawRecenterHint(canvas)
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

    /**
     * Quand le joueur s'est éloigné au point de perdre la machine de vue, on lui
     * rappelle comment revenir : rien, à l'écran, ne laisse deviner le double-appui.
     */
    private fun drawRecenterHint(canvas: Canvas) {
        if (!manualCam || !cameraFree) return
        val px = sx(game.pivotX)
        if (px > 0f && px < width) return
        pHint.textSize = 12f * dp
        canvas.drawText(
            context.getString(R.string.trebuchet_recenter), width / 2f, 24f * dp, pHint
        )
    }
}
