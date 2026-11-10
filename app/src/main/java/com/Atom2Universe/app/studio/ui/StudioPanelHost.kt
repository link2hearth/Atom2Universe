package com.Atom2Universe.app.studio.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.Atom2Universe.app.R
import kotlin.math.roundToInt

/** Les zones vides laissent le clavier, les pads et le transport recevoir leurs propres doigts. */
internal class StudioPanelHost(context: Context) : FrameLayout(context) {
    // Le bureau et chaque bulle sont frères : Android attribue chaque doigt à la vue qu’il touche.
    init { isMotionEventSplittingEnabled = true }
    private val panelCount get() = childCount - 1
    var onCountChanged: (Int) -> Unit = {}
    private val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val ink = if (dark) 0xFFF1F1F6.toInt() else 0xFF262630.toInt()
    private val margin = dp(6)

    fun show(key: Int, title: String, content: View, widthDp: Int, anchor: View,
             at: Pair<Float, Float>? = null, onHistory: (() -> Unit)? = null, valid: () -> Boolean = { true }) {
        val old = findViewWithTag<Card>(key)
        val where = IntArray(2)
        val origin = IntArray(2)
        getLocationOnScreen(origin)
        anchor.getLocationOnScreen(where)
        val card = Card(widthDp, valid, onHistory).apply {
            tag = key
            orientation = LinearLayout.VERTICAL
            elevation = dp(8).toFloat()
            isClickable = true
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (dark) 0xFF2B2B33.toInt() else 0xFFFFFFFF.toInt())
                setStroke(dp(1), if (dark) 0xFF52525E.toInt() else 0xFFD2D2DB.toInt())
            }
            clipToOutline = true
        }
        val header = FrameLayout(context)
        header.addView(View(context).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(ink) }
            alpha = 0.28f
        }, LayoutParams(dp(32), dp(3), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(5) })
        header.addView(TextView(context).apply {
            text = title
            textSize = if (resources.configuration.smallestScreenWidthDp >= 600) 16f else 14f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).apply { leftMargin = dp(14); rightMargin = dp(48) })
        header.addView(ImageButton(context).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(ink)
            setPadding(dp(13), dp(13), dp(13), dp(13))
            contentDescription = context.getString(R.string.studio_close_panel, title)
            val ripple = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
            setBackgroundResource(ripple.resourceId)
            setOnClickListener { close(card) }
        }, LayoutParams(dp(44), dp(44), Gravity.RIGHT))
        var dx = 0f
        var dy = 0f
        header.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX - card.x; dy = e.rawY - card.y }
                MotionEvent.ACTION_MOVE -> position(card, e.rawX - dx, e.rawY - dy)
            }
            true
        }
        card.addView(header)
        card.addView(ScrollView(context).apply { addView(content) })
        card.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
        val x = at?.let { it.first - card.measuredWidth / 2 } ?: where[0].toFloat()
        val below = (at?.second ?: (where[1] + anchor.height).toFloat()) - origin[1] + margin
        var targetX = old?.x ?: (x - origin[0])
        var targetY = old?.y ?: if (below + card.measuredHeight <= height) below else
            ((at?.second ?: where[1].toFloat()) - origin[1] - margin - card.measuredHeight).coerceAtLeast(margin.toFloat())
        if (old == null && panelCount > 0) {
            val front = getChildAt(childCount - 1)
            targetX = when {
                front.x + front.width + card.measuredWidth + 2 * margin <= width -> front.x + front.width + margin
                front.x >= card.measuredWidth + 2 * margin -> front.x - card.measuredWidth - margin
                else -> targetX
            }
            if (kotlin.math.abs(targetX - front.x) < card.measuredWidth / 2) targetY = front.y + dp(44)
        }
        if (old != null) removeView(old)
        addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT))
        position(card, targetX, targetY)
        onCountChanged(panelCount)
    }

    private inner class Card(val widthDp: Int, val valid: () -> Boolean, val onHistory: (() -> Unit)?) : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = minOf(dp(widthDp), (this@StudioPanelHost.width - 2 * margin).coerceAtLeast(1))
            val h = (this@StudioPanelHost.height * 0.66f).roundToInt().coerceAtLeast(1)
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST))
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) bringToFront()
            return super.dispatchTouchEvent(ev)
        }
    }

    private fun position(card: View, x: Float, y: Float) {
        card.x = x.coerceIn(margin.toFloat(), maxOf(margin, width - margin - card.measuredWidth).toFloat())
        card.y = y.coerceIn(margin.toFloat(), maxOf(margin, height - margin - card.measuredHeight).toFloat())
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        for (i in 1 until childCount) getChildAt(i).let { position(it, it.x, it.y) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w < oldw && panelCount > 1) keepRowVisible(w)
        // Les enfants ont été mesurés avant que le parent reçoive ses nouvelles dimensions.
        if (panelCount > 0) post { requestLayout() }
    }

    /** À la rotation, deux panneaux rangés côte à côte gardent leurs commandes accessibles si la ligne tient encore. */
    private fun keepRowVisible(available: Int) {
        val cards = (1 until childCount).map { getChildAt(it) }.sortedBy { it.x }
        if (cards.zipWithNext().any { (left, right) -> right.x < left.x + left.width * 0.75f }) return
        var spare = (available - cards.sumOf { it.width } - (cards.size + 1) * margin).toFloat()
        if (spare < 0f) return
        var next = margin.toFloat()
        for (card in cards) {
            val gap = (card.x - next).coerceIn(0f, spare)
            card.x = next + gap
            spare -= gap
            next = card.x + card.width + margin
        }
    }

    fun prune() {
        for (i in childCount - 1 downTo 1) {
            val card = getChildAt(i) as Card
            if (!card.valid()) close(card)
        }
    }

    fun closeTop() { if (panelCount > 0) close(getChildAt(childCount - 1)) }

    fun refreshAfterHistory() {
        val open = (1 until childCount).map { getChildAt(it) as Card }
        for (card in open) if (card.valid()) card.onHistory?.invoke() else close(card)
    }

    private fun close(card: View) {
        removeView(card)
        onCountChanged(panelCount)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
