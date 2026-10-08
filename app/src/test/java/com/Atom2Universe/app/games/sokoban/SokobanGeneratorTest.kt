package com.Atom2Universe.app.games.sokoban

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mesure le temps de génération et vérifie de façon indépendante que chaque
 * niveau est résoluble (solveur de poussées séparé), et que [SokobanPuzzle.optimalPushes]
 * correspond bien à l'optimum.
 */
class SokobanGeneratorTest {

    private val runsPerDifficulty = 6

    @Test
    fun generationIsFastAndSolvable() {
        val report = StringBuilder()
        var globalMax = 0L

        for (diff in SokobanDifficulty.entries) {
            var total = 0L
            var max = 0L
            var minDepthSeen = Int.MAX_VALUE
            var maxDepthSeen = 0

            repeat(runsPerDifficulty) {
                val start = System.nanoTime()
                val puzzle = SokobanGenerator.generate(diff)
                val elapsed = (System.nanoTime() - start) / 1_000_000
                total += elapsed
                if (elapsed > max) max = elapsed
                if (elapsed > globalMax) globalMax = elapsed

                assertNotNull("$diff: génération nulle", puzzle)
                puzzle!!

                val optimal = forwardOptimalPushes(puzzle)
                assertNotNull("$diff: niveau INSOLUBLE", optimal)
                assertTrue(
                    "$diff: optimal annoncé ${puzzle.optimalPushes} != mesuré $optimal",
                    optimal == puzzle.optimalPushes
                )
                minDepthSeen = minOf(minDepthSeen, puzzle.optimalPushes)
                maxDepthSeen = maxOf(maxDepthSeen, puzzle.optimalPushes)
            }

            val avg = total / runsPerDifficulty
            report.append(
                "%-7s  avg=%4dms  max=%4dms  pushes=%d..%d\n"
                    .format(diff.name, avg, max, minDepthSeen, maxDepthSeen)
            )
        }

        File("build/sokoban_timing.txt").writeText(report.toString())
        println(report.toString())

        assertTrue("Génération trop lente: ${globalMax}ms\n$report", globalMax < 3500)
    }

    // ── Solveur de poussées indépendant (forward) ────────────────────────────

    private fun forwardOptimalPushes(p: SokobanPuzzle): Int? {
        val w = p.width; val h = p.height; val n = w * h
        val wall = BooleanArray(n) { it in p.walls }
        val goalKey = encode(p.goals.sorted().toIntArray(), 0).let { it / 128L }

        val isBox = BooleanArray(n)
        val regionStamp = IntArray(n) { -1 }
        var stamp = 0
        val stack = IntArray(n)

        fun regionRep(cell: Int): Int {
            stamp++
            var top = 0
            stack[top++] = cell; regionStamp[cell] = stamp
            var rep = cell
            while (top > 0) {
                val cur = stack[--top]
                if (cur < rep) rep = cur
                val cx = cur % w; val cy = cur / w
                for (dir in SokobanDir.entries) {
                    val nx = cx + dir.dx; val ny = cy + dir.dy
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                    val ni = ny * w + nx
                    if (wall[ni] || isBox[ni] || regionStamp[ni] == stamp) continue
                    regionStamp[ni] = stamp; stack[top++] = ni
                }
            }
            return rep
        }

        val startBoxes = p.boxes.sorted().toIntArray()
        for (b in startBoxes) isBox[b] = true
        val startRep = regionRep(p.player)
        for (b in startBoxes) isBox[b] = false

        if (startBoxes.toSortedSet() == p.goals) return 0

        val visited = HashSet<Long>()
        val queue = ArrayDeque<Pair<IntArray, Int>>() // boxes + player rep
        val dist = HashMap<Long, Int>()

        val startKey = encode(startBoxes, startRep)
        visited.add(startKey); dist[startKey] = 0
        queue.addLast(startBoxes to startRep)

        var nodes = 0
        val cap = 3_000_000
        while (queue.isNotEmpty() && nodes < cap) {
            val (boxes, rep) = queue.removeFirst()
            nodes++
            val d = dist[encode(boxes, rep)]!!

            for (b in boxes) isBox[b] = true
            // Recompute current region membership for the player.
            regionRep(rep)
            var curStamp = stamp

            for (b in boxes) {
                val bx = b % w; val by = b / w
                for (dir in SokobanDir.entries) {
                    // Pousser b dans la direction dir : joueur en b-dir, caisse vers b+dir.
                    val fromX = bx - dir.dx; val fromY = by - dir.dy
                    val toX = bx + dir.dx; val toY = by + dir.dy
                    if (fromX < 0 || fromX >= w || fromY < 0 || fromY >= h) continue
                    if (toX < 0 || toX >= w || toY < 0 || toY >= h) continue
                    val from = fromY * w + fromX
                    val to = toY * w + toX
                    if (regionStamp[from] != curStamp) continue
                    if (wall[to] || isBox[to]) continue

                    val nb = boxes.copyOf()
                    nb[nb.indexOf(b)] = to
                    nb.sort()

                    isBox[b] = false; isBox[to] = true
                    val nrep = regionRep(b) // joueur finit là où était la caisse
                    isBox[to] = false; isBox[b] = true
                    // restaure la région courante (nouveau stamp)
                    regionRep(rep)
                    curStamp = stamp

                    val key = encode(nb, nrep)
                    if (visited.add(key)) {
                        dist[key] = d + 1
                        if (encode(nb, 0) / 128L == goalKey) {
                            return d + 1
                        }
                        queue.addLast(nb to nrep)
                    }
                }
            }
            for (b in boxes) isBox[b] = false
        }
        return null
    }

    private fun encode(boxes: IntArray, rep: Int): Long {
        var key = 0L
        for (b in boxes) key = key * 128L + b
        return key * 128L + rep
    }
}
