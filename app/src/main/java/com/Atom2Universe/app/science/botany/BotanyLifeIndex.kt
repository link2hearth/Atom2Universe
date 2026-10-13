package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.science.parentes.LifeTree

/** Exact taxon identity or explicitly reviewed homotypic synonym only; never common name/genus.
 * The source tree remains authoritative. Its green-plant subtree also includes green algae.
 */
class BotanyLifeIndex(val tree:LifeTree, illustratedNames:Map<String,String>) {
    data class Entry(val nodeId:String,val scientific:String,val lineage:List<String>,val family:String?,val illustratedId:String?)
    val entries:List<Entry>
    private val byNode:Map<String,Entry>
    private val byIllustrated:Map<String,Entry>

    init {
        require(tree.nodes[GREEN_ROOT]?.scientific=="Chloroplastida") { "Green-plant root has changed" }
        val names=illustratedNames.entries.groupBy{treeScientificName(it.value)}
        entries=tree.descendants(GREEN_ROOT).map { node ->
            val lineage=tree.pathTo(node.id,GREEN_ROOT).drop(1)
            Entry(node.id,node.scientific,lineage,
                lineage.map{tree.nodes.getValue(it).scientific}.firstOrNull{it.endsWith("aceae")},
                names[treeScientificName(node.scientific)]?.singleOrNull()?.key)
        }
        byNode=entries.associateBy{it.nodeId}
        byIllustrated=entries.filter{it.illustratedId!=null}.associateBy{it.illustratedId!!}
    }
    fun forNode(id:String?)=byNode[id]
    fun forIllustrated(id:String)=byIllustrated[id]
    fun isPlant(id:String)=id in tree.nodes && GREEN_ROOT in tree.ancestors(id)

    companion object {
        const val GREEN_ROOT="ott361838"
        // Kew POWO: reviewed accepted-name matches for retained source-tree terminal names.
        // Rabelera holostea is a synonym of Stellaria holostea (OTT 3.7draft3).
        // https://powo.science.kew.org/taxon/urn:lsid:ipni.org:names:307758-2
        // Sansevieria kirkii is a synonym of Dracaena pethera (accepted by POWO).
        // https://powo.science.kew.org/taxon/urn:lsid:ipni.org:names:77182814-1/general-information
        // Opuntia helleri is a synonym of the accepted Opuntia galapageia var. helleri.
        // https://powo.science.kew.org/taxon/urn:lsid:ipni.org:names:175098-2
        internal fun treeScientificName(name:String):String = when(name) {
            "Rabelera holostea" -> "Stellaria holostea"
            "Dracaena pethera" -> "Sansevieria kirkii"
            "Opuntia helleri" -> "Opuntia galapageia var. helleri"
            else -> name
        }
    }
}
