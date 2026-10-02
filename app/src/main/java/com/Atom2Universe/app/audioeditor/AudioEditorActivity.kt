package com.Atom2Universe.app.audioeditor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.Manifest
import android.media.AudioManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.Atom2Universe.app.AppThemeManager
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audioeditor.core.FadeShape
import com.Atom2Universe.app.audioeditor.core.Marker
import com.Atom2Universe.app.audioeditor.engine.AndroidMicSource
import com.Atom2Universe.app.audioeditor.core.setClipGain
import com.Atom2Universe.app.audioeditor.ui.AnalysisUi
import com.Atom2Universe.app.audioeditor.ui.EditorViewModel
import com.Atom2Universe.app.audioeditor.io.AudioExporter
import com.Atom2Universe.app.audioeditor.ui.EffectUi
import com.Atom2Universe.app.audioeditor.ui.ExportRequest
import com.Atom2Universe.app.audioeditor.ui.ExportUi
import com.Atom2Universe.app.audioeditor.ui.mime
import com.Atom2Universe.app.audioeditor.ui.ProgressHandle
import com.Atom2Universe.app.audioeditor.ui.progressDialog
import com.Atom2Universe.app.audioeditor.ui.Tool
import com.Atom2Universe.app.audioeditor.ui.TimelineMath
import com.Atom2Universe.app.audioeditor.ui.TimelineView
import com.Atom2Universe.app.audioeditor.ui.TrackColors
import com.Atom2Universe.app.pixelart.ui.LabeledSlider
import com.Atom2Universe.app.pixelart.ui.SheetItem
import com.Atom2Universe.app.pixelart.ui.bottomSheet
import com.Atom2Universe.app.pixelart.ui.chip
import com.Atom2Universe.app.pixelart.ui.label
import com.Atom2Universe.app.pixelart.ui.dp
import com.Atom2Universe.app.pixelart.ui.scrollRow
import com.Atom2Universe.app.pixelart.ui.secondaryButton
import com.Atom2Universe.app.pixelart.ui.actionSheet
import com.Atom2Universe.app.pixelart.ui.confirm
import com.Atom2Universe.app.pixelart.ui.iconButton
import com.Atom2Universe.app.pixelart.ui.promptText
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * L'éditeur d'un projet audio : chronologie multipiste, transport, barre d'édition. Tout l'état vit dans
 * [EditorViewModel] (la rotation recrée l'écran sans rien perdre, même en pleine lecture) ; cet écran
 * ne fait que le montrer et lui transmettre les actions.
 */
class AudioEditorActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val vm: EditorViewModel by viewModels()

    private lateinit var timeline: TimelineView
    private lateinit var timeText: TextView
    private lateinit var title: TextView
    private lateinit var loading: ProgressBar
    private lateinit var btnPlay: ImageButton
    private lateinit var btnLoop: ImageButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var effects: EffectUi
    private lateinit var exportUi: ExportUi
    private lateinit var analysisUi: AnalysisUi
    private lateinit var btnRecord: ImageButton
    private lateinit var level: ProgressBar
    private var levelShown = 0f
    /** Le projet vient d'être créé depuis « Enregistrer tout de suite » : on lance l'enregistrement dès qu'il est prêt. */
    private var pendingRecord = false
    private var progress: ProgressHandle? = null

    /** Un bouton de la barre d'édition et ce qui décide s'il est utilisable / allumé. */
    private class EditButton(val view: ImageButton, val enabled: () -> Boolean, val on: (() -> Boolean)? = null)

    private val editButtons = ArrayList<EditButton>()
    private var ticking = false

    /** Crée un fichier audio : le sélecteur reçoit le bon type MIME (qui change avec le format) et un nom proposé. */
    private class CreateAudioFile : ActivityResultContract<Pair<String, String>, Uri?>() {
        override fun createIntent(context: Context, input: Pair<String, String>) =
            Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(input.first).putExtra(Intent.EXTRA_TITLE, input.second)
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == RESULT_OK) intent?.data else null
    }

    private val pickExportFile = registerForActivityResult(CreateAudioFile()) { uri ->
        val req = vm.pendingExport
        vm.pendingExport = null
        if (uri != null && req != null) vm.exportAudio(req, uri)
    }

    private val saveMarkersFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.exportMarkers(uri)
    }

    private val openMarkersFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importMarkers(uri)
    }

    private val pickExportFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val req = vm.pendingExport
        vm.pendingExport = null
        if (uri != null && req != null) vm.exportAudio(req, uri)
    }

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording() else Toast.makeText(this, R.string.ae_rec_no_permission, Toast.LENGTH_SHORT).show()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!vm.isPlaying && !vm.isRecording) { ticking = false; refresh(); return }
            updateTime()
            updateLevel()
            timeline.postOnAnimation(this)
        }
    }

    /** Casque débranché : on ne continue pas à jouer sur le haut-parleur. */
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { vm.pause() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeManager.applyAppStyle(this)
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_audio_editor)

        val id = intent.getStringExtra(EXTRA_PROJECT_ID)
        if (id == null) { finish(); return }
        vm.load(id)

        timeline = findViewById(R.id.ed_timeline)
        timeText = findViewById(R.id.ed_time)
        title = findViewById(R.id.ed_title)
        loading = findViewById(R.id.ed_loading)
        btnPlay = findViewById(R.id.ed_btn_play)
        btnLoop = findViewById(R.id.ed_btn_loop)
        btnUndo = findViewById(R.id.ed_btn_undo)
        btnRedo = findViewById(R.id.ed_btn_redo)
        btnRecord = findViewById(R.id.ed_btn_record)
        level = findViewById(R.id.ed_level)
        pendingRecord = savedInstanceState == null && intent.getBooleanExtra(EXTRA_RECORD, false)
        effects = EffectUi(this, vm)
        exportUi = ExportUi(this, vm, ::startExport)
        analysisUi = AnalysisUi(this, vm)
        timeline.bind(vm)
        timeline.onTrackMenu = ::showTrackMenu

        findViewById<View>(R.id.ed_btn_back).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        // Pendant un enregistrement, « retour » l'arrête et le range dans le projet au lieu de quitter.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (vm.isRecording) vm.stopRecording() else finish() }
        })
        btnRecord.setOnClickListener { toggleRecord() }
        findViewById<View>(R.id.ed_btn_more).setOnClickListener { showMainMenu() }
        btnUndo.setOnClickListener { vm.undo() }
        btnRedo.setOnClickListener { vm.redo() }
        findViewById<View>(R.id.ed_btn_to_start).setOnClickListener { vm.toStart() }
        btnPlay.setOnClickListener { vm.togglePlay() }
        findViewById<View>(R.id.ed_btn_stop).setOnClickListener { vm.stop() }
        btnLoop.setOnClickListener { vm.toggleLoop() }
        buildEditBar()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.version.collect { refresh() } }
                launch { vm.state.collect { refresh() } }
                launch { vm.work.collect { showWork(it) } }
                launch { vm.messages.collect { Toast.makeText(this@AudioEditorActivity, it, Toast.LENGTH_SHORT).show() } }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(noisy)
        progress?.dismiss()
        progress = null
        if (vm.state.value == EditorViewModel.State.READY) vm.stopPreview()
        if (vm.state.value == EditorViewModel.State.READY) {
            vm.saveNow()
            // Une rotation ne doit pas couper le son ; quitter l'écran, si.
            if (!isChangingConfigurations) vm.pause()
        }
        super.onStop()
    }

    // ---- Barre d'édition -----------------------------------------------------------------------------

    private fun addEdit(@DrawableRes icon: Int, @StringRes description: Int, enabled: () -> Boolean = { true }, on: (() -> Boolean)? = null, action: () -> Unit) {
        val b = iconButton(icon, description) { action() }
        findViewById<LinearLayout>(R.id.ed_edit_bar).addView(b)
        editButtons.add(EditButton(b, enabled, on))
    }

    private fun buildEditBar() {
        addEdit(R.drawable.ic_ae_envelope, R.string.ae_tool_envelope, on = { vm.ui.tool == Tool.ENVELOPE }) {
            vm.ui.tool = if (vm.ui.tool == Tool.ENVELOPE) Tool.SELECT else Tool.ENVELOPE
            vm.touch()
        }
        addEdit(R.drawable.ic_px_tune, R.string.ae_clip_settings, { vm.selectedClip() != null }) { showClipSheet() }
        addEdit(R.drawable.ic_px_cut, R.string.ae_cut, { vm.ui.hasSelection }) { vm.cutSelection() }
        addEdit(R.drawable.ic_px_copy, R.string.ae_copy, { vm.ui.hasSelection }) { vm.copySelection() }
        addEdit(R.drawable.ic_px_paste, R.string.ae_paste, { vm.canPaste }) { vm.paste() }
        addEdit(R.drawable.ic_px_delete, R.string.ae_delete, { vm.ui.hasSelection }) { vm.deleteSelection() }
        addEdit(R.drawable.ic_ae_split, R.string.ae_split) { vm.splitAtCursor() }
        addEdit(R.drawable.ic_ae_trim, R.string.ae_trim, { vm.ui.hasSelection }) { vm.trimToSelection() }
        addEdit(R.drawable.ic_ae_silence, R.string.ae_silence, { vm.ui.hasSelection }) { vm.silenceSelection() }
        addEdit(R.drawable.ic_ae_select_all, R.string.ae_select_all) { vm.selectAll() }
        addEdit(R.drawable.ic_ae_fx, R.string.ae_effects) { effects.showEffects() }
        addEdit(R.drawable.ic_ae_snap, R.string.ae_snap, on = { vm.ui.snap }) { vm.ui.snap = !vm.ui.snap; vm.touch() }
        addEdit(R.drawable.ic_ae_zoom_out, R.string.ae_zoom_out) { timeline.zoomOut() }
        addEdit(R.drawable.ic_ae_zoom_in, R.string.ae_zoom_in) { timeline.zoomIn() }
        addEdit(R.drawable.ic_ae_zoom_fit, R.string.ae_zoom_fit) { timeline.zoomFit() }
    }

    // ---- Export --------------------------------------------------------------------------------------

    /** La feuille a composé la demande : on choisit où écrire (un fichier, ou un dossier pour « un fichier par piste »). */
    private fun startExport(req: ExportRequest) {
        vm.pendingExport = req
        if (req.perTrack) pickExportFolder.launch(null)
        else pickExportFile.launch(req.options.format.mime to "${AudioExporter.safeName(req.baseName)}.${req.options.format.extension}")
    }

    // ---- Enregistrement ------------------------------------------------------------------------------

    private fun toggleRecord() {
        if (vm.state.value != EditorViewModel.State.READY) return
        when {
            vm.isRecording -> vm.stopRecording()
            AndroidMicSource.hasPermission(this) -> vm.startRecording()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Le vumètre monte d'un coup et redescend doucement : une crête brève reste lisible. */
    private fun updateLevel() {
        val l = vm.recordLevel
        levelShown = if (l > levelShown) l else levelShown * 0.85f
        level.progress = (levelShown.coerceIn(0f, 1f) * level.max).toInt()
    }

    // ---- Travail long (rendu d'un effet, génération) ---------------------------------------------------

    /** La boîte de progression suit l'état du ViewModel : elle réapparaît après une rotation tant que le travail dure. */
    private fun showWork(w: EditorViewModel.Work?) {
        if (w == null) {
            progress?.dismiss()
            progress = null
            return
        }
        val handle = progress ?: progressDialog(w.title) { vm.cancelWork() }.also { progress = it }
        handle.update(w.label, w.progress)
    }

    // ---- Mise à jour de l'écran ---------------------------------------------------------------------

    private fun refresh() {
        when (vm.state.value) {
            EditorViewModel.State.LOADING -> loading.visibility = View.VISIBLE
            EditorViewModel.State.FAILED -> {
                Toast.makeText(this, R.string.ae_open_failed, Toast.LENGTH_SHORT).show()
                finish()
            }
            EditorViewModel.State.READY -> {
                loading.visibility = View.GONE
                title.text = vm.name
                updateTime()
                val rec = vm.isRecording
                setEnabled(btnUndo, vm.canUndo && !rec)
                setEnabled(btnRedo, vm.canRedo && !rec)
                btnRecord.setColorFilter(if (rec) 0xFFE53935.toInt() else Color.TRANSPARENT, android.graphics.PorterDuff.Mode.SRC_ATOP)
                btnRecord.contentDescription = getString(if (rec) R.string.ae_record_stop else R.string.ae_record)
                level.visibility = if (rec) View.VISIBLE else View.INVISIBLE
                val playing = vm.isPlaying
                btnPlay.setImageResource(if (playing) R.drawable.ic_px_pause else R.drawable.ic_px_play)
                btnPlay.contentDescription = getString(if (playing) R.string.ae_pause else R.string.ae_play)
                btnLoop.isSelected = vm.ui.loop
                btnLoop.alpha = if (vm.ui.loop) 1f else 0.55f
                for (e in editButtons) {
                    setEnabled(e.view, e.enabled() && !rec)
                    e.on?.let { e.view.isSelected = it() }
                }
                timeline.invalidate()
                if (pendingRecord) { pendingRecord = false; toggleRecord() }
                if ((playing || rec) && !ticking) {
                    ticking = true
                    timeline.postOnAnimation(tick)
                }
            }
        }
    }

    private fun setEnabled(v: View, enabled: Boolean) {
        v.isEnabled = enabled
        v.alpha = if (enabled) 1f else 0.35f
    }

    private fun updateTime() {
        timeText.text = clockText(vm.playheadFrame())
    }

    private fun clockText(frames: Long): String {
        val c = TimelineMath.clock(frames, vm.project.sampleRate)
        return if (c.hours > 0) getString(R.string.ae_clock_hms_cs, c.hours, c.minutes, c.seconds, c.centis)
        else getString(R.string.ae_time_mss_cs, c.minutes, c.seconds, c.centis)
    }

    // ---- Menus --------------------------------------------------------------------------------------

    private fun showMainMenu() {
        val items = ArrayList<SheetItem>()
        items += SheetItem(R.drawable.ic_px_add, getString(R.string.ae_add_track)) { vm.addTrack(getString(R.string.ae_track_n, vm.project.tracks.size + 1)) }
        items += SheetItem(R.drawable.ic_px_star, getString(R.string.ae_add_marker)) {
            vm.addMarker(getString(R.string.ae_marker_n, vm.project.markers.size + 1))
        }
        if (vm.project.markers.isNotEmpty()) {
            items += SheetItem(R.drawable.ic_px_up, getString(R.string.ae_prev_marker)) { jumpToMarker(false) }
            items += SheetItem(R.drawable.ic_px_down, getString(R.string.ae_next_marker)) { jumpToMarker(true) }
        }
        items += SheetItem(R.drawable.ic_px_grid, getString(R.string.ae_markers)) { showMarkerList() }
        items += SheetItem(R.drawable.ic_ae_fx, getString(R.string.ae_generate)) { effects.showGenerators() }
        items += SheetItem(R.drawable.ic_px_export, getString(R.string.ae_menu_export_audio)) { exportUi.show() }
        items += SheetItem(R.drawable.ic_px_eye, getString(R.string.ae_menu_analyze)) { analysisUi.show() }
        if (vm.targetTracks().count { id -> vm.project.track(id)?.clips?.isNotEmpty() == true } >= 2) {
            items += SheetItem(R.drawable.ic_px_merge_down, getString(R.string.ae_mixdown)) { vm.mixDown(getString(R.string.ae_mixdown_name)) }
        }
        items += SheetItem(R.drawable.ic_ae_record, getString(R.string.ae_overdub), checked = vm.ui.overdub) { vm.ui.overdub = !vm.ui.overdub }
        if (vm.ui.hasSelection) {
            items += SheetItem(R.drawable.ic_ae_loop, getString(R.string.ae_repeat_selection)) {
                promptText(R.string.ae_repeat_times, "1", R.string.px_ok, number = true) { n ->
                    n.toIntOrNull()?.coerceIn(1, 99)?.let { vm.repeatSelection(it) }
                }
            }
        }
        actionSheet(null, items).show()
    }

    private fun jumpToMarker(forward: Boolean) {
        if (vm.jumpToMarker(forward)) timeline.reveal(vm.ui.cursor)
    }

    private fun showMarkerList() {
        val files = ArrayList<SheetItem>()
        if (vm.project.markers.isNotEmpty()) {
            files += SheetItem(R.drawable.ic_px_export, getString(R.string.ae_markers_export)) {
                saveMarkersFile.launch("${AudioExporter.safeName(vm.name)}.markers.txt")
            }
        }
        files += SheetItem(R.drawable.ic_px_import, getString(R.string.ae_markers_import)) {
            openMarkersFile.launch(arrayOf("text/plain", "text/*", "application/octet-stream"))
        }
        val items = files + vm.project.markers.map { m ->
            val name = m.name.ifEmpty { getString(R.string.ae_marker_n, vm.project.markers.indexOf(m) + 1) }
            SheetItem(R.drawable.ic_px_star, "${clockText(m.pos)}  $name") { showMarkerActions(m) }
        }
        actionSheet(getString(R.string.ae_markers), items).show()
    }

    private fun showMarkerActions(m: Marker) {
        actionSheet(m.name.ifEmpty { clockText(m.pos) }, listOf(
            SheetItem(R.drawable.ic_px_play, getString(R.string.ae_goto)) { vm.goToMarker(m.id); timeline.reveal(m.pos) },
            SheetItem(R.drawable.ic_px_rename, getString(R.string.ae_rename)) {
                promptText(R.string.ae_rename, m.name, R.string.px_ok) { name -> vm.renameMarker(m.id, name) }
            },
            SheetItem(R.drawable.ic_px_delete, getString(R.string.ae_delete), destructive = true) { vm.removeMarker(m.id) },
        )).show()
    }

    // ---- Réglages d'un clip ---------------------------------------------------------------------------

    private fun showClipSheet() {
        val clip = vm.selectedClip() ?: return
        val trackName = vm.project.findClip(clip.id)?.first?.name
        var gestureStarted = false
        val shapes = listOf(
            FadeShape.LINEAR to R.string.ae_shape_linear,
            FadeShape.EXPONENTIAL to R.string.ae_shape_exp,
            FadeShape.LOGARITHMIC to R.string.ae_shape_log,
            FadeShape.S_CURVE to R.string.ae_shape_s,
            FadeShape.EQUAL_POWER to R.string.ae_shape_equal,
        )
        val current = when {
            !clip.fadeIn.isNone -> clip.fadeIn.shape
            !clip.fadeOut.isNone -> clip.fadeOut.shape
            else -> clip.fadeIn.shape
        }
        val dialog = bottomSheet(clip.name.ifEmpty { trackName }) { root, dlg ->
            val db = (20 * log10(clip.gain.coerceAtLeast(1e-4f))).roundToInt().coerceIn(-24, 12)
            root.addView(LabeledSlider(this, getString(R.string.ae_gain), -24, 12, db, { getString(R.string.ae_gain_db, it) }) { value ->
                // Un seul pas d'annulation pour tout le glissement du curseur.
                if (!gestureStarted) { vm.beginGesture(); gestureStarted = true }
                val g = 10f.pow(value / 20f)
                vm.previewGesture { it.setClipGain(clip.id, g) }
            })

            root.addView(label(getString(R.string.ae_fade_curve), 12f).apply { setPadding(dp(2), dp(16), 0, dp(6)) })
            val chips = ArrayList<Pair<FadeShape, android.widget.TextView>>()
            for ((shape, text) in shapes) {
                chips.add(shape to chip(getString(text)) {
                    vm.setClipFadeShape(clip.id, shape)
                    chips.forEach { (s, c) -> c.isSelected = s == shape }
                })
            }
            chips.forEach { (s, c) -> c.isSelected = s == current }
            root.addView(scrollRow(*chips.map { it.second }.toTypedArray()))

            val row = LinearLayout(this)
            row.addView(secondaryButton(getString(R.string.ae_reverse), R.drawable.ic_px_flip_h) { dlg.dismiss(); vm.reverseClip(clip.id) })
            row.addView(secondaryButton(getString(R.string.ae_duplicate), R.drawable.ic_px_copy) { dlg.dismiss(); vm.duplicateClip(clip.id) })
            row.addView(secondaryButton(getString(R.string.ae_delete), R.drawable.ic_px_delete) { dlg.dismiss(); vm.deleteClip(clip.id) })
            root.addView(row, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) })
        }
        dialog.setOnDismissListener { if (gestureStarted) vm.commitGesture("gain") }
        dialog.show()
    }

    /** Réglages d'une piste : volume, panoramique, couleur, puis les actions (renommer, verrouiller, dupliquer, monter, descendre, supprimer). */
    private fun showTrackMenu(trackId: Int) {
        val t = vm.project.track(trackId) ?: return
        val index = vm.project.indexOfTrack(trackId)
        val last = vm.project.tracks.size - 1
        var vol = t.volume
        var pan = t.pan
        var gesture = false

        fun mix() {
            if (!gesture) { vm.beginGesture(); gesture = true }
            vm.previewTrackMix(trackId, vol, pan)
        }
        // Le glissement des curseurs est un seul pas d'annulation ; il doit être validé avant toute autre action.
        fun commitMix() {
            if (gesture) { vm.commitGesture("track_mix"); gesture = false }
        }
        fun panText(v: Int) = when {
            v == 0 -> getString(R.string.ae_pan_center)
            v < 0 -> getString(R.string.ae_pan_left, -v)
            else -> getString(R.string.ae_pan_right, v)
        }

        val dialog = bottomSheet(t.name) { root, dlg ->
            root.addView(LabeledSlider(this, getString(R.string.ae_volume), 0, 200, (t.volume * 100).roundToInt().coerceIn(0, 200),
                { getString(R.string.ae_unit_percent, it.toString()) }) { v -> vol = v / 100f; mix() })
            root.addView(LabeledSlider(this, getString(R.string.ae_pan), -100, 100, (t.pan * 100).roundToInt().coerceIn(-100, 100),
                { panText(it) }) { v -> pan = if (abs(v) <= 3) 0f else v / 100f; mix() })

            // Couleur : « Auto » reprend la palette selon la place de la piste.
            root.addView(label(getString(R.string.ae_track_color), 12f).apply { setPadding(dp(2), dp(14), 0, dp(6)) })
            val swatches = ArrayList<Pair<Int, View>>()
            fun markColor(color: Int) = swatches.forEach { (c, v) -> v.alpha = if (c == color) 1f else 0.45f }
            val auto = chip(getString(R.string.ae_color_auto)) { commitMix(); vm.setTrackColor(trackId, 0); markColor(0) }
            swatches.add(0 to auto)
            val views = ArrayList<View>()
            views.add(auto)
            for (color in TrackColors.PALETTE) {
                val sw = View(this).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { setMargins(dp(5), 0, dp(5), 0) }
                    setOnClickListener { commitMix(); vm.setTrackColor(trackId, color); markColor(color) }
                }
                swatches.add(color to sw)
                views.add(sw)
            }
            markColor(t.color)
            root.addView(scrollRow(*views.toTypedArray()))

            // Affichage : forme d'onde ou spectrogramme.
            root.addView(label(getString(R.string.ae_track_display), 12f).apply { setPadding(dp(2), dp(14), 0, dp(6)) })
            val spectro = trackId in vm.ui.spectrogramTracks
            val displayChips = ArrayList<TextView>()
            for ((i, text) in listOf(R.string.ae_display_wave, R.string.ae_display_spectro).withIndex()) {
                displayChips.add(chip(getString(text)) {
                    commitMix()
                    vm.toggleSpectrogram(trackId, i == 1)
                    displayChips.forEachIndexed { k, c -> c.isSelected = k == i }
                })
            }
            displayChips.forEachIndexed { k, c -> c.isSelected = (k == 1) == spectro }
            root.addView(scrollRow(*displayChips.toTypedArray()))

            fun row(vararg buttons: View) = LinearLayout(this).also { r ->
                buttons.forEach { r.addView(it) }
                root.addView(r, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            }
            fun act(text: Int, enabled: Boolean = true, run: () -> Unit) = secondaryButton(getString(text)) {
                if (enabled) { commitMix(); dlg.dismiss(); run() }
            }.apply { alpha = if (enabled) 1f else 0.35f }

            row(
                act(R.string.ae_rename) {
                    promptText(R.string.ae_rename, t.name, R.string.px_ok) { name ->
                        if (name.isNotEmpty()) vm.updateTrack(trackId, "rename") { it.copy(name = name) }
                    }
                },
                act(if (t.locked) R.string.ae_unlock_track else R.string.ae_lock_track) { vm.toggleLock(trackId) },
                act(R.string.ae_duplicate) { vm.duplicateTrack(trackId, getString(R.string.ae_copy_of, t.name)) },
            )
            row(
                act(R.string.ae_move_up, index > 0) { vm.moveTrack(trackId, -1) },
                act(R.string.ae_move_down, index < last) { vm.moveTrack(trackId, 1) },
                act(R.string.ae_delete) {
                    confirm(R.string.ae_delete_track_title, getString(R.string.ae_delete_track_message, t.name), R.string.ae_delete, true) { vm.removeTrack(trackId) }
                },
            )
            if (t.envelope.isNotEmpty()) row(act(R.string.ae_envelope_reset) { vm.clearEnvelope(trackId) })
        }
        dialog.setOnDismissListener { commitMix() }
        dialog.show()
    }

    companion object {
        private const val EXTRA_PROJECT_ID = "project_id"

        private const val EXTRA_RECORD = "start_recording"

        /** [record] : lancer l'enregistrement dès l'ouverture (projet créé depuis « Enregistrer tout de suite »). */
        fun intent(context: Context, projectId: String, record: Boolean = false) =
            Intent(context, AudioEditorActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId).putExtra(EXTRA_RECORD, record)
    }
}
