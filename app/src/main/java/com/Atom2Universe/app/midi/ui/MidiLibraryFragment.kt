package com.Atom2Universe.app.midi.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.data.AlbumItem
import com.Atom2Universe.app.midi.data.ArtistItem
import com.Atom2Universe.app.midi.data.MidiTrack
import com.Atom2Universe.app.midi.repository.MidiRepository
import com.Atom2Universe.app.midi.scanner.MidiLibraryScanner
import com.Atom2Universe.app.midi.ui.adapter.AlbumAdapter
import com.Atom2Universe.app.midi.ui.adapter.ArtistAdapter
import com.Atom2Universe.app.midi.ui.adapter.MidiTrackAdapter
import com.Atom2Universe.app.midi.viewmodel.MidiPlayerViewModel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Fragment pour afficher la bibliothèque MIDI avec navigation hiérarchique
 * Niveau 1: Artistes → Niveau 2: Albums → Niveau 3: Titres
 */
class MidiLibraryFragment : Fragment() {

    private val viewModel: MidiPlayerViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var breadcrumbScroll: HorizontalScrollView
    private lateinit var breadcrumbContainer: LinearLayout
    private lateinit var repository: MidiRepository

    // Loading overlay views
    private lateinit var scanLoadingOverlay: LinearLayout
    private lateinit var scanLoadingProgress: TextView
    private lateinit var scanLoadingCurrentFile: TextView

    // Adapters pour les 3 niveaux
    private lateinit var artistAdapter: ArtistAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var trackAdapter: MidiTrackAdapter

    // État de navigation
    private enum class NavigationLevel {
        ARTISTS, ALBUMS, TRACKS
    }

    private var currentLevel = NavigationLevel.ARTISTS
    private var currentArtist: String? = null
    private var currentAlbum: String? = null

    // Favoris
    private var favoriteIds: Set<Long> = emptySet()
    private var favoritesJob: Job? = null
    private var scanProgressJob: Job? = null
    private var nowPlayingTrackId: Long? = null
    private var isPlaying: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_midi_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = MidiRepository(requireContext())

        recyclerView = view.findViewById(R.id.recycler_view)
        emptyState = view.findViewById(R.id.empty_state)
        breadcrumbScroll = view.findViewById(R.id.breadcrumb_scroll)
        breadcrumbContainer = view.findViewById(R.id.breadcrumb_container)

        // Loading overlay views
        scanLoadingOverlay = view.findViewById(R.id.scan_loading_overlay)
        scanLoadingProgress = view.findViewById(R.id.scan_loading_progress)
        scanLoadingCurrentFile = view.findViewById(R.id.scan_loading_current_file)

        setupAdapters()
        setupRecyclerView()
        setupEmptyState(view)
        setupSwipeBackGesture()
        observeScanProgress()
        observePlaybackState()

