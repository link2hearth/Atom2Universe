package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.core.EditSession
import com.Atom2Universe.app.audioeditor.core.Mixer
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.addClip
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.dsp.Amplify
import com.Atom2Universe.app.audioeditor.dsp.BlockEffect
import com.Atom2Universe.app.audioeditor.dsp.Effect
import com.Atom2Universe.app.audioeditor.dsp.FrameReader
import com.Atom2Universe.app.audioeditor.dsp.FrameWriter
import com.Atom2Universe.app.audioeditor.dsp.RenderRange
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import com.Atom2Universe.app.audioeditor.dsp.StereoToMono
import com.Atom2Universe.app.audioeditor.dsp.TrackRangeReader
import com.Atom2Universe.app.audioeditor.io.FileSampleProvider
import com.Atom2Universe.app.audioeditor.io.PeakFiles
import com.Atom2Universe.app.audioeditor.io.WavFile
import com.Atom2Universe.app.audioeditor.io.WavWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

class RenderRangeTest {

    private lateinit var dir: File
    private lateinit var provider: FileSampleProvider

    @Before fun setUp() { dir = createTempDir("render"); File(dir, "sources").mkdirs(); provider = FileSampleProvider(dir) }
    @After fun tearDown() { provider.close(); dir.deleteRecursively() }

    private fun writeSource(id: String, channels: Int, frames: Int, amp: Float = 0.5f): Source {
        val ch = Array(channels) { c -> DspTest.sine(frames, 440.0 * (c + 1), amp) }
        WavWriter(File(dir, "sources/$id.wav"), channels, 44100, float = false).use { it.write(ch, frames) }
        return Source(id, "sources/$id.wav", channels, frames.toLong(), 44100)
    }

    /** Un projet : une piste, un clip de 44 100 trames d'une source mono. */
    private fun project(channels: Int = 1): Triple<Project, Int, Int> {
        var p = Project().addSource(writeSource("a", channels, 44100))
        val (p1, t) = p.addTrack("Piste 1")
        val (p2, c) = p1.addClip(t, "a", 0)
        p = p2
        return Triple(p, t, c!!)
    }

    private fun mix(p: Project, from: Long, n: Int): FloatArray {
        val l = FloatArray(n); val r = FloatArray(n)
        Mixer(provider).render(p, from, n, l, r)
        return l
    }

    @Test
    fun `amplifier une plage ne change que cette plage`() {
        val (p, t, _) = project()
        val res = RenderRange.apply(p, dir, provider, listOf(t), 10_000, 30_000, Amplify(6.0206f), RunContext(44100))
        val q = res.project
        assertEquals(p.length, q.length)
        val before = mix(p, 0, 44100)
        val after = mix(q, 0, 44100)
        for (i in 0 until 10_000) assertEquals(before[i], after[i], 1e-6f)
        for (i in 10_000 until 30_000) assertEquals("trame $i", before[i] * 2f, after[i], 1e-3f)
        for (i in 30_000 until 44100) assertEquals(before[i], after[i], 1e-6f)
    }

    @Test
    fun `le rendu cree une source flottante avec ses crêtes et ne touche pas a l'ancienne`() {
        val (p, t, _) = project()
        val res = RenderRange.apply(p, dir, provider, listOf(t), 10_000, 30_000, Amplify(12f), RunContext(44100))
        val src = res.newSources.single()
        assertTrue(src.float); assertEquals(20_000L, src.frames)
        val f = File(dir, src.file)
        assertTrue(f.isFile)
        assertEquals(20_000L, WavFile.readInfo(f).frames)
        assertTrue(WavFile.readInfo(f).float)
        assertTrue(File(dir, "sources/${src.id}.peaks").isFile)
        assertEquals(20_000L, PeakFiles.load(File(dir, "sources/${src.id}.peaks"))!!.frames)
        // Les +12 dB dépassent 1,0 : le flottant les garde (un 16 bits les aurait écrêtés).
        val boosted = FloatArray(20_000)
        provider.read(src, 0, 20_000, arrayOf(boosted), 0)
        assertTrue(DspTest.peak(boosted) > 1.5f)
    }

    @Test
    fun `annuler le rendu revient au projet d'avant`() {
        val (p, t, _) = project()
        val s = EditSession(p)
        s.commit("fx", RenderRange.apply(p, dir, provider, listOf(t), 0, 44100, Amplify(3f), RunContext(44100)).project)
        assertTrue(s.project != p)
        s.undo()
        assertEquals(p, s.project)
    }

