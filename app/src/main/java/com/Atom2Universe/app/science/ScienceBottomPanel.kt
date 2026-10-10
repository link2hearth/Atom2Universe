package com.Atom2Universe.app.science

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.Atom2Universe.app.util.ImmersiveDialog
import kotlin.math.min

/**
 * Un panneau posé en bas de l'écran, sur une scène qui reste visible derrière (le modèle 3D de la Biologie humaine).
 * Remplace la feuille du bas de Material, qui laissait une bande grise sur la zone de la barre de navigation : ici la
 * couleur du panneau passe sous cette barre. Toucher à côté du panneau ou revenir en arrière le ferme.
 *
 * [content] est le contenu avec ses propres marges ; [maxWidth] est en pixels ; [dim] assombrit la scène (0 à 1).
 */
fun bottomPanelDialog(activity: Activity, content: View, color: Int, maxWidth: Int, dim: Float = .22f): Dialog {
    val dialog = ImmersiveDialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar)
    dialog.useFullScreenWindow(Color.argb((dim * 255).toInt(), 0, 0, 0))
    val metrics = activity.resources.displayMetrics
    val root = FrameLayout(activity)
    root.addView(View(activity).apply { setOnClickListener { dialog.dismiss() } }, FrameLayout.LayoutParams(-1, -1))

    val radius = 16f * metrics.density
    val panel = FrameLayout(activity).apply {
        // Cliquable pour garder les touches sur le panneau : seul ce qui est à côté ferme.
        isClickable = true
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        }
        addView(content, FrameLayout.LayoutParams(-1, -2))
        // Le fond va jusqu'au bas de l'écran ; le contenu reste au-dessus de la barre de navigation.
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, 0, safe.right, safe.bottom)
            insets
        }
    }
    root.addView(panel, FrameLayout.LayoutParams(min(metrics.widthPixels, maxWidth), -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
    dialog.setContentView(root)
    return dialog
}
