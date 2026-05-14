package com.Atom2Universe.app.midi.ui

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.data.Playlist
import com.Atom2Universe.app.midi.repository.PlaylistRepository
import com.Atom2Universe.app.midi.viewmodel.MidiPlayerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Dialog pour ajouter un morceau à une playlist
 * Affiche la liste des playlists existantes + option "Nouvelle playlist"
 */
class AddToPlaylistDialog : DialogFragment() {

    private val viewModel: MidiPlayerViewModel by activityViewModels()

    private var trackId: Long = 0
    private var trackTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trackId = arguments?.getLong(ARG_TRACK_ID) ?: 0
        trackTitle = arguments?.getString(ARG_TRACK_TITLE) ?: ""
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        // Charger les playlists
        lifecycleScope.launch {
            val playlists = viewModel.allPlaylists.first()
            showPlaylistDialog(playlists)
        }

        // Dialog temporaire pendant le chargement (avec thème sombre)
        return AlertDialog.Builder(context, R.style.Theme_A2U_AlertDialog_Dark)
            .setTitle(R.string.midi_add_to_playlist)
            .setMessage(R.string.midi_loading_playlists)
            .setNegativeButton(R.string.midi_cancel) { _, _ -> dismiss() }
            .create()
    }

    private fun showPlaylistDialog(playlists: List<Playlist>) {
        val ctx = context ?: return

        // Sauvegarder le context pour les dialogs suivants
        val savedContext = ctx

        // Filtrer les favoris (on ne peut pas ajouter manuellement aux favoris via ce dialog)
        val filteredPlaylists = playlists.filter {
            it.name != PlaylistRepository.FAVORITES_PLAYLIST_NAME
        }

        // Créer la liste des options (utiliser savedContext.getString car le fragment sera détaché)
        val options = mutableListOf<String>()
        options.add(savedContext.getString(R.string.midi_new_playlist))
        options.addAll(filteredPlaylists.map { it.name })

        // Sauvegarder le titre avant dismiss
        val dialogTitle = savedContext.getString(R.string.midi_add_to_playlist_title, trackTitle)

        // Utiliser un layout personnalisé pour les items (texte blanc sur fond sombre)
        val adapter = ArrayAdapter(savedContext, R.layout.item_dialog_list, options)

        // Fermer le dialog de chargement d'abord
        dismiss()

        // Recréer le dialog avec les playlists (avec thème sombre)
        AlertDialog.Builder(savedContext, R.style.Theme_A2U_AlertDialog_Dark)
            .setTitle(dialogTitle)
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> showNewPlaylistDialog(savedContext, filteredPlaylists)
                    else -> {
                        val playlist = filteredPlaylists[which - 1]
                        addToPlaylist(savedContext, playlist)
                    }
                }
            }
            .setNegativeButton(R.string.midi_cancel, null)
            .show()
    }

    private fun showNewPlaylistDialog(savedContext: android.content.Context, existingPlaylists: List<Playlist>) {
        val editText = EditText(savedContext).apply {
            hint = savedContext.getString(R.string.midi_playlist_name_hint)
            setPadding(48, 32, 48, 32)
            setTextColor(ContextCompat.getColor(savedContext, R.color.midi_text_primary))
            setHintTextColor(ContextCompat.getColor(savedContext, R.color.midi_text_secondary))
        }

        AlertDialog.Builder(savedContext, R.style.Theme_A2U_AlertDialog_Dark)
            .setTitle(R.string.midi_create_playlist)
            .setView(editText)
            .setPositiveButton(R.string.midi_create) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    createPlaylistAndAdd(savedContext, name)
                } else {
                    Toast.makeText(savedContext, R.string.midi_playlist_name_empty, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.midi_cancel) { _, _ ->
                // Revenir à la liste des playlists
                showPlaylistSelectionAgain(savedContext, existingPlaylists)
            }
            .show()

        editText.requestFocus()
    }

    private fun showPlaylistSelectionAgain(savedContext: android.content.Context, filteredPlaylists: List<Playlist>) {
        val options = mutableListOf<String>()
        options.add(savedContext.getString(R.string.midi_new_playlist))
        options.addAll(filteredPlaylists.map { it.name })

        val adapter = ArrayAdapter(savedContext, R.layout.item_dialog_list, options)

        AlertDialog.Builder(savedContext, R.style.Theme_A2U_AlertDialog_Dark)
            .setTitle(savedContext.getString(R.string.midi_add_to_playlist_title, trackTitle))
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> showNewPlaylistDialog(savedContext, filteredPlaylists)
                    else -> {
                        val playlist = filteredPlaylists[which - 1]
                        addToPlaylist(savedContext, playlist)
                    }
                }
            }
            .setNegativeButton(R.string.midi_cancel, null)
            .show()
    }

    private fun createPlaylistAndAdd(savedContext: android.content.Context, playlistName: String) {
        viewModel.createPlaylist(playlistName) { playlistId ->
            viewModel.addTrackToPlaylist(playlistId, trackId) { success ->
                val message = if (success) {
                    savedContext.getString(R.string.midi_track_added_to_playlist, playlistName)
                } else {
                    savedContext.getString(R.string.midi_track_already_in_playlist)
                }
                Toast.makeText(savedContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addToPlaylist(savedContext: android.content.Context, playlist: Playlist) {
        viewModel.addTrackToPlaylist(playlist.id, trackId) { success ->
            val message = if (success) {
                savedContext.getString(R.string.midi_track_added_to_playlist, playlist.name)
            } else {
                savedContext.getString(R.string.midi_track_already_in_playlist)
            }
            Toast.makeText(savedContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val TAG = "AddToPlaylistDialog"
        private const val ARG_TRACK_ID = "track_id"
        private const val ARG_TRACK_TITLE = "track_title"

        fun newInstance(trackId: Long, trackTitle: String): AddToPlaylistDialog {
            return AddToPlaylistDialog().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TRACK_ID, trackId)
                    putString(ARG_TRACK_TITLE, trackTitle)
                }
            }
        }
    }
}
