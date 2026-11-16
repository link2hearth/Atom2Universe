package com.Atom2Universe.app.games.balance

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Règles du mode « Gros tas », en mètres (Y vers le haut, origine au pied du pivot).
 *
 * Les pièces sont des polyominos posés sur une **grille qui descend** : une case vaut
 * [CELL] de côté et un kilo. La masse d'une pièce se lit donc à sa taille, rien n'est écrit.
 */
object BigPileRules {
    const val WORLD_WIDTH = 5f
    const val COLUMNS = 16
    const val CELL = WORLD_WIDTH / COLUMNS

    /**
     * Les pièces gardent la taille d'une case, mais se placent et se déplacent au dixième de
     * case : la grille dessinée reste la même, le geste devient fluide.
     */
    const val SUB = 10
    const val STEP = CELL / SUB

    /** La planche couvre quatorze colonnes : une colonne de vide de chaque côté. */
    const val PLANK_HALF_LENGTH = 7 * CELL
    const val PLANK_HALF_THICKNESS = 0.06f

    /** Lourde : elle répond lentement, comme une vraie poutre. */
    const val PLANK_MASS = 40f
    const val PIVOT_HEIGHT = 0.85f
    const val PLANK_TOP = PIVOT_HEIGHT + PLANK_HALF_THICKNESS

    /**
     * Rappel de la balance, en kg·m : c'est l'appui large et la quille lourde sous la planche.
     *
     * Le tas, lui, est posé **au-dessus** du pivot : quand la planche penche, son centre de
     * gravité glisse du côté bas et aggrave la pente. Le moteur physique le fait tout seul, par
     * les contacts. Tant que la somme masse × hauteur du tas reste sous ce rappel, la balance
     * revient ; au-delà, le moindre écart s'emballe. Une planche pleine sur huit rangs y est
     * déjà : un tas aussi haut ne tient pas longtemps, quel que soit l'écran.
     */
    const val KEEL = 180f

    const val SLOTS = 4
    const val LIVES = 3

    /** Pente au-delà de laquelle la balance est en train de basculer, et combien de temps on la tolère. */
    const val TIP_DEG = 15f
    const val TIP_DELAY = 1f

    /** Hauteur de la réserve, en haut de l'écran. */
    const val RESERVE_HEIGHT = 1.4f

    // Rythme : une pièce toutes les trois secondes au départ, puis de plus en plus vite.
    const val SPAWN_START = 3f
    const val SPAWN_MIN = 1.4f
    const val SPAWN_RAMP = 0.009f
    const val FALL_START = 0.6f
    const val FALL_MAX = 1.1f
    const val FALL_RAMP = 0.003f
}

/** Une pièce : sa forme sur la grille, puis, une fois posée, son corps physique. */
class BigPiece internal constructor(val id: Int, val kind: Kind) {

    /** Les formes, en cases (x vers la droite, y vers le haut). Le poids règle leur fréquence. */
    enum class Kind(val cells: Array<IntArray>, val weight: Int) {
        MONO(arrayOf(intArrayOf(0, 0)), 2),
        DUO(arrayOf(intArrayOf(0, 0), intArrayOf(1, 0)), 3),
        TRI_I(arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(2, 0)), 2),
        TRI_L(arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(0, 1)), 2),
        SQUARE(arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1)), 2),
        BAR(arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(2, 0), intArrayOf(3, 0)), 1),
        L(arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(2, 0), intArrayOf(2, 1)), 2),
        T(arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(2, 0), intArrayOf(1, 1)), 2),
        Z(arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(2, 1)), 2)
    }

    enum class State { RESERVE, FALLING, LANDED, FALLEN }

    var cells: Array<IntArray> = kind.cells.map { it.copyOf() }.toTypedArray()
        private set
    var state = State.RESERVE
        internal set
    var slot = -1
        internal set

    /**
     * Position sur la grille qui descend, en dixièmes de case ([BigPileRules.SUB]) : bord
     * gauche et bas de la pièce.
     */
    var col = 0
        internal set
    var row = 0
        internal set

    /**
     * Le rang le plus bas atteint. La pièce ne peut jamais remonter de plus d'une case au-dessus :
     * on peut la soulever un peu, pas la faire grimper en la reprenant en boucle.
     */
    var floorRow = 0
        internal set

    /** Corps physique une fois posée ; il reste dessiné, hors du monde, quand la pièce tombe. */
    var body: PhysBody? = null
        internal set

    /** Opacité d'une pièce tombée, qui s'efface au sol. */
    var fade = 0f
        internal set

    val mass: Int get() = cells.size
    val w: Int get() = cells.maxOf { it[0] } + 1
    val h: Int get() = cells.maxOf { it[1] } + 1

    /** Quart de tour dans le sens trigonométrique, recalé en bas à gauche. */
    internal fun rotate() {
        val turned = cells.map { intArrayOf(-it[1], it[0]) }
        val minX = turned.minOf { it[0] }
        val minY = turned.minOf { it[1] }
        cells = turned.map { intArrayOf(it[0] - minX, it[1] - minY) }.toTypedArray()
    }

    internal fun restore(saved: Array<IntArray>) {
        cells = saved
    }
}

