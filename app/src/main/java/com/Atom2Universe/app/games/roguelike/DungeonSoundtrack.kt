package com.Atom2Universe.app.games.roguelike

import com.Atom2Universe.app.games.roguelike.DungeonScore.Style
import com.Atom2Universe.app.games.roguelike.DungeonScore.Voice

/** Original, hand-written themes. GM programs are zero-based for Sonivox.
 * Each exploration/battle cue has two distinct eight-bar melodies, arranged over
 * 32 measures. No random pitch generation and no third-party recordings.
 */
internal object DungeonSoundtrack {
    enum class Region { OUTDOOR, CAVERN, CRYPT, PIRATE, HAVEN, SPACE }

    fun region(theme: DungeonTheme, backdrop: DungeonBackdrop?): Region {
        // Combat scenery can differ from the player's tile (chapel, cabin, ambush).
        if (backdrop != null) return when (backdrop) {
            DungeonBackdrop.CRYPT, DungeonBackdrop.CEMETERY_DAY, DungeonBackdrop.CEMETERY_NIGHT -> Region.CRYPT
            DungeonBackdrop.DUNGEON, DungeonBackdrop.MINE -> Region.CAVERN
            DungeonBackdrop.PIRATE_DECK_DAY, DungeonBackdrop.PIRATE_DECK_NIGHT, DungeonBackdrop.CAPTAIN_CABIN -> Region.PIRATE
            DungeonBackdrop.INN, DungeonBackdrop.LIBRARY, DungeonBackdrop.MONASTERY_DAY, DungeonBackdrop.MONASTERY_NIGHT -> Region.HAVEN
            DungeonBackdrop.SPACESHIP -> Region.SPACE
            else -> Region.OUTDOOR
        }
        return when (theme) {
            DungeonTheme.CEMETERY, DungeonTheme.CRYPT -> Region.CRYPT
            DungeonTheme.DUNGEON, DungeonTheme.MINE, DungeonTheme.MINE_DEPOT -> Region.CAVERN
            DungeonTheme.PIRATE, DungeonTheme.PIRATE_CABIN, DungeonTheme.PORT -> Region.PIRATE
            DungeonTheme.LIBRARY, DungeonTheme.MONASTERY, DungeonTheme.INN, DungeonTheme.VILLAGE -> Region.HAVEN
            DungeonTheme.SPACESHIP -> Region.SPACE
            else -> Region.OUTDOOR
        }
    }

    fun exploration(region: Region): List<DungeonScore> = when (region) {
        Region.OUTDOOR -> listOf(path, meadow)
        Region.CAVERN -> listOf(cavern)
        Region.CRYPT -> listOf(crypt)
        Region.PIRATE -> listOf(sailing)
        Region.HAVEN -> listOf(hearth)
        Region.SPACE -> listOf(orbit)
    }

    fun battle(region: Region): DungeonScore = when (region) {
        Region.OUTDOOR, Region.HAVEN -> skirmish
        Region.CAVERN, Region.CRYPT -> depths
        Region.PIRATE -> boarding
        Region.SPACE -> pursuit
    }

    // Bass is separate from the close upper voicing. Minor/major thirds are explicit.
    private val harmonies = mapOf(
        "C" to DungeonScore.harmony("C3", "G3 C4 E4"),
        "Dm" to DungeonScore.harmony("D3", "A3 D4 F4"),
        "Em" to DungeonScore.harmony("E3", "G3 B3 E4"),
        "F" to DungeonScore.harmony("F2", "A3 C4 F4"),
        "G" to DungeonScore.harmony("G2", "G3 B3 D4"),
        "Am" to DungeonScore.harmony("A2", "A3 C4 E4"),
        "Bb" to DungeonScore.harmony("Bb2", "Bb3 D4 F4"),
        "Gm" to DungeonScore.harmony("G2", "Bb3 D4 G4"),
        "A" to DungeonScore.harmony("A2", "A3 C#4 E4"),
        "E" to DungeonScore.harmony("E3", "G#3 B3 E4"),
        "D" to DungeonScore.harmony("D3", "A3 D4 F#4"),
        "Bm" to DungeonScore.harmony("B2", "B3 D4 F#4"),
        "B" to DungeonScore.harmony("B2", "B3 D#4 F#4")
    )

