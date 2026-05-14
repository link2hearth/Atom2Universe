package com.Atom2Universe.app.notes.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.GroupWithCount

class NoteGroupAdapter(
    private val onClick: (GroupWithCount) -> Unit,
    private val onEdit: (GroupWithCount) -> Unit,
    private val onColorChange: (GroupWithCount) -> Unit,
    private val onDelete: (GroupWithCount) -> Unit
) : ListAdapter<GroupWithCount, NoteGroupAdapter.GroupViewHolder>(DIFF) {

    var selectedGroupId: Long? = null
        set(value) { field = value; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = GroupViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_note_group, parent, false)
    )

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view as MaterialCardView
        private val name: TextView = view.findViewById(R.id.group_name)
        private val description: TextView = view.findViewById(R.id.group_description)
        private val noteCount: TextView = view.findViewById(R.id.group_note_count)
        private val menuBtn: ImageButton = view.findViewById(R.id.group_menu_btn)

        fun bind(item: GroupWithCount) {
            name.text = item.group.name
            description.text = item.group.description
            description.visibility = if (item.group.description.isBlank()) View.GONE else View.VISIBLE
            noteCount.text = item.noteCount.toString()

            val isSelected = item.group.id == selectedGroupId
            card.strokeWidth = if (isSelected) 3 else 0

            item.group.colorHex?.let { hex ->
                try {
                    card.setCardBackgroundColor(Color.parseColor(hex))
                } catch (_: Exception) {}
            } ?: card.setCardBackgroundColor(Color.parseColor("#1E293B"))

            itemView.setOnClickListener { onClick(item) }
            menuBtn.setOnClickListener { v ->
                PopupMenu(v.context, v).apply {
                    inflate(R.menu.notes_group_menu)
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.action_edit_group -> { onEdit(item); true }
                            R.id.action_group_color -> { onColorChange(item); true }
                            R.id.action_delete_group -> { onDelete(item); true }
                            else -> false
                        }
                    }
                    show()
                }
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<GroupWithCount>() {
            override fun areItemsTheSame(a: GroupWithCount, b: GroupWithCount) = a.group.id == b.group.id
            override fun areContentsTheSame(a: GroupWithCount, b: GroupWithCount) = a == b
        }
    }
}
