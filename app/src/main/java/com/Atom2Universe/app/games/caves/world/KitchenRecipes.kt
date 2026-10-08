package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.CraftDef
import com.Atom2Universe.app.games.caves.node.FarmItems
import com.Atom2Universe.app.games.caves.node.ExpeditionItems as E
import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import com.Atom2Universe.app.games.caves.node.KitchenItems as K
import com.Atom2Universe.app.games.farm.FarmCrop
import com.Atom2Universe.app.games.farm.FarmCrop.*
import com.Atom2Universe.app.games.caves.world.FrontierWorkshops.Recipe

/** Every ingredient comes from an existing crop, livestock product or fishing catch. */
internal object KitchenRecipes {
    private fun crop(crop: FarmCrop) = FarmItems.produce(FarmItems.crops.indexOf(crop).also { require(it>=0) })
    private fun cook(result: Short, count: Int, seconds: Int, vararg input: Pair<Short,Int>): Recipe {
        val ingredients=input.toMap()
        require(ingredients.size==input.size)
        val output=linkedMapOf(result to count)
        ingredients[F.MILK]?.let { output[BUCKET_EMPTY]=it }
        return Recipe(F.COOKER,ingredients,output,seconds)
    }
    val workshops: List<Recipe> = listOf(
        Recipe(F.VAT,mapOf(F.MILK to 1),mapOf(K.BUTTER to 3,BUCKET_EMPTY to 1),25),
        Recipe(F.VAT,mapOf(F.MILK to 1),mapOf(K.CREAM to 3,BUCKET_EMPTY to 1),20),
        Recipe(F.MILL,mapOf(crop(CORN) to 1),mapOf(K.CORNMEAL to 3),6,true),
        cook(K.STEAK,1,8,K.BEEF to 1), cook(K.PORK_CHOP,1,8,K.PORK to 1),
        cook(K.ROAST_CHICKEN,1,8,K.CHICKEN to 1), cook(K.LAMB_CHOP,1,8,K.MUTTON to 1),
        // Bread is the base of the menu: what goes with it decides how deep the dish heals.
        cook(K.EGG_SANDWICH,2,10,F.BREAD to 2,F.EGG to 2,crop(LETTUCE) to 1),
        cook(K.BURGER,2,12,F.BREAD to 2,K.STEAK to 1,crop(LETTUCE) to 1),
        cook(K.CHICKEN_BURGER,2,12,F.BREAD to 2,K.ROAST_CHICKEN to 1,crop(LETTUCE) to 1,crop(TOMATO) to 1),
        cook(K.BURGER_FRIES,2,16,F.BREAD to 2,K.STEAK to 1,crop(POTATO) to 2,crop(LETTUCE) to 1,crop(TOMATO) to 1),
        cook(K.HOT_DOG,2,12,F.BREAD to 2,K.PORK_CHOP to 1,crop(ONION) to 1),
        cook(K.PIZZA,2,14,F.FLOUR to 2,crop(TOMATO) to 2,F.CHEESE to 1),
        cook(K.PIZZA_SUPREME,3,18,F.FLOUR to 2,crop(TOMATO) to 2,F.CHEESE to 1,K.PORK_CHOP to 1,crop(PEPPER) to 1),
        cook(K.TACOS,3,16,K.CORNMEAL to 2,K.STEAK to 1,crop(TOMATO) to 1,crop(CHILI) to 1),
        cook(K.TOMATO_SOUP,2,10,crop(TOMATO) to 3,K.BUTTER to 1),
        cook(K.PEA_SOUP,2,10,crop(PEAS) to 3,crop(ONION) to 1,F.BREAD to 1),
        cook(K.LEEK_SOUP,3,12,crop(LEEK) to 2,crop(POTATO) to 2,K.CREAM to 1),
        cook(K.CHICKEN_SOUP,3,16,K.CHICKEN to 2,crop(CARROT) to 1,crop(LEEK) to 1),
        cook(K.BEEF_STEW,3,20,K.BEEF to 2,crop(POTATO) to 2,crop(CARROT) to 1,crop(ONION) to 1),
        cook(K.LAMB_STEW,3,20,K.MUTTON to 2,crop(PEAS) to 2,crop(CARROT) to 1,crop(ONION) to 1),
        cook(K.SHEPHERDS_PIE,3,20,K.MUTTON to 2,crop(POTATO) to 3,crop(CARROT) to 1,K.BUTTER to 1),
        cook(K.STUFFED_PEPPER,2,16,crop(PEPPER) to 2,K.BEEF to 1,crop(ONION) to 1),
        cook(K.STUFFED_TOMATO,2,16,crop(TOMATO) to 2,K.PORK to 1,crop(ONION) to 1),
        cook(K.PORK_SKEWER,2,14,K.PORK to 1,crop(PEPPER) to 1,crop(ONION) to 1),
        cook(K.CHICKEN_PIE,3,18,K.CHICKEN to 2,F.FLOUR to 2,crop(PEAS) to 1,K.CREAM to 1),
        cook(K.GRILLED_CORN,2,8,crop(CORN) to 2,K.BUTTER to 1),
        cook(K.BROCCOLI_GRATIN,2,14,crop(BROCCOLI) to 2,F.CHEESE to 1,K.CREAM to 1),
        cook(K.CAULIFLOWER_GRATIN,2,14,crop(CAULIFLOWER) to 2,F.CHEESE to 1,K.CREAM to 1),
        cook(K.EGGPLANT_BAKE,2,14,crop(EGGPLANT) to 2,crop(TOMATO) to 1,F.CHEESE to 1),
        cook(K.CHEESE_QUICHE,3,16,F.FLOUR to 2,F.EGG to 2,F.CHEESE to 1,K.CREAM to 1),
        cook(K.LEEK_QUICHE,3,16,F.FLOUR to 2,F.EGG to 2,crop(LEEK) to 2,K.CREAM to 1),
        cook(K.EGG_SALAD,2,10,F.EGG to 2,crop(LETTUCE) to 1,crop(RADISH) to 1),
        // Flour pastries: the wheat field feeds them from the first day.
        cook(K.CORNBREAD,4,12,K.CORNMEAL to 2,F.MILK to 1,F.EGG to 1),
        cook(K.SHORTBREAD,4,10,F.FLOUR to 2,K.BUTTER to 1,F.EGG to 1),
        cook(K.FRENCH_TOAST,3,10,F.BREAD to 2,F.EGG to 1,F.MILK to 1),
        cook(K.BRIOCHE,3,16,F.FLOUR to 2,K.BUTTER to 1,F.EGG to 1,F.MILK to 1),
        cook(K.STRAWBERRY_JAM,2,12,crop(STRAWBERRY) to 4),
        cook(K.RASPBERRY_JAM,2,12,crop(RASPBERRY) to 4),
        cook(K.BLUEBERRY_JAM,2,12,crop(BLUEBERRY) to 4),
        cook(K.PINEAPPLE_CAKE,4,18,F.FLOUR to 2,F.EGG to 2,K.BUTTER to 1,crop(PINEAPPLE) to 2),
        cook(K.CUSTARD,3,12,F.MILK to 1,F.EGG to 2)
    ) + listOf(E.RIVER_FISH,E.CAVE_FISH,E.DEEP_FISH).map { fish ->
        cook(K.FISH_AND_CHIPS,2,16,fish to 1,crop(POTATO) to 2,F.BREAD to 1)
    } + listOf(K.STRAWBERRY_JAM,K.RASPBERRY_JAM,K.BLUEBERRY_JAM).map { jam ->
        cook(K.BERRY_PANCAKES,2,8,F.PANCAKE to 2,jam to 1,K.CREAM to 1)
    }

    val crafts = listOf(
        CraftDef(listOf(crop(CORN) to 1),K.CORNMEAL,tools=listOf(F.MORTAR)),
        CraftDef(listOf(F.BREAD to 1,F.CHEESE to 1,crop(LETTUCE) to 1),K.CHEESE_SANDWICH,2),
        CraftDef(listOf(F.BREAD to 1,K.BUTTER to 1,crop(RADISH) to 1),K.RADISH_TARTINE,2)
    ) + listOf(STRAWBERRY,RASPBERRY,BLUEBERRY).map { berry ->
        CraftDef(listOf(crop(PINEAPPLE) to 1,crop(berry) to 2),K.FRUIT_SALAD,2)
    }
}
