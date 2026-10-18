package com.Atom2Universe.app.science.bestiary

import android.content.Context
import android.os.Looper
import org.junit.Assert.assertFalse
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.File

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
        } finally { worker.shutdownNow() }
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
