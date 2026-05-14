package com.Atom2Universe.app.midi.ui

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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
import org.billthefarmer.mididriver.MidiDriver
import java.util.Locale

/**
 * Fragment pour afficher le lecteur en cours avec visualisation MIDI
 *
 * Affiche:
 * - Informations sur le morceau en cours
 * - Liste des pistes/canaux MIDI avec clavier individuel par canal
 * - Toggle pour afficher/cacher chaque clavier
 * - Contrôles de lecture
 */
class NowPlayingFragment : Fragment(),
    MidiEventDispatcher.MidiEventListener,
    MidiEventDispatcher.MidiAnalysisListener {

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
    private lateinit var btnRefreshKeyboards: ImageButton
    private lateinit var btnTwoHandsPractice: ImageButton
    private lateinit var channelsList: RecyclerView
    private lateinit var channelsEmpty: TextView
    private lateinit var channelsHeader: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView

    // État favori du track actuel
    private var currentTrackId: Long? = null
    private var isCurrentTrackFavorite: Boolean = false

    // Adapter pour la liste des canaux avec claviers
    private lateinit var channelAdapter: MidiChannelAdapter

    // Plage de notes détectée
    private var currentNoteRangeMin = 48
    private var currentNoteRangeMax = 84

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

    // Test MidiDriver
    private var testMidiDriver: MidiDriver? = null

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
        btnRefreshKeyboards = view.findViewById(R.id.btn_refresh_keyboards)
        btnTwoHandsPractice = view.findViewById(R.id.btn_two_hands_practice)
        channelsList = view.findViewById(R.id.channels_list)
        channelsEmpty = view.findViewById(R.id.channels_empty)
        channelsHeader = view.findViewById(R.id.channels_header)
        seekBar = view.findViewById(R.id.seek_bar)
        timeCurrent = view.findViewById(R.id.time_current)
        timeTotal = view.findViewById(R.id.time_total)

        // Initialize adapter with context
        channelAdapter = MidiChannelAdapter(requireContext())

        setupControls()
        setupFavoriteButton()
        setupSeekBar()
        setupChannelsList()
        setupRefreshButton()
        setupTwoHandsPracticeButton()
        observePlaybackState()
        observeFavorites()

        // Bouton de test MIDI (temporaire pour debug)
        setupTestButton(view)
    }

    override fun onResume() {
        super.onResume()
        // S'enregistrer pour recevoir les événements MIDI
        MidiEventDispatcher.addMidiEventListener(this)
        MidiEventDispatcher.addAnalysisListener(this)

        // S'enregistrer pour les mises à jour du MediaController
        getMediaController()?.registerCallback(mediaControllerCallback)

        // Si un tracker existe déjà (fichier déjà chargé), initialiser l'UI
        MidiEventDispatcher.getTracker()?.let { tracker ->
            currentNoteRangeMin = tracker.displayRangeMin
            currentNoteRangeMax = tracker.displayRangeMax
            channelAdapter.updateNoteRange(currentNoteRangeMin, currentNoteRangeMax)
        }

        // Mettre à jour l'UI avec l'état actuel du playback
        getMediaController()?.playbackState?.let { updatePlaybackUI(it) }

        // Mettre à jour l'UI avec les métadonnées actuelles (titre, artiste)
        getMediaController()?.metadata?.let { updateTrackInfo(it) }
    }

    override fun onPause() {
        super.onPause()
        // Se désenregistrer
        MidiEventDispatcher.removeMidiEventListener(this)
        MidiEventDispatcher.removeAnalysisListener(this)
        getMediaController()?.unregisterCallback(mediaControllerCallback)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        testMidiDriver?.stop()
        testMidiDriver = null

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
        btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )

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
            PlaybackQueueManager.RepeatMode.NONE -> {
                android.R.drawable.ic_menu_revert to colorInactive
            }
            PlaybackQueueManager.RepeatMode.ALL -> {
                android.R.drawable.ic_menu_revert to colorActive
            }
            PlaybackQueueManager.RepeatMode.ONE -> {
                // Utiliser la même icône mais avec une indication "1"
                // Pour l'instant on utilise la même icône, on pourrait créer une icône custom
                android.R.drawable.ic_menu_revert to colorActive
            }
        }
        btnRepeat.setImageResource(iconRes)
        btnRepeat.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)

        // Ajouter un indicateur visuel pour le mode ONE (rotation légère)
        btnRepeat.rotation = if (repeatMode == PlaybackQueueManager.RepeatMode.ONE) 15f else 0f
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
                    "Shuffle: ${sortedTracks.size} morceaux",
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
        updateChannelsVisibility(false)
    }

    /**
     * Configure le bouton de rafraîchissement des claviers
     * Efface les notes fantômes et re-synchronise l'affichage
     */
    private fun setupRefreshButton() {
        btnRefreshKeyboards.setOnClickListener {
            refreshKeyboards()
        }
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

    /**
     * Rafraîchit tous les claviers en les enroulant/déroulant.
     * Cette méthode force le re-bind des ViewHolders via DiffUtil, ce qui:
     * - Ré-enregistre les ViewHolders dans viewHolderMap
     * - Réinitialise les PianoKeyboardViews
     * - Corrige les problèmes d'affichage des notes
     */
    private fun refreshKeyboards() {
        // 1. Éteindre toutes les notes visuelles sur tous les claviers
        channelAdapter.allNotesOff()

        // 2. Effacer les notes actives dans le tracker (pas l'analyse)
        MidiEventDispatcher.clearTrackerActiveNotes()

        // 3. Réinitialiser les volumes des canaux à 1.0
        for (channel in 0 until 16) {
            MidiAudioMixer.setChannelVolume(channel, 1.0f)
        }

        // 4. Enrouler puis dérouler tous les claviers pour forcer le re-bind
        // C'est ce qui fait vraiment fonctionner le refresh
        channelAdapter.collapseExpandAllKeyboards {
            // 5. Une fois les claviers re-bindés, demander au service de resync
            getMediaController()?.sendCommand(
                MidiPlaybackService.COMMAND_SYNC_VISUALIZER,
                null,
                null
            )
        }

        // 6. Afficher un toast de confirmation
        Toast.makeText(context, R.string.midi_keyboards_refreshed, Toast.LENGTH_SHORT).show()
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

                // Reset la visualisation
                channelAdapter.allNotesOff()
                updateChannelsVisibility(false)
            }
        }

        // Note: L'état play/pause est maintenant géré via MediaController callback (updatePlaybackUI)
    }

    // === MidiEventDispatcher.MidiEventListener Implementation ===

    override fun onNoteOn(channel: Int, note: Int, velocity: Int) {
        // Envoyer la note au clavier du canal concerné
        channelAdapter.noteOn(channel, note, velocity)
    }

    override fun onNoteOff(channel: Int, note: Int) {
        // Éteindre la note sur le clavier du canal concerné
        channelAdapter.noteOff(channel, note)
    }

    override fun onProgramChange(channel: Int, program: Int) {
        // Mettre à jour le nom de l'instrument affiché en temps réel
        channelAdapter.onProgramChange(channel, program)
    }

    override fun onAllNotesOff() {
        // Éteindre toutes les notes sur tous les claviers
        channelAdapter.allNotesOff()
    }

    // === MidiEventDispatcher.MidiAnalysisListener Implementation ===

    override fun onAnalysisComplete(
        noteMin: Int,
        noteMax: Int,
        displayMin: Int,
        displayMax: Int,
        tracks: List<MidiNoteTracker.TrackInfo>
    ) {
        // Stocker la plage de notes
        currentNoteRangeMin = displayMin
        currentNoteRangeMax = displayMax

        // Mettre à jour la liste des pistes avec claviers
        if (tracks.isNotEmpty()) {
            channelAdapter.setTracks(tracks, displayMin, displayMax)
            updateChannelsVisibility(true)

            // Après setTracks, les ViewHolders n'existent pas encore (le RecyclerView
            // fait son layout au prochain frame). Les noteOn qui arrivent entre-temps
            // ne trouvent rien dans viewHolderMap. On demande un re-sync du visualiseur
            // après le layout pour que les notes en cours s'affichent sur les claviers.
            channelsList.post {
                getMediaController()?.sendCommand(
                    MidiPlaybackService.COMMAND_SYNC_VISUALIZER,
                    null,
                    null
                )
            }

            // Détecter si c'est un MIDI piano deux mains
            twoHandsInfo = MidiNoteTracker.detectTwoHands(tracks)
            btnTwoHandsPractice.visibility = if (twoHandsInfo?.isDetected == true) {
                View.VISIBLE
            } else {
                View.GONE
            }
        } else {
            updateChannelsVisibility(false)
            twoHandsInfo = null
            btnTwoHandsPractice.visibility = View.GONE
        }
    }

    override fun onAnalysisReset() {
        // Reset l'UI
        channelAdapter.allNotesOff()
        currentNoteRangeMin = 48
        currentNoteRangeMax = 84

        // Cacher la liste sans soumettre une liste vide à l'adapter.
        // Soumettre emptyList() provoque une race condition avec AsyncListDiffer
        // quand onAnalysisComplete() suit immédiatement (ex: skip de piste).
        // L'adapter gardera les anciennes pistes invisiblement, puis recevra
        // les nouvelles via onAnalysisComplete().
        channelsList.visibility = View.GONE
        channelsEmpty.visibility = View.VISIBLE

        // Reset la détection deux mains
        twoHandsInfo = null
        btnTwoHandsPractice.visibility = View.GONE

        // Reset les mutes lors du changement de fichier
        MidiEventDispatcher.clearMutes()

        // Reset les volumes des canaux lors du changement de fichier
        for (channel in 0..15) {
            MidiAudioMixer.setChannelVolume(channel, 1.0f)
        }
    }

    // === Helper Methods ===

    private fun updateChannelsVisibility(hasTracks: Boolean) {
        if (hasTracks) {
            channelsList.visibility = View.VISIBLE
            channelsEmpty.visibility = View.GONE
        } else {
            channelsList.visibility = View.GONE
            channelsEmpty.visibility = View.VISIBLE
            channelAdapter.setTracks(emptyList(), currentNoteRangeMin, currentNoteRangeMax)
        }
    }

    // === Test Button (Debug) ===

    private fun setupTestButton(view: View) {
        val btnTest = view.findViewById<Button>(R.id.btn_test_midi)
        btnTest?.setOnClickListener {
            testMidiDriverDirectly()
        }
    }

    private fun testMidiDriverDirectly() {
        Toast.makeText(context, "Testing MidiDriver...", Toast.LENGTH_SHORT).show()

        try {
            testMidiDriver = MidiDriver.getInstance()

            if (testMidiDriver == null) {
                Toast.makeText(context, "ERROR: MidiDriver is null", Toast.LENGTH_LONG).show()
                return
            }

            testMidiDriver?.start()

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    // Note On + simulation sur les claviers
                    val noteOn = byteArrayOf(0x90.toByte(), 60.toByte(), 100.toByte())
                    testMidiDriver?.write(noteOn)

                    // Afficher la note sur le clavier du canal 0
                    channelAdapter.noteOn(0, 60, 100)
                    Toast.makeText(context, "Note MIDI (Do) envoyée!", Toast.LENGTH_SHORT).show()

                    // Arrêter après 1 seconde
                    Handler(Looper.getMainLooper()).postDelayed({
                        val noteOff = byteArrayOf(0x80.toByte(), 60.toByte(), 0.toByte())
                        testMidiDriver?.write(noteOff)
                        channelAdapter.noteOff(0, 60)
                        testMidiDriver?.stop()
                    }, 1000)

                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }, 200)

        } catch (e: Exception) {
            Toast.makeText(context, "Exception: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
