package com.Atom2Universe.app.games.toyboxracers.track

import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import kotlin.math.PI
import kotlin.math.cos

/**
 * Maison de poupée ouverte par le toit : atelier, séjour/cuisine, chambre.
 * Les dalles, murs et ouvertures partagent les mêmes cotes. Les quatre rampes
 * passent dans les ailes extérieures, hors des dalles des étages.
 */
internal object HouseGeometry {
    const val LEVEL_HEIGHT = 26f
    const val HALF_WIDTH = 214f
    const val HALF_DEPTH = 102f
    private const val SHELL_X = 132f
    private const val SHELL_Z = 94f
    private const val ATRIUM_X = 38f
    private const val ATRIUM_Z = 30f
    private const val SLAB = 1.2f
    private val wallColors = intArrayOf(0xB9D8D5, 0xF0D4D9, 0xD7CFEB)
    private val accents = intArrayOf(0x579C9A, 0xD68D78, 0x9A84BE)

    // Coordonnées en segments du ruban, partagées par le profil et les vides.
    private data class Jump(val approach: Float, val lip: Float, val landing: Float, val end: Float)
    private val jumps = listOf(
        Jump(1.08f, 1.62f, 1.72f, 2.18f),
        Jump(23.30f, 23.84f, 23.98f, 24.58f)
    )
    val crossings: List<GradeCrossing>
        get() = jumps.map { GradeCrossing(it.lip / route.size, it.landing / route.size) }

    fun floorBoxes(): List<RoomBox> = buildList {
        add(RoomBox(0f, -SLAB / 2, 0f, HALF_WIDTH * 2, SLAB, HALF_DEPTH * 2, 0xCFDFDE))
        for (level in 1..2) {
            val y = level * LEVEL_HEIGHT - SLAB / 2
            val color = if (level == 1) 0xF3DFC5 else 0xE5DDF2
            for (side in floatArrayOf(-1f, 1f)) {
                add(RoomBox(side * (SHELL_X + ATRIUM_X) / 2, y, 0f,
                    SHELL_X - ATRIUM_X, SLAB, SHELL_Z * 2, color))
                add(RoomBox(0f, y, side * (SHELL_Z + ATRIUM_Z) / 2,
                    ATRIUM_X * 2, SLAB, SHELL_Z - ATRIUM_Z, color))
            }
        }
    }

    fun wallBoxes(): List<RoomBox> = buildList {
        for (level in 0..2) {
            val base = level * LEVEL_HEIGHT
            val color = wallColors[level]
            // Façades continues du plancher au plafond, fenêtres en retrait.
            for (side in floatArrayOf(-1f, 1f)) {
                add(RoomBox(0f, base + 13f, side * SHELL_Z, SHELL_X * 2 + 2f, 26f, 2f, color))
                // Deux portails de 52 unités (z = ±36..±88) raccordent les rampes.
                add(RoomBox(side * SHELL_X, base + 13f, 0f, 2f, 26f, 72f, color))
                for (z in floatArrayOf(-91f, 91f))
                    add(RoomBox(side * SHELL_X, base + 13f, z, 2f, 26f, 6f, color))
                for (z in floatArrayOf(-62f, 62f))
                    add(RoomBox(side * SHELL_X, base + 22f, z, 2f, 8f, 52f, color))
            }
        }
        // Socle périphérique bas : les ailes restent lisibles depuis la voiture.
        for (x in floatArrayOf(-HALF_WIDTH, HALF_WIDTH))
            add(RoomBox(x, 2f, 0f, 2f, 4f, HALF_DEPTH * 2, 0x8DA7B3))
        for (z in floatArrayOf(-HALF_DEPTH, HALF_DEPTH))
            add(RoomBox(0f, 2f, z, HALF_WIDTH * 2, 4f, 2f, 0x8DA7B3))
    }

