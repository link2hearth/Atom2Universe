package com.Atom2Universe.app.sf2creator.ui

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.sf2creator.data.SampleData
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity

/**
 * Adapter for displaying samples within a project.
 */
class ProjectSampleAdapter(
    private val onDeleteClick: (Sf2SampleEntity) -> Unit,
    private val onItemClick: ((Sf2SampleEntity) -> Unit)? = null,
    private val onLongClick: ((Sf2SampleEntity) -> Unit)? = null
) : ListAdapter<Sf2SampleEntity, ProjectSampleAdapter.SampleViewHolder>(SampleDiffCallback()) {

    // Currently selected sample ID for visual highlight
    private var selectedSampleId: Long = -1

    /**
     * Set the selected sample ID and refresh the display.
     */
    fun setSelectedSample(sampleId: Long) {
        val previousSelected = selectedSampleId
        selectedSampleId = sampleId

        // Refresh only changed items for efficiency
        currentList.forEachIndexed { index, sample ->
            if (sample.id == previousSelected || sample.id == sampleId) {
                notifyItemChanged(index)
            }
        }
    }

    /**
     * Get the currently selected sample ID.
     */
    fun getSelectedSampleId(): Long = selectedSampleId

    /**
     * Clear selection.
     */
    fun clearSelection() {
        setSelectedSample(-1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SampleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_project_sample, parent, false)
        return SampleViewHolder(view)
    }

    override fun onBindViewHolder(holder: SampleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SampleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val rootNoteBadge: TextView = itemView.findViewById(R.id.root_note_badge)
        private val sampleName: TextView = itemView.findViewById(R.id.sample_name)
        private val keyRangeText: TextView = itemView.findViewById(R.id.key_range_text)
        private val loopIndicator: TextView = itemView.findViewById(R.id.loop_indicator)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)

        fun bind(sample: Sf2SampleEntity) {
            val noteName = SampleData.midiNoteToName(sample.rootNote)
            rootNoteBadge.text = noteName
            sampleName.text = sample.name

            // Display key range info
            if (sample.keyRangeStart == sample.keyRangeEnd) {
                // Single note
                keyRangeText.text = itemView.context.getString(R.string.sf2_note_info, noteName)
            } else {
                // Key range
                val startName = SampleData.midiNoteToName(sample.keyRangeStart)
                val endName = SampleData.midiNoteToName(sample.keyRangeEnd)
                keyRangeText.text = itemView.context.getString(R.string.sf2_key_range_label, startName, endName)
            }

            // Show loop indicator if sample has loop
            loopIndicator.visibility = if (sample.hasLoop) View.VISIBLE else View.GONE

            // Apply selection highlight
            val isSelected = sample.id == selectedSampleId
            if (isSelected) {
                val accentColor = getThemeColor(R.attr.a2uMidiAccent)
                cardView.strokeColor = accentColor
                cardView.strokeWidth = 4
            } else {
                cardView.strokeWidth = 0
            }

            deleteButton.setOnClickListener {
                onDeleteClick(sample)
            }

            // Item click to select sample
            itemView.setOnClickListener {
                onItemClick?.invoke(sample)
            }

            // Long click to edit sample
            itemView.setOnLongClickListener {
                onLongClick?.invoke(sample)
                true
            }
        }

        private fun getThemeColor(attrResId: Int): Int {
            val typedValue = TypedValue()
            itemView.context.theme.resolveAttribute(attrResId, typedValue, true)
            return typedValue.data
        }
    }

    private class SampleDiffCallback : DiffUtil.ItemCallback<Sf2SampleEntity>() {
        override fun areItemsTheSame(oldItem: Sf2SampleEntity, newItem: Sf2SampleEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Sf2SampleEntity, newItem: Sf2SampleEntity): Boolean {
            return oldItem == newItem
        }
    }
}
