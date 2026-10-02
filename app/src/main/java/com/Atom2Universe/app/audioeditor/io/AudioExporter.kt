package com.Atom2Universe.app.audioeditor.io

import com.Atom2Universe.app.audioeditor.core.Levels
import com.Atom2Universe.app.audioeditor.core.Mixer
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.SampleProvider
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** Les formats d'export. Mêmes encodeurs que l'ancien éditeur : ce sont ceux que la variante FFmpeg du projet embarque. */
enum class ExportFormat(val extension: String) {
    WAV16("wav"), WAV24("wav"), WAV32F("wav"), MP3("mp3"), AAC("m4a"), FLAC("flac"), OGG("ogg");

    val lossy get() = this == MP3 || this == AAC || this == OGG
}

class ExportTags(val title: String = "", val artist: String = "", val album: String = "", val year: String = "") {
    val isEmpty get() = title.isBlank() && artist.isBlank() && album.isBlank() && year.isBlank()
}

/**
 * @param to fin de la plage en trames ; −1 = jusqu'à la fin du projet
 * @param sampleRate fréquence de sortie ; 0 = celle du projet
 * @param mono mélange les deux voies en une
 */
class ExportOptions(
    val format: ExportFormat,
    val bitrateKbps: Int = 192,
    val from: Long = 0,
    val to: Long = -1,
    val tags: ExportTags = ExportTags(),
    val sampleRate: Int = 0,
    val mono: Boolean = false,
)

/** [peak] : crête du mixage avant écrêtage ; au-dessus de 1, un format entier ou un encodeur a coupé le signal. */
class ExportResult(val file: File, val frames: Long, val peak: Float) {
    val clipped get() = peak > 1f
}

/** Ce que [OfflineMix.render] a écrit. */
class MixResult(val frames: Long, val peak: Float)

/** Le mixage d'un projet vers un WAV, sans temps réel : même [Mixer] que la lecture. */
object OfflineMix {

    const val BLOCK = 8192

    /**
     * Mixe `[from, to)` dans [file] (WAV 16 bits, ou flottant 32 bits si [float] : rien n'est alors écrêté).
     * L'annulation efface le fichier. Rend le nombre de trames et la crête avant écrêtage.
     */
    fun render(
        project: Project,
        provider: SampleProvider,
        from: Long,
        to: Long,
        file: File,
        float: Boolean,
        mono: Boolean,
        ctx: RunContext,
    ): MixResult {
        val mixer = Mixer(provider)
        val l = FloatArray(BLOCK)
        val r = FloatArray(BLOCK)
        val levels = Levels()
        val outCh = if (mono) 1 else 2
        val out = arrayOf(l, r)
        val total = maxOf(1L, to - from)
        var pos = from
        var peak = 0f
        try {
            WavWriter(file, outCh, project.sampleRate, float).use { w ->
                while (pos < to) {
                    ctx.checkCancelled()
                    val n = minOf(BLOCK.toLong(), to - pos).toInt()
                    mixer.render(project, pos, n, l, r, levels)
                    if (mono) for (i in 0 until n) l[i] = (l[i] + r[i]) * 0.5f
                    peak = maxOf(peak, if (mono) maxAbs(l, n) else maxOf(levels.peakL, levels.peakR))
                    w.write(out, n)
                    pos += n
                    ctx.report((pos - from).toFloat() / total)
                }
            }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        return MixResult(pos - from, peak)
    }

    private fun maxAbs(a: FloatArray, n: Int): Float {
        var m = 0f
        for (i in 0 until n) { val v = if (a[i] < 0f) -a[i] else a[i]; if (v > m) m = v }
        return m
    }
}

/** Construit la ligne de commande FFmpeg d'un export. Fonction pure : on la teste sans FFmpeg. */
object FfmpegArgs {

