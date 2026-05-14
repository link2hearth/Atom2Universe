package com.Atom2Universe.app.notes.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.NoteGroup
import com.Atom2Universe.app.notes.data.NoteWithTags
import com.Atom2Universe.app.notes.ui.adapter.BoardItem
import com.Atom2Universe.app.notes.ui.adapter.GroupBoardAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GroupsFragment : Fragment() {

    private lateinit var notesActivity: NotesActivity
    private lateinit var boardAdapter: GroupBoardAdapter
    private lateinit var touchHelper: ItemTouchHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_groups, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        notesActivity = activity as NotesActivity

        boardAdapter = GroupBoardAdapter(
            onNoteClick = { notesActivity.navigateToNoteEditor(it.note.id) },
            onNoteLongClick = { showMoveToGroupDialog(it) },
            onGroupAdd = { groupId -> notesActivity.navigateToNoteEditor(groupId = groupId) },
            onGroupEdit = { group -> showEditGroupDialog(group) },
            onGroupColorChange = { group ->
                NoteColorPickerDialog(requireContext(), group.colorHex, group.textColorMode) { hex, mode ->
                    notesActivity.viewModel.updateGroupColor(group.id, hex, mode)
                }.show()
            },
            onGroupDelete = { group ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.notes_delete_group_title)
                    .setMessage(R.string.notes_delete_group_message)
                    .setPositiveButton(R.string.notes_delete) { _, _ ->
                        lifecycleScope.launch { notesActivity.viewModel.deleteGroup(group) }
                    }
                    .setNegativeButton(R.string.notes_cancel, null)
                    .show()
            },
            onDragStart = { holder -> touchHelper.startDrag(holder) }
        )

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                return if (boardAdapter.getItem(viewHolder.adapterPosition) is BoardItem.NoteCard)
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                else
                    makeMovementFlags(0, 0)
            }

            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                if (boardAdapter.getItem(to.adapterPosition) is BoardItem.GroupHeader) return false
                boardAdapter.moveItem(from.adapterPosition, to.adapterPosition)
                return true
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val pos = viewHolder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val item = boardAdapter.getItem(pos) as? BoardItem.NoteCard ?: return
                val newGroupId = boardAdapter.resolveGroupForPosition(pos)
                if (newGroupId != item.note.note.groupId) {
                    notesActivity.viewModel.moveNoteToGroup(item.note.note.id, newGroupId)
                    val groupName = groupNameFor(newGroupId)
                    Snackbar.make(view, getString(R.string.notes_moved_to_group, groupName), Snackbar.LENGTH_SHORT).show()
                }
            }
        })

        val recycler = view.findViewById<RecyclerView>(R.id.groups_recycler)
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = boardAdapter
        touchHelper.attachToRecyclerView(recycler)

        view.findViewById<View>(R.id.btn_create_group).setOnClickListener { showCreateGroupDialog() }
        view.findViewById<View>(R.id.btn_sort_groups)?.visibility = View.GONE

        lifecycleScope.launch {
            combine(
                notesActivity.viewModel.allGroupsWithCount,
                notesActivity.viewModel.allNotesWithTags
            ) { groups, notes -> Pair(groups, notes) }.collectLatest { (groups, notes) ->
                val notesByGroup = mutableMapOf<Long?, MutableList<NoteWithTags>>()
                notesByGroup[null] = mutableListOf()
                groups.forEach { notesByGroup[it.group.id] = mutableListOf() }
                notes.forEach { notesByGroup.getOrPut(it.note.groupId) { mutableListOf() }.add(it) }

                val groupOrder: List<NoteGroup?> = groups.sortedBy { it.group.position }.map { it.group } + listOf(null)
                boardAdapter.submitBoard(groupOrder, notesByGroup)

                view.findViewById<View>(R.id.groups_empty_state).visibility =
                    if (groups.isEmpty() && notes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun groupNameFor(groupId: Long?): String {
        if (groupId == null) return getString(R.string.notes_board_ungrouped)
        return notesActivity.viewModel.allGroupsWithCount.value
            .find { it.group.id == groupId }?.group?.name
            ?: getString(R.string.notes_board_ungrouped)
    }

    private fun showMoveToGroupDialog(noteWithTags: NoteWithTags) {
        val groups = notesActivity.viewModel.allGroupsWithCount.value
        val names = (listOf(getString(R.string.notes_board_ungrouped)) +
                groups.map { it.group.name }).toTypedArray()
        val groupIds: List<Long?> = listOf(null) + groups.map { it.group.id }
        val currentIndex = groupIds.indexOf(noteWithTags.note.groupId).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_move_to_group_title)
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                notesActivity.viewModel.moveNoteToGroup(noteWithTags.note.id, groupIds[which])
                Snackbar.make(requireView(), getString(R.string.notes_moved_to_group, names[which]), Snackbar.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.notes_cancel, null)
            .show()
    }

    private fun showCreateGroupDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_group, null)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_create_group)
            .setView(dialogView)
            .setPositiveButton(R.string.notes_create) { _, _ ->
                val name = dialogView.findViewById<EditText>(R.id.group_name_input).text.toString().trim()
                if (name.isNotBlank()) lifecycleScope.launch { notesActivity.viewModel.createGroup(name) }
            }
            .setNegativeButton(R.string.notes_cancel, null)
            .show()
    }

    private fun showEditGroupDialog(group: NoteGroup) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_group, null)
        dialogView.findViewById<EditText>(R.id.group_name_input).setText(group.name)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_edit_group)
            .setView(dialogView)
            .setPositiveButton(R.string.notes_save) { _, _ ->
                val name = dialogView.findViewById<EditText>(R.id.group_name_input).text.toString().trim()
                if (name.isNotBlank()) lifecycleScope.launch { notesActivity.viewModel.updateGroup(group.copy(name = name)) }
            }
            .setNegativeButton(R.string.notes_cancel, null)
            .show()
    }
}
