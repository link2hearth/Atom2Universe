package com.Atom2Universe.app.midi.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.data.MidiTrack
import com.Atom2Universe.app.midi.data.Playlist
import com.Atom2Universe.app.midi.repository.PlaylistRepository
import com.Atom2Universe.app.midi.ui.adapter.MidiTrackAdapter
import com.Atom2Universe.app.midi.ui.adapter.PlaylistAdapter
import com.Atom2Universe.app.midi.ui.adapter.PlaylistDisplayItem
import com.Atom2Universe.app.midi.viewmodel.MidiPlayerViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Fragment pour afficher et gérer les playlists
 * Gère deux états:
 * - Liste des playlists
 * - Contenu d'une playlist sélectionnée
 */
class PlaylistsFragment : Fragment() {

    private val viewModel: MidiPlayerViewModel by activityViewModels()

    // UI Components
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyStateText: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var playlistHeader: LinearLayout
    private lateinit var playlistHeaderTitle: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnShufflePlay: ImageButton

    // Adapters
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var trackAdapter: MidiTrackAdapter

    // État de navigation
    private enum class ViewState {
        PLAYLISTS,  // Liste des playlists
        TRACKS      // Contenu d'une playlist
    }

    private var currentState = ViewState.PLAYLISTS
    private var currentPlaylist: Playlist? = null
    private var currentTracks: List<MidiTrack> = emptyList()

    // Job pour observer les tracks d'une playlist
    private var tracksJob: Job? = null
    private var favoritesJob: Job? = null
    private var favoriteIds: Set<Long> = emptySet()
    private var nowPlayingTrackId: Long? = null
    private var isPlaying: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_playlists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        recyclerView = view.findViewById(R.id.recycler_view)
        emptyState = view.findViewById(R.id.empty_state)
        emptyStateText = view.findViewById(R.id.empty_state_text)
        fab = view.findViewById(R.id.fab_create_playlist)
        playlistHeader = view.findViewById(R.id.playlist_header)
        playlistHeaderTitle = view.findViewById(R.id.playlist_header_title)
        btnBack = view.findViewById(R.id.btn_back)
        btnShufflePlay = view.findViewById(R.id.btn_shuffle_play)

        setupAdapters()
        setupRecyclerView()
        setupFAB()
        setupHeader()
        observePlaylists()
        observeFavorites()
        observePlaybackState()

