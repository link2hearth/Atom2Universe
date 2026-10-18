package com.Atom2Universe.app.games.toyboxracers

import com.Atom2Universe.app.games.toyboxracers.track.CircuitKind
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import com.Atom2Universe.app.games.toyboxracers.track.RoomBox
import com.Atom2Universe.app.games.toyboxracers.track.RoomKind
import com.Atom2Universe.app.games.toyboxracers.track.SceneChoice
import com.Atom2Universe.app.games.toyboxracers.track.SculptedCircuits
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Garde-fous des circuits classiques : ce que l'œil rate sur un plan et que le joueur paie
 * en course. Chaque circuit sculpté est vérifié dans les pièces, car le même tracé y
 * côtoie des meubles différents.
 */
class ToyboxCircuitGeometryTest {
    private val sculpted = CircuitKind.entries.filter { it.usesSculptedLayout }

    /** Le bord d'une dalle qui recule replie la route sur elle-même : c'était le bug des épingles. */
    @Test
    fun noDeckEdgeFoldsBackInATightTurn() {
        for (kind in CircuitKind.entries) {
            val track = PrototypeTrack(scene = SceneChoice(RoomKind.BEDROOM, kind))
            val samples = track.allSamples()
            for (i in samples.indices) {
                val a = samples[i]
                val b = samples[(i + 1) % samples.size]
                val end = if (i == samples.lastIndex) track.length else b.distance
                if (!track.hasDeck(track.sampleAt((a.distance + end) * .5f))) continue
                val tx = b.position.x - a.position.x
                val tz = b.position.z - a.position.z
                for (side in floatArrayOf(-1f, 1f)) {
                    val ha = a.roadWidth * .5f + PrototypeTrack.CURB_WIDTH
                    val hb = b.roadWidth * .5f + PrototypeTrack.CURB_WIDTH
                    val ex = (b.position.x + b.right.x * hb * side) - (a.position.x + a.right.x * ha * side)
                    val ez = (b.position.z + b.right.z * hb * side) - (a.position.z + a.right.z * ha * side)
                    assertTrue("$kind : le bord de la route se replie à ${(a.fraction * 100).toInt()} %",
                        ex * tx + ez * tz > 0f)
                }
            }
        }
    }

    @Test
    fun sculptedSlopesStayDrivable() {
        for (kind in sculpted) {
            val track = PrototypeTrack(scene = SceneChoice(RoomKind.BEDROOM, kind))
            for (s in track.allSamples()) {
                if (!track.hasDeck(s)) continue
                val slope = abs(s.tangent.y) / hypot(s.tangent.x, s.tangent.z)
                assertTrue("$kind : pente de ${(slope * 100).toInt()} % à ${(s.fraction * 100).toInt()} %", slope < .5f)
            }
        }
    }

    /** Aucun meuble, décor, pilier ou jouet ne doit se trouver sur la route, dans aucune pièce. */
    @Test
    fun roadsStayClearOfEveryRoomFurniture() {
        val rubans = CircuitKind.entries.filter { !it.usesHouseLayout && !it.usesFurnitureLayout }
        for (kind in rubans) for (room in RoomKind.entries) {
            val track = PrototypeTrack(scene = SceneChoice(room, kind))
            val solids = track.furnitureSolids.filter { it.top > .3f }
            for (point in roadPoints(track)) {
                val (x, z, top) = point
                assertTrue("$kind dans $room : la route sort de la pièce en $x, $z",
                    abs(x) < PrototypeTrack.ROOM_HALF_WIDTH - 1f && abs(z) < PrototypeTrack.ROOM_HALF_DEPTH - 1f)
                for (box in solids) {
                    if (x < box.left - .4f || x > box.right + .4f || z < box.back - .4f || z > box.front + .4f) continue
                    // Sous la dalle (pilier) ou au-dessus de la voiture (plateau de table) : rien à dire.
                    if (box.top <= top - PrototypeTrack.ROAD_THICKNESS + .05f || box.bottom >= top + 1.6f) continue
                    throw AssertionError("$kind dans $room : un meuble ${describe(box)} est sur la route en " +
                        "${"%.1f".format(x)}, ${"%.1f".format(z)}")
                }
                for (toy in track.toyObstacles) {
                    assertTrue("$kind dans $room : le jouet ${toy.kind} est sur la route",
                        hypot(x - toy.x, z - toy.z) > toy.radius + .8f || top > toy.height + 1f)
                }
            }
        }
    }

