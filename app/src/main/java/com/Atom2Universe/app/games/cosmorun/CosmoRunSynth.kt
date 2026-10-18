package com.Atom2Universe.app.games.cosmorun

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Les bruitages du jeu. Chacun se synthétise en quelques millisecondes : aucun fichier, aucun crédit. */
enum class CosmoSound { ATOM, JUMP, LAND, SLIDE, LANE, STUMBLE, SHIELD_GET, SHIELD_BREAK, MAGNET,
    OVERDRIVE, JET, SMASH, SECTOR, MISSION, WARDEN, GRAZE, GAME_OVER, HUM,
    MUSIC_PAD, MUSIC_DRUMS, MUSIC_BASS, MUSIC_LEAD;
    /** Les quatre couches de la musique : des boucles de même longueur, lancées ensemble. */
    val music get() = ordinal >= MUSIC_PAD.ordinal
}

/** Synthèse pure (aucune dépendance Android) : testable sur PC, mise en cache en WAV par [CosmoRunSfx]. */
object CosmoRunSynth {
    const val RATE = 22050
    /** Version du timbre : à incrémenter pour que les installations écartent leur cache. */
    const val VERSION = 2
    private const val TAU = 2.0 * PI

    /** Gamme de do majeur en rapports de fréquence, jouée par la chaîne d'atomes. */
    val LADDER = floatArrayOf(1f, 1.1225f, 1.2599f, 1.3348f, 1.4983f, 1.6818f, 1.8877f, 2f)

    fun render(sound: CosmoSound): ShortArray {
        val samples = when (sound) {
            CosmoSound.ATOM -> bell(523.25, .3, .09, .55)
            CosmoSound.JUMP -> sweep(300.0, 780.0, .16, .5, .3)
            CosmoSound.LAND -> thump(.2, 110.0, 45.0, .075, .55)
            CosmoSound.SLIDE -> whoosh(.34, .04, .32, .55)
            CosmoSound.LANE -> whoosh(.08, .18, .5, .3)
            CosmoSound.STUMBLE -> impact(.5, 170.0, 45.0, .13, .85)
            CosmoSound.SHIELD_GET -> arpeggio(doubleArrayOf(1046.5, 1318.5, 1568.0), .085, .55, .1)
            CosmoSound.SHIELD_BREAK -> shatter()
            CosmoSound.MAGNET -> wobble()
            CosmoSound.OVERDRIVE -> sweep(150.0, 1250.0, .7, .5, .55, noise = .25)
            CosmoSound.JET -> jet()
            CosmoSound.SMASH -> impact(.24, 260.0, 70.0, .05, .6, grit = .7)
            CosmoSound.SECTOR -> pad(doubleArrayOf(261.6, 329.6, 392.0, 523.25), 1.05, .5)
            CosmoSound.MISSION -> arpeggio(doubleArrayOf(523.25, 659.25, 783.99, 1046.5, 1318.5), .1, .6, .13)
            CosmoSound.WARDEN -> growl()
            CosmoSound.GRAZE -> sweep(1400.0, 2500.0, .1, .32, .04)
            CosmoSound.GAME_OVER -> descent(doubleArrayOf(392.0, 329.6, 261.6, 196.0))
            CosmoSound.HUM -> hum()
            CosmoSound.MUSIC_PAD -> Music.pad()
            CosmoSound.MUSIC_DRUMS -> Music.drums()
            CosmoSound.MUSIC_BASS -> Music.bass()
            CosmoSound.MUSIC_LEAD -> Music.lead()
        }
        return finish(samples, loop = sound == CosmoSound.HUM || sound.music)
    }

