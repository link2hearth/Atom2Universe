package com.Atom2Universe.app.zoomcanvas.core

import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Ce que la galerie affiche d'un projet, lu dans l'en-tête sans charger les traits. */
class ZoomProjectSummary(
    val id: String,
    val name: String,
    val modified: Long,
    val ratio: Double,
    val layerCount: Int,
    val strokeCount: Int,
)

class ZoomProjectMeta(val id: String, var name: String, val created: Long, var modified: Long)

class LoadedZoomProject(val meta: ZoomProjectMeta, val scene: ZoomScene)

/**
 * Une copie figée de ce qu'il faut écrire, prise sur le fil principal (les traits sont immuables,
 * on ne copie que les listes) et écrite ensuite sur un autre fil.
 *
 * Seules les couches utiles sont gardées : de la plus haute couche dessinée (ou de la caméra) à
 * la plus profonde. Les couches vides coincées entre les deux ne gardent que leur ancre.
 */
class ZoomSnapshot(
    val meta: ZoomProjectMeta,
    val ratio: Double,
    val camDepth: Long,
    val cx: Double,
    val cy: Double,
    val zoom: Double,
    val nextStrokeId: Long,
    val firstDepth: Long,
    val layers: List<LayerData>,
    val contentVersion: Long,
) {
    class LayerData(
        val ax: Double,
        val ay: Double,
        val strokes: List<Stroke>,
        val images: List<ImageItem> = emptyList(),
        val shapes: List<ShapeItem> = emptyList(),
        val texts: List<TextItem> = emptyList(),
        /** L'ordre de dessin s'il n'est plus celui de la pose (sinon null : l'ordre des identifiants). */
        val order: List<Long>? = null,
    )

    companion object {
        fun of(meta: ZoomProjectMeta, scene: ZoomScene): ZoomSnapshot {
            val all = scene.allLayers()
            var lo = scene.depth
            var hi = scene.depth
            for (l in all) if (!l.isEmpty) { lo = min(lo, l.depth); hi = max(hi, l.depth) }
            val kept = ArrayList<LayerData>()
            for (d in lo..hi) {
                val l = scene.layer(d)!!
                kept.add(LayerData(l.ax, l.ay, ArrayList(l.strokes), ArrayList(l.images), ArrayList(l.shapes), ArrayList(l.texts), if (l.hasCustomOrder) ArrayList(l.orderIds) else null))
            }
            return ZoomSnapshot(meta, scene.ratio, scene.depth, scene.cx, scene.cy, scene.zoom, scene.nextStrokeId, lo, kept, scene.contentVersion)
        }
    }
}

/**
 * Les projets du canvas infini, un dossier chacun :
 *
 *     <racine>/<id>/scene.bin   en-tête (nom, dates, rapport d'échelle, caméra) puis les couches
 *                  images/<clé>  les images importées (une fois chacune, telles que réduites à l'import)
 *                  thumb.png    vignette de la galerie
 *
 * Tout est en coordonnées locales à chaque couche (`Double`), jamais en valeurs absolues. Chaque
 * écriture passe par un fichier temporaire synchronisé sur le disque puis renommé : une coupure en
 * plein milieu laisse la version précédente intacte. La précédente est aussi gardée en `.bak`, relue
 * si jamais la principale était illisible.
 */
class ZoomStore(val root: File) {

    init {
        root.mkdirs()
    }

    fun dir(id: String) = File(root, id)
    fun thumbFile(id: String) = File(dir(id), "thumb.png")
    private fun sceneFile(id: String) = File(dir(id), "scene.bin")
    private fun backupFile(id: String) = File(dir(id), "scene.bak")
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

    fun list(): List<ZoomProjectSummary> {
        val out = ArrayList<ZoomProjectSummary>()
        for (d in root.listFiles().orEmpty()) {
            if (!d.isDirectory) continue
            val summary = readSummary(File(d, "scene.bin"), d.name) ?: readSummary(File(d, "scene.bak"), d.name)
            if (summary != null) out.add(summary)
        }
        return out.sortedByDescending { it.modified }
    }

    fun create(name: String, ratio: Double, now: Long = System.currentTimeMillis()): LoadedZoomProject {
        val meta = ZoomProjectMeta(UUID.randomUUID().toString(), name, now, now)
        dir(meta.id).mkdirs()
        val scene = ZoomScene(ratio)
        save(ZoomSnapshot.of(meta, scene))
        return LoadedZoomProject(meta, scene)
    }

    fun load(id: String): LoadedZoomProject? =
        read(sceneFile(id), id) ?: read(backupFile(id), id)

    /** Écriture atomique : fichier temporaire, synchronisation, puis renommage. */
    fun save(s: ZoomSnapshot) {
        val dir = dir(s.meta.id)
        dir.mkdirs()
        val tmp = File(dir, "scene.bin.tmp")
        FileOutputStream(tmp).use { fos ->
            val out = DataOutputStream(BufferedOutputStream(fos, 1 shl 16))
            write(s, out)
            out.flush()
            fos.fd.sync()
        }
        val target = sceneFile(s.meta.id)
        if (target.exists()) {
            val bak = backupFile(s.meta.id)
            bak.delete()
            target.renameTo(bak)
        }
        if (!tmp.renameTo(target)) throw IOException("rename failed: $tmp")
    }

