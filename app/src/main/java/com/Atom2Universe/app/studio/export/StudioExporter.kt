package com.Atom2Universe.app.studio.export

import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.data.Track
import com.Atom2Universe.app.studio.dsp.ExternalSynth
import com.Atom2Universe.app.studio.dsp.SynthSample
import com.Atom2Universe.app.studio.engine.GM_DRUM_NOTES
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StepPattern
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.SynthBank
import com.Atom2Universe.app.studio.engine.TrackKind
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Sortie du Studio vers des fichiers : un enregistrement audio (WAV) rendu hors ligne par le même moteur que celui qu'on
 * entend, et un fichier MIDI (batterie sur le canal 10, une piste par instrument mélodique). Rien d'Android ici : testable sur le PC.
 *
 * Ce qui est exporté : le morceau s'il est activé, sinon le motif en cours répété [LOOPS_WITHOUT_SONG] fois.
 */
object StudioExporter {

    /** Nombre de tours d'un motif seul (sans morceau). */
    const val LOOPS_WITHOUT_SONG = 4

    /** Queue de réverbération et d'écho gardée après la dernière frappe, en secondes. */
    private const val TAIL_SECONDS = 2.5

    /** Les sons enregistrés au micro que le rendu doit jouer comme à l'écoute (percussion, synthé). */
    class Sounds(val perc: FloatArray? = null, val percRate: Int = 48_000, val synth: SynthSample? = null)

    /** Les motifs exportés, dans l'ordre. */
    fun plan(project: StudioProject): List<StepPattern> =
        if (project.songMode && project.chain.isNotEmpty()) project.chain.map { project.patterns[it] }
        else List(LOOPS_WITHOUT_SONG) { project.pattern }

    /** Nombre de trames (musique + queue) d'un rendu du projet à [sampleRate]. */
    fun frameCount(project: StudioProject, sampleRate: Int): Long {
        val steps = plan(project).sumOf { it.stepCount }
        val frames = sampleRate * 60.0 / project.bpm / 4.0 * steps
        return frames.roundToInt().toLong() + (TAIL_SECONDS * sampleRate).toLong()
    }

    /** Durée du rendu en secondes. */
    fun seconds(project: StudioProject): Double = frameCount(project, 48_000) / 48_000.0

    /**
     * Rend le projet en WAV 16 bits stéréo dans [out]. [onProgress] reçoit une valeur de 0 à 1 ; rendre `false` annule
     * (le rendu s'arrête et la fonction rend `false`).
     */
    fun renderWav(
        project: StudioProject,
        sampleRate: Int,
        out: OutputStream,
        /** Fabrique le synthé des pistes soundfont (sans lui, ces pistes restent muettes). */
        sf2Factory: ((sampleRate: Int, bank: Int, program: Int) -> ExternalSynth?)? = null,
        sounds: Sounds = Sounds(),
        onProgress: (Float) -> Boolean = { true },
    ): Boolean = StudioEngine(sampleRate).use { engine ->
        engine.sf2Factory = sf2Factory
        // Avant les pistes : les instruments fabriqués par la session reçoivent déjà ces sons
        engine.setPercSample(sounds.perc, sounds.percRate)
        engine.setSynthSample(sounds.synth)
        StudioSession(engine, project) // pousse tout le projet (sons, mixage, morceau) dans le moteur
        val total = frameCount(project, sampleRate)
        val music = total - (TAIL_SECONDS * sampleRate).toLong()
        writeWavHeader(out, sampleRate, total)

        val block = 1024
        val left = FloatArray(block)
        val right = FloatArray(block)
        val bytes = ByteArray(block * 4)
        engine.play()
        var done = 0L
        var stopped = false
        while (done < total) {
            if (!stopped && done >= music) {
                engine.stop()
                stopped = true
            }
            // On coupe le bloc à la fin de la musique pour que l'arrêt tombe au bon échantillon
            var n = minOf(block.toLong(), total - done).toInt()
            if (!stopped && done + n > music) n = (music - done).toInt().coerceAtLeast(1)
            engine.render(left, right, n)
            for (i in 0 until n) {
                putShort(bytes, i * 4, toPcm(left[i]))
                putShort(bytes, i * 4 + 2, toPcm(right[i]))
            }
            out.write(bytes, 0, n * 4)
            done += n
            if (!onProgress(done.toFloat() / total)) return@use false
        }
        out.flush()
        true
    }

