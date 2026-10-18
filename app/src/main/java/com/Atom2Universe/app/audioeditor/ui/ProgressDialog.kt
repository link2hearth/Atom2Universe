package com.Atom2Universe.app.audioeditor.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.ui.accentColor
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.label
import com.Atom2Universe.app.util.followImmersiveMode
import com.Atom2Universe.app.util.ImmersiveMaterialAlertDialogBuilder as MaterialAlertDialogBuilder

/** Une boîte de progression qu'on peut mettre à jour et fermer depuis le fil principal. */
class ProgressHandle(private val dialog: AlertDialog, private val bar: ProgressBar, private val message: TextView) {

    /** [fraction] entre 0 et 1 ; une valeur négative donne une barre indéterminée. */
    fun update(text: CharSequence, fraction: Float) {
        message.text = text
        bar.isIndeterminate = fraction < 0f
        if (fraction >= 0f) bar.progress = (fraction.coerceIn(0f, 1f) * bar.max).toInt()
    }

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }
}

/**
 * Montre une progression avec un bouton « Annuler » : le bouton appelle [onCancel] et se grise, mais la
 * boîte reste jusqu'à ce que le travail s'arrête vraiment et appelle [ProgressHandle.dismiss].
 */
fun Context.progressDialog(@StringRes title: Int, onCancel: () -> Unit): ProgressHandle {
    val message = label("", 14f, true)
    val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        isIndeterminate = true
        progressTintList = ColorStateList.valueOf(accentColor())
        indeterminateTintList = ColorStateList.valueOf(accentColor())
    }
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(12), dp(24), 0)
        addView(message, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
    }
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(box)
        .setCancelable(false)
        .setNegativeButton(R.string.px_cancel, null)
        .create()
    dialog.followImmersiveMode()
    dialog.setOnShowListener {
        val button = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        button.setOnClickListener {
            button.isEnabled = false
            onCancel()
        }
    }
    dialog.show()
    return ProgressHandle(dialog, bar, message)
}
