package com.Atom2Universe.app.studio.data

import com.Atom2Universe.app.studio.dsp.SynthPatch
import com.Atom2Universe.app.studio.dsp.SynthPresets
import com.Atom2Universe.app.studio.engine.DRUM_KIT_BANK
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.SynthBank
import com.Atom2Universe.app.studio.engine.TrackKind
import com.Atom2Universe.app.studio.engine.TrackMix
import com.Atom2Universe.app.studio.engine.TrackSpec

enum class PadSize { NORMAL, WIDE, HALF }

enum class PadPlayMode(val key: String) {
    DRIVE("drive"), FILTER("filter"), CLEAN("clean"), THEREMIN("theremin");

    companion object {
        fun fromKey(key: String): PadPlayMode = entries.firstOrNull { it.key == key } ?: DRIVE
    }
}

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
    /** Banque MIDI du soundfont. */
    val bank: Int = 0,
    /** Programme MIDI du soundfont ou des banques SCC et Famicom. */
    val program: Int = 0,
    /** Arpégiateur d'une piste qui joue des notes : mode, vitesse (0 = croche, 1 = double-croche, 2 = triple), octaves. */
    val arpMode: Int = 0,
    val arpRate: Int = 1,
    val arpOctaves: Int = 1,
    /** Le caractère d'une piste de batterie : variante du son (0 = d'origine), hauteur en demi-tons, durée en pour cent. */
    val kit: Int = 0,
    val tune: Int = 0,
    val length: Int = 100,
    /** Le kit de batterie du soundfont (numéro de programme de la banque 128) qui remplace le son fabriqué ; -1 : aucun. */
    val sfKit: Int = -1,
    val padOrder: Int = Int.MAX_VALUE,
    val padSize: PadSize = PadSize.NORMAL,
    val padPlayMode: PadPlayMode = PadPlayMode.DRIVE,
    val synthBank: SynthBank = SynthBank.STUDIO,
    val chipProgramAssigned: Boolean = kind == TrackKind.SYNTH && (synthBank.isGx || program != 0),
    val chipSmoothBass: Boolean = false,
    /** 1–15 : voix du YM2413. 0 : instrument personnel de cette piste. */
    val fmProgram: Int = DEFAULT_FM_PROGRAM,
    val fmBrightness: Float = DEFAULT_FM_BRIGHTNESS,
    val fmAttack: Float = DEFAULT_FM_ATTACK,
    /** Octave de la tonique des nouveaux pas de Rythme et du début de la tuile mélodique dans Pads (do central = do4). */
    val rhythmOctave: Int = DEFAULT_RHYTHM_OCTAVE,
) {
    val melodic: Boolean get() = kind != TrackKind.DRUM

    val spec: TrackSpec
        get() = if (kind == TrackKind.DRUM && sfKit >= 0) TrackSpec(id, kind, voice, DRUM_KIT_BANK, sfKit)
        else TrackSpec(id, kind, voice, bank, program)

    companion object {
        const val DEFAULT_RHYTHM_OCTAVE = 4

        /** MIDI se termine à sol9 (127) ; sol♯ à si culminent à l'octave 8. */
        fun maxRhythmOctave(keyPc: Int): Int = (127 - keyPc) / 12 - 1

        const val DEFAULT_FM_PROGRAM = 3 // Piano
        const val DEFAULT_FM_BRIGHTNESS = 0.5f
        const val DEFAULT_FM_ATTACK = 0.5f

        /** Le nombre de variantes du son de chaque instrument de batterie. */
        const val KIT_COUNT = 3

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
