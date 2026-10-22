package com.Atom2Universe.app.util

import android.app.Activity
import android.util.TypedValue
import android.widget.TextView
import com.Atom2Universe.app.R

/** Les bulles de règles sont lues à bout de bras sur tablette : texte agrandi par rapport au style du dialogue. */
private const val RULES_TEXT_SCALE = 1.5f

/**
 * La bulle de règles commune à tous les jeux : le bouton « i » (R.drawable.ic_info, en haut à droite)
 * l'ouvre avec le titre du jeu, le texte des règles et « Fermer ».
 */
fun Activity.showGameRules(titleRes: Int, message: CharSequence, onDismiss: (() -> Unit)? = null) {
    val dialog = ImmersiveAlertDialogBuilder(this)
        .setTitle(titleRes)
        .setMessage(message)
        .setPositiveButton(R.string.close, null)
        .create()
    dialog.setOnShowListener {
        dialog.findViewById<TextView>(android.R.id.message)?.let {
            it.setTextSize(TypedValue.COMPLEX_UNIT_PX, it.textSize * RULES_TEXT_SCALE)
        }
    }
    if (onDismiss != null) dialog.setOnDismissListener { onDismiss() }
    dialog.show()
}

fun Activity.showGameRules(titleRes: Int, messageRes: Int, onDismiss: (() -> Unit)? = null) =
    showGameRules(titleRes, getString(messageRes), onDismiss)
