package com.Atom2Universe.app.games.kit

/**
 * Analyse du seul état affiché, réutilisée pendant les animations et les changements de sélection.
 * Les états du kit sont immuables : un coup ou une annulation change leur identité. Ne conserver
 * qu'une entrée évite de multiplier les analyses dans les 500 états de l'historique.
 * Chaque cache appartient à une vue et reste sur son fil de dessin.
 */
class PuzzleStateCache<S : Any, T>(private val calculate: (S) -> T) {
    private var entry: Pair<S, T>? = null

    operator fun get(state: S): T {
        entry?.let { if (it.first === state) return it.second }
        return calculate(state).also { entry = state to it }
    }
}

/** Les recherches CPU lancées avec runInterruptible doivent rendre la main à l'annulation. */
fun checkPuzzleWork() {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
}
