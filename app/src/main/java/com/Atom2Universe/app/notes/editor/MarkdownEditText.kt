package com.Atom2Universe.app.notes.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatEditText
import com.Atom2Universe.app.notes.editor.MarkdownSyntax.Block
import com.Atom2Universe.app.notes.editor.MarkdownSyntax.Kind
import kotlin.math.abs

/**
 * Le champ de texte des notes : le texte saisi est le Markdown enregistré, mis en forme en direct
 * (voir [MarkdownSyntax]). Il ajoute :
 * - la mise en forme de chaque ligne touchée, à chaque frappe ;
 * - les listes qui se continuent à l'appui sur Entrée, et le préfixe effacé d'un seul retour arrière ;
 * - les cases qu'on coche du doigt, les liens qu'on ouvre d'un appui ;
 * - annuler / rétablir ([EditHistory]).
 */
class MarkdownEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {

    var palette = MdPalette(currentTextColor, 0xFF4F8CFF.toInt(), 0xFFFFFFFF.toInt())
        set(value) {
            field = value
            setTextColor(value.text)
            setHintTextColor(value.marker)
            restyle(0, length())
        }

    val history = EditHistory()

    /** Un appui sur `[[Titre]]`. */
    var onWikiLink: ((String) -> Unit)? = null
    /** Un appui sur un lien web. */
    var onUrl: ((String) -> Unit)? = null
    /** Le curseur, la sélection ou l'historique ont changé : la barre d'outils se met à jour. */
    var onStateChanged: (() -> Unit)? = null

    /** Vrai pendant nos propres remplacements : pas d'automatisme de liste en cascade. */
    private var applying = false
    /** Faux pendant annuler / rétablir et le chargement : ces changements ne s'enregistrent pas. */
    private var recording = true

