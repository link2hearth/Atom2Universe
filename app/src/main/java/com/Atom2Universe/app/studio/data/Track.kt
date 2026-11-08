package com.Atom2Universe.app.studio.data

import com.Atom2Universe.app.studio.dsp.SynthPatch
import com.Atom2Universe.app.studio.dsp.SynthPresets
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.TrackKind
import com.Atom2Universe.app.studio.engine.TrackMix
import com.Atom2Universe.app.studio.engine.TrackSpec

/**
 * Une piste du projet : un son de batterie ([TrackKind.DRUM], une ligne de pas), le synthé maison ([TrackKind.SYNTH]) ou
 * un instrument de soundfont ([TrackKind.SF2]) ; les deux derniers jouent des notes. [id] relie la piste à ses pas et à ses
 * notes dans les motifs. Chaque piste a son mixage, son éventuel arpège et, selon son genre, son son.
 *
 * Une piste cachée ([hidden]) ne s'affiche plus et ne s'entend plus, mais garde ses pas et ses notes.
 */
data class Track(
    val id: Int,
    val kind: Int,
    /** Le son de batterie ([Inst]) d'une piste rythmique. */
    val voice: Int = 0,
    val mix: TrackMix,
    val hidden: Boolean = false,
    val muted: Boolean = false,
    val solo: Boolean = false,
    /** Le son d'une piste de synthé. */
    val synth: SynthPatch = SynthPresets.BASS,
    /** L'instrument d'une piste soundfont : banque et programme. */
    val bank: Int = 0,
    val program: Int = 0,
    /** Arpégiateur d'une piste qui joue des notes : mode, vitesse (0 = croche, 1 = double-croche, 2 = triple), octaves. */
    val arpMode: Int = 0,
    val arpRate: Int = 1,
    val arpOctaves: Int = 1,
) {
    val melodic: Boolean get() = kind != TrackKind.DRUM

    val spec: TrackSpec get() = TrackSpec(id, kind, voice, bank, program)

    companion object {
        /** Mixage d'origine d'une piste soundfont. */
        val SF2_MIX = TrackMix(level = 0.7f, pan = 0f, reverbSend = 0.2f, delaySend = 0.1f)

        fun drum(id: Int, voice: Int) = Track(id, TrackKind.DRUM, voice, TrackMix.defaults()[voice])

        fun synth(id: Int, patch: SynthPatch = SynthPresets.BASS, space: Float = 0.25f): Track = Track(
            id, TrackKind.SYNTH,
            mix = TrackMix.defaults()[Inst.SYNTH].copy(reverbSend = space, delaySend = space * 0.7f),
            synth = patch,
        )

        fun sf2(id: Int, bank: Int, program: Int) = Track(id, TrackKind.SF2, mix = SF2_MIX, bank = bank, program = program)

        /** Les huit pistes d'un projet neuf : les sept sons de batterie, puis le synthé. */
        fun defaults(patch: SynthPatch = SynthPresets.BASS, synthSpace: Float = 0.25f): List<Track> =
            List(Inst.DRUM_COUNT) { drum(it, it) } + synth(Inst.SYNTH, patch, synthSpace)

        /** Le plus petit numéro de piste libre, ou -1 si le moteur n'a plus de place. */
        fun freeId(used: Collection<Int>): Int {
            for (i in 0 until StudioEngine.MAX_TRACKS) if (i !in used) return i
            return -1
        }
    }
}
