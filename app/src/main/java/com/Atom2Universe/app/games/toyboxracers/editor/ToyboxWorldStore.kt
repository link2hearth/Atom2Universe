package com.Atom2Universe.app.games.toyboxracers.editor

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        if (!worldFile.exists()) return ToyboxWorld()
        return runCatching {
            ToyboxWorld.fromJson(JSONObject(worldFile.readText()))
        }.getOrElse { ToyboxWorld() }
    }

    fun save(world: ToyboxWorld): File {
        worldDir.mkdirs()
        worldFile.writeText(world.toJson().toString(2))
        return worldFile
    }

    fun loadUndoHistory(file: File?): JSONArray? {
        val undoFile = undoFileFor(file)
        val pending = synchronized(undoWrites) { undoWrites[undoFile]?.snapshot }
        if (pending != null) return pending()
        if (!undoFile.exists()) return null
        return runCatching { JSONArray(undoFile.readText()) }.getOrNull()
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
                        undoFile.parentFile?.mkdirs()
                        undoFile.writeText(text)
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
            .ifBlank { "creation" }
        val file = File(creationsDir, "${stamp}_$safeName.json")
        file.writeText(world.toJson().toString(2))
        save(world)
        return file
    }

    fun listCreations(): List<File> {
        creationsDir.mkdirs()
        cleanupInvalidCreations()
        return creationsDir
            .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) && !file.name.endsWith(".undo.json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun loadCreation(file: File): ToyboxWorld? {
        val canonicalDir = creationsDir.canonicalFile
        val canonicalFile = file.canonicalFile
        if (!canonicalFile.path.startsWith(canonicalDir.path)) return null
        return runCatching {
            ToyboxWorld.fromJson(JSONObject(canonicalFile.readText()))
        }.getOrNull()
    }

    /** Écrase une création déjà nommée (bouton "Sauvegarder" sur une copie
     * qui a déjà un fichier propre) — jamais utilisé pour un monde intégré. */
    fun overwriteCreation(world: ToyboxWorld, file: File): Boolean {
        val canonicalDir = creationsDir.canonicalFile
        val canonicalFile = file.canonicalFile
        if (!canonicalFile.path.startsWith(canonicalDir.path) || !canonicalFile.exists()) return false
        canonicalFile.writeText(world.toJson().toString(2))
        save(world)
        return true
    }

    fun duplicateCreation(file: File): File? {
        val world = loadCreation(file) ?: return null
        return saveCreation(world, "${world.name}_copie")
    }

    fun renameCreation(file: File, name: String): File? {
        val world = loadCreation(file) ?: return null
        val renamed = world.copy(name = name)
        val newFile = saveCreation(renamed, name)
        deleteCreation(file)
        return newFile
    }

    fun deleteCreation(file: File): Boolean {
        val canonicalDir = creationsDir.canonicalFile
        val canonicalFile = file.canonicalFile
        if (!canonicalFile.path.startsWith(canonicalDir.path)) return false
        val undoFile = File(canonicalFile.parentFile, "${canonicalFile.nameWithoutExtension}.undo.json")
        synchronized(undoWrites) { undoWrites.remove(undoFile)?.future?.cancel(false) }
        // A write already in progress must finish before the final cleanup.
        undoWriter.execute {
            if (synchronized(undoWrites) { !undoWrites.containsKey(undoFile) }) undoFile.delete()
        }
        if (undoFile.exists()) undoFile.delete()
        return canonicalFile.delete()
    }

    fun creationFileNamed(name: String): File? {
        creationsDir.mkdirs()
        return creationsDir
            .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) && !file.name.endsWith(".undo.json") }
            ?.firstOrNull { it.name == name }
    }

    private fun undoFileFor(file: File?): File {
        if (file == null) return File(worldDir, "${FILE_NAME.removeSuffix(".json")}.undo.json")
        val canonicalDir = creationsDir.canonicalFile
        val canonicalFile = file.canonicalFile
        return if (canonicalFile.path.startsWith(canonicalDir.path)) {
            File(canonicalFile.parentFile, "${canonicalFile.nameWithoutExtension}.undo.json")
        } else {
            File(worldDir, "${FILE_NAME.removeSuffix(".json")}.undo.json")
        }
    }

    fun exportCopy(world: ToyboxWorld): android.net.Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, FILE_NAME).apply {
            writeText(world.toJson().toString(2))
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            exportFile
        )
    }

    fun absoluteSavePath(): String = worldFile.absolutePath

    private fun cleanupInvalidCreations() {
        creationsDir
            .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) && !file.name.endsWith(".undo.json") }
            ?.forEach { file ->
                val valid = runCatching {
                    val json = JSONObject(file.readText())
                    json.optInt("version", -1) == ToyboxWorld.VERSION &&
                        json.optJSONArray("trackSections") != null
                }.getOrDefault(false)
                if (!valid) deleteCreation(file)
            }
    }

    companion object {
        private class UndoWrite(val snapshot: () -> JSONArray) {
            var future: ScheduledFuture<*>? = null
        }
        // Shared across activity recreation; a late write cannot overtake a newer write.
        private val undoWrites = mutableMapOf<File, UndoWrite>()
        private val undoWriter = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
        const val FILE_NAME = "toybox_tablet_house.json"
    }
}
