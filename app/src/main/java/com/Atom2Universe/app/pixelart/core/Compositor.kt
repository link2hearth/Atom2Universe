package com.Atom2Universe.app.pixelart.core

/** Rectangle de pixels, bornes incluses à gauche/haut, exclues à droite/bas. */
class PixelRect(var left: Int, var top: Int, var right: Int, var bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val isEmpty get() = right <= left || bottom <= top

    fun set(l: Int, t: Int, r: Int, b: Int) {
        left = l; top = t; right = r; bottom = b
    }

    fun union(l: Int, t: Int, r: Int, b: Int) {
        if (isEmpty) set(l, t, r, b) else {
            if (l < left) left = l
            if (t < top) top = t
            if (r > right) right = r
            if (b > bottom) bottom = b
        }
    }

    fun union(o: PixelRect) {
        if (!o.isEmpty) union(o.left, o.top, o.right, o.bottom)
    }

    fun intersect(l: Int, t: Int, r: Int, b: Int) {
        left = maxOf(left, l); top = maxOf(top, t); right = minOf(right, r); bottom = minOf(bottom, b)
        if (isEmpty) set(0, 0, 0, 0)
    }

    fun copy() = PixelRect(left, top, right, bottom)

    override fun toString() = "[$left,$top - $right,$bottom]"

    companion object {
        fun empty() = PixelRect(0, 0, 0, 0)
    }
}

/**
 * Aplatit les calques visibles d'une image en un seul tableau ARGB.
 * Travaille sur une zone, pour qu'un trait de crayon ne recompose que quelques pixels.
 */
object Compositor {

    /**
     * Écrit dans [out] (taille `width * height` du document) la zone `[x0,x1) × [y0,y1)`.
     * [only] permet de ne composer qu'un calque (aperçu d'un calque seul).
     */
    fun composite(
        doc: Document,
        frameId: Int,
        out: IntArray,
        x0: Int = 0,
        y0: Int = 0,
        x1: Int = doc.width,
        y1: Int = doc.height,
        only: Layer? = null,
    ) {
        val w = doc.width
        val l = x0.coerceIn(0, w)
        val r = x1.coerceIn(0, w)
        val t = y0.coerceIn(0, doc.height)
        val b = y1.coerceIn(0, doc.height)
        if (l >= r || t >= b) return

        // Fond transparent.
        for (y in t until b) java.util.Arrays.fill(out, y * w + l, y * w + r, 0)

        for (layer in doc.layers) {
            if (only != null && layer !== only) continue
            if (!layer.visible || layer.opacity == 0) continue
            val cel = doc.cel(layer.id, frameId) ?: continue
            val fast = layer.blend == BlendMode.NORMAL && layer.opacity == 255
            for (y in t until b) {
                val row = y * w
                if (fast) {
                    for (i in row + l until row + r) {
                        val s = cel[i]
                        if (s == 0) continue
                        val a = s ushr 24
                        out[i] = if (a == 255) s else PixelColor.over(out[i], s)
                    }
                } else {
                    for (i in row + l until row + r) {
                        val s = cel[i]
                        if (s ushr 24 == 0) continue
                        out[i] = PixelColor.blend(out[i], s, layer.blend, layer.opacity)
                    }
                }
            }
        }
    }

    /**
     * Version réduite (plus proche voisin) de l'image, sans jamais composer la pleine taille :
     * pour les vignettes, où seuls quelques milliers de pixels comptent.
     */
    fun compositeScaled(doc: Document, frameId: Int, tw: Int, th: Int): IntArray {
        val out = IntArray(tw * th)
        val layers = ArrayList<Layer>()
        val cels = ArrayList<IntArray>()
        for (l in doc.layers) {
            if (!l.visible || l.opacity == 0) continue
            val c = doc.cel(l.id, frameId) ?: continue
            layers.add(l); cels.add(c)
        }
        if (layers.isEmpty()) return out
        for (y in 0 until th) {
            val sy = (y.toLong() * doc.height / th).toInt()
            for (x in 0 until tw) {
                val si = sy * doc.width + (x.toLong() * doc.width / tw).toInt()
                var c = 0
                for (i in layers.indices) {
                    val s = cels[i][si]
                    if (s ushr 24 != 0) c = PixelColor.blend(c, s, layers[i].blend, layers[i].opacity)
                }
                out[y * tw + x] = c
            }
        }
        return out
    }

    fun compositeFrame(doc: Document, frameId: Int): IntArray {
        val out = IntArray(doc.pixelCount)
        composite(doc, frameId, out)
        return out
    }

    /** Pose `src` (image de `sw × sh`) sur `dst` (`dw × dh`) avec un décalage, sans dépasser. */
    fun overlay(dst: IntArray, dw: Int, dh: Int, src: IntArray, sw: Int, sh: Int, ox: Int, oy: Int) {
        for (sy in 0 until sh) {
            val dy = sy + oy
            if (dy < 0 || dy >= dh) continue
            for (sx in 0 until sw) {
                val dx = sx + ox
                if (dx < 0 || dx >= dw) continue
                val s = src[sy * sw + sx]
                if (s == 0) continue
                val i = dy * dw + dx
                dst[i] = PixelColor.over(dst[i], s)
            }
        }
    }
}
