package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.core.Marker
import com.Atom2Universe.app.audioeditor.core.Mixer
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.addClip
import com.Atom2Universe.app.audioeditor.core.addEnvelopePoint
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.core.clearEnvelope
import com.Atom2Universe.app.audioeditor.core.moveEnvelopePoint
import com.Atom2Universe.app.audioeditor.core.removeEnvelopePoint
import com.Atom2Universe.app.audioeditor.dsp.Analysis
import com.Atom2Universe.app.audioeditor.dsp.MemoryReader
import com.Atom2Universe.app.audioeditor.dsp.MixDown
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import com.Atom2Universe.app.audioeditor.dsp.Spectrogram
import com.Atom2Universe.app.audioeditor.io.FileSampleProvider
import com.Atom2Universe.app.audioeditor.io.MarkerText
import com.Atom2Universe.app.audioeditor.io.WavWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

/** Les briques de l'étape 8 : enveloppe, repères en texte, mixage de pistes, analyse, spectrogramme. */
class Step8Test {

    private val rate = 44100

    // ---- Enveloppe ----------------------------------------------------------------------------

    private fun oneTrack(): Pair<Project, Int> = Project().addTrack("A").let { (p, t) -> p to t }

    @Test
    fun `les points d'enveloppe restent tries et un point a la meme trame est remplace`() {
        var (p, t) = oneTrack()
        p = p.addEnvelopePoint(t, 1000, 0.5f).first
        p = p.addEnvelopePoint(t, 200, 1f).first
        val (q, idx) = p.addEnvelopePoint(t, 600, 1.5f)
        assertEquals(listOf(200L, 600L, 1000L), q.track(t)!!.envelope.map { it.frame })
        assertEquals(1, idx)
        val (r, same) = q.addEnvelopePoint(t, 600, 0.25f)
        assertEquals(1, same)
        assertEquals(3, r.track(t)!!.envelope.size)
        assertEquals(0.25f, r.track(t)!!.envelope[1].gain, 0f)
    }

    @Test
    fun `le gain d'un point est borne a l'echelle de l'outil`() {
        val (p, t) = oneTrack()
        val q = p.addEnvelopePoint(t, 0, 9f).first.addEnvelopePoint(t, 10, -3f).first
        assertEquals(listOf(2f, 0f), q.track(t)!!.envelope.map { it.gain })
    }

    @Test
    fun `un point deplace ne depasse jamais ses voisins`() {
        var (p, t) = oneTrack()
        for (f in listOf(100L, 200L, 300L)) p = p.addEnvelopePoint(t, f, 1f).first
        assertEquals(299L, p.moveEnvelopePoint(t, 1, 5000, 1f).track(t)!!.envelope[1].frame)
        assertEquals(101L, p.moveEnvelopePoint(t, 1, -50, 1f).track(t)!!.envelope[1].frame)
        assertEquals(0L, p.moveEnvelopePoint(t, 0, -50, 1f).track(t)!!.envelope[0].frame)
        assertEquals(9000L, p.moveEnvelopePoint(t, 2, 9000, 0.5f).track(t)!!.envelope[2].frame)
        // Un rang qui n'existe pas ne change rien.
        assertEquals(p, p.moveEnvelopePoint(t, 7, 10, 1f))
    }

    @Test
    fun `supprimer un point et effacer l'enveloppe, sauf sur une piste verrouillee`() {
        var (p, t) = oneTrack()
        for (f in listOf(100L, 200L, 300L)) p = p.addEnvelopePoint(t, f, 1f).first
        assertEquals(listOf(100L, 300L), p.removeEnvelopePoint(t, 1).track(t)!!.envelope.map { it.frame })
        assertTrue(p.clearEnvelope(t).track(t)!!.envelope.isEmpty())
        val locked = p.mapTrack(t) { it.copy(locked = true) }
        assertEquals(locked, locked.removeEnvelopePoint(t, 0))
        assertEquals(locked, locked.clearEnvelope(t))
        assertEquals(-1, locked.addEnvelopePoint(t, 5, 1f).second)
    }

    // ---- Repères en texte ---------------------------------------------------------------------

    @Test
    fun `les reperes s'ecrivent au format des etiquettes d'Audacity et se relisent a l'identique`() {
        val markers = listOf(Marker(2, 66150, 66150, "Refrain"), Marker(1, 44100, 88200, "Intro\tcourte"))
        val text = MarkerText.format(markers, rate)
        assertEquals("1.000000\t2.000000\tIntro courte\n1.500000\t1.500000\tRefrain\n", text)
        val back = MarkerText.parse(text)
        assertEquals(listOf(1.0, 1.5), back.map { it.startSec })
        assertEquals(listOf(2.0, 1.5), back.map { it.endSec })
        assertEquals(listOf("Intro courte", "Refrain"), back.map { it.name })
    }

