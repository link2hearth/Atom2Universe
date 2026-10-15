package com.Atom2Universe.app.science.mycology

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Petit schéma de groupe, volontairement sans détails qui prétendraient identifier l’espèce. */
internal class FungusDiagramView(context: Context, private val kind: FungusDiagramKind) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFF66594B.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = minOf(width / 240f, height / 200f)
        canvas.save()
        canvas.translate((width - 240f * s) / 2f, (height - 200f * s) / 2f)
        canvas.scale(s, s)
        when (kind) {
            FungusDiagramKind.MUSHROOM, FungusDiagramKind.GENERIC_MUSHROOM -> mushroom(canvas)
            FungusDiagramKind.JELLY -> jelly(canvas)
            FungusDiagramKind.TRUFFLE -> truffle(canvas)
            FungusDiagramKind.YEAST -> yeast(canvas)
            FungusDiagramKind.MOLD -> mold(canvas)
            FungusDiagramKind.RUST -> rust(canvas)
            FungusDiagramKind.LICHEN -> lichen(canvas)
            FungusDiagramKind.CORTICIOID -> corticioid(canvas)
        }
        canvas.restore()
    }

    private fun fill(color: Int) { paint.color = color; paint.style = Paint.Style.FILL }
    private fun mushroom(c: Canvas) {
        fill(0xFFD7C7A6.toInt())
        c.drawRoundRect(106f, 98f, 134f, 177f, 12f, 12f, paint)
        fill(0xFF9E7554.toInt())
        val cap = Path().apply { moveTo(42f, 102f); cubicTo(49f, 43f, 191f, 43f, 198f, 102f); close() }
        c.drawPath(cap, paint)
        line.color = 0xFF66594B.toInt()
        c.drawLine(60f, 105f, 180f, 105f, line)
    }
    private fun jelly(c: Canvas) {
        fill(0xFFDAAA57.toInt())
        c.drawOval(46f, 69f, 122f, 145f, paint); c.drawOval(96f, 45f, 188f, 130f, paint)
        c.drawOval(116f, 91f, 199f, 158f, paint)
    }
    private fun truffle(c: Canvas) {
        fill(0xFF55453A.toInt()); c.drawCircle(120f, 100f, 68f, paint)
        fill(0xFFE2D2B4.toInt()); c.drawCircle(120f, 100f, 49f, paint)
        line.color = 0xFF594A3B.toInt()
        val p = Path().apply { moveTo(86f, 133f); cubicTo(99f, 112f, 101f, 91f, 121f, 65f); cubicTo(134f, 93f, 146f, 103f, 156f, 128f) }
        c.drawPath(p, line)
    }
    private fun yeast(c: Canvas) {
        fill(0xFFD5BC91.toInt()); c.drawOval(45f, 67f, 131f, 153f, paint); c.drawCircle(171f, 72f, 27f, paint)
        line.color = 0xFF806E54.toInt(); c.drawLine(122f, 79f, 151f, 67f, line)
        c.drawOval(140f, 122f, 199f, 181f, paint); c.drawCircle(203f, 115f, 17f, paint)
    }
    private fun mold(c: Canvas) {
        line.color = 0xFF637B56.toInt()
        val p = Path().apply {
            moveTo(120f, 184f); lineTo(120f, 72f); lineTo(84f, 34f); moveTo(120f, 101f); lineTo(165f, 48f)
            moveTo(97f, 150f); lineTo(55f, 118f); moveTo(120f, 126f); lineTo(172f, 105f)
        }
        c.drawPath(p, line)
        fill(0xFF78649A.toInt())
        listOf(84f to 34f, 165f to 48f, 55f to 118f, 172f to 105f, 120f to 72f).forEach { (x, y) -> c.drawCircle(x, y, 13f, paint) }
    }
    private fun rust(c: Canvas) {
        fill(0xFF739264.toInt())
        val leaf = Path().apply { moveTo(30f, 148f); cubicTo(53f, 37f, 177f, 34f, 211f, 58f); cubicTo(189f, 153f, 83f, 180f, 30f, 148f); close() }
        c.drawPath(leaf, paint); line.color = 0xFF40573D.toInt(); c.drawLine(38f, 151f, 203f, 62f, line)
        fill(0xFFB45C39.toInt()); listOf(81f to 112f, 110f to 92f, 143f to 90f, 158f to 68f, 119f to 133f).forEach { (x, y) -> c.drawCircle(x, y, 8f, paint) }
    }
    private fun lichen(c: Canvas) {
        fill(0xFF87907B.toInt())
        val path = Path().apply {
            moveTo(116f, 178f); cubicTo(75f, 149f, 43f, 157f, 36f, 127f); cubicTo(30f, 95f, 57f, 91f, 72f, 108f)
            cubicTo(61f, 71f, 83f, 54f, 102f, 82f); cubicTo(102f, 42f, 143f, 36f, 147f, 79f)
            cubicTo(170f, 54f, 205f, 69f, 190f, 104f); cubicTo(222f, 121f, 189f, 154f, 145f, 143f); close()
        }
        c.drawPath(path, paint); line.color = 0xFF596452.toInt(); c.drawLine(119f, 175f, 112f, 91f, line)
    }
    private fun corticioid(c: Canvas) {
        fill(0xFF8A6546.toInt()); c.drawRoundRect(25f, 133f, 215f, 171f, 8f, 8f, paint)
        fill(0xFFCDBB93.toInt()); c.drawRoundRect(35f, 115f, 207f, 140f, 10f, 10f, paint)
        line.color = 0xFF77684F.toInt(); c.drawLine(39f, 115f, 202f, 115f, line)
    }
}
