package com.Atom2Universe.app.science.parentes

import java.text.Normalizer
import java.util.Locale

/** Source topology. Rendering may hide nodes but must never change this graph. */
data class LifeNode(
    val id: String,
    val parent: String?,
    val scientific: String,
    val species: Boolean,
    val labelKey: String,
    val support: List<String>,
    val conflicts: List<String>,
    val taxSources: List<String>
)

class LifeTree(val nodes: Map<String, LifeNode>, val root: String, val groups: Set<String>) {
    val children: Map<String, List<String>> = nodes.values.filter { it.parent != null }
        .groupBy({ it.parent!! }, { it.id })
    val species = nodes.values.filter { it.species }
    private val depths = HashMap<String, Int>(nodes.size)
    private val leafRanges = HashMap<String, IntRange>(nodes.size)
    private val orderedLeaves = ArrayList<LifeNode>()

    init {
        require(nodes.isNotEmpty() && nodes[root]?.parent == null)
        require(nodes.values.count { it.parent == null } == 1)
        require(groups.all { it in nodes })
        // One traversal instead of copying a complete ancestry for every node.
        // Species occupy contiguous intervals, so group counts and lists are cheap.
        val pending = java.util.ArrayDeque<Pair<String, Boolean>>()
        pending.addLast(root to false)
        val starts = HashMap<String, Int>(nodes.size)
        while (pending.isNotEmpty()) {
            val (id, leaving) = pending.removeLast()
            if (leaving) {
                leafRanges[id] = starts.getValue(id) until orderedLeaves.size
            } else {
                require(id !in depths) { "Cycle at $id" }
                val node = nodes.getValue(id)
                depths[id] = node.parent?.let { depths.getValue(it) + 1 } ?: 0
                starts[id] = orderedLeaves.size
                if (node.species) orderedLeaves.add(node)
                pending.addLast(id to true)
                children[id].orEmpty().asReversed().forEach { pending.addLast(it to false) }
            }
        }
        require(depths.size == nodes.size) { "Disconnected, cyclic or missing-parent nodes" }
        require(species.all { children[it.id].isNullOrEmpty() }) { "Selected species must be terminal" }
    }

    fun ancestors(id: String): List<String> = buildList {
        var current: String? = id
        while (current != null) {
            add(current)
            current = nodes.getValue(current).parent
        }
    }
    fun lca(a: String, b: String): String {
        var left = a
        var right = b
        var leftDepth = depths.getValue(left)
        var rightDepth = depths.getValue(right)
        while (leftDepth > rightDepth) { left = nodes.getValue(left).parent!!; leftDepth-- }
        while (rightDepth > leftDepth) { right = nodes.getValue(right).parent!!; rightDepth-- }
        while (left != right) { left = nodes.getValue(left).parent!!; right = nodes.getValue(right).parent!! }
        return left
    }
    fun pathTo(id: String, ancestor: String): List<String> {
        val path = ancestors(id)
        val end = path.indexOf(ancestor)
        require(end >= 0) { "Not an ancestor" }
        return path.take(end + 1)
    }
    fun descendants(id: String): List<LifeNode> {
        val range = leafRanges.getValue(id)
        return orderedLeaves.subList(range.first, range.last + 1)
    }

    fun isBrowsable(id: String): Boolean = id in groups ||
        (nodes.getValue(id).scientific.isNotEmpty() && children[id].orEmpty().size > 1)

    /** Skip unary paths only for presentation. Bifurcations, including unnamed ones, survive. */
    fun displayChildren(id: String): List<String> = children[id].orEmpty().map { child ->
        var next = child
        while (next !in groups && children[next]?.size == 1) next = children.getValue(next).single()
        next
    }

    fun displayParent(id: String): String? = ancestors(id).drop(1).firstOrNull {
        it == root || isBrowsable(it)
    }

    /** Full selected subtree: only unary intermediate nodes are contracted. All
     * branching points survive, but unnamed junctions need not carry a UI label. */
    fun atlas(focus: String, tips: List<String> = descendants(focus).map { it.id }): TreeScene {
        val included = tips.flatMap { pathTo(it, focus) }.toSet() + focus
        val keep = included.filter { id ->
            id == focus || nodes.getValue(id).species || id in groups ||
                children[id].orEmpty().count { it in included } > 1
        }.toSet()
        val edges = linkedMapOf<String, String?>()
        fun visit(id: String, parent: String?) {
            val nextParent = if (id in keep) { edges[id] = parent; id } else parent
            children[id].orEmpty().filter { it in included }.forEach { visit(it, nextParent) }
        }
        visit(focus, null)
        return TreeScene(edges)
    }

