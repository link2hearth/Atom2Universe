package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * La grille de pads du Rythme : une ligne par instrument, une colonne par pas. Un toucher allume ou éteint un
 * pas (et fait entendre l'instrument), glisser le doigt peint ou efface plusieurs pas d'un coup, un appui long
 * sur un pas allumé change sa force (fort, moyen, doux). Toucher le nom d'une ligne la met en sourdine.
 *
 * La vue ne possède aucune donnée : elle interroge [isOn], [velocity] et [isMuted] à chaque dessin et rend la
 * main par [listener].
 */
class StepGridView(context: Context) : View(context) {

    interface Listener {
        fun onStepSet(lane: Int, step: Int, on: Boolean)
        fun onVelocityCycle(lane: Int, step: Int)
        fun onPreview(lane: Int, step: Int)
        fun onMuteToggle(lane: Int)
    }

    var listener: Listener? = null

    var laneNames: List<String> = emptyList()
        set(value) {
            field = value
            layouts = emptyList()
            invalidate()
        }
    var laneColors: IntArray = IntArray(0)
    var stepCount: Int = 16
        set(value) {
            field = value
            invalidate()
        }
    var isOn: (lane: Int, step: Int) -> Boolean = { _, _ -> false }
    var velocity: (lane: Int, step: Int) -> Float = { _, _ -> 0f }
    var isMuted: (lane: Int) -> Boolean = { false }

    /** Pas en cours de lecture (-1 = arrêté). */
    var playhead: Int = -1
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    private val padOffA = if (dark) 0xFF3B3B42.toInt() else 0xFFD8D8DE.toInt()
    private val padOffB = if (dark) 0xFF4A4A52.toInt() else 0xFFC8C8D0.toInt()
    private val numberColor = if (dark) 0xFF9A9AA4.toInt() else 0xFF6A6A74.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.WHITE
    }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        color = numberColor
        textAlign = Paint.Align.CENTER
    }

    private val rect = RectF()
    private var layouts: List<StaticLayout> = emptyList()

    // Mise en page calculée à chaque mesure
    private var headerW = 0f
    private var topBar = 0f
    private var rowH = 0f
    private var cellW = 0f
    private var gridLeft = 0f
    private val groupGap = dp(6f)
    private val pad = dp(2.5f)

    // Geste en cours
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var downLane = -1
    private var downStep = -1
    private var downWasOn = false
    private var painting = false
    private var paintValue = false
    private var longPressed = false
    private var headerLane = -1
    private var lastLane = -1
    private var lastStep = -1
    private val longPress = Runnable {
        if (downLane >= 0 && downWasOn && !painting) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onVelocityCycle(downLane, downStep)
            listener?.onPreview(downLane, downStep)
            invalidate()
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layouts = emptyList()
    }

    private fun computeLayout() {
        val lanes = max(laneNames.size, 1)
        headerW = min(dp(104f), width * 0.2f)
        topBar = dp(20f)
        rowH = (height - topBar) / lanes
        gridLeft = headerW + dp(8f)
        val groups = max(1, stepCount / 4)
        cellW = (width - gridLeft - groupGap * (groups - 1)) / stepCount
        if (layouts.size != laneNames.size && laneNames.isNotEmpty()) {
            val inner = (headerW - dp(26f)).toInt().coerceAtLeast(10)
            layouts = laneNames.map { name ->
                StaticLayout.Builder.obtain(name, 0, name.length, text, inner)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(2)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .build()
            }
        }
    }

    private fun cellLeft(step: Int): Float = gridLeft + step * cellW + (step / 4) * groupGap

    override fun onDraw(canvas: Canvas) {
        if (laneNames.isEmpty() || width == 0) return
        computeLayout()

        // Numéros des temps
        for (s in 0 until stepCount step 4) {
            canvas.drawText((s + 1).toString(), cellLeft(s) + cellW / 2f, topBar - dp(6f), numberPaint)
        }

        for (lane in laneNames.indices) {
            val top = topBar + lane * rowH
            val color = laneColors.getOrElse(lane) { Color.GRAY }
            val muted = isMuted(lane)

            // En-tête : plaque colorée, pastille de sourdine, nom
            rect.set(0f, top + pad, headerW, top + rowH - pad)
            fill.color = if (muted) padOffB else color
            canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
            val dotX = dp(13f)
            val dotY = top + rowH / 2f
            if (muted) {
                ring.color = numberColor
                canvas.drawCircle(dotX, dotY, dp(5f), ring)
            } else {
                fill.color = Color.WHITE
                canvas.drawCircle(dotX, dotY, dp(5f), fill)
            }
            val layout = layouts.getOrNull(lane)
            if (layout != null) {
                text.color = if (muted) numberColor else Color.WHITE
                canvas.save()
                canvas.translate(dp(24f), dotY - layout.height / 2f)
                layout.draw(canvas)
                canvas.restore()
            }

            // Pads
            for (step in 0 until stepCount) {
                val left = cellLeft(step)
                rect.set(left + pad, top + pad, left + cellW - pad, top + rowH - pad)
                val off = if ((step / 4) % 2 == 0) padOffA else padOffB
                val v = velocity(lane, step)
                fill.color = if (v > 0f) blend(off, color, if (muted) 0.25f else 0.35f + 0.65f * v) else off
                canvas.drawRoundRect(rect, dp(6f), dp(6f), fill)
                if (step == playhead) {
                    fill.color = Color.argb(if (v > 0f) 90 else 55, 255, 255, 255)
                    canvas.drawRoundRect(rect, dp(6f), dp(6f), fill)
                }
            }
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt(),
        )
    }

    /** La ligne sous [y], ou -1. */
    private fun laneAt(y: Float): Int {
        if (y < topBar || rowH <= 0f) return -1
        val l = ((y - topBar) / rowH).toInt()
        return if (l in laneNames.indices) l else -1
    }

    /** Le pas sous [x] (les petits espaces entre groupes comptent pour le pas le plus proche), ou -1. */
    private fun stepAt(x: Float): Int {
        if (x < gridLeft - groupGap || cellW <= 0f) return -1
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (s in 0 until stepCount) {
            val left = cellLeft(s)
            val d = when {
                x < left -> left - x
                x > left + cellW -> x - (left + cellW)
                else -> 0f
            }
            if (d < bestDist) {
                bestDist = d
                best = s
            }
        }
        return if (bestDist <= groupGap) best else -1
    }

    private fun applyPaint(lane: Int, step: Int) {
        if (lane < 0 || step < 0 || (lane == lastLane && step == lastStep)) return
        lastLane = lane
        lastStep = step
        if (isOn(lane, step) != paintValue) {
            listener?.onStepSet(lane, step, paintValue)
            if (paintValue) listener?.onPreview(lane, step)
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                painting = false
                longPressed = false
                downLane = -1
                headerLane = -1
                lastLane = -1
                lastStep = -1
                parent?.requestDisallowInterceptTouchEvent(true)
                if (event.x < headerW) {
                    headerLane = laneAt(event.y)
                    return true
                }
                val lane = laneAt(event.y)
                val step = stepAt(event.x)
                if (lane < 0 || step < 0) return true
                downLane = lane
                downStep = step
                downWasOn = isOn(lane, step)
                listener?.onPreview(lane, step)
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (downLane < 0 || longPressed) return true
                val lane = laneAt(event.y)
                val step = stepAt(event.x)
                if (!painting) {
                    val moved = abs(event.x - downX) > slop || abs(event.y - downY) > slop
                    if (!moved || (lane == downLane && step == downStep)) return true
                    painting = true
                    paintValue = !downWasOn
                    handler.removeCallbacks(longPress)
                    applyPaint(downLane, downStep)
                }
                applyPaint(lane, step)
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (headerLane >= 0 && laneAt(event.y) == headerLane && event.x < headerW) {
                    listener?.onMuteToggle(headerLane)
                    invalidate()
                } else if (downLane >= 0 && !painting && !longPressed) {
                    listener?.onStepSet(downLane, downStep, !downWasOn)
                    invalidate()
                }
                downLane = -1
                headerLane = -1
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                downLane = -1
                headerLane = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPress)
        super.onDetachedFromWindow()
    }
}
