package com.Atom2Universe.app.zoomcanvas.data

import com.Atom2Universe.app.cloud.projects.CloudModule
import com.Atom2Universe.app.cloud.projects.CloudProjectAdapter
import java.io.InputStream
import java.io.OutputStream

/**
 * Les projets du canvas infini ([single] faux) ou du « Canvas » ([single] vrai) sur Drive. Les deux
 * vivent dans la même base, séparés par leur type : un seul code, deux préfixes de fichier.
 */
class ZoomCloudAdapter(private val store: ZoomStore, private val single: Boolean) : CloudProjectAdapter {

    override val module = if (single) CloudModule.CANVAS else CloudModule.ZOOM

    override fun localModified(id: String): Long? = store.modifiedOf(id)

    override fun nameOf(id: String): String? = store.nameOf(id)

    override fun exportTo(id: String, out: OutputStream) = store.exportProject(id, out)

    override fun importAs(id: String, input: InputStream): Boolean = store.importProject(input, id, single) != null

    override fun importCopy(input: InputStream, nameSuffix: String): String? =
        store.importProject(input, null, single, nameSuffix)
}
