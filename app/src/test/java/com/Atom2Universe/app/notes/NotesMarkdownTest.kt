package com.Atom2Universe.app.notes

import com.Atom2Universe.app.notes.editor.EditHistory
import com.Atom2Universe.app.notes.editor.MarkdownEdits
import com.Atom2Universe.app.notes.editor.MarkdownEdits.BlockStyle
import com.Atom2Universe.app.notes.editor.MarkdownEdits.Inline
import com.Atom2Universe.app.notes.editor.MarkdownSyntax
import com.Atom2Universe.app.notes.editor.MarkdownSyntax.Block
import com.Atom2Universe.app.notes.editor.MarkdownSyntax.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le Markdown des notes : lecture des lignes, rendu sans marques, gestes de la barre d'outils,
 * listes qui se continuent, et annuler / rétablir.
 */
class NotesMarkdownTest {

    private fun kinds(line: String, kind: Kind) =
        MarkdownSyntax.parseLine(line).tokens.filter { it.kind == kind }.map { line.substring(it.start, it.end) }

    // ---- Lecture -------------------------------------------------------------------------------

    @Test
    fun `les blocs sont reconnus`() {
        assertEquals(Block.HEADING, MarkdownSyntax.parseLine("## Titre").block)
        assertEquals(2, MarkdownSyntax.parseLine("## Titre").level)
        assertEquals(Block.PARAGRAPH, MarkdownSyntax.parseLine("#Titre").block)
        assertEquals(Block.QUOTE, MarkdownSyntax.parseLine("> cité").block)
        assertEquals(Block.BULLET, MarkdownSyntax.parseLine("- puce").block)
        assertEquals(Block.BULLET, MarkdownSyntax.parseLine("  * puce").block)
        assertEquals(2, MarkdownSyntax.parseLine("  * puce").indent)
        val check = MarkdownSyntax.parseLine("- [x] fait")
        assertEquals(Block.CHECK, check.block)
        assertEquals(1, check.level)
        assertEquals(6, check.prefixEnd)
        assertEquals(0, MarkdownSyntax.parseLine("- [ ] à faire").level)
        val num = MarkdownSyntax.parseLine("12. douze")
        assertEquals(Block.NUMBER, num.block)
        assertEquals(12, num.level)
    }

    @Test
    fun `gras italique et leur melange ne se confondent pas`() {
        assertEquals(listOf("gras"), kinds("un **gras** ici", Kind.BOLD))
        assertEquals(emptyList<String>(), kinds("un **gras** ici", Kind.ITALIC))
        assertEquals(listOf("pench"), kinds("un *pench* ici", Kind.ITALIC))
        assertEquals(listOf("les deux"), kinds("***les deux***", Kind.BOLD))
        assertEquals(listOf("les deux"), kinds("***les deux***", Kind.ITALIC))
        assertEquals(listOf("a", "c"), kinds("**a** et **c**", Kind.BOLD))
        // Une étoile seule n'est pas un style
        assertEquals(emptyList<String>(), kinds("2 * 3 = 6", Kind.ITALIC))
    }

    @Test
    fun `rien n est lu dans un code`() {
        assertEquals(listOf("**x**"), kinds("voir `**x**`", Kind.CODE))
        assertEquals(emptyList<String>(), kinds("voir `**x**`", Kind.BOLD))
    }

    @Test
    fun `liens web et liens vers une note`() {
        val line = "Voir [le site](https://a.b/c) et [[Courses]]"
        val p = MarkdownSyntax.parseLine(line)
        val link = p.tokens.single { it.kind == Kind.LINK }
        assertEquals("le site", line.substring(link.start, link.end))
        assertEquals("https://a.b/c", link.data)
        val wiki = p.tokens.single { it.kind == Kind.WIKI }
        assertEquals("Courses", wiki.data)
        assertEquals(listOf("Courses"), MarkdownSyntax.wikiLinks("a [[Courses]] b [[Courses]]"))
    }

    // ---- Rendu ---------------------------------------------------------------------------------

