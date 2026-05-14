package com.Atom2Universe.app.midi.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.cardview.widget.CardView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.practice.themes.PracticeTheme
import com.Atom2Universe.app.midi.practice.themes.PracticeThemeManager

/**
 * Dialog pour configurer le thème personnalisé.
 *
 * Permet de :
 * - Choisir une image de fond depuis la galerie
 * - Sélectionner un thème de base pour les couleurs des notes
 * - Ajuster l'opacité de l'image
 */
class CustomThemeDialog(
    context: Context,
    private val imagePickerLauncher: ActivityResultLauncher<String>,
    private val onThemeConfigured: () -> Unit
) : Dialog(context, R.style.Theme_A2U_Dialog) {

    private val density = context.resources.displayMetrics.density

    private lateinit var opacitySeekBar: SeekBar
    private lateinit var opacityValueText: TextView
    private lateinit var imageStatusText: TextView
    private lateinit var baseThemeContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Titre
        val titleText = TextView(context).apply {
            text = context.getString(R.string.custom_theme_title)
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        rootLayout.addView(titleText)

        // Description
        val descText = TextView(context).apply {
            text = context.getString(R.string.custom_theme_desc)
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        rootLayout.addView(descText)

        // ScrollView pour le contenu
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ========== Section Image ==========
        addSectionTitle(contentLayout, context.getString(R.string.custom_theme_section_image))

        // Statut de l'image
        imageStatusText = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(8), dp(8), dp(8), dp(8))
            updateImageStatus()
        }
        contentLayout.addView(imageStatusText)

        // Boutons image
        val imageButtonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }

        // Bouton choisir image
        val selectImageButton = createButton(
            context.getString(R.string.custom_theme_select_image),
            Color.parseColor("#4CAF50")
        ) {
            // Lancer le picker d'image
            imagePickerLauncher.launch("image/*")
        }
        imageButtonsLayout.addView(selectImageButton)

        // Bouton supprimer image (si existe)
        if (PracticeThemeManager.hasCustomBackground()) {
            val deleteImageButton = createButton(
                context.getString(R.string.custom_theme_delete_image),
                Color.parseColor("#F44336")
            ) {
                PracticeThemeManager.deleteCustomBackground()
                imageStatusText.updateImageStatus()
                onThemeConfigured()
            }
            deleteImageButton.layoutParams = (deleteImageButton.layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = dp(8)
            }
            imageButtonsLayout.addView(deleteImageButton)
        }

        contentLayout.addView(imageButtonsLayout)

        // ========== Section Opacité ==========
        addSectionTitle(contentLayout, context.getString(R.string.custom_theme_section_opacity))

        val opacityLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(16))
        }

        opacitySeekBar = SeekBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            max = 70 // 10% à 80%
            progress = ((PracticeThemeManager.getCustomImageOpacity() - 0.1f) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val opacity = 0.1f + progress / 100f
                    opacityValueText.text = "${(opacity * 100).toInt()}%"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val opacity = 0.1f + (seekBar?.progress ?: 40) / 100f
                    PracticeThemeManager.setCustomImageOpacity(opacity)
                    onThemeConfigured()
                }
            })
        }
        opacityLayout.addView(opacitySeekBar)

        opacityValueText = TextView(context).apply {
            val opacity = PracticeThemeManager.getCustomImageOpacity()
            text = "${(opacity * 100).toInt()}%"
            setTextColor(Color.WHITE)
            textSize = 14f
            minWidth = dp(48)
            gravity = Gravity.END
            setPadding(dp(8), 0, 0, 0)
        }
        opacityLayout.addView(opacityValueText)

        contentLayout.addView(opacityLayout)

        // ========== Section Thème de base ==========
        addSectionTitle(contentLayout, context.getString(R.string.custom_theme_section_base))

        val baseThemeDesc = TextView(context).apply {
            text = context.getString(R.string.custom_theme_base_desc)
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        contentLayout.addView(baseThemeDesc)

        baseThemeContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        populateBaseThemes()
        contentLayout.addView(baseThemeContainer)

        scrollView.addView(contentLayout)
        rootLayout.addView(scrollView)

        // ========== Bouton bas ==========
        val buttonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }

        // Bouton Fermer (applique automatiquement le thème si configuré)
        val closeButton = createButton(
            context.getString(R.string.close),
            Color.parseColor("#BB86FC")
        ) {
            // Sélectionner automatiquement le thème personnalisé si une image est configurée
            if (PracticeThemeManager.hasCustomBackground()) {
                PracticeThemeManager.selectTheme("custom_background")
                onThemeConfigured()
            }
            dismiss()
        }
        buttonsLayout.addView(closeButton)

        rootLayout.addView(buttonsLayout)

        setContentView(rootLayout)

        // Taille du dialog
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.85).toInt()
        )
    }

    private fun TextView.updateImageStatus() {
        text = if (PracticeThemeManager.hasCustomBackground()) {
            context.getString(R.string.custom_theme_image_set)
        } else {
            context.getString(R.string.custom_theme_no_image)
        }
        setTextColor(
            if (PracticeThemeManager.hasCustomBackground())
                Color.parseColor("#4CAF50")
            else
                Color.parseColor("#FF9800")
        )
    }

    private fun addSectionTitle(container: LinearLayout, title: String) {
        val sectionTitle = TextView(context).apply {
            text = title
            setTextColor(Color.parseColor("#BB86FC"))
            textSize = 14f
            setPadding(dp(8), dp(12), dp(8), dp(4))
        }
        container.addView(sectionTitle)
    }

    private fun populateBaseThemes() {
        baseThemeContainer.removeAllViews()

        val currentBaseThemeId = PracticeThemeManager.getCustomBaseThemeId()
        val themes = PracticeThemeManager.getThemesForBaseSelection()

        for (theme in themes) {
            addBaseThemeOption(theme, theme.id == currentBaseThemeId)
        }
    }

    private fun addBaseThemeOption(theme: PracticeTheme, isSelected: Boolean) {
        val card = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(4), dp(2), dp(4), dp(2))
            }
            radius = dp(8).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(
                if (isSelected) Color.parseColor("#2A2A4E")
                else Color.parseColor("#252538")
            )
        }

        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        // Prévisualisation des couleurs
        val colorsPreview = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val previewColors = listOf(
            theme.getNoteColor(0),  // C
            theme.getNoteColor(4),  // E
            theme.getNoteColor(7)   // G
        )

        for (color in previewColors) {
            val colorView = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
            }
            colorsPreview.addView(colorView)
        }
        cardContent.addView(colorsPreview)

        // Nom du thème
        val nameText = TextView(context).apply {
            text = theme.displayName
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(12), 0, dp(8), 0)
        }
        cardContent.addView(nameText)

        // Indicateur de sélection
        if (isSelected) {
            val checkIcon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                setImageResource(R.drawable.ic_check)
                setColorFilter(Color.parseColor("#4CAF50"))
            }
            cardContent.addView(checkIcon)
        }

        card.addView(cardContent)

        // Click listener
        card.setOnClickListener {
            PracticeThemeManager.setCustomBaseTheme(theme.id)
            populateBaseThemes() // Rafraîchir la sélection
            onThemeConfigured()
        }

        baseThemeContainer.addView(card)
    }

    private fun createButton(text: String, bgColor: Int, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(if (bgColor == Color.parseColor("#666666")) Color.WHITE else Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(bgColor)
            }
            setOnClickListener { onClick() }
        }
    }

    /**
     * Appelé quand une image a été sélectionnée
     */
    fun onImageSelected() {
        imageStatusText.updateImageStatus()
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
