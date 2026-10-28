package com.Atom2Universe.app.games.papattes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * L'écran de marche : décor en parallaxe, sol en aplats, eau, obstacles, graines d'or et bestiole.
 * Un seul doigt, n'importe où : poser, tenir, lever. Simulation et dessin sur le fil UI.
 */
class PapattesView(context: Context) : View(context) {
    var game: PapattesGame? = null; private set
    var onFinished: (() -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    private val sprites = PapattesSprites.get(context)
    private val art = PapattesArt(sprites)
    private val pose = PapattesPose()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val path = Path()
    private val rect = RectF()
    private val tmp = FloatArray(2)

    private var running = false
    private var lastFrame = 0L
    private var time = 0f
    private var camX = 0f
    private var camY = 0f
    private var viewportTop = 0f
    private var finishDelay = -1f
    private var shake = 0f
    private var lastFalls = -1
    private var backdrop: Bitmap? = null
    private var terrain: Terrain? = null
    private var groundGradient: LinearGradient? = null
    private var gradientColors: IntArray? = null
    /** Écrasement du chapeau des champignons après un rebond, de 1 à 0. */
    private var squash = 0f
    private var squashed: Mushroom? = null

    private class Particle { var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var life = 0f; var size = 0f; var color = 0 }
    private val particles = Array(PARTICLES) { Particle() }
    private val rnd = Random(7)

    init {
        isClickable = true
        contentDescription = context.getString(R.string.papattes_touch_description)
    }

    /** Prépare une étape sans la lancer : le décor et la bestiole debout s'affichent derrière les menus. */
    fun prepare(stage: Int, critter: Critter) {
        val g = PapattesGame(stage, critter)
        game = g
        backdrop = sprites.backdrop(g.course.biome)
        terrain = buildTerrain(g.course)
        g.walker.torsoCenter(tmp)
        camX = tmp[0]
        camY = g.course.surfaceAt(tmp[0]).takeIf { it.isFinite() } ?: 0f
        finishDelay = -1f
        lastFalls = -1
        for (p in particles) p.life = 0f
        invalidate()
    }

    fun setRunning(value: Boolean) {
        if (running == value) return
        running = value
        if (!value) game?.release()
        lastFrame = 0L
        if (value) postOnAnimation(frame)
    }

    fun setViewportTop(top: Int) {
        viewportTop = top.toFloat()
        invalidate()
    }

    private val frame = object : Runnable {
        override fun run() {
            if (!running || !isAttachedToWindow) return
            val now = SystemClock.elapsedRealtimeNanos()
            val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceIn(0f, .05f)
            lastFrame = now
            step(dt)
            invalidate()
            if (running) postOnAnimation(this)
        }
    }

    private fun step(dt: Float) {
        val g = game ?: return
        time += dt
        g.update(dt)
        for (e in g.events) react(g, e)
        val w = g.walker
        w.torsoCenter(tmp)
        val ease = 1f - exp(-dt * 4.5f)
        camX += (tmp[0] + .6f + (w.body.vx * .25f).coerceIn(0f, 1.2f) - camX) * ease
        val feet = (w.front.foot()[1] + w.back.foot()[1]) / 2
        camY += (feet - camY) * (1f - exp(-dt * 3f))
        shake = (shake - dt * 3f).coerceAtLeast(0f)
        squash = (squash - dt * 4f).coerceAtLeast(0f)
        for (p in particles) if (p.life > 0f) {
            p.life -= dt
            p.vy -= 6f * dt
            p.x += p.vx * dt; p.y += p.vy * dt
        }
        if (g.falls != lastFalls) { lastFalls = g.falls; onChanged?.invoke() }
        if (g.phase == PapattesGame.Phase.FINISHED) {
            if (finishDelay < 0f) finishDelay = FINISH_DELAY
            finishDelay -= dt
            if (finishDelay <= 0f) { setRunning(false); onFinished?.invoke() }
        }
    }

    private fun react(g: PapattesGame, e: PapattesGame.Event) {
        val w = g.walker
        when (e) {
            PapattesGame.Event.STEP -> {
                val foot = (w.swing ?: w.front).foot()
                burst(foot[0], foot[1], 5, dustColor(g.course.biome), .9f)
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            PapattesGame.Event.SPLASH -> {
                w.torsoCenter(tmp)
                val level = g.course.waterAt(tmp[0])?.level ?: tmp[1]
                burst(tmp[0], level, 16, 0xFFBFE9FF.toInt(), 2.4f)
                shake = 1f
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            PapattesGame.Event.FALL -> {
                w.torsoCenter(tmp)
                burst(tmp[0], tmp[1], 10, 0xFFFFE07A.toInt(), 2f)
                shake = 1f
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            PapattesGame.Event.CHECKPOINT -> burst(g.course.checkpointX, g.course.surfaceAt(g.course.checkpointX) + 1.2f, 14, 0xFF7FC8FF.toInt(), 2f)
            PapattesGame.Event.FINISH -> {
                val x = g.course.finishX
                burst(x, g.course.surfaceAt(x) + 1.6f, 30, 0xFFFFC94D.toInt(), 3f)
                performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            PapattesGame.Event.RESPAWN -> {
                w.torsoCenter(tmp)
                camX = tmp[0]
            }
            PapattesGame.Event.JUMP -> {
                val foot = w.front.foot()
                burst(foot[0], foot[1], 6, dustColor(g.course.biome), 1.2f)
            }
            PapattesGame.Event.LAND -> {
                val foot = w.front.foot()
                burst(foot[0], foot[1], 8, dustColor(g.course.biome), 1.4f)
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            PapattesGame.Event.BOUNCE -> {
                w.torsoCenter(tmp)
                squashed = g.course.mushrooms.minByOrNull { kotlin.math.abs(it.x - tmp[0]) }
                squash = 1f
                squashed?.let { burst(it.x, it.y + it.radius, 12, 0xFFFF8A8A.toInt(), 2.2f) }
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            PapattesGame.Event.CRUMBLE -> {
                for (i in g.plankFall.indices) if (g.plankFall[i] == 0f) {
                    val s = g.course.segments[i]
                    burst((s.x0 + s.x1) / 2, s.y0, 6, if (s.surface == Surface.ICE) 0xFFE8F6FF.toInt() else 0xFFB98A5A.toInt(), 1.2f)
                }
            }
            PapattesGame.Event.GOLD -> {
                val seed = g.course.goldSeeds.getOrNull(g.lastGold)
                if (seed != null) burst(seed.x, seed.y, 14, 0xFFFFD34D.toInt(), 2.4f)
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onChanged?.invoke()
            }
            PapattesGame.Event.SAVED -> {
                w.torsoCenter(tmp)
                burst(tmp[0], tmp[1] - walkerRadius(g), 12, 0xFFFF9FC4.toInt(), 2f)
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }

    private fun walkerRadius(g: PapattesGame) = g.walker.bodyRadius

    private fun dustColor(biome: Biome) = when (biome) {
        Biome.MEADOW -> 0xFFC9B48A.toInt()
        Biome.MARSH -> 0xFF8C7A5C.toInt()
        Biome.ICE -> 0xFFF2FAFF.toInt()
    }

    private fun burst(x: Float, y: Float, count: Int, color: Int, power: Float) {
        var made = 0
        for (p in particles) {
            if (made >= count) break
            if (p.life > 0f) continue
            p.x = x; p.y = y
            p.vx = (rnd.nextFloat() - .5f) * power
            p.vy = rnd.nextFloat() * power
            p.life = .5f + rnd.nextFloat() * .4f
            p.size = .03f + rnd.nextFloat() * .04f
            p.color = color
            made++
        }
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = game ?: return false
        if (!running) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> g.press()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> g.release()
        }
        return true
    }

    // ── Dessin ──────────────────────────────────────────────────────────────────────────────────

    private var ppm = 100f
    private var originX = 0f
    private var groundLine = 0f
    private fun sx(x: Float) = originX + (x - camX) * ppm
    private fun sy(y: Float) = groundLine - (y - camY) * ppm

    override fun onDraw(canvas: Canvas) {
        val g = game
        if (g == null) { canvas.drawColor(SKY); return }
        val legs = g.critter.legs
        ppm = min(width / (VIEW_BASE + VIEW_PER_LEG * legs.length), (height - viewportTop) / VIEW_HEIGHT)
        originX = width * CAMERA_LEFT + if (shake > 0f) sin(time * 70f) * shake * 6f else 0f
        groundLine = viewportTop + (height - viewportTop) * GROUND_LINE
        drawBackdrop(canvas, g)
        drawWater(canvas, g)
        drawWind(canvas, g)
        drawGround(canvas, g)
        drawFloaters(canvas, g)
        drawSeesaws(canvas, g)
        drawDecor(canvas, g)
        drawMushrooms(canvas, g)
        drawFlags(canvas, g)
        drawGold(canvas, g)
        drawCritter(canvas, g)
        drawParticles(canvas)
        drawProgress(canvas, g)
    }

    private fun drawBackdrop(canvas: Canvas, g: PapattesGame) {
        canvas.drawColor(SKY)
        val bmp = backdrop ?: return
        val scale = (groundLine + (height - groundLine) * .3f) / bmp.height
        val w = bmp.width * scale
        var offset = -(camX * ppm * PARALLAX) % w
        if (offset > 0) offset -= w
        // Le haut du fond rejoint le ciel uni : on le cale en bas, contre le sol.
        val top = groundLine + (height - groundLine) * .3f - bmp.height * scale
        var x = offset
        while (x < width) {
            matrix.reset()
            matrix.postScale(scale, scale)
            matrix.postTranslate(x, top)
            canvas.drawBitmap(bmp, matrix, bitmapPaint)
            x += w - 1f
        }
        if (top > 0f) {
            fill.color = SKY
            canvas.drawRect(0f, 0f, width.toFloat(), top + 1f, fill)
        }
    }

    private fun visible(x0: Float, x1: Float) = sx(x1) >= -50f && sx(x0) <= width + 50f

    /** L'eau ne remplit que la cuvette : le sol, dessiné par-dessus, en ferme le fond. */
    private fun drawWater(canvas: Canvas, g: PapattesGame) {
        val colors = when (g.course.biome) {
            Biome.MARSH -> intArrayOf(0xE0587E5A.toInt(), 0xFFB9D7A6.toInt())
            Biome.ICE -> intArrayOf(0xE05B8FB8.toInt(), 0xFFE3F4FF.toInt())
            Biome.MEADOW -> intArrayOf(0xE04C93C2.toInt(), 0xFFCFEFFF.toInt())
        }
        for (w in g.course.waters) {
            if (!visible(w.x0 - .3f, w.x1 + .3f)) continue
            val top = sy(w.level)
            fill.color = colors[0]
            canvas.drawRect(sx(w.x0) - 4f, top, sx(w.x1) + 4f, sy(w.level - BASIN - .2f), fill)
            stroke.color = colors[1]
            stroke.strokeWidth = .025f * ppm
            path.reset()
            var x = w.x0
            path.moveTo(sx(x), top)
            while (x < w.x1) {
                x = (x + .08f).coerceAtMost(w.x1)
                path.lineTo(sx(x), top + sin(x * 9f + time * 3f) * .015f * ppm)
            }
            canvas.drawPath(path, stroke)
        }
    }

    /**
     * Le sol d'une étape, calculé une fois en mètres (y vers le bas) : un seul contour aux angles
     * arrondis, qui plonge en cuvette sous chaque trou d'eau ; le dessus (pour la bande d'herbe ou
     * de neige), les festons et les touffes d'herbe.
     */
    private class Terrain(val fill: Path, val tops: Path, val iceTops: Path, val scallops: FloatArray, val tufts: Path)

    private fun buildTerrain(course: PapattesCourse): Terrain {
        val solids = course.segments.filter { !it.floating }.sortedBy { it.x0 }
        val outline = Path()
        val tops = Path()
        val ice = Path()
        val tufts = Path()
        val scallops = ArrayList<Float>()
        var prev: Segment? = null
        for (s in solids) {
            val p = prev
            if (p == null) {
                outline.moveTo(s.x0, -s.y0)
                tops.moveTo(s.x0, -s.y0)
            } else if (s.x0 - p.x1 > .02f) {
                // Un trou : la rive descend en cuvette sous l'eau, puis remonte de l'autre côté.
                val water = course.waterAt((p.x1 + s.x0) / 2)
                val bottom = (water?.level ?: minOf(p.y1, s.y0)) - BASIN
                outline.lineTo(p.x1 + BANK_INSET, -bottom)
                outline.lineTo(s.x0 - BANK_INSET, -bottom)
                outline.lineTo(s.x0, -s.y0)
                tops.moveTo(s.x0, -s.y0)
            } else if (kotlin.math.abs(s.y0 - p.y1) > .005f) {
                outline.lineTo(s.x0, -s.y0)
                tops.lineTo(s.x0, -s.y0)
            }
            outline.lineTo(s.x1, -s.y1)
            tops.lineTo(s.x1, -s.y1)
            if (s.surface == Surface.ICE) { ice.moveTo(s.x0, -s.y0); ice.lineTo(s.x1, -s.y1) }
            var x = ceil(s.x0 / SCALLOP_STEP) * SCALLOP_STEP
            while (x < s.x1) {
                val big = ((x / SCALLOP_STEP).toInt() and 1) == 0
                scallops += x; scallops += -s.yAt(x) + BAND; scallops += BAND * .55f * (if (big) 1f else .75f)
                scallops += if (s.surface == Surface.ICE) 1f else 0f
                x += SCALLOP_STEP
            }
            // Des touffes d'herbe çà et là, jamais sur la glace ni sur la neige.
            if (s.surface == Surface.GRASS || s.surface == Surface.MUD) {
                var tx = ceil(s.x0 / TUFT_STEP) * TUFT_STEP
                while (tx < s.x1 - .05f) {
                    val k = (tx * 7.31f).toInt()
                    if (k % 3 != 0) {
                        val y = -s.yAt(tx)
                        for (blade in -1..1) {
                            tufts.moveTo(tx + blade * .025f, y)
                            tufts.lineTo(tx + blade * .05f, y - .07f - (if (blade == 0) .03f else 0f))
                        }
                    }
                    tx += TUFT_STEP
                }
            }
            prev = s
        }
        val last = solids.last()
        outline.lineTo(last.x1, DEEP)
        outline.lineTo(solids.first().x0, DEEP)
        outline.close()
        // Les angles arrondis sont calculés une fois : le même contour sert au remplissage et à la découpe.
        val rounding = Paint().apply { style = Paint.Style.FILL; pathEffect = CornerPathEffect(CORNER) }
        val rounded = Path()
        @Suppress("DEPRECATION") rounding.getFillPath(outline, rounded)
        val roundedTops = Path()
        @Suppress("DEPRECATION") rounding.getFillPath(tops, roundedTops)
        return Terrain(if (rounded.isEmpty) outline else rounded, if (roundedTops.isEmpty) tops else roundedTops, ice,
            scallops.toFloatArray(), tufts)
    }

    /** Rondins et planches, posés sur l'eau des cuvettes. */
    private fun drawFloaters(canvas: Canvas, g: PapattesGame) {
        for ((i, s) in g.course.segments.withIndex()) {
            if (!visible(s.x0, s.x1)) continue
            when (s.floater) {
                Floater.LOG -> drawLog(canvas, s)
                Floater.PLANK -> drawPlank(canvas, g, i, s)
                Floater.NONE -> Unit
            }
        }
    }

    /** Le sol en aplats : un dégradé par biome, coiffé d'une bande (herbe, mousse, neige) au bord festonné. */
    private fun drawGround(canvas: Canvas, g: PapattesGame) {
        val terrain = terrain ?: return
        val colors = groundColors(g.course.biome)
        if (colors !== gradientColors) {
            groundGradient = LinearGradient(0f, 0f, 0f, 1f, colors[0], colors[1], Shader.TileMode.CLAMP)
            gradientColors = colors
        }
        canvas.save()
        // Le sol est en mètres, y vers le bas : on le pose à l'écran d'un seul coup.
        canvas.translate(originX - camX * ppm, groundLine + camY * ppm)
        canvas.scale(ppm, ppm)
        matrix.reset()
        matrix.postScale(1f, 3.2f)
        matrix.postTranslate(0f, -(camY + .2f))
        groundGradient?.setLocalMatrix(matrix)
        groundPaint.shader = groundGradient
        groundPaint.style = Paint.Style.FILL
        canvas.drawPath(terrain.fill, groundPaint)
        groundPaint.shader = null
        canvas.clipPath(terrain.fill)
        // La bande du dessus : un trait épais centré sur le bord, dont seule la moitié intérieure se voit.
        groundPaint.style = Paint.Style.STROKE
        groundPaint.strokeJoin = Paint.Join.ROUND
        groundPaint.strokeCap = Paint.Cap.ROUND
        groundPaint.strokeWidth = BAND * 2f
        groundPaint.color = colors[3]
        canvas.drawPath(terrain.tops, groundPaint)
        groundPaint.color = 0xFFDDF3FF.toInt()
        canvas.drawPath(terrain.iceTops, groundPaint)
        groundPaint.style = Paint.Style.FILL
        val sc = terrain.scallops
        var i = 0
        while (i < sc.size) {
            if (sc[i] > camX - 12f && sc[i] < camX + 12f) {
                groundPaint.color = if (sc[i + 3] > 0f) 0xFFDDF3FF.toInt() else colors[3]
                canvas.drawCircle(sc[i], sc[i + 1], sc[i + 2], groundPaint)
            }
            i += 4
        }
        // Le liseré clair du bord.
        groundPaint.style = Paint.Style.STROKE
        groundPaint.strokeWidth = .07f
        groundPaint.color = colors[2]
        canvas.drawPath(terrain.tops, groundPaint)
        groundPaint.color = Color.WHITE
        canvas.drawPath(terrain.iceTops, groundPaint)
        canvas.restore()
        // Les touffes d'herbe dépassent du bord : dessinées hors de la découpe.
        if (g.course.biome != Biome.ICE) {
            canvas.save()
            canvas.translate(originX - camX * ppm, groundLine + camY * ppm)
            canvas.scale(ppm, ppm)
            groundPaint.strokeWidth = .022f
            groundPaint.color = colors[4]
            canvas.drawPath(terrain.tufts, groundPaint)
            canvas.restore()
        }
        groundPaint.style = Paint.Style.FILL
    }

    /** Haut et bas du dégradé, liseré clair, bande du dessus, touffes d'herbe. */
    private fun groundColors(biome: Biome) = when (biome) {
        Biome.MEADOW -> MEADOW_GROUND
        Biome.MARSH -> MARSH_GROUND
        Biome.ICE -> ICE_GROUND
    }

    /**
     * Une planche de passerelle (ou un glaçon) : elle tremble dès qu'un pied l'a touchée, puis
     * s'enfonce dans l'eau en pâlissant quand elle cède.
     */
    private fun drawPlank(canvas: Canvas, g: PapattesGame, i: Int, s: Segment) {
        val fall = g.plankFall[i]
        if (fall > 1f) return
        val ice = g.course.biome == Biome.ICE
        val thick = PLANK_THICKNESS * ppm
        val timer = g.plankTimer[i]
        val wobble = if (timer >= 0f && fall < 0f) sin(time * 60f + i) * .012f * ppm * (timer / PapattesGame.CRUMBLE_DELAY) else 0f
        val drop = if (fall >= 0f) .5f * 6f * fall * fall * ppm else 0f
        val alpha = if (fall >= 0f) (255 * (1f - fall)).toInt().coerceIn(0, 255) else 255
        rect.set(sx(s.x0) + wobble, sy(s.y0) + drop, sx(s.x1) + wobble, sy(s.y0) + drop + thick)
        fill.color = if (ice) 0xFFCFEFFF.toInt() else 0xFFC08A55.toInt()
        fill.alpha = alpha
        canvas.drawRoundRect(rect, thick * .3f, thick * .3f, fill)
        stroke.color = if (ice) 0xFF7FB6DA.toInt() else 0xFF6B4423.toInt()
        stroke.alpha = alpha
        stroke.strokeWidth = .014f * ppm
        canvas.drawRoundRect(rect, thick * .3f, thick * .3f, stroke)
        // Une fente au milieu : on voit qu'elle peut casser.
        canvas.drawLine(rect.centerX() - thick * .3f, rect.top + thick * .25f, rect.centerX() + thick * .15f, rect.bottom - thick * .3f, stroke)
        fill.alpha = 255
        stroke.alpha = 255
    }

    /** Les bascules : un poteau planté dans l'eau et la planche, dessinée d'après sa pose physique. */
    private fun drawSeesaws(canvas: Canvas, g: PapattesGame) {
        for ((i, s) in g.course.seesaws.withIndex()) {
            if (!visible(s.pivotX - s.halfLength, s.pivotX + s.halfLength)) continue
            val water = g.course.waterAt(s.pivotX)
            fill.color = 0xFF6B4423.toInt()
            val post = .06f * ppm
            canvas.drawRect(sx(s.pivotX) - post, sy(s.pivotY), sx(s.pivotX) + post, sy((water?.level ?: s.pivotY) - BASIN + .05f), fill)
            val plank = g.seesawBodies[i]
            canvas.save()
            canvas.rotate(-Math.toDegrees(plank.angle.toDouble()).toFloat(), sx(plank.x), sy(plank.y))
            val half = PapattesCourse.SEESAW_THICKNESS / 2 * ppm
            rect.set(sx(plank.x) - s.halfLength * ppm, sy(plank.y) - half, sx(plank.x) + s.halfLength * ppm, sy(plank.y) + half)
            fill.color = 0xFFC9925A.toInt()
            canvas.drawRoundRect(rect, half, half, fill)
            stroke.color = 0xFF6B4423.toInt()
            stroke.strokeWidth = .014f * ppm
            canvas.drawRoundRect(rect, half, half, stroke)
            canvas.restore()
            fill.color = 0xFF4A2F18.toInt()
            canvas.drawCircle(sx(s.pivotX), sy(s.pivotY), .045f * ppm, fill)
        }
    }

    /** Les champignons rebondissants, écrasés un instant après un rebond. */
    private fun drawMushrooms(canvas: Canvas, g: PapattesGame) {
        val sprite = sprites.decor[DecorKind.MUSHROOM] ?: return
        for (m in g.course.mushrooms) {
            if (!visible(m.x - 1f, m.x + 1f)) continue
            val height = (m.y + m.radius - m.groundY) * ppm
            val press = if (m === squashed) 1f - .22f * sin(squash * PI_F) else 1f
            val base = sprite.anchor("base")
            val scale = height / sprite.bitmap.height
            matrix.reset()
            matrix.postTranslate(-base[0], -base[1])
            matrix.postScale(scale * (2f - press), scale * press)
            matrix.postTranslate(sx(m.x), sy(m.groundY))
            canvas.drawBitmap(sprite.bitmap, matrix, bitmapPaint)
        }
    }

    /** Les rafales : des traits qui filent dans le sens du vent, plus nets quand il souffle fort. */
    private fun drawWind(canvas: Canvas, g: PapattesGame) {
        val clock = g.elapsed
        for (wind in g.course.winds) {
            if (!visible(wind.x0, wind.x1)) continue
            val strength = kotlin.math.abs(wind.at(clock) / wind.push)
            stroke.color = 0xFFFFFFFF.toInt()
            stroke.strokeWidth = .02f * ppm
            val dir = if (wind.push > 0f) 1f else -1f
            val ground = g.course.surfaceAt((wind.x0 + wind.x1) / 2)
            for (k in 0 until WIND_STREAKS) {
                val span = wind.x1 - wind.x0
                val x = wind.x0 + ((k * .37f * span + dir * clock * (2.5f + k % 3)) % span + span) % span
                val y = ground + .25f + (k * 0.61f % 1f) * 1.8f
                stroke.alpha = (150 * strength * (1f - kotlin.math.abs(x - wind.x0 - span / 2) / (span / 2))).toInt().coerceIn(0, 255)
                canvas.drawLine(sx(x), sy(y), sx(x - dir * .35f), sy(y), stroke)
            }
            stroke.alpha = 255
        }
    }

    /** Les graines d'or : elles flottent doucement, avec un halo. */
    private fun drawGold(canvas: Canvas, g: PapattesGame) {
        for ((i, seed) in g.course.goldSeeds.withIndex()) {
            if (g.goldTaken[i] || !visible(seed.x - .3f, seed.x + .3f)) continue
            val bob = sin(time * 3f + i) * .04f
            fill.color = 0x55FFE27A
            canvas.drawCircle(sx(seed.x), sy(seed.y + bob), .2f * ppm, fill)
            drawProp(canvas, sprites.seed, seed.x, seed.y + bob - .1f, .24f)
        }
    }

    /** Un rondin qui flotte : écorce, veinures et cernes au bout. */
    private fun drawLog(canvas: Canvas, s: Segment) {
        val thick = LOG_THICKNESS * ppm
        val top = sy(s.y0)
        rect.set(sx(s.x0), top, sx(s.x1), top + thick)
        fill.color = 0xFF8A5A33.toInt()
        canvas.drawRoundRect(rect, thick / 2, thick / 2, fill)
        stroke.color = 0xFF5A3A20.toInt()
        stroke.strokeWidth = .015f * ppm
        canvas.drawRoundRect(rect, thick / 2, thick / 2, stroke)
        stroke.strokeWidth = .01f * ppm
        var x = s.x0 + .15f
        while (x < s.x1 - .25f) {
            canvas.drawLine(sx(x), top + thick * .35f, sx(x + .18f), top + thick * .35f, stroke)
            canvas.drawLine(sx(x + .09f), top + thick * .68f, sx(x + .3f), top + thick * .68f, stroke)
            x += .42f
        }
        val cx = sx(s.x1) - thick / 2
        val cy = top + thick / 2
        fill.color = 0xFFD8A86C.toInt()
        canvas.drawCircle(cx, cy, thick * .42f, fill)
        canvas.drawCircle(cx, cy, thick * .26f, stroke)
        canvas.drawCircle(cx, cy, thick * .1f, stroke)
    }

    private fun drawProp(canvas: Canvas, sprite: Sprite, x: Float, y: Float, width: Float) {
        val base = sprite.anchor("base")
        val scale = width * ppm / sprite.bitmap.width
        matrix.reset()
        matrix.postTranslate(-base[0], -base[1])
        matrix.postScale(scale, scale)
        matrix.postTranslate(sx(x), sy(y))
        canvas.drawBitmap(sprite.bitmap, matrix, bitmapPaint)
    }

    private fun drawDecor(canvas: Canvas, g: PapattesGame) {
        for (d in g.course.decor) {
            if (!visible(d.x - d.size, d.x + d.size)) continue
            val sprite = sprites.decor[d.kind] ?: continue
            drawProp(canvas, sprite, d.x, d.y + if (d.kind == DecorKind.LILY_PAD) -.02f else .02f, d.size)
        }
    }

    private fun drawFlags(canvas: Canvas, g: PapattesGame) {
        val c = g.course
        if (c.checkpointX > 0f && visible(c.checkpointX - 1f, c.checkpointX + 1f)) {
            bitmapPaint.alpha = if (g.checkpointReached) 255 else 190
            drawProp(canvas, sprites.checkpointFlag, c.checkpointX, c.surfaceAt(c.checkpointX), .55f)
            bitmapPaint.alpha = 255
        }
        if (visible(c.finishX - 1.5f, c.finishX + 1.5f)) drawProp(canvas, sprites.finishFlag, c.finishX, c.surfaceAt(c.finishX), 1.3f)
    }

    private fun drawCritter(canvas: Canvas, g: PapattesGame) {
        val w = g.walker
        w.torsoCenter(tmp)
        pose.torsoX = sx(tmp[0]); pose.torsoY = sy(tmp[1])
        w.hipPoint(tmp)
        pose.hipX = sx(tmp[0]); pose.hipY = sy(tmp[1])
        pose.bodyAngle = w.body.angle
        pose.thigh[0] = w.front.thigh.angle; pose.shin[0] = w.front.shin.angle
        pose.thigh[1] = w.back.thigh.angle; pose.shin[1] = w.back.shin.angle
        pose.ppm = ppm
        // Ombre portée sous la bestiole.
        val ground = g.groundAt(tmp[0])
        if (ground.isFinite()) {
            fill.color = 0x33000000
            val r = .45f * ppm
            rect.set(pose.hipX - r, sy(ground) - r * .12f, pose.hipX + r, sy(ground) + r * .12f)
            canvas.drawOval(rect, fill)
        }
        // Un corps qui flotte laisse des ronds dans l'eau sous ses pieds.
        if (g.critter.body.power == Power.FLOAT) for (leg in w.all) {
            val foot = leg.foot()
            val water = g.course.waterAt(foot[0]) ?: continue
            if (foot[1] - water.level > PapattesCourse.FLOAT_LIFT + .08f) continue
            val ring = (.12f + .04f * sin(time * 5f + foot[0] * 3f)) * ppm
            stroke.color = 0xCCFFFFFF.toInt()
            stroke.strokeWidth = .015f * ppm
            rect.set(sx(foot[0]) - ring, sy(water.level) - ring * .22f, sx(foot[0]) + ring, sy(water.level) + ring * .22f)
            canvas.drawOval(rect, stroke)
        }
        art.critter(canvas, g.critter, pose)
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) if (p.life > 0f) {
            fill.color = p.color
            fill.alpha = (255 * (p.life / .9f).coerceIn(0f, 1f)).toInt()
            canvas.drawCircle(sx(p.x), sy(p.y), p.size * ppm, fill)
        }
        fill.alpha = 255
    }

    private fun drawProgress(canvas: Canvas, g: PapattesGame) {
        val margin = 24f * resources.displayMetrics.density
        val h = 8f * resources.displayMetrics.density
        val top = viewportTop + h
        val left = margin
        val right = width - margin
        rect.set(left, top, right, top + h)
        fill.color = 0x66000000
        canvas.drawRoundRect(rect, h, h, fill)
        rect.set(left, top, left + (right - left) * g.progress, top + h)
        fill.color = 0xFFFFD36E.toInt()
        canvas.drawRoundRect(rect, h, h, fill)
        val c = g.course
        if (c.checkpointX > 0f) {
            val t = (c.checkpointX - c.startX) / (c.finishX - c.startX)
            fill.color = if (g.checkpointReached) 0xFF7FC8FF.toInt() else Color.WHITE
            canvas.drawCircle(left + (right - left) * t, top + h / 2, h * .9f, fill)
        }
        // Les graines d'or sur la barre : pleines une fois ramassées.
        for ((i, seed) in c.goldSeeds.withIndex()) {
            val t = ((seed.x - c.startX) / (c.finishX - c.startX)).coerceIn(0f, 1f)
            fill.color = if (g.goldTaken[i]) 0xFFFFD34D.toInt() else 0x99FFFFFF.toInt()
            canvas.drawCircle(left + (right - left) * t, top + h / 2, h * .7f, fill)
        }
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    companion object {
        private const val SKY = 0xFFBFE3F2.toInt()
        private const val PARTICLES = 64
        private const val FINISH_DELAY = 1.1f
        /** Largeur visible : une base plus une part de la longueur des jambes, en mètres. */
        private const val VIEW_BASE = 3.5f
        private const val VIEW_PER_LEG = 1.6f
        private const val VIEW_HEIGHT = 4.6f
        /** Position de la bestiole et de la ligne de sol, en part de l'écran. */
        private const val CAMERA_LEFT = .36f
        private const val GROUND_LINE = .72f
        private const val PARALLAX = .25f
        /** Épaisseur de la bande du dessus du sol, et pas des festons, en mètres. */
        private const val BAND = .14f
        private const val SCALLOP_STEP = .22f
        /** Profondeur des cuvettes d'eau sous la surface, et retrait des rives vers le fond, en mètres. */
        private const val BASIN = .5f
        private const val BANK_INSET = .1f
        /** Rayon des angles arrondis du sol. */
        private const val CORNER = .12f
        private const val TUFT_STEP = .55f
        /** Le bas du sol, assez loin pour sortir de l'écran. */
        private const val DEEP = 40f
        private val MEADOW_GROUND = intArrayOf(0xFF8BC85C.toInt(), 0xFF4F9447.toInt(), 0xFFC8F08A.toInt(), 0xFFA6DD6C.toInt(), 0xFF6DB54A.toInt())
        private val MARSH_GROUND = intArrayOf(0xFF7FA867.toInt(), 0xFF3F6B4C.toInt(), 0xFFC2DE8C.toInt(), 0xFF9CC46E.toInt(), 0xFF6F8F45.toInt())
        private val ICE_GROUND = intArrayOf(0xFFE4F2FB.toInt(), 0xFFA9CBE6.toInt(), 0xFFFFFFFF.toInt(), 0xFFF4FAFF.toInt(), 0xFFFFFFFF.toInt())
        private const val LOG_THICKNESS = .2f
        private const val PLANK_THICKNESS = .09f
        private const val WIND_STREAKS = 14
        private const val PI_F = 3.1415927f
    }
}
