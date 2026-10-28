package com.Atom2Universe.app.games.papattes

import org.junit.Test

/**
 * Banc de mesure (exclu par défaut, voir `bancsDeMesure`) : fait marcher le robot sur des étapes de
 * chaque biome avec chaque paire de jambes, puis essaie les mauvais gestes (tenir trop, tapoter).
 * Résultats dans build/outputs/papattes/diag.txt et obstacles.txt.
 */
class PapattesDiagTest {
    private fun line(sb: StringBuilder, label: String, game: PapattesGame, t: Float) {
        sb.append(String.format("%-28s progress=%5.1f / %5.1f falls=%d phase=%s t=%.1f\n",
            label, game.progressX, game.course.finishX, game.falls, game.phase, t))
    }

    @Test
    fun walk() {
        val sb = StringBuilder()
        val dt = 1f / 60f
        for (stage in listOf(1, 3, 7, 9, 13, 16)) {
            sb.append("== stage $stage ${PapattesCourse(stage).biome}\n")
            for (legs in Legs.entries) {
                val game = PapattesGame(stage, Critter(Head.CAT, Body.POMPOM, legs))
                val robot = PapattesRobot(game)
                var t = 0f
                while (t < 90f && game.phase != PapattesGame.Phase.FINISHED) { robot.tick(dt); game.update(dt); t += dt }
                line(sb, "$legs", game, t)
            }
        }
        run {
            val game = PapattesGame(7, Critter.STARTER)
            sb.append("== stage 7 waters: " + game.course.waters.take(4).joinToString { String.format("[%.2f..%.2f]", it.x0, it.x1) } + "\n")
            val robot = PapattesRobot(game)
            var t = 0f
            var falls = 0
            val c = FloatArray(2)
            while (t < 40f && falls < 4) {
                robot.tick(dt); game.update(dt); t += dt
                val w = game.walker
                if (PapattesGame.Event.STEP in game.events) {
                    w.torsoCenter(c)
                    sb.append(String.format("  step t=%.2f torso=%.2f feet=%.2f/%.2f\n", t, c[0], w.front.foot()[0], w.back.foot()[0]))
                }
                for (e in game.events) if (e == PapattesGame.Event.FALL || e == PapattesGame.Event.SPLASH) {
                    falls++
                    w.torsoCenter(c)
                    sb.append(String.format("  %s t=%.2f torso=%.2f,%.2f feet=%.2f,%.2f / %.2f,%.2f pressed=%s\n", e, t, c[0], c[1],
                        w.front.foot()[0], w.front.foot()[1], w.back.foot()[0], w.back.foot()[1], w.pressed))
                }
            }
        }
        sb.append("== misuse, stage 1, stilts\n")
        for ((label, hold, rest) in listOf(Triple("hold forever", 99f, 0f), Triple("hold 1.4 s", 1.4f, .4f),
            Triple("hold 1.1 s", 1.1f, .4f), Triple("hold 0.8 s", .8f, .3f), Triple("taps 0.15/0.1", .15f, .1f),
            Triple("hold 0.5 s", .5f, .2f))) {
            val game = PapattesGame(1, Critter.STARTER)
            var t = 0f
            var phaseT = 0f
            var down = false
            while (t < 30f && game.phase != PapattesGame.Phase.FINISHED) {
                phaseT += dt
                if (down && phaseT > hold) { game.release(); down = false; phaseT = 0f }
                else if (!down && phaseT > rest) { game.press(); down = true; phaseT = 0f }
                game.update(dt); t += dt
            }
            line(sb, label, game, t)
        }
        for (body in listOf(Body.BEE, Body.SHELL)) for (legs in listOf(Legs.FLAMINGO, Legs.DACHSHUND)) {
            val game = PapattesGame(1, Critter(Head.CAT, body, legs))
            val robot = PapattesRobot(game)
            var t = 0f
            while (t < 90f && game.phase != PapattesGame.Phase.FINISHED) { robot.tick(dt); game.update(dt); t += dt }
            line(sb, "$body+$legs", game, t)
        }
        println(sb)
        java.io.File("build/outputs/papattes").mkdirs()
        java.io.File("build/outputs/papattes/diag.txt").writeText(sb.toString())
    }

    /** Les nouveaux obstacles, étape par étape, pour chaque démarche et la bouée : arrivée, chutes et où. */
    @Test
    fun obstacles() {
        val sb = StringBuilder()
        val dt = 1f / 60f
        val critters = listOf(
            Critter(Head.CAT, Body.POMPOM, Legs.STILTS), Critter(Head.CAT, Body.POMPOM, Legs.DACHSHUND),
            Critter(Head.CAT, Body.POMPOM, Legs.FROG), Critter(Head.CAT, Body.POMPOM, Legs.KANGAROO),
            Critter(Head.CAT, Body.POMPOM, Legs.SPRING), Critter(Head.CAT, Body.POMPOM, Legs.ROLLERS),
            Critter(Head.CAT, Body.BUOY, Legs.SKATES), Critter(Head.CAT, Body.BUOY, Legs.STILTS))
        for (stage in listOf(2, 3, 4, 5, 6, 8, 10, 12, 14, 16)) {
            val c = PapattesCourse(stage)
            sb.append("== stage $stage ${c.biome} seesaws=${c.seesaws.size} mushrooms=${c.mushrooms.size} winds=${c.winds.size} " +
                "planks=${c.segments.count { it.floater == Floater.PLANK }} logs=${c.segments.count { it.floater == Floater.LOG }} waters=${c.waters.size}\n")
            for (critter in critters) {
                val game = PapattesGame(stage, critter)
                val robot = PapattesRobot(game)
                var t = 0f
                val where = ArrayList<String>()
                val p = FloatArray(2)
                while (t < 120f && game.phase != PapattesGame.Phase.FINISHED) {
                    robot.tick(dt); game.update(dt); t += dt
                    for (e in game.events) if (e == PapattesGame.Event.FALL || e == PapattesGame.Event.SPLASH) {
                        game.walker.torsoCenter(p)
                        if (where.size < 6) where += String.format("%s@%.1f", if (e == PapattesGame.Event.FALL) "F" else "S", p[0])
                    }
                }
                sb.append(String.format("  %-10s %-8s done=%-5s falls=%2d gold=%d prog=%5.1f/%5.1f t=%5.1f %s\n",
                    critter.legs, critter.body, game.phase == PapattesGame.Phase.FINISHED, game.falls, game.goldCount,
                    game.progressX, c.finishX, t, where.joinToString(" ")))
            }
        }
        java.io.File("build/outputs/papattes").mkdirs()
        java.io.File("build/outputs/papattes/obstacles.txt").writeText(sb.toString())
    }
}
