package com.Atom2Universe.app.notes.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.NotesPreferences
import com.Atom2Universe.app.notes.data.GroupWithCount
import com.Atom2Universe.app.notes.data.NoteGroup
import com.Atom2Universe.app.notes.data.NoteWithTags
import com.Atom2Universe.app.notes.data.TagWithCount
import com.Atom2Universe.app.notes.export.NoteExportManager
import com.Atom2Universe.app.notes.export.NotesBackupManager
import com.Atom2Universe.app.notes.viewmodel.NotesViewModel
import com.Atom2Universe.app.notes.viewmodel.NotesViewModel.LibraryFilter
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.chip
import com.Atom2Universe.app.pixelart.ui.confirm
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * La bibliothèque : toutes les notes en grille, les épinglées en tête. En haut, la recherche et
 * une rangée de filtres (toutes, favoris, un tag, chaque groupe). Un appui long sur une carte
 * ouvre ses actions.
 */
class NotesLibraryFragment : Fragment(R.layout.fragment_notes_library) {

    private val notesActivity get() = activity as NotesActivity
    private val vm: NotesViewModel get() = notesActivity.viewModel
    private lateinit var prefs: NotesPreferences
    private lateinit var adapter: NoteCardsAdapter
    private lateinit var searchField: EditText
    private lateinit var title: TextView
    private lateinit var filtersRow: LinearLayout
    private lateinit var emptyView: View
    private lateinit var emptyText: TextView
    private lateinit var list: RecyclerView

    private var query = ""
    private var notes: List<NoteWithTags> = emptyList()
    private var groups: List<GroupWithCount> = emptyList()
    private var tags: List<TagWithCount> = emptyList()

