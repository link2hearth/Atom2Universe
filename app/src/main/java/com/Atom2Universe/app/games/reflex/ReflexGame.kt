package com.Atom2Universe.app.games.reflex

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.random.Random

enum class ReflexPhase { MENU, PLAYING, PAUSED, GAME_OVER }

/**
 * Les cinq sortes de particules. Elles arrivent une à une au fil de la partie
 * ([unlockAt], en secondes de jeu), pour que chacune soit découverte seule.
 */
enum class ParticleKind(val color: Int, val unlockAt: Float) {
    /** Proton : un appui quand l'anneau touche le cœur. */
    PROTON(0xFF29E0FF.toInt(), 0f),
    /** Antimatière : ne jamais la toucher, elle s'éteint toute seule. */
    ANTI(0xFFFF2D6F.toInt(), 12f),
    /** Électron : comme un proton, mais il se déplace. */
    ELECTRON(0xFF7CFF6B.toInt(), 30f),
    /** Noyau lourd : un premier appui le fend, le second se joue sur un anneau rapide. */
    NUCLEUS(0xFFFFC247.toInt(), 50f),
    /** Chaîne : trois particules numérotées, à toucher dans l'ordre. */
    CHAIN(0xFFB388FF.toInt(), 70f)
}

enum class HitGrade(val basePoints: Int, val heatGain: Float) {
    PERFECT(300, 0.06f),
    GOOD(100, 0.03f),
    /** Trop tôt : quelques points, mais le combo retombe à zéro. */
    EARLY(50, 0f)
}

/** Une particule à l'écran. Les positions sont en pixels, les temps en secondes de jeu. */
class Particle(
    val kind: ParticleKind,
    var x: Float,
    var y: Float,
    val radius: Float,
    /** Moment où l'anneau d'approche démarre. */
    var ringStart: Float,
    /** Moment où l'anneau touche le cœur : le « bon moment ». */
    var hitAt: Float,
    val chainId: Int = -1,
    val chainIndex: Int = 0
) {
    var vx = 0f
    var vy = 0f
    /** Le noyau lourd demande deux appuis ; les autres un seul. */
    var hitsLeft = if (kind == ParticleKind.NUCLEUS) 2 else 1
    var alive = true

    /** Avancement de l'anneau, de 0 (grand) à 1 (posé sur le cœur). */
    fun ringProgress(now: Float) =
        ((now - ringStart) / (hitAt - ringStart)).coerceIn(0f, 1f)
}

/**
 * Tout ce que la vue et le son ont besoin d'apprendre d'une partie. Le jeu ne
 * dessine rien et ne joue rien : il raconte, et chacun réagit.
 */
interface ReflexListener {
    fun onHit(p: Particle, grade: HitGrade, points: Int) {}
    fun onCrack(p: Particle) {}
    fun onMiss(p: Particle) {}
    fun onAntiTouched(p: Particle) {}
    fun onAntiFaded(p: Particle) {}
    fun onEmptyTap(x: Float, y: Float) {}
    fun onWrongOrder(p: Particle) {}
    fun onComboBroken(combo: Int) {}
    fun onLifeRegained() {}
    fun onOverheatStart() {}
    fun onOverheatEnd() {}
    fun onUnlock(kind: ParticleKind) {}
    fun onGameOver() {}
}

/**
 * Règles du « Collisionneur », sans rien d'Android : la partie ne connaît que des
 * pixels, des secondes et des appuis. Un seul mode, dont la difficulté monte
 * toute seule avec le temps de jeu.
 *
 * Tout est cadencé en secondes ([update] reçoit un dt quelconque) : le jeu se
 * comporte pareil à 60 et à 120 images par seconde.
 */
class ReflexGame(seed: Long = System.nanoTime()) {

    companion object {
        const val START_LIVES = 3
        const val MAX_LIVES = 3

        /** Écart au « bon moment » toléré pour PARFAIT et BIEN, en secondes. */
        const val PERFECT_WINDOW = 0.075f
        const val GOOD_WINDOW = 0.19f

        /** Une vie rendue tous les [LIFE_COMBO_STEP] combos. */
        const val LIFE_COMBO_STEP = 50
        /** Le combo multiplie les points : ×1, puis +1 tous les 10, jusqu'à ×4. */
        const val COMBO_PER_MULT = 10
        const val MAX_MULT = 4

        const val OVERHEAT_SECONDS = 8f
        const val OVERHEAT_MULT = 2

        /** La difficulté tend vers son maximum avec cette constante de temps. */
        const val DIFFICULTY_TAU = 75f

        /** Zone de toucher un peu plus large que le cœur dessiné. */
        const val TOUCH_SLOP = 1.35f

        /** Anneau rapide du noyau lourd, après le premier appui. */
        const val CRACK_RING = 0.5f

        const val CHAIN_LENGTH = 3
        const val CHAIN_STEP = 0.42f

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    }

