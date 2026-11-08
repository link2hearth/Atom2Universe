package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
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
    }

    var listener: Listener? = null

    /** Les hauteurs MIDI affichées, de la plus grave à la plus aiguë. */
    var rows: IntArray = IntArray(0)
        set(value) {
            field = value
            invalidate()
        }
    var stepCount: Int = 16
        set(value) {
            field = value
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

    private val keyW get() = dp(60f)
    private val topBar get() = dp(20f)
    private val groupGap = dp(5f)

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun rowH(): Float = if (rows.isEmpty()) 0f else (height - topBar) / rows.size

    private fun cellW(): Float {
        val groups = max(1, stepCount / 4)
        return (width - keyW - dp(6f) - groupGap * (groups - 1)) / stepCount
    }

    private fun cellLeft(step: Int): Float = keyW + dp(6f) + step * cellW() + (step / 4) * groupGap

    override fun onDraw(canvas: Canvas) {
        if (rows.isEmpty() || width == 0) return
        val rh = rowH()
        val cw = cellW()
        val pad = dp(1.2f)

        for (s in 0 until stepCount step 4) {
            canvas.drawText((s + 1).toString(), cellLeft(s) + cw / 2f, topBar - dp(6f), numberPaint)
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

            // Touche
            rect.set(0f, top + pad, keyW, top + rh - pad)
            fill.color = base
            canvas.drawRoundRect(rect, dp(5f), dp(5f), fill)
            val name = noteNames[((pitch % 12) + 12) % 12] + (pitch / 12 - 1)
            label.color = if (isRoot) accent else labelColor
            label.isFakeBoldText = isRoot
            canvas.drawText(name, dp(8f), top + rh / 2f + label.textSize / 3f, label)

            // Cases
            for (s in 0 until stepCount) {
                val left = cellLeft(s)
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
    }

    // ---- Toucher ------------------------------------------------------------------------------------

    private class Ghost(val step: Int, val length: Int, val pitch: Int)

    private var ghost: Ghost? = null
    private var downStep = -1
    private var downPitch = -1
    private var downNote: Note? = null
    private var resizing = false
    private var moved = false

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                moved = false
                resizing = false
                ghost = null
                val pitch = pitchAt(event.y)
                if (event.x < keyW) {
                    if (pitch >= 0) listener?.onPreview(pitch)
                    downStep = -1
                    return true
                }
                val step = stepAt(event.x)
                if (pitch < 0 || step < 0) {
                    downStep = -1
                    return true
                }
                downStep = step
                downPitch = pitch
                listener?.onPreview(pitch)
                val hit = notes().firstOrNull { it.pitch == pitch && step >= it.step && step < it.step + it.length }
                downNote = hit
                if (hit != null) {
                    resizing = step == hit.step + hit.length - 1
                } else {
                    ghost = Ghost(step, 1, pitch)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (downStep < 0) return true
                val step = stepAt(event.x)
                if (step < 0) return true
                val note = downNote
                if (note != null) {
                    if (resizing && step >= note.step) {
                        val len = step - note.step + 1
                        if (len != note.length) {
                            moved = true
                            listener?.onResizeNote(note, len)
                            // la note a été remplacée : on retrouve la nouvelle pour la suite du geste
                            downNote = notes().firstOrNull { it.pitch == note.pitch && it.step == note.step }
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
                val note = downNote
                val g = ghost
                ghost = null
                if (downStep >= 0) {
                    if (note != null && !moved) listener?.onRemoveNote(note)
                    else if (note == null && g != null) listener?.onAddNote(g.step, g.pitch, g.length)
                }
                downStep = -1
                downNote = null
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
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
