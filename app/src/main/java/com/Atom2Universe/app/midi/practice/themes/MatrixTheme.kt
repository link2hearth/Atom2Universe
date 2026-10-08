package com.Atom2Universe.app.midi.practice.themes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.toColorInt

/**
 * Thème Matrix - Style cascade de caractères verts
 * OPTIMISED for performance - reduced allocations and computations
 *
 * Caractéristiques :
 * - Notes = symboles tombants (runiques, coréen, chinois, cyrillique, alphabet)
 * - Notes noires = pointillés/points d'exclamation
 * - Fond noir avec effet CRT (lignes de balayage)
 * - Tout en vert Matrix
 */
class MatrixTheme : BasePracticeTheme() {

    override val id = "matrix"  // Keep internal ID for compatibility
    override val displayName = "Hacker"
    override val description = "Cascade de symboles style hacker"
    companion object {
        // Le vert Matrix iconique
        private const val MATRIX_GREEN = 0xFF00FF41.toInt()
        private const val MATRIX_GREEN_BRIGHT = 0xFF39FF14.toInt()

        // Symboles mélangés de différentes cultures (reduced set for performance)
        // Runiques (Elder Futhark) - reduced
        private val RUNES = charArrayOf(
            'ᚠ', 'ᚢ', 'ᚦ', 'ᚨ', 'ᚱ', 'ᚲ', 'ᚷ', 'ᚹ', 'ᚺ', 'ᚾ', 'ᛁ', 'ᛃ'
        )

        // Coréen (Hangul jamo) - reduced
        private val KOREAN = charArrayOf(
            'ㄱ', 'ㄴ', 'ㄷ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅅ', 'ㅇ', 'ㅈ', 'ㅊ'
        )

        // Chinois/Kanji simples - reduced
        private val CHINESE = charArrayOf(
            '中', '国', '人', '大', '小', '山', '水', '火', '木', '金', '土', '日'
        )

        // Cyrillique - reduced
        private val CYRILLIC = charArrayOf(
            'Д', 'Ж', 'И', 'Л', 'Ф', 'Ц', 'Ч', 'Ш', 'Щ', 'Э', 'Ю', 'Я'
        )

        // Alphabet latin + chiffres - reduced
        private val LATIN = charArrayOf(
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'K', 'M', 'N', 'P',
            'R', 'S', 'T', 'V', 'W', 'X', 'Y', 'Z'
        )

        // Symboles pour touches noires (ponctuation, chiffres, symboles)
        private val BLACK_KEY_SYMBOLS = charArrayOf(
            '·', '!', '?', '%', '#', '@', '&', '"', ':', '¦',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
        )

        // Tous les symboles combinés pour le fond
        private val ALL_SYMBOLS = RUNES + KOREAN + CHINESE + CYRILLIC + LATIN

        // PERFORMANCE: Reduced number of columns (was 25)
        private const val NUM_COLUMNS = 12
        private const val SYMBOL_DROP_SPEED = 0.08f // vitesse de chute

        // PERFORMANCE: Pre-calculated symbol strings to avoid toString() calls
        private val SYMBOL_STRINGS = ALL_SYMBOLS.map { it.toString() }.toTypedArray()
        private val BLACK_KEY_STRINGS = BLACK_KEY_SYMBOLS.map { it.toString() }.toTypedArray()

        // PERFORMANCE: Reduced symbol update frequency
        private const val SYMBOL_UPDATE_INTERVAL_MS = 200L

        // PERFORMANCE: CRT scan lines spacing (draw fewer lines)
        private const val CRT_LINE_SPACING = 4f
    }

    private var animationTimeMs = 0L
    private var lastSymbolUpdateMs = 0L
    private val columns = mutableListOf<MatrixColumn>()
    private var lastWidth = 0
    private var lastHeight = 0

    // PERFORMANCE: Pre-allocated random for symbol updates
    private var symbolUpdateCounter = 0

