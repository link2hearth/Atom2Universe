package com.Atom2Universe.app.games.balance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Affichage et pilotage du mode « Gros tas ». Même mécanique que [BalanceView] : la physique
 * tourne sur son propre fil à pas fixe, les gestes arrivent du fil UI, d'où `synchronized(game)`.
 */
class BigPileView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : SurfaceView(ctx, attrs), SurfaceHolder.Callback, Runnable {

    interface Listener {
        /** Score, chute ou fin de partie : l'activité rafraîchit ses textes. */
        fun onChanged()
    }

    val game = BigPileGame()
    var listener: Listener? = null

    private var thread: Thread? = null
    @Volatile private var running = false
    private var lastNanos = 0L
    private var accumulator = 0f
    private var lastRevision = -1

    private val dp = resources.displayMetrics.density
    private var scale = 100f
    private var cx = 0f

    private companion object {
        const val FIXED_DT = 1f / 120f
        const val FRAME_NANOS = 1_000_000_000L / 60L
        const val TAP_MS = 300L

        /** Couché : la hauteur du monde à l'écran, et la petite bande laissée en haut. */
        const val LANDSCAPE_HEIGHT = 6f
        const val LANDSCAPE_BAND = 0.25f
    }

    // ── Couleurs ──────────────────────────────────────────────────────────────

    private val couleurs = SciencePalette(ctx)
    private val screenBackground = com.Atom2Universe.app.AppearanceStyle.screen(ctx)?.mutate()

