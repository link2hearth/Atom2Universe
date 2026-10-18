package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.FrontierItems
import com.Atom2Universe.app.games.caves.world.UndergroundSites.Kind
import com.Atom2Universe.app.games.caves.world.UndergroundSites.Plan
import com.Atom2Universe.app.games.caves.world.UndergroundSites.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vérifie les voxels réellement construits, après l'ajout du mobilier. */
class UndergroundSiteLayoutTest {
    private val seeds = listOf(0L, 1L, -1L, 792137L, Long.MIN_VALUE, Long.MAX_VALUE)
    private val entrance = Point(0, 1, -4)

    // Exiger deux blocs d'air évite de considérer un meuble ou de l'eau comme un
    // passage. Le support est un bloc plein de la structure ou un véritable escalier.
    // Les décors traversables ne sont pas nécessaires aux itinéraires.
    private fun walkable(plan: Plan, p: Point, supports: Set<Short>): Boolean =
        plan.blocks[p]?.id == AIR &&
            plan.blocks[p.copy(y = p.y + 1)]?.id == AIR &&
            plan.blocks[p.copy(y = p.y - 1)]?.id in supports

    private fun reachable(plan: Plan, kind: Kind): Map<Point, Int> {
        val palette = UndergroundSiteDressing.palette(kind)
        val supports = setOf(palette.floor, palette.wall, palette.trim, palette.stairs)
        assertTrue("Entrée impraticable", walkable(plan, entrance, supports))
        val distances = mutableMapOf(entrance to 0)
        val queue = ArrayDeque<Point>()
        queue.add(entrance)
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            for (horizontal in listOf(p.copy(x = p.x - 1), p.copy(x = p.x + 1),
                    p.copy(z = p.z - 1), p.copy(z = p.z + 1))) {
                for (dy in -1..1) {
                    val next = horizontal.copy(y = p.y + dy)
                    // Vérifier aussi le déplacement sous le plafond, dans les deux sens.
                    val headY = maxOf(p.y, next.y) + 1
                    val clearHead = plan.blocks[p.copy(y = headY)]?.id == AIR &&
                        plan.blocks[next.copy(y = headY)]?.id == AIR
                    if (next !in distances && clearHead && walkable(plan, next, supports)) {
                        distances[next] = distances.getValue(p) + 1
                        queue.add(next)
                    }
                }
            }
        }
        return distances
    }

    @Test
    fun `toutes les rencontres et les faces des deux coffres restent accessibles a pied`() {
        assertEquals(12, Kind.entries.size)
        for (kind in Kind.entries) for (seed in seeds)
            for (stage in listOf(0, MineralProgression.LAST_STAGE)) {
                val plan = UndergroundSites.blueprint(kind, seed, stage)
                val context = "$kind graine=$seed étage=$stage"
                val paths = reachable(plan, kind)
                assertEquals("$context : rencontres dupliquées", plan.spawnPoints.size,
                    plan.spawnPoints.toSet().size)
                assertTrue("$context : nombre de rencontres", plan.spawnPoints.size in 14..17)
                assertEquals("$context : rencontres du niveau supérieur", 8,
                    plan.spawnPoints.count { it.y == 1 })
                assertTrue("$context : rencontres du sous-sol",
                    plan.spawnPoints.count { it.y == -9 } in 6..9)
                for (spawn in plan.spawnPoints + plan.boss) {
                    assertTrue("$context : rencontre inaccessible $spawn", spawn in paths)
                }
                val caches = plan.blocks.filterValues { it.id == FrontierItems.CACHE }
                assertEquals("$context : coffres manquants", 2, caches.size)
                for ((p, cell) in caches) {
                    val approach = when (cell.meta.toInt() and 3) {
                        0 -> p.copy(z = p.z - 1)
                        1 -> p.copy(z = p.z + 1)
                        2 -> p.copy(x = p.x + 1)
                        else -> p.copy(x = p.x - 1)
                    }
                    assertTrue("$context : face du coffre inaccessible $p", approach in paths)
                }
                // L'exploration impose un détour : aucun couloir droit entrée/boss.
                assertTrue("$context : boss au bout d'un couloir droit",
                    paths.getValue(plan.boss) > plan.boss.z - entrance.z)
            }
    }

    @Test
    fun `le plan reste dans sa cellule pour les quatre orientations et les ancres extremes`() {
        for (kind in Kind.entries) for (seed in seeds) {
            val plan = UndergroundSites.blueprint(kind, seed)
            for (turn in 0..3) for (p in plan.blocks.keys) {
                val rotated = rotate(p.x, p.z, turn)
                // Le placement tire x/z entre 48 et 79, et y entre 16 et 80.
                assertTrue("$kind $p rotation=$turn hors cellule horizontale",
                    rotated.first + 48 >= 0 && rotated.first + 79 < 128 &&
                        rotated.second + 48 >= 0 && rotated.second + 79 < 128)
                assertTrue("$kind $p hors cellule verticale", p.y + 16 >= 0 && p.y + 80 < 96)
                assertTrue("$kind $p hors hauteur du terrain d'entraînement", p.y + 16 in 0 until 32)
            }
        }
    }

    @Test
    fun `une graine reconstruit exactement les memes blocs et rencontres`() {
        for (kind in Kind.entries) for (seed in seeds) {
            assertEquals("$kind graine=$seed", UndergroundSites.blueprint(kind, seed),
                UndergroundSites.blueprint(kind, seed))
        }
    }

    @Test
    fun `les graines changent les connexions du labyrinthe et pas seulement le mobilier`() {
        for (kind in Kind.entries) {
            val routes = (0L..15L).map { seed ->
                val blocks = UndergroundSites.blueprint(kind, seed).blocks
                buildList {
                    // Milieu des douze connexions possibles entre les neuf salles.
                    for (z in listOf(5, 17, 29)) for (x in listOf(-8, 8))
                        add(blocks[Point(x, 1, z)]?.id == AIR)
                    for (z in listOf(11, 23)) for (x in listOf(-16, 0, 16))
                        add(blocks[Point(x, 1, z)]?.id == AIR)
                }
            }.toSet()
            assertTrue("$kind : connexions identiques pour toutes les graines", routes.size > 1)
        }
    }

    @Test
    fun `les douze escaliers tournent dans le monde en conservant leur demi hauteur`() {
        val riseDirections = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)
        for (id in 2400..2411) for (meta in 0..15) for (turn in 0..3) {
            val actual = UndergroundSites.rotatedMeta(id.toShort(), meta.toByte(), turn).toInt()
            val initial = riseDirections[meta and 3]
            val expected = rotate(initial.first, initial.second, turn)
            assertEquals("Escalier $id meta=$meta rotation=$turn", expected,
                riseDirections[actual and 3])
            assertEquals("Demi-hauteur perdue pour $id", meta and 12, actual and 12)
        }
    }

    private fun rotate(x: Int, z: Int, turn: Int): Pair<Int, Int> = when (turn) {
        1 -> -z to x
        2 -> -x to -z
        3 -> z to -x
        else -> x to z
    }
}
