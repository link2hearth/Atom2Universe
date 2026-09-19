package com.Atom2Universe.app.games.roguelike

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView

/**
 * Les liens du lexique dans un texte. Une chaîne de ressource marque un mot par `[[id|texte]]` :
 * `id` est l'entrée du [Lexicon] qu'il ouvre, `texte` ce qu'on lit. Le marqueur passe après le
 * `getString(…, args)` (contrairement à `<annotation>`, qui se perd dans un format), et laisse la
 * traduction libre de placer le mot où elle veut dans la phrase.
 *
 * Là où on ne sait pas dessiner un lien (canvas de la carte et du combat), [strip] rend le texte nu.
 */
object LexiconText {

    /** La couleur des mots cliquables, la même partout. */
    const val LINK_COLOR = 0xFF64B5F6.toInt()

    private val MARKUP = Regex("""\[\[([^|\]]+)\|([^\]]*)]]""")

    fun link(id: String, text: String) = "[[$id|$text]]"

    /** Le texte sans ses marqueurs : « [[stat_dodge|esquive]] » devient « esquive ». */
    fun strip(markup: String): String = MARKUP.replace(markup) { it.groupValues[2] }

    /** Le texte lu, avec un [ClickableSpan] (couleur + soulignement) sur chaque mot marqué. */
    fun render(markup: String, onLink: (String) -> Unit): CharSequence {
        val out = SpannableStringBuilder()
        var last = 0
        for (m in MARKUP.findAll(markup)) {
            out.append(markup, last, m.range.first)
            val id = m.groupValues[1]
            val start = out.length
            out.append(m.groupValues[2])
            out.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = onLink(id)
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = LINK_COLOR
                    ds.isUnderlineText = true
                }
            }, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            last = m.range.last + 1
        }
        out.append(markup, last, markup.length)
        return out
    }

    /** Affiche [markup] dans [tv], ses mots marqués ouvrant le lexique. */
    fun TextView.setLexiconText(markup: String, onLink: (String) -> Unit) {
        text = render(markup, onLink)
        movementMethod = LinkMovementMethod.getInstance()
        highlightColor = Color.TRANSPARENT
    }
}
