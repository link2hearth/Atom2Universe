package com.Atom2Universe.app.games.mahjong

import android.app.Application
import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class MahjongStoreTest {
    private val prefs get()=RuntimeEnvironment.getApplication().getSharedPreferences("mahjong_garden",Context.MODE_PRIVATE)
    @Before fun reset() { prefs.edit().clear().commit() }

    @Test fun preparingABoardDoesNotPublishItUntilActivation() {
        val store=MahjongStore(prefs)
        val empty=prefs.all.toMap()
        val first=store.prepareRun("CLASSIC",21312L)
        assertEquals(empty,prefs.all)
        assertNull(store.load("CLASSIC"))
        assertNull(store.summary("CLASSIC"))
        store.activate(first)
        val saved=prefs.all.toMap()
        val replacement=store.prepareRun("CLASSIC",21313L,fresh=true)
        assertNotEquals(first.id,replacement.id)
        assertEquals(saved,prefs.all)
        assertEquals(first.id,store.load("CLASSIC")!!.id)
        store.activate(replacement)
        assertEquals(replacement.id,store.load("CLASSIC")!!.id)
        assertEquals(MahjongStore.Summary(replacement.game.tiles.size,replacement.game.remaining),store.summary("CLASSIC"))
    }

    @Test fun restoringKeepsARewardedBoardWhileOrdinaryPreparationStartsTheNextOne() {
        val store=MahjongStore(prefs)
        val completed=store.newRun("CLASSIC",21312L)
        finish(completed.game)
        store.save(completed)
        assertEquals(completed.id,store.prepareRun("CLASSIC",21313L).id)
        val reward=store.finish(completed)!!
        val before=prefs.all.toMap()
        val restored=store.prepareRun("CLASSIC",21313L,restoring=true)
        assertEquals(completed.id,restored.id)
        assertEquals(reward,restored.reward)
        assertTrue(restored.game.complete)
        assertEquals(completed.id,store.prepareRun("CLASSIC",21313L,fresh=true,restoring=true).id)
        assertNotEquals(completed.id,store.prepareRun("CLASSIC",21313L).id)
        assertEquals(before,prefs.all)
    }

    @Test fun summariesFollowAcceptedSavesAndIgnoreStaleRuns() {
        val store=MahjongStore(prefs)
        val old=store.newRun("CLASSIC",21312L)
        old.game.hint().forEach { old.game.tap(it) }
        store.save(old)
        assertEquals(MahjongStore.Summary(old.game.tiles.size,old.game.remaining),store.summary("CLASSIC"))
        val active=store.newRun("CLASSIC",21312L)
        val stale=MahjongSave.decode(MahjongSave.encode(active))!!
        store.save(old)
        assertEquals(MahjongStore.Summary(active.game.tiles.size,active.game.tiles.size),store.summary("CLASSIC"))
        finish(active.game)
        store.finish(active)
        store.save(stale)
        assertEquals(MahjongStore.Summary(active.game.tiles.size,0),store.summary("CLASSIC"))
        assertEquals(store.summary("CLASSIC"),MahjongStore(prefs).summary("CLASSIC"))
    }

    @Test fun legacySummariesCountSavedTilesWithoutMigratingTheMemoryBoard() {
        val store=MahjongStore(prefs)
        val game=MahjongGame(MahjongMode.CLASSIC,99,5)
        game.restore(game.snapshot().copy(tiles=List(36) { id ->
            MahjongTile(id,id%6,id/6,0,id/2,removed=id<2)
        }))
        val raw=JSONObject(MahjongSave.encode(game,true,false,0L,id="legacy-memory"))
            .put("version",2).put("mode","MEMORY").toString()
        prefs.edit().putString("run_journey",raw).putString("current_journey","legacy-memory").commit()
        // Loading this old board would replace Memory with a 32-tile ice board.
        assertEquals(MahjongStore.Summary(36,34),store.summary("journey"))
        assertEquals(raw,prefs.getString("run_journey",null))
        assertEquals("legacy-memory",prefs.getString("current_journey",null))
        val cached=prefs.all.toMap()
        assertEquals(MahjongStore.Summary(36,34),store.summary("journey"))
        assertEquals(cached,prefs.all)
    }

    @Test fun summariesRefreshIfTheStoredPayloadChangesWithoutMetadata() {
        val store=MahjongStore(prefs)
        val run=store.newRun("CLASSIC",21312L)
        val raw=JSONObject(prefs.getString("run_CLASSIC",null)!!)
        val tiles=raw.getJSONObject("state").getJSONArray("tiles")
        tiles.getJSONArray(0).put(5,1)
        tiles.getJSONArray(1).put(5,1)
        prefs.edit().putString("run_CLASSIC",raw.toString()).commit()
        assertEquals(MahjongStore.Summary(run.game.tiles.size,run.game.tiles.size-2),store.summary("CLASSIC"))
        prefs.edit().remove("run_CLASSIC").commit()
        assertNull(store.summary("CLASSIC"))
        prefs.edit().putString("run_CLASSIC","broken").commit()
        assertNull(store.summary("CLASSIC"))
    }

    @Test fun allEightSlotsKeepTheirBoardsTraysAndUndoAcrossRecreation() {
        val store=MahjongStore(prefs)
        val expected=mutableMapOf<String,MahjongSnapshot>()
        val ids=mutableMapOf<String,String>()
        for(slot in MahjongMode.entries.map { it.name }+listOf("journey","daily")) {
            val run=store.resumeOrCreate(slot,21311L)
            if(run.game.mode==MahjongMode.SLIDE) {
                val directions=listOf(1 to 0,0 to 1,-1 to 0,0 to -1)
                val move=run.game.board.firstNotNullOf { tile ->
                    directions.firstNotNullOfOrNull { (dx,dy) -> run.game.iceSlideTarget(tile.id,dx,dy) }
                }
                run.game.slide(move.id,move.x,move.y)
            } else {
                val selection=if(run.game.mode==MahjongMode.TRIPLE) run.game.solution.first().take(2) else run.game.hint()
                selection.forEach { run.game.tap(it) }
            }
            store.save(run)
            expected[slot]=run.game.snapshot(); ids[slot]=run.id
        }
        val reopened=MahjongStore(prefs)
        expected.forEach { (slot,snapshot) ->
            val restored=reopened.resumeOrCreate(slot,21312L)
            assertEquals(slot,ids[slot],restored.id)
            assertEquals(slot,snapshot,restored.game.snapshot())
            assertTrue(slot,restored.game.history.isNotEmpty())
        }
        assertEquals(8,ids.values.toSet().size)
    }

    @Test fun migrationPreservesTheLegacyRunAndAlreadyEarnedCollection() {
        val game=MahjongGame(MahjongMode.TRIPLE,99,5)
        game.solution.first().take(2).forEach { game.tap(it) }
        val legacy=JSONObject(MahjongSave.encode(game,false,false,0L)).put("version",1).apply { remove("id") }
        prefs.edit().putString("run",legacy.toString()).putInt("wins",5).putInt("pearls",500).putInt("theme",2).commit()
        val store=MahjongStore(prefs)
        assertEquals(game.snapshot(),store.load("TRIPLE")!!.game.snapshot())
        assertEquals(500,store.pearls); assertEquals(5,store.wins); assertEquals(4,store.style)
        assertTrue(store.unlocked.containsAll(listOf(0,2,4,6)))
        assertFalse(prefs.contains("run"))
        val id=store.load("TRIPLE")!!.id
        assertEquals(id,MahjongStore(prefs).load("TRIPLE")!!.id)
    }

    @Test fun victoriesEarnPearlsWithoutAutomaticUnlocksAndReceiptsSurviveStaleSaves() {
        val store=MahjongStore(prefs)
        val received=store.unlocked.toMutableSet()
        repeat(24) { index ->
            val run=store.newRun("CLASSIC",0L)
            val stale=MahjongSave.decode(MahjongSave.encode(run))!!
            finish(run.game)
            val reward=store.finish(run)!!
            assertEquals(index+1,store.wins)
            assertEquals((index+1)%5==0,reward.gift)
            for(style in reward.styles) assertTrue("Duplicate style $style",received.add(style))
            assertTrue(reward.styles.isEmpty())
            val pearls=store.pearls
            assertEquals(reward,store.finish(run))
            assertEquals(pearls,store.pearls)
            store.save(stale)
            assertTrue(store.load("CLASSIC")!!.game.complete)
            assertEquals(reward,store.load("CLASSIC")!!.reward)
        }
        assertEquals(3,received.size)
        assertEquals(received,store.unlocked)
        val old=store.load("CLASSIC")!!
        val next=store.newRun("CLASSIC",0L)
        store.save(old)
        assertEquals(next.id,store.load("CLASSIC")!!.id)
        assertNull(store.finish(old))
    }

    @Test fun dailyRolloverKeepsTheUnfinishedBoardAndBonusIsOncePerDate() {
        val store=MahjongStore(prefs)
        val old=store.newRun("daily",21312L) // classic, for a reproducible clearing witness
        val same=store.resumeOrCreate("daily",21313L)
        assertEquals(old.id,same.id); assertEquals(21312L,same.day)
        finish(old.game)
        val first=store.finish(old)!!
        val replay=store.newRun("daily",21312L); finish(replay.game)
        val second=store.finish(replay)!!
        assertEquals(40,first.pearls-second.pearls)
        val tomorrow=store.resumeOrCreate("daily",21313L)
        assertEquals(21313L,tomorrow.day)
        assertEquals(MahjongMode.TRIPLE,tomorrow.game.mode)
    }

    @Test fun purchasesSpendPearlsOnlyOnceAndCanChooseAnyDesign() {
        val store=MahjongStore(prefs)
        assertEquals(350,store.nextPrice)
        assertFalse(store.purchase(23))
        assertEquals(3,store.unlocked.size)
        prefs.edit().putInt("pearls",1000).commit()
        assertTrue(store.purchase(23))
        assertEquals(650,store.pearls)
        assertEquals(23,store.style)
        assertEquals(462,store.nextPrice)
        assertFalse(store.purchase(23))
        assertEquals(650,store.pearls)
        assertFalse(store.purchase(-1)); assertFalse(store.purchase(24))
        assertTrue(store.purchase(7))
        assertEquals(188,store.pearls)
        assertEquals(598,store.nextPrice)
        assertFalse(store.purchase(8))
        val reopened=MahjongStore(prefs)
        assertEquals(188,reopened.pearls); assertEquals(7,reopened.style)
        assertTrue(reopened.unlocked.containsAll(listOf(0,1,2,7,23)))
    }

    @Test fun legacyCollectionIsKeptWithoutRaisingPurchasePrices() {
        prefs.edit().putBoolean("independent_saves_v2",true)
            .putStringSet("porcelains",setOf("0","1","2","8","23"))
            .putInt("porcelain",23).putInt("pearls",1000).commit()
        val store=MahjongStore(prefs)
        assertEquals(5,store.unlocked.size); assertEquals(23,store.style)
        assertEquals(350,store.nextPrice)
        assertTrue(store.purchase(4)); assertEquals(650,store.pearls)
    }

    @Test fun calendarBonusIsLockedWhenStartingAndSurvivesSave() {
        val monday=java.time.LocalDate.of(2026,10,26).toEpochDay()
        val saturday=java.time.LocalDate.of(2026,10,24).toEpochDay()
        for(mode in MahjongMode.entries) {
            assertEquals(2,MahjongCollection.multiplier(mode,saturday))
            assertEquals(if(mode==MahjongCollection.featuredMode(monday)) 2 else 1,
                MahjongCollection.multiplier(mode,monday))
        }
        val store=MahjongStore(prefs)
        val run=store.newRun("CLASSIC",saturday)
        assertEquals(2,store.resumeOrCreate("CLASSIC",monday).bonusMultiplier)
        finish(run.game)
        val expected=(30+run.game.tiles.size/3+MahjongCollection.comboBonus(run.game))*2
        assertEquals(expected,store.finish(run)!!.pearls)
        assertEquals(expected,store.pearls)
        assertEquals(2,store.load("CLASSIC")!!.bonusMultiplier)
    }

    @Test fun texturesHaveIndependentPurchasesAndSelections() {
        val store=MahjongStore(prefs)
        assertFalse(store.purchaseTexture(23))
        prefs.edit().putInt("pearls",2000).commit()
        assertTrue(store.purchaseTexture(23))
        assertEquals(1650,store.pearls)
        assertEquals(23,store.texture); assertEquals(0,store.style)
        assertEquals(462,store.nextTexturePrice); assertEquals(350,store.nextPrice)
        assertFalse(store.purchaseTexture(23)); assertFalse(store.purchaseTexture(-1))
        assertFalse(store.purchaseTexture(24)); assertEquals(1650,store.pearls)
        assertTrue(store.purchase(7)); assertEquals(1300,store.pearls)
        assertEquals(23,store.texture); assertEquals(7,store.style)
        assertFalse(7 in store.unlockedTextures)
        store.equipTexture(4); assertEquals(23,store.texture)
        store.equipTexture(1); assertEquals(1,store.texture)
        val reopened=MahjongStore(prefs)
        assertEquals(1,reopened.texture); assertEquals(7,reopened.style)
        assertEquals(462,reopened.nextTexturePrice)
        assertFalse(7 in reopened.unlockedTextures)
    }

    @Test fun separatingTexturesPreservesMaterialsIncludedInPreviousPurchasesOnlyOnce() {
        prefs.edit().putBoolean("independent_saves_v2",true)
            .putStringSet("porcelains",setOf("0","1","2","8"))
            .putInt("porcelain",8).putInt("pearls",1000).commit()
        val store=MahjongStore(prefs)
        assertEquals(store.unlocked,store.unlockedTextures)
        assertEquals(8,store.texture); assertEquals(1000,store.pearls)
        assertEquals(350,store.nextTexturePrice)
        assertTrue(store.purchase(9))
        assertFalse(9 in MahjongStore(prefs).unlockedTextures)
    }

    @Test fun difficultyChangesOnlyFutureFreePlayBoardsAndSurvivesRecreation() {
        val store=MahjongStore(prefs)
        assertEquals(1,store.difficulty)
        val started=store.newRun("CLASSIC",21312L)
        assertEquals(4,started.game.stage)
        store.setDifficulty(0)
        val resumed=MahjongStore(prefs).resumeOrCreate("CLASSIC",21313L)
        assertEquals(started.id,resumed.id)
        assertEquals(started.game.snapshot(),resumed.game.snapshot())
        assertEquals(4,resumed.game.stage)
        assertEquals(0,MahjongStore(prefs).difficulty)
        assertEquals(1,store.newRun("CLASSIC",21313L).game.stage)
        store.setDifficulty(1)
        assertEquals(4,store.newRun("CLASSIC",21313L).game.stage)
        store.setDifficulty(0)
        prefs.edit().putInt("wins_CLASSIC",9).commit()
        assertEquals(4,store.newRun("CLASSIC",21313L).game.stage)
        prefs.edit().putInt("wins_CLASSIC",999).commit()
        assertEquals(5,store.newRun("CLASSIC",21313L).game.stage)
        store.setDifficulty(99); assertEquals(1,store.difficulty)
        store.setDifficulty(-1); assertEquals(0,store.difficulty)
    }

    @Test fun journeyAndDailyIgnoreTheFreePlayDifficulty() {
        val store=MahjongStore(prefs)
        prefs.edit().putInt("stage",5).commit()
        store.setDifficulty(0)
        val easy=store.newRun("daily",21312L)
        store.setDifficulty(1)
        val hard=store.newRun("daily",21312L)
        assertEquals(5,easy.game.stage); assertEquals(easy.game.snapshot(),hard.game.snapshot())
        val journey=store.newRun("journey",21312L)
        assertEquals(5,journey.game.stage)
        assertEquals(MahjongMode.SLIDE,journey.game.mode)
    }

    @Test fun replacingLegacyMemoryKeepsEconomyCollectionAndJourneyProgress() {
        prefs.edit().putBoolean("independent_saves_v2",true).putInt("pearls",876)
            .putInt("wins",11).putInt("stage",5)
            .putStringSet("porcelains",setOf("0","1","2","17"))
            .putStringSet("textures",setOf("0","1","2","9"))
            .putInt("porcelain",17).putInt("texture",9).commit()
        val legacyGame=MahjongGame(MahjongMode.CLASSIC,99,5)
        legacyGame.restore(legacyGame.snapshot().copy(tiles=List(36) { id ->
            MahjongTile(id,id%6,id/6,0,id/2)
        }))
        val legacy=JSONObject(MahjongSave.encode(legacyGame,true,false,0L,id="legacy-memory"))
            .put("version",2).put("mode","MEMORY")
        prefs.edit().putString("run_journey",legacy.toString())
            .putString("current_journey","legacy-memory").commit()
        val store=MahjongStore(prefs)
        val resumed=store.resumeOrCreate("journey",21312L)
        assertEquals(MahjongMode.SLIDE,resumed.game.mode)
        assertEquals("legacy-memory",resumed.id); assertEquals(5,resumed.game.stage)
        assertEquals(876,store.pearls); assertEquals(11,store.wins); assertEquals(5,store.stage)
        assertEquals(17,store.style); assertEquals(9,store.texture)
        store.save(resumed)
        assertEquals(4,JSONObject(prefs.getString("run_journey",null)!!).getInt("version"))
        assertEquals(resumed.game.snapshot(),MahjongStore(prefs).load("journey")!!.game.snapshot())
    }

    @Test fun iceMigrationKeepsInventoryAndOnlyCreditsItsVictoryOnce() {
        val store=MahjongStore(prefs)
        prefs.edit().putInt("pearls",876).putInt("wins",11).putInt("stage",5)
            .putStringSet("porcelains",setOf("0","1","2","17"))
            .putStringSet("textures",setOf("0","1","2","9"))
            .putInt("porcelain",17).putInt("texture",9).commit()
        val initial=store.newRun("SLIDE",21312L)
        val legacy=JSONObject(MahjongSave.encode(initial)).put("version",3)
        prefs.edit().putString("run_SLIDE",legacy.toString()).commit()
        val migrated=store.resumeOrCreate("SLIDE",21313L)
        assertTrue(migrated.iceMigration)
        assertEquals(initial.id,migrated.id)
        assertEquals(876,store.pearls); assertEquals(11,store.wins); assertEquals(5,store.stage)
        assertEquals(17,store.style); assertEquals(9,store.texture)
        assertEquals(initial.game.remaining,migrated.game.remaining)
        assertEquals(initial.game.assists,migrated.game.assists)
        val witness=migrated.game.solutionSteps.toList()
        val stale=MahjongSave.decode(MahjongSave.encode(migrated))!!
        store.save(migrated)
        assertEquals(4,JSONObject(prefs.getString("run_SLIDE",null)!!).getInt("version"))
        assertFalse(store.load("SLIDE")!!.iceMigration)
        assertEquals(migrated.game.snapshot(),store.load("SLIDE")!!.game.snapshot())
        for(step in witness) {
            step.slide?.let { move -> assertEquals(MahjongGame.Result.MOVED,migrated.game.slide(move.id,move.x,move.y)) }
            step.ids.forEach { migrated.game.tap(it) }
        }
        assertTrue(migrated.game.complete)
        val reward=store.finish(migrated)!!
        val earned=store.pearls
        assertEquals(876+reward.pearls,earned)
        assertEquals(12,store.wins); assertEquals(5,store.stage)
        assertEquals(reward,store.finish(migrated))
        store.save(stale)
        assertEquals(earned,store.pearls); assertEquals(12,store.wins)
        assertEquals(reward,store.load("SLIDE")!!.reward)
    }

    private fun finish(game:MahjongGame) {
        if(game.mode==MahjongMode.CLASSIC || game.mode==MahjongMode.TRIPLE) {
            game.solution.toList().forEach { ids -> ids.forEach { game.tap(it) } }
        } else {
            while(!game.complete) {
                if(game.hint().isEmpty()) game.shuffle()
                game.hint().forEach { game.tap(it) }
            }
        }
        assertTrue(game.complete)
    }
}
