package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** L'abréviation des grands nombres du Donjon ([DungeonNumbers]), sans Android. */
class DungeonNumbersTest {

    private val en = listOf("k", "M", "B", "T", "Qa", "Qi")
    private val fr = listOf("k", "M", "Md", "Bn", "Bd", "Tn")

    private fun en(v: Long) = DungeonNumbers.format(v, Locale.US, en)
    private fun fr(v: Long) = DungeonNumbers.format(v, Locale.FRENCH, fr)

    @Test
    fun `sous 100 000 le nombre reste entier`() {
        assertEquals("0", en(0))
        assertEquals("12345", en(12_345))
        assertEquals("12345", fr(12_345))
        assertEquals("99999", fr(99_999))
        assertEquals("-500", en(-500))
    }

    @Test
    fun `trois chiffres significatifs`() {
        assertEquals("100k", en(100_000))
        assertEquals("290k", en(290_123))
        assertEquals("290k", fr(290_123))
        assertEquals("1.25M", en(1_250_000))
        assertEquals("1,25M", fr(1_250_000))
        assertEquals("12.5M", en(12_500_000))
        assertEquals("12,5M", fr(12_500_000))
        assertEquals("1.2M", en(1_200_000))
        assertEquals("1M", en(1_000_000))
    }

    @Test
    fun `l arrondi qui deborde passe au palier suivant`() {
        assertEquals("1M", en(999_999))
        assertEquals("1Md", fr(999_999_999))
        assertEquals("2.15B", en(Int.MAX_VALUE.toLong()))
        assertEquals("9.22Qi", en(Long.MAX_VALUE))
    }

    @Test
    fun `negatifs`() {
        assertEquals("-290k", en(-290_123))
        assertEquals(DungeonNumbers.Split(-1.25, 2, 1), DungeonNumbers.split(-1_250_000))
    }
}
