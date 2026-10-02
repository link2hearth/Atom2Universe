package com.Atom2Universe.app.audioeditor.engine

import com.Atom2Universe.app.audioeditor.core.Levels
import com.Atom2Universe.app.audioeditor.core.Mixer
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.SampleProvider
import java.io.IOException

/**
 * Là où le moteur envoie les échantillons : un `AudioTrack` en vrai ([AndroidAudioSink]), un faux dans
 * les tests. Tout est en stéréo flottant entrelacé.
 */
interface AudioSink {
    val sampleRate: Int

    /**
     * Envoie [frames] trames lues dans [interleaved] à partir de la trame [offsetFrames] et bloque
     * jusqu'à ce qu'elles soient acceptées. Rend le nombre de trames acceptées (0 après [abort]) ou un
     * nombre négatif en cas d'erreur.
     */
    fun write(interleaved: FloatArray, offsetFrames: Int, frames: Int): Int

    /** Démarre la sortie ; appelé une fois que quelques blocs sont déjà en file, pour éviter un souffle de démarrage. */
    fun start()

    /** Trames réellement jouées depuis la création (pas seulement écrites : la sortie a un tampon). */
    fun playedFrames(): Long

    /** Attend, au plus [timeoutMs], que tout ce qui a été écrit soit joué. */
    fun drain(timeoutMs: Long)

    /** Libère un [write] bloqué. Appelable depuis un autre fil. */
    fun abort()

    fun release()
}

/** Une zone à jouer en boucle ; [end] = −1 suit la fin du projet (qui bouge quand on édite pendant la lecture). */
class LoopRange(val start: Long, val end: Long = -1)

/**
 * Joue un [Project] en temps réel : un fil, un [Mixer], un [AudioSink].
 *
 * - **Hors fil audio** : [play], [pause], [stop], [seek], [setProject], [setLoop], [position]. Aucun
 *   n'attend le fil audio plus de quelques millisecondes.
 * - **Fil audio** : mixe un bloc, l'écrit (bloquant), recommence. Il lit « son » instantané du projet
 *   à chaque bloc ; comme un [Project] est immuable, [setProject] pendant la lecture est donc sans
 *   verrou : les éditions s'entendent au bloc suivant (~46 ms).
 * - **Position** : jamais déduite d'un minuteur. Le fil note à quelle trame du projet correspond chaque
 *   « trame écrite » (au départ, après un saut, après un retour de boucle) ; la position est celle
 *   de ce que la sortie a *réellement joué*, retrouvée dans ces repères. Elle reste donc juste malgré
 *   le tampon de sortie, les sauts et les boucles.
 * - **Pause** : on arrête le fil et on retient la position ; reprendre recrée la sortie. Rien à vider.
 *
 * Les rappels [onEnded] et [onError] arrivent sur le fil audio : l'écran doit les reposter sur le sien.
 */
