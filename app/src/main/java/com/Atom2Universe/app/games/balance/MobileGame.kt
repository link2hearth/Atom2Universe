package com.Atom2Universe.app.games.balance

import kotlin.math.hypot
import kotlin.random.Random

/**
 * Le jeu du mobile : des tiges déjà montées, des crochets vides, et des objets à y accrocher
 * pour que tout pende droit.
 *
 * ## Le casse-tête
 *
 * Chaque tige a son nœud à un cran précis : bras de 2 et de 1, par exemple. Elle est équilibrée
 * quand poids × bras est le même des deux côtés, donc ici quand la gauche pèse moitié moins que
 * la droite. Les objets ont des poids entiers, lisibles à leurs points ; le joueur les répartit.
 *
 * ## Le tableau
 *
 * Le générateur pose des objets au hasard sur un arbre au hasard, puis **déduit** les crans de
 * chaque tige des poids qui pendent de part et d'autre : c'est une règle de construction qui
 * garantit qu'une répartition au moins existe, pas une recherche. Il ne garde que les tiges
 * courtes (pas plus de [CRANS_MAX] crans), puis retire tous les objets et les pose en vrac sur
 * l'étagère. N'importe quelle répartition exacte gagne, pas seulement celle du tirage.
 *
 * ## La physique
 *
 * Elle ne juge pas : l'équilibre se vérifie en entiers. Elle montre — chaque objet accroché fait
 * pencher ce qui doit pencher, et le mobile se balance pendant qu'on le construit.
 */
class MobileGame(private val rng: Random = Random.Default) {

    /**
     * [objets] : autant de crochets. [leurres] : des objets en plus sur l'étagère, qui ne servent
     * pas — sans eux, le poids total se lit sur l'étagère et tout se déduit en divisant depuis le
     * haut ; avec, il faut choisir lesquels accrocher.
     */
    enum class Difficulty(val objets: Int, val leurres: Int, val masseMax: Int, val jumeaux: Int) {
        EASY(3, 0, 6, 1),
        MEDIUM(4, 1, 8, 1),
        // Avec six poids distincts sur neuf, plus deux leurres, l'étagère experte était presque
        // toujours la même ; des jumeaux la font varier.
        HARD(5, 1, 9, 2),
        EXPERT(6, 1, 9, 2)
    }

    var difficulty = Difficulty.EASY
        private set
    var level = 1
        private set

    lateinit var monde: MobileMonde
        private set

    /** Les objets pas encore accrochés. */
    val etagere = ArrayList<MobileObjet>()

    /** L'objet de chaque case de l'étagère, fixé au début du tableau : un objet revient toujours à sa case. */
    var casesObjets: List<MobileObjet> = emptyList()
        private set

    /** Position de chaque case de l'étagère (x, y du centre), en mètres. */
    var casesEtagere = FloatArray(0)
        private set

    /**
     * Taille des objets posés sur l'étagère, par rapport à leur taille au mobile. La vue la
     * réduit quand la rangée serait plus large que l'écran : c'est l'étagère qui rapetisse, pas le
     * mobile. Un objet repris retrouve sa vraie taille.
     */
    var echelleEtagere = 1f
        private set

    /** Largeur de l'étagère à pleine taille, en mètres. */
    var largeurEtagere = 0f
        private set

    /** L'objet sous le doigt, s'il y en a un. */
    var enMain: MobileObjet? = null
        private set

    /** D'où vient l'objet en main : un crochet, ou `null` pour l'étagère. */
    private var origine: BoutCrochet? = null

    /** Centre de l'objet en main, en mètres. */
    var mainX = 0f
        private set
    var mainY = 0f
        private set

    /** Le crochet sur lequel l'objet en main se poserait si on le lâchait maintenant. */
    var cible: BoutCrochet? = null
        private set

    /** Vrai une fois tous les crochets garnis et le mobile exactement équilibré. */
    var gagne = false
        private set

