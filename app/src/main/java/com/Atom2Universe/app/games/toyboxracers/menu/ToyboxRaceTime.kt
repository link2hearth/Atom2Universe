package com.Atom2Universe.app.games.toyboxracers.menu

import android.content.Context
import com.Atom2Universe.app.R
import java.util.Locale

/** Tronquer aux dixièmes évite le chrono « 00:60.0 » juste avant une minute. */
internal fun formatToyboxTime(context: Context, seconds: Float): String {
    val tenths = if (seconds.isFinite()) (seconds.coerceAtLeast(0f).toDouble() * 10.0).toLong() else 0L
    return String.format(Locale.getDefault(), context.getString(R.string.toybox_time_format),
        tenths / 600L, (tenths % 600L) / 10.0)
}
