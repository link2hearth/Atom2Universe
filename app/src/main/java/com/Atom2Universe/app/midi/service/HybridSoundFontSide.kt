package com.Atom2Universe.app.midi.service

import com.Atom2Universe.app.midi.fluidsynth.FluidSynthEngine
import com.Atom2Universe.app.midi.sf2.Sf2Engine

/**
 * Moteur à SoundFont du mode hybride (les instruments choisis), qui joue à côté de Sonivox :
 * le moteur SF2 maison ou FluidSynth, selon le « moteur de base » choisi pour l'hybride.
 */
interface HybridSoundFontSide {
    fun initialize(soundFontPath: String): Boolean
    fun reloadSoundFont(soundFontPath: String): Boolean
    fun isReady(): Boolean
    fun soundFontPath(): String

    fun startAudio(): Boolean
    fun pauseAudio()
    fun resumeAudio()
    fun stopAudio()
    /** Jette le son déjà calculé mais pas encore joué (coupe net après un seek). */
    fun flushAudio()

    fun allNotesOff()
    fun allSoundOff()
    fun reset()
    fun noteOn(channel: Int, note: Int, velocity: Int)
    fun noteOff(channel: Int, note: Int)
    fun programChange(channel: Int, program: Int)
    fun controlChange(channel: Int, controller: Int, value: Int)
    fun pitchBend(channel: Int, value: Int)

    fun setVolume(volume: Float)
    fun setReverb(preset: Int)
    fun setChorus(preset: Int)
    fun setEqEnabled(enabled: Boolean)
    fun setEqBandLevel(band: Int, millibels: Int)
    fun getEqBandLevel(band: Int): Int

    fun audioSessionId(): Int
    fun driverStats(): String
    fun forceOutputToSpeaker(): Boolean
    fun resetOutputDevice(): Boolean
}

class Sf2HybridSide(private val engine: Sf2Engine) : HybridSoundFontSide {

    init {
        // Le mode hybride applique déjà le gain du mixeur aux vélocités (comme pour Sonivox) :
        // le moteur SF2 ne doit pas l'appliquer une seconde fois au signal
        engine.applyMixerGain = false
    }

    override fun initialize(soundFontPath: String): Boolean = engine.initialize(soundFontPath)
    override fun reloadSoundFont(soundFontPath: String): Boolean = engine.reloadSoundFont(soundFontPath)
    override fun isReady(): Boolean = engine.isReadyForDirectMidi()
    override fun soundFontPath(): String = engine.getCurrentSoundFontPath()

    override fun startAudio(): Boolean = engine.startAudioRenderer()
    override fun pauseAudio() = engine.pauseAudioRenderer()
    override fun resumeAudio() = engine.resumeAudioRenderer()
    override fun stopAudio() = engine.stopAudioRenderer()
    override fun flushAudio() = engine.flushAudioBuffer()

    override fun allNotesOff() = engine.sendAllNotesOff()
    override fun allSoundOff() = engine.sendAllSoundOff()
    override fun reset() = engine.resetSynthesizer()
    override fun noteOn(channel: Int, note: Int, velocity: Int) = engine.sendNoteOn(channel, note, velocity)
    override fun noteOff(channel: Int, note: Int) = engine.sendNoteOff(channel, note)
    override fun programChange(channel: Int, program: Int) = engine.sendProgramChange(channel, program)
    override fun controlChange(channel: Int, controller: Int, value: Int) =
        engine.sendControlChange(channel, controller, value)
    override fun pitchBend(channel: Int, value: Int) = engine.sendPitchBend(channel, value)

    override fun setVolume(volume: Float) = engine.setVolume(volume)
    override fun setReverb(preset: Int) = engine.setReverb(preset)
    override fun setChorus(preset: Int) = engine.setChorus(preset)
    override fun setEqEnabled(enabled: Boolean) = engine.setEqEnabled(enabled)
    override fun setEqBandLevel(band: Int, millibels: Int) = engine.setEqBandLevel(band, millibels)
    override fun getEqBandLevel(band: Int): Int = engine.getEqBandLevel(band)

    override fun audioSessionId(): Int = engine.getAudioSessionId()
    override fun driverStats(): String = engine.getDriverStats()
    override fun forceOutputToSpeaker(): Boolean = engine.forceOutputToSpeaker()
    override fun resetOutputDevice(): Boolean = engine.resetOutputDevice()
}

class FluidSynthHybridSide(private val engine: FluidSynthEngine) : HybridSoundFontSide {

    override fun initialize(soundFontPath: String): Boolean = engine.initialize(soundFontPath)
    override fun reloadSoundFont(soundFontPath: String): Boolean = engine.reloadSoundFont(soundFontPath)
    override fun isReady(): Boolean = engine.isReadyForDirectMidi()
    override fun soundFontPath(): String = engine.getCurrentSoundFontPath()

    override fun startAudio(): Boolean = engine.startAudioRenderer()
    override fun pauseAudio() = engine.pauseAudioRenderer()
    override fun resumeAudio() = engine.resumeAudioRenderer()
    override fun stopAudio() = engine.stopAudioRenderer()
    // FluidSynth ne garde pas d'avance de son : rien à jeter
    override fun flushAudio() { }

    override fun allNotesOff() = engine.sendAllNotesOff()
    override fun allSoundOff() = engine.sendAllSoundOff()
    override fun reset() = engine.resetSynthesizer()
    override fun noteOn(channel: Int, note: Int, velocity: Int) = engine.sendNoteOn(channel, note, velocity)
    override fun noteOff(channel: Int, note: Int) = engine.sendNoteOff(channel, note)
    override fun programChange(channel: Int, program: Int) = engine.sendProgramChange(channel, program)
    override fun controlChange(channel: Int, controller: Int, value: Int) =
        engine.sendControlChange(channel, controller, value)
    override fun pitchBend(channel: Int, value: Int) = engine.sendPitchBend(channel, value)

    override fun setVolume(volume: Float) = engine.setVolume(volume)
    override fun setReverb(preset: Int) = engine.setReverb(preset)
    override fun setChorus(preset: Int) = engine.setChorus(preset)
    override fun setEqEnabled(enabled: Boolean) = engine.setEqEnabled(enabled)
    override fun setEqBandLevel(band: Int, millibels: Int) = engine.setEqBandLevel(band, millibels)
    override fun getEqBandLevel(band: Int): Int = engine.getEqBandLevel(band)

    override fun audioSessionId(): Int = engine.getAudioSessionId()
    override fun driverStats(): String = engine.getDriverStats()
    override fun forceOutputToSpeaker(): Boolean = engine.forceOutputToSpeaker(TAG)
    override fun resetOutputDevice(): Boolean = engine.resetPreferredOutput(TAG)

    private companion object {
        const val TAG = "HybridMidiEngine"
    }
}
