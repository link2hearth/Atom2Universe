package com.Atom2Universe.app.science.botany

/** Reusable whole-plant architectures; no painter dispatches on a catalogue ID.
 * Scale refers to the main organ; the smaller diagnostic insets are independently enlarged.
 */
enum class StudyForm(val nightshade:Boolean=false, val orchard:Boolean=false, val foodCrop:Boolean=false, val usefulPlant:Boolean=false, val culinary:Boolean=false, val structural:Boolean=false, val meadow:Boolean=false) {
    BUGLE_SPIKE(meadow=true),
    SNAPDRAGON_POUCH(meadow=true),
    TOADFLAX_SPUR(meadow=true),
    MEADOW_REFERENCE(meadow=true),
    WHITE_CAMPION(meadow=true),
    ANEMONE_INVOLUCRE(meadow=true),
    PASQUE_PLUMOSE(meadow=true),
    CORNFLOWER_HEAD(meadow=true),
    COLTSFOOT_SEASONS(meadow=true),
    AGRIMONY_BURR(meadow=true),
    FRINGED_ROSE(meadow=true),
    WHITEBEAM_LOBES(meadow=true),
    BUR_MEDICK(meadow=true),
    ALOE_ROSETTE(structural=true),
    PINEAPPLE_SYNCARP(structural=true),
    BANANA_PSEUDOSTEM(structural=true),
    GINGER_RHIZOME(structural=true),
    COCONUT_PALM(structural=true),
    DATE_PALM(structural=true),
    ARECA_PALM(structural=true),
    CLIVIA_FAN(structural=true),
    LILY_RACEME(structural=true),
    BOWIEA_BULB(structural=true),
    PEAR_POME(structural=true),
    MEDLAR_POME(structural=true),
    WALNUT_HUSK(structural=true),
    FIG_SYCONIUM(structural=true),
    CURRANT_RACEME(structural=true),
    LINGONBERRY_RACEME(structural=true),
    CREEPING_WILLOW(structural=true),
    JUNIPER_FLESHY_CONE(structural=true),
    TREE_HAZEL(structural=true),
    GARLIC_UMBEL(culinary=true), BROAD_ALLIUM(culinary=true), TUFT_ALLIUM(culinary=true), LEEK_UMBEL(culinary=true),
    COMPOUND_UMBEL(culinary=true), AROMATIC_NUTLETS(culinary=true), INFLATED_LEGUME(culinary=true), SLENDER_LEGUME(culinary=true),
    ROUND_PEPO(culinary=true), BEAKED_SILIQUE(culinary=true), JOINTED_SILIQUE(culinary=true), WATER_SILIQUES(culinary=true),
    OAT_PANICLE, BARLEY_SPIKE, RYE_SPIKE,
    ROSETTE_SILIQUES, TENDRIL_LEGUME, CEREAL_SPIKE, CEREAL_PANICLE, MONOECIOUS_CEREAL,
    PINNATE_BERRY(true), TUBEROUS_BERRY(true), LOBED_BERRY(true), HOLLOW_BERRY(true), SMALL_BERRY_CYME(true),
    SILVERY_DRUPE(orchard=true), AXILLARY_COFFEE(orchard=true),
    LANCE_STONE_FRUIT(orchard=true), CORDATE_STONE_FRUIT(orchard=true), WINGED_CITRUS(orchard=true),
    HOLLOW_BULB_UMBEL(foodCrop=true), WAXY_ROSETTE_SILIQUES(foodCrop=true),
    TENDRIL_PEPO(foodCrop=true), HAIRY_TRIFOLIATE_POD(foodCrop=true), GEOCARPIC_POD(foodCrop=true),
    TEA_CAPSULE(usefulPlant=true), FIBROUS_BOLL(usefulPlant=true), CROWNED_BERRY(usefulPlant=true),
    SINGLE_SEED_BERRY(usefulPlant=true), DIOECIOUS_UMBEL(usefulPlant=true);

