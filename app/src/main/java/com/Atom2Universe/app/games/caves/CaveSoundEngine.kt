package com.Atom2Universe.app.games.caves

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import com.Atom2Universe.app.games.caves.node.EventBus
import com.Atom2Universe.app.games.caves.node.GameEvent

/** Preloaded licensed recordings and the original shotgun. See caves/audio/CREDITS.md. */
internal class CaveSoundEngine(private val context: Context) {
    private var pool: SoundPool? = null
    private val ids = mutableMapOf<String, Int>()
    private val loaded = mutableSetOf<Int>()
    private val lastPlayed = mutableMapOf<String, Long>()
    private val streams = mutableMapOf<Int, Long>()
    private var paused = false
    private var shot = 0
    private var animalVoice = 0
    private var footstepStream = 0
    private var footstepSurface = ""
    private var footstepRate = 1f

    @Synchronized fun start() {
        if (pool != null) return
        val sounds = SoundPool.Builder().setMaxStreams(12).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        pool = sounds
        sounds.setOnLoadCompleteListener { source, id, status ->
            synchronized(this) { if (source === pool && status == 0) loaded.add(id) }
        }
        val names = listOf("gun", "smg", "shotgun", "lever_rifle").flatMap { name ->
            (0..2).map { "${name}_$it" }
        } + listOf("bow", "crossbow", "sling", "hurt", "boss") +
            listOf("stone", "earth", "wood").map { surface -> "step_${surface}_loop" } +
            listOf("cow", "sheep", "pig", "chicken").flatMap { species -> (0..1).map { "animal_${species}_$it" } }
        for (name in names) {
            try {
                context.assets.openFd("caves/audio/$name.wav").use { ids[name] = sounds.load(it, 1) }
            } catch (e: Exception) { Log.w("CaveAudio", "Cannot load $name", e) }
        }
    }

    @Synchronized fun pause() {
        paused = true
        stopFootsteps()
        streams.keys.forEach { pool?.stop(it) }
        streams.clear()
    }
    @Synchronized fun resume() { paused = false }
    @Synchronized fun destroy() {
        paused = true
        stopFootsteps()
        pool?.release()
        pool = null
        ids.clear(); loaded.clear(); streams.clear(); lastPlayed.clear()
    }

    fun subscribe(bus: EventBus) {
        bus.subscribe { event ->
            when (event) {
                is GameEvent.WeaponFired -> onShot(event.weaponType)
                is GameEvent.WeaponReload -> Unit
                is GameEvent.Footstep -> onFootstep(event)
                is GameEvent.AnimalCall -> onAnimal(event)
                is GameEvent.MobNearby -> Unit // Detection is silent; no unrelated creaking cue.
                // The shot carries the hit feedback; no extra "tac" on contact.
                is GameEvent.MobHit -> Unit
                is GameEvent.PlayerHit -> play("hurt", .7f, 5, cooldown = 200)
                is GameEvent.MobDied -> Unit // Sonivox ding handled by the activity.
                is GameEvent.BossSpawned -> play("boss", .72f, 4)
                is GameEvent.EnemyFired -> onShot(event.weaponType, enemy = true)
                is GameEvent.SoldierDown -> Unit
            }
        }
    }

    @Synchronized private fun onShot(type: String, enemy: Boolean = false) {
        val family = if (type == "dual_pistols") "gun" else type
        val name = if (family in listOf("gun", "smg", "shotgun", "lever_rifle")) {
            val variant = shot
            shot = (shot + 1) % 3
            "${family}_$variant"
        } else family
        if (enemy) {
            // Same sample family as the player, quieter in the mix; bound crowd audio.
            play(name, if (family == "smg") .30f else .38f, 1, "enemy", 65)
        } else {
            play(name, if (family == "smg") .46f else .64f, 3)
        }
    }

    @Synchronized private fun onFootstep(event: GameEvent.Footstep) {
        if (paused || !event.moving) { stopFootsteps(); return }
        val sounds = pool ?: return
        val id = ids["step_${event.surface}_loop"] ?: return
        if (id !in loaded) return
        val volume = if (event.running) .30f else .19f
        val rate = (.46f / event.interval).coerceIn(.5f, 2f)
        if (footstepStream == 0 || footstepSurface != event.surface) {
            stopFootsteps()
            // The PCM buffer contains exactly one step and its gap. Native looping owns
            // every beat, independent of render frame timing. Highest priority prevents
            // one-shot gunfire from stealing this continuous stream.
            footstepStream = sounds.play(id, volume, volume, 10, -1, rate)
            footstepSurface = event.surface
            footstepRate = rate
        } else {
            sounds.setVolume(footstepStream, volume, volume)
            if (kotlin.math.abs(rate - footstepRate) > .04f) {
                sounds.setRate(footstepStream, rate)
                footstepRate = rate
            }
        }
    }

    private fun stopFootsteps() {
        if (footstepStream != 0) pool?.stop(footstepStream)
        footstepStream = 0
        footstepSurface = ""
    }

    @Synchronized private fun onAnimal(event: GameEvent.AnimalCall) {
        play("animal_${event.species}_$animalVoice", event.volume, 1, "animal", 3000, event.pan)
        animalVoice = (animalVoice + 1) % 2
    }

    @Synchronized private fun play(name: String, volume: Float, priority: Int,
                                    group: String = name, cooldown: Long = 0, pan: Float = 0f) {
        if (paused) return
        val sounds = pool ?: return
        val id = ids[name] ?: return
        if (id !in loaded) return
        val now = SystemClock.uptimeMillis()
        if (lastPlayed[group]?.let { now - it < cooldown } == true) return
        streams.entries.removeAll { it.value <= now }
        val stream = sounds.play(id, volume * (1f - pan.coerceAtLeast(0f)),
            volume * (1f + pan.coerceAtMost(0f)), priority, 0, 1f)
        if (stream != 0) {
            lastPlayed[group] = now
            // Animal recordings can last over two seconds; retain their IDs through the tail.
            streams[stream] = now + 4000
        }
    }
}
