package com.Atom2Universe.app.games.infernale

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.Shape
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * La vue du tableau : la caverne, la machine, et le doigt qui pose les pieces.
 *
 * ## Pourquoi on dessine les corps et pas les jolies vignettes
 *
 * La bibliotheque `art/` sait dessiner cent vingt-six pieces, et elles sont belles. Mais
 * ce sont des **icones** : des silhouettes centrees dans un carre, qui ne suivent pas les
 * cotes reelles d'une rampe de deux metres ni l'angle d'un domino en train de tomber.
 * Les montrer sur le tableau mentirait sur ce que fait la physique, et un casse-tete ou
 * l'image ne correspond pas au calcul est injouable.
 *
 * Ici on dessine donc les corps eux-memes, tels que le moteur les voit — mais **habilles**
 * selon ce qu'ils representent. C'est le role de [Element], pose sur `PhysBody.tag` par
 * l'atelier : une planche recoit son bois et ses boulons, un domino ses points, une peau
 * de tambour son cercle. La forme reste celle du moteur, au pixel pres ; seule la peinture
 * change. Les vignettes gardent leur emploi la ou elles sont justes : dans la reserve, en
 * bas, ou une piece n'est encore qu'un choix.
 *
 * ## Le fil, le canevas, le pas de temps
 *
 * Un seul fil dessine et simule, le canevas est materiel, et le temps ecoule est plafonne :
 * ce sont les trois regles de la liste de controle des vues de jeu du projet, et elles ne
 * se negocient pas.
 */
class InfernaleView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : SurfaceView(ctx, attrs), SurfaceHolder.Callback, Runnable {

    interface Listener {
        fun surVictoire()
        fun surEchec()
        fun surChangement()
    }

    var listener: Listener? = null

    /** La partie en cours. Toute lecture depuis le fil de rendu passe par [verrou]. */
    private var partie: Partie? = null
    private val verrou = Object()

    /** Le type que le doigt s'apprete a poser, ou `null` s'il ne pose rien. */
    var typeChoisi: TypePiece? = null
        set(value) {
            // Pose par l'activite, donc depuis le fil principal, alors que le fantome et
            // la selection sont lus par le fil de rendu. Le verrou n'est pas une
            // precaution de principe : sans lui, choisir une piece pendant que l'image se
            // peint peut laisser [apercuPiece] a moitie remplace.
            synchronized(verrou) {
                field = value
                viser(null)
                selection = -1
            }
        }

    /**
     * Le reglage de chaque type — la pente d'une rampe, la direction d'un ventilateur.
     *
     * Un reglage **par type** et pas un seul pour tous : on ne veut pas que choisir un
     * ventilateur remette la pente des rampes a zero. Chaque piece garde le sien d'un
     * bout a l'autre du tableau.
     */
    private val reglages = HashMap<TypePiece, Float>()

    /** Le sens de chaque type : le curseur regle la quantite, le miroir regle le cote. */
    private val miroirs = HashMap<TypePiece, Boolean>()

    fun reglage(type: TypePiece): Float = reglages[type] ?: reglageParDefaut(type)

    fun miroir(type: TypePiece): Boolean = miroirs[type] ?: false

    /** Retire la piece designee, s'il y en a une. */
    fun supprimerDesignee() {
        synchronized(verrou) {
            val p = partie ?: return@synchronized
            if (selection < 0) return@synchronized
            p.reprendre(selection)
            selection = -1
            viser(null)
        }
    }

    /** Retourne le type [type], et la piece designee si c'en est une du meme type. */
    fun basculerMiroir(type: TypePiece) {
        val valeur = !miroir(type)
        miroirs[type] = valeur
        appliquerAuDesigne(type) { it.copy(miroir = valeur) }
    }

    fun reglage(type: TypePiece, valeur: Float) {
        reglages[type] = valeur
        appliquerAuDesigne(type) { it.copy(reglage = valeur) }
    }

    /**
     * Applique un changement de reglage a la piece designee, si elle est du bon type.
     *
     * C'est ce qui permet d'ajuster une rampe deja posee au lieu de la reprendre et de la
     * reposer au juge. Le refus eventuel de [Partie.deplacer] — la piece retournee
     * chevaucherait sa voisine — est silencieux : la piece reste comme elle etait, ce que
     * le joueur voit tout de suite.
     */
    private fun appliquerAuDesigne(type: TypePiece, changer: (Pose) -> Pose) {
        val index = selection
        if (index < 0) return
        synchronized(verrou) {
            val p = partie ?: return@synchronized
            val pose = p.placees().getOrNull(index) ?: return@synchronized
            if (pose.type == type) p.deplacer(index, changer(pose))
        }
    }

    /** La piece posee que le doigt a designee, ou -1. Elle se dessine en surbrillance. */
    var selection = -1
        private set

    /**
     * Ou la piece choisie irait, si elle y a le droit, et les corps qui la dessinent.
     *
     * Les corps sont gardes avec la pose, et ce n'est pas de l'avarice : le fantome se
     * redessine a chaque image, et le refabriquer a chaque fois faisait naitre quatre
     * corps et trois liaisons soixante fois par seconde pendant qu'un doigt traine une
     * poulie. Une pose ne change qu'au mouvement du doigt ; les corps aussi.
     */
    private var apercu: Pose? = null
    private var apercuPiece: Piece? = null
    private var apercuRefus = Refus.OK

    private fun viser(pose: Pose?, refus: Refus = Refus.OK) {
        if (pose == null) {
            apercu = null
            apercuPiece = null
            return
        }
        if (pose != apercu) {
            apercu = pose
            apercuPiece = pose.creer()
        }
        apercuRefus = refus
    }

    private var fil: Thread? = null
    @Volatile private var tourne = false
    private var dernier = 0L
    private var reste = 0f
    private var victoireAnnoncee = false
    private var echecAnnonce = false

    /** Horloge d'animation, en secondes. Elle tourne meme machine a l'arret. */
    private var horloge = 0f

    /** Avancement de l'ouverture du portail, de 0 a 1. */
    private var ouverture = 0f

    // ── Peintures ────────────────────────────────────────────────────────────

    private val fond = Paint()
    private val brume = Paint().apply { isAntiAlias = true }
    private val roche = Paint().apply { color = 0xFF171D30.toInt(); isAntiAlias = true }
    private val rocheClaire = Paint().apply { color = 0xFF222A42.toInt(); isAntiAlias = true }
    private val terre = Paint().apply { color = 0xFF2E2620.toInt() }
    private val terreHaut = Paint().apply { color = 0xFF4A3A28.toInt() }
    private val herbe = Paint().apply {
        color = 0xFF5E4A30.toInt(); strokeWidth = 2f; isAntiAlias = true
    }
    private val cailloux = Paint().apply { color = 0xFF3B3129.toInt(); isAntiAlias = true }

