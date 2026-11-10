package com.Atom2Universe.app.crypto.clicker

import android.app.Application
import android.os.Looper
import androidx.room.Room
import com.Atom2Universe.app.crypto.MainClickerPreferences
import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AtomShopPurchaseTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val main get() = shadowOf(Looper.getMainLooper())
    private val instance = ClickerDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }
    private lateinit var database: ClickerDatabase

    @Before fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(app, ClickerDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.build()
        instance.set(null, database)
    }

    @After fun closeDatabase() {
        main.idle()
        database.close()
        instance.set(null, null)
    }

    private fun loadGame(atoms: LayeredNumber, fingerLevel: Int = 0): ClickerViewModel {
        runBlocking { ClickerRepository(app).save(ClickerGameState(atoms = atoms, godFingerLevel = fingerLevel)) }
        val game = ClickerViewModel(app)
        main.idle()
        assertEquals(atoms, game.state.value.atoms)
        return game
    }

    @Test fun manualPurchaseRequiresTheEntireBatchAtSuccessiveUnitPrices() {
        val type = FactoryType.PROTON_INJECTOR
        val upgrade = AtomShopUpgrade.PROTON_INJECTOR
        FactoryRepository(app).setCount(type, 3)
        val tenPrices = (3 until 13).fold(LayeredNumber.zero()) { sum, count -> sum.add(FactoryEngine.cost(type, count)) }
        val game = loadGame(tenPrices)
        game.cycleShopQuantity(upgrade)
        assertEquals(tenPrices, game.shopCost(upgrade, game.state.value))
        game.cycleShopQuantity(upgrade)
        game.buyUpgrade(upgrade)
        assertEquals(3, upgrade.level(game.state.value))
        assertEquals(tenPrices, game.state.value.atoms)

        game.cycleShopQuantity(upgrade)
        game.cycleShopQuantity(upgrade)
        game.setShopPlusEnabled(upgrade, false)
        game.buyUpgrade(upgrade)
        assertEquals(13, FactoryRepository(app).getCount(type))
        assertEquals(13, upgrade.level(game.state.value))
        assertTrue(game.state.value.atoms.isZero())
    }

    @Test fun automaticPurchasesUseSelectedRowsQuantitiesAndShopOrderForTies() {
        val upgrade = AtomShopUpgrade.PROTON_ACCELERATOR
        val competing = AtomShopUpgrade.FUSION_REACTOR
        val price = FactoryEngine.batchCost(FactoryType.PROTON_ACCELERATOR, 0, 10)
        MainClickerPreferences.setShopQuantity(app, upgrade.preferenceId, 10)
        MainClickerPreferences.setShopQuantity(app, competing.preferenceId, 10)
        val game = loadGame(price)
        for (row in AtomShopUpgrade.entries) game.setShopPlusEnabled(row, row == upgrade || row == competing)
        assertFalse(game.shopAutoBuyEnabled)
        game.startGameLoop()
        try {
            main.idleFor(1, TimeUnit.SECONDS)
            assertEquals(price, game.state.value.atoms)
            game.shopAutoBuyEnabled = true
            main.idleFor(1, TimeUnit.SECONDS)
            val state = game.state.value
            assertEquals(10, upgrade.level(state))
            assertEquals(0, competing.level(state))
            assertEquals(0, state.godFingerLevel)
            assertEquals(0, state.starCoreLevel)
            assertTrue(state.atoms.isZero())
            assertTrue(MainClickerPreferences.isShopAutoBuyEnabled(app))
        } finally {
            game.stopGameLoop()
        }
    }

    @Test fun autoBuyRecomparesFactoryAndFingerPricesAfterEveryPurchase() {
        val factory = FactoryType.PROTON_ACCELERATOR
        FactoryRepository(app).setCount(factory, 5)
        val balance = LayeredNumber(200_000_000)
        val game = loadGame(balance, fingerLevel = 20)
        for (row in AtomShopUpgrade.entries) {
            game.setShopPlusEnabled(row, row == AtomShopUpgrade.GOD_FINGER || row == AtomShopUpgrade.PROTON_ACCELERATOR)
        }
        game.shopAutoBuyEnabled = true
        game.startGameLoop()
        try {
            main.idleFor(100, TimeUnit.MILLISECONDS)
            // L'usine coûte 44,8 M puis 60,5 M. Au niveau 7 elle passe à 81,7 M,
            // donc le Doigt à 65,4 M devient le prochain achat.
            val remaining = balance.subtract(FactoryEngine.cost(factory, 5))
                .subtract(FactoryEngine.cost(factory, 6)).subtract(ClickerShopEngine.cost(21))
            assertEquals(7, FactoryRepository(app).getCount(factory))
            assertEquals(21, game.state.value.godFingerLevel)
            assertEquals(remaining, game.state.value.atoms)
        } finally {
            game.stopGameLoop()
        }
    }
}
