package com.Atom2Universe.app.science.botany

import android.content.Context
import android.content.res.Configuration
import com.Atom2Universe.app.science.parentes.ParentesRepository
import java.util.Locale

/** Built off the UI thread from the same offline archive and localized notices as the living tree. */
class BotanyLifeCatalogue private constructor(val repository:ParentesRepository,val index:BotanyLifeIndex,val searchTerms:Map<String,String>) {
    companion object {
        fun load(context:Context):BotanyLifeCatalogue {
            val app=context.applicationContext
            val repo=ParentesRepository.load(app)
            val index=BotanyLifeIndex(repo.tree,BotanyCatalog.species.associate{it.id to app.getString(it.latin)})
            val locales=listOf("fr","en").map{language->app.createConfigurationContext(Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))})}
            val terms=index.entries.associate { entry ->
                val illustrated=entry.illustratedId?.let{BotanyCatalog.get(it)}
                val names=locales.flatMap { c -> listOf(repo.name(c,entry.nodeId)) +
                    (illustrated?.let{listOf(c.getString(it.name),c.getString(it.aliases))}.orEmpty()) +
                    entry.lineage.filter{it in repo.groups}.map{repo.name(c,it)} }
                entry.nodeId to PlantQuery.normalize((names+entry.scientific+entry.family.orEmpty()+
                    (repo.labels[entry.nodeId]?.aliases ?: repo.notes[entry.nodeId]?.aliases).orEmpty()).joinToString(" "))
            }
            return BotanyLifeCatalogue(repo,index,terms)
        }
    }
}
