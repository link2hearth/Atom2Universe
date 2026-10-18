package com.Atom2Universe.app.science.mycology

import com.Atom2Universe.app.science.parentes.LifeTree

/**
 * Lien entre l'atlas des champignons et l'arbre du vivant, par nom scientifique exact seulement : jamais
 * d'espèce déduite d'un genre. L'arbre garde parfois l'ancien nom (table [TREE_NAMES]), l'atlas le nom actuel.
 */
class MycologyLifeIndex(tree: LifeTree, latinById: Map<String, String>) {
    private val profiles = latinById.entries.groupBy { it.value }
    private val nodeByLatin = tree.species.groupBy { TREE_NAMES[it.scientific] ?: it.scientific }
        .mapNotNull { (latin, nodes) -> if (nodes.size == 1) latin to nodes.single() else null }
        .toMap()
    private val nodeToProfile = nodeByLatin.mapNotNull { (latin, node) ->
            // « Amanita phalloides » a deux fiches (adulte et stade œuf) : la première est l'espèce.
            val profile = profiles[latin]?.firstOrNull()
            if (profile != null) node.id to profile.key else null
        }.toMap()
    // Plusieurs fiches pédagogiques (par ex. stade œuf) peuvent partager le même taxon.
    private val profileToNode = latinById.mapNotNull { (profileId, latin) ->
        nodeByLatin[latin]?.let { profileId to it.id }
    }.toMap()
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
            "Inocybe erubescens" to "Inosperma erubescens",
            "Lactarius volemus" to "Lactifluus volemus",
            "Lactarius piperatus" to "Lactifluus piperatus",
            "Inocybe maculata" to "Inosperma maculatum",
            "Cantharellus cinereus" to "Craterellus cinereus",
            "Leccinum crocipodium" to "Leccinellum crocipodium",
            "Hypholoma lateritium" to "Hypholoma sublateritium",
            "Xerocomellus rubellus" to "Hortiboletus rubellus"
        )
    }
}
