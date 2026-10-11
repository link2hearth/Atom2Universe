package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.R

/** Fifth reference batch: familiar plants and the organs behind everyday products. */
internal object UsefulPlantsCatalog {
    val species=listOf(
        PlantSpecies("camellia_sinensis",R.string.bot_camellia_sinensis_name,R.string.bot_camellia_sinensis_latin,R.string.bot_camellia_sinensis_aliases,"Theaceae",setOf("trees","shrubs"),
            100,900,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),true,
            PlantLook(Crown.SHRUB,LeafShape.OVAL,LeafMargin.SERRATE,LeafArrangement.ALTERNATE,FlowerShape.CUP,FruitShape.CAPSULE,FlowerColor.WHITE,
                10.0f,3.0f,3.0f,seed=191,petals=7,petiole=.12f,
                leafColor=0xff496f43.toInt(),leafDetail=LeafDetail(width=.25f,veinPairs=8,glossy=true),
                architecture=TreeArchitecture(radius=82f,top=48f,bottom=240f,fork=263f,trunkWidth=7f,density=.7f),study=StudyForm.TEA_CAPSULE),
            R.string.bot_camellia_sinensis_description,R.string.bot_camellia_sinensis_distribution,PlantView.entries.zip(listOf(R.string.bot_camellia_sinensis_habit,R.string.bot_camellia_sinensis_leaf,R.string.bot_camellia_sinensis_flower,R.string.bot_camellia_sinensis_fruit)).toMap(),
            setOf("powo","ncsu","efloras"),collectionIds=setOf("useful_plants"),phenologyNote=R.string.bot_useful_plants_season),
        PlantSpecies("gossypium_hirsutum",R.string.bot_gossypium_hirsutum_name,R.string.bot_gossypium_hirsutum_latin,R.string.bot_gossypium_hirsutum_aliases,"Malvaceae",setOf("herbs","shrubs"),
            60,200,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),false,
            PlantLook(Crown.HERB,LeafShape.PALMATILOBED,LeafMargin.LOBED,LeafArrangement.ALTERNATE,FlowerShape.CUP,FruitShape.CAPSULE,FlowerColor.YELLOW,
                14.0f,6.0f,4.0f,seed=192,petals=5,petiole=.30f,
                leafColor=0xff496f43.toInt(),leafDetail=LeafDetail(base=LeafBase.CORDATE,venation=Venation.PALMATE,palmateSegments=5,lobeDepth=.30f,ciliate=true),
                study=StudyForm.FIBROUS_BOLL),
            R.string.bot_gossypium_hirsutum_description,R.string.bot_gossypium_hirsutum_distribution,PlantView.entries.zip(listOf(R.string.bot_gossypium_hirsutum_habit,R.string.bot_gossypium_hirsutum_leaf,R.string.bot_gossypium_hirsutum_flower,R.string.bot_gossypium_hirsutum_fruit)).toMap(),
            setOf("powo","ncsu","efloras"),collectionIds=setOf("useful_plants"),phenologyNote=R.string.bot_useful_plants_season,additionalColors=setOf(FlowerColor.WHITE,FlowerColor.PINK),variablePersistence=true),
        PlantSpecies("punica_granatum",R.string.bot_punica_granatum_name,R.string.bot_punica_granatum_latin,R.string.bot_punica_granatum_aliases,"Lythraceae",setOf("trees","shrubs"),
            200,600,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),false,
            PlantLook(Crown.SHRUB,LeafShape.LANCE,LeafMargin.ENTIRE,LeafArrangement.OPPOSITE,FlowerShape.CUP,FruitShape.BERRY,FlowerColor.RED,
                6.0f,4.0f,9.0f,seed=193,petals=6,petiole=.12f,
                leafColor=0xff496f43.toInt(),leafDetail=LeafDetail(width=.16f,tip=LeafTip.ROUNDED,veinPairs=8,glossy=true),
                architecture=TreeArchitecture(radius=90f,top=38f,bottom=225f,fork=266f,trunkWidth=8f,density=.50f,crooked=7f),study=StudyForm.CROWNED_BERRY),
            R.string.bot_punica_granatum_description,R.string.bot_punica_granatum_distribution,PlantView.entries.zip(listOf(R.string.bot_punica_granatum_habit,R.string.bot_punica_granatum_leaf,R.string.bot_punica_granatum_flower,R.string.bot_punica_granatum_fruit)).toMap(),
            setOf("powo","ncsu","efloras"),collectionIds=setOf("useful_plants"),phenologyNote=R.string.bot_useful_plants_season,additionalArrangements=setOf(LeafArrangement.ALTERNATE,LeafArrangement.WHORLED),additionalColors=setOf(FlowerColor.ORANGE),variablePersistence=true),
        PlantSpecies("persea_americana",R.string.bot_persea_americana_name,R.string.bot_persea_americana_latin,R.string.bot_persea_americana_aliases,"Lauraceae",setOf("trees"),
            500,2000,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),true,
            PlantLook(Crown.SPREAD,LeafShape.OVAL,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.PANICLE,FruitShape.BERRY,FlowerColor.GREEN,
                20.0f,1.0f,14.0f,seed=194,petals=6,petiole=.12f,
                leafColor=0xff496f43.toInt(),leafDetail=LeafDetail(width=.23f,tip=LeafTip.ACUMINATE,veinPairs=10),
                architecture=TreeArchitecture(radius=99f,top=35f,bottom=200f,fork=218f,trunkWidth=19f,density=.65f),study=StudyForm.SINGLE_SEED_BERRY),
            R.string.bot_persea_americana_description,R.string.bot_persea_americana_distribution,PlantView.entries.zip(listOf(R.string.bot_persea_americana_habit,R.string.bot_persea_americana_leaf,R.string.bot_persea_americana_flower,R.string.bot_persea_americana_fruit)).toMap(),
            setOf("powo","ncsu"),collectionIds=setOf("useful_plants"),phenologyNote=R.string.bot_useful_plants_season,additionalColors=setOf(FlowerColor.GREEN,FlowerColor.YELLOW)),
        PlantSpecies("laurus_nobilis",R.string.bot_laurus_nobilis_name,R.string.bot_laurus_nobilis_latin,R.string.bot_laurus_nobilis_aliases,"Lauraceae",setOf("trees","shrubs"),
            200,1500,emptySet(),emptySet(),setOf(Habitat.CULTIVATED),true,
            PlantLook(Crown.ROUND,LeafShape.LANCE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,FlowerShape.UMBEL,FruitShape.DRUPE,FlowerColor.YELLOW,
                9.0f,0.8f,1.5f,seed=195,petals=4,petiole=.12f,
                leafColor=0xff496f43.toInt(),leafDetail=LeafDetail(width=.19f,teeth=LeafTeeth.WAVY,veinPairs=9,glossy=true),
                architecture=TreeArchitecture(radius=75f,top=32f,bottom=239f,fork=243f,trunkWidth=11f,density=.85f),study=StudyForm.DIOECIOUS_UMBEL),
            R.string.bot_laurus_nobilis_description,R.string.bot_laurus_nobilis_distribution,PlantView.entries.zip(listOf(R.string.bot_laurus_nobilis_habit,R.string.bot_laurus_nobilis_leaf,R.string.bot_laurus_nobilis_flower,R.string.bot_laurus_nobilis_fruit)).toMap(),
            setOf("powo","ncsu","efloras"),collectionIds=setOf("useful_plants"),phenologyNote=R.string.bot_useful_plants_season,additionalColors=setOf(FlowerColor.GREEN,FlowerColor.YELLOW))
    )
}
