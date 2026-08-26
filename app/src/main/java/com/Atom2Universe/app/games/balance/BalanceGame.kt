package com.Atom2Universe.app.games.balance

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Règles géométriques du plateau, en mètres (repère physique, Y vers le haut,
 * origine au pivot).
 */
object BalanceRules {
    /** Largeur du monde visible ; la vue cale cette largeur sur sa propre largeur. */
    const val WORLD_WIDTH = 5f

    /** Demi-longueur de la planche. */
    const val PLANK_HALF_LENGTH = 1.7f
    const val PLANK_HALF_THICKNESS = 0.05f
    const val PLANK_MASS = 9f

    /** Hauteur du pivot : c'est aussi le centre de rotation de la planche. */
    const val PIVOT_HEIGHT = 0.85f

    /**
     * Demi-largeur de la zone interdite au centre : aucune brique ne doit
     * mordre sur le pivot, même d'un coin.
     */
    const val DEAD_HALF = 0.12f

    /**
     * Pas de la règle gravée sur la planche. Purement visuel : rien ne s'y
     * aimante, le joueur pose où il veut, mais ça donne un repère pour calculer.
     */
    const val RULER_UNIT = PLANK_HALF_LENGTH / 6f

    /** Face supérieure de la planche quand elle est horizontale. */
    const val PLANK_TOP = PIVOT_HEIGHT + PLANK_HALF_THICKNESS

    /** Un poids lâché plus bas que ça est considéré comme tombé. */
    const val FALL_LIMIT = PIVOT_HEIGHT - 0.15f

    /**
     * Côté d'une brique, en mètres : plus la masse est grande, plus la brique
     * est grosse (surface ~ masse, comme si toutes avaient la même densité).
     *
     * Le facteur [weightCount] resserre l'échelle quand il y a beaucoup de
     * poids : à sept briques, tout doit encore tenir sur la planche. Les
     * proportions entre briques d'un même niveau, elles, ne changent pas.
     */
    fun sizeForMass(mass: Int, weightCount: Int): Float {
        val fit = when {
            weightCount <= 3 -> 1f
            weightCount <= 5 -> 0.88f
            else -> 0.76f
        }
        return (0.17f + 0.055f * sqrt(mass.toFloat())) * fit
    }

    /** Longueur utilisable de chaque côté du pivot. */
    const val USABLE_PER_SIDE = PLANK_HALF_LENGTH - DEAD_HALF

    /**
     * Raideur du rappel de la planche, exprimée en kg·m.
     *
     * Une vraie balance a son centre de gravité *sous* le pivot : elle revient à
     * l'horizontale et s'incline d'autant plus que le déséquilibre est grand.
     * On reproduit ça avec un couple de rappel, ce qui rend le jeu jouable (sinon
     * l'équilibre serait instable et la planche basculerait au moindre écart).
     *
     * À l'équilibre : tan(angle) = déséquilibre / (raideur − terme déstabilisant),
     * ce dernier venant de ce que les poids reposent au-dessus du pivot.
     */
    fun stiffness(totalMass: Float) = (0.40f * totalMass * PLANK_HALF_LENGTH).coerceAtLeast(2f)
}

/** Un poids : une brique de masse entière, avec son corps physique. */
class BalanceWeight(
    val index: Int,
    val mass: Int,
    halfW: Float,
    halfH: Float
) {
    val body = PhysBody(halfW, halfH, mass.toFloat())

    /** Vrai quand le poids a été déposé sur la planche. */
    var placed = false

    /**
     * Abscisse choisie par le joueur. Pendant un test la brique bouge (la
     * planche s'incline, les piles glissent) : c'est cette valeur-là qu'on
     * restitue quand on revient à la pose, pas la position chahutée.
     */
    var plankX = 0f

    /** Emplacement d'attente dans le plateau du haut. */
    var trayX = 0f
    var trayY = 0f

    var dragging = false

    init {
        body.tag = this
        body.inWorld = false
        body.friction = 0.62f
    }

    /** Distance minimale au pivot : la brique ne doit pas mordre sur la zone interdite. */
    val minDistance: Float get() = BalanceRules.DEAD_HALF + body.halfW

    /** Distance maximale : la brique doit reposer entièrement sur la planche. */
    val maxDistance: Float get() = BalanceRules.PLANK_HALF_LENGTH - body.halfW

    /** Couple exercé sur la planche (kg·m) : positif = penche à droite. */
    val torque: Float get() = if (placed) mass * body.x else 0f
}

