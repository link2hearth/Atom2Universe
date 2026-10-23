package com.Atom2Universe.app.games.bigger

import android.content.Intent
import android.os.Bundle
import com.Atom2Universe.app.ThemedActivity

/** Conserve les anciens raccourcis du hub parent vers Accrétion. */
class BiggerMenuActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, BiggerActivity::class.java))
        finish()
    }
}
