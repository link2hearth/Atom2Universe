package com.Atom2Universe.app.games.trebuchet

import kotlin.math.PI

/**
 * Ce qu'on met dans la fronde.
 *
 * La machine, elle, ne change pas d'un projectile à l'autre : c'est le même bras, le
 * même contrepoids, la même fronde. Ce qui change est la masse qu'elle doit lancer —
 * et un trébuchet est très sensible au rapport contrepoids/boulet, donc changer de
 * projectile **change le tir**, pas seulement ce qui arrive au bout. Un boulet lourd
 * part moins vite et tombe plus court, et il faut réaccorder la machine autour de lui :
 * c'est délibéré, c'est le seul choix du jeu qui oblige à tout revoir.
 *
 * Les quatre ne se distinguent pas par des dégâts en plus — ils se distinguent par la
 * **façon dont on s'en sert** :
 *
 *  - le [BOULET] perce : il entre par un endroit et ce qu'il traverse tombe ;
 *  - le [LOURD] enfonce : trop lent pour aller loin, mais rien ne l'arrête ;
 *  - la [FRAGMENTATION] arrose : un seul tir bien calé couvre tout un front, et c'est
 *    la seule façon d'abattre trois maisons d'un coup ;
 *  - la [BOMBE] creuse : elle ne compte pas sur sa vitesse, elle rend tout d'un coup à
 *    l'endroit où elle touche.
 */
enum class Projectile(
    /** Masse, en kilogrammes. */
    val mass: Float,
    /** Rayon, en mètres. */
    val radius: Float,
    /** Nombre d'éclats, pour un projectile qui se sépare en vol. Un seul sinon. */
    val shards: Int = 1,
    /**
     * À quelle fraction de la **descente** les éclats se séparent, de 0 (au sommet de
     * la cloche) à 1 (au sol).
     *
     * La séparation se fait après le sommet, jamais avant : un projectile qui se
     * sépare pendant la montée disperse ses éclats sur toute la trajectoire, et le
     * joueur n'a plus rien à viser.
     */
    val splitFraction: Float = 0f,
    /** Énergie de l'explosion à l'impact, en joules. Zéro pour ce qui n'explose pas. */
    val blastEnergy: Float = 0f,
    /** Rayon de l'explosion, en mètres **de site réaliste** — voir [blastRadiusNow]. */
    private val blastRadiusBase: Float = 0f
) {
    /** La belle pierre de taille : l'étalon, et celui avec lequel on apprend la machine. */
    BOULET(12f, 0.16f),

    /**
     * Le gros bloc : trois fois la masse, et la machine le sent passer.
     *
     * Il ne sert à rien contre du bois — il n'ira pas plus loin qu'un boulet dans une
     * maison — mais son énergie ne s'épuise pas dans une courtine, là où le boulet
     * s'arrête à la deuxième assise.
     */
    LOURD(34f, 0.24f),

    /**
     * Le paquet lié : il se défait en cinq pendant sa chute et arrose un front entier.
     *
     * Trente pour cent de la descente est le bon endroit, et ce n'est pas un chiffre
     * au hasard : plus tôt, les éclats se dispersent trop et la moitié tombe à côté ;
     * plus tard, ils arrivent groupés et autant tirer un boulet.
     */
    FRAGMENTATION(12f, 0.19f, shards = 5, splitFraction = 0.30f),

    /**
     * La bombe : elle rend tout à l'endroit où elle touche.
     *
     * L'énergie du souffle n'a rien à voir avec sa vitesse — c'est bien l'intérêt : un
     * tir mou qui touche juste vaut mieux qu'un tir tendu qui frôle. C'est le
     * projectile de celui qui sait viser mais dont la machine n'est pas accordée.
     *
     * Son souffle **renverse** plus qu'il ne pulvérise, et c'est ce qui a changé depuis
     * la première version. Elle valait alors quatre-vingt-dix kilojoules de dégâts purs
     * sur douze mètres : tout ce qui se trouvait dans le cercle disparaissait, ce qui
     * était spectaculaire une fois et faux tout le temps. Une explosion, ça pousse — la
     * moitié de l'énergie, donc, et une impulsion qui envoie la charpente en l'air
     * pendant que la muraille se contente de basculer.
     */
    BOMBE(14f, 0.21f, blastEnergy = 45_000f, blastRadiusBase = 6f);

    /**
     * Traînée, en kg/m : la moitié de ρ·Cx·S pour une sphère de ce rayon.
     *
     * Elle sort du rayon et n'est pas recopiée à la main : un projectile plus gros
     * freine davantage, et c'est une bonne part de ce qui distingue les quatre.
     */
    val drag: Float get() = (0.5f * 1.2f * 0.47f * PI * radius * radius).toFloat()

    /**
     * Rayon du souffle à l'échelle du site en cours.
     *
     * Une bombe doit faire un trou qui se voit, et « qui se voit » se mesure en
     * pierres, pas en mètres : en arcade les pierres font six mètres de front, donc le
     * souffle doit faire six mètres de plus.
     */
    val blastRadiusNow: Float get() = TargetRules.site(blastRadiusBase)

    /**
     * Masse de **chaque** éclat, celle du paquet étant donnée.
     *
     * C'est le seul endroit du projectile où le tempérament du jeu s'invite, et
     * l'écart est assumé : en réaliste, cinq éclats se partagent le paquet, chacun
     * pèse deux kilos et demi, et ça ne casse plus rien du tout — ce qui est exact et
     * décevant. En arcade, **chaque éclat pèse le paquet entier**. On triche, on le
     * dit, et on obtient la seule chose qui rende ce projectile intéressant : cinq
     * boulets pour le prix d'un.
     */
    fun shardMass(): Float =
        if (TargetRules.style == TargetStyle.ARCADE) mass else mass / shards
}