        // Afficher les artistes au démarrage
        showArtists()
    }

    private fun setupAdapters() {
        // Adapter pour les artistes
        artistAdapter = ArtistAdapter { artist ->
            handleArtistClick(artist)
        }

        // Adapter pour les albums
        albumAdapter = AlbumAdapter { album ->
            handleAlbumClick(album)
        }

        // Adapter pour les titres avec gestion des favoris et infos extensibles
        trackAdapter = MidiTrackAdapter(
            onTrackClick = { track ->
                handleTrackClick(track)
            },
            onFavoriteClick = { track, _ ->
                handleFavoriteClick(track)
            },
            onAddToPlaylistClick = { track ->
                showAddToPlaylistDialog(track)
            }
        )

        // Observer les IDs des favoris
        observeFavorites()
    }

    private fun observeFavorites() {
        favoritesJob?.cancel()
        favoritesJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favoriteTrackIds.collect { ids ->
                favoriteIds = ids.toSet()
                trackAdapter.setFavorites(favoriteIds)
            }
        }
    }

    private fun observePlaybackState() {
        viewModel.currentTrack.observe(viewLifecycleOwner) { track ->
            nowPlayingTrackId = track?.id
            trackAdapter.setNowPlaying(nowPlayingTrackId, isPlaying)
        }
        viewModel.isPlaying.observe(viewLifecycleOwner) { playing ->
            isPlaying = playing
            trackAdapter.setNowPlaying(nowPlayingTrackId, isPlaying)
        }
    }

    /**
     * Observe le scanProgress et affiche/masque l'overlay de chargement
     */
    private fun observeScanProgress() {
        scanProgressJob?.cancel()
        scanProgressJob = viewLifecycleOwner.lifecycleScope.launch {
            (activity as? MidiPlayerActivity)?.getScanProgress()?.collect { progress ->
                updateScanLoadingUI(progress)
            }
        }
    }

    /**
     * Met à jour l'UI du loading overlay selon la progression du scan
     */
    private fun updateScanLoadingUI(progress: MidiLibraryScanner.ScanProgress) {
        if (progress.isScanning) {
            // Afficher l'overlay
            scanLoadingOverlay.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.GONE

            // Mettre à jour le texte de progression
            if (progress.totalFiles > 0) {
                scanLoadingProgress.text = getString(
                    R.string.midi_scan_loading_progress,
                    progress.scannedFiles,
                    progress.totalFiles
                )
            } else {
                scanLoadingProgress.text = getString(R.string.midi_scan_loading_counting)
            }

            // Afficher le fichier en cours
            if (progress.currentFile.isNotEmpty()) {
                scanLoadingCurrentFile.text = progress.currentFile
                scanLoadingCurrentFile.visibility = View.VISIBLE
            } else {
                scanLoadingCurrentFile.visibility = View.GONE
            }
        } else {
            // Masquer l'overlay et rafraîchir la liste
            scanLoadingOverlay.visibility = View.GONE

            // Rafraîchir la liste des artistes après le scan
            if (currentLevel == NavigationLevel.ARTISTS) {
                showArtists()
            }
        }
    }

    private fun handleFavoriteClick(track: MidiTrack) {
        viewModel.toggleFavorite(track.id) { actualState ->
            val messageRes = if (actualState) {
                R.string.midi_added_to_favorites
            } else {
                R.string.midi_removed_from_favorites
            }
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddToPlaylistDialog(track: MidiTrack) {
        AddToPlaylistDialog.newInstance(track.id, track.title)
            .show(childFragmentManager, AddToPlaylistDialog.TAG)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun setupEmptyState(view: View) {
        val scanButton = view.findViewById<MaterialButton>(R.id.btn_scan_folder)
        scanButton.setOnClickListener {
            (activity as? MidiPlayerActivity)?.selectMidiFolder()
        }
    }

    /**
     * Configure le geste de swipe depuis le bord gauche pour revenir en arrière
     */
    private fun setupSwipeBackGesture() {
        val density = resources.displayMetrics.density
        val edgeZoneWidth = 50 * density        // Zone de 50dp depuis le bord gauche
        val minSwipeDistance = 80 * density     // Distance min de 80dp pour valider le swipe
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop

        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var isTrackingEdgeSwipe = false
            private var hasDecidedDirection = false

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = e.x
                        startY = e.y
                        hasDecidedDirection = false
                        // Activer seulement si le touch est dans la zone de bord et pas au niveau racine
                        isTrackingEdgeSwipe = startX <= edgeZoneWidth && currentLevel != NavigationLevel.ARTISTS
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isTrackingEdgeSwipe) return false
                        if (hasDecidedDirection) return isTrackingEdgeSwipe

                        val diffX = e.x - startX
                        val diffY = e.y - startY
                        val absDiffX = abs(diffX)
                        val absDiffY = abs(diffY)

                        // Attendre assez de mouvement pour décider de la direction
                        if (absDiffX > touchSlop || absDiffY > touchSlop) {
                            hasDecidedDirection = true
                            // Intercepter si mouvement horizontal vers la droite (swipe back)
                            // Horizontal doit être > 1.5x vertical
                            isTrackingEdgeSwipe = diffX > 0 && absDiffX > absDiffY * 1.5f
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isTrackingEdgeSwipe = false
                        hasDecidedDirection = false
                    }
                }
                return isTrackingEdgeSwipe && hasDecidedDirection
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                when (e.actionMasked) {
                    MotionEvent.ACTION_UP -> {
                        val diffX = e.x - startX
                        val diffY = e.y - startY
                        val absDiffX = abs(diffX)
                        val absDiffY = abs(diffY)

                        // Valider le swipe: assez de distance horizontale et majoritairement horizontal
                        if (diffX >= minSwipeDistance && absDiffX > absDiffY * 2f) {
                            navigateBack()
                        }

                        isTrackingEdgeSwipe = false
                        hasDecidedDirection = false
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isTrackingEdgeSwipe = false
                        hasDecidedDirection = false
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                if (disallowIntercept) {
                    isTrackingEdgeSwipe = false
                    hasDecidedDirection = false
                }
            }
        })
    }

    /**
     * Affiche la liste des artistes (niveau 1)
     */
    private fun showArtists() {
        currentLevel = NavigationLevel.ARTISTS
        currentArtist = null
        currentAlbum = null

        updateBreadcrumbs()
        recyclerView.adapter = artistAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val artists = repository.getArtistsWithStats()
                if (artists.isEmpty()) {
                    showEmptyState()
                } else {
                    hideEmptyState()
                    artistAdapter.submitList(artists)
                }
            } catch (e: Exception) {
                showEmptyState()
            }
        }
    }

    /**
     * Affiche les albums d'un artiste (niveau 2)
     */
    private fun showAlbums(artist: String) {
        currentLevel = NavigationLevel.ALBUMS
        currentArtist = artist

        updateBreadcrumbs()
        recyclerView.adapter = albumAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val albums = repository.getAlbumsWithStats(artist)
                hideEmptyState()
                albumAdapter.submitList(albums)
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Affiche les titres d'un album (niveau 3)
     */
    private fun showTracks(artist: String, album: String) {
        currentLevel = NavigationLevel.TRACKS
        currentArtist = artist
        currentAlbum = album

        updateBreadcrumbs()
        recyclerView.adapter = trackAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            repository.getTracksByAlbum(artist, album).collect { tracks ->
                hideEmptyState()
                trackAdapter.submitList(tracks)
            }
        }
    }

    /**
     * Navigation arrière
     */
    private fun navigateBack() {
        when (currentLevel) {
            NavigationLevel.TRACKS -> {
                // Revenir aux albums
                currentArtist?.let { showAlbums(it) }
            }
            NavigationLevel.ALBUMS -> {
                // Revenir aux artistes
                showArtists()
            }
            NavigationLevel.ARTISTS -> {
                // Déjà au niveau racine
            }
        }
    }

    /**
     * Gestion du bouton retour Android
     */
    fun onBackPressed(): Boolean {
        return when (currentLevel) {
            NavigationLevel.ARTISTS -> false // Laisser l'activité gérer
            else -> {
                navigateBack()
                true
            }
        }
    }

    // === Click handlers ===

    private fun handleArtistClick(artist: ArtistItem) {
        showAlbums(artist.name)
    }

    private fun handleAlbumClick(album: AlbumItem) {
        showTracks(album.artist, album.album)
    }

    private fun handleTrackClick(track: MidiTrack) {
        // Récupérer la liste actuelle des tracks affichés
        val currentTracks = trackAdapter.currentList.sortedBy { it.title.lowercase() }
        val startIndex = currentTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        viewModel.setCurrentTrack(track)

        // Envoyer la liste complète avec l'index de départ
        (activity as? MidiPlayerActivity)?.playTracks(currentTracks, startIndex)

        Toast.makeText(
            context,
            "▶ ${track.title}",
            Toast.LENGTH_SHORT
        ).show()
    }

    // === UI helpers ===

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
    }

    private fun hideEmptyState() {
        recyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
    }

    // === Breadcrumb Navigation ===

    private data class BreadcrumbSegment(
        val label: String,
        val onClick: (() -> Unit)?
    )

    /**
     * Met à jour les breadcrumbs avec des segments cliquables
     */
    private fun updateBreadcrumbs() {
        breadcrumbContainer.removeAllViews()

        // Au niveau racine (ARTISTS), masquer les breadcrumbs
        if (currentLevel == NavigationLevel.ARTISTS) {
            breadcrumbScroll.visibility = View.GONE
            return
        }

        breadcrumbScroll.visibility = View.VISIBLE

        // Construire les segments selon le niveau de navigation
        val segments = mutableListOf<BreadcrumbSegment>()

        // Racine toujours en premier
        segments.add(BreadcrumbSegment(getString(R.string.midi_nav_library)) { showArtists() })

        when (currentLevel) {
            NavigationLevel.ARTISTS -> {
                // Géré au-dessus
            }
            NavigationLevel.ALBUMS -> {
                // Library > Artist (non cliquable car c'est le niveau actuel)
                segments.add(BreadcrumbSegment(currentArtist ?: "", null))
            }
            NavigationLevel.TRACKS -> {
                // Library > Artist (cliquable) > Album (non cliquable)
                segments.add(BreadcrumbSegment(currentArtist ?: "") {
                    currentArtist?.let { showAlbums(it) }
                })
                segments.add(BreadcrumbSegment(currentAlbum ?: "", null))
            }
        }

        // Créer les vues pour chaque segment
        val density = resources.displayMetrics.density
        val textColorClickable = MaterialColors.getColor(
            requireContext(),
            R.attr.a2uMidiAccent,
            ContextCompat.getColor(requireContext(), R.color.midi_accent)
        )
        val textColorCurrent = ContextCompat.getColor(requireContext(), R.color.midi_text_primary)

        segments.forEachIndexed { index, segment ->
            // Ajouter un séparateur avant ce segment (sauf pour le premier)
            if (index > 0) {
                val separator = ImageView(requireContext()).apply {
                    setImageResource(R.drawable.ic_chevron_right)
                    layoutParams = LinearLayout.LayoutParams(
                        (18 * density).toInt(),
                        (18 * density).toInt()
                    ).apply {
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        marginStart = (2 * density).toInt()
                        marginEnd = (2 * density).toInt()
                    }
                    setColorFilter(textColorCurrent)
                }
                breadcrumbContainer.addView(separator)
            }

            // Ajouter le TextView pour le segment
            val textView = TextView(requireContext()).apply {
                text = segment.label
                textSize = 14f
                maxLines = 1
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                if (segment.onClick != null && index < segments.size - 1) {
                    // Segment cliquable (pas le dernier)
                    setTextColor(textColorClickable)
                    setOnClickListener { segment.onClick.invoke() }
                    isClickable = true
                    isFocusable = true
                    background = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
                    setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                } else {
                    // Segment actuel (non cliquable)
                    setTextColor(textColorCurrent)
                    setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                }
            }
            breadcrumbContainer.addView(textView)
        }

        // Scroller jusqu'à la fin pour montrer la position actuelle
        breadcrumbScroll.post {
            breadcrumbScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
        }
    }

    companion object {
        private const val TAG = "MidiLibraryFragment"
        fun newInstance() = MidiLibraryFragment()
    }
}
