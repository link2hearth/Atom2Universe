package com.Atom2Universe.app.games.sokoban

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Garde-fou des niveaux fabriqués sur PC : le fichier se lit, chaque solution rangée avec son
 * niveau le résout dans le vrai jeu avec exactement le nombre de poussées annoncé (le défi).
 */
class SokobanLevelsTest {

    @Test
    fun chaqueNiveauSeResoutAvecLeDefiAnnonce() {
        val levels = SokobanLevels.fromText(File("src/main/assets/sokoban/levels.txt").readText())
        assertTrue("trop peu de niveaux : ${levels.size}", levels.size >= 100)
        var chapter = 1
        for (level in levels) {
            val p = level.puzzle
            val name = "niveau ${level.number}"
            assertTrue("$name : chapitres dans l'ordre", level.chapter >= chapter)
            chapter = level.chapter
            assertEquals("$name : autant de caisses que de quais", p.goals.size, p.boxes.size)
            assertFalse("$name : déjà résolu", p.boxes.toSet() == p.goals)
            assertTrue("$name : une case occupée est un mur",
                (p.boxes + p.goals + p.player).none { p.isWall(it) })
            assertTrue("$name : sans solution", p.solution.isNotEmpty())

            val game = SokobanGame()
            game.load(p)
            for (c in p.solution) {
                val dir = when (c.lowercaseChar()) {
                    'u' -> SokobanDir.UP
                    'd' -> SokobanDir.DOWN
                    'l' -> SokobanDir.LEFT
                    else -> SokobanDir.RIGHT
                }
                assertTrue("$name : coup impossible dans la solution", game.move(dir))
            }
            assertTrue("$name : la solution ne résout pas", game.isSolved())
            assertEquals("$name : défi", p.optimalPushes, game.pushes)
        }
    }
}
