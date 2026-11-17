package com.Atom2Universe.app.games.mahjong

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlin.random.Random

/**
 * Sons générés une fois dans le cache : le « clac » d'une tuile prise, et une cloche par cran
 * de chaîne, de plus en plus aiguë (gamme pentatonique).
 */
internal class MahjongChimes(context: Context) {
    private val lock = Any()
    private var closed = false
    private val ready = ConcurrentHashMap.newKeySet<Int>()
    private val notes = IntArray(8)
    private var clack = 0
    private val pool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready += id }
        val cache = context.cacheDir
        Thread({
            runCatching {
                val click = File(cache, "mahjong_clack_v2.wav")
                if (!click.exists()) click.writeBytes(clackSound())
                synchronized(lock) { if (!closed) clack = pool.load(click.absolutePath, 1) }
                val frequencies = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66, 1318.51)
                frequencies.forEachIndexed { index, frequency ->
                    val file = File(cache, "mahjong_bell_v2_$index.wav")
                    if (!file.exists()) file.writeBytes(bell(frequency))
                    synchronized(lock) { if (!closed) notes[index] = pool.load(file.absolutePath, 1) }
                }
            }
        }, "mahjong-chimes").apply { isDaemon = true; start() }
    }

    fun play(combo: Int) = synchronized(lock) {
        if (!closed) {
            val id = notes[(combo - 1).coerceIn(0, notes.size - 1)]
            if (id in ready) pool.play(id, .32f, .32f, 1, 0, 1f)
            if (clack in ready) pool.play(clack, .5f, .5f, 1, 0, 1f)
        }
    }

    fun click() = synchronized(lock) {
        if (!closed && clack in ready) pool.play(clack, .38f, .38f, 0, 0, 1f)
    }

    fun close() = synchronized(lock) { closed = true; pool.release() }

    private fun wav(samples: ShortArray, rate: Int): ByteArray {
        val pcm = samples.size * 2
        return ByteBuffer.allocate(44 + pcm).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + pcm)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
            putShort(1.toShort()); putShort(1.toShort()); putInt(rate); putInt(rate * 2)
            putShort(2.toShort()); putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII)); putInt(pcm)
            samples.forEach { putShort(it) }
        }.array()
    }

    private fun bell(frequency: Double): ByteArray {
        val rate = 22050
        val count = (rate * .45).toInt()
        return wav(ShortArray(count) { i ->
            val t = i.toDouble() / rate
            val envelope = min(1.0, t / .004) * exp(-t * 9) * (1.0 - i.toDouble() / count)
            val wave = sin(2 * PI * frequency * t) + .32 * sin(2 * PI * frequency * 2.76 * t) * exp(-t * 18) +
                .18 * sin(2 * PI * frequency * 5.4 * t) * exp(-t * 30)
            (wave * envelope * 12000).toInt().toShort()
        }, rate)
    }

    /** Deux tuiles d'os qui se touchent : un bruit bref et deux résonances aiguës. */
    private fun clackSound(): ByteArray {
        val rate = 22050
        val count = (rate * .07).toInt()
        val random = Random(7)
        var low = 0.0
        return wav(ShortArray(count) { i ->
            val t = i.toDouble() / rate
            val noise = random.nextDouble(-1.0, 1.0)
            low += (noise - low) * .35
            val envelope = exp(-t * 90)
            val tone = sin(2 * PI * 2350 * t) * .55 + sin(2 * PI * 3720 * t) * .35
            ((low * .9 + tone) * envelope * 14000).toInt().coerceIn(-32000, 32000).toShort()
        }, rate)
    }
}
