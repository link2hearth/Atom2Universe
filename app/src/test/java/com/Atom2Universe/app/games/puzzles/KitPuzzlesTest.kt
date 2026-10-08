package com.Atom2Universe.app.games.puzzles

import com.Atom2Universe.app.games.puzzles.fifteen.FifteenState
import com.Atom2Universe.app.games.puzzles.flip.FlipState
import com.Atom2Universe.app.games.puzzles.pegs.PegsState
import com.Atom2Universe.app.games.puzzles.sixteen.SixteenState
import com.Atom2Universe.app.games.puzzles.twiddle.TwiddleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.Atom2Universe.app.games.puzzles.magnets.MagnetsState
import com.Atom2Universe.app.games.puzzles.undead.UndeadState
import com.Atom2Universe.app.games.puzzles.palisade.PalisadeState
import com.Atom2Universe.app.games.puzzles.galaxies.GalaxiesState
import com.Atom2Universe.app.games.puzzles.cube.CubeState
import com.Atom2Universe.app.games.puzzles.loopy.LoopyState
import com.Atom2Universe.app.games.puzzles.pearl.PearlState
import com.Atom2Universe.app.games.puzzles.tracks.TracksState
import com.Atom2Universe.app.games.puzzles.inertia.InertiaState
import com.Atom2Universe.app.games.puzzles.netslide.NetslideState
import com.Atom2Universe.app.games.puzzles.slant.SlantState
import com.Atom2Universe.app.games.puzzles.blackbox.BlackBoxState
import com.Atom2Universe.app.games.puzzles.filling.FillingState
import com.Atom2Universe.app.games.puzzles.map.MapState
import com.Atom2Universe.app.games.puzzles.signpost.SignpostState
import com.Atom2Universe.app.games.puzzles.mosaic.MosaicState
import com.Atom2Universe.app.games.puzzles.range.RangeState
import com.Atom2Universe.app.games.puzzles.singles.SinglesState
import com.Atom2Universe.app.games.puzzles.tents.TentsState
import com.Atom2Universe.app.games.kit.GridSearch
import com.Atom2Universe.app.games.puzzles.dominosa.DominosaState
import com.Atom2Universe.app.games.puzzles.lightup.LightUpState
import com.Atom2Universe.app.games.puzzles.fifteen.FifteenSolver
import com.Atom2Universe.app.games.puzzles.pattern.PatternPictures
import com.Atom2Universe.app.games.puzzles.pattern.PatternState
import com.Atom2Universe.app.games.puzzles.rect.RectState
import com.Atom2Universe.app.games.puzzles.unruly.UnrulyState
import com.Atom2Universe.app.games.kit.LatinBoard
import com.Atom2Universe.app.games.puzzles.keen.KeenState
import com.Atom2Universe.app.games.puzzles.towers.TowersState
import com.Atom2Universe.app.games.puzzles.unequal.UnequalState
import com.Atom2Universe.app.games.puzzles.flood.FloodState
import com.Atom2Universe.app.games.puzzles.guess.GuessState
import com.Atom2Universe.app.games.puzzles.samegame.SameGameState
import com.Atom2Universe.app.games.puzzles.untangle.UntangleState
import kotlin.random.Random

/**
 * Garde-fous des puzzles du kit : une partie générée n'est pas déjà résolue, se relit telle
 * quelle après sauvegarde, et peut se résoudre.
 */
class KitPuzzlesTest {

    /** Le taquin 3×3 : on vérifie la parité par une recherche complète des positions atteignables. */
    @Test fun leTaquinGenereEstToujoursFaisable() {
        val random = Random(1)
        repeat(4) {
            val s = FifteenState.generate(3, 3, random)
            assertFalse(s.isSolved)
            val seen = HashSet<String>()
            val queue = ArrayDeque<FifteenState>()
            queue.add(s); seen.add(s.tiles.joinToString())
            var found = false
            while (queue.isNotEmpty() && !found) {
                val cur = queue.removeFirst()
                if (cur.isSolved) { found = true; break }
                val g = cur.gap
                for (n in listOf(g - 1, g + 1, g - 3, g + 3)) {
                    if (n !in 0 until 9 || (n % 3 != g % 3 && n / 3 != g / 3)) continue
                    val next = cur.slide(n) ?: continue
                    if (seen.add(next.tiles.joinToString())) queue.add(next)
                }
            }
            assertTrue(found)
            assertEquals(s.encode(), FifteenState.decode(s.encode())!!.encode())
        }
    }

