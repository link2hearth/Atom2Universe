package com.Atom2Universe.app.midi.ui

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.use
import androidx.core.graphics.toColorInt
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.fluidsynth.FluidSynthEngine
import com.Atom2Universe.app.midi.repository.SettingsRepository
import com.Atom2Universe.app.midi.scanner.SoundFontManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

/**
 * Dialog pour sélectionner le moteur de synthèse MIDI.
 *
 * Affiche 3 cartes moteur :
 * - Sonivox : synthétiseur intégré, toujours disponible
 * - A2U : moteur SF2 maison, nécessite un SoundFont
 * - FluidSynth : moteur SF2 open-source, nécessite un SoundFont
 *
 * Inclut une carte SoundFont pour gérer le fichier SF2 importé,
 * et un bouton "Mode Hybride" pour combiner Sonivox et SF2.
 *
 * Quand le mode hybride est actif, une icône shuffle s'affiche
 * sur les cartes Sonivox et du moteur SF2 utilisé.
 */
class SynthesizerSelectionDialog : DialogFragment() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var soundFontManager: SoundFontManager

    // Engine cards
    private lateinit var cardSonivox: MaterialCardView
    private lateinit var cardA2u: MaterialCardView
    private lateinit var cardFluidsynth: MaterialCardView

    // Active badges
    private lateinit var badgeSonivox: TextView
    private lateinit var badgeA2u: TextView
    private lateinit var badgeFluidsynth: TextView

    // Hybrid icons on engine cards
    private lateinit var iconHybridSonivox: ImageView
    private lateinit var iconHybridA2u: ImageView
    private lateinit var iconHybridFluidsynth: ImageView

    // SoundFont card
    private lateinit var cardSoundfont: MaterialCardView
    private lateinit var layoutSf2Loaded: LinearLayout
    private lateinit var layoutSf2Empty: LinearLayout
    private lateinit var textSf2Filename: TextView
    private lateinit var buttonChangeSf2: MaterialButton
    private lateinit var buttonRemoveSf2: MaterialButton
    private lateinit var buttonImportSf2: MaterialButton

    // Other views
    private lateinit var textSf2Tip: TextView
    private lateinit var buttonHybridMode: MaterialButton

    // State
    private var currentSf2Path: String? = null
    private var currentSf2Label: String? = null
    private var currentSynthMode: String = SettingsRepository.SYNTH_MODE_SONIVOX
    // Which SF2 engine is used as base for hybrid mode ("sf2" or "fluidsynth")
    private var hybridBaseEngine: String = SettingsRepository.SYNTH_MODE_SF2

    // Theme colors
    private var accentColor: Int = 0
    private var inactiveStrokeColor: Int = 0
    private var secondaryTextColor: Int = 0

    // Callbacks
    var onSonivoxSelected: (() -> Unit)? = null
    var onImportSf2Requested: (() -> Unit)? = null
    var onSf2Selected: ((String) -> Unit)? = null
    var onSf2Removed: (() -> Unit)? = null
    var onHybridSelected: ((String, Set<Int>, Boolean) -> Unit)? = null
    var onFluidSynthSelected: ((String) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        settingsRepository = SettingsRepository(requireContext())
        soundFontManager = SoundFontManager(requireContext(), settingsRepository)

        resolveColors()

        val view = layoutInflater.inflate(R.layout.dialog_synthesizer_selection, null)
        setupViews(view)

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.midi_synthesizer_selection)
            .setView(view)
            .setPositiveButton(R.string.close, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        loadCurrentState()
    }

    private fun resolveColors() {
        val context = requireContext()

        // Resolve accent color from custom theme attribute
        context.obtainStyledAttributes(intArrayOf(R.attr.a2uMidiAccent)).use { typedArray ->
            accentColor = typedArray.getColor(0, "#6200EE".toColorInt())
        }

        // Resolve inactive stroke color from text hint color (theme-aware)
        context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorHint)).use { typedArray ->
            inactiveStrokeColor = typedArray.getColor(0, Color.GRAY)
        }

        // Resolve secondary text color for hybrid button inactive state
        context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary)).use { typedArray ->
            secondaryTextColor = typedArray.getColor(0, Color.GRAY)
        }
    }

    private fun setupViews(view: View) {
        // Engine cards
        cardSonivox = view.findViewById(R.id.card_sonivox)
        cardA2u = view.findViewById(R.id.card_a2u)
        cardFluidsynth = view.findViewById(R.id.card_fluidsynth)

        // Badges
        badgeSonivox = view.findViewById(R.id.badge_sonivox)
        badgeA2u = view.findViewById(R.id.badge_a2u)
        badgeFluidsynth = view.findViewById(R.id.badge_fluidsynth)

        // Apply accent-colored rounded background to badges
        setupBadge(badgeSonivox)
        setupBadge(badgeA2u)
        setupBadge(badgeFluidsynth)

        // Hybrid icons
        iconHybridSonivox = view.findViewById(R.id.icon_hybrid_sonivox)
        iconHybridA2u = view.findViewById(R.id.icon_hybrid_a2u)
        iconHybridFluidsynth = view.findViewById(R.id.icon_hybrid_fluidsynth)

        // SoundFont card
        cardSoundfont = view.findViewById(R.id.card_soundfont)
        layoutSf2Loaded = view.findViewById(R.id.layout_sf2_loaded)
        layoutSf2Empty = view.findViewById(R.id.layout_sf2_empty)
        textSf2Filename = view.findViewById(R.id.text_sf2_filename)
        buttonChangeSf2 = view.findViewById(R.id.button_change_sf2)
        buttonRemoveSf2 = view.findViewById(R.id.button_remove_sf2)
        buttonImportSf2 = view.findViewById(R.id.button_import_sf2)

        // Other
        textSf2Tip = view.findViewById(R.id.text_sf2_tip)
        buttonHybridMode = view.findViewById(R.id.button_hybrid_mode)

        // Engine card click listeners
        cardSonivox.setOnClickListener { selectEngine(SettingsRepository.SYNTH_MODE_SONIVOX) }
        cardA2u.setOnClickListener { selectEngine(SettingsRepository.SYNTH_MODE_SF2) }
        cardFluidsynth.setOnClickListener { selectEngine(SettingsRepository.SYNTH_MODE_FLUIDSYNTH) }

        // SoundFont buttons
        buttonImportSf2.setOnClickListener {
            onImportSf2Requested?.invoke()
        }

        buttonChangeSf2.setOnClickListener {
            onImportSf2Requested?.invoke()
        }

        buttonRemoveSf2.setOnClickListener {
            removeSf2()
        }

        // Hybrid mode button → opens instrument selection dialog
        buttonHybridMode.setOnClickListener {
            openHybridInstrumentSelection()
        }

        // Set SoundFont card stroke to inactive color
        cardSoundfont.strokeColor = inactiveStrokeColor
        cardSoundfont.strokeWidth = dpToPx(1)
    }

    private fun setupBadge(badge: TextView) {
        val bg = GradientDrawable().apply {
            setColor(accentColor)
            cornerRadius = dpToPx(10).toFloat()
        }
        badge.background = bg
    }

    private fun loadCurrentState() {
        lifecycleScope.launch {
            val path = soundFontManager.getCurrentSoundFontPath()
            currentSf2Path = if (path.isNullOrBlank()) null else path
            val label = settingsRepository.getSoundFontLabel()
            currentSf2Label = if (label.isNullOrBlank()) null else label
            currentSynthMode = settingsRepository.getSynthMode()
            hybridBaseEngine = settingsRepository.getHybridBaseEngine()

            updateUI()
        }
    }

    private fun updateUI() {
        val hasSf2 = currentSf2Path != null
        val isHybrid = currentSynthMode == SettingsRepository.SYNTH_MODE_HYBRID

        // Update SoundFont card content
        updateSoundFontUI(hasSf2)

        // Update engine cards enabled/disabled state
        updateEngineCardsState(hasSf2)

        // Update which card is highlighted as active
        updateActiveCard()

        // Update hybrid icons on engine cards
        updateHybridIcons(isHybrid)

        // Update hybrid mode button state
        updateHybridButton(hasSf2, isHybrid)

        // Always show the SF2 tip
        textSf2Tip.visibility = View.VISIBLE
    }

    private fun updateSoundFontUI(hasSf2: Boolean) {
        if (hasSf2 && currentSf2Label != null) {
            layoutSf2Loaded.visibility = View.VISIBLE
            layoutSf2Empty.visibility = View.GONE
            textSf2Filename.text = currentSf2Label
        } else {
            layoutSf2Loaded.visibility = View.GONE
            layoutSf2Empty.visibility = View.VISIBLE
        }
    }

    private fun updateEngineCardsState(hasSf2: Boolean) {
        // A2U requires a loaded SF2
        cardA2u.isClickable = hasSf2
        cardA2u.isFocusable = hasSf2
        cardA2u.alpha = if (hasSf2) 1.0f else 0.4f

        // FluidSynth requires both SF2 and native library
        val fluidSynthAvailable = hasSf2 && FluidSynthEngine.isSupported()
        cardFluidsynth.visibility = if (FluidSynthEngine.isSupported()) View.VISIBLE else View.GONE
        cardFluidsynth.isClickable = fluidSynthAvailable
        cardFluidsynth.isFocusable = fluidSynthAvailable
        cardFluidsynth.alpha = if (fluidSynthAvailable) 1.0f else 0.4f

        // Sonivox is always available
        cardSonivox.isClickable = true
        cardSonivox.isFocusable = true
        cardSonivox.alpha = 1.0f
    }

    private fun updateActiveCard() {
        // Deselect all cards
        deselectCard(cardSonivox, badgeSonivox)
        deselectCard(cardA2u, badgeA2u)
        deselectCard(cardFluidsynth, badgeFluidsynth)

        // Select the active card based on current mode
        when (currentSynthMode) {
            SettingsRepository.SYNTH_MODE_SONIVOX -> {
                selectCardVisual(cardSonivox, badgeSonivox)
            }
            SettingsRepository.SYNTH_MODE_SF2 -> {
                if (currentSf2Path != null) selectCardVisual(cardA2u, badgeA2u)
                else selectCardVisual(cardSonivox, badgeSonivox)
            }
            SettingsRepository.SYNTH_MODE_HYBRID -> {
                // Hybrid uses BOTH Sonivox and the SF2 engine → highlight both
                selectCardVisual(cardSonivox, badgeSonivox)
                if (currentSf2Path != null) {
                    if (hybridBaseEngine == SettingsRepository.SYNTH_MODE_FLUIDSYNTH) {
                        selectCardVisual(cardFluidsynth, badgeFluidsynth)
                    } else {
                        selectCardVisual(cardA2u, badgeA2u)
                    }
                }
            }
            SettingsRepository.SYNTH_MODE_FLUIDSYNTH -> {
                // Fallback to A2U SF2 if FluidSynth not available, or Sonivox if no SF2
                when {
                    currentSf2Path != null && FluidSynthEngine.isSupported() ->
                        selectCardVisual(cardFluidsynth, badgeFluidsynth)
                    currentSf2Path != null ->
                        selectCardVisual(cardA2u, badgeA2u)
                    else ->
                        selectCardVisual(cardSonivox, badgeSonivox)
                }
            }
        }
    }

    /**
     * Shows/hides the hybrid shuffle icons on the engine cards.
     * When hybrid is active, shows the icon on Sonivox AND the active SF2 engine.
     */
    private fun updateHybridIcons(isHybrid: Boolean) {
        // Hide all hybrid icons first
        iconHybridSonivox.visibility = View.GONE
        iconHybridA2u.visibility = View.GONE
        iconHybridFluidsynth.visibility = View.GONE

        if (isHybrid && currentSf2Path != null) {
            // Hybrid uses both Sonivox and the SF2 engine → show icon on both
            iconHybridSonivox.visibility = View.VISIBLE
            // Only show FluidSynth icon if library is available
            if (hybridBaseEngine == SettingsRepository.SYNTH_MODE_FLUIDSYNTH && FluidSynthEngine.isSupported()) {
                iconHybridFluidsynth.visibility = View.VISIBLE
            } else {
                iconHybridA2u.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Updates the hybrid mode button appearance:
     * - Sonivox mode: grayed out, non-clickable
     * - SF2/FluidSynth mode (not hybrid): gray, clickable
     * - Hybrid active: accent colored, clickable
     */
    private fun updateHybridButton(hasSf2: Boolean, isHybrid: Boolean) {
        val isSonivoxMode = currentSynthMode == SettingsRepository.SYNTH_MODE_SONIVOX

        when {
            isHybrid -> {
                // Hybrid active: accent color, clickable
                buttonHybridMode.isClickable = true
                buttonHybridMode.isFocusable = true
                buttonHybridMode.alpha = 1.0f
                buttonHybridMode.setTextColor(accentColor)
                buttonHybridMode.iconTint = ColorStateList.valueOf(accentColor)
            }
            isSonivoxMode || !hasSf2 -> {
                // Sonivox mode or no SF2: grayed out, non-clickable
                buttonHybridMode.isClickable = false
                buttonHybridMode.isFocusable = false
                buttonHybridMode.alpha = 0.38f
                buttonHybridMode.setTextColor(secondaryTextColor)
                buttonHybridMode.iconTint = ColorStateList.valueOf(secondaryTextColor)
            }
            else -> {
                // SF2 engine selected, not hybrid: gray, clickable
                buttonHybridMode.isClickable = true
                buttonHybridMode.isFocusable = true
                buttonHybridMode.alpha = 1.0f
                buttonHybridMode.setTextColor(secondaryTextColor)
                buttonHybridMode.iconTint = ColorStateList.valueOf(secondaryTextColor)
            }
        }
    }

    private fun selectCardVisual(card: MaterialCardView, badge: TextView) {
        card.strokeColor = accentColor
        card.strokeWidth = dpToPx(2)
        badge.visibility = View.VISIBLE
    }

    private fun deselectCard(card: MaterialCardView, badge: TextView) {
        card.strokeColor = inactiveStrokeColor
        card.strokeWidth = dpToPx(1)
        badge.visibility = View.GONE
    }

    private fun selectEngine(mode: String) {
        lifecycleScope.launch {
            settingsRepository.setSynthMode(mode)
            settingsRepository.setHybridModeEnabled(false)
            currentSynthMode = mode

            when (mode) {
                SettingsRepository.SYNTH_MODE_SONIVOX -> {
                    onSonivoxSelected?.invoke()
                }
                SettingsRepository.SYNTH_MODE_SF2 -> {
                    currentSf2Path?.let { onSf2Selected?.invoke(it) }
                }
                SettingsRepository.SYNTH_MODE_FLUIDSYNTH -> {
                    currentSf2Path?.let { onFluidSynthSelected?.invoke(it) }
                }
            }

            updateUI()
        }
    }

    /**
     * Opens the hybrid instrument selection dialog.
     * Hybrid mode is only activated when the user confirms the selection.
     */
    private fun openHybridInstrumentSelection() {
        // Remember which SF2 engine we're coming from (sf2 or fluidsynth)
        val baseEngine = when (currentSynthMode) {
            SettingsRepository.SYNTH_MODE_FLUIDSYNTH -> SettingsRepository.SYNTH_MODE_FLUIDSYNTH
            SettingsRepository.SYNTH_MODE_HYBRID -> hybridBaseEngine // Keep existing base
            else -> SettingsRepository.SYNTH_MODE_SF2
        }

        val dialog = HybridProgramSelectionDialog.newInstance()
        dialog.onSelectionChanged = { programs, useSf2ForDrums ->
            lifecycleScope.launch {
                if (programs.isNotEmpty() && currentSf2Path != null) {
                    hybridBaseEngine = baseEngine
                    settingsRepository.setHybridBaseEngine(baseEngine)
                    settingsRepository.setSynthMode(SettingsRepository.SYNTH_MODE_HYBRID)
                    settingsRepository.setHybridModeEnabled(true)
                    currentSynthMode = SettingsRepository.SYNTH_MODE_HYBRID
                    onHybridSelected?.invoke(currentSf2Path!!, programs, useSf2ForDrums)
                    updateUI()
                }
            }
        }
        dialog.onCancelled = {
            // Cancel deactivates hybrid mode → revert to the SF2 engine we came from
            lifecycleScope.launch {
                if (currentSynthMode == SettingsRepository.SYNTH_MODE_HYBRID) {
                    settingsRepository.setSynthMode(baseEngine)
                    settingsRepository.setHybridModeEnabled(false)
                    currentSynthMode = baseEngine
                    currentSf2Path?.let { path ->
                        if (baseEngine == SettingsRepository.SYNTH_MODE_FLUIDSYNTH) {
                            onFluidSynthSelected?.invoke(path)
                        } else {
                            onSf2Selected?.invoke(path)
                        }
                    }
                }
                updateUI()
            }
        }
        dialog.show(parentFragmentManager, HybridProgramSelectionDialog.TAG)
    }

    private fun removeSf2() {
        lifecycleScope.launch {
            soundFontManager.removeSoundFont()
            settingsRepository.setSynthMode(SettingsRepository.SYNTH_MODE_SONIVOX)
            settingsRepository.setHybridModeEnabled(false)

            currentSf2Path = null
            currentSf2Label = null
            currentSynthMode = SettingsRepository.SYNTH_MODE_SONIVOX

            updateUI()

            // Notify callback (don't dismiss, let user see the result)
            onSf2Removed?.invoke()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    /**
     * Rafraîchit l'état du dialogue après un changement externe (ex: import SF2).
     * Recharge les données depuis le repository et met à jour l'UI.
     */
    fun refreshState() {
        loadCurrentState()
    }

    companion object {
        const val TAG = "SynthesizerSelectionDialog"

        fun newInstance() = SynthesizerSelectionDialog()
    }
}
