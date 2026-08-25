package com.Atom2Universe.app.crypto

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Une bougie OHLCV issue d'une kline Binance. */
data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

/**
 * Ligne de tendance dessinée par l'utilisateur, ancrée en (temps, prix) : elle garde sa
 * place quel que soit le timeframe affiché. [colorIndex] indexe [CandleChartView.LINE_COLORS].
 */
data class TrendLine(
    var time1: Long,
    var price1: Double,
    var time2: Long,
    var price2: Double,
    var colorIndex: Int = 0
)

/**
 * Nombre de décimales pertinent pour afficher un prix : 2 pour les gros prix (BTC),
 * davantage pour les petits actifs (DOGE, SHIB…) sinon tout serait arrondi à 0,00.
 */
internal fun cryptoPriceDecimals(price: Double): Int {
    val p = abs(price)
    return when {
        p >= 10 -> 2
        p >= 0.1 -> 4
        p >= 0.001 -> 6
        else -> 8
    }
}

/**
 * Graphique en chandeliers interactif (vert haussier / rouge baissier), façon TradingView.
 *
 * Le jeu complet de bougies (jusqu'à ~1000) est chargé en mémoire ; la vue n'en affiche
 * qu'une fenêtre. Gestes : glisser à un doigt = déplacer le graphe librement dans le temps
 * (sur-défilement possible au-delà des données, avec inertie) et en prix (échelle manuelle) ;
 * pincer horizontalement = élargir/rétrécir les bougies, pincer verticalement = les étirer ou
 * les aplatir (zoom de l'échelle de prix) ; double-tap = retour aux dernières bougies avec
 * échelle automatique ; appui long puis glisser = viseur (crosshair) affichant prix + date.
 *
 * Habillage façon TradingView : ligne pointillée au dernier prix avec bulle sur l'axe,
 * barres de volume en bas, légende OHLC en haut à gauche, quadrillage horizontal et vertical.
 */
class CandleChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        /** Palette des lignes de tendance (indexée par [TrendLine.colorIndex]). */
        val LINE_COLORS = intArrayOf(
            0xFFE2E8F0.toInt(), // blanc cassé
            0xFFF59E0B.toInt(), // ambre
            0xFF3B82F6.toInt(), // bleu
            0xFF4ADE80.toInt(), // vert
            0xFFF87171.toInt(), // rouge
            0xFFA78BFA.toInt() // violet
        )
    }

    private enum class LineDrag { NONE, POINT1, POINT2, BODY }

    private val upColor = ContextCompat.getColor(context, R.color.crypto_widget_up)
    private val downColor = ContextCompat.getColor(context, R.color.crypto_widget_down)
    private val gridColor = ContextCompat.getColor(context, R.color.crypto_chart_grid)
    private val axisTextColor = ContextCompat.getColor(context, R.color.crypto_chart_axis_text)
    private val crosshairColor = ContextCompat.getColor(context, R.color.crypto_chart_crosshair)
    private val bubbleTextColor = ContextCompat.getColor(context, R.color.crypto_widget_background)

    private val density = context.resources.displayMetrics.density

    private val wickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, density)
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val volumePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = gridColor
    }
    private val axisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = axisTextColor
        textSize = 11f * density
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = crosshairColor
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 6f * density), 0f)
    }
    private val crosshairLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = crosshairColor
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bubbleTextColor
        textSize = 11f * density
    }
    private val lastPricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
    }
    private val lastPriceLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val legendTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f * density
    }
    private val maPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = ContextCompat.getColor(context, R.color.crypto_chart_ma)
    }
    private val maPath = Path()
    private val legendBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ColorUtils.setAlphaComponent(bubbleTextColor, 190)
    }
    private val trendLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val handleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xFFFFFFFF.toInt()
    }
    private val toolbarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ColorUtils.setAlphaComponent(0xFF334155.toInt(), 230)
    }
    private val toolbarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF8FAFC.toInt()
        textSize = 13f * density
    }
    private val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var priceAxisWidth = 64f * density
    private val timeAxisHeight = 20f * density
    private val verticalPadding = 12f * density

    /** Fraction de la hauteur du graphe réservée aux barres de volume (en bas). */
    private val volumeZoneFraction = 0.18f

    private val priceFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = true
    }
    private val percentFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private var candles: List<Candle> = emptyList()
    private var interval: CryptoCandleInterval = CryptoCandleInterval.M15

    private val defaultVisible = 100
    private val minVisible = 8f
    private var visibleCount = defaultVisible.toFloat()
    private var scrollOffset = 0f

    private var minLow = 0.0
    private var maxHigh = 0.0
    private var maxVolume = 0.0

    /** Mapping logarithmique effectif pour la frame courante (échelle log + prix > 0). */
    private var useLogMapping = false
    private var logMin = 0.0
    private var logMax = 0.0

    /** Échelle de prix logarithmique (façon bouton « log » de TradingView). */
    var logScale: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Période de la moyenne mobile simple en surimpression (0 = masquée). */
    var movingAveragePeriod: Int = 0
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private var crosshairActive = false
    private var crosshairX = 0f
    private var crosshairY = 0f

    /** Défilement avec inertie après un fling. */
    private val scroller = OverScroller(context)
    private var lastFlingX = 0

    /**
     * Échelle de prix : automatique (ajustée aux bougies visibles) tant que l'utilisateur
     * n'a pas glissé verticalement ; ensuite manuelle ([manualMin]..[manualMax]), ce qui
     * permet de regarder n'importe quelle zone de prix (0 compris). Double-tap pour
     * revenir en automatique.
     */
    private var autoScale = true
    private var manualMin = 0.0
    private var manualMax = 0.0

    /** Lignes de tendance de l'actif affiché. */
    private val trendLines = mutableListOf<TrendLine>()
    private var selectedLine: TrendLine? = null
    private var lineDrag = LineDrag.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val swatchRects = Array(LINE_COLORS.size) { RectF() }
    private val deleteRect = RectF()

    /** Notifie le prix de la dernière bougie visible et la variation % de la fenêtre affichée. */
    var onViewportChanged: ((lastPrice: Double, changePercent: Double) -> Unit)? = null

    /**
     * Invoqué quand la fenêtre visible approche du début de l'historique chargé : le parent
     * peut alors télécharger des bougies plus anciennes et les préfixer via
     * [setData] avec `preserveViewport = true` (la vue ne bouge pas).
     */
    var onNeedOlderCandles: (() -> Unit)? = null

    /** Invoqué après tout changement des lignes de tendance (ajout, déplacement, couleur, suppression). */
    var onTrendLinesChanged: (() -> Unit)? = null

    /** Remplace les lignes de tendance (changement d'actif) sans déclencher [onTrendLinesChanged]. */
    fun setTrendLines(newLines: List<TrendLine>) {
        trendLines.clear()
        trendLines.addAll(newLines)
        selectedLine = null
        lineDrag = LineDrag.NONE
        invalidate()
    }

    /** Copie des lignes de tendance actuelles, pour la persistance. */
    fun trendLinesSnapshot(): List<TrendLine> = trendLines.map { it.copy() }

    /** Ajoute une ligne diagonale au centre de la fenêtre visible et la sélectionne. */
    fun addTrendLine() {
        if (candles.isEmpty()) return
        val top = chartTop()
        val bottom = chartBottom()
        val line = TrendLine(
            time1 = indexToTime(scrollOffset + visibleCount * 0.3f),
            price1 = yToPrice(top + (bottom - top) * 0.65f),
            time2 = indexToTime(scrollOffset + visibleCount * 0.7f),
            price2 = yToPrice(top + (bottom - top) * 0.35f)
        )
        trendLines.add(line)
        selectedLine = line
        invalidate()
        onTrendLinesChanged?.invoke()
    }

    /**
     * Remplace les données. Avec [preserveViewport], la fenêtre visible est conservée
     * (ancrée sur la fin de l'historique) : utilisé par le rafraîchissement automatique
     * pour ne pas ramener l'utilisateur aux dernières bougies pendant qu'il consulte
     * l'historique.
     */
    fun setData(newCandles: List<Candle>, newInterval: CryptoCandleInterval, preserveViewport: Boolean = false) {
        scroller.abortAnimation()
        val hadData = candles.isNotEmpty()
        val distanceFromEnd = candles.size - scrollOffset
        val keepCount = visibleCount
        candles = newCandles
        interval = newInterval
        if (preserveViewport && hadData && candles.isNotEmpty()) {
            visibleCount = keepCount.coerceIn(minVisible, candles.size.toFloat())
            scrollOffset = candles.size - distanceFromEnd
            clampScroll()
        } else {
            visibleCount = min(defaultVisible.toFloat(), max(1, candles.size).toFloat())
            scrollOffset = (candles.size - visibleCount).coerceAtLeast(0f)
            crosshairActive = false
            autoScale = true
        }
        notifyViewport()
        invalidate()
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (candles.isEmpty()) return false
            val left = chartLeft()
            val span = chartRight() - left
            if (span <= 0f) return false

            // Pincement directionnel façon TradingView : la composante horizontale du geste
            // élargit/rétrécit les bougies (zoom temps), la composante verticale les étire ou
            // les aplatit (zoom sur l'échelle de prix).
            val minSpan = 24f * density
            val scaleX = if (detector.previousSpanX > minSpan) {
                detector.currentSpanX / detector.previousSpanX
            } else 1f
            val scaleY = if (detector.previousSpanY > minSpan) {
                detector.currentSpanY / detector.previousSpanY
            } else 1f

            if (scaleX > 0f && scaleX != 1f) {
                val focusCandle = scrollOffset + (detector.focusX - left) / (span / visibleCount)
                // Zoom arrière plafonné : au-delà, les bougies feraient moins d'un pixel
                // et le rendu deviendrait coûteux pour rien.
                val maxVisibleCount = min(candles.size, 2000).toFloat()
                visibleCount = (visibleCount / scaleX).coerceIn(minVisible, maxVisibleCount)
                scrollOffset = focusCandle - (detector.focusX - left) / (span / visibleCount)
                clampScroll()
            }

            if (scaleY > 0f && abs(scaleY - 1f) > 0.001f) {
                applyVerticalZoom(scaleY, detector.focusY)
            }

            notifyViewport()
            invalidate()
            return true
        }
    })

    /**
     * Zoom vertical autour du prix situé sous le point focal du geste : l'échelle passe en
     * manuel et la fenêtre de prix se resserre (doigts qui s'écartent) ou s'élargit.
     */
    private fun applyVerticalZoom(factor: Float, focusY: Float) {
        val top = chartTop()
        val bottom = chartBottom()
        if (bottom <= top) return
        if (autoScale) {
            autoScale = false
            manualMin = minLow
            manualMax = maxHigh
        }
        val focusRatio = ((focusY.coerceIn(top, bottom) - top) / (bottom - top)).toDouble()
        if (logScale && manualMin > 0.0) {
            val lMin = log10(manualMin)
            val lMax = log10(manualMax)
            val focusLog = lMax - focusRatio * (lMax - lMin)
            val newSpan = ((lMax - lMin) / factor).coerceIn(1e-4, 12.0)
            val newMax = focusLog + focusRatio * newSpan
            manualMax = 10.0.pow(newMax)
            manualMin = 10.0.pow(newMax - newSpan)
        } else {
            val range = manualMax - manualMin
            val focusPrice = manualMax - focusRatio * range
            val newRange = (range / factor).coerceAtLeast(1e-9)
            manualMax = focusPrice + focusRatio * newRange
            manualMin = manualMax - newRange
        }
    }

    private val flingRunnable = object : Runnable {
        override fun run() {
            if (!scroller.computeScrollOffset()) return
            val slot = (chartRight() - chartLeft()) / visibleCount
            if (slot <= 0f) return
            val dx = scroller.currX - lastFlingX
            lastFlingX = scroller.currX
            scrollOffset -= dx / slot
            val unclamped = scrollOffset
            clampScroll()
            notifyViewport()
            invalidate()
            if (scrollOffset != unclamped) {
                // Bord de l'historique atteint : on arrête l'inertie.
                scroller.abortAnimation()
                return
            }
            postOnAnimation(this)
        }
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (candles.isEmpty() || crosshairActive) return false
            val slot = (chartRight() - chartLeft()) / visibleCount
            if (slot <= 0f) return false
            scrollOffset += distanceX / slot
            clampScroll()

            // Glissement vertical : l'échelle passe en manuel et la fenêtre de prix suit le
            // doigt, ce qui permet d'aller regarder n'importe quelle zone (0 compris).
            val chartHeight = chartBottom() - chartTop()
            if (distanceY != 0f && chartHeight > 0f) {
                if (autoScale) {
                    autoScale = false
                    manualMin = minLow
                    manualMax = maxHigh
                }
                if (logScale && manualMin > 0.0) {
                    var lMin = log10(manualMin)
                    var lMax = log10(manualMax)
                    val delta = distanceY / chartHeight * (lMax - lMin)
                    lMin -= delta
                    lMax -= delta
                    manualMin = 10.0.pow(lMin)
                    manualMax = 10.0.pow(lMax)
                } else {
                    val delta = distanceY / chartHeight * (manualMax - manualMin)
                    manualMin -= delta
                    manualMax -= delta
                }
            }

            notifyViewport()
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (candles.isEmpty() || crosshairActive) return false
            scroller.abortAnimation()
            lastFlingX = 0
            scroller.fling(0, 0, velocityX.toInt(), 0, Int.MIN_VALUE, Int.MAX_VALUE, 0, 0)
            postOnAnimation(flingRunnable)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (candles.isEmpty()) return
            scroller.abortAnimation()
            crosshairActive = true
            crosshairX = e.x
            crosshairY = e.y
            invalidate()
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (candles.isEmpty()) return false
            scroller.abortAnimation()
            visibleCount = min(defaultVisible.toFloat(), max(1, candles.size).toFloat())
            scrollOffset = (candles.size - visibleCount).coerceAtLeast(0f)
            autoScale = true
            notifyViewport()
            invalidate()
            return true
        }
    })

    /**
     * Autorise le sur-défilement au-delà de l'historique, des deux côtés (naviguer dans le
     * « futur » vide ou avant les données), en gardant toujours au moins quelques bougies
     * visibles pour ne pas perdre le graphe.
     */
    private fun clampScroll() {
        if (candles.isEmpty()) {
            scrollOffset = 0f
            return
        }
        val keep = min(3f, candles.size.toFloat())
        val minOffset = keep - visibleCount
        val maxOffset = candles.size - keep
        scrollOffset = if (minOffset <= maxOffset) {
            scrollOffset.coerceIn(minOffset, maxOffset)
        } else {
            (candles.size - visibleCount).coerceAtLeast(0f)
        }
    }

    private fun firstVisibleIndex(): Int =
        floor(scrollOffset).toInt().coerceIn(0, (candles.size - 1).coerceAtLeast(0))

    private fun lastVisibleIndex(): Int {
        val exclusive = ceil(scrollOffset + visibleCount).toInt().coerceAtMost(candles.size)
        return (exclusive - 1).coerceIn(firstVisibleIndex(), (candles.size - 1).coerceAtLeast(0))
    }

    private fun notifyViewport() {
        if (candles.isEmpty()) return
        val first = candles[firstVisibleIndex()].open
        val last = candles[lastVisibleIndex()].close
        val change = if (first != 0.0) (last - first) / first * 100.0 else 0.0
        onViewportChanged?.invoke(last, change)
        // Près du début de l'historique chargé : demander des bougies plus anciennes.
        if (scrollOffset < 120f) onNeedOlderCandles?.invoke()
    }

    private val dateFormat: SimpleDateFormat
        get() {
            val pattern = when (interval) {
                CryptoCandleInterval.M1, CryptoCandleInterval.M5, CryptoCandleInterval.M15 -> "HH:mm"
                CryptoCandleInterval.H1, CryptoCandleInterval.H4 -> "dd/MM HH:mm"
                CryptoCandleInterval.D1, CryptoCandleInterval.W1 -> "dd/MM/yy"
                CryptoCandleInterval.MN1 -> "MM/yyyy"
            }
            return SimpleDateFormat(pattern, Locale.getDefault())
        }

    private fun chartLeft() = 0f
    private fun chartRight() = width - priceAxisWidth
    private fun chartTop() = verticalPadding
    private fun chartBottom() = height - timeAxisHeight - verticalPadding

    private fun priceToY(price: Double): Float {
        val top = chartTop()
        val bottom = chartBottom()
        val ratio = if (useLogMapping) {
            val safePrice = max(price, 1e-12)
            ((logMax - log10(safePrice)) / (logMax - logMin)).toFloat()
        } else {
            ((maxHigh - price) / (maxHigh - minLow)).toFloat()
        }
        return top + ratio * (bottom - top)
    }

    private fun yToPrice(y: Float): Double {
        val top = chartTop()
        val bottom = chartBottom()
        if (bottom <= top) return maxHigh
        val ratio = ((y - top) / (bottom - top)).toDouble()
        return if (useLogMapping) {
            10.0.pow(logMax - ratio * (logMax - logMin))
        } else {
            maxHigh - ratio * (maxHigh - minLow)
        }
    }

    private fun candleCenterX(globalIndex: Int): Float = xForIndex(globalIndex.toFloat())

    private fun xForIndex(index: Float): Float {
        val slot = (chartRight() - chartLeft()) / visibleCount
        return chartLeft() + (index - scrollOffset) * slot + slot / 2f
    }

    private fun indexForX(x: Float): Float {
        val slot = (chartRight() - chartLeft()) / visibleCount
        if (slot <= 0f) return 0f
        return scrollOffset + (x - chartLeft()) / slot - 0.5f
    }

    /**
     * Index (fractionnaire) de bougie correspondant à un timestamp : recherche binaire dans
     * l'historique chargé, extrapolation au pas approximatif de l'intervalle au-delà. C'est
     * ce qui permet aux lignes de tendance de rester au bon endroit sur tous les timeframes.
     */
    private fun timeToIndex(t: Long): Float {
        if (candles.isEmpty()) return 0f
        val firstTime = candles.first().openTime
        val lastTime = candles.last().openTime
        val step = interval.approxMillis.toDouble()
        if (t <= firstTime) return ((t - firstTime) / step).toFloat()
        if (t >= lastTime) return (candles.size - 1 + (t - lastTime) / step).toFloat()
        var lo = 0
        var hi = candles.size - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) / 2
            if (candles[mid].openTime <= t) lo = mid else hi = mid
        }
        val span = (candles[hi].openTime - candles[lo].openTime).toDouble().coerceAtLeast(1.0)
        return lo + ((t - candles[lo].openTime) / span).toFloat()
    }

    /** Réciproque de [timeToIndex]. */
    private fun indexToTime(index: Float): Long {
        if (candles.isEmpty()) return 0L
        val step = interval.approxMillis
        val i = floor(index).toInt()
        return when {
            i < 0 -> candles.first().openTime + (index * step).toLong()
            i >= candles.size - 1 ->
                candles.last().openTime + ((index - (candles.size - 1)) * step).toLong()
            else -> {
                val span = candles[i + 1].openTime - candles[i].openTime
                candles[i].openTime + ((index - i) * span).toLong()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (candles.isEmpty() || width == 0 || height == 0) return

        computeVisibleMinMax()
        computeAxisMetrics()
        val timeAnchors = timeLabelAnchors()
        drawGridAndPriceAxis(canvas, timeAnchors)
        drawVolume(canvas)
        drawCandles(canvas)
        if (movingAveragePeriod > 1) drawMovingAverage(canvas)
        drawTrendLines(canvas)
        drawLastPriceLine(canvas)
        drawTimeAxis(canvas, timeAnchors)
        if (crosshairActive) drawCrosshair(canvas)
        drawLegend(canvas)
        drawLineToolbar(canvas)
    }

    private fun computeVisibleMinMax() {
        val first = firstVisibleIndex()
        val last = lastVisibleIndex()
        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        var vol = 0.0
        for (i in first..last) {
            lo = min(lo, candles[i].low)
            hi = max(hi, candles[i].high)
            vol = max(vol, candles[i].volume)
        }
        maxVolume = vol

        if (!autoScale) {
            // Échelle manuelle : la fenêtre de prix est celle définie par le glissement
            // vertical, indépendamment des bougies visibles.
            if (logScale) {
                if (manualMax <= 0.0) manualMax = 1.0
                if (manualMin <= 0.0 || manualMin >= manualMax) manualMin = manualMax / 10_000
                useLogMapping = true
                logMin = log10(manualMin)
                logMax = log10(manualMax)
            } else {
                useLogMapping = false
                if (manualMax - manualMin < 1e-12) manualMax = manualMin + 1e-12
            }
            minLow = manualMin
            maxHigh = manualMax
            return
        }

        useLogMapping = logScale && lo > 0.0
        if (useLogMapping) {
            // Marges calculées dans l'espace log pour rester cohérentes visuellement.
            var lLo = log10(lo)
            var lHi = log10(hi)
            val logMargin = max((lHi - lLo) * 0.04, 1e-9)
            lLo -= logMargin
            lHi += logMargin
            logMin = lLo
            logMax = lHi
            minLow = 10.0.pow(lLo)
            maxHigh = 10.0.pow(lHi)
        } else {
            val margin = (hi - lo) * 0.04
            minLow = lo - margin
            maxHigh = hi + margin
            if (maxHigh - minLow < 1e-9) maxHigh = minLow + 1.0
        }
    }

    /**
     * Adapte le format des prix à l'échelle affichée (plus de décimales pour les petits
     * prix) et élargit l'axe de droite pour que les étiquettes tiennent toujours.
     */
    private fun computeAxisMetrics() {
        val decimals = cryptoPriceDecimals(max(abs(maxHigh), abs(minLow)))
        priceFormat.minimumFractionDigits = min(2, decimals)
        priceFormat.maximumFractionDigits = decimals
        val widest = max(
            axisTextPaint.measureText(priceFormat.format(maxHigh)),
            axisTextPaint.measureText(priceFormat.format(minLow))
        )
        priceAxisWidth = max(52f * density, widest + 12f * density)
    }

    /**
     * Positions/étiquettes des repères temporels, réparties sur toute la fenêtre visible —
     * y compris les zones sans bougie, où la date est extrapolée au pas de l'intervalle
     * (on peut donc lire les dates du « futur » en sur-défilement).
     */
    private fun timeLabelAnchors(): List<Pair<Float, String>> {
        val fmt = dateFormat
        val labelCount = 4
        val anchors = ArrayList<Pair<Float, String>>(labelCount + 1)
        for (i in 0..labelCount) {
            val index = scrollOffset + visibleCount * i / labelCount
            anchors.add(xForIndex(index) to fmt.format(Date(indexToTime(index))))
        }
        return anchors
    }

    private fun drawGridAndPriceAxis(canvas: Canvas, timeAnchors: List<Pair<Float, String>>) {
        val steps = 4
        val left = chartLeft()
        val right = chartRight()

        // Quadrillage vertical aligné sur les étiquettes de temps.
        for ((x, _) in timeAnchors) {
            if (x in left..right) {
                canvas.drawLine(x, chartTop(), x, chartBottom(), gridPaint)
            }
        }

        // Lignes horizontales à intervalle régulier en pixels ; le prix de chaque ligne est
        // recalculé via yToPrice, ce qui reste juste en échelle linéaire comme logarithmique.
        for (i in 0..steps) {
            val y = chartTop() + (chartBottom() - chartTop()) * (i.toFloat() / steps)
            canvas.drawLine(left, y, right, y, gridPaint)
            val label = priceFormat.format(yToPrice(y))
            val textY = (y + axisTextPaint.textSize / 2.5f)
                .coerceIn(chartTop() + axisTextPaint.textSize, chartBottom())
            canvas.drawText(label, right + 6f * density, textY, axisTextPaint)
        }

        // Séparateur entre le graphe et l'axe des prix.
        canvas.drawLine(right, chartTop(), right, chartBottom(), gridPaint)
    }

    private fun drawVolume(canvas: Canvas) {
        if (maxVolume <= 0.0) return
        val first = firstVisibleIndex()
        val last = lastVisibleIndex()
        val slot = (chartRight() - chartLeft()) / visibleCount
        val barWidth = max(1f, slot * 0.7f)
        val bottom = chartBottom()
        val zoneHeight = (bottom - chartTop()) * volumeZoneFraction

        for (index in first..last) {
            val candle = candles[index]
            if (candle.volume <= 0.0) continue
            val centerX = candleCenterX(index)
            val bullish = candle.close >= candle.open
            volumePaint.color = ColorUtils.setAlphaComponent(if (bullish) upColor else downColor, 80)
            val barHeight = (zoneHeight * (candle.volume / maxVolume)).toFloat().coerceAtLeast(1f)
            canvas.drawRect(centerX - barWidth / 2f, bottom - barHeight, centerX + barWidth / 2f, bottom, volumePaint)
        }
    }

    private fun drawCandles(canvas: Canvas) {
        val first = firstVisibleIndex()
        val last = lastVisibleIndex()
        val slot = (chartRight() - chartLeft()) / visibleCount
        val bodyWidth = max(1f, slot * 0.7f)

        for (index in first..last) {
            val candle = candles[index]
            val centerX = candleCenterX(index)
            val bullish = candle.close >= candle.open
            val color = if (bullish) upColor else downColor
            wickPaint.color = color
            bodyPaint.color = color

            canvas.drawLine(centerX, priceToY(candle.high), centerX, priceToY(candle.low), wickPaint)

            val openY = priceToY(candle.open)
            val closeY = priceToY(candle.close)
            val topY = min(openY, closeY)
            val bottomY = max(max(openY, closeY), topY + density)
            canvas.drawRect(centerX - bodyWidth / 2f, topY, centerX + bodyWidth / 2f, bottomY, bodyPaint)
        }
    }

    /**
     * Moyenne mobile simple sur [movingAveragePeriod] bougies (clôtures), tracée sur la
     * fenêtre visible. Les premières bougies de l'historique n'ont pas assez de recul : la
     * courbe commence à la première bougie disposant d'une fenêtre complète.
     */
    private fun drawMovingAverage(canvas: Canvas) {
        val period = movingAveragePeriod
        val first = firstVisibleIndex()
        val last = lastVisibleIndex()
        // Première bougie affichable : celle qui dispose d'une fenêtre complète derrière elle.
        val start = max(first, period - 1)
        if (start > last) return

        // Somme glissante : initialisée sur la fenêtre de départ puis mise à jour en O(1)
        // par bougie, pour rester fluide même avec des milliers de bougies visibles.
        var sum = 0.0
        for (j in start - period + 1..start) sum += candles[j].close
        maPath.reset()
        maPath.moveTo(candleCenterX(start), priceToY(sum / period))
        for (i in start + 1..last) {
            sum += candles[i].close - candles[i - period].close
            maPath.lineTo(candleCenterX(i), priceToY(sum / period))
        }
        canvas.drawPath(maPath, maPaint)
    }

    /**
     * Ligne pointillée au dernier prix connu (dernière bougie de l'historique), avec une
     * bulle colorée sur l'axe de droite — vert si la dernière bougie est haussière, rouge
     * sinon. Si le prix sort de la fenêtre visible, la bulle reste collée au bord.
     */
    private fun drawLastPriceLine(canvas: Canvas) {
        val lastCandle = candles.last()
        val price = lastCandle.close
        val color = if (lastCandle.close >= lastCandle.open) upColor else downColor
        val left = chartLeft()
        val right = chartRight()

        if (price in minLow..maxHigh) {
            lastPricePaint.color = color
            val y = priceToY(price)
            canvas.drawLine(left, y, right, y, lastPricePaint)
        }

        val padH = 6f * density
        val padV = 4f * density
        val labelHeight = bubbleTextPaint.textSize + padV * 2
        val label = priceFormat.format(price)
        val labelWidth = bubbleTextPaint.measureText(label) + padH * 2
        val rawY = priceToY(price)
        val bubbleTop = (rawY - labelHeight / 2f).coerceIn(chartTop(), chartBottom() - labelHeight)
        lastPriceLabelBgPaint.color = color
        canvas.drawRect(right, bubbleTop, right + labelWidth, bubbleTop + labelHeight, lastPriceLabelBgPaint)
        canvas.drawText(
            label,
            right + padH,
            bubbleTop + labelHeight - padV - bubbleTextPaint.descent(),
            bubbleTextPaint
        )
    }

    private fun drawTimeAxis(canvas: Canvas, timeAnchors: List<Pair<Float, String>>) {
        val left = chartLeft()
        val right = chartRight()
        val baselineY = height - 6f * density
        for ((anchorX, text) in timeAnchors) {
            val textWidth = axisTextPaint.measureText(text)
            var x = anchorX - textWidth / 2f
            x = x.coerceIn(left, right - textWidth)
            canvas.drawText(text, x, baselineY, axisTextPaint)
        }
    }

    private fun crosshairIndex(): Int {
        val slot = (chartRight() - chartLeft()) / visibleCount
        if (slot <= 0f) return lastVisibleIndex()
        return floor(scrollOffset + (crosshairX - chartLeft()) / slot).toInt()
            .coerceIn(firstVisibleIndex(), lastVisibleIndex())
    }

    private fun drawCrosshair(canvas: Canvas) {
        val left = chartLeft()
        val right = chartRight()
        val index = crosshairIndex()
        val candle = candles[index]
        val centerX = candleCenterX(index)
        // Ligne horizontale libre : elle suit le doigt et la bulle affiche le prix pointé.
        val y = crosshairY.coerceIn(chartTop(), chartBottom())
        val pointedPrice = yToPrice(y)

        canvas.drawLine(centerX, chartTop(), centerX, chartBottom(), crosshairPaint)
        canvas.drawLine(left, y, right, y, crosshairPaint)

        val padH = 6f * density
        val padV = 4f * density
        val labelHeight = bubbleTextPaint.textSize + padV * 2

        // Bulle de prix sur l'axe de droite.
        val priceLabel = priceFormat.format(pointedPrice)
        val priceWidth = bubbleTextPaint.measureText(priceLabel) + padH * 2
        val bubbleTop = (y - labelHeight / 2f).coerceIn(chartTop(), chartBottom() - labelHeight)
        canvas.drawRect(right, bubbleTop, right + priceWidth, bubbleTop + labelHeight, crosshairLabelBgPaint)
        canvas.drawText(
            priceLabel,
            right + padH,
            bubbleTop + labelHeight - padV - bubbleTextPaint.descent(),
            bubbleTextPaint
        )

        // Bulle date en bas, sur l'axe du temps.
        val dateLabel = dateFormat.format(Date(candle.openTime))
        val dateWidth = bubbleTextPaint.measureText(dateLabel) + padH * 2
        val dateLeft = (centerX - dateWidth / 2f).coerceIn(left, right - dateWidth)
        val dateTop = chartBottom()
        canvas.drawRect(dateLeft, dateTop, dateLeft + dateWidth, dateTop + labelHeight, crosshairLabelBgPaint)
        canvas.drawText(
            dateLabel,
            dateLeft + padH,
            dateTop + labelHeight - padV - bubbleTextPaint.descent(),
            bubbleTextPaint
        )
    }

    /**
     * Légende OHLC façon TradingView, en haut à gauche : valeurs de la bougie visée par le
     * crosshair, ou de la dernière bougie visible sinon, colorées selon la direction.
     * Les segments passent à la ligne quand la largeur du graphe ne suffit pas.
     */
    private fun drawLegend(canvas: Canvas) {
        val index = if (crosshairActive) crosshairIndex() else lastVisibleIndex()
        val candle = candles[index]
        val bullish = candle.close >= candle.open
        val valueColor = if (bullish) upColor else downColor
        val changePercent = if (candle.open != 0.0) (candle.close - candle.open) / candle.open * 100.0 else 0.0
        val changeSign = if (changePercent >= 0) "+" else "−"
        val changeText = "$changeSign${percentFormat.format(abs(changePercent))} %"

        // Segments (préfixe gris, valeur colorée) ; la variation % est un segment sans préfixe.
        val segments = listOf(
            "O" to priceFormat.format(candle.open),
            "H" to priceFormat.format(candle.high),
            "L" to priceFormat.format(candle.low),
            "C" to priceFormat.format(candle.close),
            "" to changeText
        )

        val gap = 8f * density
        val innerGap = 4f * density
        val padding = 5f * density
        val startX = chartLeft() + 6f * density
        val startY = chartTop() + 4f * density
        val maxWidth = (chartRight() - padding) - startX
        val lineHeight = legendTextPaint.textSize + 4f * density

        fun segmentWidth(segment: Pair<String, String>): Float {
            val labelWidth = if (segment.first.isEmpty()) 0f
                else legendTextPaint.measureText(segment.first) + innerGap
            return labelWidth + legendTextPaint.measureText(segment.second)
        }

        // Répartition gloutonne des segments sur autant de lignes que nécessaire.
        val rows = ArrayList<MutableList<Pair<String, String>>>()
        var currentRow = mutableListOf<Pair<String, String>>()
        var currentWidth = 0f
        var widestRow = 0f
        for (segment in segments) {
            val width = segmentWidth(segment)
            val needed = if (currentRow.isEmpty()) width else currentWidth + gap + width
            if (currentRow.isNotEmpty() && needed > maxWidth) {
                rows.add(currentRow)
                widestRow = max(widestRow, currentWidth)
                currentRow = mutableListOf(segment)
                currentWidth = width
            } else {
                currentRow.add(segment)
                currentWidth = needed
            }
        }
        rows.add(currentRow)
        widestRow = max(widestRow, currentWidth)

        val boxHeight = rows.size * lineHeight + padding * 2 - 4f * density
        canvas.drawRoundRect(
            startX - padding, startY, startX + widestRow + padding, startY + boxHeight,
            4f * density, 4f * density, legendBgPaint
        )

        var baseline = startY + padding + legendTextPaint.textSize - legendTextPaint.descent()
        for (row in rows) {
            var x = startX
            for ((label, value) in row) {
                if (label.isNotEmpty()) {
                    legendTextPaint.color = axisTextColor
                    canvas.drawText(label, x, baseline, legendTextPaint)
                    x += legendTextPaint.measureText(label) + innerGap
                }
                legendTextPaint.color = valueColor
                canvas.drawText(value, x, baseline, legendTextPaint)
                x += legendTextPaint.measureText(value) + gap
            }
            baseline += lineHeight
        }
    }

    // ------------------------------------------------------------------------------
    // Lignes de tendance : dessin et interaction
    // ------------------------------------------------------------------------------

    private fun drawTrendLines(canvas: Canvas) {
        if (trendLines.isEmpty()) return
        canvas.save()
        canvas.clipRect(chartLeft(), chartTop(), chartRight(), chartBottom())
        for (line in trendLines) {
            val x1 = xForIndex(timeToIndex(line.time1))
            val y1 = priceToY(line.price1)
            val x2 = xForIndex(timeToIndex(line.time2))
            val y2 = priceToY(line.price2)
            val color = LINE_COLORS[line.colorIndex.mod(LINE_COLORS.size)]
            trendLinePaint.color = color
            trendLinePaint.strokeWidth = (if (line === selectedLine) 3f else 2f) * density
            canvas.drawLine(x1, y1, x2, y2, trendLinePaint)
            if (line === selectedLine) {
                handleFillPaint.color = color
                canvas.drawCircle(x1, y1, 6f * density, handleFillPaint)
                canvas.drawCircle(x1, y1, 6f * density, handleRingPaint)
                canvas.drawCircle(x2, y2, 6f * density, handleFillPaint)
                canvas.drawCircle(x2, y2, 6f * density, handleRingPaint)
            }
        }
        canvas.restore()
    }

    /** Palette de couleurs + bouton supprimer, en haut à droite, quand une ligne est sélectionnée. */
    private fun drawLineToolbar(canvas: Canvas) {
        val selected = selectedLine ?: return
        val cy = chartTop() + 16f * density
        var cx = chartRight() - 18f * density

        canvas.drawCircle(cx, cy, 12f * density, toolbarBgPaint)
        val cross = "✕"
        canvas.drawText(
            cross,
            cx - toolbarTextPaint.measureText(cross) / 2f,
            cy + toolbarTextPaint.textSize * 0.35f,
            toolbarTextPaint
        )
        val touchRadius = 14f * density
        deleteRect.set(cx - touchRadius, cy - touchRadius, cx + touchRadius, cy + touchRadius)

        cx -= 34f * density
        for (i in LINE_COLORS.indices.reversed()) {
            swatchPaint.color = LINE_COLORS[i]
            canvas.drawCircle(cx, cy, 8f * density, swatchPaint)
            if (selected.colorIndex.mod(LINE_COLORS.size) == i) {
                canvas.drawCircle(cx, cy, 10.5f * density, handleRingPaint)
            }
            swatchRects[i].set(cx - touchRadius, cy - touchRadius, cx + touchRadius, cy + touchRadius)
            cx -= 26f * density
        }
    }

    /** Distance d'un point au segment [x1,y1]-[x2,y2], en pixels. */
    private fun distanceToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq <= 0f) 0f else (((px - x1) * dx + (py - y1) * dy) / lengthSq).coerceIn(0f, 1f)
        val cx = x1 + t * dx
        val cy = y1 + t * dy
        return kotlin.math.hypot(px - cx, py - cy)
    }

    private fun findLineAt(x: Float, y: Float): TrendLine? {
        val threshold = 14f * density
        var best: TrendLine? = null
        var bestDistance = threshold
        for (line in trendLines) {
            val distance = distanceToSegment(
                x, y,
                xForIndex(timeToIndex(line.time1)), priceToY(line.price1),
                xForIndex(timeToIndex(line.time2)), priceToY(line.price2)
            )
            if (distance < bestDistance) {
                bestDistance = distance
                best = line
            }
        }
        return best
    }

    /**
     * Interactions avec les lignes de tendance, prioritaires sur les gestes du graphe :
     * toucher une extrémité la déplace seule, toucher le corps déplace toute la ligne,
     * la barre d'outils change la couleur ou supprime, toucher ailleurs désélectionne.
     */
    private fun handleDrawingTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                val selected = selectedLine
                if (selected != null) {
                    if (deleteRect.contains(x, y)) {
                        trendLines.remove(selected)
                        selectedLine = null
                        invalidate()
                        onTrendLinesChanged?.invoke()
                        return true
                    }
                    for (i in swatchRects.indices) {
                        if (swatchRects[i].contains(x, y)) {
                            selected.colorIndex = i
                            invalidate()
                            onTrendLinesChanged?.invoke()
                            return true
                        }
                    }
                    val handleThreshold = 20f * density
                    val d1 = kotlin.math.hypot(
                        x - xForIndex(timeToIndex(selected.time1)), y - priceToY(selected.price1)
                    )
                    val d2 = kotlin.math.hypot(
                        x - xForIndex(timeToIndex(selected.time2)), y - priceToY(selected.price2)
                    )
                    if (d1 < handleThreshold || d2 < handleThreshold) {
                        scroller.abortAnimation()
                        lineDrag = if (d1 <= d2) LineDrag.POINT1 else LineDrag.POINT2
                        lastTouchX = x
                        lastTouchY = y
                        return true
                    }
                }
                val hit = findLineAt(x, y)
                if (hit != null) {
                    scroller.abortAnimation()
                    selectedLine = hit
                    lineDrag = LineDrag.BODY
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                    return true
                }
                if (selected != null) {
                    // Tap dans le vide : on désélectionne mais on laisse le geste continuer
                    // (défilement possible dans la foulée).
                    selectedLine = null
                    invalidate()
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (lineDrag == LineDrag.NONE) return false
                val line = selectedLine ?: run { lineDrag = LineDrag.NONE; return false }
                val x = event.x
                val y = event.y
                when (lineDrag) {
                    LineDrag.POINT1 -> {
                        line.time1 = indexToTime(indexForX(x))
                        line.price1 = yToPrice(y.coerceIn(chartTop(), chartBottom()))
                    }
                    LineDrag.POINT2 -> {
                        line.time2 = indexToTime(indexForX(x))
                        line.price2 = yToPrice(y.coerceIn(chartTop(), chartBottom()))
                    }
                    LineDrag.BODY -> {
                        val slot = (chartRight() - chartLeft()) / visibleCount
                        if (slot > 0f) {
                            val deltaIndex = (x - lastTouchX) / slot
                            val deltaY = y - lastTouchY
                            line.time1 = indexToTime(timeToIndex(line.time1) + deltaIndex)
                            line.time2 = indexToTime(timeToIndex(line.time2) + deltaIndex)
                            line.price1 = yToPrice(priceToY(line.price1) + deltaY)
                            line.price2 = yToPrice(priceToY(line.price2) + deltaY)
                        }
                    }
                    LineDrag.NONE -> Unit
                }
                lastTouchX = x
                lastTouchY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (lineDrag == LineDrag.NONE) return false
                lineDrag = LineDrag.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                onTrendLinesChanged?.invoke()
                return true
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (candles.isEmpty()) return false
        parent?.requestDisallowInterceptTouchEvent(true)

        if (handleDrawingTouch(event)) return true

        scaleDetector.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            scroller.abortAnimation()
        }

        // Une fois le viseur activé (appui long), il suit le doigt directement : le GestureDetector
        // n'émet plus d'onScroll après un long press, on gère donc le déplacement à la main.
        if (crosshairActive) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    crosshairX = event.x
                    crosshairY = event.y
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    crosshairActive = false
                    invalidate()
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            return true
        }

        if (!scaleDetector.isInProgress) {
            gestureDetector.onTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}
