package com.Atom2Universe.app.games.hamlet

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.Atom2Universe.app.R
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

/** Ce que fait le doigt : parcourir le village, poser un objet, ou peindre en glissant. */
sealed class HamletMode {
    object Browse : HamletMode()
    class Place(val def: Def, val moving: Obj?) : HamletMode()
    class Brush(val def: Def) : HamletMode()
    object Bulldoze : HamletMode()
    object Eyedropper : HamletMode()
    object Redesign : HamletMode()
    class Fill(val def: Def) : HamletMode()
}

interface HamletViewHost {
    fun onTapObject(o: Obj)
    fun onTapVillager(v: Villager)
    fun onTapEmpty()
    fun onModeChanged()
}

/** La vue du village : dessin, déplacement, zoom et pose d'objets. Toute la règle est dans [HamletGame]. */
class HamletView(context: Context, val game: HamletGame, val art: HamletArt) : View(context) {

    val renderer = HamletRenderer(game, art)
    var host: HamletViewHost? = null
    var haptics = true
    /** Neighbor villages animate and pan normally but cannot alter their simulated economy. */
    var readOnly = false
        set(value) { field = value; if (value) browse() }
    var photoMode = false
        set(value) { field = value; renderer.photoMode = value; if (value) browse(); invalidate() }
    var mode: HamletMode = HamletMode.Browse
        private set

    private val rng = Random(System.nanoTime())
    private val density = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var running = false
    private var startRequested = false
    private var lastNanos = 0L
    private var lastVillagerSync = -1

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var multi = false
    private var pinchDist = 0f
    private var pinchZoom = 1f
    private var pinchWx = 0f
    private var pinchWy = 0f
    private var draggingGhost = false
    private var lastBrush: IntArray? = null
    private var tipVillager: Villager? = null
    private var rewardAnchor: Villager? = null
    private var editTarget: Obj? = null
    private var directEdit = false
    private var editOffsetX = 0f
    private var editOffsetY = 0f

    init {
        renderer.density = density
        isFocusable = true
        contentDescription = context.getString(R.string.hamlet_map_accessibility)
    }

