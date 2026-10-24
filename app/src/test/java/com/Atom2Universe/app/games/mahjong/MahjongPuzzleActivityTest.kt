package com.Atom2Universe.app.games.mahjong

import android.app.Application
import android.content.Context
import android.content.DialogInterface
import android.graphics.RectF
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.Atom2Universe.app.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w412dp-h820dp-port")
class MahjongPuzzleActivityTest {
    private val prefs get() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("mahjong_garden", Context.MODE_PRIVATE)

    @Before fun reset() {
        prefs.edit().clear().putBoolean("sound", false).putBoolean("effects", false)
            .putBoolean("haptics", false).commit()
    }

    @Test fun slideReplacesMemoryAndDifficultyChangesOnlyNewBoards() {
        val controller = Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            val activity = controller.get()
            val tags = descendants(content(activity)).map { it.tag }.toList()
            assertTrue(tags.contains("mode:SLIDE"))
            assertFalse(tags.contains("mode:MEMORY"))
            assertEquals(1, MahjongStore(prefs).difficulty)
            for (level in 0..1) assertTrue(tags.contains("difficulty:$level"))
            assertFalse(tags.contains("difficulty:2"))

            tagged(activity, "difficulty:1").performClick()
            assertEquals(1, MahjongStore(prefs).difficulty)
            mode(activity, "CLASSIC")
            val hard = board(activity).game
            assertTrue(hard.stage >= 4)
            val saved = hard.snapshot()
            val runId = MahjongStore(prefs).load("CLASSIC")!!.id

            activity.onBackPressedDispatcher.onBackPressed()
            tagged(activity, "difficulty:0").performClick()
            assertEquals(0, MahjongStore(prefs).difficulty)
            mode(activity, "CLASSIC")
            assertEquals(hard.stage, board(activity).game.stage)
            assertEquals(saved, board(activity).game.snapshot())
            assertEquals(runId, MahjongStore(prefs).load("CLASSIC")!!.id)

            action(activity, R.string.mj_new_short)
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals(activity.getString(R.string.mj_new_board),
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).text.toString())
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            // AlertDialog dispatches its positive-button listener through the main Handler.
            shadowOf(Looper.getMainLooper()).idle()
            awaitMahjongWork(activity)
            assertEquals(1, board(activity).game.stage)
            assertNotEquals(runId, MahjongStore(prefs).load("CLASSIC")!!.id)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun tappingMidCorridorAndShortSwipesGlideToTheObstacleWithUndoAndResume() {
        var saved: MahjongSnapshot? = null
        val controller = Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            val activity = controller.get()
            mode(activity, "SLIDE")
            layout(activity)
            val current = board(activity)
            val game = current.game
            arrangeIceCorridor(game)
            current.refresh()
            val move = MahjongSlide(0, 4, 2)
            val original = game.tiles[move.id].copy()
            val initialMoves = game.moves
            val source = tileBounds(current, move.id)
            val middle = cellCenter(current, 2, 2)

            tap(current, source.centerX(), source.centerY())
            assertEquals(move.id, game.selected)
            // The tapped empty cell chooses the direction, never an intermediate stop.
            tap(current, middle.first, middle.second)
            assertEquals(move.x, game.tiles[move.id].x)
            assertEquals(move.y, game.tiles[move.id].y)
            assertEquals(5, game.tiles[2].x)
            assertEquals(initialMoves + 1, game.moves)
            assertEquals(game.snapshot(), MahjongStore(prefs).load("SLIDE")!!.game.snapshot())

            action(activity, R.string.mj_undo)
            assertEquals(original, game.tiles[move.id])
            assertEquals(initialMoves, game.moves)
            val restoredBounds = tileBounds(current, move.id)
            // Release within the starting tile, with a slight diagonal finger movement.
            // The dominant axis still sends it all the way to the blocker.
            gesture(current, restoredBounds.centerX(), restoredBounds.centerY(),
                restoredBounds.centerX() + restoredBounds.width() * .42f,
                restoredBounds.centerY() + restoredBounds.height() * .08f)
            assertEquals(move.x, game.tiles[move.id].x)
            assertEquals(move.y, game.tiles[move.id].y)
            val atStop = tileBounds(current, move.id)
            val movesAtStop = game.moves
            gesture(current, atStop.centerX(), atStop.centerY(),
                atStop.centerX() + atStop.width() * .42f, atStop.centerY())
            assertEquals(movesAtStop, game.moves)
            assertEquals(4, game.tiles[move.id].x)
            assertEquals(5, game.tiles[2].x) // An occupied neighbour is not pushed.
            saved = game.snapshot()

            activity.onBackPressedDispatcher.onBackPressed()
            mode(activity, "SLIDE")
            assertEquals(saved, board(activity).game.snapshot())
        } finally {
            controller.pause().stop().destroy()
        }
        val reopened = Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            mode(reopened.get(), "SLIDE")
            assertEquals(saved, board(reopened.get()).game.snapshot())
        } finally {
            reopened.pause().stop().destroy()
        }
    }

    @Test fun aShortVerticalSwipeTravelsToTheWallEvenWhenReleasedInsideTheTile() {
        val controller = Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            val activity = controller.get()
            mode(activity, "SLIDE")
            layout(activity)
            val current = board(activity)
            arrangeIceCorridor(current.game)
            current.refresh()
            val source = tileBounds(current, 0)
            gesture(current, source.centerX(), source.centerY(),
                source.centerX() - source.width() * .05f,
                source.centerY() - source.height() * .38f)
            assertEquals(1, current.game.tiles[0].x)
            assertEquals(0, current.game.tiles[0].y)
            assertEquals(1, current.game.moves)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun changingFocusReturnsThePreviousTileAndFocusSurvivesReopening() {
        var saved: MahjongSnapshot? = null
        val controller = Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            val activity = controller.get()
            mode(activity, "SLIDE")
            layout(activity)
            val current = board(activity)
            val game = current.game
            arrangeIceCorridor(game)
            current.refresh()
            shortSwipe(current, 0, 1, 0)
            shortSwipe(current, 0, 0, -1)
            assertEquals(0, game.selected)
            assertEquals(MahjongCell(1, 2), game.focusOrigin)
            assertEquals(4, game.tiles[0].x)
            assertEquals(0, game.tiles[0].y)

            val other = tileBounds(current, 2)
            tap(current, other.centerX(), other.centerY())
            assertEquals(1, game.tiles[0].x)
            assertEquals(2, game.tiles[0].y)
            assertEquals(2, game.selected)
            assertEquals(MahjongCell(5, 2), game.focusOrigin)
            assertEquals(4, game.remaining)
            assertEquals(game.snapshot(), MahjongStore(prefs).load("SLIDE")!!.game.snapshot())

            shortSwipe(current, 2, 0, -1)
            shortSwipe(current, 2, -1, 0)
            assertEquals(0, game.tiles[2].x)
            assertEquals(0, game.tiles[2].y)
            assertEquals(MahjongCell(5, 2), game.focusOrigin)
            saved = game.snapshot()
            activity.onBackPressedDispatcher.onBackPressed()
            mode(activity, "SLIDE")
            assertEquals(saved, board(activity).game.snapshot())
            assertEquals(2, board(activity).game.selected)
        } finally {
            controller.pause().stop().destroy()
        }
        val reopened = Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            val activity = reopened.get()
            mode(activity, "SLIDE")
            layout(activity)
            val current = board(activity)
            assertEquals(saved, current.game.snapshot())
            assertEquals(2, current.game.selected)
            assertEquals(MahjongCell(5, 2), current.game.focusOrigin)
            val other = tileBounds(current, 0)
            tap(current, other.centerX(), other.centerY())
            assertEquals(5, current.game.tiles[2].x)
            assertEquals(2, current.game.tiles[2].y)
            assertEquals(0, current.game.selected)
            assertEquals(MahjongCell(1, 2), current.game.focusOrigin)
        } finally {
            reopened.pause().stop().destroy()
        }
    }

    @Test fun swipingAnotherTileReturnsTheOldFocusBeforeCalculatingItsStop() {
        val controller = Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            val activity = controller.get()
            mode(activity, "SLIDE")
            layout(activity)
            val current = board(activity)
            val game = current.game
            arrangeIceCorridor(game)
            current.refresh()
            shortSwipe(current, 0, 1, 0)
            shortSwipe(current, 0, 0, -1)
            assertEquals(4, game.tiles[0].x)
            assertEquals(0, game.tiles[0].y)

            // The previous focus appears at the top of this lane, but returns
            // to its origin before the newly selected tile starts its slide.
            shortSwipe(current, 3, 0, -1)
            assertEquals(1, game.tiles[0].x)
            assertEquals(2, game.tiles[0].y)
            assertEquals(3, game.selected)
            assertEquals(MahjongCell(4, 4), game.focusOrigin)
            assertEquals(4, game.tiles[3].x)
            assertEquals(0, game.tiles[3].y)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun cascadeExplainsTheNextGravityDirectionAndUndoRestoresIt() {
        val controller = Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            val activity = controller.get()
            mode(activity, "CASCADE")
            val current = board(activity)
            val directions = activity.resources.getStringArray(R.array.mj_gravity_directions)
            fun hasDirection(index: Int) = descendants(content(activity)).filterIsInstance<TextView>()
                .any { it.text.toString() == activity.getString(R.string.mj_cascade_status, directions[index]) }
            assertEquals(0, current.game.gravityDirection)
            assertTrue(hasDirection(0))
            val pair = current.game.hint()
            assertEquals(2, pair.size)
            pair.forEach(current::chooseTile)
            assertEquals(1, current.game.matches)
            assertEquals(1, current.game.gravityDirection)
            assertTrue(hasDirection(1))
            action(activity, R.string.mj_undo)
            assertEquals(0, current.game.gravityDirection)
            assertTrue(hasDirection(0))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun leavingWhilePreparingNeverPublishesTheCancelledBoard() {
        val controller = Robolectric.buildActivity(MahjongActivity::class.java).setup()
        val release = CountDownLatch(1)
        try {
            val activity = controller.get()
            val started = CountDownLatch(1)
            mahjongWorker(activity).submit { started.countDown(); release.await() }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            tagged(activity, "mode:SLIDE").performClick()
            assertTrue(descendants(content(activity)).filterIsInstance<TextView>()
                .any { it.text.toString() == activity.getString(R.string.mj_preparing) })
            // The UI remains responsive while the puzzle worker is occupied.
            activity.onBackPressedDispatcher.onBackPressed()
            release.countDown()
            awaitMahjongWork(activity)
            assertTrue(descendants(content(activity)).any { it.tag == "mode:SLIDE" })
            assertNull(MahjongStore(prefs).load("SLIDE"))
        } finally {
            release.countDown()
            controller.pause().stop().destroy()
        }
    }

    @Test fun hintsAreDiscardedAfterAnotherMoveAndShuffleCannotMutateAClosedBoard() {
        val controller = Robolectric.buildActivity(MahjongActivity::class.java).setup()
        val release = CountDownLatch(1)
        try {
            val activity = controller.get()
            mode(activity, "SLIDE")
            val current = board(activity)
            arrangeIceCorridor(current.game)
            current.refresh()
            val started = CountDownLatch(1)
            mahjongWorker(activity).submit { started.countDown(); release.await() }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            action(activity, R.string.mj_hint)
            current.chooseTile(0)
            assertFalse(descendants(content(activity)).filterIsInstance<TextView>()
                .any { it.text.toString() == activity.getString(R.string.mj_thinking) })
            val state = current.game.snapshot()
            action(activity, R.string.mj_shuffle)
            assertFalse(current.isEnabled)
            current.chooseTile(2)
            assertEquals(state, current.game.snapshot())
            activity.onBackPressedDispatcher.onBackPressed()
            release.countDown()
            awaitMahjongWork(activity)
            assertEquals(state, current.game.snapshot())
            assertEquals(state, MahjongStore(prefs).load("SLIDE")!!.game.snapshot())
            assertTrue(descendants(content(activity)).any { it.tag == "mode:SLIDE" })
        } finally {
            release.countDown()
            controller.pause().stop().destroy()
        }
    }

    @Test fun backgroundHintAndShuffleKeepAPlayableBoardAndUndo() {
        val controller = Robolectric.buildActivity(MahjongActivity::class.java).setup()
        try {
            val activity = controller.get()
            mode(activity, "SLIDE")
            val current = board(activity)
            arrangeIceCorridor(current.game)
            current.refresh()
            val state = current.game.snapshot()
            action(activity, R.string.mj_hint)
            awaitMahjongWork(activity)
            assertEquals(state, current.game.snapshot())
            assertFalse(descendants(content(activity)).filterIsInstance<TextView>()
                .any { it.text.toString() == activity.getString(R.string.mj_thinking) })
            action(activity, R.string.mj_shuffle)
            awaitMahjongWork(activity)
            assertTrue(current.isEnabled)
            assertNotNull(MahjongIceSolver.solve(current.game))
            assertEquals(1, current.game.assists)
            action(activity, R.string.mj_undo)
            assertEquals(state.tiles, current.game.snapshot().tiles)
            assertEquals(state.focusOrigin, current.game.focusOrigin)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun pausedPreparationWaitsUntilResumeAndPausedShuffleCostsNothing() {
        val controller = Robolectric.buildActivity(MahjongActivity::class.java).setup()
        val release = CountDownLatch(1)
        try {
            val activity = controller.get()
            val started = CountDownLatch(1)
            mahjongWorker(activity).submit { started.countDown(); release.await() }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            tagged(activity, "mode:SLIDE").performClick()
            controller.pause()
            release.countDown()
            awaitMahjongWork(activity)
            assertNull(MahjongStore(prefs).load("SLIDE"))
            controller.resume()
            awaitMahjongWork(activity)
            val current = board(activity)
            val state = current.game.snapshot()
            // Even a completed worker result cannot be applied after onPause.
            action(activity, R.string.mj_shuffle)
            mahjongWorker(activity).submit {}.get(20, TimeUnit.SECONDS)
            controller.pause()
            shadowOf(Looper.getMainLooper()).idle()
            controller.resume()
            assertEquals(state, current.game.snapshot())
            assertTrue(current.isEnabled)
        } finally {
            release.countDown()
            controller.pause().stop().destroy()
        }
    }

    private fun layout(activity: MahjongActivity) {
        val density = activity.resources.displayMetrics.density
        val width = (412 * density).toInt(); val height = (820 * density).toInt()
        content(activity).measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        content(activity).layout(0, 0, width, height)
    }

    private fun arrangeIceCorridor(game: MahjongGame) {
        val positions = listOf(1 to 2, 1 to 4, 5 to 2, 4 to 4)
        val tiles = game.tiles.map { tile ->
            if (tile.id < positions.size) {
                val (x, y) = positions[tile.id]
                tile.copy(x = x, y = y, z = 0, face = tile.id / 2, removed = false)
            } else tile.copy(removed = true)
        }
        game.restore(game.snapshot().copy(tiles = tiles))
    }

    private fun tap(view: MahjongBoardView, x: Float, y: Float) = gesture(view, x, y, x, y)

    private fun shortSwipe(view: MahjongBoardView, id: Int, dx: Int, dy: Int) {
        val source = tileBounds(view, id)
        gesture(view, source.centerX(), source.centerY(),
            source.centerX() + dx * source.width() * .42f,
            source.centerY() + dy * source.height() * .38f)
    }

    private fun gesture(view: MahjongBoardView, x: Float, y: Float, endX: Float, endY: Float) {
        val start = SystemClock.uptimeMillis()
        fun send(action: Int, at: Long, px: Float, py: Float) {
            val event = MotionEvent.obtain(start, at, action, px, py, 0)
            try { assertTrue(view.dispatchTouchEvent(event)) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, start, x, y)
        if (x != endX || y != endY) send(MotionEvent.ACTION_MOVE, start + 30, endX, endY)
        send(MotionEvent.ACTION_UP, start + 60, endX, endY)
    }

    @Suppress("UNCHECKED_CAST")
    private fun tileBounds(view: MahjongBoardView, id: Int): RectF {
        val field = MahjongBoardView::class.java.getDeclaredField("bounds").apply { isAccessible = true }
        return RectF((field.get(view) as Map<Int, RectF>).getValue(id))
    }

    private fun cellCenter(view: MahjongBoardView, x: Int, y: Int): Pair<Float, Float> {
        fun number(name: String) = MahjongBoardView::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.getFloat(view)
        return number("left") + (x + .47f) * number("tileW") to
            number("top") + (y + .455f) * number("tileH")
    }

    private fun content(activity: MahjongActivity): View = activity.findViewById(android.R.id.content)
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
    private fun tagged(activity: MahjongActivity, tag: String) = descendants(content(activity)).first { it.tag == tag }
    private fun board(activity: MahjongActivity) = descendants(content(activity)).filterIsInstance<MahjongBoardView>().single()
    private fun mode(activity: MahjongActivity, name: String) {
        tagged(activity, "mode:$name").performClick()
        awaitMahjongWork(activity)
    }
    private fun action(activity: MahjongActivity, title: Int) {
        descendants(content(activity)).first { it.contentDescription == activity.getString(title) }.performClick()
    }
}
