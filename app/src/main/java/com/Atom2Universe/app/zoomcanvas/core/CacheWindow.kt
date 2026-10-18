package com.Atom2Universe.app.zoomcanvas.core

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * La fenêtre qu'un cache raster a cuite : un rectangle de [w]×[h] pixels centré sur ([vx], [vy])
 * dans le repère de la couche, à [zoom] pixels par unité. Elle déborde de l'écran (de [MARGIN])
 * pour qu'on puisse faire glisser l'image un moment sans voir le bord.
 *
 * [viewZoom] est le zoom de l'écran quand on l'a cuite : le zoom du cache en diffère quand la
 * fenêtre a dû être cuite à une résolution réduite pour tenir dans le budget de pixels.
 *
 * Tout est en `Double`, sans rien d'Android : la géométrie se teste en JVM.
 */
class CacheWindow(
    val vx: Double,
    val vy: Double,
    val zoom: Double,
    val viewZoom: Double,
    val w: Int,
    val h: Int,
) {
    /** Le pixel (px, py) du cache est, à l'écran, en (a·px + tx, a·py + ty), pour une caméra ([cx], [cy], [z]) dans une vue [viewW]×[viewH]. */
    class Transform {
        var scale = 1.0
        var tx = 0.0
        var ty = 0.0
    }

    fun transformTo(cx: Double, cy: Double, z: Double, viewW: Double, viewH: Double, out: Transform = Transform()): Transform {
        val s = z / zoom
        out.scale = s
        out.tx = (vx - cx) * z + viewW / 2 - (w / 2.0) * s
        out.ty = (vy - cy) * z + viewH / 2 - (h / 2.0) * s
        return out
    }

    /**
     * Le cache couvre-t-il tout l'écran, avec encore [slackPx] pixels d'écran de réserve de chaque
     * côté ? Avec 0, il couvre juste ; avec une réserve, on sait qu'il est temps d'en cuire un autre
     * avant que le bord ne se voie.
     */
    fun covers(cx: Double, cy: Double, z: Double, viewW: Double, viewH: Double, slackPx: Double, t: Transform = Transform()): Boolean {
        transformTo(cx, cy, z, viewW, viewH, t)
        return t.tx <= -slackPx && t.ty <= -slackPx &&
            t.tx + t.scale * w >= viewW + slackPx && t.ty + t.scale * h >= viewH + slackPx
    }

    /** Le cache est-il encore assez net (ni trop agrandi, ni très réduit) pour une caméra au zoom [z] ? */
    fun sharpEnough(z: Double): Boolean {
        val k = z / viewZoom
        return k <= MAX_STRETCH && k >= MIN_SHRINK
    }

    companion object {
        /** Part de l'écran de plus, de chaque axe : l'image cuite fait ce nombre de fois l'écran. */
        const val MARGIN = 1.3
        /** Au-delà, une image agrandie est floue : on la recuit. */
        const val MAX_STRETCH = 1.25
        /** En dessous, une image réduite gaspille de la place et du net : on la recuit. */
        const val MIN_SHRINK = 0.5

        /**
         * La fenêtre à cuire pour une caméra ([cx], [cy], [z]) dans une vue [viewW]×[viewH], dans la
         * limite de [maxPixels] pixels : au-delà, la résolution baisse (le cache n'a pas besoin d'être
         * net au pixel quand on voit des dizaines de milliers de traits).
         */
        fun around(cx: Double, cy: Double, z: Double, viewW: Double, viewH: Double, maxPixels: Long): CacheWindow {
            val area = viewW * MARGIN * viewH * MARGIN
            val r = min(1.0, sqrt(maxPixels / max(area, 1.0)))
            val w = max(1, ceil(viewW * MARGIN * r).toInt())
            val h = max(1, ceil(viewH * MARGIN * r).toInt())
            return CacheWindow(cx, cy, z * r, z, w, h)
        }
    }
}
