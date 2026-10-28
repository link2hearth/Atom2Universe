package com.Atom2Universe.app.crypto

import android.view.View

/**
 * Pause/reprise commune des vues animées du clicker et de ses widgets.
 *
 * Actif seulement quand la vue est attachée, que sa fenêtre est visible (l'activité est au
 * premier plan : la fenêtre passe à GONE en onStop) et, si [requireShown], que la vue est
 * affichée avec tous ses parents. La vue appelle [update] depuis onAttachedToWindow,
 * onVisibilityChanged et onWindowVisibilityChanged (ces deux-là derrière `isAttachedToWindow`,
 * car le constructeur de View peut les appeler avant que la porte n'existe), et [detach]
 * depuis onDetachedFromWindow.
 */
class VisibleWindowGate(
    private val view: View,
    private val requireShown: Boolean = true,
    private val onActiveChanged: (Boolean) -> Unit,
) {
    var isActive = false
        private set

    fun update() = set(
        view.isAttachedToWindow &&
            view.windowVisibility == View.VISIBLE &&
            (!requireShown || view.isShown)
    )

    fun detach() = set(false)

    private fun set(active: Boolean) {
        if (active == isActive) return
        isActive = active
        onActiveChanged(active)
    }
}