    @Test fun flipSeResoutEnRejouantSesAppuis() {
        val s = FlipState.generate(4, 4, true, Random(2))
        assertEquals(s.encode(), FlipState.decode(s.encode())!!.encode())
        // Résolution par élimination de Gauss sur GF(2).
        val n = 16
        val rows = Array(n) { j -> BooleanArray(n + 1) { i -> if (i == n) s.lit[j] else j in s.patterns[i] } }
        var r = 0
        val pivots = IntArray(n) { -1 }
        for (c in 0 until n) {
            val p = (r until n).firstOrNull { rows[it][c] } ?: continue
            val t = rows[p]; rows[p] = rows[r]; rows[r] = t
            for (k in 0 until n) if (k != r && rows[k][c]) for (x in 0..n) rows[k][x] = rows[k][x] xor rows[r][x]
            pivots[r] = c; r++
        }
        var cur = s
        for (k in 0 until r) if (rows[k][n]) cur = cur.press(pivots[k])
        assertTrue(cur.isSolved)
    }

    @Test fun sixteenEtTwiddleTournentSansPerdreDePlaque() {
        val a = SixteenState.generate(4, 4, Random(3))
        assertEquals((1..16).toSet(), a.tiles.toSet())
        assertEquals(a.tiles.toList(), a.shiftRow(1, 1).shiftRow(1, -1).tiles.toList())
        assertEquals(a.tiles.toList(), a.shiftCol(2, 3).shiftCol(2, 1).tiles.toList())
        val t = TwiddleState.generate(5, 3, Random(4))
        assertEquals((1..25).toSet(), t.tiles.toSet())
        assertEquals(t.tiles.toList(), t.rotate(1, 2, true).rotate(1, 2, false).tiles.toList())
        assertEquals(t.tiles.toList(), t.rotate(0, 0, true).rotate(0, 0, true).rotate(0, 0, true).rotate(0, 0, true).tiles.toList())
        assertNotNull(TwiddleState.decode(t.encode()))
    }

    /** Un plateau au hasard se résout : une recherche en profondeur trouve la fiche unique. */
    @Test fun lesPlateauxDeFichesAuHasardSontFaisables() {
        val random = Random(5)
        repeat(3) {
            val s = PegsState.random(5, 5, random)
            assertTrue(s.pegs > 1)
            val dead = HashSet<String>()
            fun solve(p: PegsState): Boolean {
                if (p.isSolved) return true
                val key = p.cells.joinToString("")
                if (key in dead) return false
                for (i in p.cells.indices) if (p.cells[i] == PegsState.PEG)
                    for (t in p.targets(i)) if (solve(p.jump(i, t)!!)) return true
                dead.add(key)
                return false
            }
            assertTrue(solve(s))
        }
        assertEquals(32, PegsState.cross(7).pegs)
        assertEquals(36, PegsState.octagon(random).pegs)
    }

    @Test fun demelageInondationSameGameEtMastermind() {
        val u = UntangleState.generate(15, Random(6))
        assertFalse(u.isSolved)
        assertEquals(u.edges.toList(), UntangleState.decode(u.encode())!!.edges.toList())

        val f = FloodState.generate(14, 6, 0, Random(7))
        // Rejouer la stratégie gloutonne tient dans la limite.
        assertEquals(f.limit, FloodState.greedyMoves(14, 6, f.cells))
        assertTrue(f.limit in 10..40)

        val start = System.nanoTime()
        var cleared = 0
        repeat(5) {
            val g = SameGameState.generate(10, 8, 3, Random(100 + it))
            if (SameGameState.clearable(g, 60_000)) cleared++
        }
        println("Same Game 10×8 : $cleared/5 grilles vidables, ${(System.nanoTime() - start) / 5_000_000} ms par grille")
        assertTrue(cleared >= 4)

        assertEquals(2 to 1, GuessState.score(intArrayOf(0, 1, 2, 3), intArrayOf(0, 1, 3, 5)))
        assertEquals(0 to 2, GuessState.score(intArrayOf(1, 1, 2, 2), intArrayOf(2, 2, 0, 0)))
        var g = GuessState.generate(4, 6, 10, false, Random(8))
        g.secret.forEach { g = g.put(it) }
        assertTrue(g.submit()!!.isSolved)
    }

