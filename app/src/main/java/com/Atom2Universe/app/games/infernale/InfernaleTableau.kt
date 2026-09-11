package com.Atom2Universe.app.games.infernale

import com.Atom2Universe.app.games.physics.PhysBody
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
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
     * Reglage libre, dont le sens depend du type : la **pente** d'une rampe en degres, la
     * **direction** du jet d'un ventilateur. Les pieces sans reglage l'ignorent.
     */
    val reglage: Float = 0f,
    /**
     * La piece est-elle retournee ?
     *
     * Un reglage separe et pas un signe glisse dans [reglage], parce que ce sont deux
     * questions distinctes : « de combien » et « de quel cote ». Les melanger obligeait le
     * tremplin a detourner la pente pour y coder son cote de charniere, et laissait la
     * poulie sans aucun moyen d'etre retournee. Avec deux champs, la regle se dit en une
     * phrase — **le curseur regle la quantite, le miroir regle le cote** — et elle vaut
     * pour toutes les pieces.
     */
    val miroir: Boolean = false,
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
    /** -1 quand la piece est retournee, +1 sinon. */
    private val cote: Float get() = if (miroir) -1f else 1f

    fun creer(): Piece = when (type) {
        TypePiece.RAMPE -> Pieces.rampe(
            x, y,
            pente = reglage * cote,
            longueur = taille(Pieces.RAMPE_LONGUEUR)
        )
        TypePiece.PLOT -> Pieces.plot(x, y, rayon = taille(Pieces.PLOT_RAYON))
        TypePiece.BLOC -> Pieces.bloc(
            x, y,
            largeur = taille(Pieces.BLOC_COTE),
            hauteur = taille(Pieces.BLOC_COTE)
        )
        TypePiece.DOMINO -> Pieces.domino(x, y, hauteur = taille(Pieces.DOMINO_HAUTEUR))
        TypePiece.BASCULE -> Pieces.bascule(x, y, longueur = taille(Pieces.BASCULE_LONGUEUR))
        TypePiece.TREMPLIN -> Pieces.tremplin(
            x, y,
            longueur = taille(Pieces.TREMPLIN_LONGUEUR),
            sens = cote
        )
        TypePiece.VENTILATEUR -> Pieces.ventilateur(
            x, y,
            direction = if (miroir) 180f - reglage else reglage,
            cote = taille(Pieces.VENTILATEUR_COTE)
        )
        TypePiece.TAMBOUR -> Pieces.tambour(x, y, largeur = taille(Pieces.TAMBOUR_LARGEUR))
        TypePiece.POULIE -> Pieces.poulie(
            x, y,
            hauteur = taille(Pieces.POULIE_HAUTEUR),
            sens = cote
        )
        TypePiece.BILLE -> Pieces.bille(x, y, rayon = taille(Pieces.BILLE_RAYON))
        TypePiece.TAPIS -> Pieces.tapis(
            x, y,
            longueur = taille(Pieces.TAPIS_LONGUEUR),
            sens = cote
        )
    }

    private fun taille(defaut: Float): Float = if (taille > 0f) taille else defaut
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
     * Vrai si la piece tient entierement dans la zone constructible.
     *
     * C'est le plateau entier, pas ce que la camera montre : on se deplace a deux doigts,
     * donc rien n'oblige a batir dans la fenetre de depart.
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
/**
 * Ce que le joueur a dans les mains, et c'est le meme jeu a tous les tableaux.
 *
 * ## Pourquoi tout donner
 *
 * La version precedente distribuait **exactement** les pieces d'une solution que le
 * generateur avait verifiee. C'etait defendable sur le papier — aucune piece inutile,
 * aucun tableau insoluble — et c'etait une erreur de conception : neuf dixiemes du travail
 * du generateur servaient a prouver une chose que le joueur ne voit jamais, et le prix en
 * etait un jeu ou l'on devine la solution d'un autre au lieu d'inventer la sienne. Recevoir
 * « une bascule et six dominos » **est** l'indice, et un indice qu'on ne peut pas refuser.
 *
 * Avec toute la panoplie a chaque tableau, le probleme redevient celui qu'on voulait poser :
 * la bille est ici, le bouton est la, debrouillez-vous. Deux joueurs ne rendront pas la meme
 * machine, ce qui est la definition meme d'un bac a sable reussi.
 *
 * ## Pourquoi pas l'infini
 *
 * Les comptes sont larges — assez pour qu'on ne les sente jamais — mais finis, pour deux
 * raisons qui n'ont rien a voir avec la difficulte. La reserve doit afficher un nombre ;
 * et trente dominos qui se touchent sont le pire cas du solveur, cf. le cout du chateau du
 * trebuchet. Ce qui recompense l'economie de pieces, c'est le bareme en etoiles, pas la
 * penurie.
 */
object Panoplie {

