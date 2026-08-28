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
    /**
     * Masse, en kilogrammes — **pour ce qui n'emporte pas de charge**.
     *
     * Elle vaut zéro pour la bombe, et ce n'est pas un oubli : sa masse dépend du nombre
     * de bâtons qu'on y met, et rien ne peut la connaître sans le savoir. Tout ce qui
     * veut la masse réellement lancée passe par [massFor] — ou mieux, par
     * `MachineConfig.shotMass`, qui sait déjà combien de bâtons sont chargés.
     */
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
    /**
     * Masse de l'enveloppe seule, en kilogrammes, pour ce qui porte une charge.
     *
     * La masse d'une bombe n'est pas une constante : c'est l'enveloppe **plus la
     * poudre**, et la poudre se règle. Voir [massFor].
     */
    val casing: Float = 0f,
    /**
     * Rayon de l'explosion à la charge de référence, en mètres **de site réaliste** —
     * voir [blastRadiusFor]. Zéro pour ce qui n'explose pas.
     */
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
     * Son souffle **renverse** autant qu'il pulvérise : une impulsion qui envoie la
     * charpente en l'air pendant que la muraille se contente de basculer, et une énergie
     * qui fend ce qui est trop lourd pour bouger.
     *
     * C'est le seul projectile dont la **masse n'est pas écrite ici** : elle vaut
     * l'enveloppe plus la poudre, et la poudre se règle en bâtons. Voir [massFor] et
     * [DEFAULT_STICKS].
     */
    BOMBE(0f, 0.21f, casing = 5f, blastRadiusBase = 6f);

    /**
     * Traînée, en kg/m : la moitié de ρ·Cx·S pour une sphère de ce rayon.
     *
     * Elle sort du rayon et n'est pas recopiée à la main : un projectile plus gros
     * freine davantage, et c'est une bonne part de ce qui distingue les quatre.
     */
    val drag: Float get() = (0.5f * 1.2f * 0.47f * PI * radius * radius).toFloat()

    /** Vrai pour ce qui porte une charge, et dont la masse dépend donc du réglage. */
    val explosive: Boolean get() = blastRadiusBase > 0f

    /**
     * Masse lancée, la charge comprise.
     *
     * Pour tout le reste du catalogue c'est la constante de la table. Pour la bombe,
     * c'est l'enveloppe plus les bâtons — et **c'est délibérément un arbitrage** : une
     * grosse charge est une bombe lourde, qui part moins vite et retombe plus court. Un
     * réglage de puissance qui ne coûterait rien serait un réglage qu'on pousse à fond
     * une fois pour toutes et qu'on ne retouche jamais.
     */
    fun massFor(sticks: Int): Float =
        if (explosive) casing + sticks.coerceAtLeast(0) * STICK_MASS else mass

    /**
     * Énergie que le souffle dépose dans **une** pierre au point zéro, en joules.
     *
     * Ce n'est pas ce que la charge libère, et la nuance est tout le sujet. Neuf kilos
     * de poudre libèrent vingt-sept mégajoules, quand la vie entière d'un château
     * réaliste en vaut trente-cinq : une bombe qui rendrait sa chimie effacerait le site
     * d'un coup. Ce qui compte est **ce qui entre dans la pierre** : le souffle part en
     * sphère, une face d'un mètre carré à un mètre de la charge en intercepte huit pour
     * cent, à trois mètres moins d'un, et sur cette part-là seule une fraction se paie
     * en travail de rupture. D'où [BLAST_COUPLING], de l'ordre du pour cent.
     *
     * Le choix de l'explosif, lui, ne change presque rien : le TNT vaut 4,2 MJ/kg, la
     * dynamite 5 à 7,5, et le plafond des explosifs classiques tourne autour de 6 à 7.
     * Un facteur deux, là où le couplage en vaut cent.
     */
    fun blastEnergyFor(sticks: Int): Float =
        if (explosive) sticks.coerceAtLeast(0) * STICK_ENERGY * BLAST_COUPLING else 0f

    /**
     * Rayon du souffle, à l'échelle du site en cours et de la charge emportée.
     *
     * Deux mises à l'échelle, pour deux raisons différentes. Celle du **site** parce
     * qu'une bombe doit faire un trou qui se voit, et que « qui se voit » se mesure en
     * pierres et pas en mètres : en arcade les pierres font six mètres de front, donc le
     * souffle doit faire six mètres de plus. Celle de la **charge** parce qu'un volume
     * grandit comme le cube de son rayon : quadrupler la poudre n'agrandit le cercle que
     * de moitié. Un bâton fait 1,7 m, quarante-cinq en font 6, cent en font 7,8.
     */
    fun blastRadiusFor(sticks: Int): Float {
        if (!explosive) return 0f
        val part = (sticks.coerceAtLeast(1).toFloat() / DEFAULT_STICKS).toDouble()
        return TargetRules.site(blastRadiusBase) * Math.cbrt(part).toFloat()
    }

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

    companion object {

        /** Masse d'un bâton, en kilogrammes : deux cents grammes, comme un vrai. */
        const val STICK_MASS = 0.2f

        /** Ce qu'un bâton libère, en joules : 200 g de TNT à 4,18 MJ/kg. */
        const val STICK_ENERGY = 836_000f

        /**
         * Part de l'énergie libérée qui finit en travail de rupture dans une pierre au
         * point zéro. Voir [blastEnergyFor], où le raisonnement est fait.
         */
        const val BLAST_COUPLING = 0.0106f

        /**
         * La charge par défaut, et celle qui sert de référence au rayon.
         *
         * Quarante-cinq bâtons ne sont pas un chiffre rond par hasard : ils redonnent
         * **exactement** la bombe d'avant le réglage — quatorze kilos, quatre cents
         * kilojoules, six mètres de rayon. Une machine enregistrée avant que la charge
         * n'existe se recharge donc sans changer de comportement, et c'est la seule
         * façon honnête d'ajouter un réglage à un jeu déjà joué.
         */
        const val DEFAULT_STICKS = 45

        /** Bornes de la charge : d'un pétard à un baril. */
        const val MIN_STICKS = 1
        const val MAX_STICKS = 120
    }
}
