package com.Atom2Universe.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.Atom2Universe.app.stats.StatsTracker
import com.Atom2Universe.app.crypto.sync.GamesSyncManager
import com.Atom2Universe.app.readingprogress.sync.ReadingProgressSyncManager
import com.Atom2Universe.app.stats.sync.StatsSyncManager
import com.Atom2Universe.app.util.LogcatNoiseReducer

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
        LogcatNoiseReducer.reducePopupMenuLogs()
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
