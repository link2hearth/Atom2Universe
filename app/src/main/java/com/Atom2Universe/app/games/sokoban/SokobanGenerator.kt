package com.Atom2Universe.app.games.sokoban

import kotlin.random.Random

/**
 * Générateur de niveaux Sokoban par **génération inverse** (reverse / pull).
 *
 * Principe : on part de l'état résolu (les caisses posées sur les cibles) et on
 * "tire" les caisses à reculons. Tout état atteint par une suite de tirages est,
 * par symétrie, résoluble par les poussées inverses — la solvabilité est donc
 * garantie par construction.
 *
 * Le même parcours en largeur (BFS) sur le graphe inverse joue le rôle de
 * solveur : la distance (en poussées) entre l'état résolu et un état donné EST
 * la longueur de la solution optimale de cet état. On ne *vise* donc pas un
 * chiffre fixe (coûteux à atteindre en profondeur), on prend l'état le plus
 * profond atteint dans un budget de nœuds **et** un deadline temps-réel.
 *
 * Mécanique d'un tirage (centrée caisse) : pour une caisse en `b` et une
 * direction unitaire `t`, le joueur se tient en `b+t`, recule en `b+2t`, et la
 * caisse le suit de `b` vers `b+t`. Conditions : `b+t` est une case sol libre
 * accessible au joueur, et `b+2t` est une case sol libre.
 */
object SokobanGenerator {

    private const val MAX_ATTEMPTS = 8
    private const val MAX_TOTAL_MS = 2200L
    private const val RESERVOIR_CAP = 60
    private const val EARLY_EXTRA = 3       // s'arrêter à targetDepth + ceci
    private const val EARLY_MIN_VARIETY = 12

    fun generate(difficulty: SokobanDifficulty, checkCancellation: () -> Unit = {}): SokobanPuzzle? {
        val deadline = System.nanoTime() + MAX_TOTAL_MS * 1_000_000
        var best: SearchResult? = null

        var attempt = 0
        while (attempt < MAX_ATTEMPTS && System.nanoTime() < deadline) {
            checkCancellation()
            attempt++
            val room = buildRoom(difficulty) ?: continue
            val boxCount = difficulty.randomBoxCount()
            if (room.floor.size < boxCount + 4) continue

            // Chaque quai doit laisser la place à au moins un tirage inverse.
            val candidates = room.floor.filter { cell ->
                SokobanDir.entries.any { dir ->
                    (1..2).all { step ->
                        val x = cell % room.width + dir.dx * step
                        val y = cell / room.width + dir.dy * step
                        room.inBounds(x, y) && !room.wall[y * room.width + x]
                    }
                }
            }
            if (candidates.size < boxCount) continue
            val goals = candidates.shuffled().take(boxCount).sorted().toIntArray()
            // Une pièce ne doit pas consommer tout le budget des autres essais.
            val attemptDeadline = minOf(deadline, System.nanoTime() + 300_000_000L)
            val result = reverseSearch(room, goals, difficulty, attemptDeadline, checkCancellation) ?: continue

            if (best == null || result.distance > best!!.distance) {
                best = result.copy(room = room, goals = goals)
            }
            if (result.distance >= difficulty.targetDepth) break
        }

        return best?.let { buildPuzzle(it.room!!, it.goals!!, it) }
    }

    // ── Génération de la pièce ───────────────────────────────────────────────

    private class Room(val width: Int, val height: Int, val wall: BooleanArray) {
        val size = width * height
        val floor: List<Int> = (0 until size).filter { !wall[it] }
        fun inBounds(x: Int, y: Int) = x in 0 until width && y in 0 until height
    }

