package com.Atom2Universe.app.games.starswar

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log

/** Petits sons PCM originaux, préchargés ; aucune note MIDI ni thread par tir. */
internal class StarsWarSoundEngine(private val context: Context) {
    private var pool: SoundPool? = null
    private val ids = mutableMapOf<String, Int>()
    private val loaded = mutableSetOf<Int>()
    private val lastPlayed = mutableMapOf<String, Long>()
    private val streams = mutableMapOf<Int, Long>()
    private var shot = 0
    private var paused = false

    @Synchronized fun start() {
        if (pool != null) return
        val sounds = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        pool = sounds
        sounds.setOnLoadCompleteListener { source, id, status ->
            synchronized(this) {
                if (source === pool && status == 0) loaded.add(id)
            }
        }
        for (name in listOf("shot_0", "shot_1", "shot_2", "pop", "hit", "boss", "wave", "meteor", "game_over")) {
            try {
                context.assets.openFd("spacefight/audio/$name.wav").use {
                    ids[name] = sounds.load(it, 1)
                }
            } catch (e: Exception) {
                Log.w("SpaceFightAudio", "Cannot load $name", e)
            }
        }
        lastPlayed.clear()
        streams.clear()
    }

    @Synchronized fun stop() {
        pool?.release()
        pool = null
        ids.clear()
        loaded.clear()
        lastPlayed.clear()
        streams.clear()
    }

    @Synchronized fun setPaused(value: Boolean) {
        paused = value
        // Paused one-shots should not replay when the game resumes.
        if (value) {
            streams.keys.forEach { pool?.stop(it) }
            streams.clear()
        }
    }

    @Synchronized fun onPlayerShot() {
        play("shot_${shot % 3}", .48f, 1, "shot", 115)
        shot = (shot + 1) % 3
    }
    fun onEnemyDestroyed() = play("pop", .65f, 2, "pop", 85)
    fun onBossDestroyed() = play("boss", .85f, 4)
    fun onPlayerHit() = play("hit", .8f, 5, "hit", 180)
    fun onGameOver() = play("game_over", .85f, 6)
    fun onNewWave() = play("wave", .65f, 3)
    fun onMeteorPhase() = play("meteor", .55f, 3)

    @Synchronized private fun play(
        name: String, volume: Float, priority: Int, group: String = name, cooldown: Long = 0
    ) {
        if (paused) return
        val sounds = pool ?: return
        val id = ids[name] ?: return
        if (id !in loaded) return
        val now = SystemClock.uptimeMillis()
        val previous = lastPlayed[group]
        if (previous != null && now - previous < cooldown) return
        streams.entries.removeAll { it.value <= now }
        val stream = sounds.play(id, volume, volume, priority, 0, 1f)
        if (stream != 0) {
            lastPlayed[group] = now
            // Longest asset is 1.54 s; retain IDs briefly to stop every active one-shot.
            streams[stream] = now + 1600
        }
    }
}
