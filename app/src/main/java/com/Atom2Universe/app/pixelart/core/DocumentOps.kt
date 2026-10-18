package com.Atom2Universe.app.pixelart.core

/**
 * Opérations qui changent la structure du dessin (calques, images, taille).
 * Chacune s'applique immédiatement et retourne l'[Edit] qui sait la défaire.
 */
object DocumentOps {

    // ---- Calques -------------------------------------------------------------------------

    /** Ajoute un calque vide juste au-dessus de la position [above] (ou tout en haut). */
    fun addLayer(doc: Document, above: Int, name: String): Pair<Layer, Edit> {
        val layer = Layer(doc.newLayerId(), name)
        val index = (above + 1).coerceIn(0, doc.layers.size)
        doc.layers.add(index, layer)
        doc.markMetaDirty()
        val edit = LambdaEdit(
            0,
            { d -> d.layers.removeAll { it.id == layer.id }; d.detachLayerCels(layer.id); d.markMetaDirty() },
            { d -> d.layers.add(index.coerceAtMost(d.layers.size), layer); d.markMetaDirty() },
        )
        return layer to edit
    }

    fun duplicateLayer(doc: Document, index: Int, name: String): Pair<Layer, Edit> {
        val src = doc.layers[index]
        val copy = src.copyProps(doc.newLayerId(), name)
        val pos = index + 1
        doc.layers.add(pos, copy)
        val cels = HashMap<Int, IntArray>()
        for (f in doc.frames) doc.cel(src.id, f.id)?.let { cels[f.id] = it.copyOf() }
        for ((fid, data) in cels) doc.setCel(copy.id, fid, data)
        doc.markMetaDirty()
        val edit = LambdaEdit(
            cels.values.sumOf { it.size * 4L },
            { d -> d.layers.removeAll { it.id == copy.id }; d.detachLayerCels(copy.id); d.markMetaDirty() },
            { d ->
                d.layers.add(pos.coerceAtMost(d.layers.size), copy)
                for ((fid, data) in cels) d.setCel(copy.id, fid, data)
                d.markMetaDirty()
            },
        )
        return copy to edit
    }

    /** Supprime un calque ; refuse (retourne null) de supprimer le dernier. */
    fun deleteLayer(doc: Document, index: Int): Edit? {
        if (doc.layers.size <= 1) return null
        val layer = doc.layers.removeAt(index)
        val cels = doc.detachLayerCels(layer.id)
        doc.markMetaDirty()
        return LambdaEdit(
            cels.values.sumOf { it.size * 4L },
            { d ->
                d.layers.add(index.coerceAtMost(d.layers.size), layer)
                for ((fid, data) in cels) d.setCel(layer.id, fid, data)
                d.markMetaDirty()
            },
            { d -> d.layers.removeAll { it.id == layer.id }; d.detachLayerCels(layer.id); d.markMetaDirty() },
        )
    }

    fun moveLayer(doc: Document, from: Int, to: Int): Edit? {
        if (from == to || from !in doc.layers.indices || to !in doc.layers.indices) return null
        val layer = doc.layers.removeAt(from)
        doc.layers.add(to, layer)
        doc.markMetaDirty()
        return LambdaEdit(
            0,
            { d -> val l = d.layers.removeAt(to); d.layers.add(from, l); d.markMetaDirty() },
            { d -> val l = d.layers.removeAt(from); d.layers.add(to, l); d.markMetaDirty() },
        )
    }

    /** Fusionne le calque [index] dans celui du dessous, image par image. */
    fun mergeDown(doc: Document, index: Int): Edit? {
        if (index <= 0 || index >= doc.layers.size) return null
        val upper = doc.layers[index]
        val lower = doc.layers[index - 1]
        val upperCels = HashMap<Int, IntArray>()
        val lowerBefore = HashMap<Int, IntArray?>()
        val lowerAfter = HashMap<Int, IntArray>()
        for (f in doc.frames) {
            val u = doc.cel(upper.id, f.id)
            val before = doc.cel(lower.id, f.id)
            lowerBefore[f.id] = before?.copyOf()
            if (u != null) upperCels[f.id] = u
            if (u == null || !upper.visible) {
                before?.let { lowerAfter[f.id] = it }
                continue
            }
            val merged = before?.copyOf() ?: IntArray(doc.pixelCount)
            for (i in merged.indices) {
                val s = u[i]
                if (s ushr 24 != 0) merged[i] = PixelColor.blend(merged[i], s, upper.blend, upper.opacity)
            }
            lowerAfter[f.id] = merged
        }
        for ((fid, data) in lowerAfter) doc.setCel(lower.id, fid, data)
        doc.layers.removeAt(index)
        doc.detachLayerCels(upper.id)
        doc.markMetaDirty()
        val bytes = (upperCels.size + lowerBefore.size + lowerAfter.size) * doc.pixelCount * 4L
        return LambdaEdit(
            bytes,
            { d ->
                d.layers.add(index.coerceAtMost(d.layers.size), upper)
                for ((fid, data) in upperCels) d.setCel(upper.id, fid, data)
                for ((fid, data) in lowerBefore) d.setCel(lower.id, fid, data)
                d.markMetaDirty()
            },
            { d ->
                for ((fid, data) in lowerAfter) d.setCel(lower.id, fid, data)
                d.layers.removeAll { it.id == upper.id }
                d.detachLayerCels(upper.id)
                d.markMetaDirty()
            },
        )
    }

