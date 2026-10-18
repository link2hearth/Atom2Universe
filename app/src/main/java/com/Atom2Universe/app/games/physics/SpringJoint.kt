package com.Atom2Universe.app.games.physics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Un ressort-amortisseur entre deux points : une raideur, un frottement visqueux, et
 * une longueur au repos.
 *
 * Là où [DistanceJoint] **impose** une distance, celui-ci ne fait que la **préférer** :
 * il pousse d'autant plus fort qu'on s'en écarte, et il finit toujours par céder. C'est
 * la pièce d'une machine infernale qui range de l'énergie et la rend plus tard — un
 * tremplin, une détente armée, le rappel d'un bouton qui remonte tout seul.
 *
 * ## Pourquoi ce n'est pas « une force appliquée à chaque image »
 *
 * La façon naïve d'écrire un ressort est d'appliquer `F = -k·x` sur les deux corps à
 * chaque pas. Elle marche, et elle explose : le pas de temps étant fini, le ressort
 * répond toujours à l'écart d'**avant**, si bien qu'il en rend un peu plus qu'il n'en
 * a reçu à chaque aller-retour. Un ressort mou ne le montre pas, un ressort raide
 * envoie la pièce dans le décor au bout de trois oscillations — et « raide » commence
 * bien plus tôt qu'on ne croit, dès que `k·dt²` approche la masse.
 *
 * Celui-ci est donc une **contrainte molle** résolue avec les autres, dans le même
 * solveur : on ne lui demande pas la force qu'il exerce, on lui demande d'amener la
 * vitesse d'écartement là où elle doit être à la **fin** du pas. La différence est
 * celle entre un calcul explicite et un calcul implicite, et elle vaut une garantie :
 * quelle que soit la raideur, quel que soit le pas, ce ressort ne diverge pas. Un
 * ressort de 10 MN/m dans un pas de 1/60 s reste sage.
 *
 * ## Ce qu'il fait à l'énergie, et ce qu'il en perd
 *
 * Il en range et il en rend — c'est son métier, et le bilan ne se lit donc pas sur un
 * corps mais sur l'ensemble « corps + ressort ». Ce qu'il ne **crée** jamais, c'est la
 * garantie, et c'est celle que vérifie le banc d'essai : quoi qu'on lui fasse, le total
 * ne repasse jamais au-dessus de ce qu'on a mis au départ.
 *
 * En revanche **il en perd, et il faut le savoir avant de s'en servir**. Le prix de la
 * stabilité inconditionnelle, c'est un calcul implicite, et un calcul implicite est
 * toujours un peu dissipatif. Mesuré sur un ressort de 100 N/m portant un kilo, sans
 * amortissement, à 120 Hz :
 *
 * | après | il reste |
 * |---|---|
 * | un quart de période (0,16 s) | 88 % |
 * | une seconde | 47 % |
 * | dix secondes | 0,03 % |
 *
 * Ce que ça veut dire en pratique tient en une phrase : **c'est un excellent lanceur et
 * un mauvais pendule.** Une détente qu'on arme et qu'on relâche travaille sur un quart
 * de période et rend l'essentiel de ce qu'on lui a confié ; un tremplin qui devrait
 * osciller une minute s'éteindra bien avant.
 *
 * La perte sur une détente vaut à peu près `(π/2)·ω·h`, avec `ω = √(k/m)` et `h` la
 * durée du sous-pas. Elle se réduit donc en assouplissant le ressort, en l'alourdissant,
 * ou en raccourcissant le pas — et le moteur raccourcit déjà le sien tout seul quand la
 * machine s'anime, ce qui tombe bien : c'est exactement le moment de la détente.
 *
 * ## Il ne replace rien
 *
 * [applyPositionImpulse] ne fait rien, volontairement. La passe de position sert à
 * rattraper ce qui s'est **enfoncé à tort** ; or un ressort comprimé n'est pas une
 * erreur à corriger, c'est l'état normal d'un ressort qui travaille. Le replacer
 * reviendrait à le raidir infiniment.
 */
class SpringJoint(a: PhysBody, b: PhysBody) : Joint(a, b) {

    /** Ancrage sur [a], dans son repère. */
    var localAnchorAX = 0f
    var localAnchorAY = 0f

    /** Ancrage sur [b], dans son repère. */
    var localAnchorBX = 0f
    var localAnchorBY = 0f

    /** Longueur au repos, en mètres : celle où le ressort ne pousse ni ne tire. */
    var restLength = 1f

    /**
     * Raideur, en newtons par mètre. Zéro fait un amortisseur pur.
     *
     * Aucun plafond : c'est tout l'intérêt d'une contrainte molle plutôt que d'une
     * force appliquée à la main.
     */
    var stiffness = 1_000f

