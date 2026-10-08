package com.Atom2Universe.app.music.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.model.Playlist

/**
 * Item de playlist avec le nombre de pistes
 */
data class PlaylistItem(
    val playlist: Playlist,
    val trackCount: Int
)

class MusicPlaylistAdapter(
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onPlaylistLongClick: ((Playlist, View) -> Boolean)? = null
) : ListAdapter<PlaylistItem, MusicPlaylistAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music_playlist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.playlist_icon)
        private val name: TextView = itemView.findViewById(R.id.playlist_name)
        private val count: TextView = itemView.findViewById(R.id.playlist_count)

        fun bind(item: PlaylistItem) {
            name.text = item.playlist.name

            val context = itemView.context
            count.text = context.resources.getQuantityString(
                R.plurals.music_track_count_plural,
                item.trackCount,
                item.trackCount
            )

            // Set icon based on playlist type
            when {
                item.playlist.iconResId != null -> {
                    icon.setImageResource(item.playlist.iconResId)
                }
                item.playlist.isSystemPlaylist -> {
                    // System playlists (Favorites) keep their icon
                    icon.setImageResource(android.R.drawable.btn_star_big_on)
                }
                else -> {
                    // Custom playlists use the playlist icon
                    icon.setImageResource(R.drawable.ic_playlist)
                }
            }

            itemView.setOnClickListener {
                onPlaylistClick(item.playlist)
            }

            // Long click only for non-system playlists
            if (!item.playlist.isSystemPlaylist && onPlaylistLongClick != null) {
                itemView.setOnLongClickListener { view ->
                    onPlaylistLongClick.invoke(item.playlist, view)
                }
            } else {
                itemView.setOnLongClickListener(null)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<PlaylistItem>() {
        override fun areItemsTheSame(oldItem: PlaylistItem, newItem: PlaylistItem): Boolean {
            return oldItem.playlist.id == newItem.playlist.id
        }

        override fun areContentsTheSame(oldItem: PlaylistItem, newItem: PlaylistItem): Boolean {
            return oldItem == newItem
        }
    }
}
