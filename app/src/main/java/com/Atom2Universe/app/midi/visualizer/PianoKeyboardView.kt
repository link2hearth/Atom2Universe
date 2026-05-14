package com.Atom2Universe.app.midi.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.Atom2Universe.app.midi.practice.ColorSettingsManager
import kotlin.math.roundToInt

/**
 * Vue personnalisée affichant un clavier de piano adaptatif
 *
 * - Affiche uniquement les octaves nécessaires selon le fichier MIDI
 * - Illumine les notes jouées en temps réel avec couleur par canal
 * - Supporte le scroll horizontal si trop de touches
 * - Optimisé pour les performances (redraw minimal)
 */
class PianoKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // Dimensions par défaut
        private const val DEFAULT_WHITE_KEY_WIDTH_DP = 28f
        private const val DEFAULT_BLACK_KEY_WIDTH_RATIO = 0.6f
        private const val DEFAULT_BLACK_KEY_HEIGHT_RATIO = 0.62f

        // Notes noires dans une octave (indices relatifs)
        private val BLACK_KEY_INDICES = setOf(1, 3, 6, 8, 10)  // C#, D#, F#, G#, A#

        // Position des touches noires (offset par rapport à la touche blanche précédente)
        private val BLACK_KEY_OFFSETS = mapOf(
            1 to 0.65f,   // C# après C
            3 to 0.65f,   // D# après D
            6 to 0.65f,   // F# après F
            8 to 0.65f,   // G# après G
            10 to 0.65f   // A# après A
        )

        // Sanity check pour notes fantômes
        private const val SANITY_CHECK_INTERVAL_MS = 1000L  // Check toutes les secondes
        private const val NOTE_MAX_DURATION_MS = 15000L    // Note max 15 secondes
    }

    // Configuration de la plage de notes
    private var noteRangeMin = 48  // C3 par défaut
    private var noteRangeMax = 72  // C5 par défaut

    // Programme actuel pour la recherche de couleur d'instrument
    private var currentProgram = 0

    // Dimensions calculées
    private var whiteKeyWidth = 0f
    private var blackKeyWidth = 0f
    private var blackKeyHeight = 0f
    private var whiteKeyCount = 0

    // Notes actives: Map<note, Triple<channel, velocity, timestamp>>
    private val activeNotes = mutableMapOf<Int, Triple<Int, Int, Long>>()

    // Handler pour le sanity check périodique
    private val sanityCheckHandler = Handler(Looper.getMainLooper())
    private var isSanityCheckRunning = false

    private val sanityCheckRunnable = object : Runnable {
        override fun run() {
            cleanupGhostNotes()
            if (isSanityCheckRunning) {
                sanityCheckHandler.postDelayed(this, SANITY_CHECK_INTERVAL_MS)
            }
        }
    }

    // Paint objects (réutilisés pour performance)
    private val whiteKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val blackKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val keyOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCCCCC")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val activeKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    // Rectangle réutilisable pour le dessin
    private val keyRect = RectF()

    // Cache des rectangles des touches pour éviter les recalculs
    private val whiteKeyRects = mutableListOf<RectF>()
    private val blackKeyRects = mutableListOf<Pair<Int, RectF>>()  // note, rect

    init {
        // Permettre le dessin hardware accéléré
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Configure la plage de notes à afficher
     */
    fun setNoteRange(minNote: Int, maxNote: Int) {
        if (minNote != noteRangeMin || maxNote != noteRangeMax) {
            noteRangeMin = minNote.coerceIn(0, 120)
            noteRangeMax = maxNote.coerceIn(12, 127)

            // Recalculer les dimensions
            calculateDimensions()
            invalidate()
        }
    }

    /**
     * Active une note (appelé lors d'un Note On)
     */
    fun noteOn(channel: Int, note: Int, velocity: Int) {
        if (note in noteRangeMin until noteRangeMax) {
            activeNotes[note] = Triple(channel, velocity, System.currentTimeMillis())
            // Invalider seulement la zone de la touche
            invalidate()
        }
    }

    /**
     * Désactive une note (appelé lors d'un Note Off)
     */
    fun noteOff(channel: Int, note: Int) {
        if (activeNotes.remove(note) != null) {
            invalidate()
        }
    }

    /**
     * Désactive toutes les notes
     */
    fun allNotesOff() {
        if (activeNotes.isNotEmpty()) {
            activeNotes.clear()
            invalidate()
        }
    }

    /**
     * Démarre le sanity check périodique pour nettoyer les notes fantômes
     */
    fun startSanityCheck() {
        if (!isSanityCheckRunning) {
            isSanityCheckRunning = true
            sanityCheckHandler.postDelayed(sanityCheckRunnable, SANITY_CHECK_INTERVAL_MS)
        }
    }

    /**
     * Arrête le sanity check
     */
    fun stopSanityCheck() {
        isSanityCheckRunning = false
        sanityCheckHandler.removeCallbacks(sanityCheckRunnable)
    }

    /**
     * Nettoie les notes fantômes (actives depuis trop longtemps)
     */
    private fun cleanupGhostNotes() {
        val now = System.currentTimeMillis()
        val ghostNotes = mutableListOf<Int>()

        for ((note, data) in activeNotes) {
            val (_, _, timestamp) = data
            if (now - timestamp > NOTE_MAX_DURATION_MS) {
                ghostNotes.add(note)
            }
        }

        if (ghostNotes.isNotEmpty()) {
            ghostNotes.forEach { activeNotes.remove(it) }
            invalidate()
        }
    }

    /**
     * Vérifie si une note est une touche noire
     */
    private fun isBlackKey(note: Int): Boolean {
        return (note % 12) in BLACK_KEY_INDICES
    }

    /**
     * Compte les touches blanches dans la plage
     */
    private fun countWhiteKeys(): Int {
        var count = 0
        for (note in noteRangeMin until noteRangeMax) {
            if (!isBlackKey(note)) count++
        }
        return count
    }

    /**
     * Calcule les dimensions des touches
     */
    private fun calculateDimensions() {
        whiteKeyCount = countWhiteKeys()

        if (whiteKeyCount == 0 || width == 0 || height == 0) return

        // Calculer la largeur des touches blanches pour remplir la vue
        whiteKeyWidth = width.toFloat() / whiteKeyCount

        // Calculer les dimensions des touches noires
        blackKeyWidth = whiteKeyWidth * DEFAULT_BLACK_KEY_WIDTH_RATIO
        blackKeyHeight = height * DEFAULT_BLACK_KEY_HEIGHT_RATIO

        // Recalculer les rectangles des touches
        calculateKeyRects()

        // Ajuster la taille du texte des labels
        labelPaint.textSize = (whiteKeyWidth * 0.4f).coerceIn(10f, 20f)
    }

    /**
     * Précalcule les rectangles de toutes les touches
     */
    private fun calculateKeyRects() {
        whiteKeyRects.clear()
        blackKeyRects.clear()

        var whiteKeyIndex = 0

        for (note in noteRangeMin until noteRangeMax) {
            if (!isBlackKey(note)) {
                // Touche blanche
                val left = whiteKeyIndex * whiteKeyWidth
                val rect = RectF(left, 0f, left + whiteKeyWidth, height.toFloat())
                whiteKeyRects.add(rect)
                whiteKeyIndex++
            }
        }

        // Calculer les touches noires (après les blanches pour les superposer)
        whiteKeyIndex = 0
        for (note in noteRangeMin until noteRangeMax) {
            if (!isBlackKey(note)) {
                // Vérifier si la note suivante est une touche noire
                val nextNote = note + 1
                if (nextNote < noteRangeMax && isBlackKey(nextNote)) {
                    val whiteKeyLeft = whiteKeyIndex * whiteKeyWidth
                    val blackKeyLeft = whiteKeyLeft + whiteKeyWidth - (blackKeyWidth / 2)
                    val rect = RectF(blackKeyLeft, 0f, blackKeyLeft + blackKeyWidth, blackKeyHeight)
                    blackKeyRects.add(Pair(nextNote, rect))
                }
                whiteKeyIndex++
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (DEFAULT_WHITE_KEY_WIDTH_DP * 3 * resources.displayMetrics.density).roundToInt()

        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> desiredHeight.coerceAtMost(heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (whiteKeyCount == 0) return

        // 1. Dessiner les touches blanches
        var noteIndex = noteRangeMin
        for (rect in whiteKeyRects) {
            // Trouver la note correspondante (sauter les noires)
            while (noteIndex < noteRangeMax && isBlackKey(noteIndex)) {
                noteIndex++
            }

            if (noteIndex >= noteRangeMax) break

            // Vérifier si la note est active
            val activeData = activeNotes[noteIndex]
            if (activeData != null) {
                // Touche active - colorer selon le canal
                val (channel, velocity, _) = activeData
                activeKeyPaint.color = getKeyColor(channel, velocity, false)
                canvas.drawRect(rect, activeKeyPaint)
            } else {
                canvas.drawRect(rect, whiteKeyPaint)
            }

            // Contour
            canvas.drawRect(rect, keyOutlinePaint)

            // Label pour les Do (C)
            if (noteIndex % 12 == 0) {
                val octave = (noteIndex / 12) - 1
                val label = "C$octave"
                canvas.drawText(label, rect.centerX(), rect.bottom - 8f, labelPaint)
            }

            noteIndex++
        }

        // 2. Dessiner les touches noires (par-dessus les blanches)
        for ((note, rect) in blackKeyRects) {
            val activeData = activeNotes[note]
            if (activeData != null) {
                // Touche active
                val (channel, velocity, _) = activeData
                activeKeyPaint.color = getKeyColor(channel, velocity, true)
                canvas.drawRoundRect(rect, 4f, 4f, activeKeyPaint)
            } else {
                canvas.drawRoundRect(rect, 4f, 4f, blackKeyPaint)
            }
        }
    }

    /**
     * Calcule la couleur d'une touche active
     * Utilise ColorSettingsManager pour les couleurs personnalisées
     */
    private fun getKeyColor(channel: Int, velocity: Int, isBlackKey: Boolean): Int {
        // Use ColorSettingsManager for custom colors (instrument > channel > default)
        val baseColor = ColorSettingsManager.getNoteColor(channel, currentProgram)

        // Ajuster la luminosité selon la vélocité (plus fort = plus brillant)
        val alpha = ((velocity / 127f) * 0.5f + 0.5f).coerceIn(0.5f, 1f)

        // Pour les touches noires actives, utiliser une couleur plus saturée
        return if (isBlackKey) {
            adjustColorBrightness(baseColor, 0.8f, alpha)
        } else {
            adjustColorBrightness(baseColor, 1f, alpha)
        }
    }

    /**
     * Définit le programme actuel (pour la couleur d'instrument)
     */
    fun setCurrentProgram(program: Int) {
        if (currentProgram != program) {
            currentProgram = program
            invalidate()
        }
    }

    /**
     * Ajuste la luminosité et l'alpha d'une couleur
     */
    private fun adjustColorBrightness(color: Int, brightnessFactor: Float, alpha: Float): Int {
        val a = (255 * alpha).toInt()
        val r = ((color shr 16 and 0xFF) * brightnessFactor).toInt().coerceIn(0, 255)
        val g = ((color shr 8 and 0xFF) * brightnessFactor).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * brightnessFactor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    /**
     * Retourne le nombre d'octaves affichées
     */
    fun getOctaveCount(): Int {
        return (noteRangeMax - noteRangeMin) / 12
    }

    /**
     * Retourne la plage de notes actuelle
     */
    fun getNoteRange(): Pair<Int, Int> {
        return Pair(noteRangeMin, noteRangeMax)
    }
}
