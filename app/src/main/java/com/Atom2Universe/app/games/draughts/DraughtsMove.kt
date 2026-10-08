package com.Atom2Universe.app.games.draughts

// animPath : liste de (position d'atterrissage, pièce capturée à cette étape)
// Utilisé pour l'animation IA case par case
data class DraughtsMove(
    val from: DraughtsPos,
    val to: DraughtsPos,
    val captures: List<DraughtsPos> = emptyList(),
    val isPromotion: Boolean = false,
    val animPath: List<Pair<DraughtsPos, DraughtsPos?>> = emptyList()
) {
    val isCapture get() = captures.isNotEmpty()
    val captureCount get() = captures.size
}
