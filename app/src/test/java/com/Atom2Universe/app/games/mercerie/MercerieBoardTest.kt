package com.Atom2Universe.app.games.mercerie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Garde-fous du moteur : les cartes sont valides, les bonus naissent et partent comme prévu. */
class MercerieBoardTest {

    private fun level(vararg map: String, goal: Goal = Goal(), moves: Int = 30, portals: List<Portal> = emptyList(), timer: Int = 0) =
        MercerieLevel(1, moves, 6, map.toList(), goal, intArrayOf(100, 200, 300), portals = portals, timer = timer, timers = if (timer > 0) 1 else 0)

    /** Plateau 6 × 6 sans aucun alignement possible, fait des couleurs 1 à 5 : la couleur 0 sert aux essais. */
    private fun quiet(
        vararg map: String = Array(6) { "......" }, goal: Goal = Goal(), portals: List<Portal> = emptyList(), timer: Int = 0
    ): MercerieBoard {
        val b = MercerieBoard(level(*map, goal = goal, portals = portals, timer = timer), 7L)
        for (r in 0 until b.rows) for (c in 0 until b.cols) b.piece[b.idx(r, c)]?.let {
            it.color = (r * 2 + c) % 5 + 1; it.kind = Kind.NORMAL; it.pinned = false
        }
        return b
    }

    private fun MercerieBoard.set(r: Int, c: Int, color: Int = 0, kind: Kind = Kind.NORMAL) {
        val p = piece[idx(r, c)]!!
        p.color = color; p.kind = kind
    }

    private fun MercerieBoard.settle(): List<Step> {
        val steps = ArrayList<Step>()
        while (true) {
            steps += advance() ?: break
            assertTrue("résolution sans fin", steps.size < 400)
        }
        return steps
    }

    @Test
    fun levelsAreValid() {
        assertTrue(MercerieLevels.handmade.size >= 40)
        for (l in MercerieLevels.handmade) {
            val tag = "niveau ${l.number}"
            assertTrue(tag, l.cols in 5..9 && l.rows in 5..9)
            assertTrue(tag, l.map.all { it.length == l.cols })
            assertTrue(tag, l.colors in 4..6)
            assertTrue(tag, l.moves in 6..60)
            if (l.number > MercerieLevels.HANDMADE) assertTrue(tag, l.moves >= l.goal.minMoves)
            assertTrue(tag, l.stars[0] < l.stars[1] && l.stars[1] < l.stars[2])
            for (c in l.colors until MercerieBoard.COLORS) assertEquals(tag, 0, l.goal.collect[c])
            val b = MercerieBoard(l, 1L)
            assertEquals(tag, l.goal.patches, b.patchesLeft > 0)
            if (l.goal.dust) assertTrue(tag, b.dustLeft > 0)
            for (p in l.portals) assertTrue(tag, b.cell[b.idx(p.r1, p.c1)] && b.cell[b.idx(p.r2, p.c2)])
            assertFalse(tag, b.hasMatches())
            assertTrue(tag, b.hasMove())
            assertFalse(tag, b.goalMet)
        }
    }

    @Test
    fun randomPlayKeepsTheBoardSound() {
        for (l in MercerieLevels.handmade) {
            val rnd = Random(l.number)
            val b = MercerieBoard(l, l.number * 31L)
            var last = 0
            while (!b.finished) {
                val moves = b.allMoves()
                if (moves.isEmpty()) { b.settle(); if (b.allMoves().isEmpty()) break else continue }
                val m = moves[rnd.nextInt(moves.size)]
                assertTrue(b.swap(m[0], m[1]))
                for (s in b.settle()) if (s is FallStep) for (f in s.falls) assertTrue(f.path.size >= 2)
                assertFalse("niveau ${l.number}", b.hasMatches())
                assertEquals("niveau ${l.number}", 0, b.starvedCells())
                assertTrue(b.score >= last)
                last = b.score
                val ids = b.piece.filterNotNull().map { it.id }
                assertEquals(ids.size, ids.toSet().size)
            }
            if (b.goalMet) { b.startBonus(); b.settle() }
        }
    }

