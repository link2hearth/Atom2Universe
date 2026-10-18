package com.Atom2Universe.app.notes.editor

/**
 * Annuler / rétablir pour l'éditeur de notes, sans rien d'Android : testé en JVM.
 *
 * On enregistre chaque changement du texte (ce qui a été retiré, ce qui a été mis, et où). Les
 * frappes successives d'un même mot se fondent en un seul pas : annuler retire un mot, pas une
 * lettre. Un espace, un retour à la ligne ou une pause de plus de [MERGE_MS] ferment le pas.
 */
class EditHistory(private val limit: Int = 300) {

    data class Change(val start: Int, val removed: String, val inserted: String, val time: Long)

    private val undo = ArrayDeque<Change>()
    private val redo = ArrayDeque<Change>()

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun clear() { undo.clear(); redo.clear() }

    fun record(start0: Int, removed0: String, inserted0: String, time: Long) {
        // Le clavier remplace souvent tout le mot en cours (« bon » → « bonj ») : on ne garde que
        // ce qui change vraiment, pour que ce soit une simple frappe.
        var p = 0
        while (p < removed0.length && p < inserted0.length && removed0[p] == inserted0[p]) p++
        var q = 0
        while (q < removed0.length - p && q < inserted0.length - p &&
            removed0[removed0.length - 1 - q] == inserted0[inserted0.length - 1 - q]) q++
        val start = start0 + p
        val removed = removed0.substring(p, removed0.length - q)
        val inserted = inserted0.substring(p, inserted0.length - q)
        if (removed.isEmpty() && inserted.isEmpty()) return
        redo.clear()
        val last = undo.lastOrNull()
        if (last != null && canMerge(last, start, removed, inserted, time)) {
            undo[undo.lastIndex] = when {
                // Frappe qui prolonge la précédente
                inserted.isNotEmpty() -> last.copy(inserted = last.inserted + inserted, time = time)
                // Retour arrière qui grignote la frappe précédente
                last.inserted.isNotEmpty() -> last.copy(inserted = last.inserted.dropLast(removed.length), time = time)
                // Retours arrière successifs
                else -> Change(start, removed + last.removed, "", time)
            }
            return
        }
        undo.addLast(Change(start, removed, inserted, time))
        while (undo.size > limit) undo.removeFirst()
    }

    private fun canMerge(last: Change, start: Int, removed: String, inserted: String, time: Long): Boolean {
        if (time - last.time > MERGE_MS) return false
        if (inserted.isNotEmpty() && removed.isEmpty() && last.removed.isEmpty()) {
            if (start != last.start + last.inserted.length) return false
            if (inserted.length != 1 || inserted[0].isWhitespace()) return false
            return last.inserted.isNotEmpty() && !last.inserted.last().isWhitespace()
        }
        if (inserted.isEmpty() && removed.length == 1 && removed[0] != '\n') {
            // Retour arrière juste à la fin de la frappe précédente
            if (last.removed.isEmpty() && last.inserted.isNotEmpty() && start == last.start + last.inserted.length - 1) {
                return last.inserted.length > 1
            }
            if (last.inserted.isEmpty() && start == last.start - 1) return true
        }
        return false
    }

    /** Le changement à défaire : remplacer [start, start + inserted[ par removed. */
    fun undo(): Change? = undo.removeLastOrNull()?.also { redo.addLast(it) }

    /** Le changement à refaire : remplacer [start, start + removed[ par inserted. */
    fun redo(): Change? = redo.removeLastOrNull()?.also { undo.addLast(it) }

    /** Une frappe ne doit pas se fondre dans un pas qu'on vient de défaire ou refaire. */
    fun seal() {
        undo.lastOrNull()?.let { undo[undo.lastIndex] = it.copy(time = Long.MIN_VALUE / 2) }
    }

    companion object {
        const val MERGE_MS = 1200L
    }
}
