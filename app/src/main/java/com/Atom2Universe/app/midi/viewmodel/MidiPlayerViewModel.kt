package com.Atom2Universe.app.midi.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Atom2Universe.app.midi.data.MidiTrack
import com.Atom2Universe.app.midi.repository.MidiRepository
import com.Atom2Universe.app.midi.repository.PlaylistRepository
import com.Atom2Universe.app.midi.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * ViewModel principal pour MidiPlayerActivity
 *
 * Gère:
 * - État de configuration (SoundFont, dossier MIDI)
 * - État de lecture (track actuel, position, état playback)
 * - Communication entre UI et repositories
 */
class MidiPlayerViewModel(
    private val midiRepository: MidiRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // === Configuration State ===

    private val _soundFontConfigured = MutableLiveData(false)
    val soundFontConfigured: LiveData<Boolean> = _soundFontConfigured

    private val _midiFolderConfigured = MutableLiveData(false)
    val midiFolderConfigured: LiveData<Boolean> = _midiFolderConfigured

    // === Playback State ===

    private val _currentTrack = MutableLiveData<MidiTrack?>()
    val currentTrack: LiveData<MidiTrack?> = _currentTrack

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _playbackPosition = MutableLiveData(0L)
    val playbackPosition: LiveData<Long> = _playbackPosition

    // === Library State (Flow depuis Room) ===

    val allTracks: Flow<List<MidiTrack>> = midiRepository.allTracks
    val allArtists: Flow<List<String>> = midiRepository.allArtists
    val allPlaylists = playlistRepository.allPlaylists

    // === Favorites State ===
    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteTrackIds: Flow<List<Long>> = playlistRepository.getFavoriteTrackIds()
    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteTracks: Flow<List<MidiTrack>> = playlistRepository.getFavoriteTracks()

    // === Initialization ===

    init {
        loadConfiguration()
    }

    /**
     * Charge la configuration depuis SettingsRepository
     */
    private fun loadConfiguration() {
        viewModelScope.launch {
            // Vérifie SoundFont
            val sfPath = settingsRepository.getSoundFontPath()
            _soundFontConfigured.value = !sfPath.isNullOrBlank()

            // Vérifie dossier MIDI
            val midiFolder = settingsRepository.getMidiFolderUri()
            _midiFolderConfigured.value = !midiFolder.isNullOrBlank()

            // Charge le dernier track joué
            val lastTrackId = settingsRepository.getCurrentTrackId()
            if (lastTrackId != null && lastTrackId > 0) {
                _currentTrack.value = midiRepository.getTrackById(lastTrackId)
            }

            // Charge la position de lecture
            val position = settingsRepository.getPlaybackPosition()
            _playbackPosition.value = position ?: 0L
        }
    }

    // === Configuration Methods ===

    fun setSoundFontConfigured(configured: Boolean) {
        _soundFontConfigured.value = configured
    }

    fun setMidiFolderConfigured(configured: Boolean) {
        _midiFolderConfigured.value = configured
    }

    // === Playback Control Methods ===

    fun setCurrentTrack(track: MidiTrack?) {
        _currentTrack.value = track
        viewModelScope.launch {
            track?.let {
                settingsRepository.saveCurrentTrackId(it.id)
            }
        }
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun setPlaybackPosition(position: Long) {
        _playbackPosition.value = position
    }

    /**
     * Sauvegarde la position de lecture (pour persist state)
     */
    fun savePlaybackPosition() {
        viewModelScope.launch {
            _playbackPosition.value?.let { position ->
                settingsRepository.savePlaybackPosition(position)
            }
        }
    }

    // === Library Methods ===

    /**
     * Récupère les tracks d'un artiste
     */
    fun getTracksByArtist(artist: String): Flow<List<MidiTrack>> {
        return midiRepository.getTracksByArtist(artist)
    }

    /**
     * Récupère les albums d'un artiste
     */
    fun getAlbumsByArtist(artist: String): Flow<List<String>> {
        return midiRepository.getAlbumsByArtist(artist)
    }

    /**
     * Récupère les tracks d'un album
     */
    fun getTracksByAlbum(artist: String, album: String): Flow<List<MidiTrack>> {
        return midiRepository.getTracksByAlbum(artist, album)
    }

    /**
     * Supprime un track de la bibliothèque
     */
    fun deleteTrack(track: MidiTrack) {
        viewModelScope.launch {
            midiRepository.deleteTrack(track.id)
        }
    }

    // === Favorites Methods ===

    /**
     * Toggle favori pour un track
     * @return true si ajouté aux favoris, false si retiré
     */
    fun toggleFavorite(trackId: Long, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val isNowFavorite = playlistRepository.toggleFavorite(trackId)
            onResult?.invoke(isNowFavorite)
        }
    }

    /**
     * Vérifie si un track est favori
     */
    suspend fun isTrackFavorite(trackId: Long): Boolean {
        return playlistRepository.isTrackFavorite(trackId)
    }

    // === Playlist Methods ===

    /**
     * Crée une nouvelle playlist
     */
    fun createPlaylist(name: String, onResult: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val playlistId = playlistRepository.createPlaylist(name)
            onResult?.invoke(playlistId)
        }
    }

    /**
     * Renomme une playlist
     */
    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, newName)
        }
    }

    /**
     * Supprime une playlist
     */
    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    /**
     * Ajoute un track à une playlist
     */
    fun addTrackToPlaylist(playlistId: Long, trackId: Long, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val added = playlistRepository.addTrackToPlaylist(playlistId, trackId)
            onResult?.invoke(added)
        }
    }

    /**
     * Retire un track d'une playlist
     */
    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    /**
     * Récupère les tracks d'une playlist
     */
    fun getPlaylistTracks(playlistId: Long): Flow<List<MidiTrack>> {
        return playlistRepository.getPlaylistTracks(playlistId)
    }

    /**
     * Récupère le nombre de tracks d'une playlist
     */
    suspend fun getPlaylistTrackCount(playlistId: Long): Int {
        return playlistRepository.getPlaylistTrackCount(playlistId)
    }

    // === Cleanup ===

    override fun onCleared() {
        super.onCleared()
        // Sauvegarde l'état avant destruction
        savePlaybackPosition()
    }
}
