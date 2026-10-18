package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.R

/** Six more terminal taxa illustrated from the priority tree. */
internal object PriorityTreeCatalogHerbariumLot30 {
    val species = listOf(
        PlantSpecies("agave_fourcroydes",R.string.bot_agave_fourcroydes_name,R.string.bot_agave_fourcroydes_latin,R.string.bot_agave_fourcroydes_aliases,"Asparagaceae",setOf("herbs"),100,200,emptySet(),emptySet(),setOf(Habitat.DRY_SCRUB,Habitat.CULTIVATED),true,
            PlantLook(Crown.HERB,LeafShape.LANCE,LeafMargin.SPINY,LeafArrangement.ROSETTE,FlowerShape.PANICLE,FruitShape.CAPSULE,FlowerColor.YELLOW,120f,7f,4f,seed=1031,thorny=true,leafDetail=LeafDetail(width=.35f,veinPairs=0)),
            R.string.bot_agave_fourcroydes_description,R.string.bot_agave_fourcroydes_distribution,PlantView.entries.zip(listOf(R.string.bot_agave_fourcroydes_habit,R.string.bot_agave_fourcroydes_leaf,R.string.bot_agave_fourcroydes_flower,R.string.bot_agave_fourcroydes_fruit)).toMap(),setOf("powo","rhs"),collectionIds=setOf("useful_plants")),
        PlantSpecies("agave_shawii",R.string.bot_agave_shawii_name,R.string.bot_agave_shawii_latin,R.string.bot_agave_shawii_aliases,"Asparagaceae",setOf("herbs"),30,100,emptySet(),emptySet(),setOf(Habitat.DRY_SCRUB),true,
            PlantLook(Crown.HERB,LeafShape.LANCE,LeafMargin.SPINY,LeafArrangement.ROSETTE,FlowerShape.PANICLE,FruitShape.CAPSULE,FlowerColor.YELLOW,45f,6f,3f,seed=1032,thorny=true,leafDetail=LeafDetail(width=.55f,veinPairs=0)),
            R.string.bot_agave_shawii_description,R.string.bot_agave_shawii_distribution,PlantView.entries.zip(listOf(R.string.bot_agave_shawii_habit,R.string.bot_agave_shawii_leaf,R.string.bot_agave_shawii_flower,R.string.bot_agave_shawii_fruit)).toMap(),setOf("powo","osu")),
        PlantSpecies("hedera_helix",R.string.bot_hedera_helix_name,R.string.bot_hedera_helix_latin,R.string.bot_hedera_helix_aliases,"Araliaceae",setOf("shrubs"),500,3000,emptySet(),emptySet(),setOf(Habitat.WOOD,Habitat.HEDGE,Habitat.PARK),true,
            PlantLook(Crown.SPREAD,LeafShape.PALMATILOBED,LeafMargin.LOBED,LeafArrangement.ALTERNATE,FlowerShape.GLOBOSE_HEAD,FruitShape.BERRY,FlowerColor.GREEN,8f,0.5f,0.7f,seed=1033,leafDetail=LeafDetail(width=.75f,veinPairs=5)),
            R.string.bot_hedera_helix_description,R.string.bot_hedera_helix_distribution,PlantView.entries.zip(listOf(R.string.bot_hedera_helix_habit,R.string.bot_hedera_helix_leaf,R.string.bot_hedera_helix_flower,R.string.bot_hedera_helix_fruit)).toMap(),setOf("powo","rhs"),additionalLeaves=setOf(LeafShape.OVAL),additionalMargins=setOf(LeafMargin.ENTIRE)),
        PlantSpecies("agrostis_gigantea",R.string.bot_agrostis_gigantea_name,R.string.bot_agrostis_gigantea_latin,R.string.bot_agrostis_gigantea_aliases,"Poaceae",setOf("herbs"),40,130,emptySet(),emptySet(),setOf(Habitat.MEADOW,Habitat.WET,Habitat.DISTURBED),false,
            PlantLook(Crown.GRASS,LeafShape.LINEAR,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.PANICLE,FruitShape.GRAIN,FlowerColor.GREEN,35f,0.3f,0.2f,seed=1034,leafDetail=LeafDetail(width=.25f,veinPairs=0)),
            R.string.bot_agrostis_gigantea_description,R.string.bot_agrostis_gigantea_distribution,PlantView.entries.zip(listOf(R.string.bot_agrostis_gigantea_habit,R.string.bot_agrostis_gigantea_leaf,R.string.bot_agrostis_gigantea_flower,R.string.bot_agrostis_gigantea_fruit)).toMap(),setOf("powo","efloras")),
        PlantSpecies("adansonia_suarezensis",R.string.bot_adansonia_suarezensis_name,R.string.bot_adansonia_suarezensis_latin,R.string.bot_adansonia_suarezensis_aliases,"Malvaceae",setOf("trees"),1000,2500,emptySet(),emptySet(),setOf(Habitat.DRY_SCRUB),false,
            PlantLook(Crown.SPREAD,LeafShape.PALMATE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.STAR,FruitShape.POD,FlowerColor.WHITE,25f,12f,25f,seed=1035,bark=0xff816a55.toInt(),leafDetail=LeafDetail(width=.6f,veinPairs=5),architecture=TreeArchitecture(radius=135f,top=30f,bottom=232f,fork=214f,trunkWidth=57f,density=.25f)),
            R.string.bot_adansonia_suarezensis_description,R.string.bot_adansonia_suarezensis_distribution,PlantView.entries.zip(listOf(R.string.bot_adansonia_suarezensis_habit,R.string.bot_adansonia_suarezensis_leaf,R.string.bot_adansonia_suarezensis_flower,R.string.bot_adansonia_suarezensis_fruit)).toMap(),setOf("powo","adansonia_suarezensis")),
        PlantSpecies("anthemis_arvensis",R.string.bot_anthemis_arvensis_name,R.string.bot_anthemis_arvensis_latin,R.string.bot_anthemis_arvensis_aliases,"Asteraceae",setOf("herbs"),10,50,emptySet(),emptySet(),setOf(Habitat.MEADOW,Habitat.DISTURBED,Habitat.CULTIVATED),false,
            PlantLook(Crown.HERB,LeafShape.DIVIDED,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.DAISY,FruitShape.ACHENE,FlowerColor.WHITE,6f,2.5f,0.2f,seed=1036,leafDetail=LeafDetail(width=.45f,veinPairs=0)),
            R.string.bot_anthemis_arvensis_description,R.string.bot_anthemis_arvensis_distribution,PlantView.entries.zip(listOf(R.string.bot_anthemis_arvensis_habit,R.string.bot_anthemis_arvensis_leaf,R.string.bot_anthemis_arvensis_flower,R.string.bot_anthemis_arvensis_fruit)).toMap(),setOf("powo","rhs"))
    )
}