/**
 * Le jeu d'équilibre : on répartit des poids sur une planche posée sur un pivot,
 * puis on relâche la planche pour voir si les deux côtés se compensent.
 *
 * Le joueur pose où il veut le long de la planche (pas de crans), seule la zone
 * du pivot est interdite. Les briques peuvent s'empiler : une lourde posée de
 * travers sur une petite bascule, exactement comme dans la réalité.
 */
class BalanceGame {

    /**
     * Difficulté : nombre de poids, tolérance d'inclinaison acceptée à l'arrivée
     * et masse maximale des briques.
     */
    enum class Difficulty(
        val weightCount: Int,
        val toleranceDeg: Float,
        val maxMass: Int
    ) {
        EASY(3, 8f, 12),
        MEDIUM(5, 5f, 18),
        HARD(7, 3.5f, 25)
    }

    enum class Phase { PLACING, TESTING, WON, LOST }

    /** Raison d'un échec, ou d'un refus de pose. */
    enum class Notice { NONE, DEAD_ZONE, FELL, TIPPED, TOO_TILTED }

    // ── État ──────────────────────────────────────────────────────────────────

    var difficulty = Difficulty.EASY
        private set
    var phase = Phase.PLACING
        private set
    var level = 1
        private set
    var notice = Notice.NONE

    val weights = ArrayList<BalanceWeight>()

    /** Poids posés, dans l'ordre où le joueur les a posés (les piles en dépendent). */
    private val placementOrder = ArrayList<BalanceWeight>()

    /**
     * Nombre de tests lancés sur ce niveau. Chaque test qui n'aboutit pas —
     * raté ou interrompu — coûte un neutrino sur la récompense finale.
     */
    var testAttempts = 0
        private set

    val world = PhysWorld()
    lateinit var plank: PhysBody
        private set
    private lateinit var ground: PhysBody

    /** Hauteur du monde visible, renseignée par la vue (dépend de l'écran). */
    var worldHeight = 8f
        private set

    /** Bas du plateau de rangement : en dessous, on est en zone de pose. */
    var trayBottom = 6f
        private set

    /** Raideur du rappel retenue pour le test en cours (kg·m). */
    var stiffness = 10f
        private set

    /** Amortissement du levier retenu pour le test en cours. */
    var damping = 1f
        private set

    private var testTime = 0f

    // Détection de la mise au repos : on compare l'inclinaison à celle relevée
    // en [settleRefTime] ; tant qu'elle bouge encore, le repère est réarmé.
    private var settleRefLean = 0f
    private var settleRefTime = 0f

    /** Inclinaison retenue au moment du verdict (le levier peut bouger après). */
    var verdictLeanDeg = 0f
        private set

    // Glisser-déposer
    private var dragged: BalanceWeight? = null
    private var grabDx = 0f
    private var grabDy = 0f

    /** Vrai quand le poids déplacé est au-dessus d'un emplacement valide. */
    var dragValid = false
        private set

    /** Abscisse à laquelle le poids déplacé se posera. */
    var dragPreviewX = 0f
        private set

    /** Hauteur de la surface sur laquelle il se posera (planche ou pile). */
    var dragPreviewTop = 0f
        private set

    private val rng = Random.Default

    init {
        buildWorld()
    }

    // ── Construction ──────────────────────────────────────────────────────────

    private fun buildWorld() {
        world.clear()
        world.gravity = 9.81f

        ground = PhysBody(BalanceRules.WORLD_WIDTH, 0.5f, 0f).apply {
            x = 0f
            y = -0.5f
            lockPosition = true
            lockRotation = true
            friction = 0.8f
            refreshMass()
        }
        world.add(ground)

        plank = PhysBody(
            BalanceRules.PLANK_HALF_LENGTH,
            BalanceRules.PLANK_HALF_THICKNESS,
            BalanceRules.PLANK_MASS
        ).apply {
            x = 0f
            y = BalanceRules.PIVOT_HEIGHT
            lockPosition = true      // la planche ne fait que tourner autour du pivot
            lockRotation = true      // déverrouillée au moment du test
            friction = 0.7f
            refreshMass()
        }
        world.add(plank)
    }

    /** Appelé par la vue quand elle connaît sa taille : fixe la hauteur du monde. */
    fun setViewport(worldHeight: Float) {
        this.worldHeight = worldHeight.coerceAtLeast(4f)
        layoutTray()
    }

