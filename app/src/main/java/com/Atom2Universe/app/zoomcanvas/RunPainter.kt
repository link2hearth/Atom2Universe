package com.Atom2Universe.app.zoomcanvas

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import com.Atom2Universe.app.zoomcanvas.core.RenderList
import com.Atom2Universe.app.zoomcanvas.core.Stroke
import com.Atom2Universe.app.zoomcanvas.core.TextItem
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Trace sur un canevas les passes calculées par [com.Atom2Universe.app.zoomcanvas.core.ZoomRenderer].
 * L'écran en a un, et chaque fil qui cuit un cache raster a le sien : les `Paint` et le `Path` de
 * travail ne se partagent pas entre deux fils.
 *
 * La gomme dessine de la transparence : un coup de gomme est tracé en mode « destination out », qui
 * retire ce qui est déjà tracé dessous (d'où des groupes composés dans un calque à part).
 * Le pinceau s'affine aux bouts : il est tracé comme une forme pleine (un disque par point, un
 * trapèze par segment), remplie d'un coup pour que ses morceaux qui se chevauchent ne foncent pas.
 * Le feutre se multiplie avec ce qu'il recouvre.
 */
class RunPainter {

    /** Fournit le bitmap d'une image du projet (null tant qu'il se charge : un cadre gris en attendant). */
    var imageProvider: ((String) -> Bitmap?)? = null

    /** Donne la police d'un texte (nom d'affichage, style) : la vue ne connaît pas les ressources. */
    var typefaceProvider: ((String, Int) -> Typeface)? = null

    private val path = Path()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val eraseDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val brushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.MULTIPLY
    }
    private val markerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.MULTIPLY
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { isLinearText = true }
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint().apply { color = 0x22000000 }
    private val srcRect = Rect()
    private val dstRect = RectF()

    /** Trace les passes [from] à [to] de [list] (coordonnées d'écran, déjà découpées). */
    fun drawRuns(canvas: Canvas, list: RenderList, from: Int, to: Int) {
        val c = list.coords
        for (r in from until to) {
            val img = list.runImage[r]
            if (img >= 0) {
                drawImage(canvas, list, img, list.runAlpha[r])
                continue
            }
            val text = list.runText[r]
            if (text != null) {
                drawText(canvas, list, r, text)
                continue
            }
            val erase = list.runErase[r]
            val kind = list.runKind[r].toInt()
            val base = if (erase) Color.BLACK else list.runColor[r]
            val a = ((base ushr 24) * list.runAlpha[r]).toInt().coerceIn(0, 255)
            if (a == 0) continue
            val argb = (a shl 24) or (base and 0xFFFFFF)
            val start = list.runStart[r]
            val n = list.runPoints[r]
            val width = list.runWidth[r]
            if (kind == RenderList.KIND_FILL) {
                path.rewind()
                path.moveTo(c[start], c[start + 1])
                for (k in 1 until n) path.lineTo(c[start + 2 * k], c[start + 2 * k + 1])
                path.close()
                fillPaint.color = argb
                canvas.drawPath(path, fillPaint)
                continue
            }
            if (n == 1) {
                val dot = when {
                    erase -> eraseDotPaint
                    kind == Stroke.MARKER -> markerDotPaint
                    else -> dotPaint
                }
                dot.color = argb
                canvas.drawCircle(c[start], c[start + 1], width / 2, dot)
                continue
            }
            if (kind == Stroke.BRUSH) {
                drawBrush(canvas, list, start, n, argb)
                continue
            }
            val paint = when {
                erase -> erasePaint
                kind == Stroke.MARKER -> markerPaint
                else -> strokePaint
            }
            paint.color = argb
            paint.strokeWidth = width
            path.rewind()
            path.moveTo(c[start], c[start + 1])
            for (k in 1 until n) path.lineTo(c[start + 2 * k], c[start + 2 * k + 1])
            canvas.drawPath(path, paint)
        }
    }

    /**
     * Un trait de pinceau : un disque par point et un trapèze par segment, tous dans le même sens de
     * parcours, remplis d'un seul coup (leur union, sans double opacité là où ils se chevauchent).
     * Les points plus serrés qu'une demi-épaisseur sont sautés : la forme n'en change pas.
     */
    private fun drawBrush(canvas: Canvas, list: RenderList, start: Int, n: Int, argb: Int) {
        val c = list.coords
        val pw = list.pointWidth
        val first = start / 2
        path.rewind()
        var px = c[start]
        var py = c[start + 1]
        var pr = pw[first] / 2
        path.addCircle(px, py, pr, Path.Direction.CCW)
        for (k in 1 until n) {
            val x = c[start + 2 * k]
            val y = c[start + 2 * k + 1]
            val r = pw[first + k] / 2
            val dx = x - px
            val dy = y - py
            val len = hypot(dx, dy)
            if (k < n - 1 && len < max(1f, min(pr, r))) continue
            if (len > 0f) {
                // Le trapèze tangent aux deux disques, parcouru dans le même sens qu'eux.
                val nx = -dy / len
                val ny = dx / len
                path.moveTo(px + nx * pr, py + ny * pr)
                path.lineTo(x + nx * r, y + ny * r)
                path.lineTo(x - nx * r, y - ny * r)
                path.lineTo(px - nx * pr, py - ny * pr)
                path.close()
            }
            path.addCircle(x, y, r, Path.Direction.CCW)
            px = x; py = y; pr = r
        }
        brushPaint.color = argb
        canvas.drawPath(path, brushPaint)
    }

    /** Un texte, ligne par ligne : le coin haut-gauche et la taille viennent du rendu, déjà à l'écran. */
    private fun drawText(canvas: Canvas, list: RenderList, r: Int, t: TextItem) {
        val a = ((t.color ushr 24) * list.runAlpha[r]).toInt().coerceIn(0, 255)
        if (a == 0) return
        val size = list.runTextSize[r]
        textPaint.color = (a shl 24) or (t.color and 0xFFFFFF)
        textPaint.textSize = size
        textPaint.typeface = typefaceProvider?.invoke(t.font, t.style) ?: Typeface.DEFAULT
        val x = list.runTextX[r]
        var y = list.runTextY[r] + (TextItem.BASELINE * size).toFloat()
        for (line in t.lines) {
            canvas.drawText(line, x, y, textPaint)
            y += (TextItem.LINE_HEIGHT * size).toFloat()
        }
    }

    private fun drawImage(canvas: Canvas, list: RenderList, i: Int, alpha: Float) {
        val d = list.imageDst
        dstRect.set(d[4 * i], d[4 * i + 1], d[4 * i + 2], d[4 * i + 3])
        val bmp = list.imageKeys[i]?.let { imageProvider?.invoke(it) }
        if (bmp == null) {
            canvas.drawRect(dstRect, placeholderPaint)
            return
        }
        // Le bitmap chargé peut être plus petit que l'image d'origine : on met le rectangle source à son échelle.
        val kx = bmp.width.toFloat() / list.imagePx[2 * i]
        val ky = bmp.height.toFloat() / list.imagePx[2 * i + 1]
        val sr = list.imageSrc
        srcRect.set(
            (sr[4 * i] * kx).toInt(), (sr[4 * i + 1] * ky).toInt(),
            kotlin.math.ceil(sr[4 * i + 2] * kx).toInt().coerceAtMost(bmp.width),
            kotlin.math.ceil(sr[4 * i + 3] * ky).toInt().coerceAtMost(bmp.height),
        )
        if (srcRect.width() <= 0 || srcRect.height() <= 0) return
        imagePaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(bmp, srcRect, dstRect, imagePaint)
    }
}
