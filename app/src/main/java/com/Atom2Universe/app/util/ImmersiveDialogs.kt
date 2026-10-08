package com.Atom2Universe.app.util

import android.app.Dialog
import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Prepare before show() takes focus, including when a builder or DialogFragment shows it. */
open class ImmersiveDialog(context: Context, themeResId: Int = 0) : Dialog(context, themeResId) {
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
