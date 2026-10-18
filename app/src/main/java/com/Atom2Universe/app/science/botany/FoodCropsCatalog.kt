package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.R

/** Fourth reference batch: crop forms are examples, while taxon identity stays species-level. */
internal object FoodCropsCatalog {
    val species=listOf(
        PlantSpecies("allium_cepa",R.string.bot_allium_cepa_name,R.string.bot_allium_cepa_latin,R.string.bot_allium_cepa_aliases,"Amaryllidaceae",setOf("herbs"),
            30,120,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),false,
            PlantLook(Crown.HERB,LeafShape.LINEAR,LeafMargin.ENTIRE,LeafArrangement.ROSETTE,FlowerShape.UMBEL,FruitShape.CAPSULE,FlowerColor.WHITE,
                40.0f,0.6f,0.6f,petals=6,leaflets=3,seed=181,
                leafColor=0xff638b78.toInt(),leafDetail=LeafDetail(venation=Venation.PARALLEL),study=StudyForm.HOLLOW_BULB_UMBEL),
            R.string.bot_allium_cepa_description,R.string.bot_allium_cepa_distribution,PlantView.entries.zip(listOf(R.string.bot_allium_cepa_habit,R.string.bot_allium_cepa_leaf,R.string.bot_allium_cepa_flower,R.string.bot_allium_cepa_fruit)).toMap(),
            setOf("powo","ncsu","efloras"),collectionIds=setOf("food_crops"),phenologyNote=R.string.bot_food_crop_season,additionalColors=setOf(FlowerColor.PINK)),
        PlantSpecies("brassica_oleracea",R.string.bot_brassica_oleracea_name,R.string.bot_brassica_oleracea_latin,R.string.bot_brassica_oleracea_aliases,"Brassicaceae",setOf("herbs"),
            30,200,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),false,
            PlantLook(Crown.HERB,LeafShape.OVAL,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.STAR,FruitShape.SILIQUE,FlowerColor.YELLOW,
                30.0f,3.0f,8.0f,petals=4,leaflets=3,seed=182,
                leafColor=0xff638b78.toInt(),leafDetail=LeafDetail(width=.35f,teeth=LeafTeeth.WAVY,veinPairs=8,tip=LeafTip.ROUNDED),study=StudyForm.WAXY_ROSETTE_SILIQUES),
            R.string.bot_brassica_oleracea_description,R.string.bot_brassica_oleracea_distribution,PlantView.entries.zip(listOf(R.string.bot_brassica_oleracea_habit,R.string.bot_brassica_oleracea_leaf,R.string.bot_brassica_oleracea_flower,R.string.bot_brassica_oleracea_fruit)).toMap(),
            setOf("powo","ncsu","efloras"),collectionIds=setOf("food_crops"),phenologyNote=R.string.bot_food_crop_season,additionalArrangements=setOf(LeafArrangement.ROSETTE),additionalMargins=setOf(LeafMargin.LOBED,LeafMargin.SERRATE),additionalColors=setOf(FlowerColor.WHITE)),
        PlantSpecies("cucumis_sativus",R.string.bot_cucumis_sativus_name,R.string.bot_cucumis_sativus_latin,R.string.bot_cucumis_sativus_aliases,"Cucurbitaceae",setOf("herbs"),
            20,250,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),false,
            PlantLook(Crown.HERB,LeafShape.PALMATILOBED,LeafMargin.SERRATE,LeafArrangement.ALTERNATE,FlowerShape.STAR,FruitShape.BERRY,FlowerColor.YELLOW,
                18.0f,3.0f,20.0f,petals=5,leaflets=3,seed=183,
                leafColor=0xff587c43.toInt(),leafDetail=LeafDetail(venation=Venation.PALMATE,base=LeafBase.CORDATE,palmateSegments=5,ciliate=true),study=StudyForm.TENDRIL_PEPO),
            R.string.bot_cucumis_sativus_description,R.string.bot_cucumis_sativus_distribution,PlantView.entries.zip(listOf(R.string.bot_cucumis_sativus_habit,R.string.bot_cucumis_sativus_leaf,R.string.bot_cucumis_sativus_flower,R.string.bot_cucumis_sativus_fruit)).toMap(),
            setOf("powo","ncsu","efloras"),collectionIds=setOf("food_crops"),phenologyNote=R.string.bot_food_crop_season),
        PlantSpecies("glycine_max",R.string.bot_glycine_max_name,R.string.bot_glycine_max_latin,R.string.bot_glycine_max_aliases,"Fabaceae",setOf("herbs"),
            30,150,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),false,
            PlantLook(Crown.HERB,LeafShape.TRIFOLIATE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.PEA,FruitShape.POD,FlowerColor.PURPLE,
                20.0f,0.7f,6.0f,petals=5,leaflets=3,seed=184,
                leafColor=0xff587c43.toInt(),leafDetail=LeafDetail(leafletShape=LeafShape.OVAL,leafletWidth=.29f,ciliate=true,veinPairs=8),study=StudyForm.HAIRY_TRIFOLIATE_POD),
            R.string.bot_glycine_max_description,R.string.bot_glycine_max_distribution,PlantView.entries.zip(listOf(R.string.bot_glycine_max_habit,R.string.bot_glycine_max_leaf,R.string.bot_glycine_max_flower,R.string.bot_glycine_max_fruit)).toMap(),
            setOf("powo","ncsu","efloras"),collectionIds=setOf("food_crops"),phenologyNote=R.string.bot_food_crop_season,additionalLeaves=setOf(LeafShape.OVAL),additionalArrangements=setOf(LeafArrangement.OPPOSITE),additionalColors=setOf(FlowerColor.WHITE)),
        PlantSpecies("arachis",R.string.bot_arachis_name,R.string.bot_arachis_latin,R.string.bot_arachis_aliases,"Fabaceae",setOf("herbs"),
            20,70,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),false,
            PlantLook(Crown.HERB,LeafShape.PINNATE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.PEA,FruitShape.POD,FlowerColor.YELLOW,
                14.0f,1.0f,4.0f,petals=5,leaflets=4,seed=185,
                leafColor=0xff587c43.toInt(),leafDetail=LeafDetail(leafletShape=LeafShape.OVAL,leafletWidth=.35f,leafletTip=LeafTip.ROUNDED,ciliate=true,veinPairs=9),study=StudyForm.GEOCARPIC_POD),
            R.string.bot_arachis_description,R.string.bot_arachis_distribution,PlantView.entries.zip(listOf(R.string.bot_arachis_habit,R.string.bot_arachis_leaf,R.string.bot_arachis_flower,R.string.bot_arachis_fruit)).toMap(),
            setOf("powo","ncsu","efloras"),collectionIds=setOf("food_crops"),phenologyNote=R.string.bot_food_crop_season)
    )
}
