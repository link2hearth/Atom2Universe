package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Test
import java.io.File
import kotlin.math.*

/** Fixed novice putts: same line and dose before/after; no numerical aiming search. */
class GardensPuttingBenchmarkTest {
    // Putting surfaces from the first course before the collecting-green revision.
    private val previousGreens = listOf(
        GolfGreenShape(1.04f,-.3f,.12f,0.0f,0f,0f,.003f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.PLANE, 0f, 0f, run=18f)),
        GolfGreenShape(1.04f,.3f,.12f,0.6f,0f,0f,-.004f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.RIDGE, .6f, .07f, run=18f)),
        GolfGreenShape(1.04f,-.3f,.12f,1.2f,0f,0f,.004f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.PLANE, 0f, 0f, run=18f)),
        GolfGreenShape(1.04f,.3f,.12f,1.8f,0f,0f,0f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.SWALE, 0f, .07f, run=18f)),
        GolfGreenShape(1.04f,-.3f,.12f,0.0f,0f,0f,.003f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.TIER, 1.5707964f, .07f, run=18f)),
        GolfGreenShape(1.04f,.3f,.12f,0.6f,0f,0f,-.003f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.RIDGE, .45f, .07f, run=18f)),
        GolfGreenShape(1.04f,-.3f,.12f,1.2f,0f,0f,-.003f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.CROWN, 0f, .025f, run=18f)),
        GolfGreenShape(1.04f,.3f,.12f,1.8f,0f,0f,.003f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.SWALE, -.4f, .07f, run=18f)),
        GolfGreenShape(1.04f,-.3f,.12f,0.0f,0f,0f,0f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.RIDGE, 0f, .07f, run=18f)),
        GolfGreenShape(1.04f,.3f,.12f,0.6f,0f,0f,.003f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.FALSE_FRONT, 1.5707964f, .07f, run=18f)),
        GolfGreenShape(1.04f,-.3f,.12f,1.2f,0f,0f,-.004f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.TIER, .7f, .07f, run=18f)),
        GolfGreenShape(1.04f,.3f,.12f,1.8f,0f,0f,.004f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.TIER, .2f, .07f, run=18f)),
        GolfGreenShape(1.04f,-.3f,.12f,0.0f,0f,0f,-.004f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.CROWN, 0f, .025f, run=18f)),
        GolfGreenShape(1.04f,.3f,.12f,0.6f,0f,0f,.004f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.RIDGE, -.5f, .07f, run=18f)),
        GolfGreenShape(1.04f,-.3f,.12f,1.2f,0f,0f,-.003f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.RIDGE, .35f, .07f, run=18f)),
        GolfGreenShape(1.04f,.3f,.12f,1.8f,0f,0f,.004f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.PLANE, 0f, 0f, run=18f)),
        GolfGreenShape(1.04f,-.3f,.12f,0.0f,0f,0f,-.004f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.TIER, .7f, .07f, run=18f)),
        GolfGreenShape(1.04f,.3f,.12f,0.6f,0f,0f,.003f,.014f,
            relief=GolfGreenRelief(GolfGreenForm.PUNCHBOWL, 0f, .025f, run=18f))
    )

    @Test fun straightPuttsBeforeAndAfter() {
        val csv = StringBuilder("variant,distance,samples,holed,mean_remaining_metres\n")
        for (variant in listOf("before", "after")) for (distance in listOf(2f, 5f, 10f)) {
            var holed = 0; var samples = 0; var remaining = 0f
            for (current in ClassicCourse.holes) {
                val h = if (variant == "before") current.copy(greenShape = previousGreens[current.number - 1],
                    cupRadius = ClassicHole.CUP_RADIUS, cupBowlDepth = 0f, retainingBankHeight = 0f) else current
                for (direction in 0..7) for (aimError in listOf(-1f, 0f, 1f)) for (dose in listOf(.9f, 1f, 1.1f)) {
                    val angle = direction * PI.toFloat() / 4f
                    val power = (2f * GolfBallPhysics.rolling(GolfLie.GREEN) * distance + .25f) /
                        (2f * GolfBallPhysics.rolling(GolfLie.GREEN) * GolfClub.PUTTER.carry)
                    val g = ClassicGame(h).apply {
                        restore(GolfPoint(h.cup.x - sin(angle) * distance, 0f, h.cup.z - cos(angle) * distance), 0)
                        club = GolfClub.PUTTER; aimAngle = angle + aimError * PI.toFloat() / 180f
                        check(hit(power * dose))
                        var frames = 0
                        while (state in listOf(GolfState.FLYING, GolfState.ROLLING) && frames++ < 2400) update(1f / 60f)
                        check(state in listOf(GolfState.READY, GolfState.HOLED))
                    }
                    samples++
                    if (g.state == GolfState.HOLED) holed++ else remaining += g.distanceToCup
                }
            }
            csv.append("$variant,$distance,$samples,$holed,${remaining / samples}\n")
        }
        File("build/reports/gardens-scoring").mkdirs()
        File("build/reports/gardens-scoring/putting-before-after.csv").writeText(csv.toString())
        println(csv)
    }
}