    /**
     * Amortissement visqueux, en newtons par mètre par seconde. Zéro fait un ressort
     * parfait, qui oscille sans jamais s'arrêter.
     *
     * L'ordre de grandeur qui « ne rebondit qu'une fois » est `2·√(k·m)`, avec m la
     * masse portée : en dessous ça oscille, au-dessus ça revient mollement.
     */
    var damping = 0f

    /**
     * Précharge, en newtons : l'effort que le ressort exerce déjà à sa longueur au
     * repos. Positif, il **pousse** les deux ancrages l'un loin de l'autre.
     *
     * Ce n'est pas une pièce de plus, c'est une commodité : un ressort préchargé est
     * exactement un ressort dont la longueur au repos a été allongée de `précharge / k`,
     * et c'est ce que fait le calcul. Sans raideur, elle n'a donc aucun sens et reste
     * sans effet.
     */
    var preload = 0f

    /**
     * Effort maximal transmissible, en newtons. Au-delà, le ressort **glisse** : il
     * continue de pousser à cette valeur sans jamais dépasser.
     *
     * C'est la butée d'un ressort qu'on ne veut pas voir devenir une barre quand la
     * machine lui tombe dessus. Infini par défaut.
     */
    var maxForce = Float.POSITIVE_INFINITY

    /** Impulsion cumulée du pas, en kg·m/s. Positive quand le ressort **tire**. */
    var impulse = 0f
        private set

    /** Distance actuelle entre les deux ancrages, en mètres. */
    var length = 0f
        private set

    /**
     * Effort exercé au dernier pas, en newtons. Positif quand le ressort tire ses
     * ancrages l'un vers l'autre, négatif quand il les pousse.
     *
     * C'est aussi un capteur d'effort tout fait : un plateau posé sur deux ressorts
     * raides donne le poids de ce qu'on y pose.
     */
    val force: Float get() = impulse * lastInvDt

    /** Vrai quand [maxForce] a été atteint au dernier pas. */
    var saturated = false
        private set

    private var nx = 0f
    private var ny = 0f
    private var rax = 0f
    private var ray = 0f
    private var rbx = 0f
    private var rby = 0f
    private var effectiveMass = 0f
    private var gamma = 0f
    private var bias = 0f
    private var maxImpulse = Float.POSITIVE_INFINITY
    private var lastInvDt = 0f
    private var solvable = false

    companion object {
        /**
         * Tend un ressort entre deux points du monde, en prenant l'écartement actuel
         * comme longueur au repos : la machine démarre donc détendue.
         */
        fun between(
            a: PhysBody, ax: Float, ay: Float,
            b: PhysBody, bx: Float, by: Float,
            stiffness: Float = 1_000f,
            damping: Float = 0f
        ): SpringJoint = SpringJoint(a, b).also {
            it.setWorldAnchors(ax, ay, bx, by)
            it.restLength = hypot(bx - ax, by - ay)
            it.stiffness = stiffness
            it.damping = damping
        }

        /**
         * L'amortissement qui ramène le plus vite possible sans dépasser, pour une
         * raideur et une masse portée données : `2·√(k·m)`.
         */
        fun criticalDamping(stiffness: Float, mass: Float): Float =
            2f * kotlin.math.sqrt((stiffness * mass).coerceAtLeast(0f))
    }

    /** Pose les deux ancrages d'après des points du monde et la pose actuelle. */
    fun setWorldAnchors(ax: Float, ay: Float, bx: Float, by: Float) {
        val ca = cos(a.angle)
        val sa = sin(a.angle)
        val dxa = ax - a.x
        val dya = ay - a.y
        localAnchorAX = dxa * ca + dya * sa
        localAnchorAY = -dxa * sa + dya * ca

        val cb = cos(b.angle)
        val sb = sin(b.angle)
        val dxb = bx - b.x
        val dyb = by - b.y
        localAnchorBX = dxb * cb + dyb * sb
        localAnchorBY = -dxb * sb + dyb * cb
    }

    /** Position monde de l'ancrage porté par [a], dans [out]. */
    fun anchorAWorld(out: FloatArray) = a.localToWorld(localAnchorAX, localAnchorAY, out)

    /** Position monde de l'ancrage porté par [b], dans [out]. */
    fun anchorBWorld(out: FloatArray) = b.localToWorld(localAnchorBX, localAnchorBY, out)

