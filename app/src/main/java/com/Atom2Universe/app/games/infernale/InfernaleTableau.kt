package com.Atom2Universe.app.games.infernale

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
    /** Reglage libre, dont le sens depend du type : la pente d'une rampe, par exemple. */
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
        TypePiece.TREMPLIN -> Pieces.tremplin(x, y, longueur = cote(Pieces.TREMPLIN_LONGUEUR))
    }

    private fun cote(defaut: Float): Float = if (taille > 0f) taille else defaut
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
    val solution: List<Pose>
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
}

/**
 * La fabrique de tableaux.
 *
 * ## Comment on garantit qu'un tableau est soluble
 *
 * Tirer un decor au hasard puis se demander s'il a une solution est un probleme
 * qu'on ne sait pas resoudre : il faudrait explorer tous les placements possibles de
 * toutes les pieces. On fait donc l'inverse, et c'est la vieille ruse des generateurs
 * de niveaux : **on construit la solution d'abord, on la verifie en la faisant tourner,
 * et on la retire.** Ce qui reste est un tableau vide dont on sait, parce qu'on l'a vu,
 * qu'au moins un placement gagne — et l'inventaire remis au joueur est precisement
 * celui de ce placement.
 *
 * La verification n'est pas une formalite : elle fait tourner la machine pour de vrai,
 * avec le meme moteur et le meme pas de temps que la partie. Un tableau accepte ici a
 * donc deja gagne une fois. Cela coute une poignee de secondes simulees par essai, soit
 * quelques millisecondes reelles.
 */
object Tableaux {

    /** Duree laissee a une solution candidate pour faire ses preuves. */
    private const val PATIENCE = 10f

    /**
     * Tire un tableau soluble a partir de [graine], ou rend `null` si aucun des
     * [essais] candidats n'a gagne.
     *
     * Le meme nombre rend toujours le meme tableau : c'est ce qui permet de partager un
     * tableau par son seul numero.
     */
    fun generer(graine: Long, essais: Int = 40): Tableau? {
        val hasard = Random(graine)
        repeat(essais) {
            val candidat = tirer(graine, hasard)
            if (verifier(candidat)) return candidat
        }
        return null
    }

    /**
     * Le premier tableau soluble a partir de [graine], en essayant les graines
     * suivantes si celle-la ne donne rien. Ne rend jamais `null`.
     */
    fun genererSurement(graine: Long): Tableau {
        var g = graine
        repeat(64) {
            generer(g)?.let { return it }
            g++
        }
        error("aucun tableau soluble autour de la graine $graine")
    }

    /**
     * Monte un monde a partir d'un tableau. [avecSolution] pose les pieces gagnantes —
     * c'est ce dont se sert la verification, et ce qui permettra un jour de montrer la
     * reponse.
     */
    fun monter(tableau: Tableau, avecSolution: Boolean = false): Plateau {
        val p = Plateau()
        p.poserBouton(x = tableau.boutonX, bas = tableau.boutonBas)
        if (avecSolution) for (pose in tableau.solution) p.poser(pose.creer())
        p.poserBille(x = tableau.billeX, y = tableau.billeY)
        return p
    }

    /** Fait tourner une solution candidate et dit si elle gagne. */
    private fun verifier(tableau: Tableau): Boolean =
        monter(tableau, avecSolution = true).let { monde ->
            monde.derouler(PATIENCE)
            monde.gagne
        }

    /**
     * Un candidat : une rampe qui lance la bille, et une ligne de dominos qui finit
     * dans la zone du bouton.
     *
     * C'est volontairement **une seule forme de machine**, avec des cotes qui varient.
     * Un generateur qui saurait melanger bascules, tremplins et plots viendra apres, et
     * il viendra plus facilement une fois qu'on aura regarde des dizaines de tableaux de
     * celle-ci tourner. Mieux vaut une forme dont on sait qu'elle marche que cinq dont
     * on n'est sur d'aucune.
     */
    private fun tirer(graine: Long, hasard: Random): Tableau {
        val nombre = hasard.nextInt(4, 8)
        val ecart = hasard.nextFloat() * 0.06f + 0.26f
        val boutonX = hasard.nextFloat() * 1.4f + 0.3f

        // Le dernier domino doit tomber dans la zone : sa pointe decrit un arc dont le
        // rayon est sa hauteur, donc on le recule d'un peu moins que ca.
        val dernierX = boutonX - 0.48f
        val premierX = dernierX - (nombre - 1) * ecart

        val pente = hasard.nextFloat() * 8f + 18f
        val rampeLongueur = hasard.nextFloat() * 0.6f + 1.6f
        // La rampe se termine assez loin devant le premier domino pour que la bille ait
        // le temps de retomber au sol et d'arriver en roulant, pas en cloche.
        val rampeX = premierX - 1.35f
        val rampeY = hasard.nextFloat() * 0.25f + 0.55f

        val poses = ArrayList<Pose>(nombre + 1)
        poses += Pose(TypePiece.RAMPE, rampeX, rampeY, reglage = pente, taille = rampeLongueur)
        for (i in 0 until nombre) poses += Pose(TypePiece.DOMINO, premierX + i * ecart, 0f)

        // La bille est lachee au-dessus du bout haut de la rampe, legerement en retrait
        // pour qu'elle s'y pose au lieu de la rater.
        val demi = rampeLongueur / 2f
        val radian = Math.toRadians(pente.toDouble())
        val hautX = rampeX - demi * Math.cos(radian).toFloat()
        val hautY = rampeY + demi * Math.sin(radian).toFloat()

        return Tableau(
            graine = graine,
            billeX = hautX + 0.18f,
            billeY = hautY + 0.55f,
            boutonX = boutonX,
            boutonBas = 0f,
            solution = poses
        )
    }
}
