package com.Atom2Universe.app.games.toyboxracers.track

import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3

/** Maison ouverte en coupe : garage, séjour et mezzanine autour d'un atrium.
 * X/Z = plan horizontal, Y = altitude absolue. Les planchers sont des volumes
 * finis partagés par le rendu et les collisions, jamais une hauteur globale.
 * HOUSE_GROUND_FLOOR reste l'identifiant sauvegardé du mode Maison.
 */
internal object HouseGeometry {
    const val LEVEL_HEIGHT = 26f
    const val HALF_WIDTH = 214f
    const val HALF_DEPTH = 102f

    fun floorBoxes(): List<RoomBox> = buildList {
        add(RoomBox(0f, -.4f, 0f, HALF_WIDTH * 2f, .8f, HALF_DEPTH * 2f, 0xD7E6E4))
        for (level in 1..2) {
            val y = level * LEVEL_HEIGHT - .4f
            val color = if (level == 1) 0xF3DFC5 else 0xE5DDF2
            // Ouverture centrale de 76 × 60 sur les trois niveaux.
            add(RoomBox(-69f, y, 0f, 62f, .8f, 160f, color))
            add(RoomBox(69f, y, 0f, 62f, .8f, 160f, color))
            add(RoomBox(0f, y, -55f, 76f, .8f, 50f, color))
            add(RoomBox(0f, y, 55f, 76f, .8f, 50f, color))
        }
    }

    fun wallBoxes(): List<RoomBox> = buildList {
        for (level in 0..2) {
            val y = level * LEVEL_HEIGHT
            val color = if (level == 1) 0xF0D4D9 else 0xD2DDED
            for (z in floatArrayOf(-96f, 96f))
                add(RoomBox(0f, y + 5f, z, 232f, 10f, 2f, color))
            // Grandes ouvertures aux quatre coins pour les rampes extérieures.
            for (x in floatArrayOf(-132f, 132f))
                add(RoomBox(x, y + 5f, 0f, 2f, 10f, 78f, color))
            if (level < 2) for (x in floatArrayOf(-35f, 35f))
                for (z in floatArrayOf(-27f, 27f))
                    add(RoomBox(x, y + LEVEL_HEIGHT / 2f, z, 2f, LEVEL_HEIGHT, 2f, 0xF3E9D7))
        }
        for (x in floatArrayOf(-HALF_WIDTH, HALF_WIDTH))
            add(RoomBox(x, 2f, 0f, 2f, 4f, HALF_DEPTH * 2f, 0xD2DDED))
        for (z in floatArrayOf(-HALF_DEPTH, HALF_DEPTH))
            add(RoomBox(0f, 2f, z, HALF_WIDTH * 2f, 4f, 2f, 0xD2DDED))
    }