    class LayerState(val name: String, val visible: Boolean, val opacity: Int, val locked: Boolean, val blend: BlendMode) {
        constructor(l: Layer) : this(l.name, l.visible, l.opacity, l.locked, l.blend)

        fun applyTo(l: Layer) {
            l.name = name; l.visible = visible; l.opacity = opacity; l.locked = locked; l.blend = blend
        }
    }

    /** Modifie les propriétés d'un calque ; `null` si rien n'a changé. */
    fun editLayer(doc: Document, layerId: Int, change: (Layer) -> Unit): Edit? {
        val layer = doc.layerById(layerId) ?: return null
        val before = LayerState(layer)
        change(layer)
        val after = LayerState(layer)
        if (before.name == after.name && before.visible == after.visible && before.opacity == after.opacity &&
            before.locked == after.locked && before.blend == after.blend
        ) return null
        doc.markMetaDirty()
        doc.touchAllCels(layerId)
        return LambdaEdit(
            0,
            { d -> d.layerById(layerId)?.let { before.applyTo(it) }; d.markMetaDirty(); d.touchAllCels(layerId) },
            { d -> d.layerById(layerId)?.let { after.applyTo(it) }; d.markMetaDirty(); d.touchAllCels(layerId) },
        )
    }

    // ---- Images (animation) --------------------------------------------------------------

    /** Ajoute une image après [after] ; copie les pixels de l'image [copyOf] si fournie. */
    fun addFrame(doc: Document, after: Int, copyOf: Int?): Pair<Frame, Edit> {
        val src = copyOf?.let { doc.frames.getOrNull(it) }
        val frame = Frame(doc.newFrameId(), src?.durationMs ?: Frame.DEFAULT_DURATION_MS)
        val index = (after + 1).coerceIn(0, doc.frames.size)
        doc.frames.add(index, frame)
        val cels = HashMap<Int, IntArray>()
        if (src != null) for (l in doc.layers) doc.cel(l.id, src.id)?.let { cels[l.id] = it.copyOf() }
        for ((lid, data) in cels) doc.setCel(lid, frame.id, data)
        doc.markMetaDirty()
        val edit = LambdaEdit(
            cels.values.sumOf { it.size * 4L },
            { d -> d.frames.removeAll { it.id == frame.id }; d.detachFrameCels(frame.id); d.markMetaDirty() },
            { d ->
                d.frames.add(index.coerceAtMost(d.frames.size), frame)
                for ((lid, data) in cels) d.setCel(lid, frame.id, data)
                d.markMetaDirty()
            },
        )
        return frame to edit
    }

    fun deleteFrame(doc: Document, index: Int): Edit? {
        if (doc.frames.size <= 1) return null
        val frame = doc.frames.removeAt(index)
        val cels = doc.detachFrameCels(frame.id)
        doc.markMetaDirty()
        return LambdaEdit(
            cels.values.sumOf { it.size * 4L },
            { d ->
                d.frames.add(index.coerceAtMost(d.frames.size), frame)
                for ((lid, data) in cels) d.setCel(lid, frame.id, data)
                d.markMetaDirty()
            },
            { d -> d.frames.removeAll { it.id == frame.id }; d.detachFrameCels(frame.id); d.markMetaDirty() },
        )
    }

    fun moveFrame(doc: Document, from: Int, to: Int): Edit? {
        if (from == to || from !in doc.frames.indices || to !in doc.frames.indices) return null
        val f = doc.frames.removeAt(from)
        doc.frames.add(to, f)
        doc.markMetaDirty()
        return LambdaEdit(
            0,
            { d -> val x = d.frames.removeAt(to); d.frames.add(from, x); d.markMetaDirty() },
            { d -> val x = d.frames.removeAt(from); d.frames.add(to, x); d.markMetaDirty() },
        )
    }

    fun setFrameDuration(doc: Document, frameId: Int, ms: Int): Edit? {
        val f = doc.frameById(frameId) ?: return null
        val before = f.durationMs
        val after = ms.coerceIn(Frame.MIN_DURATION_MS, Frame.MAX_DURATION_MS)
        if (before == after) return null
        f.durationMs = after
        doc.markMetaDirty()
        return LambdaEdit(
            0,
            { d -> d.frameById(frameId)?.durationMs = before; d.markMetaDirty() },
            { d -> d.frameById(frameId)?.durationMs = after; d.markMetaDirty() },
        )
    }

    // ---- Taille du dessin ----------------------------------------------------------------

