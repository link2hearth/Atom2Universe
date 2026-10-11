package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.R

/**
 * Extension points:
 * 1. Add a PlantSpecies to an existing catalogue or append a new catalogue to species below.
 * 2. Register new family/section/collection IDs here; pages and filters are derived from this data.
 * 3. Add FR + EN resources, organ dimensions, traits and references. Variants belong in additional* fields.
 * 4. PlantGroup members are stable species IDs; a species may belong to any number of groups/sections.
 * 5. LeafDetail and TreeArchitecture refine existing shapes (lobes, veins, crown, bark, branch sprays).
 *    Defaults still draw new species. Novel organs extend their enum and the corresponding organ painter.
 * 6. Run compileDebugKotlin and the targeted BotanyCatalogTest/BotanyRenderingTest guards.
 * The source catalogues are authoritative; no generator or network access is needed to extend the atlas.
 */
object BotanyCatalog {
    val collections = listOf(
        PlantCollection("model_plants", R.string.bot_collection_model_plants, R.string.bot_collection_model_plants_desc),
        PlantCollection("north_america", R.string.bot_collection_north_america, R.string.bot_collection_north_america_desc),
        PlantCollection("europe", R.string.bot_collection_europe, R.string.bot_collection_europe_desc),
        PlantCollection("africa_dry", R.string.bot_collection_africa_dry, R.string.bot_collection_africa_dry_desc),
    )
    val sections = listOf(
        PlantSection("trees", R.string.bot_section_trees, R.string.bot_section_trees_desc),
        PlantSection("shrubs", R.string.bot_section_shrubs, R.string.bot_section_shrubs_desc),
        PlantSection("herbs", R.string.bot_section_herbs, R.string.bot_section_herbs_desc),
    )
    val families = listOf(
        PlantFamily("Cupressaceae", R.string.bot_family_Cupressaceae, R.string.bot_family_Cupressaceae_desc, R.string.bot_family_Cupressaceae_aliases),
        PlantFamily("Zygophyllaceae", R.string.bot_family_Zygophyllaceae, R.string.bot_family_Zygophyllaceae_desc, R.string.bot_family_Zygophyllaceae_aliases),
        PlantFamily("Apocynaceae", R.string.bot_family_Apocynaceae, R.string.bot_family_Apocynaceae_desc, R.string.bot_family_Apocynaceae_aliases),
        PlantFamily("Combretaceae", R.string.bot_family_Combretaceae, R.string.bot_family_Combretaceae_desc, R.string.bot_family_Combretaceae_aliases),
        PlantFamily("Pedaliaceae", R.string.bot_family_Pedaliaceae, R.string.bot_family_Pedaliaceae_desc, R.string.bot_family_Pedaliaceae_aliases),
        PlantFamily("Fagaceae", R.string.bot_family_Fagaceae, R.string.bot_family_Fagaceae_desc, R.string.bot_family_Fagaceae_aliases),
        PlantFamily("Betulaceae", R.string.bot_family_Betulaceae, R.string.bot_family_Betulaceae_desc, R.string.bot_family_Betulaceae_aliases),
        PlantFamily("Sapindaceae", R.string.bot_family_Sapindaceae, R.string.bot_family_Sapindaceae_desc, R.string.bot_family_Sapindaceae_aliases),
        PlantFamily("Oleaceae", R.string.bot_family_Oleaceae, R.string.bot_family_Oleaceae_desc, R.string.bot_family_Oleaceae_aliases),
        PlantFamily("Malvaceae", R.string.bot_family_Malvaceae, R.string.bot_family_Malvaceae_desc, R.string.bot_family_Malvaceae_aliases),
        PlantFamily("Rosaceae", R.string.bot_family_Rosaceae, R.string.bot_family_Rosaceae_desc, R.string.bot_family_Rosaceae_aliases),
        PlantFamily("Salicaceae", R.string.bot_family_Salicaceae, R.string.bot_family_Salicaceae_desc, R.string.bot_family_Salicaceae_aliases),
        PlantFamily("Pinaceae", R.string.bot_family_Pinaceae, R.string.bot_family_Pinaceae_desc, R.string.bot_family_Pinaceae_aliases),
        PlantFamily("Viburnaceae", R.string.bot_family_Viburnaceae, R.string.bot_family_Viburnaceae_desc, R.string.bot_family_Viburnaceae_aliases),
        PlantFamily("Cornaceae", R.string.bot_family_Cornaceae, R.string.bot_family_Cornaceae_desc, R.string.bot_family_Cornaceae_aliases),
        PlantFamily("Celastraceae", R.string.bot_family_Celastraceae, R.string.bot_family_Celastraceae_desc, R.string.bot_family_Celastraceae_aliases),
        PlantFamily("Aquifoliaceae", R.string.bot_family_Aquifoliaceae, R.string.bot_family_Aquifoliaceae_desc, R.string.bot_family_Aquifoliaceae_aliases),
        PlantFamily("Buxaceae", R.string.bot_family_Buxaceae, R.string.bot_family_Buxaceae_desc, R.string.bot_family_Buxaceae_aliases),
        PlantFamily("Fabaceae", R.string.bot_family_Fabaceae, R.string.bot_family_Fabaceae_desc, R.string.bot_family_Fabaceae_aliases),
        PlantFamily("Ericaceae", R.string.bot_family_Ericaceae, R.string.bot_family_Ericaceae_desc, R.string.bot_family_Ericaceae_aliases),
        PlantFamily("Asteraceae", R.string.bot_family_Asteraceae, R.string.bot_family_Asteraceae_desc, R.string.bot_family_Asteraceae_aliases),
        PlantFamily("Papaveraceae", R.string.bot_family_Papaveraceae, R.string.bot_family_Papaveraceae_desc, R.string.bot_family_Papaveraceae_aliases),
        PlantFamily("Ranunculaceae", R.string.bot_family_Ranunculaceae, R.string.bot_family_Ranunculaceae_desc, R.string.bot_family_Ranunculaceae_aliases),
        PlantFamily("Primulaceae", R.string.bot_family_Primulaceae, R.string.bot_family_Primulaceae_desc, R.string.bot_family_Primulaceae_aliases),
        PlantFamily("Plantaginaceae", R.string.bot_family_Plantaginaceae, R.string.bot_family_Plantaginaceae_desc, R.string.bot_family_Plantaginaceae_aliases),
        PlantFamily("Campanulaceae", R.string.bot_family_Campanulaceae, R.string.bot_family_Campanulaceae_desc, R.string.bot_family_Campanulaceae_aliases),
        PlantFamily("Violaceae", R.string.bot_family_Violaceae, R.string.bot_family_Violaceae_desc, R.string.bot_family_Violaceae_aliases),
        PlantFamily("Apiaceae", R.string.bot_family_Apiaceae, R.string.bot_family_Apiaceae_desc, R.string.bot_family_Apiaceae_aliases),
        PlantFamily("Urticaceae", R.string.bot_family_Urticaceae, R.string.bot_family_Urticaceae_desc, R.string.bot_family_Urticaceae_aliases),
        PlantFamily("Lamiaceae", R.string.bot_family_Lamiaceae, R.string.bot_family_Lamiaceae_desc, R.string.bot_family_Lamiaceae_aliases),
        PlantFamily("Poaceae", R.string.bot_family_Poaceae, R.string.bot_family_Poaceae_desc, R.string.bot_family_Poaceae_aliases),
        PlantFamily("Ulmaceae", R.string.bot_family_Ulmaceae, R.string.bot_family_Ulmaceae_desc, R.string.bot_family_Ulmaceae_aliases),
        PlantFamily("Rhamnaceae", R.string.bot_family_Rhamnaceae, R.string.bot_family_Rhamnaceae_desc, R.string.bot_family_Rhamnaceae_aliases),
        PlantFamily("Brassicaceae", R.string.bot_family_Brassicaceae, R.string.bot_family_Brassicaceae_desc, R.string.bot_family_Brassicaceae_aliases),
        PlantFamily("Caryophyllaceae", R.string.bot_family_Caryophyllaceae, R.string.bot_family_Caryophyllaceae_desc, R.string.bot_family_Caryophyllaceae_aliases),
        PlantFamily("Geraniaceae", R.string.bot_family_Geraniaceae, R.string.bot_family_Geraniaceae_desc, R.string.bot_family_Geraniaceae_aliases),
    )
    val species = TreesCatalog.species + ShrubsCatalog.species + HerbsCatalog.species + FieldBotanyCatalog.species + AfricanDrylandsCatalog.species + NorthAmericanCatalog.species + ModelPlantsCatalog.species
    private val byId = species.associateBy { it.id }
    fun get(id: String) = byId[id]
    fun family(id: String) = families.first { it.id == id }
    val groups = listOf(
        PlantGroup("model_cereals", R.string.bot_group_model_cereals, listOf("triticum","oryza","zea"), R.string.bot_group_model_cereals_desc),
        PlantGroup("redwoods", R.string.bot_group_redwoods, listOf("sequoiadendron","sequoia","pseudotsuga"), R.string.bot_group_redwoods_desc),
        PlantGroup("prairie_heads", R.string.bot_group_prairie_heads, listOf("echinacea","rudbeckia","helianthus"), R.string.bot_group_prairie_heads_desc),
        PlantGroup("savanna_thorns", R.string.bot_group_savanna_thorns, listOf("vachellia_tortilis","faidherbia","balanites"), R.string.bot_group_savanna_thorns_desc),
        PlantGroup("combretaceae", R.string.bot_group_combretaceae, listOf("guiera","combretum_micranthum"), R.string.bot_group_combretaceae_desc),
        PlantGroup("african_grasses", R.string.bot_group_african_grasses, listOf("cenchrus_ciliaris","hyparrhenia"), R.string.bot_group_african_grasses_desc),
        PlantGroup("oaks", R.string.bot_group_oaks, listOf("robur","petraea"), R.string.bot_group_oaks_desc),
        PlantGroup("beech", R.string.bot_group_beech, listOf("fagus","carpinus"), R.string.bot_group_beech_desc),
        PlantGroup("maples", R.string.bot_group_maples, listOf("campestre","platanoides","pseudoplatanus"), R.string.bot_group_maples_desc),
        PlantGroup("conifers", R.string.bot_group_conifers, listOf("pinus","abies","picea","larix"), R.string.bot_group_conifers_desc),
        PlantGroup("chestnuts", R.string.bot_group_chestnuts, listOf("castanea","aesculus"), R.string.bot_group_chestnuts_desc),
        PlantGroup("pinnate", R.string.bot_group_pinnate, listOf("fraxinus","sorbus","sambucus"), R.string.bot_group_pinnate_desc),
        PlantGroup("thorny", R.string.bot_group_thorny, listOf("crataegus","spinosa","rosa"), R.string.bot_group_thorny_desc),
        PlantGroup("heathers", R.string.bot_group_heathers, listOf("calluna","erica"), R.string.bot_group_heathers_desc),
        PlantGroup("yellow_shrubs", R.string.bot_group_yellow_shrubs, listOf("cytisus","ulex"), R.string.bot_group_yellow_shrubs_desc),
        PlantGroup("daisies", R.string.bot_group_daisies, listOf("bellis","leucanthemum"), R.string.bot_group_daisies_desc),
        PlantGroup("buttercups", R.string.bot_group_buttercups, listOf("ranunculus","ficaria"), R.string.bot_group_buttercups_desc),
        PlantGroup("clovers", R.string.bot_group_clovers, listOf("trifolium_pratense","trifolium_repens"), R.string.bot_group_clovers_desc),
        PlantGroup("umbels", R.string.bot_group_umbels, listOf("daucus","anthriscus"), R.string.bot_group_umbels_desc),
        PlantGroup("plantains", R.string.bot_group_plantains, listOf("plantago_lanceolata","plantago_major"), R.string.bot_group_plantains_desc),
        PlantGroup("nettles", R.string.bot_group_nettles, listOf("urtica","lamium"), R.string.bot_group_nettles_desc),
        PlantGroup("grasses", R.string.bot_group_grasses, listOf("dactylis","lolium","briza"), R.string.bot_group_grasses_desc),
        PlantGroup("dry_oaks", R.string.bot_group_dry_oaks, listOf("quercus_ilex","quercus_pubescens","petraea"), R.string.bot_group_dry_oaks_desc),
        PlantGroup("birches", R.string.bot_group_birches, listOf("betula","betula_pubescens"), R.string.bot_group_birches_desc),
        PlantGroup("lindens", R.string.bot_group_lindens, listOf("tilia","tilia_platyphyllos"), R.string.bot_group_lindens_desc),
        PlantGroup("willows", R.string.bot_group_willows, listOf("salix_alba","salix_caprea"), R.string.bot_group_willows_desc),
        PlantGroup("hawthorns", R.string.bot_group_hawthorns, listOf("crataegus","crataegus_laevigata"), R.string.bot_group_hawthorns_desc),
        PlantGroup("buckthorns", R.string.bot_group_buckthorns, listOf("frangula","rhamnus"), R.string.bot_group_buckthorns_desc),
        PlantGroup("viburnums", R.string.bot_group_viburnums, listOf("viburnum","viburnum_lantana"), R.string.bot_group_viburnums_desc),
        PlantGroup("brassicas", R.string.bot_group_brassicas, listOf("cardamine","capsella"), R.string.bot_group_brassicas_desc),
        PlantGroup("heads", R.string.bot_group_heads, listOf("leucanthemum","matricaria","cichorium"), R.string.bot_group_heads_desc),
    )
    val sources = listOf(
        BotanySource("umich", R.string.bot_source_umich, "https://climbers.lsa.umich.edu/"),
        BotanySource("efloras", R.string.bot_source_efloras, "https://www.efloras.org/"),
        BotanySource("sernec", R.string.bot_source_sernec, "https://sernecportal.org/"),
        BotanySource("ncsu", R.string.bot_source_ncsu, "https://plants.ces.ncsu.edu/"),
        BotanySource("osu", R.string.bot_source_osu, "https://landscapeplants.oregonstate.edu/"),
        BotanySource("usfs", R.string.bot_source_usfs, "https://research.fs.usda.gov/silvics/"),
        BotanySource("sanbi", R.string.bot_source_sanbi, "https://pza.sanbi.org/"),
        BotanySource("icraf", R.string.bot_source_icraf, "https://apps.worldagroforestry.org/treedb2/"),
        BotanySource("fz", R.string.bot_source_fz, "https://www.zimbabweflora.co.zw/"),
        BotanySource("prota", R.string.bot_source_prota, "https://plantuse.plantnet.org/en/PROTA"),
        BotanySource("powo", R.string.bot_source_powo, "https://powo.science.kew.org/"),
        BotanySource("tela", R.string.bot_source_tela, "https://www.tela-botanica.org/ressources/ressources-pedagogiques/"),
        BotanySource("onf", R.string.bot_source_onf, "https://www.onf.fr/"),
        BotanySource("botascopia", R.string.bot_source_botascopia, "https://botascopia.inria.fr/"),
    )
}