    private fun voices(lead: Int, pluck: Int, bass: Int = 42, pad: Int = 48, answer: Int = 71) = listOf(
        Voice(lead, 76, 68), Voice(pluck, 61, 46), Voice(bass, 70),
        Voice(pad, 46, 76), Voice(answer, 54, 84), Voice(47, 58)
    )

    private fun cue(tempo: Int, style: Style, voices: List<Voice>, chords: String, melody: String) =
        DungeonScore.compose(tempo, style, voices,
            chords.split(" ").map { harmonies.getValue(it) }, melody)

    // The winding path: flute, nylon guitar, cello. G major, 4/4, ~85 seconds.
    private val path by lazy { cue(90, Style.PASTORAL, voices(73, 24),
        "G D Em C G Am C D Em Bm C G Am Em C D", """
        R:2 D5:1 G5:1 B5:2 A5:2 | F#5:3 E5:1 D5:2 R:2 |
        E5:2 G5:1 B5:1 A5:2 G5:2 | E5:4 D5:2 R:2 |
        D5:1 G5:1 B5:2 D6:2 B5:2 | C6:3 B5:1 A5:2 G5:2 |
        E5:2 G5:2 C6:2 B5:1 A5:1 | F#5:2 E5:1 F#5:1 D5:2 R:2 |
        B5:2 G5:1 E5:1 G5:2 B5:2 | A5:2 F#5:2 D5:3 R:1 |
        G5:2 E5:2 C5:1 D5:1 E5:2 | D5:2 B4:2 G4:2 R:2 |
        A4:1 C5:1 E5:2 A5:2 G5:2 | G5:3 E5:1 B4:2 E5:2 |
        E5:2 G5:1 A5:1 G5:2 E5:2 | F#5:2 A5:2 D5:2 R:2
        """) }

    // Open fields: oboe/harp, warmer F major and longer, singing answers.
    private val meadow by lazy { cue(82, Style.PASTORAL, voices(68, 46, answer = 73),
        "F C Dm Bb F Gm C C Bb F Gm Dm Bb C F C", """
        A4:2 C5:1 F5:1 G5:2 A5:2 | G5:3 E5:1 C5:2 R:2 |
        F5:2 A5:2 D6:1 C6:1 A5:2 | Bb5:2 A5:1 G5:1 F5:2 R:2 |
        A5:2 G5:1 F5:1 C5:2 A4:2 | Bb4:2 D5:2 G5:3 F5:1 |
        E5:2 G5:2 C6:2 B5:1 A5:1 | G5:4 R:2 E5:1 G5:1 |
        F5:3 D5:1 Bb4:2 D5:2 | C5:2 A4:2 F4:2 R:2 |
        G4:1 Bb4:1 D5:2 G5:2 A5:2 | A5:3 F5:1 D5:2 R:2 |
        D5:2 F5:1 Bb5:1 A5:2 F5:2 | E5:2 D5:1 E5:1 G5:2 E5:2 |
        F5:6 R:2 | E5:2 G5:2 C5:2 R:2
        """) }

    // Lanterns below: clarinet, distant vibraphone, sparse A-minor phrases.
    private val cavern by lazy { cue(72, Style.CAVERN, voices(71, 11, 43, 89, 73),
        "Am F C G Am F Dm E C G F Am Dm Am E E", """
        E5:3 A5:1 G5:2 E5:2 | F5:4 C5:2 R:2 |
        E5:2 G5:2 E5:1 D5:1 C5:2 | D5:4 B4:2 R:2 |
        A4:2 C5:1 E5:1 A5:2 E5:2 | F5:3 E5:1 C5:2 R:2 |
        D5:2 F5:2 E5:2 D5:2 | B4:2 G#4:2 E4:2 R:2 |
        G5:4 E5:2 C5:2 | B4:3 D5:1 G5:2 R:2 |
        A5:2 G5:1 F5:1 E5:2 C5:2 | E5:4 A4:2 R:2 |
        F5:2 E5:1 D5:1 A4:2 D5:2 | C5:3 B4:1 A4:2 E5:2 |
        G#5:2 F#5:1 E5:1 B4:2 E5:2 | G#5:2 B5:2 E5:2 R:2
        """) }

