package com.Atom2Universe.app.util

import android.content.res.ColorStateList
import android.view.View
import androidx.core.view.ViewCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Donne au cadre de la feuille la couleur de son contenu. Le cadre est peint avec la couleur « surface » du thème
 * (un gris) et, quand le contenu est plus court que lui, ce gris apparaissait en bande au bas de l'écran.
 * À appeler après `setContentView`.
 */
fun BottomSheetDialog.paintSheetFrame(color: Int) {
    val frame = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
    ViewCompat.setBackgroundTintList(frame, ColorStateList.valueOf(color))
}
