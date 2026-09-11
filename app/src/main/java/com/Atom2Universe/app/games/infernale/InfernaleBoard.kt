package com.Atom2Universe.app.games.infernale

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld

/**
 * Le bouton : une **zone de detection**. Ce qui entre dedans gagne.
 *
 * ## Pourquoi pas un vrai bouton a enfoncer
 *
 * Il y en a eu un, et il a echoue trois fois de suite. Un poussoir coulissant tenu par
 * un ressort, avec la victoire a la butee basse : plus proche de l'image qu'on se fait
 * d'un bouton, et coince dans un triangle dont aucun reglage ne sortait.
 *
 *  - Poussoir leger, pour etre sensible : il **vibrait** sous un domino cinq fois plus
 *    lourd, et secouait la fin de la ligne quinze secondes apres que tout se soit arrete.
 *  - Poussoir plus lourd, ressort sous-amorti : il claquait, mais il **sonnait**, et plus
 *    rien ne s'endormait.
 *  - Ressort sur-amorti : plus de bruit, mais l'amortisseur opposait huit newtons a un
 *    appui de deux et demi, et le bouton **ne claquait plus du tout**.
 *
 * La cause tient en une phrase : **un domino couche est un actionneur faible et lent.**
 * Il pese deux newtons et demi et arrive en fin de course, sans elan. Lui demander de
 * vaincre un ressort, c'est lui demander ce qu'il n'a pas.
 *
 * L'objection qu'on faisait au capteur - « une bille qui frole gagnerait » - ne tient
 * pas : une zone se place ou l'on veut, et rien n'oblige a la mettre sur un passage. Et
 * le mouvement visible, l'autre argument, appartient au dessin : la bibliotheque sait
 * deja dessiner un bouton enfonce a partir d'un simple booleen.
 *
 * Reste donc le plus simple, qui est aussi le seul a ne rien demander a personne : une
 * zone qui constate. Zero reglage, zero vibration, zero echec possible.
 */
class Bouton internal constructor(
    /** Le capteur : il ne repousse rien, il constate. */
    val zone: PhysBody
) {
    /**
     * Vrai une fois que quelque chose est entre dans la zone, et **il le reste**.
     *
     * Le verrou est delibere : une bille qui traverse et ressort a quand meme gagne. Ce
     * qu'un joueur retient, c'est que sa machine a marche.
     */
    var declenche = false
        private set

    /** Ce qui a declenche le bouton, pour l'annoncer ou le mettre en valeur. */
    var declencheur: PhysBody? = null
        private set

    /** 0 ou 1 : c'est l'etat `active` du dessin. */
    val enfoncement: Float get() = if (declenche) 1f else 0f

    internal fun activer(par: PhysBody) {
        if (declenche) return
        declenche = true
        declencheur = par
    }

    /** Remet le bouton a zero, pour rejouer le tableau. */
    fun rearmer() {
        declenche = false
        declencheur = null
    }
}

/**
 * Le plateau : le monde, le sol, la bille, le bouton, et les pièces posées.
 *
 * C'est le bac à sable minimal dans lequel une idée se teste sans APK : on pose des
 * pièces, on lâche la bille, on demande si le bouton est tombé.
 */
