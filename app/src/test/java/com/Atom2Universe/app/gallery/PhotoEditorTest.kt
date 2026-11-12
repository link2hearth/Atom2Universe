package com.Atom2Universe.app.gallery

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoEditorTest {
    private fun sheet(w: Int, h: Int, color: Int) = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }
    private fun rectangle(left: Float,top: Float,right: Float,bottom: Float) =
        PhotoMask(listOf(MaskPart.Shape(Trace(listOf(PointF(left,top),PointF(right,bottom)),rectangle=true))))

    @Test fun localFilterPreservesUnselectedPixelsAndTransparencyAtBothResolutions() {
        val op = PhotoEdit.Tone(PhotoLook.MONO.matrix(),rectangle(0f,0f,0.5f,1f))
        for (edge in listOf(32,240)) {
            val source = sheet(edge,edge,0x802040a0.toInt())
            val output = PhotoRender.apply(source,op)
            val tinted = output.getPixel(edge/4,edge/2)
            assertEquals(Color.red(tinted),Color.green(tinted))
            assertEquals(Color.green(tinted),Color.blue(tinted))
            assertEquals(128,Color.alpha(tinted))
            assertEquals(source.getPixel(edge-3,5),output.getPixel(edge-3,5))
        }
    }

    @Test fun wandRemovesOnlyConnectedBackgroundAndBrushRefinesACutout() {
        val source = sheet(64,64,Color.WHITE)
        for (y in 8..55) for (x in 18..45) source.setPixel(x,y,Color.BLUE)
        source.setPixel(30,30,Color.WHITE)
        val bg = PhotoSelections.wand(source,PointF(.05f,.1f),20)
        val erased = PhotoRender.apply(source,PhotoEdit.Cut(bg,false))
        assertEquals(0,Color.alpha(erased.getPixel(2,2)))
        assertEquals(Color.BLUE,erased.getPixel(24,20))
        assertEquals(Color.WHITE,erased.getPixel(30,30))
        val estimate = PhotoSelections.subject(source,RectF(.2f,.05f,.8f,.95f))
        val refined = PhotoMask(estimate.parts + MaskPart.Shape(Trace(listOf(PointF(.4f,.4f)),.15f),subtract=true))
        val cut = PhotoRender.apply(source,PhotoEdit.Cut(refined,true))
        assertEquals(0,Color.alpha(cut.getPixel(1,1)))
        assertEquals(0,Color.alpha(cut.getPixel(25,25)))
        assertEquals(Color.BLUE,cut.getPixel(32,40))
        assertEquals(Color.WHITE,source.getPixel(1,1))
    }

    @Test fun cropMirrorAndRotationReplayInImageCoordinates() {
        val src = sheet(100,60,Color.RED)
        for (y in 0 until 60) for (x in 50 until 100) src.setPixel(x,y,Color.BLUE)
        val cropped = PhotoRender.apply(src,PhotoEdit.Crop(RectF(.25f,0f,.75f,1f)))
        val mirrored = PhotoRender.apply(cropped,PhotoEdit.Mirror)
        val rotated = PhotoRender.apply(mirrored,PhotoEdit.Rotate)
        assertEquals(60,rotated.width)
        assertEquals(50,rotated.height)
        assertEquals(Color.BLUE,rotated.getPixel(5,5))
        assertEquals(Color.RED,rotated.getPixel(5,45))
    }

    @Test fun undoRedoAndExportReplayOnSourceAfterSmallPreview() {
        val app = RuntimeEnvironment.getApplication()
        val model = PhotoEditorModel(app)
        fun settled() {
            val deadline = System.nanoTime()+10_000_000_000L
            while (model.busy && System.nanoTime()<deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            assertFalse(model.busy)
            assertFalse(model.error)
        }
        val file = File(app.cacheDir,"editor-source.png")
        val source = sheet(2000,1000,Color.BLUE)
        file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.PNG,100,it)) }
        model.open(Uri.fromFile(file),null)
        settled()
        assertTrue(requireNotNull(model.preview).width < source.width)
        val trace = Trace(listOf(PointF(.25f,.5f)),.25f)
        model.edit(PhotoEdit.Ink(trace,Color.WHITE,true,null))
        settled()
        model.history(-1)
        settled()
        assertEquals(Color.BLUE,requireNotNull(model.preview).let { it.getPixel(it.width/4,it.height/2) })
        model.history(1)
        settled()
        model.prepare()
        settled()
        val exported = requireNotNull(model.prepared)
        assertEquals(2000,exported.width)
        assertEquals(1000,exported.height)
        assertEquals(0,Color.alpha(exported.getPixel(500,500)))
        assertEquals(Color.BLUE,exported.getPixel(1500,500))
        model.cancelExport()
    }
}
