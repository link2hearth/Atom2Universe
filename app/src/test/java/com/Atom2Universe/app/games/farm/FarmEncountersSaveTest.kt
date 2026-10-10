package com.Atom2Universe.app.games.farm

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
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
class FarmEncountersSaveTest {
    private lateinit var prefs: SharedPreferences
    private val now = System.currentTimeMillis() + 10_000
    @Before fun setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("farm-encounters-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }
    @Test fun `accueil verse pieces et graines une seule fois et sauvegarde le carnet`() {
        val farm = FarmState(prefs)
        farm.advanceEncounters(now)
        val meeting = farm.encounters.pending!!; val coins = farm.coins; val seeds = farm.seeds[meeting.crop.ordinal]
        assertNotNull(farm.greetVisitor(meeting.id, true, now))
        assertNull(farm.greetVisitor(meeting.id, true, now))
        val restored = FarmState(prefs)
        assertEquals(coins + meeting.coins, restored.coins); assertEquals(seeds + 4, restored.seeds[meeting.crop.ordinal])
        assertNull(restored.encounters.pending); assertEquals(1, restored.encounters.count(meeting.visitor))
        restored.advanceEncounters(now + FarmEncountersState.INTERVAL - 1); assertNull(restored.encounters.pending)
        restored.advanceEncounters(now + FarmEncountersState.INTERVAL); assertNotNull(restored.encounters.pending)
    }
    @Test fun `sac plein ne prend pas la visite et son equivalent en pieces reste accessible`() {
        val farm = FarmState(prefs)
        val meeting = farm.encounters.pending!!
        farm.seeds[meeting.crop.ordinal] = 9998; farm.save()
        val before = prefs.getString(FarmState.KEY_STATE, null); val coins = farm.coins
        assertNull(farm.greetVisitor(meeting.id, true, now)); assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
        val result = farm.greetVisitor(meeting.id, false, now)!!
        assertEquals(0, result.seeds); assertEquals(meeting.coinAlternative, result.coins)
        assertEquals(coins + meeting.coinAlternative, FarmState(prefs).coins)
        assertEquals(9998, farm.seeds[meeting.crop.ordinal])
    }
    @Test fun `migration v14 conserve toute la ferme et une lecture de hub necrit pas`() {
        val farm = FarmState(prefs); farm.cheatAddCoins(2000); farm.unlock(1); farm.cleanForVisit(); farm.plantAll(FarmCrop.RADISH, now)
        val coins = farm.coins; val orders = farm.village.orders; val plots = farm.plots.map { it.copy() }
        val json = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!).put("version", 14); json.remove("encounters")
        prefs.edit().putString(FarmState.KEY_STATE, json.toString()).commit()
        val before = prefs.getString(FarmState.KEY_STATE, null)
        val restored = FarmState(prefs)
        restored.readyToHarvest(now)
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
        assertEquals(coins, restored.coins); assertEquals(orders, restored.village.orders); assertEquals(plots, restored.plots)
        restored.advanceEncounters(now)
        assertEquals(FarmState.MAX_SAVE_VERSION, JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!).getInt("version"))
        assertEquals(restored.encounters.pending, FarmState(prefs).encounters.pending)
    }
    @Test fun `les nouvelles especes dependent de la ferme sans modifier le visiteur present`() {
        val farm = FarmState(prefs); val first = farm.encounters.pending
        assertEquals(setOf(FarmVisitor.ROBIN), farm.availableVisitors())
        assertTrue(farm.startFlower(FarmFlower.DAISY_WHITE, false, farm.greenhouse.revision, now))
        assertTrue(FarmVisitor.BUTTERFLY in farm.availableVisitors())
        farm.cheatAddCoins(2000); farm.unlock(1); farm.changeUse(1, FarmLandUse.ORCHARD); farm.cleanForVisit(1)
        farm.buy(FarmCrop.APPLE, 1); farm.plantAll(FarmCrop.APPLE, now)
        assertTrue(FarmVisitor.SQUIRREL in farm.availableVisitors())
        farm.advanceEncounters(now + 10 * FarmEncountersState.INTERVAL)
        assertEquals(first, farm.encounters.pending)
    }
    @Test fun `retour apres dix jours retrouve les productions et une seule rencontre`() {
        val farm = FarmState(prefs)
        val first = farm.encounters.pending
        farm.cleanForVisit(); farm.plantAll(FarmCrop.RADISH, now); farm.waterMany(farm.visitCells(), now)
        repeat(6) { assertTrue(farm.startFlower(FarmFlower.DAISY_WHITE, false, farm.greenhouse.revision, now)) }
        farm.produce[FarmCrop.RADISH.ordinal][0] = 3
        assertTrue(farm.startPreparation(FarmRecipe.PICKLES, farm.workshop.revision, now))
        farm.cheatAddCoins(5000); farm.livestock.unlock(LivestockKind.CHICKENS)
        assertTrue(farm.buyAnimal(LivestockKind.CHICKENS, false, now))
        val later = now + 10 * FarmEncountersState.INTERVAL
        val restored = FarmState(prefs); restored.advanceEncounters(later)
        assertEquals(first, restored.encounters.pending)
        assertEquals(12, restored.harvestMany(restored.visitCells(), later).count)
        assertEquals(6, restored.collectFlowers(later)); assertEquals(1, restored.collectPreparations(later))
        assertEquals(1, restored.collectAnimalProducts(now = later))
        assertEquals(0, restored.collectFlowers(later)); assertEquals(0, restored.collectPreparations(later))
        assertEquals(0, restored.collectAnimalProducts(now = later))
        assertTrue(restored.greenhouse.jobs.isEmpty()); assertTrue(restored.workshop.jobs.isEmpty())
        assertNotNull(restored.greetVisitor(first!!.id, true, later))
        restored.advanceEncounters(later); assertNull(restored.encounters.pending)
    }
    @Test fun `une visite par jour avance sans tracteur ni optimisation horaire`() {
        val farm = FarmState(prefs)
        farm.cleanForVisit(); farm.plantAll(FarmCrop.RADISH, now); farm.waterMany(farm.visitCells(), now)
        repeat(10) { day ->
            val clock = now + (day + 1) * FarmEncountersState.INTERVAL
            farm.harvestMany(farm.visitCells(), clock)
            repeat(8) { farm.village.orders.firstOrNull { farm.orderReward(it.id) != null }?.let { farm.deliverOrder(it.id) } }
            farm.sellProduce()
            farm.advanceEncounters(clock); farm.encounters.pending?.let { farm.greetVisitor(it.id, true, clock) }
            val next = farm.unlockedParcels
            if (next < farm.parcels.size && farm.coins >= farm.unlockCost(next)) farm.unlock(next)
            farm.cleanForVisit()
            val crop = FarmCrop.ladder.last { farm.cropUnlocked(it) }
            val needed = (farm.emptyCells(false) - farm.seeds[crop.ordinal]).coerceAtLeast(0)
            val buy = minOf(needed, (farm.coins / crop.cost).toInt(), FarmState.MAX_SEED_BATCH)
            if (buy > 0) assertTrue(farm.buy(crop, buy))
            farm.plantAll(crop, clock); farm.waterMany(farm.visitCells(), clock)
        }
        assertTrue("daily parcel progress: ${farm.unlockedParcels}", farm.unlockedParcels >= 3)
        assertTrue(farm.livestockUnlocked()); assertEquals(0, farm.largeFields.cycles)
        assertTrue(farm.coins >= 0); assertTrue(farm.village.deliveries > 0)
    }
    @Test fun `la couche dessinee ne capture pas les clics meme avec plusieurs effets`() {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val context = controller.get()
        val root = FrameLayout(context); var clicked = 0
        root.addView(Button(context).apply { setOnClickListener { clicked++ } }, FrameLayout.LayoutParams(300, 300))
        val layer = FarmActionEffectsView(context)
        layer.elevation = 30f
        root.addView(layer, FrameLayout.LayoutParams(300, 300)); context.setContentView(root); root.layout(0, 0, 300, 300)
        root.getChildAt(0).layout(0, 0, 300, 300); layer.layout(0, 0, 300, 300)
        repeat(20) { layer.emit(FarmActionEffectsView.Kind.entries[it % FarmActionEffectsView.Kind.entries.size], 150f, 150f) }
        layer.draw(Canvas(Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)))
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 150f, 150f, 0)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 150f, 150f, 0)
        root.dispatchTouchEvent(down); root.dispatchTouchEvent(up); down.recycle(); up.recycle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, clicked)
        controller.pause().stop().destroy()
    }
    @Test fun `reset et donnees optionnelles invalides ne touchent pas aux autres systemes`() {
        val farm = FarmState(prefs); farm.cheatAddCoins(2000)
        farm.greetVisitor(farm.encounters.pending!!.id, true, now)
        val json = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!).put("encounters", "broken")
        prefs.edit().putString(FarmState.KEY_STATE, json.toString()).commit()
        val restored = FarmState(prefs)
        assertEquals(farm.coins, restored.coins); assertEquals(farm.village.orders, restored.village.orders)
        restored.cheatReset()
        val reset = FarmState(prefs)
        assertEquals(0, reset.encounters.totalGreetings); assertNotNull(reset.encounters.pending)
        assertEquals(FarmState.STARTING_COINS, reset.coins)
    }
}
