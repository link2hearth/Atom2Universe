package com.Atom2Universe.app.science.botany

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class BotanyCatalogTest {
    private fun texts(locale:String):Map<String,String> {
        val nodes=DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$locale/strings_botany.xml")).getElementsByTagName("string")
        return (0 until nodes.length).associate {i->nodes.item(i).let{it.attributes.getNamedItem("name").nodeValue to it.textContent}}
    }

    @Test fun catalogueExtensibleEtReferencesCoherentes() {
        val c=BotanyCatalog
        assertTrue(c.species.size>=90)
        assertTrue(c.species.count{"trees" in it.sectionIds}>=30)
        assertTrue(c.species.count{"shrubs" in it.sectionIds}>=25)
        assertTrue(c.species.count{"herbs" in it.sectionIds}>=35)
        assertEquals(c.species.size,c.species.map{it.id}.toSet().size)
        assertEquals(c.families.size,c.families.map{it.id}.toSet().size)
        assertEquals(c.groups.size,c.groups.map{it.id}.toSet().size)
        for(p in c.species) {
            assertTrue(p.id,c.families.any{it.id==p.familyId})
            assertTrue(p.id,p.sectionIds.isNotEmpty() && p.sectionIds.all{id->c.sections.any{it.id==id}})
            assertTrue(p.id,p.sourceIds.isNotEmpty() && p.sourceIds.all{id->c.sources.any{it.id==id}})
            assertTrue(p.id,p.heightMinCm>0 && p.heightMaxCm>=p.heightMinCm)
            assertTrue(p.id,p.flowering.isNotEmpty() && p.fruiting.isNotEmpty())
            assertTrue(p.id,(p.flowering+p.fruiting).all{it in 1..12})
            assertEquals(p.id,PlantView.entries.toSet(),p.captions.keys)
            assertTrue(p.id,p.look.leafCm>0 && p.look.flowerCm>0 && p.look.fruitCm>0)
        }
        c.groups.forEach{g->assertTrue(g.id,g.members.distinct().size>=2);g.members.forEach{assertNotNull(it,c.get(it))}}
        BotanyLessons.all.forEach{assertNotNull(it.id,c.get(it.specimen))}
    }

    @Test fun tousLesTextesTraduitsEtFormatsCompatibles() {
        val en=texts("values");val fr=texts("values-fr")
        val translated=en.keys.filterNot{it.endsWith("_latin") && it!="bot_sort_latin" || it.startsWith("bot_family_") && !it.endsWith("_desc") && !it.endsWith("_aliases")}.toSet()
        assertEquals(translated,fr.keys)
        val formats=Regex("%[0-9]+\\$[dsf]")
        fr.forEach{(key,value)->assertEquals(key,formats.findAll(en.getValue(key)).map{it.value}.toList(),formats.findAll(value).map{it.value}.toList());assertTrue(key,value.isNotBlank())}
        // Catch misspelled resource references before a catalogue expansion ships.
        val used=Regex("R\\.string\\.(bot_[a-zA-Z0-9_]+)")
        File("src/main/java/com/Atom2Universe/app/science/botany").listFiles()!!.forEach{file->
            used.findAll(file.readText()).forEach{match->assertTrue("${file.name}: ${match.groupValues[1]}",match.groupValues[1] in en)}
        }
    }

    @Test fun rechercheSansAccentsEtAvecPlusieursMots() {
        val oak=BotanyCatalog.get("robur")!!
        assertTrue(PlantQuery(text="CHENE pedoncule").matches(oak,"Chêne pédonculé Quercus robur Fagaceae"))
        assertTrue(PlantQuery(text="robur fagaceae").matches(oak,"Chêne pédonculé Quercus robur Fagaceae"))
        assertFalse(PlantQuery(text="chene sessile").matches(oak,"Chêne pédonculé Quercus robur Fagaceae"))
        assertEquals("coeur",PlantQuery.normalize("Cœur"))
    }

    @Test fun choixOuDansUneLigneEtEntreLesLignes() {
        val query=PlantQuery(sections=setOf("trees","shrubs"),arrangements=setOf(LeafArrangement.OPPOSITE),leaves=setOf(LeafShape.PINNATE))
        val ids=BotanyCatalog.species.filter{query.matches(it,"")}.map{it.id}.toSet()
        assertEquals(setOf("fraxinus","sambucus"),ids)
        assertFalse(PlantQuery(months=setOf(12)).matches(BotanyCatalog.get("papaver")!!,""))
        assertTrue(PlantQuery(months=setOf(12)).matches(BotanyCatalog.get("bellis")!!,""))
    }

    @Test fun lesVariationsReellesRestentRetrouvables() {
        val p=BotanyCatalog.get("campanula")!!
        assertTrue(PlantQuery(leaves=setOf(LeafShape.ROUND),arrangements=setOf(LeafArrangement.ROSETTE)).matches(p,""))
        assertTrue(PlantQuery(leaves=setOf(LeafShape.LINEAR)).matches(p,""))
        assertTrue(PlantQuery(margins=setOf(LeafMargin.ENTIRE)).matches(BotanyCatalog.get("ilex")!!,""))
        assertTrue(PlantQuery(evergreen=true).matches(BotanyCatalog.get("ligustrum")!!,""))
        assertTrue(PlantQuery(evergreen=false).matches(BotanyCatalog.get("ligustrum")!!,""))
    }

    @Test fun triNumeriqueEtNonLexicographique() {
        val plants=listOf(BotanyCatalog.get("robur")!!,BotanyCatalog.get("bellis")!!,BotanyCatalog.get("corylus")!!)
        val sorted=PlantQuery.sorted(plants,PlantSort.HEIGHT,Locale.FRENCH,{it.id},{it.id},{it.familyId})
        assertEquals(listOf("bellis","corylus","robur"),sorted.map{it.id})
        val seasonal=PlantQuery.sorted(plants,PlantSort.FLOWERING,Locale.FRENCH,{it.id},{it.id},{it.familyId})
        assertEquals("robur",seasonal.last().id)
    }

    @Test fun uneNouvelleFamilleNeDemandePasDeModifierLeMoteurDeRecherche() {
        val newPlant=BotanyCatalog.species.first().copy(id="future",familyId="FutureFamily",sectionIds=setOf("ferns"))
        assertTrue(PlantQuery(sections=setOf("ferns"),families=setOf("FutureFamily")).matches(newPlant,"Future fern"))
        assertFalse(PlantQuery(sections=setOf("trees")).matches(newPlant,"Future fern"))
    }

    @Test fun leLotDeTerrainExposeSesNouveauxCaracteresAuxFiltres() {
        val brassicas=BotanyCatalog.species.filter{PlantQuery(families=setOf("Brassicaceae")).matches(it,"")}
        assertEquals(setOf("cardamine","capsella"),brassicas.map{it.id}.toSet())
        assertEquals(listOf("capsella"),brassicas.filter{PlantQuery(fruits=setOf(FruitShape.SILICLE)).matches(it,"")}.map{it.id})
        assertTrue(PlantQuery(flowers=setOf(FlowerShape.LIGULATE),colors=setOf(FlowerColor.BLUE)).matches(BotanyCatalog.get("cichorium")!!,""))
        assertTrue(PlantQuery(fruits=setOf(FruitShape.BERRY),evergreen=false).matches(BotanyCatalog.get("vaccinium")!!,""))
        assertTrue(PlantQuery(margins=setOf(LeafMargin.ENTIRE),evergreen=true).matches(BotanyCatalog.get("quercus_ilex")!!,""))
        assertTrue(PlantQuery(arrangements=setOf(LeafArrangement.ROSETTE),leaves=setOf(LeafShape.PINNATE)).matches(BotanyCatalog.get("cardamine")!!,""))
        val fr=texts("values-fr")
        val searchable=fr.getValue("bot_stellaria_name")+" "+fr.getValue("bot_stellaria_aliases")
        assertTrue(PlantQuery(text="Stellaria holostea").matches(BotanyCatalog.get("stellaria")!!,searchable))
    }
}
