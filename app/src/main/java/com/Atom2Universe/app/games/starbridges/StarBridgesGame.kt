package com.Atom2Universe.app.games.starbridges

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class SbNode(val id: Int, val x: Int, val y: Int, var required: Int = 0)

data class SbEdge(
    val from: Int, val to: Int,
    val key: String,
    val x1: Float, val y1: Float,
    val x2: Float, val y2: Float,
    val orientation: String
)

class StarBridgesGame {

    companion object {
        val ALLOWED_SIZES = listOf(6, 7, 8)
        const val DEFAULT_SIZE = 6
    }

    var size = DEFAULT_SIZE
    var seed = ""
    var seedWasRandom = true
    var nodes: List<SbNode> = emptyList()
    var edges: List<SbEdge> = emptyList()
    var edgesByKey: Map<String, SbEdge> = emptyMap()
    var solution: Set<String> = emptySet()
    var bridges: MutableMap<String, Int> = mutableMapOf()
    var moves = 0
    var elapsedSeconds = 0L
    var solved = false
    var rewardClaimed = false
    var selectedNodeId: Int? = null

    private var rngState = 0

    // ── Seeded RNG (xmur3 init + mulberry32) ─────────────────────────────────────
    private fun initRng(seedStr: String) {
        var h = -1640531527 xor seedStr.length
        for (c in seedStr) {
            h = h xor c.code
            h = (h.toLong() * 3432918353L).toInt()
            h = (h shl 13) or (h ushr 19)
        }
        h = (h.toLong() xor (h ushr 16).toLong()).toInt()
        h = (h.toLong() * -2048144789L).toInt()
        h = (h.toLong() xor (h ushr 13).toLong()).toInt()
        h = (h.toLong() * -1028477387L).toInt()
        rngState = h xor (h ushr 16)
    }

    private fun nextFloat(): Float {
        var t = rngState + 0x6D2B79F5
        rngState = t
        t = (t.toLong() xor (t ushr 15).toLong()).toInt()
        val a = t.toLong() * ((t or 1).toLong() and 0xFFFFFFFFL)
        t = a.toInt()
        t = t xor (t + (t.toLong() * ((t or 61).toLong() and 0xFFFFFFFFL)).toInt())
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }

    private fun nextInt(n: Int): Int = (nextFloat() * n).toInt().coerceIn(0, n - 1)

    private fun <T> shuffle(list: MutableList<T>): MutableList<T> {
        for (i in list.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        }
        return list
    }

    // ── Edge key ──────────────────────────────────────────────────────────────────
    private fun makeEdgeKey(a: Int, b: Int): String = if (a < b) "$a-$b" else "$b-$a"

    // ── Segment intersection check ────────────────────────────────────────────────
    private fun edgesCross(a: SbEdge, b: SbEdge): Boolean {
        if (a.key == b.key) return false
        if (a.from == b.from || a.from == b.to || a.to == b.from || a.to == b.to) return false

        fun orient(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float): Int {
            val v = (qx - px) * (ry - py) - (qy - py) * (rx - px)
            return if (abs(v) <= 1e-9f) 0 else if (v > 0) 1 else -1
        }

        fun onSeg(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float): Boolean =
            rx <= max(px, qx) + 1e-9f && rx + 1e-9f >= min(px, qx) &&
            ry <= max(py, qy) + 1e-9f && ry + 1e-9f >= min(py, qy)

        val o1 = orient(a.x1, a.y1, a.x2, a.y2, b.x1, b.y1)
        val o2 = orient(a.x1, a.y1, a.x2, a.y2, b.x2, b.y2)
        val o3 = orient(b.x1, b.y1, b.x2, b.y2, a.x1, a.y1)
        val o4 = orient(b.x1, b.y1, b.x2, b.y2, a.x2, a.y2)

        if (o1 == 0 && onSeg(a.x1, a.y1, a.x2, a.y2, b.x1, b.y1)) return true
        if (o2 == 0 && onSeg(a.x1, a.y1, a.x2, a.y2, b.x2, b.y2)) return true
        if (o3 == 0 && onSeg(b.x1, b.y1, b.x2, b.y2, a.x1, a.y1)) return true
        if (o4 == 0 && onSeg(b.x1, b.y1, b.x2, b.y2, a.x2, a.y2)) return true

        return o1 != o2 && o3 != o4
    }

