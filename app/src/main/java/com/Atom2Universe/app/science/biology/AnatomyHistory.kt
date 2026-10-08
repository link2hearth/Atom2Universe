package com.Atom2Universe.app.science.biology

/** Historique borné d'actions atomiques ; une action sans changement garde le rétablissement. */
internal class AnatomyHistory<T>(private val limit: Int = 40) {
    data class Change<T>(val before: T, val after: T)
    private val previous = ArrayDeque<Change<T>>()
    private val next = ArrayDeque<Change<T>>()
    val canUndo get() = previous.isNotEmpty()
    val canRedo get() = next.isNotEmpty()
    val undoEntries get() = previous.toList()
    val redoEntries get() = next.toList()

    fun record(before: T, after: T) {
        if (before == after) return
        previous.addLast(Change(before, after))
        if (previous.size > limit) previous.removeFirst()
        next.clear()
    }

    fun undo(): T? = previous.removeLastOrNull()?.also { next.addLast(it) }?.before
    fun redo(): T? = next.removeLastOrNull()?.also { previous.addLast(it) }?.after

    fun restore(undo: List<Change<T>>, redo: List<Change<T>>) {
        previous.clear(); next.clear()
        previous.addAll(undo.takeLast(limit))
        next.addAll(redo.takeLast((limit - previous.size).coerceAtLeast(0)))
    }
}