    /** Depuis combien de temps le mobile est complet et juste. */
    var tenue = 0f
        private set

    /** Temps écoulé depuis la victoire. */
    var depuisVictoire = 0f
        private set

    /**
     * Vrai une fois la récompense de ce tableau versée : recommencer un tableau gagné, puis le
     * regagner, ne paie pas deux fois.
     */
    var recompense = false

    /** Étendue du mobile garni : xMin, xMax, yMin, yMax (mètres). L'étagère n'y est pas : la vue la range en bas. */
    val bornes = FloatArray(4)

    /**
     * La répartition qui a servi à construire le tableau. Le jeu ne s'en sert jamais (toute
     * répartition exacte gagne) ; les tests, si, pour jouer un tableau sans le résoudre.
     */
    internal var tirage: List<Pair<BoutCrochet, MobileObjet>> = emptyList()
        private set

    /** Rayon du plus gros objet du tableau. */
    var rayonMax = 0f
        private set

    /** Ce qui vient de se passer, pour le son. Lu et vidé par la vue. */
    val evenements = ArrayList<Evenement>()

    sealed class Evenement {
        class Accroche(val objet: MobileObjet) : Evenement()
        class Prise(val objet: MobileObjet) : Evenement()
        object Equilibre : Evenement()
        object Victoire : Evenement()
    }

    companion object {
        const val PLAFOND = 6f

        /** Une tige ne dépasse jamais ce nombre de crans : au-delà, on ne les compte plus d'un regard. */
        const val CRANS_MAX = 6

        /**
         * Le temps de voir le dernier nœud passer au vert avant la victoire. Elle n'attend pas que
         * le mobile s'arrête : l'équilibre se juge en entiers, et attendre la fin du balancement
         * faisait patienter six secondes devant un mobile déjà juste.
         */
        const val TENUE_REQUISE = 0.35f

        /** Pente (degrés) sous laquelle le mobile juste compte comme droit. */
        const val PENTE_VICTOIRE = 3f

        /** Au plus tard, la victoire tombe ce temps après que le mobile est juste. */
        const val TENUE_MAX = 1.6f

        /** Après la victoire, le mobile se pose (amorti fort) pendant ce temps, puis la brise passe. */
        const val POSE = 1.4f

        /** Facteur d'amortissement d'un mobile juste, le temps qu'il se pose. */
        const val AMORTI_POSE = 4f

        /** La brise de la victoire : accélération de pointe (m/s²) et durée (s). */
        const val BRISE_FORCE = 0.9f
        const val BRISE_DUREE = 8f

        /** Les premiers niveaux du mode facile n'ont que deux objets : une tige, une idée. */
        const val NIVEAUX_INITIATION = 2
    }

    init {
        newLevel(Difficulty.EASY)
    }

    // ── Tableaux ──────────────────────────────────────────────────────────────

    /**
     * Tire un nouveau tableau. Le niveau monte après une victoire ; [niveau] le fixe (reprise
     * d'une partie, changement de difficulté).
     */
    fun newLevel(diff: Difficulty = difficulty, niveau: Int? = null) {
        level = when {
            niveau != null -> niveau.coerceAtLeast(1)
            diff != difficulty -> 1
            gagne -> level + 1
            else -> level
        }
        difficulty = diff
        val initiation = diff == Difficulty.EASY && level <= NIVEAUX_INITIATION
        val n = if (initiation) 2 else diff.objets
        // Les poids possibles sont peu nombreux : sans cette règle, deux tableaux de suite
        // tombaient sur la même étagère. Elle compare l'étagère entière, leurres compris.
        val precedente = casesObjets.map { it.masse }
        var racine: MobileTige
        var leurres: List<MobileObjet>
        var essais = 0
        while (true) {
            racine = tirer(n, diff.masseMax, diff.jumeaux) ?: continue
            leurres = if (initiation) emptyList() else leurres(racine, diff)
            val rangee = (racine.crochets().map { it.objet!!.masse } + leurres.map { it.masse }).sorted()
            if (rangee != precedente || essais++ >= 50) break
        }
        etagere.clear()
        etagere += leurres
        recompense = false
        installer(racine)
    }

