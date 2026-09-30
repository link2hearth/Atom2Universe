package com.Atom2Universe.app.pixelart.io

import com.Atom2Universe.app.pixelart.core.BlendMode
import com.Atom2Universe.app.pixelart.core.Compositor
import com.Atom2Universe.app.pixelart.core.Document
import com.Atom2Universe.app.pixelart.core.Frame
import com.Atom2Universe.app.pixelart.core.Layer
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Les projets vivent chacun dans un dossier :
 *
 *     <racine>/<id>/manifest.json      calques, images, couleurs, lien vers le fichier d'origine…
 *                   cels/<calque>_<image>.cel   pixels (ARGB compressé zlib), un fichier par cel non vide
 *                   thumb.png          vignette de la galerie
 *                   reference.png      image de référence, si le projet en a une
 *
 * L'enregistrement est **incrémental** : seules les cels modifiées sont réécrites (voir
 * [Document.peekDirty]). Toute écriture passe par un fichier temporaire renommé ensuite, donc une
 * coupure en plein milieu laisse l'ancienne version intacte.
 */
class ProjectStore(val root: File) {

    init {
        root.mkdirs()
    }

    fun dir(id: String) = File(root, id)
    fun thumbFile(id: String) = File(dir(id), "thumb.png")
    fun referenceFile(id: String) = File(dir(id), "reference.png")
    private fun manifestFile(id: String) = File(dir(id), "manifest.json")
    private fun celFile(id: String, key: Long) =
        File(File(dir(id), "cels"), "${Document.unpackLayer(key)}_${Document.unpackFrame(key)}.cel")

    // ---- Catalogue -----------------------------------------------------------------------

    fun list(): List<ProjectSummary> {
        val out = ArrayList<ProjectSummary>()
        for (d in root.listFiles().orEmpty()) {
            if (!d.isDirectory) continue
            val mf = File(d, "manifest.json")
            if (!mf.isFile) continue
            try {
                val j = JSONObject(mf.readText())
                out.add(
                    ProjectSummary(
                        id = d.name,
                        name = j.optString("name", ""),
                        width = j.getInt("w"),
                        height = j.getInt("h"),
                        frames = j.getJSONArray("frames").length(),
                        modified = j.optLong("modified"),
                        linkName = j.optJSONObject("link")?.optString("name"),
                    ),
                )
            } catch (e: Exception) {
                // Un manifeste abîmé n'empêche pas d'afficher les autres projets.
            }
        }
        return out.sortedByDescending { it.modified }
    }

    fun exists(id: String) = manifestFile(id).isFile

    // ---- Création ------------------------------------------------------------------------

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    /** Crée un projet vide (un calque, une image) et l'écrit tout de suite. */
    fun create(name: String, width: Int, height: Int, layerName: String = "1"): LoadedProject {
        val doc = Document.blank(width, height, layerName)
        val now = System.currentTimeMillis()
        val meta = ProjectMeta(newId(), name, now, now, activeLayerId = doc.layers[0].id, activeFrameId = doc.frames[0].id)
        return LoadedProject(doc, meta).also { saveNow(it) }
    }

    /** Crée un projet à partir d'une image déjà décodée ; [link] la relie à son fichier d'origine. */
    fun createFromPixels(name: String, width: Int, height: Int, pixels: IntArray, link: SourceLink?, layerName: String = "1"): LoadedProject {
        val doc = Document.blank(width, height, layerName)
        doc.setCel(doc.layers[0].id, doc.frames[0].id, pixels.copyOf())
        val now = System.currentTimeMillis()
        val meta = ProjectMeta(
            newId(), name, now, now,
            activeLayerId = doc.layers[0].id, activeFrameId = doc.frames[0].id, link = link,
        )
        return LoadedProject(doc, meta).also { saveNow(it) }
    }

    /** Crée un projet animé : une image par élément de [frames] (planche de sprites découpée, par exemple). */
    fun createFromFrames(name: String, width: Int, height: Int, frames: List<IntArray>, layerName: String = "1"): LoadedProject {
        val doc = Document.blank(width, height, layerName)
        val layerId = doc.layers[0].id
        doc.setCel(layerId, doc.frames[0].id, frames[0].copyOf())
        for (i in 1 until frames.size) {
            val f = Frame(doc.newFrameId())
            doc.frames.add(f)
            doc.setCel(layerId, f.id, frames[i].copyOf())
        }
        val now = System.currentTimeMillis()
        val meta = ProjectMeta(newId(), name, now, now, activeLayerId = layerId, activeFrameId = doc.frames[0].id)
        return LoadedProject(doc, meta).also { saveNow(it) }
    }