    fun explore(focus: String, levels: Int = 2): TreeScene {
        val edges = linkedMapOf<String, String?>()
        fun visit(id: String, parent: String?, depth: Int) {
            edges[id] = parent
            if (depth < levels) displayChildren(id).forEach { visit(it, id, depth + 1) }
        }
        visit(focus, null, 0)
        return TreeScene(edges)
    }

    fun compare(a: String, b: String): TreeScene {
        val common = lca(a, b)
        val first = pathTo(a, common).toSet()
        val second = pathTo(b, common).toSet()
        val keep = (first + second).filter { it == common || it == a || it == b || it in groups }.toSet()
        val edges = linkedMapOf<String, String?>()
        fun append(id: String) {
            val path = pathTo(id, common).filter { it in keep }.reversed()
            path.forEachIndexed { index, n -> edges[n] = path.getOrNull(index - 1) }
        }
        append(a)
        append(b)
        return TreeScene(edges, first, second, common, a, b)
    }

    companion object {
        private val combiningMarks = Regex("\\p{M}+")
        fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "").lowercase(Locale.ROOT).trim()
    }
}

data class TreeScene(
    val parents: Map<String, String?>,
    val first: Set<String> = emptySet(),
    val second: Set<String> = emptySet(),
    val common: String? = null,
    val a: String? = null,
    val b: String? = null
)

/** Independent layout in logical units; branch lengths are deliberately not dates. */
object TreeLayout {
    data class Point(val x: Float, val y: Float)
    data class RadialPoint(val x: Float, val y: Float, val angle: Float, val radius: Float)

    /** Circular cladogram: angles encode display order only, never time or distance.
     * Every source fork in the scene is retained, including polytomies. */
    fun radial(scene: TreeScene): Map<String, RadialPoint> {
        val children = scene.parents.entries.filter { it.value != null }.groupBy({ it.value!! }, { it.key })
        val root = scene.parents.entries.single { it.value == null }.key
        val heights = mutableMapOf<String, Int>()
        val spans = mutableMapOf<String, Pair<Int, Int>>()
        var leaf = 0
        fun visit(id: String) {
            val start = leaf
            children[id].orEmpty().forEach { visit(it) }
            if (children[id].isNullOrEmpty()) leaf++
            spans[id] = start to (leaf - 1)
            heights[id] = children[id].orEmpty().maxOfOrNull { heights.getValue(it) + 1 } ?: 0
        }
        visit(root)
        val maxHeight = heights.getValue(root).coerceAtLeast(1)
        return scene.parents.keys.associateWith { id ->
            val (first, last) = spans.getValue(id)
            val angle = (((first + last + 1.0) / 2.0 / leaf) * Math.PI * 2.0 - Math.PI / 2.0).toFloat()
            val radius = when {
                id == root -> 0f
                children[id].isNullOrEmpty() -> 640f
                else -> 640f * (maxHeight - heights.getValue(id)) / maxHeight
            }
            RadialPoint(kotlin.math.cos(angle) * radius, kotlin.math.sin(angle) * radius, angle, radius)
        }
    }
    fun place(scene: TreeScene, rowHeight: Float = 76f): Map<String, Point> {
        val children = scene.parents.entries.filter { it.value != null }.groupBy({ it.value!! }, { it.key })
        val result = linkedMapOf<String, Point>()
        var row = 0
        fun visit(id: String, depth: Int): Float {
            val ys = children[id].orEmpty().map { visit(it, depth + 1) }
            val y = if (ys.isEmpty()) row++ * rowHeight else (ys.first() + ys.last()) / 2f
            result[id] = Point(depth * 260f, y)
            return y
        }
        visit(scene.parents.entries.single { it.value == null }.key, 0)
        // Align terminals so that panning vertically browses the species column.
        val lastColumn = result.values.maxOf { it.x }
        result.keys.toList().filter { children[it].isNullOrEmpty() }.forEach { id ->
            result[id] = result.getValue(id).copy(x = lastColumn)
        }
        return result
    }
}
