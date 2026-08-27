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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
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

        /**
         * Un réglage a bougé au doigt, ou le joueur a pris une autre pièce en main :
         * l'activité rafraîchit son bandeau. Appelé depuis le thread UI.
         */
        fun onMachineChanged()
    }

    /**
     * Les pièces qu'on peut prendre en main. Une pièce sélectionnée s'éclaire et
     * sort ses poignées ; les autres se taisent. C'est ce qui permet à deux réglages
     * de partager un même point de la machine sans jamais se disputer le doigt —
     * l'axe, par exemple, règle la hauteur du pied ou la position du levier selon
     * la pièce qu'on tient.
     */
    enum class Part { NONE, BEAM, POST, WEIGHT, PIN, SLING }

    /** Ce qu'un doigt posé sur une pièce sélectionnée est en train de régler. */
    private enum class Grip {
        NONE, BEAM_LENGTH, LEVER, POST_HEIGHT, CW_MASS, CW_HANG, PIN_ANGLE, SLING_LENGTH
    }

    val game = TrebuchetGame()
    var listener: Listener? = null

    /** La pièce tenue en main, ou [Part.NONE]. L'activité la lit pour son bandeau. */
    var selected = Part.NONE
        private set

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

        /**
         * Tolérance de saisie, en dp. En dp et non en mètres : sur une grande machine
         * tout est plus petit à l'écran, et une marge en mètres deviendrait ridicule.
         * C'est aussi ce qui rend le zoom utile — zoomé, on règle finement.
         */
        const val PICK_REACH_DP = 30f

        /**
         * … mais jamais plus large que ça, en mètres. Vue de cinq cents mètres, une
         * tolérance de trente dp couvrirait la machine entière et le doigt
         * attraperait n'importe quoi.
         */
        const val PICK_REACH_MAX = 2f

        /** Longueur du départ prévisualisé, en mètres. Au-delà, il faudra tirer. */
        const val PREVIEW_METRES = 50f

        /** Ouverture du cône : il s'écarte de six pour cent de ce qu'il parcourt. */
        const val PREVIEW_SPREAD = 0.06f

        /** Le fantôme ne repart pas à chaque image : huit fois par seconde suffisent. */
        const val PREVIEW_PERIOD_MS = 120L

        /**
         * Pas de simulation accordés au fantôme par image. Un départ complet en
         * demande deux à quatre cents : les jouer d'un bloc ferait sauter l'affichage
         * à chaque cran de réglage, alors que le joueur a précisément les yeux dessus.
         */
        const val PREVIEW_BUDGET = 60

        /** Au-delà, la machine ne largue pas : inutile d'insister. */
        const val PREVIEW_MAX_STEPS = 480
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
    /**
     * Les peintures de la cible, une par matériau, plus leur trait de contour.
     *
     * Elles sont dans le même ordre que [Material], ce qui permet d'aller les chercher
     * par l'ordinal sans table intermédiaire.
     */
    private val targetFills = intArrayOf(
        "#C9A84C".toColorInt(), // chaume
        "#A8D8E8".toColorInt(), // glace
        "#B8916A".toColorInt(), // torchis
        "#7A6247".toColorInt(), // terre
        "#8B5E3C".toColorInt(), // bois
        "#9AA3AB".toColorInt(), // pierre
        "#54606B".toColorInt()  // fer
    ).map { c -> Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c } }

    private val targetEdges = intArrayOf(
        "#9B8038".toColorInt(),
        "#7FB3C6".toColorInt(),
        "#8D6B4A".toColorInt(),
        "#584734".toColorInt(),
        "#5F3F28".toColorInt(),
        "#6E767D".toColorInt(),
        "#39424A".toColorInt()
    ).map { c ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * dp
        }
    }

    /** Les fêlures : des traits sombres, d'autant plus nombreux que la pierre a pris. */
    private val pCrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 20, 16, 12)
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * dp
    }

    /** La ligne de ruine : l'énoncé du niveau, en pointillé rouge. */
    private val pRuin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FF6B6B".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        pathEffect = DashPathEffect(floatArrayOf(10f * dp, 8f * dp), 0f)
    }

    private val pRuinLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FF9D9D".toColorInt()
        textSize = 12f * dp
    }

    private val pTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        color = Color.argb(210, 255, 214, 120)
    }
    /** Le cône du départ : une nappe qui s'ouvre et s'éteint. */
    private val pCone = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pConeLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.argb(150, 255, 209, 102)
        pathEffect = DashPathEffect(floatArrayOf(9f * dp, 7f * dp), 0f)
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
    /** La pièce tenue en main : un liseré ambré, la couleur des cordes. */
    private val pSelect = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
        color = "#FFD166".toColorInt()
    }
    /** Les poignées qu'on peut tirer. */
    private val pGrip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#FFD166".toColorInt() }
    private val pGripEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = "#3E2723".toColorInt()
    }
    /** Les pastilles discrètes qui disent « il y a quelque chose à toucher ici ». */
    private val pSpot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 209, 102)
    }
    /** Les valeurs chiffrées, écrites au ras de la poignée qui les règle. */
    private val pValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = "#FFD166".toColorInt()
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
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
    private val pickTmp = FloatArray(2)
    private val axisA = FloatArray(2)
    private val axisB = FloatArray(2)

    private val starsX = FloatArray(40)
    private val starsY = FloatArray(40)
    private val starsR = FloatArray(40)

    // ── Saisie ───────────────────────────────────────────────────────────────

    /** Le réglage en cours de glissement, et l'écart doigt-valeur au moment où on
     *  l'a saisi : sans cet écart, attraper une poutre par le milieu la ferait
     *  sauter à la longueur du point touché. */
    private var grip = Grip.NONE
    private var gripOffset = 0f

    /** Le doigt s'est posé dans le vide : s'il n'a pas glissé, il désélectionne. */
    private var tappedVoid = false

    // ── Prévisualisation ──────────────────────────────────────────────────────

    /**
     * La machine fantôme : une seconde machine, identique à celle du joueur, qu'on
     * lâche en coulisse pour voir par où part le boulet. Elle donne le **vrai**
     * départ — pas une formule — et c'est ce qui fait bouger le cône sous le doigt
     * quand on incline le crochet.
     */
    private val ghostMachine = TrebuchetGame()
    private var previewPath = FloatArray(0)
    private var previewArc = FloatArray(0)
    private var previewSig = 0
    private var previewAt = 0L
    private var previewSteps = -1

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
                updatePreview()
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

    /** Repose la pièce tenue en main : un tir qui part n'a plus de réglage en cours. */
    fun clearSelection() {
        synchronized(game) {
            selected = Part.NONE
            grip = Grip.NONE
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
    private fun panRight(): Float {
        // La butée avant doit englober la cible, sinon le joueur ne peut littéralement
        // pas aller voir ce qu'il est en train de détruire.
        val cible = game.level?.let { game.targets.right + 40f } ?: 0f
        return max(max(PAN_FRONT, game.shotDistance + 60f), cible)
            .coerceAtMost(TrebuchetRules.GROUND_RIGHT)
    }

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
        var notify = false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> synchronized(game) {
                val now = SystemClock.uptimeMillis()
                val doubleTap = now - lastTapAt < DOUBLE_TAP_MS
                lastTapAt = now
                grip = Grip.NONE
                tappedVoid = false
                dragTravel = 0f
                gestureLocked = false
                if (doubleTap) {
                    // Deux appuis rapprochés : on lâche la pièce et on reprend du recul.
                    // Un seul geste, un seul sens.
                    if (selected != Part.NONE) {
                        selected = Part.NONE
                        notify = true
                    }
                    manualCam = false
                    gestureLocked = true
                } else {
                    notify = grabAt(worldX(event.x), worldY(event.y))
                }
                readPointers(event, -1)
            }
            // Un deuxième doigt, c'est toujours la caméra : on lâche le réglage en cours.
            MotionEvent.ACTION_POINTER_DOWN -> synchronized(game) {
                if (grip != Grip.NONE) {
                    grip = Grip.NONE
                    notify = true
                }
                readPointers(event, -1)
            }
            MotionEvent.ACTION_MOVE -> synchronized(game) {
                if (grip != Grip.NONE) {
                    applyGrip(worldX(event.x), worldY(event.y))
                    notify = true
                } else {
                    dragCamera(event)
                }
            }
            // Un doigt s'en va : on repart de ceux qui restent, sinon la vue saute.
            MotionEvent.ACTION_POINTER_UP -> synchronized(game) {
                readPointers(event, event.actionIndex)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Un appui dans le vide qui n'a pas glissé : le joueur pose la pièce.
                if (tappedVoid && dragTravel < DRAG_SLOP_DP * dp && selected != Part.NONE) {
                    synchronized(game) { selected = Part.NONE }
                    notify = true
                }
                if (grip != Grip.NONE) notify = true
                grip = Grip.NONE
                tappedVoid = false
                gestureCount = 0
            }
        }
        // Hors du verrou : l'activité relit la machine pour son bandeau.
        if (notify) listener?.onMachineChanged()
        return true
    }

    /**
     * Un doigt se pose. Trois cas : il tombe sur une poignée de la pièce déjà tenue
     * et on règle ; il tombe sur une autre pièce et on la prend en main, le même
     * geste pouvant enchaîner sur son réglage ; il tombe dans le vide, et c'est le
     * lever du doigt qui dira s'il s'agissait d'une déselection ou du début d'un
     * glissement de vue.
     */
    private fun grabAt(wx: Float, wy: Float): Boolean {
        if (selected != Part.NONE) {
            val g = pickGrip(selected, wx, wy)
            if (g != Grip.NONE) {
                startGrip(g, wx, wy)
                return true
            }
        }
        val part = pickPart(wx, wy)
        if (part == Part.NONE) {
            tappedVoid = true
            return false
        }
        // Attraper une pièce pendant ou après un tir rebande la machine : c'est ce que
        // le joueur veut dire. La caméra, elle, ne bouge pas d'un pouce — on ne déplace
        // pas la vue sous un doigt qui vient de se poser.
        if (game.phase != TrebuchetGame.Phase.BUILD) {
            game.rebuild()
            lastPhase = game.phase
            camPhase = game.phase
        }
        selected = part
        val g = pickGrip(part, wx, wy)
        if (g != Grip.NONE) startGrip(g, wx, wy)
        return true
    }

    /**
     * Saisit un réglage en mémorisant l'écart entre la valeur et ce que mesure le
     * doigt : sans lui, attraper une poutre par le milieu la ferait sauter à la
     * longueur du point touché.
     */
    private fun startGrip(g: Grip, wx: Float, wy: Float) {
        grip = g
        gripOffset = gripValue(g) - measureGrip(g, wx, wy)
    }

    /** La valeur du réglage, dans l'unité où le doigt la mesure. */
    private fun gripValue(g: Grip): Float {
        val cfg = game.config
        return when (g) {
            Grip.BEAM_LENGTH -> cfg.longArm
            Grip.LEVER -> cfg.shortArm
            Grip.POST_HEIGHT -> cfg.pivotHeight
            Grip.CW_MASS -> cfg.counterweightHalf
            Grip.CW_HANG -> cfg.hangLength
            Grip.PIN_ANGLE -> cfg.pinAngleDeg
            Grip.SLING_LENGTH -> cfg.slingLength
            Grip.NONE -> 0f
        }
    }

    /** Ce que le doigt désigne, dans la même unité que [gripValue]. */
    private fun measureGrip(g: Grip, wx: Float, wy: Float): Float = when (g) {
        // Le long de la poutre depuis l'axe : c'est la longueur du bras long.
        Grip.BEAM_LENGTH -> alongBeam(game.pivotX, game.pivotY, wx, wy)
        // Le long de la poutre depuis le talon : c'est la longueur du bras court.
        Grip.LEVER -> {
            game.buttWorld(pickTmp)
            alongBeam(pickTmp[0], pickTmp[1], wx, wy)
        }
        Grip.POST_HEIGHT -> wy
        Grip.CW_MASS -> max(abs(wx - game.counterweight.x), abs(wy - game.counterweight.y))
        Grip.CW_HANG -> {
            game.buttWorld(pickTmp)
            pickTmp[1] - wy
        }
        Grip.PIN_ANGLE -> pinAngleAt(wx, wy)
        Grip.SLING_LENGTH -> {
            game.tipWorld(pickTmp)
            hypot(pickTmp[0] - wx, pickTmp[1] - TrebuchetRules.BALL_RADIUS)
        }
        Grip.NONE -> 0f
    }

    /**
     * Pose la valeur désignée par le doigt. Le jeu se charge de la borner : un
     * réglage borné n'arrête pas le doigt, il arrête la machine.
     */
    private fun applyGrip(wx: Float, wy: Float) {
        val cfg = game.config
        val v = measureGrip(grip, wx, wy) + gripOffset
        when (grip) {
            // Le doigt tient le bras long ; la poutre entière en découle.
            Grip.BEAM_LENGTH -> game.setBeamLength(v * (1f + cfg.leverRatio) / cfg.leverRatio)
            // Le doigt fait coulisser la poutre dans son axe : le bras court change,
            // la longueur totale ne bouge pas.
            Grip.LEVER -> {
                val short = v.coerceIn(cfg.beamLength * 0.08f, cfg.beamLength * 0.45f)
                game.setLeverRatio((cfg.beamLength - short) / short)
            }
            Grip.POST_HEIGHT -> game.setPivotHeight(v)
            // Le côté de la caisse suit la racine de la masse : on remonte au carré.
            Grip.CW_MASS -> {
                val side = v.coerceAtLeast(0.01f) / 0.0105f
                game.setCounterweightMass(side * side)
            }
            Grip.CW_HANG -> game.setHangLength(v)
            Grip.PIN_ANGLE -> game.setPinAngle(v)
            Grip.SLING_LENGTH -> game.setSlingLength(v)
            Grip.NONE -> {}
        }
    }

    // ── Désignation ───────────────────────────────────────────────────────────

    /** La pièce sous le doigt, la plus petite d'abord : sinon la poutre prend tout. */
    private fun pickPart(wx: Float, wy: Float): Part {
        val reach = pickReach()
        game.pinWorld(pickTmp)
        if (hypot(wx - pickTmp[0], wy - pickTmp[1]) < reach) return Part.PIN
        if (hypot(wx - game.ball.x, wy - game.ball.y) < reach + TrebuchetRules.BALL_RADIUS) {
            return Part.SLING
        }
        val cw = game.counterweight
        val box = game.config.counterweightHalf + reach
        if (abs(wx - cw.x) < box && abs(wy - cw.y) < box) return Part.WEIGHT
        if (hypot(wx - game.pivotX, wy - game.pivotY) < reach) return Part.POST
        if (distanceToBeam(wx, wy) < reach) return Part.BEAM
        return Part.NONE
    }

    /**
     * La poignée visée sur la pièce tenue. C'est ici que deux réglages partagent une
     * pièce sans se marcher dessus : la poutre s'allonge par sa longueur et se
     * recentre par son collier, la caisse pend par son corps et s'alourdit par son
     * coin.
     */
    private fun pickGrip(part: Part, wx: Float, wy: Float): Grip {
        val reach = pickReach()
        val onAxle = hypot(wx - game.pivotX, wy - game.pivotY) < reach
        return when (part) {
            Part.BEAM -> when {
                onAxle -> Grip.LEVER
                distanceToBeam(wx, wy) < reach -> Grip.BEAM_LENGTH
                else -> Grip.NONE
            }
            Part.POST -> if (onAxle) Grip.POST_HEIGHT else Grip.NONE
            Part.WEIGHT -> {
                val cw = game.counterweight
                val h = game.config.counterweightHalf
                when {
                    hypot(wx - (cw.x + h), wy - (cw.y - h)) < reach -> Grip.CW_MASS
                    abs(wx - cw.x) < h + reach && abs(wy - cw.y) < h + reach -> Grip.CW_HANG
                    else -> Grip.NONE
                }
            }
            Part.PIN -> {
                game.pinWorld(pickTmp)
                if (hypot(wx - pickTmp[0], wy - pickTmp[1]) < reach) Grip.PIN_ANGLE else Grip.NONE
            }
            Part.SLING ->
                if (hypot(wx - game.ball.x, wy - game.ball.y) < reach + TrebuchetRules.BALL_RADIUS) {
                    Grip.SLING_LENGTH
                } else {
                    Grip.NONE
                }
            Part.NONE -> Grip.NONE
        }
    }

    /** La tolérance de saisie, en mètres, à l'échelle où l'on regarde. */
    private fun pickReach(): Float =
        (PICK_REACH_DP * dp / camScale).coerceAtMost(PICK_REACH_MAX)

    /** Projection du doigt sur l'axe de la poutre, comptée depuis un point donné. */
    private fun alongBeam(ox: Float, oy: Float, wx: Float, wy: Float): Float {
        game.buttWorld(axisA)
        game.tipWorld(axisB)
        val ux = axisB[0] - axisA[0]
        val uy = axisB[1] - axisA[1]
        val len = hypot(ux, uy)
        if (len < 0.001f) return 0f
        return ((wx - ox) * ux + (wy - oy) * uy) / len
    }

    /** Distance du doigt à la poutre, prise sur le segment talon-pointe. */
    private fun distanceToBeam(wx: Float, wy: Float): Float {
        game.buttWorld(axisA)
        game.tipWorld(axisB)
        val ux = axisB[0] - axisA[0]
        val uy = axisB[1] - axisA[1]
        val len2 = ux * ux + uy * uy
        if (len2 < 0.000001f) return hypot(wx - axisA[0], wy - axisA[1])
        val t = (((wx - axisA[0]) * ux + (wy - axisA[1]) * uy) / len2).coerceIn(0f, 1f)
        return hypot(wx - (axisA[0] + t * ux), wy - (axisA[1] + t * uy))
    }

    /**
     * L'inclinaison que le doigt donne au crochet, en degrés depuis l'axe du bras.
     * On repasse dans le repère de la poutre : c'est là que l'angle a un sens, et il
     * y garde le même quelle que soit la position du bras.
     */
    private fun pinAngleAt(wx: Float, wy: Float): Float {
        val b = game.beam
        val c = cos(b.angle)
        val si = sin(b.angle)
        val dx = wx - b.x
        val dy = wy - b.y
        val lx = dx * c + dy * si
        val ly = -dx * si + dy * c
        val a = atan2(ly, lx - game.config.beamLength / 2f)
        return Math.toDegrees(a.toDouble()).toFloat()
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

    // ── Prévisualisation ──────────────────────────────────────────────────────

    /**
     * Rejoue le départ sur la machine fantôme quand celle du joueur a changé.
     *
     * Deux garde-fous : on ne rejoue que si un réglage a bougé, et jamais plus de
     * huit fois par seconde. Un départ coûte quelques centaines de pas de solveur —
     * rien du tout de temps en temps, beaucoup trop à chaque image.
     */
    private fun updatePreview() {
        if (game.phase != TrebuchetGame.Phase.BUILD) return

        // Un réglage a bougé : on relâche un nouveau fantôme, mais pas plus souvent
        // que la cadence. Pendant un glissement, la machine change à chaque image.
        val sig = configSignature(game.config)
        val now = SystemClock.uptimeMillis()
        if (sig != previewSig && now - previewAt >= PREVIEW_PERIOD_MS) {
            previewAt = now
            previewSig = sig
            ghostMachine.config.copyFrom(game.config)
            ghostMachine.build()
            ghostMachine.release()
            previewSteps = 0
        }
        if (previewSteps < 0) return

        // Le départ se joue par tranches : le fantôme avance de quelques pas, puis
        // rend la main à l'affichage. La trace précédente reste visible en attendant,
        // ce qui vaut mieux qu'un cône qui clignote à chaque cran.
        var budget = PREVIEW_BUDGET
        while (budget > 0 && previewSteps < PREVIEW_MAX_STEPS &&
            ghostMachine.phase == TrebuchetGame.Phase.FLIGHT &&
            ghostMachine.ball.x - TrebuchetRules.FIRING_LINE < PREVIEW_METRES
        ) {
            ghostMachine.step(1f / 120f)
            previewSteps++
            budget--
        }
        if (budget > 0 || previewSteps >= PREVIEW_MAX_STEPS) {
            previewPath = ghostMachine.startTrace()
            previewSteps = -1
        }
    }

    /** L'empreinte des réglages : deux machines identiques ont la même. */
    private fun configSignature(c: MachineConfig): Int {
        var h = c.beamLength.toRawBits()
        h = h * 31 + c.pivotHeight.toRawBits()
        h = h * 31 + c.leverRatio.toRawBits()
        h = h * 31 + c.counterweightMass.toRawBits()
        h = h * 31 + c.hangLength.toRawBits()
        h = h * 31 + c.pinAngleDeg.toRawBits()
        h = h * 31 + c.slingRatio.toRawBits()
        return h
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
        drawTargets(canvas, w)
        drawGhost(canvas)
        drawStartCone(canvas)
        drawFrameAndPivot(canvas)
        drawStrap(canvas)
        drawBody(canvas, game.counterweight, pWeight, pWeightEdge)
        drawBody(canvas, game.beam, pBeam, pEdge)
        drawPin(canvas)
        drawSling(canvas)
        drawBody(canvas, game.ball, pBall, pBallEdge)
        drawTrail(canvas)
        drawGrabSpots(canvas)
        drawSelection(canvas)
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

    /**
     * La cible : chaque pierre de sa couleur, fêlée selon ce qu'elle a encaissé, et la
     * ligne de ruine en travers.
     *
     * On ne dessine que ce qui traverse l'écran : un site fait cent corps, et le joueur
     * passe le plus clair de son temps à regarder sa machine, trois cents mètres plus
     * loin.
     */
    private fun drawTargets(canvas: Canvas, w: Float) {
        val field = game.targets
        if (field.pieces.isEmpty()) return

        val viewWidth = w / camScale
        val leftWorld = camX - viewWidth / 2f - 5f
        val rightWorld = camX + viewWidth / 2f + 5f

        for (p in field.pieces) {
            val b = p.body
            if (b.x + b.boundingRadius < leftWorld || b.x - b.boundingRadius > rightWorld) continue
            val i = p.material.ordinal
            drawBody(canvas, b, targetFills[i], targetEdges[i])
            if (p.crackLevel > 0) drawCracks(canvas, b, p.crackLevel)
        }

        // La ligne de ruine, tracée d'un bout à l'autre de l'emprise.
        val y = sy(field.ruinLine)
        val x0 = sx(field.left - 3f)
        val x1 = sx(field.right + 3f)
        if (x1 > 0f && x0 < w) {
            canvas.drawLine(x0, y, x1, y, pRuin)
            canvas.drawText("ligne de ruine", x0 + 4f * dp, y - 6f * dp, pRuinLabel)
        }
    }

    /**
     * Les fêlures d'une pierre entamée : un trait au premier palier, une croix au
     * deuxième, une étoile au troisième.
     *
     * C'est le seul retour que le joueur ait sur un coup qui a porté sans casser, et
     * sans lui un tir qui enlève la moitié de la vie d'une assise ressemble exactement
     * à un tir qui n'a rien fait.
     */
    private fun drawCracks(canvas: Canvas, b: PhysBody, level: Int) {
        for (i in b.parts.indices) {
            val part = b.parts[i]
            if (part.shape == Shape.CIRCLE) continue
            b.partWorld(i, partPose)
            val cx = sx(partPose[0])
            val cy = sy(partPose[1])
            val hw = part.halfW * camScale * 0.8f
            val hh = part.halfH * camScale * 0.8f
            if (hw < 2f * dp || hh < 2f * dp) continue
            val a = -partPose[2]
            canvas.save()
            canvas.rotate(Math.toDegrees(a.toDouble()).toFloat(), cx, cy)
            canvas.drawLine(cx - hw, cy - hh * 0.4f, cx + hw * 0.2f, cy + hh, pCrack)
            if (level >= 2) canvas.drawLine(cx + hw, cy - hh, cx - hw * 0.3f, cy + hh * 0.5f, pCrack)
            if (level >= 3) {
                canvas.drawLine(cx - hw, cy + hh * 0.6f, cx + hw, cy + hh * 0.2f, pCrack)
                canvas.drawLine(cx - hw * 0.2f, cy - hh, cx + hw * 0.4f, cy + hh * 0.3f, pCrack)
            }
            canvas.restore()
        }
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

    /**
     * Le cône du départ : la trajectoire de la machine fantôme, épaissie d'autant
     * qu'elle s'éloigne, et éteinte au bout de cinquante mètres.
     *
     * L'ouverture n'est pas une marge d'erreur calculée, c'est une promesse tenue :
     * le trait central est juste, la nappe autour dit « à peu près par là », et le
     * fait qu'elle s'ouvre dit « plus loin, je ne sais plus ». Un jeu où l'on ne vise
     * pas ne doit pas afficher une ligne de mire.
     */
    private fun drawStartCone(canvas: Canvas) {
        if (game.phase != TrebuchetGame.Phase.BUILD) return
        val t = previewPath
        val n = t.size / 2
        if (n < 4) return

        // Longueur parcourue le long de la trace : c'est elle qui ouvre le cône.
        if (previewArc.size < n) previewArc = FloatArray(n)
        previewArc[0] = 0f
        for (i in 1 until n) {
            previewArc[i] = previewArc[i - 1] +
                hypot(t[i * 2] - t[(i - 1) * 2], t[i * 2 + 1] - t[(i - 1) * 2 + 1])
        }

        // Un bord à l'aller, l'autre au retour : la nappe se referme sur elle-même.
        tmpPath.reset()
        for (pass in 0..1) {
            val side = if (pass == 0) 1f else -1f
            var i = if (pass == 0) 0 else n - 1
            while (i in 0 until n) {
                // La normale se prend sur le segment voisin ; au dernier point, sur le
                // précédent, faute de suivant.
                val a = if (i < n - 1) i else i - 1
                val dx = t[(a + 1) * 2] - t[a * 2]
                val dy = t[(a + 1) * 2 + 1] - t[a * 2 + 1]
                val len = hypot(dx, dy).coerceAtLeast(0.0001f)
                val w = previewArc[i] * PREVIEW_SPREAD * side
                val px = sx(t[i * 2] - dy / len * w)
                val py = sy(t[i * 2 + 1] + dx / len * w)
                if (pass == 0 && i == 0) tmpPath.moveTo(px, py) else tmpPath.lineTo(px, py)
                i += if (pass == 0) 1 else -1
            }
        }
        tmpPath.close()

        pCone.shader = LinearGradient(
            sx(t[0]), sy(t[1]), sx(t[(n - 1) * 2]), sy(t[(n - 1) * 2 + 1]),
            Color.argb(70, 255, 209, 102), Color.argb(0, 255, 209, 102), Shader.TileMode.CLAMP
        )
        canvas.drawPath(tmpPath, pCone)

        // Le trait central, en pointillés : c'est lui qui est juste.
        tmpPath.reset()
        tmpPath.moveTo(sx(t[0]), sy(t[1]))
        for (i in 1 until n) tmpPath.lineTo(sx(t[i * 2]), sy(t[i * 2 + 1]))
        canvas.drawPath(tmpPath, pConeLine)
    }

    /**
     * Les pastilles de saisie. Sans elles, rien ne dirait que la machine se touche :
     * une pièce qui se règle doit se voir avant qu'on la prenne en main.
     */
    private fun drawGrabSpots(canvas: Canvas) {
        if (game.phase != TrebuchetGame.Phase.BUILD || selected != Part.NONE) return
        val r = 4f * dp
        // Au milieu du bras long, loin de l'axe et de la pointe : les trois repères
        // de la poutre ne doivent pas se confondre.
        game.tipWorld(tip)
        canvas.drawCircle(
            sx((tip[0] + game.pivotX) / 2f), sy((tip[1] + game.pivotY) / 2f), r, pSpot
        )
        canvas.drawCircle(sx(game.pivotX), sy(game.pivotY), r, pSpot)
        canvas.drawCircle(sx(game.counterweight.x), sy(game.counterweight.y), r, pSpot)
        game.pinWorld(pin)
        canvas.drawCircle(sx(pin[0]), sy(pin[1]), r, pSpot)
        canvas.drawCircle(sx(game.ball.x), sy(game.ball.y), r, pSpot)
    }

    /**
     * La pièce tenue en main : son liseré, ses poignées, ses valeurs. La machine
     * porte les chiffres, le bandeau du bas porte l'explication.
     */
    private fun drawSelection(canvas: Canvas) {
        if (selected == Part.NONE) return
        pValue.textSize = 12f * dp
        drawGuide(canvas)
        val cfg = game.config
        when (selected) {
            Part.BEAM -> {
                drawOutline(canvas, game.beam)
                game.tipWorld(tip)
                drawGrip(canvas, tip[0], tip[1])
                drawValue(canvas, tip[0], tip[1], "%.1f m".format(cfg.beamLength))
                drawGrip(canvas, game.pivotX, game.pivotY)
                drawValue(canvas, game.pivotX, game.pivotY, "%.1f:1".format(cfg.leverRatio))
            }
            Part.POST -> {
                // Le montant, du sol à l'axe : c'est lui qu'on étire.
                canvas.drawLine(
                    sx(game.pivotX), sy(0f), sx(game.pivotX), sy(game.pivotY), pSelect
                )
                drawGrip(canvas, game.pivotX, game.pivotY)
                drawValue(canvas, game.pivotX, game.pivotY, "%.1f m".format(cfg.pivotHeight))
            }
            Part.WEIGHT -> {
                drawOutline(canvas, game.counterweight)
                game.buttWorld(butt)
                val cw = game.counterweight
                canvas.drawLine(sx(butt[0]), sy(butt[1]), sx(cw.x), sy(cw.y), pSelect)
                drawGrip(canvas, cw.x, cw.y)
                drawValue(
                    canvas, (butt[0] + cw.x) / 2f, (butt[1] + cw.y) / 2f,
                    "%.1f m".format(cfg.hangLength)
                )
                val h = cfg.counterweightHalf
                drawGrip(canvas, cw.x + h, cw.y - h)
                drawValue(
                    canvas, cw.x + h, cw.y - h, "%d kg".format(cfg.counterweightMass.toInt())
                )
            }
            Part.PIN -> {
                game.pinWorld(pin)
                drawGrip(canvas, pin[0], pin[1])
                drawValue(canvas, pin[0], pin[1], "%d°".format(cfg.pinAngleDeg.toInt()))
            }
            Part.SLING -> {
                drawOutline(canvas, game.ball)
                drawGrip(canvas, game.ball.x, game.ball.y)
                game.tipWorld(tip)
                drawValue(
                    canvas, (tip[0] + game.ball.x) / 2f, game.ball.y,
                    "%.1f m".format(cfg.slingLength)
                )
            }
            Part.NONE -> {}
        }
    }

    /** Le liseré qui dit « c'est cette pièce-là que tu tiens ». */
    private fun drawOutline(canvas: Canvas, b: PhysBody) {
        for (i in b.parts.indices) {
            val part = b.parts[i]
            if (part.shape == Shape.CIRCLE) {
                b.partWorld(i, partPose)
                canvas.drawCircle(
                    sx(partPose[0]), sy(partPose[1]), part.radius * camScale + 2f * dp, pSelect
                )
                continue
            }
            b.partCorners(i, corners)
            tmpPath.reset()
            tmpPath.moveTo(sx(corners[0]), sy(corners[1]))
            for (k in 1 until 4) tmpPath.lineTo(sx(corners[k * 2]), sy(corners[k * 2 + 1]))
            tmpPath.close()
            canvas.drawPath(tmpPath, pSelect)
        }
    }

    /**
     * Le rail du réglage en cours : un pointillé entre ce qui ne bouge pas et ce que
     * le doigt tire. Il ne s'affiche que pendant le glissement — c'est le moment où
     * la question « qu'est-ce que je suis en train de changer ? » se pose.
     */
    private fun drawGuide(canvas: Canvas) {
        if (grip == Grip.NONE) return
        val cw = game.counterweight
        val h = game.config.counterweightHalf
        game.buttWorld(butt)
        game.tipWorld(tip)
        game.pinWorld(pin)
        val ax: Float
        val ay: Float
        val bx: Float
        val by: Float
        when (grip) {
            Grip.BEAM_LENGTH -> {
                ax = game.pivotX; ay = game.pivotY; bx = tip[0]; by = tip[1]
            }
            Grip.LEVER -> {
                ax = butt[0]; ay = butt[1]; bx = game.pivotX; by = game.pivotY
            }
            Grip.POST_HEIGHT -> {
                ax = game.pivotX; ay = 0f; bx = game.pivotX; by = game.pivotY
            }
            Grip.CW_MASS -> {
                ax = cw.x; ay = cw.y; bx = cw.x + h; by = cw.y - h
            }
            Grip.CW_HANG -> {
                ax = butt[0]; ay = butt[1]; bx = cw.x; by = cw.y
            }
            Grip.PIN_ANGLE -> {
                ax = tip[0]; ay = tip[1]; bx = pin[0]; by = pin[1]
            }
            Grip.SLING_LENGTH -> {
                ax = tip[0]; ay = tip[1]; bx = game.ball.x; by = game.ball.y
            }
            Grip.NONE -> return
        }
        canvas.drawLine(sx(ax), sy(ay), sx(bx), sy(by), pHandle)
    }

    /** Une poignée : un point qu'on peut tirer. */
    private fun drawGrip(canvas: Canvas, x: Float, y: Float) {
        val px = sx(x)
        val py = sy(y)
        canvas.drawCircle(px, py, 7f * dp, pGrip)
        canvas.drawCircle(px, py, 7f * dp, pGripEdge)
    }

    /** La valeur, posée juste au-dessus de ce qui la règle. */
    private fun drawValue(canvas: Canvas, x: Float, y: Float, text: String) {
        canvas.drawText(text, sx(x), sy(y) - 15f * dp, pValue)
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
