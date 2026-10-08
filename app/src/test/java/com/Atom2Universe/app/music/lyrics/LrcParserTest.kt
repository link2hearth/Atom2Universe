package com.Atom2Universe.app.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parse_supportsMultipleTimestampsPerLine() {
        val lyrics = """
            [00:01.00][00:02.50]Hello
            [00:03.00]World
        """.trimIndent()

        val parsed = LrcParser.parse(lyrics)

        assertEquals(3, parsed.size)
        assertEquals(listOf(1000L, 2500L, 3000L), parsed.map { it.timeMs })
        assertTrue(parsed.all { it.text == "Hello" || it.text == "World" })
        assertEquals(listOf("Hello", "Hello", "World"), parsed.map { it.text })
    }

    @Test
    fun extractPlainText_removesAllTimestampsFromLine() {
        val lyrics = """
            [00:10.00][00:20.00]Repeated line
            [00:30.00]Final line
        """.trimIndent()

        val plainText = LrcParser.extractPlainText(lyrics)

        assertEquals("Repeated line\nFinal line", plainText)
    }
}
