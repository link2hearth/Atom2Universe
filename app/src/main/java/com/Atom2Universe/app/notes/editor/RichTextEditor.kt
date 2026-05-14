package com.Atom2Universe.app.notes.editor

import android.content.Context
import android.graphics.Typeface
import android.text.*
import android.text.style.*
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class RichTextEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private var isBold = false
    private var isItalic = false
    private var isUnderline = false
    private var isStrikethrough = false
    private var headerLevel = 0

    private val textWatcher = object : TextWatcher {
        private var beforeLength = 0
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            beforeLength = s?.length ?: 0
        }
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (count > 0 && s is Editable) {
                applyActiveStyles(s, start, start + count)
            }
        }
        override fun afterTextChanged(s: Editable?) {}
    }

    init {
        addTextChangedListener(textWatcher)
    }

    private fun applyActiveStyles(editable: Editable, start: Int, end: Int) {
        if (isBold) editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (isItalic) editable.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (isUnderline) editable.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (isStrikethrough) editable.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun toggleBold(): Boolean {
        isBold = !isBold
        toggleSpanOnSelection { StyleSpan(Typeface.BOLD) }
        return isBold
    }

    fun toggleItalic(): Boolean {
        isItalic = !isItalic
        toggleSpanOnSelection { StyleSpan(Typeface.ITALIC) }
        return isItalic
    }

    fun toggleUnderline(): Boolean {
        isUnderline = !isUnderline
        toggleSpanOnSelection { UnderlineSpan() }
        return isUnderline
    }

    fun toggleStrikethrough(): Boolean {
        isStrikethrough = !isStrikethrough
        toggleSpanOnSelection { StrikethroughSpan() }
        return isStrikethrough
    }

    fun setHeaderLevel(level: Int) {
        headerLevel = level
        val editable = editableText ?: return
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(start)
        val lineStart = editable.toString().lastIndexOf('\n', start - 1) + 1
        val lineEnd = editable.toString().indexOf('\n', end).let { if (it == -1) editable.length else it }

        editable.getSpans(lineStart, lineEnd, RelativeSizeSpan::class.java).forEach { editable.removeSpan(it) }
        editable.getSpans(lineStart, lineEnd, StyleSpan::class.java)
            .filter { it.style == Typeface.BOLD }.forEach { editable.removeSpan(it) }

        if (level > 0) {
            val size = when (level) { 1 -> 1.8f; 2 -> 1.4f; else -> 1.2f }
            editable.setSpan(RelativeSizeSpan(size), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            editable.setSpan(StyleSpan(Typeface.BOLD), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun toggleQuote() {
        val editable = editableText ?: return
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(start)
        val lineStart = editable.toString().lastIndexOf('\n', start - 1) + 1
        val lineEnd = editable.toString().indexOf('\n', end).let { if (it == -1) editable.length else it }
        val existing = editable.getSpans(lineStart, lineEnd, QuoteSpan::class.java)
        if (existing.isNotEmpty()) existing.forEach { editable.removeSpan(it) }
        else editable.setSpan(QuoteSpan(), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun <T : Any> toggleSpanOnSelection(spanFactory: () -> T) {
        val editable = editableText ?: return
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(start)
        if (start == end) return

        val spanClass = spanFactory()::class.java
        val existing = editable.getSpans(start, end, spanClass)
        if (existing.isNotEmpty()) existing.forEach { editable.removeSpan(it) }
        else editable.setSpan(spanFactory(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun setMarkdownContent(markdown: String) {
        removeTextChangedListener(textWatcher)
        setText(MarkdownParser.toSpanned(markdown))
        addTextChangedListener(textWatcher)
    }

    fun getMarkdownContent(): String = MarkdownParser.toMarkdown(editableText ?: SpannableString(""))

    fun getPlainTextContent(): String = MarkdownParser.toPlainText(getMarkdownContent())

    fun insertLink(text: String, url: String) {
        val editable = editableText ?: return
        val pos = selectionEnd.coerceAtLeast(0)
        val link = "[$text]($url)"
        editable.insert(pos, link)
    }

    fun insertBulletList() {
        val editable = editableText ?: return
        val pos = selectionEnd.coerceAtLeast(0)
        val insertion = if (pos == 0 || editable[pos - 1] == '\n') "• " else "\n• "
        editable.insert(pos, insertion)
    }

    fun insertTextAtCursor(text: String) {
        val editable = editableText ?: return
        val pos = selectionEnd.coerceAtLeast(0)
        editable.insert(pos, text)
    }

    fun setFontSizeSp(sizeSp: Int) {
        textSize = sizeSp.toFloat()
    }

    fun setFontFamily(fontName: String) {
        val tf = when (fontName) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.create(fontName, Typeface.NORMAL) ?: Typeface.DEFAULT
        }
        typeface = tf
    }

    fun isBoldActive() = isBold
    fun isItalicActive() = isItalic
    fun isUnderlineActive() = isUnderline
    fun isStrikethroughActive() = isStrikethrough
    fun getHeaderLevel() = headerLevel
}
