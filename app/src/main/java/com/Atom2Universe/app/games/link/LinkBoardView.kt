package com.Atom2Universe.app.games.link

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min

/**
 * Le plateau d'Intrication : les atomes, les fils d'onde entre jumeaux, et le doigt qui
 * dessine une pièce.
 *
 * On glisse d'atome en atome ; au lever du doigt, la forme tracée est posée si une
 * pièce libre de la main a cette forme. Pendant le tracé, les jumeaux qui descendraient aussi s'allument déjà : on voit tout ce que la
 * pose va toucher avant de lâcher.
 */
class LinkBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        /** La sélection change : la main peut montrer la pièce qu'elle reconnaît (-1 : aucune). */
        fun onSelectionChanged(matchingPiece: Int)
        fun onPlaced()
        /** La dernière pose a mis tous les atomes au repos (avant la vague). */
        fun onSolved()
        /** La vague de victoire est finie. */
        fun onVictoryShown()
    }

    var game: LinkGame? = null
    var listener: Listener? = null

    private var cell = 0f
    private var ox = 0f
    private var oy = 0f
    private var baked: Bitmap? = null
    private var bakedFor = ""

    private val selection = mutableListOf<Int>()
    private var solvedAt = -1L
    private var victoryShown = false
    /** Centre de la vague de victoire : la dernière pièce posée. */
    private var waveX = 0f
    private var waveY = 0f

    /** Un éclair sur une case : photon émis (couleur d'énergie) ou refus (rouge). */
    private class Flash(val cell: Int, val color: Int, val start: Long, val refused: Boolean)
    /** Une impulsion qui court le long d'un fil d'onde, d'un jumeau à l'autre. */
    private class Pulse(val from: Int, val to: Int, val color: Int, val start: Long)
    private val flashes = mutableListOf<Flash>()
    private val pulses = mutableListOf<Pulse>()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** Nouvelle grille, ou grille rechargée. */
    fun notifyBoardChanged() {
        selection.clear()
        flashes.clear(); pulses.clear()
        val g = game
        solvedAt = -1L
        victoryShown = g?.isVictory == true
        bakedFor = ""
        layoutBoard()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bakedFor = ""
        layoutBoard()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        baked?.recycle(); baked = null; bakedFor = ""
    }

    private fun layoutBoard() {
        val g = game ?: return
        if (width <= 0 || height <= 0 || g.width == 0) return
        val availW = (width - paddingLeft - paddingRight).toFloat()
        val availH = (height - paddingTop - paddingBottom).toFloat()
        cell = min(availW / g.width, availH / g.height)
        ox = paddingLeft + (availW - cell * g.width) / 2f
        oy = paddingTop + (availH - cell * g.height) / 2f
        val key = "${width}x$height:${g.width}:${g.floor}:${g.initial.contentHashCode()}"
        if (key == bakedFor) return
        val bmp = baked?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { baked?.recycle(); baked = it }
        val c = Canvas(bmp)
        QuantumPainter.backdrop(c, width.toFloat(), height.toFloat(), 17 + g.floor, cell, g.isBoss)
        for (y in 0 until g.height) for (x in 0 until g.width) QuantumPainter.cellFrame(c, ox + x * cell, oy + y * cell, cell)
        bakedFor = key
    }

    private fun cx(i: Int) = ox + ((i % (game?.width ?: 1)) + 0.5f) * cell
    private fun cy(i: Int) = oy + ((i / (game?.width ?: 1)) + 0.5f) * cell

    // ── Dessin ────────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        if (g.width == 0) return
        if (bakedFor.isEmpty()) layoutBoard()
        baked?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        val now = SystemClock.uptimeMillis()
        val t = now / 1000f

        // Ce que la sélection toucherait : ses cases et les jumeaux hors sélection.
        val affected = if (selection.isEmpty()) emptyList() else g.affected(selection)
        val match = if (selection.isEmpty()) -1 else g.matchingPiece(selection)

        // Fils d'onde, sous les atomes.
        for (k in 0 until g.pairCount) {
            val a = g.pairCells[k * 2]; val b = g.pairCells[k * 2 + 1]
            val color = QuantumPainter.PAIR_COLORS[k % QuantumPainter.PAIR_COLORS.size]
            // Une paire au repos des deux côtés n'a plus rien à dire : son fil pâlit.
            val alive = g.energy[a] > 0 || g.energy[b] > 0
            QuantumPainter.entanglement(canvas, cx(a), cy(a), cx(b), cy(b), cell, color, t + k, if (alive) 1f else 0.3f)
        }

        // Tracé en cours : cases reliées par un trait.
        if (selection.isNotEmpty()) {
            val bad = affected.any { g.energy[it] <= 0 }
            val color = when {
                bad -> 0xFFFF5252.toInt()
                match >= 0 -> 0xFFE0F7FF.toInt()
                else -> 0xFF8C9EFF.toInt()
            }
            linePaint.color = (color and 0xFFFFFF) or (0x50 shl 24)
            linePaint.strokeWidth = cell * 0.5f
            // Un trait entre chaque paire de cases voisines : la forme se lit, même bifurquée.
            for (i in selection.indices) for (j in i + 1 until selection.size) {
                val a = selection[i]; val b = selection[j]
                if (adjacent(a, b, g.width)) canvas.drawLine(cx(a), cy(a), cx(b), cy(b), linePaint)
            }
            framePaint.color = color; framePaint.strokeWidth = cell * 0.035f
            val r = cell * 0.44f
            for (s in selection) canvas.drawRoundRect(cx(s) - r, cy(s) - r, cx(s) + r, cy(s) + r, cell * 0.14f, cell * 0.14f, framePaint)
        }

        // Atomes.
        val waveAge = if (solvedAt >= 0) (now - solvedAt) / 1000f else -1f
        val reach = hypot(width.toFloat(), height.toFloat())
        for (i in g.energy.indices) {
            val x = cx(i); val y = cy(i)
            var boost = 0f
            if (waveAge >= 0f) {
                val front = waveAge * reach / (WAVE_MS / 1000f)
                val d = hypot(x - waveX, y - waveY)
                boost = (1f - abs(front - d) / (cell * 1.2f)).coerceIn(0f, 1f)
            }
            QuantumPainter.atom(canvas, x, y, cell, g.energy[i], t, i * 2.399f, boost)
            val p = g.partnerOf(i)
            if (p >= 0) {
                val k = pairIndex(g, i)
                QuantumPainter.pairRing(canvas, x, y, cell, QuantumPainter.PAIR_COLORS[k % QuantumPainter.PAIR_COLORS.size],
                    if (g.energy[i] > 0) 1f else 0.4f)
            }
        }

        // Les jumeaux que la sélection entraînerait : un anneau qui bat.
        if (selection.isNotEmpty()) for (c in affected) {
            if (c in selection) continue
            val beat = 0.5f + 0.5f * kotlin.math.sin(t * 9f)
            val color = if (g.energy[c] <= 0) 0xFFFF5252.toInt() else 0xFFE0F7FF.toInt()
            QuantumPainter.glow(canvas, cx(c), cy(c), cell * 0.55f, color, 0.3f + 0.35f * beat)
        }

        drawEffects(canvas, now)

        if (solvedAt >= 0 && !victoryShown && now - solvedAt >= WAVE_MS) {
            victoryShown = true
            post { listener?.onVictoryShown() }
        }
        // Les électrons tournent toujours : le plateau s'anime tant qu'il est visible.
        postInvalidateDelayed(FRAME_MS)
    }

    private fun pairIndex(g: LinkGame, cellIndex: Int): Int {
        for (k in 0 until g.pairCount) if (g.pairCells[k * 2] == cellIndex || g.pairCells[k * 2 + 1] == cellIndex) return k
        return 0
    }

    private fun drawEffects(canvas: Canvas, now: Long) {
        val fi = flashes.iterator()
        while (fi.hasNext()) {
            val f = fi.next()
            val age = (now - f.start) / FLASH_MS.toFloat()
            if (age >= 1f) { fi.remove(); continue }
            if (f.refused) {
                QuantumPainter.glow(canvas, cx(f.cell), cy(f.cell), cell * 0.6f, f.color, 1f - age)
            } else {
                // Le photon : un anneau qui s'élargit et s'efface.
                framePaint.color = f.color
                framePaint.alpha = ((1f - age) * 220).toInt()
                framePaint.strokeWidth = cell * 0.05f * (1f - age * 0.6f)
                canvas.drawCircle(cx(f.cell), cy(f.cell), cell * (0.2f + 0.45f * age), framePaint)
                framePaint.alpha = 255
            }
        }
        val pi = pulses.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            val age = (now - p.start) / PULSE_MS.toFloat()
            if (age >= 1f) { pi.remove(); continue }
            val x = cx(p.from) + (cx(p.to) - cx(p.from)) * age
            val y = cy(p.from) + (cy(p.to) - cy(p.from)) * age
            QuantumPainter.glow(canvas, x, y, cell * 0.35f, p.color, 1f - age * 0.4f)
        }
    }

    // ── Gestes ────────────────────────────────────────────────────────────────────
    private fun cellAt(px: Float, py: Float): Int {
        val g = game ?: return -1
        if (cell <= 0f) return -1
        val x = floor((px - ox) / cell).toInt(); val y = floor((py - oy) / cell).toInt()
        if (x !in 0 until g.width || y !in 0 until g.height) return -1
        return y * g.width + x
    }

    private fun adjacent(a: Int, b: Int, w: Int) =
        abs(a % w - b % w) + abs(a / w - b / w) == 1

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = game ?: return false
        if (g.width == 0 || g.isVictory) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                selection.clear()
                val c = cellAt(event.x, event.y)
                if (c >= 0) selection.add(c)
                selectionChanged()
            }
            MotionEvent.ACTION_MOVE -> {
                val c = cellAt(event.x, event.y)
                // Repasser sur une case prise ne retire rien : c'est ainsi qu'on revient au
                // centre d'un T pour dessiner sa branche. Un tracé raté se refait en relevant le doigt.
                if (c < 0 || selection.isEmpty() || c in selection) return true
                if (selection.size < MAX_PIECE && touchesSelection(c, g.width)) {
                    selection.add(c)
                    selectionChanged()
                }
            }
            MotionEvent.ACTION_UP -> commit()
            MotionEvent.ACTION_CANCEL -> { selection.clear(); selectionChanged() }
        }
        return true
    }

    /** Une pièce peut bifurquer (le T) : la case doit toucher n'importe quelle case déjà prise. */
    private fun touchesSelection(c: Int, w: Int) = selection.any { adjacent(it, c, w) }

    private fun selectionChanged() {
        val g = game ?: return
        listener?.onSelectionChanged(if (selection.isEmpty()) -1 else g.matchingPiece(selection))
        invalidate()
    }

    private fun commit() {
        val g = game ?: return
        if (selection.isEmpty()) return
        val cells = selection.toList()
        val now = SystemClock.uptimeMillis()
        // Un simple appui ne pose rien et ne proteste pas : on ne pose pas de pièce d'une case.
        if (cells.size == 1) { selection.clear(); selectionChanged(); return }
        val before = g.energy.copyOf()
        when (g.place(cells)) {
            LinkGame.Result.PLACED -> {
                val hit = g.affected(cells)
                for (c in hit) flashes.add(Flash(c, QuantumPainter.energyColor(before[c]), now, false))
                for (c in cells) {
                    val p = g.partnerOf(c)
                    if (p >= 0 && p !in cells) {
                        val k = pairIndex(g, c)
                        pulses.add(Pulse(c, p, QuantumPainter.PAIR_COLORS[k % QuantumPainter.PAIR_COLORS.size], now))
                    }
                }
                selection.clear()
                listener?.onSelectionChanged(-1)
                listener?.onPlaced()
                if (g.isVictory) {
                    waveX = cells.map { cx(it) }.average().toFloat()
                    waveY = cells.map { cy(it) }.average().toFloat()
                    solvedAt = now
                    victoryShown = false
                    listener?.onSolved()
                }
            }
            LinkGame.Result.EXHAUSTED -> {
                for (c in g.affected(cells)) if (g.energy[c] <= 0) flashes.add(Flash(c, 0xFFFF5252.toInt(), now, true))
                selection.clear(); selectionChanged()
            }
            LinkGame.Result.NO_MATCH -> {
                for (c in cells) flashes.add(Flash(c, 0xFF8C9EFF.toInt(), now, true))
                selection.clear(); selectionChanged()
            }
        }
        invalidate()
    }

    /** Après une annulation : l'atome rendu à son énergie clignote. */
    fun flashUndo(cells: List<Int>) {
        val g = game ?: return
        val now = SystemClock.uptimeMillis()
        for (c in g.affected(cells)) flashes.add(Flash(c, QuantumPainter.energyColor(g.energy[c]), now, false))
        invalidate()
    }

    companion object {
        private const val MAX_PIECE = 4
        private const val FRAME_MS = 33L
        private const val FLASH_MS = 450L
        private const val PULSE_MS = 420L
        private const val WAVE_MS = 1100L
    }
}
