package com.Atom2Universe.app.games.caves

import android.content.Context

internal object CaveControlsPrefs {
    private const val PREFS = "cave_controls_v1"

    internal enum class Btn(
        val key: String,
        val defaultXf: Float, val defaultYf: Float,
        val defaultSizeDp: Int
    ) {
        UP   ("up",    0.925f, 0.38f, 64),
        DOWN ("down",  0.925f, 0.62f, 64),
        LASER("laser", 0.915f, 0.88f, 72),
        PLACE("place", 0.835f, 0.88f, 72)
    }

    fun xf(ctx: Context, btn: Btn)     = prefs(ctx).getFloat("${btn.key}_x", btn.defaultXf)
    fun yf(ctx: Context, btn: Btn)     = prefs(ctx).getFloat("${btn.key}_y", btn.defaultYf)
    fun sizeDp(ctx: Context, btn: Btn) = prefs(ctx).getInt("${btn.key}_sz", btn.defaultSizeDp)

    fun save(ctx: Context, btn: Btn, xf: Float, yf: Float, sizeDp: Int) {
        prefs(ctx).edit()
            .putFloat("${btn.key}_x", xf)
            .putFloat("${btn.key}_y", yf)
            .putInt("${btn.key}_sz", sizeDp)
            .apply()
    }

    fun reset(ctx: Context) = prefs(ctx).edit().clear().apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
