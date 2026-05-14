package com.Atom2Universe.app.notes.editor

import android.graphics.Typeface
import android.text.*
import android.text.style.*

object MarkdownParser {

    fun toSpanned(markdown: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val lines = markdown.split("\n")
        for ((index, line) in lines.withIndex()) {
            val processedLine = processLine(line)
            val start = sb.length
            sb.append(processedLine.text)
            processedLine.spans.forEach { (span, relStart, relEnd) ->
                sb.setSpan(span, start + relStart, start + relEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (index < lines.lastIndex) sb.append("\n")
        }
        return sb
    }

    private data class SpanInfo(val span: Any, val start: Int, val end: Int)
    private data class ProcessedLine(val text: String, val spans: List<SpanInfo>)

    private fun processLine(line: String): ProcessedLine {
        val spans = mutableListOf<SpanInfo>()

        // Headers
        val h3Match = Regex("^### (.+)$").matchEntire(line)
        val h2Match = Regex("^## (.+)$").matchEntire(line)
        val h1Match = Regex("^# (.+)$").matchEntire(line)
        val quoteMatch = Regex("^> (.+)$").matchEntire(line)
        val bulletMatch = Regex("^[*-] (.+)$").matchEntire(line)

        val text = when {
            h1Match != null -> {
                val content = h1Match.groupValues[1]
                spans.add(SpanInfo(RelativeSizeSpan(1.8f), 0, content.length))
                spans.add(SpanInfo(StyleSpan(Typeface.BOLD), 0, content.length))
                content
            }
            h2Match != null -> {
                val content = h2Match.groupValues[1]
                spans.add(SpanInfo(RelativeSizeSpan(1.4f), 0, content.length))
                spans.add(SpanInfo(StyleSpan(Typeface.BOLD), 0, content.length))
                content
            }
            h3Match != null -> {
                val content = h3Match.groupValues[1]
                spans.add(SpanInfo(RelativeSizeSpan(1.2f), 0, content.length))
                spans.add(SpanInfo(StyleSpan(Typeface.BOLD), 0, content.length))
                content
            }
            quoteMatch != null -> {
                val content = quoteMatch.groupValues[1]
                spans.add(SpanInfo(QuoteSpan(), 0, content.length))
                content
            }
            bulletMatch != null -> {
                val content = "• ${bulletMatch.groupValues[1]}"
                content
            }
            else -> line
        }

        val inlineSpans = parseInline(text)
        spans.addAll(inlineSpans)
        return ProcessedLine(text, spans)
    }

    private fun parseInline(text: String): List<SpanInfo> {
        val spans = mutableListOf<SpanInfo>()

        // Bold+Italic ***text***
        applyRegexSpans(text, Regex("\\*\\*\\*(.+?)\\*\\*\\*")) { start, end ->
            spans.add(SpanInfo(StyleSpan(Typeface.BOLD_ITALIC), start, end))
        }
        // Bold **text**
        applyRegexSpans(text, Regex("\\*\\*(.+?)\\*\\*")) { start, end ->
            spans.add(SpanInfo(StyleSpan(Typeface.BOLD), start, end))
        }
        // Italic *text*
        applyRegexSpans(text, Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")) { start, end ->
            spans.add(SpanInfo(StyleSpan(Typeface.ITALIC), start, end))
        }
        // Underline __text__
        applyRegexSpans(text, Regex("__(.+?)__")) { start, end ->
            spans.add(SpanInfo(UnderlineSpan(), start, end))
        }
        // Strikethrough ~~text~~
        applyRegexSpans(text, Regex("~~(.+?)~~")) { start, end ->
            spans.add(SpanInfo(StrikethroughSpan(), start, end))
        }
        // Links [text](url)
        val linkRegex = Regex("\\[(.+?)\\]\\((.+?)\\)")
        linkRegex.findAll(text).forEach { match ->
            val linkText = match.groupValues[1]
            val url = match.groupValues[2]
            val start = match.range.first
            val end = start + linkText.length
            spans.add(SpanInfo(URLSpan(url), start, end))
        }

        return spans
    }

    private fun applyRegexSpans(text: String, regex: Regex, block: (Int, Int) -> Unit) {
        regex.findAll(text).forEach { match ->
            val inner = match.groupValues[1]
            val start = match.range.first
            block(start, start + inner.length)
        }
    }

    fun toMarkdown(spannable: Spanned): String {
        val sb = StringBuilder()
        val lines = spannable.toString().split("\n")
        var pos = 0
        for ((index, line) in lines.withIndex()) {
            val lineStart = pos
            val lineEnd = pos + line.length

            val sizeSpans = spannable.getSpans(lineStart, lineEnd, RelativeSizeSpan::class.java)
            val boldSpans = spannable.getSpans(lineStart, lineEnd, StyleSpan::class.java)
                .filter { it.style == Typeface.BOLD }
            val quoteSpans = spannable.getSpans(lineStart, lineEnd, QuoteSpan::class.java)

            var processedLine = line
            when {
                sizeSpans.isNotEmpty() && boldSpans.isNotEmpty() -> {
                    val size = sizeSpans[0].sizeChange
                    processedLine = when {
                        size >= 1.7f -> "# $line"
                        size >= 1.3f -> "## $line"
                        else -> "### $line"
                    }
                }
                quoteSpans.isNotEmpty() -> processedLine = "> $line"
            }

            processedLine = applyInlineMarkdown(spannable, lineStart, processedLine, line)
            sb.append(processedLine)
            if (index < lines.lastIndex) sb.append("\n")
            pos = lineEnd + 1
        }
        return sb.toString()
    }

    private fun applyInlineMarkdown(
        spannable: Spanned,
        lineStart: Int,
        processedLine: String,
        originalLine: String
    ): String {
        var result = processedLine
        val boldSpans = spannable.getSpans(lineStart, lineStart + originalLine.length, StyleSpan::class.java)
        boldSpans.forEach { span ->
            if (span.style == Typeface.BOLD) {
                val s = spannable.getSpanStart(span) - lineStart
                val e = spannable.getSpanEnd(span) - lineStart
                if (s >= 0 && e <= originalLine.length) {
                    val word = originalLine.substring(s, e)
                    result = result.replace(word, "**$word**")
                }
            }
        }
        return result
    }

    fun toPlainText(markdown: String): String {
        return markdown
            .replace(Regex("^#{1,6} ", RegexOption.MULTILINE), "")
            .replace(Regex("\\*\\*\\*(.+?)\\*\\*\\*"), "$1")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "$1")
            .replace(Regex("__(.+?)__"), "$1")
            .replace(Regex("~~(.+?)~~"), "$1")
            .replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")
            .replace(Regex("^> ", RegexOption.MULTILINE), "")
            .replace(Regex("^[*-] ", RegexOption.MULTILINE), "")
            .trim()
    }
}
