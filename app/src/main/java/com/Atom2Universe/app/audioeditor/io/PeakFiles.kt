package com.Atom2Universe.app.audioeditor.io

import com.Atom2Universe.app.audioeditor.core.PeakBuilder
import com.Atom2Universe.app.audioeditor.core.PeakData
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.min

/** Calcul, écriture et lecture des fichiers `.peaks` posés à côté des sources. */
object PeakFiles {

    /** Calcule les crêtes d'un WAV en le parcourant une seule fois, par blocs (mémoire constante). */
    fun build(wav: File, chunk: Int = 16384): PeakData = PcmReader(wav).use { reader ->
        val ch = reader.info.channels
        val builder = PeakBuilder(ch)
        val buf = Array(ch) { FloatArray(chunk) }
        var pos = 0L
        while (pos < reader.info.frames) {
            val n = min(chunk.toLong(), reader.info.frames - pos).toInt()
            reader.read(pos, n, buf, 0)
            builder.add(buf, n)
            pos += n
        }
        builder.finish()
    }

    /** Écrit par un fichier temporaire renommé : une coupure n'abîme pas des crêtes déjà valides. */
    fun save(data: PeakData, file: File) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        BufferedOutputStream(FileOutputStream(tmp)).use { data.write(it) }
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw java.io.IOException("Impossible d'écrire ${file.name}")
        }
    }

    /** Les crêtes enregistrées, ou `null` si le fichier manque ou est abîmé (on les recalcule alors). */
    fun load(file: File): PeakData? = try {
        if (!file.isFile) null else BufferedInputStream(FileInputStream(file)).use { PeakData.read(it) }
    } catch (e: Exception) {
        null
    }

    /** Charge les crêtes de [wav] dans [peaks], ou les calcule et les enregistre. */
    fun loadOrBuild(wav: File, peaks: File): PeakData =
        load(peaks) ?: build(wav).also { runCatching { save(it, peaks) } }
}
