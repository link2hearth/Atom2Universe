package com.Atom2Universe.app.games.cosmorun

import com.Atom2Universe.app.games.cosmorun.CosmoRunGame.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les motifs sont écrits à la main : ce test garantit qu'aucun n'est injouable. */
private const val lanes = CosmoRunGame.NUM_LANES

class CosmoRunPatternsTest {
    /**
     * Recherche discrétisée en rangées (0,16 s). Un saut dure 4 rangées et franchit une barrière pendant
     * les 3 premières ; une glissade dure 3 rangées ; on change d'une voie par rangée au plus. Le niveau
     * (sol / toit) suit les rampes et les conteneurs. Ce n'est pas le solveur du jeu : c'est un garde-fou
     * d'écriture pour des motifs faits à la main.
     */
    private class Solver(val pattern: CosmoRunPatterns.Pattern, val slack: Int = 0) {
        // mode : 0 debout, 1..4 en l'air, 5..7 en glissade
        val cell = Array(pattern.rows) { arrayOfNulls<MutableList<CosmoRunPatterns.Cell>>(lanes) }
        init {
            for (c in pattern.cells) for (r in c.row until minOf(pattern.rows, c.row + c.length)) {
                val list = cell[r][c.lane] ?: mutableListOf<CosmoRunPatterns.Cell>().also { cell[r][c.lane] = it }
                list.add(c)
            }
        }

        private fun has(r: Int, lane: Int, type: EntityType, roofed: Boolean) =
            cell[r][lane]?.any { it.type == type && it.roofed == roofed } == true

        /** Vrai si l'état (voie, niveau, mode) passe la rangée [r]. */
        private fun safe(r: Int, lane: Int, level: Int, mode: Int): Boolean {
            val airClear = mode in 1..3
            val sliding = mode in 5..7
            if (level == 0) {
                if (has(r, lane, EntityType.CONTAINER, false)) return false
                if ((has(r, lane, EntityType.HURDLE, false) || has(r, lane, EntityType.METEOR, false)) && !airClear) return false
                if ((has(r, lane, EntityType.LASER, false) || has(r, lane, EntityType.DRONE, false)) && !sliding) return false
            } else {
                if ((has(r, lane, EntityType.HURDLE, true) || has(r, lane, EntityType.METEOR, true)) && !airClear) return false
                if (has(r, lane, EntityType.LASER, true) && !sliding) return false
            }
            return true
        }

        private fun hasSupport(r: Int, lane: Int) =
            cell[r][lane]?.any { it.type == EntityType.CONTAINER || it.type == EntityType.RAMP } == true

        fun solvable(): Boolean {
            // état : voie * 100 + niveau * 10 + mode
            var states = HashSet<Int>()
            for (lane in 0 until lanes) {
                val onRamp = cell[0][lane]?.any { it.type == EntityType.RAMP } == true
                val level = if (onRamp) 1 else 0
                // On arrive déjà prêt : l'intervalle avant le motif a laissé le temps de sauter ou de se baisser.
                for (mode in intArrayOf(0, 1, 2, 3, 5, 6, 7))
                    if (onRamp || safe(0, lane, 0, mode)) states.add(lane * 100 + level * 10 + mode)
            }
            for (r in 1 until pattern.rows) {
                val next = HashSet<Int>()
                for (s in states) {
                    val lane = s / 100; val level = s / 10 % 10; val mode = s % 10
                    val modes = ArrayList<Int>()
                    when (mode) {
                        0 -> { modes.add(0); modes.add(1); modes.add(5) }
                        in 1..3 -> modes.add(mode + 1)
                        4 -> { modes.add(0); modes.add(1); modes.add(5) }   // atterri : peut rebondir aussitôt
                        5, 6 -> modes.add(mode + 1)
                        else -> { modes.add(0); modes.add(1); modes.add(5) }
                    }
                    for (dl in -1..1) {
                        val l2 = lane + dl
                        if (l2 !in 0 until lanes) continue
                        // On ne s'engage pas dans une voie tant que son conteneur n'a pas fini de passer :
                        // elle doit être libre à cette rangée ET à la précédente.
                        if (dl != 0 && level == 0 && (0..1 + slack).any { back -> r - back >= 0 && has(r - back, l2, EntityType.CONTAINER, false) }) continue
                        for (m2 in modes) {
                            val ramp = cell[r][l2]?.any { it.type == EntityType.RAMP } == true
                            val support = cell[r][l2]?.any { it.type == EntityType.CONTAINER } == true
                            val lv = when {
                                ramp -> 1
                                support -> if (level == 1) 1 else continue      // de face au sol : un mur
                                else -> if (level == 1 && m2 in 1..3) 1 else 0  // saut au-dessus d'une brèche de toit, sinon on tombe
                            }
                            if (!ramp && !safe(r, l2, lv, m2)) continue
                            next.add(l2 * 100 + lv * 10 + m2)
                        }
                    }
                }
                if (next.isEmpty()) return false
                states = next
            }
            return true
        }
    }

