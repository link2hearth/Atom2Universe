package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.core.Mixer
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.addClip
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import com.Atom2Universe.app.audioeditor.io.AudioExporter
import com.Atom2Universe.app.audioeditor.io.ExportFormat
import com.Atom2Universe.app.audioeditor.io.ExportOptions
import com.Atom2Universe.app.audioeditor.io.ExportTags
import com.Atom2Universe.app.audioeditor.io.FfmpegArgs
import com.Atom2Universe.app.audioeditor.io.FileSampleProvider
import com.Atom2Universe.app.audioeditor.io.PcmReader
import com.Atom2Universe.app.audioeditor.io.Transcoder
import com.Atom2Universe.app.audioeditor.io.WavFile
import com.Atom2Universe.app.audioeditor.io.WavWriter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException

class AudioExporterTest {

    private lateinit var dir: File
    private lateinit var out: File
    private lateinit var tmp: File
    private lateinit var provider: FileSampleProvider

    @Before fun setUp() {
        dir = createTempDir("export")
        out = File(dir, "out")
        tmp = File(dir, "tmp")
        File(dir, "sources").mkdirs()
        provider = FileSampleProvider(dir)
    }
    @After fun tearDown() { provider.close(); dir.deleteRecursively() }

    private fun source(id: String, frames: Int, channels: Int = 1, amp: Float = 0.3f, float: Boolean = false): Source {
        val data = Array(channels) { c -> DspTest.sine(frames, 440.0 * (c + 1), amp) }
        WavWriter(File(dir, "sources/$id.wav"), channels, 44100, float).use { it.write(data, frames) }
        return Source(id, "sources/$id.wav", channels, frames.toLong(), 44100, float)
    }

    /** Deux pistes mono d'une seconde : « Voix » (440 Hz) et « Musique » (440 Hz aussi, donc elles s'additionnent). */
    private fun twoTracks(amp: Float = 0.3f): Project {
        var p = Project().addSource(source("a", 44100, amp = amp))
        val (p1, t1) = p.addTrack("Voix")
        val (p2, t2) = p1.addTrack("Musique")
        p = p2.addClip(t1, "a", 0).first
        p = p.addClip(t2, "a", 0).first
        return p
    }

    private fun readAll(f: File): Array<FloatArray> = PcmReader(f).use { r ->
        val o = Array(r.info.channels) { FloatArray(r.info.frames.toInt()) }
        r.read(0, r.info.frames.toInt(), o, 0)
        o
    }

    private fun exporter(t: Transcoder = Transcoder { _, _, _ -> fail("pas de transcodage attendu") }) = AudioExporter(provider, t)
    private fun ctx() = RunContext(44100)

    // ---- WAV direct ---------------------------------------------------------------------

    @Test
    fun `un WAV 16 bits est le mixage exact du projet`() {
        val p = twoTracks()
        val r = exporter().export(p, ExportOptions(ExportFormat.WAV16), File(out, "x.wav"), tmp, ctx())
        assertEquals(44100L, r.frames)
        assertEquals(0.6f, r.peak, 0.01f)
        assertFalse(r.clipped)
        val info = WavFile.readInfo(r.file)
        assertEquals(2, info.channels); assertEquals(16, info.bitsPerSample); assertEquals(44100L, info.frames)
        val y = readAll(r.file)
        val l = FloatArray(44100); val rr = FloatArray(44100)
        Mixer(provider).render(p, 0, 44100, l, rr)
        for (i in 0 until 44100 step 97) { assertEquals(l[i], y[0][i], 1f / 32768); assertEquals(rr[i], y[1][i], 1f / 32768) }
    }

