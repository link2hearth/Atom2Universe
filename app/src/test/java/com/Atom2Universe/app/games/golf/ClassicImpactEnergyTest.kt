package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ClassicImpactEnergyTest {
    private fun energy(b: BallState) = .5f * (b.vx * b.vx + b.vy * b.vy + b.vz * b.vz) +
        .2f * GolfBallPhysics.RADIUS * GolfBallPhysics.RADIUS * (b.wx * b.wx + b.wy * b.wy + b.wz * b.wz)

    @Test fun grazingAndSpinningImpactsOnlyDissipateEnergyOnEverySurface() {
        // Small normal energy cannot hide a torque that spins up a fast, skimming ball for free.
        for (lie in GolfLie.entries) for (slope in listOf(-.3f, 0f, .3f)) for (landing in listOf(true, false))
            for (speed in listOf(.2f, 3f, 20f, 50f)) for (normalSpeed in listOf(.01f, .2f, 5f))
                for (spin in listOf(-1600f, -150f, 0f, 150f, 1600f)) {
                    val n = sqrt(1f + slope * slope)
                    val b = BallState().apply {
                        vx = speed; vy = -normalSpeed / n; vz = -normalSpeed * slope / n
                        wx = spin * .3f; wy = spin * .2f; wz = spin
                    }
                    val before = energy(b)
                    GolfBallPhysics.bounce(b, 0f, 1f / n, slope / n, lie, landing)
                    val after = energy(b)
                    assertTrue("$lie landing=$landing slope=$slope speed=$speed normal=$normalSpeed spin=$spin: $before -> $after",
                        after.isFinite() && after <= before * 1.0001f + .00001f)
                }
    }
}