    /** Une couleur par forme : la taille dit la masse, la couleur aide à suivre la pièce. */
    private val kindFill = intArrayOf(
        "#FFF176".toColorInt(), // case seule
        "#81C784".toColorInt(), // domino
        "#4DD0E1".toColorInt(), // barre de trois
        "#FFB74D".toColorInt(), // coin de trois
        "#4FC3F7".toColorInt(), // carré
        "#F06292".toColorInt(), // barre de quatre
        "#E57373".toColorInt(), // L
        "#BA68C8".toColorInt(), // T
        "#AED581".toColorInt()  // Z
    )
    private val kindEdge = IntArray(kindFill.size) { darken(kindFill[it]) }
    private val danger = "#E53935".toColorInt()

    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * dp
    }
    private val pShine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(52, 255, 255, 255) }
    private val pGrid = Paint().apply {
        strokeWidth = 1f * dp
        color = ColorUtils.setAlphaComponent(couleurs.grid, 70)
    }
    private val pReserve = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = couleurs.surface }
    private val pSlot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = couleurs.raised }
    private val pGround = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = couleurs.surface }
    private val pGroundLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = couleurs.outline
    }
    private val pSupport = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = couleurs.raised }
    private val pSupportEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = couleurs.outline
    }
    private val plankWood = "#8D6E63".toColorInt()
    private val plankEdgeWood = "#5D4037".toColorInt()
    private val pPlank = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pPlankEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * dp
    }
    private val pLife = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = couleurs.accent }
    private val pLifeLost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * dp
        color = couleurs.secondary
    }

    private val tmpRect = RectF()
    private val tmpPath = Path()

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ── Cycle de vie ──────────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) = resume()

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) = layoutWorld(w, h)

    private fun layoutWorld(w: Int, h: Int) {
        // Couché, la réserve et les vies sont dans la bulle de l'écran (BigPileReserveView) :
        // l'échelle se règle aussi sur la hauteur et la grille monte presque jusqu'en haut.
        landscape = externalReserve
        val band = if (landscape) LANDSCAPE_BAND else BigPileRules.RESERVE_HEIGHT
        cx = w / 2f
        synchronized(game) {
            if (game.started) {
                // Partie commencée : la grille garde sa hauteur, seule l'échelle suit l'écran.
                val worldHeight = game.gridTop + band
                scale = minOf(w / BigPileRules.WORLD_WIDTH, h / worldHeight)
                game.setViewport(worldHeight, band)
            } else {
                scale = if (landscape) minOf(w / BigPileRules.WORLD_WIDTH, h / LANDSCAPE_HEIGHT) else w / BigPileRules.WORLD_WIDTH
                game.setViewport(h / scale, band)
            }
        }
        reveiller()
    }

    /** Vrai quand la réserve et les vies sont dessinées hors de la vue (dans la bulle, couché). */
    var externalReserve = false
    private var landscape = false

    /** Appelé sur le fil de l'écran quand la réserve, la pièce tenue ou les vies changent. */
    var onReserveChanged: (() -> Unit)? = null
    private var reserveSignature = 0

    private fun signature(): Int {
        var sig = game.lives
        for (p in game.reserve) sig = sig * 31 + (p?.let { it.id * 17 + it.cells.contentDeepHashCode() } ?: -1)
        return sig * 31 + if (game.heldFromReserve) (game.held?.id ?: -1) else -1
    }

    /** La place de réserve sous le doigt, ou -1 (couché, la réserve est dans la bulle). */
    private fun slotUnder(wx: Float, wy: Float): Int =
        if (!landscape && wy >= game.gridTop) game.slotAt(wx) else -1

    /** Position du monde d'un point de la vue (pour la bulle, qui transmet ses gestes). */
    fun worldX(x: Float) = (x - cx) / scale
    fun worldY(y: Float) = (height - y) / scale

    /** Relance l'image après un geste venu d'ailleurs (la bulle). */
    fun wake() = reveiller()

    override fun surfaceDestroyed(holder: SurfaceHolder) = pause()

    fun pause() {
        running = false
        reveiller()
        thread?.join(1500)
        thread = null
    }

    fun resume() {
        if (running) return
        running = true
        lastNanos = System.nanoTime()
        accumulator = 0f
        thread = Thread(this, "BigPilePhysics").also { it.start() }
    }

    // La partie finie, le tas s'écroule puis se tait : le fil dort jusqu'au prochain geste.
    private val veille = Object()
    private var reveil = false

    fun reveiller() {
        synchronized(veille) {
            reveil = true
            veille.notifyAll()
        }
    }

    private fun dormir() {
        var dormi = false
        synchronized(veille) {
            while (running && !reveil) {
                veille.wait()
                dormi = true
            }
            reveil = false
        }
        if (dormi) lastNanos = System.nanoTime()
    }

    private var calme = 0f

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            val frameDt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.1f)
            lastNanos = now

            var changed = false
            var figee = false
            synchronized(game) {
                accumulator += frameDt
                while (accumulator >= FIXED_DT) {
                    game.step(FIXED_DT)
                    accumulator -= FIXED_DT
                }
                if (game.revision != lastRevision) {
                    lastRevision = game.revision
                    changed = true
                }
                val sig = signature()
                if (sig != reserveSignature) {
                    reserveSignature = sig
                    post { onReserveChanged?.invoke() }
                }
                val auRepos = game.phase == BigPileGame.Phase.OVER &&
                    game.world.isAtRest() && game.pieces.none { it.state == BigPiece.State.FALLEN }
                calme = if (auRepos) calme + frameDt else 0f
                figee = calme > 0.5f
            }
            if (changed) post { listener?.onChanged() }

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
            val reste = FRAME_NANOS - (System.nanoTime() - now)
            if (reste >= 1_000_000L) Thread.sleep(reste / 1_000_000L)
            if (figee) {
                calme = 0f
                dormir()
            }
        }
    }

    private var hardwareCanvas = true

    /** Canevas matériel, avec repli logiciel si la surface le refuse (voir [BalanceView]). */
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

    fun newGame() {
        synchronized(game) { game.newGame() }
        // Une partie neuve prend la hauteur de grille qui va à l'écran du moment.
        if (width > 0 && height > 0) layoutWorld(width, height)
        reveiller()
    }

    // ── Repère ────────────────────────────────────────────────────────────────

    private fun sx(x: Float) = cx + x * scale
    private fun sy(y: Float) = height - y * scale

    // ── Saisie ────────────────────────────────────────────────────────────────

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moved = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wx = (event.x - cx) / scale
        val wy = (height - event.y) / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                moved = false
                synchronized(game) { game.touchDown(wx, wy, slotUnder(wx, wy)) }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(event.x - downX, event.y - downY) > 10f * dp) moved = true
                if (moved) synchronized(game) { game.touchMove(wx, wy) }
            }
            MotionEvent.ACTION_UP -> {
                val tap = !moved && event.eventTime - downTime < TAP_MS
                synchronized(game) { game.touchUp(tap) }
            }
            MotionEvent.ACTION_CANCEL -> synchronized(game) { game.touchUp(false) }
        }
        reveiller()
        return true
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    private fun drawFrame(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        // Le fond du thème de l'appli, comme les autres écrans ; une couleur unie s'il n'en a pas.
        val fond = screenBackground
        if (fond != null) {
            fond.setBounds(0, 0, width, height)
            fond.draw(canvas)
        } else {
            canvas.drawColor(couleurs.background)
        }

        drawGrid(canvas, w)
        drawGround(canvas, w, h)
        drawSupport(canvas)
        drawPlank(canvas)
        for (p in game.pieces) {
            when (p.state) {
                BigPiece.State.LANDED -> drawBody(canvas, p, 255)
                BigPiece.State.FALLEN -> drawBody(canvas, p, (p.fade.coerceIn(0f, 1f) * 255).toInt())
                BigPiece.State.FALLING -> drawOnGrid(canvas, p, p.col, p.row, if (p === game.held) 215 else 255)
                BigPiece.State.RESERVE -> Unit
            }
        }
        drawReserve(canvas, w)
        drawHeldFromReserve(canvas)
        drawLives(canvas, w)
    }

    /** Les lignes de la grille, qui défilent vers le bas avec elle. */
    private fun drawGrid(canvas: Canvas, w: Float) {
        val cell = BigPileRules.CELL
        val top = sy(game.gridTop)
        val bottom = sy(0f)
        for (c in 0..BigPileRules.COLUMNS) {
            val x = sx(game.colLeft(c * BigPileRules.SUB))
            canvas.drawLine(x, top, x, bottom, pGrid)
        }
        val first = floor(game.shift / cell).toInt()
        var k = first
        while (true) {
            val y = game.rowBottom(k * BigPileRules.SUB)
            if (y > game.gridTop) break
            if (y >= 0f) canvas.drawLine(sx(-BigPileRules.WORLD_WIDTH / 2f), sy(y), sx(BigPileRules.WORLD_WIDTH / 2f), sy(y), pGrid)
            k++
        }
    }

    private fun drawGround(canvas: Canvas, w: Float, h: Float) {
        val y = sy(0f)
        canvas.drawRect(0f, y, w, h, pGround)
        canvas.drawLine(0f, y, w, y, pGroundLine)
    }

    /** Un appui large et arrondi : c'est lui qui rend la balance si stable au départ. */
    private fun drawSupport(canvas: Canvas) {
        val top = sy(BigPileRules.PIVOT_HEIGHT)
        val base = sy(0f)
        val halfBase = 0.8f * scale
        val halfTop = 0.28f * scale
        tmpPath.reset()
        tmpPath.moveTo(cx - halfTop, top)
        tmpPath.lineTo(cx + halfTop, top)
        tmpPath.lineTo(cx + halfBase, base)
        tmpPath.lineTo(cx - halfBase, base)
        tmpPath.close()
        canvas.drawPath(tmpPath, pSupport)
        canvas.drawPath(tmpPath, pSupportEdge)
        canvas.drawCircle(cx, top, 0.12f * scale, pSupport)
        canvas.drawCircle(cx, top, 0.12f * scale, pSupportEdge)
    }

    private fun drawPlank(canvas: Canvas) {
        val b = game.plank
        val hw = b.halfW * scale
        val hh = b.halfH * scale
        // La planche rougit pendant qu'elle penche trop, d'autant plus que la fin approche.
        pPlank.color = ColorUtils.blendARGB(plankWood, danger, game.danger * 0.7f)
        pPlankEdge.color = ColorUtils.blendARGB(plankEdgeWood, danger, game.danger)
        canvas.save()
        canvas.translate(sx(b.x), sy(b.y))
        canvas.rotate(-Math.toDegrees(b.angle.toDouble()).toFloat())
        tmpRect.set(-hw, -hh, hw, hh)
        canvas.drawRoundRect(tmpRect, 4f * dp, 4f * dp, pPlank)
        canvas.drawRoundRect(tmpRect, 4f * dp, 4f * dp, pPlankEdge)
        canvas.restore()
    }

    /** Une case de pièce, centrée sur l'origine courante du canevas. */
    private fun drawCell(canvas: Canvas, half: Float, kind: Int, alpha: Int) {
        val r = half * 0.24f
        pFill.color = kindFill[kind]
        pFill.alpha = alpha
        pEdge.color = kindEdge[kind]
        pEdge.alpha = alpha
        pShine.alpha = 52 * alpha / 255
        tmpRect.set(-half, -half, half, half)
        canvas.drawRoundRect(tmpRect, r, r, pFill)
        canvas.drawRoundRect(tmpRect, r, r, pEdge)
        tmpRect.set(-half + 2f * dp, -half + 2f * dp, half - 2f * dp, -half * 0.45f)
        canvas.drawRoundRect(tmpRect, r * 0.5f, r * 0.5f, pShine)
    }

    private fun drawBody(canvas: Canvas, p: BigPiece, alpha: Int) {
        val b: PhysBody = p.body ?: return
        canvas.save()
        canvas.translate(sx(b.x), sy(b.y))
        canvas.rotate(-Math.toDegrees(b.angle.toDouble()).toFloat())
        val half = BigPileRules.CELL / 2f * scale
        for (part in b.parts) {
            canvas.save()
            canvas.translate(part.localX * scale, -part.localY * scale)
            drawCell(canvas, half, p.kind.ordinal, alpha)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawOnGrid(canvas: Canvas, p: BigPiece, col: Int, row: Int, alpha: Int) {
        val cell = BigPileRules.CELL
        val half = cell / 2f * scale
        for (c in p.cells) {
            val x = game.cellLeft(col, c) + cell / 2f
            val y = game.cellBottom(row, c) + cell / 2f
            canvas.save()
            canvas.translate(sx(x), sy(y))
            drawCell(canvas, half, p.kind.ordinal, alpha)
            canvas.restore()
        }
    }

    /** La réserve en bande en haut de l'écran (debout) ; couché, c'est la bulle qui la dessine. */
    private fun drawReserve(canvas: Canvas, w: Float) {
        if (landscape) return
        val bottom = sy(game.gridTop)
        canvas.drawRect(0f, 0f, w, bottom, pReserve)
        val slotW = w / BigPileRules.SLOTS
        val pad = 6f * dp
        for (i in 0 until BigPileRules.SLOTS) {
            slotRect.set(i * slotW + pad, pad, (i + 1) * slotW - pad, bottom - pad)
            drawSlot(canvas, i, slotRect)
        }
    }

    private val slotRect = RectF()

    /** Une place de réserve et sa pièce, réduite pour y tenir, dans [rect]. */
    fun drawSlot(canvas: Canvas, i: Int, rect: RectF) {
        val corner = 12f * dp
        canvas.drawRoundRect(rect, corner, corner, pSlot)
        val p = game.reserve[i] ?: return
        val held = p === game.held
        val cellPx = BigPileRules.CELL * scale
        val fit = minOf(
            1f,
            rect.width() * 0.8f / (p.w * cellPx),
            rect.height() * 0.8f / (p.h * cellPx)
        )
        val size = cellPx * fit
        val left = rect.centerX() - p.w * size / 2f
        val base = rect.centerY() + p.h * size / 2f
        for (c in p.cells) {
            canvas.save()
            canvas.translate(left + (c[0] + 0.5f) * size, base - (c[1] + 0.5f) * size)
            drawCell(canvas, size / 2f, p.kind.ordinal, if (held) 70 else 255)
            canvas.restore()
        }
    }

    /** Pièce sortie de la réserve : aimantée sur sa case si l'endroit est libre, pâle sinon. */
    private fun drawHeldFromReserve(canvas: Canvas) {
        val p = game.held ?: return
        if (!game.heldFromReserve) return
        if (game.heldValid) {
            drawOnGrid(canvas, p, game.heldCol, game.heldRow, 220)
            return
        }
        val cell = BigPileRules.CELL
        val half = cell / 2f * scale
        for (c in p.cells) {
            canvas.save()
            canvas.translate(
                sx(game.heldFreeX + (c[0] + 0.5f) * cell),
                sy(game.heldFreeY + (c[1] + 0.5f) * cell)
            )
            drawCell(canvas, half, p.kind.ordinal, 110)
            canvas.restore()
        }
    }

    /** Les chutes encore permises : trois ronds sous la réserve, à droite (debout seulement). */
    private fun drawLives(canvas: Canvas, w: Float) {
        if (landscape) return
        val y = sy(game.gridTop) + 14f * dp
        for (i in 0 until BigPileRules.LIVES) drawLife(canvas, i, w - 14f * dp - i * 16f * dp, y)
    }

    /** Les trois ronds de vie, centrés sur ([centreX], [y]) : pour la bulle. */
    fun drawLivesRow(canvas: Canvas, centreX: Float, y: Float) {
        for (i in 0 until BigPileRules.LIVES) drawLife(canvas, i, centreX + (1 - i) * 22f * dp, y)
    }

    private fun drawLife(canvas: Canvas, i: Int, x: Float, y: Float) {
        val r = if (landscape) 7f * dp else 5f * dp
        canvas.drawCircle(x, y, r, if (i < game.lives) pLife else pLifeLost)
    }

    private fun darken(color: Int): Int {
        val f = 0.45f
        return Color.rgb((Color.red(color) * f).toInt(), (Color.green(color) * f).toInt(), (Color.blue(color) * f).toInt())
    }
}
