package com.Atom2Universe.app.games.toyboxracers.editor

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        return creationsDir
            .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
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

    companion object {
        const val FILE_NAME = "toybox_tablet_house.json"
    }
}
