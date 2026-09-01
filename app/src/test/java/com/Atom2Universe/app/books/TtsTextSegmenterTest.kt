package com.Atom2Universe.app.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TtsTextSegmenterTest {
    @Test
    fun `long paragraph is cut at a sentence boundary under 500 words`() {
        val first = (1..300).joinToString(" ") { "mot$it" } + "."
        val second = (301..600).joinToString(" ") { "mot$it" } + "!"
        val paragraph = "$first $second"

        val segment = TtsTextSegmenter.next(paragraph, maxWords = 500, maxChars = 10_000, locale = Locale.FRENCH)!!

        assertEquals(first, segment.text)
        assertEquals(300, segment.text.split(Regex("\\s+")).size)
        assertEquals(paragraph.indexOf("mot301"), TtsTextSegmenter.next(paragraph, segment.end, 500, 10_000, Locale.FRENCH)!!.start)
    }

    @Test
    fun `short sentences are grouped without exceeding word limit`() {
        val paragraph = (1..120).joinToString(" ") { "Phrase $it contient quatre mots." }

        val segment = TtsTextSegmenter.next(paragraph, maxWords = 500, maxChars = 10_000, locale = Locale.FRENCH)!!

        assertTrue(segment.text.endsWith('.'))
        assertTrue(segment.text.split(Regex("\\s+")).size <= 500)
        assertTrue(segment.end < paragraph.length)
    }

    @Test
    fun `oversized sentence falls back to a word boundary`() {
        val paragraph = (1..620).joinToString(" ") { "mot$it" } + "."

        val segment = TtsTextSegmenter.next(paragraph, maxWords = 500, maxChars = 10_000, locale = Locale.FRENCH)!!

        assertEquals(500, segment.text.split(Regex("\\s+")).size)
        assertTrue(segment.end < paragraph.length)
    }
}
