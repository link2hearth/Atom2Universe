package com.Atom2Universe.app.audioeditor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.LruCache
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audioeditor.core.Clip
import com.Atom2Universe.app.audioeditor.core.ENVELOPE_MAX_GAIN
import com.Atom2Universe.app.audioeditor.core.PeakData
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.ui.EditorViewModel.RawWindow
import com.Atom2Universe.app.audioeditor.core.Track
import com.Atom2Universe.app.audioeditor.core.moveClip
import com.Atom2Universe.app.audioeditor.core.moveEnvelopePoint
import com.Atom2Universe.app.audioeditor.core.setClipFades
import com.Atom2Universe.app.audioeditor.core.trimClipLeft
import com.Atom2Universe.app.audioeditor.core.trimClipRight
import com.Atom2Universe.app.pixelart.ui.accentColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * La chronologie : règle, pistes (en-têtes épinglés à gauche), clips avec leur forme d'onde, sélection et
 * curseur, dans **une seule vue**.
 *
 * Elle ne possède aucun état : tout vit dans [EditorViewModel] (le projet immuable et [EditorUi]). Elle lit
 * cet état à chaque dessin et appelle les méthodes du modèle quand l'utilisateur agit.
 *
 * Gestes : toucher = placer le curseur et choisir la piste ; appui long puis glisser = sélectionner une plage ;
 * glisser près d'un bord de la sélection = l'ajuster ; glisser = défiler (avec inertie) ; pincer = zoomer ;
 * double toucher sur un clip = sélectionner ce clip. Un clip se désigne par son *dessus* : quand deux clips
 * se recouvrent, le dernier de la liste de la piste gagne.
 */
