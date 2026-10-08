package com.Atom2Universe.app.games.cosmorun

import com.Atom2Universe.app.games.cosmorun.CosmoRunGesture.Swipe
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sin

/** Un geste par contact : aucune trajectoire de doigt ne doit fabriquer un geste de plus. */
class CosmoRunGestureTest {
    private val threshold = 24f

    private fun play(path: List<Pair<Float, Float>>): List<Swipe> {
        val g = CosmoRunGesture(threshold)
        val out = ArrayList<Swipe>()
        g.down(path[0].first, path[0].second)
        for (i in 1 until path.size) g.move(path[i].first, path[i].second)?.let { out.add(it) }
        return out
    }

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, n: Int = 30) =
        List(n + 1) { x0 + (x1 - x0) * it / n to y0 + (y1 - y0) * it / n }

    @Test fun aLongStraightDragIsOneSwipeInEveryDirection() {
        assertEquals(listOf(Swipe.RIGHT), play(line(0f, 0f, 150f, 0f)))
        assertEquals(listOf(Swipe.LEFT), play(line(0f, 0f, -150f, 0f)))
        assertEquals(listOf(Swipe.UP), play(line(0f, 0f, 0f, -220f)))
        assertEquals(listOf(Swipe.DOWN), play(line(0f, 0f, 0f, 220f)))
    }

    @Test fun aCrookedFastFlickStaysOneSwipe() {
        // Le cas rapporté : un glissé vers la droite qui dérive vers le haut ne doit pas devenir « droite, droite, saut ».
        assertEquals(listOf(Swipe.RIGHT), play(line(0f, 0f, 160f, -70f)))
        assertEquals(listOf(Swipe.RIGHT), play(line(0f, 0f, 200f, 90f)))
        // Un arc : part à droite, remonte franchement à la fin.
        val arc = List(41) { i -> i * 5f to -60f * sin(i / 40f * 1.6f) * (i / 40f) }
        assertEquals(listOf(Swipe.RIGHT), play(arc))
    }

    @Test fun anLShapeIsStillOneSwipe() {
        assertEquals(listOf(Swipe.RIGHT), play(line(0f, 0f, 100f, 0f) + line(100f, 0f, 100f, 100f)))
    }

    @Test fun aHesitationOrSlowReleaseNeverAddsASwipe() {
        // Arrêt en cours de geste, puis reprise dans le même sens : toujours un seul geste.
        assertEquals(listOf(Swipe.RIGHT), play(line(0f, 0f, 60f, 0f) + line(60f, 0f, 60f, 3f, 5) + line(60f, 3f, 130f, 4f)))
        // Le doigt qui retombe un peu à la fin du geste (relâchement) n'est pas un demi-tour.
        assertEquals(listOf(Swipe.RIGHT), play(line(0f, 0f, 120f, 0f) + line(120f, 0f, 90f, 0f, 6)))
    }

    @Test fun aFrankReversalStartsAnotherSwipe() {
        assertEquals(listOf(Swipe.RIGHT, Swipe.LEFT), play(line(0f, 0f, 100f, 0f) + line(100f, 0f, 0f, 0f)))
    }

    @Test fun aReversalIsMeasuredFromTheFarthestPoint() {
        assertEquals(listOf(Swipe.RIGHT), play(line(0f, 0f, 100f, 0f) + line(100f, 0f, 70f, 0f, 5)))
    }

    @Test fun anExactDiagonalWaitsForTwiceTheThresholdThenPicksOne() {
        val g = CosmoRunGesture(threshold)
        g.down(0f, 0f)
        for (d in 1..47) assertEquals("pas de geste à $d dp en 45°", null, g.move(d.toFloat(), d.toFloat()))
        assertEquals(Swipe.RIGHT, g.move(48f, 48f))
        assertEquals("un seul geste", null, g.move(120f, 120f))
    }

    @Test fun aTinyMovementIsNotASwipe() {
        assertEquals(emptyList<Swipe>(), play(line(0f, 0f, 15f, 10f)))
    }

    @Test fun aShortSnapBecomesASwipeOnReleaseNotATap() {
        val g = CosmoRunGesture(threshold)
        g.down(0f, 0f)
        assertEquals(null, g.move(14f, 2f))                 // sous le seuil : rien pendant le geste
        assertEquals(Swipe.RIGHT, g.up(15f, 2f))
        assertEquals(false, g.isTap)
    }

    @Test fun aMotionlessTouchIsATapAndAmbiguousWobbleIsNeither() {
        val tap = CosmoRunGesture(threshold)
        tap.down(0f, 0f); tap.move(3f, 2f)
        assertEquals(null, tap.up(3f, 2f)); assertEquals(true, tap.isTap)
        val wobble = CosmoRunGesture(threshold)
        wobble.down(0f, 0f); wobble.move(12f, 12f)
        assertEquals(null, wobble.up(12f, 12f)); assertEquals(false, wobble.isTap)
    }

    @Test fun releasingAfterASwipeAddsNothing() {
        val g = CosmoRunGesture(threshold)
        g.down(0f, 0f)
        assertEquals(Swipe.RIGHT, g.move(40f, 0f))
        assertEquals(null, g.up(45f, 5f)); assertEquals(false, g.isTap)
    }
}
