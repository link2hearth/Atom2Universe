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
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.practice.themes.CustomBackgroundTheme
import com.Atom2Universe.app.midi.practice.themes.PracticeTheme
import com.Atom2Universe.app.midi.practice.themes.PracticeThemeManager

/**
 * Dialog pour sélectionner un thème visuel pour le mode practice.
 *
 * Affiche tous les thèmes avec prévisualisation des couleurs.
 */
class ThemeSelectionDialog(
    context: Context,
    private val onThemeSelected: (PracticeTheme) -> Unit,
    private val onCustomThemeConfigureRequested: (() -> Unit)? = null
) : Dialog(context, R.style.Theme_A2U_Dialog) {

    private val density = context.resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Titre
        val titleText = TextView(context).apply {
            text = context.getString(R.string.theme_selection_title)
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        rootLayout.addView(titleText)

        // ScrollView pour les thèmes
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val themesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (theme in PracticeThemeManager.getAllThemes()) {
            if (theme is CustomBackgroundTheme) {
                addCustomThemeCard(themesContainer, theme)
            } else {
                addThemeCard(themesContainer, theme)
            }
        }

        scrollView.addView(themesContainer)
        rootLayout.addView(scrollView)

        // Bouton fermer
        val closeButton = TextView(context).apply {
            text = context.getString(R.string.close)
            setTextColor(Color.parseColor("#BB86FC"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(8))
            setOnClickListener { dismiss() }
        }
        rootLayout.addView(closeButton)

        setContentView(rootLayout)

        // Taille du dialog
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.8).toInt()
        )
    }

    private fun addThemeCard(container: LinearLayout, theme: PracticeTheme) {
        val isSelected = PracticeThemeManager.getCurrentTheme().id == theme.id

        val card = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            radius = dp(12).toFloat()
            cardElevation = dp(4).toFloat()
            setCardBackgroundColor(
                if (isSelected) Color.parseColor("#2A2A4E")
                else Color.parseColor("#252538")
            )
        }

        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // Prévisualisation des couleurs (3 cercles)
        val colorsPreview = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val (bgTop, bgBottom) = theme.getBackgroundColors()
        val previewColors = listOf(
            bgTop,
            theme.getNoteColor(0),  // C
            theme.getNoteColor(4),  // E
            theme.getNoteColor(7)   // G
        )

        for (color in previewColors) {
            val colorView = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(1), Color.parseColor("#40FFFFFF"))
                }
            }
            colorsPreview.addView(colorView)
        }
        cardContent.addView(colorsPreview)

        // Textes
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(12), 0, dp(8), 0)
        }

        val nameText = TextView(context).apply {
            text = theme.displayName
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        textContainer.addView(nameText)

        val descText = TextView(context).apply {
            text = theme.description
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
        }
        textContainer.addView(descText)

        cardContent.addView(textContainer)

        // Icône de statut
        val statusIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        }

        if (isSelected) {
            statusIcon.setImageResource(R.drawable.ic_check)
            statusIcon.setColorFilter(Color.parseColor("#4CAF50"))
        } else {
            statusIcon.visibility = View.GONE
        }
        cardContent.addView(statusIcon)

        card.addView(cardContent)

        card.setOnClickListener {
            if (PracticeThemeManager.selectTheme(theme.id)) {
                onThemeSelected(theme)
                dismiss()
            }
        }

        container.addView(card)
    }

    /**
     * Ajoute une carte spéciale pour le thème personnalisé
     * avec un bouton "Configurer" au lieu de sélectionner directement
     */
    private fun addCustomThemeCard(container: LinearLayout, theme: CustomBackgroundTheme) {
        val isSelected = PracticeThemeManager.getCurrentTheme().id == theme.id
        val hasImage = PracticeThemeManager.hasCustomBackground()

        val card = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            radius = dp(12).toFloat()
            cardElevation = dp(4).toFloat()
            setCardBackgroundColor(
                if (isSelected) Color.parseColor("#2A2A4E")
                else Color.parseColor("#252538")
            )
        }

        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // Icône personnalisée (image ou placeholder)
        val iconView = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                marginEnd = dp(12)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#3A3A5E"))
                setStroke(dp(2), Color.parseColor("#BB86FC"))
            }
        }
        cardContent.addView(iconView)

        // Textes
        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameText = TextView(context).apply {
            text = theme.displayName
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        textContainer.addView(nameText)

        val descText = TextView(context).apply {
            text = if (hasImage) {
                "${theme.description} - ${context.getString(R.string.custom_theme_image_set)}"
            } else {
                theme.description
            }
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
        }
        textContainer.addView(descText)

        // Thème de base info
        val baseTheme = theme.getBaseTheme()
        val baseInfo = TextView(context).apply {
            text = "Base: ${baseTheme.displayName}"
            setTextColor(Color.parseColor("#BB86FC"))
            textSize = 11f
            setPadding(0, dp(2), 0, 0)
        }
        textContainer.addView(baseInfo)

        cardContent.addView(textContainer)

        // Bouton Configurer
        val configureButton = TextView(context).apply {
            text = context.getString(R.string.custom_theme_configure)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(20), dp(12), dp(20), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#BB86FC"))
            }
            setOnClickListener {
                dismiss()
                onCustomThemeConfigureRequested?.invoke()
            }
        }
        cardContent.addView(configureButton)

        if (isSelected) {
            val checkIcon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    marginStart = dp(8)
                }
                setImageResource(R.drawable.ic_check)
                setColorFilter(Color.parseColor("#4CAF50"))
            }
            cardContent.addView(checkIcon)
        }

        card.addView(cardContent)

        card.setOnClickListener {
            if (hasImage) {
                // Sélectionner directement si déjà configuré
                if (PracticeThemeManager.selectTheme(theme.id)) {
                    onThemeSelected(theme)
                    dismiss()
                }
            } else {
                // Ouvrir la configuration si pas d'image
                dismiss()
                onCustomThemeConfigureRequested?.invoke()
            }
        }

        container.addView(card)
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
