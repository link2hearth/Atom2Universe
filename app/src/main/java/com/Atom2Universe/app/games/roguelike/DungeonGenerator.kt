package com.Atom2Universe.app.games.roguelike

import kotlin.math.roundToInt
import kotlin.random.Random

data class Room(val x: Int, val y: Int, val w: Int, val h: Int) {
    fun center() = Pos(x + w / 2, y + h / 2)
    fun contains(p: Pos) = p.x in x until x + w && p.y in y until y + h
    fun randomInner(rng: Random) = Pos(x + rng.nextInt(w), y + rng.nextInt(h))
}

/** Le plan d'un étage, avant qu'on y mette monstres et objets. */
class DungeonLayout(
    val tiles: Array<Array<TileType>>,
    val rooms: List<Room>,
    val start: Pos,
    val stairs: Pos,
    /** Bouts de couloir sans issue : de bonnes cachettes pour l'or. */
    val deadEnds: List<Pos>,
)

/**
 * Génération « salles et labyrinthe » :
 *  1. on pose de petites salles sur une grille impaire, sans chevauchement ;
 *  2. un labyrinthe remplit tout l'espace restant (chaque case impaire) ;
 *  3. on perce des portes jusqu'à ce que tout soit relié, plus quelques portes en trop
 *     pour créer des boucles (utiles pour semer un poursuivant) ;
 *  4. on raccourcit les culs-de-sac sans tous les supprimer : c'est ce qui garde
 *     l'aspect labyrinthe.
 * Chaque étage tire son propre style, plus « salles » ou plus « couloirs ».
 *
 * Les dimensions doivent être impaires.
 */
object DungeonGenerator {

    private val DIRS = listOf(Pos(1, 0), Pos(-1, 0), Pos(0, 1), Pos(0, -1))

    fun generate(w: Int, h: Int, rng: Random): DungeonLayout {
        require(w % 2 == 1 && h % 2 == 1) { "dimensions impaires attendues" }
        while (true) {
            tryGenerate(w, h, rng)?.let { return it }
        }
    }

    private fun tryGenerate(w: Int, h: Int, rng: Random): DungeonLayout? {
        val tiles  = Array(h) { Array(w) { TileType.WALL } }
        val region = Array(h) { IntArray(w) { -1 } }
        var regionCount = 0

        // Style de l'étage
        // Peu d'essais → labyrinthe, beaucoup → salles ; proportionnel à la surface de la carte
        val roomAttempts   = (rng.nextInt(6, 60) * (w * h) / 1107f).roundToInt().coerceAtLeast(8)
        val windiness      = 0.25f + rng.nextFloat() * 0.5f
        val loopChance     = 0.04f + rng.nextFloat() * 0.08f
        val deadEndPasses  = rng.nextInt(1, 8)

        fun carve(x: Int, y: Int, r: Int) { tiles[y][x] = TileType.FLOOR; region[y][x] = r }

        // 1. Salles : 3, 5 ou 7 de large, 3 ou 5 de haut
        val rooms = mutableListOf<Room>()
        repeat(roomAttempts) {
            val rw = 3 + 2 * rng.nextInt(3)
            val rh = 3 + 2 * rng.nextInt(2)
            val x = 1 + 2 * rng.nextInt((w - rw - 1) / 2 + 1)
            val y = 1 + 2 * rng.nextInt((h - rh - 1) / 2 + 1)
            if (x + rw > w - 1 || y + rh > h - 1) return@repeat
            val room = Room(x, y, rw, rh)
            val clash = rooms.any { o -> x - 1 < o.x + o.w && x + rw + 1 > o.x && y - 1 < o.y + o.h && y + rh + 1 > o.y }
            if (clash) return@repeat
            rooms += room
            val r = regionCount++
            for (cy in y until y + rh) for (cx in x until x + rw) carve(cx, cy, r)
        }
        if (rooms.size < 3) return null

        // 2. Labyrinthe dans tout ce qui reste
        for (y in 1 until h step 2) for (x in 1 until w step 2) {
            if (tiles[y][x] != TileType.WALL) continue
            val r = regionCount++
            carve(x, y, r)
            val stack = ArrayDeque<Pos>().apply { add(Pos(x, y)) }
            var lastDir: Pos? = null
            while (stack.isNotEmpty()) {
                val cell = stack.last()
                val open = DIRS.filter { d ->
                    val nx = cell.x + d.x * 2; val ny = cell.y + d.y * 2
                    nx in 1 until w - 1 && ny in 1 until h - 1 && tiles[ny][nx] == TileType.WALL
                }
                if (open.isEmpty()) { stack.removeLast(); lastDir = null; continue }
                val dir = if (lastDir in open && rng.nextFloat() > windiness) lastDir!! else open.random(rng)
                carve(cell.x + dir.x, cell.y + dir.y, r)
                carve(cell.x + dir.x * 2, cell.y + dir.y * 2, r)
                stack.add(Pos(cell.x + dir.x * 2, cell.y + dir.y * 2))
                lastDir = dir
            }
        }

        // 3. Portes : relier toutes les régions (union-find), plus quelques boucles
        val parent = IntArray(regionCount) { it }
        fun find(a: Int): Int { var x = a; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }

        val connectors = mutableListOf<Pair<Pos, Set<Int>>>()
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            if (tiles[y][x] != TileType.WALL) continue
            val regs = DIRS.mapNotNull { d -> region[y + d.y][x + d.x].takeIf { it >= 0 } }.toSet()
            if (regs.size >= 2) connectors += Pos(x, y) to regs
        }
        val opened = HashSet<Pos>()
        for ((pos, regs) in connectors.shuffled(rng)) {
            val roots = regs.map { find(it) }.toSet()
            val nextToDoor = DIRS.any { d -> Pos(pos.x + d.x, pos.y + d.y) in opened }
            val joins = roots.size >= 2
            if (joins || (!nextToDoor && rng.nextFloat() < loopChance)) {
                tiles[pos.y][pos.x] = TileType.FLOOR
                opened += pos
                val first = roots.first()
                for (other in roots) parent[find(other)] = find(first)
            }
        }

