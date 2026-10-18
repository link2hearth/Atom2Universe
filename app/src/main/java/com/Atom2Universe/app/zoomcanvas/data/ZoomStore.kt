package com.Atom2Universe.app.zoomcanvas.data

import com.Atom2Universe.app.zoomcanvas.core.ImageItem
import com.Atom2Universe.app.zoomcanvas.core.Layer
import com.Atom2Universe.app.zoomcanvas.core.LayerItem
import com.Atom2Universe.app.zoomcanvas.core.PixelLayer
import com.Atom2Universe.app.zoomcanvas.core.SceneDelta
import com.Atom2Universe.app.zoomcanvas.core.ZoomCodec
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Ce que la galerie affiche d'un projet, lu dans la base sans charger un seul élément. */
class ZoomProjectSummary(
    val id: String,
    val name: String,
    val modified: Long,
    val ratio: Double,
    val single: Boolean,
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

    /** Les projets d'une des deux galeries : les canvas infinis à couches, ou ceux d'une seule couche ([single]). */
    fun list(single: Boolean = false): List<ZoomProjectSummary> =
        dao.summaries(kindOf(single)).map {
            ZoomProjectSummary(it.uuid, it.name, it.modified, it.ratio, it.kind == ZcProject.KIND_SINGLE, it.layerCount, it.itemCount, it.dataBytes + filesSize(dir(it.uuid)))
        }

    private fun kindOf(single: Boolean) = if (single) ZcProject.KIND_SINGLE else ZcProject.KIND_LAYERS

    /** Octets des fichiers d'un projet (images importées, vignette). */
    private fun filesSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun create(name: String, ratio: Double, now: Long = System.currentTimeMillis(), single: Boolean = false): LoadedZoomProject {
        val uuid = UUID.randomUUID().toString()
        val scene = ZoomScene.create(single, ratio)
        val pid = db.runInTransaction<Long> {
            val pid = dao.insertProject(ZcProject(uuid = uuid, name = name, created = now, modified = now, ratio = scene.ratio, camDepth = 0, cx = 0.0, cy = 0.0, zoom = scene.zoom, nextId = 1, kind = kindOf(single)))
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
        val scene = ZoomScene.create(p.kind == ZcProject.KIND_SINGLE, p.ratio)
        scene.restore(first, layers, cam, p.cx, p.cy, p.zoom, p.nextId)
        if (p.kind == ZcProject.KIND_SINGLE) {
            // La couche de pixels se lit d'un bloc : seules ses tuiles colorées existent, et la scène en a besoin pour tracer.
            val tiles = dao.pixelTiles(p.pid).mapNotNull { r -> PixelLayer.decode(r.data)?.let { PixelLayer.tileKey(r.tx, r.ty) to it } }
            scene.restorePixels(tiles, p.pixelsAbove == 1)
        }
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
            for (chunk in delta.pixels.upserts.chunked(WRITE_CHUNK)) {
                dao.putPixelTiles(chunk.map { (k, px) -> ZcPixelTile(pid, PixelLayer.tileX(k), PixelLayer.tileY(k), PixelLayer.encode(px)) })
            }
            for (k in delta.pixels.deletes) dao.deletePixelTile(pid, PixelLayer.tileX(k), PixelLayer.tileY(k))
            if (delta.pixelSettingsChanged) dao.savePixelsAbove(pid, if (delta.pixelsAbove) 1 else 0)
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
            val pid = dao.insertProject(ZcProject(uuid = uuid, name = name, created = now, modified = now, ratio = p.ratio, camDepth = p.camDepth, cx = p.cx, cy = p.cy, zoom = p.zoom, nextId = p.nextId, kind = p.kind, pixelsAbove = p.pixelsAbove))
            dao.copyLayers(p.pid, pid)
            dao.copyItems(p.pid, pid)
            dao.copyPixelTiles(p.pid, pid)
        }
        File(dir(id), "images").takeIf { it.isDirectory }?.copyRecursively(File(dir(uuid), "images"), overwrite = true)
        thumbFile(id).takeIf { it.isFile }?.copyTo(thumbFile(uuid), overwrite = true)
        return uuid
    }

    fun delete(id: String) {
        dao.project(id)?.let { dao.deleteProject(it.pid) }
        dir(id).deleteRecursively()
    }

    /** La date de dernière modification du dessin (elle ne bouge pas quand on déplace seulement la vue) ; null si absent. */
    fun modifiedOf(id: String): Long? = dao.project(id)?.modified

    fun nameOf(id: String): String? = dao.project(id)?.name

    // ---- Fichier de projet (cloud, partage) ---------------------------------------------------------

    /**
     * Écrit un projet entier dans un zip :
     *
     *     manifest.json   le projet, ses couches, la caméra
     *     items.bin       les éléments (un enregistrement par élément, leurs octets tels qu'en base)
     *     tiles.bin       les tuiles de la couche de pixels
     *     thumb.png, images/<clé>
     *
     * Les éléments sont lus par pages, couche après couche : un grand dessin ne tient jamais en mémoire.
     */
    fun exportProject(id: String, out: OutputStream) {
        val p = dao.project(id) ?: throw IOException("projet introuvable : $id")
        val layers = dao.layers(p.pid)
        ZipOutputStream(BufferedOutputStream(out)).use { z ->
            z.putNextEntry(ZipEntry(F_MANIFEST))
            z.write(manifestOf(p, layers).toString().toByteArray(Charsets.UTF_8))
            z.closeEntry()

            z.putNextEntry(ZipEntry(F_ITEMS))
            val items = DataOutputStream(z)
            for (l in layers) {
                var after = Long.MIN_VALUE
                while (true) {
                    val page = dao.itemsPage(p.pid, l.depth, after, PAGE)
                    for (row in page) {
                        items.writeBoolean(true)
                        items.writeLong(row.itemId); items.writeLong(row.depth); items.writeInt(row.type); items.writeLong(row.z)
                        items.writeInt(row.data.size); items.write(row.data)
                    }
                    if (page.size < PAGE) break
                    after = page.last().itemId
                }
            }
            items.writeBoolean(false)
            items.flush()
            z.closeEntry()

            z.putNextEntry(ZipEntry(F_TILES))
            val tiles = DataOutputStream(z)
            for (t in dao.pixelTiles(p.pid)) {
                tiles.writeBoolean(true)
                tiles.writeInt(t.tx); tiles.writeInt(t.ty); tiles.writeInt(t.data.size); tiles.write(t.data)
            }
            tiles.writeBoolean(false)
            tiles.flush()
            z.closeEntry()

            thumbFile(id).takeIf { it.isFile }?.let { f ->
                z.putNextEntry(ZipEntry(F_THUMB)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
            File(dir(id), "images").listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.forEach { f ->
                z.putNextEntry(ZipEntry("$F_IMAGES${f.name}")); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
    }

    private fun manifestOf(p: ZcProject, layers: List<ZcLayer>): JSONObject {
        val arr = JSONArray()
        for (l in layers) {
            arr.put(
                JSONObject().put("depth", l.depth).put("ax", l.ax).put("ay", l.ay).put("count", l.count).put("erasers", l.erasers)
                    .put("minX", l.minX).put("minY", l.minY).put("maxX", l.maxX).put("maxY", l.maxY),
            )
        }
        return JSONObject()
            .put("v", FORMAT).put("name", p.name).put("created", p.created).put("modified", p.modified)
            .put("ratio", p.ratio).put("camDepth", p.camDepth).put("cx", p.cx).put("cy", p.cy).put("zoom", p.zoom)
            .put("nextId", p.nextId).put("kind", p.kind).put("pixelsAbove", p.pixelsAbove)
            .put("layers", arr)
    }

    /**
     * Lit un projet exporté par [exportProject].
     *
     * - [id] donné : le projet prend cet identifiant et **remplace** celui qui le porte (le cloud, c'est
     *   « le même projet, ailleurs »), avec ses dates d'origine ;
     * - [id] null : un nouveau projet, dont le nom est suivi d'une espace puis de [copySuffix].
     *
     * Tout se passe dans une seule transaction, et les images sont déballées à côté puis échangées en
     * dernier : un fichier tronqué ou abîmé ne laisse aucune trace, et le projet qui existait déjà reste tel quel.
     * Un fichier du mauvais type de projet ([single]) est refusé.
     *
     * @return l'identifiant du projet posé, ou null si le fichier est illisible
     */
    fun importProject(input: InputStream, id: String?, single: Boolean, copySuffix: String = ""): String? {
        val uuid = id ?: UUID.randomUUID().toString()
        val incoming = File(root, ".incoming_$uuid")
        val backup = File(root, ".replaced_$uuid")
        incoming.deleteRecursively()
        backup.deleteRecursively()
        incoming.mkdirs()
        try {
            db.runInTransaction {
                dao.project(uuid)?.let { dao.deleteProject(it.pid) }
                readProjectFile(input, uuid, single, id == null, copySuffix, incoming)
                // En dernier : si l'échange des dossiers échoue, la transaction revient en arrière.
                val target = dir(uuid)
                if (target.exists() && !target.renameTo(backup)) throw IOException("dossier occupé : $uuid")
                if (!incoming.renameTo(target)) {
                    if (backup.exists()) backup.renameTo(target)
                    throw IOException("dossier non posé : $uuid")
                }
            }
            backup.deleteRecursively()
            return uuid
        } catch (e: Exception) {
            incoming.deleteRecursively()
            // Si l'ancien dossier a été mis de côté puis la base a refusé, on le remet.
            if (backup.exists() && !dir(uuid).exists()) backup.renameTo(dir(uuid)) else backup.deleteRecursively()
            return null
        }
    }

    private fun readProjectFile(input: InputStream, uuid: String, single: Boolean, asCopy: Boolean, copySuffix: String, incoming: File) {
        val z = ZipInputStream(input.buffered())
        val first = z.nextEntry ?: throw IOException("zip vide")
        if (first.name != F_MANIFEST) throw IOException("manifeste attendu en premier")
        val m = JSONObject(String(z.readBytes(), Charsets.UTF_8))
        if (m.getInt("v") > FORMAT) throw IOException("format trop récent")
        val kind = m.optInt("kind", ZcProject.KIND_LAYERS)
        if (kind != kindOf(single)) throw IOException("autre type de projet")
        val ratio = m.getDouble("ratio")
        if (!(ratio > 1.0) || !ratio.isFinite()) throw IOException("rapport invalide")
        val layerRows = m.getJSONArray("layers")
        val now = System.currentTimeMillis()
        val name = m.optString("name", "").let { if (asCopy) "$it $copySuffix".trim() else it }
        val pid = dao.insertProject(
            ZcProject(
                uuid = uuid, name = name,
                created = if (asCopy) now else m.optLong("created", now), modified = if (asCopy) now else m.optLong("modified", now),
                ratio = ratio, camDepth = m.getLong("camDepth"), cx = m.getDouble("cx"), cy = m.getDouble("cy"), zoom = m.getDouble("zoom"),
                nextId = m.getLong("nextId"), kind = kind, pixelsAbove = m.optInt("pixelsAbove", 0),
            ),
        )
        val layers = ArrayList<ZcLayer>(layerRows.length())
        for (i in 0 until layerRows.length()) {
            val l = layerRows.getJSONObject(i)
            layers.add(ZcLayer(pid, l.getLong("depth"), l.getDouble("ax"), l.getDouble("ay"), l.getInt("count"), l.getInt("erasers"),
                l.getDouble("minX"), l.getDouble("minY"), l.getDouble("maxX"), l.getDouble("maxY")))
        }
        // Mêmes garde-fous qu'à l'ouverture : des couches sans trou (la caméra, elle, est recadrée à la lecture).
        if (layers.isEmpty()) throw IOException("aucune couche")
        for (i in 1 until layers.size) if (layers[i].depth != layers[i - 1].depth + 1) throw IOException("couches non contiguës")
        dao.putLayers(layers)

        var sawItems = false
        var sawTiles = false
        while (true) {
            val e = z.nextEntry ?: break
            when {
                e.name == F_ITEMS -> { readItems(DataInputStream(z), pid); sawItems = true }
                e.name == F_TILES -> { readTiles(DataInputStream(z), pid); sawTiles = true }
                e.name == F_THUMB -> File(incoming, "thumb.png").outputStream().use { z.copyTo(it) }
                e.name.startsWith(F_IMAGES) && !e.isDirectory -> {
                    val key = e.name.removePrefix(F_IMAGES)
                    // Une clé est un nom de fichier, jamais un chemin.
                    if (key.isEmpty() || key.contains('/') || key.contains('\\') || key.startsWith(".")) throw IOException("clé d'image invalide")
                    val f = File(File(incoming, "images").apply { mkdirs() }, key)
                    f.outputStream().use { z.copyTo(it) }
                }
            }
        }
        // Un zip qui s'arrête avant la fin du dessin n'est pas un projet entier.
        if (!sawItems || !sawTiles) throw IOException("fichier incomplet")
    }

    private fun readItems(d: DataInputStream, pid: Long) {
        val batch = ArrayList<ZcItem>(WRITE_CHUNK)
        while (d.readBoolean()) {
            val itemId = d.readLong(); val depth = d.readLong(); val type = d.readInt(); val z = d.readLong()
            val len = d.readInt()
            if (len < 0 || len > MAX_BLOB) throw IOException("élément trop gros")
            val data = ByteArray(len); d.readFully(data)
            batch.add(ZcItem(pid, itemId, depth, type, z, data))
            if (batch.size >= WRITE_CHUNK) { dao.putItems(batch.toList()); batch.clear() }
        }
        if (batch.isNotEmpty()) dao.putItems(batch.toList())
    }

    private fun readTiles(d: DataInputStream, pid: Long) {
        val batch = ArrayList<ZcPixelTile>()
        while (d.readBoolean()) {
            val tx = d.readInt(); val ty = d.readInt(); val len = d.readInt()
            if (len < 0 || len > MAX_BLOB) throw IOException("tuile trop grosse")
            val data = ByteArray(len); d.readFully(data)
            batch.add(ZcPixelTile(pid, tx, ty, data))
            if (batch.size >= WRITE_CHUNK) { dao.putPixelTiles(batch.toList()); batch.clear() }
        }
        if (batch.isNotEmpty()) dao.putPixelTiles(batch.toList())
    }

    private companion object {
        const val FORMAT = 1
        const val F_MANIFEST = "manifest.json"
        const val F_ITEMS = "items.bin"
        const val F_TILES = "tiles.bin"
        const val F_THUMB = "thumb.png"
        const val F_IMAGES = "images/"
        /** Un élément ou une tuile ne pèse jamais autant : au-delà, le fichier est abîmé (et on n'alloue pas ça). */
        const val MAX_BLOB = 64 * 1024 * 1024

        /** Éléments lus d'un coup : assez gros pour aller vite, assez petits pour tenir dans une fenêtre du curseur. */
        const val PAGE = 2000
        /** Identifiants par `DELETE` : bien sous la limite de variables d'une requête SQLite. */
        const val DELETE_CHUNK = 500
        /** Éléments encodés et écrits d'un coup (on n'encode jamais tout un lot en mémoire à la fois). */
        const val WRITE_CHUNK = 1000
    }
}
