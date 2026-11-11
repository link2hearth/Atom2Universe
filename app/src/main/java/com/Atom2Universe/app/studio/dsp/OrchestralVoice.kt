package com.Atom2Universe.app.studio.dsp

import java.io.DataInputStream
import kotlin.math.*

/** Partiels et souffle mesurés sur les 88 touches, à deux vélocités et sept instants. */
internal object OrchestralBank {
    val seconds = floatArrayOf(0.018f, 0.06f, 0.15f, 0.34f, 0.68f, 1.18f, 1.85f)
    class Spectrum(val tones: Int, val release: Float, val ratios: FloatArray, val levels: Array<FloatArray>,
                   val times: FloatArray, val movingRatios: Array<FloatArray>?, val maxVelocity: Int,
                   val referenceVelocity: Int, val loopStart: Int, val loopEnd: Int) {
        @Volatile var attack: OrchestralAttack? = null
    }
    class Program(val firstKey: Int, val lastKey: Int, val layers: Int, val limitedRange: Boolean,
                  val spectra: Array<Spectrum>) {
        fun spectrum(key: Int, velocity: Int): Spectrum {
            val index = (key.coerceIn(firstKey, lastKey) - firstKey) * layers
            var layer = 0
            while (layer < layers - 1 && velocity > spectra[index + layer].maxVelocity) layer++
            return spectra[index + layer]
        }
    }
    private val recent = LinkedHashMap<String, Program>(8, 0.75f, true)
    private val amplitudes = FloatArray(111) { if (it == 110) 0f else 10f.pow(-it / 20f) }
    @Synchronized fun load(program: Int): Program {
        val name = "$program"
        recent[name]?.let { return it }
        val stream = checkNotNull(javaClass.getResourceAsStream("/studio/orchestral/$name.bin"))
        val result = DataInputStream(stream.buffered()).use { input ->
            val format = input.readInt()
            require(format in 0x4f524332..0x4f524336)
            val moving = format != 0x4f524332
            val mapped = format == 0x4f524335
            val looped = format == 0x4f524336
            val noiseBands = if (format >= 0x4f524334) ResonantNoise.BANDS else 6
            val times = if (moving) {
                val count = if (looped) input.readUnsignedShort() else input.readUnsignedByte()
                FloatArray(count) { input.readFloat() }
            } else seconds
            val firstKey = if (mapped) input.readUnsignedByte() else 21
            val lastKey = if (mapped) input.readUnsignedByte() else 108
            val layers = if (mapped) input.readUnsignedByte() else 2
            val spectra = Array((lastKey - firstKey + 1) * layers) { index ->
                val soft = index % layers == 0
                val loopStart = if (looped) input.readUnsignedShort() else -1
                val loopEnd = if (looped) input.readUnsignedShort() else -1
                val maxVelocity = if (mapped) input.readUnsignedByte() else if (soft) 80 else 127
                val reference = if (mapped) input.readUnsignedByte() else if (soft) 64 else 104
                val tones = input.readUnsignedByte()
                val release = input.readFloat()
                val ratios = FloatArray(tones + noiseBands)
                val trajectories = if (moving) Array(tones) { FloatArray(times.size) } else null
                val levels = Array(ratios.size) { i ->
                    if (trajectories == null) ratios[i] = input.readFloat()
                    else if (i < tones) {
                        val key = firstKey + index / layers
                        for (j in times.indices) {
                            trajectories[i][j] = 2f.pow((input.readUnsignedShort() / 256f - key) / 12f)
                        }
                        ratios[i] = trajectories[i][0]
                    }
                    FloatArray(times.size) { amplitudes[input.readUnsignedByte()] }
                }
                Spectrum(tones, release, ratios, levels, times, trajectories, maxVelocity, reference, loopStart, loopEnd)
            }
            Program(firstKey, lastKey, layers, mapped, spectra)
        }
        if (recent.size >= 4) recent.remove(recent.keys.first())
        recent[name] = result
        return result
    }
}

/** Une voix sans enregistrement. Les partiels suivent la hauteur jouée, le souffle garde ses bandes acoustiques. */
internal class OrchestralVoice(private val rate: Int) {
    private val phase = FloatArray(40)
    private val step = FloatArray(40)
    private val weight = FloatArray(40)
    private val level = FloatArray(40 + ResonantNoise.BANDS)
    private val delta = FloatArray(level.size)
    private val edges = intArrayOf(220,650,1550,3500,7000,16000)
    private val poles = FloatArray(6) { exp(-2.0 * PI * edges[it] / rate).toFloat() }
    private val noiseState = FloatArray(6)
    private val noise = Noise()
    // Écart type de la différence de deux passe-bas du premier ordre alimentés par le même bruit blanc.
    private val noiseGain = FloatArray(6) { i ->
        val a = poles[i].toDouble()
        val b = poles.getOrNull(i - 1)?.toDouble() ?: 1.0
        val variance = (1-a)/(1+a) + (1-b)/(1+b) - 2*(1-a)*(1-b)/(1-a*b)
        sqrt(3 / variance).toFloat()
    }
    private val breaks = IntArray(512)
    private val resonances = ResonantNoise(rate)
    private var useResonances = false
    private val stepDelta = FloatArray(40)
    private var fundamental = 0f
    private lateinit var sound: OrchestralBank.Spectrum
    private var age = 0
    private var segment = 0
    private var gain = 1f
    private var ring = 1f
    private var ringDecay = 1f
    private var carry = 0f
    private var previous = 0f
    private var attack: OrchestralAttack? = null
    private var attackFrame = 0
    private val carryDecay = Fast.decayCoef(0.014f, rate)
    val finished get() = ring < 0.0001f