/**
 * Le mode « Gros tas » : les pièces arrivent seules dans la réserve, le joueur les pose sur une
 * grille qui descend « à la Tetris », et elles atterrissent sur une large balance. Il faut tenir
 * l'équilibre des deux tas le plus longtemps possible.
 */
class BigPileGame(private val rng: Random = Random.Default) {

    enum class Phase { PLAYING, OVER }

    enum class Ending { NONE, TIPPED, DROPPED, FULL }

    val world = PhysWorld()
    lateinit var plank: PhysBody
        private set
    private lateinit var ground: PhysBody

    val pieces = ArrayList<BigPiece>()
    val reserve = arrayOfNulls<BigPiece>(BigPileRules.SLOTS)

    var phase = Phase.PLAYING
        private set
    var ending = Ending.NONE
        private set
    /** Pièces posées sur la balance. */
    var score = 0
        private set
    var lives = BigPileRules.LIVES
        private set
    var elapsed = 0f
        private set

    /** Change à chaque pose, chute ou fin de partie : la vue sait quand prévenir l'activité. */
    var revision = 0
        private set

    var worldHeight = 8f
        private set

    /**
     * La bande gardée en haut de l'écran pour la réserve. Debout, la réserve y tient ; couché,
     * elle passe en colonne sur le côté et la grille monte presque jusqu'en haut.
     */
    var reserveBand = BigPileRules.RESERVE_HEIGHT
        private set
    val gridTop: Float get() = worldHeight - reserveBand

    /** Vrai dès qu'une pièce a quitté la réserve : la hauteur de la grille ne doit plus changer. */
    val started: Boolean get() = pieces.any { it.state != BigPiece.State.RESERVE }

    /** Chemin parcouru par la grille depuis le début, en mètres. */
    var shift = 0.0
        private set
    var fallSpeed = BigPileRules.FALL_START
        private set

    /** De 0 à 1 : depuis combien de temps la balance penche trop, rapporté au délai toléré. */
    var danger = 0f
        private set

    /** Les tests posent les pièces eux-mêmes : plus aucune n'arrive seule. */
    internal var autoSpawn = true

    private var spawnTimer = 0f
    private var tipTime = 0f
    private var nextId = 1

    // ── Pièce tenue ───────────────────────────────────────────────────────────

    var held: BigPiece? = null
        private set
    var heldFromReserve = false
        private set
    /** Pièce prise dans la réserve : où elle se poserait, et si l'endroit est libre. */
    var heldCol = 0
        private set
    var heldRow = 0
        private set
    var heldValid = false
        private set
    /** Coin bas gauche où la dessiner quand l'endroit visé est refusé. */
    var heldFreeX = 0f
        private set
    var heldFreeY = 0f
        private set

    private var fingerX = 0f
    private var fingerY = 0f
    private var grabDx = 0f
    private var lastFingerY = 0f
    private var verticalAccum = 0f
    private var aimTick = 0

    init {
        buildWorld()
    }

