package com.Atom2Universe.app.games.toyboxracers.editor

import com.Atom2Universe.app.games.toyboxracers.track.HouseGeometry
import com.Atom2Universe.app.games.toyboxracers.track.RoomBox
import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal enum class ToyboxVolumeKind(val label: String, val color: Int, val solidByDefault: Boolean) {
    FLOOR("Sol", 0xFFD7E6E4.toInt(), true),
    WALL("Mur", 0xFFD2DDED.toInt(), true),
    DOOR("Porte", 0xFFA8D1B7.toInt(), false),
    WINDOW("Fenetre", 0xFF9FD2E3.toInt(), false),
    RAIL("Rambarde", 0xFFF7E7C8.toInt(), true),
    RAMP("Rampe", 0xFFC08A55.toInt(), true),
    STAIR("Marche", 0xFFF3E9D7.toInt(), true),
    DUCT("Conduit", 0xFF8DA7B3.toInt(), true),
    FURNITURE("Meuble", 0xFFDDB4C3.toInt(), true),
    DECOR("Decor", 0xFFE8C36E.toInt(), false)
}

internal data class ToyboxVolume(
    val id: Long,
    val kind: ToyboxVolumeKind,
    val x: Float,
    val y: Float,
    val z: Float,
    val width: Float,
    val height: Float,
    val depth: Float,
    val solid: Boolean = kind.solidByDefault,
    val color: Int = kind.color,
    val quarterTurns: Int = 0,
    val yawDegrees: Float = quarterTurns * 90f,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f
) {
    val normalizedYaw get() = ((yawDegrees % 360f) + 360f) % 360f
    val rotation get() = (((normalizedYaw / 90f).toInt() % 4) + 4) % 4
    val yawRadians get() = normalizedYaw * kotlin.math.PI.toFloat() / 180f
    val pitchRadians get() = pitchDegrees * kotlin.math.PI.toFloat() / 180f
    val rollRadians get() = rollDegrees * kotlin.math.PI.toFloat() / 180f
    val yawCos get() = cos(yawRadians)
    val yawSin get() = sin(yawRadians)
    val pitchCos get() = cos(pitchRadians)
    val pitchSin get() = sin(pitchRadians)
    val rollCos get() = cos(rollRadians)
    val rollSin get() = sin(rollRadians)
    private val corners get() = buildList {
        for (lx in floatArrayOf(-width * 0.5f, width * 0.5f)) {
            for (ly in floatArrayOf(-height * 0.5f, height * 0.5f)) {
                for (lz in floatArrayOf(-depth * 0.5f, depth * 0.5f)) add(worldPoint(lx, ly, lz))
            }
        }
    }
    val worldWidth get() = right - left
    val worldDepth get() = front - back
    val left get() = corners.minOf { it.x }
    val right get() = corners.maxOf { it.x }
    val back get() = corners.minOf { it.z }
    val front get() = corners.maxOf { it.z }

    fun moveTo(nx: Float, nz: Float) = copy(x = nx, z = nz)
    fun resize(dw: Float, dd: Float) = copy(
        width = (width + dw).coerceAtLeast(1f),
        depth = (depth + dd).coerceAtLeast(1f)
    )
    fun lift(dy: Float) = copy(y = (y + dy).coerceAtLeast(0f))
    fun taller(dh: Float) = copy(height = (height + dh).coerceAtLeast(0.25f))
    fun toggleSolid() = copy(solid = !solid)
    fun rotateQuarter(delta: Int = 1) = rotateYaw(delta * 90f)
    fun rotateYaw(deltaDegrees: Float) = withYaw(yawDegrees + deltaDegrees)
    fun rotatePitch(deltaDegrees: Float) = copy(pitchDegrees = pitchDegrees + deltaDegrees)
    fun rotateRoll(deltaDegrees: Float) = copy(rollDegrees = rollDegrees + deltaDegrees)
    fun withYaw(degrees: Float): ToyboxVolume {
        val normalized = ((degrees % 360f) + 360f) % 360f
        val snappedQuarter = ((normalized / 90f).toInt() % 4 + 4) % 4
        return copy(yawDegrees = normalized, quarterTurns = snappedQuarter)
    }

    fun localX(worldX: Float, worldZ: Float): Float {
        val dx = worldX - x
        val dz = worldZ - z
        return dx * yawCos - dz * yawSin
    }

    fun localZ(worldX: Float, worldZ: Float): Float {
        val dx = worldX - x
        val dz = worldZ - z
        return dx * yawSin + dz * yawCos
    }

    fun worldX(localX: Float, localZ: Float): Float = x + localX * yawCos + localZ * yawSin
    fun worldZ(localX: Float, localZ: Float): Float = z - localX * yawSin + localZ * yawCos

    fun worldPoint(localX: Float, localY: Float, localZ: Float): VolumePoint {
        val pitchedY = localY + localZ * pitchSin
        val pitchedZ = localZ * pitchCos - localY * pitchSin
        val rolledX = localX * rollCos - pitchedY * rollSin
        val rolledY = localX * rollSin + pitchedY * rollCos
        return VolumePoint(
            x = x + rolledX * yawCos + pitchedZ * yawSin,
            y = y + rolledY,
            z = z - rolledX * yawSin + pitchedZ * yawCos
        )
    }

    fun localPoint(worldX: Float, worldY: Float, worldZ: Float): VolumePoint {
        val dx = worldX - x
        val dy = worldY - y
        val dz = worldZ - z
        val yawedX = dx * yawCos - dz * yawSin
        val yawedZ = dx * yawSin + dz * yawCos
        val unrolledX = yawedX * rollCos + dy * rollSin
        val unrolledY = -yawedX * rollSin + dy * rollCos
        val unpitchedY = unrolledY * pitchCos - yawedZ * pitchSin
        val unpitchedZ = unrolledY * pitchSin + yawedZ * pitchCos
        return VolumePoint(unrolledX, unpitchedY, unpitchedZ)
    }

    fun containsXZ(worldX: Float, worldZ: Float): Boolean =
        worldX in left..right && worldZ in back..front

    fun toJson() = JSONObject()
        .put("id", id)
        .put("kind", kind.name)
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("z", z.toDouble())
        .put("width", width.toDouble())
        .put("height", height.toDouble())
        .put("depth", depth.toDouble())
        .put("solid", if (kind == ToyboxVolumeKind.FLOOR) true else solid)
        .put("color", color)
        .put("quarterTurns", quarterTurns)
        .put("yawDegrees", yawDegrees.toDouble())
        .put("pitchDegrees", pitchDegrees.toDouble())
        .put("rollDegrees", rollDegrees.toDouble())

    companion object {
        fun fromJson(json: JSONObject): ToyboxVolume {
            val kind = ToyboxVolumeKind.entries.find { it.name == json.optString("kind") }
                ?: ToyboxVolumeKind.FURNITURE
            val quarterTurns = json.optInt("quarterTurns", 0)
            return ToyboxVolume(
                id = json.optLong("id", System.nanoTime()),
                kind = kind,
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat(),
                z = json.optDouble("z", 0.0).toFloat(),
                width = json.optDouble("width", 12.0).toFloat().coerceAtLeast(1f),
                height = json.optDouble("height", 4.0).toFloat().coerceAtLeast(0.25f),
                depth = json.optDouble("depth", 12.0).toFloat().coerceAtLeast(1f),
                solid = if (kind == ToyboxVolumeKind.FLOOR) true else json.optBoolean("solid", true),
                color = json.optInt("color", kind.color),
                quarterTurns = quarterTurns,
                yawDegrees = json.optDouble("yawDegrees", quarterTurns * 90.0).toFloat(),
                pitchDegrees = json.optDouble("pitchDegrees", 0.0).toFloat(),
                rollDegrees = json.optDouble("rollDegrees", 0.0).toFloat()
            )
        }
    }
}

