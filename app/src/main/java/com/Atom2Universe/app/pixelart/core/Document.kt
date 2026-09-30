package com.Atom2Universe.app.pixelart.core

enum class BlendMode { NORMAL, MULTIPLY, SCREEN, ADD }

class Layer(
    val id: Int,
    var name: String,
    var visible: Boolean = true,
    /** 0..255 */
    var opacity: Int = 255,
    var locked: Boolean = false,
    var blend: BlendMode = BlendMode.NORMAL,
) {
    fun copyProps(newId: Int, newName: String = name) =
        Layer(newId, newName, visible, opacity, locked, blend)
}

class Frame(
    val id: Int,
    var durationMs: Int = DEFAULT_DURATION_MS,
) {
    companion object {
        const val DEFAULT_DURATION_MS = 100
        const val MIN_DURATION_MS = 20
        const val MAX_DURATION_MS = 5000
    }
}

/**
 * Un dessin : des calques (le 0 est tout en bas) × des images (l'ordre de l'animation).
 *
 * Chaque case calque×image est une « cel » : un tableau ARGB de `width * height` pixels, ou
 * `null` quand elle est entièrement transparente (un calque vide ne coûte donc rien).
 *
 * Les cels sont rangées par **identifiants** de calque et d'image, jamais par position :
 * déplacer un calque ou une image ne touche aucune donnée de pixels.
 *
 * Le document sait quelles cels ont changé depuis la dernière sauvegarde disque
 * ([peekDirty] puis [markSaved]) — c'est ce qui rend l'enregistrement automatique incrémental.
 */
class Document(width: Int, height: Int) {

    var width: Int = width
        private set
    var height: Int = height
        private set

    val layers = ArrayList<Layer>()
    val frames = ArrayList<Frame>()

    private val cels = HashMap<Long, IntArray>()
    // clé de cel -> révision du dernier changement (pour ne pas effacer un changement arrivé pendant l'écriture disque)
    private val dirtyCels = HashMap<Long, Long>()
    private val removedCels = HashMap<Long, Long>()
    private var metaRevision = 1L
    private var metaSavedRevision = 0L
    val metaDirty get() = metaRevision != metaSavedRevision

    var nextLayerId = 1
    var nextFrameId = 1

    /** Incrémenté à chaque modification de pixels ; sert d'invalidation aux caches d'aperçu. */
    var revision = 0L
        private set

    init {
        require(width in 1..MAX_SIDE && height in 1..MAX_SIDE) { "Taille invalide : ${width}x$height" }
    }

    val pixelCount get() = width * height

    // ---- Création ------------------------------------------------------------------------

    fun newLayerId() = nextLayerId++
    fun newFrameId() = nextFrameId++

    fun layerById(id: Int): Layer? = layers.firstOrNull { it.id == id }
    fun layerIndexOf(id: Int): Int = layers.indexOfFirst { it.id == id }
    fun frameById(id: Int): Frame? = frames.firstOrNull { it.id == id }
    fun frameIndexOf(id: Int): Int = frames.indexOfFirst { it.id == id }

    // ---- Cels ----------------------------------------------------------------------------

    fun cel(layerId: Int, frameId: Int): IntArray? = cels[key(layerId, frameId)]

    fun celOrCreate(layerId: Int, frameId: Int): IntArray =
        cels.getOrPut(key(layerId, frameId)) { IntArray(pixelCount) }.also { touch(layerId, frameId) }

    /** Remplace (ou vide, avec `null`) une cel. Le tableau doit avoir la taille du dessin. */
    fun setCel(layerId: Int, frameId: Int, data: IntArray?) {
        val k = key(layerId, frameId)
        if (data == null) {
            revision++
            if (cels.remove(k) != null) removedCels[k] = revision
            dirtyCels.remove(k)
        } else {
            require(data.size == pixelCount) { "Cel de ${data.size} pixels pour un dessin de $pixelCount" }
            cels[k] = data
            removedCels.remove(k)
            revision++
            dirtyCels[k] = revision
        }
    }

    /** Signale qu'on a écrit dans une cel obtenue par [cel] ou [celOrCreate]. */
    fun touch(layerId: Int, frameId: Int) {
        val k = key(layerId, frameId)
        removedCels.remove(k)
        revision++
        dirtyCels[k] = revision
    }

    fun markMetaDirty() {
        metaRevision++
    }

    /** Identifiants (calque, image) de toutes les cels non vides. */
    fun allCelKeys(): List<Pair<Int, Int>> = cels.keys.map { unpackLayer(it) to unpackFrame(it) }

