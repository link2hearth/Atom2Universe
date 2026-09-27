package com.Atom2Universe.app.games.caves

import android.graphics.*
import android.graphics.drawable.Drawable

/** Line icons sized independently from translated labels and Android button padding. */
internal class CaveActionDrawable(private val kind: String) : Drawable() {
    /** Lets LayerDrawable/RippleDrawable create an independent copy when mutating backgrounds. */
    private class IconState(private val kind: String) : Drawable.ConstantState() {
        override fun newDrawable(): Drawable = CaveActionDrawable(kind)
        override fun getChangingConfigurations(): Int = 0
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CaveUiStyle.TEXT; style = Paint.Style.STROKE; strokeWidth = 1.6f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    override fun draw(canvas: Canvas) {
        canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width()/32f, bounds.height()/32f)
        when (kind) {
            "helmet" -> {
                canvas.drawPath(Path().apply { moveTo(6f,25f); lineTo(6f,15f); cubicTo(6f,1f,26f,1f,26f,15f); lineTo(26f,25f); lineTo(20f,27f); lineTo(20f,18f); lineTo(12f,18f); lineTo(12f,27f); close() },paint)
                canvas.drawLine(16f,6f,16f,14f,paint)
            }
            "legs" -> canvas.drawPath(Path().apply {
                moveTo(7f,5f); lineTo(25f,5f); lineTo(27f,27f); lineTo(18f,27f); lineTo(16f,15f); lineTo(14f,27f); lineTo(5f,27f); close(); moveTo(7f,10f); lineTo(25f,10f)
            },paint)
            "boots" -> {
                for(x in listOf(4f,18f)) canvas.drawPath(Path().apply {
                    moveTo(x,5f); lineTo(x+8f,5f); lineTo(x+8f,19f); lineTo(x+11f,22f); lineTo(x+11f,27f); lineTo(x,27f); close(); moveTo(x,23f); lineTo(x+10f,23f)
                },paint)
            }
            "shield" -> {
                canvas.drawPath(Path().apply { moveTo(16f,4f); lineTo(27f,8f); lineTo(25f,20f); quadTo(22f,26f,16f,29f); quadTo(10f,26f,7f,20f); lineTo(5f,8f); close(); moveTo(16f,8f); lineTo(16f,24f); moveTo(10f,14f); lineTo(22f,14f) },paint)
            }
            "suit" -> {
                canvas.drawCircle(16f,6f,4f,paint)
                canvas.drawPath(Path().apply { moveTo(11f,12f); lineTo(21f,12f); lineTo(24f,20f); lineTo(20f,20f); lineTo(21f,29f); lineTo(16f,29f); lineTo(16f,22f); lineTo(15f,29f); lineTo(10f,29f); lineTo(11f,20f); lineTo(7f,20f); close() },paint)
            }
            "armor" -> {
                val path = Path().apply { moveTo(11f,5f); lineTo(7f,8f); lineTo(4f,15f); lineTo(10f,18f); lineTo(10f,27f); lineTo(23f,27f); lineTo(23f,18f); lineTo(28f,15f); lineTo(25f,8f); lineTo(21f,5f); quadTo(16f,13f,11f,5f); close() }
                canvas.drawPath(path,paint)
            }
            "ammo" -> {
                canvas.drawRoundRect(7f,12f,14f,27f,1f,1f,paint)
                canvas.drawRoundRect(19f,12f,26f,27f,1f,1f,paint)
                for(x in listOf(7f,19f)) {
                    canvas.drawPath(Path().apply { moveTo(x,12f); quadTo(x,6f,x+3.5f,4f); quadTo(x+7f,6f,x+7f,12f) },paint)
                    canvas.drawLine(x,23f,x+7f,23f,paint)
                }
            }
            "tools" -> {
                canvas.drawLine(8f,27f,21f,9f,paint)
                canvas.drawPath(Path().apply { moveTo(5f,12f); quadTo(18f,0f,28f,17f); lineTo(19f,11f); close() },paint)
            }
            "kitchen" -> {
                canvas.drawRoundRect(7f,14f,25f,26f,3f,3f,paint)
                canvas.drawLine(5f,14f,27f,14f,paint);canvas.drawLine(13f,11f,19f,11f,paint)
                canvas.drawLine(4f,19f,7f,19f,paint);canvas.drawLine(25f,19f,28f,19f,paint)
                canvas.drawPath(Path().apply { moveTo(12f,8f); quadTo(8f,5f,12f,2f); moveTo(21f,8f); quadTo(17f,5f,21f,2f) },paint)
            }
            "machine" -> {
                canvas.drawCircle(16f,16f,8f,paint);canvas.drawCircle(16f,16f,3f,paint)
                repeat(8) { canvas.save();canvas.rotate(it*45f,16f,16f);canvas.drawRect(14f,4f,18f,8f,paint);canvas.restore() }
            }
            "terrain" -> {
                canvas.drawPath(Path().apply { moveTo(3f,25f); lineTo(12f,7f); lineTo(18f,17f); lineTo(23f,11f); lineTo(29f,25f); close(); moveTo(8f,15f); lineTo(12f,17f); lineTo(16f,14f) },paint)
            }
            "wood" -> {
                canvas.drawOval(5f,15f,17f,28f,paint);canvas.drawOval(8f,19f,14f,25f,paint)
                canvas.drawPath(Path().apply { moveTo(7f,16f); lineTo(19f,4f); quadTo(29f,2f,28f,14f); lineTo(15f,26f); moveTo(19f,10f); lineTo(12f,17f); moveTo(25f,12f); lineTo(19f,19f) },paint)
            }
            "stone" -> {
                canvas.drawPath(Path().apply { moveTo(4f,21f); lineTo(9f,10f); lineTo(21f,6f); lineTo(28f,17f); lineTo(24f,26f); lineTo(10f,27f); close(); moveTo(9f,10f); lineTo(16f,18f); lineTo(28f,17f); moveTo(16f,18f); lineTo(10f,27f) },paint)
            }
            "nature" -> {
                canvas.drawPath(Path().apply { moveTo(16f,3f); lineTo(6f,16f); lineTo(11f,16f); lineTo(4f,24f); lineTo(28f,24f); lineTo(21f,16f); lineTo(26f,16f); close(); moveTo(16f,24f); lineTo(16f,29f) },paint)
            }
            "fabric" -> {
                canvas.drawRect(6f,6f,26f,26f,paint)
                for(i in 0..2) { val p=10f+i*6;canvas.drawLine(p,4f,p,28f,paint);canvas.drawLine(4f,p,28f,p,paint) }
            }
            "ore" -> {
                canvas.drawPath(Path().apply { moveTo(6f,18f); lineTo(10f,5f); lineTo(17f,11f); lineTo(22f,3f); lineTo(28f,15f); lineTo(23f,27f); lineTo(10f,28f); close(); moveTo(17f,11f); lineTo(15f,26f); moveTo(22f,3f); lineTo(23f,27f) },paint)
            }
            "resources" -> {
                canvas.drawPath(Path().apply { moveTo(5f,14f); lineTo(24f,14f); lineTo(28f,23f); lineTo(9f,23f); close(); moveTo(5f,14f); lineTo(3f,20f); lineTo(9f,27f); lineTo(28f,27f); lineTo(28f,23f); moveTo(9f,23f); lineTo(9f,27f) },paint)
            }
            "close", "remove" -> { canvas.drawLine(9f,9f,23f,23f,paint);canvas.drawLine(23f,9f,9f,23f,paint) }
            "previous", "next" -> {
                if(kind=="next") canvas.rotate(180f,16f,16f)
                canvas.drawLine(20f,7f,11f,16f,paint);canvas.drawLine(11f,16f,20f,25f,paint)
            }
            "star" -> {
                val path=Path()
                repeat(10) { i -> val a=Math.PI*i/5-Math.PI/2;val r=if(i%2==0) 12 else 5
                    val x=16+(kotlin.math.cos(a)*r).toFloat();val y=16+(kotlin.math.sin(a)*r).toFloat()
                    if(i==0) path.moveTo(x,y) else path.lineTo(x,y) }
                path.close();canvas.drawPath(path,paint)
            }
            "clock" -> { canvas.drawCircle(16f,16f,11f,paint);canvas.drawLine(16f,8f,16f,16f,paint);canvas.drawLine(16f,16f,22f,19f,paint) }
            "sort" -> { for(i in 0..2) canvas.drawLine(6f,9f+i*7,25f-i*5,9f+i*7,paint) }
            "info" -> { canvas.drawCircle(16f,16f,11f,paint);canvas.drawCircle(16f,10f,1f,paint);canvas.drawLine(16f,15f,16f,23f,paint) }
            "craft" -> { canvas.drawLine(9f,26f,23f,8f,paint);canvas.drawRoundRect(13f,5f,28f,11f,2f,2f,paint);canvas.drawLine(5f,27f,26f,27f,paint) }
            "pin" -> { canvas.drawRoundRect(8f,6f,24f,16f,3f,3f,paint);canvas.drawLine(11f,16f,8f,21f,paint);canvas.drawLine(8f,21f,24f,21f,paint);canvas.drawLine(24f,21f,21f,16f,paint);canvas.drawLine(16f,21f,16f,28f,paint) }
            "all" -> { for(x in 0..1) for(y in 0..1) canvas.drawRoundRect(6f+x*12,6f+y*12,14f+x*12,14f+y*12,2f,2f,paint) }
            "map" -> {
                val path = Path().apply { moveTo(5f,8f); lineTo(12f,5f); lineTo(20f,8f); lineTo(27f,5f); lineTo(27f,24f); lineTo(20f,27f); lineTo(12f,24f); lineTo(5f,27f); close(); moveTo(12f,5f); lineTo(12f,24f); moveTo(20f,8f); lineTo(20f,27f) }
                canvas.drawPath(path, paint)
            }
            "reload" -> {
                canvas.drawArc(7f, 7f, 25f, 25f, 45f, 285f, false, paint)
                val arrow = Path().apply { moveTo(20f, 5f); lineTo(25f, 11f); lineTo(18f, 12f) }
                canvas.drawPath(arrow, paint)
            }
            "camera" -> {
                canvas.drawRoundRect(5f,10f,27f,26f,4f,4f,paint); canvas.drawCircle(16f,18f,5f,paint)
                canvas.drawLine(10f,10f,12f,6f,paint); canvas.drawLine(12f,6f,20f,6f,paint); canvas.drawLine(20f,6f,22f,10f,paint)
            }
            "sun" -> {
                canvas.drawCircle(16f,16f,6f,paint)
                repeat(8) { canvas.save(); canvas.rotate(it*45f,16f,16f); canvas.drawLine(16f,3f,16f,6f,paint); canvas.restore() }
            }
            "combat" -> {
                val path = Path().apply { moveTo(8f,25f); lineTo(23f,6f); lineTo(27f,5f); lineTo(27f,9f); lineTo(12f,28f); close(); moveTo(7f,19f); lineTo(18f,27f) }
                canvas.drawPath(path,paint)
            }
            "garden" -> {
                canvas.drawLine(16f,28f,16f,12f,paint)
                canvas.drawOval(5f,7f,16f,17f,paint)
                canvas.drawOval(16f,3f,27f,13f,paint)
                canvas.drawLine(7f,28f,25f,28f,paint)
            }
            "bag" -> {
                canvas.drawRoundRect(8f,10f,24f,27f,4f,4f,paint)
                canvas.drawRoundRect(12f,5f,20f,12f,3f,3f,paint)
                canvas.drawRoundRect(11f,18f,21f,24f,2f,2f,paint)
            }
            "place" -> {
                val path = Path().apply { moveTo(16f,5f); lineTo(26f,11f); lineTo(26f,23f); lineTo(16f,29f); lineTo(6f,23f); lineTo(6f,11f); close(); moveTo(6f,11f); lineTo(16f,17f); lineTo(26f,11f); moveTo(16f,17f); lineTo(16f,29f) }
                canvas.drawPath(path, paint)
            }
            "run" -> {
                canvas.drawCircle(21f, 6f, 3f, paint)
                val path = Path().apply {
                    moveTo(19f, 10f); lineTo(14f, 17f); lineTo(22f, 21f); lineTo(26f, 28f)
                    moveTo(14f, 17f); lineTo(10f, 24f); lineTo(4f, 25f)
                    moveTo(17f, 12f); lineTo(10f, 10f); lineTo(6f, 15f)
                    moveTo(17f, 12f); lineTo(23f, 15f); lineTo(28f, 12f)
                }
                canvas.drawPath(path, paint)
            }
            "crouch" -> {
                canvas.drawCircle(18f, 7f, 3f, paint)
                val path = Path().apply {
                    moveTo(17f, 11f); lineTo(12f, 17f); lineTo(21f, 21f)
                    lineTo(15f, 27f); lineTo(23f, 27f)
                    moveTo(15f, 13f); lineTo(22f, 16f); lineTo(27f, 16f)
                }
                canvas.drawPath(path, paint)
                canvas.drawLine(5f, 29f, 27f, 29f, paint)
            }
            "aim" -> {
                canvas.drawCircle(16f,16f,8f,paint)
                canvas.drawLine(16f,3f,16f,10f,paint); canvas.drawLine(16f,22f,16f,29f,paint)
                canvas.drawLine(3f,16f,10f,16f,paint); canvas.drawLine(22f,16f,29f,16f,paint)
            }
            else -> {
                if (kind == "down") { canvas.rotate(180f,16f,16f) }
                canvas.drawLine(16f,25f,16f,7f,paint)
                canvas.drawLine(7f,16f,16f,7f,paint); canvas.drawLine(25f,16f,16f,7f,paint)
                canvas.drawLine(7f,28f,25f,28f,paint)
            }
        }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getConstantState(): Drawable.ConstantState = IconState(kind)
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
