package com.Atom2Universe.app

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.StrictMode
import com.Atom2Universe.app.stats.StatsTracker
import com.Atom2Universe.app.crypto.sync.GamesSyncManager
import com.Atom2Universe.app.readingprogress.sync.ReadingProgressSyncManager
import com.Atom2Universe.app.stats.sync.StatsSyncManager

class A2UApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LocaleHelper.syncApplicationLocale(this)
        LocaleHelper.ensureLocale(this)
        instance = this
        AudioFocusManager.init(this)
        StatsTracker.init(this)
        StatsSyncManager.init(this)
        ReadingProgressSyncManager.init(this)
        GamesSyncManager.init(this)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // Debug seulement : le logcat indique la ligne qui a ouvert un flux sans le fermer.
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleHelper.ensureLocale(this)
    }

    companion object {
        @Volatile
        private lateinit var instance: A2UApplication

        val appContext: Context
            get() = instance.applicationContext
    }
}
