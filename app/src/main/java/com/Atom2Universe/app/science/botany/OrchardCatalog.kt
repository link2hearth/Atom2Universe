package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.R

/** Familiar crops already present in the living tree; representative forms remain reusable. */
internal object OrchardCatalog {
    val species=listOf(
        PlantSpecies("olea",R.string.bot_olea_name,R.string.bot_olea_latin,R.string.bot_olea_aliases,"Oleaceae",setOf("trees","shrubs"),
            300,1500,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),true,
            PlantLook(Crown.ROUND,LeafShape.LANCE,LeafMargin.ENTIRE,LeafArrangement.OPPOSITE,FlowerShape.STAR,FruitShape.DRUPE,FlowerColor.WHITE,
                7.0f,0.6f,3.0f,petals=4,petiole=.14f,fruitColor=0xff464038.toInt(),seed=170,
                leafColor=0xff87917a.toInt(),leafDetail=LeafDetail(width=.12f,veinPairs=9),
                architecture=TreeArchitecture(radius=98f,fork=207f,trunkWidth=23f,crooked=19f,density=.7f),study=StudyForm.SILVERY_DRUPE),
            R.string.bot_olea_description,R.string.bot_olea_distribution,PlantView.entries.zip(listOf(R.string.bot_olea_habit,R.string.bot_olea_leaf,R.string.bot_olea_flower,R.string.bot_olea_fruit)).toMap(),
            setOf("powo","ncsu"),collectionIds=setOf("orchard"),phenologyNote=R.string.bot_orchard_season),
        PlantSpecies("coffea",R.string.bot_coffea_name,R.string.bot_coffea_latin,R.string.bot_coffea_aliases,"Rubiaceae",setOf("trees","shrubs"),
            200,800,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),true,
            PlantLook(Crown.ROUND,LeafShape.OVAL,LeafMargin.ENTIRE,LeafArrangement.OPPOSITE,FlowerShape.STAR,FruitShape.DRUPE,FlowerColor.WHITE,
                14.0f,3.0f,1.5f,petals=5,petiole=.14f,fruitColor=0xffb43936.toInt(),seed=171,
                leafColor=0xff466d40.toInt(),leafDetail=LeafDetail(width=.22f,tip=LeafTip.ACUMINATE,teeth=LeafTeeth.WAVY,veinPairs=10,glossy=true),
                architecture=TreeArchitecture(radius=78f,trunkWidth=9f),study=StudyForm.AXILLARY_COFFEE),
            R.string.bot_coffea_description,R.string.bot_coffea_distribution,PlantView.entries.zip(listOf(R.string.bot_coffea_habit,R.string.bot_coffea_leaf,R.string.bot_coffea_flower,R.string.bot_coffea_fruit)).toMap(),
            setOf("powo","efloras"),collectionIds=setOf("orchard"),phenologyNote=R.string.bot_orchard_season),
        PlantSpecies("persica",R.string.bot_persica_name,R.string.bot_persica_latin,R.string.bot_persica_aliases,"Rosaceae",setOf("trees"),
            300,800,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),false,
            PlantLook(Crown.ROUND,LeafShape.LANCE,LeafMargin.SERRATE,LeafArrangement.ALTERNATE,FlowerShape.STAR,FruitShape.DRUPE,FlowerColor.PINK,
                16.0f,3.0f,7.0f,petals=5,petiole=.14f,fruitColor=0xffe5a04b.toInt(),seed=172,
                leafColor=0xff466d40.toInt(),leafDetail=LeafDetail(width=.12f,veinPairs=12,petioleGlands=true),
                architecture=TreeArchitecture(radius=89f,fork=222f,trunkWidth=12f,density=.6f,barkSurface=BarkSurface.LENTICELS),study=StudyForm.LANCE_STONE_FRUIT),
            R.string.bot_persica_description,R.string.bot_persica_distribution,PlantView.entries.zip(listOf(R.string.bot_persica_habit,R.string.bot_persica_leaf,R.string.bot_persica_flower,R.string.bot_persica_fruit)).toMap(),
            setOf("powo","ncsu"),collectionIds=setOf("orchard"),phenologyNote=R.string.bot_orchard_season,additionalColors=setOf(FlowerColor.WHITE)),
        PlantSpecies("armeniaca",R.string.bot_armeniaca_name,R.string.bot_armeniaca_latin,R.string.bot_armeniaca_aliases,"Rosaceae",setOf("trees"),
            300,1000,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),false,
            PlantLook(Crown.ROUND,LeafShape.HEART,LeafMargin.SERRATE,LeafArrangement.ALTERNATE,FlowerShape.STAR,FruitShape.DRUPE,FlowerColor.WHITE,
                9.0f,3.0f,5.0f,petals=5,petiole=.14f,fruitColor=0xffe7a23e.toInt(),seed=173,
                leafColor=0xff466d40.toInt(),leafDetail=LeafDetail(width=.36f,base=LeafBase.CORDATE,tip=LeafTip.ACUMINATE,petioleGlands=true),
                architecture=TreeArchitecture(radius=91f,fork=205f,trunkWidth=15f,density=.8f,barkSurface=BarkSurface.LENTICELS),study=StudyForm.CORDATE_STONE_FRUIT),
            R.string.bot_armeniaca_description,R.string.bot_armeniaca_distribution,PlantView.entries.zip(listOf(R.string.bot_armeniaca_habit,R.string.bot_armeniaca_leaf,R.string.bot_armeniaca_flower,R.string.bot_armeniaca_fruit)).toMap(),
            setOf("powo","ncsu"),collectionIds=setOf("orchard"),phenologyNote=R.string.bot_orchard_season,additionalLeaves=setOf(LeafShape.OVAL),additionalColors=setOf(FlowerColor.PINK)),
        PlantSpecies("citrus_maxima",R.string.bot_citrus_maxima_name,R.string.bot_citrus_maxima_latin,R.string.bot_citrus_maxima_aliases,"Rutaceae",setOf("trees"),
            500,1500,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),true,
            PlantLook(Crown.ROUND,LeafShape.OVAL,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.STAR,FruitShape.BERRY,FlowerColor.WHITE,
                18.0f,5.0f,20.0f,petals=5,petiole=.25f,fruitColor=0xffb6be54.toInt(),seed=174,
                leafColor=0xff466d40.toInt(),leafDetail=LeafDetail(width=.27f,base=LeafBase.ROUNDED,tip=LeafTip.ROUNDED,veinPairs=9,glossy=true),
                architecture=TreeArchitecture(radius=94f,fork=216f,trunkWidth=17f,density=1f),study=StudyForm.WINGED_CITRUS),
            R.string.bot_citrus_maxima_description,R.string.bot_citrus_maxima_distribution,PlantView.entries.zip(listOf(R.string.bot_citrus_maxima_habit,R.string.bot_citrus_maxima_leaf,R.string.bot_citrus_maxima_flower,R.string.bot_citrus_maxima_fruit)).toMap(),
            setOf("powo","ntbg","efloras"),collectionIds=setOf("orchard"),phenologyNote=R.string.bot_orchard_season)
    )
}
