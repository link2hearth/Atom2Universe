package com.Atom2Universe.app.games.cosmorun

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Lecture des bruitages de [CosmoRunSynth] : un WAV par son, écrit une fois dans le cache puis
 * préchargé par SoundPool. La synthèse tourne sur son propre fil, jamais sur celui du dessin.
 */
class CosmoRunSfx(private val context: Context) {
    private var pool: SoundPool? = null
    private val ids = HashMap<CosmoSound, Int>()
    private val loaded = HashSet<Int>()
    private val lastPlayed = HashMap<CosmoSound, Long>()
    private var humStream = 0
    private var humRate = 1f
    private val musicStreams = IntArray(4)
    private val musicVolume = FloatArray(4)
    private var paused = false

    /** Coupe tout (le réglage du menu) sans détruire le moteur. */
    @Volatile var enabled = true
        set(value) {
            field = value
            if (!value) { stopHum(); stopMusic() }
        }

    @Synchronized fun start() {
        if (pool != null) return
        val sounds = SoundPool.Builder().setMaxStreams(16).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        pool = sounds
        sounds.setOnLoadCompleteListener { source, id, status ->
            synchronized(this) { if (source === pool && status == 0) loaded.add(id) }
        }
        Thread({
            try {
                val files = CosmoSound.entries.map { it to cached(it) }
                synchronized(this) {
                    if (pool === sounds) for ((sound, file) in files) ids[sound] = sounds.load(file.path, 1)
                }
            } catch (e: Exception) {
                Log.w("CosmoRunAudio", "Cannot prepare sounds", e)
            }
        }, "CosmoRunAudio").start()
    }

    private fun cached(sound: CosmoSound): File {
        val file = File(context.cacheDir, "cosmo_run_v${CosmoRunSynth.VERSION}_${sound.name.lowercase()}.wav")
        if (file.isFile && file.length() > 44) return file
        val temp = File.createTempFile("cosmo_run_", ".wav", context.cacheDir)
        try {
            FileOutputStream(temp).use { it.write(CosmoRunSynth.wav(CosmoRunSynth.render(sound))) }
            check(temp.renameTo(file)) { "Cannot cache ${sound.name}" }
        } finally { temp.delete() }
        return file
    }

    @Synchronized fun pause() {
        paused = true
        stopHum(); stopMusic()
    }
    @Synchronized fun resume() { paused = false }

    @Synchronized fun release() {
        paused = true
        stopHum(); stopMusic()
        pool?.release()
        pool = null
        ids.clear(); loaded.clear(); lastPlayed.clear()
    }

    /** [cooldownMs] borne la cadence d'un même son (une rafale d'atomes ne doit pas saturer). */
    @Synchronized fun play(sound: CosmoSound, volume: Float = 1f, rate: Float = 1f, cooldownMs: Long = 0L) {
        if (!enabled || paused) return
        val sounds = pool ?: return
        val id = ids[sound]?.takeIf { it in loaded } ?: return
        val now = SystemClock.uptimeMillis()
        if (cooldownMs > 0 && lastPlayed[sound]?.let { now - it < cooldownMs } == true) return
        lastPlayed[sound] = now
        sounds.play(id, volume, volume, if (sound == CosmoSound.STUMBLE || sound == CosmoSound.GAME_OVER) 5 else 1,
            0, rate.coerceIn(.5f, 2f))
    }

    /** Ronronnement du moteur : vivant tant qu'on court, plus aigu quand on accélère. */
    @Synchronized fun setHum(active: Boolean, speedFactor: Float) {
        val sounds = pool ?: return
        if (!active || !enabled || paused) { stopHum(); return }
        val rate = (.8f + speedFactor * .5f).coerceIn(.6f, 1.6f)
        if (humStream == 0) {
            val id = ids[CosmoSound.HUM]?.takeIf { it in loaded } ?: return
            humStream = sounds.play(id, .16f, .16f, 0, -1, rate)
            humRate = rate
        } else if (kotlin.math.abs(rate - humRate) > .03f) {
            sounds.setRate(humStream, rate)
            humRate = rate
        }
    }

    /**
     * La musique : quatre couches lancées ensemble, dont le volume suit le palier de chaîne
     * (nappe seule ; batterie à 10 de chaîne, basse à 20, mélodie à 35). Les volumes glissent d'une
     * image à l'autre pour que perdre sa chaîne s'entende sans claquer.
     */
    @Synchronized fun setMusic(active: Boolean, chain: Int, dt: Float) {
        val sounds = pool ?: return
        if (!active || !enabled || paused) { stopMusic(); return }
        val layers = arrayOf(CosmoSound.MUSIC_PAD, CosmoSound.MUSIC_DRUMS, CosmoSound.MUSIC_BASS, CosmoSound.MUSIC_LEAD)
        if (musicStreams[0] == 0) {
            val ids = layers.map { ids[it]?.takeIf { id -> id in loaded } ?: return }
            // Lancées dans la même foulée, à volume nul : seul le volume décide de ce qu'on entend.
            for (i in layers.indices) { musicStreams[i] = sounds.play(ids[i], 0f, 0f, 3, -1, 1f); musicVolume[i] = 0f }
        }
        val target = floatArrayOf(.2f, if (chain >= 10) .24f else 0f, if (chain >= 20) .2f else 0f, if (chain >= 35) .15f else 0f)
        for (i in layers.indices) {
            if (musicStreams[i] == 0) continue
            val step = 1.6f * dt
            musicVolume[i] += (target[i] - musicVolume[i]).coerceIn(-step, step)
            sounds.setVolume(musicStreams[i], musicVolume[i], musicVolume[i])
        }
    }

    private fun stopMusic() {
        for (i in musicStreams.indices) {
            if (musicStreams[i] != 0) pool?.stop(musicStreams[i])
            musicStreams[i] = 0; musicVolume[i] = 0f
        }
    }

    private fun stopHum() {
        if (humStream != 0) pool?.stop(humStream)
        humStream = 0
    }
}
