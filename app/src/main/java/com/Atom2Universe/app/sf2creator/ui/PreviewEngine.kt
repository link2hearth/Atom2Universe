package com.Atom2Universe.app.sf2creator.ui

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.midi.fluidsynth.FluidSynthEngine
import com.Atom2Universe.app.midi.sf2.Sf2Engine
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Synth behind the preview keyboards of the SF2 creator: the app's SF2 engine, or FluidSynth
 * when it was chosen and is available.
 */
internal class PreviewEngine private constructor(
    private val sf2: Sf2Engine?,
    private val fluid: FluidSynthEngine?
) {

    fun noteOn(note: Int, velocity: Int) {
        if (fluid != null) fluid.sendNoteOn(0, note, velocity) else sf2?.sendNoteOn(0, note, velocity)
    }

    fun noteOff(note: Int) {
        if (fluid != null) fluid.sendNoteOff(0, note) else sf2?.sendNoteOff(0, note)
    }

    /** Stops the audio thread and frees the engine. */
    fun release() {
        try {
            sf2?.stopAudioRenderer()
            sf2?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing SF2 engine", e)
        }
        try {
            fluid?.stopAudioRenderer()
            fluid?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing FluidSynth engine", e)
        }
    }

    companion object {
        private const val TAG = "Sf2PreviewEngine"

        /** How long a new preview waits for the cancelled one, which may still write the same file. */
        private const val CANCELLED_PREVIEW_WAIT_MS = 3000L

        /**
         * Loads [sf2Path] into a started engine, or returns null. Reads the whole SoundFont:
         * call it off the main thread.
         */
        fun load(context: Context, sf2Path: String, useFluidSynth: Boolean): PreviewEngine? {
            return try {
                if (useFluidSynth && FluidSynthEngine.isSupported()) {
                    val engine = FluidSynthEngine(context)
                    if (engine.initialize(sf2Path)) {
                        engine.startAudioRenderer()
                        PreviewEngine(null, engine)
                    } else {
                        engine.release()
                        null
                    }
                } else {
                    val engine = Sf2Engine(context)
                    if (engine.initialize(sf2Path)) {
                        engine.startAudioRenderer()
                        PreviewEngine(engine, null)
                    } else {
                        engine.release()
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize preview engine", e)
                null
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "SoundFont too large for the preview", e)
                null
            }
        }

        /** Waits (bounded) for a cancelled preview job to finish. */
        suspend fun awaitCancelled(job: Job?) {
            if (job != null) withTimeoutOrNull(CANCELLED_PREVIEW_WAIT_MS) { job.join() }
        }
    }
}