    /** L'inventaire, identique pour tous les tableaux. */
    val COMPLET: Map<TypePiece, Int> = mapOf(
        // Douze rampes : de quoi batir un toboggan d'un bout a l'autre des seize metres du
        // plateau. C'est la seule cote qui ait ete calculee, parce que c'est la piece a
        // tout faire — scellee, donc elle tient en l'air ou l'on veut, et le bouton est
        // toujours plus bas que la bille.
        TypePiece.RAMPE to 12,
        TypePiece.PLOT to 6,
        TypePiece.BLOC to 6,
        TypePiece.DOMINO to 14,
        TypePiece.BASCULE to 3,
        TypePiece.TREMPLIN to 3,
        TypePiece.VENTILATEUR to 4,
        TypePiece.TAMBOUR to 3,
        TypePiece.POULIE to 2,
        TypePiece.BILLE to 3,
        TypePiece.TAPIS to 3
    )
}

/**
 * Un tableau : d'ou part la bille, ou est le bouton, et rien d'autre.
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
     * Hauteur du socle qui porte le bouton, ou zero s'il est a meme le sol.
     *
     * Le socle est un vrai corps scelle, pas un decor peint : un bouton qui flotterait en
     * l'air devant un pilier dessine se laisserait traverser par la bille, et le joueur y
     * verrait a juste titre un bug. Il est aussi ce qui rend un bouton en hauteur
     * interessant — il faut poser la bille **sur** quelque chose, pas seulement l'amener
     * quelque part.
     */
    val socleHauteur: Float = 0f
) {
    /** Ce que le joueur recoit : toute la panoplie, a chaque tableau. */
    val inventaire: Map<TypePiece, Int> get() = Panoplie.COMPLET

    /**
     * Le nombre de pieces qu'on attend d'une machine soignee — le « par » du parcours.
     *
     * Il vaut a peu pres ce que coute un toboggan de rampes entre la bille et le bouton,
     * plus une. Ce n'est pas un objectif impose : c'est l'echelle du bareme en etoiles, qui
     * a remplace le comptage des essais. Recommencer autant qu'on veut ne coute plus rien,
     * et bien faire du premier coup ne rapporte plus rien non plus — ce qu'on note, c'est
     * la machine, pas la patience.
     */
    val par: Int = (ceil(hypot(boutonX - billeX, billeY - boutonBas) / 1.5f).toInt() + 1)
        .coerceIn(3, 10)

    // ── Ou l'on batit, et ce qu'on voit en arrivant ──────────────────────────
    //
    // **Ce sont deux choses differentes, et les confondre a coute cher.** Elles n'en
    // faisaient qu'une : un cadre calcule autour de la bille et du bouton, qui servait a la
    // fois de fenetre d'affichage et de limite de construction. Tant que la vue montrait
    // tout d'un coup, c'etait defendable. Des lors qu'on peut zoomer et se deplacer a deux
    // doigts, ca ne l'est plus : brider la construction a ce que la camera montrait au
    // depart reviendrait a offrir un terrain de seize metres et a en interdire onze.
    //
    // On batit donc **partout sur le plateau**, et la camera se contente de s'ouvrir sur ce
    // qui compte.

    /** Bord gauche de la zone constructible. */
    val cadreMinX: Float get() = -Plateau.LARGEUR / 2f

    /** Bord droit de la zone constructible. */
    val cadreMaxX: Float get() = Plateau.LARGEUR / 2f

    /** Plafond de la zone constructible. */
    val cadreMaxY: Float get() = Plateau.HAUTEUR

    /** Bord gauche de la vue au chargement. */
    val vueMinX: Float

    /** Bord droit de la vue au chargement. */
    val vueMaxX: Float

    /** Plafond de la vue au chargement. */
    val vueMaxY: Float

    init {
        var lo = minOf(billeX, boutonX) - MARGE_VUE
        var hi = maxOf(billeX, boutonX) + MARGE_VUE
        if (hi - lo < LARGEUR_MINI) {
            val centre = (lo + hi) / 2f
            lo = centre - LARGEUR_MINI / 2f
            hi = centre + LARGEUR_MINI / 2f
        }
        vueMinX = lo.coerceAtLeast(cadreMinX)
        vueMaxX = hi.coerceAtMost(cadreMaxX)
        // De la place au-dessus de la bille : c'est la qu'on pose le premier aiguillage, et
        // une vue qui s'arreterait a la bille obligerait a dezoomer avant le premier geste.
        vueMaxY = (billeY + 1.4f).coerceIn(3f, cadreMaxY)
    }

    private companion object {
        const val MARGE_VUE = 1.8f
        const val LARGEUR_MINI = 7f
    }
}

