package com.Atom2Universe.app.games.enamel

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette

/** Les deux choix restent accessibles, même quand les explications nécessitent de défiler. */
internal class RicochetMenuView(context: Context, onClassic: () -> Unit, onPuzzle: () -> Unit) : ScrollView(context) {
    private val dp = resources.displayMetrics.density
    private val palette = SciencePalette(context)

    init {
        isFillViewport = true
        val cards = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt())
        }
        fun cardParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (14 * dp).toInt() }
        cards.addView(card(R.string.ricochet_classic, R.string.enamel_description, EnamelHubTileDrawable(context), onClassic), cardParams())
        cards.addView(card(R.string.puzzle_title, R.string.puzzle_description, RicochetPuzzleHubDrawable(context), onPuzzle), cardParams())
        addView(cards)
    }

    private fun card(title: Int, description: Int, art: Drawable, click: () -> Unit): View = FrameLayout(context).apply {
        minimumHeight = (240 * dp).toInt()
        // Un enfant MATCH_PARENT gonfle la carte lors de la seconde mesure du ScrollView.
        // Le décor en fond ne participe pas à la mesure : seuls les textes fixent la hauteur.
        background = LayerDrawable(arrayOf(
            GradientDrawable().apply { setColor(palette.surface); cornerRadius = AppearanceStyle.corner(context, 18f) },
            art
        ))
        clipToOutline = true; isClickable = true; isFocusable = true
        foreground = GradientDrawable().apply {
            setColor(Color.TRANSPARENT); cornerRadius = AppearanceStyle.corner(context, 18f)
            setStroke((2 * dp).toInt(), palette.accent)
        }
        setOnClickListener { click() }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * dp).toInt(), (28 * dp).toInt(), (22 * dp).toInt(), (18 * dp).toInt())
            setBackgroundColor(palette.surface)
            addView(TextView(context).apply { text = context.getString(title); setTextColor(palette.ink(palette.accent)); textSize = 26f; typeface = Typeface.DEFAULT_BOLD })
            addView(TextView(context).apply { text = context.getString(description); setTextColor(palette.secondary); textSize = 14f })
        }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }
}
