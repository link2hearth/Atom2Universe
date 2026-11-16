package com.Atom2Universe.app.util

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import kotlin.math.abs

/**
 * La bulle des jeux couchés : une colonne flottante, aux couleurs du thème, qui porte les réglages,
 * le chrono, les commandes et les actions ; sa hauteur suit son contenu. La bande du haut (retour,
 * nom du jeu, règles) garde toute la largeur, et le plateau prend le reste de l'écran.
 *
 * On la déplace par sa poignée (le petit trait en haut) : elle suit le doigt partout, par-dessus
 * le plateau ; lâchée, elle se range du côté le plus proche (gauche ou droite), à la hauteur où on
 * l'a laissée. Côté et hauteur valent pour tous les jeux.
 *
 * Deux usages :
 * - écran construit en code : créer un [SidePanel], poser ses vues dans [content], puis à chaque
 *   disposition couchée appeler [compose] ;
 * - écran décrit en XML (`layout-land`) : une rangée horizontale contient le plateau et un
 *   FrameLayout qui porte la bulle (`@id/side_panel`, LinearLayout vertical) ; après chaque mise
 *   en page couchée, appeler [bind] sur la bulle (fait par `Landscape.bindSidePanel`).
 */
class SidePanel(private val activity: Activity) {

    private val dp = activity.resources.displayMetrics.density

