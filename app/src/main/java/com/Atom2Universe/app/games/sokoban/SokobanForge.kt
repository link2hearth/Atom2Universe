package com.Atom2Universe.app.games.sokoban

import kotlin.math.exp
import kotlin.random.Random

/**
 * Ce que la fabrique doit produire : la pièce (intérieur, sans le mur d'enceinte), le nombre de
 * caisses, la quantité de sol permise et le budget d'exploration d'un plateau.
 */
internal data class SokobanForgeSpec(
    val interiorW: Int,
    val interiorH: Int,
    val boxes: Int,
    val minFloor: Int,
    val maxFloor: Int,
    /** Au-delà, le plateau est jugé trop ouvert : il est écarté sans être exploré jusqu'au bout. */
    val stateCap: Int,
    /** Nombre de départs lointains comparés pour un plateau. */
    val samples: Int = 8,
    val memoLimit: Int = 200_000
)

/** Un plateau : la grille complète (mur d'enceinte compris), le sol et les quais. */
internal class SokobanBoard(val width: Int, val height: Int, val floor: BooleanArray, val goals: IntArray) {
    val floorCount get() = floor.count { it }
    fun copy() = SokobanBoard(width, height, floor.copyOf(), goals.copyOf())
}

/** Un plateau évalué : son exploration, le départ choisi et la solution qui en est lue. */
internal class SokobanForged(
    val board: SokobanBoard,
    val exploration: SokobanExploration,
    val start: Int,
    val line: SokobanLine,
    val score: Double
)

/**
 * La fabrique : un plateau tiré au hasard, puis des milliers de petites retouches (un mur posé
 * ou ôté, un quai déplacé) gardées quand elles rendent le meilleur départ plus riche — plus de
 * poussées, mais surtout plus d'allers-retours entre les caisses et de changements de direction.
 * Chaque plateau est exploré en entier : la solution annoncée est la vraie solution optimale.
 */
internal object SokobanForge {

    /**
     * La richesse d'un départ. Les poussées comptent peu (une longue ligne droite est facile) ;
     * les changements de caisse et de direction font la difficulté ; une caisse jamais déplacée
     * n'est qu'un mur déguisé ; le sol inutile est découragé.
     */
    fun score(line: SokobanLine, floor: Int, boxes: Int): Double {
        val unmoved = boxes - line.movedBoxes
        return 0.5 * line.pushes + line.lines + 2.0 * line.changes - 0.15 * floor - 25.0 * unmoved
    }

    fun evaluate(board: SokobanBoard, spec: SokobanForgeSpec, rnd: Random, cancel: () -> Unit = {}): SokobanForged? {
        val space = SokobanSpace(board.width, board.height, board.floor, board.goals)
        val ex = space.explore(spec.stateCap, cancel) ?: return null
        val floor = space.floorCount
        val picks = ArrayList<Int>()
        // Surtout les départs les plus lointains, quelques-uns juste avant.
        var d = ex.maxDist
        while (d >= 1 && picks.size < spec.samples) {
            val layer = ex.layer(d)
            val want = if (d == ex.maxDist) (spec.samples + 1) / 2 else (spec.samples + 3) / 4
            val size = layer.last - layer.first + 1
            if (size <= want) for (i in layer) picks.add(i)
            else repeat(want) { picks.add(layer.first + rnd.nextInt(size)) }
            if (ex.maxDist - d >= 2) break
            d--
        }
        var best: SokobanForged? = null
        for (i in picks.distinct()) {
            cancel()
            val line = ex.solve(i, spec.memoLimit) ?: continue
            val s = score(line, floor, board.goals.size)
            if (best == null || s > best.score) best = SokobanForged(board, ex, i, line, s)
        }
        return best
    }

    // ── Plateaux ──────────────────────────────────────────────────────────────

    /** Une pièce creusée par une marche au hasard : couloirs et petites salles mêlés. */
    fun randomBoard(spec: SokobanForgeSpec, rnd: Random): SokobanBoard {
        val w = spec.interiorW + 2
        val h = spec.interiorH + 2
        val floor = BooleanArray(w * h)
        val target = spec.minFloor + rnd.nextInt(spec.maxFloor - spec.minFloor + 1)
        var x = 1 + rnd.nextInt(spec.interiorW)
        var y = 1 + rnd.nextInt(spec.interiorH)
        var dir = SokobanDir.entries.random(rnd)
        var count = 0
        fun carve(cx: Int, cy: Int) {
            if (cx < 1 || cy < 1 || cx > spec.interiorW || cy > spec.interiorH) return
            val i = cy * w + cx
            if (!floor[i] && count < target) { floor[i] = true; count++ }
        }
        var guard = 0
        while (count < target && guard++ < 10_000) {
            carve(x, y)
            if (rnd.nextInt(100) < 22) { carve(x + 1, y); carve(x, y + 1); carve(x + 1, y + 1) }
            if (rnd.nextInt(100) < 35) dir = SokobanDir.entries.random(rnd)
            val nx = x + dir.dx
            val ny = y + dir.dy
            if (nx < 1 || ny < 1 || nx > spec.interiorW || ny > spec.interiorH) {
                dir = SokobanDir.entries.random(rnd)
            } else {
                x = nx; y = ny
            }
        }
        val cells = floor.indices.filter { floor[it] }.shuffled(rnd)
        val goals = cells.take(spec.boxes).sorted().toIntArray()
        return SokobanBoard(w, h, floor, goals)
    }

