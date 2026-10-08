package com.Atom2Universe.app

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup

/** Responsive silhouettes shared by XML surfaces, Kotlin panels and illustrated cards. */
object OrnamentStyle {
    fun background(context: Context, base: Drawable, variant: Int = 0): Drawable {
        if (!AppearanceStyle.hasOrnaments(context)) return base
        return OrnamentDrawable(context, base, variant)
    }

    /** Refresh after a live theme change, without rebuilding views or attaching overlays. */
    internal fun refreshCards(root: View, context: Context) {
        fun visit(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (view is OrnamentalCard) view.refreshOrnament(context)
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
    }
}
