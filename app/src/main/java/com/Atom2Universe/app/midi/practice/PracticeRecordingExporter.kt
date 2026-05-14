package com.Atom2Universe.app.midi.practice

import android.util.Log
import com.Atom2Universe.app.midi.sf2.Sf2FileCache
import com.Atom2Universe.app.midi.sf2.Sf2Synthesizer
import com.leff.midi.MidiFile
import com.leff.midi.MidiTrack
import com.leff.midi.event.NoteOn
import com.leff.midi.event.NoteOff
import com.leff.midi.event.ProgramChange
import com.leff.midi.event.meta.Tempo
import com.leff.midi.event.meta.TimeSignature
import java.io.File
import java.io.RandomAccessFile

/**
 * Exporte les notes enregistrees en mode pratique vers MIDI et/ou WAV.
 */
object PracticeRecordingExporter {

    private const val TAG = "PracticeRecordingExporter"
    private const val RESOLUTION = 480 // Ticks per quarter note
    private const val DEFAULT_BPM = 120f
    private const val SAMPLE_RATE = 44100
    private const val RENDER_BUFFER_SIZE = 1024

    /**
     * Exporte les notes enregistrees en fichier MIDI.
     *
     * @param notes liste des notes enregistrees
     * @param programNumber programme MIDI (instrument) 0-127
     * @param outputFile fichier de sortie .mid
     */
    fun exportToMidi(
        notes: List<ScheduledNote>,
        programNumber: Int,
        outputFile: File
    ): Boolean {
        if (notes.isEmpty()) return false

        try {
            // Tempo track
            val tempoTrack = MidiTrack()
            val timeSignature = TimeSignature()
            timeSignature.setTimeSignature(4, 4, TimeSignature.DEFAULT_METER, TimeSignature.DEFAULT_DIVISION)
            tempoTrack.insertEvent(timeSignature)

            val tempo = Tempo()
            tempo.setBpm(DEFAULT_BPM)
            tempoTrack.insertEvent(tempo)

            // Note track
            val noteTrack = MidiTrack()

            // Program change at tick 0
            noteTrack.insertEvent(ProgramChange(0L, 0, programNumber))

            // Normaliser les timestamps (commencer a 0)
            val minTime = notes.minOf { it.startTimeMs }

            // Convertir ms en ticks : ticks = (ms / 1000) * (BPM / 60) * resolution
            val ticksPerMs = (DEFAULT_BPM / 60f * RESOLUTION) / 1000f

            for (note in notes) {
                val startTick = ((note.startTimeMs - minTime) * ticksPerMs).toLong()
                val endTick = ((note.endTimeMs - minTime) * ticksPerMs).toLong()
                    .coerceAtLeast(startTick + 1) // Au minimum 1 tick de duree

                noteTrack.insertEvent(NoteOn(startTick, 0, note.note, note.velocity))
                noteTrack.insertEvent(NoteOff(endTick, 0, note.note, 0))
            }

            val tracks = arrayListOf(tempoTrack, noteTrack)
            val midiFile = MidiFile(RESOLUTION, tracks)

            outputFile.parentFile?.mkdirs()
            midiFile.writeToFile(outputFile)

            Log.d(TAG, "MIDI exported: ${outputFile.absolutePath} (${notes.size} notes)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export MIDI", e)
            return false
        }
    }