        // Afficher la liste des playlists
        showPlaylists()
    }

    private fun setupAdapters() {
        // Adapter pour les playlists
        playlistAdapter = PlaylistAdapter(
            onPlaylistClick = { playlist ->
                showPlaylistContent(playlist)
            },
            onMenuClick = { playlist, anchor ->
                showPlaylistMenu(playlist, anchor)
            }
        )

        // Adapter pour les tracks (contenu d'une playlist)
        // Long-press pour retirer de la playlist
        trackAdapter = MidiTrackAdapter(
            onTrackClick = { track ->
                handleTrackClick(track)
            },
            onFavoriteClick = { track, _ ->
                handleFavoriteClick(track)
            },
            onAddToPlaylistClick = { track ->
                showAddToPlaylistDialog(track)
            },
            onLongClick = { track, anchor ->
                showTrackContextMenu(track, anchor)
                true
            }
        )
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun setupFAB() {
        fab.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    private fun setupHeader() {
        btnBack.setOnClickListener {
            showPlaylists()
        }

        btnShufflePlay.setOnClickListener {
            shufflePlayCurrentPlaylist()
        }
    }

    private fun observePlaylists() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allPlaylists.collect { playlists ->
                if (currentState == ViewState.PLAYLISTS) {
                    if (playlists.isEmpty()) {
                        recyclerView.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                        emptyStateText.text = getString(R.string.midi_playlists_empty)
                    } else {
                        recyclerView.visibility = View.VISIBLE
                        emptyState.visibility = View.GONE

                        // Charger le nombre de tracks pour chaque playlist
                        val displayItems = playlists.map { playlist ->
                            val trackCount = viewModel.getPlaylistTrackCount(playlist.id)
                            PlaylistDisplayItem(playlist, trackCount)
                        }
                        playlistAdapter.submitList(displayItems)
                    }
                }
            }
        }
    }

    private fun observeFavorites() {
        favoritesJob?.cancel()
        favoritesJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favoriteTrackIds.collect { ids ->
                favoriteIds = ids.toSet()
                if (currentState == ViewState.TRACKS) {
                    trackAdapter.setFavorites(favoriteIds)
                }
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

    // === Navigation ===

    private fun showPlaylists() {
        currentState = ViewState.PLAYLISTS
        currentPlaylist = null
        tracksJob?.cancel()

        // UI
        playlistHeader.visibility = View.GONE
        fab.visibility = View.VISIBLE
        recyclerView.adapter = playlistAdapter

        // Rafraîchir la liste
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allPlaylists.collect { playlists ->
                if (playlists.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                    emptyStateText.text = getString(R.string.midi_playlists_empty)
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE

                    val displayItems = playlists.map { playlist ->
                        val trackCount = viewModel.getPlaylistTrackCount(playlist.id)
                        PlaylistDisplayItem(playlist, trackCount)
                    }
                    playlistAdapter.submitList(displayItems)
                }
                // Ne collecter qu'une fois pour le refresh
                return@collect
            }
        }
    }

    private fun showPlaylistContent(playlist: Playlist) {
        currentState = ViewState.TRACKS
        currentPlaylist = playlist

        // UI
        playlistHeader.visibility = View.VISIBLE
        playlistHeaderTitle.text = playlist.name
        fab.visibility = View.GONE
        recyclerView.adapter = trackAdapter
        trackAdapter.setFavorites(favoriteIds)

        // Observer les tracks de cette playlist
        tracksJob?.cancel()
        tracksJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getPlaylistTracks(playlist.id).collect { tracks ->
                currentTracks = tracks
                if (tracks.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                    emptyStateText.text = getString(R.string.midi_playlist_empty)
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                    trackAdapter.submitList(tracks)
                }
            }
        }
    }

    /**
     * Gestion du bouton retour Android
     */
    fun onBackPressed(): Boolean {
        return when (currentState) {
            ViewState.TRACKS -> {
                showPlaylists()
                true
            }
            ViewState.PLAYLISTS -> false
        }
    }

    // === Track Actions ===

    private fun handleTrackClick(track: MidiTrack) {
        // Mettre à jour le track courant dans le ViewModel (important pour le mode Practice)
        viewModel.setCurrentTrack(track)

        // Jouer le track depuis la playlist
        val startIndex = currentTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        (activity as? MidiPlayerActivity)?.playTracks(currentTracks, startIndex)

        Toast.makeText(context, "▶ ${track.title}", Toast.LENGTH_SHORT).show()
    }

    private fun handleFavoriteClick(track: MidiTrack) {
        viewModel.toggleFavorite(track.id) { isNowFavorite ->
            val messageRes = if (isNowFavorite) {
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

    private fun showTrackContextMenu(track: MidiTrack, anchor: View) {
        val context = context ?: return
        val playlist = currentPlaylist ?: return

        PopupMenu(context, anchor).apply {
            menu.add(0, MENU_REMOVE_FROM_PLAYLIST, 0, R.string.midi_remove_from_playlist)

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_REMOVE_FROM_PLAYLIST -> {
                        viewModel.removeTrackFromPlaylist(playlist.id, track.id)
                        Toast.makeText(context, R.string.midi_track_removed, Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun shufflePlayCurrentPlaylist() {
        if (currentTracks.isEmpty()) {
            Toast.makeText(context, R.string.midi_playlist_empty, Toast.LENGTH_SHORT).show()
            return
        }

        // Mélanger et jouer
        val shuffled = currentTracks.shuffled()

        // Mettre à jour le track courant (le premier de la liste shuffled)
        viewModel.setCurrentTrack(shuffled[0])

        (activity as? MidiPlayerActivity)?.playTracks(shuffled, 0)

        Toast.makeText(
            context,
            getString(R.string.midi_shuffle_play) + " (${shuffled.size})",
            Toast.LENGTH_SHORT
        ).show()
    }

    // === Playlist Actions ===

    private fun showCreatePlaylistDialog() {
        val context = context ?: return

        val editText = EditText(context).apply {
            hint = getString(R.string.midi_playlist_name_hint)
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.midi_create_playlist)
            .setView(editText)
            .setPositiveButton(R.string.midi_create) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    createPlaylist(name)
                } else {
                    Toast.makeText(context, R.string.midi_playlist_name_empty, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.midi_cancel, null)
            .show()

        editText.requestFocus()
    }

    private fun createPlaylist(name: String) {
        viewModel.createPlaylist(name) { _ ->
            Toast.makeText(
                context,
                getString(R.string.midi_playlist_created, name),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showPlaylistMenu(playlist: Playlist, anchor: View) {
        val context = context ?: return
        val isFavorites = playlist.name == PlaylistRepository.FAVORITES_PLAYLIST_NAME

        PopupMenu(context, anchor).apply {
            if (!isFavorites) {
                menu.add(0, MENU_RENAME, 0, R.string.midi_rename)
                menu.add(0, MENU_DELETE, 1, R.string.midi_delete)
            }
            menu.add(0, MENU_CLEAR, 2, R.string.midi_clear_playlist)

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_RENAME -> {
                        showRenameDialog(playlist)
                        true
                    }
                    MENU_DELETE -> {
                        showDeleteConfirmation(playlist)
                        true
                    }
                    MENU_CLEAR -> {
                        showClearConfirmation(playlist)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showRenameDialog(playlist: Playlist) {
        val context = context ?: return

        val editText = EditText(context).apply {
            setText(playlist.name)
            setPadding(48, 32, 48, 32)
            setSelection(text.length)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.midi_rename_playlist)
            .setView(editText)
            .setPositiveButton(R.string.midi_rename) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != playlist.name) {
                    viewModel.renamePlaylist(playlist.id, newName)
                    Toast.makeText(context, R.string.midi_playlist_renamed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.midi_cancel, null)
            .show()
    }

    private fun showDeleteConfirmation(playlist: Playlist) {
        val context = context ?: return

        AlertDialog.Builder(context)
            .setTitle(R.string.midi_delete_playlist)
            .setMessage(getString(R.string.midi_delete_playlist_confirm, playlist.name))
            .setPositiveButton(R.string.midi_delete) { _, _ ->
                viewModel.deletePlaylist(playlist.id)
                Toast.makeText(context, R.string.midi_playlist_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.midi_cancel, null)
            .show()
    }

    private fun showClearConfirmation(playlist: Playlist) {
        val context = context ?: return

        AlertDialog.Builder(context)
            .setTitle(R.string.midi_clear_playlist)
            .setMessage(getString(R.string.midi_clear_playlist_confirm, playlist.name))
            .setPositiveButton(R.string.midi_clear) { _, _ ->
                (activity as? MidiPlayerActivity)?.clearPlaylist(playlist.id)
                Toast.makeText(context, R.string.midi_playlist_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.midi_cancel, null)
            .show()
    }

    companion object {
        private const val MENU_RENAME = 1
        private const val MENU_DELETE = 2
        private const val MENU_CLEAR = 3
        private const val MENU_REMOVE_FROM_PLAYLIST = 10

        fun newInstance() = PlaylistsFragment()
    }
}
