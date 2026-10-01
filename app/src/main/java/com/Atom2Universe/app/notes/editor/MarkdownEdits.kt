package com.Atom2Universe.app.notes.editor

import com.Atom2Universe.app.notes.editor.MarkdownSyntax.Block

/**
 * Les gestes d'édition (boutons de la barre, retour à la ligne dans une liste, case cochée) écrits
 * comme de purs calculs sur le texte : testés en JVM, appliqués tels quels par l'éditeur.
 *
 * Chaque geste rend un [Edit] : remplacer [start, end[ par [text], puis sélectionner
 * [selStart, selEnd[ (positions dans le texte APRÈS remplacement).
 */
object MarkdownEdits {

    data class Edit(val start: Int, val end: Int, val text: String, val selStart: Int, val selEnd: Int = selStart)

    /** Applique un [Edit] à une chaîne : ce que fera l'éditeur, pour les tests. */
    fun apply(text: String, e: Edit): String = text.substring(0, e.start) + e.text + text.substring(e.end)

    fun lineStart(text: CharSequence, pos: Int): Int {
        var i = pos.coerceIn(0, text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    fun lineEnd(text: CharSequence, pos: Int): Int {
        var i = pos.coerceIn(0, text.length)
        while (i < text.length && text[i] != '\n') i++
        return i
    }

    // ---- Styles en ligne ------------------------------------------------------------------------

    enum class Inline(val marker: String) { BOLD("**"), ITALIC("*"), UNDERLINE("__"), STRIKE("~~"), CODE("`") }

    /**
     * Met ou retire un style en ligne. Sans sélection, le mot sous le curseur est pris ; hors d'un
     * mot, une paire de marques vide est posée et le curseur va au milieu.
     *
     * Gras et italique partagent l'étoile : on compte les étoiles qui entourent la sélection
     * (1 = italique, 2 = gras, 3 = les deux) pour savoir lequel est déjà là.
     */
    fun toggleInline(text: String, selStart: Int, selEnd: Int, style: Inline): Edit {
        var s = minOf(selStart, selEnd)
        var e = maxOf(selStart, selEnd)
        if (s == e) {
            val (ws, we) = wordAt(text, s)
            if (ws == we) {
                val m = style.marker
                return Edit(s, s, m + m, s + m.length)
            }
            s = ws; e = we
        }
        if (style == Inline.BOLD || style == Inline.ITALIC) {
            val left = run(text, s - 1, -1, '*')
            val right = run(text, e, 1, '*')
            val around = minOf(left, right)
            val n = if (style == Inline.BOLD) 2 else 1
            val active = if (style == Inline.BOLD) around >= 2 else around == 1 || around == 3
            return if (active) {
                Edit(s - n, e + n, text.substring(s, e), s - n, e - n)
            } else {
                val m = "*".repeat(n)
                Edit(s, e, m + text.substring(s, e) + m, s + n, e + n)
            }
        }
        val m = style.marker
        val l = m.length
        return if (s >= l && e + l <= text.length && text.regionMatches(s - l, m, 0, l) && text.regionMatches(e, m, 0, l)) {
            Edit(s - l, e + l, text.substring(s, e), s - l, e - l)
        } else {
            Edit(s, e, m + text.substring(s, e) + m, s + l, e + l)
        }
    }

    /** Les styles en ligne actifs à cette position (pour allumer les boutons de la barre). */
    fun activeInline(text: CharSequence, selStart: Int, selEnd: Int): Set<Inline> {
        val ls = lineStart(text, selStart)
        val le = lineEnd(text, selStart)
        if (maxOf(selStart, selEnd) > le) return emptySet()
        val line = MarkdownSyntax.parseLine(text.substring(ls, le))
        val s = minOf(selStart, selEnd) - ls
        val e = maxOf(selStart, selEnd) - ls
        val out = HashSet<Inline>()
        for (t in line.tokens) {
            if (s < t.start || e > t.end) continue
            when (t.kind) {
                MarkdownSyntax.Kind.BOLD -> out += Inline.BOLD
                MarkdownSyntax.Kind.ITALIC -> out += Inline.ITALIC
                MarkdownSyntax.Kind.UNDERLINE -> out += Inline.UNDERLINE
                MarkdownSyntax.Kind.STRIKE -> out += Inline.STRIKE
                MarkdownSyntax.Kind.CODE -> out += Inline.CODE
                else -> Unit
            }
        }
        return out
    }

    private fun run(text: String, from: Int, step: Int, c: Char): Int {
        var n = 0
        var i = from
        while (i in text.indices && text[i] == c && n < 3) { n++; i += step }
        return n
    }

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '-'

    private fun wordAt(text: String, pos: Int): Pair<Int, Int> {
        var s = pos
        var e = pos
        while (s > 0 && isWordChar(text[s - 1])) s--
        while (e < text.length && isWordChar(text[e])) e++
        return s to e
    }

    // ---- Blocs ------------------------------------------------------------------------------------

    /** Ce qu'un bouton de bloc pose en tête de chaque ligne sélectionnée. */
    enum class BlockStyle { BULLET, CHECK, NUMBER, QUOTE }

    /**
     * Pose ou retire un type de bloc sur les lignes touchées par la sélection. Si toutes les lignes
     * l'ont déjà, il est retiré ; sinon chaque ligne prend ce préfixe à la place de l'ancien
     * (une puce devient une case, un titre devient une citation…). Une liste numérotée se compte
     * à partir de 1, ou à la suite de la ligne du dessus si elle est déjà numérotée.
     */
    fun toggleBlock(text: String, selStart: Int, selEnd: Int, style: BlockStyle): Edit {
        val from = lineStart(text, minOf(selStart, selEnd))
        val to = lineEnd(text, maxOf(selStart, selEnd))
        val lines = text.substring(from, to).split('\n')
        val parsed = lines.map { MarkdownSyntax.parseLine(it) }
        val target = when (style) {
            BlockStyle.BULLET -> Block.BULLET
            BlockStyle.CHECK -> Block.CHECK
            BlockStyle.NUMBER -> Block.NUMBER
            BlockStyle.QUOTE -> Block.QUOTE
        }
        val remove = parsed.all { it.block == target }
        var number = 1
        if (style == BlockStyle.NUMBER && from > 0) {
            val above = MarkdownSyntax.parseLine(text.substring(lineStart(text, from - 1), from - 1))
            if (above.block == Block.NUMBER) number = above.level + 1
        }
        val sb = StringBuilder()
        // Décalage du curseur : seule la première et la dernière ligne comptent.
        var firstShift = 0
        var totalShift = 0
        for ((i, line) in lines.withIndex()) {
            val p = parsed[i]
            val indent = " ".repeat(p.indent)
            val body = line.substring(p.prefixEnd)
            val prefix = when {
                remove -> indent.takeIf { target != Block.QUOTE } ?: ""
                style == BlockStyle.BULLET -> "$indent- "
                style == BlockStyle.CHECK -> "$indent- [ ] "
                style == BlockStyle.NUMBER -> "$indent${number++}. "
                else -> "> "
            }
            if (i > 0) sb.append('\n')
            sb.append(prefix).append(body)
            val shift = prefix.length - p.prefixEnd
            if (i == 0) firstShift = shift
            totalShift += shift
        }
        val s = minOf(selStart, selEnd)
        val e = maxOf(selStart, selEnd)
        val newS = (s + firstShift).coerceAtLeast(from)
        val newE = (e + totalShift).coerceAtLeast(newS)
        return Edit(from, to, sb.toString(), newS, newE)
    }

    /** Le bouton « Titre » fait tourner la ligne : texte → # → ## → ### → texte. */
    fun cycleHeading(text: String, pos: Int): Edit {
        val ls = lineStart(text, pos)
        val le = lineEnd(text, pos)
        val line = text.substring(ls, le)
        val p = MarkdownSyntax.parseLine(line)
        val next = if (p.block == Block.HEADING) (p.level + 1) % 4 else 1
        val prefix = if (next == 0) "" else "#".repeat(next) + " "
        val body = line.substring(p.prefixEnd)
        val newPos = (pos + prefix.length - p.prefixEnd).coerceAtLeast(ls)
        return Edit(ls, le, prefix + body, newPos)
    }

    /** Coche ou décoche la case de la ligne qui contient [pos] ; null si la ligne n'en a pas. */
    fun toggleCheck(text: String, pos: Int): Edit? {
        val ls = lineStart(text, pos)
        val line = text.substring(ls, lineEnd(text, pos))
        val p = MarkdownSyntax.parseLine(line)
        if (p.block != Block.CHECK) return null
        val box = ls + p.indent + 3 // `- [` puis le caractère de la case
        return Edit(box, box + 1, if (p.level == 1) " " else "x", pos)
    }

    /**
     * Juste après un retour à la ligne tapé en [newline] (la position du `\n`) : une liste se
     * continue d'elle-même. Sur un élément vide, le retour à la ligne termine la liste à la place
     * (le préfixe vide et le saut de ligne sont retirés). Null quand il n'y a rien à faire.
     */
    fun continueList(text: String, newline: Int): Edit? {
        if (newline !in text.indices || text[newline] != '\n') return null
        val ls = lineStart(text, newline)
        val line = text.substring(ls, newline)
        val p = MarkdownSyntax.parseLine(line)
        if (p.block == Block.PARAGRAPH || p.block == Block.HEADING) return null
        val empty = line.substring(p.prefixEnd).isBlank()
        if (empty) {
            // On efface l'élément vide et le saut de ligne : la liste est finie.
            return Edit(ls, newline + 1, "", ls)
        }
        val indent = " ".repeat(p.indent)
        val prefix = when (p.block) {
            Block.QUOTE -> "> "
            Block.BULLET -> indent + line[p.indent] + " "
            Block.CHECK -> indent + line[p.indent] + " [ ] "
            Block.NUMBER -> indent + (p.level + 1) + line[p.prefixEnd - 2] + " "
            else -> return null
        }
        val at = newline + 1
        return Edit(at, at, prefix, at + prefix.length)
    }

    /**
     * Juste après un retour arrière qui a abîmé un préfixe de liste (`- [ ]` sans son espace…) :
     * on retire le reste du préfixe, pour que la ligne redevienne du texte simple d'un seul geste.
     * [lineBefore] est la ligne avant l'effacement, [deletedAt] la position effacée dans le texte.
     */
    fun afterBackspace(text: String, lineBefore: String, lineStartPos: Int, deletedAt: Int): Edit? {
        val before = MarkdownSyntax.parseLine(lineBefore)
        if (before.block !in setOf(Block.BULLET, Block.CHECK, Block.NUMBER, Block.QUOTE)) return null
        // Seul l'effacement du dernier caractère du préfixe déclenche le nettoyage.
        if (deletedAt - lineStartPos != before.prefixEnd - 1) return null
        val keep = if (before.block == Block.QUOTE) 0 else before.indent
        val start = lineStartPos + keep
        val end = lineStartPos + before.prefixEnd - 1
        if (end <= start || end > text.length) return null
        return Edit(start, end, "", start)
    }

    /** Insère un lien web `[texte](adresse)` à la place de la sélection. */
    fun insertLink(text: String, selStart: Int, selEnd: Int, label: String, url: String): Edit {
        val s = minOf(selStart, selEnd)
        val e = maxOf(selStart, selEnd)
        val shown = label.ifBlank { text.substring(s, e).ifBlank { url } }
        val md = "[$shown]($url)"
        return Edit(s, e, md, s + md.length)
    }

    /** Insère un lien vers une note `[[Titre]]`. */
    fun insertWikiLink(selStart: Int, selEnd: Int, title: String): Edit {
        val s = minOf(selStart, selEnd)
        val md = "[[$title]]"
        return Edit(s, maxOf(selStart, selEnd), md, s + md.length)
    }
}