    private val bois = Paint().apply { color = 0xFFB27C48.toInt(); isAntiAlias = true }
    private val boisSombre = Paint().apply { color = 0xFF7C5430.toInt(); isAntiAlias = true }
    private val grain = Paint().apply {
        color = 0x66000000; strokeWidth = 1.5f; isAntiAlias = true
    }
    private val pierre = Paint().apply { color = 0xFF6C7285.toInt(); isAntiAlias = true }
    private val pierreClaire = Paint().apply { color = 0xFF8C93A8.toInt(); isAntiAlias = true }
    private val fer = Paint().apply { color = 0xFF4E5568.toInt(); isAntiAlias = true }
    private val ferClair = Paint().apply { color = 0xFF7C8499.toInt(); isAntiAlias = true }
    private val caoutchouc = Paint().apply { color = 0xFFC03A4E.toInt(); isAntiAlias = true }
    private val caoutchoucClair = Paint().apply { color = 0xFFFF8676.toInt(); isAntiAlias = true }
    private val ivoire = Paint().apply { color = 0xFFE9E2CE.toInt(); isAntiAlias = true }
    private val ivoireOmbre = Paint().apply { color = 0xFFBFB6A0.toInt(); isAntiAlias = true }
    private val pointDomino = Paint().apply { color = 0xFF3A3226.toInt(); isAntiAlias = true }
    private val peau = Paint().apply { color = 0xFFE0C088.toInt(); isAntiAlias = true }
    private val cercle = Paint().apply { color = 0xFF8A5A32.toInt(); isAntiAlias = true }
    private val corde = Paint().apply {
        color = 0xFFCBA76A.toInt(); strokeWidth = 3f; isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val contour = Paint().apply {
        color = 0xCC0A0E1C.toInt(); style = Paint.Style.STROKE
        strokeWidth = 2f; isAntiAlias = true
    }
    private val billeP = Paint().apply { color = 0xFFD8DEEC.toInt(); isAntiAlias = true }
    private val billeReflet = Paint().apply { color = 0xFFFFFFFF.toInt(); isAntiAlias = true }
    private val billeOmbre = Paint().apply { color = 0x66000000; isAntiAlias = true }
    private val traineeP = Paint().apply {
        color = 0x5588C8FF.toInt(); strokeWidth = 3f; isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    private val vent = Paint().apply {
        color = 0x55BFE4FF.toInt(); strokeWidth = 2.5f; isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    private val or = Paint().apply { color = 0xFFFFC65A.toInt(); isAntiAlias = true }
    private val orPale = Paint().apply { color = 0x66FFC65A; isAntiAlias = true }
    private val torcheP = Paint().apply { color = 0xFFFFB33C.toInt(); isAntiAlias = true }
    private val halo = Paint().apply { color = 0x22FF9A2E; isAntiAlias = true }
    private val apercuOk = Paint().apply { color = 0x9955E08A.toInt(); isAntiAlias = true }
    private val apercuNon = Paint().apply { color = 0x99E05555.toInt(); isAntiAlias = true }
    private val priseFond = Paint().apply { color = 0xCC0B1020.toInt(); isAntiAlias = true }
    private val priseCoeur = Paint().apply { color = 0xFF9BC2FF.toInt(); isAntiAlias = true }
    private val marqueur = Paint().apply {
        color = 0xFF9BC2FF.toInt(); style = Paint.Style.STROKE
        strokeWidth = 3f; isAntiAlias = true
    }

    // Tampons de travail, alloues une fois : une allocation par image dans une boucle de
    // rendu est le genre de detail qui coute dix images par seconde et ne se voit nulle part.
    private val coins = FloatArray(8)
    // Trois flottants, pas deux : `partWorld` rend x, y **et l'angle**. La taille a ete
    // deduite de la signature au lieu d'etre lue dans la documentation, et la vue
    // plantait des la premiere image.
    private val centre = FloatArray(3)
    private val ancre = FloatArray(3)
    private val trace = Path()
    private val segments = FloatArray(4 * 64)

    // La traine de la bille : un anneau de positions, dessine en trois tronçons de plus
    // en plus pales. Trois `drawLines` et pas un `Path` — voir les notes du trebuchet :
    // un chemin ferme force un masque logiciel, une ligne ne coute rien.
    private val traineeX = FloatArray(TRAINEE)
    private val traineeY = FloatArray(TRAINEE)
    private var traineeTete = 0
    private var traineeNombre = 0
    private val traineeSegs = FloatArray(4 * TRAINEE)

    // Le decor, tire une fois par tableau : sa graine est celle du tableau, donc la meme
    // caverne revient quand on rejoue.
    private val rocherX = FloatArray(DECOR)
    private val rocherY = FloatArray(DECOR)
    private val rocherR = FloatArray(DECOR)
    private var decorPret = false

    private var echelle = 40f
    private var origineX = 0f
    private var basY = 0f
    private var largeurVue = 0
    private var hauteurVue = 0

    init {
        holder.addCallback(this)
        setWillNotDraw(true)
    }

    fun jouer(nouvelle: Partie) {
        synchronized(verrou) {
            partie = nouvelle
            victoireAnnoncee = false
            echecAnnonce = false
            ouverture = 0f
            viser(null)
            selection = -1
            traineeNombre = 0
            traineeTete = 0
            decorPret = false
        }
        // **Recadrer ici aussi**, et pas seulement quand la surface change de taille.
        // Le tableau est tire sur un fil a part, donc il arrive presque toujours apres
        // que la surface a pris ses mesures : sans ce rappel, le cadrage garde son
        // echelle par defaut et le monde s'affiche a la mauvaise taille jusqu'a ce que
        // l'ecran tourne.
        if (largeurVue > 0 && hauteurVue > 0) cadrer(largeurVue, hauteurVue)
    }

    fun partieCourante(): Partie? = synchronized(verrou) { partie }

    /** Execute [action] sur la partie sans risquer que le fil de rendu la lise entre-temps. */
    fun <T> surPartie(action: (Partie) -> T): T? = synchronized(verrou) {
        partie?.let(action)
    }

    /** Oublie la traine : a faire quand on remonte le tableau. */
    fun effacerTrainee() = synchronized(verrou) {
        traineeNombre = 0
        traineeTete = 0
        victoireAnnoncee = false
        echecAnnonce = false
        ouverture = 0f
    }

    // ── Boucle ───────────────────────────────────────────────────────────────

    override fun surfaceCreated(h: SurfaceHolder) = reprendre()
    override fun surfaceDestroyed(h: SurfaceHolder) = suspendre()
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {
        largeurVue = w
        hauteurVue = ht
        cadrer(w, ht)
    }

    fun reprendre() {
        if (tourne) return
        tourne = true
        dernier = System.nanoTime()
        reste = 0f
        fil = Thread(this, "InfernalePhysique").also { it.start() }
    }

    fun suspendre() {
        tourne = false
        fil?.join(500)
        fil = null
    }

    override fun run() {
        while (tourne) {
            val maintenant = System.nanoTime()
            // Le plafond est ce qui empeche une image en retard — un changement
            // d'application, un ramasse-miettes — de faire avancer la machine d'un
            // demi-seconde d'un coup et de tout traverser.
            val ecoule = ((maintenant - dernier) / 1_000_000_000f).coerceAtMost(0.1f)
            dernier = maintenant
            horloge += ecoule

            var gagneMaintenant = false
            var echoueMaintenant = false
            synchronized(verrou) {
                val p = partie
                if (p != null && p.lancee) {
                    reste += ecoule
                    while (reste >= PAS) {
                        p.avancer(PAS)
                        reste -= PAS
                        noterTrainee(p)
                    }
                    if (p.gagne && !victoireAnnoncee) {
                        victoireAnnoncee = true
                        gagneMaintenant = true
                    }
                    if (!p.gagne && p.echoue && !echecAnnonce) {
                        echecAnnonce = true
                        echoueMaintenant = true
                    }
                } else {
                    reste = 0f
                }
                if (victoireAnnoncee && ouverture < 1f) {
                    ouverture = (ouverture + ecoule / 0.55f).coerceAtMost(1f)
                }
            }
            if (gagneMaintenant) post { listener?.surVictoire() }
            if (echoueMaintenant) post { listener?.surEchec() }

            val canevas = verrouillerCanevas()
            if (canevas == null) {
                Thread.sleep(16)
                continue
            }
            try {
                synchronized(verrou) { peindre(canevas) }
            } finally {
                holder.unlockCanvasAndPost(canevas)
            }
            Thread.sleep(2)
        }
    }

    private fun verrouillerCanevas(): Canvas? = try {
        // Canevas materiel : c'est lui qui evite que Skia rastérise les formes au
        // processeur, et la difference se compte en dizaines d'images par seconde.
        if (holder.surface?.isValid == true) holder.lockHardwareCanvas() else null
    } catch (_: IllegalStateException) {
        null
    }

    private fun noterTrainee(p: Partie) {
        val b = p.plateau.bille ?: return
        traineeX[traineeTete] = b.x
        traineeY[traineeTete] = b.y
        traineeTete = (traineeTete + 1) % TRAINEE
        if (traineeNombre < TRAINEE) traineeNombre++
    }

    // ── Cadrage ──────────────────────────────────────────────────────────────

    /**
     * Ouvre la camera sur ce qui compte : la bille, le bouton, et de la place autour.
     *
     * Le plateau fait seize metres ; on n'en montre que la fenetre utile, et le joueur
     * ecarte les doigts pour voir le reste. Tenter de tout montrer d'un coup rendrait la
     * bille grosse comme une tete d'epingle.
     */
    private fun cadrer(w: Int, h: Int) {
        val p = synchronized(verrou) { partie } ?: return
        if (w <= 0 || h <= 0) return
        val t = p.tableau
        val largeurMonde = (t.vueMaxX - t.vueMinX).coerceAtLeast(1f)
        val hauteurMonde = (t.vueMaxY + SOL_VISIBLE).coerceAtLeast(1f)
        echelle = minOf(w / largeurMonde, h / hauteurMonde)
        origineX = w / 2f - (t.vueMinX + t.vueMaxX) / 2f * echelle
        // Tout le mou vertical passe **au-dessus**, et pas moitie-moitie. Centrer laissait
        // un quart de l'ecran de terre sous les pieds pour rien, alors que la place utile
        // est en haut : c'est la qu'on pose les premieres rampes.
        basY = h - SOL_VISIBLE * echelle
        // Les bornes du zoom sont absolues : « tout le plateau tient a l'ecran » d'un cote,
        // « on voit la moitie d'un domino » de l'autre. Les rapporter au cadrage d'arrivee
        // donnait une course differente a chaque tableau, et souvent trop courte.
        echelleMin = w / (Plateau.LARGEUR + 1f)
        echelleMax = maxOf(echelleMin * 6f, 260f)
        preparerDecor(t)
    }

    /** Echelles extremes, en pixels par metre. Posees au cadrage, absolues ensuite. */
    private var echelleMin = 20f
    private var echelleMax = 400f

    /** Remet la camera ou elle etait en arrivant. */
    fun recadrer() {
        synchronized(verrou) {
            if (largeurVue > 0 && hauteurVue > 0) cadrer(largeurVue, hauteurVue)
        }
    }

    /**
     * Grossit et deplace la camera.
     *
     * [ancreX]/[ancreY] est le point de l'ecran qui ne doit pas bouger — le milieu des deux
     * doigts **avant** le mouvement — et [dx]/[dy] le deplacement de ce milieu depuis. Les
     * prendre au meme instant etait l'erreur : en ancrant sur le milieu d'apres tout en
     * ajoutant son deplacement, on appliquait le glissement deux fois, et le monde fuyait
     * sous la main a chaque pincement.
     */
    private fun bougerCamera(dScale: Float, dx: Float, dy: Float, ancreX: Float, ancreY: Float) {
        val vise = (echelle * dScale).coerceIn(echelleMin, echelleMax)
        val facteur = vise / echelle
        origineX = ancreX - (ancreX - origineX) * facteur + dx
        basY = ancreY - (ancreY - basY) * facteur + dy
        echelle = vise
        borner()
    }

    /** Fait glisser la camera de [dx]/[dy] pixels. */
    private fun glisserCamera(dx: Float, dy: Float) {
        origineX += dx
        basY += dy
        borner()
    }

    /**
     * Empeche la camera de partir dans le vide.
     *
     * **Et surtout, elle ne descend pas sous terre.** On pouvait remonter la vue au point de
     * n'avoir que de la terre a l'ecran, ce qui n'est pas seulement laid : il n'y a rien a
     * faire sous le sol, donc c'est un etat dont on ne peut que vouloir sortir. La ligne de
     * sol reste donc toujours au ras du bas de l'ecran ou plus bas, jamais au-dessus.
     */
    private fun borner() {
        val w = largeurVue.toFloat()
        val h = hauteurVue.toFloat()
        if (w <= 0f || h <= 0f) return

        // Horizontal : on garde toujours un bout de plateau a l'ecran.
        val demi = Plateau.LARGEUR / 2f
        val marge = w * 0.35f
        val gauche = ex(-demi)
        val droite = ex(demi)
        if (gauche > w - marge) origineX -= gauche - (w - marge)
        if (droite < marge) origineX += marge - droite

        // Vertical : le sol au ras du bas au plus haut, le plafond du plateau au plus bas.
        val plancher = h - SOL_VISIBLE * echelle
        val plafond = (Plateau.HAUTEUR + 1f) * echelle
        basY = basY.coerceAtLeast(plancher).coerceAtMost(maxOf(plancher, plafond))
    }

    private fun ex(x: Float) = origineX + x * echelle
    private fun ey(y: Float) = basY - y * echelle

    /**
     * Tire la caverne. Le hasard est celui du tableau, donc elle ne bouge pas d'un essai
     * a l'autre — un decor qui change a chaque relance donne l'impression que le jeu
     * n'est pas le meme, et rend impossible de comparer deux tentatives a l'oeil.
     */
    private fun preparerDecor(t: Tableau) {
        var graine = t.graine * 6364136223846793005L + 1442695040888963407L
        fun suivant(): Float {
            graine = graine * 6364136223846793005L + 1442695040888963407L
            return ((graine ushr 40).toInt() and 0xFFFFFF) / 16777216f
        }
        val l = t.vueMinX
        val r = t.vueMaxX
        for (i in 0 until DECOR) {
            rocherX[i] = l + suivant() * (r - l)
            rocherY[i] = -0.05f - suivant() * 0.35f
            rocherR[i] = 0.04f + suivant() * 0.09f
        }
        halo.shader = RadialGradient(
            0f, 0f, echelle * 1.6f,
            0x66FF9A2E, 0x00FF9A2E, Shader.TileMode.CLAMP
        )
        fond.shader = LinearGradient(
            0f, 0f, 0f, hauteurVue.toFloat().coerceAtLeast(1f),
            0xFF080B16.toInt(), 0xFF1B1526.toInt(), Shader.TileMode.CLAMP
        )
        decorPret = true
    }

    // ── Peinture ─────────────────────────────────────────────────────────────

    private fun peindre(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fond)
        val p = partie ?: return
        if (!decorPret) preparerDecor(p.tableau)

        peindreParois(c, p)
        peindreSol(c)
        p.plateau.socle?.let { boite(c, it, 0, pierre, pierreClaire) }
        peindrePortail(c, p)

        for (piece in p.plateau.pieces) peindrePiece(c, piece)
        for (piece in p.plateau.pieces) piece.souffle?.let { peindreVent(c, it) }

        peindreTrainee(c)
        p.plateau.bille?.let { peindreBille(c, it) }

        // La piece designee, en dernier : elle doit se voir par-dessus ses voisines.
        if (selection >= 0) {
            p.plateau.pieces.getOrNull(selection)?.let { encadrer(c, it) }
            p.placees().getOrNull(selection)?.let { peindrePoignees(c, it) }
        }

        apercuPiece?.let { fantome ->
            val teinte = if (apercuRefus == Refus.OK) apercuOk else apercuNon
            for (corps in fantome.corps) silhouette(c, corps, teinte)
            fantome.souffle?.let { peindreVent(c, it) }
        }
    }

    /**
     * Les parois du plateau, dessinees **la ou elles sont**.
     *
     * Il y avait deux bandes plates peintes sur les cotes de l'ecran, qui n'existaient
     * nulle part dans le monde : elles suivaient la fenetre et pas le plateau, si bien
     * qu'en se deplacant on voyait deux rectangles bleus glisser sur rien. Le moteur a
     * deux murs, a une position connue ; on dessine ceux-la, et ce qu'on voit redevient ce
     * qui existe.
     */
    private fun peindreParois(c: Canvas, p: Partie) {
        for (mur in p.plateau.murs) {
            mur.updateAabb()
            val gauche = ex(mur.aabbMinX)
            val droite = ex(mur.aabbMaxX)
            if (droite < 0f || gauche > width) continue
            c.drawRect(gauche, 0f, droite, height.toFloat(), roche)
            // Un liseret plus clair du cote du tableau : c'est ce qui donne l'epaisseur.
            val versLInterieur = if (mur.x < 0f) droite else gauche
            val liseret = echelle * 0.07f
            c.drawRect(
                minOf(versLInterieur, versLInterieur - liseret), 0f,
                maxOf(versLInterieur, versLInterieur - liseret), height.toFloat(), rocheClaire
            )
        }
    }

    private fun peindreSol(c: Canvas) {
        val y = ey(0f)
        c.drawRect(0f, y, width.toFloat(), height.toFloat(), terre)
        c.drawRect(0f, y, width.toFloat(), y + echelle * 0.06f, terreHaut)
        for (i in 0 until DECOR) {
            c.drawCircle(ex(rocherX[i]), ey(rocherY[i]), rocherR[i] * echelle, cailloux)
        }
        // Quelques brins secs plantes sur la ligne de sol : trois traits par brin, en un
        // seul `drawLines`.
        var n = 0
        var i = 0
        while (i < DECOR && n + 4 <= segments.size) {
            val bx = ex(rocherX[i])
            segments[n] = bx
            segments[n + 1] = y
            segments[n + 2] = bx + (if (i % 2 == 0) 1f else -1f) * echelle * 0.05f
            segments[n + 3] = y - echelle * 0.12f
            n += 4
            i += 2
        }
        if (n > 0) c.drawLines(segments, 0, n, herbe)
    }

    /**
     * Le portail : une porte de pierre qui s'ouvre quand la machine a gagne.
     *
     * Le bouton du moteur n'est qu'une zone de detection — un booleen. Tout ce qui suit
     * est du dessin, et c'est deliberement la que vit la recompense : une porte qui se
     * leve sur une lueur doree se lit en un quart de seconde, la ou un rectangle qui
     * change de couleur ne dit rien a personne.
     */
    private fun peindrePortail(c: Canvas, p: Partie) {
        val b = p.plateau.bouton ?: return
        val z = b.zone
        val demiL = z.halfW * echelle * 1.9f
        val demiH = z.halfH * echelle
        val cx = ex(z.x)
        val cy = ey(z.y)
        val hauteur = demiH * 3.4f

        // L'encadrement de pierre. Les y sont des y d'ecran : `haut` est plus petit que
        // `bas`, ce qui est l'inverse du monde et la source de toutes les erreurs de
        // cadre — d'ou les deux noms explicites plutot qu'un rectangle.
        val gauche = cx - demiL - echelle * 0.07f
        val droite = cx + demiL + echelle * 0.07f
        val haut = cy - hauteur
        val bas = cy + demiH
        c.drawRect(gauche, haut, droite, bas, pierre)
        c.drawRect(gauche + echelle * 0.05f, haut, droite - echelle * 0.05f, bas - echelle * 0.05f, roche)

        // La lueur au fond, d'autant plus forte que la porte est levee
        if (ouverture > 0f) {
            orPale.alpha = (110 * ouverture).toInt().coerceIn(0, 255)
            c.drawCircle(cx, cy - hauteur * 0.3f, hauteur * (0.5f + ouverture * 0.9f), orPale)
        }

        // La porte, qui glisse vers le haut
        val course = hauteur * 0.92f * ouverture
        val hautPorte = cy - hauteur + echelle * 0.05f - course
        val basPorte = cy + demiH - echelle * 0.02f - course
        c.drawRect(cx - demiL + echelle * 0.05f, hautPorte, cx + demiL - echelle * 0.05f, basPorte, pierreClaire)
        // Le crane de la clef de voute : c'est lui qui dit « ne touchez pas a ca ».
        val crane = cy - hauteur * 0.62f - course
        c.drawCircle(cx, crane, demiL * 0.34f, ivoire)
        c.drawCircle(cx - demiL * 0.13f, crane - demiL * 0.03f, demiL * 0.08f, pointDomino)
        c.drawCircle(cx + demiL * 0.13f, crane - demiL * 0.03f, demiL * 0.08f, pointDomino)
        c.drawRect(
            cx - demiL * 0.09f, crane + demiL * 0.14f,
            cx + demiL * 0.09f, crane + demiL * 0.3f, ivoireOmbre
        )

        // La zone sensible elle-meme, discrete mais visible : le joueur doit savoir ou
        // viser, et une porte fermee ne le dit pas.
        if (ouverture < 1f) {
            orPale.alpha = (60 + 40 * sin(horloge * 3f)).toInt().coerceIn(20, 120)
            c.drawCircle(cx, cy, demiH * 1.3f, orPale)
        } else {
            for (k in 0 until 5) {
                val a = horloge * 1.6f + k * 1.257f
                val r = hauteur * 0.35f * (0.5f + 0.5f * sin(horloge * 2f + k))
                c.drawCircle(cx + cos(a) * r, cy - hauteur * 0.35f + sin(a) * r * 0.6f, echelle * 0.05f, or)
            }
        }
    }

    private fun peindrePiece(c: Canvas, piece: Piece) {
        // La corde d'abord : elle passe derriere le godet et le contrepoids.
        piece.poulie?.let { poulie ->
            poulie.anchorAWorld(ancre)
            c.drawLine(ex(poulie.groundAX), ey(poulie.groundAY), ex(ancre[0]), ey(ancre[1]), corde)
            poulie.anchorBWorld(ancre)
            c.drawLine(ex(poulie.groundBX), ey(poulie.groundBY), ex(ancre[0]), ey(ancre[1]), corde)
            // Un seul rea dans le cas courant : les deux points de renvoi sont confondus,
            // et dessiner deux roues au meme endroit ne ferait qu'epaissir le trait.
            roue(c, poulie.groundAX, poulie.groundAY)
            if (poulie.groundAX != poulie.groundBX || poulie.groundAY != poulie.groundBY) {
                roue(c, poulie.groundBX, poulie.groundBY)
            }
        }
        for (corps in piece.corps) peindreCorps(c, corps)
    }

    private fun roue(c: Canvas, x: Float, y: Float) {
        c.drawCircle(ex(x), ey(y), echelle * 0.11f, fer)
        c.drawCircle(ex(x), ey(y), echelle * 0.06f, ferClair)
    }

    private fun peindreCorps(c: Canvas, corps: PhysBody) {
        when (corps.tag as? Element) {
            Element.PLANCHE -> for (i in corps.parts.indices) planche(c, corps, i)
            Element.PLOT -> for (i in corps.parts.indices) plot(c, corps, i)
            Element.BLOC -> for (i in corps.parts.indices) bloc(c, corps, i)
            Element.DOMINO -> for (i in corps.parts.indices) domino(c, corps, i)
            Element.BATI -> for (i in corps.parts.indices) boite(c, corps, i, fer, ferClair)
            Element.VENTILATEUR -> ventilateur(c, corps)
            Element.PEAU -> for (i in corps.parts.indices) tambour(c, corps, i)
            Element.GODET -> for (i in corps.parts.indices) boite(c, corps, i, boisSombre, bois)
            Element.CONTREPOIDS -> for (i in corps.parts.indices) boite(c, corps, i, fer, ferClair)
            Element.BILLE -> bille(c, corps)
            Element.TAPIS -> tapis(c, corps)
            Element.TORCHE -> torche(c, corps)
            null -> for (i in corps.parts.indices) boite(c, corps, i, pierre, pierreClaire)
        }
    }

    /**
     * Pose le repere sur une forme et appelle [dessin] dans son repere a elle.
     *
     * Le repere local a le x vers la droite et **le y vers le bas**, comme l'ecran : le
     * monde a le y vers le haut, donc l'angle change de signe une fois ici plutot que
     * dans chaque piece. C'est la conversion que tout le monde oublie une fois sur deux,
     * et la faire a un seul endroit la rend impossible a oublier.
     */
    private inline fun surForme(c: Canvas, corps: PhysBody, part: Int, dessin: (Float, Float) -> Unit) {
        corps.partWorld(part, centre)
        val p = corps.parts[part]
        c.save()
        c.translate(ex(centre[0]), ey(centre[1]))
        c.rotate(-Math.toDegrees(centre[2].toDouble()).toFloat())
        dessin(p.halfW * echelle, p.halfH * echelle)
        c.restore()
    }

    private fun planche(c: Canvas, corps: PhysBody, part: Int) {
        surForme(c, corps, part) { hw, hh ->
            c.drawRect(-hw, -hh, hw, hh, bois)
            c.drawRect(-hw, hh * 0.25f, hw, hh, boisSombre)
            // Le fil du bois : quatre traits, pas une texture.
            var n = 0
            var k = 0
            while (k < 4 && n + 4 <= segments.size) {
                val x0 = -hw + hw * 0.5f * k
                segments[n] = x0 + hw * 0.08f
                segments[n + 1] = -hh * 0.35f
                segments[n + 2] = x0 + hw * 0.42f
                segments[n + 3] = hh * 0.1f
                n += 4
                k++
            }
            if (n > 0) c.drawLines(segments, 0, n, grain)
            // Les boulons d'about
            c.drawCircle(-hw + hh * 0.9f, 0f, hh * 0.32f, ferClair)
            c.drawCircle(hw - hh * 0.9f, 0f, hh * 0.32f, ferClair)
            c.drawRect(-hw, -hh, hw, hh, contour)
        }
    }

    private fun plot(c: Canvas, corps: PhysBody, part: Int) {
        corps.partWorld(part, centre)
        val r = corps.parts[part].radius * echelle
        val x = ex(centre[0])
        val y = ey(centre[1])
        c.drawCircle(x, y, r, caoutchouc)
        c.drawCircle(x, y, r * 0.62f, caoutchoucClair)
        c.drawCircle(x, y, r * 0.3f, caoutchouc)
        c.drawCircle(x, y, r, contour)
    }

    private fun bloc(c: Canvas, corps: PhysBody, part: Int) {
        surForme(c, corps, part) { hw, hh ->
            c.drawRect(-hw, -hh, hw, hh, pierre)
            c.drawRect(-hw, -hh, hw, -hh + hh * 0.3f, pierreClaire)
            c.drawLine(-hw, 0f, hw, 0f, grain)
            c.drawLine(0f, 0f, 0f, hh, grain)
            c.drawRect(-hw, -hh, hw, hh, contour)
        }
    }

    private fun domino(c: Canvas, corps: PhysBody, part: Int) {
        surForme(c, corps, part) { hw, hh ->
            c.drawRect(-hw, -hh, hw, hh, ivoire)
            c.drawRect(hw * 0.35f, -hh, hw, hh, ivoireOmbre)
            c.drawLine(-hw, 0f, hw, 0f, grain)
            val r = hw * 0.34f
            c.drawCircle(0f, -hh * 0.55f, r, pointDomino)
            c.drawCircle(0f, hh * 0.55f, r, pointDomino)
            c.drawRect(-hw, -hh, hw, hh, contour)
        }
    }

    private fun boite(c: Canvas, corps: PhysBody, part: Int, plein: Paint, clair: Paint) {
        surForme(c, corps, part) { hw, hh ->
            c.drawRect(-hw, -hh, hw, hh, plein)
            c.drawRect(-hw, -hh, hw, -hh + minOf(hh * 0.45f, echelle * 0.03f), clair)
            c.drawRect(-hw, -hh, hw, hh, contour)
        }
    }

    /**
     * Une torche posee : l'applique, la flamme, et la lueur autour.
     *
     * **Une respiration, pas un clignotement.** Les torches du decor vacillaient a deux
     * hertz et papillotaient au point d'attirer l'oeil loin de la machine, qui est le seul
     * endroit ou il doit etre. Un hertz et un dixieme d'amplitude suffisent a ce que ca ne
     * paraisse pas fige.
     */
    private fun torche(c: Canvas, corps: PhysBody) {
        corps.partWorld(0, centre)
        val x = ex(centre[0])
        val bas = ey(centre[1] - corps.parts[0].halfH)
        val haut = ey(centre[1] + corps.parts[0].halfH)
        val vif = 0.92f + 0.08f * sin(horloge * 1.1f + corps.id)

        c.save()
        c.translate(x, haut)
        c.drawCircle(0f, 0f, echelle * 1.6f, halo)
        c.restore()

        c.drawRect(x - echelle * 0.035f, haut, x + echelle * 0.035f, bas, fer)
        c.drawCircle(x, haut, echelle * 0.085f * vif, torcheP)
        c.drawCircle(x, haut - echelle * 0.04f, echelle * 0.045f * vif, or)
    }

    /** Une bille posee par le joueur : la meme que celle du tableau, sans la traine. */
    private fun bille(c: Canvas, corps: PhysBody) {
        corps.partWorld(0, centre)
        val r = corps.parts[0].radius * echelle
        val x = ex(centre[0])
        val y = ey(centre[1])
        c.drawCircle(x, y, r, billeP)
        c.drawCircle(x - r * 0.3f, y - r * 0.32f, r * 0.32f, billeReflet)
        c.drawCircle(x, y, r, contour)
    }

    /**
     * Le tapis : une bande, deux tambours aux bouts, et des chevrons qui defilent.
     *
     * Le sens se lit dans `surfaceSpeed`, que le moteur porte deja sur le corps : rien a
     * ranger a cote, rien qui puisse diverger du comportement reel. Un tapis dessine dans
     * le mauvais sens serait le pire des bugs de cette piece, puisque le sens est la seule
     * chose qu'elle ait a dire.
     */
    private fun tapis(c: Canvas, corps: PhysBody) {
        val sens = if (corps.surfaceSpeed < 0f) -1f else 1f
        surForme(c, corps, 0) { hw, hh ->
            c.drawRect(-hw, -hh, hw, hh, fer)
            c.drawRect(-hw, -hh, hw, -hh + hh * 0.5f, ferClair)
            // Les chevrons defilent dans le sens de la bande. Le repere local a le y vers
            // le bas, mais le x vers la droite comme le monde : le sens s'y lit tel quel.
            val pas = hh * 3f
            val defile = ((horloge * 1.6f * sens) % 1f) * pas
            var n = 0
            var x = -hw + defile - pas
            while (x < hw && n + 8 <= segments.size) {
                val a = x.coerceIn(-hw, hw)
                val b = (x + hh * 1.2f).coerceIn(-hw, hw)
                if (b > a) {
                    segments[n] = a
                    segments[n + 1] = hh * 0.55f
                    segments[n + 2] = b
                    segments[n + 3] = -hh * 0.55f
                    n += 4
                }
                x += pas
            }
            if (n > 0) c.drawLines(segments, 0, n, grain)
            c.drawRect(-hw, -hh, hw, hh, contour)
        }
        // Les tambours d'about, dessines dans le repere du monde pour rester ronds.
        corps.partWorld(0, centre)
        val demi = corps.parts[0].halfW * echelle
        val rayon = corps.parts[0].halfH * echelle
        c.drawCircle(ex(centre[0]) - demi, ey(centre[1]), rayon, ferClair)
        c.drawCircle(ex(centre[0]) + demi, ey(centre[1]), rayon, ferClair)
    }

    private fun tambour(c: Canvas, corps: PhysBody, part: Int) {
        surForme(c, corps, part) { hw, hh ->
            c.drawRect(-hw, -hh, hw, hh, peau)
            // La peau vibre apres un coup : le moteur compte deja l'energie d'impact
            // recue par le corps, il n'y a qu'a la lire.
            val vibre = corps.impactAccum.coerceAtMost(4f)
            if (vibre > 0.02f) {
                val a = sin(horloge * 42f) * vibre * echelle * 0.02f
                c.drawLine(-hw * 0.8f, a, hw * 0.8f, -a, cercle)
            }
            c.drawRect(-hw, -hh, -hw + hh * 1.6f, hh, cercle)
            c.drawRect(hw - hh * 1.6f, -hh, hw, hh, cercle)
            c.drawRect(-hw, -hh, hw, hh, contour)
        }
    }

    /**
     * Le ventilateur : un carter, une grille, et trois pales qui tournent.
     *
     * Aucune pale n'existe dans le moteur — voir [Pieces.ventilateur]. Elles tournent ici
     * a une vitesse fixe, et personne ne peut faire la difference : ce que le joueur doit
     * lire, c'est **ou souffle le jet**, et une helice qui tourne le dit mieux qu'une
     * fleche.
     */
    private fun ventilateur(c: Canvas, corps: PhysBody) {
        surForme(c, corps, 0) { hw, hh ->
            c.drawRect(-hw, -hh, hw, hh, fer)
            c.drawRect(-hw, -hh, hw, -hh + hh * 0.22f, ferClair)
            // Le carter est dessine dans le repere de la piece, ou le jet part vers +x.
            // La grille, cote sortie.
            var n = 0
            var k = 0
            while (k < 4 && n + 4 <= segments.size) {
                val y = -hh + hh * 0.5f * (k + 0.5f)
                segments[n] = hw * 0.55f
                segments[n + 1] = y
                segments[n + 2] = hw
                segments[n + 3] = y
                n += 4
                k++
            }
            if (n > 0) c.drawLines(segments, 0, n, grain)
            c.rotate(-horloge * 900f)
            for (pale in 0 until 3) {
                c.save()
                c.rotate(pale * 120f)
                c.drawRect(-hw * 0.1f, -hh * 0.8f, hw * 0.1f, 0f, ferClair)
                c.restore()
            }
            c.drawCircle(0f, 0f, hh * 0.18f, or)
        }
        surForme(c, corps, 0) { hw, hh -> c.drawRect(-hw, -hh, hw, hh, contour) }
    }

    /** Le jet : des traits qui filent dans le sens du souffle. */
    private fun peindreVent(c: Canvas, s: Souffle) {
        val dx = cos(s.direction)
        val dy = sin(s.direction)
        val px = -dy
        val py = dx
        var n = 0
        for (voie in -1..1) {
            val ox = s.x + px * voie * s.demiLargeur * 0.62f
            val oy = s.y + py * voie * s.demiLargeur * 0.62f
            for (k in 0 until 3) {
                if (n + 4 > segments.size) break
                val phase = ((horloge * 1.5f + k * 0.33f + voie * 0.17f) % 1f) * s.portee
                val fin = (phase + s.portee * 0.16f).coerceAtMost(s.portee)
                segments[n] = ex(ox + dx * phase)
                segments[n + 1] = ey(oy + dy * phase)
                segments[n + 2] = ex(ox + dx * fin)
                segments[n + 3] = ey(oy + dy * fin)
                n += 4
            }
        }
        if (n > 0) c.drawLines(segments, 0, n, vent)
    }

    private fun peindreTrainee(c: Canvas) {
        if (traineeNombre < 4) return
        // Trois tronçons du plus vieux au plus recent, de plus en plus opaques.
        val parTroncon = traineeNombre / 3
        var index = (traineeTete - traineeNombre + TRAINEE) % TRAINEE
        for (troncon in 0 until 3) {
            var n = 0
            val combien = if (troncon == 2) traineeNombre - 2 * parTroncon else parTroncon
            for (k in 0 until combien - 1) {
                if (n + 4 > traineeSegs.size) break
                val j = (index + k) % TRAINEE
                val j2 = (index + k + 1) % TRAINEE
                traineeSegs[n] = ex(traineeX[j])
                traineeSegs[n + 1] = ey(traineeY[j])
                traineeSegs[n + 2] = ex(traineeX[j2])
                traineeSegs[n + 3] = ey(traineeY[j2])
                n += 4
            }
            traineeP.alpha = 30 + troncon * 45
            if (n > 0) c.drawLines(traineeSegs, 0, n, traineeP)
            index = (index + combien) % TRAINEE
        }
    }

    private fun peindreBille(c: Canvas, b: PhysBody) {
        b.partWorld(0, centre)
        val r = b.parts[0].radius * echelle
        val x = ex(centre[0])
        val y = ey(centre[1])
        // L'ombre au sol : elle donne la hauteur, ce qu'aucune autre indication ne fait
        // sur une image fixe.
        val h = (centre[1] / 2.5f).coerceIn(0f, 1f)
        billeOmbre.alpha = (70 * (1f - h)).toInt().coerceIn(0, 90)
        c.drawOval(x - r * (1f + h), ey(0f) - r * 0.2f, x + r * (1f + h), ey(0f) + r * 0.3f, billeOmbre)
        c.drawCircle(x, y, r, billeP)
        c.drawCircle(x - r * 0.3f, y - r * 0.32f, r * 0.32f, billeReflet)
        c.drawCircle(x, y, r, contour)
    }

    private fun silhouette(c: Canvas, corps: PhysBody, peinture: Paint) {
        for (i in corps.parts.indices) {
            if (corps.parts[i].shape == Shape.CIRCLE) {
                corps.partWorld(i, centre)
                c.drawCircle(ex(centre[0]), ey(centre[1]), corps.parts[i].radius * echelle, peinture)
            } else {
                corps.partCorners(i, coins)
                dessinerCoins(c, coins, peinture)
            }
        }
    }

    /**
     * Un seul cadre autour de toute la piece, et pas un par corps.
     *
     * Une poulie a quatre corps : le mat, le godet, le contrepoids et ses tablettes. Quatre
     * rectangles bleus emboites ne designaient plus rien — on ne voyait plus la piece sous
     * le marquage.
     */
    private fun encadrer(c: Canvas, piece: Piece) {
        var loX = Float.MAX_VALUE
        var hiX = -Float.MAX_VALUE
        var loY = Float.MAX_VALUE
        var hiY = -Float.MAX_VALUE
        for (corps in piece.corps) {
            corps.updateAabb()
            if (corps.aabbMinX < loX) loX = corps.aabbMinX
            if (corps.aabbMaxX > hiX) hiX = corps.aabbMaxX
            if (corps.aabbMinY < loY) loY = corps.aabbMinY
            if (corps.aabbMaxY > hiY) hiY = corps.aabbMaxY
        }
        c.drawRect(ex(loX) - 5f, ey(hiY) - 5f, ex(hiX) + 5f, ey(loY) + 5f, marqueur)
    }

    /**
     * Les poignees de la piece designee.
     *
     * Deux cercles concentriques et rien de plus : elles doivent se voir sur du bois comme
     * sur de la pierre, et ne rien cacher de la piece qu'on regle. Leur taille est en
     * **pixels** et non en metres — une poignee est faite pour un doigt, dont la largeur ne
     * depend pas du zoom.
     */
    private fun peindrePoignees(c: Canvas, pose: Pose) {
        for (poignee in Poignees.pour(pose)) {
            val x = ex(poignee.x)
            val y = ey(poignee.y)
            c.drawCircle(x, y, 17f, priseFond)
            c.drawCircle(x, y, 17f, marqueur)
            c.drawCircle(x, y, 5f, priseCoeur)
        }
    }

    private fun dessinerCoins(c: Canvas, pts: FloatArray, peinture: Paint) {
        trace.rewind()
        trace.moveTo(ex(pts[0]), ey(pts[1]))
        trace.lineTo(ex(pts[2]), ey(pts[3]))
        trace.lineTo(ex(pts[4]), ey(pts[5]))
        trace.lineTo(ex(pts[6]), ey(pts[7]))
        trace.close()
        c.drawPath(trace, peinture)
    }

    // ── Doigt ────────────────────────────────────────────────────────────────
    //
    // ## Le geste, et ce qu'il veut dire
    //
    // Quatre intentions, une seule regle de depart : **ce qu'il y a sous le doigt decide.**
    //
    //  - une poignee de la piece designee -> on la regle (longueur, angle, taille) ;
    //  - une piece posee -> on la deplace, et un simple appui la **designe** ;
    //  - une piece choisie dans la reserve -> on la pose ;
    //  - rien du tout -> on fait glisser la camera.
    //
    // Le point qui a coute le plus : **un appui sur une piece la designe, il ne la supprime
    // plus.** Tapoter supprimait, ce qui rendait litteralement inatteignable tout reglage
    // d'une piece posee — la designer pour ouvrir sa barre d'outils la faisait disparaitre.
    // La suppression a maintenant son propre bouton, ce qui est aussi plus sur.

    /** Ce que le doigt est en train de faire. */
    private enum class Geste { RIEN, POSER, DEPLACER, POIGNEE, CAMERA }

    private var geste = Geste.RIEN
    private var doigtIndex = -1
    private var priseActive: Prise? = null
    private var doigtDepartX = 0f
    private var doigtDepartY = 0f
    private var dernierX = 0f
    private var dernierY = 0f
    private var aBouge = false

    // La camera a deux doigts. `pince` reste vrai jusqu'a ce que tous les doigts soient
    // partis : sans ce verrou, lever un doigt sur deux reprendrait le geste precedent la ou
    // le pincement l'avait laisse, et poserait une piece sans que personne l'ait demande.
    private var pince = false
    private var pinceEcart = 0f
    private var pinceX = 0f
    private var pinceY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount >= 2 || pince) return piloterPince(event)

        val mx = (event.x - origineX) / echelle
        val my = (basY - event.y) / echelle

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                doigtDepartX = event.x
                doigtDepartY = event.y
                dernierX = event.x
                dernierY = event.y
                aBouge = false
                synchronized(verrou) { commencer(mx, my) }
                if (geste == Geste.DEPLACER || geste == Geste.POIGNEE) {
                    post { listener?.surChangement() }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (hypot(event.x - doigtDepartX, event.y - doigtDepartY) > SEUIL_GLISSE) {
                    aBouge = true
                }
                synchronized(verrou) { continuer(event, mx, my) }
                dernierX = event.x
                dernierY = event.y
            }

            MotionEvent.ACTION_UP -> {
                synchronized(verrou) { finir(mx, my) }
                post { listener?.surChangement() }
            }

            MotionEvent.ACTION_CANCEL -> synchronized(verrou) {
                viser(null)
                geste = Geste.RIEN
                doigtIndex = -1
                priseActive = null
            }
        }
        return true
    }

    private fun commencer(mx: Float, my: Float) {
        val p = partie
        if (p == null || p.lancee) {
            geste = Geste.CAMERA
            return
        }
        // Les poignees d'abord : elles sont petites et se superposent a la piece, donc les
        // tester apres reviendrait a ne jamais les atteindre.
        val prise = priseSous(p, mx, my)
        if (prise != null) {
            priseActive = prise
            geste = Geste.POIGNEE
            return
        }
        val type = typeChoisi
        if (type != null) {
            geste = Geste.POSER
            majApercu(p, mx, my)
            return
        }
        val index = pieceSous(p, mx, my)
        if (index >= 0) {
            doigtIndex = index
            selection = index
            geste = Geste.DEPLACER
            return
        }
        geste = Geste.CAMERA
    }

    private fun continuer(event: MotionEvent, mx: Float, my: Float) {
        val p = partie ?: return
        when (geste) {
            Geste.POSER -> majApercu(p, mx, my)

            Geste.POIGNEE -> {
                val prise = priseActive ?: return
                val pose = p.placees().getOrNull(selection) ?: return
                val vise = Poignees.tirer(pose, prise, accrocher(mx), accrocher(my))
                p.deplacer(selection, vise)
            }

            Geste.DEPLACER -> {
                if (!aBouge) return
                val pose = p.placees().getOrNull(doigtIndex) ?: return
                val vise = deplacee(pose, mx, my)
                viser(vise, p.verifier(vise, sauf = doigtIndex))
            }

            // Un doigt sur le vide fait glisser la camera. C'est le geste le plus courant
            // sur un terrain de seize metres, et lui demander deux doigts serait une taxe.
            Geste.CAMERA -> glisserCamera(event.x - dernierX, event.y - dernierY)

            Geste.RIEN -> Unit
        }
    }

    private fun finir(mx: Float, my: Float) {
        val p = partie
        when (geste) {
            // La piece choisie le reste : dans un bac a sable on en pose dix d'affilee, et
            // devoir la rechoisir a chaque fois serait insupportable.
            Geste.POSER -> typeChoisi?.let { p?.poser(poseA(it, mx, my)) }

            Geste.DEPLACER -> {
                val pose = p?.placees()?.getOrNull(doigtIndex)
                // Sans mouvement, l'appui a seulement designe la piece — c'est deja fait.
                if (pose != null && aBouge) p.deplacer(doigtIndex, deplacee(pose, mx, my))
            }

            // Un appui sur le vide deselectionne : c'est la facon la plus naturelle de
            // ranger les poignees quand on a fini de regler.
            Geste.CAMERA -> if (!aBouge) selection = -1

            else -> Unit
        }
        viser(null)
        geste = Geste.RIEN
        doigtIndex = -1
        priseActive = null
    }

    /** La meme piece, ailleurs : deplacer ne touche ni aux cotes ni a l'angle. */
    private fun deplacee(pose: Pose, mx: Float, my: Float): Pose {
        val gy = accrocher(my)
        return pose.copy(
            x = accrocher(mx),
            y = if (pose.type.ancrage == Ancrage.SCELLE) gy else gy.coerceAtLeast(0f)
        )
    }

    /** Deux doigts : on deplace et on grossit, on ne pose rien. */
    private fun piloterPince(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                pince = true
                synchronized(verrou) {
                    viser(null)
                    geste = Geste.RIEN
                    doigtIndex = -1
                    priseActive = null
                }
                mesurerPince(event)
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount < 2) return true
                val cx = (event.getX(0) + event.getX(1)) / 2f
                val cy = (event.getY(0) + event.getY(1)) / 2f
                val ecart = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                if (pinceEcart > 1f && ecart > 1f) {
                    synchronized(verrou) {
                        bougerCamera(ecart / pinceEcart, cx - pinceX, cy - pinceY, pinceX, pinceY)
                    }
                }
                pinceEcart = ecart
                pinceX = cx
                pinceY = cy
            }

            MotionEvent.ACTION_POINTER_UP -> mesurerPince(event)

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pince = false
        }
        return true
    }

    private fun mesurerPince(event: MotionEvent) {
        if (event.pointerCount < 2) {
            pinceEcart = 0f
            return
        }
        pinceEcart = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
        pinceX = (event.getX(0) + event.getX(1)) / 2f
        pinceY = (event.getY(0) + event.getY(1)) / 2f
    }

    private fun majApercu(p: Partie, mx: Float, my: Float) {
        val type = typeChoisi ?: return
        val pose = poseA(type, mx, my)
        viser(pose, p.verifier(pose))
    }

    /**
     * Ou tombe une piece quand le doigt est la.
     *
     * Les pieces scellees se posent par leur centre, puisqu'elles flottent ou l'on veut ;
     * les libres et les mixtes par leur base, puisqu'on les pose sur quelque chose. Un
     * domino vise par son centre s'enfonce a moitie dans le sol.
     */
    private fun poseA(type: TypePiece, x: Float, y: Float): Pose {
        val gx = accrocher(x)
        val gy = accrocher(y)
        val hauteur = if (type.ancrage == Ancrage.SCELLE) gy else gy.coerceAtLeast(0f)
        return Pose(type, gx, hauteur, reglage = reglage(type), miroir = miroir(type))
    }

    private fun accrocher(v: Float): Float = Math.round(v / GRILLE) * GRILLE

    /** La poignee de la piece designee qui se trouve sous le doigt, ou `null`. */
    private fun priseSous(p: Partie, x: Float, y: Float): Prise? {
        val pose = p.placees().getOrNull(selection) ?: return null
        val portee = RAYON_PRISE / echelle
        var meilleure: Prise? = null
        var plusProche = portee
        for (poignee in Poignees.pour(pose)) {
            val d = hypot(poignee.x - x, poignee.y - y)
            if (d <= plusProche) {
                plusProche = d
                meilleure = poignee.prise
            }
        }
        return meilleure
    }

    private fun pieceSous(p: Partie, x: Float, y: Float): Int {
        val placees = p.placees()
        for (i in placees.indices.reversed()) {
            val piece = p.plateau.pieces.getOrNull(i) ?: continue
            for (corps in piece.corps) {
                corps.updateAabb()
                if (x >= corps.aabbMinX - TOUCHE && x <= corps.aabbMaxX + TOUCHE &&
                    y >= corps.aabbMinY - TOUCHE && y <= corps.aabbMaxY + TOUCHE
                ) return i
            }
        }
        return -1
    }

    private fun reglageParDefaut(type: TypePiece): Float = when (type) {
        TypePiece.RAMPE -> 20f
        else -> 0f
    }

    private companion object {
        /** Pas de simulation, fixe : c'est ce qui rend une partie reproductible. */
        const val PAS = 1f / 120f

        /** Nombre de positions gardees pour la traine de la bille. */
        const val TRAINEE = 96

        /** Nombre de cailloux et de brins du decor. */
        const val DECOR = 26

        /** Hauteur de sol visible sous l'altitude zero, en metres. */
        const val SOL_VISIBLE = 0.45f

        /** Pas de la grille d'accrochage, en metres. */
        const val GRILLE = 0.05f

        /** Tolerance de designation au doigt, en metres. */
        const val TOUCHE = 0.06f

        /** Deplacement au-dela duquel un appui devient un glissement, en pixels. */
        const val SEUIL_GLISSE = 18f

        /** Rayon de saisie d'une poignee, en pixels. Genereux : un doigt est large. */
        const val RAYON_PRISE = 46f
    }
}
