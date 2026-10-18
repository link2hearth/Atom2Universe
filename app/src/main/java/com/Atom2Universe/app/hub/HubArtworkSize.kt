package com.Atom2Universe.app.hub

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Même résolution pour le préchargement et l'affichage, indépendante du pixel de colonne. */
internal object HubArtworkSize {
    fun forBounds(width: Int, height: Int): Pair<Int, Int> {
        // GridLayoutManager répartit les pixels restants entre les colonnes. La hauteur
        // commune des carrés est stable : ne pas cuire 100 autres images pour un pixel.
        val normalizedWidth = if (abs(width - height) <= 2) height else width
        val scale = min(1f, 384f / maxOf(normalizedWidth, height).coerceAtLeast(1))
        return (normalizedWidth * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }
}
