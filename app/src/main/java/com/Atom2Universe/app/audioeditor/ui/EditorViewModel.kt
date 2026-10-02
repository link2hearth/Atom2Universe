package com.Atom2Universe.app.audioeditor.ui

import android.app.Application
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audioeditor.AudioEditorStorage
import com.Atom2Universe.app.audioeditor.core.EditSession
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.Track
import com.Atom2Universe.app.audioeditor.core.PeakData
import com.Atom2Universe.app.audioeditor.core.addEnvelopePoint
import com.Atom2Universe.app.audioeditor.core.addMarker
import com.Atom2Universe.app.audioeditor.core.clearEnvelope
import com.Atom2Universe.app.audioeditor.core.removeEnvelopePoint
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.core.Clip
import com.Atom2Universe.app.audioeditor.core.FadeShape
import com.Atom2Universe.app.audioeditor.core.deleteClip
import com.Atom2Universe.app.audioeditor.core.duplicateClip
import com.Atom2Universe.app.audioeditor.core.removeMarker
import com.Atom2Universe.app.audioeditor.core.repeatRange
import com.Atom2Universe.app.audioeditor.core.reverseRange
import com.Atom2Universe.app.audioeditor.core.setClipFades
import com.Atom2Universe.app.audioeditor.core.updateClip
import com.Atom2Universe.app.audioeditor.core.updateMarker
import com.Atom2Universe.app.audioeditor.core.copyRange
import com.Atom2Universe.app.audioeditor.core.deleteRange
import com.Atom2Universe.app.audioeditor.core.pasteAt
import com.Atom2Universe.app.audioeditor.core.duplicateTrack
import com.Atom2Universe.app.audioeditor.core.insertSilence
import com.Atom2Universe.app.audioeditor.core.moveTrack
import com.Atom2Universe.app.audioeditor.core.removeTrack
import com.Atom2Universe.app.audioeditor.core.silenceRange
import com.Atom2Universe.app.audioeditor.core.splitAt
import com.Atom2Universe.app.audioeditor.core.removeLeadingGap
import com.Atom2Universe.app.audioeditor.core.trimToRange
import com.Atom2Universe.app.audioeditor.engine.AndroidAudioSink
import com.Atom2Universe.app.audioeditor.engine.LoopRange
import com.Atom2Universe.app.audioeditor.dsp.Analysis
import com.Atom2Universe.app.audioeditor.dsp.Effect
import com.Atom2Universe.app.audioeditor.dsp.GenerateSource
import com.Atom2Universe.app.audioeditor.dsp.MixDown
import com.Atom2Universe.app.audioeditor.dsp.MixReader
import com.Atom2Universe.app.audioeditor.dsp.NoiseProfile
import com.Atom2Universe.app.audioeditor.dsp.RenderRange
import com.Atom2Universe.app.audioeditor.dsp.RunContext
import com.Atom2Universe.app.audioeditor.dsp.SignalStats
import com.Atom2Universe.app.audioeditor.dsp.Spectrogram
import com.Atom2Universe.app.audioeditor.dsp.TrackRangeReader
import com.Atom2Universe.app.audioeditor.engine.EffectPreview
import com.Atom2Universe.app.audioeditor.engine.PlaybackEngine
import com.Atom2Universe.app.audioeditor.engine.PreviewPlayer
import com.Atom2Universe.app.audioeditor.engine.RecordController
import com.Atom2Universe.app.audioeditor.engine.RecordException
import com.Atom2Universe.app.audioeditor.engine.RecordingPlacement
import com.Atom2Universe.app.audioeditor.io.AudioExporter
import com.Atom2Universe.app.audioeditor.io.FfmpegTranscoder
import com.Atom2Universe.app.audioeditor.io.FileSampleProvider
import com.Atom2Universe.app.audioeditor.io.LoadedProject
import com.Atom2Universe.app.audioeditor.io.MarkerText
import com.Atom2Universe.app.audioeditor.io.PeakFiles
import com.Atom2Universe.app.audioeditor.io.ProjectMeta
import com.Atom2Universe.app.audioeditor.io.ViewState
import com.Atom2Universe.app.audioeditor.io.peaksFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Ce qui se voit à l'écran et n'est pas dans le projet : curseur, sélection, zoom, défilement, pistes choisies. Fil principal seulement. */
class EditorUi {
    var cursor = 0L
    var selStart = 0L
    var selEnd = 0L
    /** Pistes visées par les éditions. */
    val selectedTracks = LinkedHashSet<Int>()
    /** Zoom en trames par pixel ; 0 = à calculer à la première mise en page. */
    var framesPerPixel = 0.0
    var scrollFrame = 0L
    var scrollY = 0
    var snap = true
    var loop = false
    var tool = Tool.SELECT
    var selectedClip = -1
    /** Lecture suivie : la chronologie défile avec la tête de lecture. */
    var follow = true
    /** Pendant un enregistrement, les pistes jouent en même temps (casque conseillé). */
    var overdub = true
    /** Les pistes montrées en spectrogramme. */
    val spectrogramTracks = LinkedHashSet<Int>()

    val hasSelection get() = selEnd > selStart
}

enum class Tool { SELECT, ENVELOPE }

