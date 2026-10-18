package com.Atom2Universe.app.games.toyboxracers.track

import com.Atom2Universe.app.games.toyboxracers.track.SculptedCircuits.Bump
import com.Atom2Universe.app.games.toyboxracers.track.SculptedCircuits.Kicker
import com.Atom2Universe.app.games.toyboxracers.track.SculptedCircuits.Layout
import com.Atom2Universe.app.games.toyboxracers.track.SculptedCircuits.Level

/** Four distinct routes; the house seed and its original circuit order stay unchanged. */
internal object DestinationCircuits {
    private class Route(vararg points: Float) {
        val loop = PlanarLoop.corners(points.toList().chunked(3).map { (x, z, radius) -> Corner(x, z, radius) })
        fun at(x: Float, z: Float) = loop.fractionNear(x, z)
    }

    fun all(): Map<CircuitKind, Layout> = mapOf(
        CircuitKind.CRYSTAL_RUN to mine(), CircuitKind.REACTOR_LOOP to laboratory(),
        CircuitKind.TABLETOP_SPRINT to restaurant(), CircuitKind.NEON_BOULEVARD to boulevard()
    )

    // Galleries, a long climb past the crystals and a short, wide ore-loader jump.
    private fun mine(): Layout {
        val p = Route(-94f,-42f,20f, 94f,-42f,22f, 94f,42f,22f,
            42f,42f,18f, 18f,8f,18f, -24f,8f,18f, -50f,42f,18f, -94f,42f,20f)
        return Layout(p.loop, 10.5f,
            levels = listOf(Level(p.at(30f,-42f),0f), Level(p.at(94f,-10f),8f),
                Level(p.at(72f,42f),8f), Level(p.at(18f,8f),0f)),
            bumps = listOf(Bump(p.at(-70f,-42f),30f,1.8f), Bump(p.at(-94f,0f),34f,2.2f)),
            kickers = listOf(Kicker(p.at(-12f,-42f),22f,2.6f,6f,2f,14f)),
            pillarColor = 0x876847)
    }

    // Wide sweep around the instruments, then a raised observation lane and tight return.
    private fun laboratory(): Layout {
        val p = Route(-94f,-42f,20f, 94f,-42f,22f, 94f,42f,22f,
            25f,42f,18f, 25f,-6f,18f, -35f,-6f,18f, -35f,42f,18f, -94f,42f,20f)
        return Layout(p.loop, 10f,
            levels = listOf(Level(p.at(-62f,-42f),0f), Level(p.at(35f,-42f),10f),
                Level(p.at(94f,-10f),10f), Level(p.at(70f,42f),0f)),
            bumps = listOf(Bump(p.at(-4f,-6f),34f,2.5f), Bump(p.at(-94f,0f),36f,2f)),
            pillarColor = 0x93B8C3)
    }

    // Broad straights between giant place settings; a rolling serving ramp at the back.
    private fun restaurant(): Layout {
        val p = Route(-94f,-42f,22f, 38f,-42f,20f, 64f,-20f,20f,
            94f,-20f,18f, 94f,42f,20f, -94f,42f,22f)
        return Layout(p.loop, 11f,
            bumps = listOf(Bump(p.at(-20f,-42f),76f,8f), Bump(p.at(-65f,42f),38f,2.5f)),
            kickers = listOf(Kicker(p.at(18f,42f),24f,2.8f,6f,2.3f,16f)),
            pillarColor = 0xBA8264)
    }

    // Fast avenue, a traffic-island chicane, two generous block corners and a high flyover.
    private fun boulevard(): Layout {
        val p = Route(-94f,-42f,20f, -30f,-42f,18f, -6f,-22f,18f,
            24f,-22f,18f, 48f,-42f,18f, 94f,-42f,20f, 94f,42f,22f, -94f,42f,22f)
        return Layout(p.loop, 11f,
            levels = listOf(Level(p.at(68f,42f),0f), Level(p.at(5f,42f),9f),
                Level(p.at(-30f,42f),9f), Level(p.at(-94f,0f),0f)),
            bumps = listOf(Bump(p.at(94f,0f),36f,2.5f)), pillarColor = 0x60708C)
    }
}
