package com.Atom2Universe.app.games.mahjong

import android.os.Looper
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

internal fun mahjongWorker(activity: MahjongActivity): ExecutorService =
    MahjongActivity::class.java.getDeclaredField("worker").apply { isAccessible = true }
        .get(activity) as ExecutorService

/** Drain the serial puzzle queue, then deliver its UI results; no timing sleeps. */
internal fun awaitMahjongWork(activity: MahjongActivity) {
    mahjongWorker(activity).submit {}.get(20, TimeUnit.SECONDS)
    shadowOf(Looper.getMainLooper()).idle()
}
