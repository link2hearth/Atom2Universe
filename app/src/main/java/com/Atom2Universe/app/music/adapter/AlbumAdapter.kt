package com.Atom2Universe.app.music.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.AlbumDisplayMode
import com.Atom2Universe.app.music.AlbumImageCache
import com.Atom2Universe.app.music.model.Album
import com.Atom2Universe.app.music.model.AlbumListItem
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AlbumAdapter(
    private val onAlbumClick: (Album) -> Unit,
    private val onAlbumLongClick: ((Album, View) -> Boolean)? = null,
    private val onPlayClick: ((Album) -> Unit)? = null,
    private val onShuffleClick: ((Album) -> Unit)? = null,
    private val onAllTracksClick: ((List<MusicTrack>) -> Unit)? = null,
    private val onAllTracksPlayClick: ((List<MusicTrack>) -> Unit)? = null,
    private val onAllTracksShuffleClick: ((List<MusicTrack>) -> Unit)? = null
) : ListAdapter<AlbumListItem, RecyclerView.ViewHolder>(AlbumListItemDiffCallback()) {

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

    // Set des albums favoris (clé: "artistName|albumName" en lowercase)
    private val favoriteAlbums = mutableSetOf<String>()

    // Mode d'affichage actuel
    var displayMode: AlbumDisplayMode = AlbumDisplayMode.LIST
        set(value) {
            if (field != value) {
                field = value
                if (itemCount > 0) {
                    notifyItemRangeChanged(0, itemCount)
                }
            }
        }

    /**
     * Met à jour la liste des albums favoris.
     * @param favorites Set de paires (artistName, albumName)
     */
    fun setFavoriteAlbums(favorites: Set<Pair<String, String>>) {
        val oldFavorites = favoriteAlbums.toSet()
        val newFavorites = favorites.map { (artist, album) ->
            "${artist.lowercase().trim()}|${album.lowercase().trim()}"
        }.toSet()

        favoriteAlbums.clear()
        favoriteAlbums.addAll(newFavorites)

        // Notifier uniquement les items dont le statut favori a changé
        if (oldFavorites != newFavorites) {
            val changedKeys = (oldFavorites - newFavorites) + (newFavorites - oldFavorites)
            if (changedKeys.size <= 10) {
                // Peu de changements : notifier individuellement
                currentList.forEachIndexed { index, item ->
                    if (item is AlbumListItem.AlbumItem) {
                        val key = "${item.album.artist.lowercase().trim()}|${item.album.name.lowercase().trim()}"
                        if (changedKeys.contains(key)) {
                            notifyItemChanged(index)
                        }
                    }
                }
            } else {
                // Beaucoup de changements : notifier tout
                if (itemCount > 0) {
                    notifyItemRangeChanged(0, itemCount)
                }
            }
        }
    }

    /**
     * Vérifie si un album est favori.
     */
    private fun isAlbumFavorite(album: Album): Boolean {
        val key = "${album.artist.lowercase().trim()}|${album.name.lowercase().trim()}"
        return favoriteAlbums.contains(key)
    }

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_COMPACT = 1
        private const val VIEW_TYPE_TILE = 2
        private const val VIEW_TYPE_ALL_TRACKS_LIST = 3
        private const val VIEW_TYPE_ALL_TRACKS_TILE = 4
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return when {
            item is AlbumListItem.AllTracksItem && displayMode == AlbumDisplayMode.TILES -> VIEW_TYPE_ALL_TRACKS_TILE
            item is AlbumListItem.AllTracksItem -> VIEW_TYPE_ALL_TRACKS_LIST
            displayMode == AlbumDisplayMode.LIST -> VIEW_TYPE_LIST
            displayMode == AlbumDisplayMode.COMPACT -> VIEW_TYPE_COMPACT
            displayMode == AlbumDisplayMode.TILES -> VIEW_TYPE_TILE
            else -> VIEW_TYPE_LIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_COMPACT -> {
                val view = inflater.inflate(R.layout.item_music_album_compact, parent, false)
                CompactViewHolder(view)
            }
            VIEW_TYPE_TILE -> {
                val view = inflater.inflate(R.layout.item_music_album_tile, parent, false)
                TileViewHolder(view)
            }
            VIEW_TYPE_ALL_TRACKS_LIST -> {
                val view = inflater.inflate(R.layout.item_music_all_tracks, parent, false)
                AllTracksListViewHolder(view)
            }
            VIEW_TYPE_ALL_TRACKS_TILE -> {
                val view = inflater.inflate(R.layout.item_music_all_tracks_tile, parent, false)
                AllTracksTileViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_music_album, parent, false)
                ListViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)

        when (holder) {
            is ListViewHolder -> {
                val album = (item as AlbumListItem.AlbumItem).album
                holder.bind(album)
                holder.itemView.setOnClickListener { onAlbumClick(album) }
                holder.itemView.setOnLongClickListener { view ->
                    onAlbumLongClick?.invoke(album, view) ?: false
                }
            }
            is CompactViewHolder -> {
                val album = (item as AlbumListItem.AlbumItem).album
                holder.bind(album)
                holder.itemView.setOnClickListener { onAlbumClick(album) }
                holder.itemView.setOnLongClickListener { view ->
                    onAlbumLongClick?.invoke(album, view) ?: false
                }
            }
            is TileViewHolder -> {
                val album = (item as AlbumListItem.AlbumItem).album
                holder.bind(album)
                holder.itemView.setOnClickListener { onAlbumClick(album) }
                holder.itemView.setOnLongClickListener { view ->
                    onAlbumLongClick?.invoke(album, view) ?: false
                }
            }
            is AllTracksListViewHolder -> {
                val allTracksItem = item as AlbumListItem.AllTracksItem
                holder.bind(allTracksItem)
                holder.itemView.setOnClickListener { onAllTracksClick?.invoke(allTracksItem.tracks) }
            }
            is AllTracksTileViewHolder -> {
                val allTracksItem = item as AlbumListItem.AllTracksItem
                holder.bind(allTracksItem)
                holder.itemView.setOnClickListener { onAllTracksClick?.invoke(allTracksItem.tracks) }
            }
        }
    }

    // ========== LIST VIEW HOLDER (mode avec pochettes) ==========

    inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val albumArt: ImageView = itemView.findViewById(R.id.album_art)
        private val albumName: TextView = itemView.findViewById(R.id.album_name)
        private val albumInfo: TextView = itemView.findViewById(R.id.album_info)
        private val btnPlay: ImageView = itemView.findViewById(R.id.btn_play)
        private val btnShuffle: ImageView = itemView.findViewById(R.id.btn_shuffle)
        private val favoriteIcon: ImageView? = itemView.findViewById(R.id.favorite_icon)

        fun bind(album: Album) {
            albumName.text = album.name

            val trackText = itemView.context.resources.getQuantityString(
                R.plurals.music_track_count_plural, album.trackCount, album.trackCount
            )
            albumInfo.text = itemView.context.getString(
                R.string.music_album_track_count_with_duration,
                trackText,
                album.totalDurationFormatted
            )

            // Charger via le cache
            AlbumImageCache.loadAlbumArt(
                imageView = albumArt,
                album = album,
                defaultIconResId = R.drawable.ic_music_album_placeholder,
                scope = adapterScope
            )

            // Afficher le coeur si l'album est favori
            favoriteIcon?.visibility = if (isAlbumFavorite(album)) View.VISIBLE else View.GONE

            // Quick action buttons
            btnPlay.setOnClickListener { onPlayClick?.invoke(album) }
            btnShuffle.setOnClickListener { onShuffleClick?.invoke(album) }
        }
    }

    // ========== COMPACT VIEW HOLDER (mode sans pochettes) ==========

    class CompactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val albumName: TextView = itemView.findViewById(R.id.album_name)
        private val albumInfo: TextView = itemView.findViewById(R.id.album_info)

        fun bind(album: Album) {
            albumName.text = album.name

            val trackText = itemView.context.resources.getQuantityString(
                R.plurals.music_track_count_plural, album.trackCount, album.trackCount
            )
            albumInfo.text = trackText
        }
    }

    // ========== TILE VIEW HOLDER (mode grille) ==========

    inner class TileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val albumArt: ImageView = itemView.findViewById(R.id.album_art)
        private val albumName: TextView = itemView.findViewById(R.id.album_name)
        private val albumInfo: TextView = itemView.findViewById(R.id.album_info)
        private val btnPlay: ImageView = itemView.findViewById(R.id.btn_play)
        private val btnShuffle: ImageView = itemView.findViewById(R.id.btn_shuffle)
        private val favoriteIcon: ImageView? = itemView.findViewById(R.id.favorite_icon)

        fun bind(album: Album) {
            albumName.text = album.name

            val trackText = itemView.context.resources.getQuantityString(
                R.plurals.music_track_count_plural, album.trackCount, album.trackCount
            )
            albumInfo.text = album.year?.let { year ->
                itemView.context.getString(R.string.music_album_track_count_with_year, trackText, year)
            } ?: trackText

            // Charger via le cache
            AlbumImageCache.loadAlbumArt(
                imageView = albumArt,
                album = album,
                defaultIconResId = R.drawable.ic_music_album_placeholder,
                scope = adapterScope
            )

            // Afficher le coeur si l'album est favori
            favoriteIcon?.visibility = if (isAlbumFavorite(album)) View.VISIBLE else View.GONE

            // Quick action buttons
            btnPlay.setOnClickListener { onPlayClick?.invoke(album) }
            btnShuffle.setOnClickListener { onShuffleClick?.invoke(album) }
        }
    }

    // ========== ALL TRACKS LIST VIEW HOLDER ==========

    inner class AllTracksListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val info: TextView = itemView.findViewById(R.id.info)
        private val btnPlay: ImageView = itemView.findViewById(R.id.btn_play)
        private val btnShuffle: ImageView = itemView.findViewById(R.id.btn_shuffle)

        fun bind(item: AlbumListItem.AllTracksItem) {
            title.text = itemView.context.getString(R.string.music_all_tracks)

            val trackText = itemView.context.resources.getQuantityString(
                R.plurals.music_track_count_plural, item.trackCount, item.trackCount
            )
            info.text = trackText

            btnPlay.setOnClickListener { onAllTracksPlayClick?.invoke(item.tracks) }
            btnShuffle.setOnClickListener { onAllTracksShuffleClick?.invoke(item.tracks) }
        }
    }

    // ========== ALL TRACKS TILE VIEW HOLDER ==========

    inner class AllTracksTileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val info: TextView = itemView.findViewById(R.id.info)
        private val btnPlay: ImageView = itemView.findViewById(R.id.btn_play)
        private val btnShuffle: ImageView = itemView.findViewById(R.id.btn_shuffle)
        private val gradientBg: View = itemView.findViewById(R.id.gradient_bg)
        private val fallbackIcon: ImageView = itemView.findViewById(R.id.fallback_icon)
        private val artistCustomIcon: ImageView = itemView.findViewById(R.id.artist_custom_icon)
        private val albumGrid: GridLayout = itemView.findViewById(R.id.album_grid)
        private val albumArt1: ImageView = itemView.findViewById(R.id.album_art_1)
        private val albumArt2: ImageView = itemView.findViewById(R.id.album_art_2)
        private val albumArt3: ImageView = itemView.findViewById(R.id.album_art_3)
        private val albumArt4: ImageView = itemView.findViewById(R.id.album_art_4)

        fun bind(item: AlbumListItem.AllTracksItem) {
            title.text = itemView.context.getString(R.string.music_all_tracks)

            val trackText = itemView.context.resources.getQuantityString(
                R.plurals.music_track_count_plural, item.trackCount, item.trackCount
            )
            info.text = trackText

            btnPlay.setOnClickListener { onAllTracksPlayClick?.invoke(item.tracks) }
            btnShuffle.setOnClickListener { onAllTracksShuffleClick?.invoke(item.tracks) }

            // Reset visibility
            gradientBg.visibility = View.GONE
            fallbackIcon.visibility = View.GONE
            artistCustomIcon.visibility = View.GONE
            albumGrid.visibility = View.GONE

            when {
                // Priority 1: Artist custom icon
                item.hasCustomIcon && item.artistCustomIconPath != null -> {
                    artistCustomIcon.visibility = View.VISIBLE
                    loadCustomIcon(item.artistCustomIconPath)
                }
                // Priority 2: Album art grid (at least 1 album)
                item.albumArtUris.isNotEmpty() -> {
                    albumGrid.visibility = View.VISIBLE
                    loadAlbumGrid(item.albumArtUris)
                }
                // Fallback: Gradient + icon
                else -> {
                    gradientBg.visibility = View.VISIBLE
                    fallbackIcon.visibility = View.VISIBLE
                }
            }
        }

        private fun loadCustomIcon(path: String) {
            adapterScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            BitmapFactory.decodeFile(path)
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
                bitmap?.let { artistCustomIcon.setImageBitmap(it) }
            }
        }

        private fun loadAlbumGrid(uris: List<String>) {
            val albumViews = listOf(albumArt1, albumArt2, albumArt3, albumArt4)
            val context = itemView.context

            // Clear all first
            albumViews.forEach { it.setImageResource(R.drawable.ic_music_album_placeholder) }

            // Load each album art
            uris.take(4).forEachIndexed { index, uriString ->
                val imageView = albumViews[index]
                adapterScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            val uri = uriString.toUri()
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                    bitmap?.let { imageView.setImageBitmap(it) }
                }
            }

            // If less than 4 albums, fill remaining with placeholder
            for (i in uris.size until 4) {
                albumViews[i].setImageResource(R.drawable.ic_music_album_placeholder)
            }
        }
    }

    class AlbumListItemDiffCallback : DiffUtil.ItemCallback<AlbumListItem>() {
        override fun areItemsTheSame(oldItem: AlbumListItem, newItem: AlbumListItem): Boolean {
            return when {
                oldItem is AlbumListItem.AlbumItem && newItem is AlbumListItem.AlbumItem ->
                    oldItem.album.id == newItem.album.id && oldItem.album.name == newItem.album.name
                oldItem is AlbumListItem.AllTracksItem && newItem is AlbumListItem.AllTracksItem ->
                    oldItem.artistName == newItem.artistName
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: AlbumListItem, newItem: AlbumListItem): Boolean {
            return oldItem == newItem
        }
    }
}
