package com.Atom2Universe.app.notes.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.ui.adapter.NoteAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var searchJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val notesActivity = activity as NotesActivity
        val adapter = NoteAdapter(onClick = { notesActivity.navigateToNoteEditor(it.note.id) })
        val recycler = view.findViewById<RecyclerView>(R.id.search_results_recycler)
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter

        val emptyView = view.findViewById<View>(R.id.search_empty_state)
        val initialView = view.findViewById<View>(R.id.search_initial_state)
        val resultsCountView = view.findViewById<TextView>(R.id.search_results_count)

        initialView?.visibility = View.VISIBLE

        view.findViewById<EditText>(R.id.search_input).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                searchJob?.cancel()
                if (query.isBlank()) {
                    initialView?.visibility = View.VISIBLE
                    emptyView?.visibility = View.GONE
                    recycler.visibility = View.GONE
                    adapter.submitList(emptyList())
                    return
                }
                initialView?.visibility = View.GONE
                searchJob = lifecycleScope.launch {
                    delay(300)
                    notesActivity.viewModel.search(query)
                }
            }
        })

        lifecycleScope.launch {
            notesActivity.viewModel.searchResults.collectLatest { results ->
                adapter.submitList(results)
                val hasQuery = view.findViewById<EditText>(R.id.search_input).text.isNotBlank()
                if (hasQuery) {
                    recycler.visibility = if (results.isNotEmpty()) View.VISIBLE else View.GONE
                    emptyView?.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                    resultsCountView?.text = resources.getQuantityString(R.plurals.notes_search_results, results.size, results.size)
                }
            }
        }
    }
}
