package com.Atom2Universe.app.notes.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.TagCategory
import com.Atom2Universe.app.notes.data.TagWithCount

class ExpandableTagAdapter(
    private val onTagClick: (TagWithCount) -> Unit,
    private val onTagEdit: (TagWithCount) -> Unit,
    private val onTagDelete: (TagWithCount) -> Unit,
    private val onCategoryEdit: (TagCategory) -> Unit,
    private val onCategoryDelete: (TagCategory) -> Unit,
    private val onTagDroppedOnNote: ((tagId: Long, noteId: Long) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ListItem>()
    var expandedTagId: Long? = null

    sealed class ListItem {
        data class CategoryHeader(val category: TagCategory?) : ListItem()
        data class TagItem(val tagWithCount: TagWithCount, val categoryId: Long?) : ListItem()
    }

    fun submitData(categories: List<TagCategory?>, tagsByCategory: Map<Long?, List<TagWithCount>>) {
        items.clear()
        categories.forEach { cat ->
            items.add(ListItem.CategoryHeader(cat))
            val tags = tagsByCategory[cat?.id] ?: emptyList()
            tags.forEach { items.add(ListItem.TagItem(it, cat?.id)) }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ListItem.CategoryHeader -> TYPE_CATEGORY
        is ListItem.TagItem -> TYPE_TAG
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CATEGORY -> CategoryVH(inflater.inflate(R.layout.item_tag_category_header, parent, false))
            else -> TagVH(inflater.inflate(R.layout.item_tag_chip, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.CategoryHeader -> (holder as CategoryVH).bind(item)
            is ListItem.TagItem -> (holder as TagVH).bind(item)
        }
    }

    override fun getItemCount() = items.size

    fun moveTag(from: Int, to: Int) {
        val fromItem = items.removeAt(from)
        items.add(to, fromItem)
        notifyItemMoved(from, to)
    }

    fun moveCategory(from: Int, to: Int) {
        val fromItem = items.removeAt(from)
        items.add(to, fromItem)
        notifyItemMoved(from, to)
    }

    fun getTagsSortOrder(): List<Pair<Long, Int>> = items
        .filterIsInstance<ListItem.TagItem>()
        .mapIndexed { i, t -> Pair(t.tagWithCount.tag.id, i) }

    fun getCategoriesSortOrder(): List<Pair<Long, Int>> = items
        .filterIsInstance<ListItem.CategoryHeader>()
        .filter { it.category != null }
        .mapIndexed { i, c -> Pair(c.category!!.id, i) }

    fun isTagAlreadyOnNote(tagId: Long, noteId: Long): Boolean = false

    fun expandTag(tagId: Long) {
        expandedTagId = if (expandedTagId == tagId) null else tagId
        notifyDataSetChanged()
    }

    inner class CategoryVH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: ListItem.CategoryHeader) {
            val cat = item.category
            itemView.findViewById<TextView>(R.id.category_title)?.text = cat?.name ?: itemView.context.getString(R.string.notes_uncategorized)
            val menuBtn = itemView.findViewById<ImageButton>(R.id.category_menu_btn)
            if (cat != null) {
                menuBtn?.visibility = View.VISIBLE
                menuBtn?.setOnClickListener { v ->
                    PopupMenu(v.context, v).apply {
                        inflate(R.menu.menu_tag_category)
                        setOnMenuItemClickListener { menuItem ->
                            when (menuItem.itemId) {
                                R.id.action_edit_category -> { onCategoryEdit(cat); true }
                                R.id.action_delete_category -> { onCategoryDelete(cat); true }
                                else -> false
                            }
                        }
                        show()
                    }
                }
            } else {
                menuBtn?.visibility = View.GONE
            }
        }
    }

    inner class TagVH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: ListItem.TagItem) {
            val tag = item.tagWithCount.tag
            itemView.findViewById<TextView>(R.id.tag_name)?.text = tag.name
            itemView.findViewById<TextView>(R.id.tag_count)?.text = item.tagWithCount.usageCount.toString()
            tag.colorHex?.let { hex ->
                try {
                    (itemView as? MaterialCardView)?.setCardBackgroundColor(Color.parseColor(hex))
                } catch (_: Exception) {}
            }
            val expandIcon = itemView.findViewById<ImageView>(R.id.tag_expand_icon)
            expandIcon?.setImageResource(
                if (expandedTagId == tag.id) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
            val editIcon = itemView.findViewById<ImageView>(R.id.tag_edit_icon)
            editIcon?.setOnClickListener { onTagEdit(item.tagWithCount) }
            itemView.setOnClickListener { onTagClick(item.tagWithCount) }
            itemView.setOnLongClickListener {
                onTagEdit(item.tagWithCount)
                true
            }
        }
    }

    companion object {
        const val TYPE_CATEGORY = 0
        const val TYPE_TAG = 1
        const val TYPE_NOTE = 2
    }
}
