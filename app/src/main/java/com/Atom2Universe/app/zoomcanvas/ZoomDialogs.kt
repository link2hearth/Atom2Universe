package com.Atom2Universe.app.zoomcanvas

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.util.followImmersiveMode
import com.Atom2Universe.app.zoomcanvas.core.TextItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Boîte de dialogue pour écrire un texte sur plusieurs lignes (la touche « entrée » passe à la ligne).
 * Le texte rendu est tel qu'il a été tapé, sans les blancs de fin.
 */
fun Context.promptMultiline(@StringRes title: Int, initial: String, onOk: (String) -> Unit) {
    val input = EditText(this).apply {
        setText(initial)
        setSelection(initial.length)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        minLines = 3
        maxLines = 8
        gravity = Gravity.TOP or Gravity.START
        filters = arrayOf(InputFilter.LengthFilter(TextItem.MAX_LENGTH))
        setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
    }
    val box = FrameLayout(this).apply {
        setPadding(dp(24), dp(8), dp(24), 0)
        addView(input)
    }
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(box)
        .setPositiveButton(R.string.px_ok) { _, _ -> onOk(input.text.toString().trimEnd()) }
        .setNegativeButton(R.string.px_cancel, null)
        .create()
        .also { it.followImmersiveMode(); it.show(); input.requestFocus() }
}
