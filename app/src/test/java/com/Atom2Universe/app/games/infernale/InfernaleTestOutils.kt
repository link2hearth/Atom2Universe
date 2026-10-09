package com.Atom2Universe.app.games.infernale

import com.Atom2Universe.app.games.physics.PhysBody

/**
 * Lache une bille dont le **centre** est en ([x], [y]) et rend son corps.
 *
 * Une bille est une piece comme une autre ; ce raccourci evite aux tests de calculer
 * le point de contact qu'attend [Pieces.bille].
 */
fun Plateau.lacher(x: Float, y: Float, masse: Float = 2f): PhysBody =
    poser(Pieces.bille(x, y - Pieces.BILLE_RAYON, masse = masse)).principal
