package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.CraftDef
import com.Atom2Universe.app.games.caves.node.CraftGroup
import com.Atom2Universe.app.games.caves.node.ForgedEquipment as G
import com.Atom2Universe.app.games.caves.node.MineralItems as M
import com.Atom2Universe.app.games.caves.node.MineralItems.Form as Fm
import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import com.Atom2Universe.app.games.caves.node.ExpeditionItems as E
import com.Atom2Universe.app.games.caves.world.FrontierWorkshops.Recipe

internal object MineralRecipes {
    val workshops: List<Recipe> by lazy { buildList {
        for(stage in 0..MineralProgression.LAST_STAGE) {
            fun id(form: Fm) = M.id(stage,form)
            val heat=if(MineralProgression.metal(stage)<=2) 2 else 3
            val forge=MineralProgression.forgeRequired(stage)
            add(Recipe(F.CRUSHER,mapOf(id(Fm.RAW) to 1),mapOf(id(Fm.DUST) to 2),8,true))
            for(form in listOf(Fm.RAW,Fm.DUST)) {
                add(Recipe(E.FORGE,mapOf(id(form) to 1),mapOf(id(Fm.INGOT) to 1),6,heat=heat,forgeTier=forge))
            }
            for(form in listOf(Fm.RAW,Fm.DUST,Fm.INGOT)) {
                add(Recipe(F.CRUCIBLE,mapOf(id(form) to 1),mapOf(id(Fm.MOLTEN) to 1),4,heat=heat,forgeTier=forge))
            }
            for((form,measures) in listOf(Fm.INGOT to 1,Fm.PLATE to 1,Fm.BLADE to 2,Fm.HEAD to 4)) {
                add(Recipe(F.CAST_MOLD,mapOf(id(Fm.MOLTEN) to measures),mapOf(id(form) to 1),6+measures*2))
            }
            add(Recipe(F.PRESS,mapOf(id(Fm.PLATE) to 1),mapOf(id(Fm.ARMOR_PART) to 1),6,true))
            // Current-tier iron remains useful for industrial supplies throughout the cycle.
            if(MineralProgression.metal(stage)==0 && stage>0) {
                add(Recipe(F.PRESS,mapOf(id(Fm.PLATE) to 1),mapOf(E.RIVETS to 8),6,true))
            }
        }
        for(tier in 1..MineralProgression.TIERS) {
            val iron=M.id((tier-1)*MineralProgression.METALS,Fm.INGOT)
            add(Recipe(E.FORGE,mapOf(iron to 2,FrontierWorkshops.CHARCOAL to 1),mapOf(M.steel(tier) to 1),15,heat=3,forgeTier=tier-1))
            add(Recipe(F.CRUCIBLE,mapOf(M.steel(tier) to 1),mapOf(M.moltenSteel(tier) to 1),4,heat=3,forgeTier=tier-1))
            add(Recipe(F.CAST_MOLD,mapOf(M.moltenSteel(tier) to 1),mapOf(M.steel(tier) to 1),8))
            if(tier>1) add(Recipe(F.CAST_MOLD,mapOf(M.moltenSteel(tier) to 1),mapOf(E.STEEL_PLATE to 1),8))
        }
    } }
    val crafts: List<CraftDef> by lazy { buildList {
        for(stage in 0..MineralProgression.LAST_STAGE) {
            fun id(form: Fm)=M.id(stage,form)
            fun craft(form: Fm, ingredients: List<Pair<Short,Int>>) {
                add(CraftDef(ingredients,result=id(form),station=E.ANVIL))
            }
            craft(Fm.PICK,listOf(id(Fm.HEAD) to 1,3110.toShort() to 2))
            craft(Fm.SWORD,listOf(id(Fm.BLADE) to 1,3110.toShort() to 1,E.RIVETS to 2))
            craft(Fm.SPEAR,listOf(id(Fm.BLADE) to 1,3110.toShort() to 3,E.RIVETS to 1))
            craft(Fm.HAMMER,listOf(id(Fm.HEAD) to 2,3110.toShort() to 2,E.RIVETS to 2))
            for(slot in G.Slot.entries) add(CraftDef(listOf(id(Fm.ARMOR_PART) to slot.plates,
                F.CLOTH to 1,E.RIVETS to 2),result=G.Template(stage,slot).id,station=E.ANVIL))
        }
        for(tier in 1..MineralProgression.TIERS) {
            add(CraftDef(listOf(M.steel(tier) to 6,2300.toShort() to 4),result=M.reinforcement(tier),station=E.ANVIL))
        }
    } }
    fun replacesCraft(recipe: CraftDef) = M.variant(recipe.result) != null
    /** Ordinary machines accept the local cycle's iron/steel, while equipment keeps exact grades. */
    fun industrialCraft(recipe: CraftDef): CraftDef {
        val replace=recipe.ingredients.filter { it.first==3114.toShort() || it.first==F.STEEL }
        if(replace.isEmpty()) return recipe
        return recipe.copy(ingredients=recipe.ingredients-replace.toSet(), groups=recipe.groups+replace.map { (id,count) ->
            if(id==F.STEEL) CraftGroup("mineral_steel",(1..MineralProgression.TIERS).map(M::steel),count)
            else CraftGroup("mineral_iron",(0 until MineralProgression.TIERS).map { M.id(it*MineralProgression.METALS,Fm.INGOT) },count)
        })
    }
    fun replacesWorkshop(recipe: Recipe) = recipe.output.keys.any { M.variant(it)!=null || it==F.STEEL || it==F.MOLTEN_STEEL }
}
