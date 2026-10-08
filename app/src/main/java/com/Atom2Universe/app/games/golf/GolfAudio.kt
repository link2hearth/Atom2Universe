package com.Atom2Universe.app.games.golf

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Frappes de golf ElevenLabs et retours sonores de synthèse propres au golf. */
internal class GolfAudio(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    ).build()
    private val loaded = HashSet<Int>()
    @Volatile private var closed = false
    @Volatile private var active = false

    private fun load(context: Context, name: String) =
        runCatching { context.assets.openFd("golf/sounds/$name").use { pool.load(it, 1) } }.getOrDefault(0)

    private val hits: IntArray
    private val bump: Int
    private val cup: Int
    private val out: Int

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded += id } }
        hits = intArrayOf(load(context, "golf_hit_soft.ogg"), load(context, "golf_hit_medium.ogg"), load(context, "golf_hit_strong.ogg"))
        bump = load(context, "landing.wav")
        cup = load(context, "cup.wav")
        out = load(context, "penalty.wav")
    }

    private fun play(id: Int, volume: Float): Int {
        if (closed || !active || id == 0 || synchronized(loaded) { id !in loaded }) return 0
        return pool.play(id, volume, volume, 1, 0, 1f)
    }

    /** Faible, moyen ou fort selon la puissance (0..1) ; le volume suit aussi. */
    fun shot(power: Float) {
        val p = if (power.isFinite()) power.coerceIn(0f, 1f) else 0f
        val level = when { p < 0.35f -> 0; p < 0.7f -> 1; else -> 2 }
        play(hits[level], (0.55f + 0.45f * p).coerceIn(0.5f, 1f))
    }
    fun impact(strength: Float) { play(bump, (0.35f + strength / 8f).coerceIn(0.35f, 1f)) }
    fun holed() { play(cup, 1f) }
    fun outOfBounds() { play(out, 0.8f) }

    fun resume() { active = true; if (!closed) pool.autoResume() }
    fun pause() { active = false; if (!closed) pool.autoPause() }
    fun close() { closed = true; pool.release() }
}
