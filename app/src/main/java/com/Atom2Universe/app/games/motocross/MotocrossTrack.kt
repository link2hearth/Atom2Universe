package com.Atom2Universe.app.games.motocross

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Bibliothèque de blocs de piste portée depuis motocross.js.
 *
 * Chaque bloc est une polyligne lissée par spline Catmull-Rom. Les blocs sont
 * assemblés bout à bout en faisant correspondre [Block.slopeOut] et [Block.slopeIn]
 * (cf. pickNextBlock dans MotocrossView), ce qui garantit un sol continu.
 */

internal class Block(
    val id: String,
    val tags: Array<String>,
    val geo: Array<FloatArray>,   // points relatifs, geo[0] = [0, 0]
    val length: Float,
    val y1: Float,
    val slopeIn: Float,
    val slopeOut: Float,
    val minY: Float,
    val maxY: Float
)

internal class Segment(
    val p0x: Float, val p0y: Float,
    val p1x: Float, val p1y: Float,
    val tx: Float, val ty: Float,
    val nx: Float, val ny: Float,
    val minX: Float, val maxX: Float
)

private const val TRACK_LENGTH_MULTIPLIER = 2.6f
private const val TRACK_HEIGHT_MULTIPLIER = 2.8f
private const val PROFILE_AMPLITUDE_MULTIPLIER = 1.3f
private const val TRACK_CURVE_SUBDIVISIONS = 10
private const val TRACK_MIN_CURVE_STEP = 4f

