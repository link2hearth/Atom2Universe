package com.Atom2Universe.app.notes.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.NoteWithTags
import com.Atom2Universe.app.notes.export.NotesBackupManager
import com.Atom2Universe.app.notes.ui.adapter.NoteAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotesHomeFragment : Fragment() {

    private enum class SortMode {
        DATE_DESC, DATE_ASC, ALPHA_ASC, ALPHA_DESC;
        fun next() = values()[(ordinal + 1) % values().size]
        fun label(res: android.content.res.Resources) = when (this) {
            DATE_DESC -> res.getString(R.string.notes_sort_date_desc)
            DATE_ASC  -> res.getString(R.string.notes_sort_date_asc)
            ALPHA_ASC -> res.getString(R.string.notes_sort_alpha_asc)
            ALPHA_DESC -> res.getString(R.string.notes_sort_alpha_desc)
        }
    }

    private lateinit var notesActivity: NotesActivity
    private lateinit var adapter: NoteAdapter
    private lateinit var backupManager: NotesBackupManager
    private var sortMode = SortMode.DATE_DESC
    private var allNotes: List<NoteWithTags> = emptyList()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { exportBackup(it) }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { askImportMode(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_notes_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        notesActivity = activity as NotesActivity
        backupManager = NotesBackupManager(requireContext(), notesActivity.viewModel.repositoryRef)

        adapter = NoteAdapter(
            onClick = { notesActivity.navigateToNoteEditor(it.note.id) },
            onLongClick = { showNoteOptionsDialog(it); true }
        )
        view.findViewById<RecyclerView>(R.id.notes_recent_list).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@NotesHomeFragment.adapter
        }

        view.findViewById<View>(R.id.btn_create_note).setOnClickListener {
            notesActivity.navigateToNoteEditor()
        }

        view.findViewById<ImageButton>(R.id.btn_sort_notes).setOnClickListener { btn ->
            sortMode = sortMode.next()
            (btn as? ImageButton)?.contentDescription = sortMode.label(resources)
            Snackbar.make(view, sortMode.label(resources), Snackbar.LENGTH_SHORT).show()
            applySort()
        }

        view.findViewById<View>(R.id.backup_options_btn).setOnClickListener { showBackupMenu() }

        lifecycleScope.launch {
            notesActivity.viewModel.allNotesWithTags.collectLatest { notes ->
                allNotes = notes
                applySort()
                view.findViewById<TextView>(R.id.notes_empty_text).visibility =
                    if (notes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun applySort() {
        val sorted = when (sortMode) {
            SortMode.DATE_DESC  -> allNotes.sortedByDescending { it.note.dateModified }
            SortMode.DATE_ASC   -> allNotes.sortedBy { it.note.dateModified }
            SortMode.ALPHA_ASC  -> allNotes.sortedBy { it.note.title.lowercase() }
            SortMode.ALPHA_DESC -> allNotes.sortedByDescending { it.note.title.lowercase() }
        }
        adapter.submitList(sorted)
    }

    private fun showNoteOptionsDialog(noteWithTags: NoteWithTags) {
        val groups = notesActivity.viewModel.allGroupsWithCount.value
        val groupNames = (listOf(getString(R.string.notes_board_ungrouped)) +
                groups.map { it.group.name }).toTypedArray()
        val groupIds: List<Long?> = listOf(null) + groups.map { it.group.id }
        val currentIndex = groupIds.indexOf(noteWithTags.note.groupId).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(noteWithTags.note.title.ifBlank { getString(R.string.notes_untitled) })
            .setItems(arrayOf(getString(R.string.notes_move_to_group_title))) { _, _ ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.notes_move_to_group_title)
                    .setSingleChoiceItems(groupNames, currentIndex) { dialog, which ->
                        notesActivity.viewModel.moveNoteToGroup(noteWithTags.note.id, groupIds[which])
                        Snackbar.make(requireView(), getString(R.string.notes_moved_to_group, groupNames[which]), Snackbar.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.notes_cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.notes_cancel, null)
            .show()
    }

    private fun showBackupMenu() {
        val options = arrayOf(getString(R.string.notes_export_backup), getString(R.string.notes_import_backup))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_backup_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchExportPicker()
                    1 -> launchImportPicker()
                }
            }
            .show()
    }

    private fun launchExportPicker() {
        exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "a2u_notes_backup.zip")
        })
    }

    private fun launchImportPicker() {
        importLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        })
    }

    private fun exportBackup(uri: Uri) {
        lifecycleScope.launch {
            val result = backupManager.exportFullBackup(uri)
            val msg = when (result) {
                is NotesBackupManager.BackupResult.Success -> result.message
                is NotesBackupManager.BackupResult.Error -> result.message
            }
            Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun askImportMode(uri: Uri) {
        val modes = arrayOf(getString(R.string.notes_import_merge), getString(R.string.notes_import_replace))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_import_mode_title)
            .setItems(modes) { _, which ->
                val mode = if (which == 1) NotesBackupManager.ImportMode.REPLACE else NotesBackupManager.ImportMode.MERGE
                importBackup(uri, mode)
            }
            .show()
    }

    private fun importBackup(uri: Uri, mode: NotesBackupManager.ImportMode) {
        lifecycleScope.launch {
            val result = backupManager.importFullBackup(uri, mode)
            val msg = when (result) {
                is NotesBackupManager.BackupResult.Success -> result.message
                is NotesBackupManager.BackupResult.Error -> result.message
            }
            Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show()
        }
    }
}
