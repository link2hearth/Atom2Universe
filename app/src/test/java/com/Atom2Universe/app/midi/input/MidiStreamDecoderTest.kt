package com.Atom2Universe.app.midi.input

import org.junit.Assert.assertEquals
import org.junit.Test

class MidiStreamDecoderTest {
    private val messages = mutableListOf<List<Int>>()
    private fun decoder() = MidiStreamDecoder { message -> messages += message.map { it.toInt() and 0xFF } }
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun notesAndPedalSurviveEveryPacketBoundaryAndOffsets() {
        val stream = bytes(0x92, 60, 100, 64, 85, 60, 0, 67, 90, 0xB2, 64, 127, 64, 0, 0x82, 67, 17)
        val expected = listOf(
            listOf(0x92, 60, 100), listOf(0x92, 64, 85), listOf(0x82, 60, 0),
            listOf(0x92, 67, 90), listOf(0xB2, 64, 127), listOf(0xB2, 64, 0), listOf(0x82, 67, 17)
        )
        val padded = bytes(0x94, 44, 33) + stream + bytes(0x85, 55, 66)
        for (split in 0..stream.size) {
            messages.clear()
            val decoder = decoder()
            decoder.receive(padded, 3, split)
            decoder.receive(padded, 3 + split, stream.size - split)
            assertEquals("packet split at $split", expected, messages)
        }
        messages.clear()
        val decoder = decoder()
        for (i in stream.indices) decoder.receive(stream, i, 1)
        assertEquals(expected, messages)
    }

    @Test
    fun realTimeBytesKeepPartialMessagesAndRunningStatus() {
        val stream = bytes(0xF8, 0x9C, 0xFA, 60, 0xF8, 100, 0xFE, 63, 0xFC, 0, 0xFF, 67, 80)
        decoder().receive(stream, 0, stream.size)
        assertEquals(listOf(listOf(0x9C, 60, 100), listOf(0x8C, 63, 0), listOf(0x9C, 67, 80)), messages)
    }

    @Test
    fun unsupportedMessagesAndExclusiveDataDoNotBecomeNotes() {
        val stream = bytes(
            0x91, 60, 0xC1, 12, 18, 0xA1, 60, 80, // Changement de programme et pression.
            0x91, 62, 90, 0xF2, 1, 2, 63, 80,     // System Common annule le running status.
            0xF0, 0x7D, 1, 0xF8, 2, 3, 0xF7, 60, 100,
            0xF0, 0x7D, 4, 0xB1, 64, 127          // Un nouveau statut peut aussi terminer SysEx.
        )
        val decoder = decoder()
        for (i in stream.indices) decoder.receive(stream, i, 1)
        assertEquals(listOf(listOf(0x91, 62, 90), listOf(0xB1, 64, 127)), messages)
    }

    @Test
    fun reconnectDropsOldFragmentsAndLatePacketsFromThePreviousPort() {
        val old = decoder()
        old.receive(bytes(0xB0, 64), 0, 2)
        old.close()
        val fresh = decoder()
        fresh.receive(bytes(127), 0, 1)
        old.receive(bytes(0, 0x90, 60, 100), 0, 4)
        fresh.receive(bytes(0x90, 72, 80), 0, 3)
        assertEquals(listOf(listOf(0x90, 72, 80)), messages)
    }
}
