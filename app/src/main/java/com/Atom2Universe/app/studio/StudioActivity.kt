package com.Atom2Universe.app.studio

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audio.AudioThemedActivity
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioLibrary
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.chip
import com.Atom2Universe.app.pixelart.ui.promptText
import java.io.File
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.dsp.SynthPresets
import com.Atom2Universe.app.studio.data.LiveRecorder
import com.Atom2Universe.app.studio.data.MidiPerformance
import com.Atom2Universe.app.studio.engine.Arp
import com.Atom2Universe.app.studio.ui.FaderView
import com.Atom2Universe.app.studio.export.SampleTools
import com.Atom2Universe.app.studio.export.StudioExporter
import com.Atom2Universe.app.studio.export.StudioFiles
import com.Atom2Universe.app.audioeditor.AudioEditorLibraryActivity
import com.Atom2Universe.app.audioeditor.EXTRA_IMPORT_NAME
import com.Atom2Universe.app.audioeditor.EXTRA_IMPORT_URI
import android.content.Intent
import com.Atom2Universe.app.studio.ui.KeyboardView
import com.Atom2Universe.app.studio.ui.XyPadView
import com.Atom2Universe.app.studio.ui.FmPatchPadView
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import com.Atom2Universe.app.studio.ui.StudioPanelHost
import android.util.TypedValue
import android.widget.FrameLayout
import com.Atom2Universe.app.midi.input.MidiConnectionStatus
import com.Atom2Universe.app.midi.input.MidiInputController
import com.Atom2Universe.app.midi.visualizer.GeneralMidiInstruments
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.SynthBank
import com.Atom2Universe.app.studio.engine.TrackKind
import com.Atom2Universe.app.studio.data.Track
import com.Atom2Universe.app.studio.data.PadPlayMode
import com.Atom2Universe.app.studio.engine.PadPosition
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StudioAudioOutput
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.TrackMix
import com.Atom2Universe.app.studio.music.BarRhythm
import com.Atom2Universe.app.studio.music.TapTempo
import com.Atom2Universe.app.studio.music.StudioRegister
import com.Atom2Universe.app.studio.music.Scales
import com.Atom2Universe.app.studio.music.Styles
import com.Atom2Universe.app.studio.ui.FlowRow
import com.Atom2Universe.app.studio.ui.InstrumentIcons
import com.Atom2Universe.app.studio.ui.PianoRollView
import com.Atom2Universe.app.studio.ui.StepGridView
import com.Atom2Universe.app.studio.ui.ChainView
import com.Atom2Universe.app.studio.ui.TrackClipsView
import com.Atom2Universe.app.studio.ui.PadsView
import com.Atom2Universe.app.studio.ui.ThereminView
import com.Atom2Universe.app.studio.data.MySounds
import com.Atom2Universe.app.studio.dsp.SynthPatch
import com.Atom2Universe.app.studio.dsp.SynthSample
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Le morceau que l'écran du Studio ouvre (donné par la bibliothèque). */
const val EXTRA_PROJECT_ID = "studio_project_id"

/**
 * Le Studio : l'onglet Rythme (grille de pads, styles, Hasard, effets) et l'onglet Notes (piano roll, tonalité et
 * gamme, choix du son et réglages du synthé). Toucher le titre montre ou cache les mesures du moteur.
 *
 * Toutes les rangées de commandes passent à la ligne quand la place manque ([FlowRow]) : rien n'est caché hors écran.
 */