    fun furnitureBoxes(): List<RoomBox> = buildList {
        fun box(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: Int) {
            add(RoomBox(x, y, z, w, h, d, color))
        }
        fun rail(x: Float, baseY: Float, z: Float, w: Float, d: Float, color: Int = 0xF7E7C8) {
            box(x, baseY + 1.1f, z, w, 2.2f, d, color)
            box(x, baseY + 4.7f, z, w, .7f, d, 0xC79A5A)
        }
        fun post(x: Float, baseY: Float, z: Float) = box(x, baseY + 2.4f, z, .9f, 4.8f, .9f, 0xB57F4A)

        // Garde-corps autour de l'atrium. Les ouvertures au centre laissent passer
        // le pont de jeu, mais les bords dangereux deviennent lisibles.
        for (level in 1..2) {
            val y = level * LEVEL_HEIGHT
            for (z in floatArrayOf(-31f, 31f)) {
                rail(-19f, y, z, 36f, .85f)
                rail(19f, y, z, 36f, .85f)
                for (x in floatArrayOf(-37f, -19f, 0f, 19f, 37f)) post(x, y, z)
            }
            for (x in floatArrayOf(-39f, 39f)) {
                rail(x, y, -19f, .85f, 22f)
                rail(x, y, 19f, .85f, 22f)
                for (z in floatArrayOf(-30f, -8f, 8f, 30f)) post(x, y, z)
            }
        }

        // Portes, fenetres et plinthes : volumes minces colles aux murs exterieurs.
        for (level in 0..2) {
            val y = level * LEVEL_HEIGHT
            for (x in floatArrayOf(-76f, -25f, 25f, 76f)) {
                box(x, y + 9f, -94.55f, 20f, 11f, .35f, 0x9FD2E3)
                box(x, y + 9f, 94.55f, 20f, 11f, .35f, 0x9FD2E3)
                box(x, y + 9f, -94.15f, .7f, 12f, .6f, 0xFFF0D1)
                box(x, y + 9f, 94.15f, .7f, 12f, .6f, 0xFFF0D1)
                box(x, y + 9f, -94.15f, 21f, .7f, .6f, 0xFFF0D1)
                box(x, y + 9f, 94.15f, 21f, .7f, .6f, 0xFFF0D1)
                box(x, y + 2.8f, -93.2f, 23f, .8f, 2.8f, 0xC79A5A)
                box(x, y + 2.8f, 93.2f, 23f, .8f, 2.8f, 0xC79A5A)
            }
        }
        for (x in floatArrayOf(-103f, 103f)) {
            box(x, 9f, 83f, 13f, 18f, .8f, 0xA8D1B7)
            box(x + if (x < 0f) 4f else -4f, 9.4f, 82.35f, .7f, 1.1f, .7f, 0xE8C36E)
            box(x, 35f, -83f, 13f, 18f, .8f, 0xDDB4C3)
            box(x + if (x < 0f) 4f else -4f, 35.4f, -82.35f, .7f, 1.1f, .7f, 0xE8C36E)
        }

        // Planches qui montent sur les meubles et plateformes larges pour jouer
        // sans avoir l'impression de suivre une piste peinte au sol.
        box(-75f, 3.3f, -52f, 42f, 1.0f, 13f, 0xC08A55)
        box(-91f, 7.1f, -57f, 34f, 1.0f, 11f, 0xD6A46E)
        box(79f, 29.2f, -51f, 46f, 1.0f, 13f, 0xC08A55)
        box(94f, 33.1f, -57f, 32f, 1.0f, 11f, 0xD6A46E)
        box(-74f, 55.2f, 49f, 46f, 1.0f, 13f, 0xC08A55)
        box(-91f, 59.0f, 55f, 32f, 1.0f, 11f, 0xD6A46E)

        // Escalier hybride : une moitie rampe lisse, l'autre vraies marches.
        box(126f, 4.5f, 66f, 18f, 1.0f, 28f, 0xA7B9C6)
        for (i in 0..6) {
            val z = 54f + i * 3.7f
            box(139f, 1.2f + i * 1.55f, z, 18f, 1.0f, 3.2f, 0xF3E9D7)
            box(148f, 1.8f + i * 1.55f, z, .9f, 3.6f, .8f, 0xB57F4A)
        }
        box(132f, 14.8f, 80f, 38f, 1.0f, 14f, 0xC08A55)

        // Conduits larges d'aeration : les cotes bordent la bande jouable, mais
        // le centre reste la surface de conduite continue.
        for (level in 0..1) {
            val y = level * LEVEL_HEIGHT
            for (side in intArrayOf(-1, 1)) {
                box(side * 110f, y + 6f, -53f, 2.2f, 12f, 20f, 0x8DA7B3)
                box(side * 170f, y + 13f, -19f, 2.2f, 12f, 22f, 0x8DA7B3)
                box(side * 110f, y + 19.8f, 16f, 2.2f, 12f, 20f, 0x8DA7B3)
                for (i in 0..4) {
                    box(side * 116f, y + 4f + i * 3.8f, -33f + i * 14f, 8f, .28f, .9f, 0xE6EEF2)
                }
            }
        }

        // Quelques meubles solides agrandissent les pieces sans fermer le circuit.
        box(-155f, 5f, -72f, 30f, 10f, 13f, 0xA8D1B7)
        box(-155f, 10.4f, -72f, 31f, .8f, 14f, 0xFFF0D1)
        box(154f, 31f, 70f, 31f, 10f, 13f, 0xDDB4C3)
        box(154f, 36.4f, 70f, 32f, .8f, 14f, 0xFFF0D1)
        box(0f, 56f, 79f, 62f, 8f, 10f, 0xB9AAD8)
        box(0f, 60.5f, 79f, 63f, .8f, 11f, 0xFFF0D1)
    }