    /**
     * Les objets en trop : des poids qu'aucun crochet du tirage ne porte, pour qu'aucun ne double
     * simplement un objet utile — et **voisins** des poids utiles (un ou deux de plus ou de
     * moins). Tirés parmi tous les poids libres, ils tombaient presque toujours sur les plus
     * gros, ceux que le générateur place le moins : « le plus gros est en trop » suffisait.
     */
    private fun leurres(racine: MobileTige, diff: Difficulty): List<MobileObjet> {
        val pris = racine.crochets().map { it.objet!!.masse }
        val voisins = pris.flatMap { m -> listOf(m - 2, m - 1, m + 1, m + 2) }
            .filter { it in 1..diff.masseMax && it !in pris }
            .distinct().shuffled(rng)
        val libres = (1..diff.masseMax).filter { it !in pris }.shuffled(rng)
        // Le plus lourd de l'étagère n'est un leurre qu'aussi souvent que le voudrait le hasard :
        // une fois sur le nombre d'objets.
        val dessous = voisins.filter { it < pris.max() }
        val total = pris.size + diff.leurres
        val preferes = if (dessous.isNotEmpty() && rng.nextInt(total) != 0) dessous else voisins
        val choix = (preferes + voisins + libres).distinct()
        return List(diff.leurres) { i ->
            MobileObjet(if (i < choix.size) choix[i] else 1 + rng.nextInt(diff.masseMax))
        }
    }

    /** Remet tous les objets sur l'étagère, même tableau. */
    fun recommencer() {
        val racine = monde.racine
        enMain?.let { etagere += it }
        for (c in racine.crochets()) c.objet?.let { etagere += it }
        for (c in racine.crochets()) c.objet = null
        installer(racine)
    }

    /**
     * Monte le tableau. Les objets sont soit encore aux crochets (tableau neuf : c'est le tirage),
     * soit déjà sur l'étagère (on recommence).
     */
    private fun installer(racine: MobileTige) {
        val objets = ArrayList<MobileObjet>()
        for (c in racine.crochets()) c.objet?.let { objets += it }
        val neuf = objets.isNotEmpty()
        objets += etagere
        etagere.clear()
        rayonMax = objets.maxOf { it.rayon }

        // La mise en page se fait **sur la solution** : c'est une fois tout accroché que les
        // voisins doivent tenir côte à côte sans se chevaucher. Pour recommencer, les crans sont
        // déjà fixés, on ne les recalcule pas.
        if (neuf) {
            MobileMiseEnPage.etendue(racine, rayonMax)
            tirage = racine.crochets().map { it to it.objet!! }
        }
        for (c in racine.crochets()) c.objet = null
        val (gauche, droite) = etendueVide(racine)
        val hauteur = MobileMiseEnPage.hauteur(racine, rayonMax)

        if (neuf) {
            objets.sortBy { it.masse }
            casesObjets = objets
        }
        etagere += casesObjets
        monde = MobileMonde(racine, PLAFOND)

        // L'étagère, sous le mobile, en une rangée ; la vue la redescend au bas de l'écran.
        val bas = PLAFOND - hauteur
        largeurEtagere = casesObjets.sumOf { (2f * it.rayon + 2f * MobileRegles.ECART).toDouble() }.toFloat()
        poserEtagere(bas - 0.35f - rayonMax, 0f, 1f)

        bornes[0] = -gauche - 0.15f
        bornes[1] = droite + 0.15f
        bornes[2] = bas - 0.1f
        bornes[3] = PLAFOND

        enMain = null
        origine = null
        cible = null
        gagne = false
        depuisVictoire = 0f
        tenue = 0f
        evenements.clear()
    }

