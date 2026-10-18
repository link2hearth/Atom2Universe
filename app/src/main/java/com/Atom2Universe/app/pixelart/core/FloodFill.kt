package com.Atom2Universe.app.pixelart.core

/**
 * Sélection de pixels par ressemblance de couleur : sert au pot de peinture et à la baguette magique.
 */
object FloodFill {

    /**
     * Masque (1 = retenu) des pixels de [src] proches de celui en (sx, sy).
     *
     * - [tolerance] : écart maximal toléré sur chaque canal, 0 = couleur identique.
     * - [contiguous] : seulement les pixels reliés au point de départ ; sinon, toute l'image.
     * - [limit] : si fourni, les pixels hors de ce masque sont des murs.
     * - [bounds] reçoit la boîte englobante du résultat.
     */
    fun region(
        src: IntArray,
        w: Int,
        h: Int,
        sx: Int,
        sy: Int,
        tolerance: Int,
        contiguous: Boolean,
        limit: ByteArray? = null,
        bounds: PixelRect? = null,
    ): ByteArray {
        val mask = ByteArray(w * h)
        bounds?.set(0, 0, 0, 0)
        if (sx !in 0 until w || sy !in 0 until h) return mask
        if (limit != null && limit[sy * w + sx].toInt() == 0) return mask
        val seed = src[sy * w + sx]

        fun ok(i: Int): Boolean =
            (limit == null || limit[i].toInt() != 0) && PixelColor.distance(src[i], seed) <= tolerance

        if (!contiguous) {
            for (i in 0 until w * h) if (ok(i)) {
                mask[i] = 1
                bounds?.union(i % w, i / w, i % w + 1, i / w + 1)
            }
            return mask
        }

        var stack = IntArray(256)
        var top = 0
        fun push(x: Int, y: Int) {
            if (top + 2 > stack.size) stack = stack.copyOf(stack.size * 2)
            stack[top++] = x
            stack[top++] = y
        }
        push(sx, sy)
        while (top > 0) {
            val y = stack[--top]
            val x = stack[--top]
            val row = y * w
            if (mask[row + x].toInt() != 0 || !ok(row + x)) continue
            var l = x
            while (l > 0 && mask[row + l - 1].toInt() == 0 && ok(row + l - 1)) l--
            var r = x
            while (r < w - 1 && mask[row + r + 1].toInt() == 0 && ok(row + r + 1)) r++
            for (i in l..r) mask[row + i] = 1
            bounds?.union(l, y, r + 1, y + 1)
            for (ny in intArrayOf(y - 1, y + 1)) {
                if (ny < 0 || ny >= h) continue
                val nrow = ny * w
                var inRun = false
                for (i in l..r) {
                    val hit = mask[nrow + i].toInt() == 0 && ok(nrow + i)
                    if (hit && !inRun) push(i, ny)
                    inRun = hit
                }
            }
        }
        return mask
    }
}