    // ── Génération d'un niveau ────────────────────────────────────────────────

    fun newLevel(diff: Difficulty = difficulty) {
        // Changer de difficulté repart du niveau 1 : le record est suivi par
        // difficulté, la progression doit l'être aussi.
        if (diff != difficulty) level = 1
        difficulty = diff
        resetRun()
        testAttempts = 0
        placementOrder.clear()

        for (w in weights) {
            world.bodies.remove(w.body)
            world.forgetContacts(w.body)
        }
        weights.clear()

        for ((i, m) in generateMasses(diff).withIndex()) {
            // Surface proportionnelle à la masse : une brique lourde se voit.
            // L'allure varie un peu (un peu large, un peu haute) sans trahir
            // la lecture de la masse.
            val side = BalanceRules.sizeForMass(m, diff.weightCount)
            val aspect = 0.86f + rng.nextFloat() * 0.32f
            val width = side * sqrt(aspect)
            val height = side / sqrt(aspect)
            val w = BalanceWeight(i, m, width / 2f, height / 2f)
            weights.add(w)
            world.add(w.body)
        }
        layoutTray()
    }

    /** Repart du même niveau : tous les poids retournent dans le plateau. */
    fun resetPlacement() {
        resetRun()
        for (w in weights) sendToTray(w)
    }

    /**
     * Retourne en phase de pose *en gardant* les poids là où ils sont, pour
     * corriger le tir. Utilisable dès qu'un test est lancé : inutile d'attendre
     * le verdict quand on voit que ça part mal.
     *
     * La planche est remise à plat et chaque brique retrouve l'abscisse choisie
     * par le joueur, reposée dans l'ordre initial pour reconstruire les piles.
     */
    fun resumePlacing() {
        if (phase == Phase.PLACING) return
        resetRun()

        val kept = placementOrder.filter { it.placed && it.body.y > BalanceRules.FALL_LIMIT }
        for (w in weights) if (w !in kept) sendToTray(w)
        // On repart d'une planche vide, puis on repose dans l'ordre d'origine :
        // sinon une brique encore de travers servirait d'appui à sa voisine.
        for (w in kept) w.placed = false
        placementOrder.clear()
        for (w in kept) {
            val side = if (w.plankX < 0f) -1f else 1f
            place(w, side * abs(w.plankX).coerceIn(w.minDistance, w.maxDistance))
        }
    }

    private fun resetRun() {
        phase = Phase.PLACING
        notice = Notice.NONE
        testTime = 0f
        settleRefLean = 0f
        settleRefTime = 0f
        dragged = null
        dragValid = false
        plank.angle = 0f
        plank.omega = 0f
        plank.lockRotation = true
        plank.refreshMass()
    }

    /**
     * Tire des masses dont on sait qu'une répartition parfaitement équilibrée
     * existe : voir [hasExactSolution].
     */
    private fun generateMasses(diff: Difficulty): IntArray {
        val n = diff.weightCount
        repeat(500) {
            val masses = IntArray(n)
            var i = 0
            var guard = 0
            while (i < n && guard < 400) {
                guard++
                val m = rng.nextInt(1, diff.maxMass + 1)
                if (masses.take(i).contains(m)) continue
                masses[i] = m
                i++
            }
            if (i == n && hasExactSolution(masses)) return masses
        }
        // Filet de sécurité : des masses régulières sont toujours équilibrables.
        return IntArray(n) { it + 1 }
    }

    /**
     * Vrai s'il existe une répartition des masses en deux camps, et des positions
     * dans les plages autorisées, qui annule exactement le couple.
     *
     * Comme les positions sont continues, chaque camp couvre tout un intervalle
     * de couples : il suffit qu'un découpage donne deux intervalles qui se
     * croisent (avec un peu de marge, pour ne pas générer une solution qui ne
     * tiendrait qu'au millimètre près).
     */
    private fun hasExactSolution(masses: IntArray): Boolean {
        val n = masses.size
        val lo = FloatArray(n)
        val hi = FloatArray(n)
        // Marge de largeur : la plus large des aspects possibles, plus un espace
        // entre briques. Il doit rester de la place pour les faire coulisser.
        var totalWidth = 0f
        for (i in 0 until n) {
            val side = BalanceRules.sizeForMass(masses[i], n)
            val halfW = side / 2f
            totalWidth += side * 1.09f + 0.02f
            lo[i] = masses[i] * (BalanceRules.DEAD_HALF + halfW)
            hi[i] = masses[i] * (BalanceRules.PLANK_HALF_LENGTH - halfW)
        }
        // Les briques doivent tenir côte à côte sur les deux moitiés de planche,
        // avec assez de jeu pour ajuster : sans ça il faudrait les empiler.
        if (totalWidth > 2f * BalanceRules.USABLE_PER_SIDE - 0.55f) return false

        val margin = 0.4f
        for (mask in 1 until (1 shl n) - 1) {
            var leftLo = 0f; var leftHi = 0f
            var rightLo = 0f; var rightHi = 0f
            for (i in 0 until n) {
                if ((mask shr i) and 1 == 1) { leftLo += lo[i]; leftHi += hi[i] }
                else { rightLo += lo[i]; rightHi += hi[i] }
            }
            val overlap = minOf(leftHi, rightHi) - maxOf(leftLo, rightLo)
            if (overlap >= margin) return true
        }
        return false
    }