    /** Un coup ne déclenche pas de cascades sans fin, même à quatre couleurs (niveau 1). */
    @Test
    fun cascadesStayShort() {
        var moves = 0
        var clears = 0
        var long = 0
        for (seed in 1L..20L) {
            val rnd = Random(seed)
            val b = MercerieBoard(MercerieLevels.get(1), seed)
            while (!b.finished) {
                val all = b.allMoves()
                if (all.isEmpty()) { b.settle(); if (b.allMoves().isEmpty()) break else continue }
                val m = all[rnd.nextInt(all.size)]
                b.swap(m[0], m[1])
                val n = b.settle().count { it is ClearStep }
                moves++; clears += n
                if (n > 4) long++
            }
        }
        assertTrue("moyenne ${clears.toFloat() / moves} temps par coup", clears.toFloat() / moves < 2f)
        // Une longue cascade reste possible (un pompon compte pour deux temps), mais rare.
        assertTrue("$long longues cascades sur $moves coups", long * 20 < moves)
    }

    /** Faisabilité : le robot gagne chaque niveau au moins deux fois sur dix avec les coups prévus. */
    @Test
    fun robotCanWinEveryLevel() {
        val report = StringBuilder()
        val stuck = ArrayList<Int>()
        for (l in MercerieLevels.handmade) {
            val games = (0 until 10).map { MercerieRobot.play(l, 77L * l.number + it) }
            val wins = games.count { it.won }
            if (wins < 2) stuck += l.number
            report.append("niveau ${l.number} (${l.moves} coups) : gagné $wins/10")
            for (g in games) report.append("  [${if (g.won) "ok" else "raté"} ${g.used} coups, ${g.score} pts, ${g.stars}*]")
            val scores = games.filter { it.won }.map { it.score }.sorted()
            if (scores.isNotEmpty()) report.append("  médiane ${scores[scores.size / 2]}, min ${scores[0]}")
            report.append('\n')
        }
        java.io.File("build/outputs/mercerie/feasibility.txt").apply { parentFile.mkdirs(); writeText(report.toString()) }
        assertTrue("niveaux jamais gagnés par le robot : $stuck\n$report", stuck.isEmpty())
    }

    @Test
    fun fourInARowMakesALine() {
        val b = quiet()
        b.set(5, 0); b.set(5, 1); b.set(5, 3); b.set(4, 2)
        assertTrue(b.swap(b.idx(4, 2), b.idx(5, 2)))
        val step = b.advance() as ClearStep
        assertEquals(4, step.gone.size)
        assertEquals(1, step.born.size)
        assertEquals(Kind.LINE_V, step.born[0].piece.kind)
        assertEquals(b.idx(5, 2), step.born[0].cell)
        assertEquals(29, b.movesLeft)
    }

    @Test
    fun fiveInARowMakesARainbow() {
        val b = quiet()
        b.set(5, 0); b.set(5, 1); b.set(5, 3); b.set(5, 4); b.set(4, 2)
        assertTrue(b.swap(b.idx(4, 2), b.idx(5, 2)))
        val step = b.advance() as ClearStep
        assertEquals(Kind.RAINBOW, step.born.single().piece.kind)
    }

    @Test
    fun cornerMakesABomb() {
        val b = quiet()
        b.set(5, 0); b.set(5, 1); b.set(3, 2); b.set(4, 2); b.set(5, 3)
        assertTrue(b.swap(b.idx(5, 3), b.idx(5, 2)))
        val step = b.advance() as ClearStep
        assertEquals(Kind.BOMB, step.born.single().piece.kind)
        assertEquals(b.idx(5, 2), step.born.single().cell)
    }

    @Test
    fun uselessSwapIsRefused() {
        val b = quiet()
        assertFalse(b.swap(b.idx(0, 0), b.idx(0, 1)))
        assertEquals(30, b.movesLeft)
    }

