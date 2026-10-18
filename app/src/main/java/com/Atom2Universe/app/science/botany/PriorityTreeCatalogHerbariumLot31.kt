package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.R

/** Six additional terminal flowering taxa, chosen for contrasting habits and useful comparisons. */
internal object PriorityTreeCatalogHerbariumLot31 {
    val species = listOf(
        PlantSpecies("anchusa_arvensis",R.string.bot_anchusa_arvensis_name,R.string.bot_anchusa_arvensis_latin,R.string.bot_anchusa_arvensis_aliases,"Boraginaceae",setOf("herbs"),15,60,emptySet(),emptySet(),setOf(Habitat.MEADOW,Habitat.DISTURBED,Habitat.CULTIVATED),false,
            PlantLook(Crown.HERB,LeafShape.LANCE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.LIPPED,FruitShape.NUT,FlowerColor.BLUE,8f,0.7f,0.2f,seed=1041,leafDetail=LeafDetail(width=.4f,veinPairs=6)),
            R.string.bot_anchusa_arvensis_description,R.string.bot_anchusa_arvensis_distribution,PlantView.entries.zip(listOf(R.string.bot_anchusa_arvensis_habit,R.string.bot_anchusa_arvensis_leaf,R.string.bot_anchusa_arvensis_flower,R.string.bot_anchusa_arvensis_fruit)).toMap(),setOf("powo","wfo","rhs")),
        PlantSpecies("artemisia_maritima",R.string.bot_artemisia_maritima_name,R.string.bot_artemisia_maritima_latin,R.string.bot_artemisia_maritima_aliases,"Asteraceae",setOf("shrubs"),20,60,emptySet(),emptySet(),setOf(Habitat.WET),false,
            PlantLook(Crown.SHRUB,LeafShape.DIVIDED,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.GLOBOSE_HEAD,FruitShape.ACHENE,FlowerColor.YELLOW,4f,0.3f,0.15f,seed=1042,fineShrub=true,leafDetail=LeafDetail(width=.32f,veinPairs=0)),
            R.string.bot_artemisia_maritima_description,R.string.bot_artemisia_maritima_distribution,PlantView.entries.zip(listOf(R.string.bot_artemisia_maritima_habit,R.string.bot_artemisia_maritima_leaf,R.string.bot_artemisia_maritima_flower,R.string.bot_artemisia_maritima_fruit)).toMap(),setOf("powo","rhs")),
        PlantSpecies("asarum_europaeum",R.string.bot_asarum_europaeum_name,R.string.bot_asarum_europaeum_latin,R.string.bot_asarum_europaeum_aliases,"Aristolochiaceae",setOf("herbs"),5,15,emptySet(),emptySet(),setOf(Habitat.WOOD),true,
            PlantLook(Crown.HERB,LeafShape.HEART,LeafMargin.ENTIRE,LeafArrangement.ROSETTE,FlowerShape.CUP,FruitShape.CAPSULE,FlowerColor.PURPLE,8f,2f,1f,seed=1043,leafDetail=LeafDetail(width=.8f,veinPairs=5)),
            R.string.bot_asarum_europaeum_description,R.string.bot_asarum_europaeum_distribution,PlantView.entries.zip(listOf(R.string.bot_asarum_europaeum_habit,R.string.bot_asarum_europaeum_leaf,R.string.bot_asarum_europaeum_flower,R.string.bot_asarum_europaeum_fruit)).toMap(),setOf("powo","rhs")),
        PlantSpecies("astrophytum_caput_medusae",R.string.bot_astrophytum_caput_medusae_name,R.string.bot_astrophytum_caput_medusae_latin,R.string.bot_astrophytum_caput_medusae_aliases,"Cactaceae",setOf("herbs"),5,20,emptySet(),emptySet(),setOf(Habitat.DRY_SCRUB),true,
            PlantLook(Crown.HERB,LeafShape.LINEAR,LeafMargin.ENTIRE,LeafArrangement.ROSETTE,FlowerShape.STAR,FruitShape.CAPSULE,FlowerColor.YELLOW,12f,5f,2f,seed=1044,leafDetail=LeafDetail(width=.22f,veinPairs=0)),
            R.string.bot_astrophytum_caput_medusae_description,R.string.bot_astrophytum_caput_medusae_distribution,PlantView.entries.zip(listOf(R.string.bot_astrophytum_caput_medusae_habit,R.string.bot_astrophytum_caput_medusae_leaf,R.string.bot_astrophytum_caput_medusae_flower,R.string.bot_astrophytum_caput_medusae_fruit)).toMap(),setOf("powo"),collectionIds=setOf("north_america")),
        PlantSpecies("brocchinia_reducta",R.string.bot_brocchinia_reducta_name,R.string.bot_brocchinia_reducta_latin,R.string.bot_brocchinia_reducta_aliases,"Bromeliaceae",setOf("herbs"),20,60,emptySet(),emptySet(),setOf(Habitat.WET,Habitat.MOUNTAIN),true,
            PlantLook(Crown.HERB,LeafShape.LINEAR,LeafMargin.SPINY,LeafArrangement.ROSETTE,FlowerShape.PANICLE,FruitShape.CAPSULE,FlowerColor.YELLOW,35f,1f,0.5f,seed=1045,thorny=true,leafDetail=LeafDetail(width=.3f,veinPairs=0)),
            R.string.bot_brocchinia_reducta_description,R.string.bot_brocchinia_reducta_distribution,PlantView.entries.zip(listOf(R.string.bot_brocchinia_reducta_habit,R.string.bot_brocchinia_reducta_leaf,R.string.bot_brocchinia_reducta_flower,R.string.bot_brocchinia_reducta_fruit)).toMap(),setOf("powo","wfo"),collectionIds=setOf("tropical_america")),
        PlantSpecies("cestrum_nocturnum",R.string.bot_cestrum_nocturnum_name,R.string.bot_cestrum_nocturnum_latin,R.string.bot_cestrum_nocturnum_aliases,"Solanaceae",setOf("shrubs"),100,300,emptySet(),emptySet(),setOf(Habitat.WOOD,Habitat.CULTIVATED),true,
            PlantLook(Crown.SHRUB,LeafShape.LANCE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.RACEME,FruitShape.BERRY,FlowerColor.WHITE,12f,2.5f,0.7f,seed=1046,leafDetail=LeafDetail(width=.4f,veinPairs=7)),
            R.string.bot_cestrum_nocturnum_description,R.string.bot_cestrum_nocturnum_distribution,PlantView.entries.zip(listOf(R.string.bot_cestrum_nocturnum_habit,R.string.bot_cestrum_nocturnum_leaf,R.string.bot_cestrum_nocturnum_flower,R.string.bot_cestrum_nocturnum_fruit)).toMap(),setOf("powo","efloras","ncsu"),collectionIds=setOf("tropical_america"))
    )
}
