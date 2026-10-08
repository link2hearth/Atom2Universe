package com.Atom2Universe.app.games.bigger

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Les règles d'Accrétion : de la poussière au trou noir.
 *
 * Tout est exprimé en **unités de monde**, pas en pixels : le bocal fait [WIDTH] de
 * large, et c'est la vue qui choisit l'échelle. Sa hauteur, elle, suit l'écran entre
 * [MIN_HEIGHT] et [MAX_HEIGHT] : un bocal figé laissait vide tout le haut d'une tablette.
 * Un écran allongé donne donc un bocal un peu plus profond, mais borné, pour que la
 * partie reste la même partie.
 */
object Accretion {
    const val WIDTH = 10f
    const val MIN_HEIGHT = 12f
    const val MAX_HEIGHT = 16f

    /**
     * Hauteur du centre de l'objet tenu, au-dessus du bord du bocal : juste assez pour que
     * le plus gros astre qui tombe (rayon 0,86) passe le bord sans le toucher.
     */
    const val HOLD_ABOVE = 1.0f

    /** Ce que la vue doit montrer au-dessus du bord : la zone de lâcher, et rien de plus. */
    const val DROP_ZONE = 1.95f

    const val TIER_COUNT = 12
    const val BLUE_GIANT = 10
    const val BLACK_HOLE = 11

    /**
     * Rayon de chaque objet. La croissance ralentit en haut de l'échelle, sans quoi la
     * géante bleue remplirait la moitié du bocal.
     *
     * **Le trou noir est plus petit que la géante bleue** : c'est l'effondrement. Deux
     * géantes qui fusionnent libèrent de la place au lieu d'en prendre, et c'est le seul
     * moment où le bocal respire.
     */
    val RADIUS = floatArrayOf(0.34f, 0.45f, 0.58f, 0.72f, 0.86f, 1.01f, 1.18f, 1.36f, 1.55f, 1.76f, 2.0f, 1.15f)

    /** Masse volumique : le trou noir est quatre fois plus dense, il coule sous les autres. */
    fun density(tier: Int) = if (tier == BLACK_HOLE) 4f else 1f

    /** Seuls les cinq plus petits objets tombent, et surtout les plus petits. */
    val SPAWN_WEIGHTS = intArrayOf(30, 26, 21, 14, 9)

    /** Points d'une fusion qui crée [tier] : 3, 6, 10, 15… comme les nombres triangulaires. */
    fun points(tier: Int) = (tier + 1) * (tier + 2) / 2

    const val ANNIHILATION_POINTS = 250

    /** Fenêtre pour qu'une fusion prolonge la chaîne en cours. */
    const val CHAIN_WINDOW = 1.0f
    const val MAX_CHAIN = 8

    /** Délai entre deux lâchers : on ne vide pas la file en tapotant. */
    const val DROP_COOLDOWN = 0.5f

    /**
     * Un objet qui vient d'être lâché dépasse forcément du bocal : il est ignoré pendant
     * ce temps-là.
     */
    const val OVERFLOW_GRACE = 1.2f

    /** Temps passé au-dessus du bord avant la fin de partie. */
    const val OVERFLOW_LIMIT = 2.0f

    /** Distance en plus des deux rayons à laquelle deux jumeaux fusionnent. */
    const val MERGE_EPS = 0.03f

    const val GRAVITY = 30f
}

class Orb(val tier: Int, val body: PhysBody) {
    var age = 0f
    var overflow = 0f
    var consumed = false
    val radius get() = Accretion.RADIUS[tier]
}

/** Ce qui vient de se passer, pour les effets et les sons. La vue vide la liste à chaque image. */
class BiggerEvent(
    val kind: Kind,
    val x: Float,
    val y: Float,
    val tier: Int,
    val points: Int = 0,
    val chain: Int = 0,
    val discovered: Boolean = false
) {
    enum class Kind { DROP, MERGE, COLLAPSE, ANNIHILATION, GAME_OVER }
}

class BiggerGame(private val random: Random = Random.Default) {

