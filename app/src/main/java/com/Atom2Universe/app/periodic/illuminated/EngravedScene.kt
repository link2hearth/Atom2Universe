package com.Atom2Universe.app.periodic.illuminated

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Une gravure au centre de la carte. Elle vit en deux calques :
 *
 * - [engrave] : la planche fixe, hachurée à loisir, cuite **une fois** dans l'image de la carte ;
 * - [animate] : ce qui bouge, redessiné à chaque image, découpé à la fenêtre de la miniature.
 *
 * Une pièce mobile qui doit rester gravée (un marteau, un ballon) se déclare dans [sprites] :
 * elle est cuite à part, puis posée, tournée ou déplacée à chaque image pour presque rien.
 *
 * Le temps [t] est en secondes, sans bouclage : une animation ne saute jamais.
 */
internal abstract class EngravedScene {
    /** Le ciel gravé (traits horizontaux et lavis bleu) sous la scène. */
    open val sky: Sky = Sky.DAY

    /** Le nom du motif et sa légende, pour l'atelier et l'accessibilité. */
    abstract val nameRes: Int
    abstract val noteRes: Int

    /** Ce que montre l'image et ce qui y bouge, en mots simples : caché sous la carte, affiché d'un appui. */
    abstract val explainRes: Int

    abstract fun engrave(b: Burin)

    open fun sprites(): List<Sprite> = emptyList()

    open fun animate(c: Canvas, s: Sprites, t: Float) {}

    enum class Sky { DAY, DUSK, NIGHT, NONE }
}

/** Une pièce gravée à part, dessinée dans son propre cadre [box] (coordonnées de la carte). */
internal class Sprite(val id: Int, val box: RectF, val draw: (Burin) -> Unit)

/** Les pièces cuites d'une scène, prêtes à être posées. */
internal class Sprites(private val scale: Float) {
    private val baked = HashMap<Int, Pair<Sprite, Bitmap>>()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun bake(list: List<Sprite>) {
        release()
        for (s in list) {
            val w = (s.box.width() * scale).toInt().coerceAtLeast(1)
            val h = (s.box.height() * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(w / s.box.width(), h / s.box.height())
            c.translate(-s.box.left, -s.box.top)
            s.draw(Burin(c))
            baked[s.id] = s to bmp
        }
    }

    /** Pose la pièce à sa place d'origine, décalée de ([dx], [dy]) et tournée de [deg] autour de ([px], [py]). */
    fun draw(c: Canvas, id: Int, dx: Float = 0f, dy: Float = 0f, deg: Float = 0f, px: Float = 0f, py: Float = 0f, alpha: Int = 255) {
        val (sprite, bmp) = baked[id] ?: return
        c.save()
        c.translate(dx, dy)
        if (deg != 0f) c.rotate(deg, px, py)
        paint.alpha = alpha
        c.drawBitmap(bmp, null, sprite.box, paint)
        c.restore()
    }

    /** Pas de `recycle()` : le fil de rendu peut encore tenir l'image ; le ramasse-miettes s'en charge. */
    fun release() = baked.clear()
}