    /** Un WAV mono 16 bits, prêt pour SoundPool. */
    fun wav(pcm: ShortArray): ByteArray {
        val bytes = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + pcm.size * 2)
        bytes.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        bytes.putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        bytes.put("data".toByteArray(Charsets.US_ASCII)).putInt(pcm.size * 2)
        for (s in pcm) bytes.putShort(s)
        return bytes.array()
    }

    // ── Outils ────────────────────────────────────────────────────────────────────────────────

    private fun buffer(seconds: Double) = DoubleArray((RATE * seconds).toInt())

    /** Les extrémités sont adoucies (pas de claquement) ; la boucle, elle, ne l'est jamais. */
    private fun finish(samples: DoubleArray, loop: Boolean): ShortArray {
        val edge = min(samples.size / 2, 220)
        if (!loop) for (i in 0 until edge) {
            val g = i.toDouble() / edge
            samples[samples.size - 1 - i] *= g
            if (i < 40) samples[i] *= i / 40.0
        }
        var peak = 0.0
        for (v in samples) peak = maxOf(peak, kotlin.math.abs(v))
        val gain = if (peak > .92) .92 / peak else 1.0
        return ShortArray(samples.size) { (samples[it] * gain * 32767).toInt().coerceIn(-32767, 32767).toShort() }
    }

    private fun decay(t: Double, tau: Double) = exp(-t / tau)

    private fun bell(freq: Double, length: Double, tau: Double, gain: Double, offset: Double = 0.0,
                     out: DoubleArray = buffer(length)): DoubleArray {
        val first = (offset * RATE).toInt()
        for (i in first until out.size) {
            val t = (i - first).toDouble() / RATE
            val attack = min(1.0, t / .002)
            out[i] += gain * attack * decay(t, tau) *
                (sin(TAU * freq * t) + .42 * sin(TAU * freq * 2.0 * t) + .16 * sin(TAU * freq * 3.01 * t))
        }
        return out
    }

    private fun arpeggio(notes: DoubleArray, step: Double, gain: Double, tail: Double): DoubleArray {
        val out = buffer(step * (notes.size - 1) + tail + .22)
        notes.forEachIndexed { i, f -> bell(f, .22, .07, gain, i * step, out) }
        return out
    }

    /** Glissando avec un peu de souffle ; la phase est intégrée pour ne jamais craquer. */
    private fun sweep(from: Double, to: Double, length: Double, gain: Double, harmonic: Double,
                      noise: Double = 0.0): DoubleArray {
        val out = buffer(length)
        val random = Random(17)
        var phase = 0.0
        for (i in out.indices) {
            val u = i.toDouble() / out.size
            val f = from * Math.pow(to / from, u)
            phase += TAU * f / RATE
            val env = sin(PI * min(1.0, u * 1.15)).coerceAtLeast(0.0) * (1.0 - u * .3)
            out[i] = gain * env * (sin(phase) + harmonic * sin(phase * 2.0) + noise * random.nextDouble(-1.0, 1.0))
        }
        return out
    }

    private fun thump(length: Double, from: Double, to: Double, tau: Double, gain: Double): DoubleArray {
        val out = buffer(length)
        val random = Random(29)
        var phase = 0.0
        var lp = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            phase += TAU * (to + (from - to) * exp(-t * 20.0)) / RATE
            lp += (random.nextDouble(-1.0, 1.0) - lp) * .12
            out[i] = gain * decay(t, tau) * (sin(phase) + .5 * lp * decay(t, .03))
        }
        return out
    }

    private fun impact(length: Double, from: Double, to: Double, tau: Double, gain: Double,
                       grit: Double = .45): DoubleArray {
        val out = buffer(length)
        val random = Random(41)
        var phase = 0.0
        var lp = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            phase += TAU * (to + (from - to) * exp(-t * 14.0)) / RATE
            lp += (random.nextDouble(-1.0, 1.0) - lp) * .35
            out[i] = gain * (decay(t, tau * 1.7) * sin(phase) + grit * lp * decay(t, tau))
        }
        return out
    }

    /** Souffle filtré dont la coupure glisse : glissade, changement de voie. */
    private fun whoosh(length: Double, startCut: Double, endCut: Double, gain: Double): DoubleArray {
        val out = buffer(length)
        val random = Random(53)
        var a = 0.0
        var b = 0.0
        for (i in out.indices) {
            val u = i.toDouble() / out.size
            val cut = startCut + (endCut - startCut) * u
            a += (random.nextDouble(-1.0, 1.0) - a) * cut
            b += (a - b) * cut
            out[i] = gain * 2.2 * (a - b) * Math.pow(sin(PI * u), 1.4)
        }
        return out
    }

    private fun shatter(): DoubleArray {
        val out = buffer(.34)
        val random = Random(67)
        var lp = 0.0
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            lp += (random.nextDouble(-1.0, 1.0) - lp) * .7
            phase += TAU * (900.0 * exp(-t * 6.0) + 180.0) / RATE
            out[i] = .6 * (decay(t, .05) * (random.nextDouble(-1.0, 1.0) - lp * .4) + .5 * decay(t, .11) * sin(phase))
        }
        return out
    }

    private fun wobble(): DoubleArray {
        val out = buffer(.55)
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            val u = t / .55
            phase += TAU * (220.0 + 220.0 * u + 30.0 * sin(TAU * 9.0 * t)) / RATE
            out[i] = .5 * sin(PI * u) * (sin(phase) + .35 * sin(phase * 2.0))
        }
        return out
    }

    private fun jet(): DoubleArray {
        val out = buffer(.7)
        val random = Random(71)
        var a = 0.0
        var b = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            val u = t / .7
            val cut = .06 + .22 * u
            a += (random.nextDouble(-1.0, 1.0) - a) * cut
            b += (a - b) * cut
            out[i] = (1.9 * (a - b) + .35 * sin(TAU * 62.0 * t)) * .6 * sin(PI * min(1.0, u * 1.25)).coerceAtLeast(0.0)
        }
        return out
    }

    private fun pad(freqs: DoubleArray, length: Double, gain: Double): DoubleArray {
        val out = buffer(length)
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            val u = t / length
            var sum = 0.0
            for (f in freqs) sum += sin(TAU * f * t) + .3 * sin(TAU * f * 2.0 * t)
            out[i] = gain / freqs.size * sum * sin(PI * u) * (.6 + .4 * u)
        }
        return out
    }

    private fun growl(): DoubleArray {
        val out = buffer(.75)
        val random = Random(83)
        var lp = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            val u = t / .75
            lp += (random.nextDouble(-1.0, 1.0) - lp) * .05
            val saw = 2.0 * ((t * 52.0) % 1.0) - 1.0
            val tremolo = .55 + .45 * sin(TAU * 13.0 * t)
            out[i] = .7 * (saw * .5 + lp * 2.0) * tremolo * sin(PI * u).coerceAtLeast(0.0)
        }
        return out
    }

    private fun descent(notes: DoubleArray): DoubleArray {
        val step = .17
        val out = buffer(step * notes.size + .35)
        notes.forEachIndexed { n, f ->
            val first = (n * step * RATE).toInt()
            var phase = 0.0
            for (i in first until out.size) {
                val t = (i - first).toDouble() / RATE
                phase += TAU * f / RATE
                val tri = 2.0 * kotlin.math.abs(2.0 * ((phase / TAU) % 1.0) - 1.0) - 1.0
                out[i] += .42 * min(1.0, t / .006) * decay(t, .17) * (tri + .3 * sin(phase))
            }
        }
        return out
    }

    /** Une seconde exactement, fréquences entières : la boucle se recolle sans couture. */
    private fun hum(): DoubleArray {
        val out = buffer(1.0)
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            out[i] = .5 * (.55 * sin(TAU * 55.0 * t) + .25 * sin(TAU * 110.0 * t) + .12 * sin(TAU * 165.0 * t)) *
                (.8 + .2 * sin(TAU * 3.0 * t))
        }
        return out
    }

    /**
     * La musique : 4 mesures en la mineur (Am - F - C - G), 128 battements par minute, en quatre couches
     * de même durée. Le jeu lance les quatre ensemble et ne joue que celles que la chaîne mérite.
     * Les notes qui dépassent la fin de la boucle reviennent au début : aucune couture.
     */
    object Music {
        const val BPM = 128.0
        const val BARS = 4
        private val BEAT = 60.0 / BPM
        val LENGTH = (RATE * BEAT * 4 * BARS).toInt()
        private val EIGHTH = BEAT / 2

        private val ROOTS = doubleArrayOf(110.0, 87.31, 130.81, 98.0)            // A2 F2 C3 G2
        private val CHORDS = arrayOf(                                              // A3.. par mesure
            doubleArrayOf(220.0, 261.63, 329.63), doubleArrayOf(174.61, 220.0, 261.63),
            doubleArrayOf(261.63, 329.63, 392.0), doubleArrayOf(196.0, 246.94, 293.66))

        private fun add(out: DoubleArray, startSeconds: Double, seconds: Double, gain: Double, voice: (Double) -> Double) {
            val first = (startSeconds * RATE).toInt()
            val count = (seconds * RATE).toInt()
            for (k in 0 until count) out[(first + k) % out.size] += gain * voice(k.toDouble() / RATE)
        }

        fun pad(): DoubleArray {
            val out = DoubleArray(LENGTH)
            val pattern = intArrayOf(0, 1, 2, 1, 0, 1, 2, 1)
            for (bar in 0 until BARS) for (step in 0 until 8) {
                val f = CHORDS[bar][pattern[step]]
                add(out, (bar * 8 + step) * EIGHTH, .42, .3) { t ->
                    val env = minOf(1.0, t / .004) * decay(t, .16)
                    env * (sin(TAU * f * t) + .35 * sin(TAU * f * 2 * t) + .12 * sin(TAU * f * 3.01 * t))
                }
            }
            // Un nappage doux : la tonique tenue, pour que la couche ne soit jamais nue.
            for (bar in 0 until BARS) add(out, bar * 4 * BEAT, 4 * BEAT, .1) { t ->
                sin(TAU * ROOTS[bar] * 2 * t) * sin(PI * t / (4 * BEAT))
            }
            return out
        }

        fun drums(): DoubleArray {
            val out = DoubleArray(LENGTH)
            val random = Random(5)
            for (beat in 0 until BARS * 4) {
                // Grosse caisse à chaque temps.
                var phase = 0.0
                add(out, beat * BEAT, .22, .9) { t ->
                    phase += TAU * (48.0 + 70.0 * exp(-t * 28.0)) / RATE
                    decay(t, .09) * sin(phase)
                }
                // Caisse claire sur les temps 2 et 4.
                if (beat % 2 == 1) {
                    var lp = 0.0
                    add(out, beat * BEAT, .18, .5) { t ->
                        lp += (random.nextDouble(-1.0, 1.0) - lp) * .55
                        decay(t, .05) * (lp * 1.4 + .5 * sin(TAU * 190.0 * t))
                    }
                }
            }
            // Charleston sur les contretemps.
            for (step in 0 until BARS * 8) if (step % 2 == 1) {
                var prev = 0.0
                add(out, step * EIGHTH, .06, .28) { t ->
                    val n = random.nextDouble(-1.0, 1.0)
                    val high = n - prev; prev = n
                    decay(t, .015) * high
                }
            }
            return out
        }

        fun bass(): DoubleArray {
            val out = DoubleArray(LENGTH)
            val accent = intArrayOf(1, 0, 1, 1, 0, 1, 1, 0)
            for (bar in 0 until BARS) for (step in 0 until 8) if (accent[step] == 1) {
                val f = ROOTS[bar]
                add(out, (bar * 8 + step) * EIGHTH, .3, .55) { t ->
                    val env = minOf(1.0, t / .006) * decay(t, .13)
                    env * (sin(TAU * f * t) + .55 * sin(TAU * f * 2 * t) + .2 * sin(TAU * f * 3 * t))
                }
            }
            return out
        }

        fun lead(): DoubleArray {
            val out = DoubleArray(LENGTH)
            // (croche de départ, fréquence) sur les 4 mesures : phrase pentatonique qui suit les accords.
            val notes = arrayOf(
                0 to 659.25, 2 to 880.0, 3 to 783.99, 4 to 659.25, 6 to 523.25,
                8 to 523.25, 10 to 698.46, 11 to 659.25, 12 to 523.25, 14 to 440.0,
                16 to 659.25, 18 to 783.99, 19 to 659.25, 20 to 523.25, 22 to 659.25,
                24 to 587.33, 26 to 493.88, 27 to 587.33, 28 to 783.99, 30 to 587.33)
            for ((step, f) in notes) {
                // La phase s'accumule : un vibrato écrit en sin(f * vibrato * t) ferait dériver la hauteur.
                var phase = 0.0
                var last = 0.0
                add(out, step * EIGHTH, .5, .5) { t ->
                    phase += TAU * f * (1.0 + .004 * sin(TAU * 5.5 * t)) * (t - last); last = t
                    val env = minOf(1.0, t / .006) * decay(t, .2)
                    env * (sin(phase) + .3 * sin(2 * phase))
                }
            }
            return out
        }
    }
}
