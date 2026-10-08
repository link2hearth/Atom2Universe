package com.Atom2Universe.app.games.roguelike

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.roguelike.LexiconText.setLexiconText

/**
 * L'écran du lexique, en surimpression comme l'inventaire (pas un Dialog : il casserait le mode
 * immersif). Deux visages : la **liste** par catégories (repliées, avec une recherche), et une
 * **fiche**, dont les mots marqués ouvrent d'autres fiches. Retour remonte l'historique, puis ferme.
 *
 * Les entrées secrètes (réactions, résonances, reliques pas trouvées…) n'apparaissent pas tant que
 * le héros ne les a pas vues ; les catégories qui en ont montrent « trouvées / total ».
 */
class LexiconPanel(private val root: View, private val game: () -> RoguelikeGame?) {

    private val ctx = root.context
    private val density = ctx.resources.displayMetrics.density

    private val search  = root.findViewById<EditText>(R.id.lex_search)
    private val content = root.findViewById<LinearLayout>(R.id.lex_content)
    private val scroll  = root.findViewById<ScrollView>(R.id.lex_scroll)

    private val expanded = mutableSetOf<LexiconCategory>()
    /** Les fiches ouvertes les unes après les autres ; vide, on est sur la liste. */
    private val history = ArrayDeque<String>()
    /** Vrai si la liste est « en dessous » des fiches : Retour y revient au lieu de fermer. */
    private var listBelow = true

    val isOpen get() = root.visibility == View.VISIBLE

    init {
        root.findViewById<View>(R.id.lex_back).setOnClickListener { back() }
        search.doAfterTextChanged { if (history.isEmpty() && isOpen) renderList() }
    }

    private fun env() = LexiconEnv(ctx, game()?.hero, game()?.floor ?: 1)

    /**
     * Ouvre le lexique : sur la liste (id nul), sur une catégorie (`cat:STATS`), ou directement
     * sur une fiche — Retour referme alors le lexique, on revient d'où on venait.
     */
    fun open(id: String? = null) {
        val wasOpen = isOpen
        root.visibility = View.VISIBLE
        when {
            id == null -> { history.clear(); listBelow = true; renderList() }
            id.startsWith("cat:") -> {
                val cat = runCatching { LexiconCategory.valueOf(id.removePrefix("cat:")) }.getOrNull()
                history.clear(); listBelow = true
                if (cat != null) expanded += cat
                search.setText("")
                renderList()
            }
            Lexicon.find(id) == null -> if (!wasOpen) root.visibility = View.GONE
            else -> {
                if (!wasOpen) { history.clear(); listBelow = false }
                history.addLast(id)
                renderEntry(id)
            }
        }
    }

    fun back() {
        when {
            history.size > 1 -> { history.removeLast(); renderEntry(history.last()) }
            history.size == 1 -> { history.removeLast(); if (listBelow) renderList() else hide() }
            else -> hide()
        }
    }

    fun hide() {
        root.visibility = View.GONE
        history.clear()
    }

    // ── La liste ────────────────────────────────────────────────────────────────

    private fun renderList() {
        search.visibility = View.VISIBLE
        content.removeAllViews()
        val env = env()
        val hero = env.hero
        val query = search.text.toString().trim().lowercase()

        if (query.isNotEmpty()) {
            val found = Lexicon.entries.filter { it.visible(hero) && it.title(env).lowercase().contains(query) }
            if (found.isEmpty()) content.addView(row(ctx.getString(R.string.lex_empty), 14f, 0xFF607D8B.toInt()) {})
            for (e in found) content.addView(row(e.title(env), 15f, LexiconText.LINK_COLOR) { open(e.id) })
            scroll.scrollTo(0, 0)
            return
        }

        for (cat in Lexicon.categories) {
            val shown = Lexicon.visibleIn(cat, hero)
            if (shown.isEmpty()) continue
            val secrets = Lexicon.entries.filter { it.category == cat && it.secret }
            val label = ctx.getString(cat.labelRes)
            val header = if (secrets.isEmpty()) label
                else ctx.getString(R.string.lex_category_count, label, secrets.count { it.visible(hero) }, secrets.size)
            val open = cat in expanded
            content.addView(row(header, 16f, if (open) 0xFFFFFFFF.toInt() else 0xFFB0BEC5.toInt(), bold = true) {
                if (open) expanded -= cat else expanded += cat
                renderList()
            })
            if (open) for (e in shown) content.addView(row(e.title(env), 15f, LexiconText.LINK_COLOR, indent = 16) { open(e.id) })
        }
    }

    // ── Une fiche ───────────────────────────────────────────────────────────────

    private fun renderEntry(id: String) {
        val entry = Lexicon.find(id) ?: return
        search.visibility = View.GONE
        content.removeAllViews()
        val env = env()

        content.addView(TextView(ctx).apply {
            text = entry.title(env)
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        content.addView(TextView(ctx).apply {
            text = ctx.getString(entry.category.labelRes)
            setTextColor(0xFF78909C.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        })
        for ((i, line) in entry.lines(env).withIndex()) {
            content.addView(TextView(ctx).apply {
                setLexiconText(line.text) { open(it) }
                setTextColor(when { line.personal -> 0xFFFFD54F.toInt(); i == 0 -> 0xFFECEFF1.toInt(); else -> 0xFFB0BEC5.toInt() })
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (i == 0) 15f else 14f)
                setLineSpacing(2 * density, 1f)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = (10 * density).toInt() }
            })
        }
        scroll.scrollTo(0, 0)
    }

    // ── Une ligne cliquable de la liste ─────────────────────────────────────────

    private fun row(text: String, sp: Float, color: Int, bold: Boolean = false, indent: Int = 0, onClick: () -> Unit) =
        TextView(ctx).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = (44 * density).toInt()
            setPadding(((8 + indent) * density).toInt(), 0, (8 * density).toInt(), 0)
            val out = TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            setBackgroundResource(out.resourceId)
            setOnClickListener { onClick() }
        }
}
