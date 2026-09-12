package com.Atom2Universe.app.hub

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
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

    companion object {
        private const val MIN_GRID_TILE_HEIGHT_DP = 112
    }

    private val tiles = mutableListOf<HubTile>()
    private var itemTouchHelper: ItemTouchHelper? = null
    private var recyclerViewHeight: Int = 0
    private var isEditMode: Boolean = false
    private var isGridMode: Boolean = true
    private var showQuickAccessButtons: Boolean = false
    private var spanCount: Int = 2
    private val artworkCache = mutableMapOf<String, Drawable>()

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

    fun setSpanCount(count: Int) {
        if (spanCount != count) {
            spanCount = count
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

    /**
     * Pose ou retire la pastille d'une tuile. Ne redessine que cette tuile : le hub appelle cela a
     * chaque retour a l'ecran, un notifyDataSetChanged y couperait les animations en cours.
     */
    fun setNotificationCount(tileId: String, count: Int) {
        val index = tiles.indexOfFirst { it.id == tileId }
        if (index != -1 && tiles[index].notificationCount != count) {
            tiles[index].notificationCount = count
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
            val density = context.resources.displayMetrics.density
            val screenWidth = context.resources.displayMetrics.widthPixels
            val maxTileHeight = (screenWidth * 0.35).toInt()
            // Hauteur minimale pour que l'icône (48dp) et le titre ne soient jamais coupés.
            val minTileHeight = (MIN_GRID_TILE_HEIGHT_DP * density).toInt()
            val numRows = (tiles.size + spanCount - 1) / spanCount.coerceAtLeast(1)
            val calculatedHeight = recyclerViewHeight / numRows.coerceAtLeast(1)
            // En dessous du minimum on préfère un léger scroll plutôt que d'écraser le contenu.
            val itemHeight = calculatedHeight.coerceIn(minTileHeight, maxOf(maxTileHeight, minTileHeight))
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
        private val artwork: ImageView? = itemView.findViewById(R.id.tile_artwork)
        private val icon: ImageView = itemView.findViewById(R.id.tile_icon)
        private val title: TextView = itemView.findViewById(R.id.tile_title)
        private val description: TextView? = itemView.findViewById(R.id.tile_description)
        private val editButton: ImageButton? = itemView.findViewById(R.id.tile_edit_button)
        private val quickAccessContainer: View? = itemView.findViewById(R.id.tile_quick_access_container)
        private val badge1: TextView? = itemView.findViewById(R.id.tile_quick_access_1)
        private val badge2: TextView? = itemView.findViewById(R.id.tile_quick_access_2)
        private val badge3: TextView? = itemView.findViewById(R.id.tile_quick_access_3)
        private val notificationBadge: TextView? = itemView.findViewById(R.id.tile_notification_badge)
        // Taille et ombre d'origine, lues une fois : les vues sont recyclees, un titre agrandi ou
        // assombri pour une tuile ne doit ni s'additionner, ni deteindre sur la tuile suivante.
        private val titleBaseSize = title.textSize
        private val titleBaseShadow = floatArrayOf(title.shadowRadius, title.shadowDx, title.shadowDy)
        private val titleBaseShadowColor = title.shadowColor
        private val badgeBaseSize = badge1?.textSize ?: 0f

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

            val customArtwork = tile.artworkClass?.let { artworkClass ->
                artworkCache.getOrPut("${tile.id}:${artworkClass.qualifiedName}") {
                    createArtwork(artworkClass.java)
                }
            }
            artwork?.setImageDrawable(customArtwork)
            artwork?.visibility = if (customArtwork != null) View.VISIBLE else View.GONE
            val textColor = if (customArtwork != null) Color.WHITE else calculateTextColor(bgColor, tile.textColorMode)
            val subtitleColor = if (customArtwork != null) Color.argb(230, 255, 255, 255)
                else calculateSubtitleColor(bgColor, tile.textColorMode)

            if (customArtwork != null) {
                icon.visibility = View.GONE
            } else {
                icon.visibility = View.VISIBLE
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
            }

            val textStyle = if (customArtwork != null)
                tile.activityClass?.let { HubTileArtworks.textStyleFor(it.name) } else null
            title.setText(tile.titleRes)
            title.setTextColor(textStyle?.color ?: textColor)
            title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, titleBaseSize * (textStyle?.scale ?: 1f))
            if (textStyle != null) {
                // Sous un texte sombre, l'ombre noire d'origine salit les lettres : un halo clair le detache.
                title.setShadowLayer(4f, 0f, 1f, 0x99FFFFFF.toInt())
            } else {
                title.setShadowLayer(titleBaseShadow[0], titleBaseShadow[1], titleBaseShadow[2], titleBaseShadowColor)
            }

            description?.setText(tile.descriptionRes)
            description?.setTextColor(subtitleColor)
            description?.visibility = if (tile.showDescription) View.VISIBLE else View.GONE

            if (isEditMode && editButton != null) {
                editButton.visibility = View.VISIBLE
                editButton.setColorFilter(textColor)
                editButton.setOnClickListener { onEditTile?.invoke(tile) }
            } else {
                editButton?.visibility = View.GONE
            }

            bindQuickAccessBadges(tile)
            bindNotificationBadge(tile)

            if (isEditMode) startWobble(bindingAdapterPosition) else stopWobble()
        }

        /**
         * La pastille disparait en mode edition : le coin de la tuile appartient alors au crayon,
         * et on ne veut pas faire trembler un compteur qu'on ne peut pas toucher.
         */
        private fun bindNotificationBadge(tile: HubTile) {
            val badge = notificationBadge ?: return
            val count = tile.notificationCount
            if (count <= 0 || isEditMode) {
                badge.visibility = View.GONE
                return
            }
            badge.visibility = View.VISIBLE
            badge.text = if (count > 99) "99+" else count.toString()
            badge.contentDescription = context.getString(R.string.hub_tile_ready_badge, count)
        }

        private fun createArtwork(clazz: Class<out Drawable>): Drawable =
            clazz.getConstructor(Context::class.java).newInstance(context)

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
                        // Un raccourci vers un jeu illustre reprend le dessin de sa tuile : la couleur
                        // enregistree avec lui est celle cachee sous le dessin, souvent presque noire.
                        val badgeArtwork = HubTileArtworks.forActivity(item.activityClassName)?.let { art ->
                            artworkCache.getOrPut("quick:${tile.id}:$index:${art.qualifiedName}") {
                                createArtwork(art.java)
                            }
                        }
                        if (badgeArtwork != null) applyBadgeArtwork(badge, badgeArtwork,
                            HubTileArtworks.textStyleFor(item.activityClassName))
                        else applyBadgeColor(badge, item.colorHex)
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

        /** Meme arrondi que quick_access_badge_bg : le dessin est rogne a la forme du badge. */
        private val roundedBadgeOutline = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 10f * view.resources.displayMetrics.density)
            }
        }

        private fun applyBadgeArtwork(badge: TextView, art: Drawable, style: HubTileArtworks.TextStyle?) {
            badge.background = art
            badge.backgroundTintList = null
            badge.outlineProvider = roundedBadgeOutline
            badge.clipToOutline = true
            badge.setTextColor(style?.color ?: Color.WHITE)
            badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, badgeBaseSize * (style?.scale ?: 1f))
            // Le titre est pose directement sur le dessin : une ombre le garde lisible, claire sous
            // un texte sombre, sombre sous un texte clair.
            if (style != null) badge.setShadowLayer(4f, 0f, 1f, 0x99FFFFFF.toInt())
            else badge.setShadowLayer(3f, 0f, 1f, 0xCC000000.toInt())
        }

        private fun applyBadgeColor(badge: TextView, colorHex: String?) {
            // Les vues sont recyclees : un badge qui portait un dessin doit retrouver son fond normal.
            badge.setBackgroundResource(R.drawable.quick_access_badge_bg)
            badge.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            badge.clipToOutline = false
            badge.setShadowLayer(0f, 0f, 0f, 0)
            badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, badgeBaseSize)
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
