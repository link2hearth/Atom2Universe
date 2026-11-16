package com.Atom2Universe.app.games.enamel

import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Joueur automatique de l'accueil. Le but du jeu : toucher un palet de même couleur pour fusionner,
 * sans laisser le tas descendre vers la base. Chaque coup candidat est joué sur une copie de la planche.
 * La réflexion se découpe en tranches de temps pour ne pas figer l'écran.
 */
class EnamelBot(private val random: Random = Random.Default) {
    class Plan(val x: Float, val y: Float, val dx: Float, val dy: Float, val power: Float) {
        var value = Float.NEGATIVE_INFINITY
    }

    private val sim = EnamelGame(Random(7))
    private val plans = ArrayList<Plan>()
    private var evaluated = 0
    private var snapshot: String? = null
    var best: Plan? = null; private set

    fun clear() { plans.clear(); evaluated = 0; best = null; snapshot = null }

    /** Avance la réflexion pendant au plus [budgetNanos] ; vrai quand le meilleur coup est connu. */
    fun think(game: EnamelGame, budgetNanos: Long): Boolean {
        if (plans.isEmpty()) prepare(game)
        val started = System.nanoTime()
        while (evaluated < plans.size && System.nanoTime() - started < budgetNanos) {
            val plan = plans[evaluated++]
            plan.value = evaluate(plan)
            if (plan.value > (best?.value ?: Float.NEGATIVE_INFINITY)) best = plan
        }
        return evaluated >= plans.size && best != null
    }

    /** Puissance qui donne au moins [speed] dans cette direction, ou null si hors de portée. */
    private fun powerFor(game: EnamelGame, y: Float, dx: Float, dy: Float, speed: Float): Float? {
        if ((game.shotSpeed(y, dx, dy, 1f) ?: return null) < speed) return null
        var lo = .03f; var hi = 1f
        repeat(18) {
            val mid = (lo + hi) / 2
            if ((game.shotSpeed(y, dx, dy, mid) ?: 0f) >= speed) hi = mid else lo = mid
        }
        return hi
    }

    /**
     * Tirs droits sur les palets de la couleur à jouer, depuis plusieurs places ; tirs par la bande vers eux ;
     * sinon des tirs qui rangent le palet en haut de la planche, loin de la base.
     */
    private fun prepare(game: EnamelGame) {
        snapshot = game.saveState()
        val r = EnamelRules.RADII[game.current]
        fun add(x: Float, y: Float, dx: Float, dy: Float, power: Float) {
            if (game.isLaunchClear(x, y) && game.shotSpeed(y, dx, dy, power) != null) plans.add(Plan(x, y, dx, dy, power))
        }
        // Depuis le haut de la base : plus près des cibles, et le doigt a toute la base pour tirer.
        val y = game.baseTop + r + .05f
        val spots = (0..5).map { r + it / 5f * (game.width - 2 * r) }
        // Les plus proches de la base d'abord : ce sont les plus faciles à toucher.
        for (target in game.pucks.filter { it.tier == game.current }.sortedByDescending { it.y }.take(4)) {
            for (x in spots + target.x.coerceIn(r, game.width - r)) {
                val dx = target.x - x; val dy = target.y - y
                val reach = hypot(dx, dy) - target.radius - r
                // Juste assez pour arriver au contact, ou un peu plus pour pousser.
                for (extra in floatArrayOf(.4f, 1.6f))
                    powerFor(game, y, dx, dy, sqrt(2 * EnamelRules.DRAG * (reach + extra)))?.let { add(x, y, dx, dy, it) }
                // Par la bande : viser le reflet du palet derrière un bord.
                for (mirror in floatArrayOf(-target.x, 20f - target.x)) {
                    val mx = mirror - x
                    powerFor(game, y, mx, dy, sqrt(2 * EnamelRules.DRAG * (hypot(mx, dy) / .72f + .6f)))?.let { add(x, y, mx, dy, it) }
                }
            }
        }
        for (x in spots) for (power in floatArrayOf(.65f, 1f)) {
            add(x, y, 0f, -1f, power); add(x, y, .25f, -1f, power); add(x, y, -.25f, -1f, power)
        }
        if (plans.isEmpty()) plans.add(Plan(game.width / 2, game.launchY, 0f, -1f, .6f))
    }

    /** Chaque fusion compte beaucoup (plus pour les gros rangs) ; un palet près de la base coûte. */
    private fun evaluate(plan: Plan): Float {
        val saved = snapshot ?: return random.nextFloat()
        if (!sim.restoreState(saved)) return Float.NEGATIVE_INFINITY
        settle(); sim.events.clear()
        val before = sim.score
        if (!sim.launch(plan.x, plan.y, plan.dx, plan.dy, plan.power)) return Float.NEGATIVE_INFINITY
        settle()
        if (sim.over) return -1_000_000f
        var value = (sim.score - before) * 4f
        for (event in sim.events) value += 60f + event.tier * 25f
        sim.events.clear()
        // Seul compte ce qui s'approche vraiment de la limite ; le reste est rangé.
        val limit = sim.baseTop - 1.2f
        for (puck in sim.pucks) value -= maxOf(0f, puck.y + puck.radius - limit) * 30f
        value -= sim.danger * 400f
        // À égalité, ranger haut : le tas reste loin de la base.
        value -= (sim.pucks.maxOfOrNull { it.y + it.radius } ?: 0f) * .5f
        return value + random.nextFloat() * .5f
    }

    private fun settle() {
        var steps = 0
        while (sim.moving && steps < 900) { sim.step(1f / 120); steps++ }
    }
}
