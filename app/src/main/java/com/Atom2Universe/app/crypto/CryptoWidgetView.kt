package com.Atom2Universe.app.crypto

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.Atom2Universe.app.R
import com.google.android.material.card.MaterialCardView
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CryptoWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val btcLine: TextView
    private val ethLine: TextView
    private val eurUsdLine: TextView
    private val statusView: TextView
    private val rootContent: LinearLayout
    private val cardView: MaterialCardView

    private val baseValueColor = ContextCompat.getColor(context, R.color.crypto_widget_value)
    private val upColor = ContextCompat.getColor(context, R.color.crypto_widget_up)
    private val downColor = ContextCompat.getColor(context, R.color.crypto_widget_down)
    private val baseBackgroundColor = ContextCompat.getColor(context, R.color.crypto_widget_background)

    private val btcPrefix: String
    private val ethPrefix: String
    private val eurUsdPrefix: String
    private var currencySuffix: String
    private var minimalMode = false
    private var eurMode = false

    private val minScale = 0.8f
    private val maxScale = 2.2f
    private var currentScale = 1f

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastDragX = 0f
    private var lastDragY = 0f
    private var isDragging = false
    private var isTwoFingerGesture = false
    private var skipNextDragFrame = false
    private val tapThresholdPx = 12f * context.resources.displayMetrics.density

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val nextScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
            if (nextScale != currentScale) {
                currentScale = nextScale
                scaleX = currentScale
                scaleY = currentScale
            }
            return true
        }
    })

    private val numberFormat: NumberFormat
    private val eurUsdFormat: NumberFormat
    private val decimalSeparator: Char

    init {
        LayoutInflater.from(context).inflate(R.layout.view_crypto_widget, this, true)
        isClickable = true
        isFocusable = true

        cardView = findViewById(R.id.crypto_widget_card)
        btcLine = findViewById(R.id.crypto_widget_btc_line)
        ethLine = findViewById(R.id.crypto_widget_eth_line)
        eurUsdLine = findViewById(R.id.crypto_widget_eurusd_line)
        statusView = findViewById(R.id.crypto_widget_status)
        rootContent = findViewById(R.id.crypto_widget_root)

        val locale = currentLocale()
        numberFormat = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = true
        }
        eurUsdFormat = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 4
            maximumFractionDigits = 4
            isGroupingUsed = false
        }
        decimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        btcPrefix = buildPrefix(context.getString(R.string.crypto_widget_btc_label), 8)
        ethPrefix = buildPrefix(context.getString(R.string.crypto_widget_eth_label), 9)
        eurUsdPrefix = buildPrefix(context.getString(R.string.crypto_widget_eurusd_label), 8)
        currencySuffix = " ${context.getString(R.string.crypto_widget_currency_usd)}"

        setOnTouchListener { _, event ->
            handleTouch(event)
        }
    }

    fun setCurrencyEur(isEur: Boolean) {
        eurMode = isEur
        currencySuffix = if (isEur) " ${context.getString(R.string.crypto_widget_currency_eur)}"
                         else " ${context.getString(R.string.crypto_widget_currency_usd)}"
    }

    fun setMinimalMode(minimal: Boolean) {
        minimalMode = minimal
        val density = context.resources.displayMetrics.density
        val padding = if (minimal) 0 else (14 * density).toInt()
        val paddingV = if (minimal) 0 else (12 * density).toInt()
        rootContent.setPadding(padding, paddingV, padding, paddingV)
        val marginTop = if (minimal) 0 else (6 * density).toInt()
        (ethLine.layoutParams as? LinearLayout.LayoutParams)?.topMargin = marginTop
        (eurUsdLine.layoutParams as? LinearLayout.LayoutParams)?.topMargin = marginTop
        ethLine.requestLayout()
        eurUsdLine.requestLayout()
        cardView.radius = if (minimal) 0f else (18 * density)

        val innerContainer = btcLine.parent as? LinearLayout
        val containerGravity = if (minimal) android.view.Gravity.END else android.view.Gravity.START
        rootContent.gravity = containerGravity
        innerContainer?.gravity = containerGravity
    }

    fun setEurUsdEnabled(enabled: Boolean) {
        eurUsdLine.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    fun applyBackgroundOpacity(opacityPercent: Int) {
        val clampedPercent = opacityPercent.coerceIn(0, 100)
        val alpha = ((clampedPercent / 100f) * 255f).roundToInt().coerceIn(0, 255)
        cardView.setCardBackgroundColor(ColorUtils.setAlphaComponent(baseBackgroundColor, alpha))
    }

    fun showLoading() {
        statusView.text = context.getString(R.string.crypto_widget_loading)
        statusView.isVisible = true
    }

    fun showError() {
        statusView.text = context.getString(R.string.crypto_widget_error)
        statusView.isVisible = true
    }

    fun clearStatus() {
        statusView.text = ""
        statusView.isVisible = false
    }

    fun updatePrices(
        btcPrice: Double?,
        previousBtcPrice: Double?,
        ethPrice: Double?,
        previousEthPrice: Double?
    ) {
        val btcPfx = if (minimalMode) "" else btcPrefix
        val ethPfx = if (minimalMode) "" else ethPrefix
        updateLine(btcLine, btcPfx, btcPrice, previousBtcPrice, omitSuffix = minimalMode)
        updateLine(ethLine, ethPfx, ethPrice, previousEthPrice, omitSuffix = minimalMode)
    }

    fun updateEurUsdRate(rate: Double?, previousRate: Double?) {
        val prefix = if (minimalMode) "" else eurUsdPrefix
        updateLine(eurUsdLine, prefix, rate, previousRate, omitSuffix = minimalMode, fmt = eurUsdFormat)
    }

    private fun updateLine(
        lineView: TextView,
        prefix: String,
        value: Double?,
        previousValue: Double?,
        omitSuffix: Boolean = false,
        fmt: NumberFormat = numberFormat
    ) {
        val suffix = if (omitSuffix) "" else currencySuffix
        val numeric = value?.takeIf { it.isFinite() }
        if (numeric == null) {
            lineView.text = prefix + context.getString(R.string.crypto_widget_placeholder) + suffix
            lineView.setTextColor(baseValueColor)
            return
        }

        val formatted = fmt.format(numeric)
        val separatorIndex = formatted.indexOf(decimalSeparator)
        val numberStart = prefix.length
        val numberEnd = numberStart + formatted.length
        val minorStart = if (separatorIndex >= 0) numberStart + separatorIndex else numberEnd

        val fullText = prefix + formatted + suffix
        val spannable = SpannableString(fullText).apply {
            setSpan(ForegroundColorSpan(baseValueColor), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (minorStart < numberEnd) {
                setSpan(
                    ForegroundColorSpan(resolveTrendColor(numeric, previousValue)),
                    minorStart,
                    numberEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        lineView.text = spannable
    }

    private fun resolveTrendColor(current: Double, previous: Double?): Int {
        val prev = previous?.takeIf { it.isFinite() } ?: return baseValueColor
        return when {
            current > prev -> upColor
            current < prev -> downColor
            else -> baseValueColor
        }
    }

    private fun buildPrefix(label: String, spacesAfterColon: Int): String {
        val safeSpaces = spacesAfterColon.coerceAtLeast(0)
        val spacing = "\u00A0".repeat(safeSpaces)
        return "$label:$spacing"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)

        if (widthMode == MeasureSpec.EXACTLY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val unspecifiedWidth = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val unspecifiedHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        btcLine.measure(unspecifiedWidth, unspecifiedHeight)
        ethLine.measure(unspecifiedWidth, unspecifiedHeight)
        if (eurUsdLine.isVisible) eurUsdLine.measure(unspecifiedWidth, unspecifiedHeight)
        statusView.measure(unspecifiedWidth, unspecifiedHeight)

        var contentWidth = max(btcLine.measuredWidth, ethLine.measuredWidth)
        if (eurUsdLine.isVisible) contentWidth = max(contentWidth, eurUsdLine.measuredWidth)
        val widestChild = max(contentWidth, statusView.measuredWidth)
        val desiredWidth = widestChild + rootContent.paddingStart + rootContent.paddingEnd

        val resolvedWidth = when (widthMode) {
            MeasureSpec.AT_MOST -> min(desiredWidth, widthSize)
            else -> desiredWidth
        }.coerceAtLeast(0)

        val resolvedWidthSpec = MeasureSpec.makeMeasureSpec(resolvedWidth, MeasureSpec.EXACTLY)
        super.onMeasure(resolvedWidthSpec, heightMeasureSpec)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                bringToFront()
                downRawX = event.rawX
                downRawY = event.rawY
                lastDragX = event.rawX
                lastDragY = event.rawY
                isDragging = false
                isTwoFingerGesture = false
                skipNextDragFrame = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                isTwoFingerGesture = true
                isDragging = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                skipNextDragFrame = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    if (skipNextDragFrame) {
                        lastDragX = event.rawX
                        lastDragY = event.rawY
                        downRawX = event.rawX
                        downRawY = event.rawY
                        isDragging = false
                        skipNextDragFrame = false
                    } else {
                        val moved = hypot(event.rawX - downRawX, event.rawY - downRawY)
                        if (isDragging || moved > tapThresholdPx) {
                            isDragging = true
                            translationX += event.rawX - lastDragX
                            translationY += event.rawY - lastDragY
                        }
                        lastDragX = event.rawX
                        lastDragY = event.rawY
                    }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isTwoFingerGesture = false
                skipNextDragFrame = false
            }
        }
        return true
    }

    private fun currentLocale(): Locale {
        val locales = resources.configuration.locales
        return if (!locales.isEmpty) locales[0] else Locale.getDefault()
    }
}