    private fun buildWorld() {
        world.clear()
        world.gravity = 9.81f
        world.allowedPenetration = 0.003f

        ground = PhysBody(BigPileRules.WORLD_WIDTH * 2f, 0.5f, 0f).apply {
            x = 0f
            y = -0.5f
            lockPosition = true
            lockRotation = true
            friction = 0.8f
            refreshMass()
        }
        world.add(ground)

        plank = PhysBody(
            BigPileRules.PLANK_HALF_LENGTH,
            BigPileRules.PLANK_HALF_THICKNESS,
            BigPileRules.PLANK_MASS
        ).apply {
            x = 0f
            y = BigPileRules.PIVOT_HEIGHT
            lockPosition = true
            friction = 0.8f
            refreshMass()
        }
        world.add(plank)
    }

    fun setViewport(worldHeight: Float, reserveBand: Float = BigPileRules.RESERVE_HEIGHT) {
        this.reserveBand = reserveBand
        this.worldHeight = worldHeight.coerceAtLeast(reserveBand + 3f)
    }

    fun newGame() {
        for (p in pieces) p.body?.let { world.remove(it) }
        pieces.clear()
        reserve.fill(null)
        held = null
        plank.angle = 0f
        plank.omega = 0f
        phase = Phase.PLAYING
        ending = Ending.NONE
        score = 0
        lives = BigPileRules.LIVES
        elapsed = 0f
        shift = 0.0
        fallSpeed = BigPileRules.FALL_START
        tipTime = 0f
        danger = 0f
        revision++
        if (autoSpawn) {
            // Deux pièces d'entrée, pour avoir le choix dès la première seconde.
            spawn()
            spawn()
        }
        spawnTimer = BigPileRules.SPAWN_START
    }

    // ── Grille ────────────────────────────────────────────────────────────────

    private val cell = BigPileRules.CELL
    private val sub = BigPileRules.SUB
    private val step = BigPileRules.STEP
    private val halfWidth = BigPileRules.WORLD_WIDTH / 2f

    /** Bord gauche d'une position horizontale, en dixièmes de case. */
    fun colLeft(col: Int): Float = -halfWidth + col * step

    /** Bas d'une position verticale, en dixièmes de case, dans la grille qui descend. */
    fun rowBottom(row: Int): Float = (row * step - shift).toFloat()

    /** Coin bas gauche d'une case de pièce posée en ([col], [row]). */
    fun cellLeft(col: Int, c: IntArray): Float = colLeft(col + c[0] * sub)
    fun cellBottom(row: Int, c: IntArray): Float = rowBottom(row + c[1] * sub)

    /** Rang le plus haut où une pièce de hauteur [h] tient encore sous la réserve. */
    private fun topRow(h: Int): Int = floor((gridTop + shift) / step).toInt() - h * sub

    private val maxCol: Int get() = BigPileRules.COLUMNS * sub

    private fun spawnInterval(): Float =
        (BigPileRules.SPAWN_START - BigPileRules.SPAWN_RAMP * elapsed).coerceAtLeast(BigPileRules.SPAWN_MIN)

    private fun randomKind(): BigPiece.Kind {
        val kinds = BigPiece.Kind.entries
        var roll = rng.nextInt(kinds.sumOf { it.weight })
        for (k in kinds) {
            roll -= k.weight
            if (roll < 0) return k
        }
        return kinds.last()
    }

    /** Une nouvelle pièce : dans la réserve s'il y a de la place, sinon elle tombe au hasard. */
    private fun spawn() {
        val p = BigPiece(nextId++, randomKind())
        repeat(rng.nextInt(4)) { p.rotate() }
        val free = reserve.indexOfFirst { it == null }
        if (free >= 0) {
            reserve[free] = p
            p.slot = free
            pieces.add(p)
            return
        }
        // Réserve pleine : la pièce part d'elle-même, en haut de la grille, au-dessus de la
        // planche (jamais dans les colonnes vides du bord, ce serait une chute assurée).
        val top = topRow(p.h)
        val cols = (sub..(BigPileRules.COLUMNS - 1 - p.w) * sub).shuffled(rng)
        for (c in cols) {
            if (fits(p, c, top)) {
                startFalling(p, c, top)
                pieces.add(p)
                return
            }
        }
        finish(Ending.FULL)
    }