    fun cmPerPixel(p:PlantSpecies,view:PlantView):Float = when(view) {
        PlantView.HABIT -> (p.heightMinCm+p.heightMaxCm)/2f/if(this==GEOCARPIC_POD)180f else 249f
        PlantView.LEAF -> p.look.leafCm/if(this==JUNIPER_FLESHY_CONE)49.45f else 198f
        PlantView.FLOWER -> p.look.flowerCm/if(meadow)when(this) {
            MEADOW_REFERENCE,FRINGED_ROSE,WHITEBEAM_LOBES -> when(p.look.flower) {
                FlowerShape.STAR,FlowerShape.CUP,FlowerShape.DAISY -> 124f
                else -> 180f
            }
            WHITE_CAMPION -> 150f
            PASQUE_PLUMOSE -> 124f
            else -> 124f
        } else if(structural)when(this) {
            COCONUT_PALM,DATE_PALM,ARECA_PALM -> 183f
            BANANA_PSEUDOSTEM -> 198f
            PINEAPPLE_SYNCARP -> 105.4f
            GINGER_RHIZOME -> 116f
            WALNUT_HUSK -> 140f
            else -> 124f
        } else if(culinary) (if(this==COMPOUND_UMBEL || this in setOf(GARLIC_UMBEL,BROAD_ALLIUM,TUFT_ALLIUM,LEEK_UMBEL))180f else 124f) else if(nightshade || orchard || foodCrop || usefulPlant)124f else when(this) {
            ROSETTE_SILIQUES -> 124f
            TENDRIL_LEGUME -> 120f
            else -> 180f
        }
        PlantView.FRUIT -> p.look.fruitCm/if(meadow)when(this) {
            PASQUE_PLUMOSE -> 18f
            COLTSFOOT_SEASONS,CORNFLOWER_HEAD -> 52f
            AGRIMONY_BURR -> 120f
            BUR_MEDICK -> 120f
            MEADOW_REFERENCE -> when(p.look.fruit) {
                FruitShape.POD,FruitShape.SILIQUE -> 172f
                FruitShape.ACHENE -> 54.6f
                FruitShape.CAPSULE -> 132f
                else -> 120f
            }
            else -> 120f
        } else if(structural)when(this) {
            CLIVIA_FAN -> 40f
            LILY_RACEME,LINGONBERRY_RACEME,CURRANT_RACEME -> 60f
            ARECA_PALM -> 84f
            JUNIPER_FLESHY_CONE -> 50f
            TREE_HAZEL -> 41.8f
            GINGER_RHIZOME -> 90f
            else -> 120f
        } else if(culinary) (if(this in setOf(INFLATED_LEGUME,SLENDER_LEGUME,BEAKED_SILIQUE,JOINTED_SILIQUE,WATER_SILIQUES))180f else 120f) else if(nightshade || orchard || usefulPlant)120f else when(this) {
            ROSETTE_SILIQUES,TENDRIL_LEGUME,WAXY_ROSETTE_SILIQUES,TENDRIL_PEPO,HAIRY_TRIFOLIATE_POD,GEOCARPIC_POD -> 180f
            HOLLOW_BULB_UMBEL -> 120f
            else -> 166f
        }
    }
    fun anchor(view:PlantView):Pair<Float,Float> = when(view) {
        PlantView.HABIT -> 150f to 130f
        PlantView.LEAF -> 150f to 150f
        PlantView.FLOWER -> if(meadow)150f to 150f else if(structural)when(this) {
            PINEAPPLE_SYNCARP -> 230f to 135f
            ALOE_ROSETTE,CLIVIA_FAN -> 125f to 146f
            LILY_RACEME,LINGONBERRY_RACEME -> 117f to 145f
            GINGER_RHIZOME -> 229f to 140f
            else -> 150f to 150f
        } else if(culinary || nightshade || orchard || foodCrop || usefulPlant || this==ROSETTE_SILIQUES || this==TENDRIL_LEGUME)150f to 150f else 104f to 130f
        PlantView.FRUIT -> if(meadow)when(this) {
            MEADOW_REFERENCE -> 239f to 240f
            else -> 125f to 150f
        } else if(structural)when(this) {
            LILY_RACEME,LINGONBERRY_RACEME,CURRANT_RACEME -> 239f to 151f
            CLIVIA_FAN -> 142f to 140f
            TREE_HAZEL -> 145f to 151f
            else -> 125f to 150f
        } else 125f to 150f
    }
}
