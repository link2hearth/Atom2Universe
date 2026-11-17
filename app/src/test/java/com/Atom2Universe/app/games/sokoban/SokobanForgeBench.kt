package com.Atom2Universe.app.games.sokoban

import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * La fabrique de niveaux du Sokoban, sur PC (banc de mesure, jamais lancé par défaut).
 *
 * Production des niveaux du jeu (≈ 25 min sur 4 cœurs), écrits dans `assets/sokoban/levels.txt` :
 *
 *     ./gradlew testDebugUnitTest -PbancsMesure --tests "*SokobanForgeBench.fabrique"
 *
 * Chaque chapitre est une liste de « commandes » (pièce, caisses, effort de recuit) de la plus
 * simple à la plus travaillée ; les niveaux obtenus sont vérifiés (solution rejouée dans le vrai
 * jeu, optimum recompté par un solveur indépendant), dédoublonnés, puis rangés par richesse.
 */
class SokobanForgeBench {

    /** Une commande : la pièce voulue et l'effort accordé au recuit. */
    private class Order(val chapter: Int, val spec: SokobanForgeSpec, val iterations: Int, val restarts: Int)

    private fun ramp(from: Int, to: Int, count: Int, k: Int) =
        if (count <= 1) from else from + (to - from) * k / (count - 1)

    private fun orders(): List<Order> {
        val list = ArrayList<Order>()
        fun add(chapter: Int, count: Int, w: Int, h: Int, boxes: Int, minF: Int, maxF: Int, cap: Int, it0: Int, it1: Int) {
            repeat(count) { k ->
                val it = ramp(it0, it1, count, k)
                // Pièce couchée ou debout, au hasard de la commande.
                val spec = if (w != h && k % 2 == 1) SokobanForgeSpec(h, w, boxes, minF, maxF, cap)
                else SokobanForgeSpec(w, h, boxes, minF, maxF, cap)
                list.add(Order(chapter, spec, it, if (it >= 300) 2 else 1))
            }
        }
        // Chapitre 1 : une puis deux caisses, de l'échauffement au vrai casse-tête.
        add(1, 2, 4, 4, 1, 7, 11, 50_000, 10, 20)
        add(1, 3, 5, 4, 2, 8, 13, 50_000, 20, 60)
        add(1, 6, 5, 5, 2, 10, 16, 100_000, 100, 500)
        add(1, 9, 6, 6, 2, 12, 20, 200_000, 600, 2000)
        // Chapitre 2 : trois caisses.
        add(2, 6, 6, 5, 3, 13, 20, 300_000, 60, 300)
        add(2, 8, 6, 6, 3, 14, 24, 400_000, 400, 2000)
        add(2, 6, 7, 6, 3, 15, 27, 500_000, 1500, 3000)
        // Chapitre 3 : quatre caisses.
        add(3, 6, 7, 6, 4, 16, 28, 800_000, 150, 600)
        add(3, 8, 7, 7, 4, 18, 32, 1_000_000, 800, 2000)
        add(3, 6, 8, 7, 4, 19, 34, 1_000_000, 2000, 3000)
        // Chapitre 4 : cinq caisses.
        add(4, 6, 8, 7, 5, 20, 38, 1_500_000, 300, 800)
        add(4, 8, 8, 8, 5, 22, 42, 1_500_000, 1000, 2000)
        add(4, 6, 9, 8, 5, 24, 44, 1_500_000, 2000, 2500)
        // Chapitre 5 : six puis sept caisses.
        add(5, 6, 9, 8, 6, 24, 46, 2_000_000, 400, 800)
        add(5, 8, 9, 9, 6, 26, 48, 2_000_000, 800, 1500)
        add(5, 6, 10, 9, 7, 30, 54, 2_000_000, 1000, 1500)
        return list
    }

    /** Un niveau fini : le plan prêt à jouer et ses mesures (gardés en cache entre deux lancements). */
    private class Made(val order: Order, val puzzle: SokobanPuzzle, val pushes: Int, val changes: Int, val lines: Int, val score: Double)

    private fun cacheFile(idx: Int, o: Order) = File("build/sokoban_forge_cache/%03d_%dx%d_%d_%d_%d_%d.txt".format(
        idx, o.spec.interiorW, o.spec.interiorH, o.spec.boxes, o.spec.minFloor, o.spec.maxFloor, o.iterations, o.restarts))

    private fun metrics(m: Made, extra: List<Pair<String, Any>> = emptyList()) = extra + listOf(
        "pushes" to m.pushes, "moves" to m.puzzle.solution.length, "changes" to m.changes,
        "lines" to m.lines, "score" to "%.1f".format(java.util.Locale.ROOT, m.score))