internal data class VolumePoint(val x: Float, val y: Float, val z: Float)

/** Normalized longitudinal position, lateral displacement and altitude above the end-to-end slope. */
internal data class TrackBend(val t: Float, val side: Float, val height: Float, val along: Float = 0f)

/** Which edge of the ribbon a banking edit applies to: the two edges tilt independently. */
internal enum class TrackEdge(val label: String) {
    LEFT("Bord G"),
    BOTH("Bords G+D"),
    RIGHT("Bord D");

    fun next() = when (this) {
        LEFT -> BOTH
        BOTH -> RIGHT
        RIGHT -> LEFT
    }
}

internal data class ToyboxTrackSection(
    val id: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val yawDegrees: Float,
    val length: Float,
    val width: Float,
    val endY: Float = y,
    val bankDegrees: Float = 0f,
    val rightBankDegrees: Float = bankDegrees,
    val color: Int = 0xFF6F7B91.toInt(),
    val startYawOffset: Float = 0f,
    val endYawOffset: Float = 0f,
    val endWidth: Float = width,
    val bends: List<TrackBend> = emptyList(),
    val smoothElevation: Boolean = false,
    val style: TrackStyle = TrackStyle.CLASSIC,
    /** Bits: 1 = left barrier, 2 = right barrier. */
    val barriers: Int = 0,
    val startTangentDegrees: Float? = null,
    val endTangentDegrees: Float? = null,
    val startGrade: Float? = null,
    val endGrade: Float? = null,
    val startBankDegrees: Float? = null,
    val endBankDegrees: Float? = null,
    val startRightBankDegrees: Float? = null,
    val endRightBankDegrees: Float? = null
) {
    /** Zero-tangent Hermite ease (same shape as elevation's spline with no imposed slope):
     * a section's banking now eases in/out instead of ramping at a constant rate, so it no
     * longer kinks abruptly the moment it meets the next section's ramp. */
    private fun bankEase(t: Float): Float { val u = t.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }
    fun leftBankAt(t: Float): Float {
        val u = bankEase(t)
        return (startBankDegrees ?: bankDegrees) * (1f - u) + (endBankDegrees ?: bankDegrees) * u
    }
    fun rightBankAt(t: Float): Float {
        val u = bankEase(t)
        return (startRightBankDegrees ?: rightBankDegrees) * (1f - u) + (endRightBankDegrees ?: rightBankDegrees) * u
    }
    fun bankAt(edge: TrackEdge, t: Float) = if (edge == TrackEdge.RIGHT) rightBankAt(t) else leftBankAt(t)
    fun withBank(edge: TrackEdge, value: Float): ToyboxTrackSection = when (edge) {
        TrackEdge.LEFT -> copy(bankDegrees = value,
            startBankDegrees = startBankDegrees?.plus(value - bankDegrees),
            endBankDegrees = endBankDegrees?.plus(value - bankDegrees))
        TrackEdge.RIGHT -> copy(rightBankDegrees = value,
            startRightBankDegrees = startRightBankDegrees?.plus(value - rightBankDegrees),
            endRightBankDegrees = endRightBankDegrees?.plus(value - rightBankDegrees))
        TrackEdge.BOTH -> withBank(TrackEdge.LEFT, value).withBank(TrackEdge.RIGHT, value)
    }
    private val curved get() = bends.isNotEmpty() || smoothElevation || startTangentDegrees != null || endTangentDegrees != null
    private val elevationKnots by lazy {
        listOf(0f to y) + bends.map { it.t to (y + (endY - y) * it.t + it.height) } + (1f to endY)
    }

    /** Horizontal end tangents join neighbouring ribbons without a sudden change of slope.
     * Monotone interior tangents round peaks without overshooting the edited heights. */
    private fun elevationAt(t: Float): Float {
        val points = elevationKnots
        val i = (points.indexOfFirst { it.first >= t }.coerceAtLeast(1) - 1).coerceAtMost(points.lastIndex - 1)
        fun tangent(index: Int): Float {
            if (index == 0) return (startGrade ?: 0f) * length
            if (index == points.lastIndex) return (endGrade ?: 0f) * length
            val h0 = (points[index].first - points[index - 1].first).coerceAtLeast(0.0001f)
            val h1 = (points[index + 1].first - points[index].first).coerceAtLeast(0.0001f)
            val d0 = (points[index].second - points[index - 1].second) / h0
            val d1 = (points[index + 1].second - points[index].second) / h1
            if (d0 * d1 <= 0f) return 0f
            val w0 = 2f * h1 + h0
            val w1 = h1 + 2f * h0
            return (w0 + w1) / (w0 / d0 + w1 / d1)
        }
        val a = points[i]
        val b = points[i + 1]
        val span = (b.first - a.first).coerceAtLeast(0.0001f)
        val u = ((t - a.first) / span).coerceIn(0f, 1f)
        return (2*u*u*u - 3*u*u + 1)*a.second + (u*u*u - 2*u*u + u)*span*tangent(i) +
            (-2*u*u*u + 3*u*u)*b.second + (u*u*u - u*u)*span*tangent(i + 1)
    }
    private fun displacement(t: Float): TrackBend {
        val points = listOf(TrackBend(0f, 0f, 0f)) + bends + TrackBend(1f, 0f, 0f)
        val i = (points.indexOfFirst { it.t >= t }.coerceAtLeast(1) - 1).coerceAtMost(points.lastIndex - 1)
        val a = points[i]
        val b = points[i + 1]
        val before = points[(i - 1).coerceAtLeast(0)]
        val after = points[(i + 2).coerceAtMost(points.lastIndex)]
        val span = (b.t - a.t).coerceAtLeast(0.0001f)
        val u = ((t - a.t) / span).coerceIn(0f, 1f)
        fun interpolate(startDerivative: Float? = null, endDerivative: Float? = null, value: (TrackBend) -> Float): Float {
            val m0 = if (i == 0 && startDerivative != null) startDerivative else
                (value(b) - value(before)) / (b.t - before.t).coerceAtLeast(0.0001f)
            val m1 = if (i + 1 == points.lastIndex && endDerivative != null) endDerivative else
                (value(after) - value(a)) / (after.t - a.t).coerceAtLeast(0.0001f)
            return (2*u*u*u - 3*u*u + 1)*value(a) + (u*u*u - 2*u*u + u)*span*m0 +
                (-2*u*u*u + 3*u*u)*value(b) + (u*u*u - u*u)*span*m1
        }
        val start = startTangentDegrees?.times(kotlin.math.PI.toFloat()/180f)
        val end = endTangentDegrees?.times(kotlin.math.PI.toFloat()/180f)
        return TrackBend(t,
            interpolate(start?.let { length*sin(it) }, end?.let { length*sin(it) }) { it.side },
            interpolate { it.height },
            interpolate(start?.let { length*(cos(it)-1f) }, end?.let { length*(cos(it)-1f) }) { it.along })
    }

    fun centerAt(t: Float): VolumePoint {
        val offset = displacement(t)
        return VolumePoint(startX + forwardX * (length * t + offset.along) + rightX * offset.side,
            if (smoothElevation) elevationAt(t) else y + (endY - y) * t + offset.height,
            startZ + forwardZ * (length * t + offset.along) + rightZ * offset.side)
    }

    /** Recomputed on immutable copies: more editable sections on long or strongly bent ribbons.
     * Horizontal distance only: a steep elevation edit at a single point must not inflate how
     * many handles the whole ribbon gets, or a big vertical move degrades editing everywhere
     * else on the ribbon into a swarm of tightly-packed, barely-useful points. */
    val editFractions: List<Float> by lazy {
        val planarLength = (0..32).map { centerAt(it / 32f) }.zipWithNext().sumOf { (a,b) ->
            kotlin.math.hypot((b.x-a.x).toDouble(), (b.z-a.z).toDouble())
        }
        val count = kotlin.math.ceil(planarLength / 8.0).toInt().coerceIn(2, 32)
        (List(count + 1) { it.toFloat() / count } + bends.map { it.t }).sorted()
            .fold(mutableListOf<Float>()) { result, t ->
                if (result.isEmpty() || t - result.last() > 0.001f) result.add(t)
                result
            }
    }

    fun withBend(t: Float, worldX: Float, worldY: Float, worldZ: Float): ToyboxTrackSection {
        val bend = TrackBend(t, localSide(worldX, worldZ), worldY - (y + (endY - y) * t),
            localAlong(worldX, worldZ) + halfLength - length * t)
        return copy(bends = (bends.filter { kotlin.math.abs(it.t - t) > 0.001f } + bend).sortedBy { it.t },
            smoothElevation = true)
    }

    fun slice(from: Float, to: Float, newId: Long = id): ToyboxTrackSection {
        val a = centerAt(from)
        val b = centerAt(to)
        val yaw = kotlin.math.atan2(b.x-a.x,b.z-a.z)*180f/kotlin.math.PI.toFloat()
        var result = copy(id=newId, x=(a.x+b.x)*0.5f, z=(a.z+b.z)*0.5f, y=a.y, endY=b.y,
            yawDegrees=yaw, length=hypot(b.x-a.x,b.z-a.z).coerceAtLeast(0.05f),
            width=width+(endWidth-width)*from, endWidth=width+(endWidth-width)*to, bends=emptyList(),
            startYawOffset=yawDegrees+startYawOffset-yaw, endYawOffset=yawDegrees+endYawOffset-yaw)
        if (curved) {
            fun derivative(t: Float): VolumePoint {
                val p = centerAt((t-.0001f).coerceAtLeast(0f))
                val q = centerAt((t+.0001f).coerceAtMost(1f))
                return VolumePoint(q.x-p.x,q.y-p.y,q.z-p.z)
            }
            val da = derivative(from)
            val db = derivative(to)
            result = result.copy(
                startTangentDegrees=kotlin.math.atan2(da.x,da.z)*180f/kotlin.math.PI.toFloat()-yaw,
                endTangentDegrees=kotlin.math.atan2(db.x,db.z)*180f/kotlin.math.PI.toFloat()-yaw,
                startGrade=da.y/hypot(da.x,da.z).coerceAtLeast(.0001f),
                endGrade=db.y/hypot(db.x,db.z).coerceAtLeast(.0001f),
                startBankDegrees=leftBankAt(from),endBankDegrees=leftBankAt(to),
                startRightBankDegrees=rightBankAt(from),endRightBankDegrees=rightBankAt(to))
            // Carry over only the ribbon's own intentional bends, re-expressed on the new
            // chord: the boundary tangent/grade/bank above already reconstructs the same
            // curve shape between them. A previous version also baked 31 evenly-sampled
            // points into permanent bends "just in case", which silently multiplied a
            // section's point count on every split and made the result nearly impossible
            // to edit afterwards (each bend only reshapes its own tiny neighbourhood).
            for (bend in bends) {
                if (bend.t <= from || bend.t >= to) continue
                val p = centerAt(bend.t)
                result = result.withBend((bend.t-from)/(to-from), p.x, p.y, p.z)
            }
        }
        return result
    }

    /** Shared tessellation for rendering, picking and driving. Preserve the exact legacy quad when straight. */
    val meshSections: List<ToyboxTrackSection> by lazy {
        if (!curved) listOf(this) else {
            val samples = mutableListOf(0f)
            fun subdivide(a: Float, b: Float, depth: Int) {
                val p = centerAt(a)
                val q = centerAt(b)
                // Quarter samples detect S-shaped profiles whose midpoint lies on the chord.
                val error = listOf(0.25f, 0.5f, 0.75f).maxOf { fraction ->
                    val m = centerAt(a + (b-a)*fraction)
                    hypot(m.x - (p.x+(q.x-p.x)*fraction), m.z - (p.z+(q.z-p.z)*fraction)) +
                        kotlin.math.abs(m.y - (p.y+(q.y-p.y)*fraction))
                }
                val distance = kotlin.math.sqrt((q.x-p.x)*(q.x-p.x) + (q.y-p.y)*(q.y-p.y) + (q.z-p.z)*(q.z-p.z))
                if (depth < 10 && (error > 0.0025f || distance > 0.75f)) {
                    subdivide(a, (a+b)*0.5f, depth+1)
                    subdivide((a+b)*0.5f, b, depth+1)
                } else samples.add(b)
            }
            val knots = (listOf(0f) + bends.map { it.t } + 1f)
            knots.zipWithNext().forEach { (a,b) -> subdivide(a,b,0) }
            fun angle(t: Float): Float {
                if (t == 0f && startTangentDegrees != null) return yawDegrees + startTangentDegrees
                if (t == 1f && endTangentDegrees != null) return yawDegrees + endTangentDegrees
                val p = centerAt((t-0.001f).coerceAtLeast(0f))
                val q = centerAt((t+0.001f).coerceAtMost(1f))
                return kotlin.math.atan2(q.x-p.x,q.z-p.z)*180f/kotlin.math.PI.toFloat()
            }
            samples.zipWithNext().map { (a,b) ->
                val p = centerAt(a)
                val q = centerAt(b)
                val yaw = kotlin.math.atan2(q.x-p.x,q.z-p.z)*180f/kotlin.math.PI.toFloat()
                copy(x=(p.x+q.x)*0.5f, z=(p.z+q.z)*0.5f, y=p.y, endY=q.y,
                    length=hypot(q.x-p.x,q.z-p.z).coerceAtLeast(0.0001f), yawDegrees=yaw,
                    width=width+(endWidth-width)*a, endWidth=width+(endWidth-width)*b,
                    startYawOffset=angle(a)-yaw, endYawOffset=angle(b)-yaw, bends=emptyList(), smoothElevation=false,
                    startTangentDegrees=null, endTangentDegrees=null, startGrade=null, endGrade=null,
                    startBankDegrees=leftBankAt(a), endBankDegrees=leftBankAt(b),
                    startRightBankDegrees=rightBankAt(a), endRightBankDegrees=rightBankAt(b))
            }
        }
    }
    val yawRadians get() = yawDegrees * kotlin.math.PI.toFloat() / 180f
    val yawSin get() = sin(yawRadians)
    val yawCos get() = cos(yawRadians)
    val forwardX get() = yawSin
    val forwardZ get() = yawCos
    val rightX get() = yawCos
    val rightZ get() = -yawSin
    val halfLength get() = length * 0.5f
    val halfWidth get() = width * 0.5f
    val startX get() = x - forwardX * halfLength
    val startZ get() = z - forwardZ * halfLength
    val finishX get() = x + forwardX * halfLength
    val finishZ get() = z + forwardZ * halfLength
    val minX get() = minOf(corner(-1f, -1f).x, corner(-1f, 1f).x, corner(1f, -1f).x, corner(1f, 1f).x)
    val maxX get() = maxOf(corner(-1f, -1f).x, corner(-1f, 1f).x, corner(1f, -1f).x, corner(1f, 1f).x)
    val minZ get() = minOf(corner(-1f, -1f).z, corner(-1f, 1f).z, corner(1f, -1f).z, corner(1f, 1f).z)
    val maxZ get() = maxOf(corner(-1f, -1f).z, corner(-1f, 1f).z, corner(1f, -1f).z, corner(1f, 1f).z)

    fun moveTo(nx: Float, nz: Float) = copy(x = nx, z = nz)
    fun rotateYaw(deltaDegrees: Float) = copy(yawDegrees = ((yawDegrees + deltaDegrees) % 360f + 360f) % 360f)
    fun withStartY(value: Float) = copy(y = value, smoothElevation = smoothElevation || value != y)
    fun withEndY(value: Float) = copy(endY = value, smoothElevation = smoothElevation || value != endY)
    fun withEndpoint(finish: Boolean, px: Float, py: Float, pz: Float): ToyboxTrackSection {
        val ax = if (finish) startX else px
        val az = if (finish) startZ else pz
        val bx = if (finish) px else finishX
        val bz = if (finish) pz else finishZ
        val distance = hypot(bx - ax, bz - az)
        if (distance < 0.05f) return this
        val angle = kotlin.math.atan2(bx - ax, bz - az) * 180f / kotlin.math.PI.toFloat()
        return copy(x = (ax + bx) * 0.5f, z = (az + bz) * 0.5f,
            y = if (finish) y else py, endY = if (finish) py else endY,
            length = distance, yawDegrees = angle,
            startTangentDegrees = startTangentDegrees?.plus(yawDegrees-angle),
            endTangentDegrees = endTangentDegrees?.plus(yawDegrees-angle),
            smoothElevation = smoothElevation || py != (if (finish) endY else y),
            startYawOffset = startYawOffset + yawDegrees - angle,
            endYawOffset = endYawOffset + yawDegrees - angle)
    }
    fun resize(width: Float = this.width, length: Float = this.length) =
        copy(width = width.coerceAtLeast(0.05f), endWidth = endWidth * width / this.width,
            length = length.coerceAtLeast(0.05f),
            bends = bends.map { it.copy(along = it.along * length.coerceAtLeast(0.05f) / this.length) })

    fun localAlong(worldX: Float, worldZ: Float): Float {
        val dx = worldX - x
        val dz = worldZ - z
        return dx * forwardX + dz * forwardZ
    }

    fun localSide(worldX: Float, worldZ: Float): Float {
        val dx = worldX - x
        val dz = worldZ - z
        return dx * rightX + dz * rightZ
    }

    fun containsXZ(worldX: Float, worldZ: Float, margin: Float = 0f): Boolean =
        localAlong(worldX, worldZ) in (-halfLength - margin)..(halfLength + margin) &&
            localSide(worldX, worldZ) in (-halfWidth - margin)..(halfWidth + margin)

    fun surfaceYAt(worldX: Float, worldZ: Float): Float? {
        if (curved) return meshSections.mapNotNull { it.surfaceYAt(worldX, worldZ) }.maxOrNull()
        val a = corner(-1f, -1f)
        val b = corner(1f, -1f)
        val c = corner(1f, 1f)
        val d = corner(-1f, 1f)
        fun triangle(p: VolumePoint, q: VolumePoint, r: VolumePoint): Float? {
            val denominator = (q.z - r.z) * (p.x - r.x) + (r.x - q.x) * (p.z - r.z)
            if (kotlin.math.abs(denominator) < 0.000001f) return null
            val u = ((q.z - r.z) * (worldX - r.x) + (r.x - q.x) * (worldZ - r.z)) / denominator
            val v = ((r.z - p.z) * (worldX - r.x) + (p.x - r.x) * (worldZ - r.z)) / denominator
            if (u < -0.0001f || v < -0.0001f || u + v > 1.0001f) return null
            return u * p.y + v * q.y + (1f - u - v) * r.y
        }
        return triangle(a, b, c) ?: triangle(a, c, d)
    }

    /** sideSign -1/0/+1 = left edge / centreline / right edge. Each edge pivots the cross-section
     * rigidly around the centreline instead of shearing it, so the projected width foreshortens
     * with the bank like a real tilted plane; left and right can tilt independently (only one
     * edge lifted mid-turn instead of the whole width), meeting at the centreline point. */
    fun corner(alongSign: Float, sideSign: Float): VolumePoint {
        val along = alongSign * halfLength
        val side = sideSign * (if (alongSign < 0f) width else endWidth) * 0.5f
        val angle = (yawDegrees + if (alongSign < 0f) startYawOffset else endYawOffset) * kotlin.math.PI.toFloat() / 180f
        val t = (along + halfLength) / length.coerceAtLeast(0.0001f)
        val centreY = y + (endY - y) * t
        val bank = (if (sideSign < 0f) leftBankAt(t) else rightBankAt(t)) * kotlin.math.PI.toFloat() / 180f
        val tiltedSide = side * cos(bank)
        return VolumePoint(
            x = x + forwardX * along + cos(angle) * tiltedSide,
            y = centreY + side * sin(bank),
            z = z + forwardZ * along - sin(angle) * tiltedSide
        )
    }

    fun snappedTo(sections: List<ToyboxTrackSection>, snapDistance: Float): ToyboxTrackSection {
        var best: Pair<Float, ToyboxTrackSection>? = null
        for (other in sections) {
            if (other.id == id) continue
            for (ownEnd in listOf(
                floatArrayOf(startX, y, startZ, 0f),
                floatArrayOf(finishX, endY, finishZ, 1f)
            )) {
                val sx = ownEnd[0]
                val sy = ownEnd[1]
                val sz = ownEnd[2]
                val end = ownEnd[3]
                for (otherEnd in listOf(
                    floatArrayOf(other.startX, other.y, other.startZ),
                    floatArrayOf(other.finishX, other.endY, other.finishZ)
                )) {
                    val ox = otherEnd[0]
                    val oy = otherEnd[1]
                    val oz = otherEnd[2]
                    val d = hypot(sx - ox, sz - oz) + kotlin.math.abs(sy - oy) * 0.35f
                    if (d <= snapDistance && (best == null || d < best!!.first)) {
                        val dx = ox - sx
                        val dz = oz - sz
                        val moved = copy(
                            x = x + dx,
                            z = z + dz,
                            y = if (end < 0.5f) oy else y,
                            endY = if (end >= 0.5f) oy else endY
                        )
                        best = d to moved
                    }
                }
            }
        }
        return best?.second ?: this
    }

    fun toJson() = JSONObject()
        .put("id", id)
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("z", z.toDouble())
        .put("yawDegrees", yawDegrees.toDouble())
        .put("length", length.toDouble())
        .put("width", width.toDouble())
        .put("endY", endY.toDouble())
        .put("bankDegrees", bankDegrees.toDouble())
        .put("rightBankDegrees", rightBankDegrees.toDouble())
        .put("startYawOffset", startYawOffset.toDouble())
        .put("endYawOffset", endYawOffset.toDouble())
        .put("endWidth", endWidth.toDouble())
        .put("smoothElevation", smoothElevation)
        .put("style", style.name)
        .put("barriers", barriers)
        .put("startTangentDegrees", startTangentDegrees).put("endTangentDegrees", endTangentDegrees)
        .put("startGrade", startGrade).put("endGrade", endGrade)
        .put("startBankDegrees", startBankDegrees).put("endBankDegrees", endBankDegrees)
        .put("startRightBankDegrees", startRightBankDegrees).put("endRightBankDegrees", endRightBankDegrees)
        .put("bends", JSONArray().apply { bends.forEach { put(JSONObject()
            .put("t", it.t.toDouble()).put("side", it.side.toDouble()).put("height", it.height.toDouble())
            .put("along", it.along.toDouble())) } })
        .put("color", color)

    companion object {
        fun fromJson(json: JSONObject): ToyboxTrackSection {
            val bankDegrees = json.optDouble("bankDegrees", 0.0).toFloat()
            val startBankDegrees = json.optDouble("startBankDegrees", Double.NaN).toFloat().takeIf { it.isFinite() }
            val endBankDegrees = json.optDouble("endBankDegrees", Double.NaN).toFloat().takeIf { it.isFinite() }
            // Older saves only had one symmetric bank: an absent right-edge key falls back to the
            // left value so those tracks keep looking the same; an explicit null (new saves where
            // only the left edge has a start/end override) is respected as-is.
            return ToyboxTrackSection(
                id = json.optLong("id", System.nanoTime()),
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat(),
                z = json.optDouble("z", 0.0).toFloat(),
                yawDegrees = json.optDouble("yawDegrees", 0.0).toFloat(),
                length = json.optDouble("length", 24.0).toFloat().coerceAtLeast(0.05f),
                width = json.optDouble("width", 8.0).toFloat().coerceAtLeast(0.05f),
                startYawOffset = json.optDouble("startYawOffset", 0.0).toFloat(),
                endYawOffset = json.optDouble("endYawOffset", 0.0).toFloat(),
                endWidth = json.optDouble("endWidth", json.optDouble("width", 8.0)).toFloat().coerceAtLeast(0.05f),
                smoothElevation = json.optBoolean("smoothElevation", (json.optJSONArray("bends")?.length() ?: 0) > 0),
                style = TrackStyle.parse(json.optString("style")),
                barriers = json.optInt("barriers", 0) and 3,
                startTangentDegrees = json.optDouble("startTangentDegrees", Double.NaN).toFloat().takeIf { it.isFinite() },
                endTangentDegrees = json.optDouble("endTangentDegrees", Double.NaN).toFloat().takeIf { it.isFinite() },
                startGrade = json.optDouble("startGrade", Double.NaN).toFloat().takeIf { it.isFinite() },
                endGrade = json.optDouble("endGrade", Double.NaN).toFloat().takeIf { it.isFinite() },
                startBankDegrees = startBankDegrees,
                endBankDegrees = endBankDegrees,
                startRightBankDegrees = if (json.has("startRightBankDegrees"))
                    json.optDouble("startRightBankDegrees", Double.NaN).toFloat().takeIf { it.isFinite() }
                else startBankDegrees,
                endRightBankDegrees = if (json.has("endRightBankDegrees"))
                    json.optDouble("endRightBankDegrees", Double.NaN).toFloat().takeIf { it.isFinite() }
                else endBankDegrees,
                bends = json.optJSONArray("bends")?.let { array ->
                    List(array.length()) { array.getJSONObject(it) }.map {
                        TrackBend(it.optDouble("t").toFloat(), it.optDouble("side", 0.0).toFloat(),
                            it.optDouble("height", 0.0).toFloat(), it.optDouble("along", 0.0).toFloat())
                    }.filter { it.t.isFinite() && it.t > 0f && it.t < 1f && it.side.isFinite() && it.height.isFinite() && it.along.isFinite() }
                        .distinctBy { it.t }.sortedBy { it.t }
                } ?: emptyList(),
                endY = json.optDouble("endY", json.optDouble("y", 0.0)).toFloat(),
                bankDegrees = bankDegrees,
                rightBankDegrees = json.optDouble("rightBankDegrees", bankDegrees.toDouble()).toFloat(),
                color = json.optInt("color", 0xFF6F7B91.toInt())
            )
        }
    }
}

