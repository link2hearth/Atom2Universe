package com.Atom2Universe.app.sf2creator

import com.Atom2Universe.app.sf2creator.data.db.Sf2ProjectDatabase
import com.Atom2Universe.app.sf2creator.reader.Sf2InfoField
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedModulator
import com.Atom2Universe.app.sf2creator.reader.Sf2Reader
import com.Atom2Universe.app.sf2creator.writer.Sf2Document
import com.Atom2Universe.app.sf2creator.writer.Sf2Document.Modulator
import com.Atom2Universe.app.sf2creator.writer.Sf2Document.Zone
import com.Atom2Universe.app.sf2creator.writer.Sf2DocumentWriter
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sin
import org.junit.Assert.assertEquals

/** Banque SF2 de test et outils de comparaison partagés par les tests du créateur SF2. */
internal object Sf2TestBank {

    fun resetDatabase() {
        val field = Sf2ProjectDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        (field.get(null) as? Sf2ProjectDatabase)?.close()
        field.set(null, null)
    }

    // ==================== Fichier source ====================

    fun tone(frames: Int, period: Double, amplitude: Double = 12000.0) =
        ShortArray(frames) { (sin(2 * Math.PI * it / period) * amplitude).toInt().toShort() }

    fun pcm(data: ShortArray) = Sf2Document.Audio.Pcm(data.size, "test") { Sf2Document.LoadedPcm(data) }

    fun range(lo: Int, hi: Int) = (hi shl 8) or lo

    val pianoLeft = tone(2000, 40.0)
    val pianoRight = tone(2000, 41.0)
    val bell = tone(1500, 25.0, 8000.0)

    /**
     * Banque qui contient tout ce que l'ancien export perdait : zones globales (attaque à
     * 0 timecent = 1 s, scaleTuning 0, mode de boucle, plage, note de base), zone qui répète
     * une valeur par défaut pour écraser sa zone globale, décalage de début d'échantillon,
     * paire stéréo, modulateurs de zone de preset à côté d'une zone globale, zone globale de
     * preset sans générateur, correction de hauteur négative, champs INFO.
     */
    fun sourceDocument(): Sf2Document {
        val info = listOf(
            Sf2InfoField("ifil", byteArrayOf(2, 0, 1, 0)),
            Sf2DocumentWriter.textField("isng", "EMU8000"),
            Sf2DocumentWriter.textField("INAM", "Banque test"),
            Sf2DocumentWriter.textField("ICRD", "2024"),
            Sf2DocumentWriter.textField("IENG", "Quelqu'un"),
            Sf2DocumentWriter.textField("ICOP", "CC-BY"),
            Sf2DocumentWriter.textField("ICMT", "Commentaire accentué"),
            Sf2DocumentWriter.textField("ISFT", "Polyphone")
        )
        val samples = listOf(
            Sf2Document.Sample("Piano L", pcm(pianoLeft), 500, 1500, 44100, 60, -5, link = 1, type = 4),
            Sf2Document.Sample("Piano R", pcm(pianoRight), 500, 1500, 44100, 60, -5, link = 0, type = 2),
            Sf2Document.Sample("Cloche à vent", pcm(bell), 0, 0, 32000, 72, 3, link = 0, type = 1)
        )
        val piano = Sf2Document.Instrument(
            "Piano", listOf(
                Zone(listOf(43 to range(21, 108), 34 to 0, 48 to 100, 54 to 1, 56 to 0, 58 to 60), listOf(Modulator(0x0081, 6, 30, 0, 0))),
                Zone(listOf(43 to range(21, 64), 17 to -500, 53 to 0), emptyList()),
                Zone(listOf(43 to range(65, 108), 0 to 10, 17 to 500, 48 to 0, 53 to 1), listOf(Modulator(0x0502, 8, -1200, 0, 0)))
            )
        )
        val bellInstrument = Sf2Document.Instrument(
            "Cloche", listOf(
                Zone(listOf(44 to range(0, 100), 8 to 13500, 38 to -12000, 54 to 0, 53 to 2), emptyList())
            )
        )
        val presets = listOf(
            Sf2Document.Preset(
                "Piano", 0, 0, listOf(
                    Zone(emptyList(), listOf(Modulator(0x008B, 48, 960, 0, 0))),
                    Zone(listOf(43 to range(0, 127), 48 to 20, 41 to 0), listOf(Modulator(0x0081, 22, 100, 0, 0))),
                    Zone(listOf(44 to range(0, 90), 41 to 1), emptyList())
                )
            ),
            Sf2Document.Preset(
                "Cloches", 9, 128, listOf(
                    Zone(listOf(52 to 5), emptyList()),
                    Zone(listOf(41 to 1), emptyList())
                )
            )
        )
        return Sf2Document(info, presets, listOf(piano, bellInstrument), samples)
    }