    @Test
    fun `le rendu retire les marques et garde les styles`() {
        val r = MarkdownSyntax.render("# Titre\n- [ ] lait\n- [x] pain\n- puce\n> **cité**")
        assertEquals("Titre\n☐ lait\n☑ pain\n• puce\ncité", r.text)
        val bold = r.tokens.single { it.kind == Kind.BOLD }
        assertEquals("cité", r.text.substring(bold.start, bold.end))
        val done = r.tokens.single { it.kind == Kind.DONE }
        assertEquals("pain", r.text.substring(done.start, done.end))
        val heading = r.tokens.single { it.kind == Kind.HEADING }
        assertEquals("Titre", r.text.substring(heading.start, heading.end))
    }

    @Test
    fun `texte brut mots et avancement`() {
        val md = "Liste **utile**\n- [x] un\n- [ ] deux\n- [X] trois"
        assertEquals("Liste utile\n☑ un\n☐ deux\n☑ trois", MarkdownSyntax.toPlainText(md))
        assertEquals(2 to 3, MarkdownSyntax.checklistProgress(md))
        assertEquals(5, MarkdownSyntax.wordCount(md))
    }

    // ---- Styles en ligne -------------------------------------------------------------------------

    @Test
    fun `gras pose puis retire sur une selection`() {
        val t = "un mot ici"
        val on = MarkdownEdits.toggleInline(t, 3, 6, Inline.BOLD)
        val bolded = MarkdownEdits.apply(t, on)
        assertEquals("un **mot** ici", bolded)
        assertEquals(5, on.selStart); assertEquals(8, on.selEnd)
        val off = MarkdownEdits.toggleInline(bolded, on.selStart, on.selEnd, Inline.BOLD)
        assertEquals(t, MarkdownEdits.apply(bolded, off))
    }

    @Test
    fun `sans selection le mot sous le curseur est pris`() {
        val t = "un mot ici"
        assertEquals("un *mot* ici", MarkdownEdits.apply(t, MarkdownEdits.toggleInline(t, 4, 4, Inline.ITALIC)))
        val empty = MarkdownEdits.toggleInline("a  b", 2, 2, Inline.STRIKE)
        assertEquals("a ~~~~ b", MarkdownEdits.apply("a  b", empty))
        assertEquals(4, empty.selStart)
    }

    @Test
    fun `gras et italique se cumulent et se retirent un par un`() {
        var t = "mot"
        var e = MarkdownEdits.toggleInline(t, 0, 3, Inline.ITALIC); t = MarkdownEdits.apply(t, e)
        assertEquals("*mot*", t)
        e = MarkdownEdits.toggleInline(t, e.selStart, e.selEnd, Inline.BOLD); t = MarkdownEdits.apply(t, e)
        assertEquals("***mot***", t)
        assertEquals(setOf(Inline.BOLD, Inline.ITALIC), MarkdownEdits.activeInline(t, e.selStart, e.selEnd))
        e = MarkdownEdits.toggleInline(t, e.selStart, e.selEnd, Inline.ITALIC); t = MarkdownEdits.apply(t, e)
        assertEquals("**mot**", t)
        e = MarkdownEdits.toggleInline(t, e.selStart, e.selEnd, Inline.BOLD); t = MarkdownEdits.apply(t, e)
        assertEquals("mot", t)
    }

    // ---- Blocs ---------------------------------------------------------------------------------

    @Test
    fun `les cases remplacent les puces et se retirent`() {
        val t = "- un\n- deux"
        val e = MarkdownEdits.toggleBlock(t, 0, t.length, BlockStyle.CHECK)
        val checks = MarkdownEdits.apply(t, e)
        assertEquals("- [ ] un\n- [ ] deux", checks)
        assertEquals("un\ndeux", MarkdownEdits.apply(checks, MarkdownEdits.toggleBlock(checks, 0, checks.length, BlockStyle.CHECK)))
    }

