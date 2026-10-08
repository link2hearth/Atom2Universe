package com.Atom2Universe.app.audio

import android.content.Context
import com.Atom2Universe.app.R
import com.Atom2Universe.app.util.ImmersiveDialog

/** Les dialogues personnalisés gardent leur contenu, leur taille et leurs interactions. */
open class AudioDialog(context: Context) : ImmersiveDialog(context, R.style.ThemeOverlay_A2U_Audio_Dialog) {
    override fun onStart() {
        super.onStart()
        AudioStyle.styleDialog(this)
    }
}