    @Test
    fun lineEmptiesItsRow() {
        val b = quiet()
        b.set(5, 0, kind = Kind.LINE_H); b.set(5, 1); b.set(4, 2)
        assertTrue(b.swap(b.idx(4, 2), b.idx(5, 2)))
        val step = b.advance() as ClearStep
        val cells = step.gone.map { it.cell }.toSet()
        for (c in 0 until 6) assertTrue(b.idx(5, c) in cells)
    }

    @Test
    fun bombGoesOffTwice() {
        val b = quiet()
        b.set(5, 1, kind = Kind.BOMB); b.set(5, 0); b.set(4, 2)
        val bomb = b.piece[b.idx(5, 1)]!!
        assertTrue(b.swap(b.idx(4, 2), b.idx(5, 2)))
        val first = b.advance() as ClearStep
        assertTrue(first.gone.none { it.piece === bomb })
        assertTrue(bomb.armed)
        assertTrue(first.gone.any { it.cell == b.idx(4, 0) })
        val later = b.settle().filterIsInstance<ClearStep>()
        assertTrue(later.any { s -> s.gone.any { it.piece === bomb } })
    }

    @Test
    fun rainbowTakesAWholeColor() {
        val b = quiet()
        b.set(5, 2, color = -1, kind = Kind.RAINBOW)
        val color = b.piece[b.idx(5, 3)]!!.color
        assertTrue(b.swap(b.idx(5, 2), b.idx(5, 3)))
        b.advance() as ClearStep
        assertTrue(b.piece.none { it != null && it.color == color })
    }

    @Test
    fun twoLinesMakeACross() {
        val b = quiet()
        b.set(2, 2, kind = Kind.LINE_H); b.set(2, 3, color = 1, kind = Kind.LINE_V)
        assertTrue(b.swap(b.idx(2, 2), b.idx(2, 3)))
        val cells = (b.advance() as ClearStep).gone.map { it.cell }.toSet()
        for (k in 0 until 6) { assertTrue(b.idx(2, k) in cells); assertTrue(b.idx(k, 3) in cells) }
    }

    @Test
    fun matchNextToAKnotLoosensIt() {
        val b = quiet("......", "......", "......", "......", "...k..", "......")
        b.set(5, 0); b.set(5, 1); b.set(4, 2)
        assertEquals(1, b.knotsLeft)
        assertTrue(b.swap(b.idx(4, 2), b.idx(5, 2)))
        b.advance()
        // Le nœud (4,3) ne touche l'alignement que par un coin : il tient bon.
        assertEquals(1, b.knotsLeft)
        val c = quiet("......", "......", "......", "......", "......", "...k..")
        c.set(5, 0); c.set(5, 1); c.set(4, 2)
        assertTrue(c.swap(c.idx(4, 2), c.idx(5, 2)))
        c.advance()
        assertEquals(0, c.knotsLeft)
    }

    @Test
    fun patchAndPinComeOff() {
        val b = quiet("......", "......", "......", "......", "......", "p.....", goal = Goal(patches = true))
        b.piece[b.idx(5, 1)]!!.pinned = true
        b.set(5, 0); b.set(5, 1); b.set(4, 2)
        assertEquals(1, b.patchesLeft)
        assertTrue(b.swap(b.idx(4, 2), b.idx(5, 2)))
        b.advance()
        assertEquals(0, b.patchesLeft)
        assertNotNull(b.piece[b.idx(5, 1)])
        assertFalse(b.piece[b.idx(5, 1)]!!.pinned)
        assertTrue(b.goalMet)
    }

    @Test
    fun thimbleReachesTheBasket() {
        val b = quiet()
        b.set(4, 0, color = -1, kind = Kind.THIMBLE)
        b.set(5, 0); b.set(5, 1); b.set(4, 2)
        assertTrue(b.swap(b.idx(4, 2), b.idx(5, 2)))
        b.settle()
        assertEquals(1, b.thimblesDone)
    }