    private fun connected(board: SokobanBoard): Boolean {
        val w = board.width
        val start = board.floor.indexOfFirst { it }
        if (start < 0) return false
        val seen = BooleanArray(board.floor.size)
        val stack = IntArray(board.floor.size)
        var top = 0
        stack[top++] = start; seen[start] = true
        var reached = 1
        while (top > 0) {
            val c = stack[--top]
            for (n in intArrayOf(c - 1, c + 1, c - w, c + w)) {
                if (n < 0 || n >= board.floor.size || seen[n] || !board.floor[n]) continue
                seen[n] = true; reached++; stack[top++] = n
            }
        }
        return reached == board.floorCount
    }

    private fun interior(board: SokobanBoard, i: Int): Boolean {
        val x = i % board.width
        val y = i / board.width
        return x >= 1 && y >= 1 && x < board.width - 1 && y < board.height - 1
    }

    /** Une à trois retouches ; null si le plateau obtenu sort des règles. */
    fun mutate(board: SokobanBoard, spec: SokobanForgeSpec, rnd: Random): SokobanBoard? {
        val b = board.copy()
        val w = b.width
        val steps = 1 + (if (rnd.nextInt(3) == 0) 1 else 0) + (if (rnd.nextInt(8) == 0) 1 else 0)
        repeat(steps) {
            val roll = rnd.nextInt(100)
            when {
                roll < 38 -> { // un mur de plus
                    val cells = b.floor.indices.filter { b.floor[it] && it !in b.goals }
                    if (cells.isEmpty()) return null
                    b.floor[cells.random(rnd)] = false
                }
                roll < 72 -> { // un mur de moins, contre le sol existant
                    val cells = b.floor.indices.filter { i ->
                        !b.floor[i] && interior(b, i) &&
                            (b.floor[i - 1] || b.floor[i + 1] || b.floor[i - w] || b.floor[i + w])
                    }
                    if (cells.isEmpty()) return null
                    b.floor[cells.random(rnd)] = true
                }
                else -> { // un quai déplacé, souvent d'un pas
                    val k = rnd.nextInt(b.goals.size)
                    val g = b.goals[k]
                    val near = intArrayOf(g - 1, g + 1, g - w, g + w)
                        .filter { it >= 0 && it < b.floor.size && b.floor[it] && it !in b.goals }
                    val pool = if (near.isNotEmpty() && rnd.nextInt(3) > 0) near
                    else b.floor.indices.filter { b.floor[it] && it !in b.goals }
                    if (pool.isEmpty()) return null
                    b.goals[k] = pool.random(rnd)
                }
            }
        }
        b.goals.sort()
        val floor = b.floorCount
        if (floor < spec.minFloor || floor > spec.maxFloor || floor > 64) return null
        if (b.goals.any { !b.floor[it] }) return null
        if (!connected(b)) return null
        return b
    }

    /** Premier plateau valable : quelques tirages au hasard. */
    fun seed(spec: SokobanForgeSpec, rnd: Random, deadline: Long, cancel: () -> Unit = {}): SokobanForged? {
        repeat(60) {
            if (System.nanoTime() > deadline) return null
            cancel()
            val e = evaluate(randomBoard(spec, rnd), spec, rnd, cancel)
            if (e != null) return e
        }
        return null
    }

    /**
     * Recuit : une retouche moins bonne passe parfois au début (pour sortir d'une impasse), de
     * moins en moins ensuite. Renvoie le meilleur plateau vu.
     */
    fun climb(
        from: SokobanForged,
        spec: SokobanForgeSpec,
        rnd: Random,
        iterations: Int,
        deadline: Long,
        cancel: () -> Unit = {},
        heat: Double = 2.5
    ): SokobanForged {
        var cur = from
        var best = from
        val t0 = System.nanoTime()
        val span = (deadline - t0).coerceAtLeast(1L).toDouble()
        for (it in 0 until iterations) {
            val now = System.nanoTime()
            if (now > deadline) break
            cancel()
            val board = mutate(cur.board, spec, rnd) ?: continue
            val e = evaluate(board, spec, rnd, cancel) ?: continue
            // Le refroidissement suit le premier épuisé des deux budgets : essais ou temps.
            val progress = maxOf(it.toDouble() / iterations, (now - t0) / span).coerceAtMost(1.0)
            val t = heat * (1.0 - progress) + 0.05
            if (e.score >= cur.score || rnd.nextDouble() < exp((e.score - cur.score) / t)) cur = e
            if (e.score > best.score) best = e
        }
        return best
    }

