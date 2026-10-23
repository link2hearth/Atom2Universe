package com.Atom2Universe.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Le système signale la réussite après avoir remplacé et arrêté l'ancienne application. */
class AppUpdateInstalledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        AppUpdateRepository.cleanupInstalledApkAsync(context) { pending.finish() }
    }
}
