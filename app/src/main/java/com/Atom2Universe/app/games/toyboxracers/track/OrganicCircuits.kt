package com.Atom2Universe.app.games.toyboxracers.track

import com.Atom2Universe.app.games.toyboxracers.models.DecorBuilder
import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorModel
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement
import com.Atom2Universe.app.games.toyboxracers.models.DecorRoom
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3

internal enum class CourseSurface { DECK, FLOOR, FURNITURE }

/** La ligne de course est un repère de progression, pas un mur invisible.
 * Seules les rampes ont une dalle ; le sol et le plateau sont les vrais supports.
 */
internal object OrganicCircuits {
    private data class Node(val x: Float, val y: Float, val z: Float, val surface: CourseSurface)
    private fun floor(x: Int, z: Int) = Node(x.toFloat(), 0f, z.toFloat(), CourseSurface.FLOOR)
    private fun ramp(x: Int, y: Int, z: Int) = Node(x.toFloat(), y.toFloat(), z.toFloat(), CourseSurface.DECK)
    private fun top(x: Int, z: Int) = Node(x.toFloat(), 12f, z.toFloat(), CourseSurface.FURNITURE)
    // Variantes en virgule flottante : le parcours signature de la maison colle
    // aux vraies hauteurs de mobilier (bureau, commode, étagère...), jamais 12
    // par défaut.
    private fun floor(x: Float, z: Float) = Node(x, 0f, z, CourseSurface.FLOOR)
    private fun ramp(x: Float, y: Float, z: Float) = Node(x, y, z, CourseSurface.DECK)
    private fun top(x: Float, y: Float, z: Float) = Node(x, y, z, CourseSurface.FURNITURE)

    private val trails = mapOf(
        CircuitKind.FURNITURE_TRAIL to listOf(
            floor(-92,24), floor(-60,26), floor(-24,24), floor(22,24), floor(62,24), floor(82,10),
            ramp(68,0,-20), top(28,-20), top(0,-20), ramp(-28,12,-20), floor(-68,-20),
            floor(-90,-4), floor(-70,4), floor(-38,4), floor(-10,0), floor(0,-10), floor(0,-20),
            floor(0,-44), floor(40,-44), floor(80,-44), floor(98,-28), floor(100,10),
            floor(92,38), floor(60,46), floor(22,46), floor(-22,46), floor(-66,46), floor(-92,40)
        ),
        CircuitKind.WORKSHOP_EXPEDITION to listOf(
            floor(-94,-44), floor(-54,-44), floor(-12,-44), floor(36,-44), floor(84,-40),
            floor(98,-10), floor(94,32), floor(70,44), floor(28,44), floor(-18,44), floor(-66,40),
            floor(-92,16), ramp(-68,0,16), top(-28,16), top(0,16), ramp(28,12,16), floor(68,16),
            floor(78,-6), floor(60,-22), floor(22,-22), floor(0,-4), floor(0,16), floor(0,32),
            floor(-30,30), floor(-58,26), floor(-74,4), floor(-62,-16), floor(-26,-22),
            floor(-44,-34), floor(-80,-30)
        ),
        // Parcours signature du mode Maison : chambre (lit, tapis, jouets) →
        // couloir → bureau (chaise, bureau, commode, étagère-escabeau, armoire)
        // → couloir → retour au lit. Coordonnées en repère MONDE (après les
        // poses de HouseGeometry), pas locales à une seule pièce.
        CircuitKind.HOUSE_GROUND_FLOOR to listOf(
            top(-23f, 5.4f, -25f), ramp(-23f, 0f, -42f),
            floor(-40f, -67f), floor(-55f, -37f), floor(-73f, -19f), floor(-73f, -12.5f),
            floor(-73f, -6f), floor(-20f, 0f), floor(40f, 4f), floor(73f, 10f), floor(73f, 12.5f),
            floor(73f, 37f), floor(40f, 77f), floor(12f, 117f),
            top(12f, 5.95f, 142f), top(12f, 11.1f, 148f), ramp(12f, 0f, 127f),
            floor(-10f, 127f), floor(-24f, 145f),
            top(-24f, 14.06f, 154f), top(-39f, 14f, 154f), top(-45f, 18f, 154f),
            top(-51f, 22f, 154f), top(-58f, 26.3f, 154f), top(-75f, 19f, 154f),
            floor(-90f, 127f), floor(-40f, 90f), floor(20f, 50f), floor(73f, 20f), floor(73f, 12.5f),
            floor(73f, 6f), floor(0f, 0f), floor(-73f, -6f), floor(-73f, -12.5f), floor(-73f, -19f),
            floor(-40f, -47f), floor(-23f, -32f)
        )
    )

