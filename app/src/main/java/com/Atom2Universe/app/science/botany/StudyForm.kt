package com.Atom2Universe.app.science.botany

/** Reusable whole-plant architectures; no painter dispatches on a catalogue ID.
 * Scale refers to the main organ; the smaller diagnostic insets are independently enlarged.
 */
enum class StudyForm {
    ROSETTE_SILIQUES, TENDRIL_LEGUME, CEREAL_SPIKE, CEREAL_PANICLE, MONOECIOUS_CEREAL;

    fun cmPerPixel(p:PlantSpecies,view:PlantView):Float = when(view) {
        PlantView.HABIT -> (p.heightMinCm+p.heightMaxCm)/2f/249f
        PlantView.LEAF -> p.look.leafCm/198f
        PlantView.FLOWER -> p.look.flowerCm/when(this) {
            ROSETTE_SILIQUES -> 124f
            TENDRIL_LEGUME -> 120f
            else -> 180f
        }
        PlantView.FRUIT -> p.look.fruitCm/when(this) {
            ROSETTE_SILIQUES,TENDRIL_LEGUME -> 180f
            else -> 166f
        }
    }
    fun anchor(view:PlantView):Pair<Float,Float> = when(view) {
        PlantView.HABIT -> 150f to 130f
        PlantView.LEAF -> 150f to 150f
        PlantView.FLOWER -> if(this==ROSETTE_SILIQUES || this==TENDRIL_LEGUME)150f to 150f else 104f to 130f
        PlantView.FRUIT -> 125f to 150f
    }
}
