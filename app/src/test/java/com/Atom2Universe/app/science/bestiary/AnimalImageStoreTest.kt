package com.Atom2Universe.app.science.bestiary

import android.content.Context
import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AnimalImageStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    // This project does not enable includeAndroidResources for Robolectric. Feed the same
    // bundled asset bytes through the IO seam; native BitmapFactory still performs decoding.
    private fun store() = AnimalImageStore(context) { File("src/main/assets", it).inputStream() }

    @Test fun offlineDecodeIsBoundedAndSharedAndMissingAssetsAreSafe() {
        val store = store()
        val worker = Executors.newSingleThreadExecutor()
        try {
            val first = worker.submit<android.graphics.Bitmap?> { store.loadBlocking("lion") }.get(10, TimeUnit.SECONDS)
            requireNotNull(first)
            assertTrue(maxOf(first.width, first.height) <= 640)
            assertFalse(first.isRecycled)
            assertSame(first, worker.submit<android.graphics.Bitmap?> { store.loadBlocking("lion") }.get(10, TimeUnit.SECONDS))
            assertNull(worker.submit<android.graphics.Bitmap?> { store.loadBlocking("not_in_catalogue") }.get(10, TimeUnit.SECONDS))
            assertNull(store.loadBlocking("../lion"))
            val thumbnail = requireNotNull(worker.submit<android.graphics.Bitmap?> { store.loadBlocking("lion", thumbnail = true) }.get(10, TimeUnit.SECONDS))
            assertTrue(maxOf(thumbnail.width, thumbnail.height) <= 320)
            assertNotSame(first, thumbnail)
            assertSame(first, store.peek("lion"))
        } finally { worker.shutdownNow() }
    }

    @Test fun aPlateNobodyWaitsForAnyMoreIsNeverDecoded() {
        val opened = Collections.synchronizedList(mutableListOf<String>())
        val busy = CountDownLatch(2)
        val release = CountDownLatch(1)
        val done = CountDownLatch(1)
        val store = AnimalImageStore(context) { path ->
            opened += path
            if ("busy" in path) { busy.countDown(); release.await(10, TimeUnit.SECONDS) }
            if ("last" in path) done.countDown()
            throw IOException(path)
        }
        // Both decoding threads are held, so the next requests stay queued.
        store.request("busy_a") {}
        store.request("busy_b") {}
        assertTrue(busy.await(10, TimeUnit.SECONDS))
        store.request("scrolled_away") {}.close()
        store.request("last") {}
        release.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertFalse(opened.any { "scrolled_away" in it })
    }

    @Test fun cancelledCachedSubscriberCannotReceiveAPlateAfterRebinding() {
        val store = store()
        val worker = Executors.newSingleThreadExecutor()
        try {
            requireNotNull(worker.submit<android.graphics.Bitmap?> { store.loadBlocking("lion") }.get(10, TimeUnit.SECONDS))
            var received = false
            val oldRequest = store.request("lion") { received = true }
            oldRequest.close()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(received)
        } finally { worker.shutdownNow() }
    }
}