    fun surface(kind: CircuitKind, fraction: Float): CourseSurface {
        val nodes = trails.getValue(kind)
        return nodes[(fraction * nodes.size).toInt().coerceIn(0, nodes.lastIndex)].surface
    }

    fun point(kind: CircuitKind, fraction: Float): Vec3 {
        val nodes = trails.getValue(kind)
        val scaled = fraction * nodes.size
        val index = scaled.toInt().coerceIn(0, nodes.lastIndex)
        val t = scaled - index
        fun p(offset: Int) = nodes[(index + offset + nodes.size) % nodes.size]
        val a = p(-1); val b = p(0); val c = p(1); val d = p(2)
        fun spline(a: Float, b: Float, c: Float, d: Float) = .5f *
            (2*b + (-a+c)*t + (2*a-5*b+4*c-d)*t*t + (-a+3*b-3*c+d)*t*t*t)
        // Rampe droite jusqu'au bord exact du plateau, sans dépassement latéral
        // d'une spline dans le meuble ; l'altitude raccorde les deux surfaces.
        val straight = b.surface != CourseSurface.FLOOR
        val x = if (straight) b.x + (c.x-b.x)*t else spline(a.x,b.x,c.x,d.x)
        val z = if (straight) b.z + (c.z-b.z)*t else spline(a.z,b.z,c.z,d.z)
        val y = b.y + (c.y-b.y) * t*t*(3f-2f*t)
        return Vec3(x, y - PrototypeTrack.ROAD_SURFACE_LIFT, z)
    }

    fun decorations(scene: SceneChoice): List<DecorPlacement> = buildList {
        val z = if (scene.circuit == CircuitKind.FURNITURE_TRAIL) -20f else 16f
        val theme = RoomThemes.theme(scene.room)
        val room = when (scene.room) {
            RoomKind.KITCHEN -> DecorRoom.KITCHEN
            RoomKind.GARAGE -> DecorRoom.GARAGE
            RoomKind.BATHROOM, RoomKind.LAUNDRY -> DecorRoom.BATHROOM
            RoomKind.OFFICE -> DecorRoom.OFFICE
            else -> DecorRoom.LIVING_ROOM
        }
        val builder = DecorBuilder().apply {
            // Grand meuble traversable dessus ET dessous, aucun volume global plein.
            box(0f, 11.5f, 0f, 56f, 1f, 18f, theme.floorColor)
            for (x in floatArrayOf(-24f,24f)) for (depth in floatArrayOf(-7f,7f))
                box(x, 5.5f, depth, 2f, 11f, 2f, theme.accent)
            for (depth in floatArrayOf(-8.5f,8.5f))
                box(0f, 10.9f, depth, 52f, .3f, .7f, theme.wall)
        }
        add(DecorPlacement(DecorModel("course.table.${scene.room.name}", room, builder.parts.toList()), 0f, 0f, z))
        val accessory = when (scene.room) {
            RoomKind.KITCHEN, RoomKind.DINING_ROOM -> "kitchen.kettle"
            RoomKind.BATHROOM, RoomKind.LAUNDRY -> "bathroom.laundry_basket"
            RoomKind.GARAGE -> "garage.toolbox"
            else -> "living.books"
        }
        add(DecorPlacement(DecorCatalog[accessory], -9f, 12f, z + 5.5f, scale = .8f))
        add(DecorPlacement(DecorCatalog[accessory], 10f, 12f, z - 5.5f, scale = .8f))
        // Le mobilier du pourtour reste celui de la pièce ; ces objets donnent
        // des repères et des obstacles à contourner dans la portion libre.
        val obstacle = if (scene.room == RoomKind.GARAGE) "garage.tires" else "bathroom.laundry_basket"
        add(DecorPlacement(DecorCatalog[obstacle], -42f, 0f, -4f))
    }
}
