package com.Atom2Universe.app.effects

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.Atom2Universe.app.AppEffectPack
import com.Atom2Universe.app.AppEffectsSettings
import com.Atom2Universe.app.R

/** Optional quiet bells. No audio focus or media commands; silence during playback/recording. */
internal class EffectsSoundPlayer(context: Context) : Runnable {
    private val context = context.applicationContext
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var pool: SoundPool? = null
    private var soundId = 0
    private var loaded = false

    fun update(settings: AppEffectsSettings, active: Boolean) {
        val wanted = active && settings.enabled && settings.sounds && settings.pack == AppEffectPack.CHRISTMAS
        if (!wanted) {
            stop()
            return
        }
        if (pool != null) return
        val soundPool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        pool = soundPool
        soundPool.setOnLoadCompleteListener { source, _, status ->
            if (source === pool) loaded = status == 0
        }
        soundId = soundPool.load(context, R.raw.effect_christmas_bells, 1)
        // Coming back to a hub keeps the bells' rhythm instead of ringing again after each return.
        val sinceRing = SystemClock.elapsedRealtime() - lastRing
        handler.postDelayed(this, if (lastRing == 0L) FIRST_DELAY_MS
            else (INTERVAL_MS - sinceRing).coerceAtLeast(FIRST_DELAY_MS))
    }

    override fun run() {
        val soundPool = pool ?: return
        val quiet = try {
            audio != null && audio.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
                audio.mode == AudioManager.MODE_NORMAL &&
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0 &&
                !audio.isMusicActive && audio.activeRecordingConfigurations.isEmpty()
        } catch (_: SecurityException) { false }
        if (loaded && quiet && soundPool.play(soundId, .12f, .12f, 1, 0, 1f) != 0) {
            lastRing = SystemClock.elapsedRealtime()
        }
        handler.postDelayed(this, INTERVAL_MS)
    }

    fun stop() {
        handler.removeCallbacks(this)
        pool?.release()
        pool = null
        loaded = false
        soundId = 0
    }

    private companion object {
        const val FIRST_DELAY_MS = 1800L
        const val INTERVAL_MS = 45000L
        // Shared by every hub: the last time bells were actually heard in this process.
        var lastRing = 0L
    }
}
