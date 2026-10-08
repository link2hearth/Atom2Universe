package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** Food IDs are saved in inventories. Never reorder or reuse them. */
internal object KitchenItems {
    const val BEEF: Short = 9930
    const val PORK: Short = 9931
    const val CHICKEN: Short = 9932
    const val MUTTON: Short = 9933
    const val STEAK: Short = 9934
    const val PORK_CHOP: Short = 9935
    const val ROAST_CHICKEN: Short = 9936
    const val LAMB_CHOP: Short = 9937
    const val BUTTER: Short = 9938
    const val CREAM: Short = 9939
    const val CORNMEAL: Short = 9940
    const val CORNBREAD: Short = 9941
    const val CHEESE_SANDWICH: Short = 9942
    const val EGG_SANDWICH: Short = 9943
    const val RADISH_TARTINE: Short = 9944
    const val BURGER: Short = 9945
    const val CHICKEN_BURGER: Short = 9946
    const val BURGER_FRIES: Short = 9947
    const val HOT_DOG: Short = 9948
    const val PIZZA: Short = 9949
    const val PIZZA_SUPREME: Short = 9950
    const val TACOS: Short = 9951
    const val TOMATO_SOUP: Short = 9952
    const val PEA_SOUP: Short = 9953
    const val LEEK_SOUP: Short = 9954
    const val CHICKEN_SOUP: Short = 9955
    const val BEEF_STEW: Short = 9956
    const val LAMB_STEW: Short = 9957
    const val SHEPHERDS_PIE: Short = 9958
    const val STUFFED_PEPPER: Short = 9959
    const val STUFFED_TOMATO: Short = 9960
    const val PORK_SKEWER: Short = 9961
    const val CHICKEN_PIE: Short = 9962
    const val FISH_AND_CHIPS: Short = 9963
    const val GRILLED_CORN: Short = 9964
    const val BROCCOLI_GRATIN: Short = 9965
    const val CAULIFLOWER_GRATIN: Short = 9966
    const val EGGPLANT_BAKE: Short = 9967
    const val CHEESE_QUICHE: Short = 9968
    const val LEEK_QUICHE: Short = 9969
    const val EGG_SALAD: Short = 9970
    const val SHORTBREAD: Short = 9971
    const val FRENCH_TOAST: Short = 9972
    const val BRIOCHE: Short = 9973
    const val BERRY_PANCAKES: Short = 9974
    const val PINEAPPLE_CAKE: Short = 9975
    const val CUSTARD: Short = 9976
    const val FRUIT_SALAD: Short = 9977
    const val STRAWBERRY_JAM: Short = 9978
    const val RASPBERRY_JAM: Short = 9979
    const val BLUEBERRY_JAM: Short = 9980

