package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.core.Mixer
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.addClip
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.dsp.Amplify
import com.Atom2Universe.app.audioeditor.dsp.ChangeSpeed
import com.Atom2Universe.app.audioeditor.dsp.RenderRange
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import com.Atom2Universe.app.audioeditor.engine.AudioSink
import com.Atom2Universe.app.audioeditor.engine.EffectPreview
import com.Atom2Universe.app.audioeditor.engine.MicCapture
import com.Atom2Universe.app.audioeditor.engine.MicSource
import com.Atom2Universe.app.audioeditor.engine.PreviewBuffer
import com.Atom2Universe.app.audioeditor.engine.PreviewPlayer
import com.Atom2Universe.app.audioeditor.engine.RecordException
import com.Atom2Universe.app.audioeditor.engine.RecordingPlacement
import com.Atom2Universe.app.audioeditor.io.FileSampleProvider
import com.Atom2Universe.app.audioeditor.io.PcmReader
import com.Atom2Universe.app.audioeditor.io.WavFile
import com.Atom2Universe.app.audioeditor.io.WavWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class PreviewRecordingTest {

    private lateinit var dir: File
    private lateinit var provider: FileSampleProvider

    @Before fun setUp() {
        dir = createTempDir("preview")
        File(dir, "sources").mkdirs()
        provider = FileSampleProvider(dir)
    }
    @After fun tearDown() { provider.close(); dir.deleteRecursively() }

    private fun source(id: String, frames: Int, freq: Double = 440.0, amp: Float = 0.3f): Source {
        WavWriter(File(dir, "sources/$id.wav"), 1, 44100, float = false).use { it.write(arrayOf(DspTest.sine(frames, freq, amp)), frames) }
        return Source(id, "sources/$id.wav", 1, frames.toLong(), 44100)
    }

    /** Deux pistes : A (440 Hz) et B (660 Hz), [frames] trames chacune. */
    private fun twoTracks(frames: Int = 88_200): Triple<Project, Int, Int> {
        var p = Project().addSource(source("a", frames)).addSource(source("b", frames, 660.0, 0.2f))
        val (p1, ta) = p.addTrack("A")
        val (p2, tb) = p1.addTrack("B")
        p = p2.addClip(ta, "a", 0).first.addClip(tb, "b", 0).first
        return Triple(p, ta, tb)
    }

    // ---- Aperçu d'un effet ------------------------------------------------------------------

    @Test
    fun `l'apercu est exactement ce que donnera le rendu`() {
        val (p, ta, _) = twoTracks()
        val effect = Amplify(6.0206f)
        val prev = EffectPreview.render(p, provider, listOf(ta), 10_000, 40_000, effect, RunContext(44100))!!
        assertEquals(30_000, prev.frames)

        val applied = RenderRange.apply(p, dir, provider, listOf(ta), 10_000, 40_000, effect, RunContext(44100)).project
        val l = FloatArray(30_000); val r = FloatArray(30_000)
        Mixer(provider).render(applied, 10_000, 30_000, l, r)
        for (i in 0 until 30_000 step 7) { assertEquals(l[i], prev.left[i], 1e-5f); assertEquals(r[i], prev.right[i], 1e-5f) }
    }

    @Test
    fun `l'apercu garde les autres pistes et ne touche pas au disque`() {
        val (p, ta, _) = twoTracks()
        val before = File(dir, "sources").list()!!.sorted()
        val prev = EffectPreview.render(p, provider, listOf(ta), 0, 20_000, Amplify(-120f), RunContext(44100))!!
        // Piste A quasi muette : il reste la piste B, 0,2 de crête.
        assertEquals(0.2f, DspTest.peak(prev.left), 0.01f)
        assertEquals(before, File(dir, "sources").list()!!.sorted())
    }

    @Test
    fun `l'apercu est limite a quelques secondes`() {
        val (p, ta, _) = twoTracks(frames = 44100 * 20)
        val prev = EffectPreview.render(p, provider, listOf(ta), 0, 44100L * 20, Amplify(3f), RunContext(44100))!!
        assertEquals(6 * 44100, prev.frames)
        val short = EffectPreview.render(p, provider, listOf(ta), 0, 44100L * 20, Amplify(3f), RunContext(44100), maxSeconds = 2)!!
        assertEquals(2 * 44100, short.frames)
    }

    @Test
    fun `un effet qui change la duree donne un apercu de la duree du resultat`() {
        val (p, ta, _) = twoTracks()
        val prev = EffectPreview.render(p, provider, listOf(ta), 0, 44100, ChangeSpeed(2f), RunContext(44100))!!
        assertTrue("trames : ${prev.frames}", Math.abs(prev.frames - 22_050) <= 4)
    }

    @Test
    fun `pas d'apercu sans son dans la plage, sur une piste verrouillee ou une plage vide`() {
        val (p, ta, _) = twoTracks(frames = 10_000)
        assertNull(EffectPreview.render(p, provider, listOf(ta), 20_000, 30_000, Amplify(3f), RunContext(44100)))
        assertNull(EffectPreview.render(p, provider, listOf(ta), 100, 100, Amplify(3f), RunContext(44100)))
        val locked = p.mapTrack(ta) { it.copy(locked = true) }
        assertNull(EffectPreview.render(locked, provider, listOf(ta), 0, 5000, Amplify(3f), RunContext(44100)))
        assertNull(EffectPreview.render(p, provider, emptyList(), 0, 5000, Amplify(3f), RunContext(44100)))
    }

    @Test
    fun `l'annulation interrompt l'apercu`() {
        val (p, ta, _) = twoTracks()
        var cancel = false
        val ctx = RunContext(44100, progress = { if (it > 0.2f) cancel = true }, isCancelled = { cancel })
        try {
            EffectPreview.render(p, provider, listOf(ta), 0, 88_200, Amplify(3f), ctx)
            fail("aurait dû être annulé")
        } catch (e: CancellationException) {
            // attendu
        }
    }

    // ---- Lecteur d'aperçu -------------------------------------------------------------------

    private class FakeSink(override val sampleRate: Int, val gated: Boolean) : AudioSink {
        val permits = Semaphore(0)
        @Volatile var written = 0L
        @Volatile var started = false
        @Volatile var released = false
        @Volatile var aborted = false
        override fun write(interleaved: FloatArray, offsetFrames: Int, frames: Int): Int {
            if (aborted) return 0
            if (gated) { permits.acquire(); if (aborted) return 0 }
            written += frames
            return frames
        }
        override fun start() { started = true }
        override fun playedFrames() = written
        override fun drain(timeoutMs: Long) {}
        override fun abort() { aborted = true; permits.release(1000) }
        override fun release() { released = true }
    }

    @Test
    fun `le lecteur d'apercu joue le tampon une fois et previent`() {
        val sinks = ArrayList<FakeSink>()
        val player = PreviewPlayer({ FakeSink(it, false).also { s -> sinks.add(s) } })
        val done = CountDownLatch(1)
        var completed = false
        player.play(PreviewBuffer(FloatArray(10_000), FloatArray(10_000), 44100)) { completed = it; done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(completed)
        assertEquals(10_000L, sinks[0].written)
        assertTrue(sinks[0].started && sinks[0].released)
        assertFalse(player.isPlaying)
    }

    @Test
    fun `arreter l'apercu l'interrompt et un nouvel apercu remplace l'ancien`() {
        val sinks = ArrayList<FakeSink>()
        val player = PreviewPlayer({ FakeSink(it, true).also { s -> synchronized(sinks) { sinks.add(s) } } })
        val first = CountDownLatch(1)
        var firstCompleted = true
        player.play(PreviewBuffer(FloatArray(100_000), FloatArray(100_000), 44100)) { firstCompleted = it; first.countDown() }
        waitFor { synchronized(sinks) { sinks.isNotEmpty() } }
        sinks[0].permits.release(2)
        waitFor { sinks[0].written == 4096L }
        player.stop()
        assertTrue(first.await(5, TimeUnit.SECONDS))
        assertFalse(firstCompleted)
        assertTrue(sinks[0].released)

        // Rejouer arrête l'éventuel aperçu en cours.
        player.play(PreviewBuffer(FloatArray(100_000), FloatArray(100_000), 44100))
        waitFor { synchronized(sinks) { sinks.size == 2 } }
        player.play(PreviewBuffer(FloatArray(100_000), FloatArray(100_000), 44100))
        waitFor { synchronized(sinks) { sinks.size == 3 } }
        assertTrue(sinks[1].released)
        player.stop()
    }

    private fun waitFor(ms: Long = 5000, cond: () -> Boolean) {
        val end = System.nanoTime() + ms * 1_000_000L
        while (!cond()) { if (System.nanoTime() > end) throw AssertionError("délai dépassé"); Thread.sleep(2) }
    }

    // ---- Capture du micro -------------------------------------------------------------------

    /** Un micro factice : sert [data] par morceaux ; une fois épuisé, ne rend plus rien (0) ou une erreur si [failAtEnd]. */
    private class FakeMic(val data: FloatArray, val failAtEnd: Boolean = false, val failStart: Boolean = false) : MicSource {
        override val sampleRate = 44100
        private var pos = 0
        @Volatile var stopped = false
        @Volatile var released = false
        override fun start() { if (failStart) throw IllegalStateException("micro occupé") }
        override fun read(dst: FloatArray, n: Int): Int {
            val m = minOf(n, 1000, data.size - pos)
            if (m <= 0) { Thread.sleep(2); return if (failAtEnd) -1 else 0 }
            System.arraycopy(data, pos, dst, 0, m)
            pos += m
            return m
        }
        override fun stop() { stopped = true }
        override fun release() { released = true }
    }

    @Test
    fun `la capture ecrit un WAV 16 bits mono fidele et mesure la duree par les echantillons`() {
        val x = DspTest.sine(30_000, 440.0, 0.5f)
        val mic = FakeMic(x)
        val cap = MicCapture(mic, File(dir, "rec/a.wav"))
        cap.start()
        waitFor { cap.frames >= 30_000 }
        assertTrue("crête du dernier bloc : " + cap.level, cap.level in 0f..1f)
        val f = cap.stop()
        assertEquals(30_000L, cap.frames)
        assertEquals(30_000.0 / 44100, cap.seconds, 1e-9)
        assertTrue(mic.stopped && mic.released)
        val info = WavFile.readInfo(f)
        assertEquals(1, info.channels); assertEquals(44100, info.sampleRate); assertEquals(16, info.bitsPerSample); assertEquals(30_000L, info.frames)
        val y = arrayOf(FloatArray(30_000))
        PcmReader(f).use { it.read(0, 30_000, y, 0) }
        for (i in x.indices step 5) assertEquals(x[i], y[0][i], 1f / 32768)
        assertEquals(0f, cap.level, 0f)
    }

    @Test
    fun `sans rien recu, l'arret refuse et efface le fichier`() {
        val cap = MicCapture(FakeMic(FloatArray(0)), File(dir, "rec/vide.wav"))
        cap.start()
        Thread.sleep(30)
        try {
            cap.stop()
            fail("aurait dû lever RecordException")
        } catch (e: RecordException) {
            assertEquals(RecordException.Code.NO_DATA, e.code)
        }
        assertFalse(File(dir, "rec/vide.wav").exists())
    }

    @Test
    fun `annuler efface l'enregistrement`() {
        val mic = FakeMic(DspTest.sine(5000, 440.0))
        val cap = MicCapture(mic, File(dir, "rec/c.wav"))
        cap.start()
        waitFor { cap.frames >= 5000 }
        cap.cancel()
        assertFalse(File(dir, "rec/c.wav").exists())
        assertTrue(mic.released)
    }

    @Test
    fun `une erreur de lecture arrete la capture mais garde ce qui est deja ecrit`() {
        val mic = FakeMic(DspTest.sine(8000, 440.0), failAtEnd = true)
        val cap = MicCapture(mic, File(dir, "rec/e.wav"))
        val got = CountDownLatch(1)
        cap.onError = { got.countDown() }
        cap.start()
        assertTrue(got.await(5, TimeUnit.SECONDS))
        assertEquals(RecordException.Code.READ_FAILED, cap.error!!.code)
        val f = cap.stop()
        assertEquals(8000L, WavFile.readInfo(f).frames)
    }

    @Test
    fun `un micro qui ne demarre pas est signale et libere`() {
        val mic = FakeMic(FloatArray(0), failStart = true)
        val cap = MicCapture(mic, File(dir, "rec/s.wav"))
        try {
            cap.start()
            fail("aurait dû lever RecordException")
        } catch (e: RecordException) {
            assertEquals(RecordException.Code.INIT_FAILED, e.code)
        }
        assertTrue(mic.released)
    }

    @Test
    fun `un disque inutilisable est signale comme erreur d'ecriture`() {
        // Le chemin du fichier est un dossier : l'ouverture en écriture échoue.
        val asDir = File(dir, "rec/dossier").also { it.mkdirs() }
        val cap = MicCapture(FakeMic(DspTest.sine(5000, 440.0)), asDir)
        val got = CountDownLatch(1)
        cap.onError = { got.countDown() }
        cap.start()
        assertTrue(got.await(5, TimeUnit.SECONDS))
        assertEquals(RecordException.Code.WRITE_FAILED, cap.error!!.code)
        cap.cancel()
    }

    // ---- Pose de l'enregistrement dans le projet -------------------------------------------

    private fun recording(frames: Int, rate: Int = 44100): File {
        val f = File(dir, "rec_${frames}_$rate.wav")
        WavWriter(f, 1, rate, float = false).use { it.write(arrayOf(DspTest.sine(frames, 440.0, 0.4f, rate)), frames) }
        return f
    }

    @Test
    fun `l'enregistrement devient un clip sur une nouvelle piste a la position du curseur`() {
        val wav = recording(44100)
        val res = RecordingPlacement.place(Project(), dir, wav, null, "Enreg", "Prise 1", at = 10_000, ctx = RunContext(44100)) { "r1" }
        val p = res.project
        assertEquals(1, p.tracks.size)
        assertEquals("Enreg", p.tracks[0].name)
        val c = p.tracks[0].clips.single()
        assertEquals(10_000L, c.start); assertEquals(44100L, c.length); assertEquals("Prise 1", c.name)
        assertEquals("r1", c.sourceId)
        assertTrue(File(dir, "sources/r1.wav").isFile && File(dir, "sources/r1.peaks").isFile)
        assertFalse("le WAV de la capture est effacé", wav.exists())
        assertEquals(listOf("r1"), res.newSources.map { it.id })
    }

    @Test
    fun `un projet en 48 kHz recoit l'enregistrement rééchantillonné`() {
        val wav = recording(44100)
        val res = RecordingPlacement.place(Project(sampleRate = 48000), dir, wav, null, "t", "c", 0, RunContext(48000)) { "r2" }
        val src = res.project.sources["r2"]!!
        assertEquals(48000, src.sampleRate)
        assertTrue(Math.abs(src.frames - 48000) <= 2)
    }

    @Test
    fun `l'enregistrement peut aller sur une piste existante`() {
        var p = Project()
        val (p1, t) = p.addTrack("Voix")
        p = p1
        val res = RecordingPlacement.place(p, dir, recording(2000), t, "inutile", "c", 500, RunContext(44100)) { "r3" }
        assertEquals(1, res.project.tracks.size)
        assertEquals(500L, res.project.tracks[0].clips.single().start)
    }

    @Test
    fun `la compensation de latence avance le clip, ou rogne son debut s'il passerait avant zero`() {
        val a = RecordingPlacement.place(Project(), dir, recording(5000), null, "t", "c", at = 10_000, ctx = RunContext(44100), latencyFrames = 600) { "l1" }
        assertEquals(9_400L, a.project.tracks[0].clips.single().start)
        assertEquals(5000L, a.project.tracks[0].clips.single().length)

        val b = RecordingPlacement.place(Project(), dir, recording(5000), null, "t", "c", at = 100, ctx = RunContext(44100), latencyFrames = 600) { "l2" }
        val c = b.project.tracks[0].clips.single()
        assertEquals(0L, c.start); assertEquals(500L, c.srcStart); assertEquals(4500L, c.length)
    }
}
