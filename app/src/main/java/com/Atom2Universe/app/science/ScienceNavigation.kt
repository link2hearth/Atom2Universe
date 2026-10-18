package com.Atom2Universe.app.science

import android.content.Intent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.Atom2Universe.app.R

/** Module links use the normal activity stack so Back restores the caller's screen. */
object ScienceNavigation {
    const val EXTRA_FROM_MODULE = "science_from_module"

    fun isModuleLink(intent: Intent) = intent.getBooleanExtra(EXTRA_FROM_MODULE, false)

    fun bindHomeAction(back: View, goHome: () -> Unit) {
        // A tooltip also uses long press; reserve that gesture for returning home.
        back.tooltipText = null
        back.setOnLongClickListener { goHome(); true }
        ViewCompat.replaceAccessibilityAction(back,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
            back.context.getString(R.string.science_return_to_module_start)) { _, _ ->
            goHome(); true
        }
    }
}