    fun write(file: File): File = file.also { Sf2DocumentWriter.write(sourceDocument(), it) }

    // ==================== Comparaison ====================

    fun gens(map: Map<Int, Int>) = map.toSortedMap().entries.joinToString(" ") { "${it.key}=${it.value}" }
    fun mods(list: List<Sf2ParsedModulator>) =
        list.joinToString(" ") { "[${it.srcOper},${it.destOper},${it.amount},${it.amtSrcOper},${it.transOper}]" }

    /** Tout ce que Polyphone montre, indépendamment de l'ordre des éléments dans le fichier. */
    fun describe(file: File): Map<String, String> {
        val reader = Sf2Reader()
        val r = reader.parse(file)!!
        val out = linkedMapOf<String, String>()
        for (field in r.info.fields) out["info ${field.id}"] = String(field.data, Charsets.ISO_8859_1).trimEnd('\u0000')
        for (p in r.presets.sortedWith(compareBy({ it.bankNumber }, { it.programNumber }))) {
            val key = "preset ${p.bankNumber}:${p.programNumber}"
            out["$key name"] = p.name
            out["$key global"] = gens(p.globalGenerators) + " " + mods(p.globalModulators)
            p.zones.forEachIndexed { zi, z ->
                val inst = z.instrument!!
                out["$key zone $zi"] = gens(z.generators - 41) + " " + mods(z.modulators) + " -> " + inst.name
                out["$key zone $zi inst global"] = gens(inst.globalGenerators) + " " + mods(inst.globalModulators)
                inst.zones.forEachIndexed { ii, iz ->
                    val s = r.samples[iz.sampleIndex]
                    val link = if (s.sampleType == 1) "-" else r.samples[s.sampleLink].name
                    out["$key zone $zi inst zone $ii"] = gens(iz.generators - 53) + " " + mods(iz.modulators)
                    out["$key zone $zi inst zone $ii sample"] =
                        "${s.name} rate=${s.sampleRate} pitch=${s.originalPitch} corr=${s.pitchCorrection} " +
                            "type=${s.sampleType} link=$link loop=${s.loopStart - s.start}..${s.loopEnd - s.start} " +
                            "audio=${reader.extractSampleAudio(s)!!.contentHashCode()}"
                }
            }
        }
        return out
    }

    fun assertSameExcept(expected: Map<String, String>, actual: Map<String, String>, vararg changes: Pair<String, String>) {
        val wanted = LinkedHashMap(expected).apply { changes.forEach { (k, v) -> put(k, v) } }
        assertEquals(wanted.entries.joinToString("\n"), actual.entries.joinToString("\n"))
    }

    /** Chaque chunk RIFF annonce la taille qu'il occupe vraiment. */
    fun assertRiffSizesConsistent(file: File) {
        RandomAccessFile(file, "r").use { raf ->
            fun u32(): Long {
                val b = ByteArray(4); raf.readFully(b)
                return (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
                    ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
            }
            fun id(): String = ByteArray(4).also { raf.readFully(it) }.toString(Charsets.US_ASCII)
            assertEquals("RIFF", id())
            assertEquals(file.length() - 8, u32())
            assertEquals("sfbk", id())
            while (raf.filePointer < raf.length()) {
                assertEquals("LIST", id())
                val size = u32()
                val end = raf.filePointer + size
                id()
                while (raf.filePointer < end) {
                    id()
                    val sub = u32()
                    raf.seek(raf.filePointer + sub + (sub and 1))
                }
                assertEquals(end, raf.filePointer)
            }
        }
    }

}
