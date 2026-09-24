package com.Atom2Universe.app.audio

import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity

/** Habillage partagé de la suite audio ; conserve les couleurs du thème sélectionné. */
open class AudioThemedActivity : ThemedActivity() {
    override val moduleThemeOverlay: Int = R.style.ThemeOverlay_A2U_Audio
}
