package com.Atom2Universe.app.music.lyrics

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.model.MusicTrack
import com.Atom2Universe.app.music.lyrics.api.AlternativeLyrics
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bottom sheet pour afficher et éditer les paroles d'un titre.
 */
class LyricsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var track: MusicTrack
    private lateinit var lyricsText: EditText
    private lateinit var lyricsScrollView: NestedScrollView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSearchAuto: Button
    private lateinit var btnSearchManual: Button
    private lateinit var btnSave: Button
    private lateinit var btnEdit: Button

    private var isEditing = false
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null

    // Navigation entre alternatives
    private lateinit var alternativesNavLayout: View
    private lateinit var btnPrevAlt: ImageButton
    private lateinit var btnNextAlt: ImageButton
    private lateinit var tvAltCounter: TextView

    private var alternativesList: List<AlternativeLyrics> = emptyList()
    private var currentAltIndex = 0
    private var isAlternativesMode = false
    private var savedByButton = false
    private var hasNavigated = false

    companion object {
        private const val TAG = "LyricsBottomSheet"
        const val RESULT_KEY = "lyrics_bottom_sheet_result"
        const val RESULT_TRACK_ID = "lyrics_bottom_sheet_track_id"
        const val RESULT_LYRICS = "lyrics_bottom_sheet_lyrics"

        fun newInstance(track: MusicTrack): LyricsBottomSheet {
            return LyricsBottomSheet().apply {
                this.track = track
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        // Configurer le dialog pour se redimensionner quand le clavier apparaît
        @Suppress("DEPRECATION")
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let {
                bottomSheetBehavior = BottomSheetBehavior.from(it)
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
                bottomSheetBehavior?.skipCollapsed = true

                // Écouter les WindowInsets pour ajuster le padding quand le clavier apparaît
                ViewCompat.setOnApplyWindowInsetsListener(it) { view, insets ->
                    val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

                    // Ajouter un padding en bas égal à la hauteur du clavier
                    view.setPadding(
                        view.paddingLeft,
                        view.paddingTop,
                        view.paddingRight,
                        imeInsets.bottom.coerceAtLeast(systemBarsInsets.bottom)
                    )

                    insets
                }
            }
        }

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_lyrics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        loadLyrics()
    }

    private fun initViews(view: View) {
        lyricsText = view.findViewById(R.id.lyrics_text)
        lyricsScrollView = view.findViewById(R.id.lyrics_scroll_view)
        statusText = view.findViewById(R.id.status_text)
        progressBar = view.findViewById(R.id.progress_bar)
        btnSearchAuto = view.findViewById(R.id.btn_search_auto)
        btnSearchManual = view.findViewById(R.id.btn_search_manual)
        btnSave = view.findViewById(R.id.btn_save)
        btnEdit = view.findViewById(R.id.btn_edit)
        alternativesNavLayout = view.findViewById(R.id.alternatives_nav_layout)
        btnPrevAlt = view.findViewById(R.id.btn_prev_alt)
        btnNextAlt = view.findViewById(R.id.btn_next_alt)
        tvAltCounter = view.findViewById(R.id.tv_alt_counter)
    }

    private fun setupListeners() {
        btnSearchAuto.setOnClickListener { searchAutomatic() }
        btnSearchManual.setOnClickListener { searchManual() }
        btnSave.setOnClickListener { saveLyrics() }
        btnEdit.setOnClickListener { toggleEditMode() }
        btnPrevAlt.setOnClickListener { navigateAlternative(-1) }
        btnNextAlt.setOnClickListener { navigateAlternative(1) }

        // Empêcher le BottomSheet de se fermer quand on scroll dans le texte
        lyricsScrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Désactiver le drag du BottomSheet quand on touche la zone de texte
                    bottomSheetBehavior?.isDraggable = false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Réactiver le drag du BottomSheet
                    bottomSheetBehavior?.isDraggable = true
                }
            }
            false // Ne pas consommer l'événement pour permettre le scroll
        }
    }

    private fun searchAutomatic() {
        lifecycleScope.launch {
            setLoading(true)
            statusText.text = getString(R.string.lyrics_searching)

            val result = LyricsManager.fetchLyricsOnline(track, forceRefresh = true)

            when (result) {
                is com.Atom2Universe.app.music.lyrics.api.LyricsResult.Success -> {
                    lyricsText.setText(result.lyrics)
                    statusText.text = getString(R.string.lyrics_found_from, result.source)

                    // Si plusieurs résultats disponibles, activer la navigation entre alternatives
                    if (result.alternatives.isNotEmpty()) {
                        val allResults = mutableListOf<AlternativeLyrics>()
                        allResults.add(AlternativeLyrics(result.lyrics, result.source, result.isSynced))
                        allResults.addAll(result.alternatives)
                        setupAlternativesNav(allResults)
                    }

                    Snackbar.make(
                        requireView(),
                        getString(R.string.lyrics_found_from, result.source),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                is com.Atom2Universe.app.music.lyrics.api.LyricsResult.NotFound -> {
                    statusText.text = getString(R.string.lyrics_not_found_online)
                    Snackbar.make(
                        requireView(),
                        R.string.lyrics_not_found_online,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                is com.Atom2Universe.app.music.lyrics.api.LyricsResult.RateLimited -> {
                    statusText.text = getString(R.string.lyrics_error_network)
                    Snackbar.make(
                        requireView(),
                        R.string.lyrics_error_rate_limited,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                is com.Atom2Universe.app.music.lyrics.api.LyricsResult.Error -> {
                    val msg = result.message
                    statusText.text = getString(R.string.lyrics_error_network)
                    Snackbar.make(
                        requireView(),
                        getString(R.string.lyrics_error_connection, msg),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }

            setLoading(false)
        }
    }

    private fun searchManual() {
        // Lancer la recherche web dans une coroutine pour lire les tags ID3 d'abord
        lifecycleScope.launch {
            // Lire les métadonnées depuis les tags ID3 (priorité sur MediaStore)
            val metadata = LyricsUtils.getMetadataFromFile(track)
            val actualTitle = metadata?.title ?: track.title
            val actualArtist = metadata?.artist ?: track.artist

            val intent = LyricsWebSearchActivity.createIntent(
                requireContext(),
                actualTitle,
                actualArtist
            )
            startActivity(intent)
        }
    }

    private fun loadLyrics() {
        lifecycleScope.launch {
            setLoading(true)
            statusText.text = getString(R.string.lyrics_loading)

            // Charger depuis le cache ou le fichier
            val lyrics = LyricsManager.getLyrics(track)

            if (lyrics != null) {
                lyricsText.setText(lyrics)
                statusText.text = getString(R.string.lyrics_loaded_from_cache)

                // Restaurer la navigation si des alternatives sont encore en cache (10 min TTL)
                val cachedAlternatives = LyricsAlternativesCache.get(track.id)
                if (!cachedAlternatives.isNullOrEmpty()) {
                    setupAlternativesNav(cachedAlternatives)
                    // Retrouver l'index correspondant aux lyrics actuellement affichées
                    val matchIndex = cachedAlternatives.indexOfFirst { it.lyrics.trim() == lyrics.trim() }
                    if (matchIndex > 0) {
                        currentAltIndex = matchIndex
                        updateAltCounter()
                        updateAltNavButtons()
                    }
                }
            } else {
                lyricsText.setText("")
                statusText.text = getString(R.string.lyrics_not_found)
            }

            setLoading(false)
        }
    }

    private fun saveLyrics() {
        val lyrics = lyricsText.text.toString().trim()

        if (lyrics.isEmpty()) {
            Snackbar.make(
                requireView(),
                R.string.lyrics_empty_cannot_save,
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            statusText.text = getString(R.string.lyrics_saving)

            val success = LyricsManager.saveLyrics(track, lyrics, "manual")

            if (success) {
                statusText.text = getString(R.string.lyrics_saved_successfully)
                Snackbar.make(
                    requireView(),
                    R.string.lyrics_saved,
                    Snackbar.LENGTH_SHORT
                ).show()
                parentFragmentManager.setFragmentResult(
                    RESULT_KEY,
                    bundleOf(
                        RESULT_TRACK_ID to track.id,
                        RESULT_LYRICS to lyrics
                    )
                )
                // Marquer comme sauvegardé pour éviter le double-save dans onDismiss
                savedByButton = true
                // Fermer la bottom sheet après sauvegarde réussie
                dismiss()
            } else {
                statusText.text = getString(R.string.lyrics_save_failed)
                Snackbar.make(
                    requireView(),
                    R.string.lyrics_save_error,
                    Snackbar.LENGTH_LONG
                ).show()
            }

            setLoading(false)
        }
    }

    private fun toggleEditMode() {
        isEditing = !isEditing
        lyricsText.isEnabled = isEditing
        lyricsText.isFocusableInTouchMode = isEditing
        lyricsText.isFocusable = isEditing

        if (isEditing) {
            // Mode édition activé
            btnEdit.text = getString(R.string.lyrics_done)
            lyricsScrollView.setBackgroundResource(R.drawable.lyrics_edit_background_active)
            lyricsText.requestFocus()
            // Placer le curseur à la fin du texte
            lyricsText.setSelection(lyricsText.text.length)
            // Afficher le clavier
            showKeyboard()
            statusText.text = getString(R.string.lyrics_editing_hint)
        } else {
            // Mode édition désactivé
            btnEdit.text = getString(R.string.lyrics_edit)
            lyricsScrollView.setBackgroundResource(R.drawable.lyrics_edit_background)
            lyricsText.clearFocus()
            // Cacher le clavier
            hideKeyboard()
            statusText.text = getString(R.string.lyrics_loaded_from_cache)
        }
    }

    private fun showKeyboard() {
        val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(lyricsText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(lyricsText.windowToken, 0)
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSearchAuto.isEnabled = !loading
        btnSearchManual.isEnabled = !loading
        btnSave.isEnabled = !loading
        btnEdit.isEnabled = !loading
    }

    /**
     * Active la navigation entre alternatives et affiche la barre de navigation.
     * @param results Liste complète : [meilleur résultat] + [alternatives]
     */
    private fun setupAlternativesNav(results: List<AlternativeLyrics>) {
        alternativesList = results
        currentAltIndex = 0
        isAlternativesMode = true
        alternativesNavLayout.visibility = View.VISIBLE
        updateAltCounter()
        updateAltNavButtons()
    }

    /**
     * Navigue vers l'alternative précédente ou suivante.
     * @param delta -1 pour précédent, +1 pour suivant
     */
    private fun navigateAlternative(delta: Int) {
        val newIndex = (currentAltIndex + delta).coerceIn(0, alternativesList.size - 1)
        if (newIndex == currentAltIndex) return
        currentAltIndex = newIndex
        hasNavigated = true
        val alt = alternativesList[currentAltIndex]
        lyricsText.setText(alt.lyrics)
        statusText.text = getString(R.string.lyrics_found_from, alt.source)
        updateAltCounter()
        updateAltNavButtons()
    }

    private fun updateAltCounter() {
        tvAltCounter.text = getString(
            R.string.lyrics_alt_counter,
            currentAltIndex + 1,
            alternativesList.size
        )
    }

    private fun updateAltNavButtons() {
        btnPrevAlt.isEnabled = currentAltIndex > 0
        btnNextAlt.isEnabled = currentAltIndex < alternativesList.size - 1
        btnPrevAlt.alpha = if (btnPrevAlt.isEnabled) 1f else 0.3f
        btnNextAlt.alpha = if (btnNextAlt.isEnabled) 1f else 0.3f
    }

    /**
     * Si la navigation entre alternatives était active et que l'utilisateur n'a pas
     * cliqué sur "Save", sauvegarde automatiquement les paroles actuellement affichées.
     */
    override fun onDismiss(dialog: DialogInterface) {
        if (isAlternativesMode && !savedByButton && hasNavigated) {
            val lyrics = lyricsText.text.toString().trim()
            if (lyrics.isNotEmpty()) {
                activity?.lifecycleScope?.launch(Dispatchers.IO) {
                    LyricsManager.saveLyrics(track, lyrics, "api")
                }
            }
        }
        super.onDismiss(dialog)
    }
}
