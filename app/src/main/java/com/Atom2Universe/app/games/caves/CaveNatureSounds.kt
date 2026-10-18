package com.Atom2Universe.app.games.caves

import java.io.File
import java.io.FileOutputStream
import kotlin.math.*
import kotlin.random.Random

/** Original synthesized ambience, cached as PCM for SoundPool. Generated off the UI/GL threads. */
internal object CaveNatureSounds {
    @Synchronized fun prepare(cache: File): Map<String, File> {
        val result = linkedMapOf<String, File>()
        for (name in listOf("rain", "crickets")) {
            // Version the cache: existing installations must discard the old harsh rain timbre.
            val file = File(cache, "cave_nature_v2_$name.wav")
            if (!file.isFile || file.length() < 44) write(file, name)
            result["nature_$name"] = file
        }
        return result
    }

    private fun write(file: File, name: String) {
        val rate = 22050
        val duration = if (name == "rain") 8.0 else 2.0
        val count = (rate * duration).toInt()
        val seam = if (name == "rain") rate / 2 else 0
        val random = Random(9143)
        val samples = DoubleArray(count + seam)
        var low = 0.0
        var soft = 0.0
        var rumble = 0.0
        for (i in samples.indices) {
            val t = i.toDouble() / rate
            samples[i] = when (name) {
                "rain" -> {
                    val noise = random.nextDouble(-1.0, 1.0)
                    // Two low-pass stages remove the sharp white-noise hiss. No audible beat.
                    low += (noise-low)*.22
                    soft += (low-soft)*.17
                    rumble += (soft-rumble)*.008
                    (soft-rumble)*.8
                }
                else -> {
                    val p = t % .25
                    val envelope = if (p < .075) sin(PI*p/.075).pow(2) else 0.0
                    envelope * sin(2*PI*3900*t)*.18
                }
            }
        }
        // Equal-power overlap with the continuation of the noise, without a flat patch at the seam.
        for (i in 0 until seam) {
            val angle = i.toDouble()/seam * PI/2
            samples[i] = samples[count+i]*cos(angle)+samples[i]*sin(angle)
        }
        val bytes = java.nio.ByteBuffer.allocate(44+count*2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36+count*2)
        bytes.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        bytes.putShort(1).putShort(1).putInt(rate).putInt(rate*2).putShort(2).putShort(16)
        bytes.put("data".toByteArray(Charsets.US_ASCII)).putInt(count*2)
        for (i in 0 until count) bytes.putShort((samples[i].coerceIn(-1.0,1.0)*32767).toInt().toShort())
        val temp = File.createTempFile("cave_nature_", ".wav", file.parentFile)
        try {
            FileOutputStream(temp).use { it.write(bytes.array()) }
            check(temp.renameTo(file)) { "Cannot cache nature audio" }
        } finally { temp.delete() }
    }
}
