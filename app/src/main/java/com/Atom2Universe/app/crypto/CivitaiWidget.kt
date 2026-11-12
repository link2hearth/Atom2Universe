package com.Atom2Universe.app.crypto

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import com.Atom2Universe.app.R

internal class CivitaiWidget(private val card: View, navigate: (Int) -> Unit,
                            save: () -> Unit, reject: () -> Unit, add: () -> Unit) {
    private val title = card.findViewById<TextView>(R.id.civitai_title)
    private val count = card.findViewById<TextView>(R.id.civitai_count)
    private val accept = card.findViewById<ImageButton>(R.id.civitai_accept)
    private val cross = card.findViewById<ImageButton>(R.id.civitai_reject)
    private val more = card.findViewById<ImageButton>(R.id.civitai_more)
    private val back = card.findViewById<ImageButton>(R.id.civitai_back)
    private val next = card.findViewById<ImageButton>(R.id.civitai_next)

    init {
        accept.setOnClickListener { save() }
        cross.setOnClickListener { reject() }
        back.setOnClickListener { navigate(-1) }
        next.setOnClickListener { navigate(1) }
        more.setOnClickListener { add() }
        val normal = accept.imageTintList?.defaultColor ?: title.currentTextColor
        accept.imageTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()), intArrayOf(Color.WHITE, normal))
        var dx = 0f
        var dy = 0f
        card.findViewById<View>(R.id.civitai_handle).setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = card.translationX - event.rawX
                    dy = card.translationY - event.rawY
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    card.translationX = event.rawX + dx
                    card.translationY = event.rawY + dy
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    card.keepFloatingWidgetOnScreen()
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            true
        }
        card.keepFloatingWidgetOnScreenOnResize()
    }

    fun render(visible: Boolean, label: String?, position: CivitaiSlideshow.Position, batch: Int?, saved: Boolean, busy: Boolean,
               transparency: Int) {
        card.isVisible = visible
        if (!visible) return
        card.alpha = 1f - transparency / 100f
        title.text = label ?: card.context.getString(R.string.civitai_widget_label)
        val index = card.context.getString(R.string.civitai_position, position.current, position.total)
        count.text = if (batch == null) index else card.context.getString(R.string.civitai_loading_batch, index, batch)
        accept.isSelected = saved
        accept.contentDescription = card.context.getString(if (saved) R.string.civitai_unvalidate else R.string.clicker_civitai_save)
        accept.isEnabled = label != null && !busy
        cross.isEnabled = label != null && !saved && !busy
        back.isEnabled = label != null && !busy
        next.isEnabled = !busy
        more.isEnabled = batch == null && !busy
        for (v in listOf(cross, back, next, more, accept)) v.alpha = if (v.isEnabled || v.isSelected) 1f else .45f
    }
}
