package com.Atom2Universe.app.games.survivor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.min

/** Illustration du hub composée avec le rendu réel du jeu, calculée une fois par taille. */
class SurvivorHubTileDrawable(@Suppress("UNUSED_PARAMETER") context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val art = SurvivorArt()
        val backdrop = Paint(Paint.ANTI_ALIAS_FLAG)
        art.terrain(canvas, 120f, 80f, 1.4f)
        backdrop.shader = LinearGradient(0f, 0f, w, h,
            0x403A887D, 0x00213A39, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, backdrop)
        backdrop.shader = null
        val size = min(w, h)
        val heroX = w * 0.5f
        val heroY = h * 0.27f

        fun creature(type: EnemyType, x: Float, y: Float, radius: Float, variant: Int, palette: Int = 0) {
            val enemy = SEnemy(x, y, 1f, 1f, 0f, 0f, 0f, radius / 1.65f, type).apply {
                visualVariant = variant
                visualPalette = palette
            }
            art.enemy(canvas, enemy, x, y, 1.4f, heroX, heroY)
        }

        // Le gardien domine la composition ; quatre silhouettes distinctes l'encadrent.
        creature(EnemyType.ERRATIC, w * .23f, h * .28f, size * .23f, 0)
        creature(EnemyType.SHOOTER, w * .77f, h * .29f, size * .23f, 1)
        creature(EnemyType.ORBITER, w * .10f, h * .51f, size * .15f, 0)
        creature(EnemyType.FAST, w * .89f, h * .52f, size * .15f, 0)
        art.player(canvas, heroX, heroY, size * .21f, 1.4f, false)
        backdrop.shader = LinearGradient(0f, h * 0.48f, 0f, h,
            intArrayOf(Color.TRANSPARENT, 0xB008171A.toInt(), 0xF008171A.toInt()),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.48f, w, h, backdrop)
        backdrop.shader = null
    }

}
