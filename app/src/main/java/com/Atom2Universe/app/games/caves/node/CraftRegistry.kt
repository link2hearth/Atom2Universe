package com.Atom2Universe.app.games.caves.node

import android.content.res.AssetManager
import org.json.JSONObject

internal object CraftRegistry {

    private val recipes = mutableListOf<CraftDef>()

    fun load(assets: AssetManager) {
        if (recipes.isNotEmpty()) return
        val files = assets.list("caves/crafts") ?: return
        for (file in files) {
            if (!file.endsWith(".json")) continue
            val json = assets.open("caves/crafts/$file").bufferedReader().readText()
            recipes += CraftDef.fromJson(JSONObject(json))
        }
        recipes += com.Atom2Universe.app.games.caves.world.KitchenRecipes.crafts
    }

    fun all(): List<CraftDef> = recipes
}
