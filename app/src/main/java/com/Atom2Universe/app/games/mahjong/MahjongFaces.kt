package com.Atom2Universe.app.games.mahjong

/**
 * Le jeu de tuiles traditionnel : 0-8 ronds, 9-17 bambous, 18-26 caractères, 27-30 vents
 * (est, sud, ouest, nord), 31-33 dragons (rouge, vert, blanc), 34-37 fleurs, 38-41 saisons.
 * La « famille » ([kind]) décide des associations. Les quatre fleurs se dessinent toutes avec
 * la porcelaine équipée et les quatre saisons avec un même dessin : seules des tuiles
 * identiques à l'œil s'associent.
 */
internal object MahjongFaces {
    const val COUNT = 42
    const val KINDS = 36
    const val FLOWERS = 34
    const val SEASONS = 38
    fun kind(face: Int) = when { face < FLOWERS -> face; face < SEASONS -> 34; else -> 35 }
    fun isNumber(face: Int) = face < 27
    fun suit(face: Int) = face / 9
    fun rank(face: Int) = face % 9 + 1
    /** Faces disponibles pour une famille : une seule, sauf fleurs et saisons. */
    fun faces(kind: Int) = when (kind) { 34 -> (FLOWERS until SEASONS).toList(); 35 -> (SEASONS until COUNT).toList(); else -> listOf(kind) }
}
