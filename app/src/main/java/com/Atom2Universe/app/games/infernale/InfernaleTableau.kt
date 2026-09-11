package com.Atom2Universe.app.games.infernale

import com.Atom2Universe.app.games.physics.PhysBody
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Une piece a poser, decrite sans corps physique : de quoi la reconstruire a volonte.
 *
 * C'est ce qu'un tableau range, ce qu'une sauvegarde ecrit et ce qu'un doigt deplace.
 * Un [Piece] porte des corps vivants dans un monde ; une [Pose] n'est qu'un souvenir de
 * l'endroit ou on la voulait, et se recopie sans consequence.
 */
data class Pose(
    val type: TypePiece,
    val x: Float,
    val y: Float,
    /**
     * Reglage libre, dont le sens depend du type : la pente d'une rampe en degres, la
     * direction du jet d'un ventilateur en degres, le cote de la charniere d'un tremplin
     * (negatif = miroir).
     */
    val reglage: Float = 0f,
    /**
     * Cote principale de la piece — sa longueur, son rayon, son cote. Zero prend celle
     * par defaut.
     *
     * **Elle fait partie de la pose, et ce n'est pas un detail de confort.** Sans elle,
     * le generateur calculait ou lacher la bille pour une rampe de deux metres pendant
     * que `creer` en fabriquait une de un metre cinquante : la bille tombait cinq
     * centimetres avant le bord, ratait la planche, repartait sur le coin dans l'autre
     * sens, et **aucun** tableau tire au hasard n'etait soluble. Une pose qui ne decrit
     * pas entierement sa piece est une pose qui ment.
     */
    val taille: Float = 0f
) {
    fun creer(): Piece = when (type) {
        TypePiece.RAMPE -> Pieces.rampe(x, y, pente = reglage, longueur = cote(Pieces.RAMPE_LONGUEUR))
        TypePiece.PLOT -> Pieces.plot(x, y, rayon = cote(Pieces.PLOT_RAYON))
        TypePiece.BLOC -> Pieces.bloc(x, y, largeur = cote(Pieces.BLOC_COTE), hauteur = cote(Pieces.BLOC_COTE))
        TypePiece.DOMINO -> Pieces.domino(x, y, hauteur = cote(Pieces.DOMINO_HAUTEUR))
        TypePiece.BASCULE -> Pieces.bascule(x, y, longueur = cote(Pieces.BASCULE_LONGUEUR))
        TypePiece.TREMPLIN -> Pieces.tremplin(
            x, y,
            longueur = cote(Pieces.TREMPLIN_LONGUEUR),
            sens = if (reglage < 0f) -1f else 1f
        )
        TypePiece.VENTILATEUR -> Pieces.ventilateur(
            x, y,
            direction = reglage,
            cote = cote(Pieces.VENTILATEUR_COTE)
        )
        TypePiece.TAMBOUR -> Pieces.tambour(x, y, largeur = cote(Pieces.TAMBOUR_LARGEUR))
        TypePiece.POULIE -> Pieces.poulie(x, y, hauteur = cote(Pieces.POULIE_HAUTEUR))
    }

    private fun cote(defaut: Float): Float = if (taille > 0f) taille else defaut
}

/**
 * Les regles de placement, partagees par le joueur et par le generateur.
 *
 * **Elles doivent etre les memes des deux cotes, et c'est tout l'interet de ce petit
 * objet.** Un generateur qui s'autorise a superposer deux pieces produit une solution
 * que le joueur ne peut pas reproduire : le tableau est alors soluble sur le papier et
 * insoluble a la main, ce qui est la pire facon d'echouer — personne ne peut le
 * diagnostiquer en jouant.
 */
object Placement {

    /** Tolerance de placement, en metres : deux pieces peuvent se toucher. */
    const val MARGE = 0.02f

    /** Marge sous le sol : une piece ne s'enterre pas. */
    const val MARGE_SOL = 0.02f

    /** Vrai si la piece tient entierement dans le tableau. */
    fun dansLeTableau(piece: Piece, largeur: Float, hauteur: Float): Boolean =
        dansLeCadre(piece, -largeur / 2f, largeur / 2f, hauteur)

    /**
     * Vrai si la piece tient entierement dans le cadre de jeu.
     *
     * Le cadre est ce que le joueur voit. Une piece qui en sort serait invisible donc
     * impossible a reprendre : le refus est ici la seule facon de ne jamais mettre le
     * joueur dans une impasse dont il ne comprendrait pas la cause.
     */
    fun dansLeCadre(piece: Piece, minX: Float, maxX: Float, maxY: Float): Boolean {
        for (c in piece.corps) c.updateAabb()
        for (c in piece.corps) {
            if (c.aabbMinX < minX || c.aabbMaxX > maxX) return false
            if (c.aabbMinY < -MARGE_SOL || c.aabbMaxY > maxY) return false
        }
        return true
    }

