package com.Atom2Universe.app.games.sokoban

import android.content.Context

/** Un niveau de la campagne, fabriqué sur PC (`SokobanForgeBench`) et rangé dans les assets. */
class SokobanLevel(
    /** Numéro affiché, à partir de 1. */
    val number: Int,
    /** Chapitre, à partir de 1. */
    val chapter: Int,
    val puzzle: SokobanPuzzle
)

/** Les niveaux de `assets/sokoban/levels.txt`, lus une fois. */
object SokobanLevels {
    const val ASSET = "sokoban/levels.txt"

    @Volatile private var cache: List<SokobanLevel>? = null

    fun all(context: Context): List<SokobanLevel> {
        cache?.let { return it }
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        return fromText(text).also { cache = it }
    }

    fun fromText(text: String): List<SokobanLevel> =
        SokobanXsb.parse(text).mapIndexed { index, (puzzle, meta) ->
            SokobanLevel(index + 1, meta["chapter"]?.toIntOrNull() ?: 1, puzzle)
        }
}

/**
 * Le format texte habituel du Sokoban : `#` mur, `.` quai, `$` caisse, `*` caisse sur quai,
 * `@` robot, `+` robot sur quai, espace = sol (ou rien, hors des murs). Une ligne `;` porte
 * des champs `clé=valeur` ; une ligne vide sépare deux niveaux.
 */
object SokobanXsb {

    fun format(p: SokobanPuzzle, meta: List<Pair<String, Any>>): String {
        val out = StringBuilder()
        out.append("; ").append(meta.joinToString(" ") { (k, v) -> "$k=$v" }).append('\n')
        if (p.solution.isNotEmpty()) out.append("; solution=").append(p.solution).append('\n')
        val boxes = p.boxes.toSet()
        for (y in 0 until p.height) {
            val row = StringBuilder()
            for (x in 0 until p.width) {
                val i = y * p.width + x
                row.append(
                    if (p.isWall(i)) {
                        if (touchesFloor(p, x, y)) '#' else ' '
                    } else {
                        val goal = i in p.goals
                        when {
                            i in boxes -> if (goal) '*' else '$'
                            i == p.player -> if (goal) '+' else '@'
                            goal -> '.'
                            else -> ' '
                        }
                    }
                )
            }
            out.append(row.trimEnd()).append('\n')
        }
        return out.toString()
    }

    private fun touchesFloor(p: SokobanPuzzle, x: Int, y: Int): Boolean {
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx; val ny = y + dy
            if (nx < 0 || ny < 0 || nx >= p.width || ny >= p.height) continue
            if (!p.isWall(ny * p.width + nx)) return true
        }
        return false
    }

    fun parse(text: String): List<Pair<SokobanPuzzle, Map<String, String>>> {
        val result = ArrayList<Pair<SokobanPuzzle, Map<String, String>>>()
        val meta = HashMap<String, String>()
        val rows = ArrayList<String>()
        fun flush() {
            if (rows.isNotEmpty()) result.add(build(rows, meta) to HashMap(meta))
            rows.clear(); meta.clear()
        }
        for (raw in text.lines()) {
            val line = raw.trimEnd('\r')
            when {
                line.startsWith(";") -> {
                    if (rows.isNotEmpty()) flush()
                    for (token in line.substring(1).trim().split(' ')) {
                        val eq = token.indexOf('=')
                        if (eq > 0) meta[token.substring(0, eq)] = token.substring(eq + 1)
                    }
                }
                line.isBlank() -> flush()
                else -> rows.add(line)
            }
        }
        flush()
        return result
    }

    private fun build(rows: List<String>, meta: Map<String, String>): SokobanPuzzle {
        val h = rows.size
        val w = rows.maxOf { it.length }
        fun at(x: Int, y: Int) = rows[y].getOrElse(x) { ' ' }
        val goals = HashSet<Int>()
        val boxes = ArrayList<Int>()
        var player = -1
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            when (at(x, y)) {
                '.' -> goals.add(i)
                '$' -> boxes.add(i)
                '*' -> { goals.add(i); boxes.add(i) }
                '@' -> player = i
                '+' -> { goals.add(i); player = i }
            }
        }
        require(player >= 0) { "niveau sans robot" }
        // Le sol : ce que le robot atteint sans traverser de mur. Le reste (dehors) compte comme mur.
        val floor = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        stack.addLast(player); floor[player] = true
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            val cx = c % w; val cy = c / w
            for (dir in SokobanDir.entries) {
                val nx = cx + dir.dx; val ny = cy + dir.dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val n = ny * w + nx
                if (floor[n] || at(nx, ny) == '#') continue
                floor[n] = true; stack.addLast(n)
            }
        }
        val walls = (0 until w * h).filter { !floor[it] }.toSet()
        return SokobanPuzzle(
            width = w,
            height = h,
            walls = walls,
            goals = goals,
            boxes = boxes.sorted(),
            player = player,
            optimalPushes = meta["pushes"]?.toIntOrNull() ?: 0,
            solution = meta["solution"] ?: ""
        )
    }
}
