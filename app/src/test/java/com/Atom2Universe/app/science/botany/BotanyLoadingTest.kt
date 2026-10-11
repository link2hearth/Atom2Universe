package com.Atom2Universe.app.science.botany

import android.graphics.Bitmap
import android.os.Looper
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE)
class BotanyLoadingTest {
    private val plant=BotanyCatalog.get("robur")!!
    private fun key(mode:PlantView=PlantView.HABIT)=PlantImageKey(plant.id,mode,12,false)

    @Test fun leCalculNeBloquePasLeFilPrincipalEtLeCacheEstReutilise()=runBlocking {
        val main=Looper.getMainLooper().thread
        val calls=AtomicInteger()
        val store=PlantImageStore(render={_,_->
            assertNotSame("Rasterization must never run on Android's main thread",main,Thread.currentThread())
            calls.incrementAndGet();Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888)
        })
        val first=store.load(plant,key())
        assertSame(first,store.load(plant,key()))
        assertEquals(1,calls.get())
        assertSame(first,store.peek(key()))
    }

    @Test fun leCacheEstBorneSansRecyclerUneImageEncoreAffichee()=runBlocking {
        val store=PlantImageStore(maxBytes=12*12*4*2,render={_,_->Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888)})
        val heldByView=store.load(plant,key(PlantView.HABIT))
        store.load(plant,key(PlantView.FLOWER))
        store.load(plant,key(PlantView.FRUIT))
        assertNull(store.peek(key(PlantView.HABIT)))
        assertTrue(store.bytes<=12*12*4*2)
        assertFalse("An evicted raster may still be used by RenderThread",heldByView.isRecycled)
        assertNotNull(store.peek(key(PlantView.FRUIT)))
        assertNull(store.peek(key(PlantView.FRUIT).copy(compact=true)))
        assertEquals(256,PlantImageStore.edge(2000f,true))
        assertEquals(768,PlantImageStore.edge(2000f,false))
    }

    @Test fun changerDePageAnnuleLesDessinsEnAttenteEtNePubliePasLesAnciens()=runBlocking {
        val started=CountDownLatch(1)
        val release=CountDownLatch(1)
        val calls=AtomicInteger()
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher->
            val store=PlantImageStore(dispatcher=dispatcher,render={_,_->
                calls.incrementAndGet();started.countDown()
                check(release.await(5,TimeUnit.SECONDS))
                Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888)
            })
            val active=launch(start=CoroutineStart.UNDISPATCHED){store.load(plant,key())}
            try {
                assertTrue(started.await(5,TimeUnit.SECONDS))
                val queued=launch(start=CoroutineStart.UNDISPATCHED){store.load(plant,key(PlantView.LEAF))}
                active.cancel();queued.cancel();release.countDown()
                joinAll(active,queued)
                assertEquals("Invisible plates must not start queued raster work",1,calls.get())
                assertNull("A cancelled plate must not be published",store.peek(key()))
                assertNull(store.peek(key(PlantView.LEAF)))
            } finally {release.countDown();active.cancelAndJoin()}
        }
    }
}