    @Test
    fun `la lecture des etiquettes ignore ce qu'elle ne comprend pas`() {
        val text = "1,5\t2,5\tVirgule\r\n" +
            "\\\t440.0\t880.0\n" +        // plage de fréquences d'Audacity
            "\n" +
            "abc\t1\tpas un nombre\n" +
            "-1\t2\tnégatif\n" +
            "3\t2\tfin avant début\n" +
            "seulement deux\tchamps\n" +
            "4\t4\n"
        val l = MarkerText.parse(text)
        assertEquals(listOf("Virgule", "fin avant début"), l.map { it.name })
        assertEquals(1.5, l[0].startSec, 0.0)
        assertEquals(2.5, l[0].endSec, 0.0)
        assertEquals(3.0, l[1].endSec, 0.0)   // une fin avant le début est ramenée au début
    }

    // ---- Mixage de pistes ---------------------------------------------------------------------

    private lateinit var dir: File
    private lateinit var provider: FileSampleProvider

    @Before fun setUp() { dir = createTempDir("step8"); File(dir, "sources").mkdirs(); provider = FileSampleProvider(dir) }
    @After fun tearDown() { provider.close(); dir.deleteRecursively() }

    private fun source(id: String, freq: Double, frames: Int): Source {
        WavWriter(File(dir, "sources/$id.wav"), 1, rate, float = false).use { it.write(arrayOf(DspTest.sine(frames, freq, 0.4f)), frames) }
        return Source(id, "sources/$id.wav", 1, frames.toLong(), rate)
    }

    private fun mix(p: Project, n: Int): Pair<FloatArray, FloatArray> {
        val l = FloatArray(n); val r = FloatArray(n)
        Mixer(provider).render(p, 0, n, l, r)
        return l to r
    }

    private fun threeTracks(): Project {
        var p = Project().addSource(source("a", 440.0, 20000)).addSource(source("b", 660.0, 30000))
        val (p1, t1) = p.addTrack("Une"); p = p1.addClip(t1, "a", 0).first
        val (p2, t2) = p.addTrack("Deux"); p = p2.addClip(t2, "b", 5000).first
        val (p3, t3) = p.addTrack("Trois"); p = p3.addClip(t3, "a", 0).first
        return p.mapTrack(t1) { it.copy(pan = -0.5f, volume = 0.8f) }.mapTrack(t2) { it.copy(mute = true) }
    }

    @Test
    fun `mixer deux pistes en une garde le son, le volume et le panoramique, meme d'une piste en sourdine`() {
        val p = threeTracks()
        val ids = p.tracks.take(2).map { it.id }
        val res = MixDown.apply(p, dir, provider, ids, "Mixage", RunContext(rate))
        val q = res.project
        // Deux pistes remplacées par une, à la place de la première ; la troisième n'a pas bougé.
        assertEquals(listOf("Mixage", "Trois"), q.tracks.map { it.name })
        assertEquals(1, res.newSources.size)
        val clip = q.tracks[0].clips.single()
        assertEquals(35000L, clip.length)                      // la plus longue des deux : 5 000 + 30 000
        assertEquals(2, q.sources[clip.sourceId]!!.channels)
        assertTrue(File(dir, q.sources[clip.sourceId]!!.file).isFile)
        // Le même son qu'avant, la piste en sourdine en plus (le mixage a pris tout ce qu'on lui a désigné).
        val expected = mix(p.mapTrack(p.tracks[1].id) { it.copy(mute = false) }.let { it.copy(tracks = it.tracks.take(2)) }, 35000)
        val got = mix(q.copy(tracks = q.tracks.take(1)), 35000)
        for (i in 0 until 35000 step 97) {
            assertEquals("gauche $i", expected.first[i], got.first[i], 2e-4f)
            assertEquals("droite $i", expected.second[i], got.second[i], 2e-4f)
        }
    }

    @Test
    fun `mixer sans piste utilisable ne change rien, et l'annulation efface le fichier`() {
        val p = threeTracks()
        val locked = p.tracks.map { it.id }.fold(p) { acc, id -> acc.mapTrack(id) { it.copy(locked = true) } }
        assertEquals(locked, MixDown.apply(locked, dir, provider, locked.tracks.map { it.id }, "x", RunContext(rate)).project)
        val before = File(dir, "sources").listFiles()!!.size
        var calls = 0
        try {
            MixDown.apply(p, dir, provider, p.tracks.map { it.id }, "x", RunContext(rate, isCancelled = { ++calls > 2 }))
            assertTrue("l'annulation aurait dû lever", false)
        } catch (e: CancellationException) {
            assertEquals(before, File(dir, "sources").listFiles()!!.size)
        }
    }

    // ---- Analyse ------------------------------------------------------------------------------

