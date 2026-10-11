package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.science.parentes.LifeTree

/** Exact taxon identity only: never link two species merely because they share a common name/genus.
 * The source tree remains authoritative. Its green-plant subtree also includes green algae.
 */
class BotanyLifeIndex(val tree:LifeTree, illustratedNames:Map<String,String>) {
    data class Entry(val nodeId:String,val scientific:String,val lineage:List<String>,val family:String?,val illustratedId:String?)
    val entries:List<Entry>
    private val byNode:Map<String,Entry>
    private val byIllustrated:Map<String,Entry>

    init {
        require(tree.nodes[GREEN_ROOT]?.scientific=="Chloroplastida") { "Green-plant root has changed" }
        val names=illustratedNames.entries.groupBy{it.value}
        entries=tree.descendants(GREEN_ROOT).map { node ->
            val lineage=tree.pathTo(node.id,GREEN_ROOT).drop(1)
            Entry(node.id,node.scientific,lineage,
                lineage.map{tree.nodes.getValue(it).scientific}.firstOrNull{it.endsWith("aceae")},
                names[node.scientific]?.singleOrNull()?.key)
        }
        byNode=entries.associateBy{it.nodeId}
        byIllustrated=entries.filter{it.illustratedId!=null}.associateBy{it.illustratedId!!}
    }
    fun forNode(id:String?)=byNode[id]
    fun forIllustrated(id:String)=byIllustrated[id]
    fun isPlant(id:String)=id in tree.nodes && GREEN_ROOT in tree.ancestors(id)

    companion object { const val GREEN_ROOT="ott361838" }
}
