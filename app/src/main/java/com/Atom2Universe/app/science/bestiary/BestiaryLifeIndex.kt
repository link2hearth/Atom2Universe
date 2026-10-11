package com.Atom2Universe.app.science.bestiary

import com.Atom2Universe.app.science.parentes.LifeTree

/** Only unambiguous terminal scientific names link profiles; never infer a species from a genus. */
class BestiaryLifeIndex(tree: LifeTree, names: Map<String, String>) {
    private val profiles = names.entries.groupBy { it.value }
    private val nodes = tree.species.groupBy { it.scientific }
    private val nodeToProfile = nodes.mapNotNull { (scientific, matches) ->
        val profile = profiles[scientific]?.singleOrNull()
        if (matches.size == 1 && profile != null) matches.single().id to profile.key else null
    }.toMap()
    private val profileToNode = nodeToProfile.entries.associate { it.value to it.key }
    fun forNode(id: String?) = nodeToProfile[id]
    fun forSpecies(id: String) = profileToNode[id]
}
