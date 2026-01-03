package com.example.atom2univers.radio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.atom2univers.R

class RadioStationAdapter(
    private val primaryLabel: String,
    private val secondaryLabelProvider: (RadioStation) -> String,
    private val onPrimaryAction: (RadioStation) -> Unit,
    private val onSecondaryAction: (RadioStation) -> Unit
) : RecyclerView.Adapter<RadioStationAdapter.StationViewHolder>() {

    private val stations = mutableListOf<RadioStation>()

    fun submitList(items: List<RadioStation>) {
        stations.clear()
        stations.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_radio_station, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        holder.bind(stations[position])
    }

    override fun getItemCount(): Int = stations.size

    inner class StationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.radio_item_title)
        private val meta: TextView = view.findViewById(R.id.radio_item_meta)
        private val primaryButton: Button = view.findViewById(R.id.radio_item_primary)
        private val secondaryButton: Button = view.findViewById(R.id.radio_item_secondary)

        fun bind(station: RadioStation) {
            title.text = station.name
            meta.text = formatRadioMeta(station)
            primaryButton.text = primaryLabel
            secondaryButton.text = secondaryLabelProvider(station)
            primaryButton.setOnClickListener { onPrimaryAction(station) }
            secondaryButton.setOnClickListener { onSecondaryAction(station) }
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
