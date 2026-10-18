package com.Atom2Universe.app.games.balance

import com.Atom2Universe.app.games.physics.DistanceJoint
import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Le mobile : un arbre de tiges déjà montées, dont les bouts libres portent des crochets vides.
 * Le joueur y accroche des objets ; le mobile est équilibré quand, sur chaque tige, poids fois
 * bras est le même des deux côtés.
 *
 * Les bras se comptent en **crans** : une tige de bras 2 et 3 a son nœud au deuxième cran sur
 * cinq. Tout se calcule en entiers (masses et crans), donc l'équilibre se vérifie exactement :
 * la physique sert à le **montrer**, pas à le juger.
 */
class MobileObjet(val masse: Int) {
    val rayon: Float get() = MobileRegles.RAYON_UNITAIRE * sqrt(masse.toFloat())
}

/** Ce qui pend à un bout de tige : une autre tige, ou un crochet (vide ou portant un objet). */
sealed class MobileBout

class BoutTige(val tige: MobileTige) : MobileBout()

class BoutCrochet : MobileBout() {
    var objet: MobileObjet? = null
}

/**
 * Une tige, nœud compris. [brasGauche] et [brasDroit] sont en crans ; [unite] est la longueur
 * d'un cran en mètres, choisie à la mise en page pour que rien ne se chevauche.
 */
class MobileTige(
    val brasGauche: Int,
    val brasDroit: Int,
    val gauche: MobileBout,
    val droite: MobileBout
) {
    var unite = MobileRegles.UNITE_MIN

    val longueurGauche: Float get() = brasGauche * unite
    val longueurDroite: Float get() = brasDroit * unite

    /** Poids de tout ce qui pend à cette tige (les tiges elles-mêmes ne pèsent rien). */
    fun masse(): Int = masse(gauche) + masse(droite)

    /** Vrai si cette tige **et toutes celles qui pendent dessous** sont exactement équilibrées. */
    fun equilibree(): Boolean {
        if (!equilibreeSeule()) return false
        val g = gauche
        val d = droite
        if (g is BoutTige && !g.tige.equilibree()) return false
        if (d is BoutTige && !d.tige.equilibree()) return false
        return true
    }

    /** Cette tige seule (sans regarder dessous) est-elle équilibrée ? */
    fun equilibreeSeule(): Boolean = masse(gauche) * brasGauche == masse(droite) * brasDroit

    /** Vrai si tous les crochets sous cette tige portent un objet. */
    fun garnie(): Boolean = garni(gauche) && garni(droite)

    /** Toutes les tiges de l'arbre, celle-ci d'abord. */
    fun tiges(): List<MobileTige> {
        val out = ArrayList<MobileTige>()
        fun visiter(t: MobileTige) {
            out += t
            (t.gauche as? BoutTige)?.let { visiter(it.tige) }
            (t.droite as? BoutTige)?.let { visiter(it.tige) }
        }
        visiter(this)
        return out
    }

    /** Tous les crochets de l'arbre, de gauche à droite. */
    fun crochets(): List<BoutCrochet> {
        val out = ArrayList<BoutCrochet>()
        fun visiter(b: MobileBout) {
            when (b) {
                is BoutCrochet -> out += b
                is BoutTige -> {
                    visiter(b.tige.gauche)
                    visiter(b.tige.droite)
                }
            }
        }
        visiter(gauche)
        visiter(droite)
        return out
    }

    companion object {
        fun masse(b: MobileBout): Int = when (b) {
            is BoutTige -> b.tige.masse()
            is BoutCrochet -> b.objet?.masse ?: 0
        }

        fun garni(b: MobileBout): Boolean = when (b) {
            is BoutTige -> b.tige.garnie()
            is BoutCrochet -> b.objet != null
        }
    }
}

/** Dimensions du mobile, en mètres et kilogrammes (repère physique, Y vers le haut). */
object MobileRegles {
    /** Longueur du fil entre un bout de tige et ce qui pend dessous. */
    const val FIL = 0.3f

    /** Longueur du fil qui tient la tige du haut au plafond. */
    const val FIL_RACINE = 0.5f

