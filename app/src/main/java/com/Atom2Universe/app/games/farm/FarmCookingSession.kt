package com.Atom2Universe.app.games.farm

import com.Atom2Universe.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

enum class FarmCookingStep(val label: Int, val help: Int) {
    CHOP(R.string.farm_kitchen_chop, R.string.farm_kitchen_chop_help),
    STIR(R.string.farm_kitchen_stir, R.string.farm_kitchen_stir_help),
    ASSEMBLE(R.string.farm_kitchen_assemble, R.string.farm_kitchen_assemble_help)
}

/** Gesture progress only. No clock, stock or farm mutation until the final preparation transaction. */
class FarmCookingSession(val recipe: FarmRecipe) {
    val steps: List<FarmCookingStep> = when (recipe) {
        FarmRecipe.SCARF, FarmRecipe.GARDEN_BOUQUET -> emptyList()
        FarmRecipe.EGG_SANDWICH, FarmRecipe.CHEESE_SANDWICH, FarmRecipe.EGG_SALAD -> listOf(FarmCookingStep.ASSEMBLE)
        FarmRecipe.CHEESE -> listOf(FarmCookingStep.STIR)
        FarmRecipe.CORNBREAD, FarmRecipe.CORN_FRITTERS, FarmRecipe.BERRY_PANCAKES -> listOf(FarmCookingStep.STIR, FarmCookingStep.ASSEMBLE)
        FarmRecipe.SOUP, FarmRecipe.PUMPKIN_SOUP, FarmRecipe.MUTTON_STEW, FarmRecipe.TOMATO_SOUP,
        FarmRecipe.TRUFFLE_PAN, FarmRecipe.PICKLES, FarmRecipe.APPLE_COMPOTE, FarmRecipe.PEAR_SYRUP,
        FarmRecipe.CHERRY_JAM, FarmRecipe.STRAWBERRY_JAM -> listOf(FarmCookingStep.CHOP, FarmCookingStep.STIR)
        else -> listOf(FarmCookingStep.CHOP, FarmCookingStep.ASSEMBLE)
    }
    var index = 0
        private set
    val complete get() = steps.isNotEmpty() && index == steps.size
    val step get() = steps.getOrNull(index)
    val sandwich get() = recipe == FarmRecipe.EGG_SANDWICH || recipe == FarmRecipe.CHEESE_SANDWICH
    val layerLabel: Int? get() = if (!sandwich || step != FarmCookingStep.ASSEMBLE) null else when (layers) {
        0 -> R.string.farm_kitchen_bread_bottom
        1 -> R.string.farm_lettuce
        2 -> if (recipe == FarmRecipe.EGG_SANDWICH) R.string.farm_product_egg else R.string.farm_recipe_cheese
        3 -> R.string.farm_kitchen_bread_top
        else -> null
    }
    private val slices = BooleanArray(4)
    fun chopped(index: Int) = slices.getOrElse(index) { false }
    var layers = 0
        private set
    private var turn = 0.0
    private var previousAngle: Double? = null
    private var direction = 0
    val progress: Float get() = when (step) {
        FarmCookingStep.CHOP -> slices.count { it } / 4f
        FarmCookingStep.STIR -> (turn / (PI * 4)).toFloat().coerceIn(0f, 1f)
        FarmCookingStep.ASSEMBLE -> layers / 4f
        null -> if (complete) 1f else 0f
    }
    private fun normalized(vararg values: Float) = values.all { it.isFinite() && it in 0f..1f }
    fun drag(x: Float, y: Float, toX: Float, toY: Float): Boolean {
        if (!normalized(x, y, toX, toY)) return false
        return when (step) {
            FarmCookingStep.CHOP -> {
                val target = (0..3).firstOrNull { abs(x - (.2f + it * .2f)) <= .085f &&
                    abs(toX - (.2f + it * .2f)) <= .085f }
                if (target == null || slices[target] || y > .38f || toY < .7f) false
                else { slices[target] = true; true }
            }
            FarmCookingStep.ASSEMBLE -> {
                val targetX = .14f + layers * .24f
                if (layers >= 4 || abs(x - targetX) > .095f || y !in .12f.. .36f ||
                    hypot(toX - .5f, toY - .73f) > .18f) false
                else { layers++; true }
            }
            else -> false
        }
    }
    /** A continuous path in the pot. Ignore reversals and jumps instead of penalizing mistakes. */
    fun stir(x: Float, y: Float): Boolean {
        if (step != FarmCookingStep.STIR || !normalized(x, y)) { previousAngle = null; return false }
        val dx = (x - .5f).toDouble(); val dy = (y - .53f).toDouble()
        if (hypot(dx, dy) !in .13.. .42) { previousAngle = null; return false }
        val angle = atan2(dy, dx)
        val previous = previousAngle; previousAngle = angle
        if (previous == null) return false
        var delta = angle - previous
        if (delta > PI) delta -= PI * 2
        if (delta < -PI) delta += PI * 2
        if (abs(delta) > PI / 2 || abs(delta) < .002) return false
        val sign = if (delta > 0) 1 else -1
        if (direction == 0) direction = sign
        if (direction != sign) return false
        turn = (turn + abs(delta)).coerceAtMost(PI * 4)
        return true
    }
    fun endStroke() { previousAngle = null }
    /** Alternative input for assistive technologies and players who prefer buttons. */
    fun assist(): Boolean {
        when (step) {
            FarmCookingStep.CHOP -> slices.indexOfFirst { !it }.takeIf { it >= 0 }?.let { slices[it] = true } ?: return false
            FarmCookingStep.STIR -> turn = (turn + PI / 2).coerceAtMost(PI * 4)
            FarmCookingStep.ASSEMBLE -> if (layers < 4) layers++ else return false
            null -> return false
        }
        return true
    }
    fun next(): Boolean {
        if (step == null || progress < .99999f) return false
        index++; slices.fill(false); layers = 0; turn = 0.0; direction = 0; previousAngle = null
        return true
    }
}
