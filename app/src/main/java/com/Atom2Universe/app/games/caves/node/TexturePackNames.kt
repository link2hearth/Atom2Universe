package com.Atom2Universe.app.games.caves.node

/**
 * Le contrat de nommage des packs de textures : ce sont ces noms, lisibles, que voit l'utilisateur
 * dans le zip. Rien ici ne touche à Android, pour que les tests JVM puissent le vérifier.
 */
internal object TexturePackNames {
    private const val MIN_NATIVE = 16

    /** Minuscules, chiffres, `_` et `-` : un nom sûr sur tous les systèmes de fichiers. */
    fun sanitize(raw: String): String {
        val cleaned = buildString {
            for (c in raw.lowercase()) append(if (c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-') c else '_')
        }.trim('_')
        return cleaned.ifEmpty { "texture" }
    }

    /**
     * Un bloc dont le dessus, le côté et le dessous partagent une texture s'appelle `pierre`.
     * Sinon chaque face prend son suffixe : `herbe_top`, `herbe_side`.
     */
    fun faceName(block: String, suffix: String, allFacesSame: Boolean = false) =
        if (allFacesSame) block else "${block}_$suffix"

    /** Rend chaque nom unique, sans tenir compte de la casse (Windows). */
    class Namer {
        private val taken = HashSet<String>()

        fun unique(raw: String): String {
            val base = sanitize(raw)
            var name = base
            var n = 2
            while (!taken.add(name)) name = "${base}_${n++}"
            return name
        }
    }

    /**
     * Beaucoup de textures sont dessinées à 32 px puis agrandies sans lissage à 96 px. Pour l'export,
     * on retrouve la vraie résolution : si chaque bloc f×f est d'une seule couleur, on réduit sans
     * aucune perte (réagrandi, on retombe exactement sur les mêmes pixels).
     */
    fun shrinkToNative(pixels: IntArray, size: Int): Pair<IntArray, Int> {
        require(size > 0 && pixels.size == size * size)
        // Jamais sous 16 px : une texture unie ne doit pas sortir en 1×1, illisible à éditer.
        for (f in size / MIN_NATIVE downTo 2) {
            if (size % f != 0 || !uniformBlocks(pixels, size, f)) continue
            val out = size / f
            return IntArray(out * out) { pixels[(it / out * f) * size + (it % out) * f] } to out
        }
        return pixels to size
    }

    private fun uniformBlocks(pixels: IntArray, size: Int, f: Int): Boolean {
        for (by in 0 until size step f) for (bx in 0 until size step f) {
            val first = pixels[by * size + bx]
            for (y in by until by + f) for (x in bx until bx + f) if (pixels[y * size + x] != first) return false
        }
        return true
    }
}
