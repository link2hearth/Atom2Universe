package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.core.Mixer
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.addClip
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.engine.AudioSink
import com.Atom2Universe.app.audioeditor.engine.LoopRange
import com.Atom2Universe.app.audioeditor.engine.PlaybackEngine
import com.Atom2Universe.app.audioeditor.io.FileSampleProvider
import com.Atom2Universe.app.audioeditor.io.WavWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class PlaybackEngineTest {

    private lateinit var dir: File
    private lateinit var provider: FileSampleProvider
    private lateinit var engine: PlaybackEngine
    private val sinks = ArrayList<FakeSink>()

    /**
     * Une sortie factice. En mode [gated], chaque écriture attend un permis : le test avance le « temps »
     * bloc par bloc. [lag] simule le tampon de sortie : ce qui est joué retarde sur ce qui est écrit.
     */
    private class FakeSink(override val sampleRate: Int, val gated: Boolean, val lag: Long, val failAfter: Int = -1) : AudioSink {
        val permits = Semaphore(0)
        private val left = ArrayList<Float>()
        private val right = ArrayList<Float>()
        @Volatile var written = 0L
        @Volatile var started = false
        @Volatile var released = false
        @Volatile var aborted = false
        private var calls = 0

        override fun write(interleaved: FloatArray, offsetFrames: Int, frames: Int): Int {
            if (aborted) return 0
            if (gated) { permits.acquire(); if (aborted) return 0 }
            if (failAfter >= 0 && calls++ >= failAfter) return -3
            synchronized(left) {
                for (i in 0 until frames) { left.add(interleaved[(offsetFrames + i) * 2]); right.add(interleaved[(offsetFrames + i) * 2 + 1]) }
            }
            written += frames
            return frames
        }
        override fun start() { started = true }
        override fun playedFrames(): Long = maxOf(0L, written - lag)
        override fun drain(timeoutMs: Long) {}
        override fun abort() { aborted = true; permits.release(1000) }
        override fun release() { released = true }

        fun left(): FloatArray = synchronized(left) { left.toFloatArray() }
        fun right(): FloatArray = synchronized(right) { right.toFloatArray() }
    }

    @Before fun setUp() {
        dir = createTempDir("playback")
        File(dir, "sources").mkdirs()
        provider = FileSampleProvider(dir)
    }
    @After fun tearDown() {
        if (this::engine.isInitialized) engine.release()
        provider.close()
        dir.deleteRecursively()
    }

    private fun makeEngine(gated: Boolean = false, lag: Long = 0, failAfter: Int = -1, factoryError: Boolean = false, onThread: () -> Unit = {}): PlaybackEngine {
        engine = PlaybackEngine(provider, { rate ->
            if (factoryError) throw IOException("pas de sortie audio")
            FakeSink(rate, gated, lag, failAfter).also { synchronized(sinks) { sinks.add(it) } }
        }, onThread)
        return engine
    }

    private fun sink(i: Int = 0): FakeSink = synchronized(sinks) { sinks[i] }

    /** Un projet : une piste, un clip de [frames] trames d'un sinus mono. */
    private fun project(frames: Int, amp: Float = 0.5f): Project {
        val f = File(dir, "sources/a.wav")
        WavWriter(f, 1, 44100, float = false).use { it.write(arrayOf(DspTest.sine(frames, 440.0, amp)), frames) }
        val p = Project().addSource(Source("a", "sources/a.wav", 1, frames.toLong(), 44100))
        val (p1, t) = p.addTrack("t")
        return p1.addClip(t, "a", 0).first
    }

    private fun mix(p: Project, from: Long, n: Int): FloatArray {
        val l = FloatArray(n); val r = FloatArray(n)
        Mixer(provider).render(p, from, n, l, r)
        return l
    }

    private fun waitUntil(ms: Long = 5000, cond: () -> Boolean) {
        val end = System.nanoTime() + ms * 1_000_000L
        while (!cond()) {
            if (System.nanoTime() > end) throw AssertionError("délai dépassé")
            Thread.sleep(2)
        }
    }

    // ---- Lecture jusqu'au bout ----------------------------------------------------------

    @Test
    fun `joue tout le projet et rend exactement le mixage`() {
        val p = project(10_000)
        val e = makeEngine()
        e.setProject(p)
        val ended = CountDownLatch(1)
        var endFrame = -1L
        e.onEnded = { endFrame = it; ended.countDown() }
        e.play(0)
        assertTrue(ended.await(5, TimeUnit.SECONDS))
        assertEquals(10_000L, endFrame)
        val s = sink()
        val l = s.left()
        assertEquals(10_000, l.size)
        val expect = mix(p, 0, 10_000)
        for (i in 0 until 10_000 step 7) { assertEquals(expect[i], l[i], 1e-6f); assertEquals(expect[i], s.right()[i], 1e-6f) }
        assertTrue(s.started)
        waitUntil { s.released }
        assertFalse(e.isPlaying)
        assertEquals(10_000L, e.position())
    }

    @Test
    fun `un projet vide ou une lecture au-dela de la fin se terminent aussitot`() {
        val e = makeEngine()
        e.setProject(Project())
        val ended = CountDownLatch(1)
        e.onEnded = { ended.countDown() }
        e.play(0)
        assertTrue(ended.await(5, TimeUnit.SECONDS))
        assertEquals(0, sink().left().size)

        val e2 = makeEngine()
        e2.setProject(project(1000))
        val ended2 = CountDownLatch(1)
        var f = -1L
        e2.onEnded = { f = it; ended2.countDown() }
        e2.play(5000)
        assertTrue(ended2.await(5, TimeUnit.SECONDS))
        assertEquals(5000L, f)
    }

    @Test
    fun `le fil audio demande la priorite et les ecrêtages sont bornes`() {
        var called = 0
        val p = project(4000, amp = 0.9f).let { it.copy(master = 3f) }   // 0,9 × 3 = 2,7 : doit être écrêté à 1
        val e = makeEngine(onThread = { called++ })
        e.setProject(p)
        val ended = CountDownLatch(1)
        e.onEnded = { ended.countDown() }
        e.play(0)
        assertTrue(ended.await(5, TimeUnit.SECONDS))
        assertEquals(1, called)
        assertEquals(1f, sink().left().max(), 0f)
        assertTrue(sink().left().min() >= -1f)
    }

    // ---- Position ----------------------------------------------------------------------

    @Test
    fun `la position est celle de ce qui a ete joue, pas de ce qui a ete ecrit`() {
        val e = makeEngine(gated = true, lag = 4096)
        e.setProject(project(100_000))
        e.play(0)
        waitUntil { synchronized(sinks) { sinks.isNotEmpty() } }
        sink().permits.release(3)
        waitUntil { sink().written == 6144L }
        waitUntil { e.position() == 2048L }     // 6 144 écrites − 4 096 de retard
        assertTrue(e.isPlaying)
    }

    @Test
    fun `la position part du point demande`() {
        val e = makeEngine(gated = true)
        e.setProject(project(100_000))
        e.play(30_000)
        waitUntil { synchronized(sinks) { sinks.isNotEmpty() } }
        sink().permits.release(2)
        waitUntil { sink().written == 4096L }
        waitUntil { e.position() == 34_096L }
        val l = sink().left()
        val expect = mix(project(100_000), 30_000, 4096)
        for (i in 0 until 4096 step 13) assertEquals(expect[i], l[i], 1e-6f)
    }

    // ---- Pause, arrêt, reprise -------------------------------------------------------------

    @Test
    fun `pause garde la position et reprendre repart de la`() {
        val p = project(100_000)
        val e = makeEngine(gated = true)
        e.setProject(p)
        e.play(0)
        waitUntil { synchronized(sinks) { sinks.isNotEmpty() } }
        sink(0).permits.release(3)
        waitUntil { sink(0).written == 6144L }
        e.pause()
        assertFalse(e.isPlaying)
        assertEquals(6144L, e.position())
        assertTrue(sink(0).released)

        e.play()
        waitUntil { synchronized(sinks) { sinks.size == 2 } }
        sink(1).permits.release(1)
        waitUntil { sink(1).written == 2048L }
        val expect = mix(p, 6144, 2048)
        val l = sink(1).left()
        for (i in 0 until 2048 step 11) assertEquals(expect[i], l[i], 1e-6f)
    }

    @Test
    fun `stop peut ramener la position au depart de la lecture`() {
        val e = makeEngine(gated = true)
        e.setProject(project(100_000))
        e.play(1000)
        waitUntil { synchronized(sinks) { sinks.isNotEmpty() } }
        sink().permits.release(2)
        waitUntil { sink().written == 4096L }
        e.stop(returnToStart = true)
        assertEquals(1000L, e.position())

        e.play(2000)
        waitUntil { synchronized(sinks) { sinks.size == 2 } }
        sink(1).permits.release(1)
        waitUntil { sink(1).written == 2048L }
        e.stop()
        assertEquals(4048L, e.position())
    }

    @Test
    fun `chercher a l'arret deplace seulement la position`() {
        val e = makeEngine()
        e.setProject(project(10_000))
        e.seek(1234)
        assertEquals(1234L, e.position())
        e.seek(-50)
        assertEquals(0L, e.position())
        assertFalse(e.isPlaying)
        assertTrue(synchronized(sinks) { sinks.isEmpty() })
    }

    // ---- Saut pendant la lecture --------------------------------------------------------

    @Test
    fun `un saut pendant la lecture change le contenu des blocs suivants et la position suit`() {
        val p = project(100_000)
        val e = makeEngine(gated = true)
        e.setProject(p)
        e.play(0)
        waitUntil { synchronized(sinks) { sinks.isNotEmpty() } }
        sink().permits.release(2)
        waitUntil { sink().written == 4096L }
        e.seek(50_000)
        // Le bloc en attente d'écriture a pu être mixé avant ou après le saut (course voulue : le fil audio
        // n'attend personne) ; ce qui est sûr, c'est que le saut est pris au bloc 3 ou 4 et que la suite en vient.
        sink().permits.release(4)
        waitUntil { sink().written == 12_288L }
        val l = sink().left()
        val target = mix(p, 50_000, 2048)
        val k = listOf(4096, 6144).firstOrNull { b -> (0 until 2048 step 17).all { i -> Math.abs(target[i] - l[b + i]) < 1e-6f } }
        assertNotNull("le saut doit apparaître au bloc 3 ou 4", k)
        val next = mix(p, 52_048, 2048)
        for (i in 0 until 2048 step 17) assertEquals(next[i], l[k!! + 2048 + i], 1e-6f)
        // La position suit : ce qui reste écrit après le saut, compté depuis 50 000.
        waitUntil { e.position() == 50_000L + (12_288L - k!!) }
    }

    // ---- Boucle -------------------------------------------------------------------------

    @Test
    fun `la boucle revient au debut de la zone`() {
        val p = project(5000)
        val e = makeEngine(gated = true)
        e.setProject(p)
        e.setLoop(LoopRange(1000, 3000))
        e.play(0)
        waitUntil { synchronized(sinks) { sinks.isNotEmpty() } }
        sink().permits.release(3)                    // 2 048 + 952, puis 2 000 depuis 1 000
        waitUntil { sink().written == 5000L }
        val l = sink().left()
        val first = mix(p, 0, 3000)
        for (i in 0 until 3000 step 9) assertEquals(first[i], l[i], 1e-6f)
        val again = mix(p, 1000, 2000)
        for (i in 0 until 2000 step 9) assertEquals("trame ${3000 + i}", again[i], l[3000 + i], 1e-6f)
        // 5 000 trames jouées = 3 000, puis 2 000 depuis 1 000 : on est à la fin de la zone (3 000), ou déjà revenu à son début (1 000).
        waitUntil { e.position() == 3000L || e.position() == 1000L }
        assertTrue(e.isPlaying)
    }

    @Test
    fun `une boucle sans fin precise suit la fin du projet et peut etre retiree`() {
        val p = project(3000)
        val e = makeEngine(gated = true)
        e.setProject(p)
        e.setLoop(LoopRange(0))
        e.play(0)
        waitUntil { synchronized(sinks) { sinks.isNotEmpty() } }
        sink().permits.release(4)                    // 2 048, 952, 2 048, 952
        waitUntil { sink().written == 6000L }
        val l = sink().left()
        val one = mix(p, 0, 3000)
        for (i in 0 until 3000 step 7) { assertEquals(one[i], l[i], 1e-6f); assertEquals(one[i], l[3000 + i], 1e-6f) }

        val ended = CountDownLatch(1)
        e.onEnded = { ended.countDown() }
        e.setLoop(null)
        sink().permits.release(1000)
        assertTrue(ended.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `stopAt arrete la lecture a la fin d'une selection`() {
        val p = project(10_000)
        val e = makeEngine()
        e.setProject(p)
        e.setStopAt(3000)
        val ended = CountDownLatch(1)
        var endFrame = -1L
        e.onEnded = { endFrame = it; ended.countDown() }
        e.play(1000)
        assertTrue(ended.await(5, TimeUnit.SECONDS))
        assertEquals(3000L, endFrame)
        val l = sink().left()
        assertEquals(2000, l.size)
        val expect = mix(p, 1000, 2000)
        for (i in 0 until 2000 step 11) assertEquals(expect[i], l[i], 1e-6f)
    }

    // ---- Édition pendant la lecture -------------------------------------------------------

    @Test
    fun `un projet remplace pendant la lecture s'entend au bloc suivant`() {
        val p = project(100_000)
        val e = makeEngine(gated = true)
        e.setProject(p)
        e.play(0)
        waitUntil { synchronized(sinks) { sinks.isNotEmpty() } }
        sink().permits.release(1)
        waitUntil { sink().written == 2048L }
        e.setProject(p.copy(master = 0f))
        sink().permits.release(3)                    // le bloc déjà mixé, puis deux silencieux
        waitUntil { sink().written == 8192L }
        val l = sink().left()
        assertTrue(DspTest.peak(l, 0, 2048) > 0.4f)
        assertEquals(0f, DspTest.peak(l, 4096, 8192), 0f)
    }

    @Test
    fun `les vumetres suivent le bloc mixe et retombent a zero a l'arret`() {
        val e = makeEngine(gated = true)
        e.setProject(project(100_000, amp = 0.5f))
        e.play(0)
        waitUntil { synchronized(sinks) { sinks.isNotEmpty() } }
        sink().permits.release(2)
        waitUntil { sink().written == 4096L }
        waitUntil { e.peakL > 0.45f }
        assertEquals(e.peakL, e.peakR, 1e-6f)
        e.stop()
        assertEquals(0f, e.peakL, 0f)
    }

    // ---- Erreurs ------------------------------------------------------------------------

    @Test
    fun `une sortie impossible a ouvrir est signalee`() {
        val e = makeEngine(factoryError = true)
        e.setProject(project(1000))
        val err = CountDownLatch(1)
        var got: Throwable? = null
        e.onError = { got = it; err.countDown() }
        e.play(0)
        assertTrue(err.await(5, TimeUnit.SECONDS))
        assertNotNull(got)
        waitUntil { !e.isPlaying }
    }

    @Test
    fun `une ecriture en erreur arrete la lecture et la signale`() {
        val e = makeEngine(failAfter = 1)
        e.setProject(project(100_000))
        val err = CountDownLatch(1)
        e.onError = { err.countDown() }
        e.play(0)
        assertTrue(err.await(5, TimeUnit.SECONDS))
        waitUntil { !e.isPlaying }
        assertTrue(sink().released)
    }

    @Test
    fun `une source absente du disque arrete la lecture proprement`() {
        val p = project(5000)
        File(dir, "sources/a.wav").delete()
        provider.close()
        val e = makeEngine()
        e.setProject(p)
        val err = CountDownLatch(1)
        e.onError = { err.countDown() }
        e.play(0)
        assertTrue(err.await(5, TimeUnit.SECONDS))
        waitUntil { !e.isPlaying }
    }
}