    @Test
    fun piecesSlideUnderAKnot() {
        val b = quiet(".....", ".....", "..k..", ".....", ".....")
        b.piece[b.idx(3, 2)] = null
        b.piece[b.idx(4, 2)] = null
        assertNotNull(b.fallNow())
        assertNotNull(b.piece[b.idx(3, 2)])
        assertNotNull(b.piece[b.idx(4, 2)])
        assertEquals(0, b.starvedCells())
    }

    @Test
    fun spareMovesBecomeLines() {
        val b = MercerieBoard(level("......", "......", "......", "......", "......", "......", moves = 5), 3L)
        val step = b.startBonus()
        assertNotNull(step)
        assertEquals(5, step!!.fx.count { it.type == FxType.TRANSFORM })
        assertTrue(b.bonus)
        assertEquals(0, b.movesLeft)
        b.settle()
        assertTrue(b.piece.none { it != null && it.kind != Kind.NORMAL })
    }

    @Test
    fun forkPlaysWithoutTouchingTheOriginal() {
        val b = MercerieBoard(MercerieLevels.get(4), 5L)
        val before = b.piece.map { it?.color }
        val m = b.findMove()!!
        val f = b.fork(9L)
        assertTrue(f.swap(m[0], m[1]))
        f.settle()
        assertEquals(before, b.piece.map { it?.color })
        assertEquals(MercerieLevels.get(4).moves, b.movesLeft)
    }

    @Test
    fun squareMakesAButterfly() {
        val b = quiet()
        b.set(4, 0); b.set(4, 1); b.set(5, 0); b.set(5, 2)
        assertTrue(b.swap(b.idx(5, 2), b.idx(5, 1)))
        val step = b.advance() as ClearStep
        assertEquals(4, step.gone.size)
        assertEquals(Kind.BUTTERFLY, step.born.single().piece.kind)
        assertEquals(b.idx(5, 1), step.born.single().cell)
    }

    @Test
    fun butterflyFliesToThePatch() {
        val b = quiet("......", "......", "......", "......", "......", "......", goal = Goal(patches = true))
        b.patch[b.idx(0, 5)] = 1
        b.set(5, 0); b.set(5, 1, kind = Kind.BUTTERFLY); b.set(4, 2)
        assertTrue(b.swap(b.idx(4, 2), b.idx(5, 2)))
        val step = b.advance() as ClearStep
        val fly = step.fx.single { it.type == FxType.FLY }
        assertEquals(b.idx(0, 5), fly.arg)
        assertTrue(step.gone.any { it.leave == Leave.FLY && it.cell == b.idx(5, 1) })
        assertEquals(0, b.patch[b.idx(0, 5)])
    }

    @Test
    fun dustSpreadsWhenLeftAlone() {
        val b = quiet("d.....", "......", "......", "......", "......", "......", goal = Goal(dust = true))
        assertEquals(1, b.dustLeft)
        b.set(5, 3); b.set(5, 4); b.set(4, 5)
        assertTrue(b.swap(b.idx(4, 5), b.idx(5, 5)))
        val steps = b.settle()
        assertTrue(steps.any { it is ClearStep && it.endOfMove && it.fx.any { f -> f.type == FxType.SPREAD } })
        assertEquals(2, b.dustLeft)
        assertEquals(0, b.starvedCells())
        // Un alignement juste à côté le chasse, et il ne s'étend pas ce coup-là.
        val c = quiet("d.....", "......", "......", "......", "......", "......", goal = Goal(dust = true))
        c.set(1, 0); c.set(1, 1); c.set(2, 2)
        assertTrue(c.swap(c.idx(2, 2), c.idx(1, 2)))
        c.settle()
        assertEquals(0, c.dustLeft)
        assertTrue(c.goalMet)
    }

