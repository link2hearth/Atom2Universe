package com.Atom2Universe.app.periodic

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R

/**
 * L'explication d'une carte, cachée sous elle : rien qu'un petit « i » discret. Un appui déplie le
 * texte (ce que montre l'image, et le lien avec l'élément) ; un second appui, sur le « i » ou sur
 * le texte, le replie. Rien n'est écrit sur la carte elle-même.
 */
class ElementCardCaption(context: Context) : LinearLayout(context) {

    /** Prévenu à chaque dépli (`true`) ou repli (`false`), pour qui doit faire de la place au texte. */
    var onToggle: ((Boolean) -> Unit)? = null

    val text = TextView(context)
    private val button = ImageView(context)
    private var open = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val d = resources.displayMetrics.density
        text.apply {
            textSize = 13f
            setTextColor(INK)
            setLineSpacing(0f, 1.15f)
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply { cornerRadius = 12 * d; setColor(0xD910141C.toInt()) }
            visibility = GONE
            setOnClickListener { toggle() }
        }
        button.apply {
            setImageResource(R.drawable.ic_info)
            imageTintList = ColorStateList.valueOf(INK)
            alpha = CLOSED_ALPHA
            val pad = (10 * d).toInt()
            setPadding(pad, pad, pad, pad)
            contentDescription = context.getString(R.string.card_explain_toggle)
            setOnClickListener { toggle() }
        }
        addView(text, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(button, LayoutParams((44 * d).toInt(), (44 * d).toInt()))
    }

    /** Prend le texte de [card] ; l'explication repart repliée. */
    fun bind(card: ProceduralElementCardView) {
        text.text = card.sceneExplanation
        if (open) toggle()
    }

    fun toggle() {
        open = !open
        text.animate().cancel()
        if (open) {
            text.alpha = 0f
            text.visibility = VISIBLE
            text.animate().alpha(1f).setDuration(220).start()
        } else {
            text.animate().alpha(0f).setDuration(160).withEndAction { if (!open) text.visibility = GONE }.start()
        }
        button.alpha = if (open) 1f else CLOSED_ALPHA
        onToggle?.invoke(open)
    }

    private companion object {
        const val INK = 0xFFE8E2D4.toInt()
        const val CLOSED_ALPHA = .5f
    }
}
