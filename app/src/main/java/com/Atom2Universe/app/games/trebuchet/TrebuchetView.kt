package com.Atom2Universe.app.games.trebuchet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.graphics.toColorInt
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.Shape
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Affichage et pilotage du trébuchet.
 *
 * La simulation tourne sur son propre thread à pas fixe ; les événements tactiles
 * arrivent depuis le thread UI, d'où les blocs `synchronized(game)`.
 *
 * La caméra suit le boulet pendant le vol puis recule pour montrer tout l'arc :
 * une machine bien réglée envoie à plus de quatre cents mètres, et c'est justement
 * cette courbe-là qu'on veut voir. Hors du vol, le joueur reprend la main : il
 * fait défiler la distance au doigt et pince pour zoomer, entre deux butées —
 * juste derrière la machine et cinq cents mètres devant. Le sol, lui, reste calé
 * en bas de l'image : on ne monte pas la vue, on dézoome.
 *
 * Un **double-appui** bascule entre deux plans choisis : le terrain entier, machine et
 * cible d'un seul coup d'œil, puis la machine seule. Il vaut à tout moment, y compris
 * pendant le tir — voir [CamView].
 */
class TrebuchetView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : SurfaceView(ctx, attrs), SurfaceHolder.Callback, Runnable {

    interface Listener {
        /** Le tir est terminé : l'activité affiche la portée. */
        fun onShotFinished()

        /**
         * Un réglage a bougé au doigt, ou le joueur a pris une autre pièce en main :
         * l'activité rafraîchit son bandeau. Appelé depuis le thread UI.
         */
        fun onMachineChanged()
    }

    /**
     * Les pièces qu'on peut prendre en main. Une pièce sélectionnée s'éclaire et
     * sort ses poignées ; les autres se taisent. C'est ce qui permet à deux réglages
     * de partager un même point de la machine sans jamais se disputer le doigt —
     * l'axe, par exemple, règle la hauteur du pied ou la position du levier selon
     * la pièce qu'on tient.
     */
    enum class Part { NONE, BEAM, POST, WEIGHT, PIN, SLING }

    /** Ce qu'un doigt posé sur une pièce sélectionnée est en train de régler. */
    private enum class Grip {
        NONE, BEAM_LENGTH, LEVER, POST_HEIGHT, CW_MASS, CW_HANG, PIN_ANGLE, SLING_LENGTH
    }

    val game = TrebuchetGame()
    var listener: Listener? = null

    /** La pièce tenue en main, ou [Part.NONE]. L'activité la lit pour son bandeau. */
    var selected = Part.NONE
        private set

    private var thread: Thread? = null
    @Volatile private var running = false

    /** Faux dès qu'une surface a refusé le canevas matériel : voir [lockFrame]. */
    private var hardwareCanvas = true
    private var lastNanos = 0L
    private var accumulator = 0f
    private var lastPhase = TrebuchetGame.Phase.BUILD

    private val dp = resources.displayMetrics.density

    // ── Caméra ───────────────────────────────────────────────────────────────

    private var camX = 0f
    private var camY = 2f
    private var camScale = 60f
    private var camReady = false

    /**
     * Jusqu'où le sol descend dans la portion regardée, en mètres, et jamais au-dessus
     * de zéro. C'est ce que le cadrage pose au bas de l'image. Voir [groundCamY].
     */
    private var camFloor = 0f

    /**
     * Le joueur tient le cadrage. Tant qu'il le tient, la caméra ne bouge plus
     * toute seule ; un changement de phase le lui reprend, parce qu'au départ d'un
     * tir c'est le boulet qui commande, et qu'à l'arrivée on veut voir tout l'arc.
     */
    private var manualCam = false
    private var camPhase = TrebuchetGame.Phase.BUILD

    /**
     * Les cadrages que le joueur peut demander lui-même, au double-appui.
     *
     * [AUTO] est celui de la caméra : elle suit le boulet, puis montre l'arc, puis
     * revient sur la machine. Les deux autres sont des réponses à une question que le
     * joueur se pose souvent et à laquelle rien ne répondait — *où en suis-je par rapport
     * à la cible ?* et *à quoi ressemble ma machine ?* — et le double-appui les fait
     * alterner. Ils valent **même pendant le vol**, où le cadrage automatique colle au
     * boulet : c'est le seul moyen de regarder ailleurs que lui.
     */
    private enum class CamView { AUTO, FULL, MACHINE }

    private var camView = CamView.AUTO

    /** Le cadrage ne s'attrape pas en vol : pendant le tir, la caméra suit. */
    private val cameraFree: Boolean
        get() = game.phase != TrebuchetGame.Phase.FLIGHT

    private companion object {
        const val FIXED_DT = 1f / 120f

        /** Marges autour de la machine, en mètres : la vue suit sa taille. */
        const val BUILD_VIEW_MARGIN = 8f
        const val FLIGHT_VIEW_MARGIN = 18f

        /** Fenêtre minimale en vol : un boulet rapide doit rester dedans. */
        const val FLIGHT_MIN_WIDTH = 90f

        /**
         * Ciel gardé au-dessus du boulet en vol, en mètres.
         *
         * Un boulet collé au bord haut de l'écran est un boulet qu'on croit sorti de
         * l'image. Quinze mètres suffisent à ce qu'il ait toujours l'air d'avoir de la
         * place — et comme c'est l'échelle qui s'ajuste, en donner davantage
         * reviendrait à regarder le tir de plus loin pour rien.
         */
        const val FLIGHT_TOP_MARGIN = 15f

        /** De combien le vol peut reculer au-delà du dézoom ordinaire. */
        const val FLIGHT_ZOOM_OUT = 0.55f

        /**
         * À partir de quelle fraction de la chute (depuis le sommet de l'arc) le
         * cadrage resserre un peu, pour finir plus près au moment de l'impact.
         */
        const val FLIGHT_END_ZOOM_START = 0.65f

        /** De combien le cadrage se resserre au tout dernier instant de la chute. */
        const val FLIGHT_END_ZOOM_BOOST = 0.18f

        /** Marge gardée autour de la construction quand la caméra y reste après un tir touché. */
        const val RESULT_HIT_MARGIN = 10f

        /** En dessous, la caméra ne se rapproche plus d'une construction trop petite. */
        const val RESULT_HIT_MIN_WIDTH = 16f
        const val RESULT_HIT_MIN_HEIGHT = 10f

        /** Marge gardée devant le point d'impact (ou la construction) au dézoom de fin de tir. */
        const val RESULT_VIEW_MARGIN = 30f

        /** Au-delà, le cadrage automatique ne recule plus. */
        const val MAX_VIEW_WIDTH = 560f

        /** Butée arrière : juste derrière la pointe du bras bandé, en mètres. */
        const val PAN_BACK_MARGIN = 6f

        /** Butée avant : le terrain de jeu s'arrête là, en mètres. */
        const val PAN_FRONT = 500f

        /** Zoom maximal : au plus près, la fenêtre fait cette largeur en mètres. */
        const val MIN_VIEW_WIDTH = 8f

        /**
         * Bande de terre gardée sous le sol, en dp — la place des bornes de distance.
         * En dp et non en mètres : c'est une marge d'affichage, elle n'a aucune raison
         * de grandir quand on zoome.
         */
        const val GROUND_INSET_DP = 34f

        /** Durée de l'appui qui donne la main sur l'heure, en millisecondes. */
        const val TIME_HOLD_MS = 420L

        /**
         * Part de la largeur de l'écran qu'il faut parcourir pour aller à pleine vitesse.
         *
         * Un tiers, et pas la moitié : le doigt part rarement du centre, et il faut que
         * la pleine vitesse reste atteignable quand on a appuyé un peu de côté.
         */
        const val TIME_THROW = 0.33f


        /** Deux appuis rapprochés rendent le cadrage à la caméra. */
        const val DOUBLE_TAP_MS = 300L

        /** Sous ce déplacement, un doigt posé est un appui, pas un glissement. */
        const val DRAG_SLOP_DP = 9f

        /** Cadence minimale entre deux coups de marteau pendant un glissé de réglage. */
        const val HAMMER_INTERVAL_MS = 110L

        /**
         * Tolérance de saisie, en dp. En dp et non en mètres : sur une grande machine
         * tout est plus petit à l'écran, et une marge en mètres deviendrait ridicule.
         * C'est aussi ce qui rend le zoom utile — zoomé, on règle finement.
         */
        const val PICK_REACH_DP = 30f

        /**
         * … mais jamais plus large que ça, en mètres. Vue de cinq cents mètres, une
         * tolérance de trente dp couvrirait la machine entière et le doigt
         * attraperait n'importe quoi.
         */
        const val PICK_REACH_MAX = 2f

        /**
         * Jusqu'où, en fraction de la hauteur du pivot, le bâti se laisse désigner.
         *
         * Le dernier tiers ne lui appartient pas : c'est là que pend le contrepoids,
         * et c'est lui qu'on veut y attraper.
         */
        const val POST_PICK_TOP = 0.7f

        /** Où, sur le bâti, se dessinent le repère et la poignée du pied. */
        const val POST_GRIP_HEIGHT = 0.4f



        /** Nombre de paliers d'opacité pour le groupage des particules. */
        const val SPARK_ALPHAS = 4

        /** Nombre de classes de taille, la plus fine faisant [SPARK_STEP_DP]. */
        const val SPARK_SIZES = 3

        /** Épaisseur de la plus fine classe de particules, en dp. */
        const val SPARK_STEP_DP = 2f

        /** Au-delà, une particule se dessine en disque et non en point. */
        const val BIG_SPARK_DP = 7f

        /** Longueur du départ prévisualisé, en mètres. Au-delà, il faudra tirer. */
        const val PREVIEW_METRES = 50f

        /** Ouverture du cône : il s'écarte de six pour cent de ce qu'il parcourt. */
        const val PREVIEW_SPREAD = 0.06f

        /** Le fantôme ne repart pas à chaque image : huit fois par seconde suffisent. */
        const val PREVIEW_PERIOD_MS = 120L

        /**
         * Pas de simulation accordés au fantôme par image. Un départ complet en
         * demande deux à quatre cents : les jouer d'un bloc ferait sauter l'affichage
         * à chaque cran de réglage, alors que le joueur a précisément les yeux dessus.
         */
        const val PREVIEW_BUDGET = 60

        /**
         * Au-delà, la machine ne largue pas : inutile d'insister.
         *
         * Le plafond ne se mesure pas en durée de vol mais en **pas de simulation**, et
         * les deux ne vont pas ensemble. Un bloc lourd part à quarante-huit mètres par
         * seconde sous soixante-seize degrés : il monte en cloche, et il lui faut mille
         * pas pour couvrir les cinquante mètres que le cône affiche, là où un boulet
         * tendu en demande quatre cents. À quatre cent quatre-vingts, le cône du bloc
         * lourd s'arrêtait donc en pleine montée et ne montrait rien de son vol.
         */
        const val PREVIEW_MAX_STEPS = 1400

        /** Graine du décor en bac à sable, où il n'y a pas de niveau pour en fournir une. */
        const val SANDBOX_DECOR_SEED = 424242L

        /** Temps minimal entre deux vagues de fuyards, en secondes. */
        const val VILLAGER_PANIC_COOLDOWN = 1.1f
    }

    // ── Palette ──────────────────────────────────────────────────────────────

