package com.Atom2Universe.app.games.golf.classic

import com.Atom2Universe.app.R

/**
 * Each section has its own saves and records; full rounds keep the legacy preference file.
 * [WHOLE] is the round of a generated course, whatever its number of holes.
 */
internal enum class ClassicRoundLength(val startIndex: Int, private val fixedCount: Int, val labelRes: Int) {
    NINE(0, 9, R.string.golf_front_nine),
    BACK_NINE(9, 9, R.string.golf_back_nine),
    FULL(0, 18, R.string.golf_full_round),
    WHOLE(0, 0, R.string.golf_random_round);

    fun count(course: ClassicCourseDefinition) = if (this == WHOLE) course.holes.size else fixedCount
    fun lastIndex(course: ClassicCourseDefinition) = startIndex + count(course) - 1
    fun holes(course: ClassicCourseDefinition) = course.holes.subList(startIndex, startIndex + count(course))
    fun nextIndex(completedHoles: Int) = startIndex + completedHoles
    fun progressName(course: ClassicCourseDefinition) = progressName(course.progressName)
    /** From the course's own preference name alone, without its holes. */
    fun progressName(base: String) = when (this) {
        FULL -> base
        NINE -> "${base}_9"
        BACK_NINE -> "${base}_back9"
        WHOLE -> "${base}_all"
    }
    /** Par of the section; for a generated course read from its seed. */
    fun par(course: ClassicCourseDefinition) = if (this == WHOLE) course.par else holes(course).sumOf { it.par }
    fun finished(scores: List<Int>, course: ClassicCourseDefinition) = scores.size >= count(course)

    companion object {
        /** The sections offered on the fixed 18-hole courses. */
        val STANDARD = listOf(NINE, BACK_NINE, FULL)
    }
}