    @Test
    fun fabrique() {
        val all = orders()
        val log = File("build/sokoban_forge_log.txt")
        log.writeText("")
        val seen = HashSet<String>()
        val made = arrayOfNulls<Made>(all.size)
        // Les niveaux déjà finis lors d'un lancement précédent (mêmes commandes, mêmes graines).
        for ((idx, order) in all.withIndex()) {
            val f = cacheFile(idx, order)
            if (!f.exists()) continue
            val (p, meta) = SokobanXsb.parse(f.readText()).firstOrNull() ?: continue
            if (!seen.add(canonical(p))) continue
            made[idx] = Made(order, p, meta.getValue("pushes").toInt(), meta.getValue("changes").toInt(),
                meta.getValue("lines").toInt(), meta.getValue("score").toDouble())
        }
        val pool = Executors.newFixedThreadPool(4)
        // Les commandes les plus lourdes d'abord, pour que les 4 cœurs finissent ensemble.
        val byCost = all.indices.filter { made[it] == null }
            .sortedByDescending { all[it].spec.boxes * 10_000 + all[it].iterations * all[it].restarts }
        val jobs = byCost.map { idx ->
            pool.submit {
                val order = all[idx]
                var attempt = 0
                while (attempt < 6) {
                    val rnd = Random(77_000L + idx * 7919L + attempt * 104_729L)
                    attempt++
                    val t0 = System.nanoTime()
                    val (level, puzzle) = forgeOne(order, rnd) ?: continue
                    val fresh = synchronized(seen) { seen.add(canonical(puzzle)) }
                    if (!fresh) continue
                    // Contrôle indépendant de l'optimum, quand l'espace des positions le permet
                    // (la solution, elle, est toujours rejouée dans le vrai jeu par forgeOne).
                    val verified = forwardOptimalPushes(puzzle)
                    check(verified == null || verified == level.line.pushes) {
                        "optimum faux : annoncé ${level.line.pushes}, recompté $verified"
                    }
                    val m = Made(order, puzzle, level.line.pushes, level.line.changes, level.line.lines, level.score)
                    cacheFile(idx, order).apply { parentFile?.mkdirs() }.writeText(SokobanXsb.format(puzzle, metrics(m)))
                    made[idx] = m
                    val sec = (System.nanoTime() - t0) / 1_000_000_000
                    synchronized(log) {
                        log.appendText("ch%d %d caisses %dx%d  %4d essais  score %6.1f  poussées %3d  changements %2d  positions %7d  %s  %4d s\n".format(
                            order.chapter, order.spec.boxes, order.spec.interiorW, order.spec.interiorH, order.iterations,
                            level.score, level.line.pushes, level.line.changes, level.exploration.stateCount,
                            if (verified == null) "non recompté" else "recompté", sec))
                    }
                    break
                }
            }
        }
        jobs.forEach { it.get() }
        pool.shutdown()

        val out = StringBuilder()
        out.append("; Sokoban : niveaux fabriqués sur PC par SokobanForgeBench.fabrique, ne pas retoucher à la main\n\n")
        val report = StringBuilder()
        var number = 0
        for (chapter in all.map { it.chapter }.distinct().sorted()) {
            val group = made.filterNotNull().filter { it.order.chapter == chapter }.sortedBy { it.score }
            for (m in group) {
                number++
                out.append(SokobanXsb.format(m.puzzle, metrics(m, listOf("level" to number, "chapter" to chapter))))
                out.append('\n')
                report.append("%3d  ch%d  %d caisses  %2dx%-2d  poussées %3d  coups %3d  changements %2d  score %6.1f\n".format(
                    number, chapter, m.order.spec.boxes, m.puzzle.width, m.puzzle.height, m.pushes,
                    m.puzzle.solution.length, m.changes, m.score))
            }
        }
        val asset = File("src/main/assets/sokoban/levels.txt")
        asset.parentFile?.mkdirs()
        asset.writeText(out.toString())
        File("build/sokoban_forge_report.txt").writeText(report.toString())
        println(report)
        check(number == all.size) { "${all.size - number} commandes sans niveau" }
    }

    private fun forgeOne(order: Order, rnd: Random): Pair<SokobanForged, SokobanPuzzle>? {
        val spec = order.spec
        val deadline = System.nanoTime() + 20L * 60 * 1_000_000_000
        var best: SokobanForged? = null
        repeat(order.restarts) {
            val seed = SokobanForge.seed(spec, rnd, deadline) ?: return@repeat
            val climbed = SokobanForge.climb(seed, spec, rnd, order.iterations, deadline)
            if (best == null || climbed.score > best!!.score) best = climbed
        }
        val b = best ?: return null
        val done = SokobanForge.polish(b, spec, rnd, deadline)
        if (done.line.movedBoxes < spec.boxes) return null
        val p = SokobanForge.toPuzzle(done, rnd)
        val (ok, pushes) = replay(p)
        check(ok && pushes == done.line.pushes) { "solution fausse" }
        return done to p
    }

    private fun replay(p: SokobanPuzzle): Pair<Boolean, Int> {
        val g = SokobanGame()
        g.load(p)
        for (c in p.solution) {
            val dir = when (c.lowercaseChar()) {
                'u' -> SokobanDir.UP; 'd' -> SokobanDir.DOWN; 'l' -> SokobanDir.LEFT; else -> SokobanDir.RIGHT
            }
            if (!g.move(dir)) return false to g.pushes
        }
        return g.isSolved() to g.pushes
    }

