package com.Atom2Universe.app.games.hamlet

import com.Atom2Universe.app.games.hamlet.AdventureResource.*

/** Every working shop has an explicit trade. Decorative buildings keep their neighbourhood role.
 * Crop quantities stay below pantry capacity even at rank five. Long jobs use larger material lots,
 * never hundreds of a crop that cannot fit in the granary. Rare rewards still require adventure. */
object HamletTrades {
    fun recipe(id: String, rank: Int, balanced: Boolean = false): HamletIndustry.Recipe {
        val n = rank.coerceIn(1, HamletEconomy.MAX_TIER).toLong()
        // Long offline contracts retain their hourly output instead of stopping at four hours' worth.
        // Version two is retained for ingredients already paid in older saves.
        val lots = ((Hamlet.byId[id]?.job ?: 900) / 900).coerceIn(1, if (balanced) Int.MAX_VALUE else 16).toLong()
        val adjustedOutput = if (!balanced) null else when (id) {
            "dairy" -> mapOf(MILK to 20L)
            "barn" -> mapOf(MILK to 72L, EGGS to 72L)
            "eggfarm" -> mapOf(EGGS to 160L)
            "fishing" -> mapOf(FISH to 64L)
            "apothecary" -> mapOf(MEDICINE to 96L)
            "greenhouse" -> mapOf(MEDICINE to 28L)
            "conservatory" -> mapOf(MEDICINE to 36L)
            "skygarden" -> mapOf(MEDICINE to 96L)
            "prismforge" -> mapOf(TOOLS to 72L)
            "lighthouse", "lighthouseBlue", "lighthouseStone", "lighthouseGrand", "clocktower" -> mapOf(BOOKS to 24 * lots)
            "stables", "ridingschool" -> mapOf(BOOKS to 16 * lots)
            else -> null
        }
        fun make(inputs: Map<AdventureResource, Long> = emptyMap(), crops: Map<String, Int> = emptyMap(),
            outputs: Map<AdventureResource, Long> = emptyMap(), coins: Long = 0) = HamletIndustry.Recipe(
            VillageCost(coins = coins * n, resources = inputs.mapValues { it.value * n },
                crops = crops.mapValues { it.value * n.toInt() }), (adjustedOutput ?: outputs).mapValues { it.value * n })
        fun food(crops: Map<String, Int> = emptyMap(), inputs: Map<AdventureResource, Long> = emptyMap(), amount: Long = (if (balanced) 80 else 24) * lots) =
            make(inputs + (WATER to 4 * lots), crops, mapOf(FOOD to amount))
        val craftValue = if (!balanced) 2 else when (id) {
            "jeweler", "gemcutter", "clockmaker", "shipyard", "balloon" -> 8
            "potter", "stonemason", "glassworks", "painter", "lanternmaker", "boathouse", "perfumer" -> 4
            else -> 2
        }
        fun craft(inputs: Map<AdventureResource, Long>, crops: Map<String, Int> = emptyMap()) =
            make(inputs, crops, mapOf(CRAFTS to craftValue * lots))
        return when (id) {
            "bakery", "lumbermill", "quarry", "mine", "blacksmith", "adventureForge", "armory",
            "library", "academy", "adventureAcademy", "grandacademy", "cartographers",
            "adventureInn", "adventureStore", "adventureYard", "barracks", "adventureGate", "waypost" ->
                if (balanced && id == "barracks") make(mapOf(BREAD to 2 * lots, TIMBER to 6 * lots), outputs = mapOf(FOOD to 80 * lots))
                else HamletIndustry.originalRecipe(id, rank)

            // Food, orchards and livestock: fresh produce becomes supplies for heroes.
            "greengrocer" -> food(mapOf("carrots" to 2, "lettuce" to 2))
            "farmmarket" -> food(mapOf("carrots" to 2), mapOf(FRUIT to 2, EGGS to 2))
            "jamkitchen" -> food(mapOf("strawberries" to 2), mapOf(FRUIT to 4, TIMBER to 2))
            "cheesemaker" -> food(inputs = mapOf(MILK to 6, TIMBER to 2))
            "dairy" -> make(mapOf(WATER to 6), mapOf("corn" to 3), mapOf(MILK to 8))
            "barn" -> make(mapOf(WATER to 12), mapOf("oats" to 3), mapOf(MILK to 12, EGGS to 8))
            "eggfarm" -> make(mapOf(WATER to 8), mapOf("corn" to 4), mapOf(EGGS to 16))
            "butcher" -> food(inputs = mapOf(FOOD to 40, TIMBER to 10), amount = if (balanced) 640 else 80)
            "honeyshop" -> food(inputs = mapOf(HONEY to 8, FRUIT to 8))
            "cider" -> food(inputs = mapOf(FRUIT to 12, TIMBER to 8))
            "brewery" -> food(mapOf("wheat" to 4), mapOf(TIMBER to 12))
            "vineyard", "winecellar" -> food(mapOf("grapes" to 4), mapOf(TIMBER to 12))
            "teahouse" -> food(mapOf("tea" to 4), mapOf(HONEY to 6))
            "spicemarket" -> food(mapOf("peppers" to 3, "garlic" to 3))
            "pumpkinstand" -> food(mapOf("pumpkins" to 4))
            "juicebar" -> food(mapOf("watermelon" to 2), mapOf(FRUIT to 8))
            "berryfarm" -> food(mapOf("blueberries" to 3, "strawberries" to 3))
            "soupkitchen" -> food(mapOf("potatoes" to 4, "leeks" to 2), mapOf(TIMBER to 12))
            "tavern" -> food(inputs = mapOf(BREAD to 4, EGGS to 4, TIMBER to 4), amount = 100)
            "fishmonger" -> make(outputs = mapOf(FISH to 8), coins = 40)
            "fishing" -> make(mapOf(TIMBER to 12, CLOTH to 2), mapOf("corn" to 2), mapOf(FISH to 32))
            "fishmarket" -> food(inputs = mapOf(FISH to 12, TIMBER to 8))
            "windmill", "watermill" -> food(mapOf("wheat" to 6), mapOf(TIMBER to 8), amount = if (balanced) 80 * lots else 144)
            "cornmill" -> food(mapOf("corn" to 8), mapOf(TIMBER to 12))

            // Wool unlocks weaving at level five; cotton also supplies late-game balloon makers.
            "weaver" -> make(mapOf(WOOL to 6, WATER to 4), outputs = mapOf(CLOTH to 4))
            "weavery" -> make(mapOf(WOOL to 12, WATER to 8), outputs = mapOf(CLOTH to 10))
            "tailor", "hatter" -> craft(mapOf(CLOTH to 2 * lots))
            "cobbler" -> craft(mapOf(CLOTH to 2 * lots, TIMBER to lots))
            "florist" -> craft(mapOf(WATER to 4), mapOf("wildflowers" to 2))
            "chandler" -> craft(mapOf(WAX to 2 * lots, TIMBER to lots))
            "toymaker", "carpenters" -> craft(mapOf(TIMBER to 12 * lots))
            "potter" -> craft(mapOf(STONE to 8 * lots, TIMBER to 4 * lots, WATER to 2 * lots))
            "stonemason" -> craft(mapOf(STONE to 12 * lots, TOOLS to lots))
            "glassworks" -> craft(mapOf(STONE to 10 * lots, TIMBER to 8 * lots))
            "painter" -> craft(mapOf(CLOTH to lots, TIMBER to 4 * lots), mapOf("beets" to 2))
            "lanternmaker" -> craft(mapOf(ORE to 4 * lots, WAX to 2 * lots))
            "jeweler", "gemcutter" -> craft(mapOf(ORE to 6 * lots, CRYSTAL to lots))
            "foundry", "bellfoundry", "locksmith", "weaponsmith", "shieldmaker", "fletcher" ->
                make(mapOf(ORE to 4 * lots, TIMBER to 8 * lots), outputs = mapOf(TOOLS to 8 * lots))
            "clockmaker" -> craft(mapOf(TOOLS to 2 * lots, ORE to 4 * lots))
            "prismforge" -> make(mapOf(ORE to 24, CRYSTAL to 4, TIMBER to 32), outputs = mapOf(TOOLS to 16))
            "boathouse", "shipyard" -> craft(mapOf(TIMBER to 16 * lots, CLOTH to 2 * lots, TOOLS to lots))
            "balloon" -> craft(mapOf(TIMBER to (if (balanced) 16 * lots else 16),
                CLOTH to (if (balanced) 2 * lots else 20), TOOLS to (if (balanced) lots else 4)), mapOf("cotton" to 6))

            // Healing gardens support repeatable expedition equipment, never a daily energy refill.
            "herbalist" -> make(mapOf(WATER to 4), mapOf("herbs" to 2), mapOf(MEDICINE to 2))
            "gardenremedies" -> make(mapOf(WATER to 8, HONEY to 4), mapOf("lavender" to 2), mapOf(MEDICINE to 6))
            "apothecary" -> make(mapOf(WATER to 12, HONEY to 6), mapOf("herbs" to 4, "lavender" to 2), mapOf(MEDICINE to 12))
            "perfumer" -> craft(mapOf(WATER to 12, HONEY to 4), mapOf("lavender" to 4, "wildflowers" to 4))
            "seedhouse" -> make(mapOf(WATER to 8), mapOf("herbs" to 2), mapOf(FOOD to 24, MEDICINE to 2))
            "greenhouse" -> make(mapOf(WATER to 16), mapOf("herbs" to 4), mapOf(MEDICINE to 8))
            "conservatory" -> make(mapOf(WATER to 24), mapOf("herbs" to 4, "lavender" to 4), mapOf(MEDICINE to 20))
            "skygarden" -> make(mapOf(WATER to 32, CRYSTAL to 4), mapOf("herbs" to 6, "starfruit" to 4), mapOf(MEDICINE to 32))
            "watertower" -> make(outputs = mapOf(WATER to 360))

            // Services consume finished village goods; their contract pays coins and experience.
            "tradingpost", "giftshop", "harbor" -> make(mapOf(CRAFTS to 2 * lots, TIMBER to 2 * lots))
            "marketHall" -> make(mapOf(FOOD to 8 * lots, CRAFTS to lots))
            "bank", "mint" -> make(mapOf(ORE to 4 * lots, BOOKS to lots))
            "bookshop" -> make(mapOf(BOOKS to 8 * lots))
            "school", "scribe", "cartographer" -> make(mapOf(TIMBER to 10 * lots, FOOD to 4 * lots), outputs = mapOf(BOOKS to 24 * lots))
            "courthouse", "postoffice" -> make(mapOf(BOOKS to 4 * lots, BREAD to lots))
            "prison" -> make(mapOf(BREAD to 2 * lots, MEDICINE to lots))
            "chapel" -> make(mapOf(WAX to lots, BREAD to lots))
            "lighthouse", "lighthouseBlue", "lighthouseStone", "lighthouseGrand" ->
                make(mapOf(WAX to 2 * lots, TOOLS to lots), outputs = mapOf(BOOKS to 8 * lots))
            "clocktower" -> make(mapOf(TOOLS to lots, ORE to 2 * lots), outputs = mapOf(BOOKS to 8 * lots))
            "stables", "ridingschool" -> make(mapOf(CLOTH to 2 * lots, TOOLS to lots), mapOf("oats" to 4), mapOf(BOOKS to 8 * lots))
            "petshop" -> make(mapOf(MEDICINE to lots), mapOf("corn" to 4))
            "aviary" -> make(mapOf(MEDICINE to lots), mapOf("corn" to 4), mapOf(BOOKS to 8 * lots))
            "bootcamp", "jousting" -> make(mapOf(BREAD to 2 * lots, TOOLS to lots, CLOTH to lots), outputs = mapOf(BOOKS to 12 * lots))
            "musichall", "theater", "opera", "circus", "festivalhall" ->
                make(mapOf(CRAFTS to 2 * lots, CLOTH to lots, BREAD to 2 * lots))
            "museum" -> make(mapOf(CRAFTS to 2 * lots, BOOKS to 2 * lots))
            "observatory", "starwatch", "stargazer" ->
                make(mapOf(TOOLS to lots, CRYSTAL to lots), outputs = mapOf(BOOKS to 20 * lots))
            "wizard", "alchemist", "magicshop" ->
                make(mapOf(CRYSTAL to 2 * lots, MEDICINE to lots), outputs = mapOf(RUNES to 8 * lots))
            "dragon", "griffin", "unicorn" ->
                make(mapOf(FOOD to 8 * lots, MEDICINE to lots), outputs = mapOf(RUNES to 4 * lots))
            else -> error("No village recipe for $id")
        }
    }
}
