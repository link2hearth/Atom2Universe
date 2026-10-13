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
        "pseudotsuga", "rosa_virginiana", "pisum")

    fun hasPlate(id: String) = id in speciesIds

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
