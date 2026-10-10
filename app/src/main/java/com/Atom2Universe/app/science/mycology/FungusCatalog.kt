package com.Atom2Universe.app.science.mycology

import com.Atom2Universe.app.R

/**
 * Le catalogue : 44 espèces d'Europe (19 au premier lot, 13 au second, 12 au troisième), rangées en groupes de sosies d'après les confusions
 * réellement observées en France (ANSES). Les mesures sont typiques, jamais exactes.
 * Faits et sources : « CHAMPIGNONS.md » (hors dépôt) et la page À propos du module.
 */
object FungusCatalog {
    private fun c(rgb: Long) = (0xFF000000L or rgb).toInt()
    private val WHITE = c(0xF5F3E8)
    private val SPORE_WHITE = c(0xF4F4EE)
    private val SPORE_BROWN = c(0x40291F)

    private fun m(at: Anchor, label: Int) = Mark(at, label)

    private val phalloides = FungusSpecies(
        id = "phalloides", name = R.string.myco_phalloides_name, altNames = R.string.myco_phalloides_alt,
        latin = R.string.myco_phalloides_latin, status = FungusStatus.DEADLY,
        look = FungusLook(
            capDiam = 9f, capRise = 3.3f, capN = 2.25f, edgeDrop = 0.4f,
            capCenter = c(0x6A7530), capMid = c(0x8A9140), capEdge = c(0xB4B56C),
            capDecos = listOf(CapDeco.Fibrils(c(0x4C5A22), 36, 80), CapDeco.Flakes(c(0xF4F2E6), 1, 1.6f)),
            stipeH = 10f, stipeW = 1.15f, stipeBaseW = 1.3f, stipeForm = StipeForm.BULB, bulbW = 2.7f, bulbH = 2.3f,
            stipeTop = c(0xEAE8D8), stipeBottom = c(0xE2DFCB),
            stipeDecos = listOf(StipeDeco.Fibrils(c(0x8A9050), 95)), interior = Interior.STUFFED,
            hymColor = c(0xF2F0E2), attach = GillAttach.FREE, crowd = 0.9f,
            ring = RingKind.SKIRT, ringAt = 0.8f, volva = VolvaKind.SAC, volvaH = 3.4f,
            flesh = c(0xF4F2E6), spore = SPORE_WHITE, seed = 1,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_olive_cap), m(Anchor.FACE, R.string.myco_l_white_free_gills),
                m(Anchor.RING, R.string.myco_l_ring), m(Anchor.VOLVA, R.string.myco_l_volva_sac)),
            marksSection = listOf(m(Anchor.S_HYM, R.string.myco_l_free_gills), m(Anchor.S_VOLVA, R.string.myco_l_volva_sac)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_white_gills))
        ),
        traits = R.array.myco_phalloides_traits, note = R.string.myco_phalloides_note
    )

    private val virosa = FungusSpecies(
        id = "virosa", name = R.string.myco_virosa_name, altNames = R.string.myco_virosa_alt,
        latin = R.string.myco_virosa_latin, status = FungusStatus.DEADLY,
        look = FungusLook(
            capDiam = 8f, capRise = 4.8f, capN = 1.7f,
            capCenter = c(0xEFEBDB), capMid = c(0xF6F3E7), capEdge = c(0xFAF8F0),
            capDecos = listOf(CapDeco.Fibrils(c(0xB9B3A0), 28, 60)),
            stipeH = 12f, stipeW = 1.1f, stipeBaseW = 1.3f, stipeForm = StipeForm.BULB, bulbW = 2.4f, bulbH = 2f,
            stipeTop = c(0xF4F1E6), stipeBottom = c(0xEFEBDC),
            stipeDecos = listOf(StipeDeco.Fibrils(c(0xB9B3A0), 120)), interior = Interior.STUFFED,
            hymColor = c(0xF3F1E5), attach = GillAttach.FREE, crowd = 0.85f,
            ring = RingKind.SKIRT, ringAt = 0.82f, volva = VolvaKind.SAC, volvaH = 4.2f,
            flesh = c(0xF5F3E8), spore = SPORE_WHITE, seed = 2,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_all_white), m(Anchor.RING, R.string.myco_l_ring_fragile),
                m(Anchor.VOLVA, R.string.myco_l_volva_sac)),
            marksSection = listOf(m(Anchor.S_VOLVA, R.string.myco_l_volva_sac), m(Anchor.S_FLESH, R.string.myco_l_all_white)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_white_gills))
        ),
        traits = R.array.myco_virosa_traits, note = R.string.myco_virosa_note
    )

    private val muscaria = FungusSpecies(
        id = "muscaria", name = R.string.myco_muscaria_name, altNames = R.string.myco_muscaria_alt,
        latin = R.string.myco_muscaria_latin, status = FungusStatus.TOXIC,
        look = FungusLook(
            capDiam = 12f, capRise = 4.3f, capN = 2.2f, edgeDrop = 0.5f,
            capCenter = c(0xC4201A), capMid = c(0xD83822), capEdge = c(0xEB6A2C),
            capDecos = listOf(CapDeco.Warts(c(0xF8F4E8), 38, 0.62f)),
            stipeH = 12f, stipeW = 1.8f, stipeBaseW = 2f, stipeForm = StipeForm.BULB, bulbW = 3.5f, bulbH = 3f,
            stipeTop = c(0xF6F2E6), stipeBottom = c(0xEFEADB), interior = Interior.STUFFED,
            hymColor = c(0xF4F0E2), attach = GillAttach.FREE, crowd = 0.8f,
            ring = RingKind.SKIRT, ringAt = 0.75f, volva = VolvaKind.RIMS, volvaH = 3f,
            flesh = c(0xF6F3E8), spore = SPORE_WHITE, seed = 3,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_white_warts), m(Anchor.FACE, R.string.myco_l_white_gills),
                m(Anchor.RING, R.string.myco_l_ring), m(Anchor.VOLVA, R.string.myco_l_rims)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_white)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_white_gills))
        ),
        traits = R.array.myco_muscaria_traits, note = R.string.myco_muscaria_note
    )

    private val pantherina = FungusSpecies(
        id = "pantherina", name = R.string.myco_pantherina_name, altNames = R.string.myco_pantherina_alt,
        latin = R.string.myco_pantherina_latin, status = FungusStatus.TOXIC,
        look = FungusLook(
            capDiam = 10f, capRise = 3.6f, capN = 2.3f, edgeDrop = 0.5f,
            capCenter = c(0x4A2F1E), capMid = c(0x654630), capEdge = c(0x8C6B49),
            capDecos = listOf(CapDeco.Warts(c(0xF6F2E6), 34, 0.42f), CapDeco.Striate(130)),
            stipeH = 10f, stipeW = 1.6f, stipeBaseW = 1.8f, stipeForm = StipeForm.MARGINATE, bulbW = 3.1f, bulbH = 2.1f,
            stipeTop = c(0xF3EFE2), stipeBottom = c(0xEBE6D6), interior = Interior.STUFFED,
            hymColor = c(0xF3F0E3), attach = GillAttach.FREE, crowd = 0.85f,
            ring = RingKind.SKIRT, ringAt = 0.7f, volva = VolvaKind.RIMS, volvaH = 2.6f,
            flesh = c(0xF6F3E8), spore = SPORE_WHITE, seed = 4,
            marksSide = listOf(m(Anchor.EDGE, R.string.myco_l_striate_margin), m(Anchor.CAP, R.string.myco_l_white_warts),
                m(Anchor.RING, R.string.myco_l_ring_smooth), m(Anchor.VOLVA, R.string.myco_l_bulb_edge)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_unchanging)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_white_gills))
        ),
        traits = R.array.myco_pantherina_traits, note = R.string.myco_pantherina_note
    )

    private val rubescens = FungusSpecies(
        id = "rubescens", name = R.string.myco_rubescens_name, altNames = R.string.myco_rubescens_alt,
        latin = R.string.myco_rubescens_latin, status = FungusStatus.COOKED,
        look = FungusLook(
            capDiam = 10f, capRise = 4f, capN = 2.2f, edgeDrop = 0.3f,
            capCenter = c(0x8A5742), capMid = c(0xA9775F), capEdge = c(0xC7A38D),
            capDecos = listOf(CapDeco.Flakes(c(0xB9AC9B), 30, 0.65f)),
            stipeH = 10f, stipeW = 1.9f, stipeBaseW = 2.2f, stipeForm = StipeForm.BULB, bulbW = 3.7f, bulbH = 3.4f,
            stipeTop = c(0xEDDDD3), stipeBottom = c(0xD8B5A6), interior = Interior.STUFFED,
            hymColor = c(0xF3F0E3), attach = GillAttach.FREE, crowd = 0.85f,
            ring = RingKind.STRIATE, ringAt = 0.78f,
            flesh = c(0xF6F2EA), stains = listOf(Stain(StainZone.STIPE_BASE, c(0xB54A3D), 0.7f), Stain(StainZone.CAP_SKIN, c(0xB55A4B), 0.55f)),
            spore = SPORE_WHITE, seed = 5,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_flakes), m(Anchor.RING, R.string.myco_l_ring_striate),
                m(Anchor.BULB, R.string.myco_l_bulb_no_volva)),
            marksSection = listOf(m(Anchor.S_STAIN, R.string.myco_l_turns_red)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_white_gills))
        ),
        traits = R.array.myco_rubescens_traits, note = R.string.myco_rubescens_note
    )

    private val edulis = FungusSpecies(
        id = "edulis", name = R.string.myco_edulis_name, altNames = R.string.myco_edulis_alt,
        latin = R.string.myco_edulis_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 14f, capRise = 5.4f, capN = 2f, edgeDrop = 0.7f,
            capCenter = c(0x6B4224), capMid = c(0x8C5B33), capEdge = c(0xBE915F),
            capDecos = listOf(CapDeco.Sticky),
            stipeH = 10f, stipeW = 3.4f, stipeForm = StipeForm.BARREL, bulbW = 6.6f,
            fleshThick = 3.3f,
            stipeTop = c(0xDCC29C), stipeBottom = c(0xCDB085),
            stipeDecos = listOf(StipeDeco.Net(c(0xFFF8E6), 0.5f, 0.5f, 220)),
            hymenium = Hymenium.PORES, hymColor = c(0xE6DCA4), hymInner = c(0xE4DA9C), attach = GillAttach.ADNATE, hymDepth = 1.9f,
            flesh = c(0xF7F2E4), stains = listOf(Stain(StainZone.CAP_SKIN, c(0xC9A590), 0.3f)),
            spore = c(0x6F5E2C), seed = 6,
            marksSide = listOf(m(Anchor.FACE, R.string.myco_l_pores_white_yellow), m(Anchor.STIPE_U, R.string.myco_l_net_white)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_unchanging), m(Anchor.S_HYM, R.string.myco_l_tubes)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_pores))
        ),
        traits = R.array.myco_edulis_traits, note = R.string.myco_edulis_note
    )

    private val felleus = FungusSpecies(
        id = "felleus", name = R.string.myco_felleus_name, altNames = R.string.myco_felleus_alt,
        latin = R.string.myco_felleus_latin, status = FungusStatus.INEDIBLE,
        look = FungusLook(
            capDiam = 10f, capRise = 3.8f, capN = 2.1f, edgeDrop = 0.5f,
            capCenter = c(0x866040), capMid = c(0xA67F55), capEdge = c(0xC7A87E),
            stipeH = 9f, stipeW = 2.4f, stipeBaseW = 3.4f, stipeForm = StipeForm.CLUB,
            fleshThick = 2.4f,
            stipeTop = c(0xC9AD86), stipeBottom = c(0xE3D2B6),
            stipeDecos = listOf(StipeDeco.Net(c(0x5A3D22), 0.7f, 0.55f, 235)),
            hymenium = Hymenium.PORES, hymColor = c(0xDBAEA4), hymInner = c(0xE6C4BA), attach = GillAttach.ADNATE, hymDepth = 1.5f,
            flesh = c(0xF5F0E8), spore = c(0xCBA79A), seed = 7,
            marksSide = listOf(m(Anchor.FACE, R.string.myco_l_pink_pores), m(Anchor.STIPE_U, R.string.myco_l_net_dark)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_bitter), m(Anchor.S_HYM, R.string.myco_l_tubes_pink)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_pink_pores))
        ),
        traits = R.array.myco_felleus_traits, note = R.string.myco_felleus_note
    )

    private val satanas = FungusSpecies(
        id = "satanas", name = R.string.myco_satanas_name, altNames = R.string.myco_satanas_alt,
        latin = R.string.myco_satanas_latin, status = FungusStatus.TOXIC,
        look = FungusLook(
            capDiam = 16f, capRise = 6.2f, capN = 2f, edgeDrop = 0.8f,
            capCenter = c(0xB5AD95), capMid = c(0xD0C9B5), capEdge = c(0xE6E1D2),
            stipeH = 8f, stipeW = 4.2f, stipeForm = StipeForm.BARREL, bulbW = 7.6f,
            fleshThick = 3.6f,
            stipeTop = c(0xE2B83A), stipeBottom = c(0xC53C2B),
            stipeDecos = listOf(StipeDeco.Net(c(0xA52B23), 1f, 0.38f, 190)),
            hymenium = Hymenium.PORES, hymColor = c(0xC1281E), hymInner = c(0xE8D872), attach = GillAttach.ADNATE, hymDepth = 2.1f,
            flesh = c(0xF1E9C9), stains = listOf(Stain(StainZone.ABOVE_TUBES, c(0x5F86BE), 0.85f)),
            spore = c(0x7A7342), seed = 8,
            marksSide = listOf(m(Anchor.FACE, R.string.myco_l_red_pores), m(Anchor.STIPE_M, R.string.myco_l_net_red),
                m(Anchor.CAP, R.string.myco_l_pale_cap)),
            marksSection = listOf(m(Anchor.S_STAIN, R.string.myco_l_turns_blue), m(Anchor.S_HYM, R.string.myco_l_tubes_yellow)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_red_pores))
        ),
        traits = R.array.myco_satanas_traits, note = R.string.myco_satanas_note
    )

    private val cibarius = FungusSpecies(
        id = "cibarius", name = R.string.myco_cibarius_name, altNames = R.string.myco_cibarius_alt,
        latin = R.string.myco_cibarius_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 7f, capRise = 3.2f, capShape = CapShape.FUNNEL, dip = 0.3f,
            capCenter = c(0xD79A18), capMid = c(0xEBB22C), capEdge = c(0xF4C84C),
            capDecos = listOf(CapDeco.Felt),
            stipeH = 4.2f, stipeW = 1.6f, stipeBaseW = 1f, stipeForm = StipeForm.TAPER_DOWN,
            stipeTop = c(0xEDB93A), stipeBottom = c(0xE2AA2C),
            hymenium = Hymenium.RIDGES, hymColor = c(0xF1C253), hymInner = c(0xF3D074), attach = GillAttach.DECURRENT,
            flesh = c(0xF5ECD2), stipeFlesh = c(0xF3E4B8), spore = c(0xF2E6B6), seed = 9,
            marksSide = listOf(m(Anchor.FACE, R.string.myco_l_folds), m(Anchor.CAP, R.string.myco_l_egg_yellow)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_cream)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_forked_folds))
        ),
        traits = R.array.myco_cibarius_traits, note = R.string.myco_cibarius_note
    )

    private val olearius = FungusSpecies(
        id = "olearius", name = R.string.myco_olearius_name, altNames = R.string.myco_olearius_alt,
        latin = R.string.myco_olearius_latin, status = FungusStatus.TOXIC,
        look = FungusLook(
            capDiam = 10f, capRise = 3.8f, capShape = CapShape.FUNNEL, dip = 0.4f,
            capCenter = c(0xA94A18), capMid = c(0xD8691C), capEdge = c(0xEE8E2A),
            stipeH = 7f, stipeW = 2f, stipeBaseW = 1.2f, stipeForm = StipeForm.TAPER_DOWN,
            stipeTop = c(0xD66F23), stipeBottom = c(0x7B3E1C),
            hymenium = Hymenium.GILLS, hymColor = c(0xF3A331), hymInner = c(0xF3A331), attach = GillAttach.DECURRENT, crowd = 1f,
            flesh = c(0xF2AE45), stipeFlesh = c(0xE58B2E), spore = c(0xF4E9CD), cluster = 3, seed = 10,
            marksSide = listOf(m(Anchor.FACE, R.string.myco_l_true_gills), m(Anchor.CAP, R.string.myco_l_cluster)),
            marksSection = listOf(m(Anchor.S_STIPE, R.string.myco_l_flesh_orange), m(Anchor.S_HYM, R.string.myco_l_true_gills)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_true_gills))
        ),
        traits = R.array.myco_olearius_traits, note = R.string.myco_olearius_note
    )

    private val aurantiaca = FungusSpecies(
        id = "aurantiaca", name = R.string.myco_aurantiaca_name, altNames = R.string.myco_aurantiaca_alt,
        latin = R.string.myco_aurantiaca_latin, status = FungusStatus.INEDIBLE,
        look = FungusLook(
            capDiam = 5.5f, capRise = 1.6f, capShape = CapShape.FUNNEL, dip = 0.25f, inrolled = true,
            capCenter = c(0xC26A1B), capMid = c(0xE5882A), capEdge = c(0xF3B04F),
            capDecos = listOf(CapDeco.Felt),
            stipeH = 3.6f, stipeW = 0.85f, stipeBaseW = 0.7f, stipeForm = StipeForm.TAPER_DOWN, lean = 0.35f,
            stipeTop = c(0xDD8A33), stipeBottom = c(0x6D4627),
            hymenium = Hymenium.GILLS, hymColor = c(0xE98322), forked = true, attach = GillAttach.DECURRENT, crowd = 1f,
            flesh = c(0xF4CB8E), spore = c(0xF6EEDC), seed = 11,
            marksSide = listOf(m(Anchor.FACE, R.string.myco_l_fine_forked_gills), m(Anchor.STIPE_M, R.string.myco_l_stem_darkens)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_soft)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_fine_forked_gills))
        ),
        traits = R.array.myco_aurantiaca_traits, note = R.string.myco_aurantiaca_note, history = R.string.myco_aurantiaca_history
    )

    private val procera = FungusSpecies(
        id = "procera", name = R.string.myco_procera_name, altNames = R.string.myco_procera_alt,
        latin = R.string.myco_procera_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 17f, capRise = 6f, capN = 2.4f, edgeDrop = 0.3f, umbo = 1f,
            capCenter = c(0x5C3E2E), capMid = c(0xBA9F89), capEdge = c(0xDCCDB9),
            capDecos = listOf(CapDeco.Scales(c(0x8E6849), 7, 1.5f, 0.22f)),
            stipeH = 20f, stipeW = 1.5f, stipeBaseW = 1.6f, stipeForm = StipeForm.BULB, bulbW = 3.4f, bulbH = 2.6f,
            stipeTop = c(0xE7DCCB), stipeBottom = c(0xDED0BB),
            stipeDecos = listOf(StipeDeco.Bands(c(0x8D6D50), 0.85f, 215)), interior = Interior.HOLLOW,
            hymColor = c(0xF4F0E4), attach = GillAttach.FREE, hymDepth = 0.9f, crowd = 0.9f,
            ring = RingKind.SLIDING, ringAt = 0.76f, ringColor = c(0xF2EADB),
            flesh = c(0xF6F2E8), spore = SPORE_WHITE, seed = 12,
            marksSide = listOf(m(Anchor.UMBO, R.string.myco_l_umbo), m(Anchor.CAP, R.string.myco_l_scales),
                m(Anchor.RING, R.string.myco_l_ring_sliding), m(Anchor.STIPE_M, R.string.myco_l_banded_stem)),
            marksSection = listOf(m(Anchor.S_HYM, R.string.myco_l_free_gills)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_white_gills))
        ),
        traits = R.array.myco_procera_traits, note = R.string.myco_procera_note
    )

    private val brunneoincarnata = FungusSpecies(
        id = "brunneoincarnata", name = R.string.myco_brunneoincarnata_name, altNames = R.string.myco_brunneoincarnata_alt,
        latin = R.string.myco_brunneoincarnata_latin, status = FungusStatus.DEADLY,
        look = FungusLook(
            capDiam = 4.2f, capRise = 1.7f, capN = 2.1f, edgeDrop = 0.15f,
            capCenter = c(0x7A4838), capMid = c(0xAC7561), capEdge = c(0xDCC5B3),
            capDecos = listOf(CapDeco.Scales(c(0x9A5D49), 4, 0.5f, 0.25f)),
            stipeH = 4.5f, stipeW = 0.5f, stipeBaseW = 0.55f,
            stipeTop = c(0xEBD0BE), stipeBottom = c(0x7B4E3C),
            stipeDecos = listOf(StipeDeco.Scales(c(0x5C3A2C), 0.55f)), interior = Interior.STUFFED,
            hymColor = c(0xF3EFE4), attach = GillAttach.FREE, crowd = 0.8f,
            ring = RingKind.ZONE, ringAt = 0.6f,
            flesh = c(0xF4EEE6), stains = listOf(Stain(StainZone.STIPE_BASE, c(0xB5524A), 0.6f)), spore = SPORE_WHITE, seed = 13,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_scales), m(Anchor.RING, R.string.myco_l_ring_zone),
                m(Anchor.BASE, R.string.myco_l_scaly_stem)),
            marksSection = listOf(m(Anchor.S_HYM, R.string.myco_l_free_gills), m(Anchor.S_STAIN, R.string.myco_l_turns_red)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_white_gills))
        ),
        traits = R.array.myco_brunneoincarnata_traits, note = R.string.myco_brunneoincarnata_note
    )

    private val campestris = FungusSpecies(
        id = "campestris", name = R.string.myco_campestris_name, altNames = R.string.myco_campestris_alt,
        latin = R.string.myco_campestris_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 7f, capRise = 3f, capN = 2.1f, edgeDrop = 0.3f,
            capCenter = c(0xEAE5D6), capMid = c(0xF5F2E8), capEdge = c(0xFBF9F2),
            capDecos = listOf(CapDeco.Fibrils(c(0xB7B09F), 30, 50)),
            stipeH = 5f, stipeW = 1.4f, stipeBaseW = 1.2f,
            stipeTop = c(0xF5F1E6), stipeBottom = c(0xEFEADC),
            hymColor = c(0xE6969E), attach = GillAttach.FREE, crowd = 0.9f,
            ring = RingKind.FUGACIOUS, ringAt = 0.8f,
            flesh = c(0xF6F2E8), spore = SPORE_BROWN, seed = 14,
            marksSide = listOf(m(Anchor.FACE, R.string.myco_l_pink_gills), m(Anchor.RING, R.string.myco_l_ring_fugacious),
                m(Anchor.STIPE_M, R.string.myco_l_no_bulb)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_white), m(Anchor.S_HYM, R.string.myco_l_free_gills)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_pink_to_brown))
        ),
        traits = R.array.myco_campestris_traits, note = R.string.myco_campestris_note
    )

    private val xanthodermus = FungusSpecies(
        id = "xanthodermus", name = R.string.myco_xanthodermus_name, altNames = R.string.myco_xanthodermus_alt,
        latin = R.string.myco_xanthodermus_latin, status = FungusStatus.TOXIC,
        look = FungusLook(
            capDiam = 10f, capRise = 4.3f, capN = 3f, edgeDrop = 0.8f,
            capCenter = c(0xE6DFCC), capMid = c(0xF2ECDD), capEdge = c(0xFAF6EB),
            capDecos = listOf(CapDeco.Fibrils(c(0xA89A7E), 24, 50)),
            stipeH = 10f, stipeW = 1.5f, stipeBaseW = 1.7f, stipeForm = StipeForm.BULB, bulbW = 3.2f, bulbH = 2.5f,
            stipeTop = c(0xF5F1E6), stipeBottom = c(0xEFE9D6), interior = Interior.STUFFED,
            hymColor = c(0xE2A0A6), attach = GillAttach.FREE, crowd = 0.9f,
            ring = RingKind.FLOCCOSE, ringAt = 0.74f,
            flesh = c(0xF6F2E6),
            stains = listOf(Stain(StainZone.CAP_EDGE, c(0xE3BF1D), 0.9f), Stain(StainZone.STIPE_BASE, c(0xE8C21B), 0.95f)),
            spore = SPORE_BROWN, seed = 15,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_turns_yellow), m(Anchor.RING, R.string.myco_l_ring_large),
                m(Anchor.BULB, R.string.myco_l_bulb)),
            marksSection = listOf(m(Anchor.S_STAIN, R.string.myco_l_chrome_yellow), m(Anchor.S_HYM, R.string.myco_l_free_gills)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_pink_to_brown))
        ),
        traits = R.array.myco_xanthodermus_traits, note = R.string.myco_xanthodermus_note
    )

    private val morchella = FungusSpecies(
        id = "morchella", name = R.string.myco_morchella_name, altNames = R.string.myco_morchella_alt,
        latin = R.string.myco_morchella_latin, status = FungusStatus.COOKED,
        look = FungusLook(
            capDiam = 4.8f, capRise = 6f, capShape = CapShape.MOREL,
            capCenter = c(0x5B4427), capMid = c(0xB9944F), capEdge = c(0xD0B067),
            stipeH = 5.2f, stipeW = 2.4f, stipeBaseW = 3.4f, stipeForm = StipeForm.FLARED,
            stipeTop = c(0xF0E4C8), stipeBottom = c(0xE6D6B2), interior = Interior.HOLLOW,
            hymenium = Hymenium.NONE, hymColor = c(0xC9A55E),
            flesh = c(0xEEE3C8), spore = c(0xF1E7BE), seed = 16,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_pits), m(Anchor.STIPE_M, R.string.myco_l_stem_granular)),
            marksSection = listOf(m(Anchor.S_CAVITY, R.string.myco_l_one_cavity), m(Anchor.S_HYM, R.string.myco_l_fused_base)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_pits))
        ),
        traits = R.array.myco_morchella_traits, note = R.string.myco_morchella_note
    )

    private val gyromitra = FungusSpecies(
        id = "gyromitra", name = R.string.myco_gyromitra_name, altNames = R.string.myco_gyromitra_alt,
        latin = R.string.myco_gyromitra_latin, status = FungusStatus.DEADLY, saleBanned = true,
        look = FungusLook(
            capDiam = 8f, capRise = 5.4f, capShape = CapShape.BRAIN,
            capCenter = c(0x5A2C14), capMid = c(0x7D4122), capEdge = c(0x9C5B33),
            stipeH = 3.8f, stipeW = 2.4f, stipeBaseW = 3f, stipeForm = StipeForm.CLUB,
            stipeTop = c(0xEBE0CC), stipeBottom = c(0xE2D5BC),
            stipeDecos = listOf(StipeDeco.Furrows(c(0x8B7A5E))), interior = Interior.CHAMBERED,
            hymenium = Hymenium.NONE, hymColor = c(0xA86A3C),
            flesh = c(0xF1E8D6), spore = c(0xEFE2BE), seed = 17,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_brain), m(Anchor.STIPE_M, R.string.myco_l_furrowed)),
            marksSection = listOf(m(Anchor.S_CAVITY, R.string.myco_l_chambered), m(Anchor.S_HYM, R.string.myco_l_fused_points)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_brain))
        ),
        traits = R.array.myco_gyromitra_traits, note = R.string.myco_gyromitra_note, history = R.string.myco_gyromitra_history
    )

    private val paxillus = FungusSpecies(
        id = "paxillus", name = R.string.myco_paxillus_name, altNames = R.string.myco_paxillus_alt,
        latin = R.string.myco_paxillus_latin, status = FungusStatus.DEADLY,
        look = FungusLook(
            capDiam = 9f, capRise = 2.6f, capN = 2f, edgeDrop = 0.9f, dip = 0.7f, inrolled = true,
            capCenter = c(0x6B4D24), capMid = c(0x8D6B3A), capEdge = c(0xB89B64),
            capDecos = listOf(CapDeco.Felt),
            stipeH = 5f, stipeW = 2.1f, stipeBaseW = 1.2f, stipeForm = StipeForm.TAPER_DOWN, lean = 0.4f,
            stipeTop = c(0xBF9A58), stipeBottom = c(0x9B7B48),
            hymColor = c(0xCDA24F), forked = true, attach = GillAttach.DECURRENT, crowd = 0.9f,
            flesh = c(0xEBD9A2), spore = c(0x8A5A2B), seed = 18,
            marksSide = listOf(m(Anchor.EDGE, R.string.myco_l_inrolled), m(Anchor.FACE, R.string.myco_l_decurrent_gills),
                m(Anchor.STIPE_M, R.string.myco_l_no_ring)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_yellowish), m(Anchor.S_HYM, R.string.myco_l_decurrent_gills)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_forked_gills))
        ),
        traits = R.array.myco_paxillus_traits, note = R.string.myco_paxillus_note, history = R.string.myco_paxillus_history
    )

    private val equestre = FungusSpecies(
        id = "equestre", name = R.string.myco_equestre_name, altNames = R.string.myco_equestre_alt,
        latin = R.string.myco_equestre_latin, status = FungusStatus.TOXIC, saleBanned = true,
        look = FungusLook(
            capDiam = 8f, capRise = 2.8f, capN = 2.4f, edgeDrop = 0.5f,
            capCenter = c(0x8C6B1F), capMid = c(0xC9B62B), capEdge = c(0xE4D646),
            capDecos = listOf(CapDeco.Fibrils(c(0x8C5F1C), 26, 150), CapDeco.Scales(c(0x8C5F1C), 2, 0.4f, 0.1f), CapDeco.Sticky),
            stipeH = 6f, stipeW = 1.7f, stipeBaseW = 1.9f, stipeForm = StipeForm.CLUB,
            stipeTop = c(0xE1D34A), stipeBottom = c(0xC8B845),
            hymColor = c(0xEDE04C), attach = GillAttach.FREE, crowd = 1f,
            flesh = c(0xF7F3DD), spore = c(0xF8F6EA), seed = 19,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_brown_sticky), m(Anchor.FACE, R.string.myco_l_sulphur_gills),
                m(Anchor.STIPE_M, R.string.myco_l_no_ring)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_white_yellow), m(Anchor.S_HYM, R.string.myco_l_sulphur_gills)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_sulphur_gills))
        ),
        traits = R.array.myco_equestre_traits, note = R.string.myco_equestre_note, history = R.string.myco_equestre_history
    )

    // ------------------------------------------------------------------ lot 2

    private val caesarea = FungusSpecies(
        id = "caesarea", name = R.string.myco_caesarea_name, altNames = R.string.myco_caesarea_alt,
        latin = R.string.myco_caesarea_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 13f, capRise = 4.6f, capN = 2.2f, edgeDrop = 0.6f,
            capCenter = c(0xD9421A), capMid = c(0xE9591C), capEdge = c(0xF2B037),
            capDecos = listOf(CapDeco.Striate(150)),
            stipeH = 12f, stipeW = 2.4f, stipeBaseW = 3f, stipeForm = StipeForm.CLUB,
            stipeTop = c(0xF0C94A), stipeBottom = c(0xE9BE3E), interior = Interior.SOLID,
            hymColor = c(0xF0C040), attach = GillAttach.FREE, crowd = 0.9f,
            ring = RingKind.SKIRT, ringAt = 0.78f, ringColor = c(0xF2CF55),
            volva = VolvaKind.SAC, volvaH = 4.6f,
            flesh = c(0xF7F2DE), spore = SPORE_WHITE, seed = 20,
            marksSide = listOf(m(Anchor.EDGE, R.string.myco_l_striate_margin), m(Anchor.FACE, R.string.myco_l_yellow_gills),
                m(Anchor.RING, R.string.myco_l_ring_yellow), m(Anchor.VOLVA, R.string.myco_l_volva_sac)),
            marksSection = listOf(m(Anchor.S_HYM, R.string.myco_l_free_gills), m(Anchor.S_VOLVA, R.string.myco_l_volva_sac)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_yellow_gills))
        ),
        traits = R.array.myco_caesarea_traits, note = R.string.myco_caesarea_note
    )

    private val sinuatum = FungusSpecies(
        id = "sinuatum", name = R.string.myco_sinuatum_name, altNames = R.string.myco_sinuatum_alt,
        latin = R.string.myco_sinuatum_latin, status = FungusStatus.TOXIC,
        look = FungusLook(
            capDiam = 11f, capRise = 3.6f, capN = 2.1f, edgeDrop = 0.6f, umbo = 0.3f,
            capCenter = c(0xA9A08A), capMid = c(0xC2B8A0), capEdge = c(0xD8CFB8),
            capDecos = listOf(CapDeco.Fibrils(c(0x9A9078), 30, 60)),
            stipeH = 9f, stipeW = 2.2f, stipeBaseW = 3f, stipeForm = StipeForm.CLUB,
            stipeTop = c(0xF1ECDD), stipeBottom = c(0xE8E1CE), interior = Interior.SOLID,
            hymColor = c(0xE3B9A8), attach = GillAttach.ADNATE, crowd = 0.6f,
            flesh = c(0xF4F0E6), spore = c(0xD6A59B), seed = 21,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_grey_beige_cap), m(Anchor.FACE, R.string.myco_l_gills_pink_ripe),
                m(Anchor.STIPE_M, R.string.myco_l_no_ring)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_mealy)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_gills_pink_ripe))
        ),
        traits = R.array.myco_sinuatum_traits, note = R.string.myco_sinuatum_note
    )

    private val nebularis = FungusSpecies(
        id = "nebularis", name = R.string.myco_nebularis_name, altNames = R.string.myco_nebularis_alt,
        latin = R.string.myco_nebularis_latin, status = FungusStatus.INEDIBLE,
        look = FungusLook(
            capDiam = 15f, capRise = 4.5f, capN = 2.1f, edgeDrop = 0.8f, inrolled = true,
            capCenter = c(0x8E8A80), capMid = c(0xA9A59A), capEdge = c(0xC8C4B8),
            capDecos = listOf(CapDeco.Fibrils(c(0x8A867C), 40, 60), CapDeco.Felt),
            stipeH = 8f, stipeW = 3f, stipeBaseW = 4.2f, stipeForm = StipeForm.CLUB,
            stipeTop = c(0xD9D4C6), stipeBottom = c(0xD0CABC), interior = Interior.SOLID,
            hymColor = c(0xE9E1C6), attach = GillAttach.ADNATE, forked = true, crowd = 0.9f,
            flesh = c(0xF3F0E6), spore = c(0xF1E8C4), seed = 22,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_grey_bloom_cap), m(Anchor.FACE, R.string.myco_l_cream_close_gills),
                m(Anchor.STIPE_M, R.string.myco_l_stout_stem)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_thick_white)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_cream_close_gills))
        ),
        traits = R.array.myco_nebularis_traits, note = R.string.myco_nebularis_note
    )

    private val gambosa = FungusSpecies(
        id = "gambosa", name = R.string.myco_gambosa_name, altNames = R.string.myco_gambosa_alt,
        latin = R.string.myco_gambosa_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 8f, capRise = 3.6f, capN = 2.1f, edgeDrop = 0.7f, inrolled = true,
            capCenter = c(0xD9C7A3), capMid = c(0xE6D8BC), capEdge = c(0xEFE5CE),
            capDecos = listOf(CapDeco.Felt),
            stipeH = 4.5f, stipeW = 2.2f, stipeBaseW = 2.5f, stipeForm = StipeForm.CLUB,
            stipeTop = c(0xF3EEDF), stipeBottom = c(0xEDE6D2), interior = Interior.SOLID,
            hymColor = c(0xF2EBD6), attach = GillAttach.ADNATE, crowd = 1f,
            flesh = c(0xF7F3E8), spore = SPORE_WHITE, seed = 23,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_cream_cap), m(Anchor.FACE, R.string.myco_l_white_close_gills),
                m(Anchor.STIPE_M, R.string.myco_l_stubby_stem)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_mealy)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_white_close_gills))
        ),
        traits = R.array.myco_gambosa_traits, note = R.string.myco_gambosa_note
    )

    private val erubescens = FungusSpecies(
        id = "erubescens", name = R.string.myco_erubescens_name, altNames = R.string.myco_erubescens_alt,
        latin = R.string.myco_erubescens_latin, status = FungusStatus.TOXIC,
        look = FungusLook(
            capDiam = 5f, capRise = 3.6f, capN = 1.6f, edgeDrop = 0.2f, umbo = 0.5f,
            capCenter = c(0xC9A66A), capMid = c(0xDCC590), capEdge = c(0xEADFC0),
            capDecos = listOf(CapDeco.Fibrils(c(0x9A7A48), 36, 90)),
            stipeH = 6f, stipeW = 0.9f, stipeBaseW = 1.1f,
            stipeTop = c(0xF0EADC), stipeBottom = c(0xEADFCB), interior = Interior.SOLID,
            hymColor = c(0xCDA878), attach = GillAttach.FREE, crowd = 0.9f,
            flesh = c(0xF3EEDF),
            stains = listOf(Stain(StainZone.CAP_SKIN, c(0xB5382C), 0.5f), Stain(StainZone.STIPE_BASE, c(0xC24A3A), 0.55f)),
            spore = c(0x8A6A45), seed = 24,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_conical_cap), m(Anchor.FACE, R.string.myco_l_gills_ochre_ripe),
                m(Anchor.STIPE_M, R.string.myco_l_no_ring)),
            marksSection = listOf(m(Anchor.S_STAIN, R.string.myco_l_turns_red)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_gills_ochre_ripe))
        ),
        traits = R.array.myco_erubescens_traits, note = R.string.myco_erubescens_note
    )

    private val oreades = FungusSpecies(
        id = "oreades", name = R.string.myco_oreades_name, altNames = R.string.myco_oreades_alt,
        latin = R.string.myco_oreades_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 4f, capRise = 1.8f, capN = 2f, edgeDrop = 0.2f, umbo = 0.45f,
            capCenter = c(0xC9A06B), capMid = c(0xDDBB8A), capEdge = c(0xEACFA6),
            capDecos = listOf(CapDeco.Striate(110)),
            stipeH = 7f, stipeW = 0.5f, stipeBaseW = 0.5f,
            stipeTop = c(0xE8D6B2), stipeBottom = c(0xE0CCA4), interior = Interior.HOLLOW,
            hymColor = c(0xF0E4CF), attach = GillAttach.FREE, crowd = 0.2f,
            flesh = c(0xF3E9D6), spore = SPORE_WHITE, seed = 25,
            marksSide = listOf(m(Anchor.UMBO, R.string.myco_l_umbo_plain), m(Anchor.FACE, R.string.myco_l_far_gills),
                m(Anchor.STIPE_M, R.string.myco_l_thin_tough_stem)),
            marksSection = listOf(m(Anchor.S_CAVITY, R.string.myco_l_stem_hollow)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_far_gills))
        ),
        traits = R.array.myco_oreades_traits, note = R.string.myco_oreades_note
    )

    private val rivulosa = FungusSpecies(
        id = "rivulosa", name = R.string.myco_rivulosa_name, altNames = R.string.myco_rivulosa_alt,
        latin = R.string.myco_rivulosa_latin, status = FungusStatus.TOXIC,
        look = FungusLook(
            capDiam = 4f, capRise = 1.4f, capN = 2.1f, edgeDrop = 0.4f, dip = 0.25f, inrolled = true,
            capCenter = c(0xD6CFC0), capMid = c(0xE9E5DA), capEdge = c(0xF4F1EA),
            capDecos = listOf(CapDeco.Felt),
            stipeH = 3.2f, stipeW = 0.8f, stipeBaseW = 0.8f,
            stipeTop = c(0xF1EEE6), stipeBottom = c(0xE6E1D4), interior = Interior.SOLID,
            hymColor = c(0xF0EBDD), attach = GillAttach.ADNATE, crowd = 1f,
            flesh = c(0xF4F1EA), spore = SPORE_WHITE, seed = 26,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_white_frosted_cap), m(Anchor.FACE, R.string.myco_l_close_gills),
                m(Anchor.STIPE_M, R.string.myco_l_short_stem)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_white)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_close_gills))
        ),
        traits = R.array.myco_rivulosa_traits, note = R.string.myco_rivulosa_note
    )

    private val atramentaria = FungusSpecies(
        id = "atramentaria", name = R.string.myco_atramentaria_name, altNames = R.string.myco_atramentaria_alt,
        latin = R.string.myco_atramentaria_latin, status = FungusStatus.INEDIBLE,
        look = FungusLook(
            capDiam = 4.2f, capRise = 5.6f, capN = 2f, edgeDrop = 0.5f,
            capCenter = c(0x8B8577), capMid = c(0xA39D8E), capEdge = c(0xC1BBAD),
            capDecos = listOf(CapDeco.Striate(150)),
            stipeH = 12f, stipeW = 1.1f, stipeBaseW = 1.5f, stipeForm = StipeForm.CLUB,
            stipeTop = c(0xE9E7E0), stipeBottom = c(0xE2DFD6), interior = Interior.HOLLOW,
            hymColor = c(0x5A5560), attach = GillAttach.FREE, crowd = 1f,
            flesh = c(0xE8E4DA), spore = c(0x2E2326), seed = 27,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_egg_cap)),
            marksSection = listOf(m(Anchor.S_CAVITY, R.string.myco_l_stem_hollow)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_gills_to_ink))
        ),
        traits = R.array.myco_atramentaria_traits, note = R.string.myco_atramentaria_note
    )

    private val comatus = FungusSpecies(
        id = "comatus", name = R.string.myco_comatus_name, altNames = R.string.myco_comatus_alt,
        latin = R.string.myco_comatus_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 5f, capRise = 9f, capN = 3f, edgeDrop = 0.2f,
            capCenter = c(0xB59470), capMid = c(0xEDE6D8), capEdge = c(0xF6F2EA),
            capDecos = listOf(CapDeco.Shaggy(c(0xA98660), 10, 1.15f)),
            stipeH = 13f, stipeW = 1.3f, stipeBaseW = 1.7f, stipeForm = StipeForm.CLUB,
            stipeTop = c(0xF4F1EA), stipeBottom = c(0xEFEBE0), interior = Interior.HOLLOW,
            hymColor = c(0xCDA3AB), attach = GillAttach.FREE, crowd = 1f,
            ring = RingKind.FUGACIOUS, ringAt = 0.3f,
            flesh = c(0xF6F3EC), spore = c(0x2B2022), seed = 28,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_shaggy_cap), m(Anchor.RING, R.string.myco_l_ring)),
            marksSection = listOf(m(Anchor.S_HYM, R.string.myco_l_free_gills)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_gills_pink_black))
        ),
        traits = R.array.myco_comatus_traits, note = R.string.myco_comatus_note
    )

    private val galerina = FungusSpecies(
        id = "galerina", name = R.string.myco_galerina_name, altNames = R.string.myco_galerina_alt,
        latin = R.string.myco_galerina_latin, status = FungusStatus.DEADLY,
        look = FungusLook(
            capDiam = 3.8f, capRise = 1.9f, capN = 2.2f, edgeDrop = 0.3f, umbo = 0.2f,
            capCenter = c(0xA8672E), capMid = c(0xC48A48), capEdge = c(0xD9AF72),
            stipeH = 4.5f, stipeW = 0.45f, stipeBaseW = 0.5f,
            stipeTop = c(0xE6D9BC), stipeBottom = c(0x8A5A32),
            stipeDecos = listOf(StipeDeco.Fibrils(c(0x6B4A2A), 90)), interior = Interior.STUFFED,
            hymColor = c(0xB98550), attach = GillAttach.ADNATE, crowd = 0.85f,
            ring = RingKind.SKIRT, ringAt = 0.74f, ringColor = c(0xC9A878),
            flesh = c(0xD9C09A), spore = c(0x8B5A2B), cluster = 2, seed = 29,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_tawny_cap), m(Anchor.RING, R.string.myco_l_ring_fragile),
                m(Anchor.STIPE_M, R.string.myco_l_silky_below_ring)),
            marksSection = listOf(m(Anchor.S_HYM, R.string.myco_l_tawny_gills)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_tawny_gills))
        ),
        traits = R.array.myco_galerina_traits, note = R.string.myco_galerina_note
    )

    private val mutabilis = FungusSpecies(
        id = "mutabilis", name = R.string.myco_mutabilis_name, altNames = R.string.myco_mutabilis_alt,
        latin = R.string.myco_mutabilis_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 5.5f, capRise = 2.4f, capN = 2.2f, edgeDrop = 0.4f, umbo = 0.2f,
            capCenter = c(0xE8C288), capMid = c(0xC4803E), capEdge = c(0xA6642B),
            stipeH = 7f, stipeW = 0.9f, stipeBaseW = 1f,
            stipeTop = c(0xE3C48E), stipeBottom = c(0x8F5B2C),
            stipeDecos = listOf(StipeDeco.Scales(c(0x6E4524), 0.5f)), interior = Interior.STUFFED,
            hymColor = c(0xC28A52), attach = GillAttach.ADNATE, crowd = 0.85f,
            ring = RingKind.SKIRT, ringAt = 0.62f, ringColor = c(0xA9763F),
            flesh = c(0xE8D2A8), spore = c(0x8B5A2B), cluster = 3, seed = 30,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_pale_centre_cap), m(Anchor.RING, R.string.myco_l_ring_fragile),
                m(Anchor.STIPE_M, R.string.myco_l_scaly_below_ring)),
            marksSection = listOf(m(Anchor.S_HYM, R.string.myco_l_cinnamon_gills)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_cinnamon_gills))
        ),
        traits = R.array.myco_mutabilis_traits, note = R.string.myco_mutabilis_note
    )

    private val fasciculare = FungusSpecies(
        id = "fasciculare", name = R.string.myco_fasciculare_name, altNames = R.string.myco_fasciculare_alt,
        latin = R.string.myco_fasciculare_latin, status = FungusStatus.TOXIC,
        look = FungusLook(
            capDiam = 5f, capRise = 2.4f, capN = 2.3f, edgeDrop = 0.3f,
            capCenter = c(0xC9791B), capMid = c(0xE8D22E), capEdge = c(0xF2E461),
            stipeH = 8f, stipeW = 0.7f, stipeBaseW = 0.7f, lean = 0.6f,
            stipeTop = c(0xEDD94C), stipeBottom = c(0xC98B2E), interior = Interior.STUFFED,
            hymColor = c(0x8D9A45), attach = GillAttach.ADNATE, crowd = 1f,
            ring = RingKind.ZONE, ringAt = 0.8f, ringColor = c(0x6A5A4A),
            flesh = c(0xF0E27A), spore = c(0x4A3040), cluster = 3, seed = 31,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_sulphur_cap), m(Anchor.FACE, R.string.myco_l_gills_green_ripe),
                m(Anchor.RING, R.string.myco_l_ring_zone)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_sulphur)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_gills_green_ripe))
        ),
        traits = R.array.myco_fasciculare_traits, note = R.string.myco_fasciculare_note
    )

    private val mellea = FungusSpecies(
        id = "mellea", name = R.string.myco_mellea_name, altNames = R.string.myco_mellea_alt,
        latin = R.string.myco_mellea_latin, status = FungusStatus.INEDIBLE,
        look = FungusLook(
            capDiam = 8f, capRise = 3f, capN = 2.3f, edgeDrop = 0.4f, umbo = 0.15f,
            capCenter = c(0x8A6B2E), capMid = c(0xB89A45), capEdge = c(0xD8C070),
            capDecos = listOf(CapDeco.Flakes(c(0x5A4421), 30, 0.32f)),
            stipeH = 11f, stipeW = 1.1f, stipeBaseW = 1.3f,
            stipeTop = c(0xE9DDB0), stipeBottom = c(0xC4A55A), interior = Interior.STUFFED,
            hymColor = c(0xF0E8C4), attach = GillAttach.ADNATE, crowd = 0.8f,
            ring = RingKind.SKIRT, ringAt = 0.8f, ringColor = c(0xF2EBCB),
            flesh = c(0xF3EEDC), spore = SPORE_WHITE, cluster = 3, seed = 32,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_honey_cap), m(Anchor.RING, R.string.myco_l_ring_white)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_white)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_white_gills))
        ),
        traits = R.array.myco_mellea_traits, note = R.string.myco_mellea_note, history = R.string.myco_mellea_history
    )

    private val craterellus = FungusSpecies(
        id = "craterellus", name = R.string.myco_craterellus_name, altNames = R.string.myco_craterellus_alt,
        latin = R.string.myco_craterellus_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 6.5f, capRise = 5.4f, capShape = CapShape.FUNNEL, dip = 2.5f,
            capCenter = c(0x1F1C1A), capMid = c(0x3A3430), capEdge = c(0x5E5750),
            stipeH = 2.8f, stipeW = 1.5f, stipeBaseW = 0.9f, stipeForm = StipeForm.TAPER_DOWN,
            stipeTop = c(0x7C7E83), stipeBottom = c(0x45423F), interior = Interior.HOLLOW,
            hymenium = Hymenium.NONE, hymColor = c(0x9A9CA2), hymInner = c(0x9A9CA2), attach = GillAttach.DECURRENT,
            flesh = c(0x8A857E), stipeFlesh = c(0x8A857E), spore = SPORE_WHITE, seed = 33,
            marksSide = listOf(m(Anchor.EDGE, R.string.myco_l_wavy_edge), m(Anchor.CAP, R.string.myco_l_dark_inside),
                m(Anchor.FACE, R.string.myco_l_smooth_grey_face)),
            marksSection = listOf(m(Anchor.S_CAVITY, R.string.myco_l_hollow_all), m(Anchor.S_FLESH, R.string.myco_l_thin_flesh)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_smooth_grey_under))
        ),
        traits = R.array.myco_craterellus_traits, note = R.string.myco_craterellus_note
    )

    private val hydnum = FungusSpecies(
        id = "hydnum", name = R.string.myco_hydnum_name, altNames = R.string.myco_hydnum_alt,
        latin = R.string.myco_hydnum_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 7.5f, capRise = 2.3f, capN = 2.2f, edgeDrop = 0.4f, inrolled = true,
            capCenter = c(0xC3955F), capMid = c(0xD6B184), capEdge = c(0xE6CFAB),
            capDecos = listOf(CapDeco.Felt),
            stipeH = 5f, stipeW = 2.2f, stipeBaseW = 1.9f,
            stipeTop = c(0xEADCC2), stipeBottom = c(0xD6C09B),
            hymenium = Hymenium.TEETH, hymColor = c(0xF1E3C9), hymInner = c(0xEFE0C4), attach = GillAttach.DECURRENT, hymDepth = 0.55f,
            flesh = c(0xF5EFE3), stains = listOf(Stain(StainZone.STIPE_CUT, c(0xE7A04A), 0.5f)), spore = c(0xF6ECD6), seed = 34,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_matt_cap), m(Anchor.FACE, R.string.myco_l_cream_spines),
                m(Anchor.STIPE_M, R.string.myco_l_stubby_stem)),
            marksSection = listOf(m(Anchor.S_HYM, R.string.myco_l_spines), m(Anchor.S_STAIN, R.string.myco_l_turns_orange)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_spines_no_gills))
        ),
        traits = R.array.myco_hydnum_traits, note = R.string.myco_hydnum_note
    )

    private val badia = FungusSpecies(
        id = "badia", name = R.string.myco_badia_name, altNames = R.string.myco_badia_alt,
        latin = R.string.myco_badia_latin, status = FungusStatus.COOKED,
        look = FungusLook(
            capDiam = 9f, capRise = 3.9f, capN = 2.1f, edgeDrop = 0.5f,
            capCenter = c(0x5B3A22), capMid = c(0x7C4C2B), capEdge = c(0x9B6A42),
            stipeH = 7f, stipeW = 2.1f, stipeBaseW = 1.9f,
            stipeTop = c(0xC2A06A), stipeBottom = c(0xA8774A),
            hymenium = Hymenium.PORES, hymColor = c(0xD2C770), hymInner = c(0xD6CC7E), attach = GillAttach.ADNATE, hymDepth = 1.3f,
            flesh = c(0xF2EEDD), stains = listOf(Stain(StainZone.ABOVE_TUBES, c(0x5F86BE), 0.6f)), spore = c(0x6B5B2A), seed = 35,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_chestnut_cap), m(Anchor.FACE, R.string.myco_l_pores_olive_yellow),
                m(Anchor.STIPE_M, R.string.myco_l_smooth_stem)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_white), m(Anchor.S_STAIN, R.string.myco_l_turns_blue),
                m(Anchor.S_HYM, R.string.myco_l_tubes)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_pores))
        ),
        traits = R.array.myco_badia_traits, note = R.string.myco_badia_note
    )

    private val chrysenteron = FungusSpecies(
        id = "chrysenteron", name = R.string.myco_chrysenteron_name, altNames = R.string.myco_chrysenteron_alt,
        latin = R.string.myco_chrysenteron_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 5.5f, capRise = 2.3f, capN = 2.3f, edgeDrop = 0.4f,
            capCenter = c(0x6A5A3E), capMid = c(0x877355), capEdge = c(0xA19070),
            capDecos = listOf(CapDeco.Cracks(c(0xD98C8C), 0.5f, 200)),
            stipeH = 6f, stipeW = 1f, stipeBaseW = 0.8f, stipeForm = StipeForm.TAPER_DOWN,
            stipeTop = c(0xDDBF55), stipeBottom = c(0xAE3646),
            stipeDecos = listOf(StipeDeco.Dots(c(0xB02A3C), 0.3f, 0.075f, 200)),
            hymenium = Hymenium.PORES, hymColor = c(0xCDC05A), hymInner = c(0xD2C666), attach = GillAttach.ADNATE, hymDepth = 0.9f,
            flesh = c(0xF0EBD3), stipeFlesh = c(0xEFE6B6), stains = listOf(Stain(StainZone.STIPE_BASE, c(0xA3394A), 0.65f)),
            spore = c(0x6B5B2A), seed = 36,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_cracked_cap), m(Anchor.FACE, R.string.myco_l_pores_yellow),
                m(Anchor.STIPE_U, R.string.myco_l_red_spotted_stem)),
            marksSection = listOf(m(Anchor.S_STAIN, R.string.myco_l_turns_red), m(Anchor.S_HYM, R.string.myco_l_tubes)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_pores))
        ),
        traits = R.array.myco_chrysenteron_traits, note = R.string.myco_chrysenteron_note
    )

    private val erythropus = FungusSpecies(
        id = "erythropus", name = R.string.myco_erythropus_name, altNames = R.string.myco_erythropus_alt,
        latin = R.string.myco_erythropus_latin, status = FungusStatus.COOKED,
        look = FungusLook(
            capDiam = 12f, capRise = 4.8f, capN = 2f, edgeDrop = 0.7f,
            capCenter = c(0x3E2616), capMid = c(0x5E3A20), capEdge = c(0x80552F),
            capDecos = listOf(CapDeco.Felt),
            stipeH = 9f, stipeW = 3.4f, stipeBaseW = 3f, fleshThick = 2.8f,
            stipeTop = c(0xDDB63A), stipeBottom = c(0xCB9330),
            stipeDecos = listOf(StipeDeco.Dots(c(0xC4251D), 0.27f, 0.09f, 235)),
            hymenium = Hymenium.PORES, hymColor = c(0xC8341F), hymInner = c(0xE9D766), attach = GillAttach.ADNATE, hymDepth = 1.8f,
            flesh = c(0xF3E269), stipeFlesh = c(0xF3E269),
            stains = listOf(Stain(StainZone.ABOVE_TUBES, c(0x2447B0), 0.9f), Stain(StainZone.STIPE_CUT, c(0x2447B0), 1f)),
            spore = c(0x6B5B2A), seed = 37,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_dark_velvet_cap), m(Anchor.FACE, R.string.myco_l_red_pores),
                m(Anchor.STIPE_M, R.string.myco_l_red_dots_stem)),
            marksSection = listOf(m(Anchor.S_STAIN, R.string.myco_l_turns_blue), m(Anchor.S_FLESH, R.string.myco_l_flesh_yellow),
                m(Anchor.S_HYM, R.string.myco_l_tubes_yellow)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_red_pores))
        ),
        traits = R.array.myco_erythropus_traits, note = R.string.myco_erythropus_note
    )

    private val prunulus = FungusSpecies(
        id = "prunulus", name = R.string.myco_prunulus_name, altNames = R.string.myco_prunulus_alt,
        latin = R.string.myco_prunulus_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 7f, capRise = 2.4f, capN = 2.2f, edgeDrop = 0.4f, dip = 0.5f, inrolled = true,
            capCenter = c(0xCFCABD), capMid = c(0xE2DED2), capEdge = c(0xF0EDE4),
            capDecos = listOf(CapDeco.Felt),
            stipeH = 3.5f, stipeW = 1.1f, stipeBaseW = 0.9f, stipeForm = StipeForm.FLARED,
            stipeTop = c(0xF0EDE4), stipeBottom = c(0xE4DFD1),
            hymColor = c(0xEBCDC6), attach = GillAttach.DECURRENT, crowd = 0.8f,
            flesh = c(0xF5F2EA), spore = c(0xEDBDB4), seed = 38,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_white_frosted_cap), m(Anchor.FACE, R.string.myco_l_decurrent_gills),
                m(Anchor.STIPE_M, R.string.myco_l_flared_stem)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_mealy)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_gills_pink_ripe))
        ),
        traits = R.array.myco_prunulus_traits, note = R.string.myco_prunulus_note
    )

    private val deliciosus = FungusSpecies(
        id = "deliciosus", name = R.string.myco_deliciosus_name, altNames = R.string.myco_deliciosus_alt,
        latin = R.string.myco_deliciosus_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 9f, capRise = 3.2f, capN = 2.2f, edgeDrop = 0.5f, dip = 0.7f, inrolled = true,
            capCenter = c(0xC4622B), capMid = c(0xE08C44), capEdge = c(0xEBB275),
            capDecos = listOf(CapDeco.Zones(c(0xA9501F), 4, 120)),
            stipeH = 3.8f, stipeW = 1.7f, stipeBaseW = 1.5f,
            stipeTop = c(0xECC9A0), stipeBottom = c(0xE3AC76),
            stipeDecos = listOf(StipeDeco.Dots(c(0xC9692C), 0.36f, 0.12f, 220)), interior = Interior.STUFFED,
            hymColor = c(0xE89C4F), attach = GillAttach.ADNATE, crowd = 0.9f,
            flesh = c(0xEFA862), stipeFlesh = c(0xF3E6D2), latex = c(0xE2701F), spore = c(0xF2E7B9), seed = 39,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_zoned_cap), m(Anchor.FACE, R.string.myco_l_orange_gills),
                m(Anchor.STIPE_M, R.string.myco_l_pitted_stem)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_orange), m(Anchor.S_STAIN, R.string.myco_l_orange_milk)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_orange_gills))
        ),
        traits = R.array.myco_deliciosus_traits, note = R.string.myco_deliciosus_note
    )

    private val torminosus = FungusSpecies(
        id = "torminosus", name = R.string.myco_torminosus_name, altNames = R.string.myco_torminosus_alt,
        latin = R.string.myco_torminosus_latin, status = FungusStatus.TOXIC,
        look = FungusLook(
            capDiam = 8f, capRise = 2.8f, capN = 2.2f, edgeDrop = 0.5f, dip = 0.6f, inrolled = true,
            capCenter = c(0xC98A78), capMid = c(0xDDA897), capEdge = c(0xEBCDB9),
            capDecos = listOf(CapDeco.Zones(c(0xB46A58), 3, 100), CapDeco.Felt),
            stipeH = 5f, stipeW = 1.4f, stipeBaseW = 1.3f,
            stipeTop = c(0xF0D8C8), stipeBottom = c(0xE5BFA8), interior = Interior.STUFFED,
            hymColor = c(0xF0D2C2), attach = GillAttach.DECURRENT, crowd = 1f,
            flesh = c(0xF4E6DA), latex = c(0xFBFBF5), spore = c(0xF3E9C6), seed = 40,
            marksSide = listOf(m(Anchor.EDGE, R.string.myco_l_hairy_margin), m(Anchor.CAP, R.string.myco_l_zoned_cap),
                m(Anchor.FACE, R.string.myco_l_close_gills)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_white), m(Anchor.S_STAIN, R.string.myco_l_white_milk)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_close_gills))
        ),
        traits = R.array.myco_torminosus_traits, note = R.string.myco_torminosus_note
    )

    private val nuda = FungusSpecies(
        id = "nuda", name = R.string.myco_nuda_name, altNames = R.string.myco_nuda_alt,
        latin = R.string.myco_nuda_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 8f, capRise = 3.1f, capN = 2.1f, edgeDrop = 0.4f, inrolled = true,
            capCenter = c(0x8B6B7E), capMid = c(0x9F82AE), capEdge = c(0xB8A0C6),
            stipeH = 5.5f, stipeW = 1.8f, stipeBaseW = 2f, stipeForm = StipeForm.BULB, bulbW = 2.6f, bulbH = 1.4f,
            stipeTop = c(0xBBA7C6), stipeBottom = c(0xA38CB2),
            stipeDecos = listOf(StipeDeco.Fibrils(c(0xE9E3EE), 130)),
            hymColor = c(0x8D62A4), attach = GillAttach.ADNATE, crowd = 1f,
            flesh = c(0xF0ECF1), stipeFlesh = c(0xDACDE2), spore = c(0xEFC9C9), seed = 41,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_lilac_cap), m(Anchor.FACE, R.string.myco_l_violet_gills),
                m(Anchor.STIPE_U, R.string.myco_l_lilac_stem)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_white)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_violet_gills))
        ),
        traits = R.array.myco_nuda_traits, note = R.string.myco_nuda_note
    )

    private val orellanus = FungusSpecies(
        id = "orellanus", name = R.string.myco_orellanus_name, altNames = R.string.myco_orellanus_alt,
        latin = R.string.myco_orellanus_latin, status = FungusStatus.DEADLY,
        look = FungusLook(
            capDiam = 5f, capRise = 2.1f, capN = 2.2f, edgeDrop = 0.3f, inrolled = true,
            capCenter = c(0x9A4F1C), capMid = c(0xB4672B), capEdge = c(0xC98443),
            capDecos = listOf(CapDeco.Fibrils(c(0x7A3A14), 42, 80)),
            stipeH = 6.5f, stipeW = 1.3f, stipeBaseW = 0.9f, stipeForm = StipeForm.TAPER_DOWN,
            stipeTop = c(0xD5A558), stipeBottom = c(0xB27338),
            hymColor = c(0xB05A25), attach = GillAttach.ADNATE, crowd = 0.35f,
            flesh = c(0xE9D0A0), spore = c(0x8A4B24), seed = 42,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_orange_brown_cap), m(Anchor.FACE, R.string.myco_l_rust_far_gills),
                m(Anchor.STIPE_M, R.string.myco_l_tapering_stem)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_ochre)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_rust_far_gills))
        ),
        traits = R.array.myco_orellanus_traits, note = R.string.myco_orellanus_note
    )

    private val virescens = FungusSpecies(
        id = "virescens", name = R.string.myco_virescens_name, altNames = R.string.myco_virescens_alt,
        latin = R.string.myco_virescens_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 8.5f, capRise = 3.3f, capN = 2.2f, edgeDrop = 0.4f, dip = 0.3f,
            capCenter = c(0x7F9E6E), capMid = c(0xA0B98F), capEdge = c(0xD2DCBD),
            capDecos = listOf(CapDeco.Cracks(c(0xE6EDD3), 0.7f, 200)),
            stipeH = 5.5f, stipeW = 1.9f, stipeBaseW = 1.7f,
            stipeTop = c(0xF4F0E4), stipeBottom = c(0xE9DFCB),
            hymColor = c(0xF1EDDB), attach = GillAttach.ADNATE, crowd = 0.7f, forked = true,
            flesh = c(0xF6F3EA), spore = SPORE_WHITE, seed = 43,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_green_cracked_cap), m(Anchor.FACE, R.string.myco_l_white_close_gills),
                m(Anchor.STIPE_U, R.string.myco_l_no_ring)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_thick_white)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_white_gills))
        ),
        traits = R.array.myco_virescens_traits, note = R.string.myco_virescens_note
    )

    private val columbetta = FungusSpecies(
        id = "columbetta", name = R.string.myco_columbetta_name, altNames = R.string.myco_columbetta_alt,
        latin = R.string.myco_columbetta_latin, status = FungusStatus.EDIBLE,
        look = FungusLook(
            capDiam = 7f, capRise = 3.3f, capN = 2f, edgeDrop = 0.4f,
            capCenter = c(0xF1EDD6), capMid = c(0xF9F7EE), capEdge = c(0xFEFDF9),
            capDecos = listOf(CapDeco.Sticky, CapDeco.Fibrils(c(0xD3CEBC), 36, 60)),
            stipeH = 9f, stipeW = 1.7f, stipeBaseW = 1.6f,
            stipeTop = c(0xF6F4EA), stipeBottom = c(0xECEBE4),
            hymColor = c(0xF6F4EA), attach = GillAttach.ADNATE, crowd = 0.5f,
            flesh = c(0xF7F5EE), spore = SPORE_WHITE, seed = 44,
            marksSide = listOf(m(Anchor.CAP, R.string.myco_l_white_silky_cap), m(Anchor.FACE, R.string.myco_l_white_wide_gills),
                m(Anchor.STIPE_U, R.string.myco_l_no_ring)),
            marksSection = listOf(m(Anchor.S_FLESH, R.string.myco_l_flesh_mealy)),
            marksUnder = listOf(m(Anchor.U_HYM, R.string.myco_l_white_gills))
        ),
        traits = R.array.myco_columbetta_traits, note = R.string.myco_columbetta_note
    )

    val species: List<FungusSpecies> = listOf(
        phalloides, virosa, muscaria, pantherina, rubescens,
        edulis, felleus, satanas,
        cibarius, olearius, aurantiaca,
        procera, brunneoincarnata,
        campestris, xanthodermus,
        morchella, gyromitra,
        paxillus, equestre,
        caesarea, sinuatum, nebularis, gambosa, erubescens, oreades, rivulosa,
        atramentaria, comatus, galerina, mutabilis, fasciculare, mellea,
        craterellus, hydnum, badia, chrysenteron, erythropus,
        prunulus, deliciosus, torminosus, nuda, orellanus, virescens, columbetta
    )

    private val byId = species.associateBy { it.id }
    fun get(id: String): FungusSpecies? = byId[id]

    val groups: List<FungusGroup> = listOf(
        FungusGroup("chanterelle", R.string.myco_g_chanterelle_title, listOf("cibarius", "olearius", "aurantiaca"),
            R.array.myco_g_chanterelle_points, R.string.myco_g_chanterelle_evidence),
        FungusGroup("boletus", R.string.myco_g_boletus_title, listOf("edulis", "felleus", "satanas"),
            R.array.myco_g_boletus_points, R.string.myco_g_boletus_evidence),
        FungusGroup("parasol", R.string.myco_g_parasol_title, listOf("procera", "phalloides", "pantherina", "brunneoincarnata"),
            R.array.myco_g_parasol_points, R.string.myco_g_parasol_evidence),
        FungusGroup("field", R.string.myco_g_field_title, listOf("campestris", "xanthodermus", "virosa"),
            R.array.myco_g_field_points, R.string.myco_g_field_evidence),
        FungusGroup("morel", R.string.myco_g_morel_title, listOf("morchella", "gyromitra"),
            R.array.myco_g_morel_points, R.string.myco_g_morel_evidence),
        FungusGroup("amanita", R.string.myco_g_amanita_title, listOf("rubescens", "pantherina", "muscaria"),
            R.array.myco_g_amanita_points, R.string.myco_g_amanita_evidence),
        FungusGroup("oronge", R.string.myco_g_oronge_title, listOf("caesarea", "muscaria"),
            R.array.myco_g_oronge_points, R.string.myco_g_oronge_evidence),
        FungusGroup("stgeorge", R.string.myco_g_stgeorge_title, listOf("gambosa", "erubescens", "sinuatum"),
            R.array.myco_g_stgeorge_points, R.string.myco_g_stgeorge_evidence),
        FungusGroup("fairyring", R.string.myco_g_fairyring_title, listOf("oreades", "rivulosa", "paxillus"),
            R.array.myco_g_fairyring_points, R.string.myco_g_fairyring_evidence),
        FungusGroup("entoloma", R.string.myco_g_entoloma_title, listOf("sinuatum", "nebularis"),
            R.array.myco_g_entoloma_points, R.string.myco_g_entoloma_evidence),
        FungusGroup("inkcap", R.string.myco_g_inkcap_title, listOf("comatus", "atramentaria"),
            R.array.myco_g_inkcap_points, R.string.myco_g_inkcap_evidence),
        FungusGroup("woodtuft", R.string.myco_g_woodtuft_title, listOf("mutabilis", "galerina", "fasciculare", "mellea"),
            R.array.myco_g_woodtuft_points, R.string.myco_g_woodtuft_evidence),
        FungusGroup("greenforms", R.string.myco_g_greenforms_title, listOf("phalloides", "virescens", "equestre"),
            R.array.myco_g_greenforms_points, R.string.myco_g_greenforms_evidence),
        FungusGroup("whiteforms", R.string.myco_g_whiteforms_title, listOf("virosa", "columbetta", "sinuatum"),
            R.array.myco_g_whiteforms_points, R.string.myco_g_whiteforms_evidence),
        FungusGroup("clitopilus", R.string.myco_g_clitopilus_title, listOf("prunulus", "rivulosa"),
            R.array.myco_g_clitopilus_points, R.string.myco_g_clitopilus_evidence),
        FungusGroup("redpore", R.string.myco_g_redpore_title, listOf("erythropus", "satanas"),
            R.array.myco_g_redpore_points, R.string.myco_g_redpore_evidence),
        FungusGroup("milkcap", R.string.myco_g_milkcap_title, listOf("deliciosus", "torminosus"),
            R.array.myco_g_milkcap_points, R.string.myco_g_milkcap_evidence)
    )

    fun groupsOf(id: String): List<FungusGroup> = groups.filter { id in it.members }

    /** Les champignons dont le classement a changé : de ce qu'on en disait avant à ce qu'on en sait aujourd'hui. */
    val changes: List<FungusChange> = listOf(
        FungusChange("paxillus", FungusStatus.EDIBLE, FungusStatus.DEADLY, listOf(1944, 1985), R.string.myco_change_paxillus),
        FungusChange("equestre", FungusStatus.EDIBLE, FungusStatus.TOXIC, listOf(2001, 2004), R.string.myco_change_equestre),
        FungusChange("gyromitra", FungusStatus.COOKED, FungusStatus.DEADLY, listOf(1991), R.string.myco_change_gyromitra),
        FungusChange("aurantiaca", FungusStatus.TOXIC, FungusStatus.INEDIBLE, listOf(1821, 1999), R.string.myco_change_aurantiaca),
        FungusChange("mellea", FungusStatus.EDIBLE, FungusStatus.INEDIBLE, listOf(2015, 2016), R.string.myco_change_mellea)
    )
}