    var listener: ReflexListener? = null
    private var rng = Random(seed)

    var phase = ReflexPhase.MENU

    // ── Terrain ──────────────────────────────────────────────────────────────
    private var fieldLeft = 0f
    private var fieldTop = 0f
    private var fieldRight = 1f
    private var fieldBottom = 1f
    /** Taille de référence d'une particule, en pixels (dépend de la densité). */
    private var dp = 1f

    /** Rectangle où naissent les particules (la vue y retire le bandeau du haut). */
    fun setField(left: Float, top: Float, right: Float, bottom: Float, density: Float) {
        fieldLeft = left; fieldTop = top; fieldRight = right; fieldBottom = bottom
        dp = density
    }

    // ── État de la partie ────────────────────────────────────────────────────
    /** Temps de jeu écoulé : l'horloge de toutes les particules. */
    var time = 0f
        private set
    var score = 0L
        private set
    var lives = START_LIVES
        private set
    var combo = 0
        private set
    var bestCombo = 0
        private set
    var perfects = 0
        private set
    var goods = 0
        private set
    var misses = 0
        private set
    /** Jauge de surchauffe, de 0 à 1. */
    var heat = 0f
        private set
    /** Secondes de surchauffe restantes ; 0 hors surchauffe. */
    var overheatLeft = 0f
        private set
    val isOverheating get() = overheatLeft > 0f

    val particles = ArrayList<Particle>()
    private var nextSpawnAt = 0f
    private var nextChainId = 0
    private val unlocked = HashSet<ParticleKind>()

    val multiplier: Int
        get() = (1 + combo / COMBO_PER_MULT).coerceAtMost(MAX_MULT) *
            (if (isOverheating) OVERHEAT_MULT else 1)

    fun start() {
        particles.clear()
        unlocked.clear()
        unlocked += ParticleKind.PROTON
        time = 0f
        score = 0
        lives = START_LIVES
        combo = 0
        bestCombo = 0
        perfects = 0
        goods = 0
        misses = 0
        heat = 0f
        overheatLeft = 0f
        nextChainId = 0
        nextSpawnAt = 0.6f
        phase = ReflexPhase.PLAYING
    }

    // ── Courbe de difficulté ─────────────────────────────────────────────────
    /** De 0 (début) à 1 (asymptote), selon le temps de jeu. */
    fun difficulty() = 1f - exp(-time / DIFFICULTY_TAU)

    fun spawnInterval() = lerp(1.05f, 0.40f, difficulty())
    fun approachTime() = lerp(1.45f, 0.78f, difficulty())
    private fun radiusPx() = lerp(46f, 33f, difficulty()) * dp

    // ── Boucle ───────────────────────────────────────────────────────────────
    fun update(dt: Float) {
        if (phase != ReflexPhase.PLAYING) return
        time += dt

        if (isOverheating) {
            overheatLeft -= dt
            heat = (overheatLeft / OVERHEAT_SECONDS).coerceAtLeast(0f)
            if (overheatLeft <= 0f) {
                overheatLeft = 0f
                heat = 0f
                listener?.onOverheatEnd()
            }
        }

        for (kind in ParticleKind.entries) {
            if (kind !in unlocked && time >= kind.unlockAt) {
                unlocked += kind
                listener?.onUnlock(kind)
            }
        }

        moveElectrons(dt)
        expire()
        if (phase != ReflexPhase.PLAYING) return

        if (time >= nextSpawnAt) spawn()
    }

