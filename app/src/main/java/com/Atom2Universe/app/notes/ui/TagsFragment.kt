package com.Atom2Universe.app.notes.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.TagCategory
import com.Atom2Universe.app.notes.data.TagWithCount
import com.Atom2Universe.app.notes.ui.adapter.ExpandableTagAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TagsFragment : Fragment() {

    private lateinit var notesActivity: NotesActivity
    private lateinit var adapter: ExpandableTagAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_tags, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        notesActivity = activity as NotesActivity

        adapter = ExpandableTagAdapter(
            onTagClick = { /* navigate to notes by tag */ },
            onTagEdit = { item -> showEditTagDialog(item) },
            onTagDelete = { item ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.notes_delete_tag_title)
                    .setPositiveButton(R.string.notes_delete) { _, _ ->
                        lifecycleScope.launch { notesActivity.viewModel.deleteTag(item.tag) }
                    }
                    .setNegativeButton(R.string.notes_cancel, null)
                    .show()
            },
            onCategoryEdit = { cat -> showEditCategoryDialog(cat) },
            onCategoryDelete = { cat ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.notes_delete_category_title)
                    .setPositiveButton(R.string.notes_delete) { _, _ ->
                        lifecycleScope.launch { notesActivity.viewModel.deleteCategory(cat) }
                    }
                    .setNegativeButton(R.string.notes_cancel, null)
                    .show()
            }
        )

        view.findViewById<RecyclerView>(R.id.tags_recycler).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@TagsFragment.adapter
        }

        view.findViewById<ImageButton>(R.id.btn_new_tag).setOnClickListener { showCreateTagDialog() }
        view.findViewById<ImageButton>(R.id.btn_new_category).setOnClickListener { showCreateCategoryDialog() }

        lifecycleScope.launch {
            combine(
                notesActivity.viewModel.allTagsWithCount,
                notesActivity.viewModel.allCategories
            ) { tags, categories -> Pair(tags, categories) }.collectLatest { (tags, categories) ->
                val catMap = mutableMapOf<Long?, MutableList<TagWithCount>>()
                catMap[null] = mutableListOf()
                categories.forEach { catMap[it.id] = mutableListOf() }
                tags.forEach { catMap.getOrPut(it.tag.categoryId) { mutableListOf() }.add(it) }

                val catList: List<TagCategory?> = listOf(null) + categories
                adapter.submitData(catList, catMap)

                view.findViewById<View>(R.id.tags_empty_state).visibility =
                    if (tags.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showCreateTagDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_tag, null)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_create_tag)
            .setView(dialogView)
            .setPositiveButton(R.string.notes_create) { _, _ ->
                val name = dialogView.findViewById<EditText>(R.id.tag_name_input).text.toString().trim()
                if (name.isNotBlank()) lifecycleScope.launch { notesActivity.viewModel.createTag(name) }
            }
            .setNegativeButton(R.string.notes_cancel, null)
            .show()
    }

    private fun showCreateCategoryDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_tag, null)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_create_category)
            .setView(dialogView)
            .setPositiveButton(R.string.notes_create) { _, _ ->
                val name = dialogView.findViewById<EditText>(R.id.tag_name_input).text.toString().trim()
                if (name.isNotBlank()) lifecycleScope.launch { notesActivity.viewModel.createCategory(name) }
            }
            .setNegativeButton(R.string.notes_cancel, null)
            .show()
    }

    private fun showEditTagDialog(item: TagWithCount) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_tag, null)
        dialogView.findViewById<EditText>(R.id.tag_name_input).setText(item.tag.name)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_edit_tag)
            .setView(dialogView)
            .setPositiveButton(R.string.notes_save) { _, _ ->
                val name = dialogView.findViewById<EditText>(R.id.tag_name_input).text.toString().trim()
                if (name.isNotBlank()) lifecycleScope.launch { notesActivity.viewModel.updateTag(item.tag.copy(name = name)) }
            }
            .setNegativeButton(R.string.notes_cancel, null)
            .show()
    }

    private fun showEditCategoryDialog(category: TagCategory) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_tag, null)
        dialogView.findViewById<EditText>(R.id.tag_name_input).setText(category.name)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notes_edit_category)
            .setView(dialogView)
            .setPositiveButton(R.string.notes_save) { _, _ ->
                val name = dialogView.findViewById<EditText>(R.id.tag_name_input).text.toString().trim()
                if (name.isNotBlank()) lifecycleScope.launch { notesActivity.viewModel.updateCategory(category.copy(name = name)) }
            }
            .setNegativeButton(R.string.notes_cancel, null)
            .show()
    }
}
