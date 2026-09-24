package com.Atom2Universe.app.audio

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import com.google.android.material.snackbar.Snackbar

/** Messages dans une bulle audio au premier plan ; toast système hors d'une activité. */
object AudioFeedback {
    const val LENGTH_SHORT = Toast.LENGTH_SHORT
    const val LENGTH_LONG = Toast.LENGTH_LONG

    fun makeText(context: Context?, text: CharSequence, duration: Int) = Message(context, text, duration)
    fun makeText(context: Context?, text: Int, duration: Int): Message =
        makeText(context, context?.getText(text) ?: "", duration)

    class Message internal constructor(
        private val context: Context?,
        private val text: CharSequence,
        private val duration: Int
    ) {
        fun show() {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                Handler(Looper.getMainLooper()).post { show() }
                return
            }
            // Un fragment détaché ne doit pas tenter d'afficher une nouvelle fenêtre.
            val context = context ?: return
            var current = context
            while (current is ContextWrapper && current !is Activity) {
                val base = current.baseContext
                if (base === current) break
                current = base
            }
            val activity = current as? Activity
            val root = activity?.findViewById<View>(android.R.id.content)
            if (activity == null || activity.isFinishing || activity.isDestroyed ||
                root == null || !root.isAttachedToWindow || !root.hasWindowFocus()) {
                Toast.makeText(context, text, duration).show()
                return
            }
            Snackbar.make(root, text, if (duration == LENGTH_LONG) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT)
                .setTextColor(ContextCompat.getColor(context, R.color.audio_text_primary))
                .setTextMaxLines(5)
                .apply {
                    view.background = AudioStyle.panel(context, 18f)
                    val miniPlayer = activity.findViewById<View>(R.id.mini_player)
                    if (miniPlayer?.visibility == View.VISIBLE) anchorView = miniPlayer
                }.show()
        }
    }
}