    /**
     * Hauteur du nœud au-dessus de la tige : c'est l'étrier, le petit triangle de fil par lequel
     * la tige est suspendue.
     *
     * C'est **lui seul** qui rend une tige stable. Les objets pendent à des fils, donc leur poids
     * agit au bout de la tige quelle que soit la longueur du fil : rien ne rappelle la tige
     * vers l'horizontale, sinon le fait qu'elle est suspendue au-dessus d'elle-même. Une tige
     * déséquilibrée penche d'un angle dont la tangente vaut le déséquilibre divisé par cette
     * hauteur. À 6 cm, une erreur d'un centimètre faisait déjà dix degrés et les tiges se
     * dressaient à la verticale ; à 22 cm la pente se lit sans s'affoler.
     */
    const val ETRIER = 0.22f

    /** Pente (radians) au-delà de laquelle une tige rencontre sa butée : voir [MobileMonde.step]. */
    const val BUTEE = 0.17f

    /** Raideur de la butée : un dépassement d'environ 1/RAIDEUR radian sous le pire déséquilibre. */
    const val RAIDEUR_BUTEE = 10f

    /** Poids apparent d'une tige pour pendre (kg) : il tend son fil sans peser sur l'équilibre. */
    const val TENSION = 2f

    /** Longueur minimale d'un cran. */
    const val UNITE_MIN = 0.2f

    /** Écart minimal entre deux choses qui pendent côte à côte. */
    const val ECART = 0.12f

    const val RAYON_UNITAIRE = 0.11f

    /**
     * Masse d'une tige, pour son inertie seulement : son poids est compensé à chaque pas (voir
     * [MobileMonde.step]), sinon chaque tige ajouterait à son crochet un poids que le joueur ne
     * peut pas compter. Sans inertie, en revanche, une tige de quelques grammes sous deux objets
     * de neuf kilos ferait trembler le solveur.
     */
    const val MASSE_TIGE = 0.6f

    /**
     * Amortissement par seconde. Un vrai mobile se calme en brassant l'air ; le jeu ne peut pas
     * attendre une minute.
     */
    const val AMORTISSEMENT_TIGE = 4f
    const val AMORTISSEMENT_OBJET_LINEAIRE = 1.8f
    const val AMORTISSEMENT_OBJET_ANGULAIRE = 2.5f

    /**
     * Les pièces d'un mobile ne se touchent jamais : une collision entre voisins décale
     * silencieusement l'équilibre, et rien à l'écran ne dirait pourquoi.
     */
    const val CATEGORIE = 1 shl 21
}

/**
 * Le mobile monté dans le moteur.
 *
 * Une tige est un **corps centré sur son nœud** : son centre de masse est au point de
 * suspension, donc une tige vide pend droite, et ses bouts sont de simples points d'ancrage.
 *
 * Les fils sont des liaisons de distance en mode **barre** (`rope = false`). Une corde passe à
 * chaque pas de molle à tendue sous une charge permanente, ce qui faisait osciller une tige
 * légère de quatre degrés pour toujours ; une barre n'a pas d'état mou, et un objet qui pend ne
 * pousse jamais son fil.
 */
class MobileMonde(val racine: MobileTige, val plafond: Float) {

    val world = PhysWorld()

    private val crochetPlafond: PhysBody
    private val corpsTige = IdentityHashMap<MobileTige, PhysBody>()
    private val corpsObjet = IdentityHashMap<BoutCrochet, PhysBody>()
    private val filObjet = IdentityHashMap<BoutCrochet, DistanceJoint>()

    /** Pour chaque crochet, la tige qui le porte et le côté (-1 gauche, +1 droite). */
    private val porteur = IdentityHashMap<BoutCrochet, Pair<MobileTige, Int>>()

    /** Les fils qui tiennent les tiges (du plafond, ou d'une tige à une autre). */
    val filsTiges = ArrayList<DistanceJoint>()

    /** Les tiges et les crochets de l'arbre, listés une fois : la forme ne change jamais. */
    val tiges: List<MobileTige> = racine.tiges()
    val crochets: List<BoutCrochet> = racine.crochets()