    fun furnitureDecorations(): List<DecorPlacement> = listOf(
        DecorPlacement(DecorCatalog["garage.workbench"], -78f, 0f, -72f, scale = .7f),
        DecorPlacement(DecorCatalog["garage.tool_chest"], 78f, 0f, -72f, scale = .7f),
        DecorPlacement(DecorCatalog["garage.tires"], 78f, 0f, 30f),
        DecorPlacement(DecorCatalog["garage.storage_rack"], -157f, 0f, 70f, quarterTurns = 2, scale = .75f),
        DecorPlacement(DecorCatalog["garage.crate"], 152f, 0f, 22f, scale = .9f),
        DecorPlacement(DecorCatalog["living.sofa"], -78f, 26f, -72f, scale = .7f),
        DecorPlacement(DecorCatalog["living.coffee_table"], -80f, 26f, -42f, scale = .6f),
        DecorPlacement(DecorCatalog["living.plant"], -83f, 26f, 27f),
        DecorPlacement(DecorCatalog["living.tv_cabinet"], -154f, 26f, 70f, quarterTurns = 2, scale = .8f),
        DecorPlacement(DecorCatalog["living.floor_lamp"], -155f, 26f, -30f, scale = .8f),
        DecorPlacement(DecorCatalog["kitchen.counter"], 78f, 26f, -72f, scale = .7f),
        DecorPlacement(DecorCatalog["kitchen.fridge"], 90f, 26f, -72f, scale = .7f),
        DecorPlacement(DecorCatalog["kitchen.island"], 156f, 26f, -20f, scale = .75f),
        DecorPlacement(DecorCatalog["kitchen.fruit_bowl"], 156f, 33.6f, -20f, scale = .75f),
        DecorPlacement(DecorCatalog["bedroom.wardrobe"], -80f, 52f, -72f, scale = .6f),
        DecorPlacement(DecorCatalog["office.dresser"], 78f, 52f, -72f, scale = .7f),
        DecorPlacement(DecorCatalog["office.computer"], 154f, 52f, -70f, quarterTurns = 2, scale = .75f),
        DecorPlacement(DecorCatalog["bathroom.vanity"], -154f, 52f, 71f, scale = .8f),
        DecorPlacement(DecorCatalog["living.armchair"], -80f, 52f, 30f, scale = .8f),
        DecorPlacement(DecorCatalog["living.books"], 80f, 52f, 28f)
    )

    fun toys(): List<ToyObstacle> = listOf(
        ToyObstacle(ToyKind.TEDDY, -82f, 25f, 5.5f, 10f),
        ToyObstacle(ToyKind.BLOCKS, 20f, -25f, 6.5f, 7f)
    )

    // Ruban fermé : montée à l'est, descente à l'ouest, pont sur l'atrium.
    // Les changements d'altitude passent hors des planchers des étages.
    private val route = listOf(
        Vec3(-78f,0f,-62f), Vec3(-15f,0f,-60f), Vec3(62f,0f,-58f),
        Vec3(104f,0f,-66f), Vec3(136f,0f,-52f), Vec3(151f,13f,-2f),
        Vec3(136f,26f,54f), Vec3(102f,26f,66f), Vec3(60f,26f,58f),
        Vec3(0f,26f,55f), Vec3(-60f,26f,55f), Vec3(-60f,26f,0f),
        Vec3(-60f,26f,-55f), Vec3(0f,26f,-55f), Vec3(60f,26f,-55f),
        Vec3(103f,26f,-65f), Vec3(155f,26f,-58f), Vec3(186f,39f,-1f),
        Vec3(156f,52f,57f), Vec3(102f,52f,68f), Vec3(60f,52f,58f),
        Vec3(0f,52f,55f), Vec3(-60f,52f,55f), Vec3(-60f,52f,0f),
        Vec3(0f,52f,0f), Vec3(60f,52f,0f), Vec3(60f,52f,-55f),
        Vec3(0f,52f,-55f), Vec3(-60f,52f,-55f), Vec3(-104f,52f,-66f),
        Vec3(-156f,52f,-58f), Vec3(-186f,39f,-1f), Vec3(-156f,26f,57f),
        Vec3(-118f,26f,67f), Vec3(-116f,26f,22f), Vec3(-116f,26f,-30f),
        Vec3(-136f,26f,-52f), Vec3(-151f,13f,-2f), Vec3(-136f,0f,54f),
        Vec3(-104f,0f,66f), Vec3(-62f,0f,58f), Vec3(0f,0f,55f),
        Vec3(62f,0f,58f), Vec3(62f,0f,2f), Vec3(0f,0f,0f), Vec3(-62f,0f,2f)
    )

    fun point(fraction: Float): Vec3 {
        val scaled = fraction * route.size
        val index = scaled.toInt() % route.size
        val t = scaled - scaled.toInt()
        fun p(offset: Int) = route[(index + offset + route.size) % route.size]
        val a = p(-1); val b = p(0); val c = p(1); val d = p(2)
        fun curve(a: Float, b: Float, c: Float, d: Float) = .5f *
            (2*b + (-a+c)*t + (2*a-5*b+4*c-d)*t*t + (-a+3*b-3*c+d)*t*t*t)
        val y = b.y + (c.y-b.y) * t*t*(3f-2f*t)
        // Le lift commun de la piste évite deux faces coplanaires avec le parquet.
        return Vec3(curve(a.x,b.x,c.x,d.x), y,
            curve(a.z,b.z,c.z,d.z))
    }
}