    // ---- Enregistrement ------------------------------------------------------------------

    /**
     * Photographie ce qui doit être écrit. **À appeler sur le fil qui modifie le document** : les cels
     * sont copiées ici, l'écriture ([save]) peut ensuite se faire sur un autre fil.
     */
    fun prepareSave(p: LoadedProject, forceAll: Boolean = false): SavePlan {
        val doc = p.doc
        if (forceAll) doc.markEverythingDirty()
        val dirty = doc.peekDirty()
        val cels = HashMap<Long, IntArray>()
        for (k in dirty.written.keys) doc.celAt(k)?.let { cels[k] = it.copyOf() }
        // La vignette montre la première image : inutile de la refaire quand on dessine ailleurs.
        val firstFrame = doc.frames.firstOrNull()?.id
        val thumbStale = dirty.meta || dirty.removed.isNotEmpty() ||
            dirty.written.keys.any { Document.unpackFrame(it) == firstFrame } || !thumbFile(p.meta.id).isFile
        val thumb = if (thumbStale) makeThumbnail(doc) else null
        p.meta.modified = System.currentTimeMillis()
        val manifest = ManifestCodec.encode(doc, p.meta).toString()
        return SavePlan(p.meta.id, dirty, cels, thumb, manifest, doc.width, doc.height)
    }

    class SavePlan internal constructor(
        val id: String,
        val dirty: Document.DirtySet,
        internal val cels: Map<Long, IntArray>,
        internal val thumb: Thumb?,
        internal val manifest: String,
        internal val width: Int,
        internal val height: Int,
    )

    class Thumb(val pixels: IntArray, val w: Int, val h: Int)

    /** Prépare, écrit et acquitte d'un coup : pour les fils qui n'ont pas d'autre fil à ménager. */
    fun saveNow(p: LoadedProject, forceAll: Boolean = false) {
        val plan = prepareSave(p, forceAll)
        save(plan)
        p.doc.markSaved(plan.dirty)
    }

    /** Écrit le plan sur le disque. Peut tourner hors du fil principal. */
    fun save(plan: SavePlan) {
        val d = dir(plan.id)
        File(d, "cels").mkdirs()
        for (k in plan.dirty.removed.keys) celFile(plan.id, k).delete()
        for ((k, data) in plan.cels) {
            val f = celFile(plan.id, k)
            if (data.all { it == 0 }) f.delete() else writeCel(f, data, plan.width, plan.height)
        }
        plan.thumb?.let { t -> atomicWrite(thumbFile(plan.id)) { PngWriter.write(it, t.pixels, t.w, t.h) } }
        atomicWrite(manifestFile(plan.id)) { it.write(plan.manifest.toByteArray(Charsets.UTF_8)) }
    }

    private fun makeThumbnail(doc: Document): Thumb {
        val frame = doc.frames.firstOrNull()?.id ?: return Thumb(IntArray(1), 1, 1)
        val max = 256
        val s = maxOf(doc.width, doc.height)
        val tw = if (s <= max) doc.width else maxOf(1, doc.width * max / s)
        val th = if (s <= max) doc.height else maxOf(1, doc.height * max / s)
        return Thumb(Compositor.compositeScaled(doc, frame, tw, th), tw, th)
    }

