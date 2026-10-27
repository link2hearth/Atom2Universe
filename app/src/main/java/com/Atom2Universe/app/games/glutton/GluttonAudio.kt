package com.Atom2Universe.app.games.glutton

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.Atom2Universe.app.R

/** Four short original synth sounds (39 KB total); decoded asynchronously by SoundPool. */
internal class GluttonAudio(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = HashSet<Int>()
    private val sounds: IntArray
    private var released = false

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (!released && status == 0) loaded.add(id) }
        sounds = intArrayOf(R.raw.glutton_bite, R.raw.glutton_grow, R.raw.glutton_vacuum, R.raw.glutton_bump)
            .map { pool.load(context, it, 1) }.toIntArray()
    }

    fun play(index: Int, pitch: Float = 1f) {
        if (released || sounds[index] !in loaded) return
        pool.play(sounds[index], .38f, .38f, 1, 0, pitch.coerceIn(.5f, 2f))
    }

    fun pause() { if (!released) pool.autoPause() }
    fun release() { released = true; pool.release(); loaded.clear() }
}
