package com.Atom2Universe.app.games.theline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Le plateau de Circuit : la carte électronique, les tracés et le doigt qui les tire.
 *
 * Le décor (vernis, plaque, trous métallisés, composants) ne bouge pas : il est cuit une
 * fois par grille dans une image. Seuls les tracés, les bornes et les lumières se
 * redessinent à chaque image.
 *
 * Quand la grille est résolue, le courant parcourt la piste (ou chaque fil) de bout en
 * bout ; [onPowered] prévient quand il est arrivé.
 */
class TheLineBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var game: TheLineGame? = null
        private set
    /** Un tracé a changé : c'est le moment de sauvegarder. */
    var onBoardChanged: (() -> Unit)? = null
    /** La grille vient d'être résolue (avant l'animation du courant). */
    var onSolved: (() -> Unit)? = null
    /** Le courant a fini de parcourir le circuit. */
    var onPowered: (() -> Unit)? = null
    /** Peindre aussi le vernis autour de la plaque (l'écran de jeu) ; le widget garde sa carte. */
    var drawBackdrop = false

    private var cell = 0f
    private var ox = 0f
    private var oy = 0f
    private var baked: Bitmap? = null

    private var activePath = -1
    private var solved = false
    /** Début du passage du courant, en temps d'horloge ; -1 hors victoire. */
    private var powerStart = -1L
    private var powerDone = false
    private var refusedCell: TLCoord? = null
    private var refusedAt = 0L

    private val tracePath = Path()
    private val powerPath = Path()

    fun loadGame(g: TheLineGame) {
        game = g
        activePath = -1
        solved = g.isComplete()
        powerStart = -1L
        powerDone = solved
        rebake()
        invalidate()
    }

    /** Pour une grille reprise déjà résolue : le courant est déjà passé. */
    fun showSolved() {
        solved = true; powerDone = true; powerStart = -1L
        invalidate()
    }

    /** Après un effacement des tracés. */
    fun refresh() {
        val g = game ?: return
        solved = g.isComplete()
        powerDone = solved; powerStart = -1L
        activePath = -1
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebake()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        baked?.recycle(); baked = null
    }

    // ── Mise en page et décor cuit ────────────────────────────────────────────────
    private fun rebake() {
        val p = game?.puzzle ?: return
        if (width <= 0 || height <= 0) return
        val availW = (width - paddingLeft - paddingRight).toFloat()
        val availH = (height - paddingTop - paddingBottom).toFloat()
        // La plaque déborde de MARGIN case de chaque côté de la grille.
        cell = min(availW / (p.width + 2 * MARGIN), availH / (p.height + 2 * MARGIN))
        ox = paddingLeft + (availW - cell * p.width) / 2f
        oy = paddingTop + (availH - cell * p.height) / 2f

        val bmp = baked?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { baked?.recycle(); baked = it }
        bmp.eraseColor(0)
        val c = Canvas(bmp)
        if (drawBackdrop) CircuitPainter.backdrop(c, width.toFloat(), height.toFloat(), p.path.hashCode(), cell)
        val m = cell * MARGIN
        CircuitPainter.plate(c, ox - m, oy - m, ox + cell * p.width + m, oy + cell * p.height + m, cell)
        for (y in 0 until p.height) for (x in 0 until p.width) {
            if ((y * p.width + x) !in p.blockedIndices) CircuitPainter.via(c, cx(x), cy(y), cell)
        }
        CircuitPainter.components(c, p.width, p.height, p.blockedIndices, ox, oy, cell)
    }

    private fun cx(x: Int) = ox + (x + 0.5f) * cell
    private fun cy(y: Int) = oy + (y + 0.5f) * cell

    // ── Dessin ────────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val p = g.puzzle ?: return
        baked?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        val now = SystemClock.uptimeMillis()
        var animating = false

        // Le courant : une fraction 0..1 du chemin de chaque tracé.
        val power = when {
            powerDone -> 1f
            powerStart >= 0 -> {
                val longest = g.paths.maxOf { it.sequence.size }
                val duration = (longest * 45L).coerceIn(900L, 2200L)
                ((now - powerStart).toFloat() / duration).coerceIn(0f, 1f)
            }
            else -> 0f
        }
        if (powerStart >= 0 && !powerDone) {
            animating = true
            if (power >= 1f) {
                powerDone = true
                post { onPowered?.invoke() }
            }
        }

        refusedCell?.let { c ->
            val age = now - refusedAt
            if (age < REFUSE_MS) {
                CircuitPainter.glow(canvas, cx(c.x), cy(c.y), cell * 0.6f, CircuitPainter.LED, 1f - age / REFUSE_MS.toFloat())
                animating = true
            } else refusedCell = null
        }

        if (p.mode == TheLineMode.SINGLE) drawTrack(canvas, g, p, power, now)
        else drawWires(canvas, g, power)

        // La borne suivante respire tant que la partie dure : il y a toujours une animation.
        if (!solved && p.mode == TheLineMode.SINGLE) animating = true
        if (animating) postInvalidateDelayed(FRAME_MS)
    }

    private fun buildPath(seq: List<TLCoord>, into: Path, upTo: Float = seq.size.toFloat()) {
        into.reset()
        if (seq.isEmpty()) return
        into.moveTo(cx(seq[0].x), cy(seq[0].y))
        val whole = upTo.toInt().coerceAtMost(seq.size)
        for (i in 1 until whole) into.lineTo(cx(seq[i].x), cy(seq[i].y))
        if (whole < seq.size && whole >= 1) {
            val f = upTo - whole
            val a = seq[whole - 1]; val b = seq[whole]
            into.lineTo(cx(a.x) + (cx(b.x) - cx(a.x)) * f, cy(a.y) + (cy(b.y) - cy(a.y)) * f)
        }
        // Une piste d'une seule case reste visible : un segment nul à bouts ronds fait un point.
        if (whole <= 1) into.rLineTo(0.01f, 0f)
    }

    /** Le point où en est le courant, en « cases parcourues » (1 = la première case). */
    private fun headOf(size: Int, power: Float) = 1f + (size - 1) * power

    private fun drawTrack(canvas: Canvas, g: TheLineGame, p: TheLinePuzzle, power: Float, now: Long) {
        val seq = g.paths.firstOrNull()?.sequence ?: return
        if (seq.isNotEmpty()) {
            buildPath(seq, tracePath)
            CircuitPainter.copper(canvas, tracePath, cell)
        }
        val head = headOf(seq.size, power)
        if (power > 0f && seq.isNotEmpty()) {
            buildPath(seq, powerPath, head)
            CircuitPainter.powered(canvas, powerPath, cell)
        }

        val visited = HashSet<TLCoord>(seq)
        val next = g.nextCheckpoint()
        val last = p.checkpoints.size
        p.checkpoints.forEachIndexed { i, c ->
            val n = i + 1
            val x = cx(c.x); val y = cy(c.y)
            if (n == last) {
                CircuitPainter.ledRing(canvas, x, y, cell)
                if (powerDone || (power > 0f && head >= seq.size)) CircuitPainter.glow(canvas, x, y, cell * 1.2f, CircuitPainter.LED, 0.85f)
            }
            // Pendant le passage du courant, chaque borne s'allume quand il l'atteint.
            if (power > 0f) {
                val at = seq.indexOf(c)
                if (at >= 0 && at + 1 <= head) CircuitPainter.glow(canvas, x, y, cell * 0.7f, CircuitPainter.POWER, 0.6f)
            }
            CircuitPainter.pad(canvas, x, y, cell, n, c in visited)
            if (!solved && n == next) {
                val breath = 0.5f + 0.5f * sin(now / 260.0).toFloat()
                CircuitPainter.glow(canvas, x, y, cell * (0.55f + 0.1f * breath), CircuitPainter.POWER_CORE, 0.25f + 0.3f * breath)
            }
        }
        if (power > 0f && !powerDone && seq.isNotEmpty()) spark(canvas, seq, head)
    }

    private fun drawWires(canvas: Canvas, g: TheLineGame, power: Float) {
        for (path in g.paths) {
            val seq = path.sequence
            if (seq.isEmpty()) continue
            buildPath(seq, tracePath)
            CircuitPainter.wire(canvas, tracePath, cell, path.colorValue)
            if (power > 0f) {
                val head = headOf(seq.size, power)
                buildPath(seq, powerPath, head)
                CircuitPainter.wire(canvas, powerPath, cell, path.colorValue, lit = 1f)
            }
        }
        for (path in g.paths) {
            for (e in path.endpoints) {
                val x = cx(e.x); val y = cy(e.y)
                if (path.complete) CircuitPainter.glow(canvas, x, y, cell * 0.8f, path.colorValue, if (powerDone) 0.9f else 0.55f)
                CircuitPainter.socket(canvas, x, y, cell, path.colorValue, path.complete)
            }
        }
        if (power > 0f && !powerDone) for (path in g.paths) {
            if (path.sequence.isNotEmpty()) spark(canvas, path.sequence, headOf(path.sequence.size, power))
        }
    }

    /** L'étincelle en tête du courant. */
    private fun spark(canvas: Canvas, seq: List<TLCoord>, head: Float) {
        val i = (head.toInt() - 1).coerceIn(0, seq.lastIndex)
        val j = (i + 1).coerceAtMost(seq.lastIndex)
        val f = head - (i + 1)
        val x = cx(seq[i].x) + (cx(seq[j].x) - cx(seq[i].x)) * f
        val y = cy(seq[i].y) + (cy(seq[j].y) - cy(seq[i].y)) * f
        CircuitPainter.glow(canvas, x, y, cell * 0.65f, CircuitPainter.POWER_CORE, 1f)
    }

    // ── Gestes ────────────────────────────────────────────────────────────────────
    private fun cellAt(tx: Float, ty: Float): TLCoord? {
        val p = game?.puzzle ?: return null
        if (cell <= 0f) return null
        val gx = kotlin.math.floor((tx - ox) / cell).toInt()
        val gy = kotlin.math.floor((ty - oy) / cell).toInt()
        if (gx !in 0 until p.width || gy !in 0 until p.height) return null
        return TLCoord(gx, gy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = game ?: return false
        if (g.puzzle == null || solved) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val c = cellAt(event.x, event.y) ?: return true
                activePath = g.begin(c)
                if (activePath >= 0) changed()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePath < 0) return true
                val target = cellAt(event.x, event.y) ?: return true
                walkTo(g, target)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePath = -1
                return true
            }
        }
        return false
    }

    /**
     * Un doigt rapide saute des cases entre deux évènements : on refait le trajet case par
     * case, sur l'axe le plus long d'abord, et on s'arrête au premier refus.
     */
    private fun walkTo(g: TheLineGame, target: TLCoord) {
        var moved = false
        var guard = 0
        while (guard++ < 64) {
            val last = g.paths[activePath].sequence.lastOrNull() ?: break
            if (last == target) break
            val dx = target.x - last.x; val dy = target.y - last.y
            val first = if (abs(dx) >= abs(dy)) TLCoord(last.x + Integer.signum(dx), last.y)
            else TLCoord(last.x, last.y + Integer.signum(dy))
            var step = g.extend(activePath, first)
            var tried = first
            if (step == TheLineGame.Step.REFUSED && dx != 0 && dy != 0) {
                val second = if (first.x != last.x) TLCoord(last.x, last.y + Integer.signum(dy))
                else TLCoord(last.x + Integer.signum(dx), last.y)
                val alt = g.extend(activePath, second)
                if (alt != TheLineGame.Step.REFUSED) { step = alt; tried = second }
            }
            if (step == TheLineGame.Step.REFUSED) {
                refusedCell = tried; refusedAt = SystemClock.uptimeMillis()
                invalidate()
                break
            }
            if (step == TheLineGame.Step.IGNORED) break
            moved = true
        }
        if (moved) changed()
    }

    private fun changed() {
        val g = game ?: return
        if (!solved && g.isComplete()) {
            solved = true
            activePath = -1
            powerStart = SystemClock.uptimeMillis()
            powerDone = false
            onBoardChanged?.invoke()
            onSolved?.invoke()
        } else onBoardChanged?.invoke()
        invalidate()
    }

    companion object {
        /** Débord de la plaque autour de la grille, en cases. */
        private const val MARGIN = 0.32f
        private const val FRAME_MS = 33L
        private const val REFUSE_MS = 320L
    }
}
