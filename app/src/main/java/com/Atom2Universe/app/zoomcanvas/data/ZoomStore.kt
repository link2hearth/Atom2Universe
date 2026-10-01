package com.Atom2Universe.app.zoomcanvas.data

import com.Atom2Universe.app.zoomcanvas.core.ImageItem
import com.Atom2Universe.app.zoomcanvas.core.Layer
import com.Atom2Universe.app.zoomcanvas.core.LayerItem
import com.Atom2Universe.app.zoomcanvas.core.SceneDelta
import com.Atom2Universe.app.zoomcanvas.core.ZoomCodec
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** Ce que la galerie affiche d'un projet, lu dans la base sans charger un seul élément. */
class ZoomProjectSummary(
    val id: String,
    val name: String,
    val modified: Long,
    val ratio: Double,
    val layerCount: Int,
    val itemCount: Int,
    /** Ce que le projet pèse sur l'appareil : le dessin (dans la base), les images et la vignette. */
    val sizeBytes: Long,
)

/** [pid] : la clé du projet dans la base ; [id] : son nom de dossier et ce que voit l'interface. */
class ZoomProjectMeta(val id: String, var name: String, val created: Long, var modified: Long, val pid: Long = 0)

class LoadedZoomProject(val meta: ZoomProjectMeta, val scene: ZoomScene)

/**
 * Les projets du canvas infini : le dessin dans la base ([ZoomDatabase], un élément par ligne),
 * les images et la vignette dans un dossier par projet :
 *
 *     <racine>/<id>/images/<clé>   les images importées (une fois chacune, telles que réduites à l'import)
 *                  thumb.png       vignette de la galerie
 *
 * Tout est en coordonnées locales à chaque couche, jamais en valeurs absolues. L'enregistrement
 * ([save]) n'écrit que ce qui a changé depuis le précédent, en une seule transaction : une coupure
 * en plein milieu laisse l'état d'avant, jamais la moitié d'un trait.
 */
class ZoomStore(val root: File, private val db: ZoomDatabase) {

    private val dao = db.dao()

    init {
        root.mkdirs()
    }

    fun dir(id: String) = File(root, id)
    fun thumbFile(id: String) = File(dir(id), "thumb.png")
    fun imageFile(id: String, key: String) = File(File(dir(id), "images"), key)