    /**
     * Chevauchement par boites englobantes, avec une marge de tolerance.
     *
     * **Pas par cercles englobants**, et ca s'est paye : le cercle circonscrit d'un
     * domino pose au sol plonge sous le sol (son centre est a 22 cm, son rayon a 22,4),
     * si bien qu'aucune piece n'etait posable. Et celui d'une rampe de deux metres fait
     * un metre de rayon, ce qui lui faisait « chevaucher » tout ce qui passait a moins
     * d'un metre — y compris la solution du generateur. Un cercle est une mauvaise
     * approximation des pieces longues et plates, et elles le sont presque toutes ici.
     *
     * La marge autorise les pieces qui se touchent sans se penetrer : un domino pose
     * contre une rampe est un placement legitime, et souvent le bon.
     */
    fun seChevauchent(a: PhysBody, b: PhysBody): Boolean =
        a.aabbMinX < b.aabbMaxX - MARGE && b.aabbMinX < a.aabbMaxX - MARGE &&
            a.aabbMinY < b.aabbMaxY - MARGE && b.aabbMinY < a.aabbMaxY - MARGE

    /** Vrai si la piece heurte l'un des corps deja en place. */
    fun heurte(piece: Piece, occupants: List<PhysBody>): Boolean {
        for (c in piece.corps) c.updateAabb()
        for (c in piece.corps) {
            for (autre in occupants) if (seChevauchent(c, autre)) return true
        }
        return false
    }
}

/**
 * Un tableau : d'ou part la bille, ou est le bouton, et de quoi dispose le joueur.
 *
 * Il ne contient aucun corps : c'est une recette, pas un monde. On en monte autant de
 * mondes qu'on veut, ce qui permet de rejouer un essai sans rien reinitialiser.
 */
class Tableau(
    val graine: Long,
    val billeX: Float,
    val billeY: Float,
    val boutonX: Float,
    val boutonBas: Float,
    /**
     * Le placement qui gagne — celui que le generateur a verifie.
     *
     * Il sert a deux choses et jamais a une troisieme : prouver que le tableau est
     * soluble, et fabriquer l'inventaire. Il n'est **pas** « la » solution : rien
     * n'empeche le joueur d'en trouver une autre avec les memes pieces, et c'est
     * meme tout l'interet.
     */
    val solution: List<Pose>,
    /** Temps qu'a mis la solution du generateur, en secondes. Sert au bareme. */
    val tempsReference: Float = 0f
) {
    /**
     * Ce que le joueur recoit : exactement les pieces de la solution, pas une de plus.
     *
     * C'est le reglage de difficulte le plus honnete qui soit. Donner trois dominos de
     * trop transforme un casse-tete en bac a sable ; en donner exactement ce qu'il faut
     * oblige a comprendre a quoi sert chaque piece, sans jamais rendre le tableau
     * insoluble.
     */
    val inventaire: Map<TypePiece, Int> = solution.groupingBy { it.type }.eachCount()

    /** Nombre total de pieces a poser. */
    val pieces: Int get() = solution.size

    // ── Le cadre de jeu ──────────────────────────────────────────────────────
    //
    // Le plateau fait dix metres de large, mais un tableau n'en occupe qu'une partie.
    // Montrer les dix metres sur un telephone rend la bille grosse comme une tete
    // d'epingle ; en montrer six rend le jeu lisible. Le cadre est donc calcule sur ce
    // que le tableau utilise vraiment.
    //
    // **Et c'est aussi la limite de construction.** Le joueur ne peut poser une piece
    // que dans ce qu'il voit : une piece posee hors cadre serait invisible, donc
    // impossible a reprendre, et le tableau paraitrait casse. Une seule zone pour les
    // deux usages, c'est une incoherence de moins a inventer.

    /** Bord gauche du cadre, en metres. */
    val cadreMinX: Float

    /** Bord droit du cadre. */
    val cadreMaxX: Float

    /** Plafond du cadre. Le plancher est toujours le sol. */
    val cadreMaxY: Float

    init {
        var lo = minOf(billeX, boutonX)
        var hi = maxOf(billeX, boutonX)
        var haut = maxOf(billeY, boutonBas)
        for (p in solution) {
            lo = minOf(lo, p.x - 1.3f)
            hi = maxOf(hi, p.x + 1.3f)
            // 1,8 m et pas 0,9 : la pose d'une poulie designe le **pied** de son bati,
            // qui monte a un metre cinquante au-dessus. Une marge taillee sur les pieces
            // plates laissait la traverse hors cadre, donc impossible a poser.
            haut = maxOf(haut, p.y + 2.1f)
        }
        lo -= 1.1f
        hi += 1.1f
        haut += 0.8f

        // Une largeur plancher, sinon un tableau ramasse se retrouve grossi au point que
        // le moindre geste du doigt deplace une piece d'un demi-metre.
        val largeurMini = 5.5f
        if (hi - lo < largeurMini) {
            val centre = (lo + hi) / 2f
            lo = centre - largeurMini / 2f
            hi = centre + largeurMini / 2f
        }
        val limite = 5f
        if (lo < -limite) { hi += -limite - lo; lo = -limite }
        if (hi > limite) { lo -= hi - limite; hi = limite }
        cadreMinX = lo.coerceAtLeast(-limite)
        cadreMaxX = hi.coerceAtMost(limite)
        cadreMaxY = haut.coerceIn(3f, 6.8f)
    }

    /** Les types qui apparaissent, dans l'ordre de l'enumeration : le resume d'un tableau. */
    val panoplie: List<TypePiece> get() = TypePiece.entries.filter { it in inventaire }
}

