package com.Atom2Universe.app.games.kit

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.util.ImmersiveSpinner

/**
 * Un réglage à plusieurs valeurs (taille, difficulté, niveau) : un seul bouton qui montre le choix
 * et déroule les autres sous le doigt, aux couleurs du thème. Jamais une rangée de pastilles.
 */
object KitDropdown {

    /**
     * [selected] dit le choix en cours (pour le mettre en valeur dans la liste) ; [onPick] reçoit
     * un autre choix. Pour remettre le bouton sur le choix en cours : `setSelection(i, false)`.
     */
    fun create(activity: Activity, palette: KitPalette, labels: List<String>, selected: () -> Int,
               onPick: (Int) -> Unit): Spinner {
        val dp = activity.resources.displayMetrics.density
        val spinner = ImmersiveSpinner(activity)
        fun shape(color: Int) = GradientDrawable().apply {
            cornerRadius = AppearanceStyle.corner(activity, 16f)
            setColor(color)
            setStroke(dp.toInt().coerceAtLeast(1), palette.outline)
        }
        spinner.background = shape(palette.raised)
        spinner.setPopupBackgroundDrawable(shape(palette.surface))
        spinner.setPadding(0, 0, 0, 0)
        spinner.adapter = object : ArrayAdapter<String>(activity, 0, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                label(position, convertView, dropDown = false)
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                label(position, convertView, dropDown = true)
            private fun label(position: Int, convertView: View?, dropDown: Boolean): View =
                ((convertView as? TextView) ?: TextView(context)).apply {
                    text = getItem(position)
                    textSize = if (dropDown) 15f else 14f
                    maxLines = 1
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(if (dropDown && position == selected()) palette.ink else palette.text)
                    val h = ((if (dropDown) 20 else 14) * dp).toInt()
                    val v = ((if (dropDown) 12 else 6) * dp).toInt()
                    setPadding(h, v, h, v)
                }
        }
        spinner.setSelection(selected(), false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != selected()) onPick(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        return spinner
    }
}
