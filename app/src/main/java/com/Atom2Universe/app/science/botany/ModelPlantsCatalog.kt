package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.R

/** First enrichment batch: exact names from the living-tree archive retain their cross-module identity.
 * Pisum sativum is retained as the archive name; its newer Lathyrus name is explained in the fiche.
 */
internal object ModelPlantsCatalog {
    val species=listOf(
        PlantSpecies(
            id="arabidopsis",name=R.string.bot_arabidopsis_name,latin=R.string.bot_arabidopsis_latin,aliases=R.string.bot_arabidopsis_aliases,
            familyId="Brassicaceae",sectionIds=setOf("herbs"),heightMinCm=5,heightMaxCm=40,
            flowering=setOf(3,4,5,6),fruiting=setOf(4,5,6,7),habitats=setOf(Habitat.DISTURBED),evergreen=false,
            look=PlantLook(Crown.HERB,LeafShape.OVAL,LeafMargin.SERRATE,LeafArrangement.ROSETTE,
                FlowerShape.STAR,FruitShape.SILIQUE,FlowerColor.WHITE,3f,.5f,2f,petals=4,seed=121,
                leafDetail=LeafDetail(width=.20f,ciliate=true,veinPairs=5,tip=LeafTip.ROUNDED,teeth=LeafTeeth.WAVY),flowerDetail=FlowerDetail(stamens=6,shortStamens=2,petalWidth=19f),
                study=StudyForm.ROSETTE_SILIQUES),
            description=R.string.bot_arabidopsis_description,distribution=R.string.bot_arabidopsis_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_arabidopsis_habit,PlantView.LEAF to R.string.bot_arabidopsis_leaf,
                PlantView.FLOWER to R.string.bot_arabidopsis_flower,PlantView.FRUIT to R.string.bot_arabidopsis_fruit),
            sourceIds=setOf("powo"),additionalLeaves=setOf(LeafShape.LANCE),additionalArrangements=setOf(LeafArrangement.ALTERNATE),
            additionalMargins=setOf(LeafMargin.ENTIRE),collectionIds=setOf("model_plants"),phenologyNote=R.string.bot_model_wild_season
        ),
        PlantSpecies(
            id="pisum",name=R.string.bot_pisum_name,latin=R.string.bot_pisum_latin,aliases=R.string.bot_pisum_aliases,
            familyId="Fabaceae",sectionIds=setOf("herbs"),heightMinCm=30,heightMaxCm=200,
            flowering=emptySet(),fruiting=emptySet(),habitats=setOf(Habitat.CULTIVATED,Habitat.DISTURBED),evergreen=false,
            look=PlantLook(Crown.HERB,LeafShape.PINNATE,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,
                FlowerShape.PEA,FruitShape.POD,FlowerColor.WHITE,20f,3f,10f,leaflets=4,seed=122,
                leafDetail=LeafDetail(terminalTendril=true,leafletShape=LeafShape.OVAL,leafletWidth=.34f,leafletTip=LeafTip.ROUNDED),
                study=StudyForm.TENDRIL_LEGUME),
            description=R.string.bot_pisum_description,distribution=R.string.bot_pisum_distribution,
            captions=mapOf(PlantView.HABIT to R.string.bot_pisum_habit,PlantView.LEAF to R.string.bot_pisum_leaf,
                PlantView.FLOWER to R.string.bot_pisum_flower,PlantView.FRUIT to R.string.bot_pisum_fruit),
            sourceIds=setOf("umich","ncsu"),additionalColors=setOf(FlowerColor.PINK,FlowerColor.PURPLE),
            collectionIds=setOf("model_plants"),phenologyNote=R.string.bot_model_crop_season
        ),
        cereal("triticum",R.string.bot_triticum_name,R.string.bot_triticum_latin,R.string.bot_triticum_aliases,
            R.string.bot_triticum_description,R.string.bot_triticum_distribution,
            listOf(R.string.bot_triticum_habit,R.string.bot_triticum_leaf,R.string.bot_triticum_flower,R.string.bot_triticum_fruit),
            StudyForm.CEREAL_SPIKE,30,150,25f,12f,.7f,setOf("sernec")),
        cereal("oryza",R.string.bot_oryza_name,R.string.bot_oryza_latin,R.string.bot_oryza_aliases,
            R.string.bot_oryza_description,R.string.bot_oryza_distribution,
            listOf(R.string.bot_oryza_habit,R.string.bot_oryza_leaf,R.string.bot_oryza_flower,R.string.bot_oryza_fruit),
            StudyForm.CEREAL_PANICLE,50,150,40f,25f,.7f,setOf("efloras","powo")),
        cereal("zea",R.string.bot_zea_name,R.string.bot_zea_latin,R.string.bot_zea_aliases,
            R.string.bot_zea_description,R.string.bot_zea_distribution,
            listOf(R.string.bot_zea_habit,R.string.bot_zea_leaf,R.string.bot_zea_flower,R.string.bot_zea_fruit),
            StudyForm.MONOECIOUS_CEREAL,150,300,70f,30f,20f,setOf("ncsu"))
    )

    private fun cereal(id:String,name:Int,latin:Int,aliases:Int,description:Int,distribution:Int,captions:List<Int>,
                       form:StudyForm,min:Int,max:Int,leafCm:Float,flowerCm:Float,fruitCm:Float,sources:Set<String>)=PlantSpecies(
        id,name,latin,aliases,"Poaceae",setOf("herbs"),min,max,emptySet(),emptySet(),
        if(form==StudyForm.CEREAL_PANICLE)setOf(Habitat.CULTIVATED,Habitat.WET) else setOf(Habitat.CULTIVATED,Habitat.DISTURBED),false,
        PlantLook(Crown.GRASS,LeafShape.LINEAR,LeafMargin.ENTIRE,LeafArrangement.ALTERNATE,
            if(form==StudyForm.CEREAL_SPIKE)FlowerShape.SPIKE else FlowerShape.PANICLE,FruitShape.GRAIN,FlowerColor.GREEN,
            leafCm,flowerCm,fruitCm,seed=123+form.ordinal,leafDetail=LeafDetail(venation=Venation.PARALLEL),study=form),
        description,distribution,PlantView.entries.zip(captions).toMap(),sources,
        collectionIds=setOf("model_plants"),phenologyNote=R.string.bot_model_crop_season
    )
}
