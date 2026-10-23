package com.Atom2Universe.app.games.enamel

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Physique locale, vue du dessus : aucune gravité et aucun changement au moteur partagé. */
object EnamelRules {
    const val WIDTH = 10f
    const val HEIGHT = 16f
    const val LAUNCH_Y = 14f
    const val MAX_SPEED = 19f
    const val DRAG = 4.2f
    const val LAST_TIER = 11
    val RADII = floatArrayOf(.34f, .44f, .55f, .68f, .83f, 1f, 1.18f, 1.38f, 1.57f, 1.78f, 2f, 1.25f)
    val WEIGHTS = intArrayOf(35, 28, 20, 12, 5)
    fun points(tier: Int) = (tier + 1) * (tier + 2) / 2
}

class EnamelPuck(val tier: Int, var x: Float, var y: Float, var vx: Float = 0f, var vy: Float = 0f) {
    val radius get() = EnamelRules.RADII[tier]
    val mass get() = radius * radius
    var angle = 0f
}

data class EnamelEvent(val x: Float, val y: Float, val tier: Int, val points: Int, val chain: Int, val finale: Boolean)

class EnamelGame(private val random: Random = Random.Default) {
    val pucks = ArrayList<EnamelPuck>()
    val events = ArrayList<EnamelEvent>()
    var score = 0; private set
    var shots = 0; private set
    var bestTier = -1; private set
    var current = 0; private set
    var next = 0; private set
    var chain = 0; private set
    var moving = false; private set
    var over = false; private set
    private var quiet = 0f
    private var elapsed = 0f
    val canLaunch get() = !moving && !over

    init { reset() }

    fun reset() {
        pucks.clear(); events.clear()
        score = 0; shots = 0; bestTier = -1; chain = 0
        moving = false; over = false; quiet = 0f; elapsed = 0f
        current = roll(); next = roll()
    }

    private fun roll(): Int {
        var pick = random.nextInt(EnamelRules.WEIGHTS.sum())
        for (i in EnamelRules.WEIGHTS.indices) {
            pick -= EnamelRules.WEIGHTS[i]
            if (pick < 0) return i
        }
        return 0
    }

    fun isLaunchClear(x: Float): Boolean {
        val r = EnamelRules.RADII[current]
        if (!x.isFinite() || x < r || x > EnamelRules.WIDTH - r) return false
        return pucks.none {
            hypot(it.x - x, it.y - EnamelRules.LAUNCH_Y) < it.radius + r + .025f
        }
    }

    /** Intervalles interdits sur la ligne, plutôt qu'un échantillonnage qui peut rater un trou. */
    fun availableLaunchX(preferred: Float): Float? {
        val r = EnamelRules.RADII[current]
        val blocked = pucks.mapNotNull {
            val reach = it.radius + r + .03f
            val dy = it.y - EnamelRules.LAUNCH_Y
            if (kotlin.math.abs(dy) >= reach) null else {
                val dx = kotlin.math.sqrt(reach * reach - dy * dy)
                (it.x - dx) to (it.x + dx)
            }
        }.sortedBy { it.first }
        val candidates = ArrayList<Float>()
        var left = r
        for ((a, b) in blocked) {
            if (a > left) candidates.add(preferred.coerceIn(left, min(a, EnamelRules.WIDTH - r)))
            left = max(left, b)
            if (left > EnamelRules.WIDTH - r) break
        }
        if (left <= EnamelRules.WIDTH - r) candidates.add(preferred.coerceIn(left, EnamelRules.WIDTH - r))
        return candidates.filter { isLaunchClear(it) }.minByOrNull { kotlin.math.abs(it - preferred) }
    }

    /** dx/dy : vecteur de visée vers le haut de la planche ; puissance dans [0, 1]. */
    fun launch(x: Float, dx: Float, dy: Float, power: Float): Boolean {
        if (!canLaunch || !isLaunchClear(x) || !dx.isFinite() || !dy.isFinite() || !power.isFinite()) return false
        val length = hypot(dx, dy)
        if (length < .001f || dy >= -.05f || power < .025f) return false
        val speed = 1.5f + power.coerceIn(0f, 1f) * (EnamelRules.MAX_SPEED - 1.5f)
        pucks.add(EnamelPuck(current, x, EnamelRules.LAUNCH_Y, dx / length * speed, dy / length * speed))
        current = next; next = roll(); shots++; chain = 0
        quiet = 0f; elapsed = 0f; moving = true
        return true
    }

    fun step(dt: Float) {
        if (!moving || over || !dt.isFinite() || dt <= 0f) return
        val frame = dt.coerceAtMost(.05f)
        // Le déplacement maximal reste inférieur au quart du diamètre du plus petit palet.
        val fastest = pucks.maxOfOrNull { hypot(it.vx, it.vy) } ?: 0f
        val count = max(ceil(frame * 120).toInt(), ceil(fastest * frame / .14f).toInt()).coerceAtMost(32)
        val h = frame / count
        repeat(count) {
            for (p in pucks) {
                val speed = hypot(p.vx, p.vy)
                val factor = if (speed > 0f) max(0f, speed - EnamelRules.DRAG * h) / speed else 0f
                p.vx *= factor; p.vy *= factor
                p.x += p.vx * h; p.y += p.vy * h
                p.angle += (p.vx - p.vy) * h * .08f
                wall(p)
            }
            mergeContacts()
            // Corrections de position sans ajout de vitesse : les piles serrées restent calmes.
            repeat(6) {
                for (i in pucks.indices) for (j in i + 1 until pucks.size) collide(pucks[i], pucks[j])
                pucks.forEach { wall(it) }
            }
        }
        elapsed += frame
        if (pucks.all { hypot(it.vx, it.vy) < .035f }) quiet += frame else quiet = 0f
        if (quiet >= .18f || elapsed > 10f) {
            pucks.forEach { it.vx = 0f; it.vy = 0f }
            moving = false
            over = availableLaunchX(EnamelRules.WIDTH / 2) == null
        }
    }

