package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.R

/** Final six source-checked terminal taxa from the living-tree index (atlas lot 46). */
internal object PriorityTreeCatalogHerbariumLot46 {
    private fun r(id:String,part:String) = R.string::class.java.getField("bot_${id}_${part}").getInt(null)
    private fun p(id:String,family:String,groups:Set<String>,min:Int,max:Int,habitat:Habitat,evergreen:Boolean,look:PlantLook) =
        PlantSpecies(id,r(id,"name"),r(id,"latin"),r(id,"aliases"),family,groups,min,max,
            emptySet(),emptySet(),setOf(habitat),evergreen,look,r(id,"description"),r(id,"distribution"),
            PlantView.entries.zip(listOf(r(id,"habit"),r(id,"leaf"),r(id,"flower"),r(id,"fruit"))).toMap(),
            setOf("powo"),collectionIds=setOf("tree_diversity"),phenologyNote=r(id,"phenology"))

    val species = listOf(
        p("chamaecrista_aldabrensis","Fabaceae",setOf("shrubs"),15,40,Habitat.DRY_SCRUB,false,
            PlantLook(Crown.SHRUB,LeafShape.PINNATE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.PEA,FruitShape.POD,FlowerColor.YELLOW,1.5f,0.6f,2.5f,leaflets=7)),
        p("aglaia_archboldiana","Meliaceae",setOf("trees"),500,1000,Habitat.WET,true,
            PlantLook(Crown.SPREAD,LeafShape.PINNATE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.PANICLE,FruitShape.BERRY,FlowerColor.WHITE,16f,0.5f,2f)),
        p("syzygium_phaeophyllum","Myrtaceae",setOf("trees"),400,600,Habitat.WET,true,
            PlantLook(Crown.SPREAD,LeafShape.OVAL,LeafMargin.ENTIRE,LeafArrangement.OPPOSITE,FlowerShape.PANICLE,FruitShape.BERRY,FlowerColor.WHITE,7f,0.5f,1.5f)),
        p("erythrina_atitlanensis","Fabaceae",setOf("trees"),600,1200,Habitat.WOOD,false,
            PlantLook(Crown.SPREAD,LeafShape.TRIFOLIATE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.RACEME,FruitShape.POD,FlowerColor.RED,18f,5f,18f,leaflets=3,thorny=true)),
        p("pierrebraunia_bahiensis","Cactaceae",setOf("herbs"),20,110,Habitat.DRY_SCRUB,true,
            PlantLook(Crown.COLUMN,LeafShape.SCALE,LeafMargin.SPINY,LeafArrangement.FASCICLE,FlowerShape.BELL,FruitShape.BERRY,FlowerColor.PINK,8f,3.5f,2f)),
        p("rauhocereus_riosaniensis","Cactaceae",setOf("trees"),200,400,Habitat.DRY_SCRUB,true,
            PlantLook(Crown.COLUMN,LeafShape.SCALE,LeafMargin.SPINY,LeafArrangement.FASCICLE,FlowerShape.BELL,FruitShape.BERRY,FlowerColor.WHITE,12f,9f,4f))
    )
}
