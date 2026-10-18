package com.Atom2Universe.app.games.trebuchet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Les particules : etincelles, fumee, gravats, feux d'artifice.
 *
 * Troisieme peintre partage apres [SkyBackdrop] et [LandScene], et pour la meme raison :
 * une explosion ne ressemble pas plus a un trebuchet qu'a un train d'engrenages. La vue
 * lui tend ses effets et son cadrage, il rend des points.
 *
 * **Le groupage par seau est tout l'interet de la classe.** Un tir en fin de course
 * compte des milliers de particules, et les dessiner une par une revenait a autant
 * d'appels de trace, chacun avec sa mise en place d'anticrenelage. Rangees par teinte,
 * opacite et epaisseur, il n'en reste qu'un appel par combinaison — quelques dizaines au
 * pire — et le cout ne depend plus du nombre de particules mais du nombre de couleurs
 * qu'elles prennent.
 */
class SparkScene(context: Context) {

    private val dp = context.resources.displayMetrics.density

    /** Les points d'un seau : une teinte, une opacité, une épaisseur. */
    private val pSpark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** Les grosses particules, celles qui méritent un vrai disque. */
    private val pBigSpark = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bucketXY = arrayOfNulls<FloatArray>(
        TrebuchetEffects.PALETTE.size * SPARK_ALPHAS * SPARK_SIZES
    )
    private val bucketCount = IntArray(bucketXY.size)

    /**
     * Dessine les particules d'une couche, **groupées par teinte et par taille**.
     *
     * Un bouquet compte un millier d'étoiles. Les dessiner une par une, c'est mille
     * appels de tracé par image, chacun avec sa mise en place d'anticrénelage, pour des
     * points de trois pixels : mesuré ailleurs dans ce fichier, c'est exactement le
     * genre de chose qui fait tomber une image à trente. `drawPoints` en dessine autant
     * qu'on veut d'un seul appel, à condition qu'ils partagent leur couleur et leur
     * épaisseur — on range donc les particules dans des seaux (une teinte, un palier
     * d'opacité, une classe de taille) et on vide chaque seau d'un trait. Un bouquet
     * ordinaire tient dans une dizaine de seaux.
     *
     * Les grosses particules — boules de feu, fumée — sortent du lot : elles sont peu
     * nombreuses et il leur faut un vrai disque, pas un point épais.
     */
    fun draw(
        canvas: Canvas,
        fx: TrebuchetEffects,
        background: Boolean,
        w: Float,
        h: Float,
        camX: Float,
        camY: Float,
        camScale: Float
    ) {
        if (fx.aliveCount == 0) return
        for (i in bucketCount.indices) bucketCount[i] = 0

        for (sp in fx.sparks) {
            if (!sp.alive || sp.background != background) continue
            val px = ((sp.x - camX) * camScale + w / 2f)
            val py = (h / 2f - (sp.y - camY) * camScale)
            if (px < -40f || px > w + 40f || py < -40f || py > h + 40f) continue
            val r = sp.shownSize * camScale
            // Ce qui est gros se dessine rond ; ce qui est petit se dessine en points.
            if (r > BIG_SPARK_DP * dp) {
                pBigSpark.color = tinted(sp)
                canvas.drawCircle(px, py, r, pBigSpark)
                continue
            }
            val size = (r / (SPARK_STEP_DP * dp)).toInt().coerceIn(0, SPARK_SIZES - 1)
            val alpha = (sp.fade * (SPARK_ALPHAS - 1)).toInt().coerceIn(0, SPARK_ALPHAS - 1)
            val b = (sp.tint * SPARK_ALPHAS + alpha) * SPARK_SIZES + size
            var arr = bucketXY[b]
            if (arr == null) {
                arr = FloatArray(256)
                bucketXY[b] = arr
            }
            var n = bucketCount[b]
            if (n + 2 > arr.size) {
                arr = arr.copyOf(arr.size * 2)
                bucketXY[b] = arr
            }
            arr[n++] = px
            arr[n++] = py
            bucketCount[b] = n
        }

        for (b in bucketCount.indices) {
            val n = bucketCount[b]
            if (n == 0) continue
            val size = b % SPARK_SIZES
            val rest = b / SPARK_SIZES
            val alpha = rest % SPARK_ALPHAS
            val tint = rest / SPARK_ALPHAS
            pSpark.color = TrebuchetEffects.PALETTE[tint]
            pSpark.alpha = 40 + 215 * alpha / (SPARK_ALPHAS - 1)
            pSpark.strokeWidth = (size + 1) * SPARK_STEP_DP * dp
            canvas.drawPoints(bucketXY[b]!!, 0, n, pSpark)
        }
    }

    /** La couleur d'une particule, son extinction comprise. */
    private fun tinted(sp: Spark): Int {
        val c = TrebuchetEffects.PALETTE[sp.tint]
        val a = (255 * sp.fade).toInt().coerceIn(0, 255)
        return (c and 0x00FFFFFF) or (a shl 24)
    }

    private companion object {
        /** Nombre de paliers d'opacite pour le groupage des particules. */
        const val SPARK_ALPHAS = 4

        /** Nombre de classes de taille, la plus fine faisant [SPARK_STEP_DP]. */
        const val SPARK_SIZES = 3

        /** Pas d'epaisseur d'une classe a la suivante, en dp. */
        const val SPARK_STEP_DP = 2f

        /** Au-dela, une particule merite un vrai disque plutot qu'un point. */
        const val BIG_SPARK_DP = 7f
    }
}