    @Test
    fun `le lecteur de plage ignore volume panoramique et sourdine`() {
        val (p, t, _) = project()
        val loud = p.mapTrack(t) { it.copy(volume = 0.1f, pan = 1f, mute = true) }
        val r = TrackRangeReader(loud, t, 0, 1000, provider)
        assertEquals(1, r.channels)
        val buf = arrayOf(FloatArray(1000))
        assertEquals(1000, r.read(buf, 1000))
        assertEquals(DspTest.peak(DspTest.sine(1000, 440.0, 0.5f)), DspTest.peak(buf[0]), 2e-3f)
        assertEquals(0, r.read(buf, 1000))
    }

    @Test
    fun `une source stereo donne un lecteur stereo`() {
        val (p, t, _) = project(channels = 2)
        val r = TrackRangeReader(p, t, 0, 100, provider)
        assertEquals(2, r.channels)
        val buf = arrayOf(FloatArray(100), FloatArray(100))
        r.read(buf, 100)
        // Les deux voies de la source sont à 440 et 880 Hz : elles diffèrent.
        assertTrue(buf[0][20] != buf[1][20])
    }

    @Test
    fun `un effet qui change le nombre de canaux produit une source adaptee`() {
        val (p, t, _) = project(channels = 2)
        val res = RenderRange.apply(p, dir, provider, listOf(t), 0, 44100, StereoToMono(), RunContext(44100))
        assertEquals(1, res.newSources.single().channels)
    }

    /** Un effet qui ne garde que la première moitié de l'entrée (la durée change). */
    private class HalfEffect : Effect() {
        override val changesLength get() = true
        override fun run(openInput: () -> FrameReader, out: FrameWriter, ctx: RunContext) {
            val r = openInput()
            val buf = Array(r.channels) { FloatArray(r.frames.toInt()) }
            r.read(buf, r.frames.toInt())
            out.write(buf, r.frames.toInt() / 2)
        }
    }

    @Test
    fun `un effet qui raccourcit tire la suite vers la gauche`() {
        val (p, t, _) = project()
        val q = RenderRange.apply(p, dir, provider, listOf(t), 10_000, 30_000, HalfEffect(), RunContext(44100)).project
        assertEquals(44100L - 10_000, q.length)
    }

    @Test
    fun `une queue se superpose sans decaler la suite`() {
        val (p, t, _) = project()
        val tail = object : BlockEffect() {
            override fun tailFrames(sampleRate: Int) = 5000
            override fun start(channels: Int, sampleRate: Int) {}
            override fun process(buf: Array<FloatArray>, n: Int) {}
        }
        val q = RenderRange.apply(p, dir, provider, listOf(t), 10_000, 30_000, tail, RunContext(44100)).project
        assertEquals(44100L, q.length)
        val last = q.track(t)!!.clips.last()
        assertEquals(30_000L, last.start) // la suite est toujours là où elle était
    }

    @Test
    fun `une annulation efface le fichier en cours et laisse le projet intact`() {
        val (p, t, _) = project()
        var calls = 0
        val ctx = RunContext(44100, isCancelled = { ++calls > 1 })
        var cancelled = false
        try { RenderRange.apply(p, dir, provider, listOf(t), 0, 44100, Amplify(3f), ctx) } catch (e: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertEquals(listOf("a.wav"), File(dir, "sources").list()!!.filter { it.endsWith(".wav") }.sorted())
    }

    @Test
    fun `les pistes verrouillees et sans audio dans la plage sont ignorees`() {
        val (p, t, _) = project()
        val (p2, t2) = p.addTrack("Vide")
        val locked = p2.mapTrack(t) { it.copy(locked = true) }
        val res = RenderRange.apply(locked, dir, provider, listOf(t, t2), 0, 44100, Amplify(3f), RunContext(44100))
        assertTrue(res.newSources.isEmpty())
        assertEquals(locked, res.project)
        assertFalse(File(dir, "sources").list()!!.any { it.endsWith(".peaks") })
    }

    @Test
    fun `plusieurs pistes sont traitees chacune pour elle-meme`() {
        val (p, t, _) = project()
        val (p2, t2) = p.addTrack("B")
        val p3 = p2.addSource(writeSource("b", 1, 44100, 0.2f)).addClip(t2, "b", 0).first
        val seen = ArrayList<Float>()
        val res = RenderRange.apply(p3, dir, provider, listOf(t, t2), 0, 44100, Amplify(6.0206f), RunContext(44100, progress = { seen.add(it) }))
        assertEquals(2, res.newSources.size)
        assertEquals(1f, seen.last(), 1e-6f)
        val l = FloatArray(44100)
        provider.read(res.newSources[1], 0, 44100, arrayOf(l), 0)
        assertEquals(DspTest.peak(DspTest.sine(44100, 440.0, 0.4f)), DspTest.peak(l), 5e-3f)
    }
}
