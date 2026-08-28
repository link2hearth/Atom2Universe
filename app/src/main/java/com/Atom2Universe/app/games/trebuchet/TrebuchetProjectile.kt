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
 * Les trois ne se distinguent pas par des dégâts en plus — ils se distinguent par la
 * **façon dont on s'en sert** :
 *
 *  - le [BOULET] perce : il entre par un endroit et ce qu'il traverse tombe. Son poids
 *    se règle de un à quatre-vingt-dix-neuf kilos, et c'est tout le vocabulaire du tir
 *    tendu ou du tir qui enfonce ;
 *  - la [FRAGMENTATION] arrose : un seul tir bien calé couvre tout un front, et c'est
 *    la seule façon d'abattre trois maisons d'un coup ;
 *  - la [BOMBE] creuse : elle ne compte pas sur sa vitesse, elle rend tout d'un coup à
 *    l'endroit où elle touche.
 *
 * **Il y en avait quatre.** Un « bloc lourd » de trente-quatre kilos figurait au
 * catalogue à côté du boulet de douze, et c'était une entrée de trop : deux points fixes
 * sur un axe continu, dans un jeu dont la règle est que les réglages sont « continus et
 * toujours entièrement disponibles ». Le poids du boulet se règle donc, et le bloc lourd
 * n'est plus qu'un boulet à trente-quatre — une machine enregistrée qui en emportait un
 * le retrouve exactement, voir [MachineLibrary].
 */
enum class Projectile(
    /**
     * Masse **de référence**, en kilogrammes : celle à laquelle [radius] est donné.
     *
     * Ce n'est pas forcément la masse lancée. Le boulet se règle en poids, la bombe se
     * règle en bâtons, et tout ce qui veut savoir ce que la machine soulève vraiment
     * passe par `MachineConfig.shotMass` — jamais par ce champ-ci.
     */
    val mass: Float,
    /** Rayon à la masse de référence, en mètres. Voir [radiusFor]. */
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
    private val blastRadiusBase: Float = 0f,
    /**
     * Vrai pour ce dont le joueur règle le poids directement, en kilos.
     *
     * Le boulet seul. Le paquet de fragmentation a une masse qui est celle de son
     * paquet, la bombe une masse qui sort de sa charge : leur poids se règle par un
     * autre bout, ou pas du tout.
     */
    val weighable: Boolean = false
) {
    /**
     * La belle pierre de taille : l'étalon, et celui avec lequel on apprend la machine.
     *
     * **C'est celui dont on règle le poids**, de un à quatre-vingt-dix-neuf kilos, et ce
     * seul réglage remplace les deux entrées d'avant. Un caillou d'un kilo part très
     * vite et se fait manger par l'air ; un bloc de quatre-vingt-dix-neuf part lentement
     * mais ne s'arrête plus. Entre les deux il y a un optimum, il dépend de la machine,
     * et le trouver est exactement le genre de chose que ce jeu demande.
     */
    BOULET(12f, 0.16f, weighable = true),

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
     * Sa masse de référence — quatorze kilos — est celle de la charge par défaut. La
     * masse réelle vaut l'enveloppe plus la poudre, et la poudre se règle en bâtons.
     * Voir [massFor] et [DEFAULT_STICKS].
     */
    BOMBE(14f, 0.21f, casing = 5f, blastRadiusBase = 6f);

    /**
     * Traînée, en kg/m : la moitié de ρ·Cx·S pour une sphère de ce rayon-là.
     *
     * Elle sort du rayon et n'est pas recopiée à la main : un projectile plus gros
     * freine davantage, et c'est une bonne part de ce qui distingue les trois.
     */
    fun dragFor(r: Float): Float = (0.5f * 1.2f * 0.47f * PI * r * r).toFloat()

    /**
     * Rayon à la masse donnée : un projectile lourd est un projectile **gros**.
     *
     * En racine cubique, parce qu'une masse remplit un volume. Ce n'est pas de la
     * coquetterie : le rayon décide de la traînée, donc de la façon dont l'air mange le
     * tir. Un caillou d'un kilo a un rapport surface/masse quatre fois plus mauvais
     * qu'un bloc de soixante-quatre, il part plus vite et arrive plus lentement — et
     * c'est ce qui fait qu'il existe un poids optimal pour chaque machine.
     *
     * La loi est calée sur le catalogue d'avant : le boulet de douze kilos faisait
     * seize centimètres et le bloc lourd de trente-quatre en faisait vingt-quatre. La
     * racine cubique en donne vingt-deux et demi. Les deux entrées écrites à la main
     * suivaient donc déjà cette règle sans le dire, ce qui est la meilleure raison de
     * l'écrire.
     */
    fun radiusFor(m: Float): Float {
        if (mass <= 0f) return radius
        val part = (m.coerceAtLeast(0.01f) / mass).toDouble()
        return radius * Math.cbrt(part).toFloat()
    }

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

        /**
         * Bornes du poids d'un boulet, en kilogrammes, et son poids par défaut.
         *
         * Quatre-vingt-dix-neuf et pas cent, pour que la roulette n'ait que **deux
         * colonnes de chiffres** : une troisième colonne qui ne sert qu'à afficher un
         * zéro coûte de la place à l'écran et une seconde de lecture à chaque coup
         * d'œil. La borne haute d'un réglage doit tenir dans l'affichage qu'on lui
         * donne, sinon c'est l'affichage qui commande.
         */
        const val MIN_BALL_MASS = 1f
        const val MAX_BALL_MASS = 99f
        const val DEFAULT_BALL_MASS = 12f
    }
}