    // ── Node layout ───────────────────────────────────────────────────────────────
    private fun generateLayout(s: Int): List<SbNode> {
        val nodeList = mutableListOf<SbNode>()
        val used = mutableSetOf<String>()
        val colCounts = IntArray(s)

        fun addNode(x: Int, y: Int): Boolean {
            val key = "$x,$y"
            if (!used.add(key)) return false
            nodeList.add(SbNode(id = nodeList.size, x = x, y = y))
            colCounts[x]++
            return true
        }

        val cols = MutableList(s) { it }
        for (y in 0 until s) {
            val shuffled = shuffle(cols.toMutableList())
            var placed = 0
            for (x in shuffled) { if (placed >= 2) break; if (addNode(x, y)) placed++ }
            for (x in 0 until s) { if (placed >= 2) break; addNode(x, y).also { if (it) placed++ } }
        }

        val target = max(
            max(nodeList.size, (s * 2.5).toInt()),
            min(s * s - s, (s * 3.2).toInt())
        ).let { max(it, nodeList.size + nextInt(s)) }

        val allPos = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until s) for (x in 0 until s) allPos.add(x to y)
        shuffle(allPos)
        for ((x, y) in allPos) { if (nodeList.size >= target) break; addNode(x, y) }

        for (x in 0 until s) {
            if (colCounts[x] > 0) continue
            val rowOrder = shuffle(MutableList(s) { it })
            for (y in rowOrder) { if (addNode(x, y)) break }
        }