    @Test
    fun `les mesures d un sinus - crete, niveau efficace, aucune saturation`() {
        val x = DspTest.sine(rate, 1000.0, 0.5f)
        val s = Analysis.stats(MemoryReader(arrayOf(x, x.copyOf())), RunContext(rate))
        assertEquals(rate.toLong(), s.frames)
        assertEquals(0.5f, s.peak, 0.001f)
        assertEquals(0.5 / Math.sqrt(2.0), s.rms.toDouble(), 0.002)
        assertEquals(0L, s.clipped)
        assertEquals(0f, s.dc, 0.001f)
    }

    @Test
    fun `les saturations sont comptees et regroupees en series`() {
        val x = FloatArray(rate * 2) { 0.2f }
        for (i in 1000 until 1100) x[i] = 1f                 // une série de 100
        for (i in 50_000 until 50_010) x[i] = -1f            // une autre, loin de la première
        val s = Analysis.stats(MemoryReader(arrayOf(x)), RunContext(rate))
        assertEquals(110L, s.clipped)
        assertEquals(listOf(1000L, 50_000L), s.clipRuns)
        assertEquals(s.peakL, s.peakR, 0f)  // une voie : les deux crêtes sont la même
    }

    @Test
    fun `le decalage continu se mesure`() {
        val x = FloatArray(10_000) { 0.1f }
        assertEquals(0.1f, Analysis.stats(MemoryReader(arrayOf(x)), RunContext(rate)).dc, 1e-4f)
    }

    @Test
    fun `le spectre d'un sinus a son pic a sa frequence et pres de son niveau`() {
        val x = DspTest.sine(rate, 1000.0, 0.5f)
        val sp = Analysis.spectrum(MemoryReader(arrayOf(x)), RunContext(rate))
        val peakBin = sp.indices.maxByOrNull { sp[it] }!!
        assertEquals(1000.0, peakBin * rate / 4096.0, 15.0)
        assertEquals(-6.0, sp[peakBin].toDouble(), 1.5)       // 0,5 = −6 dB, au plus 1,4 dB de perte entre deux cases
        assertTrue("loin du pic, presque rien", sp[peakBin + 200] < -60f)
    }

    @Test
    fun `le spectre d'une plage plus courte qu'une fenetre ne plante pas`() {
        val sp = Analysis.spectrum(MemoryReader(arrayOf(DspTest.sine(1000, 1000.0, 0.5f))), RunContext(rate))
        assertEquals(2049, sp.size)
        assertTrue(sp.all { it.isFinite() })
    }

    // ---- Spectrogramme ------------------------------------------------------------------------

    @Test
    fun `une tuile cuite met un sinus de 1 kHz dans la bonne ligne, et le silence reste noir`() {
        val layout = Spectrogram.Layout(rate)
        val n = Spectrogram.TILE_FRAMES + Spectrogram.FFT_SIZE
        val tone = Spectrogram.bake(DspTest.sine(n, 1000.0, 0.5f), layout)
        val col = 100
        val top = (0 until Spectrogram.ROWS).maxByOrNull { tone[it * Spectrogram.TILE_COLS + col].toInt() and 0xFF }!!
        val row = Spectrogram.ROWS - 1 - top      // la rangée 0 de la tuile est la plus aiguë
        assertEquals(1000.0, layout.centerHz(row), 250.0)
        assertTrue((tone[top * Spectrogram.TILE_COLS + col].toInt() and 0xFF) > 200)
        val silence = Spectrogram.bake(FloatArray(n), layout)
        assertTrue(silence.all { it.toInt() == 0 })
    }

    @Test
    fun `les lignes couvrent les cases de la FFT, du grave a l'aigu, et la palette monte`() {
        for (r in listOf(44100, 48000, 22050)) {
            val l = Spectrogram.Layout(r)
            for (row in 0 until Spectrogram.ROWS) {
                assertTrue(l.hi[row] > l.lo[row])
                if (row > 0) assertTrue(l.lo[row] >= l.lo[row - 1])
            }
            assertTrue(l.hi.last() <= Spectrogram.FFT_SIZE / 2)
        }
        fun lum(c: Int) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
        for (i in 1 until 256) assertTrue("palette $i", lum(Spectrogram.PALETTE[i]) >= lum(Spectrogram.PALETTE[i - 1]))
        assertEquals(0xFF000004.toInt(), Spectrogram.PALETTE[0])
        assertNotEquals(Spectrogram.PALETTE[0], Spectrogram.PALETTE[255])
    }

    @Test
    fun `le numero de tuile suit les trames de la source`() {
        assertEquals(0, Spectrogram.tileOf(0))
        assertEquals(0, Spectrogram.tileOf(Spectrogram.TILE_FRAMES - 1L))
        assertEquals(1, Spectrogram.tileOf(Spectrogram.TILE_FRAMES.toLong()))
        assertEquals(0, Spectrogram.tileOf(-5))
    }
}
