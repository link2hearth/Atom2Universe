package com.Atom2Universe.app.games.golf.classic

import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.golf.classic.core.ClassicCourse
import com.Atom2Universe.app.games.golf.classic.core.ClassicHole
import com.Atom2Universe.app.games.golf.classic.core.HeatherCourse
import com.Atom2Universe.app.games.golf.classic.core.WildDetoursCourse
import com.Atom2Universe.app.games.golf.classic.core.VertigoCourse
import com.Atom2Universe.app.games.golf.classic.core.ArchipelagoCourse
import com.Atom2Universe.app.games.golf.classic.core.SnowPeaksCourse

/** Stable IDs keep saved rounds separate when more courses are added or reordered. */
data class ClassicCourseDefinition(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val holeNamesRes: Int,
    val tipsRes: Int,
    val progressName: String,
    val holes: List<ClassicHole>
) {
    val par: Int get() = holes.sumOf { it.par }
    val length: Int get() = holes.sumOf { it.length.toInt() }
}

object ClassicCourses {
    val all = listOf(
        ClassicCourseDefinition("gardens", R.string.classic_course, R.string.classic_gardens_description,
            R.array.classic_holes, R.array.classic_gardens_tips,
            "classic_golf_v2", ClassicCourse.holes), // Existing saves/records stay in their original file.
        ClassicCourseDefinition("heather", R.string.classic_heather_course, R.string.classic_heather_description,
            R.array.classic_heather_holes, R.array.classic_heather_tips,
            "classic_golf_v2_heather", HeatherCourse.holes),
        ClassicCourseDefinition("wild_detours", R.string.classic_wild_course, R.string.classic_wild_description,
            R.array.classic_wild_holes, R.array.classic_wild_tips,
            "classic_golf_v2_wild_detours", WildDetoursCourse.holes),
        ClassicCourseDefinition("vertigo", R.string.classic_vertigo_course, R.string.classic_vertigo_description,
            R.array.classic_vertigo_holes, R.array.classic_vertigo_tips,
            "classic_golf_v2_vertigo", VertigoCourse.holes),
        ClassicCourseDefinition("archipelago", R.string.classic_archipelago_course, R.string.classic_archipelago_description,
            R.array.classic_archipelago_holes, R.array.classic_archipelago_tips,
            "classic_golf_v2_archipelago", ArchipelagoCourse.holes),
        ClassicCourseDefinition("snow_peaks", R.string.classic_snow_course, R.string.classic_snow_description,
            R.array.classic_snow_holes, R.array.classic_snow_tips,
            "classic_golf_v2_snow_peaks", SnowPeaksCourse.holes)
    )

    fun find(id: String?): ClassicCourseDefinition = all.firstOrNull { it.id == id } ?: all.first()
}