    /** Pour chaque tige, le corps d'où pend son fil et le point d'attache dans son repère. */
    private val suspension = IdentityHashMap<MobileTige, Triple<PhysBody, Float, Float>>()

    /**
     * Multiplie l'amortissement de toutes les pièces : 1 dans l'air ordinaire, plus quand le
     * mobile juste doit se poser vite.
     */
    var amorti = 1f
        set(value) {
            if (value == field) return
            field = value
            for (c in corpsTige.values) amortir(c, tige = true)
            for (c in corpsObjet.values) amortir(c, tige = false)
        }

    private fun amortir(c: PhysBody, tige: Boolean) {
        c.linearDamping = MobileRegles.AMORTISSEMENT_OBJET_LINEAIRE * amorti
        c.angularDamping = amorti * if (tige) MobileRegles.AMORTISSEMENT_TIGE else MobileRegles.AMORTISSEMENT_OBJET_ANGULAIRE
    }

    init {
        crochetPlafond = PhysBody(0.05f, 0.05f, 0f).apply {
            category = MobileRegles.CATEGORIE
            collidesWith = MobileRegles.CATEGORIE.inv()
            x = 0f
            y = plafond
            lockPosition = true
            lockRotation = true
            refreshMass()
        }
        world.add(crochetPlafond)
        monter(racine, crochetPlafond, 0f, plafond, MobileRegles.FIL_RACINE)
    }

    fun corps(tige: MobileTige): PhysBody = corpsTige.getValue(tige)

    fun corps(crochet: BoutCrochet): PhysBody? = corpsObjet[crochet]

    private fun monter(tige: MobileTige, parent: PhysBody, px: Float, py: Float, fil: Float) {
        val noeudY = py - fil
        val y = noeudY - MobileRegles.ETRIER
        val longueur = tige.longueurGauche + tige.longueurDroite
        // Un disque centré sur le nœud, dont l'inertie est celle d'une tige de même longueur :
        // m·L²/12 = m·r²/2 donne r = L/√6. Les collisions sont coupées, sa forme ne compte pas.
        val corps = PhysBody.circle(max(0.05f, longueur / 2.449f), MobileRegles.MASSE_TIGE).apply {
            x = px
            this.y = y
            category = MobileRegles.CATEGORIE
            collidesWith = MobileRegles.CATEGORIE.inv()
        }
        amortir(corps, tige = true)
        world.add(corps)
        val j = DistanceJoint.between(parent, px, py, corps, px, noeudY, rope = false)
        world.addJoint(j)
        filsTiges += j
        corpsTige[tige] = corps
        // Le point d'attache dans le repère du parent : tout est encore droit, il suffit de
        // retrancher sa position.
        suspension[tige] = Triple(parent, px - parent.x, py - parent.y)

        brancher(tige, tige.gauche, corps, px - tige.longueurGauche, y, -1)
        brancher(tige, tige.droite, corps, px + tige.longueurDroite, y, +1)
    }

    private fun brancher(tige: MobileTige, bout: MobileBout, corps: PhysBody, x: Float, y: Float, cote: Int) {
        when (bout) {
            is BoutTige -> monter(bout.tige, corps, x, y, MobileRegles.FIL)
            is BoutCrochet -> {
                porteur[bout] = tige to cote
                bout.objet?.let { poser(bout, it) }
            }
        }
    }

    /** Point d'attache d'un bout de tige, dans le repère de la tige. */
    private fun ancrageLocal(tige: MobileTige, cote: Int): Float =
        if (cote < 0) -tige.longueurGauche else tige.longueurDroite

    /** Position monde du bout de tige qui porte [crochet]. */
    fun bout(crochet: BoutCrochet, out: FloatArray) {
        val (tige, cote) = porteur.getValue(crochet)
        corps(tige).localToWorld(ancrageLocal(tige, cote), 0f, out)
    }