    private fun moveElectrons(dt: Float) {
        for (p in particles) {
            if (!p.alive || p.kind != ParticleKind.ELECTRON) continue
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.x < fieldLeft + p.radius) { p.x = fieldLeft + p.radius; p.vx = -p.vx }
            if (p.x > fieldRight - p.radius) { p.x = fieldRight - p.radius; p.vx = -p.vx }
            if (p.y < fieldTop + p.radius) { p.y = fieldTop + p.radius; p.vy = -p.vy }
            if (p.y > fieldBottom - p.radius) { p.y = fieldBottom - p.radius; p.vy = -p.vy }
        }
    }

    /** L'antimatière s'éteint sans rien coûter ; tout le reste, raté, coûte une vie. */
    private fun expire() {
        val it = particles.iterator()
        val brokenChains = HashSet<Int>()
        while (it.hasNext()) {
            val p = it.next()
            if (!p.alive) { it.remove(); continue }
            if (time <= p.hitAt + GOOD_WINDOW) continue
            if (p.kind == ParticleKind.ANTI) {
                // L'antimatière reste visible un peu après son « moment », puis s'éteint.
                if (time > p.hitAt + 0.35f) {
                    p.alive = false
                    it.remove()
                    listener?.onAntiFaded(p)
                }
                continue
            }
            p.alive = false
            it.remove()
            listener?.onMiss(p)
            if (p.kind == ParticleKind.CHAIN) brokenChains += p.chainId
            else loseLife()
            if (phase != ReflexPhase.PLAYING) return
        }
        // Une chaîne ratée s'effondre entière, mais ne coûte qu'une vie.
        for (id in brokenChains) {
            for (p in particles) {
                if (p.alive && p.chainId == id) {
                    p.alive = false
                    listener?.onMiss(p)
                }
            }
            particles.removeAll { !it.alive }
            loseLife()
            if (phase != ReflexPhase.PLAYING) return
        }
    }

    // ── Naissances ───────────────────────────────────────────────────────────
    private fun pickKind(): ParticleKind {
        // Chaque sorte gagne en fréquence pendant la minute qui suit son arrivée.
        fun weight(kind: ParticleKind, from: Float, to: Float): Float {
            if (kind !in unlocked) return 0f
            val t = ((time - kind.unlockAt) / 60f).coerceIn(0f, 1f)
            return lerp(from, to, t)
        }
        val weights = floatArrayOf(
            1f,
            weight(ParticleKind.ANTI, 0.14f, 0.30f),
            weight(ParticleKind.ELECTRON, 0.16f, 0.28f),
            weight(ParticleKind.NUCLEUS, 0.10f, 0.20f),
            weight(ParticleKind.CHAIN, 0.07f, 0.13f)
        )
        var roll = rng.nextFloat() * weights.sum()
        for (i in weights.indices) {
            roll -= weights[i]
            if (roll <= 0f) return ParticleKind.entries[i]
        }
        return ParticleKind.PROTON
    }

    private fun spawn() {
        val kind = pickKind()
        val approach = approachTime()
        val r = radiusPx()
        when (kind) {
            ParticleKind.CHAIN -> spawnChain(r, approach)
            else -> {
                val pos = freeSpot(r) ?: run { nextSpawnAt = time + 0.15f; return }
                val extra = if (kind == ParticleKind.ELECTRON) 1.2f else 1f
                val p = Particle(kind, pos.first, pos.second, r, time, time + approach * extra)
                if (kind == ParticleKind.ELECTRON) {
                    val speed = lerp(70f, 130f, difficulty()) * dp
                    val a = rng.nextFloat() * 6.2832f
                    p.vx = kotlin.math.cos(a) * speed
                    p.vy = kotlin.math.sin(a) * speed
                }
                particles += p
            }
        }
        val gap = if (kind == ParticleKind.CHAIN) CHAIN_STEP * (CHAIN_LENGTH - 1) else 0f
        nextSpawnAt = time + spawnInterval() + gap
    }

    /** Trois particules en ligne brisée, chacune un peu plus tard que la précédente. */
    private fun spawnChain(r: Float, approach: Float) {
        val first = freeSpot(r) ?: return
        val id = nextChainId++
        var x = first.first
        var y = first.second
        for (i in 0 until CHAIN_LENGTH) {
            if (i > 0) {
                val next = nearSpot(x, y, r) ?: break
                x = next.first; y = next.second
            }
            particles += Particle(
                ParticleKind.CHAIN, x, y, r,
                ringStart = time, hitAt = time + approach + i * CHAIN_STEP,
                chainId = id, chainIndex = i + 1
            )
        }
    }

    private fun isFree(x: Float, y: Float, r: Float) = particles.none {
        it.alive && hypot(it.x - x, it.y - y) < (it.radius + r) * 1.25f
    }

    private fun freeSpot(r: Float): Pair<Float, Float>? {
        val w = fieldRight - fieldLeft - 2 * r
        val h = fieldBottom - fieldTop - 2 * r
        if (w <= 0f || h <= 0f) return null
        repeat(16) {
            val x = fieldLeft + r + rng.nextFloat() * w
            val y = fieldTop + r + rng.nextFloat() * h
            if (isFree(x, y, r)) return x to y
        }
        return null
    }

    private fun nearSpot(x0: Float, y0: Float, r: Float): Pair<Float, Float>? {
        repeat(20) {
            val a = rng.nextFloat() * 6.2832f
            val d = r * (2.8f + rng.nextFloat() * 1.4f)
            val x = x0 + kotlin.math.cos(a) * d
            val y = y0 + kotlin.math.sin(a) * d
            val inside = x in (fieldLeft + r)..(fieldRight - r) && y in (fieldTop + r)..(fieldBottom - r)
            if (inside && isFree(x, y, r)) return x to y
        }
        return null
    }

    // ── Appuis ───────────────────────────────────────────────────────────────
    /**
     * Un appui en ([x], [y]) survenu [secondsAgo] secondes avant l'image en cours :
     * le fil de jeu ne voit l'appui qu'à l'image suivante, et c'est l'instant réel
     * du doigt qui compte pour le jugement.
     */
    fun tap(x: Float, y: Float, secondsAgo: Float = 0f) {
        if (phase != ReflexPhase.PLAYING) return
        val at = time - secondsAgo.coerceIn(0f, 0.1f)

        // La particule visée : sous le doigt, et la plus proche de son « moment ».
        var target: Particle? = null
        var bestDist = Float.MAX_VALUE
        for (p in particles) {
            if (!p.alive) continue
            val d = hypot(p.x - x, p.y - y)
            if (d > p.radius * TOUCH_SLOP) continue
            val rank = d / p.radius + kotlin.math.abs(p.hitAt - at)
            if (rank < bestDist) { bestDist = rank; target = p }
        }

        if (target == null) {
            listener?.onEmptyTap(x, y)
            breakCombo()
            return
        }
        val p = target
        when (p.kind) {
            ParticleKind.ANTI -> {
                p.alive = false
                particles.remove(p)
                listener?.onAntiTouched(p)
                breakCombo()
                if (!isOverheating) heat *= 0.5f
                loseLife()
            }
            ParticleKind.NUCLEUS -> if (p.hitsLeft == 2) {
                // Premier appui : le noyau se fend, un anneau court repart de zéro.
                p.hitsLeft = 1
                p.ringStart = at
                p.hitAt = at + CRACK_RING
                score += 25L * multiplier
                listener?.onCrack(p)
            } else {
                resolveHit(p, at)
            }
            ParticleKind.CHAIN -> {
                val lower = particles.any { it.alive && it.chainId == p.chainId && it.chainIndex < p.chainIndex }
                if (lower) {
                    listener?.onWrongOrder(p)
                    breakCombo()
                } else {
                    resolveHit(p, at)
                }
            }
            else -> resolveHit(p, at)
        }
    }

    private fun grade(p: Particle, at: Float): HitGrade {
        val delta = at - p.hitAt
        return when {
            kotlin.math.abs(delta) <= PERFECT_WINDOW -> HitGrade.PERFECT
            kotlin.math.abs(delta) <= GOOD_WINDOW -> HitGrade.GOOD
            else -> HitGrade.EARLY
        }
    }

    private fun resolveHit(p: Particle, at: Float) {
        val g = grade(p, at)
        p.alive = false
        particles.remove(p)
        when (g) {
            HitGrade.PERFECT -> perfects++
            HitGrade.GOOD -> goods++
            HitGrade.EARLY -> Unit
        }
        if (g == HitGrade.EARLY) {
            breakCombo()
        } else {
            combo++
            if (combo > bestCombo) bestCombo = combo
            if (combo % LIFE_COMBO_STEP == 0 && lives < MAX_LIVES) {
                lives++
                listener?.onLifeRegained()
            }
        }
        val points = g.basePoints * multiplier
        score += points
        listener?.onHit(p, g, points)
        if (!isOverheating && g.heatGain > 0f) {
            heat = (heat + g.heatGain).coerceAtMost(1f)
            if (heat >= 1f) {
                overheatLeft = OVERHEAT_SECONDS
                listener?.onOverheatStart()
            }
        }
    }

    private fun breakCombo() {
        if (combo > 0) listener?.onComboBroken(combo)
        combo = 0
    }

    private fun loseLife() {
        misses++
        breakCombo()
        lives--
        if (lives <= 0) {
            lives = 0
            phase = ReflexPhase.GAME_OVER
            for (p in particles) p.alive = false
            listener?.onGameOver()
            particles.clear()
        }
    }
}
