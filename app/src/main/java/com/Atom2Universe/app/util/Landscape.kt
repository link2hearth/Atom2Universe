package com.Atom2Universe.app.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R

/**
 * Les écrans de jeu qui changent de disposition quand la tablette tourne.
 *
 * Les jeux déclarent `configChanges="orientation|screenSize"` : l'activité n'est pas recréée, la
 * partie reste en mémoire. Quand l'orientation change, l'écran se redispose avec les mêmes vues :
 * - un écran construit en code redispose ses vues lui-même (voir [detach]) ;
 * - un écran décrit en XML appelle [swapLayout] : sa variante `layout-land` (ou portrait) est
 *   relue, et chaque vue qui porte un identifiant y est remplacée par la vue déjà vivante de même
 *   identifiant. Écouteurs, textes, états, vues de jeu et références du code restent valables.
 */
object Landscape {

    /** Vrai quand l'écran est plus large que haut (tablette tournée, fenêtre partagée large). */
    fun isLandscape(context: Context): Boolean {
        val c = context.resources.configuration
        return if (c.screenWidthDp > 0 && c.screenHeightDp > 0) c.screenWidthDp > c.screenHeightDp
        else c.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    /** Retire une vue de son parent, pour la reposer ailleurs. */
    fun detach(view: View): View {
        (view.parent as? ViewGroup)?.removeView(view)
        return view
    }

    /**
     * Relit [layoutRes] dans la configuration courante et y replace les vues vivantes de l'écran.
     * Un conteneur qui porte un identifiant est gardé (même instance) mais reçoit l'orientation,
     * la gravité et les marges intérieures de la nouvelle mise en page ; s'il est vide dans le XML,
     * ses enfants ajoutés en code sont gardés.
     */
    fun swapLayout(activity: Activity, layoutRes: Int) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val landscape = isLandscape(activity)
        // D'autres changements de configuration arrivent ici aussi : seule l'orientation compte.
        if (content.getTag(R.id.landscape_arranged) == landscape) return
        content.setTag(R.id.landscape_arranged, landscape)
        val oldRoot = content.getChildAt(0) ?: return
        val byId = HashMap<Int, View>()
        index(oldRoot, byId)
        val fresh = activity.layoutInflater.inflate(layoutRes, content, false)
        val params = fresh.layoutParams
        val root = graft(fresh, byId)
        detach(root)
        content.removeAllViews()
        content.addView(root, params ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        bindSidePanel(activity)
    }

    /**
     * À appeler après le `setContentView` d'un écran XML : note l'orientation de la mise en page
     * posée et habille sa bulle s'il en a une (`@id/side_panel`).
     */
    fun bindSidePanel(activity: Activity) {
        activity.findViewById<ViewGroup>(android.R.id.content)
            .setTag(R.id.landscape_arranged, isLandscape(activity))
        (activity.findViewById<View>(R.id.side_panel) as? LinearLayout)?.let { SidePanel.bind(activity, it) }
    }

    private fun index(view: View, out: HashMap<Int, View>) {
        if (view.id != View.NO_ID && view.id !in out) out[view.id] = view
        if (view is ViewGroup && view !is RecyclerView && view !is AdapterView<*>) {
            for (i in 0 until view.childCount) index(view.getChildAt(i), out)
        }
    }

    /** Rend la vue à poser à la place de [fresh] (elle-même, ou la vue vivante de même identifiant). */
    private fun graft(fresh: View, byId: HashMap<Int, View>): View {
        val old = if (fresh.id != View.NO_ID) byId.remove(fresh.id) else null
        if (old == null) {
            if (fresh is ViewGroup) graftChildren(fresh, fresh, byId)
            return fresh
        }
        if (old is ViewGroup && fresh is ViewGroup && old !is RecyclerView && old !is AdapterView<*>) {
            if (old.javaClass == fresh.javaClass) {
                copyArrangement(fresh, old)
                if (fresh.childCount > 0) {
                    old.removeAllViews()
                    graftChildren(fresh, old, byId)
                }
                return old
            }
            // Conteneur d'une autre sorte : on garde la nouvelle mise en page.
            graftChildren(fresh, fresh, byId)
            return fresh
        }
        copyLook(fresh, old)
        return old
    }

    /**
     * Une vue reprise prend la forme décrite pour la nouvelle orientation (taille du texte,
     * alignement, marges intérieures, hauteur minimale) ; son état (texte, couleur, visibilité,
     * activation) reste le sien.
     */
    private fun copyLook(from: View, to: View) {
        if (from.javaClass != to.javaClass) return
        to.setPaddingRelative(from.paddingStart, from.paddingTop, from.paddingEnd, from.paddingBottom)
        to.minimumHeight = from.minimumHeight
        to.minimumWidth = from.minimumWidth
        if (from is TextView && to is TextView) {
            to.setTextSize(TypedValue.COMPLEX_UNIT_PX, from.textSize)
            to.gravity = from.gravity
        }
    }

    /** Pose dans [target] les enfants de [source], chacun remplacé par sa vue vivante s'il en a une. */
    private fun graftChildren(source: ViewGroup, target: ViewGroup, byId: HashMap<Int, View>) {
        val children = (0 until source.childCount).map { source.getChildAt(it) }
        if (source !== target) source.removeAllViews()
        children.forEachIndexed { i, child ->
            val params = child.layoutParams
            val placed = graft(child, byId)
            if (source === target) {
                if (placed !== child) {
                    target.removeViewAt(i)
                    detach(placed)
                    target.addView(placed, i, params)
                }
            } else {
                detach(placed)
                target.addView(placed, params)
            }
        }
    }

    private fun copyArrangement(from: ViewGroup, to: ViewGroup) {
        to.setPaddingRelative(from.paddingStart, from.paddingTop, from.paddingEnd, from.paddingBottom)
        if (from is LinearLayout && to is LinearLayout) {
            to.orientation = from.orientation
            to.gravity = from.gravity
            to.weightSum = from.weightSum
        }
    }
}