    // ── Plateau de rangement ──────────────────────────────────────────────────

    /** Range les poids non posés en une ou plusieurs rangées en haut de l'écran. */
    private fun layoutTray() {
        if (weights.isEmpty()) return
        val margin = 0.14f
        val usable = BalanceRules.WORLD_WIDTH - 2f * margin
        val gap = 0.08f

        val rows = ArrayList<MutableList<BalanceWeight>>()
        var current = ArrayList<BalanceWeight>()
        var width = 0f
        for (w in weights) {
            val bw = w.body.halfW * 2f
            if (current.isNotEmpty() && width + gap + bw > usable) {
                rows.add(current)
                current = ArrayList()
                width = 0f
            }
            if (current.isNotEmpty()) width += gap
            width += bw
            current.add(w)
        }
        if (current.isNotEmpty()) rows.add(current)

        var top = worldHeight - 0.14f
        for (row in rows) {
            val rowHeight = row.maxOf { it.body.halfH * 2f }
            val rowWidth = row.sumOf { (it.body.halfW * 2f).toDouble() }.toFloat() + gap * (row.size - 1)
            var x = -rowWidth / 2f
            for (w in row) {
                x += w.body.halfW
                w.trayX = x
                w.trayY = top - rowHeight / 2f
                x += w.body.halfW + gap
            }
            top -= rowHeight + 0.10f
        }
        trayBottom = top - 0.04f

        for (w in weights) if (!w.placed && !w.dragging) parkInTray(w)
    }

    private fun parkInTray(w: BalanceWeight) {
        w.body.x = w.trayX
        w.body.y = w.trayY
        w.body.angle = 0f
        w.body.vx = 0f; w.body.vy = 0f; w.body.omega = 0f
        w.body.inWorld = false
    }

    private fun sendToTray(w: BalanceWeight) {
        w.placed = false
        w.dragging = false
        placementOrder.remove(w)
        world.forgetContacts(w.body)
        parkInTray(w)
    }

    // ── Glisser-déposer ───────────────────────────────────────────────────────

    /** Poids situé sous le point (monde) donné ; en cas de pile, celui du dessus. */
    fun weightAt(wx: Float, wy: Float): BalanceWeight? {
        var best: BalanceWeight? = null
        for (w in weights) {
            if (!contains(w.body, wx, wy)) continue
            if (best == null || w.body.y > best.body.y) best = w
        }
        return best
    }

    private fun contains(b: PhysBody, wx: Float, wy: Float): Boolean {
        val dx = wx - b.x
        val dy = wy - b.y
        val c = kotlin.math.cos(-b.angle)
        val s = kotlin.math.sin(-b.angle)
        val lx = dx * c - dy * s
        val ly = dx * s + dy * c
        return abs(lx) <= b.halfW + 0.03f && abs(ly) <= b.halfH + 0.03f
    }

    fun beginDrag(w: BalanceWeight, wx: Float, wy: Float) {
        if (phase != Phase.PLACING) return
        dragged = w
        w.dragging = true
        w.placed = false
        w.body.inWorld = false     // fantôme : ne bouscule pas les autres briques
        w.body.angle = 0f
        w.body.vx = 0f; w.body.vy = 0f; w.body.omega = 0f
        world.forgetContacts(w.body)
        grabDx = w.body.x - wx
        grabDy = w.body.y - wy
        notice = Notice.NONE
        updatePreview()
    }

