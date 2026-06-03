package com.Atom2Universe.app.games.caves

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView animée qui joue un swing style Minecraft quand triggerSwing() est appelé.
 * Le pivot est en bas à droite (poignée), la rotation passe du repos (+40°) à l'attaque (-50°).
 */
internal class WeaponSwingView(context: Context) : AppCompatImageView(context) {

    companion object {
        private const val REST_ANGLE   =  40f
        private const val ATTACK_ANGLE = -50f
    }

    init {
        scaleType = ScaleType.FIT_CENTER
        rotation  = REST_ANGLE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pivotX = w.toFloat()
        pivotY = h.toFloat()
    }

    fun setWeapon(bitmap: Bitmap?) {
        setImageBitmap(bitmap)
    }

    fun triggerSwing() {
        animate().cancel()
        rotation = REST_ANGLE
        animate()
            .rotation(ATTACK_ANGLE)
            .setDuration(140)
            .withEndAction {
                animate()
                    .rotation(REST_ANGLE)
                    .setDuration(220)
                    .start()
            }
            .start()
    }
}
