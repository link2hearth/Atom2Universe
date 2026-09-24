package com.Atom2Universe.app.science

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Les briques communes des illustrations du hub Sciences : un ciel étoilé, une planète tirée
 * de sa vraie texture, et le bas assombri où se pose le titre.
 */
object ScienceTileArt {

    /** Des étoiles fixes (graine donnée) : la tuile est identique à chaque rendu. */
    fun stars(canvas: Canvas, w: Float, h: Float, count: Int, seed: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val random = Random(seed)
        val unit = min(w, h)
        repeat(count) {
            paint.color = Color.argb(50 + random.nextInt(140), 255, 255, 255)
            canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h,
                unit * (0.0015f + random.nextFloat() * 0.0035f), paint)
        }
    }

    /**
     * Le bas de la tuile, fondu vers [color] : le titre agrandi s'y lit quel que soit le décor.
     * Commence à [from] (fraction de la hauteur).
     */
    fun bottomShade(canvas: Canvas, w: Float, h: Float, color: Int, from: Float = 0.52f) {
        val paint = Paint()
        val clear = color and 0x00FFFFFF
        paint.shader = LinearGradient(0f, h * from, 0f, h,
            intArrayOf(clear, (color and 0x00FFFFFF) or (0xB0 shl 24), (color and 0x00FFFFFF) or (0xE6 shl 24)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * from, w, h, paint)
    }

    /** Un halo lumineux doux, pour un soleil ou une lueur. */
    fun glow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rgb = color and 0x00FFFFFF
        paint.shader = RadialGradient(cx, cy, radius,
            intArrayOf(rgb or (alpha shl 24), rgb or ((alpha / 3) shl 24), rgb),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
    }

    /** Une texture de planète (équirectangulaire), réduite : une tuile n'a pas besoin de plus. */
    class Texture(val width: Int, val height: Int, val pixels: IntArray)

    fun loadTexture(context: Context, asset: String): Texture? = try {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = context.assets.open(asset).use { BitmapFactory.decodeStream(it, null, options) }
        bitmap?.let {
            val pixels = IntArray(it.width * it.height)
            it.getPixels(pixels, 0, it.width, 0, 0, it.width, it.height)
            Texture(it.width, it.height, pixels).also { _ -> it.recycle() }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Une planète en boule : la texture est plaquée sur la sphère pixel par pixel, éclairée
     * depuis ([lightX], [lightY]) (direction dans le plan de l'image, vers la lumière). Sans
     * texture, un disque de [fallback] éclairé de la même façon.
     *
     * [spin] tourne le globe (fraction de tour) pour montrer la face voulue.
     */
    fun planet(
        canvas: Canvas, texture: Texture?, fallback: Int,
        cx: Float, cy: Float, radius: Float,
        lightX: Float, lightY: Float, spin: Float = 0f
    ) {
        val size = max(2, (radius * 2).toInt())
        val r = size / 2f
        val pixels = IntArray(size * size)
        // La direction dans l'image ramenée à 1, puis un peu de lumière de face.
        val flat = sqrt(lightX * lightX + lightY * lightY).coerceAtLeast(1e-6f)
        val len = sqrt(1f + 0.6f * 0.6f)
        val lx = lightX / flat / len
        val ly = lightY / flat / len
        val lz = 0.6f / len
        for (y in 0 until size) {
            val ny = (y + 0.5f - r) / r
            for (x in 0 until size) {
                val nx = (x + 0.5f - r) / r
                val d = nx * nx + ny * ny
                if (d > 1f) continue
                val nz = sqrt(1f - d)
                val base = if (texture != null) {
                    val lon = atan2(nx, nz) / (2 * PI).toFloat() + 0.5f + spin
                    val lat = asin(-ny.coerceIn(-1f, 1f)) / PI.toFloat()
                    val u = ((lon % 1f + 1f) % 1f * texture.width).toInt().coerceIn(0, texture.width - 1)
                    val v = ((0.5f - lat) * texture.height).toInt().coerceIn(0, texture.height - 1)
                    texture.pixels[v * texture.width + u]
                } else fallback
                // Lumière rasante + un peu d'ambiance : la face cachée reste devinable.
                val light = (0.10f + 0.95f * max(0f, nx * lx + ny * ly + nz * lz)).coerceAtMost(1f)
                // Bord légèrement adouci : pas d'escalier sur le contour.
                val edge = ((1f - d) * r).coerceIn(0f, 1f)
                val red = (Color.red(base) * light).toInt()
                val green = (Color.green(base) * light).toInt()
                val blue = (Color.blue(base) * light).toInt()
                pixels[y * size + x] = Color.argb((255 * edge).toInt(), red, green, blue)
            }
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        canvas.drawBitmap(bitmap, cx - r, cy - r, Paint(Paint.FILTER_BITMAP_FLAG))
        bitmap.recycle()
    }
}