    private fun startFalling(p: BigPiece, col: Int, row: Int) {
        p.state = BigPiece.State.FALLING
        p.slot = -1
        p.col = col
        p.row = row
        p.floorRow = row
    }

    /** Pose une pièce dans la grille, pour les tests ; [col] se compte en cases entières. */
    internal fun dropForTest(kind: BigPiece.Kind, col: Int, bottom: Float, turns: Int = 0): BigPiece {
        val p = BigPiece(nextId++, kind)
        repeat(turns) { p.rotate() }
        val row = ((bottom + shift) / step).roundToInt()
        startFalling(p, col * sub, row)
        pieces.add(p)
        return p
    }

    // ── Appuis ────────────────────────────────────────────────────────────────

    // Boîtes englobantes des cases posées, refaites une fois par pas.
    private var supMinX = FloatArray(64)
    private var supMaxX = FloatArray(64)
    private var supMinY = FloatArray(64)
    private var supMaxY = FloatArray(64)
    private var supCount = 0
    private val partPose = FloatArray(3)

    private fun refreshSupports() {
        supCount = 0
        for (p in pieces) {
            if (p.state != BigPiece.State.LANDED) continue
            val b = p.body ?: continue
            for (k in b.parts.indices) {
                b.partWorld(k, partPose)
                val part = b.parts[k]
                val ca = abs(cos(partPose[2]))
                val sa = abs(sin(partPose[2]))
                val hx = part.halfW * ca + part.halfH * sa
                val hy = part.halfW * sa + part.halfH * ca
                if (supCount == supMinX.size) {
                    val n = supCount * 2
                    supMinX = supMinX.copyOf(n); supMaxX = supMaxX.copyOf(n)
                    supMinY = supMinY.copyOf(n); supMaxY = supMaxY.copyOf(n)
                }
                supMinX[supCount] = partPose[0] - hx
                supMaxX[supCount] = partPose[0] + hx
                supMinY[supCount] = partPose[1] - hy
                supMaxY[supCount] = partPose[1] + hy
                supCount++
            }
        }
    }

    /** Hauteur du dessus de la planche à l'abscisse [x] (planche inclinée). */
    fun plankTopAt(x: Float): Float {
        val a = plank.angle
        return BigPileRules.PIVOT_HEIGHT + x * kotlin.math.tan(a) + BigPileRules.PLANK_HALF_THICKNESS / cos(a)
    }

    private companion object {
        const val NOTHING = 0
        const val GROUND = 1
        const val SUPPORT = 2
    }

    /**
     * Ce que touche le rectangle donné, abaissé de [margin] : rien, le sol, ou un appui (la
     * planche, une pièce posée).
     */
    private fun touches(l: Float, b: Float, r: Float, t: Float, margin: Float): Int {
        val bottom = b - margin
        val reach = BigPileRules.PLANK_HALF_LENGTH * cos(plank.angle)
        if (r > -reach && l < reach) {
            val top = maxOf(plankTopAt(l.coerceAtLeast(-reach)), plankTopAt(r.coerceAtMost(reach)))
            // Tout ce qui est sous le dessus de la planche compte : on ne pose rien dessous.
            if (bottom < top) return SUPPORT
        }
        val s = 0.004f
        for (i in 0 until supCount) {
            if (r > supMinX[i] + s && l < supMaxX[i] - s && bottom < supMaxY[i] - s && t > supMinY[i] + s) {
                return SUPPORT
            }
        }
        if (bottom <= 0f) return GROUND
        return NOTHING
    }

