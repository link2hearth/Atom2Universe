package com.Atom2Universe.app.games.toyboxracers.editor

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import org.json.JSONObject

internal class ToyboxWorldStore(private val context: Context) {
    private val worldDir: File = File(context.filesDir, "toybox_worlds")
    private val worldFile: File = File(worldDir, FILE_NAME)

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