    @Test
    fun timerRingsAtZero() {
        val b = quiet("t.....", "......", "......", "......", "......", "......", timer = 2)
        val clock = b.piece[b.idx(0, 0)]!!
        assertEquals(2, clock.timer)
        b.set(5, 3); b.set(5, 4); b.set(4, 5)
        assertTrue(b.swap(b.idx(4, 5), b.idx(5, 5)))
        b.settle()
        assertEquals(1, clock.timer)
        assertFalse(b.finished)
        b.set(3, 1); b.set(3, 2); b.set(2, 3)
        assertTrue(b.swap(b.idx(2, 3), b.idx(3, 3)))
        b.settle()
        assertTrue(b.timedOut)
        assertTrue(b.finished)
        assertFalse(b.goalMet)
        assertFalse(b.swap(b.idx(0, 1), b.idx(0, 2)))
    }

    @Test
    fun portalFeedsTheOtherSide() {
        val map = arrayOf("..   ", "..   ", "   ..", "   ..")
        val b = quiet(*map, portals = listOf(Portal(1, 0, 2, 3), Portal(1, 1, 2, 4)))
        val through = b.piece[b.idx(1, 0)]!!
        b.piece[b.idx(2, 3)] = null
        b.piece[b.idx(3, 3)] = null
        assertNotNull(b.fallNow())
        assertTrue(b.piece[b.idx(3, 3)] === through)
        assertEquals(0, b.starvedCells())
        for (i in 0 until b.size) if (b.cell[i]) assertNotNull(b.piece[i])
    }

    /** Les niveaux de la mise à jour se posent à la suite de la progression, sans rien décaler avant. */
    @Test
    fun newLevelsComeAfterTheProgress() {
        fun floor(n: Int, at: Int) = (MercerieLevels.slot(n, at) as MercerieLevels.Slot.Floor).floor
        fun extra(n: Int, at: Int) = (MercerieLevels.slot(n, at) as MercerieLevels.Slot.Extra).k
        // Nouveau joueur : les trente d'origine, les dix nouveaux, puis les étages depuis le 31e.
        assertTrue(MercerieLevels.slot(30, 31) is MercerieLevels.Slot.Base)
        assertEquals(0, extra(31, 31))
        assertEquals(9, extra(40, 31))
        assertEquals(31, floor(41, 31))
        // Joueur arrivé au niveau 50 : ses 49 premiers ne bougent pas, les dix nouveaux sont les 50 à 59.
        assertEquals(49, floor(49, 50))
        assertEquals(0, extra(50, 50))
        assertEquals(9, extra(59, 50))
        assertEquals(50, floor(60, 50))
        assertEquals(55, MercerieLevels.extra(5, 55).number)
    }

    /** Objectif atteint en autant de coups que le robot : le bonus du défi donne les trois étoiles. */
    @Test
    fun beatingTheRobotGivesThreeStars() {
        fun play(par: Int): MercerieBoard {
            val goal = Goal(collect = IntArray(MercerieBoard.COLORS).also { it[0] = 3 })
            val b = MercerieBoard(MercerieLevel(1, 30, 6, List(6) { "......" }, goal, intArrayOf(100, 5000, 90000), par = par), 7L)
            for (r in 0 until 6) for (c in 0 until 6) b.piece[b.idx(r, c)]!!.let { it.color = (r * 2 + c) % 5 + 1; it.kind = Kind.NORMAL }
            b.set(5, 0); b.set(5, 1); b.set(4, 2)
            assertTrue(b.swap(b.idx(4, 2), b.idx(5, 2)))
            b.settle()
            assertTrue(b.goalMet)
            assertTrue(b.finished)
            b.startBonus()
            b.settle()
            return b
        }
        val fast = play(par = 5)
        assertEquals(90000, fast.challenge)
        assertEquals(3, fast.stars)
        val noChallenge = play(par = 0)
        assertEquals(0, noChallenge.challenge)
        assertTrue(noChallenge.stars < 3)
    }
}
