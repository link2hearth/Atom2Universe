package com.Atom2Universe.app.games.infernale

import com.Atom2Universe.app.games.physics.PhysBody
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/** Un point du tableau, en metres. */
data class Point(val x: Float, val y: Float)

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
     * Reglage libre, dont le sens depend du type : la **pente** d'une rampe, d'un tapis ou
     * d'un tambour en degres, la **direction** du jet d'un ventilateur. Les autres
     * l'ignorent.
     */
    val reglage: Float = 0f,
    /**
     * Cote principale de la piece — sa longueur, son rayon, sa hauteur. Zero prend celle
     * par defaut.
     *
     * **Elle fait partie de la pose, et ce n'est pas un detail de confort.** Une pose qui ne
     * decrit pas entierement sa piece est une pose qui ment : le jour ou le generateur
     * calculait ou lacher la bille pour une rampe de deux metres pendant que `creer` en
     * fabriquait une de un metre cinquante, aucun tableau n'etait soluble.
     */
    val taille: Float = 0f,
    /** Seconde cote, pour les pieces qui en ont deux. Le bloc seul s'en sert : sa hauteur. */
    val taille2: Float = 0f,
    /**
     * La piece est-elle retournee ?
     *
     * Reserve a ce qu'aucun angle ne dit : le sens de marche d'un tapis, le cote de la
     * charniere d'un tremplin, lequel des deux plateaux d'une poulie porte le godet. Une
     * rampe, elle, n'en a pas besoin — son angle dit deja tout, et le miroir n'y voudrait
     * rien dire.
     */
    val miroir: Boolean = false
) {
    /** -1 quand la piece est retournee, +1 sinon. */
    private val cote: Float get() = if (miroir) -1f else 1f

    fun creer(): Piece = when (type) {
        TypePiece.RAMPE -> Pieces.rampe(
            x, y,
            pente = reglage,
            longueur = cote(Pieces.RAMPE_LONGUEUR)
        )
        TypePiece.PLOT -> Pieces.plot(x, y, rayon = cote(Pieces.PLOT_RAYON))
        TypePiece.BLOC -> Pieces.bloc(
            x, y,
            largeur = cote(Pieces.BLOC_COTE),
            hauteur = if (taille2 > 0f) taille2 else cote(Pieces.BLOC_COTE)
        )
        TypePiece.DOMINO -> Pieces.domino(x, y, hauteur = cote(Pieces.DOMINO_HAUTEUR))
        TypePiece.BASCULE -> Pieces.bascule(x, y, longueur = cote(Pieces.BASCULE_LONGUEUR))
        TypePiece.TREMPLIN -> Pieces.tremplin(
            x, y,
            longueur = cote(Pieces.TREMPLIN_LONGUEUR),
            sens = this.cote
        )
        TypePiece.VENTILATEUR -> Pieces.ventilateur(
            x, y,
            direction = reglage,
            cote = cote(Pieces.VENTILATEUR_COTE)
        )
        TypePiece.TAMBOUR -> Pieces.tambour(
            x, y,
            largeur = cote(Pieces.TAMBOUR_LARGEUR),
            pente = reglage
        )
        TypePiece.POULIE -> Pieces.poulie(
            x, y,
            hauteur = cote(Pieces.POULIE_HAUTEUR),
            sens = this.cote
        )
        TypePiece.BILLE -> Pieces.bille(x, y, rayon = cote(Pieces.BILLE_RAYON))
        TypePiece.TAPIS -> Pieces.tapis(
            x, y,
            longueur = cote(Pieces.TAPIS_LONGUEUR),
            pente = reglage,
            sens = this.cote
        )
        TypePiece.TORCHE -> Pieces.torche(x, y)
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
 * Un tableau : d'ou part la bille, et ou est le bouton. Rien d'autre.
 *
 * Il ne contient aucun corps : c'est une recette, pas un monde. On en monte autant de
 * mondes qu'on veut, ce qui permet de rejouer un essai sans rien reinitialiser.
 *
 * **Il n'y a plus d'inventaire.** Il y en a eu un, taille sur une solution verifiee, puis
 * un autre, large mais compte. Les deux se sont reveles etre le meme malentendu : ce qu'on
 * construit ici est un bac a sable, et compter les pieces d'un bac a sable n'apporte rien
 * qu'un agacement. On pose ce qu'on veut, autant qu'on veut.
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
    val socleHauteur: Float = 0f,
    /**
     * Ou se perche la bille temoin, et a quelle hauteur est le sommet de son pilier. Une
     * hauteur nulle veut dire qu'il n'y a pas de temoin : c'est un tableau ordinaire, ou
     * n'importe quelle bille gagne.
     */
    val temoinX: Float = 0f,
    val temoinHaut: Float = 0f,
    /**
     * Les anneaux a franchir, dans l'ordre, avant que le bouton compte. Vide pour un tableau
     * ordinaire.
     */
    val anneaux: List<Point> = emptyList(),
    /**
     * Combien de secondes la bille doit **rester** dans la zone du bouton, ou zero si
     * l'effleurer suffit. Un tableau a tenir a aussi sa cuvette.
     */
    val tenir: Float = 0f,
    /** Temps accorde apres le lancement, en secondes, ou zero s'il n'y a pas de limite. */
    val limite: Float = 0f
) {
    /** Vrai quand le bouton ne reconnait que la bille temoin, et pas celle du depart. */
    val avecTemoin: Boolean get() = temoinHaut > 0f

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
     * Ce que demande un niveau : le verbe du tableau, qui change toutes les quelques
     * parties pour que deux niveaux voisins ne se ressemblent pas.
     *
     * Le cycle fait six niveaux et chaque verbe y arrive **un par un** — jamais deux
     * nouveautes d'un coup. A partir du niveau 3 : bille doree (3), anneau (4), bouton sur
     * socle (5), doree plus anneau (6), tenir (7), chrono (8), puis ca recommence en plus dur.
     */
    class Verbes(
        val temoin: Boolean,
        val anneaux: Int,
        val socle: Boolean,
        val tenir: Boolean,
        val chrono: Boolean
    )

    fun verbes(niveau: Int): Verbes {
        val k = niveau % 6
        return Verbes(
            temoin = niveau >= 3 && niveau % 3 == 0,
            anneaux = when {
                niveau >= 6 && k == 0 -> if (niveau >= 12) 2 else 1
                niveau >= 4 && k == 4 -> if (niveau >= 16) 2 else 1
                else -> 0
            },
            socle = niveau >= 5 && k == 5,
            tenir = niveau >= 7 && k == 1,
            chrono = niveau >= 8 && k == 2
        )
    }

    /**
     * Le tableau du niveau [niveau], numerote a partir de 1.
     *
     * La difficulte ne monte pas en retirant des pieces — le joueur les a toutes, toujours.
     * Elle monte par la **geometrie** : le bouton s'eloigne, se perche sur un socle, la tenue
     * s'allonge, le temps se resserre. Chaque verbe pose une question differente : un bouton
     * a meme le sol se gagne en y faisant rouler la bille, un bouton perche demande de la
     * poser **sur** quelque chose, un anneau demande de passer **par** un point, tenir demande
     * de s'**arreter**.
     */
    fun pourNiveau(niveau: Int): Tableau {
        val avance = ((niveau - 1) / 9f).coerceAtMost(1f)
        val v = verbes(niveau)
        return generer(
            graine = niveau.toLong() * 7919L,
            ecartMin = ECART_MIN + avance * 3.4f,
            socle = v.socle,
            hauteurSocle = 1f + avance * 2f,
            temoin = v.temoin,
            anneaux = v.anneaux,
            tenir = if (v.tenir) 2.5f + avance else 0f,
            chrono = v.chrono,
            tension = avance
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
        hauteurSocle: Float = 1.4f,
        temoin: Boolean = false,
        anneaux: Int = 0,
        tenir: Float = 0f,
        chrono: Boolean = false,
        tension: Float = 0f
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

        // Le temoin se perche entre la bille et le bouton, nettement plus bas que le depart :
        // la regle de dessin reste « on ne demande jamais de monter », pour les deux billes.
        val temoinX = billeX + sens * ecart * 0.4f
        val temoinHaut = if (temoin) (billeY - 1.6f).coerceIn(1.6f, 3.4f) else 0f

        // Les anneaux jalonnent la descente : entre le depart (ou le perchoir du temoin, qui
        // est celui qui doit les passer) et le bouton, de plus en plus bas. Toujours plus bas
        // que ce qui les precede — on ne demande jamais de monter.
        val departX = if (temoin) temoinX else billeX
        val departY = if (temoin) temoinHaut else billeY

        fun bati(bx: Float): Tableau {
            val jalons = (1..anneaux).map { i ->
                val f = i / (anneaux + 1f)
                Point(
                    x = (departX + (bx - departX) * f).coerceIn(-bord, bord),
                    y = (departY * (1f - f)).coerceAtLeast(1.3f)
                )
            }
            val chronometre = if (chrono) limiteDe(departY, abs(bx - departX), tension) else 0f
            return Tableau(
                graine, billeX, billeY, bx, socleH, socleH, temoinX, temoinHaut,
                jalons, tenir, chronometre
            )
        }

        var tableau = bati(boutonX)
        // Le garde-fou : un tableau qui se gagne sans poser une seule piece n'est pas un
        // tableau. On decale le bouton et on recommence, ce qui n'arrive presque jamais.
        var essais = 0
        while (gagneSansRien(tableau) && essais < 8) {
            essais++
            boutonX = (boutonX + sens * 0.5f).coerceIn(-bord, bord)
            tableau = bati(boutonX)
        }
        return tableau
    }

    /**
     * Le temps qu'on accorde : une fraction genereuse de ce que mettrait une bille qui
     * tomberait presque droit, plus une marge, et qui se resserre avec le niveau. Ce n'est
     * pas mesure sur des machines jouees — c'est un point de depart a regler sur appareil.
     */
    private fun limiteDe(denivele: Float, ecart: Float, tension: Float): Float {
        val chute = sqrt(2f * denivele / 9.81f)
        val plancher = chute + ecart / 6f
        return (plancher * (2.4f - tension * 0.6f) + 2.5f).coerceIn(4f, 10f)
    }

    /** Monte un monde a partir d'un tableau : le sol, le socle, le bouton, la bille. */
    fun monter(tableau: Tableau): Plateau {
        val p = Plateau()
        if (tableau.socleHauteur > 0f) p.poserSocle(tableau.boutonX, tableau.socleHauteur)
        val bouton = p.poserBouton(
            x = tableau.boutonX, bas = tableau.boutonBas,
            largeur = if (tableau.tenir > 0f) 0.5f else 0.3f
        )
        bouton.duree = tableau.tenir
        if (tableau.tenir > 0f) p.poserCuvette(tableau.boutonX)
        for (a in tableau.anneaux) p.poserAnneau(a.x, a.y)
        p.poserBille(x = tableau.billeX, y = tableau.billeY)
        if (tableau.avecTemoin) p.poserTemoin(x = tableau.temoinX, haut = tableau.temoinHaut)
        return p
    }

    private fun gagneSansRien(tableau: Tableau): Boolean {
        val monde = monter(tableau)
        monde.derouler(PATIENCE)
        return monde.gagne
    }
}
