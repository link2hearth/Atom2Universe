package com.Atom2Universe.app.notes.ui.adapter

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.NoteGroup
import com.Atom2Universe.app.notes.data.NoteWithTags
import com.Atom2Universe.app.notes.ui.NoteColorPickerDialog
import java.text.SimpleDateFormat
import java.util.*

sealed class BoardItem {
    data class GroupHeader(val group: NoteGroup?, val count: Int) : BoardItem()
    data class NoteCard(val note: NoteWithTags) : BoardItem()
}

class GroupBoardAdapter(
    private val onNoteClick: (NoteWithTags) -> Unit,
    private val onNoteLongClick: (NoteWithTags) -> Unit,
    private val onGroupAdd: (groupId: Long?) -> Unit,
    private val onGroupEdit: (group: NoteGroup) -> Unit,
    private val onGroupColorChange: (group: NoteGroup) -> Unit,
    private val onGroupDelete: (group: NoteGroup) -> Unit,
    private val onDragStart: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<BoardItem>()

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_NOTE = 1
    }

    fun submitBoard(groups: List<NoteGroup?>, notesByGroup: Map<Long?, List<NoteWithTags>>) {
        items.clear()
        groups.forEach { group ->
            val groupId = group?.id
            val notes = notesByGroup[groupId] ?: emptyList()
            items.add(BoardItem.GroupHeader(group, notes.size))
            notes.forEach { items.add(BoardItem.NoteCard(it)) }
        }
        notifyDataSetChanged()
    }

    fun getItem(position: Int): BoardItem = items[position]

    fun moveItem(from: Int, to: Int) {
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    fun resolveGroupForPosition(position: Int): Long? {
        for (i in position downTo 0) {
            val item = items[i]
            if (item is BoardItem.GroupHeader) return item.group?.id
        }
        return null
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = when (items[position]) {
        is BoardItem.GroupHeader -> TYPE_HEADER
        is BoardItem.NoteCard -> TYPE_NOTE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(inflater.inflate(R.layout.item_board_group_header, parent, false))
            else -> NoteVH(inflater.inflate(R.layout.item_note, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is BoardItem.GroupHeader -> (holder as HeaderVH).bind(item)
            is BoardItem.NoteCard -> (holder as NoteVH).bind(item.note)
        }
    }

    inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.board_group_name)
        private val count: TextView = view.findViewById(R.id.board_group_count)
        private val addBtn: ImageButton = view.findViewById(R.id.board_group_add)
        private val menuBtn: ImageButton = view.findViewById(R.id.board_group_menu)

        fun bind(item: BoardItem.GroupHeader) {
            name.text = item.group?.name ?: itemView.context.getString(R.string.notes_board_ungrouped)
            count.text = item.count.toString()
            addBtn.setOnClickListener { onGroupAdd(item.group?.id) }

            if (item.group == null) {
                menuBtn.visibility = View.GONE
            } else {
                menuBtn.visibility = View.VISIBLE
                menuBtn.setOnClickListener { v ->
                    PopupMenu(v.context, v).apply {
                        menu.add(0, 1, 0, v.context.getString(R.string.notes_group_edit))
                        menu.add(0, 2, 0, v.context.getString(R.string.notes_group_color))
                        menu.add(0, 3, 0, v.context.getString(R.string.notes_group_delete))
                        setOnMenuItemClickListener { menuItem ->
                            when (menuItem.itemId) {
                                1 -> { onGroupEdit(item.group); true }
                                2 -> { onGroupColorChange(item.group); true }
                                3 -> { onGroupDelete(item.group); true }
                                else -> false
                            }
                        }
                        show()
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class NoteVH(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view as MaterialCardView
        private val title: TextView = view.findViewById(R.id.note_title)
        private val preview: TextView = view.findViewById(R.id.note_preview)
        private val date: TextView = view.findViewById(R.id.note_date)
        private val pinIcon: ImageView = view.findViewById(R.id.pin_icon)
        private val favoriteIcon: ImageView = view.findViewById(R.id.note_favorite_icon)
        private val tagsGroup: ChipGroup = view.findViewById(R.id.note_tags_group)
        private val groupBadge: TextView = view.findViewById(R.id.note_group_badge)
        private val dragHandle: ImageView = view.findViewById(R.id.note_drag_handle)
        private val sdf = SimpleDateFormat("d MMM", Locale.getDefault())

        fun bind(item: NoteWithTags) {
            val note = item.note
            title.text = note.title.ifBlank { itemView.context.getString(R.string.notes_untitled) }
            preview.text = note.contentPlainText.take(100)
            date.text = sdf.format(Date(note.dateModified))
            pinIcon.visibility = if (note.isPinned) View.VISIBLE else View.GONE
            favoriteIcon.visibility = if (note.isFavorite) View.VISIBLE else View.GONE
            groupBadge.visibility = View.GONE

            val bgColor = try { note.colorHex?.let { Color.parseColor(it) } } catch (_: Exception) { null }
            if (bgColor != null) {
                card.setCardBackgroundColor(bgColor)
                val tc = NoteColorPickerDialog.calculateTextColor(note.colorHex)
                title.setTextColor(tc)
                preview.setTextColor(NoteColorPickerDialog.calculateSubtitleColor(note.colorHex))
                date.setTextColor(NoteColorPickerDialog.calculateSubtitleColor(note.colorHex))
            } else {
                card.setCardBackgroundColor(Color.parseColor("#1E293B"))
                title.setTextColor(Color.parseColor("#F1F5F9"))
                preview.setTextColor(Color.parseColor("#94A3B8"))
                date.setTextColor(Color.parseColor("#64748B"))
            }

            tagsGroup.removeAllViews()
            item.tags.take(3).forEach { tag ->
                val chip = Chip(itemView.context).apply {
                    text = tag.name
                    isClickable = false; isCheckable = false; textSize = 10f
                    tag.colorHex?.let { hex ->
                        try { chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.parseColor(hex)) } catch (_: Exception) {}
                    }
                }
                tagsGroup.addView(chip)
            }

            dragHandle.visibility = View.VISIBLE
            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onDragStart(this)
                }
                false
            }

            itemView.setOnClickListener { onNoteClick(item) }
            itemView.setOnLongClickListener { onNoteLongClick(item); true }
        }
    }
}
