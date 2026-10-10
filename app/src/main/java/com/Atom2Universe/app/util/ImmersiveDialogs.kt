package com.Atom2Universe.app.util

import android.app.Dialog
import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialog

/** Prepare before show() takes focus, including when a builder or DialogFragment shows it. */
open class ImmersiveDialog(context: Context, themeResId: Int = 0) : Dialog(context, themeResId) {
    override fun show() {
        followImmersiveMode()
        super.show()
    }
}

class ImmersiveBottomSheetDialog(context: Context, themeResId: Int = 0) : BottomSheetDialog(context, themeResId) {
    override fun show() {
        followImmersiveMode()
        super.show()
    }
}

class ImmersiveDatePickerDialog(
    context: Context,
    listener: android.app.DatePickerDialog.OnDateSetListener,
    year: Int,
    month: Int,
    day: Int
) : android.app.DatePickerDialog(context, listener, year, month, day) {
    override fun show() {
        followImmersiveMode()
        super.show()
    }
}

class ImmersiveAlertDialogBuilder(context: Context, themeResId: Int = 0) : AlertDialog.Builder(context, themeResId) {
    override fun create(): AlertDialog = super.create().also { it.followImmersiveMode() }
}

class ImmersivePlatformAlertDialogBuilder(context: Context, themeResId: Int = 0) :
    android.app.AlertDialog.Builder(context, themeResId) {
    override fun create(): android.app.AlertDialog = super.create().also { it.followImmersiveMode() }
}

class ImmersiveMaterialAlertDialogBuilder(context: Context, themeResId: Int = 0) :
    MaterialAlertDialogBuilder(context, themeResId) {
    override fun create(): AlertDialog = super.create().also { it.followImmersiveMode() }
}