    /** La pièce tient-elle à cet endroit de la grille, sans rien chevaucher ? */
    private fun fits(p: BigPiece, col: Int, row: Int): Boolean {
        if (col < 0 || col + p.w * sub > maxCol) return false
        if (row > topRow(p.h)) return false
        for (c in p.cells) {
            val cx = col + c[0] * sub
            val cy = row + c[1] * sub
            for (o in pieces) {
                if (o === p || o.state != BigPiece.State.FALLING) continue
                for (oc in o.cells) {
                    if (abs(o.col + oc[0] * sub - cx) < sub && abs(o.row + oc[1] * sub - cy) < sub) return false
                }
            }
            val l = colLeft(cx)
            val b = rowBottom(cy)
            if (touches(l, b, l + cell, b + cell, 0f) != NOTHING) return false
        }
        return true
    }

    // ── Simulation ────────────────────────────────────────────────────────────

    fun step(dt: Float) {
        if (phase == Phase.PLAYING) {
            elapsed += dt
            fallSpeed = (BigPileRules.FALL_START + BigPileRules.FALL_RAMP * elapsed)
                .coerceAtMost(BigPileRules.FALL_MAX)
            shift += fallSpeed * dt
            if (autoSpawn) {
                spawnTimer -= dt
                if (spawnTimer <= 0f) {
                    spawnTimer += spawnInterval()
                    spawn()
                }
            }
            refreshSupports()
            // La grille glisse sous la pièce tenue : on revise la visée trente fois par seconde.
            if (held != null && heldFromReserve && ++aimTick % 4 == 0) aimFromReserve()
            landTouching()
        }
        applyKeel()
        world.step(dt)
        checkFallen()
        if (phase == Phase.PLAYING) checkTip(dt)
        fadeFallen(dt)
    }

    /** Les pièces de la grille qui touchent un appui passent au moteur physique. */
    private fun landTouching() {
        for (i in pieces.indices) {
            val p = pieces[i]
            if (p.state != BigPiece.State.FALLING) continue
            var hit = NOTHING
            for (c in p.cells) {
                val l = cellLeft(p.col, c)
                val b = cellBottom(p.row, c)
                val t = touches(l, b, l + cell, b + cell, 0.002f)
                if (t == SUPPORT) { hit = SUPPORT; break }
                if (t == GROUND) hit = GROUND
            }
            if (hit != NOTHING) land(p, hit == GROUND)
        }
    }

    private fun bodyFor(p: BigPiece): PhysBody {
        val inset = 0.003f
        val half = cell / 2f - inset
        val body = PhysBody.compound(p.mass.toFloat()) {
            for (c in p.cells) box(half, half, (c[0] + 0.5f) * cell, (c[1] + 0.5f) * cell)
        }
        var cx = 0f
        var cy = 0f
        for (c in p.cells) {
            cx += (c[0] + 0.5f) * cell
            cy += (c[1] + 0.5f) * cell
        }
        body.x = colLeft(p.col) + cx / p.mass
        body.y = rowBottom(p.row) + cy / p.mass
        body.vy = -fallSpeed
        body.friction = 0.7f
        body.tag = p
        return body
    }

    private fun land(p: BigPiece, onGround: Boolean) {
        if (held === p) held = null
        val body = bodyFor(p)
        p.body = body
        if (onGround) {
            body.inWorld = false
            p.state = BigPiece.State.FALLEN
            p.fade = 1f
            loseLife()
        } else {
            p.state = BigPiece.State.LANDED
            world.add(body)
            score++
        }
        revision++
    }

    private fun checkFallen() {
        for (p in pieces) {
            if (p.state != BigPiece.State.LANDED) continue
            val b = p.body ?: continue
            val onGround = b.aabbMinY < 0.02f && b.y < BigPileRules.PIVOT_HEIGHT
            val gone = abs(b.x) > halfWidth + 1f || b.y < -1f
            if (onGround || gone) {
                world.remove(b)
                b.inWorld = false
                p.state = BigPiece.State.FALLEN
                p.fade = 1f
                if (phase == Phase.PLAYING) loseLife()
                revision++
            }
        }
    }

    private fun loseLife() {
        if (phase != Phase.PLAYING) return
        lives--
        if (lives <= 0) finish(Ending.DROPPED)
    }