    @Test
    fun `le flottant garde ce qui depasse zero dB et le 16 bits l'ecrete`() {
        val p = twoTracks(amp = 0.8f)
        val f32 = exporter().export(p, ExportOptions(ExportFormat.WAV32F), File(out, "f.wav"), tmp, ctx())
        assertTrue(f32.clipped)
        assertEquals(1.6f, f32.peak, 0.02f)
        assertTrue(WavFile.readInfo(f32.file).float)
        assertEquals(1.6f, DspTest.peak(readAll(f32.file)[0]), 0.02f)

        val i16 = exporter().export(p, ExportOptions(ExportFormat.WAV16), File(out, "i.wav"), tmp, ctx())
        assertTrue(i16.clipped)
        assertTrue(DspTest.peak(readAll(i16.file)[0]) <= 1f)
    }

    @Test
    fun `une plage n'exporte que cette plage`() {
        val p = twoTracks()
        val r = exporter().export(p, ExportOptions(ExportFormat.WAV32F, from = 10_000, to = 30_000), File(out, "r.wav"), tmp, ctx())
        assertEquals(20_000L, r.frames)
        val y = readAll(r.file)[0]
        val l = FloatArray(20_000); val rr = FloatArray(20_000)
        Mixer(provider).render(p, 10_000, 20_000, l, rr)
        for (i in 0 until 20_000 step 53) assertEquals(l[i], y[i], 1e-6f)
    }

    @Test
    fun `une fin au-dela du projet est ramenee a sa fin`() {
        val r = exporter().export(twoTracks(), ExportOptions(ExportFormat.WAV16, from = 40_000, to = 10_000_000), File(out, "z.wav"), tmp, ctx())
        assertEquals(4100L, r.frames)
    }

    @Test
    fun `mono melange les deux voies`() {
        var p = Project().addSource(source("s", 8000, channels = 2, amp = 0.4f))
        val (p1, t) = p.addTrack("st")
        p = p1.addClip(t, "s", 0).first
        val r = exporter().export(p, ExportOptions(ExportFormat.WAV32F, mono = true), File(out, "m.wav"), tmp, ctx())
        assertEquals(1, WavFile.readInfo(r.file).channels)
        val y = readAll(r.file)[0]
        val l = FloatArray(8000); val rr = FloatArray(8000)
        Mixer(provider).render(p, 0, 8000, l, rr)
        for (i in 0 until 8000 step 31) assertEquals((l[i] + rr[i]) / 2, y[i], 1e-6f)
    }

    @Test
    fun `une plage vide est refusee`() {
        try {
            exporter().export(Project(), ExportOptions(ExportFormat.WAV16), File(out, "v.wav"), tmp, ctx())
            fail("aurait dû lever IOException")
        } catch (e: IOException) {
            assertFalse(File(out, "v.wav").exists())
        }
    }

    @Test
    fun `l'annulation efface le fichier`() {
        var cancel = false
        val c = RunContext(44100, progress = { if (it > 0.4f) cancel = true }, isCancelled = { cancel })
        try {
            exporter().export(twoTracks(), ExportOptions(ExportFormat.WAV16), File(out, "c.wav"), tmp, c)
            fail("aurait dû être annulé")
        } catch (e: CancellationException) {
            assertFalse(File(out, "c.wav").exists())
        }
    }

    // ---- Transcodage --------------------------------------------------------------------

    @Test
    fun `un MP3 passe par un WAV flottant temporaire qui disparait ensuite`() {
        val p = twoTracks(amp = 0.8f)
        var seen: List<String> = emptyList()
        var tmpFrames = -1L
        var tmpFloat = false
        var progress = 0f
        val fake = Transcoder { args, durationMs, c ->
            seen = args
            val input = File(args[args.indexOf("-i") + 1])
            val info = WavFile.readInfo(input)
            tmpFrames = info.frames; tmpFloat = info.float
            assertEquals(1000L, durationMs)
            File(args.last()).writeText("mp3 factice")
            c.report(1f)
        }
        val tags = ExportTags(title = "Mon titre", artist = "Moi", year = "2026")
        val target = File(out, "x.mp3")
        val r = exporter(fake).export(p, ExportOptions(ExportFormat.MP3, bitrateKbps = 256, tags = tags), target, tmp, RunContext(44100, progress = { progress = it }))
        assertEquals(44100L, tmpFrames)
        assertTrue(tmpFloat)
        assertEquals(target.path, seen.last())
        assertTrue(r.clipped)           // 1,6 : le MP3 aussi sera écrêté, l'écran doit le dire
        assertEquals(1f, progress)
        assertTrue(target.isFile)
        assertEquals(emptyList<String>(), tmp.list()!!.toList())
    }