    private fun toPcm(v: Float): Int = (v.coerceIn(-1f, 1f) * 32767f).roundToInt()

    private fun putShort(b: ByteArray, at: Int, v: Int) {
        b[at] = (v and 0xFF).toByte()
        b[at + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun writeWavHeader(out: OutputStream, sampleRate: Int, frames: Long) {
        val dataSize = frames * 4
        val h = ByteArray(44)
        fun str(at: Int, s: String) { for (i in s.indices) h[at + i] = s[i].code.toByte() }
        fun int(at: Int, v: Long) { for (i in 0 until 4) h[at + i] = ((v shr (8 * i)) and 0xFF).toByte() }
        fun short(at: Int, v: Int) { h[at] = (v and 0xFF).toByte(); h[at + 1] = ((v shr 8) and 0xFF).toByte() }
        str(0, "RIFF"); int(4, 36 + dataSize); str(8, "WAVE")
        str(12, "fmt "); int(16, 16); short(20, 1); short(22, 2); int(24, sampleRate.toLong())
        int(28, sampleRate * 4L); short(32, 4); short(34, 16)
        str(36, "data"); int(40, dataSize)
        out.write(h)
    }

    // ---- MIDI --------------------------------------------------------------------------------------------------

    private const val TICKS_PER_QUARTER = 480
    private const val TICKS_PER_STEP = TICKS_PER_QUARTER / 4

    /** Notes GM de la batterie : les mêmes que celles que jouent les kits du soundfont (le tom était 47 ici, 45 au moteur). */
    private val DRUM_NOTES = GM_DRUM_NOTES

    /** Programme GM donné aux pistes du synthé maison (« lead scie »). */
    private const val SYNTH_PROGRAM = 81

    // General MIDI n'encode pas les registres Yamaha. Choisir le timbre équivalent pour chaque son ROM.
    private val FM_GM_PROGRAMS = intArrayOf(81, 40, 24, 0, 73, 71, 68, 56, 16, 60, 81, 6, 11, 38, 32, 27)

    private class Ev(val tick: Int, val order: Int, val bytes: ByteArray)

    /** Une piste s'entend : ni cachée, ni muette, et en solo si une autre l'est. */
    private fun audible(project: StudioProject, t: Track): Boolean =
        !t.hidden && !t.muted && (project.tracks.none { it.solo } || t.solo)

    /** Le projet en fichier MIDI (format 1 : tempo, batterie, puis une piste par instrument mélodique audible). */
    fun midiBytes(project: StudioProject): ByteArray {
        val drums = ArrayList<Ev>()
        val melodic = project.tracks.filter { it.melodic && audible(project, it) }
        val channels = IntArray(melodic.size) { project.midiChannel(melodic[it].id) }
        val melodies = List(melodic.size) { ArrayList<Ev>() }
        val swingTicks = (project.swing.coerceIn(0f, 0.5f) * TICKS_PER_STEP).roundToInt()
        var offset = 0
        for (pattern in plan(project)) {
            fun tickOf(step: Int) = offset + step * TICKS_PER_STEP + if (step % 2 == 1) swingTicks else 0
            for (lane in pattern.lanes) {
                val track = project.trackOrNull(lane.instrument) ?: continue
                if (track.melodic || !audible(project, track)) continue
                val note = DRUM_NOTES[track.voice.coerceIn(0, DRUM_NOTES.size - 1)]
                for (s in 0 until pattern.stepCount) {
                    val v = lane.velocity[s]
                    if (v <= 0f) continue
                    val t = tickOf(s)
                    drums += Ev(t, 1, byteArrayOf((0x99).toByte(), note.toByte(), (v * 127f).roundToInt().coerceIn(1, 127).toByte()))
                    drums += Ev(t + TICKS_PER_STEP / 2, 0, byteArrayOf((0x89).toByte(), note.toByte(), 0))
                }
            }
            for (n in pattern.notes) {
                val index = melodic.indexOfFirst { it.id == n.track }
                if (index < 0) continue
                val ch = channels[index]
                val t = tickOf(n.step)
                val off = t + (n.length * TICKS_PER_STEP * 0.95f).roundToInt().coerceAtLeast(1)
                melodies[index] += Ev(t, 1, byteArrayOf((0x90 or ch).toByte(), n.pitch.coerceIn(0, 127).toByte(), (n.velocity * 127f).roundToInt().coerceIn(1, 127).toByte()))
                melodies[index] += Ev(off, 0, byteArrayOf((0x80 or ch).toByte(), n.pitch.coerceIn(0, 127).toByte(), 0))
            }
            offset += pattern.stepCount * TICKS_PER_STEP
        }
        // Au début de chaque piste mélodique : l'instrument (banque puis programme)
        for ((index, track) in melodic.withIndex()) {
            val ch = channels[index]
            val program = when {
                track.kind != TrackKind.SYNTH -> track.program
                track.synthBank == SynthBank.FM -> FM_GM_PROGRAMS[track.fmProgram.coerceIn(0, 15)]
                track.synthBank == SynthBank.STUDIO -> SYNTH_PROGRAM
                else -> track.program
            }
            if (track.kind == TrackKind.SF2) {
                melodies[index] += Ev(0, -3, byteArrayOf((0xB0 or ch).toByte(), 0, ((track.bank shr 7) and 0x7F).toByte()))
                melodies[index] += Ev(0, -2, byteArrayOf((0xB0 or ch).toByte(), 32, (track.bank and 0x7F).toByte()))
            }
            melodies[index] += Ev(0, -1, byteArrayOf((0xC0 or ch).toByte(), program.coerceIn(0, 127).toByte()))
        }

        val tempoMicros = (60_000_000.0 / project.bpm).roundToInt()
        val conductor = ByteArrayOutputStream().also { t ->
            vlq(t, 0); t.write(byteArrayOf(0xFF.toByte(), 0x51, 0x03, (tempoMicros shr 16).toByte(), (tempoMicros shr 8).toByte(), tempoMicros.toByte()))
            vlq(t, 0); t.write(byteArrayOf(0xFF.toByte(), 0x58, 0x04, 4, 2, 24, 8))
            vlq(t, 0); t.write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00))
        }
        val trackCount = 2 + melodic.size
        val file = ByteArrayOutputStream()
        file.write(byteArrayOf('M'.code.toByte(), 'T'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte(), 0, 0, 0, 6, 0, 1, (trackCount shr 8).toByte(), trackCount.toByte()))
        file.write(byteArrayOf((TICKS_PER_QUARTER shr 8).toByte(), TICKS_PER_QUARTER.toByte()))
        chunk(file, conductor.toByteArray())
        chunk(file, trackBytes(drums))
        for (m in melodies) chunk(file, trackBytes(m))
        return file.toByteArray()
    }

    private fun trackBytes(events: List<Ev>): ByteArray {
        val t = ByteArrayOutputStream()
        var last = 0
        for (e in events.sortedWith(compareBy<Ev> { it.tick }.thenBy { it.order })) {
            vlq(t, e.tick - last)
            t.write(e.bytes)
            last = e.tick
        }
        vlq(t, 0)
        t.write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00))
        return t.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, body: ByteArray) {
        out.write(byteArrayOf('M'.code.toByte(), 'T'.code.toByte(), 'r'.code.toByte(), 'k'.code.toByte()))
        out.write(byteArrayOf((body.size shr 24).toByte(), (body.size shr 16).toByte(), (body.size shr 8).toByte(), body.size.toByte()))
        out.write(body)
    }

    /** Entier à longueur variable du format MIDI. */
    private fun vlq(out: ByteArrayOutputStream, value: Int) {
        var v = value
        val stack = IntArray(5)
        var n = 0
        stack[n++] = v and 0x7F
        v = v shr 7
        while (v > 0) {
            stack[n++] = (v and 0x7F) or 0x80
            v = v shr 7
        }
        while (n > 0) out.write(stack[--n])
    }
}
