package com.Atom2Universe.app.notes.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.NoteWithTags
import com.Atom2Universe.app.notes.ui.NoteColorPickerDialog
import java.text.SimpleDateFormat
import java.util.*

class NoteAdapter(
    private val onClick: (NoteWithTags) -> Unit,
    private val onLongClick: (NoteWithTags) -> Boolean = { false }
) : ListAdapter<NoteWithTags, NoteAdapter.NoteViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view as MaterialCardView
        private val title: TextView = view.findViewById(R.id.note_title)
        private val preview: TextView = view.findViewById(R.id.note_preview)
        private val date: TextView = view.findViewById(R.id.note_date)
        private val pinIcon: ImageView = view.findViewById(R.id.pin_icon)
        private val favoriteIcon: ImageView = view.findViewById(R.id.note_favorite_icon)
        private val tagsGroup: ChipGroup = view.findViewById(R.id.note_tags_group)
        private val groupBadge: TextView = view.findViewById(R.id.note_group_badge)
        private val sdf = SimpleDateFormat("d MMM", Locale.getDefault())

        fun bind(item: NoteWithTags) {
            val note = item.note
            title.text = note.title.ifBlank { itemView.context.getString(R.string.notes_untitled) }
            preview.text = note.contentPlainText.take(120)
            date.text = sdf.format(Date(note.dateModified))
            pinIcon.visibility = if (note.isPinned) View.VISIBLE else View.GONE
            favoriteIcon.visibility = if (note.isFavorite) View.VISIBLE else View.GONE

            // Background color
            val bgColor = try {
                note.colorHex?.let { Color.parseColor(it) }
            } catch (_: Exception) { null }
            if (bgColor != null) {
                card.setCardBackgroundColor(bgColor)
                val textColor = NoteColorPickerDialog.calculateTextColor(note.colorHex)
                title.setTextColor(textColor)
                preview.setTextColor(NoteColorPickerDialog.calculateSubtitleColor(note.colorHex))
                date.setTextColor(NoteColorPickerDialog.calculateSubtitleColor(note.colorHex))
            } else {
                card.setCardBackgroundColor(Color.parseColor("#1E293B"))
                title.setTextColor(Color.parseColor("#F1F5F9"))
                preview.setTextColor(Color.parseColor("#94A3B8"))
                date.setTextColor(Color.parseColor("#64748B"))
            }

            // Group badge
            item.group?.let {
                groupBadge.visibility = View.VISIBLE
                groupBadge.text = it.name
            } ?: run { groupBadge.visibility = View.GONE }

            // Tags chips (max 3)
            tagsGroup.removeAllViews()
            item.tags.take(3).forEach { tag ->
                val chip = Chip(itemView.context).apply {
                    text = tag.name
                    isClickable = false
                    isCheckable = false
                    textSize = 10f
                    tag.colorHex?.let { hex ->
                        try { chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.parseColor(hex)) } catch (_: Exception) {}
                    }
                }
                tagsGroup.addView(chip)
            }
            if (item.tags.size > 3) {
                val more = Chip(itemView.context).apply {
                    text = "+${item.tags.size - 3}"
                    isClickable = false; isCheckable = false; textSize = 10f
                }
                tagsGroup.addView(more)
            }

            itemView.setOnClickListener { onClick(item) }
            itemView.setOnLongClickListener { onLongClick(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<NoteWithTags>() {
            override fun areItemsTheSame(a: NoteWithTags, b: NoteWithTags) = a.note.id == b.note.id
            override fun areContentsTheSame(a: NoteWithTags, b: NoteWithTags) = a == b
        }
    }
}
