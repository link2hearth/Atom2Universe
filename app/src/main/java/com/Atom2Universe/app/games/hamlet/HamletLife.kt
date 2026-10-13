package com.Atom2Universe.app.games.hamlet

/** Économie des productions : aucune dépendance au dessin ou au téléphone. */
data class Produce(val seconds: Int, val coins: Int, val xp: Int, val feed: Int = 0, val glyph: String = "fruit")

object HamletLife {
    // Les vergers conservent trois récoltes hors ligne. Un élevage attend son prochain repas.
    val produce = mapOf(
        "henyard" to Produce(180, 30, 7, 8, "egg"),
        "rabbitgarden" to Produce(600, 70, 14, 18, "paw"),
        "sheepmeadow" to Produce(1200, 140, 28, 30, "cloth"),
        "dairyfarm" to Produce(1800, 220, 42, 45, "milk"),
        "duckpond" to Produce(900, 95, 20, 22, "egg"),
        "goathill" to Produce(1500, 180, 34, 36, "milk"),
        "treeOrange" to Produce(1800, 22, 5),
        "treeApple" to Produce(1800, 24, 6, glyph = "apple"),
        "lemon" to Produce(2400, 30, 7),
        "pear" to Produce(2700, 32, 8),
        "plum" to Produce(3000, 38, 9),
        "beehive" to Produce(1800, 50, 12, glyph = "pot"),
    )

    val dailyKeys = listOf("harvested", "products", "delivered")
    val dailyTargets = listOf(8, 4, 3)
    val dailyCoins = listOf(100, 140, 180)
    val services = setOf("well", "school", "tavern", "library", "farmmarket", "gazebo", "fountain", "smallfountain", "birdbath")
}