class Plateau(
    val largeur: Float = LARGEUR,
    val hauteur: Float = HAUTEUR
) {
    val monde = PhysWorld().apply {
        // Les pièces posées passent beaucoup de temps immobiles à attendre leur tour :
        // le sommeil est exactement fait pour ça, et il rend le tableau presque gratuit
        // tant que la bille n'est pas partie.
        sleepEnabled = true
    }

    /** Le sol, scellé, à l'altitude zéro. */
    val sol: PhysBody = PhysBody(largeur, 0.5f, 0f).apply {
        x = 0f
        y = -0.5f
        lockPosition = true
        lockRotation = true
        friction = 0.7f
        refreshMass()
    }

    private val murs = listOf(
        mur(-largeur / 2f - 0.25f),
        mur(largeur / 2f + 0.25f)
    )

    private fun mur(x: Float): PhysBody = PhysBody(0.25f, hauteur, 0f).apply {
        this.x = x
        this.y = hauteur
        lockPosition = true
        lockRotation = true
        friction = 0.3f
        refreshMass()
    }

    val pieces = ArrayList<Piece>()

    companion object {
        /** Largeur du tableau, en metres. */
        const val LARGEUR = 10f

        /** Hauteur utile du tableau, en metres. */
        const val HAUTEUR = 7f

        /** Categorie du decor et des pieces scellees : le capteur les ignore. */
        const val DECOR = 1

        /** Categorie de tout ce qui bouge : bille, dominos, planches de bascule. */
        const val MOBILE = 2
    }
    var bille: PhysBody? = null
        private set
    var bouton: Bouton? = null
        private set

    init {
        monde.add(sol)
        for (m in murs) monde.add(m)
    }

    /** Vrai quand la machine a fait son travail. */
    val gagne: Boolean get() = bouton?.declenche == true

    fun poser(piece: Piece): Piece {
        piece.poser(monde)
        pieces.add(piece)
        return piece
    }

    fun retirer(piece: Piece) {
        piece.retirer(monde)
        pieces.remove(piece)
    }

    /**
     * Echange la piece [index] contre une autre **sans changer son rang**.
     *
     * Le rang n'est pas cosmetique : la partie tient la liste des poses du joueur en
     * parallele de celle-ci, et l'interface designe une piece par son indice. Retirer
     * puis reposer aurait renvoye la piece deplacee en fin de liste, et le doigt aurait
     * deplace la voisine au geste suivant.
     */
    fun remplacer(index: Int, piece: Piece): Piece {
        pieces[index].retirer(monde)
        piece.poser(monde)
        pieces[index] = piece
        return piece
    }

    /**
     * Pose la bille de départ. Elle est lourde et peu rebondissante par elle-même : le
     * rebond doit venir des plots, sinon une bille élastique rend tout tableau soluble
     * par hasard.
     */
    fun poserBille(x: Float, y: Float, rayon: Float = 0.11f, masse: Float = 2f): PhysBody {
        bille?.let { monde.remove(it) }
        val b = PhysBody.circle(rayon, masse).apply {
            this.x = x
            this.y = y
            friction = 0.25f
            restitution = 0.1f
            category = MOBILE
        }
        monde.add(b)
        bille = b
        return b
    }

    /**
     * Pose le bouton : une zone de detection large de [largeur] et haute de [hauteur],
     * posee sur [bas].
     *
     * Elle ne voit que ce qui bouge - decor et pieces scellees la traversent sans rien
     * declencher. C'est ce qui permet de la poser a meme le sol sans qu'elle se declenche
     * toute seule sur lui.
     */
    fun poserBouton(
        x: Float,
        bas: Float,
        largeur: Float = 0.3f,
        hauteur: Float = 0.22f
    ): Bouton {
        val zone = PhysBody(largeur / 2f, hauteur / 2f, 0f).apply {
            this.x = x
            this.y = bas + hauteur / 2f
            lockPosition = true
            lockRotation = true
            isSensor = true
            collidesWith = MOBILE
            refreshMass()
        }
        monde.add(zone)
        val b = Bouton(zone)
        bouton = b
        return b
    }

    /**
     * Le socle qui porte le bouton, quand il est perché. `null` quand il est à même le sol.
     *
     * C'est un vrai corps scellé et pas un pilier dessiné. Un bouton qui flotterait devant
     * un décor peint se laisserait traverser par la bille, et le joueur y verrait à juste
     * titre un bug — le décor du trébuchet a coûté assez cher pour qu'on se souvienne que
     * ce qu'on voit doit être ce qui existe. Et c'est ce socle qui fait tout l'intérêt d'un
     * bouton en hauteur : il faut poser la bille **sur** quelque chose, ce qui est un autre
     * problème que l'amener quelque part.
     */
    var socle: PhysBody? = null
        private set

    /** Dresse le socle du bouton. [hauteur] est mesurée depuis le sol. */
    fun poserSocle(x: Float, hauteur: Float): PhysBody {
        socle?.let { monde.remove(it) }
        val pilier = PhysBody(0.22f, hauteur / 2f, 0f).apply {
            this.x = x
            this.y = hauteur / 2f
            lockPosition = true
            lockRotation = true
            friction = 0.55f
            restitution = 0.02f
            refreshMass()
        }
        monde.add(pilier)
        socle = pilier
        return pilier
    }

    /** Les jets d'air des ventilateurs posés. Refait à chaque image, il est court. */
    private val souffles = ArrayList<Souffle>()

    /**
     * Les ventilateurs poussent **avant** le pas, jamais pendant.
     *
     * Le moteur remet les forces à zéro à la fin de chaque image et pas entre les
     * sous-pas : une force posée ici vaut donc pour toute l'image, ce qui est exactement
     * ce qu'on veut d'un vent — il ne change pas trois fois pendant un centième de
     * seconde.
     *
     * Une force posée sur un corps le **réveille**. C'est voulu pour ce qui flotte dans
     * le jet, et c'est pourquoi [Souffle.appliquer] refuse les corps scellés : un
     * ventilateur qui soufflerait sur son propre carter empêcherait le tableau entier
     * de s'endormir, et un tableau qui ne dort jamais coûte le prix fort à ne rien faire.
     */
    private fun souffler() {
        souffles.clear()
        for (p in pieces) p.souffle?.let { souffles.add(it) }
        if (souffles.isEmpty()) return
        val corps = monde.bodies
        for (i in corps.indices) {
            val c = corps[i]
            if (!c.inWorld || c.immovable || c.isSensor) continue
            for (j in souffles.indices) souffles[j].appliquer(c)
        }
    }

    /**
     * Une image de simulation, puis la lecture de ce qui s'est passe pendant.
     *
     * Les evenements se lisent **apres** le pas, jamais pendant : le moteur les range
     * dans une liste au lieu de rappeler le jeu au milieu d'une resolution. C'est ce qui
     * permet de retirer une piece ou de rebatir le tableau ici sans rien casser.
     */
    fun avancer(dt: Float) {
        souffler()
        monde.stepFrame(dt)
        val b = bouton ?: return
        for (e in monde.contactEvents) {
            if (!e.begin || !e.sensor) continue
            val entre = e.other(b.zone) ?: continue
            b.activer(entre)
        }
    }

    /**
     * Vrai quand plus rien ne bouge assez pour que la suite change quoi que ce soit.
     *
     * C'est ce que le générateur interroge pour savoir qu'une machine a fini de dérouler
     * ce qu'elle avait à dérouler : inutile de simuler dix secondes de dominos immobiles.
     */
    fun immobile(): Boolean = monde.isAtRest()

    /** Vrai quand la bille est sortie du tableau — une machine ratée, pas une machine lente. */
    fun billePerdue(): Boolean {
        val b = bille ?: return true
        return b.x < -largeur / 2f - 0.5f || b.x > largeur / 2f + 0.5f || b.y < -1f || b.y > hauteur + 3f
    }

    /**
     * Fait tourner la machine jusqu'a la victoire ou jusqu'a [duree] secondes, et rend
     * le temps ecoule. C'est le raccourci des essais : « est-ce que ca marche ? ».
     *
     * [arreterALaVictoire] a l'air d'un detail et n'en est pas un. Par defaut la
     * simulation **s'arrete au moment ou le bouton tombe** — ce qui est ce qu'on veut
     * pour chronometrer une solution, mais laisse la scene en plein effondrement, avec
     * des dominos encore en l'air. Une mesure prise apres coup sur cet etat-la ne dit
     * rien de la scene au repos : elle dit seulement qu'on a coupe le film au milieu.
     * On a perdu une demi-heure a chercher une fuite d'energie dans le moteur qui
     * n'etait que ca. Pour regarder l'etat final, mettre `false`.
     */
    fun derouler(
        duree: Float = 20f,
        dt: Float = 1f / 120f,
        arreterALaVictoire: Boolean = true
    ): Float {
        var t = 0f
        while (t < duree) {
            avancer(dt)
            t += dt
            if (arreterALaVictoire && gagne) break
        }
        return t
    }
}