    private fun fadeFallen(dt: Float) {
        var i = pieces.size - 1
        while (i >= 0) {
            val p = pieces[i]
            if (p.state == BigPiece.State.FALLEN) {
                p.fade -= dt * 1.4f
                if (p.fade <= 0f) pieces.removeAt(i)
            }
            i--
        }
    }

    val leanDeg: Float get() = -Math.toDegrees(plank.angle.toDouble()).toFloat()

    private fun checkTip(dt: Float) {
        tipTime = if (abs(leanDeg) > BigPileRules.TIP_DEG) tipTime + dt else (tipTime - dt).coerceAtLeast(0f)
        danger = (tipTime / BigPileRules.TIP_DELAY).coerceIn(0f, 1f)
        if (tipTime >= BigPileRules.TIP_DELAY) finish(Ending.TIPPED)
    }

    /**
     * Rappel de l'appui et amortissement : 0,7 fois l'amortissement critique de l'ensemble
     * {planche + tas}, pour que la balance oscille une fois puis se pose au lieu de vibrer.
     */
    private fun applyKeel() {
        val g = world.gravity
        var inertia = plank.inertia
        for (p in pieces) {
            if (p.state != BigPiece.State.LANDED) continue
            val b = p.body ?: continue
            val dy = b.y - BigPileRules.PIVOT_HEIGHT
            inertia += b.mass * (b.x * b.x + dy * dy)
        }
        val damping = 1.4f * sqrt(inertia * BigPileRules.KEEL * g)
        plank.torque = -BigPileRules.KEEL * g * sin(plank.angle) - damping * plank.omega
    }

    private fun finish(why: Ending) {
        if (phase == Phase.OVER) return
        phase = Phase.OVER
        ending = why
        held = null
        revision++
        // Ce qui était encore dans la grille tombe pour de bon sur le tas.
        for (p in pieces) {
            if (p.state != BigPiece.State.FALLING) continue
            val body = bodyFor(p)
            body.vy = 0f
            p.body = body
            p.state = BigPiece.State.LANDED
            world.add(body)
        }
    }

    // ── Gestes (coordonnées du monde) ─────────────────────────────────────────

    /** Hauteur à laquelle une pièce prise dans la réserve flotte au-dessus du doigt. */
    private val lift = cell * 0.8f

    fun slotAt(wx: Float): Int =
        ((wx + halfWidth) / (BigPileRules.WORLD_WIDTH / BigPileRules.SLOTS)).toInt()
            .coerceIn(0, BigPileRules.SLOTS - 1)

    /**
     * Prend une pièce sous le doigt : dans la réserve (la vue dit quelle place, [slot], car c'est
     * elle qui sait où les places sont dessinées ; -1 hors de la réserve), ou en train de descendre.
     */
    fun touchDown(wx: Float, wy: Float, slot: Int): Boolean {
        if (phase != Phase.PLAYING) return false
        fingerX = wx
        fingerY = wy
        lastFingerY = wy
        verticalAccum = 0f
        if (slot >= 0) {
            val p = reserve[slot] ?: return false
            held = p
            heldFromReserve = true
            aimFromReserve()
            return true
        }
        val p = fallingAt(wx, wy) ?: return false
        held = p
        heldFromReserve = false
        grabDx = (wx - colLeft(p.col)) / step
        return true
    }

    fun touchMove(wx: Float, wy: Float) {
        val p = held ?: return
        fingerX = wx
        fingerY = wy
        if (heldFromReserve) {
            aimFromReserve()
            return
        }
        refreshSupports()
        // De côté : la pièce suit le doigt, dixième par dixième (elle ne traverse rien).
        val targetCol = ((wx + halfWidth) / step - grabDx).roundToInt()
        while (p.col != targetCol) {
            val c = p.col + if (targetCol > p.col) 1 else -1
            if (!fits(p, c, p.row)) break
            p.col = c
        }
        // En hauteur : au geste, dixième par dixième. Vers le bas autant qu'on veut ; vers le
        // haut, jamais plus d'une case au-dessus du rang le plus bas déjà atteint.
        verticalAccum += wy - lastFingerY
        lastFingerY = wy
        while (verticalAccum <= -step) {
            verticalAccum += step
            if (fits(p, p.col, p.row - 1)) p.row-- else verticalAccum = 0f
        }
        while (verticalAccum >= step) {
            verticalAccum -= step
            if (p.row + 1 <= p.floorRow + sub && fits(p, p.col, p.row + 1)) p.row++ else verticalAccum = 0f
        }
        p.floorRow = minOf(p.floorRow, p.row)
    }

