package com.Atom2Universe.app.music.adapter

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.ArtistCustomizationManager
import com.Atom2Universe.app.music.ArtistDisplayMode
import com.Atom2Universe.app.music.ArtistImageCache
import com.Atom2Universe.app.music.model.Artist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class ArtistAdapter(
    private val onArtistClick: (Artist) -> Unit,
    private val onArtistLongClick: ((Artist, View) -> Boolean)? = null,
    private val onPlayClick: ((Artist) -> Unit)? = null,
    private val onShuffleClick: ((Artist) -> Unit)? = null
) : ListAdapter<Artist, RecyclerView.ViewHolder>(ArtistDiffCallback()) {

    // Scope pour le chargement asynchrone des images (avec SupervisorJob pour cancellation)
    private var supervisorJob = SupervisorJob()
    private var adapterScope = CoroutineScope(Dispatchers.Main + supervisorJob)

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        // Annuler tous les jobs en cours pour éviter les fuites mémoire
        supervisorJob.cancel()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        // Recréer le scope si nécessaire (en cas de réattachement)
        if (supervisorJob.isCancelled) {
            supervisorJob = SupervisorJob()
            adapterScope = CoroutineScope(Dispatchers.Main + supervisorJob)
        }
    }

    // Mode d'affichage actuel
    var displayMode: ArtistDisplayMode = ArtistDisplayMode.LIST
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    // Set des artistes favoris pour affichage rapide
    private val favoriteArtists = mutableSetOf<String>()

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_COMPACT = 1
        private const val VIEW_TYPE_TILE = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayMode) {
            ArtistDisplayMode.LIST -> VIEW_TYPE_LIST
            ArtistDisplayMode.COMPACT -> VIEW_TYPE_COMPACT
            ArtistDisplayMode.TILES -> VIEW_TYPE_TILE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_COMPACT -> {
                val view = inflater.inflate(R.layout.item_music_artist_compact, parent, false)
                CompactViewHolder(view)
            }
            VIEW_TYPE_TILE -> {
                val view = inflater.inflate(R.layout.item_music_artist_tile, parent, false)
                TileViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_music_artist, parent, false)
                ListViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val artist = getItem(position)
        val isFavorite = favoriteArtists.contains(artist.name.lowercase().trim())

        when (holder) {
            is ListViewHolder -> holder.bind(artist, isFavorite)
            is CompactViewHolder -> holder.bind(artist, isFavorite)
            is TileViewHolder -> holder.bind(artist, isFavorite)
        }

        holder.itemView.setOnClickListener { onArtistClick(artist) }

        if (onArtistLongClick != null) {
            holder.itemView.setOnLongClickListener { view ->
                onArtistLongClick.invoke(artist, view)
            }
        }
    }

    /**
     * Met a jour la liste des artistes favoris pour l'affichage.
     */
    fun setFavoriteArtists(favorites: Set<String>) {
        val oldFavorites = favoriteArtists.toSet()
        val newFavorites = favorites.map { it.lowercase().trim() }.toSet()

        favoriteArtists.clear()
        favoriteArtists.addAll(newFavorites)

        // Notifier uniquement les items dont le statut favori a changé
        if (oldFavorites != newFavorites) {
            val changedNames = (oldFavorites - newFavorites) + (newFavorites - oldFavorites)
            if (changedNames.size <= 10) {
                // Peu de changements : notifier individuellement
                currentList.forEachIndexed { index, artist ->
                    if (changedNames.contains(artist.name.lowercase().trim())) {
                        notifyItemChanged(index)
                    }
                }
            } else {
                // Beaucoup de changements : notifier tout
                notifyItemRangeChanged(0, itemCount)
            }
        }
    }

    /**
     * Force la mise a jour d'un artiste specifique.
     */
    fun notifyArtistChanged(artistName: String) {
        val position = currentList.indexOfFirst {
            it.name.equals(artistName, ignoreCase = true)
        }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    // ========== LIST VIEW HOLDER (mode avec icones) ==========

    inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val artistIcon: ImageView = itemView.findViewById(R.id.artist_icon)
        private val artistName: TextView = itemView.findViewById(R.id.artist_name)
        private val artistInfo: TextView = itemView.findViewById(R.id.artist_info)
        private val favoriteIcon: ImageView? = itemView.findViewById(R.id.favorite_icon)
        private val iconContainer: View? = itemView.findViewById(R.id.icon_container)
        private val btnPlay: ImageView = itemView.findViewById(R.id.btn_play)
        private val btnShuffle: ImageView = itemView.findViewById(R.id.btn_shuffle)

        fun bind(artist: Artist, isFavorite: Boolean) {
            artistName.text = artist.name

            val albumText = itemView.context.resources.getQuantityString(
                R.plurals.music_album_count, artist.albumCount, artist.albumCount
            )
            val trackText = itemView.context.resources.getQuantityString(
                R.plurals.music_track_count_plural, artist.trackCount, artist.trackCount
            )
            artistInfo.text = itemView.context.getString(
                R.string.music_artist_album_track_counts,
                albumText,
                trackText
            )

            // Gestion de l'icone personnalisee
            val customization = ArtistCustomizationManager.getCustomization(artist.name)
            val customIconPath = customization?.iconPath?.takeIf { File(it).exists() }

            // Trouver la pochette d'album comme fallback (comme en mode tuiles)
            val albumArtUri = if (customIconPath == null && artist.albums.isNotEmpty()) {
                artist.albums.firstOrNull { it.albumArtUri != null }?.albumArtUri
            } else null

            if (customIconPath != null || albumArtUri != null) {
                // Charger via le cache
                artistIcon.setPadding(0, 0, 0, 0)
                artistIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                artistIcon.imageTintList = null
                ArtistImageCache.loadArtistImage(
                    imageView = artistIcon,
                    artistName = artist.name,
                    customIconPath = customIconPath,
                    albumArtUri = albumArtUri,
                    defaultIconResId = R.drawable.ic_music_artist,
                    scope = adapterScope
                )
            } else {
                setDefaultIcon()
            }

            // Gestion de la couleur personnalisee - appliquee a toute la ligne
            val customColor = customization?.color
            if (customColor != null) {
                try {
                    val color = customColor.toColorInt()
                    // Appliquer la couleur a toute la ligne avec un peu de transparence
                    val rowBackground = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 8 * itemView.context.resources.displayMetrics.density
                        setColor(color)
                        alpha = 180 // Semi-transparent pour garder un aspect elegant
                    }
                    itemView.background = rowBackground
                } catch (_: Exception) {
                    resetRowBackground()
                }
            } else {
                resetRowBackground()
            }

            // L'icone container garde son style par defaut
            iconContainer?.setBackgroundResource(R.drawable.music_icon_circle_bg)

            favoriteIcon?.visibility = if (isFavorite) View.VISIBLE else View.GONE

            // Quick action buttons
            btnPlay.setOnClickListener { onPlayClick?.invoke(artist) }
            btnShuffle.setOnClickListener { onShuffleClick?.invoke(artist) }
        }

        private fun setDefaultIcon() {
            val padding = (8 * itemView.context.resources.displayMetrics.density).toInt()
            artistIcon.setImageResource(R.drawable.ic_music_artist)
            artistIcon.scaleType = ImageView.ScaleType.CENTER
            artistIcon.setPadding(padding, padding, padding, padding)
            artistIcon.imageTintList = ContextCompat.getColorStateList(
                itemView.context, R.color.music_text_primary
            )
        }

        private fun resetRowBackground() {
            // Restaurer le fond selectableItemBackground
            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val typedArray = itemView.context.obtainStyledAttributes(attrs)
            val backgroundDrawable = typedArray.getDrawable(0)
            typedArray.recycle()
            itemView.background = backgroundDrawable
        }
    }

    // ========== COMPACT VIEW HOLDER (mode sans icones) ==========

    class CompactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val artistName: TextView = itemView.findViewById(R.id.artist_name)
        private val artistInfo: TextView = itemView.findViewById(R.id.artist_info)
        private val favoriteIcon: ImageView? = itemView.findViewById(R.id.favorite_icon)

        fun bind(artist: Artist, isFavorite: Boolean) {
            artistName.text = artist.name

            // En mode compact, on affiche juste le nombre d'albums
            val albumText = itemView.context.resources.getQuantityString(
                R.plurals.music_album_count, artist.albumCount, artist.albumCount
            )
            artistInfo.text = albumText

            // Gestion de la couleur personnalisee - appliquee a toute la ligne
            val customization = ArtistCustomizationManager.getCustomization(artist.name)
            val customColor = customization?.color
            if (customColor != null) {
                try {
                    val color = customColor.toColorInt()
                    // Appliquer la couleur a toute la ligne avec un peu de transparence
                    val rowBackground = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 4 * itemView.context.resources.displayMetrics.density
                        setColor(color)
                        alpha = 180 // Semi-transparent pour garder un aspect elegant
                    }
                    itemView.background = rowBackground
                } catch (_: Exception) {
                    resetRowBackground()
                }
            } else {
                resetRowBackground()
            }

            favoriteIcon?.visibility = if (isFavorite) View.VISIBLE else View.GONE
        }

        private fun resetRowBackground() {
            // Restaurer le fond selectableItemBackground
            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val typedArray = itemView.context.obtainStyledAttributes(attrs)
            val backgroundDrawable = typedArray.getDrawable(0)
            typedArray.recycle()
            itemView.background = backgroundDrawable
        }
    }

    // ========== TILE VIEW HOLDER (mode grille) ==========

    inner class TileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorBackground: View = itemView.findViewById(R.id.color_background)
        private val artistImage: ImageView = itemView.findViewById(R.id.artist_image)
        private val defaultIcon: ImageView = itemView.findViewById(R.id.default_icon)
        private val artistName: TextView = itemView.findViewById(R.id.artist_name)
        private val artistInfo: TextView = itemView.findViewById(R.id.artist_info)
        private val favoriteIcon: ImageView? = itemView.findViewById(R.id.favorite_icon)
        private val btnPlay: ImageView = itemView.findViewById(R.id.btn_play)
        private val btnShuffle: ImageView = itemView.findViewById(R.id.btn_shuffle)

        fun bind(artist: Artist, isFavorite: Boolean) {
            artistName.text = artist.name

            val albumText = itemView.context.resources.getQuantityString(
                R.plurals.music_album_count, artist.albumCount, artist.albumCount
            )
            val trackText = itemView.context.resources.getQuantityString(
                R.plurals.music_track_count_plural, artist.trackCount, artist.trackCount
            )
            artistInfo.text = itemView.context.getString(
                R.string.music_artist_album_track_counts,
                albumText,
                trackText
            )

            // Le ratio 1:1 est gere par ConstraintLayout dans le XML

            val customization = ArtistCustomizationManager.getCustomization(artist.name)
            val customIconPath = customization?.iconPath?.takeIf { File(it).exists() }
            val customColor = customization?.color

            // Couleur de fond
            if (customColor != null) {
                try {
                    colorBackground.setBackgroundColor(customColor.toColorInt())
                } catch (_: Exception) {
                    colorBackground.setBackgroundColor(
                        ContextCompat.getColor(itemView.context, R.color.music_surface)
                    )
                }
            } else {
                colorBackground.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.music_surface)
                )
            }

            // Trouver la pochette d'album comme fallback
            val albumArtUri = if (customIconPath == null && artist.albums.isNotEmpty()) {
                artist.albums.firstOrNull { it.albumArtUri != null }?.albumArtUri
            } else null

            // Charger l'image via le cache (asynchrone avec placeholder)
            if (customIconPath != null || albumArtUri != null) {
                artistImage.visibility = View.VISIBLE
                defaultIcon.visibility = View.GONE

                ArtistImageCache.loadArtistImage(
                    imageView = artistImage,
                    artistName = artist.name,
                    customIconPath = customIconPath,
                    albumArtUri = albumArtUri,
                    defaultIconResId = R.drawable.ic_music_artist,
                    scope = adapterScope
                )
            } else {
                // Pas d'image disponible
                artistImage.visibility = View.GONE
                defaultIcon.visibility = View.VISIBLE
            }

            favoriteIcon?.visibility = if (isFavorite) View.VISIBLE else View.GONE

            // Quick action buttons
            btnPlay.setOnClickListener { onPlayClick?.invoke(artist) }
            btnShuffle.setOnClickListener { onShuffleClick?.invoke(artist) }
        }
    }

    class ArtistDiffCallback : DiffUtil.ItemCallback<Artist>() {
        override fun areItemsTheSame(oldItem: Artist, newItem: Artist): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: Artist, newItem: Artist): Boolean {
            return oldItem == newItem
        }
    }
}
