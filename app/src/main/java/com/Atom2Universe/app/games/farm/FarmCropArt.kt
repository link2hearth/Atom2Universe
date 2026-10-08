package com.Atom2Universe.app.games.farm

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Procedural crop rendering ported from the ChatGPT-provided design prototypes in
 * `assets/My Farm/ma-ferme-cultures*.html`, one crop at a time - drawn straight into the field
 * cell instead of sampled from a sprite sheet. [FarmSprites.crop] dispatches here for any crop
 * this object knows how to draw, so the field, the harvest minigame, the shop and inventory
 * previews and the produce icon all pick it up automatically: they never draw a crop any other way.
 */
object FarmCropArt {
    private val paint = Paint().apply { isAntiAlias = false }

    /** Same bit-mixing hash as the prototype's `hash(s,k)`, so a given seed always looks the same. */
    private fun hash(seed: Int, k: Int): Float {
        var n = (seed + 13) * (k + 713) xor 0x68bc21eb.toInt()
        n = (n xor (n ushr 16)) * 0x045d9f3b
        val mixed = n xor (n ushr 16)
        return (mixed.toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
    private fun smooth(a: Float, b: Float, g: Float): Float {
        val p = ((g - a) / (b - a)).coerceIn(0f, 1f)
        return p * p * (3 - 2 * p)
    }
    private fun poly(canvas: Canvas, points: List<FloatArray>, color: Int) {
        paint.style = Paint.Style.FILL; paint.color = color
        val path = Path().apply {
            moveTo(points[0][0], points[0][1])
            for (i in 1 until points.size) lineTo(points[i][0], points[i][1])
            close()
        }
        canvas.drawPath(path, paint)
    }
    private fun line(canvas: Canvas, x: Float, y: Float, x2: Float, y2: Float, width: Float, color: Int) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.strokeCap = Paint.Cap.BUTT
        paint.color = color; canvas.drawLine(x, y, x2, y2, paint)
        paint.style = Paint.Style.FILL
    }
    private fun oval(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        if (rx < .3f || ry < .3f) return
        paint.style = Paint.Style.FILL; paint.color = color
        canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint)
    }
    private fun rect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        if (w <= 0f || h <= 0f) return
        paint.style = Paint.Style.FILL; paint.color = color
        canvas.drawRect(x, y, x + w, y + h, paint)
    }
    /** A pointed, slightly toothed leaf with a lighter upper plane and a visible midrib. */
    private fun leaf(canvas: Canvas, x: Float, y: Float, dx: Float, dy: Float, width: Float, pal: IntArray) {
        val len = hypot(dx, dy); if (len < 1f) return
        val nx = -dy / len * width; val ny = dx / len * width
        fun p(u: Float, v: Float) = floatArrayOf(x + dx * u + nx * v, y + dy * u + ny * v)
        poly(canvas, listOf(p(0f, 0f), p(.25f, -.65f), p(.38f, -.52f), p(.49f, -1f), p(.62f, -.65f), p(.76f, -.72f),
            p(1f, 0f), p(.74f, .65f), p(.62f, .52f), p(.45f, .9f), p(.3f, .5f)), pal[0])
        poly(canvas, listOf(p(.1f, 0f), p(.3f, -.4f), p(.5f, -.75f), p(.74f, -.4f), p(.95f, 0f), p(.55f, .37f), p(.3f, .32f)), pal[1])
        poly(canvas, listOf(p(.15f, 0f), p(.52f, -.55f), p(.85f, -.2f), p(.6f, 0f)), pal[2])
        val a = p(.08f, 0f); val b = p(.88f, 0f)
        line(canvas, a[0], a[1], b[0], b[1], 1f, pal[2])
    }
    private fun withAlpha(color: Int, fraction: Float): Int {
        val a = (255f * fraction).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }
    /**
     * A small patch of freshly turned earth, visible the instant a seed goes in - before that, an
     * empty cell reads as "nothing was planted", not as "not sprouted yet". Unlike the plant's own
     * growth curves it fades out completely and quickly: it is a planting cue, not a permanent
     * fixture, so it must be gone well before the plant has anything worth looking at instead - and
     * it stays centred on the ground line rather than spreading further below it than above it, so
     * it never spills past the bed into the row below.
     */
    private fun soilMound(canvas: Canvas, x: Float, y: Float, scale: Float, g: Float) {
        val fade = 1f - smooth(0f, .12f, g)
        if (fade <= 0f) return
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(Color.parseColor("#5c4128"), fade)
        canvas.drawOval(x - 15f * scale, y - 3f * scale, x + 15f * scale, y + 3f * scale, paint)
        paint.color = withAlpha(Color.parseColor("#3a2814"), fade)
        canvas.drawOval(x - 9f * scale, y - 1.5f * scale, x + 9f * scale, y + 1.5f * scale, paint)
        paint.color = withAlpha(Color.parseColor("#7a5a35"), fade)
        canvas.drawRect(x - 6f * scale, y - 2.4f * scale, x + 6f * scale, y - 1.2f * scale, paint)
    }
    /** The very first growth stage: a bare stem with two tiny cotyledon leaves. */
    private fun sprout(canvas: Canvas, x: Float, y: Float, g: Float, pal: IntArray, scale: Float) {
        val a = smooth(.025f, .19f, g); if (a <= 0f) return
        val h = 9f * a * scale
        line(canvas, x, y, x, y - h, 1f, pal[0])
        leaf(canvas, x, y - h + scale, -7f * a * scale, -3f * a * scale, 2f * a * scale, pal)
        leaf(canvas, x, y - h, 6f * a * scale, -4f * a * scale, 2f * a * scale, pal)
    }
    private val GREENS = arrayOf(
        intArrayOf(Color.parseColor("#21573d"), Color.parseColor("#398949"), Color.parseColor("#69b654"), Color.parseColor("#b7df87")),
        intArrayOf(Color.parseColor("#205c47"), Color.parseColor("#3c9460"), Color.parseColor("#79bf76"), Color.parseColor("#c5e8a0")),
        intArrayOf(Color.parseColor("#305e35"), Color.parseColor("#4c9444"), Color.parseColor("#88bd55"), Color.parseColor("#d0e695")),
        intArrayOf(Color.parseColor("#20553f"), Color.parseColor("#318551"), Color.parseColor("#68b36a"), Color.parseColor("#b4df98"))
    )
    private val RADISH_BULB = intArrayOf(
        Color.parseColor("#e45f85"), Color.parseColor("#ee7093"), Color.parseColor("#d95d8b"), Color.parseColor("#ed7c91")
    )

    /**
     * [stage] is the same 0..4 growth index the sprite sheet used (5 snapshots): mapped to a
     * continuous 0..1 fraction because the growth formulas below - lifted straight from the
     * prototype - expect a smooth timeline, not a stage index. [variant] only ever comes in as 0
     * or 1 in this game (`FarmPlot.variant`), unlike the prototype's four-plant row, so only two of
     * its four seeds/palettes are ever seen in practice; both still exist here so nothing breaks if
     * a wider variant range is ever introduced.
     */
    fun radish(canvas: Canvas, variant: Int, stage: Int, target: RectF) {
        // stage 0 must land at g=0: the sprout only starts appearing past g=.025 and the first leaf
        // past g=.06, so anything higher than that already shows a part-grown plant right at planting.
        val g = (stage / 4f).coerceIn(0f, 1f)
        val seed = 100 + variant * 17
        val pal = GREENS[((variant % GREENS.size) + GREENS.size) % GREENS.size]
        val scale = target.height() / 46f
        val x = target.centerX(); val y = target.bottom
        soilMound(canvas, x, y, scale, g)
        sprout(canvas, x, y, g, pal, scale)
        val a = smooth(.35f, .95f, g)
        val w = (6f + hash(seed, 41) * 3f) * a * scale
        val h = (6f + hash(seed, 42) * 3f) * a * scale
        if (a > 0f) {
            oval(canvas, x, y - 2f * scale, w, h, Color.parseColor("#983e58"))
            oval(canvas, x - .7f * scale, y - 3f * scale, w * .86f, h * .82f, RADISH_BULB[((seed % 4) + 4) % 4])
            oval(canvas, x - w * .32f, y - 5f * scale, w * .42f, h * .45f, Color.parseColor("#ffafbf"))
            rect(canvas, x - w * .42f, y - 5f * scale, 2f * a * scale, 1f * scale, Color.parseColor("#ffd3d7"))
            oval(canvas, x, y + 2f * scale, w * .58f, 2f * a * scale, Color.parseColor("#fff0d4"))
            rect(canvas, x - 2f * scale, y + 3f * scale, 4f * scale, 2f * scale, Color.parseColor("#a47b55"))
        }
        for (i in 0 until 5) {
            val b = smooth(.06f + i * .035f, .45f + i * .075f, g); if (b <= 0f) continue
            val angle = -2.65f + i * .53f + (hash(seed, i + 71) - .5f) * .14f
            val length = (23f + hash(seed, i + 81) * 9f) * b
            val dx = cos(angle) * length * scale; val dy = sin(angle) * length * scale
            line(canvas, x, y - 5f * scale, x + dx * .48f, y - 5f * scale + dy * .48f, 1f, pal[1])
            leaf(canvas, x + dx * .24f, y - 5f * scale + dy * .24f, dx * .8f, dy * .8f, (5f + hash(seed, i + 91) * 2f) * b * scale, pal)
        }
    }
}
