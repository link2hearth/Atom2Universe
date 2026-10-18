package com.Atom2Universe.app.notes.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.Atom2Universe.app.R
import com.Atom2Universe.app.notes.data.NoteWithTags
import com.Atom2Universe.app.notes.editor.MarkdownStyler
import com.Atom2Universe.app.notes.editor.MarkdownSyntax
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.dpf

/** Une ligne de la grille : un intertitre sur toute la largeur, ou une note. */
sealed class NoteRow {
    data class Header(val text: String) : NoteRow()
    data class Card(val item: NoteWithTags) : NoteRow()
}

/**
 * La grille des notes (bibliothèque et corbeille). Les cartes ont la hauteur de leur contenu :
 * une liste de courses est plus haute qu'une idée d'une ligne, comme sur un tableau de liège.
 */
class NoteCardsAdapter(
    private val context: Context,
    private val showGroup: () -> Boolean,
    private val onOpen: (NoteWithTags) -> Unit,
    private val onMenu: (NoteWithTags) -> Unit,
) : ListAdapter<NoteRow, RecyclerView.ViewHolder>(DIFF) {

    private val palette = context.notePalette()

    class HeaderHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    class CardHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.nt_card_title)
        val star: ImageView = v.findViewById(R.id.nt_card_star)
        val preview: TextView = v.findViewById(R.id.nt_card_preview)
        val tags: TextView = v.findViewById(R.id.nt_card_tags)
        val info: TextView = v.findViewById(R.id.nt_card_info)
    }

    override fun getItemViewType(position: Int) = if (getItem(position) is NoteRow.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == 0) HeaderHolder(TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ContextCompat.getColor(context, R.color.audio_text_tertiary))
            isAllCaps = true
            letterSpacing = 0.08f
            setPadding(context.dp(10), context.dp(14), context.dp(10), context.dp(4))
            layoutParams = StaggeredGridLayoutManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { isFullSpan = true }
        })
        else CardHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_notes_card, parent, false))

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is NoteRow.Header -> (holder as HeaderHolder).text.text = row.text
            is NoteRow.Card -> bind(holder as CardHolder, row.item)
        }
    }

    private fun bind(h: CardHolder, item: NoteWithTags) {
        val note = item.note
        h.itemView.background = com.Atom2Universe.app.OrnamentStyle.background(context, GradientDrawable().apply {
            setColor(context.noteSurface(note.colorHex))
            cornerRadius = com.Atom2Universe.app.AppearanceStyle.corner(context, 24f)
            if (note.colorHex == null) setStroke(context.dp(1), ContextCompat.getColor(context, R.color.px_divider))
        }, variant = note.id.hashCode())
        h.title.text = note.title
        h.title.visibility = if (note.title.isBlank()) View.GONE else View.VISIBLE
        h.star.visibility = if (note.isFavorite) View.VISIBLE else View.GONE

        val rendered = MarkdownSyntax.render(note.content, maxLines = 14)
        h.preview.text = MarkdownStyler.styledPreview(rendered, palette)
        h.preview.visibility = if (rendered.text.isBlank()) View.GONE else View.VISIBLE
        // Une note vide n'a ni titre ni texte : on l'annonce, sinon la carte serait muette.
        if (note.title.isBlank() && rendered.text.isBlank()) {
            h.title.visibility = View.VISIBLE
            h.title.text = context.getString(R.string.notes_untitled)
        }

        h.tags.text = item.tags.joinToString("  ") { context.getString(R.string.notes_tag_label, it.name) }
        h.tags.visibility = if (item.tags.isEmpty()) View.GONE else View.VISIBLE

        val parts = ArrayList<CharSequence>()
        if (showGroup()) item.group?.let { parts += it.name }
        val (done, total) = MarkdownSyntax.checklistProgress(note.content)
        if (total > 0) parts += context.getString(R.string.notes_checklist_progress, done, total)
        parts += DateUtils.getRelativeTimeSpanString(note.dateModified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        h.info.text = parts.joinToString(context.getString(R.string.notes_info_separator))

        h.itemView.setOnClickListener { onOpen(item) }
        h.itemView.setOnLongClickListener { onMenu(item); true }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<NoteRow>() {
            override fun areItemsTheSame(a: NoteRow, b: NoteRow) = when {
                a is NoteRow.Header && b is NoteRow.Header -> a.text == b.text
                a is NoteRow.Card && b is NoteRow.Card -> a.item.note.id == b.item.note.id
                else -> false
            }
            override fun areContentsTheSame(a: NoteRow, b: NoteRow) = a == b
        }
    }
}