        // 4. Raccourcir les culs-de-sac, quelques passes seulement
        fun inRoom(p: Pos) = rooms.any { it.contains(p) }
        fun floorNeighbours(x: Int, y: Int) = DIRS.count { d -> tiles[y + d.y][x + d.x] != TileType.WALL }
        repeat(deadEndPasses) {
            val ends = mutableListOf<Pos>()
            for (y in 1 until h - 1) for (x in 1 until w - 1)
                if (tiles[y][x] == TileType.FLOOR && floorNeighbours(x, y) <= 1 && !inRoom(Pos(x, y))) ends += Pos(x, y)
            if (ends.isEmpty()) return@repeat
            for (p in ends) tiles[p.y][p.x] = TileType.WALL
        }
        val deadEnds = mutableListOf<Pos>()
        for (y in 1 until h - 1) for (x in 1 until w - 1)
            if (tiles[y][x] == TileType.FLOOR && floorNeighbours(x, y) == 1 && !inRoom(Pos(x, y))) deadEnds += Pos(x, y)

        // Départ dans une salle, escalier dans la salle la plus lointaine (en pas réels)
        val startRoom = rooms.random(rng)
        val start = startRoom.center()
        val dist = distances(tiles, start)
        val stairsRoom = rooms.filter { it != startRoom }.maxByOrNull { dist[it.center().y][it.center().x] } ?: return null
        val stairs = stairsRoom.randomInner(rng)
        if (dist[stairs.y][stairs.x] < 0) return null
        tiles[stairs.y][stairs.x] = TileType.STAIRS_DOWN

        return DungeonLayout(tiles, rooms, start, stairs, deadEnds)
    }

    /** Distance en pas (4 directions) depuis [from] ; −1 si inatteignable. */
    fun distances(tiles: Array<Array<TileType>>, from: Pos): Array<IntArray> {
        val h = tiles.size; val w = tiles[0].size
        val d = Array(h) { IntArray(w) { -1 } }
        val q = ArrayDeque<Pos>().apply { add(from) }
        d[from.y][from.x] = 0
        while (q.isNotEmpty()) {
            val c = q.removeFirst()
            for (dir in DIRS) {
                val nx = c.x + dir.x; val ny = c.y + dir.y
                if (nx !in 0 until w || ny !in 0 until h || tiles[ny][nx] == TileType.WALL || d[ny][nx] >= 0) continue
                d[ny][nx] = d[c.y][c.x] + 1
                q.add(Pos(nx, ny))
            }
        }
        return d
    }
}
