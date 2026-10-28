package com.Atom2Universe.app.games.papattes

import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pose d'une bestiole à l'écran, en pixels : le centre du torse, la hanche, l'angle du corps et les
 * angles absolus des cuisses et des tibias (indice 0 = jambe avant, 1 = jambe arrière). Les angles
 * sont ceux du monde physique (radians, sens trigonométrique, 0 = jambe pendante).
 */
class PapattesPose {
    var torsoX = 0f; var torsoY = 0f
    var hipX = 0f; var hipY = 0f
    var bodyAngle = 0f
    val thigh = FloatArray(2)
    val shin = FloatArray(2)
    /** Pixels par mètre. */
    var ppm = 100f

    /** La pose debout en « A », hanche en (hx, hy) : celle du Labo et du menu. */
    fun standing(critter: Critter, hx: Float, hy: Float, scale: Float, spread: Float = PapattesWalker.START_SPREAD) {
        ppm = scale
        hipX = hx; hipY = hy
        val r = bodyRadius(critter.body)
        torsoX = hx; torsoY = hy - r * HIP_DROP * scale
        bodyAngle = 0f
        thigh[0] = spread; shin[0] = spread
        thigh[1] = -spread; shin[1] = -spread
    }

    companion object {
        /** Rayon du torse (le même pour la physique, voir [PapattesWalker]) et décalage de la hanche. */
        fun bodyRadius(body: Body) = when (body.weight) { Weight.LIGHT -> .2f; Weight.MEDIUM -> .23f; Weight.HEAVY -> .26f }
        const val HIP_DROP = .55f
    }
}

/** Dessine une bestiole à partir de ses sprites, en alignant les points d'accroche sur la pose. */
internal class PapattesArt(private val sprites: PapattesSprites) {
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shaded = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = LightingColorFilter(0xFFB4B0B8.toInt(), 0)
    }
    private val point = FloatArray(2)

    fun critter(canvas: Canvas, critter: Critter, pose: PapattesPose, alpha: Int = 255) {
        paint.alpha = alpha
        shaded.alpha = alpha
        val legs = critter.legs
        leg(canvas, legs, pose, 1, shaded)
        body(canvas, critter, pose)
        leg(canvas, legs, pose, 0, paint)
        head(canvas, critter, pose)
    }

    private fun leg(canvas: Canvas, legs: Legs, pose: PapattesPose, i: Int, p: Paint) {
        val s = pose.ppm
        val kx = pose.hipX + sin(pose.thigh[i]) * legs.thigh * s
        val ky = pose.hipY + cos(pose.thigh[i]) * legs.thigh * s
        segment(canvas, sprites.shins[legs.ordinal], "ankle", kx, ky, pose.shin[i], (legs.shin + legs.footRadius) * s, p)
        segment(canvas, sprites.thighs[legs.ordinal], "bottom", pose.hipX, pose.hipY, pose.thigh[i], legs.thigh * s, p)
    }

    /** Pose un segment de jambe : son point « top » en (x, y), son axe dans la direction de la jambe. */
    private fun segment(canvas: Canvas, sprite: Sprite, end: String, x: Float, y: Float, angle: Float, length: Float, p: Paint) {
        val t = sprite.anchor("top")
        val b = sprite.anchor(end)
        val ax = b[0] - t[0]
        val ay = b[1] - t[1]
        val axis = hypot(ax, ay).coerceAtLeast(1f)
        // Le monde a l'axe y vers le haut, l'écran vers le bas : la direction (sin, −cos) devient (sin, cos).
        val target = atan2(cos(angle), sin(angle))
        val scale = length / axis
        matrix.reset()
        matrix.postTranslate(-t[0], -t[1])
        matrix.postScale(scale, scale)
        matrix.postRotate(Math.toDegrees((target - atan2(ay, ax)).toDouble()).toFloat())
        matrix.postTranslate(x, y)
        canvas.drawBitmap(sprite.bitmap, matrix, p)
    }

    private fun body(canvas: Canvas, critter: Critter, pose: PapattesPose) {
        val sprite = sprites.bodies[critter.body.ordinal]
        val bmp = sprite.bitmap
        val width = PapattesPose.bodyRadius(critter.body) * BODY_WIDTH * pose.ppm
        val scale = width / bmp.width
        matrix.reset()
        matrix.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
        matrix.postScale(scale, scale)
        matrix.postRotate(-Math.toDegrees(pose.bodyAngle.toDouble()).toFloat())
        matrix.postTranslate(pose.torsoX, pose.torsoY)
        canvas.drawBitmap(bmp, matrix, paint)
        // Le cou du corps, à l'écran, pour y poser la tête.
        val neck = sprite.anchor("neck")
        point[0] = neck[0]; point[1] = neck[1]
        matrix.mapPoints(point)
    }

    private fun head(canvas: Canvas, critter: Critter, pose: PapattesPose) {
        val sprite = sprites.heads[critter.head.ordinal]
        val bmp = sprite.bitmap
        val scale = HEAD_WIDTH * pose.ppm / bmp.width
        val neck = sprite.anchor("neck")
        matrix.reset()
        // La tête s'enfonce un peu dans le corps.
        matrix.postTranslate(-neck[0], -neck[1] + bmp.height * HEAD_OVERLAP)
        matrix.postScale(scale, scale)
        matrix.postRotate(-Math.toDegrees(pose.bodyAngle.toDouble()).toFloat())
        matrix.postTranslate(point[0], point[1])
        canvas.drawBitmap(bmp, matrix, paint)
    }

    companion object {
        /** Largeur dessinée du corps, en rayons physiques. */
        const val BODY_WIDTH = 2.2f
        /** Largeur dessinée de la tête, en mètres. */
        const val HEAD_WIDTH = .44f
        const val HEAD_OVERLAP = .1f

        /** Hauteur totale d'une bestiole debout, en mètres, pour cadrer un portrait. */
        fun standingHeight(critter: Critter): Float =
            critter.legs.length * cos(PapattesWalker.START_SPREAD) + critter.legs.footRadius +
                PapattesPose.bodyRadius(critter.body) * (PapattesPose.HIP_DROP + BODY_WIDTH * .5f) + HEAD_WIDTH * .85f
    }
}
