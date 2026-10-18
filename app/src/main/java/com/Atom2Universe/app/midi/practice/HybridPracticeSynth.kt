package com.Atom2Universe.app.midi.practice

import android.util.Log

/**
 * Hybrid synthesizer for practice mode.
 *
 * Routes MIDI events between a SoundFont engine (SF2 or FluidSynth) and Sonivox
 * based on program configuration, mirroring the HybridMidiEngine routing logic.
 *
 * Routing rules:
 * - Programs in [sf2Programs] → SoundFont engine
 * - Channel 9 (drums) → SoundFont if [useSf2ForDrums], else Sonivox
 * - All other programs → Sonivox (built-in GM sounds)
 * - ProgramChange/ControlChange → sent to BOTH engines for state coherence
 * - NoteOff → sent to BOTH engines (prevents stuck notes after routing change)
 * - NoteOn → routed to ONE engine based on channel's current program
 */
class HybridPracticeSynth(
    private val soundFontSynth: PracticeSynthesizer,
    private val sonivoxSynth: SonivoxPracticeSynth,
    private val sf2Programs: Set<Int>,
    private val useSf2ForDrums: Boolean
) : PracticeSynthesizer {

    companion object {
        private const val TAG = "HybridPracticeSynth"
        private const val PERCUSSION_CHANNEL = 9
    }

    private enum class SynthTarget { SOUNDFONT, SONIVOX }

    // Per-channel routing and program tracking
    private val channelRouting = Array(16) { SynthTarget.SONIVOX }
    private val channelProgram = IntArray(16)

    override fun initialize(): Boolean {
        Log.d(TAG, "initialize: sf2Programs=$sf2Programs, useSf2ForDrums=$useSf2ForDrums")

        // Initialize Sonivox (always needed for non-SF2 programs)
        if (!sonivoxSynth.initialize()) {
            Log.e(TAG, "initialize: Sonivox initialization failed")
            return false
        }

        // Initialize SoundFont engine
        if (!soundFontSynth.initialize()) {
            Log.e(TAG, "initialize: SoundFont engine initialization failed")
            sonivoxSynth.release()
            return false
        }

        // Setup initial routing
        for (channel in 0 until 16) {
            channelProgram[channel] = 0
            updateChannelRouting(channel, 0)
        }

        Log.i(TAG, "initialize: SUCCESS (soundFont=${soundFontSynth.getName()}, " +
                "sf2Programs=${sf2Programs.size} programs, drumsOnSf2=$useSf2ForDrums)")
        return true
    }

    override fun release() {
        Log.d(TAG, "release")
        soundFontSynth.release()
        sonivoxSynth.release()
    }

    private fun updateChannelRouting(channel: Int, program: Int) {
        channelRouting[channel] = when {
            channel == PERCUSSION_CHANNEL -> if (useSf2ForDrums) SynthTarget.SOUNDFONT else SynthTarget.SONIVOX
            sf2Programs.contains(program) -> SynthTarget.SOUNDFONT
            else -> SynthTarget.SONIVOX
        }
    }

    override fun noteOn(channel: Int, note: Int, velocity: Int) {
        // Route to the appropriate engine based on channel's current program
        when (channelRouting[channel]) {
            SynthTarget.SOUNDFONT -> soundFontSynth.noteOn(channel, note, velocity)
            SynthTarget.SONIVOX -> sonivoxSynth.noteOn(channel, note, velocity)
        }
    }

    override fun noteOff(channel: Int, note: Int) {
        // Send to BOTH engines: if routing changed between NoteOn and NoteOff,
        // the note would be stuck on the old engine
        soundFontSynth.noteOff(channel, note)
        sonivoxSynth.noteOff(channel, note)
    }

    override fun programChange(channel: Int, program: Int) {
        // Update routing
        channelProgram[channel] = program
        updateChannelRouting(channel, program)

        // Send to BOTH engines for state coherence
        soundFontSynth.programChange(channel, program)
        sonivoxSynth.programChange(channel, program)
    }

    override fun controlChange(channel: Int, controller: Int, value: Int) {
        // Send to BOTH engines for state coherence
        soundFontSynth.controlChange(channel, controller, value)
        sonivoxSynth.controlChange(channel, controller, value)
    }

    override fun allNotesOff() {
        soundFontSynth.allNotesOff()
        sonivoxSynth.allNotesOff()
    }

    override fun allSoundOff() {
        soundFontSynth.allSoundOff()
        sonivoxSynth.allSoundOff()
    }

    override fun isReady(): Boolean = soundFontSynth.isReady() && sonivoxSynth.isReady()

    override fun getName(): String = "Hybrid (${soundFontSynth.getName()} + Sonivox)"

    /**
     * Force audio output to speaker (delegates to both engines).
     */
    fun forceOutputToSpeaker(reason: String): Boolean {
        val sfResult = when (soundFontSynth) {
            is Sf2PracticeSynth -> soundFontSynth.forceOutputToSpeaker(reason)
            is com.Atom2Universe.app.midi.fluidsynth.FluidSynthPracticeSynth -> soundFontSynth.forceOutputToSpeaker(reason)
            else -> true
        }
        val sonivoxResult = sonivoxSynth.forceOutputToSpeaker()
        return sfResult && sonivoxResult
    }

    /**
     * Reset audio output to default (delegates to both engines).
     */
    fun resetPreferredOutput(reason: String): Boolean {
        val sfResult = when (soundFontSynth) {
            is Sf2PracticeSynth -> soundFontSynth.resetPreferredOutput(reason)
            is com.Atom2Universe.app.midi.fluidsynth.FluidSynthPracticeSynth -> soundFontSynth.resetPreferredOutput(reason)
            else -> true
        }
        val sonivoxResult = sonivoxSynth.resetOutputDevice()
        return sfResult && sonivoxResult
    }
}
