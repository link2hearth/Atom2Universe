package com.Atom2Universe.app.crypto

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import com.Atom2Universe.app.R

internal class CivitaiWidget(private val card: View, navigate: (Int) -> Unit,
                            save: () -> Unit, reject: () -> Unit, add: () -> Unit, sort: () -> Unit,
                            undo: () -> Unit = {}) {
    private val sortButton = card.findViewById<TextView>(R.id.civitai_sort)
    private val count = card.findViewById<TextView>(R.id.civitai_count)
    private val accept = card.findViewById<ImageButton>(R.id.civitai_accept)
    private val cross = card.findViewById<ImageButton>(R.id.civitai_reject)
    private val back = card.findViewById<ImageButton>(R.id.civitai_back)
    private val next = card.findViewById<ImageButton>(R.id.civitai_next)
    private var undoing = false

    init {
        accept.setOnClickListener { save() }
        cross.setOnClickListener { reject() }
        back.setOnClickListener { if (undoing) undo() else navigate(-1) }
        next.setOnClickListener { navigate(1) }
        count.setOnClickListener { add() }
        sortButton.setOnClickListener { sort() }
        val normal = accept.imageTintList?.defaultColor ?: count.currentTextColor
        accept.imageTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()), intArrayOf(Color.rgb(46, 184, 108), normal))
        card.keepFloatingWidgetOnScreenOnResize()
    }

    fun render(visible: Boolean, label: String?, position: CivitaiSlideshow.Position, batch: Int?, saved: Boolean, busy: Boolean,
               transparency: Int, source: ClickerBackgroundSource = ClickerBackgroundSource.RECENT,
               batchTotal: Int = source.batchSize, undo: Boolean = false) {
        card.isVisible = visible
        if (!visible) return
        card.alpha = 1f - transparency / 100f
        sortButton.setText(if (source.weekly) R.string.civitai_sort_top else R.string.civitai_sort_recent)
        count.contentDescription = card.context.getString(R.string.civitai_add_batch, source.batchSize)
        val index = card.context.getString(R.string.civitai_position, position.current, position.total)
        count.text = if (batch == null) index else card.context.getString(R.string.civitai_loading_batch, index, batch, batchTotal)
        accept.isSelected = saved
        accept.contentDescription = card.context.getString(if (saved) R.string.civitai_unvalidate else R.string.clicker_civitai_save)
        accept.isEnabled = label != null && !busy
        cross.isEnabled = label != null && !busy
        if (undo != undoing) {
            undoing = undo
            back.setImageResource(if (undo) R.drawable.ic_undo else R.drawable.ic_arrow_back)
            back.contentDescription = card.context.getString(if (undo) R.string.civitai_undo else R.string.crypto_image_previous)
        }
        back.isEnabled = (label != null || undo) && !busy
        next.isEnabled = !busy
        for (v in listOf(cross, back, next, accept)) v.alpha = if (v.isEnabled || v.isSelected) 1f else .45f
    }
}