/** Un instant de la course de la bille : ou elle est, et ou elle va. */
class Echantillon(val t: Float, val x: Float, val y: Float, val vx: Float, val vy: Float) {
    val vitesse: Float get() = hypot(vx, vy)

    /** Le sens du deplacement horizontal : +1 a droite, -1 a gauche. */
    fun sens(defaut: Float): Float = when {
        vx > 0.3f -> 1f
        vx < -0.3f -> -1f
        else -> defaut
    }
}

/**
 * Ce qu'a fait la bille pendant un essai : sa course echantillonnee, et ce qui l'a
 * arretee.
 *
 * C'est le retour d'information dont vit le generateur. Une machine infernale ne se
 * calcule pas — trois rebonds et la moindre erreur d'un centimetre change tout. Mais
 * elle se **regarde** : il suffit de la faire tourner et de noter ou la bille est
 * passee, ce qui coute quelques millisecondes et ne se trompe jamais.
 */
class Trace(
    val points: List<Echantillon>,
    /** La bille est sortie du tableau : l'essai ne vaut rien. */
    val perdue: Boolean,
    /** Longueur totale parcourue, en metres. C'est la mesure de « il s'est passe quelque chose ». */
    val parcours: Float
) {
    val fin: Echantillon? get() = points.lastOrNull()
    val duree: Float get() = fin?.t ?: 0f
}

/**
 * La fabrique de tableaux.
 *
 * ## Comment on garantit qu'un tableau est soluble
 *
 * Tirer un decor au hasard puis se demander s'il a une solution est un probleme qu'on ne
 * sait pas resoudre : il faudrait explorer tous les placements possibles de toutes les
 * pieces. On fait donc l'inverse, et c'est la vieille ruse des generateurs de niveaux :
 * **on construit la solution d'abord, on la verifie en la faisant tourner, et on la
 * retire.**
 *
 * ## Ce qui a change : on ne tire plus la machine, on la fait pousser
 *
 * La premiere version tirait une forme unique — une rampe, une ligne de dominos — avec
 * des cotes qui variaient. Tous les tableaux se ressemblaient, et pour cause : la seule
 * facon de tirer une machine complete au hasard et d'esperer qu'elle marche, c'est de
 * n'en tirer qu'une sorte.
 *
 * Ici, la machine **pousse etape par etape**, et chaque etape est choisie en regardant ce
 * que la bille vient de faire :
 *
 *  1. on lache la bille sur le tableau tel qu'il est, et on note toute sa course ;
 *  2. on choisit un instant de cette course — la bille tombe ici, elle roule la ;
 *  3. on propose une piece qui sait agir sur cet instant-la : une rampe sous une chute,
 *     un tremplin sous ce qui tombe, un ventilateur derriere ce qui roule ;
 *  4. on relance tout, et on ne garde la piece que si **la bille va plus loin qu'avant**.
 *
 * Le critere de l'etape 4 est ce qui tient l'ensemble. Il est monotone — chaque piece
 * ajoute du parcours — donc la machine ne peut pas tourner en rond, et il est mesure sur
 * le moteur lui-meme, donc il ne ment pas.
 *
 * ## Et l'inventaire ne contient que des pieces utiles
 *
 * A la fin, [elaguer] retire une a une les pieces dont l'absence ne change pas l'issue.
 * C'est ce qui empeche le generateur de refiler au joueur un domino decoratif, pose
 * quelque part ou rien ne le touche : une piece de l'inventaire est une piece dont on a
 * **verifie** que la machine echoue sans elle.
 */
object Tableaux {

    /** Pas de simulation. Le meme que celui du jeu : une solution verifiee est jouable. */
    private const val PAS = 1f / 120f

    /** Duree laissee a une machine pour faire ses preuves. */
    private const val PATIENCE = 9f

    /** Nombre de placements tentes avant d'abandonner une etape. */
    private const val TENTATIVES = 14

    /** Parcours supplementaire exige d'une piece pour etre gardee, en metres. */
    private const val PROGRES_MINIMUM = 0.45f

    /**
     * Deplacement exige du **point d'arrivee** de la bille, en metres.
     *
     * C'est le critere qui a sauve le generateur. Il ne demandait d'abord qu'un parcours
     * plus long, et le resultat etait desolant : sur vingt niveaux, quatorze se
     * resolvaient avec **une seule piece**. La raison est limpide une fois vue — le
     * bouton finit la ou la bille s'arrete, et la bille roule jusqu'au meme coin quel que
     * soit le chemin qu'on lui fait prendre. Rallonger sa course ne change donc rien a
     * l'endroit ou elle finit, et l'elagage avait raison de tout jeter.
     *
     * Exiger que le point d'arrivee **bouge** rend chaque piece responsable de la fin de
     * l'histoire, et donc indispensable. C'est la meme mesure que celle de l'elagage, prise
     * a l'avance au lieu d'etre subie.
     */
    private const val DEPLACEMENT_ARRIVEE = 0.45f