    fun furnitureBoxes(): List<RoomBox> = buildList {
        fun box(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: Int) {
            add(RoomBox(x, y, z, w, h, d, color))
        }
        for (level in 0..2) {
            val y = level * LEVEL_HEIGHT
            val accent = accents[level]
            // Plinthes, corniches et piliers : chaque bloc appartient à la structure.
            for (z in floatArrayOf(-92.7f, 92.7f)) {
                box(0f, y + .8f, z, SHELL_X * 2 - 2, 1.6f, .6f, accent)
                box(0f, y + 24.7f, z, SHELL_X * 2 - 2, 1.2f, .8f, 0xFFF0D1)
                for (x in floatArrayOf(-96f, -48f, 0f, 48f, 96f)) {
                    box(x, y + 14f, z, 25f, 13f, .3f, 0xFFF0D1)
                    box(x, y + 14f, z - kotlin.math.sign(z) * .25f,
                        22f, 10f, .3f, 0x8DBED3)
                    box(x, y + 14f, z - kotlin.math.sign(z) * .5f,
                        .7f, 11f, .3f, 0xFFF0D1)
                    box(x, y + 14f, z - kotlin.math.sign(z) * .5f,
                        23f, .7f, .3f, 0xFFF0D1)
                    box(x, y + 7f, z - kotlin.math.sign(z), 26f, .7f, 2f, 0xDAB68B)
                }
            }
            for (x in floatArrayOf(-SHELL_X + 2, SHELL_X - 2)) {
                for (z in floatArrayOf(-90f, 90f))
                    box(x, y + 13f, z, 2f, 26f, 3f, 0xFFF0D1)
                box(x, y + .8f, 0f, .7f, 1.6f, 70f, accent)
                for (z in floatArrayOf(-62f, 62f))
                    box(x, y + 18.8f, z, .8f, 1.6f, 52f, accent)
            }
        }
        // Poteaux de l'atrium ancrés dans la dalle, hors du passage du pont.
        for (x in floatArrayOf(-36f, 36f)) for (z in floatArrayOf(-28f, 28f))
            box(x, 26f, z, 2f, 52f, 2f, 0xFFF0D1)
        for (level in 1..2) {
            val y = level * LEVEL_HEIGHT
            fun rail(x: Float, z: Float, w: Float, d: Float) {
                box(x, y + .7f, z, w, 1.4f, d, accents[level])
                box(x, y + 4f, z, w, .6f, d, 0xDAB68B)
            }
            for (z in floatArrayOf(-31f, 31f)) {
                rail(0f, z, 78f, .8f)
                for (x in floatArrayOf(-38f, -19f, 0f, 19f, 38f))
                    box(x, y + 2f, z, .7f, 4f, .7f, 0xFFF0D1)
            }
            for (x in floatArrayOf(-39f, 39f)) {
                // Au dernier étage, ouverture de 36 unités pour le pont et le saut.
                if (level == 1) rail(x, 0f, .8f, 60f)
                else for (z in floatArrayOf(-24.5f, 24.5f)) rail(x, z, .8f, 13f)
                for (z in floatArrayOf(-30f, -18f, 18f, 30f))
                    box(x, y + 2f, z, .7f, 4f, .7f, 0xFFF0D1)
            }
        }
        // Piles sous les grandes rampes : sommet exactement sous le tablier.
        for (segment in floatArrayOf(5f, 17f, 31f, 37f)) {
            val p = point(segment / route.size)
            val top = p.y + PrototypeTrack.ROAD_SURFACE_LIFT - PrototypeTrack.ROAD_THICKNESS
            box(p.x, top / 2, p.z, 5f, top, 5f, 0xA9BDC6)
            box(p.x, .6f, p.z, 10f, 1.2f, 10f, 0x8DA7B3)
        }
    }

