package com.Atom2Universe.app.science.biology

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test

class AnatomyModelReaderTest {
    // GLB header + JSON chunk containing {} and padding. Total: 24 bytes.
    private val fixture = byteArrayOf(0x67, 0x6c, 0x54, 0x46, 2, 0, 0, 0, 24, 0, 0, 0,
        4, 0, 0, 0, 0x4a, 0x53, 0x4f, 0x4e, 0x7b, 0x7d, 0x20, 0x20)

    private fun gzip(bytes: ByteArray) = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()

    @Test fun fragmentBoundaryInsideTheHeaderReassemblesExactBytesAndClosesStreams() {
        val parts = mapOf("first" to gzip(fixture.copyOfRange(0, 7)), "second" to gzip(fixture.copyOfRange(7, 24)))
        val closed = mutableSetOf<String>()
        val buffer = AnatomyModelReader.read(parts.keys.toList(), 24) { name ->
            object : ByteArrayInputStream(parts.getValue(name)) {
                override fun close() { closed.add(name); super.close() }
            }
        }
        assertTrue(buffer.isDirect)
        assertEquals(0, buffer.position())
        assertArrayEquals(fixture, ByteArray(buffer.remaining()).also(buffer::get))
        assertEquals(parts.keys, closed)
    }

    @Test fun truncatedModelIsRejected() {
        try {
            AnatomyModelReader.read(listOf("part"), 24) { gzip(fixture.copyOf(23)).inputStream() }
            fail("A truncated GLB must not reach Filament")
        } catch (_: EOFException) { }
    }

    @Test fun unexpectedTrailingBytesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AnatomyModelReader.read(listOf("part"), 24) { gzip(fixture + byteArrayOf(0)).inputStream() }
        }
    }

    @Test fun catalogueAndGlbLengthMustAgree() {
        assertThrows(IllegalArgumentException::class.java) {
            AnatomyModelReader.read(listOf("part"), 25) { gzip(fixture).inputStream() }
        }
    }

    @Test fun cancelledLoadingClosesCurrentFragmentWithoutOpeningTheNext() {
        val size = 256 * 1024
        val model = ByteArray(size).also { java.util.Random(42).nextBytes(it) }
        fixture.copyInto(model)
        ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN).putInt(8, size)
        val opened = mutableListOf<String>()
        val closed = mutableListOf<String>()
        var bytesRead = 0
        assertThrows(CancellationException::class.java) {
            AnatomyModelReader.read(listOf("first", "second"), size, checkActive = {
                if (bytesRead > 64 * 1024) throw CancellationException()
            }) { name ->
                opened += name
                object : ByteArrayInputStream(gzip(model.copyOfRange(0, size / 2))) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        return super.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
                    }
                    override fun close() { closed += name; super.close() }
                }
            }
        }
        assertEquals(listOf("first"), opened)
        assertEquals(opened, closed)
    }

    @Test fun cancellationBeforeReadingOpensNoAssets() {
        assertThrows(CancellationException::class.java) {
            AnatomyModelReader.read(listOf("part"), fixture.size, checkActive = { throw CancellationException() }) {
                fail("A cancelled load must not open its first asset")
                fixture.inputStream()
            }
        }
    }
}
