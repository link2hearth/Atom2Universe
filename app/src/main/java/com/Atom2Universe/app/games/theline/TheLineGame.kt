package com.Atom2Universe.app.games.theline

/** Un tracé : la piste de cuivre (mode Piste) ou un fil de couleur (mode Câblage). */
class PathState(
    val colorValue: Int,
    /** Les deux bouts : départ et arrivée en mode Piste, les deux connecteurs en Câblage. */
    val endpoints: List<TLCoord>,
    val sequence: MutableList<TLCoord> = mutableListOf(),
    /** Le tracé a atteint son autre bout. */
    var complete: Boolean = false
)

/**
 * Circuit (ancien « The Line ») : les règles du tracé, sans rien d'Android.
 *
 * **Piste** : une seule piste de cuivre part de la borne 1, traverse les bornes dans
 * l'ordre et finit sur la dernière, en passant par toutes les cases libres. **Câblage** :
 * chaque paire de connecteurs se relie par un fil, les fils ne se croisent pas et
 * remplissent la carte.
 *
 * Toute solution qui respecte les règles gagne, pas seulement celle du générateur : il
 * n'y a pas de résolveur, et aucune grille n'a « la » bonne réponse cachée.
 */
class TheLineGame {

    /** Ce que devient un pas de doigt vers une case voisine. */
    enum class Step { ADVANCED, RETRACTED, REFUSED, IGNORED }

    var mode: TheLineMode = TheLineMode.SINGLE
    var difficulty: TheLineDifficulty = TheLineDifficulty.EASY

    /** Niveau atteint pour chaque couple mode × difficulté, à partir de 1. */
    val levels = IntArray(TheLineMode.entries.size * TheLineDifficulty.entries.size) { 1 }

    var puzzle: TheLinePuzzle? = null
        private set
    val paths = mutableListOf<PathState>()
    /** Les neutrinos de cette grille ont été versés (pour ne pas payer deux fois après une reprise). */
    var rewardClaimed = false

    val currentLevel: Int get() = levels[levelSlot(mode, difficulty)]

    private fun levelSlot(m: TheLineMode, d: TheLineDifficulty) = m.ordinal * TheLineDifficulty.entries.size + d.ordinal

    val freeCells: Int get() = puzzle?.let { it.width * it.height - it.blockedIndices.size } ?: 0
    val cellsRemaining: Int get() = freeCells - paths.sumOf { it.sequence.size }

    fun loadPuzzle(p: TheLinePuzzle) {
        puzzle = p
        paths.clear()
        rewardClaimed = false
        if (p.mode == TheLineMode.MULTI) {
            for (seg in p.segments) paths.add(PathState(seg.colorValue, listOf(seg.start, seg.end)))
        } else {
            val ends = if (p.checkpoints.size >= 2) p.checkpoints.first() to p.checkpoints.last()
            else p.endpoints ?: (p.path.first() to p.path.last())
            paths.add(PathState(THE_LINE_SINGLE_COLOR, listOf(ends.first, ends.second)))
        }
    }

    fun isComplete(): Boolean = puzzle != null && cellsRemaining == 0 && paths.all { it.complete }

    fun onLevelCompleted() {
        levels[levelSlot(mode, difficulty)]++
    }

    fun clearAll() {
        for (p in paths) { p.sequence.clear(); p.complete = false }
    }

    // ── Lecture du plateau ────────────────────────────────────────────────────────
    fun isBlocked(x: Int, y: Int): Boolean {
        val p = puzzle ?: return true
        if (x !in 0 until p.width || y !in 0 until p.height) return true
        return (y * p.width + x) in p.blockedIndices
    }

    fun pathAt(c: TLCoord): Int = paths.indexOfFirst { c in it.sequence }

    fun endpointOwner(c: TLCoord): Int = paths.indexOfFirst { c in it.endpoints }

    /** Numéro de borne (1, 2, 3…) de cette case en mode Piste, 0 si ce n'est pas une borne. */
    fun checkpointNumber(c: TLCoord): Int {
        val p = puzzle ?: return 0
        return p.checkpoints.indexOf(c) + 1
    }

    /** Le prochain numéro que la piste doit atteindre (mode Piste). */
    fun nextCheckpoint(): Int {
        val p = puzzle ?: return 0
        val seq = paths.firstOrNull()?.sequence ?: return 1
        var n = 0
        for (c in seq) if (c in p.checkpoints) n++
        return n + 1
    }

    // ── Gestes ────────────────────────────────────────────────────────────────────
    /**
     * Le doigt se pose sur [c] : rend l'indice du tracé qu'il tient désormais, ou -1.
     *
     * Sur un bout (la borne 1, un connecteur), le tracé repart de zéro depuis ce bout. Sur
     * une case déjà tracée, il est coupé juste après elle et le doigt reprend de là.
     */
    fun begin(c: TLCoord): Int {
        val p = puzzle ?: return -1
        if (isBlocked(c.x, c.y)) return -1
        val owner = pathAt(c)
        if (owner >= 0) {
            val path = paths[owner]
            val startsHere = path.sequence.firstOrNull() == c
            if (!startsHere || p.mode == TheLineMode.SINGLE) {
                truncateAfter(owner, path.sequence.indexOf(c))
                return owner
            }
        }
        val end = endpointOwner(c)
        if (end < 0) return -1
        // En mode Piste, seule la borne 1 lance la piste : la dernière est l'arrivée.
        if (p.mode == TheLineMode.SINGLE && c != paths[end].endpoints[0]) return -1
        paths[end].sequence.clear()
        paths[end].sequence.add(c)
        paths[end].complete = false
        return end
    }

