package com.Atom2Universe.app.music

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import kotlin.math.roundToInt

/** Habillage de la bibliothèque musicale, validé puis intégré à la suite audio. */
internal object MusicLibraryStyle {
    fun apply(activity: Activity) {
        Palette(activity).apply()
    }

    private class Palette(private val activity: Activity) {
        private val density = activity.resources.displayMetrics.density
        private fun dp(value: Int) = (value * density).roundToInt()
        private fun color(id: Int) = ContextCompat.getColor(activity, id)
        private val accent = TypedValue().let {
            activity.theme.resolveAttribute(R.attr.a2uMusicAccent, it, true)
            if (it.resourceId != 0) color(it.resourceId) else it.data
        }
        private val background = color(R.color.audio_background)
        private val surface = color(R.color.audio_surface)
        private val edge = ColorUtils.blendARGB(surface, accent, 0.24f)
        private fun tint(base: Int, amount: Float) = ColorUtils.blendARGB(base, accent, amount)
        private fun panel(radius: Int, raised: Boolean = false) = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(tint(surface, if (raised) 0.16f else 0.07f), surface)
        ).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), edge)
        }

        private fun round(view: View, radius: Int) {
            view.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(radius).toFloat())
                }
            }
            view.clipToOutline = true
        }

        private fun margins(view: View, horizontal: Int, top: Int, bottom: Int) {
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.marginStart = dp(horizontal)
            params.marginEnd = dp(horizontal)
            params.topMargin = dp(top)
            params.bottomMargin = dp(bottom)
            view.layoutParams = params
        }

        fun apply() {
            val toolbar = activity.findViewById<View>(R.id.toolbar)
            (toolbar.parent as View).background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(tint(background, 0.12f), background, background)
            )
            toolbar.background = panel(20)
            toolbar.elevation = dp(2).toFloat()
            margins(toolbar, 12, 12, 0)

            activity.findViewById<TextView>(R.id.stats_info).apply {
                setPadding(dp(22), dp(16), dp(22), dp(12))
                setTextColor(color(R.color.audio_text_secondary))
                letterSpacing = 0.025f
            }
            activity.findViewById<View>(R.id.folder_tracks_sort_bar).apply {
                background = panel(16)
                margins(this, 12, 0, 8)
            }

            val list = activity.findViewById<RecyclerView>(R.id.content_list)
            // Aucun padding en haut : la grille dessine alors une rangée cachée juste au-dessus
            // de l'écran, puis la reprend pour ancre à chaque mise en page (chargement d'une
            // pochette…). Après un saut de la barre alphabétique, la liste remontait ainsi
            // rangée par rangée jusqu'au début.
            list.setPadding(dp(12), 0, dp(12), dp(12))
            // Les fonds et états de sélection des adapters restent responsables de leurs états.
            list.addItemDecoration(object : RecyclerView.ItemDecoration() {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                private val bounds = RectF()
                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    if (view !is CardView) outRect.bottom = dp(6)
                }

                override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                    for (index in 0 until parent.childCount) {
                        val child = parent.getChildAt(index)
                        if (child is CardView) continue
                        bounds.set(child.left + child.translationX, child.top + child.translationY,
                            child.right + child.translationX, child.bottom + child.translationY)
                        paint.style = Paint.Style.FILL
                        paint.color = surface
                        paint.alpha = (255 * child.alpha).roundToInt().coerceIn(0, 255)
                        canvas.drawRoundRect(bounds, dp(16).toFloat(), dp(16).toFloat(), paint)
                    }
                }
            })
            list.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    if (view is CardView) {
                        view.radius = dp(20).toFloat()
                        view.cardElevation = dp(3).toFloat()
                        view.setCardBackgroundColor(surface)
                        // Habillage du contenu : conserve le ripple et les clics existants.
                        view.getChildAt(0)?.background = RippleDrawable(
                            ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 35)),
                            panel(20), null
                        )
                    } else {
                        round(view, 16)
                    }
                    view.findViewById<ImageView>(R.id.option_icon)?.apply {
                        background = panel(14, true)
                        // Icônes compactes : conserver leur espace utile.
                        val inset = if (layoutParams.width >= dp(40)) dp(10) else dp(3)
                        setPadding(inset, inset, inset, inset)
                    }
                }
                override fun onChildViewDetachedFromWindow(view: View) = Unit
            })

            val mini = activity.findViewById<ConstraintLayout>(R.id.mini_player)
            mini.background = panel(24, true)
            mini.elevation = dp(10).toFloat()
            mini.minimumHeight = dp(84)
            mini.foreground = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 24)), null, panel(24)
            )
            round(mini, 24)
            margins(mini, 12, 8, 12)
            activity.findViewById<View>(R.id.mini_progress).apply {
                // Progression rentrée dans le panneau, sous son bord supérieur arrondi.
                margins(this, 24, 7, 0)
            }
            activity.findViewById<ImageView>(R.id.mini_album_art).apply {
                layoutParams = layoutParams.apply { width = dp(44); height = dp(44) }
                margins(this, 12, 12, 12)
                round(this, 12)
            }
            activity.findViewById<TextView>(R.id.mini_title).apply {
                setTypeface(typeface, Typeface.BOLD)
                textSize = 15f
            }
            activity.findViewById<TextView>(R.id.mini_artist)
                .setTextColor(color(R.color.audio_text_secondary))
            for (id in intArrayOf(R.id.mini_btn_prev, R.id.mini_btn_next)) {
                activity.findViewById<View>(id).apply {
                    layoutParams = layoutParams.apply { width = dp(48); height = dp(48) }
                }
            }
            activity.findViewById<ImageView>(R.id.mini_btn_play_pause).apply {
                val fill = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accent)
                }
                background = RippleDrawable(ColorStateList.valueOf(0x33000000), fill, null)
                imageTintList = ColorStateList.valueOf(color(R.color.audio_on_accent))
                elevation = dp(3).toFloat()
            }
        }
    }
}