    /** Plafond de pieces : au-dela, le tableau devient illisible sur un telephone. */
    private const val PIECES_MAX = 11

    /** Chute de la bille jusqu'a la margelle du godet d'une poulie, en metres. */
    private const val CHUTE_GODET = 0.3f

    /**
     * Duree pendant laquelle la bille doit rester immobile pour qu'on arrete l'essai.
     *
     * Le monde entier au repos etait le critere, et il coutait cher : un ventilateur
     * pose quelque part maintient un corps eveille indefiniment, si bien que chaque essai
     * allait au bout de ses neuf secondes. Or ce qu'on mesure est la **course de la
     * bille** ; quand elle est arretee depuis une demi-seconde, la suite ne dira rien de
     * plus. La generation d'un niveau est passee de huit secondes a moins d'une.
     */
    private const val REPOS_BILLE = 0.5f

    /**
     * Tire un tableau soluble a partir de [graine], ou rend `null` si aucun des
     * [essais] candidats n'a gagne.
     *
     * Le meme nombre rend toujours le meme tableau : c'est ce qui permet de partager un
     * tableau par son seul numero.
     */
    fun generer(graine: Long, etapes: Int = 3, essais: Int = 24): Tableau? {
        val hasard = Random(graine)
        repeat(essais) {
            val candidat = construire(graine, hasard, etapes)
            if (candidat != null) return candidat
        }
        return null
    }

    /**
     * Le premier tableau soluble a partir de [graine], en essayant les graines suivantes
     * si celle-la ne donne rien. Ne rend jamais `null`.
     *
     * ## Et il redescend d'une etape plutot que d'echouer
     *
     * Une machine a cinq maillons ou chaque maillon doit **deplacer l'arrivee de la
     * bille** est un evenement rare : la probabilite est celle d'une etape, a la
     * puissance cinq. Le generateur a bel et bien echoue sur les vingt-quatre graines du
     * niveau onze, et il levait une exception au milieu de l'ecran de chargement.
     *
     * Servir un tableau a quatre maillons est infiniment preferable a ne rien servir du
     * tout : le joueur verra un niveau un peu plus facile que prevu, ce qu'il ne
     * remarquera meme pas, la ou un plantage se remarque beaucoup. On redescend donc
     * d'un cran jusqu'a deux, et deux maillons, on en trouve toujours.
     */
    fun genererSurement(graine: Long, etapes: Int = 3): Tableau {
        var exigence = etapes
        while (exigence >= 2) {
            repeat(24) { essai ->
                // **Des graines ecartees, pas la suivante.** En prenant `graine + 1`, la
                // graine 1 qui echoue et la graine 2 qui reussit rendaient le meme
                // tableau : sur vingt numeros, huit tableaux distincts seulement, et un
                // numero de tableau ne voulait plus rien dire. Multiplier par un nombre
                // plus grand que le nombre d'essais rend toute collision impossible.
                generer(graine * 101L + essai, exigence)?.let { return it }
            }
            exigence--
        }
        error("aucun tableau soluble autour de la graine $graine")
    }

    /**
     * Le tableau du niveau [niveau], numerote a partir de 1.
     *
     * La difficulte ne monte pas en ajoutant des pieces au hasard : elle monte en
     * ajoutant des **etapes**, c'est-a-dire des maillons a la chaine. Un tableau a deux
     * etapes se lit d'un coup d'oeil ; un tableau a cinq demande de comprendre ce que
     * chaque piece transmet a la suivante.
     */
    fun pourNiveau(niveau: Int): Tableau {
        val etapes = 2 + (niveau - 1) / 4
        return genererSurement(niveau.toLong() * 7919L, etapes.coerceIn(2, 4))
    }

    /**
     * Monte un monde a partir d'un tableau. [avecSolution] pose les pieces gagnantes —
     * c'est ce dont se sert la verification, et ce qui permet de montrer la reponse.
     */
    fun monter(tableau: Tableau, avecSolution: Boolean = false): Plateau {
        val p = Plateau()
        p.poserBouton(x = tableau.boutonX, bas = tableau.boutonBas)
        if (avecSolution) for (pose in tableau.solution) p.poser(pose.creer())
        p.poserBille(x = tableau.billeX, y = tableau.billeY)
        return p
    }

    // ── La croissance ────────────────────────────────────────────────────────

