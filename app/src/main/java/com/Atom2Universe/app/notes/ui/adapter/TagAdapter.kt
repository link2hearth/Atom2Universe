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
import com.Atom2Universe.app.notes.data.TagWithCount

class TagAdapter(
    private val onClick: (TagWithCount) -> Unit,
    private val onEdit: (TagWithCount) -> Unit,
    private val onDelete: (TagWithCount) -> Unit
) : ListAdapter<TagWithCount, TagAdapter.TagViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TagViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_tag, parent, false)
    )

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) = holder.bind(getItem(position))

    inner class TagViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view as MaterialCardView
        private val tagName: TextView = view.findViewById(R.id.tag_name)
        private val usageCount: TextView = view.findViewById(R.id.tag_usage_count)
        private val menuBtn: ImageButton = view.findViewById(R.id.tag_menu_btn)

        fun bind(item: TagWithCount) {
            tagName.text = item.tag.name
            usageCount.text = item.usageCount.toString()
            item.tag.colorHex?.let { hex ->
                try { card.setCardBackgroundColor(Color.parseColor(hex)) } catch (_: Exception) {}
            }
            itemView.setOnClickListener { onClick(item) }
            menuBtn.setOnClickListener { v ->
                PopupMenu(v.context, v).apply {
                    inflate(R.menu.notes_tag_menu)
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.action_edit_tag -> { onEdit(item); true }
                            R.id.action_delete_tag -> { onDelete(item); true }
                            else -> false
                        }
                    }
                    show()
                }
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TagWithCount>() {
            override fun areItemsTheSame(a: TagWithCount, b: TagWithCount) = a.tag.id == b.tag.id
            override fun areContentsTheSame(a: TagWithCount, b: TagWithCount) = a == b
        }
    }
}
