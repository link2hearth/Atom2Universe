package com.Atom2Universe.app.games.caves.world


/**
 * Catalogue unifié des structures : structures intégrées + structures créées par le joueur.
 * Appelé une fois au démarrage depuis CaveActivity, puis consulté par World lors de la génération.
 */
object StructureRegistry {

    private val builtIn  = StructureData.all.toMutableList()
    private val userDefs = mutableListOf<StructureDef>()

    /** Recharge les structures utilisateur depuis Documents/cave_world/structures/. */
    fun loadUserStructures() {
        userDefs.clear()
        userDefs += StructureCapture.loadUserStructures()
    }

    /** Toutes les structures disponibles (intégrées + utilisateur). */
    val all: List<StructureDef> get() = builtIn + userDefs

    /** Structures créées par le joueur uniquement. */
    val userStructures: List<StructureDef> get() = userDefs.toList()

    /** Ajoute immédiatement une structure en mémoire (après sauvegarde en jeu). */
    fun addUserStructure(def: StructureDef) {
        userDefs.removeAll { it.name == def.name }
        userDefs += def
    }
}