    /**
     * Énergie élastique rangée en ce moment, en joules : `½·k·x²`.
     *
     * C'est elle qu'un banc d'essai doit ajouter à l'énergie des corps pour retrouver
     * un bilan qui se conserve — voir la remarque sur l'énergie en tête de classe.
     */
    val storedEnergy: Float
        get() {
            if (stiffness <= 0f) return 0f
            val x = length - relaxedLength()
            return 0.5f * stiffness * x * x
        }

    /** Longueur au repos réellement visée, précharge comprise. */
    private fun relaxedLength(): Float =
        if (stiffness > 0f) restLength + preload / stiffness else restLength

    override fun reset() {
        impulse = 0f
    }

    override fun preStep(invDt: Float) {
        solvable = false
        saturated = false
        lastInvDt = invDt
        if (!enabled || !a.inWorld || !b.inWorld) return
        // Ni raideur ni amortissement : il ne reste rien à résoudre. Et surtout, il ne
        // faut pas laisser passer ce cas — les deux à zéro annulent le terme mou, et la
        // liaison redeviendrait une barre rigide, ce que personne n'a demandé.
        if (stiffness <= 0f && damping <= 0f) return

        val ca = cos(a.angle)
        val sa = sin(a.angle)
        rax = localAnchorAX * ca - localAnchorAY * sa
        ray = localAnchorAX * sa + localAnchorAY * ca

        val cb = cos(b.angle)
        val sb = sin(b.angle)
        rbx = localAnchorBX * cb - localAnchorBY * sb
        rby = localAnchorBX * sb + localAnchorBY * cb

        val dx = (b.x + rbx) - (a.x + rax)
        val dy = (b.y + rby) - (a.y + ray)
        val dist = hypot(dx, dy)
        length = dist
        // Deux ancrages confondus : plus aucune direction où pousser.
        if (dist < 1e-5f) return
        nx = dx / dist
        ny = dy / dist

        val crossA = rax * ny - ray * nx
        val crossB = rbx * ny - rby * nx
        val k = a.invMass + b.invMass + a.invI * crossA * crossA + b.invI * crossB * crossB
        if (k <= 1e-9f) return

        // Le cœur de la contrainte molle. [gamma] est la souplesse rapportée au pas, et
        // [bias] la part de l'écart que le ressort cherche à résorber pendant ce pas-ci.
        // Les deux dépendent de la durée du pas : c'est ce qui rend le ressort
        // inconditionnellement stable, et c'est pour ça qu'ils se recalculent à chaque
        // sous-pas plutôt qu'une fois à la construction.
        val h = 1f / invDt
        val denom = h * (damping + h * stiffness)
        gamma = if (denom > 1e-20f) 1f / denom else 0f
        bias = (dist - relaxedLength()) * h * stiffness * gamma
        effectiveMass = 1f / (k + gamma)
        maxImpulse = if (maxForce.isFinite()) maxForce * h else Float.POSITIVE_INFINITY

        // Réapplication de l'impulsion du pas précédent.
        val px = impulse * nx
        val py = impulse * ny
        a.vx -= a.invMass * px
        a.vy -= a.invMass * py
        a.omega -= a.invI * (rax * py - ray * px)
        b.vx += b.invMass * px
        b.vy += b.invMass * py
        b.omega += b.invI * (rbx * py - rby * px)

        solvable = true
    }

    override fun applyImpulse() {
        if (!solvable) return

        val dvx = (b.vx - b.omega * rby) - (a.vx - a.omega * ray)
        val dvy = (b.vy + b.omega * rbx) - (a.vy + a.omega * rax)
        val vn = dvx * nx + dvy * ny

        // Le terme en `gamma × impulsion` est ce qui distingue une contrainte molle
        // d'une contrainte dure : il laisse la liaison céder d'autant plus que l'effort
        // qu'elle porte est grand.
        var dP = -effectiveMass * (vn + bias + gamma * impulse)
        val old = impulse
        impulse = (old + dP).coerceIn(-maxImpulse, maxImpulse)
        if (impulse != old + dP) saturated = true
        dP = impulse - old

        val px = dP * nx
        val py = dP * ny
        a.vx -= a.invMass * px
        a.vy -= a.invMass * py
        a.omega -= a.invI * (rax * py - ray * px)
        b.vx += b.invMass * px
        b.vy += b.invMass * py
        b.omega += b.invI * (rbx * py - rby * px)
    }

    /** Un ressort comprimé n'est pas une erreur de position : voir la tête de classe. */
    override fun applyPositionImpulse() = Unit

    /** Écart actuel à la longueur de repos, en mètres. Positif quand il est étiré. */
    val stretch: Float get() = length - relaxedLength()

    /** Vrai quand le ressort est comprimé de plus de [tolerance]. */
    fun isCompressed(tolerance: Float = 1e-4f): Boolean = stretch < -abs(tolerance)
}