/**
 * La fabrique de tableaux : elle place la bille, le bouton, et s'arrete la.
 *
 * ## Ce qu'elle ne fait plus, et pourquoi c'est mieux
 *
 * Elle construisait une machine complete a la place du joueur, la faisait tourner dans le
 * moteur, elaguait ce qui ne servait a rien, et ne livrait que l'inventaire de ce qui
 * restait. Trois cents lignes, des secondes de calcul par niveau, et un jeu qui posait en
 * creux la mauvaise question : « retrouvez ce que la machine a trouve ». On lui prefere
 * desormais la bonne : « la bille est ici, le bouton est la ».
 *
 * ## La solubilite n'est plus une chose a prouver
 *
 * Elle etait le probleme central de l'ancienne fabrique, qui montait et simulait une
 * machine entiere pour s'en assurer. Avec toute la panoplie dans les mains du joueur, elle
 * cesse d'etre un probleme : huit rampes scellees, qui tiennent en l'air ou l'on veut,
 * descendent de n'importe ou a n'importe ou. La fabrique se contente donc d'une regle de
 * dessin — **le bouton est toujours plus bas que la bille** — qui evite de demander la
 * seule chose que la panoplie fasse mal, monter.
 *
 * Reste un unique garde-fou mesure : le tableau vide ne doit pas se gagner tout seul. Il
 * coute une simulation, il ne rate presque jamais, et il couvre le seul accident que le
 * dessin ne couvre pas.
 */
object Tableaux {

    /** Ecart horizontal minimal entre la bille et le bouton. */
    private const val ECART_MIN = 3.4f

    /** Ecart horizontal maximal. */
    private const val ECART_MAX = 9f

    /** De combien le bouton est au moins plus bas que la bille : on ne demande pas de monter. */
    private const val DENIVELE_MIN = 1.3f

    /** Duree laissee au tableau vide pour prouver qu'il ne se gagne pas tout seul. */
    private const val PATIENCE = 8f

    /**
     * Le tableau du niveau [niveau], numerote a partir de 1.
     *
     * La difficulte ne monte pas en retirant des pieces — le joueur les a toutes, toujours.
     * Elle monte par la **geometrie** : le bouton s'eloigne, et il finit par se percher sur
     * un socle. Un bouton a meme le sol se gagne en faisant rouler la bille jusqu'a lui ;
     * un bouton a deux metres de haut demande de la poser **sur** quelque chose, ce qui est
     * un autre probleme.
     */
    fun pourNiveau(niveau: Int): Tableau {
        val avance = ((niveau - 1) / 9f).coerceAtMost(1f)
        return generer(
            graine = niveau.toLong() * 7919L,
            ecartMin = ECART_MIN + avance * 3.4f,
            // Un socle une fois sur deux a partir du niveau quatre, jamais avant : le
            // premier tableau doit s'expliquer tout seul.
            socle = niveau >= 4 && niveau % 2 == 0,
            hauteurSocle = 1f + avance * 2f
        )
    }

    /**
     * Tire un tableau. Le meme nombre rend toujours le meme : c'est ce qui permet de
     * partager un tableau par son seul numero.
     */
    fun generer(
        graine: Long,
        ecartMin: Float = ECART_MIN,
        socle: Boolean = false,
        hauteurSocle: Float = 1.4f
    ): Tableau {
        val hasard = Random(graine)
        // La bille part d'un cote ou de l'autre, tire au sort : sans ca, tous les tableaux
        // se lisent de gauche a droite et se ressemblent au premier coup d'oeil.
        val sens = if (hasard.nextBoolean()) 1f else -1f

        val ecart = (ecartMin + hasard.nextFloat() * 2f).coerceAtMost(ECART_MAX)
        val billeY = 4.8f + hasard.nextFloat() * 1.4f

        // Le socle ne monte jamais assez haut pour approcher la bille : sinon on demanderait
        // de la faire monter, ce que la panoplie ne sait faire que sur soixante-dix
        // centimetres, avec la poulie.
        val socleH = if (socle) hauteurSocle.coerceAtMost(billeY - DENIVELE_MIN) else 0f

        // On centre l'ensemble avant de l'ecarter : le tableau occupe le cadre au lieu de
        // se tasser dans un coin.
        val billeX = -sens * ecart / 2f + (hasard.nextFloat() - 0.5f) * 0.8f
        val bord = Plateau.LARGEUR / 2f - 0.6f
        var boutonX = (billeX + sens * ecart).coerceIn(-bord, bord)

        var tableau = Tableau(graine, billeX, billeY, boutonX, socleH, socleH)
        // Le garde-fou : un tableau qui se gagne sans poser une seule piece n'est pas un
        // tableau. On decale le bouton et on recommence, ce qui n'arrive presque jamais.
        var essais = 0
        while (gagneSansRien(tableau) && essais < 8) {
            essais++
            boutonX = (boutonX + sens * 0.5f).coerceIn(-bord, bord)
            tableau = Tableau(graine, billeX, billeY, boutonX, socleH, socleH)
        }
        return tableau
    }

    /** Monte un monde a partir d'un tableau : le sol, le socle, le bouton, la bille. */
    fun monter(tableau: Tableau): Plateau {
        val p = Plateau()
        if (tableau.socleHauteur > 0f) p.poserSocle(tableau.boutonX, tableau.socleHauteur)
        p.poserBouton(x = tableau.boutonX, bas = tableau.boutonBas)
        p.poserBille(x = tableau.billeX, y = tableau.billeY)
        return p
    }

    private fun gagneSansRien(tableau: Tableau): Boolean {
        val monde = monter(tableau)
        monde.derouler(PATIENCE)
        return monde.gagne
    }
}
