package com.Atom2Universe.app.pixelart.core

/** Côté des tuiles de mémorisation : un trait n'enregistre que les tuiles qu'il touche. */
const val TILE = 32

class TileDelta(val tx: Int, val ty: Int, val before: IntArray, val after: IntArray)

/** Ce qu'un geste a changé dans une cel, tuile par tuile (avant / après). */
class CelDelta(val layerId: Int, val frameId: Int, val tiles: List<TileDelta>) {
    val bytes: Long get() = tiles.sumOf { (it.before.size + it.after.size) * 4L }

    fun apply(doc: Document, useAfter: Boolean) {
        val cel = doc.celOrCreate(layerId, frameId)
        val w = doc.width
        val h = doc.height
        for (t in tiles) {
            val src = if (useAfter) t.after else t.before
            val x0 = t.tx * TILE
            val y0 = t.ty * TILE
            val tw = minOf(TILE, w - x0)
            val th = minOf(TILE, h - y0)
            if (tw <= 0 || th <= 0) continue
            for (r in 0 until th) System.arraycopy(src, r * TILE, cel, (y0 + r) * w + x0, tw)
        }
        doc.touch(layerId, frameId)
    }
}

/**
 * Écriture dans une cel avec mémoire de ce qu'elle contenait avant : la première fois qu'un pixel
 * d'une tuile est modifié, la tuile entière est copiée. À la fin du geste, [build] compare et ne
 * garde que les tuiles réellement changées.
 *
 * [clip] limite l'écriture à une sélection (les pixels hors sélection sont intouchables).
 */
class CelEditor(val cel: IntArray, val w: Int, val h: Int, private val clip: Selection? = null) {

    private val tilesX = (w + TILE - 1) / TILE
    private val originals = HashMap<Int, IntArray>()

    /** Zone touchée depuis le dernier [takeDirty]. */
    private val dirty = PixelRect.empty()

    val touched get() = originals.isNotEmpty()

    fun get(x: Int, y: Int): Int = if (x in 0 until w && y in 0 until h) cel[y * w + x] else 0

    fun canWrite(x: Int, y: Int): Boolean =
        x in 0 until w && y in 0 until h && (clip == null || clip.mask[y * w + x].toInt() != 0)

    /** Écrit un pixel ; retourne vrai si la cel a changé. */
    fun set(x: Int, y: Int, color: Int): Boolean {
        if (!canWrite(x, y)) return false
        val i = y * w + x
        if (cel[i] == color) return false
        remember(x / TILE, y / TILE)
        cel[i] = color
        dirty.union(x, y, x + 1, y + 1)
        return true
    }

    /** Valeur du pixel au début du geste (avant toute écriture de ce [CelEditor]). */
    fun original(x: Int, y: Int): Int {
        if (x !in 0 until w || y !in 0 until h) return 0
        val t = originals[(y / TILE) * tilesX + x / TILE] ?: return cel[y * w + x]
        return t[(y % TILE) * TILE + (x % TILE)]
    }

    /** Remet chaque pixel touché dans son état d'origine (le geste peut ensuite être rejoué). */
    fun revertAll() {
        for ((k, orig) in originals) {
            val tx = k % tilesX
            val ty = k / tilesX
            val x0 = tx * TILE
            val y0 = ty * TILE
            val tw = minOf(TILE, w - x0)
            val th = minOf(TILE, h - y0)
            for (r in 0 until th) System.arraycopy(orig, r * TILE, cel, (y0 + r) * w + x0, tw)
            dirty.union(x0, y0, x0 + tw, y0 + th)
        }
    }

    /** Zone à redessiner depuis le dernier appel (puis remise à vide). */
    fun takeDirty(into: PixelRect) {
        into.union(dirty)
        dirty.set(0, 0, 0, 0)
    }

    private fun remember(tx: Int, ty: Int) {
        val k = ty * tilesX + tx
        if (originals.containsKey(k)) return
        val buf = IntArray(TILE * TILE)
        val x0 = tx * TILE
        val y0 = ty * TILE
        val tw = minOf(TILE, w - x0)
        val th = minOf(TILE, h - y0)
        for (r in 0 until th) System.arraycopy(cel, (y0 + r) * w + x0, buf, r * TILE, tw)
        originals[k] = buf
    }