    private companion object {
        const val MAX_TRAVEL = 0.06f
    }

    val world = PhysWorld().apply {
        gravity = Accretion.GRAVITY
        linearDamping = 0.05f
        angularDamping = 0.4f
        // Un bocal plein, c'est quarante astres empilés sur dix étages : la pression du tas
        // enfonçait ceux du bord dans la paroi. Le monde est petit, on peut payer plus de
        // passes au solveur.
        iterations = 24
        positionIterations = 12
    }
    val orbs = ArrayList<Orb>()
    val events = ArrayList<BiggerEvent>()
    private val pairs = ArrayList<Orb>()

    var current = 0; private set
    var next = 0; private set
    var score = 0; private set
    var drops = 0; private set
    var merges = 0; private set
    /** Le plus gros objet de la partie, -1 avant la première fusion. */
    var bestTier = -1; private set
    var over = false; private set
    var cooldown = 0f; private set
    var chain = 0; private set
    private var chainTimer = 0f

    /** Entre 0 et 1 : où en est l'objet le plus proche de faire déborder le bocal. */
    var danger = 0f; private set

    val canDrop get() = !over && cooldown <= 0f

    /** Hauteur du bocal, choisie par la vue selon la place à l'écran. */
    var jarHeight = Accretion.MIN_HEIGHT
        set(value) { field = value.coerceIn(Accretion.MIN_HEIGHT, Accretion.MAX_HEIGHT) }

    val holdY get() = jarHeight + Accretion.HOLD_ABOVE

    init {
        buildJar()
        reset()
    }

    private fun buildJar() {
        val w = Accretion.WIDTH
        val h = Accretion.MAX_HEIGHT
        fun wall(halfW: Float, halfH: Float, x: Float, y: Float) = PhysBody(halfW, halfH, 0f).apply {
            this.x = x; this.y = y
            friction = 0.5f
            world.add(this)
        }
        wall(w / 2f + 1f, 0.5f, w / 2f, -0.5f)
        // Les parois montent bien au-dessus du bord : un objet lâché ne doit jamais
        // pouvoir passer par-dessus.
        wall(0.5f, h * 2f, -0.5f, h)
        wall(0.5f, h * 2f, w + 0.5f, h)
    }

    fun reset() {
        for (o in orbs) world.remove(o.body)
        orbs.clear(); events.clear()
        score = 0; drops = 0; merges = 0; bestTier = -1
        over = false; cooldown = 0f; chain = 0; chainTimer = 0f; danger = 0f
        current = roll(); next = roll()
    }

    private fun roll(): Int {
        val total = Accretion.SPAWN_WEIGHTS.sum()
        var pick = random.nextInt(total)
        for (i in Accretion.SPAWN_WEIGHTS.indices) {
            pick -= Accretion.SPAWN_WEIGHTS[i]
            if (pick < 0) return i
        }
        return 0
    }

    private fun spawn(tier: Int, x: Float, y: Float): Orb {
        val r = Accretion.RADIUS[tier]
        val body = PhysBody.circle(r, (PI * r * r).toFloat() * Accretion.density(tier)).apply {
            this.x = x; this.y = y
            friction = 0.6f
            restitution = 0.08f
        }
        val orb = Orb(tier, body)
        body.tag = orb
        world.add(body)
        orbs.add(orb)
        return orb
    }

    /** Lâche l'objet tenu à l'abscisse [x] (en unités de monde). */
    fun drop(x: Float): Boolean {
        if (!canDrop) return false
        val r = Accretion.RADIUS[current]
        val cx = x.coerceIn(r, Accretion.WIDTH - r)
        spawn(current, cx, holdY).body.vy = -2f
        events.add(BiggerEvent(BiggerEvent.Kind.DROP, cx, holdY, current))
        current = next; next = roll()
        cooldown = Accretion.DROP_COOLDOWN
        drops++
        return true
    }

