package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.view.View
import android.widget.OverScroller
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.music.Scale
import com.Atom2Universe.app.studio.music.Scales
import kotlin.math.max
import kotlin.math.min

/**
 * Le piano roll du synthé : une ligne par hauteur (de la plus aiguë en haut à la plus grave en bas), une colonne par
 * pas. Toucher une case vide pose une note (glisser vers la droite l'allonge), toucher une note l'efface, glisser le
 * bout d'une note la raccourcit ou l'allonge. Chaque toucher fait entendre la hauteur.
 *
 * La vue ne possède aucune donnée : elle interroge [notes] à chaque dessin et rend la main par [listener].
 */
class PianoRollView(context: Context) : View(context) {

    interface Listener {
        fun onAddNote(step: Int, pitch: Int, length: Int)
        fun onRemoveNote(note: Note)
        fun onResizeNote(note: Note, length: Int)
        fun onPreview(pitch: Int)

        /** Le cadre de sélection vient d'être tracé (ou effacé) : l'écran met à jour ses boutons. */
        fun onSelectionChanged()

        /** Un toucher en mode « coller » : on colle à partir de ce pas, la note la plus grave sur cette hauteur. */
        fun onPasteAt(pitch: Int, step: Int)

        /** Appui long sur une note : l'écran ouvre sa bulle de réglages, près du doigt (position à l'écran). */
        fun onEditNote(note: Note, screenX: Float, screenY: Float)
    }

    var listener: Listener? = null

    /** Les hauteurs MIDI affichées, de la plus grave à la plus aiguë. */
    var rows: IntArray = IntArray(0)
        set(value) {
            val changed = field.size != value.size
            if (!field.contentEquals(value) && selection != null) {
                selection = null
                selecting = false
                listener?.onSelectionChanged()
            }
            field = value
            if (changed) requestLayout()
            invalidate()
        }

    /** Place à garder sous la vue (pour le clavier) : le piano roll prend sa hauteur naturelle, sans dépasser le reste. */
    var reserveBelow: Int = 0
    /** Quand plusieurs motifs sont affichés à la suite : leur longueur et leur lettre (A à D). Les numéros repartent de 1 à chaque motif. */
    var sectionLen: Int = Int.MAX_VALUE
    var sectionLabels: List<String> = emptyList()

    var stepCount: Int = 16
        set(value) {
            field = value
            clampScroll()
            invalidate()
        }
    var keyPc: Int = 9
    var scale: Scale = Scales.MINOR_PENT
    var accent: Int = 0xFF1ABC9C.toInt()

    /** Noms des douze notes d'une octave (do, do dièse, ré…), fournis par l'écran dans la langue de l'utilisateur. */
    var noteNames: List<String> = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    var notes: () -> List<Note> = { emptyList() }

