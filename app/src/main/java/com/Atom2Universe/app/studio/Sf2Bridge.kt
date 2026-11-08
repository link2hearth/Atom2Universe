package com.Atom2Universe.app.studio

import android.content.Context
import com.Atom2Universe.app.midi.repository.SettingsRepository
import com.Atom2Universe.app.midi.sf2.ProgramInfo
import com.Atom2Universe.app.midi.sf2.Sf2File
import com.Atom2Universe.app.midi.sf2.Sf2FileCache
import com.Atom2Universe.app.midi.sf2.Sf2Synthesizer
import com.Atom2Universe.app.studio.dsp.ExternalSynth
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Le pont entre le Studio et le lecteur SF2 maison de l'appli : il ouvre le soundfont que l'utilisateur a importé et fabrique,
 * pour chaque piste soundfont, un synthé qui rend dans les tampons du Studio (écoute et export).
 *
 * Le fichier est projeté en mémoire et partagé avec le lecteur de musique : plusieurs synthés ne le recopient pas.
 */
class Sf2Bridge private constructor(private val file: Sf2File) {

    /** Les instruments mélodiques du soundfont (les kits de batterie, banque 128, sont laissés de côté). */
    val programs: List<ProgramInfo> = file.getPrograms().filter { it.bank < 128 }

    val name: String get() = file.name

    /** Le premier instrument, ou le piano s'il y est : celui d'une piste neuve. */
    fun defaultProgram(): ProgramInfo? = programs.firstOrNull { it.bank == 0 && it.program == 0 } ?: programs.firstOrNull()

    /** À donner à [com.Atom2Universe.app.studio.engine.StudioEngine.sf2Factory] (et à l'export). */
    val factory: (sampleRate: Int, bank: Int, program: Int) -> ExternalSynth? = { rate, bank, program ->
        Source(Sf2Synthesizer(file, rate, MAX_VOICES).also {
            it.setReverbEnabled(false) // le Studio a sa réverbération et son écho : une piste ne doit pas avoir les siens
            it.setChorusEnabled(false)
            it.setForceSequentialRendering(true) // un seul fil : le rendu se fait dans le fil audio du Studio
        }).also { it.setProgram(bank, program) }
    }

    private class Source(private val synth: Sf2Synthesizer) : ExternalSynth {
        override fun noteOn(note: Int, velocity: Int) = synth.noteOn(0, note, velocity)

        override fun noteOff(note: Int) = synth.noteOff(0, note)

        override fun allOff() = synth.allNotesOff()

        override fun setProgram(bank: Int, program: Int) {
            synth.controlChange(0, Sf2Synthesizer.CC_BANK_SELECT_MSB, bank and 0x7F)
            synth.programChange(0, program)
        }

        override fun render(left: FloatArray, right: FloatArray, count: Int) = synth.render(left, right, count)

        override val activeVoices: Int get() = synth.getActiveVoiceCount()
    }

    companion object {
        private const val MAX_VOICES = 48

        /** Ouvre le soundfont configuré ; `null` s'il n'y en a pas. Peut prendre un instant : à appeler hors du fil de l'écran. */
        fun open(context: Context): Sf2Bridge? {
            val path = runBlocking { SettingsRepository(context.applicationContext).getSoundFontPath() }
            if (path.isNullOrBlank() || !File(path).exists()) return null
            val file = Sf2FileCache.getMemoryMapped(path) ?: return null
            return Sf2Bridge(file)
        }
    }
}
