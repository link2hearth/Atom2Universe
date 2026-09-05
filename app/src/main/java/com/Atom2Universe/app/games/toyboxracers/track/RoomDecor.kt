package com.Atom2Universe.app.games.toyboxracers.track

/** Les mêmes boîtes décrivent les meshes et les volumes solides du mobilier. */
internal data class RoomBox(
    val x: Float, val y: Float, val z: Float,
    val width: Float, val height: Float, val depth: Float, val color: Int
) {
    val left get() = x - width * 0.5f
    val right get() = x + width * 0.5f
    val bottom get() = y - height * 0.5f
    val top get() = y + height * 0.5f
    val back get() = z - depth * 0.5f
    val front get() = z + depth * 0.5f
}

internal object RoomDecor {
    const val WOOD = 0xDAB68B
    const val CREAM = 0xFFF0D1
    const val MINT = 0x91CDB4
    const val ROSE = 0xECA0AF
    const val BLUE = 0x91BEDD
    const val LILAC = 0xB9AAD8
    private val covers = intArrayOf(ROSE, MINT, BLUE, LILAC, 0xE8C36E)

    val boxes: List<RoomBox> = buildList {
        fun box(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: Int) {
            add(RoomBox(x, y, z, w, h, d, color))
        }
        fun book(x: Float, bottom: Float, z: Float, w: Float, h: Float, d: Float, color: Int) {
            box(x, bottom + h / 2, z, w, h, d, color)
            // Tranche ivoire, coiffes et petite étiquette sur le dos.
            box(x, bottom + h / 2, z + d / 2 + 0.025f, w * 0.72f, h * 0.75f, 0.05f, CREAM)
            box(x, bottom + h * 0.68f, z + d / 2 + 0.065f, w * 0.5f, 0.3f, 0.05f, color)
        }

        // Bureau : plateau à chant clair, pieds distincts, caisson à trois tiroirs.
        box(-20f, 10.2f, -65f, 34f, 1.3f, 15f, WOOD)
        box(-20f, 10.9f, -65f, 34.4f, 0.25f, 15.4f, CREAM)
        for (x in floatArrayOf(-35f, -5f)) for (z in floatArrayOf(-71f, -59f)) {
            box(x, 4.8f, z, 1.6f, 9.6f, 1.6f, MINT)
            box(x, 0.55f, z, 1.7f, 1.1f, 1.7f, WOOD)
        }
        box(-30f, 6.3f, -65f, 8f, 6.2f, 12f, WOOD)
        repeat(3) { i ->
            box(-30f, 4.3f + i * 2f, -58.9f, 7.4f, 1.8f, 0.35f, covers[i])
            box(-30f, 4.3f + i * 2f, -58.6f, 1.6f, 0.3f, 0.3f, CREAM)
        }
        // Petit tabouret sous le bord du bureau, sans volume invisible entre ses pieds.
        box(-13f, 5f, -54f, 7f, 1f, 6f, ROSE)
        for (x in floatArrayOf(-15.5f, -10.5f)) for (z in floatArrayOf(-56f, -52f))
            box(x, 2.25f, z, 1f, 4.5f, 1f, WOOD)
        repeat(3) { i -> book(-29f + i * 0.4f, 11.05f + i * 0.85f, -66f, 7f, 0.8f, 5f, covers[i]) }
        // Cahier ouvert et crayon sur le plateau.
        box(-17f, 11.1f, -62f, 7.5f, 0.15f, 4.8f, LILAC)
        for (x in floatArrayOf(-19f, -15f)) {
            box(x, 11.22f, -62f, 3.7f, 0.12f, 4.4f, CREAM)
            repeat(4) { i -> box(x, 11.29f, -63.4f + i * 0.8f, 2.7f, 0.025f, 0.055f, BLUE) }
        }
        box(-11f, 11.25f, -61f, 0.25f, 0.3f, 4.2f, 0xE8C36E)

        // Bibliothèque frontale, fond, côtés et quatre vrais niveaux de rangement.
        box(30f, 11f, 73f, 24f, 22f, 0.65f, LILAC)
        for (x in floatArrayOf(18f, 42f)) box(x, 11f, 70.5f, 0.8f, 22f, 5.6f, WOOD)
        for (y in floatArrayOf(0.7f, 7.3f, 14f, 21.7f)) box(30f, y, 70.5f, 24f, 0.7f, 5.6f, CREAM)
        // Livres orientés vers l'intérieur de la chambre (face -Z).
        repeat(9) { i ->
            val h = 3.8f + (i % 3) * 0.6f
            box(20f + i * 2.3f, 14.35f + h / 2, 70.4f, 1.7f, h, 3.6f, covers[i % covers.size])
            box(20f + i * 2.3f, 16f, 68.55f, 1.1f, 0.4f, 0.12f, CREAM)
        }
        for (x in floatArrayOf(23f, 36f)) {
            box(x, 3f, 70f, 8f, 4f, 4.4f, if (x < 30f) MINT else ROSE)
            box(x, 3.4f, 67.72f, 2.4f, 0.65f, 0.15f, CREAM)
        }
        repeat(4) { i -> book(23f, 7.65f + i * 0.95f, 70f, 7f, 0.9f, 4f, covers[i]) }

        // Deux étagères murales arrière avec équerres, livres et boîtes.
        for (y in floatArrayOf(12f, 22f)) {
            box(66f, y, -71.7f, 25f, 0.65f, 5.8f, WOOD)
            for (x in floatArrayOf(57f, 75f)) {
                box(x, y - 1.3f, -73.8f, 0.65f, 2.6f, 0.65f, CREAM)
                box(x, y - 0.7f, -72f, 0.65f, 0.65f, 3.8f, CREAM)
            }
            repeat(6) { i -> book(56f + i * 2f, y + 0.325f, -71.8f, 1.5f, 3.2f + i % 3, 3.5f, covers[i % 5]) }
            box(74f, y + 1.8f, -71.5f, 5f, 3f, 4f, MINT)
            box(74f, y + 3.4f, -71.5f, 5.3f, 0.3f, 4.3f, CREAM)
        }

        // Lit bas en bois peint, matelas, couverture à bandes et oreiller.
        box(-23f, 2.4f, 62f, 23f, 2f, 20f, WOOD)
        for (x in floatArrayOf(-33f, -13f)) for (z in floatArrayOf(54f, 70f)) box(x, 0.9f, z, 1.8f, 1.8f, 1.8f, WOOD)
        box(-23f, 4.2f, 62f, 22f, 1.7f, 19f, CREAM)
        box(-23f, 5.2f, 59.4f, 22.2f, 0.45f, 13.6f, BLUE)
        repeat(5) { i -> box(-31.5f + i * 4.2f, 5.45f, 59.4f, 1f, 0.06f, 13.4f, CREAM) }
        box(-23f, 6f, 72f, 24f, 9f, 1f, MINT)
        box(-23f, 10.6f, 72f, 24.5f, 0.5f, 1.3f, CREAM)
        box(-23f, 5.7f, 68.4f, 11f, 1.4f, 4.8f, ROSE)

        // Porte fermée à panneaux : elle fait partie du mur, sans fausse sortie.
        box(-73f, 12.5f, 74.5f, 15f, 25f, 0.6f, MINT)
        for (x in floatArrayOf(-81f, -65f)) box(x, 13f, 74f, 1f, 26f, 1.2f, CREAM)
        box(-73f, 26f, 74f, 17f, 1f, 1.2f, CREAM)
        for (y in floatArrayOf(6.8f, 18f)) {
            box(-73f, y, 74.1f, 11.5f, 8.6f, 0.35f, WOOD)
            box(-73f, y, 73.86f, 10.7f, 7.8f, 0.22f, MINT)
        }
        box(-67.2f, 12.8f, 73.6f, 0.7f, 2.3f, 0.5f, WOOD)
        box(-68f, 13f, 73.1f, 2.2f, 0.4f, 0.6f, CREAM)

        // Fenêtres fermées : ciel en aplat, croisillons, rebord et rideaux plissés.
        for (x in floatArrayOf(-21f, 25f)) {
            box(x, 21f, -74.7f, 23f, 13f, 0.3f, 0xAEDBEA)
            for (edge in floatArrayOf(x - 12f, x + 12f)) box(edge, 21f, -74.15f, 1f, 14.5f, 1.1f, CREAM)
            for (y in floatArrayOf(14f, 28f)) box(x, y, -74.15f, 25f, 0.9f, 1.1f, CREAM)
            box(x, 21f, -74.05f, 0.6f, 13f, 1.1f, CREAM)
            box(x, 21f, -74.05f, 23f, 0.6f, 1.1f, CREAM)
            box(x, 13.5f, -73.3f, 26f, 0.7f, 3.2f, WOOD)
            box(x, 29f, -73.2f, 31f, 0.5f, 0.5f, WOOD)
            for (side in intArrayOf(-1, 1)) repeat(4) { i ->
                box(x + side * (12.5f + i * 0.7f), 21f, -73.1f + (i % 2) * 0.3f,
                    0.85f, 14.8f, 0.6f, if (i % 2 == 0) LILAC else ROSE)
            }
        }
    }

    // Volumes simples pour les accessoires arrondis construits par le renderer.
    val solids: List<RoomBox> = boxes + listOf(
        RoomBox(-7.5f, 11.3f, -68f, 3.6f, 0.5f, 3.6f, LILAC),
        RoomBox(-7.5f, 13.6f, -68f, 0.6f, 4.2f, 0.6f, CREAM),
        RoomBox(-7.5f, 16f, -68f, 4.8f, 2.5f, 4.8f, ROSE),
        RoomBox(-24f, 12.1f, -69f, 2.2f, 2.1f, 2.2f, MINT)
    )

}
