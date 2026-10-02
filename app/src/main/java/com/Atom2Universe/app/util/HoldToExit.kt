package com.Atom2Universe.app.util

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.TooltipCompat

/**
 * Appui long « de sortie » sur une flèche retour : maintenir [holdMs] quitte l'écran d'un coup,
 * même au fond d'une arborescence, alors qu'un simple clic ne fait que remonter d'un niveau.
 *
 * Aucun texte à l'écran : l'icône grossit pendant l'appui pour montrer qu'une action se prépare,
 * puis un retour haptique confirme. Le listener ne consomme jamais l'événement (il retourne false) :
 * ripple et clic restent ceux du système. Le clic doit toutefois vérifier [fired], car le doigt
 * qui se relève après une sortie déclenche encore un clic.
 *
 * Usage :
 *     val hold = HoldToExit.attach(backButton) { quitterLeModule() }
 *     backButton.setOnClickListener { if (!hold.fired) remonterOuQuitter() }
 */
class HoldToExit private constructor(
    private val view: View,
    private val holdMs: Long,
    private val onExit: () -> Unit
) {
    /** Vrai si l'appui en cours (ou le dernier) a déclenché la sortie. */
    var fired = false
        private set

    private val exit = Runnable {
        fired = true
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onExit()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun install() {
        // Sans ça, la bulle « retour » (et sa vibration) surgit vers 1 s et on lâche avant la sortie
        TooltipCompat.setTooltipText(view, null)
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false
                    v.postDelayed(exit, holdMs)
                    v.animate().scaleX(GROW).scaleY(GROW)
                        .setDuration(holdMs)
                        .setInterpolator(LinearInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(exit)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(RELEASE_MS).start()
                }
            }
            false
        }
    }

    companion object {
        const val DEFAULT_HOLD_MS = 1500L
        private const val GROW = 1.35f
        private const val RELEASE_MS = 120L

        fun attach(view: View, holdMs: Long = DEFAULT_HOLD_MS, onExit: () -> Unit): HoldToExit =
            HoldToExit(view, holdMs, onExit).also { it.install() }
    }
}
