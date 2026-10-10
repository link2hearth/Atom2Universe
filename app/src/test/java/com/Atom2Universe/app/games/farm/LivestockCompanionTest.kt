package com.Atom2Universe.app.games.farm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LivestockCompanionTest {
    private val start = 1_700_000_000_000L
    private fun hens(count: Int = 1) = LivestockState().apply {
        unlock(LivestockKind.CHICKENS)
        repeat(count) { buy(LivestockKind.CHICKENS, false, start) }
        advance(start)
    }

    @Test fun `sexe espece et age determinent la production`() {
        val expected = mapOf(LivestockKind.CHICKENS to FarmAnimalProduct.EGG, LivestockKind.SHEEP to FarmAnimalProduct.WOOL,
            LivestockKind.PIGS to FarmAnimalProduct.TRUFFLE, LivestockKind.CATTLE to FarmAnimalProduct.MILK)
        expected.forEach { (kind, product) ->
            assertEquals(product, FarmAnimal(1, kind, false, 1).product)
            assertEquals(if (kind in listOf(LivestockKind.SHEEP, LivestockKind.PIGS)) product else null,
                FarmAnimal(2, kind, true, 1).product)
            assertNull(FarmAnimal(3, kind, false, 1, adultAt = start).product)
        }
    }

    @Test fun `la premiere ponte rapide ne se repete pas apres achat ou sauvegarde`() {
        val herd = hens(2)
        assertEquals(start + LivestockState.FIRST_EGG_MILLIS, herd.animals[0].productAt)
        assertEquals(start + LivestockState.DAY, herd.animals[1].productAt)
        assertEquals(0, herd.readyProducts(clock = start + 59_999))
        assertEquals(1, herd.collectProducts(clock = start + 60_000))
        assertEquals(start + 60_000 + LivestockState.DAY, herd.animals[0].productAt)
        val restored = LivestockState().apply { restore(herd.toJson(), start + 60_000) }
        restored.buy(LivestockKind.CHICKENS, false, start + 60_000)
        assertEquals(start + 60_000 + LivestockState.DAY, restored.animals.last().productAt)
        assertFalse(restored.firstEggAvailable)
    }

    @Test fun `une absence longue laisse une production par animal puis repart a la collecte`() {
        val herd = hens(2)
        val due = herd.animals.map { it.productAt }
        val returnAt = start + 10 * LivestockState.DAY
        herd.advance(returnAt)
        assertEquals(due, herd.animals.map { it.productAt })
        assertEquals(2, herd.readyProducts(clock = returnAt))
        assertEquals(2, herd.collectProducts(clock = returnAt))
        assertEquals(2, herd.stock(FarmAnimalProduct.EGG))
        assertEquals(0, herd.collectProducts(clock = returnAt))
        assertTrue(herd.animals.all { it.productAt == returnAt + LivestockState.DAY })
    }

    @Test fun `les quatre enclos se collectent ensemble ou separement`() {
        val herd = LivestockState()
        LivestockKind.entries.forEach { herd.unlock(it); herd.buy(it, false, start) }
        herd.advance(start + 2 * LivestockState.DAY)
        assertEquals(1, herd.collectProducts(LivestockKind.CHICKENS, start + 2 * LivestockState.DAY))
        assertEquals(3, herd.collectProducts(clock = start + 2 * LivestockState.DAY))
        FarmAnimalProduct.entries.forEach { assertEquals(1, herd.stock(it)) }
        assertEquals(1852L, herd.productValue())
    }

    @Test fun `les petits commencent un cycle a leur maturite meme hors ligne`() {
        val herd = hens()
        herd.buy(LivestockKind.CHICKENS, true, start)
        val birth = start + LivestockKind.CHICKENS.cycleMillis
        herd.advance(birth)
        val young = herd.animals.first { !it.adult }
        assertEquals(-1L, young.productAt)
        // Either sex is valid; use a deterministic restored female for this age guard.
        val json = herd.toJson()
        val saved = json.getJSONArray("animals").getJSONObject(2).put("male", false)
        val maturity = saved.getLong("adultAt")
        val restored = LivestockState().apply { restore(json, birth) }
        restored.advance(maturity + LivestockState.DAY)
        val grown = restored.find(young.id)!!
        assertTrue(grown.adult)
        assertEquals(maturity + LivestockState.DAY, grown.productAt)
        assertTrue(restored.readyProducts(clock = maturity + LivestockState.DAY) > 0)
    }

    @Test fun `inventaire plein la production reste sur animal jusqu a une place libre`() {
        val herd = hens()
        val json = herd.toJson().put("products", JSONObject().put("EGG", LivestockState.PRODUCT_CAPACITY))
        val restored = LivestockState().apply { restore(json, start) }
        val readyAt = start + 60_000
        assertEquals(0, restored.collectProducts(clock = readyAt))
        assertEquals(1, restored.readyProducts(clock = readyAt))
        assertEquals(1, restored.takeProduct(FarmAnimalProduct.EGG, 1))
        assertEquals(1, restored.collectProducts(clock = readyAt))
        assertEquals(LivestockState.PRODUCT_CAPACITY, restored.stock(FarmAnimalProduct.EGG))
    }

    @Test fun `caresser reste facultatif et ne degrade jamais les liens`() {
        val herd = hens()
        val animal = herd.animals.single()
        val production = animal.productAt
        assertTrue(herd.pet(animal.id, start))
        assertFalse(herd.pet(animal.id, start + LivestockState.PET_INTERVAL - 1))
        assertTrue(herd.pet(animal.id, start + LivestockState.PET_INTERVAL))
        herd.advance(start + 10 * LivestockState.DAY)
        assertEquals(2, animal.affection)
        assertEquals(production, animal.productAt)
        assertTrue(herd.pet(animal.id, start + 10 * LivestockState.DAY))
        assertEquals(3, animal.affection)
    }

    @Test fun `saluer un enclos ne touche pas les autres animaux`() {
        val herd = hens(3)
        herd.unlock(LivestockKind.SHEEP); herd.buy(LivestockKind.SHEEP, false, start)
        assertEquals(3, herd.petAll(LivestockKind.CHICKENS, start).size)
        assertTrue(herd.petAll(LivestockKind.CHICKENS, start).isEmpty())
        assertEquals(0, herd.animals.last().affection)
    }

    @Test fun `une horloge reculee ne redonne ni produit ni lien`() {
        val herd = hens()
        val animal = herd.animals.single()
        val now = start + LivestockState.DAY
        assertTrue(herd.pet(animal.id, now))
        assertEquals(1, herd.collectProducts(clock = now))
        assertFalse(herd.pet(animal.id, start))
        assertEquals(0, herd.collectProducts(clock = start))
        assertEquals(now + LivestockState.DAY, animal.productAt)
    }

    @Test fun `les noms sont nettoyes sans couper un emoji et persistants`() {
        val herd = hens()
        val animal = herd.animals.single()
        assertTrue(herd.rename(animal.id, "  Belle   Noisette  "))
        assertEquals("Belle Noisette", animal.name)
        val name = "🐔".repeat(30)
        herd.rename(animal.id, name)
        assertEquals("🐔".repeat(24), animal.name)
        herd.pet(animal.id, start)
        val restored = LivestockState().apply { restore(herd.toJson(), start) }
        assertEquals(animal.name, restored.animals.single().name)
        assertEquals(1, restored.animals.single().affection)
        assertFalse(restored.rename(12345, "Nobody"))
        assertTrue(restored.rename(animal.id, ""))
        assertEquals("", restored.animals.single().name)
    }

    @Test fun `la vente cible un animal precis et garde sa production prete`() {
        val herd = hens(2)
        val animal = herd.animals[1]
        herd.rename(animal.id, "Noisette"); herd.pet(animal.id, start)
        assertEquals(animal.kind.sale, herd.sell(animal.id, start + LivestockState.DAY))
        assertEquals(1, herd.stock(FarmAnimalProduct.EGG))
        assertEquals(1, herd.count(LivestockKind.CHICKENS))
        assertNull(herd.find(animal.id))
        assertEquals(0, herd.sell(animal.id, start + LivestockState.DAY))
    }

    @Test fun `vente avec production prete et inventaire plein conserve animal et lien`() {
        val herd = hens()
        val id = herd.animals.single().id
        herd.pet(id, start)
        val json = herd.toJson().put("products", JSONObject().put("EGG", LivestockState.PRODUCT_CAPACITY))
        val restored = LivestockState().apply { restore(json, start) }
        assertEquals(0, restored.sell(id, start + LivestockState.DAY))
        assertEquals(1, restored.find(id)!!.affection)
        assertEquals(1, restored.readyProducts(clock = start + LivestockState.DAY))
    }

    @Test fun `migration ancienne demarre les productions sans toucher aux naissances`() {
        val herd = hens()
        herd.buy(LivestockKind.CHICKENS, true, start)
        val json = herd.toJson()
        json.remove("productionVersion"); json.remove("products"); json.remove("firstEggStarted")
        val animals = json.getJSONArray("animals")
        for (i in 0 until animals.length()) animals.getJSONObject(i).apply {
            remove("name"); remove("affection"); remove("lastPetAt"); remove("productAt")
        }
        val restored = LivestockState().apply { restore(json, start) }
        assertEquals(herd.animals.map { it.birthAt }, restored.animals.map { it.birthAt })
        assertEquals(start + LivestockState.DAY, restored.animals.first().productAt)
        assertEquals(-1L, restored.animals.last().productAt)
        assertEquals(0, restored.productCount())
        assertFalse(restored.firstEggAvailable)
    }

    @Test fun `le reset efface identites stocks et introduction`() {
        val herd = hens()
        herd.rename(herd.animals.single().id, "Noisette")
        herd.collectProducts(clock = start + LivestockState.DAY)
        herd.reset()
        assertEquals(0, herd.productCount())
        assertTrue(herd.animals.isEmpty())
        assertTrue(herd.firstEggAvailable)
    }
}
