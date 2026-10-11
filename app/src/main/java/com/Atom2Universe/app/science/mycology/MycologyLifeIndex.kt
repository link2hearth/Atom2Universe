package com.Atom2Universe.app.science.mycology

import com.Atom2Universe.app.science.parentes.LifeTree

/**
 * Lien entre l'atlas des champignons et l'arbre du vivant, par nom scientifique exact seulement : jamais
 * d'espèce déduite d'un genre. L'arbre garde parfois l'ancien nom (table [TREE_NAMES]), l'atlas le nom actuel.
 */
class MycologyLifeIndex(tree: LifeTree, latinById: Map<String, String>) {
    private val profiles = latinById.entries.groupBy { it.value }
    private val nodeToProfile = tree.species.groupBy { TREE_NAMES[it.scientific] ?: it.scientific }
        .mapNotNull { (latin, nodes) ->
            // « Amanita phalloides » a deux fiches (adulte et stade œuf) : la première est l'espèce.
            val profile = profiles[latin]?.firstOrNull()
            if (nodes.size == 1 && profile != null) nodes.single().id to profile.key else null
        }.toMap()
    private val profileToNode = nodeToProfile.entries.associate { it.value to it.key }
    fun forNode(id: String?) = nodeToProfile[id]
    fun forSpecies(id: String) = profileToNode[id]

    private companion object {
        /** Nom dans l'arbre (Open Tree) → nom actuel utilisé par l'atlas. */
        val TREE_NAMES = mapOf(
            "Boletus calopus" to "Caloboletus calopus",
            "Boletus badius" to "Imleria badia",
            "Boletus erythropus" to "Neoboletus erythropus",
            "Boletus satanas" to "Rubroboletus satanas",
            "Chlorophyllum rachodes" to "Chlorophyllum rhacodes",
            "Inocybe erubescens" to "Inosperma erubescens"
        )
    }
}