    @Test
    fun `une liste numerotee se compte`() {
        val t = "a\nb\nc"
        assertEquals("1. a\n2. b\n3. c", MarkdownEdits.apply(t, MarkdownEdits.toggleBlock(t, 0, t.length, BlockStyle.NUMBER)))
        val suite = "1. a\nb"
        assertEquals("1. a\n2. b", MarkdownEdits.apply(suite, MarkdownEdits.toggleBlock(suite, 5, 5, BlockStyle.NUMBER)))
    }

    @Test
    fun `le bouton titre fait le tour`() {
        var t = "Titre"
        val seen = ArrayList<String>()
        repeat(4) { t = MarkdownEdits.apply(t, MarkdownEdits.cycleHeading(t, t.length)); seen += t }
        assertEquals(listOf("# Titre", "## Titre", "### Titre", "Titre"), seen)
    }

    @Test
    fun `cocher puis decocher`() {
        val t = "x\n- [ ] lait"
        val on = MarkdownEdits.apply(t, MarkdownEdits.toggleCheck(t, 5)!!)
        assertEquals("x\n- [x] lait", on)
        assertEquals(t, MarkdownEdits.apply(on, MarkdownEdits.toggleCheck(on, 5)!!))
        assertNull(MarkdownEdits.toggleCheck(t, 0))
    }

    // ---- Listes qui se continuent ------------------------------------------------------------

    private fun enter(before: String, cursor: Int): String {
        val typed = before.substring(0, cursor) + "\n" + before.substring(cursor)
        val e = MarkdownEdits.continueList(typed, cursor) ?: return typed
        return MarkdownEdits.apply(typed, e)
    }

    @Test
    fun `entree continue la liste`() {
        assertEquals("- [x] lait\n- [ ] ", enter("- [x] lait", 10))
        assertEquals("  * a\n  * ", enter("  * a", 5))
        assertEquals("9. a\n10. ", enter("9. a", 4))
        assertEquals("> a\n> ", enter("> a", 3))
        assertEquals("texte\n", enter("texte", 5))
    }

    @Test
    fun `entree sur un element vide termine la liste`() {
        assertEquals("- a\n", enter("- a\n- ", 6))
        assertEquals("- [ ] a\n", enter("- [ ] a\n- [ ] ", 14))
    }

    @Test
    fun `retour arriere sur un prefixe le retire entier`() {
        // « - [ ] » : on efface l'espace final du préfixe, il reste « - [ ]mot »
        val before = "- [ ] mot"
        val after = "- [ ]mot"
        val e = MarkdownEdits.afterBackspace(after, before, 0, 5)!!
        assertEquals("mot", MarkdownEdits.apply(after, e))
        // Effacer ailleurs ne touche à rien
        assertNull(MarkdownEdits.afterBackspace("- [ ] mt", before, 0, 7))
    }

    // ---- Annuler / rétablir ------------------------------------------------------------------

    @Test
    fun `les frappes d un mot se fondent`() {
        val h = EditHistory()
        var t = 0L
        "bonjour".forEachIndexed { i, c -> h.record(i, "", c.toString(), t); t += 100 }
        h.record(7, "", " ", t); t += 100
        "toi".forEachIndexed { i, c -> h.record(8 + i, "", c.toString(), t); t += 100 }
        assertEquals("toi", h.undo()!!.inserted)
        assertEquals(" ", h.undo()!!.inserted)
        assertEquals("bonjour", h.undo()!!.inserted)
        assertFalse(h.canUndo)
        assertEquals("bonjour", h.redo()!!.inserted)
        assertTrue(h.canRedo)
    }

    @Test
    fun `le clavier qui remplace le mot en cours reste une frappe`() {
        val h = EditHistory()
        h.record(0, "", "b", 0)
        h.record(0, "b", "bo", 50)
        h.record(0, "bo", "bon", 100)
        val c = h.undo()!!
        assertEquals(0, c.start)
        assertEquals("bon", c.inserted)
        assertFalse(h.canUndo)
    }

    @Test
    fun `une pause ferme le pas`() {
        val h = EditHistory()
        h.record(0, "", "a", 0)
        h.record(1, "", "b", 5_000)
        assertEquals("b", h.undo()!!.inserted)
        assertEquals("a", h.undo()!!.inserted)
    }
}
