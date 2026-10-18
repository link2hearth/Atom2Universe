package com.Atom2Universe.app.games.farm

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FarmVisitTest {
    private lateinit var prefs: SharedPreferences
    private val start = 1_000_000L

    @Before
    fun setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("farm-visit-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun `une nouvelle ferme peut livrer son premier panier en une minute sans achat`() {
        val farm = FarmState(prefs)
        val coins = farm.coins
        assertEquals(12, farm.seeds[FarmCrop.RADISH.ordinal])
        assertEquals(12, farm.cleanForVisit())
        assertEquals(12, farm.plantAll(FarmCrop.RADISH, start))
        assertEquals(coins, farm.coins)
        assertEquals(12, farm.waterMany(farm.visitCells(), start).size)
        assertEquals(0, farm.visitSummary(now = start + 59_000).ready)
        assertEquals(12, farm.visitSummary(now = start + 60_000).ready)
        assertEquals(12, farm.harvestMany(farm.visitCells(), start + 60_000).count)
        val delivery = farm.deliverOrder(farm.village.orders.first().id)
        assertNotNull(delivery)
        assertTrue(farm.coins > coins)
        assertEquals(1, farm.village.deliveries)
        assertEquals(0, farm.welcomePlantings)
    }

    @Test
    fun `la pousse rapide s arrete au quota et ne se recharge pas apres fermeture`() {
        val farm = FarmState(prefs)
        farm.cleanForVisit(); farm.plantAll(FarmCrop.RADISH, start)
        farm.waterMany(farm.visitCells(), start)
        farm.harvestMany(farm.visitCells(), start + 60_000)
        farm.deliverOrder(farm.village.orders.first().id)
        val restored = FarmState(prefs)
        assertEquals(0, restored.welcomePlantings)
        assertTrue(restored.buy(FarmCrop.RADISH, 6))
        assertEquals(6, restored.plantAll(FarmCrop.RADISH, start + 60_000))
        restored.waterMany(restored.visitCells(), start + 60_000)
        assertEquals(0, restored.visitSummary(now = start + 120_000).ready)
        assertTrue(restored.plots.filter { it.crop != null }.all { it.duration() == FarmCrop.RADISH.seconds })
    }

    @Test
    fun `le quota et les plants rapides survivent a une absence de dix jours`() {
        val farm = FarmState(prefs)
        farm.cleanForVisit()
        repeat(5) { farm.plant(it, start) }
        farm.waterMany(farm.visitCells(), start)
        val restored = FarmState(prefs)
        assertEquals(7, restored.welcomePlantings)
        assertEquals(7, restored.seeds[FarmCrop.RADISH.ordinal])
        assertEquals(5, restored.visitSummary(now = start + 10 * 86_400_000L).ready)
        assertEquals(5, restored.harvestMany(restored.visitCells(), start + 10 * 86_400_000L).count)
        assertEquals(7, FarmState(prefs).welcomePlantings)
    }

    @Test
    fun `une ferme v8 garde ses plants normaux et ses liens sans cadeaux repetes`() {
        val farm = FarmState(prefs)
        farm.cleanForVisit(); farm.plant(0, start); farm.waterMany(listOf(0), start)
        val order = farm.village.orders.first()
        order.items.forEach { farm.produce[it.crop.ordinal][0] = it.count }
        farm.deliverOrder(order.id)
        val village = farm.village.toJson().toString()
        val legacy = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        legacy.put("version", 8); legacy.remove("welcomePlantings")
        val plots = legacy.getJSONArray("plots")
        for (i in 0 until plots.length()) plots.getJSONObject(i).remove("welcome")
        prefs.edit().putString(FarmState.KEY_STATE, legacy.toString()).commit()
        val restored = FarmState(prefs)
        assertEquals(farm.coins, restored.coins)
        assertArrayEquals(farm.seeds, restored.seeds)
        assertEquals(village, restored.village.toJson().toString())
        assertEquals(0, restored.welcomePlantings)
        assertEquals(start, restored.plots[0].planted)
        assertTrue(restored.plots[0].watered)
        assertEquals(FarmCrop.RADISH.seconds, restored.plots[0].duration())
        assertEquals(0, restored.visitSummary(now = start + 60_000).ready)
        restored.save()
        assertEquals(0, FarmState(prefs).welcomePlantings)
    }

    @Test
    fun `les actions de parcelle ne touchent ni les voisins ni les terrains verrouilles`() {
        val farm = FarmState(prefs)
        farm.cheatAddCoins(2000); assertTrue(farm.unlock(1))
        farm.buy(FarmCrop.RADISH, 12)
        val locked = FarmLayout.cells(2).map { farm.plots[it].copy() }
        assertEquals(0, farm.cleanForVisit(2))
        assertEquals(0, farm.plantAll(FarmCrop.RADISH, start, 2))
        assertEquals(12, farm.cleanForVisit(0))
        assertEquals(12, farm.plantAll(FarmCrop.RADISH, start, 0))
        assertEquals(12, farm.cleanForVisit(1))
        assertEquals(12, farm.plantAll(FarmCrop.RADISH, start, 1))
        assertEquals(12, farm.waterMany(farm.visitCells(0), start).size)
        assertEquals(12, farm.visitSummary(1, start).thirsty)
        assertEquals(12, farm.harvestMany(farm.visitCells(0), start + 60_000).count)
        assertEquals(12, farm.visitSummary(1, start + 60_000).thirsty)
        assertEquals(locked, FarmLayout.cells(2).map { farm.plots[it].copy() })
        assertTrue(farm.visitCells(-1).isEmpty())
        assertTrue(farm.visitCells(999).isEmpty())
    }

    @Test
    fun `les gestes groupes sont gratuits et la simple eau ne peut pas etre relancee pour la qualite`() {
        val farm = FarmState(prefs)
        farm.cleanForVisit(); farm.plantAll(FarmCrop.RADISH, start)
        assertEquals(0, farm.wateringLevel)
        assertEquals(0, farm.harvestLevel)
        assertEquals(farm.visitCells(0), farm.wateringTargets(0))
        assertEquals(farm.visitCells(0), farm.harvestTargets(0))
        assertEquals(12, farm.waterMany(farm.wateringTargets(0), start).size)
        val before = farm.plots.map { it.copy() }
        assertTrue(farm.waterMany(farm.wateringTargets(0), start + 30_000, careful = true).isEmpty())
        assertEquals(before, farm.plots.map { it.copy() })
        assertEquals(10L, farm.coins)
    }

    @Test
    fun `l arrosage soigne ajoute seulement dix points de chance a chaque niveau`() {
        val farm = FarmState(prefs)
        farm.cheatAddCoins(300_000)
        repeat(3) { level ->
            assertEquals(farm.criticalChance(), farm.wateringQualityChance(false))
            assertEquals(farm.criticalChance() + 10, farm.wateringQualityChance(true))
            assertTrue(farm.wateringQualityChance(true) <= 40)
            if (level < 2) assertTrue(farm.upgradeFertilizer())
        }
    }

    @Test
    fun `le secours de graines est gratuit et refuse si des graines ou une recolte sont disponibles`() {
        val farm = FarmState(prefs)
        assertFalse(farm.claimSeedHelp())
        farm.buy(FarmCrop.RADISH, 5) // spend the ten starting coins
        farm.seeds.fill(0)
        farm.produce[FarmCrop.RADISH.ordinal][0] = 1
        assertFalse(farm.claimSeedHelp())
        farm.produce[FarmCrop.RADISH.ordinal][0] = 0
        assertTrue(farm.claimSeedHelp())
        assertEquals(0L, farm.coins)
        assertEquals(6, farm.seeds[FarmCrop.RADISH.ordinal])
        assertFalse(farm.claimSeedHelp())
        assertEquals(6, FarmState(prefs).seeds[FarmCrop.RADISH.ordinal])
    }

    @Test
    fun `les poules deviennent accessibles apres deux recoltes de parcelle sans tracteur`() {
        val farm = FarmState(prefs)
        farm.cheatAddCoins(1000)
        farm.cleanForVisit(); farm.plantAll(FarmCrop.RADISH, start)
        farm.waterMany(farm.visitCells(), start)
        farm.harvestMany(farm.visitCells(), start + 60_000)
        assertFalse(farm.livestockUnlocked())
        farm.buy(FarmCrop.RADISH, 12); farm.plantAll(FarmCrop.RADISH, start + 60_000)
        farm.waterMany(farm.visitCells(), start + 60_000)
        farm.harvestMany(farm.visitCells(), start + 60_000 + FarmCrop.RADISH.seconds * 1000L)
        assertEquals(24, farm.harvests)
        assertEquals(0, farm.largeFields.cycles)
        assertTrue(farm.livestockUnlocked())
        val coins = farm.coins
        assertTrue(farm.unlockLivestock(LivestockKind.CHICKENS))
        assertTrue(farm.buyAnimal(LivestockKind.CHICKENS, male = false))
        assertEquals(coins - 360, farm.coins)
        val restored = FarmState(prefs)
        assertTrue(restored.livestockUnlocked())
        assertEquals(1, restored.livestock.animals.size)
    }

    @Test
    fun `annuler un plant d introduction non arrose rend son quota et le reset reste accueillant`() {
        val farm = FarmState(prefs)
        farm.clean(0); farm.plant(0, start)
        assertEquals(11, farm.welcomePlantings)
        farm.clear(0)
        assertEquals(12, farm.welcomePlantings)
        farm.plant(0, start); farm.waterMany(listOf(0), start); farm.clear(0)
        assertEquals(11, farm.welcomePlantings)
        farm.cheatReset()
        assertEquals(12, farm.welcomePlantings)
        assertEquals(12, farm.seeds[FarmCrop.RADISH.ordinal])
        assertTrue(farm.plots.none { it.welcome })
        assertEquals(12, FarmState(prefs).welcomePlantings)
    }
}
