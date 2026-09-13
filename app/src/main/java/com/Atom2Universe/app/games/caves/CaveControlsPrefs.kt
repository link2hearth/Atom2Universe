package com.Atom2Universe.app.games.caves

import android.content.Context

internal object CaveControlsPrefs {
    private const val PREFS = "cave_controls_v1"
    private const val MIN_SIZE_DP = 40
    private const val MAX_SIZE_DP = 140

    internal enum class Btn(
        val key: String,
        val defaultXf: Float, val defaultYf: Float,
        val defaultSizeDp: Int
    ) {
        UP   ("up",    0.925f, 0.38f, 64),
        DOWN ("down",  0.925f, 0.62f, 64),
        LASER("laser", 0.915f, 0.78f, 84),
        PLACE("place", 0.835f, 0.88f, 72),
        RUN  ("run",   0.07f,  0.86f, 72)
    }

    fun xf(ctx: Context, btn: Btn) = rawF(ctx, "${btn.key}_x", btn.defaultXf).coerceIn(0f, 1f)

    fun yf(ctx: Context, btn: Btn) = rawF(ctx, "${btn.key}_y", btn.defaultYf).coerceIn(0f, 1f)

    fun sizeDp(ctx: Context, btn: Btn) = prefs(ctx).getInt("${btn.key}_sz", btn.defaultSizeDp)
        .coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)

    data class Layout(val xf: Float, val yf: Float, val sizeDp: Int)

    fun crouchToggle(ctx: Context) = prefs(ctx).getBoolean("crouch_toggle", false)
    fun runToggle(ctx: Context) = prefs(ctx).getBoolean("run_toggle", false)

    fun saveAll(ctx: Context, layouts: Map<Btn, Layout>, crouchToggle: Boolean = false, runToggle: Boolean = false): Boolean {
        if (layouts.keys != Btn.entries.toSet()) return false
        val editor = prefs(ctx).edit()
        editor.putBoolean("crouch_toggle", crouchToggle)
        editor.putBoolean("run_toggle", runToggle)
        for ((btn, layout) in layouts) {
            if (!layout.xf.isFinite() || !layout.yf.isFinite()) return false
            editor.putFloat("${btn.key}_x", layout.xf.coerceIn(0f, 1f))
                .putFloat("${btn.key}_y", layout.yf.coerceIn(0f, 1f))
                .putInt("${btn.key}_sz", layout.sizeDp.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP))
        }
        return editor.commit()
    }

    fun reset(ctx: Context) = prefs(ctx).edit().clear().apply()

    private fun rawF(ctx: Context, key: String, fallback: Float): Float {
        val raw = prefs(ctx).getFloat(key, fallback)
        return if (raw.isFinite()) raw else fallback
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
