package com.Atom2Universe.app.music.equalizer.dsp

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Custom RenderersFactory that injects the DSP equalizer processor into the audio pipeline.
 * This allows us to process audio samples before they reach Android's audio system,
 * bypassing Samsung SoundAlive and other system-level audio effects.
 */
@OptIn(UnstableApi::class)
class DspRenderersFactory(
    context: Context,
    private val dspProcessor: DspEqualizerProcessor
) : DefaultRenderersFactory(context) {

    init {
        @Suppress("DEPRECATION")
        setEnableAudioTrackPlaybackParams(true)
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setAudioProcessors(arrayOf<AudioProcessor>(dspProcessor))
            .build()
    }
}
