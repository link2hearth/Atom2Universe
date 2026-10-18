package com.Atom2Universe.app.notes.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.TagCategory
import com.Atom2Universe.app.notes.data.TagWithCount
import com.Atom2Universe.app.notes.viewmodel.NotesViewModel
import com.Atom2Universe.app.notes.viewmodel.NotesViewModel.LibraryFilter
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.confirm
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.iconButton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Les tags, rangés par catégorie. Un appui sur un tag revient à la bibliothèque filtrée sur lui ;
 * un appui long (ou ⋯) le renomme, le range ou le supprime.
 */
class NotesTagsFragment : Fragment(R.layout.fragment_notes_page) {

    private val notesActivity get() = activity as NotesActivity
    private val vm: NotesViewModel get() = notesActivity.viewModel
    private lateinit var column: LinearLayout
    private var categories: List<TagCategory> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        view.findViewById<TextView>(R.id.nt_page_title).setText(R.string.notes_tags)
        view.findViewById<View>(R.id.nt_page_back).setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        view.findViewById<ImageView>(R.id.nt_page_empty_icon).setImageResource(R.drawable.ic_nt_tag)
        view.findViewById<TextView>(R.id.nt_page_empty_text).setText(R.string.notes_tags_empty)
        val empty = view.findViewById<View>(R.id.nt_page_empty)

        view.findViewById<LinearLayout>(R.id.nt_page_actions).addView(ctx.iconButton(R.drawable.ic_px_add, R.string.notes_new) { showAddMenu() }
            .apply { layoutParams = LinearLayout.LayoutParams(ctx.dp(44), ctx.dp(44)) })

        column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(24))
        }
        view.findViewById<FrameLayout>(R.id.nt_page_content).addView(ScrollView(ctx).apply { addView(column) }, 0)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.allTagsWithCount, vm.allCategories) { t, c -> t to c }.collect { (tags, cats) ->
                    categories = cats
                    render(tags, cats)
                    empty.visibility = if (tags.isEmpty() && cats.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun render(tags: List<TagWithCount>, cats: List<TagCategory>) {
        column.removeAllViews()
        val byCategory = tags.groupBy { t -> t.tag.categoryId?.takeIf { id -> cats.any { it.id == id } } }
        // Les tags sans catégorie d'abord ; l'intertitre n'apparaît que s'il existe des catégories.
        byCategory[null]?.let { loose ->
            if (cats.isNotEmpty()) column.addView(header(getString(R.string.notes_uncategorized), null))
            loose.forEach { column.addView(tagRow(it)) }
        }
        for (c in cats) {
            column.addView(header(c.name, c))
            byCategory[c.id]?.forEach { column.addView(tagRow(it)) }
        }
    }

    private fun header(text: String, category: TagCategory?): View {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(4), ctx.dp(16), 0, ctx.dp(4))
            addView(TextView(ctx).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ContextCompat.getColor(ctx, R.color.audio_text_tertiary))
                isAllCaps = true
                letterSpacing = 0.08f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (category != null) addView(ctx.iconButton(R.drawable.ic_px_more, R.string.px_more, size = 36, pad = 8) { categoryMenu(category) })
        }
    }

    private fun tagRow(t: TagWithCount): View {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ctx.dp(52)
            setPadding(ctx.dp(14), 0, ctx.dp(4), 0)
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_px_card)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(6) }
            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_nt_tag)
                imageTintList = ContextCompat.getColorStateList(ctx, R.color.px_icon_tint)
            }, LinearLayout.LayoutParams(ctx.dp(20), ctx.dp(20)).apply { marginEnd = ctx.dp(14) })
            addView(TextView(ctx).apply {
                text = t.tag.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(ContextCompat.getColor(ctx, R.color.audio_text_primary))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(ctx).apply {
                text = resources.getQuantityString(R.plurals.notes_count, t.usageCount, t.usageCount)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ContextCompat.getColor(ctx, R.color.audio_text_tertiary))
            })
            addView(ctx.iconButton(R.drawable.ic_px_more, R.string.px_more, size = 40, pad = 10) { tagMenu(t) })
            setOnClickListener {
                vm.filter.value = LibraryFilter.TagFilter(t.tag.id)
                notesActivity.backToLibrary()
            }
            setOnLongClickListener { tagMenu(t); true }
        }
    }

    private fun showAddMenu() {
        val ctx = requireContext()
        ctx.actionSheet(null, listOf(
            SheetItem(R.drawable.ic_nt_tag, getString(R.string.notes_new_tag)) {
                ctx.askName(R.string.notes_new_tag, "", R.string.notes_create) { name ->
                    viewLifecycleOwner.lifecycleScope.launch { vm.getOrCreateTag(name.removePrefix("#").trim()) }
                }
            },
            SheetItem(R.drawable.ic_px_folder_open, getString(R.string.notes_new_category)) {
                ctx.askName(R.string.notes_new_category, "", R.string.notes_create) { vm.createCategory(it) }
            },
        )).show()
    }

    private fun tagMenu(t: TagWithCount) {
        val ctx = requireContext()
        val items = arrayListOf(
            SheetItem(R.drawable.ic_px_rename, getString(R.string.notes_rename)) {
                ctx.askName(R.string.notes_rename_tag, t.tag.name, R.string.notes_save) { vm.renameTag(t.tag, it.removePrefix("#").trim()) }
            },
        )
        if (categories.isNotEmpty()) items += SheetItem(R.drawable.ic_px_folder_open, getString(R.string.notes_move_to)) {
            ctx.actionSheet(getString(R.string.notes_move_to),
                listOf(SheetItem(R.drawable.ic_px_close, getString(R.string.notes_uncategorized), checked = t.tag.categoryId == null) {
                    vm.moveTagToCategory(t.tag.id, null)
                }) + categories.map { c ->
                    SheetItem(R.drawable.ic_px_folder_open, c.name, checked = c.id == t.tag.categoryId) { vm.moveTagToCategory(t.tag.id, c.id) }
                }).show()
        }
        items += SheetItem(R.drawable.ic_px_delete, getString(R.string.notes_delete_tag), destructive = true) {
            ctx.confirm(R.string.notes_delete_tag, getString(R.string.notes_delete_tag_message, t.tag.name), R.string.notes_delete, true) { vm.deleteTag(t.tag) }
        }
        ctx.actionSheet(getString(R.string.notes_tag_label, t.tag.name), items).show()
    }

    private fun categoryMenu(c: TagCategory) {
        val ctx = requireContext()
        ctx.actionSheet(c.name, listOf(
            SheetItem(R.drawable.ic_px_rename, getString(R.string.notes_rename)) {
                ctx.askName(R.string.notes_rename_category, c.name, R.string.notes_save) { vm.renameCategory(c, it) }
            },
            SheetItem(R.drawable.ic_px_delete, getString(R.string.notes_delete_category), destructive = true) {
                ctx.confirm(R.string.notes_delete_category, getString(R.string.notes_delete_category_message, c.name), R.string.notes_delete, true) {
                    vm.deleteCategory(c)
                }
            },
        )).show()
    }
}