internal enum class ToyboxRotationAxis(val label: String) {
    YAW("Plan"),
    PITCH("Incl. av/ar"),
    ROLL("Incl. g/d");

    fun next() = when (this) {
        YAW -> PITCH
        PITCH -> ROLL
        ROLL -> YAW
    }
}

internal data class ToyboxDecor(
    val id: Long,
    val modelId: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val quarterTurns: Int = 0,
    val scale: Float = 1f,
    val yawDegrees: Float = quarterTurns * 90f,
    val solid: Boolean = true,
    val colors: Map<Int, Int> = emptyMap()
) {
    private val resolvedPlacement by lazy {
        runCatching {
            val original = DecorCatalog[modelId]
            val hasCollisionParts = original.parts.any { it.solid }
            val model = if (solid && hasCollisionParts && colors.isEmpty()) original else original.copy(parts = original.parts.map {
                it.copy(color = colors[it.color and 0xFFFFFF] ?: it.color,
                    solid = solid && (it.solid || !hasCollisionParts))
            })
            DecorPlacement(model, x, y, z, quarterTurns, scale, yawDegrees)
        }.getOrNull()
    }
    fun placement(): DecorPlacement? = resolvedPlacement

    fun moveTo(nx: Float, nz: Float) = copy(x = nx, z = nz)
    fun lift(dy: Float) = copy(y = (y + dy).coerceAtLeast(0f))
    fun resize(ds: Float) = copy(scale = (scale + ds).coerceAtLeast(0.05f))
    fun rotateQuarter(delta: Int = 1) = rotateYaw(delta * 90f)
    fun rotateYaw(deltaDegrees: Float) = withYaw(yawDegrees + deltaDegrees)
    fun withYaw(degrees: Float): ToyboxDecor {
        val normalized = ((degrees % 360f) + 360f) % 360f
        val snappedQuarter = ((normalized / 90f).toInt() % 4 + 4) % 4
        return copy(yawDegrees = normalized, quarterTurns = snappedQuarter)
    }

    fun toJson() = JSONObject()
        .put("id", id)
        .put("modelId", modelId)
        .put("solid", solid)
        .put("colors", colorsToJson(colors))
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("z", z.toDouble())
        .put("quarterTurns", quarterTurns)
        .put("scale", scale.toDouble())
        .put("yawDegrees", yawDegrees.toDouble())

    companion object {
        fun colorsToJson(colors: Map<Int, Int>) = JSONObject().apply {
            colors.forEach { (original, replacement) -> put(original.toString(), replacement and 0xFFFFFF) }
        }
        fun colorsFromJson(json: JSONObject?): Map<Int, Int> = buildMap {
            if (json != null) for (key in json.keys()) {
                val original = key.toIntOrNull() ?: continue
                put(original and 0xFFFFFF, json.optInt(key, original) and 0xFFFFFF)
            }
        }
        fun fromJson(json: JSONObject): ToyboxDecor {
            val quarterTurns = json.optInt("quarterTurns", 0)
            return ToyboxDecor(
                id = json.optLong("id", System.nanoTime()),
                modelId = json.optString("modelId"),
                solid = json.optBoolean("solid", true),
                colors = colorsFromJson(json.optJSONObject("colors")),
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat(),
                z = json.optDouble("z", 0.0).toFloat(),
                quarterTurns = quarterTurns,
                scale = json.optDouble("scale", 1.0).toFloat().coerceAtLeast(0.05f),
                yawDegrees = json.optDouble("yawDegrees", quarterTurns * 90.0).toFloat()
            )
        }
    }
}

