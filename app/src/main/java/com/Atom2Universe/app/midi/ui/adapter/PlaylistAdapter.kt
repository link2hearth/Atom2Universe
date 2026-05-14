package com.Atom2Universe.app.midi.ui.adapter

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
import com.Atom2Universe.app.midi.data.Playlist
import com.Atom2Universe.app.midi.repository.PlaylistRepository

/**
 * Item de playlist avec informations supplémentaires pour l'affichage
 */
data class PlaylistDisplayItem(
    val playlist: Playlist,
    val trackCount: Int
) {
    val isFavorites: Boolean
        get() = playlist.name == PlaylistRepository.FAVORITES_PLAYLIST_NAME
}

/**
 * Adapter pour afficher la liste des playlists
 */
class PlaylistAdapter(
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onMenuClick: (Playlist, View) -> Unit
) : ListAdapter<PlaylistDisplayItem, PlaylistAdapter.PlaylistViewHolder>(PlaylistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view, onPlaylistClick, onMenuClick)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PlaylistViewHolder(
        itemView: View,
        private val onPlaylistClick: (Playlist) -> Unit,
        private val onMenuClick: (Playlist, View) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val iconView: ImageView = itemView.findViewById(R.id.playlist_icon)
        private val nameText: TextView = itemView.findViewById(R.id.playlist_name)
        private val trackCountText: TextView = itemView.findViewById(R.id.playlist_track_count)
        private val menuButton: ImageButton = itemView.findViewById(R.id.btn_menu)

        private var currentPlaylist: Playlist? = null

        init {
            itemView.setOnClickListener {
                currentPlaylist?.let { onPlaylistClick(it) }
            }

            menuButton.setOnClickListener {
                currentPlaylist?.let { playlist ->
                    onMenuClick(playlist, it)
                }
            }
        }

        fun bind(item: PlaylistDisplayItem) {
            currentPlaylist = item.playlist
            nameText.text = item.playlist.name

            val context = itemView.context
            trackCountText.text = context.resources.getQuantityString(
                R.plurals.midi_track_count,
                item.trackCount,
                item.trackCount
            )

            // Icône spéciale pour les favoris
            if (item.isFavorites) {
                iconView.setImageResource(R.drawable.ic_star_filled)
            } else {
                iconView.setImageResource(android.R.drawable.ic_menu_agenda)
            }
        }
    }

    private class PlaylistDiffCallback : DiffUtil.ItemCallback<PlaylistDisplayItem>() {
        override fun areItemsTheSame(oldItem: PlaylistDisplayItem, newItem: PlaylistDisplayItem): Boolean {
            return oldItem.playlist.id == newItem.playlist.id
        }

        override fun areContentsTheSame(oldItem: PlaylistDisplayItem, newItem: PlaylistDisplayItem): Boolean {
            return oldItem == newItem
        }
    }
}
