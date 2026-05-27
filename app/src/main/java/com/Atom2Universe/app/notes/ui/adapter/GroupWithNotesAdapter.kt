package com.Atom2Universe.app.notes.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.GroupWithNotes
import com.Atom2Universe.app.notes.data.NoteGroup
import com.Atom2Universe.app.notes.data.NoteWithTags

class GroupWithNotesAdapter(
    private val onGroupClick: (NoteGroup) -> Unit,
    private val onNoteClick: (NoteWithTags) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ListItem>()

    sealed class ListItem {
        data class GroupHeader(val group: NoteGroup, val noteCount: Int) : ListItem()
        data class NoteItem(val note: NoteWithTags) : ListItem()
        data class EmptyMessage(val groupId: Long) : ListItem()
    }

    fun submitGroups(groups: List<GroupWithNotes>, notesByGroup: Map<Long, List<NoteWithTags>>) {
        items.clear()
        groups.forEach { gw ->
            items.add(ListItem.GroupHeader(gw.group, gw.notes.size))
            val notes = notesByGroup[gw.group.id] ?: emptyList()
            if (notes.isEmpty()) items.add(ListItem.EmptyMessage(gw.group.id))
            else notes.forEach { items.add(ListItem.NoteItem(it)) }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is ListItem.GroupHeader -> TYPE_GROUP
        is ListItem.NoteItem -> TYPE_NOTE
        is ListItem.EmptyMessage -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_GROUP -> GroupVH(inflater.inflate(R.layout.item_note_group, parent, false))
            TYPE_NOTE -> NoteVH(inflater.inflate(R.layout.item_note_compact, parent, false))
            else -> EmptyVH(inflater.inflate(R.layout.item_groups_empty_notes, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.GroupHeader -> (holder as GroupVH).bind(item)
            is ListItem.NoteItem -> (holder as NoteVH).bind(item.note)
            is ListItem.EmptyMessage -> {}
        }
    }

    override fun getItemCount() = items.size

    fun moveGroup(from: Int, to: Int) {
        val fromItem = items.removeAt(from)
        items.add(to, fromItem)
        notifyItemMoved(from, to)
    }

    fun getGroupsSortOrder(): List<Pair<Long, Int>> = items
        .filterIsInstance<ListItem.GroupHeader>()
        .mapIndexed { index, h -> Pair(h.group.id, index) }

    inner class GroupVH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(header: ListItem.GroupHeader) {
            itemView.findViewById<TextView>(R.id.group_name)?.text = header.group.name
            itemView.setOnClickListener { onGroupClick(header.group) }
        }
    }

    inner class NoteVH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(note: NoteWithTags) {
            itemView.findViewById<TextView>(R.id.note_title)?.text = note.note.title
            itemView.setOnClickListener { onNoteClick(note) }
        }
    }

    class EmptyVH(view: View) : RecyclerView.ViewHolder(view)

    companion object {
        const val TYPE_GROUP = 0
        const val TYPE_NOTE = 1
        const val TYPE_EMPTY = 2
    }
}
