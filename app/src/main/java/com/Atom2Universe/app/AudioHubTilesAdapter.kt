package com.Atom2Universe.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.Collections

/**
 * Données pour une tuile de module audio
 */
data class AudioHubTile(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    @ColorRes val backgroundColorRes: Int
)

/**
 * Adaptateur pour les tuiles du Hub Audio avec support drag & drop
 */
class AudioHubTilesAdapter(
    private val context: Context,
    private val onTileClick: (AudioHubTile) -> Unit,
    private val onOrderChanged: (List<String>) -> Unit
) : RecyclerView.Adapter<AudioHubTilesAdapter.TileViewHolder>() {

    private val tiles = mutableListOf<AudioHubTile>()
    private var itemTouchHelper: ItemTouchHelper? = null
    private var recyclerViewHeight: Int = 0

    fun setTiles(newTiles: List<AudioHubTile>) {
        tiles.clear()
        tiles.addAll(newTiles)
        notifyDataSetChanged()
    }

    fun getTileIds(): List<String> = tiles.map { it.id }

    fun attachItemTouchHelper(helper: ItemTouchHelper) {
        itemTouchHelper = helper
    }

    fun setRecyclerViewHeight(height: Int) {
        if (recyclerViewHeight != height) {
            recyclerViewHeight = height
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audio_hub_tile, parent, false)
        return TileViewHolder(view)
    }

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        val tile = tiles[position]

        // Calcule la hauteur de chaque item (2 lignes = hauteur totale / 2)
        if (recyclerViewHeight > 0) {
            val itemHeight = recyclerViewHeight / 2
            holder.itemView.layoutParams.height = itemHeight
        }

        holder.bind(tile)

        holder.itemView.setOnClickListener {
            onTileClick(tile)
        }

        // Long press pour démarrer le drag
        holder.itemView.setOnLongClickListener {
            itemTouchHelper?.startDrag(holder)
            true
        }
    }

    override fun getItemCount(): Int = tiles.size

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(tiles, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(tiles, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    fun onDragEnd() {
        onOrderChanged(getTileIds())
    }

    inner class TileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.tile_card)
        private val icon: ImageView = itemView.findViewById(R.id.tile_icon)
        private val title: TextView = itemView.findViewById(R.id.tile_title)
        private val description: TextView = itemView.findViewById(R.id.tile_description)

        fun bind(tile: AudioHubTile) {
            card.setCardBackgroundColor(ContextCompat.getColor(context, tile.backgroundColorRes))
            icon.setImageResource(tile.iconRes)
            title.setText(tile.titleRes)
            description.setText(tile.descriptionRes)
        }
    }
}

/**
 * ItemTouchHelper.Callback pour le drag & drop des tuiles
 */
class AudioHubTileTouchCallback(
    private val adapter: AudioHubTilesAdapter
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean = true

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        adapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Non utilisé
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        // Reset l'élévation après le drag
        viewHolder.itemView.alpha = 1.0f
        adapter.onDragEnd()
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.alpha = 0.8f
        }
    }
}
