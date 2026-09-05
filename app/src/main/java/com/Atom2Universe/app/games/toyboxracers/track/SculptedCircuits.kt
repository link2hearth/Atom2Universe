package com.Atom2Universe.app.games.toyboxracers.track

import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import kotlin.math.PI
import kotlin.math.cos

/** Rubans originaux avec relief indépendant. Les réceptions continues restent
 * franchissables au ralenti ; les crêtes font décoller avec suffisamment d'élan. */
internal object SculptedCircuits {
    private data class Hill(val start: Float, val end: Float, val height: Float)
    private data class Layout(val points: List<Pair<Float, Float>>, val hills: List<Hill>, val width: Float = 9f)
    private fun points(vararg coordinates: Int) = coordinates.toList().chunked(2)
        .map { (x, z) -> x.toFloat() to z.toFloat() }

    private val layouts = mapOf(
        CircuitKind.ROLLING_HILLS to Layout(
            points(-72,-38, -24,-40, 28,-38, 72,-30, 86,0, 66,34, 20,42, -30,40, -78,28, -88,0),
            listOf(Hill(.12f,.40f,9f), Hill(.48f,.70f,6f), Hill(.73f,.94f,4f)), 10f),
        CircuitKind.DOUBLE_BUMPS to Layout(
            points(-76,-38, -30,-38, 20,-38, 70,-32, 86,-8, 74,28,
                34,40, 0,16, -36,38, -78,30, -88,0),
            listOf(Hill(.075f,.135f,2.5f), Hill(.14f,.20f,2.5f),
                Hill(.33f,.45f,7f), Hill(.56f,.63f,2.8f), Hill(.64f,.71f,2.8f))),
        CircuitKind.HIGH_GARDEN to Layout(
            points(-76,-36, -26,-40, 30,-34, 78,-18, 82,16, 46,40,
                6,32, -2,4, -34,-4, -48,24, -78,36, -88,6),
            listOf(Hill(.09f,.81f,16f), Hill(.83f,.97f,2f)), 10f),
        CircuitKind.SWITCHBACKS to Layout(
            points(-78,-38, -24,-38, 32,-38, 78,-36, 86,-20, 64,-10,
                12,-10, -30,-8, -40,8, -18,22, 34,22, 76,28, 78,42,
                32,44, -24,42, -76,32, -88,4),
            listOf(Hill(.08f,.40f,11f), Hill(.44f,.68f,8f), Hill(.73f,.93f,5f)), 8f),
        CircuitKind.JUMP_PARADE to Layout(
            points(-76,-36, -30,-40, 22,-40, 72,-30, 86,0, 72,32,
                24,40, -26,40, -76,30, -88,0),
            listOf(Hill(.09f,.17f,3f), Hill(.19f,.27f,3.5f), Hill(.31f,.49f,8f),
                Hill(.56f,.64f,3f), Hill(.66f,.74f,3f), Hill(.79f,.94f,5f)), 10.5f),
        CircuitKind.RIBBON_RALLY to Layout(
            points(-78,-36, -38,-42, -6,-24, 28,-40, 72,-34, 86,-6,
                62,16, 28,4, 0,24, 34,42, -14,42, -46,20, -78,32, -88,0),
            listOf(Hill(.08f,.29f,5f), Hill(.32f,.58f,12f),
                Hill(.62f,.70f,2.5f), Hill(.72f,.80f,2.5f), Hill(.83f,.97f,4f)), 8.5f)
    )

    fun width(kind: CircuitKind): Float = layouts.getValue(kind).width

    fun point(kind: CircuitKind, fraction: Float): Vec3 {
        val layout = layouts.getValue(kind)
        val scaled = fraction * layout.points.size
        val index = scaled.toInt()
        val t = scaled - index
        fun p(offset: Int) = layout.points[(index + offset + layout.points.size) % layout.points.size]
        val a = p(-1); val b = p(0); val c = p(1); val d = p(2)
        fun spline(a: Float, b: Float, c: Float, d: Float): Float =
            .5f * (2f*b + (-a+c)*t + (2f*a-5f*b+4f*c-d)*t*t + (-a+3f*b-3f*c+d)*t*t*t)
        var height = 0f
        for (hill in layout.hills) {
            if (fraction in hill.start..hill.end) {
                val phase = (fraction - hill.start) / (hill.end - hill.start)
                height += hill.height * .5f * (1f - cos(phase * 2f * PI.toFloat()))
            }
        }
        return Vec3(spline(a.first,b.first,c.first,d.first), height, spline(a.second,b.second,c.second,d.second))
    }
}
