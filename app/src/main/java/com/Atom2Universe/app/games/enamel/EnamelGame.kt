package com.Atom2Universe.app.games.enamel

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Physique locale, vue du dessus : aucune gravité et aucun changement au moteur partagé. */
object EnamelRules {
    const val WIDTH = 10f
    const val HEIGHT = 16f
    const val MIN_HEIGHT = 10f
    const val MAX_HEIGHT = 24f
    const val BASE_DEPTH = 3f
    const val RETURN_LIMIT = 1.5f
    const val LAUNCH_GRACE = 1.2f
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
    var entered = true
    var age = EnamelRules.LAUNCH_GRACE
    var overflow = 0f
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
    var height = EnamelRules.HEIGHT; private set
    val baseTop get() = height - EnamelRules.BASE_DEPTH
    val launchY get() = baseTop + EnamelRules.BASE_DEPTH / 2
    var danger = 0f; private set
    private var quiet = 0f
    private var elapsed = 0f
    val canLaunch get() = !moving && !over

    init { reset() }

    fun reset() {
        pucks.clear(); events.clear()
        score = 0; shots = 0; bestTier = -1; chain = 0
        moving = false; over = false; quiet = 0f; elapsed = 0f; danger = 0f
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

    /** Le plateau suit l'écran, en conservant les palets du même côté de la limite. */
    fun resize(newHeight: Float) {
        if (!newHeight.isFinite()) return
        val target = newHeight.coerceIn(EnamelRules.MIN_HEIGHT, EnamelRules.MAX_HEIGHT)
        val oldBase = baseTop
        height = target
        for (p in pucks) {
            val safe = oldBase - p.radius
            p.y = if (p.y <= safe) {
                p.radius + (p.y - p.radius) / (safe - p.radius) * (baseTop - 2 * p.radius)
            } else p.y + baseTop - oldBase
            p.y = p.y.coerceIn(p.radius, height - p.radius)
        }
    }

    fun isLaunchClear(x: Float, y: Float): Boolean {
        val r = EnamelRules.RADII[current]
        if (!x.isFinite() || !y.isFinite() || x < r || x > EnamelRules.WIDTH - r ||
            y < baseTop + r || y > height - r) return false
        return pucks.none {
            hypot(it.x - x, it.y - y) < it.radius + r + .025f
        }
    }

    /** Même un tir doux peut sortir de la base ; les tirs presque horizontaux sont refusés. */
    fun shotSpeed(y: Float, dx: Float, dy: Float, power: Float): Float? {
        if (!y.isFinite() || !dx.isFinite() || !dy.isFinite() || !power.isFinite()) return null
        val length = hypot(dx, dy)
        if (!length.isFinite() || length < .001f || dy / length > -.15f || power < .025f) return null
        val r = EnamelRules.RADII[current]
        // La composante verticale suffit même si une bande latérale absorbe de la vitesse.
        val minimum = sqrt(2 * EnamelRules.DRAG * max(.1f, y - baseTop + r + .16f)) / (-dy / length)
        if (minimum > EnamelRules.MAX_SPEED) return null
        return minimum + power.coerceIn(0f, 1f) * (EnamelRules.MAX_SPEED - minimum)
    }

    /** dx/dy : vecteur de visée vers le haut de la planche ; puissance dans [0, 1]. */
    fun launch(x: Float, y: Float, dx: Float, dy: Float, power: Float): Boolean {
        if (!canLaunch || !isLaunchClear(x, y)) return false
        val speed = shotSpeed(y, dx, dy, power) ?: return false
        val length = hypot(dx, dy)
        pucks.add(EnamelPuck(current, x, y, dx / length * speed, dy / length * speed).apply {
            entered = false; age = 0f
        })
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
                wall(p, returnToBoard = true)
            }
            mergeContacts()
            // Corrections de position sans ajout de vitesse : les piles serrées restent calmes.
            repeat(6) {
                for (i in pucks.indices) for (j in i + 1 until pucks.size) collide(pucks[i], pucks[j])
                pucks.forEach { wall(it) }
            }
            checkReturn(h)
            if (over) return
        }
        elapsed += frame
        if (pucks.all { hypot(it.vx, it.vy) < .035f }) quiet += frame else quiet = 0f
        if ((quiet >= .18f || elapsed > 10f) && danger == 0f && pucks.none { it.y + it.radius > baseTop + .06f }) {
            pucks.forEach { it.vx = 0f; it.vy = 0f }
            moving = false
        }
    }

    private fun checkReturn(dt: Float) {
        var worst = 0f
        for (p in pucks) {
            p.age += dt
            if (p.y + p.radius <= baseTop + .025f) p.entered = true
            if ((p.entered || p.age >= EnamelRules.LAUNCH_GRACE) && p.y + p.radius > baseTop + .06f) {
                p.overflow += dt
            } else p.overflow = max(0f, p.overflow - 2 * dt)
            worst = max(worst, p.overflow)
        }
        danger = (worst / EnamelRules.RETURN_LIMIT).coerceIn(0f, 1f)
        if (worst >= EnamelRules.RETURN_LIMIT) {
            over = true; moving = false
            pucks.forEach { it.vx = 0f; it.vy = 0f }
        }
    }

    private fun wall(p: EnamelPuck, returnToBoard: Boolean = false) {
        val r = p.radius
        if (p.x < r) { p.x = r; if (p.vx < 0) p.vx *= -.72f }
        if (p.x > EnamelRules.WIDTH - r) { p.x = EnamelRules.WIDTH - r; if (p.vx > 0) p.vx *= -.72f }
        if (p.y < r) { p.y = r; if (p.vy < 0) p.vy *= -.72f }
        // Bande à sens unique : le tir sort librement, puis les retours rebondissent.
        // Seulement avant les contacts : leur correction reste libre de dépasser la limite,
        // ce qui permet de mesurer une vraie pression du tas, sans punir un rebond isolé.
        if (returnToBoard && p.entered && p.y > baseTop - r) {
            p.y = baseTop - r
            if (p.vy > 0) p.vy *= -.72f
        }
        if (p.y > height - r) { p.y = height - r; if (p.vy > 0) p.vy = 0f }
    }

    private fun collide(a: EnamelPuck, b: EnamelPuck) {
        val dx = b.x - a.x; val dy = b.y - a.y
        val reach = a.radius + b.radius
        val distance = hypot(dx, dy)
        if (distance >= reach) return
        // Un tir contré dès l'entrée bénéficie déjà de la bande de renvoi.
        if (a.entered || b.entered) { a.entered = true; b.entered = true }
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
                    p.entered = a.entered || b.entered
                    p.age = max(a.age, b.age); p.overflow = max(a.overflow, b.overflow)
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
        return listOf("2", score, shots, bestTier, current, next, chain, if (moving) 1 else 0,
            pucks.joinToString(";") { "${it.tier},${it.x},${it.y},${it.vx},${it.vy},${it.angle},${if (it.entered) 1 else 0},${it.age},${it.overflow}" }, height).joinToString("|")
    }

    /** Validation entière avant mutation : une sauvegarde tronquée ne laisse pas un demi-plateau. */
    fun restoreState(state: String): Boolean = try {
        val s = state.split('|')
        val legacy = s.size == 9 && s[0] == "1"
        require(legacy || (s.size == 10 && s[0] == "2"))
        val savedHeight = if (legacy) EnamelRules.HEIGHT else s[9].toFloat()
        require(savedHeight.isFinite() && savedHeight in EnamelRules.MIN_HEIGHT..EnamelRules.MAX_HEIGHT)
        val numbers = (1..7).map { s[it].toInt() }
        require(numbers[0] >= 0 && numbers[1] > 0 && numbers[2] in -1..EnamelRules.LAST_TIER)
        require(numbers[3] in EnamelRules.WEIGHTS.indices && numbers[4] in EnamelRules.WEIGHTS.indices && numbers[5] >= 0)
        require(s[7] == "0" || s[7] == "1")
        val restored = if (s[8].isEmpty()) emptyList() else s[8].split(';').map { row ->
            val f = row.split(',')
            require(f.size == if (legacy) 6 else 9)
            val tier = f[0].toInt(); require(tier in EnamelRules.RADII.indices)
            val v = (1..5).map { f[it].toFloat().also { value -> require(value.isFinite()) } }
            val r = EnamelRules.RADII[tier]
            require(v[0] in r - .01f..EnamelRules.WIDTH - r + .01f && v[1] in r - .01f..savedHeight - r + .01f)
            require(hypot(v[2], v[3]) <= 100f)
            EnamelPuck(tier, v[0], v[1], v[2], v[3]).apply {
                angle = v[4]
                if (legacy) {
                    // Reprend les anciennes planches sans condamner les palets posés dans l'ancienne base.
                    y = min(y, savedHeight - EnamelRules.BASE_DEPTH - r - .06f)
                } else {
                    require(f[6] == "0" || f[6] == "1")
                    entered = f[6] == "1"; age = f[7].toFloat(); overflow = f[8].toFloat()
                    require(age.isFinite() && age >= 0f && overflow.isFinite() && overflow in 0f..EnamelRules.RETURN_LIMIT)
                }
            }
        }
        require(restored.size <= 256)
        reset(); height = savedHeight; pucks.addAll(restored)
        score = numbers[0]; shots = numbers[1]; bestTier = numbers[2]; current = numbers[3]; next = numbers[4]; chain = numbers[5]
        danger = (pucks.maxOfOrNull { it.overflow } ?: 0f) / EnamelRules.RETURN_LIMIT
        // Recalcule contacts et dépassement, même pour un plateau sauvegardé au repos.
        moving = true
        true
    } catch (_: RuntimeException) { false }
}