    /**
     * Position monde du crochet lui-même, là où se nouerait le haut d'un objet : au bout de son
     * fil, qui pend toujours à la verticale. Pour un crochet garni, c'est le haut de l'objet.
     */
    fun crochet(crochet: BoutCrochet, out: FloatArray) {
        val o = corpsObjet[crochet]
        val objet = crochet.objet
        if (o != null && objet != null) {
            o.localToWorld(0f, objet.rayon, out)
        } else {
            bout(crochet, out)
            out[1] -= MobileRegles.FIL
        }
    }

    /** Accroche [objet] à [crochet] (qui doit être vide), dans le monde comme dans le modèle. */
    fun accrocher(crochet: BoutCrochet, objet: MobileObjet) {
        require(crochet.objet == null) { "crochet déjà garni" }
        crochet.objet = objet
        poser(crochet, objet)
        world.wakeAll()
    }

    /** Décroche ce que porte [crochet] et le rend. */
    fun decrocher(crochet: BoutCrochet): MobileObjet? {
        val objet = crochet.objet ?: return null
        crochet.objet = null
        filObjet.remove(crochet)?.let { world.removeJoint(it) }
        corpsObjet.remove(crochet)?.let { world.remove(it) }
        world.wakeAll()
        return objet
    }

    private fun poser(crochet: BoutCrochet, objet: MobileObjet) {
        val (tige, cote) = porteur.getValue(crochet)
        val corpsT = corps(tige)
        val p = FloatArray(2)
        corpsT.localToWorld(ancrageLocal(tige, cote), 0f, p)
        val haut = p[1] - MobileRegles.FIL
        val corps = PhysBody.circle(objet.rayon, objet.masse.toFloat()).apply {
            x = p[0]
            y = haut - objet.rayon
            // Il part avec la vitesse du bout de tige : accroché à une tige qui se balance, il
            // ne doit pas recevoir un coup sec.
            vx = corpsT.vx
            vy = corpsT.vy
            category = MobileRegles.CATEGORIE
            collidesWith = MobileRegles.CATEGORIE.inv()
        }
        amortir(corps, tige = false)
        world.add(corps)
        val j = DistanceJoint.between(corpsT, p[0], p[1], corps, p[0], haut, rope = false)
        world.addJoint(j)
        corpsObjet[crochet] = corps
        filObjet[crochet] = j
    }

    /**
     * Fait avancer le monde.
     *
     * Le poids des tiges est compensé : elles ont une inertie, mais elles ne pèsent rien dans
     * l'équilibre, comme le veut le compte en entiers. La force qui les porte s'applique **au
     * nœud**, pas à leur centre : la tige pend alors sous son nœud comme un pendule, et une tige
     * vide revient à l'horizontale. Portée par son centre, elle n'avait plus aucun rappel et
     * restait figée à soixante degrés une fois ses objets décrochés.
     */
    /** Tampons de calcul, réutilisés à chaque pas plutôt qu'alloués. */
    private val p = FloatArray(2)
    private val q = FloatArray(2)

    fun step(dt: Float) {
        for ((tige, c) in corpsTige) {
            c.localToWorld(0f, MobileRegles.ETRIER, p)
            c.applyForceAtWorldPoint(0f, c.mass * world.gravity, p[0], p[1])
            tendre(tige, c)
            buter(tige, c, dt)
        }
        world.stepFrame(dt)
    }

    /**
     * Tend le fil d'une tige : un poids à son milieu, et la même force vers le haut au point d'où
     * pend ce fil.
     *
     * Une tige ne pèse rien (voir plus haut) ; vide, rien ne la tirait donc vers le bas, et au
     * bout de son fil rigide elle restait là où on l'avait lancée — au-dessus de sa tige mère,
     * sous le plafond, en travers des autres. Les deux forces s'annulent dans le fil : la tige
     * pend comme si elle pesait, mais celle du dessus ne sent rien, et le compte en entiers reste
     * celui que voit l'équilibre. Posé au milieu, sous le nœud, ce poids la redresse aussi : une
     * tige vide ne vacille plus au moindre mouvement de sa mère.
     */
    private fun tendre(tige: MobileTige, c: PhysBody) {
        val (parent, ax, ay) = suspension.getValue(tige)
        val f = MobileRegles.TENSION * world.gravity
        c.applyForce(0f, -f)
        if (parent !== crochetPlafond) {
            parent.localToWorld(ax, ay, q)
            parent.applyForceAtWorldPoint(0f, f, q[0], q[1])
        }
    }

