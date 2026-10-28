package com.Atom2Universe.app.music

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Écriture par fichier temporaire puis renommage : une coupure pendant l'écriture laisse
 * l'ancien fichier intact au lieu d'un JSON tronqué.
 */
internal object MusicFileWriter {

    fun writeTextAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("Cannot replace ${file.path}")
        }
    }
}
