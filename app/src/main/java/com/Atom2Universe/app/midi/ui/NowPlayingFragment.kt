package com.Atom2Universe.app.midi.ui

import android.app.AlertDialog
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.Atom2Universe.app.audio.AudioFeedback as Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.practice.ColorSettingsManager
import com.Atom2Universe.app.midi.repository.MidiRepository
import com.Atom2Universe.app.midi.service.MidiAudioMixer
import com.Atom2Universe.app.midi.service.MidiPlaybackService
import com.Atom2Universe.app.midi.service.PlaybackQueueManager
import com.Atom2Universe.app.midi.viewmodel.MidiPlayerViewModel
import com.Atom2Universe.app.midi.visualizer.MidiChannelAdapter
import com.Atom2Universe.app.midi.visualizer.MidiEventDispatcher
import com.Atom2Universe.app.midi.visualizer.MidiNoteTracker
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Fragment pour afficher le lecteur en cours avec visualisation MIDI
 *
 * Affiche:
 * - Informations sur le morceau en cours
 * - Liste des pistes/canaux MIDI avec clavier individuel par canal
 *   (les claviers lisent eux-mêmes MidiLiveState : aucune note ne transite par ce fragment)
 * - Toggle pour afficher/cacher chaque clavier
 * - Contrôles de lecture
 */
class NowPlayingFragment : Fragment(), MidiEventDispatcher.MidiAnalysisListener {

    private val viewModel: MidiPlayerViewModel by activityViewModels()

    // UI Components
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnAddToPlaylist: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnTwoHandsPractice: View
    private lateinit var channelsList: RecyclerView
    private lateinit var channelsEmpty: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView

    // État favori du track actuel
    private var currentTrackId: Long? = null
    private var isCurrentTrackFavorite: Boolean = false

    // Adapter pour la liste des canaux avec claviers
    private lateinit var channelAdapter: MidiChannelAdapter

    // Détection deux mains
    private var twoHandsInfo: MidiNoteTracker.TwoHandsInfo? = null

    // État shuffle/repeat
    private var isShuffleEnabled = false
    private var lastPlaybackState: Int = PlaybackStateCompat.STATE_NONE
    private var repeatMode = PlaybackQueueManager.RepeatMode.NONE

    // Couleurs pour les boutons
    private var colorActive = 0
    private var colorInactive = 0

    // Position tracking
    private var currentDurationMs: Long = 0L
    private var isUserSeeking: Boolean = false

    // Repository pour charger les tracks par scope
    private lateinit var repository: MidiRepository

    // Enum pour le scope du shuffle
    enum class ShuffleScope {
        CURRENT,    // Album/dossier actuel (queue actuelle)
        ARTIST,     // Artiste actuel (tous les albums)
        LIBRARY     // Toute la bibliothèque
    }

