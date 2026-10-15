package com.Atom2Universe.app.games.hamlet

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.hamlet.HamletUi.Weight
import com.Atom2Universe.app.util.SystemBarsManager
import com.Atom2Universe.app.util.followImmersiveMode

/**
 * The game's popup: a parchment card under a forest ribbon with its title and a close cross.
 * It replaces Android's dialogs, so it keeps the game's colours and the full-screen setting.
 * [tall] gives it most of the screen (catalogues, long lists); otherwise it fits its content.
 */
internal class HamletSheet(private val context: Context, title: CharSequence, help: CharSequence? = null,
                           tall: Boolean = false, wide: Boolean = false) {
    val dialog = Dialog(context)
    /** Where the content goes; it scrolls when it is longer than the screen. */
    val body: LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    /** Fixed controls between the ribbon and the scrolling content (tabs, search). */
    val top: LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val scroll = ScrollView(context)
    private val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val titleView: TextView
    private val dim = ColorDrawable(0x8C000000.toInt())
    private val card: LinearLayout
    var onDismiss: (() -> Unit)? = null
    val isShowing get() = dialog.isShowing
    private fun dp(v: Int) = HamletUi.dp(context, v)

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = FrameLayout(context).apply { background = dim; setOnClickListener { dialog.dismiss() } }
        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; isClickable = true
            background = HamletUi.panel(context, HamletUi.PAPER, 18).apply { setStroke(dp(2), 0xFFCFA859.toInt()) }
            clipToOutline = true; elevation = dp(10).toFloat()
        }
        val ribbon = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(6), dp(6), dp(6))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(HamletUi.FOREST, HamletUi.DEEP)).apply {
                val r = dp(17).toFloat(); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        titleView = TextView(context).apply {
            text = title; textSize = 18.5f; setTextColor(HamletUi.GOLD); setTypeface(null, Typeface.BOLD)
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        ribbon.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        if (help != null) ribbon.addView(HamletUi.info(context, title, help, onDark = true), LinearLayout.LayoutParams(dp(44), dp(40)).apply { marginEnd = dp(4) })
        ribbon.addView(HamletUi.round(context, "cross", context.getString(R.string.hamlet_ui_close), 40) { dialog.dismiss() }.apply {
            setImageDrawable(HamletUi.icon(context, "cross", 18, accent = 0xF6EDD9)); elevation = 0f
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        card.addView(ribbon, LinearLayout.LayoutParams(-1, -2))
        top.setPadding(dp(12), 0, dp(12), 0)
        card.addView(top, LinearLayout.LayoutParams(-1, -2))
        body.setPadding(dp(14), dp(8), dp(14), dp(12))
        scroll.addView(body); scroll.isFillViewport = true
        card.addView(scroll, LinearLayout.LayoutParams(-1, if (tall) 0 else -2, 1f))
        footer.setPadding(dp(10), dp(4), dp(10), dp(10)); footer.visibility = View.GONE
        card.addView(footer, LinearLayout.LayoutParams(-1, -2))
        val metrics = context.resources.displayMetrics
        val widest = dp(if (wide) 900 else 600)
        val width = minOf(metrics.widthPixels - dp(20), widest)
        root.addView(card, FrameLayout.LayoutParams(width, if (tall) -1 else -2, Gravity.CENTER))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(SystemBarsManager.visibleTypes(context) or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top + dp(16), bars.right, bars.bottom + dp(16)); insets
        }
        dialog.setContentView(root, ViewGroup.LayoutParams(-1, -1))
        dialog.setOnDismissListener { onDismiss?.invoke() }
    }

    fun title(value: CharSequence) { titleView.text = value }
    /** Puts a long list that scrolls by itself (a recycled grid) where the plain column was; [body] is then unused. */
    fun content(view: View) {
        val at = card.indexOfChild(scroll)
        card.removeViewAt(at)
        card.addView(view, at, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    /** Buttons kept under the content, sharing the width by their weights. */
    fun footer(vararg buttons: Pair<Button, Float>) {
        footer.removeAllViews(); footer.visibility = if (buttons.isEmpty()) View.GONE else View.VISIBLE
        for ((b, w) in buttons) footer.addView(b, LinearLayout.LayoutParams(0, -2, w).apply { marginStart = dp(3); marginEnd = dp(3) })
    }
    fun show(): HamletSheet {
        dialog.followImmersiveMode()
        dialog.show()
        dialog.window?.apply {
            setLayout(-1, -1); setBackgroundDrawable(ColorDrawable(0)); clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(0)
        }
        if (HamletMotion.on) {
            dim.alpha = 0
            android.animation.ValueAnimator.ofInt(0, 255).apply { duration = 180; addUpdateListener { dim.alpha = it.animatedValue as Int } }.start()
        }
        HamletMotion.open(card)
        return this
    }
    fun dismiss() = dialog.dismiss()

    companion object {
        /** A rule or a story, closed by the cross or a tap outside. */
        fun message(context: Context, title: CharSequence, message: CharSequence) = HamletSheet(context, title).apply {
            body.addView(TextView(context).apply {
                text = message; textSize = HamletUi.TEXT; setTextColor(HamletUi.INK); setLineSpacing(HamletUi.dp(context, 3).toFloat(), 1f)
            })
        }.show()

        /** A question with its answer button; [run] happens only on that button. */
        fun confirm(context: Context, title: CharSequence, message: CharSequence?, action: CharSequence = context.getString(android.R.string.ok),
                    glyph: String? = null, run: () -> Unit): HamletSheet {
            val sheet = HamletSheet(context, title)
            if (!message.isNullOrEmpty()) sheet.body.addView(TextView(context).apply {
                text = message; textSize = HamletUi.TEXT; setTextColor(HamletUi.INK); setLineSpacing(HamletUi.dp(context, 3).toFloat(), 1f)
            })
            sheet.footer(HamletUi.button(context, context.getString(android.R.string.cancel), Weight.QUIET) { sheet.dismiss() } to 1f,
                HamletUi.button(context, action, Weight.MAIN, glyph = glyph) { sheet.dismiss(); run() } to 1.6f)
            return sheet.show()
        }

        /** One choice in a list; [chosen] is drawn gold. Rows may carry a picture. */
        fun pick(context: Context, title: CharSequence, labels: List<CharSequence>, chosen: Int = -1,
                 glyphs: List<String?>? = null, choose: (Int) -> Unit): HamletSheet {
            val sheet = HamletSheet(context, title, tall = labels.size > 9)
            labels.forEachIndexed { i, label ->
                sheet.body.addView(HamletUi.row(context, label, i == chosen, glyphs?.getOrNull(i)) { sheet.dismiss(); choose(i) },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = HamletUi.dp(context, 4) })
            }
            return sheet.show()
        }
    }
}
