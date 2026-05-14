package com.Atom2Universe.app.notes.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.NoteWithTags
import com.Atom2Universe.app.notes.ui.adapter.NoteAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GroupNotesFragment : Fragment() {

    private var groupId: Long = -1
    private var groupName: String = ""
    private lateinit var notesActivity: NotesActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong(ARG_GROUP_ID, -1) ?: -1
        groupName = arguments?.getString(ARG_GROUP_NAME, "") ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_group_notes, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        notesActivity = activity as NotesActivity
        view.findViewById<TextView>(R.id.group_notes_title)?.text = groupName

        val adapter = NoteAdapter(
            onClick = { notesActivity.navigateToNoteEditor(it.note.id) },
            onLongClick = { showMoveToGroupDialog(it); true }
        )
        view.findViewById<RecyclerView>(R.id.group_notes_recycler).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }

        view.findViewById<FloatingActionButton>(R.id.fab_create_note)?.setOnClickListener {
            notesActivity.navigateToNoteEditor(groupId = groupId)
        }

        lifecycleScope.launch {
            notesActivity.viewModel.getNotesByGroup(groupId).collectLatest { notes ->
                adapter.submitList(notes)
                view.findViewById<View>(R.id.group_notes_empty).visibility =
                    if (notes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
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

    companion object {
        private const val ARG_GROUP_ID = "group_id"
        private const val ARG_GROUP_NAME = "group_name"

        fun newInstance(groupId: Long, groupName: String) = GroupNotesFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_GROUP_ID, groupId)
                putString(ARG_GROUP_NAME, groupName)
            }
        }
    }
}
