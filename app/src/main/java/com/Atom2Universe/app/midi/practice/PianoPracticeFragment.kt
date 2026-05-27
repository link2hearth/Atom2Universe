package com.Atom2Universe.app.midi.practice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.session.MediaControllerCompat
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.Guideline
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.input.MidiConnectionStatus
import com.Atom2Universe.app.midi.input.MidiInputController
import com.Atom2Universe.app.midi.service.MidiPlaybackService
import com.Atom2Universe.app.midi.ui.MidiPlayerActivity
import com.Atom2Universe.app.midi.ui.MidiKeyboardSettingsDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.Atom2Universe.app.midi.practice.scoring.ScoringConfig
import com.Atom2Universe.app.midi.practice.scoring.ScoringObserver
import com.Atom2Universe.app.midi.practice.scoring.ScoreDisplayView
import com.Atom2Universe.app.midi.practice.themes.PracticeThemeManager
import com.Atom2Universe.app.midi.repository.SettingsRepository
import com.Atom2Universe.app.midi.ui.CustomThemeDialog
import com.Atom2Universe.app.midi.ui.ThemeSelectionDialog
import androidx.activity.result.contract.ActivityResultContracts
import com.Atom2Universe.app.midi.fluidsynth.FluidSynthEngine
import com.Atom2Universe.app.midi.fluidsynth.FluidSynthPracticeSynth
import com.Atom2Universe.app.midi.visualizer.GeneralMidiInstruments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fragment pour le mode pratique piano
 *
 * Affiche un clavier interactif avec des notes tombantes
 * et permet de pratiquer un canal MIDI a vitesse variable
 */
class PianoPracticeFragment : Fragment(), MidiKeyboardSettingsDialog.OnSettingsChangedListener {