    /**
     * Exporte les notes enregistrees en fichier WAV via le synthétiseur SF2 offline.
     *
     * @param notes liste des notes enregistrees
     * @param programNumber programme MIDI (instrument) 0-127
     * @param sf2Path chemin vers le fichier SoundFont SF2
     * @param outputFile fichier de sortie .wav
     * @param onProgress callback de progression (0.0 - 1.0)
     * @return true si l'export a réussi
     */
    fun exportToWav(
        notes: List<ScheduledNote>,
        programNumber: Int,
        sf2Path: String,
        outputFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        if (notes.isEmpty()) return false

        try {
            // Charger le SF2
            val useMmap = Sf2FileCache.shouldUseMmap(sf2Path)
            val sf2File = if (useMmap) {
                Sf2FileCache.getMemoryMapped(sf2Path)
            } else {
                Sf2FileCache.get(sf2Path)
            }

            if (sf2File == null) {
                Log.e(TAG, "Failed to load SF2 file: $sf2Path")
                return false
            }

            val synth = Sf2Synthesizer(sf2File, SAMPLE_RATE)

            // Programme change
            synth.programChange(0, programNumber)

            // Normaliser les timestamps
            val minTime = notes.minOf { it.startTimeMs }

            // Construire la timeline d'événements triés
            data class MidiEvent(val timeMs: Long, val isNoteOn: Boolean, val note: Int, val velocity: Int)

            val events = mutableListOf<MidiEvent>()
            for (n in notes) {
                events.add(MidiEvent(n.startTimeMs - minTime, true, n.note, n.velocity))
                events.add(MidiEvent(n.endTimeMs - minTime, false, n.note, 0))
            }
            events.sortWith(compareBy({ it.timeMs }, { if (it.isNoteOn) 1 else 0 }))

            // Durée totale + 1 seconde de reverb tail
            val totalDurationMs = (notes.maxOf { it.endTimeMs } - minTime) + 1000L
            val totalSamples = (totalDurationMs * SAMPLE_RATE / 1000L).toInt()

            // Buffers de rendu
            val leftBuf = FloatArray(RENDER_BUFFER_SIZE)
            val rightBuf = FloatArray(RENDER_BUFFER_SIZE)

            outputFile.parentFile?.mkdirs()
            val raf = RandomAccessFile(outputFile, "rw")

            try {
                // Ecrire un header WAV placeholder (sera mis à jour à la fin)
                writeWavHeader(raf, 0)

                var samplesRendered = 0
                var eventIndex = 0

                while (samplesRendered < totalSamples) {
                    val samplesToRender = minOf(RENDER_BUFFER_SIZE, totalSamples - samplesRendered)
                    val currentTimeMs = samplesRendered.toLong() * 1000L / SAMPLE_RATE

                    // Dispatcher les événements MIDI jusqu'au temps courant
                    while (eventIndex < events.size && events[eventIndex].timeMs <= currentTimeMs) {
                        val evt = events[eventIndex]
                        if (evt.isNoteOn) {
                            synth.noteOn(0, evt.note, evt.velocity)
                        } else {
                            synth.noteOff(0, evt.note)
                        }
                        eventIndex++
                    }

                    // Rendu audio
                    leftBuf.fill(0f)
                    rightBuf.fill(0f)
                    synth.render(leftBuf, rightBuf, samplesToRender)

                    // Convertir float -> PCM 16-bit interleaved et écrire en streaming
                    val pcmBytes = ByteArray(samplesToRender * 4) // 2 channels * 2 bytes
                    for (i in 0 until samplesToRender) {
                        val leftSample = (leftBuf[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                        val rightSample = (rightBuf[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()

                        val offset = i * 4
                        pcmBytes[offset] = (leftSample.toInt() and 0xFF).toByte()
                        pcmBytes[offset + 1] = (leftSample.toInt() shr 8 and 0xFF).toByte()
                        pcmBytes[offset + 2] = (rightSample.toInt() and 0xFF).toByte()
                        pcmBytes[offset + 3] = (rightSample.toInt() shr 8 and 0xFF).toByte()
                    }
                    raf.write(pcmBytes, 0, samplesToRender * 4)

                    samplesRendered += samplesToRender
                    onProgress?.invoke(samplesRendered.toFloat() / totalSamples)
                }

                // Mettre à jour le header WAV avec la taille réelle
                val dataSize = samplesRendered * 4 // 2 channels * 2 bytes per sample
                raf.seek(0)
                writeWavHeader(raf, dataSize)

                Log.d(TAG, "WAV exported: ${outputFile.absolutePath} ($samplesRendered samples)")
                return true
            } finally {
                raf.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export WAV", e)
            return false
        }
    }

    /**
     * Ecrit un header WAV (RIFF/WAVE/fmt/data) dans un RandomAccessFile.
     */
    private fun writeWavHeader(raf: RandomAccessFile, dataSize: Int) {
        val channels = 2
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        // RIFF header
        raf.writeBytes("RIFF")
        raf.writeIntLE(36 + dataSize) // File size - 8
        raf.writeBytes("WAVE")

        // fmt sub-chunk
        raf.writeBytes("fmt ")
        raf.writeIntLE(16) // Sub-chunk size (PCM)
        raf.writeShortLE(1) // Audio format: PCM
        raf.writeShortLE(channels)
        raf.writeIntLE(SAMPLE_RATE)
        raf.writeIntLE(byteRate)
        raf.writeShortLE(blockAlign)
        raf.writeShortLE(bitsPerSample)

        // data sub-chunk
        raf.writeBytes("data")
        raf.writeIntLE(dataSize)
    }

    // Extensions pour écriture little-endian
    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
        write(value shr 16 and 0xFF)
        write(value shr 24 and 0xFF)
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
    }
}
