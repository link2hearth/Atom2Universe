package com.Atom2Universe.app.pixelart.core

enum class SelectMode { REPLACE, ADD, SUBTRACT }

/**
 * Zone sélectionnée : un masque d'un octet par pixel (0 = hors sélection) et sa boîte englobante.
 */
class Selection(val w: Int, val h: Int, val mask: ByteArray = ByteArray(w * h)) {

    val bounds = PixelRect.empty()
    var count = 0
        private set

    init {
        recompute()
    }

    val isEmpty get() = count == 0

    fun isSet(x: Int, y: Int): Boolean = x in 0 until w && y in 0 until h && mask[y * w + x].toInt() != 0

    fun recompute() {
        var c = 0
        var l = w; var t = h; var r = 0; var b = 0
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) if (mask[row + x].toInt() != 0) {
                c++
                if (x < l) l = x
                if (x + 1 > r) r = x + 1
                if (y < t) t = y
                if (y + 1 > b) b = y + 1
            }
        }
        count = c
        if (c == 0) bounds.set(0, 0, 0, 0) else bounds.set(l, t, r, b)
    }

    /** Combine [other] (même taille) avec cette sélection selon [mode]. */
    fun combine(other: ByteArray, mode: SelectMode) {
        when (mode) {
            SelectMode.REPLACE -> System.arraycopy(other, 0, mask, 0, mask.size)
            SelectMode.ADD -> for (i in mask.indices) if (other[i].toInt() != 0) mask[i] = 1
            SelectMode.SUBTRACT -> for (i in mask.indices) if (other[i].toInt() != 0) mask[i] = 0
        }
        recompute()
    }

    fun selectRect(l: Int, t: Int, r: Int, b: Int, mode: SelectMode) {
        val m = ByteArray(mask.size)
        for (y in t.coerceAtLeast(0) until b.coerceAtMost(h)) {
            for (x in l.coerceAtLeast(0) until r.coerceAtMost(w)) m[y * w + x] = 1
        }
        combine(m, mode)
    }

    fun selectAll() = selectRect(0, 0, w, h, SelectMode.REPLACE)

    fun clear() {
        java.util.Arrays.fill(mask, 0)
        recompute()
    }

    fun invert() {
        for (i in mask.indices) mask[i] = if (mask[i].toInt() == 0) 1 else 0
        recompute()
    }

    fun copy(): Selection = Selection(w, h, mask.copyOf())

    /**
     * Contour de la sélection sous forme de segments (x0, y0, x1, y1) en coordonnées de coins de pixels.
     * Les arêtes alignées sont fusionnées : un rectangle ne donne que quatre segments.
     */
    fun outline(): IntArray {
        if (isEmpty) return IntArray(0)
        val out = ArrayList<Int>()
        val l = bounds.left; val r = bounds.right; val t = bounds.top; val b = bounds.bottom
        // Arêtes horizontales : entre la ligne y-1 et la ligne y.
        for (y in t..b) {
            var start = -1
            for (x in l..r) {
                val edge = x < r && isSet(x, y - 1) != isSet(x, y)
                if (edge && start < 0) start = x
                if (!edge && start >= 0) {
                    out.add(start); out.add(y); out.add(x); out.add(y)
                    start = -1
                }
            }
        }
        // Arêtes verticales : entre la colonne x-1 et la colonne x.
        for (x in l..r) {
            var start = -1
            for (y in t..b) {
                val edge = y < b && isSet(x - 1, y) != isSet(x, y)
                if (edge && start < 0) start = y
                if (!edge && start >= 0) {
                    out.add(x); out.add(start); out.add(x); out.add(y)
                    start = -1
                }
            }
        }
        return out.toIntArray()
    }

    companion object {
        /** Sélection dont le masque est celui d'un polygone (lasso), en coordonnées de centres de pixels. */
        fun fromPolygon(w: Int, h: Int, xs: FloatArray, ys: FloatArray): ByteArray {
            val m = ByteArray(w * h)
            Raster.polygon(xs, ys, true) { x, y ->
                if (x in 0 until w && y in 0 until h) m[y * w + x] = 1
            }
            return m
        }
    }
}