private fun catmullRomScalar(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val t2 = t * t
    val t3 = t2 * t
    return 0.5f * (
        (2f * p1) +
            (-p0 + p2) * t +
            (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
            (-p0 + 3f * p1 - 3f * p2 + p3) * t3
        )
}

private fun computeSlope(a: FloatArray, b: FloatArray): Float {
    val dx = b[0] - a[0]
    if (abs(dx) < 1e-6f) return 0f
    return (b[1] - a[1]) / dx
}

private fun smoothPolyline(pointsIn: List<FloatArray>, subdivisions: Int): ArrayList<FloatArray> {
    if (pointsIn.size < 2) {
        return arrayListOf(floatArrayOf(0f, 0f), floatArrayOf(200f * TRACK_LENGTH_MULTIPLIER, 0f))
    }
    val result = ArrayList<FloatArray>()
    val segmentCount = pointsIn.size - 1
    val steps = max(1, subdivisions)
    for (i in 0 until segmentCount) {
        val p0 = pointsIn[max(0, i - 1)]
        val p1 = pointsIn[i]
        val p2 = pointsIn[i + 1]
        val p3 = pointsIn[min(pointsIn.size - 1, i + 2)]
        if (result.isEmpty()) result.add(floatArrayOf(p1[0], p1[1]))
        val samples = steps + 1
        for (s in 1..samples) {
            if (s == samples) { result.add(floatArrayOf(p2[0], p2[1])); continue }
            val t = s.toFloat() / samples
            var x = p1[0] + (p2[0] - p1[0]) * t
            val y = catmullRomScalar(p0[1], p1[1], p2[1], p3[1], t)
            if (result.isNotEmpty()) {
                val prevX = result[result.size - 1][0]
                if (x <= prevX) {
                    val span = abs(p2[0] - p1[0])
                    val minDelta = max(span / (samples + 1), TRACK_MIN_CURVE_STEP * 0.25f)
                    x = prevX + minDelta
                    if (x >= p2[0]) x = p2[0] - max(minDelta * 0.25f, 0.1f)
                    if (x <= prevX) continue
                }
            }
            result.add(floatArrayOf(x, y))
        }
    }
    return result
}

private fun polyFlat(length: Float): ArrayList<FloatArray> =
    arrayListOf(floatArrayOf(0f, 0f), floatArrayOf(length, 0f))

private fun polyProfile(length: Float, template: Array<FloatArray>, amplitude: Float, verticalOffset: Float): ArrayList<FloatArray> {
    if (template.size < 2) return polyFlat(length)
    val safeLength = max(length, 1f)
    val points = ArrayList<FloatArray>()
    points.add(floatArrayOf(0f, verticalOffset))
    for (i in 1 until template.size - 1) {
        val entry = template[i]
        val t = entry[0].coerceIn(0f, 1f)
        val x = t * safeLength
        val y = verticalOffset + amplitude * entry[1]
        points.add(floatArrayOf(x, y))
    }
    points.add(floatArrayOf(safeLength, verticalOffset))
    points.sortBy { it[0] }
    for (i in 1 until points.size) {
        if (points[i][0] <= points[i - 1][0]) {
            val previous = points[i - 1][0]
            val minimumStep = max(1f, safeLength * 0.01f)
            points[i][0] = min(safeLength, previous + minimumStep)
        }
    }
    points[0][0] = 0f
    points[points.size - 1][0] = safeLength
    return points
}

private fun prepareBlockGeometry(geo: List<FloatArray>): ArrayList<FloatArray> {
    if (geo.size < 2) {
        return arrayListOf(floatArrayOf(0f, 0f), floatArrayOf(200f * TRACK_LENGTH_MULTIPLIER, 0f))
    }
    val scaled = geo.map { floatArrayOf(it[0] * TRACK_LENGTH_MULTIPLIER, it[1] * TRACK_HEIGHT_MULTIPLIER) }
    return smoothPolyline(scaled, TRACK_CURVE_SUBDIVISIONS)
}

private fun createBlock(id: String, tags: Array<String>, rawGeo: List<FloatArray>): Block {
    val poly = prepareBlockGeometry(rawGeo)
    val start = poly[0]
    val end = poly[poly.size - 1]
    val slopeIn = computeSlope(poly[0], poly[1])
    val slopeOut = computeSlope(poly[poly.size - 2], end)
    val length = end[0] - start[0]
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (p in poly) { if (p[1] < minY) minY = p[1]; if (p[1] > maxY) maxY = p[1] }
    return Block(id, tags, poly.toTypedArray(), length, end[1] - start[1], slopeIn, slopeOut, minY, maxY)
}

private fun createProfileBlock(
    id: String, tags: Array<String>, length: Float, templateKey: String,
    amplitude: Float, verticalOffset: Float = 0f
): Block {
    val template = PROFILE_TEMPLATES[templateKey]
    val scaledAmplitude = amplitude * PROFILE_AMPLITUDE_MULTIPLIER
    val scaledOffset = verticalOffset * PROFILE_AMPLITUDE_MULTIPLIER
    val geometry = if (template != null) polyProfile(length, template, scaledAmplitude, scaledOffset) else polyFlat(length)
    return createBlock(id, tags, geometry)
}

private fun tpl(vararg pts: FloatArray): Array<FloatArray> = arrayOf(*pts)
private fun p(a: Float, b: Float) = floatArrayOf(a, b)

private val PROFILE_TEMPLATES: Map<String, Array<FloatArray>> = mapOf(
    "doubleHill" to tpl(p(0f, 0f), p(0.12f, -0.45f), p(0.24f, 0.05f), p(0.44f, -0.88f), p(0.64f, -0.18f), p(0.82f, -0.62f), p(1f, 0f)),
    "softWave" to tpl(p(0f, 0f), p(0.18f, -0.35f), p(0.36f, -0.08f), p(0.58f, 0.32f), p(0.78f, 0.08f), p(0.92f, -0.18f), p(1f, 0f)),
    "ridgeDrop" to tpl(p(0f, 0f), p(0.1f, -0.18f), p(0.22f, -0.52f), p(0.38f, -0.68f), p(0.52f, -0.42f), p(0.68f, -0.12f), p(0.84f, 0.08f), p(1f, 0f)),
    "rolling" to tpl(p(0f, 0f), p(0.12f, -0.35f), p(0.26f, 0.32f), p(0.4f, -0.46f), p(0.56f, 0.46f), p(0.72f, -0.3f), p(0.88f, 0.2f), p(1f, 0f)),
    "mellow" to tpl(p(0f, 0f), p(0.16f, -0.28f), p(0.34f, 0.18f), p(0.52f, 0.3f), p(0.72f, 0.08f), p(0.9f, -0.12f), p(1f, 0f)),
    "shallowValley" to tpl(p(0f, 0f), p(0.2f, 0.22f), p(0.4f, 0.48f), p(0.5f, 0.6f), p(0.62f, 0.42f), p(0.82f, 0.16f), p(1f, 0f)),
    "deepValley" to tpl(p(0f, 0f), p(0.12f, 0.18f), p(0.28f, 0.52f), p(0.42f, 0.74f), p(0.58f, 0.74f), p(0.72f, 0.52f), p(0.86f, 0.18f), p(1f, 0f)),
    "bowl" to tpl(p(0f, 0f), p(0.14f, 0.12f), p(0.32f, 0.42f), p(0.5f, 0.72f), p(0.68f, 0.42f), p(0.86f, 0.12f), p(1f, 0f)),
    "landingSlope" to tpl(p(0f, 0f), p(0.26f, 0.08f), p(0.52f, -0.18f), p(0.78f, -0.32f), p(1f, 0f)),
    "closingRise" to tpl(p(0f, 0f), p(0.24f, -0.32f), p(0.48f, -0.58f), p(0.74f, -0.24f), p(1f, 0f)),
    "gentleRipples" to tpl(p(0f, 0f), p(0.1f, -0.12f), p(0.22f, 0.08f), p(0.36f, -0.16f), p(0.52f, 0.14f), p(0.68f, -0.12f), p(0.84f, 0.06f), p(0.94f, -0.08f), p(1f, 0f)),
    "longDescent" to tpl(p(0f, 0f), p(0.16f, 0.12f), p(0.36f, -0.4f), p(0.56f, -0.78f), p(0.74f, -0.88f), p(0.88f, -0.54f), p(1f, 0f)),
    "longClimb" to tpl(p(0f, 0f), p(0.12f, -0.18f), p(0.34f, 0f), p(0.58f, 0.18f), p(0.78f, 0.34f), p(0.9f, 0.28f), p(1f, 0f)),
    "plateauRollers" to tpl(p(0f, 0f), p(0.14f, -0.12f), p(0.3f, -0.08f), p(0.46f, 0.24f), p(0.6f, 0.2f), p(0.74f, -0.06f), p(0.86f, -0.14f), p(1f, 0f)),
    "cascadeWaves" to tpl(p(0f, 0f), p(0.08f, -0.22f), p(0.18f, -0.06f), p(0.32f, 0.34f), p(0.46f, 0.58f), p(0.6f, 0.16f), p(0.74f, -0.42f), p(0.88f, -0.24f), p(1f, 0f)),
    "smoothHump" to tpl(p(0f, 0f), p(0.15f, -0.12f), p(0.35f, -0.45f), p(0.5f, -0.55f), p(0.65f, -0.45f), p(0.85f, -0.12f), p(1f, 0f)),
    "gentleSlalom" to tpl(p(0f, 0f), p(0.12f, -0.15f), p(0.24f, 0.1f), p(0.38f, -0.2f), p(0.52f, 0.15f), p(0.66f, -0.18f), p(0.8f, 0.08f), p(0.92f, -0.06f), p(1f, 0f)),
    "upperPlatform" to tpl(p(0f, 0f), p(0.1f, -0.2f), p(0.2f, -0.55f), p(0.3f, -0.75f), p(0.4f, -0.8f), p(0.6f, -0.8f), p(0.7f, -0.75f), p(0.8f, -0.55f), p(0.9f, -0.2f), p(1f, 0f)),
    "doubleArch" to tpl(p(0f, 0f), p(0.08f, -0.22f), p(0.18f, -0.38f), p(0.28f, -0.22f), p(0.38f, 0f), p(0.5f, -0.15f), p(0.62f, -0.42f), p(0.72f, -0.38f), p(0.82f, -0.18f), p(0.92f, -0.05f), p(1f, 0f))
)

internal val START_BLOCK: Block = createBlock("flat/start/01", arrayOf("flat", "easy", "starter"), polyFlat(200f))

internal val BLOCK_LIBRARY: List<Block> = listOf(
    START_BLOCK,
    createBlock("flat/easy/02", arrayOf("flat", "easy"), polyFlat(240f)),
    createBlock("flat/normal/01", arrayOf("flat", "normal"), polyFlat(300f)),
    createProfileBlock("flow/double/easy/01", arrayOf("flow", "easy"), 280f, "doubleHill", 28f),
    createProfileBlock("flow/double/normal/01", arrayOf("flow", "normal"), 320f, "doubleHill", 42f),
    createProfileBlock("flow/wave/easy/01", arrayOf("wave", "easy"), 260f, "softWave", 22f),
    createProfileBlock("flow/wave/normal/01", arrayOf("wave", "normal"), 300f, "softWave", 34f),
    createProfileBlock("crest/launch/easy/01", arrayOf("crest", "easy"), 280f, "ridgeDrop", 36f),
    createProfileBlock("crest/launch/normal/01", arrayOf("crest", "normal"), 320f, "ridgeDrop", 48f),
    createProfileBlock("rhythm/mellow/easy/01", arrayOf("rhythm", "easy"), 240f, "mellow", 20f),
    createProfileBlock("rhythm/mellow/normal/01", arrayOf("rhythm", "normal"), 280f, "mellow", 30f),
    createProfileBlock("valley/shallow/easy/01", arrayOf("valley", "easy"), 260f, "shallowValley", 32f),
    createProfileBlock("valley/shallow/normal/01", arrayOf("valley", "normal"), 300f, "shallowValley", 44f),
    createProfileBlock("valley/deep/easy/01", arrayOf("valley", "easy"), 280f, "deepValley", 38f),
    createProfileBlock("valley/deep/normal/01", arrayOf("valley", "normal"), 320f, "deepValley", 54f),
    createProfileBlock("valley/bowl/easy/01", arrayOf("valley", "easy"), 280f, "bowl", 34f),
    createProfileBlock("valley/bowl/normal/01", arrayOf("valley", "normal"), 320f, "bowl", 48f),
    createProfileBlock("landing/flow/easy/01", arrayOf("landing_pad", "easy"), 280f, "landingSlope", 26f),
    createProfileBlock("landing/flow/normal/01", arrayOf("landing_pad", "normal"), 320f, "landingSlope", 32f),
    createProfileBlock("closing/rise/easy/01", arrayOf("flow", "easy"), 260f, "closingRise", 26f),
    createProfileBlock("closing/rise/normal/01", arrayOf("flow", "normal"), 300f, "closingRise", 36f),
    createProfileBlock("flow/double/easy/long/01", arrayOf("flow", "easy"), 620f, "doubleHill", 36f),
    createProfileBlock("flow/double/easy/long/02", arrayOf("flow", "easy"), 760f, "doubleHill", 42f),
    createProfileBlock("rhythm/rolling/easy/long/01", arrayOf("rhythm", "easy"), 660f, "rolling", 34f),
    createProfileBlock("rhythm/ripples/easy/long/01", arrayOf("rhythm", "easy"), 600f, "gentleRipples", 18f),
    createProfileBlock("rhythm/ripples/easy/long/02", arrayOf("rhythm", "easy"), 720f, "gentleRipples", 22f),
    createProfileBlock("descent/long/easy/01", arrayOf("descent", "easy"), 700f, "longDescent", 44f, -6f),
    createProfileBlock("descent/long/easy/02", arrayOf("descent", "easy"), 860f, "longDescent", 52f, -4f),
    createProfileBlock("climb/long/easy/01", arrayOf("climb", "easy"), 680f, "longClimb", 42f, 8f),
    createProfileBlock("climb/long/easy/02", arrayOf("climb", "easy"), 820f, "longClimb", 48f, 10f),
    createProfileBlock("flow/plateau/easy/long/01", arrayOf("flow", "easy"), 640f, "plateauRollers", 26f, 6f),
    createProfileBlock("flow/cascade/easy/01", arrayOf("flow", "easy"), 720f, "cascadeWaves", 32f),
    createProfileBlock("hump/smooth/easy/01", arrayOf("hump", "easy"), 320f, "smoothHump", 28f),
    createProfileBlock("hump/smooth/easy/02", arrayOf("hump", "easy"), 420f, "smoothHump", 35f),
    createProfileBlock("slalom/gentle/easy/01", arrayOf("slalom", "easy"), 380f, "gentleSlalom", 22f),
    createProfileBlock("slalom/gentle/easy/02", arrayOf("slalom", "easy"), 520f, "gentleSlalom", 28f),
    createProfileBlock("platform/upper/easy/01", arrayOf("platform", "easy"), 400f, "upperPlatform", 45f),
    createProfileBlock("platform/upper/easy/02", arrayOf("platform", "easy"), 560f, "upperPlatform", 55f),
    createProfileBlock("arch/double/easy/01", arrayOf("arch", "easy"), 380f, "doubleArch", 30f),
    createProfileBlock("arch/double/easy/02", arrayOf("arch", "easy"), 500f, "doubleArch", 40f)
)

internal val TRACK_BLOCKS: List<Block> = BLOCK_LIBRARY.filter { it !== START_BLOCK }
internal val LANDING_BLOCKS: List<Block> = TRACK_BLOCKS.filter { it.tags.contains("landing_pad") }
