package com.Atom2Universe.app.science.parentes

import com.Atom2Universe.app.R

/** Explanations of tree reading, with independently checked bibliography. */
object ParentesReading {
    data class Section(val title: Int, val body: Int, val url: String)
    val sections = listOf(
        Section(R.string.pt_read_ancestor_title, R.string.pt_read_ancestor_body,
            "https://evolution.berkeley.edu/phylogenetic-systematics/reading-trees-a-quick-review/"),
        Section(R.string.pt_read_clade_title, R.string.pt_read_clade_body,
            "https://evolution.berkeley.edu/evolution-101/the-history-of-life-looking-at-the-patterns/understanding-phylogenies/"),
        Section(R.string.pt_read_order_title, R.string.pt_read_order_body,
            "https://evolution.berkeley.edu/evolution-101/the-history-of-life-looking-at-the-patterns/trees-not-ladders/"),
        Section(R.string.pt_read_cousin_title, R.string.pt_read_cousin_body,
            "https://evolution.berkeley.edu/evolution-101/the-history-of-life-looking-at-the-patterns/trees-not-ladders/"),
        Section(R.string.pt_read_uncertain_title, R.string.pt_read_uncertain_body,
            "https://evolution.berkeley.edu/phylogenetic-systematics/reading-trees-a-quick-review/phylogenetic-pitchforks/"),
        Section(R.string.pt_read_length_title, R.string.pt_read_length_body,
            "https://evolution.berkeley.edu/phylogenetic-systematics/reading-trees-a-quick-review/"),
    )
}
