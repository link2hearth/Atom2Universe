package com.Atom2Universe.app.audioeditor.core

/**
 * L'état d'édition d'un projet ouvert : le projet courant, l'historique annuler / rétablir et le
 * presse-papiers.
 *
 * Comme un [Project] est immuable, l'historique n'est qu'une pile de projets : annuler ne réécrit
 * aucun fichier et coûte quelques pointeurs, même après des centaines d'éditions. Les fichiers
 * audio ne sont jamais effacés pendant la session (une annulation peut en avoir besoin) ; on
 * ramasse ceux que plus aucun clip n'utilise à la fermeture, voir [Project.usedSourceIds].
 */
class EditSession(initial: Project, private val limit: Int = 200) {

    private class Step(val label: String, val project: Project)

    var project: Project = initial
        private set

    private val undoStack = ArrayList<Step>()
    private val redoStack = ArrayList<Step>()

    var clipboard: Clipboard? = null

    /** Augmente à chaque changement du projet : sert à savoir s'il faut enregistrer ou redessiner. */
    var revision: Int = 0
        private set

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    /** Ce que ferait « Annuler » / « Rétablir » (la clé passée à [commit]), ou `null`. */
    val undoLabel get() = undoStack.lastOrNull()?.label
    val redoLabel get() = redoStack.lastOrNull()?.label

    /**
     * Adopte [next] comme nouveau projet et garde l'ancien pour l'annulation. [label] est une clé
     * libre que l'écran traduit (« cut », « paste », « effect:reverb »…). Ne fait rien si [next]
     * est identique au projet courant.
     */
    fun commit(label: String, next: Project): Boolean {
        if (next == project) return false
        undoStack.add(Step(label, project))
        if (undoStack.size > limit) undoStack.removeAt(0)
        redoStack.clear()
        project = next
        revision++
        return true
    }

    /**
     * Change le projet **sans** créer de pas d'historique : pour ce qui n'a pas à s'annuler un par
     * un (le curseur de volume pendant qu'on le tire, par exemple). Appeler [commit] à la fin du
     * geste pour en faire un pas.
     */
    fun preview(next: Project) {
        if (next == project) return
        project = next
        revision++
    }

    /**
     * Valide un geste qui était en [preview] : [before] est le projet d'avant le geste, que
     * l'annulation doit retrouver.
     */
    fun commitGesture(label: String, before: Project): Boolean {
        if (before == project) return false
        undoStack.add(Step(label, before))
        if (undoStack.size > limit) undoStack.removeAt(0)
        redoStack.clear()
        revision++
        return true
    }

    fun undo(): Boolean {
        val step = undoStack.removeLastOrNull() ?: return false
        redoStack.add(Step(step.label, project))
        project = step.project
        revision++
        return true
    }

    fun redo(): Boolean {
        val step = redoStack.removeLastOrNull() ?: return false
        undoStack.add(Step(step.label, project))
        project = step.project
        revision++
        return true
    }

    /** Toutes les sources que l'historique (annuler ou rétablir) peut encore faire revenir. */
    fun sourcesInHistory(): Set<String> {
        val out = HashSet<String>(project.usedSourceIds())
        for (s in undoStack) out.addAll(s.project.usedSourceIds())
        for (s in redoStack) out.addAll(s.project.usedSourceIds())
        return out
    }
}