    /** Lâche la pièce tenue ; une simple touche la fait tourner. */
    fun touchUp(tap: Boolean) {
        val p = held ?: return
        held = null
        if (heldFromReserve) {
            if (tap) {
                p.rotate()
            } else if (heldValid && p.state == BigPiece.State.RESERVE) {
                reserve[p.slot] = null
                startFalling(p, heldCol, heldRow)
                revision++
            }
            return
        }
        if (tap && p.state == BigPiece.State.FALLING) rotateFalling(p)
    }

    private fun aimFromReserve() {
        val p = held ?: return
        val left = fingerX - p.w * cell / 2f
        val bottom = fingerY + lift
        heldFreeX = left
        heldFreeY = bottom
        val col = ((left + halfWidth) / step).roundToInt().coerceIn(0, maxCol - p.w * sub)
        val row = ((bottom + shift) / step).roundToInt().coerceAtMost(topRow(p.h))
        heldCol = col
        heldRow = row
        // Le doigt doit être au-dessus de la grille (couché, la réserve est à côté d'elle).
        heldValid = fingerY < gridTop && abs(fingerX) <= halfWidth && nearestFit(p, col, row)
    }

    /**
     * La place libre la plus proche de ([col], [row]), à une case au plus : une pièce lâchée qui
     * mord un peu sur sa voisine se cale contre elle au lieu de retourner dans la réserve. On
     * essaie les écarts croissants, de côté d'abord. Rend faux s'il n'y a rien d'assez près.
     */
    private fun nearestFit(p: BigPiece, col: Int, row: Int): Boolean {
        val top = topRow(p.h)
        for (d in 0..sub) {
            for (k in 0..2 * d) {
                // 0, −1, +1, −2, +2… : le décalage horizontal le plus petit en premier.
                val dc = if (k % 2 == 0) k / 2 else -(k + 1) / 2
                val rest = d - abs(dc)
                val first = if (rest == 0) 0 else -rest
                var dr = first
                while (dr <= rest) {
                    val r = row + dr
                    if (r <= top && fits(p, col + dc, r)) {
                        heldCol = col + dc
                        heldRow = r
                        return true
                    }
                    dr += if (rest == 0) 1 else 2 * rest
                }
            }
        }
        return false
    }

    private fun rotateFalling(p: BigPiece) {
        refreshSupports()
        val saved = p.cells
        val ow = p.w
        val oh = p.h
        p.rotate()
        val baseCol = p.col + (ow - p.w) * sub / 2
        val baseRow = minOf(p.row + (oh - p.h) * sub / 2, p.floorRow + sub)
        for (dc in intArrayOf(0, -sub, sub, -2 * sub, 2 * sub)) {
            if (fits(p, baseCol + dc, baseRow)) {
                p.col = baseCol + dc
                p.row = baseRow
                p.floorRow = minOf(p.floorRow, p.row)
                return
            }
        }
        p.restore(saved)
    }

    /** Pièce de la grille sous le doigt, avec une demi-case de marge pour les petites. */
    private fun fallingAt(wx: Float, wy: Float): BigPiece? {
        var best: BigPiece? = null
        var bestDist = Float.MAX_VALUE
        val margin = cell * 0.5f
        for (p in pieces) {
            if (p.state != BigPiece.State.FALLING) continue
            for (c in p.cells) {
                val cx = cellLeft(p.col, c) + cell / 2f
                val cy = cellBottom(p.row, c) + cell / 2f
                val dx = abs(wx - cx)
                val dy = abs(wy - cy)
                if (dx > cell / 2f + margin || dy > cell / 2f + margin) continue
                val d = dx + dy
                if (d < bestDist) {
                    bestDist = d
                    best = p
                }
            }
        }
        return best
    }
}