    /**
     * Le décor : ciel, astres, nuages, oiseaux.
     *
     * **Il n'appartient pas à cette vue**, et c'est tout l'intérêt : l'atelier
     * d'engrenages peint exactement le même, et les machines à venir aussi. Voir
     * [SkyBackdrop] pour la raison d'avoir sorti le décor plutôt que fondu les vues.
     *
     * Le ciel n'appartient pas non plus à la partie : il n'est pas dans [TrebuchetGame],
     * il ne se rejoue pas, il n'entre dans la graine d'aucun niveau. C'est de l'ambiance,
     * et l'ambiance n'a pas à être reproductible — un tir doit donner la même portée à
     * midi et à minuit.
     */
    private val backdrop = SkyBackdrop(context)

    /**
     * Le paysage : sol, constructions, verdure, habitants, feux.
     *
     * Comme le ciel, **il n'appartient pas à cette vue** — voir [LandScene]. La vue lui
     * tend le relief et les cibles, qui sont du jeu et non du décor, plus le cadrage.
     */
    private val land = LandScene(context).apply { firingLine = TrebuchetRules.FIRING_LINE }

    /** Raccourcis de lecture : le reste de la vue lit le ciel à longueur de dessin. */
    private val sky get() = backdrop.sky
    private val skyClock get() = backdrop.clock

    /** La flamme d'un feu : un halo chaud, refait quand sa teinte change. */


    /** L'ambiance sonore : marteau, vent, cris, explosions, feux d'artifice. */
    private val sfx = TrebuchetSfx()

    /** Dernier instant où le marteau a tapé, pour ne pas marteler à chaque frame
     *  d'un glissé continu — voir [applyGrip]. */
    private var lastHammerAt = 0L

    /** Horloge réelle, pour le balancement des plantes — pas l'heure du jeu, qui court
     *  soixante-douze fois plus vite et se remonte à la main. Elle vit dans le décor,
     *  qui s'en sert aussi pour le vol des oiseaux. */
    private val ambientClock get() = backdrop.ambientClock