    var playhead: Int = -1
        set(value) {
            if (field != value) {
                field = value
                // Pendant la lecture, la grille suit le repère quand il sort de l'écran (sauf si un doigt la tient)
                if (value >= 0 && !touching && scrollTracker == null) ensureVisible(value)
                invalidate()
            }
        }

    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val rowIn = if (dark) 0xFF3B3B42.toInt() else 0xFFD8D8DE.toInt()
    private val rowInAlt = if (dark) 0xFF44444B.toInt() else 0xFFCFCFD6.toInt()
    private val rowOut = if (dark) 0xFF2A2A2F.toInt() else 0xFFE8E8EC.toInt()
    private val rowRoot = if (dark) 0xFF54545E.toInt() else 0xFFBDBDC8.toInt()
    private val labelColor = if (dark) 0xFFB5B5C0.toInt() else 0xFF55555F.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        color = labelColor
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        color = labelColor
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    private val keyW get() = dp(72f)
    private val topBar get() = dp(24f)
    private val groupGap = dp(5f)

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val available = (MeasureSpec.getSize(heightSpec) - reserveBelow).coerceAtLeast(0)
        val natural = (topBar + rows.size * dp(ROW_MAX_DP)).toInt()
        setMeasuredDimension(w, if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED) natural else min(natural, available))
    }

    /** Hauteur d'une rangée : la place disponible partagée, au plus [ROW_MAX_DP] (on n'étire pas au-delà), moins s'il y en a beaucoup. */
    private fun rowH(): Float = if (rows.isEmpty()) 0f else min(dp(ROW_MAX_DP), (height - topBar) / rows.size)

    /** Une case : la grille tient dans la largeur si elle peut, sinon chaque case garde au moins 32 dp et la grille défile. */
    private fun cellW(): Float {
        val groups = max(1, stepCount / 4)
        val fit = (width - keyW - dp(6f) - groupGap * (groups - 1)) / stepCount
        return fit.coerceIn(dp(32f), dp(64f))
    }

    private val gridLeft get() = keyW + dp(6f)

    private fun cellLeft(step: Int): Float = gridLeft + step * cellW() + (step / 4) * groupGap - scrollX

    private fun contentW(): Float = stepCount * cellW() + ((stepCount - 1) / 4) * groupGap

    // ---- Défilement horizontal -----------------------------------------------------------------------

    // ---- Sélection : un cadre de hauteurs et de pas --------------------------------------------------------------

    /** Un cadre : les hauteurs de [pitchLow] à [pitchHigh] et les pas de [stepFrom] à [stepTo], bornes comprises. */
    class Frame(val pitchLow: Int, val pitchHigh: Int, val stepFrom: Int, val stepTo: Int)

    /** Vrai : un glissé sur les cases trace un cadre de sélection au lieu de poser une note. */
    var selectMode = false
        set(value) {
            field = value
            if (!value) {
                selection = null
                selecting = false
            }
            invalidate()
        }

    /** Vrai : on place ce qu'on a copié ; un toucher ou un glissé déplace l'aperçu, c'est le bouton « Coller ici » qui colle. */
    var pasteArmed = false
        set(value) {
            field = value
            if (!value) {
                pasteStep = -1
                placing = false
            }
            invalidate()
        }

    /** Les notes à coller, en fantôme : hauteur d'origine, pas relatif, longueur. La plus grave se pose sur [pasteAnchorPitch]. */
    var pasteNotes: List<IntArray> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var pasteLow = 0

    var pasteAnchorPitch = -1
        private set

    /** Le pas où la forme se posera (-1 : pas encore placée). */
    var pasteStep = -1
        private set
    private var placing = false

    fun placePaste(pitch: Int, step: Int) {
        pasteAnchorPitch = pitch
        pasteStep = step.coerceIn(0, max(0, stepCount - 1))
        invalidate()
    }

    private fun updatePlacementEnd() {
        val pitch = pitchAt(selY).let { if (it >= 0) it else if (selY < topBar) rows.last() else rows.first() }
        val step = stepAt(selX).let { if (it >= 0) it else if (selX < gridLeft) 0 else stepCount - 1 }
        placePaste(pitch, step)
    }

    var selection: Frame? = null
        private set
    private var selecting = false
    private var anchorPitch = 0
    private var anchorStep = 0
    private var selDownPitch = -1
    private var selDownStep = -1
    private var selX = 0f
    private var selY = 0f

    fun setSelection(pitchA: Int, pitchB: Int, stepA: Int, stepB: Int) {
        selection = Frame(minOf(pitchA, pitchB), maxOf(pitchA, pitchB), minOf(stepA, stepB), maxOf(stepA, stepB))
        invalidate()
    }

    fun clearSelection() {
        selection = null
        invalidate()
    }

    private fun updateSelectionEnd() {
        val pitch = pitchAt(selY).let { if (it >= 0) it else if (selY < topBar) rows.last() else rows.first() }
        val step = stepAt(selX).let { if (it >= 0) it else if (selX < gridLeft) 0 else stepCount - 1 }
        setSelection(anchorPitch, pitch, anchorStep, step)
    }

    /** Pendant qu'on trace un cadre, le bord de l'écran fait défiler la grille de gauche à droite. */
    private val selScroll = object : Runnable {
        override fun run() {
            if (!selecting && !placing) return
            val dx = when {
                selX > width - dp(48f) -> dp(14f)
                selX < gridLeft + dp(40f) -> -dp(14f)
                else -> 0f
            }
            if (dx != 0f) {
                scrollX = (scrollX + dx).coerceIn(0f, maxScrollX())
                if (placing) updatePlacementEnd() else updateSelectionEnd()
            }
            handler.postDelayed(this, 16)
        }
    }

    private fun drawSelection(canvas: Canvas) {
        val f = selection ?: return
        val hi = rows.indexOf(f.pitchHigh)
        val lo = rows.indexOf(f.pitchLow)
        if (hi < 0 || lo < 0) return
        val rh = rowH()
        val left = cellLeft(f.stepFrom) - dp(1f)
        val right = cellLeft(f.stepTo) + cellW() + dp(1f)
        val top = topBar + (rows.size - 1 - hi) * rh
        val bottom = topBar + (rows.size - lo) * rh
        rect.set(left, top, right, bottom)
        fill.color = Color.argb(46, 255, 255, 255)
        canvas.drawRoundRect(rect, dp(5f), dp(5f), fill)
        selStroke.strokeWidth = dp(2f)
        canvas.drawRoundRect(rect, dp(5f), dp(5f), selStroke)
    }

    private val selStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    private var scrollX = 0f
    private val scroller = OverScroller(context)
    private var scrollTracker: VelocityTracker? = null
    private var scrollDownX = 0f
    private var scrollStart = 0f
    private var touching = false

    private fun maxScrollX(): Float = max(0f, contentW() - (width - gridLeft))

    private fun clampScroll() {
        scrollX = scrollX.coerceIn(0f, maxScrollX())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clampScroll()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollX = scroller.currX.toFloat().coerceIn(0f, maxScrollX())
            postInvalidateOnAnimation()
        }
    }

    fun ensureVisible(step: Int) {
        val abs = step * cellW() + (step / 4) * groupGap
        val viewW = width - gridLeft
        if (abs < scrollX || abs + cellW() > scrollX + viewW) scrollX = (abs - cellW()).coerceIn(0f, maxScrollX())
    }

    /**
     * Fait glisser la grille d'un geste horizontal. La règle, et le vide sous la grille (l'écran renvoie ses touches ici),
     * servent à ça : sur les cases, un glissé dessine une note.
     */
    fun dragScroll(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                scrollTracker?.recycle()
                scrollTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                scrollDownX = event.x
                scrollStart = scrollX
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                scrollTracker?.addMovement(event)
                scrollX = (scrollStart - (event.x - scrollDownX)).coerceIn(0f, maxScrollX())
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val t = scrollTracker
                if (t != null) {
                    t.addMovement(event)
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        t.computeCurrentVelocity(1000)
                        scroller.fling(scrollX.toInt(), 0, -t.xVelocity.toInt(), 0, 0, maxScrollX().toInt(), 0, 0)
                        postInvalidateOnAnimation()
                    }
                    t.recycle()
                }
                scrollTracker = null
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        if (rows.isEmpty() || width == 0) return
        clampScroll()
        val rh = rowH()
        val cw = cellW()
        val pad = dp(1.2f)
        val left0 = gridLeft

        // Règle, cases, notes et repère : sous un clip, pour que la colonne des touches reste fixe à gauche
        canvas.save()
        canvas.clipRect(left0 - dp(2f), 0f, width.toFloat(), height.toFloat())
        for (s in 0 until stepCount step 4) {
            val x = cellLeft(s) + cw / 2f
            if (x < left0 - cw || x > width + cw) continue
            val local = s % sectionLen
            val letter = if (local == 0) sectionLabels.getOrNull(s / sectionLen) else null
            canvas.drawText(if (letter != null) "$letter ${local + 1}" else (local + 1).toString(), x, topBar - dp(6f), numberPaint)
        }
        val maxX = maxScrollX()
        if (maxX > 0f) {
            // Une fine jauge sous la règle : la partie visible, dans toute la grille
            val trackR = width.toFloat() - dp(4f)
            val frac = (width - left0) / contentW()
            val thumbW = max(dp(24f), (trackR - left0) * frac)
            val thumbL = left0 + (trackR - left0 - thumbW) * (scrollX / maxX)
            fill.color = rowOut
            rect.set(left0, topBar - dp(2.5f), trackR, topBar)
            canvas.drawRect(rect, fill)
            fill.color = labelColor
            rect.set(thumbL, topBar - dp(3f), thumbL + thumbW, topBar)
            canvas.drawRoundRect(rect, dp(1.5f), dp(1.5f), fill)
        }

        // Lignes : de la plus aiguë (haut) à la plus grave (bas)
        for (r in rows.indices) {
            val pitch = rows[rows.size - 1 - r]
            val top = topBar + r * rh
            val inScale = Scales.contains(scale, keyPc, pitch)
            val isRoot = ((pitch - keyPc) % 12 + 12) % 12 == 0
            val base = when {
                isRoot -> rowRoot
                inScale -> rowIn
                else -> rowOut
            }
            for (s in 0 until stepCount) {
                val left = cellLeft(s)
                if (left + cw < left0 - dp(2f) || left > width) continue
                rect.set(left, top + pad, left + cw, top + rh - pad)
                fill.color = if ((s / 4) % 2 == 0 || !inScale) base else rowInAlt
                canvas.drawRoundRect(rect, dp(3f), dp(3f), fill)
            }
        }

        // Notes
        val low = rows.first()
        val high = rows.last()
        for (n in notes()) {
            if (n.pitch < low || n.pitch > high) continue
            drawNote(canvas, n.step, n.length, n.pitch, n.velocity, 1f)
        }
        ghost?.let { drawNote(canvas, it.step, it.length, it.pitch, 1f, 0.55f) }

        if (playhead in 0 until stepCount) {
            val x = cellLeft(playhead)
            fill.color = Color.argb(70, 255, 255, 255)
            canvas.drawRect(x, topBar, x + cw, height.toFloat(), fill)
        }
        drawSelection(canvas)
        if (pasteArmed && pasteStep >= 0 && pasteAnchorPitch >= 0) {
            val lo = rows.first()
            val hi = rows.last()
            for (n in pasteNotes) {
                val pitch = n[0] - pasteLow + pasteAnchorPitch
                val s = pasteStep + n[1]
                if (pitch < lo || pitch > hi || s >= stepCount) continue
                drawNote(canvas, s, min(n[2], stepCount - s), pitch, 1f, 0.6f)
            }
        }
        canvas.restore()

        // La colonne des touches : de vraies touches de piano, une blanche pleine largeur, une noire plus courte et sombre
        for (r in rows.indices) {
            val pitch = rows[rows.size - 1 - r]
            val top = topBar + r * rh
            val inScale = Scales.contains(scale, keyPc, pitch)
            val isRoot = ((pitch - keyPc) % 12 + 12) % 12 == 0
            val pc = ((pitch % 12) + 12) % 12
            val black = pc == 1 || pc == 3 || pc == 6 || pc == 8 || pc == 10
            val pressedNow = pitch == pressedPitch || pitch in lit
            rect.set(0f, top + pad, keyW, top + rh - pad)
            fill.color = if (black) 0xFF24242B.toInt() else 0xFF14141A.toInt()
            canvas.drawRoundRect(rect, dp(4f), dp(4f), fill)
            val keyRight = if (black) keyW * 0.62f else keyW - dp(1f)
            rect.set(0f, top + pad, keyRight, top + rh - pad)
            fill.color = when {
                pressedNow -> accent
                black -> if (inScale) 0xFF3A3A44.toInt() else 0xFF2C2C33.toInt()
                else -> if (inScale) 0xFFF1F1F6.toInt() else 0xFF8A8A94.toInt()
            }
            canvas.drawRoundRect(rect, dp(4f), dp(4f), fill)
            if (isRoot && !black) {
                fill.color = accent
                canvas.drawCircle(keyW - dp(10f), top + rh / 2f, dp(3.5f), fill)
            }
            if (pc == 0) {
                val name = noteNames[pc] + (pitch / 12 - 1)
                label.color = if (pressedNow) 0xFFFFFFFF.toInt() else 0xFF45454F.toInt()
                label.isFakeBoldText = true
                canvas.drawText(name, dp(8f), top + rh / 2f + label.textSize / 3f, label)
            }
        }
    }

    private fun drawNote(canvas: Canvas, step: Int, length: Int, pitch: Int, velocity: Float, alpha: Float) {
        val rowIndex = rows.size - 1 - rows.indexOf(pitch)
        if (rowIndex < 0 || rowIndex >= rows.size) return
        val rh = rowH()
        val top = topBar + rowIndex * rh
        val left = cellLeft(step)
        val right = cellLeft(min(step + length, stepCount) - 1) + cellW()
        rect.set(left, top + dp(2f), right, top + rh - dp(2f))
        fill.color = Color.argb(
            (255 * alpha).toInt(),
            Color.red(accent), Color.green(accent), Color.blue(accent),
        ).let { c ->
            // Plus la note est douce, plus elle est transparente sur la grille
            val a = (Color.alpha(c) * (0.45f + 0.55f * velocity)).toInt()
            Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
        }
        canvas.drawRoundRect(rect, dp(5f), dp(5f), fill)
        // La poignée : deux petits traits au bout droit de la note, à saisir pour l'allonger
        if (alpha >= 1f) {
            fill.color = Color.argb(150, 255, 255, 255)
            val cy = (rect.top + rect.bottom) / 2f
            val gap = dp(2.5f)
            val h = min(dp(14f), (rect.bottom - rect.top) * 0.5f)
            canvas.drawRoundRect(right - dp(9f), cy - h / 2f, right - dp(9f) + dp(1.6f), cy + h / 2f, dp(1f), dp(1f), fill)
            canvas.drawRoundRect(right - dp(9f) + gap + dp(1.6f), cy - h / 2f, right - dp(9f) + gap + dp(3.2f), cy + h / 2f, dp(1f), dp(1f), fill)
        }
    }

    // ---- Toucher ------------------------------------------------------------------------------------

    private class Ghost(val step: Int, val length: Int, val pitch: Int)

    private var ghost: Ghost? = null
    /** La touche qu'un doigt tient dans la colonne des touches (elle s'allume). */
    private var pressedPitch = -1

    /** Les touches que le clavier du bas (ou un clavier MIDI) tient : elles s'allument aussi dans la colonne de gauche. */
    private val lit = HashSet<Int>()

    fun setLit(pitch: Int, on: Boolean) {
        if (if (on) lit.add(pitch) else lit.remove(pitch)) invalidate()
    }

    private var downStep = -1
    private var downPitch = -1
    private var downNote: Note? = null
    private var moved = false
    private var resizing = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var longPressed = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private val longPress = Runnable {
        val note = downNote
        if (note != null && !moved && downStep >= 0) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onEditNote(note, downRawX, downRawY)
        }
    }

    private fun pitchAt(y: Float): Int {
        if (y < topBar || rows.isEmpty()) return -1
        val r = ((y - topBar) / rowH()).toInt()
        if (r !in rows.indices) return -1
        return rows[rows.size - 1 - r]
    }

    private fun stepAt(x: Float): Int {
        val cw = cellW()
        var best = -1
        var bestD = Float.MAX_VALUE
        for (s in 0 until stepCount) {
            val l = cellLeft(s)
            val d = when {
                x < l -> l - x
                x > l + cw -> x - (l + cw)
                else -> 0f
            }
            if (d < bestD) {
                bestD = d
                best = s
            }
        }
        return if (x >= keyW && bestD <= groupGap) best else -1
    }

    private var rulerDrag = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (rulerDrag && event.actionMasked != MotionEvent.ACTION_DOWN) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                rulerDrag = false
                touching = false
            }
            return dragScroll(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                moved = false
                longPressed = false
                ghost = null
                touching = true
                selDownStep = -1
                downRawX = event.rawX
                downRawY = event.rawY
                // La règle (en haut, à droite des touches) fait glisser la grille
                if (event.y < topBar && event.x >= keyW) {
                    rulerDrag = true
                    downStep = -1
                    return dragScroll(event)
                }
                rulerDrag = false
                val pitch = pitchAt(event.y)
                if (event.x < keyW) {
                    if (pitch >= 0) {
                        pressedPitch = pitch
                        listener?.onPreview(pitch)
                        invalidate()
                    }
                    downStep = -1
                    return true
                }
                val step = stepAt(event.x)
                if (pitch < 0 || step < 0) {
                    downStep = -1
                    return true
                }
                if (selectMode) {
                    // En sélection, on ne pose rien : un glissé trace un cadre ; en mode coller, il place l'aperçu
                    downStep = -1
                    selDownPitch = pitch
                    selDownStep = step
                    selX = event.x
                    selY = event.y
                    if (!pasteArmed) {
                        selecting = true
                        anchorPitch = pitch
                        anchorStep = step
                        setSelection(pitch, pitch, step, step)
                        handler.post(selScroll)
                    } else {
                        placing = true
                        updatePlacementEnd()
                        handler.post(selScroll)
                    }
                    return true
                }
                downStep = step
                downPitch = pitch
                listener?.onPreview(pitch)
                val hit = notes().firstOrNull { it.pitch == pitch && step >= it.step && step < it.step + it.length }
                downNote = hit
                // On n'allonge une note qu'en saisissant sa poignée (le dernier pas) ; ailleurs un glissé ne l'efface pas non plus
                resizing = hit != null && step == hit.step + hit.length - 1
                if (hit != null) handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                else ghost = Ghost(step, 1, pitch)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (selectMode && selecting) {
                    selX = event.x
                    selY = event.y
                    updateSelectionEnd()
                    return true
                }
                if (selectMode && placing) {
                    selX = event.x
                    selY = event.y
                    updatePlacementEnd()
                    return true
                }
                if (downStep < 0) {
                    // Glisser le doigt sur les touches fait entendre chaque note
                    if (pressedPitch >= 0 && event.x < keyW) {
                        val p = pitchAt(event.y)
                        if (p >= 0 && p != pressedPitch) {
                            pressedPitch = p
                            listener?.onPreview(p)
                            invalidate()
                        }
                    }
                    return true
                }
                if (kotlin.math.abs(event.rawX - downRawX) > slop || kotlin.math.abs(event.rawY - downRawY) > slop) handler.removeCallbacks(longPress)
                if (longPressed) return true
                val step = stepAt(event.x)
                if (step < 0) return true
                val note = downNote
                if (note != null) {
                    if (step != downStep || moved) {
                        moved = true
                        handler.removeCallbacks(longPress)
                        if (resizing && step >= note.step) {
                            val len = step - note.step + 1
                            if (len != note.length) {
                                listener?.onResizeNote(note, len)
                                // la note a été remplacée : on retrouve la nouvelle pour la suite du geste
                                downNote = notes().firstOrNull { it.pitch == note.pitch && it.step == note.step }
                            }
                        }
                    }
                } else {
                    val len = max(1, step - downStep + 1)
                    if (len != 1) moved = true
                    ghost = Ghost(downStep, min(len, stepCount - downStep), downPitch)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                touching = false
                handler.removeCallbacks(longPress)
                if (selectMode && selDownStep >= 0) {
                    handler.removeCallbacks(selScroll)
                    if (selecting) {
                        selecting = false
                        listener?.onSelectionChanged()
                    } else if (placing) {
                        placing = false
                        listener?.onPasteAt(pasteAnchorPitch, pasteStep)
                    }
                    selDownStep = -1
                    return true
                }
                pressedPitch = -1
                val note = downNote
                val g = ghost
                ghost = null
                if (downStep >= 0 && !longPressed) {
                    if (note != null && !moved) listener?.onRemoveNote(note)
                    else if (note == null && g != null) listener?.onAddNote(g.step, g.pitch, g.length)
                }
                downStep = -1
                downNote = null
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                selecting = false
                placing = false
                selDownStep = -1
                handler.removeCallbacks(selScroll)
                touching = false
                handler.removeCallbacks(longPress)
                pressedPitch = -1
                ghost = null
                downStep = -1
                downNote = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

/** La plus grande hauteur d'une rangée du piano roll, en dp. */
private const val ROW_MAX_DP = 56f