    // Stone and candles: restrained oboe, celesta and choir; a D-minor lament.
    private val crypt by lazy { cue(66, Style.CRYPT, voices(68, 8, 43, 52, 42),
        "Dm Bb Gm A Dm C Bb A F C Gm Dm Bb Gm A A", """
        A4:2 D5:3 E5:1 F5:2 | D5:4 Bb4:2 R:2 |
        D5:2 G5:2 F5:2 D5:2 | C#5:4 A4:2 R:2 |
        F5:3 E5:1 D5:2 A4:2 | G4:2 C5:2 E5:2 R:2 |
        F5:2 D5:2 Bb4:3 A4:1 | A4:4 C#5:2 R:2 |
        C5:2 F5:2 A5:2 G5:2 | E5:4 C5:2 R:2 |
        Bb4:2 D5:1 G5:1 A5:2 G5:2 | F5:3 E5:1 D5:2 R:2 |
        D5:2 F5:2 Bb5:2 A5:2 | G5:2 F5:1 D5:1 Bb4:2 D5:2 |
        E5:3 D5:1 C#5:2 A4:2 | E5:4 C#5:2 R:2
        """) }

    // Salt on the deck: accordion, nylon guitar and fiddle in a gentle 6/8.
    private val sailing by lazy { cue(126, Style.SEA, voices(21, 24, 32, 48, 40),
        "Dm C Bb A Dm Gm A Dm F C Gm Dm Bb Gm A A", """
        D5:1 F5:1 A5:1 A5:2 G5:1 | E5:2 C5:1 G5:2 E5:1 |
        F5:1 D5:1 Bb4:1 D5:2 F5:1 | E5:3 C#5:2 A4:1 |
        D5:1 E5:1 F5:1 A5:2 F5:1 | G5:2 Bb5:1 A5:1 G5:1 F5:1 |
        E5:1 A5:1 G5:1 E5:2 C#5:1 | D5:3 R:2 A4:1 |
        C5:1 F5:1 A5:1 C6:2 A5:1 | G5:2 E5:1 C5:2 E5:1 |
        D5:1 G5:1 Bb5:1 A5:2 G5:1 | F5:2 E5:1 D5:3 |
        Bb4:1 D5:1 F5:1 Bb5:2 A5:1 | G5:1 F5:1 D5:1 G5:2 F5:1 |
        E5:1 C#5:1 A4:1 E5:1 G5:1 A5:1 | C#6:2 B5:1 A5:2 R:1
        """) }

    // The hearth: classical guitar and harp; a quiet major-key resting place.
    private val hearth by lazy { cue(78, Style.HAVEN, voices(24, 46, 42, 48, 73),
        "C G Am F C Em Dm G F C Dm Am F G C G", """
        E5:3 D5:1 C5:2 G4:2 | B4:2 D5:2 G5:2 R:2 |
        E5:2 A5:2 G5:1 E5:1 C5:2 | A4:4 C5:2 R:2 |
        G4:1 C5:1 E5:2 G5:2 E5:2 | B4:2 E5:2 G5:3 E5:1 |
        F5:2 E5:1 D5:1 A4:2 D5:2 | B4:4 G4:2 R:2 |
        C5:2 F5:2 A5:2 G5:2 | E5:3 D5:1 C5:2 R:2 |
        D5:2 F5:1 A5:1 F5:2 E5:2 | E5:4 C5:2 A4:2 |
        A4:2 C5:2 F5:2 E5:1 D5:1 | B4:2 D5:2 G5:2 F5:2 |
        E5:2 D5:1 C5:1 C5:2 R:2 | B4:2 D5:2 G4:2 R:2
        """) }