    @Test
    fun `un echec de FFmpeg ne laisse ni fichier final ni temporaire`() {
        val fake = Transcoder { args, _, _ ->
            File(args.last()).writeText("à moitié")
            throw IOException("FFmpeg a échoué")
        }
        val target = File(out, "x.ogg")
        try {
            exporter(fake).export(twoTracks(), ExportOptions(ExportFormat.OGG), target, tmp, ctx())
            fail("aurait dû lever IOException")
        } catch (e: IOException) {
            assertEquals("FFmpeg a échoué", e.message)
        }
        assertFalse(target.exists())
        assertEquals(emptyList<String>(), tmp.list()!!.toList())
    }

    @Test
    fun `un WAV 16 bits a une autre frequence passe par FFmpeg`() {
        var args: List<String> = emptyList()
        val fake = Transcoder { a, _, _ -> args = a; File(a.last()).writeText("x") }
        exporter(fake).export(twoTracks(), ExportOptions(ExportFormat.WAV16, sampleRate = 48000), File(out, "x.wav"), tmp, ctx())
        assertEquals("48000", args[args.indexOf("-ar") + 1])
        assertEquals("pcm_s16le", args[args.indexOf("-c:a") + 1])
    }

    @Test
    fun `la ligne de commande de chaque format`() {
        val i = File(dir, "in.wav").absoluteFile
        val o = File(dir, "o.bin").absoluteFile
        fun args(f: ExportFormat, br: Int = 192, tags: ExportTags = ExportTags(), rate: Int = 0) =
            FfmpegArgs.build(i, o, ExportOptions(f, br, tags = tags, sampleRate = rate), 44100)

        val mp3 = args(ExportFormat.MP3, 320)
        assertEquals(listOf("-y", "-i", i.path, "-vn", "-b:a", "320k", "-id3v2_version", "3", o.path), mp3)
        assertEquals(listOf("-c:a", "aac", "-b:a", "128k"), args(ExportFormat.AAC, 128).let { it.subList(4, 8) })
        assertEquals(listOf("-c:a", "libvorbis", "-b:a", "192k"), args(ExportFormat.OGG).let { it.subList(4, 8) })
        assertEquals(listOf("-compression_level", "8"), args(ExportFormat.FLAC).let { it.subList(4, 6) })
        assertEquals("pcm_s24le", args(ExportFormat.WAV24)[5])
        assertEquals("pcm_f32le", args(ExportFormat.WAV32F)[5])
        assertEquals("pcm_s16le", args(ExportFormat.WAV16)[5])
        assertFalse("-ar" in args(ExportFormat.FLAC, rate = 44100))
        assertEquals("22050", args(ExportFormat.FLAC, rate = 22050).let { it[it.indexOf("-ar") + 1] })
        // Débit borné.
        assertEquals("512k", args(ExportFormat.MP3, 9999)[5])
        assertEquals("32k", args(ExportFormat.MP3, 1)[5])
    }

