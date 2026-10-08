package com.Atom2Universe.app.midi.sf2

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Écrit un petit fichier SF2 en mémoire pour les tests (structure RIFF minimale mais conforme).
 * Les générateurs sont donnés comme paires (opérateur, valeur) dans l'ordre du fichier.
 */
internal class TestSf2Writer {

    class Sample(
        val name: String,
        val data: ShortArray,
        val loopStart: Int = 0,
        val loopEnd: Int = 0,
        val sampleRate: Int = 48000,
        val originalPitch: Int = 60
    )

    class Instrument(val name: String, val zones: List<List<Pair<Int, Int>>>)

    class Preset(val name: String, val bank: Int, val program: Int, val zones: List<List<Pair<Int, Int>>>)

    val samples = mutableListOf<Sample>()
    val instruments = mutableListOf<Instrument>()
    val presets = mutableListOf<Preset>()

    fun build(): ByteArray {
        val info = chunk("INAM", "Test".toByteArray() + ByteArray(4))
        val ifil = chunk("ifil", le(8) { putShort(2); putShort(1) })

        // smpl : chaque échantillon suivi de 46 zéros, comme le demande la norme
        val smplData = ByteArrayOutputStream()
        val starts = mutableListOf<Int>()
        var position = 0
        for (sample in samples) {
            starts.add(position)
            smplData.write(le(sample.data.size * 2) { sample.data.forEach { putShort(it) } })
            smplData.write(ByteArray(46 * 2))
            position += sample.data.size + 46
        }

        val phdr = ByteArrayOutputStream()
        val pbag = ByteArrayOutputStream()
        val pgen = ByteArrayOutputStream()
        var bagIndex = 0
        var genIndex = 0
        for (preset in presets) {
            phdr.write(le(38) { putName(preset.name); putShort(preset.program.toShort()); putShort(preset.bank.toShort()); putShort(bagIndex.toShort()); putInt(0); putInt(0); putInt(0) })
            for (zone in preset.zones) {
                pbag.write(le(4) { putShort(genIndex.toShort()); putShort(0) })
                for ((op, amount) in zone) {
                    pgen.write(le(4) { putShort(op.toShort()); putShort(amount.toShort()) })
                    genIndex++
                }
                bagIndex++
            }
        }
        phdr.write(le(38) { putName("EOP"); putShort(0); putShort(0); putShort(bagIndex.toShort()); putInt(0); putInt(0); putInt(0) })
        pbag.write(le(4) { putShort(genIndex.toShort()); putShort(0) })
        pgen.write(ByteArray(4))

        val inst = ByteArrayOutputStream()
        val ibag = ByteArrayOutputStream()
        val igen = ByteArrayOutputStream()
        bagIndex = 0
        genIndex = 0
        for (instrument in instruments) {
            inst.write(le(22) { putName(instrument.name); putShort(bagIndex.toShort()) })
            for (zone in instrument.zones) {
                ibag.write(le(4) { putShort(genIndex.toShort()); putShort(0) })
                for ((op, amount) in zone) {
                    igen.write(le(4) { putShort(op.toShort()); putShort(amount.toShort()) })
                    genIndex++
                }
                bagIndex++
            }
        }
        inst.write(le(22) { putName("EOI"); putShort(bagIndex.toShort()) })
        ibag.write(le(4) { putShort(genIndex.toShort()); putShort(0) })
        igen.write(ByteArray(4))

        val shdr = ByteArrayOutputStream()
        samples.forEachIndexed { i, sample ->
            val start = starts[i]
            shdr.write(le(46) {
                putName(sample.name)
                putInt(start); putInt(start + sample.data.size)
                putInt(start + sample.loopStart); putInt(start + sample.loopEnd)
                putInt(sample.sampleRate); put(sample.originalPitch.toByte()); put(0)
                putShort(0); putShort(1)
            })
        }
        shdr.write(le(46) { putName("EOS"); putInt(0); putInt(0); putInt(0); putInt(0); putInt(0); put(0); put(0); putShort(0); putShort(0) })

        val pdta = list(
            "pdta",
            chunk("phdr", phdr.toByteArray()), chunk("pbag", pbag.toByteArray()),
            chunk("pmod", ByteArray(10)), chunk("pgen", pgen.toByteArray()),
            chunk("inst", inst.toByteArray()), chunk("ibag", ibag.toByteArray()),
            chunk("imod", ByteArray(10)), chunk("igen", igen.toByteArray()),
            chunk("shdr", shdr.toByteArray())
        )
        val body = "sfbk".toByteArray() +
            list("INFO", ifil, info) +
            list("sdta", chunk("smpl", smplData.toByteArray())) +
            pdta
        return chunk("RIFF", body)
    }

    private fun chunk(id: String, data: ByteArray): ByteArray {
        val padded = if (data.size % 2 == 1) data + ByteArray(1) else data
        return id.toByteArray() + le(4) { putInt(data.size) } + padded
    }

    private fun list(type: String, vararg chunks: ByteArray): ByteArray =
        chunk("LIST", chunks.fold(type.toByteArray()) { acc, c -> acc + c })

    private fun le(size: Int, block: ByteBuffer.() -> Unit): ByteArray {
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.block()
        return buffer.array()
    }

    private fun ByteBuffer.putName(name: String) {
        val bytes = name.toByteArray().copyOf(20)
        put(bytes)
    }

    companion object {
        fun range(lo: Int, hi: Int): Int = lo or (hi shl 8)
    }
}