    private fun construire(graine: Long, hasard: Random, etapes: Int): Tableau? {
        val billeX = -(hasard.nextFloat() * 1.1f + 2.9f)
        val billeY = hasard.nextFloat() * 1.2f + 3.7f

        var poses = emptyList<Pose>()
        var trace = simuler(billeX, billeY, poses)
        if (trace.perdue) return null

        // On rejoue une etape ratee au lieu de l'abandonner. Une etape echoue le plus
        // souvent parce que le point tire au hasard sur la course ne se pretait a rien ;
        // en retirer un autre coute une poignee de simulations et reussit souvent. Sans
        // cette reprise, un seul point malheureux condamnait toute la machine, et le
        // generateur ne servait qu'un tableau sur deux.
        var reprises = 0
        while (poses.size < etapes && reprises < etapes * 2) {
            reprises++
            val suivante = pousser(poses, trace, billeX, billeY, hasard) ?: continue
            poses = suivante.first
            trace = suivante.second
        }
        // Une machine plus courte que demande n'est pas un tableau rate : c'est un tableau
        // d'un autre niveau. On la rejette plutot que de la servir a la place de celle
        // qu'on avait promise.
        if (poses.size < etapes) return null

        val fini = achever(poses, trace, billeX, billeY, hasard) ?: return null
        val (avecFin, bouton) = fini

        val retenues = elaguer(avecFin, billeX, billeY, bouton)
        if (retenues.size < etapes || retenues.size > PIECES_MAX) return null

        val tableau = Tableau(
            graine = graine,
            billeX = billeX,
            billeY = billeY,
            boutonX = bouton.first,
            boutonBas = bouton.second,
            solution = retenues,
            tempsReference = 0f
        )
        val temps = verifier(tableau) ?: return null
        if (gagneSansRien(tableau)) return null
        if (!posable(tableau)) return null
        return Tableau(
            graine, billeX, billeY, bouton.first, bouton.second, retenues, temps
        )
    }

    /**
     * Ajoute une piece si elle fait aller la bille plus loin, et rend le nouvel etat.
     *
     * L'echec n'est pas une erreur : une etape qui ne trouve rien laisse simplement la
     * machine telle quelle, et le tableau sera plus court. Mieux vaut un tableau a trois
     * pieces qui marche qu'un tableau a cinq qu'on n'a pas su batir.
     */
    private fun pousser(
        poses: List<Pose>,
        trace: Trace,
        billeX: Float,
        billeY: Float,
        hasard: Random
    ): Pair<List<Pose>, Trace>? {
        repeat(TENTATIVES) {
            val candidate = proposer(trace, hasard) ?: return@repeat
            if (!placable(candidate, poses)) return@repeat
            val essai = poses + candidate
            val apres = simuler(billeX, billeY, essai)
            if (apres.perdue) return@repeat
            if (apres.parcours < trace.parcours + PROGRES_MINIMUM) return@repeat
            if (!deplaceLArrivee(trace, apres)) return@repeat
            return essai to apres
        }
        return null
    }

    /** Vrai si la piece ajoutee a change l'endroit ou la bille finit sa course. */
    private fun deplaceLArrivee(avant: Trace, apres: Trace): Boolean {
        val a = avant.fin ?: return false
        val b = apres.fin ?: return false
        return hypot(b.x - a.x, b.y - a.y) > DEPLACEMENT_ARRIVEE
    }

    /**
     * Choisit un instant de la course, puis une piece qui sait agir sur cet instant.
     *
     * Les modules sont essayes dans un ordre tire au hasard : c'est ce qui evite que
     * tous les tableaux commencent par une rampe, ce qui etait exactement le defaut de
     * la premiere version.
     */
    private fun proposer(trace: Trace, hasard: Random): Pose? {
        val point = choisirPoint(trace, hasard) ?: return null
        val modules = MODULES.shuffled(hasard)
        for (module in modules) {
            val pose = module(point, hasard)
            if (pose != null) return pose
        }
        return null
    }

    /**
     * Un instant ou la bille bouge encore.
     *
     * On ecarte le tout debut — une piece posee sous la bille immobile ne fait rien de
     * plus que le sol — et on ecarte la fin, ou la bille est deja arretee.
     */
    private fun choisirPoint(trace: Trace, hasard: Random): Echantillon? {
        val vivants = trace.points.filter { it.t > 0.25f && it.vitesse > 0.9f }
        if (vivants.isEmpty()) return null
        // Biais vers la fin de la course : c'est la que la machine a besoin d'etre
        // prolongee. Le carre d'un tirage uniforme suffit a pencher franchement.
        val u = hasard.nextFloat()
        val index = ((1f - u * u) * (vivants.size - 1)).toInt().coerceIn(0, vivants.size - 1)
        return vivants[index]
    }

    // ── Les modules ──────────────────────────────────────────────────────────

    private val MODULES: List<(Echantillon, Random) -> Pose?> = listOf(
        ::moduleRampe,
        ::modulePlot,
        ::moduleTambour,
        ::moduleTremplin,
        ::moduleBascule,
        ::moduleVentilateur,
        ::moduleBloc
    )

    /** Une planche sous une bille qui tombe : elle la rattrape et la renvoie de cote. */
    private fun moduleRampe(pt: Echantillon, hasard: Random): Pose? {
        if (pt.vy > -0.8f || pt.y < 0.9f) return null
        val sens = pt.sens(if (hasard.nextBoolean()) 1f else -1f)
        val longueur = hasard.nextFloat() * 0.8f + 1.3f
        val pente = (hasard.nextFloat() * 14f + 16f) * sens
        return Pose(
            TypePiece.RAMPE,
            x = pt.x + sens * longueur * 0.3f,
            y = pt.y - 0.32f,
            reglage = pente,
            taille = longueur
        )
    }

