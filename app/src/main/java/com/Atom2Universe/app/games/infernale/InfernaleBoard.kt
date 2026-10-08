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

    /**
     * Combien de secondes la bille doit rester dans la zone, ou zero si l'effleurer suffit.
     *
     * C'est le verbe « tenir » : on ne cherche plus a atteindre le bouton mais a **s'y
     * arreter**, ce qui demande de freiner — une cuvette, un butoir — et pas seulement de
     * descendre.
     */
    var duree = 0f
        internal set

    /** Depuis combien de temps la bille est dans la zone, d'une traite. Retombe a zero si elle sort. */
    var tenue = 0f
        internal set

    /** Avancement de la tenue, de 0 a 1, pour le dessin. */
    val progression: Float get() = if (duree > 0f) (tenue / duree).coerceIn(0f, 1f) else 0f

    /** 0 ou 1 : c'est l'etat `active` du dessin. */
    val enfoncement: Float get() = if (declenche) 1f else 0f

    /**
     * Le seul corps que le bouton accepte, ou `null` s'il les accepte tous.
     *
     * Dans un tableau a bille temoin, la bille de depart peut tomber dans la zone sans rien
     * gagner : ce qui doit y arriver, c'est l'autre.
     */
    var exige: PhysBody? = null
        internal set

    internal fun activer(par: PhysBody) {
        if (declenche) return
        exige?.let { if (it !== par) return }
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
 * Un anneau : une zone de detection en plein air, par ou la bille doit passer.
 *
 * Il n'a **aucun corps solide** — on le traverse, sinon ce serait un obstacle et pas un
 * passage. Plusieurs anneaux se franchissent **dans l'ordre** : le deuxieme ne compte pas
 * tant que le premier n'a pas ete passe, ce qui fait d'un tableau un parcours et pas une
 * simple cible.
 */
class Anneau internal constructor(
    /** La zone de detection, ronde, pour que le cercle dessine soit exactement ce qui compte. */
    val zone: PhysBody
) {
    /** Vrai une fois la bille passee, et il le reste. */
    var franchi = false
        internal set
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

    /** Les deux parois du plateau. Publiques : la vue les dessine telles qu'elles sont. */
    val murs = listOf(
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
        /**
         * Largeur du tableau, en metres.
         *
         * Seize et non plus dix : ce qu'on construit ici est un **terrain
         * d'experimentation**, et dix metres se remplissaient en une machine. La vue
         * n'essaie plus de tout montrer d'un coup — on s'y deplace a deux doigts — donc
         * rien n'empeche plus le plateau d'etre large.
         */
        const val LARGEUR = 16f

        /** Hauteur utile du tableau, en metres. */
        const val HAUTEUR = 10f

        /** Categorie du decor et des pieces scellees : le capteur les ignore. */
        const val DECOR = 1

        /** Categorie de tout ce qui bouge : bille, dominos, planches de bascule. */
        const val MOBILE = 2
    }
    var bille: PhysBody? = null
        private set
    var bouton: Bouton? = null
        private set

    /**
     * La bille temoin : une seconde bille, dorée, posee par le tableau sur un perchoir.
     *
     * Elle est **a celle qui doit gagner** : le bouton ne reconnait qu'elle. La bille de
     * depart ne sert alors qu'a la liberer — il faut la lui amener, la toucher, et ce
     * qu'elle fait ensuite est le vrai probleme. C'est la machine en deux temps, sans qu'un
     * joueur puisse la contourner en posant sa propre bille : dans ces tableaux la piece
     * « bille » n'est pas proposee.
     */
    var temoin: PhysBody? = null
        private set

    /** Le pilier qui porte la bille temoin. Scelle, comme le socle du bouton. */
    var perchoir: PhysBody? = null
        private set

    /** Les anneaux a franchir, dans l'ordre ou ils sont poses. */
    val anneaux = ArrayList<Anneau>()

    /** Les deux murets qui forment la cuvette du bouton, quand il faut y tenir. */
    var cuvette: List<PhysBody> = emptyList()
        private set

    /** Ce qui est actuellement dans la zone du bouton, tenu a jour par les evenements. */
    private val dansLeBouton = HashSet<PhysBody>()

    /**
     * Les corps que la chaine a atteints : touches par la bille de depart, ou par un corps
     * mobile deja atteint. Voir [chaine].
     */
    private val atteints = HashSet<PhysBody>()

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
        b.exige = temoin
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

    /**
     * Pose la bille temoin sur un pilier dont le sommet est a [haut], et fait du bouton un
     * bouton qui ne reconnait qu'elle. Le pilier est etroit : la moindre poussee la fait
     * tomber, ce qui est voulu — le tableau demande de la liberer, pas de la deloger de
     * force.
     */
    fun poserTemoin(x: Float, haut: Float, rayon: Float = 0.11f, masse: Float = 2f): PhysBody {
        temoin?.let { monde.remove(it) }
        perchoir?.let { monde.remove(it) }
        val pilier = PhysBody(0.17f, haut / 2f, 0f).apply {
            this.x = x
            this.y = haut / 2f
            lockPosition = true
            lockRotation = true
            friction = 0.5f
            restitution = 0.02f
            refreshMass()
        }
        monde.add(pilier)
        perchoir = pilier
        val b = PhysBody.circle(rayon, masse).apply {
            this.x = x
            this.y = haut + rayon + 0.005f
            friction = 0.25f
            restitution = 0.1f
            category = MOBILE
        }
        monde.add(b)
        temoin = b
        bouton?.exige = b
        return b
    }

    /**
     * Pose un anneau centre en ([x], [y]). Il ne reconnait que la bille qui compte : la
     * temoin quand il y en a une, sinon la bille de depart et celles du joueur. Un domino
     * qui rouleraient dedans n'a rien « franchi ».
     */
    fun poserAnneau(x: Float, y: Float, rayon: Float = 0.4f): Anneau {
        val zone = PhysBody.circle(rayon, 1f).apply {
            this.x = x
            this.y = y
            lockPosition = true
            lockRotation = true
            isSensor = true
            collidesWith = MOBILE
            refreshMass()
        }
        monde.add(zone)
        return Anneau(zone).also { anneaux.add(it) }
    }

    /**
     * Ceinture le bouton de deux murets pour qu'on puisse s'y arreter.
     *
     * Ce sont de vrais corps scelles, pas du decor : une bille qui arrive trop vite doit
     * pouvoir en rebondir, et c'est ce qui fait de « tenir » un probleme de freinage.
     */
    fun poserCuvette(x: Float, ecart: Float = 0.42f) {
        for (c in cuvette) monde.remove(c)
        cuvette = listOf(-1f, 1f).map { cote ->
            PhysBody(0.1f, 0.2f, 0f).apply {
                this.x = x + cote * ecart
                this.y = 0.2f
                lockPosition = true
                lockRotation = true
                friction = 0.55f
                restitution = 0.05f
                refreshMass()
            }.also { monde.add(it) }
        }
    }

    /** Vrai quand tous les anneaux sont franchis : le bouton ne compte qu'alors. */
    val anneauxFranchis: Boolean get() = anneaux.all { it.franchi }

    /** Vrai si la bille est en train de tenir dans la zone : la machine n'est pas arretee, elle attend. */
    fun enTenue(): Boolean = bouton?.let { it.duree > 0f && !it.declenche && it.tenue > 0f } == true

    private fun accepteAnneau(corps: PhysBody): Boolean =
        if (temoin != null) corps === temoin
        else corps === bille || (corps.owner as? Piece)?.type == TypePiece.BILLE

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
        propager()
        lireLesZones()
        lireLeBouton(dt)
    }

    /**
     * Les anneaux se franchissent **dans l'ordre** : un anneau n'est compte que si tous
     * ceux d'avant le sont deja. Sans cette regle, le dernier anneau suffirait a lui seul
     * et les autres ne seraient que du decor.
     */
    private fun lireLesZones() {
        for (e in monde.contactEvents) {
            if (!e.sensor) continue
            for ((i, a) in anneaux.withIndex()) {
                if (!e.begin || a.franchi) continue
                val entre = e.other(a.zone) ?: continue
                if (accepteAnneau(entre) && (0 until i).all { anneaux[it].franchi }) a.franchi = true
            }
            val b = bouton ?: continue
            val entre = e.other(b.zone) ?: continue
            if (e.begin) {
                dansLeBouton.add(entre)
                // Un bouton qu'il suffit d'effleurer se decide a l'entree ; un bouton qu'il faut
                // tenir se decide plus bas, image apres image.
                if (b.duree == 0f && anneauxFranchis) b.activer(entre)
            } else {
                dansLeBouton.remove(entre)
            }
        }
    }

    /**
     * Tenir : le compteur grimpe tant que la bille qui compte est dans la zone, et **retombe
     * a zero** des qu'elle en sort. Une bille qui traverse a toute vitesse ne cumule rien.
     */
    private fun lireLeBouton(dt: Float) {
        val b = bouton ?: return
        if (b.duree <= 0f || b.declenche) return
        val present = if (anneauxFranchis) dansLeBouton.firstOrNull { b.exige == null || it === b.exige } else null
        if (present == null) {
            b.tenue = 0f
            return
        }
        b.tenue += dt
        if (b.tenue >= b.duree) b.activer(present)
    }

    /**
     * Fait avancer la chaine : un corps touche par la bille de depart, ou par un corps
     * mobile deja touche, est atteint a son tour.
     *
     * **Seul ce qui bouge transmet.** Une rampe scellee est atteinte par la bille mais ne
     * touche rien de plus : sans cette regle, la bille frolant une rampe qui en frole une
     * autre ferait croire a une chaine la ou il n'y a que deux planches voisines. Un domino
     * touche par un domino atteint, lui, est atteint — c'est exactement ce qu'on veut
     * compter.
     *
     * On repasse sur les evenements de l'image tant que ca avance : deux contacts nes dans
     * la meme image se lisent dans un ordre quelconque, et le second ne doit pas depender
     * de ce hasard.
     */
    private fun propager() {
        val depart = bille
        val evenements = monde.contactEvents
        var change = true
        while (change) {
            change = false
            for (e in evenements) {
                if (!e.begin || e.sensor) continue
                val a = e.a ?: continue
                val c = e.b ?: continue
                if (transmet(a, depart) && atteints.add(c)) change = true
                if (transmet(c, depart) && atteints.add(a)) change = true
            }
        }
    }

    private fun transmet(corps: PhysBody, depart: PhysBody?): Boolean =
        !corps.immovable && (corps === depart || corps in atteints)

    /**
     * Combien de pieces posees la chaine a atteintes, torches exceptees.
     *
     * C'est la **note du tableau** : une piece posee mais jamais touchee n'a rien fait, et
     * ce jeu recompense la machine dont chaque piece sert, pas celle qui en a le moins.
     */
    fun chaine(): Int = pieces.count { it.type != TypePiece.TORCHE && estAtteinte(it) }

    /** Combien de pieces posees comptent pour la note : toutes sauf les torches. */
    fun comptees(): Int = pieces.count { it.type != TypePiece.TORCHE }

    private fun estAtteinte(p: Piece): Boolean = p.corps.any { it in atteints }

    /** Vrai si cette piece a ete atteinte par la chaine : le dessin la fait briller. */
    fun atteinte(p: Piece): Boolean = estAtteinte(p)

    /**
     * Vrai quand plus rien ne bouge assez pour que la suite change quoi que ce soit.
     *
     * C'est ce que le générateur interroge pour savoir qu'une machine a fini de dérouler
     * ce qu'elle avait à dérouler : inutile de simuler dix secondes de dominos immobiles.
     */
    fun immobile(): Boolean = monde.isAtRest()

    /** Vrai quand la bille est sortie du tableau — une machine ratée, pas une machine lente. */
    fun billePerdue(): Boolean {
        // Dans un tableau a temoin, c'est elle qu'on ne doit pas perdre : la bille de depart,
        // elle, a le droit de sortir une fois son travail fait.
        val b = temoin ?: bille ?: return true
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