    /** La colonne où l'écran pose ses vues, de haut en bas. */
    val content: LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
    }

    private val scroll = ScrollView(activity).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** La bulle elle-même. */
    val bubble: LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /**
     * Rend la vue couchée : la bande [top] en haut, puis la bulle d'un côté et [board] de l'autre
     * sur toute la place restante.
     */
    fun compose(board: View, background: Drawable?, top: View? = null, width: Int = defaultWidth(activity)): ViewGroup {
        Landscape.detach(board)
        Landscape.detach(bubble)
        top?.let { Landscape.detach(it) }
        val screen = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            this.background = background
            setTag(R.id.app_effects_scene, true)
        }
        top?.let { screen.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        screen.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val holder = FrameLayout(activity)
        holder.addView(bubble, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(board, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        row.addView(holder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        bind(activity, bubble)
        return screen
    }

    companion object {
        private const val PREFS = "landscape_prefs"
        private const val KEY_LEFT = "panel_left"
        private const val KEY_HEIGHT = "panel_height"
        private const val GRIP_TAG = "side_panel_grip"

        private fun prefs(activity: Activity) = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)

        fun isLeft(activity: Activity): Boolean = prefs(activity).getBoolean(KEY_LEFT, false)

        /** La hauteur de la bulle, de 0 (en haut) à 1 (en bas) ; au milieu par défaut. */
        private fun heightOf(activity: Activity): Float = prefs(activity).getFloat(KEY_HEIGHT, 0.5f)

        /** Une largeur qui laisse au plateau carré toute la hauteur sous la bande du haut, entre 280 et 360 dp. */
        fun defaultWidth(activity: Activity): Int {
            val c = activity.resources.configuration
            val free = c.screenWidthDp - (c.screenHeightDp - 56) - 24
            return (free.coerceIn(280, 360) * activity.resources.displayMetrics.density).toInt()
        }

        /**
         * Habille une bulle : couleurs du thème, poignée, côté et hauteur choisis. La bulle est
         * posée dans un conteneur, lui-même enfant de la rangée horizontale qui porte le plateau ;
         * c'est ce conteneur qui passe d'un bout à l'autre de la rangée.
         */
        fun bind(activity: Activity, bubble: LinearLayout) {
            val dp = activity.resources.displayMetrics.density
            style(activity, bubble)
            val holder = bubble.parent as? FrameLayout ?: return
            val row = holder.parent as? LinearLayout ?: return
            val margin = (12 * dp).toInt()
            (bubble.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(margin, margin, margin, margin)
            }
            // La bulle déborde de son conteneur pendant qu'on la déplace, et passe devant le plateau.
            holder.clipChildren = false
            row.clipChildren = false
            (row.parent as? ViewGroup)?.clipChildren = false
            holder.translationZ = 8 * dp

            fun place() {
                Landscape.detach(holder)
                row.addView(holder, if (isLeft(activity)) 0 else row.childCount)
            }
            fun travel(holderHeight: Int): Float =
                (holderHeight - bubble.height - 2 * margin).coerceAtLeast(0).toFloat()
            fun settleHeight() {
                bubble.translationY = heightOf(activity) * travel(holder.height)
            }
            place()
            bubble.translationX = 0f
            // Une seule écoute à la fois : la bulle d'un écran en code passe d'un conteneur à l'autre.
            (bubble.getTag(R.id.landscape_arranged) as? View.OnLayoutChangeListener)?.let { bubble.removeOnLayoutChangeListener(it) }
            val settle = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (!dragging(bubble)) settleHeight() }
            bubble.setTag(R.id.landscape_arranged, settle)
            bubble.addOnLayoutChangeListener(settle)
            holder.addOnLayoutChangeListener(settle)

            if (bubble.getChildAt(0)?.tag == GRIP_TAG) return
            val zone = grip(activity)
            bubble.addView(zone, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (26 * dp).toInt()))
            follow(activity, zone, bubble) { fromX ->
                val loc = IntArray(2)
                bubble.getLocationOnScreen(loc)
                val centre = loc[0] + bubble.width / 2f
                val wantLeft = centre < activity.window.decorView.width / 2f
                val holderNow = bubble.parent as FrameLayout
                val max = travel(holderNow.height)
                prefs(activity).edit()
                    .putBoolean(KEY_LEFT, wantLeft)
                    .putFloat(KEY_HEIGHT, if (max > 0f) (bubble.translationY / max).coerceIn(0f, 1f) else 0.5f)
                    .apply()
                bubble.translationY = bubble.translationY.coerceIn(0f, max)
                val rowNow = holderNow.parent as LinearLayout
                if (wantLeft != (rowNow.getChildAt(0) === holderNow)) {
                    Landscape.detach(holderNow)
                    rowNow.addView(holderNow, if (wantLeft) 0 else rowNow.childCount)
                    bubble.translationX = 0f
                    bubble.post {
                        val after = IntArray(2)
                        bubble.getLocationOnScreen(after)
                        bubble.translationX = fromX - after[0]
                        bubble.animate().translationX(0f).setDuration(200).start()
                    }
                } else {
                    bubble.animate().translationX(0f).setDuration(180).start()
                }
            }
        }

        private fun dragging(bubble: View) = bubble.getTag(R.id.side_panel) == true

        private fun style(activity: Activity, bubble: LinearLayout) {
            val dp = activity.resources.displayMetrics.density
            bubble.background = GradientDrawable().apply {
                cornerRadius = AppearanceStyle.corner(activity, 24f)
                val surface = AppearanceStyle.color(activity, R.attr.a2uSurfaceColor)
                setColor(ColorUtils.setAlphaComponent(surface, 238))
                setStroke(dp.toInt().coerceAtLeast(1), AppearanceStyle.color(activity, R.attr.a2uOutlineColor))
            }
            bubble.elevation = AppearanceStyle.elevation(activity, 10f)
            bubble.setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), (10 * dp).toInt())
        }

        private fun grip(activity: Activity): FrameLayout {
            val dp = activity.resources.displayMetrics.density
            val bar = View(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 2.5f * dp
                    setColor(ColorUtils.setAlphaComponent(AppearanceStyle.color(activity, R.attr.a2uTextColor), 100))
                }
            }
            return FrameLayout(activity).apply {
                tag = GRIP_TAG
                addView(bar, FrameLayout.LayoutParams((44 * dp).toInt(), (5 * dp).toInt(), Gravity.CENTER))
            }
        }

        /**
         * La bulle suit le doigt posé sur [handle], dans les deux sens. Au lâcher, [onRelease]
         * reçoit sa position d'écran (avant tout rangement) et la range.
         */
        @SuppressLint("ClickableViewAccessibility")
        private fun follow(activity: Activity, handle: View, bubble: View, onRelease: (fromX: Float) -> Unit) {
            val slop = ViewConfiguration.get(activity).scaledTouchSlop
            var downX = 0f
            var downY = 0f
            var startX = 0f
            var startY = 0f
            var dragging = false
            handle.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY
                        bubble.animate().cancel()
                        startX = bubble.translationX; startY = bubble.translationY
                        dragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (!dragging && abs(dx) + abs(dy) > slop) {
                            dragging = true
                            bubble.setTag(R.id.side_panel, true)
                            handle.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        if (dragging) {
                            bubble.translationX = startX + dx
                            // En hauteur, la bulle reste dans la zone sous la bande du titre.
                            val holder = bubble.parent as View
                            val lp = bubble.layoutParams as ViewGroup.MarginLayoutParams
                            val max = (holder.height - bubble.height - lp.topMargin - lp.bottomMargin).coerceAtLeast(0).toFloat()
                            bubble.translationY = (startY + dy).coerceIn(0f, max)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Ranger la bulle la détache de la rangée : Android renvoie alors une
                        // annulation à la poignée, qui ne doit pas relancer le rangement.
                        val was = dragging
                        dragging = false
                        if (was) {
                            val loc = IntArray(2)
                            bubble.getLocationOnScreen(loc)
                            bubble.setTag(R.id.side_panel, null)
                            onRelease(loc[0].toFloat())
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }
}