    // Paint pour les symboles
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }

    private val crtPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // PERFORMANCE: Cached vignette shader (created once when size changes)
    private var vignetteShader: RadialGradient? = null
    private var cachedVignetteWidth = 0
    private var cachedVignetteHeight = 0

    // Couleurs identiques pour toutes les notes (vert Matrix)
    override fun getNoteColor(pitchClass: Int): Int = MATRIX_GREEN

    override fun getBackgroundColors() = Pair(
        Color.BLACK,
        "#050505".toColorInt()
    )

    override fun getHitZoneColor() = MATRIX_GREEN
    override fun getGridLineColor() = Color.argb(20, 0, 255, 65)
    override fun getSheetMusicNoteColor() = MATRIX_GREEN

    override fun hasGlowEffect() = true
    override fun getGlowIntensity() = 0.7f
    override fun getGlowRadiusDp() = 12f

    // Notes avec symboles au lieu de rectangles
    override fun hasCustomNoteShape() = true

    // PERFORMANCE: Pre-computed black key lookup (avoids list creation)
    private val isBlackKeyLookup = booleanArrayOf(
        false, true, false, true, false, false, true, false, true, false, true, false
    ) // C, C#, D, D#, E, F, F#, G, G#, A, A#, B

    override fun drawNote(
        canvas: Canvas,
        rect: RectF,
        pitchClass: Int,
        velocity: Int,
        paint: Paint,
        cornerRadius: Float
    ) {
        // PERFORMANCE: Use lookup table instead of list containment check
        val isBlackKey = isBlackKeyLookup[pitchClass % 12]

        // Taille du symbole proportionnelle à la largeur de la note
        val symbolSize = (rect.width() * 0.8f).coerceIn(12f, 40f)
        symbolPaint.textSize = symbolSize

        // Plus de symboles sur les longues notes (jusqu'à 12)
        val maxSymbols = 12
        val numSymbols = (rect.height() / (symbolSize * 1.2f)).toInt().coerceIn(1, maxSymbols)

        // Position X centrée
        val centerX = rect.centerX()

        // Identifiant unique par note basé sur des valeurs STABLES (qui ne changent pas pendant la chute)
        // rect.left = position horizontale (fixe pour une note donnée)
        // rect.height() = durée de la note (fixe)
        // pitchClass = la note jouée (fixe)
        val noteId = (rect.left.toInt() * 7919 + rect.height().toInt() * 3571 + pitchClass * 6271) and 0x7FFFFFFF

        // Chaque note a son propre décalage temporel pour les changements de symboles
        // Intervalles variés entre 500ms et 1500ms selon la note (plus lent)
        val timeOffset = noteId % 500
        val timeInterval = 500 + (noteId % 11) * 100  // 500, 600, 700... jusqu'à 1500ms
        val noteTimeIndex = ((animationTimeMs + timeOffset) / timeInterval).toInt()

        // Dessiner les symboles empilés verticalement
        for (i in 0 until numSymbols) {
            val t = if (numSymbols == 1) 0.5f else i.toFloat() / (numSymbols - 1).coerceAtLeast(1)
            val y = rect.top + symbolSize / 2 + t * (rect.height() - symbolSize)

            // Hash unique par symbole: combine noteId, position dans la note, temps
            val symbolStr = if (isBlackKey) {
                val blackIdx = (noteId + i * 7) % BLACK_KEY_STRINGS.size
                BLACK_KEY_STRINGS[blackIdx]
            } else {
                val hash = (noteId + i * 3571 + noteTimeIndex * 2689) and 0x7FFFFFFF
                SYMBOL_STRINGS[hash % SYMBOL_STRINGS.size]
            }

            // Luminosité variable (plus lumineux en haut, simule la cascade)
            val brightness = 1f - (t * 0.4f)
            val alpha = (velocity / 127f * 255 * brightness).toInt().coerceIn(100, 255)

            // Couleur du symbole (vert Matrix avec variation)
            symbolPaint.color = if (isBlackKey) {
                Color.argb(alpha, 0, 180, 30) // Plus sombre pour touches noires
            } else {
                Color.argb(alpha, 0, 255, 65)
            }

            // Dessiner le symbole
            canvas.drawText(symbolStr, centerX, y + symbolSize / 3f, symbolPaint)
        }
    }

    override fun hasAnimatedBackground() = true

    override fun onActivate() {
        columns.clear()
    }

    override fun updateBackgroundAnimation(deltaMs: Long) {
        animationTimeMs += deltaMs

        // Mettre à jour les colonnes
        for (column in columns) {
            column.offset += deltaMs * SYMBOL_DROP_SPEED * column.speed
        }

        // PERFORMANCE: Update symbols less frequently using time-based throttling
        if (animationTimeMs - lastSymbolUpdateMs >= SYMBOL_UPDATE_INTERVAL_MS) {
            lastSymbolUpdateMs = animationTimeMs

            // Mettre à jour plusieurs symboles pour plus de variété
            if (columns.isNotEmpty()) {
                // Mettre à jour 2-3 colonnes différentes par intervalle
                val numUpdates = 3
                for (u in 0 until numUpdates) {
                    symbolUpdateCounter = (symbolUpdateCounter + 1) % columns.size
                    val column = columns[symbolUpdateCounter]

                    // Hash basé sur le temps et le compteur pour plus de variété
                    val hash1 = ((animationTimeMs.toInt() * 7919 + u * 3571) and 0x7FFFFFFF)
                    val hash2 = ((animationTimeMs.toInt() * 6271 + u * 2689) and 0x7FFFFFFF)

                    // Mettre à jour 1-2 symboles par colonne
                    val idx1 = hash1 % column.symbols.size
                    val idx2 = hash2 % column.symbols.size

                    column.symbols[idx1] = ALL_SYMBOLS[hash1 % ALL_SYMBOLS.size]
                    if (idx1 != idx2) {
                        column.symbols[idx2] = ALL_SYMBOLS[hash2 % ALL_SYMBOLS.size]
                    }
                }
            }
        }
    }

    override fun drawAnimatedBackground(canvas: Canvas, width: Int, height: Int) {
        // Mettre à jour les dimensions (utilisées pour le dessin)
        lastWidth = width
        lastHeight = height

        // Initialiser les colonnes seulement si elles n'existent pas encore
        // (ne pas réinitialiser lors du redimensionnement pour éviter les sauts visuels)
        if (columns.isEmpty() && width > 0 && height > 0) {
            initColumns(width, height)
        }

        // Dessiner les cascades de symboles
        drawMatrixRain(canvas, width, height)

        // Effet CRT (lignes de balayage)
        drawCRTEffect(canvas, width, height)
    }

    private fun initColumns(width: Int, height: Int) {
        columns.clear()
        val columnWidth = width.toFloat() / NUM_COLUMNS

        // Seed basé sur le temps pour plus de variété à chaque init
        val timeSeed = (System.currentTimeMillis() and 0xFFFF).toInt()

        for (i in 0 until NUM_COLUMNS) {
            // PERFORMANCE: Reduced symbol count (was height/20, now height/30)
            val symbolCount = (height / 30f).toInt().coerceIn(10, 25)

            // Génération de symboles avec plus de variété
            // Utilise des nombres premiers différents et le seed temporel
            val symbols = CharArray(symbolCount) { j ->
                val hash = (i * 7919 + j * 6271 + timeSeed * 3571 + j * j * 13) and 0x7FFFFFFF
                ALL_SYMBOLS[hash % ALL_SYMBOLS.size]
            }

            // Valeurs pseudo-aléatoires avec plus de variation
            val seed1 = (i * 7919 + timeSeed) and 0x7FFFFFFF
            val seed2 = (i * 6271 + timeSeed * 3) and 0x7FFFFFFF
            val seed3 = (i * 3571 + timeSeed * 7) and 0x7FFFFFFF

            val pseudoRandom1 = (seed1 % 1000) / 1000f
            val pseudoRandom2 = (seed2 % 1000) / 1000f
            val pseudoRandom3 = (seed3 % 1000) / 1000f

            columns.add(MatrixColumn(
                x = i * columnWidth + columnWidth / 2,
                symbols = symbols,
                offset = pseudoRandom1 * height,
                speed = 0.5f + pseudoRandom2 * 1f,
                brightness = 0.3f + pseudoRandom3 * 0.7f
            ))
        }
    }

    private fun drawMatrixRain(canvas: Canvas, width: Int, height: Int) {
        val symbolSize = (width.toFloat() / NUM_COLUMNS) * 0.7f
        symbolPaint.textSize = symbolSize.coerceIn(10f, 24f)

        val step = symbolSize * 1.2f
        // PERFORMANCE: Pre-compute outside loops
        val heightWithBuffer = height + step

        for (column in columns) {
            val symbolCount = column.symbols.size
            val wrapHeight = heightWithBuffer + step * symbolCount
            val columnBrightness = column.brightness

            // PERFORMANCE: Use index-based loop instead of withIndex()
            for (index in 0 until symbolCount) {
                val baseY = (column.offset + index * step) % wrapHeight - step * 3

                // Early skip for off-screen symbols
                if (baseY < -step || baseY > heightWithBuffer) continue

                // Gradient de luminosité (plus brillant en tête)
                val posInColumn = index.toFloat() / symbolCount
                val brightness = (1f - posInColumn) * columnBrightness
                val alpha = (brightness * 180).toInt().coerceIn(5, 180)

                // Le premier symbole est plus brillant (tête de la cascade)
                if (index < 3) {
                    symbolPaint.color = Color.argb((alpha * 1.5f).toInt().coerceAtMost(255), 200, 255, 200)
                } else {
                    symbolPaint.color = Color.argb(alpha, 0, 255, 65)
                }

                // Utilise les symboles uniques de chaque colonne
                val symbol = column.symbols[index % symbolCount]
                canvas.drawText(symbol.toString(), column.x, baseY, symbolPaint)
            }
        }
    }

    private fun drawCRTEffect(canvas: Canvas, width: Int, height: Int) {
        // PERFORMANCE: Draw fewer CRT lines with larger spacing
        crtPaint.color = Color.argb(15, 0, 0, 0)
        val widthFloat = width.toFloat()
        var y = 0f
        while (y < height) {
            canvas.drawRect(0f, y, widthFloat, y + 1.5f, crtPaint)
            y += CRT_LINE_SPACING // PERFORMANCE: Increased spacing (was 3f)
        }

        // PERFORMANCE: Cache vignette shader - only recreate when size changes
        if (vignetteShader == null || cachedVignetteWidth != width || cachedVignetteHeight != height) {
            cachedVignetteWidth = width
            cachedVignetteHeight = height
            val vignetteColors = intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.argb(40, 0, 20, 0)
            )
            vignetteShader = RadialGradient(
                width / 2f, height / 2f,
                (width.coerceAtLeast(height)) * 0.7f,
                vignetteColors,
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        glowPaint.shader = vignetteShader
        canvas.drawRect(0f, 0f, widthFloat, height.toFloat(), glowPaint)
        glowPaint.shader = null

        // Ligne de scan très subtile qui descend
        val scanY = (animationTimeMs * 0.05f) % height
        crtPaint.color = Color.argb(8, 0, 255, 65)
        canvas.drawRect(0f, scanY, widthFloat, scanY + 3f, crtPaint)
    }

    override fun hasParticles() = true
    // PERFORMANCE: Reduced particles (was 15)
    override fun getParticlesPerHit() = 8
    override fun getParticleLifetimeMs() = 800L
    override fun getParticleColor(noteColor: Int) = MATRIX_GREEN_BRIGHT

    // Piano style Matrix
    override fun getWhiteKeyColor() = "#0A1A0A".toColorInt()  // Vert très foncé
    override fun getBlackKeyColor() = Color.BLACK
    override fun getPressedWhiteKeyColor() = "#002200".toColorInt()
    override fun getPressedBlackKeyColor() = "#001500".toColorInt()

    // Partition style Matrix
    override fun getSheetMusicBackgroundColor() = Color.BLACK
    override fun getStaffLineColor() = Color.argb(40, 0, 255, 65)
    override fun getCurrentTimeIndicatorColor() = MATRIX_GREEN
    override fun getSheetMusicSymbolColor() = MATRIX_GREEN_BRIGHT  // Vert Matrix pour les symboles

    private data class MatrixColumn(
        val x: Float,
        val symbols: CharArray,
        var offset: Float,
        val speed: Float,
        val brightness: Float
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as MatrixColumn
            return x == other.x
        }

        override fun hashCode(): Int = x.hashCode()
    }
}