    private fun buildRoom(difficulty: SokobanDifficulty): Room? {
        val (w, h) = difficulty.gridSizes.random()
        val wall = BooleanArray(w * h) { true }

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                if (Random.nextDouble() >= difficulty.wallProbability) {
                    wall[y * w + x] = false
                }
            }
        }

        // Garde la plus grande composante connexe de sol, le reste devient mur.
        val remaining = (wall.indices).filter { !wall[it] }.toMutableSet()
        var component: Set<Int> = emptySet()
        while (remaining.isNotEmpty()) {
            val connected = floodFill(w, h, wall, remaining.first())
            remaining.removeAll(connected)
            if (connected.size > component.size) component = connected
        }
        for (i in wall.indices) if (!wall[i] && i !in component) wall[i] = true
        if (component.size < 9) return null

        return Room(w, h, wall)
    }

    private fun floodFill(w: Int, h: Int, wall: BooleanArray, start: Int): Set<Int> {
        val seen = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack.addLast(start); seen.add(start)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            val cx = cur % w; val cy = cur / w
            for (dir in SokobanDir.entries) {
                val nx = cx + dir.dx; val ny = cy + dir.dy
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                val ni = ny * w + nx
                if (wall[ni] || ni in seen) continue
                seen.add(ni); stack.addLast(ni)
            }
        }
        return seen
    }

    // ── BFS sur le graphe inverse (tirages) = solveur ────────────────────────

    private class Node(val boxes: IntArray, val player: Int, val dist: Int)

    private data class SearchResult(
        val boxes: IntArray,
        val player: Int,
        val distance: Int,
        val room: Room? = null,
        val goals: IntArray? = null
    )

    private fun reverseSearch(
        room: Room,
        goals: IntArray,
        difficulty: SokobanDifficulty,
        deadline: Long,
        checkCancellation: () -> Unit
    ): SearchResult? {
        val w = room.width; val h = room.height; val n = room.size
        val wall = room.wall
        val nodeBudget = difficulty.nodeBudget
        val earlyDepth = difficulty.targetDepth + EARLY_EXTRA

        val isBox = BooleanArray(n)

        // Région du joueur du nœud courant (booléen stable pendant l'expansion).
        val nodeRegion = BooleanArray(n)
        val nodeTouched = IntArray(n)
        var nodeTouchedCount = 0
        val regionStack = IntArray(n)

        fun fillNodeRegion(playerCell: Int) {
            for (k in 0 until nodeTouchedCount) nodeRegion[nodeTouched[k]] = false
            nodeTouchedCount = 0
            var top = 0
            regionStack[top++] = playerCell
            nodeRegion[playerCell] = true
            nodeTouched[nodeTouchedCount++] = playerCell
            while (top > 0) {
                val cur = regionStack[--top]
                val cx = cur % w; val cy = cur / w
                for (dir in SokobanDir.entries) {
                    val nx = cx + dir.dx; val ny = cy + dir.dy
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                    val ni = ny * w + nx
                    if (wall[ni] || isBox[ni] || nodeRegion[ni]) continue
                    nodeRegion[ni] = true
                    nodeTouched[nodeTouchedCount++] = ni
                    regionStack[top++] = ni
                }
            }
        }

        // Représentant (case min) de la région d'une case, via stamp — pour
        // normaliser la position joueur des nouveaux états.
        val regionStamp = IntArray(n) { -1 }
        var stamp = 0
        fun computeRegionRep(cell: Int): Int {
            stamp++
            var top = 0
            regionStack[top++] = cell
            regionStamp[cell] = stamp
            var rep = cell
            while (top > 0) {
                val cur = regionStack[--top]
                if (cur < rep) rep = cur
                val cx = cur % w; val cy = cur / w
                for (dir in SokobanDir.entries) {
                    val nx = cx + dir.dx; val ny = cy + dir.dy
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                    val ni = ny * w + nx
                    if (wall[ni] || isBox[ni] || regionStamp[ni] == stamp) continue
                    regionStamp[ni] = stamp
                    regionStack[top++] = ni
                }
            }
            return rep
        }

        fun encode(boxes: IntArray, rep: Int): Long {
            var key = 0L
            for (b in boxes) key = key * 128L + b
            return key * 128L + rep
        }

        val visited = HashSet<Long>()
        val queue = ArrayDeque<Node>()

        // Réservoir des états les plus profonds (à la profondeur max courante).
        val reservoir = ArrayList<Node>()
        var maxDist = -1
        var seenAtDepth = 0
        var fallback: Node? = null
        fun consider(node: Node) {
            if (node.dist > (fallback?.dist ?: 0)) fallback = node
            // Préfère les départs où aucune caisse n'est déjà garée.
            if (node.boxes.any { it in goals }) return
            when {
                node.dist > maxDist -> {
                    maxDist = node.dist; reservoir.clear(); reservoir.add(node); seenAtDepth = 1
                }
                node.dist == maxDist -> {
                    seenAtDepth++
                    if (reservoir.size < RESERVOIR_CAP) reservoir.add(node)
                    else {
                        val slot = Random.nextInt(seenAtDepth)
                        if (slot < RESERVOIR_CAP) reservoir[slot] = node
                    }
                }
            }
        }

        // Amorce : état résolu, toutes les régions joueur possibles à distance 0.
        run {
            for (b in goals) isBox[b] = true
            val seededReps = HashSet<Int>()
            for (cell in room.floor) {
                if (isBox[cell] || cell in seededReps) continue
                val rep = computeRegionRep(cell)
                for (c in room.floor) if (regionStamp[c] == stamp) seededReps.add(c)
                if (visited.add(encode(goals, rep))) {
                    val node = Node(goals, rep, 0)
                    queue.addLast(node); consider(node)
                }
            }
            for (b in goals) isBox[b] = false
        }

        var nodes = 0
        while (queue.isNotEmpty()) {
            if (nodes >= nodeBudget) break
            if (maxDist >= earlyDepth && reservoir.size >= EARLY_MIN_VARIETY) break
            if ((nodes and 0x7F) == 0) {
                checkCancellation()
                if (System.nanoTime() >= deadline) break
            }

            val node = queue.removeFirst()
            nodes++

            for (b in node.boxes) isBox[b] = true
            fillNodeRegion(node.player)

            for (b in node.boxes) {
                val bx = b % w; val by = b / w
                for (dir in SokobanDir.entries) {
                    val standX = bx + dir.dx; val standY = by + dir.dy
                    val destX = bx + 2 * dir.dx; val destY = by + 2 * dir.dy
                    if (!room.inBounds(standX, standY) || !room.inBounds(destX, destY)) continue
                    val stand = standY * w + standX
                    val dest = destY * w + destX
                    if (!nodeRegion[stand]) continue
                    if (wall[dest] || isBox[dest]) continue

                    val newBoxes = node.boxes.copyOf()
                    newBoxes[newBoxes.indexOf(b)] = stand
                    newBoxes.sort()

                    isBox[b] = false; isBox[stand] = true
                    val rep = computeRegionRep(dest)
                    isBox[stand] = false; isBox[b] = true

                    if (visited.add(encode(newBoxes, rep))) {
                        val child = Node(newBoxes, dest, node.dist + 1)
                        queue.addLast(child); consider(child)
                    }
                }
            }

            for (b in node.boxes) isBox[b] = false
        }

        val chosen = reservoir.randomOrNull() ?: fallback ?: return null
        return SearchResult(chosen.boxes, chosen.player, chosen.dist)
    }

    private fun buildPuzzle(room: Room, goals: IntArray, result: SearchResult): SokobanPuzzle {
        val walls = (0 until room.size).filter { room.wall[it] }.toSet()
        return SokobanPuzzle(
            width = room.width,
            height = room.height,
            walls = walls,
            goals = goals.toSet(),
            boxes = result.boxes.toList(),
            player = result.player,
            optimalPushes = result.distance
        )
    }
}