    /** Un plot sous une chute : la bille repart de biais, et c'est imprevisible a l'oeil. */
    private fun modulePlot(pt: Echantillon, hasard: Random): Pose? {
        if (pt.vy > -1.6f || pt.y < 0.7f) return null
        val rayon = hasard.nextFloat() * 0.08f + 0.13f
        val decalage = (hasard.nextFloat() * 0.14f + 0.04f) * (if (hasard.nextBoolean()) 1f else -1f)
        return Pose(
            TypePiece.PLOT,
            x = pt.x + decalage,
            y = pt.y - 0.3f - rayon,
            taille = rayon
        )
    }

    /** Un tambour sous une chute franche : la bille remonte presque aussi haut. */
    private fun moduleTambour(pt: Echantillon, hasard: Random): Pose? {
        if (pt.vy > -2.6f || pt.y < 0.6f) return null
        val largeur = hasard.nextFloat() * 0.3f + 0.6f
        return Pose(
            TypePiece.TAMBOUR,
            x = pt.x + pt.vx * 0.05f,
            y = pt.y - 0.34f,
            taille = largeur
        )
    }

    /**
     * Un tremplin sous une chute : la bille repart **en avant**, pas en l'air.
     *
     * Le sens de la charniere est ce qui compte : le volet tourne autour d'elle, donc la
     * bille est chassee du cote oppose. On met donc la charniere derriere le sens de la
     * course.
     */
    private fun moduleTremplin(pt: Echantillon, hasard: Random): Pose? {
        if (pt.vy > -1.4f || pt.y < 1.0f) return null
        val bas = pt.y - 0.48f
        if (bas < 0.02f) return null
        val sens = pt.sens(if (hasard.nextBoolean()) 1f else -1f)
        val longueur = hasard.nextFloat() * 0.25f + 0.6f
        return Pose(
            TypePiece.TREMPLIN,
            x = pt.x - sens * longueur / 2f,
            y = bas,
            reglage = sens,
            taille = longueur
        )
    }

    /** Une bascule sous une chute : ce qui tombe d'un cote fait monter l'autre. */
    private fun moduleBascule(pt: Echantillon, hasard: Random): Pose? {
        if (pt.vy > -1.2f || pt.y < 0.9f) return null
        val bas = pt.y - 0.42f
        if (bas < 0.02f) return null
        val sens = pt.sens(if (hasard.nextBoolean()) 1f else -1f)
        val longueur = hasard.nextFloat() * 0.5f + 1f
        return Pose(
            TypePiece.BASCULE,
            x = pt.x - sens * longueur * 0.38f,
            y = bas,
            taille = longueur
        )
    }

    /**
     * Un ventilateur derriere la bille : il la pousse la ou la pente ne la mene pas.
     *
     * Le carter est place **en arriere de la course**, jamais devant : un obstacle scelle
     * en travers du chemin arreterait la bille au lieu de la pousser, et c'est la seule
     * facon de rater completement l'effet recherche.
     */
    private fun moduleVentilateur(pt: Echantillon, hasard: Random): Pose? {
        if (pt.vitesse < 1f) return null
        val vertical = hasard.nextInt(3) == 0
        if (vertical) {
            val bas = pt.y - 1.3f
            if (bas < 0.25f) return null
            return Pose(TypePiece.VENTILATEUR, x = pt.x, y = bas, reglage = 90f)
        }
        val sens = pt.sens(if (hasard.nextBoolean()) 1f else -1f)
        val recul = hasard.nextFloat() * 0.5f + 0.9f
        val y = pt.y.coerceAtLeast(0.25f)
        return Pose(
            TypePiece.VENTILATEUR,
            x = pt.x - sens * recul,
            y = y,
            reglage = if (sens > 0f) 0f else 180f
        )
    }

    /** Un bloc en travers : la bille bute, retombe, et repart ailleurs. */
    private fun moduleBloc(pt: Echantillon, hasard: Random): Pose? {
        if (abs(pt.vx) < 1.2f) return null
        val sens = pt.sens(1f)
        val cote = hasard.nextFloat() * 0.25f + 0.3f
        return Pose(
            TypePiece.BLOC,
            x = pt.x + sens * (0.4f + cote / 2f),
            y = pt.y + cote * 0.1f,
            taille = cote
        )
    }

    // ── La fin : le bouton ───────────────────────────────────────────────────

    /**
     * Termine la machine et pose le bouton. Rend la solution complete et la position du
     * bouton, ou `null` si aucune fin ne convient.
     *
     * Trois fins, essayees dans un ordre tire au hasard, parce qu'une fin toujours
     * identique se reconnait des le premier coup d'oeil et tue le casse-tete :
     *
     *  - **la poulie** : la bille tombe dans le godet et le contrepoids monte declencher
     *    le bouton, seule facon d'atteindre un bouton place en hauteur ;
     *  - **les dominos** : la ligne classique, qui demande de la precision au placement ;
     *  - **l'arrivee directe** : le bouton la ou la bille finit sa course.
     */
    private fun achever(
        poses: List<Pose>,
        trace: Trace,
        billeX: Float,
        billeY: Float,
        hasard: Random
    ): Pair<List<Pose>, Pair<Float, Float>>? {
        // Les deux vraies fins d'abord, dans un ordre tire au hasard ; l'arrivee directe
        // ensuite, et seulement si aucune des deux n'a pris.
        //
        // L'ordre n'est pas cosmetique. Une machine qui finit sur une ligne de dominos ou
        // sur un contrepoids qui monte a un bouton **qu'on ne peut pas atteindre
        // autrement** ; une machine qui finit la ou la bille s'arrete a un bouton que la
        // moitie des machines plus courtes atteindrait aussi. Preferer les premieres, c'est
        // preferer les tableaux ou chaque piece compte.
        val fins = listOf(::finPoulie, ::finDominos).shuffled(hasard) + listOf(::finDirecte)
        for (fin in fins) {
            val resultat = fin(poses, trace, hasard) ?: continue
            val essai = simulerAvecBouton(billeX, billeY, resultat.first, resultat.second)
            if (essai) return resultat
        }
        return null
    }

