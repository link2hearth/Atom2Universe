package com.Atom2Universe.app.audioeditor.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException

/** Un fichier importé : la source écrite dans le projet et le nom à donner au clip (nom du fichier, sans extension). */
class ImportedAudio(val source: Source, val displayName: String)

/**
 * Importe un fichier audio (ou la bande son d'une vidéo) dans un projet : décodage, rééchantillonnage à
 * la fréquence du projet, écriture en WAV 16 bits avec ses crêtes.
 *
 * Le décodeur du téléphone passe en premier ([MediaDecoderStream]) ; s'il refuse le fichier, on le
 * convertit d'abord en WAV avec FFmpeg, qui lit bien plus de formats, puis on importe ce WAV.
 * À appeler hors du fil principal.
 */
class AudioImporter(private val context: Context) {

    fun import(uri: Uri, projectDir: File, projectRate: Int, ctx: RunContext): ImportedAudio {
        val name = displayName(uri)
        val source = try {
            MediaDecoderStream.open(context, uri).use { SourceImporter.import(it, projectDir, projectRate, ctx) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            importWithFfmpeg(uri, projectDir, projectRate, ctx, e)
        }
        return ImportedAudio(source, name)
    }

    fun importFile(file: File, projectDir: File, projectRate: Int, ctx: RunContext): ImportedAudio {
        val r = import(Uri.fromFile(file), projectDir, projectRate, ctx)
        return ImportedAudio(r.source, file.nameWithoutExtension)
    }

    private fun importWithFfmpeg(uri: Uri, projectDir: File, projectRate: Int, ctx: RunContext, cause: Exception): Source {
        val tmpDir = File(context.cacheDir, "audio_import").also { it.mkdirs() }
        val token = UUID.randomUUID().toString().take(8)
        val input = File(tmpDir, "in_$token")
        val wav = File(tmpDir, "out_$token.wav")
        try {
            val opened = context.contentResolver.openInputStream(uri) ?: throw IOException("Fichier illisible", cause)
            opened.use { src -> input.outputStream().use { src.copyTo(it) } }
            // Pas de conversion de fréquence ici : SourceImporter s'en charge avec le même rééchantillonneur pour tous les chemins.
            FfmpegRunner.run(
                listOf("-y", "-i", input.path, "-vn", "-map", "0:a:0", "-c:a", "pcm_s16le", wav.path),
                0, ctx,
            )
            return WavPcmStream(wav).use { SourceImporter.import(it, projectDir, projectRate, ctx) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Import impossible : ${e.message}", cause)
        } finally {
            input.delete()
            wav.delete()
        }
    }

    /** Le nom du fichier sans extension (vide s'il est inconnu). */
    fun displayName(uri: Uri): String {
        val raw = if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
        } else {
            uri.lastPathSegment
        }
        return (raw ?: "").substringBeforeLast('.').trim()
    }
}
