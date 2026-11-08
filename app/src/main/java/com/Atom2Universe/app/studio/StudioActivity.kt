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
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audio.AudioThemedActivity
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectStore
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
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StudioAudioOutput
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.TrackMix
import com.Atom2Universe.app.studio.music.Scales
import com.Atom2Universe.app.studio.music.Styles
import com.Atom2Universe.app.studio.ui.FlowRow
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
    private lateinit var store: StudioProjectStore

    // Commun
    private lateinit var playButton: MaterialButton
    private lateinit var tempoText: TextView
    private lateinit var swingBar: SeekBar
    private lateinit var stepsGroup: MaterialButtonToggleGroup
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
    private val panFaders = ArrayList<FaderView>()
    private val muteButtons = ArrayList<MaterialButton>()
    private val soloButtons = ArrayList<MaterialButton>()
    private val tabMixerId = View.generateViewId()

    // Rythme
    private lateinit var grid: StepGridView
    private lateinit var spaceBar: SeekBar
    private lateinit var echoBar: SeekBar
    private lateinit var styleButton: MaterialButton

    // Notes
    private lateinit var roll: PianoRollView
    private lateinit var keyButton: MaterialButton
    private lateinit var scaleButton: MaterialButton
    private lateinit var lockSwitch: SwitchCompat
    private lateinit var soundButton: MaterialButton
    private lateinit var advancedPanel: FlowRow
    private lateinit var keyboard: KeyboardView
    private lateinit var recordButton: MaterialButton
    private lateinit var midiText: TextView
    private lateinit var arpButton: MaterialButton
    private lateinit var arpRateGroup: MaterialButtonToggleGroup
    private lateinit var arpOctaveGroup: MaterialButtonToggleGroup
    private val arpRateIds = IntArray(3) { View.generateViewId() }
    private val arpOctaveIds = IntArray(3) { View.generateViewId() }
    private lateinit var recorder: LiveRecorder
    private var midi: MidiInputController? = null
    private lateinit var waveGroup: MaterialButtonToggleGroup
    private val soundParams = ArrayList<SoundParam>()
    private val waveIds = IntArray(4) { View.generateViewId() }

    private val step16Id = View.generateViewId()
    private val step32Id = View.generateViewId()
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

    private val saveRunnable = Runnable { store.save(session.project) }

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
        store = StudioProjectStore(this)
        SampleTools.load(this)?.let { (data, rate) -> engine.setPercSample(data, rate) }
        session = StudioSession(engine, store.load() ?: StudioProject.fromStyle(Styles.HOUSE))
        output = StudioAudioOutput(engine)
        recorder = LiveRecorder(session, { engine.playing }, { engine.playPosition })
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
        store.save(session.project)
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
        titleRow.addView(TextView(this).apply {
            setText(R.string.studio_title)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener {
                stats.visibility = if (stats.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                output.resetMax()
                refreshStats()
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
                    .setMessage(R.string.studio_help_text)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        })
        root.addView(titleRow)

        // Transport : lecture, tempo, swing, longueur
        val transport = flowRow(topMargin = dp(10))
        playButton = MaterialButton(this).apply {
            isAllCaps = false
            letterSpacing = 0.02f
            setOnClickListener { togglePlay() }
        }
        transport.addView(playButton, wrap())
        val tempo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tempo.addView(square(R.string.studio_tempo_less).also { holdToRepeat(it) { session.setBpm(session.project.bpm - 1); refreshTempo(); scheduleSave() } })
        tempoText = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            minWidth = dp(88)
        }
        tempo.addView(tempoText)
        tempo.addView(square(R.string.studio_tempo_more).also { holdToRepeat(it) { session.setBpm(session.project.bpm + 1); refreshTempo(); scheduleSave() } })
        transport.addView(tempo, wrap())
        swingBar = slider(max = 50) { v -> session.setSwing(v / 100f); scheduleSave() }
        transport.addView(labeled(R.string.studio_swing, swingBar, 120), wrap())
        stepsGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            addView(outlined(null).apply { id = step16Id; text = "16" })
            addView(outlined(null).apply { id = step32Id; text = "32" })
            addOnButtonCheckedListener { _, id, checked ->
                if (checked && !building) {
                    val n = if (id == step32Id) 32 else 16
                    session.setStepCount(n)
                    grid.stepCount = n
                    roll.stepCount = n
                    scheduleSave()
                }
            }
        }
        transport.addView(stepsGroup, wrap())

        // Motifs A à D, copie, morceau
        val names = resources.getStringArray(R.array.studio_pattern_names)
        patternGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            for (k in 0 until StudioProject.SLOTS) {
                val b = outlined(null).apply { id = patternIds[k]; text = names[k] }
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
        transport.addView(patternGroup, wrap())
        transport.addView(outlined(R.string.studio_pattern_copy).apply { setOnClickListener { showCopyMenu(it) } }, wrap())
        songButton = outlined(R.string.studio_song).apply { setOnClickListener { showSongPanel(it) } }
        transport.addView(songButton, wrap())
        songDefaultColor = songButton.textColors.defaultColor
        transport.addView(outlined(R.string.studio_output).apply { setOnClickListener { showOutputMenu(it) } }, wrap())
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

        val row = flowRow(topMargin = dp(2))
        styleButton = outlined(null).also { it.setOnClickListener { v -> pickStyle(v) } }
        row.addView(styleButton, wrap())
        row.addView(square(R.string.studio_more).also { it.setOnClickListener { v -> showEditMenu(v) } })
        spaceBar = slider(max = 100) { v -> session.setSpace(v / 100f); scheduleSave() }
        row.addView(labeled(R.string.studio_space, spaceBar, 120), wrap())
        echoBar = slider(max = 100) { v -> session.setEcho(v / 100f); scheduleSave() }
        row.addView(labeled(R.string.studio_echo, echoBar, 120), wrap())
        pane.addView(row)

        grid = StepGridView(this).apply {
            laneNames = listOf(
                R.string.studio_lane_kick, R.string.studio_lane_snare, R.string.studio_lane_clap,
                R.string.studio_lane_hat, R.string.studio_lane_open_hat, R.string.studio_lane_tom,
                R.string.studio_lane_perc, R.string.studio_lane_synth,
            ).map { getString(it) }
            laneColors = LANE_COLORS
            stepCount = session.project.stepCount
            isOn = { lane, step -> session.isOn(lane, step) }
            velocity = { lane, step -> session.velocity(lane, step) }
            isMuted = { lane -> session.isMuted(lane) }
            listener = object : StepGridView.Listener {
                override fun onStepSet(lane: Int, step: Int, on: Boolean) {
                    session.setStep(lane, step, on)
                    scheduleSave()
                }

                override fun onVelocityCycle(lane: Int, step: Int) {
                    session.cycleVelocity(lane, step)
                    scheduleSave()
                }

                override fun onPreview(lane: Int, step: Int) = previewLane(lane, step)

                override fun onMuteToggle(lane: Int) {
                    session.setMuted(lane, !session.isMuted(lane))
                    scheduleSave()
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
        val names = laneTitles()
        for (lane in 0 until Inst.COUNT) {
            val strip = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            strip.addView(TextView(this).apply {
                text = names[lane]
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 2
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(4), dp(6), dp(4), dp(6))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(LANE_COLORS[lane])
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

            val level = FaderView(this, vertical = true).apply {
                accent = LANE_COLORS[lane]
                defaultValue = TrackMix.defaults()[lane].level / LEVEL_MAX
                snapToDefault = true
                onChange = { v ->
                    session.updateTrack(lane) { it.copy(level = v * LEVEL_MAX) }
                    scheduleSave()
                }
            }
            levelFaders += level
            strip.addView(level, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

            val pan = FaderView(this, vertical = false).apply {
                accent = LANE_COLORS[lane]
                defaultValue = 0.5f
                snapToDefault = true
                fillFromDefault = true
                onChange = { v ->
                    session.updateTrack(lane) { it.copy(pan = v * 2f - 1f) }
                    scheduleSave()
                }
            }
            panFaders += pan
            strip.addView(pan, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))

            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val mute = square(R.string.studio_mute_letter).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                textSize = 14f
                setOnClickListener {
                    session.setMuted(lane, !session.isMuted(lane))
                    refreshMixer()
                    grid.invalidate()
                    scheduleSave()
                }
            }
            val solo = square(R.string.studio_solo_letter).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                textSize = 14f
                setOnClickListener {
                    session.toggleSolo(lane)
                    refreshMixer()
                    scheduleSave()
                }
            }
            muteButtons += mute
            soloButtons += solo
            buttons.addView(mute)
            buttons.addView(solo)
            strip.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            strip.addView(outlined(R.string.studio_fx).apply {
                textSize = 13f
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { showFxPanel(it, lane, names[lane]) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

            strips.addView(strip, LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.MATCH_PARENT).apply { marginEnd = dp(6) })
        }
        pane.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            addView(strips, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })

        // Pad XY : le doigt ferme le filtre (vers la gauche) et ajoute de la réverbération (vers le haut)
        val xyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
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
        xyRow.addView(pad, LinearLayout.LayoutParams(dp(200), dp(200)))
        val xyText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        xyText.addView(TextView(this).apply {
            setText(R.string.studio_xy_title)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        })
        xyText.addView(TextView(this).apply {
            setText(R.string.studio_xy_hint)
            textSize = 13f
            alpha = 0.75f
        })
        val gestureRow = FlowRow(this)
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
        xyText.addView(gestureRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        xyRow.addView(xyText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })
        pane.addView(xyRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        return pane
    }

    private fun laneTitles(): List<String> = listOf(
        R.string.studio_lane_kick, R.string.studio_lane_snare, R.string.studio_lane_clap,
        R.string.studio_lane_hat, R.string.studio_lane_open_hat, R.string.studio_lane_tom,
        R.string.studio_lane_perc, R.string.studio_lane_synth,
    ).map { getString(it) }

    /** Les effets d'une piste : distorsion, lo-fi, filtre et envois vers la réverbération et l'écho. */
    private fun showFxPanel(anchor: View, lane: Int, title: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        fun add(labelRes: Int, read: (TrackMix) -> Float, toT: (Float) -> Float, fromT: (Float) -> Float, write: (TrackMix, Float) -> TrackMix) {
            box.addView(TextView(this).apply { setText(labelRes); textSize = 13f; alpha = 0.75f; setPadding(0, dp(10), 0, 0) })
            val bar = SeekBar(this).apply {
                max = 1000
                progress = (toT(read(session.track(lane))).coerceIn(0f, 1f) * 1000).roundToInt()
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
                        if (lane == Inst.SYNTH) previewKeyNote() else engine.trigger(lane, 60, 1f)
                    }
                })
            }
            box.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        add(R.string.studio_fx_drive, { it.drive }, { it }, { it }, { m, v -> m.copy(drive = v) })
        add(R.string.studio_fx_crush, { it.crush }, { it }, { it }, { m, v -> m.copy(crush = v) })
        add(R.string.studio_fx_filter, { it.filter }, { (it + 1f) / 2f }, { it * 2f - 1f }, { m, v -> m.copy(filter = if (kotlin.math.abs(v) < 0.04f) 0f else v) })
        add(R.string.studio_fx_reverb, { it.reverbSend }, { it }, { it }, { m, v -> m.copy(reverbSend = v) })
        add(R.string.studio_fx_echo, { it.delaySend }, { it }, { it }, { m, v -> m.copy(delaySend = v) })
        box.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        }, 0)
        panel(anchor, box, 340).setOnDismissListener { refreshNotesControls() }
    }

    private fun refreshGestureButtons() {
        if (!::gestureClearButton.isInitialized) return
        gestureClearButton.isEnabled = session.project.pattern.hasAutomation
        gestureClearButton.alpha = if (gestureClearButton.isEnabled) 1f else 0.4f
    }

    private fun refreshMixer() {
        refreshGestureButtons()
        for (lane in 0 until levelFaders.size) {
            val m = session.track(lane)
            levelFaders[lane].value = m.level / LEVEL_MAX
            panFaders[lane].value = (m.pan + 1f) / 2f
            muteButtons[lane].setBackgroundColor(if (session.isMuted(lane)) 0x88E74C3C.toInt() else android.graphics.Color.TRANSPARENT)
            soloButtons[lane].setBackgroundColor(if (session.isSolo(lane)) 0x88F1C40F.toInt() else android.graphics.Color.TRANSPARENT)
        }
    }

    private fun buildNotesPane(): LinearLayout {
        val pane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Tonalité, gamme, verrouillage, octave
        val row1 = flowRow(topMargin = dp(2))
        row1.addView(caption(R.string.studio_key_caption), wrap())
        keyButton = outlined(null).also { it.setOnClickListener { v -> pickKey(v) } }
        row1.addView(keyButton, wrap(end = dp(4)))
        row1.addView(caption(R.string.studio_scale_caption), wrap())
        scaleButton = outlined(null).also { it.setOnClickListener { v -> pickScale(v) } }
        row1.addView(scaleButton, wrap(end = dp(4)))
        lockSwitch = SwitchCompat(this).apply {
            setText(R.string.studio_lock)
            textSize = 14f
            isChecked = scaleLocked
            setOnCheckedChangeListener { _, checked ->
                scaleLocked = checked
                refreshRoll()
            }
        }
        row1.addView(lockSwitch, wrap(end = dp(8)))
        row1.addView(caption(R.string.studio_octave), wrap())
        row1.addView(square(R.string.studio_tempo_less).also {
            it.setOnClickListener { rollBase = (rollBase - 12).coerceAtLeast(12); refreshRoll() }
        })
        row1.addView(square(R.string.studio_tempo_more).also {
            it.setOnClickListener { rollBase = (rollBase + 12).coerceAtMost(84); refreshRoll() }
        })
        pane.addView(row1)

        // Son : choix + trois réglages « macro » + avancé
        val row2 = flowRow(topMargin = dp(2))
        row2.addView(caption(R.string.studio_sound_caption), wrap())
        soundButton = outlined(null).also { it.setOnClickListener { pickSound() } }
        row2.addView(soundButton, wrap(end = dp(8)))
        val bright = SoundParam(
            R.string.studio_macro_bright,
            { session.project.synth.cutoffHz },
            { hz -> ln(hz.coerceIn(100f, 10000f) / 100f) / ln(100f) },
            { t -> 100f * exp(t * ln(100f)) },
            { v -> session.updateSynth { it.copy(cutoffHz = v) } },
        )
        val drive = SoundParam(
            R.string.studio_macro_drive,
            { session.project.synth.drive },
            { it }, { it },
            { v -> session.updateSynth { it.copy(drive = v) } },
        )
        val ambience = SoundParam(
            R.string.studio_macro_ambience,
            { session.project.synthSpace },
            { it }, { it },
            { v -> session.setSynthSpace(v) },
        )
        for (p in listOf(bright, drive, ambience)) addSoundParam(row2, p)
        row2.addView(outlined(R.string.studio_advanced).also {
            it.setOnClickListener {
                advancedPanel.visibility = if (advancedPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }, wrap())
        pane.addView(row2)

        // Réglages avancés (cachés par défaut)
        advancedPanel = flowRow(topMargin = dp(2)).also { it.visibility = View.GONE }
        waveGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            val names = listOf(R.string.studio_wave_saw, R.string.studio_wave_square, R.string.studio_wave_triangle, R.string.studio_wave_sine)
            for (i in 0 until 4) addView(outlined(names[i]).apply { id = waveIds[i] })
            addOnButtonCheckedListener { _, id, checked ->
                if (checked && !building) {
                    val wave = waveIds.indexOf(id)
                    session.updateSynth { it.copy(wave = wave) }
                    scheduleSave()
                    previewKeyNote()
                }
            }
        }
        advancedPanel.addView(waveGroup, wrap())
        val advanced = listOf(
            SoundParam(R.string.studio_adv_resonance,
                { session.project.synth.resonance }, { (it - 0.5f) / 9.5f }, { 0.5f + it * 9.5f },
                { v -> session.updateSynth { it.copy(resonance = v) } }),
            SoundParam(R.string.studio_adv_detune,
                { session.project.synth.detuneCents }, { (it / 1200f).coerceIn(0f, 1f).pow(1f / 3f) }, { it * it * it * 1200f },
                { v -> session.updateSynth { it.copy(detuneCents = v) } }),
            SoundParam(R.string.studio_adv_sub,
                { session.project.synth.subLevel }, { it }, { it },
                { v -> session.updateSynth { it.copy(subLevel = v) } }),
            SoundParam(R.string.studio_adv_sweep,
                { session.project.synth.envOctaves }, { it / 5f }, { it * 5f },
                { v -> session.updateSynth { it.copy(envOctaves = v) } }),
            SoundParam(R.string.studio_adv_attack,
                { session.project.synth.attack }, { ln(it.coerceIn(0.002f, 3f) / 0.002f) / ln(1500f) }, { 0.002f * exp(it * ln(1500f)) },
                { v -> session.updateSynth { it.copy(attack = v) } }),
            SoundParam(R.string.studio_adv_release,
                { session.project.synth.release }, { ln(it.coerceIn(0.005f, 4f) / 0.005f) / ln(800f) }, { 0.005f * exp(it * ln(800f)) },
                { v -> session.updateSynth { it.copy(release = v) } }),
            SoundParam(R.string.studio_adv_move_speed,
                { session.project.synth.lfoRate }, { (it / 12f).coerceIn(0f, 1f).pow(0.5f) }, { it * it * 12f },
                { v -> session.updateSynth { it.copy(lfoRate = v) } }),
            SoundParam(R.string.studio_adv_move_depth,
                { session.project.synth.lfoFilter }, { it / 3f }, { it * 3f },
                { v -> session.updateSynth { it.copy(lfoFilter = v) } }),
        )
        for (p in advanced) addSoundParam(advancedPanel, p)
        pane.addView(advancedPanel)

        // Arpégiateur : les notes tenues sont jouées une à une, en boucle, au rythme choisi
        val arpRow = flowRow(topMargin = dp(2))
        arpRow.addView(caption(R.string.studio_arp_caption), wrap())
        arpButton = outlined(null).also { it.setOnClickListener { v -> pickArp(v) } }
        arpRow.addView(arpButton, wrap(end = dp(4)))
        arpRateGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            val labels = listOf("1/8", "1/16", "1/32")
            for (i in 0 until 3) addView(outlined(null).apply { id = arpRateIds[i]; text = labels[i] })
            addOnButtonCheckedListener { _, id, checked ->
                if (checked && !building) {
                    session.setArpRate(arpRateIds.indexOf(id))
                    scheduleSave()
                }
            }
        }
        arpRow.addView(arpRateGroup, wrap(end = dp(4)))
        arpRow.addView(caption(R.string.studio_arp_octaves), wrap())
        arpOctaveGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            for (i in 0 until 3) addView(outlined(null).apply { id = arpOctaveIds[i]; text = (i + 1).toString() })
            addOnButtonCheckedListener { _, id, checked ->
                if (checked && !building) {
                    session.setArpOctaves(arpOctaveIds.indexOf(id) + 1)
                    scheduleSave()
                }
            }
        }
        arpRow.addView(arpOctaveGroup, wrap())
        pane.addView(arpRow)

        roll = PianoRollView(this).apply {
            accent = LANE_COLORS[Inst.SYNTH]
            noteNames = resources.getStringArray(R.array.studio_key_names).toList()
            stepCount = session.project.stepCount
            notes = { session.project.pattern.notes }
            listener = object : PianoRollView.Listener {
                override fun onAddNote(step: Int, pitch: Int, length: Int) {
                    session.addNote(step, pitch, length)
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
        pane.addView(roll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })

        // Jeu en direct : le clavier suit les lignes du piano roll ; Enregistrer garde ce qu'on joue pendant la lecture
        val liveRow = flowRow(topMargin = dp(6))
        recordButton = outlined(null).also { it.setOnClickListener { toggleRecord() } }
        liveRow.addView(recordButton, wrap(end = dp(8)))
        midiText = caption(R.string.studio_midi_connected).also { it.visibility = View.GONE }
        liveRow.addView(midiText, wrap())
        pane.addView(liveRow)

        keyboard = KeyboardView(this).apply {
            accent = LANE_COLORS[Inst.SYNTH]
            noteNames = resources.getStringArray(R.array.studio_key_names).toList()
            listener = object : KeyboardView.Listener {
                override fun onKeyDown(pitch: Int) = liveNoteOn(pitch, 0.9f, engine.playPosition)
                override fun onKeyUp(pitch: Int) = liveNoteOff(pitch, engine.playPosition)
            }
        }
        pane.addView(keyboard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)).apply { topMargin = dp(4) })
        return pane
    }

    private fun addSoundParam(row: ViewGroup, p: SoundParam) {
        soundParams += p
        p.bar = SeekBar(this).apply {
            max = 1000
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
        row.addView(labeled(p.labelRes, p.bar, 130), wrap())
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
            rollBase = autoBase()
            refreshRoll()
            refreshNotesControls()
        }
    }

    private fun previewLane(lane: Int, step: Int) {
        if (session.isMuted(lane)) return
        val v = session.velocity(lane, step).takeIf { it > 0f } ?: 1f
        if (lane == Inst.SYNTH) {
            val pitch = session.project.pattern.notes.firstOrNull { it.step == step }?.pitch ?: (24 + session.project.keyPc)
            previewPitch(pitch, v)
        } else {
            engine.trigger(lane, 60, v)
        }
    }

    private fun previewPitch(pitch: Int, velocity: Float = 0.9f, holdMs: Long = 250) {
        if (session.isMuted(Inst.SYNTH)) return
        engine.trigger(Inst.SYNTH, pitch, velocity)
        handler.postDelayed({ engine.release(Inst.SYNTH, pitch) }, holdMs)
    }

    /** Une note d'essai après un réglage du son : la tonique, dans l'octave du piano roll. */
    private fun previewKeyNote() = previewPitch(rollBase + 12 + session.project.keyPc, 0.9f, 450)

    private fun pickStyle(anchor: View) {
        dropdown(anchor, Styles.ALL.map { getString(styleName(it.id)) }) { which ->
            session.applyStyle(Styles.ALL[which])
            changed()
        }
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

    private fun pickArp(anchor: View) {
        val modes = listOf(Arp.OFF, Arp.UP, Arp.DOWN, Arp.UP_DOWN, Arp.RANDOM)
        dropdown(anchor, modes.map { getString(arpName(it)) }) { which ->
            session.setArpMode(modes[which])
            refreshNotesControls()
            scheduleSave()
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
        if (session.isMuted(Inst.SYNTH)) return
        engine.trigger(Inst.SYNTH, pitch, velocity)
        recorder.press(pitch, position)
    }

    private fun liveNoteOff(pitch: Int, position: Double) {
        engine.release(Inst.SYNTH, pitch)
        recorder.release(pitch, position)
        roll.invalidate()
    }

    private fun startMidi() {
        val controller = midi ?: MidiInputController(
            this,
            { bytes ->
                // Fil MIDI : le son et l'instant partent tout de suite, le reste (projet, écran) passe par le fil principal.
                val position = engine.playPosition
                MidiNotes.handle(
                    bytes,
                    { note, velocity ->
                        if (!session.isMuted(Inst.SYNTH)) engine.trigger(Inst.SYNTH, note, velocity)
                        handler.post { recorder.press(note, position) }
                    },
                    { note ->
                        engine.release(Inst.SYNTH, note)
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

    private fun pickSound() {
        val names = SynthPresets.ALL.map { getString(presetName(it.id)) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.studio_sound_pick)
            .setItems(names) { _, which ->
                session.setPreset(SynthPresets.ALL[which].id)
                changed()
                previewKeyNote()
            }
            .show()
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
        val notes = session.project.pattern.notes
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
        val perc = engine.instruments[Inst.PERC] as com.Atom2Universe.app.studio.dsp.Perc
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val status = TextView(this).apply { textSize = 15f }
        fun showDefaultStatus() = status.setText(if (perc.hasSample) R.string.studio_sample_mine else R.string.studio_sample_default)
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
                            engine.trigger(Inst.PERC, 60, 1f)
                            showDefaultStatus()
                        } else {
                            status.setText(if (pcm == null) R.string.studio_sample_failed else R.string.studio_sample_nothing)
                        }
                    }
                }.start()
            }
        }
        row.addView(record)
        row.addView(outlined(R.string.studio_sample_play).apply { setOnClickListener { engine.trigger(Inst.PERC, 60, 1f) } })
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
                else -> StudioFiles.saveWav(this, project) { f ->
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
        refreshTransport()
        refreshTempo()
        swingBar.progress = (p.swing * 100).roundToInt()
        spaceBar.progress = (p.space * 100).roundToInt()
        echoBar.progress = (p.echo * 100).roundToInt()
        styleButton.text = getString(R.string.studio_style_label, getString(styleName(p.styleId)))
        refreshPatterns()
        bufferButton.text = getString(R.string.studio_buffer_label, bufferBlocks)
        val wanted = if (p.stepCount == 32) step32Id else step16Id
        if (stepsGroup.checkedButtonId != wanted) {
            val was = building
            building = true
            stepsGroup.check(wanted)
            building = was
        }
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
        keyboard.rows = roll.rows
    }

    private fun refreshNotesControls() {
        val p = session.project
        val keyNames = resources.getStringArray(R.array.studio_key_names)
        keyButton.text = keyNames[p.keyPc]
        scaleButton.text = getString(scaleName(p.scaleId))
        if (lockSwitch.isChecked != scaleLocked) lockSwitch.isChecked = scaleLocked
        soundButton.text = getString(presetName(p.synth.id))
        for (sp in soundParams) sp.bar.progress = (sp.toSlider(sp.read()).coerceIn(0f, 1f) * 1000).roundToInt()
        arpButton.text = getString(arpName(p.arpMode))
        recordButton.setText(if (recorder.armed) R.string.studio_record_on else R.string.studio_record_off)
        recordButton.setBackgroundColor(if (recorder.armed) 0x66E74C3C else android.graphics.Color.TRANSPARENT)
        val was2 = building
        building = true
        if (arpRateGroup.checkedButtonId != arpRateIds[p.arpRate]) arpRateGroup.check(arpRateIds[p.arpRate])
        if (arpOctaveGroup.checkedButtonId != arpOctaveIds[p.arpOctaves - 1]) arpOctaveGroup.check(arpOctaveIds[p.arpOctaves - 1])
        building = was2
        val wanted = waveIds[p.synth.wave.coerceIn(0, 3)]
        if (waveGroup.checkedButtonId != wanted) {
            val was = building
            building = true
            waveGroup.check(wanted)
            building = was
        }
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

    private fun styleName(id: String): Int = when (id) {
        "techno" -> R.string.studio_style_techno
        "hiphop" -> R.string.studio_style_hiphop
        "lofi" -> R.string.studio_style_lofi
        "electro" -> R.string.studio_style_electro
        "ambient" -> R.string.studio_style_ambient
        else -> R.string.studio_style_house
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
        val frame = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (dark) 0xFF2B2B33.toInt() else 0xFFFFFFFF.toInt())
                setStroke(dp(1), if (dark) 0xFF4A4A54.toInt() else 0xFFCFCFD6.toInt())
            }
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val popup = PopupWindow(frame, dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT, true)
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

        /** Une couleur par ligne : grosse caisse verte, caisse claire rouge, clap orange, charleston jaune, etc. */
        val LANE_COLORS = intArrayOf(
            0xFF2ECC71.toInt(), 0xFFE74C3C.toInt(), 0xFFF39C12.toInt(), 0xFFF1C40F.toInt(),
            0xFF3498DB.toInt(), 0xFFE91E8C.toInt(), 0xFF9B59B6.toInt(), 0xFF1ABC9C.toInt(),
        )
    }
}
