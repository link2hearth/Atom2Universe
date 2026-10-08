package com.Atom2Universe.app.midi.practice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.practice.themes.PracticeTheme
import com.Atom2Universe.app.midi.practice.themes.PracticeThemeManager

/**
 * Vue affichant une partition simplifiée avec défilement horizontal
 *
 * Affiche les notes sur deux portées (clé de sol et clé de fa)
 * avec le temps courant fixé à 25% du bord gauche (style doublage)
 *
 * Améliorations:
 * - Symboles de clés (sol et fa)
 * - Hampes de notes avec direction automatique
 * - Altérations (dièses/bémols)
 * - Lignes de mesure
 * - Marqueurs 8va/8vb pour les notes extrêmes
 * - Optimisations de performance
 */
class SheetMusicView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // Position du temps courant (25% du bord gauche)
        private const val CURRENT_TIME_POSITION_PERCENT = 0.25f

        // Pixels par milliseconde (contrôle la vitesse de défilement)
        private const val DEFAULT_PIXELS_PER_MS = 0.15f

        // Dimensions des portées en dp
        private const val STAFF_LINE_SPACING_DP = 8f
        private const val STAFF_GAP_DP = 28f  // Espace entre clé de sol et clé de fa
        private const val STAFF_MARGIN_TOP_DP = 28f  // Augmenté pour 8va et lignes supplémentaires
        private const val STAFF_MARGIN_BOTTOM_DP = 24f  // Augmenté pour 8vb et lignes supplémentaires
        private const val CLEF_MARGIN_LEFT_DP = 8f

        // Dimensions des notes (ratio professionnel ~1.4:1)
        private const val NOTE_HEAD_WIDTH_DP = 9f
        private const val NOTE_HEAD_HEIGHT_DP = 6.5f
        private const val LEDGER_LINE_EXTENSION_DP = 3f

        // Hampes de notes
        private const val STEM_WIDTH_DP = 1f
        private const val STEM_HEIGHT_MULTIPLIER = 3.5f  // 3.5 x line spacing

        // Drapeaux de croches (flags)
        private const val FLAG_WIDTH_DP = 6f
        private const val FLAG_HEIGHT_DP = 12f

        // Altérations
        private const val ACCIDENTAL_SIZE_DP = 10f
        private const val ACCIDENTAL_OFFSET_DP = 8f

        // Barres de mesure
        private const val BAR_LINE_WIDTH_DP = 1f
        private const val DEFAULT_BEATS_PER_MEASURE = 4
        private const val DEFAULT_MS_PER_BEAT = 500L  // 120 BPM par défaut

        // Marqueurs d'octave (8va/8vb)
        private const val OCTAVE_MARKER_SIZE_DP = 8f
        private const val MAX_LEDGER_LINES = 2  // Au-delà, utiliser 8va/8vb

        // Do central = MIDI 60
        private const val MIDDLE_C = 60

        // Seuil pour direction de hampe (B4 = MIDI 71)
        private const val STEM_DIRECTION_THRESHOLD = 71

        // Zoom
        private const val MIN_SCALE = 0.7f
        private const val MAX_SCALE = 5.0f  // Limite haute, sera ajustée dynamiquement
        private const val DEFAULT_SCALE = 1.0f
        private const val MAX_HEIGHT_PERCENT = 0.5f  // Maximum 50% de la hauteur de l'écran

        // Affichage en mode enregistrement
        private const val SCHEDULED_NOTES_RECORDING_ALPHA = 160
        private const val RECORDED_NOTES_ALPHA = 235
    }

    // Enum pour les altérations
    private enum class Accidental { NONE, SHARP, FLAT }

    // Enum pour la transposition d'octave
    private enum class OctaveTransposition { NONE, UP_8VA, DOWN_8VB }

    // Enum pour les valeurs rythmiques
    private enum class NoteValue {
        WHOLE,      // Ronde (4 temps)
        HALF,       // Blanche (2 temps)
        QUARTER,    // Noire (1 temps)
        EIGHTH,     // Croche (1/2 temps)
        SIXTEENTH   // Double croche (1/4 temps)
    }

    // Notes programmées
    private var scheduledNotes: List<ScheduledNote> = emptyList()
    private var recordedNotes: List<ScheduledNote> = emptyList()
    private var currentPositionMs: Long = 0L
    private var targetChannel: Int = 0
    private var currentProgram: Int = 0  // For color lookup
    private var pixelsPerMs: Float = DEFAULT_PIXELS_PER_MS

    // Système de thèmes
    private var currentTheme: PracticeTheme? = null
    private var sheetMusicBaseNoteColor: Int = Color.BLACK
    private var sheetMusicSymbolColor: Int = "#222222".toColorInt()  // Couleur des clés, altérations, 8va/8vb
    private var recordedNoteColor: Int = ContextCompat.getColor(context, R.color.midi_accent)
    private var isRecordingModeEnabled: Boolean = false

    // Configuration tempo/mesure (peut être mis à jour depuis le fichier MIDI)
    private var msPerBeat: Long = DEFAULT_MS_PER_BEAT
    private var beatsPerMeasure: Int = DEFAULT_BEATS_PER_MEASURE

    // Zoom / Scale
    private var scaleFactor: Float = DEFAULT_SCALE
    private var maxScaleForScreen: Float = MAX_SCALE  // Limite dynamique basée sur l'écran
    private var onScaleChangedListener: ((Float, Float) -> Unit)? = null  // (scale, newHeight)
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    // Dimensions calculées (en pixels)
    private var staffLineSpacing = 0f
    private var staffGap = 0f
    private var staffMarginTop = 0f
    private var staffMarginBottom = 0f
    private var clefMarginLeft = 0f
    private var noteHeadWidth = 0f
    private var noteHeadHeight = 0f
    private var ledgerLineExtension = 0f
    private var stemWidth = 0f
    private var stemHeight = 0f
    private var accidentalSize = 0f
    private var accidentalOffset = 0f
    private var barLineWidth = 0f
    private var octaveMarkerSize = 0f
    private var flagWidth = 0f
    private var flagHeight = 0f
    private var density = 1f

    // Positions des portées
    private var trebleStaffTop = 0f
    private var trebleStaffBottom = 0f
    private var bassStaffTop = 0f
    private var bassStaffBottom = 0f

    // Position X du temps courant et début de la zone de notes
    private var currentTimeX = 0f
    private var notesStartX = 0f  // Après les clés

    // Hauteur de base (avant scale) pour calculer la nouvelle hauteur
    private var baseHeight = 0f

    // Horloge partagée pour synchronisation avec le thread audio
    private var sharedClock: PlaybackClock? = null

    // Animation avec Choreographer
    private var isAnimating = false
    private var animationStartTimeNs: Long = 0L
    private var animationStartPositionMs: Long = 0L
    private var playbackSpeed: Float = 1.0f

    // Pause animation
    private var isPaused = false
    private var pausedPositionMs: Long = 0L

    // BUG FIX 3.27: Dirty flag pour éviter les invalidate() excessifs
    private var isDirty = false
    private var lastInvalidateTimeMs = 0L
    private val minInvalidateIntervalMs = 16L  // ~60 FPS max

    private val choreographer = Choreographer.getInstance()
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isAnimating) {
                // Utiliser l'horloge partagée si disponible, sinon fallback sur le calcul local
                val clock = sharedClock
                if (clock != null && clock.isRunning()) {
                    currentPositionMs = clock.getCurrentPositionMs()
                } else {
                    val elapsedNs = frameTimeNanos - animationStartTimeNs
                    val elapsedMs = (elapsedNs / 1_000_000L * playbackSpeed).toLong()
                    currentPositionMs = animationStartPositionMs + elapsedMs
                }
                invalidate()
                choreographer.postFrameCallback(this)
            }
        }
    }

    // ========== PAINTS (pré-alloués pour performance) ==========

    private val backgroundPaint = Paint().apply {
        color = "#1A1A2E".toColorInt()
        style = Paint.Style.FILL
    }

    private val staffLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#50FFFFFF".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val currentTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#4CAF50".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val barLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#30FFFFFF".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val clefPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#222222".toColorInt()  // Noir pour fond gris par défaut
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
    }

    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val noteOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        color = "#40000000".toColorInt()
    }

    private val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // Pour les rondes et blanches (notes évidées)
    private val hollowNotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // Pour les drapeaux des croches
    private val flagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ledgerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#60FFFFFF".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val accidentalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#222222".toColorInt()  // Noir pour fond gris par défaut
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }

    private val octaveMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#CC222222".toColorInt()  // Noir semi-transparent
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }

    private val octaveMarkerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#66222222".toColorInt()  // Noir très transparent
        style = Paint.Style.STROKE
        strokeWidth = 1f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f)
    }

    // Objets réutilisables (évite les allocations dans onDraw)
    private val noteRect = RectF()
    private val flagPath = Path()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        initScaleGestureDetector()

        // Permettre la réception des événements tactiles
        isClickable = true
        isFocusable = true
    }

    private fun initScaleGestureDetector() {
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val effectiveMaxScale = maxScaleForScreen.coerceAtMost(MAX_SCALE)
                val newScale = (scaleFactor * detector.scaleFactor).coerceIn(MIN_SCALE, effectiveMaxScale)
                if (newScale != scaleFactor) {
                    scaleFactor = newScale
                    calculateDimensions()
                    invalidate()

                    // Notifier le listener du changement de scale
                    val newHeight = getDesiredHeight()
                    onScaleChangedListener?.invoke(scaleFactor, newHeight)
                }
                return true
            }
        })
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Toujours passer l'événement au détecteur de gestes
        val handled = scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Premier doigt - se préparer à recevoir plus d'événements
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Deuxième doigt arrive - on commence potentiellement un pinch
                // Demander au parent de ne pas intercepter
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Si on est en train de zoomer, consommer l'événement
                if (scaleGestureDetector.isInProgress || event.pointerCount > 1) {
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Fin du geste - permettre au parent d'intercepter à nouveau
                parent?.requestDisallowInterceptTouchEvent(false)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Un doigt levé
                if (event.pointerCount <= 2) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }

        return handled || event.pointerCount > 1
    }

    /**
     * Définit les notes programmées
     */
    fun setNotes(notes: List<ScheduledNote>) {
        scheduledNotes = notes
        markDirtyAndInvalidate()
    }

    /**
     * BUG FIX 3.27: Throttle les invalidate() pour éviter les appels excessifs
     */
    private fun markDirtyAndInvalidate() {
        isDirty = true
        val now = System.currentTimeMillis()
        if (now - lastInvalidateTimeMs >= minInvalidateIntervalMs) {
            lastInvalidateTimeMs = now
            invalidate()
        }
    }

    /**
     * Définit les notes enregistrées par l'utilisateur
     */
    fun setRecordedNotes(notes: List<ScheduledNote>) {
        recordedNotes = notes
        markDirtyAndInvalidate()
    }

    /**
     * Efface les notes enregistrées
     */
    @Suppress("unused")
    fun clearRecordedNotes() {
        recordedNotes = emptyList()
        invalidate()
    }

    /**
     * Active/désactive le mode enregistrement (affichage dédié)
     */
    fun setRecordingModeEnabled(enabled: Boolean) {
        if (isRecordingModeEnabled != enabled) {
            isRecordingModeEnabled = enabled
            markDirtyAndInvalidate()
        }
    }

    /**
     * Définit le canal cible (pour les couleurs)
     */
    fun setTargetChannel(channel: Int) {
        targetChannel = channel
        markDirtyAndInvalidate()
    }

    /**
     * Définit l'horloge partagée pour synchronisation avec le thread audio.
     * Si définie, la vue utilisera cette horloge au lieu de calculer sa propre position.
     */
    fun setSharedClock(clock: PlaybackClock?) {
        sharedClock = clock
    }

    /**
     * Applique un thème visuel à la partition
     */
    fun setTheme(theme: PracticeTheme) {
        currentTheme = theme
        applyThemeColors()
        markDirtyAndInvalidate()
    }

    /**
     * Applique le thème actuel du PracticeThemeManager
     */
    @Suppress("unused")
    fun applyCurrentTheme() {
        setTheme(PracticeThemeManager.getCurrentTheme())
    }

    /**
     * Met à jour les couleurs selon le thème
     */
    private fun applyThemeColors() {
        currentTheme?.let { theme ->
            backgroundPaint.color = theme.getSheetMusicBackgroundColor()
            staffLinePaint.color = theme.getStaffLineColor()
            currentTimePaint.color = theme.getCurrentTimeIndicatorColor()
            sheetMusicBaseNoteColor = theme.getSheetMusicNoteColor()
            sheetMusicSymbolColor = theme.getSheetMusicSymbolColor()
            recordedNoteColor = theme.getCurrentTimeIndicatorColor()

            // Appliquer la couleur des symboles aux paints
            clefPaint.color = sheetMusicSymbolColor
            accidentalPaint.color = sheetMusicSymbolColor
            // Pour les marqueurs 8va/8vb, utiliser une version semi-transparente
            val symbolAlpha = (sheetMusicSymbolColor ushr 24) and 0xFF
            val symbolR = (sheetMusicSymbolColor shr 16) and 0xFF
            val symbolG = (sheetMusicSymbolColor shr 8) and 0xFF
            val symbolB = sheetMusicSymbolColor and 0xFF
            octaveMarkerPaint.color = Color.argb((symbolAlpha * 0.8f).toInt(), symbolR, symbolG, symbolB)
            octaveMarkerLinePaint.color = Color.argb((symbolAlpha * 0.4f).toInt(), symbolR, symbolG, symbolB)
        }
    }

    /**
     * Définit le tempo pour les barres de mesure
     */
    @Suppress("unused")
    fun setTempo(bpm: Int, beatsPerMeasure: Int = DEFAULT_BEATS_PER_MEASURE) {
        // BUG FIX 3.26: Valider les valeurs de tempo raisonnables (20-300 BPM)
        val validBpm = bpm.coerceIn(20, 300)
        this.msPerBeat = (60000L / validBpm)
        this.beatsPerMeasure = beatsPerMeasure.coerceIn(1, 16)
        markDirtyAndInvalidate()
    }

    /**
     * Définit le facteur de zoom
     */
    fun setScale(scale: Float) {
        val effectiveMaxScale = maxScaleForScreen.coerceAtMost(MAX_SCALE)
        scaleFactor = scale.coerceIn(MIN_SCALE, effectiveMaxScale)
        calculateDimensions()
        markDirtyAndInvalidate()
    }

    /**
     * Retourne le facteur de zoom actuel
     */
    @Suppress("unused")
    fun getScale(): Float = scaleFactor

    /**
     * Définit la hauteur maximale disponible (pour calculer le scale max)
     * @param availableHeight la hauteur disponible en pixels
     */
    fun setAvailableHeight(availableHeight: Float) {
        if (baseHeight == 0f) {
            val d = resources.displayMetrics.density
            baseHeight = (STAFF_MARGIN_TOP_DP + 4 * STAFF_LINE_SPACING_DP + STAFF_GAP_DP +
                    4 * STAFF_LINE_SPACING_DP + STAFF_MARGIN_BOTTOM_DP) * d
        }
        // Calculer le scale max pour que la hauteur ne dépasse pas MAX_HEIGHT_PERCENT de l'écran
        val maxHeight = availableHeight * MAX_HEIGHT_PERCENT
        maxScaleForScreen = (maxHeight / baseHeight).coerceAtLeast(MIN_SCALE)
    }

    /**
     * Définit le listener pour les changements de scale
     */
    fun setOnScaleChangedListener(listener: ((scale: Float, newHeight: Float) -> Unit)?) {
        onScaleChangedListener = listener
    }

    /**
     * Retourne la hauteur désirée en fonction du scale
     */
    fun getDesiredHeight(): Float {
        if (baseHeight == 0f) {
            // Calculer la hauteur de base si pas encore définie
            val d = resources.displayMetrics.density
            baseHeight = (STAFF_MARGIN_TOP_DP + 4 * STAFF_LINE_SPACING_DP + STAFF_GAP_DP +
                    4 * STAFF_LINE_SPACING_DP + STAFF_MARGIN_BOTTOM_DP) * d
        }
        return baseHeight * scaleFactor
    }

    /**
     * Met à jour la position de lecture
     */
    fun updatePosition(positionMs: Long) {
        currentPositionMs = positionMs
        if (isAnimating) {
            animationStartTimeNs = System.nanoTime()
            animationStartPositionMs = positionMs
        } else {
            markDirtyAndInvalidate()
        }
    }

    /**
     * Démarre l'animation fluide
     */
    fun startAnimation(fromPositionMs: Long, speed: Float = 1.0f) {
        if (!isAnimating) {
            isAnimating = true
            animationStartTimeNs = System.nanoTime()
            animationStartPositionMs = fromPositionMs
            playbackSpeed = speed
            currentPositionMs = fromPositionMs
            choreographer.postFrameCallback(frameCallback)
        }
    }

    /**
     * Arrête l'animation
     */
    fun stopAnimation() {
        isAnimating = false
        isPaused = false
        choreographer.removeFrameCallback(frameCallback)
    }

    /**
     * Met en pause l'animation
     */
    fun pauseAnimation() {
        if (isAnimating && !isPaused) {
            isPaused = true
            pausedPositionMs = currentPositionMs
            choreographer.removeFrameCallback(frameCallback)
        }
    }

    /**
     * Reprend l'animation après une pause
     */
    fun resumeAnimation() {
        if (isPaused) {
            isPaused = false
            animationStartTimeNs = System.nanoTime()
            animationStartPositionMs = pausedPositionMs
            isAnimating = true
            choreographer.postFrameCallback(frameCallback)
        }
    }

    /**
     * Met à jour la vitesse de lecture pendant l'animation
     */
    fun setPlaybackSpeed(speed: Float) {
        if (isAnimating) {
            val currentTime = System.nanoTime()
            val elapsedNs = currentTime - animationStartTimeNs
            val elapsedMs = (elapsedNs / 1_000_000L * playbackSpeed).toLong()
            animationStartPositionMs += elapsedMs
            animationStartTimeNs = currentTime
        }
        playbackSpeed = speed
    }

    /**
     * Retourne la position actuelle
     */
    @Suppress("unused")
    fun getCurrentPositionMs(): Long = currentPositionMs

    /**
     * Efface l'état et remet à zéro
     */
    fun reset() {
        stopAnimation()
        isPaused = false
        pausedPositionMs = 0L
        currentPositionMs = 0L
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions()
    }

    private fun calculateDimensions() {
        density = resources.displayMetrics.density

        // Appliquer le scale factor à toutes les dimensions
        staffLineSpacing = STAFF_LINE_SPACING_DP * density * scaleFactor
        staffGap = STAFF_GAP_DP * density * scaleFactor
        staffMarginTop = STAFF_MARGIN_TOP_DP * density * scaleFactor
        staffMarginBottom = STAFF_MARGIN_BOTTOM_DP * density * scaleFactor
        clefMarginLeft = CLEF_MARGIN_LEFT_DP * density * scaleFactor
        noteHeadWidth = NOTE_HEAD_WIDTH_DP * density * scaleFactor
        noteHeadHeight = NOTE_HEAD_HEIGHT_DP * density * scaleFactor
        ledgerLineExtension = LEDGER_LINE_EXTENSION_DP * density * scaleFactor
        stemWidth = STEM_WIDTH_DP * density * scaleFactor
        stemHeight = staffLineSpacing * STEM_HEIGHT_MULTIPLIER
        accidentalSize = ACCIDENTAL_SIZE_DP * density * scaleFactor
        accidentalOffset = ACCIDENTAL_OFFSET_DP * density * scaleFactor
        barLineWidth = BAR_LINE_WIDTH_DP * density * scaleFactor
        octaveMarkerSize = OCTAVE_MARKER_SIZE_DP * density * scaleFactor
        flagWidth = FLAG_WIDTH_DP * density * scaleFactor
        flagHeight = FLAG_HEIGHT_DP * density * scaleFactor

        // Mettre à jour les paints qui dépendent de la taille
        stemPaint.strokeWidth = stemWidth.coerceAtLeast(1f)
        hollowNotePaint.strokeWidth = (1.5f * scaleFactor).coerceAtLeast(1f)
        barLinePaint.strokeWidth = barLineWidth.coerceAtLeast(1f)
        staffLinePaint.strokeWidth = (1f * scaleFactor).coerceAtLeast(0.5f)
        ledgerLinePaint.strokeWidth = (1f * scaleFactor).coerceAtLeast(0.5f)
        noteOutlinePaint.strokeWidth = (0.5f * scaleFactor).coerceAtLeast(0.3f)
        clefPaint.textSize = staffLineSpacing * 4.5f
        accidentalPaint.textSize = accidentalSize
        octaveMarkerPaint.textSize = octaveMarkerSize

        // Calculer la position X du temps courant
        currentTimeX = width * CURRENT_TIME_POSITION_PERCENT

        // Zone de départ des notes (après les clés)
        notesStartX = clefMarginLeft + staffLineSpacing * 3

        // Calculer les positions des portées
        // Clé de sol : 5 lignes
        trebleStaffTop = staffMarginTop
        trebleStaffBottom = trebleStaffTop + 4 * staffLineSpacing

        // Clé de fa : 5 lignes (avec gap entre les deux portées)
        bassStaffTop = trebleStaffBottom + staffGap
        bassStaffBottom = bassStaffTop + 4 * staffLineSpacing

        // Calculer la hauteur de base (à scale 1.0)
        if (baseHeight == 0f) {
            baseHeight = (STAFF_MARGIN_TOP_DP + 4 * STAFF_LINE_SPACING_DP + STAFF_GAP_DP +
                    4 * STAFF_LINE_SPACING_DP + STAFF_MARGIN_BOTTOM_DP) * density
        }

        // Ajuster pixelsPerMs en fonction du zoom (plus gros = moins de notes visibles = plus de pixels par ms)
        pixelsPerMs = DEFAULT_PIXELS_PER_MS * scaleFactor
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Fond
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (width == 0 || height == 0) return

        // 1. Dessiner les portées
        drawStaffLines(canvas)

        // 2. Dessiner les clés
        drawClefs(canvas)

        // 3. Dessiner les barres de mesure
        drawBarLines(canvas)

        // 4. Dessiner l'indicateur de position courante
        canvas.drawLine(currentTimeX, trebleStaffTop - staffLineSpacing,
            currentTimeX, bassStaffBottom + staffLineSpacing, currentTimePaint)

        // 5. Dessiner les notes
        drawNotes(canvas)
    }

    private fun drawStaffLines(canvas: Canvas) {
        val lineEndX = width.toFloat()

        // Clé de sol (5 lignes)
        for (i in 0..4) {
            val y = trebleStaffTop + i * staffLineSpacing
            canvas.drawLine(0f, y, lineEndX, y, staffLinePaint)
        }

        // Clé de fa (5 lignes)
        for (i in 0..4) {
            val y = bassStaffTop + i * staffLineSpacing
            canvas.drawLine(0f, y, lineEndX, y, staffLinePaint)
        }
    }

    private fun drawClefs(canvas: Canvas) {
        // Clé de sol (G clef) - Unicode: 𝄞 ou dessin manuel
        // Position: centré sur G4 (deuxième ligne depuis le bas)
        val trebleClefX = clefMarginLeft
        val trebleClefY = trebleStaffBottom - staffLineSpacing  // Ligne du G4
        canvas.drawText("𝄞", trebleClefX, trebleClefY + staffLineSpacing * 1.5f, clefPaint)

        // Clé de fa (F clef) - Unicode: 𝄢
        // Position: centré sur F3 (deuxième ligne depuis le haut)
        val bassClefX = clefMarginLeft
        val bassClefY = bassStaffTop + staffLineSpacing  // Ligne du F3
        canvas.drawText("𝄢", bassClefX, bassClefY + staffLineSpacing * 1.2f, clefPaint)
    }

    private fun drawBarLines(canvas: Canvas) {
        val measureDurationMs = msPerBeat * beatsPerMeasure

        // Calculer la fenêtre temporelle visible
        val leftTimeMs = currentPositionMs - (currentTimeX / pixelsPerMs).toLong()
        val rightTimeMs = currentPositionMs + ((width - currentTimeX) / pixelsPerMs).toLong()

        // Premier measure visible
        val firstMeasure = (leftTimeMs / measureDurationMs).coerceAtLeast(0)
        var measureStartMs = firstMeasure * measureDurationMs

        while (measureStartMs <= rightTimeMs) {
            val barX = timeToX(measureStartMs)
            if (barX >= notesStartX && barX <= width) {
                // Barre verticale sur toute la hauteur des portées
                canvas.drawLine(barX, trebleStaffTop, barX, trebleStaffBottom, barLinePaint)
                canvas.drawLine(barX, bassStaffTop, barX, bassStaffBottom, barLinePaint)
            }
            measureStartMs += measureDurationMs
        }
    }

    private fun drawNotes(canvas: Canvas) {
        // Calculer la fenêtre temporelle visible
        val leftTimeMs = currentPositionMs - (currentTimeX / pixelsPerMs).toLong()
        val rightTimeMs = currentPositionMs + ((width - currentTimeX) / pixelsPerMs).toLong()

        val scheduledBaseColor = if (currentTheme != null) {
            sheetMusicBaseNoteColor
        } else {
            ColorSettingsManager.getNoteColor(targetChannel, currentProgram)
        }
        val scheduledAlpha = if (isRecordingModeEnabled) SCHEDULED_NOTES_RECORDING_ALPHA else null

        drawNoteCollection(
            canvas = canvas,
            notes = scheduledNotes,
            leftTimeMs = leftTimeMs,
            rightTimeMs = rightTimeMs
        ) { velocity ->
            applyVelocityToColor(scheduledBaseColor, velocity, scheduledAlpha)
        }

        drawNoteCollection(
            canvas = canvas,
            notes = recordedNotes,
            leftTimeMs = leftTimeMs,
            rightTimeMs = rightTimeMs
        ) { velocity ->
            applyVelocityToColor(recordedNoteColor, velocity, RECORDED_NOTES_ALPHA)
        }
    }

    private fun drawNoteCollection(
        canvas: Canvas,
        notes: List<ScheduledNote>,
        leftTimeMs: Long,
        rightTimeMs: Long,
        colorForVelocity: (Int) -> Int
    ) {
        for (note in notes) {
            // Vérifier si la note est visible
            if (note.endTimeMs < leftTimeMs) continue
            if (note.startTimeMs > rightTimeMs) continue

            // Calculer la position X
            val x = timeToX(note.startTimeMs)
            if (x < notesStartX - noteHeadWidth) continue

            // Déterminer la transposition d'octave si nécessaire
            val transposition = getOctaveTransposition(note.note)
            val displayNote = getDisplayNote(note.note, transposition)

            // Calculer la position Y sur la portée
            val y = midiNoteToY(displayNote)

            // Obtenir l'altération
            val accidental = getAccidental(note.note)

            // Déterminer la valeur rythmique
            val durationMs = note.endTimeMs - note.startTimeMs
            val noteValue = getNoteValue(durationMs)

            // Couleur basée sur le canal et la vélocité
            val noteColor = colorForVelocity(note.velocity)
            drawSingleNote(canvas, x, y, displayNote, transposition, accidental, noteColor, noteValue)
        }
    }

    private fun drawSingleNote(
        canvas: Canvas,
        x: Float,
        y: Float,
        displayNote: Int,
        transposition: OctaveTransposition,
        accidental: Accidental,
        noteColor: Int,
        noteValue: NoteValue
    ) {
        notePaint.color = noteColor
        stemPaint.color = noteColor
        hollowNotePaint.color = noteColor
        flagPaint.color = noteColor

        // Dessiner les lignes supplémentaires
        drawLedgerLines(canvas, x, displayNote, y)

        // Dessiner l'altération si nécessaire
        if (accidental != Accidental.NONE) {
            drawAccidental(canvas, x, y, accidental)
        }

        // Dessiner la hampe (sauf pour les rondes)
        if (noteValue != NoteValue.WHOLE) {
            drawStem(canvas, x, y, displayNote, noteValue)
        }

        // Dessiner la tête de note
        noteRect.set(
            x - noteHeadWidth / 2,
            y - noteHeadHeight / 2,
            x + noteHeadWidth / 2,
            y + noteHeadHeight / 2
        )

        // Rondes et blanches : tête évidée, noires et croches : tête pleine
        when (noteValue) {
            NoteValue.WHOLE, NoteValue.HALF -> {
                // Tête évidée (outline)
                canvas.drawOval(noteRect, hollowNotePaint)
            }
            else -> {
                // Tête pleine
                canvas.drawOval(noteRect, notePaint)
                canvas.drawOval(noteRect, noteOutlinePaint)
            }
        }

        // Dessiner le marqueur d'octave si transposé
        if (transposition != OctaveTransposition.NONE) {
            drawOctaveMarker(canvas, x, y, transposition)
        }
    }

    /**
     * Dessine la hampe d'une note (et les drapeaux pour croches/doubles croches)
     */
    private fun drawStem(canvas: Canvas, x: Float, y: Float, midiNote: Int, noteValue: NoteValue) {
        // Direction: vers le haut si sous B4, vers le bas sinon
        val stemUp = midiNote < STEM_DIRECTION_THRESHOLD

        val stemX = if (stemUp) {
            x + noteHeadWidth / 2 - stemWidth / 2
        } else {
            x - noteHeadWidth / 2 + stemWidth / 2
        }

        val stemStartY = y
        val stemEndY = if (stemUp) {
            y - stemHeight
        } else {
            y + stemHeight
        }

        canvas.drawLine(stemX, stemStartY, stemX, stemEndY, stemPaint)

        // Dessiner les drapeaux pour croches et doubles croches
        val flagCount = when (noteValue) {
            NoteValue.EIGHTH -> 1
            NoteValue.SIXTEENTH -> 2
            else -> 0
        }

        if (flagCount > 0) {
            drawFlags(canvas, stemX, stemEndY, stemUp, flagCount)
        }
    }

    /**
     * Dessine les drapeaux (flags) pour les croches et doubles croches
     */
    private fun drawFlags(canvas: Canvas, stemX: Float, stemEndY: Float, stemUp: Boolean, count: Int) {
        flagPath.reset()

        for (i in 0 until count) {
            val flagOffset = i * flagHeight * 0.6f  // Espacement entre les drapeaux

            if (stemUp) {
                // Drapeau vers la droite et vers le bas
                val startY = stemEndY + flagOffset
                flagPath.moveTo(stemX, startY)
                flagPath.quadTo(
                    stemX + flagWidth * 0.8f, startY + flagHeight * 0.3f,
                    stemX + flagWidth, startY + flagHeight
                )
                flagPath.lineTo(stemX, startY + flagHeight * 0.7f)
                flagPath.close()
            } else {
                // Drapeau vers la droite et vers le haut
                val startY = stemEndY - flagOffset
                flagPath.moveTo(stemX, startY)
                flagPath.quadTo(
                    stemX + flagWidth * 0.8f, startY - flagHeight * 0.3f,
                    stemX + flagWidth, startY - flagHeight
                )
                flagPath.lineTo(stemX, startY - flagHeight * 0.7f)
                flagPath.close()
            }
        }

        canvas.drawPath(flagPath, flagPaint)
    }

    /**
     * Dessine une altération (dièse ou bémol)
     */
    private fun drawAccidental(canvas: Canvas, noteX: Float, noteY: Float, accidental: Accidental) {
        val symbol = when (accidental) {
            Accidental.SHARP -> "♯"
            Accidental.FLAT -> "♭"
            else -> return
        }

        val accX = noteX - noteHeadWidth / 2 - accidentalOffset
        canvas.drawText(symbol, accX, noteY + accidentalSize / 3, accidentalPaint)
    }

    /**
     * Dessine un marqueur d'octave (8va ou 8vb)
     */
    private fun drawOctaveMarker(canvas: Canvas, noteX: Float, noteY: Float, transposition: OctaveTransposition) {
        val label = when (transposition) {
            OctaveTransposition.UP_8VA -> "8va"
            OctaveTransposition.DOWN_8VB -> "8vb"
            else -> return
        }

        val markerY = if (transposition == OctaveTransposition.UP_8VA) {
            trebleStaffTop - staffLineSpacing * 1.5f
        } else {
            bassStaffBottom + staffLineSpacing * 2f
        }

        canvas.drawText(label, noteX, markerY, octaveMarkerPaint)

        // Ligne pointillée vers la note
        val lineStartY = if (transposition == OctaveTransposition.UP_8VA) {
            markerY + octaveMarkerSize / 2
        } else {
            markerY - octaveMarkerSize
        }
        canvas.drawLine(noteX, lineStartY, noteX, noteY, octaveMarkerLinePaint)
    }

    /**
     * Convertit un temps en position X
     */
    private fun timeToX(timeMs: Long): Float {
        val delta = timeMs - currentPositionMs
        return currentTimeX + delta * pixelsPerMs
    }

    /**
     * Détermine si une note nécessite une transposition d'octave pour l'affichage
     */
    private fun getOctaveTransposition(midiNote: Int): OctaveTransposition {
        return when {
            // Notes très hautes (au-dessus de C6 = 84) → 8va
            midiNote >= 84 -> OctaveTransposition.UP_8VA
            // Notes très basses (en dessous de C2 = 36) → 8vb
            midiNote < 36 -> OctaveTransposition.DOWN_8VB
            else -> OctaveTransposition.NONE
        }
    }

    /**
     * Retourne la note MIDI à afficher (transposée si nécessaire)
     */
    private fun getDisplayNote(midiNote: Int, transposition: OctaveTransposition): Int {
        return when (transposition) {
            OctaveTransposition.UP_8VA -> midiNote - 12
            OctaveTransposition.DOWN_8VB -> midiNote + 12
            OctaveTransposition.NONE -> midiNote
        }
    }

    /**
     * Retourne l'altération d'une note MIDI
     */
    private fun getAccidental(midiNote: Int): Accidental {
        val semitone = midiNote % 12
        // Notes avec dièse: C#(1), D#(3), F#(6), G#(8), A#(10)
        return when (semitone) {
            1, 3, 6, 8, 10 -> Accidental.SHARP
            else -> Accidental.NONE
        }
    }

    /**
     * Détermine la valeur rythmique d'une note en fonction de sa durée
     */
    private fun getNoteValue(durationMs: Long): NoteValue {
        // On utilise msPerBeat pour déterminer la valeur relative
        // ronde = 4 temps, blanche = 2 temps, noire = 1 temps, croche = 0.5 temps, double = 0.25 temps
        val beats = durationMs.toFloat() / msPerBeat.toFloat()

        return when {
            beats >= 3.5f -> NoteValue.WHOLE       // Ronde (>= 3.5 temps pour tolérance)
            beats >= 1.5f -> NoteValue.HALF        // Blanche (>= 1.5 temps)
            beats >= 0.75f -> NoteValue.QUARTER    // Noire (>= 0.75 temps)
            beats >= 0.375f -> NoteValue.EIGHTH    // Croche (>= 0.375 temps)
            else -> NoteValue.SIXTEENTH            // Double croche
        }
    }

    /**
     * Convertit une note MIDI en position Y sur la portée
     */
    private fun midiNoteToY(midiNote: Int): Float {
        // Convertir note MIDI en offset diatonique (Do central = 0)
        val diatonicOffset = midiNoteToDiatonicOffset(midiNote)

        return if (midiNote >= MIDDLE_C) {
            // Clé de sol
            // E4 (64) = ligne du bas de la portée treble
            // Chaque step diatonique = staffLineSpacing / 2
            val e4Offset = midiNoteToDiatonicOffset(64)  // E4
            val stepsFromE4 = diatonicOffset - e4Offset
            trebleStaffBottom - stepsFromE4 * (staffLineSpacing / 2)
        } else {
            // Clé de fa
            // G2 (43) = ligne du bas de la portée bass
            val g2Offset = midiNoteToDiatonicOffset(43)  // G2
            val stepsFromG2 = diatonicOffset - g2Offset
            bassStaffBottom - stepsFromG2 * (staffLineSpacing / 2)
        }
    }

    /**
     * Convertit une note MIDI en offset diatonique par rapport à C0
     */
    private fun midiNoteToDiatonicOffset(midiNote: Int): Int {
        val octave = midiNote / 12
        val semitone = midiNote % 12
        // Mapping demi-ton -> position diatonique dans l'octave
        // C=0, C#=0, D=1, D#=1, E=2, F=3, F#=3, G=4, G#=4, A=5, A#=5, B=6
        val diatonicInOctave = intArrayOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)[semitone]
        return octave * 7 + diatonicInOctave
    }

    /**
     * Dessine les lignes supplémentaires si nécessaire (limité à MAX_LEDGER_LINES)
     */
    private fun drawLedgerLines(canvas: Canvas, x: Float, midiNote: Int, noteY: Float) {
        val lineX1 = x - noteHeadWidth / 2 - ledgerLineExtension
        val lineX2 = x + noteHeadWidth / 2 + ledgerLineExtension

        if (midiNote >= MIDDLE_C) {
            // Clé de sol - lignes sous la portée
            if (noteY > trebleStaffBottom + staffLineSpacing / 4) {
                var lineY = trebleStaffBottom + staffLineSpacing
                var count = 0
                while (lineY <= noteY + staffLineSpacing / 4 && count < MAX_LEDGER_LINES + 2) {
                    canvas.drawLine(lineX1, lineY, lineX2, lineY, ledgerLinePaint)
                    lineY += staffLineSpacing
                    count++
                }
            }

            // Lignes au-dessus de la portée
            if (noteY < trebleStaffTop - staffLineSpacing / 4) {
                var lineY = trebleStaffTop - staffLineSpacing
                var count = 0
                while (lineY >= noteY - staffLineSpacing / 4 && count < MAX_LEDGER_LINES + 2) {
                    canvas.drawLine(lineX1, lineY, lineX2, lineY, ledgerLinePaint)
                    lineY -= staffLineSpacing
                    count++
                }
            }
        } else {
            // Clé de fa - lignes au-dessus de la portée
            if (noteY < bassStaffTop - staffLineSpacing / 4) {
                var lineY = bassStaffTop - staffLineSpacing
                var count = 0
                while (lineY >= noteY - staffLineSpacing / 4 && count < MAX_LEDGER_LINES + 2) {
                    canvas.drawLine(lineX1, lineY, lineX2, lineY, ledgerLinePaint)
                    lineY -= staffLineSpacing
                    count++
                }
            }

            // Lignes en dessous de la portée
            if (noteY > bassStaffBottom + staffLineSpacing / 4) {
                var lineY = bassStaffBottom + staffLineSpacing
                var count = 0
                while (lineY <= noteY + staffLineSpacing / 4 && count < MAX_LEDGER_LINES + 2) {
                    canvas.drawLine(lineX1, lineY, lineX2, lineY, ledgerLinePaint)
                    lineY += staffLineSpacing
                    count++
                }
            }
        }
    }

    /**
     * Retourne la couleur d'une note basée sur la vélocité
     */
    private fun applyVelocityToColor(baseColor: Int, velocity: Int, alphaOverride: Int? = null): Int {
        // Ajuster la luminosité selon la vélocité
        val brightness = 0.6f + (velocity / 127f) * 0.4f

        val r = ((baseColor shr 16 and 0xFF) * brightness).toInt().coerceIn(0, 255)
        val g = ((baseColor shr 8 and 0xFF) * brightness).toInt().coerceIn(0, 255)
        val b = ((baseColor and 0xFF) * brightness).toInt().coerceIn(0, 255)

        val baseAlpha = (baseColor ushr 24) and 0xFF
        val resolvedAlpha = alphaOverride ?: if (baseAlpha == 0) 255 else baseAlpha

        return Color.argb(resolvedAlpha, r, g, b)
    }

    /**
     * Définit le programme actuel (pour les couleurs d'instrument)
     */
    fun setCurrentProgram(program: Int) {
        currentProgram = program
        markDirtyAndInvalidate()
    }

    /**
     * Rafraîchit les couleurs (après modification dans les paramètres)
     */
    fun refreshColors() {
        markDirtyAndInvalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
