package com.Atom2Universe.app.radio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R

class RadioStationAdapter(
    private val primaryLabel: String,
    private val secondaryLabelProvider: (RadioStation) -> String,
    private val onPrimaryAction: (RadioStation) -> Unit,
    private val onSecondaryAction: (RadioStation) -> Unit,
    private val isFavoriteProvider: ((RadioStation) -> Boolean)? = null
) : RecyclerView.Adapter<RadioStationAdapter.StationViewHolder>() {

    // Liste immutable pour éviter les race conditions lors du binding
    private var stations: List<RadioStation> = emptyList()

    fun submitList(items: List<RadioStation>) {
        val oldList = stations
        val newList = items.toList() // Copie immutable

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = newList.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                return oldList[oldPos].id == newList[newPos].id
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                return oldList[oldPos] == newList[newPos]
            }
        })

        stations = newList
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_radio_station, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        // Accès sécurisé à la liste immutable
        if (position in stations.indices) {
            holder.bind(stations[position])
        }
    }

    override fun getItemCount(): Int = stations.size

    inner class StationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.radio_item_title)
        private val meta: TextView = view.findViewById(R.id.radio_item_meta)
        private val playButton: ImageButton = view.findViewById(R.id.radio_item_play)
        private val favoriteButton: ImageButton = view.findViewById(R.id.radio_item_favorite)

        fun bind(station: RadioStation) {
            title.text = station.name
            meta.text = formatRadioMeta(station)

            // Update favorite icon based on status
            val isFavorite = isFavoriteProvider?.invoke(station) ?: false
            favoriteButton.setImageResource(
                if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )

            playButton.setOnClickListener { onPrimaryAction(station) }
            favoriteButton.setOnClickListener { onSecondaryAction(station) }
        }
    }

    private fun formatRadioMeta(station: RadioStation): String {
        val parts = mutableListOf<String>()
        if (station.country.isNotBlank() && station.language.isNotBlank()) {
            parts.add("${station.country} · ${station.language}")
        } else if (station.country.isNotBlank()) {
            parts.add(station.country)
        } else if (station.language.isNotBlank()) {
            parts.add(station.language)
        }
        station.bitrate?.let { parts.add("${it} kbps") }
        return parts.joinToString(" · ")
    }
}