    private enum class Shape { RAW, ROAST, BUTTER, JUG, MEAL, BREAD, CORN, SOUP, PLATE, PIE, SALAD, JAM, CAKE }
    /** [weight]: how much of a "standard dish" it heals (0 = ingredient, .6 snack, 1 dish, 1.5 full meal). */
    private data class Food(val id: Short, val name: String, val weight: Float, val shape: Shape, val color: Long)
    private val foods = listOf(
        Food(BEEF,"beef",0f,Shape.RAW,0xFFC76258),
        Food(PORK,"pork",0f,Shape.RAW,0xFFE39A91),
        Food(CHICKEN,"chicken",0f,Shape.RAW,0xFFE9C3A3),
        Food(MUTTON,"mutton",0f,Shape.RAW,0xFFAD6662),
        Food(STEAK,"steak",1.0f,Shape.ROAST,0xFF985B38),
        Food(PORK_CHOP,"pork_chop",1.0f,Shape.ROAST,0xFFBB7850),
        Food(ROAST_CHICKEN,"roast_chicken",1.0f,Shape.ROAST,0xFFD29B54),
        Food(LAMB_CHOP,"lamb_chop",1.0f,Shape.ROAST,0xFFA16F45),
        Food(BUTTER,"butter",0f,Shape.BUTTER,0xFFF1D77D),
        Food(CREAM,"cream",0f,Shape.JUG,0xFFF5E9CE),
        Food(CORNMEAL,"cornmeal",0f,Shape.MEAL,0xFFE2BC62),
        Food(CORNBREAD,"cornbread",1.0f,Shape.BREAD,0xFFE2AF52),
        Food(CHEESE_SANDWICH,"cheese_sandwich",1.0f,Shape.BREAD,0xFFDBBA79),
        Food(EGG_SANDWICH,"egg_sandwich",1.0f,Shape.BREAD,0xFFE7CF7E),
        Food(RADISH_TARTINE,"radish_tartine",0.6f,Shape.BREAD,0xFFE8B4B8),
        Food(BURGER,"burger",1.5f,Shape.BREAD,0xFFB56B3C),
        Food(CHICKEN_BURGER,"chicken_burger",1.5f,Shape.BREAD,0xFFD4A05A),
        Food(BURGER_FRIES,"burger_fries",1.5f,Shape.BREAD,0xFFC0723A),
        Food(HOT_DOG,"hot_dog",1.5f,Shape.BREAD,0xFFC98A52),
        Food(PIZZA,"pizza",1.0f,Shape.PIE,0xFFD9704A),
        Food(PIZZA_SUPREME,"pizza_supreme",1.5f,Shape.PIE,0xFFC65B3C),
        Food(TACOS,"tacos",1.5f,Shape.PLATE,0xFFD8A648),
        Food(TOMATO_SOUP,"tomato_soup",1.0f,Shape.SOUP,0xFFD36548),
        Food(PEA_SOUP,"pea_soup",1.0f,Shape.SOUP,0xFF90AF5F),
        Food(LEEK_SOUP,"leek_soup",1.0f,Shape.SOUP,0xFFB8C987),
        Food(CHICKEN_SOUP,"chicken_soup",1.0f,Shape.SOUP,0xFFCEAD70),
        Food(BEEF_STEW,"beef_stew",1.5f,Shape.SOUP,0xFFA66943),
        Food(LAMB_STEW,"lamb_stew",1.5f,Shape.SOUP,0xFFB98956),
        Food(SHEPHERDS_PIE,"shepherds_pie",1.5f,Shape.PIE,0xFFE3C174),
        Food(STUFFED_PEPPER,"stuffed_pepper",1.5f,Shape.PLATE,0xFFD68C42),
        Food(STUFFED_TOMATO,"stuffed_tomato",1.5f,Shape.PLATE,0xFFD76146),
        Food(PORK_SKEWER,"pork_skewer",1.0f,Shape.ROAST,0xFFB47A55),
        Food(CHICKEN_PIE,"chicken_pie",1.5f,Shape.PIE,0xFFC99A61),
        Food(FISH_AND_CHIPS,"fish_and_chips",1.5f,Shape.PLATE,0xFFE0BD81),
        Food(GRILLED_CORN,"grilled_corn",0.6f,Shape.CORN,0xFFE1B442),
        Food(BROCCOLI_GRATIN,"broccoli_gratin",1.0f,Shape.PLATE,0xFF78A757),
        Food(CAULIFLOWER_GRATIN,"cauliflower_gratin",1.0f,Shape.PLATE,0xFFE5D7A0),
        Food(EGGPLANT_BAKE,"eggplant_bake",1.0f,Shape.PLATE,0xFF947394),
        Food(CHEESE_QUICHE,"cheese_quiche",1.0f,Shape.PIE,0xFFE2BE63),
        Food(LEEK_QUICHE,"leek_quiche",1.0f,Shape.PIE,0xFFACC079),
        Food(EGG_SALAD,"egg_salad",1.0f,Shape.SALAD,0xFFE8D8A0),
        Food(SHORTBREAD,"shortbread",0.6f,Shape.CAKE,0xFFE6C27A),
        Food(FRENCH_TOAST,"french_toast",1.0f,Shape.CAKE,0xFFD9A75E),
        Food(BRIOCHE,"brioche",1.0f,Shape.BREAD,0xFFE0A24E),
        Food(BERRY_PANCAKES,"berry_pancakes",1.5f,Shape.CAKE,0xFFBA768F),
        Food(PINEAPPLE_CAKE,"pineapple_cake",1.5f,Shape.CAKE,0xFFE7C671),
        Food(CUSTARD,"custard",1.0f,Shape.JUG,0xFFE4CF88),
        Food(FRUIT_SALAD,"fruit_salad",1.0f,Shape.SALAD,0xFFE99A75),
        Food(STRAWBERRY_JAM,"strawberry_jam",0.6f,Shape.JAM,0xFFD46565),
        Food(RASPBERRY_JAM,"raspberry_jam",0.6f,Shape.JAM,0xFFBB5379),
        Food(BLUEBERRY_JAM,"blueberry_jam",0.6f,Shape.JAM,0xFF7978AD)
    )
    private val byId = foods.associateBy { it.id }
    fun isItem(id: Short) = id in byId
    fun isRawMeat(id: Short) = id in BEEF..MUTTON
    fun weight(id: Short) = byId[id]?.weight ?: 0f