    /**
     * La butée : au-delà de [MobileRegles.BUTEE], un ressort ramène la tige.
     *
     * Une tige garnie d'un seul côté se dresserait presque à la verticale (la tangente de sa pente
     * vaut son bras divisé par l'étrier), et tout ce qui pend de l'autre côté filerait vers le
     * haut : le joueur visait des crochets qui s'envolaient à chaque objet posé. Le sens de la
     * pente suffit à dire quel côté est lourd ; son ampleur, elle, ne sert qu'aux petits écarts,
     * qui restent bien en deçà de la butée.
     *
     * La raideur suit la charge et le bras, pour que le dépassement reste de quelques degrés quel
     * que soit le poids. L'amortissement est borné par l'inertie propre de la tige : il agit sur
     * elle seule, d'un pas à l'autre, et ne doit pas renverser sa vitesse.
     */
    private fun buter(tige: MobileTige, c: PhysBody, dt: Float) {
        val exces = kotlin.math.abs(c.angle) - MobileRegles.BUTEE
        if (exces <= 0f) return
        // Une tige vide a sa butée aussi : sans poids qui la rappelle, elle tournoyait sous une
        // tige qui bougeait.
        val charge = tige.masse() + 1f
        val bras = max(tige.longueurGauche, tige.longueurDroite)
        val k = MobileRegles.RAIDEUR_BUTEE * world.gravity * charge * bras
        val amortissement = kotlin.math.min(
            2f * sqrt(k * charge * bras * bras),
            0.5f * c.inertia / dt
        )
        c.applyTorque(-kotlin.math.sign(c.angle) * k * exces - amortissement * c.omega)
    }

    /** Pente d'une tige en degrés (positif : le côté droit monte). */
    fun penteDeg(tige: MobileTige): Float = corps(tige).angle * 57.29578f
}

/**
 * Mise en page : la longueur d'un cran, tige par tige, pour que deux choses qui pendent côte à
 * côte ne se chevauchent pas une fois le mobile équilibré.
 */
object MobileMiseEnPage {

    /** Étendue d'un bout à gauche et à droite de son point d'accroche, en mètres. */
    private fun etendue(b: MobileBout, rayonCrochetVide: Float): Pair<Float, Float> = when (b) {
        is BoutCrochet -> {
            val r = b.objet?.rayon ?: rayonCrochetVide
            r to r
        }
        is BoutTige -> etendue(b.tige, rayonCrochetVide)
    }

    /** Fixe [MobileTige.unite] dans tout l'arbre et rend l'étendue de [tige] autour de son nœud. */
    fun etendue(tige: MobileTige, rayonCrochetVide: Float): Pair<Float, Float> {
        val (gG, gD) = etendue(tige.gauche, rayonCrochetVide)
        val (dG, dD) = etendue(tige.droite, rayonCrochetVide)
        val crans = tige.brasGauche + tige.brasDroit
        tige.unite = max(MobileRegles.UNITE_MIN, (gD + dG + MobileRegles.ECART) / crans)
        return (tige.longueurGauche + gG) to (tige.longueurDroite + dD)
    }

    /** Profondeur de l'arbre sous le plafond, jusqu'au bas du plus bas objet possible. */
    fun hauteur(tige: MobileTige, rayonMax: Float): Float {
        fun h(b: MobileBout): Float = when (b) {
            is BoutCrochet -> MobileRegles.FIL + 2f * rayonMax
            is BoutTige -> MobileRegles.FIL + MobileRegles.ETRIER + max(h(b.tige.gauche), h(b.tige.droite))
        }
        return MobileRegles.FIL_RACINE + MobileRegles.ETRIER + max(h(tige.gauche), h(tige.droite))
    }
}
