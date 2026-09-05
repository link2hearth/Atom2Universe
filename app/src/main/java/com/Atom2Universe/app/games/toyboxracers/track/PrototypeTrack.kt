package com.Atom2Universe.app.games.toyboxracers.track

import kotlin.math.atan2
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement

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
internal class PrototypeTrack(
    sampleCount: Int = 480,
    decorations: List<DecorPlacement> = emptyList(),
    val scene: SceneChoice = SceneChoice()
) {
    val decorations = RaceLayouts.decorations(scene) + decorations
    val roomBoxes = RaceLayouts.boxes(scene)
    val furnitureSolids: List<RoomBox> = RaceLayouts.solids(scene) + this.decorations.flatMap { it.solids }

    fun furnitureHeightAt(x: Float, z: Float, maximumY: Float): Float {
        var height = 0f
        for (box in furnitureSolids) {
            if (x in box.left..box.right && z in box.back..box.front && box.top <= maximumY)
                height = maxOf(height, box.top)
        }
        return height
    }

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

    /** Une zone de croisement, exprimée en distance de piste plutôt qu'en fraction. */
    data class CrossingRange(val startDistance: Float, val endDistance: Float)

    private val crossingRanges: List<CrossingRange>

    /** Début et fin de la portion sans route, exprimés en distance de piste.
     * Conservés pour compat (RivalCar, tests) : reflètent le premier croisement du circuit. */
    val jumpStartDistance: Float
    val jumpEndDistance: Float

    val toyObstacles = RaceLayouts.toys(scene)

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

        crossingRanges = CircuitCrossings.crossingsFor(scene.circuit).map { crossing ->
            CrossingRange(
                samples.first { it.fraction >= crossing.gapStartFraction }.distance,
                samples.first { it.fraction >= crossing.gapEndFraction }.distance
            )
        }
        jumpStartDistance = crossingRanges.firstOrNull()?.startDistance ?: 0f
        jumpEndDistance = crossingRanges.firstOrNull()?.endDistance ?: 0f
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
            fraction = first.fraction + ((if (secondIndex == 0) 1f else second.fraction) - first.fraction) * alpha,
            roadWidth = first.roadWidth + (second.roadWidth - first.roadWidth) * alpha
        )
    }

    fun allSamples(): List<Sample> = samples

    fun surface(sample: Sample): CourseSurface = if (scene.circuit.usesFurnitureLayout)
        OrganicCircuits.surface(scene.circuit, sample.fraction) else CourseSurface.DECK

    fun hasDeck(sample: Sample) = surface(sample) == CourseSurface.DECK && !isJumpGap(sample.distance)

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
        val dz = worldZ - nearest.position.z
        // L'altitude choisit la branche au croisement, mais ne doit pas décaler
        // la surface sous les roues vers l'arrière lorsqu'on monte une bosse.
        val horizontalSquared = nearest.tangent.x * nearest.tangent.x + nearest.tangent.z * nearest.tangent.z
        val along = (dx * nearest.tangent.x + dz * nearest.tangent.z) / horizontalSquared.coerceAtLeast(0.0001f)
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
            if (!hasDeck(sample)) continue
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
        if (!hasDeck(refined)) return null
        val refinedDx = worldX - refined.position.x
        val refinedDz = worldZ - refined.position.z
        return Projection(
            sample = refined,
            lateralOffset = refinedDx * refined.right.x + refinedDz * refined.right.z,
            horizontalDistance = sqrt(refinedDx * refinedDx + refinedDz * refinedDz)
        )
    }

    fun isJumpGap(distance: Float): Boolean {
        if (crossingRanges.isEmpty()) return false
        val wrapped = wrapDistance(distance)
        return crossingRanges.any { wrapped in it.startDistance..it.endDistance }
    }

    /** Zone de croisement contenant cette distance, ou null. Généralise l'ancien
     * couple jumpStartDistance/jumpEndDistance pour un circuit à plusieurs croisements. */
    fun crossingAt(distance: Float): CrossingRange? {
        val wrapped = wrapDistance(distance)
        return crossingRanges.firstOrNull { wrapped in it.startDistance..it.endDistance }
    }

    fun wrapDistance(distance: Float): Float {
        var wrapped = distance % length
        if (wrapped < 0f) wrapped += length
        return wrapped
    }

    fun headingRadians(sample: Sample): Float = atan2(sample.tangent.x, sample.tangent.z)

    private fun point(fraction: Float): Vec3 {
        if (scene.circuit.usesFurnitureLayout) return OrganicCircuits.point(scene.circuit, fraction)
        if (scene.circuit.usesSculptedLayout) return SculptedCircuits.point(scene.circuit, fraction)
        if (scene.circuit.usesCrossroadsLayout) return CrossroadsCircuit.point(fraction)
        if (scene.circuit == CircuitKind.SLALOM) return slalomPoint(fraction)
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
        if (scene.circuit.usesFurnitureLayout) return when (OrganicCircuits.surface(scene.circuit, fraction)) {
            CourseSurface.DECK -> 9f
            CourseSurface.FLOOR -> 18f
            CourseSurface.FURNITURE -> 16f
        }
        if (scene.circuit.usesSculptedLayout) return SculptedCircuits.width(scene.circuit)
        if (scene.circuit.usesCrossroadsLayout) return CrossroadsCircuit.width(fraction)
        if (scene.circuit == CircuitKind.SLALOM) return 7f
        // Le plateau et la réception pardonnent davantage les erreurs.
        val nearJump = fraction in 0.42f..0.60f
        return if (nearJump) 10.5f else 8.5f
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerp(a: Vec3, b: Vec3, t: Float) = a + (b - a) * t

    /** Spline fermée à tangente continue ; toutes les coordonnées Y restent à zéro. */
    private fun slalomPoint(fraction: Float): Vec3 {
        val scaled = fraction * SLALOM_POINTS.size
        val index = scaled.toInt() % SLALOM_POINTS.size
        val t = scaled - scaled.toInt()
        fun p(offset: Int) = SLALOM_POINTS[(index + offset + SLALOM_POINTS.size) % SLALOM_POINTS.size]
        val a = p(-1); val b = p(0); val c = p(1); val d = p(2)
        fun component(axis: Int): Float = 0.5f * ((2f * b[axis]) + (-a[axis] + c[axis]) * t +
            (2f * a[axis] - 5f * b[axis] + 4f * c[axis] - d[axis]) * t * t +
            (-a[axis] + 3f * b[axis] - 3f * c[axis] + d[axis]) * t * t * t)
        return Vec3(component(0), 0f, component(1))
    }

    companion object {
        private val SLALOM_POINTS = arrayOf(
            floatArrayOf(-78f, -44f), floatArrayOf(-28f, -44f), floatArrayOf(28f, -44f),
            floatArrayOf(64f, -44f), floatArrayOf(84f, -28f), floatArrayOf(64f, -12f),
            floatArrayOf(4f, -12f), floatArrayOf(-18f, 6f), floatArrayOf(4f, 24f),
            floatArrayOf(64f, 24f), floatArrayOf(84f, 42f), floatArrayOf(64f, 56f),
            floatArrayOf(-62f, 56f), floatArrayOf(-86f, 32f), floatArrayOf(-64f, 6f),
            floatArrayOf(-48f, -14f), floatArrayOf(-72f, -26f), floatArrayOf(-88f, -28f)
        )
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
        const val ROOM_WALL_HEIGHT = 32f
    }
}