    private val searchBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = closeSearch()
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.data?.let { exportBackup(it) }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.data?.let { askImportMode(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = NotesPreferences(requireContext())
        searchField = view.findViewById(R.id.nt_lib_search)
        title = view.findViewById(R.id.nt_lib_title)
        filtersRow = view.findViewById(R.id.nt_lib_filters)
        emptyView = view.findViewById(R.id.nt_lib_empty)
        emptyText = view.findViewById(R.id.nt_lib_empty_text)
        list = view.findViewById(R.id.nt_lib_list)

        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        list.layoutManager = StaggeredGridLayoutManager((widthDp / 180f).toInt().coerceAtLeast(2), StaggeredGridLayoutManager.VERTICAL)
        adapter = NoteCardsAdapter(
            requireContext(),
            showGroup = { vm.filter.value !is LibraryFilter.Group },
            onOpen = { notesActivity.openNote(it.note.id) },
            onMenu = { showNoteMenu(it) },
        )
        list.adapter = adapter

        view.findViewById<View>(R.id.nt_lib_back).setOnClickListener {
            if (searchBack.isEnabled) closeSearch() else requireActivity().finish()
        }
        view.findViewById<View>(R.id.nt_lib_fab).setOnClickListener { newNote() }
        view.findViewById<ImageButton>(R.id.nt_lib_btn_search).setOnClickListener {
            if (searchBack.isEnabled) closeSearch() else openSearch()
        }
        view.findViewById<View>(R.id.nt_lib_btn_more).setOnClickListener { showLibraryMenu() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, searchBack)

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                render()
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.allNotesWithTags, vm.allGroupsWithCount, vm.allTagsWithCount, vm.filter) { n, g, t, _ -> Triple(n, g, t) }
                    .collect { (n, g, t) ->
                        notes = n; groups = g; tags = t
                        // Un filtre sur un groupe ou un tag disparu retombe sur « toutes ».
                        when (val f = vm.filter.value) {
                            is LibraryFilter.Group -> if (g.none { it.group.id == f.id }) vm.filter.value = LibraryFilter.All
                            is LibraryFilter.TagFilter -> if (t.none { it.tag.id == f.id }) vm.filter.value = LibraryFilter.All
                            else -> Unit
                        }
                        buildFilters()
                        render()
                    }
            }
        }
    }

    // ---- Recherche -----------------------------------------------------------------------------

    private fun openSearch() {
        searchBack.isEnabled = true
        title.visibility = View.GONE
        searchField.visibility = View.VISIBLE
        searchField.requestFocus()
        imm()?.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT)
        view?.findViewById<ImageButton>(R.id.nt_lib_btn_search)?.setImageResource(R.drawable.ic_px_close)
    }

    private fun closeSearch() {
        searchBack.isEnabled = false
        searchField.setText("")
        imm()?.hideSoftInputFromWindow(searchField.windowToken, 0)
        searchField.visibility = View.GONE
        title.visibility = View.VISIBLE
        view?.findViewById<ImageButton>(R.id.nt_lib_btn_search)?.setImageResource(R.drawable.ic_nt_search)
    }

    private fun imm() = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    /** Chaque mot doit se trouver dans le titre, le texte ou un tag ; `#mot` ne cherche que les tags. */
    private fun matches(n: NoteWithTags, words: List<String>): Boolean = words.all { w ->
        if (w.startsWith("#") && w.length > 1) n.tags.any { it.name.lowercase().contains(w.substring(1)) }
        else n.note.title.lowercase().contains(w) || n.note.contentPlainText.lowercase().contains(w) ||
            n.tags.any { it.name.lowercase().contains(w) }
    }

    // ---- Filtres -------------------------------------------------------------------------------

    private fun buildFilters() {
        val ctx = requireContext()
        val f = vm.filter.value
        filtersRow.removeAllViews()
        filtersRow.addView(ctx.chip(getString(R.string.notes_filter_all)) { vm.filter.value = LibraryFilter.All }
            .apply { isSelected = f is LibraryFilter.All })
        filtersRow.addView(ctx.chip(getString(R.string.notes_filter_favorites), R.drawable.ic_nt_star) { vm.filter.value = LibraryFilter.Favorites }
            .apply { isSelected = f is LibraryFilter.Favorites })
        if (f is LibraryFilter.TagFilter) tags.firstOrNull { it.tag.id == f.id }?.let { t ->
            filtersRow.addView(ctx.chip(getString(R.string.notes_tag_label, t.tag.name), R.drawable.ic_px_close) { vm.filter.value = LibraryFilter.All }
                .apply { isSelected = true; contentDescription = getString(R.string.notes_filter_clear_tag, t.tag.name) })
        }
        for (g in groups) {
            val c = ctx.chip(getString(R.string.notes_group_chip, g.group.name, g.noteCount), R.drawable.ic_px_folder_open) {
                vm.filter.value = if ((vm.filter.value as? LibraryFilter.Group)?.id == g.group.id) LibraryFilter.All else LibraryFilter.Group(g.group.id)
            }
            c.isSelected = f is LibraryFilter.Group && f.id == g.group.id
            c.setOnLongClickListener { showGroupMenu(g.group); true }
            filtersRow.addView(c)
        }
        filtersRow.addView(ctx.chip(null, R.drawable.ic_px_add) {
            ctx.askName(R.string.notes_new_group, "", R.string.notes_create) { name ->
                viewLifecycleOwner.lifecycleScope.launch { vm.filter.value = LibraryFilter.Group(vm.createGroup(name)) }
            }
        }.apply { contentDescription = getString(R.string.notes_new_group) })
    }

    private fun showGroupMenu(group: NoteGroup) {
        val ctx = requireContext()
        ctx.actionSheet(group.name, listOf(
            SheetItem(R.drawable.ic_px_rename, getString(R.string.notes_rename)) {
                ctx.askName(R.string.notes_rename_group, group.name, R.string.notes_save) { vm.renameGroup(group, it) }
            },
            SheetItem(R.drawable.ic_px_delete, getString(R.string.notes_delete_group), destructive = true) {
                ctx.confirm(R.string.notes_delete_group, getString(R.string.notes_delete_group_message, group.name), R.string.notes_delete, true) {
                    vm.deleteGroup(group)
                }
            },
        )).show()
    }

    // ---- Grille --------------------------------------------------------------------------------

    private fun render() {
        if (!::adapter.isInitialized) return
        val f = vm.filter.value
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val shown = notes.filter { n ->
            when (f) {
                LibraryFilter.All -> true
                LibraryFilter.Favorites -> n.note.isFavorite
                is LibraryFilter.Group -> n.note.groupId == f.id
                is LibraryFilter.TagFilter -> n.tags.any { it.id == f.id }
            } && (words.isEmpty() || matches(n, words))
        }.let { l ->
            when (prefs.sort) {
                NotesPreferences.Sort.MODIFIED -> l.sortedByDescending { it.note.dateModified }
                NotesPreferences.Sort.CREATED -> l.sortedByDescending { it.note.dateCreated }
                NotesPreferences.Sort.TITLE -> l.sortedBy { it.note.title.ifBlank { it.note.contentPlainText }.lowercase() }
            }
        }
        val (pinned, others) = shown.partition { it.note.isPinned }
        val rows = ArrayList<NoteRow>()
        if (pinned.isNotEmpty()) {
            rows += NoteRow.Header(getString(R.string.notes_section_pinned))
            pinned.mapTo(rows) { NoteRow.Card(it) }
            if (others.isNotEmpty()) rows += NoteRow.Header(getString(R.string.notes_section_others))
        }
        others.mapTo(rows) { NoteRow.Card(it) }
        adapter.submitList(rows)

        emptyView.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        emptyText.setText(when {
            words.isNotEmpty() -> R.string.notes_search_empty
            f is LibraryFilter.Favorites -> R.string.notes_favorites_empty
            f !is LibraryFilter.All -> R.string.notes_filter_empty
            else -> R.string.notes_empty
        })
    }

    private fun newNote() {
        val f = vm.filter.value
        notesActivity.openNote(
            null,
            groupId = (f as? LibraryFilter.Group)?.id,
            tagId = (f as? LibraryFilter.TagFilter)?.id,
            favorite = f is LibraryFilter.Favorites,
        )
    }

    // ---- Actions d'une note --------------------------------------------------------------------

    private fun showNoteMenu(item: NoteWithTags) {
        val ctx = requireContext()
        val n = item.note
        ctx.actionSheet(n.title.ifBlank { getString(R.string.notes_untitled) }, listOf(
            SheetItem(if (n.isPinned) R.drawable.ic_nt_pin_filled else R.drawable.ic_nt_pin, getString(if (n.isPinned) R.string.notes_unpin else R.string.notes_pin)) {
                vm.setPinned(n.id, !n.isPinned)
            },
            SheetItem(if (n.isFavorite) R.drawable.ic_nt_star_filled else R.drawable.ic_nt_star,
                getString(if (n.isFavorite) R.string.notes_unfavorite else R.string.notes_favorite)) {
                vm.setFavorite(n.id, !n.isFavorite)
            },
            SheetItem(R.drawable.ic_px_palette, getString(R.string.notes_color)) {
                ctx.showNoteColorSheet(n.colorHex) { vm.setColor(n.id, it) }
            },
            SheetItem(R.drawable.ic_px_folder_open, getString(R.string.notes_move_to)) {
                ctx.showGroupPicker(groups, n.groupId, onPick = { vm.moveNoteToGroup(n.id, it) }) { name ->
                    viewLifecycleOwner.lifecycleScope.launch { vm.moveNoteToGroup(n.id, vm.createGroup(name)) }
                }
            },
            SheetItem(R.drawable.ic_px_copy, getString(R.string.notes_duplicate)) {
                viewLifecycleOwner.lifecycleScope.launch {
                    vm.duplicate(n.id, if (n.title.isBlank()) "" else getString(R.string.notes_copy_of, n.title))
                }
            },
            SheetItem(R.drawable.ic_px_share, getString(R.string.notes_share)) { ctx.shareNote(item) },
            SheetItem(R.drawable.ic_px_delete, getString(R.string.notes_move_to_trash), destructive = true) { trash(item) },
        )).show()
    }

    private fun trash(item: NoteWithTags) {
        vm.moveToTrash(item.note.id)
        Snackbar.make(requireView(), R.string.notes_trashed, Snackbar.LENGTH_LONG)
            .setAction(R.string.notes_undo) { vm.restore(item.note.id) }
            .show()
    }

    // ---- Menu de la bibliothèque ---------------------------------------------------------------

    private fun showLibraryMenu() {
        val ctx = requireContext()
        ctx.actionSheet(null, listOf(
            SheetItem(R.drawable.ic_nt_sort, getString(R.string.notes_sort_by, getString(prefs.sort.label))) { showSortMenu() },
            SheetItem(R.drawable.ic_nt_tag, getString(R.string.notes_tags)) { notesActivity.openTags() },
            SheetItem(R.drawable.ic_px_delete, getString(R.string.notes_trash)) { notesActivity.openTrash() },
            SheetItem(R.drawable.ic_cloud, getString(R.string.cloud_proj_sync)) { syncWithGoogle() },
            SheetItem(R.drawable.ic_px_export, getString(R.string.notes_export_backup)) { launchExport() },
            SheetItem(R.drawable.ic_px_import, getString(R.string.notes_import_backup)) { launchImport() },
        )).show()
    }

    /** Le bouton « Sync Google » : marche dès qu'on est connecté, interrupteur de l'écran Cloud ou non. */
    private fun syncWithGoogle() {
        val app = requireContext().applicationContext
        if (!com.Atom2Universe.app.music.sync.GoogleSignInManager(app).isSignedIn()) {
            startActivity(com.Atom2Universe.app.cloud.CloudActivity.intent(requireContext()))
            return
        }
        Toast.makeText(app, R.string.cloud_proj_syncing, Toast.LENGTH_SHORT).show()
        // Dans le scope de l'application : quitter l'écran en plein envoi ne doit pas le couper.
        com.Atom2Universe.app.notes.sync.NotesSyncManager.launchSync(app) { report ->
            val message = when (report.outcome) {
                com.Atom2Universe.app.notes.sync.NotesSyncManager.Outcome.SYNCED ->
                    if (report.changedLocal) R.string.cloud_notes_synced_new else R.string.cloud_notes_synced
                com.Atom2Universe.app.notes.sync.NotesSyncManager.Outcome.TOO_NEW -> R.string.cloud_notes_too_new
                else -> R.string.cloud_proj_failed
            }
            Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSortMenu() {
        requireContext().actionSheet(getString(R.string.notes_sort), NotesPreferences.Sort.entries.map { s ->
            SheetItem(R.drawable.ic_nt_sort, getString(s.label), checked = s == prefs.sort) { prefs.sort = s; render() }
        }).show()
    }

    // ---- Sauvegarde ----------------------------------------------------------------------------

    private fun launchExport() = exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/zip"
        putExtra(Intent.EXTRA_TITLE, getString(R.string.notes_backup_file_name))
    })

    private fun launchImport() = importLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/zip"
    })

    private fun backupManager() = NotesBackupManager(requireContext().applicationContext, vm.repositoryRef)

    private fun exportBackup(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val r = backupManager().exportFullBackup(uri)
            view?.let { Snackbar.make(it, r.message, Snackbar.LENGTH_SHORT).show() }
        }
    }

    private fun askImportMode(uri: Uri) {
        val ctx = requireContext()
        ctx.actionSheet(getString(R.string.notes_import_mode_title), listOf(
            SheetItem(R.drawable.ic_px_add, getString(R.string.notes_import_merge)) { importBackup(uri, NotesBackupManager.ImportMode.MERGE) },
            SheetItem(R.drawable.ic_nt_restore, getString(R.string.notes_import_replace), destructive = true) {
                ctx.confirm(R.string.notes_import_replace, getString(R.string.notes_import_replace_message), R.string.notes_import_replace_confirm, true) {
                    importBackup(uri, NotesBackupManager.ImportMode.REPLACE)
                }
            },
        )).show()
    }

    private fun importBackup(uri: Uri, mode: NotesBackupManager.ImportMode) {
        viewLifecycleOwner.lifecycleScope.launch {
            val r = backupManager().importFullBackup(uri, mode)
            view?.let { Snackbar.make(it, r.message, Snackbar.LENGTH_SHORT).show() }
        }
    }
}

/** Partage une note en texte : son titre puis son Markdown. */
fun Context.shareNote(item: NoteWithTags) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (item.note.title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, item.note.title)
        putExtra(Intent.EXTRA_TEXT, NoteExportManager.shareText(item.note))
    }
    startActivity(Intent.createChooser(send, getString(R.string.notes_share)))
}
