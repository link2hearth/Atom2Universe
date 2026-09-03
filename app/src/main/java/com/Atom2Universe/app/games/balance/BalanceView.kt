package com.Atom2Universe.app.games.balance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.graphics.toColorInt
import kotlin.math.abs
import kotlin.random.Random

/**
 * Affichage et pilotage du jeu d'équilibre.
 *
 * La simulation tourne sur son propre thread à pas fixe (1/120 s) ; les
 * événements tactiles arrivent depuis le thread UI, d'où les blocs
 * `synchronized(game)`.
 */
class BalanceView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : SurfaceView(ctx, attrs), SurfaceHolder.Callback, Runnable {

    /** Faux dès qu'une surface a refusé le canevas matériel : voir [lockFrame]. */
    private var hardwareCanvas = true

    /**
     * Attrape l'image à venir — **sur le processeur graphique**.
     *
     * C'était [SurfaceHolder.lockCanvas], donc un canevas logiciel : le processeur
     * calculait et écrivait lui-même les quatre millions et demi de pixels de l'écran,
     * à chaque image. Mesuré à la tablette sur le jeu Particules, qui souffrait du même
     * mal, cela coûtait un cœur entier et un ampère pendant que la puce graphique
     * restait à deux pour cent ; le basculement a ramené le jeu à trente pour cent d'un
     * cœur et la puce de 53 à 36 degrés. Remplir des surfaces est précisément ce que la
     * carte graphique fait pour rien.
     *
     * [SurfaceHolder.lockHardwareCanvas] ne demande qu'une chose : **tout redessiner à
     * chaque image**, puisque le contenu de l'image précédente n'est pas conservé — ce
     * que cette vue fait déjà, son rendu commençant par repeindre l'écran entier.
     *
     * Le repli logiciel n'est pas de la prudence de principe : une surface peut refuser
     * le canevas matériel, et le jeu doit alors continuer comme avant plutôt que de
     * s'arrêter. Un refus vaut pour toujours, on ne le redemande pas soixante fois par
     * seconde ; une toile nulle, en revanche, veut seulement dire que la surface n'est
     * pas prête, et c'est l'appelant qui patiente.
     */
    private fun lockFrame(): Canvas? {
        if (hardwareCanvas) {
            try {
                return holder.lockHardwareCanvas()
            } catch (_: Throwable) {
                hardwareCanvas = false
            }
        }
        return holder.lockCanvas()
    }

    interface Listener {
        /** Fin d'un test : la partie est gagnée ou perdue. */
        fun onResult(won: Boolean)

        /** Une pose, un retrait ou un refus : l'activité rafraîchit ses textes. */
        fun onPlacementChanged()
    }

    val game = BalanceGame()
    var listener: Listener? = null

    private var thread: Thread? = null
    @Volatile private var running = false
    private var lastNanos = 0L
    private var accumulator = 0f
    private var lastPhase = BalanceGame.Phase.PLACING

    private val dp = resources.displayMetrics.density

    /** Pixels par mètre. */
    private var scale = 100f
    private var cx = 0f

    private companion object {
        const val FIXED_DT = 1f / 120f
        const val GAUGE_RANGE_DEG = 12f
    }

    // ── Palette ───────────────────────────────────────────────────────────────

    private val bgTop = "#070B18".toColorInt()
    private val bgBottom = "#101A33".toColorInt()

    private val weightFill = intArrayOf(
        "#4FC3F7".toColorInt(),
        "#FFB74D".toColorInt(),
        "#81C784".toColorInt(),
        "#E57373".toColorInt(),
        "#BA68C8".toColorInt(),
        "#4DD0E1".toColorInt(),
        "#FFF176".toColorInt(),
    )

