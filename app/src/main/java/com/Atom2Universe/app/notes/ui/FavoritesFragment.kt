package com.Atom2Universe.app.notes.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.ui.adapter.NoteAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_favorites, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val notesActivity = activity as NotesActivity
        val adapter = NoteAdapter(onClick = { notesActivity.navigateToNoteEditor(it.note.id) })
        view.findViewById<RecyclerView>(R.id.favorites_recycler).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        lifecycleScope.launch {
            notesActivity.viewModel.favoriteNotes.collectLatest { notes ->
                adapter.submitList(notes)
                view.findViewById<View>(R.id.favorites_empty_state).visibility =
                    if (notes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
