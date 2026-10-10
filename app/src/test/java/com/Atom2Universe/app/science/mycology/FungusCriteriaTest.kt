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
