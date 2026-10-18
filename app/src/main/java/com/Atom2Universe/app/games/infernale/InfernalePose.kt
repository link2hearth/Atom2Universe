package com.Atom2Universe.app.games.infernale

import com.Atom2Universe.app.games.physics.PhysBody

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
    /**
     * Seconde cote, pour les pieces qui en ont deux : la hauteur d'un bloc, ou le decalage du
     * centre d'une bascule par rapport a son pied.
     */
    val taille2: Float = 0f,
    /**
     * La piece est-elle retournee ?
     *
     * Reserve a ce qu'aucun angle ne dit : le sens de marche d'un tapis, le cote de la
     * charniere d'un tremplin, lequel des deux plateaux d'une poulie porte le godet. Une
     * rampe, elle, n'en a pas besoin — son angle dit deja tout, et le miroir n'y voudrait
     * rien dire.
     */
    val miroir: Boolean = false,
    /**
     * Puissance du souffle d'un ventilateur, en newtons a la bouche. Zero prend la valeur par
     * defaut. Seul le ventilateur s'en sert.
     */
    val force: Float = 0f
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
        TypePiece.BASCULE -> Pieces.bascule(
            x, y,
            longueur = cote(Pieces.BASCULE_LONGUEUR),
            decalage = taille2
        )
        TypePiece.TREMPLIN -> Pieces.tremplin(
            x, y,
            longueur = cote(Pieces.TREMPLIN_LONGUEUR),
            sens = this.cote
        )
        TypePiece.VENTILATEUR -> Pieces.ventilateur(
            x, y,
            direction = reglage,
            cote = cote(Pieces.VENTILATEUR_COTE),
            poussee = if (force > 0f) force else Pieces.SOUFFLE_POUSSEE
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
        TypePiece.BALLON -> Pieces.ballon(x, y, rayon = cote(Pieces.BALLON_RAYON))
        TypePiece.PENDULE -> Pieces.pendule(
            x, y,
            longueur = cote(Pieces.PENDULE_LONGUEUR),
            angle = reglage
        )
        TypePiece.AIMANT -> Pieces.aimant(
            x, y,
            force = if (force > 0f) force else Pieces.forceParDefaut(TypePiece.AIMANT),
            repousse = miroir
        )
        TypePiece.CANON -> Pieces.canon(
            x, y,
            direction = reglage,
            vitesse = Pieces.canonVitesse(if (force > 0f) force else Pieces.forceParDefaut(TypePiece.CANON)),
            auDepart = miroir
        )
        TypePiece.PIC -> Pieces.pic(x, y, direction = reglage)
        TypePiece.PLAQUE -> Pieces.plaque(x, y, largeur = cote(Pieces.PLAQUE_LARGEUR), continu = miroir)
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
 * Un lien entre une plaque de pression et une **cible** — un canon, un ventilateur — par
 * **rang** de pose : quand la plaque s'enfonce, le canon tire, le ventilateur se met a souffler.
 *
 * Un lien est une relation entre deux poses, pas un champ de l'une d'elles : une plaque peut
 * commander plusieurs canons, un canon peut etre commande par plusieurs plaques. Les rangs
 * sont ceux de [Partie.placees] ; retirer une pose renumerote ceux qui la suivent.
 */
data class Lien(val plaque: Int, val cible: Int)

/** Ce que le joueur peut relier : une plaque d'un cote, de l'autre un canon ou un ventilateur. */
object Liens {
    fun liable(type: TypePiece): Boolean = type == TypePiece.PLAQUE || estCible(type)

    /** Ce qu'une plaque peut commander. */
    fun estCible(type: TypePiece): Boolean = type == TypePiece.CANON || type == TypePiece.VENTILATEUR

    /** Vrai si [a] et [b] forment un couple plaque/cible, dans un sens ou dans l'autre. */
    fun complementaires(a: TypePiece, b: TypePiece): Boolean =
        (a == TypePiece.PLAQUE && estCible(b)) || (b == TypePiece.PLAQUE && estCible(a))
}
