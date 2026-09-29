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
    const val GRILLED_CORN: Short = 9942
    const val TOMATO_SOUP: Short = 9943
    const val PEA_SOUP: Short = 9944
    const val LEEK_SOUP: Short = 9945
    const val BROCCOLI_GRATIN: Short = 9946
    const val CAULIFLOWER_GRATIN: Short = 9947
    const val STUFFED_PEPPER: Short = 9948
    const val STUFFED_TOMATO: Short = 9949
    const val EGGPLANT_BAKE: Short = 9950
    const val SHEPHERDS_PIE: Short = 9951
    const val BEEF_STEW: Short = 9952
    const val PORK_SKEWER: Short = 9953
    const val CHICKEN_PIE: Short = 9954
    const val CHICKEN_SOUP: Short = 9955
    const val LAMB_STEW: Short = 9956
    const val SPICY_BEEF: Short = 9957
    const val FISH_PIE: Short = 9958
    const val FISH_CHOWDER: Short = 9959
    const val CHEESE_QUICHE: Short = 9960
    const val LEEK_QUICHE: Short = 9961
    const val VEGETABLE_OMELETTE: Short = 9962
    const val CHEESE_SANDWICH: Short = 9963
    const val EGG_SALAD: Short = 9964
    const val GARDEN_SALAD: Short = 9965
    const val FRUIT_SALAD: Short = 9966
    const val STRAWBERRY_JAM: Short = 9967
    const val RASPBERRY_JAM: Short = 9968
    const val BLUEBERRY_JAM: Short = 9969
    const val PINEAPPLE_CAKE: Short = 9970
    const val BERRY_PANCAKES: Short = 9971
    const val CUSTARD: Short = 9972

    private enum class Shape { RAW, ROAST, BUTTER, JUG, MEAL, BREAD, CORN, SOUP, PLATE, PIE, SALAD, JAM, CAKE }
    private data class Food(val id: Short, val name: String, val heal: Int, val shape: Shape, val color: Long)
    private val foods = listOf(
        Food(BEEF,"beef",0,Shape.RAW,0xFFC76258), Food(PORK,"pork",0,Shape.RAW,0xFFE39A91),
        Food(CHICKEN,"chicken",0,Shape.RAW,0xFFE9C3A3), Food(MUTTON,"mutton",0,Shape.RAW,0xFFAD6662),
        Food(STEAK,"steak",10,Shape.ROAST,0xFF985B38), Food(PORK_CHOP,"pork_chop",10,Shape.ROAST,0xFFBB7850),
        Food(ROAST_CHICKEN,"roast_chicken",11,Shape.ROAST,0xFFD29B54), Food(LAMB_CHOP,"lamb_chop",10,Shape.ROAST,0xFFA16F45),
        Food(BUTTER,"butter",0,Shape.BUTTER,0xFFF1D77D), Food(CREAM,"cream",0,Shape.JUG,0xFFF5E9CE),
        Food(CORNMEAL,"cornmeal",0,Shape.MEAL,0xFFE2BC62), Food(CORNBREAD,"cornbread",9,Shape.BREAD,0xFFE2AF52),
        Food(GRILLED_CORN,"grilled_corn",7,Shape.CORN,0xFFE1B442),
        Food(TOMATO_SOUP,"tomato_soup",11,Shape.SOUP,0xFFD36548), Food(PEA_SOUP,"pea_soup",12,Shape.SOUP,0xFF90AF5F),
        Food(LEEK_SOUP,"leek_soup",13,Shape.SOUP,0xFFB8C987),
        Food(BROCCOLI_GRATIN,"broccoli_gratin",17,Shape.PLATE,0xFF78A757), Food(CAULIFLOWER_GRATIN,"cauliflower_gratin",17,Shape.PLATE,0xFFE5D7A0),
        Food(STUFFED_PEPPER,"stuffed_pepper",21,Shape.PLATE,0xFFD68C42), Food(STUFFED_TOMATO,"stuffed_tomato",21,Shape.PLATE,0xFFD76146),
        Food(EGGPLANT_BAKE,"eggplant_bake",19,Shape.PLATE,0xFF947394), Food(SHEPHERDS_PIE,"shepherds_pie",26,Shape.PIE,0xFFE3C174),
        Food(BEEF_STEW,"beef_stew",24,Shape.SOUP,0xFFA66943), Food(PORK_SKEWER,"pork_skewer",20,Shape.ROAST,0xFFB47A55),
        Food(CHICKEN_PIE,"chicken_pie",25,Shape.PIE,0xFFC99A61), Food(CHICKEN_SOUP,"chicken_soup",22,Shape.SOUP,0xFFCEAD70),
        Food(LAMB_STEW,"lamb_stew",24,Shape.SOUP,0xFFB98956), Food(SPICY_BEEF,"spicy_beef",25,Shape.PLATE,0xFFC4693E),
        Food(FISH_PIE,"fish_pie",24,Shape.PIE,0xFFE0BD81), Food(FISH_CHOWDER,"fish_chowder",23,Shape.SOUP,0xFFE6D6AC),
        Food(CHEESE_QUICHE,"cheese_quiche",22,Shape.PIE,0xFFE2BE63), Food(LEEK_QUICHE,"leek_quiche",23,Shape.PIE,0xFFACC079),
        Food(VEGETABLE_OMELETTE,"vegetable_omelette",19,Shape.PLATE,0xFFE7C96D),
        Food(CHEESE_SANDWICH,"cheese_sandwich",13,Shape.BREAD,0xFFDBBA79), Food(EGG_SALAD,"egg_salad",16,Shape.SALAD,0xFFE8D8A0),
        Food(GARDEN_SALAD,"garden_salad",10,Shape.SALAD,0xFF94BA62), Food(FRUIT_SALAD,"fruit_salad",12,Shape.SALAD,0xFFE99A75),
        Food(STRAWBERRY_JAM,"strawberry_jam",8,Shape.JAM,0xFFD46565), Food(RASPBERRY_JAM,"raspberry_jam",8,Shape.JAM,0xFFBB5379),
        Food(BLUEBERRY_JAM,"blueberry_jam",8,Shape.JAM,0xFF7978AD), Food(PINEAPPLE_CAKE,"pineapple_cake",21,Shape.CAKE,0xFFE7C671),
        Food(BERRY_PANCAKES,"berry_pancakes",23,Shape.CAKE,0xFFBA768F), Food(CUSTARD,"custard",18,Shape.JUG,0xFFE4CF88)
    )
    private val byId = foods.associateBy { it.id }
    fun isItem(id: Short) = id in byId
    fun isRawMeat(id: Short) = id in BEEF..MUTTON
    fun baseHealing(id: Short) = byId[id]?.heal ?: 0

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