    /** Range une image importée dans le projet (écriture atomique). */
    fun saveImage(id: String, key: String, bytes: ByteArray) {
        val f = imageFile(id, key)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "$key.tmp")
        FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
        if (!tmp.renameTo(f)) throw IOException("rename failed: $tmp")
    }

    /** Supprime les fichiers d'images que plus rien n'utilise (à l'ouverture, historique vide). */
    fun deleteUnusedImages(id: String, used: Set<String>) {
        File(dir(id), "images").listFiles()?.forEach { if (it.name !in used) it.delete() }
    }

    fun list(): List<ZoomProjectSummary> =
        dao.summaries().map { ZoomProjectSummary(it.uuid, it.name, it.modified, it.ratio, it.layerCount, it.itemCount, it.dataBytes + filesSize(dir(it.uuid))) }

    /** Octets des fichiers d'un projet (images importées, vignette). */
    private fun filesSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun create(name: String, ratio: Double, now: Long = System.currentTimeMillis()): LoadedZoomProject {
        val uuid = UUID.randomUUID().toString()
        val scene = ZoomScene(ratio)
        val pid = db.runInTransaction<Long> {
            val pid = dao.insertProject(ZcProject(uuid = uuid, name = name, created = now, modified = now, ratio = ratio, camDepth = 0, cx = 0.0, cy = 0.0, zoom = scene.zoom, nextId = 1))
            dao.putLayers(listOf(ZcLayer(pid, 0, 0.0, 0.0, 0, 0, 0.0, 0.0, 0.0, 0.0)))
            pid
        }
        dir(uuid).mkdirs()
        return LoadedZoomProject(ZoomProjectMeta(uuid, name, now, now, pid), scene)
    }

    /**
     * Les éléments d'une couche, lus par morceaux de [PAGE] avec leur rang de dessin (un élément aux octets
     * abîmés est perdu, pas la couche). C'est ce que la scène demande quand la caméra s'approche d'une
     * couche qu'elle avait déchargée.
     */
    fun loadLayerItems(pid: Long, depth: Long): List<Pair<LayerItem, Long>> {
        val items = ArrayList<Pair<LayerItem, Long>>()
        var after = Long.MIN_VALUE
        while (true) {
            val page = dao.itemsPage(pid, depth, after, PAGE)
            for (row in page) ZoomCodec.decode(row.type, row.itemId, row.data)?.let { items.add(it to row.z) }
            if (page.size < PAGE) break
            after = page.last().itemId
        }
        return items
    }

    /** Les images que le projet utilise, toutes couches comprises, même celles qui ne sont pas en mémoire. */
    fun usedImageKeys(pid: Long): Set<String> {
        val keys = HashSet<String>()
        for (row in dao.itemsOfType(pid, ZoomCodec.IMAGE)) (ZoomCodec.decode(row.type, row.itemId, row.data) as? ImageItem)?.let { keys.add(it.key) }
        return keys
    }

    /**
     * Ouvre un projet : la scène, avec seulement les couches proches de la caméra en mémoire (voir
     * [ZoomScene.updateResidency]) ; les autres ne gardent que leur résumé et se chargent à la demande.
     * Avec [all], toutes les couches sont lues (un export, un test). Null s'il n'existe pas ou s'il est abîmé.
     */
    fun load(id: String, all: Boolean = false): LoadedZoomProject? {
        val p = dao.project(id) ?: return null
        if (!(p.ratio > 1.0) || !p.ratio.isFinite()) return null
        val rows = dao.layers(p.pid)
        // Les couches forment une suite sans trou : sinon la base n'est pas dans l'état où on l'a laissée.
        for (i in 1 until rows.size) if (rows[i].depth != rows[i - 1].depth + 1) return null
        val first = rows.firstOrNull()?.depth ?: p.camDepth
        // La caméra est hors des couches lues (base bricolée) : on repart d'une vue sûre.
        val camOk = rows.isNotEmpty() && p.camDepth in first until first + rows.size
        val cam = if (camOk) p.camDepth else first
        val layers = ArrayList<Layer>(rows.size)
        for (r in rows) {
            val near = all || r.depth in cam - ZoomScene.LOAD_ABOVE..cam + ZoomScene.LOAD_BELOW
            val layer = when {
                // Vide : rien à lire. Proche de la caméra : on la lit. Loin : un résumé suffit.
                r.count == 0 -> Layer(r.depth, r.ax, r.ay)
                near -> Layer(r.depth, r.ax, r.ay).also { it.load(loadLayerItems(p.pid, r.depth)) }
                else -> Layer.shell(r.depth, r.ax, r.ay, r.count, r.erasers, if (r.count > r.erasers) doubleArrayOf(r.minX, r.minY, r.maxX, r.maxY) else null)
            }
            layers.add(layer)
        }
        val scene = ZoomScene(p.ratio)
        scene.restore(first, layers, cam, p.cx, p.cy, p.zoom, p.nextId)
        return LoadedZoomProject(ZoomProjectMeta(p.uuid, p.name, p.created, p.modified, p.pid), scene)
    }

    /**
     * Écrit ce qui a changé ([delta], pris par [ZoomScene.drainChanges]) en une transaction : les
     * éléments retirés, les éléments posés ou remplacés, les couches, la caméra. Coûte le nombre de
     * changements, pas la taille du dessin. Se lance hors du fil de l'écran.
     */
    fun save(meta: ZoomProjectMeta, delta: SceneDelta) {
        val pid = meta.pid
        db.runInTransaction {
            for (chunk in delta.deletes.asList().chunked(DELETE_CHUNK)) dao.deleteItems(pid, chunk)
            for (chunk in delta.upserts.chunked(WRITE_CHUNK)) {
                dao.putItems(chunk.map { u ->
                    ZcItem(pid, u.entry.id, u.depth, ZoomCodec.typeOf(u.entry.item), u.entry.z, ZoomCodec.encode(u.entry.item))
                })
            }
            if (delta.layers.isNotEmpty()) {
                dao.putLayers(delta.layers.map {
                    val b = it.bounds
                    ZcLayer(pid, it.depth, it.ax, it.ay, it.count, it.erasers, b?.get(0) ?: 0.0, b?.get(1) ?: 0.0, b?.get(2) ?: 0.0, b?.get(3) ?: 0.0)
                })
            }
            dao.pruneLayers(pid, delta.firstDepth, delta.lastDepth)
            dao.saveCamera(pid, delta.camDepth, delta.cx, delta.cy, delta.zoom, delta.nextId)
            if (delta.itemsChanged) dao.touch(pid, meta.modified)
        }
    }

    fun saveThumb(id: String, png: ByteArray) {
        val dir = dir(id)
        if (!dir.isDirectory) return
        val tmp = File(dir, "thumb.png.tmp")
        tmp.writeBytes(png)
        if (!tmp.renameTo(thumbFile(id))) tmp.delete()
    }

    fun rename(id: String, name: String) {
        val p = dao.project(id) ?: return
        dao.rename(p.pid, name, System.currentTimeMillis())
    }

    fun duplicate(id: String, name: String): String? {
        val p = dao.project(id) ?: return null
        val now = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString()
        db.runInTransaction {
            val pid = dao.insertProject(ZcProject(uuid = uuid, name = name, created = now, modified = now, ratio = p.ratio, camDepth = p.camDepth, cx = p.cx, cy = p.cy, zoom = p.zoom, nextId = p.nextId))
            dao.copyLayers(p.pid, pid)
            dao.copyItems(p.pid, pid)
        }
        File(dir(id), "images").takeIf { it.isDirectory }?.copyRecursively(File(dir(uuid), "images"), overwrite = true)
        thumbFile(id).takeIf { it.isFile }?.copyTo(thumbFile(uuid), overwrite = true)
        return uuid
    }

    fun delete(id: String) {
        dao.project(id)?.let { dao.deleteProject(it.pid) }
        dir(id).deleteRecursively()
    }

    private companion object {
        /** Éléments lus d'un coup : assez gros pour aller vite, assez petits pour tenir dans une fenêtre du curseur. */
        const val PAGE = 2000
        /** Identifiants par `DELETE` : bien sous la limite de variables d'une requête SQLite. */
        const val DELETE_CHUNK = 500
        /** Éléments encodés et écrits d'un coup (on n'encode jamais tout un lot en mémoire à la fois). */
        const val WRITE_CHUNK = 1000
    }
}
