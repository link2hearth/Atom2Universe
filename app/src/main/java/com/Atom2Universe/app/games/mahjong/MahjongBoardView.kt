package com.Atom2Universe.app.games.mahjong

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.*
import kotlin.random.Random

/**
 * Le plateau : vraies tuiles en relief, empilées en demi-tuiles (Solitaire, Triples) ou posées
 * sur une grille (autres modes). En haut, la bande de la chaîne : score, multiplicateur et sa
 * jauge qui se vide, barre des trois étoiles. Les tuiles sont des images mises en cache par face.
 */
class MahjongBoardView(context: Context, val game: MahjongGame, private val accent: Int,
                       private val porcelain: Int = 0, private val effects: Boolean = true,
                       private val haptics: Boolean = true, val texture: Int = porcelain) : View(context) {
    var onMove: (MahjongGame.Result) -> Unit = {}
    var onTurned: (() -> Unit)? = null
    /** Couché, les grilles tournent d'un quart de tour pour remplir la largeur ; pas les pyramides. */
    var turned = false
        private set
    private val art = MahjongArt(context)
    private val density = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val numberLabels = Array(10) { context.getString(R.string.mj_number, it) }
    private val arrows = resources.getStringArray(R.array.mj_gravity_arrows)
    private val animated get() = effects && ValueAnimator.areAnimatorsEnabled()

    // Géométrie.
    private var tileW = 0f
    private var tileH = 0f
    private var faceW = 0f
    private var faceH = 0f
    private var depth = 0f
    private var cellW = 0f
    private var cellH = 0f
    private var left = 0f
    private var top = 0f
    private var hudH = 0f
    private var pad = 0f
    private var traySlot = 0f
    private var trayTop = 0f
    private var trayLeft = 0f
    private val bounds = HashMap<Int, RectF>()
    private var drawOrder = emptyList<MahjongTile>()
    private var free = emptySet<Int>()
    private var pairsAvailable = 0
    private val shownColumns get() = if (turned) game.rows else game.columns
    private val shownRows get() = if (turned) game.columns else game.rows
    private fun dispX(x: Int, y: Int) = if (turned) y else x
    private fun dispY(x: Int, y: Int) = if (turned) game.columns - 1 - x else y
    /** Un glissé à l'écran (dx, dy) traduit dans le sens du jeu. */
    private fun gameDirection(dx: Int, dy: Int) = if (turned) -dy to dx else dx to dy

    // Images.
    private val sprites = SparseArray<Bitmap>()
    private var spriteKey = 0L
    private var spriteOffX = 0f
    private var spriteOffY = 0f
    private val dst = RectF()
    private val work = RectF()
    private val path = Path()
    private val matchPath = Path()

    // Animations.
    private val from = HashMap<Int, RectF>()
    private val moving = SparseArray<RectF>()
    private var motionAt = 0L
    private var entranceAt = SystemClock.uptimeMillis()
    private var lastFrame = 0L
    private var active = true
    private var hints = emptyList<Int>()
    private var slideHint: MahjongSlide? = null
    private var hintUntil = 0L
    private var shakeId = -1
    private var shakeAt = 0L
    private var shownScore = game.score.toFloat()
    private var comboAt = 0L
    private var shownCombo = game.combo
    private data class Spark(val x: Float, val y: Float, val vx: Float, val vy: Float, val color: Int, val star: Boolean)
    private data class Ghost(val rect: RectF, val face: Int)
    private class Burst(val start: Long, val ghosts: List<Ghost>, val x: Float, val y: Float, val sparks: List<Spark>,
                        val path: Path?, val gain: String, val combo: Int, val comboText: String)
    private val bursts = mutableListOf<Burst>()
    /** Seul l'anneau de la chaîne se vide : une image tous les 30e de seconde lui suffit. */
    private val chainFrame = Runnable { invalidate() }

    // Couche du plateau immobile (API 29+) : repeinte seulement quand une tuile, la sélection
    // ou le glissé change, pas à chaque image de l'anneau ou des éclats.
    private var revision = 0
    private var boardNode: RenderNode? = null
    private var nodeRevision = -1
    private var nodeSelected: Int? = null
    private var nodeDragged: Int? = null
    private var nodeTarget: MahjongSlide? = null

    // Textes du bandeau, refaits seulement quand leur nombre change.
    private var scoreValue = Int.MIN_VALUE
    private var scoreSize = -1f
    private var scoreText = ""
    private var scoreW = 0f
    private var multiplierValue = -1
    private var multiplierText = ""
    private var pairsValue = -1
    private var pairsText = ""
    private var starBar: LinearGradient? = null
    private var starBarL = 0f
    private var starBarR = 0f

    // Toucher.
    private var pressed: Int? = null
    private var downX = 0f
    private var downY = 0f
    private var touchCell: MahjongCell? = null
    private var dragging = false
    private var dragTarget: MahjongSlide? = null
    private val swipeThreshold get() = max(density * 6f, min(cellW * .24f, density * 12f))

    // Glissières.
    private var gridRects = emptyArray<RectF>()
    private var occupiedCells = BooleanArray(0)
    private var reachableCells = BooleanArray(0)
    private val slideTargets = arrayOfNulls<MahjongSlide>(4)
    private var slideStopSource: Int? = null
    private var slideStopsValid = false
    private val iceBounds = RectF()
    private var iceShader: LinearGradient? = null
    private var footprintDashes: DashPathEffect? = null

    init {
        isClickable = true; isFocusable = true
        contentDescription = context.getString(R.string.mj_board_description)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        from.clear(); moving.clear(); layoutTiles()
    }

    // ---- Mise en place ---------------------------------------------------------------------

    private fun layoutTiles() {
        if (width <= 0 || height <= 0) return
        revision++
        val wasTurned = turned
        turned = !game.layered && width > height
        if (wasTurned != turned) post { onTurned?.invoke() }
        pad = density * 8f
        hudH = min(62f * density, height * .12f)
        val reserve = if (game.mode == MahjongMode.TRIPLE) {
            traySlot = min((width - pad * 2) / 7.7f, 84f * density)
            traySlot * ASPECT + 26f * density
        } else 0f
        val availW = width - pad * 2
        val availH = height - hudH - reserve - pad * 2
        if (game.layered) {
            val cols = game.columns; val rws = game.rows
            val zMax = game.tiles.maxOfOrNull { it.z } ?: 0
            val k = .13f
            tileW = min(availW / (cols / 2f + k * (1 + zMax) + .1f), availH / (rws * ASPECT / 2f + k * (1 + zMax) + .1f))
                .coerceIn(1f, 124f * density)
            tileH = tileW * ASPECT; depth = tileW * k
            cellW = tileW / 2; cellH = tileH / 2
            faceW = tileW - tileW * .03f; faceH = tileH - tileW * .03f
            val boardW = cols * cellW + depth * (1 + zMax)
            val boardH = rws * cellH + depth * (1 + zMax)
            left = (width - boardW) / 2 + depth
            top = hudH + pad + (availH - boardH) / 2 + depth * zMax
        } else {
            val perimeter = game.grid
            val margin = if (perimeter) .85f else .2f
            cellW = min(availW / (shownColumns + margin * 2), availH / ((shownRows + margin * 2) * GRID_ASPECT))
                .coerceIn(1f, 130f * density)
            cellH = cellW * GRID_ASPECT; depth = cellW * .09f
            tileW = cellW; tileH = cellH
            faceW = cellW - depth - cellW * .04f; faceH = cellH - depth - cellW * .04f
            left = (width - shownColumns * cellW) / 2
            top = hudH + pad + (availH - shownRows * cellH) / 2
        }
        val key = (faceW.toInt().toLong() shl 32) or faceH.toInt().toLong()
        if (key != spriteKey) { recycleSprites(); spriteKey = key }
        trayTop = height - reserve + 12f * density
        trayLeft = (width - traySlot * 7.42f) / 2
        val live = game.board
        free = live.filter { game.isFree(it, live) }.mapTo(HashSet()) { it.id }
        drawOrder = if (game.layered) live.sortedWith(compareBy<MahjongTile> { it.z }.thenBy { it.y - it.x }.thenByDescending { it.x })
            else live.sortedWith(compareBy<MahjongTile> { dispY(it.x, it.y) }.thenByDescending { dispX(it.x, it.y) })
        bounds.clear()
        for (tile in drawOrder) bounds[tile.id] = faceRect(tile)
        for ((i, id) in game.tray.withIndex()) bounds[id] = traySlotRect(i)
        pairsAvailable = if (game.mode == MahjongMode.CLASSIC || game.grid) game.availablePairs() else 0
        if (game.mode == MahjongMode.SLIDE) updateSlideGrid()
        matchPath.rewind()
        game.lastPath.forEachIndexed { index, cell ->
            val x = cellCenterX(cell.x, cell.y); val y = cellCenterY(cell.x, cell.y)
            if (index == 0) matchPath.moveTo(x, y) else matchPath.lineTo(x, y)
        }
    }

    private fun faceRect(tile: MahjongTile): RectF = if (game.layered) {
        val x = left + tile.x * cellW + tile.z * depth
        val y = top + tile.y * cellH - tile.z * depth
        RectF(x, y, x + faceW, y + faceH)
    } else {
        val x = left + dispX(tile.x, tile.y) * cellW + depth
        val y = top + dispY(tile.x, tile.y) * cellH
        RectF(x, y, x + faceW, y + faceH)
    }

    private fun traySlotRect(i: Int): RectF {
        val w = traySlot * .94f
        val x = trayLeft + i * traySlot * 1.06f
        return RectF(x, trayTop, x + w, trayTop + w * ASPECT)
    }

    private fun cellCenterX(x: Int, y: Int) = left + (dispX(x, y) + .5f) * cellW + depth * .5f
    private fun cellCenterY(x: Int, y: Int) = top + (dispY(x, y) + .5f) * cellH - depth * .5f

    private fun recycleSprites() {
        for (i in 0 until sprites.size()) sprites.valueAt(i).recycle()
        sprites.clear()
    }

    private fun sprite(face: Int): Bitmap {
        sprites[face]?.let { return it }
        val extra = 3f * density
        spriteOffX = depth * 1.7f + extra; spriteOffY = extra
        val w = ceil(faceW + spriteOffX + extra).toInt().coerceAtLeast(1)
        val h = ceil(faceH + depth * 2f + extra * 2).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            art.tile(Canvas(it), spriteOffX, spriteOffY, faceW, faceH, face, accent, style = porcelain, texture = texture, depth = depth)
            sprites.put(face, it)
        }
    }

    /** Dessine la tuile dont la face occupe [rect] (l'image est mise à l'échelle si besoin). */
    private fun drawTile(c: Canvas, face: Int, rect: RectF, alpha: Int = 255) {
        val bitmap = sprite(face)
        val s = rect.width() / faceW
        dst.set(rect.left - spriteOffX * s, rect.top - spriteOffY * s,
            rect.left + (bitmap.width - spriteOffX) * s, rect.top + (bitmap.height - spriteOffY) * s)
        p.alpha = alpha; c.drawBitmap(bitmap, null, dst, p); p.alpha = 255
        if (game.mode == MahjongMode.TEN) {
            // La règle des nombres reste lisible avec toutes les familles.
            val radius = rect.width() * .2f
            val cx = rect.right - radius * .95f; val cy = rect.bottom - radius * .95f
            p.color = 0xff1f3b5c.toInt(); p.alpha = alpha; c.drawCircle(cx, cy, radius, p)
            label.color = Color.WHITE; label.alpha = alpha; label.textSize = rect.width() * .3f; label.textAlign = Paint.Align.CENTER
            c.drawText(numberLabels[face % 9 + 1], cx, cy - (label.ascent() + label.descent()) / 2, label)
            label.alpha = 255; p.alpha = 255
        }
    }

    private fun cornerRadius(rect: RectF) = rect.width() * .12f

    // ---- Dessin ----------------------------------------------------------------------------

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (bounds.isEmpty() && !game.complete) layoutTiles()
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceIn(0f, .1f)
        lastFrame = now
        var animating = false
        val chainTicking = active && game.tick(dt)
        // Le paysage est le fond de l'écran entier (MahjongTableDrawable) : la vue reste transparente.
        if (now - motionAt >= MOTION_MS) from.clear()
        val animated = animated
        if (animated && from.isNotEmpty()) animating = true
        val hintPulse = if (now < hintUntil) (.55f + .45f * sin((now % 900) / 900f * 2 * PI).toFloat()) else 0f
        // Ni chute d'entrée, ni glissement, ni secousse, ni indice : le plateau est tiré de sa couche.
        val still = hintPulse == 0f && !(animated && (from.isNotEmpty() || now - entranceAt < ENTRANCE_MS || now - shakeAt < 300))
        if (!still || !drawCachedBoard(c, now)) { if (drawBoard(c, now, hintPulse)) animating = true }
        if (game.mode == MahjongMode.SLIDE) {
            val arrow = if (dragging) dragTarget else if (now < hintUntil) slideHint else null
            arrow?.let { drawSlideArrow(c, it, now) }
        }
        var old = bursts.size
        while (--old >= 0) if (now - bursts[old].start > 1300) bursts.removeAt(old)
        if (animated) for (i in bursts.indices) { drawBurst(c, bursts[i], now); animating = true }
        if (drawHud(c, now)) animating = true
        if (active) {
            if (animating) postInvalidateOnAnimation()
            else if (chainTicking) { removeCallbacks(chainFrame); postOnAnimationDelayed(chainFrame, CHAIN_FRAME_MS) }
        }
    }

    /** Le plateau immobile tiré de sa couche (repeinte quand il change) ; faux si elle n'est pas disponible. */
    private fun drawCachedBoard(c: Canvas, now: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !c.isHardwareAccelerated) return false
        if (width <= 0 || height <= 0) return false
        val node = boardNode ?: RenderNode("mahjongBoard").also {
            it.setUseCompositingLayer(true, null)
            boardNode = it
        }
        val dragged = if (dragging) pressed else null
        if (!node.hasDisplayList() || nodeRevision != revision || nodeSelected != game.selected ||
            nodeDragged != dragged || nodeTarget != dragTarget) {
            node.setPosition(0, 0, width, height)
            val rc = node.beginRecording(width, height)
            drawBoard(rc, now, 0f)
            node.endRecording()
            nodeRevision = revision; nodeSelected = game.selected; nodeDragged = dragged; nodeTarget = dragTarget
        }
        c.drawRenderNode(node)
        return true
    }

    /** Réserve, glace, tuiles du plateau puis de la réserve ; vrai si une tuile bouge encore. */
    private fun drawBoard(c: Canvas, now: Long, hintPulse: Float): Boolean {
        var animating = false
        if (game.mode == MahjongMode.TRIPLE) drawTray(c)
        if (game.mode == MahjongMode.SLIDE) drawSlideGrid(c, now)
        val animated = animated
        val count = drawOrder.size.coerceAtLeast(1)
        for (index in drawOrder.indices) {
            val tile = drawOrder[index]
            val base = location(tile.id, now) ?: continue
            work.set(base)
            var alpha = 255
            if (animated) {
                val delay = index * 520f / count
                val e = ((now - entranceAt - delay) / 360f).coerceIn(0f, 1f)
                if (e < 1f) {
                    animating = true
                    work.offset(0f, -(1f - e).pow(2) * tileH * .7f); alpha = (255 * e).toInt()
                }
                if (tile.id == shakeId && now - shakeAt < 300) {
                    val t = (now - shakeAt) / 300f
                    work.offset(sin(t * 34f) * faceW * .06f * (1 - t), 0f); animating = true
                }
            }
            val selected = tile.id == game.selected || (dragging && tile.id == pressed)
            if (selected && game.layered) work.offset(depth * .35f, -depth * .35f)
            drawTile(c, tile.face, work, alpha)
            val r = cornerRadius(work)
            if (game.layered && tile.id !in free) { p.color = 0x420c1822; c.drawRoundRect(work, r, r, p) }
            if (selected) {
                p.color = 0x50ffd34d; c.drawRoundRect(work, r, r, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = max(2f, work.width() * .05f); p.color = 0xffffb627.toInt()
                c.drawRoundRect(work, r, r, p); p.style = Paint.Style.FILL
            } else if (hintPulse > 0f && tile.id in hints) {
                p.color = 0x6fe0ff; p.alpha = (110 * hintPulse).toInt()
                c.drawRoundRect(work, r, r, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = max(3f, work.width() * .085f)
                p.color = 0xff6fe0ff.toInt(); p.alpha = (255 * hintPulse).toInt()
                c.drawRoundRect(work, r, r, p); p.style = Paint.Style.FILL; p.alpha = 255
                animating = true
            }
        }
        for (k in game.tray.indices) { val id = game.tray[k]; location(id, now)?.let { drawTile(c, game.tiles[id].face, it) } }
        return animating
    }

    private fun location(id: Int, now: Long): RectF? {
        val target = bounds[id] ?: return null
        val old = from[id] ?: return target
        if (!animated || now - motionAt >= MOTION_MS) return target
        val t = ((now - motionAt) / MOTION_MS.toFloat()).coerceIn(0f, 1f)
        val ease = if (game.mode == MahjongMode.SLIDE) t else 1f - (1f - t).pow(3)
        val rect = moving[id] ?: RectF().also { moving.put(id, it) }
        rect.set(old.left + (target.left - old.left) * ease, old.top + (target.top - old.top) * ease,
            old.right + (target.right - old.right) * ease, old.bottom + (target.bottom - old.bottom) * ease)
        return rect
    }

    private fun startMotion(before: Map<Int, RectF>, now: Long) {
        from.clear()
        for ((id, old) in before) { val target = bounds[id] ?: continue; if (old != target) from[id] = old }
        motionAt = now
    }

    /** Bande du haut : score, multiplicateur et sa jauge, barre des étoiles, paires jouables. */
    private fun drawHud(c: Canvas, now: Long): Boolean {
        var animating = false
        val h = hudH
        val panelTop = pad * .4f; val panelBottom = h - pad * .1f
        p.color = 0x4a000000; c.drawRoundRect(pad, panelTop, width - pad, panelBottom, h * .3f, h * .3f, p)
        val cy = (panelTop + panelBottom) / 2
        // Score qui défile.
        val target = game.score.toFloat()
        if (abs(shownScore - target) > .5f && animated) {
            shownScore += (target - shownScore) * .22f; animating = true
        } else shownScore = target
        val scoreTextSize = h * .4f
        label.textAlign = Paint.Align.LEFT; label.color = Color.WHITE; label.textSize = scoreTextSize
        val shownValue = shownScore.roundToInt()
        if (shownValue != scoreValue || scoreTextSize != scoreSize) {
            scoreValue = shownValue; scoreSize = scoreTextSize
            scoreText = context.getString(R.string.mj_number, shownValue)
            scoreW = max(label.measureText(scoreText), label.measureText(context.getString(R.string.mj_number, 8888)))
        }
        c.drawText(scoreText, pad * 2.4f, cy - (label.ascent() + label.descent()) / 2, label)
        // Multiplicateur : un anneau qui se vide pendant la chaîne.
        if (game.combo != shownCombo) { if (game.combo > shownCombo) comboAt = now; shownCombo = game.combo }
        val radius = h * .33f
        val bx = pad * 2.4f + scoreW + radius + h * .3f
        val chain = game.chaining
        val tone = chainColor(game.combo)
        val pop = if (animated && now - comboAt < 280) { animating = true; 1f + .35f * (1 - (now - comboAt) / 280f) } else 1f
        p.color = if (chain) darken(tone) else 0x30ffffff
        c.drawCircle(bx, cy, radius * pop, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = radius * .2f; p.strokeCap = Paint.Cap.ROUND
        if (chain) {
            p.color = tone
            work.set(bx - radius * pop, cy - radius * pop, bx + radius * pop, cy + radius * pop)
            c.drawArc(work, -90f, 360f * (if (game.timed) game.chainLeft else 1f), false, p)
        }
        p.style = Paint.Style.FILL; p.strokeCap = Paint.Cap.BUTT
        label.textAlign = Paint.Align.CENTER; label.textSize = radius * .82f * pop
        label.color = if (chain) Color.WHITE else 0x90ffffff.toInt()
        val multiplier = game.multiplierShown()
        if (multiplier != multiplierValue) { multiplierValue = multiplier; multiplierText = context.getString(R.string.mj_multiplier, multiplier) }
        c.drawText(multiplierText, bx, cy - (label.ascent() + label.descent()) / 2, label)
        // Paires jouables, et sens de la prochaine chute en Cascade.
        var right = width - pad * 2.2f
        if (game.mode == MahjongMode.CASCADE) {
            val shown = if (turned) (game.gravityDirection + 3) % 4 else game.gravityDirection
            label.textSize = h * .5f; label.color = 0xffffe08a.toInt(); label.textAlign = Paint.Align.RIGHT
            c.drawText(arrows[shown], right, cy - (label.ascent() + label.descent()) / 2, label)
            right -= label.measureText(arrows[shown]) + h * .25f
        }
        if (game.mode == MahjongMode.CLASSIC || game.grid) {
            label.textSize = h * .34f; label.color = Color.WHITE; label.textAlign = Paint.Align.RIGHT
            if (pairsAvailable != pairsValue) { pairsValue = pairsAvailable; pairsText = context.getString(R.string.mj_number, pairsAvailable) }
            c.drawText(pairsText, right, cy - (label.ascent() + label.descent()) / 2, label)
            right -= label.measureText(pairsText) + h * .12f
            // Deux petites tuiles : « paires possibles ».
            val iw = h * .22f; val ih = iw * ASPECT
            for (k in 0..1) {
                val x = right - iw - (1 - k) * iw * .55f; val y = cy - ih / 2 + (1 - k) * ih * .12f
                p.color = 0xff2c6e5f.toInt(); c.drawRoundRect(x - iw * .1f, y + iw * .1f, x + iw * .9f, y + ih + iw * .1f, iw * .18f, iw * .18f, p)
                p.color = 0xfff6efdc.toInt(); c.drawRoundRect(x, y, x + iw, y + ih, iw * .18f, iw * .18f, p)
            }
            right -= iw * 1.7f + h * .3f
        }
        // Barre des étoiles.
        val barL = bx + radius + h * .45f; val barR = right
        if (barR - barL > h) {
            val t3 = game.starThreshold(3).coerceAtLeast(1).toFloat()
            val barH = h * .16f
            p.color = 0x38ffffff; c.drawRoundRect(barL, cy - barH / 2, barR, cy + barH / 2, barH / 2, barH / 2, p)
            val fill = (shownScore / t3).coerceIn(0f, 1f)
            if (fill > 0f) {
                if (starBar == null || barL != starBarL || barR != starBarR) {
                    starBarL = barL; starBarR = barR
                    starBar = LinearGradient(barL, 0f, barR, 0f, 0xffffd66b.toInt(), 0xffffa53a.toInt(), Shader.TileMode.CLAMP)
                }
                p.shader = starBar
                c.drawRoundRect(barL, cy - barH / 2, barL + (barR - barL) * fill, cy + barH / 2, barH / 2, barH / 2, p)
                p.shader = null
            }
            for (star in 1..3) {
                val x = barL + (barR - barL) * (game.starThreshold(star) / t3)
                val reached = shownScore >= game.starThreshold(star)
                art.star(c, x.coerceAtMost(barR - h * .1f), cy, h * .23f, if (reached) 0xff3a2a10.toInt() else 0x66000000)
                art.star(c, x.coerceAtMost(barR - h * .1f), cy, h * .19f, if (reached) 0xffffd34a.toInt() else 0x60ffffff)
            }
        }
        return animating
    }

    private fun MahjongGame.multiplierShown() = if (chaining) combo else 1

    private fun chainColor(combo: Int) = when {
        combo <= 1 -> 0xfff3f0e6.toInt()
        combo <= 3 -> 0xffffcf4a.toInt()
        combo <= 5 -> 0xffff9a3c.toInt()
        combo <= 7 -> 0xffff5d73.toInt()
        else -> 0xffc77dff.toInt()
    }
    private fun darken(color: Int) = MahjongArt.darken(color, .45f)

    private fun drawTray(c: Canvas) {
        val w = traySlot * 7.42f
        val full = game.tray.size
        p.color = when { full >= 6 -> 0xcc7a2121.toInt(); full >= 5 -> 0xcc7a5521.toInt(); else -> 0xaa0b2420.toInt() }
        c.drawRoundRect(trayLeft - traySlot * .2f, trayTop - traySlot * .2f, trayLeft + w + traySlot * .1f,
            trayTop + traySlot * .94f * ASPECT + traySlot * .3f, traySlot * .22f, traySlot * .22f, p)
        for (i in 0 until MahjongGame.TRAY) {
            val rect = traySlotRect(i)
            p.color = 0x22ffffff; c.drawRoundRect(rect, rect.width() * .12f, rect.width() * .12f, p)
        }
    }

    private fun drawBurst(c: Canvas, b: Burst, now: Long) {
        val t = (now - b.start) / 1000f
        val meet = .16f
        val gather = (t / meet).coerceIn(0f, 1f).let { it * it }
        val n = b.ghosts.size
        // Les tuiles glissent l'une vers l'autre, se touchent, puis s'envolent en éclats.
        if (t < .5f) b.ghosts.forEachIndexed { i, ghost ->
            val slot = (i - (n - 1) / 2f) * ghost.rect.width() * 1.02f
            val tx = b.x + slot; val ty = b.y
            val cx = ghost.rect.centerX() + (tx - ghost.rect.centerX()) * gather
            val cy = ghost.rect.centerY() + (ty - ghost.rect.centerY()) * gather
            val scale = if (t < meet) 1f + .06f * gather else (1.12f - (t - meet) / .34f * 1.12f).coerceAtLeast(0f)
            if (scale <= 0f) return@forEachIndexed
            val alpha = if (t < meet) 255 else (255 * (1 - (t - meet) / .34f)).toInt().coerceIn(0, 255)
            work.set(cx - ghost.rect.width() * scale / 2, cy - ghost.rect.height() * scale / 2,
                cx + ghost.rect.width() * scale / 2, cy + ghost.rect.height() * scale / 2)
            drawTile(c, ghost.face, work, alpha)
            p.color = 0xffffe9a8.toInt(); p.alpha = (alpha * .45f).toInt()
            c.drawRoundRect(work, cornerRadius(work), cornerRadius(work), p); p.alpha = 255
        }
        if (b.path != null && t < .45f) {
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
            p.color = 0xffffd45a.toInt(); p.alpha = (255 * (1 - t / .45f)).toInt(); p.strokeWidth = cellW * .09f
            c.drawPath(b.path, p); p.strokeWidth = cellW * .035f; p.color = Color.WHITE; p.alpha = (255 * (1 - t / .45f)).toInt()
            c.drawPath(b.path, p)
            p.style = Paint.Style.FILL; p.alpha = 255; p.strokeCap = Paint.Cap.BUTT
        }
        if (t >= meet) {
            val u = t - meet
            val fade = (1f - u / 1.0f).coerceIn(0f, 1f)
            val size = faceW
            p.style = Paint.Style.STROKE; p.color = chainColor(b.combo); p.alpha = (fade * 200).toInt()
            p.strokeWidth = size * .06f * fade + 1f
            c.drawCircle(b.x, b.y, size * (.4f + u * 3.2f), p)
            p.style = Paint.Style.FILL; p.alpha = 255
            if (u < .12f) { p.color = Color.WHITE; p.alpha = (255 * (1 - u / .12f)).toInt(); c.drawCircle(b.x, b.y, size * .9f, p); p.alpha = 255 }
            for (s in b.sparks) {
                val x = s.x + s.vx * u; val y = s.y + s.vy * u + size * 2.4f * u * u
                val color = (s.color and 0x00ffffff) or ((fade * 255).toInt() shl 24)
                if (s.star) art.star(c, x, y, size * .1f * fade + 1f, color) else { p.color = color; c.drawCircle(x, y, size * .045f * fade + .5f, p) }
            }
            if (u < .9f) {
                val rise = u * size * 1.4f
                label.textAlign = Paint.Align.CENTER; label.textSize = size * .42f
                val textY = (b.y - size * .55f - rise).coerceAtLeast(hudH + label.textSize)
                val x = b.x.coerceIn(size, width - size)
                label.color = 0xcc000000.toInt(); label.alpha = (fade * 200).toInt(); c.drawText(b.gain, x + 2f, textY + 2f, label)
                label.color = Color.WHITE; label.alpha = (fade * 255).toInt(); c.drawText(b.gain, x, textY, label)
                if (b.combo >= 2) {
                    label.textSize = size * .34f; label.color = chainColor(b.combo); label.alpha = (fade * 255).toInt()
                    c.drawText(b.comboText, x, textY + size * .42f, label)
                }
                label.alpha = 255
            }
        }
    }

    // ---- Glissières ------------------------------------------------------------------------

    private fun updateSlideGrid() {
        gridRects = Array(game.columns * game.rows) { index ->
            val x = dispX(index % game.columns, index / game.columns); val y = dispY(index % game.columns, index / game.columns)
            RectF(left + x * cellW + depth, top + y * cellH, left + x * cellW + depth + faceW, top + y * cellH + faceH)
        }
        occupiedCells = BooleanArray(gridRects.size); reachableCells = BooleanArray(gridRects.size)
        iceBounds.set(left - cellW * .08f, top - cellH * .06f, left + shownColumns * cellW + cellW * .08f, top + shownRows * cellH + cellH * .04f)
        iceShader = null
        footprintDashes = DashPathEffect(floatArrayOf(cellW * .11f, cellW * .08f), 0f)
        for (tile in drawOrder) occupiedCells[tile.y * game.columns + tile.x] = true
        slideStopsValid = false
    }

    private fun ensureSlideStops(sourceId: Int?) {
        if (slideStopsValid && slideStopSource == sourceId) return
        slideStopsValid = true; slideStopSource = sourceId
        reachableCells.fill(false); slideTargets.fill(null)
        if (sourceId == null) return
        slideTargets[0] = game.iceSlideTarget(sourceId, 1, 0)
        slideTargets[1] = game.iceSlideTarget(sourceId, -1, 0)
        slideTargets[2] = game.iceSlideTarget(sourceId, 0, 1)
        slideTargets[3] = game.iceSlideTarget(sourceId, 0, -1)
        for (target in slideTargets) if (target != null) reachableCells[target.y * game.columns + target.x] = true
    }

    private fun drawSlideGrid(c: Canvas, now: Long) {
        val sourceId = if (dragging) pressed else game.selected
        val source = sourceId?.let { game.tiles.getOrNull(it) }
        ensureSlideStops(sourceId)
        p.style = Paint.Style.FILL
        p.shader = iceShader ?: LinearGradient(iceBounds.left, iceBounds.top, iceBounds.right, iceBounds.bottom,
            0x70d9f3ff, 0x5095c8de, Shader.TileMode.CLAMP).also { iceShader = it }
        c.drawRoundRect(iceBounds, cellW * .18f, cellW * .18f, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = max(2f, cellW * .03f); p.color = 0x90c7ecff.toInt()
        c.drawRoundRect(iceBounds, cellW * .18f, cellW * .18f, p)
        for (y in 0 until game.rows) for (x in 0 until game.columns) {
            val index = y * game.columns + x
            if (occupiedCells[index]) continue
            val reachable = reachableCells[index]
            val target = (dragging && dragTarget?.let { it.x == x && it.y == y } == true) ||
                (now < hintUntil && slideHint?.let { it.x == x && it.y == y } == true)
            val rect = gridRects[index]
            p.style = Paint.Style.FILL
            p.color = when { target -> 0x70ffbd36; reachable -> 0x50a8e4ff; else -> 0x14ffffff }
            c.drawRoundRect(rect, cellW * .12f, cellW * .12f, p)
            if (reachable) {
                p.style = Paint.Style.STROKE; p.color = 0xffffffff.toInt(); p.strokeWidth = max(2f, cellW * .05f)
                if ((source?.x == x) != turned) c.drawLine(rect.centerX() - cellW * .14f, rect.centerY(), rect.centerX() + cellW * .14f, rect.centerY(), p)
                else c.drawLine(rect.centerX(), rect.centerY() - cellH * .12f, rect.centerX(), rect.centerY() + cellH * .12f, p)
            }
            if (game.focusOrigin?.let { it.x == x && it.y == y } == true) {
                // L'empreinte reste tant que la tuile choisie essaie plusieurs glissades.
                p.style = Paint.Style.STROKE; p.strokeWidth = max(2f, cellW * .04f); p.color = 0xffffc252.toInt()
                p.pathEffect = footprintDashes
                work.set(rect); work.inset(cellW * .06f, cellH * .06f)
                c.drawRoundRect(work, cellW * .11f, cellW * .11f, p); p.pathEffect = null
            }
        }
        p.style = Paint.Style.FILL
    }

    private fun drawSlideArrow(c: Canvas, move: MahjongSlide, now: Long) {
        val start = location(move.id, now) ?: return
        val endX = cellCenterX(move.x, move.y); val endY = cellCenterY(move.x, move.y)
        val angle = atan2(endY - start.centerY(), endX - start.centerX())
        val size = cellW * .23f
        p.style = Paint.Style.STROKE; p.strokeWidth = max(3f, cellW * .075f)
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = 0xffffb627.toInt()
        path.rewind()
        path.moveTo(start.centerX(), start.centerY()); path.lineTo(endX, endY)
        path.moveTo(endX - cos(angle - .65f) * size, endY - sin(angle - .65f) * size); path.lineTo(endX, endY)
        path.lineTo(endX - cos(angle + .65f) * size, endY - sin(angle + .65f) * size)
        c.drawPath(path, p); p.style = Paint.Style.FILL; p.strokeCap = Paint.Cap.BUTT
    }

    // ---- Commandes ---------------------------------------------------------------------------

    fun showHint(ids: List<Int>, slide: MahjongSlide?) {
        hints = ids; slideHint = slide
        hintUntil = SystemClock.uptimeMillis() + 3200L
        invalidate()
    }

    /** Après annuler ou mélanger ; un mélange refait tomber les tuiles. */
    fun refresh(shuffled: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        val old = bounds.keys.mapNotNull { id -> location(id, now)?.let { id to RectF(it) } }.toMap()
        layoutTiles(); startMotion(old, now)
        if (shuffled) { entranceAt = now; from.clear() }
        hints = emptyList(); slideHint = null; hintUntil = 0
        invalidate()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); active = true; lastFrame = 0L }
    override fun onDetachedFromWindow() {
        pause()
        // La couche tient les images des tuiles : elle part avant elles.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) boardNode?.discardDisplayList()
        recycleSprites(); super.onDetachedFromWindow()
    }
    /** En pause, les mouvements en cours se posent à leur place : rien ne reste figé en plein vol. */
    fun pause() {
        removeCallbacks(chainFrame)
        active = false; pressed = null; dragging = false; dragTarget = null
        from.clear(); bursts.clear(); entranceAt = 0L; invalidate()
    }
    fun resume() { active = true; lastFrame = 0L; invalidate() }

    private fun cellAt(x: Float, y: Float): MahjongCell? {
        if (cellW <= 0f || cellH <= 0f || game.layered) return null
        val column = floor((x - left) / cellW).toInt(); val row = floor((y - top) / cellH).toInt()
        if (column !in 0 until shownColumns || row !in 0 until shownRows) return null
        return if (turned) MahjongCell(game.columns - 1 - row, column) else MahjongCell(column, row)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !active) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; dragging = false; dragTarget = null; touchCell = cellAt(event.x, event.y)
                val now = SystemClock.uptimeMillis()
                pressed = drawOrder.asReversed().firstOrNull { location(it.id, now)?.contains(event.x, event.y) == true }?.id
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (game.mode == MahjongMode.SLIDE && pressed != null) {
                    val wasDragging = dragging; val previousTarget = dragTarget
                    dragging = hypot(event.x - downX, event.y - downY) >= swipeThreshold
                    val direction = swipeDirection(event.x - downX, event.y - downY)
                    if (dragging) {
                        ensureSlideStops(pressed)
                        val index = when { direction.first > 0 -> 0; direction.first < 0 -> 1; direction.second > 0 -> 2; else -> 3 }
                        dragTarget = slideTargets[index]
                    } else dragTarget = null
                    if (wasDragging != dragging || previousTarget != dragTarget) invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                touchCell = cellAt(event.x, event.y)
                val distance = hypot(event.x - downX, event.y - downY)
                val threshold = if (game.mode == MahjongMode.SLIDE) swipeThreshold else cellW * .9f
                if (game.mode == MahjongMode.SLIDE && pressed != null && distance >= threshold) {
                    val direction = swipeDirection(event.x - downX, event.y - downY)
                    slideInDirection(pressed!!, direction.first, direction.second)
                } else if (distance < threshold) performClick()
                pressed = null; dragging = false; dragTarget = null; invalidate(); return true
            }
            MotionEvent.ACTION_CANCEL -> { pressed = null; dragging = false; dragTarget = null; invalidate(); return true }
        }
        return true
    }

    override fun performClick(): Boolean {
        if (!isEnabled || !active) return false
        super.performClick()
        val id = pressed
        if (id != null) chooseTile(id)
        else if (game.mode == MahjongMode.SLIDE) {
            val selected = game.selected; val cell = touchCell
            if (selected != null && cell != null) {
                val tile = game.tiles[selected]
                if (tile.x == cell.x || tile.y == cell.y)
                    slideInDirection(selected, (cell.x - tile.x).compareTo(0), (cell.y - tile.y).compareTo(0))
            }
        }
        return true
    }

    fun chooseTile(id: Int) = applyMove(id) { game.tap(id) }

    fun slideTile(id: Int, x: Int, y: Int) = applyMove(id) { game.slide(id, x, y) }

    private fun swipeDirection(dx: Float, dy: Float): Pair<Int, Int> =
        if (abs(dx) >= abs(dy)) gameDirection(dx.compareTo(0f), 0) else gameDirection(0, dy.compareTo(0f))

    private fun slideInDirection(id: Int, dx: Int, dy: Int) = applyMove(id) { game.slideDirection(id, dx, dy) }

    private fun applyMove(target: Int, action: () -> MahjongGame.Result) {
        if (!isEnabled || !active || game.complete) return
        val now = SystemClock.uptimeMillis()
        val before = bounds.keys.mapNotNull { key -> location(key, now)?.let { key to RectF(it) } }.toMap()
        val score = game.score
        val result = action(); hintUntil = 0; slideHint = null
        layoutTiles(); startMotion(before, now)
        when (result) {
            MahjongGame.Result.MATCHED -> {
                val ghosts = game.lastMatched.mapNotNull { key -> before[key]?.let { Ghost(it, game.tiles[key].face) } }
                if (ghosts.isNotEmpty() && animated) {
                    val cx = ghosts.map { it.rect.centerX() }.average().toFloat()
                    val cy = ghosts.map { it.rect.centerY() }.average().toFloat()
                    val colors = intArrayOf(0xffffd45a.toInt(), 0xffff8fb1.toInt(), 0xff6fe0ff.toInt(), 0xffb79bff.toInt(), Color.WHITE)
                    val sparks = List((18 + game.combo * 6).coerceAtMost(70)) { index ->
                        val a = Random.nextFloat() * PI * 2; val speed = faceW * (1.2f + Random.nextFloat() * (2.2f + game.combo * .35f))
                        Spark(cx, cy, cos(a).toFloat() * speed, sin(a).toFloat() * speed - faceW * 1.2f, colors[index % colors.size], index % 3 == 0)
                    }
                    bursts += Burst(now, ghosts, cx, cy, sparks, if (game.lastPath.size > 1) Path(matchPath) else null,
                        context.getString(R.string.mj_gain_short, game.score - score), game.combo,
                        if (game.combo >= 2) context.getString(R.string.mj_multiplier, game.combo) else "")
                    while (bursts.size > 5) bursts.removeAt(0)
                }
                if (haptics) performHapticFeedback(if (game.combo >= 4) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK)
            }
            MahjongGame.Result.BLOCKED -> { shakeId = target; shakeAt = now }
            MahjongGame.Result.MOVED, MahjongGame.Result.SELECTED -> if (haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            else -> Unit
        }
        invalidate(); onMove(result)
    }

    private companion object {
        const val ASPECT = 1.33f
        const val GRID_ASPECT = 1.22f
        const val MOTION_MS = 300L
        /** Au-delà, toutes les tuiles ont fini leur chute d'entrée (520 ms d'écart + 360 ms de chute). */
        const val ENTRANCE_MS = 880L
        /** L'anneau seul : la prochaine image de l'écran après 25 ms, soit 30 par seconde à 60 ou 120 Hz. */
        const val CHAIN_FRAME_MS = 25L
    }
}