class StudioActivity : AudioThemedActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var engine: StudioEngine
    private lateinit var output: StudioAudioOutput
    private lateinit var session: StudioSession
    private lateinit var library: StudioLibrary
    private var projectId = ""
    private var projectName = ""
    private lateinit var titleView: TextView

    /** Le soundfont de l'appli, quand il est ouvert (la première piste soundfont l'ouvre). */
    private var sf2: Sf2Bridge? = null

    /** La piste mélodique que montrent l'onglet Notes et le clavier (-1 : aucune). */
    @Volatile private var selectedTrack = -1

    /** Les pistes visibles, dans l'ordre de la grille ; [trackSignature] dit à quoi elles ressemblaient au dernier dessin. */
    private var gridIds: List<Int> = emptyList()
    private var trackSignature = ""
    private lateinit var mixerStrips: LinearLayout
    private lateinit var mixerBody: LinearLayout
    private lateinit var mixerStripView: View
    private lateinit var mixerPadBox: LinearLayout
    private lateinit var mixerPadTitle: TextView
    private lateinit var padGestureRow: FlowRow
    private lateinit var padsXyToggle: MaterialButton
    private lateinit var notesContent: LinearLayout
    private lateinit var notesEmpty: TextView
    private lateinit var trackStrip: LinearLayout
    private lateinit var notesHeader: View

    /** La piste de batterie dont l'onglet Notes montre le son (-1 : on montre les notes de la piste mélodique choisie). */
    private var editedDrum = -1
    private lateinit var drumPanel: LinearLayout
    private lateinit var drumKitRow: FlowRow
    private lateinit var drumSliders: LinearLayout
    private lateinit var drumTuneBar: SeekBar
    private lateinit var drumLengthBar: SeekBar
    private lateinit var drumTuneText: TextView
    private lateinit var drumLengthText: TextView
    /** Les commandes de Notes qui n'ont de sens que s'il y a une piste mélodique. */
    private val trackDependent = ArrayList<View>()
    private val mixerIds = ArrayList<Int>()

    // Commun
    private lateinit var playButton: MaterialButton
    private lateinit var tempoText: TextView
    private lateinit var tabsGroup: MaterialButtonToggleGroup
    private lateinit var stats: LinearLayout
    private lateinit var statsText: TextView
    private lateinit var bufferButton: MaterialButton
    private lateinit var rhythmPane: LinearLayout
    private lateinit var notesPane: LinearLayout
    private lateinit var padsBody: LinearLayout
    private var padsShowXY = false
    private lateinit var mixerPane: LinearLayout
    private lateinit var padView: XyPadView
    private var fmSoundPad: XyPadView? = null
    private lateinit var padLoopRow: LinearLayout
    private lateinit var loopNames: Array<String>
    private var shownPadLoop = -2
    private var shownPendingLoop = -2
    private var launchEnabled = false
    private var clipPanel: TrackClipsView? = null
    private var shownClipRevision = -1L
    private var queuedPattern: com.Atom2Universe.app.studio.engine.StepPattern? = null
    private lateinit var patternGroup: MaterialButtonToggleGroup
    private var songBlocks: ChainView? = null
    private lateinit var songButton: MaterialButton
    private var songDefaultColor = 0
    private val patternIds = List(StudioProject.SLOTS) { View.generateViewId() }
    private lateinit var holdPadSwitch: SwitchCompat
    private lateinit var gestureRecordButton: MaterialButton
    private lateinit var gestureClearButton: MaterialButton
    private var gestureArmed = false
    private var gestureTaking = false
    private val levelFaders = ArrayList<FaderView>()
    private val muteButtons = ArrayList<MaterialButton>()
    private val soloButtons = ArrayList<MaterialButton>()
    private val tabMixerId = View.generateViewId()

    // Rythme
    private lateinit var panels: StudioPanelHost
    private lateinit var grid: StepGridView

    // Notes
    private lateinit var roll: PianoRollView
    private lateinit var keyButton: MaterialButton
    private lateinit var scaleButton: MaterialButton
    private lateinit var lockSwitch: SwitchCompat
    private lateinit var soundButton: MaterialButton
    private lateinit var keyboard: KeyboardView
    private lateinit var recordButton: MaterialButton
    private lateinit var midiText: TextView
    private lateinit var arpButton: MaterialButton
    private lateinit var recorder: LiveRecorder
    private val midiLights = HashMap<Int, IntArray>()
    private val keyboardTracks = HashMap<Int, Int>()
    @Volatile private var takeRevision = 0L
    private var midiPerformance: MidiPerformance? = null
    private var midi: MidiInputController? = null
    private val waveIds = IntArray(4) { View.generateViewId() }

    private val tabRhythmId = View.generateViewId()
    private val tabNotesId = View.generateViewId()

    private val handler = Handler(Looper.getMainLooper())
    private var playing = false
        set(value) {
            if (value) stopSoundAudition()
            field = value
        }
    private var bufferBlocks = 2
    private var screenActive = false
    private var framePending = false
    private val statsTick = object : Runnable {
        override fun run() {
            if (screenActive && stats.visibility == View.VISIBLE) {
                refreshStats()
                handler.postDelayed(this, 250)
            }
        }
    }
    private var building = true
    private var scaleLocked = true
    private var requestedOctaves: Int? = null
    private var manualRegister = false
    private lateinit var register: StudioRegister
    private lateinit var registerButton: MaterialButton
    private var onNotesTab = false

    private val saveRunnable = Runnable { session.flush(); refreshPatterns() }

    // Pads de batterie (onglet Rythme) et leur enregistrement
    private lateinit var padsPane: LinearLayout
    private lateinit var padsStage: FrameLayout
    private lateinit var padModeAnchor: View
    private lateinit var pads: PadsView

    /** Vrai : rester sur une case de batterie la fait rejouer à chaque double-croche. */
    private var padsRoll = false
    private lateinit var padsRecordButton: MaterialButton
    private val tabPadsId = View.generateViewId()
    private var padsRecording = false

    // Pad thérémine (onglet Notes)
    private lateinit var thereminToggle: MaterialButton
    private lateinit var theremin: ThereminView
    private var thereminOn = false
    private var thereminTrack = -1

    /** Après une note du thérémine, la piste retrouve son expression normale (une fois la note éteinte). */

    private lateinit var mySounds: MySounds
    private var shownCountIn = 0

    private val frameTick = object : Runnable {
        override fun run() {
            framePending = false
            if (padLoopRow.visibility == View.VISIBLE) {
                acceptPadLaunch()
                refreshPadLauncher()
            }
            val step = if (playing) engine.currentStep else -1
            songBlocks?.playingIndex = if (playing && session.project.songMode) engine.songPosition else -1
            if (onNotesTab) roll.playhead = step else if (rhythmPane.visibility == View.VISIBLE) grid.playhead = step
            // Le décompte s'affiche sur le bouton de lecture : 4, 3, 2, 1
            val countIn = if (playing) engine.countInBeats else 0
            if (countIn != shownCountIn) {
                shownCountIn = countIn
                if (countIn > 0) playButton.text = countIn.toString() else refreshTransport()
            }
            drainPadHits()
            if (::padView.isInitialized) {
                if (padView.isTouching) {
                    if (gestureTaking) session.recordGesture(engine.playPosition, engine.masterFilter, engine.spaceBoost)
                } else if (padView.isShown) showPadPosition()
            }
            if (playing || pads.hasHeldDrum) requestFrame()
        }
    }

    private fun requestFrame() {
        if (screenActive && !framePending) {
            framePending = true
            grid.postOnAnimation(frameTick)
        }
    }

    private fun drainPadHits() {
        // Les coups joués aux pads pendant l'enregistrement s'écrivent dans la grille
        var wrote = false
        engine.takeLiveHitsWithSlot { track, position, velocity, slot ->
            if (padsRecording) {
                session.recordHit(track, position, velocity, slot)
                wrote = true
            }
            if (::pads.isInitialized) pads.flash(track)
        }
        if (wrote) {
            grid.invalidate()
            scheduleSave()
        }
    }

    /** Un réglage du son : un curseur 0..1000 qui lit et écrit une valeur réelle par une correspondance (souvent logarithmique). */
    private class SoundParam(
        val labelRes: Int,
        val read: () -> Float,
        val toSlider: (Float) -> Float,
        val fromSlider: (Float) -> Float,
        val write: (Float) -> Unit,
    ) {
        lateinit var bar: SeekBar
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()

        engine = StudioEngine(StudioAudioOutput.preferredSampleRate(this))
        library = StudioLibrary(File(filesDir, "studio/songs"))
        val entry = intent.getStringExtra(EXTRA_PROJECT_ID)?.let { library.load(it) }
        if (entry == null) {
            finish()
            return
        }
        projectId = entry.id
        projectName = entry.name
        SampleTools.load(this)?.let { (data, rate) -> engine.setPercSample(data, rate) }
        loadSynthSample()
        mySounds = MySounds(File(filesDir, "studio/my_sounds.json"))
        val loaded = entry.project
        // Un projet qui a des pistes soundfont ouvre le soundfont avant que le moteur fabrique leurs instruments
        if (loaded.tracks.any { it.kind == TrackKind.SF2 || it.sfKit >= 0 }) {
            sf2 = Sf2Bridge.open(this)
            engine.sf2Factory = sf2?.factory
        }
        session = StudioSession(engine, loaded, entry.history, library.journal(projectId))
        output = StudioAudioOutput(engine)
        recorder = LiveRecorder(session, { engine.playing }, { engine.playPosition }, { selectedTrack })
        ensureSelected()
        register = newRegister()
        focusRegister()

        setContentView(buildContent())
        building = false
        refreshAll()
        refreshHistory()
    }

    override fun onResume() {
        super.onResume()
        if (!::output.isInitialized) return
        if (!output.start(this)) {
            stats.visibility = View.VISIBLE
            statsText.setText(R.string.studio_audio_failed)
        }
        output.setBufferBlocks(bufferBlocks)
        screenActive = true
        requestFrame()
        statsTick.run()
        startMidi()
    }

    override fun onPause() {
        if (!::output.isInitialized) { super.onPause(); return }
        screenActive = false
        framePending = false
        grid.removeCallbacks(frameTick)
        handler.removeCallbacks(statsTick)
        cancelPadLaunch()
        // Rien ne reste tenu : pads, thérémine, enregistrement des pads
        padView.finishTouch(false)
        fmSoundPad?.finishTouch(false)
        pads.releaseAll()
        keyboard.releaseAll()
        theremin.finish()
        padExpressionResets.keys.toList().forEach(::clearPendingPadEffects)
        stopSoundAudition()
        for (release in presetReleases.values.toList()) {
            handler.removeCallbacks(release)
            release.run()
        }
        setPadsRecording(false)
        stopLive()
        handler.removeCallbacks(saveRunnable)
        session.flush()
        engine.stop()
        playing = false
        refreshTransport()
        output.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (::engine.isInitialized) engine.close()
        super.onDestroy()
    }

    // ---- Construction de l'écran ---------------------------------------------------------------

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(12)
            setPadding(pad, dp(24), pad, pad)
        }

        // Titre et onglets
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back_24)
            val tv = TypedValue()
            theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
            if (tv.resourceId != 0) setColorFilter(getColorStateList(tv.resourceId).defaultColor)
            val ripple = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
            if (ripple.resourceId != 0) setBackgroundResource(ripple.resourceId) else background = null
            contentDescription = getString(R.string.studio_back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(4) })
        titleView = TextView(this).apply {
            text = projectName
            textSize = 22f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
            // Toucher le nom le change ; un appui long montre ou cache les mesures du moteur
            setOnClickListener {
                promptText(R.string.studio_rename, projectName, android.R.string.ok) { name ->
                    if (name.isNotEmpty()) {
                        projectName = name
                        library.rename(projectId, name)
                        text = name
                    }
                }
            }
            setOnLongClickListener {
                stats.visibility = if (stats.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                output.resetMax()
                handler.removeCallbacks(statsTick)
                statsTick.run()
                true
            }
        }
        titleRow.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // Annuler / rétablir : deux flèches au milieu de l'en-tête, à côté des onglets
        undoButton = historyButton(R.drawable.ic_studio_undo, R.string.studio_undo) { stepHistory(redo = false) }
        redoButton = historyButton(R.drawable.ic_studio_redo, R.string.studio_redo) { stepHistory(redo = true) }
        titleRow.addView(undoButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        titleRow.addView(redoButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        tabsGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            addView(outlined(R.string.studio_tab_rhythm).apply { id = tabRhythmId })
            addView(outlined(R.string.studio_pads).apply { id = tabPadsId })
            addView(outlined(R.string.studio_tab_notes).apply { id = tabNotesId })
            addView(outlined(R.string.studio_tab_mixer).apply { id = tabMixerId })
            check(tabRhythmId)
            addOnButtonCheckedListener { _, id, checked ->
                if (checked) showTab(id)
            }
        }
        titleRow.addView(tabsGroup, wrap())
        titleRow.addView(square(R.string.studio_help_button).apply {
            setOnClickListener {
                MaterialAlertDialogBuilder(this@StudioActivity)
                    .setTitle(R.string.studio_help_title)
                    .setMessage(helpText())
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        })
        root.addView(titleRow)
        layoutTabs()

        // Transport commun aux trois pages : lecture, tempo, motifs A à D, morceau, réglages
        val transport = flowRow(topMargin = dp(10))
        playButton = MaterialButton(this).apply {
            isAllCaps = false
            letterSpacing = 0.02f
            insetTop = 0
            insetBottom = 0
            minimumHeight = dp(48)
            setOnClickListener { togglePlay() }
        }
        transport.addView(playButton, wrap(end = dp(8)))
        val tempo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tempo.addView(square(R.string.studio_tempo_less).also { holdToRepeat(it) { session.setBpm(session.project.bpm - 1); refreshTempo(); scheduleSave() } })
        tempoText = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            minWidth = dp(76)
            // Un toucher sur le tempo déroule les allures les plus courantes, du lent au très rapide
            val ripple = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            setBackgroundResource(ripple.resourceId)
            minHeight = dp(48)
            setOnClickListener { showTempoPanel(it) }
        }
        tempo.addView(tempoText)
        tempo.addView(square(R.string.studio_tempo_more).also { holdToRepeat(it) { session.setBpm(session.project.bpm + 1); refreshTempo(); scheduleSave() } })
        transport.addView(tempo, wrap(end = dp(8)))

        // Motifs A à D, morceau
        val names = resources.getStringArray(R.array.studio_pattern_names)
        patternGroup = MaterialButtonToggleGroup(this).apply {
            // On coche les motifs qu'on veut voir à la suite ; un reste toujours coché
            isSingleSelection = false
            isSelectionRequired = true
            for (k in 0 until StudioProject.SLOTS) {
                val b = outlined(null).apply { id = patternIds[k]; text = names[k]; minWidth = dp(44); minimumWidth = dp(44); setPadding(dp(8), 0, dp(8), 0) }
                addView(b)
            }
            addOnButtonCheckedListener { group, _, _ ->
                if (!building) {
                    setPasteArmed(false)
                    finishTakeSegment()
                    session.setView(patternIds.indices.filter { patternIds[it] in group.checkedButtonIds })
                    // La grille et le piano roll changent de longueur : tout se redessine, la sélection n'a plus de sens
                    grid.clearSelection()
                    roll.clearSelection()
                    changed()
                }
            }
        }
        transport.addView(patternGroup, wrap(end = dp(8)))
        loopNames = names
        padLoopRow = LinearLayout(this).apply {
            visibility = View.GONE
            names.forEachIndexed { slot, name ->
                addView(outlined(null).apply {
                    text = name
                    minWidth = dp(44)
                    minimumWidth = dp(44)
                    isCheckable = true
                    setPadding(dp(6), 0, dp(6), 0)
                    setOnClickListener { launchPadLoop(slot) }
                }, wrap(end = dp(2)))
            }
        }
        transport.addView(padLoopRow, wrap(end = dp(8)))
        selectToggle = buildSelectToggle()
        transport.addView(selectToggle, wrap(end = dp(8)))
        songButton = outlined(R.string.studio_song).apply { setOnClickListener { showSongPanel(it) } }
        transport.addView(songButton, wrap(end = dp(8)))
        songDefaultColor = songButton.textColors.defaultColor
        transport.addView(square(R.string.studio_more).also {
            it.contentDescription = getString(R.string.studio_settings)
            it.setOnClickListener { v -> showSettingsPanel(v) }
        })
        root.addView(transport)
        selectionBar = buildSelectionBar()
        root.addView(selectionBar)

        // Mesures du moteur (cachées par défaut)
        stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        statsText = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
        }
        stats.addView(statsText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bufferButton = outlined(null).also {
            it.setOnClickListener {
                bufferBlocks = if (bufferBlocks >= 4) 2 else bufferBlocks + 1
                output.setBufferBlocks(bufferBlocks)
                output.resetMax()
                refreshAll()
            }
        }
        stats.addView(bufferButton, wrap())
        root.addView(stats, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) })

        rhythmPane = buildRhythmPane()
        padsPane = buildPadsPane().also { it.visibility = View.GONE }
        notesPane = buildNotesPane().also { it.visibility = View.GONE }
        mixerPane = buildMixerPane().also { it.visibility = View.GONE }
        root.addView(rhythmPane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(padsPane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(notesPane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(mixerPane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val back = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = panels.closeTop()
        }
        onBackPressedDispatcher.addCallback(this, back)
        panels = StudioPanelHost(this).apply {
            addView(root)
            onCountChanged = { back.isEnabled = it > 0 }
        }
        return panels
    }

    private fun layoutTabs() {
        val titleRow = titleView.parent as LinearLayout
        val root = titleRow.parent as LinearLayout
        val target = if (resources.configuration.screenWidthDp < 520) root else titleRow
        if (tabsGroup.parent !== target) {
            (tabsGroup.parent as ViewGroup).removeView(tabsGroup)
            val index = if (target === root) 1 else titleRow.childCount - 1
            target.addView(tabsGroup, index, wrap())
        }
        val compact = resources.configuration.screenWidthDp < 680
        for (i in 0 until tabsGroup.childCount) {
            val b = tabsGroup.getChildAt(i) as MaterialButton
            b.minWidth = dp(if (compact) 56 else 88)
            b.minimumWidth = b.minWidth
            b.setPadding(dp(if (compact) 8 else 16), 0, dp(if (compact) 8 else 16), 0)
        }
    }

    private fun buildRhythmPane(): LinearLayout {
        val pane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        grid = StepGridView(this).apply {
            stepCount = session.project.stepCount
            addLabel = getString(R.string.studio_tracks_add)
            isOn = { lane, step -> gridIds.getOrNull(lane)?.let { session.isOn(it, step) } ?: false }
            velocity = { lane, step -> gridIds.getOrNull(lane)?.let { session.velocity(it, step) } ?: 0f }
            isMuted = { lane -> gridIds.getOrNull(lane)?.let { session.isMuted(it) } ?: false }
            isTail = { lane, step -> gridIds.getOrNull(lane)?.let { session.isTail(it, step) } ?: false }
            cond = { lane, step -> gridIds.getOrNull(lane)?.let { session.stepCond(it, step) } ?: 0 }
            condMarks = resources.getStringArray(R.array.studio_cond_marks).toList()
            listener = object : StepGridView.Listener {
                override fun onStepSet(lane: Int, step: Int, on: Boolean) {
                    gridIds.getOrNull(lane)?.let { id ->
                        session.setStep(id, step, on)
                        if (on) applyPen(id, step)
                    }
                    scheduleSave()
                }

                override fun onSelectionChanged() = refreshSelectionBar()

                override fun onPasteAt(lane: Int, step: Int) = updatePasteTarget()

                override fun onStepEdit(lane: Int, step: Int, screenX: Float, screenY: Float) {
                    gridIds.getOrNull(lane)?.let { showStepBubble(it, step, screenX, screenY) }
                }

                override fun onPreview(lane: Int, step: Int) {
                    gridIds.getOrNull(lane)?.let { previewLane(it, step) }
                }

                override fun onMuteToggle(lane: Int) {
                    gridIds.getOrNull(lane)?.let { session.setMuted(it, !session.isMuted(it)) }
                    scheduleSave()
                }

                override fun onAddTrack() = showAddTrackSheet()

                override fun onMoveLane(from: Int, to: Int) {
                    val id = gridIds.getOrNull(from) ?: return
                    val target = gridIds.getOrNull(to) ?: return
                    session.moveTrack(id, session.project.tracks.indexOfFirst { it.id == target })
                    changed()
                }
            }
        }
        pane.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
        return pane
    }

    /**
     * L'onglet Pads : une case par piste. Batterie : toucher = un coup, rester = roulement en rythme. Piste à notes : la case est
     * coupée en niveaux (les notes de la gamme, le grave en bas) ; la note tient tant que le doigt reste.
     */
    private fun buildPadsPane(): LinearLayout {
        val pane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = flowRow(topMargin = dp(8))
        padsRecordButton = outlined(R.string.studio_record_off).apply { setOnClickListener { setPadsRecording(!padsRecording) } }
        header.addView(padsRecordButton, wrap(end = dp(8)))
        // Roulement : un doigt qui reste sur une case de batterie la fait rejouer en rythme (sinon, un seul coup). Gardé d'une fois sur l'autre.
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        padsRoll = prefs.getBoolean(PREF_PADS_ROLL, false)
        padsShowXY = prefs.getBoolean(PREF_PADS_SHOW_XY, false)
        val rates = resources.getStringArray(R.array.studio_roll_rates)
        var rollRate = prefs.getInt(PREF_ROLL_RATE, 0).coerceIn(rates.indices)
        val rateButton = outlined(null).apply {
            text = rates[rollRate]
            visibility = if (padsRoll) View.VISIBLE else View.GONE
            setOnClickListener { v ->
                dropdown(v, rates.toList()) { which ->
                    rollRate = which
                    text = rates[which]
                    prefs.edit().putInt(PREF_ROLL_RATE, which).apply()
                }
            }
        }
        header.addView(outlined(R.string.studio_roll).apply {
            isCheckable = true
            isChecked = padsRoll
            setOnClickListener {
                padsRoll = isChecked
                rateButton.visibility = if (padsRoll) View.VISIBLE else View.GONE
                prefs.edit().putBoolean(PREF_PADS_ROLL, padsRoll).apply()
            }
        }, wrap(end = dp(8)))
        header.addView(rateButton)
        header.addView(outlined(R.string.studio_clips).apply { setOnClickListener { showTrackClips(it) } })
        header.addView(outlined(R.string.studio_pad_sounds).apply { setOnClickListener { showPadSounds(it) } })
        header.addView(outlined(R.string.studio_pad_arrange).apply {
            isCheckable = true
            setOnClickListener { pads.editing = isChecked }
        })
        pane.addView(header)
        pads = PadsView(this).apply {
            noteNames = resources.getStringArray(R.array.studio_key_names).toList()
            onDown = { track ->
                stopSoundAudition()
                if (padsRoll) engine.rollOn(track, 1f, 1 shl rollRate) else engine.trigger(track, 60, 1f)
                requestFrame()
            }
            onUp = { track -> engine.rollOff(track) }
            onNoteDown = { track, pitch -> padNoteDown(track, pitch) }
            onNoteUp = { track, pitch -> padNoteUp(track, pitch) }
            onNoteEffect = { track, amount ->
                when (session.project.trackOrNull(track)?.padPlayMode) {
                    PadPlayMode.DRIVE -> engine.setLiveDrive(track, amount)
                    PadPlayMode.FILTER -> engine.setLiveFilter(track, amount)
                    else -> Unit
                }
            }
            onChooseMode = ::showPadModeMenu
            onGlide = ::glideLiveNote
            onThereminExpression = { track, amount ->
                if (!session.isMuted(track)) {
                    clearPendingPadEffects(track)
                    engine.setExpression(track, amount)
                }
            }
            onReorder = { from, to ->
                session.movePad(from, to)
                refreshPads()
                scheduleSave()
            }
            onResize = { id ->
                session.resizePad(id)
                refreshPads()
                scheduleSave()
            }
            onToggleHalf = { id ->
                session.togglePadHalf(id)
                refreshPads()
                scheduleSave()
            }
        }
        padModeAnchor = View(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        padsStage = FrameLayout(this).apply {
            addView(pads, FrameLayout.LayoutParams(-1, -1))
            addView(padModeAnchor, FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.LEFT))
        }
        padsBody = LinearLayout(this).apply {
            isMotionEventSplittingEnabled = true
            addView(padsStage)
        }
        pane.addView(padsBody, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
        return pane
    }

    private fun showPadModeMenu(id: Int, x: Int, y: Int) {
        val track = session.project.trackOrNull(id) ?: return
        padModeAnchor.translationX = x.coerceIn(0, pads.width - 1).toFloat()
        padModeAnchor.translationY = y.coerceIn(0, pads.height - 1).toFloat()
        val menu = androidx.appcompat.widget.PopupMenu(this, padModeAnchor)
        for (mode in PadPlayMode.entries) {
            val name = getString(StudioNames.padMode(mode))
            val title = if (mode == PadPlayMode.THEREMIN) name else getString(R.string.studio_pad_mode_keyboard, name)
            menu.menu.add(0, mode.ordinal, mode.ordinal, title)
        }
        menu.menu.setGroupCheckable(0, true, true)
        menu.menu.findItem(track.padPlayMode.ordinal).isChecked = true
        menu.setOnMenuItemClickListener { item ->
            val mode = PadPlayMode.entries[item.itemId]
            if (track.padPlayMode != mode) {
                session.setPadPlayMode(id, mode)
                refreshPads()
                clearPendingPadEffects(id)
                scheduleSave()
            }
            true
        }
        menu.show()
    }

    private fun showPadSounds(anchor: View) {
        val tracks = visibleTracks()
        dropdown(anchor, tracks.map(::trackName) + getString(R.string.studio_tracks_add)) { choice ->
            val t = tracks.getOrNull(choice)
            when {
                t == null -> showAddTrackSheet()
                t.melodic -> showSoundPanel(anchor, t.id)
                else -> showDrumSound(anchor, t.id)
            }
        }
    }

    private fun showDrumSound(anchor: View, id: Int) {
        val t = session.project.trackOrNull(id) ?: return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(12))
        }
        val kits = resources.getStringArray(KIT_NAMES[t.voice])
        val picker = outlined(null).apply {
            text = if (t.sfKit >= 0) getString(R.string.studio_drum_soundfont) else kits[t.kit]
            setOnClickListener { button ->
                dropdown(button, kits.toList() + getString(R.string.studio_drum_soundfont)) { choice ->
                    if (choice == kits.size) pickDrumKit(id) { showDrumSound(anchor, id) }
                    else {
                        val now = session.project.trackOrNull(id) ?: return@dropdown
                        session.setDrumSoundfont(id, -1)
                        session.setDrumVoice(id, choice, now.tune, now.length)
                        previewTrack(id)
                        scheduleSave()
                        showDrumSound(anchor, id)
                    }
                }
            }
        }
        box.addView(picker, wrap())
        if (t.sfKit < 0) {
            fun control(title: Int, max: Int, value: Int, change: (Track, Int) -> Unit) {
                val bar = slider(max) { v ->
                    session.project.trackOrNull(id)?.let { change(it, v) }
                    scheduleSave()
                }.apply { progress = value; contentDescription = getString(title) }
                box.addView(labeled(title, bar))
            }
            control(R.string.studio_drum_tune, 24, t.tune + 12) { now, v -> session.setDrumVoice(id, now.kit, v - 12, now.length) }
            control(R.string.studio_drum_length, 150, t.length - 50) { now, v -> session.setDrumVoice(id, now.kit, now.tune, v + 50) }
        }
        val actions = FlowRow(this).apply { hGap = dp(6) }
        actions.addView(outlined(R.string.studio_drum_listen).apply { setOnClickListener { previewTrack(id) } })
        actions.addView(outlined(R.string.studio_fx).apply { setOnClickListener { showFxPanel(it, id, trackName(t)) } })
        actions.addView(outlined(R.string.studio_spread).apply { setOnClickListener { showSpread(it, id) } })
        box.addView(actions)
        panels.show(R.string.studio_sound_caption, getString(R.string.studio_panel_track,
            getString(R.string.studio_sound_caption), trackName(t)), box, 320, anchor,
            onHistory = { showDrumSound(anchor, id) }) { session.project.trackOrNull(id)?.hidden == false }
    }

    /** Les doigts posés sur les touches de chaque piste, et le retour au son net une fois tous levés. */
    private val padFingers = HashMap<Int, Int>()
    private val padExpressionResets = HashMap<Int, Runnable>()

    private fun neutralPadEffects(track: Int) {
        engine.setLiveDrive(track, 0f)
        engine.setLiveFilter(track, 0f)
        engine.setExpression(track, 1f)
    }

    private fun clearPendingPadEffects(track: Int) {
        val reset = padExpressionResets.remove(track) ?: return
        handler.removeCallbacks(reset)
        neutralPadEffects(track)
    }

    private fun resetAfterRelease(track: Int) {
        padExpressionResets.remove(track)?.let { handler.removeCallbacks(it) }
        val reset = Runnable {
            padExpressionResets.remove(track)
            neutralPadEffects(track)
        }
        padExpressionResets[track] = reset
        handler.postDelayed(reset, 1500)
    }

    private fun glideLiveNote(track: Int, from: Int, to: Int) {
        val position = engine.playPosition
        endRecordedNote(track, from, position)
        if (!session.isMuted(track)) {
            engine.glide(track, from, to, 0.9f)
            recorder.press(to, position, track, 0.9f)
        }
    }

    /**
     * Une note posée sur une case à notes : elle sonne tout de suite, avec le son normal (c'est le doigt qui bouge qui distord),
     * et s'enregistre sur sa piste si la prise est en cours.
     */
    private fun padNoteDown(track: Int, pitch: Int) {
        if (session.isMuted(track)) return
        stopSoundAudition()
        clearPendingPadEffects(track)
        val fingers = padFingers[track] ?: 0
        padFingers[track] = fingers + 1
        engine.trigger(track, pitch, 0.9f)
        recorder.press(pitch, engine.playPosition, track, 0.9f)
    }

    private fun padNoteUp(track: Int, pitch: Int) {
        engine.release(track, pitch)
        endRecordedNote(track, pitch, engine.playPosition)
        roll.invalidate()
        val left = ((padFingers[track] ?: 1) - 1).coerceAtLeast(0)
        if (left == 0) {
            padFingers.remove(track)
            resetAfterRelease(track)
        } else padFingers[track] = left
    }

    /**
     * Les niveaux d'une case à notes : une octave de la gamme, de tonique à tonique, dans le registre de la piste (celui de
     * ses notes ; sans note, l'octave du milieu pour un synthé de mélodie).
     */
    private fun padLevels(track: Track): IntArray {
        val p = session.project
        return StudioRegister.padPitches(session.notesOf(track.id), Scales.byId(p.scaleId), p.keyPc, StudioRegister.chipOctave(track))
    }

    /** Les cases : une par piste visible (batterie ou notes), dans l'ordre des pistes. */
    private fun refreshPads() {
        if (!::pads.isInitialized) return
        pads.keyPc = session.project.keyPc
        pads.tiles = session.padTracks().filterNot { it.hidden }.map { t ->
            PadsView.Tile(t.id, trackColor(t), InstrumentIcons.of(t), if (t.melodic) padLevels(t) else null, t.padSize, trackName(t), t.padPlayMode)
        }
    }

    /**
     * Enregistrer aux pads : les coups s'écrivent dans la boucle sur le pas le plus proche, et ce qu'on joue sur la zone synthé
     * devient des notes. À l'arrêt, une mesure de décompte précède la lecture ; le métronome bat pendant toute la prise.
     */
    private fun setPadsRecording(on: Boolean) {
        if (!::padsRecordButton.isInitialized) return
        if (on) {
            cancelPadLaunch()
            engine.cancelPendingTrackClips()
        }
        if (padsRecording != on) drainPadHits()
        padsRecording = on
        padsRecordButton.setText(if (on) R.string.studio_record_on else R.string.studio_record_off)
        padsRecordButton.setBackgroundColor(if (on) 0x66E74C3C else android.graphics.Color.TRANSPARENT)
        if (::recorder.isInitialized && recorder.armed != on) {
            finishTakeSegment()
            recorder.armed = on
            scheduleSave()
        }
        refreshMetronome()
        if (on && !playing) {
            playing = true
            engine.play(countIn = true)
            refreshTransport()
        }
    }

    /** Le métronome bat tant qu'on enregistre (aux pads ou au clavier). */
    private fun refreshMetronome() {
        engine.metronome = padsRecording || (::recorder.isInitialized && recorder.armed)
    }

    /** Le mixeur : une tranche par piste (nom, volume, panoramique, muet, solo, effets) et un pad XY pour jouer le filtre et la réverbération. */
    private fun buildMixerPane(): LinearLayout {
        val pane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val strips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        mixerStrips = strips
        val strip = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            // Sur un écran large, les tranches prennent toute la hauteur (des faders plus longs, plus précis)
            isFillViewport = true
            addView(strips, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        // Pad XY : le doigt ferme le filtre (vers la gauche) et ajoute de la réverbération (vers le haut)
        val pad = XyPadView(this).apply {
            accent = 0xFF7C8CFF.toInt()
            onPress = {
                engine.padActive = true
                // Prise : le geste s'écrit dans la boucle tant que le doigt reste (seulement pendant la lecture)
                if (gestureArmed && playing) {
                    cancelPadLaunch()
                    session.beginGesture()
                    gestureTaking = true
                    recording = true
                }
            }
            onMove = XyPadView.MoveListener { x, y ->
                engine.masterFilter = 1f - x
                engine.spaceBoost = y
            }
            onRelease = { completed ->
                if (completed && session.project.heldPad != null) {
                    session.setHeldPad(PadPosition(engine.masterFilter, engine.spaceBoost))
                    scheduleSave()
                }
                engine.masterFilter = 0f
                engine.spaceBoost = 0f
                engine.padActive = false
                if (gestureTaking) {
                    gestureTaking = false
                    recording = false
                    session.endGesture()
                    refreshGestureButtons()
                    scheduleSave()
                }
                showPadPosition()
            }
        }
        padView = pad
        val xyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mixerPadTitle = TextView(this).apply {
            setText(R.string.studio_xy_title)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        }
        xyBox.addView(mixerPadTitle)
        xyBox.addView(pad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        padGestureRow = flowRow(topMargin = dp(8)).apply { vGap = dp(4) }
        padsXyToggle = outlined(R.string.studio_xy_title).apply {
            visibility = View.GONE
            isCheckable = true
            isChecked = padsShowXY
            iconSize = dp(20)
            iconPadding = dp(4)
            setOnClickListener {
                padsShowXY = isChecked
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_PADS_SHOW_XY, isChecked).apply()
                placePerformancePad()
                showPadPosition()
            }
        }
        padGestureRow.addView(padsXyToggle)
        holdPadSwitch = SwitchCompat(this).apply {
            setText(R.string.studio_pad_hold)
            isChecked = session.project.heldPad != null
            setOnClickListener {
                val fixed = if (isChecked) PadPosition(1f - pad.padX, pad.padY) else null
                pad.finishTouch(false)
                if (fixed != null) {
                    gestureArmed = false
                    gestureRecordButton.isChecked = false
                }
                session.setHeldPad(fixed)
                showPadPosition()
                scheduleSave()
            }
        }
        padGestureRow.addView(holdPadSwitch)
        gestureRecordButton = outlined(R.string.studio_gesture_record).apply {
            isCheckable = true
            setOnClickListener {
                pad.finishTouch(false)
                gestureArmed = isChecked
                if (gestureArmed && session.project.heldPad != null) {
                    session.setHeldPad(null)
                    holdPadSwitch.isChecked = false
                    showPadPosition()
                    scheduleSave()
                }
                if (gestureArmed && !playing) {
                    // Pour enregistrer, il faut que ça tourne : on lance la lecture
                    playing = true
                    engine.play()
                    refreshTransport()
                }
            }
        }
        gestureClearButton = outlined(R.string.studio_gesture_clear).apply {
            setOnClickListener {
                session.clearGesture()
                refreshGestureButtons()
                scheduleSave()
            }
        }
        padGestureRow.addView(gestureRecordButton)
        padGestureRow.addView(gestureClearButton)
        xyBox.addView(padGestureRow)

        // Les tranches et le pad : côte à côte sur un écran large, l'un sous l'autre sinon (voir applyMixerLayout)
        mixerStripView = strip
        mixerPadBox = xyBox
        mixerBody = LinearLayout(this)
        mixerBody.addView(strip)
        mixerBody.addView(xyBox)
        applyMixerLayout()
        pane.addView(mixerBody, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) })
        return pane
    }

    /**
     * Range les tranches du mixeur et le pad selon la largeur de l'écran *maintenant* : l'activité n'est pas recréée à la
     * rotation, donc ce choix se refait à chaque changement de configuration.
     */
    private fun applyMixerLayout() {
        val config = resources.configuration
        val wide = config.screenWidthDp >= 650 && config.screenWidthDp > config.screenHeightDp
        mixerBody.orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        // Large : les tranches prennent toute la hauteur ; étroit : elles gardent leur taille et le pad prend ce qui reste
        mixerStripView.layoutParams =
            if (wide) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f)
            else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.35f)
        if (wide != mixerWide) {
            mixerWide = wide
            if (::session.isInitialized && !building) rebuildMixer()
        }
        if (mixerPadBox.parent === mixerBody) mixerPadBox.layoutParams =
            if (wide) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(24)
                marginEnd = dp(16)
                bottomMargin = dp(12)
            } else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(16)
                bottomMargin = dp(12)
            }
        mixerBody.requestLayout()
    }

    /** Le même pad, le même maintien et la même prise de geste dans les deux onglets. */
    private fun placePerformancePad() {
        val onPads = padsPane.visibility == View.VISIBLE
        val docked = onPads && padsShowXY
        val target = if (docked) padsBody else mixerBody
        if (mixerPadBox.parent !== target) {
            padView.finishTouch(false)
            (mixerPadBox.parent as? ViewGroup)?.removeView(mixerPadBox)
            target.addView(mixerPadBox)
        }
        // Le pied reste sous les tuiles dans les deux orientations, même quand le pad est replié.
        val footerParent = if (onPads) padsPane else mixerPadBox
        if (padGestureRow.parent !== footerParent) {
            (padGestureRow.parent as? ViewGroup)?.removeView(padGestureRow)
            footerParent.addView(padGestureRow)
        }
        mixerPadTitle.visibility = if (docked) View.GONE else View.VISIBLE
        padsXyToggle.visibility = if (onPads) View.VISIBLE else View.GONE
        if (onPads) {
            padsXyToggle.setIconResource(if (docked) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up)
            padsXyToggle.contentDescription = getString(if (docked) R.string.studio_xy_fold else R.string.studio_xy_unfold)
        }
        val controls = if (onPads && !docked) View.GONE else View.VISIBLE
        holdPadSwitch.visibility = controls
        gestureRecordButton.visibility = controls
        gestureClearButton.visibility = controls
        val config = resources.configuration
        val wide = config.screenWidthDp >= 650 && config.screenWidthDp > config.screenHeightDp
        padsBody.orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        fun part(weight: Float) = if (wide) LinearLayout.LayoutParams(0, -1, weight) else LinearLayout.LayoutParams(-1, 0, weight)
        padsStage.layoutParams = part(2f)
        if (docked) mixerPadBox.layoutParams = part(1.2f).apply {
            if (wide) marginStart = dp(12) else topMargin = dp(6)
        } else applyMixerLayout()
    }

    /** Vrai quand le mixeur est rangé côte à côte (tranches pleine hauteur, pad à droite). */
    private var mixerWide = false

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::tabsGroup.isInitialized) layoutTabs()
        if (::mixerBody.isInitialized) { applyMixerLayout(); placePerformancePad() }
        applyNotesLayout()
        if (::theremin.isInitialized) {
            val low = register.low
            register = newRegister()
            if (manualRegister) register.setLow(low) else focusRegister()
            refreshRoll()
        }
    }

    /** Les tranches du mixeur : une par piste visible (nom, volume, panoramique, muet, solo, effets). */
    private fun rebuildMixer() {
        mixerStrips.removeAllViews()
        levelFaders.clear()
        muteButtons.clear()
        soloButtons.clear()
        mixerIds.clear()
        for (t in visibleTracks()) {
            val lane = t.id
            val color = trackColor(t)
            val title = trackName(t)
            val strip = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            strip.addView(TextView(this).apply {
                text = title
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 2
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(4), dp(6), dp(4), dp(6))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(color)
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

            strip.addView(TextView(this).apply {
                setText(R.string.studio_volume)
                textSize = 11f
                alpha = 0.6f
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(2))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val level = FaderView(this, vertical = true).apply {
                accent = color
                defaultValue = defaultMix(t).level / LEVEL_MAX
                snapToDefault = true
                onChange = { v ->
                    session.updateTrack(lane) { it.copy(level = v * LEVEL_MAX) }
                    scheduleSave()
                }
            }
            levelFaders += level
            strip.addView(
                level,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )

            strip.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val mute = square(R.string.studio_mute_letter).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                textSize = 11f
                setOnClickListener {
                    session.setMuted(lane, !session.isMuted(lane))
                    refreshMixer()
                    grid.invalidate()
                    scheduleSave()
                }
            }
            val solo = square(R.string.studio_solo_letter).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                textSize = 11f
                setOnClickListener {
                    session.toggleSolo(lane)
                    refreshMixer()
                    scheduleSave()
                }
            }
            muteButtons += mute
            soloButtons += solo
            mixerIds += lane
            buttons.addView(mute)
            buttons.addView(solo)
            strip.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            strip.addView(outlined(R.string.studio_fx).apply {
                textSize = 13f
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { showFxPanel(it, lane, title) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

            mixerStrips.addView(
                strip,
                LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.MATCH_PARENT)
                    .apply { marginEnd = dp(8); bottomMargin = if (mixerWide) dp(12) else 0 },
            )
        }
        refreshMixer()
    }

    /** Les effets d'une piste : distorsion, lo-fi, filtre et envois vers la réverbération et l'écho. */
    private fun showFxPanel(anchor: View, lane: Int, title: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        fun add(labelRes: Int, read: (TrackMix) -> Float, toT: (Float) -> Float, fromT: (Float) -> Float, write: (TrackMix, Float) -> TrackMix) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply { setText(labelRes); textSize = 14f }, LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT))
            val bar = SeekBar(this).apply {
                max = 1000
                progress = (toT(read(session.mixOf(lane))).coerceIn(0f, 1f) * 1000).roundToInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val v = fromT(progress / 1000f)
                        session.updateTrack(lane) { write(it, v) }
                        scheduleSave()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        if (!playing) previewTrack(lane)
                    }
                })
            }
            row.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        if (padsPane.visibility == View.VISIBLE) {
            add(R.string.studio_volume, { it.level }, { it / LEVEL_MAX }, { it * LEVEL_MAX }, { mix, value -> mix.copy(level = value) })
        }
        add(R.string.studio_fx_pan, { it.pan }, { (it + 1f) / 2f }, { it * 2f - 1f }, { m, v -> m.copy(pan = if (kotlin.math.abs(v) < 0.04f) 0f else v) })
        add(R.string.studio_fx_reverb, { it.reverbSend }, { it }, { it }, { m, v -> m.copy(reverbSend = v) })
        add(R.string.studio_fx_echo, { it.delaySend }, { it }, { it }, { m, v -> m.copy(delaySend = v) })
        add(R.string.studio_fx_filter, { it.filter }, { (it + 1f) / 2f }, { it * 2f - 1f }, { m, v -> m.copy(filter = if (kotlin.math.abs(v) < 0.04f) 0f else v) })
        add(R.string.studio_fx_drive, { it.drive }, { it }, { it }, { m, v -> m.copy(drive = v) })
        add(R.string.studio_fx_crush, { it.crush }, { it }, { it }, { m, v -> m.copy(crush = v) })
        // La pompe suit la grosse caisse : elle n'a pas de sens sur la grosse caisse elle-même
        val t = session.project.trackOrNull(lane)
        if (t != null && !(t.kind == TrackKind.DRUM && t.voice == Inst.KICK)) {
            add(R.string.studio_fx_pump, { it.pump }, { it }, { it }, { m, v -> m.copy(pump = if (v < 0.02f) 0f else v) })
        }
        panels.show(R.string.studio_fx, getString(R.string.studio_panel_track, getString(R.string.studio_fx), title), box, 400, anchor, onHistory = { showFxPanel(anchor, lane, title) }) { session.project.trackOrNull(lane)?.hidden == false }
    }

    private fun showPadPosition() {
        val fixed = session.project.heldPad
        val pattern = if (fixed == null && playing) engine.displayPattern() else null
        val at = engine.playPosition
        val filter = fixed?.filter ?: pattern?.filterAt(at) ?: 0f
        val space = fixed?.space ?: pattern?.spaceAt(at) ?: 0f
        padView.showAt(1f - filter, space)
    }

    private fun refreshGestureButtons() {
        if (!::gestureClearButton.isInitialized) return
        gestureClearButton.isEnabled = session.project.pattern.hasAutomation
        gestureClearButton.alpha = if (gestureClearButton.isEnabled) 1f else 0.4f
    }

    private fun refreshMixer() {
        refreshGestureButtons()
        if (::holdPadSwitch.isInitialized) {
            holdPadSwitch.isChecked = session.project.heldPad != null
            showPadPosition()
        }
        for (i in 0 until mixerIds.size) {
            val id = mixerIds[i]
            val m = session.project.trackOrNull(id)?.mix ?: continue
            levelFaders[i].value = m.level / LEVEL_MAX
            muteButtons[i].setBackgroundColor(if (session.isMuted(id)) 0x88E74C3C.toInt() else android.graphics.Color.TRANSPARENT)
            soloButtons[i].setBackgroundColor(if (session.isSolo(id)) 0x88F1C40F.toInt() else android.graphics.Color.TRANSPARENT)
        }
    }

    private fun buildNotesPane(): LinearLayout {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Une seule rangée de boutons (elle passe à la ligne si la place manque) ; les réglages fins sont dans des panneaux
        // Une pastille par piste à notes, dans une ligne discrète qui défile s'il y en a beaucoup : on passe d'une piste à l'autre d'un toucher
        trackStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        outer.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(trackStrip)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })

        val header = flowRow(topMargin = dp(6))
        notesHeader = header
        val tuning = FlowRow(this).apply {
            hGap = dp(6)
            vGap = dp(4)
            setPadding(dp(12), 0, dp(12), dp(12))
        }
        val tuningButton = outlined(R.string.studio_tab_notes).apply {
            setIconResource(R.drawable.ic_px_tune)
            setOnClickListener { anchor ->
                (tuning.parent as? ViewGroup)?.removeView(tuning)
                panels.show(R.string.studio_tab_notes, getString(R.string.studio_tab_notes), tuning, 350, anchor)
            }
        }
        header.addView(tuningButton, wrap())
        keyButton = outlined(null).also { it.setOnClickListener { v -> pickKey(v) } }
        tuning.addView(keyButton, wrap(end = dp(8)))
        scaleButton = outlined(null).also { it.setOnClickListener { v -> pickScale(v) } }
        tuning.addView(scaleButton, wrap(end = dp(8)))
        lockSwitch = SwitchCompat(this).apply {
            setText(R.string.studio_lock)
            textSize = 14f
            isChecked = scaleLocked
            setOnCheckedChangeListener { _, checked ->
                scaleLocked = checked
                refreshRoll()
            }
        }
        tuning.addView(lockSwitch, wrap(end = dp(8)))
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        tuning.addView(SwitchCompat(this).apply {
            setText(R.string.studio_dynamic_touch)
            isChecked = prefs.getBoolean("dynamic_touch", false)
            setOnCheckedChangeListener { _, enabled ->
                keyboard.dynamicTouch = enabled
                prefs.edit().putBoolean("dynamic_touch", enabled).apply()
            }
        }, wrap())
        val octaves = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        octaves.addView(square(R.string.studio_tempo_less).also {
            it.contentDescription = getString(R.string.studio_octave_down)
            it.setOnClickListener { shiftRegister(-7) }
        })
        registerButton = outlined(null).apply {
            setOnClickListener { showRegisterMenu(it) }
        }
        octaves.addView(registerButton)
        octaves.addView(square(R.string.studio_tempo_more).also {
            it.contentDescription = getString(R.string.studio_octave_up)
            it.setOnClickListener { shiftRegister(7) }
        })
        tuning.addView(octaves, wrap(end = dp(8)))
        soundButton = outlined(null).also { it.setOnClickListener { v -> showSoundPanel(v) } }
        header.addView(soundButton, wrap(end = dp(8)))
        arpButton = outlined(null).also { it.setOnClickListener { v -> showArpPanel(v) } }
        header.addView(arpButton, wrap(end = dp(8)))
        recordButton = outlined(null).also { it.setOnClickListener { toggleRecord() } }
        header.addView(recordButton, wrap(end = dp(8)))
        // Thérémine : le clavier laisse la place à une surface où l'on glisse le doigt
        thereminToggle = outlined(R.string.studio_theremin).apply {
            isCheckable = true
            setOnClickListener { setTheremin(isChecked) }
        }
        header.addView(thereminToggle, wrap(end = dp(8)))
        midiText = caption(R.string.studio_midi_connected).also { it.visibility = View.GONE }
        header.addView(midiText, wrap())
        outer.addView(header)
        trackDependent.addAll(listOf(tuningButton, soundButton, arpButton, recordButton, thereminToggle))

        notesEmpty = TextView(this).apply {
            setText(R.string.studio_no_melody)
            textSize = 15f
            alpha = 0.75f
            setPadding(dp(8), dp(24), dp(8), 0)
            visibility = View.GONE
        }
        outer.addView(notesEmpty)

        val pane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        notesContent = pane
        roll = PianoRollView(this).apply {
            accent = LANE_COLORS[Inst.SYNTH]
            noteNames = resources.getStringArray(R.array.studio_key_names).toList()
            stepCount = session.project.stepCount
            notes = { session.notesOf(selectedTrack) }
            listener = object : PianoRollView.Listener {
                override fun onAddNote(step: Int, pitch: Int, length: Int) {
                    // Une note posée d'un simple toucher reprend la force et la longueur copiées pour cette piste
                    val pen = pens[selectedTrack]
                    session.addNote(
                        step, pitch, if (length == 1 && pen != null) pen.length else length,
                        velocity = pen?.velocity ?: StudioSession.LEVELS[0], track = selectedTrack,
                    )
                    scheduleSave()
                    invalidate()
                }

                override fun onRemoveNote(note: Note) {
                    session.removeNote(note)
                    scheduleSave()
                    invalidate()
                }

                override fun onEditPosition(note: Note, step: Int, pitch: Int, length: Int) {
                    session.editNote(note, step, pitch, length)
                    scheduleSave()
                    invalidate()
                }

                override fun onScrollRegister(whiteKeys: Int) = shiftRegister(whiteKeys)

                override fun onRevealNote(note: Note) = revealNote(note)

                override fun onPreview(pitch: Int) = previewPitch(pitch)

                override fun onSelectionChanged() = refreshSelectionBar()

                override fun onPasteAt(pitch: Int, step: Int) = updatePasteTarget()

                override fun onEditNote(note: Note, screenX: Float, screenY: Float) = showStepBubble(note.track, note.step, screenX, screenY, note.pitch)
            }
        }
        // Le piano roll prend sa hauteur naturelle, le clavier se pose juste dessous (plus de place au clavier en portrait)
        applyNotesLayout()
        pane.addView(roll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        keyboard = KeyboardView(this).apply {
            dynamicTouch = prefs.getBoolean("dynamic_touch", false)
            accent = LANE_COLORS[Inst.SYNTH]
            noteNames = resources.getStringArray(R.array.studio_key_names).toList()
            listener = object : KeyboardView.Listener {
                override fun onKeyDown(pitch: Int, velocity: Float) {
                    if (selectedTrack < 0 || session.isMuted(selectedTrack)) return
                    keyboardTracks[pitch] = selectedTrack
                    roll.setLit(pitch, true)
                    liveNoteOn(pitch, velocity, engine.playPosition)
                }

                override fun onKeyUp(pitch: Int) {
                    roll.setLit(pitch, false)
                    val track = keyboardTracks.remove(pitch) ?: return
                    engine.release(track, pitch)
                    endRecordedNote(track, pitch, engine.playPosition)
                }
            }
        }
        // Le clavier occupe tout le bas : il prend la place restante (au plus 300 dp), collé en bas, sans bande vide dessous
        val keyboardZone = FrameLayout(this).apply {
            minimumHeight = dp(48)
            // Le vide au-dessus du clavier fait glisser la grille des notes, comme sur la page Rythme
            setOnTouchListener { _, event -> roll.dragScroll(event) }
        }
        keyboardZone.addView(keyboard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { topMargin = dp(8) })
        theremin = ThereminView(this).apply { visibility = View.GONE }
        wireTheremin(theremin)
        keyboardZone.addView(theremin, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply { topMargin = dp(8) })
        pane.addView(keyboardZone, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        outer.addView(pane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        drumPanel = buildDrumPanel()
        outer.addView(drumPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return outer
    }

    /**
     * Branche un pad thérémine sur la piste mélodique choisie : le doigt joue (et s'enregistre si l'enregistrement est armé),
     * glisse d'une note de la gamme à l'autre, et règle le jeu (doux et sourd en bas, fort et brillant en haut).
     */
    private fun wireTheremin(view: ThereminView) {
        view.apply {
            noteNames = resources.getStringArray(R.array.studio_key_names).toList()
            onStart = { pitch, expression ->
                clearPendingPadEffects(selectedTrack)
                thereminTrack = selectedTrack
                engine.setExpression(selectedTrack, expression)
                roll.setLit(pitch, true)
                liveNoteOn(pitch, 0.9f, engine.playPosition)
            }
            onPitch = { from, to ->
                val track = thereminTrack
                if (track >= 0) glideLiveNote(track, from, to)
                roll.setLit(from, false)
                roll.setLit(to, true)
                roll.invalidate()
            }
            onExpression = { expression -> if (thereminTrack >= 0) engine.setExpression(thereminTrack, expression) }
            onEnd = { pitch ->
                roll.setLit(pitch, false)
                if (thereminTrack >= 0) engine.release(thereminTrack, pitch)
                endRecordedNote(thereminTrack, pitch, engine.playPosition)
                roll.invalidate()
                // La piste retrouve son niveau normal une fois la note éteinte (pas pendant sa queue)
                if (thereminTrack >= 0) resetAfterRelease(thereminTrack)
                thereminTrack = -1
            }
        }
    }

    /**
     * Le son d'une piste de batterie : trois variantes à toucher, deux curseurs (hauteur, durée) et « Réinitialiser » qui remet
     * tout d'origine. Chaque changement se fait entendre ; rien ici ne peut dérégler le reste du morceau.
     */
    private fun buildDrumPanel(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), 0)
            visibility = View.GONE
        }
        fun section(textRes: Int) = caption(textRes).apply { setPadding(0, dp(18), 0, dp(6)) }

        box.addView(section(R.string.studio_drum_kit))
        drumKitRow = FlowRow(this).apply {
            hGap = dp(8)
            vGap = dp(8)
        }
        box.addView(drumKitRow)

        fun sliderRow(max: Int, text: TextView, onValue: (Int) -> Unit): Pair<SeekBar, LinearLayout> {
            val bar = SeekBar(this).apply {
                this.max = max
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (fromUser) onValue(progress)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) = listenDrum()
                })
            }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text, LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT))
            return bar to row
        }

        // Hauteur et durée ne concernent que les sons fabriqués : un kit du soundfont les masque
        drumSliders = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(drumSliders)
        drumTuneText = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.END
        }
        val tune = sliderRow(24, drumTuneText) { editDrum(tune = it - 12) }
        drumTuneBar = tune.first
        drumSliders.addView(section(R.string.studio_drum_tune))
        drumSliders.addView(tune.second, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        drumLengthText = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.END
        }
        val length = sliderRow(150, drumLengthText) { editDrum(length = it + 50) }
        drumLengthBar = length.first
        drumSliders.addView(section(R.string.studio_drum_length))
        drumSliders.addView(length.second, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val buttons = FlowRow(this).apply { hGap = dp(8); vGap = dp(4) }
        buttons.addView(outlined(R.string.studio_spread).apply { setOnClickListener { showSpread(it, editedDrum) } })
        buttons.addView(outlined(R.string.studio_drum_listen).also { it.setOnClickListener { listenDrum() } })
        buttons.addView(outlined(R.string.studio_drum_reset).also {
            it.setOnClickListener {
                session.setDrumSoundfont(editedDrum, -1)
                editDrum(kit = 0, tune = 0, length = 100)
                refreshDrumPanel()
                listenDrum()
            }
        }, wrap())
        box.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22) })
        return box
    }

    private var refreshSpread: (() -> Unit)? = null

    private fun showSpread(anchor: View, track: Int) {
        val project = session.project
        val instrument = project.trackOrNull(track) ?: return
        val lane = project.pattern.lane(track) ?: return
        val slots = project.viewSlots
        fun valid() = session.project.viewSlots == slots && session.project.baseSteps == project.baseSteps &&
            session.project.trackOrNull(track)?.hidden == false

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(12))
        }
        var hits = lane.velocity.take(BarRhythm.STEPS).count { it > 0f }.takeIf { it > 0 } ?: 4
        var offset = BarRhythm.offsetOf(lane.velocity, hits)
        val strip = LinearLayout(this)
        val color = trackColor(instrument)
        val dots = List(BarRhythm.STEPS) { step ->
            View(this).also { cell ->
                cell.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(3).toFloat()
                    setColor(color)
                }
                strip.addView(cell, LinearLayout.LayoutParams(0, dp(14), 1f).apply {
                    marginStart = dp(if (step == 0) 0 else if (step % 4 == 0) 5 else 2)
                })
            }
        }
        box.addView(strip, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val listen = outlined(R.string.studio_drum_listen)
        val playback = Any()
        fun stopPreview() {
            handler.removeCallbacksAndMessages(playback)
            listen.isSelected = false
            listen.setText(R.string.studio_drum_listen)
        }
        val apply = outlined(R.string.studio_spread_apply)
        fun refreshApply() {
            val currentLane = session.project.pattern.lane(track)
            val mask = BarRhythm.steps(hits, offset)
            apply.isEnabled = currentLane != null && currentLane.velocity.indices.any {
                (currentLane.velocity[it] > 0f) != mask[it % mask.size]
            }
            apply.alpha = if (apply.isEnabled) 1f else 0.45f
        }
        fun update() {
            refreshApply()
            stopPreview()
            BarRhythm.steps(hits, offset).forEachIndexed { index, active -> dots[index].alpha = if (active) 1f else 0.18f }
        }
        fun control(label: Int, min: Int, max: Int, value: Int, edit: (Int) -> Unit) {
            val title = caption(label)
            fun showValue(v: Int) { title.text = getString(label, v) }
            val bar = slider(max - min) { progress ->
                val v = min + progress
                edit(v)
                showValue(v)
                update()
            }
            showValue(value)
            bar.id = View.generateViewId()
            title.labelFor = bar.id
            bar.progress = value - min
            box.addView(title)
            box.addView(bar, LinearLayout.LayoutParams(-1, dp(44)))
        }
        control(R.string.studio_spread_hits, 1, 16, hits) { hits = it }
        control(R.string.studio_spread_shift, 0, 15, offset) { offset = it }
        val actions = FlowRow(this).apply { hGap = dp(8) }
        actions.addView(listen)
        listen.setOnClickListener {
            if (listen.isSelected) { stopPreview(); return@setOnClickListener }
            if (!valid() || padsRecording) return@setOnClickListener
            val current = session.project
            val start = android.os.SystemClock.uptimeMillis()
            val stepMs = 15000.0 / current.bpm
            listen.isSelected = true
            listen.setText(R.string.studio_stop)
            BarRhythm.steps(hits, offset).forEachIndexed { i, active ->
                if (active) {
                    val delay = (i + if (i % 2 == 1) current.swing.toDouble() else 0.0) * stepMs
                    handler.postAtTime({
                        if (box.isShown && hasWindowFocus() && valid() && !padsRecording) {
                            val info = session.stepInfo(track, i)
                            engine.trigger(track, info?.pitch ?: 60, info?.velocity ?: 1f, (info?.hold ?: 100) / 100f, recordHit = false)
                        }
                    }, playback, start + delay.toLong())
                }
            }
            handler.postAtTime({ stopPreview() }, playback, start + (BarRhythm.STEPS * stepMs).toLong())
        }
        box.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { refreshSpread = ::refreshApply }
            override fun onViewDetachedFromWindow(v: View) {
                refreshSpread = null
                stopPreview()
            }
        })
        apply.setOnClickListener {
            stopPreview()
            if (valid() && session.spreadDrum(track, hits, offset)) changed()
        }
        actions.addView(apply)
        box.addView(actions)
        update()
        panels.show(R.string.studio_spread, getString(R.string.studio_panel_track,
            getString(R.string.studio_spread), trackName(instrument)), box, 320, anchor, valid = ::valid)
    }

    /** Change le son de la piste de batterie ouverte (ce qu'on ne donne pas reste comme avant) et le note. */
    private fun editDrum(kit: Int? = null, tune: Int? = null, length: Int? = null) {
        val t = session.project.trackOrNull(editedDrum) ?: return
        session.setDrumVoice(t.id, kit ?: t.kit, tune ?: t.tune, length ?: t.length)
        scheduleSave()
        val n = session.project.trackOrNull(editedDrum) ?: return
        drumTuneText.text = getString(R.string.studio_signed, n.tune)
        drumLengthText.text = getString(R.string.studio_percent, n.length)
    }

    /** La liste des kits de batterie du soundfont ; le choix remplace le son fabriqué de la piste. */
    private fun pickDrumKit(trackId: Int, onPicked: () -> Unit = {}) {
        withSf2 { bridge ->
            val kits = bridge.drumKits
            if (kits.isEmpty()) {
                MaterialAlertDialogBuilder(this).setMessage(R.string.studio_no_drum_kit).setPositiveButton(android.R.string.ok, null).show()
                return@withSf2
            }
            val names = kits.map { it.name.trim().ifEmpty { it.program.toString() } }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.studio_drum_kit_pick)
                .setItems(names) { _, which ->
                    session.setDrumSoundfont(trackId, kits[which].program)
                    scheduleSave()
                    refreshDrumPanel()
                    previewTrack(trackId)
                    onPicked()
                }
                .show()
        }
    }

    private fun listenDrum() {
        if (editedDrum >= 0) engine.trigger(editedDrum, 60, 1f, recordHit = false)
    }

    /** Remet le panneau d'accord avec la piste de batterie ouverte. */
    private fun refreshDrumPanel() {
        val t = session.project.trackOrNull(editedDrum)?.takeIf { it.kind == TrackKind.DRUM } ?: return
        drumKitRow.removeAllViews()
        resources.getStringArray(KIT_NAMES[t.voice.coerceIn(0, Inst.DRUM_COUNT - 1)]).forEachIndexed { i, name ->
            val c = chip(name) {
                session.setDrumSoundfont(t.id, -1)
                editDrum(kit = i)
                refreshDrumPanel()
                listenDrum()
            }
            c.isSelected = t.sfKit < 0 && i == t.kit
            drumKitRow.addView(c)
        }
        // Les vrais échantillons du soundfont de l'appli
        val sfName = if (t.sfKit >= 0) sf2?.drumKits?.firstOrNull { it.program == t.sfKit }?.name?.trim()?.takeIf { it.isNotEmpty() } else null
        drumKitRow.addView(chip(sfName ?: getString(R.string.studio_drum_soundfont)) { pickDrumKit(t.id) }.also { it.isSelected = t.sfKit >= 0 })
        drumSliders.visibility = if (t.sfKit >= 0) View.GONE else View.VISIBLE
        drumTuneBar.progress = t.tune + 12
        drumLengthBar.progress = t.length - 50
        drumTuneText.text = getString(R.string.studio_signed, t.tune)
        drumLengthText.text = getString(R.string.studio_percent, t.length)
    }

    /** Le texte du bouton « ? » : comment ça marche, puis le lexique (chaque mot en gras, sa phrase à côté). */
    private fun helpText(): CharSequence {
        val b = android.text.SpannableStringBuilder(getString(R.string.studio_help_text))
        b.append("\n\n").append(getString(R.string.studio_clip_help))
        fun bold(start: Int, end: Int) = b.setSpan(android.text.style.StyleSpan(Typeface.BOLD), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        b.append("\n\n")
        val titleStart = b.length
        b.append(getString(R.string.studio_lexicon_title))
        bold(titleStart, b.length)
        val terms = resources.getStringArray(R.array.studio_lexicon_terms)
        val defs = resources.getStringArray(R.array.studio_lexicon_defs)
        for (i in terms.indices) {
            b.append("\n")
            val start = b.length
            b.append(getString(R.string.studio_lexicon_line, terms[i], defs.getOrElse(i) { "" }))
            bold(start, start + terms[i].length)
        }
        return b
    }

    /** Un curseur de réglage du son, avec son nom au-dessus, dans un panneau. */
    private fun soundSlider(box: ViewGroup, p: SoundParam, trackId: Int): SeekBar {
        val bar = SeekBar(this).apply {
            max = 1000
            progress = (p.toSlider(p.read()).coerceIn(0f, 1f) * 1000).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        p.write(p.fromSlider(progress / 1000f))
                        scheduleSave()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) = previewTrack(trackId)
            })
        }
        p.bar = bar
        box.addView(labeled(p.labelRes, bar), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        return bar
    }

    /** Le panneau « Son » de la piste choisie : instrument, trois réglages simples, et le détail avancé du synthé. */
    private fun showSoundPanel(anchor: View, trackId: Int = selectedTrack) {
        val t = session.project.trackOrNull(trackId) ?: return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val params = ArrayList<SoundParam>()
        fun patch() = session.project.trackOrNull(t.id)?.synth ?: t.synth
        val synthTrack = t.kind == TrackKind.SYNTH
        val studioSynth = synthTrack && t.synthBank == SynthBank.STUDIO
        val fmSynth = synthTrack && t.synthBank == SynthBank.FM
        val chipSynth = synthTrack && t.synthBank.isGx
        if (synthTrack) {
            box.addView(MaterialButtonToggleGroup(this).apply {
                isSingleSelection = true
                isSelectionRequired = true
                for (bank in SynthBank.entries) {
                    val button = outlined(bankName(bank)).apply {
                        id = View.generateViewId()
                        tag = bank
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(dp(10), 0, dp(10), 0)
                    }
                    addView(button)
                    if (bank == t.synthBank) check(button.id)
                }
                addOnButtonCheckedListener { group, id, checked ->
                    if (checked) {
                        val bank = group.findViewById<View>(id).tag as SynthBank
                        fmSoundPad?.finishTouch(false)
                        session.setSynthBank(t.id, bank)
                        scheduleSave()
                        showSoundPanel(anchor, t.id)
                        refreshChipKeyboard(t.id)
                    }
                }
            }, wrap())
        }
        val pick = outlined(null)
        val forget = outlined(R.string.studio_forget_sound)
        var personalPad: XyPadView? = null
        var waveGroup: MaterialButtonToggleGroup? = null
        var refreshingWave = false
        fun refreshPick() {
            val now = session.project.trackOrNull(t.id) ?: return
            pick.text = instrumentName(now)
            personalPad?.visibility = if (now.fmProgram == 0) View.VISIBLE else View.GONE
            forget.visibility = if (studioSynth && now.synth.id.startsWith(SynthPatch.MINE_PREFIX)) View.VISIBLE else View.GONE
            if (studioSynth) {
                refreshingWave = true
                waveGroup?.let { group ->
                    val waveId = waveIds.getOrNull(now.synth.wave)
                    if (waveId == null) group.clearChecked() else group.check(waveId)
                }
                refreshingWave = false
            }
            for (p in params) p.bar.progress = (p.toSlider(p.read()).coerceIn(0f, 1f) * 1000).roundToInt()
            refreshNotesControls()
        }
        fun chooseProgram(program: Int) {
            personalPad?.finishTouch(false)
            if (fmSynth) session.setFmProgram(t.id, program) else session.setChipProgram(t.id, program)
            scheduleSave()
            refreshPick()
            refreshChipKeyboard(t.id)
        }
        pick.setOnClickListener {
            if (fmSynth) {
                val current = session.project.trackOrNull(t.id) ?: return@setOnClickListener
                val popup = androidx.appcompat.widget.PopupMenu(this, pick)
                fmProgramNames.forEachIndexed { index, name ->
                    popup.menu.add(name).apply {
                        isCheckable = true
                        isChecked = index == current.fmProgram
                        setOnMenuItemClickListener { chooseProgram(index); true }
                    }
                }
                popup.show()
            } else if (chipSynth) {
                val current = session.project.trackOrNull(t.id) ?: return@setOnClickListener
                pickChipProgram(pick, current.program, ::chooseProgram)
            } else if (studioSynth) {
                pickSynthPreset(pick, patch().id) { chosen ->
                    session.updateSynth(t.id) { chosen }
                    scheduleSave()
                    refreshPick()
                    auditionPreset(t.id, chosen)
                }
            } else {
                withSf2 { bridge ->
                    pickProgram(bridge) { bank, program ->
                        session.setSf2Program(t.id, bank, program)
                        scheduleSave()
                        refreshPick()
                        previewTrack(t.id)
                    }
                }
            }
        }
        if (chipSynth || fmSynth) {
            val programRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            fun step(label: Int, description: Int, delta: Int) = square(label).apply {
                contentDescription = getString(description)
                setOnClickListener {
                    val now = session.project.trackOrNull(t.id) ?: return@setOnClickListener
                    val current = if (fmSynth) now.fmProgram else now.program
                    val count = if (fmSynth) fmProgramNames.size else chipProgramNames.size
                    chooseProgram(Math.floorMod(current + delta, count))
                }
            }
            programRow.addView(step(R.string.studio_tempo_less, R.string.studio_program_previous, -1))
            pick.setPadding(dp(6), 0, dp(6), 0)
            programRow.addView(pick, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            programRow.addView(step(R.string.studio_tempo_more, R.string.studio_program_next, 1))
            box.addView(programRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            if (fmSynth) {
                val pad = personalFmPad(t)
                personalPad = pad
                box.addView(pad, LinearLayout.LayoutParams(-1, dp(132)).apply { topMargin = dp(8) })
            } else box.addView(SwitchCompat(this).apply {
                setText(R.string.studio_chip_smooth_bass)
                minimumHeight = dp(48)
                switchPadding = dp(8)
                isChecked = t.chipSmoothBass
                setOnCheckedChangeListener { _, checked ->
                    session.setChipSmoothBass(t.id, checked)
                    scheduleSave()
                }
            }, wrap())
        } else if (studioSynth) {
            box.addView(FlowRow(this).apply {
                hGap = dp(8)
                addView(pick)
                addView(outlined(R.string.studio_drum_listen).apply {
                    setOnClickListener { auditionPreset(t.id, patch()) }
                })
            })
        } else box.addView(pick, wrap())

        if (studioSynth) {
            // Un son à soi : enregistré au micro, ou un réglage gardé pour le retrouver dans tous les morceaux
            val own = FlowRow(this).apply { hGap = dp(8); vGap = dp(4) }
            val status = TextView(this).apply { textSize = 13f; alpha = 0.75f; visibility = View.GONE }
            val record = outlined(R.string.studio_synth_record)
            record.setOnClickListener {
                withMic {
                    record.isEnabled = false
                    status.visibility = View.VISIBLE
                    status.setText(R.string.studio_synth_listening)
                    Thread {
                        val pcm = SampleTools.record()
                        val clean = pcm?.let { SampleTools.clean(it) }
                        val root = clean?.let { SampleTools.detectRoot(it, SampleTools.RATE) }
                        if (clean != null) SampleTools.save(this, clean, SampleTools.SYNTH_FILE)
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            record.isEnabled = true
                            if (clean != null && root != null) {
                                status.visibility = View.GONE
                                engine.setSynthSample(SynthSample(clean, SampleTools.RATE, root))
                                session.updateSynth(t.id) { SynthPresets.MY_VOICE }
                                scheduleSave()
                                refreshPick()
                                previewTrack(t.id)
                            } else {
                                status.setText(if (pcm == null) R.string.studio_sample_failed else R.string.studio_sample_nothing)
                            }
                        }
                    }.start()
                }
            }
            own.addView(record)
            own.addView(outlined(R.string.studio_keep_sound).apply {
                setOnClickListener {
                    val now = session.project.trackOrNull(t.id)?.synth ?: return@setOnClickListener
                    val suggested = if (now.id.startsWith(SynthPatch.MINE_PREFIX)) MySounds.nameOf(now.id) else soundName(now)
                    promptText(R.string.studio_keep_sound_title, suggested, android.R.string.ok) { name ->
                        if (name.isNotBlank()) {
                            val kept = mySounds.keep(now, name)
                            session.updateSynth(t.id) { kept }
                            scheduleSave()
                            refreshPick()
                        }
                    }
                }
            })
            forget.setOnClickListener {
                // Le morceau garde son exemplaire du réglage ; le son disparaît seulement de la liste
                session.project.trackOrNull(t.id)?.synth?.id?.let { mySounds.forget(it) }
                forget.visibility = View.GONE
            }
            own.addView(forget)
            box.addView(own, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            box.addView(status)
        }

        fun add(into: ViewGroup, p: SoundParam) {
            params += p
            soundSlider(into, p, t.id)
        }
        if (studioSynth) {
            add(box, SoundParam(R.string.studio_macro_bright, { patch().cutoffHz },
                { hz -> ln(hz.coerceIn(100f, 10000f) / 100f) / ln(100f) }, { t2 -> 100f * exp(t2 * ln(100f)) },
                { v -> session.updateSynth(t.id) { it.copy(cutoffHz = v) } }))
            add(box, SoundParam(R.string.studio_macro_drive, { patch().drive }, { it }, { it },
                { v -> session.updateSynth(t.id) { it.copy(drive = v) } }))
        }
        add(box, SoundParam(R.string.studio_macro_ambience, { session.project.trackOrNull(t.id)?.mix?.reverbSend ?: 0f }, { it }, { it },
            { v -> session.setSynthSpace(v, t.id) }))

        if (studioSynth) {
            val advanced = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            val wave = MaterialButtonToggleGroup(this).apply {
                isSingleSelection = true
                isSelectionRequired = true
                val labels = listOf(R.string.studio_wave_saw, R.string.studio_wave_square, R.string.studio_wave_triangle, R.string.studio_wave_sine)
                for (i in 0 until 4) addView(outlined(labels[i]).apply { id = waveIds[i]; minWidth = 0; setPadding(dp(8), 0, dp(8), 0) })
                if (patch().wave in 0..3) check(waveIds[patch().wave])
                addOnButtonCheckedListener { _, id, checked ->
                    if (checked && !refreshingWave) {
                        session.updateSynth(t.id) { it.copy(wave = waveIds.indexOf(id)) }
                        scheduleSave()
                        previewTrack(t.id)
                    }
                }
            }
            waveGroup = wave
            advanced.addView(wave, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            val list = listOf(
                SoundParam(R.string.studio_adv_resonance, { patch().resonance }, { (it - 0.5f) / 9.5f }, { 0.5f + it * 9.5f },
                    { v -> session.updateSynth(t.id) { it.copy(resonance = v) } }),
                SoundParam(R.string.studio_adv_detune, { patch().detuneCents }, { (it / 1200f).coerceIn(0f, 1f).pow(1f / 3f) }, { it * it * it * 1200f },
                    { v -> session.updateSynth(t.id) { it.copy(detuneCents = v) } }),
                SoundParam(R.string.studio_adv_sub, { patch().subLevel }, { it }, { it },
                    { v -> session.updateSynth(t.id) { it.copy(subLevel = v) } }),
                SoundParam(R.string.studio_adv_sweep, { patch().envOctaves }, { it / 5f }, { it * 5f },
                    { v -> session.updateSynth(t.id) { it.copy(envOctaves = v) } }),
                SoundParam(R.string.studio_adv_attack, { patch().attack }, { ln(it.coerceIn(0.002f, 3f) / 0.002f) / ln(1500f) }, { 0.002f * exp(it * ln(1500f)) },
                    { v -> session.updateSynth(t.id) { it.copy(attack = v) } }),
                SoundParam(R.string.studio_adv_release, { patch().release }, { ln(it.coerceIn(0.005f, 4f) / 0.005f) / ln(800f) }, { 0.005f * exp(it * ln(800f)) },
                    { v -> session.updateSynth(t.id) { it.copy(release = v) } }),
                SoundParam(R.string.studio_adv_move_speed, { patch().lfoRate }, { (it / 12f).coerceIn(0f, 1f).pow(0.5f) }, { it * it * 12f },
                    { v -> session.updateSynth(t.id) { it.copy(lfoRate = v) } }),
                SoundParam(R.string.studio_adv_move_depth, { patch().lfoFilter }, { it / 3f }, { it * 3f },
                    { v -> session.updateSynth(t.id) { it.copy(lfoFilter = v) } }),
            )
            for (p in list) add(advanced, p)
            box.addView(outlined(R.string.studio_advanced).apply {
                setOnClickListener { advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            box.addView(advanced)
        }
        if (padsPane.visibility == View.VISIBLE) {
            val actions = FlowRow(this).apply { hGap = dp(6) }
            actions.addView(outlined(R.string.studio_fx).apply { setOnClickListener { showFxPanel(it, t.id, trackName(t)) } })
            actions.addView(outlined(R.string.studio_arp_caption).apply { setOnClickListener { showArpPanel(it, t.id) } })
            box.addView(actions)
        }
        refreshPick()
        panels.show(R.string.studio_sound_caption, getString(R.string.studio_panel_track, getString(R.string.studio_sound_caption), trackName(t)), box, 340, anchor, onHistory = { showSoundPanel(anchor, t.id) }) { session.project.trackOrNull(t.id)?.hidden == false }
    }

    private val presetReleases = mutableMapOf<Int, Runnable>()

    /** Une audition FM cède sa place avant l'attaque suivante, même si la touche vient du thread MIDI. */
    private val auditionLock = Any()
    private var heldMidiNotes = 0
    private var auditionTrack = -1
    private var auditionPitch = 60
    private var auditionOwner: XyPadView? = null
    private val stopAuditionTask = Runnable { stopSoundAudition() }

    private fun stopSoundAudition(owner: XyPadView? = null): Unit = synchronized(auditionLock) {
        if (auditionTrack >= 0 && (owner == null || auditionOwner === owner)) {
            handler.removeCallbacks(stopAuditionTask)
            engine.release(auditionTrack, auditionPitch)
            auditionTrack = -1
            auditionOwner = null
        }
    }

    private fun personalFmPad(track: Track) = FmPatchPadView(this).apply {
        accent = trackColor(track)
        showAt(track.fmBrightness, track.fmAttack)
        describePosition()
        var firstPosition = false
        var heldPreview = false
        onPress = {
            fmSoundPad = this
            firstPosition = true
            handler.removeCallbacks(saveRunnable)
        }
        onMove = XyPadView.MoveListener { brightness, attack ->
            session.setFmPad(track.id, brightness, attack)
            if (firstPosition) {
                firstPosition = false
                heldPreview = isTouching
                auditionFmNote(track.id, 60 + session.project.keyPc, 0.9f, if (heldPreview) 0L else 450L, this)
            }
        }
        onRelease = { completed ->
            if (heldPreview) stopSoundAudition(this)
            if (fmSoundPad === this) fmSoundPad = null
            session.endFmPad()
            describePosition()
            if (completed) scheduleSave() else handler.post { if (screenActive) scheduleSave() }
        }
    }

    private fun hasLiveInput(id: Int): Boolean =
        id in keyboardTracks.values || padFingers.containsKey(id) || id == thereminTrack || midiLights[id]?.any { it > 0 } == true

    private fun refreshChipKeyboard(id: Int) {
        val t = session.project.trackOrNull(id) ?: return
        // Garder les touches sous les doigts et la note tenue pendant un changement de son.
        if (hasLiveInput(id)) return
        val octave = StudioRegister.chipOctave(t)
        if (id == selectedTrack && !manualRegister && octave != null && session.notesOf(id).isEmpty()) {
            register.setLow(octave)
            refreshRoll()
        }
        if (padsPane.visibility == View.VISIBLE) refreshPads()
        if (!playing) {
            if (octave == null) auditionPreset(id, t.synth) else auditionNote(id, octave, 500L)
        }
    }

    private fun auditionPreset(track: Int, patch: SynthPatch) {
        val octave = if (SynthPresets.BASSES.any { it.id == patch.id }) 36 else 60
        // Laisser aussi aux nappes et aux drones le temps d'atteindre leur attaque.
        auditionNote(track, octave, (patch.attack * 1000).toLong() + 450L)
    }

    private fun auditionNote(track: Int, octave: Int, durationMs: Long) {
        if (session.isMuted(track)) return
        presetReleases.remove(track)?.let { handler.removeCallbacks(it); it.run() }
        val pitch = octave + session.project.keyPc
        if (session.project.trackOrNull(track)?.synthBank == SynthBank.FM) {
            auditionFmNote(track, pitch, 0.9f, durationMs)
            return
        }
        stopSoundAudition()
        engine.trigger(track, pitch, 0.9f, recordHit = false)
        val release = Runnable {
            engine.release(track, pitch)
            presetReleases.remove(track)
        }
        presetReleases[track] = release
        handler.postDelayed(release, durationMs)
    }

    private fun auditionFmNote(track: Int, pitch: Int, velocity: Float, durationMs: Long, owner: XyPadView? = null): Unit =
        synchronized(auditionLock) {
            if (session.isMuted(track) || heldMidiNotes > 0 || keyboardTracks.isNotEmpty() ||
                padFingers.isNotEmpty() || thereminTrack >= 0) return@synchronized
            if (owner != null && (playing || recorder.armed || padsRecording || pads.hasHeldDrum)) return@synchronized
            stopSoundAudition()
            auditionTrack = track
            auditionPitch = pitch
            auditionOwner = owner
            engine.trigger(track, auditionPitch, velocity, recordHit = false)
            if (durationMs > 0L) handler.postDelayed(stopAuditionTask, durationMs)
        }

    private fun pickChipProgram(anchor: View, program: Int, onPick: (Int) -> Unit) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        for (family in GeneralMidiInstruments.Category.entries) {
            val group = popup.menu.addSubMenu(family.stringResId)
            for (index in family.range) {
                group.add(chipProgramNames[index]).apply {
                    isCheckable = true
                    isChecked = index == program
                    setOnMenuItemClickListener { onPick(index); true }
                }
            }
        }
        popup.show()
    }

    private fun pickSynthPreset(anchor: View, currentId: String, onPick: (SynthPatch) -> Unit) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        fun family(title: Int, patches: List<SynthPatch>) {
            if (patches.isEmpty()) return
            val group = popup.menu.addSubMenu(title)
            for (patch in patches) {
                group.add(soundName(patch)).apply {
                    isCheckable = true
                    isChecked = patch.id == currentId
                    setOnMenuItemClickListener { onPick(patch); true }
                }
            }
        }
        family(R.string.studio_family_bass, SynthPresets.BASSES)
        family(R.string.studio_family_keys, SynthPresets.KEYS)
        family(R.string.studio_family_lead, SynthPresets.LEADS)
        family(R.string.studio_family_pad, SynthPresets.PADS)
        family(R.string.studio_family_pluck, SynthPresets.PLUCKS)
        family(R.string.studio_family_own, listOfNotNull(SynthPresets.MY_VOICE.takeIf { engine.synthSample != null }) + mySounds.all())
        popup.show()
    }

    /** Le panneau « Arpège » de la piste choisie : mode, vitesse et nombre d'octaves. */
    private fun showArpPanel(anchor: View, trackId: Int = selectedTrack) {
        val t = session.project.trackOrNull(trackId) ?: return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val modes = listOf(Arp.OFF, Arp.UP, Arp.DOWN, Arp.UP_DOWN, Arp.RANDOM)
        val modeButton = outlined(null).apply { text = getString(arpName(t.arpMode)) }
        modeButton.setOnClickListener {
            dropdown(modeButton, modes.map { getString(arpName(it)) }) { which ->
                session.setArpMode(modes[which], t.id)
                modeButton.text = getString(arpName(modes[which]))
                refreshNotesControls()
                scheduleSave()
            }
        }
        box.addView(modeButton, wrap())
        box.addView(caption(R.string.studio_arp_rate), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        val rateIds = IntArray(3) { View.generateViewId() }
        val rate = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            val labels = listOf("1/8", "1/16", "1/32")
            for (i in 0 until 3) addView(outlined(null).apply { id = rateIds[i]; text = labels[i]; minWidth = dp(64) })
            check(rateIds[t.arpRate.coerceIn(0, 2)])
            addOnButtonCheckedListener { _, id, checked ->
                if (checked) {
                    session.setArpRate(rateIds.indexOf(id), t.id)
                    scheduleSave()
                }
            }
        }
        box.addView(rate)
        box.addView(caption(R.string.studio_arp_octaves), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        val octIds = IntArray(3) { View.generateViewId() }
        val oct = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            for (i in 0 until 3) addView(outlined(null).apply { id = octIds[i]; text = (i + 1).toString(); minWidth = dp(64) })
            check(octIds[t.arpOctaves.coerceIn(1, 3) - 1])
            addOnButtonCheckedListener { _, id, checked ->
                if (checked) {
                    session.setArpOctaves(octIds.indexOf(id) + 1, t.id)
                    scheduleSave()
                }
            }
        }
        box.addView(oct)
        panels.show(R.string.studio_arp_caption, getString(R.string.studio_panel_track, getString(R.string.studio_arp_caption), trackName(t)), box, 300, anchor, onHistory = { showArpPanel(anchor, t.id) }) { session.project.trackOrNull(t.id)?.hidden == false }
    }

    /** Le panneau « Réglages » : swing, espace, écho, longueur, copie du motif, sortie des fichiers. */
    private fun showSettingsPanel(anchor: View) {
        val p = session.project
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        fun addSlider(labelRes: Int, max: Int, value: Int, onChange: (Int) -> Unit) {
            val bar = slider(max = max) { v -> onChange(v); scheduleSave() }.apply { progress = value }
            box.addView(labeled(labelRes, bar), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        addSlider(R.string.studio_swing, 50, (p.swing * 100).roundToInt()) { session.setSwing(it / 100f) }
        addSlider(R.string.studio_space, 100, (p.space * 100).roundToInt()) { session.setSpace(it / 100f) }
        addSlider(R.string.studio_echo, 100, (p.echo * 100).roundToInt()) { session.setEcho(it / 100f) }

        val ids = intArrayOf(View.generateViewId(), View.generateViewId(), View.generateViewId())
        val steps = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            addView(outlined(null).apply { id = ids[0]; text = "16"; minWidth = dp(64) })
            addView(outlined(null).apply { id = ids[1]; text = "32"; minWidth = dp(64) })
            addView(outlined(null).apply { id = ids[2]; text = "64"; minWidth = dp(64) })
            check(when (p.baseSteps) { 64 -> ids[2]; 32 -> ids[1]; else -> ids[0] })
            addOnButtonCheckedListener { _, id, checked ->
                if (checked) {
                    val n = when (id) { ids[2] -> 64; ids[1] -> 32; else -> 16 }
                    if (n != session.project.baseSteps) {
                        cancelPadLaunch()
                        finishTakeSegment()
                        session.setStepCount(n)
                        grid.clearSelection()
                        roll.clearSelection()
                        setPasteArmed(false)
                        changed()
                    }
                }
            }
        }
        box.addView(steps, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })

        val actions = FlowRow(this).apply { hGap = dp(8); vGap = dp(8) }
        actions.addView(outlined(R.string.studio_pattern_copy).apply { setOnClickListener { showCopyMenu(it) } })
        actions.addView(outlined(R.string.studio_output).apply { setOnClickListener { showOutputMenu(it) } })
        box.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        panels.show(R.string.studio_settings, getString(R.string.studio_settings), box, 340, anchor, onHistory = { showSettingsPanel(anchor) })
    }

    // ---- Actions ---------------------------------------------------------------------------------

    private fun togglePlay() {
        if (playing) cancelPadLaunch()
        if (playing && recorder.armed) {
            setPadsRecording(false)
            refreshNotesControls()
        }
        playing = !playing
        if (playing) engine.play() else engine.stop()
        refreshTransport()
    }

    private fun showTab(id: Int) {
        val notes = id == tabNotesId
        val padsTab = id == tabPadsId
        setPasteArmed(false)
        onNotesTab = notes
        // Rien ne reste tenu sur la page qu'on quitte ; la prise aux pads s'arrête avec elle
        if (!padsTab) {
            cancelPadLaunch()
            pads.releaseAll()
            if (padsRecording) setPadsRecording(false)
        }
        if (id != tabMixerId) padView.finishTouch(false)
        if (!notes) { keyboard.releaseAll(); theremin.finish() }
        // Les coups des pads reviennent à l'écran (pour briller, et pour s'écrire pendant l'enregistrement)
        engine.recordHits = padsTab
        patternGroup.visibility = if (padsTab) View.GONE else View.VISIBLE
        padLoopRow.visibility = if (padsTab) View.VISIBLE else View.GONE
        if (padsTab) {
            if (recorder.armed && !padsRecording) setPadsRecording(true)
            refreshPadLauncher(force = true)
            setSelectMode(false)
            ensureSelected()
            refreshRoll()
            refreshPads()
        }
        rhythmPane.visibility = if (id == tabRhythmId) View.VISIBLE else View.GONE
        padsPane.visibility = if (padsTab) View.VISIBLE else View.GONE
        notesPane.visibility = if (notes) View.VISIBLE else View.GONE
        mixerPane.visibility = if (id == tabMixerId) View.VISIBLE else View.GONE
        placePerformancePad()
        panels.prune()
        requestFrame()
        if (id == tabMixerId) refreshMixer()
        refreshSelectionBar()
        if (notes) {
            ensureSelected()
            focusRegister()
            refreshRoll()
            refreshNotesControls()
        }
    }

    private fun previewLane(trackId: Int, step: Int) {
        val t = session.project.trackOrNull(trackId) ?: return
        if (t.muted) return
        val v = session.velocity(trackId, step).takeIf { it > 0f } ?: 1f
        if (t.melodic) {
            previewPitch(session.rhythmPreviewPitch(trackId, step), v, track = trackId)
        } else {
            val info = session.stepInfo(trackId, step)
            engine.trigger(trackId, info?.pitch ?: 60, v, (info?.hold ?: 100) / 100f, recordHit = false)
        }
    }

    private fun previewPitch(pitch: Int, velocity: Float = 0.9f, holdMs: Long = 250, track: Int = selectedTrack) {
        if (track < 0 || session.isMuted(track)) return
        if (session.project.trackOrNull(track)?.synthBank == SynthBank.FM) {
            auditionFmNote(track, pitch, velocity, holdMs)
            return
        }
        stopSoundAudition()
        engine.trigger(track, pitch, velocity)
        handler.postDelayed({ engine.release(track, pitch) }, holdMs)
    }

    /** Une note d'essai après un réglage du son : la tonique, dans l'octave du piano roll. */
    private fun previewKeyNote() = previewPitch((register.low + (session.project.keyPc - register.low % 12 + 12) % 12), 0.9f, 450)

    /** Une note d'essai sur la piste [id] (une frappe pour la batterie). */
    private fun previewTrack(id: Int) {
        val t = session.project.trackOrNull(id) ?: return
        if (t.melodic) previewPitch((register.low + (session.project.keyPc - register.low % 12 + 12) % 12), 0.9f, 450, track = id) else engine.trigger(id, 60, 1f, recordHit = false)
    }

    private fun pickKey(anchor: View) {
        dropdown(anchor, resources.getStringArray(R.array.studio_key_names).toList()) { which ->
            session.setKey(which)
            focusRegister()
            changed()
        }
    }

    private fun pickScale(anchor: View) {
        dropdown(anchor, Scales.ALL.map { getString(scaleName(it.id)) }) { which ->
            session.setScale(Scales.ALL[which].id)
            changed()
        }
    }

    private lateinit var undoButton: ImageButton
    private lateinit var redoButton: ImageButton

    /** Une flèche de l'en-tête (annuler ou rétablir), de la couleur du texte, grisée quand elle ne sert à rien. */
    private fun historyButton(icon: Int, description: Int, onClick: () -> Unit): ImageButton = ImageButton(this).apply {
        setImageResource(icon)
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
        if (tv.resourceId != 0) setColorFilter(getColorStateList(tv.resourceId).defaultColor)
        val ripple = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
        if (ripple.resourceId != 0) setBackgroundResource(ripple.resourceId) else background = null
        contentDescription = getString(description)
        isEnabled = false
        alpha = 0.3f
        setOnClickListener { onClick() }
    }

    private fun stepHistory(redo: Boolean) {
        cancelPadLaunch()
        setPasteArmed(false)
        finishTakeSegment()
        if (redo) session.redo() else session.undo()
        panels.refreshAfterHistory()
        grid.clearSelection()
        roll.clearSelection()
        changed()
    }

    private fun refreshHistory() {
        if (!::undoButton.isInitialized || !::session.isInitialized) return
        undoButton.isEnabled = session.canUndo
        undoButton.alpha = if (session.canUndo) 1f else 0.3f
        redoButton.isEnabled = session.canRedo
        redoButton.alpha = if (session.canRedo) 1f else 0.3f
    }

    /** Le menu « Sortie » : fichiers à emporter, et son venu du micro. */
    private fun showOutputMenu(anchor: View) {
        val seconds = StudioExporter.seconds(session.project).roundToInt()
        val labels = listOf(
            getString(R.string.studio_export_wav_duration, seconds),
            getString(R.string.studio_export_midi),
            getString(R.string.studio_export_editor),
            getString(R.string.studio_sample),
        )
        dropdown(anchor, labels) { which ->
            if (which == 3) showSamplePanel(anchor) else runExport(which)
        }
    }

    private fun showCopyMenu(anchor: View) {
        val names = resources.getStringArray(R.array.studio_pattern_names)
        val from = session.project.current
        val targets = (0 until StudioProject.SLOTS).filter { it != from }
        dropdown(anchor, targets.map { names[it] }) { which ->
            session.copyPattern(from, targets[which])
            refreshPatterns()
            scheduleSave()
        }
    }

    private fun arpName(mode: Int): Int = when (mode) {
        Arp.UP -> R.string.studio_arp_up
        Arp.DOWN -> R.string.studio_arp_down
        Arp.UP_DOWN -> R.string.studio_arp_updown
        Arp.RANDOM -> R.string.studio_arp_random
        else -> R.string.studio_arp_off
    }

    private fun finishTakeSegment() {
        takeRevision++
        recorder.finishAll()
    }

    private fun toggleRecord() {
        if (!recorder.armed) engine.cancelPendingTrackClips()
        finishTakeSegment()
        recorder.armed = !recorder.armed
        refreshMetronome()
        // À l'arrêt, une mesure de décompte (au métronome) laisse le temps de poser les doigts
        if (recorder.armed && !playing) {
            playing = true
            engine.play(countIn = true)
            refreshTransport()
        }
        refreshNotesControls()
        roll.invalidate()
        scheduleSave()
    }

    /** Une touche (écran ou clavier MIDI) : le son part tout de suite ; l'enregistreur note l'instant. */
    private fun liveNoteOn(pitch: Int, velocity: Float, position: Double) {
        if (selectedTrack < 0 || session.isMuted(selectedTrack)) return
        stopSoundAudition()
        clearPendingPadEffects(selectedTrack)
        engine.trigger(selectedTrack, pitch, velocity)
        recorder.press(pitch, position, selectedTrack, velocity)
    }

    private fun endRecordedNote(track: Int, pitch: Int, position: Double) {
        if (recorder.release(pitch, position, track)) {
            roll.invalidate()
            grid.invalidate()
            scheduleSave()
        }
    }

    private fun displayMidiLights() {
        val lights = midiLights[selectedTrack]
        keyboard.midiLights = lights
        roll.midiLights = lights
        keyboard.invalidate()
        roll.invalidate()
    }

    private fun midiLight(track: Int, pitch: Int, down: Boolean) {
        val counts = midiLights.getOrPut(track) { IntArray(128) }
        counts[pitch] = if (down) counts[pitch] + 1 else (counts[pitch] - 1).coerceAtLeast(0)
        if (track == selectedTrack) displayMidiLights()
    }

    private fun startMidi() {
        lateinit var keys: MidiPerformance
        keys = MidiPerformance(
            onPress = { track, pitch, velocity ->
                val revision = takeRevision
                val at = engine.playPosition
                synchronized(auditionLock) {
                    heldMidiNotes++
                    stopSoundAudition()
                }
                engine.trigger(track, pitch, velocity)
                handler.post {
                    if (midiPerformance === keys) {
                        midiLight(track, pitch, true)
                        if (revision == takeRevision) recorder.press(pitch, at, track, velocity)
                    }
                }
            },
            onRelease = { track, pitch ->
                val revision = takeRevision
                val at = engine.playPosition
                synchronized(auditionLock) {
                    engine.release(track, pitch)
                    heldMidiNotes--
                }
                handler.post {
                    if (midiPerformance === keys) {
                        midiLight(track, pitch, false)
                        if (revision == takeRevision) endRecordedNote(track, pitch, at)
                    }
                }
            },
        )
        midiPerformance = keys
        midi = MidiInputController(this, { bytes ->
            val track = selectedTrack
            keys.receive(bytes, if (track >= 0 && !session.isMuted(track)) track else -1)
        }, { status ->
            if (status != MidiConnectionStatus.DEVICE_DETECTED) keys.reset()
            handler.post {
                if (midiPerformance === keys) midiText.visibility =
                    if (status == MidiConnectionStatus.DEVICE_DETECTED) View.VISIBLE else View.GONE
            }
        }).also { it.start() }
    }

    private fun stopLive() {
        midiPerformance?.close()
        midiPerformance = null
        midiLights.clear()
        displayMidiLights()
        midi?.stop()
        midi = null
        recorder.finishAll()
        recorder.armed = false
        refreshMetronome()
    }

    /** Après un remplacement en bloc (style, hasard, effacer, annuler, tonalité, son) : tout se redessine et se sauvegarde. */
    private fun changed() {
        grid.stepCount = session.project.stepCount
        roll.stepCount = session.project.stepCount
        grid.invalidate()
        refreshAll()
        scheduleSave()
    }

    private fun scheduleSave() {
        val pending = engine.pendingPadLoop
        if (pending >= 0 && queuedPattern !== session.project.patterns[pending]) cancelPadLaunch()
        panels.prune()
        if (clipPanel != null) refreshPadLauncher(force = true)
        refreshSpread?.invoke()
        refreshHistory()
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 600)
    }

    private fun newRegister() = resources.configuration.let { StudioRegister(it.screenWidthDp, it.screenHeightDp, requestedOctaves) }

    private fun focusRegister() {
        manualRegister = false
        register.focus(session.notesOf(selectedTrack), curTrack()?.let { StudioRegister.chipOctave(it) })
    }

    private fun shiftRegister(keys: Int) {
        manualRegister = true
        register.shift(keys)
        refreshRoll()
    }

    private fun revealNote(note: Note) {
        manualRegister = true
        register.reveal(note.pitch)
        roll.ensureVisible(note.step)
        refreshRoll()
    }

    private fun showRegisterMenu(anchor: View) {
        val notes = session.notesOf(selectedTrack)
        val below = notes.filter { it.pitch < register.low }.maxByOrNull { it.pitch }
        val above = notes.filter { it.pitch > register.high }.minByOrNull { it.pitch }
        val options = mutableListOf(getString(R.string.studio_register_focus))
        val targets = mutableListOf<Note?>(null)
        for (note in listOfNotNull(above, below)) {
            val label = if (note.pitch > register.high) R.string.studio_register_above else R.string.studio_register_below
            options += getString(label, pitchName(note.pitch))
            targets += note
        }
        val otherOctaves = 3 - register.octaves
        options += resources.getQuantityString(R.plurals.studio_octaves_count, otherOctaves, otherOctaves)
        dropdown(anchor, options, enabled = { it != 0 || notes.isNotEmpty() }) { i ->
            if (i == targets.size) {
                requestedOctaves = otherOctaves
                val low = register.low
                register = newRegister()
                register.setLow(low)
                if (!manualRegister) focusRegister()
                refreshRoll()
                return@dropdown
            }
            val note = targets[i]
            if (note != null) revealNote(note) else {
                focusRegister()
                refreshRoll()
            }
        }
    }

    // ---- Affichage -------------------------------------------------------------------------------

    private fun showTempoPanel(anchor: View) {
        val taps = TapTempo()
        val names = resources.getStringArray(R.array.studio_tempo_names)
        val box = FlowRow(this).apply { setPadding(dp(12), 0, dp(12), dp(10)); hGap = dp(6) }
        box.addView(outlined(R.string.studio_tap_tempo).apply {
            setOnClickListener {
                taps.tap(android.os.SystemClock.uptimeMillis())?.let { bpm ->
                    session.setBpm(bpm)
                    refreshTempo()
                    scheduleSave()
                }
            }
        })
        box.addView(outlined(R.string.studio_tempos).apply {
            setOnClickListener { button ->
                dropdown(button, TEMPO_PRESETS.mapIndexed { i, bpm -> getString(R.string.studio_tempo_option, names[i], bpm) }) { i ->
                    session.setBpm(TEMPO_PRESETS[i])
                    refreshTempo()
                    scheduleSave()
                }
            }
        })
        panels.show(R.string.studio_tap_tempo, getString(R.string.studio_tempo), box, 300, anchor)
    }

    private fun refreshTempo() {
        tempoText.text = getString(R.string.studio_tempo_format, session.project.bpm)
    }

    // ---- Mon son : un enregistrement du micro pour la piste Perc. ---------------------------------------------------

    private var afterMicPermission: (() -> Unit)? = null
    private val micPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        val action = afterMicPermission
        afterMicPermission = null
        if (ok) action?.invoke()
    }

    private fun withMic(action: () -> Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            afterMicPermission = action
            micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun showSamplePanel(anchor: View) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val status = TextView(this).apply { textSize = 15f }
        fun showDefaultStatus() = status.setText(if (engine.hasPercSample) R.string.studio_sample_mine else R.string.studio_sample_default)
        showDefaultStatus()
        box.addView(status)
        val row = FlowRow(this)
        val record = outlined(R.string.studio_sample_record)
        record.setOnClickListener {
            withMic {
                record.isEnabled = false
                status.setText(R.string.studio_sample_listening)
                Thread {
                    val pcm = SampleTools.record()
                    val clean = pcm?.let { SampleTools.clean(it) }
                    if (clean != null) SampleTools.save(this, clean)
                    runOnUiThread {
                        record.isEnabled = true
                        if (clean != null) {
                            engine.setPercSample(clean, SampleTools.RATE)
                            triggerPerc()
                            showDefaultStatus()
                        } else {
                            status.setText(if (pcm == null) R.string.studio_sample_failed else R.string.studio_sample_nothing)
                        }
                    }
                }.start()
            }
        }
        row.addView(record)
        row.addView(outlined(R.string.studio_sample_play).apply { setOnClickListener { triggerPerc() } })
        row.addView(outlined(R.string.studio_sample_remove).apply {
            setOnClickListener {
                engine.setPercSample(null, SampleTools.RATE)
                SampleTools.save(this@StudioActivity, null)
                showDefaultStatus()
            }
        })
        box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        panels.show(R.string.studio_sample_title, getString(R.string.studio_sample_title), box, 380, anchor)
    }

    // ---- Export ---------------------------------------------------------------------------------------------------

    /** Les sons enregistrés au micro, pour que le fichier sonne comme l'écoute. */
    private fun exportSounds(): StudioExporter.Sounds {
        val perc = SampleTools.load(this)
        return StudioExporter.Sounds(perc?.first, perc?.second ?: SampleTools.RATE, engine.synthSample)
    }

    /** Le son du micro pour le synthé, s'il y en a un : sa note est retrouvée à l'ouverture (rapide : une fraction de seconde de calcul). */
    private fun loadSynthSample() {
        val (data, rate) = SampleTools.load(this, SampleTools.SYNTH_FILE) ?: return
        engine.setSynthSample(SynthSample(data, rate, SampleTools.detectRoot(data, rate)))
    }

    /** Rend le fichier hors ligne (plus vite que le temps réel) avec une barre de progression, puis dit où il est. */
    private fun runExport(kind: Int) {
        // Pendant le rendu, la lecture se tait : le téléphone a besoin de toute sa puissance
        if (playing) togglePlay()
        val project = session.project
        val bar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
        val holder = FrameLayout(this).apply { setPadding(dp(24), dp(16), dp(24), 0); addView(bar) }
        // Lu par le fil de rendu, écrit par le bouton : partagé entre deux fils
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.studio_export_working)
            .setView(holder)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled.set(true) }
            .show()
        Thread {
            var shown = -1
            val saved = when (kind) {
                EXPORT_MIDI -> StudioFiles.saveMidi(this, project)
                else -> StudioFiles.saveWav(this, project, sf2?.factory, sounds = exportSounds()) { f ->
                    // Le rendu donne des nouvelles tous les 1 024 échantillons : l'écran n'en reçoit qu'une par pour mille gagné
                    val p = (f * 1000).toInt()
                    if (p != shown) {
                        shown = p
                        runOnUiThread { bar.progress = p }
                    }
                    !cancelled.get()
                }
            }
            runOnUiThread {
                dialog.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                when {
                    cancelled.get() -> Unit
                    saved == null -> MaterialAlertDialogBuilder(this).setMessage(R.string.studio_export_failed).setPositiveButton(android.R.string.ok, null).show()
                    kind == EXPORT_EDITOR -> startActivity(
                        Intent(this, AudioEditorLibraryActivity::class.java)
                            .putExtra(EXTRA_IMPORT_URI, saved.uri.toString())
                            .putExtra(EXTRA_IMPORT_NAME, saved.name.removeSuffix(".wav")),
                    )
                    else -> MaterialAlertDialogBuilder(this)
                        .setMessage(getString(R.string.studio_export_done, StudioFiles.FOLDER, saved.name))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }.start()
    }

    private fun currentPadScene(): Int {
        val p = session.project
        return when {
            playing && p.songMode -> p.chain.getOrNull(engine.songPosition) ?: p.current
            playing && p.view.isNotEmpty() -> p.view.getOrNull(engine.currentStep.coerceAtLeast(0) / p.baseSteps) ?: p.current
            else -> p.current
        }
    }

    private fun showTrackClips(anchor: View) {
        val panel = TrackClipsView(this, session, engine, ::outlined, ::trackName, ::showTrackClipActions, ::captureTrackClips)
        panel.refresh(currentPadScene(), !recorder.armed && !gestureTaking)
        panel.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { clipPanel = panel }
            override fun onViewDetachedFromWindow(v: View) { if (clipPanel === panel) clipPanel = null }
        })
        panels.show(R.string.studio_clips, getString(R.string.studio_clips), panel, 360, anchor,
            valid = { padsPane.visibility == View.VISIBLE })
    }

    private fun showTrackClipActions(anchor: View, track: Track, slot: Int) {
        val actions = listOf(getString(R.string.studio_clip_edit), getString(R.string.studio_clip_duplicate))
        dropdown(anchor, actions, enabled = { it == 0 || session.hasTrackClip(track.id, slot) }) { choice ->
            if (choice == 0) editTrackClip(track, slot)
            else {
                val labels = clipDestinations { !session.hasTrackClip(track.id, it) }
                dropdown(anchor, labels, enabled = { it != slot }) { destination ->
                    if (session.copyTrackClip(track.id, slot, destination)) changed()
                }
            }
        }
    }

    private fun clipDestinations(empty: (Int) -> Boolean) = loopNames.mapIndexed { slot, letter ->
        getString(if (empty(slot)) R.string.studio_clip_destination_free else R.string.studio_clip_destination_replace, letter)
    }

    private fun editTrackClip(track: Track, slot: Int) {
        cancelPadLaunch()
        engine.resetTrackClips()
        selectPadLoop(slot)
        if (track.melodic) {
            editedDrum = -1
            selectedTrack = track.id
            tabsGroup.check(tabNotesId)
        } else {
            tabsGroup.check(tabRhythmId)
        }
    }

    private fun captureTrackClips(anchor: View) {
        acceptPadLaunch()
        val base = currentPadScene()
        val clips = engine.activeTrackClips()
        if (clips.isEmpty()) return
        dropdown(anchor, clipDestinations(session::isPatternEmpty)) { destination ->
            cancelPadLaunch()
            session.captureTrackClips(base, clips, destination)
            engine.resetTrackClips()
            selectPadLoop(destination)
        }
    }

    private fun launchPadLoop(slot: Int) {
        acceptPadLaunch()
        val project = session.project
        if (!playing) {
            selectPadLoop(slot)
        } else if (slot == engine.pendingPadLoop ||
            (!project.songMode && project.view.isEmpty() && slot == project.current)) {
            cancelPadLaunch()
        } else {
            val next = project.patterns[slot]
            queuedPattern = next
            engine.queuePadLoop(slot, next)
        }
        refreshPadLauncher(force = true)
    }

    private fun selectPadLoop(slot: Int) {
        if (session.project.songMode) session.setSongMode(false)
        session.selectPattern(slot)
        grid.clearSelection()
        roll.clearSelection()
        changed()
    }

    private fun acceptPadLaunch() {
        val slot = engine.takeLaunchedPadLoop()
        if (slot < 0) return
        if (engine.pendingPadLoop < 0) queuedPattern = null
        selectPadLoop(slot)
    }

    private fun cancelPadLaunch() {
        engine.cancelPadLoop()
        queuedPattern = null
        acceptPadLaunch()
    }

    private fun refreshPadLauncher(force: Boolean = false) {
        val active = currentPadScene()
        val pending = engine.pendingPadLoop
        val enabled = !recorder.armed && !gestureTaking
        val revision = engine.trackClipRevision
        if (force || active != shownPadLoop || enabled != launchEnabled || revision != shownClipRevision) {
            shownClipRevision = revision
            clipPanel?.refresh(active, enabled)
        }
        if (!force && active == shownPadLoop && pending == shownPendingLoop && enabled == launchEnabled) return
        shownPadLoop = active
        shownPendingLoop = pending
        launchEnabled = enabled
        for (i in loopNames.indices) {
            val button = padLoopRow.getChildAt(i) as MaterialButton
            val waiting = i == pending
            button.isChecked = i == active
            button.strokeWidth = dp(if (i == active || waiting) 2 else 1)
            button.text = if (waiting) getString(R.string.studio_loop_queued, loopNames[i]) else loopNames[i]
            button.contentDescription = getString(if (waiting) R.string.studio_loop_waiting else R.string.studio_loop_launch, loopNames[i])
            button.isEnabled = enabled
        }
    }

    private fun refreshPatterns() {
        if (!::patternGroup.isInitialized) return
        val p = session.project
        val shown = p.viewSlots
        val was = building
        building = true
        // D'abord on coche, ensuite on décoche : il en reste toujours un
        for (k in 0 until StudioProject.SLOTS) if (k in shown && patternIds[k] !in patternGroup.checkedButtonIds) patternGroup.check(patternIds[k])
        for (k in 0 until StudioProject.SLOTS) if (k !in shown && patternIds[k] in patternGroup.checkedButtonIds) patternGroup.uncheck(patternIds[k])
        building = was
        // Les quatre destinations restent lisibles ; un point marque celles qui contiennent de la musique.
        for (k in 0 until StudioProject.SLOTS) {
            val button = patternGroup.findViewById<MaterialButton>(patternIds[k])
            val filled = !session.isPatternEmpty(k)
            if (filled != (button.icon != null)) button.icon = if (filled) android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(button.currentTextColor)
            } else null
            button.iconSize = dp(5)
            button.iconPadding = dp(4)
            button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        }
        // Plusieurs motifs à la suite : la lettre de chacun marque son début, les numéros repartent de 1
        val letters = resources.getStringArray(R.array.studio_pattern_names)
        val labels = if (shown.size > 1) shown.map { letters[it] } else emptyList()
        grid.sectionLen = p.baseSteps
        grid.sectionLabels = labels
        roll.sectionLen = p.baseSteps
        roll.sectionLabels = labels
        songButton.isSelected = p.songMode
        songButton.alpha = 1f
        songButton.setTextColor(if (p.songMode) 0xFFFFC857.toInt() else songDefaultColor)
    }

    /** Le morceau : la suite des motifs ; on l'écrit avec des boutons « + A » … et on le lance avec un interrupteur. */
    private fun showSongPanel(anchor: View) {
        val names = resources.getStringArray(R.array.studio_pattern_names)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        // Les blocs du morceau : glisser pour ranger, tirer hors de la rangée pour retirer ; celui qui joue s'allume
        val emptyText = TextView(this).apply { textSize = 16f; alpha = 0.75f; setText(R.string.studio_song_empty); setPadding(0, dp(8), 0, dp(8)) }
        val blocks = ChainView(this).apply {
            this.names = names.toList()
            colors = PATTERN_COLORS
            songBlocks = this
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) { if (songBlocks === v) songBlocks = null }
            })
        }
        val playSwitch = androidx.appcompat.widget.SwitchCompat(this).apply { setText(R.string.studio_song_play) }
        fun refresh() {
            val chain = session.project.chain
            blocks.chain = chain
            emptyText.visibility = if (chain.isEmpty()) View.VISIBLE else View.GONE
            playSwitch.isEnabled = chain.isNotEmpty()
            val was = playSwitch.isChecked
            if (was != session.project.songMode) playSwitch.isChecked = session.project.songMode
            refreshPatterns()
            scheduleSave()
        }
        blocks.onMove = { from, to -> session.chainMove(from, to); refresh() }
        blocks.onRemove = { index -> session.chainRemoveAt(index); refresh() }
        box.addView(emptyText)
        box.addView(blocks, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8); bottomMargin = dp(8) })
        val adds = FlowRow(this).apply { hGap = dp(8); vGap = dp(4) }
        for (k in 0 until StudioProject.SLOTS) {
            adds.addView(outlined(null).apply {
                text = getString(R.string.studio_song_add, names[k])
                setOnClickListener { session.chainAdd(k); refresh() }
            })
        }
        adds.addView(outlined(R.string.studio_song_clear).apply { setOnClickListener { session.chainClear(); refresh() } })
        box.addView(adds)
        playSwitch.setOnCheckedChangeListener { _, on ->
            if (on != session.project.songMode) {
                cancelPadLaunch()
                session.setSongMode(on)
                refreshPatterns()
                scheduleSave()
            }
        }
        box.addView(playSwitch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        refresh()
        panels.show(R.string.studio_song_title, getString(R.string.studio_song_title), box, 420, anchor, onHistory = { showSongPanel(anchor) })
    }

    private fun refreshTransport() {
        playButton.setText(if (playing) R.string.studio_stop else R.string.studio_play)
        requestFrame()
    }

    private fun refreshAll() {
        refreshTracks()
        refreshTransport()
        refreshTempo()
        refreshPatterns()
        bufferButton.text = getString(R.string.studio_buffer_label, bufferBlocks)
        refreshRoll()
        refreshNotesControls()
        refreshMixer()
        // Les niveaux des cases à notes suivent la gamme, la tonalité et le registre des notes
        if (padsPane.visibility == View.VISIBLE) refreshPads()
    }

    private fun refreshRoll() {
        displayMidiLights()
        val p = session.project
        val scale = Scales.byId(p.scaleId)
        roll.keyPc = p.keyPc
        roll.scale = scale
        roll.scaleLocked = scaleLocked
        roll.rows = (register.low..register.high).toList().toIntArray()
        registerButton.text = getString(R.string.studio_register_range, pitchName(register.low), pitchName(register.high))
        if (roll.pasteArmed) updatePasteTarget()
        roll.invalidate()
        keyboard.keyPc = p.keyPc
        keyboard.scale = scale
        keyboard.low = register.low
        keyboard.high = register.high
        keyboard.locked = scaleLocked
        theremin.keyPc = p.keyPc
        theremin.pitches = roll.rows.filter { Scales.contains(scale, p.keyPc, it) }.toIntArray()
    }

    /** Le clavier ou le thérémine, sous le piano roll. */
    private fun setTheremin(on: Boolean) {
        thereminOn = on
        if (thereminToggle.isChecked != on) thereminToggle.isChecked = on
        if (on) keyboard.releaseAll() else theremin.finish()
        theremin.visibility = if (on) View.VISIBLE else View.GONE
        keyboard.visibility = if (on) View.GONE else View.VISIBLE
        applyNotesLayout()
    }

    /**
     * La part du bas de l'onglet Notes : en portrait, le clavier (ou le thérémine) a plus de place ; en paysage, le piano roll
     * garde la sienne. Le thérémine, lui, prend toujours au moins un tiers de la hauteur.
     */
    private fun applyNotesLayout() {
        if (!::roll.isInitialized) return
        val portrait = resources.configuration.screenHeightDp > resources.configuration.screenWidthDp
        val below = when {
            thereminOn -> maxOf(resources.configuration.screenHeightDp / 3, 220)
            portrait -> 208
            else -> 158
        }
        roll.reserveBelow = dp(below)
        roll.requestLayout()
    }

    private fun refreshNotesControls() {
        refreshSelectionBar()
        val p = session.project
        val t = curTrack()
        val drumMode = editedDrum >= 0
        notesHeader.visibility = if (drumMode) View.GONE else View.VISIBLE
        notesContent.visibility = if (t != null && !drumMode) View.VISIBLE else View.GONE
        notesEmpty.visibility = if (t == null && !drumMode) View.VISIBLE else View.GONE
        for (v in trackDependent) v.visibility = if (t != null) View.VISIBLE else View.GONE
        drumPanel.visibility = if (drumMode) View.VISIBLE else View.GONE
        if (drumMode) refreshDrumPanel()
        refreshTrackStrip()
        val keyNames = resources.getStringArray(R.array.studio_key_names)
        keyButton.text = getString(R.string.studio_key_label, keyNames[p.keyPc])
        scaleButton.text = getString(R.string.studio_scale_label, getString(scaleName(p.scaleId)))
        if (lockSwitch.isChecked != scaleLocked) lockSwitch.isChecked = scaleLocked
        recordButton.setText(if (recorder.armed) R.string.studio_record_on else R.string.studio_record_off)
        recordButton.setBackgroundColor(if (recorder.armed) 0x66E74C3C else android.graphics.Color.TRANSPARENT)
        if (t == null) return
        val soundName = instrumentName(t)
        soundButton.text = soundName
        soundButton.contentDescription = getString(R.string.studio_sound_label, soundName)
        arpButton.setText(R.string.studio_arp_caption)
        arpButton.isSelected = t.arpMode != Arp.OFF
        arpButton.alpha = if (arpButton.isSelected) 1f else 0.8f
        arpButton.contentDescription = getString(R.string.studio_arp_label, getString(arpName(t.arpMode)))
        roll.accent = trackColor(t)
        keyboard.accent = trackColor(t)
        keyboard.invalidate()
        theremin.accent = trackColor(t)
        theremin.invalidate()
    }

    // ---- Les pistes -------------------------------------------------------------------------------------------------

    private fun curTrack(): Track? = session.project.trackOrNull(selectedTrack)

    private fun visibleTracks(): List<Track> = session.project.tracks.filter { !it.hidden }

    private fun defaultMix(t: Track): TrackMix = when (t.kind) {
        TrackKind.DRUM -> TrackMix.defaults()[t.voice.coerceIn(0, Inst.DRUM_COUNT - 1)]
        TrackKind.SYNTH -> TrackMix.defaults()[Inst.SYNTH]
        else -> Track.SF2_MIX
    }

    private fun trackColor(t: Track): Int = StudioColors.of(t)

    private val chipProgramNames by lazy { resources.getStringArray(R.array.gm_instruments) }
    private val fmProgramNames by lazy { resources.getStringArray(R.array.studio_fm_programs) }

    private fun instrumentName(t: Track): String = when {
        t.kind != TrackKind.SYNTH -> sf2ProgramName(t)
        t.synthBank == SynthBank.STUDIO -> soundName(t.synth)
        t.synthBank == SynthBank.FM -> fmProgramNames[t.fmProgram]
        else -> chipProgramNames[t.program]
    }

    private fun bankName(bank: SynthBank): Int = when (bank) {
        SynthBank.STUDIO -> R.string.studio_title
        SynthBank.SCC -> R.string.studio_bank_scc
        SynthBank.FAMICOM -> R.string.studio_bank_famicom
        SynthBank.FM -> R.string.studio_bank_fm
    }

    private fun sf2ProgramName(t: Track): String =
        sf2?.programs?.firstOrNull { it.bank == t.bank && it.program == t.program }?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.studio_sf2_default_name)

    private fun baseName(t: Track): String = when (t.kind) {
        TrackKind.DRUM -> getString(DRUM_NAMES[t.voice.coerceIn(0, Inst.DRUM_COUNT - 1)])
        TrackKind.SYNTH -> getString(R.string.studio_lane_synth)
        else -> sf2ProgramName(t)
    }

    // ---- Réglages d'un pas ------------------------------------------------------------------------------------------

    /** Ce qu'on a copié, piste par piste : les prochains pas posés sur la piste reprennent sa force, sa longueur et sa hauteur. */
    private val pens = HashMap<Int, StudioSession.StepInfo>()

    /** Un pas qu'on vient de poser reprend ce qui a été copié pour sa piste (la hauteur d'une note vient de l'endroit touché). */
    private fun applyPen(trackId: Int, step: Int) {
        val pen = pens[trackId] ?: return
        val melodic = session.project.trackOrNull(trackId)?.melodic == true
        session.editStep(
            trackId, step, velocity = pen.velocity, length = if (melodic) pen.length else null,
            pitch = if (melodic) null else pen.pitch, hold = if (melodic) null else pen.hold,
        )
    }

    private fun pitchName(pitch: Int): String =
        resources.getStringArray(R.array.studio_key_names)[((pitch % 12) + 12) % 12] + (pitch / 12 - 1)

    /**
     * La bulle d'un pas, près du doigt : force, longueur et hauteur (pour une piste à notes), « Copier » pour que les prochains
     * pas de cette piste reprennent ces réglages. Chaque réglage se fait entendre.
     */
    private fun showStepBubble(trackId: Int, step: Int, screenX: Float, screenY: Float, notePitch: Int? = null) {
        val t = session.project.trackOrNull(trackId) ?: return
        var selectedPitch = notePitch
        fun targetNote() = selectedPitch?.let { pitch ->
            session.notesOf(trackId).firstOrNull { it.step == step && it.pitch == pitch }
        }
        fun info(): StudioSession.StepInfo? {
            if (selectedPitch == null) return session.stepInfo(trackId, step)
            val n = targetNote() ?: return null
            return StudioSession.StepInfo(n.velocity, n.length, n.pitch)
        }
        fun edit(velocity: Float? = null, length: Int? = null, pitch: Int? = null, hold: Int? = null, cond: Int? = null) {
            if (selectedPitch == null) {
                session.editStep(trackId, step, velocity, length, pitch, hold, cond)
            } else {
                val n = targetNote() ?: return
                val target = (pitch ?: n.pitch).coerceIn(0, 127)
                session.editNote(n, step, target, length ?: n.length, velocity ?: n.velocity)
                selectedPitch = target
            }
        }
        fun preview() {
            val n = targetNote()
            if (n == null) previewLane(trackId, step) else previewPitch(n.pitch, n.velocity, track = trackId)
        }
        if (info() == null) return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val forceText = TextView(this).apply { textSize = 14f; gravity = Gravity.END }
        val lengthText = TextView(this).apply { textSize = 14f; gravity = Gravity.CENTER; minWidth = dp(64) }
        val pitchText = TextView(this).apply { textSize = 14f; gravity = Gravity.CENTER; minWidth = dp(64) }

        fun refreshTexts() {
            val i = info() ?: return
            forceText.text = getString(R.string.studio_percent, (i.velocity * 100).roundToInt())
            lengthText.text = if (t.melodic) i.length.toString() else getString(R.string.studio_percent, i.hold)
            pitchText.text = if (t.melodic) pitchName(i.pitch) else getString(R.string.studio_signed, i.pitch - 60)
        }

        fun redraw() {
            grid.invalidate()
            roll.invalidate()
            scheduleSave()
            refreshTexts()
        }

        fun row(labelRes: Int, control: View, controlWeight: Float = 0f): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply { setText(labelRes); textSize = 14f }, LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(control, if (controlWeight > 0f) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, controlWeight) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        fun stepper(text: TextView, onLess: () -> Unit, onMore: () -> Unit): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(square(R.string.studio_tempo_less).also { it.setOnClickListener { onLess() } })
            addView(text)
            addView(square(R.string.studio_tempo_more).also { it.setOnClickListener { onMore() } })
        }

        fun rowParams(top: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = top }

        // Force
        val bar = SeekBar(this).apply {
            max = 90
            progress = ((info()?.velocity ?: 1f) * 100).roundToInt() - 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    edit(velocity = (progress + 10) / 100f)
                    redraw()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) = preview()
            })
        }
        val forceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(forceText, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        box.addView(row(R.string.studio_step_force, forceRow, 1f), rowParams(dp(10)))

        // Longueur (une piste à notes seulement : un pas de batterie est un coup)
        if (t.melodic) {
            val control = stepper(
                lengthText,
                { info()?.let { edit(length = it.length - 1) }; redraw() },
                { info()?.let { edit(length = it.length + 1) }; redraw() },
            )
            box.addView(row(R.string.studio_step_length, control), rowParams(dp(6)))
        } else if (t.sfKit < 0) {
            // Un coup de batterie dure plus ou moins longtemps (la caisse claire qui résonne, le charleston qui claque)
            val control = stepper(
                lengthText,
                { info()?.let { edit(hold = it.hold - 10) }; redraw(); preview() },
                { info()?.let { edit(hold = it.hold + 10) }; redraw(); preview() },
            )
            box.addView(row(R.string.studio_drum_length, control), rowParams(dp(6)))
        }

        // Hauteur
        val pitchControl = stepper(
            pitchText,
            { info()?.let { edit(pitch = it.pitch - 1) }; redraw(); preview() },
            { info()?.let { edit(pitch = it.pitch + 1) }; redraw(); preview() },
        )
        box.addView(row(R.string.studio_drum_tune, pitchControl), rowParams(dp(6)))

        // Quand le coup joue : à chaque tour, un tour sur deux, le 4e tour, ou au hasard (batterie seulement)
        val condButton = outlined(null)
        val conds = resources.getStringArray(R.array.studio_step_conds)
        fun refreshCond() {
            condButton.text = conds.getOrElse(info()?.cond ?: 0) { conds[0] }
        }
        if (!t.melodic) {
            condButton.setOnClickListener {
                dropdown(condButton, conds.toList()) { which ->
                    edit(cond = which)
                    refreshCond()
                    redraw()
                }
            }
            refreshCond()
            box.addView(row(R.string.studio_step_when, condButton), rowParams(dp(6)))
        }

        // Copier / Oublier / Effacer
        val actions = FlowRow(this).apply { hGap = dp(6) }
        val copy = outlined(null)
        if (!t.melodic) actions.addView(outlined(R.string.studio_spread).apply {
            setOnClickListener { showSpread(it, trackId) }
        })
        val reset = outlined(R.string.studio_drum_reset)
        fun refreshCopy() = copy.setText(if (pens.containsKey(trackId)) R.string.studio_step_stamped else R.string.studio_step_stamp)
        copy.setOnClickListener {
            info()?.let { pens[trackId] = it }
            refreshCopy()
        }
        // Tout remettre d'origine : ce pas retrouve ses réglages normaux et la copie de la piste est oubliée
        reset.setOnClickListener {
            pens.remove(trackId)
            if (selectedPitch == null) session.resetStep(trackId, step) else edit(velocity = 1f, length = 1)
            bar.progress = 90
            redraw()
            refreshCopy()
            refreshCond()
            preview()
        }
        refreshCopy()
        actions.addView(copy, wrap(end = dp(8)))
        actions.addView(reset, wrap())
        box.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })

        val slots = session.project.viewSlots
        val base = session.project.baseSteps
        refreshTexts()
        panels.show(R.string.studio_step_force, getString(R.string.studio_step_title, trackName(t), step % base + 1), box, 360, window.decorView, screenX to screenY,
            onHistory = { showStepBubble(trackId, step, screenX, screenY, selectedPitch) }) {
            session.project.viewSlots == slots && session.project.baseSteps == base && session.project.trackOrNull(trackId)?.hidden == false && info() != null
        }
    }

    // ---- Sélection : copier, couper, coller, supprimer -------------------------------------------------------------

    private lateinit var selectionBar: FlowRow
    private lateinit var selectToggle: MaterialButton
    private lateinit var copyButton: MaterialButton
    private lateinit var cutButton: MaterialButton
    private lateinit var duplicateButton: MaterialButton
    private lateinit var pasteButton: MaterialButton
    private lateinit var pasteHereButton: MaterialButton
    private lateinit var cancelPasteButton: MaterialButton
    private lateinit var pasteTarget: TextView
    private var clipSourceStep = 0
    private var clipLaneOffsets = emptyMap<Int, Int>()
    private val patternLetters by lazy { resources.getStringArray(R.array.studio_pattern_names) }
    private lateinit var deleteButton: MaterialButton
    private var selectMode = false
    private var clip: StudioSession.Clip? = null

    /** Ce qui est entouré, dans la page ouverte : les pistes, les pas, et (piano roll) les hauteurs. */
    private class Spec(val tracks: Set<Int>, val s0: Int, val s1: Int, val p0: Int, val p1: Int)

    private fun selectionSpec(): Spec? {
        if (onNotesTab) {
            val f = roll.selection ?: return null
            if (selectedTrack < 0 || editedDrum >= 0) return null
            return Spec(setOf(selectedTrack), f.stepFrom, f.stepTo, f.pitchLow, f.pitchHigh)
        }
        val f = grid.selection ?: return null
        val ids = (f.laneFrom..f.laneTo).mapNotNull { gridIds.getOrNull(it) }.toSet()
        if (ids.isEmpty()) return null
        return Spec(ids, f.stepFrom, f.stepTo, 0, 127)
    }

    /** La barre de sélection : le bouton « Sélection » toujours, les actions seulement une fois la sélection ouverte. */
    private fun buildSelectionBar(): FlowRow {
        val bar = flowRow(topMargin = dp(6))
        copyButton = outlined(R.string.studio_step_copy).apply { setOnClickListener { copySelection(cut = false) } }
        cutButton = outlined(R.string.studio_cut).apply { setOnClickListener { copySelection(cut = true) } }
        pasteButton = outlined(R.string.studio_paste).apply { setOnClickListener { setPasteArmed(true) } }
        deleteButton = outlined(R.string.studio_delete_selection).apply { setOnClickListener { deleteSelection() } }
        duplicateButton = outlined(R.string.studio_duplicate).apply { setOnClickListener { duplicateSelection() } }
        pasteHereButton = MaterialButton(this).apply {
            isAllCaps = false
            setText(R.string.studio_paste_here)
            setOnClickListener { pasteAtGhost() }
        }
        cancelPasteButton = outlined(android.R.string.cancel).apply { setOnClickListener { setPasteArmed(false) } }
        pasteTarget = TextView(this).apply { textSize = 15f; gravity = Gravity.CENTER_VERTICAL }
        for ((button, drawable) in listOf(copyButton to R.drawable.ic_px_copy, cutButton to R.drawable.ic_px_cut,
                deleteButton to R.drawable.ic_px_delete)) {
            button.contentDescription = button.text
            button.tooltipText = button.text
            button.text = null
            button.setIconResource(drawable)
            button.iconPadding = 0
            button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            button.minWidth = dp(48)
            button.minimumWidth = dp(48)
            button.setPadding(dp(10), 0, dp(10), 0)
        }
        for (b in listOf(duplicateButton, copyButton, cutButton, deleteButton, pasteButton, pasteTarget, pasteHereButton, cancelPasteButton)) {
            bar.addView(b, wrap())
        }
        bar.visibility = View.GONE
        return bar
    }

    /** Le bouton « Sélection », une main, posé à côté des motifs A à D. */
    private fun buildSelectToggle(): MaterialButton = outlined(null).apply {
        isCheckable = true
        contentDescription = getString(R.string.studio_select)
        icon = com.Atom2Universe.app.studio.ui.HandIconDrawable()
        iconSize = dp(24)
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        iconPadding = 0
        minWidth = 0
        minimumWidth = dp(44)
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { setSelectMode(isChecked) }
    }

    private fun setSelectMode(on: Boolean) {
        selectMode = on
        grid.selectMode = on
        roll.selectMode = on
        if (!on) setPasteArmed(false)
        refreshSelectionBar()
    }

    private fun setPasteArmed(on: Boolean, firstStep: Int? = null) {
        val c = clip
        val armed = on && c != null && !c.isEmpty && (!onNotesTab || canPasteNotes(c))
        grid.pasteArmed = armed && !onNotesTab
        roll.pasteArmed = armed && onNotesTab
        if (armed) prepareGhost(c, firstStep)
        refreshSelectionBar()
    }

    private fun canPasteNotes(c: StudioSession.Clip) =
        c.hits.isEmpty() && c.notes.isNotEmpty() && c.tracks.size == 1 && selectedTrack >= 0

    private fun prepareGhost(c: StudioSession.Clip, firstStep: Int?) {
        if (onNotesTab) {
            roll.pasteSpan = c.span
            roll.pasteNotes = c.notes.map { intArrayOf(it.pitch, it.rel, it.length) }
            roll.pasteLow = c.lowPitch
            roll.pastePitchSpan = c.highPitch - c.lowPitch
            register.reveal(c.lowPitch)
            refreshRoll()
            roll.placePaste(c.lowPitch, firstStep ?: roll.selection?.stepFrom ?: clipSourceStep)
            roll.ensureVisible(roll.pasteStep)
        } else {
            val cells = ArrayList<IntArray>()
            for (h in c.hits) cells.add(intArrayOf(clipLaneOffsets.getValue(h.track), h.rel))
            for (n in c.notes) for (k in 0 until n.length) cells.add(intArrayOf(clipLaneOffsets.getValue(n.track), n.rel + k))
            grid.pasteCells = cells
            grid.pasteSpan = c.span
            val first = clipLaneOffsets.entries.first { it.value == 0 }.key
            grid.placePaste(gridIds.indexOf(first).coerceAtLeast(0), firstStep ?: grid.selection?.stepFrom ?: clipSourceStep)
            grid.ensureVisible(grid.pasteStep)
            grid.ensureLaneVisible(grid.pasteLane)
        }
    }

    /** La grille conserve les écarts entre pistes. Une batterie aboutit sur une batterie, une mélodie sur un instrument. */
    private fun gridDestinations(c: StudioSession.Clip): Map<Int, Int>? {
        val targets = mutableMapOf<Int, Int>()
        for ((source, offset) in clipLaneOffsets) {
            val id = gridIds.getOrNull(grid.pasteLane + offset) ?: return null
            val melodic = session.project.trackOrNull(id)?.melodic ?: return null
            if (melodic != c.notes.any { it.track == source }) return null
            targets[source] = id
        }
        return targets
    }

    private fun fitsAt(c: StudioSession.Clip, step: Int): Boolean {
        val base = session.project.baseSteps
        return step >= 0 && step + c.span <= session.project.stepCount &&
            c.notes.all { (step + it.rel) % base + it.length <= base }
    }

    private fun updatePasteTarget() {
        val c = clip ?: return
        val step = if (onNotesTab) roll.pasteStep else grid.pasteStep
        if (step < 0) return
        val valid = fitsAt(c, step) && if (onNotesTab) {
            canPasteNotes(c)
        } else gridDestinations(c) != null
        grid.pasteValid = valid
        roll.pasteValid = valid
        pasteHereButton.isEnabled = valid
        val p = session.project
        val slot = p.viewSlots.getOrNull(step / p.baseSteps) ?: return
        val target = if (onNotesTab) pitchName(roll.pasteAnchorPitch)
            else grid.laneNames.getOrNull(grid.pasteLane).orEmpty()
        pasteTarget.text = if (valid) getString(R.string.studio_paste_target, patternLetters[slot], step % p.baseSteps + 1, target)
            else getString(R.string.studio_no_room)
    }

    private fun pasteAtGhost() {
        val c = clip ?: return
        val step = if (onNotesTab) roll.pasteStep else grid.pasteStep
        if (!fitsAt(c, step)) return
        if (roll.pasteArmed && canPasteNotes(c) && roll.pasteValid) {
            val pitch = roll.pasteAnchorPitch
            session.paste(c, step, pitchShift = pitch - c.lowPitch, noteTrack = selectedTrack)
            roll.setSelection(pitch, pitch + roll.pastePitchSpan, step, step + c.span - 1)
        } else if (grid.pasteArmed) {
            val destinations = gridDestinations(c) ?: return
            session.paste(c, step, trackMap = destinations)
            grid.setSelection(grid.pasteLane, grid.pasteLane + clipLaneOffsets.values.max(), step, step + c.span - 1)
        } else return
        setPasteArmed(false)
        afterSelectionEdit()
    }

    /** Copie la sélection juste après elle-même ; la copie devient la sélection, on peut donc répéter. */
    private fun duplicateSelection() {
        val s = selectionSpec() ?: return
        val at = s.s1 + 1
        val steps = session.project.stepCount
        if (at >= steps) return
        val c = session.copyRange(s.tracks, s.s0, s.s1, s.p0, s.p1)
        if (onNotesTab) session.paste(StudioSession.Clip(emptyList(), c.notes), at, noteTrack = selectedTrack) else session.paste(c, at)
        val end = minOf(steps - 1, at + (s.s1 - s.s0))
        if (onNotesTab) {
            roll.selection?.let { roll.setSelection(it.pitchLow, it.pitchHigh, at, end) }
            roll.ensureVisible(end)
        } else {
            grid.selection?.let { grid.setSelection(it.laneFrom, it.laneTo, at, end) }
            grid.ensureVisible(end)
        }
        afterSelectionEdit()
        refreshSelectionBar()
    }

    /** Montre ou cache la barre (pas sur le mixeur ni sur le son d'une batterie) et grise ce qui n'a pas de sens pour l'instant. */
    private fun refreshSelectionBar() {
        if (!::selectionBar.isInitialized) return
        val relevant = (!onNotesTab || selectedTrack >= 0) && mixerPane.visibility != View.VISIBLE && padsPane.visibility != View.VISIBLE && !(onNotesTab && editedDrum >= 0)
        selectionBar.visibility = if (relevant && selectMode) View.VISIBLE else View.GONE
        selectToggle.visibility = if (relevant) View.VISIBLE else View.GONE
        if (selectToggle.isChecked != selectMode) selectToggle.isChecked = selectMode
        val placing = roll.pasteArmed || grid.pasteArmed
        val spec = selectionSpec()
        val c = clip
        val canPaste = c != null && !c.isEmpty && (!onNotesTab || canPasteNotes(c))
        val selected = spec?.let { session.copyRange(it.tracks, it.s0, it.s1, it.p0, it.p1) }
        val hasNotes = selected != null && !selected.isEmpty
        for (b in listOf(duplicateButton, copyButton, cutButton, deleteButton)) {
            b.visibility = if (!placing) View.VISIBLE else View.GONE
            b.isEnabled = hasNotes
        }
        duplicateButton.isEnabled = hasNotes && fitsAt(selected, spec.s1 + 1)
        pasteButton.visibility = if (!placing && canPaste) View.VISIBLE else View.GONE
        for (v in listOf(pasteTarget, pasteHereButton, cancelPasteButton)) v.visibility = if (placing) View.VISIBLE else View.GONE
        if (placing) updatePasteTarget()
    }

    private fun copySelection(cut: Boolean) {
        val s = selectionSpec() ?: return
        val c = session.copyRange(s.tracks, s.s0, s.s1, s.p0, s.p1)
        if (c.isEmpty) return
        val lanes = c.tracks.associateWith { gridIds.indexOf(it) }
        val first = lanes.values.min()
        if (first < 0) return
        clip = c.alignedToFirstEvent()
        clipLaneOffsets = lanes.mapValues { it.value - first }
        clipSourceStep = s.s0 + c.firstStep
        if (cut) {
            session.clearRange(s.tracks, s.s0, s.s1, s.p0, s.p1)
            afterSelectionEdit()
        }
        setPasteArmed(true, clipSourceStep)
    }

    private fun deleteSelection() {
        val s = selectionSpec() ?: return
        session.clearRange(s.tracks, s.s0, s.s1, s.p0, s.p1)
        afterSelectionEdit()
        refreshSelectionBar()
    }

    private fun afterSelectionEdit() {
        grid.invalidate()
        roll.invalidate()
        scheduleSave()
    }

    /** Le petit chiffre d'une piste de synthé ou de soundfont quand il y en a plusieurs du même genre (« 2 » : le deuxième) ; sinon rien. */
    private fun trackBadge(t: Track): String {
        if (t.kind == TrackKind.DRUM) return ""
        val same = session.project.tracks.filter { it.kind == t.kind }
        return if (same.size > 1) (same.indexOfFirst { it.id == t.id } + 1).toString() else ""
    }

    /** Le nom d'une piste ; deux pistes du même nom se numérotent (« Synthé 1 », « Synthé 2 »). */
    private fun trackName(t: Track): String {
        val base = baseName(t)
        val same = session.project.tracks.filter { baseName(it) == base }
        return if (same.size > 1) "$base ${same.indexOfFirst { it.id == t.id } + 1}" else base
    }

    /** La piste montrée par l'onglet Notes doit exister, jouer des notes et être visible ; sinon la première qui convient. */
    private fun ensureSelected() {
        val p = session.project
        val t = p.trackOrNull(selectedTrack)
        if (t == null || !t.melodic || t.hidden) selectedTrack = p.tracks.firstOrNull { it.melodic && !it.hidden }?.id ?: -1
        // Une piste de batterie ouverte qui a disparu (ou qu'on a cachée) : retour aux notes
        if (editedDrum >= 0 && p.trackOrNull(editedDrum)?.let { it.kind == TrackKind.DRUM && !it.hidden } != true) editedDrum = -1
    }

    /** Redessine la grille et le mixeur seulement si la liste des pistes a changé. */
    private fun refreshTracks() {
        ensureSelected()
        val sig = session.project.tracks.joinToString(",") {
            // Seul le soundfont nomme sa piste d'après le programme. Parcourir les sons SCC garde la sélection de notes.
            val namedProgram = if (it.kind == TrackKind.SF2) "${it.bank}:${it.program}" else ""
            "${it.id}:${it.kind}:${it.voice}:${it.hidden}:$namedProgram"
        }
        if (sig == trackSignature) return
        setPasteArmed(false)
        grid.clearSelection()
        trackSignature = sig
        val visible = visibleTracks()
        gridIds = visible.map { it.id }
        grid.laneColors = visible.map { trackColor(it) }.toIntArray()
        grid.laneIcons = visible.map { InstrumentIcons.of(it) }.toIntArray()
        grid.laneBadges = visible.map { trackBadge(it) }
        grid.laneNames = visible.map { trackName(it) }
        rebuildMixer()
        refreshPads()
    }

    private fun triggerPerc() {
        session.project.tracks.firstOrNull { it.kind == TrackKind.DRUM && it.voice == Inst.PERC }?.let { engine.trigger(it.id, 60, 1f, recordHit = false) }
    }

    /** La ligne de pastilles de l'onglet Notes : une par piste à notes (sa couleur, son nom), et « + » pour en ajouter. */
    private fun refreshTrackStrip() {
        trackStrip.removeAllViews()
        for (t in session.project.tracks.filter { !it.hidden }) {
            val c = chip(trackName(t)) {
                keyboard.releaseAll()
                theremin.finish()
                setPasteArmed(false)
                roll.clearSelection()
                if (t.melodic) {
                    editedDrum = -1
                    if (selectedTrack != t.id) {
                        selectedTrack = t.id
                        focusRegister()
                    }
                } else {
                    editedDrum = t.id
                }
                refreshRoll()
                refreshNotesControls()
            }
            styleTrackChip(c, t, if (editedDrum >= 0) t.id == editedDrum else t.id == selectedTrack)
            trackStrip.addView(c)
        }
        trackStrip.addView(chip(null, R.drawable.ic_px_add) { showAddTrackSheet() })
    }

    /** Le contour d'une pastille de piste porte sa couleur ; la piste ouverte ([on]) a un trait plus épais et un fond teinté. */
    private fun styleTrackChip(c: View, t: Track, on: Boolean) {
        val color = trackColor(t)
        val raised = ContextCompat.getColor(this, R.color.audio_surface_raised)
        val shape = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(17).toFloat()
            setColor(if (on) androidx.core.graphics.ColorUtils.compositeColors((color and 0x00FFFFFF) or 0x40000000, raised) else raised)
            setStroke(dp(if (on) 2 else 1), if (on) color else androidx.core.graphics.ColorUtils.blendARGB(raised, color, 0.48f))
        }
        c.background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.audio_ripple)), shape, null,
        )
    }

    /** Ouvre le soundfont de l'appli (hors du fil de l'écran) puis fait [action] ; sans soundfont, le dit. */
    private fun withSf2(action: (Sf2Bridge) -> Unit) {
        sf2?.let {
            action(it)
            return
        }
        Thread {
            val bridge = Sf2Bridge.open(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (bridge == null) {
                    MaterialAlertDialogBuilder(this).setMessage(R.string.studio_no_soundfont).setPositiveButton(android.R.string.ok, null).show()
                    return@runOnUiThread
                }
                sf2 = bridge
                engine.sf2Factory = bridge.factory
                trackSignature = ""
                action(bridge)
            }
        }.start()
    }

    /** La liste des instruments du soundfont, dans une fenêtre qu'on fait défiler. */
    private fun pickProgram(bridge: Sf2Bridge, onPick: (bank: Int, program: Int) -> Unit) {
        val list = bridge.programs
        val names = list.map { it.name.trim().ifEmpty { "${it.bank}:${it.program}" } }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.studio_sf2_pick)
            .setItems(names) { _, which -> onPick(list[which].bank, list[which].program) }
            .show()
    }

    private fun pickSf2Program(trackId: Int) {
        withSf2 { bridge ->
            pickProgram(bridge) { bank, program ->
                session.setSf2Program(trackId, bank, program)
                changed()
                previewKeyNote()
            }
        }
    }

    /** Ajoute une piste, la choisit si elle joue des notes, et met tout à jour. */
    private fun addTrack(kind: Int, voice: Int = 0, bank: Int = 0, program: Int = 0) {
        val id = session.addTrack(kind, voice, bank, program)
        if (id < 0) return
        if (kind != TrackKind.DRUM) {
            selectedTrack = id
            focusRegister()
        }
        changed()
    }

    /** La feuille « ajouter une piste » (touchée sur la rangée vide de la grille) : ce qu'on veut ajouter, ou gérer les pistes. */
    private fun showAddTrackSheet() {
        val add = R.drawable.ic_px_add
        actionSheet(getString(R.string.studio_tracks), listOf(
            SheetItem(add, getString(R.string.studio_add_drum)) {
                actionSheet(getString(R.string.studio_add_drum), DRUM_NAMES.mapIndexed { voice, name ->
                    SheetItem(add, getString(name)) { addTrack(TrackKind.DRUM, voice) }
                }).show()
            },
            SheetItem(add, getString(R.string.studio_add_synth)) { addTrack(TrackKind.SYNTH) },
            SheetItem(add, getString(R.string.studio_add_sf2)) {
                withSf2 { bridge -> pickProgram(bridge) { bank, program -> addTrack(TrackKind.SF2, bank = bank, program = program) } }
            },
            SheetItem(R.drawable.ic_px_more, getString(R.string.studio_manage_tracks)) { showTracksSheet() },
        )).show()
    }

    /** La feuille pour montrer ou cacher chaque piste, ou la retirer. */
    private fun showTracksSheet() {
        bottomSheet(getString(R.string.studio_manage_tracks)) { root, _ ->
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            fun fill() {
                list.removeAllViews()
                for (t in session.project.tracks) {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    row.addView(View(this).apply {
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(trackColor(t))
                        }
                    }, LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(10) })
                    val name = TextView(this).apply {
                        text = trackName(t)
                        textSize = 16f
                        alpha = if (t.hidden) 0.5f else 1f
                    }
                    row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(SwitchCompat(this).apply {
                        isChecked = !t.hidden
                        setOnCheckedChangeListener { _, on ->
                            session.setHidden(t.id, !on)
                            name.alpha = if (on) 1f else 0.5f
                            changed()
                        }
                    })
                    row.addView(TextView(this).apply {
                        text = getString(R.string.studio_track_remove_glyph)
                        contentDescription = getString(R.string.studio_track_remove)
                        textSize = 18f
                        setPadding(dp(16), dp(8), dp(4), dp(8))
                        setOnClickListener {
                            session.removeTrack(t.id)
                            changed()
                            fill()
                        }
                    })
                    list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
                }
            }
            fill()
            root.addView(list)
        }.show()
    }

    private fun refreshStats() {
        val s = output.stats
        if (s.failed) {
            statsText.setText(R.string.studio_audio_failed)
            return
        }
        val rate = s.sampleRate.coerceAtLeast(1)
        statsText.text = getString(
            R.string.studio_stats,
            s.sampleRate,
            s.bufferFrames * 1000f / rate,
            s.burstFrames,
            (s.loadAverage * 100).roundToInt(),
            (s.loadMax * 100).roundToInt(),
            s.underruns,
            engine.takePeak(),
            engine.activeVoices,
            getString(if (s.lowLatency) R.string.studio_yes else R.string.studio_no),
        )
    }

    private fun scaleName(id: String): Int = when (id) {
        "major_pent" -> R.string.studio_scale_major_pent
        "minor" -> R.string.studio_scale_minor
        "major" -> R.string.studio_scale_major
        "dorian" -> R.string.studio_scale_dorian
        "blues" -> R.string.studio_scale_blues
        "chromatic" -> R.string.studio_scale_chromatic
        else -> R.string.studio_scale_minor_pent
    }

    /** Le nom d'un son du synthé : celui d'un son d'origine, « Mon son (micro) », ou le nom donné par l'utilisateur. */
    private fun soundName(patch: SynthPatch): String = when {
        patch.id.startsWith(SynthPatch.MINE_PREFIX) -> MySounds.nameOf(patch.id)
        patch.id == SynthPresets.MY_VOICE.id -> getString(R.string.studio_preset_my_voice)
        else -> getString(presetName(patch.id))
    }

    private fun presetName(id: String): Int = when (id) {
        "sub" -> R.string.studio_preset_sub
        "acid" -> R.string.studio_preset_acid
        "dist_bass" -> R.string.studio_preset_dist_bass
        "wobble" -> R.string.studio_preset_wobble
        "pad" -> R.string.studio_preset_pad
        "strings" -> R.string.studio_preset_strings
        "drone" -> R.string.studio_preset_drone
        "lead" -> R.string.studio_preset_lead
        "arp" -> R.string.studio_preset_arp
        "pluck" -> R.string.studio_preset_pluck
        "bell" -> R.string.studio_preset_bell
        "organ" -> R.string.studio_preset_organ
        "mallet" -> R.string.studio_preset_mallet
        "brass" -> R.string.studio_preset_brass
        "pick_bass" -> R.string.studio_preset_pick_bass
        "octave_bass" -> R.string.studio_preset_octave_bass
        "tines" -> R.string.studio_preset_tines
        "harpsichord" -> R.string.studio_preset_harpsichord
        "flute" -> R.string.studio_preset_flute
        "clarinet" -> R.string.studio_preset_clarinet
        "glass_pad" -> R.string.studio_preset_glass_pad
        "fifth_pad" -> R.string.studio_preset_fifth_pad
        "kalimba" -> R.string.studio_preset_kalimba
        "arcade" -> R.string.studio_preset_arcade
        else -> R.string.studio_preset_bass
    }

    // ---- Petits outils de vues ---------------------------------------------------------------------

    /** Un menu court qui s'ouvre juste sous le bouton touché (pas de fenêtre qui voile la page). */
    private fun dropdown(anchor: View, labels: List<String>, enabled: (Int) -> Boolean = { true }, onPick: (Int) -> Unit) {
        val menu = androidx.appcompat.widget.PopupMenu(this, anchor)
        labels.forEachIndexed { i, label -> menu.menu.add(0, i, i, label).isEnabled = enabled(i) }
        menu.setOnMenuItemClickListener {
            onPick(it.itemId)
            true
        }
        menu.show()
    }

    /** Un bouton à contour, texte sans espacement exagéré entre les lettres. */
    private fun outlined(textRes: Int?): MaterialButton =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            if (textRes != null) setText(textRes)
            isAllCaps = false
            letterSpacing = 0.02f
            textSize = 14f
            insetTop = 0
            insetBottom = 0
            minimumHeight = dp(48)
        }

    /** Un petit bouton carré (« − », « + »). */
    private fun square(textRes: Int): MaterialButton = outlined(textRes).apply {
        minWidth = 0
        minimumWidth = 0
        setPadding(0, 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        textSize = 18f
    }

    /** Petit texte gris posé devant une commande (« Tonalité », « Gamme »…). */
    private fun caption(textRes: Int) = TextView(this).apply {
        setText(textRes)
        textSize = 13f
        alpha = 0.75f
    }

    /** Un curseur avec son nom au-dessus. */
    private fun labeled(textRes: Int, bar: SeekBar): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            setText(textRes)
            textSize = 12f
            alpha = 0.75f
            setPadding(dp(4), 0, 0, 0)
        })
        addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun slider(max: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
        this.max = max
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun flowRow(topMargin: Int): FlowRow = FlowRow(this).apply {
        hGap = dp(8)
        vGap = dp(2)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { this.topMargin = topMargin }
    }

    /** Un appui répète l'action ; maintenu, elle se répète de plus en plus vite. */
    private fun holdToRepeat(button: MaterialButton, action: () -> Unit) {
        val repeat = object : Runnable {
            override fun run() {
                action()
                handler.postDelayed(this, 90)
            }
        }
        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    action()
                    handler.postDelayed(repeat, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeat)
                }
            }
            true
        }
    }

    /** Des marges pour une vue posée dans une [FlowRow] ou un LinearLayout. */
    private fun wrap(end: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { marginEnd = end }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val EXPORT_WAV = 0
        const val EXPORT_MIDI = 1
        const val EXPORT_EDITOR = 2

        /** Volume maximal d'une piste : le haut du fader. */
        const val LEVEL_MAX = 1.5f

        /** Les réglages du Studio gardés d'une fois sur l'autre (pas ceux d'un morceau). */
        const val PREFS = "studio_prefs"
        const val PREF_ROLL_RATE = "pads_roll_rate"
        const val PREF_PADS_ROLL = "pads_roll"
        const val PREF_PADS_SHOW_XY = "pads_show_xy"

        /** La couleur de chaque motif (A à D) dans les blocs du morceau. */
        val PATTERN_COLORS = intArrayOf(0xFF7C8CFF.toInt(), 0xFF1ABC9C.toInt(), 0xFFF39C12.toInt(), 0xFFE84393.toInt())

        /** Les noms des sept sons de batterie, dans l'ordre de [Inst]. */
        /** Les allures proposées en touchant le tempo, de la plus lente à la plus rapide (noms : `studio_tempo_names`). */
        val TEMPO_PRESETS = intArrayOf(70, 90, 110, 128, 150, 175)

        /** Les noms des trois variantes du son de chaque instrument de batterie, dans l'ordre de [Inst]. */
        val KIT_NAMES = intArrayOf(
            R.array.studio_kits_kick, R.array.studio_kits_snare, R.array.studio_kits_clap, R.array.studio_kits_hat,
            R.array.studio_kits_open_hat, R.array.studio_kits_tom, R.array.studio_kits_perc,
        )

        val DRUM_NAMES = intArrayOf(
            R.string.studio_lane_kick, R.string.studio_lane_snare, R.string.studio_lane_clap,
            R.string.studio_lane_hat, R.string.studio_lane_open_hat, R.string.studio_lane_tom,
            R.string.studio_lane_perc,
        )

        /** Les couleurs des pistes, partagées avec la bibliothèque. */
        val EXTRA_COLORS = StudioColors.EXTRA
        val LANE_COLORS = StudioColors.LANE
    }
}