class TimelineView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var model: EditorViewModel? = null

    /** Le ViewModel, seulement une fois le projet chargé : avant, il n'y a ni projet ni session (la mise en page arrive avant la fin du chargement). */
    private val vm: EditorViewModel? get() = model?.takeIf { it.state.value == EditorViewModel.State.READY }

    /** Appelé quand on touche le menu ⋮ d'un en-tête de piste. */
    var onTrackMenu: ((trackId: Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun dp(v: Int) = v * density

    private val headerW = dp(104)
    private val rulerH = dp(28)
    private val trackH = dp(112)
    private val bottomSpace = dp(72)

    // ---- Peintures ----------------------------------------------------------------------------

    private val cBackground = ContextCompat.getColor(context, R.color.audio_background)
    private val cSurface = ContextCompat.getColor(context, R.color.audio_surface)
    private val cRaised = ContextCompat.getColor(context, R.color.audio_surface_raised)
    private val cText = ContextCompat.getColor(context, R.color.audio_text_primary)
    private val cTextDim = ContextCompat.getColor(context, R.color.audio_text_secondary)
    private val cTextFaint = ContextCompat.getColor(context, R.color.audio_text_tertiary)
    private val cOutline = ContextCompat.getColor(context, R.color.audio_outline)
    private val cAccent = context.accentColor()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val wave = Paint().apply { strokeWidth = 1f; isAntiAlias = false }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val tri = Path()
    private val rect = RectF()
    private val rect2 = RectF()

    // Tampons réutilisés à chaque dessin : pas d'allocation dans onDraw.
    private var mins = FloatArray(0)
    private var maxs = FloatArray(0)
    private var mins2 = FloatArray(0)
    private var maxs2 = FloatArray(0)
    private var lines = FloatArray(0)

    private val labels = LruCache<Long, String>(96)

    // ---- Gestes -------------------------------------------------------------------------------

    private enum class Drag { NONE, SELECT, EDGE, SEL_MOVE, CURSOR, ENV_POINT, CLIP_MOVE, CLIP_TRIM_L, CLIP_TRIM_R, FADE_IN, FADE_OUT }

    private var drag = Drag.NONE
    private var anchor = 0L          // le bout fixe de la sélection qu'on est en train de tirer
    private var lastX = 0f
    private var lastY = 0f
    private var dragClip: Clip? = null      // le clip tel qu'il était au début du geste
    private var dragTrackId = -1            // la piste d'où le clip est parti
    private var grab = 0L                   // décalage entre la trame touchée et le début du clip (ou de la sélection)
    private var selLen = 0L                 // longueur de la sélection qu'on déplace
    private var envTrack = -1               // le point d'enveloppe qu'on tient : sa piste, son rang
    private var envIndex = -1
    private var envMoved = false            // le doigt a-t-il vraiment bougé (sinon, un appui long le supprime) ?
    private var envDownX = 0f
    private var envDownY = 0f
    private var axis = 0             // 0 = pas encore décidé, 1 = horizontal, 2 = vertical
    private val scroller = OverScroller(context)
    private var flingStartFpp = 1.0

    private val autoScroll = object : Runnable {
        override fun run() {
            if (drag == Drag.NONE) return
            val edge = dp(44)
            val over = when {
                lastX > width - edge -> (lastX - (width - edge)) / edge
                lastX < headerW + edge -> -((headerW + edge) - lastX) / edge
                else -> 0f
            }
            if (over != 0f) {
                val ui = vm?.ui ?: return
                val step = (over * dp(14) * ui.framesPerPixel).toLong()
                ui.scrollFrame = clampScroll(ui.scrollFrame + step)
                continueDrag(lastX, lastY)
                postOnAnimation(this)
            }
        }
    }

    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
            scroller.forceFinished(true)
            return vm?.state?.value == EditorViewModel.State.READY
        }

        override fun onScale(d: ScaleGestureDetector): Boolean {
            zoomAround(d.focusX, 1.0 / d.scaleFactor)
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true)
            axis = 0
            drag = Drag.NONE
            val v = vm ?: return true
            // La poignée de la sélection (la bande en haut) : on la glisse en entier, sans toucher à sa longueur.
            // La poignée ronde en bas du curseur : on la glisse pour placer le curseur (ou la tête de lecture) au doigt.
            if (!v.isRecording && cursorGrabAt(v, e.x, e.y)) {
                drag = Drag.CURSOR
                lastX = e.x
                lastY = e.y
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                parent?.requestDisallowInterceptTouchEvent(true)
                grab = frameAt(e.x) - v.playheadFrame()
                return true
            }
            if (!v.isRecording && selectionGrabAt(v, e.x, e.y)) {
                drag = Drag.SEL_MOVE
                grab = frameAt(e.x) - v.ui.selStart
                selLen = v.ui.selEnd - v.ui.selStart
                lastX = e.x
                lastY = e.y
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            if (v.ui.tool == Tool.ENVELOPE && e.x > headerW && e.y > rulerH) {
                val i = trackIndexAt(e.y)
                if (i >= 0) {
                    val t = v.project.tracks[i]
                    val k = if (t.locked) -1 else envPointAt(t, i, e.x, e.y)
                    if (k >= 0) {
                        drag = Drag.ENV_POINT
                        envTrack = t.id
                        envIndex = k
                        envMoved = false
                        envDownX = e.x
                        envDownY = e.y
                        lastX = e.x
                        lastY = e.y
                        v.beginGesture()
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
            if (v.ui.tool != Tool.ENVELOPE && e.x > headerW && e.y > rulerH && startClipDrag(v, e.x, e.y)) {
                lastX = e.x
                lastY = e.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            if (e.x > headerW && e.y > rulerH && v.ui.hasSelection) {
                val a = xOf(v.ui.selStart)
                val b = xOf(v.ui.selEnd)
                val slop = dp(24)
                val da = abs(e.x - a)
                val db = abs(e.x - b)
                if (min(da, db) <= slop) {
                    drag = Drag.EDGE
                    anchor = if (da <= db) v.ui.selEnd else v.ui.selStart
                    lastX = e.x
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Une poignée saisie puis relâchée sur place n'est pas un toucher : le curseur ne bouge pas.
            if (drag != Drag.NONE) return true
            tap(e.x, e.y)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            doubleTap(e.x, e.y)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val v = vm ?: return
            if (drag == Drag.ENV_POINT && !envMoved) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                v.cancelGesture()
                v.removeEnvelopePoint(envTrack, envIndex)
                drag = Drag.NONE
                return
            }
            if (e.x <= headerW || e.y <= rulerH || drag != Drag.NONE) return
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            drag = Drag.SELECT
            lastX = e.x
            anchor = snapAt(e.x)
            v.setSelection(anchor, anchor)
            parent?.requestDisallowInterceptTouchEvent(true)
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (drag != Drag.NONE || scale.isInProgress) return true
            val v = vm ?: return true
            if (axis == 0) axis = if (abs(dx) >= abs(dy)) 1 else 2
            val inHeader = e1 != null && e1.x < headerW
            if (axis == 1 && !inHeader) {
                v.ui.scrollFrame = clampScroll(v.ui.scrollFrame + (dx * v.ui.framesPerPixel).toLong())
            } else {
                v.ui.scrollY = clampScrollY(v.ui.scrollY + dy.toInt())
            }
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (drag != Drag.NONE || scale.isInProgress) return true
            val v = vm ?: return true
            flingStartFpp = v.ui.framesPerPixel
            if (axis == 1 && (e1 == null || e1.x >= headerW)) {
                val startX = (v.ui.scrollFrame / flingStartFpp).toInt()
                val maxX = (maxScrollFrame() / flingStartFpp).toInt()
                scroller.fling(startX, 0, (-vx).toInt(), 0, 0, max(0, maxX), 0, 0)
            } else if (axis == 2) {
                scroller.fling(0, v.ui.scrollY, 0, (-vy).toInt(), 0, 0, 0, maxScrollY())
            }
            postInvalidateOnAnimation()
            return true
        }
    }).apply { setIsLongpressEnabled(true) }

    fun bind(model: EditorViewModel) {
        this.model = model
        invalidate()
    }

    // ---- Géométrie ----------------------------------------------------------------------------

    private fun fpp() = vm?.ui?.framesPerPixel?.takeIf { it > 0 } ?: 1.0

    private fun xOf(frame: Long): Float {
        val ui = vm?.ui ?: return headerW
        return headerW + ((frame - ui.scrollFrame) / fpp()).toFloat()
    }

    private fun frameAt(x: Float): Long {
        val ui = vm?.ui ?: return 0
        return max(0L, ui.scrollFrame + ((x - headerW) * fpp()).toLong())
    }

    private fun snapAt(x: Float): Long = vm?.snapped(frameAt(x), dp(10) * fpp()) ?: frameAt(x)

    private fun trackTop(index: Int): Float = rulerH + index * trackH - (vm?.ui?.scrollY ?: 0)

    private fun trackIndexAt(y: Float): Int {
        val v = vm ?: return -1
        val i = ((y - rulerH + v.ui.scrollY) / trackH).toInt()
        return if (y >= rulerH && i in v.project.tracks.indices) i else -1
    }

    private fun contentHeight() = rulerH + (vm?.project?.tracks?.size ?: 0) * trackH + bottomSpace

    private fun maxScrollY() = max(0, (contentHeight() - height).toInt())
    private fun clampScrollY(y: Int) = y.coerceIn(0, maxScrollY())

    private fun visibleFrames() = ((width - headerW) * fpp()).toLong()

    private fun maxScrollFrame(): Long {
        val v = vm ?: return 0
        return max(0L, v.project.length + visibleFrames() / 2 - visibleFrames() / 4)
    }

    private fun clampScroll(f: Long) = f.coerceIn(0L, maxScrollFrame())

    /** Le clip visible sous [frame] sur la piste : le dernier de la liste (celui qui est dessiné par-dessus). */
    private fun clipAt(track: Track, frame: Long): Clip? = track.clips.lastOrNull { frame >= it.start && frame < it.end }

    // ---- Zoom ---------------------------------------------------------------------------------

    private fun ensureZoom() {
        val v = vm ?: return
        val w = width - headerW
        if (w <= 0) return
        val rate = v.project.sampleRate
        if (v.ui.framesPerPixel <= 0) v.ui.framesPerPixel = TimelineMath.fitFramesPerPixel(v.project.length, w.toInt(), rate)
        v.ui.framesPerPixel = v.ui.framesPerPixel.coerceIn(
            TimelineMath.minFramesPerPixel(),
            TimelineMath.maxFramesPerPixel(w.toInt(), rate, v.project.length),
        )
    }

    /** Multiplie le nombre de trames par pixel par [factor] (< 1 = zoom avant) en gardant fixe la trame sous le pixel [focusX]. */
    private fun zoomAround(focusX: Float, factor: Double) {
        val v = vm ?: return
        val old = v.ui.framesPerPixel
        val w = (width - headerW).toInt()
        val rate = v.project.sampleRate
        val next = (old * factor).coerceIn(TimelineMath.minFramesPerPixel(), TimelineMath.maxFramesPerPixel(w, rate, v.project.length))
        if (next == old) return
        val focusFrame = v.ui.scrollFrame + (focusX - headerW) * old
        v.ui.framesPerPixel = next
        v.ui.scrollFrame = clampScroll(TimelineMath.scrollForZoom(focusFrame, (focusX - headerW).toDouble(), next))
        v.touch()
        invalidate()
    }

    fun zoomIn() = zoomAround(headerW + (width - headerW) / 2f, 0.5)
    fun zoomOut() = zoomAround(headerW + (width - headerW) / 2f, 2.0)

    /** Tout le projet dans la largeur. */
    fun zoomFit() {
        val v = vm ?: return
        v.ui.framesPerPixel = 0.0
        v.ui.scrollFrame = 0
        ensureZoom()
        v.touch()
        invalidate()
    }

    /** La sélection (si elle existe) remplit la largeur. */
    fun zoomToSelection() {
        val v = vm ?: return
        if (!v.ui.hasSelection) return
        val w = (width - headerW).toDouble()
        val rate = v.project.sampleRate
        val span = (v.ui.selEnd - v.ui.selStart) * 1.1
        v.ui.framesPerPixel = (span / w).coerceIn(TimelineMath.minFramesPerPixel(), TimelineMath.maxFramesPerPixel(w.toInt(), rate, v.project.length))
        v.ui.scrollFrame = clampScroll(v.ui.selStart - (span * 0.05).toLong())
        v.touch()
        invalidate()
    }

    // ---- Touchers -----------------------------------------------------------------------------

    private fun tap(x: Float, y: Float) {
        val v = vm ?: return
        if (y < rulerH) {
            if (x <= headerW) return
            // Un repère touché : le curseur (ou sa zone) s'y place.
            val hit = v.project.markers.minByOrNull { abs(xOf(it.pos) - x) }
            if (hit != null && abs(xOf(hit.pos) - x) <= dp(14)) { v.goToMarker(hit.id); return }
            v.setCursor(snapAt(x))
            v.clearSelection()
            return
        }
        val i = trackIndexAt(y)
        if (x < headerW) {
            if (i >= 0) headerTap(v.project.tracks[i], x, y - trackTop(i))
            return
        }
        if (v.ui.tool == Tool.ENVELOPE && i >= 0) {
            val t = v.project.tracks[i]
            v.selectOnlyTrack(t.id)
            if (!t.locked && envPointAt(t, i, x, y) < 0) v.addEnvelopePoint(t.id, snapAt(x), envGainAt(i, y))
            return
        }
        if (i >= 0) {
            val t = v.project.tracks[i]
            if (t.id !in v.ui.selectedTracks) v.selectOnlyTrack(t.id)
            v.ui.selectedClip = clipAt(t, frameAt(x))?.id ?: -1
        }
        v.setCursor(snapAt(x))
        v.clearSelection()
    }

    private fun doubleTap(x: Float, y: Float) {
        val v = vm ?: return
        if (x <= headerW) return
        val i = trackIndexAt(y)
        if (i < 0) return
        val t = v.project.tracks[i]
        val c = clipAt(t, frameAt(x)) ?: return
        v.selectOnlyTrack(t.id)
        v.ui.selectedClip = c.id
        v.setSelection(c.start, c.end)
    }

    /** La poignée du curseur : un rond en bas du trait, de la couleur du curseur ; on l'attrape à 26 dp près. */
    private fun cursorGrabAt(v: EditorViewModel, x: Float, y: Float): Boolean {
        if (x <= headerW || y < height - dp(46)) return false
        return abs(x - xOf(v.playheadFrame())) <= dp(26)
    }

    private fun drawCursorGrip(canvas: Canvas, px: Float, playing: Boolean) {
        val cy = height - dp(24)
        fill.color = if (playing) cText else cAccent
        canvas.drawCircle(px, cy, dp(12f), fill)
        stroke.color = cBackground
        stroke.strokeWidth = dp(1.6f)
        for (k in intArrayOf(-1, 1)) canvas.drawLine(px + k * dp(3.5f), cy - dp(4.5f), px + k * dp(3.5f), cy + dp(4.5f), stroke)
    }

    /** La bande de la sélection : juste sous la règle, sur toute sa largeur (sauf un peu près des bords, réservés à l'ajustement). */
    private fun selectionGrabAt(v: EditorViewModel, x: Float, y: Float): Boolean {
        if (!v.ui.hasSelection || x <= headerW || y < rulerH || y > rulerH + dp(28)) return false
        val a = xOf(v.ui.selStart)
        val b = xOf(v.ui.selEnd)
        val edge = min(dp(12), (b - a) / 4)
        return x >= a + edge && x <= b - edge
    }

    private fun extendSelection(x: Float) {
        val v = vm ?: return
        v.setSelection(anchor, snapAt(x))
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val v = vm
        if (v == null || v.state.value != EditorViewModel.State.READY) return true
        scale.onTouchEvent(e)
        if (scale.isInProgress) {
            endClipDrag(commit = false)
            drag = Drag.NONE
            return true
        }
        gestures.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (drag != Drag.NONE) {
                lastX = e.x
                lastY = e.y
                continueDrag(e.x, e.y)
                removeCallbacks(autoScroll)
                postOnAnimation(autoScroll)
            }
            MotionEvent.ACTION_UP -> {
                if (drag != Drag.NONE) removeCallbacks(autoScroll)
                endClipDrag(commit = true)
                drag = Drag.NONE
            }
            MotionEvent.ACTION_CANCEL -> {
                if (drag != Drag.NONE) removeCallbacks(autoScroll)
                endClipDrag(commit = false)
                drag = Drag.NONE
            }
        }
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val v = vm ?: return
            if (axis == 1) v.ui.scrollFrame = clampScroll((scroller.currX * flingStartFpp).toLong())
            else v.ui.scrollY = clampScrollY(scroller.currY)
            postInvalidateOnAnimation()
        }
    }

    // ---- Déplacer, rogner, fondus ----------------------------------------------------------------------

    /** Où se trouve, dans le haut du clip, la poignée de fondu d'entrée / de sortie du clip sélectionné. */
    private fun fadeHandleX(c: Clip, out: Boolean): Float = xOf(if (out) c.end - c.fadeOut.len else c.start + c.fadeIn.len)

    /**
     * Regarde si le doigt saisit une poignée du clip **choisi** (un clip se choisit en le touchant) : un bout de fondu (rond en
     * haut), un bord (languette : rogner), ou la barre du bas (déplacer, d'une piste à l'autre). Démarre le geste et rend
     * `true` si c'est le cas ; sinon le toucher reste un défilement ou un appui normal.
     */
    private fun startClipDrag(v: EditorViewModel, x: Float, y: Float): Boolean {
        val sel = v.project.findClip(v.ui.selectedClip) ?: return false
        val track = sel.first
        val c = sel.second
        if (track.locked) return false
        val i = v.project.indexOfTrack(track.id)
        if (i < 0 || trackIndexAt(y) != i) return false
        val top = trackTop(i) + dp(5)
        val bottom = trackTop(i) + trackH - dp(5)
        val x0 = xOf(c.start)
        val x1 = xOf(c.end)

        // 1. Les ronds de fondu, en haut du clip.
        val hy = top + dp(11)
        val slop = dp(18)
        if (abs(y - hy) <= slop) {
            val dIn = abs(x - fadeHandleX(c, false))
            val dOut = abs(x - fadeHandleX(c, true))
            if (min(dIn, dOut) <= slop) {
                begin(v, if (dIn <= dOut) Drag.FADE_IN else Drag.FADE_OUT, c, track.id, 0)
                return true
            }
        }

        val frame = frameAt(x)

        // 2. Les languettes des bords : la plus proche, à 24 dp près (un petit clip a ses deux languettes l'une contre l'autre).
        //    Le doigt garde l'écart qu'il avait avec le bord au moment de saisir : rien ne saute.
        val d0 = abs(x - x0)
        val d1 = abs(x - x1)
        if (min(d0, d1) <= dp(24) && y > top + dp(24)) {
            if (d0 <= d1) begin(v, Drag.CLIP_TRIM_L, c, track.id, frame - c.start)
            else begin(v, Drag.CLIP_TRIM_R, c, track.id, frame - c.end)
            return true
        }

        // 3. La barre du bas : déplacer le clip entier.
        if (y >= bottom - dp(28) && x >= x0 && x <= x1) {
            begin(v, Drag.CLIP_MOVE, c, track.id, frame - c.start)
            return true
        }
        return false
    }

    private fun begin(v: EditorViewModel, mode: Drag, c: Clip, trackId: Int, grabOffset: Long) {
        drag = mode
        dragClip = c
        dragTrackId = trackId
        grab = grabOffset
        v.ui.selectedClip = c.id
        v.beginGesture()
        v.touch()
    }

    private fun continueDrag(x: Float, y: Float) {
        val v = vm ?: return
        when (drag) {
            Drag.SELECT, Drag.EDGE -> extendSelection(x)
            Drag.CURSOR -> v.setCursor(v.snapped(frameAt(x) - grab, dp(10) * fpp()).coerceAtLeast(0))
            Drag.ENV_POINT -> {
                if (!envMoved && (abs(x - envDownX) > dp(6) || abs(y - envDownY) > dp(6))) envMoved = true
                if (envMoved) {
                    val i = v.project.indexOfTrack(envTrack)
                    if (i >= 0) {
                        val frame = v.snapped(frameAt(x), dp(10) * fpp())
                        val gain = envGainAt(i, y)
                        v.previewGesture { it.moveEnvelopePoint(envTrack, envIndex, frame, gain) }
                    }
                }
            }
            Drag.SEL_MOVE -> {
                val start = v.snappedSelectionMove(frameAt(x) - grab, selLen, dp(10) * fpp()).coerceAtLeast(0)
                v.setSelection(start, start + selLen)
            }
            Drag.CLIP_MOVE -> {
                val c = dragClip ?: return
                val thr = dp(10) * fpp()
                val start = v.snappedMove(c, frameAt(x) - grab, thr).coerceAtLeast(0)
                // Passer sur une autre piste : le clip y suit le doigt (une piste verrouillée refuse).
                val i = trackIndexAt(y)
                val dest = if (i >= 0) v.project.tracks[i].takeIf { !it.locked }?.id ?: dragTrackId else dragTrackId
                v.previewGesture { it.moveClip(c.id, start, dest.takeIf { d -> d != dragTrackId }) }
            }
            Drag.CLIP_TRIM_L -> {
                val c = dragClip ?: return
                val f = v.snapped(frameAt(x) - grab, dp(10) * fpp(), c.id)
                v.previewGesture { it.trimClipLeft(c.id, f) }
            }
            Drag.CLIP_TRIM_R -> {
                val c = dragClip ?: return
                val f = v.snapped(frameAt(x) - grab, dp(10) * fpp(), c.id)
                v.previewGesture { it.trimClipRight(c.id, f) }
            }
            Drag.FADE_IN -> {
                val c = dragClip ?: return
                val len = (frameAt(x) - c.start).coerceIn(0L, c.length - c.fadeOut.len)
                v.previewGesture { it.setClipFades(c.id, len, c.fadeOut.len, shapeOf(c)) }
            }
            Drag.FADE_OUT -> {
                val c = dragClip ?: return
                val len = (c.end - frameAt(x)).coerceIn(0L, c.length - c.fadeIn.len)
                v.previewGesture { it.setClipFades(c.id, c.fadeIn.len, len, shapeOf(c)) }
            }
            Drag.NONE -> Unit
        }
        invalidate()
    }

    private fun shapeOf(c: Clip) = when {
        !c.fadeIn.isNone -> c.fadeIn.shape
        !c.fadeOut.isNone -> c.fadeOut.shape
        else -> c.fadeIn.shape      // sans fondu, la forme choisie dans la feuille du clip attend là
    }

    private fun endClipDrag(commit: Boolean) {
        val v = vm
        val label = when (drag) {
            Drag.CLIP_MOVE -> "move"
            Drag.CLIP_TRIM_L, Drag.CLIP_TRIM_R -> "trim"
            Drag.FADE_IN, Drag.FADE_OUT -> "fade"
            Drag.ENV_POINT -> "envelope"
            else -> return
        }
        dragClip = null
        if (v == null) return
        if (commit) v.commitGesture(label) else v.cancelGesture()
    }

    /** Fait défiler pour que [frame] soit visible (saut vers un repère…). */
    fun reveal(frame: Long) {
        val v = vm ?: return
        val x = xOf(frame)
        if (x < headerW || x > width - dp(24)) {
            v.ui.scrollFrame = clampScroll(frame - visibleFrames() / 5)
            invalidate()
        }
    }

    // ---- En-têtes de piste --------------------------------------------------------------------

    private val btn get() = dp(26)

    private fun muteRect(top: Float, out: RectF) = out.set(dp(10), top + trackH - btn - dp(8), dp(10) + btn, top + trackH - dp(8))
    private fun soloRect(top: Float, out: RectF) = out.set(dp(10) + btn + dp(6), top + trackH - btn - dp(8), dp(10) + 2 * btn + dp(6), top + trackH - dp(8))
    private fun menuRect(top: Float, out: RectF) = out.set(headerW - dp(34), top, headerW, top + dp(36))

    private fun headerTap(t: Track, x: Float, yInTrack: Float) {
        val v = vm ?: return
        val top = 0f
        val r = RectF()
        muteRect(top, r)
        if (r.contains(x, yInTrack)) { v.toggleMute(t.id); return }
        soloRect(top, r)
        if (r.contains(x, yInTrack)) { v.toggleSolo(t.id); return }
        menuRect(top, r)
        if (r.contains(x, yInTrack)) { onTrackMenu?.invoke(t.id); return }
        v.tapTrack(t.id)
    }

    // ---- Dessin -------------------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ensureZoom()
        vm?.ui?.let { it.scrollY = clampScrollY(it.scrollY); it.scrollFrame = clampScroll(it.scrollFrame) }
    }

    override fun onDraw(canvas: Canvas) {
        val v = vm
        canvas.drawColor(cBackground)
        if (v == null || v.state.value != EditorViewModel.State.READY) return
        ensureZoom()
        val ui = v.ui
        val project = v.project
        val playing = v.isPlaying
        val playhead = v.playheadFrame()

        // La lecture suivie : quand la tête sort de l'écran, on tourne la page.
        if (playing && ui.follow && drag == Drag.NONE && scroller.isFinished) {
            val x = xOf(playhead)
            if (x > width - dp(24) || x < headerW) ui.scrollFrame = clampScroll(playhead - visibleFrames() / 10)
        }

        // Pistes
        canvas.save()
        canvas.clipRect(headerW, rulerH, width.toFloat(), height.toFloat())
        for ((i, track) in project.tracks.withIndex()) {
            val top = trackTop(i)
            if (top + trackH < rulerH || top > height) continue
            drawLane(canvas, v, track, i, top)
        }
        drawRecording(canvas, v, project)
        // Sélection
        if (ui.hasSelection) {
            val a = max(xOf(ui.selStart), headerW)
            val b = min(xOf(ui.selEnd), width.toFloat())
            if (b > a) {
                fill.color = withAlpha(cAccent, 0x38)
                canvas.drawRect(a, rulerH, b, height.toFloat(), fill)
                stroke.color = withAlpha(cAccent, 0xCC)
                stroke.strokeWidth = dp(1.5f)
                val xa = xOf(ui.selStart)
                val xb = xOf(ui.selEnd)
                if (xa >= headerW) canvas.drawLine(xa, rulerH, xa, height.toFloat(), stroke)
                if (xb <= width) canvas.drawLine(xb, rulerH, xb, height.toFloat(), stroke)
                drawSelectionGrip(canvas, a, b)
            }
        }
        // Curseur / tête de lecture
        val px = xOf(playhead)
        if (px >= headerW && px <= width) {
            stroke.color = if (playing) cText else cAccent
            stroke.strokeWidth = dp(1.5f)
            canvas.drawLine(px, rulerH, px, height.toFloat(), stroke)
            if (!v.isRecording) drawCursorGrip(canvas, px, playing)
        }
        canvas.restore()

        drawRuler(canvas, v, playhead)
        drawHeaders(canvas, v)
        if (playing || v.isRecording) postInvalidateOnAnimation()
    }

    /** Ce qui s'enregistre : une bande rouge qui s'allonge, sur la piste visée ou sur une piste à venir sous les autres. */
    private fun drawRecording(canvas: Canvas, v: EditorViewModel, project: com.Atom2Universe.app.audioeditor.core.Project) {
        val span = v.recordingSpan() ?: return
        val target = v.recordingTrack()
        val index = project.tracks.indexOfFirst { it.id == target }.let { if (it < 0) project.tracks.size else it }
        val top = trackTop(index)
        val a = max(xOf(span.first), headerW)
        val b = min(xOf(span.last), width.toFloat())
        if (b <= a) return
        val red = 0xFFE53935.toInt()
        fill.color = withAlpha(red, 0x55)
        canvas.drawRect(a, top + dp(5), b, top + trackH - dp(5), fill)
        stroke.color = withAlpha(red, 0xDD)
        stroke.strokeWidth = dp(1.5f)
        canvas.drawRect(a, top + dp(5), b, top + trackH - dp(5), stroke)
    }

    /** La poignée de déplacement : une bande pleine en haut de la sélection, avec un repère de prise au milieu. */
    private fun drawSelectionGrip(canvas: Canvas, a: Float, b: Float) {
        fill.color = withAlpha(cAccent, 0xB0)
        canvas.drawRect(a, rulerH, b, rulerH + dp(16), fill)
        if (b - a < dp(44)) return
        val cx = (a + b) / 2
        stroke.color = withAlpha(cText, 0xE6)
        stroke.strokeWidth = dp(1.5f)
        for (k in -1..1) canvas.drawLine(cx - dp(9), rulerH + dp(8) + k * dp(3), cx + dp(9), rulerH + dp(8) + k * dp(3), stroke)
    }

    private fun drawLane(canvas: Canvas, v: EditorViewModel, track: Track, index: Int, top: Float) {
        val ui = v.ui
        val selected = track.id in ui.selectedTracks
        fill.color = if (selected) cSurface else blend(cBackground, cSurface, 0.55f)
        canvas.drawRect(headerW, top, width.toFloat(), top + trackH, fill)
        stroke.color = cOutline
        stroke.strokeWidth = 1f
        canvas.drawLine(headerW, top + trackH, width.toFloat(), top + trackH, stroke)

        val color = TrackColors.colorFor(track.color, index)
        val dim = track.mute || (v.project.tracks.any { it.solo } && !track.solo)
        val padY = dp(5)
        for (clip in track.clips) {
            val x0 = xOf(clip.start)
            val x1 = xOf(clip.end)
            if (x1 < headerW || x0 > width) continue
            rect.set(x0, top + padY, max(x1, x0 + 1f), top + trackH - padY)
            fill.color = withAlpha(blend(cBackground, color, 0.22f), if (dim) 0x88 else 0xFF)
            canvas.drawRoundRect(rect, dp(4), dp(4), fill)
            if (clip.id == ui.selectedClip) {
                stroke.color = cText
                stroke.strokeWidth = dp(1.5f)
                canvas.drawRoundRect(rect, dp(4), dp(4), stroke)
            }
            val src = v.project.sources[clip.sourceId]
            if (src != null) {
                val alpha = if (dim) 0x77 else 0xFF
                val asSpectro = track.id in ui.spectrogramTracks && drawSpectrogram(canvas, v, clip, src, rect, alpha)
                if (!asSpectro) drawWave(canvas, v, clip, src, v.peaksFor(src), rect, withAlpha(color, alpha))
            }
            drawFades(canvas, clip, x0, x1, rect)
            if (clip.id == ui.selectedClip && ui.tool != Tool.ENVELOPE && !track.locked) drawHandles(canvas, clip, rect)
            if (clip.name.isNotEmpty() && x1 - x0 > dp(40)) {
                text.color = cText
                text.textSize = dp(11)
                text.typeface = Typeface.DEFAULT
                val room = (min(x1, width.toFloat()) - max(x0, headerW) - dp(8)).toInt()
                if (room > 0) {
                    val s = TextUtils.ellipsize(clip.name, text, room.toFloat(), TextUtils.TruncateAt.END)
                    canvas.drawText(s, 0, s.length, max(x0, headerW) + dp(4), rect.top + dp(12), text)
                }
            }
        }
        drawEnvelope(canvas, track, index, ui.tool == Tool.ENVELOPE)
    }

    // ---- Enveloppe de volume ---------------------------------------------------------------------------

    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(5f), dp(5f)), 0f)
    }
    private val envPath = Path()

    /** La zone de la piste où vit la courbe : de gain 0 (en bas) à [ENVELOPE_MAX_GAIN] (en haut). */
    private fun envTop(index: Int) = trackTop(index) + dp(5)
    private fun envBottom(index: Int) = trackTop(index) + trackH - dp(5)
    private fun envY(index: Int, gain: Float): Float =
        envBottom(index) - (gain / ENVELOPE_MAX_GAIN).coerceIn(0f, 1f) * (envBottom(index) - envTop(index))

    /** Le gain sous le doigt ; proche de 1 (0 dB), il se colle à 1. */
    private fun envGainAt(index: Int, y: Float): Float {
        val g = ((envBottom(index) - y) / (envBottom(index) - envTop(index)) * ENVELOPE_MAX_GAIN).coerceIn(0f, ENVELOPE_MAX_GAIN)
        return if (abs(g - 1f) < 0.05f) 1f else g
    }

    /** Le rang du point d'enveloppe de la piste [index] touché en [x], [y] (à 22 dp près), ou −1. */
    private fun envPointAt(track: Track, index: Int, x: Float, y: Float): Int {
        var best = -1
        var bestD = dp(22)
        for ((k, p) in track.envelope.withIndex()) {
            val dx = xOf(p.frame) - x
            val dy = envY(index, p.gain) - y
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d <= bestD) { bestD = d; best = k }
        }
        return best
    }

    /** La courbe de volume d'une piste, dessinée par-dessus ses clips ; avec l'outil Courbe : repère de gain 1 et points. */
    private fun drawEnvelope(canvas: Canvas, track: Track, index: Int, editing: Boolean) {
        val e = track.envelope
        if (e.isEmpty() && !editing) return
        if (editing) {
            dashPaint.color = withAlpha(cText, 0x66)
            dashPaint.strokeWidth = 1f
            val y1 = envY(index, 1f)
            canvas.drawLine(headerW, y1, width.toFloat(), y1, dashPaint)
        }
        if (e.isEmpty()) return
        envPath.reset()
        envPath.moveTo(headerW, envY(index, e.first().gain))
        for (p in e) envPath.lineTo(xOf(p.frame), envY(index, p.gain))
        envPath.lineTo(width.toFloat(), envY(index, e.last().gain))
        stroke.color = withAlpha(0xFFFFD54F.toInt(), if (editing) 0xFF else 0xAA)
        stroke.strokeWidth = dp(2f)
        canvas.drawPath(envPath, stroke)
        if (editing) {
            fill.color = 0xFFFFD54F.toInt()
            for (p in e) {
                val x = xOf(p.frame)
                if (x >= headerW - dp(8) && x <= width + dp(8)) canvas.drawCircle(x, envY(index, p.gain), dp(5f), fill)
            }
        }
    }

    // ---- Spectrogramme -----------------------------------------------------------------------------------

    private val spectroPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val spectroDst = RectF()

    /**
     * Le spectrogramme d'un clip, tuile par tuile (voir [EditorViewModel.spectrogramTile]) : chaque tuile est étirée sur sa
     * place d'après le zoom, sans rien recalculer. Rend `false` quand on est trop dézoomé (trop de tuiles) : la forme d'onde
     * prend alors le relais.
     */
    private fun drawSpectrogram(canvas: Canvas, v: EditorViewModel, clip: Clip, src: Source, r: RectF, alpha: Int): Boolean {
        val left = max(r.left, headerW)
        val right = min(r.right, width.toFloat())
        if (right <= left) return true
        fun sourceOf(t: Long): Long =
            if (!clip.reversed) clip.srcStart + (t - clip.start) else clip.srcStart + clip.length - 1 - (t - clip.start)
        val a = sourceOf(frameAt(left))
        val b = sourceOf(frameAt(right))
        val lo = min(a, b).coerceIn(clip.srcStart, clip.srcStart + clip.length - 1)
        val hi = max(a, b).coerceIn(clip.srcStart, clip.srcStart + clip.length - 1)
        val first = com.Atom2Universe.app.audioeditor.dsp.Spectrogram.tileOf(lo)
        val last = com.Atom2Universe.app.audioeditor.dsp.Spectrogram.tileOf(hi)
        if (last - first >= MAX_SPECTRO_TILES) return false
        spectroPaint.alpha = alpha
        val tileFrames = com.Atom2Universe.app.audioeditor.dsp.Spectrogram.TILE_FRAMES
        canvas.save()
        canvas.clipRect(left, r.top, right, r.bottom)
        // Un clip retourné se lit à l'envers : même dessin, renversé autour du milieu du clip.
        if (clip.reversed) canvas.scale(-1f, 1f, (xOf(clip.start) + xOf(clip.end)) / 2f, 0f)
        for (tile in first..last) {
            val bmp = v.spectrogramTile(src, tile) ?: continue
            val f0 = tile.toLong() * tileFrames
            spectroDst.set(xOf(clip.start + f0 - clip.srcStart), r.top, xOf(clip.start + f0 + tileFrames - clip.srcStart), r.bottom)
            canvas.drawBitmap(bmp, null, spectroDst, spectroPaint)
        }
        canvas.restore()
        return true
    }

    private val fadePath = Path()

    /** La courbe de chaque fondu, et la zone atténuée au-dessus : on voit d'un coup d'œil où le clip monte ou descend. */
    private fun drawFades(canvas: Canvas, clip: Clip, x0: Float, x1: Float, r: RectF) {
        val h = r.height()
        fun curve(len: Long, gainAt: (Long) -> Float, startX: Float, widthPx: Float) {
            if (len <= 0 || widthPx < 2f) return
            val steps = 16
            fadePath.reset()
            for (k in 0..steps) {
                val p = k.toFloat() / steps
                val g = gainAt((p * len).toLong().coerceAtMost(len - 1))
                val x = startX + p * widthPx
                val y = r.bottom - g.coerceIn(0f, 1f) * h
                if (k == 0) fadePath.moveTo(x, y) else fadePath.lineTo(x, y)
            }
            stroke.color = withAlpha(cText, 0xCC)
            stroke.strokeWidth = dp(1.5f)
            canvas.drawPath(fadePath, stroke)
            fadePath.lineTo(startX + widthPx, r.top)
            fadePath.lineTo(startX, r.top)
            fadePath.close()
            fill.color = withAlpha(cBackground, 0x66)
            canvas.drawPath(fadePath, fill)
        }
        val inPx = (clip.fadeIn.len / fpp()).toFloat()
        val outPx = (clip.fadeOut.len / fpp()).toFloat()
        curve(clip.fadeIn.len, { clip.fadeIn.gainAt(it) }, x0, inPx)
        curve(clip.fadeOut.len, { clip.fadeOut.gainAt(it) }, x1 - outPx, outPx)
    }

    /**
     * Les poignées du clip choisi : deux ronds en haut (début et fin des fondus), une languette à chaque bord (rogner : on la
     * tire vers l'intérieur pour raccourcir, vers l'extérieur pour rallonger tant qu'il reste de la source) et une barre en bas
     * (déplacer le clip, d'une piste à l'autre).
     */
    private fun drawHandles(canvas: Canvas, c: Clip, r: RectF) {
        val x0 = xOf(c.start)
        val x1 = xOf(c.end)
        fill.color = cText
        val hy = r.top + dp(11)
        canvas.drawCircle(fadeHandleX(c, false), hy, dp(6), fill)
        canvas.drawCircle(fadeHandleX(c, true), hy, dp(6), fill)

        // Barre de déplacement, en bas, avec ses trois traits de prise.
        val barL = max(x0 + dp(16), headerW)
        val barR = min(x1 - dp(16), width.toFloat())
        if (barR - barL > dp(30)) {
            rect2.set(barL, r.bottom - dp(20), barR, r.bottom - dp(6))
            fill.color = withAlpha(cText, 0x55)
            canvas.drawRoundRect(rect2, dp(7), dp(7), fill)
            val cx = (barL + barR) / 2
            stroke.color = withAlpha(cText, 0xEE)
            stroke.strokeWidth = dp(1.5f)
            for (k in -1..1) canvas.drawLine(cx + k * dp(5), r.bottom - dp(16), cx + k * dp(5), r.bottom - dp(10), stroke)
        }

        // Languettes des bords, à cheval sur le bord, au milieu du clip.
        val midY = (r.top + r.bottom) / 2f
        for (edge in floatArrayOf(x0, x1)) {
            if (edge < headerW - dp(10) || edge > width + dp(10)) continue
            rect2.set(edge - dp(8), midY - dp(24), edge + dp(8), midY + dp(24))
            fill.color = cText
            canvas.drawRoundRect(rect2, dp(8), dp(8), fill)
            stroke.color = cBackground
            stroke.strokeWidth = dp(1.5f)
            canvas.drawLine(edge - dp(2.5f), midY - dp(9), edge - dp(2.5f), midY + dp(9), stroke)
            canvas.drawLine(edge + dp(2.5f), midY - dp(9), edge + dp(2.5f), midY + dp(9), stroke)
        }
    }

    /**
     * La forme d'onde d'un clip. Tant qu'un pixel couvre au moins un bloc de crêtes, on lit les crêtes ; plus
     * serré, on lit les échantillons bruts (fenêtre chargée en tâche de fond, voir [EditorViewModel.rawFor]) et,
     * quand un pixel couvre moins de deux échantillons, on les relie par une ligne, avec un point par échantillon
     * au plus fort zoom. Le temps que les échantillons arrivent, on garde le dessin des crêtes.
     */
    private fun drawWave(canvas: Canvas, v: EditorViewModel, clip: Clip, src: Source, peaks: PeakData?, r: RectF, color: Int) {
        wave.color = color
        val px0 = max(r.left, headerW).toInt()
        val px1 = min(r.right, width.toFloat()).toInt()
        val n = px1 - px0
        if (n <= 0) return
        val midAll = (r.top + r.bottom) / 2f
        if (peaks == null) {
            canvas.drawLine(px0.toFloat(), midAll, px1.toFloat(), midAll, wave)
            return
        }
        if (mins.size < n) { mins = FloatArray(n); maxs = FloatArray(n); mins2 = FloatArray(n); maxs2 = FloatArray(n); lines = FloatArray(n * 4) }
        val fpp = fpp()
        // Décalage dans le clip de la première colonne dessinée.
        val t0 = max(0L, frameAt(px0.toFloat()) - clip.start)
        val windowFrames = (n * fpp).toLong() + 1
        val srcStart = if (!clip.reversed) clip.srcStart + t0 else clip.srcStart + clip.length - t0 - windowFrames
        val raw = if (fpp < peaks.blockSize) v.rawFor(src, srcStart - 1, srcStart + windowFrames + 2) else null
        val split = src.channels >= 2 && peaks.channels >= 2 && r.height() >= dp(70)
        val lanes = if (split) 2 else 1
        val laneH = (r.height() - dp(14)) / lanes
        val gain = clip.gain.coerceIn(0f, 4f)
        for (c in 0 until lanes) {
            val mid = r.top + dp(12) + laneH * (c + 0.5f)
            val half = laneH / 2f
            if (raw != null && fpp < 2.0) {
                drawSamples(canvas, raw, c.coerceAtMost(raw.data.size - 1), clip, fpp, mid, half, gain, px0.toFloat(), px1.toFloat(), color)
                continue
            }
            if (raw != null) {
                if (split || raw.data.size == 1) {
                    PeakData.columnsFromSamples(raw.data[c.coerceAtMost(raw.data.size - 1)], raw.first, srcStart, fpp, n, mins, maxs)
                } else {
                    PeakData.columnsFromSamples(raw.data[0], raw.first, srcStart, fpp, n, mins, maxs)
                    PeakData.columnsFromSamples(raw.data[1], raw.first, srcStart, fpp, n, mins2, maxs2)
                    for (i in 0 until n) { if (mins2[i] < mins[i]) mins[i] = mins2[i]; if (maxs2[i] > maxs[i]) maxs[i] = maxs2[i] }
                }
            } else if (split || peaks.channels == 1) {
                peaks.columns(c.coerceAtMost(peaks.channels - 1), srcStart, fpp, n, mins, maxs)
            } else {
                // Stéréo dessinée sur une seule ligne : l'enveloppe des deux voies.
                peaks.columns(0, srcStart, fpp, n, mins, maxs)
                peaks.columns(1, srcStart, fpp, n, mins2, maxs2)
                for (i in 0 until n) { if (mins2[i] < mins[i]) mins[i] = mins2[i]; if (maxs2[i] > maxs[i]) maxs[i] = maxs2[i] }
            }
            if (clip.reversed) { mins.reverse(0, n); maxs.reverse(0, n) }
            var k = 0
            for (i in 0 until n) {
                val x = px0 + i + 0.5f
                val top = mid - (maxs[i] * gain).coerceIn(-1f, 1f) * half
                var bottom = mid - (mins[i] * gain).coerceIn(-1f, 1f) * half
                if (bottom - top < 1f) bottom = top + 1f
                lines[k++] = x; lines[k++] = top; lines[k++] = x; lines[k++] = bottom
            }
            canvas.drawLines(lines, 0, k, wave)
        }
    }

    /** Très gros plan : les échantillons reliés par une ligne, placés exactement (un échantillon = une position), avec un point chacun au plus fort zoom. */
    private fun drawSamples(
        canvas: Canvas, win: RawWindow, ch: Int, clip: Clip, fpp: Double,
        mid: Float, half: Float, gain: Float, px0: Float, px1: Float, color: Int,
    ) {
        val data = win.data[ch]
        // Une trame de la source -> sa trame sur la chronologie (le clip retourné se lit à l'envers).
        fun timelineOf(f: Long): Long =
            if (!clip.reversed) clip.start + (f - clip.srcStart) else clip.start + (clip.srcStart + clip.length - 1 - f)
        fun sourceOf(t: Long): Long =
            if (!clip.reversed) clip.srcStart + (t - clip.start) else clip.srcStart + clip.length - 1 - (t - clip.start)
        val a = sourceOf(frameAt(px0) - 1)
        val b = sourceOf(frameAt(px1) + 1)
        val f0 = max(min(a, b), max(clip.srcStart, win.first))
        val f1 = min(max(a, b), min(clip.srcStart + clip.length - 1, win.first + data.size - 1))
        if (f1 < f0) return
        val count = (f1 - f0 + 1).toInt()
        if (lines.size < count * 4) lines = FloatArray(count * 4)
        stroke.color = color
        stroke.strokeWidth = dp(1.3f)
        var k = 0
        var prevX = 0f
        var prevY = 0f
        for (f in f0..f1) {
            val x = xOf(timelineOf(f))
            val y = mid - (data[(f - win.first).toInt()] * gain).coerceIn(-1f, 1f) * half
            if (f > f0) { lines[k++] = prevX; lines[k++] = prevY; lines[k++] = x; lines[k++] = y }
            prevX = x
            prevY = y
        }
        canvas.drawLines(lines, 0, k, stroke)
        if (fpp <= 0.5) {
            fill.color = color
            for (f in f0..f1) {
                val y = mid - (data[(f - win.first).toInt()] * gain).coerceIn(-1f, 1f) * half
                canvas.drawCircle(xOf(timelineOf(f)), y, dp(2f), fill)
            }
        }
    }

    private fun drawRuler(canvas: Canvas, v: EditorViewModel, playhead: Long) {
        fill.color = cSurface
        canvas.drawRect(0f, 0f, width.toFloat(), rulerH, fill)
        stroke.color = cOutline
        stroke.strokeWidth = 1f
        canvas.drawLine(0f, rulerH, width.toFloat(), rulerH, stroke)

        val rate = v.project.sampleRate
        val fpp = fpp()
        val minPx = dp(76).toDouble()
        val step = TimelineMath.majorTickFrames(fpp, rate, minPx)
        val minor = TimelineMath.minorDivisions(fpp, rate, minPx)
        val first = TimelineMath.floorTick(v.ui.scrollFrame, step)
        canvas.save()
        canvas.clipRect(headerW, 0f, width.toFloat(), rulerH)
        text.textSize = dp(10)
        text.color = cTextDim
        text.typeface = Typeface.DEFAULT
        stroke.strokeWidth = 1f
        var t = first
        while (true) {
            val x = xOf(t)
            if (x > width) break
            stroke.color = cTextFaint
            canvas.drawLine(x, rulerH * 0.45f, x, rulerH, stroke)
            canvas.drawText(label(t, rate, step), x + dp(3), rulerH * 0.5f - dp(2), text)
            for (m in 1 until minor) {
                val mx = xOf(t + step * m / minor)
                canvas.drawLine(mx, rulerH * 0.75f, mx, rulerH, stroke)
            }
            t += step
        }
        // Repères de la ligne de temps
        for (m in v.project.markers) {
            val x = xOf(m.pos)
            if (x < headerW || x > width) continue
            fill.color = cAccent
            tri.reset(); tri.moveTo(x - dp(4), 0f); tri.lineTo(x + dp(4), 0f); tri.lineTo(x, dp(7)); tri.close()
            canvas.drawPath(tri, fill)
        }
        // Tête de lecture
        val px = xOf(playhead)
        fill.color = if (v.isPlaying) cText else cAccent
        tri.reset(); tri.moveTo(px - dp(6), rulerH - dp(9)); tri.lineTo(px + dp(6), rulerH - dp(9)); tri.lineTo(px, rulerH); tri.close()
        canvas.drawPath(tri, fill)
        canvas.restore()
    }

    private fun label(frame: Long, rate: Int, step: Long): String {
        val key = frame * 2 + if (step < rate) 1 else 0
        labels.get(key)?.let { return it }
        val c = TimelineMath.clock(frame, rate)
        val s = when {
            step < rate -> context.getString(R.string.ae_time_mss_cs, c.hours * 60 + c.minutes, c.seconds, c.centis)
            c.hours > 0 -> context.getString(R.string.ae_time_hmmss, c.hours, c.minutes, c.seconds)
            else -> context.getString(R.string.ae_time_mss, c.minutes, c.seconds)
        }
        labels.put(key, s)
        return s
    }

    private fun drawHeaders(canvas: Canvas, v: EditorViewModel) {
        canvas.save()
        canvas.clipRect(0f, rulerH, headerW, height.toFloat())
        fill.color = cSurface
        canvas.drawRect(0f, rulerH, headerW, height.toFloat(), fill)
        val r = RectF()
        for ((i, t) in v.project.tracks.withIndex()) {
            val top = trackTop(i)
            if (top + trackH < rulerH || top > height) continue
            val selected = t.id in v.ui.selectedTracks
            fill.color = if (selected) cRaised else cSurface
            canvas.drawRect(0f, top, headerW, top + trackH - 1f, fill)
            fill.color = TrackColors.colorFor(t.color, i)
            canvas.drawRect(0f, top, dp(4), top + trackH - 1f, fill)
            stroke.color = cOutline
            stroke.strokeWidth = 1f
            canvas.drawLine(0f, top + trackH, headerW, top + trackH, stroke)

            text.color = cText
            text.textSize = dp(12)
            text.typeface = Typeface.DEFAULT_BOLD
            val name = TextUtils.ellipsize(t.name, text, headerW - dp(50), TextUtils.TruncateAt.END)
            canvas.drawText(name, 0, name.length, dp(12), top + dp(22), text)

            // Menu ⋮
            menuRect(top, r)
            fill.color = cTextDim
            for (k in 0..2) canvas.drawCircle(r.centerX(), r.centerY() - dp(6) + k * dp(6), dp(1.8f), fill)

            // Sourdine / solo
            muteRect(top, r)
            drawToggle(canvas, r, context.getString(R.string.ae_mute_short), t.mute, 0xFFE57373.toInt())
            soloRect(top, r)
            drawToggle(canvas, r, context.getString(R.string.ae_solo_short), t.solo, 0xFFFFD54F.toInt())
            if (t.locked) {
                text.color = cTextDim
                text.textSize = dp(10)
                text.typeface = Typeface.DEFAULT
                canvas.drawText(context.getString(R.string.ae_locked_short), dp(12), top + dp(40), text)
            }
        }
        // Séparation entre en-têtes et pistes
        stroke.color = cOutline
        stroke.strokeWidth = 1f
        canvas.drawLine(headerW, rulerH, headerW, height.toFloat(), stroke)
        canvas.restore()
        // Coin haut gauche
        fill.color = cSurface
        canvas.drawRect(0f, 0f, headerW, rulerH, fill)
        stroke.color = cOutline
        canvas.drawLine(0f, rulerH, headerW, rulerH, stroke)
    }

    private fun drawToggle(canvas: Canvas, r: RectF, label: String, on: Boolean, onColor: Int) {
        fill.color = if (on) onColor else cBackground
        canvas.drawRoundRect(r, dp(5), dp(5), fill)
        text.color = if (on) cBackground else cTextDim
        text.textSize = dp(12)
        text.typeface = Typeface.DEFAULT_BOLD
        val w = text.measureText(label)
        canvas.drawText(label, r.centerX() - w / 2f, r.centerY() + dp(4), text)
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun blend(a: Int, b: Int, t: Float): Int {
        val r = ((a shr 16 and 0xFF) * (1 - t) + (b shr 16 and 0xFF) * t).toInt()
        val g = ((a shr 8 and 0xFF) * (1 - t) + (b shr 8 and 0xFF) * t).toInt()
        val bl = ((a and 0xFF) * (1 - t) + (b and 0xFF) * t).toInt()
        return 0xFF shl 24 or (r shl 16) or (g shl 8) or bl
    }

    private companion object {
        /** Au-delà, trop dézoomé : on dessine la forme d'onde plutôt que des dizaines de tuiles. */
        const val MAX_SPECTRO_TILES = 60
    }
}