    /**
     * Un décor ou un pilier du circuit ne doit traverser ni un meuble de la pièce, ni l'îlot,
     * ni un jouet, ni un autre décor ou pilier du circuit.
     */
    @Test
    fun sculptedPropsAndPillarsNeverOverlapAnything() {
        val problems = LinkedHashSet<String>()
        for (kind in sculpted) for (room in RoomKind.entries.filterNot { it.isDestination }) {
            val scene = SceneChoice(room, kind)
            val track = PrototypeTrack(sampleCount = 16, scene = scene)
            // Chaque objet du circuit est un groupe : ses propres pièces peuvent se toucher.
            val groups = SculptedCircuits.decorations(kind).map { "${it.model.id} en ${it.x}, ${it.z}" to it.solids } +
                SculptedCircuits.pillars(kind).chunked(2).map { "pilier en ${it[0].x}, ${it[0].z}" to it }
            val others = track.furnitureSolids.toMutableList()
            for ((_, solids) in groups) for (solid in solids) others.remove(solid)
            for ((index, group) in groups.withIndex()) for (solid in group.second) {
                if (solid.top < .3f) continue
                for (other in others) {
                    if (other.top < .3f) continue
                    if (overlaps(solid, other)) problems += "$kind dans $room : ${group.first} traverse ${describe(other)}"
                }
                for (otherGroup in groups.drop(index + 1)) for (other in otherGroup.second) {
                    if (overlaps(solid, other)) problems += "$kind : ${group.first} traverse ${otherGroup.first}"
                }
                for (toy in track.toyObstacles) {
                    val dx = (toy.x).coerceIn(solid.left, solid.right) - toy.x
                    val dz = (toy.z).coerceIn(solid.back, solid.front) - toy.z
                    if (hypot(dx, dz) < toy.radius) problems += "$kind : ${group.first} touche le jouet ${toy.kind}"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Les destinations remplacent les accessoires du tracé par leur propre décor réel. */
    @Test
    fun destinationPropsAndPillarsNeverOverlapTheirSurroundings() {
        for (room in RoomKind.entries.filter { it.isDestination }) {
            val track = PrototypeTrack(scene = SceneChoice(room, room.defaultCircuit))
            assertTrue("$room doit garder ses grands objets caractéristiques", track.decorations.size >= 3)
            val groups = track.decorations.map { it.model.id to it.solids } +
                SculptedCircuits.pillars(room.defaultCircuit).chunked(2).map { "pilier" to it }
            val architecture = track.furnitureSolids.toMutableList()
            for ((_, solids) in groups) for (solid in solids) architecture.remove(solid)
            for ((index, group) in groups.withIndex()) for (solid in group.second) {
                for (other in architecture) {
                    assertFalse("$room : ${group.first} traverse la façade ${describe(other)}",
                        overlaps(solid, other))
                }
                for (otherGroup in groups.drop(index + 1)) for (other in otherGroup.second) {
                    assertFalse("$room : ${group.first} traverse ${otherGroup.first}", overlaps(solid, other))
                }
            }
        }
    }

    /** Là où la route repasse sur elle-même, la voiture du dessous doit passer sous la dalle. */
    @Test
    fun bridgesLeaveHeadroom() {
        for (kind in sculpted) {
            val track = PrototypeTrack(scene = SceneChoice(RoomKind.BEDROOM, kind))
            val deck = track.allSamples().filter { track.hasDeck(it) }
            for (a in deck) for (b in deck) {
                val along = abs(a.distance - b.distance).let { minOf(it, track.length - it) }
                if (along < 30f || b.position.y <= a.position.y) continue
                val apart = hypot(a.position.x - b.position.x, a.position.z - b.position.z)
                if (apart > (a.roadWidth + b.roadWidth) * .5f + .6f) continue
                assertTrue("$kind : seulement ${"%.1f".format(b.position.y - a.position.y)} m sous le pont " +
                    "à ${(a.fraction * 100).toInt()} % / ${(b.fraction * 100).toInt()} %",
                    b.position.y - a.position.y > 2.6f)
            }
        }
    }

    @Test
    fun autopilotCompletesALapOnEveryClassicCircuit() {
        val scenes = CircuitKind.entries.map { SceneChoice(RoomKind.BEDROOM, it) } +
            RoomKind.entries.filter { it.isDestination }.map { SceneChoice(it, it.defaultCircuit) }
        for (scene in scenes) {
            val track = PrototypeTrack(scene = scene)
            val ride = ToyboxPilote.drive(track, laps = 1)
            assertFalse("$scene : ${ride.summary()}", ride.stuck)
            assertTrue("$scene : ${ride.summary()}", ride.lapSeconds.isNotEmpty())
        }
    }

    private fun roadPoints(track: PrototypeTrack): List<Triple<Float, Float, Float>> = buildList {
        var d = 0f
        while (d < track.length) {
            val s = track.sampleAt(d)
            if (track.hasDeck(s)) {
                val half = s.roadWidth * .5f + PrototypeTrack.CURB_WIDTH
                var lateral = -half
                while (lateral <= half + .01f) {
                    add(Triple(s.position.x + s.right.x * lateral, s.position.z + s.right.z * lateral,
                        s.position.y + PrototypeTrack.ROAD_SURFACE_LIFT))
                    lateral += half / 4f
                }
            }
            d += 1f
        }
    }

    private fun overlaps(a: RoomBox, b: RoomBox) =
        a.left < b.right && a.right > b.left && a.back < b.front && a.front > b.back &&
            a.bottom < b.top && a.top > b.bottom

    private fun describe(box: RoomBox) = "(${"%.1f".format(box.x)}, ${"%.1f".format(box.y)}, ${"%.1f".format(box.z)} " +
        "${"%.1f".format(box.width)}×${"%.1f".format(box.height)}×${"%.1f".format(box.depth)})"
}