    /** Les carrés latins : solution unique, et la solution trouvée satisfait le jeu. */
    @Test fun grattecielFutoshikiEtKenKenOntUneSolutionUnique() {
        val start = System.nanoTime()
        val t = TowersState.generate(5, 0, Random(20))
        assertEquals(1, LatinBoard.countSolutions(5, t.board.values) { g, c -> TowersState.consistent(g, 5, t.clues, c) })
        val u = UnequalState.generate(6, 0, Random(21))
        assertEquals(1, LatinBoard.countSolutions(6, u.board.values) { g, c -> UnequalState.consistent(g, 6, u.rel, c) })
        val k = KeenState.generate(6, Random(22))
        assertEquals(1, LatinBoard.countSolutions(6, k.board.values) { g, c -> KeenState.consistent(g, 6, k.cage, k.ops, k.targets, c) })
        assertEquals(t.encode(), TowersState.decode(t.encode()).encode())
        assertEquals(k.encode(), KeenState.decode(k.encode()).encode())
        println("Carrés latins : ${(System.nanoTime() - start) / 1_000_000} ms pour trois grilles")
    }

    /** Les puzzles de grille : génération dans un temps raisonnable, solution unique. */
    @Test fun binairoAkariLogimageShikakuDominosa() {
        val times = ArrayList<String>()
        fun <T> timed(name: String, block: () -> T): T {
            val t = System.nanoTime(); val r = block()
            times.add("$name ${(System.nanoTime() - t) / 1_000_000} ms"); return r
        }
        val u = timed("Binairo 10") { UnrulyState.generate(10, 0, Random(30)) }
        assertEquals(1, GridSearch.count(100, 2, u.cells) { g, c -> UnrulyState.ok(g, 10, c) })
        val l = timed("Akari 10") { LightUpState.generate(10, 10, 0.2f, Random(31)) }
        assertEquals(l.encode(), LightUpState.decode(l.encode()).encode())
        val p = timed("Logimage 15") { PatternState.generate(15, 15, Random(32)) }
        assertNotNull(PatternState.lineSolution(15, 15, p.rows, p.cols))
        val r = timed("Shikaku 10") { RectState.generate(10, 10, 10, Random(33)) }
        assertEquals(1, RectState.countSolutions(10, 10, r.numbers))
        val d = timed("Dominosa 6") { DominosaState.generate(6, Random(34)) }
        assertEquals(1, DominosaState.countSolutions(6, 8, 7, d.numbers))
        println(times.joinToString(" ; "))
    }

    @Test fun tentesHitoriMosaiqueKurodoko() {
        val times = ArrayList<String>()
        fun <T> timed(name: String, block: () -> T): T {
            val t = System.nanoTime(); val r = block()
            times.add("$name ${(System.nanoTime() - t) / 1_000_000} ms"); return r
        }
        val t = timed("Tentes 10") { TentsState.generate(10, 10, Random(40)) }
        assertEquals(t.encode(), TentsState.decode(t.encode()).encode())
        val s = timed("Hitori 8") { SinglesState.generate(8, Random(41)) }
        assertFalse(s.isSolved)
        val m = timed("Mosaïque 13") { MosaicState.generate(13, 13, Random(42)) }
        assertTrue(m.clues.count { it >= 0 } < 169)
        val r = timed("Kurodoko 10") { RangeState.generate(10, 10, Random(43)) }
        assertTrue(r.numbers.any { it >= 0 })
        println(times.joinToString(" ; "))
    }

