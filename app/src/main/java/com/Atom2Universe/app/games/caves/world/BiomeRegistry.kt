package com.Atom2Universe.app.games.caves.world

import android.content.res.AssetManager
import org.json.JSONObject

internal object BiomeRegistry {

    private val _cave        = mutableListOf<CaveBiomeDef>()
    private val _surface     = mutableListOf<SurfaceBiomeDef>()
    private val _underground = mutableListOf<SurfaceBiomeDef>()

    val caveBiomes:        List<CaveBiomeDef>    get() = _cave
    val surfaceBiomes:     List<SurfaceBiomeDef> get() = _surface
    val undergroundBiomes: List<SurfaceBiomeDef> get() = _underground

    fun load(assets: AssetManager) {
        if (_cave.isNotEmpty()) return
        loadDir(assets, "caves/biomes/cave")         { _cave        += CaveBiomeDef.fromJson(it)    }
        loadDir(assets, "caves/biomes/surface")      { _surface     += SurfaceBiomeDef.fromJson(it) }
        loadDir(assets, "caves/biomes/underground")  { _underground += SurfaceBiomeDef.fromJson(it) }
    }

    private fun loadDir(assets: AssetManager, dir: String, onDef: (JSONObject) -> Unit) {
        val files = assets.list(dir) ?: return
        for (file in files) {
            if (!file.endsWith(".json")) continue
            val text = assets.open("$dir/$file").bufferedReader().readText()
            onDef(JSONObject(text))
        }
    }
}
