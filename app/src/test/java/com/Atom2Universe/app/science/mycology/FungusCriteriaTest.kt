package com.Atom2Universe.app.science.mycology

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fous des critères de tri de l'écran Espèces : ils sont calculés d'après les dessins, donc ils doivent
 * retrouver ce que le dessin montre, et la combinaison « ou » dans une ligne / « et » entre les lignes doit tenir.
 */
class FungusCriteriaTest {
    private fun look(id: String) = FungusCatalog.get(id)!!.look
    private fun ids(vararg criteria: String) = FungusCatalog.species.filter { FungusCriteria.matches(it.look, criteria.toSet()) }.map { it.id }.toSet()

    @Test
    fun sansCritereToutLeMondeEstLa() {
        assertEquals(FungusCatalog.species.size, ids().size)
    }

    @Test
    fun unCritereLitLeDessin() {
        val phalloides = look("phalloides")
        for (id in listOf("s_ring", "s_volva", "u_gills", "cap_GREEN", "sp_white", "w_medium")) assertTrue(id, FungusCriteria.matches(phalloides, setOf(id)))
        for (id in listOf("s_no_ring", "s_no_volva", "u_pores", "cap_RED", "sp_black", "w_small", "m_cluster")) assertFalse(id, FungusCriteria.matches(phalloides, setOf(id)))

        val edulis = look("edulis")
        for (id in listOf("u_pores", "s_no_ring", "s_no_volva", "cap_BROWN", "w_large")) assertTrue(id, FungusCriteria.matches(edulis, setOf(id)))

        assertTrue(FungusCriteria.matches(look("muscaria"), setOf("cap_RED", "c_scaly", "s_volva")))
        assertTrue(FungusCriteria.matches(look("cibarius"), setOf("u_ridges", "c_funnel", "cap_YELLOW")))
        assertTrue(FungusCriteria.matches(look("morchella"), setOf("u_none", "c_wrinkled", "w_small")))
        assertTrue(FungusCriteria.matches(look("mutabilis"), setOf("m_cluster", "s_ring")))
        assertTrue(FungusCriteria.matches(look("campestris"), setOf("sp_brown")))
        assertTrue(FungusCriteria.matches(look("atramentaria"), setOf("sp_black")))
        assertTrue(FungusCriteria.matches(look("rubescens"), setOf("m_stains")))
    }

    @Test
    fun leLotTroisSeLitDansLeDessin() {
        assertTrue(FungusCriteria.matches(look("hydnum"), setOf("u_teeth", "s_no_ring", "m_stains")))
        assertTrue(FungusCriteria.matches(look("craterellus"), setOf("u_none", "c_funnel", "cap_BLACK", "stipe_GREY")))
        assertTrue(FungusCriteria.matches(look("erythropus"), setOf("u_pores", "cap_BROWN", "stipe_YELLOW", "m_stains", "w_medium")))
        assertTrue(FungusCriteria.matches(look("chrysenteron"), setOf("u_pores", "w_small", "stipe_RED", "m_stains")))
        assertTrue(FungusCriteria.matches(look("deliciosus"), setOf("m_latex", "cap_ORANGE", "u_gills")))
        assertTrue(FungusCriteria.matches(look("torminosus"), setOf("m_latex", "cap_PINK")))
        assertTrue(FungusCriteria.matches(look("nuda"), setOf("sp_pink", "cap_PINK", "u_gills")))
        assertTrue(FungusCriteria.matches(look("prunulus"), setOf("sp_pink", "s_no_ring", "s_no_volva")))
        assertTrue(FungusCriteria.matches(look("orellanus"), setOf("sp_brown", "w_small")))
        assertTrue(FungusCriteria.matches(look("virescens"), setOf("cap_GREEN", "s_no_ring", "s_no_volva", "sp_white")))
        assertTrue(FungusCriteria.matches(look("columbetta"), setOf("cap_WHITE", "stipe_WHITE", "s_no_ring", "s_no_volva")))
        // les lactaires seuls laissent couler un lait
        assertEquals(setOf("deliciosus", "torminosus"), ids("m_latex"))
        // ce que le dessin ne montre pas ne s'invente pas : pas d'aiguillons ailleurs, pas de volve chez le lot trois
        assertEquals(setOf("hydnum"), ids("u_teeth"))
        for (id in listOf("hydnum", "craterellus", "badia", "chrysenteron", "erythropus", "prunulus", "deliciosus", "torminosus", "nuda", "orellanus", "virescens", "columbetta"))
            assertTrue("$id a une volve", look(id).volva == VolvaKind.NONE)
    }

    @Test
    fun leLotQuatreSeLitDansLeDessin() {
        // les deux formes nouvelles ne se trouvent que par leur forme
        assertEquals(setOf("perlatum", "egg", "citrinum", "gigantea"), ids("c_ball"))
        assertEquals(setOf("ostreatus"), ids("c_bracket"))
        assertTrue(FungusCriteria.matches(look("ostreatus"), setOf("c_bracket", "m_cluster", "u_gills", "sp_white", "s_no_ring")))
        assertTrue(FungusCriteria.matches(look("perlatum"), setOf("c_ball", "u_none", "cap_WHITE", "s_no_ring", "s_no_volva")))
        assertTrue(FungusCriteria.matches(look("citrinum"), setOf("c_ball", "u_none", "cap_YELLOW", "c_scaly")))
        // l'œuf d'amanite est enfermé dans sa volve : sur « sans volve » il ne doit jamais sortir
        assertTrue(FungusCriteria.matches(look("egg"), setOf("c_ball", "s_volva", "sp_white")))
        assertFalse(FungusCriteria.matches(look("egg"), setOf("s_no_volva")))
        assertTrue(FungusCriteria.matches(look("pardinum"), setOf("c_scaly", "cap_GREY", "u_gills", "sp_white", "s_no_ring", "s_no_volva")))
        assertTrue(FungusCriteria.matches(look("terreum"), setOf("cap_GREY", "u_gills", "sp_white", "s_no_ring", "s_no_volva")))
        assertTrue(FungusCriteria.matches(look("silvicola"), setOf("cap_WHITE", "s_ring", "s_no_volva", "sp_brown", "m_stains")))
        assertTrue(FungusCriteria.matches(look("granulatus"), setOf("u_pores", "s_no_ring", "s_no_volva")))
        assertTrue(FungusCriteria.matches(look("olivacea"), setOf("u_gills", "s_no_ring", "s_no_volva", "w_medium")))
    }

