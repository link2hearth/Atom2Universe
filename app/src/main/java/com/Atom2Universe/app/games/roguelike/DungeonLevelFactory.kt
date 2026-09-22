package com.Atom2Universe.app.games.roguelike

import kotlin.random.Random

/** Pure generation pipeline, shared by gameplay and the connectivity/population checks. */
internal object DungeonLevelFactory {
    data class Prepared(val level:DungeonLevel,val layout:DungeonLayout,val population:PopulationPlan)
    fun create(floor:Int,rng:Random,spec:DungeonSpec= DungeonFormats.rollForFloor(floor,rng)):Prepared {
        val plan=DungeonDistricts.generate(spec,rng)
        val level=DungeonLevel(spec.w,spec.h,floor)
        level.theme=spec.regions.first();level.format=spec.format;level.targetPacks=spec.packs
        for(y in 0 until level.h)for(x in 0 until level.w) {
            level.tiles[y][x]=plan.layout.tiles[y][x]
            level.themes[y][x]=plan.themes[y][x]
            level.districts[y][x]=plan.districts[y][x]
        }
        level.start=plan.layout.start
        if(spec.regions.contains(DungeonTheme.CEMETERY))level.mausoleums=CemeteryMonuments.place(level)
        level.passages=DungeonPassages.place(level)
        level.waterways=DungeonWaterways.place(level)
        level.scenery=DungeonScenery.place(level)+(level.start to MapScenery(SceneryKind.BONFIRE))
        val population=DungeonPopulation.plan(level,plan.layout.rooms,spec.packs,rng)
        level.sites=population.sites;level.quietCells=population.quietCells
        level.campDistances=DungeonPaths.distances(level,level.start)
        return Prepared(level,plan.layout,population)
    }
}
