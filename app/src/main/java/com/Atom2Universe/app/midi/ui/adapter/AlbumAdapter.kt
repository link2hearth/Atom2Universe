package com.Atom2Universe.app.midi.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.data.AlbumItem

/**
 * Adapter pour afficher la liste des albums
 */
class AlbumAdapter(
    private val onAlbumClick: (AlbumItem) -> Unit
) : ListAdapter<AlbumItem, AlbumAdapter.AlbumViewHolder>(AlbumDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album, parent, false)
        return AlbumViewHolder(view, onAlbumClick)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AlbumViewHolder(
        itemView: View,
        private val onAlbumClick: (AlbumItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val albumName: TextView = itemView.findViewById(R.id.album_name)
        private val albumTrackCount: TextView = itemView.findViewById(R.id.album_track_count)

        fun bind(album: AlbumItem) {
            // Nom de l'album
            albumName.text = album.album

            // Nombre de titres
            val trackText = if (album.trackCount == 1) "titre" else "titres"
            albumTrackCount.text = "${album.trackCount} $trackText"

            // Click listener
            itemView.setOnClickListener {
                onAlbumClick(album)
            }
        }
    }

    class AlbumDiffCallback : DiffUtil.ItemCallback<AlbumItem>() {
        override fun areItemsTheSame(oldItem: AlbumItem, newItem: AlbumItem): Boolean {
            return oldItem.artist == newItem.artist && oldItem.album == newItem.album
        }

        override fun areContentsTheSame(oldItem: AlbumItem, newItem: AlbumItem): Boolean {
            return oldItem == newItem
        }
    }
}
