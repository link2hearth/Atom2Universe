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
import com.Atom2Universe.app.studio.data.MidiNotes
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
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.PopupWindow
import android.util.TypedValue
import android.widget.FrameLayout
import com.Atom2Universe.app.midi.input.MidiConnectionStatus
import com.Atom2Universe.app.midi.input.MidiInputController
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.TrackKind
import com.Atom2Universe.app.studio.data.Track
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StudioAudioOutput
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.TrackMix
import com.Atom2Universe.app.studio.music.Scales
import com.Atom2Universe.app.studio.music.Styles
import com.Atom2Universe.app.studio.ui.FlowRow
import com.Atom2Universe.app.studio.ui.InstrumentIcons
import com.Atom2Universe.app.studio.ui.PianoRollView
import com.Atom2Universe.app.studio.ui.StepGridView
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
    private var selectedTrack = -1

    /** Les pistes visibles, dans l'ordre de la grille ; [trackSignature] dit à quoi elles ressemblaient au dernier dessin. */
    private var gridIds: List<Int> = emptyList()
    private var trackSignature = ""
    private lateinit var mixerStrips: LinearLayout
    private lateinit var mixerBody: LinearLayout
    private lateinit var mixerStripView: View
    private lateinit var mixerPadBox: View
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
    private lateinit var mixerPane: LinearLayout
    private lateinit var padView: XyPadView
    private lateinit var patternGroup: MaterialButtonToggleGroup
    private lateinit var songButton: MaterialButton
    private var songDefaultColor = 0
    private val patternIds = List(StudioProject.SLOTS) { View.generateViewId() }
    private lateinit var gestureRecordButton: MaterialButton
    private lateinit var gestureClearButton: MaterialButton
    private var gestureArmed = false
    private var gestureTaking = false
    private val levelFaders = ArrayList<FaderView>()
    private val muteButtons = ArrayList<MaterialButton>()
    private val soloButtons = ArrayList<MaterialButton>()
    private val tabMixerId = View.generateViewId()

    // Rythme
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
    private var midi: MidiInputController? = null
    private val waveIds = IntArray(4) { View.generateViewId() }

    private val tabRhythmId = View.generateViewId()
    private val tabNotesId = View.generateViewId()

    private val handler = Handler(Looper.getMainLooper())
    private var playing = false
    private var bufferBlocks = 2
    private var frames = 0
    private var building = true
    private var scaleLocked = true
    private var rollBase = 24
    private var onNotesTab = false

    private val saveRunnable = Runnable { library.save(projectId, session.project) }

    private val frameTick = object : Runnable {
        override fun run() {
            val step = if (playing) engine.currentStep else -1
            if (onNotesTab) roll.playhead = step else grid.playhead = step
            if (::padView.isInitialized) {
                if (padView.isTouching) {
                    if (gestureTaking) session.recordGesture(engine.playPosition, engine.masterFilter, engine.spaceBoost)
                } else if (playing && mixerPane.visibility == View.VISIBLE) {
                    // Sans doigt, le repère suit le geste enregistré
                    val pat = session.project.pattern
                    val f = pat.filterAt(engine.playPosition)
                    val s = pat.spaceAt(engine.playPosition)
                    padView.showAt(1f - (f ?: 0f), s ?: 0f)
                }
            }
            if (stats.visibility == View.VISIBLE && ++frames % 15 == 0) refreshStats()
            grid.postOnAnimation(this)
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
        val loaded = entry.project
        // Un projet qui a des pistes soundfont ouvre le soundfont avant que le moteur fabrique leurs instruments
        if (loaded.tracks.any { it.kind == TrackKind.SF2 || it.sfKit >= 0 }) {
            sf2 = Sf2Bridge.open(this)
            engine.sf2Factory = sf2?.factory
        }
        session = StudioSession(engine, loaded)
        output = StudioAudioOutput(engine)
        recorder = LiveRecorder(session, { engine.playing }, { engine.playPosition }, { selectedTrack })
        ensureSelected()
        rollBase = autoBase()

        setContentView(buildContent())
        building = false
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        if (!output.start(this)) {
            stats.visibility = View.VISIBLE
            statsText.setText(R.string.studio_audio_failed)
        }
        output.setBufferBlocks(bufferBlocks)
        grid.postOnAnimation(frameTick)
        startMidi()
    }

    override fun onPause() {
        grid.removeCallbacks(frameTick)
        handler.removeCallbacks(saveRunnable)
        stopLive()
        library.save(projectId, session.project)
        engine.stop()
        playing = false
        refreshTransport()
        output.stop()
        super.onPause()
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
                refreshStats()
                true
            }
        }
        titleRow.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tabsGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            addView(outlined(R.string.studio_tab_rhythm).apply { id = tabRhythmId })
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
        }
        tempo.addView(tempoText)
        tempo.addView(square(R.string.studio_tempo_more).also { holdToRepeat(it) { session.setBpm(session.project.bpm + 1); refreshTempo(); scheduleSave() } })
        transport.addView(tempo, wrap(end = dp(8)))

        // Motifs A à D, morceau
        val names = resources.getStringArray(R.array.studio_pattern_names)
        patternGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            for (k in 0 until StudioProject.SLOTS) {
                val b = outlined(null).apply { id = patternIds[k]; text = names[k]; minWidth = dp(44); minimumWidth = dp(44); setPadding(dp(8), 0, dp(8), 0) }
                addView(b)
            }
            addOnButtonCheckedListener { _, id, checked ->
                if (checked && !building) {
                    val slot = patternIds.indexOf(id)
                    if (slot >= 0) {
                        session.selectPattern(slot)
                        refreshAll()
                        grid.invalidate()
                    }
                }
            }
        }
        transport.addView(patternGroup, wrap(end = dp(8)))
        songButton = outlined(R.string.studio_song).apply { setOnClickListener { showSongPanel(it) } }
        transport.addView(songButton, wrap(end = dp(8)))
        songDefaultColor = songButton.textColors.defaultColor
        transport.addView(outlined(R.string.studio_edit_menu).also { it.setOnClickListener { v -> showEditMenu(v) } }, wrap(end = dp(8)))
        transport.addView(square(R.string.studio_more).also {
            it.contentDescription = getString(R.string.studio_settings)
            it.setOnClickListener { v -> showSettingsPanel(v) }
        })
        root.addView(transport)

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
        notesPane = buildNotesPane().also { it.visibility = View.GONE }
        mixerPane = buildMixerPane().also { it.visibility = View.GONE }
        root.addView(rhythmPane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(notesPane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(mixerPane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun buildRhythmPane(): LinearLayout {
        val pane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        grid = StepGridView(this).apply {
            stepCount = session.project.stepCount
            addLabel = getString(R.string.studio_tracks_add)
            isOn = { lane, step -> gridIds.getOrNull(lane)?.let { session.isOn(it, step) } ?: false }
            velocity = { lane, step -> gridIds.getOrNull(lane)?.let { session.velocity(it, step) } ?: 0f }
            isMuted = { lane -> gridIds.getOrNull(lane)?.let { session.isMuted(it) } ?: false }
            listener = object : StepGridView.Listener {
                override fun onStepSet(lane: Int, step: Int, on: Boolean) {
                    gridIds.getOrNull(lane)?.let { session.setStep(it, step, on) }
                    scheduleSave()
                }

                override fun onVelocityCycle(lane: Int, step: Int) {
                    gridIds.getOrNull(lane)?.let { session.cycleVelocity(it, step) }
                    scheduleSave()
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

    /** Le mixeur : une tranche par piste (nom, volume, panoramique, muet, solo, effets) et un pad XY pour jouer le filtre et la réverbération. */
    private fun buildMixerPane(): LinearLayout {
        val pane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val strips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        mixerStrips = strips
        val strip = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            addView(strips, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // Pad XY : le doigt ferme le filtre (vers la gauche) et ajoute de la réverbération (vers le haut)
        val pad = XyPadView(this).apply {
            accent = 0xFF7C8CFF.toInt()
            onPress = {
                engine.padActive = true
                // Prise : le geste s'écrit dans la boucle tant que le doigt reste (seulement pendant la lecture)
                if (gestureArmed && playing) {
                    session.beginGesture()
                    gestureTaking = true
                    recording = true
                }
            }
            onMove = { x, y ->
                engine.masterFilter = 1f - x
                engine.spaceBoost = y
            }
            onRelease = {
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
            }
        }
        padView = pad
        val xyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        xyBox.addView(TextView(this).apply {
            setText(R.string.studio_xy_title)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        })
        xyBox.addView(pad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val gestureRow = FlowRow(this).apply { hGap = dp(8); vGap = dp(4) }
        gestureRecordButton = outlined(R.string.studio_gesture_record).apply {
            isCheckable = true
            setOnClickListener {
                gestureArmed = isChecked
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
        gestureRow.addView(gestureRecordButton)
        gestureRow.addView(gestureClearButton)
        xyBox.addView(gestureRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

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
        val wide = resources.configuration.screenWidthDp >= 1100
        mixerBody.orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        // Les tranches gardent leur taille ; le pad de jeu, lui, prend toute la place qui reste
        mixerStripView.layoutParams =
            if (wide) LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        mixerPadBox.layoutParams =
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

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::mixerBody.isInitialized) applyMixerLayout()
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
            strip.addView(level, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)))

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

            mixerStrips.addView(strip, LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
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
                        // Une note d'essai : on entend l'effet tout de suite
                        previewTrack(lane)
                    }
                })
            }
            row.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        add(R.string.studio_fx_pan, { it.pan }, { (it + 1f) / 2f }, { it * 2f - 1f }, { m, v -> m.copy(pan = if (kotlin.math.abs(v) < 0.04f) 0f else v) })
        add(R.string.studio_fx_reverb, { it.reverbSend }, { it }, { it }, { m, v -> m.copy(reverbSend = v) })
        add(R.string.studio_fx_echo, { it.delaySend }, { it }, { it }, { m, v -> m.copy(delaySend = v) })
        add(R.string.studio_fx_filter, { it.filter }, { (it + 1f) / 2f }, { it * 2f - 1f }, { m, v -> m.copy(filter = if (kotlin.math.abs(v) < 0.04f) 0f else v) })
        add(R.string.studio_fx_drive, { it.drive }, { it }, { it }, { m, v -> m.copy(drive = v) })
        add(R.string.studio_fx_crush, { it.crush }, { it }, { it }, { m, v -> m.copy(crush = v) })
        box.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        }, 0)
        panel(anchor, box, 400).setOnDismissListener { refreshNotesControls() }
    }

    private fun refreshGestureButtons() {
        if (!::gestureClearButton.isInitialized) return
        gestureClearButton.isEnabled = session.project.pattern.hasAutomation
        gestureClearButton.alpha = if (gestureClearButton.isEnabled) 1f else 0.4f
    }

    private fun refreshMixer() {
        refreshGestureButtons()
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
        keyButton = outlined(null).also { it.setOnClickListener { v -> pickKey(v) } }
        header.addView(keyButton, wrap(end = dp(8)))
        scaleButton = outlined(null).also { it.setOnClickListener { v -> pickScale(v) } }
        header.addView(scaleButton, wrap(end = dp(8)))
        lockSwitch = SwitchCompat(this).apply {
            setText(R.string.studio_lock)
            textSize = 14f
            isChecked = scaleLocked
            setOnCheckedChangeListener { _, checked ->
                scaleLocked = checked
                refreshRoll()
            }
        }
        header.addView(lockSwitch, wrap(end = dp(8)))
        val octaves = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        octaves.addView(caption(R.string.studio_octave), wrap(end = dp(4)))
        octaves.addView(square(R.string.studio_tempo_less).also {
            it.setOnClickListener { rollBase = (rollBase - 12).coerceAtLeast(12); refreshRoll() }
        })
        octaves.addView(square(R.string.studio_tempo_more).also {
            it.setOnClickListener { rollBase = (rollBase + 12).coerceAtMost(84); refreshRoll() }
        })
        header.addView(octaves, wrap(end = dp(8)))
        soundButton = outlined(null).also { it.setOnClickListener { v -> showSoundPanel(v) } }
        header.addView(soundButton, wrap(end = dp(8)))
        arpButton = outlined(null).also { it.setOnClickListener { v -> showArpPanel(v) } }
        header.addView(arpButton, wrap(end = dp(8)))
        recordButton = outlined(null).also { it.setOnClickListener { toggleRecord() } }
        header.addView(recordButton, wrap(end = dp(8)))
        midiText = caption(R.string.studio_midi_connected).also { it.visibility = View.GONE }
        header.addView(midiText, wrap())
        outer.addView(header)
        trackDependent.addAll(listOf(keyButton, scaleButton, lockSwitch, octaves, soundButton, arpButton, recordButton))

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
            notes = { session.project.pattern.notes.filter { it.track == selectedTrack } }
            listener = object : PianoRollView.Listener {
                override fun onAddNote(step: Int, pitch: Int, length: Int) {
                    session.addNote(step, pitch, length, track = selectedTrack)
                    scheduleSave()
                    invalidate()
                }

                override fun onRemoveNote(note: Note) {
                    session.removeNote(note)
                    scheduleSave()
                    invalidate()
                }

                override fun onResizeNote(note: Note, length: Int) {
                    session.resizeNote(note, length)
                    scheduleSave()
                    invalidate()
                }

                override fun onPreview(pitch: Int) = previewPitch(pitch)
            }
        }
        // Le piano roll prend sa hauteur naturelle, le clavier se pose juste dessous
        roll.reserveBelow = dp(150 + 8)
        pane.addView(roll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        keyboard = KeyboardView(this).apply {
            accent = LANE_COLORS[Inst.SYNTH]
            noteNames = resources.getStringArray(R.array.studio_key_names).toList()
            listener = object : KeyboardView.Listener {
                override fun onKeyDown(pitch: Int) {
                    roll.setLit(pitch, true)
                    liveNoteOn(pitch, 0.9f, engine.playPosition)
                }

                override fun onKeyUp(pitch: Int) {
                    roll.setLit(pitch, false)
                    liveNoteOff(pitch, engine.playPosition)
                }
            }
        }
        // Le clavier occupe tout le bas : il prend la place restante (au plus 300 dp), collé en bas, sans bande vide dessous
        val keyboardZone = FrameLayout(this).apply { minimumHeight = dp(150 + 8) }
        keyboardZone.addView(keyboard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { topMargin = dp(8) })
        pane.addView(keyboardZone, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        outer.addView(pane, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        drumPanel = buildDrumPanel()
        outer.addView(drumPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return outer
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
        drumSliders.addView(tune.second, LinearLayout.LayoutParams(dp(520), ViewGroup.LayoutParams.WRAP_CONTENT))

        drumLengthText = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.END
        }
        val length = sliderRow(150, drumLengthText) { editDrum(length = it + 50) }
        drumLengthBar = length.first
        drumSliders.addView(section(R.string.studio_drum_length))
        drumSliders.addView(length.second, LinearLayout.LayoutParams(dp(520), ViewGroup.LayoutParams.WRAP_CONTENT))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(outlined(R.string.studio_drum_listen).also { it.setOnClickListener { listenDrum() } }, wrap(end = dp(8)))
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
    private fun pickDrumKit(trackId: Int) {
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
                    listenDrum()
                }
                .show()
        }
    }

    private fun listenDrum() {
        if (editedDrum >= 0) engine.trigger(editedDrum, 60, 1f)
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
    private fun soundSlider(box: ViewGroup, p: SoundParam): SeekBar {
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
                override fun onStopTrackingTouch(seekBar: SeekBar) = previewKeyNote()
            })
        }
        p.bar = bar
        box.addView(labeled(p.labelRes, bar, 300), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        return bar
    }

    /** Le panneau « Son » de la piste choisie : instrument, trois réglages simples, et le détail avancé du synthé. */
    private fun showSoundPanel(anchor: View) {
        val t = curTrack() ?: return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        box.addView(TextView(this).apply {
            setText(R.string.studio_sound_caption)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })
        val params = ArrayList<SoundParam>()
        val synthKind = t.kind == TrackKind.SYNTH
        val pick = outlined(null)
        fun refreshPick() {
            val now = curTrack() ?: return
            pick.text = if (now.kind == TrackKind.SYNTH) getString(presetName(now.synth.id)) else sf2ProgramName(now)
            for (p in params) p.bar.progress = (p.toSlider(p.read()).coerceIn(0f, 1f) * 1000).roundToInt()
            refreshNotesControls()
        }
        pick.setOnClickListener {
            if (synthKind) {
                dropdown(pick, SynthPresets.ALL.map { getString(presetName(it.id)) }) { which ->
                    session.setPreset(SynthPresets.ALL[which].id, selectedTrack)
                    scheduleSave()
                    refreshPick()
                    previewKeyNote()
                }
            } else {
                withSf2 { bridge ->
                    pickProgram(bridge) { bank, program ->
                        session.setSf2Program(selectedTrack, bank, program)
                        scheduleSave()
                        refreshPick()
                        previewKeyNote()
                    }
                }
            }
        }
        box.addView(pick)

        fun add(into: ViewGroup, p: SoundParam) {
            params += p
            soundSlider(into, p)
        }
        if (synthKind) {
            add(box, SoundParam(R.string.studio_macro_bright, { curSynth().cutoffHz },
                { hz -> ln(hz.coerceIn(100f, 10000f) / 100f) / ln(100f) }, { t2 -> 100f * exp(t2 * ln(100f)) },
                { v -> session.updateSynth(selectedTrack) { it.copy(cutoffHz = v) } }))
            add(box, SoundParam(R.string.studio_macro_drive, { curSynth().drive }, { it }, { it },
                { v -> session.updateSynth(selectedTrack) { it.copy(drive = v) } }))
        }
        add(box, SoundParam(R.string.studio_macro_ambience, { curTrack()?.mix?.reverbSend ?: 0f }, { it }, { it },
            { v -> session.setSynthSpace(v, selectedTrack) }))

        if (synthKind) {
            val advanced = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            val wave = MaterialButtonToggleGroup(this).apply {
                isSingleSelection = true
                isSelectionRequired = true
                val labels = listOf(R.string.studio_wave_saw, R.string.studio_wave_square, R.string.studio_wave_triangle, R.string.studio_wave_sine)
                for (i in 0 until 4) addView(outlined(labels[i]).apply { id = waveIds[i]; minWidth = 0; setPadding(dp(8), 0, dp(8), 0) })
                check(waveIds[curSynth().wave.coerceIn(0, 3)])
                addOnButtonCheckedListener { _, id, checked ->
                    if (checked) {
                        session.updateSynth(selectedTrack) { it.copy(wave = waveIds.indexOf(id)) }
                        scheduleSave()
                        previewKeyNote()
                    }
                }
            }
            advanced.addView(wave, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            val list = listOf(
                SoundParam(R.string.studio_adv_resonance, { curSynth().resonance }, { (it - 0.5f) / 9.5f }, { 0.5f + it * 9.5f },
                    { v -> session.updateSynth(selectedTrack) { it.copy(resonance = v) } }),
                SoundParam(R.string.studio_adv_detune, { curSynth().detuneCents }, { (it / 1200f).coerceIn(0f, 1f).pow(1f / 3f) }, { it * it * it * 1200f },
                    { v -> session.updateSynth(selectedTrack) { it.copy(detuneCents = v) } }),
                SoundParam(R.string.studio_adv_sub, { curSynth().subLevel }, { it }, { it },
                    { v -> session.updateSynth(selectedTrack) { it.copy(subLevel = v) } }),
                SoundParam(R.string.studio_adv_sweep, { curSynth().envOctaves }, { it / 5f }, { it * 5f },
                    { v -> session.updateSynth(selectedTrack) { it.copy(envOctaves = v) } }),
                SoundParam(R.string.studio_adv_attack, { curSynth().attack }, { ln(it.coerceIn(0.002f, 3f) / 0.002f) / ln(1500f) }, { 0.002f * exp(it * ln(1500f)) },
                    { v -> session.updateSynth(selectedTrack) { it.copy(attack = v) } }),
                SoundParam(R.string.studio_adv_release, { curSynth().release }, { ln(it.coerceIn(0.005f, 4f) / 0.005f) / ln(800f) }, { 0.005f * exp(it * ln(800f)) },
                    { v -> session.updateSynth(selectedTrack) { it.copy(release = v) } }),
                SoundParam(R.string.studio_adv_move_speed, { curSynth().lfoRate }, { (it / 12f).coerceIn(0f, 1f).pow(0.5f) }, { it * it * 12f },
                    { v -> session.updateSynth(selectedTrack) { it.copy(lfoRate = v) } }),
                SoundParam(R.string.studio_adv_move_depth, { curSynth().lfoFilter }, { it / 3f }, { it * 3f },
                    { v -> session.updateSynth(selectedTrack) { it.copy(lfoFilter = v) } }),
            )
            for (p in list) add(advanced, p)
            box.addView(outlined(R.string.studio_advanced).apply {
                setOnClickListener { advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            box.addView(advanced)
        }
        refreshPick()
        panel(anchor, box, 340)
    }

    /** Le panneau « Arpège » de la piste choisie : mode, vitesse et nombre d'octaves. */
    private fun showArpPanel(anchor: View) {
        val t = curTrack() ?: return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        box.addView(TextView(this).apply {
            setText(R.string.studio_arp_caption)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })
        val modes = listOf(Arp.OFF, Arp.UP, Arp.DOWN, Arp.UP_DOWN, Arp.RANDOM)
        val modeButton = outlined(null).apply { text = getString(arpName(t.arpMode)) }
        modeButton.setOnClickListener {
            dropdown(modeButton, modes.map { getString(arpName(it)) }) { which ->
                session.setArpMode(modes[which], selectedTrack)
                modeButton.text = getString(arpName(modes[which]))
                refreshNotesControls()
                scheduleSave()
            }
        }
        box.addView(modeButton)
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
                    session.setArpRate(rateIds.indexOf(id), selectedTrack)
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
                    session.setArpOctaves(octIds.indexOf(id) + 1, selectedTrack)
                    scheduleSave()
                }
            }
        }
        box.addView(oct)
        panel(anchor, box, 300)
    }

    /** Le panneau « Réglages » : swing, espace, écho, longueur, copie du motif, sortie des fichiers. */
    private fun showSettingsPanel(anchor: View) {
        val p = session.project
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        box.addView(TextView(this).apply {
            setText(R.string.studio_settings)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        })
        fun addSlider(labelRes: Int, max: Int, value: Int, onChange: (Int) -> Unit) {
            val bar = slider(max = max) { v -> onChange(v); scheduleSave() }.apply { progress = value }
            box.addView(labeled(labelRes, bar, 300), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        addSlider(R.string.studio_swing, 50, (p.swing * 100).roundToInt()) { session.setSwing(it / 100f) }
        addSlider(R.string.studio_space, 100, (p.space * 100).roundToInt()) { session.setSpace(it / 100f) }
        addSlider(R.string.studio_echo, 100, (p.echo * 100).roundToInt()) { session.setEcho(it / 100f) }

        val ids = intArrayOf(View.generateViewId(), View.generateViewId())
        val steps = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            addView(outlined(null).apply { id = ids[0]; text = "16"; minWidth = dp(64) })
            addView(outlined(null).apply { id = ids[1]; text = "32"; minWidth = dp(64) })
            check(if (p.stepCount == 32) ids[1] else ids[0])
            addOnButtonCheckedListener { _, id, checked ->
                if (checked) {
                    val n = if (id == ids[1]) 32 else 16
                    if (n != session.project.stepCount) {
                        session.setStepCount(n)
                        grid.stepCount = n
                        roll.stepCount = n
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
        panel(anchor, box, 340)
    }

    // ---- Actions ---------------------------------------------------------------------------------

    private fun togglePlay() {
        playing = !playing
        if (playing) engine.play() else engine.stop()
        refreshTransport()
    }

    private fun showTab(id: Int) {
        val notes = id == tabNotesId
        onNotesTab = notes
        rhythmPane.visibility = if (id == tabRhythmId) View.VISIBLE else View.GONE
        notesPane.visibility = if (notes) View.VISIBLE else View.GONE
        mixerPane.visibility = if (id == tabMixerId) View.VISIBLE else View.GONE
        if (id == tabMixerId) refreshMixer()
        if (notes) {
            ensureSelected()
            rollBase = autoBase()
            refreshRoll()
            refreshNotesControls()
        }
    }

    private fun previewLane(trackId: Int, step: Int) {
        val t = session.project.trackOrNull(trackId) ?: return
        if (t.muted) return
        val v = session.velocity(trackId, step).takeIf { it > 0f } ?: 1f
        if (t.melodic) {
            val pitch = session.project.pattern.notes.firstOrNull { it.track == trackId && it.step == step }?.pitch
                ?: (24 + session.project.keyPc)
            previewPitch(pitch, v, track = trackId)
        } else {
            engine.trigger(trackId, 60, v)
        }
    }

    private fun previewPitch(pitch: Int, velocity: Float = 0.9f, holdMs: Long = 250, track: Int = selectedTrack) {
        if (track < 0 || session.isMuted(track)) return
        engine.trigger(track, pitch, velocity)
        handler.postDelayed({ engine.release(track, pitch) }, holdMs)
    }

    /** Une note d'essai après un réglage du son : la tonique, dans l'octave du piano roll. */
    private fun previewKeyNote() = previewPitch(rollBase + 12 + session.project.keyPc, 0.9f, 450)

    /** Une note d'essai sur la piste [id] (une frappe pour la batterie). */
    private fun previewTrack(id: Int) {
        val t = session.project.trackOrNull(id) ?: return
        if (t.melodic) previewPitch(rollBase + 12 + session.project.keyPc, 0.9f, 450, track = id) else engine.trigger(id, 60, 1f)
    }

    private fun pickKey(anchor: View) {
        dropdown(anchor, resources.getStringArray(R.array.studio_key_names).toList()) { which ->
            session.setKey(which)
            rollBase = autoBase()
            changed()
        }
    }

    private fun pickScale(anchor: View) {
        dropdown(anchor, Scales.ALL.map { getString(scaleName(it.id)) }) { which ->
            session.setScale(Scales.ALL[which].id)
            changed()
        }
    }

    /** Le menu « ⋯ » : les actions qui changent tout le motif en cours. */
    private fun showEditMenu(anchor: View) {
        val labels = listOf(getString(R.string.studio_random), getString(R.string.studio_clear), getString(R.string.studio_undo))
        dropdown(anchor, labels, enabled = { it != 2 || session.canUndo }) { which ->
            when (which) {
                0 -> session.randomize(System.nanoTime())
                1 -> session.clear()
                else -> session.undo()
            }
            changed()
        }
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

    private fun toggleRecord() {
        if (recorder.armed) recorder.finishAll()
        recorder.armed = !recorder.armed
        refreshNotesControls()
        roll.invalidate()
        scheduleSave()
    }

    /** Une touche (écran ou clavier MIDI) : le son part tout de suite ; l'enregistreur note l'instant. */
    private fun liveNoteOn(pitch: Int, velocity: Float, position: Double) {
        if (selectedTrack < 0 || session.isMuted(selectedTrack)) return
        engine.trigger(selectedTrack, pitch, velocity)
        recorder.press(pitch, position)
    }

    private fun liveNoteOff(pitch: Int, position: Double) {
        if (selectedTrack >= 0) engine.release(selectedTrack, pitch)
        recorder.release(pitch, position)
        roll.invalidate()
    }

    private fun startMidi() {
        val controller = midi ?: MidiInputController(
            this,
            { bytes ->
                // Fil MIDI : le son et l'instant partent tout de suite, le reste (projet, écran) passe par le fil principal.
                val position = engine.playPosition
                val track = selectedTrack
                MidiNotes.handle(
                    bytes,
                    { note, velocity ->
                        if (track >= 0 && !session.isMuted(track)) engine.trigger(track, note, velocity)
                        handler.post { recorder.press(note, position) }
                    },
                    { note ->
                        if (track >= 0) engine.release(track, note)
                        handler.post { recorder.release(note, position); roll.invalidate() }
                    },
                )
            },
            { status ->
                handler.post { midiText.visibility = if (status == MidiConnectionStatus.DEVICE_DETECTED) View.VISIBLE else View.GONE }
            },
        ).also { midi = it }
        controller.start()
    }

    private fun stopLive() {
        midi?.stop()
        recorder.finishAll()
        recorder.armed = false
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
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, 600)
    }

    /** La fenêtre du piano roll (deux octaves) part de l'octave de la note la plus grave et monte si besoin. */
    private fun autoBase(): Int {
        val notes = session.project.pattern.notes.filter { it.track == selectedTrack }
        if (notes.isEmpty()) return 24
        var base = notes.minOf { it.pitch } / 12 * 12
        val top = notes.maxOf { it.pitch }
        if (top > base + 24) base = (top - 24 + 11) / 12 * 12
        return base.coerceIn(12, 84)
    }

    // ---- Affichage -------------------------------------------------------------------------------

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
        box.addView(TextView(this).apply {
            setText(R.string.studio_sample_title)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        }, 0)
        panel(anchor, box, 380)
    }

    // ---- Export ---------------------------------------------------------------------------------------------------

    /** Rend le fichier hors ligne (plus vite que le temps réel) avec une barre de progression, puis dit où il est. */
    private fun runExport(kind: Int) {
        // Pendant le rendu, la lecture se tait : le téléphone a besoin de toute sa puissance
        if (playing) {
            playing = false
            engine.stop()
            refreshTransport()
        }
        val project = session.project
        val bar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
        val holder = FrameLayout(this).apply { setPadding(dp(24), dp(16), dp(24), 0); addView(bar) }
        var cancelled = false
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.studio_export_working)
            .setView(holder)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled = true }
            .show()
        Thread {
            val saved = when (kind) {
                EXPORT_MIDI -> StudioFiles.saveMidi(this, project)
                else -> StudioFiles.saveWav(this, project, sf2?.factory) { f ->
                    runOnUiThread { bar.progress = (f * 1000).toInt() }
                    !cancelled
                }
            }
            runOnUiThread {
                dialog.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                when {
                    cancelled -> Unit
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

    private fun refreshPatterns() {
        if (!::patternGroup.isInitialized) return
        val p = session.project
        val wanted = patternIds[p.current]
        if (patternGroup.checkedButtonId != wanted) {
            val was = building
            building = true
            patternGroup.check(wanted)
            building = was
        }
        // Un motif vide est estompé : on voit tout de suite lesquels servent
        for (k in 0 until StudioProject.SLOTS) {
            patternGroup.findViewById<View>(patternIds[k])?.alpha = if (k == p.current || !session.isPatternEmpty(k)) 1f else 0.45f
        }
        songButton.isSelected = p.songMode
        songButton.alpha = if (p.songMode) 1f else 0.8f
        songButton.setTextColor(if (p.songMode) 0xFFFFC857.toInt() else songDefaultColor)
    }

    /** Le morceau : la suite des motifs ; on l'écrit avec des boutons « + A » … et on le lance avec un interrupteur. */
    private fun showSongPanel(anchor: View) {
        val names = resources.getStringArray(R.array.studio_pattern_names)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val chainText = TextView(this).apply { textSize = 22f; setPadding(0, dp(8), 0, dp(12)) }
        val playSwitch = androidx.appcompat.widget.SwitchCompat(this).apply { setText(R.string.studio_song_play) }
        fun refresh() {
            val chain = session.project.chain
            chainText.text = if (chain.isEmpty()) getString(R.string.studio_song_empty)
            else chain.joinToString(getString(R.string.studio_chain_join)) { names[it] }
            playSwitch.isEnabled = chain.isNotEmpty()
            val was = playSwitch.isChecked
            if (was != session.project.songMode) playSwitch.isChecked = session.project.songMode
            refreshPatterns()
            scheduleSave()
        }
        box.addView(chainText)
        val adds = FlowRow(this)
        for (k in 0 until StudioProject.SLOTS) {
            adds.addView(outlined(null).apply {
                text = getString(R.string.studio_song_add, names[k])
                setOnClickListener { session.chainAdd(k); refresh() }
            })
        }
        adds.addView(outlined(R.string.studio_song_remove).apply { setOnClickListener { session.chainRemoveLast(); refresh() } })
        adds.addView(outlined(R.string.studio_song_clear).apply { setOnClickListener { session.chainClear(); refresh() } })
        box.addView(adds)
        playSwitch.setOnCheckedChangeListener { _, on ->
            if (on != session.project.songMode) {
                session.setSongMode(on)
                refreshPatterns()
                scheduleSave()
            }
        }
        box.addView(playSwitch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        refresh()
        box.addView(TextView(this).apply {
            setText(R.string.studio_song_title)
            textSize = 14f
            alpha = 0.75f
        }, 0)
        panel(anchor, box, 420)
    }

    private fun refreshTransport() {
        playButton.setText(if (playing) R.string.studio_stop else R.string.studio_play)
    }

    private fun refreshAll() {
        val p = session.project
        refreshTracks()
        refreshTransport()
        refreshTempo()
        refreshPatterns()
        bufferButton.text = getString(R.string.studio_buffer_label, bufferBlocks)
        refreshRoll()
        refreshNotesControls()
        refreshMixer()
    }

    private fun refreshRoll() {
        val p = session.project
        val scale = Scales.byId(p.scaleId)
        roll.keyPc = p.keyPc
        roll.scale = scale
        roll.rows = Scales.rows(scale, p.keyPc, rollBase, 2, scaleLocked)
        roll.invalidate()
        keyboard.keyPc = p.keyPc
        keyboard.scale = scale
        keyboard.low = rollBase
        keyboard.locked = scaleLocked
    }

    private fun refreshNotesControls() {
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
        val soundName = if (t.kind == TrackKind.SYNTH) getString(presetName(t.synth.id)) else sf2ProgramName(t)
        soundButton.text = getString(R.string.studio_sound_label, soundName)
        arpButton.text = getString(R.string.studio_arp_label, getString(arpName(t.arpMode)))
        roll.accent = trackColor(t)
        keyboard.accent = trackColor(t)
        keyboard.invalidate()
    }

    // ---- Les pistes -------------------------------------------------------------------------------------------------

    private fun curTrack(): Track? = session.project.trackOrNull(selectedTrack)

    private fun curSynth() = curTrack()?.synth ?: SynthPresets.BASS

    private fun visibleTracks(): List<Track> = session.project.tracks.filter { !it.hidden }

    private fun defaultMix(t: Track): TrackMix = when (t.kind) {
        TrackKind.DRUM -> TrackMix.defaults()[t.voice.coerceIn(0, Inst.DRUM_COUNT - 1)]
        TrackKind.SYNTH -> TrackMix.defaults()[Inst.SYNTH]
        else -> Track.SF2_MIX
    }

    private fun trackColor(t: Track): Int = StudioColors.of(t)

    private fun sf2ProgramName(t: Track): String =
        sf2?.programs?.firstOrNull { it.bank == t.bank && it.program == t.program }?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.studio_sf2_default_name)

    private fun baseName(t: Track): String = when (t.kind) {
        TrackKind.DRUM -> getString(DRUM_NAMES[t.voice.coerceIn(0, Inst.DRUM_COUNT - 1)])
        TrackKind.SYNTH -> getString(R.string.studio_lane_synth)
        else -> sf2ProgramName(t)
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
        val sig = session.project.tracks.joinToString(",") { "${it.id}:${it.kind}:${it.voice}:${it.hidden}:${it.bank}:${it.program}" }
        if (sig == trackSignature) return
        trackSignature = sig
        val visible = visibleTracks()
        gridIds = visible.map { it.id }
        grid.laneColors = visible.map { trackColor(it) }.toIntArray()
        grid.laneIcons = visible.map { InstrumentIcons.of(it) }.toIntArray()
        grid.laneBadges = visible.map { trackBadge(it) }
        grid.laneNames = visible.map { trackName(it) }
        rebuildMixer()
    }

    private fun triggerPerc() {
        session.project.tracks.firstOrNull { it.kind == TrackKind.DRUM && it.voice == Inst.PERC }?.let { engine.trigger(it.id, 60, 1f) }
    }

    /** La ligne de pastilles de l'onglet Notes : une par piste à notes (sa couleur, son nom), et « + » pour en ajouter. */
    private fun refreshTrackStrip() {
        trackStrip.removeAllViews()
        for (t in session.project.tracks.filter { !it.hidden }) {
            val c = chip(trackName(t)) {
                if (t.melodic) {
                    editedDrum = -1
                    if (selectedTrack != t.id) {
                        selectedTrack = t.id
                        rollBase = autoBase()
                    }
                } else {
                    editedDrum = t.id
                }
                refreshRoll()
                refreshNotesControls()
            }
            // Le contour porte la couleur de la piste ; la piste ouverte a un trait plus épais et un fond teinté
            val on = if (editedDrum >= 0) t.id == editedDrum else t.id == selectedTrack
            val color = trackColor(t)
            val raised = ContextCompat.getColor(this, R.color.audio_surface_raised)
            val shape = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(17).toFloat()
                setColor(if (on) androidx.core.graphics.ColorUtils.compositeColors((color and 0x00FFFFFF) or 0x40000000, raised) else raised)
                setStroke(dp(if (on) 3 else 2), color)
            }
            c.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.audio_ripple)), shape, null,
            )
            trackStrip.addView(c)
        }
        trackStrip.addView(chip(null, R.drawable.ic_px_add) { showAddTrackSheet() })
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
            rollBase = autoBase()
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

    /** Un petit panneau flottant sous le bouton : on règle, puis on touche à côté pour le refermer. */
    private fun panel(anchor: View, content: View, widthDp: Int): PopupWindow {
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val scroll = android.widget.ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val frame = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (dark) 0xFF2B2B33.toInt() else 0xFFFFFFFF.toInt())
                setStroke(dp(1), if (dark) 0xFF4A4A54.toInt() else 0xFFCFCFD6.toInt())
            }
            addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // La hauteur : celle du contenu, sans dépasser la place sous le bouton
        val where = IntArray(2)
        anchor.getLocationOnScreen(where)
        val room = resources.displayMetrics.heightPixels - (where[1] + anchor.height) - dp(16)
        content.measure(
            View.MeasureSpec.makeMeasureSpec(dp(widthDp), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val height = minOf(content.measuredHeight + dp(4), maxOf(room, dp(240)))
        val popup = PopupWindow(frame, dp(widthDp), height, true)
        popup.isOutsideTouchable = true
        popup.elevation = dp(12).toFloat()
        popup.showAsDropDown(anchor, 0, dp(4))
        return popup
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
    private fun labeled(textRes: Int, bar: SeekBar, widthDp: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            setText(textRes)
            textSize = 12f
            alpha = 0.75f
            setPadding(dp(4), 0, 0, 0)
        })
        addView(bar, LinearLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT))
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

        /** Les noms des sept sons de batterie, dans l'ordre de [Inst]. */
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
