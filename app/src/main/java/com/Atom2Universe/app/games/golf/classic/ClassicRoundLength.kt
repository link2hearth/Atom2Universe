package com.Atom2Universe.app.games.golf.classic

import com.Atom2Universe.app.R

/** Each section has its own saves and records; full rounds keep the legacy preference file. */
internal enum class ClassicRoundLength(val startIndex: Int, val count: Int, val labelRes: Int) {
    NINE(0, 9, R.string.golf_front_nine),
    BACK_NINE(9, 9, R.string.golf_back_nine),
    FULL(0, 18, R.string.golf_full_round);

    val lastIndex get() = startIndex + count - 1
    fun holes(course: ClassicCourseDefinition) = course.holes.subList(startIndex, startIndex + count)
    fun nextIndex(completedHoles: Int) = startIndex + completedHoles
    fun progressName(course: ClassicCourseDefinition) = when (this) {
        FULL -> course.progressName
        NINE -> "${course.progressName}_9"
        BACK_NINE -> "${course.progressName}_back9"
    }
    fun finished(scores: List<Int>) = scores.size >= count
}
