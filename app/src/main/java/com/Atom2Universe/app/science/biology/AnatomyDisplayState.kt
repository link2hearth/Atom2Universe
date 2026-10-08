package com.Atom2Universe.app.science.biology

/** Valeurs immuables uniquement : aucune référence au moteur ou aux maillages. */
internal data class AnatomyDisplayState(
    val hidden: Set<String>,
    val isolated: String?,
    val layers: Set<String>,
    val groups: Set<String>,
    val colored: Set<String>,
    val selected: String?,
    val camera: List<Double>,
)

/** Les choix d'une couche ne doivent jamais modifier ceux d'une autre. */
internal fun updateLayerGroups(visible: Set<String>, layerGroups: Set<String>, chosen: Set<String>): Set<String> =
    (visible - layerGroups) + (chosen intersect layerGroups)