    companion object {
        private const val TAG = "PianoPracticeFragment"

        private const val ARG_TRACK_FILE_PATH = "track_file_path"
        private const val ARG_CHANNEL_NUMBER = "channel_number"
        private const val ARG_NOTE_RANGE_MIN = "note_range_min"
        private const val ARG_NOTE_RANGE_MAX = "note_range_max"
        private const val ARG_INSTRUMENT_NAME = "instrument_name"
        private const val ARG_TRACK_TITLE = "track_title"
        private const val ARG_TRACK_INDEX = "track_index"
        private const val ARG_PROGRAM_NUMBER = "program_number"

        // Two-hands mode arguments
        private const val ARG_TWO_HANDS_MODE = "two_hands_mode"
        private const val ARG_LEFT_HAND_CHANNEL = "left_hand_channel"
        private const val ARG_RIGHT_HAND_CHANNEL = "right_hand_channel"
        private const val ARG_LEFT_HAND_NAME = "left_hand_name"
        private const val ARG_RIGHT_HAND_NAME = "right_hand_name"
        private const val ARG_LEFT_HAND_NOTE_MIN = "left_hand_note_min"
        private const val ARG_LEFT_HAND_NOTE_MAX = "left_hand_note_max"
        private const val ARG_RIGHT_HAND_NOTE_MIN = "right_hand_note_min"
        private const val ARG_RIGHT_HAND_NOTE_MAX = "right_hand_note_max"

        // Position refresh rate
        private const val POSITION_UPDATE_INTERVAL_MS = 16L // ~60fps

        // Divider position limits (percentage)
        private const val DIVIDER_MIN_PERCENT = 0.15f  // Minimum 15% for falling notes
        private const val DIVIDER_MAX_PERCENT = 0.85f  // Maximum 85% for falling notes (petit clavier)
        private const val DIVIDER_DEFAULT_PERCENT = 0.35f

        // SharedPreferences key
        private const val PREFS_NAME = "practice_prefs"
        private const val PREF_DIVIDER_POSITION = "divider_position"
        private const val PREF_SHEET_MUSIC_VISIBLE = "sheet_music_visible"
        private const val PREF_SHEET_MUSIC_Y_OFFSET = "sheet_music_y_offset"
        private const val PREF_SHEET_MUSIC_SCALE = "sheet_music_scale"
        @Suppress("unused")
        private const val PREF_VISUAL_STYLE = "visual_style"
        private const val PREF_MUTE_KEYBOARD_SOUND = "mute_keyboard_sound"
        private const val PREF_COUNTDOWN_DELAY = "countdown_delay_seconds"
        private const val PREF_START_ON_FIRST_NOTE = "start_on_first_note"

        // Notes dans un intervalle de 50ms sont considérées comme un accord
        private const val CHORD_TIMING_TOLERANCE_MS = 50L
        private const val MIN_RECORDED_NOTE_DURATION_MS = 120L

        fun newInstance(args: PracticeArgs): PianoPracticeFragment {
            return PianoPracticeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TRACK_FILE_PATH, args.trackFilePath)
                    putInt(ARG_CHANNEL_NUMBER, args.channelNumber)
                    putInt(ARG_NOTE_RANGE_MIN, args.noteRangeMin)
                    putInt(ARG_NOTE_RANGE_MAX, args.noteRangeMax)
                    putString(ARG_INSTRUMENT_NAME, args.instrumentName)
                    putString(ARG_TRACK_TITLE, args.trackTitle)
                    putInt(ARG_TRACK_INDEX, args.trackIndex)
                    putInt(ARG_PROGRAM_NUMBER, args.programNumber)

                    // Two-hands mode
                    putBoolean(ARG_TWO_HANDS_MODE, args.isTwoHandsMode)
                    putInt(ARG_LEFT_HAND_CHANNEL, args.leftHandChannel)
                    putInt(ARG_RIGHT_HAND_CHANNEL, args.rightHandChannel)
                    putString(ARG_LEFT_HAND_NAME, args.leftHandName)
                    putString(ARG_RIGHT_HAND_NAME, args.rightHandName)
                    putInt(ARG_LEFT_HAND_NOTE_MIN, args.leftHandNoteRangeMin)
                    putInt(ARG_LEFT_HAND_NOTE_MAX, args.leftHandNoteRangeMax)
                    putInt(ARG_RIGHT_HAND_NOTE_MIN, args.rightHandNoteRangeMin)
                    putInt(ARG_RIGHT_HAND_NOTE_MAX, args.rightHandNoteRangeMax)
                }
            }
        }
    }

    // Views
    private lateinit var fallingNotesView: FallingNotesView
    private lateinit var pianoView: InteractivePianoView
    private lateinit var pianoScroll: HorizontalScrollView
    private lateinit var resizeDivider: ResizeDividerView
    private lateinit var guidelinePianoTop: Guideline
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var sheetMusicContainer: DraggableSheetMusicContainer
    private lateinit var sheetMusicView: SheetMusicView
    private lateinit var btnBack: ImageButton
    private lateinit var tabsContainer: HorizontalScrollView
    private lateinit var tabLibrary: TextView
    private lateinit var tabPlaylists: TextView
    private lateinit var tabNowPlaying: TextView
    private lateinit var tabPractice: TextView
    private lateinit var btnMenu: ImageButton
    private lateinit var btnMidiKeyboard: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnRestart: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var txtTrackTitle: TextView
    private lateinit var txtInstrument: TextView
    private lateinit var titleOverlay: View
    private lateinit var topOverlay: View
    private lateinit var markerSeekBar: MarkerSeekBarView
    private lateinit var txtTimeCurrent: TextView
    private lateinit var txtTimeTotal: TextView
    private lateinit var seekbarPosition: SeekBar  // Gardé pour compatibilité (caché)
    private lateinit var btnMark: ImageButton
    private lateinit var btnGotoMark: ImageButton
    private lateinit var btnAccompaniment: ImageButton
    private lateinit var btnIPlay: ImageButton
    private lateinit var btnKeyboardLeds: ImageButton
    private lateinit var btnMuteLedSound: ImageButton
    private lateinit var btnMuteInternalSynth: ImageButton
    private lateinit var btnSpeed: ImageButton
    private lateinit var btnCountdown: ImageButton
    private lateinit var btnRecord: ImageButton
    private lateinit var txtSpeed: TextView
    private lateinit var speedSliderContainer: View
    private lateinit var seekbarSpeed: SeekBar
    private lateinit var checkboxAccompaniment: CheckBox
    private lateinit var checkboxIPlay: CheckBox
    private lateinit var checkboxNoteNames: CheckBox

    // Free mode controls
    private lateinit var freeModeControls: View
    private lateinit var octaveIndicator: OctaveRangeIndicatorView
    private lateinit var btnShiftLeft: ImageButton
    private lateinit var btnShiftRight: ImageButton
    private lateinit var btnOctaveCountMinus: ImageButton
    private lateinit var btnOctaveCountPlus: ImageButton
    private lateinit var txtOctaveCount: TextView
    private lateinit var instrumentSelector: View
    private lateinit var txtCurrentInstrument: TextView
    private lateinit var btnRecordFreeMode: ImageButton
    private lateinit var controlsPanel: View
    // Free mode button (visible in MIDI mode to switch back to free mode)
    private lateinit var btnFreeMode: com.google.android.material.button.MaterialButton

    // Hand selection buttons (visible in two-hands mode only) - controls LEDs/visual
    private lateinit var handSelectionContainer: View
    private lateinit var btnHandLeft: ImageButton
    private lateinit var btnHandsBoth: ImageButton
    private lateinit var btnHandRight: ImageButton

    // Hand sound buttons (visible in two-hands mode only) - controls audio per hand
    private lateinit var handSoundContainer: View
    private lateinit var btnHandLeftSound: ImageButton
    private lateinit var btnHandsBothSound: ImageButton
    private lateinit var btnHandRightSound: ImageButton

    // Hand sound mode state (which hands play audio)
    private var handSoundMode: HandPracticeMode = HandPracticeMode.BOTH_HANDS

    // Countdown/start delay settings
    private var countdownDelaySeconds: Int = 0  // 0 = disabled, 1-10 = delay in seconds
    private var startOnFirstNote: Boolean = false  // Start when user plays first note
    private var isWaitingForFirstNote: Boolean = false  // Currently waiting for user to play
    private var countdownJob: kotlinx.coroutines.Job? = null  // Active countdown coroutine

    // Etat des boutons toggle
    private var isAccompanimentOn = true
    private var isIPlayOn = false
    private var isSpeedSliderVisible = false

    // Volume levels (0-127) - controllable via long press on buttons
    private var accompanimentVolume = 100  // Volume for accompaniment (all non-target channels)
    private var targetChannelVolume = 100  // Volume for the target channel (when "Je joue" is OFF)

    // Volume popup
    private var volumePopup: PopupWindow? = null

    // Mode libre (pas de fichier MIDI)
    private var isFreeMode = false
    private var currentProgram = 0  // Programme MIDI actuel (0-127)

    // External MIDI keyboard
    private var midiInputController: MidiInputController? = null
    private var isMidiKeyboardConnected = false
    private var ledOutputChannel = 0  // 0-15 (displayed as 1-16 in UI)
    private var muteKeyboardSound = false  // Default OFF - avoid muting on practice entry
    private var ledOctaveOffset = 0  // -4 to +4 octaves (in semitones: -48 to +48)
    private var sendLedsToKeyboard = true  // Send note LEDs to external keyboard (default ON)
    private var muteLedChannelSound = true  // Mute the sound on LED channel (default ON - light only, no sound)

    // Paliers de vitesse : 0.1 à 1.0 (10% à 100%)
    private val speedSteps = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f)

    // Playback controller
    private lateinit var playbackController: PracticePlaybackController
    private lateinit var session: PracticeSession

    // Synthesizer (Sonivox ou SF2)
    private var synthesizer: PracticeSynthesizer? = null

    // Scoring system (passive observer - never modifies practice flow)
    private lateinit var scoringObserver: ScoringObserver
    private lateinit var scoringConfig: ScoringConfig
    private lateinit var scoreDisplayView: ScoreDisplayView

    // Custom theme dialog and image picker
    private var customThemeDialog: CustomThemeDialog? = null
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleImageSelected(it) }
    }

    // Audio stream recorder (live audio capture)
    private val audioStreamRecorder = AudioStreamRecorder()
    private val recordingHandler = Handler(Looper.getMainLooper())
    private val maxRecordingDurationMs = 30L * 60 * 1000 // 30 minutes
    private val autoStopRecordingRunnable = Runnable {
        if (audioStreamRecorder.isRecording()) {
            stopAudioRecording()
            Toast.makeText(context, getString(R.string.practice_recording_max_duration), Toast.LENGTH_LONG).show()
        }
    }

    // Export folder picker
    private var pendingExportWavFile: File? = null
    private var pendingExportFilename: String = ""
    private val exportFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            performSaveToFolder(uri)
        } else {
            // Utilisateur a annulé le folder picker - supprimer le fichier temporaire
            pendingExportWavFile?.delete()
            pendingExportWavFile = null
        }
    }

    // Position marquee (bookmark)
    private var markedPositionMs: Long = -1L

    // Flag pour eviter les boucles lors du seek manuel
    private var isUserSeeking = false

    // Animation handler
    private val handler = Handler(Looper.getMainLooper())
    private var isAnimating = false

    // Mode attente (wait mode)
    private var isInWaitMode = false

    // Pour le tracking des notes avec timing (validation ordonnée)
    data class TimedNote(val note: Int, val timeMs: Long)

    // Notes en attente d'être jouées en mode attente (avec timing pour grouper les accords)
    private val waitingNotesWithTiming = mutableListOf<TimedNote>()

    // Notes du groupe actuel que l'utilisateur doit jouer (un accord ou une note seule)
    // Utilise un Map pour compter combien de fois chaque note doit être jouée
    // (pour gérer Do-Do-Do-Do où la même note apparaît plusieurs fois)
    private val currentWaitingNoteCounts = mutableMapOf<Int, Int>()

    // Notes actuellement tenues par l'utilisateur (clavier virtuel + externe)
    // Permet de valider une note si elle est déjà tenue quand elle entre dans la hit zone
    // Thread-safe car accédé depuis le thread MIDI et le UI thread
    private val currentlyHeldNotes = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    // Mode enregistrement (capture de la partition jouée)
    private var isPracticeRecordingEnabled = false
    private var recordingStartPositionMs: Long = 0L
    private var recordingStartRealtimeMs: Long = 0L
    private var isPracticeRecordingClockFrozen = false
    private var isPracticeRecordingUpdatesRunning = false
    private val recordedNotes = mutableListOf<ScheduledNote>()
    private val activeRecordedNotes = mutableMapOf<Int, ActiveRecordedNote>()

    private data class ActiveRecordedNote(
        val startTimeMs: Long,
        val index: Int
    )

    // Divider position (percentage)
    private var currentDividerPercent = DIVIDER_DEFAULT_PERCENT
    private lateinit var prefs: SharedPreferences

    // Sheet music state
    private var isSheetMusicVisible = false
    private var sheetMusicYOffset = 0f
    private var sheetMusicScale = 1.0f

    // Broadcast receiver for SF2 load complete notifications
    private val sf2LoadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MidiPlaybackService.ACTION_SF2_LOAD_COMPLETE) {
                // SF2 loading complete in the main service - reload practice synthesizer
                viewLifecycleOwner.lifecycleScope.launch {
                    reloadSynthesizerFromSettings()
                }
            }
        }
    }
    private var isReceiverRegistered = false

    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            if (isAnimating && playbackController.isPlaying()) {
                // Utiliser l'horloge partagée comme source unique de position
                val position = playbackController.sharedClock.getCurrentPositionMs()

                // Update expected notes on piano
                updateExpectedNotes(position)

                // Update position slider and time display
                if (!isUserSeeking) {
                    updatePositionDisplay(position)
                }

                // Note: plus besoin de resynchroniser manuellement car FallingNotesView
                // et SheetMusicView utilisent maintenant la même horloge partagée

                updatePracticeRecordingState(position)

                handler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private val recordingUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isPracticeRecordingEnabled || !isPracticeRecordingUpdatesRunning) {
                isPracticeRecordingUpdatesRunning = false
                return
            }

            if (isInWaitMode || playbackController.isPlaying()) {
                pausePracticeRecordingClock()
                handler.removeCallbacks(this)
                isPracticeRecordingUpdatesRunning = false
                return
            }

            val position = getPracticeRecordingPositionMs()
            updatePracticeRecordingState(position)

            if (isSheetMusicVisible) {
                sheetMusicView.updatePosition(position)
            }

            if (!isUserSeeking && !isFreeMode) {
                updatePositionDisplay(position)
            }

            if (isPracticeRecordingUpdatesRunning) {
                handler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_piano_practice, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        android.util.Log.d(TAG, "onViewCreated: START")

        // Nettoyer les anciens fichiers d'enregistrement temporaires
        cleanupOldRecordings()

        // Pause the main playback service when entering training mode
        // The service stays ready to resume when user goes back to Now Playing
        pauseMainPlayback()

        // Initialize preferences
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        muteKeyboardSound = prefs.getBoolean(PREF_MUTE_KEYBOARD_SOUND, false)
        countdownDelaySeconds = prefs.getInt(PREF_COUNTDOWN_DELAY, 0)
        startOnFirstNote = prefs.getBoolean(PREF_START_ON_FIRST_NOTE, false)
        android.util.Log.d(TAG, "onViewCreated: muteKeyboardSoundPref=$muteKeyboardSound countdownDelay=$countdownDelaySeconds startOnFirstNote=$startOnFirstNote")
        logAudioOutputs("practice_onViewCreated")

        // Initialize color manager for custom colors
        ColorSettingsManager.init(requireContext())
        lifecycleScope.launch {
            ColorSettingsManager.loadAllColors()
            // Refresh views after colors are loaded
            refreshColors()
        }

        // Parse arguments
        val args = parseArguments()
        android.util.Log.d(TAG, "onViewCreated: args parsed - file=${args.trackFilePath}, channel=${args.channelNumber}")
        session = PracticeSession(args)

        // Detect free mode (no MIDI file)
        isFreeMode = args.trackFilePath.isEmpty()

        // Init synthesizer FIRST (chooses between Sonivox and SF2)
        initSynthesizer()
        configurePracticeAudioRouting("after_init_synth")

        // Init playback controller BEFORE setupListeners (listeners access it)
        playbackController = PracticePlaybackController()
        synthesizer?.let { playbackController.initialize(it) }

        // Init views
        initViews(view)

        // Setup resize divider
        setupResizeDivider()

        // Setup sheet music view
        setupSheetMusicView()

        // Setup listeners AFTER playbackController is initialized
        setupListeners()

        // Setup scoring system (passive observer)
        initScoring()

        // Setup external MIDI keyboard
        setupMidiKeyboard()

        // Setup free mode listeners if applicable
        if (isFreeMode) {
            setupFreeModeListeners()
            setupFreeModeUI(args)
        } else {
            // Setup display and load file for practice mode
            setupDisplay(args)
            loadMidiFile(args)
        }
    }

    private fun parseArguments(): PracticeArgs {
        val args = arguments ?: throw IllegalStateException("Arguments required")
        return PracticeArgs(
            trackFilePath = args.getString(ARG_TRACK_FILE_PATH) ?: "",
            channelNumber = args.getInt(ARG_CHANNEL_NUMBER, 0),
            noteRangeMin = args.getInt(ARG_NOTE_RANGE_MIN, 48),
            noteRangeMax = args.getInt(ARG_NOTE_RANGE_MAX, 84),
            instrumentName = args.getString(ARG_INSTRUMENT_NAME) ?: "Piano",
            trackTitle = args.getString(ARG_TRACK_TITLE) ?: "Track",
            trackIndex = args.getInt(ARG_TRACK_INDEX, 0),
            programNumber = args.getInt(ARG_PROGRAM_NUMBER, 0),
            // Two-hands mode
            isTwoHandsMode = args.getBoolean(ARG_TWO_HANDS_MODE, false),
            leftHandChannel = args.getInt(ARG_LEFT_HAND_CHANNEL, -1),
            rightHandChannel = args.getInt(ARG_RIGHT_HAND_CHANNEL, -1),
            leftHandName = args.getString(ARG_LEFT_HAND_NAME) ?: "",
            rightHandName = args.getString(ARG_RIGHT_HAND_NAME) ?: "",
            leftHandNoteRangeMin = args.getInt(ARG_LEFT_HAND_NOTE_MIN, 0),
            leftHandNoteRangeMax = args.getInt(ARG_LEFT_HAND_NOTE_MAX, 0),
            rightHandNoteRangeMin = args.getInt(ARG_RIGHT_HAND_NOTE_MIN, 0),
            rightHandNoteRangeMax = args.getInt(ARG_RIGHT_HAND_NOTE_MAX, 0)
        )
    }

    private fun initViews(view: View) {
        rootLayout = view as ConstraintLayout
        fallingNotesView = view.findViewById(R.id.falling_notes_view)
        pianoView = view.findViewById(R.id.piano_view)
        pianoScroll = view.findViewById(R.id.piano_scroll)
        resizeDivider = view.findViewById(R.id.resize_divider)
        guidelinePianoTop = view.findViewById(R.id.guideline_piano_top)
        sheetMusicContainer = view.findViewById(R.id.sheet_music_container)
        sheetMusicView = view.findViewById(R.id.sheet_music_view)
        btnBack = view.findViewById(R.id.btn_back)
        tabsContainer = view.findViewById(R.id.tabs_container)
        tabLibrary = view.findViewById(R.id.tab_library)
        tabPlaylists = view.findViewById(R.id.tab_playlists)
        tabNowPlaying = view.findViewById(R.id.tab_now_playing)
        tabPractice = view.findViewById(R.id.tab_practice)

        // Cacher les onglets de navigation en mode pratique (training et free mode)
        tabsContainer.visibility = View.GONE
        btnMenu = view.findViewById(R.id.btn_menu)
        btnMidiKeyboard = view.findViewById(R.id.btn_midi_keyboard)
        btnPlayPause = view.findViewById(R.id.btn_play_pause)
        btnRestart = view.findViewById(R.id.btn_restart)
        btnStop = view.findViewById(R.id.btn_stop)
        txtTrackTitle = view.findViewById(R.id.txt_track_title)
        txtInstrument = view.findViewById(R.id.txt_instrument)
        titleOverlay = view.findViewById(R.id.title_overlay)
        txtTimeCurrent = view.findViewById(R.id.txt_time_current)
        txtTimeTotal = view.findViewById(R.id.txt_time_total)
        seekbarPosition = view.findViewById(R.id.seekbar_position)
        btnMark = view.findViewById(R.id.btn_mark)
        btnGotoMark = view.findViewById(R.id.btn_goto_mark)
        btnAccompaniment = view.findViewById(R.id.btn_accompaniment)
        btnIPlay = view.findViewById(R.id.btn_i_play)
        btnKeyboardLeds = view.findViewById(R.id.btn_keyboard_leds)
        btnMuteLedSound = view.findViewById(R.id.btn_mute_led_sound)
        btnMuteInternalSynth = view.findViewById(R.id.btn_mute_internal_synth)
        btnSpeed = view.findViewById(R.id.btn_speed)
        btnCountdown = view.findViewById(R.id.btn_countdown)
        btnRecord = view.findViewById(R.id.btn_record)
        txtSpeed = view.findViewById(R.id.txt_speed)
        speedSliderContainer = view.findViewById(R.id.speed_slider_container)
        seekbarSpeed = view.findViewById(R.id.seekbar_speed)
        checkboxAccompaniment = view.findViewById(R.id.checkbox_accompaniment)
        checkboxIPlay = view.findViewById(R.id.checkbox_i_play)
        checkboxNoteNames = view.findViewById(R.id.checkbox_note_names)

        // Free mode controls
        freeModeControls = view.findViewById(R.id.free_mode_controls)
        octaveIndicator = view.findViewById(R.id.octave_indicator)
        btnShiftLeft = view.findViewById(R.id.btn_shift_left)
        btnShiftRight = view.findViewById(R.id.btn_shift_right)
        btnOctaveCountMinus = view.findViewById(R.id.btn_octave_count_minus)
        btnOctaveCountPlus = view.findViewById(R.id.btn_octave_count_plus)
        txtOctaveCount = view.findViewById(R.id.txt_octave_count)
        instrumentSelector = view.findViewById(R.id.instrument_selector)
        txtCurrentInstrument = view.findViewById(R.id.txt_current_instrument)
        btnRecordFreeMode = view.findViewById(R.id.btn_record_free_mode)
        controlsPanel = view.findViewById(R.id.controls_panel)
        btnFreeMode = view.findViewById(R.id.btn_free_mode)
        // Hand selection buttons (two-hands mode only) - visual/LED control
        handSelectionContainer = view.findViewById(R.id.hand_selection_container)
        btnHandLeft = view.findViewById(R.id.btn_hand_left)
        btnHandsBoth = view.findViewById(R.id.btn_hands_both)
        btnHandRight = view.findViewById(R.id.btn_hand_right)
        // Hand sound buttons (two-hands mode only) - audio control
        handSoundContainer = view.findViewById(R.id.hand_sound_container)
        btnHandLeftSound = view.findViewById(R.id.btn_hand_left_sound)
        btnHandsBothSound = view.findViewById(R.id.btn_hands_both_sound)
        btnHandRightSound = view.findViewById(R.id.btn_hand_right_sound)
        // Top overlay avec MarkerSeekBar
        topOverlay = view.findViewById(R.id.top_overlay)
        markerSeekBar = view.findViewById(R.id.marker_seekbar)

        // Scoring display
        scoreDisplayView = view.findViewById(R.id.score_display)
    }

    /**
     * Initialize the scoring system (passive observer)
     */
    private fun initScoring() {
        // Load user preferences for scoring display
        scoringConfig = ScoringConfig.load(requireContext())

        // Create the observer
        scoringObserver = ScoringObserver()

        // Configure the display view
        scoreDisplayView.setConfig(scoringConfig)

        // Connect observer to display
        scoringObserver.addListener(scoreDisplayView)

        // Hide scoring in free mode (no expected notes to compare against)
        if (isFreeMode) {
            scoreDisplayView.visibility = View.GONE
        }
    }

    /**
     * Setup the resize divider for adjusting falling notes / piano split
     */
    private fun setupResizeDivider() {
        // Restore saved position
        currentDividerPercent = prefs.getFloat(PREF_DIVIDER_POSITION, DIVIDER_DEFAULT_PERCENT)
        updateGuidelinePosition(currentDividerPercent)

        // Handle drag events
        resizeDivider.onDragListener = { deltaY ->
            val layoutHeight = rootLayout.height.toFloat()
            if (layoutHeight > 0) {
                // Convert pixel delta to percentage change
                val deltaPercent = deltaY / layoutHeight
                val newPercent = (currentDividerPercent + deltaPercent)
                    .coerceIn(DIVIDER_MIN_PERCENT, DIVIDER_MAX_PERCENT)

                currentDividerPercent = newPercent
                updateGuidelinePosition(newPercent)
            }
        }

        // Save position when drag ends
        resizeDivider.onDragEndListener = {
            prefs.edit {
                putFloat(PREF_DIVIDER_POSITION, currentDividerPercent)
            }
        }
    }

    /**
     * Update the guideline position (percentage from top)
     */
    private fun updateGuidelinePosition(percent: Float) {
        val params = guidelinePianoTop.layoutParams as ConstraintLayout.LayoutParams
        params.guidePercent = percent
        guidelinePianoTop.layoutParams = params
    }

    /**
     * Setup the sheet music view for draggable positioning
     */
    private fun setupSheetMusicView() {
        // Restore saved visibility, position and scale
        isSheetMusicVisible = prefs.getBoolean(PREF_SHEET_MUSIC_VISIBLE, false)
        sheetMusicYOffset = prefs.getFloat(PREF_SHEET_MUSIC_Y_OFFSET, 0f)
        sheetMusicScale = prefs.getFloat(PREF_SHEET_MUSIC_SCALE, 1.0f)

        // Calculer la hauteur disponible et définir le scale max (50% de l'écran)
        rootLayout.post {
            val screenHeight = rootLayout.height.toFloat()
            sheetMusicView.setAvailableHeight(screenHeight)

            // Apply saved scale (après avoir défini la limite)
            sheetMusicView.setScale(sheetMusicScale)

            // Apply initial height based on scale
            val newHeight = sheetMusicView.getDesiredHeight()
            sheetMusicContainer.setContainerHeight(newHeight)
        }

        // Set initial visibility
        sheetMusicContainer.visibility = if (isSheetMusicVisible) View.VISIBLE else View.GONE

        // Apply saved Y offset
        if (sheetMusicYOffset != 0f) {
            sheetMusicContainer.translationY = sheetMusicYOffset
        }

        // Setup scale change listener
        sheetMusicView.setOnScaleChangedListener { scale, newHeight ->
            sheetMusicScale = scale
            sheetMusicContainer.setContainerHeight(newHeight)

            // Save scale
            prefs.edit {
                putFloat(PREF_SHEET_MUSIC_SCALE, scale)
            }
        }

        // Setup drag listeners
        sheetMusicContainer.onDragListener = { deltaY ->
            // Calculate new Y offset with constraints
            val newOffset = sheetMusicContainer.translationY + deltaY

            // Get toolbar height for minimum constraint
            val toolbarContainer = view?.findViewById<View>(R.id.toolbar_container)
            val toolbarHeight = toolbarContainer?.height ?: 0
            val minY = 0f  // Don't go above the initial position

            // Get controls panel for maximum constraint
            val controlsPanel = view?.findViewById<View>(R.id.controls_panel)
            val controlsTop = controlsPanel?.top ?: rootLayout.height
            val maxY = (controlsTop - sheetMusicContainer.height - toolbarHeight).toFloat().coerceAtLeast(0f)

            // Apply constrained offset
            sheetMusicYOffset = newOffset.coerceIn(minY, maxY)
            sheetMusicContainer.translationY = sheetMusicYOffset
        }

        sheetMusicContainer.onDragEndListener = {
            // Save position when drag ends
            prefs.edit {
                putFloat(PREF_SHEET_MUSIC_Y_OFFSET, sheetMusicYOffset)
            }
        }
    }

    /**
     * Toggle sheet music visibility
     */
    private fun toggleSheetMusic() {
        isSheetMusicVisible = !isSheetMusicVisible
        sheetMusicContainer.visibility = if (isSheetMusicVisible) View.VISIBLE else View.GONE

        if (isSheetMusicVisible) {
            sheetMusicView.setRecordingModeEnabled(isPracticeRecordingEnabled)
            sheetMusicView.setRecordedNotes(recordedNotes)

            val position = if (isPracticeRecordingEnabled) {
                getPracticeRecordingPositionMs()
            } else {
                playbackController.getCurrentPositionMs()
            }

            // If visible and playing, start animation
            if (playbackController.isPlaying()) {
                val currentSpeed = speedSteps[seekbarSpeed.progress]
                sheetMusicView.startAnimation(position, currentSpeed)
            } else {
                sheetMusicView.updatePosition(position)
                startPracticeRecordingUpdatesIfNeeded()
            }
        } else {
            sheetMusicView.stopAnimation()
        }

        // Save visibility
        prefs.edit {
            putBoolean(PREF_SHEET_MUSIC_VISIBLE, isSheetMusicVisible)
        }
    }

    private fun setupListeners() {
        // Back button
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Free mode button (switch from MIDI practice to free play)
        btnFreeMode.setOnClickListener {
            showSwitchToFreeModeDialog()
        }

        // Hand selection buttons (two-hands mode only) - visual/LED control
        btnHandLeft.setOnClickListener {
            setHandPracticeMode(HandPracticeMode.LEFT_HAND_ONLY)
        }
        btnHandsBoth.setOnClickListener {
            setHandPracticeMode(HandPracticeMode.BOTH_HANDS)
        }
        btnHandRight.setOnClickListener {
            setHandPracticeMode(HandPracticeMode.RIGHT_HAND_ONLY)
        }

        // Hand sound buttons (two-hands mode only) - audio control
        btnHandLeftSound.setOnClickListener {
            setHandSoundMode(HandPracticeMode.LEFT_HAND_ONLY)
        }
        btnHandsBothSound.setOnClickListener {
            setHandSoundMode(HandPracticeMode.BOTH_HANDS)
        }
        btnHandRightSound.setOnClickListener {
            setHandSoundMode(HandPracticeMode.RIGHT_HAND_ONLY)
        }

        // Navigation tabs - navigate back to main activity tabs
        tabLibrary.setOnClickListener {
            navigateToTab(0)
        }
        tabPlaylists.setOnClickListener {
            navigateToTab(1)
        }
        tabNowPlaying.setOnClickListener {
            navigateToTab(2)
        }
        // tabPractice is current, no action needed

        // Menu button - show settings
        btnMenu.setOnClickListener {
            showSettingsMenu()
        }

        // Record button
        btnRecord.setOnClickListener {
            toggleRecording()
        }

        // Record button (Free Mode version - same action)
        btnRecordFreeMode.setOnClickListener {
            toggleRecording()
        }

        // Play/Pause button
        btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        // Restart button
        btnRestart.setOnClickListener {
            restart()
        }

        // Stop button
        btnStop.setOnClickListener {
            stop()
        }

        // Position seekbar (seek dans la piste)
        seekbarPosition.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val durationMs = playbackController.getDurationMs()
                    val positionMs = (progress.toLong() * durationMs) / 1000L
                    txtTimeCurrent.text = formatTime(positionMs)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    val durationMs = playbackController.getDurationMs()
                    val positionMs = (it.progress.toLong() * durationMs) / 1000L
                    playbackController.seekTo(positionMs)  // Le callback onAllNotesOff sera appele automatiquement
                    fallingNotesView.updatePosition(positionMs)
                    sheetMusicView.updatePosition(positionMs)
                    handlePracticeRecordingSeek(positionMs)
                }
                isUserSeeking = false
            }
        })

        // MarkerSeekBar callbacks
        markerSeekBar.onProgressChanged = { progress, fromUser ->
            if (fromUser) {
                val durationMs = playbackController.getDurationMs()
                val positionMs = (progress * durationMs).toLong()
                txtTimeCurrent.text = formatTime(positionMs)
                updateRemainingTimeDisplay(positionMs, durationMs)

                // Effectuer le seek immédiatement
                playbackController.seekTo(positionMs)
                fallingNotesView.updatePosition(positionMs)
                sheetMusicView.updatePosition(positionMs)
                handlePracticeRecordingSeek(positionMs)
            }
        }

        markerSeekBar.onMarkerClicked = { positionMs ->
            // Sauter à la position du marqueur
            playbackController.seekTo(positionMs)
            fallingNotesView.updatePosition(positionMs)
            sheetMusicView.updatePosition(positionMs)
            updatePositionDisplay(positionMs)
            handlePracticeRecordingSeek(positionMs)
        }

        markerSeekBar.onMarkersChanged = { markers ->
            // On pourrait sauvegarder les marqueurs ici si nécessaire
            // Pour l'instant juste un feedback utilisateur
            if (markers.isNotEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.practice_marker_added, formatTime(markers.last())),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Bouton Mark : sauvegarder la position actuelle
        btnMark.setOnClickListener {
            markedPositionMs = playbackController.getCurrentPositionMs()
            btnGotoMark.alpha = 1.0f
            btnMark.setColorFilter(getThemeAccentColor())
            Toast.makeText(
                requireContext(),
                getString(R.string.practice_marker_position, formatTime(markedPositionMs)),
                Toast.LENGTH_SHORT
            ).show()
        }

        // Bouton Goto Mark : revenir a la position marquee
        btnGotoMark.setOnClickListener {
            if (markedPositionMs >= 0) {
                playbackController.seekTo(markedPositionMs)  // Le callback onAllNotesOff sera appele automatiquement
                fallingNotesView.updatePosition(markedPositionMs)
                sheetMusicView.updatePosition(markedPositionMs)
                updatePositionDisplay(markedPositionMs)
                handlePracticeRecordingSeek(markedPositionMs)
            }
        }

        // Bouton Accompagnement (toggle)
        btnAccompaniment.setOnClickListener {
            // In wait mode, orchestration is ALWAYS disabled
            if (session.waitModeEnabled && !isAccompanimentOn) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.practice_accompaniment_disabled_in_wait_mode),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            isAccompanimentOn = !isAccompanimentOn
            playbackController.playAccompaniment = isAccompanimentOn
            session.setAccompanimentEnabled(isAccompanimentOn)
            updateAccompanimentButtonState()
        }

        // Long press on Accompaniment button -> show volume slider
        btnAccompaniment.setOnLongClickListener {
            showVolumePopup(btnAccompaniment, isAccompaniment = true)
            true
        }

        // Bouton Je Joue (toggle) - mute le canal cible
        btnIPlay.setOnClickListener {
            isIPlayOn = !isIPlayOn
            playbackController.muteTargetChannel = isIPlayOn
            updateIPlayButtonState()
        }

        // Long press on "Je Joue" button -> show volume slider for target channel
        btnIPlay.setOnLongClickListener {
            showVolumePopup(btnIPlay, isAccompaniment = false)
            true
        }

        // Bouton LEDs clavier (toggle) - envoyer/ne pas envoyer les notes au clavier externe
        btnKeyboardLeds.setOnClickListener {
            sendLedsToKeyboard = !sendLedsToKeyboard
            updateKeyboardLedsButtonState()

            // Quand on désactive l'envoi, éteindre toutes les LEDs sur le clavier externe
            if (!sendLedsToKeyboard && isMidiKeyboardConnected) {
                midiInputController?.sendAllNotesOff()
                android.util.Log.d(TAG, "Keyboard LEDs disabled - sent All Notes Off to turn off LEDs")
            }

            android.util.Log.d(TAG, "Keyboard LEDs toggled: sendLedsToKeyboard=$sendLedsToKeyboard")
        }

        // Bouton Mute son LED (toggle) - lumiere sans son sur le clavier externe
        btnMuteLedSound.setOnClickListener {
            muteLedChannelSound = !muteLedChannelSound
            updateMuteLedSoundButtonState()

            // Quand on desactive le mute, restaurer le volume sur le canal LED
            if (!muteLedChannelSound && isMidiKeyboardConnected) {
                midiInputController?.sendControlChange(ledOutputChannel, 7, 100)   // Volume = 100
                midiInputController?.sendControlChange(ledOutputChannel, 11, 127)  // Expression = 127
                android.util.Log.d(TAG, "Restored LED channel volume: CC7=100, CC11=127 on channel $ledOutputChannel")
            }

            android.util.Log.d(TAG, "Mute LED sound toggled: muteLedChannelSound=$muteLedChannelSound")
        }

        // Bouton Mute synthé interne (toggle) - ne pas jouer les notes du clavier sur le synthé interne
        // Quand actif: l'utilisateur entend son clavier depuis son clavier, pas de la tablette
        btnMuteInternalSynth.setOnClickListener {
            // Toggle: muteKeyboardSound=false means internal synth is muted for user notes
            muteKeyboardSound = !muteKeyboardSound
            updateMuteInternalSynthButtonState()
            prefs.edit {
                putBoolean(PREF_MUTE_KEYBOARD_SOUND, muteKeyboardSound)
            }
            android.util.Log.d(TAG, "Mute internal synth toggled: muteKeyboardSound=$muteKeyboardSound (internal synth ${if (muteKeyboardSound) "plays" else "muted"} for user notes)")
        }

        // Bouton Vitesse - affiche/cache le slider
        btnSpeed.setOnClickListener {
            toggleSpeedSlider()
        }

        // Zone speed control entiere cliquable
        view?.findViewById<View>(R.id.speed_control)?.setOnClickListener {
            toggleSpeedSlider()
        }

        // Bouton Retardateur - affiche le dialog de configuration
        btnCountdown.setOnClickListener {
            showCountdownSettingsDialog()
        }
        updateCountdownButtonState()

        // Slider de vitesse (6 paliers: 0.5, 0.6, 0.7, 0.8, 0.9, 1.0)
        seekbarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val speed = speedSteps[progress]
                    playbackController.setSpeed(speed)
                    fallingNotesView.setPlaybackSpeed(speed)
                    sheetMusicView.setPlaybackSpeed(speed)
                    updateSpeedDisplay(speed)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Checkboxes caches - gardes pour compatibilite mais geres par les boutons
        checkboxAccompaniment.setOnCheckedChangeListener { _, isChecked ->
            playbackController.playAccompaniment = isChecked
            session.setAccompanimentEnabled(isChecked)
        }

        checkboxIPlay.setOnCheckedChangeListener { _, isChecked ->
            playbackController.muteTargetChannel = isChecked
        }

        // Note names active par defaut (checkbox cache)
        checkboxNoteNames.isChecked = true
        pianoView.showNoteNames = true
        session.setShowNoteNames(true)

        checkboxNoteNames.setOnCheckedChangeListener { _, isChecked ->
            pianoView.showNoteNames = isChecked
            session.setShowNoteNames(isChecked)
        }

        // Piano key callbacks
        pianoView.onKeyPressed = { note, velocity ->
            currentlyHeldNotes.add(note)  // Tracker la note tenue
            playNote(note, velocity)

            // Ajouter note montante en mode libre
            if (isFreeMode) {
                fallingNotesView.addRisingNote(note, velocity)
            }

            handleUserNoteOn(note, velocity)
        }

        pianoView.onKeyReleased = { note ->
            currentlyHeldNotes.remove(note)  // Retirer la note
            stopNote(note)

            // Relâcher note montante en mode libre
            if (isFreeMode) {
                fallingNotesView.releaseRisingNote(note)
            }

            handleUserNoteOff(note)
        }

        // Falling notes hit zone callback
        fallingNotesView.onNoteReachHitZone = onNoteReachHitZone@{ note, timeMs ->
            // Ignore in free mode - no expected notes
            if (isFreeMode) return@onNoteReachHitZone

            pianoView.addExpectedNote(note)

            // Mode attente : pause IMMÉDIATE quand une note atteint la zone
            if (session.waitModeEnabled) {
                // Toujours ajouter la note (même si déjà en wait mode, pour les accords)
                waitingNotesWithTiming.add(TimedNote(note, timeMs))

                if (!isInWaitMode) {
                    // Première note - entrer en mode attente
                    enterWaitModeImmediate()
                } else {
                    // Déjà en mode attente - ajouter au groupe si c'est un accord
                    addNoteToCurrentChordIfApplicable(note, timeMs)
                }
            }
        }

        fallingNotesView.onNoteLeaveHitZone = onNoteLeaveHitZone@{ note ->
            // Ignore in free mode
            if (isFreeMode) return@onNoteLeaveHitZone
            pianoView.removeExpectedNote(note)
        }

        // Playback callbacks
        playbackController.onPlaybackStarted = {
            activity?.runOnUiThread {
                if (isPracticeRecordingEnabled) {
                    resyncPracticeRecordingClock(playbackController.getCurrentPositionMs())
                    stopPracticeRecordingUpdates()
                }
                btnPlayPause.setImageResource(R.drawable.ic_pause)
                startAnimation()
            }
        }

        playbackController.onPlaybackCompleted = {
            activity?.runOnUiThread {
                handlePlaybackCompleted()
            }
        }

        // Afficher uniquement les notes du/des canal(aux) cible(s) sur le clavier (pas les accompagnements)
        playbackController.onNoteOn = { channel, note, velocity ->
            val isTargetChannel = if (session.args.isTwoHandsMode) {
                channel == session.args.leftHandChannel || channel == session.args.rightHandChannel
            } else {
                channel == session.args.channelNumber
            }
            // Filtrer par main en mode deux mains
            val shouldShowForHand = shouldSendLedForChannel(channel)
            if (isTargetChannel && shouldShowForHand) {
                activity?.runOnUiThread {
                    pianoView.noteOn(channel, note, velocity)
                }
                // Send to external keyboard LEDs
                sendNoteToKeyboardLed(note, velocity)
            }
        }

        playbackController.onNoteOff = { channel, note ->
            val isTargetChannel = if (session.args.isTwoHandsMode) {
                channel == session.args.leftHandChannel || channel == session.args.rightHandChannel
            } else {
                channel == session.args.channelNumber
            }
            // Filtrer par main en mode deux mains
            val shouldShowForHand = shouldSendLedForChannel(channel)
            if (isTargetChannel && shouldShowForHand) {
                activity?.runOnUiThread {
                    pianoView.noteOff(channel, note)
                }
                // Turn off LED on external keyboard
                sendNoteOffToKeyboardLed(note)
            }
        }

        // Callback pour eteindre toutes les notes (pause, stop, seek)
        playbackController.onAllNotesOff = {
            activity?.runOnUiThread {
                pianoView.allNotesOff()
                pianoView.allExternalNotesOff()
                pianoView.clearExpectedNotes()
            }
            // Also turn off LEDs on external keyboard
            if (isMidiKeyboardConnected) {
                midiInputController?.sendAllNotesOff()
            }
        }
    }

    // ===================== MAIN PLAYBACK SERVICE =====================

    /**
     * Pause the main MidiPlaybackService when entering training mode.
     * The service stays ready to resume when user goes back to Now Playing.
     */
    private fun pauseMainPlayback() {
        try {
            activity?.let { act ->
                MediaControllerCompat.getMediaController(act)?.transportControls?.pause()
                android.util.Log.d(TAG, "Paused main playback service")
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not pause main playback: ${e.message}")
        }
    }

    // ===================== EXTERNAL MIDI KEYBOARD =====================

    /**
     * Setup external MIDI keyboard controller with callbacks
     */
    private fun setupMidiKeyboard() {
        midiInputController = MidiInputController(
            context = requireContext(),
            onMidiBytesReceived = { bytes ->
                handleExternalMidiBytes(bytes)
            },
            onStatusChanged = { status ->
                activity?.runOnUiThread {
                    handleMidiKeyboardStatus(status)
                }
            }
        )

        // Setup keyboard button click to show settings dialog
        btnMidiKeyboard.setOnClickListener {
            showMidiKeyboardSettingsDialog()
        }
    }

    /**
     * Apply mute state to the external MIDI keyboard's internal sound generator.
     *
     * IMPORTANT: This controls the KEYBOARD's internal sounds, NOT the app's synthesizer.
     * - When muteKeyboardSound=true: The keyboard won't play its own sounds, only send MIDI.
     *   The app's synthesizer will play the notes instead.
     * - When muteKeyboardSound=false: The keyboard plays its own sounds.
     *   The app's synthesizer can also play (resulting in doubled sound if both play).
     *
     * This sends MIDI CC messages TO the keyboard via its input port:
     * - Local Control Off (CC 122=0): Tells keyboard to not play notes locally
     * - Volume/Expression=0: Backup mute for keyboards that don't support Local Control
     */
    @Suppress("unused")
    private fun applyKeyboardMuteState(reason: String) {
        android.util.Log.i(
            TAG,
            "applyKeyboardMuteState: reason=$reason muteKeyboardSound=$muteKeyboardSound " +
                "connected=$isMidiKeyboardConnected hasInputPort=${midiInputController?.isKeyboardConnected()}"
        )

        if (!isMidiKeyboardConnected) {
            android.util.Log.d(TAG, "applyKeyboardMuteState: no keyboard connected, skipping")
            return
        }

        if (muteKeyboardSound) {
            // Mute the keyboard's internal sound generator
            // Notes from the keyboard will be played by the app's synthesizer instead
            android.util.Log.i(TAG, "applyKeyboardMuteState: MUTING keyboard internal sound (LocalControlOff + VolumeMute)")
            midiInputController?.sendLocalControlOff()
            midiInputController?.sendAllChannelsMute()
            android.util.Log.d(TAG, "applyKeyboardMuteState: LocalControlOff sent, VolumeMute sent")
        } else {
            // Enable the keyboard's internal sound generator
            // Notes will be played by both the keyboard AND the app's synthesizer (if enabled)
            android.util.Log.i(TAG, "applyKeyboardMuteState: ENABLING keyboard internal sound (LocalControlOn + VolumeRestore)")
            midiInputController?.sendLocalControlOn()
            midiInputController?.sendAllChannelsUnmute()
            android.util.Log.d(TAG, "applyKeyboardMuteState: LocalControlOn sent, VolumeRestore sent")
        }
    }

    private fun logAudioOutputs(reason: String) {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .joinToString { device ->
                val typeLabel = when (device.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT_A2DP"
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT_SCO"
                    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
                    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
                    AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB_ACCESSORY"
                    else -> "type=${device.type}"
                }
                "$typeLabel(id=${device.id})"
            }
        android.util.Log.d(TAG, "logAudioOutputs[$reason]: $outputs")
    }

    private fun hasConnectedUsbMidiDevice(): Boolean {
        val midiManager = context?.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return false
        @Suppress("DEPRECATION")
        return midiManager.devices?.any { it.type == MidiDeviceInfo.TYPE_USB } ?: false
    }

    private fun configurePracticeAudioRouting(reason: String) {
        val usbMidiConnected = hasConnectedUsbMidiDevice()
        android.util.Log.d(TAG, "configurePracticeAudioRouting: reason=$reason usbMidiConnected=$usbMidiConnected synth=${synthesizer?.getName()}")
        if (!usbMidiConnected) return

        logAudioOutputs("routing_before_$reason")
        // Only configure for Sonivox - SF2 already has forceSpeaker=true in AudioRenderer constructor
        // Double configuration can cause timing issues when keyboard is connected at init time
        val routedToSpeaker = when (val synth = synthesizer) {
            is SonivoxPracticeSynth -> synth.forceOutputToSpeaker()
            is Sf2PracticeSynth -> {
                // SF2 handles speaker routing via forceSpeaker=true in AudioRenderer
                // No need to call forceOutputToSpeaker() again - it can cause conflicts
                android.util.Log.d(TAG, "configurePracticeAudioRouting: SF2 already uses forceSpeaker=true, skipping")
                true
            }
            is FluidSynthPracticeSynth -> {
                // FluidSynth uses Oboe which handles audio routing automatically
                android.util.Log.d(TAG, "configurePracticeAudioRouting: FluidSynth uses Oboe, routing handled internally")
                synth.forceOutputToSpeaker(reason)
            }
            is HybridPracticeSynth -> {
                synth.forceOutputToSpeaker(reason)
            }
            else -> false
        }
        android.util.Log.d(TAG, "configurePracticeAudioRouting: routedToSpeaker=$routedToSpeaker")
        logAudioOutputs("routing_after_$reason")
    }

    @Suppress("unused")
    private fun resetPracticeAudioRouting(reason: String) {
        val usbMidiConnected = hasConnectedUsbMidiDevice()
        if (usbMidiConnected) return

        val resetResult = when (val synth = synthesizer) {
            is SonivoxPracticeSynth -> synth.resetOutputDevice()
            is Sf2PracticeSynth -> synth.resetPreferredOutput(reason)
            is FluidSynthPracticeSynth -> synth.resetPreferredOutput(reason)
            is HybridPracticeSynth -> synth.resetPreferredOutput(reason)
            else -> false
        }
        android.util.Log.d(TAG, "resetPracticeAudioRouting: reason=$reason result=$resetResult")
    }

    private fun handleMidiKeyboardStatus(status: MidiConnectionStatus) {
        android.util.Log.i(TAG, "handleMidiKeyboardStatus: status=$status")

        when (status) {
            MidiConnectionStatus.DEVICE_DETECTED -> {
                isMidiKeyboardConnected = true
                btnMidiKeyboard.visibility = View.VISIBLE
                btnMidiKeyboard.setColorFilter(getThemeAccentColor())

                // Show the keyboard LEDs button, mute LED sound button, and mute internal synth button
                btnKeyboardLeds.visibility = View.VISIBLE
                btnMuteLedSound.visibility = View.VISIBLE
                btnMuteInternalSynth.visibility = View.VISIBLE
                updateKeyboardLedsButtonState()
                updateMuteLedSoundButtonState()
                updateMuteInternalSynthButtonState()

                // Log keyboard info
                val keyboardInfo = midiInputController?.getConnectedKeyboardInfo()
                android.util.Log.i(TAG, "handleMidiKeyboardStatus: keyboard connected - " +
                    "name='${keyboardInfo?.name}' manufacturer='${keyboardInfo?.manufacturer}' " +
                    "inputPorts=${keyboardInfo?.inputPortCount} outputPorts=${keyboardInfo?.outputPortCount}")

                logAudioOutputs("midi_device_detected")

                // When USB MIDI keyboard is connected (after user accepts permission),
                // Android may route audio to the USB device. We need to force audio
                // back to the tablet speakers for Sonivox to be heard.
                android.util.Log.i(TAG, "handleMidiKeyboardStatus: keyboard connected, configuring audio routing")
                configurePracticeAudioRouting("device_detected")
            }
            MidiConnectionStatus.NO_DEVICE, MidiConnectionStatus.DEVICE_UNAUTHORIZED -> {
                val wasConnected = isMidiKeyboardConnected
                isMidiKeyboardConnected = false
                btnMidiKeyboard.visibility = View.GONE
                btnKeyboardLeds.visibility = View.GONE
                btnMuteLedSound.visibility = View.GONE
                btnMuteInternalSynth.visibility = View.GONE

                android.util.Log.i(TAG, "handleMidiKeyboardStatus: keyboard disconnected, wasConnected=$wasConnected")

                // Don't reset audio routing - let the system handle it naturally
            }
        }
    }

    /**
     * Handle MIDI bytes received from external keyboard
     */
    private fun handleExternalMidiBytes(bytes: ByteArray) {
        if (bytes.size < 3) return

        val status = bytes[0].toInt() and 0xFF
        val note = bytes[1].toInt() and 0x7F
        val velocity = bytes[2].toInt() and 0x7F

        when (status and 0xF0) {
            0x90 -> { // NOTE_ON
                if (velocity > 0) {
                    handleExternalNoteOn(note, velocity)
                } else {
                    handleExternalNoteOff(note)
                }
            }
            0x80 -> { // NOTE_OFF
                handleExternalNoteOff(note)
            }
        }
    }

    /**
     * Handle note on from external MIDI keyboard
     * Note: Cette fonction est appelée depuis un thread MIDI, donc on doit
     * wrapper les opérations UI dans runOnUiThread
     *
     * Audio routing logic:
     * - If muteKeyboardSound=true: Keyboard is muted, app synth plays the note
     * - If muteKeyboardSound=false: Keyboard plays its own sound, app synth doesn't play
     *   (to avoid double sound)
     */
    private fun handleExternalNoteOn(note: Int, velocity: Int) {
        // Tracker la note tenue (thread-safe car c'est juste un ajout)
        currentlyHeldNotes.add(note)

        // Play via app synth only if keyboard is muted (to avoid double sound)
        val playViaAppSynth = muteKeyboardSound
        if (playViaAppSynth) {
            synthesizer?.noteOn(session.args.channelNumber, note, velocity)
        }

        // Log for debugging (only occasionally to avoid spam)
        if (note % 12 == 0) { // Log every C note
            android.util.Log.d(TAG, "handleExternalNoteOn: note=$note vel=$velocity " +
                "muteKeyboard=$muteKeyboardSound playViaAppSynth=$playViaAppSynth " +
                "synthReady=${synthesizer?.isReady()}")
        }

        // Toutes les opérations UI doivent être sur le UI thread
        activity?.runOnUiThread {
            // Display on piano UI with glow effect
            pianoView.externalNoteOn(0, note, velocity)

            // Ajouter note montante en mode libre
            if (isFreeMode) {
                fallingNotesView.addRisingNote(note, velocity)
            }

            // Validation logic (same as touch piano)
            handleUserNoteOn(note, velocity)
        }
    }

    /**
     * Handle note off from external MIDI keyboard
     * Note: Cette fonction est appelée depuis un thread MIDI
     */
    private fun handleExternalNoteOff(note: Int) {
        // Retirer la note (thread-safe)
        currentlyHeldNotes.remove(note)

        // Couper le son si notre synthé jouait
        if (muteKeyboardSound) {
            synthesizer?.noteOff(session.args.channelNumber, note)
        }

        // Opérations UI sur le UI thread
        activity?.runOnUiThread {
            pianoView.externalNoteOff(note)

            // Relâcher note montante en mode libre
            if (isFreeMode) {
                fallingNotesView.releaseRisingNote(note)
            }

            handleUserNoteOff(note)
        }
    }

    /**
     * Show MIDI keyboard settings dialog
     */
    private fun showMidiKeyboardSettingsDialog() {
        val keyboardInfo = midiInputController?.getConnectedKeyboardInfo() ?: return

        android.util.Log.d(TAG, "showMidiKeyboardSettingsDialog: ledOutputChannel=$ledOutputChannel ledOctaveOffset=$ledOctaveOffset")

        val dialog = MidiKeyboardSettingsDialog.newInstance(
            keyboardInfo = keyboardInfo,
            currentLedChannel = ledOutputChannel,
            currentOctaveOffset = ledOctaveOffset
        )
        dialog.show(childFragmentManager, "midi_keyboard_settings")
    }

    // MidiKeyboardSettingsDialog.OnSettingsChangedListener implementation
    override fun onLedChannelChanged(channel: Int) {
        android.util.Log.d(TAG, "onLedChannelChanged: $channel (was $ledOutputChannel)")
        ledOutputChannel = channel
    }

    override fun onOctaveOffsetChanged(offset: Int) {
        android.util.Log.d(TAG, "onOctaveOffsetChanged: $offset (was $ledOctaveOffset)")
        ledOctaveOffset = offset
    }

    /**
     * Apply octave offset to a note for LED output.
     * Clamps the result to valid MIDI note range (0-127).
     */
    private fun applyLedOctaveOffset(note: Int): Int {
        val shifted = note + (ledOctaveOffset * 12)  // 12 semitones per octave
        return shifted.coerceIn(0, 127)
    }

    /**
     * Vérifie si les LEDs doivent être envoyées pour ce canal selon le mode de pratique des mains
     */
    private fun shouldSendLedForChannel(channel: Int): Boolean {
        // Si pas en mode deux mains, toujours envoyer
        if (!session.args.isTwoHandsMode) return true

        // Filtrer selon le mode de pratique
        return when (session.handPracticeMode) {
            HandPracticeMode.BOTH_HANDS -> true
            HandPracticeMode.LEFT_HAND_ONLY -> channel == session.args.leftHandChannel
            HandPracticeMode.RIGHT_HAND_ONLY -> channel == session.args.rightHandChannel
        }
    }

    /**
     * Send note to external keyboard LEDs (for current track only, not accompaniment)
     * If muteLedChannelSound is true, mute the channel first so we get light without sound.
     */
    private fun sendNoteToKeyboardLed(note: Int, velocity: Int) {
        if (isMidiKeyboardConnected && sendLedsToKeyboard) {
            val shiftedNote = applyLedOctaveOffset(note)

            // Mute the channel if needed (CC7=Volume, CC11=Expression)
            if (muteLedChannelSound) {
                midiInputController?.sendControlChange(ledOutputChannel, 7, 0)   // Volume = 0
                midiInputController?.sendControlChange(ledOutputChannel, 11, 0)  // Expression = 0
            }

            midiInputController?.sendNoteOn(shiftedNote, velocity, ledOutputChannel)
        }
    }

    /**
     * Turn off note LED on external keyboard
     */
    private fun sendNoteOffToKeyboardLed(note: Int) {
        if (isMidiKeyboardConnected && sendLedsToKeyboard) {
            val shiftedNote = applyLedOctaveOffset(note)
            midiInputController?.sendNoteOff(shiftedNote, ledOutputChannel)
        }
    }

    private fun initSynthesizer() {
        try {
            val settingsRepository = SettingsRepository(requireContext())
            val synthMode = runBlocking { settingsRepository.getSynthMode() }
            val soundFontPath = runBlocking { settingsRepository.getSoundFontPath() }

            when (synthMode) {
                SettingsRepository.SYNTH_MODE_SONIVOX -> {
                    // Sonivox mode - use Sonivox directly
                    synthesizer = SonivoxPracticeSynth(requireContext()).also { it.initialize() }
                }

                SettingsRepository.SYNTH_MODE_SF2 -> {
                    // SF2 mode - use SF2 only, NO fallback to Sonivox
                    if (!soundFontPath.isNullOrBlank() && File(soundFontPath).exists()) {
                        val sf2Synth = Sf2PracticeSynth(requireContext(), soundFontPath)
                        if (sf2Synth.initialize()) {
                            synthesizer = sf2Synth
                        } else {
                            // SF2 failed - show error, no fallback
                            showSf2Error()
                            synthesizer = null
                        }
                    } else {
                        // No SF2 configured but SF2 mode selected - show error
                        showSf2Error()
                        synthesizer = null
                    }
                }

                SettingsRepository.SYNTH_MODE_HYBRID -> {
                    // True hybrid mode: SoundFont engine + Sonivox running simultaneously
                    // Routes per-channel based on configured sf2Programs
                    val sf2Programs = runBlocking { settingsRepository.getHybridSf2Programs() }
                    val useSf2ForDrums = runBlocking { settingsRepository.isHybridUseSf2ForDrums() }
                    val baseEngine = runBlocking { settingsRepository.getHybridBaseEngine() }

                    if (!soundFontPath.isNullOrBlank() && File(soundFontPath).exists()) {
                        // Create the SoundFont synth based on user preference
                        val sfSynth: PracticeSynthesizer? = when (baseEngine) {
                            SettingsRepository.SYNTH_MODE_FLUIDSYNTH -> {
                                if (FluidSynthEngine.isSupported()) {
                                    FluidSynthPracticeSynth(requireContext(), soundFontPath)
                                } else {
                                    // FluidSynth not available, try SF2
                                    Sf2PracticeSynth(requireContext(), soundFontPath)
                                }
                            }
                            else -> Sf2PracticeSynth(requireContext(), soundFontPath)
                        }

                        if (sfSynth != null) {
                            val sonivox = SonivoxPracticeSynth(requireContext())
                            val hybrid = HybridPracticeSynth(sfSynth, sonivox, sf2Programs, useSf2ForDrums)
                            if (hybrid.initialize()) {
                                synthesizer = hybrid
                            } else {
                                // Hybrid failed - fallback to Sonivox only
                                synthesizer = SonivoxPracticeSynth(requireContext()).also { it.initialize() }
                            }
                        } else {
                            synthesizer = SonivoxPracticeSynth(requireContext()).also { it.initialize() }
                        }
                    } else {
                        // No SoundFont configured - use Sonivox only
                        synthesizer = SonivoxPracticeSynth(requireContext()).also { it.initialize() }
                    }
                }

                SettingsRepository.SYNTH_MODE_FLUIDSYNTH -> {
                    // FluidSynth mode - use FluidSynth only, NO fallback to Sonivox
                    if (!soundFontPath.isNullOrBlank() && File(soundFontPath).exists()) {
                        val fluidSynth = FluidSynthPracticeSynth(requireContext(), soundFontPath)
                        if (fluidSynth.initialize()) {
                            synthesizer = fluidSynth
                        } else {
                            showSf2Error()
                            synthesizer = null
                        }
                    } else {
                        showSf2Error()
                        synthesizer = null
                    }
                }

                else -> {
                    // Unknown mode - default to Sonivox
                    synthesizer = SonivoxPracticeSynth(requireContext()).also { it.initialize() }
                }
            }
        } catch (_: Exception) {
            // Critical error - try Sonivox as last resort
            synthesizer = SonivoxPracticeSynth(requireContext()).also { it.initialize() }
        }
    }

    private fun showSf2Error() {
        activity?.runOnUiThread {
            Toast.makeText(
                requireContext(),
                getString(R.string.practice_sf2_load_error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupDisplay(args: PracticeArgs) {
        txtTrackTitle.text = args.trackTitle
        txtInstrument.text = args.instrumentName

        // Show Free Mode button in MIDI practice mode
        btnFreeMode.visibility = View.VISIBLE

        // Set note range and program for color lookup
        pianoView.setNoteRange(args.noteRangeMin, args.noteRangeMax)
        pianoView.targetChannel = args.channelNumber
        pianoView.setCurrentProgram(args.programNumber)

        fallingNotesView.setNoteRange(args.noteRangeMin, args.noteRangeMax)
        fallingNotesView.setTargetChannel(args.channelNumber)
        fallingNotesView.setCurrentProgram(args.programNumber)
        fallingNotesView.setHitZoneToleranceMs(session.timingToleranceMs)
        // Connecter l'horloge partagée pour synchronisation audio/visuel
        fallingNotesView.setSharedClock(playbackController.sharedClock)

        // Initialiser le gestionnaire de thèmes et appliquer le thème actuel
        PracticeThemeManager.init(requireContext())
        applyCurrentThemeToAllViews()

        // Configure sheet music view
        sheetMusicView.setTargetChannel(args.channelNumber)
        sheetMusicView.setCurrentProgram(args.programNumber)
        // Connecter l'horloge partagée pour synchronisation audio/visuel
        sheetMusicView.setSharedClock(playbackController.sharedClock)

        // Initialize time display (temps restant en format négatif)
        txtTimeCurrent.text = getString(R.string.practice_time_zero)
        txtTimeTotal.text = getString(R.string.practice_time_remaining, getString(R.string.practice_time_zero))
        seekbarPosition.progress = 0
        markerSeekBar.setProgress(0f)

        // Send program change to set the correct instrument for this channel
        // En mode deux mains, configurer les deux canaux avec le son de piano
        if (args.isTwoHandsMode) {
            synthesizer?.programChange(args.leftHandChannel, args.programNumber)
            synthesizer?.programChange(args.rightHandChannel, args.programNumber)
        } else {
            synthesizer?.programChange(args.channelNumber, args.programNumber)
        }
    }

    private fun loadMidiFile(args: PracticeArgs) {
        android.util.Log.d(
            TAG,
            "loadMidiFile: Loading file=${args.trackFilePath}, channel=${args.channelNumber}, twoHands=${args.isTwoHandsMode}"
        )

        // Réinitialiser le mode de son des mains (les deux mains ont du son par défaut)
        handSoundMode = HandPracticeMode.BOTH_HANDS
        playbackController.muteLeftHandChannel = false
        playbackController.muteRightHandChannel = false

        val loadSuccess: Boolean

        // Verifier si c'est un Content URI ou un chemin de fichier
        if (args.trackFilePath.startsWith("content://")) {
            // Content URI - utiliser ContentResolver
            val uri = args.trackFilePath.toUri()
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    loadSuccess = inputStream.use {
                        if (args.isTwoHandsMode) {
                            playbackController.loadFromInputStreamTwoHands(
                                it, args.leftHandChannel, args.rightHandChannel
                            )
                        } else {
                            playbackController.loadFromInputStream(it, args.channelNumber)
                        }
                    }
                } else {
                    showError("Impossible d'ouvrir le fichier MIDI")
                    return
                }
            } catch (_: Exception) {
                showError("Erreur lors de l'ouverture du fichier MIDI")
                return
            }
        } else {
            // Chemin de fichier normal
            val file = File(args.trackFilePath)
            if (!file.exists()) {
                showError("Fichier MIDI introuvable")
                return
            }
            loadSuccess = if (args.isTwoHandsMode) {
                playbackController.loadFileTwoHands(file, args.leftHandChannel, args.rightHandChannel)
            } else {
                playbackController.loadFile(file, args.channelNumber)
            }
        }

        if (loadSuccess) {
            val notes = playbackController.getTargetChannelNotes()
            // S'assurer que le mode est FALLING pour la lecture MIDI
            fallingNotesView.setNoteDirection(FallingNotesView.NoteDirection.FALLING)
            fallingNotesView.setNotes(notes)

            // En mode deux mains, configurer FallingNotesView pour distinguer les mains
            if (args.isTwoHandsMode) {
                fallingNotesView.setTwoHandsMode(true, args.leftHandChannel, args.rightHandChannel)
                // Afficher les boutons de selection de main (LED) et de son
                handSelectionContainer.visibility = View.VISIBLE
                handSoundContainer.visibility = View.VISIBLE
                updateHandSelectionButtonStates()
                updateHandSoundButtonStates()
            } else {
                handSelectionContainer.visibility = View.GONE
                handSoundContainer.visibility = View.GONE
            }

            sheetMusicView.setNotes(notes)  // Also set notes on sheet music view
            session.setTotalNotes(notes.size)

            // Initialize scoring with total expected notes
            scoringObserver.setTotalExpectedNotes(notes.size)
            scoreDisplayView.reset()

            // Reset practice recording timeline for the newly loaded score
            clearPracticeRecordingNotes()
            sheetMusicView.setRecordingModeEnabled(isPracticeRecordingEnabled)
            resyncPracticeRecordingClock(playbackController.getCurrentPositionMs())
            startPracticeRecordingUpdatesIfNeeded()

            // Utiliser le range reel calcule depuis les notes chargees
            playbackController.getActualNoteRange()?.let { (actualMin, actualMax) ->
                pianoView.setNoteRange(actualMin, actualMax)
                fallingNotesView.setNoteRange(actualMin, actualMax)
            }

            // Update total time display and configure markerSeekBar
            val durationMs = playbackController.getDurationMs()
            txtTimeTotal.text = getString(R.string.practice_time_remaining, formatTime(durationMs))
            markerSeekBar.setMaxDuration(durationMs)
            markerSeekBar.setProgress(0f)
        } else {
            showError("Erreur lors du chargement du fichier MIDI")
        }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun togglePlayPause() {
        if (playbackController.isPlaying()) {
            stopPracticeRecordingUpdates()
            playbackController.pause()  // Le callback onAllNotesOff sera appele automatiquement
            btnPlayPause.setImageResource(R.drawable.ic_play)
            stopAnimation()
        } else if (countdownJob != null || isWaitingForFirstNote) {
            // Cancel countdown or waiting mode
            countdownJob?.cancel()
            countdownJob = null
            isWaitingForFirstNote = false
            btnPlayPause.setImageResource(R.drawable.ic_play)
            Toast.makeText(requireContext(), R.string.practice_countdown_cancelled, Toast.LENGTH_SHORT).show()
        } else {
            // Use countdown/start-on-first-note if configured
            startPlaybackWithCountdown()
        }
    }

    private fun restart() {
        resetWaitMode()  // Réinitialiser le mode attente
        session.clearActiveHeldNotes()  // Effacer le tracking des notes maintenues
        playbackController.restart()  // Le callback onAllNotesOff sera appele automatiquement
        fallingNotesView.reset()
        sheetMusicView.reset()
        sheetMusicView.setRecordingModeEnabled(isPracticeRecordingEnabled)
        sheetMusicView.setRecordedNotes(recordedNotes)

        // Reset position displays
        markerSeekBar.setProgress(0f)
        updatePositionDisplay(0L)
        handlePracticeRecordingSeek(playbackController.getCurrentPositionMs())

        if (playbackController.isPlaying()) {
            btnPlayPause.setImageResource(R.drawable.ic_pause)
            startAnimation()
        }
    }

    private fun stop() {
        resetWaitMode()  // Réinitialiser le mode attente
        session.clearActiveHeldNotes()  // Effacer le tracking des notes maintenues
        playbackController.stop()  // Le callback onAllNotesOff sera appele automatiquement
        btnPlayPause.setImageResource(R.drawable.ic_play)
        stopAnimation()
        fallingNotesView.reset()
        sheetMusicView.reset()
        sheetMusicView.setRecordingModeEnabled(isPracticeRecordingEnabled)
        sheetMusicView.setRecordedNotes(recordedNotes)

        // Reset position displays and clear markers
        markerSeekBar.setProgress(0f)
        markerSeekBar.clearMarkers()
        updatePositionDisplay(0L)
        handlePracticeRecordingSeek(0L)

        // Show results dialog if there was any scoring activity
        showPracticeResultsIfNeeded()
    }

    /**
     * Called when playback reaches the end of the track naturally.
     * Shows results dialog and resets for next play.
     */
    private fun handlePlaybackCompleted() {
        btnPlayPause.setImageResource(R.drawable.ic_play)
        stopAnimation()
        if (isPracticeRecordingEnabled) {
            resyncPracticeRecordingClock(playbackController.getCurrentPositionMs())
            startPracticeRecordingUpdatesIfNeeded()
        }

        // Show results dialog
        showPracticeResultsIfNeeded()
    }

    /**
     * Shows the practice results dialog if there was scoring activity.
     * Saves the session to database and resets scoring after the dialog is dismissed.
     */
    private fun showPracticeResultsIfNeeded() {
        // Only show results in MIDI mode (not free mode) and if user played some notes
        if (!isFreeMode && scoringObserver.metrics.goodNotes > 0) {
            scoringObserver.onSessionComplete()

            val trackTitle = session.args.trackTitle
            val finalMetrics = scoringObserver.metrics

            // Save session to database
            savePracticeSessionToDatabase(finalMetrics)

            // Show results dialog
            context?.let { ctx ->
                PracticeResultsDialog(
                    context = ctx,
                    metrics = finalMetrics,
                    trackTitle = trackTitle,
                    onDismiss = {
                        // Reset scoring for next session
                        resetScoringForNextSession()
                    }
                ).show()
            } ?: run {
                // If no context, just reset
                resetScoringForNextSession()
            }
        } else {
            // No results to show, just reset
            resetScoringForNextSession()
        }
    }

    /**
     * Saves the completed practice session to the Room database for history tracking.
     */
    private fun savePracticeSessionToDatabase(metrics: com.Atom2Universe.app.midi.practice.scoring.ScoringMetrics) {
        val ctx = context ?: return

        val sessionResult = com.Atom2Universe.app.midi.data.PracticeSessionResult(
            trackFilePath = session.args.trackFilePath,
            trackTitle = session.args.trackTitle,
            channelNumber = session.args.channelNumber,
            instrumentName = session.args.instrumentName,
            grade = metrics.grade,
            score = metrics.score,
            accuracy = metrics.accuracy,
            perfectNotes = metrics.perfectNotes,
            goodNotes = metrics.goodNotes,
            missedNotes = metrics.missedNotes,
            wrongNotes = metrics.wrongNotes,
            bestStreak = metrics.bestStreak,
            totalExpectedNotes = metrics.totalExpectedNotes,
            maxComboReached = metrics.maxComboReached,
            playbackSpeed = speedSteps[seekbarSpeed.progress],
            sessionDurationMs = System.currentTimeMillis() - metrics.sessionStartTimeMs
        )

        lifecycleScope.launch {
            try {
                val database = com.Atom2Universe.app.midi.data.MidiDatabase.getInstance(ctx)
                database.practiceSessionDao().insert(sessionResult)
                android.util.Log.d(TAG, "Practice session saved: grade=${metrics.grade}, score=${metrics.score}")

                // Sauvegarder également dans les statistiques globales
                // On crée une session avec les timestamps basés sur la durée de la session de practice
                val sessionStartTime = metrics.sessionStartTimeMs
                val sessionEndTime = System.currentTimeMillis()
                val midiFileName = session.args.trackFilePath.substringAfterLast("/")

                // Créer une session de stats pour cette practice session
                val statsSession = com.Atom2Universe.app.stats.data.UsageSessionEntity(
                    moduleType = com.Atom2Universe.app.stats.data.StatsRepository.MODULE_MIDI,
                    startTimestamp = sessionStartTime,
                    endTimestamp = sessionEndTime,
                    durationMs = sessionEndTime - sessionStartTime,
                    midiFileName = midiFileName,
                    practiceScore = metrics.score.toFloat()
                )

                // Sauvegarder dans la base de données des stats
                val statsRepo = com.Atom2Universe.app.stats.data.StatsRepository(ctx)
                statsRepo.insertSession(statsSession)

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to save practice session", e)
            }
        }
    }

    /**
     * Resets the scoring system for the next practice session.
     */
    private fun resetScoringForNextSession() {
        val totalNotes = playbackController.getTargetChannelNotes().size
        scoringObserver.reset(totalNotes)
        scoreDisplayView.reset()
    }

    private fun startAnimation() {
        if (!isAnimating) {
            isAnimating = true
            // Démarrer l'animation fluide des notes tombantes (Choreographer)
            val currentSpeed = speedSteps[seekbarSpeed.progress]
            val position = playbackController.getCurrentPositionMs()
            fallingNotesView.startAnimation(position, currentSpeed)

            // Start sheet music animation if visible
            if (isSheetMusicVisible) {
                sheetMusicView.startAnimation(position, currentSpeed)
            }

            // Démarrer la mise à jour UI (slider, expected notes)
            handler.post(positionUpdateRunnable)
        }
    }

    private fun stopAnimation() {
        isAnimating = false
        fallingNotesView.stopAnimation()
        sheetMusicView.stopAnimation()
        handler.removeCallbacks(positionUpdateRunnable)

        if (isPracticeRecordingEnabled) {
            pausePracticeRecordingClock()
            if (isSheetMusicVisible) {
                sheetMusicView.updatePosition(getPracticeRecordingPositionMs())
            }
            startPracticeRecordingUpdatesIfNeeded()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun updateExpectedNotes(positionMs: Long) {
        // Get notes that should be in the hit zone
        val expectedNotes = fallingNotesView.getExpectedNotesWindow()
        pianoView.setExpectedNotes(expectedNotes.map { it.note }.toSet())
    }

    private fun playNote(note: Int, velocity: Int) {
        synthesizer?.noteOn(session.args.channelNumber, note, velocity)
    }

    private fun stopNote(note: Int) {
        synthesizer?.noteOff(session.args.channelNumber, note)
    }

    // ===================== PRACTICE RECORDING =====================

    @Suppress("unused")
    private fun togglePracticeRecording() {
        if (isPracticeRecordingEnabled) {
            stopPracticeRecording()
        } else {
            startPracticeRecording()
        }
    }

    /**
     * Supprime tous les fichiers WAV temporaires du dossier cache/recordings.
     * Appele au demarrage du fragment pour nettoyer les enregistrements
     * orphelins (ex: l'utilisateur a quitte l'app en plein enregistrement).
     */
    private fun cleanupOldRecordings() {
        val ctx = context ?: return
        val recordingsDir = File(ctx.cacheDir, "recordings")
        if (recordingsDir.exists()) {
            val deleted = recordingsDir.listFiles()?.count { file ->
                file.isFile && file.name.endsWith(".wav") && file.delete()
            } ?: 0
            if (deleted > 0) {
                android.util.Log.d(TAG, "cleanupOldRecordings: deleted $deleted temp file(s)")
            }
        }
    }

    /**
     * Toggle live audio recording via the Rec button.
     * Captures the real-time audio output of the synthesizer into a WAV file.
     */
    private fun toggleRecording() {
        if (audioStreamRecorder.isRecording()) {
            stopAudioRecording()
        } else {
            startAudioRecording()
        }
    }

    private fun startAudioRecording() {
        val ctx = context ?: return
        val sf2Synth = synthesizer as? Sf2PracticeSynth
        if (sf2Synth == null) {
            Toast.makeText(ctx, getString(R.string.practice_export_format_wav_disabled), Toast.LENGTH_SHORT).show()
            return
        }

        val cacheDir = File(ctx.cacheDir, "recordings")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val tmpFile = File(cacheDir, "rec_${dateFormat.format(Date())}.wav")

        if (audioStreamRecorder.start(tmpFile)) {
            sf2Synth.setAudioTapCallback(audioStreamRecorder.tapCallback)
            updateRecordButtonState(true)
            recordingHandler.postDelayed(autoStopRecordingRunnable, maxRecordingDurationMs)
            Toast.makeText(ctx, getString(R.string.practice_recording_enabled), Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAudioRecording() {
        recordingHandler.removeCallbacks(autoStopRecordingRunnable)
        val sf2Synth = synthesizer as? Sf2PracticeSynth
        sf2Synth?.setAudioTapCallback(null)

        val wavFile = audioStreamRecorder.stop()
        updateRecordButtonState(false)

        if (wavFile != null && wavFile.length() > 44) {
            showAudioExportDialog(wavFile)
        } else {
            Toast.makeText(requireContext(), getString(R.string.practice_no_notes_recorded), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRecordButtonState(isRecording: Boolean) {
        if (isRecording) {
            btnRecord.setImageResource(R.drawable.ic_record_stop)
            btnRecord.setBackgroundResource(R.drawable.recording_pulse_background)
            btnRecordFreeMode.setImageResource(R.drawable.ic_record_stop)
            btnRecordFreeMode.setBackgroundResource(R.drawable.recording_pulse_background)
            startRecordPulseAnimation()
        } else {
            btnRecord.setImageResource(R.drawable.ic_record)
            btnRecord.setBackgroundResource(R.drawable.circle_button_background)
            btnRecord.clearAnimation()
            btnRecord.alpha = 1f
            btnRecordFreeMode.setImageResource(R.drawable.ic_record)
            btnRecordFreeMode.setBackgroundResource(R.drawable.circle_button_background)
            btnRecordFreeMode.clearAnimation()
            btnRecordFreeMode.alpha = 1f
        }
    }

    private fun startRecordPulseAnimation() {
        val pulseAnim = AlphaAnimation(1f, 0.4f).apply {
            duration = 600
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        btnRecord.startAnimation(pulseAnim)

        // Also animate Free Mode record button
        val pulseAnimFreeMode = AlphaAnimation(1f, 0.4f).apply {
            duration = 600
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        btnRecordFreeMode.startAnimation(pulseAnimFreeMode)
    }

    private fun startPracticeRecording() {
        val hadNotes = recordedNotes.isNotEmpty() || activeRecordedNotes.isNotEmpty()
        stopPracticeRecordingUpdates()
        clearPracticeRecordingNotes()

        isPracticeRecordingEnabled = true
        sheetMusicView.setRecordingModeEnabled(true)
        sheetMusicView.setRecordedNotes(recordedNotes)

        resyncPracticeRecordingClock(playbackController.getCurrentPositionMs())

        if (!isSheetMusicVisible) {
            toggleSheetMusic()
        }

        if (isInWaitMode) {
            pausePracticeRecordingClock()
        } else {
            startPracticeRecordingUpdatesIfNeeded()
        }

        val messageRes = if (hadNotes) {
            R.string.practice_recording_enabled_cleared
        } else {
            R.string.practice_recording_enabled
        }
        Toast.makeText(requireContext(), getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    private fun stopPracticeRecording() {
        if (!isPracticeRecordingEnabled) return

        val position = getPracticeRecordingPositionMs()
        finalizeActiveRecordedNotes(position)

        isPracticeRecordingEnabled = false
        stopPracticeRecordingUpdates()
        sheetMusicView.setRecordingModeEnabled(false)
        sheetMusicView.setRecordedNotes(recordedNotes)

        Toast.makeText(
            requireContext(),
            getString(R.string.practice_recording_disabled),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearPracticeRecordingNotes() {
        recordedNotes.clear()
        activeRecordedNotes.clear()
        sheetMusicView.setRecordedNotes(recordedNotes)
    }

    private fun startPracticeRecordingUpdatesIfNeeded() {
        if (!isPracticeRecordingEnabled || playbackController.isPlaying() || isInWaitMode) return
        if (isPracticeRecordingClockFrozen) {
            recordingStartRealtimeMs = SystemClock.elapsedRealtime()
            isPracticeRecordingClockFrozen = false
        }
        if (isPracticeRecordingUpdatesRunning) return
        isPracticeRecordingUpdatesRunning = true
        handler.removeCallbacks(recordingUpdateRunnable)
        handler.post(recordingUpdateRunnable)
    }

    private fun stopPracticeRecordingUpdates() {
        handler.removeCallbacks(recordingUpdateRunnable)
        isPracticeRecordingUpdatesRunning = false
    }

    private fun resyncPracticeRecordingClock(basePositionMs: Long) {
        recordingStartPositionMs = basePositionMs
        recordingStartRealtimeMs = SystemClock.elapsedRealtime()
        isPracticeRecordingClockFrozen = false
    }

    private fun pausePracticeRecordingClock() {
        if (!isPracticeRecordingEnabled) return
        val frozenPosition = getPracticeRecordingFreezePositionMs()
        recordingStartPositionMs = frozenPosition
        recordingStartRealtimeMs = SystemClock.elapsedRealtime()
        isPracticeRecordingClockFrozen = true
    }

    private fun getPracticeRecordingFreezePositionMs(): Long {
        val playbackPosition = playbackController.getCurrentPositionMs()
        val internalPosition = getPracticeRecordingPositionMs()
        return if (!playbackController.isPlaying() && internalPosition - playbackPosition > 50L) {
            internalPosition
        } else {
            playbackPosition
        }
    }

    private fun getPracticeRecordingPositionMs(): Long {
        if (isPracticeRecordingClockFrozen) return recordingStartPositionMs
        return if (playbackController.isPlaying()) {
            playbackController.getCurrentPositionMs()
        } else {
            if (recordingStartRealtimeMs == 0L) {
                recordingStartRealtimeMs = SystemClock.elapsedRealtime()
                recordingStartPositionMs = playbackController.getCurrentPositionMs()
            }
            val elapsedRealMs = SystemClock.elapsedRealtime() - recordingStartRealtimeMs
            val elapsedAdjustedMs = (elapsedRealMs * playbackController.getSpeed()).toLong()
            recordingStartPositionMs + elapsedAdjustedMs
        }
    }

    private fun handlePracticeRecordingNoteOn(note: Int, velocity: Int) {
        if (!isPracticeRecordingEnabled) return

        val timestampMs = getPracticeRecordingPositionMs()

        // Si la note est déjà active, la finaliser avant de recommencer
        finalizeActiveRecordedNote(note, timestampMs)

        val scheduledNote = ScheduledNote(
            note = note,
            startTimeMs = timestampMs,
            durationMs = MIN_RECORDED_NOTE_DURATION_MS,
            velocity = velocity,
            channel = session.args.channelNumber,
            isLeftHand = false
        )
        recordedNotes.add(scheduledNote)
        activeRecordedNotes[note] = ActiveRecordedNote(timestampMs, recordedNotes.lastIndex)

        sheetMusicView.setRecordedNotes(recordedNotes)
        if (isSheetMusicVisible) {
            sheetMusicView.updatePosition(timestampMs)
        }

        startPracticeRecordingUpdatesIfNeeded()
    }

    private fun handlePracticeRecordingNoteOff(note: Int) {
        if (!isPracticeRecordingEnabled) return
        val timestampMs = getPracticeRecordingPositionMs()
        finalizeActiveRecordedNote(note, timestampMs)
        sheetMusicView.setRecordedNotes(recordedNotes)
        if (isSheetMusicVisible) {
            sheetMusicView.updatePosition(timestampMs)
        }
    }

    private fun updatePracticeRecordingState(currentPositionMs: Long) {
        if (!isPracticeRecordingEnabled || activeRecordedNotes.isEmpty()) return

        var hasChanges = false
        activeRecordedNotes.values.forEach { active ->
            val duration = (currentPositionMs - active.startTimeMs).coerceAtLeast(MIN_RECORDED_NOTE_DURATION_MS)
            val existing = recordedNotes.getOrNull(active.index) ?: return@forEach
            if (duration != existing.durationMs) {
                recordedNotes[active.index] = existing.copy(durationMs = duration)
                hasChanges = true
            }
        }

        if (hasChanges) {
            sheetMusicView.setRecordedNotes(recordedNotes)
        }
    }

    private fun finalizeActiveRecordedNote(note: Int, endPositionMs: Long) {
        val active = activeRecordedNotes.remove(note) ?: return
        val duration = (endPositionMs - active.startTimeMs).coerceAtLeast(MIN_RECORDED_NOTE_DURATION_MS)
        val existing = recordedNotes.getOrNull(active.index) ?: return
        recordedNotes[active.index] = existing.copy(durationMs = duration)
    }

    private fun finalizeActiveRecordedNotes(endPositionMs: Long) {
        if (activeRecordedNotes.isEmpty()) return
        activeRecordedNotes.keys.toList().forEach { note ->
            finalizeActiveRecordedNote(note, endPositionMs)
        }
    }

    private fun handlePracticeRecordingSeek(positionMs: Long) {
        if (!isPracticeRecordingEnabled) return
        stopPracticeRecordingUpdates()
        finalizeActiveRecordedNotes(getPracticeRecordingPositionMs())
        resyncPracticeRecordingClock(positionMs)
        sheetMusicView.setRecordedNotes(recordedNotes)
        if (isSheetMusicVisible) {
            sheetMusicView.updatePosition(positionMs)
        }
        if (isInWaitMode) {
            pausePracticeRecordingClock()
        } else {
            startPracticeRecordingUpdatesIfNeeded()
        }
    }

    private fun handleUserNoteOn(note: Int, velocity: Int) {
        handlePracticeRecordingNoteOn(note, velocity)

        // Check if waiting for first note to start playback
        onUserPlayedNote()

        // In free mode, no validation needed
        if (isFreeMode) return

        // Scoring only active during playback (PLAY state or wait mode)
        // When paused (not in wait mode), user can practice without affecting score
        val isScoringActive = playbackController.isPlaying() || isInWaitMode

        val expectedNotes = fallingNotesView.getExpectedNotesWindow()
        val playbackTimestampMs = playbackController.getCurrentPositionMs()
        val result = session.onUserNoteOn(note, velocity, playbackTimestampMs, expectedNotes)

        // En mode attente : vérifier si l'utilisateur joue une note attendue
        if (isInWaitMode && currentWaitingNoteCounts.containsKey(note)) {
            // Bonne note jouée ! Décrémenter le compteur
            val count = currentWaitingNoteCounts[note] ?: 0
            if (count <= 1) {
                // Dernière occurrence de cette note
                currentWaitingNoteCounts.remove(note)
                pianoView.removeExpectedNote(note)
                // Éteindre la LED sur le clavier externe
                sendNoteOffToKeyboardLed(note)
                // Feedback visuel sur la touche du piano (vert = succès)
                pianoView.showTimingFeedback(note, PracticeSession.TimingJudgement.GOOD)
                // Déclencher effet de particules (succès)
                fallingNotesView.triggerHitEffect(note, true)
                // Notify scoring - wait mode counts as perfect (user took time to play correctly)
                if (isScoringActive) {
                    scoringObserver.onNoteHit(note, 0L, true, velocity)
                }
            } else {
                // Encore des occurrences à jouer
                currentWaitingNoteCounts[note] = count - 1
                // Flash la LED pour indiquer qu'il faut rejouer
                // (on éteint puis rallume pour créer un effet visuel)
                sendNoteOffToKeyboardLed(note)
                sendNoteToKeyboardLed(note, 127)
                // Feedback visuel sur la touche du piano (vert = succès partiel)
                pianoView.showTimingFeedback(note, PracticeSession.TimingJudgement.GOOD)
                // Déclencher effet de particules (succès partiel)
                fallingNotesView.triggerHitEffect(note, true)
                // Notify scoring - wait mode counts as perfect
                if (isScoringActive) {
                    scoringObserver.onNoteHit(note, 0L, true, velocity)
                }
            }

            // Si toutes les notes du groupe sont jouées, sortir du mode attente
            if (currentWaitingNoteCounts.isEmpty()) {
                exitWaitMode()
            }
        } else if (!isInWaitMode && isScoringActive) {
            // Mode normal : vérifier timing (only when scoring is active)
            val isCorrectNote = result.isExpected && result.timing == PracticeSession.TimingJudgement.GOOD
            if (isCorrectNote) {
                pianoView.removeExpectedNote(note)
                // Feedback visuel sur la touche du piano (vert = parfait)
                pianoView.showTimingFeedback(note, PracticeSession.TimingJudgement.GOOD)
                // Déclencher effet de particules (succès)
                fallingNotesView.triggerHitEffect(note, true)

                // Notify scoring observer - good hit
                val timingOffset = result.expectedNote?.let { playbackTimestampMs - it.startTimeMs } ?: 0L
                scoringObserver.onNoteHit(
                    note = note,
                    timingOffsetMs = timingOffset,
                    isPerfect = kotlin.math.abs(timingOffset) < 30, // Perfect = within 30ms
                    velocity = velocity
                )
            } else if (result.isExpected) {
                // Note correcte mais timing mauvais
                // Feedback visuel sur la touche du piano (rouge = mauvais timing)
                pianoView.showTimingFeedback(note, PracticeSession.TimingJudgement.BAD)
                fallingNotesView.triggerHitEffect(note, false)

                // Notify scoring observer - wrong timing (still counts as attempt)
                scoringObserver.onWrongNote(note, playbackTimestampMs)
            } else {
                // Note not expected at this time
                scoringObserver.onWrongNote(note, playbackTimestampMs)
            }
        }
        // Note : si l'utilisateur joue une mauvaise note en mode attente, on l'ignore simplement
        // Note : si paused (not wait mode), pas de feedback visuel négatif pour encourager la pratique libre
    }

    /**
     * Gère le relâchement d'une note par l'utilisateur
     * Valide si la note a été maintenue assez longtemps (50% par défaut)
     */
    private fun handleUserNoteOff(note: Int) {
        handlePracticeRecordingNoteOff(note)

        // In free mode, no validation needed
        if (isFreeMode) return

        // Scoring only active during playback (PLAY state or wait mode)
        val isScoringActive = playbackController.isPlaying() || isInWaitMode

        val currentPositionMs = playbackController.getCurrentPositionMs()
        val result = session.onUserNoteOff(note, currentPositionMs)

        // Si la validation de durée est activée et on a un résultat
        result?.let { releaseResult ->
            // Notify scoring observer about hold result (only when scoring active)
            if (isScoringActive) {
                scoringObserver.onNoteHeld(
                    note = note,
                    heldPercent = releaseResult.holdPercentage,
                    isSuccess = releaseResult.holdJudgement == PracticeSession.HoldJudgement.SUCCESS
                )
            }

            when (releaseResult.holdJudgement) {
                PracticeSession.HoldJudgement.SUCCESS -> {
                    // Note maintenue assez longtemps - feedback positif
                    android.util.Log.d(TAG, "Note $note held successfully: ${(releaseResult.holdPercentage * 100).toInt()}%")
                    // Les particules de succès sont déjà déclenchées au NoteOn
                }
                PracticeSession.HoldJudgement.RELEASED_EARLY -> {
                    // Note relâchée trop tôt - feedback négatif
                    android.util.Log.d(TAG, "Note $note released early: ${(releaseResult.holdPercentage * 100).toInt()}% of ${releaseResult.expectedDurationMs}ms")
                    // Feedback visuel sur la touche du piano (rouge = relâché trop tôt)
                    pianoView.showTimingFeedback(note, PracticeSession.TimingJudgement.BAD)
                    // Effet visuel d'erreur (note relâchée trop tôt)
                    fallingNotesView.triggerHitEffect(note, false)

                    // En mode attente, si la note est relâchée trop tôt sur une note longue,
                    // on pourrait demander à l'utilisateur de rejouer
                    if (isInWaitMode && releaseResult.expectedNote != null &&
                        releaseResult.expectedDurationMs > 200) {  // Seulement pour les notes longues
                        // Remettre la note dans les notes attendues
                        val pitch = releaseResult.expectedNote.note
                        if (!currentWaitingNoteCounts.containsKey(pitch)) {
                            currentWaitingNoteCounts[pitch] = 1
                            pianoView.addExpectedNote(pitch)
                            sendNoteToKeyboardLed(pitch, 127)
                        }
                    }
                }
            }
        }
    }

    // ===================== EXPORT DIALOG =====================

    /**
     * Dialogue d'export pour un enregistrement audio WAV.
     * Propose un nom de fichier et ouvre le folder picker.
     */
    private fun showAudioExportDialog(wavFile: File) {
        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_export_recording, null)
        val editFilename = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_filename)
        val checkboxMidi = dialogView.findViewById<CheckBox>(R.id.checkbox_midi)
        val checkboxWav = dialogView.findViewById<CheckBox>(R.id.checkbox_wav)

        // Masquer les checkboxes (c'est toujours un WAV)
        checkboxMidi.visibility = View.GONE
        checkboxWav.visibility = View.GONE

        // Nom par defaut
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
        editFilename.setText(getString(R.string.practice_recording_filename, dateFormat.format(Date())))

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.practice_export_title)
            .setView(dialogView)
            .setPositiveButton(R.string.practice_export_button, null)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                // Nettoyer le fichier temporaire si annule
                wavFile.delete()
            }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val filename = editFilename.text?.toString()?.trim() ?: return@setOnClickListener
            if (filename.isBlank()) return@setOnClickListener
            dialog.dismiss()

            pendingExportWavFile = wavFile
            pendingExportFilename = filename
            exportFolderLauncher.launch(null)
        }
    }

    /**
     * Appele apres que l'utilisateur a choisi un dossier de destination.
     * Copie le fichier WAV vers le dossier SAF choisi.
     */
    private fun performSaveToFolder(treeUri: Uri) {
        val ctx = context ?: return
        val wavFile = pendingExportWavFile ?: return
        val filename = pendingExportFilename

        lifecycleScope.launch {
            try {
                val destDir = DocumentFile.fromTreeUri(ctx, treeUri)
                if (destDir == null || !destDir.canWrite()) {
                    Toast.makeText(ctx, getString(R.string.practice_export_error, "Cannot write to folder"), Toast.LENGTH_LONG).show()
                    wavFile.delete()  // Nettoyer le fichier temporaire en cas d'erreur
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    copyToDocumentFile(ctx, destDir, wavFile, "$filename.wav")
                    wavFile.delete()
                }

                Toast.makeText(ctx, getString(R.string.practice_export_success, "$filename.wav"), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, getString(R.string.practice_export_error, e.message ?: "unknown"), Toast.LENGTH_LONG).show()
                wavFile.delete()  // Nettoyer le fichier temporaire en cas d'exception
            } finally {
                pendingExportWavFile = null
            }
        }
    }

    /**
     * Copie un fichier local vers un DocumentFile (dossier SAF).
     */
    private fun copyToDocumentFile(
        ctx: Context,
        destDir: DocumentFile,
        srcFile: File,
        displayName: String
    ) {
        val docFile = destDir.createFile("audio/wav", displayName) ?: return
        val uri = docFile.uri
        ctx.contentResolver.openOutputStream(uri)?.use { output ->
            srcFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
    }

    // ===================== MODE ATTENTE (WAIT MODE) =====================

    /**
     * Entre en mode attente IMMÉDIATEMENT quand une note atteint la zone
     * Pause la lecture et attend que l'utilisateur joue la note
     */
    private fun enterWaitModeImmediate() {
        if (isInWaitMode) return
        if (waitingNotesWithTiming.isEmpty()) return

        // Prendre la première note (et les notes d'accord proches en timing)
        currentWaitingNoteCounts.clear()
        val firstNote = waitingNotesWithTiming.first()

        // Compter combien de fois chaque note apparaît dans le groupe (accord ou notes rapides)
        currentWaitingNoteCounts[firstNote.note] = (currentWaitingNoteCounts[firstNote.note] ?: 0) + 1

        // Ajouter les notes d'accord (timing proche) - avec comptage des doublons
        for (timedNote in waitingNotesWithTiming.drop(1)) {
            if (kotlin.math.abs(timedNote.timeMs - firstNote.timeMs) <= CHORD_TIMING_TOLERANCE_MS) {
                currentWaitingNoteCounts[timedNote.note] = (currentWaitingNoteCounts[timedNote.note] ?: 0) + 1
            } else {
                break
            }
        }

        // IMPORTANT: Vérifier si les notes attendues sont déjà tenues par l'utilisateur
        // Pour les notes tenues, on ne valide qu'UNE occurrence (pas toutes)
        // L'utilisateur devra relâcher et réappuyer pour les notes répétées
        val alreadyHeld = currentWaitingNoteCounts.keys.intersect(currentlyHeldNotes)
        for (heldNote in alreadyHeld) {
            val count = currentWaitingNoteCounts[heldNote] ?: 0
            if (count <= 1) {
                currentWaitingNoteCounts.remove(heldNote)
            } else {
                // Encore des occurrences à jouer - l'utilisateur devra relâcher et réappuyer
                currentWaitingNoteCounts[heldNote] = count - 1
            }
        }

        // Si toutes les notes sont déjà tenues, pas besoin d'entrer en mode attente
        if (currentWaitingNoteCounts.isEmpty()) {
            waitingNotesWithTiming.clear()
            return  // Continuer sans pause
        }

        isInWaitMode = true

        // Arrêter le runnable de mise à jour de position
        handler.removeCallbacks(positionUpdateRunnable)

        // Pause l'animation AVANT l'audio
        fallingNotesView.pauseAnimation()
        if (isSheetMusicVisible) {
            sheetMusicView.pauseAnimation()
        }

        // Pause audio
        pausePracticeRecordingClock()
        stopPracticeRecordingUpdates()
        playbackController.pause()

        // Mettre à jour l'UI
        btnPlayPause.setImageResource(R.drawable.ic_play)

        // Afficher les notes attendues sur le piano virtuel
        pianoView.clearExpectedNotes()
        currentWaitingNoteCounts.keys.forEach { note ->
            pianoView.addExpectedNote(note)
        }

        // Allumer les LEDs sur le clavier externe pour les notes attendues
        currentWaitingNoteCounts.keys.forEach { note ->
            sendNoteToKeyboardLed(note, 127)
        }

        // Feedback visuel
        showWaitModeIndicator()
    }

    /**
     * Ajoute une note au groupe actuel si c'est un accord (timing proche)
     */
    private fun addNoteToCurrentChordIfApplicable(note: Int, timeMs: Long) {
        // Vérifier si c'est un accord (timing proche de la première note)
        val firstNoteTime = waitingNotesWithTiming.firstOrNull()?.timeMs ?: return
        val timeDiff = kotlin.math.abs(timeMs - firstNoteTime)

        if (timeDiff <= CHORD_TIMING_TOLERANCE_MS) {
            // C'est un accord ou note répétée - incrémenter le compteur
            val wasEmpty = !currentWaitingNoteCounts.containsKey(note)
            currentWaitingNoteCounts[note] = (currentWaitingNoteCounts[note] ?: 0) + 1

            // Ajouter au piano seulement si c'est une nouvelle note (pas juste un compteur en plus)
            if (wasEmpty) {
                pianoView.addExpectedNote(note)
                // Allumer la LED sur le clavier externe
                sendNoteToKeyboardLed(note, 127)
            }
        }
        // Sinon cette note sera traitée après que l'accord actuel soit joué
    }

    /**
     * Sort du mode attente : reprend la lecture et l'animation
     */
    private fun exitWaitMode() {
        if (!isInWaitMode) return

        // Éteindre les LEDs restantes sur le clavier externe (au cas où)
        if (currentWaitingNoteCounts.isNotEmpty()) {
            currentWaitingNoteCounts.keys.forEach { note ->
                sendNoteOffToKeyboardLed(note)
            }
        }

        // Nettoyer
        pianoView.clearExpectedNotes()
        currentWaitingNoteCounts.clear()
        waitingNotesWithTiming.clear()

        isInWaitMode = false

        // IMPORTANT: Reprendre audio AVANT l'animation pour que isPlaying() soit true
        // quand les prochaines notes atteignent la zone
        if (isPracticeRecordingEnabled) {
            resyncPracticeRecordingClock(playbackController.getCurrentPositionMs())
            stopPracticeRecordingUpdates()
        }
        playbackController.resume()

        // Reprendre animation des notes tombantes
        fallingNotesView.resumeAnimation()
        if (isSheetMusicVisible) {
            sheetMusicView.resumeAnimation()
        }

        // Redémarrer le position update runnable
        handler.post(positionUpdateRunnable)

        // Mettre à jour l'UI
        btnPlayPause.setImageResource(R.drawable.ic_pause)

        // Cacher l'indicateur
        hideWaitModeIndicator()
    }

    /**
     * Affiche un indicateur visuel pour le mode attente
     */
    private fun showWaitModeIndicator() {
        // Pour l'instant, on change juste la couleur de la barre de progression
        // Une implémentation plus élaborée pourrait ajouter un overlay
        seekbarPosition.progressTintList = ColorStateList.valueOf(
            resources.getColor(R.color.midi_warning, null)
        )
    }

    /**
     * Cache l'indicateur visuel du mode attente
     */
    private fun hideWaitModeIndicator() {
        seekbarPosition.progressTintList = ColorStateList.valueOf(
            getThemeAccentColor()
        )
    }

    /**
     * Réinitialise le mode attente (appelé lors de stop/restart)
     */
    private fun resetWaitMode() {
        // Éteindre les LEDs sur le clavier externe si en mode attente
        if (currentWaitingNoteCounts.isNotEmpty()) {
            currentWaitingNoteCounts.keys.forEach { note ->
                sendNoteOffToKeyboardLed(note)
            }
        }

        isInWaitMode = false
        currentWaitingNoteCounts.clear()
        waitingNotesWithTiming.clear()
        hideWaitModeIndicator()
    }

    /**
     * Met a jour l'affichage de la position (slider + temps)
     */
    private fun updatePositionDisplay(positionMs: Long) {
        val durationMs = playbackController.getDurationMs()
        if (durationMs > 0) {
            // Mise à jour de l'ancien seekbar (caché mais gardé pour compatibilité)
            val progress = ((positionMs * 1000) / durationMs).toInt().coerceIn(0, 1000)
            seekbarPosition.progress = progress

            // Mise à jour du nouveau MarkerSeekBar
            val normalizedProgress = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            markerSeekBar.setProgress(normalizedProgress)
        }
        txtTimeCurrent.text = formatTime(positionMs)
        updateRemainingTimeDisplay(positionMs, playbackController.getDurationMs())
    }

    /**
     * Met à jour l'affichage du temps restant (format négatif)
     */
    private fun updateRemainingTimeDisplay(positionMs: Long, durationMs: Long) {
        val remainingMs = durationMs - positionMs
        txtTimeTotal.text = if (remainingMs > 0) {
            getString(R.string.practice_time_remaining, formatTime(remainingMs))
        } else {
            getString(R.string.practice_time_zero)
        }
    }

    /**
     * Formate un temps en millisecondes en chaine "m:ss"
     */
    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }

    /**
     * Recupere la couleur d'accent du theme actuel (suit le choix de l'utilisateur)
     */
    private fun getThemeAccentColor(): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(R.attr.a2uMidiAccent, typedValue, true)
        return typedValue.data
    }

    /**
     * Met a jour l'etat visuel du bouton accompagnement
     */
    private fun updateAccompanimentButtonState() {
        val color = if (isAccompanimentOn) {
            getThemeAccentColor()
        } else {
            resources.getColor(R.color.midi_text_secondary, null)
        }
        btnAccompaniment.setColorFilter(color)
    }

    /**
     * Met a jour l'etat visuel du bouton "Je joue"
     */
    private fun updateIPlayButtonState() {
        val color = if (isIPlayOn) {
            getThemeAccentColor()
        } else {
            resources.getColor(R.color.midi_text_secondary, null)
        }
        btnIPlay.setColorFilter(color)
    }

    /**
     * Met a jour l'etat visuel du bouton "LEDs clavier"
     */
    private fun updateKeyboardLedsButtonState() {
        val color = if (sendLedsToKeyboard) {
            getThemeAccentColor()
        } else {
            resources.getColor(R.color.midi_text_secondary, null)
        }
        btnKeyboardLeds.setColorFilter(color)
    }

    /**
     * Met a jour l'etat visuel du bouton "Mute son LED"
     * Quand actif (muteLedChannelSound = true), le bouton est accent color (lumiere sans son)
     */
    private fun updateMuteLedSoundButtonState() {
        val color = if (muteLedChannelSound) {
            getThemeAccentColor()
        } else {
            resources.getColor(R.color.midi_text_secondary, null)
        }
        btnMuteLedSound.setColorFilter(color)
    }

    /**
     * Met a jour l'etat visuel du bouton "Mute synthé interne"
     * Quand actif (muteKeyboardSound = false), le bouton est accent color (synthé interne muté)
     * Note: La logique est inversée - muteKeyboardSound=false signifie que l'on NE joue PAS
     * les notes utilisateur via le synthé interne
     */
    private fun updateMuteInternalSynthButtonState() {
        // Button is ON (highlighted) when internal synth is muted = muteKeyboardSound is FALSE
        val internalSynthMuted = !muteKeyboardSound
        val color = if (internalSynthMuted) {
            getThemeAccentColor()
        } else {
            resources.getColor(R.color.midi_text_secondary, null)
        }
        btnMuteInternalSynth.setColorFilter(color)
    }

    /**
     * Show volume popup above a button
     * @param anchorView The button to anchor the popup to
     * @param isAccompaniment true for accompaniment volume, false for target channel volume
     */
    private fun showVolumePopup(anchorView: View, isAccompaniment: Boolean) {
        // Dismiss any existing popup
        volumePopup?.dismiss()

        val context = requireContext()

        // Create a vertical SeekBar inside a small container
        val displayMetrics = resources.displayMetrics
        val minWidthPx = (160 * displayMetrics.density).toInt() // 160dp minimum width

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundResource(R.drawable.bg_title_overlay)
            gravity = Gravity.CENTER
            minimumWidth = minWidthPx
        }

        // Volume percentage label
        val currentVolume = if (isAccompaniment) accompanimentVolume else targetChannelVolume
        val initialPercent = (currentVolume * 100) / 127
        val volumeLabel = TextView(context).apply {
            text = getString(R.string.practice_volume_percent, initialPercent)
            setTextColor(resources.getColor(R.color.midi_text_primary, null))
            textSize = 12f
            gravity = Gravity.CENTER
        }
        container.addView(volumeLabel)

        // SeekBar (horizontal for simplicity)
        val seekBar = SeekBar(context).apply {
            max = 127
            progress = if (isAccompaniment) accompanimentVolume else targetChannelVolume
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }

            // Theme the seekbar
            progressTintList = ColorStateList.valueOf(getThemeAccentColor())
            thumbTintList = ColorStateList.valueOf(getThemeAccentColor())

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        // Convert 0-127 to percentage for display
                        val percent = (progress * 100) / 127
                        volumeLabel.text = getString(R.string.practice_volume_percent, percent)
                        applyVolume(isAccompaniment, progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(seekBar)

        // Create the popup window
        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true  // focusable
        ).apply {
            elevation = 8f
            isOutsideTouchable = true
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        // Measure the popup to position it correctly
        container.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        // Position above the button, centered
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)

        val x = location[0] + (anchorView.width / 2) - (container.measuredWidth / 2)
        val y = location[1] - container.measuredHeight - 16

        popup.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y)
        volumePopup = popup

        // Haptic feedback
        anchorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /**
     * Apply volume change via MIDI CC7 (volume) messages
     * @param isAccompaniment true for accompaniment channels, false for target channel
     * @param volume 0-127 volume level
     */
    private fun applyVolume(isAccompaniment: Boolean, volume: Int) {
        val synth = synthesizer ?: return

        if (isAccompaniment) {
            accompanimentVolume = volume
            // Apply to all channels except target channel(s)
            val targetChannels = if (session.args.isTwoHandsMode) {
                setOf(session.args.leftHandChannel, session.args.rightHandChannel)
            } else {
                setOf(session.args.channelNumber)
            }

            for (channel in 0..15) {
                if (channel !in targetChannels) {
                    // CC7 = Volume
                    synth.controlChange(channel, 7, volume)
                }
            }
        } else {
            targetChannelVolume = volume
            // Apply to target channel(s) only
            if (session.args.isTwoHandsMode) {
                synth.controlChange(session.args.leftHandChannel, 7, volume)
                synth.controlChange(session.args.rightHandChannel, 7, volume)
            } else {
                synth.controlChange(session.args.channelNumber, 7, volume)
            }
        }
    }

    /**
     * Change le mode de pratique des mains (visuel uniquement, n'affecte pas l'audio)
     */
    private fun setHandPracticeMode(mode: HandPracticeMode) {
        val previousMode = session.handPracticeMode

        session.setHandPracticeMode(mode)
        fallingNotesView.setHandPracticeMode(mode)
        updateHandSelectionButtonStates()

        // Éteindre les notes de la/des main(s) qu'on arrête de suivre
        if (session.args.isTwoHandsMode) {
            turnOffNotesForExcludedHands(previousMode, mode)
        }
    }

    /**
     * Éteint les notes des mains qui ne sont plus suivies après un changement de mode
     */
    private fun turnOffNotesForExcludedHands(previousMode: HandPracticeMode, newMode: HandPracticeMode) {
        val leftChannel = session.args.leftHandChannel
        val rightChannel = session.args.rightHandChannel

        // Déterminer quels canaux doivent être éteints
        val channelsToTurnOff = mutableListOf<Int>()

        when (newMode) {
            HandPracticeMode.LEFT_HAND_ONLY -> {
                // On passe à main gauche seule -> éteindre main droite
                if (previousMode != HandPracticeMode.LEFT_HAND_ONLY) {
                    channelsToTurnOff.add(rightChannel)
                }
            }
            HandPracticeMode.RIGHT_HAND_ONLY -> {
                // On passe à main droite seule -> éteindre main gauche
                if (previousMode != HandPracticeMode.RIGHT_HAND_ONLY) {
                    channelsToTurnOff.add(leftChannel)
                }
            }
            HandPracticeMode.BOTH_HANDS -> {
                // On passe à deux mains -> rien à éteindre
            }
        }

        // Éteindre les notes sur le clavier visuel et les LEDs externes
        for (channel in channelsToTurnOff) {
            val notesOff = pianoView.notesOffForChannel(channel)
            // Éteindre aussi les LEDs sur le clavier externe
            for (note in notesOff) {
                sendNoteOffToKeyboardLed(note)
            }
        }
    }

    /**
     * Met a jour l'etat visuel des boutons de selection de main
     */
    private fun updateHandSelectionButtonStates() {
        val accentColor = getThemeAccentColor()
        val secondaryColor = resources.getColor(R.color.midi_text_secondary, null)

        val currentMode = session.handPracticeMode

        btnHandLeft.setColorFilter(
            if (currentMode == HandPracticeMode.LEFT_HAND_ONLY) accentColor else secondaryColor
        )
        btnHandsBoth.setColorFilter(
            if (currentMode == HandPracticeMode.BOTH_HANDS) accentColor else secondaryColor
        )
        btnHandRight.setColorFilter(
            if (currentMode == HandPracticeMode.RIGHT_HAND_ONLY) accentColor else secondaryColor
        )
    }

    /**
     * Change le mode de son des mains (mute/unmute les canaux audio)
     */
    private fun setHandSoundMode(mode: HandPracticeMode) {
        handSoundMode = mode

        // Mettre à jour le muting des canaux dans le playback controller
        when (mode) {
            HandPracticeMode.LEFT_HAND_ONLY -> {
                playbackController.muteLeftHandChannel = false
                playbackController.muteRightHandChannel = true
            }
            HandPracticeMode.RIGHT_HAND_ONLY -> {
                playbackController.muteLeftHandChannel = true
                playbackController.muteRightHandChannel = false
            }
            HandPracticeMode.BOTH_HANDS -> {
                playbackController.muteLeftHandChannel = false
                playbackController.muteRightHandChannel = false
            }
        }

        updateHandSoundButtonStates()

        // Toast pour feedback utilisateur
        val messageRes = when (mode) {
            HandPracticeMode.LEFT_HAND_ONLY -> R.string.practice_left_hand_sound
            HandPracticeMode.RIGHT_HAND_ONLY -> R.string.practice_right_hand_sound
            HandPracticeMode.BOTH_HANDS -> R.string.practice_both_hands_sound
        }
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    /**
     * Met à jour l'état visuel des boutons de son des mains
     */
    private fun updateHandSoundButtonStates() {
        val accentColor = getThemeAccentColor()
        val secondaryColor = resources.getColor(R.color.midi_text_secondary, null)

        btnHandLeftSound.setColorFilter(
            if (handSoundMode == HandPracticeMode.LEFT_HAND_ONLY) accentColor else secondaryColor
        )
        btnHandsBothSound.setColorFilter(
            if (handSoundMode == HandPracticeMode.BOTH_HANDS) accentColor else secondaryColor
        )
        btnHandRightSound.setColorFilter(
            if (handSoundMode == HandPracticeMode.RIGHT_HAND_ONLY) accentColor else secondaryColor
        )
    }

    /**
     * Toggle l'affichage du slider de vitesse
     */
    private fun toggleSpeedSlider() {
        isSpeedSliderVisible = !isSpeedSliderVisible
        speedSliderContainer.visibility = if (isSpeedSliderVisible) View.VISIBLE else View.GONE

        // Mettre a jour l'icone du bouton
        val color = if (isSpeedSliderVisible) {
            getThemeAccentColor()
        } else {
            resources.getColor(R.color.midi_text_secondary, null)
        }
        btnSpeed.setColorFilter(color)
    }

    /**
     * Met a jour l'affichage de la vitesse
     */
    private fun updateSpeedDisplay(speed: Float) {
        txtSpeed.text = String.format(Locale.ROOT, "%.1fx", speed)

        // Changer la couleur si vitesse reduite
        val color = if (speed < 1.0f) {
            getThemeAccentColor()
        } else {
            resources.getColor(R.color.midi_text_primary, null)
        }
        txtSpeed.setTextColor(color)
    }

    // ===================== COUNTDOWN / START DELAY METHODS =====================

    /**
     * Affiche le dialog de configuration du retardateur
     */
    private fun showCountdownSettingsDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_countdown_settings, null)

        val seekbarCountdown = dialogView.findViewById<SeekBar>(R.id.seekbar_countdown)
        val txtCountdownValue = dialogView.findViewById<TextView>(R.id.txt_countdown_value)
        val switchStartOnPlay = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_start_on_play)

        // Initialize with current values
        seekbarCountdown.progress = countdownDelaySeconds
        switchStartOnPlay.isChecked = startOnFirstNote
        updateCountdownValueText(txtCountdownValue, countdownDelaySeconds)

        // Update text when slider changes
        seekbarCountdown.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateCountdownValueText(txtCountdownValue, progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.practice_countdown_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                countdownDelaySeconds = seekbarCountdown.progress
                startOnFirstNote = switchStartOnPlay.isChecked

                // Save to preferences
                prefs.edit {
                    putInt(PREF_COUNTDOWN_DELAY, countdownDelaySeconds)
                    putBoolean(PREF_START_ON_FIRST_NOTE, startOnFirstNote)
                }

                updateCountdownButtonState()
                android.util.Log.d(TAG, "Countdown settings saved: delay=$countdownDelaySeconds startOnFirstNote=$startOnFirstNote")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Met à jour le texte de la valeur du countdown dans le dialog
     */
    private fun updateCountdownValueText(textView: TextView, seconds: Int) {
        textView.text = if (seconds == 0) {
            getString(R.string.practice_countdown_disabled)
        } else {
            getString(R.string.practice_countdown_seconds, seconds)
        }
    }

    /**
     * Met à jour l'état visuel du bouton retardateur
     */
    private fun updateCountdownButtonState() {
        val isActive = countdownDelaySeconds > 0 || startOnFirstNote
        val color = if (isActive) {
            getThemeAccentColor()
        } else {
            resources.getColor(R.color.midi_text_secondary, null)
        }
        btnCountdown.setColorFilter(color)
    }

    /**
     * Démarre la lecture avec le délai configuré ou en attendant la première note
     */
    private fun startPlaybackWithCountdown() {
        // Cancel any existing countdown
        countdownJob?.cancel()
        isWaitingForFirstNote = false

        when {
            startOnFirstNote -> {
                // Wait for user to play first note
                isWaitingForFirstNote = true
                Toast.makeText(requireContext(), R.string.practice_countdown_waiting, Toast.LENGTH_SHORT).show()
                android.util.Log.d(TAG, "Waiting for first note to start playback")
            }
            countdownDelaySeconds > 0 -> {
                // Start countdown
                countdownJob = lifecycleScope.launch {
                    for (i in countdownDelaySeconds downTo 1) {
                        Toast.makeText(requireContext(), getString(R.string.practice_countdown_active, i), Toast.LENGTH_SHORT).show()
                        delay(1000)
                    }
                    // Start playback after countdown
                    startActualPlayback()
                }
            }
            else -> {
                // No delay, start immediately
                startActualPlayback()
            }
        }
    }

    /**
     * Démarre réellement la lecture (appelé après countdown ou immédiatement)
     */
    private fun startActualPlayback() {
        isWaitingForFirstNote = false
        countdownJob = null

        if (isFreeMode) return

        if (playbackController.isPaused()) {
            playbackController.resume()
        } else {
            playbackController.play()
        }

        // Update UI
        if (playbackController.isPlaying()) {
            btnPlayPause.setImageResource(R.drawable.ic_pause)
            startAnimation()
        } else {
            showError("Impossible de demarrer la lecture")
        }
    }

    /**
     * Appelé quand l'utilisateur joue une note (pour le mode "start on first note")
     */
    private fun onUserPlayedNote() {
        if (isWaitingForFirstNote) {
            android.util.Log.d(TAG, "First note played, starting playback")
            startActualPlayback()
        }
    }

    // ===================== FREE MODE METHODS =====================

    /**
     * Configure l'UI pour le mode libre (sans fichier MIDI)
     */
    @Suppress("UNUSED_PARAMETER")
    private fun setupFreeModeUI(args: PracticeArgs) {
        // Show free mode controls
        freeModeControls.visibility = View.VISIBLE

        // Keep falling notes view visible for theme background display
        // (it will just show the background, no notes)
        fallingNotesView.visibility = View.VISIBLE
        fallingNotesView.setNotes(emptyList())  // No notes to display

        // Hide title overlay and entire top overlay (no track info and no seekbar in free mode)
        titleOverlay.visibility = View.GONE
        topOverlay.visibility = View.GONE

        // Hide playback controls that don't apply in free mode
        btnPlayPause.visibility = View.GONE
        btnRestart.visibility = View.GONE
        btnStop.visibility = View.GONE
        btnMark.visibility = View.GONE
        btnGotoMark.visibility = View.GONE
        btnAccompaniment.visibility = View.GONE
        btnIPlay.visibility = View.GONE
        speedSliderContainer.visibility = View.GONE
        txtSpeed.visibility = View.GONE
        btnSpeed.visibility = View.GONE
        btnCountdown.visibility = View.GONE  // No countdown delay in free mode
        view?.findViewById<View>(R.id.speed_control)?.visibility = View.GONE

        // Hide position controls
        seekbarPosition.visibility = View.GONE
        txtTimeCurrent.visibility = View.GONE
        txtTimeTotal.visibility = View.GONE

        // Hide the entire playback controls panel in free mode
        controlsPanel.visibility = View.GONE

        // Update constraints: free_mode_controls goes to bottom, piano_scroll goes to top of free_mode_controls
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)
        // free_mode_controls: bottom to parent bottom
        constraintSet.connect(
            R.id.free_mode_controls,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM
        )
        // piano_scroll: bottom to top of free_mode_controls
        constraintSet.connect(
            R.id.piano_scroll,
            ConstraintSet.BOTTOM,
            R.id.free_mode_controls,
            ConstraintSet.TOP
        )
        constraintSet.applyTo(rootLayout)

        // Set title for free mode
        txtTrackTitle.text = getString(R.string.practice_free_mode)
        txtInstrument.text = GeneralMidiInstruments.getName(requireContext(), currentProgram)

        // Configure octave indicator
        octaveIndicator.setActiveRange(3, 5)  // Default: C3 to C5 (3 octaves)
        octaveIndicator.onRangeChanged = { noteMin, noteMax ->
            pianoView.setNoteRange(noteMin, noteMax)
            fallingNotesView.setNoteRange(noteMin, noteMax)  // Sync falling notes view
            updateOctaveCountDisplay()
        }

        // Update displays
        updateOctaveCountDisplay()
        updateInstrumentDisplay()

        // Initialiser le gestionnaire de thèmes et appliquer le thème actuel
        PracticeThemeManager.init(requireContext())
        applyCurrentThemeToAllViews()

        // Set initial note range for both views
        val noteMin = octaveIndicator.getNoteMin()
        val noteMax = octaveIndicator.getNoteMax()
        pianoView.setNoteRange(noteMin, noteMax)
        fallingNotesView.setNoteRange(noteMin, noteMax)

        // Activer le mode rising notes pour le free practice
        fallingNotesView.setNoteDirection(FallingNotesView.NoteDirection.RISING)
        fallingNotesView.startRisingAnimation()

        // Enable note names on piano
        pianoView.showNoteNames = true

        // Send initial program change to MidiDriver
        sendProgramChange(currentProgram)
    }

    /**
     * Configure les listeners pour les controles du mode libre
     */
    private fun setupFreeModeListeners() {
        // Shift left (towards bass)
        btnShiftLeft.setOnClickListener {
            octaveIndicator.shiftDown()
        }

        // Shift right (towards treble)
        btnShiftRight.setOnClickListener {
            octaveIndicator.shiftUp()
        }

        // Decrease octave count
        btnOctaveCountMinus.setOnClickListener {
            octaveIndicator.decreaseOctaveCount()
            updateOctaveCountDisplay()
        }

        // Increase octave count
        btnOctaveCountPlus.setOnClickListener {
            octaveIndicator.increaseOctaveCount()
            updateOctaveCountDisplay()
        }

        // Instrument selector
        instrumentSelector.setOnClickListener {
            showInstrumentSelector()
        }
    }

    /**
     * Met a jour l'affichage du nombre d'octaves
     */
    private fun updateOctaveCountDisplay() {
        val count = octaveIndicator.getOctaveCount()
        txtOctaveCount.text = getString(R.string.practice_octave_count, count)
    }

    /**
     * Met a jour l'affichage de l'instrument actuel et les couleurs des vues
     */
    private fun updateInstrumentDisplay() {
        val instrumentName = GeneralMidiInstruments.getName(requireContext(), currentProgram)
        txtCurrentInstrument.text = getString(R.string.practice_current_instrument, currentProgram, instrumentName)
        txtInstrument.text = instrumentName

        // Update views with new program for color lookup
        if (::pianoView.isInitialized) pianoView.setCurrentProgram(currentProgram)
        if (::fallingNotesView.isInitialized) fallingNotesView.setCurrentProgram(currentProgram)
        if (::sheetMusicView.isInitialized) sheetMusicView.setCurrentProgram(currentProgram)
    }

    /**
     * Affiche le selecteur d'instrument (0-127)
     */
    private fun showInstrumentSelector() {
        // Create list of instruments
        val instruments = Array(128) { program ->
            "$program: ${GeneralMidiInstruments.getName(requireContext(), program)}"
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.practice_select_instrument)
            .setSingleChoiceItems(instruments, currentProgram) { dialog, which ->
                currentProgram = which
                updateInstrumentDisplay()
                sendProgramChange(currentProgram)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Envoie un Program Change MIDI au synthetiseur
     */
    private fun sendProgramChange(program: Int) {
        synthesizer?.programChange(session.args.channelNumber, program)
    }

    /**
     * Affiche la confirmation pour passer en mode libre
     */
    private fun showSwitchToFreeModeDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.practice_switch_to_free_mode_title)
            .setMessage(R.string.practice_switch_to_free_mode_message)
            .setPositiveButton(R.string.practice_switch_confirm) { _, _ ->
                switchToFreeMode()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Passe en mode libre en déchargeant le fichier MIDI
     */
    private fun switchToFreeMode() {
        // IMPORTANT: Set free mode flag FIRST to prevent any callbacks from adding notes
        isFreeMode = true

        // Stop playback and reset wait mode
        resetWaitMode()

        // Stop all animation and position updates IMMEDIATELY
        isAnimating = false
        stopAnimation()
        handler.removeCallbacks(positionUpdateRunnable)
        stopPracticeRecordingUpdates()

        // Stop playback controller completely
        playbackController.stop()
        playbackController.release()

        // Clear waiting notes tracking BEFORE resetting views
        waitingNotesWithTiming.clear()
        currentWaitingNoteCounts.clear()
        currentlyHeldNotes.clear()

        // Clear piano state FIRST (before views that might trigger callbacks)
        pianoView.clearExpectedNotes()
        pianoView.allNotesOff()
        pianoView.allExternalNotesOff()
        pianoView.targetChannel = 0  // Reset to channel 0 for free mode
        pianoView.setCurrentProgram(0)  // Reset to piano

        // Reset all views (now safe since isFreeMode is true)
        fallingNotesView.reset()
        fallingNotesView.setNotes(emptyList())  // Vider complètement les notes
        handSelectionContainer.visibility = View.GONE  // Cacher la selection de main
        handSoundContainer.visibility = View.GONE  // Cacher le contrôle de son par main
        sheetMusicView.reset()
        sheetMusicView.setNotes(emptyList())
        clearPracticeRecordingNotes()
        sheetMusicView.setRecordingModeEnabled(isPracticeRecordingEnabled)
        sheetMusicView.setRecordedNotes(recordedNotes)

        // Force redraw
        pianoView.invalidate()

        // Clear markers
        markerSeekBar.clearMarkers()
        markerSeekBar.setProgress(0f)

        // Stop all sounds on synthesizer on ALL channels
        synthesizer?.allSoundOff()

        // Reset all MIDI controllers on all channels (fixes stuck sustain pedal, etc.)
        for (channel in 0..15) {
            // CC121 = Reset All Controllers
            synthesizer?.controlChange(channel, 121, 0)
            // CC64 = Sustain Pedal Off (explicit, au cas où)
            synthesizer?.controlChange(channel, 64, 0)
            // CC123 = All Notes Off
            synthesizer?.controlChange(channel, 123, 0)
        }

        // Reset to default piano sound (program 0) on channel 0
        currentProgram = 0
        // Reset program on all 16 MIDI channels to piano
        for (channel in 0..15) {
            synthesizer?.programChange(channel, 0)
        }

        // Create default free mode args
        val freeModeArgs = PracticeArgs(
            trackFilePath = "",
            channelNumber = 0,
            noteRangeMin = 48,
            noteRangeMax = 84,
            instrumentName = GeneralMidiInstruments.getName(requireContext(), currentProgram),
            trackTitle = getString(R.string.practice_free_mode),
            trackIndex = 0,
            programNumber = currentProgram
        )

        // Create new session for free mode
        session = PracticeSession(freeModeArgs)

        // Reinitialize playback controller without file
        synthesizer?.let { playbackController.initialize(it) }
        resyncPracticeRecordingClock(playbackController.getCurrentPositionMs())
        startPracticeRecordingUpdatesIfNeeded()

        // Setup free mode UI and listeners
        setupFreeModeListeners()
        setupFreeModeUI(freeModeArgs)

        // Hide the Free Mode button (we're already in free mode)
        btnFreeMode.visibility = View.GONE
    }

    /**
     * Navigate to a tab in the main activity
     */
    private fun navigateToTab(tabIndex: Int) {
        // Pop back to exit practice mode
        parentFragmentManager.popBackStack()
        // Tell the activity to switch to the specified tab
        (activity as? MidiPlayerActivity)?.selectTab(tabIndex)
    }

    /**
     * Show settings popup menu
     */
    private fun showSettingsMenu() {
        val popup = PopupMenu(requireContext(), btnMenu)
        popup.menuInflater.inflate(R.menu.menu_practice, popup.menu)

        // Mettre à jour l'état du checkbox pour le mode attente
        popup.menu.findItem(R.id.action_wait_mode)?.isChecked = session.waitModeEnabled

        // Mettre à jour l'état du checkbox pour la partition
        popup.menu.findItem(R.id.action_sheet_music)?.isChecked = isSheetMusicVisible

        // Mettre à jour l'état du checkbox pour la validation de durée
        popup.menu.findItem(R.id.action_hold_validation)?.isChecked = session.holdDurationValidationEnabled

        // Cacher les options non pertinentes en mode libre (pas de MIDI chargé)
        if (isFreeMode) {
            popup.menu.findItem(R.id.action_wait_mode)?.isVisible = false
            popup.menu.findItem(R.id.action_hold_validation)?.isVisible = false
            popup.menu.findItem(R.id.action_timing_tolerance)?.isVisible = false
            popup.menu.findItem(R.id.action_scoring_display)?.isVisible = false
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sheet_music -> {
                    toggleSheetMusic()
                    item.isChecked = isSheetMusicVisible
                    true
                }
                R.id.action_audio_settings -> {
                    (activity as? MidiPlayerActivity)?.showAudioSettingsDialog()
                    true
                }
                R.id.action_select_soundfont -> {
                    // Just show the dialog - the broadcast receiver will handle reloading
                    // when the service finishes loading the new SF2
                    (activity as? MidiPlayerActivity)?.showSynthesizerSelectionDialog()
                    true
                }
                R.id.action_wait_mode -> {
                    val newState = !session.waitModeEnabled
                    session.setWaitModeEnabled(newState)
                    item.isChecked = newState

                    // When wait mode is enabled, orchestration must ALWAYS be off
                    if (newState && isAccompanimentOn) {
                        isAccompanimentOn = false
                        playbackController.playAccompaniment = false
                        session.setAccompanimentEnabled(false)
                        updateAccompanimentButtonState()
                    }

                    // Feedback utilisateur
                    val message = if (newState) {
                        getString(R.string.practice_wait_mode_enabled)
                    } else {
                        getString(R.string.practice_wait_mode_disabled)
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

                    // Si on désactive le mode attente et qu'on est en pause, reprendre
                    if (!newState && isInWaitMode) {
                        exitWaitMode()
                    }
                    true
                }
                R.id.action_hold_validation -> {
                    val newState = !session.holdDurationValidationEnabled
                    session.setHoldDurationValidationEnabled(newState)
                    item.isChecked = newState

                    val message = if (newState) {
                        getString(R.string.practice_hold_duration_enabled)
                    } else {
                        getString(R.string.practice_hold_duration_disabled)
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_timing_tolerance -> {
                    showTimingToleranceDialog()
                    true
                }
                R.id.action_visual_style -> {
                    showVisualStyleDialog()
                    true
                }
                R.id.action_scoring_display -> {
                    showScoringDisplayDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * Affiche un dialogue pour choisir la tolérance de timing
     */
    private fun showTimingToleranceDialog() {
        val options = arrayOf(
            getString(R.string.practice_timing_tolerance_strict),
            getString(R.string.practice_timing_tolerance_normal),
            getString(R.string.practice_timing_tolerance_relaxed),
            getString(R.string.practice_timing_tolerance_very_relaxed)
        )
        val tolerances = longArrayOf(30L, 60L, 120L, 200L)

        // Trouver l'index actuel
        val currentIndex = tolerances.indexOfFirst { it == session.timingToleranceMs }.takeIf { it >= 0 } ?: 1

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.practice_timing_tolerance)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                session.setTimingToleranceMs(tolerances[which])
                fallingNotesView.setHitZoneToleranceMs(tolerances[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Affiche un dialogue pour choisir le thème visuel
     */
    private fun showVisualStyleDialog() {
        ThemeSelectionDialog(
            context = requireContext(),
            onThemeSelected = { theme ->
                applyCurrentThemeToAllViews()
                val message = getString(R.string.theme_applied, theme.displayName)
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            },
            onCustomThemeConfigureRequested = {
                showCustomThemeDialog()
            }
        ).show()
    }

    /**
     * Affiche le dialogue de configuration du thème personnalisé
     */
    private fun showCustomThemeDialog() {
        customThemeDialog = CustomThemeDialog(
            context = requireContext(),
            imagePickerLauncher = imagePickerLauncher,
            onThemeConfigured = {
                applyCurrentThemeToAllViews()
            }
        )
        customThemeDialog?.show()
    }

    /**
     * Gère la sélection d'une image pour le thème personnalisé
     */
    private fun handleImageSelected(uri: Uri) {
        val success = PracticeThemeManager.saveCustomBackgroundImage(uri)
        if (success) {
            Toast.makeText(
                requireContext(),
                getString(R.string.custom_theme_image_set),
                Toast.LENGTH_SHORT
            ).show()
            customThemeDialog?.onImageSelected()
            applyCurrentThemeToAllViews()
        } else {
            Toast.makeText(
                requireContext(),
                "Error loading image",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Applique le thème actuel à toutes les vues (falling notes, piano, partition)
     */
    private fun applyCurrentThemeToAllViews() {
        val theme = PracticeThemeManager.getCurrentTheme()

        if (::fallingNotesView.isInitialized) {
            fallingNotesView.setTheme(theme)
        }
        if (::pianoView.isInitialized) {
            pianoView.setTheme(theme)
        }
        if (::sheetMusicView.isInitialized) {
            sheetMusicView.setTheme(theme)
        }
    }

    /**
     * Affiche le dialogue de configuration de l'affichage du score
     */
    private fun showScoringDisplayDialog() {
        val presets = arrayOf(
            getString(R.string.practice_scoring_preset_encouragement),
            getString(R.string.practice_scoring_preset_full),
            getString(R.string.practice_scoring_preset_minimal),
            getString(R.string.practice_scoring_preset_custom)
        )

        // Determine which preset is currently active (if any)
        val currentIndex = when {
            !scoringConfig.scoringEnabled -> -1 // No preset if disabled
            matchesPreset(scoringConfig, ScoringConfig.encouragementPreset()) -> 0
            matchesPreset(scoringConfig, ScoringConfig.fullStatsPreset()) -> 1
            matchesPreset(scoringConfig, ScoringConfig.minimalPreset()) -> 2
            else -> 3 // Custom
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.practice_scoring_display_title)
            .setSingleChoiceItems(presets, currentIndex) { dialog, which ->
                val newConfig = when (which) {
                    0 -> ScoringConfig.encouragementPreset()
                    1 -> ScoringConfig.fullStatsPreset()
                    2 -> ScoringConfig.minimalPreset()
                    3 -> {
                        // Show custom config dialog
                        dialog.dismiss()
                        showCustomScoringDialog()
                        return@setSingleChoiceItems
                    }
                    else -> scoringConfig
                }
                applyScoringConfig(newConfig)
                dialog.dismiss()
            }
            .setNeutralButton(R.string.practice_scoring_disabled) { dialog, _ ->
                applyScoringConfig(ScoringConfig.disabledPreset())
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Show custom scoring configuration dialog
     */
    private fun showCustomScoringDialog() {
        val items = arrayOf(
            getString(R.string.practice_scoring_show_score),
            getString(R.string.practice_scoring_show_accuracy),
            getString(R.string.practice_scoring_show_streak),
            getString(R.string.practice_scoring_show_best_streak),
            getString(R.string.practice_scoring_show_good_notes),
            getString(R.string.practice_scoring_show_perfect_notes),
            getString(R.string.practice_scoring_show_missed_notes),
            getString(R.string.practice_scoring_show_combo),
            getString(R.string.practice_scoring_show_grade),
            getString(R.string.practice_scoring_compact),
            getString(R.string.practice_scoring_animations),
            getString(R.string.practice_scoring_streak_popups)
        )

        val checkedItems = booleanArrayOf(
            scoringConfig.showScore,
            scoringConfig.showAccuracy,
            scoringConfig.showCurrentStreak,
            scoringConfig.showBestStreak,
            scoringConfig.showGoodNotes,
            scoringConfig.showPerfectNotes,
            scoringConfig.showMissedNotes,
            scoringConfig.showCombo,
            scoringConfig.showGrade,
            scoringConfig.compactMode,
            scoringConfig.showAnimations,
            scoringConfig.showStreakPopups
        )

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.practice_scoring_preset_custom)
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newConfig = ScoringConfig(
                    scoringEnabled = true,
                    showScore = checkedItems[0],
                    showAccuracy = checkedItems[1],
                    showCurrentStreak = checkedItems[2],
                    showBestStreak = checkedItems[3],
                    showGoodNotes = checkedItems[4],
                    showPerfectNotes = checkedItems[5],
                    showMissedNotes = checkedItems[6],
                    showCombo = checkedItems[7],
                    showGrade = checkedItems[8],
                    compactMode = checkedItems[9],
                    showAnimations = checkedItems[10],
                    showStreakPopups = checkedItems[11]
                )
                applyScoringConfig(newConfig)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Check if current config matches a preset
     */
    private fun matchesPreset(current: ScoringConfig, preset: ScoringConfig): Boolean {
        return current.scoringEnabled == preset.scoringEnabled &&
               current.showScore == preset.showScore &&
               current.showAccuracy == preset.showAccuracy &&
               current.showCurrentStreak == preset.showCurrentStreak &&
               current.showBestStreak == preset.showBestStreak &&
               current.showGoodNotes == preset.showGoodNotes &&
               current.showPerfectNotes == preset.showPerfectNotes &&
               current.showMissedNotes == preset.showMissedNotes &&
               current.showCombo == preset.showCombo &&
               current.showGrade == preset.showGrade
    }

    /**
     * Apply a scoring configuration and update the display
     */
    private fun applyScoringConfig(newConfig: ScoringConfig) {
        scoringConfig = newConfig
        ScoringConfig.save(requireContext(), newConfig)
        scoreDisplayView.setConfig(newConfig)

        val message = if (newConfig.scoringEnabled) {
            getString(R.string.practice_scoring_config_saved)
        } else {
            getString(R.string.practice_scoring_disabled)
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Recharge le synthétiseur en relisant les paramètres depuis la base de données.
     * Appelé après un changement de paramètres via le dialog.
     */
    private fun reloadSynthesizerFromSettings() {
        // Stop current playback
        if (::playbackController.isInitialized) {
            playbackController.pause()
        }

        // Release old synthesizer
        synthesizer?.allSoundOff()
        synthesizer?.release()
        synthesizer = null

        // Reinitialize by reading fresh settings from DB
        initSynthesizer()

        // Reconnect to playback controller
        synthesizer?.let { playbackController.initialize(it) }

        // Send current program change
        if (isFreeMode) {
            sendProgramChange(currentProgram)
        } else {
            // Training mode - restore the instrument from args
            synthesizer?.programChange(session.args.channelNumber, session.args.programNumber)
        }

        // Update UI
        activity?.runOnUiThread {
            btnPlayPause.setImageResource(R.drawable.ic_play)
        }
    }

    /**
     * Rafraîchit les couleurs dans toutes les vues (appelé après modification des paramètres de couleur)
     */
    fun refreshColors() {
        // Ensure we're on the main thread for UI updates
        activity?.runOnUiThread {
            if (::fallingNotesView.isInitialized) {
                fallingNotesView.refreshColors()
            }
            if (::pianoView.isInitialized) {
                pianoView.refreshColors()
            }
            if (::sheetMusicView.isInitialized) {
                sheetMusicView.refreshColors()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register broadcast receiver for SF2 load complete notifications
        if (!isReceiverRegistered) {
            val filter = IntentFilter(MidiPlaybackService.ACTION_SF2_LOAD_COMPLETE)
            ContextCompat.registerReceiver(
                requireContext(),
                sf2LoadCompleteReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        }
        // Start MIDI input controller for external keyboard
        midiInputController?.start()

        if (isPracticeRecordingEnabled) {
            if (isSheetMusicVisible) {
                sheetMusicView.updatePosition(getPracticeRecordingPositionMs())
            }
            startPracticeRecordingUpdatesIfNeeded()
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister broadcast receiver
        if (isReceiverRegistered) {
            try {
                requireContext().unregisterReceiver(sf2LoadCompleteReceiver)
            } catch (_: Exception) { }
            isReceiverRegistered = false
        }
        if (::playbackController.isInitialized) {
            playbackController.pause()
        }
        stopAnimation()
        pausePracticeRecordingClock()
        stopPracticeRecordingUpdates()
        // Stop MIDI input controller
        midiInputController?.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop live audio recording if active (before releasing synth)
        recordingHandler.removeCallbacks(autoStopRecordingRunnable)
        if (audioStreamRecorder.isRecording()) {
            (synthesizer as? Sf2PracticeSynth)?.setAudioTapCallback(null)
            audioStreamRecorder.stop()?.delete()
        }
        // Nettoyer le fichier temporaire si l'export etait en attente
        pendingExportWavFile?.delete()
        pendingExportWavFile = null
        stopPracticeRecordingUpdates()
        // Dismiss volume popup if open
        volumePopup?.dismiss()
        volumePopup = null
        // Ensure receiver is unregistered
        if (isReceiverRegistered) {
            try {
                requireContext().unregisterReceiver(sf2LoadCompleteReceiver)
            } catch (_: Exception) { }
            isReceiverRegistered = false
        }
        stopAnimation()

        // Clear wait mode state
        isInWaitMode = false
        waitingNotesWithTiming.clear()
        currentWaitingNoteCounts.clear()
        currentlyHeldNotes.clear()

        // Clear piano expected notes
        if (::pianoView.isInitialized) {
            pianoView.clearExpectedNotes()
            pianoView.allNotesOff()
        }

        if (::playbackController.isInitialized) {
            playbackController.release()
        }

        // Release synthesizer (SF2 will stop its AudioRenderer, Sonivox keeps driver running)
        // Send allSoundOff before releasing to cut any lingering notes
        synthesizer?.allSoundOff()
        synthesizer?.release()
        synthesizer = null

        // Restaurer le clavier externe à son état normal
        if (isMidiKeyboardConnected) {
            // Restaurer Local Control (le clavier joue ses propres sons)
            midiInputController?.sendLocalControlOn()
            // Restaurer le volume sur tous les canaux
            midiInputController?.sendAllChannelsUnmute()
        }
        midiInputController?.stop()
        midiInputController = null
    }
}
