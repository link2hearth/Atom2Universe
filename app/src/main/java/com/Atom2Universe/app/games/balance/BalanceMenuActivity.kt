package com.Atom2Universe.app.games.balance

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
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.util.enableImmersiveMode

/**
 * L'entrée de la tuile « Équilibre » : on y choisit entre les deux jeux du dossier, la planche
 * (poids posés sur un levier) et le mobile (fils à faire glisser).
 */
class BalanceMenuActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()

        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppearanceStyle.screen(this@BalanceMenuActivity)
        }

        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_app_back)
            setColorFilter(couleurs.text)
            background = null
            contentDescription = getString(R.string.balance_back)
            setPadding(px(10), px(10), px(10), px(10))
            setOnClickListener { finish() }
        }
        root.addView(back, LinearLayout.LayoutParams(px(52), px(52)).apply {
            setMargins(px(4), px(4), 0, 0)
        })

        val cards = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(px(20), px(8), px(20), px(28))
        }
        cards.addView(
            card(
                R.string.balance_menu_plank, R.string.balance_menu_plank_desc,
                BalanceHubTileDrawable(this), BalanceActivity::class.java
            ),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                .apply { setMargins(0, 0, 0, px(14)) }
        )
        cards.addView(
            card(
                R.string.balance_menu_mobile, R.string.balance_menu_mobile_desc,
                MobileHubTileDrawable(this), MobileActivity::class.java
            ),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        root.addView(cards, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
    }

    /** Les couleurs du thème choisi : fond, texte et accent. */
    private val couleurs by lazy { SciencePalette(this) }

    /**
     * Une carte : le dessin du jeu en plein cadre, et son nom en bas sur un voile du fond du thème.
     */
    private fun card(titleRes: Int, descRes: Int, art: Drawable, target: Class<*>): View {
        val dp = resources.displayMetrics.density
        val ctx = this
        val coin = AppearanceStyle.corner(ctx, 18f)
        return FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                setColor(couleurs.surface)
                cornerRadius = coin
            }
            clipToOutline = true
            foreground = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = coin
                setStroke((2 * dp).toInt(), couleurs.accent)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(Intent(ctx, target)) }

            addView(View(ctx).apply { background = art }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ))
            val legende = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((22 * dp).toInt(), (28 * dp).toInt(), (22 * dp).toInt(), (18 * dp).toInt())
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.TRANSPARENT, ColorUtils.setAlphaComponent(couleurs.background, 0xE6))
                )
                addView(TextView(ctx).apply {
                    setText(titleRes)
                    setTextColor(couleurs.accent)
                    textSize = 26f
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(ctx).apply {
                    setText(descRes)
                    setTextColor(couleurs.secondary)
                    textSize = 14f
                    setPadding(0, (4 * dp).toInt(), 0, 0)
                })
            }
            addView(legende, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            ))
        }
    }
}
