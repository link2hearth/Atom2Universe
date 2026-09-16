package com.Atom2Universe.app.games.trebuchet

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.*

/** Machine bandée suivant MachineConfig, sans créer de monde ni simuler un tir. */
class TrebuchetHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h, 0xFFB5DEF1.toInt(), 0xFFFFEDC5.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        // Un réglage plus bas ouvre la silhouette du bras tout en gardant la pose armée du jeu.
        val config = MachineConfig().apply {
            pivotHeight = beamLength * .55f
            counterweightMass = 7000f
        }
        val angle = Math.toRadians(config.cockAngleDeg.toDouble()).toFloat()
        val tipX = -config.longArm * cos(angle)
        val tipY = config.pivotHeight - config.longArm * sin(angle)
        val buttX = config.shortArm * cos(angle)
        val buttY = config.pivotHeight + config.shortArm * sin(angle)
        val weightY = buttY - config.hangLength
        val ballX = tipX + sqrt(max(config.slingLength.pow(2) - (tipY - config.shotRadius).pow(2), .01f))
        val spread = config.pivotHeight * .30f
        val minX = min(tipX, -spread) - .35f
        val maxX = max(max(spread, ballX + config.shotRadius), buttX + config.counterweightHalf) + .35f
        val scale = min(w * .90f / (maxX - minX), h * .66f / (buttY + .3f))
        val baseY = h * .69f
        val originX = w * .5f - (minX + maxX) * .5f * scale
        fun sx(x: Float) = originX + x * scale
        fun sy(y: Float) = baseY - y * scale
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
            paint.color = color; paint.strokeWidth = max(1f, width * scale)
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(sx(x1), sy(y1), sx(x2), sy(y2), paint)
        }
        paint.color = 0xFFBCA778.toInt()
        canvas.drawRect(0f, baseY, w, h, paint)
        // Bâti triangulé, bras abaissé côté fronde et contrepoids suspendu côté cible.
        line(-spread, 0f, 0f, config.pivotHeight, 0xFF543625.toInt(), .42f)
        line(spread, 0f, 0f, config.pivotHeight, 0xFF543625.toInt(), .42f)
        line(-spread, 0f, 0f, config.pivotHeight, 0xFFBC824C.toInt(), .22f)
        line(spread, 0f, 0f, config.pivotHeight, 0xFFBC824C.toInt(), .22f)
        line(-spread, .15f, spread, .15f, 0xFF543625.toInt(), .35f)
        line(tipX, tipY, buttX, buttY, 0xFF462D20.toInt(), .43f)
        line(tipX, tipY, buttX, buttY, 0xFFD3A369.toInt(), .24f)
        line(buttX, buttY, buttX, weightY, 0xFF425564.toInt(), .15f)
        val half = config.counterweightHalf
        paint.color = 0xFF7895A5.toInt()
        canvas.drawRect(sx(buttX - half), sy(weightY + half), sx(buttX + half), sy(weightY - half), paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = max(1f, scale * .07f)
        paint.color = 0xFF263238.toInt()
        canvas.drawRect(sx(buttX - half), sy(weightY + half), sx(buttX + half), sy(weightY - half), paint)
        paint.style = Paint.Style.FILL
        line(tipX, tipY, ballX, config.shotRadius, 0xFF65452E.toInt(), .09f)
        paint.color = 0xFF394B5B.toInt()
        canvas.drawCircle(sx(ballX), sy(config.shotRadius), max(scale * config.shotRadius, 2.8f), paint)
        paint.color = 0xFF37474F.toInt()
        canvas.drawCircle(sx(0f), sy(config.pivotHeight), max(scale * .13f, 2f), paint)
        paint.shader = LinearGradient(0f, h * .70f, 0f, h,
            Color.TRANSPARENT, 0xCD423627.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * .70f, w, h, paint)
    }
}