    fun dragTo(wx: Float, wy: Float) {
        val w = dragged ?: return
        val half = BalanceRules.WORLD_WIDTH / 2f
        w.body.x = (wx + grabDx).coerceIn(-half + w.body.halfW, half - w.body.halfW)
        w.body.y = (wy + grabDy).coerceIn(0.2f, worldHeight - 0.05f)
        updatePreview()
    }

    private fun updatePreview() {
        val w = dragged
        if (w == null || w.body.y >= trayBottom) {
            dragValid = false
            return
        }
        val x = landingX(w)
        dragValid = x != null
        if (x != null) {
            dragPreviewX = x
            dragPreviewTop = restingY(w, x)
        }
    }

    /**
     * Abscisse de pose pour le poids déplacé, ou null si l'endroit est interdit.
     * On recadre pour que la brique tienne entièrement sur la planche, mais on
     * refuse la zone du pivot : c'est une règle du jeu, pas une maladresse.
     */
    private fun landingX(w: BalanceWeight): Float? {
        val x = w.body.x
        if (abs(x) < w.minDistance) return null
        val side = sign(x)
        return side * abs(x).coerceAtMost(w.maxDistance)
    }

    /** Relâche le poids en cours de déplacement. Retourne vrai s'il a été posé. */
    fun endDrag(): Boolean {
        val w = dragged ?: return false
        dragged = null
        w.dragging = false
        dragValid = false

        if (w.body.y >= trayBottom) {          // relâché dans le plateau
            sendToTray(w)
            return false
        }
        val x = landingX(w)
        if (x == null) {
            notice = Notice.DEAD_ZONE          // interdit de poser sur le pivot
            sendToTray(w)
            return false
        }
        place(w, x)
        return true
    }

    /** Pose le poids à l'abscisse demandée, empilé sur ce qui s'y trouve déjà. */
    private fun place(w: BalanceWeight, x: Float) {
        w.placed = true
        w.plankX = x
        placementOrder.remove(w)
        placementOrder.add(w)
        w.body.x = x
        w.body.angle = 0f
        w.body.vx = 0f; w.body.vy = 0f; w.body.omega = 0f
        w.body.y = restingY(w, x) + w.body.halfH
        w.body.inWorld = true
        world.forgetContacts(w.body)
        notice = Notice.NONE
    }

    /** Hauteur de la surface sur laquelle la brique va se poser (planche ou pile). */
    private fun restingY(w: BalanceWeight, x: Float): Float {
        var top = BalanceRules.PLANK_TOP + 0.004f
        for (other in weights) {
            if (other === w || !other.placed) continue
            val ohw = other.body.aabbHalfWidth()
            if (abs(other.body.x - x) < ohw + w.body.halfW - 0.01f) {
                top = maxOf(top, other.body.topY() + 0.004f)
            }
        }
        return top
    }

    // ── Test de l'équilibre ───────────────────────────────────────────────────

    val allPlaced: Boolean get() = weights.isNotEmpty() && weights.all { it.placed }

    fun canTest(): Boolean = phase == Phase.PLACING && allPlaced && dragged == null

    fun startTest() {
        if (!canTest()) return
        phase = Phase.TESTING
        notice = Notice.NONE
        testAttempts++
        testTime = 0f
        settleRefLean = 0f
        settleRefTime = 0f

        val totalMass = weights.sumOf { it.mass }.toFloat()
        stiffness = BalanceRules.stiffness(totalMass)

        // Inertie approchée du système {planche + poids} pour calibrer
        // l'amortissement. 0,7 fois l'amortissement critique : le levier oscille
        // une fois puis se stabilise, au lieu de ramper vers sa position.
        var ieff = plank.inertia
        for (w in weights) ieff += w.mass * w.body.x * w.body.x
        damping = 1.4f * sqrt(ieff * stiffness * world.gravity)

        plank.lockRotation = false
        plank.refreshMass()
    }

    // ── Simulation ────────────────────────────────────────────────────────────

    fun step(dt: Float) {
        when (phase) {
            Phase.PLACING -> {
                world.step(dt)
                // Une brique qui a glissé de la planche repart dans le plateau.
                for (w in weights) {
                    if (w.placed && w.body.y < BalanceRules.FALL_LIMIT) sendToTray(w)
                }
            }
            Phase.TESTING -> {
                applyLeverTorque()
                world.step(dt)
                testTime += dt
                evaluateTest()
            }
            // Après le verdict, le levier reste soumis à son rappel : sinon il
            // partirait au sol sous le regard du joueur, y compris après une
            // réussite où il doit justement rester horizontal.
            else -> {
                applyLeverTorque()
                world.step(dt)
            }
        }
    }