    @Test fun signpostBoiteNoireCarteFillomino() {
        val times = ArrayList<String>()
        fun <T> timed(name: String, block: () -> T): T {
            val t = System.nanoTime(); val r = block()
            times.add("$name ${(System.nanoTime() - t) / 1_000_000} ms"); return r
        }
        val sp = timed("Signpost 7") { SignpostState.generate(7, 7, Random(50)) }
        assertEquals(1, SignpostState.countPaths(7, 7, sp.dirs, sp.givens))

        // Boîte noire : une boule seule au centre d'un 5×5.
        val balls = BooleanArray(25); balls[12] = true
        val bb = BlackBoxState(5, 5, balls, BooleanArray(25), IntArray(20), 0, false)
        assertEquals(BlackBoxState.HIT, bb.trace(2))          // en face, colonne du milieu
        assertEquals(18, bb.trace(1))                           // frôle la boule : quart de tour, sort à gauche
        assertEquals(6, bb.trace(3))                            // et à droite pour la colonne symétrique
        assertEquals(14, bb.trace(0))                           // colonne du bord : traverse tout droit
        assertTrue(bb.consistentWith(balls))

        val m = timed("Carte 25") { MapState.generate(14, 14, 25, Random(51)) }
        assertEquals(1, MapState.countColourings(m.adjacency, m.colours))
        val f = timed("Fillomino 9") { FillingState.generate(9, 9, 8, Random(52)) }
        assertEquals(f.encode(), FillingState.decode(f.encode()).encode())
        println(times.joinToString(" ; "))
    }

    @Test fun gokigenNetslideInertie() {
        val times = ArrayList<String>()
        fun <T> timed(name: String, block: () -> T): T {
            val t = System.nanoTime(); val r = block()
            times.add("$name ${(System.nanoTime() - t) / 1_000_000} ms"); return r
        }
        val sl = timed("Gokigen 12") { SlantState.generate(12, 12, Random(61)) }
        assertTrue(sl.clues.any { it >= 0 })
        val n = timed("Netslide 6") { NetslideState.generate(6, 6, Random(62)) }
        assertFalse(n.isSolved)
        assertTrue(n.source >= 0)
        val ine = timed("Inertie 14") { InertiaState.generate(14, 10, Random(63)) }
        assertTrue(ine.gems > 0)
        println(times.joinToString(" ; "))
    }

    @Test(timeout = 60_000) fun slitherlink() {
        val t = System.nanoTime()
        val l = LoopyState.generate(10, 10, Random(70))
        assertTrue(l.clues.any { it >= 0 })
        println("Slitherlink 10 : ${(System.nanoTime() - t) / 1_000_000} ms")
    }

    @Test(timeout = 60_000) fun masyu() {
        val t = System.nanoTime()
        val p = PearlState.generate(10, 10, Random(71))
        assertTrue(p.pearls.any { it != 0 })
        println("Masyu 10 : ${(System.nanoTime() - t) / 1_000_000} ms")
    }

    @Test(timeout = 60_000) fun rails() {
        val t = System.nanoTime()
        val r = TracksState.generate(8, 8, Random(72))
        assertEquals(r.encode(), TracksState.decode(r.encode()).encode())
        println("Rails 8 : ${(System.nanoTime() - t) / 1_000_000} ms")
    }

    @Test(timeout = 60_000) fun aimantsEtMortsVivants() {
        var t = System.nanoTime()
        val m = MagnetsState.generate(10, 8, Random(80))
        assertEquals(m.encode(), MagnetsState.decode(m.encode()).encode())
        val tm = (System.nanoTime() - t) / 1_000_000
        t = System.nanoTime()
        val u = UndeadState.generate(7, 7, Random(81))
        assertEquals(u.totals.sum(), u.mirrors.count { it == 0 })
        println("Aimants 10×8 : $tm ms ; Morts-vivants 7 : ${(System.nanoTime() - t) / 1_000_000} ms")
    }

