package com.Atom2Universe.app.science.biology

import androidx.annotation.StringRes
import com.Atom2Universe.app.R

/** Jeux de données indépendants, rendus un seul à la fois avec les mêmes commandes. */
enum class AnatomyAtlas(val id: String, val directory: String, @StringRes val label: Int, @StringRes val coverage: Int) {
    MALE("male", "science/biology", R.string.bio_atlas_male, R.string.bio_dataset_detail),
    FEMALE("female", "science/biology/female", R.string.bio_atlas_female, R.string.bio_female_coverage),
    EAR("ear", "science/biology/ear", R.string.bio_atlas_ear, R.string.bio_ear_coverage);

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: MALE
    }
}
