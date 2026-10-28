package com.Atom2Universe.app.science

import android.content.Context
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Modules whose offline data is optional and kept outside Git. */
object ScienceAssets {
    private val directories = mapOf(
        "human_biology" to "science/biology",
        "mycology" to "science/mycology",
        "botany" to "science/botany",
        "bestiary" to "science/bestiary",
        "periodic_table" to "science/Elements"
    )

    // Packaged assets cannot change while the app runs; listing a folder of a thousand plates is not free.
    private val known = ConcurrentHashMap<String, Boolean>()

    fun isAvailable(context: Context, moduleId: String): Boolean =
        known.getOrPut(moduleId) { isAvailable(moduleId) { context.assets.list(it) } }

    internal fun isAvailable(moduleId: String, listAssets: (String) -> Array<String>?): Boolean {
        val directory = directories[moduleId] ?: return true
        return try {
            !listAssets(directory).isNullOrEmpty()
        } catch (_: IOException) {
            false
        }
    }
}