    // Orbital lights: soft electric piano, music-box arpeggio, warm synth pad.
    private val orbit by lazy { cue(86, Style.SPACE, voices(4, 10, 38, 89, 81),
        "Em C G D Em C Am B G D C Em Am Em B B", """
        B4:3 E5:1 G5:2 F#5:2 | E5:4 C5:2 R:2 |
        D5:2 G5:2 B5:1 A5:1 G5:2 | F#5:3 E5:1 D5:2 R:2 |
        E5:1 G5:1 B5:2 E6:2 B5:2 | C6:3 B5:1 G5:2 E5:2 |
        A5:2 G5:2 E5:2 C5:2 | D#5:4 B4:2 R:2 |
        B5:2 A5:1 G5:1 D5:2 G5:2 | A5:4 F#5:2 R:2 |
        G5:2 E5:2 C5:2 D5:2 | B4:3 E5:1 G5:2 R:2 |
        A5:2 E5:2 C5:1 D5:1 E5:2 | G5:3 F#5:1 E5:2 B4:2 |
        F#5:2 D#5:2 B4:2 D#5:2 | F#5:4 D#5:2 R:2
        """) }

    // Steel on the road: horn melody, staccato strings, bass and light toms.
    private val skirmish by lazy { cue(126, Style.BATTLE, voices(60, 45, 43, 48, 68),
        "Dm Bb F C Dm Gm Bb A F C Dm Am Bb Gm A A", """
        D5:1 R:1 D5:1 F5:1 A5:3 G5:1 | F5:2 D5:1 F5:1 Bb5:2 A5:2 |
        A5:1 G5:1 F5:2 C5:1 F5:1 A5:2 | G5:3 E5:1 C5:2 R:2 |
        D5:1 F5:1 A5:1 D6:1 C6:2 A5:2 | Bb5:2 A5:1 G5:1 D5:2 G5:2 |
        F5:1 Bb5:1 A5:2 F5:1 D5:1 Bb4:2 | C#5:2 E5:1 G5:1 A5:2 R:2 |
        C6:3 A5:1 F5:2 A5:2 | G5:2 E5:1 G5:1 C6:2 B5:1 A5:1 |
        A5:2 F5:1 D5:1 F5:2 A5:2 | G5:2 E5:2 C5:2 R:2 |
        D5:1 F5:1 Bb5:2 A5:1 F5:1 D5:2 | D5:1 G5:1 Bb5:2 A5:2 G5:2 |
        E5:1 A5:1 C#6:2 B5:1 A5:1 G5:2 | E5:2 C#5:2 A4:2 R:2
        """) }

    // Beneath the stones: low strings take the lead, with a rising minor sequence.
    private val depths by lazy { cue(112, Style.BATTLE, voices(42, 45, 43, 52, 60),
        "Am F Dm E Am G F E C G Dm Am F Dm E E", """
        A4:1 E4:1 A4:2 C5:1 B4:1 A4:2 | C5:3 A4:1 F4:2 R:2 |
        D5:1 A4:1 D5:2 F5:1 E5:1 D5:2 | B4:2 G#4:2 E4:2 R:2 |
        E5:1 A5:1 G5:2 E5:1 C5:1 A4:2 | B4:2 D5:1 G5:1 F5:2 D5:2 |
        C5:1 F5:1 A5:2 G5:2 F5:2 | E5:3 D5:1 B4:2 G#4:2 |
        G5:2 E5:1 G5:1 C6:2 B5:1 G5:1 | D5:2 G5:2 B5:2 A5:1 G5:1 |
        F5:1 E5:1 D5:2 A4:1 D5:1 F5:2 | E5:3 C5:1 A4:2 R:2 |
        A4:1 C5:1 F5:2 E5:1 C5:1 A4:2 | D5:2 F5:1 A5:1 G5:2 F5:2 |
        E5:1 B4:1 E5:2 G#5:2 F#5:1 E5:1 | B4:2 G#4:2 E4:2 R:2
        """) }

    // Boarding party: fiddle and accordion, a faster jig with a separate melody.
    private val boarding by lazy { cue(168, Style.SEA_BATTLE, voices(40, 21, 43, 48, 73),
        "Dm Bb C A Dm Gm A Dm F C Bb A Gm Dm A A", """
        A5:1 D6:1 A5:1 F5:1 D5:1 F5:1 | Bb5:2 A5:1 F5:2 D5:1 |
        G5:1 C6:1 G5:1 E5:1 C5:1 E5:1 | A5:2 G5:1 E5:2 C#5:1 |
        D5:1 F5:1 A5:1 D6:2 C6:1 | Bb5:1 A5:1 G5:1 D5:2 G5:1 |
        A5:1 G5:1 E5:1 C#5:1 E5:1 A5:1 | F5:1 E5:1 D5:1 D5:2 R:1 |
        A5:1 C6:1 A5:1 F5:2 A5:1 | G5:1 E5:1 C5:1 G5:2 E5:1 |
        F5:1 Bb5:1 A5:1 F5:1 D5:1 Bb4:1 | C#5:1 E5:1 A5:1 G5:2 E5:1 |
        D5:1 G5:1 Bb5:1 A5:1 G5:1 F5:1 | E5:1 F5:1 A5:1 D6:2 A5:1 |
        C#6:1 B5:1 A5:1 E5:1 G5:1 A5:1 | E5:2 C#5:1 A4:2 R:1
        """) }

