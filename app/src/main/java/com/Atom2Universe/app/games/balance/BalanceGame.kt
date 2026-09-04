package com.Atom2Universe.app.games.balance

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
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
    const val PLANK_HALF_LENGTH = 1.85f
    const val PLANK_HALF_THICKNESS = 0.05f
    const val PLANK_MASS = 9f

    /** Hauteur du pivot : c'est aussi le centre de rotation de la planche. */
    const val PIVOT_HEIGHT = 0.85f

    /**
     * Demi-largeur de la zone interdite au centre : aucune brique ne doit
     * mordre sur le pivot, même d'un coin.
     */
    const val DEAD_HALF = 0.12f

    /** Fraction minimale d'une pièce qui doit rester au-dessus de la planche. */
    const val MIN_SUPPORT_FRACTION = 0.25f

    /**
     * Pas de la règle gravée sur la planche. Purement visuel : rien ne s'y
     * aimante, le joueur pose où il veut, mais ça donne un repère pour calculer.
     */
    const val RULER_UNIT = PLANK_HALF_LENGTH / 6f

    /** Face supérieure de la planche quand elle est horizontale. */
    const val PLANK_TOP = PIVOT_HEIGHT + PLANK_HALF_THICKNESS

    /** Un poids lâché plus bas que ça est considéré comme tombé. */
    const val FALL_LIMIT = PIVOT_HEIGHT - 0.15f

    /** Aire en m² : c'est volontairement la seule échelle de taille du jeu. */
    const val AREA_PER_KG = 0.0032f
    fun areaForMass(mass: Int): Float = mass * AREA_PER_KG

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
    val shape: Shape,
    val body: PhysBody
) {
    enum class Shape { RECTANGLE, TRIANGLE, TETROMINO_L, TETROMINO_T, TETROMINO_Z }

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

    /** Orientation choisie dans le carrousel et conservée au moment de la pose. */
    var restAngle = 0f

    init {
        body.tag = this
        body.inWorld = false
        body.friction = 0.62f
    }

    /** Dimensions de l'encombrement de la pièce à plat. */
    val halfWidth: Float get() = body.aabbHalfWidth()
    val halfHeight: Float get() = body.parts.maxOf { part ->
        kotlin.math.abs(part.localY) + if (part.shape == com.Atom2Universe.app.games.physics.Shape.CIRCLE) part.radius else part.halfH
    }
    val baseOffset: Float get() = body.parts.minOf { part ->
        part.localY - if (part.shape == com.Atom2Universe.app.games.physics.Shape.CIRCLE) part.radius else part.halfH
    }

    /** Partie la plus basse de la forme dans son orientation actuelle. */
    val bottomOffset: Float
        get() {
            val c = kotlin.math.cos(body.angle)
            val s = kotlin.math.sin(body.angle)
            return body.parts.minOf { part ->
                val center = part.localX * s + part.localY * c
                if (part.shape == com.Atom2Universe.app.games.physics.Shape.TRIANGLE) {
                    val a = body.angle + part.localAngle
                    val sa = kotlin.math.sin(a)
                    val ca = kotlin.math.cos(a)
                    minOf(
                        center - part.halfW * sa - part.halfH * ca,
                        center + part.halfW * sa - part.halfH * ca,
                        center + part.halfH * ca
                    )
                } else {
                    val extent = part.halfW * kotlin.math.abs(s) + part.halfH * kotlin.math.abs(c)
                    center - extent
                }
            }
        }

    /** Distance minimale au pivot : la brique ne doit pas mordre sur la zone interdite. */
    val minDistance: Float get() = BalanceRules.DEAD_HALF + halfWidth

    /**
     * Distance maximale : une pièce peut dépasser du bord, mais au moins un
     * quart de sa largeur reste porté par la planche. Le test physique décide
     * ensuite si l'empilement tient réellement.
     */
    val maxDistance: Float get() = BalanceRules.PLANK_HALF_LENGTH + halfWidth * (1f - 2f * BalanceRules.MIN_SUPPORT_FRACTION)

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
     * et plage de masses. Les deux derniers modes sont volontairement des
     * puzzles de construction : beaucoup de pièces, des masses très écartées
     * et des formes qui obligent aussi à penser à la stabilité des piles.
     */
    enum class Difficulty(
        val weightCount: Int,
        val toleranceDeg: Float,
        val minMass: Int,
        val maxMass: Int
    ) {
        EASY(3, 8f, 1, 12),
        MEDIUM(5, 4.5f, 3, 24),
        HARD(8, 3f, 6, 42),
        EXPERT(11, 2f, 10, 65),
        EXTREME(14, 1.35f, 15, 50)
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

    /** Décalage du carrousel de pièces ; il reboucle à chaque tour complet. */
    private var trayScroll = 0f

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
        // En paysage la scène est basse : imposer quatre unités repoussait le
        // carrousel hors de la surface réelle. On garde seulement le strict
        // minimum pour le pivot ; le carrousel reste donc toujours visible.
        this.worldHeight = worldHeight.coerceAtLeast(1.25f)
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
            val w = createWeight(i, m, diff)
            weights.add(w)
            world.add(w.body)
        }
        layoutTray()
    }

    /**
     * Chaque géométrie reçoit exactement [BalanceRules.areaForMass] : un 99 kg
     * a donc 99 fois l'aire d'un 1 kg de même forme. Les tétriminos sont de
     * vrais corps composés et le triangle est un vrai polygone à trois faces.
     */
    private fun createWeight(index: Int, mass: Int, diff: Difficulty): BalanceWeight {
        val shape = when (diff) {
            Difficulty.EASY -> BalanceWeight.Shape.RECTANGLE
            Difficulty.MEDIUM -> if (index % 4 == 0) BalanceWeight.Shape.TRIANGLE else BalanceWeight.Shape.RECTANGLE
            Difficulty.HARD -> BalanceWeight.Shape.entries[index % 3]
            Difficulty.EXPERT, Difficulty.EXTREME -> BalanceWeight.Shape.entries[rng.nextInt(BalanceWeight.Shape.entries.size)]
        }
        val area = BalanceRules.areaForMass(mass)
        val body = when (shape) {
            BalanceWeight.Shape.RECTANGLE -> {
                val aspect = 0.55f + rng.nextFloat() * 1.15f
                PhysBody(sqrt(area * aspect) / 2f, sqrt(area / aspect) / 2f, mass.toFloat())
            }
            BalanceWeight.Shape.TRIANGLE -> PhysBody.compound(mass.toFloat()) {
                val half = sqrt(area / 2f)
                triangle(half, half)
            }
            BalanceWeight.Shape.TETROMINO_L -> PhysBody.compound(mass.toFloat()) {
                val u = sqrt(area / 4f)
                box(u / 2f, u / 2f, -u / 2f, -u / 2f)
                box(u / 2f, u / 2f, -u / 2f, u / 2f)
                box(u / 2f, u / 2f, -u / 2f, u * 1.5f)
                box(u / 2f, u / 2f, u / 2f, -u / 2f)
            }
            BalanceWeight.Shape.TETROMINO_T -> PhysBody.compound(mass.toFloat()) {
                val u = sqrt(area / 4f)
                box(u / 2f, u / 2f, -u, u / 2f)
                box(u / 2f, u / 2f, 0f, u / 2f)
                box(u / 2f, u / 2f, u, u / 2f)
                box(u / 2f, u / 2f, 0f, -u / 2f)
            }
            BalanceWeight.Shape.TETROMINO_Z -> PhysBody.compound(mass.toFloat()) {
                val u = sqrt(area / 4f)
                box(u / 2f, u / 2f, -u / 2f, u / 2f)
                box(u / 2f, u / 2f, u / 2f, u / 2f)
                box(u / 2f, u / 2f, u / 2f, -u / 2f)
                box(u / 2f, u / 2f, u * 1.5f, -u / 2f)
            }
        }
        return BalanceWeight(index, mass, shape, body)
    }

    /** Repart du même niveau : tous les poids retournent dans le plateau. */
    fun resetPlacement() {
        resetRun()
        for (w in weights) sendToTray(w)
        layoutTray()
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
        for (w in kept) {
            w.placed = false
            w.body.angle = w.restAngle
        }
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
                val m = rng.nextInt(diff.minMass, diff.maxMass + 1)
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
        for (i in 0 until n) {
            val halfW = sqrt(BalanceRules.areaForMass(masses[i]))
            lo[i] = masses[i] * (BalanceRules.DEAD_HALF + halfW)
            hi[i] = masses[i] * (BalanceRules.PLANK_HALF_LENGTH - halfW)
        }

        // Les niveaux avancés sont faits pour empiler : on ne les refuse donc
        // plus parce qu'une solution exigerait plusieurs étages.
        val margin = 0.25f
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

    /**
     * Range les pièces dans un seul carrousel horizontal. Contrairement aux
     * anciennes rangées, le nombre de pièces ne change jamais la hauteur de
     * jeu : un glissement latéral fait défiler la bande et reboucle à l'infini.
     */
    private fun layoutTray() {
        val trayWeights = weights.filter { !it.placed && !it.dragging }
        if (trayWeights.isEmpty()) return
        val gap = 0.08f

        val total = trayWeights.sumOf { (it.halfWidth * 2f + gap).toDouble() }.toFloat()
        if (total > 0f) trayScroll = ((trayScroll % total) + total) % total
        val maxHeight = trayWeights.maxOf { it.halfHeight * 2f }
        val top = worldHeight - 0.14f
        var cursor = -total / 2f
        for (w in trayWeights) {
            val width = w.halfWidth * 2f
            cursor += width / 2f
            // Le même cycle est répété de part et d'autre de l'écran.
            w.trayX = (((cursor + trayScroll + total / 2f) % total) + total) % total - total / 2f
            w.trayY = top - maxHeight / 2f
            cursor += width / 2f + gap
        }
        trayBottom = top - maxHeight - 0.16f

        for (w in trayWeights) parkInTray(w)
    }

    /** Fait défiler le carrousel, sans modifier les pièces déjà posées. */
    fun scrollTray(delta: Float) {
        if (phase != Phase.PLACING || dragged != null || weights.isEmpty()) return
        trayScroll += delta
        layoutTray()
    }

    /** Abandonne une traction devenue un geste de défilement. */
    fun cancelDragForScroll() {
        val w = dragged ?: return
        dragged = null
        w.dragging = false
        sendToTray(w)
    }

    /** Tourne une pièce dans le carrousel. Les triangles ont trois orientations. */
    fun rotateInTray(w: BalanceWeight) {
        if (phase != Phase.PLACING || w.placed || w.dragging) return
        val step = if (w.shape == BalanceWeight.Shape.TRIANGLE) {
            (2f * Math.PI / 3f).toFloat()
        } else {
            (Math.PI / 2.0).toFloat()
        }
        w.restAngle = (w.restAngle + step) % (2f * Math.PI.toFloat())
        w.body.angle = w.restAngle
        layoutTray()
    }

    private fun parkInTray(w: BalanceWeight) {
        w.body.x = w.trayX
        w.body.y = w.trayY
        w.body.angle = w.restAngle
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
            if (!contains(w, wx, wy)) continue
            if (best == null || w.body.y > best.body.y) best = w
        }
        return best
    }

    private fun contains(w: BalanceWeight, wx: Float, wy: Float): Boolean {
        val b = w.body
        val dx = wx - b.x
        val dy = wy - b.y
        val c = kotlin.math.cos(-b.angle)
        val s = kotlin.math.sin(-b.angle)
        val lx = dx * c - dy * s
        val ly = dx * s + dy * c
        return abs(lx) <= w.halfWidth + 0.03f && abs(ly) <= w.halfHeight + 0.03f
    }

    fun beginDrag(w: BalanceWeight, wx: Float, wy: Float) {
        if (phase != Phase.PLACING) return
        dragged = w
        w.dragging = true
        w.placed = false
        w.body.inWorld = false     // fantôme : ne bouscule pas les autres briques
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
        w.body.x = (wx + grabDx).coerceIn(-half + w.halfWidth, half - w.halfWidth)
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
            layoutTray()
            return false
        }
        val x = landingX(w)
        if (x == null) {
            notice = Notice.DEAD_ZONE          // interdit de poser sur le pivot
            sendToTray(w)
            layoutTray()
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
        w.body.angle = w.restAngle
        w.body.vx = 0f; w.body.vy = 0f; w.body.omega = 0f
        w.body.y = restingY(w, x) - w.bottomOffset
        // Le solveur générique des polygones produit un unique contact pour
        // une base triangulaire. On conserve donc l'orientation décidée par le
        // joueur : le triangle peut tomber ou glisser, mais ne pivote pas tout
        // seul sur ce point de contact artificiel.
        w.body.lockRotation = w.shape == BalanceWeight.Shape.TRIANGLE
        w.body.refreshMass()
        w.body.inWorld = true
        world.forgetContacts(w.body)
        notice = Notice.NONE
        // Une pièce sort du carrousel : les restantes se rapprochent aussitôt.
        layoutTray()
    }

    /** Hauteur de la surface sur laquelle la brique va se poser (planche ou pile). */
    private fun restingY(w: BalanceWeight, x: Float): Float {
        var top = BalanceRules.PLANK_TOP + 0.004f
        for (other in weights) {
            if (other === w || !other.placed) continue
            val ohw = other.body.aabbHalfWidth()
            if (abs(other.body.x - x) < ohw + w.halfWidth - 0.01f) {
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
                var returnedToTray = false
                for (w in weights) {
                    if (w.placed && w.body.y < BalanceRules.FALL_LIMIT) {
                        sendToTray(w)
                        returnedToTray = true
                    }
                }
                // La pièce réintègre tout de suite le cycle : sans ce recalcul,
                // son ancienne place vide ne se réapparaissait qu'après un swipe.
                if (returnedToTray) layoutTray()
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