    fun saveThumb(id: String, png: ByteArray) {
        val dir = dir(id)
        if (!dir.isDirectory) return
        val tmp = File(dir, "thumb.png.tmp")
        tmp.writeBytes(png)
        if (!tmp.renameTo(thumbFile(id))) tmp.delete()
    }

    fun rename(id: String, name: String) {
        val p = load(id) ?: return
        p.meta.name = name
        save(ZoomSnapshot.of(p.meta, p.scene))
    }

    fun duplicate(id: String, name: String): String? {
        val p = load(id) ?: return null
        val now = System.currentTimeMillis()
        val meta = ZoomProjectMeta(UUID.randomUUID().toString(), name, now, now)
        File(dir(id), "images").takeIf { it.isDirectory }?.copyRecursively(File(dir(meta.id), "images"), overwrite = true)
        save(ZoomSnapshot.of(meta, p.scene))
        thumbFile(id).takeIf { it.isFile }?.copyTo(thumbFile(meta.id), overwrite = true)
        return meta.id
    }

    fun delete(id: String) {
        dir(id).deleteRecursively()
    }

    // ---- Format --------------------------------------------------------------------------

    private fun write(s: ZoomSnapshot, out: DataOutputStream) {
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeUTF(s.meta.name)
        out.writeLong(s.meta.created)
        out.writeLong(s.meta.modified)
        out.writeDouble(s.ratio)
        out.writeInt(s.layers.count { it.strokes.isNotEmpty() || it.images.isNotEmpty() || it.shapes.isNotEmpty() || it.texts.isNotEmpty() })
        out.writeInt(s.layers.sumOf { it.strokes.size + it.images.size + it.shapes.size + it.texts.size })
        out.writeLong(s.camDepth)
        out.writeDouble(s.cx)
        out.writeDouble(s.cy)
        out.writeDouble(s.zoom)
        out.writeLong(s.nextStrokeId)
        out.writeLong(s.firstDepth)
        out.writeInt(s.layers.size)
        for (l in s.layers) {
            out.writeDouble(l.ax)
            out.writeDouble(l.ay)
            out.writeInt(l.strokes.size)
            for (st in l.strokes) {
                out.writeLong(st.id)
                out.writeInt(st.color)
                out.writeDouble(st.width)
                out.writeByte(st.kind)
                out.writeDouble(st.x)
                out.writeDouble(st.y)
                out.writeInt(st.pts.size)
                for (v in st.pts) out.writeDouble(v)
            }
            out.writeInt(l.images.size)
            for (im in l.images) {
                out.writeLong(im.id)
                out.writeUTF(im.key)
                out.writeInt(im.pxW)
                out.writeInt(im.pxH)
                out.writeDouble(im.x)
                out.writeDouble(im.y)
                out.writeDouble(im.w)
                out.writeDouble(im.h)
            }
            out.writeInt(l.shapes.size)
            for (sh in l.shapes) {
                out.writeLong(sh.id)
                out.writeByte(sh.kind.ordinal)
                out.writeDouble(sh.x)
                out.writeDouble(sh.y)
                out.writeDouble(sh.w)
                out.writeDouble(sh.h)
                out.writeByte((if (sh.flipX) 1 else 0) or (if (sh.flipY) 2 else 0))
                out.writeInt(sh.strokeColor)
                out.writeInt(sh.fillColor)
                out.writeByte(sh.fill.ordinal)
                out.writeDouble(sh.width)
            }
            out.writeInt(l.texts.size)
            for (t in l.texts) {
                out.writeLong(t.id)
                out.writeUTF(t.text)
                out.writeDouble(t.x)
                out.writeDouble(t.y)
                out.writeDouble(t.w)
                out.writeDouble(t.h)
                out.writeDouble(t.fontSize)
                out.writeInt(t.color)
                out.writeByte(t.style)
                out.writeUTF(t.font)
            }
            val order = l.order
            out.writeByte(if (order != null) 1 else 0)
            if (order != null) {
                out.writeInt(order.size)
                for (id in order) out.writeLong(id)
            }
        }
        out.writeInt(END_MAGIC)
    }