    // Red alert: rounded synth lead and muted synth bass, syncopated E minor.
    private val pursuit by lazy { cue(132, Style.SPACE_BATTLE, voices(81, 4, 38, 89, 80),
        "Em C Am B Em D C B G D Am Em C Am B B", """
        E5:1 R:1 B4:1 E5:1 G5:3 F#5:1 | E5:2 C5:1 E5:1 G5:2 R:2 |
        A5:1 E5:1 C5:2 E5:1 A5:1 G5:2 | F#5:2 D#5:1 F#5:1 B5:2 R:2 |
        B5:1 G5:1 E5:2 G5:1 B5:1 E6:2 | D6:3 A5:1 F#5:2 D5:2 |
        E5:1 G5:1 C6:2 B5:1 G5:1 E5:2 | D#5:2 F#5:2 B4:2 R:2 |
        G5:1 B5:1 D6:2 B5:1 A5:1 G5:2 | F#5:2 A5:1 D6:1 C6:2 A5:2 |
        A5:3 G5:1 E5:2 C5:2 | B4:1 E5:1 G5:2 F#5:1 E5:1 B4:2 |
        C5:1 E5:1 G5:2 C6:2 B5:1 G5:1 | A5:2 E5:1 C5:1 E5:2 A5:2 |
        F#5:1 B5:1 D#6:2 C#6:1 B5:1 F#5:2 | D#5:2 F#5:2 B4:2 R:2
        """) }

    fun ending(victory: Boolean) = if (victory) victoryCue else defeatCue

    // Two measures, including release time: ~3.6 s victory, ~4.8 s defeat.
    private val victoryCue by lazy {
        val first = DungeonScore.Builder(96).apply {
            listOf("C4", "E4", "G4").forEach { note(3, DungeonScore.pitch(it), 0, 90, 44) }
            note(2, 48, 0, 90, 65)
            note(0, 67, 0, 10, 75); note(0, 72, 12, 10, 78)
            note(0, 76, 24, 20, 81); note(0, 79, 48, 32, 84)
        }.build()
        val last = DungeonScore.Builder(96).apply {
            listOf(65, 69, 72).forEach { note(3, it, 0, 22, 40) }
            note(0, 81, 0, 20, 77); note(0, 79, 24, 10, 73)
            note(0, 76, 36, 10, 70); note(0, 72, 48, 35, 72)
            listOf(60, 64, 67).forEach { note(3, it, 48, 35, 43) }
            note(2, 48, 48, 35, 60)
        }.build()
        DungeonScore(132, voices(60, 46), listOf(first, last))
    }

    private val defeatCue by lazy {
        val first = DungeonScore.Builder(96).apply {
            listOf(57, 60, 64).forEach { note(3, it, 0, 92, 33) }
            note(2, 45, 0, 92, 48)
            note(0, 76, 0, 22, 55); note(0, 72, 24, 22, 50)
            note(0, 71, 48, 22, 46); note(0, 69, 72, 22, 43)
        }.build()
        val last = DungeonScore.Builder(96).apply {
            listOf(53, 57, 60).forEach { note(3, it, 0, 22, 30) }
            note(0, 65, 0, 22, 42); note(0, 64, 24, 20, 38)
            listOf(57, 60, 64).forEach { note(3, it, 48, 32, 28) }
            note(0, 69, 48, 32, 36); note(2, 45, 48, 32, 40)
        }.build()
        DungeonScore(100, voices(0, 46, 42, 48), listOf(first, last))
    }
}
