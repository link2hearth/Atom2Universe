package com.Atom2Universe.app.periodic

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.gacha.GachaRarity
import com.Atom2Universe.app.crypto.gacha.rarityOf

/** Isolated design playground: never reads or changes collection ownership. */
class ElementCardStudioDialog(context: Context) : Dialog(context, android.R.style.Theme_Material_NoActionBar) {
    private val elements = getPeriodicElements()
    private var selected = 0
    private lateinit var card: ProceduralElementCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val density = context.resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.rgb(7, 10, 19))
        }
        fun label(value: Int, size: Float) = TextView(context).apply {
            setText(value); textSize = size; setTextColor(0xFFDCDCEC.toInt())
            gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(4))
        }
        fun row() = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            root.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        fun LinearLayout.action(title: Int, block: (Button) -> Unit): Button {
            val button = Button(context).apply {
                setText(title); isAllCaps = false; textSize = 13f
                setOnClickListener { block(this) }
            }
            addView(button, LinearLayout.LayoutParams(0, -2, 1f))
            return button
        }
        root.addView(label(R.string.card_studio_title, 23f))
        root.addView(label(R.string.card_studio_intro, 12f))
        val description = label(R.string.card_studio_hint, 12f)
        val sceneNote = label(R.string.card_note_art, 11f)
        card = ProceduralElementCardView(context)
        val raritySelector = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                listOf(context.getString(R.string.card_studio_all_rarities)) + GachaRarity.entries.map { context.getString(it.nameRes) })
        }
        root.addView(raritySelector)
        root.addView(description)
        // A scroll container keeps the preview usable in landscape and at large font sizes.
        val scroll = ScrollView(context).apply { isFillViewport = true }
        scroll.addView(card, ViewGroup.LayoutParams(-1, dp(460)))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val provider = PeriodicElementDescriptionProvider(context)
        var visibleElements = elements
        val selector = Spinner(context)
        fun populate() {
            selector.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                visibleElements.map { context.getString(R.string.card_studio_element, it.atomicNumber, it.symbol, provider.getName(it)) })
        }
        populate()
        root.addView(selector)
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position !in visibleElements.indices) return
                selected = position; card.element = visibleElements[position]
                description.text = context.getString(R.string.card_studio_identity,
                    context.getString(rarityOf(card.element.atomicNumber).nameRes),
                    context.getString(card.sceneNameRes))
                sceneNote.text = card.sceneNote
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        raritySelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val old = card.element.atomicNumber
                visibleElements = if (position == 0) elements else elements.filter { rarityOf(it.atomicNumber) == GachaRarity.entries[position-1] }
                populate()
                selector.setSelection(visibleElements.indexOfFirst { it.atomicNumber == old }.coerceAtLeast(0))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        row().apply {
            action(R.string.card_studio_previous) { selector.setSelection((selected + visibleElements.size - 1) % visibleElements.size) }
            action(R.string.card_studio_pause) {
                card.motionEnabled = !card.motionEnabled
                it.setText(if (card.motionEnabled) R.string.card_studio_pause else R.string.card_studio_play)
            }
            action(R.string.card_studio_next) { selector.setSelection((selected + 1) % visibleElements.size) }
        }
        root.addView(sceneNote)
        row().action(R.string.card_studio_close) { dismiss() }
        setContentView(root)
        window?.setLayout(-1, -1)
        selector.setSelection(selected)
    }
}
