package com.Atom2Universe.app.audioeditor.engine

import com.Atom2Universe.app.audioeditor.core.Mixer
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.SampleProvider
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.replaceRange
import com.Atom2Universe.app.audioeditor.dsp.Effect
import com.Atom2Universe.app.audioeditor.dsp.MemoryReader
import com.Atom2Universe.app.audioeditor.dsp.MemoryWriter
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import com.Atom2Universe.app.audioeditor.dsp.TrackRangeReader

/** Quelques secondes de stéréo en mémoire, prêtes à jouer. */
class PreviewBuffer(val left: FloatArray, val right: FloatArray, val sampleRate: Int) {
    val frames get() = left.size
}

/** Fournit des sources gardées en mémoire (les rendus de l'aperçu) et laisse le reste au fournisseur de fichiers. */
private class OverlayProvider(
    private val memory: Map<String, Array<FloatArray>>,
    private val files: SampleProvider,
) : SampleProvider {
    override fun read(source: Source, frame: Long, count: Int, dst: Array<FloatArray>, off: Int) {
        val data = memory[source.id]
        if (data == null) { files.read(source, frame, count, dst, off); return }
        for (c in dst.indices) {
            val src = data[minOf(c, data.size - 1)]
            for (i in 0 until count) {
                val f = frame + i
                dst[c][off + i] = if (f < 0 || f >= src.size) 0f else src[f.toInt()]
            }
        }
    }
}

/**
 * Fabrique l'aperçu d'un effet : ce qu'on entendrait **après** l'avoir appliqué, sans rien écrire sur
 * le disque.
 *
 * L'effet passe sur les premières secondes de la plage, dans chaque piste visée ; le résultat devient
 * une source en mémoire, posée avec `replaceRange` exactement comme le fera le vrai rendu
 * ([com.Atom2Universe.app.audioeditor.dsp.RenderRange]) ; puis le projet ainsi modifié est mixé avec
 * ses autres pistes, son volume, son panoramique. L'aperçu est donc ce que donnera « Appliquer », pas
 * une approximation.
 */
object EffectPreview {

    const val DEFAULT_SECONDS = 6

    /**
     * @param from début de l'aperçu (début de la sélection, ou position de lecture)
     * @param to fin de la plage visée par l'effet ; l'aperçu n'en prend que les [maxSeconds] premières secondes
     * @return `null` si aucune piste visée n'a de son dans la plage
     * @throws java.util.concurrent.CancellationException si [ctx] est annulé
     */
    fun render(
        project: Project,
        provider: SampleProvider,
        trackIds: Collection<Int>,
        from: Long,
        to: Long,
        effect: Effect,
        ctx: RunContext,
        maxSeconds: Int = DEFAULT_SECONDS,
    ): PreviewBuffer? {
        val rate = project.sampleRate
        val end = minOf(to, from + maxSeconds.toLong() * rate)
        if (end <= from) return null
        val targets = trackIds.mapNotNull { id ->
            project.track(id)?.takeIf { !it.locked && it.clips.any { c -> c.end > from && c.start < end } }
        }
        if (targets.isEmpty()) return null

        val memory = HashMap<String, Array<FloatArray>>()
        var p = project
        var mixLen = 0L
        for ((k, t) in targets.withIndex()) {
            ctx.checkCancelled()
            val reader = TrackRangeReader(project, t.id, from, end, provider)
            val len = (end - from).toInt()
            val input = Array(reader.channels) { FloatArray(len) }
            val chunk = Array(reader.channels) { FloatArray(CHUNK) }
            var got = 0
            while (got < len) {
                val n = reader.read(chunk, minOf(CHUNK, len - got))
                if (n <= 0) break
                for (c in input.indices) System.arraycopy(chunk[c], 0, input[c], got, n)
                got += n
            }
            val writer = MemoryWriter(effect.outputChannels(input.size))
            val sub = RunContext(
                rate,
                progress = { f -> ctx.report((k + f) / targets.size * 0.9f) },
                isCancelled = { runCatching { ctx.checkCancelled() }.isFailure },
            )
            effect.run({ MemoryReader(input) }, writer, sub)
            val out = writer.toArrays()
            val frames = writer.frames.toLong()
            if (frames == 0L) continue
            val id = "preview_${t.id}"
            memory[id] = out
            p = p.addSource(Source(id, "sources/$id.wav", out.size, frames, rate, float = true))
                .replaceRange(t.id, from, end, id, frames, ripple = effect.changesLength)
            mixLen = maxOf(mixLen, frames)
        }
        if (memory.isEmpty() || mixLen <= 0) return null

        val n = mixLen.toInt()
        val left = FloatArray(n)
        val right = FloatArray(n)
        val mixer = Mixer(OverlayProvider(memory, provider))
        var pos = 0
        val l = FloatArray(CHUNK)
        val r = FloatArray(CHUNK)
        while (pos < n) {
            ctx.checkCancelled()
            val m = minOf(CHUNK, n - pos)
            mixer.render(p, from + pos, m, l, r)
            System.arraycopy(l, 0, left, pos, m)
            System.arraycopy(r, 0, right, pos, m)
            pos += m
        }
        ctx.report(1f)
        return PreviewBuffer(left, right, rate)
    }

