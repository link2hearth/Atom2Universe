package com.Atom2Universe.app.games.physics.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MachineCatalogTest {

    private fun placement(
        definitionId: String,
        id: String,
        x: Float = 0f,
        y: Float = 0f,
        layer: Int = 0,
        depth: Int? = null,
        values: Map<String, Float> = emptyMap()
    ): CatalogPlacement {
        val config = PartConfiguration(MachinePartCatalog.require(definitionId), values)
        return CatalogPlacement(id, config, x, y, primaryLayer = layer,
            layerDepth = depth ?: config.definition.defaultLayerDepth)
    }

    @Test
    fun `le catalogue est vaste stable et sans doublon`() {
        val all = MachinePartCatalog.definitions
        assertTrue("le catalogue n'est pas exhaustif : ${all.size} pièces", all.size >= 120)
        assertEquals("identifiants de pièces dupliqués", all.size, all.map { it.id }.toSet().size)
        assertTrue("une catégorie est vide", PartCategory.entries.all { category ->
            all.any { it.category == category }
        })
        assertNotNull(MachinePartCatalog.find("transmission.spur_gear"))
        assertNotNull(MachinePartCatalog.find("pneumatic.launcher"))
        assertNotNull(MachinePartCatalog.find("hydraulic.waterjet_head"))
    }

    @Test
    fun `tous les parametres par defaut et ports du catalogue sont coherents`() {
        for (definition in MachinePartCatalog.definitions) {
            val config = PartConfiguration(definition)
            for (parameter in definition.parameters) {
                assertTrue("défaut invalide : ${definition.id}.${parameter.key}",
                    parameter.accepts(config[parameter.key]))
            }
            assertEquals("ports dupliqués dans ${definition.id}",
                definition.ports.size, definition.ports.map { it.id }.toSet().size)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `une configuration refuse une dimension negative`() {
        PartConfiguration(
            MachinePartCatalog.require("transmission.spur_gear"),
            mapOf("pitch_radius" to -2f)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `une configuration refuse un parametre inconnu`() {
        PartConfiguration(
            MachinePartCatalog.require("rotary.flywheel"),
            mapOf("magic_power" to 42f)
        )
    }

    @Test
    fun `les familles critiques possedent des ports utilisables`() {
        val required = listOf(
            "transmission.spur_gear" to PortKind.GEAR_TEETH,
            "linear.guide" to PortKind.LINEAR_MECHANICAL,
            "pneumatic.chamber" to PortKind.PNEUMATIC_GAS,
            "hydraulic.accumulator" to PortKind.HYDRAULIC_LIQUID,
            "energy.electric_motor" to PortKind.ELECTRICAL_POWER,
            "control.pid" to PortKind.CONTROL_SIGNAL
        )
        for ((id, kind) in required) {
            assertTrue("$id n'expose pas de port $kind",
                MachinePartCatalog.require(id).ports.any { it.kind == kind })
        }
    }

    @Test
    fun `les couches separees ne collisionnent pas`() {
        val a = placement("structure.block", "a", layer = 0)
        val b = placement("structure.block", "b", layer = 1)
        val thick = placement("structure.frame", "frame", layer = 1, depth = 3)

        assertFalse(MachineLayerSystem.canCollide(a, b))
        assertTrue(MachineLayerSystem.canCollide(b, thick))
        assertFalse(MachineLayerSystem.canCollide(a, thick))
    }

    @Test
    fun `une denture exige une couche commune`() {
        val a = placement("transmission.spur_gear", "gear_a", layer = 4)
        val same = placement("transmission.spur_gear", "gear_b", layer = 4)
        val other = placement("transmission.spur_gear", "gear_c", layer = 5)
        val portA = PortEndpoint(a, "teeth")

        assertTrue(MachineLayerSystem.canConnect(portA, PortEndpoint(same, "teeth")).compatible)
        assertFalse(MachineLayerSystem.canConnect(portA, PortEndpoint(other, "teeth")).compatible)
    }

    @Test
    fun `un arbre et une entretoise peuvent franchir les couches`() {
        val shaft = placement("rotary.solid_shaft", "shaft", layer = -10)
        val spacer = placement("structure.spacer", "spacer", layer = -9)
        val farShaft = placement("rotary.solid_shaft", "far", layer = 100)

        assertTrue(MachineLayerSystem.canConnect(
            PortEndpoint(shaft, "end_a"), PortEndpoint(spacer, "side_a")).compatible)
        // Les deux arbres déclarent explicitement un passage libre entre couches.
        assertTrue(MachineLayerSystem.canConnect(
            PortEndpoint(shaft, "end_b"), PortEndpoint(farShaft, "end_a")).compatible)
    }

    @Test
    fun `un tuyau dair ne se branche jamais sur de lhuile`() {
        val air = placement("pneumatic.pipe", "air", layer = 0)
        val oil = placement("hydraulic.rigid_pipe", "oil", layer = 0)
        val verdict = MachineLayerSystem.canConnect(
            PortEndpoint(air, "a"), PortEndpoint(oil, "a"))

        assertFalse(verdict.compatible)
        assertTrue(verdict.reason.contains("incompatibles"))
    }

    @Test
    fun `deux engrenages geants de meme module sengrenent`() {
        val aPlacement = placement("transmission.spur_gear", "giant_a", x = 0f, layer = 7,
            values = mapOf("pitch_radius" to 250f, "teeth" to 2_000f))
        val bPlacement = placement("transmission.spur_gear", "giant_b", x = 375f, layer = 7,
            values = mapOf("pitch_radius" to 125f, "teeth" to 1_000f))
        val verdict = GearLayerMeshing.evaluate(
            CatalogGear(aPlacement, 250f, 2_000),
            CatalogGear(bPlacement, 125f, 1_000)
        )

        assertTrue(verdict.reason, verdict.compatible)
        assertEquals(2f, verdict.ratio, 1e-6f)
        assertEquals(375f, verdict.expectedCenterDistance, 1e-4f)
    }

    @Test
    fun `un engrenage refuse mauvais module entraxe ou couche`() {
        val base = placement("transmission.spur_gear", "base", x = 0f, layer = 2)
        val wrongModule = placement("transmission.spur_gear", "module", x = 1f, layer = 2)
        val wrongDistance = placement("transmission.spur_gear", "distance", x = 50f, layer = 2)
        val wrongLayer = placement("transmission.spur_gear", "layer", x = 0.5f, layer = 3)
        val a = CatalogGear(base, 0.25f, 40)

        assertFalse(GearLayerMeshing.evaluate(a, CatalogGear(wrongModule, 0.75f, 40)).compatible)
        assertFalse(GearLayerMeshing.evaluate(a, CatalogGear(wrongDistance, 0.25f, 40)).compatible)
        assertFalse(GearLayerMeshing.evaluate(a, CatalogGear(wrongLayer, 0.25f, 40)).compatible)
    }

    @Test
    fun `un engrenage epais peut toucher une denture de couche voisine`() {
        val thick = placement("transmission.spur_gear", "thick", x = 0f, layer = 0, depth = 2)
        val neighbour = placement("transmission.spur_gear", "neighbour", x = 0.5f, layer = 1)
        val verdict = GearLayerMeshing.evaluate(
            CatalogGear(thick, 0.25f, 40),
            CatalogGear(neighbour, 0.25f, 40)
        )
        assertTrue(verdict.reason, verdict.compatible)
    }
}

