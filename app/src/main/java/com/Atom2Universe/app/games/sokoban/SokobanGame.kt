package com.Atom2Universe.app.games.sokoban

/** Un coup joué, mémorisé pour l'annulation. [boxFrom] vaut -1 si rien n'a été poussé. */
private data class SokobanMove(val playerFrom: Int, val boxFrom: Int, val boxTo: Int)

/**
 * État runtime d'une partie : position du joueur, caisses, compteurs, et pile
 * d'annulation. Indépendant de la vue et du générateur.
 */
class SokobanGame {

    var difficulty: SokobanDifficulty = SokobanDifficulty.EASY

    var puzzle: SokobanPuzzle? = null
        private set

    val boxes = HashSet<Int>()
    var player: Int = 0
        private set

    var moves: Int = 0
        private set
    var pushes: Int = 0
        private set

    private val history = ArrayDeque<SokobanMove>()

    val canUndo: Boolean get() = history.isNotEmpty()

    fun load(p: SokobanPuzzle) {
        puzzle = p
        boxes.clear()
        boxes.addAll(p.boxes)
        player = p.player
        moves = 0
        pushes = 0
        history.clear()
    }

    fun reset() {
        puzzle?.let { load(it) }
    }

    private fun neighbor(index: Int, dir: SokobanDir): Int {
        val p = puzzle ?: return -1
        val x = index % p.width + dir.dx
        val y = index / p.width + dir.dy
        if (x < 0 || x >= p.width || y < 0 || y >= p.height) return -1
        return y * p.width + x
    }

    /** Tente un déplacement. Retourne true si l'état a changé. */
    fun move(dir: SokobanDir): Boolean {
        val p = puzzle ?: return false
        val target = neighbor(player, dir)
        if (target == -1 || p.isWall(target)) return false

        if (target in boxes) {
            val beyond = neighbor(target, dir)
            if (beyond == -1 || p.isWall(beyond) || beyond in boxes) return false
            boxes.remove(target)
            boxes.add(beyond)
            history.addLast(SokobanMove(player, target, beyond))
            player = target
            moves++
            pushes++
        } else {
            history.addLast(SokobanMove(player, -1, -1))
            player = target
            moves++
        }
        return true
    }

    fun undo(): Boolean {
        val last = history.removeLastOrNull() ?: return false
        if (last.boxFrom != -1) {
            boxes.remove(last.boxTo)
            boxes.add(last.boxFrom)
            pushes--
        }
        player = last.playerFrom
        moves--
        return true
    }

    fun isSolved(): Boolean {
        val p = puzzle ?: return false
        return boxes == p.goals
    }
}
