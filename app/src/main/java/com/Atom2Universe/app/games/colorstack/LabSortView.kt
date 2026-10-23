package com.Atom2Universe.app.games.colorstack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Les éprouvettes : un tube se soulève au toucher, puis s'incline au-dessus du tube visé et verse
 * son liquide en filet. Le moteur ([LabSortGame]) est mis à jour tout de suite ; la vue joue
 * l'animation à partir de ce qu'elle a vu avant le coup.
 */
class LabSortView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        /** Verse de `from` vers `to` ; rend le nombre de niveaux versés (0 si le coup est refusé). */
        fun onPour(from: Int, to: Int): Int
        fun onPipette(from: Int, to: Int): Boolean
        /** Appelé quand l'animation est finie : c'est là qu'on regarde si la partie est gagnée. */
        fun onSettled()
    }

    var game: LabSortGame? = null
        set(value) {
            field = value
            cancelAnimation()
            selected = -1
            layoutDirty = true
            invalidate()
        }
    var listener: Listener? = null
    var showFormulas = true
        set(value) { field = value; invalidate() }
    var pipetteArmed = false
        set(value) { field = value; invalidate() }

    private val dp = resources.displayMetrics.density

    // ── Géométrie (recalculée quand la taille ou le nombre de tubes change) ─────────────────────
    private var layoutDirty = true
    private var tubeW = 0f
    private var tubeH = 0f
    private var layerH = 0f
    private var wall = 0f
    private var lift = 0f
    private var lefts = FloatArray(0)
    private var tops = FloatArray(0)
    private val outerPath = Path()
    private val innerPath = Path()
    private val rect = RectF()

    private var selected = -1

    // ── Bandes de liquide du tube en cours de dessin ───────────────────────────────────────────
    private val bandColor = IntArray(8)
    private val bandUnits = FloatArray(8)
    private val bandHidden = BooleanArray(8)
    private val bandLabel = arrayOfNulls<String>(8)
    private var bandCount = 0

    // ── Animation d'un versement ──────────────────────────────────────────────────────────────
    private var animating = false
    private var animFrom = 0
    private var animTo = 0
    private var animCount = 0
    private var animDir = 1
    private var animStart = 0L
    private var animDur = 1L
    private var animLipX = 0f
    private var animLipY = 0f
    private val srcIds = IntArray(6)
    private val srcRev = BooleanArray(6)
    private var srcN = 0
    private val dstIds = IntArray(6)
    private val dstRev = BooleanArray(6)
    private var dstN = 0
    private var movedColor = 0

    private val glassFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0x1AFFFFFF }
    private val glassStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val liquid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0x33000000 }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0x40FFFFFF }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val stream = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // ── Mesure et disposition ─────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutDirty = true
    }

    private fun computeLayout() {
        layoutDirty = false
        val g = game ?: return
        val n = g.tubeCount
        if (n == 0 || width == 0 || height == 0) return

        val padX = 12f * dp
        val padY = 8f * dp
        val gapX = 10f * dp
        val gapY = 12f * dp
        val maxW = 84f * dp

        var bestW = 0f
        var bestRows = 1
        for (rows in 1..4) {
            val per = ceil(n / rows.toDouble()).toInt()
            val byWidth = (width - 2 * padX - (per - 1) * gapX) / per
            // Hauteur : de la place au-dessus de la première rangée pour l'inclinaison, puis les rangées.
            val byHeight = (height - 2 * padY - (rows - 1) * gapY) / (TUBE_RATIO * rows + TOP_ROOM)
            val w = min(min(byWidth, byHeight), maxW)
            if (w > bestW + 0.5f) { bestW = w; bestRows = rows }
        }

        tubeW = bestW
        tubeH = bestW * TUBE_RATIO
        wall = tubeW * 0.07f
        layerH = (tubeH - 2 * wall - tubeW * 0.25f) / LabSortGame.CAPACITY
        lift = tubeH * 0.1f

        val per = ceil(n / bestRows.toDouble()).toInt()
        val total = tubeW * TOP_ROOM + bestRows * tubeH + (bestRows - 1) * gapY
        val startY = (height - total) / 2f + tubeW * TOP_ROOM
        lefts = FloatArray(n)
        tops = FloatArray(n)
        val gridW = per * tubeW + (per - 1) * gapX
        for (i in 0 until n) {
            val row = i / per
            val inRow = min(per, n - row * per)
            val rowW = inRow * tubeW + (inRow - 1) * gapX
            // Les derniers tubes restent en bas à droite, même si la rangée est incomplète.
            lefts[i] = (width + gridW) / 2f - rowW + (i % per) * (tubeW + gapX)
            tops[i] = startY + row * (tubeH + gapY)
        }

        val topR = tubeW * 0.1f
        val botR = tubeW / 2f
        outerPath.rewind()
        rect.set(0f, 0f, tubeW, tubeH)
        outerPath.addRoundRect(rect, floatArrayOf(topR, topR, topR, topR, botR, botR, botR, botR), Path.Direction.CW)
        innerPath.rewind()
        rect.set(wall, wall * 0.5f, tubeW - wall, tubeH - wall)
        val ir = botR - wall
        innerPath.addRoundRect(rect, floatArrayOf(topR, topR, topR, topR, ir, ir, ir, ir), Path.Direction.CW)

        glassStroke.strokeWidth = max(1.5f * dp, tubeW * 0.045f)
    }

    // ── Dessin ────────────────────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = game ?: return
        if (layoutDirty) computeLayout()
        if (lefts.size != g.tubeCount) return

        val t = if (animating) ((SystemClock.uptimeMillis() - animStart).toFloat() / animDur).coerceIn(0f, 1f) else 0f
        val p = if (animating) pourProgress(t) else 0f

        val celeb = if (celebrating) (SystemClock.uptimeMillis() - celebStart) / 1000f else -1f

        for (i in 0 until g.tubeCount) {
            if (animating && i == animFrom) continue
            if (animating && i == animTo) {
                fillBandsReceiving(p)
                drawTube(canvas, lefts[i], tops[i], complete = false, raised = false)
            } else {
                fillBandsStatic(g.tubes[i])
                drawTube(canvas, lefts[i], tops[i] + hopOffset(i, celeb), g.isComplete(g.tubes[i]), raised = i == selected)
            }
        }
        if (celebrating) drawCelebration(canvas, celeb)

        if (animating) {
            drawPouringTube(canvas, t, p)
            if (t >= 1f) {
                animating = false
                post { listener?.onSettled() }
                invalidate()
            } else {
                postInvalidateOnAnimation()
            }
        }
    }

    private fun drawTube(canvas: Canvas, left: Float, top: Float, complete: Boolean, raised: Boolean) {
        canvas.save()
        canvas.translate(left, top - if (raised) lift else 0f)
        canvas.save()
        canvas.clipPath(innerPath)
        drawBandsUpright(canvas)
        canvas.restore()
        drawGlass(canvas, complete, raised)
        canvas.restore()
    }

    private fun drawGlass(canvas: Canvas, complete: Boolean, raised: Boolean) {
        canvas.drawPath(outerPath, glassFill)
        glassStroke.color = when {
            raised -> 0xFFFFFFFF.toInt()
            complete -> 0xFF4ADE80.toInt()
            else -> 0x88C7D2FE.toInt()
        }
        canvas.drawPath(outerPath, glassStroke)
        rect.set(wall * 1.8f, tubeH * 0.1f, wall * 1.8f + tubeW * 0.07f, tubeH * 0.72f)
        canvas.drawRoundRect(rect, tubeW * 0.035f, tubeW * 0.035f, shine)
    }

    /** Bandes à plat, dans le repère du tube (origine en haut à gauche). */
    private fun drawBandsUpright(canvas: Canvas) {
        var y = tubeH - wall
        for (b in 0 until bandCount) {
            val h = bandUnits[b] * layerH
            val top = y - h
            paintBand(canvas, b, wall, top, tubeW - wall, y)
            val sameLiquid = b > 0 && !bandHidden[b] && !bandHidden[b - 1] && bandColor[b] == bandColor[b - 1]
            if (b > 0 && !sameLiquid && bandUnits[b] >= 0.99f && bandUnits[b - 1] >= 0.99f) {
                rect.set(wall, y - dp * 0.4f, tubeW - wall, y + dp * 0.4f)
                canvas.drawRect(rect, divider)
            }
            y = top
        }
        if (bandCount > 0) {
            rect.set(wall + dp * 2, y, tubeW - wall - dp * 2, y + layerH * 0.07f)
            canvas.drawRect(rect, shine)
        }
    }

    private fun paintBand(canvas: Canvas, b: Int, l: Float, t: Float, r: Float, bt: Float) {
        liquid.color = if (bandHidden[b]) HIDDEN_COLOR else bandColor[b]
        canvas.drawRect(l, t, r, bt, liquid)
        val label = if (bandHidden[b]) "?" else if (showFormulas) bandLabel[b] else null
        if (label != null && bt - t >= layerH * 0.9f) {
            text.color = if (bandHidden[b]) 0xFF6B7A99.toInt()
            else if (ColorUtils.calculateLuminance(bandColor[b]) > 0.5) 0xFF0F172A.toInt() else 0xFFFFFFFF.toInt()
            val avail = (r - l) * 0.9f
            text.textSize = layerH * 0.42f
            val w = text.measureText(label)
            if (w > avail) text.textSize = text.textSize * avail / w
            canvas.drawText(label, (l + r) / 2f, (t + bt) / 2f - (text.ascent() + text.descent()) / 2f, text)
        }
    }

    /** Les niveaux voisins de même couleur (et visibles) ne font qu'une seule bande : plus de séparation. */
    private fun fillBandsStatic(cells: List<LabSortGame.Cell>) {
        bandCount = 0
        for (c in cells) {
            val last = bandCount - 1
            if (c.revealed && last >= 0 && !bandHidden[last] && bandColor[last] == LabLiquids.ALL[c.colorId].hex) {
                bandUnits[last] += 1f
                continue
            }
            bandColor[bandCount] = LabLiquids.ALL[c.colorId].hex
            bandUnits[bandCount] = 1f
            bandHidden[bandCount] = !c.revealed
            bandLabel[bandCount] = LabLiquids.ALL[c.colorId].formula
            bandCount++
        }
    }

    /** Le tube qui reçoit : ce qu'il contenait, plus le filet qui monte. */
    private fun fillBandsReceiving(p: Float) {
        bandCount = 0
        for (i in 0 until dstN) {
            bandColor[bandCount] = LabLiquids.ALL[dstIds[i]].hex
            bandUnits[bandCount] = 1f
            bandHidden[bandCount] = !dstRev[i]
            bandLabel[bandCount] = null
            bandCount++
        }
        bandColor[bandCount] = LabLiquids.ALL[movedColor].hex
        bandUnits[bandCount] = animCount * p
        bandHidden[bandCount] = false
        bandLabel[bandCount] = null
        bandCount++
    }

    /** Le tube qui verse : ses niveaux d'avant, le groupe du dessus qui baisse. */
    private fun fillBandsPouring(p: Float) {
        bandCount = 0
        val keep = srcN - animCount
        for (i in 0 until keep) {
            bandColor[bandCount] = LabLiquids.ALL[srcIds[i]].hex
            bandUnits[bandCount] = 1f
            bandHidden[bandCount] = !srcRev[i] && !(i == keep - 1 && p > 0.5f)
            bandLabel[bandCount] = null
            bandCount++
        }
        bandColor[bandCount] = LabLiquids.ALL[movedColor].hex
        bandUnits[bandCount] = animCount * (1f - p)
        bandHidden[bandCount] = false
        bandLabel[bandCount] = null
        bandCount++
    }

    private fun drawPouringTube(canvas: Canvas, t: Float, p: Float) {
        val homeMx = if (animDir > 0) tubeW else 0f
        val homeX = lefts[animFrom] + homeMx
        val homeY = tops[animFrom] - lift

        val lipX: Float
        val lipY: Float
        val theta: Float
        when {
            t < PHASE_IN -> {
                val e = ease(t / PHASE_IN)
                lipX = homeX + (animLipX - homeX) * e
                lipY = homeY + (animLipY - homeY) * e
                theta = TILT_START * e
            }
            t <= PHASE_OUT -> {
                lipX = animLipX
                lipY = animLipY
                theta = TILT_START + (TILT_END - TILT_START) * p
            }
            else -> {
                val e = ease((t - PHASE_OUT) / (1f - PHASE_OUT))
                lipX = animLipX + (homeX - animLipX) * e
                lipY = animLipY + (homeY - animLipY) * e
                theta = TILT_END * (1f - e)
            }
        }

        fillBandsPouring(p)
        val phi = animDir * theta
        canvas.save()
        canvas.translate(lipX, lipY)
        canvas.rotate(phi)
        canvas.translate(-homeMx, 0f)

        // Le liquide reste à l'horizontale : on garde le découpage du tube incliné, puis on redresse le repère.
        canvas.save()
        canvas.clipPath(innerPath)
        canvas.translate(homeMx, 0f)
        canvas.rotate(-phi)
        val rad = Math.toRadians(phi.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val vx = tubeW / 2f - homeMx
        val vy = tubeH - tubeW / 2f
        var y = vx * s + vy * c + (tubeW / 2f - wall)
        val f = max(abs(c), 0.3f)
        for (b in 0 until bandCount) {
            val h = bandUnits[b] * layerH * f
            liquid.color = if (bandHidden[b]) HIDDEN_COLOR else bandColor[b]
            canvas.drawRect(-width.toFloat(), y - h, width.toFloat(), y, liquid)
            y -= h
        }
        canvas.restore()
        drawGlass(canvas, complete = false, raised = false)
        canvas.restore()

        // Le filet, du bord du tube jusqu'à la surface du tube qui reçoit.
        if (p > 0f && p < 1f) {
            val level = tops[animTo] + tubeH - wall - (dstN + animCount * p) * layerH
            val startY = lipY + wall
            if (level > startY) {
                stream.color = LabLiquids.ALL[movedColor].hex
                stream.strokeWidth = tubeW * 0.14f * min(1f, min(p, 1f - p) * 10f + 0.3f)
                canvas.drawLine(lipX, startY, lipX, level, stream)
            }
        }
    }

    // ── Fête de la victoire : les tubes sautent en vague, des gouttes de leur couleur jaillissent ──

    private var celebrating = false
    private var celebStart = 0L
    private var pTube = IntArray(0)
    private var pStart = FloatArray(0)
    private var pVx = FloatArray(0)
    private var pVy = FloatArray(0)
    private var pSize = FloatArray(0)
    private var pColor = IntArray(0)
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Lance la fête ; elle s'arrête toute seule après quelques secondes. */
    fun celebrate() {
        val g = game ?: return
        if (layoutDirty) computeLayout()
        val filled = (0 until g.tubeCount).filter { g.tubes[it].isNotEmpty() }
        if (filled.isEmpty() || tubeW <= 0f) return
        val rnd = kotlin.random.Random(g.moves * 7919 + filled.size)
        val n = PARTICLES
        pTube = IntArray(n) { filled[it % filled.size] }
        pStart = FloatArray(n) { CELEB_HOP_WAVE + rnd.nextFloat() * 1.3f }
        pVx = FloatArray(n) { (rnd.nextFloat() - 0.5f) * tubeW * 2.6f }
        pVy = FloatArray(n) { -tubeH * (1.1f + rnd.nextFloat() * 1.1f) }
        pSize = FloatArray(n) { tubeW * (0.05f + rnd.nextFloat() * 0.07f) }
        pColor = IntArray(n) { LabLiquids.ALL[g.tubes[pTube[it]].last().colorId].hex }
        celebStart = SystemClock.uptimeMillis()
        celebrating = true
        invalidate()
    }

    private fun hopOffset(i: Int, t: Float): Float {
        if (t < 0f) return 0f
        val dt = t - i * 0.07f
        if (dt < 0f || dt > CELEB_HOP) return 0f
        return -tubeH * 0.14f * sin(Math.PI * dt / CELEB_HOP).toFloat()
    }

    private fun drawCelebration(canvas: Canvas, t: Float) {
        val g = game ?: return
        val gravity = tubeH * 3.4f
        for (k in pTube.indices) {
            val tt = t - pStart[k]
            if (tt < 0f || tt > PARTICLE_LIFE) continue
            val i = pTube[k]
            if (i >= lefts.size) continue
            val ox = lefts[i] + tubeW / 2f
            val oy = tops[i] + tubeH * 0.05f
            val life = tt / PARTICLE_LIFE
            spark.color = (pColor[k] and 0x00FFFFFF) or (((1f - life) * 255).toInt() shl 24)
            canvas.drawCircle(ox + pVx[k] * tt, oy + pVy[k] * tt + 0.5f * gravity * tt * tt,
                pSize[k] * (1f - 0.4f * life), spark)
        }
        if (t > CELEB_TOTAL) {
            celebrating = false
            invalidate()
        } else {
            postInvalidateOnAnimation()
        }
        if (g.tubeCount == 0) celebrating = false
    }

    private fun pourProgress(t: Float) = ((t - PHASE_IN) / (PHASE_OUT - PHASE_IN)).coerceIn(0f, 1f)

    private fun ease(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    // ── Toucher ───────────────────────────────────────────────────────────────────────────────

    private fun tubeAt(x: Float, y: Float): Int {
        if (layoutDirty) computeLayout()
        val gx = 5f * dp
        for (i in lefts.indices) {
            if (x >= lefts[i] - gx && x <= lefts[i] + tubeW + gx &&
                y >= tops[i] - lift && y <= tops[i] + tubeH + gx) return i
        }
        return -1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        if (animating) return true
        val g = game ?: return true
        val tapped = tubeAt(event.x, event.y)
        when {
            tapped < 0 -> clearSelection()
            selected < 0 -> if (g.tubes[tapped].isNotEmpty()) select(tapped)
            selected == tapped -> clearSelection()
            pipetteArmed -> if (g.canPipette(selected, tapped)) startMove(selected, tapped, pipette = true)
            g.canPour(selected, tapped) -> startMove(selected, tapped, pipette = false)
            g.tubes[tapped].isNotEmpty() -> select(tapped)
            else -> clearSelection()
        }
        return true
    }

    private fun select(i: Int) {
        selected = i
        invalidate()
    }

    fun clearSelection() {
        selected = -1
        invalidate()
    }

    private fun startMove(from: Int, to: Int, pipette: Boolean) {
        val g = game ?: return
        capture(g.tubes[from], srcIds, srcRev).also { srcN = it }
        capture(g.tubes[to], dstIds, dstRev).also { dstN = it }
        movedColor = srcIds[srcN - 1]

        val count = if (pipette) (if (listener?.onPipette(from, to) == true) 1 else 0)
        else listener?.onPour(from, to) ?: 0
        selected = -1
        if (count <= 0) { invalidate(); return }

        animFrom = from
        animTo = to
        animCount = count
        // Le tube se penche du côté où il y a le plus de place pour son corps.
        val targetX = lefts[to] + tubeW / 2f
        animDir = if (targetX >= width - targetX) 1 else -1
        animLipX = targetX
        animLipY = tops[to] - tubeW * 0.12f
        animStart = SystemClock.uptimeMillis()
        animDur = 560L + 140L * count
        animating = true
        invalidate()
    }

    private fun capture(cells: List<LabSortGame.Cell>, ids: IntArray, rev: BooleanArray): Int {
        for (i in cells.indices) { ids[i] = cells[i].colorId; rev[i] = cells[i].revealed }
        return cells.size
    }

    private fun cancelAnimation() {
        animating = false
        celebrating = false
    }

    /** À appeler quand la partie change autrement que par un geste (nouvelle partie, annuler, etc.). */
    fun refresh() {
        cancelAnimation()
        selected = -1
        invalidate()
    }

    private companion object {
        const val TUBE_RATIO = 2.75f
        /** Place réservée au-dessus de la première rangée, en largeurs de tube, pour l'inclinaison. */
        const val TOP_ROOM = 1.0f
        const val PHASE_IN = 0.25f
        const val PHASE_OUT = 0.80f
        const val TILT_START = 52f
        const val TILT_END = 84f
        const val HIDDEN_COLOR = 0xFF2A3347.toInt()
        const val PARTICLES = 160
        const val PARTICLE_LIFE = 1.2f
        const val CELEB_HOP = 0.55f
        /** Quand la vague de sauts est finie et que les gouttes commencent à partir (secondes). */
        const val CELEB_HOP_WAVE = 0.15f
        const val CELEB_TOTAL = 3.4f
    }
}