    @Test(timeout = 60_000) fun palissadeEtGalaxies() {
        var t = System.nanoTime()
        val p = PalisadeState.generate(8, 8, 4, Random(90))
        assertEquals(p.encode(), PalisadeState.decode(p.encode()).encode())
        val tp = (System.nanoTime() - t) / 1_000_000
        t = System.nanoTime()
        val g = GalaxiesState.generate(9, 9, Random(91))
        assertEquals(g.encode(), GalaxiesState.decode(g.encode()).encode())
        val init = IntArray(81) { -1 }
        for (d in g.dotX.indices) GalaxiesState.coreCells(9, 9, g.dotX[d], g.dotY[d]).forEach { init[it] = d }
        assertEquals(1, GalaxiesState.count(9, 9, g.dotX, g.dotY, init, 20_000))
        println("Palissade 8×8 : $tp ms ; Galaxies 9 : ${(System.nanoTime() - t) / 1_000_000} ms")
    }

    @Test fun cube() {
        // Sans case colorée, un aller-retour ramène chaque face à sa place.
        val faces = BooleanArray(6) { it == CubeState.NORTH }
        var c: CubeState? = CubeState(3, 3, 0, 0, faces, BooleanArray(9), 0)
        c = c!!.roll(1, 0)!!.roll(-1, 0)!!.roll(0, 1)!!.roll(0, -1)
        assertEquals(faces.toList(), c!!.faces.toList())
        assertEquals(null, c.roll(-1, 0))
        // Rouler sur une case colorée la ramasse sur la face du dessous.
        val blue = BooleanArray(9).also { it[1] = true }
        val d = CubeState(3, 3, 0, 0, BooleanArray(6), blue, 0).roll(1, 0)!!
        assertEquals(true, d.faces[CubeState.BOTTOM])
        assertEquals(false, d.blue[1])
        assertEquals(d.encode(), CubeState.decode(d.encode()).encode())
        val g = CubeState.generate(5, 5, Random(92))
        assertEquals(6, g.blue.count { it })
    }

    @Test fun logimageImages() {
        // Chaque dessin est rectangulaire et se résout ligne par ligne, sans deviner. Les images
        // refusées sont listées (numéro, taille, première ligne) pour savoir laquelle retoucher.
        val bad = PatternPictures.all.indices.filter { k ->
            val p = PatternPictures.all[k]
            p.rows.any { it.length != p.w } || PatternState.fromPicture(k).let { s ->
                !PatternState.lineSolution(s.w, s.h, s.rows, s.cols).contentEquals(s.solution)
            }
        }.map { k -> PatternPictures.all[k].let { "#${k + 1} ${it.w}x${it.h} ${it.rows[0]}" } }
        assertEquals(emptyList<String>(), bad)
        // Une collection de quelques dizaines d'images, de la plus petite à la plus grande.
        assertTrue(PatternPictures.all.size >= 40)
        assertTrue(PatternPictures.all.zipWithNext().all { (a, b) -> a.w * a.h <= b.w * b.h })
        // Chaque image a son propre nom.
        assertEquals(PatternPictures.all.size, PatternPictures.all.map { it.nameRes }.toSet().size)
        // L'aide ne barre un bloc que s'il occupe exactement ses cases.
        val heart = PatternState.fromPicture(0)
        val row1 = (5 until 10).associateWith { 1 }
        assertEquals(true, heart.with(row1).blockDone(true, 1, 0))
        assertEquals(false, heart.with(row1 + mapOf(0 to 1, 1 to 1)).blockDone(true, 0, 0))
        assertEquals(true, heart.with(mapOf(1 to 1)).blockDone(true, 0, 0))
        assertEquals(false, heart.with(mapOf(1 to 1, 2 to 1)).blockDone(true, 0, 0))
        assertEquals(heart.encode(), PatternState.decode(heart.encode()).encode())
    }

    @Test(timeout = 300_000) fun mosaiqueImages() {
        // Chaque image du mode Images donne une grille de Mosaïque à solution unique ; la plupart se
        // résolvent même case par case, sans essai.
        val bad = ArrayList<String>()
        var slowest = 0L
        var explored = 0
        for (k in PatternPictures.all.indices) {
            val t = System.nanoTime()
            val m = MosaicState.fromPicture(k)
            slowest = maxOf(slowest, (System.nanoTime() - t) / 1_000_000)
            if (!MosaicState.unique(m.w, m.h, m.clues, 20_000)) bad.add("$k (${m.w}×${m.h})")
            if (!MosaicState.propagates(m.w, m.h, m.clues)) explored++
        }
        println("Mosaïque images : plus lente ${slowest} ms ; avec exploration : $explored ; refusées : $bad")
        assertEquals(emptyList<String>(), bad)
        assertTrue(explored <= 12)
    }