    fun strike(program: OrchestralBank.Program, key: Int, velocity: Float, retrigger: Boolean): Float {
        if (program.limitedRange && key !in program.firstKey..program.lastKey) {
            ring = 0f
            return 0.04f
        }
        return strike(program.spectrum(key, (velocity * 127f).roundToInt()), velocity, retrigger, false)
    }

    fun strike(spectrum: OrchestralBank.Spectrum, velocity: Float, retrigger: Boolean, rightChannel: Boolean): Float {
        sound = spectrum
        attack = null
        attackFrame = 0
        useResonances = sound.levels.size - sound.tones == ResonantNoise.BANDS
        if (useResonances) resonances.reset()
        for (i in sound.times.indices) breaks[i] = (sound.times[i] * rate).roundToInt()
        val reference = sound.referenceVelocity / 127f
        gain = velocity / (reference * reference)
        age = 0
        segment = 0
        ring = 1f
        var before = 0f
        var last = 0f
        for (i in sound.levels.indices) {
            val row = sound.levels[i]
            level[i] = row[0]
            delta[i] = 0f
            val prior = row[row.lastIndex - 1]
            before += prior * prior
            last += row.last() * row.last()
        }
        val drop = 10 * log10((last / before.coerceAtLeast(1e-12f)).coerceAtLeast(1e-9f))
        val interval = sound.times.last() - sound.times[sound.times.lastIndex - 1]
        ringDecay = if (drop < -2.2f * interval) Fast.decayCoef((-60f * interval / drop).coerceIn(0.12f, 25f), rate) else 1f
        val initialPhaseFactor = if (rightChannel) 0.317f else 0.193f
        for (i in 0 until sound.tones) phase[i] = ((i+1)*(i+1)*initialPhaseFactor) % 1f
        noiseState.fill(0f)
        carry = if (retrigger) previous else 0f
        return sound.release
    }

    fun tune(cycles: Float) {
        if (finished) return
        if (attack != null && cycles != fundamental) leaveAttack()
        fundamental = cycles
        if (age == 0 && segment == 0) attack = sound.attack?.takeIf { it.cycles == cycles && it.rate == rate }
        for (i in 0 until sound.tones) {
            step[i] = cycles * sound.ratios[i]
            weight[i] = ((0.5f - step[i]) / 0.05f).coerceIn(0f, 1f)
            stepDelta[i] = 0f
        }
        if (segment > 0 && sound.movingRatios != null) followPitch(segment - 1)
    }

    private fun followPitch(k: Int) {
        val trajectories = sound.movingRatios ?: return
        for (i in trajectories.indices) {
            val path = trajectories[i]
            val start = path[k] * fundamental
            val end = path.getOrElse(k + 1) { path[k] } * fundamental
            val duration = if (k < path.lastIndex) breaks[k + 1] - breaks[k] else 1
            stepDelta[i] = (end - start) / duration
            step[i] = start + (age - breaks[k]).coerceIn(0, duration) * stepDelta[i]
            weight[i] = ((0.5f - max(start, end)) / 0.05f).coerceIn(0f, 1f)
        }
    }

    fun next(): Float {
        val count = sound.times.size
        if (segment < count && age == breaks[segment]) {
            if (segment == sound.loopEnd) {
                segment = sound.loopStart
                age = breaks[segment]
            }
            followPitch(segment)
            for (i in sound.levels.indices) {
                level[i] = sound.levels[i][segment]
                delta[i] = if (segment == count - 1) 0f else
                    (sound.levels[i][segment+1] - level[i]) / (breaks[segment+1] - breaks[segment])
            }
            segment++
        }
        val current = attack
        if (current != null && attackFrame == current.samples.size) leaveAttack()
        if (segment < count) age++ else ring *= ringDecay
        val cached = attack
        val tones = if (cached == null) renderTones() else cached.samples[attackFrame++]
        val hiss = if (useResonances) resonances.next(level, delta, sound.tones) else broadNoise()
        previous = (tones + hiss) * gain * ring + carry
        carry *= carryDecay
        return previous
    }

    private fun leaveAttack() {
        val cached = attack ?: return
        val checkpoint = attackFrame / OrchestralAttack.STRIDE
        val rest = attackFrame % OrchestralAttack.STRIDE
        for (i in 0 until sound.tones) {
            var p = cached.phases[checkpoint * sound.tones + i]
            if (weight[i] > 0f) repeat(rest) {
                p += step[i]
                if (p >= 1f) p -= 1f
            }
            phase[i] = p
            val frame = (segment - 1).coerceAtLeast(0)
            level[i] = sound.levels[i][frame] + delta[i] * (age - breaks[frame]).coerceAtLeast(0)
        }
        attack = null
    }

    private fun renderTones(): Float {
        var sum = 0f
        for (i in 0 until sound.tones) {
            if (weight[i] > 0f) {
                var p = phase[i] + step[i]
                if (p >= 1f) p -= 1f
                phase[i] = p
                val x = p * 2048
                val k = x.toInt()
                sum += (sine[k] + (x-k)*(sine[k+1]-sine[k])) * level[i] * weight[i]
            }
            level[i] += delta[i]
            step[i] += stepDelta[i]
        }
        return sum
    }

    private fun broadNoise(): Float {
        val white = noise.next()
        var sum = 0f
        var low = 0f
        for (i in poles.indices) {
            val filtered = white + poles[i] * (noiseState[i] - white)
            noiseState[i] = filtered
            val k = sound.tones + i
            sum += (filtered-low) * noiseGain[i] * level[k]
            low = filtered
            level[k] += delta[k]
        }
        return sum
    }

    companion object {
        val sine = FloatArray(2049) { sin(it * 2.0 * PI / 2048).toFloat() }
    }
}
