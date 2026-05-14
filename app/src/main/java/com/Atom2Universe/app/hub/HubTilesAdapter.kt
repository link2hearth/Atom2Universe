package com.Atom2Universe.app.hub

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.google.android.material.card.MaterialCardView
import java.util.Collections

/**
 * Adaptateur généralisé pour les tuiles de hub avec support:
 * - Drag & drop pour réordonner
 * - Mode édition pour personnaliser les couleurs
 * - Jusqu'à 3 badges d'accès rapide par tuile
 */
class HubTilesAdapter(
    private val context: Context,
    private val onTileClick: (HubTile) -> Unit,
    private val onOrderChanged: (List<String>) -> Unit,
    private val onEditTile: ((HubTile) -> Unit)? = null,
    private val onQuickAccessClick: ((HubTile, QuickAccessItem) -> Unit)? = null,
    private val onLongPressTile: ((HubTile) -> Unit)? = null
) : RecyclerView.Adapter<HubTilesAdapter.TileViewHolder>() {

    private val tiles = mutableListOf<HubTile>()
    private var itemTouchHelper: ItemTouchHelper? = null
    private var recyclerViewHeight: Int = 0
    private var isEditMode: Boolean = false
    private var isGridMode: Boolean = true
    private var showQuickAccessButtons: Boolean = false

    fun setTiles(newTiles: List<HubTile>) {
        tiles.clear()
        tiles.addAll(newTiles)
        notifyDataSetChanged()
    }

    fun getTiles(): List<HubTile> = tiles.toList()

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

    fun setEditMode(enabled: Boolean) {
        if (isEditMode != enabled) {
            isEditMode = enabled
            notifyDataSetChanged()
        }
    }

    fun isEditMode(): Boolean = isEditMode

    fun setGridMode(isGrid: Boolean) {
        if (isGridMode != isGrid) {
            isGridMode = isGrid
            notifyDataSetChanged()
        }
    }

    fun setShowQuickAccessButtons(show: Boolean) {
        if (showQuickAccessButtons != show) {
            showQuickAccessButtons = show
            notifyDataSetChanged()
        }
    }

    fun updateTileColor(tileId: String, colorHex: String, textColorMode: String) {
        val index = tiles.indexOfFirst { it.id == tileId }
        if (index != -1) {
            tiles[index].customColorHex = colorHex
            tiles[index].textColorMode = textColorMode
            notifyItemChanged(index)
        }
    }

    fun resetTileColor(tileId: String) {
        val index = tiles.indexOfFirst { it.id == tileId }
        if (index != -1) {
            tiles[index].customColorHex = null
            tiles[index].textColorMode = "auto"
            notifyItemChanged(index)
        }
    }

    fun updateQuickAccess(tileId: String, items: List<QuickAccessItem>) {
        val index = tiles.indexOfFirst { it.id == tileId }
        if (index != -1) {
            tiles[index].quickAccessItems = items
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val layoutRes = if (isGridMode) R.layout.item_hub_tile_grid else R.layout.item_hub_tile_list
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return TileViewHolder(view)
    }

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        val tile = tiles[position]

        if (isGridMode && recyclerViewHeight > 0) {
            val screenWidth = context.resources.displayMetrics.widthPixels
            val maxTileHeight = (screenWidth * 0.35).toInt()
            val numRows = (tiles.size + 1) / 2
            val calculatedHeight = recyclerViewHeight / numRows.coerceAtLeast(1)
            val itemHeight = minOf(calculatedHeight, maxTileHeight)
            holder.itemView.layoutParams.height = itemHeight
        } else if (!isGridMode) {
            holder.itemView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        holder.bind(tile)

        holder.itemView.setOnClickListener {
            if (!isEditMode) {
                onTileClick(tile)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!isEditMode) {
                onLongPressTile?.invoke(tile)
            } else {
                itemTouchHelper?.startDrag(holder)
            }
            true
        }
    }

    override fun onViewRecycled(holder: TileViewHolder) {
        super.onViewRecycled(holder)
        holder.stopWobble()
        holder.stopAnimation()
    }

    override fun getItemCount(): Int = tiles.size

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) 0 else 1
    }

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
        private val description: TextView? = itemView.findViewById(R.id.tile_description)
        private val editButton: ImageButton? = itemView.findViewById(R.id.tile_edit_button)
        private val quickAccessContainer: View? = itemView.findViewById(R.id.tile_quick_access_container)
        private val badge1: TextView? = itemView.findViewById(R.id.tile_quick_access_1)
        private val badge2: TextView? = itemView.findViewById(R.id.tile_quick_access_2)
        private val badge3: TextView? = itemView.findViewById(R.id.tile_quick_access_3)

        fun bind(tile: HubTile) {
            val bgColor = if (tile.customColorHex != null) {
                try {
                    Color.parseColor(tile.customColorHex)
                } catch (e: IllegalArgumentException) {
                    ContextCompat.getColor(context, tile.defaultColorRes)
                }
            } else {
                ContextCompat.getColor(context, tile.defaultColorRes)
            }
            card.setCardBackgroundColor(bgColor)

            val textColor = calculateTextColor(bgColor, tile.textColorMode)
            val subtitleColor = calculateSubtitleColor(bgColor, tile.textColorMode)

            icon.setImageResource(tile.iconRes)
            val avd = icon.drawable as? AnimatedVectorDrawable
            if (avd != null) {
                // Globe animé : pas de teinte, on conserve les couleurs d'origine
                icon.clearColorFilter()
                ImageViewCompat.setImageTintList(icon, null)
                avd.start()
            } else {
                icon.setColorFilter(textColor)
            }

            title.setText(tile.titleRes)
            title.setTextColor(textColor)

            description?.setText(tile.descriptionRes)
            description?.setTextColor(subtitleColor)

            if (isEditMode && editButton != null) {
                editButton.visibility = View.VISIBLE
                editButton.setColorFilter(textColor)
                editButton.setOnClickListener { onEditTile?.invoke(tile) }
            } else {
                editButton?.visibility = View.GONE
            }

            bindQuickAccessBadges(tile)

            if (isEditMode) startWobble(bindingAdapterPosition) else stopWobble()
        }

        private fun bindQuickAccessBadges(tile: HubTile) {
            val items = tile.quickAccessItems
            val badges = listOf(badge1, badge2, badge3)

            if (showQuickAccessButtons && items.isNotEmpty()) {
                quickAccessContainer?.visibility = View.VISIBLE
                badges.forEachIndexed { index, badge ->
                    badge ?: return@forEachIndexed
                    val item = items.getOrNull(index)
                    if (item != null) {
                        badge.visibility = View.VISIBLE
                        badge.text = item.label
                        badge.setOnClickListener { onQuickAccessClick?.invoke(tile, item) }
                        applyBadgeColor(badge, item.colorHex)
                    } else {
                        badge.visibility = View.GONE
                        badge.setOnClickListener(null)
                    }
                }
            } else {
                quickAccessContainer?.visibility = View.GONE
                badges.forEach { it?.visibility = View.GONE }
            }
        }

        private fun applyBadgeColor(badge: TextView, colorHex: String?) {
            if (colorHex != null) {
                try {
                    val color = Color.parseColor(colorHex)
                    badge.backgroundTintList = ColorStateList.valueOf(color)
                    val luminance = ColorUtils.calculateLuminance(color)
                    badge.setTextColor(if (luminance > 0.5) Color.BLACK else Color.WHITE)
                    return
                } catch (_: IllegalArgumentException) {}
            }
            badge.backgroundTintList = null
            badge.setTextColor(Color.WHITE)
        }

        fun startWobble(position: Int) {
            (card.getTag(R.id.wobble_animator_tag) as? ObjectAnimator)?.cancel()
            val animator = ObjectAnimator.ofFloat(card, "rotation", -0.6f, 0.6f).apply {
                duration = 900
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                startDelay = (position % 5) * 120L
            }
            card.setTag(R.id.wobble_animator_tag, animator)
            animator.start()
        }

        fun stopWobble() {
            (card.getTag(R.id.wobble_animator_tag) as? ObjectAnimator)?.cancel()
            card.setTag(R.id.wobble_animator_tag, null)
            card.rotation = 0f
        }

        fun stopAnimation() {
            (icon.drawable as? AnimatedVectorDrawable)?.stop()
        }

        private fun calculateTextColor(bgColor: Int, textColorMode: String): Int {
            return when (textColorMode) {
                "white" -> Color.WHITE
                "black" -> Color.BLACK
                "gray" -> Color.GRAY
                else -> {
                    val luminance = ColorUtils.calculateLuminance(bgColor)
                    if (luminance > 0.5) Color.BLACK else Color.WHITE
                }
            }
        }

        private fun calculateSubtitleColor(bgColor: Int, textColorMode: String): Int {
            return when (textColorMode) {
                "white" -> Color.argb(200, 255, 255, 255)
                "black" -> Color.argb(180, 0, 0, 0)
                "gray" -> Color.argb(200, 128, 128, 128)
                else -> {
                    val luminance = ColorUtils.calculateLuminance(bgColor)
                    if (luminance > 0.5) Color.argb(180, 0, 0, 0)
                    else Color.argb(200, 255, 255, 255)
                }
            }
        }
    }
}

/**
 * ItemTouchHelper.Callback pour le drag & drop des tuiles
 */
class HubTileTouchCallback(
    private val adapter: HubTilesAdapter
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean = false

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

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
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