    // ------------------------------------------------------------------ boucle

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceAtMost(0.1f)
            lastNanos = now
            if (!readOnly) game.tick()
            if (lastVillagerSync != game.rev) { lastVillagerSync = game.rev; renderer.syncVillagers(rng) }
            renderer.update(dt, rng)
            invalidate()
            postOnAnimation(this)
        }
    }

    fun start() {
        startRequested = true
        if (!hasWindowFocus()) return
        if (running) return
        running = true
        lastNanos = 0L
        postOnAnimation(frame)
    }

    fun stop() {
        startRequested = false
        suspendFrames()
        game.buildHistory.endGesture()
    }

    private fun suspendFrames() {
        running = false
        removeCallbacks(frame)
        lastNanos = 0L
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); start() }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) suspendFrames() else if (startRequested) start()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        renderer.width = w; renderer.height = h
        super.onSizeChanged(w, h, ow, oh)
    }

    override fun onDraw(canvas: Canvas) { renderer.draw(canvas) }

    /** Recentre sur la halle, un peu au sud, là où se passe la vie du village. */
    fun home() {
        val hall = game.objs.values.firstOrNull { it.t == "hall" } ?: return
        renderer.zoom = (density * 0.65f).coerceIn(0.65f, 2.6f)
        renderer.centerOn(hall.x + 2f, hall.y + 8f)
        renderer.clampCamera()
        lastVillagerSync = -1
    }

    // ------------------------------------------------------------------ modes

    fun browse() {
        game.buildHistory.endGesture()
        editTarget = null; directEdit = false
        mode = HamletMode.Browse
        renderer.ghostDef = null
        renderer.brushTile = null
        renderer.hidden = null
        host?.onModeChanged()
    }

    fun startPlacing(def: Def, moving: Obj? = null) {
        if (readOnly || photoMode || def.cat == Cat.HALL) return
        game.buildHistory.endGesture()
        editTarget = null; directEdit = false
        if (moving == null) game.buildHistory.noteRecent(def.id)
        renderer.selected = null
        if (moving == null && (def.isPath || def.drag)) {
            mode = HamletMode.Brush(def)
            renderer.ghostDef = null
            renderer.brushTile = null
        } else {
            mode = HamletMode.Place(def, moving)
            renderer.ghostDef = def
            renderer.ghostFlip = moving?.flip ?: false
            renderer.hidden = moving
            val c = if (moving != null) intArrayOf(moving.x, moving.y) else centerTile(def)
            renderer.ghostX = c[0]; renderer.ghostY = c[1]
            renderer.ghostCrop = null
            refreshGhost()
        }
        host?.onModeChanged()
    }

    fun startBulldozer() = toolMode(HamletMode.Bulldoze)
    fun startEyedropper() = toolMode(HamletMode.Eyedropper)
    fun startRedesign() = toolMode(HamletMode.Redesign)
    fun startFill(def: Def) { if (def.isPath) toolMode(HamletMode.Fill(def)) }
    private fun toolMode(next: HamletMode) {
        if (readOnly || photoMode) return
        browse(); mode = next; renderer.selected = null; host?.onModeChanged()
    }
    fun duplicate(o: Obj): Boolean {
        if (readOnly || photoMode || game.objs[o.id] !== o || o.def.cat == Cat.HALL) return false
        startPlacing(o.def)
        if (mode is HamletMode.Place) { renderer.ghostFlip = o.flip; refreshGhost() }
        game.listener?.onMessage("copied", o.t)
        return true
    }
    fun selectRecent(index: Int): Boolean {
        if (readOnly || photoMode) return false
        val id = game.buildHistory.recent.getOrNull(index) ?: return false
        startPlacing(Hamlet.byId.getValue(id)); return true
    }
    fun undoBuild(): Boolean {
        if (readOnly || photoMode) return false
        browse()
        val undone = game.buildHistory.undo()
        if (undone) { renderer.selected = null; lastVillagerSync = -1; feedback() }
        host?.onModeChanged(); return undone
    }

    private fun feedback() { if (haptics) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }

    private fun centerTile(def: Def): IntArray {
        val t = renderer.tileAt(width / 2f, height * 0.42f)
        return intArrayOf(floor(t[0] - def.w / 2f + 0.5f).toInt(), floor(t[1] - def.d / 2f + 0.5f).toInt())
    }

    fun rotateGhost() {
        if (mode !is HamletMode.Place) return
        renderer.ghostFlip = !renderer.ghostFlip
        refreshGhost()
        host?.onModeChanged()
    }

    fun ghostFlip() = renderer.ghostFlip

    private fun refreshGhost() {
        val m = mode as? HamletMode.Place ?: return
        renderer.ghostOk = game.canPlace(m.def.id, renderer.ghostX, renderer.ghostY, renderer.ghostFlip, m.moving?.id ?: 0) &&
            (m.moving != null || game.canAfford(m.def)) && game.ulvl() >= m.def.lvl
    }

    /** Valide la pose. Rend vrai si quelque chose a été construit ou déplacé. */
    fun confirmPlacement(): Boolean {
        if (readOnly || photoMode) return false
        val m = mode as? HamletMode.Place ?: return false
        val x = renderer.ghostX; val y = renderer.ghostY; val f = renderer.ghostFlip
        if (!renderer.ghostOk) return false
        return if (m.moving != null) {
            val ok = game.move(m.moving, x, y, f)
            if (ok) browse()
            ok
        } else {
            val o = game.buy(m.def.id, x, y, f)
            if (o != null) {
                if (!game.canAfford(m.def)) browse() else refreshGhost()
                host?.onModeChanged()
                true
            } else false
        }
    }

    // ------------------------------------------------------------------ tactile

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                moved = false; multi = false; lastBrush = null
                draggingGhost = false; editTarget = null; directEdit = false
                tipVillager = if (!readOnly && mode is HamletMode.Browse) renderer.villagerAt(e.x, e.y)?.takeIf { it.gift } else null
                val m = mode
                if (m is HamletMode.Brush && !readOnly) { game.buildHistory.beginGesture(); brushAt(e.x, e.y) }
                else if (m is HamletMode.Bulldoze && !readOnly) { game.buildHistory.beginGesture(); dozeAt(e.x, e.y) }
                else if (m is HamletMode.Redesign && !readOnly) {
                    editTarget = renderer.objectAt(e.x, e.y)?.takeIf { it.def.cat != Cat.HALL }
                    editTarget?.let {
                        val t = renderer.tileAt(e.x, e.y)
                        editOffsetX = t[0] - it.x; editOffsetY = t[1] - it.y
                    }
                }
                else if (m is HamletMode.Place) {
                    // on attrape le fantôme seulement près de lui ; sinon le doigt déplace la vue
                    val t = renderer.tileAt(e.x, e.y - ghostLift())
                    val gx = floor(t[0]).toInt(); val gy = floor(t[1]).toInt()
                    val near = gx in renderer.ghostX - 2 until renderer.ghostX + 4 + m.def.w && gy in renderer.ghostY - 2 until renderer.ghostY + 4 + m.def.d
                    draggingGhost = near
                    if (near) moveGhost(e.x, e.y)
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                game.buildHistory.endGesture()
                if (directEdit) finishDirectEdit(false)
                editTarget = null
                multi = true
                if (e.pointerCount >= 2) {
                    pinchDist = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)).coerceAtLeast(1f)
                    pinchZoom = renderer.zoom
                    val mx = (e.getX(0) + e.getX(1)) / 2; val my = (e.getY(0) + e.getY(1)) / 2
                    pinchWx = (mx - width / 2f) / renderer.zoom + renderer.camX
                    pinchWy = (my - height / 2f) / renderer.zoom + renderer.camY
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2) {
                    val d = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)).coerceAtLeast(1f)
                    renderer.zoom = (pinchZoom * d / pinchDist).coerceIn(density * 0.22f, density * 1.6f)
                    val mx = (e.getX(0) + e.getX(1)) / 2; val my = (e.getY(0) + e.getY(1)) / 2
                    renderer.camX = pinchWx - (mx - width / 2f) / renderer.zoom
                    renderer.camY = pinchWy - (my - height / 2f) / renderer.zoom
                    renderer.clampCamera()
                    moved = true
                } else if (!multi) {
                    if (!moved && hypot(e.x - downX, e.y - downY) > slop) moved = true
                    val m = mode
                    when {
                        m is HamletMode.Brush && !readOnly -> brushAt(e.x, e.y)
                        m is HamletMode.Bulldoze && !readOnly -> dozeAt(e.x, e.y)
                        m is HamletMode.Redesign && editTarget != null && moved && !readOnly -> {
                            val target = editTarget!!
                            mode = HamletMode.Place(target.def, target); renderer.ghostDef = target.def
                            renderer.ghostFlip = target.flip; renderer.ghostCrop = target.crop; renderer.hidden = target
                            directEdit = true; draggingGhost = true
                            moveDirectGhost(e.x, e.y); host?.onModeChanged()
                        }
                        m is HamletMode.Place && directEdit -> moveDirectGhost(e.x, e.y)
                        m is HamletMode.Place && draggingGhost -> moveGhost(e.x, e.y)
                        moved -> {
                            renderer.camX -= (e.x - lastX) / renderer.zoom
                            renderer.camY -= (e.y - lastY) / renderer.zoom
                            renderer.clampCamera()
                        }
                    }
                    lastX = e.x; lastY = e.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // le doigt restant reprend la main sans saut
                val rest = if (e.actionIndex == 0) 1 else 0
                if (e.pointerCount > rest) { lastX = e.getX(rest); lastY = e.getY(rest) }
            }
            MotionEvent.ACTION_UP -> {
                if (directEdit) finishDirectEdit(!multi)
                else if (!moved && !multi) tap(e.x, e.y)
                if (!moved && !multi) performClick()
                game.buildHistory.endGesture()
                renderer.brushTile = null; lastBrush = null; editTarget = null
                parent?.requestDisallowInterceptTouchEvent(false)
                host?.onModeChanged()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (directEdit) finishDirectEdit(false)
                game.buildHistory.endGesture(); renderer.brushTile = null; lastBrush = null; editTarget = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun ghostLift() = 56f * density

    private fun moveDirectGhost(px: Float, py: Float) {
        val t = renderer.tileAt(px, py)
        renderer.ghostX = floor(t[0] - editOffsetX + .5f).toInt()
        renderer.ghostY = floor(t[1] - editOffsetY + .5f).toInt()
        refreshGhost()
    }
    private fun finishDirectEdit(commit: Boolean) {
        val moving = (mode as? HamletMode.Place)?.moving
        if (commit && moving != null) {
            if (renderer.ghostOk && game.move(moving, renderer.ghostX, renderer.ghostY, renderer.ghostFlip)) feedback()
            else game.listener?.onMessage("move_blocked")
        }
        directEdit = false; editTarget = null; draggingGhost = false
        renderer.ghostDef = null; renderer.hidden = null; renderer.brushTile = null
        mode = HamletMode.Redesign; host?.onModeChanged()
    }

    private fun moveGhost(px: Float, py: Float) {
        val m = mode as? HamletMode.Place ?: return
        val w = if (renderer.ghostFlip) m.def.d else m.def.w
        val d = if (renderer.ghostFlip) m.def.w else m.def.d
        val t = renderer.tileAt(px, py - ghostLift())
        renderer.ghostX = floor(t[0] - w / 2f + 0.5f).toInt()
        renderer.ghostY = floor(t[1] - d / 2f + 0.5f).toInt()
        refreshGhost()
    }

    private fun tap(px: Float, py: Float) {
        if (photoMode) return
        when (val m = mode) {
            is HamletMode.Browse -> {
                tipVillager?.let { v ->
                    if (renderer.villagerAt(px, py) === v) {
                        rewardAnchor = v
                        try { game.tip() } finally { rewardAnchor = null }
                        v.gift = false
                        return
                    }
                }
                val v = renderer.villagerAt(px, py)
                if (v != null) { host?.onTapVillager(v); return }
                val o = renderer.objectAt(px, py)
                if (o != null) host?.onTapObject(o) else host?.onTapEmpty()
            }
            is HamletMode.Place -> {
                // un simple tapotement place le fantôme à cet endroit
                if (!draggingGhost) { val w = if (renderer.ghostFlip) m.def.d else m.def.w; val d = if (renderer.ghostFlip) m.def.w else m.def.d
                    val t = renderer.tileAt(px, py)
                    renderer.ghostX = floor(t[0] - w / 2f + 0.5f).toInt(); renderer.ghostY = floor(t[1] - d / 2f + 0.5f).toInt(); refreshGhost() }
            }
            is HamletMode.Brush -> {}
            is HamletMode.Bulldoze -> {}
            is HamletMode.Eyedropper -> eyedropAt(px, py)
            is HamletMode.Redesign -> {
                val o = renderer.objectAt(px, py)
                if (o != null) host?.onTapObject(o) else host?.onTapEmpty()
            }
            is HamletMode.Fill -> if (!readOnly) {
                val tile = renderer.tileAt(px, py)
                val count = game.fillConnected(m.def.id, floor(tile[0]).toInt(), floor(tile[1]).toInt())
                game.listener?.onMessage(if (count > 0) "fill_done" else "fill_empty")
                if (count > 0) feedback()
            }
        }
    }

    private fun eyedropAt(px: Float, py: Float) {
        if (readOnly) return
        renderer.objectAt(px, py)?.let { if (duplicate(it)) return }
        val tile = renderer.tileAt(px, py)
        val id = Hamlet.pathById[game.pathAt(floor(tile[0]).toInt(), floor(tile[1]).toInt())]
        if (id != null) { startPlacing(Hamlet.byId.getValue(id)); game.listener?.onMessage("copied", id) }
        else game.listener?.onMessage("copy_empty")
    }

    private fun dozeAt(px: Float, py: Float) {
        if (readOnly) return
        val t = renderer.tileAt(px, py)
        val tx = floor(t[0]).toInt(); val ty = floor(t[1]).toInt()
        renderer.brushTile = intArrayOf(tx, ty)
        var changed = false
        renderer.objectAt(px, py)?.let { changed = game.sell(it) }
        val last = lastBrush
        val x0 = last?.get(0) ?: tx; val y0 = last?.get(1) ?: ty
        val steps = max(abs(tx - x0), abs(ty - y0))
        for (i in 0..steps) {
            val x = if (steps == 0) tx else x0 + (tx - x0) * i / steps
            val y = if (steps == 0) ty else y0 + (ty - y0) * i / steps
            if (!game.inRealm(x, y)) continue
            val obj = game.objAt(x, y)
            changed = (if (obj != null) game.sell(obj) else game.paint("eraser", x, y)) || changed
        }
        lastBrush = intArrayOf(tx, ty)
        if (changed) feedback()
    }

    /** Peint ou pose en glissant : toutes les tuiles entre le dernier point et celui-ci. */
    private fun brushAt(px: Float, py: Float) {
        val m = mode as? HamletMode.Brush ?: return
        val t = renderer.tileAt(px, py)
        val tx = floor(t[0]).toInt(); val ty = floor(t[1]).toInt()
        renderer.brushTile = intArrayOf(tx, ty)
        val last = lastBrush
        if (last != null && last[0] == tx && last[1] == ty) return
        val x0 = last?.get(0) ?: tx; val y0 = last?.get(1) ?: ty
        val steps = max(abs(tx - x0), abs(ty - y0))
        for (i in 0..steps) {
            val x = if (steps == 0) tx else x0 + (tx - x0) * i / steps
            val y = if (steps == 0) ty else y0 + (ty - y0) * i / steps
            applyBrush(m.def, x, y)
        }
        lastBrush = intArrayOf(tx, ty)
    }

    private fun applyBrush(def: Def, x: Int, y: Int) {
        if (readOnly || !game.inGrid(x, y)) return
        if (def.isPath) { game.paint(def.id, x, y); return }
        if (game.canPlace(def.id, x, y, false) && game.canAfford(def) && game.ulvl() >= def.lvl) {
            game.buy(def.id, x, y, false)
            host?.onModeChanged()
        }
    }

    fun worldToScreenCenter(o: Obj): FloatArray {
        val wx = (o.x + o.w / 2f - (o.y + o.d / 2f)) * 32f
        val wy = (o.x + o.w / 2f + o.y + o.d / 2f) * 16f
        return floatArrayOf(renderer.sx(wx), renderer.sy(wy))
    }

    /** Fait apparaître la récompense au-dessus d'un objet (ou au centre de l'écran si [o] est nul). */
    fun reward(o: Obj?, coins: Int, xp: Int, gems: Int) {
        val parts = ArrayList<Floater.Part>()
        if (coins > 0) parts.add(Floater.Part("coin", context.getString(R.string.hamlet_gain, coins), 0xFFE14A))
        if (xp > 0) parts.add(Floater.Part("xp", context.getString(R.string.hamlet_gain, xp), 0x9FDCFF))
        if (gems > 0) parts.add(Floater.Part("gem", context.getString(R.string.hamlet_gain, gems), 0xC8A0FF))
        if (parts.isEmpty()) return
        if (o != null) renderer.floatText(o.x + o.w / 2f, o.y + o.d / 2f, 1.3f, parts)
        else if (rewardAnchor != null) rewardAnchor!!.let { renderer.floatText(it.x, it.y, 0.9f, parts) }
        else {
            val t = renderer.tileAt(width / 2f, height * 0.4f)
            renderer.floatText(t[0], t[1], 1f, parts)
        }
    }

}