    /** La poulie : la bille tombe dans le godet, le contrepoids monte sur le bouton. */
    private fun finPoulie(
        poses: List<Pose>,
        trace: Trace,
        hasard: Random
    ): Pair<List<Pose>, Pair<Float, Float>>? {
        val hauteur = Pieces.POULIE_HAUTEUR
        val candidats = trace.points.filter { it.t > 0.4f && it.vy < -1.2f && it.y > hauteur }
        if (candidats.isEmpty()) return null
        val pt = candidats[hasard.nextInt(candidats.size)]

        // La margelle du godet se place [CHUTE_GODET] plus bas que la bille : assez pour
        // qu'elle soit franchement dedans, assez peu pour qu'elle n'ait pas le temps de
        // deriver loin. [Pieces.poulieGodetHaut] donne la cote, on ne la recalcule pas ici.
        val margelle = pt.y - CHUTE_GODET
        val bas = margelle - hauteur + 0.55f
        if (bas < 0.05f) return null

        // **On vise ou la bille sera, pas ou elle est.** Elle tombe de trente centimetres
        // en un quart de seconde, et a deux metres par seconde d'elan horizontal cela fait
        // cinquante centimetres de derive — deux fois la largeur du godet. Sans cette
        // correction, un candidat sur dix touchait le godet.
        val vol = sqrt(2f * CHUTE_GODET / 9.81f)
        val arrivee = pt.x + pt.vx * vol
        val x = arrivee + Pieces.POULIE_ECART
        val pose = Pose(TypePiece.POULIE, x = x, y = bas, taille = hauteur)
        if (!placable(pose, poses)) return null

        val boutonX = x + Pieces.POULIE_ECART
        val boutonBas = Pieces.poulieContrepoidsHaut(bas) - 0.02f
        return (poses + pose) to (boutonX to boutonBas)
    }

    /** La ligne de dominos, posee sur un tronçon ou la bille roule au sol. */
    private fun finDominos(
        poses: List<Pose>,
        trace: Trace,
        hasard: Random
    ): Pair<List<Pose>, Pair<Float, Float>>? {
        val candidats = trace.points.filter {
            it.t > 0.4f && it.y < 0.32f && abs(it.vx) > 1f
        }
        if (candidats.isEmpty()) return null
        val pt = candidats[hasard.nextInt(candidats.size)]
        val sens = pt.sens(1f)
        val nombre = hasard.nextInt(4, 8)
        val ecart = hasard.nextFloat() * 0.06f + 0.26f
        val premier = pt.x + sens * 0.5f
        val dernier = premier + sens * (nombre - 1) * ecart
        // Le dernier domino doit tomber dans la zone : sa pointe decrit un arc dont le
        // rayon est sa hauteur, donc on met le bouton d'un peu moins que ca.
        val boutonX = dernier + sens * 0.48f
        if (abs(boutonX) > 4.4f) return null
        val ligne = (0 until nombre).map {
            Pose(TypePiece.DOMINO, x = premier + sens * it * ecart, y = 0f)
        }
        for (d in ligne) if (!placable(d, poses)) return null
        return (poses + ligne) to (boutonX to 0f)
    }

    /** Le bouton la ou la bille s'arrete. */
    private fun finDirecte(
        poses: List<Pose>,
        trace: Trace,
        hasard: Random
    ): Pair<List<Pose>, Pair<Float, Float>>? {
        if (poses.size < 2) return null
        val fin = trace.fin ?: return null
        if (fin.vitesse > 0.6f) return null
        if (abs(fin.x) > 4.4f) return null
        return poses to (fin.x to (fin.y - 0.12f).coerceAtLeast(0f))
    }

    // ── Verification et elagage ──────────────────────────────────────────────

