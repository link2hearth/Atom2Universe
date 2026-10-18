package com.Atom2Universe.app.audioeditor.dsp

import com.Atom2Universe.app.audioeditor.core.PeakBuilder
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.addClip
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.io.PeakFiles
import com.Atom2Universe.app.audioeditor.io.WavWriter
import java.io.File
import java.util.Random
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/*
 * Générateurs de signaux : tonalités, bruits colorés et métronome. Chacun est un [FrameReader] mono
 * qui produit exactement [frames] trames ; [GenerateSource] l'écrit dans une source du projet et pose
 * un clip.
 */

enum class ToneShape { SINE, SQUARE, SAW, TRIANGLE }

/**
 * Une tonalité. Le carré et la dent de scie sont corrigés par PolyBLEP (peu de repliement) ; un fondu de
 * [fadeMs] aux deux bouts évite le claquement d'un signal qui démarre en plein milieu d'une période.
 */
class ToneReader(
    private val shape: ToneShape,
    private val freq: Double,
    private val amp: Float,
    override val frames: Long,
    private val sampleRate: Int,
    private val fadeMs: Float = 2f,
) : FrameReader {
    override val channels = 1
    private var pos = 0L
    private var phase = 0.0
    private val fade = maxOf(1, (fadeMs * 0.001f * sampleRate).toInt())

    private fun polyBlep(t: Double, dt: Double): Double = when {
        t < dt -> { val x = t / dt; x + x - x * x - 1.0 }
        t > 1.0 - dt -> { val x = (t - 1.0) / dt; x * x + x + x + 1.0 }
        else -> 0.0
    }

    override fun read(dst: Array<FloatArray>, n: Int): Int {
        val m = minOf(n.toLong(), frames - pos).toInt()
        if (m <= 0) return 0
        val dt = freq / sampleRate
        val out = dst[0]
        for (i in 0 until m) {
            val v = when (shape) {
                ToneShape.SINE -> sin(2.0 * PI * phase)
                ToneShape.SQUARE -> {
                    var s = if (phase < 0.5) 1.0 else -1.0
                    s += polyBlep(phase, dt)
                    s -= polyBlep((phase + 0.5) % 1.0, dt)
                    s
                }
                ToneShape.SAW -> 2.0 * phase - 1.0 - polyBlep(phase, dt)
                ToneShape.TRIANGLE -> 4.0 * abs(phase - 0.5) - 1.0
            }
            val p = pos + i
            val env = minOf(1.0, minOf(p, frames - 1 - p).toDouble() / fade)
            out[i] = (v * amp * env).toFloat()
            phase += dt
            if (phase >= 1.0) phase -= 1.0
        }
        pos += m
        return m
    }
}

enum class NoiseKind { WHITE, PINK, BROWN }

/** Un bruit blanc, rose (−3 dB/octave, filtre de Paul Kellet) ou brun (−6 dB/octave), reproductible grâce à [seed]. */
class NoiseReader(private val kind: NoiseKind, private val amp: Float, override val frames: Long, seed: Long = 1L) : FrameReader {
    override val channels = 1
    private val rnd = Random(seed)
    private var pos = 0L
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0; private var b3 = 0.0
    private var b4 = 0.0; private var b5 = 0.0; private var b6 = 0.0
    private var brown = 0.0

    override fun read(dst: Array<FloatArray>, n: Int): Int {
        val m = minOf(n.toLong(), frames - pos).toInt()
        if (m <= 0) return 0
        val out = dst[0]
        for (i in 0 until m) {
            val w = rnd.nextDouble() * 2.0 - 1.0
            val v = when (kind) {
                NoiseKind.WHITE -> w
                NoiseKind.PINK -> {
                    b0 = 0.99886 * b0 + w * 0.0555179
                    b1 = 0.99332 * b1 + w * 0.0750759
                    b2 = 0.96900 * b2 + w * 0.1538520
                    b3 = 0.86650 * b3 + w * 0.3104856
                    b4 = 0.55000 * b4 + w * 0.5329522
                    b5 = -0.7616 * b5 - w * 0.0168980
                    val s = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362) * 0.11
                    b6 = w * 0.115926
                    s
                }
                NoiseKind.BROWN -> {
                    brown = (brown + 0.02 * w) / 1.02
                    brown * 3.5
                }
            }
            out[i] = (v * amp).toFloat().coerceIn(-1f, 1f)
        }
        pos += m
        return m
    }
}

/** Un métronome : un clic (sinus amorti) à chaque temps, plus aigu au premier temps de chaque mesure. */
class ClickTrackReader(
    private val bpm: Float,
    private val beatsPerBar: Int,
    private val amp: Float,
    override val frames: Long,
    private val sampleRate: Int,
) : FrameReader {
    override val channels = 1
    private var pos = 0L
    private val beatFrames = (60.0 / bpm.coerceIn(20f, 400f) * sampleRate)

    override fun read(dst: Array<FloatArray>, n: Int): Int {
        val m = minOf(n.toLong(), frames - pos).toInt()
        if (m <= 0) return 0
        val out = dst[0]
        for (i in 0 until m) {
            val p = pos + i
            val beat = (p / beatFrames).toLong()
            val into = p - (beat * beatFrames) // trames depuis le début du temps
            val accent = beat % beatsPerBar.coerceAtLeast(1) == 0L
            val f = if (accent) 1500.0 else 1000.0
            val t = into / sampleRate
            out[i] = if (t > 0.05) 0f else (amp * sin(2.0 * PI * f * t) * exp(-t * 90.0)).toFloat()
        }
        pos += m
        return m
    }
}

/** Écrit un générateur dans une nouvelle source du projet et pose un clip dessus. */
object GenerateSource {

    /**
     * @param trackId piste où poser le clip ; `null` crée une nouvelle piste nommée [trackName]
     * @param at trame de départ du clip
     */
    fun apply(
        project: Project,
        projectDir: File,
        reader: FrameReader,
        trackId: Int?,
        at: Long,
        name: String,
        trackName: String,
        ctx: RunContext,
        newId: () -> String = { "s" + UUID.randomUUID().toString().replace("-", "").take(12) },
    ): RenderResult {
        val id = newId()
        val file = File(projectDir, "sources/$id.wav")
        file.parentFile?.mkdirs()
        val peaks = PeakBuilder(reader.channels)
        val buf = Array(reader.channels) { FloatArray(BlockEffect.BLOCK) }
        var frames = 0L
        try {
            WavWriter(file, reader.channels, project.sampleRate, float = false).use { w ->
                while (true) {
                    ctx.checkCancelled()
                    val n = reader.read(buf, BlockEffect.BLOCK)
                    if (n <= 0) break
                    w.write(buf, n)
                    peaks.add(buf, n)
                    frames += n
                    ctx.report(frames.toFloat() / maxOf(1L, reader.frames))
                }
            }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        if (frames == 0L) { file.delete(); return RenderResult(project, emptyList()) }
        PeakFiles.save(peaks.finish(), File(projectDir, "sources/$id.peaks"))
        val src = Source(id, "sources/$id.wav", reader.channels, frames, project.sampleRate, float = false)
        var p = project.addSource(src)
        val target = trackId ?: p.addTrack(trackName).let { (np, tid) -> p = np; tid }
        p = p.addClip(target, id, at, 0, frames, name).first
        return RenderResult(p, listOf(src))
    }
}
