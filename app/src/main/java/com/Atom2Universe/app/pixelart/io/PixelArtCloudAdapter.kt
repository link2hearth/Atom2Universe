package com.Atom2Universe.app.pixelart.io

import com.Atom2Universe.app.cloud.projects.CloudModule
import com.Atom2Universe.app.cloud.projects.CloudProjectAdapter
import java.io.InputStream
import java.io.OutputStream

/** Les projets de pixel art sur Drive : le fichier de projet qu'on exporte déjà à la main, rien de plus. */
class PixelArtCloudAdapter(private val store: ProjectStore) : CloudProjectAdapter {

    override val module = CloudModule.PIXEL_ART

    override fun localModified(id: String): Long? = store.modifiedOf(id)

    override fun nameOf(id: String): String? = store.nameOf(id)

    override fun exportTo(id: String, out: OutputStream) = store.exportZip(id, out)

    override fun importAs(id: String, input: InputStream): Boolean = store.importFromCloud(id, input)

    override fun importCopy(input: InputStream, nameSuffix: String): String? {
        val id = store.importZip(input, fallbackName = "") ?: return null
        store.nameOf(id)?.let { store.rename(id, "$it $nameSuffix".trim()) }
        return id
    }
}
