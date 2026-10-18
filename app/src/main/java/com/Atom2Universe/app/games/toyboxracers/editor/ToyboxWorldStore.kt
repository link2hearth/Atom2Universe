package com.Atom2Universe.app.games.toyboxracers.editor

import android.content.Context
import android.util.AtomicFile
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

internal class ToyboxWorldStore(private val context: Context) {
    private val worldDir: File = File(context.filesDir, "toybox_worlds")
    private val worldFile: File = File(worldDir, FILE_NAME)
    private val creationsDir: File = File(worldDir, "creations")

    fun load(): ToyboxWorld {
        return runCatching {
            ToyboxWorld.fromJson(JSONObject(readAtomically(worldFile)))
        }.getOrElse { ToyboxWorld() }
    }

    fun save(world: ToyboxWorld): File {
        writeAtomically(worldFile, world.toJson().toString(2))
        return worldFile
    }

    fun loadUndoHistory(file: File?): JSONArray? {
        val undoFile = undoFileFor(file)
        val pending = synchronized(undoWrites) { undoWrites[undoFile]?.snapshot }
        if (pending != null) return pending()
        return runCatching { JSONArray(readAtomically(undoFile)) }.getOrNull()
    }

    fun saveUndoHistory(file: File?, snapshot: () -> JSONArray) {
        val undoFile = undoFileFor(file)
        synchronized(undoWrites) {
            undoWrites[undoFile]?.future?.cancel(false)
            val write = UndoWrite(snapshot)
            undoWrites[undoFile] = write
            write.future = undoWriter.schedule({
                try {
                    // Both JSON creation and disk I/O stay off the UI thread.
                    val text = snapshot().toString()
                    if (synchronized(undoWrites) { undoWrites[undoFile] === write }) {
                        writeAtomically(undoFile, text)
                        synchronized(undoWrites) {
                            if (undoWrites[undoFile] === write) undoWrites.remove(undoFile)
                        }
                    }
                } catch (error: Exception) {
                    android.util.Log.e("ToyboxWorldStore", "Unable to save undo history", error)
                }
            }, 350, TimeUnit.MILLISECONDS)
        }
    }

