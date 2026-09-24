package com.Atom2Universe.app.games.starbridges

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

/** L'atlas céleste de Constellations : le ciel de toutes les figures tracées. */
class ConstellationAtlasActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_constellation_atlas)
        enableImmersiveMode()

        val entries = ConstellationAtlas(this).entries()
        findViewById<ConstellationAtlasView>(R.id.atlas_view).setEntries(entries)
        findViewById<TextView>(R.id.atlas_count).text =
            resources.getQuantityString(R.plurals.starbridges_atlas_count, entries.size, entries.size)
        findViewById<ImageButton>(R.id.atlas_back).setOnClickListener { finish() }
    }
}