    @Test fun catalogueIsWellFormed() {
        val all = CosmoRunPatterns.all
        assertTrue(all.size >= 20)
        assertEquals("noms uniques", all.size, all.map { it.name }.toSet().size)
        for (p in all) {
            assertTrue("${p.name} : rangées", p.rows in 3..24)
            assertTrue("${p.name} : palier", p.tier in 0..5)
            assertTrue("${p.name} : entrée impossible", p.entry != 0 || p.usesRoof)
            assertTrue("${p.name} : sortie impossible", p.exit != 0 || p.exitRoof != 0)
            if (p.breather) assertEquals("${p.name} : une respiration ne blesse pas", 0, p.hazardCount)
        }
        for (tier in 1..5) assertTrue("palier $tier vide", all.any { it.tier == tier && !it.breather })
        assertTrue("aucune respiration", all.count { it.breather } >= 3)
    }

    @Test fun everyPatternAndItsMirrorIsSolvable() {
        for (p in CosmoRunPatterns.all) {
            // Jusqu'au palier 3, une rangée de marge : le joueur n'a pas à être parfait. Au-delà, c'est serré.
            val slack = if (p.tier <= 3) 1 else 0
            assertTrue("${p.name} est injouable (marge $slack)", Solver(p, slack).solvable())
            assertTrue("${p.name} (miroir) est injouable", Solver(CosmoRunPatterns.mirror(p), slack).solvable())
        }
    }

    @Test fun theSolverRejectsAnImpossibleWall() {
        // Sanity check du garde-fou : une paroi de conteneurs sur les cinq voies ne se passe pas.
        val wall = CosmoRunPatterns.parse("mur", 1, CosmoRunPatterns.ALL, 1, false, listOf(".....", "#####", "....."))
        assertTrue(!Solver(wall).solvable())
        val hurdles = CosmoRunPatterns.parse("barrieres", 1, CosmoRunPatterns.ALL, 1, false, listOf(".....", "hhhhh", "....."))
        assertTrue(Solver(hurdles).solvable())
        // Trois barrières collées en profondeur, sur toutes les voies : un saut de 4 rangées ne les couvre pas.
        val tooDense = CosmoRunPatterns.parse("dense", 1, CosmoRunPatterns.ALL, 1, false,
            listOf(".....", "hhhhh", ".....", ".....", ".....", "lllll", "....."))
        assertTrue("saut puis glissade à 4 rangées d'écart passent tout juste", Solver(tooDense).solvable())
        val impossible = CosmoRunPatterns.parse("impossible", 1, CosmoRunPatterns.ALL, 1, false,
            listOf(".....", "hhhhh", "lllll", "....."))
        assertTrue(!Solver(impossible).solvable())
    }

    @Test fun directorAlwaysFindsAJunction() {
        // Tous les enchaînements que le metteur en scène peut produire relient une sortie à une entrée.
        val director = CosmoRunDirector(kotlin.random.Random(3))
        for (roofs in listOf(false, true)) {
            director.roofsEnabled = roofs
            director.reset()
            var distance = 0f
            var previous: CosmoRunPatterns.Pattern? = null
            repeat(4000) {
                val choice = director.next(distance)
                if (previous != null) assertTrue("jonction sans issue", director.gapBetween(previous, choice.pattern) != null)
                assertTrue(choice.gapRows in 0..CosmoRunDirector.MAX_GAP)
                previous = choice.pattern
                distance += 40f
                if (distance > 6000f) distance = 0f
            }
        }
    }
}