    /** Étendue gauche et droite du mobile garni, crans déjà fixés. */
    private fun etendueVide(racine: MobileTige): Pair<Float, Float> {
        fun e(b: MobileBout): Pair<Float, Float> = when (b) {
            is BoutCrochet -> rayonMax to rayonMax
            is BoutTige -> {
                val t = b.tige
                val (gG, _) = e(t.gauche)
                val (_, dD) = e(t.droite)
                (t.longueurGauche + gG) to (t.longueurDroite + dD)
            }
        }
        return e(BoutTige(racine))
    }

    /**
     * Tire un tableau : un arbre au hasard de [n] objets, des poids au hasard, et les crans qui
     * en découlent. Rend `null` si une tige sort trop longue ou si le tableau n'a rien à
     * chercher ; l'appelant retire.
     */
    private fun tirer(n: Int, masseMax: Int, jumeaux: Int): MobileTige? {
        // Des poids distincts le plus souvent : une étagère de trois « 1 » se résout sans réfléchir.
        // Les petits poids passent plus facilement la limite de crans, donc sans ce tri le tirage
        // en serait saturé.
        val distincts = if (jumeaux > 1) rng.nextBoolean() else rng.nextInt(4) != 0
        val masses = if (distincts) {
            (1..masseMax).shuffled(rng).take(n)
        } else {
            List(n) { 1 + rng.nextInt(masseMax) }
        }
        // Peu de jumeaux, et jamais trois fois le même poids.
        if (masses.size - masses.distinct().size > jumeaux) return null
        if (masses.groupingBy { it }.eachCount().values.any { it > 2 }) return null
        val racine = arbre(masses) as? BoutTige ?: return null
        // Au moins une tige dont le nœud n'est pas au milieu : sinon il suffit de faire des paires.
        if (racine.tige.tiges().none { it.brasGauche != it.brasDroit }) return null
        return racine.tige
    }

    private fun arbre(masses: List<Int>): MobileBout? {
        if (masses.size == 1) return BoutCrochet().apply { objet = MobileObjet(masses[0]) }
        // Une coupe au hasard plus souvent qu'au bord : couper toujours un seul objet donne des
        // chaînes (une tige sous l'autre), qui se ressemblent toutes.
        val coupe = when {
            masses.size == 2 -> 1
            rng.nextInt(3) != 0 -> 1 + rng.nextInt(masses.size - 1)
            rng.nextBoolean() -> 1
            else -> masses.size - 1
        }
        val g = arbre(masses.subList(0, coupe)) ?: return null
        val d = arbre(masses.subList(coupe, masses.size)) ?: return null
        val mg = MobileTige.masse(g)
        val md = MobileTige.masse(d)
        val p = pgcd(mg, md)
        // Équilibre : mg × brasGauche = md × brasDroit.
        val brasGauche = md / p
        val brasDroit = mg / p
        if (brasGauche + brasDroit > CRANS_MAX) return null
        return BoutTige(MobileTige(brasGauche, brasDroit, g, d))
    }

    private fun pgcd(a: Int, b: Int): Int = if (b == 0) a else pgcd(b, a % b)

    // ── Jeu ───────────────────────────────────────────────────────────────────

    /** Vrai quand tous les crochets portent un objet (les leurres peuvent rester sur l'étagère). */
    val complet: Boolean get() = enMain == null && monde.crochets.all { it.objet != null }

    fun step(dt: Float) {
        if (gagne) {
            depuisVictoire += dt
            // Le mobile se pose d'abord, freiné ; la brise passe ensuite, dans l'air normal.
            monde.amorti = if (depuisVictoire < POSE) AMORTI_POSE else 1f
            souffler()
            monde.step(dt)
            return
        }
        val juste = complet && monde.racine.equilibree()
        // Dès que c'est juste, l'air s'épaissit : le mobile s'arrête vite au lieu de se balancer
        // de longues secondes autour de la bonne position.
        monde.amorti = if (juste) AMORTI_POSE else 1f
        monde.step(dt)
        // Le mobile bouge sous le doigt immobile : la visée suit les crochets, pas seulement le doigt.
        if (enMain != null) deplacer(anneauX, anneauY, porteeVisee)
        if (juste) tenue += dt else tenue = 0f
        // La victoire attend que le mobile soit à peu près droit — annoncée sur une tige qui
        // penche encore de quinze degrés, elle surprenait —, mais jamais plus de [TENUE_MAX].
        if (tenue >= TENUE_REQUISE && (penteMax() < PENTE_VICTOIRE || tenue >= TENUE_MAX)) {
            gagne = true
            depuisVictoire = 0f
            evenements += Evenement.Victoire
        }
    }