    /** Le doigt tenant le tracé [id] glisse sur la case voisine [c]. */
    fun extend(id: Int, c: TLCoord): Step {
        val p = puzzle ?: return Step.IGNORED
        val path = paths.getOrNull(id) ?: return Step.IGNORED
        val last = path.sequence.lastOrNull() ?: return Step.IGNORED
        if (c == last) return Step.IGNORED
        if (kotlin.math.abs(c.x - last.x) + kotlin.math.abs(c.y - last.y) != 1) return Step.IGNORED
        if (isBlocked(c.x, c.y)) return Step.REFUSED

        // Revenir sur son propre tracé le raccourcit jusque-là.
        val own = path.sequence.indexOf(c)
        if (own >= 0) {
            truncateAfter(id, own)
            return Step.RETRACTED
        }
        // Arrivé au bout, on ne va pas plus loin : il faut d'abord reculer.
        if (path.complete) return Step.REFUSED

        val otherEnd = if (path.sequence.first() == path.endpoints[0]) path.endpoints[1] else path.endpoints[0]
        val endOwner = endpointOwner(c)
        if (endOwner >= 0 && endOwner != id) return Step.REFUSED

        if (p.mode == TheLineMode.SINGLE) {
            val number = checkpointNumber(c)
            if (number > 0 && number != nextCheckpoint()) return Step.REFUSED
        } else {
            // Un fil qui passe sur un autre le coupe, comme on tire un câble à travers le fouillis.
            val other = pathAt(c)
            if (other >= 0 && other != id) truncateAfter(other, paths[other].sequence.indexOf(c) - 1)
        }

        path.sequence.add(c)
        if (c == otherEnd) path.complete = true
        return Step.ADVANCED
    }

    /** Garde le tracé jusqu'à l'indice [keep] inclus (-1 : le vide entièrement). */
    private fun truncateAfter(id: Int, keep: Int) {
        val seq = paths[id].sequence
        while (seq.size > keep + 1) seq.removeAt(seq.lastIndex)
        paths[id].complete = seq.size >= 2 && seq.last() in paths[id].endpoints && seq.last() != seq.first()
    }

    // ── Sauvegarde ────────────────────────────────────────────────────────────────
    /**
     * Une ligne compacte : `2;mode;difficulté;largeur;hauteur;trous;chemin;bornes;fils;tracés;payé`.
     * Les cases s'écrivent par leur indice (y × largeur + x), les listes séparées par des
     * virgules, les tracés entre eux par des barres. Pas de JSON : la ligne se relit aussi
     * dans les tests, où `org.json` n'existe pas.
     */
    fun serialize(): String {
        val p = puzzle ?: return ""
        fun idx(c: TLCoord) = c.y * p.width + c.x
        val sb = StringBuilder()
        sb.append("2;").append(p.mode.name).append(';').append(difficulty.name)
            .append(';').append(p.width).append(';').append(p.height)
            .append(';').append(p.blockedIndices.sorted().joinToString(","))
            .append(';').append(p.path.joinToString(",") { idx(it).toString() })
            .append(';').append(p.checkpoints.joinToString(",") { idx(it).toString() })
            .append(';').append(p.segments.joinToString(",") { it.cells.size.toString() })
            .append(';').append(paths.joinToString("/") { s -> s.sequence.joinToString(",") { idx(it).toString() } })
            .append(';').append(if (rewardClaimed) 1 else 0)
        return sb.toString()
    }

    fun deserialize(line: String): Boolean {
        val f = line.split(';')
        if (f.size != 11 || f[0] != "2") return false
        return try {
            val m = TheLineMode.valueOf(f[1])
            val d = TheLineDifficulty.valueOf(f[2])
            val w = f[3].toInt(); val h = f[4].toInt()
            fun ints(s: String) = if (s.isEmpty()) emptyList() else s.split(',').map { it.toInt() }
            fun coord(i: Int) = TLCoord(i % w, i / w)
            val blocked = ints(f[5]).toSet()
            val path = ints(f[6]).map(::coord)
            if (path.isEmpty()) return false
            val checkpoints = ints(f[7]).map(::coord)
            val segments = mutableListOf<TLSegment>()
            var cursor = 0
            ints(f[8]).forEachIndexed { i, len ->
                val cells = path.subList(cursor, cursor + len)
                segments.add(TLSegment(THE_LINE_COLORS[i % THE_LINE_COLORS.size], cells, cells.first(), cells.last()))
                cursor += len
            }
            val puzzle = TheLinePuzzle(
                m, w, h, blocked, path,
                endpoints = if (m == TheLineMode.SINGLE) path.first() to path.last() else null,
                segments = segments, checkpoints = checkpoints
            )
            mode = m; difficulty = d
            loadPuzzle(puzzle)
            val traces = f[9].split('/')
            traces.forEachIndexed { i, s -> paths.getOrNull(i)?.sequence?.addAll(ints(s).map(::coord)) }
            for (i in paths.indices) {
                val seq = paths[i].sequence
                if (seq.isNotEmpty()) truncateAfter(i, seq.lastIndex)
            }
            rewardClaimed = f[10] == "1"
            true
        } catch (_: RuntimeException) {
            false
        }
    }
}