    /** Le delta du geste, ou null si, au final, rien n'a changé. */
    fun build(layerId: Int, frameId: Int): CelDelta? {
        val tiles = ArrayList<TileDelta>()
        for ((k, before) in originals) {
            val tx = k % tilesX
            val ty = k / tilesX
            val x0 = tx * TILE
            val y0 = ty * TILE
            val tw = minOf(TILE, w - x0)
            val th = minOf(TILE, h - y0)
            val after = IntArray(TILE * TILE)
            var changed = false
            for (r in 0 until th) {
                System.arraycopy(cel, (y0 + r) * w + x0, after, r * TILE, tw)
                if (!changed) {
                    for (c in 0 until tw) if (after[r * TILE + c] != before[r * TILE + c]) { changed = true; break }
                }
            }
            if (changed) tiles.add(TileDelta(tx, ty, before, after))
        }
        return if (tiles.isEmpty()) null else CelDelta(layerId, frameId, tiles)
    }
}

/** Une étape annulable. Les implémentations sont appliquées **avant** d'être poussées dans l'historique. */
interface Edit {
    val bytes: Long
    fun undo(doc: Document)
    fun redo(doc: Document)
}

class CelEdit(private val deltas: List<CelDelta>) : Edit {
    override val bytes: Long = deltas.sumOf { it.bytes }
    override fun undo(doc: Document) { for (d in deltas) d.apply(doc, useAfter = false) }
    override fun redo(doc: Document) { for (d in deltas) d.apply(doc, useAfter = true) }
}

class LambdaEdit(
    override val bytes: Long,
    private val undoBlock: (Document) -> Unit,
    private val redoBlock: (Document) -> Unit,
) : Edit {
    override fun undo(doc: Document) = undoBlock(doc)
    override fun redo(doc: Document) = redoBlock(doc)
}

/**
 * Pile annuler / rétablir bornée en mémoire (les plus anciennes étapes s'éteignent d'abord).
 * Sait aussi dire si le dessin diffère de celui du dernier enregistrement.
 */
class History(private val budgetBytes: Long = 96L * 1024 * 1024, private val maxSteps: Int = 400) {

    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()
    private var total = 0L
    private var savedMark: Edit? = null
    private var markLost = false

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    /** Vrai si le dessin n'est plus celui du dernier [markSaved]. */
    val modifiedSinceSave get() = markLost || undoStack.lastOrNull() !== savedMark

    fun push(e: Edit) {
        for (r in redoStack) total -= r.bytes
        if (redoStack.isNotEmpty() && redoStack.any { it === savedMark }) markLost = true
        redoStack.clear()
        undoStack.addLast(e)
        total += e.bytes
        while (undoStack.size > 1 && (total > budgetBytes || undoStack.size > maxSteps)) {
            val old = undoStack.removeFirst()
            total -= old.bytes
            if (old === savedMark) { savedMark = null; markLost = true }
        }
    }

    fun undo(doc: Document): Edit? {
        val e = undoStack.removeLastOrNull() ?: return null
        e.undo(doc)
        redoStack.addLast(e)
        return e
    }

    fun redo(doc: Document): Edit? {
        val e = redoStack.removeLastOrNull() ?: return null
        e.redo(doc)
        undoStack.addLast(e)
        return e
    }

    /**
     * Ramène le dessin à l'état du dernier [markSaved], en annulant ou en rétablissant ce qu'il faut.
     * Faux (et rien ne bouge) si cet état a disparu de l'historique, qui est borné.
     */
    fun seekSaved(doc: Document): Boolean {
        if (markLost) return false
        val target = savedMark
        val inRedo = target != null && redoStack.any { it === target }
        while (undoStack.lastOrNull() !== target) {
            if (inRedo) redo(doc) ?: return false else undo(doc) ?: return false
        }
        return true
    }

    fun markSaved() {
        savedMark = undoStack.lastOrNull()
        markLost = false
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        total = 0
        savedMark = null
        markLost = false
    }
}
