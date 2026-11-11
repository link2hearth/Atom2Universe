package com.Atom2Universe.app.studio.dsp

/** Tessitures des zones mélodiques de Perso.sf2, en numéros de notes MIDI inclusifs. */
internal object OrchestralRanges {
    class Zone(val keys: IntRange, val velocities: IntRange = 0..127)
    private val programs = Array(128) { program ->
        val keys = when (program) {
            0, 48 -> 21..108
            10 -> 1..127
            16, 57, 76, 84, 85 -> 0..115
            18, 52, 87, 110 -> 12..127
            24 -> 23..120
            25 -> 24..108
            26, 28, 36, 37, 127 -> 0..84
            27, 53 -> 0..120
            34 -> 26..127
            39 -> 0..96
            42 -> 0..121
            45 -> 0..112
            47 -> 38..60
            50 -> 0..113
            51, 54, 61, 90, 96, 125 -> 0..108
            60 -> 24..84
            66 -> 38..127
            68 -> 58..88
            89 -> 0..110
            111 -> 0..126
            112 -> 12..115
            115, 117 -> 0..103
            else -> 0..127
        }
        if (program == 26) listOf(Zone(keys), Zone(85..85, 83..127)) else listOf(Zone(keys))
    }

    fun zones(program: Int) = programs[program]
    fun contains(program: Int, key: Int, velocity: Int) =
        programs[program].any { key in it.keys && velocity in it.velocities }
}