    private fun readSummary(f: File, id: String): ZoomProjectSummary? {
        if (!f.isFile) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(f), 512)).use { inp ->
                if (inp.readInt() != MAGIC) return null
                if (inp.readInt() > VERSION) return null
                val name = inp.readUTF()
                inp.readLong()
                val modified = inp.readLong()
                val ratio = inp.readDouble()
                val layers = inp.readInt()
                val strokes = inp.readInt()
                ZoomProjectSummary(id, name, modified, ratio, layers, strokes)
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun read(f: File, id: String): LoadedZoomProject? {
        if (!f.isFile) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(f), 1 shl 16)).use { inp ->
                if (inp.readInt() != MAGIC) return null
                val version = inp.readInt()
                if (version > VERSION || version == PIXEL_VERSION) return null
                val name = inp.readUTF()
                val created = inp.readLong()
                val modified = inp.readLong()
                val ratio = inp.readDouble()
                inp.readInt()
                inp.readInt()
                val camDepth = inp.readLong()
                val cx = inp.readDouble()
                val cy = inp.readDouble()
                val zoom = inp.readDouble()
                val nextId = inp.readLong()
                val first = inp.readLong()
                val n = inp.readInt()
                if (n < 0 || !(ratio > 1.0) || !ratio.isFinite()) return null
                val layers = ArrayList<Layer>(n)
                for (i in 0 until n) {
                    val l = Layer(first + i, inp.readDouble(), inp.readDouble())
                    val sc = inp.readInt()
                    for (k in 0 until sc) {
                        val sid = inp.readLong()
                        val color = inp.readInt()
                        val width = inp.readDouble()
                        val kind = if (version >= 5) inp.readByte().toInt() else Stroke.PEN
                        val x = inp.readDouble()
                        val y = inp.readDouble()
                        val np = inp.readInt()
                        if (np < 0 || np % 2 != 0) return null
                        val pts = DoubleArray(np) { inp.readDouble() }
                        l.add(Stroke(sid, x, y, pts, color, width, kind))
                    }
                    if (version >= 2) {
                        val ic = inp.readInt()
                        if (ic < 0) return null
                        for (k in 0 until ic) {
                            val iid = inp.readLong()
                            val key = inp.readUTF()
                            val pw = inp.readInt()
                            val ph = inp.readInt()
                            val item = ImageItem(iid, key, pw, ph, inp.readDouble(), inp.readDouble(), inp.readDouble(), inp.readDouble())
                            if (pw <= 0 || ph <= 0 || key.contains('/') || key.contains("..")) return null
                            l.addImage(item)
                        }
                    }
                    if (version >= 6) {
                        val kinds = ShapeKind.values()
                        val fills = ShapeFill.values()
                        val shc = inp.readInt()
                        if (shc < 0) return null
                        for (k in 0 until shc) {
                            val shId = inp.readLong()
                            val kind = kinds.getOrNull(inp.readByte().toInt()) ?: return null
                            val x = inp.readDouble()
                            val y = inp.readDouble()
                            val w = inp.readDouble()
                            val h = inp.readDouble()
                            val flips = inp.readByte().toInt()
                            val stroke = inp.readInt()
                            val fillColor = inp.readInt()
                            val fill = fills.getOrNull(inp.readByte().toInt()) ?: return null
                            val width = inp.readDouble()
                            l.addBox(ShapeItem(shId, kind, x, y, w, h, flips and 1 != 0, flips and 2 != 0, stroke, fillColor, fill, width))
                        }
                        val tc = inp.readInt()
                        if (tc < 0) return null
                        for (k in 0 until tc) {
                            val tid = inp.readLong()
                            val text = inp.readUTF()
                            val x = inp.readDouble()
                            val y = inp.readDouble()
                            val w = inp.readDouble()
                            val h = inp.readDouble()
                            val size = inp.readDouble()
                            val color = inp.readInt()
                            val style = inp.readByte().toInt()
                            val font = inp.readUTF()
                            l.addBox(TextItem(tid, text, x, y, w, h, size, color, style, font))
                        }
                    }
                    // Les éléments ont été lus par sorte : l'ordre par défaut est celui des identifiants.
                    l.resetOrderById()
                    if (version >= 7 && inp.readByte().toInt() == 1) {
                        val oc = inp.readInt()
                        if (oc < 0) return null
                        l.setOrder(List(oc) { inp.readLong() })
                    }
                    layers.add(l)
                }
                if (inp.readInt() != END_MAGIC) return null
                val scene = ZoomScene(ratio)
                // La caméra est hors des couches lues (fichier bricolé) : on repart d'une vue sûre.
                val camOk = n > 0 && camDepth in first until first + n
                scene.restore(first, layers, if (camOk) camDepth else first, cx, cy, zoom, nextId)
                LoadedZoomProject(ZoomProjectMeta(id, name, created, modified), scene)
            }
        } catch (e: IOException) {
            null
        }
    }

    companion object {
        private const val MAGIC = 0x41325A43 // « A2ZC »
        private const val END_MAGIC = 0x454E4421 // « END! » : un fichier tronqué est rejeté
        /**
         * 1 : traits seuls ; 2 : plus les images de chaque couche ; 4 : même contenu que 2, épaisseurs
         * choisies à l'écran ; 5 : plus l'outil de chaque trait (crayon, pinceau, feutre) ; 6 : plus les
         * formes et les textes de chaque couche ; 7 : plus l'ordre de dessin de chaque couche, quand il n'est plus celui de la pose. Le 3 (couches en pixels, essai du 30/09 abandonné) reste listé dans la
         * galerie pour pouvoir le supprimer, mais ne s'ouvre plus.
         */
        private const val VERSION = 7
        private const val PIXEL_VERSION = 3
    }
}
