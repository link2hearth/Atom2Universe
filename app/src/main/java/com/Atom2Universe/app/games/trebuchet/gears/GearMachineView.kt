package com.Atom2Universe.app.games.trebuchet.gears

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.trebuchet.LandScene
import com.Atom2Universe.app.games.trebuchet.SkyBackdrop
import com.Atom2Universe.app.games.trebuchet.SparkScene
import com.Atom2Universe.app.games.trebuchet.TrebuchetSfx
import com.Atom2Universe.app.games.trebuchet.SkyClock
import com.Atom2Universe.app.games.trebuchet.SkyState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class GearMachineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onGearMachineChanged()
        fun onGearSelectionChanged()

        /**
         * Une liaison a été refusée. Elle l'était jusqu'ici sans un mot : on touchait
         * la seconde roue, rien ne se passait, et rien ne disait pourquoi.
         */
        fun onGearLinkRejected(kind: GearLinkKind)
    }

    enum class TouchMode { NONE, MOVE, STRUCTURE_MOVE, SPIN, LAYER_DOWN, LAYER_UP }
    private enum class CameraView { BUILD, FULL }
    data class PendingLink(val firstId: Int, val kind: GearLinkKind, val inputDirection: Int)

    val game = GearMachineGame()
    var listener: Listener? = null
    var selectedId: Int? = null
        private set
    var placementTeeth: Int? = null
        private set
    var currentLayer = 0
        private set
    var pendingLink: PendingLink? = null
        private set
    var layoutConflictIds: Set<Int> = emptySet()
        private set
    /** Quand il est actif, le glissement d'une roue déplace tout son bâti assemblé. */
    var structureTool = false
        private set

    private val dp = resources.displayMetrics.density
    private val gearPath = Path()
    private val pBackground = Paint().apply { color = Color.rgb(10, 16, 36) }
    private val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * dp; color = Color.argb(90, 180, 220, 190)
    }
    private val pTickLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.argb(150, 200, 230, 205)
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val pHint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.argb(180, 220, 235, 255) }
    private val pFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(66, 83, 112); strokeWidth = 0.12f; style = Paint.Style.STROKE }
    private val pMesh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(130, 255, 209, 102); strokeWidth = 0.035f }
    private val pBelt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 126, 74); style = Paint.Style.STROKE; strokeWidth = 0.09f }
    private val pChain = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(205, 214, 224); style = Paint.Style.STROKE; strokeWidth = 0.075f }
    private val pGear = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(224, 234, 245); style = Paint.Style.STROKE; strokeWidth = 0.035f }
    private val pHub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 35, 58); style = Paint.Style.FILL }
    private val pSelect = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 209, 102); style = Paint.Style.STROKE; strokeWidth = 0.08f }
    private val pConflict = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 91, 91)
        style = Paint.Style.STROKE
        strokeWidth = 0.10f
    }
    private val pTexture = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.045f }
    private val motorArt = GearMotorArt(dp)
    private val cannonArt = GearCannonArt(dp)
    private val pAssembly = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(119, 239, 196); style = Paint.Style.STROKE; strokeWidth = 0.07f
        pathEffect = DashPathEffect(floatArrayOf(0.16f, 0.10f), 0f)
    }
    private val pTrail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 240, 205); style = Paint.Style.STROKE
        strokeWidth = 2.4f * dp; strokeCap = Paint.Cap.ROUND
    }
    private val pGhost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val trailPath = Path()
    private val spinRect = RectF()
    private val pTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val pSpin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 209, 102); style = Paint.Style.STROKE; strokeWidth = 0.07f
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11f * dp; textAlign = Paint.Align.CENTER }
    private val pUpperLayer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.045f
        pathEffect = DashPathEffect(floatArrayOf(0.16f, 0.11f), 0f)
    }
    private val pMagnet = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 209, 102)
        style = Paint.Style.STROKE
        strokeWidth = 0.055f
    }
    private val pLayerPanel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 16, 25, 50) }
    private val pLayerButton = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 143, 166, 200) }
    private val pPanelLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(143, 166, 200)
        textSize = 11f * dp
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val pPanelValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 209, 102)
        textSize = 13f * dp
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD
        )
    }
    private val pLayerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * dp
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val screenRect = RectF()

    /**
     * Les libellés du tableau de bord, **lus une fois**.
     *
     * `getString` n'est pas une lecture de champ : il traverse la table des ressources
     * pour retrouver la chaîne, puis la met en forme. Ces six appels-là étaient dans
     * `onDraw`, donc rejoués cent vingt fois par seconde pour afficher les trois mêmes
     * mots. Mesuré à la tablette, ils pesaient sept pour cent du fil principal.
     *
     * Les libellés fixes deviennent des champs. Les trois valeurs changent, elles, mais
     * pas à chaque image : le régime est un entier, l'énergie s'arrondit au dixième et
     * la portée au mètre. On garde donc la dernière chaîne fabriquée et le nombre qui
     * l'a produite, et on ne refait le travail que quand ce nombre a bougé. La mise en
     * forme passe par la locale des ressources, exactement comme le faisait
     * `getString(id, args)`.
     */
    private val fmtLocale = resources.configuration.locales[0]
    private val labelSpeed = resources.getString(R.string.trebuchet_gear_panel_speed)
    private val labelEnergy = resources.getString(R.string.trebuchet_gear_panel_energy)
    private val labelRange = resources.getString(R.string.trebuchet_gear_panel_range)
    private val fmtRpm = resources.getString(R.string.trebuchet_gear_panel_rpm)
    private val fmtKj = resources.getString(R.string.trebuchet_gear_panel_kj)
    private val fmtMetres = resources.getString(R.string.trebuchet_gear_panel_m)
    private val fmtCharging = resources.getString(R.string.trebuchet_gear_charging)
    private val labelToolPart = resources.getString(R.string.trebuchet_gear_tool_part)
    private val labelToolFrame = resources.getString(R.string.trebuchet_gear_tool_frame)
    private var shownRpm = Int.MIN_VALUE
    private var textRpm = ""
    private var shownEnergy = Float.NaN
    private var textEnergy = ""
    private var shownRange = Float.NaN
    private var textRange = ""
    private var shownCharge = Int.MIN_VALUE
    private var textCharge = ""
    /**
     * Le décor : **exactement le même objet que dans la vue du trébuchet**.
     *
     * L'atelier en avait sa propre copie, recopiée puis oubliée — d'où une pleine lune
     * noire qui a survécu ici pendant que le champ de tir était corrigé. Voir
     * [SkyBackdrop] pour ce qui est partagé et ce qui ne l'est pas.
     *
     * Il voit plus de ciel que le champ de tir parce que sa dalle est plus bas : c'est
     * le seul réglage qui reste propre à l'atelier.
     */
    private val backdrop = SkyBackdrop(context, skyBand = WORKSHOP_SKY_BAND)

    /**
     * Le paysage : collines, batiments, verdure, habitants, feux.
     *
     * Le meme objet qu'au champ de tir, pour la meme raison que le ciel — voir
     * [LandScene]. La dalle plate et sa regle graduee ont disparu avec : le relief porte
     * maintenant ses propres bornes, et elles suivent la pente.
     */
    private val land = LandScene(context)

    /** Les particules : fumee, gravats, feu d'artifice. Voir [SparkScene]. */
    private val sparks = SparkScene(context)

    /**
     * L'ambiance sonore, la meme qu'au champ de tir.
     *
     * L'atelier n'en avait **aucune** : ni choc, ni cri, ni feu d'artifice. Ce n'etait
     * pas genant tant qu'on y mesurait des portees sur une dalle nue ; depuis qu'on y
     * demolit un village, le silence est ce qui manque le plus.
     */
    private val sfx = TrebuchetSfx()

    /** Nombre de pierres brisees a la derniere image : sert a sonner les chocs. */
    private var lastBroken = 0

    /** Quand la derniere pierre a sonne : un mur qui tombe ne doit pas mitrailler. */
    private var lastRubbleAt = 0L

    var soundEnabled: Boolean
        get() = sfx.enabled
        set(value) { sfx.enabled = value }
    private val sky get() = backdrop.sky
    private val skyClock get() = backdrop.clock
    private val ambientClock get() = backdrop.ambientClock

    private var camX = 0f
    private var camY = 3f
    private var camScale = 60f
    private var running = false
    private var lastFrameNanos = 0L

    /**
     * Le calque des fantômes, et de quoi savoir s'il est encore valable.
     *
     * `ghostReady` dit qu'il contient bien l'image des fantômes ; les trois `ghostCam*`
     * et `ghostStamp` disent pour quelle vue et quelle liste de tirs. Les `prevCam*`
     * servent à repérer une caméra **en train** de bouger, auquel cas refaire le calque
     * serait du travail perdu : voir [drawGhosts].
     */
    private var ghostLayer: Bitmap? = null
    private var ghostLayerCanvas: Canvas? = null
    private var ghostReady = false
    private var ghostCamX = Float.NaN
    private var ghostCamY = Float.NaN
    private var ghostCamScale = Float.NaN
    private var ghostStamp = -1
    private var prevCamX = Float.NaN
    private var prevCamY = Float.NaN
    private var prevCamScale = Float.NaN
    private var cameraMoving = true
    private var accumulator = 0f
    private var touchMode = TouchMode.NONE
    private var touchGearId: Int? = null
    private var lastPointerAngle = 0f
    private var gestureStartMillis = 0L
    private var lastSpinMillis = 0L
    private var gestureAngle = 0f
    private var pointerDownRawX = 0f
    private var pointerDownRawY = 0f
    private var lastGestureRawX = 0f
    private var lastGestureRawY = 0f
    private var pointerTravel = 0f
    private var manipulationStarted = false
    private var lastUiNotifyMillis = 0L
    private var magneticTargetId: Int? = null
    private var magnetFirstId: Int? = null
    private var magnetSecondId: Int? = null
    private var magnetPulseStarted = 0L
    private var magnetFeedbackUntil = 0L
    private var cameraView = CameraView.BUILD

    /**
     * Le joueur tient le cadrage. Tant qu'il le tient, la camera ne bouge plus d'elle
     * meme -- et elle ne le reprend qu'au changement de phase, ou sur un double-appui.
     */
    private var manualCam = false
    private var camPhase = GearMachineGame.Phase.BUILD
    private var lastTapAt = 0L
    private var gestureLocked = false

    /** Le doigt s'est pose dans le vide : s'il ne glisse pas, il deselectionne. */
    private var tappedVoid = false
    private var pinching = false
    private var pinchStartDistance = 0f
    private var pinchStartScale = 0f
    private var pinchLastFocusX = 0f
    private var panLastX = 0f
    private var panLastY = 0f
    private var sceneDragging = false
    private var timePressAt = 0L
    private var timePressX = 0f
    private var timeFingerX = 0f
    private var timeCandidate = false
    private var timeMode = false
    private var timeRate = 0f

    fun toggleStructureTool(): Boolean {
        structureTool = !structureTool
        invalidate()
        return structureTool
    }

    fun resume() {
        // **Une seule fois.** `start()` ouvre un pilote MIDI et une portee de coroutines ;
        // les rouvrir sur une vue deja en marche laisserait les premiers derriere.
        if (running) return
        sfx.start()
        // Les fusees et leurs bouquets sonnent d'eux-memes : les effets previennent.
        game.effects.onRocketLaunch = { _, _ -> sfx.fireworkLaunch() }
        game.effects.onBurst = { _, _ -> sfx.fireworkBurst() }
        running = true
        lastFrameNanos = 0L
        postInvalidateOnAnimation()
    }

    fun pause() {
        running = false
        sfx.stop()
        // Vingt mégaoctets n'ont rien à faire en mémoire pendant qu'on est ailleurs. Au
        // retour, la caméra n'aura pas bougé et le calque se refera en une image.
        releaseGhostLayer()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Le decor tient l'image de la Lune et la carte lunaire : ce sont les seuls gros
        // tableaux de la vue, et rien d'autre ne les libererait.
        running = false
        backdrop.release()
        releaseGhostLayer()
    }

    fun loadConfig(config: GearMachineConfig) {
        game.loadConfig(config)
        selectedId = null
        placementTeeth = null
        pendingLink = null
        layoutConflictIds = emptySet()
        fitCamera()
        listener?.onGearSelectionChanged()
        invalidate()
    }

    fun snapshot(): GearMachineConfig = game.config.deepCopy()

    fun armPlacement(teeth: Int) {
        placementTeeth = teeth
        selectedId = null
        layoutConflictIds = emptySet()
        listener?.onGearSelectionChanged()
        invalidate()
    }


    fun cancelPlacement() {
        placementTeeth = null
        invalidate()
    }

    fun armLink(kind: GearLinkKind, inputDirection: Int = 1): Boolean {
        val first = selectedId ?: return false
        pendingLink = PendingLink(first, kind, if (inputDirection < 0) -1 else 1)
        placementTeeth = null
        listener?.onGearSelectionChanged()
        invalidate()
        return true
    }

    fun cancelLink() {
        pendingLink = null
        listener?.onGearSelectionChanged()
        invalidate()
    }

    fun removeSelectedLinks(): Int {
        val id = selectedId ?: return 0
        val removed = game.removeLinksFor(id)
        if (removed > 0) {
            pendingLink = null
            listener?.onGearMachineChanged()
            listener?.onGearSelectionChanged()
            invalidate()
        }
        return removed
    }

    fun deleteSelected(): Boolean {
        val id = selectedId ?: return false
        val removed = game.deleteGear(id)
        if (removed) {
            layoutConflictIds = emptySet()
            selectedId = null
            listener?.onGearMachineChanged()
            listener?.onGearSelectionChanged()
            invalidate()
        }
        return removed
    }

    fun setSelectedTeeth(teeth: Int): Int? {
        val id = selectedId ?: return null
        val result = game.resizeGearAndReflow(id, teeth) ?: return null
        layoutConflictIds = result.conflicts
        if (result.success) {
            listener?.onGearMachineChanged()
        } else {
            listener?.onGearSelectionChanged()
        }
        invalidate()
        return result.teeth
    }

    fun duplicateSelected(): Int? {
        val source = selectedId ?: return null
        val id = game.duplicateGear(source) ?: return null
        selectedId = id
        currentLayer = game.config.wheels.firstOrNull { it.id == id }?.layer ?: currentLayer
        pendingLink = null
        layoutConflictIds = emptySet()
        fitCamera()
        listener?.onGearMachineChanged()
        listener?.onGearSelectionChanged()
        invalidate()
        return id
    }

    fun setSelectedLayer(value: Int): Int? {
        val id = selectedId ?: return null
        val layer = game.setLayer(id, value) ?: return null
        currentLayer = layer
        layoutConflictIds = emptySet()
        listener?.onGearMachineChanged()
        listener?.onGearSelectionChanged()
        invalidate()
        return layer
    }

    fun changeSelectedMaterial(delta: Int): GearWheelMaterial? {
        val id = selectedId ?: return null
        val material = game.changeMaterial(id, delta) ?: return null
        layoutConflictIds = emptySet()
        listener?.onGearMachineChanged()
        invalidate()
        return material
    }

    fun setProjectileMass(mass: Float): Float {
        val applied = game.setProjectileMass(mass)
        listener?.onGearMachineChanged()
        listener?.onGearSelectionChanged()
        invalidate()
        return applied
    }

    /** Regle l'elevation du tir de la roue selectionnee, si c'est un volant. */
    fun setSelectedLaunchAngle(degrees: Float): Float? {
        val id = selectedId ?: return null
        val angle = game.setLaunchAngle(id, degrees) ?: return null
        listener?.onGearMachineChanged()
        invalidate()
        return angle
    }

    fun setSelectedReservoirVolume(cubicMeters: Float): Float? {
        val id = selectedId ?: return null
        val volume = game.setReservoirVolume(id, cubicMeters) ?: return null
        listener?.onGearMachineChanged()
        invalidate()
        return volume
    }

    fun launchProjectile(): Boolean {
        val launched = game.launchProjectile()
        if (launched) {
            selectedId = null
            listener?.onGearMachineChanged()
            listener?.onGearSelectionChanged()
        }
        invalidate()
        return launched
    }

    fun clearGhosts() {
        game.clearGhosts()
        invalidate()
    }

    /** Termine le tir en cours sans attendre que le boulet se calme. */
    fun stopShot() {
        game.stopShot()
        listener?.onGearMachineChanged()
        listener?.onGearSelectionChanged()
        invalidate()
    }

    /** Rebande : le boulet posé s'efface, son fantôme reste. */
    fun newShot() {
        game.newShot()
        listener?.onGearMachineChanged()
        listener?.onGearSelectionChanged()
        invalidate()
    }

    fun setCurrentLayer(delta: Int): Int {
        currentLayer = (currentLayer + delta).coerceIn(GearMachineRules.MIN_LAYER, GearMachineRules.MAX_LAYER)
        if (selectedWheel()?.layer != currentLayer) selectedId = null
        if (pendingLink?.kind != GearLinkKind.SHAFT_CLUTCH) pendingLink = null
        listener?.onGearSelectionChanged()
        invalidate()
        return currentLayer
    }

    /**
     * Lance ou interrompt une charge : les moteurs travaillent pendant la duree
     * reglee, et la machine monte en regime sous les yeux.
     */
    /**
     * Dresse un nouveau site : relief et batiments, tires d'une graine.
     *
     * Rien de la machine ne bouge — c'est le paysage qu'on change, pas l'atelier. Le
     * cadrage recule pour montrer ce qui vient d'apparaitre, sinon le joueur ne saurait
     * pas que quelque chose s'est passe a cinq cents metres de lui.
     */
    fun loadSite(seed: Long?) {
        game.loadSite(seed)
        cameraView = CameraView.FULL
        manualCam = false
        invalidate()
    }

    fun toggleCharge(): Boolean {
        if (game.charging) {
            game.cancelCharge()
            invalidate()
            return false
        }
        val started = game.startCharge()
        invalidate()
        return started
    }

    /**
     * Serre ou desserre le frein de la roue tenue.
     *
     * Freiner est une action **tenue**, pas un cran : on garde le doigt dessus tant
     * qu'on veut ralentir, exactement comme une main sur une jante.
     */
    fun setBrake(on: Boolean) {
        game.setBrake(if (on) selectedId else null)
        invalidate()
    }

    /** Arrete net la roue tenue et tout ce qui lui est accroche. */
    fun stopSelected(): Boolean {
        val id = selectedId ?: return false
        game.cancelCharge()
        game.stopConnected(id)
        listener?.onGearMachineChanged()
        invalidate()
        return true
    }

    /** Fait defiler le genre du moteur : il n'y en a qu'un, on le remplace sur place. */
    fun cycleSelectedMotor(): GearMotorKind? {
        val id = selectedId ?: return null
        val kind = game.cycleMotorKind(id) ?: return null
        listener?.onGearMachineChanged()
        listener?.onGearSelectionChanged()
        invalidate()
        return kind
    }

    /** Bascule le lanceur tenu entre volant et canon, sur place — meme geste que le moteur. */
    fun cycleSelectedLauncherKind(): GearWheelKind? {
        val id = selectedId ?: return null
        val kind = game.cycleLauncherKind(id) ?: return null
        listener?.onGearMachineChanged()
        listener?.onGearSelectionChanged()
        invalidate()
        return kind
    }

    fun setSelectedMotorSpan(span: Float): Float? {
        val id = selectedId ?: return null
        val value = game.setMotorSpan(id, span)
        listener?.onGearMachineChanged()
        invalidate()
        return value
    }

    fun setSelectedMotorUnits(signedUnits: Int): Int? {
        val id = selectedId ?: return null
        val units = game.setMotorUnits(id, signedUnits)
        listener?.onGearMachineChanged()
        invalidate()
        return units
    }

    fun setChargeSeconds(seconds: Float): Float {
        val value = game.setChargeSeconds(seconds)
        listener?.onGearMachineChanged()
        invalidate()
        return value
    }

    /** La vitesse de rotation de la roue tenue, en rad/s et signee. */
    fun selectedOmega(): Float {
        val id = selectedId ?: return 0f
        return game.gears.firstOrNull { it.wheel.id == id }?.body?.omega ?: 0f
    }

    fun stopAll() {
        game.stopAll()
        invalidate()
    }

    fun selectedWheel(): GearWheelConfig? =
        selectedId?.let { id -> game.config.wheels.firstOrNull { it.id == id } }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitCamera()
    }

    /**
     * Recadre sur la machine.
     *
     * Il ne se déclenche plus tout seul après chaque geste. Il le faisait, et c'était
     * insupportable : on zoomait sur une prise pour la régler, on déplaçait une roue
     * d'un centimètre, et la vue reculait d'un coup sur toute la machine. Un cran de
     * denture au bouton « + » recadrait onze fois par seconde. Il ne reste donc que là
     * où la pièce apparaît **ailleurs** que sous les yeux — la copie, une machine
     * chargée — et sur le double-appui, qui est la demande explicite de recadrer.
     */
    private fun fitCamera() {
        if (width <= 0 || height <= 0) return
        val target = buildTarget()
        camX = target[0]
        camScale = target[1]
        camY = groundCamY(camScale)
        clampCamera()
        cameraView = CameraView.BUILD
        manualCam = false
    }

    /** Le sol pose en bas de l'image, quelle que soit l'echelle. */
    private fun groundCamY(scale: Float) = (height / 2f - GROUND_INSET_DP * dp) / scale

    /**
     * Le cadrage de reglage : sol en bas, machine a gauche, et une grande reserve de
     * terrain a droite pour etendre une transmission.
     */
    private fun buildTarget(): FloatArray {
        val bounds = game.bounds()
        val spanX = (bounds[1] - bounds[0] + 8f).coerceAtLeast(30f)
        val spanY = (bounds[3].coerceAtLeast(0f) + 4f).coerceAtLeast(14f)
        val scale = min(width / spanX, (height - 34f * dp) / spanY).coerceAtLeast(0.01f)
        val center = (bounds[0] + bounds[1]) / 2f
        return floatArrayOf(center + spanX * 0.16f, scale)
    }

    /**
     * Le cadrage du vol.
     *
     * **Le sol reste pose en bas, et c'est la vue qui recule.** Un tir se lit par
     * rapport au sol -- c'est meme la seule chose qu'on regarde -- et une camera qui
     * centrerait le boulet en hauteur ne montrerait bientot qu'un caillou dans un ciel
     * vide, sans rien pour dire s'il monte ou s'il descend. La fenetre s'ouvre donc a
     * mesure que le boulet monte, et se referme quand il redescend.
     *
     * On vise **devant** le boulet, d'autant plus loin qu'il va vite : le cadrage
     * anticipe au lieu de courir apres.
     */
    private fun flightTarget(shot: GearMachineGame.ProjectileState): FloatArray {
        val bounds = game.bounds()
        val spanX = (bounds[1] - bounds[0] + 8f).coerceAtLeast(FLIGHT_MIN_WIDTH)
        val spanY = maxOf(bounds[3] + 4f, shot.body.y + FLIGHT_TOP_MARGIN).coerceAtLeast(14f)
        val scale = min(
            width / spanX,
            (height - GROUND_INSET_DP * dp) / spanY
        ).coerceAtLeast(0.01f)
        return floatArrayOf(shot.body.x + shot.body.vx * 0.4f, scale)
    }

    /**
     * Le cadrage du resultat, en deux plans selon ce qui vient de se passer.
     *
     * **Touche mais pas rase : la camera reste sur les ruines.** C'est elle qu'on
     * regarde, et un recul au moment ou le mur s'ecroule enleve au joueur la seule
     * chose qu'il attendait. L'atelier reculait systematiquement pour montrer la
     * courbe du tir — utile quand on mesurait des portees sur une dalle nue, absurde
     * devant un village qui tombe.
     *
     * **Rien touche, ou site rase : on recule.** D'un cote il n'y a rien a regarder de
     * pres, de l'autre c'est le plan large qu'il faut pour le feu d'artifice, qui part
     * entre la machine et les ruines. Meme regle qu'au trebuchet.
     */
    private fun resultTarget(shot: GearMachineGame.ProjectileState): FloatArray {
        val site = game.targets
        if (game.level != null && site.tookDamage && !site.cleared) {
            val left = site.left - RESULT_HIT_MARGIN
            val right = site.right + RESULT_HIT_MARGIN
            val spanX = (right - left).coerceAtLeast(RESULT_HIT_MIN_WIDTH)
            val spanY = (site.baseHeight + RESULT_HIT_MARGIN)
                .coerceAtLeast(RESULT_HIT_MIN_HEIGHT)
            val echelle = min(
                width / spanX, (height - GROUND_INSET_DP * dp) / spanY
            ).coerceAtLeast(0.01f)
            return floatArrayOf((left + right) / 2f, echelle)
        }
        val bounds = game.bounds()
        val left = bounds[0] - 6f
        val right = maxOf(shot.body.x, shot.targetX) + RESULT_MARGIN
        val spanX = (right - left).coerceAtLeast(30f)
        val spanY = (maxOf(bounds[3], shot.peakY) + 6f).coerceAtLeast(14f)
        val scale = min(
            width / spanX, (height - GROUND_INSET_DP * dp) / spanY
        ).coerceAtLeast(0.01f)
        return floatArrayOf((left + right) / 2f, scale)
    }

    /**
     * Le plan le plus large : **de la machine jusqu'au site**.
     *
     * Il s'arretait au mannequin, a soixante metres, ce qui suffisait tant que l'atelier
     * tirait sur une dalle nue. Depuis qu'il y a un village a quelques centaines de
     * metres, un double-appui qui montrerait un champ vide serait la seule chose que le
     * joueur ne peut pas faire : aller voir ce qu'il est en train de detruire. C'est la
     * regle du trebuchet, ou la butee avant englobe la cible pour la meme raison.
     *
     * La hauteur suit le relief : un village perche sur une colline de dix metres sortait
     * du cadre par le haut.
     */
    private fun fullTarget(): FloatArray {
        val bounds = game.bounds()
        val site = game.targets
        val loin = if (site.pieces.isEmpty()) objectiveX() else site.right + 40f
        val left = minOf(bounds[0], objectiveX())
        val right = maxOf(bounds[1], loin)
        val spanX = (right - left + 12f).coerceAtLeast(70f)
        val haut = maxOf(bounds[3], game.terrain.highest + 12f)
        val spanY = (haut.coerceAtLeast(2.2f) + 4f).coerceAtLeast(14f)
        val scale = min(
            width / spanX, (height - 34f * dp) / spanY
        ).coerceAtLeast(0.01f)
        return floatArrayOf((left + right) / 2f, scale)
    }

    /**
     * Rattrape la cible de la phase, sans a-coups.
     *
     * Le joueur garde toujours la main : des qu'il pince ou fait defiler, la camera
     * lache prise et ne la reprend qu'au changement de phase -- on veut voir partir son
     * tir, puis voir son arc, sans avoir a toucher l'ecran -- ou sur un double-appui,
     * qui est la demande explicite de rendre le cadrage.
     */
    private fun updateCamera(dt: Float) {
        if (width <= 0 || height <= 0 || dt <= 0f) return
        if (game.phase != camPhase) {
            camPhase = game.phase
            manualCam = false
            cameraView = CameraView.BUILD
        }
        // Le cadrage ne s'attrape pas **en vol** : pendant le tir la camera suit son
        // boulet, quoi qu'on ait choisi avant. C'est la regle du trebuchet, et elle
        // existe parce qu'un tir qu'on rate parce qu'on regardait ailleurs est un tir a
        // refaire. Hors vol, le choix du joueur prime.
        if (manualCam && game.phase != GearMachineGame.Phase.FLIGHT) {
            clampCamera()
            return
        }
        val shot = game.projectile
        val follow: Float
        val target = when {
            cameraView == CameraView.FULL -> { follow = 3f; fullTarget() }
            game.phase == GearMachineGame.Phase.FLIGHT && shot != null -> {
                follow = 8f; flightTarget(shot)
            }
            game.phase == GearMachineGame.Phase.RESULT && shot != null -> {
                follow = 3f; resultTarget(shot)
            }
            else -> { follow = 4.5f; buildTarget() }
        }
        camX += (target[0] - camX) * (dt * follow).coerceIn(0f, 1f)
        camScale += (target[1] - camScale) * (dt * 2.5f).coerceIn(0f, 1f)
        camY = groundCamY(camScale)
        clampCamera()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val frameStart = SystemClock.uptimeMillis()
        // La caméra a-t-elle bougé depuis l'image précédente ? Le calque des fantômes en
        // dépend, et il faut le savoir **avant** de le dessiner.
        cameraMoving = camX != prevCamX || camY != prevCamY || camScale != prevCamScale
        prevCamX = camX; prevCamY = camY; prevCamScale = camScale
        // Ce que l'écran montre du ciel : le feu d'artifice s'y règle. Le cadrage pose
        // l'altitude zéro en bas de l'image, à l'encoche près, donc la hauteur de ciel
        // visible se lit d'un trait au bord supérieur. Elle se relit **à chaque image**
        // et non au moment de la victoire : la caméra recule entre les deux, et des
        // fusées réglées sur le cadrage d'avant éclateraient dans le bas de celui
        // d'après. Voir [GearMachineGame.skyTop].
        if (height > 0) game.skyTop = wy(0f)
        updateSimulation()
        drawWorkshopSky(canvas)

        canvas.save()
        canvas.translate(width / 2f - camX * camScale, height / 2f + camY * camScale)
        canvas.scale(camScale, -camScale)
        drawAutomaticFrame(canvas)
        drawTransmissions(canvas)
        for (mesh in game.meshes) {
            val a = game.gears.firstOrNull { it.wheel.id == mesh.firstId } ?: continue
            val b = game.gears.firstOrNull { it.wheel.id == mesh.secondId } ?: continue
            pMesh.color = layerColor(a.wheel.layer, pastel = a.wheel.layer < currentLayer)
            pMesh.alpha = layerAlpha(a.wheel.layer, 170, 80)
            canvas.drawLine(a.body.x, a.body.y, b.body.x, b.body.y, pMesh)
        }
        drawMotors(canvas)
        for (gear in game.gears.sortedBy { it.wheel.layer }) drawGear(canvas, gear)
        // Le bras se pose par-dessus la roue : c'est une piece rapportee sur la jante,
        // et le godet comme la fleche de sortie doivent rester lisibles.
        drawLaunchers(canvas)
        drawAssemblySelection(canvas)
        drawPendingLink(canvas)
        drawMagnetFeedback(canvas)
        game.projectile?.let { shot ->
            drawShotMark(canvas, shot)
            pGear.color = Color.rgb(245, 240, 222)
            pGear.alpha = 255
            canvas.drawCircle(shot.body.x, shot.body.y, shot.body.radius, pGear)
        }
        canvas.restore()

        drawGhosts(canvas)
        drawTrail(canvas)

        for (gear in game.gears) {
            val x = sx(gear.body.x)
            val y = sy(gear.body.y)
            pText.color = if (gear.wheel.id == selectedId) Color.rgb(255, 209, 102)
                else layerColor(gear.wheel.layer, pastel = gear.wheel.layer < currentLayer)
            pText.alpha = layerAlpha(gear.wheel.layer, 210, 75)
            val kind = when (gear.wheel.kind) {
                GearWheelKind.FLYWHEEL -> "V"
                GearWheelKind.PUMP -> "C"
                else -> "${gear.wheel.teeth}T"
            }
            canvas.drawText("L${gear.wheel.layer} · $kind", x, y + 4f * dp, pText)
        }
        pText.alpha = 255
        drawLayerSelector(canvas)
        drawStructureTool(canvas)
        // Les explosions sont devant tout : une gerbe derriere le mur qu'elle demolit
        // n'aurait aucun sens.
        sparks.draw(
            canvas, game.effects, false,
            width.toFloat(), height.toFloat(), camX, camY, camScale
        )
        drawLauncherPanel(canvas)
        drawTimeControl(canvas)
        drawChargeGauge(canvas)
        if (running) scheduleNextFrame(frameStart)
    }

    /**
     * L'atelier se contente de **soixante images par seconde**.
     *
     * L'écran de la tablette en affiche cent vingt, et `postInvalidateOnAnimation`
     * redemandait une image à chaque battement : le jeu dessinait donc à cent vingt,
     * sans jamais l'avoir décidé. Mesuré à la tablette, une image coûtait dix
     * millisecondes pour un budget de huit — le compositeur absorbait le dépassement en
     * empilant les images, si bien que rien ne sautait à l'œil, mais le processeur
     * tournait au maximum en permanence et la puce montait à quarante-huit degrés.
     *
     * La physique n'est pas concernée : elle avance par pas fixes d'un cent-vingtième de
     * seconde dans [updateSimulation], donc elle fait exactement le même travail qu'on
     * l'affiche soixante ou cent vingt fois par seconde. C'est bien le **dessin** qu'on
     * divise par deux, et lui seul.
     *
     * Le délai se mesure depuis le **début** de l'image et non depuis la fin du dessin :
     * une image commence sur un battement d'écran, donc viser douze millisecondes après
     * ce battement dépose l'invalidation avant celui des 16,7 ms, et l'image suivante
     * part dessus. En comptant depuis la fin du dessin, une image un peu longue ferait
     * manquer ce battement et la cadence tomberait à quarante.
     */
    private fun scheduleNextFrame(frameStart: Long) {
        val wait = FRAME_POST_MS - (SystemClock.uptimeMillis() - frameStart)
        if (wait <= 0L) postInvalidateOnAnimation() else postInvalidateDelayed(wait)
    }

    private fun updateSimulation() {
        if (!running) return
        val now = System.nanoTime()
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now
            return
        }
        val elapsed = ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
        lastFrameNanos = now
        val fixed = 1f / 120f
        var consumed = 0f
        if (game.charging) {
            // La charge est la **meme** simulation, jouee en accelere. On la decoupe
            // pour qu'elle tienne dans une seconde et demie d'ecran quelle que soit la
            // duree demandee : on voit le train monter en regime au lieu d'attendre
            // devant une image figee, et rien ne bloque une image entiere.
            val steps = ceil(game.chargeTotal / fixed / CHARGE_FRAMES).toInt()
                .coerceIn(CHARGE_MIN_STEPS, CHARGE_MAX_STEPS)
            consumed = game.advanceCharge(fixed, steps)
            // Le ciel suit le temps **machine** : charger deux minutes deplace le
            // Soleil de deux minutes. Ces secondes-la sont deja des secondes de monde,
            // donc elles passent par `skipSeconds` et non par `clockDt` — ce dernier les
            // aurait multipliees par soixante-douze, et une charge ordinaire aurait fait
            // basculer le ciel de deux heures et demie.
            //
            // Les nuages et les oiseaux, eux, restent a l'heure de l'ecran : ce sont des
            // habitants, pas des rouages.
            backdrop.advance(elapsed, 0f, workshopBreeze(), skipSeconds = consumed)
            accumulator = 0f
        } else {
            accumulator += elapsed
            var guard = 0
            while (accumulator >= fixed && guard++ < 8) {
                game.step(fixed)
                updateTimeControl(fixed)
                accumulator -= fixed
                consumed += fixed
            }
            // Le decor avance d'un coup du temps reellement simule : nuages et horloge
            // sont lineaires en dt, donc sommer les sous-pas donne le meme resultat que
            // les parcourir, pour un seul recalcul du ciel par image.
            backdrop.advance(consumed, if (timeMode) 0f else consumed, workshopBreeze())
        }
        land.updateDecor(
            elapsed, game.level, game.terrain, game.targets, game.projectile?.body?.x ?: 0f
        ) { sfx.villagerCry() }

        // Une pierre de plus a cede : ca s'entend. On compare le compteur d'une image a
        // l'autre plutot que d'ecouter chaque poussiere — un mur qui s'effondre en fait
        // des dizaines, et autant de sons superposes ne feraient qu'un bruit blanc.
        val casse = game.targets.pieceBroken
        if (casse > lastBroken) {
            // Un mur qui s'effondre casse une pierre par image pendant une seconde : sans
            // ce delai, on entendrait une rafale au lieu d'un ecroulement.
            val now = SystemClock.uptimeMillis()
            if (now - lastRubbleAt >= RUBBLE_GAP_MS) {
                sfx.rubble()
                lastRubbleAt = now
            }
        }
        lastBroken = casse
        // La camera vit a l'heure de l'ecran : lui donner le temps machine la ferait
        // sauter d'un bond a chaque image de charge.
        updateCamera(if (game.charging) elapsed else consumed)
        val nowMillis = now / 1_000_000L
        if (nowMillis - lastUiNotifyMillis >= 250L) {
            lastUiNotifyMillis = nowMillis
            listener?.onGearMachineChanged()
        }
    }

    /**
     * Le decor commun, puis ce qui n'appartient qu'a l'atelier : la dalle et sa regle.
     *
     * Le ciel, les astres, les nuages et les oiseaux sont peints par [backdrop], donc
     * ils sont **les memes** qu'au champ de tir sans qu'une ligne soit recopiee. Le sol,
     * lui, reste ici : une dalle plate graduee en metres n'a rien a voir avec un relief
     * seme de cibles, et pretendre les partager reviendrait a refaire le melange qu'on
     * vient de defaire.
     */
    private fun drawWorkshopSky(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        backdrop.draw(canvas, w, h, camX, camY, camScale)
        // Le feu d'artifice monte au fond du ciel, et le sol lui coupe les jambes quand
        // ses etoiles retombent : c'est ce qu'on voit dehors.
        sparks.draw(canvas, game.effects, true, w, h, camX, camY, camScale)
        land.firingLine = game.bounds()[0]
        land.draw(
            canvas, w, h, camX, camY, camScale,
            game.terrain, game.targets, sky.light, 0f, backdrop.ambientClock
        )
    }

    /** La brise de l'atelier : de quoi faire deriver les nuages, rien de plus. */
    private fun workshopBreeze() = sin(ambientClock * 0.08f) * 2.2f

    /** L'objectif de tir donne un second point de repère au double-appui large. */
    private fun objectiveX(): Float = if (game.projectile != null) {
        // En vol, le mannequin reste là où il était au départ : sinon déplacer le
        // lanceur pendant le tir ferait fuir la cible devant le boulet.
        game.projectile!!.targetX
    } else {
        game.targetX()
    }

    /**
     * Le mannequin de visee, **garde mais plus dessine**.
     *
     * Il servait de cible quand l'atelier tirait sur une dalle nue ; depuis qu'il y a de
     * vrais batiments a abattre, une barre rouge plantee au milieu du village ne dirait
     * plus rien. Le code reste en place parce qu'il resservira : la distance du
     * mannequin est exactement ce qui manque pour tirer une graine de terrain qui pose
     * le site **la ou le joueur vise**, au lieu de la ou le generateur l'a mis.
     *
     * @suppress inutilise pour l'instant, et c'est voulu.
     */
    @Suppress("unused")
    private fun drawWorkshopObjective(canvas: Canvas) {
        val x = objectiveX()
        val hit = game.projectile?.hitTarget ?: false
        pFrame.color = if (hit) Color.rgb(119, 239, 196) else Color.rgb(239, 103, 79)
        pFrame.alpha = 210
        pFrame.strokeWidth = if (hit) 0.14f else 0.10f
        canvas.drawLine(x, 0f, x, 2.1f, pFrame)
        canvas.drawCircle(x, GearMachineRules.TARGET_HEIGHT, GearMachineRules.TARGET_RADIUS, pFrame)
        canvas.drawLine(x - 0.27f, 1.45f, x + 0.27f, 1.99f, pFrame)
        canvas.drawLine(x - 0.27f, 1.99f, x + 0.27f, 1.45f, pFrame)
        pFrame.color = Color.rgb(66, 83, 112)
        pFrame.alpha = 255
    }

    /** La marque du boulet posé : une portée qu'on lit sans quitter le terrain des yeux. */
    private fun drawShotMark(canvas: Canvas, shot: GearMachineGame.ProjectileState) {
        if (!shot.landed) return
        pMesh.color = Color.rgb(255, 209, 102)
        pMesh.alpha = 190
        canvas.drawLine(shot.body.x, 0f, shot.body.x, 1.1f, pMesh)
        canvas.drawLine(shot.body.x - 0.35f, 0.02f, shot.body.x + 0.35f, 0.02f, pMesh)
        pMesh.alpha = 255
    }

    /**
     * Le double-appui : il bascule entre le plan large et le cadrage automatique, et
     * dans les deux cas il **rend la main a la camera**. C'est la sortie de secours
     * d'un cadrage manuel dont on ne veut plus.
     */
    private fun focusCamera(view: CameraView) {
        if (width <= 0 || height <= 0) return
        cameraView = view
        // **On ne saute pas d'un plan a l'autre.** On rend la main a [updateCamera], qui
        // rattrape la cible en douceur — une camera qui se teleporte fait perdre le fil
        // de ce qu'on regardait, et c'est encore plus vrai quand le plan large recule de
        // cinq cents metres. Meme regle qu'au trebuchet.
        manualCam = false
    }

    /** Même butées de terrain que le trébuchet, adaptées à l'atelier extensible. */
    private fun leftCameraLimit(): Float {
        val leftmostPiece = game.bounds()[0]
        return leftmostPiece - maxOf(8f, abs(leftmostPiece) * 0.40f)
    }

    /**
     * La butée droite suit le tir. Elle était fixée à mille mètres, ce qui suffisait
     * tant que rien n'allait plus loin — mais une bonne machine dépasse cette borne, et
     * la caméra restait alors plantée à regarder un boulet déjà sorti du cadre.
     */
    private fun rightCameraLimit(): Float {
        var right = maxOf(1_000f, objectiveX() + 40f)
        game.projectile?.let { right = maxOf(right, it.body.x + 40f) }
        return right
    }

    private fun clampCamera() {
        if (width <= 0 || camScale <= 0f) return
        val left = leftCameraLimit()
        val right = rightCameraLimit()
        val halfWidth = width / camScale / 2f
        camX = if (right - left <= halfWidth * 2f) {
            (left + right) * 0.5f
        } else {
            camX.coerceIn(left + halfWidth, right - halfWidth)
        }
        camY = groundCamY(camScale)
    }

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

    /** Retour détaillé pendant l'appui long, identique à celui du trébuchet. */
    private fun drawTimeControl(canvas: Canvas) {
        if (!timeMode) return
        val minutes = ((skyClock.instant % 86_400_000L) + 86_400_000L) % 86_400_000L / 60_000L
        val hour = minutes / 60
        val minute = minutes % 60
        val direction = when {
            timeRate > 0.02f -> "▶"
            timeRate < -0.02f -> "◀"
            else -> "■"
        }
        pHint.textSize = 20f * dp
        canvas.drawText("%02d:%02d  %s %.1f h/s".format(hour, minute, direction, abs(timeRate)), width / 2f, height * 0.18f, pHint)
    }

    /**
     * La jauge de charge : combien de temps machine il reste a jouer.
     *
     * Elle dit surtout que **rien n'est fige** : sans elle, une charge de dix minutes
     * ressemblerait a un ecran bloque pendant que les roues s'emballent.
     */
    /**
     * Ce que le lanceur vaut a l'instant, en haut a droite.
     *
     * Trois nombres, et pas un de plus : le regime qu'il tient, l'energie que le train
     * relie lui offre, et la portee que ca donnerait. Sans eux, on chargeait a
     * l'aveugle et on decouvrait le resultat une fois le boulet pose -- alors que tout
     * se decide **avant** le tir, en regardant monter ces trois-la.
     */
    private fun drawLauncherPanel(canvas: Canvas) {
        val wheel = game.config.launcher() ?: return
        val state = game.gears.firstOrNull { it.wheel.id == wheel.id } ?: return
        val rpm = (state.body.omega * 60f / (2f * PI.toFloat())).roundToInt()
        val energy = game.rotationalEnergy(game.connectedTo(wheel.id)) / 1000f
        val range = game.estimatedRange()

        val w = 148f * dp
        val rowH = 21f * dp
        val h = 14f * dp + rowH * 3f
        val left = width - w - 10f * dp
        val top = 10f * dp
        screenRect.set(left, top, left + w, top + h)
        canvas.drawRoundRect(screenRect, 9f * dp, 9f * dp, pLayerPanel)

        pPanelLabel.textAlign = Paint.Align.LEFT
        pPanelValue.textAlign = Paint.Align.RIGHT
        if (rpm != shownRpm) {
            shownRpm = rpm
            textRpm = String.format(fmtLocale, fmtRpm, rpm)
        }
        if (energy != shownEnergy) {
            shownEnergy = energy
            textEnergy = String.format(fmtLocale, fmtKj, energy)
        }
        if (range != shownRange) {
            shownRange = range
            textRange = String.format(fmtLocale, fmtMetres, range)
        }
        labelRow(canvas, 0, left, w, top, rowH, labelSpeed, textRpm)
        labelRow(canvas, 1, left, w, top, rowH, labelEnergy, textEnergy)
        labelRow(canvas, 2, left, w, top, rowH, labelRange, textRange)
    }

    /** Une ligne du tableau de bord : le libellé à gauche, la valeur à droite. */
    private fun labelRow(
        canvas: Canvas, index: Int, left: Float, w: Float, top: Float, rowH: Float,
        label: String, value: String
    ) {
        val baseline = top + 7f * dp + rowH * (index + 0.5f) + (pPanelValue.textSize * 0.36f)
        canvas.drawText(label, left + 10f * dp, baseline, pPanelLabel)
        canvas.drawText(value, left + w - 10f * dp, baseline, pPanelValue)
    }

    private fun drawChargeGauge(canvas: Canvas) {
        if (!game.charging) return
        val w = 200f * dp
        val h = 26f * dp
        val left = (width - w) / 2f
        val top = height - h - 18f * dp
        screenRect.set(left, top, left + w, top + h)
        canvas.drawRoundRect(screenRect, 8f * dp, 8f * dp, pLayerPanel)
        screenRect.set(left + 3f * dp, top + 3f * dp,
            left + 3f * dp + (w - 6f * dp) * game.chargeProgress, top + h - 3f * dp)
        canvas.drawRoundRect(screenRect, 6f * dp, 6f * dp, pLayerButton)
        val secondes = game.chargeRemaining.roundToInt()
        if (secondes != shownCharge) {
            shownCharge = secondes
            textCharge = String.format(fmtLocale, fmtCharging, secondes)
        }
        canvas.drawText(
            textCharge,
            width / 2f, top + h / 2f - (pLayerText.ascent() + pLayerText.descent()) / 2f, pLayerText
        )
    }

    private fun drawAutomaticFrame(canvas: Canvas) {
        for (gear in game.gears) {
            drawGearSupport(canvas, gear)
        }
        pFrame.alpha = 255
    }

    /** Un bâti en A proportionné à chaque roue, avec traverse et contreventements. */
    private fun drawGearSupport(canvas: Canvas, gear: GearMachineGame.GearState) {
        val x = gear.body.x; val y = gear.body.y
        val spread = (gear.wheel.outerRadius * 0.60f).coerceAtLeast(0.34f)
        val top = (y - gear.wheel.pitchRadius * 0.20f).coerceAtLeast(0.22f)
        val alpha = layerAlpha(gear.wheel.layer, 145, 45)
        pFrame.color = supportColor(gear.wheel.material)
        pFrame.alpha = alpha
        pFrame.strokeWidth = (0.065f + gear.wheel.outerRadius * 0.018f).coerceAtMost(0.14f)
        canvas.drawLine(x - spread, 0f, x, top, pFrame)
        canvas.drawLine(x + spread, 0f, x, top, pFrame)
        canvas.drawLine(x - spread, 0f, x + spread, 0f, pFrame)
        canvas.drawLine(x - spread * 0.72f, 0.04f, x + spread * 0.24f, top * 0.52f, pFrame)
        canvas.drawLine(x + spread * 0.72f, 0.04f, x - spread * 0.24f, top * 0.52f, pFrame)
        canvas.drawLine(x - spread * 0.48f, top * 0.56f, x + spread * 0.48f, top * 0.56f, pFrame)
        canvas.drawCircle(x, y, (0.10f + gear.wheel.outerRadius * 0.025f).coerceAtMost(0.20f), pFrame)
    }

    private fun drawTransmissions(canvas: Canvas) {
        for (transmission in game.transmissions) {
            val a = game.gears.firstOrNull { it.wheel.id == transmission.config.firstId } ?: continue
            val b = game.gears.firstOrNull { it.wheel.id == transmission.config.secondId } ?: continue
            val dx = b.body.x - a.body.x
            val dy = b.body.y - a.body.y
            val length = hypot(dx, dy).coerceAtLeast(1e-4f)
            val ox = -dy / length * 0.10f
            val oy = dx / length * 0.10f
            val layerAlpha = layerAlpha(a.wheel.layer, 150, 60)
            pBelt.alpha = layerAlpha
            pChain.alpha = layerAlpha
            when (transmission.config.kind) {
                GearLinkKind.BELT_OPEN -> {
                    canvas.drawLine(a.body.x + ox, a.body.y + oy, b.body.x + ox, b.body.y + oy, pBelt)
                    canvas.drawLine(a.body.x - ox, a.body.y - oy, b.body.x - ox, b.body.y - oy, pBelt)
                }
                GearLinkKind.BELT_CROSSED -> {
                    canvas.drawLine(a.body.x + ox, a.body.y + oy, b.body.x - ox, b.body.y - oy, pBelt)
                    canvas.drawLine(a.body.x - ox, a.body.y - oy, b.body.x + ox, b.body.y + oy, pBelt)
                }
                GearLinkKind.CHAIN_FREEWHEEL -> {
                    if (!(transmission.joint as com.Atom2Universe.app.games.physics.OneWayRotaryJoint).engaged) {
                        pChain.alpha = (layerAlpha * 0.55f).toInt()
                    }
                    canvas.drawLine(a.body.x + ox, a.body.y + oy, b.body.x + ox, b.body.y + oy, pChain)
                    canvas.drawLine(a.body.x - ox, a.body.y - oy, b.body.x - ox, b.body.y - oy, pChain)
                    val mx = (a.body.x + b.body.x) * 0.5f
                    val my = (a.body.y + b.body.y) * 0.5f
                    val direction = transmission.config.inputDirection.toFloat()
                    gearPath.reset()
                    gearPath.moveTo(mx + dx / length * 0.24f * direction, my + dy / length * 0.24f * direction)
                    gearPath.lineTo(mx - dx / length * 0.12f * direction + ox * 1.8f, my - dy / length * 0.12f * direction + oy * 1.8f)
                    gearPath.lineTo(mx - dx / length * 0.12f * direction - ox * 1.8f, my - dy / length * 0.12f * direction - oy * 1.8f)
                    gearPath.close()
                    pGear.color = pChain.color
                    pGear.alpha = pChain.alpha
                    canvas.drawPath(gearPath, pGear)
                }
                GearLinkKind.SHAFT_CLUTCH -> {
                    val radius = (minOf(a.wheel.pitchRadius, b.wheel.pitchRadius) * 0.32f)
                        .coerceAtLeast(0.13f)
                    pChain.alpha = layerAlpha
                    pChain.strokeWidth = 0.065f
                    canvas.drawCircle(a.body.x, a.body.y, radius, pChain)
                    canvas.drawCircle(a.body.x, a.body.y, radius * 0.58f, pChain)
                    pGear.color = Color.rgb(255, 209, 102)
                    pGear.alpha = layerAlpha
                    canvas.drawCircle(a.body.x, a.body.y, 0.065f, pGear)
                }
            }
        }
        pBelt.alpha = 255
        pChain.alpha = 255
        pChain.strokeWidth = 0.075f
    }

    /**
     * La gorge de lancement de chaque volant.
     *
     * Le boulet court **dans** la jante, dans une gorge annulaire, et il en sort
     * tangentiellement -- jamais dans l'axe du rayon. Un canon plante au milieu de la
     * roue ne lancerait rien du tout : au centre, la vitesse est nulle.
     *
     * Le volant qui tire montre son boulet et sa fleche ; les autres gardent une
     * gorge plus discrete, parce qu'ils pourraient tirer eux aussi.
     */
    private fun drawLaunchers(canvas: Canvas) {
        for (gear in game.gears) {
            when (gear.wheel.kind) {
                GearWheelKind.FLYWHEEL ->
                    drawLaunchTrack(canvas, gear, gear.wheel.id == game.config.launcherWheelId)
                GearWheelKind.PUMP -> {
                    val relativeLayer = gear.wheel.layer - currentLayer
                    val alpha = if (relativeLayer == 0) 255
                        else (170 - abs(relativeLayer) * 30).coerceAtLeast(60)
                    cannonArt.draw(canvas, game, gear, alpha)
                }
                else -> Unit
            }
        }
    }

    /**
     * La gorge de lancement d'un volant, son encoche, et le boulet qui l'attend.
     *
     * Le boulet est **dans** le mecanisme, pas au bout d'une perche : un anneau creuse
     * dans la jante le tient contre la force centrifuge pendant que la roue monte en
     * regime, et il s'echappe par l'encoche quand elle passe au point de largage. Le
     * dessiner en attente dans sa gorge est ce qui donne son sens au tir -- sans lui,
     * la fleche de sortie partait de nulle part.
     */
    private fun drawLaunchTrack(
        canvas: Canvas,
        gear: GearMachineGame.GearState,
        active: Boolean
    ) {
        val wheel = gear.wheel
        val point = game.launchPointAngle(wheel)
        val aim = Math.toRadians(wheel.launchAngle.toDouble()).toFloat()
        val alpha = layerAlpha(wheel.layer, if (active) 230 else 130, 70)
        val cx = gear.body.x
        val cy = gear.body.y

        // Les deux levres de la gorge.
        pTrack.color = if (active) Color.rgb(230, 112, 82) else Color.rgb(150, 122, 116)
        pTrack.alpha = alpha
        pTrack.strokeWidth = if (active) 0.075f else 0.05f
        val inner = (wheel.launchRadius - wheel.grooveWidth / 2f).coerceAtLeast(0.05f)
        canvas.drawCircle(cx, cy, wheel.grooveOuterRadius, pTrack)
        canvas.drawCircle(cx, cy, inner, pTrack)

        // L'encoche : la levre exterieure s'ouvre, et le boulet s'en va par la.
        val px = cx + cos(point) * wheel.launchRadius
        val py = cy + sin(point) * wheel.launchRadius
        val outX = cx + cos(point) * (wheel.grooveOuterRadius + 0.10f)
        val outY = cy + sin(point) * (wheel.grooveOuterRadius + 0.10f)
        pTrack.strokeWidth = if (active) 0.10f else 0.06f
        canvas.drawLine(px, py, outX, outY, pTrack)
        val lipX = -sin(point) * wheel.grooveWidth * 0.55f
        val lipY = cos(point) * wheel.grooveWidth * 0.55f
        canvas.drawLine(px - lipX, py - lipY, px + lipX, py + lipY, pTrack)

        if (!active) {
            pTrack.alpha = 255
            return
        }

        // Le boulet en attente, exactement celui qui partira.
        if (game.phase == GearMachineGame.Phase.BUILD) {
            val radius = GearMachineRules.projectileRadius(game.config.projectileMass)
            pGear.color = Color.rgb(245, 240, 222)
            pGear.alpha = alpha
            canvas.drawCircle(px, py, radius, pGear)
            pTrack.strokeWidth = 0.035f
            canvas.drawCircle(px, py, radius, pTrack)
        }

        // La fleche de sortie : la direction du tir, la seule chose que l'on regle.
        val from = wheel.grooveOuterRadius + 0.12f
        val tipX = cx + cos(point) * from + cos(aim) * 0.2f
        val tipY = cy + sin(point) * from + sin(aim) * 0.2f
        // La fleche se mesure sur la roue : sur un petit volant, une fleche de deux
        // metres cacherait la piece qu'elle est censee expliquer.
        val reach = (wheel.outerRadius * 0.85f).coerceIn(1.1f, 3.2f)
        val headX = tipX + cos(aim) * reach
        val headY = tipY + sin(aim) * reach
        pTrack.strokeWidth = 0.12f
        canvas.drawLine(tipX, tipY, headX, headY, pTrack)
        val wing = reach * 0.2f
        canvas.drawLine(
            headX, headY,
            headX - cos(aim - 0.42f) * wing, headY - sin(aim - 0.42f) * wing, pTrack
        )
        canvas.drawLine(
            headX, headY,
            headX - cos(aim + 0.42f) * wing, headY - sin(aim + 0.42f) * wing, pTrack
        )
        pTrack.alpha = 255
        drawSpinHint(canvas, gear, alpha)
    }

    /** Un arc flechee au centre : dans quel sens la roue lachera son boulet. */
    /**
     * Les machines motrices, dessinees derriere les roues qu'elles entrainent.
     *
     * Le trace vit dans [GearMotorArt] : un moulin qui merite d'etre regarde fait deux
     * cents lignes a lui seul, et la vue en compte deja mille sept cents.
     */
    private fun drawMotors(canvas: Canvas) {
        for (gear in game.gears) {
            if (gear.wheel.motor == null) continue
            val relativeLayer = gear.wheel.layer - currentLayer
            val alpha = if (relativeLayer == 0) 255
                else (170 - abs(relativeLayer) * 30).coerceAtLeast(60)
            motorArt.draw(canvas, gear, alpha)
        }
    }

    private fun drawSpinHint(canvas: Canvas, gear: GearMachineGame.GearState, alpha: Int) {
        val spin = game.launchSpin(gear.wheel.id)
        val radius = gear.wheel.pitchRadius * 0.45f
        if (radius < 0.2f) return
        spinRect.set(
            gear.body.x - radius, gear.body.y - radius,
            gear.body.x + radius, gear.body.y + radius
        )
        pSpin.alpha = (alpha * 0.8f).toInt()
        // Le canevas est retourne en Y : un balayage positif y tourne dans le sens
        // horaire du monde, donc le signe suit directement le sens de rotation.
        val sweep = 210f * spin
        canvas.drawArc(spinRect, 150f, sweep, false, pSpin)
        val endAngle = Math.toRadians((150f + sweep).toDouble()).toFloat()
        val ex = gear.body.x + cos(endAngle) * radius
        val ey = gear.body.y + sin(endAngle) * radius
        // La pointe, posee le long de la tangente au bout de l'arc.
        val tangent = endAngle + spin * (Math.PI / 2.0).toFloat()
        val head = radius * 0.34f
        canvas.drawLine(
            ex, ey, ex - cos(tangent - 0.5f) * head, ey - sin(tangent - 0.5f) * head, pSpin
        )
        canvas.drawLine(
            ex, ey, ex - cos(tangent + 0.5f) * head, ey - sin(tangent + 0.5f) * head, pSpin
        )
        pSpin.alpha = 255
    }

    private fun drawGear(canvas: Canvas, gear: GearMachineGame.GearState) {
        val teeth = gear.wheel.teeth
        val pitch = gear.wheel.pitchRadius
        val outer = gear.wheel.outerRadius
        val root = (pitch - GearMachineRules.MODULE * 0.9f).coerceAtLeast(outer * 0.2f)
        gearPath.reset()
        val points = teeth * 4
        for (i in 0 until points) {
            val angle = gear.body.angle + i * (2f * PI.toFloat() / points)
            val radius = if (i % 4 == 1 || i % 4 == 2) outer else root
            val x = gear.body.x + cos(angle) * radius
            val y = gear.body.y + sin(angle) * radius
            if (i == 0) gearPath.moveTo(x, y) else gearPath.lineTo(x, y)
        }
        gearPath.close()
        val relativeLayer = gear.wheel.layer - currentLayer
        if (relativeLayer > 0) {
            pUpperLayer.color = layerColor(gear.wheel.layer, pastel = true)
            pUpperLayer.alpha = (105 - (relativeLayer - 1) * 16).coerceAtLeast(35)
            canvas.drawPath(gearPath, pUpperLayer)
            val hubRadius = minOf(0.22f, pitch * 0.25f).coerceAtLeast(0.08f)
            canvas.drawCircle(gear.body.x, gear.body.y, hubRadius, pUpperLayer)
            if (gear.wheel.id in layoutConflictIds) {
                canvas.drawCircle(gear.body.x, gear.body.y, outer + 0.22f, pConflict)
            }
            return
        }

        pGear.color = materialColor(gear.wheel.material, gear.wheel.layer, pastel = relativeLayer < 0)
        pGear.alpha = if (relativeLayer == 0 || gear.wheel.id == selectedId) 235
            else (175 - (-relativeLayer - 1) * 20).coerceAtLeast(75)
        canvas.drawPath(gearPath, pGear)
        drawMaterialTexture(canvas, gear, pitch, pGear.alpha)
        if (gear.wheel.kind in GearMachineRules.LAUNCHER_KINDS) {
            // Un volant ou une manivelle de canon sont une jante montée sur des
            // rayons, et il faut le **voir** : c'est ce qui explique qu'il pèse une
            // tonne et non cinquante-six, donc qu'un moteur puisse le faire tourner.
            // On creuse le disque jusque sous la jante, puis on repose les rayons
            // par-dessus le vide.
            val hollow = GearMachineRules.rimInnerRadius(gear.wheel.kind, outer) -
                GearMachineRules.MODULE * 0.4f
            pHub.color = Color.rgb(16, 24, 40)
            pHub.alpha = if (relativeLayer == 0) 255 else 150
            canvas.drawCircle(gear.body.x, gear.body.y, hollow, pHub)
            pTexture.color = materialColor(gear.wheel.material, gear.wheel.layer, pastel = false)
            pTexture.alpha = pGear.alpha
            pTexture.strokeWidth = maxOf(0.05f, outer * 0.035f)
            val boss = maxOf(0.10f, outer * 0.14f)
            for (i in 0 until FLYWHEEL_SPOKES) {
                val angle = gear.body.angle + i * (2f * PI.toFloat() / FLYWHEEL_SPOKES)
                canvas.drawLine(
                    gear.body.x + cos(angle) * boss, gear.body.y + sin(angle) * boss,
                    gear.body.x + cos(angle) * hollow, gear.body.y + sin(angle) * hollow, pTexture
                )
            }
            pTexture.alpha = 255
            pEdge.strokeWidth = 0.08f
            pEdge.alpha = pGear.alpha
            canvas.drawCircle(gear.body.x, gear.body.y, hollow, pEdge)
        }
        pEdge.strokeWidth = 0.035f
        pEdge.color = materialEdgeColor(gear.wheel.material, relativeLayer != 0)
        pEdge.alpha = if (relativeLayer == 0) 255 else 150
        canvas.drawPath(gearPath, pEdge)
        val hubRadius = minOf(0.22f, pitch * 0.25f).coerceAtLeast(0.08f)
        pHub.alpha = if (relativeLayer == 0) 255 else 150
        canvas.drawCircle(gear.body.x, gear.body.y, hubRadius, pHub)
        if (gear.wheel.id == selectedId) {
            pSelect.strokeWidth = 0.08f
            canvas.drawCircle(gear.body.x, gear.body.y, outer + 0.12f, pSelect)
        }
        if (gear.wheel.id in layoutConflictIds) {
            val radius = outer + 0.22f
            canvas.drawCircle(gear.body.x, gear.body.y, radius, pConflict)
            val cross = minOf(radius * 0.35f, 0.42f)
            canvas.drawLine(gear.body.x - cross, gear.body.y - cross, gear.body.x + cross, gear.body.y + cross, pConflict)
            canvas.drawLine(gear.body.x - cross, gear.body.y + cross, gear.body.x + cross, gear.body.y - cross, pConflict)
        }
        pHub.alpha = 255
        pEdge.alpha = 255
    }

    private fun drawMaterialTexture(canvas: Canvas, gear: GearMachineGame.GearState, pitch: Float, alpha: Int) {
        val spokes = when (gear.wheel.material) {
            GearWheelMaterial.WOOD -> 5
            GearWheelMaterial.ALUMINUM -> 6
            GearWheelMaterial.STEEL -> 8
            GearWheelMaterial.TITANIUM -> 7
        }
        pTexture.color = materialEdgeColor(gear.wheel.material, false)
        pTexture.alpha = (alpha * 0.62f).toInt()
        pTexture.strokeWidth = when (gear.wheel.material) {
            GearWheelMaterial.WOOD -> 0.035f
            else -> 0.055f
        }
        canvas.drawCircle(gear.body.x, gear.body.y, pitch * 0.62f, pTexture)
        val inner = (pitch * 0.22f).coerceAtLeast(0.11f)
        for (i in 0 until spokes) {
            val angle = gear.body.angle + i * (2f * PI.toFloat() / spokes)
            val end = if (gear.wheel.material == GearWheelMaterial.WOOD) pitch * 0.78f else pitch * 0.66f
            canvas.drawLine(gear.body.x + cos(angle) * inner, gear.body.y + sin(angle) * inner,
                gear.body.x + cos(angle) * end, gear.body.y + sin(angle) * end, pTexture)
        }
        if (gear.wheel.material == GearWheelMaterial.WOOD) {
            canvas.drawCircle(gear.body.x, gear.body.y, pitch * 0.42f, pTexture)
            canvas.drawCircle(gear.body.x, gear.body.y, pitch * 0.84f, pTexture)
        }
        pTexture.alpha = 255
    }

    private fun materialColor(material: GearWheelMaterial, layer: Int, pastel: Boolean): Int {
        val base = when (material) {
            GearWheelMaterial.WOOD -> Color.rgb(158, 95, 48)
            GearWheelMaterial.ALUMINUM -> Color.rgb(164, 184, 194)
            GearWheelMaterial.STEEL -> Color.rgb(76, 118, 151)
            GearWheelMaterial.TITANIUM -> Color.rgb(117, 91, 156)
        }
        return if (pastel) SkyState.mix(base, Color.WHITE, 0.45f) else {
            // Une légère teinte d'étage reste présente sans masquer le matériau.
            SkyState.mix(base, layerColor(layer, pastel = false), 0.14f)
        }
    }

    private fun materialEdgeColor(material: GearWheelMaterial, muted: Boolean): Int {
        val color = when (material) {
            GearWheelMaterial.WOOD -> Color.rgb(91, 50, 24)
            GearWheelMaterial.ALUMINUM -> Color.rgb(238, 246, 249)
            GearWheelMaterial.STEEL -> Color.rgb(205, 228, 241)
            GearWheelMaterial.TITANIUM -> Color.rgb(227, 207, 255)
        }
        return if (muted) SkyState.mix(color, Color.WHITE, 0.36f) else color
    }

    private fun supportColor(material: GearWheelMaterial): Int = when (material) {
        GearWheelMaterial.WOOD -> Color.rgb(108, 65, 33)
        GearWheelMaterial.ALUMINUM -> Color.rgb(184, 199, 202)
        GearWheelMaterial.STEEL -> Color.rgb(95, 119, 135)
        GearWheelMaterial.TITANIUM -> Color.rgb(141, 119, 170)
    }

    private fun drawAssemblySelection(canvas: Canvas) {
        if (!structureTool) return
        val id = selectedId ?: return
        // Une seule fermeture transitive par image, et non une par roue dessinée.
        val members = game.assemblyIds(id)
        for (member in game.gears) if (member.wheel.id in members) {
            canvas.drawCircle(member.body.x, member.body.y, member.wheel.outerRadius + 0.18f, pAssembly)
        }
    }

    /**
     * D'où part la liaison en attente.
     *
     * Le bandeau disait « touchez la seconde roue », mais rien ne montrait la
     * première : sur une machine de vingt roues, on ne savait plus laquelle on avait
     * choisie, et la seule issue était d'annuler pour recommencer.
     */
    private fun drawPendingLink(canvas: Canvas) {
        val first = pendingLink?.firstId ?: return
        val gear = game.gears.firstOrNull { it.wheel.id == first } ?: return
        canvas.drawCircle(gear.body.x, gear.body.y, gear.wheel.outerRadius + 0.18f, pAssembly)
    }

    /** Une teinte stable et très distincte pour chaque étage voisin. */
    private fun layerColor(layer: Int, pastel: Boolean): Int {
        val hue = ((205f + layer * 53f) % 360f + 360f) % 360f
        return Color.HSVToColor(floatArrayOf(hue, if (pastel) 0.32f else 0.68f, if (pastel) 0.96f else 0.92f))
    }

    private fun layerAlpha(layer: Int, below: Int, above: Int): Int = when {
        layer == currentLayer -> 255
        layer < currentLayer -> (below - (currentLayer - layer - 1) * 18).coerceAtLeast(55)
        else -> (above - (layer - currentLayer - 1) * 12).coerceAtLeast(28)
    }

    /** Retour bref mais net quand l'aimant vient de trouver une prise. */
    private fun drawMagnetFeedback(canvas: Canvas) {
        val firstId = magnetFirstId ?: return
        val secondId = magnetSecondId ?: return
        val now = SystemClock.uptimeMillis()
        if (magneticTargetId == null && now > magnetFeedbackUntil) return
        val a = game.gears.firstOrNull { it.wheel.id == firstId } ?: return
        val b = game.gears.firstOrNull { it.wheel.id == secondId } ?: return
        val dx = b.body.x - a.body.x
        val dy = b.body.y - a.body.y
        val distance = hypot(dx, dy).coerceAtLeast(1e-4f)
        val pulse = ((now - magnetPulseStarted) / 420f).coerceIn(0f, 1f)
        pMagnet.alpha = if (magneticTargetId != null) 210 else ((1f - pulse) * 190f).toInt()
        pMagnet.strokeWidth = 0.055f + (1f - pulse) * 0.035f
        canvas.drawLine(a.body.x, a.body.y, b.body.x, b.body.y, pMagnet)
        val expansion = pulse * 0.18f
        canvas.drawCircle(a.body.x, a.body.y, a.wheel.outerRadius + 0.10f + expansion, pMagnet)
        canvas.drawCircle(b.body.x, b.body.y, b.wheel.outerRadius + 0.10f + expansion, pMagnet)
        val contactX = a.body.x + dx / distance * a.wheel.pitchRadius
        val contactY = a.body.y + dy / distance * a.wheel.pitchRadius
        canvas.drawCircle(contactX, contactY, 0.10f + (1f - pulse) * 0.06f, pMagnet)
        pMagnet.alpha = 255
    }

    private fun updateMagnetFeedback(movedId: Int, targetId: Int?) {
        if (targetId == null) {
            if (magneticTargetId != null) magnetFeedbackUntil = 0L
            magneticTargetId = null
            return
        }
        if (targetId != magneticTargetId) {
            magnetFirstId = movedId
            magnetSecondId = targetId
            magnetPulseStarted = SystemClock.uptimeMillis()
        }
        magneticTargetId = targetId
        magnetFeedbackUntil = SystemClock.uptimeMillis() + 650L
    }

    /**
     * Les fantômes des tirs précédents, du plus vieux au plus récent.
     *
     * Sans eux, corriger une machine relève de la superstition : avec, on voit de
     * combien on a manqué et **dans quel sens** on se trompe. Le dernier tir est franc,
     * les précédents s'éteignent — c'est ce dégradé qui fait toute la lecture.
     */
    /**
     * Les fantômes des tirs précédents, **peints une fois pour toutes**.
     *
     * Ce sont dix polylignes par défaut — jusqu'à cinquante — qui traversent l'écran de
     * part en part. Redessinées à chaque image, Skia n'arrivait pas à les confier à la
     * puce graphique : il les rastérisait sur le processeur, chacune dans un masque de
     * la taille de l'écran, puis téléversait ces masques vers la carte. Mesuré à la
     * tablette, ce seul travail occupait un quart du temps de calcul du jeu — pour une
     * image qui **ne change pas** tant que la caméra est immobile et qu'aucun tir ne
     * s'ajoute.
     *
     * On les peint donc dans un calque gardé de côté, et les images suivantes n'en
     * recopient que le rectangle, ce qui est une seule texture à poser. Pendant un
     * glissement ou un pincement, le calque serait refait à chaque image pour rien : on
     * repasse alors au tracé direct, qui ne coûte pas plus cher qu'avant, et le calque
     * se refait tout seul dès que le doigt s'arrête.
     */
    private fun drawGhosts(canvas: Canvas) {
        if (game.ghosts.isEmpty()) {
            releaseGhostLayer()
            return
        }
        if (width <= 0 || height <= 0) return

        val stamp = game.ghostStamp
        val layer = ghostLayer
        if (layer != null && ghostReady &&
            layer.width == width && layer.height == height &&
            ghostCamX == camX && ghostCamY == camY && ghostCamScale == camScale &&
            ghostStamp == stamp
        ) {
            canvas.drawBitmap(layer, 0f, 0f, null)
            return
        }
        if (cameraMoving) {
            ghostReady = false
            paintGhosts(canvas)
            return
        }
        val fresh = ensureGhostLayer()
        if (fresh == null) {
            paintGhosts(canvas)
            return
        }
        fresh.eraseColor(Color.TRANSPARENT)
        paintGhosts(ghostLayerCanvas ?: return)
        ghostCamX = camX
        ghostCamY = camY
        ghostCamScale = camScale
        ghostStamp = stamp
        ghostReady = true
        canvas.drawBitmap(fresh, 0f, 0f, null)
    }

    private fun paintGhosts(canvas: Canvas) {
        val list = game.ghosts
        val last = list.size - 1
        for (i in last downTo 0) {
            val age = if (last == 0) 0f else i / last.toFloat()
            val v = (255 - 90f * age).toInt()
            val b = (255 - 70f * age).toInt()
            pGhost.color = Color.argb((170 - 110f * age).toInt(), v, v, b)
            pGhost.strokeWidth = (2.2f - 0.9f * age) * dp
            drawPolyline(canvas, list[i], list[i].size, pGhost)
        }
    }

    /**
     * Le calque à la taille de la vue, ou `null` s'il n'y a pas la place.
     *
     * Un plein écran en huit bits par couche pèse une vingtaine de mégaoctets. C'est
     * assez pour manquer sur un appareil serré, et le jeu doit alors continuer sans le
     * calque plutôt que de s'arrêter. L'ancien n'est jamais recyclé : la couche de rendu
     * peut encore le tenir dans la liste d'affichage de l'image en cours, et dessiner
     * une image recyclée ferait tomber l'application. Le ramasse-miettes s'en charge.
     */
    private fun ensureGhostLayer(): Bitmap? {
        val current = ghostLayer
        if (current != null && current.width == width && current.height == height) return current
        releaseGhostLayer()
        return try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                ghostLayer = it
                ghostLayerCanvas = Canvas(it)
            }
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun releaseGhostLayer() {
        ghostLayer = null
        ghostLayerCanvas = null
        ghostReady = false
    }

    private fun drawTrail(canvas: Canvas) {
        drawPolyline(canvas, game.trail, game.trailCount, pTrail)
    }

    /**
     * Une polyligne du monde, tracée en pixels, **simplifiée** et coupée hors de l'écran.
     *
     * Une trajectoire est relevée toutes les vingt millisecondes, ce qui fait jusqu'à
     * trois mille points. Vue de loin, la quasi-totalité d'entre eux tombe sur la même
     * droite au demi-pixel près : les garder ne changeait pas un pixel de l'image, mais
     * donnait à Skia trois mille arêtes à trier et à remplir, par fantôme et par image.
     *
     * La méthode est celle du **couloir** : depuis le dernier point posé, on retient la
     * direction du point suivant, et on laisse tomber tous ceux qui restent à moins de
     * [SIMPLIFY_PX] de cette droite. Le premier qui en sort fait poser son prédécesseur
     * et rouvre un couloir. Tous les points sautés sont donc bornés par rapport à la
     * droite qu'on trace à leur place — c'est ce qui garantit le résultat.
     *
     * **Ce n'est pas la première version, et la première était fausse.** Elle ne
     * comparait que le point en attente à la corde qui l'enjambait ; comme ce point est
     * toujours le voisin immédiat du bout de la corde, son écart reste minuscule quelle
     * que soit la courbure accumulée, si bien que le critère ne se déclenchait
     * jamais. Sur une parabole de trois cents points elle en gardait **trois**, avec
     * vingt et un pixels d'erreur là où elle en promettait un demi : les arcs de tir
     * étaient rendus par des segments droits. Le couloir, mesuré sur la même parabole
     * et sur un arc de cercle, retire quatre-vingt-douze pour cent des points pour un
     * écart maximal de 0,16 px.
     *
     * Le seuil est en **pixels d'écran** et non en mètres : il se resserre tout seul
     * quand on zoome, donc la courbe reste aussi lisse de près qu'avant. Un seuil en
     * unités du monde aurait fait l'inverse — invisible de loin, taillé à la serpe une
     * fois la loupe posée dessus.
     */
    private fun drawPolyline(canvas: Canvas, points: FloatArray, count: Int, paint: Paint) {
        if (count < 4) return
        val w = width.toFloat()
        val h = height.toFloat()
        trailPath.reset()
        // `px, py` : dernier point retenu. `hx, hy` : celui qu'on garde sous le coude, en
        // attendant de savoir si le point suivant le rend inutile.
        var px = sx(points[0])
        var py = sy(points[1])
        var lastX = Float.NaN
        var lastY = Float.NaN
        var drew = false
        var hx = Float.NaN
        var hy = Float.NaN
        var dirX = 0f
        var dirY = 0f
        var oriented = false

        fun poser(cx: Float, cy: Float) {
            val outside = (px < 0f && cx < 0f) || (px > w && cx > w) ||
                (py < 0f && cy < 0f) || (py > h && cy > h)
            if (!outside) {
                if (px != lastX || py != lastY) trailPath.moveTo(px, py)
                trailPath.lineTo(cx, cy)
                lastX = cx
                lastY = cy
                drew = true
            }
            px = cx
            py = cy
        }

        var i = 2
        while (i < count) {
            val cx = sx(points[i])
            val cy = sy(points[i + 1])
            i += 2
            if (!oriented) {
                // Deux points sur le même pixel ne dessinent rien de plus qu'un seul.
                val ddx = cx - px
                val ddy = cy - py
                val len = hypot(ddx, ddy)
                if (len < 1f) continue
                dirX = ddx / len
                dirY = ddy / len
                oriented = true
                hx = cx
                hy = cy
                continue
            }
            // Distance du point **à la droite du couloir**, celle qui part du dernier
            // point posé dans la direction retenue.
            if (abs((cx - px) * dirY - (cy - py) * dirX) > SIMPLIFY_PX) {
                poser(hx, hy)
                val ddx = cx - px
                val ddy = cy - py
                val len = hypot(ddx, ddy)
                if (len < 1e-4f) {
                    oriented = false
                } else {
                    dirX = ddx / len
                    dirY = ddy / len
                }
            }
            hx = cx
            hy = cy
        }
        if (!hx.isNaN()) poser(hx, hy)
        if (drew) canvas.drawPath(trailPath, paint)
    }

    /** Sélecteur d'étage fixe : il reste lisible quel que soit le zoom de la machine. */
    private fun drawLayerSelector(canvas: Canvas) {
        val left = PANEL_LEFT_DP * dp
        val top = PANEL_TOP_DP * dp
        val height = PANEL_ROW_DP * dp
        val button = PANEL_ROW_DP * dp
        val width = PANEL_WIDTH_DP * dp
        screenRect.set(left, top, left + width, top + height)
        canvas.drawRoundRect(screenRect, 9f * dp, 9f * dp, pLayerPanel)
        screenRect.set(left, top, left + button, top + height)
        canvas.drawRoundRect(screenRect, 9f * dp, 9f * dp, pLayerButton)
        screenRect.set(left + width - button, top, left + width, top + height)
        canvas.drawRoundRect(screenRect, 9f * dp, 9f * dp, pLayerButton)

        pLayerText.color = layerColor(currentLayer, pastel = false)
        canvas.drawText(
            "L${if (currentLayer > 0) "+$currentLayer" else currentLayer}",
            left + width / 2f,
            top + height / 2f - (pLayerText.ascent() + pLayerText.descent()) / 2f,
            pLayerText
        )
        pLayerText.color = Color.rgb(255, 209, 102)
        canvas.drawText("▼", left + button / 2f, top + height / 2f - (pLayerText.ascent() + pLayerText.descent()) / 2f, pLayerText)
        canvas.drawText("▲", left + width - button / 2f, top + height / 2f - (pLayerText.ascent() + pLayerText.descent()) / 2f, pLayerText)
    }

    private fun layerControlAt(x: Float, y: Float): Int {
        val left = PANEL_LEFT_DP * dp
        val top = PANEL_TOP_DP * dp
        val height = PANEL_ROW_DP * dp
        val width = PANEL_WIDTH_DP * dp
        val button = PANEL_ROW_DP * dp
        if (y !in top..(top + height)) return 0
        return when {
            x in left..(left + button) -> -1
            x in (left + width - button)..(left + width) -> +1
            else -> 0
        }
    }

    private fun drawStructureTool(canvas: Canvas) {
        val left = PANEL_LEFT_DP * dp
        val top = TOOL_TOP_DP * dp
        val width = PANEL_WIDTH_DP * dp
        val height = TOOL_HEIGHT_DP * dp
        screenRect.set(left, top, left + width, top + height)
        pLayerPanel.color = if (structureTool) Color.argb(235, 21, 77, 78) else Color.argb(220, 16, 25, 50)
        canvas.drawRoundRect(screenRect, 9f * dp, 9f * dp, pLayerPanel)
        pLayerPanel.color = Color.argb(225, 16, 25, 50)
        pLayerText.textSize = 12f * dp
        pLayerText.color = if (structureTool) Color.rgb(119, 239, 196) else Color.rgb(221, 230, 239)
        canvas.drawText(
            if (structureTool) labelToolFrame else labelToolPart,
            left + width / 2f,
            top + height / 2f - (pLayerText.ascent() + pLayerText.descent()) / 2f,
            pLayerText
        )
        pLayerText.textSize = 14f * dp
    }

    private fun structureControlAt(x: Float, y: Float): Boolean {
        val left = PANEL_LEFT_DP * dp
        val top = TOOL_TOP_DP * dp
        return x in left..(left + PANEL_WIDTH_DP * dp) &&
            y in top..(top + TOOL_HEIGHT_DP * dp)
    }

    private fun beginPinch(event: MotionEvent) {
        if (event.pointerCount < 2) return
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        pinchStartDistance = hypot(dx, dy).coerceAtLeast(1f)
        pinchStartScale = camScale
        pinchLastFocusX = (event.getX(0) + event.getX(1)) * 0.5f
        pinching = true
        // Deux doigts ne deselectionnent pas : sans ca, un pincement qui ne bouge
        // presque pas passerait pour un appui dans le vide et lacherait la piece tenue.
        tappedVoid = false
        gestureLocked = true
        manualCam = true
        touchMode = TouchMode.NONE
        touchGearId = null
        stopTimeControl()
    }

    private fun applyPinch(event: MotionEvent) {
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        val distance = hypot(dx, dy).coerceAtLeast(1f)
        val focusX = (event.getX(0) + event.getX(1)) * 0.5f
        val focusY = (event.getY(0) + event.getY(1)) * 0.5f
        // On garde le monde sous le centre *précédent* des deux doigts : si les
        // doigts voyagent ensemble, cela devient naturellement un pan horizontal.
        val worldX = wx(pinchLastFocusX)
        val minScale = (width / (rightCameraLimit() - leftCameraLimit())).coerceAtLeast(0.01f)
        val maxScale = 190f * dp
        camScale = (pinchStartScale * distance / pinchStartDistance).coerceIn(minScale, maxScale)
        camX = worldX - (focusX - width / 2f) / camScale
        pinchLastFocusX = focusX
        // Le sol ne suit jamais le doigt : il reste posé en bas de l'écran,
        // comme dans TrebuchetView, quelle que soit l'échelle choisie.
        camY = groundCamY(camScale)
        clampCamera()
    }

    private fun panScene(event: MotionEvent) {
        val dx = event.x - panLastX
        val dy = event.y - panLastY
        if (abs(dx) > 0.1f || abs(dy) > 0.1f) {
            sceneDragging = true
            manualCam = true
        }
        camX -= dx / camScale
        clampCamera()
        panLastX = event.x
        panLastY = event.y
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = wx(event.x)
        val y = wy(event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pointerDownRawX = event.rawX
                pointerDownRawY = event.rawY
                lastGestureRawX = event.rawX
                lastGestureRawY = event.rawY
                pointerTravel = 0f
                tappedVoid = false
                manipulationStarted = false
                gestureLocked = false
                pinching = false
                sceneDragging = false
                panLastX = event.x
                panLastY = event.y
                stopTimeControl()
                val layerDelta = layerControlAt(event.x, event.y)
                if (layerDelta != 0) {
                    touchMode = if (layerDelta < 0) TouchMode.LAYER_DOWN else TouchMode.LAYER_UP
                    setCurrentLayer(layerDelta)
                    return true
                }
                if (structureControlAt(event.x, event.y)) {
                    toggleStructureTool()
                    return true
                }
                val now = SystemClock.uptimeMillis()
                val doubleTap = now - lastTapAt < DOUBLE_TAP_MS
                lastTapAt = now
                if (doubleTap && placementTeeth == null) {
                    selectedId = null
                    cameraView = if (cameraView == CameraView.FULL) CameraView.BUILD else CameraView.FULL
                    focusCamera(cameraView)
                    gestureLocked = true
                    touchMode = TouchMode.NONE
                    listener?.onGearSelectionChanged()
                    invalidate()
                    return true
                }
                val hit = game.gearAt(x, y, currentLayer)
                if (hit == null) {
                    val teeth = placementTeeth
                    if (teeth != null) {
                        selectedId = game.addGear(teeth, x, y, currentLayer)
                        placementTeeth = null
                        listener?.onGearMachineChanged()
                    } else {
                        // **On ne deselectionne pas tout de suite.** Un doigt pose dans
                        // le vide veut peut-etre faire glisser la vue, et perdre sa piece
                        // a chaque fois qu'on regarde ailleurs etait une punition pour
                        // avoir navigue. C'est le lever qui tranchera, selon que le doigt
                        // a voyage ou non — meme regle qu'au trebuchet.
                        tappedVoid = true
                        // Le ciel commence **au-dessus du sol qu'on voit**, pas au-dessus
                        // de l'altitude zero : depuis qu'il y a du relief, un doigt pose
                        // sur le flanc d'une colline etait compte comme pose dans le ciel.
                        timeCandidate = y > game.terrain.heightAt(x)
                        timePressAt = now
                        timePressX = event.x
                        timeFingerX = event.x
                    }
                    touchMode = TouchMode.NONE
                    listener?.onGearSelectionChanged()
                    invalidate()
                    return true
                }
                pendingLink?.let { pending ->
                    if (hit.wheel.id != pending.firstId) {
                        val linked = game.addLink(
                            pending.firstId, hit.wheel.id, pending.kind, pending.inputDirection
                        )
                        if (linked) {
                            pendingLink = null
                            selectedId = hit.wheel.id
                            currentLayer = hit.wheel.layer
                            touchMode = TouchMode.NONE
                            listener?.onGearMachineChanged()
                            listener?.onGearSelectionChanged()
                            invalidate()
                            return true
                        }
                        listener?.onGearLinkRejected(pending.kind)
                    }
                }
                // **Prendre une piece en main ne la deplace pas**, et c'est le point
                // important. Le meme geste faisait les deux : on touchait un engrenage,
                // et les quelques millimetres que le doigt parcourt toujours avant de se
                // relever le trainaient deja ailleurs — ou le faisaient tourner. Le
                // joueur voyait sa machine bouger avant d'avoir demande quoi que ce soit.
                //
                // Il faut desormais deux gestes, comme au trebuchet : le premier prend la
                // piece, le second la deplace ou la lance. Le premier appui ne fait
                // qu'eclairer la selection, ce qui est exactement ce qu'on attend d'un
                // clic.
                val dejaTenue = selectedId == hit.wheel.id
                selectedId = hit.wheel.id
                currentLayer = hit.wheel.layer
                // Toucher une pièce annule une pose armée : sans ça elle restait
                // tapie, et le prochain appui dans le vide lâchait une roue dont
                // personne ne voulait plus.
                cancelPlacement()
                layoutConflictIds = emptySet()
                if (!dejaTenue) {
                    // Le geste s'arrete la : ni deplacement, ni lancer, ni glissement de
                    // vue, quoi que le doigt fasse ensuite avant de se relever.
                    touchMode = TouchMode.NONE
                    touchGearId = null
                    gestureLocked = true
                    listener?.onGearSelectionChanged()
                    invalidate()
                    return true
                }
                game.captureWheelAngles()
                magneticTargetId = null
                val distance = hypot(x - hit.body.x, y - hit.body.y)
                touchMode = when {
                    // Le lanceur est plante sur son pas de tir : on peut encore le
                    // lancer a la main par la jante, mais pas le trainer ailleurs.
                    game.config.isPinned(hit.wheel.id) ->
                        if (distance > hit.wheel.pitchRadius * 0.55f) TouchMode.SPIN
                        else TouchMode.NONE
                    structureTool -> TouchMode.STRUCTURE_MOVE
                    distance > hit.wheel.pitchRadius * 0.55f -> TouchMode.SPIN
                    else -> TouchMode.MOVE
                }
                touchGearId = hit.wheel.id
                lastPointerAngle = atan2(y - hit.body.y, x - hit.body.x)
                gestureStartMillis = event.eventTime
                lastSpinMillis = event.eventTime
                gestureAngle = 0f
                listener?.onGearSelectionChanged()
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                beginPinch(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching && event.pointerCount >= 2) {
                    applyPinch(event)
                    invalidate()
                    return true
                }
                if (touchGearId == null) {
                    // Le chemin parcouru, et non l'ecart au point de depart : un doigt
                    // qui balaie le temps puis revient ou il etait a bel et bien glisse,
                    // et ne doit pas passer pour un appui qui deselectionne.
                    pointerTravel += hypot(
                        event.rawX - lastGestureRawX,
                        event.rawY - lastGestureRawY
                    )
                    lastGestureRawX = event.rawX
                    lastGestureRawY = event.rawY
                    timeFingerX = event.x
                    if (timeMode) {
                        invalidate()
                        return true
                    }
                    if (timeCandidate && abs(event.x - timePressX) > DRAG_SLOP_DP * dp) {
                        timeCandidate = false
                    }
                    if (!gestureLocked) panScene(event)
                    invalidate()
                    return true
                }
                pointerTravel += hypot(
                    event.rawX - lastGestureRawX,
                    event.rawY - lastGestureRawY
                )
                lastGestureRawX = event.rawX
                lastGestureRawY = event.rawY
                val id = touchGearId ?: return true
                when (touchMode) {
                    TouchMode.MOVE -> {
                        if (!manipulationStarted) {
                            val distanceFromDown = hypot(
                                event.rawX - pointerDownRawX,
                                event.rawY - pointerDownRawY
                            )
                            if (distanceFromDown < 8f * dp) return true
                            manipulationStarted = true
                        }
                        layoutConflictIds = emptySet()
                        val target = game.moveGearMagnetic(id, x, y, magneticTargetId)
                        updateMagnetFeedback(id, target)
                    }
                    TouchMode.STRUCTURE_MOVE -> {
                        if (!manipulationStarted) {
                            val distanceFromDown = hypot(
                                event.rawX - pointerDownRawX,
                                event.rawY - pointerDownRawY
                            )
                            if (distanceFromDown < 8f * dp) return true
                            manipulationStarted = true
                        }
                        layoutConflictIds = emptySet()
                        game.moveAssembly(id, x, y)
                    }
                    TouchMode.SPIN -> {
                        if (pointerTravel < 8f * dp) return true
                        manipulationStarted = true
                        val state = game.gears.firstOrNull { it.wheel.id == id } ?: return true
                        val angle = atan2(y - state.body.y, x - state.body.x)
                        var delta = angle - lastPointerAngle
                        while (delta > PI.toFloat()) delta -= (2.0 * PI).toFloat()
                        while (delta < -PI.toFloat()) delta += (2.0 * PI).toFloat()
                        gestureAngle += delta
                        lastPointerAngle = angle
                        // La roue suit le doigt tant qu'on la tient : c'est le seul
                        // retour qui dise que la jante a été prise.
                        val spinDt = ((event.eventTime - lastSpinMillis).coerceAtLeast(1L) / 1000f)
                        lastSpinMillis = event.eventTime
                        game.driveGear(id, delta / spinDt)
                    }
                    else -> Unit
                }
                listener?.onGearMachineChanged()
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                pinching = false
                panLastX = event.x
                panLastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Un appui dans le vide qui n'a pas glisse : le joueur pose la piece.
                if (tappedVoid && pointerTravel < DRAG_SLOP_DP * dp) selectedId = null
                tappedVoid = false
                if (touchMode == TouchMode.MOVE && manipulationStarted) {
                    touchGearId?.let { id ->
                        val target = game.moveGearMagnetic(id, x, y, magneticTargetId)
                        updateMagnetFeedback(id, target)
                        if (target != null) {
                            magnetPulseStarted = SystemClock.uptimeMillis()
                            magnetFeedbackUntil = magnetPulseStarted + 650L
                        }
                    }
                } else if (touchMode == TouchMode.STRUCTURE_MOVE && manipulationStarted) {
                    touchGearId?.let { id -> game.moveAssembly(id, x, y) }
                } else if (touchMode == TouchMode.SPIN && manipulationStarted &&
                    event.actionMasked == MotionEvent.ACTION_UP) {
                    touchGearId?.let { id ->
                        game.gears.firstOrNull { it.wheel.id == id }?.let { state ->
                            val angle = atan2(y - state.body.y, x - state.body.x)
                            var delta = angle - lastPointerAngle
                            while (delta > PI.toFloat()) delta -= (2.0 * PI).toFloat()
                            while (delta < -PI.toFloat()) delta += (2.0 * PI).toFloat()
                            gestureAngle += delta
                        }
                    }
                    val duration = ((event.eventTime - gestureStartMillis) / 1000f).coerceAtLeast(1f / 120f)
                    touchGearId?.let { id -> game.flickGear(id, gestureAngle / duration) }
                }
                touchMode = TouchMode.NONE
                touchGearId = null
                magneticTargetId = null
                pinching = false
                stopTimeControl()
                listener?.onGearMachineChanged()
                listener?.onGearSelectionChanged()
                invalidate()
                return true
            }
        }
        return true
    }

    companion object {
        /**
         * Quand redemander l'image suivante, en millisecondes après le début de l'image
         * en cours. Douze vise les soixante images par seconde sur un écran qui en
         * affiche cent vingt : voir [scheduleNextFrame].
         */
        private const val FRAME_POST_MS = 12L

        /**
         * De combien une polyligne a le droit de s'écarter de son tracé exact, en pixels
         * d'écran. Sous le pixel, l'antialiassage rend l'écart invisible : voir
         * [drawPolyline].
         */
        private const val SIMPLIFY_PX = 0.6f

        /**
         * Part de la hauteur d'ecran occupee par le ciel dans l'atelier.
         *
         * Plus haute qu'au champ de tir (0,62) parce que la dalle est plus bas : les
         * astres ont besoin de la meme place au-dessus d'elle.
         */
        private const val WORKSHOP_SKY_BAND = 0.72f

        /** Ecart maximal entre deux appuis pour que ce soit un double-appui, en ms. */
        private const val DOUBLE_TAP_MS = 300L

        /**
         * De combien le doigt peut voyager tout en restant un appui, en dp.
         *
         * Un doigt ne se leve jamais exactement ou il s'est pose ; sans cette tolerance,
         * un appui franc passerait pour un glissement.
         */
        private const val DRAG_SLOP_DP = 9f

        /** Duree de l'appui qui donne la main sur l'heure, en millisecondes. */
        private const val TIME_HOLD_MS = 420L

        /** Part de la largeur d'ecran a parcourir pour balayer le temps a pleine vitesse. */
        private const val TIME_THROW = 0.33f

        /** Les rayons d'un volant : assez pour qu'on lise la rotation, pas plus. */
        private const val FLYWHEEL_SPOKES = 6

        /** En combien d'images une charge se joue, et les bornes du decoupage. */
        private const val CHARGE_FRAMES = 90f
        private const val CHARGE_MIN_STEPS = 20
        private const val CHARGE_MAX_STEPS = 2_000

        /** De combien le sol est remonte depuis le bas de l'image. */
        private const val GROUND_INSET_DP = 30f

        /** L'air garde au-dessus du boulet pendant le vol, en metres. */
        private const val FLIGHT_TOP_MARGIN = 6f

        /** En dessous, le cadrage du vol ne se resserre plus. */
        private const val FLIGHT_MIN_WIDTH = 40f

        /** L'air garde apres le point de chute au plan de resultat. */
        private const val RESULT_MARGIN = 14f

        /**
         * Le cadrage colle aux ruines : marge autour du site, et ce qu'il faut voir au
         * minimum pour qu'une maisonnette isolee ne remplisse pas l'ecran.
         */
        /** Delai minimal entre deux bruits de pierre, en millisecondes. */
        private const val RUBBLE_GAP_MS = 90L

        private const val RESULT_HIT_MARGIN = 10f
        private const val RESULT_HIT_MIN_WIDTH = 16f
        private const val RESULT_HIT_MIN_HEIGHT = 10f
        // La géométrie des deux panneaux fixes du coin haut gauche. Elle était
        // recopiée dans les quatre méthodes qui les dessinent et les touchent, et
        // la bulle d'édition doit maintenant savoir où ils s'arrêtent pour ne pas
        // se poser dessus.
        private const val PANEL_LEFT_DP = 10f
        private const val PANEL_TOP_DP = 10f
        private const val PANEL_WIDTH_DP = 152f
        private const val PANEL_ROW_DP = 44f
        private const val TOOL_TOP_DP = 60f
        private const val TOOL_HEIGHT_DP = 38f

        /** Le coin haut gauche que les panneaux occupent, en dp. */
        const val HUD_RIGHT_DP = PANEL_LEFT_DP + PANEL_WIDTH_DP
        const val HUD_BOTTOM_DP = TOOL_TOP_DP + TOOL_HEIGHT_DP
    }

    private fun sx(x: Float) = width / 2f + (x - camX) * camScale
    private fun sy(y: Float) = height / 2f - (y - camY) * camScale
    private fun wx(x: Float) = camX + (x - width / 2f) / camScale
    private fun wy(y: Float) = camY - (y - height / 2f) / camScale
}
