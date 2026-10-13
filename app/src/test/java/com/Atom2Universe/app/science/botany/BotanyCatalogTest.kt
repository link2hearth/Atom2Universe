package com.Atom2Universe.app.science.botany

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class BotanyCatalogTest {
    @Test fun familleAbsenteNeFaitPasPlanterLeModule() {
        assertEquals("unexpected_family",BotanyCatalog.family("unexpected_family").id)
        assertEquals(com.Atom2Universe.app.R.string.bot_family_unknown,BotanyCatalog.family("unexpected_family").name)
    }

    @Test fun deuxLotsPrairiesHaiesSources() {
        assertEquals(10,MeadowFlowersCatalog.species.size)
        assertEquals(10,HedgerowDiversityCatalog.species.size)
        for(p in MeadowFlowersCatalog.species+HedgerowDiversityCatalog.species) {
            assertNotNull(p.id,BotanyCatalog.get(p.id))
            assertTrue(p.id,p.id in BotanyReferences.taxonPages)
            assertTrue(p.id,p.look.study!!.meadow)
            assertEquals(PlantView.entries.toSet(),p.captions.keys)
        }
        assertEquals(0,BotanyCatalog.get("silene_latifolia")!!.look.flowerDetail.stamens)
        assertEquals(10,BotanyCatalog.get("silene_latifolia")!!.look.fruitDetail.capsuleTeeth)
        assertEquals(FruitShape.POD,BotanyCatalog.get("medicago_minima")!!.look.fruit)
        assertEquals(14,BotanyCatalog.get("filipendula_vulgaris")!!.look.fruitDetail.aggregateCount)
    }

    @Test fun nouveauxLotsEtFruitsDistincts() {
        assertEquals(10,MonocotFormsCatalog.species.size)
        assertEquals(10,WoodyFruitsCatalog.species.size)
        for(p in MonocotFormsCatalog.species+WoodyFruitsCatalog.species) {
            assertNotNull(p.id,BotanyCatalog.get(p.id))
            assertTrue(p.id,p.id in BotanyReferences.taxonPages)
            assertTrue(p.id,p.flowering.isEmpty() && p.fruiting.isEmpty() && p.phenologyNote!=0)
        }
        val pineapple=BotanyCatalog.get("ananas_comosus")!!
        val fig=BotanyCatalog.get("ficus_carica")!!
        val juniper=BotanyCatalog.get("juniperus_communis")!!
        assertTrue(PlantQuery(fruits=setOf(FruitShape.MULTIPLE)).matches(pineapple,""))
        assertTrue(PlantQuery(fruits=setOf(FruitShape.SYCONIUM)).matches(fig,""))
        assertTrue(PlantQuery(fruits=setOf(FruitShape.CONE)).matches(juniper,""))
        assertFalse(PlantQuery(fruits=setOf(FruitShape.BERRY)).matches(juniper,""))
        assertEquals(8,BotanyCatalog.get("vaccinium_vitis_idaea")!!.look.flowerDetail.stamens)
        assertEquals(4,BotanyCatalog.get("vaccinium_vitis_idaea")!!.look.petals)
        assertEquals(StudyForm.GINGER_RHIZOME,BotanyCatalog.get("zingiber_officinale")!!.look.study)
    }
    @Test fun deuxLotsCulinairesEtCaracteresDiagnostiques() {
        assertEquals(10,AromaticsCatalog.species.size)
        assertEquals(10,CropDiversityCatalog.species.size)
        for(p in AromaticsCatalog.species+CropDiversityCatalog.species) {
            assertNotNull(p.id,BotanyCatalog.get(p.id))
            assertTrue(p.id,p.id in BotanyReferences.taxonPages)
            assertTrue(p.id,p.flowering.isEmpty() && p.fruiting.isEmpty() && p.phenologyNote!=0)
        }
        assertEquals(StudyForm.TUFT_ALLIUM,BotanyCatalog.get("allium_fistulosum")!!.look.study)
        assertEquals(StudyForm.BROAD_ALLIUM,BotanyCatalog.get("allium_ursinum")!!.look.study)
        assertEquals(StudyForm.OAT_PANICLE,BotanyCatalog.get("avena_sativa")!!.look.study)
        assertTrue(PlantQuery(fruits=setOf(FruitShape.INDEHISCENT)).matches(BotanyCatalog.get("raphanus_raphanistrum")!!,""))
        assertEquals(LeafArrangement.OPPOSITE,BotanyCatalog.get("thymus_vulgaris")!!.look.arrangement)
        assertFalse(BotanyCatalog.get("cicer_arietinum")!!.look.leafDetail.terminalTendril)
        // A supporting wing-like bract must not classify the hornbeam's nutlet as a samara.
        val hornbeam=BotanyCatalog.get("carpinus")!!
        assertTrue(PlantQuery(fruits=setOf(FruitShape.ACHENE)).matches(hornbeam,""))
        assertFalse(PlantQuery(fruits=setOf(FruitShape.SAMARA)).matches(hornbeam,""))
        // Prevent generic many-stamen flowers from silently replacing these diagnostic counts.
        for(p in AromaticsCatalog.species+CropDiversityCatalog.species) {
            val count=mapOf("Amaryllidaceae" to 6,"Apiaceae" to 5,"Lamiaceae" to 4,
                "Poaceae" to 3,"Fabaceae" to 10,"Cucurbitaceae" to 3,"Brassicaceae" to 6).getValue(p.familyId)
            assertEquals(p.id,count,p.look.flowerDetail.stamens)
            if(p.familyId=="Brassicaceae")assertEquals(p.id,2,p.look.flowerDetail.shortStamens)
        }
    }
    private fun texts(locale:String):Map<String,String> {
        val nodes=DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$locale/strings_botany.xml")).getElementsByTagName("string")
        return (0 until nodes.length).associate {i->nodes.item(i).let{it.attributes.getNamedItem("name").nodeValue to it.textContent}}
    }

    @Test fun catalogueExtensibleEtReferencesCoherentes() {
        val c=BotanyCatalog
        assertTrue(c.species.size>=120)
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
            assertTrue(p.id,(p.flowering.isNotEmpty() && p.fruiting.isNotEmpty()) || p.phenologyNote!=0)
            assertTrue(p.id,p.collectionIds.isNotEmpty() && p.collectionIds.all{id->c.collections.any{it.id==id}})
            assertTrue(p.id,(p.flowering+p.fruiting).all{it in 1..12})
            assertEquals(p.id,PlantView.entries.toSet(),p.captions.keys)
            assertTrue(p.id,p.look.leafCm>0 && p.look.flowerCm>0 && p.look.fruitCm>0)
        }
        c.groups.forEach{g->assertTrue(g.id,g.members.distinct().size>=2);g.members.forEach{assertNotNull(it,c.get(it))}}
        BotanyLessons.all.forEach{assertNotNull(it.id,c.get(it.specimen))}
    }

    @Test fun tousLesTextesTraduitsEtFormatsCompatibles() {
        val en=texts("values");val fr=texts("values-fr")
        val translated=en.keys.filterNot{it.endsWith("_latin") && it !in setOf("bot_sort_latin","bot_life_sort_latin") || it.startsWith("bot_family_") && !it.endsWith("_desc") && !it.endsWith("_aliases")}.toSet()
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

    @Test fun collectionsCumulablesEtSaisonsLocalesSansFauxCalendrier() {
        val african=BotanyCatalog.species.filter{PlantQuery(collections=setOf("africa_dry")).matches(it,"")}
        assertEquals(15,african.size)
        for(section in listOf("trees","shrubs","herbs"))assertEquals(5,african.count{section in it.sectionIds})
        assertTrue(african.all{it.phenologyNote!=0 && it.flowering.isEmpty() && it.fruiting.isEmpty()})
        assertTrue(african.all{it.id in BotanyReferences.taxonPages})
        assertEquals(105,BotanyCatalog.species.count{PlantQuery(collections=setOf("africa_dry","europe")).matches(it,"")})
        val acacia=BotanyCatalog.get("vachellia_tortilis")!!
        assertTrue(PlantQuery(collections=setOf("africa_dry"),leaves=setOf(LeafShape.BIPINNATE),sections=setOf("trees")).matches(acacia,""))
        assertFalse(PlantQuery(collections=setOf("europe")).matches(acacia,""))
        assertFalse(PlantQuery(months=setOf(1)).matches(acacia,""))
        val shared=acacia.copy(collectionIds=setOf("africa_dry","future_collection"))
        assertTrue(PlantQuery(collections=setOf("future_collection")).matches(shared,""))
        val plants=listOf(acacia,BotanyCatalog.get("bellis")!!)
        assertEquals(acacia,PlantQuery.sorted(plants,PlantSort.FLOWERING,Locale.FRENCH,{it.id},{it.id},{it.familyId}).last())
    }

    @Test fun leLotDeTerrainExposeSesNouveauxCaracteresAuxFiltres() {
        val brassicas=BotanyCatalog.species.filter{PlantQuery(families=setOf("Brassicaceae")).matches(it,"")}
        assertEquals(setOf("cardamine","capsella","arabidopsis","brassica_oleracea","brassica_rapa","sinapis_alba","raphanus_raphanistrum","nasturtium_officinale","armoracia_rusticana","barbarea_vulgaris"),brassicas.map{it.id}.toSet())
        assertEquals(listOf("capsella","armoracia_rusticana"),brassicas.filter{PlantQuery(fruits=setOf(FruitShape.SILICLE)).matches(it,"")}.map{it.id})
        assertTrue(PlantQuery(flowers=setOf(FlowerShape.LIGULATE),colors=setOf(FlowerColor.BLUE)).matches(BotanyCatalog.get("cichorium")!!,""))
        assertTrue(PlantQuery(fruits=setOf(FruitShape.BERRY),evergreen=false).matches(BotanyCatalog.get("vaccinium")!!,""))
        assertTrue(PlantQuery(margins=setOf(LeafMargin.ENTIRE),evergreen=true).matches(BotanyCatalog.get("quercus_ilex")!!,""))
        assertTrue(PlantQuery(arrangements=setOf(LeafArrangement.ROSETTE),leaves=setOf(LeafShape.PINNATE)).matches(BotanyCatalog.get("cardamine")!!,""))
        val fr=texts("values-fr")
        val searchable=fr.getValue("bot_stellaria_name")+" "+fr.getValue("bot_stellaria_aliases")
        assertTrue(PlantQuery(text="Stellaria holostea").matches(BotanyCatalog.get("stellaria")!!,searchable))
    }
    @Test fun premierLotDeReferencesEtFiltresBotaniques() {
        val plants=BotanyCatalog.species.filter{PlantQuery(collections=setOf("model_plants")).matches(it,"")}
        assertEquals(setOf("arabidopsis","pisum","triticum","oryza","zea"),plants.map{it.id}.toSet())
        assertTrue(plants.all{it.look.study!=null && it.id in BotanyReferences.taxonPages})
        val crops=plants.filter{PlantQuery(habitats=setOf(Habitat.CULTIVATED)).matches(it,"")}
        assertEquals(4,crops.size)
        assertTrue(crops.all{it.flowering.isEmpty() && it.fruiting.isEmpty() && it.phenologyNote!=0})
        assertTrue(PlantQuery(fruits=setOf(FruitShape.SILIQUE),arrangements=setOf(LeafArrangement.ROSETTE)).matches(BotanyCatalog.get("arabidopsis")!!,""))
        assertTrue(PlantQuery(colors=setOf(FlowerColor.PURPLE),leaves=setOf(LeafShape.PINNATE)).matches(BotanyCatalog.get("pisum")!!,""))
        assertEquals(3,plants.count{PlantQuery(fruits=setOf(FruitShape.GRAIN)).matches(it,"")})
    }

    @Test fun lotNordAmericainEtCaracteresRetrouvables() {
        val plants=BotanyCatalog.species.filter{PlantQuery(collections=setOf("north_america")).matches(it,"")}
        assertEquals(15,plants.size)
        for(section in listOf("trees","shrubs","herbs"))assertEquals(5,plants.count{section in it.sectionIds})
        assertTrue(plants.all{it.phenologyNote!=0 && it.id in BotanyReferences.taxonPages})
        val conifers=plants.filter{PlantQuery(families=setOf("Cupressaceae"),evergreen=true).matches(it,"")}
        assertEquals(setOf("sequoiadendron","sequoia"),conifers.map{it.id}.toSet())
        assertTrue(PlantQuery(leaves=setOf(LeafShape.SCALE)).matches(BotanyCatalog.get("sequoia")!!,""))
        assertTrue(PlantQuery(colors=setOf(FlowerColor.ORANGE),fruits=setOf(FruitShape.CAPSULE)).matches(BotanyCatalog.get("eschscholzia")!!,""))
        assertTrue(PlantQuery(arrangements=setOf(LeafArrangement.OPPOSITE)).matches(BotanyCatalog.get("helianthus")!!,""))
        assertFalse(PlantQuery(collections=setOf("africa_dry")).matches(BotanyCatalog.get("sequoiadendron")!!,""))
        val fr=texts("values-fr")
        assertTrue(PlantQuery(text="sequoia geant").matches(BotanyCatalog.get("sequoiadendron")!!,fr.getValue("bot_sequoiadendron_name")))
    }

    @Test fun deuxiemeLotDeReferencesEtNouvelleFamille() {
        val plants=BotanyCatalog.species.filter{PlantQuery(collections=setOf("nightshades")).matches(it,"")}
        assertEquals(setOf("lycopersicum","tuberosum","melongena","capsicum","nigrum"),plants.map{it.id}.toSet())
        assertTrue(plants.all{it.familyId=="Solanaceae" && it.look.study?.nightshade==true && it.id in BotanyReferences.taxonPages})
        assertEquals(plants.toSet(),BotanyCatalog.species.filter{PlantQuery(families=setOf("Solanaceae")).matches(it,"")}.toSet())
        // The potato's botanical fruit is a berry, not its underground stem tuber.
        assertTrue(plants.all{PlantQuery(fruits=setOf(FruitShape.BERRY)).matches(it,"")})
        assertEquals(setOf("lycopersicum","tuberosum"),plants.filter{PlantQuery(leaves=setOf(LeafShape.PINNATE)).matches(it,"")}.map{it.id}.toSet())
        assertTrue(PlantQuery(margins=setOf(LeafMargin.ENTIRE)).matches(BotanyCatalog.get("nigrum")!!,""))
        assertTrue(plants.filter{it.id!="nigrum"}.all{it.flowering.isEmpty() && it.phenologyNote!=0})
    }

    @Test fun fruitiersConnusEtCaracteresDiagnostiques() {
        val plants=BotanyCatalog.species.filter{PlantQuery(collections=setOf("orchard")).matches(it,"")}
        assertEquals(setOf("olea","coffea","persica","armeniaca","citrus_maxima"),plants.map{it.id}.toSet())
        assertTrue(plants.all{it.look.study?.orchard==true && it.id in BotanyReferences.taxonPages})
        assertTrue(plants.all{it.flowering.isEmpty() && it.fruiting.isEmpty() && it.phenologyNote!=0})
        assertEquals(setOf("olea","coffea"),plants.filter{PlantQuery(arrangements=setOf(LeafArrangement.OPPOSITE)).matches(it,"")}.map{it.id}.toSet())
        assertEquals(setOf("persica","armeniaca"),plants.filter{PlantQuery(evergreen=false).matches(it,"")}.map{it.id}.toSet())
        assertTrue(PlantQuery(families=setOf("Rutaceae"),fruits=setOf(FruitShape.BERRY)).matches(BotanyCatalog.get("citrus_maxima")!!,""))
        assertTrue(PlantQuery(families=setOf("Rubiaceae"),fruits=setOf(FruitShape.DRUPE)).matches(BotanyCatalog.get("coffea")!!,""))
    }

    @Test fun grandesCulturesEtOrganesConsommesDistinctsDesFruits() {
        val plants=BotanyCatalog.species.filter{PlantQuery(collections=setOf("food_crops")).matches(it,"")}
        assertEquals(setOf("allium_cepa","brassica_oleracea","cucumis_sativus","glycine_max","arachis"),plants.map{it.id}.toSet())
        assertTrue(plants.all{it.look.study?.foodCrop==true && it.id in BotanyReferences.taxonPages})
        assertTrue(plants.all{it.flowering.isEmpty() && it.fruiting.isEmpty() && it.phenologyNote!=0})
        assertTrue(PlantQuery(families=setOf("Amaryllidaceae"),fruits=setOf(FruitShape.CAPSULE)).matches(BotanyCatalog.get("allium_cepa")!!,""))
        assertTrue(PlantQuery(families=setOf("Brassicaceae"),fruits=setOf(FruitShape.SILIQUE)).matches(BotanyCatalog.get("brassica_oleracea")!!,""))
        assertEquals(setOf("glycine_max","arachis"),plants.filter{PlantQuery(fruits=setOf(FruitShape.POD)).matches(it,"")}.map{it.id}.toSet())
        assertTrue(PlantQuery(families=setOf("Cucurbitaceae"),fruits=setOf(FruitShape.BERRY)).matches(BotanyCatalog.get("cucumis_sativus")!!,""))
        assertEquals(4,BotanyCatalog.get("arachis")!!.look.leaflets)
        assertTrue(PlantQuery(leaves=setOf(LeafShape.TRIFOLIATE),colors=setOf(FlowerColor.WHITE)).matches(BotanyCatalog.get("glycine_max")!!,""))
    }

    @Test fun plantesDuQuotidienEtNouveauxCaracteres() {
        val plants=BotanyCatalog.species.filter{PlantQuery(collections=setOf("useful_plants")).matches(it,"")}
        assertEquals(setOf("camellia_sinensis","gossypium_hirsutum","punica_granatum","persea_americana","laurus_nobilis"),plants.map{it.id}.toSet())
        assertTrue(plants.all{it.look.study?.usefulPlant==true && it.id in BotanyReferences.taxonPages})
        assertTrue(plants.all{it.flowering.isEmpty() && it.fruiting.isEmpty() && it.phenologyNote!=0})
        assertEquals(setOf("persea_americana","laurus_nobilis"),plants.filter{PlantQuery(families=setOf("Lauraceae")).matches(it,"")}.map{it.id}.toSet())
        // The avocado contains a seed, not a stony endocarp; cotton is seed hair within a capsule.
        assertTrue(PlantQuery(fruits=setOf(FruitShape.BERRY)).matches(BotanyCatalog.get("persea_americana")!!,""))
        assertTrue(PlantQuery(fruits=setOf(FruitShape.CAPSULE),leaves=setOf(LeafShape.PALMATILOBED)).matches(BotanyCatalog.get("gossypium_hirsutum")!!,""))
        assertTrue(PlantQuery(arrangements=setOf(LeafArrangement.WHORLED)).matches(BotanyCatalog.get("punica_granatum")!!,""))
        assertTrue(PlantQuery(families=setOf("Theaceae"),margins=setOf(LeafMargin.SERRATE)).matches(BotanyCatalog.get("camellia_sinensis")!!,""))
        assertTrue(PlantQuery(fruits=setOf(FruitShape.DRUPE),evergreen=true).matches(BotanyCatalog.get("laurus_nobilis")!!,""))
    }

}