    /** Couple de rappel de la planche + amortissement (voir [BalanceRules.stiffness]). */
    private fun applyLeverTorque() {
        if (plank.lockRotation) return
        plank.torque = -stiffness * world.gravity * sin(plank.angle) - damping * plank.omega
    }

    private fun evaluateTest() {
        if (weights.any { it.body.y < BalanceRules.FALL_LIMIT }) {
            finish(false, Notice.FELL)
            return
        }
        if (abs(leanDeg) > 32f) {
            finish(false, Notice.TIPPED)
            return
        }
        // Le verdict n'est rendu que lorsque l'inclinaison a cessé d'évoluer :
        // le levier met un peu de temps à rejoindre sa position d'équilibre, et
        // juger trop tôt validerait des répartitions encore en train de pencher.
        if (abs(leanDeg - settleRefLean) > 0.05f || !weightsStill()) {
            settleRefLean = leanDeg
            settleRefTime = testTime
        }
        val stableFor = testTime - settleRefTime
        if ((testTime > 2f && stableFor > 0.6f) || testTime > 9f) {
            val ok = abs(leanDeg) <= difficulty.toleranceDeg
            finish(ok, if (ok) Notice.NONE else Notice.TOO_TILTED)
        }
    }

    private fun weightsStill(): Boolean {
        for (w in weights) {
            val b = w.body
            if (b.vx * b.vx + b.vy * b.vy > 0.0025f) return false
            if (abs(b.omega) > 0.12f) return false
        }
        return true
    }

    private fun finish(won: Boolean, why: Notice) {
        phase = if (won) Phase.WON else Phase.LOST
        notice = why
        verdictLeanDeg = leanDeg
        if (won) level++
    }

    // ── Indicateurs affichés ──────────────────────────────────────────────────

    /**
     * Nombre d'essais infructueux à décompter de la récompense. Pendant la pose
     * c'est le nombre de tests déjà lancés ; après une réussite, le test gagnant
     * ne compte évidemment pas.
     */
    val failedAttempts: Int
        get() = if (phase == Phase.WON) (testAttempts - 1).coerceAtLeast(0) else testAttempts

    /**
     * Angle physique de la planche en degrés, au sens trigonométrique : il est
     * *négatif* quand le côté droit descend (l'axe Y pointe vers le haut).
     */
    val tiltDeg: Float get() = Math.toDegrees(plank.angle.toDouble()).toFloat()

    /**
     * Inclinaison telle qu'on la voit, en degrés : positive quand la planche
     * penche à droite. C'est cette valeur que l'affichage utilise.
     */
    val leanDeg: Float get() = -tiltDeg

    /** Déséquilibre en kg·m : somme des masses multipliées par leur bras de levier. */
    val residualTorque: Float get() = weights.sumOf { it.torque.toDouble() }.toFloat()

    val leftTorque: Float
        get() = weights.filter { it.placed && it.body.x < 0f }.sumOf { (-it.torque).toDouble() }.toFloat()

    val rightTorque: Float
        get() = weights.filter { it.placed && it.body.x > 0f }.sumOf { it.torque.toDouble() }.toFloat()

    /**
     * Terme déstabilisant, en kg·m : les poids reposent *au-dessus* du pivot,
     * donc dès que la planche s'incline leur centre de gravité glisse du côté
     * bas et aggrave le déséquilibre. Il s'oppose à la raideur du rappel.
     */
    private val topHeaviness: Float
        get() = weights.filter { it.placed }
            .sumOf { (it.mass * (it.body.y - BalanceRules.PIVOT_HEIGHT)).toDouble() }
            .toFloat()

    /**
     * Inclinaison que prendra la planche au relâcher, prévue par le calcul.
     * Sert au réglage et aux tests ; elle n'est pas montrée au joueur, qui doit
     * juger à l'œil pendant la pose.
     */
    val predictedLeanDeg: Float
        get() {
            val total = weights.sumOf { it.mass }.toFloat()
            if (total <= 0f) return 0f
            val k = BalanceRules.stiffness(total)
            // Un chargement très haut peut en théorie annuler le rappel : on garde
            // un minimum pour que la prévision reste un nombre exploitable.
            val effective = (k - topHeaviness).coerceAtLeast(0.2f * k)
            return Math.toDegrees(atan((residualTorque / effective).toDouble())).toFloat()
        }
}