    private fun atomicWrite(target: File, block: (OutputStream) -> Unit) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        BufferedOutputStream(FileOutputStream(tmp)).use { block(it) }
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw java.io.IOException("Impossible d'écrire ${target.name}")
        }
    }

    private fun writeCel(f: File, data: IntArray, w: Int, h: Int) {
        atomicWrite(f) { raw ->
            val head = DataOutputStream(raw)
            head.writeInt(CEL_MAGIC); head.writeInt(w); head.writeInt(h)
            head.flush()
            val z = DeflaterOutputStream(raw, Deflater(Deflater.BEST_SPEED), 1 shl 16)
            val buf = ByteBuffer.allocate(1 shl 16)
            var i = 0
            while (i < data.size) {
                buf.clear()
                val n = minOf(data.size - i, buf.capacity() / 4)
                for (j in 0 until n) buf.putInt(data[i + j])
                z.write(buf.array(), 0, n * 4)
                i += n
            }
            z.finish()
        }
    }

    private fun readCel(f: File, w: Int, h: Int): IntArray? {
        if (!f.isFile) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(f))).use { raw ->
                if (raw.readInt() != CEL_MAGIC || raw.readInt() != w || raw.readInt() != h) return null
                val z = InflaterInputStream(raw)
                val out = IntArray(w * h)
                val buf = ByteArray(1 shl 16)
                var i = 0
                while (i < out.size) {
                    val want = minOf((out.size - i) * 4, buf.size)
                    var got = 0
                    while (got < want) {
                        val n = z.read(buf, got, want - got)
                        if (n < 0) return null
                        got += n
                    }
                    val bb = ByteBuffer.wrap(buf, 0, want)
                    while (bb.hasRemaining()) out[i++] = bb.getInt()
                }
                out
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---- Chargement ----------------------------------------------------------------------

    fun load(id: String): LoadedProject? {
        val mf = manifestFile(id)
        if (!mf.isFile) return null
        return try {
            val (doc, meta) = ManifestCodec.decode(id, JSONObject(mf.readText()))
            for (l in doc.layers) for (f in doc.frames) {
                readCel(celFile(id, Document.key(l.id, f.id)), doc.width, doc.height)?.let { doc.setCel(l.id, f.id, it) }
            }
            // Ce qu'on vient de lire est déjà sur le disque : rien à réécrire.
            doc.markSaved(doc.peekDirty())
            meta.hasReference = referenceFile(id).isFile
            LoadedProject(doc, meta)
        } catch (e: Exception) {
            null
        }
    }

    // ---- Gestion -------------------------------------------------------------------------

    fun delete(id: String) {
        dir(id).deleteRecursively()
    }

    fun rename(id: String, name: String) {
        val mf = manifestFile(id)
        if (!mf.isFile) return
        val j = JSONObject(mf.readText())
        j.put("name", name)
        atomicWrite(mf) { it.write(j.toString().toByteArray(Charsets.UTF_8)) }
    }

    fun setLink(id: String, link: SourceLink?) {
        val mf = manifestFile(id)
        if (!mf.isFile) return
        val j = JSONObject(mf.readText())
        if (link == null) j.remove("link") else j.put("link", ManifestCodec.encodeLink(link))
        atomicWrite(mf) { it.write(j.toString().toByteArray(Charsets.UTF_8)) }
    }

    fun duplicate(id: String, newName: String): String? {
        if (!exists(id)) return null
        val newId = newId()
        dir(id).copyRecursively(dir(newId), overwrite = false)
        val mf = manifestFile(newId)
        val j = JSONObject(mf.readText())
        j.put("name", newName)
        j.put("created", System.currentTimeMillis())
        j.put("modified", System.currentTimeMillis())
        j.remove("link")     // la copie est un nouveau dessin : elle n'écrase pas le fichier de l'original
        atomicWrite(mf) { it.write(j.toString().toByteArray(Charsets.UTF_8)) }
        return newId
    }

    fun saveReference(id: String, pixels: IntArray, w: Int, h: Int) {
        atomicWrite(referenceFile(id)) { PngWriter.write(it, pixels, w, h) }
    }

    fun deleteReference(id: String) {
        referenceFile(id).delete()
    }

    // ---- Fichier de projet (partage) ---------------------------------------------------------

    fun exportZip(id: String, out: OutputStream) {
        ZipOutputStream(BufferedOutputStream(out)).use { z ->
            val base = dir(id)
            for (f in base.walkTopDown()) {
                if (!f.isFile || f.name.endsWith(".tmp")) continue
                val rel = f.relativeTo(base).path.replace(File.separatorChar, '/')
                z.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }

    /** Ouvre un fichier de projet partagé ; retourne l'identifiant du nouveau projet, ou null s'il est illisible. */
    fun importZip(input: InputStream, fallbackName: String): String? {
        val id = newId()
        val target = dir(id)
        try {
            ZipInputStream(BufferedInputStream(input)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val name = e.name
                    val ok = name == "manifest.json" || name == "thumb.png" || name == "reference.png" ||
                        (name.startsWith("cels/") && name.endsWith(".cel") && !name.substring(5).contains('/'))
                    if (e.isDirectory || !ok) continue
                    val f = File(target, name)
                    f.parentFile?.mkdirs()
                    FileOutputStream(f).use { z.copyTo(it) }
                }
            }
            val mf = manifestFile(id)
            if (!mf.isFile) { target.deleteRecursively(); return null }
            val j = JSONObject(mf.readText())
            if (j.optString("name").isBlank()) j.put("name", fallbackName)
            j.remove("link")
            j.put("modified", System.currentTimeMillis())
            atomicWrite(mf) { it.write(j.toString().toByteArray(Charsets.UTF_8)) }
            if (load(id) == null) { target.deleteRecursively(); return null }
            return id
        } catch (e: Exception) {
            target.deleteRecursively()
            return null
        }
    }

    private companion object {
        const val CEL_MAGIC = 0x43454C31 // "CEL1"
    }
}

/** Le manifeste JSON d'un projet. */
object ManifestCodec {

    const val VERSION = 1

    fun encodeLink(l: SourceLink) = JSONObject()
        .put("uri", l.uri).put("name", l.name).put("format", l.format.name)
        .put("scale", l.scale).put("writable", l.writable)

    fun encode(doc: Document, meta: ProjectMeta): JSONObject {
        val layers = JSONArray()
        for (l in doc.layers) {
            layers.put(
                JSONObject().put("id", l.id).put("name", l.name).put("visible", l.visible)
                    .put("opacity", l.opacity).put("locked", l.locked).put("blend", l.blend.name),
            )
        }
        val frames = JSONArray()
        for (f in doc.frames) frames.put(JSONObject().put("id", f.id).put("ms", f.durationMs))
        val j = JSONObject()
            .put("v", VERSION)
            .put("name", meta.name)
            .put("created", meta.created)
            .put("modified", meta.modified)
            .put("w", doc.width).put("h", doc.height)
            .put("layers", layers).put("frames", frames)
            .put("nextLayer", doc.nextLayerId).put("nextFrame", doc.nextFrameId)
            .put("activeLayer", meta.activeLayerId).put("activeFrame", meta.activeFrameId)
            .put("primary", meta.primary).put("secondary", meta.secondary)
        meta.link?.let { j.put("link", encodeLink(it)) }
        meta.reference?.let {
            j.put(
                "reference",
                JSONObject().put("scale", it.scale.toDouble()).put("x", it.x.toDouble()).put("y", it.y.toDouble())
                    .put("opacity", it.opacity.toDouble()).put("visible", it.visible),
            )
        }
        return j
    }

    fun decode(id: String, j: JSONObject): Pair<Document, ProjectMeta> {
        val doc = Document(j.getInt("w"), j.getInt("h"))
        val layers = j.getJSONArray("layers")
        for (i in 0 until layers.length()) {
            val l = layers.getJSONObject(i)
            doc.layers.add(
                Layer(
                    l.getInt("id"), l.optString("name", "").ifEmpty { (i + 1).toString() },
                    l.optBoolean("visible", true), l.optInt("opacity", 255).coerceIn(0, 255),
                    l.optBoolean("locked", false),
                    runCatching { BlendMode.valueOf(l.optString("blend", "NORMAL")) }.getOrDefault(BlendMode.NORMAL),
                ),
            )
        }
        val frames = j.getJSONArray("frames")
        for (i in 0 until frames.length()) {
            val f = frames.getJSONObject(i)
            doc.frames.add(Frame(f.getInt("id"), f.optInt("ms", Frame.DEFAULT_DURATION_MS)))
        }
        if (doc.layers.isEmpty() || doc.frames.isEmpty()) throw IllegalStateException("projet sans calque ou sans image")
        doc.nextLayerId = maxOf(j.optInt("nextLayer", 1), (doc.layers.maxOf { it.id }) + 1)
        doc.nextFrameId = maxOf(j.optInt("nextFrame", 1), (doc.frames.maxOf { it.id }) + 1)
        val meta = ProjectMeta(
            id = id,
            name = j.optString("name", ""),
            created = j.optLong("created"),
            modified = j.optLong("modified"),
            primary = j.optInt("primary", 0xFF000000.toInt()),
            secondary = j.optInt("secondary", 0xFFFFFFFF.toInt()),
            activeLayerId = j.optInt("activeLayer", doc.layers.last().id),
            activeFrameId = j.optInt("activeFrame", doc.frames.first().id),
        )
        j.optJSONObject("link")?.let { l ->
            val format = runCatching { ImageFormat.valueOf(l.optString("format", "PNG")) }.getOrDefault(ImageFormat.PNG)
            meta.link = SourceLink(
                l.getString("uri"), l.optString("name", ""), format,
                l.optInt("scale", 1).coerceIn(1, 64), l.optBoolean("writable", true),
            )
        }
        j.optJSONObject("reference")?.let { r ->
            meta.reference = ReferenceState(
                r.optDouble("scale", 1.0).toFloat(), r.optDouble("x", 0.0).toFloat(), r.optDouble("y", 0.0).toFloat(),
                r.optDouble("opacity", 0.5).toFloat(), r.optBoolean("visible", true),
            )
        }
        return doc to meta
    }
}
