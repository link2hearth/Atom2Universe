package com.Atom2Universe.app.science.botany

/** Reusable whole-plant architectures; no painter dispatches on a catalogue ID.
 * Scale refers to the main organ; the smaller diagnostic insets are independently enlarged.
 */
enum class StudyForm(val nightshade:Boolean=false, val orchard:Boolean=false) {
    ROSETTE_SILIQUES, TENDRIL_LEGUME, CEREAL_SPIKE, CEREAL_PANICLE, MONOECIOUS_CEREAL,
    PINNATE_BERRY(true), TUBEROUS_BERRY(true), LOBED_BERRY(true), HOLLOW_BERRY(true), SMALL_BERRY_CYME(true),
    SILVERY_DRUPE(orchard=true), AXILLARY_COFFEE(orchard=true),
    LANCE_STONE_FRUIT(orchard=true), CORDATE_STONE_FRUIT(orchard=true), WINGED_CITRUS(orchard=true);

    fun cmPerPixel(p:PlantSpecies,view:PlantView):Float = when(view) {
        PlantView.HABIT -> (p.heightMinCm+p.heightMaxCm)/2f/249f
        PlantView.LEAF -> p.look.leafCm/198f
        PlantView.FLOWER -> p.look.flowerCm/if(nightshade || orchard)124f else when(this) {
            ROSETTE_SILIQUES -> 124f
            TENDRIL_LEGUME -> 120f
            else -> 180f
        }
        PlantView.FRUIT -> p.look.fruitCm/if(nightshade || orchard)120f else when(this) {
            ROSETTE_SILIQUES,TENDRIL_LEGUME -> 180f
            else -> 166f
        }
    }
    fun anchor(view:PlantView):Pair<Float,Float> = when(view) {
        PlantView.HABIT -> 150f to 130f
        PlantView.LEAF -> 150f to 150f
        PlantView.FLOWER -> if(nightshade || orchard || this==ROSETTE_SILIQUES || this==TENDRIL_LEGUME)150f to 150f else 104f to 130f
        PlantView.FRUIT -> 125f to 150f
    }
}