    // MediaController callback
    private val mediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            val newState = state?.state ?: PlaybackStateCompat.STATE_NONE
            // Ne logger que si l'état a changé
            if (newState != lastPlaybackState) {
                lastPlaybackState = newState
            }
            state?.let { updatePlaybackUI(it) }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            metadata?.let { updateTrackInfo(it) }
        }
    }

    /**
     * Met à jour les infos du morceau depuis les métadonnées MediaSession
     * et synchronise le ViewModel si le track a changé (important pour le mode practice)
     */
    private fun updateTrackInfo(metadata: MediaMetadataCompat) {
        val title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "Unknown"
        val artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""

        titleText.text = title
        if (artist.isNotEmpty()) {
            artistText.text = artist
            artistText.visibility = View.VISIBLE
        } else {
            artistText.visibility = View.GONE
        }

        // Synchroniser le ViewModel.currentTrack si le track a changé
        // Cela garantit que le bouton two-hands practice utilise le bon fichier
        val mediaIdStr = metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
        val mediaId = mediaIdStr?.toLongOrNull()
        if (mediaId != null && mediaId != viewModel.currentTrack.value?.id) {
            viewLifecycleOwner.lifecycleScope.launch {
                val track = repository.getTrackById(mediaId)
                if (track != null) {
                    viewModel.setCurrentTrack(track)
                }
            }
        }
    }

    companion object {
        fun newInstance() = NowPlayingFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_now_playing, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ColorSettingsManager for custom colors
        ColorSettingsManager.init(requireContext())
        lifecycleScope.launch {
            ColorSettingsManager.loadAllColors()
        }

        // Repository pour charger les tracks
        repository = MidiRepository(requireContext())

        // Couleurs pour les boutons actifs/inactifs
        colorActive = ContextCompat.getColor(requireContext(), R.color.midi_accent)
        colorInactive = ContextCompat.getColor(requireContext(), R.color.midi_text_secondary)

        // Bind views
        titleText = view.findViewById(R.id.track_title)
        artistText = view.findViewById(R.id.track_artist)
        btnFavorite = view.findViewById(R.id.btn_favorite)
        btnAddToPlaylist = view.findViewById(R.id.btn_add_to_playlist)
        btnPlayPause = view.findViewById(R.id.btn_play_pause)
        btnStop = view.findViewById(R.id.btn_stop)
        btnPrevious = view.findViewById(R.id.btn_previous)
        btnNext = view.findViewById(R.id.btn_next)
        btnShuffle = view.findViewById(R.id.btn_shuffle)
        btnRepeat = view.findViewById(R.id.btn_repeat)
        btnTwoHandsPractice = view.findViewById(R.id.btn_two_hands_practice)
        channelsList = view.findViewById(R.id.channels_list)
        channelsEmpty = view.findViewById(R.id.channels_empty)
        seekBar = view.findViewById(R.id.seek_bar)
        timeCurrent = view.findViewById(R.id.time_current)
        timeTotal = view.findViewById(R.id.time_total)

        // Initialize adapter with context
        channelAdapter = MidiChannelAdapter(requireContext())

        setupControls()
        setupFavoriteButton()
        setupSeekBar()
        setupChannelsList()
        setupTwoHandsPracticeButton()
        observePlaybackState()
        observeFavorites()
    }

    override fun onResume() {
        super.onResume()
        // Les moteurs ne suivent les notes que si un écran les affiche
        MidiEventDispatcher.acquireVisualizer()
        MidiEventDispatcher.addAnalysisListener(this)

        // S'enregistrer pour les mises à jour du MediaController
        getMediaController()?.registerCallback(mediaControllerCallback)

        // Mettre à jour l'UI avec l'état actuel du playback
        val controller = getMediaController()
        controller?.playbackState?.let { updatePlaybackUI(it) }

        // Pendant notre absence, les moteurs n'ont pas alimenté les claviers : on leur demande
        // de reconstruire les notes en cours à la position actuelle.
        if (controller?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
            controller.sendCommand(MidiPlaybackService.COMMAND_SYNC_VISUALIZER, null, null)
        }

        // Mettre à jour l'UI avec les métadonnées actuelles (titre, artiste)
        controller?.metadata?.let { updateTrackInfo(it) }
    }

    override fun onPause() {
        super.onPause()
        MidiEventDispatcher.releaseVisualizer()
        MidiEventDispatcher.removeAnalysisListener(this)
        getMediaController()?.unregisterCallback(mediaControllerCallback)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // BUG FIX 3.35: Supprimer les observers LiveData pour éviter les fuites mémoire
        viewModel.currentTrack.removeObservers(viewLifecycleOwner)
    }

    // === Setup Methods ===

    private fun setupFavoriteButton() {
        btnFavorite.setOnClickListener {
            currentTrackId?.let { trackId ->
                viewModel.toggleFavorite(trackId) { isNowFavorite ->
                    isCurrentTrackFavorite = isNowFavorite
                    updateFavoriteIcon()
                    val messageRes = if (isNowFavorite) {
                        R.string.midi_added_to_favorites
                    } else {
                        R.string.midi_removed_from_favorites
                    }
                    Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnAddToPlaylist.setOnClickListener {
            val track = viewModel.currentTrack.value
            if (track != null) {
                AddToPlaylistDialog.newInstance(track.id, track.title)
                    .show(childFragmentManager, AddToPlaylistDialog.TAG)
            }
        }
    }

    private fun observeFavorites() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favoriteTrackIds.collect { favoriteIds ->
                // Vérifier si le track actuel est dans les favoris
                currentTrackId?.let { trackId ->
                    isCurrentTrackFavorite = favoriteIds.contains(trackId)
                    updateFavoriteIcon()
                }
            }
        }
    }

    private fun updateFavoriteIcon() {
        btnFavorite.setImageResource(
            if (isCurrentTrackFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
    }

    private fun setupControls() {
        btnPlayPause.setOnClickListener {
            val controller = getMediaController()
            if (controller == null) {
                Toast.makeText(context, R.string.midi_player_not_connected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val state = controller.playbackState?.state ?: PlaybackStateCompat.STATE_NONE
            when (state) {
                PlaybackStateCompat.STATE_PLAYING -> {
                    controller.transportControls.pause()
                }
                PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.STATE_STOPPED,
                PlaybackStateCompat.STATE_NONE -> {
                    controller.transportControls.play()
                }
                else -> {
                    controller.transportControls.play()
                }
            }
        }

        btnStop.setOnClickListener {
            val controller = getMediaController()
            if (controller == null) {
                return@setOnClickListener
            }
            controller.transportControls.stop()
        }

        btnPrevious.setOnClickListener {
            val controller = getMediaController()
            if (controller == null) {
                return@setOnClickListener
            }
            controller.transportControls.skipToPrevious()
        }

        btnNext.setOnClickListener {
            val controller = getMediaController()
            if (controller == null) {
                return@setOnClickListener
            }
            controller.transportControls.skipToNext()
        }

        btnShuffle.setOnClickListener {
            val controller = getMediaController()
            if (controller == null) {
                return@setOnClickListener
            }
            controller.sendCommand(MidiPlaybackService.COMMAND_TOGGLE_SHUFFLE, null, null)
        }

        // Long press pour choisir le scope du shuffle
        btnShuffle.setOnLongClickListener {
            showShuffleScopeDialog()
            true
        }

        btnRepeat.setOnClickListener {
            val controller = getMediaController()
            if (controller == null) {
                return@setOnClickListener
            }
            controller.sendCommand(MidiPlaybackService.COMMAND_CYCLE_REPEAT, null, null)
        }
    }

    private fun setupSeekBar() {
        // SeekBar interactif avec support seek
        seekBar.isEnabled = true
        seekBar.max = 1000 // Utiliser 1000 pour une bonne précision

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && currentDurationMs > 0) {
                    // Mettre à jour le label du temps pendant le drag
                    val positionMs = (progress.toLong() * currentDurationMs) / 1000
                    timeCurrent.text = formatTime(positionMs)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                seekBar?.progress?.let { progress ->
                    if (currentDurationMs > 0) {
                        val positionMs = (progress.toLong() * currentDurationMs) / 1000

                        // Envoyer la commande seek via MediaController
                        getMediaController()?.transportControls?.seekTo(positionMs)
                    }
                }
            }
        })
    }

    /**
     * Récupère le MediaController depuis l'Activity
     */
    private fun getMediaController(): MediaControllerCompat? {
        return try {
            activity?.let { MediaControllerCompat.getMediaController(it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Met à jour l'UI en fonction de l'état du playback
     */
    private fun updatePlaybackUI(state: PlaybackStateCompat) {
        // Mettre à jour le bouton play/pause
        val isPlaying = state.state == PlaybackStateCompat.STATE_PLAYING
        btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

        // Mettre à jour le ViewModel pour synchroniser avec d'autres fragments
        viewModel.setPlaying(isPlaying)

        // Extraire l'état shuffle/repeat et position/durée des extras
        state.extras?.let { extras ->
            isShuffleEnabled = extras.getBoolean(MidiPlaybackService.EXTRA_SHUFFLE_ENABLED, false)
            val repeatModeOrdinal = extras.getInt(MidiPlaybackService.EXTRA_REPEAT_MODE, 0)
            repeatMode = PlaybackQueueManager.RepeatMode.entries.getOrElse(repeatModeOrdinal) {
                PlaybackQueueManager.RepeatMode.NONE
            }

            updateShuffleButton()
            updateRepeatButton()

            // Position et durée
            val positionMs = extras.getLong(MidiPlaybackService.EXTRA_POSITION_MS, 0L)
            val durationMs = extras.getLong(MidiPlaybackService.EXTRA_DURATION_MS, 0L)

            updateSeekBar(positionMs, durationMs)
        }
    }

    /**
     * Met à jour le SeekBar et les labels de temps
     */
    private fun updateSeekBar(positionMs: Long, durationMs: Long) {
        currentDurationMs = durationMs

        // Mettre à jour les labels de temps
        timeCurrent.text = formatTime(positionMs)
        timeTotal.text = formatTime(durationMs)

        // Mettre à jour la position du SeekBar (max=1000 pour précision)
        if (durationMs > 0 && !isUserSeeking) {
            val progress = ((positionMs * 1000) / durationMs).toInt().coerceIn(0, 1000)
            seekBar.progress = progress
        } else if (!isUserSeeking) {
            seekBar.progress = 0
        }
    }

    /**
     * Formate un temps en millisecondes en "m:ss" ou "h:mm:ss"
     */
    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /**
     * Met à jour l'apparence du bouton shuffle
     */
    private fun updateShuffleButton() {
        btnShuffle.imageTintList = android.content.res.ColorStateList.valueOf(
            if (isShuffleEnabled) colorActive else colorInactive
        )
    }

    /**
     * Met à jour l'apparence du bouton repeat
     */
    private fun updateRepeatButton() {
        val (iconRes, tintColor) = when (repeatMode) {
            PlaybackQueueManager.RepeatMode.NONE -> R.drawable.ic_repeat to colorInactive
            PlaybackQueueManager.RepeatMode.ALL -> R.drawable.ic_repeat to colorActive
            PlaybackQueueManager.RepeatMode.ONE -> R.drawable.ic_repeat_one to colorActive
        }
        btnRepeat.setImageResource(iconRes)
        btnRepeat.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
    }

    /**
     * Affiche le dialog pour choisir le scope du shuffle
     */
    private fun showShuffleScopeDialog() {
        val currentTrack = viewModel.currentTrack.value
        if (currentTrack == null) {
            Toast.makeText(context, R.string.midi_no_track_current, Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf(
            getString(R.string.midi_shuffle_scope_current),
            getString(R.string.midi_shuffle_scope_artist),
            getString(R.string.midi_shuffle_scope_library)
        )

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.midi_shuffle_scope_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> applyShuffle(ShuffleScope.CURRENT, currentTrack)
                    1 -> applyShuffle(ShuffleScope.ARTIST, currentTrack)
                    2 -> applyShuffle(ShuffleScope.LIBRARY, currentTrack)
                }
            }
            .show()
    }

    /**
     * Applique le shuffle avec le scope sélectionné
     */
    private fun applyShuffle(scope: ShuffleScope, currentTrack: com.Atom2Universe.app.midi.data.MidiTrack) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tracks = when (scope) {
                    ShuffleScope.CURRENT -> {
                        // Garder la queue actuelle, juste activer shuffle
                        getMediaController()?.sendCommand(MidiPlaybackService.COMMAND_TOGGLE_SHUFFLE, null, null)
                        return@launch
                    }
                    ShuffleScope.ARTIST -> {
                        // Charger tous les tracks de l'artiste actuel
                        repository.getTracksByArtistDirect(currentTrack.artist)
                    }
                    ShuffleScope.LIBRARY -> {
                        // Charger tous les tracks de la bibliothèque
                        repository.getAllTracksDirect()
                    }
                }

                if (tracks.isEmpty()) {
                    Toast.makeText(context, R.string.midi_no_tracks_found, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Trier par titre
                val sortedTracks = tracks.sortedBy { it.title.lowercase() }

                // Trouver l'index du track actuel
                val startIndex = sortedTracks.indexOfFirst { it.id == currentTrack.id }.coerceAtLeast(0)

                // Envoyer la nouvelle queue à l'activité
                (activity as? MidiPlayerActivity)?.playTracksWithShuffle(sortedTracks, startIndex)

                Toast.makeText(
                    context,
                    getString(R.string.midi_shuffle_count, sortedTracks.size),
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(context, getString(R.string.midi_error_message, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupChannelsList() {
        channelsList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = channelAdapter
            // Désactiver les animations pour de meilleures performances
            itemAnimator = null
        }

        // Connecter le callback mute de l'adapter au dispatcher
        channelAdapter.onMuteChanged = { channel, isMuted ->
            MidiEventDispatcher.setChannelMuted(channel, isMuted)
        }

        // Connecter le callback volume de l'adapter au mixer audio et au dispatcher
        channelAdapter.onVolumeChanged = { channel, volume ->
            MidiAudioMixer.setChannelVolume(channel, volume)
            // Notifier aussi via le dispatcher pour les moteurs SF2
            MidiEventDispatcher.notifyChannelVolumeChanged(channel, volume)
        }

        // Connecter le callback practice mode
        channelAdapter.onPracticeClick = { trackIndex, channel, noteRangeMin, noteRangeMax, instrumentName, programNumber ->
            val currentTrack = viewModel.currentTrack.value
            if (currentTrack != null) {
                // IMPORTANT: Stop (pas pause) le playback principal avant d'entrer en mode practice
                // Sinon le callback onCompletion du synthétiseur peut déclencher skipToNext
                // ce qui charge le morceau suivant et corrompt la session de practice
                MediaControllerCompat.getMediaController(requireActivity())?.transportControls?.stop()

                // Navigate to practice mode
                (activity as? MidiPlayerActivity)?.navigateToPractice(
                    trackFilePath = currentTrack.filePath,
                    channelNumber = channel,
                    noteRangeMin = noteRangeMin,
                    noteRangeMax = noteRangeMax,
                    instrumentName = instrumentName,
                    trackTitle = currentTrack.title,
                    trackIndex = trackIndex,
                    programNumber = programNumber
                )
            }
        }

        // Afficher l'état vide par défaut
        showChannels(false)
    }

    /**
     * Configure le bouton d'entraînement deux mains.
     * Visible uniquement si le MIDI est détecté comme piano deux mains.
     */
    private fun setupTwoHandsPracticeButton() {
        btnTwoHandsPractice.setOnClickListener {
            val info = twoHandsInfo
            val currentTrack = viewModel.currentTrack.value

            if (info != null && info.isDetected && currentTrack != null) {
                // Stop le playback principal
                MediaControllerCompat.getMediaController(requireActivity())?.transportControls?.stop()

                // Lancer le mode practice deux mains
                (activity as? MidiPlayerActivity)?.navigateToTwoHandsPractice(
                    trackFilePath = currentTrack.filePath,
                    trackTitle = currentTrack.title,
                    leftHandChannel = info.leftHandChannel,
                    rightHandChannel = info.rightHandChannel,
                    leftHandName = info.leftHandName,
                    rightHandName = info.rightHandName,
                    leftHandNoteRange = info.leftHandNoteRange,
                    rightHandNoteRange = info.rightHandNoteRange
                )
            }
        }
    }

    private fun observePlaybackState() {
        // Observer current track (pour le titre et l'artiste)
        viewModel.currentTrack.observe(viewLifecycleOwner) { track ->
            if (track != null) {
                titleText.text = track.title
                artistText.text = track.artist
                artistText.visibility = View.VISIBLE

                // Mettre à jour l'état favori
                currentTrackId = track.id
                btnFavorite.visibility = View.VISIBLE
                btnAddToPlaylist.visibility = View.VISIBLE

                // Vérifier si ce track est favori
                viewLifecycleOwner.lifecycleScope.launch {
                    isCurrentTrackFavorite = viewModel.isTrackFavorite(track.id)
                    updateFavoriteIcon()
                }
            } else {
                titleText.text = getString(R.string.midi_no_track_playing)
                artistText.visibility = View.GONE
                btnFavorite.visibility = View.GONE
                btnAddToPlaylist.visibility = View.GONE
                currentTrackId = null

                showChannels(false)
            }
        }

        // Note: L'état play/pause est maintenant géré via MediaController callback (updatePlaybackUI)
    }

    // === MidiEventDispatcher.MidiAnalysisListener Implementation ===

    override fun onAnalysisComplete(
        noteMin: Int,
        noteMax: Int,
        displayMin: Int,
        displayMax: Int,
        tracks: List<MidiNoteTracker.TrackInfo>
    ) {
        if (tracks.isEmpty()) {
            showChannels(false)
            twoHandsInfo = null
            btnTwoHandsPractice.visibility = View.GONE
            return
        }

        channelAdapter.setRows(tracks.map { it.toRow() })
        showChannels(true)

        // Détecter si c'est un MIDI piano deux mains
        twoHandsInfo = MidiNoteTracker.detectTwoHands(tracks)
        btnTwoHandsPractice.visibility = if (twoHandsInfo?.isDetected == true) View.VISIBLE else View.GONE
    }

    override fun onAnalysisReset() {
        // Les claviers n'ont rien à effacer : ils lisent MidiLiveState, déjà remis à zéro.
        // On cache seulement la liste jusqu'à l'analyse suivante (mutes et volumes sont remis à
        // zéro par MidiEventDispatcher.prepareForNewFile, pas ici).
        channelAdapter.clearChannelStates()
        showChannels(false)
        twoHandsInfo = null
        btnTwoHandsPractice.visibility = View.GONE
    }

    // === Helper Methods ===

    private fun MidiNoteTracker.TrackInfo.toRow() = MidiChannelAdapter.Row(
        trackIndex = trackIndex,
        channel = channel,
        program = program,
        trackName = trackName,
        isDrumTrack = isDrumTrack,
        noteRangeMin = channelNoteRangeMin,
        noteRangeMax = channelNoteRangeMax,
        programCount = programCount,
        allPrograms = allPrograms
    )

    private fun showChannels(hasTracks: Boolean) {
        channelsList.visibility = if (hasTracks) View.VISIBLE else View.GONE
        channelsEmpty.visibility = if (hasTracks) View.GONE else View.VISIBLE
    }
}