    /** Finition : mure une à une les cases de sol dont on peut se passer. */
    fun polish(
        level: SokobanForged,
        spec: SokobanForgeSpec,
        rnd: Random,
        deadline: Long,
        cancel: () -> Unit = {}
    ): SokobanForged {
        var cur = level
        val order = cur.board.floor.indices.filter { cur.board.floor[it] && it !in cur.board.goals }.shuffled(rnd)
        for (cell in order) {
            if (System.nanoTime() > deadline) break
            cancel()
            if (!cur.board.floor[cell]) continue
            val b = cur.board.copy()
            b.floor[cell] = false
            if (b.floorCount < spec.minFloor || !connected(b)) continue
            val e = evaluate(b, spec, rnd, cancel) ?: continue
            if (e.score >= cur.score) cur = e
        }
        return cur
    }

    // ── Vers le jeu ───────────────────────────────────────────────────────────

    /**
     * Le niveau prêt à jouer : grille rognée autour du sol (un mur d'enceinte), robot posé dans
     * sa zone de départ, et la solution complète en LURD (minuscule = pas, majuscule = poussée).
     */
    fun toPuzzle(level: SokobanForged, rnd: Random): SokobanPuzzle {
        val board = level.board
        val ex = level.exploration
        val space = ex.space
        val w = board.width
        val startMask = ex.mask(level.start)
        val region = space.region(ex.rep(level.start), startMask)
        val regionCells = ArrayList<Int>()
        var r = region
        while (r != 0L) { regionCells.add(space.cellOf[r.countTrailingZeroBits()]); r = r and (r - 1) }
        val player = regionCells[rnd.nextInt(regionCells.size)]
        val boxes = ArrayList<Int>()
        var m = startMask
        while (m != 0L) { boxes.add(space.cellOf[m.countTrailingZeroBits()]); m = m and (m - 1) }

        val solution = lurd(board, boxes, player, level.line, space)

        // Rognage : le sol et son mur d'enceinte.
        var minX = w; var minY = board.height; var maxX = 0; var maxY = 0
        for (i in board.floor.indices) if (board.floor[i]) {
            val x = i % w; val y = i / w
            minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
        }
        val nw = maxX - minX + 3
        val nh = maxY - minY + 3
        fun map(i: Int) = (i / w - minY + 1) * nw + (i % w - minX + 1)
        val walls = HashSet<Int>()
        for (y in 0 until nh) for (x in 0 until nw) {
            val ox = x + minX - 1; val oy = y + minY - 1
            val inside = ox in 0 until w && oy in 0 until board.height && board.floor[oy * w + ox]
            if (!inside) walls.add(y * nw + x)
        }
        return SokobanPuzzle(
            width = nw,
            height = nh,
            walls = walls,
            goals = board.goals.map(::map).toSet(),
            boxes = boxes.map(::map),
            player = map(player),
            optimalPushes = level.line.pushes,
            solution = solution
        )
    }

    private val LETTERS = charArrayOf('u', 'd', 'l', 'r')

    /** Les pas du robot entre deux poussées : plus court chemin entre les caisses. */
    private fun lurd(board: SokobanBoard, startBoxes: List<Int>, startPlayer: Int, line: SokobanLine, space: SokobanSpace): String {
        val w = board.width
        val n = board.floor.size
        val boxes = BooleanArray(n)
        for (b in startBoxes) boxes[b] = true
        var player = startPlayer
        val out = StringBuilder()
        val prev = IntArray(n)
        val prevDir = IntArray(n)
        val queue = IntArray(n)
        val steps = intArrayOf(-w, w, -1, 1)
        for (k in 0 until line.pushes) {
            val box = space.cellOf[line.boxes[k]]
            val dir = line.dirs[k]
            val stand = box - steps[dir]
            // Plus court chemin du robot jusqu'à la case derrière la caisse.
            prev.fill(-2)
            var head = 0; var tail = 0
            queue[tail++] = player; prev[player] = -1
            while (head < tail && prev[stand] == -2) {
                val c = queue[head++]
                for (d in 0 until 4) {
                    val nc = c + steps[d]
                    if (nc < 0 || nc >= n || !board.floor[nc] || boxes[nc] || prev[nc] != -2) continue
                    prev[nc] = c; prevDir[nc] = d; queue[tail++] = nc
                }
            }
            check(prev[stand] != -2) { "chemin introuvable" }
            val walk = StringBuilder()
            var c = stand
            while (c != player) { walk.append(LETTERS[prevDir[c]]); c = prev[c] }
            out.append(walk.reverse())
            out.append(LETTERS[dir].uppercaseChar())
            boxes[box] = false
            boxes[box + steps[dir]] = true
            player = box
        }
        return out.toString()
    }
}