    /**
     * Une brise passe sur le mobile achevé : il danse un peu, puis se repose. C'est la seule
     * récompense qui ait sa place ici — un mobile est fait pour bouger.
     */
    private fun souffler() {
        val t = depuisVictoire - POSE
        if (t < 0f || t > BRISE_DUREE) return
        val force = BRISE_FORCE * kotlin.math.sin(2f * kotlin.math.PI.toFloat() * t / 2.6f) *
            kotlin.math.exp(-t / 2.2f) * minOf(1f, t / 0.4f)
        for (c in monde.crochets) {
            val corps = monde.corps(c) ?: continue
            corps.applyForce(corps.mass * force, 0f)
        }
    }

    /**
     * Une tige dont tout le dessous est garni et exactement équilibré : son nœud le dit en
     * changeant de couleur. C'est ce que la pente montre déjà, mais net — sur une tige qui porte
     * vingt kilos, un kilo de trop ne la penche que d'un degré.
     */
    fun estJuste(tige: MobileTige): Boolean = tige.garnie() && tige.equilibree()

    private fun nombreJustes(): Int = monde.tiges.count { estJuste(it) }

    /** La plus forte pente du mobile, en degrés. */
    private fun penteMax(): Float {
        var m = 0f
        for (t in monde.tiges) m = maxOf(m, kotlin.math.abs(monde.penteDeg(t)))
        return m
    }

    // ── Prendre et poser ──────────────────────────────────────────────────────

    /**
     * Saisit l'objet le plus proche de ([x], [y]), sur l'étagère ou au mobile. Rend vrai si
     * quelque chose a été pris. [portee] (mètres) s'ajoute au rayon de l'objet.
     */
    fun prendre(x: Float, y: Float, portee: Float): Boolean {
        if (gagne || enMain != null) return false
        var caseChoisie = -1
        var crochetChoisi: BoutCrochet? = null
        var d2 = Float.MAX_VALUE
        for ((i, o) in casesObjets.withIndex()) {
            if (etagere.none { it === o }) continue
            val dx = casesEtagere[2 * i] - x
            val dy = casesEtagere[2 * i + 1] - y
            val r = o.rayon * echelleEtagere + portee
            val d = dx * dx + dy * dy
            if (d < r * r && d < d2) {
                d2 = d
                caseChoisie = i
            }
        }
        for (c in monde.crochets) {
            val o = c.objet ?: continue
            val corps = monde.corps(c) ?: continue
            val dx = corps.x - x
            val dy = corps.y - y
            val r = o.rayon + portee
            val d = dx * dx + dy * dy
            if (d < r * r && d < d2) {
                d2 = d
                crochetChoisi = c
                caseChoisie = -1
            }
        }
        val objet: MobileObjet
        if (crochetChoisi != null) {
            val corps = monde.corps(crochetChoisi)!!
            mainX = corps.x
            mainY = corps.y
            objet = monde.decrocher(crochetChoisi) ?: return false
            origine = crochetChoisi
        } else if (caseChoisie >= 0) {
            objet = casesObjets[caseChoisie]
            etagere.removeAll { it === objet }
            mainX = casesEtagere[2 * caseChoisie]
            mainY = casesEtagere[2 * caseChoisie + 1]
            origine = null
        } else {
            return false
        }
        enMain = objet
        cible = null
        // Tenu par son anneau, sur place, sans rien viser tant qu'il n'a pas bougé.
        anneauX = mainX
        anneauY = mainY + objet.rayon
        porteeVisee = 0f
        evenements += Evenement.Prise(objet)
        tenue = 0f
        return true
    }