    fun furnitureDecorations(): List<DecorPlacement> = buildList {
        fun place(id: String, x: Float, y: Float, z: Float, scale: Float = 1f, turn: Int = 0): DecorPlacement {
            return DecorPlacement(DecorCatalog[id], x, y, z, turn, scale).also { add(it) }
        }
        fun onTop(id: String, support: DecorPlacement, scale: Float) {
            place(id, support.x, support.solids.maxOf { it.top }, support.z, scale)
        }
        // Atelier : outils sur l'établi, stockage contre les murs, espace de jeu central.
        place("garage.workbench", -76f, 0f, -82f, .85f)
        place("garage.toolbox", -80f, 8.5f, -81f, .7f)
        place("garage.tool_chest", -48f, 0f, -83f)
        place("garage.storage_rack", 76f, 0f, -83f, .85f)
        place("garage.tires", 95f, 0f, -82f)
        onTop("garage.crate", place("garage.crate", 108f, 0f, 18f, 1.2f), .8f)
        place("garage.cone", 28f, 0f, -77f, .8f)
        place("garage.cone", 28f, 0f, -42f, .8f)
        place("garage.storage_rack", -90f, 0f, 82f, .8f, 2)
        place("garage.tires", -106f, 0f, 25f, 1.1f)

        // Séjour côté ouest, cuisine côté est : petits objets posés sur leurs meubles.
        place("living.sofa", -87f, 26f, -81f, 1.05f)
        onTop("living.books", place("living.coffee_table", -89f, 26f, -63f, .75f), .55f)
        place("kawaii.rug_patchwork", -88f, 26f, -46f, .8f)
        place("living.floor_lamp", -109f, 26f, -81f, .9f)
        place("living.armchair", -87f, 26f, -35f, .9f, 2)
        place("living.tv_cabinet", -88f, 26f, 0f, .8f, 2)
        place("kitchen.fridge", 107f, 26f, -82f, .9f)
        onTop("kitchen.kettle", place("kitchen.counter", 86f, 26f, -82f, .9f), .7f)
        place("kitchen.sink", 64f, 26f, -82f, .9f)
        place("kitchen.oven", 43f, 26f, -82f, .9f)
        onTop("kitchen.fruit_bowl", place("kitchen.island", 88f, 26f, 4f, .95f), .75f)
        place("living.dining_table", 85f, 26f, 34f, .8f)
        place("living.dining_chair", 85f, 26f, 23f, .8f)
        place("living.dining_chair", 85f, 26f, 45f, .8f, 2)

        // Chambre et coin lecture, tous les meubles reposent sur la mezzanine.
        place("bedroom.double_bed", -78f, 52f, -83f, .8f)
        onTop("bedroom.table_lamp", place("bedroom.nightstand", -95f, 52f, -83f, .8f), .8f)
        place("bedroom.wardrobe", -44f, 52f, -83f, .9f)
        place("office.dresser", 82f, 52f, -82f, .9f)
        place("living.armchair", 89f, 52f, 27f, 1.1f)
        onTop("living.books", place("living.coffee_table", 90f, 52f, 43f, .7f), .5f)
        place("kawaii.rug_stripes", -88f, 52f, -42f, 1.1f)
        place("living.floor_lamp", 108f, 52f, 30f)
        place("living.books", -89f, 52f, 24f, .9f)
        for (level in 0..2) {
            val y = level * LEVEL_HEIGHT
            for (x in floatArrayOf(-30f, 30f))
                place("living.plant", x, y, 82f, 1.1f)
            place("living.plant", 111f, y, 80f, .9f)
        }
    }

    fun toys(): List<ToyObstacle> = listOf(
        ToyObstacle(ToyKind.TEDDY, -86f, 28f, 5.5f, 10f),
        ToyObstacle(ToyKind.BLOCKS, -15f, -24f, 6.5f, 7f),
        ToyObstacle(ToyKind.TRAIN, 22f, 28f, 8f, 7f),
        ToyObstacle(ToyKind.SPINNING_TOP, 91f, 42f, 5f, 8f)
    )

    /** Élargissement progressif : aucune marche latérale à l'entrée des réceptions. */
    fun roadWidth(fraction: Float): Float {
        val s = fraction * route.size
        var width = 15f
        for (range in listOf(4f..7f, 16f..19f, 30f..33f, 36f..39f)) {
            val blend = minOf((s - range.start) / .4f, (range.endInclusive - s) / .4f).coerceIn(0f, 1f)
            width = maxOf(width, 15f + 2.5f * smooth(blend))
        }
        for (jump in jumps) {
            val blend = minOf((s - jump.approach + .2f) / .3f, (jump.end + .2f - s) / .3f).coerceIn(0f, 1f)
            width = maxOf(width, 15f + 4f * smooth(blend))
        }
        return width
    }

    fun isJumpApproach(fraction: Float) = jumps.any { fraction * route.size in it.approach..it.lip }
    fun isJumpLanding(fraction: Float) = jumps.any { fraction * route.size in it.landing..it.end }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)

    private fun stuntHeight(s: Float): Float {
        for (jump in jumps) {
            if (s in jump.approach..jump.lip) {
                val t = (s - jump.approach) / (jump.lip - jump.approach)
                // Pente positive à la lèvre : l'impulsion provient des roues.
                return 4.5f * t * t
            }
            if (s in jump.lip..jump.landing)
                return 4.5f - 3.5f * (s - jump.lip) / (jump.landing - jump.lip)
            if (s in jump.landing..jump.end)
                return 1f - smooth((s - jump.landing) / (jump.end - jump.landing))
        }
        // Double vague sur la ligne droite du séjour, loin des virages.
        for (center in floatArrayOf(12.85f, 13.55f)) {
            val t = (s - center) / .3f
            if (t in -1f..1f) return 1.4f * (1f + cos(t * PI.toFloat()))
        }
        return 0f
    }
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
        val y = b.y + (c.y-b.y) * t*t*(3f-2f*t) + stuntHeight(scaled)
        // Le lift commun de la piste évite deux faces coplanaires avec le parquet.
        return Vec3(curve(a.x,b.x,c.x,d.x), y,
            curve(a.z,b.z,c.z,d.z))
    }
}
