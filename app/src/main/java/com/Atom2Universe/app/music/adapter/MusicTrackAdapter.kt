package com.Atom2Universe.app.music.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.MusicStatsManager
import com.Atom2Universe.app.music.TrackDisplayMode
import com.Atom2Universe.app.music.model.MusicTrack

class MusicTrackAdapter(
    private val onTrackClick: (MusicTrack) -> Unit,
    private val onFavoriteClick: ((MusicTrack) -> Unit)? = null,
    private val onTrackLongClick: ((MusicTrack, View) -> Boolean)? = null,
    private val onAddToPlaylistClick: ((MusicTrack) -> Unit)? = null,
    private val onAddToPlaylistLongClick: ((MusicTrack) -> Unit)? = null
) : ListAdapter<MusicTrack, RecyclerView.ViewHolder>(TrackDiffCallback()) {

    private var currentlyPlayingId: Long = -1
    private var favoriteIds: Set<Long> = emptySet()
    private var showPlayCount: Boolean = false
    private var showAddToPlaylistButton: Boolean = false

    // Cache des play counts pour éviter les lookups répétés pendant le bind
    private var playCountCache: Map<Long, Long> = emptyMap()

    // Mode d'affichage actuel
    var displayMode: TrackDisplayMode = TrackDisplayMode.LIST
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_COMPACT = 1
    }

    /**
     * Active ou désactive l'affichage du nombre d'écoutes
     */
    fun setShowPlayCount(show: Boolean) {
        if (showPlayCount != show) {
            showPlayCount = show
            if (show) {
                // Pré-calculer les play counts pour la liste actuelle
                refreshPlayCountCache()
            }
            notifyItemRangeChanged(0, itemCount)
        }
    }

    /**
     * Rafraîchit le cache des play counts pour la liste actuelle et force un rebind.
     * Appelé explicitement après un scan POPM complet (loadStatsForTracks).
     */
    fun refreshPlayCountCache() {
        if (showPlayCount) {
            playCountCache = currentList.associate { it.id to MusicStatsManager.getPlayCount(it) }
            // Forcer le rebind de tous les items : les play counts ont changé
            // mais DiffUtil ne le détecte pas (les MusicTrack objects sont identiques)
            notifyItemRangeChanged(0, itemCount)
        }
    }

    /**
     * Override submitList : met à jour le cache AVANT d'appeler super.submitList()
     * pour que onBindViewHolder reçoive des valeurs à jour lors du premier binding.
     *
     * Ancien comportement (bugué) : cache mis à jour dans le callback DiffUtil,
     * APRÈS que les items sont déjà bindés → affichage figé avec les anciennes valeurs.
     */
    override fun submitList(list: List<MusicTrack>?) {
        if (showPlayCount && list != null) {
            playCountCache = list.associate { it.id to MusicStatsManager.getPlayCount(it) }
        }
        super.submitList(list)
    }

    /**
     * Override submitList avec callback.
     */
    override fun submitList(list: List<MusicTrack>?, commitCallback: Runnable?) {
        if (showPlayCount && list != null) {
            playCountCache = list.associate { it.id to MusicStatsManager.getPlayCount(it) }
        }
        super.submitList(list, commitCallback)
    }

    /**
     * Active ou désactive l'affichage du bouton "+" pour ajouter à la playlist
     */
    fun setShowAddToPlaylistButton(show: Boolean) {
        if (showAddToPlaylistButton != show) {
            showAddToPlaylistButton = show
            notifyItemRangeChanged(0, itemCount)
        }
    }

    fun setCurrentlyPlaying(trackId: Long) {
        val oldId = currentlyPlayingId
        currentlyPlayingId = trackId

        val oldPosition = currentList.indexOfFirst { it.id == oldId }
        val newPosition = currentList.indexOfFirst { it.id == trackId }

        if (oldPosition >= 0) notifyItemChanged(oldPosition)
        if (newPosition >= 0) notifyItemChanged(newPosition)
    }

    fun setFavorites(favoriteTrackIds: Set<Long>) {
        val oldFavorites = favoriteIds
        favoriteIds = favoriteTrackIds

        // Notifier uniquement les items dont le statut favori a changé
        if (oldFavorites != favoriteTrackIds) {
            val changedIds = (oldFavorites - favoriteTrackIds) + (favoriteTrackIds - oldFavorites)
            if (changedIds.size <= 10) {
                // Peu de changements : notifier individuellement
                changedIds.forEach { id ->
                    val position = currentList.indexOfFirst { it.id == id }
                    if (position >= 0) notifyItemChanged(position)
                }
            } else {
                // Beaucoup de changements : notifier tout
                notifyItemRangeChanged(0, itemCount)
            }
        }
    }

    fun updateFavorite(trackId: Long, isFavorite: Boolean) {
        favoriteIds = if (isFavorite) {
            favoriteIds + trackId
        } else {
            favoriteIds - trackId
        }
        val position = currentList.indexOfFirst { it.id == trackId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    /**
     * Met à jour l'affichage du compteur d'écoutes pour un track spécifique.
     * Appelé quand le compteur est incrémenté pendant la lecture.
     */
    fun updatePlayCount(trackId: Long) {
        // Mettre à jour le cache pour ce track spécifique
        val track = currentList.find { it.id == trackId }
        if (track != null) {
            playCountCache = playCountCache + (trackId to MusicStatsManager.getPlayCount(track))
        }
        val position = currentList.indexOfFirst { it.id == trackId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayMode) {
            TrackDisplayMode.LIST -> VIEW_TYPE_LIST
            TrackDisplayMode.COMPACT -> VIEW_TYPE_COMPACT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_COMPACT -> {
                val view = inflater.inflate(R.layout.item_music_track_compact, parent, false)
                CompactViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_music_track, parent, false)
                ListViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val track = getItem(position)
        val isPlaying = track.id == currentlyPlayingId
        val isFavorite = favoriteIds.contains(track.id)
        // Utiliser le cache si disponible, sinon fetch direct (fallback pour tracks non cachés)
        val playCount = if (showPlayCount) {
            playCountCache[track.id] ?: MusicStatsManager.getPlayCount(track)
        } else 0L

        when (holder) {
            is ListViewHolder -> holder.bind(track, isPlaying, isFavorite, showPlayCount, playCount, showAddToPlaylistButton)
            is CompactViewHolder -> holder.bind(track, isPlaying, isFavorite, showPlayCount, playCount, showAddToPlaylistButton)
        }

        holder.itemView.setOnClickListener { onTrackClick(track) }

        holder.itemView.setOnLongClickListener { view ->
            onTrackLongClick?.invoke(track, view) ?: false
        }

        val btnFavorite = when (holder) {
            is ListViewHolder -> holder.btnFavorite
            is CompactViewHolder -> holder.btnFavorite
            else -> null
        }
        btnFavorite?.setOnClickListener {
            onFavoriteClick?.invoke(track)
        }

        // Handle add to playlist button
        val btnAddToPlaylist = when (holder) {
            is ListViewHolder -> holder.btnAddToPlaylist
            is CompactViewHolder -> holder.btnAddToPlaylist
            else -> null
        }
        btnAddToPlaylist?.setOnClickListener {
            onAddToPlaylistClick?.invoke(track)
        }
        btnAddToPlaylist?.setOnLongClickListener {
            onAddToPlaylistLongClick?.invoke(track)
            true
        }
    }

    // ========== LIST VIEW HOLDER (mode detaille) ==========

    class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.track_title)
        private val artistText: TextView = itemView.findViewById(R.id.track_artist)
        private val durationText: TextView = itemView.findViewById(R.id.track_duration)
        private val playingIndicator: ImageView = itemView.findViewById(R.id.playing_indicator)
        private val playCountText: TextView = itemView.findViewById(R.id.track_play_count)
        private val trackNumberText: TextView = itemView.findViewById(R.id.track_number)
        val btnFavorite: ImageButton = itemView.findViewById(R.id.btn_favorite)
        val btnAddToPlaylist: ImageButton? = itemView.findViewById(R.id.btn_add_to_playlist)

        fun bind(track: MusicTrack, isPlaying: Boolean, isFavorite: Boolean, showPlayCount: Boolean, playCount: Long, showAddToPlaylist: Boolean) {
            titleText.text = track.title
            artistText.text = track.artist
            durationText.text = track.durationFormatted
            playingIndicator.visibility = if (isPlaying) View.VISIBLE else View.GONE

            // Afficher le numéro de piste
            val trackNum = track.trackNumber
            if (trackNum != null && trackNum > 0) {
                trackNumberText.text = trackNum.toString().padStart(2, '0')
                trackNumberText.visibility = View.VISIBLE
            } else {
                trackNumberText.visibility = View.GONE
            }

            // Afficher le nombre d'écoutes
            if (showPlayCount && playCount > 0) {
                playCountText.text = itemView.context.getString(
                    R.string.music_play_count_short_format,
                    playCount
                )
                playCountText.visibility = View.VISIBLE
            } else {
                playCountText.visibility = View.GONE
            }

            // Update favorite icon
            btnFavorite.setImageResource(
                if (isFavorite) R.drawable.ic_favorite_filled
                else R.drawable.ic_favorite_outline
            )

            // Show/hide add to playlist button
            btnAddToPlaylist?.visibility = if (showAddToPlaylist) View.VISIBLE else View.GONE

            if (isPlaying) {
                itemView.setBackgroundResource(R.color.music_track_playing_bg)
            } else {
                itemView.setBackgroundResource(android.R.color.transparent)
            }
        }
    }

    // ========== COMPACT VIEW HOLDER (mode compact) ==========

    class CompactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.track_title)
        private val durationText: TextView = itemView.findViewById(R.id.track_duration)
        private val playingIndicator: ImageView = itemView.findViewById(R.id.playing_indicator)
        private val playCountText: TextView = itemView.findViewById(R.id.track_play_count)
        private val trackNumberText: TextView = itemView.findViewById(R.id.track_number)
        val btnFavorite: ImageButton = itemView.findViewById(R.id.btn_favorite)
        val btnAddToPlaylist: ImageButton? = itemView.findViewById(R.id.btn_add_to_playlist)

        fun bind(track: MusicTrack, isPlaying: Boolean, isFavorite: Boolean, showPlayCount: Boolean, playCount: Long, showAddToPlaylist: Boolean) {
            titleText.text = track.title
            durationText.text = track.durationFormatted
            playingIndicator.visibility = if (isPlaying) View.VISIBLE else View.GONE

            // Afficher le numéro de piste
            val trackNum = track.trackNumber
            if (trackNum != null && trackNum > 0) {
                trackNumberText.text = trackNum.toString().padStart(2, '0')
                trackNumberText.visibility = View.VISIBLE
            } else {
                trackNumberText.visibility = View.GONE
            }

            // Afficher le nombre d'écoutes
            if (showPlayCount && playCount > 0) {
                playCountText.text = itemView.context.getString(
                    R.string.music_play_count_short_format,
                    playCount
                )
                playCountText.visibility = View.VISIBLE
            } else {
                playCountText.visibility = View.GONE
            }

            // Update favorite icon
            btnFavorite.setImageResource(
                if (isFavorite) R.drawable.ic_favorite_filled
                else R.drawable.ic_favorite_outline
            )

            // Show/hide add to playlist button
            btnAddToPlaylist?.visibility = if (showAddToPlaylist) View.VISIBLE else View.GONE

            if (isPlaying) {
                itemView.setBackgroundResource(R.color.music_track_playing_bg)
            } else {
                itemView.setBackgroundResource(android.R.color.transparent)
            }
        }
    }

    class TrackDiffCallback : DiffUtil.ItemCallback<MusicTrack>() {
        override fun areItemsTheSame(oldItem: MusicTrack, newItem: MusicTrack): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MusicTrack, newItem: MusicTrack): Boolean {
            return oldItem == newItem
        }
    }
}
