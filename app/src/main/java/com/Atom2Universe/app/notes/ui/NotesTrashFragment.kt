package com.Atom2Universe.app.notes.ui

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.NoteWithTags
import com.Atom2Universe.app.notes.viewmodel.NotesViewModel
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.confirm
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.iconButton
import kotlinx.coroutines.launch

/**
 * La corbeille : les notes supprimées y restent [NotesViewModel.TRASH_DAYS] jours, puis
 * s'effacent pour de bon à l'ouverture suivante des Notes. Un appui propose de restaurer ou d'effacer.
 */
class NotesTrashFragment : Fragment(R.layout.fragment_notes_page) {

    private val vm: NotesViewModel get() = (activity as NotesActivity).viewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        view.findViewById<TextView>(R.id.nt_page_title).setText(R.string.notes_trash)
        view.findViewById<View>(R.id.nt_page_back).setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        view.findViewById<ImageView>(R.id.nt_page_empty_icon).setImageResource(R.drawable.ic_px_delete)
        view.findViewById<TextView>(R.id.nt_page_empty_text).setText(R.string.notes_trash_empty)
        val empty = view.findViewById<View>(R.id.nt_page_empty)

        val emptyBtn = ctx.iconButton(R.drawable.ic_px_delete, R.string.notes_empty_trash) {
            ctx.confirm(R.string.notes_empty_trash, getString(R.string.notes_empty_trash_message), R.string.notes_delete, true) { vm.emptyTrash() }
        }.apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(44)) }
        view.findViewById<LinearLayout>(R.id.nt_page_actions).addView(emptyBtn)

        val adapter = NoteCardsAdapter(ctx, showGroup = { true }, onOpen = ::showMenu, onMenu = ::showMenu)
        val list = RecyclerView(ctx).apply {
            val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
            layoutManager = StaggeredGridLayoutManager((widthDp / 180f).toInt().coerceAtLeast(2), StaggeredGridLayoutManager.VERTICAL)
            clipToPadding = false
            setPadding(ctx.dp(6), 0, ctx.dp(6), ctx.dp(24))
            this.adapter = adapter
        }
        view.findViewById<FrameLayout>(R.id.nt_page_content).addView(list, 0)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.trashedNotes.collect { notes ->
                    val rows = ArrayList<NoteRow>()
                    if (notes.isNotEmpty()) rows += NoteRow.Header(getString(R.string.notes_trash_hint, NotesViewModel.TRASH_DAYS))
                    notes.mapTo(rows) { NoteRow.Card(it) }
                    adapter.submitList(rows)
                    empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
                    emptyBtn.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun showMenu(item: NoteWithTags) {
        val ctx = requireContext()
        ctx.actionSheet(item.note.title.ifBlank { getString(R.string.notes_untitled) }, listOf(
            SheetItem(R.drawable.ic_nt_restore, getString(R.string.notes_restore)) { vm.restore(item.note.id) },
            SheetItem(R.drawable.ic_px_delete, getString(R.string.notes_delete_forever), destructive = true) {
                ctx.confirm(R.string.notes_delete_forever, null, R.string.notes_delete, true) { vm.deleteFromTrash(item.note.id) }
            },
        )).show()
    }
}