    /**
     * Le contrôle de l'heure : un appui long **dans le ciel**, puis on glisse.
     *
     * Le ciel et pas n'importe où : le doigt qui traîne sur la machine la règle, celui
     * qui traîne sur le sol déplace la vue, et il n'était pas question d'ajouter un
     * troisième sens au même geste. Au-dessus de l'horizon, en revanche, il n'y a rien
     * d'autre à faire — c'est de la place libre, et c'est ce qui rend ce geste-là
     * possible sans rien casser.
     */
    private var timePressAt = 0L
    private var timePressX = 0f
    private var timeFingerX = 0f
    private var timeCandidate = false
    private var timeMode = false
    private var timeRate = 0f
    private val pFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#6D4C41".toColorInt() }
    private val pBeam = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#A1887F".toColorInt() }
    private val pEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * dp
        color = "#4E342E".toColorInt()
    }
    private val pWeight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#546E7A".toColorInt() }
    private val pWeightEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = "#263238".toColorInt()
    }
    private val pBall = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#B0BEC5".toColorInt() }
    private val pBallEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = "#607D8B".toColorInt()
    }
    /** La chape du contrepoids : une élingue d'acier, épaisse. */
    private val pStrap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
        color = "#78909C".toColorInt()
        strokeCap = Paint.Cap.ROUND
    }
    /** La fronde : une corde tressée, claire pour qu'on la suive dans le fouet. */
    private val pSling = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * dp
        color = "#E9C46A".toColorInt()
        strokeCap = Paint.Cap.ROUND
    }
    /** La fronde molle : elle pend, elle ne tire rien. */
    private val pSlingSlack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.argb(110, 233, 196, 106)
        strokeCap = Paint.Cap.ROUND
    }
    /** Le crochet de largage : rouge, c'est la pièce qui décide du tir. */
    private val pPin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * dp
        color = "#EF5350".toColorInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val pPivot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#37474F".toColorInt() }

    /** La ligne de ruine : l'énoncé du niveau, en pointillé rouge. */
    private val pTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        color = Color.argb(210, 255, 214, 120)
    }
    /** Le cône du départ : une nappe qui s'ouvre et s'éteint. */
    private val pCone = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pConeLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.argb(150, 255, 209, 102)
        pathEffect = DashPathEffect(floatArrayOf(9f * dp, 7f * dp), 0f)
    }
    /**
     * Le pinceau des fantômes. Un seul, retouché entre deux traces.
     *
     * Dix pinceaux tout faits auraient été plus simples à lire, et plus bêtes : la
     * couleur et l'épaisseur d'un fantôme se déduisent de son rang, et un `Paint` se
     * modifie pour trois fois rien. Ce qui coûte, dans un tracé, c'est le tracé.
     */
    private val pGhost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.argb(90, 150, 190, 255)
        pathEffect = DashPathEffect(floatArrayOf(7f * dp, 6f * dp), 0f)
    }
    /** Les stries du vent, et les tourbillons qu'il fait au-delà de la moitié. */
    private val windBox = RectF()

    /** Les couloirs des stries : posés une fois, comme les étoiles du ciel. */

    private val pGaugeBed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 16, 25, 50)
    }
    private val pGaugeArrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = "#9FE7FF".toColorInt()
    }
    private val pGaugeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = "#CFE3FF".toColorInt()
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }

    /** Les points d'un seau : une teinte, une opacité, une épaisseur. */
    private val pSpark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** Les grosses particules, celles qui méritent un vrai disque. */
    private val pBigSpark = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bucketXY = arrayOfNulls<FloatArray>(
        TrebuchetEffects.PALETTE.size * SPARK_ALPHAS * SPARK_SIZES
    )
    private val bucketCount = IntArray(bucketXY.size)

    private val pHandle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.argb(120, 255, 214, 120)
        pathEffect = DashPathEffect(floatArrayOf(5f * dp, 5f * dp), 0f)
    }
    /** La pièce tenue en main : un liseré ambré, la couleur des cordes. */
    private val pSelect = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
        color = "#FFD166".toColorInt()
    }
    /** Les poignées qu'on peut tirer. */
    private val pGrip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#FFD166".toColorInt() }
    private val pGripEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = "#3E2723".toColorInt()
    }
    /** Les pastilles discrètes qui disent « il y a quelque chose à toucher ici ». */
    private val pSpot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 209, 102)
    }
    /** Les valeurs chiffrées, écrites au ras de la poignée qui les règle. */
    private val pValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = "#FFD166".toColorInt()
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val pHint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(160, 220, 235, 255)
    }

    private val tmpPath = Path()

    private val corners = FloatArray(8)
    private val partPose = FloatArray(3)
    private val tip = FloatArray(2)
    private val butt = FloatArray(2)
    private val pin = FloatArray(2)
    private val pickTmp = FloatArray(2)
    private val axisA = FloatArray(2)
    private val axisB = FloatArray(2)

    // ── Saisie ───────────────────────────────────────────────────────────────

    /** Le réglage en cours de glissement, et l'écart doigt-valeur au moment où on
     *  l'a saisi : sans cet écart, attraper une poutre par le milieu la ferait
     *  sauter à la longueur du point touché. */
    private var grip = Grip.NONE
    private var gripOffset = 0f

    /** Le doigt s'est posé dans le vide : s'il n'a pas glissé, il désélectionne. */
    private var tappedVoid = false

    // ── Prévisualisation ──────────────────────────────────────────────────────

    /**
     * La machine fantôme : une seconde machine, identique à celle du joueur, qu'on
     * lâche en coulisse pour voir par où part le boulet. Elle donne le **vrai**
     * départ — pas une formule — et c'est ce qui fait bouger le cône sous le doigt
     * quand on incline le crochet.
     */
    private val ghostMachine = TrebuchetGame()
    private var previewPath = FloatArray(0)
    private var previewArc = FloatArray(0)
    private var previewSig = 0
    private var previewAt = 0L
    private var previewSteps = -1

    /** État du geste à un ou deux doigts : centre, écartement, chemin parcouru. */
    private var gestureCount = 0
    private var focusX = 0f
    private var focusY = 0f
    private var focusSpread = 0f
    private var dragTravel = 0f
    private var gestureLocked = false
    private var lastTapAt = 0L

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ── Cycle de vie ─────────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) = resume()

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        camReady = false
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = pause()

    fun pause() {
        running = false
        thread?.join(1500)
        thread = null
        sfx.stop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Le fil de rendu d'abord : recycler une image qu'il est en train de peindre
        // ferait planter le dessin, et rien ne garantit que la surface soit deja partie.
        pause()
        // La Lune ne survit pas à son ciel : son image et sa carte sont les seuls gros
        // tableaux que le décor garde, et rien d'autre ne les libérerait.
        backdrop.release()
    }

    fun resume() {
        if (running) return
        running = true
        lastNanos = System.nanoTime()
        accumulator = 0f
        sfx.start()
        game.onExplosion = { _, _, _ -> sfx.explosion() }
        game.effects.onRocketLaunch = { _, _ -> sfx.fireworkLaunch() }
        game.effects.onBurst = { _, _ -> sfx.fireworkBurst() }
        thread = Thread(this, "TrebuchetPhysics").also { it.start() }
    }

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            val frameDt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.1f)
            lastNanos = now

            var finished = false
            synchronized(game) {
                accumulator += frameDt
                while (accumulator >= FIXED_DT) {
                    game.step(FIXED_DT)
                    accumulator -= FIXED_DT
                }
                // Les étincelles et les feux d'artifice tournent à l'image réelle et
                // non au pas fixe : ils ne décident de rien, personne ne rejoue un
                // bouquet, et les figer entre deux pas de physique se verrait.
                game.stepEffects(frameDt)
                if (game.phase != lastPhase) {
                    if (game.phase == TrebuchetGame.Phase.RESULT) finished = true
                    lastPhase = game.phase
                }
                updateCamera(frameDt)
                // Ce que l'écran montre du ciel : le feu d'artifice s'y règle.
                // Le ciel se mesure **au-dessus du sol qu'on voit**, pas au-dessus de
                // l'altitude zéro : dans un vallon, les fusées calculées sur une hauteur
                // absolue éclateraient dix mètres trop bas.
                if (height > 0) game.skyTop = worldY(0f) - camFloor
                // L'heure du jeu avance avec l'image réelle, comme les étincelles : le
                // ciel ne décide de rien, personne ne le rejoue, et le figer entre deux
                // pas de physique se verrait à la seconde près sur un crépuscule.
                updateTimeControl(frameDt)
                // Tant que le joueur tient l'heure, elle ne coule plus toute seule :
                // il ne manquerait plus qu'elle avance pendant qu'il la fait reculer.
                // La dérive suit **vx** et non la vitesse : c'est le signe qui manquait
                // aux stries, lesquelles pointaient à gauche par vent debout tout en
                // filant à droite.
                backdrop.advance(frameDt, if (timeMode) 0f else frameDt, game.wind.vx)
                land.updateDecor(
                    frameDt, game.level, game.terrain, game.targets, game.ball.x
                ) { sfx.villagerCry() }
                updatePreview()
            }
            if (finished) post { listener?.onShotFinished() }

            val canvas = lockFrame()
            if (canvas == null) {
                Thread.sleep(16)
                continue
            }
            try {
                synchronized(game) { drawFrame(canvas) }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            Thread.sleep(4)
        }
    }

    /**
     * Attrape l'image à venir — **sur le processeur graphique**.
     *
     * C'était [SurfaceHolder.lockCanvas], donc un canevas logiciel : chaque pixel des
     * quatre millions et demi de l'écran était calculé et écrit par le processeur, à
     * chaque image. Mesuré à la tablette, ce seul fait coûtait quatre-vingt-huit pour
     * cent du temps de calcul du jeu — dont quatre-vingt-deux pour le ciel seul, qui
     * remplit l'écran d'un dégradé puis le recouvre de nuages translucides. Le moteur
     * physique, les éphémérides et les couleurs de l'heure pesaient ensemble deux
     * dixièmes de pour cent. Le jeu ne tenait que quarante images par seconde en gardant
     * un cœur à quatre-vingts pour cent, ce qui se sent sous les doigts.
     *
     * Remplir des surfaces est précisément ce que la puce graphique fait pour rien.
     * [SurfaceHolder.lockHardwareCanvas] existe depuis la version 26 d'Android, qui est
     * justement le plancher du projet, et ne demande rien d'autre que **de tout
     * redessiner à chaque image** — ce que cette vue faisait déjà, puisque le ciel repeint
     * l'écran entier avant tout le reste.
     *
     * Le repli logiciel n'est pas de la prudence de principe : une surface peut refuser le
     * canevas matériel, et le jeu doit alors continuer de tourner comme avant plutôt que
     * de s'arrêter. Un refus vaut pour toujours, on ne le redemande pas soixante fois par
     * seconde ; un canevas nul, en revanche, veut seulement dire que la surface n'est pas
     * prête, et c'est l'appelant qui patiente.
     */
    private fun lockFrame(): Canvas? {
        if (hardwareCanvas) {
            try {
                return holder.lockHardwareCanvas()
            } catch (e: Throwable) {
                hardwareCanvas = false
            }
        }
        return holder.lockCanvas()
    }

    /** Repose la pièce tenue en main : un tir qui part n'a plus de réglage en cours. */
    fun clearSelection() {
        synchronized(game) {
            selected = Part.NONE
            grip = Grip.NONE
        }
    }

    /**
     * Prend une pièce en main sans passer par le doigt : c'est le menu du bas qui appelle.
     *
     * Le doigt ne sait choisir que ce qu'il voit assez gros pour le viser, ce qui obligeait
     * à revenir zoomer sur la machine avant chaque réglage. La liste, elle, désigne la pièce
     * par son nom, et **la caméra ne bouge pas d'un pouce** : c'est tout l'intérêt, on règle
     * en gardant la cible à l'écran. Le contraire de [grabAt], qui recadre parce qu'un doigt
     * posé sur une pièce est un joueur qui la regarde déjà.
     *
     * Le reste est identique à une pièce touchée : prendre une pièce après un tir rebande la
     * machine, et le tir en cours se **termine** au lieu d'être jeté.
     */
    fun selectPart(part: Part) {
        synchronized(game) {
            grip = Grip.NONE
            if (part != Part.NONE && game.phase != TrebuchetGame.Phase.BUILD) {
                game.stopShot()
                game.rebuild()
                // La phase change, mais ni la vue ni la caméra ne doivent y voir un
                // événement : sans ces deux lignes, le cadrage reprendrait la main et
                // sauterait sur la machine, ce que ce menu existe précisément pour éviter.
                lastPhase = game.phase
                camPhase = game.phase
            }
            selected = part
        }
    }

    /** Remet le suivi de phase à zéro après un changement piloté par l'activité. */
    fun syncPhase() {
        synchronized(game) { lastPhase = game.phase }
    }

    // ── Caméra ───────────────────────────────────────────────────────────────

    private fun updateCamera(dt: Float) {
        if (width == 0) return

        // Chaque changement de phase reprend le cadrage : on veut voir partir le
        // tir, puis voir l'arc entier, sans avoir à toucher l'écran.
        if (game.phase != camPhase) {
            camPhase = game.phase
            manualCam = false
            // Un cadrage choisi à la main ne survit pas au changement de phase : la
            // caméra reprend la main pour montrer le départ du tir, puis son arc.
            camView = CamView.AUTO
        }
        if (manualCam && cameraFree) {
            clampCamera()
            return
        }
        if (camView != CamView.AUTO) {
            applyChosenView(dt)
            return
        }

        val cfg = game.config
        val machineWidth = machineViewWidth()
        val machineHeight = machineViewHeight()

        val targetScale: Float
        val tx: Float
        val ty: Float
        val follow: Float

        if (game.phase == TrebuchetGame.Phase.FLIGHT) {
            // **Le sol reste à sa place, et c'est la vue qui recule.**
            //
            // La caméra centrait le boulet en hauteur comme en largeur, et l'horizon
            // disparaissait dès que le tir montait : on voyait alors un caillou au
            // milieu d'un ciel vide, sans rien pour dire s'il montait, s'il descendait,
            // ni où il en était de sa course. Un tir de trébuchet se lit par rapport au
            // sol — c'est même la seule chose qu'on regarde.
            //
            // Le sol est donc calé en bas, comme dans la vue de réglage, et le cadrage
            // ne se règle plus qu'en **échelle** : la fenêtre s'ouvre à mesure que le
            // boulet monte, puis se referme quand il redescend. La largeur suit toute
            // seule, et c'est heureux — un tir haut est aussi un tir long.
            // La hauteur à faire tenir se compte **depuis le plancher du cadrage**, qui
            // n'est pas toujours zéro : au-dessus d'un vallon, le sol posé en bas de
            // l'image est dix mètres plus bas, et une fenêtre réglée sur la seule
            // altitude du boulet le laisserait sortir par le haut.
            val flightHeight =
                max(machineHeight, game.ball.y - camFloor + FLIGHT_TOP_MARGIN)
            // Les derniers mètres de la chute resserrent un peu le cadre : c'est là que
            // l'impact se joue, et un tir qui redescend d'un arc bas franchit cette
            // fraction-là bien avant de toucher le sol, donc sans à-coup à l'arrivée.
            val descentFrac = if (game.ball.vy < 0f) {
                val fallen = (game.peakHeight - game.ball.y).coerceAtLeast(0f)
                val span = (game.peakHeight - camFloor).coerceAtLeast(1f)
                (fallen / span).coerceIn(0f, 1f)
            } else 0f
            val endBoost = ((descentFrac - FLIGHT_END_ZOOM_START) / (1f - FLIGHT_END_ZOOM_START))
                .coerceIn(0f, 1f)
            targetScale = (min(
                width / max(machineWidth + FLIGHT_VIEW_MARGIN, FLIGHT_MIN_WIDTH),
                (height - GROUND_INSET_DP * dp) / flightHeight
                // Le dézoom du vol a le droit d'aller un peu plus loin que celui du
                // cadrage libre : un tir très haut demande de reculer au-delà de la
                // largeur du terrain, et mieux vaut un peu de vide sur les côtés qu'un
                // boulet sorti par le haut de l'écran.
            ) * (1f + endBoost * FLIGHT_END_ZOOM_BOOST)).coerceIn(minScale() * FLIGHT_ZOOM_OUT, maxScale())
            // On vise devant le boulet, d'autant plus loin qu'il va vite : le
            // cadrage anticipe au lieu de courir après.
            tx = game.ball.x + game.ball.vx * 0.4f
            ty = groundCamY(targetScale)
            follow = 8f
        } else {
            val targetWidth: Float
            val targetHeight: Float
            if (game.phase == TrebuchetGame.Phase.RESULT) {
                if (game.level != null && game.targets.tookDamage && !game.targets.cleared) {
                    // Touché, mais pas encore rasé : la caméra reste sur la
                    // construction plutôt que de reculer montrer l'arc, c'est elle
                    // qu'on veut regarder maintenant.
                    val t = game.targets
                    targetWidth = (t.right - t.left + RESULT_HIT_MARGIN)
                        .coerceAtLeast(RESULT_HIT_MIN_WIDTH)
                    targetHeight = (t.baseHeight - camFloor + RESULT_HIT_MARGIN)
                        .coerceAtLeast(RESULT_HIT_MIN_HEIGHT)
                    tx = (t.left + t.right) / 2f
                } else {
                    // Rien touché, ou le site est rasé : on recule pour montrer d'un
                    // coup d'œil le trébuchet à gauche, la courbe, le point d'impact,
                    // et la construction si elle est encore plus loin que ce point.
                    // Rasé, c'est aussi ce plan large qu'il faut pour le feu
                    // d'artifice : il part entre la machine et les ruines, et un
                    // cadrage collé à la construction n'en montrerait pas grand-chose.
                    val leftMost = panLeft()
                    val impactX = TrebuchetRules.FIRING_LINE + game.shotDistance
                    val cible = game.level?.takeIf { game.targets.right > impactX }
                        ?.let { game.targets.right }
                    val rightMost = (cible ?: impactX) + RESULT_VIEW_MARGIN
                    targetWidth = max(machineWidth, rightMost - leftMost).coerceAtMost(MAX_VIEW_WIDTH)
                    targetHeight = max(machineHeight, game.peakHeight - camFloor + 12f)
                    tx = (leftMost + rightMost) / 2f
                }
                follow = 3f
            } else {
                // Bandée, la machine s'étale côté arrière : pointe plongée derrière,
                // fronde couchée dessous. On décale le cadre du même côté.
                targetWidth = machineWidth
                targetHeight = machineHeight
                tx = game.pivotX - cfg.longArm * 0.15f
                follow = 4.5f
            }
            targetScale = min(
                width / targetWidth,
                (height - GROUND_INSET_DP * dp) / targetHeight
            ).coerceIn(minScale(), maxScale())
            ty = groundCamY(targetScale)
        }

        if (!camReady) {
            camX = tx; camScale = targetScale
            // Le plancher se prend une fois la vue posée, sinon il se lisserait depuis
            // un cadrage qui n'a jamais existé et le premier dixième de seconde du
            // niveau se jouerait avec l'horizon en train de glisser.
            updateFloor(dt)
            camY = groundCamY(camScale)
            camReady = true
            return
        }
        // Suivi souple : la caméra rattrape sa cible sans à-coups.
        val k = (dt * follow).coerceIn(0f, 1f)
        camX += (tx - camX) * k
        camY += (ty - camY) * k
        camScale += (targetScale - camScale) * (dt * 2.5f).coerceIn(0f, 1f)
        // Le plancher se relit **après** que le cadrage a bougé : c'est ce qu'on voit
        // maintenant qui décide jusqu'où le sol descend, pas ce qu'on voyait à l'image
        // précédente.
        updateFloor(dt)

        if (cameraFree) {
            clampCamera()
        } else {
            // Le sol se recale sur l'échelle **réelle** et non sur celle qu'on visait :
            // le zoom et le déplacement se rattrapent à des vitesses différentes, et
            // l'écart, si petit soit-il, ferait respirer l'horizon à chaque image. Il
            // n'y a rien de pire à regarder qu'un sol qui flotte.
            camY = groundCamY(camScale)
        }
    }

    /**
     * Largeur du cadrage de la machine, en mètres : un bras de 18 m ne tient pas dans
     * la fenêtre qui suffisait à un bras de 8 m.
     */
    private fun machineViewWidth(): Float =
        game.config.beamLength + game.config.slingLength + BUILD_VIEW_MARGIN

    /**
     * Hauteur du cadrage de la machine, en mètres.
     *
     * Elle compte autant que la largeur : couché, le téléphone n'a que deux cents pixels
     * de haut, et un cadrage réglé sur la seule largeur décapiterait la machine. C'est
     * là, et seulement là, que portrait et paysage diffèrent.
     */
    private fun machineViewHeight(): Float =
        game.pivotY + game.config.shortArm + game.config.hangLength + 4f

    /**
     * Pose l'un des deux cadrages demandés au double-appui.
     *
     * **Le terrain entier**, pour voir d'un coup d'œil la machine, la cible et ce qui
     * les sépare : c'est le dézoom maximal, celui-là même que les butées autorisent, et
     * il englobe la construction puisque [panRight] la prend en compte. Ou **la
     * machine**, cadrée comme pendant le réglage.
     *
     * Le passage se fait en douceur, comme tout le reste du cadrage : une caméra qui
     * saute d'un plan à l'autre fait perdre le fil de ce qu'on regardait.
     */
    private fun applyChosenView(dt: Float) {
        val tx: Float
        val targetScale: Float
        if (camView == CamView.FULL) {
            tx = (panLeft() + panRight()) / 2f
            targetScale = minScale()
        } else {
            tx = game.pivotX - game.config.longArm * 0.15f
            targetScale = min(
                width / machineViewWidth(),
                (height - GROUND_INSET_DP * dp) / machineViewHeight()
            ).coerceIn(minScale(), maxScale())
        }
        if (!camReady) {
            camX = tx
            camScale = targetScale
            updateFloor(dt)
            camY = groundCamY(camScale)
            camReady = true
            return
        }
        camX += (tx - camX) * (dt * 4.5f).coerceIn(0f, 1f)
        camScale += (targetScale - camScale) * (dt * 2.5f).coerceIn(0f, 1f)
        updateFloor(dt)
        // Les butées et le calage du sol valent ici comme ailleurs — y compris en vol,
        // où le cadrage automatique s'en passe parce qu'il suit un boulet qui, lui, a le
        // droit de sortir du terrain.
        clampCamera()
    }

    /**
     * Fige le cadrage là où il est.
     *
     * Appelé quand le joueur arrête son tir en cours de route : il regardait quelque
     * chose, et la caméra n'a aucune raison de reculer d'elle-même pour montrer un arc
     * qu'il vient précisément d'interrompre.
     */
    fun freezeCamera() {
        synchronized(game) {
            camPhase = game.phase
            camView = CamView.AUTO
            manualCam = true
        }
    }

    /**
     * Les butées du cadrage libre : juste derrière la machine d'un côté, la ligne
     * des cinq cents mètres de l'autre. La hauteur, elle, ne se règle pas : hors du
     * vol, le sol est calé en bas de l'image et n'en bouge plus, zoom compris. Une
     * vue de tir se lit comme une gravure — l'horizon toujours à la même place, et
     * seule la distance qui défile.
     */
    private fun clampCamera() {
        camScale = camScale.coerceIn(minScale(), maxScale())
        val halfW = width / 2f / camScale

        val left = panLeft()
        val right = panRight()
        camX = if (right - left <= halfW * 2f) {
            // Dézoom complet : le terrain est plus étroit que la vue, on le centre.
            (left + right) / 2f
        } else {
            camX.coerceIn(left + halfW, right - halfW)
        }
        camY = groundCamY(camScale)
    }

    /**
     * L'ordonnée de caméra qui pose le sol au bas de l'image, la bande de terre des
     * bornes de distance gardée dessous.
     *
     * **Le « sol », c'est le point le plus bas qu'on voie, et pas l'altitude zéro.** La
     * première version calait le zéro sur le bas de l'écran, ce qui revenait à décréter
     * que rien n'est jamais sous les pieds de la machine. C'était vrai tant que le
     * terrain était une droite ; depuis qu'un site peut se bâtir au fond d'un vallon,
     * huit mètres plus bas, ce site-là tombait purement et simplement **sous le bord
     * inférieur de l'écran** — le joueur voyait un pré vide et tirait sur une cible
     * qu'il ne pouvait pas regarder.
     *
     * Le plancher se prend donc sur **la portion visible**, et jamais au-dessus de zéro :
     * une butte n'abaisse pas le cadrage, elle monte dans l'image comme il se doit, et un
     * terrain plat se cadre exactement comme avant. On ne paie le décalage que lorsqu'un
     * creux est réellement à l'écran.
     */
    private fun groundCamY(scale: Float) = camFloor + (height / 2f - GROUND_INSET_DP * dp) / scale

    /**
     * Le plancher du cadrage : jusqu'où le sol descend dans ce qu'on regarde.
     *
     * Il est **lissé**, et pour la même raison que tout le reste du cadrage : il change
     * quand un creux entre dans la vue, et un plancher qui sauterait ferait sauter
     * l'horizon avec lui. Lissé, on descend dans le vallon comme on y marcherait.
     */
    private fun updateFloor(dt: Float) {
        val halfW = width / 2f / camScale
        val vise = min(0f, game.terrain.lowestBetween(camX - halfW, camX + halfW))
        camFloor = if (!camReady) vise else camFloor + (vise - camFloor) * (dt * 4f).coerceIn(0f, 1f)
    }

    /** Butée arrière : la pointe du bras bandé plonge de tout le bras long. */
    private fun panLeft(): Float = game.pivotX - game.config.longArm - PAN_BACK_MARGIN

    /** Butée avant : cinq cents mètres, ou le tir en cours s'il est allé plus loin. */
    private fun panRight(): Float {
        // La butée avant doit englober la cible, sinon le joueur ne peut littéralement
        // pas aller voir ce qu'il est en train de détruire.
        val cible = game.level?.let { game.targets.right + 40f } ?: 0f
        return max(max(PAN_FRONT, game.shotDistance + 60f), cible)
            .coerceAtMost(TrebuchetRules.GROUND_RIGHT)
    }

    /** Dézoom maximal : tout le terrain tient dans la largeur de l'écran. */
    private fun minScale(): Float = width / max(panRight() - panLeft(), MIN_VIEW_WIDTH)

    /** Zoom maximal : de quoi examiner le crochet de largage à la loupe. */
    private fun maxScale(): Float = width / MIN_VIEW_WIDTH

    private fun sx(x: Float) = (x - camX) * camScale + width / 2f
    private fun sy(y: Float) = height / 2f - (y - camY) * camScale

    private fun worldX(px: Float) = (px - width / 2f) / camScale + camX
    private fun worldY(py: Float) = camY - (py - height / 2f) / camScale

    // ── Saisie ───────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var notify = false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> synchronized(game) {
                val now = SystemClock.uptimeMillis()
                val doubleTap = now - lastTapAt < DOUBLE_TAP_MS
                lastTapAt = now
                grip = Grip.NONE
                tappedVoid = false
                dragTravel = 0f
                gestureLocked = false
                if (doubleTap) {
                    // Deux appuis rapprochés : on lâche la pièce et on change de plan.
                    // Un seul geste, un seul sens.
                    if (selected != Part.NONE) {
                        selected = Part.NONE
                        notify = true
                    }
                    // Le premier double-appui recule pour montrer tout le terrain, le
                    // suivant revient sur la machine, et ainsi de suite. Deux plans, une
                    // bascule : c'est la question que le joueur se pose vraiment — *où
                    // est la cible* puis *à quoi ressemble ma machine* — et elle se pose
                    // à tout moment, tir en cours compris.
                    manualCam = false
                    camView = if (camView == CamView.FULL) CamView.MACHINE else CamView.FULL
                    gestureLocked = true
                } else {
                    notify = grabAt(worldX(event.x), worldY(event.y))
                }
                // Un appui dans le ciel est candidat au contrôle de l'heure : rien
                // d'autre ne se dispute cette zone-là. On ne décide pas tout de suite —
                // c'est la durée qui tranchera, dans [updateTimeControl].
                val wx = worldX(event.x)
                timeCandidate = grip == Grip.NONE && !gestureLocked &&
                    worldY(event.y) > game.terrain.heightAt(wx)
                timePressAt = now
                timePressX = event.x
                timeFingerX = event.x
                readPointers(event, -1)
            }
            // Un deuxième doigt, c'est toujours la caméra : on lâche le réglage en cours.
            MotionEvent.ACTION_POINTER_DOWN -> synchronized(game) {
                if (grip != Grip.NONE) {
                    grip = Grip.NONE
                    notify = true
                }
                // Et on lève le verrou posé par une sélection : le premier doigt vient
                // peut-être de prendre une pièce, mais deux doigts veulent la caméra.
                gestureLocked = false
                // Deux doigts, c'est le zoom : le contrôle de l'heure rend la main.
                stopTimeControl()
                readPointers(event, -1)
            }
            MotionEvent.ACTION_MOVE -> synchronized(game) {
                timeFingerX = event.x
                if (timeMode) {
                    // En contrôle de l'heure, le doigt ne fait plus que ça : la vue ne
                    // suit pas, sinon on balaierait le temps et le terrain à la fois.
                } else if (grip != Grip.NONE) {
                    applyGrip(worldX(event.x), worldY(event.y))
                    notify = true
                } else {
                    // Un doigt qui part avant la fin de l'appui long voulait déplacer la
                    // vue : ce n'est plus un candidat.
                    if (abs(event.x - timePressX) > DRAG_SLOP_DP * dp) timeCandidate = false
                    dragCamera(event)
                }
            }
            // Un doigt s'en va : on repart de ceux qui restent, sinon la vue saute.
            MotionEvent.ACTION_POINTER_UP -> synchronized(game) {
                readPointers(event, event.actionIndex)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Un appui dans le vide qui n'a pas glissé : le joueur pose la pièce.
                if (tappedVoid && dragTravel < DRAG_SLOP_DP * dp && selected != Part.NONE) {
                    synchronized(game) { selected = Part.NONE }
                    notify = true
                }
                if (grip != Grip.NONE) notify = true
                grip = Grip.NONE
                tappedVoid = false
                gestureCount = 0
                // Lâcher le doigt sort du contrôle de l'heure : c'est un geste maintenu,
                // pas un mode dans lequel on entre et dont on ressort par un autre
                // chemin. Rien à désactiver, rien à oublier de désactiver.
                stopTimeControl()
            }
        }
        // Hors du verrou : l'activité relit la machine pour son bandeau.
        if (notify) listener?.onMachineChanged()
        return true
    }

    /**
     * Un doigt se pose. Trois cas : il tombe sur une poignée de la pièce **déjà tenue**
     * et on règle ; il tombe sur une autre pièce et on la prend en main, sans rien
     * régler ; il tombe dans le vide, et c'est le lever du doigt qui dira s'il
     * s'agissait d'une déselection ou du début d'un glissement de vue.
     *
     * **Prendre une pièce en main ne la règle pas, et c'est le point important.** Le
     * même geste faisait les deux : on touchait la poutre, la fenêtre de réglage
     * s'ouvrait, et les quelques millimètres que le doigt parcourt toujours avant de se
     * relever changeaient déjà sa longueur. Le joueur voyait donc sa machine bouger
     * avant même d'avoir demandé quoi que ce soit, et devait la remettre comme elle
     * était avant de pouvoir la régler pour de bon.
     *
     * Il faut désormais deux gestes, et c'est ce qu'on attend d'une sélection : le
     * premier prend la pièce, le second l'étire. Les poignées sont dessinées entre les
     * deux, donc on sait où poser le doigt.
     */
    private fun grabAt(wx: Float, wy: Float): Boolean {
        if (selected != Part.NONE) {
            val g = pickGrip(selected, wx, wy)
            if (g != Grip.NONE) {
                startGrip(g, wx, wy)
                return true
            }
        }
        val part = pickPart(wx, wy)
        if (part == Part.NONE) {
            tappedVoid = true
            return false
        }
        // Attraper une pièce pendant ou après un tir rebande la machine : c'est ce que
        // le joueur veut dire. La caméra, elle, ne bouge pas d'un pouce — on ne déplace
        // pas la vue sous un doigt qui vient de se poser.
        if (game.phase != TrebuchetGame.Phase.BUILD) {
            // En plein vol, on **termine** le tir avant de rebander : c'est le même
            // geste que le bouton, et il doit garder les mêmes choses. Rebander tout de
            // suite jetterait la traînée du tir en cours, qui est précisément ce que le
            // joueur venait de regarder.
            game.stopShot()
            game.rebuild()
            lastPhase = game.phase
            camPhase = game.phase
            // Le joueur revient à sa machine : le plan choisi au double-appui n'a plus
            // lieu d'être, la caméra reprend son cadrage de réglage.
            camView = CamView.AUTO
        }
        selected = part
        // Le geste s'arrête là : ni réglage, ni glissement de vue. Un appui qui prend
        // une pièce en main ne fait que ça, quoi que le doigt fasse ensuite avant de se
        // relever.
        gestureLocked = true
        return true
    }

    /**
     * Saisit un réglage en mémorisant l'écart entre la valeur et ce que mesure le
     * doigt : sans lui, attraper une poutre par le milieu la ferait sauter à la
     * longueur du point touché.
     */
    private fun startGrip(g: Grip, wx: Float, wy: Float) {
        grip = g
        gripOffset = gripValue(g) - measureGrip(g, wx, wy)
    }

    /** La valeur du réglage, dans l'unité où le doigt la mesure. */
    private fun gripValue(g: Grip): Float {
        val cfg = game.config
        return when (g) {
            Grip.BEAM_LENGTH -> cfg.longArm
            Grip.LEVER -> cfg.shortArm
            Grip.POST_HEIGHT -> cfg.pivotHeight
            Grip.CW_MASS -> cfg.counterweightHalf
            Grip.CW_HANG -> cfg.hangLength
            Grip.PIN_ANGLE -> cfg.pinAngleDeg
            Grip.SLING_LENGTH -> cfg.slingLength
            Grip.NONE -> 0f
        }
    }

    /**
     * Ce que le doigt désigne, dans la même unité que [gripValue].
     *
     * **Toute mesure se prend depuis un point qui ne bouge pas quand la valeur change**,
     * et c'est une règle, pas une préférence. Le collier du levier se mesurait depuis le
     * talon de la poutre — lequel recule justement quand le bras court s'allonge. Le
     * réglage se nourrissait donc lui-même : le doigt écartait le collier de dix
     * centimètres, la poutre glissait de dix centimètres, l'événement suivant relisait
     * le même écart depuis un talon qui avait bougé d'autant, et en une poignée
     * d'images le levier était collé à sa butée. Le joueur tirait pour allonger et
     * voyait tout raccourcir d'un coup.
     *
     * Le pivot, lui, est planté dans le sol : c'est le seul repère honnête de cette
     * machine, et c'est depuis lui que les deux réglages de la poutre se mesurent.
     */
    private fun measureGrip(g: Grip, wx: Float, wy: Float): Float = when (g) {
        // Le long de la poutre depuis l'axe : c'est la longueur du bras long.
        Grip.BEAM_LENGTH -> alongBeam(game.pivotX, game.pivotY, wx, wy)
        // Le long de la poutre depuis l'axe, là aussi. Ce que le doigt désigne est
        // l'endroit de la poutre qui doit venir se poser sur l'axe : l'écart au pivot
        // est donc exactement ce dont le bras court doit croître.
        Grip.LEVER -> alongBeam(game.pivotX, game.pivotY, wx, wy)
        Grip.POST_HEIGHT -> wy
        Grip.CW_MASS -> max(abs(wx - game.counterweight.x), abs(wy - game.counterweight.y))
        Grip.CW_HANG -> {
            game.buttWorld(pickTmp)
            pickTmp[1] - wy
        }
        Grip.PIN_ANGLE -> pinAngleAt(wx, wy)
        Grip.SLING_LENGTH -> {
            game.tipWorld(pickTmp)
            hypot(pickTmp[0] - wx, pickTmp[1] - game.config.shotRadius)
        }
        Grip.NONE -> 0f
    }

    /**
     * Pose la valeur désignée par le doigt. Le jeu se charge de la borner : un
     * réglage borné n'arrête pas le doigt, il arrête la machine.
     */
    private fun applyGrip(wx: Float, wy: Float) {
        val cfg = game.config
        val v = measureGrip(grip, wx, wy) + gripOffset
        when (grip) {
            // Le doigt tient le bras long ; la poutre entière en découle.
            Grip.BEAM_LENGTH -> game.setBeamLength(v * (1f + cfg.leverRatio) / cfg.leverRatio)
            // Le doigt fait coulisser la poutre dans son axe : le bras court change,
            // la longueur totale ne bouge pas.
            Grip.LEVER -> {
                val short = v.coerceIn(cfg.beamLength * 0.08f, cfg.beamLength * 0.45f)
                game.setLeverRatio((cfg.beamLength - short) / short)
            }
            Grip.POST_HEIGHT -> game.setPivotHeight(v)
            // Le côté de la caisse suit la racine de la masse : on remonte au carré.
            Grip.CW_MASS -> {
                val side = v.coerceAtLeast(0.01f) / 0.0105f
                game.setCounterweightMass(side * side)
            }
            Grip.CW_HANG -> game.setHangLength(v)
            Grip.PIN_ANGLE -> game.setPinAngle(v)
            Grip.SLING_LENGTH -> game.setSlingLength(v)
            Grip.NONE -> {}
        }
        // Un coup de marteau de temps en temps, pas un par frame : un glissé fluide
        // en enverrait des dizaines par seconde, ce qui martèlerait au lieu de taper.
        val now = SystemClock.uptimeMillis()
        if (now - lastHammerAt >= HAMMER_INTERVAL_MS) {
            lastHammerAt = now
            sfx.hammerTap()
        }
    }

    /** Permet à la roulette ([TrebuchetWheelBubble]), qui règle la même machine
     *  sans passer par ici, de taper le même marteau à chaque cran tourné. */
    fun playHammerTap() = sfx.hammerTap()

    /** Coupe ou rétablit tous les bruitages — le réglage du menu. */
    var soundEnabled: Boolean
        get() = sfx.enabled
        set(value) { sfx.enabled = value }

    // ── Désignation ───────────────────────────────────────────────────────────

    /** La pièce sous le doigt, la plus petite d'abord : sinon la poutre prend tout. */
    private fun pickPart(wx: Float, wy: Float): Part {
        val reach = pickReach()
        game.pinWorld(pickTmp)
        if (hypot(wx - pickTmp[0], wy - pickTmp[1]) < reach) return Part.PIN
        if (hypot(wx - game.ball.x, wy - game.ball.y) < reach + game.config.shotRadius) {
            return Part.SLING
        }
        val cw = game.counterweight
        val box = game.config.counterweightHalf + reach
        if (abs(wx - cw.x) < box && abs(wy - cw.y) < box) return Part.WEIGHT
        if (onPostFrame(wx, wy)) return Part.POST
        if (distanceToBeam(wx, wy) < reach) return Part.BEAM
        return Part.NONE
    }

    /**
     * Vrai si le doigt est sur le **bâti** : le triangle des deux jambes, du sol
     * jusqu'aux deux tiers de la hauteur du pivot.
     *
     * On ne prend pas le pied par son sommet, et c'est tout l'objet de cette fonction.
     * Le sommet du bâti est le seul endroit de la machine où trois pièces se
     * rencontrent : le pivot, la poutre qui tourne autour, et le contrepoids qui pend
     * juste à côté à moins d'un mètre. Y viser le pied, c'était attraper le
     * contrepoids une fois sur deux — et le contrepoids, lui, est testé en premier.
     *
     * Plus bas, le bâti est seul, et il s'élargit : ses jambes s'écartent de trente
     * pour cent de sa hauteur de chaque côté, ce qui fait au ras du sol une cible de
     * cinq mètres de large. On ne peut pas la rater.
     */
    private fun onPostFrame(wx: Float, wy: Float): Boolean {
        val h = game.pivotY
        if (h <= 0f) return false
        val reach = pickReach()
        if (wy < -reach || wy > h * POST_PICK_TOP) return false
        // L'écartement des jambes à cette hauteur-là : nul au pivot, maximal au sol.
        val spread = 0.30f * h * (1f - (wy / h).coerceIn(0f, 1f))
        return abs(wx - game.pivotX) <= spread + reach
    }

    /**
     * La poignée visée sur la pièce tenue. C'est ici que deux réglages partagent une
     * pièce sans se marcher dessus : la poutre s'allonge par sa longueur et se
     * recentre par son collier, la caisse pend par son corps et s'alourdit par son
     * coin.
     */
    private fun pickGrip(part: Part, wx: Float, wy: Float): Grip {
        val reach = pickReach()
        val onAxle = hypot(wx - game.pivotX, wy - game.pivotY) < reach
        return when (part) {
            Part.BEAM -> when {
                onAxle -> Grip.LEVER
                distanceToBeam(wx, wy) < reach -> Grip.BEAM_LENGTH
                else -> Grip.NONE
            }
            // Le pied s'étire par son bâti, mais aussi par son sommet : on a pris la
            // peine de le sélectionner, autant ne plus être regardant sur la visée.
            Part.POST -> if (onAxle || onPostFrame(wx, wy)) Grip.POST_HEIGHT else Grip.NONE
            Part.WEIGHT -> {
                val cw = game.counterweight
                val h = game.config.counterweightHalf
                when {
                    hypot(wx - (cw.x + h), wy - (cw.y - h)) < reach -> Grip.CW_MASS
                    abs(wx - cw.x) < h + reach && abs(wy - cw.y) < h + reach -> Grip.CW_HANG
                    else -> Grip.NONE
                }
            }
            Part.PIN -> {
                game.pinWorld(pickTmp)
                if (hypot(wx - pickTmp[0], wy - pickTmp[1]) < reach) Grip.PIN_ANGLE else Grip.NONE
            }
            Part.SLING ->
                if (hypot(wx - game.ball.x, wy - game.ball.y) < reach + game.config.shotRadius) {
                    Grip.SLING_LENGTH
                } else {
                    Grip.NONE
                }
            Part.NONE -> Grip.NONE
        }
    }

    /** La tolérance de saisie, en mètres, à l'échelle où l'on regarde. */
    private fun pickReach(): Float =
        (PICK_REACH_DP * dp / camScale).coerceAtMost(PICK_REACH_MAX)

    /** Projection du doigt sur l'axe de la poutre, comptée depuis un point donné. */
    private fun alongBeam(ox: Float, oy: Float, wx: Float, wy: Float): Float {
        game.buttWorld(axisA)
        game.tipWorld(axisB)
        val ux = axisB[0] - axisA[0]
        val uy = axisB[1] - axisA[1]
        val len = hypot(ux, uy)
        if (len < 0.001f) return 0f
        return ((wx - ox) * ux + (wy - oy) * uy) / len
    }

    /** Distance du doigt à la poutre, prise sur le segment talon-pointe. */
    private fun distanceToBeam(wx: Float, wy: Float): Float {
        game.buttWorld(axisA)
        game.tipWorld(axisB)
        val ux = axisB[0] - axisA[0]
        val uy = axisB[1] - axisA[1]
        val len2 = ux * ux + uy * uy
        if (len2 < 0.000001f) return hypot(wx - axisA[0], wy - axisA[1])
        val t = (((wx - axisA[0]) * ux + (wy - axisA[1]) * uy) / len2).coerceIn(0f, 1f)
        return hypot(wx - (axisA[0] + t * ux), wy - (axisA[1] + t * uy))
    }

    /**
     * L'inclinaison que le doigt donne au crochet, en degrés depuis l'axe du bras.
     * On repasse dans le repère de la poutre : c'est là que l'angle a un sens, et il
     * y garde le même quelle que soit la position du bras.
     */
    private fun pinAngleAt(wx: Float, wy: Float): Float {
        val b = game.beam
        val c = cos(b.angle)
        val si = sin(b.angle)
        val dx = wx - b.x
        val dy = wy - b.y
        val lx = dx * c + dy * si
        val ly = -dx * si + dy * c
        val a = atan2(ly, lx - game.config.beamLength / 2f)
        return Math.toDegrees(a.toDouble()).toFloat()
    }

    /**
     * Glissement et pincement, d'un seul tenant : l'endroit du terrain posé sous
     * les doigts y reste. C'est ce qui fait qu'un zoom à deux doigts colle à l'image
     * au lieu de partir en vrille autour du centre de l'écran.
     */
    private fun dragCamera(event: MotionEvent) {
        val hadPointers = gestureCount > 0
        val prevX = focusX
        val prevSpread = focusSpread
        readPointers(event, -1)
        if (!hadPointers || gestureCount == 0) return

        // Seul l'écart horizontal compte : un doigt qui monte ne fait rien, ce n'est
        // pas un début de glissement.
        dragTravel += abs(focusX - prevX)
        if (gestureLocked || !cameraFree) return
        val pinching = gestureCount >= 2 && prevSpread > 1f && focusSpread > 1f
        if (!pinching && dragTravel < DRAG_SLOP_DP * dp) return

        // L'abscisse du monde visée, mesurée avant de changer d'échelle : c'est elle
        // qui restera sous les doigts. La hauteur, elle, appartient au sol.
        val wx = worldX(prevX)
        if (pinching) {
            camScale = (camScale * (focusSpread / prevSpread)).coerceIn(minScale(), maxScale())
        }
        camX = wx - (focusX - width / 2f) / camScale
        manualCam = true
        camReady = true
        clampCamera()
    }

    /**
     * Relève la position moyenne des doigts et leur écartement, en ignorant
     * éventuellement celui qui est en train de se lever. Au-delà de deux, les doigts
     * supplémentaires n'apprennent plus rien.
     */
    private fun readPointers(event: MotionEvent, skip: Int) {
        var n = 0
        var x0 = 0f; var y0 = 0f
        var x1 = 0f; var y1 = 0f
        for (i in 0 until event.pointerCount) {
            if (i == skip) continue
            if (n == 0) {
                x0 = event.getX(i); y0 = event.getY(i)
            } else if (n == 1) {
                x1 = event.getX(i); y1 = event.getY(i)
            }
            n++
        }
        gestureCount = min(n, 2)
        when (gestureCount) {
            0 -> focusSpread = 0f
            1 -> {
                focusX = x0; focusY = y0; focusSpread = 0f
            }
            else -> {
                focusX = (x0 + x1) / 2f
                focusY = (y0 + y1) / 2f
                focusSpread = hypot(x0 - x1, y0 - y1)
            }
        }
    }

    // ── Prévisualisation ──────────────────────────────────────────────────────

    /**
     * Rejoue le départ sur la machine fantôme quand celle du joueur a changé.
     *
     * Deux garde-fous : on ne rejoue que si un réglage a bougé, et jamais plus de
     * huit fois par seconde. Un départ coûte quelques centaines de pas de solveur —
     * rien du tout de temps en temps, beaucoup trop à chaque image.
     */
    private fun updatePreview() {
        if (game.phase != TrebuchetGame.Phase.BUILD) return

        // Un réglage a bougé : on relâche un nouveau fantôme, mais pas plus souvent
        // que la cadence. Pendant un glissement, la machine change à chaque image.
        val sig = configSignature(game.config)
        val now = SystemClock.uptimeMillis()
        if (sig != previewSig && now - previewAt >= PREVIEW_PERIOD_MS) {
            previewAt = now
            previewSig = sig
            ghostMachine.config.copyFrom(game.config)
            // Le fantôme doit voler dans le même air que le vrai boulet, sinon le cône
            // du départ promet une trajectoire que le tir ne tiendra pas. C'est le genre
            // de mensonge qu'un joueur met dix tirs à identifier et qui lui fait accuser
            // la machine.
            ghostMachine.applyWind(game.wind)
            ghostMachine.build()
            ghostMachine.release()
            previewSteps = 0
        }
        if (previewSteps < 0) return

        // Le départ se joue par tranches : le fantôme avance de quelques pas, puis
        // rend la main à l'affichage. La trace précédente reste visible en attendant,
        // ce qui vaut mieux qu'un cône qui clignote à chaque cran.
        var budget = PREVIEW_BUDGET
        while (budget > 0 && previewSteps < PREVIEW_MAX_STEPS &&
            ghostMachine.phase == TrebuchetGame.Phase.FLIGHT &&
            ghostMachine.ball.x - TrebuchetRules.FIRING_LINE < PREVIEW_METRES
        ) {
            ghostMachine.step(1f / 120f)
            previewSteps++
            budget--
        }
        if (budget > 0 || previewSteps >= PREVIEW_MAX_STEPS) {
            previewPath = ghostMachine.startTrace()
            previewSteps = -1
        }
    }

    /**
     * L'empreinte de ce qui change le départ d'un tir.
     *
     * Les **réglages** sont l'affaire de [MachineConfig.signature] : la vue en tenait
     * autrefois sa propre liste recopiée à la main, et elle a raté les deux réglages de
     * masse le jour où ils sont apparus — l'aperçu montrait alors la trajectoire du
     * projectile précédent, sans que rien ne le signale.
     *
     * Ne reste ici que le **vent**, qui ne fait pas partie des réglages mais change le
     * vol : un site suivant plus venteux garderait sinon le cône du site précédent.
     */
    private fun configSignature(c: MachineConfig): Int {
        var h = c.signature()
        h = h * 31 + game.wind.speed.toRawBits()
        h = h * 31 + game.wind.angle.toRawBits()
        return h
    }

    // ── Rendu ────────────────────────────────────────────────────────────────

    private fun drawFrame(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        drawSky(canvas, w, h)

        // Les feux d'artifice passent **derrière** le terrain : ils montent au fond du
        // ciel, et le sol leur coupe les jambes quand leurs étoiles retombent, ce qui
        // est exactement ce qu'on voit dehors.
        drawSparks(canvas, background = true)

        // Le paysage — sol, verdure, constructions, habitants, feux — et la nuit qui
        // se pose dessus. Tout est peint par [land], donc l'atelier d'engrenages en a
        // exactement le même : voir [LandScene].
        land.draw(
            canvas, w, h, camX, camY, camScale,
            game.terrain, game.targets, sky.light, game.wind.vx, ambientClock
        )
        drawGhosts(canvas)
        drawStartCone(canvas)
        drawFrameAndPivot(canvas)
        drawStrap(canvas)
        drawBody(canvas, game.counterweight, pWeight, pWeightEdge)
        drawBody(canvas, game.beam, pBeam, pEdge)
        drawPin(canvas)
        drawSling(canvas)
        // Une bombe qui a soufflé n'est plus là : voir [TrebuchetGame.ballGone].
        if (!game.ballGone) drawBody(canvas, game.ball, pBall, pBallEdge)
        // Les éclats d'un paquet qui s'est défait : rien ne les distingue du boulet,
        // sinon qu'ils sont plusieurs et plus petits.
        for (i in game.shards.indices) drawBody(canvas, game.shards[i], pBall, pBallEdge)
        drawTrail(canvas)
        // Les explosions, elles, sont devant tout : une bombe qui souffle derrière le
        // château qu'elle détruit n'aurait aucun sens.
        drawSparks(canvas, background = false)
        drawGrabSpots(canvas)
        drawSelection(canvas)
        drawRecenterHint(canvas)
        drawTimeControl(canvas, w)
        drawWindGauge(canvas, w)
    }

    /**
     * Le ciel, les astres et leurs habitants — tout est peint par [backdrop].
     *
     * La vue ne garde de tout ça que le cadrage qu'elle prête au décor : les astres se
     * posent en fractions d'écran, mais les nuages et les oiseaux sont des objets du
     * monde, et il leur faut la caméra pour savoir où atterrir.
     */
    private fun drawSky(canvas: Canvas, w: Float, h: Float) {
        backdrop.draw(canvas, w, h, camX, camY, camScale)
    }

    /**
     * Fait vivre le contrôle de l'heure, une image à la fois.
     *
     * L'appui long ne se mesure pas dans le gestionnaire de toucher, et c'est délibéré :
     * un doigt immobile n'envoie **aucun** événement, donc un appui long qui attendrait
     * un `ACTION_MOVE` ne se déclencherait jamais. On regarde donc l'âge de l'appui à
     * chaque image, là où le temps passe de toute façon.
     *
     * Une fois dedans, la vitesse suit **l'écart au point d'appui** et non la position
     * absolue du doigt : sinon un appui près du bord droit partirait à pleine vitesse
     * avant d'avoir bougé. Au point d'appui la vitesse est nulle — attraper l'heure
     * l'arrête, et c'est le meilleur moyen de regarder un crépuscule aussi longtemps
     * qu'on veut.
     */
    private fun updateTimeControl(dt: Float) {
        if (!timeMode) {
            if (timeCandidate && SystemClock.uptimeMillis() - timePressAt >= TIME_HOLD_MS) {
                timeMode = true
                timePressX = timeFingerX
            }
            return
        }
        timeRate = SkyClock.scrubRate(timeFingerX - timePressX, width * TIME_THROW)
        skyClock.scrub(timeRate * dt)
    }

    private fun stopTimeControl() {
        timeMode = false
        timeCandidate = false
        timeRate = 0f
    }

    /**
     * Ce que le joueur lit pendant qu'il tient l'heure : l'heure qu'il est, et à quelle
     * vitesse elle file.
     *
     * Sans ça le geste serait un secret : rien à l'écran ne dirait qu'on a quitté le
     * cadrage pour le temps, et un ciel qui se met à défiler passerait pour un bug.
     */
    private fun drawTimeControl(canvas: Canvas, w: Float) {
        if (!timeMode) return
        val minutes = ((skyClock.instant % 86_400_000L) + 86_400_000L) % 86_400_000L / 60_000L
        val heure = minutes / 60
        val minute = minutes % 60
        val sens = when {
            timeRate > 0.02f -> "▶"
            timeRate < -0.02f -> "◀"
            else -> "■"
        }
        pHint.textSize = 20f * dp
        canvas.drawText(
            "%02d:%02d  %s %.1f h/s".format(heure, minute, sens, abs(timeRate)),
            w / 2f, height * 0.18f, pHint
        )
    }

    /**
     * Le bâti : un A sous le pivot, purement décoratif mais il donne l'échelle.
     *
     * C'est l'un des deux seuls endroits de la vue où l'altitude zéro est écrite en dur,
     * et c'est légitime : le tablier sous la machine est **plat à zéro quel que soit le
     * relief**, et un test le garantit — voir `TrebuchetTerrainTest.le tablier de la
     * machine reste plat`. Inutile de le repasser au crible au prochain audit des
     * hauteurs.
     */
    private fun drawFrameAndPivot(canvas: Canvas) {
        val px = sx(game.pivotX)
        val py = sy(game.pivotY)
        val groundY = sy(0f)
        val spread = 0.30f * game.pivotY * camScale
        val legW = max(2f * dp, 0.06f * camScale)

        tmpPath.reset()
        tmpPath.moveTo(px - legW, py)
        tmpPath.lineTo(px - spread, groundY)
        tmpPath.lineTo(px - spread + legW * 2f, groundY)
        tmpPath.lineTo(px + legW, py)
        tmpPath.close()
        canvas.drawPath(tmpPath, pFrame)

        tmpPath.reset()
        tmpPath.moveTo(px - legW, py)
        tmpPath.lineTo(px + spread - legW * 2f, groundY)
        tmpPath.lineTo(px + spread, groundY)
        tmpPath.lineTo(px + legW, py)
        tmpPath.close()
        canvas.drawPath(tmpPath, pFrame)

        canvas.drawCircle(px, py, max(3f * dp, 0.10f * camScale), pPivot)
    }

    /** L'élingue qui suspend le contrepoids sous le bras court. */
    private fun drawStrap(canvas: Canvas) {
        game.buttWorld(butt)
        canvas.drawLine(
            sx(butt[0]), sy(butt[1]),
            sx(game.counterweight.x), sy(game.counterweight.y), pStrap
        )
    }

    /** Le crochet de largage, planté dans le prolongement de la pointe du bras. */
    private fun drawPin(canvas: Canvas) {
        game.tipWorld(tip)
        game.pinWorld(pin)
        canvas.drawLine(sx(tip[0]), sy(tip[1]), sx(pin[0]), sy(pin[1]), pPin)
    }

    /**
     * La fronde. Tant que la boucle est sur le crochet, elle relie la pointe au
     * boulet ; molle, elle s'efface, parce qu'une corde molle ne transmet rien et
     * qu'il faut que ça se voie.
     */
    private fun drawSling(canvas: Canvas) {
        if (game.ballFree) return
        game.tipWorld(tip)
        canvas.drawLine(
            sx(tip[0]), sy(tip[1]), sx(game.ball.x), sy(game.ball.y),
            if (game.sling.isSlack) pSlingSlack else pSling
        )
    }

    private fun drawBody(canvas: Canvas, b: PhysBody, fill: Paint, edge: Paint) {
        for (i in b.parts.indices) drawPart(canvas, b, i, fill, edge)
    }

    private fun drawPart(canvas: Canvas, b: PhysBody, part: Int, fill: Paint, edge: Paint) {
        val p = b.parts[part]
        if (p.shape == Shape.CIRCLE) {
            b.partWorld(part, partPose)
            val cx = sx(partPose[0])
            val cy = sy(partPose[1])
            val r = p.radius * camScale
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, edge)
            // Un rayon tracé, pour qu'on voie le boulet rouler.
            canvas.drawLine(
                cx, cy,
                cx + r * kotlin.math.cos(partPose[2]), cy - r * kotlin.math.sin(partPose[2]),
                edge
            )
            return
        }
        b.partCorners(part, corners)
        tmpPath.reset()
        tmpPath.moveTo(sx(corners[0]), sy(corners[1]))
        for (i in 1 until 4) tmpPath.lineTo(sx(corners[i * 2]), sy(corners[i * 2 + 1]))
        tmpPath.close()
        canvas.drawPath(tmpPath, fill)
        canvas.drawPath(tmpPath, edge)
    }

    private fun drawTrail(canvas: Canvas) {
        drawPolyline(canvas, game.trail, game.trailCount, pTrail)
    }


    /**
     * L'indicateur de vent : une flèche et un chiffre, en haut à droite.
     *
     * Il est dessiné et non posé en vue Android, pour une raison simple : la flèche doit
     * pointer **exactement** là où souffle l'air, pente comprise, et une image tournée
     * de dix-sept degrés dans une mise en page est bien plus de travail que deux traits.
     *
     * Le fond ne s'affiche que quand il y a un vent : par temps calme, l'indicateur
     * s'efface complètement plutôt que d'annoncer un zéro. Un cadran qui ne dit rien est
     * un cadran qu'on apprend à ne plus regarder.
     */
    private fun drawWindGauge(canvas: Canvas, w: Float) {
        val wind = game.wind
        if (wind.calm) return
        val force = (wind.speed / Wind.MAX_SPEED).coerceIn(0f, 1f)
        val pad = 10f * dp
        val boxW = 92f * dp
        val boxH = 40f * dp
        val left = w - boxW - pad
        windBox.set(left, pad, left + boxW, pad + boxH)
        canvas.drawRoundRect(windBox, 8f * dp, 8f * dp, pGaugeBed)

        // La flèche, dans le sens du vent, longueur selon la force.
        val cx = left + 26f * dp
        val cy = pad + boxH / 2f
        val len = (10f + 12f * force) * dp
        val dx = cos(wind.angle)
        val dy = -sin(wind.angle)
        pGaugeArrow.strokeWidth = (2f + 1.5f * force) * dp
        canvas.drawLine(cx - dx * len, cy - dy * len, cx + dx * len, cy + dy * len, pGaugeArrow)
        // La pointe : deux barbes, tracées à la main plutôt qu'avec une rotation de
        // toile — une flèche, c'est trois traits, et une matrice c'est une allocation.
        val hx = cx + dx * len
        val hy = cy + dy * len
        val bx = -dx * 7f * dp
        val by = -dy * 7f * dp
        canvas.drawLine(hx, hy, hx + bx - by * 0.6f, hy + by + bx * 0.6f, pGaugeArrow)
        canvas.drawLine(hx, hy, hx + bx + by * 0.6f, hy + by - bx * 0.6f, pGaugeArrow)

        canvas.drawText(
            "%d m/s".format(wind.speed.roundToInt()),
            left + boxW - 8f * dp, cy + pGaugeText.textSize * 0.36f, pGaugeText
        )
    }

    /**
     * Dessine les particules d'une couche, **groupées par teinte et par taille**.
     *
     * Un bouquet compte un millier d'étoiles. Les dessiner une par une, c'est mille
     * appels de tracé par image, chacun avec sa mise en place d'anticrénelage, pour des
     * points de trois pixels : mesuré ailleurs dans ce fichier, c'est exactement le
     * genre de chose qui fait tomber une image à trente. `drawPoints` en dessine autant
     * qu'on veut d'un seul appel, à condition qu'ils partagent leur couleur et leur
     * épaisseur — on range donc les particules dans des seaux (une teinte, un palier
     * d'opacité, une classe de taille) et on vide chaque seau d'un trait. Un bouquet
     * ordinaire tient dans une dizaine de seaux.
     *
     * Les grosses particules — boules de feu, fumée — sortent du lot : elles sont peu
     * nombreuses et il leur faut un vrai disque, pas un point épais.
     */
    private fun drawSparks(canvas: Canvas, background: Boolean) {
        val fx = game.effects
        if (fx.aliveCount == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in bucketCount.indices) bucketCount[i] = 0

        for (sp in fx.sparks) {
            if (!sp.alive || sp.background != background) continue
            val px = sx(sp.x)
            val py = sy(sp.y)
            if (px < -40f || px > w + 40f || py < -40f || py > h + 40f) continue
            val r = sp.shownSize * camScale
            // Ce qui est gros se dessine rond ; ce qui est petit se dessine en points.
            if (r > BIG_SPARK_DP * dp) {
                pBigSpark.color = tinted(sp)
                canvas.drawCircle(px, py, r, pBigSpark)
                continue
            }
            val size = (r / (SPARK_STEP_DP * dp)).toInt().coerceIn(0, SPARK_SIZES - 1)
            val alpha = (sp.fade * (SPARK_ALPHAS - 1)).toInt().coerceIn(0, SPARK_ALPHAS - 1)
            val b = (sp.tint * SPARK_ALPHAS + alpha) * SPARK_SIZES + size
            var arr = bucketXY[b]
            if (arr == null) {
                arr = FloatArray(256)
                bucketXY[b] = arr
            }
            var n = bucketCount[b]
            if (n + 2 > arr.size) {
                arr = arr.copyOf(arr.size * 2)
                bucketXY[b] = arr
            }
            arr[n++] = px
            arr[n++] = py
            bucketCount[b] = n
        }

        for (b in bucketCount.indices) {
            val n = bucketCount[b]
            if (n == 0) continue
            val size = b % SPARK_SIZES
            val rest = b / SPARK_SIZES
            val alpha = rest % SPARK_ALPHAS
            val tint = rest / SPARK_ALPHAS
            pSpark.color = TrebuchetEffects.PALETTE[tint]
            pSpark.alpha = 40 + 215 * alpha / (SPARK_ALPHAS - 1)
            pSpark.strokeWidth = (size + 1) * SPARK_STEP_DP * dp
            canvas.drawPoints(bucketXY[b]!!, 0, n, pSpark)
        }
    }

    /** La couleur d'une particule, son extinction comprise. */
    private fun tinted(sp: Spark): Int {
        val c = TrebuchetEffects.PALETTE[sp.tint]
        val a = (255 * sp.fade).toInt().coerceIn(0, 255)
        return (c and 0x00FFFFFF) or (a shl 24)
    }

    /**
     * Trace une polyligne du monde, en n'envoyant à la toile que ce qui s'y voit.
     *
     * Une trajectoire de trébuchet fait quatre cents mètres et se retient à cinquante
     * points par seconde : au bout d'un tir, la trace et le fantôme du tir précédent
     * comptent chacun plusieurs centaines de points, dont la caméra n'en montre qu'une
     * poignée puisqu'elle suit le boulet. Tout envoyer à la toile revenait à faire
     * découper, à chaque image, des centaines de segments qui tombent à des kilomètres
     * de l'écran — et le coût grandissait au fil du tir, ce qui donnait exactement le
     * symptôme « ça rame de plus en plus ».
     *
     * Deux tris, donc : les segments hors champ sont abandonnés, et les points qui
     * retombent sur le même pixel que le précédent aussi.
     */
    private fun drawPolyline(canvas: Canvas, pts: FloatArray, count: Int, paint: Paint) {
        if (count < 4) return
        val w = width.toFloat()
        val h = height.toFloat()
        tmpPath.reset()
        var px = sx(pts[0])
        var py = sy(pts[1])
        var lastX = Float.NaN
        var lastY = Float.NaN
        var drew = false
        var i = 2
        while (i < count) {
            val cx = sx(pts[i])
            val cy = sy(pts[i + 1])
            i += 2
            // Deux points sur le même pixel ne dessinent rien de plus qu'un seul.
            if (abs(cx - px) < 1f && abs(cy - py) < 1f) continue
            val outside = (px < 0f && cx < 0f) || (px > w && cx > w) ||
                (py < 0f && cy < 0f) || (py > h && cy > h)
            if (!outside) {
                if (px != lastX || py != lastY) tmpPath.moveTo(px, py)
                tmpPath.lineTo(cx, cy)
                lastX = cx
                lastY = cy
                drew = true
            }
            px = cx
            py = cy
        }
        if (drew) canvas.drawPath(tmpPath, paint)
    }

    /**
     * Les fantômes des tirs précédents. Sans eux, corriger sa machine relève de la
     * superstition : avec, on voit de combien on a manqué — et, depuis qu'ils sont
     * dix, **dans quel sens on se trompe**.
     *
     * Le dernier tir est blanc et franc ; les précédents s'éteignent vers le gris à
     * mesure qu'ils vieillissent. C'est ce dégradé qui fait la lecture : trois traits
     * de plus en plus pâles qui se resserrent sur la cible disent qu'on chauffe, trois
     * qui s'écartent disent qu'on tourne le mauvais bouton, et le trait blanc dit
     * toujours où l'on en est.
     *
     * Ils se dessinent du plus vieux au plus récent, pour que le blanc passe par-dessus
     * le gris et non l'inverse.
     */
    private fun drawGhosts(canvas: Canvas) {
        val list = game.ghosts
        if (list.isEmpty()) return
        val last = list.size - 1
        for (i in last downTo 0) {
            // Zéro pour le tir qu'on vient de faire, un pour le plus ancien.
            val age = if (last == 0) 0f else i / last.toFloat()
            // Du blanc franc au gris bleuté, et de plus en plus fin : le vieux tir doit
            // rester lisible sans jamais disputer la vedette au dernier.
            val v = (255 - 90f * age).toInt()
            val b = (255 - 70f * age).toInt()
            pGhost.color = Color.argb((170 - 110f * age).toInt(), v, v, b)
            pGhost.strokeWidth = (2.2f - 0.9f * age) * dp
            drawPolyline(canvas, list[i], list[i].size, pGhost)
        }
    }

    /**
     * Le cône du départ : la trajectoire de la machine fantôme, épaissie d'autant
     * qu'elle s'éloigne, et éteinte au bout de cinquante mètres.
     *
     * L'ouverture n'est pas une marge d'erreur calculée, c'est une promesse tenue :
     * le trait central est juste, la nappe autour dit « à peu près par là », et le
     * fait qu'elle s'ouvre dit « plus loin, je ne sais plus ». Un jeu où l'on ne vise
     * pas ne doit pas afficher une ligne de mire.
     */
    private fun drawStartCone(canvas: Canvas) {
        if (game.phase != TrebuchetGame.Phase.BUILD) return
        val t = previewPath
        val n = t.size / 2
        if (n < 4) return

        // Longueur parcourue le long de la trace : c'est elle qui ouvre le cône.
        if (previewArc.size < n) previewArc = FloatArray(n)
        previewArc[0] = 0f
        for (i in 1 until n) {
            previewArc[i] = previewArc[i - 1] +
                hypot(t[i * 2] - t[(i - 1) * 2], t[i * 2 + 1] - t[(i - 1) * 2 + 1])
        }

        // Un bord à l'aller, l'autre au retour : la nappe se referme sur elle-même.
        tmpPath.reset()
        for (pass in 0..1) {
            val side = if (pass == 0) 1f else -1f
            var i = if (pass == 0) 0 else n - 1
            while (i in 0 until n) {
                // La normale se prend sur le segment voisin ; au dernier point, sur le
                // précédent, faute de suivant.
                val a = if (i < n - 1) i else i - 1
                val dx = t[(a + 1) * 2] - t[a * 2]
                val dy = t[(a + 1) * 2 + 1] - t[a * 2 + 1]
                val len = hypot(dx, dy).coerceAtLeast(0.0001f)
                val w = previewArc[i] * PREVIEW_SPREAD * side
                val px = sx(t[i * 2] - dy / len * w)
                val py = sy(t[i * 2 + 1] + dx / len * w)
                if (pass == 0 && i == 0) tmpPath.moveTo(px, py) else tmpPath.lineTo(px, py)
                i += if (pass == 0) 1 else -1
            }
        }
        tmpPath.close()

        pCone.shader = LinearGradient(
            sx(t[0]), sy(t[1]), sx(t[(n - 1) * 2]), sy(t[(n - 1) * 2 + 1]),
            Color.argb(70, 255, 209, 102), Color.argb(0, 255, 209, 102), Shader.TileMode.CLAMP
        )
        canvas.drawPath(tmpPath, pCone)

        // Le trait central, en pointillés : c'est lui qui est juste.
        tmpPath.reset()
        tmpPath.moveTo(sx(t[0]), sy(t[1]))
        for (i in 1 until n) tmpPath.lineTo(sx(t[i * 2]), sy(t[i * 2 + 1]))
        canvas.drawPath(tmpPath, pConeLine)
    }

    /**
     * Les pastilles de saisie. Sans elles, rien ne dirait que la machine se touche :
     * une pièce qui se règle doit se voir avant qu'on la prenne en main.
     */
    private fun drawGrabSpots(canvas: Canvas) {
        if (game.phase != TrebuchetGame.Phase.BUILD || selected != Part.NONE) return
        val r = 4f * dp
        // Au milieu du bras long, loin de l'axe et de la pointe : les trois repères
        // de la poutre ne doivent pas se confondre.
        game.tipWorld(tip)
        canvas.drawCircle(
            sx((tip[0] + game.pivotX) / 2f), sy((tip[1] + game.pivotY) / 2f), r, pSpot
        )
        // Le repère du pied se pose **dans** le bâti, à mi-jambes, et non sur le
        // pivot : c'est là qu'il se désigne désormais, et un repère qui ne montre pas
        // la bonne cible est pire que pas de repère du tout.
        canvas.drawCircle(sx(game.pivotX), sy(game.pivotY * POST_GRIP_HEIGHT), r, pSpot)
        canvas.drawCircle(sx(game.counterweight.x), sy(game.counterweight.y), r, pSpot)
        game.pinWorld(pin)
        canvas.drawCircle(sx(pin[0]), sy(pin[1]), r, pSpot)
        canvas.drawCircle(sx(game.ball.x), sy(game.ball.y), r, pSpot)
    }

    /**
     * La pièce tenue en main : son liseré, ses poignées, ses valeurs. La machine
     * porte les chiffres, le bandeau du bas porte l'explication.
     */
    private fun drawSelection(canvas: Canvas) {
        if (selected == Part.NONE) return
        pGaugeText.textSize = 13f * dp
        pValue.textSize = 12f * dp
        drawGuide(canvas)
        val cfg = game.config
        when (selected) {
            Part.BEAM -> {
                drawOutline(canvas, game.beam)
                game.tipWorld(tip)
                drawGrip(canvas, tip[0], tip[1])
                drawValue(canvas, tip[0], tip[1], "%.1f m".format(cfg.beamLength))
                drawGrip(canvas, game.pivotX, game.pivotY)
                drawValue(canvas, game.pivotX, game.pivotY, "%.1f:1".format(cfg.leverRatio))
            }
            Part.POST -> {
                // Le montant, du sol à l'axe : c'est lui qu'on étire.
                canvas.drawLine(
                    sx(game.pivotX), sy(0f), sx(game.pivotX), sy(game.pivotY), pSelect
                )
                // Deux poignées, parce qu'il y a deux endroits où le doigt marche : le
                // bâti, où l'on a pris le pied, et le pivot, qui reste le geste naturel
                // pour dire « monte l'axe jusque-là ».
                drawGrip(canvas, game.pivotX, game.pivotY * POST_GRIP_HEIGHT)
                drawGrip(canvas, game.pivotX, game.pivotY)
                drawValue(canvas, game.pivotX, game.pivotY, "%.1f m".format(cfg.pivotHeight))
            }
            Part.WEIGHT -> {
                drawOutline(canvas, game.counterweight)
                game.buttWorld(butt)
                val cw = game.counterweight
                canvas.drawLine(sx(butt[0]), sy(butt[1]), sx(cw.x), sy(cw.y), pSelect)
                drawGrip(canvas, cw.x, cw.y)
                drawValue(
                    canvas, (butt[0] + cw.x) / 2f, (butt[1] + cw.y) / 2f,
                    "%.1f m".format(cfg.hangLength)
                )
                val h = cfg.counterweightHalf
                drawGrip(canvas, cw.x + h, cw.y - h)
                drawValue(
                    canvas, cw.x + h, cw.y - h, "%d kg".format(cfg.counterweightMass.toInt())
                )
            }
            Part.PIN -> {
                game.pinWorld(pin)
                drawGrip(canvas, pin[0], pin[1])
                drawValue(canvas, pin[0], pin[1], "%d°".format(cfg.pinAngleDeg.toInt()))
            }
            Part.SLING -> {
                drawOutline(canvas, game.ball)
                drawGrip(canvas, game.ball.x, game.ball.y)
                game.tipWorld(tip)
                drawValue(
                    canvas, (tip[0] + game.ball.x) / 2f, game.ball.y,
                    "%.2f m".format(cfg.slingLength)
                )
            }
            Part.NONE -> {}
        }
    }

    /** Le liseré qui dit « c'est cette pièce-là que tu tiens ». */
    private fun drawOutline(canvas: Canvas, b: PhysBody) {
        for (i in b.parts.indices) {
            val part = b.parts[i]
            if (part.shape == Shape.CIRCLE) {
                b.partWorld(i, partPose)
                canvas.drawCircle(
                    sx(partPose[0]), sy(partPose[1]), part.radius * camScale + 2f * dp, pSelect
                )
                continue
            }
            b.partCorners(i, corners)
            tmpPath.reset()
            tmpPath.moveTo(sx(corners[0]), sy(corners[1]))
            for (k in 1 until 4) tmpPath.lineTo(sx(corners[k * 2]), sy(corners[k * 2 + 1]))
            tmpPath.close()
            canvas.drawPath(tmpPath, pSelect)
        }
    }

    /**
     * Le rail du réglage en cours : un pointillé entre ce qui ne bouge pas et ce que
     * le doigt tire. Il ne s'affiche que pendant le glissement — c'est le moment où
     * la question « qu'est-ce que je suis en train de changer ? » se pose.
     */
    private fun drawGuide(canvas: Canvas) {
        if (grip == Grip.NONE) return
        val cw = game.counterweight
        val h = game.config.counterweightHalf
        game.buttWorld(butt)
        game.tipWorld(tip)
        game.pinWorld(pin)
        val ax: Float
        val ay: Float
        val bx: Float
        val by: Float
        when (grip) {
            Grip.BEAM_LENGTH -> {
                ax = game.pivotX; ay = game.pivotY; bx = tip[0]; by = tip[1]
            }
            Grip.LEVER -> {
                ax = butt[0]; ay = butt[1]; bx = game.pivotX; by = game.pivotY
            }
            Grip.POST_HEIGHT -> {
                ax = game.pivotX; ay = 0f; bx = game.pivotX; by = game.pivotY
            }
            Grip.CW_MASS -> {
                ax = cw.x; ay = cw.y; bx = cw.x + h; by = cw.y - h
            }
            Grip.CW_HANG -> {
                ax = butt[0]; ay = butt[1]; bx = cw.x; by = cw.y
            }
            Grip.PIN_ANGLE -> {
                ax = tip[0]; ay = tip[1]; bx = pin[0]; by = pin[1]
            }
            Grip.SLING_LENGTH -> {
                ax = tip[0]; ay = tip[1]; bx = game.ball.x; by = game.ball.y
            }
            Grip.NONE -> return
        }
        // Un chemin et non un trait, pour un pinceau en pointillé : le canevas matériel
        // n'applique un [DashPathEffect] à un `drawLine` qu'à partir d'Android 9, et le
        // projet descend jusqu'à Android 8. Le même pointillé passé par un chemin est
        // dessiné partout — c'est d'ailleurs par là que passent déjà les fantômes et le
        // cône de départ, qui sont pointillés eux aussi.
        tmpPath.reset()
        tmpPath.moveTo(sx(ax), sy(ay))
        tmpPath.lineTo(sx(bx), sy(by))
        canvas.drawPath(tmpPath, pHandle)
    }

    /** Une poignée : un point qu'on peut tirer. */
    private fun drawGrip(canvas: Canvas, x: Float, y: Float) {
        val px = sx(x)
        val py = sy(y)
        canvas.drawCircle(px, py, 7f * dp, pGrip)
        canvas.drawCircle(px, py, 7f * dp, pGripEdge)
    }

    /** La valeur, posée juste au-dessus de ce qui la règle. */
    private fun drawValue(canvas: Canvas, x: Float, y: Float, text: String) {
        canvas.drawText(text, sx(x), sy(y) - 15f * dp, pValue)
    }

    /**
     * Quand le joueur s'est éloigné au point de perdre la machine de vue, on lui
     * rappelle comment revenir : rien, à l'écran, ne laisse deviner le double-appui.
     */
    private fun drawRecenterHint(canvas: Canvas) {
        if (!manualCam || !cameraFree) return
        val px = sx(game.pivotX)
        if (px > 0f && px < width) return
        pHint.textSize = 12f * dp
        canvas.drawText(
            context.getString(R.string.trebuchet_recenter), width / 2f, 24f * dp, pHint
        )
    }
}