    private const val CHUNK = 8192
}

/**
 * Joue un [PreviewBuffer] une fois, sur une sortie à part (la lecture du projet n'est pas touchée).
 * [onFinished] reçoit `true` si l'aperçu est allé jusqu'au bout, `false` s'il a été interrompu ; il
 * arrive sur le fil de lecture.
 */
class PreviewPlayer(
    private val sinkFactory: (sampleRate: Int) -> AudioSink,
    private val onThreadStart: () -> Unit = {},
) {
    private val lock = Any()
    private var current: Run? = null

    val isPlaying: Boolean get() = synchronized(lock) { current != null }

    fun play(buffer: PreviewBuffer, onFinished: (completed: Boolean) -> Unit = {}) {
        stop()
        val run = Run(buffer, onFinished)
        synchronized(lock) { current = run }
        run.thread = Thread(run, "AudioEditor-preview").also { it.start() }
    }

    fun stop() {
        val run: Run
        synchronized(lock) {
            run = current ?: return
            current = null
        }
        run.running = false
        run.sink?.abort()
        run.thread.join(1500)
    }

    private inner class Run(val buffer: PreviewBuffer, val onFinished: (Boolean) -> Unit) : Runnable {
        @Volatile var running = true
        @Volatile var sink: AudioSink? = null
        lateinit var thread: Thread

        override fun run() {
            var completed = false
            var sk: AudioSink? = null
            try {
                onThreadStart()
                val out = sinkFactory(buffer.sampleRate)
                sk = out
                sink = out
                val inter = FloatArray(PlaybackEngine.BLOCK * 2)
                var pos = 0
                var started = false
                while (running && pos < buffer.frames) {
                    val n = minOf(PlaybackEngine.BLOCK, buffer.frames - pos)
                    for (i in 0 until n) {
                        inter[2 * i] = clip(buffer.left[pos + i])
                        inter[2 * i + 1] = clip(buffer.right[pos + i])
                    }
                    var off = 0
                    while (off < n && running) {
                        val w = out.write(inter, off, n - off)
                        if (w <= 0) { running = false; break }
                        off += w
                    }
                    pos += off
                    if (!started && pos >= 2 * PlaybackEngine.BLOCK) { out.start(); started = true }
                }
                if (running) {
                    if (!started) out.start()
                    out.drain(3000)
                    completed = running
                }
            } catch (e: Throwable) {
                completed = false
            } finally {
                runCatching { sk?.release() }
                synchronized(lock) { if (current === this) current = null }
                onFinished(completed)
            }
        }

        private fun clip(v: Float): Float = if (v != v) 0f else if (v > 1f) 1f else if (v < -1f) -1f else v
    }
}