    fun saveCreation(world: ToyboxWorld, name: String = world.name): File {
        creationsDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        val safeName = name
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "_")
            .trim('_')
            .take(64)
            .ifBlank { "creation" }
        // L'horodatage à la seconde et le titre ne constituent pas un identifiant.
        val file = File(creationsDir, "${stamp}_${safeName}_${UUID.randomUUID()}.json")
        writeAtomically(file, world.toJson().toString(2))
        save(world)
        return file
    }

    fun listCreations(): List<File> {
        creationsDir.mkdirs()
        // Lire le catalogue ne détruit jamais un document endommagé ou venant
        // d'une autre version : il reste disponible pour une récupération.
        return creationFiles().sortedByDescending { file ->
            maxOf(file.lastModified(), File(file.path + ".bak").lastModified())
        }
    }

    fun loadCreation(file: File): ToyboxWorld? {
        val canonicalFile = canonicalCreation(file) ?: return null
        return runCatching {
            val json = JSONObject(readAtomically(canonicalFile))
            // fromJson() produit un monde neuf pour une version inconnue :
            // ne pas présenter ce repli comme la création enregistrée.
            if (json.optInt("version", -1) != ToyboxWorld.VERSION ||
                json.optJSONArray("trackSections") == null) return@runCatching null
            ToyboxWorld.fromJson(json)
        }.getOrNull()
    }

    /** Écrase une création déjà nommée (bouton "Sauvegarder" sur une copie
     * qui a déjà un fichier propre) — jamais utilisé pour un monde intégré. */
    fun overwriteCreation(world: ToyboxWorld, file: File): Boolean {
        val canonicalFile = canonicalCreation(file) ?: return false
        if (!canonicalFile.exists() && !File(canonicalFile.path + ".bak").exists()) return false
        writeAtomically(canonicalFile, world.toJson().toString(2))
        save(world)
        return true
    }

    /** [copyName] reçoit le nom d'origine et rend celui de la copie, traduit par l'appelant. */
    fun duplicateCreation(file: File, copyName: (String) -> String): File? {
        val world = loadCreation(file) ?: return null
        val name = copyName(world.name)
        return saveCreation(world.copy(name = name), name)
    }

    fun renameCreation(file: File, name: String): File? {
        val canonicalFile = canonicalCreation(file) ?: return null
        val world = loadCreation(file) ?: return null
        val renamed = world.copy(name = name)
        // Le titre appartient au JSON. Garder l'identité du fichier conserve
        // aussi son historique et évite tout effacement après un renommage.
        return if (overwriteCreation(renamed, canonicalFile)) canonicalFile else null
    }

    fun deleteCreation(file: File): Boolean {
        val canonicalFile = canonicalCreation(file) ?: return false
        val undoFile = File(canonicalFile.parentFile, "${canonicalFile.nameWithoutExtension}.undo.json")
        synchronized(undoWrites) { undoWrites.remove(undoFile)?.future?.cancel(false) }
        // A write already in progress must finish before the final cleanup.
        undoWriter.execute {
            if (synchronized(undoWrites) { !undoWrites.containsKey(undoFile) }) deleteAtomically(undoFile)
        }
        deleteAtomically(undoFile)
        return deleteAtomically(canonicalFile)
    }

    fun creationFileNamed(name: String): File? {
        creationsDir.mkdirs()
        return creationFiles().firstOrNull { it.name == name }
    }

    private fun undoFileFor(file: File?): File {
        if (file == null) return File(worldDir, "${FILE_NAME.removeSuffix(".json")}.undo.json")
        val canonicalFile = canonicalCreation(file)
        return if (canonicalFile != null) {
            File(canonicalFile.parentFile, "${canonicalFile.nameWithoutExtension}.undo.json")
        } else {
            File(worldDir, "${FILE_NAME.removeSuffix(".json")}.undo.json")
        }
    }

    fun exportCopy(world: ToyboxWorld): android.net.Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, FILE_NAME)
        writeAtomically(exportFile, world.toJson().toString(2))
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            exportFile
        )
    }

    fun absoluteSavePath(): String = worldFile.absolutePath

    private fun canonicalCreation(file: File): File? = runCatching {
        file.canonicalFile.takeIf {
            it.parentFile == creationsDir.canonicalFile && it.extension.equals("json", ignoreCase = true) &&
                !it.name.endsWith(".undo.json", ignoreCase = true)
        }
    }.getOrNull()

    private fun creationFiles(): List<File> = creationsDir.listFiles().orEmpty().mapNotNull { entry ->
        if (!entry.isFile) return@mapNotNull null
        // AtomicFile peut avoir conservé seulement la sauvegarde après une
        // interruption. openRead() la rétablira au prochain chargement.
        val base = if (entry.name.endsWith(".json.bak", ignoreCase = true))
            File(entry.parentFile, entry.name.dropLast(4)) else entry
        canonicalCreation(base)
    }.distinct()

    private fun readAtomically(file: File): String = synchronized(storageLock) {
        AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun writeAtomically(file: File, text: String) = synchronized(storageLock) {
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun deleteAtomically(file: File): Boolean = synchronized(storageLock) {
        val existed = file.exists() || File(file.path + ".bak").exists()
        AtomicFile(file).delete()
        existed && !file.exists() && !File(file.path + ".bak").exists()
    }

    companion object {
        private class UndoWrite(val snapshot: () -> JSONArray) {
            var future: ScheduledFuture<*>? = null
        }
        // Shared across activity recreation; a late write cannot overtake a newer write.
        private val undoWrites = mutableMapOf<File, UndoWrite>()
        // AtomicFile garantit le remplacement, pas la concurrence lecture/écriture.
        private val storageLock = Any()
        private val undoWriter = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
        const val FILE_NAME = "toybox_tablet_house.json"
    }
}
