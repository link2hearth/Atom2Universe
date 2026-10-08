package com.Atom2Universe.app.games.caves.ai

import com.Atom2Universe.app.games.caves.world.MapPoint
import com.Atom2Universe.app.games.caves.world.MapleCrossingMap
import kotlin.random.Random

/** Sparse ground-level patrols in distant plots, reachable from the player's garden. */
internal class SuburbDeployment(private val grid: NavGrid, spawn: MapPoint) {
    private val plots=Array(MapleCrossingMap.PLOT_COUNT * MapleCrossingMap.PLOT_COUNT) { ArrayList<Int>() }
    init {
        val seen=BooleanArray(grid.nodeCount); val queue=IntArray(grid.nodeCount)
        var read=0; var write=0
        val start=grid.nodeAt(spawn.x,spawn.y,spawn.z)
        if(start>=0) { seen[start]=true; queue[write++]=start }
        while(read<write) {
            val n=queue[read++]
            for(e in grid.edgeStart[n] until grid.edgeStart[n+1]) {
                val next=grid.edgeTarget[e]
                if(!seen[next] && grid.nodeY[next] in 4..5) { seen[next]=true; queue[write++]=next }
            }
        }
        for(n in 0 until grid.nodeCount) {
            if(!seen[n] || grid.nodeY[n]!=5) continue
            val x=grid.nodeX[n]; val z=grid.nodeZ[n]
            val col=(x-4)/MapleCrossingMap.PLOT_STEP
            val row=(z-4)/MapleCrossingMap.PLOT_STEP
            if(col !in 0 until MapleCrossingMap.PLOT_COUNT || row !in 0 until MapleCrossingMap.PLOT_COUNT) continue
            if(x-4-col*MapleCrossingMap.PLOT_STEP !in 0..41 || z-4-row*MapleCrossingMap.PLOT_STEP !in 0..41) continue
            val zone=row*MapleCrossingMap.PLOT_COUNT+col
            if(zone==0 || (x-spawn.x)*(x-spawn.x)+(z-spawn.z)*(z-spawn.z)<85*85) continue
            plots[zone].add(n)
        }
    }

    /** Une escouade par parcelle, parcelles tirées au hasard et donc bien séparées. */
    fun chooseSquads(count: Int, size: Int, rng: Random): List<IntArray> =
        SquadSpawn.fromZones(grid, plots.filter { it.isNotEmpty() }, count, size, rng)
}
