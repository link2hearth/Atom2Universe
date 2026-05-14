package com.Atom2Universe.app.music.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.MusicStatsManager
import com.Atom2Universe.app.music.model.MusicTrack
import java.util.Collections

class QueueTrackAdapter(
    private val onTrackClick: (MusicTrack, Int) -> Unit,
    private val onDragStarted: (RecyclerView.ViewHolder) -> Unit,
    private val onFavoriteClick: ((MusicTrack) -> Unit)? = null
) : RecyclerView.Adapter<QueueTrackAdapter.QueueViewHolder>() {

    private val tracks = mutableListOf<MusicTrack>()
    private var currentlyPlayingIndex: Int = -1
    private var favoriteIds: Set<Long> = emptySet()
    private var showPlayCount: Boolean = true

    /**
     * Met à jour la liste de tracks avec DiffUtil pour des animations optimisées.
     */
    fun setTracks(newTracks: List<MusicTrack>) {
        val diffCallback = QueueDiffCallback(tracks, newTracks)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        tracks.clear()
        tracks.addAll(newTracks)

        diffResult.dispatchUpdatesTo(this)
    }

    fun getTracks(): List<MusicTrack> = tracks.toList()

    fun setCurrentlyPlayingIndex(index: Int) {
        val oldIndex = currentlyPlayingIndex
        currentlyPlayingIndex = index
        if (oldIndex >= 0 && oldIndex < tracks.size) notifyItemChanged(oldIndex)
        if (index >= 0 && index < tracks.size) notifyItemChanged(index)
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
                    val position = tracks.indexOfFirst { it.id == id }
                    if (position >= 0) notifyItemChanged(position)
                }
            } else {
                // Beaucoup de changements : notifier tout
                if (itemCount > 0) {
                    notifyItemRangeChanged(0, itemCount)
                }
            }
        }
    }

    fun updateFavorite(trackId: Long, isFavorite: Boolean) {
        favoriteIds = if (isFavorite) {
            favoriteIds + trackId
        } else {
            favoriteIds - trackId
        }
        val position = tracks.indexOfFirst { it.id == trackId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    fun setShowPlayCount(show: Boolean) {
        if (showPlayCount != show) {
            showPlayCount = show
            if (itemCount > 0) {
                notifyItemRangeChanged(0, itemCount)
            }
        }
    }

    fun updatePlayCount(trackId: Long) {
        val position = tracks.indexOfFirst { it.id == trackId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(tracks, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(tracks, i, i - 1)
            }
        }

        // Update currently playing index if it was affected
        when {
            currentlyPlayingIndex == fromPosition -> currentlyPlayingIndex = toPosition
            fromPosition < currentlyPlayingIndex && toPosition >= currentlyPlayingIndex -> currentlyPlayingIndex--
            fromPosition > currentlyPlayingIndex && toPosition <= currentlyPlayingIndex -> currentlyPlayingIndex++
        }

        notifyItemMoved(fromPosition, toPosition)
    }

    fun removeItem(position: Int): MusicTrack? {
        if (position < 0 || position >= tracks.size) return null
        val removed = tracks.removeAt(position)

        // Update currently playing index if affected
        when {
            position < currentlyPlayingIndex -> currentlyPlayingIndex--
            position == currentlyPlayingIndex -> currentlyPlayingIndex = -1
        }

        notifyItemRemoved(position)
        // Update positions for items after removed one
        notifyItemRangeChanged(position, tracks.size - position)
        return removed
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue_track, parent, false)
        return QueueViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val track = tracks[position]
        val isPlaying = position == currentlyPlayingIndex
        val isFavorite = favoriteIds.contains(track.id)
        val playCount = if (showPlayCount) MusicStatsManager.getPlayCount(track) else 0L

        holder.bind(track, position, isPlaying, isFavorite, showPlayCount, playCount)

        holder.itemView.setOnClickListener {
            onTrackClick(track, holder.bindingAdapterPosition)
        }

        // Start drag when touching the handle
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onDragStarted(holder)
            }
            false
        }

        // Favorite button click
        holder.btnFavorite.setOnClickListener {
            onFavoriteClick?.invoke(track)
        }
    }

    override fun getItemCount(): Int = tracks.size

    class QueueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.track_title)
        private val artistText: TextView = itemView.findViewById(R.id.track_artist)
        private val durationText: TextView = itemView.findViewById(R.id.track_duration)
        private val positionText: TextView = itemView.findViewById(R.id.queue_position)
        private val playingIndicator: ImageView = itemView.findViewById(R.id.playing_indicator)
        private val playCountText: TextView = itemView.findViewById(R.id.track_play_count)
        val btnFavorite: ImageButton = itemView.findViewById(R.id.btn_favorite)
        val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)

        fun bind(track: MusicTrack, position: Int, isPlaying: Boolean, isFavorite: Boolean, showPlayCount: Boolean, playCount: Long) {
            val context = itemView.context
            titleText.text = track.title
            artistText.text = track.artist
            durationText.text = track.durationFormatted
            positionText.text = context.getString(R.string.music_queue_position_format, position + 1)

            // Play count
            if (showPlayCount && playCount > 0) {
                playCountText.text = context.getString(R.string.music_play_count_short_format, playCount)
                playCountText.visibility = View.VISIBLE
            } else {
                playCountText.visibility = View.GONE
            }

            // Favorite button
            btnFavorite.setImageResource(
                if (isFavorite) R.drawable.ic_favorite_filled
                else R.drawable.ic_favorite_outline
            )

            if (isPlaying) {
                playingIndicator.visibility = View.VISIBLE
                itemView.setBackgroundResource(R.color.music_track_playing_bg)
            } else {
                playingIndicator.visibility = View.INVISIBLE
                itemView.setBackgroundResource(android.R.color.transparent)
            }
        }
    }

    /**
     * DiffUtil callback pour optimiser les mises à jour de la liste.
     */
    private class QueueDiffCallback(
        private val oldList: List<MusicTrack>,
        private val newList: List<MusicTrack>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