    private fun replaceEdit(doc: Document, newW: Int, newH: Int, newCels: Map<Long, IntArray>): Edit {
        val oldW = doc.width
        val oldH = doc.height
        val oldCels = doc.snapshotCels()
        doc.replaceAll(newW, newH, newCels)
        val bytes = (oldCels.size + newCels.size).toLong() * maxOf(oldW * oldH, newW * newH) * 4L
        return LambdaEdit(
            bytes,
            { d -> d.replaceAll(oldW, oldH, oldCels) },
            { d -> d.replaceAll(newW, newH, newCels) },
        )
    }

    /**
     * Change la taille de la toile en rognant ou en agrandissant (bord transparent), sans mettre
     * le dessin à l'échelle. [anchorX] / [anchorY] : 0 = gauche/haut, 1 = centre, 2 = droite/bas.
     */
    fun resizeCanvas(doc: Document, newW: Int, newH: Int, anchorX: Int, anchorY: Int): Edit? {
        if (newW == doc.width && newH == doc.height) return null
        if (newW !in 1..Document.MAX_SIDE || newH !in 1..Document.MAX_SIDE) return null
        val ox = when (anchorX) { 0 -> 0; 1 -> (newW - doc.width) / 2; else -> newW - doc.width }
        val oy = when (anchorY) { 0 -> 0; 1 -> (newH - doc.height) / 2; else -> newH - doc.height }
        val out = HashMap<Long, IntArray>()
        for (k in doc.allCelKeys()) {
            val key = Document.key(k.first, k.second)
            val src = doc.celAt(key) ?: continue
            val dst = IntArray(newW * newH)
            for (y in 0 until doc.height) {
                val ty = y + oy
                if (ty !in 0 until newH) continue
                for (x in 0 until doc.width) {
                    val tx = x + ox
                    if (tx in 0 until newW) dst[ty * newW + tx] = src[y * doc.width + x]
                }
            }
            out[key] = dst
        }
        return replaceEdit(doc, newW, newH, out)
    }

    /** Met le dessin à l'échelle (plus proche voisin) : les pixels sont répétés ou éliminés. */
    fun scaleCanvas(doc: Document, newW: Int, newH: Int): Edit? {
        if (newW == doc.width && newH == doc.height) return null
        if (newW !in 1..Document.MAX_SIDE || newH !in 1..Document.MAX_SIDE) return null
        val out = HashMap<Long, IntArray>()
        for (k in doc.allCelKeys()) {
            val key = Document.key(k.first, k.second)
            val src = doc.celAt(key) ?: continue
            val dst = IntArray(newW * newH)
            for (y in 0 until newH) {
                val sy = (y.toLong() * doc.height / newH).toInt()
                for (x in 0 until newW) {
                    val sx = (x.toLong() * doc.width / newW).toInt()
                    dst[y * newW + x] = src[sy * doc.width + sx]
                }
            }
            out[key] = dst
        }
        return replaceEdit(doc, newW, newH, out)
    }

    fun flipCanvas(doc: Document, horizontal: Boolean): Edit {
        val w = doc.width
        val h = doc.height
        val out = HashMap<Long, IntArray>()
        for (k in doc.allCelKeys()) {
            val key = Document.key(k.first, k.second)
            val src = doc.celAt(key) ?: continue
            val dst = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                val sx = if (horizontal) w - 1 - x else x
                val sy = if (horizontal) y else h - 1 - y
                dst[y * w + x] = src[sy * w + sx]
            }
            out[key] = dst
        }
        return replaceEdit(doc, w, h, out)
    }

    fun rotateCanvas(doc: Document, clockwise: Boolean): Edit {
        val w = doc.width
        val h = doc.height
        val out = HashMap<Long, IntArray>()
        for (k in doc.allCelKeys()) {
            val key = Document.key(k.first, k.second)
            val src = doc.celAt(key) ?: continue
            out[key] = Transform.rotate90(src, w, h, clockwise)
        }
        return replaceEdit(doc, h, w, out)
    }
}

/** Retourne / tourne un tableau de pixels. */
object Transform {
    fun rotate90(src: IntArray, w: Int, h: Int, clockwise: Boolean): IntArray {
        // Le résultat mesure h × w : la ligne y du résultat vient d'une colonne de la source.
        val dst = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val nx: Int
            val ny: Int
            if (clockwise) { nx = h - 1 - y; ny = x } else { nx = y; ny = w - 1 - x }
            dst[ny * h + nx] = src[y * w + x]
        }
        return dst
    }

    fun flipH(src: IntArray, w: Int, h: Int): IntArray {
        val dst = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) dst[y * w + x] = src[y * w + (w - 1 - x)]
        return dst
    }

    fun flipV(src: IntArray, w: Int, h: Int): IntArray {
        val dst = IntArray(w * h)
        for (y in 0 until h) System.arraycopy(src, (h - 1 - y) * w, dst, y * w, w)
        return dst
    }
}

/** Marque toutes les cels d'un calque comme modifiées (pour qu'un changement de propriété invalide les aperçus). */
fun Document.touchAllCels(layerId: Int) {
    for (f in frames) if (cel(layerId, f.id) != null) touch(layerId, f.id)
    markMetaDirty()
}