internal data class ToyboxCheckpoint(
    val id: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val radius: Float = 8f
) {
    fun toJson() = JSONObject()
        .put("id", id)
        .put("x", x.toDouble())
        .put("y", y.toDouble())
        .put("z", z.toDouble())
        .put("radius", radius.toDouble())

    companion object {
        fun fromJson(json: JSONObject) = ToyboxCheckpoint(
            id = json.optLong("id", System.nanoTime()),
            x = json.optDouble("x", 0.0).toFloat(),
            y = json.optDouble("y", 0.0).toFloat(),
            z = json.optDouble("z", 0.0).toFloat(),
            radius = json.optDouble("radius", 8.0).toFloat().coerceAtLeast(1f)
        )
    }
}

internal data class ToyboxWorld(
    val version: Int = VERSION,
    val name: String = "Circuit libre",
    val volumes: List<ToyboxVolume> = starterVolumes(),
    val trackSections: List<ToyboxTrackSection> = starterTrackSections(),
    val checkpoints: List<ToyboxCheckpoint> = emptyList(),
    val decorations: List<ToyboxDecor> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", version)
        .put("name", name)
        .put("volumes", JSONArray().apply { volumes.forEach { put(it.toJson()) } })
        .put("trackSections", JSONArray().apply { trackSections.forEach { put(it.toJson()) } })
        .put("checkpoints", JSONArray().apply { checkpoints.forEach { put(it.toJson()) } })
        .put("decorations", JSONArray().apply { decorations.forEach { put(it.toJson()) } })

    companion object {
        const val VERSION = 2

        fun fromJson(json: JSONObject): ToyboxWorld {
            if (json.optInt("version", -1) != VERSION) return ToyboxWorld()
            val volumesJson = json.optJSONArray("volumes") ?: JSONArray()
            val trackJson = json.optJSONArray("trackSections") ?: JSONArray()
            val checkpointJson = json.optJSONArray("checkpoints") ?: JSONArray()
            val decorationsJson = json.optJSONArray("decorations") ?: JSONArray()
            return ToyboxWorld(
                version = json.optInt("version", VERSION),
                name = json.optString("name", "Circuit libre"),
                volumes = List(volumesJson.length()) { ToyboxVolume.fromJson(volumesJson.getJSONObject(it)) },
                trackSections = List(trackJson.length()) { ToyboxTrackSection.fromJson(trackJson.getJSONObject(it)) },
                checkpoints = List(checkpointJson.length()) { ToyboxCheckpoint.fromJson(checkpointJson.getJSONObject(it)) },
                decorations = List(decorationsJson.length()) { ToyboxDecor.fromJson(decorationsJson.getJSONObject(it)) }
            )
        }

        fun starterVolumes(): List<ToyboxVolume> = listOf(
            ToyboxVolume(1, ToyboxVolumeKind.FLOOR, 0f, -0.4f, 0f, 180f, 0.8f, 120f)
        )

        fun starterTrackSections(): List<ToyboxTrackSection> = listOf(
            ToyboxTrackSection(101, 0f, 0.05f, -32f, 90f, 58f, 9f),
            ToyboxTrackSection(102, 32f, 0.05f, 0f, 0f, 58f, 9f, endY = 6f),
            ToyboxTrackSection(103, 0f, 6f, 32f, 270f, 58f, 9f),
            ToyboxTrackSection(104, -32f, 6f, 0f, 180f, 58f, 9f, endY = 0.05f)
        )

        fun builtInWorlds(): List<ToyboxWorld> = listOf(
            ToyboxWorld(
                name = "Maison complete 3 etages",
                volumes = completeHouseVolumes(),
                trackSections = emptyList(),
                decorations = completeHouseDecorations()
            ),
            ToyboxWorld(name = "Maison tablette", volumes = starterVolumes(), trackSections = starterTrackSections())
        )

        private fun completeHouseVolumes(): List<ToyboxVolume> {
            var nextId = 10_000L
            fun RoomBox.toVolume(kind: ToyboxVolumeKind) = ToyboxVolume(
                id = nextId++,
                kind = kind,
                x = x,
                y = y,
                z = z,
                width = width,
                height = height,
                depth = depth,
                solid = kind.solidByDefault,
                color = 0xFF000000.toInt() or (color and 0x00FFFFFF)
            )
            val floors = HouseGeometry.floorBoxes().map { it.toVolume(ToyboxVolumeKind.FLOOR) }
            val walls = HouseGeometry.wallBoxes().map { box ->
                box.toVolume(if (box.height <= 5f) ToyboxVolumeKind.RAIL else ToyboxVolumeKind.WALL)
            }
            val furniture = HouseGeometry.furnitureBoxes().map { box ->
                box.toVolume(classifyHouseBox(box))
            }
            return floors + walls + furniture
        }

        private fun classifyHouseBox(box: RoomBox): ToyboxVolumeKind {
            val color = box.color and 0x00FFFFFF
            val thin = box.width <= 1.2f || box.depth <= 1.2f
            return when {
                box.height <= 1.2f -> ToyboxVolumeKind.FLOOR
                color == 0x9FD2E3 -> ToyboxVolumeKind.WINDOW
                color == 0xA8D1B7 && thin && box.height >= 12f -> ToyboxVolumeKind.DOOR
                color == 0x8DA7B3 || color == 0xE6EEF2 -> ToyboxVolumeKind.DUCT
                color == 0xC08A55 || color == 0xD6A46E || color == 0xA7B9C6 -> ToyboxVolumeKind.RAMP
                color == 0xF3E9D7 && box.height <= 1.2f && box.depth <= 4f -> ToyboxVolumeKind.STAIR
                color == 0xF7E7C8 || color == 0xC79A5A || color == 0xB57F4A -> ToyboxVolumeKind.RAIL
                !box.isSolidDecor() -> ToyboxVolumeKind.DECOR
                else -> ToyboxVolumeKind.FURNITURE
            }
        }

        private fun RoomBox.isSolidDecor(): Boolean =
            width >= 1f && height >= 0.5f && depth >= 1f

        private fun completeHouseDecorations(): List<ToyboxDecor> =
            HouseGeometry.furnitureDecorations().mapIndexed { index, placement ->
                ToyboxDecor(
                    id = 20_000L + index,
                    modelId = placement.model.id,
                    x = placement.x,
                    y = placement.y,
                    z = placement.z,
                    quarterTurns = placement.quarterTurns,
                    scale = placement.scale,
                    yawDegrees = placement.yawDegrees
                )
            }
    }
}
