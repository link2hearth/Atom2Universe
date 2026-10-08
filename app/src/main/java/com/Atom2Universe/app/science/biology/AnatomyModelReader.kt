package com.Atom2Universe.app.science.biology

import java.io.DataInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Enumeration
import java.util.zip.GZIPInputStream

/** Reconstitue exactement le GLB sans seconde copie complète dans le tas Java. */
internal object AnatomyModelReader {
    fun read(parts: List<String>, expectedBytes: Int, checkActive: () -> Unit = {}, open: (String) -> InputStream): ByteBuffer {
        require(parts.isNotEmpty() && expectedBytes in 12..512 * 1024 * 1024)
        checkActive()
        var closing = false
        val streams = object : Enumeration<InputStream> {
            private var index = 0
            override fun hasMoreElements() = !closing && index < parts.size
            override fun nextElement(): InputStream {
                if (!hasMoreElements()) throw NoSuchElementException()
                checkActive()
                val input = open(parts[index++])
                return try { GZIPInputStream(input) } catch (error: Exception) {
                    input.close()
                    throw error
                }
            }
        }
        val sequence = object : SequenceInputStream(streams) {
            override fun close() {
                // SequenceInputStream ouvre normalement les fragments restants pour
                // les fermer : inutile et coûteux après une annulation ou une erreur.
                closing = true
                super.close()
            }
        }
        return DataInputStream(sequence).use { stream ->
            val header = ByteArray(12).also(stream::readFully)
            val fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(fields.int == 0x46546C67 && fields.int == 2)
            require(fields.int == expectedBytes)
            checkActive()
            ByteBuffer.allocateDirect(expectedBytes).apply {
                put(header)
                val chunk = ByteArray(64 * 1024)
                while (hasRemaining()) {
                    checkActive()
                    val count = minOf(remaining(), chunk.size)
                    stream.readFully(chunk, 0, count)
                    put(chunk, 0, count)
                }
                checkActive()
                require(stream.read() == -1)
                flip()
            }
        }
    }
}