/**
 * Garde le projet ouvert à travers les rotations : la session d'édition (historique compris), le
 * moteur de lecture, les crêtes chargées et l'enregistrement automatique. L'écran ([com.Atom2Universe.app.audioeditor.AudioEditorActivity])
 * et la chronologie ne font que lire cet état et appeler ses méthodes ; tout se passe sur le fil principal.
 *
 * Après un changement, [version] augmente : l'écran s'y abonne pour se redessiner.
 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {

    enum class State { LOADING, READY, FAILED }

    val store = AudioEditorStorage.store(app)

    val state = MutableStateFlow(State.LOADING)
    val version = MutableStateFlow(0)

    /** Messages à montrer (ressources de texte) : lus par l'écran, pas rejoués à la rotation. */
    val messages = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    val ui = EditorUi()

    lateinit var session: EditSession
        private set
    lateinit var engine: PlaybackEngine
        private set
    private lateinit var provider: FileSampleProvider
    private lateinit var recorder: RecordController
    private lateinit var meta: ProjectMeta
    var projectId: String? = null
        private set

    private val main = Handler(Looper.getMainLooper())
    private val peaks = HashMap<String, PeakData>()
    private val peaksLoading = HashSet<String>()
    private var saveJob: Job? = null
    private var savedRevision = 0
    /** Position où la lecture a été mise en pause : « Lecture » reprend de là tant que ni curseur ni sélection n'ont bougé. */
    private var pausedAt = -1L
    /** Position où la dernière lecture a commencé : « Arrêt » y revient. */
    private var playStart = 0L

    private val audioManager = app.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null

    val project: Project get() = session.project
    val name: String get() = if (this::meta.isInitialized) meta.name else ""
    val isPlaying: Boolean get() = state.value == State.READY && engine.isPlaying

    // ---- Ouverture et fermeture ----------------------------------------------------------------------

    fun load(id: String) {
        if (projectId == id) return
        projectId = id
        viewModelScope.launch {
            val loaded: LoadedProject? = withContext(Dispatchers.IO) { store.open(id, collectGarbage = true) }
            if (loaded == null) { state.value = State.FAILED; return@launch }
            meta = loaded.meta
            session = EditSession(loaded.project)
            provider = FileSampleProvider(store.dir(id))
            engine = PlaybackEngine(provider, { rate -> AndroidAudioSink(rate) }, { AndroidAudioSink.raiseThreadPriority() })
            engine.setProject(loaded.project)
            engine.onEnded = { end -> main.post { onPlaybackEnded(end) } }
            recorder = RecordController(getApplication(), engine).also { r ->
                r.onError = { e -> main.post { onRecordError(e) } }
                withContext(Dispatchers.IO) { r.cleanupOldRecordings() }
            }
            engine.onError = { main.post { stopPlaybackState(); messages.tryEmit(R.string.ae_error_playback) } }
            savedRevision = session.revision
            val v = loaded.meta.view
            ui.cursor = v.cursor
            ui.selStart = v.selStart
            ui.selEnd = v.selEnd
            ui.framesPerPixel = v.framesPerPixel
            ui.scrollFrame = v.scrollFrame
            ui.snap = v.snap
            ui.spectrogramTracks.clear()
            ui.spectrogramTracks.addAll(v.spectrogramTracks)
            val ids = loaded.project.tracks.map { it.id }
            ui.selectedTracks.clear()
            ui.selectedTracks.addAll(if (v.activeTrack in ids) listOf(v.activeTrack) else ids)
            state.value = State.READY
            touch()
        }
    }

    override fun onCleared() {
        if (state.value != State.READY) return
        saveJob?.cancel()
        previewPlayer.stop()
        if (isRecording) recorder.cancel()
        engine.release()
        abandonFocus()
        provider.close()
        // Fermeture : oublie les sources sans clip, enregistre, ramasse les fichiers et refait la vignette.
        val id = projectId ?: return
        val p = session.project
        val m = currentMeta()
        val touched = session.revision != savedRevision
        AudioEditorStorage.writesInFlight.value++
        AudioEditorStorage.ioScope.launch {
            try {
                AudioEditorStorage.saveMutex.withLock { store.finish(id, p, m, touch = touched) }
            } catch (e: Exception) {
                // Disque plein : le manifeste d'avant reste intact (écriture atomique), mieux vaut perdre
                // cette sauvegarde que l'application.
                Log.w("AudioEditor", "Fermeture du projet : enregistrement impossible", e)
            } finally {
                AudioEditorStorage.writesInFlight.value--
            }
        }
    }

    fun touch() { version.value++ }

    // ---- Enregistrement ------------------------------------------------------------------------------

    private fun currentMeta() = meta.copy(
        view = ViewState(
            cursor = ui.cursor, selStart = ui.selStart, selEnd = ui.selEnd, framesPerPixel = ui.framesPerPixel,
            scrollFrame = ui.scrollFrame, activeTrack = ui.selectedTracks.singleOrNull() ?: 0, snap = ui.snap,
            spectrogramTracks = ui.spectrogramTracks.toList(),
        ),
    )

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            saveNow()
        }
    }

    /** Écrit le manifeste maintenant (arrêt de l'écran, fin du délai). La date de modification ne bouge que si le projet a changé. */
    fun saveNow() {
        if (state.value != State.READY) return
        val id = projectId ?: return
        val p = session.project
        val m = currentMeta()
        val rev = session.revision
        val touched = rev != savedRevision
        AudioEditorStorage.writesInFlight.value++
        AudioEditorStorage.ioScope.launch {
            try {
                val written = AudioEditorStorage.saveMutex.withLock { store.save(id, p, m, touch = touched) }
                main.post { meta = written; savedRevision = rev }
            } catch (e: Exception) {
                // Rien n'est acquitté (savedRevision reste en arrière) : la prochaine sauvegarde réessaiera.
                Log.w("AudioEditor", "Enregistrement impossible", e)
            } finally {
                AudioEditorStorage.writesInFlight.value--
            }
        }
    }

    // ---- Crêtes de forme d'onde ----------------------------------------------------------------------

    /** Les crêtes d'une source, ou `null` tant qu'elles se chargent (l'écran se redessine quand elles arrivent). */
    fun peaksFor(src: Source): PeakData? {
        peaks[src.id]?.let { return it }
        val id = projectId ?: return null
        if (peaksLoading.add(src.id)) {
            viewModelScope.launch {
                val dir = store.dir(id)
                val d = withContext(Dispatchers.IO) {
                    runCatching { PeakFiles.loadOrBuild(File(dir, src.file), src.peaksFile(dir)) }.getOrNull()
                }
                if (d != null) peaks[src.id] = d
                peaksLoading.remove(src.id)
                touch()
            }
        }
        return null
    }

    // ---- Échantillons bruts (zoom serré) -------------------------------------------------------------

    /** Des échantillons bruts d'une source, planaires, à partir de la trame [first]. */
    class RawWindow(val first: Long, val data: Array<FloatArray>) {
        val end: Long get() = first + data[0].size
    }

    private val rawCache = object : LinkedHashMap<String, RawWindow>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RawWindow>?) = size > RAW_WINDOWS
    }
    private val rawLoading = HashSet<String>()

    /**
     * Les échantillons bruts de `[from, to)` d'une source, pour dessiner la forme d'onde en gros plan ; `null` tant
     * qu'ils ne sont pas là (l'écran se redessine à leur arrivée). La fenêtre chargée déborde de part et d'autre :
     * faire défiler un peu ne recharge rien.
     */
    fun rawFor(src: Source, from: Long, to: Long): RawWindow? {
        val a = from.coerceAtLeast(0)
        val b = to.coerceAtMost(src.frames)
        if (b <= a || b - a > RAW_MAX_FRAMES) return null
        rawCache[src.id]?.let { if (a >= it.first && b <= it.end) return it }
        if (rawLoading.add(src.id)) {
            val margin = minOf(maxOf(b - a, 4096L), (RAW_MAX_FRAMES - (b - a)) / 2)
            val first = (a - margin).coerceAtLeast(0)
            val count = (minOf(b + margin, src.frames) - first).toInt()
            viewModelScope.launch {
                val data = withContext(Dispatchers.IO) {
                    runCatching { Array(src.channels.coerceAtLeast(1)) { FloatArray(count) }.also { provider.read(src, first, count, it, 0) } }.getOrNull()
                }
                if (data != null) rawCache[src.id] = RawWindow(first, data)
                rawLoading.remove(src.id)
                touch()
            }
        }
        return null
    }

    // ---- Curseur et sélection ------------------------------------------------------------------------

    fun setCursor(frame: Long) {
        if (isRecording) return
        ui.cursor = frame.coerceAtLeast(0)
        pausedAt = -1
        if (isPlaying) { engine.seek(ui.cursor) } else touch()
        touch()
    }

    fun setSelection(a: Long, b: Long) {
        ui.selStart = minOf(a, b).coerceAtLeast(0)
        ui.selEnd = maxOf(a, b).coerceAtLeast(0)
        if (ui.selEnd > ui.selStart) ui.cursor = ui.selStart
        pausedAt = -1
        if (isPlaying) applyPlaybackRange()
        touch()
    }

    fun clearSelection() {
        ui.selStart = ui.cursor
        ui.selEnd = ui.cursor
        touch()
    }

    fun selectAll() {
        ui.selectedTracks.clear()
        ui.selectedTracks.addAll(project.tracks.map { it.id })
        setSelection(0, project.length)
    }

    /** Toucher un en-tête : seule cette piste est visée ; la retoucher, quand elle l'est déjà seule, vise toutes les pistes. */
    fun tapTrack(id: Int) {
        val only = ui.selectedTracks.size == 1 && id in ui.selectedTracks
        ui.selectedTracks.clear()
        if (only) ui.selectedTracks.addAll(project.tracks.map { it.id }) else ui.selectedTracks.add(id)
        touch()
    }

    fun selectOnlyTrack(id: Int) {
        if (ui.selectedTracks.size == 1 && id in ui.selectedTracks) return
        ui.selectedTracks.clear()
        ui.selectedTracks.add(id)
        touch()
    }

    /** Les pistes que visent les éditions : celles qui sont choisies et non verrouillées ; si aucune n'est choisie, toutes les pistes libres. */
    fun targetTracks(): List<Int> {
        val free = project.tracks.filter { !it.locked }
        val chosen = free.filter { it.id in ui.selectedTracks }
        return (chosen.ifEmpty { free }).map { it.id }
    }

    /** Les trames « collantes » : début, curseur, bords de sélection, repères, bords de clips. */
    fun snapCandidates(excludeClip: Int = -1, excludeSelection: Boolean = false): LongArray {
        val out = ArrayList<Long>()
        out += 0L
        // Quand on déplace la sélection elle-même, ni elle ni le curseur (collé à son début) ne sont des points collants.
        if (!excludeSelection) {
            out += ui.cursor
            if (ui.hasSelection) { out += ui.selStart; out += ui.selEnd }
        }
        for (m in project.markers) { out += m.pos; if (m.isRegion) out += m.end }
        for (t in project.tracks) for (c in t.clips) if (c.id != excludeClip) { out += c.start; out += c.end }
        return out.toLongArray()
    }

    /** [frame] ramenée au point collant le plus proche s'il est à moins de [thresholdFrames], si l'aimant est actif. */
    fun snapped(frame: Long, thresholdFrames: Double, excludeClip: Int = -1): Long =
        if (ui.snap) TimelineMath.snap(frame, snapCandidates(excludeClip), thresholdFrames) else frame

    /** Nouveau début de la sélection qu'on déplace (longueur [len] inchangée) : son début ou sa fin se colle aux points proches. */
    fun snappedSelectionMove(newStart: Long, len: Long, thresholdFrames: Double): Long =
        if (ui.snap) TimelineMath.snapSpan(newStart, len, snapCandidates(excludeSelection = true), thresholdFrames) else newStart

    /** Nouveau début d'un clip qu'on déplace : son début ou sa fin se colle aux points proches (le clip lui-même n'en est pas un). */
    fun snappedMove(clip: Clip, newStart: Long, thresholdFrames: Double): Long =
        if (ui.snap) TimelineMath.snapSpan(newStart, clip.length, snapCandidates(clip.id), thresholdFrames) else newStart

    // ---- Éditions ------------------------------------------------------------------------------------

    private fun edit(label: String, f: (Project) -> Project): Boolean {
        if (isRecording) return false
        val changed = session.commit(label, f(session.project))
        if (changed) {
            engine.setProject(session.project)
            pausedAt = -1
            scheduleSave()
        }
        touch()
        return changed
    }

    val canUndo get() = state.value == State.READY && session.canUndo
    val canRedo get() = state.value == State.READY && session.canRedo
    val canPaste get() = state.value == State.READY && session.clipboard?.isEmpty == false

    fun undo() { if (!isRecording && session.undo()) afterHistoryMove() }
    fun redo() { if (!isRecording && session.redo()) afterHistoryMove() }

    private fun afterHistoryMove() {
        engine.setProject(session.project)
        // Une annulation peut avoir supprimé la piste choisie.
        val ids = project.tracks.map { it.id }.toSet()
        ui.selectedTracks.retainAll(ids)
        if (ui.selectedTracks.isEmpty()) ui.selectedTracks.addAll(ids)
        pausedAt = -1
        scheduleSave()
        touch()
    }

    fun splitAtCursor() { edit("split") { it.splitAt(targetTracks(), ui.cursor) } }

    fun copySelection() {
        if (!ui.hasSelection) return
        session.clipboard = project.copyRange(targetTracks(), ui.selStart, ui.selEnd)
        touch()
    }

    fun deleteSelection() {
        if (!ui.hasSelection) return
        val at = ui.selStart
        if (edit("delete") { it.deleteRange(targetTracks(), ui.selStart, ui.selEnd, ripple = true) }) {
            ui.cursor = at
            ui.selEnd = at
            touch()
        }
    }

    fun cutSelection() {
        if (!ui.hasSelection) return
        copySelection()
        deleteSelection()
    }

    /** Colle au curseur ; une sélection est d'abord supprimée (elle est remplacée par ce qu'on colle). */
    fun paste() {
        val cb = session.clipboard ?: return
        if (cb.isEmpty) return
        val at = if (ui.hasSelection) ui.selStart else ui.cursor
        val targets = targetTracks()
        val replace = ui.hasSelection
        val from = ui.selStart
        val to = ui.selEnd
        if (edit("paste") { p -> (if (replace) p.deleteRange(targets, from, to, ripple = true) else p).pasteAt(cb, targets, at, ripple = true) }) {
            ui.cursor = at + cb.length
            ui.selStart = ui.cursor
            ui.selEnd = ui.cursor
            touch()
        }
    }

    fun trimToSelection() {
        if (!ui.hasSelection) return
        val a = ui.selStart
        if (edit("trim") { it.trimToRange(ui.selStart, ui.selEnd) }) {
            ui.cursor = 0
            ui.selStart = 0
            ui.selEnd = ui.selEnd - a
            touch()
        }
    }

    fun removeLeadingGap() {
        if (project.start <= 0) return
        edit("lead_gap") { it.removeLeadingGap() }
    }

    fun silenceSelection() {
        if (!ui.hasSelection) return
        edit("silence") { it.silenceRange(targetTracks(), ui.selStart, ui.selEnd) }
    }

    // ---- Gestes continus (déplacer, rogner, fondus, gain) ---------------------------------------------

    private var gestureBefore: Project? = null

    /** Le projet tel qu'il était au début du geste en cours ; `null` hors geste. */
    val gestureBase: Project? get() = gestureBefore

    /** Début d'un geste continu : tout ce qui suit jusqu'à [commitGesture] ne fait qu'un seul pas d'annulation. */
    fun beginGesture() { gestureBefore = session.project }

    /** Montre (et fait entendre) le résultat du geste, recalculé depuis le projet de départ : jamais d'accumulation d'erreurs. */
    fun previewGesture(f: (Project) -> Project) {
        val before = gestureBefore ?: return
        session.preview(f(before))
        engine.setProject(session.project)
        touch()
    }

    fun commitGesture(label: String) {
        val before = gestureBefore ?: return
        gestureBefore = null
        if (session.commitGesture(label, before)) {
            pausedAt = -1
            scheduleSave()
        }
        touch()
    }

    /** Abandonne le geste : le projet revient à son état de départ. */
    fun cancelGesture() {
        val before = gestureBefore ?: return
        gestureBefore = null
        session.preview(before)
        engine.setProject(before)
        touch()
    }

    // ---- Clips ---------------------------------------------------------------------------------------

    fun selectedClip(): Clip? = project.findClip(ui.selectedClip)?.second

    fun setClipFadeShape(id: Int, shape: FadeShape) {
        edit("fade_shape") { p -> p.updateClip(id) { c -> c.copy(fadeIn = c.fadeIn.copy(shape = shape), fadeOut = c.fadeOut.copy(shape = shape)) } }
    }

    fun reverseClip(id: Int) {
        val (t, c) = project.findClip(id) ?: return
        edit("reverse") { it.reverseRange(listOf(t.id), c.start, c.end) }
    }

    fun duplicateClip(id: Int) {
        var newId: Int? = null
        if (edit("duplicate") { p -> p.duplicateClip(id).let { (np, nid) -> newId = nid; np } }) {
            newId?.let { ui.selectedClip = it }
            touch()
        }
    }

    fun deleteClip(id: Int) {
        if (edit("delete_clip") { it.deleteClip(id) }) {
            ui.selectedClip = -1
            touch()
        }
    }

    /** Répète [times] fois, à la suite, la plage sélectionnée sur les pistes visées. */
    fun repeatSelection(times: Int) {
        if (!ui.hasSelection || times <= 0) return
        edit("repeat") { it.repeatRange(targetTracks(), ui.selStart, ui.selEnd, times) }
    }

    // ---- Repères -------------------------------------------------------------------------------------

    /** Pose un repère au curseur, ou une zone sur la sélection. */
    fun addMarker(name: String) {
        val a = if (ui.hasSelection) ui.selStart else ui.cursor
        val b = if (ui.hasSelection) ui.selEnd else ui.cursor
        edit("marker") { it.addMarker(a, b, name).first }
    }

    fun removeMarker(id: Int) { edit("marker_remove") { it.removeMarker(id) } }

    fun renameMarker(id: Int, name: String) { edit("marker_rename") { it.updateMarker(id) { m -> m.copy(name = name) } } }

    /** Place le curseur sur un repère (et sélectionne sa zone, si c'en est une). */
    fun goToMarker(id: Int) {
        val m = project.markers.firstOrNull { it.id == id } ?: return
        if (m.isRegion) setSelection(m.pos, m.end) else { setCursor(m.pos); clearSelection() }
    }

    /** Saute au repère suivant ([forward]) ou précédent le curseur ; rend `false` s'il n'y en a pas. */
    fun jumpToMarker(forward: Boolean): Boolean {
        val here = ui.cursor
        val target = if (forward) project.markers.filter { it.pos > here }.minByOrNull { it.pos }
        else project.markers.filter { it.pos < here }.maxByOrNull { it.pos }
        if (target == null) return false
        goToMarker(target.id)
        return true
    }

    // ---- Pistes --------------------------------------------------------------------------------------

    fun updateTrack(id: Int, label: String, f: (Track) -> Track) { edit(label) { it.mapTrack(id, f) } }

    fun toggleMute(id: Int) = updateTrack(id, "mute") { it.copy(mute = !it.mute) }
    fun toggleSolo(id: Int) = updateTrack(id, "solo") { it.copy(solo = !it.solo) }
    fun toggleLock(id: Int) = updateTrack(id, "lock") { it.copy(locked = !it.locked) }

    fun addTrack(name: String) {
        var newId = -1
        if (edit("add_track") { p -> p.addTrack(name).let { (np, id) -> newId = id; np } }) {
            ui.selectedTracks.clear()
            ui.selectedTracks.add(newId)
            touch()
        }
    }

    /** Le volume (0 … 2) et le panoramique (−1 … +1) d'une piste, par geste : un seul pas d'annulation pour tout le glissement. */
    fun previewTrackMix(id: Int, volume: Float, pan: Float) {
        previewGesture { it.mapTrack(id) { t -> t.copy(volume = volume, pan = pan) } }
    }

    fun setTrackColor(id: Int, color: Int) = updateTrack(id, "track_color") { it.copy(color = color) }

    /** Monte ([delta] < 0) ou descend ([delta] > 0) la piste dans la liste. */
    fun moveTrack(id: Int, delta: Int) {
        edit("move_track") { p -> p.moveTrack(id, p.indexOfTrack(id) + delta) }
    }

    fun duplicateTrack(id: Int, name: String) {
        var newId: Int? = null
        if (edit("duplicate_track") { p -> p.duplicateTrack(id, name).let { (np, n) -> newId = n; np } }) {
            newId?.let { ui.selectedTracks.clear(); ui.selectedTracks.add(it); touch() }
        }
    }

    fun removeTrack(id: Int) {
        if (edit("remove_track") { it.removeTrack(id) }) {
            ui.selectedTracks.remove(id)
            if (ui.selectedTracks.isEmpty()) ui.selectedTracks.addAll(project.tracks.map { it.id })
            touch()
        }
    }

    // ---- Lecture -------------------------------------------------------------------------------------

    /** La position à montrer : celle qui s'entend pendant la lecture, sinon le curseur. */
    fun playheadFrame(): Long = recordingSpan()?.last ?: if (isPlaying) engine.position() else ui.cursor

    fun togglePlay() { if (isPlaying) pause() else play() }

    fun play() {
        if (state.value != State.READY || isRecording) return
        if (!requestFocus()) return
        val from: Long
        if (pausedAt >= 0) {
            from = pausedAt
        } else {
            from = if (ui.hasSelection) ui.selStart else ui.cursor
            playStart = from
        }
        pausedAt = -1
        engine.setProject(project)
        applyPlaybackRange()
        engine.play(from)
        touch()
    }

    /** Boucle ou arrêt de fin selon la sélection et le mode boucle. */
    private fun applyPlaybackRange() {
        if (ui.hasSelection) {
            engine.setLoop(if (ui.loop) LoopRange(ui.selStart, ui.selEnd) else null)
            engine.setStopAt(if (ui.loop) -1 else ui.selEnd)
        } else {
            engine.setLoop(if (ui.loop) LoopRange(0) else null)
            engine.setStopAt(-1)
        }
    }

    fun pause() {
        if (!isPlaying || isRecording) return
        engine.pause()
        ui.cursor = engine.position()
        pausedAt = ui.cursor
        touch()
    }

    /** Arrêt : le curseur revient là où la lecture avait commencé. */
    fun stop() {
        if (state.value != State.READY) return
        if (isRecording) { stopRecording(); return }
        val wasPlaying = isPlaying
        engine.stop(returnToStart = true)
        if (wasPlaying || pausedAt >= 0) ui.cursor = playStart
        pausedAt = -1
        abandonFocus()
        touch()
    }

    fun toStart() {
        if (isRecording) return
        pausedAt = -1
        ui.cursor = 0
        ui.scrollFrame = 0
        if (isPlaying) engine.seek(0)
        touch()
    }

    fun toggleLoop() {
        ui.loop = !ui.loop
        if (isPlaying) applyPlaybackRange()
        touch()
    }

    private fun onPlaybackEnded(end: Long) {
        ui.cursor = if (ui.hasSelection && !ui.loop) ui.selStart else end
        pausedAt = -1
        abandonFocus()
        touch()
    }

    private fun stopPlaybackState() {
        pausedAt = -1
        abandonFocus()
        touch()
    }

    // ---- Enregistrement ------------------------------------------------------------------------------

    val isRecording: Boolean get() = state.value == State.READY && this::recorder.isInitialized && recorder.isRecording

    /** Crête du dernier bloc enregistré (0 à 1), pour le vumètre. */
    val recordLevel: Float get() = if (isRecording) recorder.level else 0f

    /** Les trames du projet que l'enregistrement couvre pour l'instant : de son départ à maintenant, ou `null` hors enregistrement. */
    fun recordingSpan(): LongRange? {
        if (!isRecording) return null
        val start = recorder.startFrame
        return start..(start + (recorder.seconds * project.sampleRate).toLong())
    }

    /** La piste qui reçoit l'enregistrement : la piste choisie si elle est libre, ou `null` (une nouvelle piste sera créée). */
    fun recordingTrack(): Int? = ui.selectedTracks.singleOrNull()?.takeIf { id -> project.track(id)?.locked == false }

    private fun recordErrorText(code: RecordException.Code): Int = when (code) {
        RecordException.Code.NO_PERMISSION -> R.string.ae_rec_no_permission
        RecordException.Code.INIT_FAILED -> R.string.ae_rec_init_failed
        RecordException.Code.WRITE_FAILED -> R.string.ae_rec_write_failed
        else -> R.string.ae_rec_read_failed
    }

    /** Lance l'enregistrement à partir du curseur (ou du début de la sélection). L'écran a déjà obtenu la permission du micro. */
    fun startRecording(): Boolean {
        if (state.value != State.READY || isRecording || work.value != null) return false
        stopPreview()
        if (isPlaying) engine.stop(returnToStart = false)
        val from = if (ui.hasSelection) ui.selStart else ui.cursor
        val withPlayback = ui.overdub && project.length > 0
        try {
            if (withPlayback) {
                requestFocus()
                engine.setProject(project)
                engine.setLoop(null)
                engine.setStopAt(-1)
            }
            recorder.start(from, withPlayback)
        } catch (e: RecordException) {
            abandonFocus()
            messages.tryEmit(recordErrorText(e.code))
            return false
        }
        pausedAt = -1
        playStart = from
        ui.cursor = from
        touch()
        return true
    }

    private fun onRecordError(e: RecordException) {
        if (!isRecording) return
        messages.tryEmit(recordErrorText(e.code))
        stopRecording()
    }

    /** Arrête et pose l'enregistrement dans le projet (un seul pas d'annulation). Un enregistrement vide ne laisse rien. */
    fun stopRecording() {
        if (!isRecording) return
        val at = recorder.startFrame
        val seconds = recorder.seconds
        val wav = try {
            recorder.stop()
        } catch (e: RecordException) {
            if (e.code != RecordException.Code.NO_DATA) messages.tryEmit(recordErrorText(e.code))
            abandonFocus()
            touch()
            return
        }
        abandonFocus()
        pausedAt = -1
        val id = projectId ?: return
        val before = session.project
        val rate = before.sampleRate
        val approx = (seconds * rate).toLong()
        val onto = recordingTrack()?.let { before.track(it) }
            ?.takeIf { t -> t.clips.none { it.end > at && it.start < at + approx } }?.id
        val app = getApplication<Application>()
        val trackName = app.getString(R.string.ae_track_n, before.tracks.size + 1)
        val clipName = app.getString(R.string.ae_recording)
        work.value = Work(R.string.ae_record_progress_title, "", -1f)
        workJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    RecordingPlacement.place(before, store.dir(id), wav, onto, trackName, clipName, at, workContext(rate, R.string.ae_record_progress_title, ""))
                }
                if (result.newSources.isNotEmpty() && session.project === before) {
                    edit("record") { result.project }
                    val created = result.project.tracks.firstOrNull { t -> before.tracks.none { it.id == t.id } }
                    if (created != null) { ui.selectedTracks.clear(); ui.selectedTracks.add(created.id) }
                    ui.cursor = at
                    ui.selStart = at
                    ui.selEnd = at
                }
            } catch (e: CancellationException) {
                // Annulé : le WAV reste dans le cache, ramassé au prochain lancement.
            } catch (e: Throwable) {
                messages.tryEmit(R.string.ae_effect_failed)
            } finally {
                work.value = null
                workJob = null
                touch()
            }
        }
    }

    // ---- Effets, aperçu, génération ------------------------------------------------------------------

    /** Un travail long en cours (rendu d'effet, génération). L'écran en montre la progression, rotation comprise : [progress] < 0 = indéterminé. */
    class Work(@StringRes val title: Int, val label: String, val progress: Float)

    val work = MutableStateFlow<Work?>(null)
    private var workJob: Job? = null

    fun cancelWork() { workJob?.cancel() }

    /** Progression et annulation d'un rendu, branchées sur [work] et sur la coroutine qui l'exécute. */
    private suspend fun workContext(rate: Int, @StringRes title: Int, label: String): RunContext {
        val job = coroutineContext[Job]!!
        var last = -1f
        return RunContext(rate, { f ->
            // Un rendu rapporte à chaque bloc : on ne republie que quand la barre bouge vraiment.
            if (f - last >= 0.005f || f >= 1f) { last = f; work.value = Work(title, label, f) }
        }, { !job.isActive })
    }

    /** Le rendu d'un effet s'applique à la sélection, sinon à tout le projet, sur les pistes visées. */
    fun applyEffect(def: EffectDef, effect: Effect, label: String) {
        if (state.value != State.READY || work.value != null) return
        stopPreview()
        pause()
        val id = projectId ?: return
        val before = session.project
        val tracks = targetTracks()
        val from = if (ui.hasSelection) ui.selStart else 0L
        val to = if (ui.hasSelection) ui.selEnd else before.length
        if (to <= from || tracks.isEmpty()) return
        work.value = Work(R.string.ae_effect_progress_title, label, -1f)
        workJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    RenderRange.apply(before, store.dir(id), provider, tracks, from, to, effect, workContext(before.sampleRate, R.string.ae_effect_progress_title, label))
                }
                // Si le projet a bougé pendant le rendu, on abandonne : les sources en trop seront ramassées à la fermeture.
                if (result.newSources.isNotEmpty() && session.project === before) edit("effect:${def.id}") { result.project }
            } catch (e: CancellationException) {
                // Annulé : RenderRange a effacé son fichier, le projet n'a pas bougé.
            } catch (e: Throwable) {
                messages.tryEmit(R.string.ae_effect_failed)
            } finally {
                work.value = null
                workJob = null
            }
        }
    }

    enum class PreviewState { IDLE, RENDERING, PLAYING }

    private val previewPlayer = PreviewPlayer({ rate -> AndroidAudioSink(rate) }, { AndroidAudioSink.raiseThreadPriority() })
    private var previewJob: Job? = null
    private var previewToken = 0
    private var previewCallback: ((PreviewState) -> Unit)? = null

    /**
     * Prépare puis joue les premières secondes de ce que donnerait [effect] (sélection, sinon du curseur à la fin).
     * [onState] suit RENDERING → PLAYING → IDLE sur le fil principal.
     */
    fun previewEffect(effect: Effect, onState: (PreviewState) -> Unit) {
        if (state.value != State.READY) return
        stopPreview()
        pause()
        val p = session.project
        val tracks = targetTracks()
        val from = if (ui.hasSelection) ui.selStart else ui.cursor
        val to = if (ui.hasSelection) ui.selEnd else p.length
        if (to <= from || tracks.isEmpty()) { onState(PreviewState.IDLE); return }
        val token = ++previewToken
        previewCallback = onState
        onState(PreviewState.RENDERING)
        previewJob = viewModelScope.launch {
            val buffer = try {
                withContext(Dispatchers.Default) {
                    val job = coroutineContext[Job]!!
                    EffectPreview.render(p, provider, tracks, from, to, effect, RunContext(p.sampleRate, isCancelled = { !job.isActive }))
                }
            } catch (e: CancellationException) {
                return@launch
            } catch (e: Throwable) {
                null
            }
            if (token != previewToken) return@launch
            if (buffer == null) { endPreview(token); return@launch }
            previewCallback?.invoke(PreviewState.PLAYING)
            previewPlayer.play(buffer) { main.post { endPreview(token) } }
        }
    }

    private fun endPreview(token: Int) {
        if (token != previewToken) return
        val cb = previewCallback
        previewCallback = null
        cb?.invoke(PreviewState.IDLE)
    }

    fun stopPreview() {
        previewToken++
        previewJob?.cancel()
        previewJob = null
        previewPlayer.stop()
        val cb = previewCallback
        previewCallback = null
        cb?.invoke(PreviewState.IDLE)
    }

    /** Le bruit à retrancher, mesuré sur un passage « de silence » sélectionné ; il reste en mémoire d'un effet à l'autre. */
    var noiseProfile: NoiseProfile? = null
        private set

    fun captureNoiseProfile(onDone: (Boolean) -> Unit) {
        if (state.value != State.READY || !ui.hasSelection) { onDone(false); return }
        val p = session.project
        val track = targetTracks().firstOrNull()
        if (track == null) { onDone(false); return }
        stopPreview()
        pause()
        val from = ui.selStart
        val to = ui.selEnd
        viewModelScope.launch {
            val profile = withContext(Dispatchers.Default) {
                runCatching { NoiseProfile.fromReader(TrackRangeReader(p, track, from, to, provider)) }.getOrNull()
            }
            noiseProfile = profile ?: noiseProfile
            onDone(profile != null)
        }
    }

    /** Pose le signal généré à la place du curseur (ou de la sélection), sur la piste choisie si elle est libre à cet endroit, sinon sur une nouvelle piste. */
    fun generate(def: GeneratorDef, v: Values, label: String) {
        if (state.value != State.READY || work.value != null) return
        stopPreview()
        pause()
        val id = projectId ?: return
        val before = session.project
        val rate = before.sampleRate
        val at = if (ui.hasSelection) ui.selStart else ui.cursor
        val frames = (v.f("duration") * rate).toLong().coerceAtLeast(1)
        if (def.insertsSilence) {
            edit("generate:silence") { it.insertSilence(targetTracks(), at, frames) }
            return
        }
        val reader = def.build(v, rate, frames) ?: return
        val onto = ui.selectedTracks.singleOrNull()?.let { before.track(it) }
            ?.takeIf { t -> !t.locked && t.clips.none { it.end > at && it.start < at + frames } }?.id
        val trackName = getApplication<Application>().getString(R.string.ae_track_n, before.tracks.size + 1)
        work.value = Work(R.string.ae_generate_progress_title, label, -1f)
        workJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    GenerateSource.apply(before, store.dir(id), reader, onto, at, label, trackName, workContext(rate, R.string.ae_generate_progress_title, label))
                }
                if (result.newSources.isNotEmpty() && session.project === before) {
                    edit("generate:${def.id}") { result.project }
                    val created = result.project.tracks.firstOrNull { t -> before.tracks.none { it.id == t.id } }
                    if (created != null) {
                        ui.selectedTracks.clear()
                        ui.selectedTracks.add(created.id)
                        touch()
                    }
                }
            } catch (e: CancellationException) {
                // Annulé : GenerateSource a effacé son fichier.
            } catch (e: Throwable) {
                messages.tryEmit(R.string.ae_effect_failed)
            } finally {
                work.value = null
                workJob = null
            }
        }
    }

    /** La demande d'export en attente du choix de la destination : elle survit à la rotation de l'écran. */
    var pendingExport: ExportRequest? = null

    // ---- Enveloppe de volume -------------------------------------------------------------------------

    fun addEnvelopePoint(trackId: Int, frame: Long, gain: Float) {
        edit("envelope_add") { it.addEnvelopePoint(trackId, frame, gain).first }
    }

    fun removeEnvelopePoint(trackId: Int, index: Int) {
        edit("envelope_remove") { it.removeEnvelopePoint(trackId, index) }
    }

    fun clearEnvelope(trackId: Int) {
        edit("envelope_clear") { it.clearEnvelope(trackId) }
    }

    // ---- Affichage en spectrogramme ------------------------------------------------------------------

    fun toggleSpectrogram(trackId: Int, on: Boolean) {
        if (on) ui.spectrogramTracks.add(trackId) else ui.spectrogramTracks.remove(trackId)
        scheduleSave()
        touch()
    }

    private val spectroCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > SPECTRO_TILES
    }
    private val spectroLoading = HashSet<String>()

    /**
     * La tuile [index] du spectrogramme d'une source, ou `null` tant qu'elle se cuit (l'écran se redessine à son arrivée).
     * Cuite en coordonnées du monde (une source, un temps) : défiler ou zoomer ne la recalcule jamais.
     */
    fun spectrogramTile(src: Source, index: Int): Bitmap? {
        val key = "${src.id}#$index"
        spectroCache[key]?.let { return it }
        if (spectroLoading.size < SPECTRO_PARALLEL && spectroLoading.add(key)) {
            val rate = project.sampleRate
            viewModelScope.launch {
                val bmp = withContext(Dispatchers.Default) {
                    runCatching {
                        val first = index.toLong() * Spectrogram.TILE_FRAMES - Spectrogram.FFT_SIZE / 2
                        val count = Spectrogram.TILE_FRAMES + Spectrogram.FFT_SIZE
                        val ch = Array(src.channels.coerceAtLeast(1)) { FloatArray(count) }
                        provider.read(src, first, count, ch, 0)
                        val mono = FloatArray(count) { i -> var s = 0f; for (c in ch.indices) s += ch[c][i]; s / ch.size }
                        val pixels = Spectrogram.toPixels(Spectrogram.bake(mono, Spectrogram.Layout(rate)))
                        // Créé modifiable puis rempli : un bitmap tiré d'un tableau d'entiers serait immuable.
                        Bitmap.createBitmap(Spectrogram.TILE_COLS, Spectrogram.ROWS, Bitmap.Config.ARGB_8888).also {
                            it.setPixels(pixels, 0, Spectrogram.TILE_COLS, 0, 0, Spectrogram.TILE_COLS, Spectrogram.ROWS)
                        }
                    }.getOrNull()
                }
                if (bmp != null) spectroCache[key] = bmp
                spectroLoading.remove(key)
                touch()
            }
        }
        return null
    }

    // ---- Mixer les pistes en une ---------------------------------------------------------------------

    fun mixDown(name: String) {
        if (state.value != State.READY || work.value != null || isRecording) return
        stopPreview()
        pause()
        val id = projectId ?: return
        val before = session.project
        val tracks = targetTracks().filter { t -> before.track(t)?.clips?.isNotEmpty() == true }
        if (tracks.size < 2) return
        work.value = Work(R.string.ae_mixdown_progress_title, name, -1f)
        workJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    MixDown.apply(before, store.dir(id), provider, tracks, name, workContext(before.sampleRate, R.string.ae_mixdown_progress_title, name))
                }
                if (result.newSources.isNotEmpty() && session.project === before) {
                    edit("mixdown") { result.project }
                    val created = result.project.tracks.firstOrNull { t -> before.tracks.none { it.id == t.id } }
                    if (created != null) { ui.selectedTracks.clear(); ui.selectedTracks.add(created.id); touch() }
                }
            } catch (e: CancellationException) {
                // Annulé : MixDown a effacé son fichier.
            } catch (e: Throwable) {
                messages.tryEmit(R.string.ae_effect_failed)
            } finally {
                work.value = null
                workJob = null
            }
        }
    }

    // ---- Analyse -------------------------------------------------------------------------------------

    /** Ce que mesure l'analyse sur `[from, to)` : niveaux et saturations, et le spectre moyen. */
    class AnalysisResult(val from: Long, val to: Long, val sampleRate: Int, val whole: Boolean, val stats: SignalStats, val spectrum: FloatArray)

    /** Analyse la sélection, sinon tout le projet, tel qu'on l'entend. [onDone] reçoit `null` si rien à analyser ou en cas d'échec. */
    fun analyze(onDone: (AnalysisResult?) -> Unit): Job? {
        if (state.value != State.READY || isRecording) { onDone(null); return null }
        val p = session.project
        val whole = !ui.hasSelection
        val from = if (whole) 0L else ui.selStart
        val to = if (whole) p.length else ui.selEnd
        if (to <= from) { onDone(null); return null }
        return viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.Default) {
                    val job = coroutineContext[Job]!!
                    val ctx = RunContext(p.sampleRate, isCancelled = { !job.isActive })
                    val stats = Analysis.stats(MixReader(p, provider, from, to), ctx)
                    val spectrum = Analysis.spectrum(MixReader(p, provider, from, to), ctx)
                    AnalysisResult(from, to, p.sampleRate, whole, stats, spectrum)
                }
            } catch (e: CancellationException) {
                return@launch
            } catch (e: Throwable) {
                null
            }
            onDone(result)
        }
    }

    // ---- Repères : texte et saturations --------------------------------------------------------------

    /** Un repère à chacune de ces trames, en un seul pas d'annulation ; [name] reçoit le rang (1, 2…). */
    fun addMarkers(frames: List<Long>, name: (Int) -> String) {
        edit("markers_add") { p -> frames.withIndex().fold(p) { acc, (i, f) -> acc.addMarker(f, f, name(i + 1)).first } }
    }

    fun exportMarkers(dest: Uri) {
        val text = MarkerText.format(project.markers, project.sampleRate)
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                app.contentResolver.openOutputStream(dest, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("flux illisible")
            }.isSuccess
            if (!ok) messages.tryEmit(R.string.ae_export_failed)
        }
    }

    fun importMarkers(src: Uri) {
        val app = getApplication<Application>()
        val rate = project.sampleRate
        viewModelScope.launch {
            val labels = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(src)?.use { input ->
                        // Un fichier d'étiquettes pèse quelques ko : on s'arrête à 2 Mo plutôt que de tout lire.
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (out.size() < 2_000_000) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                        }
                        MarkerText.parse(out.toString("UTF-8")).take(5000)
                    }
                }.getOrNull()
            }
            if (labels == null) { messages.tryEmit(R.string.ae_export_failed); return@launch }
            if (labels.isEmpty()) return@launch
            edit("markers_import") { p ->
                labels.fold(p) { acc, l -> acc.addMarker((l.startSec * rate).toLong(), (l.endSec * rate).toLong(), l.name).first }
            }
        }
    }

    // ---- Export --------------------------------------------------------------------------------------

    /**
     * Mixe le projet et l'écrit à l'endroit choisi : [dest] est le fichier à créer, ou le dossier si [ExportRequest.perTrack].
     * Le rendu passe par le cache (le sélecteur de fichiers ne donne qu'un flux), qui est vidé à la fin quoi qu'il arrive.
     */
    fun exportAudio(req: ExportRequest, dest: Uri) {
        if (state.value != State.READY || work.value != null || isRecording) return
        stopPreview()
        pause()
        val p = session.project
        val app = getApplication<Application>()
        val label = name
        work.value = Work(R.string.ae_export_progress_title, label, -1f)
        workJob = viewModelScope.launch {
            var clipped = false
            var done = false
            val temp = File(File(app.cacheDir, "audio_exports"), "job_" + System.currentTimeMillis()).also { it.mkdirs() }
            try {
                withContext(Dispatchers.IO) {
                    val ctx = workContext(p.sampleRate, R.string.ae_export_progress_title, label)
                    val exporter = AudioExporter(provider, FfmpegTranscoder)
                    val mime = req.options.format.mime
                    val ext = req.options.format.extension
                    if (req.perTrack) {
                        val results = exporter.exportTracks(p, req.options, temp, temp, ctx)
                        val tree = DocumentFile.fromTreeUri(app, dest) ?: throw IOException("Dossier de destination illisible")
                        for (r in results) {
                            ctx.checkCancelled()
                            val doc = tree.createFile(mime, r.file.name) ?: throw IOException("Création impossible : ${r.file.name}")
                            copyTo(app, r.file, doc.uri)
                            if (r.clipped) clipped = true
                        }
                    } else {
                        val r = exporter.export(p, req.options, File(temp, "export.$ext"), temp, ctx)
                        copyTo(app, r.file, dest)
                        clipped = r.clipped
                    }
                }
                done = true
            } catch (e: CancellationException) {
                // Annulé : ce qui était déjà copié reste, le reste n'existe pas.
            } catch (e: Throwable) {
                messages.tryEmit(R.string.ae_export_failed)
            } finally {
                temp.deleteRecursively()
                work.value = null
                workJob = null
            }
            if (done) messages.tryEmit(if (clipped) R.string.ae_export_audio_clipped else R.string.ae_export_audio_done)
        }
    }

    private fun copyTo(app: Application, file: File, dest: Uri) {
        val out = app.contentResolver.openOutputStream(dest, "wt") ?: throw IOException("Destination illisible")
        out.use { o -> file.inputStream().use { it.copyTo(o) } }
    }

    // ---- Focus audio ---------------------------------------------------------------------------------

    private fun requestFocus(): Boolean {
        val am = audioManager ?: return true
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change ->
                // Un appel ou une autre appli qui joue : on se met en pause plutôt que de se superposer.
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) main.post { pause() }
            }
            .build().also { focusRequest = it }
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val req = focusRequest ?: return
        audioManager?.abandonAudioFocusRequest(req)
    }

    private companion object {
        const val SAVE_DELAY_MS = 1500L
        /** Fenêtres d'échantillons bruts gardées (une par source, les plus récentes) et taille maximale d'une fenêtre, en trames. */
        const val RAW_WINDOWS = 4
        const val RAW_MAX_FRAMES = 2_000_000L
        /** Tuiles de spectrogramme gardées (≈ 260 Ko chacune) et nombre de tuiles cuites en même temps. */
        const val SPECTRO_TILES = 64
        const val SPECTRO_PARALLEL = 3
    }
}
