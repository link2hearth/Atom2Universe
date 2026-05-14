package com.Atom2Universe.app.pixelart.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * Alignement du texte
 */
enum class TextAlignment {
    LEFT, CENTER, RIGHT
}

/**
 * Style de police
 */
enum class FontStyle(val value: Int) {
    NORMAL(Typeface.NORMAL),
    BOLD(Typeface.BOLD),
    ITALIC(Typeface.ITALIC),
    BOLD_ITALIC(Typeface.BOLD_ITALIC)
}

/**
 * Objet texte éditable sur la toile infinie.
 *
 * Optimisations :
 * - StaticLayout pré-calculé (évite recalcul à chaque frame)
 * - Cache des mesures de texte
 * - TextPaint réutilisé
 */
class TextObject(
    override val id: String = java.util.UUID.randomUUID().toString()
) : CanvasObject(CanvasObjectType.TEXT) {

    // ========== CONTENU ==========

    /**
     * Texte à afficher (supporte multi-lignes avec \n)
     */
    var text: String = "Texte"
        set(value) {
            if (field != value) {
                field = value
                layoutDirty = true
                markDirty()
            }
        }

    // ========== POLICE ==========

    /**
     * Famille de police
     * Valeurs système : "sans-serif", "serif", "monospace", "cursive"
     * Ou nom de police personnalisée
     */
    var fontFamily: String = "sans-serif"
        set(value) {
            if (field != value) {
                field = value
                updateTypeface()
                layoutDirty = true
            }
        }

    /**
     * Taille de police en pixels
     */
    var fontSize: Float = 24f
        set(value) {
            if (field != value) {
                field = value.coerceIn(8f, 500f)
                textPaint.textSize = field
                layoutDirty = true
                markDirty()
            }
        }

    /**
     * Style (normal, gras, italique)
     */
    var fontStyle: FontStyle = FontStyle.NORMAL
        set(value) {
            if (field != value) {
                field = value
                updateTypeface()
                layoutDirty = true
            }
        }

    /**
     * Typeface personnalisé (optionnel)
     */
    var customTypeface: Typeface? = null
        set(value) {
            field = value
            updateTypeface()
            layoutDirty = true
        }

    /**
     * Nom d'affichage de la police (pour sérialisation et UI)
     */
    var fontDisplayName: String = "Sans Serif"

    // ========== COULEURS ==========

    /**
     * Couleur du texte
     */
    var textColor: Int = Color.BLACK
        set(value) {
            field = value
            textPaint.color = value
        }

    /**
     * Couleur de fond (null = transparent)
     */
    var backgroundColor: Int? = null

    /**
     * Couleur du contour du texte (null = pas de contour)
     */
    var outlineColor: Int? = null

    /**
     * Épaisseur du contour
     */
    var outlineWidth: Float = 2f

    // ========== MISE EN PAGE ==========

    /**
     * Alignement horizontal
     */
    var alignment: TextAlignment = TextAlignment.LEFT
        set(value) {
            if (field != value) {
                field = value
                layoutDirty = true
            }
        }

    /**
     * Interligne (1.0 = normal, 1.5 = 150%, etc.)
     */
    var lineSpacing: Float = 1.2f
        set(value) {
            if (field != value) {
                field = value.coerceIn(0.5f, 3f)
                layoutDirty = true
            }
        }

    /**
     * Largeur maximale du bloc de texte (0 = auto)
     */
    var maxWidth: Float = 0f
        set(value) {
            if (field != value) {
                field = value.coerceAtLeast(0f)
                layoutDirty = true
                markDirty()
            }
        }

    /**
     * Padding interne
     */
    var paddingHorizontal: Float = 8f
    var paddingVertical: Float = 4f

    // ========== OPTIMISATION : Pré-allocation ==========

    /**
     * TextPaint pré-alloué
     */
    private val textPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = fontSize
        color = textColor
    }

    /**
     * Paint pour le fond
     */
    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    /**
     * Paint pour le contour du texte
     */
    private val outlinePaint = TextPaint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    /**
     * Layout calculé (cache)
     */
    private var staticLayout: StaticLayout? = null
    private var layoutDirty = true

    /**
     * Dimensions calculées du texte
     */
    private var measuredTextWidth = 0f
    private var measuredTextHeight = 0f

    init {
        updateTypeface()
    }

    /**
     * Met à jour la typeface selon les propriétés
     */
    private fun updateTypeface() {
        val tf = customTypeface ?: Typeface.create(fontFamily, fontStyle.value)
        textPaint.typeface = tf
        outlinePaint.typeface = tf
    }

    /**
     * Recalcule le layout si nécessaire
     */
    private fun rebuildLayoutIfNeeded() {
        if (!layoutDirty) return

        textPaint.textSize = fontSize
        textPaint.color = textColor

        // Calculer la largeur
        val layoutWidth = if (maxWidth > 0f) {
            maxWidth.toInt()
        } else {
            // Mesurer chaque ligne pour trouver la plus large
            val lines = text.split("\n")
            var maxLineWidth = 0f
            for (line in lines) {
                val lineWidth = textPaint.measureText(line)
                if (lineWidth > maxLineWidth) {
                    maxLineWidth = lineWidth
                }
            }
            (maxLineWidth + paddingHorizontal * 2).toInt().coerceAtLeast(1)
        }

        // Alignement
        val align = when (alignment) {
            TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

        // Créer le StaticLayout
        staticLayout = StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, layoutWidth)
            .setAlignment(align)
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(true)
            .build()

        // Mettre à jour les dimensions
        staticLayout?.let { layout ->
            measuredTextWidth = 0f
            for (i in 0 until layout.lineCount) {
                val lineWidth = layout.getLineWidth(i)
                if (lineWidth > measuredTextWidth) {
                    measuredTextWidth = lineWidth
                }
            }
            measuredTextHeight = layout.height.toFloat()

            // Mettre à jour width/height de l'objet
            width = measuredTextWidth + paddingHorizontal * 2
            height = measuredTextHeight + paddingVertical * 2
        }

        layoutDirty = false
    }

    // ========== DESSIN ==========

    override fun draw(canvas: Canvas, paint: Paint, selected: Boolean) {
        if (!visible || opacity <= 0f) return
        if (text.isEmpty()) return

        rebuildLayoutIfNeeded()
        val layout = staticLayout ?: return

        canvas.save()

        // Appliquer les transformations
        canvas.translate(x, y)
        if (rotation != 0f) {
            canvas.rotate(rotation)
        }
        if (scaleX != 1f || scaleY != 1f) {
            canvas.scale(scaleX, scaleY)
        }

        val halfW = width / 2f
        val halfH = height / 2f

        // Dessiner le fond si défini
        backgroundColor?.let { bgColor ->
            bgPaint.color = bgColor
            bgPaint.alpha = (opacity * 255 * (Color.alpha(bgColor) / 255f)).toInt()
            canvas.drawRect(-halfW, -halfH, halfW, halfH, bgPaint)
        }

        // Positionner le texte
        canvas.translate(-halfW + paddingHorizontal, -halfH + paddingVertical)

        // Dessiner le contour du texte si défini
        outlineColor?.let { outColor ->
            outlinePaint.textSize = fontSize
            outlinePaint.color = outColor
            outlinePaint.strokeWidth = outlineWidth
            outlinePaint.alpha = (opacity * 255).toInt()

            // Dessiner chaque ligne avec contour
            val lines = text.split("\n")
            var yOffset = textPaint.textSize
            for (line in lines) {
                val xOffset = when (alignment) {
                    TextAlignment.LEFT -> 0f
                    TextAlignment.CENTER -> (measuredTextWidth - textPaint.measureText(line)) / 2f
                    TextAlignment.RIGHT -> measuredTextWidth - textPaint.measureText(line)
                }
                canvas.drawText(line, xOffset, yOffset, outlinePaint)
                yOffset += fontSize * lineSpacing
            }
        }

        // Dessiner le texte principal
        textPaint.alpha = (opacity * 255).toInt()
        layout.draw(canvas)

        canvas.restore()
        // Note: les handles de sélection sont dessinés par InfiniteCanvas.drawSelection()
    }

    // ========== ÉDITION ==========

    /**
     * Ajoute du texte à la position du curseur
     */
    fun appendText(newText: String) {
        text += newText
    }

    /**
     * Remplace tout le texte
     */
    fun replaceText(newText: String) {
        text = newText
    }

    /**
     * Obtient le nombre de lignes
     */
    fun getLineCount(): Int {
        rebuildLayoutIfNeeded()
        return staticLayout?.lineCount ?: 1
    }

    // ========== UTILITAIRES ==========

    override fun calculateBounds(outBounds: RectF) {
        rebuildLayoutIfNeeded()
        super.calculateBounds(outBounds)
    }

    override fun copy(): CanvasObject {
        val copy = TextObject()
        copy.x = x
        copy.y = y
        copy.rotation = rotation
        copy.scaleX = scaleX
        copy.scaleY = scaleY
        copy.opacity = opacity
        copy.visible = visible
        copy.locked = locked
        copy.zIndex = zIndex
        copy.name = name
        copy.text = text
        copy.fontFamily = fontFamily
        copy.fontSize = fontSize
        copy.fontStyle = fontStyle
        copy.customTypeface = customTypeface
        copy.fontDisplayName = fontDisplayName
        copy.textColor = textColor
        copy.backgroundColor = backgroundColor
        copy.outlineColor = outlineColor
        copy.outlineWidth = outlineWidth
        copy.alignment = alignment
        copy.lineSpacing = lineSpacing
        copy.maxWidth = maxWidth
        copy.paddingHorizontal = paddingHorizontal
        copy.paddingVertical = paddingVertical
        return copy
    }

    companion object {
        /**
         * Polices système disponibles
         */
        val SYSTEM_FONTS = listOf(
            "sans-serif" to "Sans Serif",
            "sans-serif-light" to "Sans Serif Light",
            "sans-serif-medium" to "Sans Serif Medium",
            "sans-serif-condensed" to "Sans Serif Condensed",
            "serif" to "Serif",
            "monospace" to "Monospace",
            "cursive" to "Cursive"
        )

        /**
         * Crée un texte simple
         */
        fun create(text: String, x: Float, y: Float, fontSize: Float = 24f): TextObject {
            return TextObject().apply {
                this.text = text
                this.x = x
                this.y = y
                this.fontSize = fontSize
            }
        }
    }
}
