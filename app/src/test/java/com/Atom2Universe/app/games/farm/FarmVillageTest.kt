package com.Atom2Universe.app.games.farm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FarmVillageTest {
    @Test
    fun `les demandes restent accessibles a tous les paliers et apres remplacement`() {
        for (parcels in 1..FarmCrop.ladder.size) {
            val village = FarmVillageState().apply { ensureOrders(parcels) }
            repeat(60) {
                assertEquals(FarmVillager.entries.toSet(), village.orders.map { it.villager }.toSet())
                assertEquals(3, village.orders.map { it.id }.distinct().size)
                village.orders.forEach { order ->
                    assertTrue(order.items.all { it.crop.rank in 1..parcels })
                    assertTrue(order.items.sumOf { it.count } <= 8)
                    assertEquals(order.items.size, order.items.map { it.crop }.distinct().size)
                }
                val order = village.orders[it % 3]
                assertTrue(village.replace(order.id, parcels))
            }
        }
    }

    @Test
    fun `les anciennes cultures reviennent dans les demandes`() {
        val village = FarmVillageState().apply { ensureOrders(FarmCrop.ladder.size) }
        val seen = mutableSetOf<FarmCrop>()
        repeat(60) {
            val order = village.orders.first()
            seen += order.items.map { it.crop }
            village.replace(order.id, FarmCrop.ladder.size)
        }
        assertEquals(FarmCrop.ladder.toSet(), seen)
    }

    @Test
    fun `chaque voisin renouvelle son dialogue meme en livrant les trois paniers dans le meme ordre`() {
        val village = FarmVillageState().apply { ensureOrders(4) }
        repeat(6) {
            val previous = village.orders
            previous.forEach { village.complete(it.id, 4) }
            previous.zip(village.orders).forEach { (old, next) ->
                assertNotEquals(old.story, next.story)
            }
        }
    }

    @Test
    fun `rouvrir ou agrandir ne renouvelle pas les demandes en attente`() {
        val village = FarmVillageState().apply { ensureOrders(3) }
        village.complete(village.orders.first().id, 3)
        val before = village.toJson().toString()
        val restored = FarmVillageState.fromJson(JSONObject(before))
        restored.ensureOrders(23)
        assertEquals(before, restored.toJson().toString())
        assertEquals(1, restored.friendship(FarmVillager.LUCIE))
    }

    @Test
    fun `remplacer ne touche ni les liens ni les autres demandes et invalide le bouton precedent`() {
        val village = FarmVillageState().apply { ensureOrders(4) }
        village.complete(village.orders.first().id, 4)
        val old = village.orders.first()
        val neighbours = village.orders.drop(1)
        assertTrue(village.replace(old.id, 4))
        assertEquals(neighbours, village.orders.drop(1))
        assertEquals(1, village.friendship(old.villager))
        assertNotEquals(old.items.first().crop, village.orders.first().items.first().crop)
        assertFalse(village.replace(old.id, 4))
        assertNull(village.complete(old.id, 4))
        assertEquals(1, village.deliveries)
    }

    @Test
    fun `un panier incomplet ne preleve rien meme si le premier ingredient est disponible`() {
        val order = FarmVillageOrder(1, FarmVillager.LUCIE, 0,
            listOf(FarmOrderItem(FarmCrop.RADISH, 4), FarmOrderItem(FarmCrop.LETTUCE, 2)))
        val produce = Array(FarmCrop.entries.size) { IntArray(FarmCropQuality.entries.size) }
        produce[FarmCrop.RADISH.ordinal][FarmCropQuality.COMMON.ordinal] = 10
        produce[FarmCrop.LETTUCE.ordinal][FarmCropQuality.RARE.ordinal] = 1
        val before = produce.map { it.toList() }
        assertNull(FarmOrderWithdrawal.plan(order, produce))
        assertEquals(before, produce.map { it.toList() })
    }

    @Test
    fun `la qualite est payee en entier et les produits ordinaires partent en premier`() {
        val order = FarmVillageOrder(1, FarmVillager.LUCIE, 0, listOf(FarmOrderItem(FarmCrop.RADISH, 4)))
        val produce = Array(FarmCrop.entries.size) { IntArray(FarmCropQuality.entries.size) }
        produce[FarmCrop.RADISH.ordinal] = intArrayOf(2, 1, 10, 10)
        val plan = FarmOrderWithdrawal.plan(order, produce)!!
        assertEquals(listOf(
            FarmHarvestStack(FarmCrop.RADISH, FarmCropQuality.COMMON, 2),
            FarmHarvestStack(FarmCrop.RADISH, FarmCropQuality.RARE, 1),
            FarmHarvestStack(FarmCrop.RADISH, FarmCropQuality.EPIC, 1)), plan.stacks)
        assertEquals(105L, plan.reward)
        assertArrayEquals(intArrayOf(2, 1, 10, 10), produce[FarmCrop.RADISH.ordinal])
    }

    @Test
    fun `un tableau endommage se regenere sans propager son erreur`() {
        val village = FarmVillageState().apply { ensureOrders(3) }
        val damaged = village.toJson()
        damaged.getJSONArray("orders").getJSONObject(1).put("id", village.orders.first().id)
        val recovered = FarmVillageState.fromJson(damaged).apply { ensureOrders(3) }
        assertEquals(3, recovered.orders.size)
        assertEquals(3, recovered.orders.map { it.id }.distinct().size)
    }

    @Test
    fun `les textes du village sont presents dans les deux langues avec les memes formats`() {
        val resources = java.io.File("src/main/res")
        fun strings(folder: String): Map<String, String> {
            val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(java.io.File(resources, "$folder/strings_farm.xml"))
            val nodes = document.getElementsByTagName("string")
            return (0 until nodes.length).associate { index ->
                val node = nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue to node.textContent
            }
        }
        val en = strings("values")
        val fr = strings("values-fr")
        val keys = en.keys.filter { key ->
            listOf("farm_village_", "farm_villager_", "farm_lucie_", "farm_malo_", "farm_iris_", "farm_visit_", "farm_duration_seconds",
                "farm_project", "farm_gift", "farm_product_", "farm_companion_", "farm_produce_empty", "farm_confirm_sale_all_body",
                "farm_workshop_", "farm_recipe_", "farm_orchard_", "farm_tree_cycle", "farm_greenhouse_", "farm_flower_",
                "farm_action_", "farm_encounter_", "farm_visitor_", "farm_robin_", "farm_butterfly_", "farm_squirrel_", "farm_rabbit_",
                "farm_butcher_", "farm_meat_", "farm_kitchen_", "farm_cookbook_")
                .any { key.startsWith(it) }
        }
        val formats = Regex("%\\d+\\$[ds]|%%")
        keys.forEach { key ->
            assertTrue("Traduction manquante : $key", fr.containsKey(key))
            assertEquals("Format different : $key", formats.findAll(en.getValue(key)).map { it.value }.toList(),
                formats.findAll(fr.getValue(key)).map { it.value }.toList())
        }
    }
}
