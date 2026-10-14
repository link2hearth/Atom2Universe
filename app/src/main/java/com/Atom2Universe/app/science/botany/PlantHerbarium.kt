package com.Atom2Universe.app.science.botany

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.Atom2Universe.app.A2UApplication

/** Text-free complete plates. Organ views retain their independent vector studies. */
internal object PlantHerbarium {
    val speciesIds = setOf("robur", "bellis", "lolium", "castanea", "urtica", "digitalis",
        "allium_ursinum", "convallaria_majalis", "daucus", "laurus_nobilis",
        "papaver", "ranunculus", "aesculus", "sambucus", "camellia_sinensis",
        "persea_americana", "punica_granatum", "aloe_vera", "ananas_comosus", "musa_acuminata",
        "allium_sativum", "anethum_graveolens", "cicer_arietinum", "cucumis_melo", "cocos_nucifera",
        "phoenix_dactylifera", "olea", "juglans_regia", "pinus_pinea", "zingiber_officinale",
        "coffea", "ficus_carica", "citrus_maxima", "persica", "armeniaca",
        "lycopersicum", "tuberosum", "capsicum", "cucumis_sativus", "arachis",
        "allium_cepa", "brassica_oleracea", "glycine_max", "gossypium_hirsutum", "pyrus_communis",
        "vaccinium_vitis_idaea", "juniperus_communis", "corylus_colurna", "thymus_vulgaris", "areca_catechu",
        "allium_ampeloprasum", "brassica_rapa", "nasturtium_officinale", "armoracia_rusticana", "avena_sativa",
        "carum_carvi", "digitalis_grandiflora", "tussilago_farfara", "bowiea_volubilis", "mespilus_germanica",
        "clivia_miniata", "hordeum_vulgare", "raphanus_raphanistrum", "rosa_multiflora", "salix_repens",
        "ajuga_reptans", "antirrhinum_majus", "centaurea_cyanus", "trigonella_foenum_graecum", "petroselinum_crispum",
        "allium_fistulosum", "secale_cereale", "sinapis_alba", "levisticum_officinale", "ribes_alpinum",
        "origanum_majorana", "pulsatilla_pratensis", "linaria_vulgaris", "saponaria_officinalis", "anemone_sylvestris",
        "agrimonia_eupatoria", "barbarea_vulgaris", "filipendula_vulgaris", "lathyrus_japonicus", "medicago_minima",
        "silene_latifolia", "sorbus_intermedia", "vicia_sativa", "vicia_sepium", "stellaria",
        "acer_monspessulanum", "prunus_padus", "cichorium", "matricaria", "capsella", "silene_dioica",
        "cornus_mas", "viburnum_lantana", "cardamine", "genista",
        "adansonia", "vachellia_tortilis", "tamarindus", "balanites", "calotropis",
        "senna_italica", "guiera", "cenchrus_ciliaris", "sesamum_alatum", "hibiscus_trionum",
        "faidherbia", "combretum_micranthum", "ziziphus_mauritiana", "hyparrhenia", "indigofera_spicata",
        "quercus_rubra", "arctostaphylos", "rhododendron_catawbiense", "echinacea", "helianthus",
        "quercus_ilex", "betula_pubescens", "populus_nigra", "malus_sylvestris", "crataegus_laevigata",
        "frangula", "rhamnus", "geranium", "malva", "potentilla",
        "achillea", "briza", "campanula", "ficaria", "corylus", "ilex", "ulex", "euonymus", "prunus_avium", "ulmus_minor",
        "quercus_pubescens", "salix_alba", "amelanchier", "vaccinium", "prunus_mahaleb", "veronica", "plantago_lanceolata", "leucanthemum", "calluna", "pinus",
        "tilia_platyphyllos", "salix_caprea", "centaurea", "primula", "trifolium_pratense", "anthriscus", "plantago_major", "lamium", "fagus", "larix",
        "viola", "trifolium_repens", "lotus", "salvia", "crataegus", "spinosa", "viburnum", "ligustrum", "petraea", "abies",
        "dactylis", "cornus", "cytisus", "erica", "betula", "alnus", "campestre", "sorbus", "picea", "rosa",
        "arabidopsis", "sequoiadendron", "sequoia", "acer_saccharum", "vaccinium_corymbosum",
        "cornus_sericea", "eschscholzia", "lupinus_polyphyllus", "carpinus", "rudbeckia",
        "pseudotsuga", "rosa_virginiana", "pisum",
        "vicia", "buxus", "platanoides", "pseudoplatanus", "fraxinus", "tilia", "populus",
        "oryza", "zea", "triticum", "melongena", "nigrum",
        "cicuta_virosa", "ipomoea_batatas", "chenopodium_quinoa", "coffea_canephora", "coffea_liberica", "illicium_anisatum",
        "ginkgo_biloba", "pteridium_aquilinum", "cannabis_sativa", "carica_papaya", "anacardium_occidentale", "arum_italicum",
        "abelmoschus_esculentus", "acer_japonicum", "actinidia_kolomikta", "agave_deserti", "aloe_polyphylla", "arctium_lappa",
        "alcea_rosea", "alisma_plantago_aquatica", "artemisia_absinthium", "artemisia_vulgaris", "chelidonium_majus", "cymbopogon_citratus",
        "abies_nordmanniana", "agave_victoriae_reginae", "artocarpus_altilis", "betula_papyrifera", "capsicum_pubescens", "aeonium_ciliatum",
        "arctium_minus", "agave_filifera", "armeria_maritima", "bertholletia_excelsa", "cirsium_vulgare", "drosera_rotundifolia",
        "acoelorraphe_wrightii", "adoxa_moschatellina", "adansonia_perrieri", "afzelia_africana", "agathis_robusta", "alstonia_scholaris",
        "abies_yuanbaoshanensis", "ammi_majus", "cuminum_cyminum", "anemarrhena_asphodeloides", "ansellia_africana", "arbutus_canariensis",
        "agave_fourcroydes", "agave_shawii", "hedera_helix", "agrostis_gigantea", "adansonia_suarezensis", "anthemis_arvensis",
        "anchusa_arvensis", "artemisia_maritima", "asarum_europaeum", "astrophytum_caput_medusae", "brocchinia_reducta", "cestrum_nocturnum",
        "astrophytum_ornatum", "calocedrus_macrolepis", "centaurium_erythraea", "cereus_jamacaru", "cyclamen_coum",
        "cunninghamia_lanceolata", "dillenia_indica", "dieffenbachia_seguine", "dracaena_cinnabari", "euterpe_oleracea",
        "euphorbia_cyparissias", "euphorbia_hirta", "eryngium_maritimum", "echinocactus_horizonthalonius", "echinopsis_pachanoi",
        "fagopyrum_esculentum", "fagus_grandifolia", "galium_verum", "picea_glauca", "nuphar_lutea",
        "dracaena_fragrans", "lemna_minor", "zostera_marina", "iris_pseudacorus", "yucca_pallida",
        "dracaena_reflexa", "musa_balbisiana", "phoenix_canariensis", "pinus_strobus", "pinus_edulis",
        "picea_abies", "larix_decidua", "pseudotsuga_menziesii", "taxodium_distichum", "dendrocalamus_giganteus",
        "puya_raimondii", "puya_chilensis", "welwitschia_mirabilis", "equisetum_palustre", "nepenthes_rajah",
        "pinus_palustris", "erica_cinerea", "scheuchzeria_palustris", "musa_basjoo", "lilium_columbianum",
        "lilium_cernuum", "erythronium_pluriflorum", "erythronium_citrinum", "vanda_coerulea", "herminium_monorchis",
        "epipactis_atrorubens", "neuwiedia_griffithii", "narcissus_poeticus", "sansevieria_kirkii", "dasylirion_acrotrichum",
        "liriope_muscari", "puya_alpestris", "roystonea_oleracea", "howea_forsteriana", "osmunda_japonica",
        "metasequoia_glyptostroboides", "humulus_lupulus", "passiflora_edulis", "litchi_chinensis", "helianthus_tuberosus",
        "drosera_capillaris", "nepenthes_inermis", "hibiscus_fragilis", "pachypodium_rosulatum", "mitragyna_speciosa",
        "narcissus_serotinus", "mammillaria_bocasana", "hippuris_vulgaris", "cyperus_alternifolius", "kalmia_microphylla",
        "nothotsuga_longibracteata", "lophira_alata", "nerine_sarniensis", "opuntia_stricta", "passiflora_incarnata",
        "aristotelia_serrata", "aztekium_ritteri", "bauhinia_racemosa", "bidens_cernua", "briza_maxima",
        "chamaecyparis_formosensis", "combretum_paniculatum", "cylindropuntia_bigelovii", "cylindropuntia_leptocaulis", "dianthus_gratianopolitanus",
        "dipterocarpus_alatus", "dracaena_braunii", "enterolobium_cyclocarpum", "euphorbia_abdelkuri", "fagopyrum_tataricum",
        "juncus_maritimus", "kalmia_cuneata", "marchantia_polymorpha", "echinocereus_dasyacanthus", "hibiscus_insularis",
        "austrotaxus_spicata", "carex_curvula", "desfontainia_spinosa", "diselma_archeri", "euphorbia_dulcis",
        "heliamphora_nutans", "hylocomium_splendens", "iberis_amara", "ipomoea_alba", "knautia_arvensis",
        "leucaena_leucocephala", "lepidium_latifolium", "lepidium_virginicum", "lycium_chinense", "mammillaria_bombycina",
        "mirabilis_jalapa", "nesocodon_mauritianus", "notobasis_syriaca", "oenothera_biennis", "orthotrichum_anomalum",
        "acaulon_muticum", "aspidosperma_quebracho_blanco", "banisteriopsis_caapi", "brachycereus_nesioticus", "buxbaumia_viridis",
        "coddia_rudis", "crinodendron_hookerianum", "disocactus_ackermannii", "eleiodoxa_conferta", "escontria_chiotilla",
        "helichrysum_arenarium", "limonium_vulgare", "opuntia_helleri", "pachypodium_baronii", "passiflora_tarminiana",
        "pistacia_vera", "rheum_rhabarbarum", "salicornia_europaea", "ruta_graveolens", "utricularia_humboldtii",
        "satureja_hortensis", "rumex_acetosa", "spinacia_oleracea", "sphagnum_capillifolium", "fragaria_iturupensis",
        "arenga_wightii", "androlepis_skinneri", "syzygium_aromaticum", "sedum_acre", "tectona_grandis",
        "volvox_carteri", "chlamydomonas_reinhardtii", "aegagropila_linnaei", "hylocereus_costaricensis", "ariocarpus_scaphirostrus",
        "echinopsis_macrogona", "scilla_morrisii", "heliamphora_chimantensis", "sphagnum_fuscum", "sphagnum_cuspidatum", "anthemis_glaberrima", "peganum_harmala", "plantago_maritima", "plantago_media", "persicaria_capitata", "prunus_maximowiczii", "psychotria_viridis", "puccinellia_maritima", "rhinanthus_minor", "rhus_typhina", "rosa_moschata", "salvadora_persica", "tanacetum_vulgare", "urtica_urens", "vaccinium_ovalifolium", "syzygium_jambos", "streblus_asper", "hyophorbe_amaricaulis", "rhipsalis_pilocarpa", "haraella_retrocalla", "takakia_ceratophylla", "polytrichum_piliferum", "riccia_fluitans", "rhytidiadelphus_squarrosus", "papuacedrus_papuana", "pilgerodendron_uviferum", "neocallitropsis_pancheri", "pseudotaxus_chienii", "roystonea_borinquena", "iriartea_deltoidea", "ammandra_decasperma", "primula_stricta", "rohdea_japonica", "heliamphora_heterodoxa", "woollsia_pungens", "teucrium_fruticans", "vestia_foetida", "tabernanthe_iboga", "lycium_europaeum", "hedypnois_rhagadioloides", "saxifraga_cernua", "sinapis_arvensis", "senna_siamea", "syzygium_malaccense", "trochetia_boutoniana", "roystonea_altissima", "hohenbergiopsis_guatemalensis", "amblystegium_serpens", "androya_decaryi", "onosma_echioides", "downingia_bacigalupii", "koeberlinia_spinosa", "physalis_crassifolia", "tacinga_inamoena", "melocactus_azureus", "rhipsalis_paradoxa", "pterospermum_acerifolium", "rhodotypos_scandens", "syzygium_aqueum", "peltophorum_dasyrhachis",
        "parasitaxus_usta", "trevoa_quinquenervia", "ostrya_rehderiana", "cottendorfia_florida", "heliamphora_glabra", "garcinia_travancorica", "miersiella_umbellata", "rosa_pendulina", "plocosperma_buxifolium", "setchellanthus_caeruleus", "stenocereus_eruca", "retzia_capensis", "terminalia_paniculata", "abarema_turbinata", "erythrina_euodiphylla", "rhoiptelea_chiliantha", "turbinicarpus_alonsoi", "dracaena_forskaliana", "bivonaea_lutea", "melocactus_lemairei",
        "vachellia_anegadensis", "acacia_mathuataensis", "ambrella_longituba", "dioncophyllum_thollonii", "halothamnus_glaucus", "rosa_cymosa", "nectouxia_formosa", "sclerocactus_mesae_verdae", "sphagnum_magellanicum", "gymnocalycium_calochlorum", "turbinicarpus_saueri", "turbinicarpus_gielsdorfianus", "tacinga_funalis", "tacinga_saxatilis", "melocactus_pachyacanthus", "plowmania_nyctaginoides", "halothamnus_lancifolius", "guamatela_tuerckheimii", "ruizia_parviflora", "plagiomnium_affine",
        "ambuchanania_leucobryoides", "andreaeobryum_macrosporum", "catoscopium_nigritum", "hennediella_heimii", "bryum_turbinatum", "cirriphyllum_piliferum", "pohlia_melanodon", "dictyostega_orobanchoides", "heteranthia_decipiens", "escobaria_missouriensis", "lophopyxis_maingayi", "coronidium_boormanii", "cryptocarya_nova_anglica", "pradosia_decipiens", "pradosia_kuhlmannii", "pradosia_cuatrecasasii", "pradosia_grisebachii", "jupunba_obovalis", "espadaea_amoena", "aglaia_evansensis", "ipomoea_pulcherrima", "chamaecrista_aldabrensis", "aglaia_archboldiana", "syzygium_phaeophyllum", "erythrina_atitlanensis", "pierrebraunia_bahiensis", "rauhocereus_riosaniensis")

    // Exclude bundled plates with verified mismatches: D. braunii depicts D. sanderiana;
    // Iriartea deltoidea depicts blue-black fruit, unlike its documented yellow-green fruit;
    // N. inermis depicts a prominent pitcher rim, unlike its nearly absent peristome;
    // P. usta depicts a self-rooted plant with red cones, unlike the parasitic species.
    private val mismatchedPlates = setOf("dracaena_braunii", "iriartea_deltoidea", "nepenthes_inermis", "parasitaxus_usta")

    fun hasPlate(id: String) = id in speciesIds && id !in mismatchedPlates

    fun decode(key: PlantImageKey): Bitmap {
        val assets = A2UApplication.appContext.assets
        val path = "botany/herbarium/${key.id}.webp"
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid herbarium plate: ${key.id}" }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (inSampleSize * 2) >= key.edge) inSampleSize *= 2
        }
        return assets.open(path).use { requireNotNull(BitmapFactory.decodeStream(it, null, options)) }
    }
}
