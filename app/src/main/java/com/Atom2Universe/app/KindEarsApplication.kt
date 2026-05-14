package com.Atom2Universe.app

import android.app.Application
import android.content.Context
import com.Atom2Universe.app.stats.StatsTracker
import com.Atom2Universe.app.crypto.sync.GamesSyncManager
import com.Atom2Universe.app.stats.sync.StatsSyncManager
import com.Atom2Universe.app.util.LogcatNoiseReducer

class A2UApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AudioFocusManager.init(this)
        StatsTracker.init(this)
        StatsSyncManager.init(this)
        GamesSyncManager.init(this)
        LogcatNoiseReducer.reducePopupMenuLogs()
    }

    companion object {
        @Volatile
        private lateinit var instance: A2UApplication

        val appContext: Context
            get() = instance.applicationContext
    }
}