    @Test fun astucesMenentALaSolution() {
        // Appliquer les astuces jusqu'au bout donne une grille résolue, même partie d'erreurs.
        var t = TowersState.generate(5, 0, Random(100))
        t = t.withBoard(t.board.set(t.board.values.indexOfFirst { it == 0 }, 1).set(t.board.values.indexOfLast { it == 0 }, 2))
        while (true) t = t.board.hint()?.let { t.withBoard(it) } ?: break
        assertTrue(t.isSolved)
        var k = KeenState.generate(4, Random(101))
        while (true) k = k.board.hint()?.let { k.withBoard(it) } ?: break
        assertTrue(k.isSolved)
        var p = PatternState.generate(10, 10, Random(102)).with(mapOf(0 to 1, 1 to 2, 2 to 1))
        while (true) p = p.hint() ?: break
        assertTrue(p.isSolved)
        var m = MosaicState.fromPicture(3).with(0, 1)
        while (true) m = m.hint() ?: break
        assertTrue(m.isSolved)
        var u = UnrulyState.generate(8, 0, Random(103))
        while (true) u = u.hint() ?: break
        assertTrue(u.isSolved)
        var d = UndeadState.generate(5, 5, Random(104))
        while (true) d = d.hint() ?: break
        assertTrue(d.isSolved)
        var g = MagnetsState.generate(6, 5, Random(105))
        while (true) g = g.hint() ?: break
        assertTrue(g.isSolved)
        var f = FillingState.generate(5, 5, 5, Random(106))
        while (true) f = f.hint() ?: break
        assertTrue(f.isSolved)
        var c = MapState.generate(8, 8, 15, Random(107))
        while (true) c = c.hint() ?: break
        assertTrue(c.isSolved)
        var si = SinglesState.generate(6, Random(108))
        while (true) si = si.hint() ?: break
        assertTrue(si.isSolved)
        var lu = LightUpState.generate(7, 7, 0.2f, Random(109))
        while (true) lu = lu.hint() ?: break
        assertTrue(lu.isSolved)
        var te = TentsState.generate(8, 8, Random(110))
        while (true) te = te.hint() ?: break
        assertTrue(te.isSolved)
        var ra = RangeState.generate(6, 6, Random(111))
        while (true) ra = ra.hint() ?: break
        assertTrue(ra.isSolved)
        var sl = SlantState.generate(6, 6, Random(112))
        while (true) sl = sl.hint() ?: break
        assertTrue(sl.isSolved)
        var lo = LoopyState.generate(6, 6, Random(113))
        while (true) lo = lo.hint() ?: break
        assertTrue(lo.isSolved)
        var pe = PearlState.generate(7, 7, Random(114))
        while (true) pe = pe.hint() ?: break
        assertTrue(pe.isSolved)
        var tr = TracksState.generate(7, 7, Random(115))
        while (true) tr = tr.hint() ?: break
        assertTrue(tr.isSolved)
        var pa = PalisadeState.generate(6, 6, 4, Random(116))
        while (true) pa = pa.hint() ?: break
        assertTrue(pa.isSolved)
        var ga = GalaxiesState.generate(7, 7, Random(117))
        while (true) ga = ga.hint() ?: break
        assertTrue(ga.isSolved)
        var dm = DominosaState.generate(4, Random(118)).toggle(0, 1)
        while (true) dm = dm.hint() ?: break
        assertTrue(dm.isSolved)
        var re = RectState.generate(7, 7, 8, Random(119)).draw(intArrayOf(0, 0, 2, 2))
        while (true) re = re.hint() ?: break
        assertTrue(re.isSolved)
        var fl = FlipState.generate(5, 5, true, Random(121)).press(0).press(7)
        while (true) fl = fl.hint() ?: break
        assertTrue(fl.isSolved)
        assertTrue(UntangleState.generate(12, Random(122)).hint()!!.isSolved)
        var bb = BlackBoxState.generate(6, 6, 3, Random(123)).toggleGuess(0)!!
        while (true) bb = bb.hint() ?: break
        assertTrue(bb.check().isSolved)
        var gs = GuessState.generate(4, 6, 10, true, Random(124))
        while (true) gs = gs.hint() ?: break
        assertTrue(gs.submit()!!.isSolved)
        var sp = SignpostState.generate(4, 4, Random(120))
        sp = sp.link(0, (1 until 16).first { sp.onRay(0, it) && it != sp.solution[0] }) ?: sp
        while (true) sp = sp.hint() ?: break
        assertTrue(sp.isSolved)
    }

