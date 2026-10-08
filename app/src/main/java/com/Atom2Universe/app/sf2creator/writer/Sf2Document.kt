package com.Atom2Universe.app.sf2creator.writer

import com.Atom2Universe.app.sf2creator.reader.Sf2InfoField
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * A complete SoundFont, as the file stores it: INFO fields, presets and instruments made of
 * zones (generators + modulators), and samples with their header and audio.
 *
 * [Sf2DocumentWriter] writes it as is: nothing is added, reordered or left out.
 */
class Sf2Document(
    val info: List<Sf2InfoField>,
    val presets: List<Preset>,
    val instruments: List<Instrument>,
    val samples: List<Sample>
) {
    /** Generators in file order (sample or instrument reference last) and modulators. */
    class Zone(val generators: List<Pair<Int, Int>>, val modulators: List<Modulator>)

    data class Modulator(val srcOper: Int, val destOper: Int, val amount: Int, val amtSrcOper: Int, val transOper: Int)

    class Preset(val name: String, val program: Int, val bank: Int, val zones: List<Zone>)

    class Instrument(val name: String, val zones: List<Zone>)

    /**
     * A sample header. Loop points are relative to the sample start; [loopEnd] is the first
     * point after the loop, as in the file. [link] is the index in [samples] of the other
     * channel of a stereo pair.
     */
    class Sample(
        val name: String,
        val audio: Audio,
        val loopStart: Long,
        val loopEnd: Long,
        val sampleRate: Int,
        val originalPitch: Int,
        val pitchCorrection: Int,
        val link: Int,
        val type: Int
    )

    sealed class Audio {
        abstract val frames: Int

        /** 16-bit little-endian frames at [offset] in [path], copied byte for byte. */
        class FromFile(
            val path: String,
            val offset: Long,
            override val frames: Int,
            /** Offset of the matching sm24 bytes (24-bit samples), or -1. */
            val sm24Offset: Long = -1
        ) : Audio()

        /**
         * Frames loaded only while writing. [identity] stands for the content in the
         * fingerprint (file path, size, date).
         */
        class Pcm(override val frames: Int, val identity: String, val load: () -> LoadedPcm) : Audio()
    }

    /** Loaded frames; non-null loop points replace those of the sample header. */
    class LoadedPcm(val data: ShortArray, val loopStart: Long? = null, val loopEnd: Long? = null)

    /**
     * Digest of everything the file would contain, audio identified by its source. Two
     * documents with the same fingerprint produce the same file.
     */
    fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(text: String) = digest.update((text + "\n").toByteArray(Charsets.UTF_8))
        fun addZones(zones: List<Zone>) {
            for (zone in zones) {
                add("zone " + zone.generators.joinToString(" ") { "${it.first}=${it.second}" })
                for (m in zone.modulators) add("mod ${m.srcOper} ${m.destOper} ${m.amount} ${m.amtSrcOper} ${m.transOper}")
            }
        }
        for (field in info) {
            add("info ${field.id}")
            digest.update(field.data)
        }
        for (p in presets) {
            add("preset ${p.name}|${p.program}|${p.bank}")
            addZones(p.zones)
        }
        for (i in instruments) {
            add("instrument ${i.name}")
            addZones(i.zones)
        }
        for (s in samples) {
            val audio = when (val a = s.audio) {
                is Audio.FromFile -> "file ${a.path}@${a.offset}+${a.frames}/${a.sm24Offset}"
                is Audio.Pcm -> "pcm ${a.identity}+${a.frames}"
            }
            add("sample ${s.name}|$audio|${s.loopStart}|${s.loopEnd}|${s.sampleRate}|${s.originalPitch}|${s.pitchCorrection}|${s.link}|${s.type}")
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Writes an [Sf2Document] as an SF2 file, streaming the audio from its sources. */
object Sf2DocumentWriter {

    private const val PADDING_FRAMES = 46
    private const val COPY_BUFFER = 1 shl 20

    fun write(document: Sf2Document, output: File) {
        FileOutputStream(output).use { file ->
            val out = LittleEndianOutput(BufferedOutputStream(file, 1 shl 16))
            write(document, out)
            out.flush()
        }
    }

    private fun write(doc: Sf2Document, out: LittleEndianOutput) {
        val withSm24 = doc.samples.any { (it.audio as? Sf2Document.Audio.FromFile)?.sm24Offset?.let { o -> o >= 0 } == true }
        val info = infoFields(doc.info, withSm24)

        val infoSize = 4 + info.sumOf { 8 + padded(it.data.size) }
        val totalFrames = doc.samples.sumOf { it.audio.frames.toLong() + PADDING_FRAMES }
        val smplSize = totalFrames * 2
        val sm24Size = if (withSm24) totalFrames else 0L
        val sdtaSize = 4 + 8 + smplSize + if (withSm24) 8 + padded(sm24Size) else 0L

        val presetZones = doc.presets.sumOf { it.zones.size }
        val presetGens = doc.presets.sumOf { p -> p.zones.sumOf { it.generators.size } }
        val presetMods = doc.presets.sumOf { p -> p.zones.sumOf { it.modulators.size } }
        val instZones = doc.instruments.sumOf { it.zones.size }
        val instGens = doc.instruments.sumOf { i -> i.zones.sumOf { it.generators.size } }
        val instMods = doc.instruments.sumOf { i -> i.zones.sumOf { it.modulators.size } }
        val pdtaSize = 4 + 9 * 8 +
            38L * (doc.presets.size + 1) + 4L * (presetZones + 1) + 10L * (presetMods + 1) + 4L * (presetGens + 1) +
            22L * (doc.instruments.size + 1) + 4L * (instZones + 1) + 10L * (instMods + 1) + 4L * (instGens + 1) +
            46L * (doc.samples.size + 1)

        out.id("RIFF"); out.u32(4 + (8 + infoSize) + (8 + sdtaSize) + (8 + pdtaSize)); out.id("sfbk")

        // INFO
        out.id("LIST"); out.u32(infoSize); out.id("INFO")
        for (field in info) {
            out.id(field.id); out.u32(field.data.size.toLong()); out.bytes(field.data)
            if (field.data.size % 2 != 0) out.u8(0)
        }

        // sdta: audio, each sample followed by 46 zero frames
        out.id("LIST"); out.u32(sdtaSize); out.id("sdta")
        out.id("smpl"); out.u32(smplSize)
        val starts = LongArray(doc.samples.size)
        val loops = Array(doc.samples.size) { longArrayOf(doc.samples[it].loopStart, doc.samples[it].loopEnd) }
        SourceFiles().use { sources ->
            var position = 0L
            val buffer = ByteArray(COPY_BUFFER)
            for ((index, sample) in doc.samples.withIndex()) {
                starts[index] = position
                when (val audio = sample.audio) {
                    is Sf2Document.Audio.FromFile -> sources.copy(audio.path, audio.offset, audio.frames * 2L, out, buffer)
                    is Sf2Document.Audio.Pcm -> {
                        val loaded = audio.load()
                        val frames = audio.frames
                        for (i in 0 until frames) out.u16(if (i < loaded.data.size) loaded.data[i].toInt() else 0)
                        loaded.loopStart?.let { loops[index][0] = it }
                        loaded.loopEnd?.let { loops[index][1] = it }
                    }
                }
                out.zeros(PADDING_FRAMES * 2L)
                position += sample.audio.frames + PADDING_FRAMES
            }
            if (withSm24) {
                out.id("sm24"); out.u32(sm24Size)
                for (sample in doc.samples) {
                    val audio = sample.audio
                    if (audio is Sf2Document.Audio.FromFile && audio.sm24Offset >= 0) {
                        sources.copy(audio.path, audio.sm24Offset, audio.frames.toLong(), out, buffer)
                    } else {
                        out.zeros(audio.frames.toLong())
                    }
                    out.zeros(PADDING_FRAMES.toLong())
                }
                if (sm24Size % 2 != 0L) out.u8(0)
            }
        }

        // pdta
        out.id("LIST"); out.u32(pdtaSize); out.id("pdta")

        out.id("phdr"); out.u32(38L * (doc.presets.size + 1))
        var bag = 0
        for (p in doc.presets) {
            out.name(p.name); out.u16(p.program); out.u16(p.bank); out.u16(bag)
            out.u32(0); out.u32(0); out.u32(0)
            bag += p.zones.size
        }
        out.name("EOP"); out.u16(0); out.u16(0); out.u16(bag); out.u32(0); out.u32(0); out.u32(0)
        writeBags(out, "pbag", doc.presets.flatMap { it.zones })
        writeMods(out, "pmod", doc.presets.flatMap { it.zones })
        writeGens(out, "pgen", doc.presets.flatMap { it.zones })

        out.id("inst"); out.u32(22L * (doc.instruments.size + 1))
        bag = 0
        for (i in doc.instruments) {
            out.name(i.name); out.u16(bag)
            bag += i.zones.size
        }
        out.name("EOI"); out.u16(bag)
        writeBags(out, "ibag", doc.instruments.flatMap { it.zones })
        writeMods(out, "imod", doc.instruments.flatMap { it.zones })
        writeGens(out, "igen", doc.instruments.flatMap { it.zones })

        out.id("shdr"); out.u32(46L * (doc.samples.size + 1))
        for ((index, s) in doc.samples.withIndex()) {
            val start = starts[index]
            out.name(s.name)
            out.u32(start)
            out.u32(start + s.audio.frames)
            out.u32((start + loops[index][0]).coerceAtLeast(0))
            out.u32((start + loops[index][1]).coerceAtLeast(0))
            out.u32(s.sampleRate.toLong())
            out.u8(s.originalPitch)
            out.u8(s.pitchCorrection)
            out.u16(s.link)
            out.u16(s.type)
        }
        out.name("EOS"); out.zeros(46L - 20)
    }

    private fun writeBags(out: LittleEndianOutput, id: String, zones: List<Sf2Document.Zone>) {
        out.id(id); out.u32(4L * (zones.size + 1))
        var gen = 0
        var mod = 0
        for (zone in zones) {
            out.u16(gen); out.u16(mod)
            gen += zone.generators.size
            mod += zone.modulators.size
        }
        out.u16(gen); out.u16(mod)
    }

    private fun writeMods(out: LittleEndianOutput, id: String, zones: List<Sf2Document.Zone>) {
        val mods = zones.flatMap { it.modulators }
        out.id(id); out.u32(10L * (mods.size + 1))
        for (m in mods) {
            out.u16(m.srcOper); out.u16(m.destOper); out.u16(m.amount); out.u16(m.amtSrcOper); out.u16(m.transOper)
        }
        out.zeros(10)
    }

    private fun writeGens(out: LittleEndianOutput, id: String, zones: List<Sf2Document.Zone>) {
        val gens = zones.flatMap { it.generators }
        out.id(id); out.u32(4L * (gens.size + 1))
        for ((oper, amount) in gens) {
            out.u16(oper); out.u16(amount)
        }
        out.zeros(4)
    }

    /**
     * INFO fields in the order the standard wants (ifil first), with the mandatory ones
     * present and the version raised to 2.04 when 24-bit data is written.
     */
    private fun infoFields(fields: List<Sf2InfoField>, withSm24: Boolean): List<Sf2InfoField> {
        val ifil = fields.firstOrNull { it.id == "ifil" && it.data.size >= 4 }
        val major = ifil?.let { (it.data[0].toInt() and 0xFF) or ((it.data[1].toInt() and 0xFF) shl 8) } ?: 2
        val minor = ifil?.let { (it.data[2].toInt() and 0xFF) or ((it.data[3].toInt() and 0xFF) shl 8) } ?: 1
        val version = if (withSm24 && major == 2 && minor < 4) 2 to 4 else major to minor
        val result = mutableListOf(
            Sf2InfoField("ifil", byteArrayOf(
                version.first.toByte(), (version.first shr 8).toByte(),
                version.second.toByte(), (version.second shr 8).toByte()
            ))
        )
        if (fields.none { it.id == "isng" }) result.add(textField("isng", "EMU8000"))
        if (fields.none { it.id == "INAM" }) result.add(textField("INAM", "Untitled"))
        result.addAll(fields.filter { it.id != "ifil" })
        return result
    }

    /** INFO text: zero-terminated, padded to an even size. */
    fun textField(id: String, text: String): Sf2InfoField {
        val bytes = text.toByteArray(Charsets.ISO_8859_1).let { if (it.size > 255) it.copyOf(255) else it }
        return Sf2InfoField(id, bytes.copyOf(padded(bytes.size + 1).toInt()))
    }

    private fun padded(size: Int): Long = (size + (size and 1)).toLong()
    private fun padded(size: Long): Long = size + (size and 1)

    /** Source files opened once for the whole export. */
    private class SourceFiles : AutoCloseable {
        private val open = HashMap<String, RandomAccessFile>()

        fun copy(path: String, offset: Long, size: Long, out: LittleEndianOutput, buffer: ByteArray) {
            val raf = open.getOrPut(path) { RandomAccessFile(path, "r") }
            raf.seek(offset)
            var remaining = size
            while (remaining > 0) {
                val read = raf.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                if (read <= 0) {
                    // Truncated source: keep the layout valid with silence
                    out.zeros(remaining)
                    return
                }
                out.bytes(buffer, read)
                remaining -= read
            }
        }

        override fun close() = open.values.forEach { it.close() }
    }

    private class LittleEndianOutput(private val out: OutputStream) {
        private val zeros = ByteArray(4096)

        fun id(id: String) = out.write(id.toByteArray(Charsets.US_ASCII), 0, 4)
        fun u8(value: Int) = out.write(value and 0xFF)
        fun u16(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }
        fun u32(value: Long) {
            u16((value and 0xFFFF).toInt())
            u16(((value shr 16) and 0xFFFF).toInt())
        }
        fun bytes(data: ByteArray, length: Int = data.size) = out.write(data, 0, length)
        fun zeros(count: Long) {
            var remaining = count
            while (remaining > 0) {
                val n = minOf(remaining, zeros.size.toLong()).toInt()
                out.write(zeros, 0, n)
                remaining -= n
            }
        }
        /** 20-byte name, zero-filled (a 20-character name has no terminator, as the standard allows). */
        fun name(name: String) {
            val bytes = name.toByteArray(Charsets.ISO_8859_1)
            val length = minOf(bytes.size, 20)
            out.write(bytes, 0, length)
            zeros((20 - length).toLong())
        }
        fun flush() = out.flush()
    }
}