    @Test
    fun leLotCinqSeLitDansLeDessin() {
        assertTrue(FungusCriteria.matches(look("ovoidea"), setOf("cap_WHITE", "s_ring", "s_volva", "u_gills", "w_large", "sp_white")))
        assertTrue(FungusCriteria.matches(look("geotropa"), setOf("c_funnel", "u_gills", "s_no_ring", "w_large", "sp_white")))
        assertTrue(FungusCriteria.matches(look("amethystina"), setOf("cap_PINK", "w_small", "u_gills", "s_no_ring", "sp_white")))
        assertTrue(FungusCriteria.matches(look("pura"), setOf("cap_PINK", "w_small", "u_gills", "s_no_ring", "sp_white")))
        assertTrue(FungusCriteria.matches(look("calopus"), setOf("u_pores", "stipe_YELLOW", "stipe_RED", "m_stains", "w_large")))
        assertTrue(FungusCriteria.matches(look("brunneum"), setOf("c_scaly", "s_ring", "s_no_volva", "m_stains", "w_medium", "u_gills")))
        assertTrue(FungusCriteria.matches(look("rhacodes"), setOf("c_scaly", "s_ring", "m_stains", "w_large", "u_gills")))
        assertTrue(FungusCriteria.matches(look("emetica"), setOf("cap_RED", "s_no_ring", "s_no_volva", "u_gills", "w_medium", "sp_white")))
        assertTrue(FungusCriteria.matches(look("cyanoxantha"), setOf("u_gills", "s_no_ring", "w_medium", "sp_white")))
        assertTrue(FungusCriteria.matches(look("hebeloma"), setOf("w_medium", "u_gills", "s_no_ring", "sp_brown")))
        assertTrue(FungusCriteria.matches(look("aegerita"), setOf("m_cluster", "s_ring", "u_gills", "sp_brown")))
        assertTrue(FungusCriteria.matches(look("tubaeformis"), setOf("c_funnel", "m_cluster", "u_ridges", "stipe_YELLOW", "w_small")))
        assertTrue(FungusCriteria.matches(look("gigantea"), setOf("c_ball", "u_none", "cap_WHITE", "w_large", "s_no_ring")))
        // seul l'amanite ovoïde du lot a une volve : rien d'autre ne sort sur « Volve » parmi les nouveaux
        for (id in listOf("geotropa", "amethystina", "pura", "calopus", "brunneum", "rhacodes", "emetica", "cyanoxantha", "hebeloma", "aegerita", "tubaeformis", "gigantea"))
            assertFalse("$id a une volve", FungusCriteria.matches(look(id), setOf("s_volva")))
    }

    @Test
    fun ouDansUneLigneEtEntreLesLignes() {
        val brown = ids("cap_BROWN")
        val yellow = ids("cap_YELLOW")
        assertEquals("ou dans une ligne", brown + yellow, ids("cap_BROWN", "cap_YELLOW"))
        val ring = ids("s_ring")
        assertEquals("et entre les lignes", brown.intersect(ring), ids("cap_BROWN", "s_ring"))
        assertEquals("avec / sans anneau ensemble = tout le monde", FungusCatalog.species.size, ids("s_ring", "s_no_ring").size)
        assertEquals("un critère inconnu ne restreint rien", FungusCatalog.species.size, ids("n_importe_quoi").size)
    }

    @Test
    fun lesBoutonsProposesTrouventTousQuelqueChose() {
        for ((_, list) in FungusCriteria.offered) for (c in list) {
            assertTrue("${c.id} ne trouve rien", ids(c.id).isNotEmpty())
        }
        // chaque espèce se retrouve par au moins une couleur de chapeau, une de pied et son dessous
        for (sp in FungusCatalog.species) {
            for (group in listOf(CriterionGroup.CAP_COLOR, CriterionGroup.STIPE_COLOR, CriterionGroup.UNDER, CriterionGroup.CAP_WIDTH, CriterionGroup.SPORE)) {
                assertTrue("${sp.id} sans ${group.name}", FungusCriteria.all.any { it.group == group && it.test(sp.look) })
            }
        }
        val all = FungusCriteria.all.map { it.id }
        assertEquals("identifiants en double", all.size, all.toSet().size)
    }

    @Test
    fun lesTeintesSeLisentCommeUnOeil() {
        assertEquals(Tone.WHITE, Tone.of(0xFFF5F3E8.toInt()))
        assertEquals(Tone.RED, Tone.of(0xFFD83822.toInt()))
        assertEquals(Tone.YELLOW, Tone.of(0xFFE8D22E.toInt()))
        assertEquals(Tone.BROWN, Tone.of(0xFF654630.toInt()))
        assertEquals(Tone.BLACK, Tone.of(0xFF101010.toInt()))
    }
}