    fun step(dt: Float) {
        if (over) return
        val h = dt.coerceIn(0f, 1f / 30f)
        cooldown = (cooldown - h).coerceAtLeast(0f)
        chainTimer -= h
        if (chainTimer <= 0f) chain = 0
        // Le moteur garantit qu'un corps ne traverse rien, pas qu'il ne s'enfonce pas :
        // une géante qui tombe du haut du bocal arrive à vingt-cinq unités par seconde et
        // s'enfonçait d'un cinquième d'unité dans le fond, le temps d'une image. On
        // découpe donc l'image pour qu'aucun astre ne parcoure plus de [MAX_TRAVEL] par pas.
        var fastest = 0f
        for (o in orbs) fastest = maxOf(fastest, o.body.vx * o.body.vx + o.body.vy * o.body.vy)
        val pieces = ceil(sqrt(fastest) * h / MAX_TRAVEL).toInt().coerceIn(1, 8)
        repeat(pieces) { world.stepFrame(h / pieces) }
        findMerges()
        checkOverflow(h)
    }

    /**
     * Cherche les jumeaux qui se touchent. Une comparaison de distances plutôt que les
     * événements de contact du moteur : un contact n'est annoncé qu'à sa naissance, et un
     * couple manqué une fois resterait collé pour toujours. Soixante objets au plus, soit
     * deux mille paires : une misère.
     */
    private fun findMerges() {
        pairs.clear()
        val n = orbs.size
        for (i in 0 until n) {
            val a = orbs[i]
            if (a.consumed) continue
            for (j in i + 1 until n) {
                val b = orbs[j]
                if (b.consumed || a.tier != b.tier) continue
                val dx = b.body.x - a.body.x
                val dy = b.body.y - a.body.y
                val reach = a.radius + b.radius + Accretion.MERGE_EPS
                if (dx * dx + dy * dy <= reach * reach) {
                    a.consumed = true; b.consumed = true
                    pairs.add(a); pairs.add(b)
                    break
                }
            }
        }
        // Les fusions s'appliquent après la recherche : elles ajoutent et retirent des
        // objets, ce qu'on ne fait pas au milieu d'un parcours.
        for (k in 0 until pairs.size step 2) merge(pairs[k], pairs[k + 1])
        pairs.clear()
    }

    private fun extendChain(): Int {
        chain = if (chainTimer > 0f) (chain + 1).coerceAtMost(Accretion.MAX_CHAIN) else 1
        chainTimer = Accretion.CHAIN_WINDOW
        return chain
    }

    private fun merge(a: Orb, b: Orb) {
        val ba = a.body; val bb = b.body
        val m = ba.mass + bb.mass
        val x = (ba.x * ba.mass + bb.x * bb.mass) / m
        val y = (ba.y * ba.mass + bb.y * bb.mass) / m
        val vx = (ba.vx * ba.mass + bb.vx * bb.mass) / m
        val vy = (ba.vy * ba.mass + bb.vy * bb.mass) / m
        val omega = (ba.omega + bb.omega) / 2f
        world.remove(ba); world.remove(bb)
        orbs.remove(a); orbs.remove(b)
        merges++
        val mult = extendChain()

        if (a.tier == Accretion.BLACK_HOLE) {
            annihilate(x, y, mult)
            return
        }
        val tier = a.tier + 1
        val r = Accretion.RADIUS[tier]
        // Le nouvel objet naît entre les deux anciens. Il chevauche ses voisins, et le
        // moteur l'en sort doucement : sa correction de position n'ajoute jamais
        // d'énergie, donc pas d'explosion quand un gros objet apparaît dans un tas.
        val orb = spawn(tier, x.coerceIn(r, Accretion.WIDTH - r), y.coerceAtLeast(r))
        orb.body.vx = vx; orb.body.vy = vy; orb.body.omega = omega
        orb.age = Accretion.OVERFLOW_GRACE // déjà dans le tas : pas de sursis
        val points = Accretion.points(tier) * mult
        score += points
        val discovered = tier > bestTier
        if (discovered) bestTier = tier
        val kind = if (tier == Accretion.BLACK_HOLE) BiggerEvent.Kind.COLLAPSE else BiggerEvent.Kind.MERGE
        events.add(BiggerEvent(kind, orb.body.x, orb.body.y, tier, points, mult, discovered))
    }

