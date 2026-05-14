package com.Atom2Universe.app.midi.practice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import com.Atom2Universe.app.midi.practice.themes.PracticeTheme
import com.Atom2Universe.app.midi.practice.themes.PracticeThemeManager
import kotlin.math.roundToInt

/**
 * Clavier piano interactif pour le mode pratique
 *
 * Caracteristiques:
 * - Detection tactile des touches (support multi-touch)
 * - Affichage des noms de notes optionnel
 * - Mise en evidence des notes attendues (hit zone)
 * - Couleurs par canal/velocite
 */
class InteractivePianoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // Dimensions
        private const val DEFAULT_WHITE_KEY_WIDTH_DP = 44f
        private const val DEFAULT_BLACK_KEY_WIDTH_RATIO = 0.6f
        private const val DEFAULT_BLACK_KEY_HEIGHT_RATIO = 0.60f

        // Notes noires dans une octave
        private val BLACK_KEY_INDICES = setOf(1, 3, 6, 8, 10) // C#, D#, F#, G#, A#

        // Noms des notes
        private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        // Velocite par defaut pour les touches tactiles
        private const val DEFAULT_TOUCH_VELOCITY = 100

        private const val TIMING_FEEDBACK_DURATION_MS = 500L  // Plus long pour être bien visible
    }

    // Configuration de la plage de notes
    private var noteRangeMin = 48  // C3
    private var noteRangeMax = 84  // C6
    private var currentProgram = 0  // For color lookup

    // Dimensions calculees
    private var whiteKeyWidth = 0f
    private var blackKeyWidth = 0f
    private var blackKeyHeight = 0f
    private var whiteKeyCount = 0

    // Cache des rectangles et mapping note -> rect
    private val whiteKeyRects = mutableListOf<Pair<Int, RectF>>() // note, rect
    private val blackKeyRects = mutableListOf<Pair<Int, RectF>>() // note, rect

    // Notes actives (jouees par le MIDI)
    private val activeNotes = mutableMapOf<Int, Triple<Int, Int, Long>>() // note -> (channel, velocity, timestamp)

    // Notes pressees par l'utilisateur
    // BUG FIX 3.28: Utiliser une structure thread-safe pour éviter les race conditions
    private val pressedNotes = java.util.concurrent.ConcurrentHashMap<Int, Int>() // pointerId -> note

    // Notes attendues (a jouer - highlight)
    private val expectedNotes = mutableSetOf<Int>()

    private data class TimingFeedback(val timing: PracticeSession.TimingJudgement, val timestampMs: Long)

    private val timingFeedback = mutableMapOf<Int, TimingFeedback>()

    // Notes jouees via clavier MIDI externe (affichees avec effet glow)
    private val externalNotes = mutableMapOf<Int, Triple<Int, Int, Long>>() // note -> (channel, velocity, timestamp)

    // Options
    var showNoteNames: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var touchEnabled: Boolean = true
    var targetChannel: Int = 0

    // Système de thèmes
    private var currentTheme: PracticeTheme? = null

    // Callbacks
    var onKeyPressed: ((note: Int, velocity: Int) -> Unit)? = null
    var onKeyReleased: ((note: Int) -> Unit)? = null

    // Paints
    private val whiteKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val blackKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val pressedWhiteKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#E0E0E0".toColorInt()
        style = Paint.Style.FILL
    }

    private val pressedBlackKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#333333".toColorInt()
        style = Paint.Style.FILL
    }

    private val activeKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val expectedKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#4CAF50".toColorInt() // Vert
        style = Paint.Style.FILL
        alpha = 180
    }

    private val goodTimingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#00E676".toColorInt() // Vert vif néon
        style = Paint.Style.FILL
        alpha = 230
    }

    private val badTimingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FF5252".toColorInt() // Rouge vif
        style = Paint.Style.FILL
        alpha = 230
    }

    // Paint pour le feedback de maintien réussi (doré/jaune)
    @Suppress("unused")
    private val holdSuccessPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#FFD700".toColorInt() // Or
        style = Paint.Style.FILL
        alpha = 230
    }

    private val keyOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#AAAAAA".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // Paint pour l'effet glow des notes externes (clavier physique)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#666666".toColorInt()
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val octaveLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#999999".toColorInt()
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
        isFocusable = true
    }

    /**
     * Configure la plage de notes
     */
    fun setNoteRange(minNote: Int, maxNote: Int) {
        if (minNote != noteRangeMin || maxNote != noteRangeMax) {
            noteRangeMin = minNote.coerceIn(0, 120)
            noteRangeMax = maxNote.coerceIn(12, 127)
            calculateDimensions()
            invalidate()
        }
    }

    /**
     * Configure le programme actuel (pour la couleur des touches)
     */
    fun setCurrentProgram(program: Int) {
        currentProgram = program
        invalidate()
    }

    /**
     * Applique un thème visuel au piano
     */
    fun setTheme(theme: PracticeTheme) {
        currentTheme = theme
        applyThemeColors()
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
     * Met à jour les couleurs des paints selon le thème
     */
    private fun applyThemeColors() {
        currentTheme?.let { theme ->
            whiteKeyPaint.color = theme.getWhiteKeyColor()
            blackKeyPaint.color = theme.getBlackKeyColor()
            pressedWhiteKeyPaint.color = theme.getPressedWhiteKeyColor()
            pressedBlackKeyPaint.color = theme.getPressedBlackKeyColor()

            // La couleur des notes attendues utilise la hit zone color du thème
            expectedKeyPaint.color = theme.getHitZoneColor()
        }
    }

    /**
     * Active une note (appele par le playback MIDI)
     */
    fun noteOn(channel: Int, note: Int, velocity: Int) {
        if (note in noteRangeMin..noteRangeMax) {
            activeNotes[note] = Triple(channel, velocity, System.currentTimeMillis())
            invalidate()
        }
    }

    /**
     * Desactive une note
     */
    @Suppress("UNUSED_PARAMETER")
    fun noteOff(channel: Int, note: Int) {
        if (activeNotes.remove(note) != null) {
            invalidate()
        }
    }

    /**
     * Desactive toutes les notes
     */
    fun allNotesOff() {
        if (activeNotes.isNotEmpty()) {
            activeNotes.clear()
            invalidate()
        }
    }

    /**
     * Desactive toutes les notes d'un canal spécifique
     * Retourne la liste des notes éteintes (pour synchroniser les LEDs externes)
     */
    fun notesOffForChannel(channel: Int): List<Int> {
        val notesToRemove = activeNotes.filter { (_, data) -> data.first == channel }.keys.toList()
        if (notesToRemove.isNotEmpty()) {
            notesToRemove.forEach { activeNotes.remove(it) }
            invalidate()
        }
        return notesToRemove
    }

    /**
     * Active une note externe (jouee via clavier MIDI physique)
     * Affichee avec un contour lumineux (glow) pour la distinguer
     */
    fun externalNoteOn(channel: Int, note: Int, velocity: Int) {
        if (note in noteRangeMin..noteRangeMax) {
            externalNotes[note] = Triple(channel, velocity, System.currentTimeMillis())
            invalidate()
        }
    }

    /**
     * Desactive une note externe
     */
    fun externalNoteOff(note: Int) {
        if (externalNotes.remove(note) != null) {
            invalidate()
        }
    }

    /**
     * Desactive toutes les notes externes
     */
    fun allExternalNotesOff() {
        if (externalNotes.isNotEmpty()) {
            externalNotes.clear()
            invalidate()
        }
    }

    /**
     * Definit les notes attendues (a jouer)
     */
    fun setExpectedNotes(notes: Set<Int>) {
        expectedNotes.clear()
        expectedNotes.addAll(notes)
        invalidate()
    }

    /**
     * Ajoute une note attendue
     */
    fun addExpectedNote(note: Int) {
        if (note in noteRangeMin..noteRangeMax) {
            expectedNotes.add(note)
            invalidate()
        }
    }

    /**
     * Retire une note attendue
     */
    fun removeExpectedNote(note: Int) {
        if (expectedNotes.remove(note)) {
            invalidate()
        }
    }

    /**
     * Efface toutes les notes attendues
     */
    fun clearExpectedNotes() {
        if (expectedNotes.isNotEmpty()) {
            expectedNotes.clear()
            invalidate()
        }
    }

    /**
     * Affiche un feedback timing (good/bad) pour une note
     */
    fun showTimingFeedback(note: Int, timing: PracticeSession.TimingJudgement) {
        if (note !in noteRangeMin..noteRangeMax) return
        timingFeedback[note] = TimingFeedback(timing, System.currentTimeMillis())
        invalidate()
        postInvalidateDelayed(TIMING_FEEDBACK_DURATION_MS)
    }

    private fun isBlackKey(note: Int): Boolean {
        return (note % 12) in BLACK_KEY_INDICES
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

        whiteKeyWidth = width.toFloat() / whiteKeyCount
        blackKeyWidth = whiteKeyWidth * DEFAULT_BLACK_KEY_WIDTH_RATIO

        // Calculate black key height based on white key width to maintain realistic proportions
        // A real piano black key is about 3.5x the white key width
        // But also cap it at 60% of view height to handle small views
        val proportionalBlackKeyHeight = whiteKeyWidth * 3.5f
        val maxBlackKeyHeight = height * DEFAULT_BLACK_KEY_HEIGHT_RATIO
        blackKeyHeight = proportionalBlackKeyHeight.coerceAtMost(maxBlackKeyHeight)

        calculateKeyRects()

        labelPaint.textSize = (whiteKeyWidth * 0.35f).coerceIn(16f, 32f)
        octaveLabelPaint.textSize = (whiteKeyWidth * 0.25f).coerceIn(12f, 24f)
    }

    private fun calculateKeyRects() {
        whiteKeyRects.clear()
        blackKeyRects.clear()

        var whiteKeyIndex = 0

        // Touches blanches
        for (note in noteRangeMin..noteRangeMax) {
            if (!isBlackKey(note)) {
                val left = whiteKeyIndex * whiteKeyWidth
                val rect = RectF(left, 0f, left + whiteKeyWidth, height.toFloat())
                whiteKeyRects.add(Pair(note, rect))
                whiteKeyIndex++
            }
        }

        // Touches noires
        whiteKeyIndex = 0
        for (note in noteRangeMin..noteRangeMax) {
            if (!isBlackKey(note)) {
                val nextNote = note + 1
                if (nextNote <= noteRangeMax && isBlackKey(nextNote)) {
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
        val desiredHeight = (DEFAULT_WHITE_KEY_WIDTH_DP * 4 * resources.displayMetrics.density).roundToInt()

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

        // BUG FIX 3.28: Créer une copie snapshot thread-safe des notes pressées
        val userPressedNotes = pressedNotes.values.toSet()
        val nowMs = System.currentTimeMillis()

        // 1. Dessiner les touches blanches
        for ((note, rect) in whiteKeyRects) {
            val isUserPressed = note in userPressedNotes
            val activeData = activeNotes[note]
            val externalData = externalNotes[note]
            val isExpected = note in expectedNotes
            val timing = getTimingFeedback(note, nowMs)

            when {
                // Notes externes (clavier physique) - priorite maximale avec effet glow
                externalData != null -> {
                    val (channel, velocity, _) = externalData
                    activeKeyPaint.color = getKeyColor(channel, velocity, false)
                    canvas.drawRect(rect, activeKeyPaint)
                    // Effet glow (contour lumineux)
                    glowPaint.color = getGlowColor(channel)
                    glowPaint.setShadowLayer(12f, 0f, 0f, glowPaint.color)
                    canvas.drawRect(rect, glowPaint)
                }
                activeData != null -> {
                    val (channel, velocity, _) = activeData
                    activeKeyPaint.color = getKeyColor(channel, velocity, false)
                    canvas.drawRect(rect, activeKeyPaint)
                }
                isUserPressed -> {
                    canvas.drawRect(rect, pressedWhiteKeyPaint)
                }
                isExpected -> {
                    canvas.drawRect(rect, whiteKeyPaint)
                    canvas.drawRect(rect, expectedKeyPaint)
                }
                else -> {
                    canvas.drawRect(rect, whiteKeyPaint)
                }
            }

            drawTimingOverlay(canvas, rect, timing, isBlackKey = false)

            canvas.drawRect(rect, keyOutlinePaint)

            // Labels
            if (showNoteNames) {
                val noteName = NOTE_NAMES[note % 12]
                canvas.drawText(noteName, rect.centerX(), rect.bottom - 40f, labelPaint)
            }

            // Octave label pour les C
            if (note % 12 == 0) {
                val octave = (note / 12) - 1
                val label = "C$octave"
                val yPos = if (showNoteNames) rect.bottom - 12f else rect.bottom - 8f
                canvas.drawText(label, rect.centerX(), yPos, octaveLabelPaint)
            }
        }

        // 2. Dessiner les touches noires
        for ((note, rect) in blackKeyRects) {
            val isUserPressed = note in userPressedNotes
            val activeData = activeNotes[note]
            val externalData = externalNotes[note]
            val isExpected = note in expectedNotes
            val timing = getTimingFeedback(note, nowMs)

            when {
                // Notes externes (clavier physique) - priorite maximale avec effet glow
                externalData != null -> {
                    val (channel, velocity, _) = externalData
                    activeKeyPaint.color = getKeyColor(channel, velocity, true)
                    canvas.drawRoundRect(rect, 4f, 4f, activeKeyPaint)
                    // Effet glow (contour lumineux)
                    glowPaint.color = getGlowColor(channel)
                    glowPaint.setShadowLayer(12f, 0f, 0f, glowPaint.color)
                    canvas.drawRoundRect(rect, 4f, 4f, glowPaint)
                }
                activeData != null -> {
                    val (channel, velocity, _) = activeData
                    activeKeyPaint.color = getKeyColor(channel, velocity, true)
                    canvas.drawRoundRect(rect, 4f, 4f, activeKeyPaint)
                }
                isUserPressed -> {
                    canvas.drawRoundRect(rect, 4f, 4f, pressedBlackKeyPaint)
                }
                isExpected -> {
                    canvas.drawRoundRect(rect, 4f, 4f, blackKeyPaint)
                    expectedKeyPaint.alpha = 150
                    canvas.drawRoundRect(rect, 4f, 4f, expectedKeyPaint)
                    expectedKeyPaint.alpha = 180
                }
                else -> {
                    canvas.drawRoundRect(rect, 4f, 4f, blackKeyPaint)
                }
            }

            drawTimingOverlay(canvas, rect, timing, isBlackKey = true)
        }
    }

    private fun getTimingFeedback(note: Int, nowMs: Long): PracticeSession.TimingJudgement? {
        val feedback = timingFeedback[note] ?: return null

        // Pour les notes tenues : garder le feedback GOOD tant que la note est
        // encore jouée (externalNotes) ET encore attendue (expectedNotes)
        val isStillHeld = note in externalNotes
        val isStillExpected = note in expectedNotes

        // Si la note est tenue correctement ET toujours attendue, garder le feedback
        if (feedback.timing == PracticeSession.TimingJudgement.GOOD && isStillHeld && isStillExpected) {
            return feedback.timing
        }

        // Sinon, utiliser le délai normal
        return if (nowMs - feedback.timestampMs <= TIMING_FEEDBACK_DURATION_MS) {
            feedback.timing
        } else {
            timingFeedback.remove(note)
            null
        }
    }

    private fun drawTimingOverlay(
        canvas: Canvas,
        rect: RectF,
        timing: PracticeSession.TimingJudgement?,
        isBlackKey: Boolean
    ) {
        when (timing) {
            PracticeSession.TimingJudgement.GOOD -> {
                if (isBlackKey) {
                    canvas.drawRoundRect(rect, 4f, 4f, goodTimingPaint)
                } else {
                    canvas.drawRect(rect, goodTimingPaint)
                }
            }
            PracticeSession.TimingJudgement.BAD -> {
                if (isBlackKey) {
                    canvas.drawRoundRect(rect, 4f, 4f, badTimingPaint)
                } else {
                    canvas.drawRect(rect, badTimingPaint)
                }
            }
            null -> Unit
        }
    }


    private fun getKeyColor(channel: Int, velocity: Int, isBlackKey: Boolean): Int {
        // Use ColorSettingsManager for custom colors
        val baseColor = ColorSettingsManager.getNoteColor(channel, currentProgram)
        val alpha = ((velocity / 127f) * 0.5f + 0.5f).coerceIn(0.5f, 1f)

        return if (isBlackKey) {
            adjustColorBrightness(baseColor, 0.8f, alpha)
        } else {
            adjustColorBrightness(baseColor, 1f, alpha)
        }
    }

    private fun adjustColorBrightness(color: Int, brightnessFactor: Float, alpha: Float): Int {
        val a = (255 * alpha).toInt()
        val r = ((color shr 16 and 0xFF) * brightnessFactor).toInt().coerceIn(0, 255)
        val g = ((color shr 8 and 0xFF) * brightnessFactor).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * brightnessFactor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    /**
     * Calcule une couleur de glow plus lumineuse basee sur la couleur du canal
     */
    private fun getGlowColor(channel: Int): Int {
        // Use ColorSettingsManager for custom colors
        val baseColor = ColorSettingsManager.getNoteColor(channel, currentProgram)
        // Eclaircir la couleur pour l'effet glow
        val r = ((baseColor shr 16 and 0xFF) * 1.3f).toInt().coerceIn(0, 255)
        val g = ((baseColor shr 8 and 0xFF) * 1.3f).toInt().coerceIn(0, 255)
        val b = ((baseColor and 0xFF) * 1.3f).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    /**
     * Rafraîchit les couleurs (après modification dans les paramètres)
     */
    fun refreshColors() {
        invalidate()
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!touchEnabled) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Empecher le parent (HorizontalScrollView) d'intercepter les touches
                // pour permettre le glissando sans declencher le scroll
                parent?.requestDisallowInterceptTouchEvent(true)

                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                findNoteAtPosition(x, y)?.let { note ->
                    pressedNotes[pointerId] = note
                    onKeyPressed?.invoke(note, DEFAULT_TOUCH_VELOCITY)
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Gerer le glissement sur les touches (glissando)
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)

                    val currentNote = pressedNotes[pointerId]
                    // Pour le glissando, utiliser findNoteAtX si le doigt est proche du clavier
                    // Cela permet de glisser meme si le doigt sort legerement de la zone
                    val newNote = if (y >= -height * 0.5f && y <= height * 1.5f) {
                        findNoteAtX(x, y)
                    } else {
                        null // Doigt trop loin du clavier
                    }

                    if (newNote != currentNote) {
                        // Relacher l'ancienne touche
                        currentNote?.let { note ->
                            onKeyReleased?.invoke(note)
                        }

                        // Presser la nouvelle touche
                        if (newNote != null) {
                            pressedNotes[pointerId] = newNote
                            onKeyPressed?.invoke(newNote, DEFAULT_TOUCH_VELOCITY)
                        } else {
                            pressedNotes.remove(pointerId)
                        }

                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)

                pressedNotes.remove(pointerId)?.let { note ->
                    onKeyReleased?.invoke(note)
                    invalidate()
                }

                // Si plus aucun doigt sur le clavier, permettre a nouveau le scroll
                if (pressedNotes.isEmpty()) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                // Relacher toutes les touches
                pressedNotes.values.forEach { note ->
                    onKeyReleased?.invoke(note)
                }
                pressedNotes.clear()
                invalidate()

                // Permettre a nouveau le scroll
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return true
    }

    /**
     * Trouve la note a une position donnee
     * Verifie d'abord les touches noires (elles sont au-dessus)
     */
    private fun findNoteAtPosition(x: Float, y: Float): Int? {
        // Verifier les touches noires en premier
        for ((note, rect) in blackKeyRects) {
            if (rect.contains(x, y)) {
                return note
            }
        }

        // Puis les touches blanches
        for ((note, rect) in whiteKeyRects) {
            if (rect.contains(x, y)) {
                return note
            }
        }

        return null
    }

    /**
     * Trouve la note basee principalement sur X (pour le glissando)
     * Permet de glisser meme si le doigt sort legerement du clavier
     */
    private fun findNoteAtX(x: Float, y: Float): Int? {
        // Si y est dans la zone des touches noires, verifier les noires d'abord
        if (y < blackKeyHeight) {
            for ((note, rect) in blackKeyRects) {
                if (x >= rect.left && x <= rect.right) {
                    return note
                }
            }
        }

        // Sinon, trouver la touche blanche a cette position X
        for ((note, rect) in whiteKeyRects) {
            if (x >= rect.left && x <= rect.right) {
                return note
            }
        }

        return null
    }

    /**
     * Retourne le nom d'une note MIDI
     */
    @Suppress("unused")
    fun getNoteName(note: Int): String {
        val name = NOTE_NAMES[note % 12]
        val octave = (note / 12) - 1
        return "$name$octave"
    }

    /**
     * Retourne la plage de notes actuelle
     */
    @Suppress("unused")
    fun getNoteRange(): Pair<Int, Int> = Pair(noteRangeMin, noteRangeMax)
}
