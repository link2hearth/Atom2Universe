package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
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

        /** Le cadre de sélection vient d'être tracé (ou effacé) : l'écran met à jour ses boutons. */
        fun onSelectionChanged()

        /** Un toucher en mode « coller » : on colle à partir de ce pas, sur cette piste (rang d'affichage). */
        fun onPasteAt(lane: Int, step: Int)

        /** Appui long sur un pas allumé : l'écran ouvre sa bulle de réglages, près du doigt (position à l'écran). */
        fun onStepEdit(lane: Int, step: Int, screenX: Float, screenY: Float)

        fun onPreview(lane: Int, step: Int)
        fun onMuteToggle(lane: Int)

        /** La ligne [from] est glissée à la place de la ligne [to] (rangs dans la liste affichée). */
        fun onMoveLane(from: Int, to: Int)

        /** Un toucher sur la rangée vide du bas : l'utilisateur veut ajouter une piste. */
        fun onAddTrack()
    }

    var listener: Listener? = null

    var laneNames: List<String> = emptyList()
        set(value) {
            field = value
            clampScroll()
            invalidate()
        }
    var laneColors: IntArray = IntArray(0)

    /** Le dessin de l'instrument de chaque piste ([InstrumentIcons]) : il tient lieu de nom sur la plaque colorée. */
    var laneIcons: IntArray = IntArray(0)

    /** Quand plusieurs motifs sont affichés à la suite : leur longueur et leur lettre (A à D). Les numéros repartent de 1 à chaque motif. */
    var sectionLen: Int = Int.MAX_VALUE
    var sectionLabels: List<String> = emptyList()

    /** Un petit chiffre dans le coin de la plaque (« 2 » pour le deuxième synthé) ; vide : rien. Une entrée par piste. */
    var laneBadges: List<String> = emptyList()

    /** Le texte de la rangée vide posée sous les pistes (ajouter une piste) ; `null` : pas de rangée. */
    var addLabel: String? = null
        set(value) {
            field = value
            clampScroll()
            invalidate()
        }

    private fun rowCount(): Int = laneNames.size + if (addLabel != null) 1 else 0
    var stepCount: Int = 16
        set(value) {
            field = value
            clampScroll()
            invalidate()
        }
    var isOn: (lane: Int, step: Int) -> Boolean = { _, _ -> false }
    var velocity: (lane: Int, step: Int) -> Float = { _, _ -> 0f }
    var isMuted: (lane: Int) -> Boolean = { false }

    /** Vrai pour un pas qui prolonge une note commencée avant (longueur d'une note) : il se dessine en teinte douce. */
    var isTail: (lane: Int, step: Int) -> Boolean = { _, _ -> false }

    /** Quand un pas allumé joue ([com.Atom2Universe.app.studio.engine.StepCond]) ; un pas qui ne joue pas à chaque tour porte une marque. */
    var cond: (lane: Int, step: Int) -> Int = { _, _ -> 0 }

    /** Les marques des pas « parfois », dans l'ordre des conditions (la première, « toujours », n'est pas dessinée). */
    var condMarks: List<String> = emptyList()

    // ---- Sélection : un cadre de pistes et de pas ---------------------------------------------------------------

    /** Un cadre : les pistes (rang d'affichage) de [laneFrom] à [laneTo] et les pas de [stepFrom] à [stepTo], bornes comprises. */
    class Frame(val laneFrom: Int, val laneTo: Int, val stepFrom: Int, val stepTo: Int)

    /** Vrai : un glissé sur les pas trace un cadre de sélection au lieu de peindre des pas. */
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

    /** La forme à coller, montrée en fantôme : pour chaque case, le rang d'affichage de la piste et le pas relatif. */
    var pasteCells: List<IntArray> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    /** Le pas où la forme se posera (-1 : pas encore placée). */
    var pasteStep = -1
        private set
    private var placing = false

    fun placePaste(step: Int) {
        pasteStep = step.coerceIn(0, max(0, stepCount - 1))
        invalidate()
    }

    private fun updatePlacementEnd() {
        val step = stepAt(selX).let { if (it >= 0) it else if (selX < gridLeft) 0 else stepCount - 1 }
        placePaste(step)
    }

    private fun drawPaste(canvas: Canvas) {
        if (!pasteArmed || pasteStep < 0 || pasteCells.isEmpty()) return
        canvas.save()
        canvas.clipRect(gridLeft - dp(2f), 0f, width.toFloat(), height.toFloat() + scrollY)
        for (c in pasteCells) {
            val s = pasteStep + c[1]
            if (c[0] !in laneNames.indices || s >= stepCount) continue
            val left = cellLeft(s)
            val top = topBar + c[0] * rowH
            rect.set(left + dp(2f), top + pad, left + stride - dp(2f), top + rowH - pad)
            fill.color = Color.argb(90, 255, 255, 255)
            canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
            ring.color = Color.WHITE
            ring.strokeWidth = dp(2f)
            canvas.drawRoundRect(rect, dp(8f), dp(8f), ring)
        }
        canvas.restore()
    }

    var selection: Frame? = null
        private set
    private var selecting = false
    private var anchorLane = 0
    private var anchorStep = 0
    private var selX = 0f
    private var selY = 0f

    fun setSelection(laneFrom: Int, laneTo: Int, stepFrom: Int, stepTo: Int) {
        selection = Frame(minOf(laneFrom, laneTo), maxOf(laneFrom, laneTo), minOf(stepFrom, stepTo), maxOf(stepFrom, stepTo))
        invalidate()
    }

    fun clearSelection() {
        selection = null
        invalidate()
    }

    /** Étire le cadre jusqu'au doigt ; hors de la grille, le bord le plus proche. */
    private fun updateSelectionEnd() {
        val lane = laneAt(selY).let { if (it >= 0) it else if (selY < topBar) 0 else laneNames.size - 1 }.coerceIn(0, laneNames.size - 1)
        val step = stepAt(selX).let { if (it >= 0) it else if (selX < gridLeft) 0 else stepCount - 1 }
        setSelection(anchorLane, lane, anchorStep, step)
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
        val left = cellLeft(f.stepFrom) - dp(1f)
        val right = cellLeft(f.stepTo) + stride + dp(1f)
        val top = topBar + f.laneFrom * rowH + pad - dp(2f)
        val bottom = topBar + (f.laneTo + 1) * rowH - pad + dp(2f)
        canvas.save()
        canvas.clipRect(gridLeft - dp(2f), 0f, width.toFloat(), height.toFloat() + scrollY)
        rect.set(left, top, right, bottom)
        fill.color = Color.argb(46, 255, 255, 255)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), fill)
        ring.color = Color.WHITE
        ring.strokeWidth = dp(2f)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), ring)
        canvas.restore()
    }

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
    private val condPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        color = Color.WHITE
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        color = numberColor
        textAlign = Paint.Align.CENTER
    }

    private val rect = RectF()

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
        rowH = rowHeight()
        stride = dp(CELL_DP)
        gridLeft = headerW + dp(8f)
    }

    private fun contentW(): Float = stepCount * dp(CELL_DP) + (max(1, stepCount / 4) - 1) * groupGap

    private fun maxScrollX(): Float = max(0f, contentW() - (width - (min(dp(HEADER_DP), width * 0.3f) + dp(8f))))

    private fun maxScrollY(): Float = max(0f, dp(TOPBAR_DP) + rowCount() * rowHeight() - height)

    /**
     * La hauteur d'une ligne : les lignes se partagent la hauteur de la vue (pas de vide sous la grille), entre [ROW_MIN_DP] et
     * [ROW_MAX_DP] ; quand elles ne tiennent pas, la grille défile.
     */
    private fun rowHeight(): Float {
        val rows = rowCount()
        if (rows == 0 || height == 0) return dp(ROW_DP)
        return ((height - dp(TOPBAR_DP)) / rows).coerceIn(dp(ROW_MIN_DP), dp(ROW_MAX_DP))
    }

    private fun clampScroll() {
        scrollX = scrollX.coerceIn(0f, maxScrollX())
        scrollY = scrollY.coerceIn(0f, maxScrollY())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
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
    fun ensureVisible(step: Int) {
        if (width == 0) return
        measures()
        val left = step * stride + (step / 4) * groupGap
        val viewW = width - gridLeft
        if (left < scrollX) scrollX = left.coerceIn(0f, maxScrollX())
        else if (left + stride > scrollX + viewW) scrollX = (left + stride - viewW).coerceIn(0f, maxScrollX())
    }

    private fun cellLeft(step: Int): Float = gridLeft + step * stride + (step / 4) * groupGap - scrollX

    // ---- Dessin -------------------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        if (laneNames.isEmpty() || width == 0) return
        measures()
        clampScroll()

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
        addLabel?.let { drawAddRow(canvas, topBar + laneNames.size * rowH, it) }
        drawSelection(canvas)
        drawPaste(canvas)
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

    /** La rangée vide du bas : un cadre en pointillés avec « + Ajouter une piste ». */
    private fun drawAddRow(canvas: Canvas, top: Float, label: String) {
        ring.color = numberColor
        ring.strokeWidth = dp(2f)
        ring.pathEffect = DashPathEffect(floatArrayOf(dp(8f), dp(6f)), 0f)
        rect.set(dp(2f), top + pad, width - dp(2f), top + rowH - pad)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), ring)
        ring.pathEffect = null
        val w = text.measureText(label)
        text.color = numberColor
        canvas.drawText(label, dp(16f), top + rowH / 2f + text.textSize / 3f, text)
    }

    /** Barre du haut : numéros des pas, repère de lecture, et une petite jauge qui dit où l'on est dans la grille. */
    private fun drawTopBar(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(gridLeft - dp(4f), 0f, width.toFloat(), topBar)
        for (s in 0 until stepCount) {
            val left = cellLeft(s)
            if (left + stride < gridLeft - dp(4f) || left > width) continue
            if (s % 4 == 0) {
                val local = s % sectionLen
                val letter = if (local == 0) sectionLabels.getOrNull(s / sectionLen) else null
                canvas.drawText(if (letter != null) "$letter ${local + 1}" else (local + 1).toString(), left + stride / 2f, topBar - dp(12f), numberPaint)
            }
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

        // En-tête fixe : plaque colorée (grise si la piste est coupée) et, au centre, le dessin de l'instrument
        rect.set(0f, top + pad, headerW, top + rowH - pad)
        fill.color = withAlpha(if (muted) padOffB else color, alpha)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), fill)
        val icon = withAlpha(if (muted) numberColor else Color.WHITE, alpha)
        InstrumentIcons.draw(canvas, laneIcons.getOrElse(lane) { 0 }, headerW / 2f, top + rowH / 2f, rowH * 0.5f, icon)
        val badge = laneBadges.getOrNull(lane).orEmpty()
        if (badge.isNotEmpty()) {
            badgePaint.color = icon
            canvas.drawText(badge, headerW - dp(7f), top + pad + dp(15f), badgePaint)
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
            val tail = v <= 0f && isTail(lane, step)
            fill.color = withAlpha(
                when {
                    v > 0f -> blend(off, color, if (muted) 0.25f else 0.35f + 0.65f * v)
                    tail -> blend(off, color, if (muted) 0.12f else 0.3f)
                    else -> off
                },
                alpha,
            )
            canvas.drawRoundRect(rect, dp(8f), dp(8f), fill)
            if (v > 0f) {
                val c = cond(lane, step)
                if (c > 0) condMarks.getOrNull(c)?.let { mark ->
                    condPaint.color = withAlpha(Color.WHITE, alpha * 0.9f)
                    canvas.drawText(mark, rect.centerX(), rect.bottom - dp(8f), condPaint)
                }
            }
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
        return if (l in 0 until rowCount()) l else -1
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

    private enum class Zone { NONE, TOPBAR, HEADER, PAD, ADD }

    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var zone = Zone.NONE
    private var touching = false
    private var downX = 0f
    private var downY = 0f
    private var downRawX = 0f
    private var downRawY = 0f
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
            listener?.onStepEdit(downLane, downStep, downRawX, downRawY)
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
        if (lane < 0 || lane >= laneNames.size || step < 0 || (lane == lastLane && step == lastStep)) return
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
                downRawX = event.rawX
                downRawY = event.rawY
                painting = false
                longPressed = false
                downLane = -1
                headerLane = -1
                lastLane = -1
                lastStep = -1
                dragFrom = -1
                parent?.requestDisallowInterceptTouchEvent(true)
                val rowAtDown = laneAt(event.y)
                // Le vide sous les pistes sert aussi à faire glisser la grille de gauche à droite, comme la barre du haut
                val belowTracks = event.y > topBar + rowCount() * rowH - scrollY
                zone = when {
                    event.y < topBar || belowTracks -> Zone.TOPBAR
                    addLabel != null && rowAtDown == laneNames.size -> Zone.ADD
                    event.x < headerW -> Zone.HEADER
                    else -> Zone.PAD
                }
                when (zone) {
                    Zone.TOPBAR, Zone.ADD -> return true
                    Zone.HEADER -> {
                        headerLane = laneAt(event.y)
                        if (headerLane >= 0) handler.postDelayed(headerLongPress, ViewConfiguration.getLongPressTimeout().toLong())
                        return true
                    }
                    else -> {
                        val lane = laneAt(event.y)
                        val step = stepAt(event.x)
                        if (lane < 0 || step < 0) return true
                        if (selectMode) {
                            // En sélection, on ne peint pas : un glissé trace un cadre ; en mode coller, il place l'aperçu
                            downLane = lane
                            downStep = step
                            selX = event.x
                            selY = event.y
                            if (!pasteArmed) {
                                selecting = true
                                anchorLane = lane
                                anchorStep = step
                                setSelection(lane, lane, step, step)
                                handler.post(selScroll)
                            } else {
                                placing = true
                                updatePlacementEnd()
                                handler.post(selScroll)
                            }
                            return true
                        }
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
                    Zone.HEADER, Zone.ADD -> {
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
                        if (selectMode) {
                            if (selecting) {
                                selX = event.x
                                selY = event.y
                                updateSelectionEnd()
                            } else if (placing) {
                                selX = event.x
                                selY = event.y
                                updatePlacementEnd()
                            }
                            return true
                        }
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
                if (selectMode && zone == Zone.PAD) {
                    handler.removeCallbacks(selScroll)
                    if (selecting) {
                        selecting = false
                        listener?.onSelectionChanged()
                    } else if (placing) {
                        placing = false
                        listener?.onPasteAt(downLane, pasteStep)
                    }
                    downLane = -1
                    endScroll(false)
                    return true
                }
                if (scrollingX || scrollingY) {
                    endScroll(true)
                    return true
                }
                endScroll(false)
                if (zone == Zone.ADD && laneAt(event.y) == laneNames.size && abs(event.y - downY) <= slop) {
                    listener?.onAddTrack()
                } else if (zone == Zone.HEADER && headerLane >= 0 && laneAt(event.y) == headerLane && event.x < headerW) {
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
                selecting = false
                placing = false
                handler.removeCallbacks(selScroll)
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
        handler.removeCallbacks(selScroll)
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(headerLongPress)
        handler.removeCallbacks(autoScroll)
        super.onDetachedFromWindow()
    }

    private companion object {
        /** Mesures fixes en dp : plaque des noms, barre des numéros, hauteur d'une ligne, largeur d'un pas (domino debout). */
        const val HEADER_DP = 60f
        const val TOPBAR_DP = 36f
        const val ROW_DP = 72f
        const val ROW_MIN_DP = 60f
        const val ROW_MAX_DP = 96f
        const val CELL_DP = 44f
    }
}
