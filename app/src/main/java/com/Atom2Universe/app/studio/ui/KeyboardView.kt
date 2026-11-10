package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.studio.music.Scale
import com.Atom2Universe.app.studio.music.StudioRegister
import com.Atom2Universe.app.studio.music.Scales
import kotlin.math.min

/**
 * Le clavier tactile : de vraies touches de piano, blanches et noires, de largeur fixe (on ajoute des octaves quand il y a
 * de la place, on ne les étire pas). Les notes de la gamme portent un point ; « verrouillé sur la gamme », les autres
 * touches sont assombries et ne jouent pas. Plusieurs doigts jouent en même temps ; glisser le doigt d'une touche à
 * l'autre enchaîne les notes (glissando).
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKeyDown(pitch: Int)
        fun onKeyUp(pitch: Int)
    }

    var listener: Listener? = null

    /** La première touche : un do (hauteur MIDI multiple de 12). Le clavier montre ensuite autant d'octaves que la place le permet. */
    var low: Int = 24
        set(value) {
            field = value
            invalidate()
        }

    /** Vrai : seules les notes de la gamme jouent. */
    var locked: Boolean = true
        set(value) {
            field = value
            invalidate()
        }
    var keyPc: Int = 9
        set(value) {
            field = value
            invalidate()
        }
    var scale: Scale = Scales.MINOR_PENT
        set(value) {
            field = value
            invalidate()
        }
    var accent: Int = 0xFF1ABC9C.toInt()
    var noteNames: List<String> = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val whiteKey = if (dark) 0xFFE9E9F0.toInt() else 0xFFFFFFFF.toInt()
    private val whiteOut = if (dark) 0xFF5A5A64.toInt() else 0xFFC6C6CE.toInt()
    private val blackKey = 0xFF1B1B22.toInt()
    private val blackOut = 0xFF2E2E36.toInt()
    private val edge = if (dark) 0xFF14141A.toInt() else 0xFF8C8C96.toInt()
    private val labelColor = 0xFF45454F.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()

    /** Doigt (identifiant) -> hauteur de la touche qu'il tient. */
    private val down = HashMap<Int, Int>()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    /** Prend la place qu'on lui laisse (touches plus hautes en portrait), entre 150 et 300 dp : jamais plus long qu'un vrai clavier. */
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val offered = MeasureSpec.getSize(heightSpec)
        val h = if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED) dp(150f).toInt()
        else offered.coerceIn(dp(150f).toInt().coerceAtMost(offered), dp(300f).toInt())
        setMeasuredDimension(w, h)
    }

    // ---- Géométrie : touches blanches de largeur fixe, centrées ------------------------------------------------

    var high = 48
        set(value) { field = value; invalidate() }
    private val whites get() = StudioRegister.whites
    private fun firstWhite() = whites.binarySearch(low)


    private fun whiteCount(): Int = whites.binarySearch(high) - firstWhite() + 1

    private fun whiteW(): Float = min(dp(WHITE_MAX_DP), width.toFloat() / whiteCount())

    private fun left0(): Float = (width - whiteW() * whiteCount()) / 2f

    private fun isBlack(pc: Int): Boolean = pc == 1 || pc == 3 || pc == 6 || pc == 8 || pc == 10

    /** Rang de la touche blanche de la hauteur [pitch] (une blanche) en partant de [low]. */
    private fun whiteIndex(pitch: Int) = whites.binarySearch(pitch) - firstWhite()

    private fun top() = 0f

    private fun blackH() = height * 0.6f

    private fun blackW() = whiteW() * 0.62f

    /** Hauteurs de touches blanches et noires affichées. */
    private fun pitches(): IntRange = low..high

    private fun active(pitch: Int): Boolean = !locked || Scales.contains(scale, keyPc, pitch)

    private fun blackLeft(pitch: Int): Float {
        // Une noire est à cheval sur la frontière entre la blanche juste en dessous et la suivante
        val prevWhite = whiteIndex(pitch - 1)
        return left0() + (prevWhite + 1) * whiteW() - blackW() / 2f
    }

    // ---- Dessin ------------------------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val ww = whiteW()
        val pressed = down.values
        val gap = dp(1f)
        stroke.strokeWidth = dp(1f)
        stroke.color = edge

        // Les blanches d'abord
        for (p in pitches()) {
            val pc = p % 12
            if (isBlack(pc)) continue
            val x = left0() + whiteIndex(p) * ww
            val isDown = p in pressed
            val offset = if (isDown) dp(2f) else 0f
            rect.set(x + gap, top() + offset, x + ww - gap, height.toFloat() - dp(1f))
            fill.color = when {
                isDown -> accent
                active(p) -> whiteKey
                else -> whiteOut
            }
            canvas.drawRoundRect(rect, dp(6f), dp(6f), fill)
            canvas.drawRoundRect(rect, dp(6f), dp(6f), stroke)
            val key = ((p - keyPc) % 12 + 12) % 12
            val inScale = Scales.contains(scale, keyPc, p)
            if (inScale && active(p)) {
                // Le point de la gamme ; plus grand sur la tonique
                fill.color = if (isDown) 0xFFFFFFFF.toInt() else accent
                canvas.drawCircle(x + ww / 2f, height - dp(34f), if (key == 0) dp(5f) else dp(3f), fill)
            }
            if (pc == 0 || ww >= dp(30f)) {
                label.color = if (isDown) 0xFFFFFFFF.toInt() else labelColor
                canvas.drawText(if (pc == 0) noteNames[pc] + (p / 12 - 1) else noteNames[pc], x + ww / 2f, height - dp(10f), label)
            }
        }

        // Puis les noires, par-dessus
        for (p in pitches()) {
            val pc = p % 12
            if (!isBlack(pc)) continue
            val x = blackLeft(p)
            val isDown = p in pressed
            val offset = if (isDown) dp(2f) else 0f
            rect.set(x, top() + offset, x + blackW(), blackH())
            fill.color = when {
                isDown -> accent
                active(p) -> blackKey
                else -> blackOut
            }
            canvas.drawRoundRect(rect, dp(5f), dp(5f), fill)
            if (Scales.contains(scale, keyPc, p) && active(p)) {
                fill.color = if (isDown) 0xFFFFFFFF.toInt() else accent
                canvas.drawCircle(rect.centerX(), blackH() - dp(12f), dp(3f), fill)
            }
        }
    }

    /** La touche sous le point ([x], [y]) : les noires ont la priorité sur la moitié haute. */
    private fun pitchAt(x: Float, y: Float): Int {
        if (width == 0 || x < left0() || y < 0 || y >= height) return -1
        if (y <= blackH()) {
            for (p in pitches()) {
                val pc = p % 12
                if (!isBlack(pc)) continue
                val bx = blackLeft(p)
                if (x >= bx && x <= bx + blackW()) return if (active(p)) p else -1
            }
        }
        val i = ((x - left0()) / whiteW()).toInt()
        if (i < 0 || i >= whiteCount()) return -1
        val p = whites[firstWhite() + i]
        return if (active(p)) p else -1
    }

    private fun setFinger(id: Int, pitch: Int) {
        val old = down[id]
        if (old == pitch || (old == null && pitch < 0)) return
        if (old != null) {
            down.remove(id)
            if (old !in down.values) listener?.onKeyUp(old)
        }
        if (pitch >= 0) {
            val held = pitch in down.values
            down[id] = pitch
            if (!held) listener?.onKeyDown(pitch)
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val pitch = pitchAt(event.getX(idx), event.getY(idx))
                setFinger(id, pitch)
            }

            MotionEvent.ACTION_MOVE -> {
                for (k in 0 until event.pointerCount) {
                    val id = event.getPointerId(k)
                    setFinger(id, pitchAt(event.getX(k), event.getY(k)))
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val idx = event.actionIndex
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    releaseAll()
                } else {
                    setFinger(event.getPointerId(idx), -1)
                }
            }
        }
        return true
    }

    /** Relâche tout (écran quitté, annulation du geste). */
    fun releaseAll() {
        val held = down.values.toSet()
        down.clear()
        for (p in held) listener?.onKeyUp(p)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        releaseAll()
        super.onDetachedFromWindow()
    }

    private companion object {
        /** Largeur d'une touche blanche : au plus 64 dp ; au moins 48 dp pour décider combien d'octaves tiennent. */
        const val WHITE_MAX_DP = 108f
    }
}
