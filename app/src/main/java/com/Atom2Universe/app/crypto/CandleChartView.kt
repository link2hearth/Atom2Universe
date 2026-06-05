package com.Atom2Universe.app.crypto

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Une bougie OHLC issue d'une kline Binance. */
data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

/**
 * Graphique en chandeliers interactif (vert haussier / rouge baissier), façon TradingView.
 *
 * Le jeu complet de bougies (jusqu'à ~1000) est chargé en mémoire ; la vue n'en affiche
 * qu'une fenêtre. Gestes : glisser à un doigt = défiler dans l'historique, pincer = zoomer
 * (nombre de bougies visibles), double-tap = revenir aux dernières bougies, appui long puis
 * glisser = viseur (crosshair) affichant prix + date. L'échelle de prix s'ajuste à la
 * fenêtre visible.
 */
class CandleChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val upColor = ContextCompat.getColor(context, R.color.crypto_widget_up)
    private val downColor = ContextCompat.getColor(context, R.color.crypto_widget_down)
    private val gridColor = ContextCompat.getColor(context, R.color.crypto_chart_grid)
    private val axisTextColor = ContextCompat.getColor(context, R.color.crypto_chart_axis_text)
    private val crosshairColor = ContextCompat.getColor(context, R.color.crypto_chart_crosshair)

    private val density = context.resources.displayMetrics.density

    private val wickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, density)
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    private val crosshairLabelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.crypto_widget_background)
        textSize = 11f * density
    }

    private val priceAxisWidth = 64f * density
    private val timeAxisHeight = 20f * density
    private val verticalPadding = 12f * density

    private val priceFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = true
    }

    private var candles: List<Candle> = emptyList()
    private var interval: CryptoCandleInterval = CryptoCandleInterval.M15

    private val defaultVisible = 100
    private val minVisible = 8f
    private var visibleCount = defaultVisible.toFloat()
    private var scrollOffset = 0f

    private var minLow = 0.0
    private var maxHigh = 0.0

    private var crosshairActive = false
    private var crosshairX = 0f

    /** Notifie le prix de la dernière bougie visible et la variation % de la fenêtre affichée. */
    var onViewportChanged: ((lastPrice: Double, changePercent: Double) -> Unit)? = null

    fun setData(newCandles: List<Candle>, newInterval: CryptoCandleInterval) {
        candles = newCandles
        interval = newInterval
        visibleCount = min(defaultVisible.toFloat(), max(1, candles.size).toFloat())
        scrollOffset = (candles.size - visibleCount).coerceAtLeast(0f)
        crosshairActive = false
        notifyViewport()
        invalidate()
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (candles.isEmpty()) return false
            val left = chartLeft()
            val span = chartRight() - left
            if (span <= 0f) return false
            val focusCandle = scrollOffset + (detector.focusX - left) / (span / visibleCount)
            visibleCount = (visibleCount / detector.scaleFactor).coerceIn(minVisible, candles.size.toFloat())
            scrollOffset = focusCandle - (detector.focusX - left) / (span / visibleCount)
            clampScroll()
            notifyViewport()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (candles.isEmpty() || crosshairActive) return false
            val slot = (chartRight() - chartLeft()) / visibleCount
            if (slot <= 0f) return false
            scrollOffset += distanceX / slot
            clampScroll()
            notifyViewport()
            invalidate()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (candles.isEmpty()) return
            crosshairActive = true
            crosshairX = e.x
            invalidate()
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (candles.isEmpty()) return false
            visibleCount = min(defaultVisible.toFloat(), max(1, candles.size).toFloat())
            scrollOffset = (candles.size - visibleCount).coerceAtLeast(0f)
            notifyViewport()
            invalidate()
            return true
        }
    })

    private fun clampScroll() {
        val maxOffset = (candles.size - visibleCount).coerceAtLeast(0f)
        scrollOffset = scrollOffset.coerceIn(0f, maxOffset)
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
        val ratio = ((maxHigh - price) / (maxHigh - minLow)).toFloat()
        return top + ratio * (bottom - top)
    }

    private fun candleCenterX(globalIndex: Int): Float {
        val slot = (chartRight() - chartLeft()) / visibleCount
        return chartLeft() + (globalIndex - scrollOffset) * slot + slot / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (candles.isEmpty()) return

        computeVisibleMinMax()
        drawGridAndPriceAxis(canvas)
        drawCandles(canvas)
        drawTimeAxis(canvas)
        if (crosshairActive) drawCrosshair(canvas)
    }

    private fun computeVisibleMinMax() {
        val first = firstVisibleIndex()
        val last = lastVisibleIndex()
        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        for (i in first..last) {
            lo = min(lo, candles[i].low)
            hi = max(hi, candles[i].high)
        }
        val margin = (hi - lo) * 0.04
        minLow = lo - margin
        maxHigh = hi + margin
        if (maxHigh - minLow < 1e-9) maxHigh = minLow + 1.0
    }

    private fun drawGridAndPriceAxis(canvas: Canvas) {
        val steps = 4
        val left = chartLeft()
        val right = chartRight()
        for (i in 0..steps) {
            val price = maxHigh - (maxHigh - minLow) * (i.toDouble() / steps)
            val y = priceToY(price)
            canvas.drawLine(left, y, right, y, gridPaint)
            val label = priceFormat.format(price)
            val textY = (y + axisTextPaint.textSize / 2.5f)
                .coerceIn(chartTop() + axisTextPaint.textSize, chartBottom())
            canvas.drawText(label, right + 6f * density, textY, axisTextPaint)
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

    private fun drawTimeAxis(canvas: Canvas) {
        val fmt = dateFormat
        val first = firstVisibleIndex()
        val last = lastVisibleIndex()
        val labelCount = 4
        val left = chartLeft()
        val right = chartRight()
        val baselineY = height - 6f * density
        for (i in 0..labelCount) {
            val fraction = i.toFloat() / labelCount
            val candleIndex = (first + ((last - first) * fraction).roundToInt()).coerceIn(first, last)
            val text = fmt.format(Date(candles[candleIndex].openTime))
            val textWidth = axisTextPaint.measureText(text)
            var x = left + fraction * (right - left) - textWidth / 2f
            x = x.coerceIn(left, right - textWidth)
            canvas.drawText(text, x, baselineY, axisTextPaint)
        }
    }

    private fun drawCrosshair(canvas: Canvas) {
        val left = chartLeft()
        val right = chartRight()
        val slot = (right - left) / visibleCount
        if (slot <= 0f) return
        val index = (scrollOffset + (crosshairX - left) / slot).roundToInt()
            .coerceIn(firstVisibleIndex(), lastVisibleIndex())
        val candle = candles[index]
        val centerX = candleCenterX(index)
        // Pointe l'extrémité de la mèche : le plus haut (high) pour une bougie haussière,
        // le plus bas (low) pour une baissière.
        val bullish = candle.close >= candle.open
        val pointedPrice = if (bullish) candle.high else candle.low
        val y = priceToY(pointedPrice)

        canvas.drawLine(centerX, chartTop(), centerX, chartBottom(), crosshairPaint)
        canvas.drawLine(left, y, right, y, crosshairPaint)

        val padH = 6f * density
        val padV = 4f * density
        val labelHeight = crosshairLabelTextPaint.textSize + padV * 2

        // Bulle de prix sur l'axe de droite.
        val priceLabel = priceFormat.format(pointedPrice)
        val priceWidth = crosshairLabelTextPaint.measureText(priceLabel) + padH * 2
        val bubbleTop = (y - labelHeight / 2f).coerceIn(chartTop(), chartBottom() - labelHeight)
        canvas.drawRect(right, bubbleTop, right + priceWidth, bubbleTop + labelHeight, crosshairLabelBgPaint)
        canvas.drawText(
            priceLabel,
            right + padH,
            bubbleTop + labelHeight - padV - crosshairLabelTextPaint.descent(),
            crosshairLabelTextPaint
        )

        // Bulle date en haut.
        val dateLabel = dateFormat.format(Date(candle.openTime))
        val dateWidth = crosshairLabelTextPaint.measureText(dateLabel) + padH * 2
        val dateLeft = (centerX - dateWidth / 2f).coerceIn(left, right - dateWidth)
        val dateTop = chartTop()
        canvas.drawRect(dateLeft, dateTop, dateLeft + dateWidth, dateTop + labelHeight, crosshairLabelBgPaint)
        canvas.drawText(
            dateLabel,
            dateLeft + padH,
            dateTop + labelHeight - padV - crosshairLabelTextPaint.descent(),
            crosshairLabelTextPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (candles.isEmpty()) return false
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)

        // Une fois le viseur activé (appui long), il suit le doigt directement : le GestureDetector
        // n'émet plus d'onScroll après un long press, on gère donc le déplacement à la main.
        if (crosshairActive) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    crosshairX = event.x
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
