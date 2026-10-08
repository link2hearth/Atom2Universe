package com.Atom2Universe.app.science.timeline

import androidx.annotation.StringRes
import com.Atom2Universe.app.R

/** Reading categories, not claims that disciplines have exclusive boundaries. */
enum class ScienceField(@param:StringRes val label: Int) {
    MATHEMATICS(R.string.ct_science_field_mathematics),
    PHYSICS(R.string.ct_science_field_physics),
    ASTRONOMY(R.string.ct_science_field_astronomy),
    CHEMISTRY(R.string.ct_science_field_chemistry),
    BIOLOGY(R.string.ct_science_field_biology),
    MEDICINE(R.string.ct_science_field_medicine),
    EARTH(R.string.ct_science_field_earth),
    COMPUTING(R.string.ct_science_field_computing)
}

enum class HistoryTopic(@param:StringRes val label: Int, private val science: ScienceField? = null) {
    ALL(R.string.ct_history_topic_all),
    HISTORY(R.string.ct_history_topic_general),
    SCIENCE(R.string.ct_history_topic_science),
    MATHEMATICS(ScienceField.MATHEMATICS.label, ScienceField.MATHEMATICS),
    PHYSICS(ScienceField.PHYSICS.label, ScienceField.PHYSICS),
    ASTRONOMY(ScienceField.ASTRONOMY.label, ScienceField.ASTRONOMY),
    CHEMISTRY(ScienceField.CHEMISTRY.label, ScienceField.CHEMISTRY),
    BIOLOGY(ScienceField.BIOLOGY.label, ScienceField.BIOLOGY),
    MEDICINE(ScienceField.MEDICINE.label, ScienceField.MEDICINE),
    EARTH(ScienceField.EARTH.label, ScienceField.EARTH),
    COMPUTING(ScienceField.COMPUTING.label, ScienceField.COMPUTING);

    fun matches(entry: HumanLandmark) = when (this) {
        ALL -> true
        HISTORY -> entry.science == null
        SCIENCE -> entry.science != null
        else -> entry.science == science
    }
}
