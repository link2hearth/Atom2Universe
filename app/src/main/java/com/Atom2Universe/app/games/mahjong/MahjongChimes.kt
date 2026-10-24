package com.Atom2Universe.app.games.mahjong

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/** Quiet porcelain bells, generated once in the app cache; no external sound assets. */
internal class MahjongChimes(context: Context) {
    private val lock = Any()
    private var closed = false
    private val ready = ConcurrentHashMap.newKeySet<Int>()
    private val notes = IntArray(5)
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready += id }
        val cache = context.cacheDir
        Thread({
            runCatching {
                val frequencies = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0)
                frequencies.forEachIndexed { index, frequency ->
                    val file = File(cache, "mahjong_bell_v1_$index.wav")
                    if (!file.exists()) file.writeBytes(bell(frequency))
                    synchronized(lock) { if (!closed) notes[index] = pool.load(file.absolutePath, 1) }
                }
            }
        }, "mahjong-chimes").apply { isDaemon = true; start() }
    }

    fun play(combo: Int) = synchronized(lock) {
        if (!closed) {
            val id = notes[(combo - 1).coerceAtLeast(0) % notes.size]
            if (id in ready) pool.play(id, .30f, .30f, 1, 0, 1f)
        }
    }

    fun close() = synchronized(lock) { closed = true; pool.release() }

    private fun bell(frequency: Double): ByteArray {
        val rate = 22050
        val samples = (rate * .32).toInt()
        val pcm = samples * 2
        return ByteBuffer.allocate(44 + pcm).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + pcm)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1.toShort()); putShort(1.toShort()); putInt(rate); putInt(rate * 2)
            putShort(2.toShort()); putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII)); putInt(pcm)
            repeat(samples) { i ->
                val t = i.toDouble() / rate
                val envelope = min(1.0, t / .006) * exp(-t * 14) * (1.0 - i.toDouble() / samples)
                val wave = sin(2 * PI * frequency * t) + .28 * sin(2 * PI * frequency * 2.76 * t)
                putShort((wave * envelope * 15000).toInt().toShort())
            }
        }.array()
    }
}
