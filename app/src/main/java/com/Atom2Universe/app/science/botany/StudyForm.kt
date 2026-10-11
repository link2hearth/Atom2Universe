package com.Atom2Universe.app.science.botany

/** Reusable whole-plant architectures; no painter dispatches on a catalogue ID.
 * Scale refers to the main organ; the smaller diagnostic insets are independently enlarged.
 */
enum class StudyForm(val nightshade:Boolean=false, val orchard:Boolean=false, val foodCrop:Boolean=false, val usefulPlant:Boolean=false) {
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
        PlantView.LEAF -> p.look.leafCm/198f
        PlantView.FLOWER -> p.look.flowerCm/if(nightshade || orchard || foodCrop || usefulPlant)124f else when(this) {
            ROSETTE_SILIQUES -> 124f
            TENDRIL_LEGUME -> 120f
            else -> 180f
        }
        PlantView.FRUIT -> p.look.fruitCm/if(nightshade || orchard || usefulPlant)120f else when(this) {
            ROSETTE_SILIQUES,TENDRIL_LEGUME,WAXY_ROSETTE_SILIQUES,TENDRIL_PEPO,HAIRY_TRIFOLIATE_POD,GEOCARPIC_POD -> 180f
            HOLLOW_BULB_UMBEL -> 120f
            else -> 166f
        }
    }
    fun anchor(view:PlantView):Pair<Float,Float> = when(view) {
        PlantView.HABIT -> 150f to 130f
        PlantView.LEAF -> 150f to 150f
        PlantView.FLOWER -> if(nightshade || orchard || foodCrop || usefulPlant || this==ROSETTE_SILIQUES || this==TENDRIL_LEGUME)150f to 150f else 104f to 130f
        PlantView.FRUIT -> 125f to 150f
    }
}