    @Test fun taquinDemonstration() {
        // La démonstration résout toutes les tailles, en un nombre de coups raisonnable.
        val lengths = ArrayList<String>()
        for (size in 3..5) for (seed in 0 until 20) {
            val start = FifteenState.generate(size, size, Random(seed * 7L + size))
            val steps = FifteenSolver.solve(start)
            assertNotNull(steps)
            assertTrue(steps!!.last().state.isSolved)
            if (seed == 0) lengths.add("${size}×$size : ${steps.size} coups")
        }
        println("Taquin, démonstration : $lengths")
    }

    @Test fun rembobinage() {
        // Le journal rembobiné ramène toujours à la grille résolue, même après des coups du joueur.
        val a = SixteenState.generate(4, 4, Random(130)).shiftRow(1, 1).shiftCol(2, -1).shiftCol(2, 1)
        assertTrue(a.rewind().last().isSolved)
        println("Sixteen 4×4 : ${a.rewind().size} étapes")
        val b = NetslideState.generate(5, 5, Random(131)).shiftCol(0, 1)
        assertTrue(b.rewind().last().isSolved)
        val c = TwiddleState.generate(4, 2, Random(132)).rotate(0, 0, true).rotate(1, 1, false)
        assertTrue(c.rewind().last().isSolved)
        println("Twiddle 4×4 : ${c.rewind().size} étapes")
    }

    @Test(timeout = 120_000) fun solitaireDemonstration() {
        // La croix anglaise, l'octogone et des plateaux au hasard se terminent par la démonstration.
        val times = ArrayList<String>()
        fun check(name: String, start: PegsState) {
            val t = System.nanoTime()
            val steps = start.demo()
            assertNotNull(name, steps)
            assertTrue(name, steps!!.last().isSolved)
            times.add("$name ${(System.nanoTime() - t) / 1_000_000} ms")
        }
        check("croix", PegsState.cross(7))
        check("hasard 7", PegsState.random(7, 7, Random(141)))
        // Une partie mal engagée : la démonstration revient d'abord en arrière.
        var s = PegsState.cross(7)
        repeat(6) { s = s.cells.indices.firstNotNullOfOrNull { a -> s.targets(a).firstOrNull()?.let { b -> s.jump(a, b) } } ?: s }
        check("croix mal engagée", s)
        println("Solitaire, démonstration : $times")
    }

    @Test fun inertieEtFloodDemonstration() {
        for (seed in 150L..154L) {
            val ine = InertiaState.generate(10, 8, Random(seed))
            assertTrue(ine.demo()!!.last().isSolved)
            val fl = FloodState.generate(12, 6, 2, Random(seed))
            val d = fl.demo()
            assertTrue(d.last().isSolved && !d.last().isLost)
        }
    }

    @Test(timeout = 300_000) fun cubeDemonstration() {
        val out = ArrayList<String>()
        for (size in listOf(4, 5, 7)) for (seed in 0L..2L) {
            val t = System.nanoTime()
            val d = com.Atom2Universe.app.games.puzzles.cube.CubeSolver.demo(CubeState.generate(size, size, Random(seed + 160)))
            out.add("$size×$size : ${d?.size ?: "rien"} coups en ${(System.nanoTime() - t) / 1_000_000} ms")
            if (d != null) assertTrue(d.last().isSolved)
        }
        println("Cube : $out")
    }

    @Test(timeout = 120_000) fun sameGameDemonstration() {
        val g = SameGameState.generate(10, 8, 4, Random(170))
        val d = g.demo()
        assertTrue(d.isNotEmpty() && d.last().remaining < g.remaining)
    }
}
