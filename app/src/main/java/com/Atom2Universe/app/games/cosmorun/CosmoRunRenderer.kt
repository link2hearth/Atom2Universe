package com.Atom2Universe.app.games.cosmorun

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

/** Dessine la scène de [CosmoRunScene] sur un Canvas : ciel, planète, faces, halos et lignes de vitesse. */
class CosmoRunRenderer {
    companion object {
        val SUIT_COLORS get() = CosmoRunScene.SUIT_COLORS
        private const val INK = CosmoRunScene.INK
        private const val ORANGE = 0xFFFFB45E.toInt()
    }

    val scene = CosmoRunScene()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val stars = FloatArray(240).also { a ->
        val r = Random(71)
        for (i in a.indices) a[i] = r.nextFloat()
    }
    private var sky: Shader? = null
    private var glow: Shader? = null
    private var lastTheme = -1
    private var lastWidth = 0
    private var lastHeight = 0
    private var w = 1f
    private var h = 1f
    private var horizon = 1f
    var suit
        get() = scene.suit
        set(value) { scene.suit = value }
    var bottomInset
        get() = scene.bottomInset
        set(value) { scene.bottomInset = value }

    fun playerScreenX(game: CosmoRunGame) = scene.playerScreenX(game)
    fun playerScreenY(game: CosmoRunGame) = scene.playerScreenY(game)

    fun draw(canvas: Canvas, width: Int, height: Int, game: CosmoRunGame, clock: Float, demo: Boolean) {
        scene.frame(width, height, game, clock)
        w = scene.w; h = scene.h; horizon = scene.horizon
        val theme = scene.theme
        val accent = scene.accent
        if (width != lastWidth || height != lastHeight || theme != lastTheme) {
            lastWidth = width; lastHeight = height; lastTheme = theme
            val top = when (theme) { 1 -> 0xFF21182E.toInt(); 2 -> 0xFF191637.toInt(); else -> 0xFF071729.toInt() }
            sky = LinearGradient(0f, 0f, 0f, h, intArrayOf(INK, top, 0xFF25314A.toInt(), INK),
                floatArrayOf(0f, .25f, .48f, 1f), Shader.TileMode.CLAMP)
            glow = RadialGradient(w * .57f, horizon, w * .7f,
                intArrayOf(scene.tint(accent, .55f), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        }
        paint.shader = sky; canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = glow; canvas.drawRect(0f, 0f, w, h * .8f, paint); paint.shader = null
        drawSpace(canvas, theme)
        val travel = if (demo) clock * 10f else game.distance
        // Le sol forme une couche dédiée : un grand panneau proche ne doit pas recouvrir
        // le personnage à cause du tri par profondeur moyenne de ses sommets.
        scene.begin(); scene.buildDeck(travel); scene.sortFarFirst(); drawFaces(canvas)
        scene.begin(); scene.buildWorld(game, demo, travel); scene.sortFarFirst(); drawFaces(canvas)
        // Visière de protection : anneau léger, le personnage reste lisible.
        if (!demo && (game.shield || game.boostTime > 0f)) {
            val x = playerScreenX(game); val y = playerScreenY(game); val s = scene.scale(0f)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = max(1.5f, w * .004f)
            paint.color = if (game.boostTime > 0f) ORANGE else 0x7054E5E0
            canvas.drawOval(x - s * .65f, y - s * 1.02f, x + s * .65f, y + s * .92f, paint)
            paint.style = Paint.Style.FILL
        }
        // Lignes de vitesse : elles n'apparaissent qu'au-delà de 21 m/s et rayonnent depuis l'horizon.
        val fast = ((game.speed - 21f) / 9f).coerceIn(0f, 1f)
        if (!demo && fast > 0f) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = max(1.5f, w * .0035f)
            paint.color = 0xFFCFE8FF.toInt(); paint.alpha = (70f * fast).toInt()
            for (i in 0..13) {
                val side = if (i % 2 == 0) -1f else 1f
                val f = (clock * 2.2f + i * .137f) % 1f
                val x0 = w * .5f + side * w * (.14f + f * .5f); val y0 = horizon + f * f * h * .8f
                val x1 = w * .5f + side * w * (.14f + (f + .09f) * .5f); val y1 = horizon + (f + .09f) * (f + .09f) * h * .8f
                canvas.drawLine(x0, y0, x1, y1, paint)
            }
            paint.alpha = 255; paint.style = Paint.Style.FILL
        }
        if (game.boostTime > 0f) {
            paint.color = 0x70FFCE88; paint.strokeWidth = 2f
            for (i in 0..15) {
                val f = ((clock * 1.8f + i * .071f) % 1f)
                val side = if (i % 2 == 0) -1f else 1f
                val x = w * .5f + side * w * (.3f + f * .3f)
                canvas.drawLine(x, horizon + f * h, x + side * w * .06f, horizon + f * h + h * .09f, paint)
            }
        }
    }

    private fun drawSpace(c: Canvas, theme: Int) {
        for (i in 0 until 80) {
            val x = stars[i * 3] * w
            val y = stars[i * 3 + 1] * h * .52f
            paint.color = Color.argb((100 + 100 * stars[i * 3 + 2]).toInt(), 201, 223, 255)
            c.drawCircle(x, y, .8f + stars[i * 3 + 2] * w * .003f, paint)
        }
        val radius = min(w * .22f, h * .18f)
        val cx = w * .77f; val cy = horizon * .66f
        paint.color = when (theme) { 1 -> 0xFFB47A67.toInt(); 2 -> 0xFF8276B2.toInt(); else -> 0xFF457A96.toInt() }
        c.drawCircle(cx, cy, radius, paint)
        c.save(); path.rewind(); path.addCircle(cx, cy, radius, Path.Direction.CW); c.clipPath(path)
        paint.color = 0x284CE1E0
        for (i in 0..6) c.drawOval(cx - radius * 1.4f, cy - radius + i * radius * .32f,
            cx + radius * 1.4f, cy - radius * .7f + i * radius * .32f, paint)
        paint.color = 0xA0081028.toInt()
        c.drawCircle(cx - radius * .5f, cy - radius * .14f, radius * 1.1f, paint)
        c.restore()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = radius * .065f; paint.color = 0x607AD6E8
        c.save(); c.rotate(-24f, cx, cy)
        c.drawOval(cx - radius * 1.5f, cy - radius * .26f, cx + radius * 1.5f, cy + radius * .26f, paint)
        c.restore(); paint.style = Paint.Style.FILL
    }

    private fun drawFaces(canvas: Canvas) {
                for (face in scene.visible) {
            path.rewind(); path.moveTo(face.xy[0], face.xy[1])
            for (i in 1 until face.count) path.lineTo(face.xy[i * 2], face.xy[i * 2 + 1])
            path.close()
            paint.color = face.color; paint.style = Paint.Style.FILL
            canvas.drawPath(path, paint)
        }
    }
}
