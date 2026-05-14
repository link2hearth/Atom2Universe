package com.Atom2Universe.app.midi.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.data.ArtistItem

/**
 * Adapter pour afficher la liste des artistes
 */
class ArtistAdapter(
    private val onArtistClick: (ArtistItem) -> Unit
) : ListAdapter<ArtistItem, ArtistAdapter.ArtistViewHolder>(ArtistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_artist, parent, false)
        return ArtistViewHolder(view, onArtistClick)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ArtistViewHolder(
        itemView: View,
        private val onArtistClick: (ArtistItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val artistInitial: TextView = itemView.findViewById(R.id.artist_initial)
        private val artistName: TextView = itemView.findViewById(R.id.artist_name)
        private val artistStats: TextView = itemView.findViewById(R.id.artist_stats)

        fun bind(artist: ArtistItem) {
            // Initiale de l'artiste
            artistInitial.text = artist.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

            // Nom de l'artiste
            artistName.text = artist.name

            // Statistiques
            val albumText = if (artist.albumCount == 1) "album" else "albums"
            val trackText = if (artist.trackCount == 1) "titre" else "titres"
            artistStats.text = "${artist.albumCount} $albumText · ${artist.trackCount} $trackText"

            // Click listener
            itemView.setOnClickListener {
                onArtistClick(artist)
            }
        }
    }

    class ArtistDiffCallback : DiffUtil.ItemCallback<ArtistItem>() {
        override fun areItemsTheSame(oldItem: ArtistItem, newItem: ArtistItem): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: ArtistItem, newItem: ArtistItem): Boolean {
            return oldItem == newItem
        }
    }
}
