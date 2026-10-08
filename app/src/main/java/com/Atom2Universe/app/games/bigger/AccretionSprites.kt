package com.Atom2Universe.app.games.bigger

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import com.Atom2Universe.app.science.StellarSurfaceCpu
import com.Atom2Universe.app.science.cosmicscale.CosmicScaleData
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cuit les images des astres d'Accrétion avec les outils du hub science : les vraies cartes
 * des planètes (celles du système solaire et de l'échelle cosmique) projetées sur une
 * sphère, et la photosphère de [StellarSurfaceCpu] pour les étoiles.
 *
 * Chaque astre donne **deux images** :
 * - [surface], ce qui roule : la carte ou la photosphère ;
 * - [lighting], ce qui ne roule pas : l'ombre propre d'une lumière fixe en haut à gauche.
 *
 * Tourner une image éclairée ferait tourner le soleil avec la planète. Ici l'ombre est un
 * voile noir dont l'opacité vaut `1 − éclairement` : poser du noir à 40 % multiplie
 * exactement la couleur par 0,6, ce qui est le calcul du shader de planète.
 *
 * Tout se fait pixel par pixel dans un tableau d'entiers puis `setPixels` : des centaines
 * de milliers de points, qu'aucun appel de dessin ne ferait à ce prix.
 */
class AccretionSprites(context: Context) {

    private val assets: AssetManager = context.applicationContext.assets

    /** Une carte équirectangulaire, avec ses réductions successives pour les petits astres. */
    private class Texture(val levels: List<Level>) {
        class Level(val w: Int, val h: Int, val px: IntArray)

        /** Le niveau le plus petit qui garde un texel par pixel à l'équateur. */
        fun levelFor(radiusPx: Float): Level {
            val need = 2f * PI.toFloat() * radiusPx
            return levels.lastOrNull { it.w >= need } ?: levels.first()
        }
    }

    private companion object {
        val textures = HashMap<String, Texture?>()

        /** Lumière fixe, en repère écran (y vers le bas) : haut-gauche, vers le regard. */
        val LX: Float; val LY: Float; val LZ: Float
        init {
            val n = sqrt(0.45f * 0.45f + 0.55f * 0.55f + 0.7f * 0.7f)
            LX = -0.45f / n; LY = -0.55f / n; LZ = 0.7f / n
        }
    }

    // ── Textures ─────────────────────────────────────────────────────────────

    private fun texture(asset: String): Texture? = synchronized(textures) {
        textures.getOrPut(asset) {
            try {
                val opts = BitmapFactory.Options().apply { inScaled = false; inSampleSize = 1 }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                assets.open(asset).use { BitmapFactory.decodeStream(it, null, bounds) }
                while (bounds.outWidth / opts.inSampleSize > 1024) opts.inSampleSize *= 2
                var bmp = assets.open(asset).use { BitmapFactory.decodeStream(it, null, opts) } ?: return@getOrPut null
                val levels = ArrayList<Texture.Level>()
                while (true) {
                    val px = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    levels.add(Texture.Level(bmp.width, bmp.height, px))
                    if (bmp.width <= 64 || bmp.height <= 8) break
                    val next = Bitmap.createScaledBitmap(bmp, bmp.width / 2, max(1, bmp.height / 2), true)
                    bmp.recycle(); bmp = next
                }
                bmp.recycle()
                Texture(levels)
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Lecture bilinéaire, bouclée en longitude, bornée en latitude. */
    private fun sample(l: Texture.Level, u: Float, v: Float): Int {
        val x = u * l.w - 0.5f
        val y = (v * l.h - 0.5f).coerceIn(0f, l.h - 1f)
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val fx = x - x0; val fy = y - y0
        val xa = Math.floorMod(x0, l.w); val xb = Math.floorMod(x0 + 1, l.w)
        val ya = y0; val yb = min(y0 + 1, l.h - 1)
        val c00 = l.px[ya * l.w + xa]; val c10 = l.px[ya * l.w + xb]
        val c01 = l.px[yb * l.w + xa]; val c11 = l.px[yb * l.w + xb]
        fun ch(shift: Int): Int {
            val a = ((c00 shr shift) and 0xFF) * (1 - fx) + ((c10 shr shift) and 0xFF) * fx
            val b = ((c01 shr shift) and 0xFF) * (1 - fx) + ((c11 shr shift) and 0xFF) * fx
            return (a * (1 - fy) + b * fy).toInt()
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    // ── Outils de pixels ─────────────────────────────────────────────────────

    private fun argb(a: Int, rgb: Int) = (a.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    private fun scaleRgb(c: Int, f: Float): Int {
        fun ch(s: Int) = (((c shr s) and 0xFF) * f).toInt().coerceIn(0, 255)
        return (c and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun tintRgb(c: Int, tint: Int, amount: Float): Int {
        val luma = (((c shr 16) and 0xFF) * 0.3f + ((c shr 8) and 0xFF) * 0.59f + (c and 0xFF) * 0.11f) / 255f
        fun ch(s: Int): Int {
            val orig = (c shr s) and 0xFF
            val t = ((tint shr s) and 0xFF) * luma
            return (orig + (t - orig) * amount).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** « a par-dessus b », couleurs non prémultipliées. */
    private fun over(a: Int, b: Int): Int {
        val aa = ((a ushr 24) and 0xFF) / 255f
        if (aa >= 1f) return a
        val ba = ((b ushr 24) and 0xFF) / 255f
        val oa = aa + ba * (1 - aa)
        if (oa <= 0f) return 0
        fun ch(s: Int) = ((((a shr s) and 0xFF) * aa + ((b shr s) and 0xFF) * ba * (1 - aa)) / oa).toInt().coerceIn(0, 255)
        return ((oa * 255).toInt() shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun whiten(c: Int, amount: Float): Int {
        fun ch(s: Int) = (((c shr s) and 0xFF) + (255 - ((c shr s) and 0xFF)) * amount).toInt().coerceIn(0, 255)
        return (c and 0xFF000000.toInt()) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * La teinte « glace » que l'échelle cosmique pose sur Neptune (`uIce = 2` dans son shader
     * de planète) : la carte ne garde que ses reliefs, la couleur devient un bleu profond.
     */
    private fun iceGiant(c: Int): Int {
        val luma = (((c shr 16) and 0xFF) * 0.2126f + ((c shr 8) and 0xFF) * 0.7152f + (c and 0xFF) * 0.0722f) / 255f
        val detail = (0.94f + (luma - 0.5f) * 0.75f).coerceIn(0.62f, 1.18f)
        fun ch(v: Float) = (v * detail * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(0.48f) shl 16) or (ch(0.70f) shl 8) or ch(0.86f)
    }

    private fun half(tier: Int, r: Float) = ceil(r * AccretionPainter.GLOW[tier]).toInt() + 2

    private fun toBitmap(px: IntArray, size: Int): Bitmap {
        // Créée modifiable puis remplie : une image tirée d'un tableau est immuable, et le
        // canevas qui la retouche ensuite (ombre des vignettes, tuile) planterait dessus.
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        return bmp
    }

    /**
     * Une sphère texturée vue de face. [outline] donne, pour un angle, le rayon du contour en
     * fraction de [r] : 1 pour une sphère, des bosses pour un caillou. [lit] cuit l'éclairage
     * dans l'image — seulement pour les formes irrégulières, que le voile rond ne suit pas.
     */
    private fun sphere(
        px: IntArray, size: Int, r: Float, asset: String, phase: Float,
        tint: Int = 0, tintAmount: Float = 0f, outline: ((Float) -> Float)? = null, lit: Boolean = false,
        surfaceTone: ((Int) -> Int)? = null
    ) {
        val tex = texture(asset)
        val level = tex?.levelFor(r)
        val c = size / 2f
        for (y in 0 until size) for (x in 0 until size) {
            val dx = (x + 0.5f - c) / r; val dy = (y + 0.5f - c) / r
            val d = sqrt(dx * dx + dy * dy)
            val edge = outline?.invoke(atan2(dy, dx)) ?: 1f
            val coverage = ((edge - d) * r + 0.5f).coerceIn(0f, 1f)
            if (coverage <= 0f) continue
            val nx = (dx / edge).coerceIn(-1f, 1f); val ny = (dy / edge).coerceIn(-1f, 1f)
            val nz = sqrt(max(0f, 1f - nx * nx - ny * ny))
            var color = if (level == null) 0xFF888888.toInt() else {
                val lat = asin(-ny)
                val lon = atan2(nx, nz) + phase
                val u = (lon / (2f * PI.toFloat()) + 0.5f).let { it - floor(it) }
                sample(level, u, 0.5f - lat / PI.toFloat())
            }
            if (tintAmount > 0f) color = tintRgb(color, tint, tintAmount)
            if (surfaceTone != null) color = surfaceTone(color)
            if (lit) color = scaleRgb(color, 0.16f + 0.84f * max(0f, nx * LX + ny * LY + nz * LZ))
            px[y * size + x] = over(argb((coverage * 255).toInt(), color), px[y * size + x])
        }
    }

    /** Contour bosselé et déterministe pour les petits corps. */
    private fun lumps(seed: Int, depth: Float): (Float) -> Float {
        val phases = FloatArray(4) { ((seed * 131 + it * 71) % 360) * PI.toFloat() / 180f }
        return { a ->
            1f - depth * (0.5f + 0.2f * sin(3f * a + phases[0]) + 0.15f * sin(5f * a + phases[1]) +
                0.1f * sin(7f * a + phases[2]) + 0.05f * sin(11f * a + phases[3]))
        }
    }

    // ── Les astres ───────────────────────────────────────────────────────────

    /** Ce qui roule avec l'astre. */
    fun surface(tier: Int, r: Float): Bitmap {
        val half = half(tier, r)
        val size = half * 2
        return when (tier) {
            0, Accretion.BLACK_HOLE -> painted(tier, r, size)
            1 -> textured(size) { sphere(it, size, r, "textures/planets/mercury.jpg", 1.2f, 0xFFA0A4B4.toInt(), 0.4f, lumps(1, 0.16f), lit = true) }
            2 -> textured(size) { sphere(it, size, r, "textures/moon.jpg", 2.4f, 0xFFB89470.toInt(), 0.6f, lumps(2, 0.1f), lit = true) }
            3 -> textured(size) { sphere(it, size, r, "textures/moon.jpg", 0f) }
            4 -> textured(size) { sphere(it, size, r, "textures/planets/mars.jpg", 0.8f) }
            5 -> textured(size) { sphere(it, size, r, "textures/earth.jpg", -1.4f) }
            6 -> textured(size) { sphere(it, size, r, "textures/planets/neptune.jpg", 0.9f, surfaceTone = ::iceGiant) }
            7 -> textured(size) { sphere(it, size, r, "textures/planets/jupiter.jpg", 0.6f) }
            // Les taches solaires de l'échelle cosmique sont faites pour un Soleil qui remplit
            // l'écran : à la taille d'un astre du bocal, elles tachetaient l'étoile comme un
            // léopard. On garde la granulation, plus contrastée, et pas de taches.
            8 -> star(r, size, CosmicScaleData.tempToRgb(2_700.0), StellarSurfaceCpu.Pattern(9f, 0.5f, 0f, 1.7f), AccretionPainter.GLOW[8])
            9 -> star(r, size, 0xFFFFD66A.toInt(), StellarSurfaceCpu.Pattern(10f, 0.45f, 0f, 4.3f), AccretionPainter.GLOW[9])
            else -> star(r, size, CosmicScaleData.tempToRgb(15_000.0), StellarSurfaceCpu.Pattern(11f, 0.3f, 0f, 7.9f), AccretionPainter.GLOW[10])
        }
    }

    /**
     * L'ombre fixe, posée par-dessus sans tourner. Rien pour ce qui brille, ni pour ce dont
     * l'éclairage est déjà cuit dans la surface.
     */
    fun lighting(tier: Int, r: Float): Bitmap? {
        if (tier !in 3..7) return null
        val half = half(tier, r)
        val size = half * 2
        val body = r
        val px = IntArray(size * size)
        val c = size / 2f
        val atmosphere = when (tier) { 4 -> 0xFFFFB08A.toInt(); 5 -> 0xFF7FD4FF.toInt(); 6 -> 0xFF8FC8FF.toInt(); 7 -> 0xFFFFD8A8.toInt(); else -> 0 }
        val reach = AccretionPainter.GLOW[tier]
        for (y in 0 until size) for (x in 0 until size) {
            val dx = (x + 0.5f - c) / body; val dy = (y + 0.5f - c) / body
            val d = sqrt(dx * dx + dy * dy)
            var out = 0
            if (atmosphere != 0 && d > 0.9f && d < reach) {
                // Liseré d'atmosphère, plus vif du côté éclairé.
                val side = 0.35f + 0.65f * max(0f, -(dx * 0.6f + dy * 0.8f) / max(d, 1e-3f))
                val t = if (d < 1f) (d - 0.9f) / 0.1f else 1f - (d - 1f) / (reach - 1f)
                out = argb((t.coerceIn(0f, 1f).pow(1.5f) * 150f * side).toInt(), atmosphere)
            }
            val coverage = ((1f - d) * body + 0.5f).coerceIn(0f, 1f)
            if (coverage > 0f) {
                val nx = dx.coerceIn(-1f, 1f); val ny = dy.coerceIn(-1f, 1f)
                val nz = sqrt(max(0f, 1f - nx * nx - ny * ny))
                val light = 0.16f + 0.84f * max(0f, nx * LX + ny * LY + nz * LZ)
                out = over(out, argb(((1f - light) * 255f * coverage).toInt(), 0))
            }
            px[y * size + x] = out
        }
        return toBitmap(px, size)
    }

    /** La surface et son ombre réunies, pour ce qui ne roule pas : frise, hublot, tuile du hub. */
    fun composite(tier: Int, r: Float): Bitmap {
        val bmp = surface(tier, r)
        lighting(tier, r)?.let { Canvas(bmp).drawBitmap(it, 0f, 0f, null); it.recycle() }
        return bmp
    }

    private inline fun textured(size: Int, fill: (IntArray) -> Unit): Bitmap {
        val px = IntArray(size * size)
        fill(px)
        return toBitmap(px, size)
    }

    private fun painted(tier: Int, r: Float, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        AccretionPainter.paint(Canvas(bmp), tier, size / 2f, size / 2f, r)
        return bmp
    }

    /** Photosphère de l'échelle cosmique, et sa couronne douce (même profil que son halo). */
    private fun star(r: Float, size: Int, tint: Int, pattern: StellarSurfaceCpu.Pattern, reach: Float): Bitmap {
        val px = IntArray(size * size)
        val c = size / 2f
        for (y in 0 until size) for (x in 0 until size) {
            val dx = (x + 0.5f - c) / r; val dy = (y + 0.5f - c) / r
            val d = sqrt(dx * dx + dy * dy)
            if (d >= reach) continue
            // La couronne : le GL de l'échelle cosmique l'ajoute en mélange additif, qui
            // éclaire le fond ; une image posée par-dessus ne peut que le recouvrir, il lui
            // faut donc une couronne plus franche pour se voir autant.
            val glowT = ((reach - d) / (reach - 1f)).coerceIn(0f, 1f)
            var color = argb((glowT * glowT * 190f).toInt(), tint)
            val coverage = ((1f - d) * r + 0.5f).coerceIn(0f, 1f)
            if (coverage > 0f) {
                val nx = dx.coerceIn(-1f, 1f); val ny = dy.coerceIn(-1f, 1f)
                val nz = sqrt(max(0f, 1f - nx * nx - ny * ny))
                // Cœur plus chaud que le bord : une étoile éblouit en son centre.
                val surface = whiten(StellarSurfaceCpu.color(nx, -ny, nz, nz, tint, pattern), 0.45f * nz * nz)
                color = over(argb((coverage * 255).toInt(), surface), color)
            }
            px[y * size + x] = color
        }
        return toBitmap(px, size)
    }
}