class PlaybackEngine(
    private val provider: SampleProvider,
    private val sinkFactory: (sampleRate: Int) -> AudioSink,
    private val onThreadStart: () -> Unit = {},
) {

    private class Mark(val writtenAt: Long, val frame: Long)

    private val lock = Any()
    private var session: Session? = null
    private var parked = 0L

    @Volatile private var project: Project = Project()
    @Volatile private var loop: LoopRange? = null
    @Volatile private var stopAt = -1L

    /** Crêtes du dernier bloc mixé (valeur absolue, avant écrêtage) : pour les vumètres. */
    @Volatile var peakL = 0f
        private set
    @Volatile var peakR = 0f
        private set

    /** Appelé quand la lecture arrive à la fin du projet (hors boucle) ; l'argument est la position finale. */
    @Volatile var onEnded: ((endFrame: Long) -> Unit)? = null
    @Volatile var onError: ((Throwable) -> Unit)? = null

    val isPlaying: Boolean get() = synchronized(lock) { session != null }

    fun setProject(p: Project) { project = p }

    fun setLoop(range: LoopRange?) { loop = range }

    /** Fin de lecture hors boucle (jouer une sélection) : la lecture s'arrête là comme à la fin du projet. −1 : pas de limite. */
    fun setStopAt(frame: Long) { stopAt = frame }

    /** La position de lecture en trames : celle qui s'entend, ou celle où l'on s'est arrêté. */
    fun position(): Long = synchronized(lock) { session?.position() ?: parked }

    fun play(from: Long = position()) {
        synchronized(lock) {
            val s = session
            if (s != null) {
                s.pendingSeek = from.coerceAtLeast(0)
                return
            }
            val ns = Session(from.coerceAtLeast(0))
            session = ns
            ns.thread = Thread(ns, "AudioEditor-playback").also { it.start() }
        }
    }

    /** Arrête la lecture en gardant la position (reprendre avec [play]). */
    fun pause() = halt(returnTo = -1)

    /** Arrête la lecture ; avec [returnToStart], la position revient là où la lecture avait commencé. */
    fun stop(returnToStart: Boolean = false) {
        halt(returnTo = if (returnToStart) -2 else -1)
    }

    /** −1 : garder la position courante ; −2 : revenir au départ de la lecture. */
    private fun halt(returnTo: Int) {
        val s: Session
        synchronized(lock) {
            s = session ?: return
            parked = if (returnTo == -2) s.from else s.position()
            session = null
            s.running = false
        }
        s.sink?.abort()
        s.thread.join(JOIN_MS)
    }

    /** Pendant la lecture, le saut se fait au prochain bloc ; à l'arrêt, il déplace seulement la position. */
    fun seek(frame: Long) {
        synchronized(lock) {
            val f = frame.coerceAtLeast(0)
            val s = session
            if (s != null) s.pendingSeek = f else parked = f
        }
    }

    /** Arrête tout et attend le fil : à appeler quand l'écran se ferme. */
    fun release() {
        stop()
        onEnded = null
        onError = null
    }

    private inner class Session(val from: Long) : Runnable {
        @Volatile var running = true
        @Volatile var sink: AudioSink? = null
        @Volatile var pendingSeek = -1L
        lateinit var thread: Thread

        private val marks = ArrayList<Mark>()
        private var written = 0L

        private fun addMark(frame: Long) {
            synchronized(marks) { marks.add(Mark(written, frame)) }
        }

        fun position(): Long {
            val sk = sink ?: return from
            val played = sk.playedFrames()
            synchronized(marks) {
                var i = 0
                while (i + 1 < marks.size && marks[i + 1].writtenAt <= played) i++
                // Les repères que la sortie a dépassés ne servent plus.
                if (i > 0) marks.subList(0, i).clear()
                val m = marks.firstOrNull() ?: return from
                return (m.frame + maxOf(0L, played - m.writtenAt)).coerceAtLeast(0)
            }
        }

        override fun run() {
            var sk: AudioSink? = null
            var endedNaturally = false
            var failure: Throwable? = null
            var pos = from
            try {
                onThreadStart()
                val out = sinkFactory(project.sampleRate)
                sk = out
                sink = out
                val mixer = Mixer(provider)
                val l = FloatArray(BLOCK)
                val r = FloatArray(BLOCK)
                val inter = FloatArray(BLOCK * 2)
                val levels = Levels()
                var started = false
                addMark(pos)

                while (running) {
                    val seek = pendingSeek
                    if (seek >= 0) {
                        pendingSeek = -1
                        pos = seek
                        addMark(pos)
                    }
                    val proj = project
                    val total = proj.length
                    val lp = loop
                    val loopEnd = if (lp == null) 0L else if (lp.end < 0) total else lp.end
                    val looping = lp != null && loopEnd > lp.start
                    val stop = stopAt
                    val limit = if (looping) loopEnd else if (stop in 0 until total) stop else total

                    if (pos >= limit) {
                        if (!looping) { endedNaturally = true; break }
                        pos = lp!!.start
                        addMark(pos)
                    }
                    val n = minOf(BLOCK.toLong(), limit - pos).toInt()
                    mixer.render(proj, pos, n, l, r, levels)
                    peakL = levels.peakL
                    peakR = levels.peakR
                    for (i in 0 until n) {
                        inter[2 * i] = clip(l[i])
                        inter[2 * i + 1] = clip(r[i])
                    }
                    var off = 0
                    while (off < n && running) {
                        val w = out.write(inter, off, n - off)
                        if (w < 0) throw IOException("Sortie audio en erreur ($w)")
                        off += w
                    }
                    if (off < n) break           // arrêté pendant l'écriture
                    written += n
                    pos += n
                    if (!started && written >= PRE_ROLL) { out.start(); started = true }
                }
                if (endedNaturally) {
                    if (!started) out.start()
                    out.drain(DRAIN_MS)
                    endedNaturally = running     // une pause pendant la vidange n'est pas une fin
                }
            } catch (e: Throwable) {
                failure = e
            } finally {
                // La position se lit avant de libérer la sortie : un AudioTrack libéré ne répond plus.
                val stopPos = if (endedNaturally) pos else runCatching { position() }.getOrDefault(from)
                runCatching { sk?.release() }
                peakL = 0f
                peakR = 0f
                var notify = false
                synchronized(lock) {
                    // Seulement si personne n'a déjà repris la main (pause, stop, nouvelle lecture).
                    if (session === this) {
                        session = null
                        parked = stopPos
                        notify = true
                    }
                }
                if (notify) {
                    val f = failure
                    if (f != null) onError?.invoke(f) else if (endedNaturally) onEnded?.invoke(pos)
                }
            }
        }
    }

    private fun clip(v: Float): Float = if (v != v) 0f else if (v > 1f) 1f else if (v < -1f) -1f else v

    companion object {
        /** Trames mixées par bloc : ~46 ms à 44,1 kHz. Plus petit = éditions entendues plus vite, plus de réveils du fil. */
        const val BLOCK = 2048
        private const val PRE_ROLL = BLOCK * 2
        private const val DRAIN_MS = 3000L
        private const val JOIN_MS = 1500L
    }
}
