package com.Atom2Universe.app.games.toyboxracers.track

import kotlin.math.atan2
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class ToyKind { BLOCKS, TEDDY, TRAIN, SPINNING_TOP }

internal data class ToyObstacle(
    val kind: ToyKind,
    val x: Float,
    val z: Float,
    val radius: Float,
    val height: Float
)

/**
 * Piste procédurale fermée de la phase P1.
 *
 * La trajectoire horizontale forme un grand huit riche en courbes.
 * L'altitude est volontairement indépendante : montée, plateau haut, tremplin,
 * vide, puis réception au sol. Cette séparation permet de remplacer plus tard
 * les volumes de test par le lit et le bureau sans réécrire la conduite.
 */
internal class PrototypeTrack(sampleCount: Int = 480) {

    data class Vec3(val x: Float, val y: Float, val z: Float) {
        operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
        operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
        operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)

        fun normalized(): Vec3 {
            val length = sqrt(x * x + y * y + z * z).coerceAtLeast(0.0001f)
            return Vec3(x / length, y / length, z / length)
        }
    }

    data class Sample(
        val position: Vec3,
        val tangent: Vec3,
        val right: Vec3,
        val distance: Float,
        val fraction: Float,
        val roadWidth: Float
    )

    data class Projection(
        val sample: Sample,
        val lateralOffset: Float,
        val horizontalDistance: Float
    )

    private val samples: List<Sample>
    val length: Float

    /** Début et fin de la portion sans route, exprimés en distance de piste. */
    val jumpStartDistance: Float
    val jumpEndDistance: Float

    val toyObstacles = listOf(
        ToyObstacle(ToyKind.BLOCKS, -65f, 0f, 6.5f, 7f),
        ToyObstacle(ToyKind.TEDDY, 65f, 0f, 5.5f, 10f),
        ToyObstacle(ToyKind.TRAIN, 0f, -62f, 8f, 7f),
        ToyObstacle(ToyKind.SPINNING_TOP, 0f, 62f, 5f, 8f)
    )

    init {
        val raw = ArrayList<Vec3>(sampleCount)
        repeat(sampleCount) { index ->
            raw += point(index.toFloat() / sampleCount)
        }

        val cumulative = FloatArray(sampleCount + 1)
        for (i in 1..sampleCount) {
            val a = raw[i - 1]
            val b = raw[i % sampleCount]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val dz = b.z - a.z
            cumulative[i] = cumulative[i - 1] + sqrt(dx * dx + dy * dy + dz * dz)
        }
        length = cumulative.last()

        samples = List(sampleCount) { index ->
            val previous = raw[(index - 1 + sampleCount) % sampleCount]
            val next = raw[(index + 1) % sampleCount]
            val tangent = (next - previous).normalized()
            val horizontalLength = sqrt(tangent.x * tangent.x + tangent.z * tangent.z)
                .coerceAtLeast(0.0001f)
            val right = Vec3(tangent.z / horizontalLength, 0f, -tangent.x / horizontalLength)
            Sample(
                position = raw[index],
                tangent = tangent,
                right = right,
                distance = cumulative[index],
                fraction = index.toFloat() / sampleCount,
                roadWidth = roadWidth(index.toFloat() / sampleCount)
            )
        }

        jumpStartDistance = samples.first { it.fraction >= JUMP_START_FRACTION }.distance
        jumpEndDistance = samples.first { it.fraction >= JUMP_END_FRACTION }.distance
    }

    fun sampleAt(distance: Float): Sample {
        val wrapped = wrapDistance(distance)
        // Les échantillons sont assez denses pour que la recherche linéaire locale
        // par fraction de longueur soit plus stable que de supposer un pas constant.
        var low = 0
        var high = samples.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (samples[middle].distance <= wrapped) low = middle + 1 else high = middle - 1
        }
        val firstIndex = high.coerceAtLeast(0)
        val secondIndex = (firstIndex + 1) % samples.size
        val first = samples[firstIndex]
        val second = samples[secondIndex]
        val secondDistance = if (secondIndex == 0) length else second.distance
        val span = (secondDistance - first.distance).coerceAtLeast(0.0001f)
        val alpha = ((wrapped - first.distance) / span).coerceIn(0f, 1f)
        return Sample(
            position = lerp(first.position, second.position, alpha),
            tangent = lerp(first.tangent, second.tangent, alpha).normalized(),
            right = lerp(first.right, second.right, alpha).normalized(),
            distance = wrapped,
            fraction = wrapped / length,
            roadWidth = first.roadWidth + (second.roadWidth - first.roadWidth) * alpha
        )
    }

    fun allSamples(): List<Sample> = samples

    /**
     * Projette une position libre sur l'axe de la piste. La voiture ne suit pas
     * cette projection : elle sert uniquement à connaître la surface, le hors-
     * piste, le saut et la progression du tour.
     */
    fun project(worldX: Float, worldY: Float, worldZ: Float, hintDistance: Float): Projection {
        var nearest = samples.first()
        var nearestScore = Float.MAX_VALUE
        for (sample in samples) {
            val dx = worldX - sample.position.x
            val dy = worldY - (sample.position.y + ROAD_SURFACE_LIFT + CAR_CLEARANCE)
            val dz = worldZ - sample.position.z
            val directProgress = kotlin.math.abs(sample.distance - wrapDistance(hintDistance))
            val progressDifference = minOf(directProgress, length - directProgress)
            // L'altitude distingue les deux routes au croisement. La pénalité de
            // progression empêche un changement de branche pendant le saut.
            val score = dx * dx + dz * dz + dy * dy * 1.5f +
                progressDifference * progressDifference * 0.018f
            if (score < nearestScore) {
                nearest = sample
                nearestScore = score
            }
        }

        // Corrige la quantification du tableau en avançant légèrement sur la
        // tangente, puis recalcule le décalage latéral sur l'échantillon affiné.
        val dx = worldX - nearest.position.x
        val dy = worldY - (nearest.position.y + ROAD_SURFACE_LIFT + CAR_CLEARANCE)
        val dz = worldZ - nearest.position.z
        val along = dx * nearest.tangent.x + dy * nearest.tangent.y + dz * nearest.tangent.z
        val refined = sampleAt(nearest.distance + along)
        val refinedDx = worldX - refined.position.x
        val refinedDz = worldZ - refined.position.z
        val lateral = refinedDx * refined.right.x + refinedDz * refined.right.z
        val horizontal = sqrt(refinedDx * refinedDx + refinedDz * refinedDz)
        return Projection(refined, lateral, horizontal)
    }

    /**
     * Cherche la dalle physiquement la plus proche sans favoriser la progression.
     * Cette seconde projection permet notamment de heurter le dessous de la
     * branche haute même lorsque la progression reste associée à la branche basse.
     */
    fun projectForCollision(worldX: Float, worldY: Float, worldZ: Float): Projection? {
        var nearest: Sample? = null
        var nearestScore = Float.MAX_VALUE
        for (sample in samples) {
            if (isJumpGap(sample.distance)) continue
            val dx = worldX - sample.position.x
            val dz = worldZ - sample.position.z
            val slabCenterY = sample.position.y + ROAD_SURFACE_LIFT - ROAD_THICKNESS * 0.5f
            val dy = worldY - slabCenterY
            val score = dx * dx + dz * dz + dy * dy * 1.5f
            if (score < nearestScore) {
                nearest = sample
                nearestScore = score
            }
        }
        val first = nearest ?: return null
        val dx = worldX - first.position.x
        val dz = worldZ - first.position.z
        val horizontalTangentSquared = first.tangent.x * first.tangent.x + first.tangent.z * first.tangent.z
        val along = (dx * first.tangent.x + dz * first.tangent.z) /
            horizontalTangentSquared.coerceAtLeast(0.0001f)
        val refined = sampleAt(first.distance + along)
        if (isJumpGap(refined.distance)) return null
        val refinedDx = worldX - refined.position.x
        val refinedDz = worldZ - refined.position.z
        return Projection(
            sample = refined,
            lateralOffset = refinedDx * refined.right.x + refinedDz * refined.right.z,
            horizontalDistance = sqrt(refinedDx * refinedDx + refinedDz * refinedDz)
        )
    }

    fun isJumpGap(distance: Float): Boolean {
        val wrapped = wrapDistance(distance)
        return wrapped in jumpStartDistance..jumpEndDistance
    }

    fun wrapDistance(distance: Float): Float {
        var wrapped = distance % length
        if (wrapped < 0f) wrapped += length
        return wrapped
    }

    fun headingRadians(sample: Sample): Float = atan2(sample.tangent.x, sample.tangent.z)

    private fun point(fraction: Float): Vec3 {
        val angle = fraction * 2f * PI.toFloat()
        // Lemniscate de Gerono : les deux passages au centre ont des directions
        // différentes et une courbure nulle, idéale pour placer le grand saut.
        val x = TRACK_HALF_WIDTH * sin(angle)
        val z = TRACK_HALF_DEPTH * sin(angle * 2f)
        return Vec3(x, groundHeightAt(x, z) + altitude(fraction), z)
    }

    /** Hauteur du plancher, conservée comme fonction pour les futurs sols spéciaux. */
    fun groundHeightAt(@Suppress("UNUSED_PARAMETER") x: Float, @Suppress("UNUSED_PARAMETER") z: Float) = 0f

    private fun altitude(fraction: Float): Float = when {
        fraction < 0.20f -> 0f
        fraction < 0.38f -> smoothStep((fraction - 0.20f) / 0.18f) * HIGH_LEVEL
        fraction < 0.44f -> HIGH_LEVEL
        fraction < JUMP_START_FRACTION -> {
            val ramp = smoothStep((fraction - 0.44f) / (JUMP_START_FRACTION - 0.44f))
            HIGH_LEVEL + ramp * RAMP_RISE
        }
        fraction < JUMP_END_FRACTION -> HIGH_LEVEL + RAMP_RISE
        fraction < 0.72f -> 0f
        else -> 0f
    }

    private fun roadWidth(fraction: Float): Float {
        // Le plateau et la réception pardonnent davantage les erreurs.
        val nearJump = fraction in 0.42f..0.60f
        return if (nearJump) 10.5f else 8.5f
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerp(a: Vec3, b: Vec3, t: Float) = a + (b - a) * t

    companion object {
        const val JUMP_START_FRACTION = 0.48f
        const val JUMP_END_FRACTION = 0.52f
        const val HIGH_LEVEL = 14.0f
        const val RAMP_RISE = 2.2f
        const val CAR_CLEARANCE = 0.24f
        const val ROAD_SURFACE_LIFT = 0.035f
        const val ROAD_THICKNESS = 0.85f
        const val CURB_WIDTH = 0.22f
        private const val TRACK_HALF_WIDTH = 105f
        private const val TRACK_HALF_DEPTH = 62f
        const val ROOM_HALF_WIDTH = 118f
        const val ROOM_HALF_DEPTH = 75f
        const val ROOM_WALL_HEIGHT = 18f
    }
}