    private var removed = ""
    private var dirtyStart = -1
    private var dirtyEnd = -1
    private var newlineAt = -1
    private var backspaceLine: String? = null
    private var backspaceLineStart = 0
    private var backspaceAt = 0

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            removed = s.subSequence(start, start + count).toString()
            backspaceLine = null
            if (!applying && count == 1 && after == 0) {
                val ls = MarkdownEdits.lineStart(s, start)
                backspaceLine = s.subSequence(ls, MarkdownEdits.lineEnd(s, start)).toString()
                backspaceLineStart = ls
                backspaceAt = start
            }
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (recording) history.record(start, removed, s.subSequence(start, start + count).toString(), SystemClock.uptimeMillis())
            dirtyStart = if (dirtyStart < 0) start else minOf(dirtyStart, start)
            dirtyEnd = maxOf(dirtyEnd, start + count)
            newlineAt = if (!applying && count - before == 1 && count >= 1 && s[start + count - 1] == '\n') start + count - 1 else -1
        }

        override fun afterTextChanged(s: Editable) {
            if (dirtyStart >= 0) {
                val from = dirtyStart
                val to = dirtyEnd
                dirtyStart = -1; dirtyEnd = -1
                // Au chargement, le texte arrive déjà mis en forme : rien à refaire.
                if (!loading) restyle(from, to)
            }
            if (applying) return
            val nl = newlineAt
            newlineAt = -1
            val bl = backspaceLine
            backspaceLine = null
            val edit = when {
                nl >= 0 -> MarkdownEdits.continueList(s.toString(), nl)
                bl != null -> MarkdownEdits.afterBackspace(s.toString(), bl, backspaceLineStart, backspaceAt)
                else -> null
            }
            if (edit != null) applyEdit(edit)
            stateChanged()
        }
    }

    init {
        addTextChangedListener(watcher)
    }

    // ---- Contenu -------------------------------------------------------------------------------

    /** Vrai pendant [setMarkdown] : le texte posé porte déjà sa mise en forme. */
    private var loading = false

    /**
     * Charge une note : le texte entier, sans historique. La mise en forme est posée sur une copie
     * hors de l'écran, puis le texte entre d'un seul coup : un seul calcul, une seule mise en page.
     */
    fun setMarkdown(markdown: String) {
        val styled = SpannableStringBuilder(markdown)
        styleLines(styled, 0, styled.length)
        recording = false
        applying = true
        loading = true
        setText(styled)
        loading = false
        applying = false
        recording = true
        history.clear()
        stateChanged()
    }

    val markdown: String get() = text?.toString() ?: ""

    /** Applique un geste de [MarkdownEdits] : un pas d'historique comme un autre. */
    fun applyEdit(e: MarkdownEdits.Edit) {
        val ed = text ?: return
        applying = true
        ed.replace(e.start.coerceIn(0, ed.length), e.end.coerceIn(0, ed.length), e.text)
        applying = false
        setSelection(e.selStart.coerceIn(0, length()), e.selEnd.coerceIn(0, length()))
        history.seal()
        stateChanged()
    }

    fun insertAtCursor(s: String) {
        val a = selectionStart.coerceAtLeast(0)
        val b = selectionEnd.coerceAtLeast(0)
        applyEdit(MarkdownEdits.Edit(minOf(a, b), maxOf(a, b), s, minOf(a, b) + s.length))
    }

    fun undo() {
        val c = history.undo() ?: return
        replaceQuietly(c.start, c.start + c.inserted.length, c.removed)
        setSelection((c.start + c.removed.length).coerceIn(0, length()))
        stateChanged()
    }

    fun redo() {
        val c = history.redo() ?: return
        replaceQuietly(c.start, c.start + c.removed.length, c.inserted)
        setSelection((c.start + c.inserted.length).coerceIn(0, length()))
        stateChanged()
    }

    private fun replaceQuietly(start: Int, end: Int, s: String) {
        val ed = text ?: return
        recording = false
        applying = true
        ed.replace(start.coerceIn(0, ed.length), end.coerceIn(0, ed.length), s)
        applying = false
        recording = true
        history.seal()
    }

    // ---- Apparence -----------------------------------------------------------------------------

    fun setFontFamily(name: String) {
        typeface = when (name) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            "default" -> Typeface.DEFAULT
            else -> Typeface.create(name, Typeface.NORMAL) ?: Typeface.DEFAULT
        }
    }

    /** Repose la mise en forme des lignes qui touchent [from, to]. */
    private fun restyle(from: Int, to: Int) {
        val ed = text ?: return
        styleLines(ed, from, to)
    }

    /**
     * Met en forme les lignes de [text] qui touchent [from, to]. Une ligne dont les mises en forme sont
     * déjà exactement celles qu'il faut (mêmes dessins, mêmes plages, même ordre) n'est pas touchée :
     * chaque retrait ou pose relance la mise en page du paragraphe. Au moindre écart, tout est reposé.
     */
    private fun styleLines(text: Spannable, from: Int, to: Int) {
        val ls = MarkdownEdits.lineStart(text, from.coerceIn(0, text.length))
        val le = MarkdownEdits.lineEnd(text, to.coerceIn(0, text.length))
        var start = ls
        while (start <= le) {
            val end = MarkdownEdits.lineEnd(text, start)
            val wanted = if (end > start) MarkdownStyler.lineSpans(start, parse(text.substring(start, end)), palette) else emptyList()
            val existing = text.getSpans(start, end, MdSpan::class.java)
            if (!matches(text, existing, wanted)) {
                for (span in existing) text.removeSpan(span)
                for (p in wanted) text.setSpan(p.span, p.start, p.end, MarkdownStyler.FLAGS)
            }
            start = end + 1
        }
    }

    private fun matches(text: Spannable, existing: Array<MdSpan>, wanted: List<MarkdownStyler.Placed>): Boolean {
        if (existing.size != wanted.size) return false
        for (i in existing.indices) {
            val e = existing[i]
            val w = wanted[i]
            if (text.getSpanStart(e) != w.start || text.getSpanEnd(e) != w.end || !e.same(w.span)) return false
        }
        return true
    }

    /** La dernière ligne lue : la mise en forme et la barre d'outils lisent la même à chaque frappe. */
    private var lastLineText: String? = null
    private var lastLine: MarkdownSyntax.Line? = null

    private fun parse(line: String): MarkdownSyntax.Line {
        val cached = lastLine
        if (cached != null && line == lastLineText) return cached
        return MarkdownSyntax.parseLine(line).also { lastLineText = line; lastLine = it }
    }

    /** La ligne qui contient [pos] : la position de son début, et la ligne lue. */
    fun lineAt(pos: Int): Pair<Int, MarkdownSyntax.Line> {
        val ed = text ?: return 0 to MarkdownSyntax.parseLine("")
        val p = pos.coerceIn(0, ed.length)
        val ls = MarkdownEdits.lineStart(ed, p)
        return ls to parse(ed.substring(ls, MarkdownEdits.lineEnd(ed, p)))
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        stateChanged()
    }

    private var statePosted = false
    private val notifyState = Runnable {
        statePosted = false
        onStateChanged?.invoke()
    }

    /** Texte et curseur changent ensemble à chaque frappe : la barre d'outils n'est mise à jour qu'une fois. */
    private fun stateChanged() {
        // Appelé aussi par le constructeur de la vue, avant que nos champs existent : rien à faire alors.
        if (onStateChanged == null || statePosted) return
        statePosted = true
        post(notifyState)
    }

    // ---- Toucher : cases et liens --------------------------------------------------------------

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    /** Le geste en cours a commencé sur une case : il est à nous, le curseur ne bouge pas. */
    private var onCheckbox = -1

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; downTime = event.eventTime
                onCheckbox = checkboxAt(event.x, event.y)
                if (onCheckbox >= 0) return true
            }
            MotionEvent.ACTION_MOVE -> if (onCheckbox >= 0) return true
            MotionEvent.ACTION_CANCEL -> if (onCheckbox >= 0) { onCheckbox = -1; return true }
            MotionEvent.ACTION_UP -> {
                val tap = abs(event.x - downX) < touchSlop && abs(event.y - downY) < touchSlop &&
                    event.eventTime - downTime < ViewConfiguration.getLongPressTimeout()
                if (onCheckbox >= 0) {
                    val pos = onCheckbox
                    onCheckbox = -1
                    if (tap) MarkdownEdits.toggleCheck(markdown, pos)?.let { e ->
                        // La case change, le curseur reste où il était.
                        val keepS = selectionStart
                        val keepE = selectionEnd
                        applyEdit(e)
                        if (keepS >= 0) setSelection(keepS.coerceIn(0, length()), keepE.coerceIn(0, length()))
                    }
                    return true
                }
                val handled = super.onTouchEvent(event)
                if (tap) linkAt(event.x, event.y)?.let { (kind, data) ->
                    if (kind == Kind.WIKI) onWikiLink?.invoke(data) else onUrl?.invoke(data)
                }
                return handled
            }
        }
        return super.onTouchEvent(event)
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /** La position du début de ligne si (x, y) tombe sur une case à cocher, sinon -1. */
    private fun checkboxAt(x: Float, y: Float): Int {
        val layout = layout ?: return -1
        val ed = text ?: return -1
        val line = layout.getLineForVertical((y - totalPaddingTop + scrollY).toInt())
        val ls = layout.getLineStart(line)
        if (ls != MarkdownEdits.lineStart(ed, ls)) return -1 // suite d'une ligne coupée : pas de case
        val parsed = MarkdownSyntax.parseLine(ed.substring(ls, MarkdownEdits.lineEnd(ed, ls)))
        if (parsed.block != Block.CHECK) return -1
        val left = layout.getPrimaryHorizontal(ls + parsed.indent)
        val right = layout.getPrimaryHorizontal(ls + parsed.prefixEnd)
        val lx = x - totalPaddingLeft + scrollX
        val margin = textSize * 0.4f
        return if (lx >= left - margin && lx <= right + margin * 0.5f) ls else -1
    }

    /** Le lien sous (x, y), s'il y en a un : son type et son adresse ou titre. */
    private fun linkAt(x: Float, y: Float): Pair<Kind, String>? {
        val layout = layout ?: return null
        val ed = text ?: return null
        val line = layout.getLineForVertical((y - totalPaddingTop + scrollY).toInt())
        val lx = x - totalPaddingLeft + scrollX
        if (lx < layout.getLineLeft(line) || lx > layout.getLineRight(line)) return null
        val off = layout.getOffsetForHorizontal(line, lx)
        val ls = MarkdownEdits.lineStart(ed, off)
        val parsed = MarkdownSyntax.parseLine(ed.substring(ls, MarkdownEdits.lineEnd(ed, off)))
        val rel = off - ls
        val t = parsed.tokens.firstOrNull { (it.kind == Kind.LINK || it.kind == Kind.WIKI) && rel >= it.start && rel < it.end }
            ?: return null
        return t.kind to (t.data ?: return null)
    }
}