    /**
     * Retire une a une les pieces dont l'absence ne change rien.
     *
     * **C'est ce qui rend l'inventaire honnete.** Le generateur pose des pieces en
     * regardant la course de la bille, mais une piece posee a l'etape deux peut se
     * retrouver hors du chemin apres qu'on en a pose une a l'etape trois. La laisser dans
     * l'inventaire, c'est donner au joueur une piece dont il n'a aucun moyen de deviner
     * a quoi elle sert — parce qu'elle ne sert a rien.
     *
     * On procede de la derniere vers la premiere : retirer une piece tardive a moins de
     * chances de casser les suivantes, donc l'elagage converge plus vite.
     *
     * ## Et on repasse jusqu'a ce que plus rien ne parte
     *
     * Une seule passe ne suffit pas, et la raison est jolie : **la physique n'est pas
     * monotone.** Une piece peut etre indispensable dans un tableau a six pieces et
     * parfaitement inutile dans le tableau a quatre pieces qu'on obtient apres en avoir
     * retire deux autres — le trajet de la bille n'est plus le meme, et elle ne la touche
     * plus. Une passe unique laissait donc passer des pieces mortes, et le test qui verifie
     * qu'aucune piece n'est superflue le voyait tout de suite.
     *
     * La boucle s'arrete forcement : chaque tour qui change quelque chose retire au moins
     * une piece, et il n'y en a jamais plus d'une douzaine.
     */
    private fun elaguer(
        poses: List<Pose>,
        billeX: Float,
        billeY: Float,
        bouton: Pair<Float, Float>
    ): List<Pose> {
        var retenues = poses
        var encore = true
        while (encore) {
            encore = false
            var i = retenues.size - 1
            while (i >= 0) {
                if (retenues.size <= 1) break
                val sans = retenues.toMutableList().also { it.removeAt(i) }
                if (simulerAvecBouton(billeX, billeY, sans, bouton)) {
                    retenues = sans
                    encore = true
                }
                i--
            }
        }
        return retenues
    }

    /** Fait tourner une solution et rend le temps qu'elle a mis, ou `null` si elle perd. */
    private fun verifier(tableau: Tableau): Float? {
        val monde = monter(tableau, avecSolution = true)
        val temps = monde.derouler(PATIENCE)
        return if (monde.gagne) temps else null
    }

    /**
     * Vrai si le tableau se gagne sans poser une seule piece.
     *
     * Le garde-fou indispensable : si le decor seul suffisait, l'inventaire ne servirait
     * a rien et le generateur ne genererait qu'une animation.
     */
    private fun gagneSansRien(tableau: Tableau): Boolean {
        val monde = monter(tableau, avecSolution = false)
        monde.derouler(PATIENCE)
        return monde.gagne
    }

    /**
     * Vrai si le joueur peut effectivement poser la solution, une piece apres l'autre.
     *
     * Une solution que les regles de placement refusent est un tableau insoluble a la
     * main : soluble sur le papier, impossible au doigt, et absolument indiagnosticable
     * en jouant. La verification coute une partie montee a blanc, c'est-a-dire rien.
     */
    private fun posable(tableau: Tableau): Boolean {
        val partie = Partie(tableau)
        for (pose in tableau.solution) if (partie.poser(pose) != Refus.OK) return false
        return true
    }

    /** Vrai si cette solution-la, avec ce bouton-la, gagne. */
    private fun simulerAvecBouton(
        billeX: Float,
        billeY: Float,
        poses: List<Pose>,
        bouton: Pair<Float, Float>
    ): Boolean {
        val p = Plateau()
        p.poserBouton(x = bouton.first, bas = bouton.second)
        for (pose in poses) p.poser(pose.creer())
        p.poserBille(billeX, billeY)
        p.derouler(PATIENCE)
        return p.gagne
    }

    /** Vrai si la piece tient dans le tableau sans en heurter une autre. */
    private fun placable(pose: Pose, poses: List<Pose>): Boolean {
        val piece = pose.creer()
        if (!Placement.dansLeTableau(piece, Plateau.LARGEUR, Plateau.HAUTEUR)) return false
        val occupants = ArrayList<PhysBody>()
        for (p in poses) occupants.addAll(p.creer().corps)
        for (c in occupants) c.updateAabb()
        return !Placement.heurte(piece, occupants)
    }

    /**
     * Lache la bille sur une machine et note toute sa course.
     *
     * L'arret est decide par le moteur, pas par un chronometre : des que plus rien ne
     * bouge, la suite ne dira rien de plus. C'est ce qui rend la generation rapide malgre
     * le nombre d'essais — une machine qui echoue s'arrete au bout d'une seconde.
     */
    private fun simuler(
        billeX: Float,
        billeY: Float,
        poses: List<Pose>,
        duree: Float = PATIENCE
    ): Trace {
        val p = Plateau()
        for (pose in poses) p.poser(pose.creer())
        val bille = p.poserBille(billeX, billeY)

        val points = ArrayList<Echantillon>()
        var t = 0f
        var parcours = 0f
        var px = bille.x
        var py = bille.y
        var image = 0
        var perdue = false
        var immobileDepuis = 0f
        while (t < duree) {
            p.avancer(PAS)
            t += PAS
            image++
            parcours += hypot(bille.x - px, bille.y - py)
            px = bille.x
            py = bille.y
            if (image % 4 == 0) {
                points.add(Echantillon(t, bille.x, bille.y, bille.vx, bille.vy))
            }
            if (p.billePerdue()) {
                perdue = true
                break
            }
            immobileDepuis = if (bille.speedSq < 0.0025f) immobileDepuis + PAS else 0f
            if (t > 0.6f && immobileDepuis > REPOS_BILLE) break
        }
        points.add(Echantillon(t, bille.x, bille.y, bille.vx, bille.vy))
        return Trace(points, perdue, parcours)
    }
}
