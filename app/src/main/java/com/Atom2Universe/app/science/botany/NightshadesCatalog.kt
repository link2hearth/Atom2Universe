package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.R

/** Second living-tree enrichment batch. Cultivated forms are examples, not species-wide invariants. */
internal object NightshadesCatalog {
    val species=listOf(
        plant("lycopersicum",R.string.bot_lycopersicum_name,R.string.bot_lycopersicum_latin,R.string.bot_lycopersicum_aliases,
            R.string.bot_lycopersicum_description,R.string.bot_lycopersicum_distribution,
            listOf(R.string.bot_lycopersicum_habit,R.string.bot_lycopersicum_leaf,R.string.bot_lycopersicum_flower,R.string.bot_lycopersicum_fruit),
            StudyForm.PINNATE_BERRY,40,250,LeafShape.PINNATE,LeafMargin.LOBED,FlowerColor.YELLOW,25f,2.5f,7f,0xffc94d36.toInt()),
        plant("tuberosum",R.string.bot_tuberosum_name,R.string.bot_tuberosum_latin,R.string.bot_tuberosum_aliases,
            R.string.bot_tuberosum_description,R.string.bot_tuberosum_distribution,
            listOf(R.string.bot_tuberosum_habit,R.string.bot_tuberosum_leaf,R.string.bot_tuberosum_flower,R.string.bot_tuberosum_fruit),
            StudyForm.TUBEROUS_BERRY,30,100,LeafShape.PINNATE,LeafMargin.ENTIRE,FlowerColor.PURPLE,25f,3f,2f,0xff79964f.toInt()),
        plant("melongena",R.string.bot_melongena_name,R.string.bot_melongena_latin,R.string.bot_melongena_aliases,
            R.string.bot_melongena_description,R.string.bot_melongena_distribution,
            listOf(R.string.bot_melongena_habit,R.string.bot_melongena_leaf,R.string.bot_melongena_flower,R.string.bot_melongena_fruit),
            StudyForm.LOBED_BERRY,40,150,LeafShape.OVAL,LeafMargin.LOBED,FlowerColor.PURPLE,20f,4f,18f,0xff4e305d.toInt()),
        plant("capsicum",R.string.bot_capsicum_name,R.string.bot_capsicum_latin,R.string.bot_capsicum_aliases,
            R.string.bot_capsicum_description,R.string.bot_capsicum_distribution,
            listOf(R.string.bot_capsicum_habit,R.string.bot_capsicum_leaf,R.string.bot_capsicum_flower,R.string.bot_capsicum_fruit),
            StudyForm.HOLLOW_BERRY,30,150,LeafShape.OVAL,LeafMargin.ENTIRE,FlowerColor.WHITE,12f,2f,10f,0xffcd4431.toInt()),
        plant("nigrum",R.string.bot_nigrum_name,R.string.bot_nigrum_latin,R.string.bot_nigrum_aliases,
            R.string.bot_nigrum_description,R.string.bot_nigrum_distribution,
            listOf(R.string.bot_nigrum_habit,R.string.bot_nigrum_leaf,R.string.bot_nigrum_flower,R.string.bot_nigrum_fruit),
            StudyForm.SMALL_BERRY_CYME,15,80,LeafShape.OVAL,LeafMargin.SERRATE,FlowerColor.WHITE,7f,1.2f,.8f,0xff30303a.toInt())
    )

    private fun plant(id:String,name:Int,latin:Int,aliases:Int,description:Int,distribution:Int,captions:List<Int>,
                      form:StudyForm,min:Int,max:Int,leaf:LeafShape,margin:LeafMargin,color:FlowerColor,
                      leafCm:Float,flowerCm:Float,fruitCm:Float,fruitColor:Int):PlantSpecies {
        val wild=form==StudyForm.SMALL_BERRY_CYME
        return PlantSpecies(id,name,latin,aliases,"Solanaceae",setOf("herbs"),min,max,
            if(wild)setOf(6,7,8,9,10) else emptySet(),if(wild)setOf(7,8,9,10,11) else emptySet(),
            if(wild)setOf(Habitat.DISTURBED,Habitat.CULTIVATED) else setOf(Habitat.CULTIVATED),false,
            PlantLook(Crown.HERB,leaf,margin,LeafArrangement.ALTERNATE,FlowerShape.STAR,FruitShape.BERRY,color,
                leafCm,flowerCm,fruitCm,leaflets=7,petals=5,fruitColor=fruitColor,seed=130+form.ordinal,study=form),
            description,distribution,PlantView.entries.zip(captions).toMap(),
            if(wild)setOf("powo","efloras") else setOf("ncsu","powo"),
            additionalMargins=if(wild)setOf(LeafMargin.ENTIRE) else emptySet(),
            additionalColors=if(form==StudyForm.TUBEROUS_BERRY)setOf(FlowerColor.WHITE,FlowerColor.PINK) else if(form==StudyForm.HOLLOW_BERRY)setOf(FlowerColor.PURPLE) else emptySet(),
            collectionIds=setOf("nightshades"),phenologyNote=if(wild)R.string.bot_nightshade_wild_season else R.string.bot_nightshade_crop_season)
    }
}
