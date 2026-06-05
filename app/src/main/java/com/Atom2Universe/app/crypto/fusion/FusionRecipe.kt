package com.Atom2Universe.app.crypto.fusion

import com.Atom2Universe.app.R

data class ElementInput(val atomicNumber: Int, val count: Int)

sealed class FusionOutput {
    data class Element(val atomicNumber: Int) : FusionOutput()
    object H2O : FusionOutput()
}

enum class FusionRecipe(
    val id: String,
    val inputs: List<ElementInput>,
    val output: FusionOutput,
    val baseRate: Float,
    val nameRes: Int,
    val scienceRes: Int,
    val gameInfoRes: Int,
    val palier: Int,
    val unlockParentId: String?
) {
    PROTON_PROTON(
        id = "pp",
        inputs = listOf(ElementInput(1, 4)),
        output = FusionOutput.Element(2),
        baseRate = 0.75f,
        nameRes = R.string.fusion_recipe_pp_name,
        scienceRes = R.string.fusion_recipe_pp_science,
        gameInfoRes = R.string.fusion_recipe_pp_game,
        palier = 1,
        unlockParentId = null
    ),
    TRIPLE_ALPHA(
        id = "triple_alpha",
        inputs = listOf(ElementInput(2, 3)),
        output = FusionOutput.Element(6),
        baseRate = 0.45f,
        nameRes = R.string.fusion_recipe_triple_alpha_name,
        scienceRes = R.string.fusion_recipe_triple_alpha_science,
        gameInfoRes = R.string.fusion_recipe_triple_alpha_game,
        palier = 2,
        unlockParentId = "pp"
    ),
    CARBON_FUSION(
        id = "carbon_fusion",
        inputs = listOf(ElementInput(6, 2)),
        output = FusionOutput.Element(10),
        baseRate = 0.35f,
        nameRes = R.string.fusion_recipe_carbon_fusion_name,
        scienceRes = R.string.fusion_recipe_carbon_fusion_science,
        gameInfoRes = R.string.fusion_recipe_carbon_fusion_game,
        palier = 3,
        unlockParentId = "triple_alpha"
    ),
    ALPHA_CAPTURE(
        id = "alpha_capture",
        inputs = listOf(ElementInput(10, 2)),
        output = FusionOutput.Element(8),
        baseRate = 0.40f,
        nameRes = R.string.fusion_recipe_alpha_capture_name,
        scienceRes = R.string.fusion_recipe_alpha_capture_science,
        gameInfoRes = R.string.fusion_recipe_alpha_capture_game,
        palier = 4,
        unlockParentId = "carbon_fusion"
    ),
    OXYGEN_FUSION(
        id = "oxygen_fusion",
        inputs = listOf(ElementInput(8, 2)),
        output = FusionOutput.Element(16),
        baseRate = 0.30f,
        nameRes = R.string.fusion_recipe_oxygen_fusion_name,
        scienceRes = R.string.fusion_recipe_oxygen_fusion_science,
        gameInfoRes = R.string.fusion_recipe_oxygen_fusion_game,
        palier = 5,
        unlockParentId = "alpha_capture"
    ),
    WATER(
        id = "water",
        inputs = listOf(ElementInput(16, 2)),
        output = FusionOutput.Element(26),
        baseRate = 0.20f,
        nameRes = R.string.fusion_recipe_water_name,
        scienceRes = R.string.fusion_recipe_water_science,
        gameInfoRes = R.string.fusion_recipe_water_game,
        palier = 6,
        unlockParentId = "oxygen_fusion"
    );

    companion object {
        fun byId(id: String): FusionRecipe? = values().firstOrNull { it.id == id }
    }
}
