package com.Atom2Universe.app.crypto

import android.content.Context
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.content.edit
import androidx.core.view.isVisible

/**
 * Mémorise l'endroit et la taille donnés à chaque widget flottant, pour les retrouver au prochain lancement.
 * Désactiver un widget les oublie : réactivé, il revient à son emplacement et à sa taille d'origine.
 */
internal class FloatingWidgetPlaces(context: Context) {
    private class Place(val key: String, val view: View, val enabled: () -> Boolean,
                        val defaultScale: Float, var visibility: Int)

    private val prefs = context.getSharedPreferences("clicker_widget_places", Context.MODE_PRIVATE)
    private val places = mutableListOf<Place>()

    // Masquer un widget déclenche une mise en page : c'est là qu'on voit qu'il vient d'être désactivé.
    val onLayout = ViewTreeObserver.OnGlobalLayoutListener {
        for (place in places) {
            val visibility = place.view.visibility
            if (visibility == place.visibility) continue
            place.visibility = visibility
            if (!place.enabled()) forget(place)
        }
    }

    /** [enabled] : par défaut un widget visible ; à préciser pour ceux qui se cachent d'eux-mêmes. */
    fun add(key: String, view: View, enabled: () -> Boolean = { view.isVisible }) {
        places += Place(key, view, enabled, view.scaleX, view.visibility)
        if (!prefs.contains("x_$key")) return
        view.translationX = prefs.getFloat("x_$key", 0f)
        view.translationY = prefs.getFloat("y_$key", 0f)
        val scale = prefs.getFloat("scale_$key", view.scaleX)
        view.scaleX = scale
        view.scaleY = scale
        view.post { view.keepFloatingWidgetOnScreen() }
    }

    fun save() = prefs.edit {
        for (place in places) {
            if (place.enabled()) {
                putFloat("x_${place.key}", place.view.translationX)
                putFloat("y_${place.key}", place.view.translationY)
                putFloat("scale_${place.key}", place.view.scaleX)
            } else {
                remove("x_${place.key}")
                remove("y_${place.key}")
                remove("scale_${place.key}")
            }
        }
    }

    private fun forget(place: Place) {
        place.view.translationX = 0f
        place.view.translationY = 0f
        place.view.scaleX = place.defaultScale
        place.view.scaleY = place.defaultScale
        prefs.edit {
            remove("x_${place.key}")
            remove("y_${place.key}")
            remove("scale_${place.key}")
        }
    }
}