    /**
     * Déplace l'objet en main par son **anneau** — le point en haut du disque où se noue le fil :
     * il va en ([x], [y]), le disque pend dessous. Retient le crochet visé.
     *
     * On vise avec l'anneau, comme on accroche un vrai mobile : le crochet retenu est le plus
     * proche de lui, à moins de [portee] mètres. Viser avec tout le disque désignait souvent un
     * crochet voisin, au-dessus de celui qu'on visait. Un crochet garni se vise en posant
     * l'anneau sur son objet ; un crochet vide tout proche passe devant, pour qu'un échange ne
     * prenne jamais la place d'un objet qu'on n'a pas désigné.
     */
    fun deplacer(x: Float, y: Float, portee: Float) {
        val objet = enMain ?: return
        anneauX = x
        anneauY = y
        porteeVisee = portee
        mainX = x
        mainY = y - objet.rayon
        val p = tampon
        var meilleur: BoutCrochet? = null
        var score = portee
        for (c in monde.crochets) {
            val occupant = c.objet
            val corps = monde.corps(c)
            val s = if (occupant != null && corps != null) {
                maxOf(0f, hypot(corps.x - x, corps.y - y) - occupant.rayon) + portee * 0.25f
            } else {
                monde.crochet(c, p)
                hypot(p[0] - x, p[1] - y)
            }
            if (s < score) {
                score = s
                meilleur = c
            }
        }
        cible = meilleur
    }

    private val tampon = FloatArray(2)

    /** Dernière visée : l'anneau (mètres) et la portée, rejouées à chaque pas. */
    private var anneauX = 0f
    private var anneauY = 0f
    private var porteeVisee = 0f

    /**
     * Lâche l'objet en main. Sur un crochet vide, il s'y accroche ; sur un crochet garni, les deux
     * objets échangent leur place (l'autre part là d'où venait le premier) ; ailleurs, il rentre
     * à l'étagère.
     */
    fun lacher() {
        val objet = enMain ?: return
        val c = cible
        val depart = origine
        enMain = null
        cible = null
        origine = null
        if (c == null) {
            etagere += objet
            return
        }
        val avant = nombreJustes()
        val occupant = c.objet
        if (occupant != null) {
            monde.decrocher(c)
            if (depart != null) {
                monde.accrocher(depart, occupant)
            } else {
                etagere += occupant
            }
        }
        monde.accrocher(c, objet)
        evenements += Evenement.Accroche(objet)
        tenue = 0f
        if (nombreJustes() > avant) evenements += Evenement.Equilibre
    }

    /**
     * Range l'étagère : une rangée centrée en [centreX], à la hauteur [y] (mètres), objets à
     * [reduction] fois leur taille. La vue la pose au bas de l'écran, quelle que soit la place que
     * le mobile laisse au-dessus.
     */
    fun poserEtagere(y: Float, centreX: Float, reduction: Float) {
        echelleEtagere = reduction
        casesEtagere = FloatArray(casesObjets.size * 2)
        var x = centreX - largeurEtagere * reduction / 2f
        for ((i, o) in casesObjets.withIndex()) {
            x += (o.rayon + MobileRegles.ECART) * reduction
            casesEtagere[2 * i] = x
            casesEtagere[2 * i + 1] = y
            x += (o.rayon + MobileRegles.ECART) * reduction
        }
    }

    /** Position de la case d'étagère d'un objet. */
    fun caseDe(objet: MobileObjet, out: FloatArray) {
        val i = casesObjets.indexOfFirst { it === objet }
        out[0] = casesEtagere[2 * i]
        out[1] = casesEtagere[2 * i + 1]
    }
}