    @Test
    fun `le debit variable passe par la qualite et jamais par -b a`() {
        val i = File(dir, "in.wav").absoluteFile
        val o = File(dir, "o.bin").absoluteFile
        fun vbr(f: ExportFormat, br: Int) = FfmpegArgs.build(i, o, ExportOptions(f, br, vbr = true), 44100)

        val mp3 = vbr(ExportFormat.MP3, 128)
        assertEquals(listOf("-y", "-i", i.path, "-vn", "-q:a", "5", "-id3v2_version", "3", o.path), mp3)
        assertEquals(listOf("-c:a", "libvorbis", "-q:a", "6"), vbr(ExportFormat.OGG, 192).subList(4, 8))
        assertFalse("-b:a" in vbr(ExportFormat.OGG, 192))
        // L'AAC n'a pas de mode variable : l'option est ignorée.
        assertTrue("-b:a" in vbr(ExportFormat.AAC, 128))
        // Les crans du curseur donnent des qualités qui montent avec le débit (0 = meilleure pour LAME).
        val q = listOf(64, 96, 128, 160, 192, 224, 256, 320).map { FfmpegArgs.mp3VbrQuality(it) }
        assertEquals(q.sortedDescending(), q)
        val v = listOf(64, 96, 128, 160, 192, 224, 256, 320).map { FfmpegArgs.vorbisQuality(it) }
        assertEquals(v.sorted(), v)
    }

    @Test
    fun `les metadonnees passent en arguments separes sans echappement`() {
        val tags = ExportTags(title = "L'été \"chaud\" \$HOME `ls`; rm -rf /\nligne 2", artist = "  ", album = "Alb", year = "")
        val a = FfmpegArgs.build(File(dir, "i.wav").absoluteFile, File(dir, "o.mp3").absoluteFile, ExportOptions(ExportFormat.MP3, tags = tags), 44100)
        val meta = a.withIndex().filter { it.value == "-metadata" }.map { a[it.index + 1] }
        assertEquals(listOf("title=L'été \"chaud\" \$HOME `ls`; rm -rf / ligne 2", "album=Alb"), meta)
    }

    @Test
    fun `des chemins relatifs sont refuses`() {
        try {
            FfmpegArgs.build(File("in.wav"), File("-o.mp3"), ExportOptions(ExportFormat.MP3), 44100)
            fail("aurait dû refuser")
        } catch (e: IllegalArgumentException) {
            // attendu : un chemin qui commence par « - » serait pris pour une option
        }
    }

    // ---- Une piste par fichier ----------------------------------------------------------

    @Test
    fun `un fichier par piste avec le nom de la piste`() {
        var p = Project().addSource(source("a", 5000, amp = 0.2f)).addSource(source("b", 3000, amp = 0.5f))
        val (p1, t1) = p.addTrack("Voix / chant : A?")
        val (p2, t2) = p1.addTrack("Basse")
        val (p3, _) = p2.addTrack("Vide")
        val (p4, t4) = p3.addTrack("Basse")
        p = p4.addClip(t1, "a", 0).first.addClip(t2, "b", 0).first.addClip(t4, "b", 100).first
        // Une piste en sourdine est exportée quand même : on l'a demandée.
        p = p.mapTrack(t2) { it.copy(mute = true) }

        val res = exporter().exportTracks(p, ExportOptions(ExportFormat.WAV32F), out, tmp, ctx())
        assertEquals(listOf("01 - Voix _ chant _ A_.wav", "02 - Basse.wav", "03 - Basse.wav"), res.map { it.file.name })
        assertEquals(listOf(5000L, 3000L, 3100L), res.map { it.frames })
        assertEquals(0.2f, DspTest.peak(readAll(res[0].file)[0]), 0.01f)
        assertEquals(0.5f, DspTest.peak(readAll(res[1].file)[0]), 0.01f)
    }

    @Test
    fun `safeName retire les caracteres interdits`() {
        assertEquals("a_b_c", AudioExporter.safeName("a/b\\c"))
        assertEquals("track", AudioExporter.safeName("  ..  "))
        assertEquals("track", AudioExporter.safeName(""))
        assertEquals(80, AudioExporter.safeName("x".repeat(200)).length)
    }
}
