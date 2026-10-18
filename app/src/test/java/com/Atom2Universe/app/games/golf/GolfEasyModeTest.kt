package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test

class GolfEasyModeTest {
    private val flatGreen = ClassicHole(1, 4, 300f, greenShape = GolfGreenShape(slopeX = 0f, slopeZ = 0f))

    @Test fun easyGaugeIsWiderAndForgivesMore() {
        assertTrue(GolfSwing.EASY_PERFECT > GolfSwing.PERFECT && GolfSwing.EASY_GOOD > GolfSwing.GOOD)
        val badRelease = .7f
        assertTrue(GolfSwing.deviation(badRelease, easy = true) < GolfSwing.deviation(badRelease) * .5f)
        assertEquals(0f, GolfSwing.deviation(.2f, easy = true), 0f)
        assertTrue(GolfSwing.period(GolfClub.IRON7, 1f, GolfLie.FAIRWAY, easy = true) > GolfSwing.period(GolfClub.IRON7, 1f, GolfLie.FAIRWAY))
    }

    @Test fun touchingTheFlagstickWinsTheHoleAfterADance() {
        val cup = flatGreen.cup
        val game = ClassicGame(flatGreen, easy = true).apply {
            windX = 0f; windZ = 0f
            throwBall(GolfPoint(cup.x, cup.y + .45f, cup.z - 1f), GolfPoint(0f, 0f, 6f))
        }
        var seconds = 0f
        var highest = 0f
        while (game.state == GolfState.FLYING && seconds < 10f) {
            game.update(1f / 60f); seconds += 1f / 60f
            highest = maxOf(highest, game.ball.y - cup.y)
        }
        assertTrue("The ball climbs the stick", highest > 1.5f)
        assertTrue("It dances for a few seconds", seconds > 2.5f)
        assertEquals(GolfState.HOLED, game.state)
        assertTrue(game.ball.y < cup.y)
    }

    @Test fun backspinSkidsBackOnTheGreenOnlyInEasyMode() {
        fun landed(easy: Boolean): Pair<Float, Float> {
            val g = ClassicGame(flatGreen, easy).apply { windX = 0f; windZ = 0f }
            g.restore(GolfPoint(0f, 0f, flatGreen.cup.z - 70f), 0)
            g.club = GolfClub.PW
            g.aimAngle = 0f
            g.setSpin(0f, -1f)
            val start = g.ball.z
            g.hit(.5f)
            var landing = Float.NaN
            var steps = 0
            while ((g.state == GolfState.FLYING || g.state == GolfState.ROLLING) && steps++ < 60 * 40) {
                val wasFlying = g.state == GolfState.FLYING
                g.update(1f / 120f)
                if (landing.isNaN() && wasFlying && g.state == GolfState.ROLLING) landing = g.ball.z
            }
            return (if (landing.isNaN()) g.ball.z else landing) - start to g.ball.z - start
        }
        val (landEasy, endEasy) = landed(true)
        assertTrue("Easy: it ends behind its landing ($landEasy → $endEasy)", endEasy < landEasy - 2f)
        val (landNormal, endNormal) = landed(false)
        assertTrue("Normal: it does not come back ($landNormal → $endNormal)", endNormal > landNormal - 1f)
    }
}
