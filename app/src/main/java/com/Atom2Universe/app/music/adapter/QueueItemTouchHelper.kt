package com.Atom2Universe.app.music.adapter

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * ItemTouchHelper.Callback pour gérer le drag & drop et le swipe-to-remove
 * dans la liste de la file d'attente.
 */
class QueueItemTouchHelper(
    private val adapter: QueueTrackAdapter,
    private val onItemMoved: () -> Unit,
    private val onItemRemoved: (Int) -> Unit
) : ItemTouchHelper.Callback() {

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = ItemTouchHelper.START // Swipe vers la gauche pour supprimer
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.bindingAdapterPosition
        val toPosition = target.bindingAdapterPosition
        if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
            return false
        }
        adapter.moveItem(fromPosition, toPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return
        onItemRemoved(position)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        // Notify that move is complete - this will update MusicPlaybackHolder
        // which will trigger onPlaylistChanged -> updateQueue() to refresh the UI
        onItemMoved()
    }

    override fun isLongPressDragEnabled(): Boolean = false // Drag only via handle

    override fun isItemViewSwipeEnabled(): Boolean = true
}
