package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import kotlin.math.abs

/** Rotation networks: who turns, how fast, in which direction, and whether the sources are strong enough.
 *
 * A speed is a signed rate of turn about the +axis of a part (X, Y or Z); [BASE] is the speed of a
 * water wheel. Rules, all local:
 * - an axle (shaft, cogwheel, crank) passes its rotation unchanged to the axle continuing it;
 * - two small cogwheels side by side turn at the same speed, opposite ways;
 * - a large cogwheel and a small one set diagonally trade speed ×2 / ÷2, opposite ways;
 * - a gearbox turns the rotation by 90° between the four faces around its axis;
 * - a machine takes the rotation of the axle or gearbox face that points at it, and passes nothing on.
 *
 * Each machine needs force in proportion to its speed; each driving source gives a fixed force.
 * A network whose machines need more than its sources give stops entirely, as does a network whose
 * sources disagree on the speed, or whose gears form a loop that cannot turn. Speeds are solved once per second, never per frame.
 */
internal class KineticNetwork(
    private val blockAt: (Int, Int, Int) -> Short,
    private val metaAt: (Int, Int, Int) -> Byte,
    /** Is this source turning right now (wheel in flowing water, crank recently turned)? */
    private val driving: (FrontierWorkshops.Pos, Short) -> Boolean,
) {
    private enum class Kind { SHAFT, COG, LARGE_COG, CRANK, GEARBOX, WHEEL, MACHINE }

    class Network(val stress: Float, val capacity: Float, val overloaded: Boolean, val conflict: Boolean) {
        val stopped get() = overloaded || conflict
    }
    class Result(val speed: Map<FrontierWorkshops.Pos, Float>, val network: Map<FrontierWorkshops.Pos, Network>) {
        fun speedAt(p: FrontierWorkshops.Pos) = speed[p] ?: 0f
    }

    private fun kind(id: Short) = when (id) {
        F.SHAFT -> Kind.SHAFT
        F.COGWHEEL -> Kind.COG
        F.LARGE_COGWHEEL -> Kind.LARGE_COG
        F.CRANK -> Kind.CRANK
        F.GEARBOX -> Kind.GEARBOX
        F.WATERWHEEL -> Kind.WHEEL
        F.MILL, F.PRESS, F.CRUSHER, F.LOOM -> Kind.MACHINE
        else -> null
    }
    private fun axial(k: Kind?) = k == Kind.SHAFT || k == Kind.COG || k == Kind.LARGE_COG || k == Kind.CRANK
    private fun axis(p: FrontierWorkshops.Pos) = PartialBlockModel.shaftAxis(metaAt(p.x, p.y, p.z))
    private fun step(p: FrontierWorkshops.Pos, axis: Int, sign: Int) = when (axis) {
        0 -> p.move(sign, 0, 0); 1 -> p.move(0, sign, 0); else -> p.move(0, 0, sign)
    }
    /** +1 for the first axis after the gearbox axis (X→Y→Z→X), -1 for the second. */
    private fun gearboxFace(gearboxAxis: Int, faceAxis: Int) = if (faceAxis == (gearboxAxis + 1) % 3) 1 else -1

    /** Every link out of [p]: the neighbour and the ratio (its value = ratio × value of p). */
    private fun links(p: FrontierWorkshops.Pos, out: MutableList<Pair<FrontierWorkshops.Pos, Float>>) {
        out.clear()
        val k = kind(blockAt(p.x, p.y, p.z)) ?: return
        when {
            axial(k) -> {
                val a = axis(p)
                for (sign in intArrayOf(1, -1)) {
                    val q = step(p, a, sign)
                    val kq = kind(blockAt(q.x, q.y, q.z)) ?: continue
                    when {
                        axial(kq) && axis(q) == a -> out += q to 1f
                        kq == Kind.GEARBOX && axis(q) != a -> out += q to (-sign * gearboxFace(axis(q), a)).toFloat()
                        // A wheel's value is its spin about the direction pointing at the axle.
                        kq == Kind.WHEEL -> out += q to -sign.toFloat()
                        kq == Kind.MACHINE -> out += q to 1f
                    }
                }
                val b = (a + 1) % 3; val c = (a + 2) % 3
                if (k == Kind.COG) for (side in intArrayOf(b, c)) for (sign in intArrayOf(1, -1)) {
                    val q = step(p, side, sign)
                    if (kind(blockAt(q.x, q.y, q.z)) == Kind.COG && axis(q) == a) out += q to -1f
                }
                if (k == Kind.COG || k == Kind.LARGE_COG) for (sb in intArrayOf(1, -1)) for (sc in intArrayOf(1, -1)) {
                    val q = step(step(p, b, sb), c, sc)
                    val kq = kind(blockAt(q.x, q.y, q.z))
                    if (axis(q) != a) continue
                    if (k == Kind.COG && kq == Kind.LARGE_COG) out += q to -.5f
                    if (k == Kind.LARGE_COG && kq == Kind.COG) out += q to -2f
                }
            }
            k == Kind.GEARBOX -> {
                val g = axis(p)
                for (a in intArrayOf((g + 1) % 3, (g + 2) % 3)) for (sign in intArrayOf(1, -1)) {
                    val q = step(p, a, sign)
                    val kq = kind(blockAt(q.x, q.y, q.z)) ?: continue
                    if (axial(kq) && axis(q) == a) out += q to (sign * gearboxFace(g, a)).toFloat()
                    if (kq == Kind.MACHINE) out += q to 1f
                }
            }
            k == Kind.WHEEL -> for (a in 0..2) for (sign in intArrayOf(1, -1)) {
                val q = step(p, a, sign)
                if (axial(kind(blockAt(q.x, q.y, q.z))) && axis(q) == a) out += q to sign.toFloat()
            }
            // Machines are ends: they take rotation and pass none on.
        }
    }

    /** Solves every network reached from [sources]. Parts not reached do not turn. */
    fun solve(sources: Collection<FrontierWorkshops.Pos>, limit: Int = 4096): Result {
        val speed = HashMap<FrontierWorkshops.Pos, Float>()
        val networks = HashMap<FrontierWorkshops.Pos, Network>()
        val rel = HashMap<FrontierWorkshops.Pos, Double>()
        val buffer = ArrayList<Pair<FrontierWorkshops.Pos, Float>>()
        for (seed in sources) {
            if (seed in rel || kind(blockAt(seed.x, seed.y, seed.z)) == null) continue
            val members = ArrayList<FrontierWorkshops.Pos>()
            var conflict = false
            rel[seed] = 1.0; members += seed
            var i = 0
            while (i < members.size && members.size < limit) {
                val p = members[i++]
                links(p, buffer)
                for ((q, ratio) in buffer) {
                    val machine = kind(blockAt(q.x, q.y, q.z)) == Kind.MACHINE
                    // A machine only uses the size of the speed: two sides may turn it either way.
                    val value = rel.getValue(p) * ratio
                    val known = rel[q]
                    if (known == null) { rel[q] = if (machine) abs(value) else value; members += q }
                    else if (abs((if (machine) abs(value) else value) - known) > 1e-6 * maxOf(1.0, abs(known))) conflict = true
                }
            }
            var scale: Double? = null
            var capacity = 0f
            for (m in members) {
                val id = blockAt(m.x, m.y, m.z)
                val k = kind(id)
                if ((k != Kind.WHEEL && k != Kind.CRANK) || !driving(m, id)) continue
                capacity += capacity(k)
                // Neither the cube wheel nor the crank has a direction of its own yet: a source follows the
                // direction of the network, only its speed must agree with the others.
                val wanted = BASE / rel.getValue(m)
                if (scale == null) scale = wanted
                else if (abs(abs(scale) - abs(wanted)) > 1e-6 * abs(wanted)) conflict = true
            }
            val s = scale ?: 0.0
            val tooFast = members.any { abs(s * rel.getValue(it)) > MAX_SPEED + 1e-6 }
            var stress = 0f
            for (m in members) {
                val k = kind(blockAt(m.x, m.y, m.z))
                if (k == Kind.MACHINE) stress += impact(blockAt(m.x, m.y, m.z)) * (abs(s * rel.getValue(m)) / BASE).toFloat()
            }
            val network = Network(stress, capacity, overloaded = stress > capacity + 1e-4f, conflict = conflict || tooFast)
            for (m in members) {
                networks[m] = network
                speed[m] = if (network.stopped) 0f else (s * rel.getValue(m)).toFloat()
            }
        }
        return Result(speed, networks)
    }

    private fun capacity(k: Kind) = if (k == Kind.CRANK) CRANK_FORCE else WHEEL_FORCE

    companion object {
        /** Speed of a water wheel; recipes take their listed time at this speed. */
        const val BASE = 16f
        const val MAX_SPEED = 256f
        const val WHEEL_FORCE = 8f
        const val CRANK_FORCE = 4f
        /** Force a machine needs at [BASE] speed; twice as much at twice the speed. */
        fun impact(machine: Short) = when (machine) { F.PRESS, F.CRUSHER -> 4f; else -> 2f }
        /** Recipe time multiplier from the speed of the machine. */
        fun durationFactor(speed: Float) = (BASE / abs(speed)).coerceIn(.25f, 4f)
    }
}