    /** [input] : le WAV flottant mixé ; [output] : le fichier final. Les deux doivent être des chemins absolus. */
    fun build(input: File, output: File, o: ExportOptions, projectRate: Int): List<String> {
        require(input.isAbsolute && output.isAbsolute) { "chemins absolus requis" }
        val a = ArrayList<String>()
        a += listOf("-y", "-i", input.path)
        a += "-vn"
        val br = "${o.bitrateKbps.coerceIn(32, 512)}k"
        when (o.format) {
            ExportFormat.WAV16 -> a += listOf("-c:a", "pcm_s16le")
            ExportFormat.WAV24 -> a += listOf("-c:a", "pcm_s24le")
            ExportFormat.WAV32F -> a += listOf("-c:a", "pcm_f32le")
            ExportFormat.MP3 -> a += listOf("-b:a", br, "-id3v2_version", "3")
            ExportFormat.AAC -> a += listOf("-c:a", "aac", "-b:a", br)
            ExportFormat.FLAC -> a += listOf("-compression_level", "8")
            ExportFormat.OGG -> a += listOf("-c:a", "libvorbis", "-b:a", br)
        }
        if (o.sampleRate > 0 && o.sampleRate != projectRate) a += listOf("-ar", o.sampleRate.toString())
        // Les métadonnées passent en arguments séparés : aucun échappement à maintenir.
        fun tag(key: String, value: String) {
            val v = value.replace('\n', ' ').replace('\r', ' ').trim()
            if (v.isNotEmpty()) a += listOf("-metadata", "$key=$v")
        }
        tag("title", o.tags.title)
        tag("artist", o.tags.artist)
        tag("album", o.tags.album)
        tag("date", o.tags.year)
        a += output.path
        return a
    }
}

/** Ce qui sait lancer une ligne de commande FFmpeg : [FfmpegTranscoder] en vrai, un faux dans les tests. */
fun interface Transcoder {
    fun run(args: List<String>, durationMs: Long, ctx: RunContext)
}

/**
 * Exporte un projet : mixage hors ligne dans un WAV, puis — sauf si ce WAV est déjà le résultat —
 * transcodage par [transcoder] (FFmpeg en vrai, un faux dans les tests).
 *
 * Un WAV 16 bits ou flottant à la fréquence du projet s'écrit directement, sans FFmpeg ; tout le reste
 * (MP3, AAC, FLAC, OGG, WAV 24 bits, autre fréquence) passe par un WAV flottant temporaire dans [tmpDir].
 */
class AudioExporter(private val provider: SampleProvider, private val transcoder: Transcoder) {

    fun export(project: Project, o: ExportOptions, outFile: File, tmpDir: File, ctx: RunContext): ExportResult {
        val from = o.from.coerceAtLeast(0)
        val to = if (o.to < 0) project.length else minOf(o.to, project.length)
        if (to <= from) throw IOException("Plage d'export vide")
        outFile.parentFile?.mkdirs()

        val direct = (o.format == ExportFormat.WAV16 || o.format == ExportFormat.WAV32F) &&
            (o.sampleRate == 0 || o.sampleRate == project.sampleRate)
        if (direct) {
            val m = OfflineMix.render(project, provider, from, to, outFile, o.format == ExportFormat.WAV32F, o.mono, ctx)
            return ExportResult(outFile, m.frames, m.peak)
        }

        tmpDir.mkdirs()
        val tmp = File(tmpDir, "export_" + UUID.randomUUID().toString().take(8) + ".wav")
        try {
            val mixCtx = sub(ctx, 0f, 0.5f)
            val m = OfflineMix.render(project, provider, from, to, tmp, float = true, mono = o.mono, ctx = mixCtx)
            val durationMs = m.frames * 1000L / project.sampleRate
            transcoder.run(FfmpegArgs.build(tmp, outFile, o, project.sampleRate), durationMs, sub(ctx, 0.5f, 1f))
            if (!outFile.isFile) throw IOException("Le fichier exporté n'a pas été créé")
            return ExportResult(outFile, m.frames, m.peak)
        } catch (e: Throwable) {
            outFile.delete()
            throw e
        } finally {
            tmp.delete()
        }
    }

    /**
     * Un fichier par piste, dans [outDir] : `01 - Nom.ext`. Chaque piste est exportée avec ses propres
     * réglages (volume, panoramique, enveloppe) mais ni sourdine ni solo : on exporte ce qu'on a demandé.
     * Les pistes sans clip sont ignorées.
     */
    fun exportTracks(project: Project, o: ExportOptions, outDir: File, tmpDir: File, ctx: RunContext): List<ExportResult> {
        val tracks = project.tracks.filter { it.clips.isNotEmpty() }
        val results = ArrayList<ExportResult>()
        for ((i, t) in tracks.withIndex()) {
            val single = project.copy(tracks = listOf(t.copy(mute = false, solo = false)))
            // Le numéro d'ordre rend les noms uniques même si deux pistes portent le même titre.
            val base = "%02d - %s".format(i + 1, safeName(t.name))
            results += export(single, o, File(outDir, "$base.${o.format.extension}"), tmpDir, sub(ctx, i.toFloat() / tracks.size, (i + 1f) / tracks.size))
        }
        return results
    }

    private fun sub(ctx: RunContext, a: Float, b: Float) = RunContext(
        ctx.sampleRate,
        progress = { f -> ctx.report(a + (b - a) * f) },
        isCancelled = { runCatching { ctx.checkCancelled() }.isFailure },
    )

    companion object {
        /** Un nom de fichier sûr : sans séparateur ni caractère interdit sur les systèmes de fichiers courants. */
        fun safeName(name: String): String {
            val s = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().trim('.').take(80)
            return s.ifEmpty { "track" }
        }
    }
}
