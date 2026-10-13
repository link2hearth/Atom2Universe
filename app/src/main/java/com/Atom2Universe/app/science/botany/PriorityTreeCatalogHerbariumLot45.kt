package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.R

/** Twenty-one source-checked living-tree profiles and plates (atlas lot 45). */
internal object PriorityTreeCatalogHerbariumLot45 {
    private fun r(id:String,part:String) = R.string::class.java.getField("bot_${id}_${part}").getInt(null)
    private fun p(id:String,family:String,groups:Set<String>,min:Int,max:Int,habitat:Habitat,evergreen:Boolean,look:PlantLook,sourceIds:Set<String> = setOf("powo")) =
        PlantSpecies(id,r(id,"name"),r(id,"latin"),r(id,"aliases"),
            family,groups,min,max,emptySet(),emptySet(),setOf(habitat),evergreen,look,
            r(id,"description"),r(id,"distribution"),
            PlantView.entries.zip(listOf(r(id,"habit"),r(id,"leaf"),r(id,"flower"),r(id,"fruit"))).toMap(),
            sourceIds,collectionIds=setOf("tree_diversity"))

    val species = listOf(
        p("ambuchanania_leucobryoides","Ambuchananiaceae",setOf("herbs"),1,3,Habitat.WET,true,PlantLook(Crown.HERB,LeafShape.LINEAR,LeafMargin.ENTIRE,LeafArrangement.ROSETTE,FlowerShape.APETALOUS,FruitShape.CAPSULE,FlowerColor.GREEN,0.1f,0.1f,0.1f),setOf("bryological","wfo")),
        p("andreaeobryum_macrosporum","Andreaeobryaceae",setOf("herbs"),1,4,Habitat.MOUNTAIN,true,PlantLook(Crown.HERB,LeafShape.LANCE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.APETALOUS,FruitShape.CAPSULE,FlowerColor.GREEN,0.2f,0.1f,0.2f),setOf("bryological","wfo")),
        p("catoscopium_nigritum","Catoscopiaceae",setOf("herbs"),1,3,Habitat.WET,true,PlantLook(Crown.HERB,LeafShape.OVAL,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.APETALOUS,FruitShape.CAPSULE,FlowerColor.GREEN,0.2f,0.1f,0.3f),setOf("bryological","wfo")),
        p("hennediella_heimii","Pottiaceae",setOf("herbs"),0,2,Habitat.DISTURBED,true,PlantLook(Crown.HERB,LeafShape.LANCE,LeafMargin.ENTIRE,LeafArrangement.ROSETTE,FlowerShape.APETALOUS,FruitShape.CAPSULE,FlowerColor.GREEN,0.1f,0.1f,0.2f),setOf("bryological","wfo")),
        p("bryum_turbinatum","Bryaceae",setOf("herbs"),1,4,Habitat.WET,true,PlantLook(Crown.HERB,LeafShape.LANCE,LeafMargin.ENTIRE,LeafArrangement.ROSETTE,FlowerShape.APETALOUS,FruitShape.CAPSULE,FlowerColor.GREEN,0.2f,0.1f,0.4f),setOf("bryological","wfo")),
        p("cirriphyllum_piliferum","Brachytheciaceae",setOf("herbs"),2,10,Habitat.WOOD,true,PlantLook(Crown.HERB,LeafShape.LANCE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.APETALOUS,FruitShape.CAPSULE,FlowerColor.GREEN,0.3f,0.1f,0.4f),setOf("bryological","wfo")),
        p("pohlia_melanodon","Mielichhoferiaceae",setOf("herbs"),0,2,Habitat.MOUNTAIN,true,PlantLook(Crown.HERB,LeafShape.LANCE,LeafMargin.ENTIRE,LeafArrangement.ROSETTE,FlowerShape.APETALOUS,FruitShape.CAPSULE,FlowerColor.GREEN,0.1f,0.1f,0.3f),setOf("bryological","wfo")),
        p("dictyostega_orobanchoides","Burmanniaceae",setOf("herbs"),5,20,Habitat.WET,false,PlantLook(Crown.HERB,LeafShape.SCALE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.BELL,FruitShape.CAPSULE,FlowerColor.WHITE,0.1f,0.4f,0.3f),setOf("powo","wfo")),
        p("heteranthia_decipiens","Lamiaceae",setOf("herbs"),15,40,Habitat.WOOD,false,PlantLook(Crown.HERB,LeafShape.OVAL,LeafMargin.SERRATE,LeafArrangement.OPPOSITE,FlowerShape.LIPPED,FruitShape.CAPSULE,FlowerColor.PINK,3f,0.8f,0.5f),setOf("powo","wfo")),
        p("escobaria_missouriensis","Cactaceae",setOf("herbs"),2,8,Habitat.DRY_SCRUB,false,PlantLook(Crown.HERB,LeafShape.SCALE,LeafMargin.SPINY,LeafArrangement.FASCICLE,FlowerShape.CUP,FruitShape.BERRY,FlowerColor.YELLOW,0.2f,2.5f,1.5f),setOf("powo","efloras")),
        p("lophopyxis_maingayi","Lophopyxidaceae",setOf("trees"),500,2000,Habitat.WET,true,PlantLook(Crown.SPREAD,LeafShape.OVAL,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.PANICLE,FruitShape.CAPSULE,FlowerColor.GREEN,8f,0.7f,2f),setOf("powo","wfo")),
        p("coronidium_boormanii","Asteraceae",setOf("shrubs"),60,150,Habitat.WOOD,false,PlantLook(Crown.SHRUB,LeafShape.LINEAR,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.LIGULATE,FruitShape.ACHENE,FlowerColor.WHITE,5f,0.5f,0.8f),setOf("powo","plantnet")),
        p("cryptocarya_nova_anglica","Lauraceae",setOf("trees"),500,2000,Habitat.WOOD,true,PlantLook(Crown.SPREAD,LeafShape.LANCE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.PANICLE,FruitShape.DRUPE,FlowerColor.WHITE,15f,1.5f,1.2f),setOf("powo","plantnet")),
        p("pradosia_decipiens","Sapotaceae",setOf("trees"),500,2000,Habitat.WET,true,PlantLook(Crown.SPREAD,LeafShape.LANCE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.GLOBOSE_HEAD,FruitShape.DRUPE,FlowerColor.GREEN,15f,1.5f,3.2f),setOf("powo","wfo")),
        p("pradosia_kuhlmannii","Sapotaceae",setOf("trees"),500,1500,Habitat.WET,true,PlantLook(Crown.SPREAD,LeafShape.OVAL,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.GLOBOSE_HEAD,FruitShape.DRUPE,FlowerColor.GREEN,12f,1f,2f),setOf("powo","wfo")),
        p("pradosia_cuatrecasasii","Sapotaceae",setOf("trees"),1000,2000,Habitat.WET,true,PlantLook(Crown.SPREAD,LeafShape.OVAL,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.GLOBOSE_HEAD,FruitShape.DRUPE,FlowerColor.GREEN,18f,3f,6f),setOf("powo","wfo")),
        p("pradosia_grisebachii","Sapotaceae",setOf("trees"),500,1500,Habitat.WET,true,PlantLook(Crown.SPREAD,LeafShape.OVAL,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.GLOBOSE_HEAD,FruitShape.DRUPE,FlowerColor.RED,12f,1.5f,2.5f),setOf("powo","wfo")),
        p("jupunba_obovalis","Fabaceae",setOf("trees"),300,1200,Habitat.WOOD,false,PlantLook(Crown.SPREAD,LeafShape.BIPINNATE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.GLOBOSE_HEAD,FruitShape.POD,FlowerColor.WHITE,8f,1f,8f),setOf("powo","wfo")),
        p("espadaea_amoena","Solanaceae",setOf("shrubs","trees"),200,800,Habitat.WOOD,true,PlantLook(Crown.SHRUB,LeafShape.OVAL,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.BELL,FruitShape.BERRY,FlowerColor.YELLOW,3f,2f,1.5f),setOf("powo","wfo")),
        p("aglaia_evansensis","Meliaceae",setOf("trees"),200,800,Habitat.WET,true,PlantLook(Crown.SPREAD,LeafShape.PINNATE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.PANICLE,FruitShape.BERRY,FlowerColor.GREEN,6f,0.7f,2f),setOf("powo","wfo")),
        p("ipomoea_pulcherrima","Convolvulaceae",setOf("trees","shrubs"),200,800,Habitat.DRY_SCRUB,false,PlantLook(Crown.SHRUB,LeafShape.HEART,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.BELL,FruitShape.CAPSULE,FlowerColor.PINK,5f,1f,4.5f),setOf("powo","wfo"))
    )
}