    /** Retire et retourne les cels du calque (pour pouvoir annuler sa suppression). */
    fun detachLayerCels(layerId: Int): Map<Int, IntArray> {
        val out = HashMap<Int, IntArray>()
        for (f in frames) {
            val k = key(layerId, f.id)
            cels[k]?.let { out[f.id] = it }
            if (cels.remove(k) != null) removedCels[k] = revision + 1
            dirtyCels.remove(k)
        }
        revision++
        return out
    }

    fun detachFrameCels(frameId: Int): Map<Int, IntArray> {
        val out = HashMap<Int, IntArray>()
        for (l in layers) {
            val k = key(l.id, frameId)
            cels[k]?.let { out[l.id] = it }
            if (cels.remove(k) != null) removedCels[k] = revision + 1
            dirtyCels.remove(k)
        }
        revision++
        return out
    }

    // ---- Suivi disque --------------------------------------------------------------------

    /** Photographie de ce qui reste à écrire ; chaque entrée garde la révision qu'elle avait. */
    class DirtySet(
        val written: Map<Long, Long>,
        val removed: Map<Long, Long>,
        val meta: Boolean,
        val metaRevision: Long,
    ) {
        val isEmpty get() = written.isEmpty() && removed.isEmpty() && !meta
    }

    fun peekDirty(): DirtySet = DirtySet(HashMap(dirtyCels), HashMap(removedCels), metaDirty, metaRevision)

    /**
     * Une fois [saved] écrit sur le disque, ne retire de la liste que ce qui n'a pas rebougé depuis :
     * une cel retouchée pendant l'écriture reste à écrire.
     */
    fun markSaved(saved: DirtySet) {
        for ((k, r) in saved.written) if (dirtyCels[k] == r) dirtyCels.remove(k)
        for ((k, r) in saved.removed) if (removedCels[k] == r) removedCels.remove(k)
        if (saved.meta && metaRevision == saved.metaRevision) metaSavedRevision = metaRevision
    }

    fun markEverythingDirty() {
        markMetaDirty()
        revision++
        for (k in cels.keys) dirtyCels[k] = revision
    }

    fun celAt(key: Long): IntArray? = cels[key]

    // ---- Redimensionnement ---------------------------------------------------------------

    /** Change la taille du dessin ; à appeler avec les cels déjà remplacées (voir [replaceAll]). */
    fun replaceAll(newWidth: Int, newHeight: Int, newCels: Map<Long, IntArray>) {
        require(newWidth in 1..MAX_SIDE && newHeight in 1..MAX_SIDE)
        revision++
        for (k in cels.keys) removedCels[k] = revision
        width = newWidth
        height = newHeight
        cels.clear()
        cels.putAll(newCels)
        for (k in newCels.keys) removedCels.remove(k)
        dirtyCels.clear()
        for (k in newCels.keys) dirtyCels[k] = revision
        markMetaDirty()
    }

    fun snapshotCels(): Map<Long, IntArray> = HashMap(cels)

    /** Une copie indépendante (pixels compris) : pour exporter sur un autre fil pendant qu'on continue à dessiner. */
    fun copy(): Document {
        val d = Document(width, height)
        for (l in layers) d.layers.add(l.copyProps(l.id))
        for (f in frames) d.frames.add(Frame(f.id, f.durationMs))
        d.nextLayerId = nextLayerId
        d.nextFrameId = nextFrameId
        for ((k, v) in cels) d.cels[k] = v.copyOf()
        return d
    }

    /** Mémoire occupée par les pixels, en octets. */
    fun pixelBytes(): Long = cels.size.toLong() * pixelCount * 4L

    companion object {
        /** Une cel de 2048² pèse 16 Mo : au-delà, une seule image ferait exploser la mémoire. */
        const val MAX_SIDE = 2048
        const val MAX_LAYERS = 32
        const val MAX_FRAMES = 200

        fun key(layerId: Int, frameId: Int): Long = (layerId.toLong() shl 32) or (frameId.toLong() and 0xFFFFFFFFL)
        fun unpackLayer(key: Long): Int = (key ushr 32).toInt()
        fun unpackFrame(key: Long): Int = key.toInt()

        /** Un document neuf : un calque, une image, tout transparent. */
        fun blank(width: Int, height: Int, layerName: String = "1"): Document {
            val d = Document(width, height)
            d.layers.add(Layer(d.newLayerId(), layerName))
            d.frames.add(Frame(d.newFrameId()))
            return d
        }
    }
}
