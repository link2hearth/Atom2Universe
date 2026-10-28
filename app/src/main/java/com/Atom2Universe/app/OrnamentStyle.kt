package com.Atom2Universe.app

import android.content.Context
import android.graphics.drawable.Drawable

/** Responsive silhouettes shared by XML surfaces, Kotlin panels and illustrated cards. */
object OrnamentStyle {
    fun background(context: Context, base: Drawable, variant: Int = 0): Drawable {
        if (!AppearanceStyle.hasOrnaments(context)) return base
        return OrnamentDrawable(context, base, variant)
    }
}
