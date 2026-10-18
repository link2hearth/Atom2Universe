package com.Atom2Universe.app.games.roguelike

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.billthefarmer.mididriver.MidiDriver

/** Original scores played on the Sonivox instance owned by RoguelikeSoundEngine.
 * All calls and MIDI writes run on the activity's main coroutine scope.
 */
internal class DungeonProceduralMusic(private val scope: CoroutineScope) {
    enum class Mode { EXPLORE, BATTLE, VICTORY, DEFEAT, SILENT }
    private data class Scene(val region: DungeonSoundtrack.Region, val mode: Mode)
    private data class Bookmark(var version: Int = 0, var bar: Int = 0)

    private var scene = Scene(DungeonSoundtrack.Region.CRYPT, Mode.EXPLORE)
    private val bookmarks = mutableMapOf<DungeonSoundtrack.Region, Bookmark>()
    private var musicJob: Job? = null
    private var running = false
    private var playingEnding = false
    private var endingPlayed = false

    fun setScene(theme: DungeonTheme, backdrop: DungeonBackdrop?, mode: Mode) {
        val next = Scene(DungeonSoundtrack.region(theme, backdrop), mode)
        if (next == scene) return
        scene = next
        // Resolve the short cue even when the result panel is dismissed quickly.
        // A chained fight or a new result always takes priority.
        if (playingEnding && mode in listOf(Mode.EXPLORE, Mode.SILENT)) return
        endingPlayed = false
        restart()
    }

    fun start() {
        if (running) return
        running = true
        restart()
    }

    fun stop() {
        if (!running) return
        running = false
        musicJob?.cancel()
        musicJob = null
        playingEnding = false
        silence()
    }

    private fun restart() {
        if (!running) return
        // Synchronous cleanup: a cancelled coroutine must never silence its successor
        // or write to the native driver after the activity has stopped that driver.
        musicJob?.cancel()
        val requestedMode = scene.mode
        playingEnding = !endingPlayed && requestedMode in listOf(Mode.VICTORY, Mode.DEFEAT)
        silence()
        musicJob = scope.launch {
            delay(60)
            val mode = requestedMode
            if (mode == Mode.VICTORY || mode == Mode.DEFEAT) {
                if (endingPlayed) return@launch
                playingEnding = true
                val score = DungeonSoundtrack.ending(mode == Mode.VICTORY)
                configure(score)
                for (bar in score.bars) playBar(bar, score.tempo)
                silence()
                playingEnding = false
                endingPlayed = true
            }
            while (isActive) {
                val current = scene
                if (current.mode != Mode.EXPLORE && current.mode != Mode.BATTLE) break
                val exploring = current.mode == Mode.EXPLORE
                val bookmark = bookmarks.getOrPut(current.region) { Bookmark() }
                val versions = DungeonSoundtrack.exploration(current.region)
                val score = if (exploring) versions[bookmark.version % versions.size]
                    else DungeonSoundtrack.battle(current.region)
                configure(score)
                val firstBar = if (exploring) bookmark.bar else 0
                for (index in firstBar until score.bars.size) {
                    // Resume at the next measure when interrupted by combat.
                    if (exploring) {
                        bookmark.bar = (index + 1) % score.bars.size
                        if (bookmark.bar == 0) bookmark.version = (bookmark.version + 1) % versions.size
                    }
                    playBar(score.bars[index], score.tempo)
                }
            }
        }
    }

    private suspend fun playBar(bar: DungeonScore.Bar, tempo: Int) {
        // Absolute deadlines avoid cumulative MIDI write/delay overhead. A UI stall
        // shifts the clock instead of playing all the missed notes in a burst.
        val tickMs = 60_000.0 / tempo / DungeonScore.TICKS_PER_BEAT
        var origin = SystemClock.uptimeMillis()
        for (event in bar.events) {
            val deadline = origin + (event.tick * tickMs).toLong()
            val remaining = deadline - SystemClock.uptimeMillis()
            if (remaining > 0) delay(remaining)
            else if (remaining < -150) origin -= remaining
            driver().queueEvent(event.bytes)
        }
        val remaining = origin + (bar.ticks * tickMs).toLong() - SystemClock.uptimeMillis()
        if (remaining > 0) delay(remaining)
    }

    private fun configure(score: DungeonScore) {
        score.voices.forEachIndexed { channel, voice ->
            cc(channel, 121, 0)
            cc(channel, 0, 0)
            cc(channel, 32, 0)
            driver().queueEvent(byteArrayOf((0xC0 or channel).toByte(), voice.program.toByte()))
            driver().queueEvent(byteArrayOf((0xE0 or channel).toByte(), 0, 64))
            cc(channel, 7, voice.volume)
            cc(channel, 10, voice.pan)
            cc(channel, 11, 100)
            cc(channel, 91, 28)
            cc(channel, 93, 0)
        }
        // Channel 9 also belongs to SFX: leave its program, volume and controllers alone.
    }

    private fun silence() {
        for (channel in 0..5) {
            cc(channel, 64, 0)
            cc(channel, 123, 0)
            cc(channel, 120, 0)
        }
        DungeonScore.MUSIC_DRUMS.forEach { pitch ->
            driver().queueEvent(byteArrayOf(0x89.toByte(), pitch.toByte(), 0))
        }
    }

    private fun driver() = MidiDriver.getInstance()
    private fun cc(channel: Int, control: Int, value: Int) = driver().queueEvent(
        byteArrayOf((0xB0 or channel).toByte(), control.toByte(), value.toByte())
    )
}
