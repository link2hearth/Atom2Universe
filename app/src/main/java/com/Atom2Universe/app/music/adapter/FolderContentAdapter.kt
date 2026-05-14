package com.Atom2Universe.app.music.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.FolderDisplayMode
import com.Atom2Universe.app.music.model.Folder
import com.Atom2Universe.app.music.model.MusicTrack

/**
 * Item mixte pour la vue dossier : peut être un sous-dossier ou une piste audio directe.
 */
sealed class FolderContentItem {
    data class FolderItem(val folder: Folder) : FolderContentItem()
    data class TrackItem(val track: MusicTrack) : FolderContentItem()
}

/**
 * Adapter pour afficher un contenu de dossier mixte (sous-dossiers + pistes audio)
 * dans le même RecyclerView, comme un explorateur de fichiers classique.
 * Les dossiers sont affichés en premier, puis les pistes.
 */
class FolderContentAdapter(
    private val onFolderClick: (Folder) -> Unit,
    private val onFolderLongClick: ((Folder, View) -> Boolean)? = null,
    private val onFolderPlayClick: ((Folder) -> Unit)? = null,
    private val onFolderShuffleClick: ((Folder) -> Unit)? = null,
    private val onTrackClick: (MusicTrack) -> Unit,
    private val onTrackLongClick: ((MusicTrack, View) -> Boolean)? = null
) : ListAdapter<FolderContentItem, RecyclerView.ViewHolder>(FolderContentDiffCallback()) {

    var displayMode: FolderDisplayMode = FolderDisplayMode.LIST
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    private var currentlyPlayingId: Long = -1

    fun setCurrentlyPlaying(trackId: Long) {
        val oldId = currentlyPlayingId
        currentlyPlayingId = trackId
        currentList.forEachIndexed { index, item ->
            if (item is FolderContentItem.TrackItem &&
                (item.track.id == oldId || item.track.id == trackId)) {
                notifyItemChanged(index)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_FOLDER_LIST = 0
        private const val VIEW_TYPE_FOLDER_COMPACT = 1
        private const val VIEW_TYPE_TRACK_LIST = 2
        private const val VIEW_TYPE_TRACK_COMPACT = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FolderContentItem.FolderItem -> when (displayMode) {
                FolderDisplayMode.LIST -> VIEW_TYPE_FOLDER_LIST
                FolderDisplayMode.COMPACT -> VIEW_TYPE_FOLDER_COMPACT
            }
            is FolderContentItem.TrackItem -> when (displayMode) {
                FolderDisplayMode.LIST -> VIEW_TYPE_TRACK_LIST
                FolderDisplayMode.COMPACT -> VIEW_TYPE_TRACK_COMPACT
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_FOLDER_LIST -> FolderListViewHolder(
                inflater.inflate(R.layout.item_music_folder, parent, false)
            )
            VIEW_TYPE_FOLDER_COMPACT -> FolderCompactViewHolder(
                inflater.inflate(R.layout.item_music_folder_compact, parent, false)
            )
            VIEW_TYPE_TRACK_LIST -> TrackListViewHolder(
                inflater.inflate(R.layout.item_music_track, parent, false)
            )
            else -> TrackCompactViewHolder(
                inflater.inflate(R.layout.item_music_track_compact, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is FolderContentItem.FolderItem -> {
                val folder = item.folder
                when (holder) {
                    is FolderListViewHolder -> holder.bind(folder)
                    is FolderCompactViewHolder -> holder.bind(folder)
                }
                holder.itemView.setOnClickListener { onFolderClick(folder) }
                holder.itemView.setOnLongClickListener { view ->
                    onFolderLongClick?.invoke(folder, view) ?: false
                }
            }
            is FolderContentItem.TrackItem -> {
                val track = item.track
                val isPlaying = track.id == currentlyPlayingId
                when (holder) {
                    is TrackListViewHolder -> holder.bind(track, isPlaying)
                    is TrackCompactViewHolder -> holder.bind(track, isPlaying)
                }
                holder.itemView.setOnClickListener { onTrackClick(track) }
                holder.itemView.setOnLongClickListener { view ->
                    onTrackLongClick?.invoke(track, view) ?: false
                }
            }
        }
    }

    // ========== FOLDER VIEW HOLDERS ==========

    inner class FolderListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val folderIcon: ImageView = itemView.findViewById(R.id.folder_icon)
        private val folderName: TextView = itemView.findViewById(R.id.folder_name)
        private val folderInfo: TextView = itemView.findViewById(R.id.folder_info)
        private val btnPlay: ImageView = itemView.findViewById(R.id.btn_play)
        private val btnShuffle: ImageView = itemView.findViewById(R.id.btn_shuffle)

        fun bind(folder: Folder) {
            folderName.text = folder.name
            folderInfo.text = buildFolderInfoText(folder)
            folderIcon.setImageResource(R.drawable.ic_folder_open)
            folderIcon.imageTintList = ContextCompat.getColorStateList(
                itemView.context, R.color.music_text_primary
            )
            btnPlay.setOnClickListener { onFolderPlayClick?.invoke(folder) }
            btnShuffle.setOnClickListener { onFolderShuffleClick?.invoke(folder) }
        }
    }

    inner class FolderCompactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val folderName: TextView = itemView.findViewById(R.id.folder_name)
        private val folderInfo: TextView = itemView.findViewById(R.id.folder_info)

        fun bind(folder: Folder) {
            folderName.text = folder.name
            folderInfo.text = buildFolderInfoText(folder)
        }
    }

    private fun buildFolderInfoText(folder: Folder): String {
        val parts = mutableListOf<String>()
        if (folder.subfolderCount > 0) parts.add("${folder.subfolderCount} \uD83D\uDCC1")
        val trackCount = folder.totalTrackCount
        if (trackCount > 0) parts.add("$trackCount \u266B")
        return parts.joinToString(" • ")
    }

    // ========== TRACK VIEW HOLDERS ==========

    inner class TrackListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.track_title)
        private val artistText: TextView = itemView.findViewById(R.id.track_artist)
        private val durationText: TextView = itemView.findViewById(R.id.track_duration)
        private val playingIndicator: ImageView = itemView.findViewById(R.id.playing_indicator)
        private val trackNumberText: TextView = itemView.findViewById(R.id.track_number)
        private val btnFavorite: ImageButton = itemView.findViewById(R.id.btn_favorite)
        private val btnAddToPlaylist: ImageButton? = itemView.findViewById(R.id.btn_add_to_playlist)

        fun bind(track: MusicTrack, isPlaying: Boolean) {
            titleText.text = track.title
            artistText.text = track.artist
            durationText.text = track.durationFormatted
            playingIndicator.visibility = if (isPlaying) View.VISIBLE else View.GONE
            // Boutons non pertinents en vue dossier mixte
            btnFavorite.visibility = View.GONE
            btnAddToPlaylist?.visibility = View.GONE
            // Numéro de piste masqué en vue mixte (pas toujours significatif)
            trackNumberText.visibility = View.GONE
        }
    }

    inner class TrackCompactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.track_title)
        private val artistText: TextView = itemView.findViewById(R.id.track_artist)
        private val durationText: TextView = itemView.findViewById(R.id.track_duration)
        private val playingIndicator: ImageView = itemView.findViewById(R.id.playing_indicator)
        private val btnFavorite: ImageButton = itemView.findViewById(R.id.btn_favorite)
        private val btnAddToPlaylist: ImageButton? = itemView.findViewById(R.id.btn_add_to_playlist)

        fun bind(track: MusicTrack, isPlaying: Boolean) {
            titleText.text = track.title
            artistText.text = track.artist
            durationText.text = track.durationFormatted
            playingIndicator.visibility = if (isPlaying) View.VISIBLE else View.GONE
            btnFavorite.visibility = View.GONE
            btnAddToPlaylist?.visibility = View.GONE
        }
    }

    class FolderContentDiffCallback : DiffUtil.ItemCallback<FolderContentItem>() {
        override fun areItemsTheSame(oldItem: FolderContentItem, newItem: FolderContentItem): Boolean {
            return when {
                oldItem is FolderContentItem.FolderItem && newItem is FolderContentItem.FolderItem ->
                    oldItem.folder.path == newItem.folder.path
                oldItem is FolderContentItem.TrackItem && newItem is FolderContentItem.TrackItem ->
                    oldItem.track.id == newItem.track.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: FolderContentItem, newItem: FolderContentItem): Boolean {
            return when {
                oldItem is FolderContentItem.FolderItem && newItem is FolderContentItem.FolderItem ->
                    oldItem.folder.path == newItem.folder.path &&
                    oldItem.folder.name == newItem.folder.name &&
                    oldItem.folder.trackCount == newItem.folder.trackCount &&
                    oldItem.folder.subfolderCount == newItem.folder.subfolderCount
                oldItem is FolderContentItem.TrackItem && newItem is FolderContentItem.TrackItem ->
                    oldItem.track.id == newItem.track.id &&
                    oldItem.track.title == newItem.track.title
                else -> false
            }
        }
    }
}