        return nodeList
    }

    // ── Edge building ─────────────────────────────────────────────────────────────
    private fun buildEdges(nodeList: List<SbNode>, s: Int): List<SbEdge> {
        val grid = Array(s) { arrayOfNulls<SbNode>(s) }
        for (n in nodeList) grid[n.y][n.x] = n

        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to -1, 1 to 1, -1 to 1)

        fun orient(dx: Int, dy: Int): String = when {
            dy == 0 -> "horizontal"
            dx == 0 -> "vertical"
            dx == dy -> "diagonal-down"
            else -> "diagonal-up"
        }

        val raw = mutableListOf<Pair<SbEdge, Float>>()
        for (n in nodeList) {
            for ((dx, dy) in dirs) {
                var nx = n.x + dx; var ny = n.y + dy
                while (nx in 0 until s && ny in 0 until s) {
                    val nb = grid[ny][nx]
                    if (nb != null) {
                        val key = makeEdgeKey(n.id, nb.id)
                        val dist = sqrt(((nb.x - n.x).toFloat() * (nb.x - n.x) + (nb.y - n.y).toFloat() * (nb.y - n.y)))
                        raw.add(SbEdge(n.id, nb.id, key, n.x + 0.5f, n.y + 0.5f, nb.x + 0.5f, nb.y + 0.5f, orient(dx, dy)) to dist)
                        break
                    }
                    nx += dx; ny += dy
                }
            }
        }

        val unique = mutableMapOf<String, Pair<SbEdge, Float>>()
        for ((edge, dist) in raw) {
            val existing = unique[edge.key]
            if (existing == null || dist < existing.second) unique[edge.key] = edge to dist
        }
        return unique.values.map { it.first }
    }

    // ── Spanning tree (Kruskal's + no-crossing) ───────────────────────────────────
    private fun buildSpanningTree(nodeList: List<SbNode>, edgeList: List<SbEdge>): List<SbEdge>? {
        val shuffled = shuffle(edgeList.toMutableList())
        val parent = IntArray(nodeList.size) { it }

        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }
            return x
        }

        fun union(a: Int, b: Int): Boolean {
            val ra = find(a); val rb = find(b)
            if (ra == rb) return false
            parent[rb] = ra; return true
        }

        val selected = mutableListOf<SbEdge>()
        for (edge in shuffled) {
            if (selected.size >= nodeList.size - 1) break
            if (selected.any { edgesCross(it, edge) }) continue
            if (union(edge.from, edge.to)) selected.add(edge)
        }

        return if (selected.size == nodeList.size - 1) selected else null
    }

    // ── Puzzle generation ─────────────────────────────────────────────────────────
    private fun generatePuzzle(s: Int): Boolean {
        for (attempt in 0 until 20) {
            val nodeList = generateLayout(s)
            val edgeList = buildEdges(nodeList, s)
            if (edgeList.isEmpty()) continue
            val edgesByKeyLocal = edgeList.associateBy { it.key }

            val tree = buildSpanningTree(nodeList, edgeList) ?: continue

            val counts = mutableMapOf<Int, Int>()
            val activeKeys = mutableSetOf<String>()
            for (e in tree) {
                activeKeys.add(e.key)
                counts[e.from] = (counts[e.from] ?: 0) + 1
                counts[e.to] = (counts[e.to] ?: 0) + 1
            }

            val extras = shuffle(edgeList.filter { it.key !in activeKeys }.toMutableList())
            for (e in extras) {
                if (nextFloat() > 0.4f) continue
                val crosses = activeKeys.any { k -> edgesByKeyLocal[k]?.let { edgesCross(it, e) } == true }
                if (crosses) continue
                activeKeys.add(e.key)
                counts[e.from] = (counts[e.from] ?: 0) + 1
                counts[e.to] = (counts[e.to] ?: 0) + 1
            }

            val updatedNodes = nodeList.map { it.copy(required = counts[it.id] ?: 0) }
            if (updatedNodes.any { it.required <= 0 }) continue

            nodes = updatedNodes
            edges = edgeList
            edgesByKey = edgesByKeyLocal
            solution = activeKeys.toSet()
            return true
        }
        return false
    }

    // ── Reset current puzzle ──────────────────────────────────────────────────────
    fun resetBridges() {
        bridges = mutableMapOf()
        moves = 0
        solved = false
        rewardClaimed = false
        selectedNodeId = null
        // elapsedSeconds intentionally kept — same puzzle, timer continues
    }

    // ── New game ──────────────────────────────────────────────────────────────────
    fun newGame(s: Int, customSeed: String = ""): Boolean {
        size = if (s in ALLOWED_SIZES) s else DEFAULT_SIZE
        seed = customSeed.ifBlank { System.currentTimeMillis().toString(36) }
        seedWasRandom = customSeed.isBlank()
        initRng(seed)
        bridges = mutableMapOf()
        moves = 0; elapsedSeconds = 0L; solved = false; rewardClaimed = false; selectedNodeId = null
        return generatePuzzle(size)
    }

    // ── Bridge toggle ─────────────────────────────────────────────────────────────
    fun canAddBridge(key: String): Boolean {
        if ((bridges[key] ?: 0) >= 1) return false
        val edge = edgesByKey[key] ?: return false
        return bridges.entries.none { (k, v) -> v > 0 && k != key && edgesByKey[k]?.let { edgesCross(it, edge) } == true }
    }

    fun toggleBridge(aId: Int, bId: Int): Boolean {
        val key = makeEdgeKey(aId, bId)
        if (!edgesByKey.containsKey(key)) return false
        if ((bridges[key] ?: 0) >= 1) {
            bridges.remove(key)
        } else {
            if (!canAddBridge(key)) return false
            bridges[key] = 1
        }
        moves++
        solved = checkSolved()
        return true
    }

    // ── Bridge count per node ─────────────────────────────────────────────────────
    fun bridgeCountFor(nodeId: Int): Int {
        var total = 0
        for ((key, count) in bridges) {
            if (count <= 0) continue
            val dash = key.indexOf('-')
            if (key.substring(0, dash).toInt() == nodeId || key.substring(dash + 1).toInt() == nodeId) total += count
        }
        return total
    }

    // ── Win detection ─────────────────────────────────────────────────────────────
    private fun checkSolved(): Boolean {
        if (nodes.isEmpty() || bridges.isEmpty()) return false
        val totals = mutableMapOf<Int, Int>()
        for (n in nodes) totals[n.id] = 0
        for ((key, count) in bridges) {
            if (count <= 0 || !edgesByKey.containsKey(key)) return false
            val dash = key.indexOf('-')
            val a = key.substring(0, dash).toInt()
            val b = key.substring(dash + 1).toInt()
            totals[a] = (totals[a] ?: 0) + count
            totals[b] = (totals[b] ?: 0) + count
        }
        if (nodes.any { (totals[it.id] ?: 0) != it.required }) return false
        return isConnected()
    }

    private fun isConnected(): Boolean {
        if (nodes.isEmpty()) return false
        val adj = HashMap<Int, MutableList<Int>>(nodes.size)
        for (n in nodes) adj[n.id] = mutableListOf()
        for ((key, count) in bridges) {
            if (count <= 0) continue
            val dash = key.indexOf('-')
            val a = key.substring(0, dash).toInt()
            val b = key.substring(dash + 1).toInt()
            adj[a]?.add(b); adj[b]?.add(a)
        }
        val visited = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack.addLast(nodes[0].id)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (!visited.add(cur)) continue
            adj[cur]?.forEach { if (it !in visited) stack.addLast(it) }
        }
        return visited.size == nodes.size
    }

    fun totalBridgesPlaced(): Int = bridges.values.sumOf { it }
    fun totalBridgesRequired(): Int = solution.size

    // ── Persistence ───────────────────────────────────────────────────────────────
    fun serialize(): String {
        val brObj = JSONObject()
        for ((k, v) in bridges) if (v > 0) brObj.put(k, v)

        val nodesArr = JSONArray()
        for (n in nodes) nodesArr.put(JSONObject().apply {
            put("id", n.id); put("x", n.x); put("y", n.y); put("req", n.required)
        })

        val edgesArr = JSONArray()
        for (e in edges) edgesArr.put(JSONObject().apply {
            put("f", e.from); put("t", e.to); put("k", e.key)
            put("x1", e.x1.toDouble()); put("y1", e.y1.toDouble())
            put("x2", e.x2.toDouble()); put("y2", e.y2.toDouble())
            put("o", e.orientation)
        })

        val solArr = JSONArray()
        for (k in solution) solArr.put(k)

        return JSONObject().apply {
            put("v", 1)
            put("size", size); put("seed", seed); put("rnd", seedWasRandom)
            put("nodes", nodesArr); put("edges", edgesArr); put("sol", solArr)
            put("br", brObj); put("moves", moves); put("elapsed", elapsedSeconds)
            put("solved", solved); put("rewarded", rewardClaimed)
        }.toString()
    }

    fun deserialize(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            if (obj.getInt("v") != 1) return false

            size = obj.getInt("size").takeIf { it in ALLOWED_SIZES } ?: DEFAULT_SIZE
            seed = obj.getString("seed")
            seedWasRandom = obj.optBoolean("rnd", true)
            moves = obj.getInt("moves")
            elapsedSeconds = obj.optLong("elapsed", 0L)
            solved = obj.getBoolean("solved")
            rewardClaimed = obj.optBoolean("rewarded", false)

            val na = obj.getJSONArray("nodes")
            nodes = (0 until na.length()).map { i -> na.getJSONObject(i).let { n ->
                SbNode(n.getInt("id"), n.getInt("x"), n.getInt("y"), n.getInt("req"))
            }}

            val ea = obj.getJSONArray("edges")
            edges = (0 until ea.length()).map { i -> ea.getJSONObject(i).let { e ->
                SbEdge(e.getInt("f"), e.getInt("t"), e.getString("k"),
                    e.getDouble("x1").toFloat(), e.getDouble("y1").toFloat(),
                    e.getDouble("x2").toFloat(), e.getDouble("y2").toFloat(), e.getString("o"))
            }}
            edgesByKey = edges.associateBy { it.key }

            val sa = obj.getJSONArray("sol")
            solution = (0 until sa.length()).map { i -> sa.getString(i) }.toSet()

            val bo = obj.getJSONObject("br")
            bridges = mutableMapOf<String, Int>().also { m ->
                for (k in bo.keys()) { val v = bo.getInt(k); if (v > 0) m[k] = v }
            }

            selectedNodeId = null
            true
        } catch (_: Exception) { false }
    }
}