    /** Le plan sans le robot, sous ses 8 symétries : la plus petite écriture sert de clé. */
    private fun canonical(p: SokobanPuzzle): String {
        val boxes = p.boxes.toSet()
        var best: String? = null
        for (t in 0 until 8) {
            val swap = t >= 4
            val w = if (swap) p.height else p.width
            val h = if (swap) p.width else p.height
            val sb = StringBuilder()
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var sx = if (swap) y else x
                    var sy = if (swap) x else y
                    if (t and 1 != 0) sx = p.width - 1 - sx
                    if (t and 2 != 0) sy = p.height - 1 - sy
                    val i = sy * p.width + sx
                    sb.append(when {
                        p.isWall(i) -> '#'
                        i in boxes && i in p.goals -> '*'
                        i in boxes -> '$'
                        i in p.goals -> '.'
                        else -> ' '
                    })
                }
                sb.append('/')
            }
            val s = sb.toString()
            if (best == null || s < best) best = s
        }
        return best!!
    }

    /**
     * Solveur indépendant de la fabrique : parcours en largeur **en avant** (poussées) depuis le
     * départ, cases de la grille et non cases de sol, jusqu'à la position résolue.
     */
    private fun forwardOptimalPushes(p: SokobanPuzzle): Int? {
        val w = p.width
        val n = w * p.height
        val k = p.boxes.size
        require(n <= 255 && k <= 7)
        val wall = BooleanArray(n) { p.isWall(it) }
        val goals = p.goals.sorted().toIntArray()
        val steps = intArrayOf(-w, w, -1, 1)

        // Cases vivantes : celles d'où une caisse seule peut encore rejoindre un quai. Pousser une
        // caisse ailleurs la perd à coup sûr : élaguer ces poussées ne change pas l'optimum.
        val live = BooleanArray(n)
        val queue = ArrayDeque<Int>()
        for (g in goals) { live[g] = true; queue.addLast(g) }
        while (queue.isNotEmpty()) {
            val b = queue.removeFirst()
            for (s in steps) {
                val a = b - s
                if (wall[a] || live[a] || wall[a - s]) continue
                live[a] = true; queue.addLast(a)
            }
        }

        val isBox = BooleanArray(n)
        val seen = IntArray(n)
        var stamp = 0
        val stack = IntArray(n)
        fun fill(start: Int): Int {
            stamp++
            var top = 0
            stack[top++] = start; seen[start] = stamp
            var rep = start
            while (top > 0) {
                val c = stack[--top]
                if (c < rep) rep = c
                for (s in steps) {
                    val d = c + s
                    if (wall[d] || isBox[d] || seen[d] == stamp) continue
                    seen[d] = stamp; stack[top++] = d
                }
            }
            return rep
        }
        // Une position tient dans un Long : une case (8 bits) par caisse triée, puis le robot.
        fun key(boxes: IntArray, rep: Int): Long {
            var x = 0L
            for (b in boxes) x = (x shl 8) or b.toLong()
            return (x shl 8) or rep.toLong()
        }
        val boxes = IntArray(k)
        fun decode(x: Long) {
            var y = x ushr 8
            for (i in k - 1 downTo 0) { boxes[i] = (y and 0xFF).toInt(); y = y ushr 8 }
        }
        val goalBoxes = key(goals, 0) ushr 8

        val start = p.boxes.sorted().toIntArray()
        if (start.contentEquals(goals)) return 0
        for (b in start) isBox[b] = true
        val startKey = key(start, fill(p.player))
        for (b in start) isBox[b] = false

        val visited = LongIntMap(1 shl 16)
        visited.put(startKey, 1)
        var cur = LongArray(16)
        var curSize = 0
        cur[curSize++] = startKey
        val reach = BooleanArray(n)
        var depth = 0
        while (curSize > 0 && visited.size < 4_000_000) {
            depth++
            var next = LongArray(maxOf(16, curSize * 2))
            var nextSize = 0
            for (idx in 0 until curSize) {
                val x = cur[idx]
                decode(x)
                for (b in boxes) isBox[b] = true
                fill((x and 0xFF).toInt())
                val region = stamp
                for (c in 0 until n) reach[c] = seen[c] == region
                for (bi in 0 until k) {
                    val b = boxes[bi]
                    for (s in steps) {
                        val to = b + s
                        if (!reach[b - s] || wall[to] || isBox[to] || !live[to]) continue
                        val nb = boxes.copyOf()
                        nb[bi] = to
                        nb.sort()
                        isBox[b] = false; isBox[to] = true
                        val nrep = fill(b)
                        isBox[to] = false; isBox[b] = true
                        val nk = key(nb, nrep)
                        if (visited.get(nk, 0) != 0) continue
                        if ((nk ushr 8) == goalBoxes) return depth
                        visited.put(nk, 1)
                        if (nextSize == next.size) next = next.copyOf(next.size * 2)
                        next[nextSize++] = nk
                    }
                }
                for (b in boxes) isBox[b] = false
            }
            cur = next
            curSize = nextSize
        }
        return null
    }
}
