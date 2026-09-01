package com.Atom2Universe.app.books

import java.text.BreakIterator
import java.util.Locale
import java.util.TreeSet

/** A TTS-sized slice whose offsets still refer to the original paragraph. */
internal data class TtsTextSegment(val start: Int, val end: Int, val text: String)

internal object TtsTextSegmenter {
    const val DEFAULT_MAX_WORDS = 500
    const val DEFAULT_MAX_CHARS = 3_800

    fun next(
        paragraph: String,
        fromOffset: Int = 0,
        maxWords: Int = DEFAULT_MAX_WORDS,
        maxChars: Int = DEFAULT_MAX_CHARS,
        locale: Locale = Locale.getDefault()
    ): TtsTextSegment? {
        require(maxWords > 0 && maxChars > 0)
        var start = fromOffset.coerceIn(0, paragraph.length)
        while (start < paragraph.length && paragraph[start].isWhitespace()) start++
        if (start >= paragraph.length) return null

        val charLimit = (start + maxChars).coerceAtMost(paragraph.length)
        var bestEnd = -1

        // Prefer the last complete sentence which satisfies both engine limits.
        for (boundary in sentenceBoundaries(paragraph, start, locale)) {
            if (boundary > charLimit) break
            val candidateEnd = trimEnd(paragraph, start, boundary)
            if (candidateEnd > start && wordCount(paragraph, start, candidateEnd) <= maxWords) {
                bestEnd = candidateEnd
            } else {
                break
            }
        }

        val end = if (bestEnd > start) bestEnd else fallbackBoundary(paragraph, start, charLimit, maxWords)
        return TtsTextSegment(start, end, paragraph.substring(start, end))
    }

    private fun fallbackBoundary(text: String, start: Int, charLimit: Int, maxWords: Int): Int {
        var words = 0
        var inWord = false
        var wordLimit = charLimit
        var i = start
        while (i < charLimit) {
            if (text[i].isWhitespace()) {
                if (inWord) {
                    words++
                    if (words >= maxWords) {
                        wordLimit = i
                        break
                    }
                }
                inWord = false
            } else {
                inWord = true
            }
            i++
        }

        val limit = wordLimit.coerceAtLeast(start + 1)
        // An exceptionally long sentence has no sentence boundary. Prefer a clause,
        // then a word boundary, so the Android engine is never given oversized text.
        for (index in limit - 1 downTo start + 1) {
            if (text[index] in charArrayOf(';', ':', ',', '—', '–') &&
                (index + 1 >= text.length || text[index + 1].isWhitespace())) return index + 1
        }
        if (limit < text.length && text[limit].isWhitespace()) return trimEnd(text, start, limit)
        for (index in limit - 1 downTo start + 1) {
            if (text[index].isWhitespace()) return trimEnd(text, start, index)
        }
        return limit
    }

    private fun sentenceBoundaries(text: String, start: Int, locale: Locale): Set<Int> {
        val boundaries = TreeSet<Int>()
        val iterator = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
        var boundary = iterator.following(start)
        while (boundary != BreakIterator.DONE) {
            boundaries += boundary
            boundary = iterator.next()
        }
        // Some engines reject a full stop followed by a lowercase word as a sentence
        // ending. EPUB punctuation is still a safer cut than an arbitrary word.
        for (i in start until text.lastIndex) {
            if (text[i] in charArrayOf('.', '!', '?', '…') && text[i + 1].isWhitespace()) {
                var end = i + 1
                while (end < text.length && text[end] in charArrayOf('"', '\'', '»', '”', ')', ']')) end++
                boundaries += end
            }
        }
        return boundaries
    }

    private fun wordCount(text: String, start: Int, end: Int): Int {
        var count = 0
        var inWord = false
        for (i in start until end) {
            if (text[i].isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                count++
                inWord = true
            }
        }
        return count
    }

    private fun trimEnd(text: String, start: Int, requestedEnd: Int): Int {
        var end = requestedEnd.coerceAtMost(text.length)
        while (end > start && text[end - 1].isWhitespace()) end--
        return end
    }
}