    private val pBg = Paint()
    private val pStar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255) }
    private val pGround = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#141C30".toColorInt() }
    private val pGroundLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = "#2A3A5C".toColorInt()
    }
    private val pTraySep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        color = Color.argb(70, 130, 170, 255)
        pathEffect = DashPathEffect(floatArrayOf(10f * dp, 8f * dp), 0f)
    }
    private val pPlank = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#8D6E63".toColorInt() }
    private val pPlankEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = "#5D4037".toColorInt()
    }
    private val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        color = Color.argb(120, 220, 200, 175)
    }
    private val pTickMajor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.argb(190, 245, 230, 205)
    }
    private val pTickLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(170, 235, 220, 195)
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val pDeadZone = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 239, 83, 80) }
    private val pDeadHatch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * dp
        color = Color.argb(150, 239, 83, 80)
    }
    private val pPivot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#546E7A".toColorInt() }
    private val pPivotEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = "#90A4AE".toColorInt()
    }
    private val pBox = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBoxEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * dp
    }
    private val pBoxShine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(48, 255, 255, 255) }
    private val pBoxLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val pBoxUnit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val pGhost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.argb(170, 120, 230, 190)
        pathEffect = DashPathEffect(floatArrayOf(8f * dp, 6f * dp), 0f)
    }
    private val pGhostBad = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.argb(190, 239, 83, 80)
        pathEffect = DashPathEffect(floatArrayOf(8f * dp, 6f * dp), 0f)
    }
    private val pGaugeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 20, 30, 55) }
    private val pGaugeOk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(95, 76, 220, 140) }
    private val pGaugeTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * dp
        color = Color.argb(90, 160, 190, 240)
    }
    private val pNeedle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
        strokeCap = Paint.Cap.ROUND
    }
    private val pHudText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = "#8FA6C8".toColorInt()
    }
    private val pHudValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }

    private val tmpRect = RectF()
    private val tmpPath = Path()

    /** Ciel étoilé de fond, tiré une seule fois. */
    private val starsX = FloatArray(46)
    private val starsY = FloatArray(46)
    private val starsR = FloatArray(46)

    init {
        holder.addCallback(this)
        isFocusable = true
        val r = Random(7)
        for (i in starsX.indices) {
            starsX[i] = r.nextFloat()
            starsY[i] = r.nextFloat()
            starsR[i] = 0.6f + r.nextFloat() * 1.3f
        }
    }

    // ── Cycle de vie ──────────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) = resume()

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        scale = w / BalanceRules.WORLD_WIDTH
        cx = w / 2f
        synchronized(game) {
            game.setViewport(h / scale)
            if (game.weights.isEmpty()) game.newLevel()
        }
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
        thread = Thread(this, "BalancePhysics").also { it.start() }
    }

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            val frameDt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.1f)
            lastNanos = now

            var finished: Boolean? = null
            synchronized(game) {
                accumulator += frameDt
                while (accumulator >= FIXED_DT) {
                    game.step(FIXED_DT)
                    accumulator -= FIXED_DT
                }
                if (game.phase != lastPhase) {
                    if (game.phase == BalanceGame.Phase.WON) finished = true
                    if (game.phase == BalanceGame.Phase.LOST) finished = false
                    lastPhase = game.phase
                }
            }
            finished?.let { won -> post { listener?.onResult(won) } }

            // La surface peut ne pas être encore prête : on patiente plutôt que
            // de tourner à vide sur le processeur.
            val canvas = lockFrame()
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

    // ── Conversion monde → écran ──────────────────────────────────────────────

    private fun sx(x: Float) = cx + x * scale
    private fun sy(y: Float) = height - y * scale

    // ── Saisie ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wx = (event.x - cx) / scale
        val wy = (height - event.y) / scale

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> synchronized(game) {
                if (game.phase != BalanceGame.Phase.PLACING) return true
                val w = game.weightAt(wx, wy) ?: return true
                game.beginDrag(w, wx, wy)
            }
            MotionEvent.ACTION_MOVE -> synchronized(game) { game.dragTo(wx, wy) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                synchronized(game) { game.endDrag() }
                listener?.onPlacementChanged()
            }
        }
        return true
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    private fun drawFrame(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (pBg.shader == null) {
            pBg.shader = LinearGradient(0f, 0f, 0f, h, bgTop, bgBottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, pBg)
        for (i in starsX.indices) {
            canvas.drawCircle(starsX[i] * w, starsY[i] * h * 0.75f, starsR[i] * dp * 0.7f, pStar)
        }

        drawGround(canvas, w, h)
        drawTraySeparator(canvas, w)
        drawGauge(canvas, w)
        drawPivot(canvas)
        drawPlank(canvas)
        drawWeights(canvas)
        drawDropGhost(canvas)
    }

    private fun drawGround(canvas: Canvas, w: Float, h: Float) {
        val y = sy(0f)
        canvas.drawRect(0f, y, w, h, pGround)
        canvas.drawLine(0f, y, w, y, pGroundLine)
    }

    private fun drawTraySeparator(canvas: Canvas, w: Float) {
        val y = sy(game.trayBottom)
        tmpPath.reset()
        tmpPath.moveTo(0f, y)
        tmpPath.lineTo(w, y)
        canvas.drawPath(tmpPath, pTraySep)
    }

    private fun drawPivot(canvas: Canvas) {
        val apexY = sy(BalanceRules.PIVOT_HEIGHT)
        val baseY = sy(0f)
        val halfBase = 0.42f * scale
        tmpPath.reset()
        tmpPath.moveTo(cx, apexY)
        tmpPath.lineTo(cx - halfBase, baseY)
        tmpPath.lineTo(cx + halfBase, baseY)
        tmpPath.close()
        canvas.drawPath(tmpPath, pPivot)
        canvas.drawPath(tmpPath, pPivotEdge)
    }

    private fun drawPlank(canvas: Canvas) {
        val b = game.plank
        val hw = b.halfW * scale
        val hh = b.halfH * scale
        canvas.save()
        canvas.translate(sx(b.x), sy(b.y))
        // L'axe Y de l'écran est inversé : une rotation positive du monde
        // (sens trigonométrique) correspond à une rotation négative du canvas.
        canvas.rotate(-Math.toDegrees(b.angle.toDouble()).toFloat())

        tmpRect.set(-hw, -hh, hw, hh)
        canvas.drawRoundRect(tmpRect, 4f * dp, 4f * dp, pPlank)
        canvas.drawRoundRect(tmpRect, 4f * dp, 4f * dp, pPlankEdge)

        // Règle gravée sous la planche : repère de calcul, rien ne s'y aimante.
        // Elle est dessinée en dessous pour rester lisible sous les briques.
        pTickLabel.textSize = 9.5f * dp
        var u = 0.5f
        while (u <= 6.01f) {
            val x = u * BalanceRules.RULER_UNIT * scale
            val major = u == u.toInt().toFloat()
            val len = if (major) 7f * dp else 4f * dp
            val paint = if (major) pTickMajor else pTick
            canvas.drawLine(x, hh, x, hh + len, paint)
            canvas.drawLine(-x, hh, -x, hh + len, paint)
            if (major) {
                val label = u.toInt().toString()
                canvas.drawText(label, x, hh + 17f * dp, pTickLabel)
                canvas.drawText(label, -x, hh + 17f * dp, pTickLabel)
            }
            u += 0.5f
        }

        // Zone interdite : la brique ne doit pas mordre sur le pivot.
        val dead = BalanceRules.DEAD_HALF * scale
        tmpRect.set(-dead, -hh, dead, hh)
        canvas.drawRect(tmpRect, pDeadZone)
        var hx = -dead
        while (hx < dead) {
            canvas.drawLine(hx, hh, hx + 2f * hh, -hh, pDeadHatch)
            hx += 6f * dp
        }
        canvas.drawLine(-dead, -hh, -dead, hh, pDeadHatch)
        canvas.drawLine(dead, -hh, dead, hh, pDeadHatch)

        canvas.restore()
    }

    private fun drawWeights(canvas: Canvas) {
        for (w in game.weights) {
            val b = w.body
            val color = weightFill[w.index % weightFill.size]
            // Une brique tenue au-dessus d'un emplacement interdit vire au rouge.
            val refused = w.dragging && !game.dragValid && b.y < game.trayBottom
            pBox.color = if (refused) "#B0555F".toColorInt() else color
            pBox.alpha = if (w.dragging) 200 else 255
            pBoxEdge.color = darken(pBox.color)
            pBoxEdge.alpha = pBox.alpha

            val hw = b.halfW * scale
            val hh = b.halfH * scale
            canvas.save()
            canvas.translate(sx(b.x), sy(b.y))
            canvas.rotate(-Math.toDegrees(b.angle.toDouble()).toFloat())

            val r = 5f * dp
            tmpRect.set(-hw, -hh, hw, hh)
            canvas.drawRoundRect(tmpRect, r, r, pBox)
            canvas.drawRoundRect(tmpRect, r, r, pBoxEdge)
            // Reflet sur la tranche haute
            tmpRect.set(-hw + 3f * dp, -hh + 3f * dp, hw - 3f * dp, -hh + hh * 0.45f)
            canvas.drawRoundRect(tmpRect, r * 0.6f, r * 0.6f, pBoxShine)

            // Gros chiffre : c'est l'information principale de la brique. On part
            // de la hauteur disponible, puis on rétrécit si le nombre déborde
            // en largeur (deux chiffres tiennent moins bien qu'un).
            val label = w.mass.toString()
            val showUnit = hh > 16f * dp
            pBoxLabel.color = darken(pBox.color)
            var ts = hh * (if (showUnit) 1.05f else 1.3f)
            pBoxLabel.textSize = ts
            val maxWidth = hw * 1.62f
            val measured = pBoxLabel.measureText(label)
            if (measured > maxWidth) {
                ts *= maxWidth / measured
                pBoxLabel.textSize = ts
            }
            val baseline = if (showUnit) ts * 0.22f else ts * 0.36f
            canvas.drawText(label, 0f, baseline, pBoxLabel)
            if (showUnit) {
                pBoxUnit.textSize = ts * 0.32f
                pBoxUnit.color = pBoxLabel.color
                pBoxUnit.alpha = 200
                canvas.drawText("kg", 0f, baseline + ts * 0.4f, pBoxUnit)
            }

            canvas.restore()
        }
    }

    /** Silhouette de l'emplacement visé pendant le glisser. */
    private fun drawDropGhost(canvas: Canvas) {
        val w = game.weights.firstOrNull { it.dragging } ?: return
        if (w.body.y >= game.trayBottom) return
        val hw = w.body.halfW * scale
        val hh = w.body.halfH * scale

        if (!game.dragValid) {
            // Rappel visuel de la règle : la zone du pivot est refusée.
            val dead = BalanceRules.DEAD_HALF * scale
            val top = sy(BalanceRules.PLANK_TOP)
            tmpRect.set(cx - dead, top - 2f * hh, cx + dead, top)
            canvas.drawRoundRect(tmpRect, 4f * dp, 4f * dp, pGhostBad)
            return
        }
        val x = sx(game.dragPreviewX)
        val top = sy(game.dragPreviewTop)
        tmpRect.set(x - hw, top - 2f * hh, x + hw, top)
        canvas.drawRoundRect(tmpRect, 4f * dp, 4f * dp, pGhost)
    }

    /**
     * Jauge d'équilibre, montrée **uniquement au moment de la résolution** :
     * pendant la pose, le joueur juge à l'œil et à la règle.
     */
    private fun drawGauge(canvas: Canvas, w: Float) {
        if (game.phase == BalanceGame.Phase.PLACING) return

        val gy = sy(game.trayBottom) + (sy(BalanceRules.PLANK_TOP + 1.1f) - sy(game.trayBottom)) * 0.5f
        val gw = w * 0.72f
        val gh = 16f * dp
        val left = (w - gw) / 2f

        tmpRect.set(left, gy - gh / 2f, left + gw, gy + gh / 2f)
        canvas.drawRoundRect(tmpRect, gh / 2f, gh / 2f, pGaugeBg)

        val tol = game.difficulty.toleranceDeg
        val okHalf = gw / 2f * (tol / GAUGE_RANGE_DEG)
        tmpRect.set(w / 2f - okHalf, gy - gh / 2f, w / 2f + okHalf, gy + gh / 2f)
        canvas.drawRoundRect(tmpRect, gh / 2f, gh / 2f, pGaugeOk)

        for (k in -4..4) {
            val x = w / 2f + gw / 2f * (k / 4f)
            canvas.drawLine(x, gy - gh / 2f, x, gy + gh / 2f, pGaugeTick)
        }

        val lean = game.leanDeg
        val clamped = lean.coerceIn(-GAUGE_RANGE_DEG, GAUGE_RANGE_DEG)
        val nx = w / 2f + gw / 2f * (clamped / GAUGE_RANGE_DEG)
        pNeedle.color = if (abs(lean) <= tol) "#4CDC8C".toColorInt() else "#FF7043".toColorInt()
        canvas.drawLine(nx, gy - gh * 0.95f, nx, gy + gh * 0.95f, pNeedle)

        pHudValue.textSize = 14f * dp
        pHudValue.color = pNeedle.color
        canvas.drawText(String.format("%.1f°", lean), w / 2f, gy - gh * 1.7f, pHudValue)

        // Une fois le verdict rendu, on montre les couples : c'est là que le
        // joueur apprend de quel côté et de combien il s'est trompé.
        if (game.phase != BalanceGame.Phase.TESTING) {
            pHudText.textSize = 12f * dp
            canvas.drawText(
                String.format("%.1f", game.leftTorque), w / 2f - gw / 2f - 24f * dp, gy + 5f * dp, pHudText
            )
            canvas.drawText(
                String.format("%.1f", game.rightTorque), w / 2f + gw / 2f + 24f * dp, gy + 5f * dp, pHudText
            )
        }
    }

    private fun darken(color: Int): Int {
        val f = 0.38f
        return Color.rgb(
            (Color.red(color) * f).toInt(),
            (Color.green(color) * f).toInt(),
            (Color.blue(color) * f).toInt()
        )
    }
}
