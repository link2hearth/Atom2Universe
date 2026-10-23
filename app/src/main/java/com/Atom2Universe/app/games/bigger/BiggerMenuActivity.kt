package com.Atom2Universe.app.games.bigger

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.games.enamel.EnamelActivity
import com.Atom2Universe.app.games.enamel.EnamelHubTileDrawable
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.util.enableImmersiveMode

/** La même entrée que la tuile double d'Équilibre, en conservant l'identifiant bigger. */
class BiggerMenuActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        val dp = resources.displayMetrics.density
        val colors = SciencePalette(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = AppearanceStyle.screen(this@BiggerMenuActivity)
        }
        root.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back); setColorFilter(colors.text); background = null
            contentDescription = getString(R.string.back); setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams((52 * dp).toInt(), (52 * dp).toInt()))
        val cards = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt())
        }
        cards.addView(card(R.string.bigger_title, R.string.bigger_description, BiggerHubTileDrawable(this), BiggerActivity::class.java),
            LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = (14 * dp).toInt() })
        cards.addView(card(R.string.enamel_title, R.string.enamel_description, EnamelHubTileDrawable(this), EnamelActivity::class.java), LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(cards, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun card(title: Int, description: Int, art: Drawable, target: Class<*>): View {
        val dp = resources.displayMetrics.density
        val colors = SciencePalette(this)
        return FrameLayout(this).apply {
            background = GradientDrawable().apply { setColor(colors.surface); cornerRadius = AppearanceStyle.corner(context, 18f) }
            clipToOutline = true; isClickable = true; isFocusable = true
            foreground = GradientDrawable().apply {
                setColor(Color.TRANSPARENT); cornerRadius = AppearanceStyle.corner(context, 18f)
                setStroke((2 * dp).toInt(), colors.accent)
            }
            setOnClickListener { startActivity(Intent(this@BiggerMenuActivity, target)) }
            addView(View(context).apply { background = art }, FrameLayout.LayoutParams(-1, -1))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((22 * dp).toInt(), (28 * dp).toInt(), (22 * dp).toInt(), (18 * dp).toInt())
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.TRANSPARENT, ColorUtils.setAlphaComponent(colors.background, 240)))
                addView(TextView(context).apply { setText(title); setTextColor(colors.accent); textSize = 26f; typeface = Typeface.DEFAULT_BOLD })
                addView(TextView(context).apply { setText(description); setTextColor(colors.secondary); textSize = 14f })
            }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
    }
}
