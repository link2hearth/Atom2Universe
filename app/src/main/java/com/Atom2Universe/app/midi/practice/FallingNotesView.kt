package com.Atom2Universe.app.midi.practice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import androidx.core.graphics.toColorInt
import android.view.Choreographer
import android.view.View
import com.Atom2Universe.app.midi.practice.themes.PracticeTheme
import com.Atom2Universe.app.midi.practice.themes.PracticeThemeManager
import com.Atom2Universe.app.midi.practice.themes.CustomBackgroundTheme
import kotlin.math.abs

/**
 * Vue affichant les notes tombantes (falling notes) style Rousseau/Synthesia
 *
 * Styles disponibles:
 * - CLASSIC: Style simple et épuré (vert par défaut)
 * - RAINBOW: Couleurs arc-en-ciel basées sur la note + glow + particules
 * - NEON: Couleurs néon vibrantes avec glow intense
 * - OCEAN: Dégradés bleu-turquoise apaisants
 * - FIRE: Dégradés rouge-orange-jaune
 */
class FallingNotesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Styles visuels disponibles
     */
    enum class VisualStyle(val displayName: String) {
        CLASSIC("Classique"),
        RAINBOW("Arc-en-ciel"),
        NEON("Néon"),
        OCEAN("Océan"),
        FIRE("Feu")
    }

    /**
     * Direction des notes (falling = MIDI playback, rising = free practice)
     */
    enum class NoteDirection {
        FALLING,  // Notes tombent du haut vers le bas (mode MIDI)
        RISING    // Notes montent du bas vers le haut (free practice)
    }

    companion object {
        // Durée d'anticipation par défaut (3 secondes)
        private const val DEFAULT_LOOKAHEAD_MS = 3000L

        // Tolérance par défaut pour la hit zone
        private const val DEFAULT_HIT_ZONE_TOLERANCE_MS = 60L

        // Hauteur de la hit zone en dp
        private const val HIT_ZONE_HEIGHT_DP = 6f

        // Marge minimale entre notes et bords
        private const val NOTE_MARGIN_DP = 1f

        // Rayon des coins des notes
        private const val NOTE_CORNER_RADIUS_DP = 6f

        // PERFORMANCE: Particules réduites (était 12)
        private const val PARTICLES_PER_HIT = 6
        private const val PARTICLE_LIFETIME_MS = 600L  // Réduit de 800
        private const val PARTICLE_MAX_SPEED = 6f      // Réduit de 8
        private const val MAX_PARTICLES = 160

        // PERFORMANCE: Glow simplifié (plus de BlurMaskFilter)
        private const val GLOW_RADIUS_DP = 6f  // Réduit de 8
        private const val GLOW_ALPHA = 0.3f    // Réduit de 0.4

        // Effets feu spécifiques (légers)
        private const val FIRE_AMBIENT_EMBER_INTERVAL_MS = 90L
        private const val FIRE_MAX_AMBIENT_EMBERS = 48
        private const val FIRE_EMBER_LIFETIME_MS = 1400L
        private const val FIRE_EMBER_BASE_SPEED = 1.6f
        private const val FIRE_EMBER_SPEED_VARIATION = 1.8f
        private const val FIRE_SPARKS_PER_BURST = 4
        private const val FIRE_SPARK_LIFETIME_MS = 720L

        private const val FIRE_FLAME_BURST_LIFETIME_MS = 480L
        private const val FIRE_FLAME_COOLDOWN_MS = 120L
        private const val FIRE_MAX_FLAME_BURSTS = 20
        private const val FIRE_FLAME_MAX_HEIGHT_RATIO = 0.22f
        private const val FIRE_FLAME_MIN_HEIGHT_DP = 28f
        private const val FIRE_FLAME_MAX_WIDTH_DP = 54f

        // Rising notes mode (free practice)
        private const val MAX_RISING_NOTES = 500
        private const val MIN_RISING_NOTE_DURATION_MS = 80L

        private val FIRE_EMBER_BASE_COLOR = "#FF9E3D".toColorInt()
        private val FIRE_EMBER_HOT_COLOR = "#FFD778".toColorInt()
        private val FIRE_FLAME_CORE_COLOR = "#FFF1A8".toColorInt()
        private val FIRE_FLAME_EDGE_COLOR = "#FF7A1A".toColorInt()

        // PERFORMANCE: Lookup table pour sin (évite calculs trigonométriques)
        private const val SIN_TABLE_SIZE = 64
        private val SIN_TABLE = FloatArray(SIN_TABLE_SIZE) { i ->
            kotlin.math.sin(i * 2.0 * Math.PI / SIN_TABLE_SIZE).toFloat()
        }
        private val COS_TABLE = FloatArray(SIN_TABLE_SIZE) { i ->
            kotlin.math.cos(i * 2.0 * Math.PI / SIN_TABLE_SIZE).toFloat()
        }

        // Couleurs arc-en-ciel LGBT pour les 12 demi-tons
        // Rouge → Orange → Jaune → Vert → Bleu → Violet
        // C, C#, D, D#, E, F, F#, G, G#, A, A#, B
        val CHROMATIC_COLORS = intArrayOf(
            0xFFE40303.toInt(),  // C  - Rouge
            0xFFFF5500.toInt(),  // C# - Rouge-Orange
            0xFFFF8C00.toInt(),  // D  - Orange
            0xFFFFBB00.toInt(),  // D# - Orange-Jaune
            0xFFFFED00.toInt(),  // E  - Jaune
            0xFF8BC34A.toInt(),  // F  - Jaune-Vert
            0xFF008026.toInt(),  // F# - Vert
            0xFF009688.toInt(),  // G  - Vert-Bleu
            0xFF004DFF.toInt(),  // G# - Bleu
            0xFF2E00FF.toInt(),  // A  - Bleu-Violet
            0xFF750787.toInt(),  // A# - Violet
            0xFFAA00AA.toInt()   // B  - Violet-Rouge
        )

        // Couleurs Néon
        val NEON_COLORS = intArrayOf(
            0xFFFF00FF.toInt(),  // C  - Magenta néon
            0xFFFF00CC.toInt(),  // C#
            0xFFFF0099.toInt(),  // D  - Rose néon
            0xFFFF0066.toInt(),  // D#
            0xFFFF3300.toInt(),  // E  - Orange néon
            0xFFFF6600.toInt(),  // F
            0xFFFFFF00.toInt(),  // F# - Jaune néon
            0xFF00FF00.toInt(),  // G  - Vert néon
            0xFF00FFFF.toInt(),  // G# - Cyan néon
            0xFF00CCFF.toInt(),  // A  - Bleu néon
            0xFF0099FF.toInt(),  // A#
            0xFF6600FF.toInt()   // B  - Violet néon
        )

        // Couleurs Océan (dégradé bleu-turquoise)
        val OCEAN_COLORS = intArrayOf(
            0xFF006994.toInt(),  // C  - Bleu océan profond
            0xFF0077A3.toInt(),  // C#
            0xFF0088B2.toInt(),  // D  - Bleu océan
            0xFF009999.toInt(),  // D#
            0xFF00A8A8.toInt(),  // E  - Turquoise
            0xFF00B8B8.toInt(),  // F
            0xFF00C8C8.toInt(),  // F# - Cyan
            0xFF00D4AA.toInt(),  // G  - Turquoise clair
            0xFF00E0CC.toInt(),  // G# - Aqua
            0xFF40E0D0.toInt(),  // A  - Turquoise
            0xFF48D1CC.toInt(),  // A#
            0xFF20B2AA.toInt()   // B  - Bleu-vert
        )

        // Couleurs Feu (dégradé rouge-orange-jaune)
        val FIRE_COLORS = intArrayOf(
            0xFFFF0000.toInt(),  // C  - Rouge feu
            0xFFFF1A00.toInt(),  // C#
            0xFFFF3300.toInt(),  // D  - Rouge-orange
            0xFFFF4D00.toInt(),  // D#
            0xFFFF6600.toInt(),  // E  - Orange
            0xFFFF8000.toInt(),  // F  - Orange vif
            0xFFFF9900.toInt(),  // F#
            0xFFFFB300.toInt(),  // G  - Or
            0xFFFFCC00.toInt(),  // G# - Jaune-or
            0xFFFFE600.toInt(),  // A  - Jaune
            0xFFFFFF00.toInt(),  // A# - Jaune vif
            0xFFFFFF66.toInt()   // B  - Jaune clair
        )
    }

    // Configuration
    private var noteRangeMin = 48
    private var noteRangeMax = 84
    private var lookaheadMs: Long = DEFAULT_LOOKAHEAD_MS
    private var currentPositionMs: Long = 0L
    private var targetChannel: Int = 0
    private var currentProgram: Int = 0
    private var hitZoneToleranceMs: Long = DEFAULT_HIT_ZONE_TOLERANCE_MS

    // Mode deux mains
    private var isTwoHandsMode: Boolean = false
    private var leftHandChannel: Int = -1
    private var rightHandChannel: Int = -1

    // Mode de pratique des mains (filtrage visuel uniquement)
    private var handPracticeMode: HandPracticeMode = HandPracticeMode.BOTH_HANDS

    // Couleurs pour main gauche/droite (bleu pour gauche, orange pour droite)
    private val leftHandColor = 0xFF4A90D9.toInt()   // Bleu
    private val rightHandColor = 0xFFFF6B35.toInt()  // Orange

    // Style visuel actuel (ancien système)
    private var visualStyle: VisualStyle = VisualStyle.CLASSIC

    // Nouveau système de thèmes (prend le dessus si activé)
    private var currentTheme: PracticeTheme? = null
    private var useThemeSystem: Boolean = false

    // Options visuelles (activées selon le style/thème)
    private var glowEnabled: Boolean = false
    private var particlesEnabled: Boolean = false
    private var gradientNotesEnabled: Boolean = false
    private var animatedBackgroundEnabled: Boolean = false
    private var customNoteShapeEnabled: Boolean = false

    // Notes à afficher
    private var scheduledNotes: List<ScheduledNote> = emptyList()

    // Mode rising notes (free practice)
    private var noteDirection: NoteDirection = NoteDirection.FALLING
    private val risingNotes = mutableListOf<ScheduledNote>()
    private val activeRisingNotes = mutableMapOf<Int, ActiveRisingNote>()
    private var risingModeStartTimeMs: Long = 0L

    private data class ActiveRisingNote(
        val startTimeMs: Long,
        val index: Int
    )

    // Système de particules
    private val particles = mutableListOf<Particle>()
    private val ambientEmbers = mutableListOf<Particle>()

    // Hit feedback animations (note -> hit time)
    private val hitFeedback = mutableMapOf<Int, HitFeedback>()

    // Bursts de flammes sur la timeline
    private val flameBursts = mutableListOf<FlameBurst>()
    private val flameBurstsByNote = mutableMapOf<Int, FlameBurst>()

    // Dimensions calculées
    private var noteWidth = 0f
    private var whiteKeyCount = 0
    private var hitZoneY = 0f
    private var hitZoneHeight = 0f
    private var noteMargin = 0f
    private var noteCornerRadius = 0f
    private var glowRadius = 0f

    // Mapping note -> position X
    private val notePositions = mutableMapOf<Int, Float>()

    // Notes dans la hit zone (pour callback)
    private val notesInHitZone = mutableSetOf<Int>()

    // Callback quand une note entre dans la hit zone (note, timeMs)
    var onNoteReachHitZone: ((note: Int, timeMs: Long) -> Unit)? = null
    var onNoteLeaveHitZone: ((note: Int) -> Unit)? = null

    // Horloge partagée pour synchronisation avec le thread audio
    private var sharedClock: PlaybackClock? = null

    // Animation avec Choreographer pour synchronisation vsync
    private var isAnimating = false
    private var animationStartTimeNs: Long = 0L
    private var animationStartPositionMs: Long = 0L
    private var playbackSpeed: Float = 1.0f

    // Pause animation (pour mode attente)
    private var isPaused = false
    private var pausedPositionMs: Long = 0L

    // Animation de la hit zone
    private var hitZonePulse = 0f
    private var lastFrameTimeMs = 0L
    private var lastAmbientEmberSpawnMs = 0L
    private var fireSeed = 0x6D2B79F5

    // Animation du background (séparée de l'animation des notes)
    private var isBackgroundAnimating = false
    private var lastBackgroundFrameTimeMs = 0L

    private val choreographer = Choreographer.getInstance()

    // Callback dédié à l'animation du background (tourne indépendamment des notes)
    private val backgroundFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isBackgroundAnimating || !animatedBackgroundEnabled) return

            val currentTimeMs = System.currentTimeMillis()
            var deltaMs = if (lastBackgroundFrameTimeMs > 0) currentTimeMs - lastBackgroundFrameTimeMs else 16L
            lastBackgroundFrameTimeMs = currentTimeMs

            // Limiter deltaMs pour éviter les sauts d'animation lors de pauses/redimensionnements
            // (max ~3 frames à 60fps)
            deltaMs = deltaMs.coerceAtMost(50L)

            // Mettre à jour l'animation du thème (fond animé)
            currentTheme?.updateBackgroundAnimation(deltaMs)

            // Redraw seulement si on n'est pas déjà en animation de notes
            if (!isAnimating || isPaused) {
                invalidate()
            }

            if (isBackgroundAnimating && animatedBackgroundEnabled) {
                choreographer.postFrameCallback(this)
            }
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAnimating || isPaused) return

            val currentTimeMs = System.currentTimeMillis()
            val deltaMs = if (lastFrameTimeMs > 0) currentTimeMs - lastFrameTimeMs else 16L
            lastFrameTimeMs = currentTimeMs

            // Utiliser l'horloge partagée si disponible, sinon fallback sur le calcul local
            val clock = sharedClock
            if (clock != null && clock.isRunning()) {
                // Synchronisation via l'horloge partagée (même source que le thread audio)
                currentPositionMs = clock.getCurrentPositionMs()
            } else {
                // Fallback: calculer la position basée sur le temps écoulé local
                val elapsedNs = frameTimeNanos - animationStartTimeNs
                val elapsedMs = (elapsedNs / 1_000_000L * playbackSpeed).toLong()
                currentPositionMs = animationStartPositionMs + elapsedMs
            }

            // Mettre à jour les particules
            updateParticles()

            // Mettre à jour les hit feedbacks
            updateHitFeedbacks(currentTimeMs)

            // Effets feu spécifiques (braises + flammes)
            updateFireEffects(deltaMs, currentTimeMs)

            // Note: l'animation du background est gérée par backgroundFrameCallback
            // pour qu'elle tourne même quand les notes ne défilent pas

            // Mode rising notes: mettre à jour les notes actives et nettoyer
            if (noteDirection == NoteDirection.RISING) {
                updateActiveRisingNotes()
                cleanupOldRisingNotes()
            }

            // Animation pulse de la hit zone
            hitZonePulse = (hitZonePulse + deltaMs * 0.005f) % (2 * Math.PI.toFloat())

            // Ne pas vérifier les notes hit zone en mode rising (pas de validation)
            if (noteDirection == NoteDirection.FALLING) {
                checkHitZoneNotes()
            }
            invalidate()

            if (isAnimating && !isPaused) {
                choreographer.postFrameCallback(this)
            }
        }
    }

    // Paints pour le rendu
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val noteGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val noteOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // Paint pour le contour de main (mode deux mains)
    private val handOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val hitZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val hitZoneGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#15FFFFFF".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val hitFeedbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val flameGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Rectangle réutilisable
    private val noteRect = RectF()
    private val glowRect = RectF()
    private val flameRect = RectF()
    private val flamePath = Path()

    // PERFORMANCE: Paint pré-alloué pour les highlights (évite allocation dans onDraw)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // PERFORMANCE: Shader caché pour la hit zone
    private var cachedHitZoneShader: LinearGradient? = null
    private var cachedHitZoneWidth = 0
    private var cachedHitZoneColor = 0

    // PERFORMANCE: Fonctions sin/cos rapides utilisant lookup table
    private fun fastSin(radians: Float): Float {
        val normalized = ((radians / (2 * Math.PI.toFloat())) % 1f + 1f) % 1f
        return SIN_TABLE[(normalized * SIN_TABLE_SIZE).toInt().coerceIn(0, SIN_TABLE_SIZE - 1)]
    }

    private fun fastCos(radians: Float): Float {
        val normalized = ((radians / (2 * Math.PI.toFloat())) % 1f + 1f) % 1f
        return COS_TABLE[(normalized * SIN_TABLE_SIZE).toInt().coerceIn(0, SIN_TABLE_SIZE - 1)]
    }

    private fun nextFireFloat(): Float {
        fireSeed = fireSeed * 1664525 + 1013904223
        val positive = fireSeed ushr 1
        return (positive % 10_000) / 10_000f
    }

    private fun isFireThemeActive(): Boolean {
        return if (useThemeSystem) {
            when (val theme = currentTheme) {
                is CustomBackgroundTheme -> theme.getBaseTheme().id == "fire"
                null -> false
                else -> theme.id == "fire"
            }
        } else {
            visualStyle == VisualStyle.FIRE
        }
    }

    init {
        // Par défaut: layer hardware (style classique sans blur)
        // Le layer software sera activé si on choisit un style avec effets
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Configure la plage de notes (doit correspondre au clavier)
     */
    fun setNoteRange(minNote: Int, maxNote: Int) {
        noteRangeMin = minNote.coerceIn(0, 120)
        noteRangeMax = maxNote.coerceIn(12, 127)
        calculateDimensions()
        invalidate()
    }

    /**
     * Définit le canal cible (pour les couleurs en mode non-rainbow)
     */
    fun setTargetChannel(channel: Int) {
        targetChannel = channel
        invalidate()
    }

    /**
     * Active le mode deux mains avec couleurs distinctes pour chaque main
     */
    fun setTwoHandsMode(enabled: Boolean, leftChannel: Int = -1, rightChannel: Int = -1) {
        isTwoHandsMode = enabled
        leftHandChannel = leftChannel
        rightHandChannel = rightChannel
        invalidate()
    }

    /**
     * Définit le mode de pratique des mains (filtrage visuel uniquement, n'affecte pas l'audio)
     * Permet de n'afficher et valider que les notes d'une main spécifique
     */
    fun setHandPracticeMode(mode: HandPracticeMode) {
        handPracticeMode = mode
        invalidate()
    }

    /**
     * Retourne le mode de pratique des mains actuel
     */
    @Suppress("unused")
    fun getHandPracticeMode(): HandPracticeMode = handPracticeMode

    /**
     * Définit le programme actuel
     */
    fun setCurrentProgram(program: Int) {
        currentProgram = program
        invalidate()
    }

    /**
     * Définit le style visuel
     */
    @Suppress("unused")
    fun setVisualStyle(style: VisualStyle) {
        visualStyle = style

        // Configurer les options selon le style
        // PERFORMANCE: Toujours utiliser LAYER_TYPE_HARDWARE (GPU)
        // Les effets de blur ont été remplacés par des alternatives plus légères
        when (style) {
            VisualStyle.CLASSIC -> {
                glowEnabled = false
                particlesEnabled = false
                gradientNotesEnabled = false
            }
            VisualStyle.RAINBOW, VisualStyle.NEON -> {
                glowEnabled = true
                particlesEnabled = true
                gradientNotesEnabled = true
            }
            VisualStyle.OCEAN, VisualStyle.FIRE -> {
                glowEnabled = true
                particlesEnabled = true
                gradientNotesEnabled = true
            }
        }
        // PERFORMANCE: Toujours hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)

        resetFireEffects()
        invalidate()
    }

    /**
     * Retourne le style visuel actuel
     */
    @Suppress("unused")
    fun getVisualStyle(): VisualStyle = visualStyle

    /**
     * Définit le thème visuel
     * Prend le dessus sur setVisualStyle
     */
    fun setTheme(theme: PracticeTheme) {
        currentTheme = theme
        useThemeSystem = true

        // Configurer les options selon le thème
        glowEnabled = theme.hasGlowEffect()
        particlesEnabled = theme.hasParticles()
        gradientNotesEnabled = true  // Toujours activé pour le nouveau système
        animatedBackgroundEnabled = theme.hasAnimatedBackground()
        customNoteShapeEnabled = theme.hasCustomNoteShape()

        // PERFORMANCE: Toujours utiliser hardware acceleration
        // Les effets de blur ont été remplacés par des alternatives légères
        setLayerType(LAYER_TYPE_HARDWARE, null)

        // Forcer la mise à jour du fond
        lastBackgroundStyle = null

        resetFireEffects()

        // Démarrer/arrêter l'animation du background selon le thème
        if (animatedBackgroundEnabled) {
            startBackgroundAnimation()
        } else {
            stopBackgroundAnimation()
        }

        invalidate()
    }

    /**
     * Applique le thème actuel du PracticeThemeManager
     */
    @Suppress("unused")
    fun applyCurrentTheme() {
        setTheme(PracticeThemeManager.getCurrentTheme())
    }

    /**
     * Retourne le thème actuel (ou null si ancien système)
     */
    @Suppress("unused")
    fun getCurrentTheme(): PracticeTheme? = currentTheme

    /**
     * Rafraîchit les couleurs (après modification dans les paramètres)
     */
    fun refreshColors() {
        lastBackgroundStyle = null
        resetFireEffects()
        invalidate()
    }

    private fun resetFireEffects() {
        lastAmbientEmberSpawnMs = 0L
        ambientEmbers.clear()
        flameBursts.clear()
        flameBurstsByNote.clear()
    }

    /**
     * Définit les notes programmées
     */
    fun setNotes(notes: List<ScheduledNote>) {
        scheduledNotes = notes
        invalidate()
    }

    /**
     * Déclenche un effet de hit réussi sur une note
     */
    fun triggerHitEffect(note: Int, isSuccess: Boolean) {
        // Les effets ne sont affichés que si les particules sont activées
        if (!particlesEnabled) return

        val noteX = notePositions[note] ?: return
        val isBlack = isBlackKey(note)
        val actualNoteWidth = if (isBlack) noteWidth * 0.7f else noteWidth
        val centerX = noteX + actualNoteWidth / 2
        val centerY = hitZoneY

        if (isSuccess) {
            // Créer des particules pour un hit réussi
            val baseColor = getNoteBaseColor(note)
            spawnParticles(centerX, centerY, baseColor)

            // Burst de flamme plus marqué pour le thème feu
            if (isFireThemeActive()) {
                spawnFlameBurst(note, 1.15f)
            }

            // Ajouter un feedback visuel
            hitFeedback[note] = HitFeedback(
                note = note,
                startTimeMs = System.currentTimeMillis(),
                x = centerX,
                y = centerY,
                color = baseColor,
                isSuccess = true
            )
        } else {
            // Feedback d'erreur (plus subtil)
            hitFeedback[note] = HitFeedback(
                note = note,
                startTimeMs = System.currentTimeMillis(),
                x = centerX,
                y = centerY,
                color = "#FF5252".toColorInt(),
                isSuccess = false
            )
        }

        invalidate()
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
            checkHitZoneNotes()
            invalidate()
        }
    }

    /**
     * Démarre l'animation fluide synchronisée avec le vsync
     */
    fun startAnimation(fromPositionMs: Long, speed: Float = 1.0f) {
        if (!isAnimating) {
            isAnimating = true
            animationStartTimeNs = System.nanoTime()
            animationStartPositionMs = fromPositionMs
            playbackSpeed = speed
            currentPositionMs = fromPositionMs
            lastFrameTimeMs = System.currentTimeMillis()
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
     * Démarre l'animation du background (indépendante des notes)
     * Appelée automatiquement quand un thème avec background animé est activé
     */
    private fun startBackgroundAnimation() {
        if (!isBackgroundAnimating) {
            isBackgroundAnimating = true
            lastBackgroundFrameTimeMs = System.currentTimeMillis()
            choreographer.postFrameCallback(backgroundFrameCallback)
        }
    }

    /**
     * Arrête l'animation du background
     */
    private fun stopBackgroundAnimation() {
        isBackgroundAnimating = false
        choreographer.removeFrameCallback(backgroundFrameCallback)
    }

    /**
     * Met en pause l'animation (pour mode attente)
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
            lastFrameTimeMs = System.currentTimeMillis()
            choreographer.postFrameCallback(frameCallback)
        }
    }

    /**
     * Retourne true si l'animation est en pause
     */
    @Suppress("unused")
    fun isAnimationPaused(): Boolean = isPaused

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
     * Définit la durée d'anticipation
     */
    @Suppress("unused")
    fun setLookaheadMs(ms: Long) {
        lookaheadMs = ms.coerceIn(1000L, 10000L)
        invalidate()
    }

    /**
     * Définit la tolérance de la hit zone (en ms)
     */
    fun setHitZoneToleranceMs(ms: Long) {
        hitZoneToleranceMs = ms.coerceIn(10L, 500L)
    }

    /**
     * Retourne la durée d'anticipation actuelle
     */
    @Suppress("unused")
    fun getLookaheadMs(): Long = lookaheadMs

    /**
     * Définit l'horloge partagée pour synchronisation avec le thread audio.
     * Si définie, la vue utilisera cette horloge au lieu de calculer sa propre position.
     */
    fun setSharedClock(clock: PlaybackClock?) {
        sharedClock = clock
    }

    // ===================== RISING NOTES MODE (FREE PRACTICE) =====================

    /**
     * Définit la direction des notes (falling pour MIDI playback, rising pour free practice)
     */
    fun setNoteDirection(direction: NoteDirection) {
        noteDirection = direction
        if (direction == NoteDirection.RISING) {
            scheduledNotes = emptyList()
            risingModeStartTimeMs = System.currentTimeMillis()
        } else {
            clearRisingNotes()
        }
        invalidate()
    }

    /**
     * Ajoute une note montante (appelé quand l'utilisateur appuie sur une touche en free practice)
     */
    fun addRisingNote(note: Int, velocity: Int) {
        if (noteDirection != NoteDirection.RISING) return

        val now = System.currentTimeMillis()
        val relativeTimeMs = now - risingModeStartTimeMs

        // Finaliser une note existante pour cette touche (si elle était maintenue)
        finalizeRisingNote(note, relativeTimeMs)

        // Limiter le nombre de notes en mémoire
        if (risingNotes.size >= MAX_RISING_NOTES) {
            risingNotes.removeAt(0)
            // Ajuster les index des notes actives
            val toRemove = mutableListOf<Int>()
            activeRisingNotes.forEach { (key, active) ->
                if (active.index == 0) {
                    toRemove.add(key)
                } else {
                    activeRisingNotes[key] = active.copy(index = active.index - 1)
                }
            }
            toRemove.forEach { activeRisingNotes.remove(it) }
        }

        val scheduledNote = ScheduledNote(
            note = note,
            startTimeMs = relativeTimeMs,
            durationMs = MIN_RISING_NOTE_DURATION_MS,
            velocity = velocity,
            channel = 0,
            isLeftHand = false
        )
        risingNotes.add(scheduledNote)
        activeRisingNotes[note] = ActiveRisingNote(relativeTimeMs, risingNotes.lastIndex)
    }

    /**
     * Relâche une note montante (appelé quand l'utilisateur relâche une touche)
     */
    fun releaseRisingNote(note: Int) {
        if (noteDirection != NoteDirection.RISING) return
        val now = System.currentTimeMillis()
        val relativeTimeMs = now - risingModeStartTimeMs
        finalizeRisingNote(note, relativeTimeMs)
    }

    /**
     * Efface toutes les notes montantes
     */
    fun clearRisingNotes() {
        risingNotes.clear()
        activeRisingNotes.clear()
        risingModeStartTimeMs = System.currentTimeMillis()
    }

    /**
     * Démarre l'animation en mode rising (free practice)
     */
    fun startRisingAnimation() {
        if (noteDirection != NoteDirection.RISING) return
        if (isAnimating) return
        isAnimating = true
        risingModeStartTimeMs = System.currentTimeMillis()
        lastFrameTimeMs = System.currentTimeMillis()
        choreographer.postFrameCallback(frameCallback)
    }

    /**
     * Finalise une note montante (calcule sa durée finale)
     */
    private fun finalizeRisingNote(note: Int, endTimeMs: Long) {
        val active = activeRisingNotes.remove(note) ?: return
        val existing = risingNotes.getOrNull(active.index) ?: return
        val duration = (endTimeMs - active.startTimeMs).coerceAtLeast(MIN_RISING_NOTE_DURATION_MS)
        risingNotes[active.index] = existing.copy(durationMs = duration)
    }

    /**
     * Met à jour les notes montantes actives (pendant l'appui continu)
     */
    private fun updateActiveRisingNotes() {
        if (activeRisingNotes.isEmpty()) return
        val relativeTimeMs = System.currentTimeMillis() - risingModeStartTimeMs

        activeRisingNotes.forEach { (_, active) ->
            val existing = risingNotes.getOrNull(active.index) ?: return@forEach
            val newDuration = (relativeTimeMs - active.startTimeMs).coerceAtLeast(MIN_RISING_NOTE_DURATION_MS)
            risingNotes[active.index] = existing.copy(durationMs = newDuration)
        }
    }

    /**
     * Nettoie les anciennes notes montantes (hors écran)
     */
    private fun cleanupOldRisingNotes() {
        val relativeTimeMs = System.currentTimeMillis() - risingModeStartTimeMs
        val cutoffMs = relativeTimeMs - lookaheadMs - 500

        var removedCount = 0
        val iterator = risingNotes.iterator()
        while (iterator.hasNext()) {
            val note = iterator.next()
            if (note.endTimeMs < cutoffMs) {
                iterator.remove()
                removedCount++
            } else {
                break // Les notes sont ordonnées par temps, on peut s'arrêter
            }
        }

        // Ajuster les index des notes actives
        if (removedCount > 0) {
            val toRemove = mutableListOf<Int>()
            activeRisingNotes.forEach { (key, active) ->
                val newIndex = active.index - removedCount
                if (newIndex < 0) {
                    toRemove.add(key)
                } else {
                    activeRisingNotes[key] = active.copy(index = newIndex)
                }
            }
            toRemove.forEach { activeRisingNotes.remove(it) }
        }
    }

    private fun isBlackKey(note: Int): Boolean {
        return (note % 12) in setOf(1, 3, 6, 8, 10)
    }

    private fun countWhiteKeys(): Int {
        var count = 0
        for (note in noteRangeMin..noteRangeMax) {
            if (!isBlackKey(note)) count++
        }
        return count
    }

    private fun calculateDimensions() {
        whiteKeyCount = countWhiteKeys()
        if (whiteKeyCount == 0 || width == 0 || height == 0) return

        val density = resources.displayMetrics.density
        hitZoneHeight = HIT_ZONE_HEIGHT_DP * density
        noteMargin = NOTE_MARGIN_DP * density
        noteCornerRadius = NOTE_CORNER_RADIUS_DP * density
        glowRadius = GLOW_RADIUS_DP * density

        hitZoneY = height - hitZoneHeight

        val whiteKeyWidth = width.toFloat() / whiteKeyCount
        val blackKeyWidth = whiteKeyWidth * 0.6f

        notePositions.clear()
        var whiteKeyIndex = 0

        for (note in noteRangeMin..noteRangeMax) {
            if (!isBlackKey(note)) {
                // Position alignée exactement avec les touches du clavier
                // (même calcul que InteractivePianoView)
                val x = whiteKeyIndex * whiteKeyWidth
                notePositions[note] = x

                val nextNote = note + 1
                if (nextNote <= noteRangeMax && isBlackKey(nextNote)) {
                    // Touche noire centrée sur la frontière entre deux touches blanches
                    // (même calcul que InteractivePianoView)
                    val blackX = (whiteKeyIndex + 1) * whiteKeyWidth - (blackKeyWidth / 2)
                    notePositions[nextNote] = blackX
                }

                whiteKeyIndex++
            }
        }

        // Largeur de la note : touche blanche complète moins marge visuelle
        noteWidth = whiteKeyWidth - (noteMargin * 2)

        // PERFORMANCE: Plus de BlurMaskFilter - ils nécessitent LAYER_TYPE_SOFTWARE
        // Le glow est maintenant simulé avec des cercles/rectangles semi-transparents
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions()

        // Créer le gradient de fond (plus sombre et élégant)
        backgroundPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            "#0D0D1A".toColorInt(),
            "#1A1A2E".toColorInt(),
            Shader.TileMode.CLAMP
        )

        fireSeed = (w * 73856093) xor (h * 19349663)
        resetFireEffects()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Mettre à jour le fond selon le style si nécessaire
        updateBackgroundIfNeeded()

        // Fond gradient sombre (de base)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Fond animé du thème (si activé)
        if (animatedBackgroundEnabled && useThemeSystem) {
            currentTheme?.drawAnimatedBackground(canvas, width, height)
        }

        if (whiteKeyCount == 0) return

        // Lignes de grille verticales subtiles
        drawGridLines(canvas)

        // Notes tombantes
        drawFallingNotes(canvas)

        // Éléments de premier plan du thème (après les notes)
        if (animatedBackgroundEnabled && useThemeSystem) {
            currentTheme?.drawForegroundElements(canvas, width, height)
        }

        // Effets feu spécifiques (braises + flammes sur la timeline)
        if (isFireThemeActive()) {
            drawFireEffects(canvas)
        }

        // Particules (si activées)
        if (particlesEnabled) {
            drawParticles(canvas)
            drawHitFeedbacks(canvas)
        }

        // Hit zone
        drawHitZone(canvas)
    }

    private var lastBackgroundStyle: VisualStyle? = null

    private fun updateBackgroundIfNeeded() {
        if (lastBackgroundStyle != visualStyle && height > 0) {
            val (topColor, bottomColor) = getBackgroundColors()
            backgroundPaint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                topColor,
                bottomColor,
                Shader.TileMode.CLAMP
            )
            lastBackgroundStyle = visualStyle
        }
    }

    private fun drawGridLines(canvas: Canvas) {
        val whiteKeyWidth = width.toFloat() / whiteKeyCount

        for (i in 1 until whiteKeyCount) {
            val x = i * whiteKeyWidth
            canvas.drawLine(x, 0f, x, hitZoneY, gridLinePaint)
        }
    }

    private fun drawFallingNotes(canvas: Canvas) {
        // Choisir les notes selon le mode
        val notesToDraw = when (noteDirection) {
            NoteDirection.FALLING -> scheduledNotes
            NoteDirection.RISING -> risingNotes
        }
        if (notesToDraw.isEmpty()) return

        val animationHeight = hitZoneY
        val pixelsPerMs = animationHeight / lookaheadMs

        // Temps de référence selon le mode
        val referenceTimeMs = when (noteDirection) {
            NoteDirection.FALLING -> currentPositionMs
            NoteDirection.RISING -> System.currentTimeMillis() - risingModeStartTimeMs
        }

        for (note in notesToDraw) {
            // Filtrage de visibilité selon la direction
            when (noteDirection) {
                NoteDirection.FALLING -> {
                    if (note.endTimeMs < referenceTimeMs) continue
                    if (note.startTimeMs > referenceTimeMs + lookaheadMs) continue
                }
                NoteDirection.RISING -> {
                    val ageMs = referenceTimeMs - note.startTimeMs
                    if (ageMs > lookaheadMs) continue  // Trop vieille, hors écran
                    if (ageMs < -100) continue  // Pas encore jouée
                }
            }

            // Filtrage par main en mode deux mains (seulement en mode falling)
            if (noteDirection == NoteDirection.FALLING && isTwoHandsMode) {
                when (handPracticeMode) {
                    HandPracticeMode.LEFT_HAND_ONLY -> if (!note.isLeftHand) continue
                    HandPracticeMode.RIGHT_HAND_ONLY -> if (note.isLeftHand) continue
                    HandPracticeMode.BOTH_HANDS -> { /* Afficher toutes les notes */ }
                }
            }

            val noteBaseX = notePositions[note.note] ?: continue
            // Ajouter la marge pour centrer visuellement la note dans la touche
            val noteX = noteBaseX + noteMargin

            // Calcul des coordonnées Y selon la direction
            val (bottomY, topY) = when (noteDirection) {
                NoteDirection.FALLING -> {
                    val startRelativeMs = note.startTimeMs - referenceTimeMs
                    val endRelativeMs = note.endTimeMs - referenceTimeMs
                    Pair(
                        hitZoneY - (startRelativeMs * pixelsPerMs),
                        hitZoneY - (endRelativeMs * pixelsPerMs)
                    )
                }
                NoteDirection.RISING -> {
                    // En mode rising : les notes montent du bas (hitZoneY) vers le haut (0)
                    // ageMs = temps écoulé depuis le début de la note (quand la touche a été appuyée)
                    val ageMs = referenceTimeMs - note.startTimeMs
                    // ageOfEndMs = temps écoulé depuis la fin de la note (quand la touche a été relâchée)
                    // Si la note est encore maintenue, endTimeMs = startTimeMs + durationMs qui est mis à jour
                    val ageOfEndMs = referenceTimeMs - note.endTimeMs

                    // Le haut de la note (partie la plus ancienne) = position du startTime
                    // Plus ageMs est grand, plus le haut est proche de Y=0
                    val noteTop = hitZoneY - (ageMs * pixelsPerMs)

                    // Le bas de la note (partie la plus récente) = position du endTime
                    // Si la note est maintenue, ageOfEndMs ≈ 0, donc noteBottom ≈ hitZoneY
                    val noteBottom = hitZoneY - (ageOfEndMs * pixelsPerMs)

                    Pair(noteBottom, noteTop)
                }
            }

            // Pour glow intensity calculation
            val startRelativeMs = when (noteDirection) {
                NoteDirection.FALLING -> note.startTimeMs - referenceTimeMs
                NoteDirection.RISING -> referenceTimeMs - note.startTimeMs
            }

            val clippedTop = topY.coerceAtLeast(0f)
            val clippedBottom = bottomY.coerceAtMost(hitZoneY)

            if (clippedBottom <= clippedTop) continue

            val isBlack = isBlackKey(note.note)
            val actualNoteWidth = if (isBlack) noteWidth * 0.7f else noteWidth
            // Toujours utiliser la couleur du thème
            val baseColor = getNoteBaseColor(note.note)
            val pitchClass = note.note % 12

            // Couleur du contour pour mode deux mains (null si pas en mode deux mains)
            val handOutlineColor: Int? = if (isTwoHandsMode) {
                if (note.isLeftHand) leftHandColor else rightHandColor
            } else null

            // Rectangle de la note
            noteRect.set(noteX, clippedTop, noteX + actualNoteWidth, clippedBottom)

            // Si le thème a des formes personnalisées (ex: bulles), déléguer le dessin
            if (customNoteShapeEnabled && useThemeSystem) {
                currentTheme?.let { theme ->
                    // Glow pour les thèmes avec formes personnalisées
                    if (theme.hasGlowEffect()) {
                        val distanceToHitZone = abs(startRelativeMs)
                        val glowIntensity = if (distanceToHitZone < 500) {
                            1f - (distanceToHitZone / 500f) * 0.5f
                        } else {
                            0.5f
                        }
                        if (glowIntensity > 0.3f) {
                            glowRect.set(
                                noteRect.left - glowRadius / 2,
                                noteRect.top - glowRadius / 2,
                                noteRect.right + glowRadius / 2,
                                noteRect.bottom + glowRadius / 2
                            )
                            noteGlowPaint.color = adjustAlpha(baseColor, (theme.getGlowIntensity() * glowIntensity).coerceIn(0f, 1f))
                            canvas.drawRoundRect(glowRect, noteCornerRadius * 1.5f, noteCornerRadius * 1.5f, noteGlowPaint)
                        }
                    }

                    // Dessiner la note avec la forme personnalisée du thème
                    theme.drawNote(canvas, noteRect, pitchClass, note.velocity, notePaint, noteCornerRadius)

                    // Dessiner le contour de main en mode deux mains (après la note)
                    handOutlineColor?.let { outlineColor ->
                        handOutlinePaint.color = outlineColor
                        canvas.drawRoundRect(noteRect, noteCornerRadius, noteCornerRadius, handOutlinePaint)
                    }
                }
                continue  // Passer au note suivante
            }

            if (glowEnabled) {
                // Calculer l'intensité du glow basée sur la proximité de la hit zone
                val distanceToHitZone = abs(startRelativeMs)
                val glowIntensity = if (distanceToHitZone < 500) {
                    1f - (distanceToHitZone / 500f) * 0.5f
                } else {
                    0.5f
                }

                // Dessiner le glow
                if (glowIntensity > 0.3f) {
                    glowRect.set(
                        noteRect.left - glowRadius / 2,
                        noteRect.top - glowRadius / 2,
                        noteRect.right + glowRadius / 2,
                        noteRect.bottom + glowRadius / 2
                    )
                    noteGlowPaint.color = adjustAlpha(baseColor, (GLOW_ALPHA * glowIntensity).coerceIn(0f, 1f))
                    canvas.drawRoundRect(glowRect, noteCornerRadius * 1.5f, noteCornerRadius * 1.5f, noteGlowPaint)
                }
            }

            if (gradientNotesEnabled) {
                // PERFORMANCE: Dessiner la note avec couleur simple + effet 3D léger
                // Plus de LinearGradient par note - trop coûteux !

                // Couleur principale
                notePaint.color = baseColor
                canvas.drawRoundRect(noteRect, noteCornerRadius, noteCornerRadius, notePaint)

                // PERFORMANCE: Effet 3D simplifié - juste un highlight en haut
                // Utiliser le highlightPaint pré-alloué au lieu de créer un nouveau Paint
                if (noteRect.height() > noteCornerRadius * 2) {
                    highlightPaint.color = Color.argb(40, 255, 255, 255)
                    highlightPaint.shader = null
                    glowRect.set(
                        noteRect.left + 1,
                        noteRect.top + 1,
                        noteRect.right - 1,
                        noteRect.top + noteCornerRadius
                    )
                    canvas.drawRoundRect(glowRect, noteCornerRadius - 1, noteCornerRadius - 1, highlightPaint)
                }

                // Contour subtil (optionnel, léger)
                noteOutlinePaint.color = Color.argb(60, 0, 0, 0)
                canvas.drawRoundRect(noteRect, noteCornerRadius, noteCornerRadius, noteOutlinePaint)
            } else {
                // Style classique: note simple avec couleur unie
                notePaint.color = baseColor

                // Ajuster la luminosité selon la vélocité (style classique)
                val brightness = 0.6f + (note.velocity / 127f) * 0.4f
                val r = ((baseColor shr 16 and 0xFF) * brightness).toInt().coerceIn(0, 255)
                val g = ((baseColor shr 8 and 0xFF) * brightness).toInt().coerceIn(0, 255)
                val b = ((baseColor and 0xFF) * brightness).toInt().coerceIn(0, 255)

                // Notes noires légèrement plus sombres
                val factor = if (isBlack) 0.85f else 1f
                notePaint.color = Color.rgb(
                    (r * factor).toInt(),
                    (g * factor).toInt(),
                    (b * factor).toInt()
                )

                canvas.drawRoundRect(noteRect, noteCornerRadius, noteCornerRadius, notePaint)

                // Contour subtil
                noteOutlinePaint.color = "#40000000".toColorInt()
                noteOutlinePaint.alpha = 255
                canvas.drawRoundRect(noteRect, noteCornerRadius, noteCornerRadius, noteOutlinePaint)
            }

            // Dessiner le contour de main en mode deux mains (après la note)
            handOutlineColor?.let { outlineColor ->
                handOutlinePaint.color = outlineColor
                canvas.drawRoundRect(noteRect, noteCornerRadius, noteCornerRadius, handOutlinePaint)
            }
        }
    }

    private fun drawHitZone(canvas: Canvas) {
        val hitZoneColor = getHitZoneColor()

        if (glowEnabled) {
            // PERFORMANCE: Utiliser fastSin au lieu de sin
            val pulseIntensity = 0.5f + 0.5f * fastSin(hitZonePulse)

            // Glow de la hit zone (sans blur)
            val glowColor = Color.argb(
                (60 * pulseIntensity).toInt(),  // Réduit de 80
                Color.red(hitZoneColor),
                Color.green(hitZoneColor),
                Color.blue(hitZoneColor)
            )
            hitZoneGlowPaint.color = glowColor
            canvas.drawRect(0f, hitZoneY - glowRadius, width.toFloat(), hitZoneY + hitZoneHeight + glowRadius, hitZoneGlowPaint)

            // PERFORMANCE: Cache le shader de la hit zone (ne pas recréer chaque frame)
            if (cachedHitZoneShader == null || cachedHitZoneWidth != width || cachedHitZoneColor != hitZoneColor) {
                cachedHitZoneWidth = width
                cachedHitZoneColor = hitZoneColor
                cachedHitZoneShader = LinearGradient(
                    0f, hitZoneY, width.toFloat(), hitZoneY,
                    intArrayOf(
                        hitZoneColor,
                        lightenColor(hitZoneColor, 0.3f),
                        hitZoneColor
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            hitZonePaint.shader = cachedHitZoneShader
            hitZonePaint.alpha = (200 * pulseIntensity + 55).toInt()
            canvas.drawRect(0f, hitZoneY, width.toFloat(), hitZoneY + hitZoneHeight, hitZonePaint)
        } else {
            // Style classique: ligne simple
            hitZonePaint.shader = null
            hitZonePaint.color = hitZoneColor
            hitZonePaint.alpha = 255
            canvas.drawRect(0f, hitZoneY, width.toFloat(), hitZoneY + hitZoneHeight, hitZonePaint)
        }
    }

    private fun drawFireEffects(canvas: Canvas) {
        if (ambientEmbers.isEmpty() && flameBursts.isEmpty()) return

        val nowMs = System.currentTimeMillis()
        val activity = computeFireActivity(nowMs)

        drawKeyboardFireGlow(canvas, nowMs, activity)
        drawFlameBursts(canvas, nowMs)
        drawAmbientEmbers(canvas, nowMs, activity)
    }

    private fun computeFireActivity(nowMs: Long): Float {
        if (flameBursts.isEmpty()) return 0f

        var total = 0f
        for (burst in flameBursts) {
            val age = nowMs - burst.startTimeMs
            if (age < 0) continue
            val progress = (age.toFloat() / FIRE_FLAME_BURST_LIFETIME_MS).coerceIn(0f, 1f)
            val life = 1f - progress
            total += burst.intensity * life
        }

        return (total / flameBursts.size.coerceAtLeast(1)).coerceIn(0f, 1.4f)
    }

    private fun drawKeyboardFireGlow(canvas: Canvas, nowMs: Long, activity: Float) {
        val density = resources.displayMetrics.density
        val glowHeight = 56f * density
        val pulse = 0.65f + 0.35f * fastSin((nowMs * 0.0045f) % (2 * Math.PI.toFloat()))
        val intensity = (0.35f + activity * 0.5f) * pulse

        val baseAlpha = (40 + intensity * 70).toInt().coerceIn(25, 120)
        val warmColor = adjustAlpha(FIRE_FLAME_EDGE_COLOR, baseAlpha / 255f)
        val hotColor = adjustAlpha(FIRE_FLAME_CORE_COLOR, (baseAlpha + 40).coerceAtMost(180) / 255f)

        flameGlowPaint.shader = LinearGradient(
            0f,
            hitZoneY + hitZoneHeight + glowHeight * 0.2f,
            0f,
            hitZoneY - glowHeight,
            intArrayOf(warmColor, hotColor, Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(
            0f,
            hitZoneY - glowHeight,
            width.toFloat(),
            hitZoneY + hitZoneHeight + glowHeight * 0.2f,
            flameGlowPaint
        )

        flameGlowPaint.shader = null
    }

    private fun drawFlameBursts(canvas: Canvas, nowMs: Long) {
        val minTop = hitZoneY - hitZoneY * FIRE_FLAME_MAX_HEIGHT_RATIO

        for (burst in flameBursts) {
            val age = nowMs - burst.startTimeMs
            if (age < 0) continue

            val progress = (age.toFloat() / FIRE_FLAME_BURST_LIFETIME_MS).coerceIn(0f, 1f)
            val life = (1f - progress).coerceAtLeast(0f)
            if (life <= 0f) continue

            val height = (burst.baseHeight * (0.45f + 0.55f * life)).coerceAtMost(hitZoneY * 0.55f)
            val widthScale = 0.65f + 0.35f * life
            val flameWidth = burst.baseWidth * widthScale
            val bottom = hitZoneY + hitZoneHeight * 0.55f
            val top = (bottom - height).coerceAtLeast(minTop)

            val sway = fastSin((nowMs * 0.0075f + burst.phaseOffset) % (2 * Math.PI.toFloat())) * flameWidth * 0.18f
            val alpha = (0.2f + life * 0.8f) * burst.intensity

            flameRect.set(
                burst.x - flameWidth / 2,
                top,
                burst.x + flameWidth / 2,
                bottom
            )

            val gradient = LinearGradient(
                burst.x,
                bottom,
                burst.x,
                top,
                intArrayOf(
                    adjustAlpha(FIRE_FLAME_CORE_COLOR, (0.9f * alpha).coerceIn(0f, 1f)),
                    adjustAlpha(lightenColor(burst.color, 0.35f), (0.8f * alpha).coerceIn(0f, 1f)),
                    adjustAlpha(burst.color, (0.6f * alpha).coerceIn(0f, 1f)),
                    adjustAlpha(darkenColor(burst.color), (0.35f * alpha).coerceIn(0f, 1f)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.18f, 0.45f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )

            flamePaint.shader = gradient

            flamePath.reset()
            flamePath.moveTo(flameRect.left, bottom)
            flamePath.quadTo(
                burst.x - flameWidth / 3 + sway * 0.4f,
                bottom - height * 0.55f,
                burst.x + sway,
                top
            )
            flamePath.quadTo(
                burst.x + flameWidth / 3 + sway * 0.4f,
                bottom - height * 0.55f,
                flameRect.right,
                bottom
            )
            flamePath.close()

            canvas.drawPath(flamePath, flamePaint)
            flamePaint.shader = null
        }
    }

    private fun drawAmbientEmbers(canvas: Canvas, nowMs: Long, activity: Float) {
        val activityBoost = (activity * 0.25f).coerceAtMost(0.35f)

        for (ember in ambientEmbers) {
            val age = nowMs - ember.createdAt
            if (age < 0) continue

            val progress = (age.toFloat() / ember.lifetimeMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            val life = 1f - progress
            if (life <= 0f) continue

            val twinkle = 0.75f + 0.25f * fastSin((nowMs * 0.01f + ember.twinklePhase) % (2 * Math.PI.toFloat()))
            val alpha = (life * (0.6f + activityBoost) * twinkle).coerceIn(0f, 1f)
            if (alpha <= 0f) continue

            val size = ember.size * (0.6f + life * 0.6f)

            // Halo discret
            particlePaint.color = adjustAlpha(ember.color, alpha * 0.25f * ember.haloScale)
            canvas.drawCircle(ember.x, ember.y, size * 1.8f, particlePaint)

            // Braise principale
            particlePaint.color = adjustAlpha(ember.color, alpha * 0.9f)
            canvas.drawCircle(ember.x, ember.y, size, particlePaint)

            // Coeur chaud
            particlePaint.color = adjustAlpha(FIRE_FLAME_CORE_COLOR, alpha * 0.7f)
            canvas.drawCircle(ember.x, ember.y, size * 0.35f, particlePaint)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        val nowMs = System.currentTimeMillis()

        // PERFORMANCE: Plus de BlurMaskFilter - trop coûteux
        // Effet de lueur simulé avec un cercle plus grand semi-transparent
        for (particle in particles) {
            val age = nowMs - particle.createdAt
            if (age < 0) continue

            val progress = (age.toFloat() / particle.lifetimeMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            val life = (1f - progress).coerceIn(0f, 1f)
            val alpha = (particle.alpha * life).coerceIn(0f, 1f)
            if (alpha <= 0f) continue

            val haloScale = if (particle.kind == ParticleKind.FIRE_SPARK) 1.9f else 1.5f
            val coreScale = if (particle.kind == ParticleKind.FIRE_SPARK) 0.45f else 0.3f

            // Halo externe (effet de glow simplifié)
            particlePaint.color = adjustAlpha(particle.color, alpha * 0.28f * particle.haloScale)
            canvas.drawCircle(particle.x, particle.y, particle.size * haloScale, particlePaint)

            // Cercle principal
            particlePaint.color = adjustAlpha(particle.color, alpha)
            canvas.drawCircle(particle.x, particle.y, particle.size, particlePaint)

            // Centre lumineux
            val coreColor = if (particle.kind == ParticleKind.FIRE_SPARK) FIRE_FLAME_CORE_COLOR else Color.WHITE
            particlePaint.color = adjustAlpha(coreColor, alpha * 0.65f)
            canvas.drawCircle(particle.x, particle.y, particle.size * coreScale, particlePaint)
        }
    }

    private fun drawHitFeedbacks(canvas: Canvas) {
        for ((_, feedback) in hitFeedback) {
            val elapsed = System.currentTimeMillis() - feedback.startTimeMs
            val progress = (elapsed / 400f).coerceIn(0f, 1f)

            if (progress >= 1f) continue

            val alpha = 1f - progress
            val radius = 20f + progress * 60f

            if (feedback.isSuccess) {
                // Cercle expansif pour le succès
                hitFeedbackPaint.color = adjustAlpha(feedback.color, alpha * 0.6f)
                hitFeedbackPaint.style = Paint.Style.STROKE
                hitFeedbackPaint.strokeWidth = 4f * (1f - progress)
                canvas.drawCircle(feedback.x, feedback.y, radius, hitFeedbackPaint)

                // Centre lumineux
                hitFeedbackPaint.style = Paint.Style.FILL
                hitFeedbackPaint.color = adjustAlpha(Color.WHITE, alpha * 0.8f)
                canvas.drawCircle(feedback.x, feedback.y, 10f * (1f - progress), hitFeedbackPaint)
            } else {
                // X rouge pour l'erreur
                hitFeedbackPaint.color = adjustAlpha(feedback.color, alpha)
                hitFeedbackPaint.style = Paint.Style.STROKE
                hitFeedbackPaint.strokeWidth = 3f
                val size = 15f
                canvas.drawLine(
                    feedback.x - size, feedback.y - size,
                    feedback.x + size, feedback.y + size,
                    hitFeedbackPaint
                )
                canvas.drawLine(
                    feedback.x + size, feedback.y - size,
                    feedback.x - size, feedback.y + size,
                    hitFeedbackPaint
                )
            }
        }
        hitFeedbackPaint.style = Paint.Style.FILL
    }

    private fun spawnParticles(x: Float, y: Float, color: Int) {
        // PERFORMANCE: Utiliser des valeurs déterministes basées sur le temps
        // au lieu de Random pour éviter les allocations
        val now = System.currentTimeMillis()
        val baseTime = (now % 10000).toInt()

        for (i in 0 until PARTICLES_PER_HIT) {
            // Angle réparti uniformément + léger décalage
            val angle = (i.toFloat() / PARTICLES_PER_HIT) * 2 * Math.PI.toFloat() + (baseTime % 100) * 0.01f
            val speedFactor = ((baseTime + i * 137) % 100) / 100f  // Pseudo-random déterministe
            val speed = speedFactor * PARTICLE_MAX_SPEED + 2f
            val size = ((baseTime + i * 73) % 100) / 100f * 3f + 2f

            if (particles.size >= MAX_PARTICLES) {
                particles.removeAt(0)
            }

            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = fastCos(angle) * speed,
                    vy = fastSin(angle) * speed - 3f,  // Légère poussée vers le haut
                    color = if ((i % 3) != 0) color else lightenColor(color, 0.5f),
                    size = size,
                    alpha = 1f,
                    createdAt = now,
                    lifetimeMs = PARTICLE_LIFETIME_MS,
                    gravity = 0.15f,
                    friction = 0.98f,
                    shrinkRate = 0.995f,
                    haloScale = 1f,
                    twinklePhase = (baseTime + i * 31) * 0.05f,
                    kind = ParticleKind.HIT_SPARK
                )
            )
        }
    }

    private fun updateParticles() {
        val currentTime = System.currentTimeMillis()
        updateParticleList(particles, currentTime)
    }

    private fun updateParticleList(list: MutableList<Particle>, currentTime: Long) {
        if (list.isEmpty()) return

        val maxY = hitZoneY + hitZoneHeight + glowRadius * 3f
        val minY = -40f
        val minX = -40f
        val maxX = width + 40f

        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            val age = currentTime - particle.createdAt

            if (age > particle.lifetimeMs) {
                iterator.remove()
                continue
            }

            particle.x += particle.vx
            particle.y += particle.vy

            particle.vy += particle.gravity
            particle.vx *= particle.friction
            particle.vy *= particle.friction
            particle.size *= particle.shrinkRate

            if (particle.size < 0.45f || particle.y < minY || particle.y > maxY || particle.x < minX || particle.x > maxX) {
                iterator.remove()
            }
        }
    }

    private fun updateFireEffects(deltaMs: Long, currentTimeMs: Long) {
        if (!isFireThemeActive() || whiteKeyCount == 0) {
            if (ambientEmbers.isNotEmpty() || flameBursts.isNotEmpty()) {
                resetFireEffects()
            }
            return
        }

        updateAmbientEmbers(deltaMs, currentTimeMs)
        updateFlameBursts(currentTimeMs)
    }

    private fun updateAmbientEmbers(deltaMs: Long, currentTimeMs: Long) {
        updateParticleList(ambientEmbers, currentTimeMs)

        if (deltaMs <= 0) return

        val interval = FIRE_AMBIENT_EMBER_INTERVAL_MS
        val elapsed = if (lastAmbientEmberSpawnMs == 0L) interval else currentTimeMs - lastAmbientEmberSpawnMs
        if (elapsed < interval) return

        val spawnBudget = (elapsed / interval).toInt().coerceIn(1, 2)
        repeat(spawnBudget) {
            spawnAmbientEmber(currentTimeMs)
        }
        lastAmbientEmberSpawnMs = currentTimeMs
    }

    private fun spawnAmbientEmber(currentTimeMs: Long) {
        if (ambientEmbers.size >= FIRE_MAX_AMBIENT_EMBERS || width == 0) return

        val density = resources.displayMetrics.density
        val activityBoost = (flameBursts.size / FIRE_MAX_FLAME_BURSTS.toFloat()).coerceAtMost(0.4f)
        val x = nextFireFloat() * width
        val baseY = hitZoneY + hitZoneHeight * (0.35f + nextFireFloat() * 0.35f)
        val vx = (nextFireFloat() - 0.5f) * 0.8f
        val vy = -(FIRE_EMBER_BASE_SPEED + nextFireFloat() * FIRE_EMBER_SPEED_VARIATION) * (1f + activityBoost)
        val size = (1.4f + nextFireFloat() * 2.2f) * density
        val hotMix = (0.35f + nextFireFloat() * 0.65f).coerceIn(0f, 1f)
        val color = lerpColor(FIRE_EMBER_BASE_COLOR, FIRE_EMBER_HOT_COLOR, hotMix)

        ambientEmbers.add(
            Particle(
                x = x,
                y = baseY,
                vx = vx,
                vy = vy,
                color = color,
                size = size,
                alpha = 1f,
                createdAt = currentTimeMs,
                lifetimeMs = FIRE_EMBER_LIFETIME_MS,
                gravity = -0.015f,
                friction = 0.992f,
                shrinkRate = 0.9965f,
                haloScale = 0.9f,
                twinklePhase = nextFireFloat() * 2f * Math.PI.toFloat(),
                kind = ParticleKind.EMBER
            )
        )
    }

    private fun updateFlameBursts(currentTimeMs: Long) {
        val iterator = flameBursts.iterator()
        while (iterator.hasNext()) {
            val burst = iterator.next()
            val age = currentTimeMs - burst.startTimeMs
            if (age > FIRE_FLAME_BURST_LIFETIME_MS) {
                iterator.remove()
                flameBurstsByNote.remove(burst.note)
            }
        }
    }

    private fun spawnFlameBurst(note: Int, strength: Float) {
        if (!isFireThemeActive()) return

        val noteX = notePositions[note] ?: return
        val isBlack = isBlackKey(note)
        val actualNoteWidth = if (isBlack) noteWidth * 0.7f else noteWidth
        val centerX = noteX + actualNoteWidth / 2f
        val now = System.currentTimeMillis()
        val clampedStrength = strength.coerceIn(0.6f, 1.35f)

        val density = resources.displayMetrics.density
        val minHeight = FIRE_FLAME_MIN_HEIGHT_DP * density
        val maxHeight = (hitZoneY * FIRE_FLAME_MAX_HEIGHT_RATIO).coerceAtLeast(minHeight * 1.4f)
        val baseHeight = minHeight + (maxHeight - minHeight) * (clampedStrength - 0.6f) / (1.35f - 0.6f)

        val maxWidth = FIRE_FLAME_MAX_WIDTH_DP * density
        val baseWidth = (actualNoteWidth * (0.95f + clampedStrength * 0.35f)).coerceAtMost(maxWidth)
        val burstColor = lightenColor(getNoteBaseColor(note), 0.18f)

        val existing = flameBurstsByNote[note]
        if (existing != null && now - existing.startTimeMs < FIRE_FLAME_COOLDOWN_MS) {
            existing.startTimeMs = now
            existing.intensity = (existing.intensity + clampedStrength * 0.35f).coerceAtMost(1.5f)
            existing.baseHeight = maxOf(existing.baseHeight, baseHeight)
            existing.baseWidth = maxOf(existing.baseWidth, baseWidth)
            spawnFlameSparks(centerX, clampedStrength, burstColor, now)
            return
        }

        if (flameBursts.size >= FIRE_MAX_FLAME_BURSTS) {
            val removed = flameBursts.removeAt(0)
            flameBurstsByNote.remove(removed.note)
        }

        val burst = FlameBurst(
            note = note,
            startTimeMs = now,
            x = centerX,
            intensity = clampedStrength,
            baseWidth = baseWidth,
            baseHeight = baseHeight,
            phaseOffset = nextFireFloat() * 2f * Math.PI.toFloat(),
            color = burstColor
        )
        flameBursts.add(burst)
        flameBurstsByNote[note] = burst

        spawnFlameSparks(centerX, clampedStrength, burstColor, now)
    }

    private fun spawnFlameSparks(centerX: Float, strength: Float, color: Int, now: Long) {
        val sparkCount = (FIRE_SPARKS_PER_BURST * strength).toInt().coerceIn(FIRE_SPARKS_PER_BURST, FIRE_SPARKS_PER_BURST + 3)

        repeat(sparkCount) {
            if (particles.size >= MAX_PARTICLES) {
                particles.removeAt(0)
            }

            val spread = (nextFireFloat() - 0.5f) * noteWidth * 0.6f
            val vx = spread * 0.08f + (nextFireFloat() - 0.5f) * 1.2f
            val vy = -(2.2f + nextFireFloat() * 3.4f) * (0.85f + strength * 0.25f)
            val size = 2f + nextFireFloat() * 2.6f

            particles.add(
                Particle(
                    x = centerX + spread,
                    y = hitZoneY + hitZoneHeight * 0.4f,
                    vx = vx,
                    vy = vy,
                    color = lerpColor(color, FIRE_FLAME_CORE_COLOR, 0.35f + nextFireFloat() * 0.4f),
                    size = size,
                    alpha = 1f,
                    createdAt = now,
                    lifetimeMs = FIRE_SPARK_LIFETIME_MS,
                    gravity = 0.05f,
                    friction = 0.985f,
                    shrinkRate = 0.992f,
                    haloScale = 1.2f,
                    twinklePhase = nextFireFloat() * 2f * Math.PI.toFloat(),
                    kind = ParticleKind.FIRE_SPARK
                )
            )
        }
    }

    private fun updateHitFeedbacks(currentTimeMs: Long) {
        val iterator = hitFeedback.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTimeMs - entry.value.startTimeMs > 500) {
                iterator.remove()
            }
        }
    }

    /**
     * Retourne la couleur de base pour une note selon le style/thème actuel
     */
    private fun getNoteBaseColor(note: Int): Int {
        // Nouveau système de thèmes
        if (useThemeSystem && currentTheme != null) {
            return currentTheme!!.getNoteColor(note % 12)
        }

        // Ancien système de styles
        return when (visualStyle) {
            VisualStyle.CLASSIC -> {
                // Style classique: couleur basée sur le canal (comportement original)
                ColorSettingsManager.getNoteColor(targetChannel, currentProgram)
            }
            VisualStyle.RAINBOW -> {
                // Style Rousseau: couleurs arc-en-ciel basées sur la note
                CHROMATIC_COLORS[note % 12]
            }
            VisualStyle.NEON -> {
                // Style néon: couleurs vibrantes
                NEON_COLORS[note % 12]
            }
            VisualStyle.OCEAN -> {
                // Style océan: dégradés bleu-turquoise
                OCEAN_COLORS[note % 12]
            }
            VisualStyle.FIRE -> {
                // Style feu: dégradés rouge-orange-jaune
                FIRE_COLORS[note % 12]
            }
        }
    }

    /**
     * Retourne la couleur de la hit zone selon le style/thème
     */
    private fun getHitZoneColor(): Int {
        // Nouveau système de thèmes
        if (useThemeSystem && currentTheme != null) {
            return currentTheme!!.getHitZoneColor()
        }

        // Ancien système de styles
        return when (visualStyle) {
            VisualStyle.CLASSIC -> "#4CAF50".toColorInt()  // Vert classique
            VisualStyle.RAINBOW -> "#00E5CC".toColorInt()  // Turquoise
            VisualStyle.NEON -> "#00FFFF".toColorInt()     // Cyan néon
            VisualStyle.OCEAN -> "#00D4AA".toColorInt()    // Turquoise océan
            VisualStyle.FIRE -> "#FF6600".toColorInt()     // Orange feu
        }
    }

    /**
     * Retourne la couleur du fond selon le style/thème
     */
    private fun getBackgroundColors(): Pair<Int, Int> {
        // Nouveau système de thèmes
        if (useThemeSystem && currentTheme != null) {
            return currentTheme!!.getBackgroundColors()
        }

        // Ancien système de styles
        return when (visualStyle) {
            VisualStyle.CLASSIC -> Pair(
                "#1A1A2E".toColorInt(),
                "#16213E".toColorInt()
            )
            VisualStyle.RAINBOW -> Pair(
                "#0D0D1A".toColorInt(),
                "#1A1A2E".toColorInt()
            )
            VisualStyle.NEON -> Pair(
                "#0A0A0F".toColorInt(),
                "#15151F".toColorInt()
            )
            VisualStyle.OCEAN -> Pair(
                "#001219".toColorInt(),
                "#002233".toColorInt()
            )
            VisualStyle.FIRE -> Pair(
                "#1A0A00".toColorInt(),
                "#2A1500".toColorInt()
            )
        }
    }

    /**
     * Éclaircit une couleur
     */
    private fun lightenColor(color: Int, factor: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val a = Color.alpha(color)

        return Color.argb(
            a,
            (r + (255 - r) * factor).toInt().coerceIn(0, 255),
            (g + (255 - g) * factor).toInt().coerceIn(0, 255),
            (b + (255 - b) * factor).toInt().coerceIn(0, 255)
        )
    }

    /**
     * Assombrit une couleur
     */
    private fun darkenColor(color: Int): Int {
        val factor = 0.45f
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val a = Color.alpha(color)

        return Color.argb(
            a,
            (r * (1 - factor)).toInt().coerceIn(0, 255),
            (g * (1 - factor)).toInt().coerceIn(0, 255),
            (b * (1 - factor)).toInt().coerceIn(0, 255)
        )
    }

    /**
     * Ajuste l'alpha d'une couleur
     */
    private fun adjustAlpha(color: Int, alphaFactor: Float): Int {
        val alpha = (Color.alpha(color) * alphaFactor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun lerpColor(startColor: Int, endColor: Int, t: Float): Int {
        val clampedT = t.coerceIn(0f, 1f)
        val a = (Color.alpha(startColor) + (Color.alpha(endColor) - Color.alpha(startColor)) * clampedT).toInt()
        val r = (Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * clampedT).toInt()
        val g = (Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * clampedT).toInt()
        val b = (Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * clampedT).toInt()
        return Color.argb(a, r, g, b)
    }

    /**
     * Vérifie quelles notes sont dans la hit zone et déclenche les callbacks
     * Filtre par main si en mode deux mains avec un mode de pratique spécifique
     */
    private fun checkHitZoneNotes() {
        val hitZoneStart = currentPositionMs - hitZoneToleranceMs
        val hitZoneEnd = currentPositionMs + hitZoneToleranceMs

        val currentHitZoneNotes = mutableSetOf<Int>()

        for (note in scheduledNotes) {
            // Filtrage par main en mode deux mains
            if (isTwoHandsMode) {
                when (handPracticeMode) {
                    HandPracticeMode.LEFT_HAND_ONLY -> if (!note.isLeftHand) continue
                    HandPracticeMode.RIGHT_HAND_ONLY -> if (note.isLeftHand) continue
                    HandPracticeMode.BOTH_HANDS -> { /* Toutes les notes */ }
                }
            }

            // Une note est dans la hit zone si :
            // - Son début est dans la zone (note qui arrive)
            // - OU elle chevauche la zone (note longue en cours)
            // La note reste attendue tant que son endTimeMs n'a pas dépassé le début de la zone
            val noteOverlapsHitZone = note.startTimeMs <= hitZoneEnd && note.endTimeMs >= hitZoneStart
            if (noteOverlapsHitZone) {
                currentHitZoneNotes.add(note.note)

                if (note.note !in notesInHitZone) {
                    onNoteReachHitZone?.invoke(note.note, note.startTimeMs)

                    if (isFireThemeActive()) {
                        val velocityBoost = (note.velocity / 127f) * 0.35f
                        spawnFlameBurst(note.note, 0.8f + velocityBoost)
                    }
                }
            }
        }

        for (oldNote in notesInHitZone) {
            if (oldNote !in currentHitZoneNotes) {
                onNoteLeaveHitZone?.invoke(oldNote)
            }
        }

        notesInHitZone.clear()
        notesInHitZone.addAll(currentHitZoneNotes)
    }

    /**
     * Retourne les notes actuellement dans la hit zone
     */
    @Suppress("unused")
    fun getNotesInHitZone(): Set<Int> = notesInHitZone.toSet()

    /**
     * Retourne les notes attendues dans la fenêtre temporelle actuelle
     * Filtre par main si en mode deux mains avec un mode de pratique spécifique
     */
    fun getExpectedNotesWindow(): List<ScheduledNote> {
        val hitZoneStart = currentPositionMs - hitZoneToleranceMs
        val hitZoneEnd = currentPositionMs + hitZoneToleranceMs
        return scheduledNotes.filter { note ->
            // Filtre temporel
            note.startTimeMs in hitZoneStart..hitZoneEnd &&
            // Filtre par main en mode deux mains
            (!isTwoHandsMode || when (handPracticeMode) {
                HandPracticeMode.LEFT_HAND_ONLY -> note.isLeftHand
                HandPracticeMode.RIGHT_HAND_ONLY -> !note.isLeftHand
                HandPracticeMode.BOTH_HANDS -> true
            })
        }
    }

    /**
     * Efface l'état et remet à zéro
     */
    fun reset() {
        stopAnimation()
        isPaused = false
        pausedPositionMs = 0L
        currentPositionMs = 0L
        notesInHitZone.clear()
        particles.clear()
        hitFeedback.clear()
        resetFireEffects()
        // Reset rising notes
        risingNotes.clear()
        activeRisingNotes.clear()
        risingModeStartTimeMs = System.currentTimeMillis()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
        stopBackgroundAnimation()
    }

    /**
     * Data class pour une particule
     */
    private enum class ParticleKind {
        HIT_SPARK,
        FIRE_SPARK,
        EMBER
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val color: Int,
        var size: Float,
        var alpha: Float,
        val createdAt: Long,
        val lifetimeMs: Long,
        val gravity: Float,
        val friction: Float,
        val shrinkRate: Float,
        val haloScale: Float,
        val twinklePhase: Float,
        val kind: ParticleKind
    )

    private data class FlameBurst(
        val note: Int,
        var startTimeMs: Long,
        val x: Float,
        var intensity: Float,
        var baseWidth: Float,
        var baseHeight: Float,
        val phaseOffset: Float,
        val color: Int
    )

    /**
     * Data class pour un feedback de hit
     */
    private data class HitFeedback(
        val note: Int,
        val startTimeMs: Long,
        val x: Float,
        val y: Float,
        val color: Int,
        val isSuccess: Boolean
    )
}