    fun definitions(template: BlockDef): List<BlockDef> = foods.map { food ->
        val texture = "kitchen:${food.name}"
        BlockRegistry.registerGeneratedTexture(texture) { size -> texture(food,size) }
        template.copy(id=food.id, name="kitchen_${food.name}", textureTop=texture, textureSide=texture,
            textureBottom=texture, drop="", placeable=false, decoration=false, creativeTab="resources",
            harvestCategory="technical", placementRule="any", tags=setOf("gardening"))
    }

    /** Small native pixel icons, with different silhouettes for ingredients, bowls and baked dishes. */
    private fun texture(food: Food, size: Int): Bitmap {
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); val paint=Paint()
        fun rect(x: Int,y: Int,w: Int,h: Int,c: Long) {
            paint.color=c.toInt();canvas.drawRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),paint)
        }
        val light=0xFFF5E8C9; val rim=0xFF729A9C; val dark=0xFF76553C
        when(food.shape) {
            Shape.RAW, Shape.ROAST -> {
                rect(5,8,19,17,food.color);rect(3,12,25,9,food.color);rect(9,6,10,3,food.color)
                rect(21,19,7,5,light);rect(25,17,4,9,light)
                if(food.shape==Shape.RAW) { rect(9,10,3,10,light);rect(12,17,6,3,light) }
                else for(x in 8..18 step 5) rect(x,10,2,10,dark)
            }
            Shape.SOUP, Shape.SALAD -> {
                rect(3,12,26,8,food.color);rect(5,20,22,5,rim);rect(8,25,16,3,0xFF4C7076)
                for(i in 0..4) rect(6+i*4,13+(i%2)*3,3,3,if(i%2==0) light else 0xFF6B9850)
                rect(3,19,26,2,light)
            }
            Shape.JUG, Shape.JAM, Shape.MEAL -> {
                rect(8,8,16,19,food.color);rect(10,5,12,3,light);rect(10,27,12,2,rim)
                rect(10,11,3,10,light)
                if(food.shape==Shape.JUG) {rect(24,11,4,12,rim);rect(24,14,2,6,0x00000000)}
                if(food.shape==Shape.JAM) {rect(7,5,18,4,rim);rect(14,15,7,6,light)}
            }
            Shape.CORN -> {
                rect(11,5,10,21,food.color);rect(8,9,3,14,0xFF729957);rect(21,13,3,14,0xFF729957)
                for(y in 8..22 step 4) {rect(12,y,3,2,light);rect(17,y,3,2,light)}
            }
            else -> {
                rect(3,24,26,3,rim);rect(6,11,20,13,dark);rect(5,9,22,12,food.color)
                rect(8,6,16,3,food.color);rect(8,10,16,2,light)
                if(food.shape==Shape.PIE) for(x in 9..23 step 5) rect(x,13,2,6,light)
                if(food.shape==Shape.PLATE || food.shape==Shape.CAKE) for(i in 0..3)
                    rect(8+i*5,14+(i%2)*3,3,3,if(i%2==0) light else 0xFF729957)
                if(food.shape==Shape.BREAD) {rect(6,17,20,3,light);rect(8,20,16,2,0xFF729957)}
            }
        }
        return if(size==32) bitmap else Bitmap.createScaledBitmap(bitmap,size,size,false).also { bitmap.recycle() }
    }
}
