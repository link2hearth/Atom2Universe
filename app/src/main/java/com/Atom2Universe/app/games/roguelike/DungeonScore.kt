package com.Atom2Universe.app.games.roguelike

/** Deterministic score format. Pitches are MIDI semitones, never scale indices.
 * Written melodies use note names and eighth-note durations (R is a rest).
 */
internal data class DungeonScore(val tempo: Int, val voices: List<Voice>, val bars: List<Bar>) {
    data class Voice(val program: Int, val volume: Int, val pan: Int = 64)
    data class Event(val tick: Int, val bytes: ByteArray)
    data class Bar(val ticks: Int, val events: List<Event>)
    enum class Style { PASTORAL, CAVERN, CRYPT, SEA, HAVEN, SPACE, BATTLE, SEA_BATTLE, SPACE_BATTLE }
    data class Harmony(val bass: Int, val tones: List<Int>)

    companion object {
        const val TICKS_PER_BEAT = 24
        // Disjoint from RoguelikeSoundEngine's percussion notes.
        val MUSIC_DRUMS = listOf(42, 45, 47, 50, 54)

        fun pitch(name: String): Int {
            val semitone = when (name.first()) {
                'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11
                else -> error("Invalid note: $name")
            }
            val accidental = when (name.getOrNull(1)) { '#' -> 1; 'b' -> -1; else -> 0 }
            val octave = name.substring(if (accidental == 0) 1 else 2).toInt()
            return ((octave + 1) * 12 + semitone + accidental).also { require(it in 0..127) }
        }

        fun harmony(bass: String, tones: String) = Harmony(pitch(bass), tones.split(" ").map(::pitch))

        /** A/B/A'/B': 16 written measures, 32 arranged measures. The reprise breathes;
         * the second and last phrases add a quiet answering voice.
         */
        fun compose(tempo: Int, style: Style, voices: List<Voice>, chords: List<Harmony>, melody: String): DungeonScore {
            val phrases = melody.trim().split("|").map(String::trim)
            require(phrases.size == 16 && chords.size == 16)
            require(voices.size == 6)
            val sea = style == Style.SEA || style == Style.SEA_BATTLE
            val battle = style in listOf(Style.BATTLE, Style.SEA_BATTLE, Style.SPACE_BATTLE)
            val eighths = if (sea) 6 else 8
            val ticks = eighths * 12
            val bars = (0 until 32).map { index ->
                val source = index % 16
                val section = index / 8
                val h = chords[source]
                val b = Builder(ticks)
                var at = 0
                val melodyVelocity = if (battle) 76 else 62
                for (token in phrases[source].split(Regex("\\s+"))) {
                    val (name, duration) = token.split(":")
                    val length = duration.toInt() * 12
                    require(length > 0)
                    if (name != "R" && (section != 2 || source % 4 != 3 || battle)) {
                        b.note(0, pitch(name), at, length - 2, melodyVelocity + if (at == 0) 4 else 0)
                    }
                    at += length
                }
                require(at == ticks) { "Melody measure ${source + 1}: $at ticks, expected $ticks" }

                val sparse = style == Style.CAVERN || style == Style.CRYPT
                val pulse = if (sea) 36 else 48
                b.note(2, h.bass, 0, if (sparse) ticks - 3 else pulse - 3, if (battle) 70 else 56)
                if (!sparse) b.note(2, h.bass + if (source % 4 == 3) 0 else 7, pulse, ticks - pulse - 3, if (battle) 58 else 44)

                // Upper voicings leave room for the bass and melody.
                if (!battle || source % 2 == 0) h.tones.forEach {
                    b.note(3, it, 0, ticks - 2, if (battle) 34 else if (sparse) 32 else 27)
                }
                when (style) {
                    Style.SEA, Style.SEA_BATTLE -> {
                        // Lilting 6/8: bass on 1/4, chord answers on 2/3/5/6.
                        for (step in listOf(1, 2, 4, 5)) h.tones.take(3).forEach {
                            b.note(1, it, step * 12, 8, if (battle) 47 else 35)
                        }
                        b.note(9, 54, 0, 4, if (battle) 36 else 22)
                        b.note(9, 54, 36, 4, if (battle) 29 else 18)
                    }
                    Style.CAVERN, Style.CRYPT -> {
                        for (step in listOf(0, 3, 6)) {
                            b.note(1, h.tones[(step / 3 + source) % h.tones.size] + 12, step * 12, 20, 34)
                        }
                    }
                    Style.HAVEN -> {
                        for (step in 0 until eighths step 2) {
                            b.note(1, h.tones[(step / 2) % h.tones.size], step * 12, 21, 42)
                        }
                    }
                    else -> {
                        val pattern = intArrayOf(0, 1, 2, 1, 0, 2, 1, 2)
                        for (step in 0 until eighths) {
                            if (!battle && section == 0 && step % 2 == 1) continue
                            val note = h.tones[pattern[step] % h.tones.size]
                            b.note(1, note, step * 12, if (battle) 8 else 10,
                                (if (battle) 50 else 38) + if (step % 4 == 0) 6 else 0)
                        }
                    }
                }
                // Chord-tone replies only at phrase ends, below the melody.
                if ((section == 1 || section == 3) && source % 4 >= 2) {
                    b.note(4, h.tones.last(), ticks / 2, ticks / 4 - 2, 39)
                    b.note(4, h.tones[1], ticks * 3 / 4, ticks / 4 - 2, 34)
                }
                if (battle) {
                    b.note(5, h.bass, 0, 10, 62)
                    b.note(5, h.bass + 7, pulse, 10, 48)
                    for (step in 0 until eighths step 2) b.note(9, 42, step * 12, 4, if (step == 0) 32 else 24)
                    b.note(9, 45, if (sea) 36 else 24, 5, 47)
                    if (!sea) b.note(9, 45, 72, 5, 42)
                    if (source % 4 == 3) {
                        b.note(9, 47, ticks - 18, 4, 39)
                        b.note(9, 50, ticks - 6, 4, 35)
                    }
                }
                b.build()
            }
            return DungeonScore(tempo, voices, bars)
        }
    }

    class Builder(private val ticks: Int) {
        private val events = mutableListOf<Event>()
        fun note(channel: Int, pitch: Int, start: Int, length: Int, velocity: Int) {
            require(channel in 0..9 && pitch in 0..127 && velocity in 1..127)
            require(start >= 0 && length > 0 && start + length <= ticks)
            events += Event(start, byteArrayOf((0x90 or channel).toByte(), pitch.toByte(), velocity.toByte()))
            events += Event(start + length, byteArrayOf((0x80 or channel).toByte(), pitch.toByte(), 0))
        }
        fun build() = Bar(ticks, events.sortedWith(compareBy<Event> { it.tick }
            .thenBy { it.bytes[0].toInt() and 0xF0 })) // Note-offs before simultaneous note-ons.
    }
}
