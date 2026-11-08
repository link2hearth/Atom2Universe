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
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * La grille de pads du Rythme : une ligne par piste, une colonne par pas, chaque pas étant un petit « domino » debout
 * de taille fixe. Un toucher allume ou éteint un pas (et fait entendre la piste), glisser le doigt de côté peint ou
 * efface plusieurs pas d'un coup, un appui long sur un pas allumé change sa force (fort, moyen, doux).
 *
 * Tout a une taille fixe, rien ne s'étire :
 * - la barre du haut (les numéros de pas) fait défiler la grille de gauche à droite quand on y glisse le doigt ;
 * - les lignes défilent de haut en bas (glisser sur les noms, ou un geste surtout vertical sur les pas) ;
 * - un appui long sur le nom d'une piste la soulève : on la glisse à sa nouvelle place ; un appui court sur le nom la met
 *   en sourdine.
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

        /** La ligne [from] est glissée à la place de la ligne [to] (rangs dans la liste affichée). */
        fun onMoveLane(from: Int, to: Int)
    }

    var listener: Listener? = null

    var laneNames: List<String> = emptyList()
        set(value) {
            field = value
            layouts = emptyList()
            clampScroll()
            invalidate()
        }
    var laneColors: IntArray = IntArray(0)
    var stepCount: Int = 16
        set(value) {
            field = value
            clampScroll()
            invalidate()
        }
    var isOn: (lane: Int, step: Int) -> Boolean = { _, _ -> false }
    var velocity: (lane: Int, step: Int) -> Float = { _, _ -> 0f }
    var isMuted: (lane: Int) -> Boolean = { false }

    /** Pas en cours de lecture (-1 = arrêté). La grille le suit quand il sort de l'écran. */
    var playhead: Int = -1
        set(value) {
            if (field != value) {
                field = value
                if (value >= 0 && !touching) ensureVisible(value)
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
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        color = numberColor
        textAlign = Paint.Align.CENTER
    }

    private val rect = RectF()
    private var layouts: List<StaticLayout> = emptyList()

    // Mesures fixes (en pixels, calculées une fois par dessin)
    private var headerW = 0f
    private var topBar = 0f
    private var rowH = 0f
    private var stride = 0f
    private var gridLeft = 0f
    private val groupGap = dp(12f)
    private val pad = dp(4f)

    // Défilement : décalage de la grille (x) et des lignes (y)
    private var scrollX = 0f
    private var scrollY = 0f
    private val flingX = OverScroller(context)
    private val flingY = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun measures() {
        headerW = min(dp(HEADER_DP), width * 0.3f)
        topBar = dp(TOPBAR_DP)
        rowH = dp(ROW_DP)
        stride = dp(CELL_DP)
        gridLeft = headerW + dp(8f)
    }

    private fun contentW(): Float = stepCount * dp(CELL_DP) + (max(1, stepCount / 4) - 1) * groupGap

    private fun maxScrollX(): Float = max(0f, contentW() - (width - (min(dp(HEADER_DP), width * 0.3f) + dp(8f))))

    private fun maxScrollY(): Float = max(0f, dp(TOPBAR_DP) + laneNames.size * dp(ROW_DP) - height)

    private fun clampScroll() {
        scrollX = scrollX.coerceIn(0f, maxScrollX())
        scrollY = scrollY.coerceIn(0f, maxScrollY())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layouts = emptyList()
        clampScroll()
    }

    override fun computeScroll() {
        var again = false
        if (flingX.computeScrollOffset()) {
            scrollX = flingX.currX.toFloat().coerceIn(0f, maxScrollX())
            again = true
        }
        if (flingY.computeScrollOffset()) {
            scrollY = flingY.currY.toFloat().coerceIn(0f, maxScrollY())
            again = true
        }
        if (again) postInvalidateOnAnimation()
    }

    /** Fait défiler la grille pour que le pas [step] soit à l'écran. */
    private fun ensureVisible(step: Int) {
        if (width == 0) return
        measures()
        val left = step * stride + (step / 4) * groupGap
        val viewW = width - gridLeft
        if (left < scrollX) scrollX = left.coerceIn(0f, maxScrollX())
        else if (left + stride > scrollX + viewW) scrollX = (left + stride - viewW).coerceIn(0f, maxScrollX())
    }

    private fun computeLayouts() {
        if (layouts.size != laneNames.size && laneNames.isNotEmpty()) {
            val inner = (headerW - dp(30f)).toInt().coerceAtLeast(10)
            layouts = laneNames.map { name ->
                StaticLayout.Builder.obtain(name, 0, name.length, text, inner)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(2)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .build()
            }
        }
    }

    private fun cellLeft(step: Int): Float = gridLeft + step * stride + (step / 4) * groupGap - scrollX

    // ---- Dessin -------------------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        if (laneNames.isEmpty() || width == 0) return
        measures()
        clampScroll()
        computeLayouts()

        drawTopBar(canvas)

        canvas.save()
        canvas.clipRect(0f, topBar, width.toFloat(), height.toFloat())
        canvas.translate(0f, -scrollY)
        val dragged = dragFrom
        for (lane in laneNames.indices) {
            if (lane == dragged) continue
            val top = topBar + slotOf(lane) * rowH
            if (top + rowH < scrollY + topBar || top > scrollY + height) continue
            drawLane(canvas, lane, top, 1f)
        }
        if (dragged >= 0) {
            // La ligne soulevée suit le doigt, par-dessus les autres
            val top = dragY + scrollY - rowH / 2f
            fill.color = Color.argb(70, 0, 0, 0)
            rect.set(0f, top - dp(2f), width.toFloat(), top + rowH + dp(6f))
            canvas.drawRoundRect(rect, dp(10f), dp(10f), fill)
            drawLane(canvas, dragged, top, 0.95f)
        }
        canvas.restore()
    }

    /** Barre du haut : numéros des pas, repère de lecture, et une petite jauge qui dit où l'on est dans la grille. */
    private fun drawTopBar(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(gridLeft - dp(4f), 0f, width.toFloat(), topBar)
        for (s in 0 until stepCount) {
            val left = cellLeft(s)
            if (left + stride < gridLeft - dp(4f) || left > width) continue
            if (s % 4 == 0) canvas.drawText((s + 1).toString(), left + stride / 2f, topBar - dp(12f), numberPaint)
            if (s == playhead) {
                fill.color = 0xFFFFC857.toInt()
                rect.set(left + dp(4f), topBar - dp(7f), left + stride - dp(4f), topBar - dp(3f))
                canvas.drawRoundRect(rect, dp(2f), dp(2f), fill)
            }
        }
        val maxX = maxScrollX()
        if (maxX > 0f) {
            // La jauge : la partie visible de la grille, dans toute la grille
            val trackL = gridLeft
            val trackR = width.toFloat() - dp(4f)
            val frac = (width - gridLeft) / contentW()
            val thumbW = max(dp(24f), (trackR - trackL) * frac)
            val thumbL = trackL + (trackR - trackL - thumbW) * (scrollX / maxX)
            fill.color = padOffB
            rect.set(trackL, topBar - dp(2.5f), trackR, topBar)
            canvas.drawRect(rect, fill)
            fill.color = numberColor
            rect.set(thumbL, topBar - dp(3f), thumbL + thumbW, topBar)
            canvas.drawRoundRect(rect, dp(1.5f), dp(1.5f), fill)
        }
        canvas.restore()
    }

    private fun drawLane(canvas: Canvas, lane: Int, top: Float, alpha: Float) {
        val color = laneColors.getOrElse(lane) { Color.GRAY }
        val muted = isMuted(lane)

        // En-tête fixe : plaque colorée, pastille de sourdine, nom
        rect.set(0f, top + pad, headerW, top + rowH - pad)
        fill.color = withAlpha(if (muted) padOffB else color, alpha)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), fill)
        val dotX = dp(14f)
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
            canvas.translate(dp(26f), dotY - layout.height / 2f)
            layout.draw(canvas)
            canvas.restore()
        }

        // Dominos : seulement ceux qui sont à l'écran, sous la plaque des noms
        canvas.save()
        canvas.clipRect(gridLeft - dp(2f), top - dp(2f), width.toFloat(), top + rowH + dp(2f))
        for (step in 0 until stepCount) {
            val left = cellLeft(step)
            if (left + stride < gridLeft - dp(2f) || left > width) continue
            rect.set(left + dp(2f), top + pad, left + stride - dp(2f), top + rowH - pad)
            val off = if ((step / 4) % 2 == 0) padOffA else padOffB
            val v = velocity(lane, step)
            fill.color = withAlpha(if (v > 0f) blend(off, color, if (muted) 0.25f else 0.35f + 0.65f * v) else off, alpha)
            canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
            if (step == playhead) {
                fill.color = Color.argb(if (v > 0f) 90 else 55, 255, 255, 255)
                canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
            }
        }
        canvas.restore()
    }

    private fun withAlpha(c: Int, a: Float): Int = if (a >= 1f) c else Color.argb((255 * a).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    private fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt(),
        )
    }

    /** Le rang où une ligne est dessinée : le sien, ou décalé d'un cran pour ouvrir la place de la ligne qu'on glisse. */
    private fun slotOf(lane: Int): Int {
        val from = dragFrom
        if (from < 0) return lane
        val to = dragTarget
        return when {
            from < to && lane in (from + 1)..to -> lane - 1
            from > to && lane in to until from -> lane + 1
            else -> lane
        }
    }

    /** La ligne sous [y] (coordonnée de la vue), ou -1. */
    private fun laneAt(y: Float): Int {
        if (y < topBar || rowH <= 0f) return -1
        val l = ((y - topBar + scrollY) / rowH).toInt()
        return if (l in laneNames.indices) l else -1
    }

    /** Le pas sous [x] (les petits espaces entre groupes comptent pour le pas le plus proche), ou -1. */
    private fun stepAt(x: Float): Int {
        if (x < gridLeft - groupGap || stride <= 0f) return -1
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (s in 0 until stepCount) {
            val left = cellLeft(s)
            val d = when {
                x < left -> left - x
                x > left + stride -> x - (left + stride)
                else -> 0f
            }
            if (d < bestDist) {
                bestDist = d
                best = s
            }
        }
        return if (bestDist <= groupGap) best else -1
    }

    // ---- Toucher ------------------------------------------------------------------------------------------

    private enum class Zone { NONE, TOPBAR, HEADER, PAD }

    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var zone = Zone.NONE
    private var touching = false
    private var downX = 0f
    private var downY = 0f
    private var startScrollX = 0f
    private var startScrollY = 0f
    private var scrollingX = false
    private var scrollingY = false

    private var downLane = -1
    private var downStep = -1
    private var downWasOn = false
    private var painting = false
    private var paintValue = false
    private var longPressed = false
    private var headerLane = -1
    private var lastLane = -1
    private var lastStep = -1

    // Réorganisation des pistes
    private var dragFrom = -1
    private var dragTarget = -1
    private var dragY = 0f

    private val longPress = Runnable {
        if (zone == Zone.PAD && downLane >= 0 && downWasOn && !painting) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onVelocityCycle(downLane, downStep)
            listener?.onPreview(downLane, downStep)
            invalidate()
        }
    }

    private val headerLongPress = Runnable {
        if (zone == Zone.HEADER && headerLane >= 0 && !scrollingY && laneNames.size > 1) {
            dragFrom = headerLane
            dragTarget = headerLane
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            handler.post(autoScroll)
            invalidate()
        }
    }

    /** Pendant qu'on glisse une piste, le bord de l'écran fait défiler la liste. */
    private val autoScroll = object : Runnable {
        override fun run() {
            if (dragFrom < 0) return
            val edge = rowH * 0.7f
            val step = when {
                dragY < topBar + edge -> -dp(10f)
                dragY > height - edge -> dp(10f)
                else -> 0f
            }
            if (step != 0f) {
                scrollY = (scrollY + step).coerceIn(0f, maxScrollY())
                updateDragTarget()
                invalidate()
            }
            handler.postDelayed(this, 16)
        }
    }

    private fun updateDragTarget() {
        dragTarget = (((dragY + scrollY - topBar) / rowH).toInt()).coerceIn(0, laneNames.size - 1)
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

    private fun endScroll(fling: Boolean) {
        val vt = velocityTracker
        if (fling && vt != null) {
            vt.computeCurrentVelocity(1000)
            if (scrollingX) flingX.fling(scrollX.toInt(), 0, -vt.xVelocity.toInt(), 0, 0, maxScrollX().toInt(), 0, 0)
            if (scrollingY) flingY.fling(0, scrollY.toInt(), 0, -vt.yVelocity.toInt(), 0, 0, 0, maxScrollY().toInt())
            postInvalidateOnAnimation()
        }
        vt?.recycle()
        velocityTracker = null
        scrollingX = false
        scrollingY = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                measures()
                flingX.forceFinished(true)
                flingY.forceFinished(true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                touching = true
                scrollingX = false
                scrollingY = false
                startScrollX = scrollX
                startScrollY = scrollY
                downX = event.x
                downY = event.y
                painting = false
                longPressed = false
                downLane = -1
                headerLane = -1
                lastLane = -1
                lastStep = -1
                dragFrom = -1
                parent?.requestDisallowInterceptTouchEvent(true)
                zone = when {
                    event.y < topBar -> Zone.TOPBAR
                    event.x < headerW -> Zone.HEADER
                    else -> Zone.PAD
                }
                when (zone) {
                    Zone.TOPBAR -> return true
                    Zone.HEADER -> {
                        headerLane = laneAt(event.y)
                        if (headerLane >= 0) handler.postDelayed(headerLongPress, ViewConfiguration.getLongPressTimeout().toLong())
                        return true
                    }
                    else -> {
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
                }
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - downX
                val dy = event.y - downY

                if (dragFrom >= 0) {
                    dragY = event.y
                    updateDragTarget()
                    invalidate()
                    return true
                }

                when (zone) {
                    Zone.TOPBAR -> {
                        if (!scrollingX && abs(dx) > slop) scrollingX = true
                        if (scrollingX) {
                            scrollX = (startScrollX - dx).coerceIn(0f, maxScrollX())
                            invalidate()
                        }
                        return true
                    }
                    Zone.HEADER -> {
                        if (!scrollingY && abs(dy) > slop && abs(dy) > abs(dx) && maxScrollY() > 0f) {
                            scrollingY = true
                            handler.removeCallbacks(headerLongPress)
                            headerLane = -1
                        }
                        if (scrollingY) {
                            scrollY = (startScrollY - dy).coerceIn(0f, maxScrollY())
                            invalidate()
                        }
                        return true
                    }
                    Zone.PAD -> {
                        if (!scrollingY && !painting && !longPressed && maxScrollY() > 0f && abs(dy) > slop && abs(dy) > abs(dx)) {
                            // Un geste surtout vertical : on fait défiler les lignes au lieu de peindre
                            scrollingY = true
                            handler.removeCallbacks(longPress)
                            downLane = -1
                        }
                        if (scrollingY) {
                            scrollY = (startScrollY - dy).coerceIn(0f, maxScrollY())
                            invalidate()
                            return true
                        }
                        if (downLane < 0 || longPressed) return true
                        val lane = laneAt(event.y)
                        val step = stepAt(event.x)
                        if (!painting) {
                            val moved = abs(dx) > slop || abs(dy) > slop
                            if (!moved || (lane == downLane && step == downStep)) return true
                            painting = true
                            paintValue = !downWasOn
                            handler.removeCallbacks(longPress)
                            applyPaint(downLane, downStep)
                        }
                        applyPaint(lane, step)
                        return true
                    }
                    else -> return true
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                handler.removeCallbacks(headerLongPress)
                handler.removeCallbacks(autoScroll)
                touching = false
                velocityTracker?.addMovement(event)
                if (dragFrom >= 0) {
                    val from = dragFrom
                    val to = dragTarget
                    dragFrom = -1
                    dragTarget = -1
                    endScroll(false)
                    if (from != to) listener?.onMoveLane(from, to)
                    invalidate()
                    return true
                }
                if (scrollingX || scrollingY) {
                    endScroll(true)
                    return true
                }
                endScroll(false)
                if (zone == Zone.HEADER && headerLane >= 0 && laneAt(event.y) == headerLane && event.x < headerW) {
                    listener?.onMuteToggle(headerLane)
                    invalidate()
                } else if (zone == Zone.PAD && downLane >= 0 && !painting && !longPressed) {
                    listener?.onStepSet(downLane, downStep, !downWasOn)
                    invalidate()
                }
                downLane = -1
                headerLane = -1
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                handler.removeCallbacks(headerLongPress)
                handler.removeCallbacks(autoScroll)
                touching = false
                dragFrom = -1
                dragTarget = -1
                endScroll(false)
                downLane = -1
                headerLane = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(headerLongPress)
        handler.removeCallbacks(autoScroll)
        super.onDetachedFromWindow()
    }

    private companion object {
        /** Mesures fixes en dp : plaque des noms, barre des numéros, hauteur d'une ligne, largeur d'un pas (domino debout). */
        const val HEADER_DP = 120f
        const val TOPBAR_DP = 36f
        const val ROW_DP = 72f
        const val CELL_DP = 44f
    }
}
