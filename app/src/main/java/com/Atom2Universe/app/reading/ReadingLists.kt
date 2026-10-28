package com.Atom2Universe.app.reading

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * Met à jour une liste déjà affichée (retour du lecteur) sans recréer l'adaptateur :
 * seules les lignes modifiées sont redessinées et la position de défilement est gardée.
 */
fun <T> RecyclerView.Adapter<*>.dispatchListDiff(
    old: List<T>,
    new: List<T>,
    sameItem: (T, T) -> Boolean,
    sameContent: (T, T) -> Boolean
) {
    DiffUtil.calculateDiff(object : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int) = sameItem(old[oldPos], new[newPos])
        override fun areContentsTheSame(oldPos: Int, newPos: Int) = sameContent(old[oldPos], new[newPos])
    }).dispatchUpdatesTo(this)
}
