package com.Atom2Universe.app.games.toyboxracers.track

/**
 * Zone où la piste (un seul spline fermé) repasse au-dessus d'elle-même : le
 * brin haut perd sa dalle entre les deux fractions pour que la voiture décolle
 * et survole le brin bas qui occupe la même zone XZ.
 */
internal data class GradeCrossing(val gapStartFraction: Float, val gapEndFraction: Float)

/** Généralise l'ancien couple jumpStartDistance/jumpEndDistance à N croisements par circuit. */
internal object CircuitCrossings {
    private val table: Map<CircuitKind, List<GradeCrossing>> = mapOf(
        CircuitKind.FIGURE_EIGHT to listOf(
            GradeCrossing(PrototypeTrack.JUMP_START_FRACTION, PrototypeTrack.JUMP_END_FRACTION)
        )
    )

    fun crossingsFor(kind: CircuitKind): List<GradeCrossing> = table[kind] ?: emptyList()
}