    /**
     * Deux trous noirs s'annihilent : ils disparaissent, avalent la poussière proche et
     * repoussent le reste du tas.
     */
    private fun annihilate(x: Float, y: Float, mult: Int) {
        var points = Accretion.ANNIHILATION_POINTS
        val swallowRadius = 2.5f
        val blastRadius = 5.5f
        val it = orbs.iterator()
        while (it.hasNext()) {
            val o = it.next()
            val dx = o.body.x - x
            val dy = o.body.y - y
            val d = sqrt(dx * dx + dy * dy)
            if (o.tier <= 2 && d < swallowRadius) {
                world.remove(o.body)
                it.remove()
                points += Accretion.points(o.tier)
            } else if (d < blastRadius && d > 1e-3f) {
                val push = o.body.mass * 7f * (1f - d / blastRadius)
                o.body.applyImpulse(dx / d * push, dy / d * push)
            }
        }
        points *= mult
        score += points
        events.add(BiggerEvent(BiggerEvent.Kind.ANNIHILATION, x, y, Accretion.BLACK_HOLE, points, mult))
    }

    private fun checkOverflow(dt: Float) {
        var worst = 0f
        for (o in orbs) {
            o.age += dt
            if (o.age >= Accretion.OVERFLOW_GRACE && o.body.y + o.radius > jarHeight) {
                o.overflow += dt
            } else {
                o.overflow = (o.overflow - dt * 2f).coerceAtLeast(0f)
            }
            if (o.overflow > worst) worst = o.overflow
        }
        danger = (worst / Accretion.OVERFLOW_LIMIT).coerceIn(0f, 1f)
        if (worst >= Accretion.OVERFLOW_LIMIT) {
            over = true
            events.add(BiggerEvent(BiggerEvent.Kind.GAME_OVER, Accretion.WIDTH / 2f, jarHeight, bestTier))
        }
    }

    // ── Sauvegarde d'une partie en cours ────────────────────────────────────────
    // Une partie d'Accrétion dure longtemps : un appel ou un passage dans une autre
    // application ne doit pas la perdre. On garde l'essentiel du mouvement ; les
    // impulsions mémorisées du moteur se reconstruisent en une image.

    fun saveState(): String? {
        if (over || drops == 0) return null
        val sb = StringBuilder()
        sb.append("1|").append(score).append('|').append(drops).append('|').append(merges)
            .append('|').append(bestTier).append('|').append(current).append('|').append(next).append('|')
        orbs.forEachIndexed { i, o ->
            if (i > 0) sb.append(';')
            val b = o.body
            sb.append(o.tier).append(',').append(b.x).append(',').append(b.y).append(',').append(b.angle)
                .append(',').append(b.vx).append(',').append(b.vy).append(',').append(b.omega)
        }
        return sb.toString()
    }

    fun restoreState(state: String): Boolean {
        val parts = state.split('|')
        if (parts.size != 8 || parts[0] != "1") return false
        return try {
            reset()
            score = parts[1].toInt(); drops = parts[2].toInt(); merges = parts[3].toInt()
            bestTier = parts[4].toInt()
            current = parts[5].toInt().coerceIn(0, Accretion.SPAWN_WEIGHTS.size - 1)
            next = parts[6].toInt().coerceIn(0, Accretion.SPAWN_WEIGHTS.size - 1)
            if (parts[7].isNotEmpty()) for (entry in parts[7].split(';')) {
                val f = entry.split(',')
                val tier = f[0].toInt()
                if (tier !in 0 until Accretion.TIER_COUNT) continue
                val o = spawn(tier, f[1].toFloat(), f[2].toFloat())
                o.body.angle = f[3].toFloat()
                o.body.vx = f[4].toFloat(); o.body.vy = f[5].toFloat(); o.body.omega = f[6].toFloat()
                o.age = Accretion.OVERFLOW_GRACE
            }
            true
        } catch (_: RuntimeException) {
            reset()
            false
        }
    }
}