    private fun wall(p: EnamelPuck) {
        val r = p.radius
        if (p.x < r) { p.x = r; if (p.vx < 0) p.vx *= -.72f }
        if (p.x > EnamelRules.WIDTH - r) { p.x = EnamelRules.WIDTH - r; if (p.vx > 0) p.vx *= -.72f }
        if (p.y < r) { p.y = r; if (p.vy < 0) p.vy *= -.72f }
        if (p.y > EnamelRules.HEIGHT - r) { p.y = EnamelRules.HEIGHT - r; if (p.vy > 0) p.vy *= -.72f }
    }

    private fun collide(a: EnamelPuck, b: EnamelPuck) {
        val dx = b.x - a.x; val dy = b.y - a.y
        val reach = a.radius + b.radius
        val distance = hypot(dx, dy)
        if (distance >= reach) return
        val nx = if (distance > .00001f) dx / distance else 1f
        val ny = if (distance > .00001f) dy / distance else 0f
        val invA = 1f / a.mass; val invB = 1f / b.mass
        val correction = max(0f, reach - distance - .0005f) / (invA + invB)
        a.x -= nx * correction * invA; a.y -= ny * correction * invA
        b.x += nx * correction * invB; b.y += ny * correction * invB
        val closing = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
        if (closing >= 0f) return
        val impulse = -(1f + .82f) * closing / (invA + invB)
        a.vx -= impulse * nx * invA; a.vy -= impulse * ny * invA
        b.vx += impulse * nx * invB; b.vy += impulse * ny * invB
    }

    private fun mergeContacts() {
        // Une nouvelle pièce attend le sous-pas suivant avant de fusionner à son tour.
        val used = HashSet<EnamelPuck>()
        val born = ArrayList<EnamelPuck>()
        for (i in pucks.indices) {
            val a = pucks[i]
            if (a in used) continue
            for (j in i + 1 until pucks.size) {
                val b = pucks[j]
                if (b in used || a.tier != b.tier || hypot(b.x - a.x, b.y - a.y) > a.radius + b.radius + .008f) continue
                used.add(a); used.add(b)
                val x = (a.x + b.x) / 2; val y = (a.y + b.y) / 2
                val finale = a.tier == EnamelRules.LAST_TIER
                val tier = if (finale) a.tier else a.tier + 1
                chain++
                val points = (if (finale) 250 else EnamelRules.points(tier)) * chain.coerceAtMost(8)
                score += points; bestTier = max(bestTier, tier)
                if (!finale) {
                    // Moyenne pondérée, dissipative même quand le dernier rang devient plus petit.
                    val p = EnamelPuck(tier, x, y)
                    val mass = max(p.mass, a.mass + b.mass)
                    p.vx = (a.mass * a.vx + b.mass * b.vx) / mass
                    p.vy = (a.mass * a.vy + b.mass * b.vy) / mass
                    wall(p); born.add(p)
                }
                events.add(EnamelEvent(x, y, tier, points, chain, finale))
                break
            }
        }
        pucks.removeAll(used); pucks.addAll(born)
    }

    fun saveState(): String? {
        if (over || shots == 0) return null
        return listOf("1", score, shots, bestTier, current, next, chain, if (moving) 1 else 0,
            pucks.joinToString(";") { "${it.tier},${it.x},${it.y},${it.vx},${it.vy},${it.angle}" }).joinToString("|")
    }

    /** Validation entière avant mutation : une sauvegarde tronquée ne laisse pas un demi-plateau. */
    fun restoreState(state: String): Boolean = try {
        val s = state.split('|')
        require(s.size == 9 && s[0] == "1")
        val numbers = (1..7).map { s[it].toInt() }
        require(numbers[0] >= 0 && numbers[1] > 0 && numbers[2] in -1..EnamelRules.LAST_TIER)
        require(numbers[3] in EnamelRules.WEIGHTS.indices && numbers[4] in EnamelRules.WEIGHTS.indices && numbers[5] >= 0)
        require(s[7] == "0" || s[7] == "1")
        val restored = if (s[8].isEmpty()) emptyList() else s[8].split(';').map { row ->
            val f = row.split(',')
            require(f.size == 6)
            val tier = f[0].toInt(); require(tier in EnamelRules.RADII.indices)
            val v = (1..5).map { f[it].toFloat().also { value -> require(value.isFinite()) } }
            val r = EnamelRules.RADII[tier]
            require(v[0] in r - .01f..EnamelRules.WIDTH - r + .01f && v[1] in r - .01f..EnamelRules.HEIGHT - r + .01f)
            require(hypot(v[2], v[3]) <= 100f)
            EnamelPuck(tier, v[0], v[1], v[2], v[3]).apply { angle = v[4] }
        }
        require(restored.size <= 256)
        reset(); pucks.addAll(restored)
        score = numbers[0]; shots = numbers[1]; bestTier = numbers[2]; current = numbers[3]; next = numbers[4]; chain = numbers[5]
        // Un sous-pas même pour un plateau au repos, afin de recalculer la ligne bloquée.
        moving = true
        true
    } catch (_: RuntimeException) { false }
}
